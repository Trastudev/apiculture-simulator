package com.apiculture.simulator.data.repository;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;

import com.apiculture.simulator.domain.game.GameCalendar;
import com.apiculture.simulator.domain.market.HoneyMarketEngine;
import com.apiculture.simulator.domain.market.HoneyMarketSnapshot;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.SetOptions;

import org.json.JSONObject;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Mercado global: demanda diaria local (snapshot) + ventas acumuladas de todos los jugadores en Firestore
 * (o fallback local si no hay Firebase / sesión).
 */
public class MarketRepository {

    private static final String COLLECTION_ROOT = "globalHoneyMarket";
    private static final String SUB_FLORA_SALES = "floraSales";

    private static final String PREFS = "honey_market_prefs";
    private static final String KEY_DAY = "dayKey";
    private static final String KEY_JSON = "snapshotJson";
    private static final String KEY_LOCAL_FALLBACK_DAY = "local_fallback_sales_day";
    private static final String KEY_LOCAL_FALLBACK_JSON = "local_fallback_sales_json";
    private static final String FIELD_KG_SOLD = "kgSold";

    private final Context appContext;
    private final AuthRepository authRepository;
    private final FirebaseFirestore firestore;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private volatile HoneyMarketSnapshot cached;
    private ListenerRegistration globalSalesListener;

    public MarketRepository(Context context, @Nullable FirebaseFirestore firestore, AuthRepository authRepository) {
        this.appContext = context.getApplicationContext();
        this.firestore = firestore;
        this.authRepository = authRepository;
    }

    public void refreshGlobalMarketForDay(int dayKey, int civilDayOfYear) {
        resetLocalFallbackSalesIfNewDay(dayKey);
        HoneyMarketSnapshot snap = HoneyMarketEngine.computeSnapshot(dayKey, civilDayOfYear);
        cached = snap;
        persist(snap);
    }

