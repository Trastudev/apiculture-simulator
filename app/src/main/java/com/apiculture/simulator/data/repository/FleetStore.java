package com.apiculture.simulator.data.repository;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.apiculture.simulator.data.local.AppDatabase;
import com.apiculture.simulator.data.local.entity.CargoTripEntity;
import com.apiculture.simulator.data.local.entity.HexParcelOwnershipEntity;
import com.apiculture.simulator.data.local.entity.TruckTripEntity;
import com.apiculture.simulator.domain.game.FleetRules;
import com.apiculture.simulator.domain.game.GameCalendar;
import com.apiculture.simulator.domain.map.Seaport;
import com.apiculture.simulator.domain.map.SeaportCatalog;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Camiones, barcos y amarres del jugador. El puerto es público; el amarre es suyo. */
public final class FleetStore {

    private static final String PREFS = "fleet_store";

    private FleetStore() {
    }

    public static final class Vehicle {
        public String id;
        public String ownerId;
        public String kind;
        public int level;
        public String homeId;
        @Nullable
        public String name;
        @Nullable
        public String cargoTripId;
        public int hiveTrips;
        @Nullable
        public String hiveIds;

        public boolean isTruck() {
            return "truck".equals(kind);
        }

        public boolean honeyBusy() {
            return cargoTripId != null && !cargoTripId.isEmpty();
        }

        public FleetRules.Kind rulesKind() {
            return isTruck() ? FleetRules.Kind.TRUCK : FleetRules.Kind.SHIP;
        }
    }

    public static final class PortSite {
        public String portId;
        /** Amarres de este jugador en el puerto. */
        public int berths;
    }

    public static void clear(@NonNull Context context, @Nullable String ownerId) {
        if (ownerId == null || ownerId.isEmpty()) {
            return;
        }
        prefs(context).edit().remove(key(ownerId, "vehicles"))
                .remove(key(ownerId, "ports"))
                .remove(key(ownerId, "exotic"))
                .remove(key(ownerId, "exoticDay"))
                .commit();
        TradeAccessStore.clear(context, ownerId);
    }

