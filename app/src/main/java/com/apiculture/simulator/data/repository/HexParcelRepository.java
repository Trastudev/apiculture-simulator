package com.apiculture.simulator.data.repository;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;

import com.apiculture.simulator.data.local.dao.HexParcelOwnershipDao;
import com.apiculture.simulator.data.local.entity.HexParcelOwnershipEntity;
import com.apiculture.simulator.domain.market.HoneyMarketEngine;
import com.apiculture.simulator.domain.parcel.HexFlora;
import com.apiculture.simulator.domain.game.XpAwards;
import com.apiculture.simulator.domain.parcel.FloraProgression;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.SetOptions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class HexParcelRepository {

    private static final String TAG = "HexParcelRepository";
    public static final String COLLECTION = "hexParcels";

    private final HexParcelOwnershipDao dao;
    private final EconomyRepository economyRepository;
    @Nullable
    private PlayerProgressRepository playerProgressRepository;
    private final HexFloraRepository hexFloraRepository;
    private final FirebaseFirestore firestore;
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Context appContext;
    private ListenerRegistration cloudListener;

    public HexParcelRepository(
            HexParcelOwnershipDao dao,
            EconomyRepository economyRepository,
            Context appContext,
            HexFloraRepository hexFloraRepository) {
        this.dao = dao;
        this.economyRepository = economyRepository;
        this.hexFloraRepository = hexFloraRepository;
        this.appContext = appContext.getApplicationContext();
        FirebaseFirestore fs;
        try {
            fs = FirebaseFirestore.getInstance();
        } catch (Exception e) {
            fs = null;
        }
        this.firestore = fs;
    }

    public void setPlayerProgressRepository(@Nullable PlayerProgressRepository playerProgressRepository) {
        this.playerProgressRepository = playerProgressRepository;
    }

    public LiveData<List<HexParcelOwnershipEntity>> observeOwnerships() {
        return dao.observeAll();
    }

    /** Mapa hexId → ownerId (solo parcelas con dueño). */
    public Map<String, String> getOwnershipMapSync() {
        Map<String, String> map = new HashMap<>();
        for (HexParcelOwnershipEntity row : dao.getAllSync()) {
            if (row != null && row.hexId != null && row.ownerId != null) {
                map.put(row.hexId, row.ownerId);
            }
        }
        return map;
    }

    public String getOwnerSync(String hexId) {
        if (hexId == null) {
            return null;
        }
        HexParcelOwnershipEntity row = dao.getByHexIdSync(hexId);
        return row == null ? null : row.ownerId;
    }

    /** Ids de hexágonos de propiedad del usuario (orden no garantizado). */
    public List<String> listOwnedHexIdsSync(String ownerId) {
        List<String> out = new ArrayList<>();
        if (ownerId == null || ownerId.isEmpty()) {
            return out;
        }
        for (HexParcelOwnershipEntity row : dao.getAllForOwnerSync(ownerId)) {
            if (row != null && row.hexId != null && !row.hexId.isEmpty()) {
                out.add(row.hexId);
            }
        }
        return out;
    }

    public void startRealtimeCloudSync() {
        if (firestore == null || cloudListener != null) {
            return;
        }
        cloudListener = firestore.collection(COLLECTION)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        return;
                    }
                    if (snapshot == null) {
                        return;
                    }
                    ioExecutor.execute(() -> {
                        for (DocumentChange dc : snapshot.getDocumentChanges()) {
                            String hexId = dc.getDocument().getId();
                            if (dc.getType() == DocumentChange.Type.REMOVED) {
                                dao.deleteByHexId(hexId);
                                hexFloraRepository.clearAllFlorasForHexBlocking(hexId);
                                continue;
                            }
                            String ownerId = dc.getDocument().getString("ownerId");
                            if (hexId.isEmpty() || ownerId == null || ownerId.isEmpty()) {
                                continue;
                            }
                            HexParcelOwnershipEntity row = new HexParcelOwnershipEntity();
                            row.hexId = hexId;
                            row.ownerId = ownerId;
                            String pn = dc.getDocument().getString("parcelName");
                            row.parcelName = (pn != null && !pn.trim().isEmpty()) ? pn.trim() : null;
                            dao.upsert(row);
                            Object florasObj = dc.getDocument().get("floras");
                            if (florasObj instanceof List) {
                                hexFloraRepository.replaceParcelFlorasFromFirestoreMapsBlocking(
                                        hexId, (List<?>) florasObj);
                            }
                        }
                    });
                });
    }

    public void stopRealtimeCloudSync() {
        if (cloudListener != null) {
            cloudListener.remove();
            cloudListener = null;
        }
    }

    /**
     * Elimina en Room y en Firestore todos los terrenos del dueño.
     * Los borrados en la nube van por id (desde Room), no por consulta {@code whereEqualTo}, porque esa
     * consulta a menudo devuelve PERMISSION_DENIED si las reglas no permiten listar bien la colección.
     */
    public void removeAllOwnershipForOwnerBlocking(String ownerId) {
        if (ownerId == null || ownerId.isEmpty()) {
            return;
        }
        List<HexParcelOwnershipEntity> snapshot = new ArrayList<>(dao.getAllForOwnerSync(ownerId));
        for (HexParcelOwnershipEntity row : snapshot) {
            if (row != null && row.hexId != null && !row.hexId.isEmpty()) {
                hexFloraRepository.clearAllFlorasForHexBlocking(row.hexId);
            }
        }
        dao.deleteAllForOwner(ownerId);
        if (firestore == null) {
            return;
        }
        for (HexParcelOwnershipEntity row : snapshot) {
            if (row == null || row.hexId == null || row.hexId.isEmpty()) {
                continue;
            }
            try {
                Tasks.await(firestore.collection(COLLECTION).document(row.hexId).delete());
            } catch (Exception e) {
                Log.w(TAG, "No se pudo borrar hexParcels/" + row.hexId + " en la nube", e);
            }
        }
        try {
            QuerySnapshot qs = Tasks.await(firestore.collection(COLLECTION)
                    .whereEqualTo("ownerId", ownerId)
                    .get());
            for (QueryDocumentSnapshot d : qs) {
                try {
                    Tasks.await(d.getReference().delete());
                } catch (Exception e) {
                    Log.w(TAG, "No se pudo borrar hex residual " + d.getId(), e);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Consulta de limpieza hexParcels omitida (reglas/red)", e);
        }
    }

    /** Nombre por defecto al reinicio: «Terreno » + 6 cifras aleatorias. */
    public static String newDefaultTerrenoName() {
        int n = 100000 + (int) (Math.random() * 900000);
        return "Terreno " + n;
    }

    public static String sanitizeParcelName(String raw) {
        if (raw == null) {
            return "";
        }
        String t = raw.trim();
        if (t.length() > 80) {
            t = t.substring(0, 80);
        }
        return t;
    }

    /**
     * Etiqueta para listas y diálogos; si no hay nombre guardado, un texto corto derivado del id.
     */
    public String getParcelDisplayNameSync(String hexId) {
        if (hexId == null || hexId.isEmpty()) {
            return "Terreno";
        }
        HexParcelOwnershipEntity row = dao.getByHexIdSync(hexId);
        if (row != null && row.parcelName != null && !row.parcelName.trim().isEmpty()) {
            return row.parcelName.trim();
        }
        int u = hexId.lastIndexOf('_');
        String tail = u > 0 ? hexId.substring(u + 1) : hexId;
        return "Terreno · " + tail;
    }

    /** Reinicio / tutorial: persiste en local y intenta la nube (sin revertir local si falla). */
    public void seedOwnershipLocalPreferCloud(String hexId, String ownerId, String parcelName) throws Exception {
        if (hexId == null || hexId.isEmpty() || ownerId == null || ownerId.isEmpty()) {
            return;
        }
        String name = sanitizeParcelName(parcelName);
        if (name.isEmpty()) {
            name = newDefaultTerrenoName();
        }
        HexParcelOwnershipEntity row = new HexParcelOwnershipEntity();
        row.hexId = hexId;
        row.ownerId = ownerId;
        row.parcelName = name;
        dao.upsert(row);
        if (firestore != null) {
            Map<String, Object> m = new HashMap<>();
            m.put("ownerId", ownerId);
            m.put("hexId", hexId);
            m.put("parcelName", name);
            try {
                Tasks.await(firestore.collection(COLLECTION).document(hexId).set(m, SetOptions.merge()));
            } catch (Exception e) {
                Log.w(TAG, "seedOwnership cloud skipped", e);
            }
        }
    }

    /** Tras cobrar: primero nube para no quedar solo en Room si falla el permiso. */
    private void publishOwnershipCloudThenLocal(String hexId, String ownerId, String parcelName) throws Exception {
        String name = sanitizeParcelName(parcelName);
        if (name.isEmpty()) {
            throw new IllegalArgumentException("Nombre de terreno vacío.");
        }
        if (firestore != null) {
            Map<String, Object> m = new HashMap<>();
            m.put("ownerId", ownerId);
            m.put("hexId", hexId);
            m.put("parcelName", name);
            Tasks.await(firestore.collection(COLLECTION).document(hexId).set(m, SetOptions.merge()));
        }
        HexParcelOwnershipEntity row = new HexParcelOwnershipEntity();
        row.hexId = hexId;
        row.ownerId = ownerId;
        row.parcelName = name;
        dao.upsert(row);
    }

    /** Sube la lista de floras del hex a Firestore (merge del campo {@code floras}). */
    public void syncHexParcelFlorasToCloudBlocking(String hexId) {
        if (firestore == null || hexId == null || hexId.isEmpty()) {
            return;
        }
        try {
            List<Map<String, Object>> arr = hexFloraRepository.parcelFlorasToFirestoreListBlocking(hexId);
            Map<String, Object> patch = new HashMap<>();
            patch.put("floras", arr);
            Tasks.await(firestore.collection(COLLECTION).document(hexId).set(patch, SetOptions.merge()));
        } catch (Exception e) {
            Log.w(TAG, "syncHexParcelFlorasToCloudBlocking", e);
        }
    }

    /**
     * Compra de terreno: cobra (base + prima por flora nativa del hex), escribe dueño y flora ya lista.
     * La flora nativa es la vista previa del mapa; no hay siembra ni espera.
     */
    public void purchaseHex(
            String hexId,
            String buyerId,
            String parcelName,
            int playerLevel,
            Consumer<String> onMainMessage) {
        if (hexId == null || hexId.isEmpty() || buyerId == null || buyerId.isEmpty()) {
            mainHandler.post(() -> onMainMessage.accept("Sesión no válida."));
            return;
        }
        String nameToSave = sanitizeParcelName(parcelName);
        if (nameToSave.isEmpty()) {
            mainHandler.post(() -> onMainMessage.accept("Escribe un nombre para el terreno."));
            return;
        }
        String nativeFlora = HexFlora.nativeFloraForParcel(
                IberiaHexOverlayStore.findById(appContext, hexId));
        final String floraKey = HoneyMarketEngine.canonicalFloraKey(nativeFlora);
        if (!FloraProgression.isFloraUnlockedForPlayerLevel(floraKey, playerLevel)) {
            mainHandler.post(() -> onMainMessage.accept(
                    "Tu nivel aún no permite comprar terrenos con la flora de esta parcela."));
            return;
        }
        final int totalPriceEuros = FloraProgression.terrainPurchaseTotalEurosForNativeFlora(floraKey);
        ioExecutor.execute(() -> {
            double spentAmount = 0.0;
            try {
                HexParcelOwnershipEntity existing = dao.getByHexIdSync(hexId);
                if (existing != null) {
                    if (buyerId.equals(existing.ownerId)) {
                        mainHandler.post(() -> onMainMessage.accept("Ya eres dueño de este terreno."));
                    } else {
                        mainHandler.post(() -> onMainMessage.accept("Este terreno pertenece a otro jugador."));
                    }
                    return;
                }
                if (!economyRepository.trySpend(totalPriceEuros)) {
                    mainHandler.post(() -> onMainMessage.accept(
                            "Saldo insuficiente (" + totalPriceEuros + " B)."));
                    return;
                }
                spentAmount = totalPriceEuros;
                publishOwnershipCloudThenLocal(hexId, buyerId, nameToSave);
                hexFloraRepository.addReadyFloraNowBlocking(hexId, floraKey);
                syncHexParcelFlorasToCloudBlocking(hexId);
                if (playerProgressRepository != null) {
                    playerProgressRepository.addXp(buyerId, XpAwards.buyTerrain(totalPriceEuros));
                }
                mainHandler.post(() -> onMainMessage.accept(null));
            } catch (Exception e) {
                Log.e(TAG, "purchaseHex", e);
                if (spentAmount > 0.0) {
                    economyRepository.setBalance(economyRepository.getBalance() + spentAmount);
                }
                try {
                    dao.deleteByHexId(hexId);
                    hexFloraRepository.clearAllFlorasForHexBlocking(hexId);
                } catch (RuntimeException ignored) {
                }
                String msg = e instanceof FirebaseFirestoreException
                        ? ((FirebaseFirestoreException) e).getMessage()
                        : e.getMessage();
                mainHandler.post(() -> onMainMessage.accept(
                        msg != null && !msg.isEmpty() ? msg : "No se pudo completar la compra."));
            }
        });
    }
}
