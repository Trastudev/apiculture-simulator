package com.apiculture.simulator.data.repository;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;

import com.apiculture.simulator.domain.game.GameCalendar;
import com.apiculture.simulator.domain.game.GlobalEventEffects;
import com.apiculture.simulator.domain.market.HoneyMarketEngine;
import com.apiculture.simulator.domain.market.HoneyMarketSnapshot;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.AggregateQuerySnapshot;
import com.google.firebase.firestore.AggregateSource;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.SetOptions;

import org.json.JSONObject;

import java.time.LocalDate;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Mercado global: demanda diaria proporcional a jugadores reales + ventas acumuladas en Firestore
 * (o fallback local si no hay Firebase / sesión).
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
    private static final String FIELD_KG_SOLD = "kgSold";
    private static final String FIELD_MARKET_DAY = "marketDayKey";

    private final Context appContext;
    private final AuthRepository authRepository;
    private final FirebaseFirestore firestore;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private volatile HoneyMarketSnapshot cached;
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
        resetLocalFallbackSalesIfNewDay(marketDay);
        HoneyMarketSnapshot snap = HoneyMarketEngine.computeSnapshot(
                marketDay, marketDate.getDayOfYear(), readCachedPlayerCount());
        applySnapshot(GlobalEventEffects.applyToMarket(snap));
        notifyUpdated(onUpdated);
        io.execute(() -> {
            int players = fetchPlayerCountBlocking();
            HoneyMarketSnapshot remote = HoneyMarketEngine.computeSnapshot(
                    marketDay, marketDate.getDayOfYear(), players);
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
        persist(snap);
        if (prev == null || prev.dayKey != snap.dayKey) {
            resetLocalFallbackSalesIfNewDay(snap.dayKey);
        }
        Runnable r = snapshotChangedListener;
        if (r != null) {
            mainHandler.post(r);
        }
    }

    private void resetLocalFallbackSalesIfNewDay(int dayKey) {
        SharedPreferences p = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        int prev = p.getInt(KEY_LOCAL_FALLBACK_DAY, Integer.MIN_VALUE);
        if (prev != dayKey) {
            p.edit().putInt(KEY_LOCAL_FALLBACK_DAY, dayKey).putString(KEY_LOCAL_FALLBACK_JSON, "{}").commit();
        }
    }

    private void persist(HoneyMarketSnapshot s) {
        try {
            JSONObject o = new JSONObject();
            o.put(KEY_DAY, s.dayKey);
            o.put("totalDemand", s.totalDemandKg);
            o.put("demandSeason", s.demandSeasonFactor);
            o.put("noise", s.dailyNoiseMultiplier);
            o.put("priceTension", s.priceTension01);
            o.put("playerCount", s.playerCount);
            o.put("demandByFlora", new JSONObject(s.demandKgByFlora));
            o.put("priceByFlora", new JSONObject(s.priceEurPerKgByFlora));
            SharedPreferences p = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            p.edit()
                    .putInt(KEY_DAY, s.dayKey)
                    .putInt(KEY_PLAYER_COUNT, s.playerCount)
                    .putString(KEY_JSON, o.toString())
                    .apply();
        } catch (Exception ignored) {
        }
    }

    private int readCachedPlayerCount() {
        SharedPreferences p = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return Math.max(1, p.getInt(KEY_PLAYER_COUNT, 1));
    }

    private int fetchPlayerCountBlocking() {
        if (firestore == null || authRepository.getCurrentUser() == null) {
            return readCachedPlayerCount();
        }
        try {
            AggregateQuerySnapshot snap = Tasks.await(
                    firestore.collection(PLAYERS).count().get(AggregateSource.SERVER),
                    8, TimeUnit.SECONDS);
            int n = (int) Math.max(1L, snap.getCount());
            appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .putInt(KEY_PLAYER_COUNT, n)
                    .apply();
            return n;
        } catch (Exception ignored) {
            return readCachedPlayerCount();
        }
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
        if (cached != null && cached.dayKey == todayKey) {
            return;
        }
        resetLocalFallbackSalesIfNewDay(todayKey);
        applySnapshot(GlobalEventEffects.applyToMarket(HoneyMarketEngine.computeSnapshot(
                todayKey, marketDate.getDayOfYear(), readCachedPlayerCount())));
    }

    /** Precio mostrado con oferta global conocida ({@code globalSoldKg} en kg ya vendidos ese día). */
    public double priceEurPerKgForFloraWithSold(@Nullable String floraType, double globalSoldKg) {
        HoneyMarketSnapshot s = getSnapshot();
        if (s == null) {
            return 12.0;
        }
        String k = HoneyMarketEngine.canonicalFloraKey(floraType);
        double demand = s.demandKgByFlora.getOrDefault(k, 0.0);
        double base = s.priceForFloraOrDefault(k, 12.0);
        return HoneyMarketEngine.priceEurPerKgFromSupply(k, demand, globalSoldKg, base);
    }

    public double priceEurPerKgForFlora(@Nullable String floraType) {
        return priceEurPerKgForFloraWithSold(floraType, 0.0);
    }

    public double blendedPriceEurPerKg() {
        return blendedPriceEurPerKg(Collections.emptyMap());
    }

    public double blendedPriceEurPerKg(Map<String, Double> globalSoldByFlora) {
        HoneyMarketSnapshot s = getSnapshot();
        if (s == null) {
            return 12.0;
        }
        Map<String, Double> sold = globalSoldByFlora != null ? globalSoldByFlora : Collections.emptyMap();
        double num = 0.0;
        double den = 0.0;
        for (String flora : s.demandKgByFlora.keySet()) {
            double d = s.demandKgByFlora.getOrDefault(flora, 0.0);
            if (d <= 1e-6) {
                continue;
            }
            double gSold = sold.getOrDefault(flora, 0.0);
            double base = s.priceForFloraOrDefault(flora, 12.0);
            double price = HoneyMarketEngine.priceEurPerKgFromSupply(flora, d, gSold, base);
            num += d * price;
            den += d;
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
        if (firestore == null || authRepository.getCurrentUser() == null) {
            mainHandler.post(() -> {
                if (gen != salesListenerGeneration || attachedSalesDayKey != marketDay) {
                    return;
                }
                onSoldMapChanged.accept(readLocalFallbackSalesMap());
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

    /**
     * Venta global: transacción Firestore (kg vendidos mundiales) y precio según oferta previa.
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
        double demand = live.demandKgByFlora.getOrDefault(flora, 0.0);
        double seasonalBase = live.priceForFloraOrDefault(flora, 12.0);
        if (economy.getHoneyStockForFlora(flora) + 1e-9 < kg) {
            callback.onFailure("stock");
            return;
        }

        if (firestore == null || authRepository.getCurrentUser() == null) {
            executeSaleLocalFallback(flora, kg, demand, seasonalBase, dayKey, economy, callback);
            return;
        }

        DocumentReference ref = floraSaleDoc(dayKey, flora);
        firestore.runTransaction(transaction -> {
            DocumentSnapshot doc = transaction.get(ref);
            double soldBefore = isSaleForMarketDay(doc, dayKey) ? readKgSold(doc) : 0.0;
            double price = HoneyMarketEngine.priceEurPerKgFromSupply(flora, demand, soldBefore, seasonalBase);
            double soldAfter = soldBefore + kg;
            Map<String, Object> data = new HashMap<>();
            data.put(FIELD_KG_SOLD, soldAfter);
            data.put(FIELD_MARKET_DAY, dayKey);
            transaction.set(ref, data, SetOptions.merge());
            return price;
        }).addOnSuccessListener(price -> {
            if (!economy.sellHoneyOfFlora(flora, kg, price)) {
                compensatingFirestoreDecrement(dayKey, flora, kg);
                callback.onFailure("stock");
                return;
            }
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
            double demand,
            double seasonalBase,
            int dayKey,
            EconomyRepository economy,
            MarketSaleExecutionCallback callback) {
        double soldBefore = readLocalFallbackSalesMap().getOrDefault(flora, 0.0);
        double price = HoneyMarketEngine.priceEurPerKgFromSupply(flora, demand, soldBefore, seasonalBase);
        if (!economy.sellHoneyOfFlora(flora, kg, price)) {
            callback.onFailure("stock");
            return;
        }
        persistLocalFallbackSale(flora, soldBefore + kg);
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

    private Map<String, Double> readLocalFallbackSalesMap() {
        int day = GameCalendar.currentGlobalMarketDayKey();
        SharedPreferences p = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (p.getInt(KEY_LOCAL_FALLBACK_DAY, Integer.MIN_VALUE) != day) {
            return new LinkedHashMap<>();
        }
        Map<String, Double> m = new LinkedHashMap<>();
        try {
            String raw = p.getString(KEY_LOCAL_FALLBACK_JSON, "{}");
            if (raw == null || raw.isEmpty()) {
                return m;
            }
            JSONObject o = new JSONObject(raw);
            Iterator<String> it = o.keys();
            while (it.hasNext()) {
                String k = it.next();
                m.put(k, o.optDouble(k, 0.0));
            }
        } catch (Exception ignored) {
        }
        return m;
    }

    private void persistLocalFallbackSale(String floraCanonical, double newTotalSold) {
        SharedPreferences p = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        int day = GameCalendar.currentGlobalMarketDayKey();
        if (p.getInt(KEY_LOCAL_FALLBACK_DAY, Integer.MIN_VALUE) != day) {
            p.edit().putInt(KEY_LOCAL_FALLBACK_DAY, day).putString(KEY_LOCAL_FALLBACK_JSON, "{}").commit();
        }
        Map<String, Double> m = readLocalFallbackSalesMap();
        m.put(floraCanonical, newTotalSold);
        try {
            JSONObject o = new JSONObject();
            for (Map.Entry<String, Double> e : m.entrySet()) {
                if (e.getValue() != null && e.getValue() > 1e-9) {
                    o.put(e.getKey(), e.getValue());
                }
            }
            p.edit().putString(KEY_LOCAL_FALLBACK_JSON, o.toString()).apply();
        } catch (Exception ignored) {
        }
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
