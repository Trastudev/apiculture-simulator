package com.apiculture.simulator.data.repository;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.apiculture.simulator.domain.game.DemandSurgeMilestones;
import com.apiculture.simulator.domain.game.GameCalendar;
import com.apiculture.simulator.domain.game.GlobalEventEffects;
import com.apiculture.simulator.domain.market.HoneyMarketEngine;
import com.apiculture.simulator.domain.market.HoneyMarketSnapshot;
import com.apiculture.simulator.domain.parcel.HexFlora;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.SetOptions;

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
        FirebaseAuth.getInstance().addAuthStateListener(auth -> {
            FirebaseUser u = auth.getCurrentUser();
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
        if (marketRepository != null) {
            int day = GameCalendar.currentGlobalMarketDayKey();
            marketRepository.refreshGlobalMarketForDay(day,
                    GameCalendar.fromDayKey(day).getDayOfYear());
        }
    }

    public void recordSaleTowardSurge(String floraCanonical, double kg, String uid) {
        if (firestore == null || kg <= 0 || uid == null) {
            return;
        }
        Snapshot s = cached;
        if (!s.surge.isLive() || !s.surge.matchesFlora(floraCanonical)) {
            return;
        }
        firestore.collection(COL_PROGRESS).document(DOC_SURGE)
                .set(mapOf("kgSold", FieldValue.increment(kg),
                        "updatedAtMs", System.currentTimeMillis(),
                        "instanceId", s.surge.instanceId()), SetOptions.merge());
        Map<String, Object> p = new HashMap<>();
        p.put("uid", uid);
        p.put("kgSold", FieldValue.increment(kg));
        firestore.collection(COL_PROGRESS).document(DOC_SURGE)
                .collection("participants").document(uid)
                .set(p, SetOptions.merge());
        Snapshot prev = cached;
        publish(new Snapshot(prev.surge, prev.shift, prev.velutina, prev.kgSoldTowardGoal, prev.myKgSold + kg));
    }

    public void hasParticipated(String uid, String instanceId, Consumer<Boolean> onMain) {
        if (firestore == null || uid == null || instanceId == null || instanceId.isEmpty()) {
            main.post(() -> onMain.accept(false));
            return;
        }
        firestore.collection(COL_PROGRESS).document(DOC_SURGE)
                .collection("participants").document(uid)
                .get()
                .addOnSuccessListener(doc -> main.post(() -> onMain.accept(doc != null && doc.exists())))
                .addOnFailureListener(e -> main.post(() -> onMain.accept(false)));
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

    public void claimSurgeRewards(String uid, Consumer<String> onMainMessage) {
        Snapshot s = cached;
        if (!s.surge.exists() || s.surge.isLive()) {
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
                economyRepository.addToBalance(coins);
            }
            EventInventoryStore.add(app, treat, feed, queens);
            markClaimedLocal(instanceId);
            persistClaimCloud(uid, instanceId, highest);
            EventInventoryStore.persistCloud(firestore, uid, app);
            onMainMessage.accept(null);
        });
        if (firestore == null) {
            grant.run();
            return;
        }
        firestore.collection("users").document(uid)
                .collection("eventClaims").document(instanceId)
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc != null && doc.exists() && Boolean.TRUE.equals(doc.getBoolean("claimed"))) {
                        markClaimedLocal(instanceId);
                        main.post(() -> onMainMessage.accept("Ya has recogido la recompensa."));
                        return;
                    }
                    grant.run();
                })
                .addOnFailureListener(e -> grant.run());
    }

    private void persistClaimCloud(String uid, String instanceId, int highest) {
        if (firestore == null) {
            return;
        }
        Map<String, Object> m = new HashMap<>();
        m.put("claimed", true);
        m.put("claimedAtMs", System.currentTimeMillis());
        m.put("highestMilestone", highest);
        firestore.collection("users").document(uid)
                .collection("eventClaims").document(instanceId)
                .set(m, SetOptions.merge());
    }

    public void activateDemandSurge(String flora, int durationDays, String adminUid, Consumer<String> onMain) {
        HoneyMarketSnapshot snap = marketRepository != null ? marketRepository.getSnapshot() : null;
        String floraKey = HoneyMarketEngine.canonicalFloraKey(flora);
        double daily = snap != null ? snap.demandKgByFlora.getOrDefault(floraKey, 50.0) : 50.0;
        int days = Math.max(1, Math.min(30, durationDays));
        double target = Math.max(1.0, daily * SURGE_DEMAND_MULT * days);
        long now = System.currentTimeMillis();
        long end = now + days * 24L * 60L * 60L * 1000L;
        Map<String, Object> doc = new HashMap<>();
        doc.put("status", STATUS_ACTIVE);
        doc.put("type", "DEMAND_SURGE");
        doc.put("floraKey", floraKey);
        doc.put("demandMult", SURGE_DEMAND_MULT);
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
            if (firestore != null) {
                firestore.collection(COL_PROGRESS).document(DOC_SURGE).set(progress);
            }
            onMain.accept(null);
        }, onMain);
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
        int days = Math.max(1, Math.min(30, durationDays));
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
        int days = Math.max(1, Math.min(30, durationDays));
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
        if (firestore == null) {
            onMain.accept("Sin Firestore.");
            return;
        }
        firestore.collection(COL_EVENTS).document(id)
                .set(data, SetOptions.merge())
                .addOnSuccessListener(v -> main.post(ok))
                .addOnFailureListener(e -> main.post(() ->
                        onMain.accept(e.getMessage() != null ? e.getMessage() : "Error al guardar")));
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
            return now >= startsAtMs && (endsAtMs <= 0 || now < endsAtMs);
        }

        public boolean isEnded() {
            if (STATUS_ENDED.equals(status)) {
                return exists();
            }
            return STATUS_ACTIVE.equals(status) && endsAtMs > 0 && System.currentTimeMillis() >= endsAtMs;
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
            if (surge.isLive()) {
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