    @NonNull
    public static List<Vehicle> vehicles(@NonNull Context context, @Nullable String ownerId) {
        List<Vehicle> out = new ArrayList<>();
        if (ownerId == null) {
            return out;
        }
        try {
            JSONArray arr = new JSONArray(prefs(context).getString(key(ownerId, "vehicles"), "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) {
                    continue;
                }
                Vehicle v = new Vehicle();
                v.id = o.optString("id");
                v.ownerId = ownerId;
                v.kind = o.optString("kind", "truck");
                v.level = Math.max(1, o.optInt("level", 1));
                v.homeId = o.optString("homeId");
                String name = o.optString("name", "").trim();
                v.name = name.isEmpty() ? null : name;
                String busy = o.optString("cargo", "");
                v.cargoTripId = busy.isEmpty() ? null : busy;
                v.hiveTrips = Math.max(0, o.optInt("hives", 0));
                String hives = o.optString("hiveIds", "");
                v.hiveIds = hives.isEmpty() ? null : hives;
                out.add(v);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    @Nullable
    public static Vehicle vehicle(@NonNull Context context, @Nullable String ownerId, @Nullable String id) {
        if (id == null) {
            return null;
        }
        for (Vehicle v : vehicles(context, ownerId)) {
            if (id.equals(v.id)) {
                return v;
            }
        }
        return null;
    }

    @NonNull
    public static List<PortSite> ports(@NonNull Context context, @Nullable String ownerId) {
        List<PortSite> out = new ArrayList<>();
        if (ownerId == null) {
            return out;
        }
        try {
            JSONArray arr = new JSONArray(prefs(context).getString(key(ownerId, "ports"), "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) {
                    continue;
                }
                PortSite p = new PortSite();
                p.portId = o.optString("id");
                p.berths = o.has("berths")
                        ? Math.max(0, o.optInt("berths", 0))
                        : berthsFromLegacyLevel(o.optInt("level", 0));
                if (p.berths > 0) {
                    out.add(p);
                }
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    public static int berths(@NonNull Context context, @Nullable String ownerId, @Nullable String portId) {
        for (PortSite site : ports(context, ownerId)) {
            if (site.portId != null && site.portId.equals(portId)) {
                return site.berths;
            }
        }
        return 0;
    }

    /** Si ya hay almacén y ningún camión, deja uno de nivel 1 en ese hex. */
    public static void ensureStarter(@NonNull Context context, @Nullable String ownerId, @Nullable String hexId) {
        if (ownerId == null || hexId == null || hexId.isEmpty()) {
            return;
        }
        List<Vehicle> all = vehicles(context, ownerId);
        for (Vehicle v : all) {
            if (v.isTruck()) {
                return;
            }
        }
        Vehicle v = new Vehicle();
        v.id = UUID.randomUUID().toString();
        v.ownerId = ownerId;
        v.kind = "truck";
        v.name = "Del almacén";
        v.level = 1;
        v.homeId = hexId;
        all.add(v);
        writeVehicles(context, ownerId, all);
    }

    @Nullable
    public static String buyTruck(@NonNull Context context, @NonNull EconomyRepository economy,
            @Nullable String ownerId, @Nullable String hexId, @Nullable String rawName) {
        String name = com.apiculture.simulator.domain.game.EntityNames.clean(rawName);
        if (name == null) {
            return "Pon un nombre de 2 a 32 letras.";
        }
        if (nameTaken(context, ownerId, true, name)) {
            return "Ya tienes un camión con ese nombre.";
        }
        if (ownerId == null || hexId == null) {
            return "Elige un almacén.";
        }
        HexParcelOwnershipEntity row = warehouseRow(context, ownerId, hexId);
        if (row == null || !row.hasWarehouse) {
            return "Ese almacén no existe.";
        }
        int slots = FleetRules.truckSlots(Math.max(1, row.warehouseLevel));
        int used = 0;
        for (Vehicle v : vehicles(context, ownerId)) {
            if (v.isTruck() && hexId.equals(v.homeId)) {
                used++;
            }
        }
        if (used >= slots) {
            return "Ese almacén no tiene plaza libre para otro camión.";
        }
        int cost = FleetRules.purchaseCostB(FleetRules.Kind.TRUCK);
        if (!economy.trySpend(cost, "Compra del camión " + name)) {
            return economy.blockedReason(cost);
        }
        List<Vehicle> all = vehicles(context, ownerId);
        Vehicle v = new Vehicle();
        v.id = UUID.randomUUID().toString();
        v.kind = "truck";
        v.name = name;
        v.level = 1;
        v.homeId = hexId;
        v.cargoTripId = "delivery";
        all.add(v);
        if (!writeVehicles(context, ownerId, all)) {
            economy.addToBalance(cost, "Devolución de la compra del camión " + name);
            return EconomyRepository.OFFLINE_ACTION;
        }
        return null;
    }

    @Nullable
    public static String buyShip(@NonNull Context context, @NonNull EconomyRepository economy,
            @Nullable String ownerId, @Nullable String portId, @Nullable String rawName) {
        String name = com.apiculture.simulator.domain.game.EntityNames.clean(rawName);
        if (name == null) {
            return "Pon un nombre de 2 a 32 letras.";
        }
        if (nameTaken(context, ownerId, false, name)) {
            return "Ya tienes un barco con ese nombre.";
        }
        if (ownerId == null || portId == null) {
            return "Elige un puerto.";
        }
        if (!TradeAccessStore.portPaid(context, ownerId, portId)) {
            return "Primero paga la cuota de este puerto.";
        }
        int slots = berths(context, ownerId, portId);
        if (slots <= 0) {
            return "Compra un amarre en este puerto.";
        }
        int used = 0;
        for (Vehicle v : vehicles(context, ownerId)) {
            if (!v.isTruck() && portId.equals(v.homeId)) {
                used++;
            }
        }
        if (used >= slots) {
            return "No te queda un amarre libre.";
        }
        int cost = FleetRules.purchaseCostB(FleetRules.Kind.SHIP);
        if (!economy.trySpend(cost, "Compra del barco " + name)) {
            return economy.blockedReason(cost);
        }
        List<Vehicle> all = vehicles(context, ownerId);
        Vehicle v = new Vehicle();
        v.id = UUID.randomUUID().toString();
        v.kind = "ship";
        v.name = name;
        v.level = 1;
        v.homeId = portId;
        all.add(v);
        if (!writeVehicles(context, ownerId, all)) {
            economy.addToBalance(cost, "Devolución de la compra del barco " + name);
            return EconomyRepository.OFFLINE_ACTION;
        }
        return null;
    }

    @Nullable
    public static String upgradeVehicle(@NonNull Context context, @NonNull EconomyRepository economy,
            @Nullable String ownerId, @Nullable String vehicleId) {
        List<Vehicle> all = vehicles(context, ownerId);
        Vehicle found = null;
        for (Vehicle v : all) {
            if (v.id != null && v.id.equals(vehicleId)) {
                found = v;
                break;
            }
        }
        if (found == null) {
            return "No se encuentra el vehículo.";
        }
        if (found.level >= FleetRules.maxLevel(found.rulesKind())) {
            return "Ya está al máximo.";
        }
        int cost = FleetRules.upgradeCostB(found.rulesKind(), found.level);
        String vehicleLabel = found.isTruck() ? "camión " : "barco ";
        String vehicleName = found.name == null ? "" : found.name;
        if (!economy.trySpend(cost, "Mejora del " + vehicleLabel + vehicleName
                + " al nivel " + (found.level + 1))) {
            return economy.blockedReason(cost);
        }
        found.level += 1;
        if (!writeVehicles(context, ownerId, all)) {
            found.level -= 1;
            economy.addToBalance(cost, "Devolución de la mejora");
            return EconomyRepository.OFFLINE_ACTION;
        }
        return null;
    }

    @Nullable
    public static String buyBerth(@NonNull Context context, @NonNull EconomyRepository economy,
            @Nullable String ownerId, @Nullable String portId) {
        if (ownerId == null || SeaportCatalog.byId(portId) == null) {
            return "Puerto desconocido.";
        }
        if (!TradeAccessStore.portPaid(context, ownerId, portId)) {
            return "Primero paga la cuota de este puerto.";
        }
        List<PortSite> all = ports(context, ownerId);
        PortSite found = null;
        for (PortSite site : all) {
            if (site.portId != null && site.portId.equals(portId)) {
                found = site;
                break;
            }
        }
        int have = found == null ? 0 : found.berths;
        if (have >= FleetRules.MAX_BERTHS) {
            return "Ya tienes el máximo de amarres en este puerto.";
        }
        if (!economy.trySpend(FleetRules.BERTH_B, "Compra de un amarre")) {
            return economy.blockedReason(FleetRules.BERTH_B);
        }
        if (found == null) {
            found = new PortSite();
            found.portId = portId;
            all.add(found);
        }
        found.berths = have + 1;
        if (!writePorts(context, ownerId, all)) {
            found.berths = have;
            economy.addToBalance(FleetRules.BERTH_B, "Devolución del amarre");
            return EconomyRepository.OFFLINE_ACTION;
        }
        return null;
    }

    @Nullable
    public static Vehicle reserveHoneyTruck(@NonNull Context context, @Nullable String ownerId,
            @Nullable String homeHex, double kg) {
        return reserveHoney(context, ownerId, true, homeHex, kg);
    }

    @Nullable
    public static Vehicle reserveShip(@NonNull Context context, @Nullable String ownerId,
            @Nullable String portId, double kg) {
        return reserveHoney(context, ownerId, false, portId, kg);
    }

    public static void releaseVehicle(@NonNull Context context, @Nullable String ownerId,
            @Nullable String vehicleId) {
        if (vehicleId == null || ownerId == null) {
            return;
        }
        List<Vehicle> all = vehicles(context, ownerId);
        for (Vehicle v : all) {
            if (vehicleId.equals(v.id)) {
                v.cargoTripId = null;
                writeVehicles(context, ownerId, all);
                return;
            }
        }
    }

    /**
     * Un camión queda libre si su marca de ruta ya no corresponde a ningún viaje.
     * La compra deja «delivery» hasta que sale de la tienda; si ese viaje no existe, vuelve al almacén.
     */
    public static void releaseIdle(@NonNull Context context, @Nullable String ownerId) {
        if (ownerId == null || ownerId.isEmpty()) {
            return;
        }
        Set<String> liveCargo = new HashSet<>();
        Map<String, String> cargoByVehicle = new HashMap<>();
        Set<String> delivering = new HashSet<>();
        List<CargoTripEntity> cargos = AppDatabase.getInstance(context).cargoTripDao().getAllSync();
        if (cargos != null) {
            for (CargoTripEntity trip : cargos) {
                if (trip == null || trip.ownerId == null || !ownerId.equals(trip.ownerId)) {
                    continue;
                }
                if (trip.id != null && !trip.id.isEmpty()) {
                    liveCargo.add(trip.id);
                }
                if (trip.vehicleId != null && !trip.vehicleId.isEmpty()
                        && trip.id != null && !trip.id.isEmpty()
                        && !cargoByVehicle.containsKey(trip.vehicleId)) {
                    cargoByVehicle.put(trip.vehicleId, trip.id);
                }
                if (CargoTripEntity.KIND_DELIVERY.equals(trip.kind)
                        && trip.vehicleId != null && !trip.vehicleId.isEmpty()) {
                    delivering.add(trip.vehicleId);
                }
            }
        }
        Set<String> movingHives = new HashSet<>();
        List<TruckTripEntity> hives = AppDatabase.getInstance(context).truckTripDao().getAllSync();
        if (hives != null) {
            for (TruckTripEntity trip : hives) {
                if (trip == null || trip.ownerId == null || !ownerId.equals(trip.ownerId)) {
                    continue;
                }
                if (trip.hiveId != null && !trip.hiveId.isEmpty()) {
                    movingHives.add(trip.hiveId);
                }
            }
        }
        List<Vehicle> all = vehicles(context, ownerId);
        boolean changed = false;
        for (Vehicle v : all) {
            String onRoad = cargoByVehicle.get(v.id);
            if ("hold".equals(v.cargoTripId)) {
                v.cargoTripId = onRoad;
                changed = true;
            } else if (v.cargoTripId != null && !"hold".equals(v.cargoTripId)) {
                boolean live = "delivery".equals(v.cargoTripId)
                        ? delivering.contains(v.id)
                        : liveCargo.contains(v.cargoTripId);
                if (!live) {
                    v.cargoTripId = onRoad;
                    changed = true;
                }
            } else if ((v.cargoTripId == null || v.cargoTripId.isEmpty()) && onRoad != null) {
                v.cargoTripId = onRoad;
                changed = true;
            }
            String kept = liveHiveIds(v.hiveIds, movingHives);
            int count = kept == null || kept.isEmpty() ? 0 : kept.split(",").length;
            if (count != v.hiveTrips || !sameText(kept, v.hiveIds)) {
                v.hiveTrips = count;
                v.hiveIds = count == 0 ? null : kept;
                changed = true;
            }
        }
        if (changed) {
            writeVehicles(context, ownerId, all);
        }
    }

    /** Igual que {@link #releaseIdle} para cada jugador con flota en este teléfono. */
    public static void releaseIdleKnown(@NonNull Context context) {
        for (String key : prefs(context).getAll().keySet()) {
            if (key != null && key.endsWith(".vehicles")) {
                releaseIdle(context, key.substring(0, key.length() - ".vehicles".length()));
            }
        }
    }

    @Nullable
    private static String liveHiveIds(@Nullable String raw, @NonNull Set<String> moving) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        StringBuilder kept = new StringBuilder();
        for (String part : raw.split(",")) {
            if (part == null || part.isEmpty() || !moving.contains(part)) {
                continue;
            }
            if (kept.length() > 0) {
                kept.append(',');
            }
            kept.append(part);
        }
        return kept.length() == 0 ? null : kept.toString();
    }

    private static boolean sameText(@Nullable String a, @Nullable String b) {
        String left = a == null ? "" : a;
        String right = b == null ? "" : b;
        return left.equals(right);
    }

    /** Libera el camión asociado a una colmena que ya llegó. */
    public static void releaseHive(@NonNull Context context, @Nullable String ownerId, @Nullable String hiveId) {
        if (ownerId == null || hiveId == null) {
            return;
        }
        List<Vehicle> all = vehicles(context, ownerId);
        boolean changed = false;
        for (Vehicle v : all) {
            if (v.hiveIds == null || !containsId(v.hiveIds, hiveId)) {
                continue;
            }
            v.hiveTrips = Math.max(0, v.hiveTrips - 1);
            v.hiveIds = removeId(v.hiveIds, hiveId);
            changed = true;
            break;
        }
        if (changed) {
            writeVehicles(context, ownerId, all);
        }
    }

    /**
     * Reserva un hueco de colmena. El id de la colmena queda en el vehículo
     * solo como última reserva; los huecos se cuentan en {@link Vehicle#hiveTrips}.
     */
    @Nullable
    public static Vehicle reserveHive(@NonNull Context context, @Nullable String ownerId,
            @Nullable String hiveId) {
        if (ownerId == null) {
            return null;
        }
        List<Vehicle> all = vehicles(context, ownerId);
        Vehicle best = null;
        for (Vehicle v : all) {
            if (!v.isTruck() || v.honeyBusy()) {
                continue;
            }
            int slots = FleetRules.hiveSlots(FleetRules.Kind.TRUCK, v.level);
            if (v.hiveTrips >= slots) {
                continue;
            }
            if (best == null || v.level > best.level) {
                best = v;
            }
        }
        if (best == null) {
            return null;
        }
        best.hiveTrips += 1;
        best.hiveIds = appendId(best.hiveIds, hiveId);
        writeVehicles(context, ownerId, all);
        return best;
    }

    public static double exoticSoldKg(@NonNull Context context, @Nullable String ownerId,
            @Nullable String marketId) {
        if (ownerId == null || marketId == null) {
            return 0.0;
        }
        int today = GameCalendar.currentGlobalMarketDayKey();
        SharedPreferences p = prefs(context);
        if (p.getInt(key(ownerId, "exoticDay"), 0) != today) {
            return 0.0;
        }
        try {
            JSONObject o = new JSONObject(p.getString(key(ownerId, "exotic"), "{}"));
            return o.optDouble(marketId, 0.0);
        } catch (Exception e) {
            return 0.0;
        }
    }

    public static void addExoticSold(@NonNull Context context, @Nullable String ownerId,
            @Nullable String marketId, double kg) {
        if (ownerId == null || marketId == null || kg <= 0) {
            return;
        }
        int today = GameCalendar.currentGlobalMarketDayKey();
        SharedPreferences p = prefs(context);
        JSONObject o;
        if (p.getInt(key(ownerId, "exoticDay"), 0) != today) {
            o = new JSONObject();
        } else {
            try {
                o = new JSONObject(p.getString(key(ownerId, "exotic"), "{}"));
            } catch (Exception e) {
                o = new JSONObject();
            }
        }
        try {
            o.put(marketId, o.optDouble(marketId, 0.0) + kg);
        } catch (Exception ignored) {
            return;
        }
        p.edit().putInt(key(ownerId, "exoticDay"), today)
                .putString(key(ownerId, "exotic"), o.toString())
                .commit();
    }

    @Nullable
    private static Vehicle reserveHoney(@NonNull Context context, @Nullable String ownerId,
            boolean truck, @Nullable String homeId, double kg) {
        if (ownerId == null || homeId == null) {
            return null;
        }
        List<Vehicle> all = vehicles(context, ownerId);
        Vehicle best = null;
        for (Vehicle v : all) {
            if (v.isTruck() != truck || !homeId.equals(v.homeId)) {
                continue;
            }
            if (v.honeyBusy() || v.hiveTrips > 0) {
                continue;
            }
            if (FleetRules.honeyKg(v.rulesKind(), v.level) + 1e-6 < kg) {
                continue;
            }
            if (best == null || v.level > best.level) {
                best = v;
            }
        }
        if (best == null) {
            return null;
        }
        best.cargoTripId = "hold";
        writeVehicles(context, ownerId, all);
        return best;
    }

    /** Reserva ese vehículo, si sigue libre. */
    @Nullable
    public static Vehicle holdVehicle(@NonNull Context context, @Nullable String ownerId,
            @Nullable String vehicleId) {
        if (ownerId == null || vehicleId == null || vehicleId.isEmpty()) {
            return null;
        }
        List<Vehicle> all = vehicles(context, ownerId);
        for (Vehicle v : all) {
            if (!vehicleId.equals(v.id)) {
                continue;
            }
            if (v.honeyBusy() || v.hiveTrips > 0) {
                return null;
            }
            v.cargoTripId = "hold";
            writeVehicles(context, ownerId, all);
            return v;
        }
        return null;
    }

    public static void bindCargo(@NonNull Context context, @Nullable String ownerId,
            @Nullable String vehicleId, @Nullable String tripId) {
        if (ownerId == null || vehicleId == null) {
            return;
        }
        List<Vehicle> all = vehicles(context, ownerId);
        for (Vehicle v : all) {
            if (vehicleId.equals(v.id)) {
                v.cargoTripId = tripId;
                writeVehicles(context, ownerId, all);
                return;
            }
        }
    }

    @Nullable
    private static boolean nameTaken(@NonNull Context context, @Nullable String ownerId,
            boolean truck, @NonNull String name) {
        for (Vehicle v : vehicles(context, ownerId)) {
            if (v.isTruck() != truck || v.name == null) {
                continue;
            }
            if (com.apiculture.simulator.domain.game.EntityNames.same(v.name, name)) {
                return true;
            }
        }
        return false;
    }

    private static HexParcelOwnershipEntity warehouseRow(@NonNull Context context,
            @NonNull String ownerId, @NonNull String hexId) {
        List<HexParcelOwnershipEntity> rows = AppDatabase.getInstance(context)
                .hexParcelOwnershipDao().getWarehousesForOwnerSync(ownerId);
        if (rows == null) {
            return null;
        }
        for (HexParcelOwnershipEntity row : rows) {
            if (row != null && hexId.equals(row.hexId) && row.hasWarehouse) {
                return row;
            }
        }
        return null;
    }

    private static boolean writeVehicles(@NonNull Context context, @NonNull String ownerId, @NonNull List<Vehicle> all) {
        JSONArray arr = new JSONArray();
        for (Vehicle v : all) {
            JSONObject o = new JSONObject();
            try {
                o.put("id", v.id);
                o.put("kind", v.kind);
                o.put("level", v.level);
                o.put("homeId", v.homeId);
                o.put("name", v.name == null ? "" : v.name);
                o.put("cargo", v.cargoTripId == null ? "" : v.cargoTripId);
                o.put("hives", v.hiveTrips);
                o.put("hiveIds", v.hiveIds == null ? "" : v.hiveIds);
                arr.put(o);
            } catch (Exception ignored) {
            }
        }
        String json = arr.toString();
        if (!publish(context, ownerId, json, null)) {
            return false;
        }
        prefs(context).edit().putString(key(ownerId, "vehicles"), json).commit();
        return true;
    }

    private static boolean writePorts(@NonNull Context context, @NonNull String ownerId, @NonNull List<PortSite> all) {
        JSONArray arr = new JSONArray();
        for (PortSite site : all) {
            JSONObject o = new JSONObject();
            try {
                o.put("id", site.portId);
                o.put("berths", site.berths);
                arr.put(o);
            } catch (Exception ignored) {
            }
        }
        String json = arr.toString();
        if (!publish(context, ownerId, null, json)) {
            return false;
        }
        prefs(context).edit().putString(key(ownerId, "ports"), json).commit();
        return true;
    }

    public static void applyServer(@NonNull Context context, @NonNull String ownerId, @NonNull JSONObject body) {
        SharedPreferences.Editor edit = prefs(context).edit();
        JSONArray vehicles = body.optJSONArray("vehicles");
        JSONArray ports = body.optJSONArray("ports");
        if (vehicles != null) {
            edit.putString(key(ownerId, "vehicles"), vehicles.toString());
        }
        if (ports != null) {
            edit.putString(key(ownerId, "ports"), ports.toString());
        }
        edit.commit();
    }

    private static boolean publish(@NonNull Context context, @NonNull String ownerId,
            @Nullable String vehiclesJson, @Nullable String portsJson) {
        if (!GameServer.enabled()) {
            return true;
        }
        if (ownerId.isEmpty() || android.os.Looper.getMainLooper().isCurrentThread()) {
            return false;
        }
        try {
            JSONObject body = new JSONObject();
            body.put("vehicles", new JSONArray(vehiclesJson != null
                    ? vehiclesJson
                    : prefs(context).getString(key(ownerId, "vehicles"), "[]")));
            body.put("ports", new JSONArray(portsJson != null
                    ? portsJson
                    : prefs(context).getString(key(ownerId, "ports"), "[]")));
            return GameServer.saveStore(ownerId, "fleet", body);
        } catch (Exception ignored) {
            return false;
        }
    }

    public static void pushServer(@NonNull Context context, @NonNull String ownerId) {
        if (!GameServer.enabled() || ownerId.isEmpty()) {
            return;
        }
        if (android.os.Looper.getMainLooper().isCurrentThread()) {
            new Thread(() -> pushServer(context, ownerId), "fleet-sync").start();
            return;
        }
        publish(context, ownerId, null, null);
    }

    @NonNull
    private static SharedPreferences prefs(@NonNull Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String key(String ownerId, String field) {
        return ownerId + "." + field;
    }

    /** Comprueba que el catálogo sigue teniendo el puerto antes de mostrarlo. */
    @Nullable
    public static Seaport portOrNull(@Nullable String portId) {
        return SeaportCatalog.byId(portId);
    }

    /** Partidas antiguas guardaban un nivel de puerto. Ese nivel pasa a amarres. */
    private static int berthsFromLegacyLevel(int level) {
        if (level <= 0) {
            return 0;
        }
        if (level <= 2) {
            return 1;
        }
        if (level <= 4) {
            return 2;
        }
        return 3;
    }

    private static boolean containsId(@NonNull String csv, @NonNull String id) {
        for (String part : csv.split(",")) {
            if (id.equals(part)) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    private static String appendId(@Nullable String csv, @Nullable String id) {
        if (id == null || id.isEmpty()) {
            return csv;
        }
        if (csv == null || csv.isEmpty()) {
            return id;
        }
        return csv + "," + id;
    }

    @Nullable
    private static String removeId(@Nullable String csv, @NonNull String id) {
        if (csv == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (String part : csv.split(",")) {
            if (part.isEmpty() || id.equals(part)) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(part);
        }
        return sb.length() == 0 ? null : sb.toString();
    }
}