    private void resetLocalFallbackSalesIfNewDay(int dayKey) {
        SharedPreferences p = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        int prev = p.getInt(KEY_LOCAL_FALLBACK_DAY, Integer.MIN_VALUE);
        if (prev != dayKey) {
            p.edit().putInt(KEY_LOCAL_FALLBACK_DAY, dayKey).putString(KEY_LOCAL_FALLBACK_JSON, "{}").apply();
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
            o.put("demandByFlora", new JSONObject(s.demandKgByFlora));
            o.put("priceByFlora", new JSONObject(s.priceEurPerKgByFlora));
            SharedPreferences p = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            p.edit().putInt(KEY_DAY, s.dayKey).putString(KEY_JSON, o.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    @Nullable
    public HoneyMarketSnapshot getSnapshot() {
        if (cached != null) {
            return cached;
        }
        ZoneId z = GameCalendar.userTimeZone();
        LocalDate today = LocalDate.now(z);
        refreshGlobalMarketForDay(GameCalendar.toDayKey(today), today.getDayOfYear());
        return cached;
    }

    /** Precio mostrado con cobertura global conocida ({@code globalSoldKg} en kg ya vendidos ese día). */
    public double priceEurPerKgForFloraWithSold(@Nullable String floraType, double globalSoldKg) {
        HoneyMarketSnapshot s = getSnapshot();
        if (s == null) {
            return 12.0;
        }
        String k = HoneyMarketEngine.canonicalFloraKey(floraType);
        double demand = s.demandKgByFlora.getOrDefault(k, 0.0);
        return HoneyMarketEngine.priceEurPerKgFromGlobalCoverage(k, demand, globalSoldKg);
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
            double price = HoneyMarketEngine.priceEurPerKgFromGlobalCoverage(flora, d, gSold);
            num += d * price;
            den += d;
        }
        if (den <= 1e-6) {
            return 12.0;
        }
        return Math.round(100.0 * num / den) / 100.0;
    }

    public void attachGlobalSoldListener(int dayKey, Consumer<Map<String, Double>> onSoldMapChanged) {
        clearGlobalSoldListener();
        if (firestore == null || authRepository.getCurrentUser() == null) {
            mainHandler.post(() -> onSoldMapChanged.accept(readLocalFallbackSalesMap()));
            return;
        }
        CollectionReference col = floraSalesCollection(dayKey);
        globalSalesListener = col.addSnapshotListener((snap, error) -> {
            if (snap == null) {
                mainHandler.post(() -> onSoldMapChanged.accept(Collections.emptyMap()));
                return;
            }
            Map<String, Double> m = new LinkedHashMap<>();
            for (DocumentSnapshot d : snap.getDocuments()) {
                double kg = d.contains(FIELD_KG_SOLD) ? d.getDouble(FIELD_KG_SOLD) : 0.0;
                m.put(d.getId(), kg);
            }
            mainHandler.post(() -> onSoldMapChanged.accept(m));
        });
    }

    public void clearGlobalSoldListener() {
        if (globalSalesListener != null) {
            globalSalesListener.remove();
            globalSalesListener = null;
        }
    }

    /**
     * Venta global: transacción Firestore (kg vendidos mundiales) y precio según cobertura previa.
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
        double demand = snap.demandKgByFlora.getOrDefault(flora, 0.0);
        if (economy.getHoneyStockForFlora(flora) + 1e-9 < kg) {
            callback.onFailure("stock");
            return;
        }

        if (firestore == null || authRepository.getCurrentUser() == null) {
            executeSaleLocalFallback(flora, kg, demand, snap.dayKey, economy, callback);
            return;
        }

        DocumentReference ref = floraSaleDoc(snap.dayKey, flora);
        firestore.runTransaction(transaction -> {
            DocumentSnapshot doc = transaction.get(ref);
            double soldBefore = (doc.exists() && doc.contains(FIELD_KG_SOLD)) ? doc.getDouble(FIELD_KG_SOLD) : 0.0;
            double price = HoneyMarketEngine.priceEurPerKgFromGlobalCoverage(flora, demand, soldBefore);
            double soldAfter = soldBefore + kg;
            Map<String, Object> data = new HashMap<>();
            data.put(FIELD_KG_SOLD, soldAfter);
            transaction.set(ref, data, SetOptions.merge());
            return price;
        }).addOnSuccessListener(price -> {
            if (!economy.sellHoneyOfFlora(flora, kg, price)) {
                compensatingFirestoreDecrement(snap.dayKey, flora, kg);
                callback.onFailure("stock");
                return;
            }
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
            int dayKey,
            EconomyRepository economy,
            MarketSaleExecutionCallback callback) {
        double soldBefore = readLocalFallbackSalesMap().getOrDefault(flora, 0.0);
        double price = HoneyMarketEngine.priceEurPerKgFromGlobalCoverage(flora, demand, soldBefore);
        if (!economy.sellHoneyOfFlora(flora, kg, price)) {
            callback.onFailure("stock");
            return;
        }
        persistLocalFallbackSale(flora, soldBefore + kg);
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
            double sold = doc.contains(FIELD_KG_SOLD) ? doc.getDouble(FIELD_KG_SOLD) : 0.0;
            double next = Math.max(0.0, sold - kg);
            transaction.set(ref, Collections.singletonMap(FIELD_KG_SOLD, next), SetOptions.merge());
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

    private Map<String, Double> readLocalFallbackSalesMap() {
        HoneyMarketSnapshot s = getSnapshot();
        int day = s != null ? s.dayKey : Integer.MIN_VALUE;
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
        HoneyMarketSnapshot s = getSnapshot();
        int day = s != null ? s.dayKey : Integer.MIN_VALUE;
        if (p.getInt(KEY_LOCAL_FALLBACK_DAY, Integer.MIN_VALUE) != day) {
            p.edit().putInt(KEY_LOCAL_FALLBACK_DAY, day).putString(KEY_LOCAL_FALLBACK_JSON, "{}").apply();
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

    public interface MarketSaleExecutionCallback {
        void onSuccess(double unitPriceEurPerKg);

        void onFailure(String reasonCodeOrMessage);
    }
}
