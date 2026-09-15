package com.apiculture.simulator.data.repository;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.apiculture.simulator.domain.admin.AdminRoles;
import com.apiculture.simulator.domain.game.GameCalendar;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.WriteBatch;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Reset masivo de partida (admin): limpia progreso en nube de todos los jugadores
 * publicados en {@code players}, publica una generación de forzado para clientes/bots,
 * y conserva perfiles (nombre / marca).
 */
public class AdminGameResetRepository {

    public static final String COL_EVENTS = GlobalEventRepository.COL_EVENTS;
    public static final String DOC_RESET = "game_reset";
    public static final String FIELD_GENERATION = "generation";
    public static final String FIELD_ISSUED_AT_MS = "issuedAtMs";
    public static final String FIELD_ISSUED_BY = "issuedByUid";

    private static final String PREFS = "admin_game_reset_v1";
    private static final String KEY_APPLIED_GEN = "applied_generation";

    private final Context app;
    @Nullable
    private final FirebaseFirestore firestore;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    public AdminGameResetRepository(Context app, @Nullable FirebaseFirestore firestore) {
        this.app = app.getApplicationContext();
        this.firestore = firestore;
    }

    public static long getAppliedGeneration(@NonNull Context ctx) {
        return prefs(ctx).getLong(KEY_APPLIED_GEN, 0L);
    }

