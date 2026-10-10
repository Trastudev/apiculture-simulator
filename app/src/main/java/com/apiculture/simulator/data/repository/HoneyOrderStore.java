package com.apiculture.simulator.data.repository;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;

import com.apiculture.simulator.data.local.AppDatabase;
import com.apiculture.simulator.data.local.dao.HoneyOrderDao;
import com.apiculture.simulator.data.local.entity.HoneyOrderEntity;
import com.apiculture.simulator.domain.game.GameCalendar;
import com.apiculture.simulator.domain.game.HoneyOrder;
import com.apiculture.simulator.domain.game.HoneyOrderCatalog;
import com.apiculture.simulator.domain.game.OfferBand;
import com.apiculture.simulator.domain.game.OfferReplenish;
import com.apiculture.simulator.domain.map.PlayableMapRegion;
import com.apiculture.simulator.domain.market.HoneyMarketEngine;
import com.apiculture.simulator.domain.parcel.HexFlora;
import com.apiculture.simulator.domain.parcel.HexParcel;
import com.apiculture.simulator.data.session.PlayerAuth;
import com.apiculture.simulator.data.session.SignedInUser;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.WriteBatch;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class HoneyOrderStore {

    private static final String TAG = "HoneyOrderStore";
    public static final String COLLECTION = "honeyOrders";

    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final String PREFS = "honey_orders";

    @Nullable
    private static volatile ListenerRegistration openListener;
    @Nullable
    private static volatile ListenerRegistration mineListener;
    private static volatile boolean started;
    private static volatile boolean cloudWritesBlocked;

    private HoneyOrderStore() {
    }

    @NonNull
    private static HoneyOrderDao dao(@NonNull Context context) {
        return AppDatabase.getInstance(context.getApplicationContext()).honeyOrderDao();
    }

    @NonNull
    public static LiveData<List<HoneyOrderEntity>> observeOpen(@NonNull Context context) {
        return dao(context).observeOpen();
    }

    @NonNull
    public static LiveData<List<HoneyOrderEntity>> observeMapFaces(@NonNull Context context,
            @Nullable String uid) {
        return dao(context).observeVisible(uid != null ? uid : "");
    }

    public static void startListening(@NonNull Context context) {
        if (GameServer.enabled()) {
            return;
        }
        Context app = context.getApplicationContext();
        if (started) {
            return;
        }
        started = true;
        attachListeners(app);
        try {
            PlayerAuth.getInstance().addAuthStateListener(auth -> attachListeners(app));
        } catch (Exception ignored) {
        }
    }

    private static void attachListeners(@NonNull Context app) {
    }

    private static void applySnapshot(@NonNull Context app, @Nullable QuerySnapshot snap,
            @Nullable Exception err, boolean mineQuery) {
        if (err != null || snap == null) {
            return;
        }
        IO.execute(() -> AppDatabase.getInstance(app).runInTransaction(() -> {
            HoneyOrderDao d = dao(app);
            long now = System.currentTimeMillis();
            String uid = currentUid();
            if (!uid.isEmpty()) {
                d.deleteForeignClaims(uid);
            }
            snap.getDocumentChanges().forEach(ch -> {
                String id = ch.getDocument().getId();
                if (ch.getType() == DocumentChange.Type.REMOVED) {
                    HoneyOrderEntity existing = d.getById(id);
                    if (existing != null && existing.taken && uid.equals(existing.claimedBy)) {
                        return;
                    }
                    d.delete(id);
                    return;
                }
                java.util.Map<String, Object> data = ch.getDocument().getData();
                Boolean taken = data.get("taken") instanceof Boolean ? (Boolean) data.get("taken") : null;
                Number exp = data.get("expireEpochMs") instanceof Number
                        ? (Number) data.get("expireEpochMs") : null;
                String claimedBy = data.get("claimedBy") instanceof String
                        ? (String) data.get("claimedBy") : "";
                boolean expired = exp != null && exp.longValue() <= now;
                HoneyOrderEntity existing = d.getById(id);
                boolean mineLocal = existing != null && existing.taken && uid.equals(existing.claimedBy);
                boolean mineCloud = claimedBy != null && claimedBy.equals(uid);
                if (mineLocal && !mineQuery) {
                    return;
                }
                if (Boolean.TRUE.equals(taken) && !mineCloud) {
                    d.delete(id);
                    return;
                }
                if (expired && !mineLocal && !mineCloud) {
                    d.delete(id);
                    return;
                }
                HoneyOrderEntity e = fromFirestore(id, data);
                if (e != null) {
                    d.upsert(e);
                }
            });
        }));
    }

    /**
     * Primero las comandas a 180 km de la sede de Iberia. El resto del catálogo
     * sigue en segundo plano y no borra las que ya se han guardado.
     */
    public static void preloadAroundIberiaHeadquarters(@NonNull Context context) {
        Context app = context.getApplicationContext();
        if (!GameServer.enabled()) {
            maintainNow(app, null);
            return;
        }
        HeadquartersStore.Hq hq = HeadquartersStore.get(app, currentUid(), PlayableMapRegion.IBERIA);
        double lat = hq != null ? hq.lat : PlayableMapRegion.IBERIA.defaultLookLat();
        double lng = hq != null ? hq.lng : PlayableMapRegion.IBERIA.defaultLookLon();
        GameServer.syncOffersBlocking(app, lat, lng, OfferReplenish.NEAR_SPAWN_KM);
        IO.execute(() -> maintainNow(app, null));
    }

    public static void maintain(@NonNull Context context, @Nullable MarketRepository market) {
        Context app = context.getApplicationContext();
        IO.execute(() -> maintainNow(app, market));
    }

    public static void maintainNow(@NonNull Context context, @Nullable MarketRepository market) {
        HoneyOrderDao d = dao(context);
        if (GameServer.enabled()) {
            // El backend es la única fuente de precios y ofertas. La app no
            // publica un snapshot de mercado para evitar que un cliente fije
            // el precio de las comandas.
            if (GameServer.syncOffersBlocking(context)) {
                d.prune(System.currentTimeMillis());
            }
            return;
        }
        HoneyOrderCatalog.PriceLookup prices = market != null ? market::priceEurPerKgForFlora : null;
        replenishExpired(context, d, prices, System.currentTimeMillis());
        d.prune(System.currentTimeMillis());
        int dayKey = GameCalendar.currentGlobalMarketDayKey();
        spawnDayIfNeeded(context, d, dayKey, MapRegionPrefs.get(context), prices);
        repairOpenOrderFlora(context, d, prices);
        repairOpenOrderKg(d);
    }

    public static void maintainRegion(@NonNull Context context, @Nullable MarketRepository market,
            @NonNull PlayableMapRegion region) {
        Context app = context.getApplicationContext();
        IO.execute(() -> {
            if (GameServer.enabled()) {
                GameServer.syncOffersBlocking(app);
                return;
            }
            HoneyOrderDao d = dao(app);
            HoneyOrderCatalog.PriceLookup prices = market != null ? market::priceEurPerKgForFlora : null;
            int dayKey = GameCalendar.currentGlobalMarketDayKey();
            spawnDayIfNeeded(app, d, dayKey, region, prices);
            repairOpenOrderFlora(app, d, prices);
            repairOpenOrderKg(d);
        });
    }

    private static void spawnDayIfNeeded(@NonNull Context context, @NonNull HoneyOrderDao d,
            int dayKey, @NonNull PlayableMapRegion region, @Nullable HoneyOrderCatalog.PriceLookup prices) {
        String regionKey = region.prefsValue();
        java.util.Set<String> used = new java.util.HashSet<>();
        List<String> openHex = d.openDestHexIds();
        if (openHex != null) {
            used.addAll(openHex);
        }
        clearClusteredOrders(context, d, dayKey, regionKey, used);
        List<HexParcel> parcels = IberiaHexOverlayStore.getParcels(context.getApplicationContext(), region);
        List<HoneyOrderEntity> fresh = new ArrayList<>();
        List<String> trimmed = new ArrayList<>();
        long now = System.currentTimeMillis();
        AppDatabase.getInstance(context.getApplicationContext()).runInTransaction(() -> {
            for (int b = 0; b < OfferBand.COUNT; b++) {
                OfferBand band = OfferBand.at(b);
                List<HoneyOrderEntity> openBand = d.getOpenRegionBand(regionKey, band.index);
                int want = HoneyOrderCatalog.bandDailyCount(band,
                        HoneyOrderCatalog.countEligible(parcels, band), region);
                int have = 0;
                if (openBand != null) {
                    for (int i = 0; i < openBand.size(); i++) {
                        HoneyOrderEntity e = openBand.get(i);
                        if (e == null || e.id == null || e.taken) {
                            continue;
                        }
                        if (have >= want) {
                            used.remove(e.destHexId);
                            trimmed.add(e.id);
                            d.delete(e.id);
                            continue;
                        }
                        have++;
                    }
                }
                List<HoneyOrder> spawned = HoneyOrderCatalog.spawnBand(
                        context, dayKey, region, prices, band, have, used);
                if (spawned.isEmpty()) {
                    continue;
                }
                Log.i(TAG, "banda " + band.index + " nv." + band.minLevel + "–" + band.maxLevel
                        + " había " + have + " se añaden " + spawned.size());
                List<HoneyOrderEntity> batch = new ArrayList<>(spawned.size());
                for (HoneyOrder o : spawned) {
                    if (o.expired(now)) {
                        continue;
                    }
                    used.add(o.destHexId);
                    HoneyOrderEntity row = o.toEntity();
                    batch.add(row);
                    fresh.add(row);
                }
                if (!batch.isEmpty()) {
                    d.insertIgnore(batch);
                }
            }
        });
        for (int i = 0; i < trimmed.size(); i++) {
            deleteCloud(trimmed.get(i));
        }
        writeCloudAsync(fresh);
    }

    private static void clearClusteredOrders(@NonNull Context context, @NonNull HoneyOrderDao d,
            int dayKey, @NonNull String regionKey, @NonNull java.util.Set<String> used) {
        String flag = "scatter_v3_eligible_" + regionKey + "_" + dayKey;
        if (context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(flag, false)) {
            return;
        }
        List<String> cloudIds = new ArrayList<>();
        AppDatabase.getInstance(context.getApplicationContext()).runInTransaction(() -> {
            for (int b = 0; b < OfferBand.COUNT; b++) {
                List<HoneyOrderEntity> open = d.getOpenRegionBand(regionKey, b);
                if (open == null) {
                    continue;
                }
                for (int i = 0; i < open.size(); i++) {
                    HoneyOrderEntity e = open.get(i);
                    if (e == null || e.id == null || e.taken) {
                        continue;
                    }
                    used.remove(e.destHexId);
                    cloudIds.add(e.id);
                    d.delete(e.id);
                }
            }
        });
        for (int i = 0; i < cloudIds.size(); i++) {
            deleteCloud(cloudIds.get(i));
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(flag, true).apply();
    }

    /**
     * Comandas ya generadas con flora de otra región (p. ej. Madagascar usaba el pool de Sudáfrica).
     */
    private static void repairOpenOrderFlora(@NonNull Context context, @NonNull HoneyOrderDao d,
            @Nullable HoneyOrderCatalog.PriceLookup prices) {
        List<HoneyOrderEntity> open = d.getOpenSync();
        if (open == null || open.isEmpty()) {
            return;
        }
        Context app = context.getApplicationContext();
        List<HoneyOrderEntity> changed = new ArrayList<>();
        for (HoneyOrderEntity e : open) {
            if (e == null || e.taken || e.destHexId == null || e.destHexId.isEmpty()) {
                continue;
            }
            HexParcel dest = IberiaHexOverlayStore.findById(app, e.destHexId);
            if (dest == null || HexFlora.isAllowedOnParcel(e.floraKey, dest)) {
                continue;
            }
            long seed = e.id != null ? e.id.hashCode() : 0L;
            String flora = HoneyOrderCatalog.floraForParcel(dest, seed);
            if (flora == null || flora.equals(e.floraKey)) {
                continue;
            }
            e.floraKey = flora;
            double base = HoneyMarketEngine.priceCeilingEurPerKgForFlora(flora)
                    * HoneyOrderCatalog.orderScarcity(flora);
            if (base > 0.0) {
                e.unitPrice = Math.round(base * HoneyOrderCatalog.PRICE_BONUS * 100.0) / 100.0;
            }
            changed.add(e);
        }
        if (changed.isEmpty()) {
            return;
        }
        AppDatabase.getInstance(app).runInTransaction(() -> {
            for (HoneyOrderEntity row : changed) {
                d.upsert(row);
            }
        });
        writeCloudAsync(changed);
    }

    /** Comandas abiertas con kg fuera del pool actual (0,5–30) se vuelven a sortear. */
    private static void repairOpenOrderKg(@NonNull HoneyOrderDao d) {
        List<HoneyOrderEntity> open = d.getOpenSync();
        if (open == null || open.isEmpty()) {
            return;
        }
        List<HoneyOrderEntity> changed = new ArrayList<>();
        for (HoneyOrderEntity e : open) {
            if (e == null || e.taken) {
                continue;
            }
            OfferBand band = OfferBand.at(e.band);
            if (e.kg + 1e-6 >= band.kgMin && e.kg - 1e-6 <= band.kgMax) {
                continue;
            }
            long seed = e.id != null ? e.id.hashCode() : (long) e.createdDayKey * 31L + e.band;
            e.kg = band.kgForSeed(seed);
            changed.add(e);
        }
        if (changed.isEmpty()) {
            return;
        }
        for (HoneyOrderEntity row : changed) {
            d.upsert(row);
        }
        writeCloudAsync(changed);
    }

    private static String spawnKey(int dayKey, @NonNull String region) {
        return "spawned_utc_" + region + "_" + dayKey;
    }

    private static void replenishExpired(@NonNull Context context, @NonNull HoneyOrderDao d,
            @Nullable HoneyOrderCatalog.PriceLookup prices, long nowMs) {
        List<HoneyOrderEntity> expired = d.getExpiredOpen(nowMs);
        if (expired == null || expired.isEmpty()) {
            return;
        }
        java.util.Set<String> used = new java.util.HashSet<>();
        List<String> openHex = d.openDestHexIds();
        if (openHex != null) {
            used.addAll(openHex);
        }
        List<HoneyOrderEntity> fresh = new ArrayList<>();
        AppDatabase.getInstance(context.getApplicationContext()).runInTransaction(() -> {
            for (HoneyOrderEntity dead : expired) {
                HoneyOrder next = HoneyOrderCatalog.replenishFrom(context, dead, prices, nowMs, used);
                d.delete(dead.id);
                if (next == null) {
                    continue;
                }
                used.add(next.destHexId);
                HoneyOrderEntity row = next.toEntity();
                d.insertIgnore(row);
                fresh.add(row);
            }
        });
        writeCloudAsync(fresh);
        for (HoneyOrderEntity dead : expired) {
            deleteCloud(dead.id);
        }
    }

    private static void deleteCloud(@Nullable String orderId) {
        if (orderId == null || orderId.isEmpty() || GameServer.enabled()
                || cloudWritesBlocked || currentUid().isEmpty()) {
            return;
        }
        try {
            ((FirebaseFirestore) null).collection(COLLECTION).document(orderId)
                    .delete()
                    .addOnFailureListener(HoneyOrderStore::onCloudWriteFailed);
        } catch (Exception ignored) {
        }
    }

    private static boolean alreadySpawned(@NonNull Context context, int dayKey, @NonNull String region) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(spawnKey(dayKey, region), false);
    }

    private static void markSpawned(@NonNull Context context, int dayKey, @NonNull String region) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(spawnKey(dayKey, region), true)
                .apply();
    }

    public static boolean claim(@NonNull Context context, @Nullable String orderId,
            @Nullable String uid) {
        if (orderId == null || orderId.isEmpty()) {
            return false;
        }
        String owner = uid != null ? uid : "";
        HoneyOrderDao d = dao(context);
        if (GameServer.enabled()) {
            if (owner.isEmpty() || !GameServer.claimOrder(orderId, owner)) {
                return false;
            }
            HoneyOrderEntity authoritative = GameServer.takeLastClaimedOrder();
            if (authoritative != null && orderId.equals(authoritative.id)) {
                authoritative.taken = true;
                if (authoritative.claimedBy == null || authoritative.claimedBy.isEmpty()) {
                    authoritative.claimedBy = owner;
                }
                d.upsert(authoritative);
            } else if (d.markClaimed(orderId, owner) <= 0) {
                GameServer.syncOffersBlocking(context);
            }
            return true;
        }
        HoneyOrderEntity row = d.getById(orderId);
        long now = System.currentTimeMillis();
        if (row == null || row.taken || row.expireEpochMs <= now) {
            return false;
        }
        if (d.markClaimed(orderId, owner) <= 0) {
            return false;
        }
        patchCloud(orderId, true, owner);
        return true;
    }

    public static void finish(@NonNull Context context, @Nullable String orderId) {
        if (orderId == null || orderId.isEmpty()) {
            return;
        }
        HoneyOrderDao d = dao(context);
        HoneyOrderEntity dead = d.getById(orderId);
        if (dead != null && dead.claimedBy != null && !dead.claimedBy.isEmpty()) {
            RankingCounters.recordOrder(context, dead.claimedBy);
        }
        if (GameServer.enabled()) {
            String owner = dead != null && dead.claimedBy != null ? dead.claimedBy : "";
            if (!owner.isEmpty()) {
                GameServer.finishOrder(orderId, owner);
            }
            d.delete(orderId);
            return;
        }
        d.delete(orderId);
        deleteCloud(orderId);
        if (dead == null) {
            return;
        }
        java.util.Set<String> used = new java.util.HashSet<>();
        List<String> openHex = d.openDestHexIds();
        if (openHex != null) {
            used.addAll(openHex);
        }
        HoneyOrder next = HoneyOrderCatalog.replenishFrom(context, dead, null, System.currentTimeMillis(), used);
        if (next == null) {
            return;
        }
        HoneyOrderEntity row = next.toEntity();
        d.insertIgnore(row);
        List<HoneyOrderEntity> fresh = new ArrayList<>();
        fresh.add(row);
        writeCloudAsync(fresh);
    }

    public static void releaseAllClaimedBy(@NonNull Context context, @Nullable String uid) {
        if (uid == null || uid.isEmpty()) {
            return;
        }
        List<HoneyOrderEntity> rows = dao(context).getClaimedBy(uid);
        if (rows == null) {
            return;
        }
        for (HoneyOrderEntity row : rows) {
            if (row != null && row.id != null) {
                release(context, row.id);
            }
        }
    }

    public static void release(@NonNull Context context, @Nullable String orderId) {
        if (orderId == null || orderId.isEmpty()) {
            return;
        }
        if (GameServer.enabled()) {
            HoneyOrderEntity row = dao(context).getById(orderId);
            String owner = row != null && row.claimedBy != null ? row.claimedBy : "";
            if (!owner.isEmpty() && GameServer.releaseOrder(orderId, owner)) {
                dao(context).unmarkTaken(orderId);
            }
            return;
        }
        dao(context).unmarkTaken(orderId);
        patchCloud(orderId, false, "");
    }

    @Nullable
    public static HoneyOrder get(@NonNull Context context, @Nullable String id) {
        if (id == null || id.isEmpty()) {
            return null;
        }
        HoneyOrderEntity e = dao(context).getById(id);
        return e == null || e.taken ? null : HoneyOrder.fromEntity(e);
    }

    @NonNull
    private static Map<String, Object> toFirestore(@NonNull HoneyOrderEntity e) {
        Map<String, Object> m = new HashMap<>();
        m.put("npcName", e.npcName);
        m.put("portraitIndex", e.portraitIndex);
        m.put("floraKey", e.floraKey);
        m.put("kg", e.kg);
        m.put("unitPrice", e.unitPrice);
        m.put("destHexId", e.destHexId);
        m.put("destLat", e.destLat);
        m.put("destLng", e.destLng);
        m.put("destLabel", e.destLabel);
        m.put("region", e.region);
        m.put("createdDayKey", e.createdDayKey);
        m.put("expireEpochMs", e.expireEpochMs);
        m.put("taken", e.taken);
        m.put("claimedBy", e.claimedBy != null ? e.claimedBy : "");
        m.put("band", e.band);
        return m;
    }

    /** No bloquear el arranque: set() de Firestore solo termina cuando llega al servidor. */
    private static void writeCloudAsync(@Nullable List<HoneyOrderEntity> rows) {
        if (rows == null || rows.isEmpty() || GameServer.enabled()
                || cloudWritesBlocked || currentUid().isEmpty()) {
            return;
        }
        FirebaseFirestore fs;
        try {
            fs = ((FirebaseFirestore) null);
        } catch (Exception e) {
            return;
        }
        IO.execute(() -> {
            if (cloudWritesBlocked) {
                return;
            }
            try {
                WriteBatch batch = fs.batch();
                int n = 0;
                for (HoneyOrderEntity row : rows) {
                    if (row == null || row.id == null || row.id.isEmpty()) {
                        continue;
                    }
                    batch.set(fs.collection(COLLECTION).document(row.id),
                            toFirestore(row), SetOptions.merge());
                    n++;
                    if (n >= 400) {
                        commitBatch(batch);
                        batch = fs.batch();
                        n = 0;
                    }
                }
                if (n > 0) {
                    commitBatch(batch);
                }
            } catch (Exception e) {
                onCloudWriteFailed(e);
            }
        });
    }

    private static void commitBatch(@NonNull WriteBatch batch) {
        batch.commit().addOnFailureListener(HoneyOrderStore::onCloudWriteFailed);
    }

    private static void patchCloud(@NonNull String orderId, boolean taken, @NonNull String claimedBy) {
        if (GameServer.enabled() || cloudWritesBlocked || currentUid().isEmpty()) {
            return;
        }
        try {
            Map<String, Object> patch = new HashMap<>();
            patch.put("taken", taken);
            patch.put("claimedBy", claimedBy);
            ((FirebaseFirestore) null).collection(COLLECTION).document(orderId)
                    .set(patch, SetOptions.merge())
                    .addOnFailureListener(HoneyOrderStore::onCloudWriteFailed);
        } catch (Exception e) {
            Log.w(TAG, "honeyOrders patch", e);
        }
    }

    @Nullable
    private static HoneyOrderEntity fromFirestore(@NonNull String id, @Nullable Map<String, Object> data) {
        if (data == null) {
            return null;
        }
        HoneyOrderEntity e = new HoneyOrderEntity();
        e.id = id;
        Object npc = data.get("npcName");
        e.npcName = npc instanceof String ? (String) npc : "";
        e.portraitIndex = data.get("portraitIndex") instanceof Number
                ? ((Number) data.get("portraitIndex")).intValue() : 0;
        e.floraKey = data.get("floraKey") instanceof String ? (String) data.get("floraKey") : "";
        e.kg = data.get("kg") instanceof Number ? ((Number) data.get("kg")).doubleValue() : 0;
        e.unitPrice = data.get("unitPrice") instanceof Number
                ? ((Number) data.get("unitPrice")).doubleValue() : 0;
        e.destHexId = data.get("destHexId") instanceof String ? (String) data.get("destHexId") : "";
        e.destLat = data.get("destLat") instanceof Number ? ((Number) data.get("destLat")).doubleValue() : 0;
        e.destLng = data.get("destLng") instanceof Number ? ((Number) data.get("destLng")).doubleValue() : 0;
        e.destLabel = data.get("destLabel") instanceof String ? (String) data.get("destLabel") : "";
        e.region = data.get("region") instanceof String ? (String) data.get("region") : "";
        e.createdDayKey = data.get("createdDayKey") instanceof Number
                ? ((Number) data.get("createdDayKey")).intValue() : 0;
        e.expireEpochMs = data.get("expireEpochMs") instanceof Number
                ? ((Number) data.get("expireEpochMs")).longValue() : 0;
        e.taken = Boolean.TRUE.equals(data.get("taken"));
        e.claimedBy = data.get("claimedBy") instanceof String ? (String) data.get("claimedBy") : "";
        e.band = data.get("band") instanceof Number ? ((Number) data.get("band")).intValue() : 0;
        return e;
    }

    private static void onCloudWriteFailed(@NonNull Exception e) {
        Throwable cause = e;
        while (cause != null) {
            if (cause instanceof FirebaseFirestoreException
                    && ((FirebaseFirestoreException) cause).getCode()
                    == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                if (!cloudWritesBlocked) {
                    cloudWritesBlocked = true;
                    Log.w(TAG, "honeyOrders: Firestore deniega escritura "
                            + "(despliega firestore.rules). Las comandas siguen en el móvil.");
                }
                return;
            }
            cause = cause.getCause();
        }
        if (!cloudWritesBlocked) {
            Log.w(TAG, "honeyOrders cloud", e);
        }
    }

    @NonNull
    private static String currentUid() {
        try {
            SignedInUser u = PlayerAuth.getInstance().getCurrentUser();
            return u != null && u.getUid() != null ? u.getUid() : "";
        } catch (Exception e) {
            return "";
        }
    }
}
