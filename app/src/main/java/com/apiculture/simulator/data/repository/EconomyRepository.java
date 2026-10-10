package com.apiculture.simulator.data.repository;

import android.content.Context;
import android.content.SharedPreferences;

import com.apiculture.simulator.domain.market.HoneyMarketEngine;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import org.json.JSONObject;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class EconomyRepository {

    /** Saldo inicial al registrar o reiniciar (sin terrenos ni colmenas). */
    public static final double DEFAULT_STARTING_BALANCE_EUR = 45_000.0;

    private static final String PREFS = "economy_prefs";
    private static final String KEY_BALANCE = "balance";
    /** Legado: un único almacén; migrado a {@link #KEY_HONEY_BUCKETS_JSON}. */
    private static final String KEY_HONEY_STOCK = "honey_stock";
    private static final String KEY_HONEY_BUCKETS_JSON = "honey_buckets_json";
    private static final String KEY_HONEY_SOLD_TOTAL = "honey_sold_total_kg";
    private static final String KEY_HONEY_SOLD_BY_FLORA_JSON = "honey_sold_by_flora_json";
    private static final String KEY_HONEY_STOCK_SEQ = "honey_stock_seq";
    private final SharedPreferences prefs;

    @Nullable
    private Runnable economyChangedCallback;
    private final MutableLiveData<Integer> revision = new MutableLiveData<>(0);

    public EconomyRepository(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public void setEconomyChangedCallback(@Nullable Runnable callback) {
        economyChangedCallback = callback;
    }

    /** Cambia cuando el saldo o la miel cambian, también al aplicar la copia del servidor. */
    public LiveData<Integer> revision() {
        return revision;
    }

    private void notifyEconomyChanged() {
        Integer current = revision.getValue();
        revision.postValue((current == null ? 0 : current) + 1);
        if (economyChangedCallback != null) {
            economyChangedCallback.run();
        }
    }

    public static final String OFFLINE_ACTION =
            "No hay conexión con el servidor. No se ha podido realizar la acción.";

    @Nullable
    private String blocked;

    /** Motivo del último {@link #trySpend} fallido: sin conexión o saldo insuficiente. */
    @NonNull
    public String blockedReason(double amount) {
        if (OFFLINE_ACTION.equals(blocked)) {
            blocked = null;
            return OFFLINE_ACTION;
        }
        blocked = null;
        return "Saldo insuficiente (" + (int) amount + " B).";
    }

    private boolean confirmField(@NonNull String key, @NonNull Object value) {
        if (!GameServer.enabled()) {
            return true;
        }
        if (android.os.Looper.getMainLooper().isCurrentThread()) {
            blocked = OFFLINE_ACTION;
            return false;
        }
        com.apiculture.simulator.data.session.SignedInUser user =
                com.apiculture.simulator.data.session.PlayerAuth.getInstance().getCurrentUser();
        if (user == null || user.getUid() == null || user.getUid().isEmpty()) {
            blocked = OFFLINE_ACTION;
            return false;
        }
        try {
            org.json.JSONObject body = new org.json.JSONObject();
            body.put(key, value);
            if ("economyHoneyBucketsJson".equals(key)) {
                body.put("honeyStockSeq", honeyStockSeq());
            }
            if (!GameServer.savePlayer(user.getUid(), body)) {
                blocked = OFFLINE_ACTION;
                return false;
            }
            blocked = null;
            return true;
        } catch (Exception ignored) {
            blocked = OFFLINE_ACTION;
            return false;
        }
    }
    public boolean hasPersistedBalance() {
        return prefs.contains(KEY_BALANCE);
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
        return trySpend(amount, null);
    }

    public synchronized boolean trySpend(double amount, @Nullable String concept) {
        if (amount <= 0) {
            return false;
        }
        double b = getBalance();
        if (b < amount) {
            blocked = null;
            return false;
        }
        if (!confirmField("economyBalanceEur", b - amount)) {
            return false;
        }
        setBalance(b - amount);
        recordMovement(-amount, concept, false);
        return true;
    }

    /** Reintegra saldo (p. ej. si falló una operación tras cobrar). */
    public synchronized void addToBalance(double amount) {
        addToBalance(amount, null);
    }

    public synchronized void addToBalance(double amount, @Nullable String concept) {
        if (amount <= 0) {
            return;
        }
        double next = getBalance() + amount;
        if (!confirmField("economyBalanceEur", next)) {
            return;
        }
        setBalance(next);
        recordMovement(amount, concept, true);
    }

    @NonNull
    public static String saleConcept(@NonNull String via, @Nullable String flora, double kg) {
        return "Venta de " + formatKg(kg) + " kg de miel de "
                + HoneyMarketEngine.canonicalFloraKey(flora) + " " + via;
    }

    @NonNull
    public static String formatKg(double kg) {
        double rounded = Math.round(kg * 100.0) / 100.0;
        return String.format(Locale.getDefault(), "%.2f", rounded);
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
        addHoneyCapped(floraType, kg, Double.MAX_VALUE);
    }

    /** Suma miel hasta un tope de almacén. Devuelve lo que cupo. */
    public synchronized double addHoneyCapped(String floraType, double kg, double capacityKg) {
        if (kg <= 0.0) {
            return 0.0;
        }
        double room = Math.max(0.0, capacityKg - getHoneyStock());
        double take = Math.min(kg, room);
        if (take <= 1e-9) {
            return 0.0;
        }
        String key = HoneyMarketEngine.canonicalFloraKey(floraType);
        Map<String, Double> m = readBuckets();
        m.put(key, m.getOrDefault(key, 0.0) + take);
        if (!writeBuckets(m)) {
            return 0.0;
        }
        return take;
    }

    /**
     * Legado: sin tipo, se trata como miel multifloral.
     */
    public synchronized void addHoney(double kg) {
        addHoney("Mil flores", kg);
    }

    /** Quita miel del almacén sin venderla (carga en camión). */
    public synchronized boolean takeHoney(String floraType, double kg) {
        if (kg <= 0.0) {
            return false;
        }
        String key = HoneyMarketEngine.canonicalFloraKey(floraType);
        Map<String, Double> m = readBuckets();
        double stock = m.getOrDefault(key, 0.0);
        if (stock + 1e-9 < kg) {
            // El diálogo redondea a céntimos de kilo (8,595 se ve como 8,60).
            if (kg - stock > 0.011) {
                return false;
            }
            kg = stock;
        }
        m.put(key, stock - kg);
        if (!writeBuckets(m)) {
            return false;
        }
        return true;
    }

    /** Cobra una venta cuya miel ya salió del almacén. */
    public synchronized void creditSaleProceeds(String floraType, double kg, double unitPrice) {
        creditSaleProceeds(floraType, kg, unitPrice, null);
    }

    public synchronized void creditSaleProceeds(String floraType, double kg, double unitPrice,
            @Nullable String concept) {
        if (kg <= 0.0) {
            return;
        }
        String key = HoneyMarketEngine.canonicalFloraKey(floraType);
        recordHoneySold(key, kg);
        double proceeds = kg * unitPrice;
        double next = getBalance() + proceeds;
        if (!confirmField("economyBalanceEur", next)) {
            return;
        }
        setBalance(next);
        recordMovement(proceeds, concept != null ? concept : saleConcept("en el mercado", key, kg), true);
    }

    public synchronized boolean sellHoneyOfFlora(String floraType, double kg, double unitPrice) {
        return sellHoneyOfFlora(floraType, kg, unitPrice, null);
    }

    public synchronized boolean sellHoneyOfFlora(String floraType, double kg, double unitPrice,
            @Nullable String concept) {
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
        if (!writeBuckets(m)) {
            return false;
        }
        recordHoneySold(key, kg);
        double proceeds = kg * unitPrice;
        double next = getBalance() + proceeds;
        if (!confirmField("economyBalanceEur", next)) {
            return false;
        }
        setBalance(next);
        recordMovement(proceeds, concept != null ? concept : saleConcept("en el mercado", key, kg), true);
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
        if (!writeBuckets(m)) {
            return false;
        }
        for (Map.Entry<String, Double> e : soldParts.entrySet()) {
            recordHoneySold(e.getKey(), e.getValue());
            recordMovement(e.getValue() * unitPrice,
                    saleConcept("en el mercado", e.getKey(), e.getValue()), true);
        }
        double next = getBalance() + kg * unitPrice;
        if (!confirmField("economyBalanceEur", next)) {
            return false;
        }
        setBalance(next);
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
        EconomyLedger.clear(prefs);
        notifyEconomyChanged();
    }

    @NonNull
    public List<EconomyLedger.Entry> movements() {
        return EconomyLedger.entries(prefs);
    }

    private void recordMovement(double signed, @Nullable String concept, boolean income) {
        String text = concept == null ? "" : concept.trim();
        if (text.isEmpty()) {
            text = income ? "Ingreso" : "Gasto";
        }
        EconomyLedger.add(prefs, signed, text);
    }

    /**
     * Aplica valores leídos de Firestore sin disparar el callback de push (evita bucles).
     */
    public long honeyStockSeq() {
        return prefs.getLong(KEY_HONEY_STOCK_SEQ, 0L);
    }

    public synchronized void setHoneyStockSeq(long seq) {
        if (seq < 0L) {
            return;
        }
        prefs.edit().putLong(KEY_HONEY_STOCK_SEQ, seq).apply();
    }

    static long stockSeq(@NonNull Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getLong(KEY_HONEY_STOCK_SEQ, 0L);
    }

    public synchronized void applyFromCloud(double balanceEur, @Nullable String honeyBucketsJson) {
        applyFromCloud(balanceEur, honeyBucketsJson, null, null);
    }

    public synchronized void applyFromCloud(double balanceEur, @Nullable String honeyBucketsJson,
            @Nullable Double honeySoldTotalKg, @Nullable String honeySoldByFloraJson) {
        prefs.edit().putLong(KEY_BALANCE, Double.doubleToRawLongBits(balanceEur)).apply();
        if (honeyBucketsJson != null && !honeyBucketsJson.trim().isEmpty()) {
            prefs.edit().putString(KEY_HONEY_BUCKETS_JSON, honeyBucketsJson).apply();
        }
        notifyEconomyChanged();
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

    private boolean writeBuckets(Map<String, Double> m) {
        return writeBucketsJson(KEY_HONEY_BUCKETS_JSON, m, true, "economyHoneyBucketsJson");
    }

    private void writeSoldBuckets(Map<String, Double> m) {
        writeBucketsJson(KEY_HONEY_SOLD_BY_FLORA_JSON, m, false, "economyHoneySoldByFloraJson");
    }

    private boolean writeBucketsJson(String prefKey, Map<String, Double> m, boolean notify,
            @NonNull String serverKey) {
        try {
            JSONObject o = new JSONObject();
            for (Map.Entry<String, Double> e : m.entrySet()) {
                if (e.getValue() != null && e.getValue() > 1e-9) {
                    o.put(e.getKey(), e.getValue());
                }
            }
            if (!confirmField(serverKey, o.toString())) {
                return false;
            }
            prefs.edit().putString(prefKey, o.toString()).apply();
            if (notify) {
                notifyEconomyChanged();
            }
            return true;
        } catch (Exception ignored) {
            blocked = OFFLINE_ACTION;
            return false;
        }
    }
}
