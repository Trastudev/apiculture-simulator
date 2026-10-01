package com.apiculture.simulator.data.repository;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.apiculture.simulator.domain.game.DemandSurgeMilestones;
import com.apiculture.simulator.domain.game.GameCalendar;
import com.apiculture.simulator.domain.game.GlobalEventEffects;
import com.apiculture.simulator.domain.market.HoneyMarketEngine;
import com.apiculture.simulator.domain.market.HoneyMarketSnapshot;
import com.apiculture.simulator.domain.parcel.HexFlora;
import com.apiculture.simulator.data.session.PlayerAuth;
import com.apiculture.simulator.data.session.SignedInUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.SetOptions;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Eventos globales en Firestore. Escritura solo para la cuenta administradora (Aleix).
 */
public class GlobalEventRepository {

    public static final String COL_EVENTS = "globalGameEvents";
    public static final String COL_PROGRESS = "globalEventProgress";
    public static final String DOC_SURGE = "demand_surge";
    public static final String DOC_SHIFT = "market_shift";
    public static final String DOC_VELUTINA = "velutina";
    public static final String STATUS_OFF = "off";
    public static final String STATUS_ACTIVE = "active";
    public static final String STATUS_ENDED = "ended";

    public static final double SURGE_PRICE_MULT = 1.50;
    public static final double SURGE_DEMAND_MULT = 1.50;

    private static final String PREF_HITS = "velutina_hits_v1";

    private final Context app;
    @Nullable
    private final FirebaseFirestore firestore;
    @Nullable
    private MarketRepository marketRepository;
    @Nullable
    private EconomyRepository economyRepository;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final MutableLiveData<Snapshot> live = new MutableLiveData<>(Snapshot.empty());
    private volatile Snapshot cached = Snapshot.empty();
    @Nullable
    private ListenerRegistration eventsReg;
    @Nullable
    private ListenerRegistration progressReg;
    @Nullable
    private ListenerRegistration myKgReg;
    @Nullable
    private String myKgUid;
    private boolean endingSurgeForGoal;
    @Nullable
    private Runnable serverPoll;
    private final java.util.concurrent.ExecutorService io = java.util.concurrent.Executors.newSingleThreadExecutor();

    public GlobalEventRepository(Context app, @Nullable FirebaseFirestore firestore) {
        this.app = app.getApplicationContext();
        this.firestore = firestore;
    }

    public void setMarketRepository(@Nullable MarketRepository marketRepository) {
        this.marketRepository = marketRepository;
    }

    public void setEconomyRepository(@Nullable EconomyRepository economyRepository) {
        this.economyRepository = economyRepository;
    }

    public LiveData<Snapshot> snapshot() {
        return live;
    }

    public Snapshot cached() {
        return cached;
    }

    public void startListening() {
        if (GameServer.enabled()) {
            if (serverPoll != null) {
                return;
            }
            serverPoll = () -> {
                pullServerEvents();
                main.postDelayed(serverPoll, 20_000L);
            };
            main.post(serverPoll);
            return;
        }
        if (firestore == null || eventsReg != null) {
            return;
        }
        eventsReg = firestore.collection(COL_EVENTS)
                .addSnapshotListener((qs, e) -> {
                    if (e != null || qs == null) {
                        return;
                    }
                    DocumentSnapshot surge = null;
                    DocumentSnapshot shift = null;
                    DocumentSnapshot vel = null;
                    for (DocumentSnapshot d : qs.getDocuments()) {
                        if (DOC_SURGE.equals(d.getId())) {
                            surge = d;
                        } else if (DOC_SHIFT.equals(d.getId())) {
                            shift = d;
                        } else if (DOC_VELUTINA.equals(d.getId())) {
                            vel = d;
                        }
                    }
                    Snapshot prev = cached;
                    Snapshot next = Snapshot.fromDocs(surge, shift, vel, prev.kgSoldTowardGoal, prev.myKgSold);
                    publish(next);
                });
        progressReg = firestore.collection(COL_PROGRESS).document(DOC_SURGE)
                .addSnapshotListener((doc, e) -> {
                    if (e != null || doc == null) {
                        return;
                    }
                    double kg = 0;
                    if (doc.exists() && doc.get("kgSold") instanceof Number) {
                        kg = ((Number) doc.get("kgSold")).doubleValue();
                    }
                    Snapshot prev = cached;
                    publish(new Snapshot(prev.surge, prev.shift, prev.velutina, kg, prev.myKgSold));
                });
        PlayerAuth.getInstance().addAuthStateListener(auth -> {
            SignedInUser u = auth.getCurrentUser();
            attachMyKgListener(u != null ? u.getUid() : null);
        });
    }

