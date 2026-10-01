package com.apiculture.simulator.data.repository;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.apiculture.simulator.data.local.entity.HexParcelOwnershipEntity;
import com.apiculture.simulator.domain.game.TranshumanceRules;
import com.apiculture.simulator.domain.market.HoneyMarketEngine;
import com.apiculture.simulator.domain.parcel.WarehouseRules;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Kilos de cada miel dentro de un almacén concreto. El total del jugador sigue
 * en {@link EconomyRepository}; aquí solo se dice en qué almacén está.
 */
public final class WarehouseHoneyStore {

    private static final String PREFS = "warehouse_honey";

    private WarehouseHoneyStore() {
    }

    public static void clear(@NonNull Context context, @Nullable String ownerId) {
        if (ownerId == null) {
            return;
        }
        prefs(context).edit().remove(ownerId).commit();
    }

    public static double kg(@NonNull Context context, @Nullable String ownerId,
            @Nullable String hexId, @Nullable String flora) {
        if (hexId == null || flora == null) {
            return 0.0;
        }
        String key = HoneyMarketEngine.canonicalFloraKey(flora);
        Map<String, Double> at = at(context, ownerId, hexId);
        return at.getOrDefault(key, 0.0);
    }

    @NonNull
    public static Map<String, Double> at(@NonNull Context context, @Nullable String ownerId,
            @Nullable String hexId) {
        if (ownerId == null || hexId == null) {
            return Collections.emptyMap();
        }
        JSONObject root = read(context, ownerId);
        JSONObject hex = root.optJSONObject(hexId);
        Map<String, Double> out = new LinkedHashMap<>();
        if (hex == null) {
            return out;
        }
        Iterator<String> keys = hex.keys();
        while (keys.hasNext()) {
            String k = keys.next();
            double v = hex.optDouble(k, 0.0);
            if (v > 1e-9) {
                String canon = HoneyMarketEngine.canonicalFloraKey(k);
                out.put(canon, out.getOrDefault(canon, 0.0) + v);
            }
        }
        return out;
    }

    public static double totalAt(@NonNull Context context, @Nullable String ownerId, @Nullable String hexId) {
        double sum = 0.0;
        for (double v : at(context, ownerId, hexId).values()) {
            sum += v;
        }
        return sum;
    }

    public static void add(@NonNull Context context, @Nullable String ownerId, @Nullable String hexId,
            @Nullable String flora, double kg) {
        if (ownerId == null || hexId == null || flora == null || kg <= 1e-9) {
            return;
        }
        String key = HoneyMarketEngine.canonicalFloraKey(flora);
        JSONObject root = read(context, ownerId);
        JSONObject hex = root.optJSONObject(hexId);
        if (hex == null) {
            hex = new JSONObject();
        }
        try {
            hex.put(key, hex.optDouble(key, 0.0) + kg);
            root.put(hexId, hex);
        } catch (Exception ignored) {
            return;
        }
        write(context, ownerId, root);
    }

    public static boolean take(@NonNull Context context, @Nullable String ownerId, @Nullable String hexId,
            @Nullable String flora, double kg) {
        if (ownerId == null || hexId == null || flora == null || kg <= 1e-9) {
            return false;
        }
        String key = HoneyMarketEngine.canonicalFloraKey(flora);
        JSONObject root = read(context, ownerId);
        JSONObject hex = root.optJSONObject(hexId);
        if (hex == null) {
            return false;
        }
        double have = floraKg(hex, key);
        if (have + 1e-6 < kg) {
            if (kg - have > 0.011) {
                return false;
            }
            kg = have;
        }
        try {
            double left = have - kg;
            clearFlora(hex, key);
            if (left > 1e-9) {
                hex.put(key, left);
            }
            root.put(hexId, hex);
        } catch (Exception e) {
            return false;
        }
        return write(context, ownerId, root);
    }

