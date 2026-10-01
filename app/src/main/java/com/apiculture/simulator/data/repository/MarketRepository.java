package com.apiculture.simulator.data.repository;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.apiculture.simulator.data.local.AppDatabase;
import com.apiculture.simulator.data.local.entity.HiveEntity;
import com.apiculture.simulator.domain.game.GameBalanceConfig;
import com.apiculture.simulator.domain.game.GameCalendar;
import com.apiculture.simulator.domain.game.GlobalEventEffects;
import com.apiculture.simulator.domain.map.PlayableMapRegion;
import com.apiculture.simulator.domain.map.ProvincialMarket;
import com.apiculture.simulator.domain.map.ProvincialMarketCatalog;
import com.apiculture.simulator.domain.market.HoneyMarketDemandRules;
import com.apiculture.simulator.domain.market.HoneyMarketEngine;
import com.apiculture.simulator.domain.market.HoneyMarketSnapshot;
import com.apiculture.simulator.domain.market.TerritorialMarketRules;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.SetOptions;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Mercado global: cupo regional compartido por tipo, actividad de jugadores ponderada
 * por nivel y presión de precio sobre el objetivo de rotación.
 */
public class MarketRepository {

    private static final String COLLECTION_ROOT = "globalHoneyMarket";
    private static final String SUB_FLORA_SALES = "floraSalesUtc";
    private static final String PLAYERS = "players";

    private static final String PREFS = "honey_market_prefs";
    private static final String KEY_DAY = "dayKey";
    private static final String KEY_JSON = "snapshotJson";
    private static final String KEY_LOCAL_FALLBACK_DAY = "local_fallback_sales_day_utc";
    private static final String KEY_LOCAL_FALLBACK_JSON = "local_fallback_sales_json_utc";
    private static final String KEY_PLAYER_COUNT = "player_count";
    private static final String KEY_ACTIVITY_IBERIA = "market_activity_iberia";
    private static final String KEY_ACTIVITY_ZA = "market_activity_za";
    private static final String KEY_ACTIVITY_MDG = "market_activity_mdg";
    static final String FIELD_HIVES_IBERIA = "hiveCountIberia";
    static final String FIELD_HIVES_ZA = "hiveCountZa";
    static final String FIELD_HIVES_MDG = "hiveCountMdg";
    private static final String KEY_SALES_HISTORY = "sales_history_by_day_json";
    private static final String FIELD_KG_SOLD = "kgSold";
    private static final String FIELD_MARKET_DAY = "marketDayKey";

    private final Context appContext;
    private final AuthRepository authRepository;
    private final FirebaseFirestore firestore;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private volatile HoneyMarketSnapshot cached;
    private volatile PlayableMapRegion cachedRegion;
    private ListenerRegistration globalSalesListener;
    private int attachedSalesDayKey = Integer.MIN_VALUE;
    private int salesListenerGeneration = 0;
    @Nullable
    private volatile Runnable snapshotChangedListener;

    public MarketRepository(Context context, @Nullable FirebaseFirestore firestore, AuthRepository authRepository) {
        this.appContext = context.getApplicationContext();
        this.firestore = firestore;
        this.authRepository = authRepository;
    }

    public void refreshGlobalMarketForDay(int dayKey, int civilDayOfYear) {
        refreshGlobalMarketForDay(dayKey, civilDayOfYear, null);
    }

    public void refreshGlobalMarketForDay(int dayKey, int civilDayOfYear, @Nullable Runnable onUpdated) {
        // Oferta/demanda mundiales: un solo día UTC, no la medianoche local de cada jugador.
        int marketDay = GameCalendar.currentGlobalMarketDayKey();
        LocalDate marketDate = GameCalendar.fromDayKey(marketDay);
        pruneSalesHistory(marketDay);
        HoneyMarketSnapshot snap = computeLiveSnapshot(marketDay, marketDate, readCachedPlayerCount());
        applySnapshot(GlobalEventEffects.applyToMarket(snap));
        notifyUpdated(onUpdated);
        io.execute(() -> {
            int players = fetchMarketScaleBlocking();
            loadRecentSalesHistoryBlocking();
            HoneyMarketSnapshot remote = computeLiveSnapshot(marketDay, marketDate, players);
            applySnapshot(GlobalEventEffects.applyToMarket(remote));
            notifyUpdated(onUpdated);
        });
    }

    /** Aviso en el hilo UI cuando el snapshot del mercado cambia (p. ej. tick / nuevo día de juego). */
    public void setSnapshotChangedListener(@Nullable Runnable listener) {
        this.snapshotChangedListener = listener;
    }

    private void notifyUpdated(@Nullable Runnable onUpdated) {
        if (onUpdated == null) {
            return;
        }
        mainHandler.post(onUpdated);
    }