    private void attachMyKgListener(@Nullable String uid) {
        if (firestore == null) {
            return;
        }
        if (uid != null && uid.equals(myKgUid) && myKgReg != null) {
            return;
        }
        if (myKgReg != null) {
            myKgReg.remove();
            myKgReg = null;
        }
        myKgUid = uid;
        if (uid == null || uid.isEmpty()) {
            Snapshot prev = cached;
            publish(new Snapshot(prev.surge, prev.shift, prev.velutina, prev.kgSoldTowardGoal, 0));
            return;
        }
        myKgReg = firestore.collection(COL_PROGRESS).document(DOC_SURGE)
                .collection("participants").document(uid)
                .addSnapshotListener((doc, e) -> {
                    if (e != null || doc == null) {
                        return;
                    }
                    double mine = 0;
                    if (doc.exists() && doc.get("kgSold") instanceof Number) {
                        mine = ((Number) doc.get("kgSold")).doubleValue();
                    }
                    Snapshot prev = cached;
                    publish(new Snapshot(prev.surge, prev.shift, prev.velutina, prev.kgSoldTowardGoal, mine));
                });
    }

    private void publish(Snapshot next) {
        cached = next;
        GlobalEventEffects.set(next.toEffects());
        live.postValue(next);
        maybeEndSurgeWhenGoalReached(next);
        if (marketRepository != null) {
            int day = GameCalendar.currentGlobalMarketDayKey();
            marketRepository.refreshGlobalMarketForDay(day,
                    GameCalendar.fromDayKey(day).getDayOfYear());
        }
    }

    /** Si el objetivo global llega al 100 %, el evento se da por terminado al momento. */
    private void maybeEndSurgeWhenGoalReached(Snapshot s) {
        if (endingSurgeForGoal || !GameServer.enabled() || s == null || !s.surge.exists()) {
            return;
        }
        if (!STATUS_ACTIVE.equals(s.surge.status)) {
            return;
        }
        if (s.surge.isEnded()) {
            return;
        }
        if (DemandSurgeMilestones.highestReached(s.kgSoldTowardGoal, s.surge.targetDemandKg) < 100) {
            return;
        }
        endingSurgeForGoal = true;
        endEvent(DOC_SURGE, msg -> endingSurgeForGoal = false);
    }

    public void recordSaleTowardSurge(String floraCanonical, double kg, String uid) {
        if (!GameServer.enabled() || kg <= 0 || uid == null) {
            return;
        }
        Snapshot s = cached;
        if (!s.surge.isLiveForEffects(s.kgSoldTowardGoal) || !s.surge.matchesFlora(floraCanonical)) {
            return;
        }
        String instance = s.surge.instanceId();
        io.execute(() -> GameServer.recordEventSale(kg, instance));
        Snapshot prev = cached;
        publish(new Snapshot(prev.surge, prev.shift, prev.velutina, prev.kgSoldTowardGoal, prev.myKgSold + kg));
    }

    public void hasParticipated(String uid, String instanceId, Consumer<Boolean> onMain) {
        if (!GameServer.enabled() || uid == null || instanceId == null || instanceId.isEmpty()) {
            main.post(() -> onMain.accept(false));
            return;
        }
        io.execute(() -> {
            JSONObject row = GameServer.fetchJson("/event-participants/" + uidPath(uid));
            double mine = row != null ? row.optDouble("kg", 0) : 0;
            main.post(() -> onMain.accept(mine > 0));
        });
    }

    public boolean hasClaimedLocal(String instanceId) {
        if (instanceId == null || instanceId.isEmpty()) {
            return false;
        }
        return app.getSharedPreferences("event_claims_v1", Context.MODE_PRIVATE)
                .getBoolean(instanceId, false);
    }

    public void markClaimedLocal(String instanceId) {
        app.getSharedPreferences("event_claims_v1", Context.MODE_PRIVATE)
                .edit().putBoolean(instanceId, true).apply();
    }

