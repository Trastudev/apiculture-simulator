package com.apiculture.simulator.data.repository;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.apiculture.simulator.data.local.AppDatabase;
import com.apiculture.simulator.data.local.dao.PollinationOfferDao;
import com.apiculture.simulator.data.local.entity.PollinationOfferEntity;
import com.apiculture.simulator.domain.game.GameCalendar;
import com.apiculture.simulator.domain.game.NpcContractFarm;
import com.apiculture.simulator.domain.game.OfferBand;
import com.apiculture.simulator.domain.game.OfferReplenish;
import com.apiculture.simulator.domain.game.PollinationOfferCatalog;
import com.apiculture.simulator.domain.map.PlayableMapRegion;
import com.apiculture.simulator.domain.parcel.HexParcel;
import com.apiculture.simulator.data.session.PlayerAuth;
import com.apiculture.simulator.data.session.SignedInUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.WriteBatch;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class PollinationOfferStore {

    private static final String TAG = "PollinationOfferStore";
    public static final String COLLECTION = "pollinationOffers";
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final String PREFS = "pollination_offers";
    private static volatile boolean cloudWritesBlocked;

    private PollinationOfferStore() {
    }

    @NonNull
    private static PollinationOfferDao dao(@NonNull Context context) {
        return AppDatabase.getInstance(context.getApplicationContext()).pollinationOfferDao();
    }

    public static void maintain(@NonNull Context context) {
        Context app = context.getApplicationContext();
        IO.execute(() -> maintainNow(app));
    }

    public static void maintainNow(@NonNull Context context) {
        PollinationOfferDao d = dao(context);
        if (GameServer.enabled()) {
            // Las ofertas open y su reposición son autoritativas del backend.
            if (GameServer.syncOffersBlocking(context)) {
                d.prune(System.currentTimeMillis());
            }
            return;
        }
        dropStartedBeforeToday(context, d);
        replenishExpired(context, d, System.currentTimeMillis());
        d.prune(System.currentTimeMillis());
        int dayKey = GameCalendar.currentGlobalMarketDayKey();
        spawnDayIfNeeded(context, d, dayKey, MapRegionPrefs.get(context), null);
    }

    /** Solo el mapa activo y la banda del jugador. */
    public static void ensureRegionNow(@NonNull Context context, @NonNull PlayableMapRegion region,
            int playerLevel) {
        if (GameServer.enabled()) {
            GameServer.syncOffersBlocking(context);
            return;
        }
        PollinationOfferDao d = dao(context);
        dropStartedBeforeToday(context, d);
        replenishExpired(context, d, System.currentTimeMillis());
        d.prune(System.currentTimeMillis());
        spawnDayIfNeeded(context, d, GameCalendar.currentGlobalMarketDayKey(), region,
                OfferBand.ofLevel(playerLevel));
    }

    public static void maintainRegion(@NonNull Context context, @NonNull PlayableMapRegion region) {
        Context app = context.getApplicationContext();
        IO.execute(() -> {
            if (GameServer.enabled()) {
                GameServer.syncOffersBlocking(app);
                return;
            }
            spawnDayIfNeeded(app, dao(app), GameCalendar.currentGlobalMarketDayKey(), region, null);
        });
    }

    @NonNull
    public static List<PollinationOfferEntity> openForPlayer(@NonNull Context context,
            @NonNull PlayableMapRegion region, int playerLevel) {
        OfferBand band = OfferBand.ofLevel(playerLevel);
        List<PollinationOfferEntity> rows = dao(context).getOpenBand(
                region.prefsValue(), band.index, System.currentTimeMillis());
        return rows != null ? rows : new ArrayList<>();
    }

    public static void takeAndReplenish(@NonNull Context context, @Nullable String hexId) {
        if (hexId == null || hexId.isEmpty()) {
            return;
        }
        PollinationOfferDao d = dao(context);
        if (GameServer.enabled()) {
            // La aceptación server-first usa la oferta exacta (hex, banda, flora
            // y tramo). No se permite el camino legacy "por hex" en modo API.
            return;
        }
        PollinationOfferEntity local = d.getOpenForHex(hexId);
        if (local == null) {
            return;
        }
        PollinationOfferEntity dead = local;
        d.markTaken(dead.id);
        d.delete(dead.id);
        deleteCloud(dead.id);
        spawnReplacement(context, d, dead);
    }

    private static void spawnDayIfNeeded(@NonNull Context context, @NonNull PollinationOfferDao d,
            int dayKey, @NonNull PlayableMapRegion region, @Nullable OfferBand onlyBand) {
        String regionKey = region.prefsValue();
        List<HexParcel> parcels = IberiaHexOverlayStore.getParcels(context.getApplicationContext(), region);
        int parcelN = parcels != null ? parcels.size() : 0;
        LocalDate today = LocalDate.now(GameCalendar.userTimeZone());
        long expire = OfferReplenish.utcDayEndEpochMs(dayKey);
        long now = System.currentTimeMillis();
        Set<String> used = new HashSet<>();
        List<String> openHex = d.openHexIds();
        if (openHex != null) {
            used.addAll(openHex);
        }
        clearClusteredPool(context, d, dayKey, regionKey, onlyBand, used);
        List<PollinationOfferEntity> fresh = new ArrayList<>();
        for (int b = 0; b < OfferBand.COUNT; b++) {
            OfferBand band = OfferBand.at(b);
            if (onlyBand != null && band.index != onlyBand.index) {
                continue;
            }
            int want = onlyBand != null
                    ? PollinationOfferCatalog.bandDailyCount(band, parcelN)
                    : band.pollinationCount;
            if (want <= 0) {
                continue;
            }
            List<PollinationOfferEntity> openBand = d.getOpenBand(regionKey, band.index, now);
            int have = openBand != null ? openBand.size() : 0;
            int need = want - have;
            if (need <= 0) {
                continue;
            }
            List<NpcContractFarm> farms = PollinationOfferCatalog.spawnForBand(
                    parcels, band, today, dayKey, used, need);
            if (farms.isEmpty()) {
                continue;
            }
            Log.i(TAG, "banda " + band.index + " había " + have + " se añaden " + farms.size());
            List<PollinationOfferEntity> batch = new ArrayList<>(farms.size());
            for (int i = 0; i < farms.size(); i++) {
                NpcContractFarm farm = farms.get(i);
                if (farm == null || farm.parcel == null) {
                    continue;
                }
                used.add(farm.hexId());
                PollinationOfferEntity row = fromFarm(farm, band, regionKey, dayKey, expire, have + i);
                batch.add(row);
                fresh.add(row);
            }
            if (!batch.isEmpty()) {
                d.insertIgnore(batch);
            }
        }
        writeCloud(fresh);
    }

    private static void clearClusteredPool(@NonNull Context context, @NonNull PollinationOfferDao d,
            int dayKey, @NonNull String regionKey, @Nullable OfferBand onlyBand, @NonNull Set<String> used) {
        String flag = "scatter_v1_" + regionKey + "_" + dayKey
                + (onlyBand != null ? "_b" + onlyBand.index : "_all");
        if (context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(flag, false)) {
            return;
        }
        long now = System.currentTimeMillis();
        for (int b = 0; b < OfferBand.COUNT; b++) {
            if (onlyBand != null && b != onlyBand.index) {
                continue;
            }
            List<PollinationOfferEntity> open = d.getOpenBand(regionKey, b, now);
            if (open == null) {
                continue;
            }
            for (int i = 0; i < open.size(); i++) {
                PollinationOfferEntity e = open.get(i);
                if (e == null || e.id == null) {
                    continue;
                }
                used.remove(e.hexId);
                d.delete(e.id);
                deleteCloud(e.id);
            }
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(flag, true).apply();
    }

    private static void dropStartedBeforeToday(@NonNull Context context, @NonNull PollinationOfferDao d) {
        List<PollinationOfferEntity> open = d.getOpenSync();
        if (open == null || open.isEmpty()) {
            return;
        }
        LocalDate today = LocalDate.now(GameCalendar.userTimeZone());
        for (int i = 0; i < open.size(); i++) {
            PollinationOfferEntity row = open.get(i);
            if (row == null || row.id == null || row.taken) {
                continue;
            }
            if (!com.apiculture.simulator.domain.game.PollinationContractCrops.startedBeforeToday(
                    row.startDoy, row.endDoy, row.createdDayKey, today)) {
                continue;
            }
            d.delete(row.id);
            deleteCloud(row.id);
        }
    }

    private static void replenishExpired(@NonNull Context context, @NonNull PollinationOfferDao d, long nowMs) {
        List<PollinationOfferEntity> expired = d.getExpiredOpen(nowMs);
        if (expired == null || expired.isEmpty()) {
            return;
        }
        for (PollinationOfferEntity dead : expired) {
            d.delete(dead.id);
            deleteCloud(dead.id);
            spawnReplacement(context, d, dead);
        }
    }

    private static void spawnReplacement(@NonNull Context context, @NonNull PollinationOfferDao d,
            @NonNull PollinationOfferEntity dead) {
        int dayKey = dead.createdDayKey > 0 ? dead.createdDayKey : GameCalendar.currentGlobalMarketDayKey();
        if (!OfferReplenish.canReplenish(System.currentTimeMillis(), dayKey)) {
            return;
        }
        PlayableMapRegion region = PlayableMapRegion.fromPrefsValue(dead.region);
        if (region == null) {
            region = PlayableMapRegion.fromHexId(dead.hexId);
        }
        if (region == null) {
            return;
        }
        List<HexParcel> parcels = IberiaHexOverlayStore.getParcels(context.getApplicationContext(), region);
        HexParcel from = IberiaHexOverlayStore.findById(context.getApplicationContext(), dead.hexId);
        if (from == null) {
            from = new HexParcel(dead.hexId, new double[][]{{0, 0}, {0, 1}, {1, 1}, {1, 0}, {0.5, -0.5}, {-0.5, 0.5}},
                    dead.destLat, dead.destLng, 1.0, false, 0);
        }
        Set<String> used = new HashSet<>();
        List<String> open = d.openHexIds();
        if (open != null) {
            used.addAll(open);
        }
        OfferBand band = OfferBand.at(dead.band);
        LocalDate today = LocalDate.now(GameCalendar.userTimeZone());
        NpcContractFarm farm = PollinationOfferCatalog.replenishNear(
                parcels, from, band, today, dayKey, used);
        if (farm == null) {
            return;
        }
        long expire = OfferReplenish.utcDayEndEpochMs(dayKey);
        String id = String.format(Locale.US, "po-r-%s-%d-%d-%s", region.prefsValue(), dayKey, band.index,
                Integer.toHexString((dead.id != null ? dead.id : farm.hexId()).hashCode()));
        PollinationOfferEntity row = fromFarm(farm, band, region.prefsValue(), dayKey, expire, 0);
        row.id = id;
        d.insertIgnore(row);
        List<PollinationOfferEntity> fresh = new ArrayList<>();
        fresh.add(row);
        writeCloud(fresh);
    }

    @NonNull
    private static PollinationOfferEntity fromFarm(@NonNull NpcContractFarm farm, @NonNull OfferBand band,
            @NonNull String regionKey, int dayKey, long expire, int i) {
        PollinationOfferEntity e = new PollinationOfferEntity();
        e.id = String.format(Locale.US, "po-%s-%d-%d-%d", regionKey, dayKey, band.index, i);
        e.hexId = farm.hexId();
        e.flora = farm.flora;
        e.startDoy = farm.terms != null ? farm.terms.startDoy : 0;
        e.endDoy = farm.terms != null ? farm.terms.endDoy : 0;
        e.band = band.index;
        e.region = regionKey;
        e.createdDayKey = dayKey;
        e.expireEpochMs = expire;
        e.destLat = farm.parcel != null ? farm.parcel.centroidLat : 0;
        e.destLng = farm.parcel != null ? farm.parcel.centroidLon : 0;
        e.npcName = farm.npcName;
        e.portraitIndex = farm.portraitIndex;
        e.taken = false;
        return e;
    }

    private static String spawnKey(int dayKey, @NonNull String region) {
        return "spawned_utc_" + region + "_" + dayKey;
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

    private static void writeCloud(@Nullable List<PollinationOfferEntity> rows) {
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
            try {
                WriteBatch batch = fs.batch();
                int n = 0;
                for (PollinationOfferEntity row : rows) {
                    if (row == null || row.id == null || row.id.isEmpty()) {
                        continue;
                    }
                    batch.set(fs.collection(COLLECTION).document(row.id), toMap(row), SetOptions.merge());
                    n++;
                    if (n >= 400) {
                        batch.commit().addOnFailureListener(PollinationOfferStore::onCloudFailed);
                        batch = fs.batch();
                        n = 0;
                    }
                }
                if (n > 0) {
                    batch.commit().addOnFailureListener(PollinationOfferStore::onCloudFailed);
                }
            } catch (Exception e) {
                onCloudFailed(e);
            }
        });
    }

    private static Map<String, Object> toMap(@NonNull PollinationOfferEntity e) {
        Map<String, Object> m = new HashMap<>();
        m.put("hexId", e.hexId != null ? e.hexId : "");
        m.put("flora", e.flora != null ? e.flora : "");
        m.put("startDoy", e.startDoy);
        m.put("endDoy", e.endDoy);
        m.put("band", e.band);
        m.put("region", e.region != null ? e.region : "");
        m.put("createdDayKey", e.createdDayKey);
        m.put("expireEpochMs", e.expireEpochMs);
        m.put("destLat", e.destLat);
        m.put("destLng", e.destLng);
        m.put("npcName", e.npcName != null ? e.npcName : "");
        m.put("portraitIndex", e.portraitIndex);
        m.put("taken", e.taken);
        return m;
    }

    private static void deleteCloud(@Nullable String id) {
        if (id == null || id.isEmpty() || GameServer.enabled()
                || cloudWritesBlocked || currentUid().isEmpty()) {
            return;
        }
        try {
            ((FirebaseFirestore) null).collection(COLLECTION).document(id)
                    .delete()
                    .addOnFailureListener(PollinationOfferStore::onCloudFailed);
        } catch (Exception ignored) {
        }
    }

    private static void onCloudFailed(@NonNull Exception e) {
        Throwable cause = e;
        while (cause != null) {
            if (cause instanceof FirebaseFirestoreException
                    && ((FirebaseFirestoreException) cause).getCode()
                    == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                cloudWritesBlocked = true;
                Log.w(TAG, "pollinationOffers: Firestore deniega escritura");
                return;
            }
            cause = cause.getCause();
        }
        Log.w(TAG, "pollinationOffers cloud", e);
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