    private void applySnapshot(HoneyMarketSnapshot snap) {
        if (snap == null) {
            return;
        }
        HoneyMarketSnapshot prev = cached;
        cached = snap;
        cachedRegion = activeRegion();
        persist(snap);
        if (prev == null || prev.dayKey != snap.dayKey) {
            pruneSalesHistory(snap.dayKey);
        }
        Runnable r = snapshotChangedListener;
        if (r != null) {
            mainHandler.post(r);
        }
    }

    private void pruneSalesHistory(int todayKey) {
        migrateLegacyFallbackIntoHistory();
        Map<Integer, Map<String, Double>> hist = readSalesHistory();
        LocalDate today = GameCalendar.fromDayKey(todayKey);
        int minKey = GameCalendar.toDayKey(today.minusDays(10));
        Iterator<Integer> it = hist.keySet().iterator();
        boolean changed = false;
        while (it.hasNext()) {
            Integer k = it.next();
            if (k == null || k < minKey || k > todayKey) {
                it.remove();
                changed = true;
            }
        }
        if (changed) {
            writeSalesHistory(hist);
        }
    }

    private void migrateLegacyFallbackIntoHistory() {
        SharedPreferences p = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        int day = p.getInt(KEY_LOCAL_FALLBACK_DAY, Integer.MIN_VALUE);
        String raw = p.getString(KEY_LOCAL_FALLBACK_JSON, null);
        if (day == Integer.MIN_VALUE || raw == null || raw.isEmpty() || "{}".equals(raw.trim())) {
            return;
        }
        Map<Integer, Map<String, Double>> hist = readSalesHistory();
        if (!hist.containsKey(day)) {
            Map<String, Double> m = new LinkedHashMap<>();
            try {
                JSONObject o = new JSONObject(raw);
                Iterator<String> it = o.keys();
                while (it.hasNext()) {
                    String k = it.next();
                    m.put(k, o.optDouble(k, 0.0));
                }
            } catch (Exception ignored) {
            }
            if (!m.isEmpty()) {
                hist.put(day, m);
                writeSalesHistory(hist);
            }
        }
        p.edit().remove(KEY_LOCAL_FALLBACK_JSON).remove(KEY_LOCAL_FALLBACK_DAY).apply();
    }

    private void loadRecentSalesHistoryBlocking() {
        if (firestore == null || authRepository.getCurrentUser() == null) {
            return;
        }
        LocalDate today = LocalDate.now(GameCalendar.globalMarketTimeZone());
        Map<Integer, Map<String, Double>> hist = readSalesHistory();
        try {
            List<Task<QuerySnapshot>> tasks = new ArrayList<>();
            int[] keys = new int[8];
            for (int i = 0; i < 8; i++) {
                keys[i] = GameCalendar.toDayKey(today.minusDays(i));
                tasks.add(floraSalesCollection(keys[i]).get());
            }
            Tasks.await(Tasks.whenAll(tasks), 10, TimeUnit.SECONDS);
            for (int i = 0; i < 8; i++) {
                QuerySnapshot snap = tasks.get(i).getResult();
                if (snap == null) {
                    continue;
                }
                int dayKey = keys[i];
                Map<String, Double> dayMap = new LinkedHashMap<>();
                for (DocumentSnapshot d : snap.getDocuments()) {
                    if (!isSaleForMarketDay(d, dayKey)) {
                        continue;
                    }
                    dayMap.put(d.getId(), readKgSold(d));
                }
                hist.put(dayKey, dayMap);
            }
            writeSalesHistory(hist);
        } catch (Exception ignored) {
        }
    }

    private HoneyMarketSnapshot cachedPreviousDaySnap;
    private int cachedPreviousDayKey = Integer.MIN_VALUE;
    private double cachedPreviousDayActivity = Double.NaN;
    @Nullable
    private PlayableMapRegion cachedPreviousDayRegion;
    private final Object last7Lock = new Object();
    private Map<String, double[]> cachedLast7;
    private int cachedLast7DayKey = Integer.MIN_VALUE;
    private int cachedLast7Players = Integer.MIN_VALUE;
    private double cachedLast7Activity = Double.NaN;
    @Nullable
    private PlayableMapRegion cachedLast7Region;
    private Map<Integer, Map<String, Double>> salesHistoryMem;

    private double postedPriceLocked(String floraCanonical, HoneyMarketSnapshot todaySnap) {
        HoneyMarketSnapshot prev = previousDaySnap(todaySnap);
        LocalDate yest = GameCalendar.fromDayKey(todaySnap.dayKey).minusDays(1);
        return HoneyMarketEngine.postedDailyPriceEurPerKg(
                floraCanonical, todaySnap, prev, soldKgOn(GameCalendar.toDayKey(yest), floraCanonical));
    }