    public boolean hasDismissed(String uid, String instanceId) {
        if (instanceId == null || instanceId.isEmpty()) {
            return false;
        }
        return app.getSharedPreferences("event_dismiss_v1", Context.MODE_PRIVATE)
                .getBoolean(dismissKey(uid, instanceId), false);
    }

    public void markDismissed(String uid, String instanceId) {
        if (instanceId == null || instanceId.isEmpty()) {
            return;
        }
        app.getSharedPreferences("event_dismiss_v1", Context.MODE_PRIVATE)
                .edit().putBoolean(dismissKey(uid, instanceId), true).apply();
    }

    private static String dismissKey(@Nullable String uid, String instanceId) {
        return (uid != null && !uid.isEmpty() ? uid : "_") + "|" + instanceId;
    }

    public void claimSurgeRewards(String uid, Consumer<String> onMainMessage) {
        Snapshot s = cached;
        boolean goalDone = DemandSurgeMilestones.highestReached(
                s.kgSoldTowardGoal, s.surge.targetDemandKg) >= 100;
        if (!s.surge.exists() || (s.surge.isLive() && !goalDone)) {
            main.post(() -> onMainMessage.accept("El evento aún no ha terminado."));
            return;
        }
        String instanceId = s.surge.instanceId();
        if (hasClaimedLocal(instanceId)) {
            main.post(() -> onMainMessage.accept("Ya has recogido la recompensa."));
            return;
        }
        Runnable grant = () -> hasParticipated(uid, instanceId, participated -> {
            if (!participated) {
                onMainMessage.accept("Solo quienes vendieron esa miel durante el evento recogen premio.");
                return;
            }
            int highest = DemandSurgeMilestones.highestReached(s.kgSoldTowardGoal, s.surge.targetDemandKg);
            if (highest < 15) {
                markClaimedLocal(instanceId);
                persistClaimCloud(uid, instanceId, highest);
                onMainMessage.accept("Ningún hito alcanzado.");
                return;
            }
            int coins = DemandSurgeMilestones.coinsForHighest(highest);
            int treat = DemandSurgeMilestones.treatmentsForHighest(highest);
            int feed = DemandSurgeMilestones.feedForHighest(highest);
            int queens = DemandSurgeMilestones.queensForHighest(highest);
            if (economyRepository != null && coins > 0) {
                economyRepository.addToBalance(coins, "Premio del evento global");
            }
            EventInventoryStore.add(app, treat, feed, queens);
            markClaimedLocal(instanceId);
            persistClaimCloud(uid, instanceId, highest);
            EventInventoryStore.persistCloud(firestore, uid, app);
            onMainMessage.accept(null);
        });
        io.execute(() -> {
            JSONObject claim = GameServer.fetchJson("/event-claims/" + claimPath(uid, instanceId));
            JSONObject body = claim != null ? claim.optJSONObject("body") : null;
            if (body != null && body.optBoolean("claimed", false)) {
                markClaimedLocal(instanceId);
                main.post(() -> onMainMessage.accept("Ya has recogido la recompensa."));
                return;
            }
            main.post(grant);
        });
    }

