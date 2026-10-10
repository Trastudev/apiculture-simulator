package com.apiculture.simulator.data.repository;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;

import com.apiculture.simulator.data.local.AppDatabase;
import com.apiculture.simulator.data.local.dao.HexParcelOwnershipDao;
import com.apiculture.simulator.data.local.entity.HexParcelOwnershipEntity;
import com.apiculture.simulator.data.local.entity.HiveEntity;
import com.apiculture.simulator.domain.game.ClimateUnlock;
import com.apiculture.simulator.domain.game.HexForageSnapshot;
import com.apiculture.simulator.domain.game.HexNectarPool;
import com.apiculture.simulator.domain.game.XpAwards;
import com.apiculture.simulator.domain.parcel.FloraProgression;
import com.apiculture.simulator.domain.parcel.HexApiary;
import com.apiculture.simulator.domain.parcel.HexFlora;
import com.apiculture.simulator.domain.parcel.HexParcel;
import com.apiculture.simulator.domain.parcel.HexParcelResolve;
import com.apiculture.simulator.domain.parcel.HexParcelRandomPoint;
import com.apiculture.simulator.domain.parcel.WarehouseRules;
import com.apiculture.simulator.domain.map.PlayableMapRegion;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.SetOptions;

import java.util.concurrent.ConcurrentHashMap;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class HexParcelRepository {

    private static final ConcurrentHashMap<String, String> PLAYER_NAMES = new ConcurrentHashMap<>();

    public static void rememberPlayerName(@Nullable String ownerId, @Nullable String playerName) {
        if (ownerId == null || ownerId.isEmpty() || playerName == null || playerName.trim().isEmpty()) {
            return;
        }
        PLAYER_NAMES.put(ownerId, playerName.trim());
    }

    @Nullable
    public static String playerNameFor(@Nullable String ownerId) {
        if (ownerId == null || ownerId.isEmpty()) {
            return null;
        }
        return PLAYER_NAMES.get(ownerId);
    }

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
    private static volatile Consumer<String> onOwnershipKnown;

    /** Avisa la primera vez que el teléfono ya tiene los apiarios de este jugador. */
    public static void setOnOwnershipKnown(@Nullable Consumer<String> listener) {
        onOwnershipKnown = listener;
    }

    public static boolean ownershipKnown(@NonNull Context context, @Nullable String ownerId) {
        if (ownerId == null || ownerId.isEmpty()) {
            return false;
        }
        return context.getApplicationContext()
                .getSharedPreferences("ownership_sync", Context.MODE_PRIVATE)
                .getBoolean(ownerId, false);
    }

    private void noteOwnershipKnown(@Nullable String ownerId) {
        if (ownerId == null || ownerId.isEmpty()) {
            return;
        }
        android.content.SharedPreferences prefs = appContext.getSharedPreferences(
                "ownership_sync", Context.MODE_PRIVATE);
        boolean already = prefs.getBoolean(ownerId, false);
        prefs.edit().putBoolean(ownerId, true).apply();
        Consumer<String> listener = onOwnershipKnown;
        if (!already && listener != null) {
            listener.accept(ownerId);
        }
    }

    public HexParcelRepository(
            HexParcelOwnershipDao dao,
            EconomyRepository economyRepository,
            Context appContext,
            HexFloraRepository hexFloraRepository) {
        this.dao = dao;
        this.economyRepository = economyRepository;
        this.hexFloraRepository = hexFloraRepository;
        this.appContext = appContext.getApplicationContext();
        this.firestore = null;
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

    public boolean hasOwnerSync(@Nullable String hexId, @Nullable String ownerId) {
        if (hexId == null || hexId.isEmpty() || ownerId == null || ownerId.isEmpty()) {
            return false;
        }
        return dao.getByHexAndOwnerSync(hexId, ownerId) != null;
    }

    @NonNull
    static String ownershipCloudId(@NonNull String hexId, @NonNull String ownerId) {
        return ownershipCloudId(hexId, ownerId, "default");
    }

    @NonNull
    static String ownershipCloudId(@NonNull String hexId, @NonNull String ownerId, @Nullable String siteId) {
        String site = siteId != null && !siteId.isEmpty() ? siteId : "default";
        if ("default".equals(site)) {
            return hexId + "::" + ownerId;
        }
        return hexId + "::" + ownerId + "::" + site;
    }

    /** Ids de hexágonos de propiedad del usuario (orden no garantizado). */
    public List<String> listOwnedHexIdsSync(String ownerId) {
        List<String> out = new ArrayList<>();
        if (ownerId == null || ownerId.isEmpty()) {
            return out;
        }
        for (HexParcelOwnershipEntity row : dao.getAllForOwnerSync(ownerId)) {
            if (row != null && row.hexId != null && !row.hexId.isEmpty()
                    && WarehouseRules.isApiarySite(row) && !out.contains(row.hexId)) {
                out.add(row.hexId);
            }
        }
        return out;
    }

    public void startRealtimeCloudSync() {
        if (GameServer.enabled()) {
            ioExecutor.execute(this::syncOwnershipWithGameServerBlocking);
            return;
        }
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
                    boolean fromCache = snapshot.getMetadata().isFromCache();
                    ioExecutor.execute(() -> {
                        AppDatabase.getInstance(appContext).runInTransaction(() -> {
                        for (DocumentChange dc : snapshot.getDocumentChanges()) {
                            String docId = dc.getDocument().getId();
                            String hexId = dc.getDocument().getString("hexId");
                            String ownerId = dc.getDocument().getString("ownerId");
                            String siteId = dc.getDocument().getString("siteId");
                            if (hexId == null || hexId.isEmpty() || ownerId == null || ownerId.isEmpty()
                                    || siteId == null || siteId.isEmpty()) {
                                String[] parts = docId.split("::", 3);
                                if (hexId == null || hexId.isEmpty()) {
                                    hexId = parts.length > 0 ? parts[0] : docId;
                                }
                                if (ownerId == null || ownerId.isEmpty()) {
                                    ownerId = parts.length > 1 ? parts[1] : null;
                                }
                                if (siteId == null || siteId.isEmpty()) {
                                    siteId = parts.length > 2 ? parts[2] : "default";
                                }
                            }
                            if (dc.getType() == DocumentChange.Type.REMOVED) {
                                if (ownerId != null && !ownerId.isEmpty()) {
                                    String gone = siteId != null && !siteId.isEmpty() ? siteId : "default";
                                    dao.deleteByHexOwnerSite(hexId, ownerId, gone);
                                } else {
                                    dao.deleteByHexId(hexId);
                                }
                                continue;
                            }
                            if (hexId.isEmpty() || ownerId == null || ownerId.isEmpty()) {
                                continue;
                            }
                            HexParcelOwnershipEntity existing = dao.getByHexOwnerSiteSync(hexId, ownerId, siteId);
                            HexParcelOwnershipEntity row = new HexParcelOwnershipEntity();
                            row.hexId = hexId;
                            row.ownerId = ownerId;
                            row.siteId = siteId != null && !siteId.isEmpty() ? siteId : "default";
                            String pn = dc.getDocument().getString("parcelName");
                            row.parcelName = (pn != null && !pn.trim().isEmpty()) ? pn.trim() : null;
                            if (dc.getDocument().contains("isPrimary")) {
                                Boolean primary = dc.getDocument().getBoolean("isPrimary");
                                row.isPrimary = Boolean.TRUE.equals(primary);
                            } else if (existing != null) {
                                row.isPrimary = existing.isPrimary;
                            }
                            if (dc.getDocument().contains("hasWarehouse")) {
                                row.hasWarehouse = Boolean.TRUE.equals(dc.getDocument().getBoolean("hasWarehouse"));
                            } else if (existing != null) {
                                row.hasWarehouse = existing.hasWarehouse;
                            }
                            if (dc.getDocument().contains("warehouseLevel")
                                    && dc.getDocument().get("warehouseLevel") instanceof Number) {
                                row.warehouseLevel = ((Number) dc.getDocument().get("warehouseLevel")).intValue();
                            } else if (existing != null) {
                                row.warehouseLevel = existing.warehouseLevel;
                            } else if (row.hasWarehouse) {
                                row.warehouseLevel = 1;
                            }
                            if (dc.getDocument().get("siteLat") instanceof Number) {
                                row.siteLat = ((Number) dc.getDocument().get("siteLat")).doubleValue();
                            } else if (existing != null) {
                                row.siteLat = existing.siteLat;
                            }
                            if (dc.getDocument().get("siteLng") instanceof Number) {
                                row.siteLng = ((Number) dc.getDocument().get("siteLng")).doubleValue();
                            } else if (existing != null) {
                                row.siteLng = existing.siteLng;
                            }
                            if (dc.getDocument().get("warehouseLat") instanceof Number) {
                                row.warehouseLat = ((Number) dc.getDocument().get("warehouseLat")).doubleValue();
                            } else if (existing != null) {
                                row.warehouseLat = existing.warehouseLat;
                            }
                            if (dc.getDocument().get("warehouseLng") instanceof Number) {
                                row.warehouseLng = ((Number) dc.getDocument().get("warehouseLng")).doubleValue();
                            } else if (existing != null) {
                                row.warehouseLng = existing.warehouseLng;
                            }
                            if (!docId.contains("::")) {
                                if (existing != null) {
                                    row.hasWarehouse = existing.hasWarehouse;
                                    row.warehouseLevel = existing.warehouseLevel;
                                    row.warehouseLat = existing.warehouseLat;
                                    row.warehouseLng = existing.warehouseLng;
                                } else {
                                    row.hasWarehouse = false;
                                    row.warehouseLevel = 0;
                                    List<HexParcelOwnershipEntity> already =
                                            dao.listByHexAndOwnerSync(hexId, ownerId);
                                    if (already != null && !already.isEmpty()) {
                                        continue;
                                    }
                                }
                            }
                            HexForageSnapshot forage = HexForageSnapshot.fromFirestore(
                                    dc.getDocument().get("forageDayKey") instanceof Number
                                            ? (Number) dc.getDocument().get("forageDayKey") : null,
                                    dc.getDocument().get("forageSiteFactor") instanceof Number
                                            ? (Number) dc.getDocument().get("forageSiteFactor") : null,
                                    dc.getDocument().get("forageDemandKgByFlora"),
                                    dc.getDocument().get("foragePoolKgByFlora"),
                                    dc.getDocument().get("forageLeftoverDemandKgByFlora"),
                                    dc.getDocument().get("forageLeftoverPoolKgByFlora"));
                            row.forageDayKey = forage.dayKey;
                            row.forageSnapshotJson = forage.toJson();
                            if (existing != null && sameOwnershipRow(existing, row)) {
                                Object florasSame = dc.getDocument().get("floras");
                                if (!(florasSame instanceof List)) {
                                    continue;
                                }
                            } else {
                                dao.upsert(row);
                            }
                            Object florasObj = dc.getDocument().get("floras");
                            if (florasObj instanceof List) {
                                hexFloraRepository.replaceParcelFlorasFromFirestoreMapsBlocking(
                                        hexId, (List<?>) florasObj);
                            }
                        }
                        });
                        if (!fromCache) {
                            com.apiculture.simulator.data.session.SignedInUser user =
                                    com.apiculture.simulator.data.session.PlayerAuth.getInstance().getCurrentUser();
                            if (user != null) {
                                noteOwnershipKnown(user.getUid());
                            }
                        }
                    });
                });
    }

    private static boolean sameOwnershipRow(
            @NonNull HexParcelOwnershipEntity a, @NonNull HexParcelOwnershipEntity b) {
        return Objects.equals(a.hexId, b.hexId)
                && Objects.equals(a.ownerId, b.ownerId)
                && Objects.equals(a.siteId, b.siteId)
                && Objects.equals(a.parcelName, b.parcelName)
                && Objects.equals(a.forageSnapshotJson, b.forageSnapshotJson)
                && a.forageDayKey == b.forageDayKey
                && a.isPrimary == b.isPrimary
                && a.hasWarehouse == b.hasWarehouse
                && a.warehouseLevel == b.warehouseLevel
                && Double.compare(a.siteLat, b.siteLat) == 0
                && Double.compare(a.siteLng, b.siteLng) == 0
                && Double.compare(a.warehouseLat, b.warehouseLat) == 0
                && Double.compare(a.warehouseLng, b.warehouseLng) == 0;
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
        if (GameServer.enabled() && !GameServer.isAvailable()) {
            return;
        }
        List<HexParcelOwnershipEntity> snapshot = new ArrayList<>(dao.getAllForOwnerSync(ownerId));
        for (HexParcelOwnershipEntity row : snapshot) {
            if (row == null || row.hexId == null || row.hexId.isEmpty()) {
                continue;
            }
            boolean shared = false;
            List<HexParcelOwnershipEntity> onHex = dao.listByHexSync(row.hexId);
            if (onHex != null) {
                for (HexParcelOwnershipEntity other : onHex) {
                    if (other != null && other.ownerId != null
                            && !ownerId.equals(other.ownerId)) {
                        shared = true;
                        break;
                    }
                }
            }
            // La flora es compartida por el hex; no se borra al reiniciar el
            // terreno de un jugador si queda otro apiario en el mismo hexágono.
            if (!shared) {
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
            String cloudId = ownershipCloudId(row.hexId, row.ownerId, row.siteId);
            firestore.collection(COLLECTION).document(cloudId).delete()
                    .addOnFailureListener(e -> Log.w(TAG, "No se pudo borrar hexParcels/" + cloudId + " en la nube", e));
        }
        firestore.collection(COLLECTION)
                .whereEqualTo("ownerId", ownerId)
                .get()
                .addOnSuccessListener(qs -> {
                    if (qs == null) {
                        return;
                    }
                    for (QueryDocumentSnapshot d : qs) {
                        d.getReference().delete()
                                .addOnFailureListener(e -> Log.w(TAG, "No se pudo borrar hex residual " + d.getId(), e));
                    }
                })
                .addOnFailureListener(e -> Log.w(TAG, "Consulta de limpieza hexParcels omitida (reglas/red)", e));
    }

    /** Nombre por defecto al reinicio: «Terreno » + 6 cifras aleatorias. */
    public static String newDefaultTerrenoName() {
        int n = 100000 + (int) (Math.random() * 900000);
        return "Apiario " + n;
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

    /** Nombres de terrenos en hilo de fondo: Room no se puede consultar desde la UI. */
    public void resolveDisplayNames(
            @Nullable Collection<String> hexIds, @NonNull Consumer<Map<String, String>> onMain) {
        ioExecutor.execute(() -> {
            Map<String, String> out = new HashMap<>();
            if (hexIds != null) {
                for (String id : hexIds) {
                    if (id == null || id.isEmpty()) {
                        continue;
                    }
                    out.put(id, getParcelDisplayNameSync(id));
                }
            }
            mainHandler.post(() -> onMain.accept(out));
        });
    }

    /**
     * Terreno inicial cerca de Barcelona. El hex es compartido: la selección
     * no lo excluye por tener ya un apiario de otro jugador.
     */
    @Nullable
    public HexParcel pickFreeStarterNearBarcelona(
            @Nullable List<HexParcel> parcels, @Nullable String ownerId) {
        if (parcels == null || parcels.isEmpty() || ownerId == null || ownerId.isEmpty()) {
            return null;
        }
        final double startLat = 41.3874;
        final double startLon = 2.1686;
        HexParcel preferred = HexParcelResolve.findContaining(parcels, startLat, startLon);
        if (isStarterCandidate(preferred)) {
            return preferred;
        }
        HexParcel best = null;
        double bestD = Double.MAX_VALUE;
        for (int i = 0; i < parcels.size(); i++) {
            HexParcel p = parcels.get(i);
            if (!isStarterCandidate(p)) {
                continue;
            }
            double dLat = p.centroidLat - startLat;
            double dLon = p.centroidLon - startLon;
            double d = dLat * dLat + dLon * dLon;
            if (d < bestD) {
                bestD = d;
                best = p;
            }
        }
        return best;
    }

    private static boolean isStarterCandidate(@Nullable HexParcel parcel) {
        if (parcel == null || parcel.id == null || parcel.id.isEmpty()) {
            return false;
        }
        return PlayableMapRegion.fromHexId(parcel.id) == PlayableMapRegion.IBERIA;
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
        row.isPrimary = dao.getPrimaryForOwnerSync(ownerId) == null;
        row.hasWarehouse = false;
        row.warehouseLevel = 0;
        dao.upsert(row);
        if (firestore != null) {
            Map<String, Object> m = new HashMap<>();
            m.put("ownerId", ownerId);
            m.put("hexId", hexId);
            m.put("parcelName", name);
            m.put("isPrimary", row.isPrimary);
            m.put("hasWarehouse", false);
            m.put("warehouseLevel", 0);
            try {
                Tasks.await(firestore.collection(COLLECTION)
                        .document(ownershipCloudId(hexId, ownerId, "default"))
                        .set(m, SetOptions.merge()));
            } catch (Exception e) {
                Log.w(TAG, "seedOwnership cloud skipped", e);
            }
        }
    }

    /** Partidas antiguas: el terreno principal pasa a tener almacén. */
    public void ensureStarterWarehouseSync(@Nullable String ownerId) {
        /* El almacén se compra a propósito; no se regala en el primer apiario. */
    }

    /** Tras cobrar: el apiario queda en el teléfono al momento y luego se publica. */
    private void publishOwnershipCloudThenLocal(
            String hexId,
            String ownerId,
            String parcelName,
            double siteLat,
            double siteLng,
            boolean withWarehouse) throws Exception {
        publishOwnershipCloudThenLocal(hexId, ownerId, UUID.randomUUID().toString(), parcelName,
                siteLat, siteLng, withWarehouse);
    }

    private void publishOwnershipCloudThenLocal(
            String hexId,
            String ownerId,
            String siteId,
            String parcelName,
            double siteLat,
            double siteLng,
            boolean withWarehouse) throws Exception {
        String name = sanitizeParcelName(parcelName);
        if (name.isEmpty()) {
            throw new IllegalArgumentException("Nombre de terreno vacío.");
        }
        String site = siteId != null && !siteId.isEmpty() ? siteId : UUID.randomUUID().toString();
        HexParcelOwnershipEntity row = new HexParcelOwnershipEntity();
        row.hexId = hexId;
        row.ownerId = ownerId;
        row.siteId = site;
        row.parcelName = name;
        row.isPrimary = dao.getPrimaryForOwnerSync(ownerId) == null;
        row.siteLat = siteLat;
        row.siteLng = siteLng;
        if (withWarehouse) {
            row.hasWarehouse = true;
            row.warehouseLevel = 1;
            row.warehouseLat = siteLat;
            row.warehouseLng = siteLng;
        }
        if (GameServer.enabled() && !pushOwnershipToGameServer(row)) {
            throw new java.io.IOException(
                    "No hay conexión con el servidor. No se puede realizar esta acción.");
        }
        dao.upsert(row);
        noteOwnershipKnown(ownerId);
        if (firestore != null) {
            Map<String, Object> m = new HashMap<>();
            m.put("ownerId", ownerId);
            m.put("hexId", hexId);
            m.put("siteId", site);
            m.put("parcelName", name);
            m.put("isPrimary", row.isPrimary);
            m.put("siteLat", row.siteLat);
            m.put("siteLng", row.siteLng);
            m.put("hasWarehouse", row.hasWarehouse);
            m.put("warehouseLevel", row.warehouseLevel);
            m.put("warehouseLat", row.warehouseLat);
            m.put("warehouseLng", row.warehouseLng);
            Tasks.await(firestore.collection(COLLECTION).document(ownershipCloudId(hexId, ownerId, site))
                    .set(m, SetOptions.merge()));
        }
    }

    @Nullable
    public String getPrimaryHexIdSync(@Nullable String ownerId) {
        if (ownerId == null || ownerId.isEmpty()) {
            return null;
        }
        HexParcelOwnershipEntity row = dao.getPrimaryForOwnerSync(ownerId);
        return row != null ? row.hexId : null;
    }

    public boolean isPrimaryHexSync(@Nullable String hexId) {
        return isPrimaryHexSync(hexId, null);
    }

    public boolean isPrimaryHexSync(@Nullable String hexId, @Nullable String ownerId) {
        if (hexId == null || hexId.isEmpty()) {
            return false;
        }
        HexParcelOwnershipEntity row = ownerId != null && !ownerId.isEmpty()
                ? dao.getByHexAndOwnerSync(hexId, ownerId)
                : dao.getByHexIdSync(hexId);
        return row != null && row.isPrimary;
    }

    /**
     * Si no hay terreno principal, marca el primero del dueño. Devuelve el hex o null.
     */
    @Nullable
    public String ensurePrimaryHexSync(@Nullable String ownerId) {
        if (ownerId == null || ownerId.isEmpty()) {
            return null;
        }
        HexParcelOwnershipEntity current = dao.getPrimaryForOwnerSync(ownerId);
        if (current != null && current.hexId != null && !current.hexId.isEmpty()) {
            return current.hexId;
        }
        List<HexParcelOwnershipEntity> all = dao.getAllForOwnerSync(ownerId);
        if (all == null || all.isEmpty()) {
            return null;
        }
        HexParcelOwnershipEntity first = all.get(0);
        if (first == null || first.hexId == null) {
            return null;
        }
        try {
            setPrimaryHexBlocking(ownerId, first.hexId);
        } catch (Exception e) {
            Log.e(TAG, "ensurePrimaryHexSync", e);
            return null;
        }
        return first.hexId;
    }

    public void buyWarehouse(String ownerId, String hexId, Consumer<String> onMainMessage) {
        buyWarehouse(ownerId, hexId, Double.NaN, Double.NaN, null, onMainMessage);
    }

    public void buyWarehouse(String ownerId, String hexId, double tapLat, double tapLng,
            String warehouseName, Consumer<String> onMainMessage) {
        ioExecutor.execute(() -> {
            String err = buyWarehouseBlocking(ownerId, hexId, tapLat, tapLng, warehouseName);
            mainHandler.post(() -> {
                if (onMainMessage != null) {
                    onMainMessage.accept(err);
                }
            });
        });
    }

    @Nullable
    public String buyWarehouseBlocking(String ownerId, String hexId) {
        return buyWarehouseBlocking(ownerId, hexId, Double.NaN, Double.NaN);
    }

    @Nullable
    public String buyWarehouseBlocking(String ownerId, String hexId, double tapLat, double tapLng) {
        return buyWarehouseBlocking(ownerId, hexId, tapLat, tapLng, null);
    }

    @Nullable
    public String buyWarehouseBlocking(String ownerId, String hexId, double tapLat, double tapLng,
            @Nullable String warehouseName) {
        if (GameServer.enabled() && !GameServer.isAvailable()) {
            return "No hay conexión con el servidor. No se puede realizar esta acción.";
        }
        String name = com.apiculture.simulator.domain.game.EntityNames.clean(warehouseName);
        if (name == null) {
            return "Pon un nombre de 2 a 32 letras.";
        }
        if (ownerId == null || ownerId.isEmpty() || hexId == null || hexId.isEmpty()) {
            return "Terreno no válido.";
        }
        List<HexParcelOwnershipEntity> owned = dao.getWarehousesForOwnerSync(ownerId);
        if (owned != null) {
            for (HexParcelOwnershipEntity other : owned) {
                if (other != null && other.hasWarehouse
                        && com.apiculture.simulator.domain.game.EntityNames.same(other.parcelName, name)) {
                    return "Ya tienes un obrador con ese nombre.";
                }
            }
        }
        if (Double.isNaN(tapLat) || Double.isNaN(tapLng)
                || (Math.abs(tapLat) < 1e-8 && Math.abs(tapLng) < 1e-8)) {
            return "Elige un punto en el mapa.";
        }
        List<HexParcelOwnershipEntity> mine = dao.listByHexAndOwnerSync(hexId, ownerId);
        if (mine != null) {
            for (HexParcelOwnershipEntity existing : mine) {
                if (existing != null && existing.hasWarehouse) {
                    return "Ya hay un obrador en este terreno.";
                }
            }
        }
        if (!economyRepository.trySpend(WarehouseRules.COST_B,
                "Construcción del obrador " + name)) {
            return "No tienes " + WarehouseRules.COST_B + " B.";
        }
        String site = UUID.randomUUID().toString();
        HexParcelOwnershipEntity row = new HexParcelOwnershipEntity();
        row.hexId = hexId;
        row.ownerId = ownerId;
        row.siteId = site;
        row.hasWarehouse = true;
        row.parcelName = name;
        row.warehouseLevel = 1;
        row.warehouseLat = tapLat;
        row.warehouseLng = tapLng;
        row.isPrimary = false;
        boolean firstOnHex = dao.listByHexSync(hexId) == null || dao.listByHexSync(hexId).isEmpty();
        try {
            persistOwnershipRowCloud(row);
            dao.upsert(row);
            if (firstOnHex) {
                hexFloraRepository.clearAllFlorasForHexBlocking(hexId);
                hexFloraRepository.addNativeMixReadyNowBlocking(hexId);
                syncHexParcelFlorasToCloudBlocking(hexId);
            }
            if (playerProgressRepository != null) {
                playerProgressRepository.addXp(ownerId, XpAwards.BUY_WAREHOUSE);
            }
            FleetStore.ensureStarter(appContext, ownerId, hexId);
        } catch (Exception e) {
            Log.e(TAG, "buyWarehouseBlocking", e);
            economyRepository.addToBalance(WarehouseRules.COST_B,
                    "Devolución de la construcción del obrador");
            try {
                dao.deleteByHexOwnerSite(hexId, ownerId, site);
            } catch (RuntimeException ignored) {
            }
            return e.getMessage() != null && !e.getMessage().isEmpty()
                    ? e.getMessage() : "No se pudo instalar el obrador.";
        }
        return null;
    }

    public void setApiarySite(String ownerId, String hexId, double tapLat, double tapLng,
            Consumer<String> onMainMessage) {
        ioExecutor.execute(() -> {
            String err = setApiarySiteBlocking(ownerId, hexId, tapLat, tapLng);
            mainHandler.post(() -> {
                if (onMainMessage != null) {
                    onMainMessage.accept(err);
                }
            });
        });
    }

    @Nullable
    public String setApiarySiteBlocking(String ownerId, String hexId, double tapLat, double tapLng) {
        if (GameServer.enabled() && !GameServer.isAvailable()) {
            return "No hay conexión con el servidor. No se puede realizar esta acción.";
        }
        if (ownerId == null || ownerId.isEmpty() || hexId == null || hexId.isEmpty()) {
            return "Terreno no válido.";
        }
        if (Double.isNaN(tapLat) || Double.isNaN(tapLng)
                || (Math.abs(tapLat) < 1e-8 && Math.abs(tapLng) < 1e-8)) {
            return "Elige un punto en el mapa.";
        }
        HexParcelOwnershipEntity row = dao.getByHexAndOwnerSync(hexId, ownerId);
        if (row == null || row.ownerId == null || !row.ownerId.equals(ownerId)) {
            return "Ese terreno no es tuyo.";
        }
        row.siteLat = tapLat;
        row.siteLng = tapLng;
        dao.upsert(row);
        if (firestore != null) {
            Map<String, Object> m = new HashMap<>();
            m.put("ownerId", row.ownerId);
            m.put("hexId", row.hexId);
            m.put("siteLat", row.siteLat);
            m.put("siteLng", row.siteLng);
            firestore.collection(COLLECTION).document(ownershipCloudId(row.hexId, row.ownerId, row.siteId))
                    .set(m, SetOptions.merge());
        }
        return null;
    }

    public void upgradeWarehouse(String ownerId, String hexId, Consumer<String> onMainMessage) {
        ioExecutor.execute(() -> {
            String err = upgradeWarehouseBlocking(ownerId, hexId);
            mainHandler.post(() -> {
                if (onMainMessage != null) {
                    onMainMessage.accept(err);
                }
            });
        });
    }

    @Nullable
    public String upgradeWarehouseBlocking(String ownerId, String hexId) {
        if (GameServer.enabled() && !GameServer.isAvailable()) {
            return "No hay conexión con el servidor. No se puede realizar esta acción.";
        }
        if (ownerId == null || ownerId.isEmpty() || hexId == null || hexId.isEmpty()) {
            return "Terreno no válido.";
        }
        HexParcelOwnershipEntity row = warehouseRow(ownerId, hexId, null);
        if (row == null || row.ownerId == null || !row.ownerId.equals(ownerId) || !row.hasWarehouse) {
            return "No hay obrador en este terreno.";
        }
        int from = WarehouseRules.levelOf(row);
        int playerLevel = playerProgressRepository != null
                ? playerProgressRepository.getLevel(ownerId) : from;
        if (from >= Math.max(1, playerLevel)) {
            return "Sube de nivel de jugador para mejorar el obrador.";
        }
        int cost = WarehouseRules.upgradeCostB(from);
        String warehouseName = row.parcelName == null || row.parcelName.trim().isEmpty()
                ? "obrador" : row.parcelName.trim();
        if (!economyRepository.trySpend(cost,
                "Mejora del obrador " + warehouseName + " al nivel " + (from + 1))) {
            return "No tienes " + cost + " B.";
        }
        row.warehouseLevel = from + 1;
        dao.upsert(row);
        persistOwnershipRowCloud(row);
        return null;
    }

    public void setPrimaryHex(String ownerId, String hexId, Consumer<String> onMainMessage) {
        ioExecutor.execute(() -> {
            try {
                String err = setPrimaryHexBlocking(ownerId, hexId);
                mainHandler.post(() -> {
                    if (onMainMessage != null) {
                        onMainMessage.accept(err);
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "setPrimaryHex", e);
                mainHandler.post(() -> {
                    if (onMainMessage != null) {
                        onMainMessage.accept(e.getMessage() != null
                                ? e.getMessage() : "No se pudo marcar el obrador.");
                    }
                });
            }
        });
    }

    @Nullable
    public String setPrimaryHexBlocking(String ownerId, String hexId) throws Exception {
        if (GameServer.enabled() && !GameServer.isAvailable()) {
            return "No hay conexión con el servidor. No se puede realizar esta acción.";
        }
        if (ownerId == null || ownerId.isEmpty() || hexId == null || hexId.isEmpty()) {
            return "Terreno no válido.";
        }
        HexParcelOwnershipEntity row = dao.getByHexAndOwnerSync(hexId, ownerId);
        if (row == null || row.ownerId == null || !row.ownerId.equals(ownerId)) {
            return "Ese terreno no es tuyo.";
        }
        List<HexParcelOwnershipEntity> all = dao.getAllForOwnerSync(ownerId);
        dao.clearPrimaryForOwner(ownerId);
        if (all != null) {
            for (HexParcelOwnershipEntity other : all) {
                if (other == null || other.hexId == null) {
                    continue;
                }
                other.isPrimary = hexId.equals(other.hexId);
                dao.upsert(other);
                persistPrimaryFlagCloud(other);
            }
        }
        return null;
    }

    private void persistPrimaryFlagCloud(HexParcelOwnershipEntity row) {
        if (firestore == null || row == null || row.hexId == null) {
            return;
        }
        Map<String, Object> m = new HashMap<>();
        m.put("isPrimary", row.isPrimary);
        m.put("ownerId", row.ownerId);
        m.put("hexId", row.hexId);
        firestore.collection(COLLECTION).document(row.hexId).set(m, SetOptions.merge());
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

    /** Cambia el nombre visible del apiario. El mensaje es null si se ha guardado. */
    public void renameApiary(@NonNull String hexId, @NonNull String ownerId,
            @Nullable String siteId, @Nullable String raw, @NonNull Consumer<String> onMain) {
        ioExecutor.execute(() -> {
            String name = com.apiculture.simulator.domain.game.EntityNames.clean(raw);
            if (name == null) {
                mainHandler.post(() -> onMain.accept("Escribe un nombre de al menos 2 letras."));
                return;
            }
            List<HexParcelOwnershipEntity> mine = dao.listByHexAndOwnerSync(hexId, ownerId);
            HexParcelOwnershipEntity row = null;
            if (mine != null) {
                for (HexParcelOwnershipEntity o : mine) {
                    if (o == null) {
                        continue;
                    }
                    if (siteId == null || siteId.isEmpty() || siteId.equals(o.siteId)) {
                        row = o;
                        break;
                    }
                }
            }
            if (row == null) {
                mainHandler.post(() -> onMain.accept("No se ha encontrado el apiario."));
                return;
            }
            row.parcelName = name;
            if (!persistOwnershipRowCloud(row)) {
                mainHandler.post(() -> onMain.accept("No se ha podido guardar el nombre."));
                return;
            }
            dao.upsert(row);
            mainHandler.post(() -> onMain.accept(null));
        });
    }

    /**
     * Instala un apiario: cobra siempre el precio del sitio (base + prima por flora nativa).
     * El hex solo define la flora compartida; no hay descuento por otro apiario en el mismo territorio.
     */
    public void purchaseHex(
            String hexId,
            String buyerId,
            String parcelName,
            int playerLevel,
            double siteLat,
            double siteLng,
            boolean withWarehouse,
            Consumer<String> onMainMessage) {
        if (GameServer.enabled() && !GameServer.isAvailable()) {
            mainHandler.post(() -> onMainMessage.accept(
                    "No hay conexión con el servidor. No se puede realizar esta acción."));
            return;
        }
        if (hexId == null || hexId.isEmpty() || buyerId == null || buyerId.isEmpty()) {
            mainHandler.post(() -> onMainMessage.accept("Sesión no válida."));
            return;
        }
        String nameToSave = com.apiculture.simulator.domain.game.EntityNames.clean(parcelName);
        if (nameToSave == null) {
            mainHandler.post(() -> onMainMessage.accept("Escribe un nombre de al menos 2 letras."));
            return;
        }
        HexParcel parcel = IberiaHexOverlayStore.findById(appContext, hexId);
        if (!ClimateUnlock.canBuyParcel(parcel, playerLevel)) {
            int need = ClimateUnlock.minLevelForParcel(parcel);
            String climate = ClimateUnlock.climateLabelForParcel(parcel);
            mainHandler.post(() -> onMainMessage.accept(
                    "Este terreno es de clima " + climate + ". Se desbloquea en el nivel " + need + "."));
            return;
        }
        java.util.List<String> mix = HexFlora.nativeMixForParcel(parcel);
        final int landPrice = FloraProgression.terrainPurchaseTotalEurosForNativeMix(mix);
        final int warehousePrice = withWarehouse ? WarehouseRules.COST_B : 0;
        final int totalPriceEuros = landPrice + warehousePrice;
        ioExecutor.execute(() -> {
            double spentAmount = 0.0;
            String newSiteId = UUID.randomUUID().toString();
            try {
                List<HexParcelOwnershipEntity> mine = dao.listByHexAndOwnerSync(hexId, buyerId);
                boolean alreadyWarehouse = false;
                if (mine != null) {
                    for (HexParcelOwnershipEntity row : mine) {
                        if (row != null && row.hasWarehouse) {
                            alreadyWarehouse = true;
                            break;
                        }
                    }
                }
                int pay = landPrice;
                if (withWarehouse && !alreadyWarehouse) {
                    pay += warehousePrice;
                }
                final int charge = pay;
                boolean firstOnHex = dao.listByHexSync(hexId) == null || dao.listByHexSync(hexId).isEmpty();
                String landConcept = (withWarehouse && !alreadyWarehouse
                        ? "Compra de terreno con obrador "
                        : "Compra de terreno ") + nameToSave;
                if (charge > 0 && !economyRepository.trySpend(charge, landConcept)) {
                    mainHandler.post(() -> onMainMessage.accept(
                            economyRepository.blockedReason(charge)));
                    return;
                }
                spentAmount = charge;
                publishOwnershipCloudThenLocal(hexId, buyerId, newSiteId, nameToSave, siteLat, siteLng, withWarehouse);
                ensurePrimaryHexSync(buyerId);
                if (firstOnHex) {
                    hexFloraRepository.clearAllFlorasForHexBlocking(hexId);
                    hexFloraRepository.addNativeMixReadyNowBlocking(hexId);
                    syncHexParcelFlorasToCloudBlocking(hexId);
                }
                if (playerProgressRepository != null && charge > 0) {
                    playerProgressRepository.addXp(buyerId, XpAwards.PLACE_APIARY);
                    if (withWarehouse && !alreadyWarehouse) {
                        playerProgressRepository.addXp(buyerId, XpAwards.BUY_WAREHOUSE);
                    }
                }
                if (withWarehouse && !alreadyWarehouse) {
                    FleetStore.ensureStarter(appContext, buyerId, hexId);
                }
                mainHandler.post(() -> onMainMessage.accept(null));
            } catch (Exception e) {
                Log.e(TAG, "purchaseHex", e);
                if (spentAmount > 0.0) {
                    economyRepository.addToBalance(spentAmount, "Devolución de la compra del terreno");
                }
                try {
                    dao.deleteByHexOwnerSite(hexId, buyerId, newSiteId);
                    GameServer.deleteHexParcel(ownershipCloudId(hexId, buyerId, newSiteId));
                } catch (RuntimeException ignored) {
                }
                String msg = e instanceof FirebaseFirestoreException
                        ? ((FirebaseFirestoreException) e).getMessage()
                        : e.getMessage();
                mainHandler.post(() -> onMainMessage.accept(
                        msg != null && !msg.isEmpty() ? msg : "No se pudo instalar el apiario."));
            }
        });
    }

    @NonNull
    public Map<String, HexNectarPool.NeighborSnap> foreignForageSnapsSync(@Nullable String ownerId) {
        Map<String, HexNectarPool.NeighborSnap> out = new HashMap<>();
        for (HexParcelOwnershipEntity row : dao.getAllSync()) {
            if (row == null || row.hexId == null) {
                continue;
            }
            if (ownerId != null && ownerId.equals(row.ownerId)) {
                continue;
            }
            HexForageSnapshot snap = HexForageSnapshot.fromJson(row.forageSnapshotJson);
            out.put(row.hexId, snap.toNeighborSnap(row.hexId, row.ownerId));
        }
        return out;
    }

    public void publishForageSnapshotBlocking(
            @Nullable String hexId,
            @Nullable String ownerId,
            @NonNull HexForageSnapshot snapshot) {
        if (hexId == null || hexId.isEmpty() || snapshot == null) {
            return;
        }
        HexParcelOwnershipEntity row = ownerId != null && !ownerId.isEmpty()
                ? dao.getByHexAndOwnerSync(hexId, ownerId)
                : dao.getByHexIdSync(hexId);
        if (row == null) {
            return;
        }
        if (ownerId != null && !ownerId.equals(row.ownerId)) {
            return;
        }
        row.forageDayKey = snapshot.dayKey;
        row.forageSnapshotJson = snapshot.toJson();
        dao.upsert(row);
        if (firestore == null) {
            return;
        }
        try {
            Tasks.await(firestore.collection(COLLECTION).document(hexId)
                    .set(snapshot.toFirestoreFields(), SetOptions.merge()));
        } catch (Exception e) {
            Log.w(TAG, "publishForageSnapshotBlocking", e);
        }
    }

    public void sellApiary(String ownerId, String hexId, @Nullable String siteId,
            Consumer<String> onMainMessage) {
        ioExecutor.execute(() -> {
            String err = sellApiaryBlocking(ownerId, hexId, siteId);
            mainHandler.post(() -> {
                if (onMainMessage != null) {
                    onMainMessage.accept(err);
                }
            });
        });
    }

    public void sellWarehouse(String ownerId, String hexId, @Nullable String siteId,
            Consumer<String> onMainMessage) {
        ioExecutor.execute(() -> {
            String err = sellWarehouseBlocking(ownerId, hexId, siteId);
            mainHandler.post(() -> {
                if (onMainMessage != null) {
                    onMainMessage.accept(err);
                }
            });
        });
    }

    @NonNull
    public double[] apiaryPinForOwnerBlocking(@Nullable String ownerId, @Nullable HexParcel hex) {
        return apiaryPinForOwnerBlocking(ownerId, hex, null);
    }

    @NonNull
    public double[] apiaryPinForOwnerBlocking(@Nullable String ownerId, @Nullable HexParcel hex,
            @Nullable String siteId) {
        if (hex == null) {
            return new double[]{0, 0};
        }
        List<HexParcelOwnershipEntity> mine = ownerId != null && hex.id != null
                ? dao.listByHexAndOwnerSync(hex.id, ownerId)
                : null;
        return HexParcelRandomPoint.pinForOwnedApiary(hex, mine, siteId, Double.NaN, Double.NaN);
    }

    public int countHivesAtSiteBlocking(@Nullable String ownerId, @Nullable String hexId,
            @Nullable String siteId) {
        if (ownerId == null || ownerId.isEmpty() || hexId == null || hexId.isEmpty()) {
            return 0;
        }
        HexParcel parcel = IberiaHexOverlayStore.findById(appContext, hexId);
        List<HexParcelOwnershipEntity> mine = dao.listByHexAndOwnerSync(hexId, ownerId);
        if (mine == null || mine.isEmpty()) {
            return 0;
        }
        String want = siteId != null && !siteId.isEmpty() ? siteId : "default";
        int n = 0;
        for (HiveEntity hive : AppDatabase.getInstance(appContext).hiveDao().getByHexIdSync(hexId)) {
            if (hive == null || hive.inWarehouse || !ownerId.equals(hive.ownerId)) {
                continue;
            }
            if (HexApiary.hiveOnSite(hive, hexId, want, parcel, mine)) {
                n++;
            }
        }
        return n;
    }

    @NonNull
    public List<HexParcelOwnershipEntity> listSitesOnHexBlocking(
            @Nullable String ownerId, @Nullable String hexId) {
        if (ownerId == null || hexId == null) {
            return new ArrayList<>();
        }
        List<HexParcelOwnershipEntity> mine = dao.listByHexAndOwnerSync(hexId, ownerId);
        return mine != null ? mine : new ArrayList<>();
    }

    @Nullable
    public String sellApiaryBlocking(String ownerId, String hexId, @Nullable String siteId) {
        if (GameServer.enabled() && !GameServer.isAvailable()) {
            return "No hay conexión con el servidor. No se puede realizar esta acción.";
        }
        HexParcelOwnershipEntity row = siteRow(ownerId, hexId, siteId);
        if (row == null) {
            return "Ese apiario no es tuyo.";
        }
        if (countHivesAtSiteBlocking(ownerId, hexId, row.siteId) > 0) {
            return "HAS_HIVES";
        }
        int paid = FloraProgression.terrainPurchaseTotalEurosForNativeMix(
                HexFlora.nativeMixForParcel(IberiaHexOverlayStore.findById(appContext, hexId)));
        int refund = WarehouseRules.sellRefundB(paid);
        if (row.hasWarehouse) {
            row.siteLat = row.warehouseLat;
            row.siteLng = row.warehouseLng;
            if (!persistOwnershipRowCloud(row)) {
                return "No hay conexión con el servidor. No se puede realizar esta acción.";
            }
            dao.upsert(row);
        } else if (!deleteOwnershipRow(row)) {
            return "No hay conexión con el servidor. No se puede realizar esta acción.";
        }
        economyRepository.addToBalance(refund, "Venta del apiario");
        ensurePrimaryHexSync(ownerId);
        return null;
    }

    @Nullable
    public String sellWarehouseBlocking(String ownerId, String hexId, @Nullable String siteId) {
        if (GameServer.enabled() && !GameServer.isAvailable()) {
            return "No hay conexión con el servidor. No se puede realizar esta acción.";
        }
        HexParcelOwnershipEntity row = warehouseRow(ownerId, hexId, siteId);
        if (row == null || !row.hasWarehouse) {
            return "No hay obrador en este punto.";
        }
        if (!HoneyLogistics.cargoTouchingHex(appContext, ownerId, hexId).isEmpty()) {
            return "HAS_TRIPS";
        }
        double[] dock = HexParcelRandomPoint.warehouseOf(
                IberiaHexOverlayStore.findById(appContext, hexId), row);
        if (!HoneyLogistics.trucksNearDock(appContext, ownerId, dock[0], dock[1]).isEmpty()) {
            return "HAS_TRIPS";
        }
        int refund = WarehouseRules.warehouseSellRefundB(WarehouseRules.levelOf(row));
        boolean keepApiary = countHivesAtSiteBlocking(ownerId, hexId, row.siteId) > 0
                || WarehouseRules.isApiarySite(row);
        if (keepApiary) {
            row.hasWarehouse = false;
            row.warehouseLevel = 0;
            row.warehouseLat = 0;
            row.warehouseLng = 0;
            if (!persistOwnershipRowCloud(row)) {
                return "No hay conexión con el servidor. No se puede realizar esta acción.";
            }
            dao.upsert(row);
        } else if (!deleteOwnershipRow(row)) {
            return "No hay conexión con el servidor. No se puede realizar esta acción.";
        }
        economyRepository.addToBalance(refund, "Venta del obrador");
        ensurePrimaryHexSync(ownerId);
        return null;
    }

    /** Quita almacenes fantasma creados al mejorar: mismo hex, sin edificio propio. */
    public void repairSplitWarehouseSites(@Nullable String ownerId) {
        if (ownerId == null || ownerId.isEmpty()) {
            return;
        }
        ioExecutor.execute(() -> repairSplitWarehouseSitesBlocking(ownerId));
    }

    private void repairSplitWarehouseSitesBlocking(@NonNull String ownerId) {
        List<HexParcelOwnershipEntity> all = dao.getAllForOwnerSync(ownerId);
        if (all == null || all.isEmpty()) {
            return;
        }
        Map<String, List<HexParcelOwnershipEntity>> byHex = new HashMap<>();
        for (HexParcelOwnershipEntity row : all) {
            if (row == null || row.hexId == null) {
                continue;
            }
            byHex.computeIfAbsent(row.hexId, key -> new ArrayList<>()).add(row);
        }
        for (List<HexParcelOwnershipEntity> group : byHex.values()) {
            boolean placed = false;
            for (HexParcelOwnershipEntity row : group) {
                if (row.hasWarehouse && warehousePlaced(row)) {
                    placed = true;
                    break;
                }
            }
            if (!placed) {
                continue;
            }
            for (HexParcelOwnershipEntity row : group) {
                if (!row.hasWarehouse || warehousePlaced(row)) {
                    continue;
                }
                boolean apiary = countHivesAtSiteBlocking(ownerId, row.hexId, row.siteId) > 0
                        || Math.abs(row.siteLat) > 1e-8 || Math.abs(row.siteLng) > 1e-8;
                if (apiary) {
                    row.hasWarehouse = false;
                    row.warehouseLevel = 0;
                    row.warehouseLat = 0;
                    row.warehouseLng = 0;
                    dao.upsert(row);
                    persistOwnershipRowCloud(row);
                } else {
                    deleteOwnershipRow(row);
                }
            }
        }
    }

    private static boolean warehousePlaced(@NonNull HexParcelOwnershipEntity row) {
        return Math.abs(row.warehouseLat) > 1e-8 || Math.abs(row.warehouseLng) > 1e-8;
    }

    @Nullable
    private HexParcelOwnershipEntity siteRow(String ownerId, String hexId, @Nullable String siteId) {
        if (ownerId == null || hexId == null) {
            return null;
        }
        if (siteId != null && !siteId.isEmpty()) {
            HexParcelOwnershipEntity row = dao.getByHexOwnerSiteSync(hexId, ownerId, siteId);
            if (row != null) {
                return row;
            }
        }
        return dao.getByHexAndOwnerSync(hexId, ownerId);
    }

    @Nullable
    private HexParcelOwnershipEntity warehouseRow(String ownerId, String hexId, @Nullable String siteId) {
        HexParcelOwnershipEntity named = siteRow(ownerId, hexId, siteId);
        if (named != null && named.hasWarehouse) {
            return named;
        }
        List<HexParcelOwnershipEntity> mine = dao.listByHexAndOwnerSync(hexId, ownerId);
        if (mine == null) {
            return null;
        }
        for (HexParcelOwnershipEntity row : mine) {
            if (row != null && row.hasWarehouse) {
                return row;
            }
        }
        return null;
    }

    /**
     * Sube los apiarios locales al servidor y baja los que el teléfono aún no tiene.
     * No borra filas locales si el servidor va por detrás.
     */
    public void syncOwnershipWithGameServerBlocking() {
        com.apiculture.simulator.data.session.SignedInUser user =
                com.apiculture.simulator.data.session.PlayerAuth.getInstance().getCurrentUser();
        if (user == null || !GameServer.enabled()) {
            return;
        }
        String uid = user.getUid();
        dropLocalCopiesOfForeignSites(uid);
        List<HexParcelOwnershipEntity> everyone = dao.getAllSync();
        if (everyone != null) {
            for (HexParcelOwnershipEntity row : everyone) {
                if (row == null || row.hexId == null || uid.equals(row.ownerId)) {
                    continue;
                }
                String site = row.siteId != null && !row.siteId.isEmpty() ? row.siteId : "default";
                dao.deleteByHexOwnerSite(row.hexId, row.ownerId, site);
            }
        }
        List<HexParcelOwnershipEntity> local = dao.getAllForOwnerSync(uid);
        HashSet<String> localIds = new HashSet<>();
        if (local != null) {
            for (HexParcelOwnershipEntity row : local) {
                if (row == null || row.hexId == null) {
                    continue;
                }
                localIds.add(ownershipCloudId(row.hexId, row.ownerId, row.siteId));
                pushOwnershipToGameServer(row);
            }
        }
        org.json.JSONArray remote = GameServer.hexParcels(uid);
        if (remote == null) {
            if (local != null && !local.isEmpty()) {
                noteOwnershipKnown(uid);
            }
            return;
        }
        for (int i = 0; i < remote.length(); i++) {
            org.json.JSONObject item = remote.optJSONObject(i);
            if (item == null) {
                continue;
            }
            String id = item.optString("id", "");
            if (localIds.contains(id)) {
                continue;
            }
            String hexId = item.optString("hexId", "");
            String ownerId = item.optString("ownerId", uid);
            String siteId = item.optString("siteId", "default");
            if (hexId.isEmpty() || !uid.equals(ownerId)) {
                continue;
            }
            if (siteId.isEmpty()) {
                siteId = "default";
            }
            if (dao.getByHexOwnerSiteSync(hexId, ownerId, siteId) != null) {
                continue;
            }
            HexParcelOwnershipEntity row = new HexParcelOwnershipEntity();
            row.hexId = hexId;
            row.ownerId = ownerId;
            row.siteId = siteId;
            String name = item.optString("parcelName", "");
            row.parcelName = name.isEmpty() ? null : name;
            row.isPrimary = item.optBoolean("isPrimary", false);
            row.hasWarehouse = item.optBoolean("hasWarehouse", false);
            row.warehouseLevel = item.optInt("warehouseLevel", row.hasWarehouse ? 1 : 0);
            row.siteLat = item.optDouble("siteLat", 0);
            row.siteLng = item.optDouble("siteLng", 0);
            row.warehouseLat = item.optDouble("warehouseLat", 0);
            row.warehouseLng = item.optDouble("warehouseLng", 0);
            row.forageDayKey = item.optInt("forageDayKey", 0);
            String forage = item.optString("forageSnapshotJson", "");
            row.forageSnapshotJson = forage.isEmpty() ? null : forage;
            dao.upsert(row);
        }
        boolean anyLocal = local != null && !local.isEmpty();
        if (anyLocal || remote.length() > 0) {
            noteOwnershipKnown(uid);
        }
        pullForeignApiaries(uid);
    }

    /** Quita del jugador las copias locales de un apiario que en el servidor es de otro. */
    private void dropLocalCopiesOfForeignSites(@NonNull String uid) {
        org.json.JSONArray pins = GameServer.mapApiaries();
        if (pins == null) {
            return;
        }
        java.util.HashMap<String, String> siteOwner = new java.util.HashMap<>();
        for (int i = 0; i < pins.length(); i++) {
            org.json.JSONObject item = pins.optJSONObject(i);
            if (item == null) {
                continue;
            }
            String siteId = item.optString("siteId", "");
            String ownerId = item.optString("ownerId", "");
            if (!siteId.isEmpty() && !ownerId.isEmpty()) {
                siteOwner.put(siteId, ownerId);
            }
        }
        List<HexParcelOwnershipEntity> mine = dao.getAllForOwnerSync(uid);
        if (mine == null) {
            return;
        }
        for (HexParcelOwnershipEntity row : mine) {
            if (row == null || row.siteId == null) {
                continue;
            }
            String ownerId = siteOwner.get(row.siteId);
            if (ownerId != null && !uid.equals(ownerId)) {
                String site = row.siteId.isEmpty() ? "default" : row.siteId;
                dao.deleteByHexOwnerSite(row.hexId, row.ownerId, site);
            }
        }
    }

    /** Pines grises del mapa. No se suben ni se tratan como apiarios propios. */
    private void pullForeignApiaries(@NonNull String uid) {
        org.json.JSONArray pins = GameServer.mapApiaries();
        if (pins == null) {
            return;
        }
        for (int i = 0; i < pins.length(); i++) {
            org.json.JSONObject item = pins.optJSONObject(i);
            if (item == null) {
                continue;
            }
            String ownerId = item.optString("ownerId", "");
            String hexId = item.optString("hexId", "");
            rememberPlayerName(ownerId, item.optString("playerName", ""));
            if (hexId.isEmpty() || ownerId.isEmpty() || uid.equals(ownerId)) {
                continue;
            }
            String siteId = item.optString("siteId", "default");
            if (siteId.isEmpty()) {
                siteId = "default";
            }
            HexParcelOwnershipEntity row = new HexParcelOwnershipEntity();
            row.hexId = hexId;
            row.ownerId = ownerId;
            row.siteId = siteId;
            String name = item.optString("parcelName", "");
            row.parcelName = name.isEmpty() ? null : name;
            row.isPrimary = item.optBoolean("isPrimary", false);
            row.hasWarehouse = item.optBoolean("hasWarehouse", false);
            row.warehouseLevel = item.optInt("warehouseLevel", 0);
            row.siteLat = item.optDouble("siteLat", 0);
            row.siteLng = item.optDouble("siteLng", 0);
            row.warehouseLat = item.optDouble("warehouseLat", 0);
            row.warehouseLng = item.optDouble("warehouseLng", 0);
            dao.upsert(row);
        }
    }

    private boolean pushOwnershipToGameServer(@Nullable HexParcelOwnershipEntity row) {
        if (row == null || row.hexId == null || row.ownerId == null || !GameServer.enabled()) {
            return !GameServer.enabled();
        }
        try {
            org.json.JSONObject body = new org.json.JSONObject();
            body.put("hexId", row.hexId);
            body.put("ownerId", row.ownerId);
            String site = row.siteId != null && !row.siteId.isEmpty() ? row.siteId : "default";
            body.put("siteId", site);
            body.put("parcelName", row.parcelName);
            body.put("forageDayKey", row.forageDayKey);
            body.put("forageSnapshotJson", row.forageSnapshotJson);
            body.put("isPrimary", row.isPrimary);
            body.put("hasWarehouse", row.hasWarehouse);
            body.put("warehouseLevel", row.warehouseLevel);
            body.put("siteLat", row.siteLat);
            body.put("siteLng", row.siteLng);
            body.put("warehouseLat", row.warehouseLat);
            body.put("warehouseLng", row.warehouseLng);
            return GameServer.pushHexParcel(body, ownershipCloudId(row.hexId, row.ownerId, site));
        } catch (Exception e) {
            Log.w(TAG, "pushOwnershipToGameServer", e);
            return false;
        }
    }

    private boolean persistOwnershipRowCloud(@NonNull HexParcelOwnershipEntity row) {
        if (!pushOwnershipToGameServer(row)) {
            return false;
        }
        if (firestore == null) {
            return true;
        }
        Map<String, Object> m = new HashMap<>();
        m.put("ownerId", row.ownerId);
        m.put("hexId", row.hexId);
        m.put("siteId", row.siteId);
        m.put("parcelName", row.parcelName);
        m.put("isPrimary", row.isPrimary);
        m.put("hasWarehouse", row.hasWarehouse);
        m.put("warehouseLevel", row.warehouseLevel);
        m.put("siteLat", row.siteLat);
        m.put("siteLng", row.siteLng);
        m.put("warehouseLat", row.warehouseLat);
        m.put("warehouseLng", row.warehouseLng);
        try {
            Tasks.await(firestore.collection(COLLECTION)
                    .document(ownershipCloudId(row.hexId, row.ownerId, row.siteId))
                    .set(m, SetOptions.merge()));
        } catch (Exception e) {
            Log.w(TAG, "persistOwnershipRowCloud", e);
        }
        return true;
    }

    private boolean deleteOwnershipRow(@NonNull HexParcelOwnershipEntity row) {
        String site = row.siteId != null ? row.siteId : "default";
        if (GameServer.enabled()
                && !GameServer.deleteHexParcel(ownershipCloudId(row.hexId, row.ownerId, site))) {
            return false;
        }
        dao.deleteByHexOwnerSite(row.hexId, row.ownerId, site);
        if (firestore == null) {
            return true;
        }
        try {
            Tasks.await(firestore.collection(COLLECTION)
                    .document(ownershipCloudId(row.hexId, row.ownerId, row.siteId))
                    .delete());
        } catch (Exception e) {
            Log.w(TAG, "deleteOwnershipRow", e);
        }
        if (row.siteId == null || "default".equals(row.siteId)) {
            try {
                Tasks.await(firestore.collection(COLLECTION).document(row.hexId).delete());
            } catch (Exception ignored) {
            }
        }
        return true;
    }
}