    public static void setAppliedGeneration(@NonNull Context ctx, long generation) {
        prefs(ctx).edit().putLong(KEY_APPLIED_GEN, Math.max(0L, generation)).commit();
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Lee la generación remota (0 si no hay doc / error). */
    public void fetchRemoteGeneration(@NonNull Consumer<Long> onMain) {
        if (firestore == null) {
            main.post(() -> onMain.accept(0L));
            return;
        }
        io.execute(() -> {
            long gen = 0L;
            try {
                DocumentSnapshot snap = Tasks.await(
                        firestore.collection(COL_EVENTS).document(DOC_RESET).get());
                if (snap.exists()) {
                    Object g = snap.get(FIELD_GENERATION);
                    if (g instanceof Number) {
                        gen = ((Number) g).longValue();
                    }
                }
            } catch (Exception ignored) {
            }
            long out = gen;
            main.post(() -> onMain.accept(out));
        });
    }

    /**
     * Wipe de nube para todos los docs en {@code players} + bump de generación.
     * {@code onMainMessage}: null = éxito (mensaje informativo en {@code onMainOk});
     * si falla, texto de error.
     */
    public void issueGlobalPlayerReset(@NonNull String adminUid,
            @NonNull Consumer<String> onMainMessage,
            @NonNull Consumer<String> onMainOk) {
        if (firestore == null) {
            main.post(() -> onMainMessage.accept("Firestore no disponible."));
            return;
        }
        if (adminUid == null || adminUid.isEmpty()) {
            main.post(() -> onMainMessage.accept("Sesión no válida."));
            return;
        }
        io.execute(() -> {
            try {
                DocumentSnapshot nameDoc = Tasks.await(
                        firestore.collection("uniquePlayerNames")
                                .document(AdminRoles.uniqueNameDocId())
                                .get());
                if (!nameDoc.exists()) {
                    main.post(() -> onMainMessage.accept("No autorizado."));
                    return;
                }
                String owner = nameDoc.getString("ownerUid");
                if (owner == null || !owner.equals(adminUid)) {
                    main.post(() -> onMainMessage.accept("No autorizado."));
                    return;
                }

                long currentGen = 0L;
                DocumentSnapshot resetSnap = Tasks.await(
                        firestore.collection(COL_EVENTS).document(DOC_RESET).get());
                if (resetSnap.exists()) {
                    Object g = resetSnap.get(FIELD_GENERATION);
                    if (g instanceof Number) {
                        currentGen = ((Number) g).longValue();
                    }
                }
                long nextGen = currentGen + 1L;

                QuerySnapshot playersSnap = Tasks.await(firestore.collection("players").get());
                Set<String> uids = new HashSet<>();
                for (DocumentSnapshot p : playersSnap.getDocuments()) {
                    if (p != null && p.getId() != null && !p.getId().isEmpty()) {
                        uids.add(p.getId());
                    }
                }
                uids.add(adminUid);

                int hiveDeletes = 0;
                int parcelDeletes = 0;
                int todayKey = GameCalendar.toDayKey(LocalDate.now(GameCalendar.userTimeZone()));

                for (String uid : uids) {
                    hiveDeletes += deleteOwnerHivesBlocking(uid);
                    parcelDeletes += deleteOwnerParcelsBlocking(uid);
                    mergeUserStarterBlocking(uid, todayKey);
                    mergePlayerStarterBlocking(uid);
                }

                Map<String, Object> resetDoc = new HashMap<>();
                resetDoc.put(FIELD_GENERATION, nextGen);
                resetDoc.put(FIELD_ISSUED_AT_MS, System.currentTimeMillis());
                resetDoc.put(FIELD_ISSUED_BY, adminUid);
                Tasks.await(firestore.collection(COL_EVENTS).document(DOC_RESET)
                        .set(resetDoc, SetOptions.merge()));

                int players = uids.size();
                int hives = hiveDeletes;
                int parcels = parcelDeletes;
                long gen = nextGen;
                main.post(() -> onMainOk.accept(
                        "Reset global #" + gen + ": " + players + " jugadores, "
                                + hives + " colmenas y " + parcels + " terrenos borrados en nube. "
                                + "Cada app se reinicia al abrir; los bots en el próximo tick."));
                main.post(() -> onMainMessage.accept(null));
            } catch (Exception e) {
                String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                main.post(() -> onMainMessage.accept("Reset global falló: " + msg));
            }
        });
    }

    private int deleteOwnerHivesBlocking(@NonNull String uid) throws Exception {
        QuerySnapshot snap = Tasks.await(
                firestore.collection("hives").whereEqualTo("ownerId", uid).get());
        int n = 0;
        for (QueryDocumentSnapshot hive : snap) {
            deleteDailyYieldsBlocking(hive.getId());
            Tasks.await(hive.getReference().delete());
            n++;
        }
        return n;
    }

    private void deleteDailyYieldsBlocking(@NonNull String hiveId) throws Exception {
        QuerySnapshot yields = Tasks.await(
                firestore.collection("hives").document(hiveId).collection("dailyYields").get());
        List<DocumentSnapshot> docs = new ArrayList<>(yields.getDocuments());
        for (int i = 0; i < docs.size(); i += 400) {
            WriteBatch batch = firestore.batch();
            int end = Math.min(i + 400, docs.size());
            for (int j = i; j < end; j++) {
                batch.delete(docs.get(j).getReference());
            }
            Tasks.await(batch.commit());
        }
    }

    private int deleteOwnerParcelsBlocking(@NonNull String uid) throws Exception {
        QuerySnapshot snap = Tasks.await(
                firestore.collection("hexParcels").whereEqualTo("ownerId", uid).get());
        int n = 0;
        WriteBatch batch = firestore.batch();
        int inBatch = 0;
        for (QueryDocumentSnapshot doc : snap) {
            batch.delete(doc.getReference());
            inBatch++;
            n++;
            if (inBatch >= 400) {
                Tasks.await(batch.commit());
                batch = firestore.batch();
                inBatch = 0;
            }
        }
        if (inBatch > 0) {
            Tasks.await(batch.commit());
        }
        return n;
    }

    private void mergeUserStarterBlocking(@NonNull String uid, int todayKey) throws Exception {
        Map<String, Object> m = new HashMap<>();
        m.put(UserGameStateRepository.FIELD_ECONOMY_BALANCE,
                EconomyRepository.DEFAULT_STARTING_BALANCE_EUR);
        m.put(UserGameStateRepository.FIELD_ECONOMY_BUCKETS_JSON, "{}");
        m.put(UserGameStateRepository.FIELD_ECONOMY_SOLD_TOTAL, 0.0);
        m.put(UserGameStateRepository.FIELD_ECONOMY_SOLD_BY_FLORA_JSON, "{}");
        m.put(UserGameStateRepository.FIELD_PLAYER_LEVEL, 0);
        m.put(UserGameStateRepository.FIELD_PLAYER_XP, 0);
        m.put("invTreatments", 0);
        m.put("invFeed", 0);
        m.put("invQueensJson", "[]");
        m.put("invQueens100", 0);
        Tasks.await(firestore.collection("users").document(uid).set(m, SetOptions.merge()));

        Map<String, Object> prod = new HashMap<>();
        prod.put("gameStartDayKey", todayKey);
        prod.put("lastProcessedProductionDayKey", 0);
        Tasks.await(firestore.collection("users").document(uid)
                .collection("meta").document("productionState")
                .set(prod, SetOptions.merge()));
    }

    private void mergePlayerStarterBlocking(@NonNull String uid) throws Exception {
        Map<String, Object> m = new HashMap<>();
        m.put("level", 0);
        m.put("xp", 0);
        m.put("honeyStockKg", 0.0);
        m.put("totalHoneyKg", 0.0);
        m.put("honeySoldKgTotal", 0.0);
        m.put("honeySoldKgByFlora", new HashMap<String, Double>());
        m.put("hiveCount", 0);
        m.put("adultBeeCount", 0);
        Tasks.await(firestore.collection("players").document(uid).set(m, SetOptions.merge()));
    }
}