    private void persistClaimCloud(String uid, String instanceId, int highest) {
        if (!GameServer.enabled() || uid == null || instanceId == null) {
            return;
        }
        io.execute(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("claimed", true);
                body.put("claimedAtMs", System.currentTimeMillis());
                body.put("highestMilestone", highest);
                JSONObject row = new JSONObject();
                row.put("ownerId", uid);
                row.put("instanceId", instanceId);
                row.put("body", body);
                GameServer.putJson("/event-claims/" + claimPath(uid, instanceId), row);
            } catch (Exception ignored) {
            }
        });
    }

    public void activateDemandSurge(String flora, int durationDays, double demandMult,
            String adminUid, Consumer<String> onMain) {
        HoneyMarketSnapshot snap = marketRepository != null ? marketRepository.getSnapshot() : null;
        String floraKey = HoneyMarketEngine.canonicalFloraKey(flora);
        double daily = snap != null ? snap.demandKgByFlora.getOrDefault(floraKey, 50.0) : 50.0;
        int days = Math.max(1, durationDays);
        double demand = clampSurgeDemandMult(demandMult);
        double target = Math.max(1.0, daily * demand * days);
        long now = System.currentTimeMillis();
        long end = now + days * 24L * 60L * 60L * 1000L;
        Map<String, Object> doc = new HashMap<>();
        doc.put("status", STATUS_ACTIVE);
        doc.put("type", "DEMAND_SURGE");
        doc.put("floraKey", floraKey);
        doc.put("demandMult", demand);
        doc.put("priceMult", SURGE_PRICE_MULT);
        doc.put("durationDays", days);
        doc.put("startsAtMs", now);
        doc.put("endsAtMs", end);
        doc.put("targetDemandKg", target);
        doc.put("createdByUid", adminUid);
        writeEvent(DOC_SURGE, doc, () -> {
            Map<String, Object> progress = new HashMap<>();
            progress.put("kgSold", 0.0);
            progress.put("instanceId", String.valueOf(now));
            progress.put("updatedAtMs", now);
            try {
                JSONObject body = new JSONObject();
                body.put("kgSold", 0.0);
                body.put("instanceId", String.valueOf(now));
                body.put("updatedAtMs", now);
                GameServer.putJson("/event-progress/" + DOC_SURGE, new JSONObject().put("body", body));
            } catch (Exception ignored) {
            }
            onMain.accept(null);
        }, onMain);
    }

    public static double clampSurgeDemandMult(double raw) {
        if (!Double.isFinite(raw) || raw < 1.0) {
            return SURGE_DEMAND_MULT;
        }
        return Math.min(50.0, raw);
    }

    public void deactivateDemandSurge(Consumer<String> onMain) {
        endEvent(DOC_SURGE, onMain);
    }

    public void activateMarketShift(
            boolean allFloras,
            List<String> floraKeys,
            int demandDeltaPercent,
            int priceDeltaPercent,
            int durationDays,
            String adminUid,
            Consumer<String> onMain) {
        int days = Math.max(1, durationDays);
        long now = System.currentTimeMillis();
        Map<String, Object> doc = new HashMap<>();
        doc.put("status", STATUS_ACTIVE);
        doc.put("type", "MARKET_SHIFT");
        doc.put("allFloras", allFloras);
        doc.put("floraKeys", floraKeys != null ? floraKeys : Collections.emptyList());
        doc.put("demandDeltaPercent", demandDeltaPercent);
        doc.put("priceDeltaPercent", priceDeltaPercent);
        doc.put("durationDays", days);
        doc.put("startsAtMs", now);
        doc.put("endsAtMs", now + days * 24L * 60L * 60L * 1000L);
        doc.put("createdByUid", adminUid);
        writeEvent(DOC_SHIFT, doc, () -> onMain.accept(null), onMain);
    }

    public void deactivateMarketShift(Consumer<String> onMain) {
        endEvent(DOC_SHIFT, onMain);
    }

    public void activateVelutina(
            double lossPercent,
            List<String> climateKeys,
            int durationDays,
            String adminUid,
            Consumer<String> onMain) {
        int days = Math.max(1, durationDays);
        long now = System.currentTimeMillis();
        Map<String, Object> doc = new HashMap<>();
        doc.put("status", STATUS_ACTIVE);
        doc.put("type", "VELUTINA");
        doc.put("lossPercent", Math.max(0, Math.min(90, lossPercent)));
        doc.put("climateKeys", climateKeys != null ? climateKeys : Collections.emptyList());
        doc.put("durationDays", days);
        doc.put("startsAtMs", now);
        doc.put("endsAtMs", now + days * 24L * 60L * 60L * 1000L);
        doc.put("createdByUid", adminUid);
        writeEvent(DOC_VELUTINA, doc, () -> onMain.accept(null), onMain);
    }

    public void deactivateVelutina(Consumer<String> onMain) {
        endEvent(DOC_VELUTINA, onMain);
    }

    private void endEvent(String docId, Consumer<String> onMain) {
        Map<String, Object> doc = new HashMap<>();
        doc.put("status", STATUS_ENDED);
        doc.put("endedAtMs", System.currentTimeMillis());
        writeEvent(docId, doc, () -> onMain.accept(null), onMain);
    }

    private void writeEvent(String id, Map<String, Object> data, Runnable ok, Consumer<String> onMain) {
        if (!GameServer.enabled()) {
            onMain.accept("Sin conexión al servidor.");
            return;
        }
        io.execute(() -> {
            try {
                JSONObject current = GameServer.fetchJson("/global-events/" + id);
                JSONObject body = current != null && current.optJSONObject("body") != null
                        ? current.optJSONObject("body") : new JSONObject();
                for (Map.Entry<String, Object> entry : data.entrySet()) {
                    body.put(entry.getKey(), entry.getValue());
                }
                JSONObject row = new JSONObject();
                row.put("status", body.optString("status", ""));
                row.put("body", body);
                if (GameServer.putJson("/global-events/" + id, row) / 100 != 2) {
                    main.post(() -> onMain.accept("Error al guardar"));
                    return;
                }
                main.post(ok);
                pullServerEvents();
            } catch (Exception e) {
                main.post(() -> onMain.accept(e.getMessage() != null ? e.getMessage() : "Error al guardar"));
            }
        });
    }

    private void pullServerEvents() {
        io.execute(() -> {
            JSONArray events = GameServer.fetchArray("/global-events");
            EventDoc surge = EventDoc.empty();
            EventDoc shift = EventDoc.empty();
            EventDoc vel = EventDoc.empty();
            if (events != null) {
                for (int i = 0; i < events.length(); i++) {
                    JSONObject row = events.optJSONObject(i);
                    if (row == null) {
                        continue;
                    }
                    EventDoc doc = EventDoc.fromJson(row.optJSONObject("body"), row.optString("status", ""));
                    String id = row.optString("id", "");
                    if (DOC_SURGE.equals(id)) {
                        surge = doc;
                    } else if (DOC_SHIFT.equals(id)) {
                        shift = doc;
                    } else if (DOC_VELUTINA.equals(id)) {
                        vel = doc;
                    }
                }
            }
            double kg = 0;
            JSONObject progress = GameServer.fetchJson("/event-progress/" + DOC_SURGE);
            if (progress != null && progress.optJSONObject("body") != null) {
                kg = progress.optJSONObject("body").optDouble("kgSold", 0);
            }
            double mine = 0;
            SignedInUser user = PlayerAuth.getInstance().getCurrentUser();
            if (user != null) {
                JSONObject part = GameServer.fetchJson("/event-participants/" + uidPath(user.getUid()));
                if (part != null) {
                    mine = part.optDouble("kg", 0);
                }
            }
            publish(new Snapshot(surge, shift, vel, kg, mine));
        });
    }

    @Nullable
    private static String uidPath(@NonNull String uid) {
        return enc("demand_surge:" + uid);
    }

    @Nullable
    private static String claimPath(@NonNull String uid, @NonNull String instanceId) {
        return enc(uid + ":" + instanceId);
    }

    @NonNull
    private static String enc(@NonNull String value) {
        try {
            return java.net.URLEncoder.encode(value, "UTF-8");
        } catch (Exception e) {
            return value;
        }
    }

    public boolean alreadyHitByVelutina(String hiveId, String instanceId) {
        if (hiveId == null || instanceId == null || instanceId.isEmpty()) {
            return true;
        }
        SharedPreferences p = app.getSharedPreferences(PREF_HITS, Context.MODE_PRIVATE);
        Set<String> set = p.getStringSet(instanceId, Collections.emptySet());
        return set != null && set.contains(hiveId);
    }

    public void markVelutinaHit(String hiveId, String instanceId) {
        SharedPreferences p = app.getSharedPreferences(PREF_HITS, Context.MODE_PRIVATE);
        Set<String> set = new HashSet<>(p.getStringSet(instanceId, Collections.emptySet()));
        set.add(hiveId);
        p.edit().putStringSet(instanceId, set).apply();
    }

    private static Map<String, Object> mapOf(String k1, Object v1, String k2, Object v2, String k3, Object v3) {
        Map<String, Object> m = new HashMap<>();
        m.put(k1, v1);
        m.put(k2, v2);
        m.put(k3, v3);
        return m;
    }

    public static final class EventDoc {
        public final String status;
        public final String floraKey;
        public final boolean allFloras;
        public final List<String> floraKeys;
        public final double demandMult;
        public final double priceMult;
        public final int demandDeltaPercent;
        public final int priceDeltaPercent;
        public final double lossPercent;
        public final List<String> climateKeys;
        public final long startsAtMs;
        public final long endsAtMs;
        public final double targetDemandKg;
        public final int durationDays;

        EventDoc(String status, String floraKey, boolean allFloras, List<String> floraKeys,
                 double demandMult, double priceMult, int demandDeltaPercent, int priceDeltaPercent,
                 double lossPercent, List<String> climateKeys, long startsAtMs, long endsAtMs,
                 double targetDemandKg, int durationDays) {
            this.status = status != null ? status : STATUS_OFF;
            this.floraKey = floraKey != null ? floraKey : "";
            this.allFloras = allFloras;
            this.floraKeys = floraKeys != null ? floraKeys : Collections.emptyList();
            this.demandMult = demandMult;
            this.priceMult = priceMult;
            this.demandDeltaPercent = demandDeltaPercent;
            this.priceDeltaPercent = priceDeltaPercent;
            this.lossPercent = lossPercent;
            this.climateKeys = climateKeys != null ? climateKeys : Collections.emptyList();
            this.startsAtMs = startsAtMs;
            this.endsAtMs = endsAtMs;
            this.targetDemandKg = targetDemandKg;
            this.durationDays = durationDays;
        }

        static EventDoc empty() {
            return new EventDoc(STATUS_OFF, "", false, null, 1, 1, 0, 0, 0, null, 0, 0, 0, 0);
        }

        static EventDoc fromJson(@Nullable JSONObject d, @Nullable String statusFallback) {
            if (d == null) {
                return empty();
            }
            String status = d.optString("status", statusFallback != null ? statusFallback : STATUS_OFF);
            return new EventDoc(
                    status,
                    d.optString("floraKey", ""),
                    d.optBoolean("allFloras", false),
                    jsonStrings(d.optJSONArray("floraKeys")),
                    d.optDouble("demandMult", 1),
                    d.optDouble("priceMult", 1),
                    d.optInt("demandDeltaPercent", 0),
                    d.optInt("priceDeltaPercent", 0),
                    d.optDouble("lossPercent", 0),
                    jsonStrings(d.optJSONArray("climateKeys")),
                    d.optLong("startsAtMs", 0),
                    d.optLong("endsAtMs", 0),
                    d.optDouble("targetDemandKg", 0),
                    d.optInt("durationDays", 0));
        }

        private static List<String> jsonStrings(@Nullable JSONArray raw) {
            if (raw == null) {
                return Collections.emptyList();
            }
            List<String> out = new ArrayList<>();
            for (int i = 0; i < raw.length(); i++) {
                String value = raw.optString(i, "");
                if (!value.isEmpty()) {
                    out.add(value);
                }
            }
            return out;
        }

        static EventDoc from(@Nullable DocumentSnapshot d) {
            if (d == null || !d.exists()) {
                return empty();
            }
            List<String> floras = stringList(d.get("floraKeys"));
            List<String> climates = stringList(d.get("climateKeys"));
            Boolean all = d.getBoolean("allFloras");
            return new EventDoc(
                    d.getString("status"),
                    d.getString("floraKey"),
                    Boolean.TRUE.equals(all),
                    floras,
                    num(d, "demandMult", 1.0),
                    num(d, "priceMult", 1.0),
                    (int) num(d, "demandDeltaPercent", 0),
                    (int) num(d, "priceDeltaPercent", 0),
                    num(d, "lossPercent", 0),
                    climates,
                    (long) num(d, "startsAtMs", 0),
                    (long) num(d, "endsAtMs", 0),
                    num(d, "targetDemandKg", 0),
                    (int) num(d, "durationDays", 0));
        }

        public boolean exists() {
            return !STATUS_OFF.equals(status) && startsAtMs > 0;
        }

        public boolean isLive() {
            if (!STATUS_ACTIVE.equals(status)) {
                return false;
            }
            long now = System.currentTimeMillis();
            if (!(now >= startsAtMs && (endsAtMs <= 0 || now < endsAtMs))) {
                return false;
            }
            return true;
        }

        /**
         * En vivo para efectos de mercado: deja de aplicar si el objetivo ya se cumplió
         * (aunque el documento aún diga {@code active}).
         */
        public boolean isLiveForEffects(double kgSoldTowardGoal) {
            return isLive() && !isGoalComplete(kgSoldTowardGoal);
        }

        public boolean isEnded() {
            if (STATUS_ENDED.equals(status)) {
                return exists();
            }
            return STATUS_ACTIVE.equals(status) && endsAtMs > 0 && System.currentTimeMillis() >= endsAtMs;
        }

        /** Objetivo global alcanzado (100 %), aunque aún no se haya escrito {@code ended} en Firestore. */
        public boolean isGoalComplete(double kgSoldTowardGoal) {
            return exists() && DemandSurgeMilestones.highestReached(kgSoldTowardGoal, targetDemandKg) >= 100;
        }

        public boolean isFinished(double kgSoldTowardGoal) {
            return isEnded() || isGoalComplete(kgSoldTowardGoal);
        }

        public String instanceId() {
            return startsAtMs > 0 ? String.valueOf(startsAtMs) : "";
        }

        public boolean matchesFlora(String floraCanonical) {
            return floraKey != null && floraKey.equals(HoneyMarketEngine.canonicalFloraKey(floraCanonical));
        }

        private static double num(DocumentSnapshot d, String k, double def) {
            Object o = d.get(k);
            return o instanceof Number ? ((Number) o).doubleValue() : def;
        }

        @SuppressWarnings("unchecked")
        private static List<String> stringList(Object raw) {
            if (!(raw instanceof List)) {
                return Collections.emptyList();
            }
            List<String> out = new ArrayList<>();
            for (Object o : (List<Object>) raw) {
                if (o != null) {
                    out.add(String.valueOf(o));
                }
            }
            return out;
        }
    }

    public static final class Snapshot {
        public final EventDoc surge;
        public final EventDoc shift;
        public final EventDoc velutina;
        public final double kgSoldTowardGoal;
        public final double myKgSold;

        Snapshot(EventDoc surge, EventDoc shift, EventDoc velutina, double kgSoldTowardGoal) {
            this(surge, shift, velutina, kgSoldTowardGoal, 0);
        }

        Snapshot(EventDoc surge, EventDoc shift, EventDoc velutina, double kgSoldTowardGoal, double myKgSold) {
            this.surge = surge != null ? surge : EventDoc.empty();
            this.shift = shift != null ? shift : EventDoc.empty();
            this.velutina = velutina != null ? velutina : EventDoc.empty();
            this.kgSoldTowardGoal = kgSoldTowardGoal;
            this.myKgSold = Math.max(0, myKgSold);
        }

        static Snapshot empty() {
            return new Snapshot(EventDoc.empty(), EventDoc.empty(), EventDoc.empty(), 0, 0);
        }

        static Snapshot fromDocs(
                @Nullable DocumentSnapshot surge,
                @Nullable DocumentSnapshot shift,
                @Nullable DocumentSnapshot vel,
                double kgSold,
                double myKgSold) {
            return new Snapshot(EventDoc.from(surge), EventDoc.from(shift), EventDoc.from(vel), kgSold, myKgSold);
        }

        GlobalEventEffects.State toEffects() {
            Map<String, Double> demand = new HashMap<>();
            Map<String, Double> price = new HashMap<>();
            if (surge.isLiveForEffects(kgSoldTowardGoal)) {
                String f = HoneyMarketEngine.canonicalFloraKey(surge.floraKey);
                demand.put(f, surge.demandMult > 0 ? surge.demandMult : SURGE_DEMAND_MULT);
                price.put(f, surge.priceMult > 0 ? surge.priceMult : SURGE_PRICE_MULT);
            }
            if (shift.isLive()) {
                double dm = 1.0 + shift.demandDeltaPercent / 100.0;
                double pm = 1.0 + shift.priceDeltaPercent / 100.0;
                List<String> keys = shift.allFloras
                        ? java.util.Arrays.asList(HexFlora.FLORA_TYPES)
                        : shift.floraKeys;
                for (String raw : keys) {
                    String k = HoneyMarketEngine.canonicalFloraKey(raw);
                    demand.put(k, demand.getOrDefault(k, 1.0) * dm);
                    price.put(k, price.getOrDefault(k, 1.0) * pm);
                }
            }
            boolean velOn = velutina.isLive();
            Set<String> climates = new HashSet<>(velutina.climateKeys);
            return new GlobalEventEffects.State(
                    demand, price, velOn, velutina.lossPercent, climates, velutina.instanceId());
        }
    }
}