    /**
     * Iguala el reparto con el stock global. Lo que sobre se coloca en los
     * almacenes con sitio; lo que falte se quita del reparto.
     */
    public static void reconcile(@NonNull Context context, @NonNull EconomyRepository economy,
            @Nullable String ownerId, @Nullable List<HexParcelOwnershipEntity> rows) {
        if (ownerId == null) {
            return;
        }
        List<HexParcelOwnershipEntity> warehouses = warehouses(rows, ownerId);
        JSONObject root = read(context, ownerId);
        moveStrayHoney(root, warehouses);
        Map<String, Double> eco = new LinkedHashMap<>();
        for (Map.Entry<String, Double> entry : economy.copyHoneyBuckets().entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            String key = HoneyMarketEngine.canonicalFloraKey(entry.getKey());
            eco.put(key, eco.getOrDefault(key, 0.0) + entry.getValue());
        }
        Map<String, Double> placed = sumByFlora(root);
        for (String flora : union(eco, placed)) {
            double want = eco.getOrDefault(flora, 0.0);
            double have = placed.getOrDefault(flora, 0.0);
            double diff = want - have;
            if (diff > 1e-6) {
                place(root, warehouses, flora, diff);
            } else if (diff < -1e-6) {
                remove(root, flora, -diff);
            }
        }
        write(context, ownerId, root);
    }

    @Nullable
    public static String hexWith(@NonNull Context context, @Nullable String ownerId,
            @Nullable String flora, double kg, double preferLat, double preferLng,
            @Nullable List<HexParcelOwnershipEntity> rows) {
        if (ownerId == null || flora == null || rows == null) {
            return null;
        }
        String key = HoneyMarketEngine.canonicalFloraKey(flora);
        String best = null;
        double bestD = Double.MAX_VALUE;
        for (HexParcelOwnershipEntity row : warehouses(rows, ownerId)) {
            if (kg(context, ownerId, row.hexId, key) + 1e-6 < kg) {
                continue;
            }
            double d = Double.isNaN(preferLat)
                    ? 0.0
                    : TranshumanceRules.haversineKm(row.warehouseLat, row.warehouseLng, preferLat, preferLng);
            if (d < bestD) {
                bestD = d;
                best = row.hexId;
            }
        }
        return best;
    }

    /** La miel de un viaje cerrado en un apiario no se ve en el almacén. Vuelve a uno real. */
    private static void moveStrayHoney(@NonNull JSONObject root,
            @NonNull List<HexParcelOwnershipEntity> warehouses) {
        if (warehouses.isEmpty()) {
            return;
        }
        java.util.Set<String> ids = new java.util.HashSet<>();
        for (HexParcelOwnershipEntity row : warehouses) {
            if (row.hexId != null) {
                ids.add(row.hexId);
            }
        }
        List<String> stray = new ArrayList<>();
        Iterator<String> hexes = root.keys();
        while (hexes.hasNext()) {
            String id = hexes.next();
            if (!ids.contains(id)) {
                stray.add(id);
            }
        }
        for (String id : stray) {
            JSONObject obj = root.optJSONObject(id);
            if (obj == null) {
                root.remove(id);
                continue;
            }
            List<String> floras = new ArrayList<>();
            Iterator<String> keys = obj.keys();
            while (keys.hasNext()) {
                floras.add(keys.next());
            }
            for (String flora : floras) {
                double kg = obj.optDouble(flora, 0.0);
                if (kg > 1e-9) {
                    place(root, warehouses, flora, kg);
                }
            }
            root.remove(id);
        }
    }

    private static void place(JSONObject root, List<HexParcelOwnershipEntity> warehouses,
            String flora, double kg) {
        double left = kg;
        for (HexParcelOwnershipEntity row : warehouses) {
            if (left <= 1e-9) {
                break;
            }
            double used = sumHex(root, row.hexId);
            double room = WarehouseRules.capacityKg(WarehouseRules.levelOf(row)) - used;
            double take = Math.min(left, Math.max(0.0, room));
            if (take <= 1e-9) {
                continue;
            }
            addTo(root, row.hexId, flora, take);
            left -= take;
        }
        if (left > 1e-6 && !warehouses.isEmpty()) {
            addTo(root, warehouses.get(0).hexId, flora, left);
        }
    }

    private static void remove(JSONObject root, String flora, double kg) {
        double left = kg;
        Iterator<String> hexes = root.keys();
        List<String> ids = new ArrayList<>();
        while (hexes.hasNext()) {
            ids.add(hexes.next());
        }
        for (String hex : ids) {
            if (left <= 1e-9) {
                break;
            }
            JSONObject obj = root.optJSONObject(hex);
            if (obj == null) {
                continue;
            }
            double have = floraKg(obj, flora);
            double take = Math.min(have, left);
            if (take <= 1e-9) {
                continue;
            }
            try {
                double rest = have - take;
                clearFlora(obj, flora);
                if (rest > 1e-9) {
                    obj.put(flora, rest);
                }
            } catch (Exception ignored) {
            }
            left -= take;
        }
    }

