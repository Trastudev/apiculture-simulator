package com.apiculture.simulator.domain.game;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.apiculture.simulator.domain.market.HoneyMarketEngine;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/** Snapshot de pecoreo de un hex para Firestore / Room. */
public final class HexForageSnapshot {

    public final int dayKey;
    public final double siteFactor;
    public final Map<String, Double> demandByFlora;
    public final Map<String, Double> poolByFlora;
    public final Map<String, Double> leftoverDemandByFlora;
    public final Map<String, Double> leftoverPoolByFlora;

    public HexForageSnapshot(
            int dayKey,
            double siteFactor,
            @Nullable Map<String, Double> demandByFlora,
            @Nullable Map<String, Double> poolByFlora,
            @Nullable Map<String, Double> leftoverDemandByFlora,
            @Nullable Map<String, Double> leftoverPoolByFlora) {
        this.dayKey = dayKey;
        this.siteFactor = Math.max(0.0, siteFactor);
        this.demandByFlora = copy(demandByFlora);
        this.poolByFlora = copy(poolByFlora);
        this.leftoverDemandByFlora = copy(leftoverDemandByFlora);
        this.leftoverPoolByFlora = copy(leftoverPoolByFlora);
    }

    public HexNectarPool.NeighborSnap toNeighborSnap(@NonNull String hexId, @Nullable String ownerId) {
        return new HexNectarPool.NeighborSnap(
                hexId, ownerId, siteFactor, demandByFlora, leftoverDemandByFlora, leftoverPoolByFlora);
    }

    @NonNull
    public String toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("dayKey", dayKey);
            o.put("siteFactor", siteFactor);
            o.put("demand", mapJson(demandByFlora));
            o.put("pool", mapJson(poolByFlora));
            o.put("leftoverDemand", mapJson(leftoverDemandByFlora));
            o.put("leftoverPool", mapJson(leftoverPoolByFlora));
        } catch (Exception ignored) {
        }
        return o.toString();
    }

    @NonNull
    public Map<String, Object> toFirestoreFields() {
        Map<String, Object> m = new HashMap<>();
        m.put("forageDayKey", dayKey);
        m.put("forageSiteFactor", siteFactor);
        m.put("forageDemandKgByFlora", new HashMap<>(demandByFlora));
        m.put("foragePoolKgByFlora", new HashMap<>(poolByFlora));
        m.put("forageLeftoverDemandKgByFlora", new HashMap<>(leftoverDemandByFlora));
        m.put("forageLeftoverPoolKgByFlora", new HashMap<>(leftoverPoolByFlora));
        return m;
    }

    @NonNull
    public static HexForageSnapshot fromJson(@Nullable String json) {
        if (json == null || json.trim().isEmpty()) {
            return empty(0);
        }
        try {
            JSONObject o = new JSONObject(json);
            return new HexForageSnapshot(
                    o.optInt("dayKey", 0),
                    o.optDouble("siteFactor", 0.0),
                    readMap(o.optJSONObject("demand")),
                    readMap(o.optJSONObject("pool")),
                    readMap(o.optJSONObject("leftoverDemand")),
                    readMap(o.optJSONObject("leftoverPool")));
        } catch (Exception e) {
            return empty(0);
        }
    }

    @NonNull
    public static HexForageSnapshot fromFirestore(
            @Nullable Number dayKey,
            @Nullable Number siteFactor,
            @Nullable Object demand,
            @Nullable Object pool,
            @Nullable Object leftoverDemand,
            @Nullable Object leftoverPool) {
        return new HexForageSnapshot(
                dayKey != null ? dayKey.intValue() : 0,
                siteFactor != null ? siteFactor.doubleValue() : 0.0,
                objectMap(demand),
                objectMap(pool),
                objectMap(leftoverDemand),
                objectMap(leftoverPool));
    }

    public static HexForageSnapshot empty(int dayKey) {
        return new HexForageSnapshot(dayKey, 0, null, null, null, null);
    }

    private static JSONObject mapJson(Map<String, Double> m) {
        JSONObject o = new JSONObject();
        if (m == null) {
            return o;
        }
        for (Map.Entry<String, Double> e : m.entrySet()) {
            if (e.getKey() == null || e.getValue() == null) {
                continue;
            }
            try {
                o.put(HoneyMarketEngine.canonicalFloraKey(e.getKey()), e.getValue());
            } catch (Exception ignored) {
            }
        }
        return o;
    }

    private static Map<String, Double> readMap(@Nullable JSONObject o) {
        Map<String, Double> m = new HashMap<>();
        if (o == null) {
            return m;
        }
        Iterator<String> it = o.keys();
        while (it.hasNext()) {
            String k = it.next();
            m.put(HoneyMarketEngine.canonicalFloraKey(k), o.optDouble(k, 0.0));
        }
        return m;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Double> objectMap(@Nullable Object raw) {
        Map<String, Double> m = new HashMap<>();
        if (!(raw instanceof Map)) {
            return m;
        }
        Map<?, ?> src = (Map<?, ?>) raw;
        for (Map.Entry<?, ?> e : src.entrySet()) {
            if (e.getKey() == null) {
                continue;
            }
            String k = HoneyMarketEngine.canonicalFloraKey(String.valueOf(e.getKey()));
            Object v = e.getValue();
            double d = 0.0;
            if (v instanceof Number) {
                d = ((Number) v).doubleValue();
            }
            m.put(k, d);
        }
        return m;
    }

    private static Map<String, Double> copy(@Nullable Map<String, Double> src) {
        Map<String, Double> m = new HashMap<>();
        if (src == null) {
            return m;
        }
        for (Map.Entry<String, Double> e : src.entrySet()) {
            if (e.getKey() == null || e.getValue() == null) {
                continue;
            }
            m.put(HoneyMarketEngine.canonicalFloraKey(e.getKey()), e.getValue());
        }
        return m;
    }
}
