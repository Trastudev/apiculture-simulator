package com.apiculture.simulator.data.repository;

import android.content.Context;
import android.os.Handler;

import androidx.annotation.Nullable;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Sincroniza saldo, miel en almacén, nivel y XP del jugador en {@code users/{uid}} para jugar en varios dispositivos.
 */
public class UserGameStateRepository {

    static final String FIELD_ECONOMY_BALANCE = "economyBalanceEur";
    static final String FIELD_ECONOMY_BUCKETS_JSON = "economyHoneyBucketsJson";
    static final String FIELD_ECONOMY_SOLD_TOTAL = "economyHoneySoldKgTotal";
    static final String FIELD_ECONOMY_SOLD_BY_FLORA_JSON = "economyHoneySoldByFloraJson";
    static final String FIELD_PLAYER_LEVEL = "playerLevel";
    static final String FIELD_PLAYER_XP = "playerXp";
    private static final String FIELD_UPDATED = "gameStateUpdatedAt";

    private static final long PUSH_DEBOUNCE_MS = 900L;

    private final FirebaseFirestore firestore;
    private final EconomyRepository economy;
    private final PlayerProgressRepository progress;
    private final Handler mainHandler;
    private final Context app;
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    private final Runnable debouncedPush = this::flushDebouncedPush;
    @Nullable private String pendingPushUid;

    public UserGameStateRepository(
            Context app,
            @Nullable FirebaseFirestore firestore,
            EconomyRepository economy,
            PlayerProgressRepository progress,
            Handler mainHandler) {
        this.app = app.getApplicationContext();
        this.firestore = firestore;
        this.economy = economy;
        this.progress = progress;
        this.mainHandler = mainHandler;
    }

    /**
     * Descarga el estado de juego desde Firestore, lo aplica en prefs (hilo IO) y ejecuta {@code onMainAfterPull} en el hilo principal.
     */
    public void pullAndApplyThen(@Nullable String uid, Runnable onMainAfterPull) {
        if (uid == null || uid.isEmpty() || firestore == null) {
            mainHandler.post(onMainAfterPull);
            return;
        }
        io.execute(() -> {
            try {
                DocumentSnapshot snap = Tasks.await(
                        firestore.collection("users").document(uid).get());
                applySnapshotToLocal(uid, snap);
            } catch (Exception ignored) {
            }
            mainHandler.post(onMainAfterPull);
        });
    }

    private void applySnapshotToLocal(String uid, DocumentSnapshot snap) {
        if (!snap.exists()) {
            pushSnapshotSync(uid);
            return;
        }
        Object balObj = snap.get(FIELD_ECONOMY_BALANCE);
        String buckets = snap.getString(FIELD_ECONOMY_BUCKETS_JSON);
        boolean hasBuckets = buckets != null && !buckets.trim().isEmpty();
        Object soldTotalObj = snap.get(FIELD_ECONOMY_SOLD_TOTAL);
        String soldByFlora = snap.getString(FIELD_ECONOMY_SOLD_BY_FLORA_JSON);
        boolean hasSold = soldTotalObj instanceof Number
                || (soldByFlora != null && !soldByFlora.trim().isEmpty());
        if (balObj instanceof Number || hasBuckets || hasSold) {
            double b = balObj instanceof Number ? ((Number) balObj).doubleValue() : economy.getBalance();
            Double soldTotal = soldTotalObj instanceof Number
                    ? ((Number) soldTotalObj).doubleValue() : null;
            economy.applyFromCloud(b, hasBuckets ? buckets : null, soldTotal,
                    hasSold ? soldByFlora : null);
        }
        Integer lvl = intField(snap, FIELD_PLAYER_LEVEL);
        Integer xpVal = intField(snap, FIELD_PLAYER_XP);
        if (lvl != null || xpVal != null) {
            int level = lvl != null ? lvl : progress.getLevel(uid);
            int xp = xpVal != null ? xpVal : progress.getXp(uid);
            progress.applyFromCloud(uid, level, xp);
        }
        EventInventoryStore.applyFromCloudIfHigher(snap, app);
        boolean hasAnyGameField = balObj instanceof Number || hasBuckets || hasSold
                || lvl != null || xpVal != null;
        if (!hasAnyGameField) {
            pushSnapshotSync(uid);
        }
    }

    @Nullable
    private static Integer intField(DocumentSnapshot snap, String key) {
        Object v = snap.get(key);
        if (v instanceof Number) {
            return ((Number) v).intValue();
        }
        return null;
    }

    /** Programa un guardado con debounce (economía / XP cambian a menudo). */
    public void enqueuePush(@Nullable String uid) {
        if (uid == null || uid.isEmpty() || firestore == null) {
            return;
        }
        pendingPushUid = uid;
        mainHandler.removeCallbacks(debouncedPush);
        mainHandler.postDelayed(debouncedPush, PUSH_DEBOUNCE_MS);
    }

    private void flushDebouncedPush() {
        String uid = pendingPushUid;
        pendingPushUid = null;
        if (uid != null) {
            pushSnapshot(uid);
        }
    }

    /** Guarda de inmediato (p. ej. al pausar la actividad o tras reiniciar juego). */
    public void pushImmediate(@Nullable String uid) {
        if (uid == null || uid.isEmpty() || firestore == null) {
            return;
        }
        mainHandler.removeCallbacks(debouncedPush);
        pendingPushUid = null;
        pushSnapshot(uid);
    }

    public void pushSnapshot(@Nullable String uid) {
        if (uid == null || uid.isEmpty() || firestore == null) {
            return;
        }
        io.execute(() -> pushSnapshotSync(uid));
    }

    private void pushSnapshotSync(String uid) {
        try {
            Map<String, Object> m = new HashMap<>();
            m.put(FIELD_ECONOMY_BALANCE, economy.getBalance());
            m.put(FIELD_ECONOMY_BUCKETS_JSON, economy.snapshotHoneyBucketsJsonForCloud());
            m.put(FIELD_ECONOMY_SOLD_TOTAL, economy.getHoneySoldTotalKg());
            m.put(FIELD_ECONOMY_SOLD_BY_FLORA_JSON, economy.snapshotHoneySoldByFloraJsonForCloud());
            m.put(FIELD_PLAYER_LEVEL, progress.getLevel(uid));
            m.put(FIELD_PLAYER_XP, progress.getXp(uid));
            m.put(FIELD_UPDATED, FieldValue.serverTimestamp());
            Tasks.await(firestore.collection("users").document(uid).set(m, SetOptions.merge()));
            EventInventoryStore.persistCloud(firestore, uid, app);
        } catch (Exception ignored) {
        }
    }
}