    private HoneyMarketSnapshot previousDaySnap(HoneyMarketSnapshot todaySnap) {
        LocalDate yest = GameCalendar.fromDayKey(todaySnap.dayKey).minusDays(1);
        int yestKey = GameCalendar.toDayKey(yest);
        PlayableMapRegion region = activeRegion();
        if (cachedPreviousDaySnap != null
                && cachedPreviousDayKey == yestKey
                && cachedPreviousDayRegion == region
                && sameDouble(cachedPreviousDayActivity, todaySnap.activityUnits)) {
            return cachedPreviousDaySnap;
        }
        cachedPreviousDaySnap = HoneyMarketEngine.computeSnapshot(
                yestKey, yest.getDayOfYear(), region,
                todaySnap.activityUnits, todaySnap.playerCount);
        cachedPreviousDayKey = yestKey;
        cachedPreviousDayActivity = todaySnap.activityUnits;
        cachedPreviousDayRegion = region;
        return cachedPreviousDaySnap;
    }


    private double soldKgOn(int dayKey, String floraCanonical) {
        Map<String, Double> day = readSalesHistory().get(dayKey);
        if (day == null) {
            return 0.0;
        }
        String keyed = TerritorialMarketRules.salesKey(activeRegion(), floraCanonical);
        if (day.containsKey(keyed)) {
            return day.getOrDefault(keyed, 0.0);
        }
        return day.getOrDefault(floraCanonical, 0.0);
    }

    private Map<String, Double> readDaySalesMap(int dayKey) {
        Map<String, Double> day = readSalesHistory().get(dayKey);
        return day != null ? new LinkedHashMap<>(day) : new LinkedHashMap<>();
    }

    private void writeSoldTotal(int dayKey, String floraCanonical, double totalKg) {
        Map<Integer, Map<String, Double>> hist = readSalesHistory();
        Map<String, Double> day = hist.get(dayKey);
        if (day == null) {
            day = new LinkedHashMap<>();
            hist.put(dayKey, day);
        }
        if (totalKg <= 1e-9) {
            day.remove(floraCanonical);
        } else {
            day.put(floraCanonical, totalKg);
        }
        writeSalesHistory(hist);
    }

    private Map<Integer, Map<String, Double>> readSalesHistory() {
        if (salesHistoryMem != null) {
            return salesHistoryMem;
        }
        Map<Integer, Map<String, Double>> out = new LinkedHashMap<>();
        SharedPreferences p = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String raw = p.getString(KEY_SALES_HISTORY, "{}");
        if (raw == null || raw.isEmpty()) {
            salesHistoryMem = out;
            return out;
        }
        try {
            JSONObject root = new JSONObject(raw);
            Iterator<String> days = root.keys();
            while (days.hasNext()) {
                String dayStr = days.next();
                int dayKey;
                try {
                    dayKey = Integer.parseInt(dayStr);
                } catch (NumberFormatException e) {
                    continue;
                }
                JSONObject flora = root.optJSONObject(dayStr);
                Map<String, Double> m = new LinkedHashMap<>();
                if (flora != null) {
                    Iterator<String> it = flora.keys();
                    while (it.hasNext()) {
                        String k = it.next();
                        m.put(k, flora.optDouble(k, 0.0));
                    }
                }
                out.put(dayKey, m);
            }
        } catch (Exception ignored) {
        }
        salesHistoryMem = out;
        return out;
    }

