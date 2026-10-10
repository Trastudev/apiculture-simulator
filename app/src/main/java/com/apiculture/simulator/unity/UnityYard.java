package com.apiculture.simulator.unity;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.apiculture.simulator.data.local.AppDatabase;
import com.apiculture.simulator.data.local.entity.CargoTripEntity;
import com.apiculture.simulator.data.local.entity.HexParcelOwnershipEntity;
import com.apiculture.simulator.data.repository.BrandStore;
import com.apiculture.simulator.data.repository.FleetStore;
import com.apiculture.simulator.domain.game.FleetRules;
import com.apiculture.simulator.presentation.hive.YardClimate;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

/**
 * Patio del obrador 3D (clase C# Apiario.YardSnapshot): clima, emblema de la empresa y
 * camiones con base en este obrador. Corre fuera del hilo principal porque lee Room.
 */
final class UnityYard {
    /** Plazas de aparcamiento dibujadas en el patio. */
    static final int PARKING = 5;

    private UnityYard() {
    }

    @NonNull
    static JSONObject build(@NonNull Context app, @Nullable String ownerId, @Nullable String hexId)
            throws JSONException {
        JSONObject yard = new JSONObject();
        yard.put("climate", YardClimate.resolve(app, hexId, null).name());
        BrandStore.Brand brand = BrandStore.get(app, ownerId);
        yard.put("emblem", brand.emblem);
        yard.put("color", brand.color);
        yard.put("brandName", honeyBrand(app, ownerId));
        yard.put("buyCost", FleetRules.purchaseCostB(FleetRules.Kind.TRUCK));
        yard.put("maxLevel", FleetRules.maxLevel(FleetRules.Kind.TRUCK));
        yard.put("parking", PARKING);

        int warehouseLevel = 1;
        if (ownerId != null && hexId != null) {
            List<HexParcelOwnershipEntity> rows = AppDatabase.getInstance(app)
                    .hexParcelOwnershipDao().getWarehousesForOwnerSync(ownerId);
            if (rows != null) {
                for (HexParcelOwnershipEntity row : rows) {
                    if (row != null && row.hasWarehouse && hexId.equals(row.hexId)) {
                        warehouseLevel = Math.max(1, row.warehouseLevel);
                    }
                }
            }
        }
        yard.put("slots", Math.min(PARKING, FleetRules.truckSlots(warehouseLevel)));

        List<CargoTripEntity> trips = AppDatabase.getInstance(app).cargoTripDao().getAllSync();
        JSONArray trucks = new JSONArray();
        for (FleetStore.Vehicle v : FleetStore.vehicles(app, ownerId)) {
            if (!v.isTruck() || hexId == null || !hexId.equals(v.homeId)) {
                continue;
            }
            JSONObject o = new JSONObject();
            o.put("id", v.id);
            o.put("name", v.name == null ? "" : v.name);
            o.put("level", v.level);
            o.put("kg", FleetRules.honeyKg(FleetRules.Kind.TRUCK, v.level));
            o.put("kmh", FleetRules.speedKmh(FleetRules.Kind.TRUCK, v.level));
            o.put("hives", FleetRules.hiveSlots(FleetRules.Kind.TRUCK, v.level));
            o.put("upgradeCost", FleetRules.upgradeCostB(FleetRules.Kind.TRUCK, v.level));
            // Mientras exista un viaje suyo sigue fuera, aunque el tramo haya acabado: entre la ida y la
            // vuelta, o hasta que se liquida, no debe aparecer en la plaza y volver a irse.
            long backAt = 0;
            boolean onTrip = false;
            String trip = "";
            String dest = "";
            if (trips != null) {
                for (CargoTripEntity t : trips) {
                    if (t == null || v.id == null || !v.id.equals(t.vehicleId)) {
                        continue;
                    }
                    onTrip = true;
                    long end = t.startEpochMs + t.durationMs;
                    if (end > backAt) {
                        backAt = end;
                        trip = t.kind == null ? "" : t.kind;
                        dest = t.destLabel == null ? "" : t.destLabel;
                    }
                }
            }
            boolean away = onTrip || v.honeyBusy() || v.hiveTrips > 0;
            o.put("away", away);
            o.put("backAt", backAt);
            o.put("trip", away && trip.isEmpty() && v.hiveTrips > 0 ? "hives" : trip);
            o.put("dest", dest);
            trucks.put(o);
        }
        yard.put("trucks", trucks);
        return yard;
    }

    @NonNull
    private static String honeyBrand(@NonNull Context app, @Nullable String ownerId) {
        if (ownerId == null) {
            return "";
        }
        String raw = app.getSharedPreferences("profile_header_cache", Context.MODE_PRIVATE)
                .getString(ownerId, null);
        if (raw == null) {
            return "";
        }
        try {
            return new JSONObject(raw).optString("honeyBrand", "");
        } catch (JSONException e) {
            return "";
        }
    }
}
