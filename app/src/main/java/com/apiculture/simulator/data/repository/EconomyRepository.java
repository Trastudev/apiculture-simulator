package com.apiculture.simulator.data.repository;

import android.content.Context;
import android.content.SharedPreferences;

import com.apiculture.simulator.domain.market.HoneyMarketEngine;

import androidx.annotation.Nullable;

import org.json.JSONObject;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

public class EconomyRepository {

    /** Saldo inicial almacenado (compra de terreno 1000 €; caben dos con 2000 €). */
    public static final double DEFAULT_STARTING_BALANCE_EUR = 2000.0;

    private static final String PREFS = "economy_prefs";
    private static final String KEY_BALANCE = "balance";
    /** Legado: un único almacén; migrado a {@link #KEY_HONEY_BUCKETS_JSON}. */
    private static final String KEY_HONEY_STOCK = "honey_stock";
    private static final String KEY_HONEY_BUCKETS_JSON = "honey_buckets_json";
    private static final String KEY_HONEY_SOLD_TOTAL = "honey_sold_total_kg";
    private static final String KEY_HONEY_SOLD_BY_FLORA_JSON = "honey_sold_by_flora_json";
    private final SharedPreferences prefs;

    @Nullable
    private Runnable economyChangedCallback;

    public EconomyRepository(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public void setEconomyChangedCallback(@Nullable Runnable callback) {
        economyChangedCallback = callback;
    }

    private void notifyEconomyChanged() {
        if (economyChangedCallback != null) {
            economyChangedCallback.run();
        }
    }

    public double getBalance() {
        return Double.longBitsToDouble(prefs.getLong(KEY_BALANCE,
                Double.doubleToRawLongBits(DEFAULT_STARTING_BALANCE_EUR)));
    }

    public void setBalance(double balance) {
        prefs.edit().putLong(KEY_BALANCE, Double.doubleToRawLongBits(balance)).apply();
        notifyEconomyChanged();
    }

    /** Descuento atómico respecto a otras operaciones de saldo en este repositorio. */
    public synchronized boolean trySpend(double amount) {
        if (amount <= 0) {
            return false;
        }
        double b = getBalance();
        if (b < amount) {
            return false;
        }
        setBalance(b - amount);
        return true;
    }

    /** Reintegra saldo (p. ej. si falló una operación tras cobrar). */
    public synchronized void addToBalance(double amount) {
        if (amount <= 0) {
            return;
        }
        setBalance(getBalance() + amount);
    }

    /**
     * Suma de todos los tipos de miel en almacén (apiario).
     */
    public double getHoneyStock() {
        double sum = 0.0;
        for (double v : readBuckets().values()) {
            sum += v;
        }
        return sum;
    }

    public double getHoneyStockForFlora(String floraType) {
        String key = HoneyMarketEngine.canonicalFloraKey(floraType);
        return readBuckets().getOrDefault(key, 0.0);
    }

    /** Copia de cubos de miel en almacén (flora → kg). */
    public Map<String, Double> copyHoneyBuckets() {
        return new LinkedHashMap<>(readBuckets());
    }

    public synchronized void addHoney(String floraType, double kg) {
        if (kg <= 0.0) {
            return;
        }
        String key = HoneyMarketEngine.canonicalFloraKey(floraType);
        Map<String, Double> m = readBuckets();
        m.put(key, m.getOrDefault(key, 0.0) + kg);
        writeBuckets(m);
    }

    /**
     * Legado: sin tipo, se trata como miel multifloral.
     */
    public synchronized void addHoney(double kg) {
        addHoney("Mil flores", kg);
    }

    public synchronized boolean sellHoneyOfFlora(String floraType, double kg, double unitPrice) {
        if (kg <= 0.0) {
            return false;
        }
        String key = HoneyMarketEngine.canonicalFloraKey(floraType);
        Map<String, Double> m = readBuckets();
        double stock = m.getOrDefault(key, 0.0);
        if (stock < kg) {
            return false;
        }
        m.put(key, stock - kg);
        writeBuckets(m);
        recordHoneySold(key, kg);
        setBalance(getBalance() + kg * unitPrice);
        return true;
    }

    /** Venta sin desglose por tipo (precio único); suma comprobada contra stock total. */
    public synchronized boolean sellHoney(double kg, double unitPrice) {
        if (kg <= 0.0 || getHoneyStock() < kg) {
            return false;
        }
        Map<String, Double> m = readBuckets();
        double remaining = kg;
        Map<String, Double> soldParts = new LinkedHashMap<>();
        for (String key : new LinkedHashMap<>(m).keySet()) {
            if (remaining <= 0.0) {
                break;
            }
            double have = m.getOrDefault(key, 0.0);
            if (have <= 0.0) {
                continue;
            }
            double take = Math.min(have, remaining);
            m.put(key, have - take);
            soldParts.put(key, take);
            remaining -= take;
        }
        if (remaining > 1e-6) {
            return false;
        }
        writeBuckets(m);
        for (Map.Entry<String, Double> e : soldParts.entrySet()) {
            recordHoneySold(e.getKey(), e.getValue());
        }
        setBalance(getBalance() + kg * unitPrice);
        return true;
    }

    /** Kg de miel vendidos a lo largo de la partida (ranking). */
    public double getHoneySoldTotalKg() {
        return Double.longBitsToDouble(prefs.getLong(KEY_HONEY_SOLD_TOTAL, Double.doubleToRawLongBits(0.0)));
    }

    public Map<String, Double> copyHoneySoldByFlora() {
        return new LinkedHashMap<>(readSoldBuckets());
    }

    public synchronized String snapshotHoneySoldByFloraJsonForCloud() {
        Map<String, Double> m = readSoldBuckets();
        try {
            JSONObject o = new JSONObject();
            for (Map.Entry<String, Double> e : m.entrySet()) {
                if (e.getValue() != null && e.getValue() > 1e-9) {
                    o.put(e.getKey(), e.getValue());
                }
            }
            return o.toString();
        } catch (Exception ignored) {
            return "{}";
        }
    }

    private void recordHoneySold(String floraKey, double kg) {
        if (kg <= 1e-9) {
            return;
        }
        String key = HoneyMarketEngine.canonicalFloraKey(floraKey);
        Map<String, Double> sold = readSoldBuckets();
        sold.put(key, sold.getOrDefault(key, 0.0) + kg);
        writeSoldBuckets(sold);
        double total = getHoneySoldTotalKg() + kg;
        prefs.edit().putLong(KEY_HONEY_SOLD_TOTAL, Double.doubleToRawLongBits(total)).apply();
        notifyEconomyChanged();
    }

    /**
     * Saldo inicial y stock de miel al reiniciar juego (no borra prefs de otros datos).
     */
    public synchronized void applyNewGameEconomyDefaults() {
        prefs.edit()
                .putLong(KEY_BALANCE, Double.doubleToRawLongBits(DEFAULT_STARTING_BALANCE_EUR))
                .remove(KEY_HONEY_STOCK)
                .remove(KEY_HONEY_BUCKETS_JSON)
                .remove(KEY_HONEY_SOLD_TOTAL)
                .remove(KEY_HONEY_SOLD_BY_FLORA_JSON)
                .commit();
        notifyEconomyChanged();
    }

    /**
     * Aplica valores leídos de Firestore sin disparar el callback de push (evita bucles).
     */
    public synchronized void applyFromCloud(double balanceEur, @Nullable String honeyBucketsJson) {
        applyFromCloud(balanceEur, honeyBucketsJson, null, null);
    }

    public synchronized void applyFromCloud(double balanceEur, @Nullable String honeyBucketsJson,
            @Nullable Double honeySoldTotalKg, @Nullable String honeySoldByFloraJson) {
        prefs.edit().putLong(KEY_BALANCE, Double.doubleToRawLongBits(balanceEur)).apply();
        if (honeyBucketsJson != null && !honeyBucketsJson.trim().isEmpty()) {
            prefs.edit().putString(KEY_HONEY_BUCKETS_JSON, honeyBucketsJson).apply();
        }
        if (honeySoldTotalKg != null && honeySoldTotalKg > getHoneySoldTotalKg()) {
            prefs.edit().putLong(KEY_HONEY_SOLD_TOTAL,
                    Double.doubleToRawLongBits(honeySoldTotalKg)).apply();
        }
        if (honeySoldByFloraJson != null && !honeySoldByFloraJson.trim().isEmpty()) {
            Map<String, Double> cloud = parseBucketsJson(honeySoldByFloraJson);
            Map<String, Double> local = readSoldBuckets();
            for (Map.Entry<String, Double> e : cloud.entrySet()) {
                double lv = local.getOrDefault(e.getKey(), 0.0);
                if (e.getValue() != null && e.getValue() > lv) {
                    local.put(e.getKey(), e.getValue());
                }
            }
            writeSoldBuckets(local);
        }
    }

    /** JSON de cubos de miel para subir a la nube (migra legado si hace falta). */
    public synchronized String snapshotHoneyBucketsJsonForCloud() {
        migrateLegacyHoneyIfNeeded();
        String json = prefs.getString(KEY_HONEY_BUCKETS_JSON, null);
        return json != null && !json.isEmpty() ? json : "{}";
    }

    private void migrateLegacyHoneyIfNeeded() {
        if (prefs.contains(KEY_HONEY_BUCKETS_JSON)) {
            return;
        }
        double leg = Double.longBitsToDouble(prefs.getLong(KEY_HONEY_STOCK, Double.doubleToRawLongBits(0.0)));
        if (leg <= 1e-9) {
            return;
        }
        try {
            JSONObject o = new JSONObject();
            o.put(HoneyMarketEngine.canonicalFloraKey("Mil flores"), leg);
            prefs.edit()
                    .putString(KEY_HONEY_BUCKETS_JSON, o.toString())
                    .putLong(KEY_HONEY_STOCK, Double.doubleToRawLongBits(0.0))
                    .apply();
        } catch (Exception ignored) {
        }
    }

    private Map<String, Double> readBuckets() {
        migrateLegacyHoneyIfNeeded();
        return parseBucketsJson(prefs.getString(KEY_HONEY_BUCKETS_JSON, null));
    }

    private Map<String, Double> readSoldBuckets() {
        return parseBucketsJson(prefs.getString(KEY_HONEY_SOLD_BY_FLORA_JSON, null));
    }

    private static Map<String, Double> parseBucketsJson(@Nullable String json) {
        Map<String, Double> m = new LinkedHashMap<>();
        if (json == null || json.isEmpty()) {
            return m;
        }
        try {
            JSONObject o = new JSONObject(json);
            Iterator<String> it = o.keys();
            while (it.hasNext()) {
                String k = it.next();
                m.put(k, o.optDouble(k, 0.0));
            }
        } catch (Exception ignored) {
        }
        return m;
    }

    private void writeBuckets(Map<String, Double> m) {
        writeBucketsJson(KEY_HONEY_BUCKETS_JSON, m, true);
    }

    private void writeSoldBuckets(Map<String, Double> m) {
        writeBucketsJson(KEY_HONEY_SOLD_BY_FLORA_JSON, m, false);
    }

    private void writeBucketsJson(String prefKey, Map<String, Double> m, boolean notify) {
        try {
            JSONObject o = new JSONObject();
            for (Map.Entry<String, Double> e : m.entrySet()) {
                if (e.getValue() != null && e.getValue() > 1e-9) {
                    o.put(e.getKey(), e.getValue());
                }
            }
            prefs.edit().putString(prefKey, o.toString()).apply();
            if (notify) {
                notifyEconomyChanged();
            }
        } catch (Exception ignored) {
        }
    }
}