    private static void addTo(JSONObject root, String hexId, String flora, double kg) {
        JSONObject hex = root.optJSONObject(hexId);
        if (hex == null) {
            hex = new JSONObject();
            try {
                root.put(hexId, hex);
            } catch (Exception ignored) {
                return;
            }
        }
        try {
            hex.put(flora, hex.optDouble(flora, 0.0) + kg);
        } catch (Exception ignored) {
        }
    }

    /** Suma las grafías que el catálogo trata como la misma miel. */
    private static double floraKg(@NonNull JSONObject hex, @NonNull String canonical) {
        double sum = 0.0;
        Iterator<String> keys = hex.keys();
        while (keys.hasNext()) {
            String k = keys.next();
            if (canonical.equals(HoneyMarketEngine.canonicalFloraKey(k))) {
                sum += hex.optDouble(k, 0.0);
            }
        }
        return sum;
    }

    private static void clearFlora(@NonNull JSONObject hex, @NonNull String canonical) {
        List<String> drop = new ArrayList<>();
        Iterator<String> keys = hex.keys();
        while (keys.hasNext()) {
            String k = keys.next();
            if (canonical.equals(HoneyMarketEngine.canonicalFloraKey(k))) {
                drop.add(k);
            }
        }
        for (String k : drop) {
            hex.remove(k);
        }
    }

    private static double sumHex(JSONObject root, String hexId) {
        JSONObject hex = root.optJSONObject(hexId);
        if (hex == null) {
            return 0.0;
        }
        double sum = 0.0;
        Iterator<String> keys = hex.keys();
        while (keys.hasNext()) {
            sum += hex.optDouble(keys.next(), 0.0);
        }
        return sum;
    }

    private static Map<String, Double> sumByFlora(JSONObject root) {
        Map<String, Double> out = new LinkedHashMap<>();
        Iterator<String> hexes = root.keys();
        while (hexes.hasNext()) {
            JSONObject hex = root.optJSONObject(hexes.next());
            if (hex == null) {
                continue;
            }
            Iterator<String> keys = hex.keys();
            while (keys.hasNext()) {
                String k = keys.next();
                String canon = HoneyMarketEngine.canonicalFloraKey(k);
                out.put(canon, out.getOrDefault(canon, 0.0) + hex.optDouble(k, 0.0));
            }
        }
        return out;
    }

    private static List<String> union(Map<String, Double> a, Map<String, Double> b) {
        List<String> keys = new ArrayList<>(a.keySet());
        for (String k : b.keySet()) {
            if (!keys.contains(k)) {
                keys.add(k);
            }
        }
        return keys;
    }

    private static List<HexParcelOwnershipEntity> warehouses(@Nullable List<HexParcelOwnershipEntity> rows,
            @NonNull String ownerId) {
        List<HexParcelOwnershipEntity> out = new ArrayList<>();
        if (rows == null) {
            return out;
        }
        for (HexParcelOwnershipEntity row : rows) {
            if (row != null && row.hasWarehouse && ownerId.equals(row.ownerId) && row.hexId != null) {
                out.add(row);
            }
        }
        out.sort((a, b) -> String.valueOf(a.hexId).compareTo(String.valueOf(b.hexId)));
        return out;
    }

    private static JSONObject read(Context context, String ownerId) {
        try {
            return new JSONObject(prefs(context).getString(ownerId, "{}"));
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    public static void applyServer(@NonNull Context context, @NonNull String ownerId, @NonNull JSONObject stock) {
        prefs(context).edit().putString(ownerId, stock.toString()).commit();
    }

    private static boolean write(Context context, String ownerId, JSONObject root) {
        if (GameServer.enabled()) {
            if (ownerId == null || ownerId.isEmpty()
                    || android.os.Looper.getMainLooper().isCurrentThread()) {
                return false;
            }
            try {
                JSONObject body = new JSONObject();
                body.put("stock", new JSONObject(root.toString()));
                body.put("honeyStockSeq", EconomyRepository.stockSeq(context));
                if (!GameServer.saveStore(ownerId, "warehouse", body)) {
                    return false;
                }
            } catch (Exception ignored) {
                return false;
            }
        }
        prefs(context).edit().putString(ownerId, root.toString()).commit();
        return true;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
