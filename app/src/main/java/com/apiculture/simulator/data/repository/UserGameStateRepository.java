package com.apiculture.simulator.data.repository;

import android.content.Context;
import android.os.Handler;

import androidx.annotation.Nullable;

import com.apiculture.simulator.domain.game.LevelSystem;
import com.apiculture.simulator.domain.market.HoneyMarketEngine;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.Transaction;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

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
        if (uid == null || uid.isEmpty() || !GameServer.enabled()) {
            mainHandler.post(onMainAfterPull);
            return;
        }
        io.execute(() -> {
            try {
                GameServer.PlayerLoad loaded = GameServer.loadPlayerStatus(uid);
                if (loaded.status == 404) {
                    if (economy.hasPersistedBalance()) {
                        // La ficha no está en el servidor, pero este teléfono ya tiene partida.
                        serverEconomyApplied = true;
                        pushSnapshotSync(uid);
                    } else {
                        seedBlankPlayerLocal(uid);
                        serverEconomyApplied = true;
                        pushSnapshotSync(uid);
                    }
                } else if (loaded.body == null) {
                    // Red, token o recreación de la actividad (cambio de idioma).
                    // No se toca el saldo ni la miel local y no se sube el valor por defecto.
                } else {
                    JSONObject player = loaded.body;
                    serverEconomyApplied = true;
                    applyPlayer(uid, player);
                    EventInventoryStore.applyFromServer(GameServer.loadStore(uid, "inventory"), app);
                    JSONObject hq = GameServer.loadStore(uid, "hq");
                    if (hq != null) {
                        HeadquartersStore.applyServer(app, uid, hq);
                    }
                    JSONObject fleet = GameServer.loadStore(uid, "fleet");
                    if (fleet != null && (fleet.has("vehicles") || fleet.has("ports"))) {
                        FleetStore.applyServer(app, uid, fleet);
                    } else {
                        FleetStore.pushServer(app, uid);
                    }
                    JSONObject workshop = GameServer.loadStore(uid, "workshop");
                    if (workshop != null && workshop.has("levels")) {
                        WorkshopStore.applyServer(app, uid, workshop);
                    } else {
                        WorkshopStore.pushServer(app, uid);
                    }
                    JSONObject warehouse = GameServer.loadStore(uid, "warehouse");
                    if (warehouse != null && warehouse.optJSONObject("stock") != null) {
                        WarehouseHoneyStore.applyServer(app, uid, warehouse.optJSONObject("stock"));
                    }
                }
            } catch (Exception ignored) {
            }
            mainHandler.post(onMainAfterPull);
        });
    }

    private void applyPlayer(String uid, JSONObject player) {
        if (!player.has("economyBalanceEur") && !player.has("playerLevel")) {
            pushSnapshotSync(uid);
            return;
        }
        double balance = player.has("economyBalanceEur")
                ? player.optDouble("economyBalanceEur", economy.getBalance())
                : economy.getBalance();
        String buckets = player.optString("economyHoneyBucketsJson", "");
        boolean hasBuckets = !buckets.trim().isEmpty() && !"null".equals(buckets);
        double sold = player.optDouble("economyHoneySoldKgTotal", economy.getHoneySoldTotalKg());
        String soldBy = player.optString("economyHoneySoldByFloraJson", "");
        economy.applyFromCloud(balance, hasBuckets ? buckets : null, sold,
                soldBy.trim().isEmpty() || "null".equals(soldBy) ? null : soldBy);
        int level = player.has("playerLevel") ? player.optInt("playerLevel", progress.getLevel(uid))
                : progress.getLevel(uid);
        double xp = player.has("playerXp") ? player.optDouble("playerXp", progress.getXp(uid))
                : progress.getXp(uid);
        progress.applyFromCloud(uid, level, xp);
        if (player.has("honeyStockSeq")) {
            economy.setHoneyStockSeq(player.optLong("honeyStockSeq", economy.honeyStockSeq()));
        }
        if (player.has("gameLocale")) {
            String tag = player.optString("gameLocale", "");
            if (tag == null) {
                tag = "";
            }
            if (!tag.equals(GameLocale.saved(app))) {
                String chosen = tag;
                mainHandler.post(() -> GameLocale.choose(app, chosen));
            }
        }
    }

    private void applySnapshotToLocal(String uid, DocumentSnapshot snap) {
        if (!snap.exists()) {
            seedBlankPlayerLocal(uid);
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
        Double xpVal = numberField(snap, FIELD_PLAYER_XP);
        if (lvl != null || xpVal != null) {
            int level = lvl != null ? lvl : progress.getLevel(uid);
            double xp = xpVal != null ? xpVal : progress.getXp(uid);
            progress.applyFromCloud(uid, level, xp);
        }
        EventInventoryStore.applyFromCloudIfHigher(snap, app);
        boolean hasAnyGameField = balObj instanceof Number || hasBuckets || hasSold
                || lvl != null || xpVal != null;
        if (!hasAnyGameField) {
            seedBlankPlayerLocal(uid);
            pushSnapshotSync(uid);
        }
    }

    /** Alta o documento de usuario sin partida: 45.000 beecoins, sin miel ni progreso. */
    private void seedBlankPlayerLocal(String uid) {
        economy.applyNewGameEconomyDefaults();
        FleetStore.clear(app, uid);
        WarehouseHoneyStore.clear(app, uid);
        WorkshopStore.clear(app, uid);
        progress.resetToNewGame(uid);
        EventInventoryStore.clearAll(app);
    }

    @Nullable
    private static Double numberField(DocumentSnapshot snap, String key) {
        Object v = snap.get(key);
        if (v instanceof Number) {
            return ((Number) v).doubleValue();
        }
        return null;
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
        if (uid == null || uid.isEmpty() || !GameServer.enabled()) {
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
        if (uid == null || uid.isEmpty() || !GameServer.enabled()) {
            return;
        }
        mainHandler.removeCallbacks(debouncedPush);
        pendingPushUid = null;
        pushSnapshot(uid);
    }

    /** El saldo se confirma en red y no puede hacerse desde el hilo de la interfaz. */
    public void runOffMain(Runnable work) {
        io.execute(work);
    }

    public void runOnMain(Runnable work) {
        mainHandler.post(work);
    }

    public void pushSnapshot(@Nullable String uid) {
        if (uid == null || uid.isEmpty() || !GameServer.enabled()) {
            return;
        }
        io.execute(() -> pushSnapshotSync(uid));
    }

    /** Hasta que llega la ficha del servidor no se sube la cartera local: pisaría el saldo real. */
    private volatile boolean serverEconomyApplied;

    private void pushSnapshotSync(String uid) {
        if (GameServer.enabled() && !serverEconomyApplied) {
            return;
        }
        try {
            JSONObject body = new JSONObject();
            body.put("economyBalanceEur", economy.getBalance());
            body.put("economyHoneyBucketsJson", economy.snapshotHoneyBucketsJsonForCloud());
            body.put("economyHoneySoldKgTotal", economy.getHoneySoldTotalKg());
            body.put("economyHoneySoldByFloraJson", economy.snapshotHoneySoldByFloraJsonForCloud());
            body.put("playerLevel", progress.getLevel(uid));
            body.put("playerXp", progress.getXp(uid));
            body.put("honeyStockSeq", economy.honeyStockSeq());
            GameServer.savePlayer(uid, body);
            EventInventoryStore.persistToServer(uid, app);
        } catch (Exception ignored) {
        }
    }

    public void grantCoinsToUsers(@Nullable List<String> uids, double amount, Consumer<String> onMain) {
        grantUsers(uids, snap -> applyCoins(snap, amount), onMain);
    }

    public void grantHoneyToUsers(@Nullable List<String> uids, @Nullable String flora, double kg,
            Consumer<String> onMain) {
        String key = HoneyMarketEngine.canonicalFloraKey(flora);
        grantUsers(uids, snap -> applyHoney(snap, key, kg), onMain);
    }

    public void grantXpToUsers(@Nullable List<String> uids, int xp, Consumer<String> onMain) {
        grantUsers(uids, snap -> applyXp(snap, xp, false), onMain);
    }

    public void grantLevelUpToUsers(@Nullable List<String> uids, Consumer<String> onMain) {
        grantUsers(uids, snap -> applyXp(snap, 0, true), onMain);
    }

    public void listAllUserIds(Consumer<List<String>> onMain) {
        if (!GameServer.enabled()) {
            mainHandler.post(() -> onMain.accept(new ArrayList<>()));
            return;
        }
        io.execute(() -> {
            List<String> ids = new ArrayList<>();
            try {
                org.json.JSONArray rows = GameServer.fetchArray("/players");
                if (rows != null) {
                    for (int i = 0; i < rows.length(); i++) {
                        JSONObject row = rows.optJSONObject(i);
                        if (row == null) {
                            continue;
                        }
                        String id = row.optString("id", "");
                        if (!id.isEmpty()) {
                            ids.add(id);
                        }
                    }
                }
            } catch (Exception ignored) {
            }
            mainHandler.post(() -> onMain.accept(ids));
        });
    }

    private interface GrantOp {
        JSONObject apply(JSONObject player) throws Exception;
    }

    private void grantUsers(@Nullable List<String> uids, GrantOp op, Consumer<String> onMain) {
        if (!GameServer.enabled()) {
            mainHandler.post(() -> onMain.accept("Sin conexión al servidor."));
            return;
        }
        if (uids == null || uids.isEmpty()) {
            mainHandler.post(() -> onMain.accept("Elige un jugador."));
            return;
        }
        io.execute(() -> {
            int ok = 0;
            String lastErr = null;
            for (int i = 0; i < uids.size(); i++) {
                String uid = uids.get(i);
                if (uid == null || uid.isEmpty()) {
                    continue;
                }
                try {
                    JSONObject player = GameServer.loadPlayer(uid);
                    if (player == null) {
                        player = new JSONObject();
                    }
                    JSONObject patch = op.apply(player);
                    if (!GameServer.savePlayer(uid, patch)) {
                        lastErr = "No se pudo guardar.";
                        continue;
                    }
                    ok++;
                } catch (Exception e) {
                    lastErr = e.getMessage();
                }
            }
            String err = lastErr;
            int done = ok;
            mainHandler.post(() -> {
                if (done <= 0) {
                    onMain.accept(err != null ? err : "No se pudo aplicar.");
                } else {
                    onMain.accept(null);
                }
            });
        });
    }

    private static JSONObject applyCoins(JSONObject player, double amount) throws Exception {
        JSONObject m = new JSONObject();
        double bal = player.has("economyBalanceEur")
                ? player.optDouble("economyBalanceEur", EconomyRepository.DEFAULT_STARTING_BALANCE_EUR)
                : EconomyRepository.DEFAULT_STARTING_BALANCE_EUR;
        m.put("economyBalanceEur", bal + amount);
        return m;
    }

    private static JSONObject applyHoney(JSONObject player, String flora, double kg) throws Exception {
        JSONObject m = new JSONObject();
        String json = player.optString("economyHoneyBucketsJson", "");
        JSONObject o = json != null && !json.trim().isEmpty() && !"null".equals(json)
                ? new JSONObject(json) : new JSONObject();
        String key = flora != null && !flora.isEmpty() ? flora : "Mil flores";
        o.put(key, o.optDouble(key, 0.0) + kg);
        m.put("economyHoneyBucketsJson", o.toString());
        return m;
    }

    private static JSONObject applyXp(JSONObject player, int xpToAdd, boolean oneLevel) throws Exception {
        int level = player.optInt("playerLevel", 0);
        double xp = player.optDouble("playerXp", 0.0);
        double add = xpToAdd;
        if (oneLevel) {
            add = Math.max(1.0, LevelSystem.xpForLevel(level) - xp);
        }
        LevelSystem.Result r = LevelSystem.addXp(level, xp, add);
        JSONObject m = new JSONObject();
        m.put("playerLevel", r.level);
        m.put("playerXp", r.xp);
        return m;
    }
}
