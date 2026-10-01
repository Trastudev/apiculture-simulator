package com.apiculture.simulator.domain.game;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.apiculture.simulator.data.local.entity.HiveEntity;
import com.apiculture.simulator.domain.market.HoneyMarketEngine;
import com.apiculture.simulator.domain.parcel.HexFlora;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reservas de miel por tipo de flora dentro de una colmena.
 * {@link HiveEntity#honeyProduction} se mantiene como total.
 */
public final class HiveHoneyStocks {

    private HiveHoneyStocks() {
    }

    public static LinkedHashMap<String, Double> parse(String json) {
        LinkedHashMap<String, Double> out = new LinkedHashMap<>();
        if (json == null || json.trim().isEmpty()) {
            return out;
        }
        try {
            JSONObject o = new JSONObject(json);
            Iterator<String> it = o.keys();
            while (it.hasNext()) {
                String raw = it.next();
                double kg = o.optDouble(raw, 0.0);
                if (kg <= 1e-9) {
                    continue;
                }
                String k = HoneyMarketEngine.canonicalFloraKey(raw);
                Double prev = out.get(k);
                out.put(k, (prev == null ? 0.0 : prev) + kg);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    public static String serialize(Map<String, Double> stocks) {
        JSONObject o = new JSONObject();
        if (stocks != null) {
            for (Map.Entry<String, Double> e : stocks.entrySet()) {
                if (e.getKey() == null || e.getValue() == null || e.getValue() <= 1e-9) {
                    continue;
                }
                try {
                    o.put(HoneyMarketEngine.canonicalFloraKey(e.getKey()), round3(e.getValue()));
                } catch (Exception ignored) {
                }
            }
        }
        return o.toString();
    }

    @Nullable
    public static String dominantFlora(@Nullable HiveEntity hive) {
        LinkedHashMap<String, Double> stocks = parse(hive == null ? null : hive.honeyStocksJson);
        String best = null;
        double max = 0.0;
        for (Map.Entry<String, Double> e : stocks.entrySet()) {
            if (e.getKey() == null || e.getValue() == null || e.getValue() <= max) {
                continue;
            }
            max = e.getValue();
            best = e.getKey();
        }
        if (best != null) {
            return best;
        }
        if (hive != null && hive.floraType != null && !hive.floraType.trim().isEmpty()) {
            return HoneyMarketEngine.canonicalFloraKey(hive.floraType);
        }
        return null;
    }

    public static double total(Map<String, Double> stocks) {
        if (stocks == null || stocks.isEmpty()) {
            return 0.0;
        }
        double t = 0.0;
        for (Double v : stocks.values()) {
            if (v != null) {
                t += v;
            }
        }
        return t;
    }

    /** Si no hay JSON, coloca el stock legado en el tipo de pecoreo actual. */
    public static LinkedHashMap<String, Double> ensureSeeded(HiveEntity hive) {
        LinkedHashMap<String, Double> stocks = parse(hive == null ? null : hive.honeyStocksJson);
        if (hive == null) {
            return stocks;
        }
        double target = Math.max(0.0, hive.honeyProduction);
        double have = total(stocks);
        if (stocks.isEmpty() && target > 1e-9) {
            String flora = hive.floraType != null && !hive.floraType.trim().isEmpty()
                    ? HoneyMarketEngine.canonicalFloraKey(hive.floraType)
                    : HexFlora.MIL_FLORES;
            stocks.put(flora, target);
        } else if (target > 1e-9 && Math.abs(have - target) > 0.001) {
            // El reloj del servidor actualiza el total y a veces deja el desglose atrás.
            // Sin esto la ficha enseña kilos que «recolectar» no ve.
            scaleToTotal(stocks, target);
        }
        write(hive, stocks);
        return stocks;
    }

    public static void seedStarter(HiveEntity hive) {
        if (hive == null) {
            return;
        }
        LinkedHashMap<String, Double> stocks = new LinkedHashMap<>();
        String flora = hive.floraType != null && !hive.floraType.trim().isEmpty()
                ? HoneyMarketEngine.canonicalFloraKey(hive.floraType)
                : HexFlora.MIL_FLORES;
        double kg = Math.max(0.0, hive.honeyProduction);
        if (kg > 1e-9) {
            stocks.put(flora, kg);
        }
        write(hive, stocks);
    }

    public static void applyNet(HiveEntity hive, double netKg, String forageFlora) {
        if (hive == null) {
            return;
        }
        LinkedHashMap<String, Double> stocks = ensureSeeded(hive);
        String flora = forageFlora != null && !forageFlora.trim().isEmpty()
                ? HoneyMarketEngine.canonicalFloraKey(forageFlora)
                : HexFlora.MIL_FLORES;
        if (netKg > 1e-9) {
            Double prev = stocks.get(flora);
            stocks.put(flora, (prev == null ? 0.0 : prev) + netKg);
        } else if (netKg < -1e-9) {
            consumeProportionally(stocks, -netKg);
        }
        write(hive, stocks);
        clampToCap(hive);
    }

    public static void clampToCap(HiveEntity hive) {
        if (hive == null) {
            return;
        }
        LinkedHashMap<String, Double> stocks = ensureSeeded(hive);
        double cap = HiveHoneyRules.maxHoneyKgForSuperCount(hive.superCount);
        double t = total(stocks);
        if (t > cap + 1e-9) {
            scaleToTotal(stocks, cap);
        } else if (t < 0) {
            stocks.clear();
        }
        write(hive, stocks);
    }

    public static double harvestType(HiveEntity hive, String floraKey, double kgRequested) {
        if (hive == null || kgRequested <= 1e-9) {
            return 0.0;
        }
        LinkedHashMap<String, Double> stocks = ensureSeeded(hive);
        String flora = HoneyMarketEngine.canonicalFloraKey(floraKey);
        double have = stocks.getOrDefault(flora, 0.0);
        double take = Math.min(have, kgRequested);
        if (take <= 1e-9) {
            return 0.0;
        }
        double left = have - take;
        if (left <= 1e-9) {
            stocks.remove(flora);
        } else {
            stocks.put(flora, left);
        }
        write(hive, stocks);
        return take;
    }

    /** Suma kilos de un tipo hasta la capacidad de la colmena. Devuelve lo que ha cabido. */
    public static double depositType(HiveEntity hive, String floraKey, double kg) {
        if (hive == null || kg <= 1e-9) {
            return 0.0;
        }
        LinkedHashMap<String, Double> stocks = ensureSeeded(hive);
        String flora = HoneyMarketEngine.canonicalFloraKey(floraKey);
        double room = HiveHoneyRules.maxHoneyKgForSuperCount(hive.superCount) - total(stocks);
        double add = Math.min(kg, Math.max(0.0, room));
        if (add <= 1e-9) {
            write(hive, stocks);
            return 0.0;
        }
        stocks.put(flora, stocks.getOrDefault(flora, 0.0) + add);
        write(hive, stocks);
        return add;
    }

    /**
     * Calcula kilos a extraer sin modificar la colmena.
     */
    @NonNull
    public static LinkedHashMap<String, Double> peekHarvestLeaving(HiveEntity hive, double leaveKg) {
        LinkedHashMap<String, Double> harvested = new LinkedHashMap<>();
        if (hive == null) {
            return harvested;
        }
        LinkedHashMap<String, Double> stocks = new LinkedHashMap<>(ensureSeeded(hive));
        double total = total(stocks);
        double leave = Math.max(0.0, leaveKg);
        if (total <= leave + 1e-9) {
            return harvested;
        }
        double excess = total - leave;
        List<String> keys = new ArrayList<>(stocks.keySet());
        double taken = 0.0;
        for (int i = 0; i < keys.size(); i++) {
            String k = keys.get(i);
            double share = stocks.getOrDefault(k, 0.0) / total;
            double take = i == keys.size() - 1 ? Math.max(0.0, excess - taken) : excess * share;
            take = Math.min(stocks.getOrDefault(k, 0.0), Math.max(0.0, take));
            if (take <= 1e-9) {
                continue;
            }
            harvested.put(k, take);
            taken += take;
        }
        return harvested;
    }

    /**
     * Saca {@code excess} kg en proporción a cada tipo, dejando {@code leaveKg} en total.
     */
    public static LinkedHashMap<String, Double> harvestLeaving(HiveEntity hive, double leaveKg) {
        LinkedHashMap<String, Double> harvested = peekHarvestLeaving(hive, leaveKg);
        if (hive == null || harvested.isEmpty()) {
            return harvested;
        }
        LinkedHashMap<String, Double> stocks = ensureSeeded(hive);
        for (Map.Entry<String, Double> e : harvested.entrySet()) {
            String k = e.getKey();
            double take = e.getValue() == null ? 0.0 : e.getValue();
            double left = stocks.getOrDefault(k, 0.0) - take;
            if (left <= 1e-9) {
                stocks.remove(k);
            } else {
                stocks.put(k, left);
            }
        }
        write(hive, stocks);
        return harvested;
    }

    public static void splitProportionally(HiveEntity source, HiveEntity dest, double destShare) {
        if (source == null || dest == null) {
            return;
        }
        double share = Math.max(0.0, Math.min(1.0, destShare));
        LinkedHashMap<String, Double> src = ensureSeeded(source);
        LinkedHashMap<String, Double> dst = new LinkedHashMap<>();
        for (Map.Entry<String, Double> e : src.entrySet()) {
            double kg = e.getValue() == null ? 0.0 : e.getValue();
            double move = kg * share;
            if (move <= 1e-9) {
                continue;
            }
            dst.put(e.getKey(), move);
            double left = kg - move;
            src.put(e.getKey(), left);
        }
        pruneEmpty(src);
        write(source, src);
        write(dest, dst);
    }

    private static void consumeProportionally(LinkedHashMap<String, Double> stocks, double kg) {
        double total = total(stocks);
        if (total <= 1e-9 || kg <= 1e-9) {
            stocks.clear();
            return;
        }
        double take = Math.min(total, kg);
        List<String> keys = new ArrayList<>(stocks.keySet());
        double removed = 0.0;
        for (int i = 0; i < keys.size(); i++) {
            String k = keys.get(i);
            double have = stocks.getOrDefault(k, 0.0);
            double part = i == keys.size() - 1 ? Math.max(0.0, take - removed) : take * (have / total);
            part = Math.min(have, Math.max(0.0, part));
            removed += part;
            double left = have - part;
            if (left <= 1e-9) {
                stocks.remove(k);
            } else {
                stocks.put(k, left);
            }
        }
    }

    private static void scaleToTotal(LinkedHashMap<String, Double> stocks, double targetTotal) {
        double t = total(stocks);
        double target = Math.max(0.0, targetTotal);
        if (t <= 1e-9) {
            stocks.clear();
            return;
        }
        if (Math.abs(t - target) < 1e-6) {
            return;
        }
        double f = target / t;
        List<String> keys = new ArrayList<>(stocks.keySet());
        for (String k : keys) {
            double v = stocks.getOrDefault(k, 0.0) * f;
            if (v <= 1e-9) {
                stocks.remove(k);
            } else {
                stocks.put(k, v);
            }
        }
    }

    private static void pruneEmpty(LinkedHashMap<String, Double> stocks) {
        List<String> keys = new ArrayList<>(stocks.keySet());
        for (String k : keys) {
            if (stocks.getOrDefault(k, 0.0) <= 1e-9) {
                stocks.remove(k);
            }
        }
    }

    private static void write(HiveEntity hive, LinkedHashMap<String, Double> stocks) {
        pruneEmpty(stocks);
        hive.honeyStocksJson = serialize(stocks);
        hive.honeyProduction = total(stocks);
    }

    private static double round3(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }
}