    private void writeSalesHistory(Map<Integer, Map<String, Double>> hist) {
        salesHistoryMem = hist;
        invalidateLast7Cache();
        try {
            JSONObject root = new JSONObject();
            for (Map.Entry<Integer, Map<String, Double>> day : hist.entrySet()) {
                if (day.getKey() == null || day.getValue() == null) {
                    continue;
                }
                JSONObject flora = new JSONObject();
                for (Map.Entry<String, Double> e : day.getValue().entrySet()) {
                    if (e.getValue() != null && e.getValue() > 1e-9) {
                        flora.put(e.getKey(), e.getValue());
                    }
                }
                root.put(String.valueOf(day.getKey()), flora);
            }
            appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY_SALES_HISTORY, root.toString())
                    .apply();
        } catch (Exception ignored) {
        }
    }

    private void persist(HoneyMarketSnapshot s) {
        try {
            JSONObject o = new JSONObject();
            o.put(KEY_DAY, s.dayKey);
            o.put("totalDemand", s.totalDemandKg);
            o.put("totalTurnoverTarget", s.totalTurnoverTargetKg);
            o.put("demandSeason", s.demandSeasonFactor);
            o.put("noise", s.dailyNoiseMultiplier);
            o.put("priceTension", s.priceTension01);
            o.put("playerCount", s.playerCount);
            o.put("activityUnits", s.activityUnits);
            o.put("demandByFlora", new JSONObject(s.demandKgByFlora));
            o.put("turnoverByFlora", new JSONObject(s.turnoverTargetKgByFlora));
            o.put("priceByFlora", new JSONObject(s.priceEurPerKgByFlora));
            SharedPreferences p = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            p.edit()
                    .putInt(KEY_DAY, s.dayKey)
                    .putInt(KEY_PLAYER_COUNT, s.playerCount)
                    .putFloat(activityPrefKey(cachedRegion), (float) s.activityUnits)
                    .putString(KEY_JSON, o.toString())
                    .apply();
        } catch (Exception ignored) {
        }
    }

    private int readCachedPlayerCount() {
        SharedPreferences p = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return Math.max(1, p.getInt(KEY_PLAYER_COUNT, 1));
    }

    @NonNull
    private PlayableMapRegion activeRegion() {
        return MapRegionPrefs.get(appContext);
    }

    private HoneyMarketSnapshot computeLiveSnapshot(int marketDay, LocalDate marketDate, int players) {
        PlayableMapRegion region = activeRegion();
        return HoneyMarketEngine.computeSnapshot(
                marketDay, marketDate.getDayOfYear(), region, activityFor(region), players);
    }

    private double activityFor(@NonNull PlayableMapRegion region) {
        SharedPreferences p = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        float cached = p.getFloat(activityPrefKey(region), -1f);
        if (cached > 0f) {
            return cached;
        }
        return Math.max(1.0, localHiveCount(region));
    }

    @NonNull
    private static String activityPrefKey(@Nullable PlayableMapRegion region) {
        PlayableMapRegion r = region != null ? region : PlayableMapRegion.IBERIA;
        if (r == PlayableMapRegion.SOUTH_AFRICA) {
            return KEY_ACTIVITY_ZA;
        }
        if (r == PlayableMapRegion.MADAGASCAR) {
            return KEY_ACTIVITY_MDG;
        }
        return KEY_ACTIVITY_IBERIA;
    }

    private int localHiveCount(@NonNull PlayableMapRegion region) {
        int n = 0;
        try {
            List<HiveEntity> hives = AppDatabase.getInstance(appContext).hiveDao().getAllHivesSync();
            if (hives != null) {
                for (HiveEntity h : hives) {
                    if (h == null || h.inWarehouse) {
                        continue;
                    }
                    if (PlayableMapRegion.fromHexId(h.hexId) == region) {
                        n++;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return n;
    }

    @NonNull
    private Map<Integer, Map<String, Double>> floraSalesForRegion(
            @Nullable Map<Integer, Map<String, Double>> hist, @NonNull PlayableMapRegion region) {
        Map<Integer, Map<String, Double>> out = new LinkedHashMap<>();
        if (hist == null) {
            return out;
        }
        String prefix = region.prefsValue() + "__";
        for (Map.Entry<Integer, Map<String, Double>> e : hist.entrySet()) {
            if (e.getKey() == null || e.getValue() == null) {
                continue;
            }
            Map<String, Double> regionalTotals = new LinkedHashMap<>();
            Map<String, Double> marketBreakdown = new LinkedHashMap<>();
            for (Map.Entry<String, Double> row : e.getValue().entrySet()) {
                String id = row.getKey();
                if (id == null || row.getValue() == null) {
                    continue;
                }
                if (id.startsWith(prefix)) {
                    String remainder = id.substring(prefix.length());
                    int split = remainder.indexOf("@@");
                    if (split >= 0) {
                        String flora = remainder.substring(0, split);
                        marketBreakdown.merge(flora, row.getValue(), Double::sum);
                    } else {
                        regionalTotals.put(remainder, row.getValue());
                    }
                } else if (!id.contains("__")) {
                    regionalTotals.merge(id, row.getValue(), Double::sum);
                }
            }
            Map<String, Double> day = new LinkedHashMap<>(regionalTotals);
            for (Map.Entry<String, Double> row : marketBreakdown.entrySet()) {
                day.putIfAbsent(row.getKey(), row.getValue());
            }
            out.put(e.getKey(), day);
        }
        return out;
    }

    private int fetchMarketScaleBlocking() {
        double[] localActivity = {
                activityFor(PlayableMapRegion.IBERIA),
                activityFor(PlayableMapRegion.SOUTH_AFRICA),
                activityFor(PlayableMapRegion.MADAGASCAR)
        };
        int players = readCachedPlayerCount();
        if (firestore != null && authRepository.getCurrentUser() != null) {
            try {
                QuerySnapshot snap = Tasks.await(
                        firestore.collection(PLAYERS).get(), 10, TimeUnit.SECONDS);
                if (snap != null) {
                    int activePlayers = 0;
                    double[] weightedActivity = {0.0, 0.0, 0.0};
                    long now = System.currentTimeMillis();
                    for (DocumentSnapshot d : snap.getDocuments()) {
                        if (!isActiveMarketPlayer(d, now)) {
                            continue;
                        }
                        activePlayers++;
                        int level = (int) Math.max(0L, longField(d, "level"));
                        int ib = (int) Math.max(0L, longField(d, FIELD_HIVES_IBERIA));
                        int za = (int) Math.max(0L, longField(d, FIELD_HIVES_ZA));
                        int mdg = (int) Math.max(0L, longField(d, FIELD_HIVES_MDG));
                        if (ib + za + mdg > 0) {
                            if (ib > 0) {
                                weightedActivity[0] += HoneyMarketDemandRules.playerRegionActivity(ib, level);
                            }
                            if (za > 0) {
                                weightedActivity[1] += HoneyMarketDemandRules.playerRegionActivity(za, level);
                            }
                            if (mdg > 0) {
                                weightedActivity[2] += HoneyMarketDemandRules.playerRegionActivity(mdg, level);
                            }
                        } else {
                            PlayableMapRegion home = PlayableMapRegion.fromPrefsValue(d.getString("mapRegion"));
                            int index = regionIndex(home);
                            weightedActivity[index] += HoneyMarketDemandRules.playerRegionActivity(0, level);
                        }
                    }
                    if (activePlayers > 0) {
                        players = activePlayers;
                        localActivity[0] = Math.max(1.0, weightedActivity[0]);
                        localActivity[1] = Math.max(1.0, weightedActivity[1]);
                        localActivity[2] = Math.max(1.0, weightedActivity[2]);
                    }
                }
            } catch (Exception ignored) {
            }
        }
        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putInt(KEY_PLAYER_COUNT, players)
                .putFloat(KEY_ACTIVITY_IBERIA, (float) localActivity[0])
                .putFloat(KEY_ACTIVITY_ZA, (float) localActivity[1])
                .putFloat(KEY_ACTIVITY_MDG, (float) localActivity[2])
                .apply();
        return players;
    }

    private static boolean isActiveMarketPlayer(@NonNull DocumentSnapshot document, long nowMs) {
        int days = Math.max(0, GameBalanceConfig.honeyMarketActivePlayerDays);
        if (days <= 0) {
            return true;
        }
        Object raw = document.get("updatedAt");
        if (!(raw instanceof com.google.firebase.Timestamp)) {
            return true; // Documento legado sin fecha: no se expulsa de golpe.
        }
        long updatedMs = ((com.google.firebase.Timestamp) raw).toDate().getTime();
        return updatedMs >= nowMs - days * 86_400_000L;
    }

    private static int regionIndex(@NonNull PlayableMapRegion region) {
        if (region == PlayableMapRegion.SOUTH_AFRICA) {
            return 1;
        }
        if (region == PlayableMapRegion.MADAGASCAR) {
            return 2;
        }
        return 0;
    }

    private static long longField(@Nullable DocumentSnapshot d, @NonNull String field) {
        if (d == null) {
            return 0L;
        }
        Object raw = d.get(field);
        if (raw instanceof Number) {
            return ((Number) raw).longValue();
        }
        return 0L;
    }

    @Nullable
    public HoneyMarketSnapshot getSnapshot() {
        ensureGlobalMarketDaySnapshot();
        return cached;
    }

    /** El mercado mundial sigue el día UTC; el snapshot en memoria no puede quedar en ayer. */
    private void ensureGlobalMarketDaySnapshot() {
        LocalDate marketDate = LocalDate.now(GameCalendar.globalMarketTimeZone());
        int todayKey = GameCalendar.toDayKey(marketDate);
        if (cached != null && cached.dayKey == todayKey && cachedRegion == activeRegion()) {
            return;
        }
        pruneSalesHistory(todayKey);
        applySnapshot(GlobalEventEffects.applyToMarket(computeLiveSnapshot(
                todayKey, marketDate, readCachedPlayerCount())));
    }

    /** Precio del día (fijo): temporada de hoy × venta/objetivo de rotación de ayer. */
    public double priceEurPerKgForFloraWithSold(@Nullable String floraType, double globalSoldKgIgnored) {
        return priceEurPerKgForFlora(floraType);
    }

    public double priceEurPerKgForFlora(@Nullable String floraType) {
        return priceEurPerKgForFlora(floraType, null);
    }

    public double priceEurPerKgForFlora(@Nullable String floraType, @Nullable ProvincialMarket market) {
        HoneyMarketSnapshot s = getSnapshot();
        if (s == null) {
            return 12.0;
        }
        double capital = postedPriceLocked(HoneyMarketEngine.canonicalFloraKey(floraType), s);
        if (market == null || market.international) {
            return capital;
        }
        List<ProvincialMarket> all = ProvincialMarketCatalog.resolve(appContext, market.region);
        return TerritorialMarketRules.priceAtMarket(
                capital, market, HoneyMarketEngine.canonicalFloraKey(floraType), all);
    }

    public double dailyDemandKg(@Nullable ProvincialMarket market, @Nullable String floraType) {
        if (market == null || market.international) {
            return 0.0;
        }
        HoneyMarketSnapshot s = getSnapshot();
        if (s == null) {
            return 0.0;
        }
        String flora = HoneyMarketEngine.canonicalFloraKey(floraType);
        double demand = s.demandKgByFlora.getOrDefault(flora, 0.0);
        return Math.round(Math.max(0.0, demand) * 100.0) / 100.0;
    }

    public double soldKgAtMarket(@Nullable ProvincialMarket market, @Nullable String floraType) {
        if (market == null) {
            return 0.0;
        }
        HoneyMarketSnapshot s = getSnapshot();
        if (s == null) {
            return 0.0;
        }
        String flora = HoneyMarketEngine.canonicalFloraKey(floraType);
        return Math.round(Math.max(0.0, soldKgAtRegion(s.dayKey, market.region, flora)) * 100.0) / 100.0;
    }

    public double remainingCapacityKg(@Nullable ProvincialMarket market, @Nullable String floraType) {
        if (market == null) {
            return 0.0;
        }
        HoneyMarketSnapshot s = getSnapshot();
        if (s == null) {
            return 0.0;
        }
        String flora = HoneyMarketEngine.canonicalFloraKey(floraType);
        double cap = dailyDemandKg(market, flora);
        double sold = soldKgAtMarket(market, flora);
        return Math.round(Math.max(0.0, cap - sold) * 100.0) / 100.0;
    }

    private double soldKgAtRegion(int dayKey, @NonNull PlayableMapRegion region,
            @NonNull String flora) {
        Map<String, Double> day = readSalesHistory().get(dayKey);
        if (day == null) {
            return 0.0;
        }
        return day.getOrDefault(TerritorialMarketRules.salesKey(region, flora), 0.0);
    }

    public double[] last7PostedPricesEurPerKg(@Nullable String floraType) {
        String k = HoneyMarketEngine.canonicalFloraKey(floraType);
        Map<String, double[]> all = last7PostedPricesByFlora();
        double[] series = all.get(k);
        return series != null ? series.clone() : new double[7];
    }

    public Map<String, double[]> last7PostedPricesByFlora() {
        HoneyMarketSnapshot s = getSnapshot();
        int todayKey = GameCalendar.currentGlobalMarketDayKey();
        int players = s != null ? s.playerCount : readCachedPlayerCount();
        PlayableMapRegion region = activeRegion();
        double activity = s != null ? s.activityUnits : activityFor(region);
        synchronized (last7Lock) {
            if (cachedLast7 != null
                    && cachedLast7DayKey == todayKey
                    && cachedLast7Players == players
                    && sameDouble(cachedLast7Activity, activity)
                    && cachedLast7Region == region) {
                return cachedLast7;
            }
        }
        Map<String, double[]> all = HoneyMarketEngine.last7PostedPricesByFlora(
                todayKey, region, activity, players, floraSalesForRegion(readSalesHistory(), region));
        if (s != null) {
            HoneyMarketSnapshot yest = previousDaySnap(s);
            LocalDate yestDate = GameCalendar.fromDayKey(s.dayKey).minusDays(1);
            int yestKey = GameCalendar.toDayKey(yestDate);
            for (String flora : all.keySet()) {
                double[] series = all.get(flora);
                if (series == null || series.length == 0) {
                    continue;
                }
                series[series.length - 1] = HoneyMarketEngine.postedDailyPriceEurPerKg(
                        flora, s, yest, soldKgOn(yestKey, flora));
            }
        }
        synchronized (last7Lock) {
            cachedLast7 = all;
            cachedLast7DayKey = todayKey;
            cachedLast7Players = players;
            cachedLast7Activity = activity;
            cachedLast7Region = region;
        }
        return all;
    }

    private void invalidateLast7Cache() {
        synchronized (last7Lock) {
            cachedLast7 = null;
            cachedLast7DayKey = Integer.MIN_VALUE;
            cachedLast7Players = Integer.MIN_VALUE;
            cachedLast7Activity = Double.NaN;
            cachedLast7Region = null;
        }
    }

    private static boolean sameDouble(double a, double b) {
        return Double.compare(a, b) == 0;
    }

    public double blendedPriceEurPerKg() {
        return blendedPriceEurPerKg(Collections.emptyMap());
    }

    public double blendedPriceEurPerKg(Map<String, Double> globalSoldByFloraIgnored) {
        HoneyMarketSnapshot s = getSnapshot();
        if (s == null) {
            return 12.0;
        }
        double num = 0.0;
        double den = 0.0;
        for (String flora : s.turnoverTargetKgByFlora.keySet()) {
            double target = s.turnoverTargetKgByFlora.getOrDefault(flora, 0.0);
            if (target <= 1e-6) {
                continue;
            }
            double price = postedPriceLocked(flora, s);
            num += target * price;
            den += target;
        }
        if (den <= 1e-6) {
            return 12.0;
        }
        return Math.round(100.0 * num / den) / 100.0;
    }

    public void attachGlobalSoldListener(int dayKey, Consumer<Map<String, Double>> onSoldMapChanged) {
        int marketDay = GameCalendar.currentGlobalMarketDayKey();
        clearGlobalSoldListener();
        int gen = ++salesListenerGeneration;
        attachedSalesDayKey = marketDay;
        onSoldMapChanged.accept(Collections.emptyMap());
        if (GameServer.enabled()) {
            new Thread(() -> {
                JSONArray rows = GameServer.fetchArray("/market-sales");
                Map<String, Double> map = new HashMap<>();
                if (rows != null) {
                    for (int i = 0; i < rows.length(); i++) {
                        JSONObject row = rows.optJSONObject(i);
                        if (row == null || row.optInt("dayKey", -1) != marketDay) {
                            continue;
                        }
                        String key = row.optString("floraKey", "");
                        if (!key.isEmpty()) {
                            map.put(key, row.optDouble("kgSold", 0));
                        }
                    }
                }
                mainHandler.post(() -> {
                    if (gen == salesListenerGeneration && attachedSalesDayKey == marketDay) {
                        onSoldMapChanged.accept(map.isEmpty() ? readDaySalesMap(marketDay) : map);
                    }
                });
            }, "market-sales").start();
            return;
        }
        if (firestore == null || authRepository.getCurrentUser() == null) {
            mainHandler.post(() -> {
                if (gen != salesListenerGeneration || attachedSalesDayKey != marketDay) {
                    return;
                }
                onSoldMapChanged.accept(readDaySalesMap(marketDay));
            });
            return;
        }
        CollectionReference col = floraSalesCollection(marketDay);
        globalSalesListener = col.addSnapshotListener((snap, error) -> {
            if (gen != salesListenerGeneration
                    || attachedSalesDayKey != marketDay
                    || GameCalendar.currentGlobalMarketDayKey() != marketDay) {
                return;
            }
            if (snap == null) {
                mainHandler.post(() -> {
                    if (gen == salesListenerGeneration && attachedSalesDayKey == marketDay) {
                        onSoldMapChanged.accept(Collections.emptyMap());
                    }
                });
                return;
            }
            Map<String, Double> m = new LinkedHashMap<>();
            for (DocumentSnapshot d : snap.getDocuments()) {
                if (!isSaleForMarketDay(d, marketDay)) {
                    continue;
                }
                m.put(d.getId(), readKgSold(d));
            }
            mainHandler.post(() -> {
                if (gen == salesListenerGeneration && attachedSalesDayKey == marketDay) {
                    onSoldMapChanged.accept(m);
                }
            });
        });
    }

    public void clearGlobalSoldListener() {
        if (globalSalesListener != null) {
            globalSalesListener.remove();
            globalSalesListener = null;
        }
        attachedSalesDayKey = Integer.MIN_VALUE;
    }

    /** Anota volumen vendido (la miel ya se reservó en el camión). */
    public void recordSaleVolume(@Nullable String floraType, double kg) {
        recordSaleVolume(floraType, kg, null);
    }

    public void recordSaleVolume(@Nullable String floraType, double kg,
            @Nullable ProvincialMarket dest) {
        if (kg <= 0.0) {
            return;
        }
        String flora = HoneyMarketEngine.canonicalFloraKey(floraType);
        int dayKey = GameCalendar.currentGlobalMarketDayKey();
        PlayableMapRegion region = dest != null ? dest.region : activeRegion();
        String key = TerritorialMarketRules.salesKey(region, flora);
        Map<String, Double> day = readDaySalesMap(dayKey);
        writeSoldTotal(dayKey, key, day.getOrDefault(key, 0.0) + kg);
        if (dest != null) {
            String localKey = TerritorialMarketRules.marketSalesKey(region, dest, flora);
            day = readDaySalesMap(dayKey);
            writeSoldTotal(dayKey, localKey, day.getOrDefault(localKey, 0.0) + kg);
        }
        notifyEventSale(flora, kg);
    }

    /**
     * Venta global: el precio es el publicado hoy; las kg se acumulan para el precio de mañana.
     */
    public void executeGlobalSale(
            @Nullable String floraType,
            double kg,
            @Nullable HoneyMarketSnapshot snap,
            EconomyRepository economy,
            MarketSaleExecutionCallback callback) {
        if (kg <= 0.0 || snap == null) {
            callback.onFailure("invalid");
            return;
        }
        String flora = HoneyMarketEngine.canonicalFloraKey(floraType);
        HoneyMarketSnapshot live = getSnapshot();
        if (live == null) {
            callback.onFailure("snapshot");
            return;
        }
        int dayKey = GameCalendar.currentGlobalMarketDayKey();
        double price = postedPriceLocked(flora, live);
        String saleKey = TerritorialMarketRules.salesKey(activeRegion(), flora);
        if (economy.getHoneyStockForFlora(flora) + 1e-9 < kg) {
            callback.onFailure("stock");
            return;
        }
        double regionalCapacity = live.demandKgByFlora.getOrDefault(flora, 0.0);
        double regionalSold = soldKgAtRegion(dayKey, activeRegion(), flora);
        if (kg > Math.max(0.0, regionalCapacity - regionalSold) + 1e-6) {
            callback.onFailure("demand");
            return;
        }

        if (firestore == null || authRepository.getCurrentUser() == null) {
            executeSaleLocalFallback(flora, kg, price, dayKey, economy, callback);
            return;
        }

        DocumentReference ref = floraSaleDoc(dayKey, saleKey);
        firestore.runTransaction(transaction -> {
            DocumentSnapshot doc = transaction.get(ref);
            double soldBefore = isSaleForMarketDay(doc, dayKey) ? readKgSold(doc) : 0.0;
            double soldAfter = soldBefore + kg;
            if (soldAfter > regionalCapacity + 1e-6) {
                throw new IllegalStateException("demand");
            }
            Map<String, Object> data = new HashMap<>();
            data.put(FIELD_KG_SOLD, soldAfter);
            data.put(FIELD_MARKET_DAY, dayKey);
            transaction.set(ref, data, SetOptions.merge());
            return soldAfter;
        }).addOnSuccessListener(soldAfter -> {
            if (!economy.sellHoneyOfFlora(flora, kg, price)) {
                compensatingFirestoreDecrement(dayKey, saleKey, kg);
                callback.onFailure("stock");
                return;
            }
            writeSoldTotal(dayKey, saleKey, soldAfter);
            notifyEventSale(flora, kg);
            callback.onSuccess(price);
        }).addOnFailureListener(e -> {
            String msg = e.getMessage() != null ? e.getMessage() : "mercado";
            callback.onFailure(msg);
        });
    }

    private void executeSaleLocalFallback(
            String flora,
            double kg,
            double price,
            int dayKey,
            EconomyRepository economy,
            MarketSaleExecutionCallback callback) {
        double soldBefore = soldKgOn(dayKey, flora);
        if (!economy.sellHoneyOfFlora(flora, kg, price)) {
            callback.onFailure("stock");
            return;
        }
        String saleKey = TerritorialMarketRules.salesKey(activeRegion(), flora);
        writeSoldTotal(dayKey, saleKey, soldBefore + kg);
        new Thread(() -> GameServer.addMarketSale(dayKey, saleKey, kg), "market-sale").start();
        notifyEventSale(flora, kg);
        callback.onSuccess(price);
    }

    private void compensatingFirestoreDecrement(int dayKey, String floraKey, double kg) {
        if (firestore == null || kg <= 0.0) {
            return;
        }
        DocumentReference ref = floraSaleDoc(dayKey, floraKey);
        firestore.runTransaction(transaction -> {
            DocumentSnapshot doc = transaction.get(ref);
            if (!doc.exists()) {
                return null;
            }
            double sold = isSaleForMarketDay(doc, dayKey) ? readKgSold(doc) : 0.0;
            double next = Math.max(0.0, sold - kg);
            Map<String, Object> data = new HashMap<>();
            data.put(FIELD_KG_SOLD, next);
            data.put(FIELD_MARKET_DAY, dayKey);
            transaction.set(ref, data, SetOptions.merge());
            return null;
        });
    }

    private CollectionReference floraSalesCollection(int dayKey) {
        return firestore.collection(COLLECTION_ROOT)
                .document(String.valueOf(dayKey))
                .collection(SUB_FLORA_SALES);
    }

    private DocumentReference floraSaleDoc(int dayKey, String floraCanonical) {
        return floraSalesCollection(dayKey).document(floraCanonical);
    }

    private static boolean isSaleForMarketDay(DocumentSnapshot d, int marketDay) {
        if (d == null || !d.exists()) {
            return false;
        }
        Object raw = d.get(FIELD_MARKET_DAY);
        if (!(raw instanceof Number)) {
            return false;
        }
        return ((Number) raw).intValue() == marketDay;
    }

    private static double readKgSold(DocumentSnapshot d) {
        if (d == null || !d.exists()) {
            return 0.0;
        }
        Object raw = d.get(FIELD_KG_SOLD);
        if (!(raw instanceof Number)) {
            return 0.0;
        }
        return ((Number) raw).doubleValue();
    }

    private void notifyEventSale(String flora, double kg) {
        if (!(appContext instanceof com.apiculture.simulator.ApicultureApp)) {
            return;
        }
        GlobalEventRepository repo =
                ((com.apiculture.simulator.ApicultureApp) appContext).getGlobalEventRepository();
        if (repo == null || authRepository.getCurrentUser() == null) {
            return;
        }
        repo.recordSaleTowardSurge(flora, kg, authRepository.getCurrentUser().getUid());
    }

    public interface MarketSaleExecutionCallback {
        void onSuccess(double unitPriceEurPerKg);

        void onFailure(String reasonCodeOrMessage);
    }
}
