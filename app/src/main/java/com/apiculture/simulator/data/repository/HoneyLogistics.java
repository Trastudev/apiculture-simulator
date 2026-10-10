package com.apiculture.simulator.data.repository;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;

import com.apiculture.simulator.ApicultureApp;
import com.apiculture.simulator.R;
import com.apiculture.simulator.data.local.AppDatabase;
import com.apiculture.simulator.data.local.dao.CargoTripDao;
import com.apiculture.simulator.data.local.dao.HexParcelOwnershipDao;
import com.apiculture.simulator.data.local.entity.CargoTripEntity;
import com.apiculture.simulator.data.local.entity.HoneyOrderEntity;
import com.apiculture.simulator.data.local.entity.HexParcelOwnershipEntity;
import com.apiculture.simulator.data.local.entity.HiveEntity;
import com.apiculture.simulator.data.local.entity.TruckTripEntity;
import com.apiculture.simulator.domain.game.CargoFreightRules;
import com.apiculture.simulator.domain.game.ExoticHoneyRules;
import com.apiculture.simulator.domain.game.FleetRules;
import com.apiculture.simulator.domain.game.CargoTripRules;
import com.apiculture.simulator.domain.game.HiveHoneyRules;
import com.apiculture.simulator.domain.game.HiveHoneyStocks;
import com.apiculture.simulator.domain.game.HoneyOrder;
import com.apiculture.simulator.domain.game.TranshumanceRules;
import com.apiculture.simulator.domain.game.TruckTripRules;
import com.apiculture.simulator.domain.game.XpAwards;
import com.apiculture.simulator.domain.market.HoneyMarketEngine;
import com.apiculture.simulator.domain.map.EncodedPolyline;
import com.apiculture.simulator.domain.map.LocalGraphHopper;
import com.apiculture.simulator.domain.map.PlayableMapRegion;
import com.apiculture.simulator.domain.map.ProvincialMarket;
import com.apiculture.simulator.domain.map.ProvincialMarketCatalog;
import com.apiculture.simulator.domain.map.RoadPath;
import com.apiculture.simulator.domain.map.SeaRoute;
import com.apiculture.simulator.domain.workshop.JarMix;
import com.apiculture.simulator.domain.workshop.WorkshopRules;
import com.apiculture.simulator.domain.map.Seaport;
import com.apiculture.simulator.domain.map.SeaportCatalog;
import com.apiculture.simulator.domain.parcel.HexApiary;
import com.apiculture.simulator.domain.parcel.HexParcel;
import com.apiculture.simulator.domain.parcel.HexParcelRandomPoint;
import com.apiculture.simulator.domain.parcel.WarehouseRules;
import com.apiculture.simulator.presentation.common.RouteErrorDialog;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public final class HoneyLogistics {

    public enum Result {
        INSTANT,
        STARTED,
        TOO_SLOW,
        NO_CASH,
        NO_DEMAND,
        NO_FLEET,
        FAILED
    }

    public static final class CollectPreview {
        public final String hiveId;
        public final String hiveLabel;
        public final String warehouseHexId;
        public final String warehouseLabel;
        public final Map<String, Double> cargo;
        public final double kg;
        public final long roundTripMs;
        public final boolean instantToday;
        @Nullable
        public final HexParcel warehouse;
        /** Camión que hace el viaje. Vacío si la miel entra al momento. */
        @Nullable
        public final String truckId;
        public final String truckLabel;
        /** Kilómetros de ida, del almacén del camión hasta los apiarios. */
        public final double distanceKm;
        /** Nivel del camión. 0 si la miel entra al momento. */
        public final int truckLevel;
        /** Colmenas que se quedan si sale solo este camión. */
        public final int leftBehind;
        /** Había miel, pero ningún camión estaba libre. */
        public final boolean noFleet;
        /** Había camión, pero el almacén de destino no tiene sitio. */
        public final boolean warehouseFull;
        /** Kilos que salen de cada colmena. */
        @NonNull
        public final Map<String, Map<String, Double>> cargoByHive;
        /** Paradas del mismo camión, en orden. La primera es el destino inicial. */
        @NonNull
        public final List<CollectStop> stops;

        public CollectPreview(String hiveId, String hiveLabel, @Nullable HexParcel warehouse,
                String warehouseLabel, Map<String, Double> cargo, long roundTripMs, boolean instantToday) {
            this(hiveId, hiveLabel, warehouse, warehouseLabel, cargo, roundTripMs, instantToday,
                    null, "Camión", 0.0, 0, 0, false, null, null, false);
        }

        public CollectPreview(String hiveId, String hiveLabel, @Nullable HexParcel warehouse,
                String warehouseLabel, Map<String, Double> cargo, long roundTripMs, boolean instantToday,
                @Nullable String truckId, @Nullable String truckLabel, double distanceKm, boolean noFleet,
                @Nullable Map<String, Map<String, Double>> cargoByHive, @Nullable List<CollectStop> stops) {
            this(hiveId, hiveLabel, warehouse, warehouseLabel, cargo, roundTripMs, instantToday,
                    truckId, truckLabel, distanceKm, 0, 0, noFleet, cargoByHive, stops, false);
        }

        public CollectPreview(String hiveId, String hiveLabel, @Nullable HexParcel warehouse,
                String warehouseLabel, Map<String, Double> cargo, long roundTripMs, boolean instantToday,
                @Nullable String truckId, @Nullable String truckLabel, double distanceKm,
                int truckLevel, int leftBehind, boolean noFleet,
                @Nullable Map<String, Map<String, Double>> cargoByHive, @Nullable List<CollectStop> stops,
                boolean warehouseFull) {
            this.hiveId = hiveId;
            this.hiveLabel = hiveLabel != null ? hiveLabel : "Apiario";
            this.warehouse = warehouse;
            this.warehouseHexId = warehouse != null ? warehouse.id : "";
            this.warehouseLabel = warehouseLabel != null ? warehouseLabel : "Obrador";
            this.cargo = cargo == null
                    ? Collections.emptyMap()
                    : Collections.unmodifiableMap(new LinkedHashMap<>(cargo));
            double sum = 0.0;
            for (Double v : this.cargo.values()) {
                if (v != null) {
                    sum += v;
                }
            }
            this.kg = sum;
            this.roundTripMs = Math.max(0L, roundTripMs);
            this.instantToday = instantToday;
            this.truckId = truckId;
            this.truckLabel = truckLabel != null && !truckLabel.isEmpty() ? truckLabel : "Camión";
            this.distanceKm = Math.max(0.0, distanceKm);
            this.truckLevel = Math.max(0, truckLevel);
            this.leftBehind = Math.max(0, leftBehind);
            this.noFleet = noFleet;
            this.warehouseFull = warehouseFull;
            if (cargoByHive != null && !cargoByHive.isEmpty()) {
                this.cargoByHive = Collections.unmodifiableMap(cargoByHive);
            } else if (hiveId != null && !this.cargo.isEmpty()) {
                Map<String, Map<String, Double>> one = new LinkedHashMap<>();
                one.put(hiveId, this.cargo);
                this.cargoByHive = Collections.unmodifiableMap(one);
            } else {
                this.cargoByHive = Collections.emptyMap();
            }
            this.stops = stops == null ? Collections.emptyList() : Collections.unmodifiableList(stops);
        }

        @NonNull
        public static CollectPreview noFleet() {
            return new CollectPreview(null, "", null, "", Collections.emptyMap(), 0L, false,
                    null, "Camión", 0.0, 0, 0, true, null, null, false);
        }

        @NonNull
        public static CollectPreview warehouseFull() {
            return new CollectPreview(null, "", null, "", Collections.emptyMap(), 0L, false,
                    null, "Camión", 0.0, 0, 0, false, null, null, true);
        }
    }

    /** Una parada de recogida en un apiario. */
    public static final class CollectStop {
        public final double lat;
        public final double lng;
        public final String label;
        public final String hexId;
        /** Tipo principal. Nulo si la parada no carga miel. */
        @Nullable
        public final String floraKey;
        /** Kilos de cada tipo que se cargan en esta parada. */
        @NonNull
        public final Map<String, Double> load;

        public CollectStop(double lat, double lng, @Nullable String label, @Nullable String hexId) {
            this(lat, lng, label, hexId, null, null);
        }

        public CollectStop(double lat, double lng, @Nullable String label, @Nullable String hexId,
                @Nullable String floraKey) {
            this(lat, lng, label, hexId, floraKey, null);
        }

        public CollectStop(double lat, double lng, @Nullable String label, @Nullable String hexId,
                @Nullable String floraKey, @Nullable Map<String, Double> load) {
            this.lat = lat;
            this.lng = lng;
            this.label = label != null ? label : "Apiario";
            this.hexId = hexId != null ? hexId : "";
            this.load = freezeLoad(load);
            if ((floraKey == null || floraKey.isEmpty()) && !this.load.isEmpty()) {
                floraKey = this.load.keySet().iterator().next();
            }
            this.floraKey = floraKey != null && !floraKey.isEmpty() ? floraKey : null;
        }
    }

    /** Un tramo de una recogida con varias paradas, almacén incluido al final. */
    public static final class TourLeg {
        public final String fromLabel;
        public final String toLabel;
        public final boolean fromWarehouse;
        public final boolean toWarehouse;
        public final long durationMs;
        public final boolean current;
        public final boolean done;
        @Nullable
        public final String polyline;
        @Nullable
        public final String roadKinds;
        public final double fromLat;
        public final double fromLng;
        public final double toLat;
        public final double toLng;
        /** Tipo principal cargado en el destino. Nulo en la vuelta al almacén. */
        @Nullable
        public final String loadFlora;
        /** Kilos de cada tipo que se cargan al llegar a este destino. */
        @NonNull
        public final Map<String, Double> load;

        TourLeg(String fromLabel, String toLabel, boolean fromWarehouse, boolean toWarehouse,
                long durationMs, boolean current, boolean done, @Nullable String polyline,
                @Nullable String roadKinds, double fromLat, double fromLng, double toLat, double toLng,
                @Nullable String loadFlora, @Nullable Map<String, Double> load) {
            this.fromLabel = fromLabel;
            this.toLabel = toLabel;
            this.fromWarehouse = fromWarehouse;
            this.toWarehouse = toWarehouse;
            this.durationMs = durationMs;
            this.current = current;
            this.done = done;
            this.polyline = polyline;
            this.roadKinds = roadKinds;
            this.fromLat = fromLat;
            this.fromLng = fromLng;
            this.toLat = toLat;
            this.toLng = toLng;
            this.load = freezeLoad(load);
            if ((loadFlora == null || loadFlora.isEmpty()) && !this.load.isEmpty()) {
                loadFlora = this.load.keySet().iterator().next();
            }
            this.loadFlora = loadFlora != null && !loadFlora.isEmpty() ? loadFlora : null;
        }
    }

    /** Tramo todavía por recorrer, para dibujarlo en el mapa. */
    public static final class DrawnLeg {
        @NonNull
        public final List<double[]> points;
        @Nullable
        public final String roadKinds;

        DrawnLeg(@NonNull List<double[]> points, @Nullable String roadKinds) {
            this.points = points;
            this.roadKinds = roadKinds;
        }
    }

    public static final class HarvestPlan {
        @NonNull
        public final List<CollectPreview> trips;
        public final int skippedNoWarehouse;
        /** Colmenas con miel que no entran en los camiones libres. */
        public final int leftBehind;
        public final boolean noFleet;
        /** Hay camión libre, pero el almacén no admite más miel. */
        public final boolean warehouseFull;

        public HarvestPlan(@Nullable List<CollectPreview> trips, int skippedNoWarehouse) {
            this(trips, skippedNoWarehouse, 0, false);
        }

        public HarvestPlan(@Nullable List<CollectPreview> trips, int skippedNoWarehouse,
                int leftBehind, boolean noFleet) {
            this(trips, skippedNoWarehouse, leftBehind, noFleet, false);
        }

        public HarvestPlan(@Nullable List<CollectPreview> trips, int skippedNoWarehouse,
                int leftBehind, boolean noFleet, boolean warehouseFull) {
            this.trips = trips == null ? Collections.emptyList() : Collections.unmodifiableList(trips);
            this.skippedNoWarehouse = Math.max(0, skippedNoWarehouse);
            this.leftBehind = Math.max(0, leftBehind);
            this.noFleet = noFleet;
            this.warehouseFull = warehouseFull;
        }
    }

    public static final class HarvestCommit {
        public final boolean success;
        public final double totalKg;
        public final int hiveCount;
        /** El camión ya salió: el resumen se muestra al llegar, no al enviarlo. */
        public final boolean deferred;
        @NonNull
        public final Map<String, Double> kgByFlora;

        public HarvestCommit(boolean success, double totalKg, int hiveCount,
                @Nullable Map<String, Double> kgByFlora) {
            this(success, totalKg, hiveCount, kgByFlora, false);
        }

        public HarvestCommit(boolean success, double totalKg, int hiveCount,
                @Nullable Map<String, Double> kgByFlora, boolean deferred) {
            this.success = success;
            this.totalKg = totalKg;
            this.hiveCount = hiveCount;
            this.deferred = deferred;
            this.kgByFlora = kgByFlora == null
                    ? Collections.emptyMap()
                    : Collections.unmodifiableMap(new LinkedHashMap<>(kgByFlora));
        }
    }

    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private static boolean liveTrips(@NonNull Context context) {
        return TruckLivePrefs.isEnabled(context) || GameServer.enabled();
    }

    private HoneyLogistics() {
    }

    @NonNull
    public static LiveData<List<CargoTripEntity>> observe(@NonNull Context context) {
        MediatorLiveData<List<CargoTripEntity>> live = new MediatorLiveData<>();
        live.addSource(dao(context).observeAll(), rows -> IO.execute(() -> {
            // El reloj del servidor manda fase y horario. Si la vuelta quedó en
            // línea recta, el teléfono solo corrige el trazado, no el reloj.
            List<CargoTripEntity> source = rows;
            if (!GameServer.enabled()) {
                if (compressSeaGapsBlocking(context, rows)) {
                    return;
                }
                repairCollectTripsBlocking(context, rows);
            } else {
                repairCargoGeometryBlocking(context, rows);
            }
            List<CargoTripEntity> fresh = dao(context).getAllSync();
            List<CargoTripEntity> publish = fresh != null ? fresh : source;
            MAIN.post(() -> live.setValue(publish != null ? publish : Collections.emptyList()));
        }));
        return live;
    }

    /**
     * Viajes de barco ya guardados con la duración del océano entero. Se queda el tiempo
     * del agua que se ve en el mapa de salida y en el de llegada, y el camión de destino
     * sale para coincidir con esa llegada.
     */
    private static boolean compressSeaGapsBlocking(@NonNull Context context,
            @Nullable List<CargoTripEntity> rows) {
        if (rows == null || rows.isEmpty()) {
            return false;
        }
        CargoTripDao dao = dao(context);
        boolean changed = false;
        long now = System.currentTimeMillis();
        for (CargoTripEntity trip : rows) {
            if (trip == null || !CargoTripEntity.LEG_HAUL_SHIP.equals(trip.legRole)) {
                continue;
            }
            if ("gap".equals(trip.routeRoadKinds)) {
                continue;
            }
            List<double[]> pts = CargoTripRules.routePoints(trip);
            double full = SeaRoute.pathKm(pts);
            Seaport from = SeaportCatalog.byId(trip.originHexId);
            Seaport to = SeaportCatalog.byId(trip.destHexId);
            PlayableMapRegion origin = from != null ? from.region : null;
            PlayableMapRegion dest = to != null ? to.region : null;
            double visible = SeaRoute.visibleKm(pts, origin, dest);
            if (full <= 1.0 || trip.durationMs <= 0) {
                trip.routeRoadKinds = "gap";
                dao.upsert(trip);
                changed = true;
                continue;
            }
            double hours = trip.durationMs / 3_600_000.0;
            double implied = full / hours;
            if (visible + 5.0 >= full || implied > 220.0) {
                trip.routeRoadKinds = "gap";
                dao.upsert(trip);
                changed = true;
                continue;
            }
            double done = Math.max(0.0, Math.min(1.0, (now - trip.startEpochMs) / (double) trip.durationMs));
            double seen = SeaRoute.visibleKmBefore(pts, done, origin, dest);
            double speed = implied;
            long duration = FleetRules.durationMs(visible, speed);
            long elapsed = FleetRules.durationMs(Math.min(seen, visible), speed);
            trip.startEpochMs = now - elapsed;
            trip.durationMs = Math.max(1L, duration);
            trip.routeRoadKinds = "gap";
            dao.upsert(trip);
            long shipEnd = trip.startEpochMs + trip.durationMs;
            for (CargoTripEntity other : rows) {
                if (other == null || other == trip || other.shipmentId == null
                        || !other.shipmentId.equals(trip.shipmentId)
                        || !CargoTripEntity.LEG_PICKUP.equals(other.legRole)) {
                    continue;
                }
                other.startEpochMs = shipEnd - Math.max(0L, other.durationMs);
                dao.upsert(other);
            }
            changed = true;
        }
        return changed;
    }

    public static void removeAllForOwnerBlocking(@NonNull Context context, @Nullable String ownerId) {
        if (ownerId == null || ownerId.isEmpty()) {
            return;
        }
        CargoTripDao trips = dao(context);
        List<CargoTripEntity> rows = trips.getAllSync();
        if (rows != null) {
            for (CargoTripEntity trip : rows) {
                if (trip == null || !ownerId.equals(trip.ownerId)) {
                    continue;
                }
                if (CargoTripEntity.KIND_ORDER.equals(trip.kind) && trip.orderId != null) {
                    HoneyOrderStore.release(context, trip.orderId);
                }
            }
        }
        trips.deleteAllForOwner(ownerId);
    }

    public static void collectFromHive(@NonNull Context context, @Nullable String ownerId,
            @NonNull HiveEntity hive, @NonNull Map<String, Double> kgByFlora,
            @NonNull EconomyRepository economy, @Nullable Consumer<Result> done) {
        collectFromHive(context, ownerId, hive, kgByFlora, economy, null, done);
    }

    public static void collectFromHive(@NonNull Context context, @Nullable String ownerId,
            @NonNull HiveEntity hive, @NonNull Map<String, Double> kgByFlora,
            @NonNull EconomyRepository economy, @Nullable HexParcel warehouse,
            @Nullable Consumer<Result> done) {
        collectFromHive(context, ownerId, hive, kgByFlora, economy, warehouse, null, done);
    }

    public static void collectFromHive(@NonNull Context context, @Nullable String ownerId,
            @NonNull HiveEntity hive, @NonNull Map<String, Double> kgByFlora,
            @NonNull EconomyRepository economy, @Nullable HexParcel warehouse,
            @Nullable String truckId, @Nullable Consumer<Result> done) {
        Context app = context.getApplicationContext();
        IO.execute(() -> {
            Result r = collectFromHiveNow(app, ownerId, hive, cleanCargo(kgByFlora), economy, warehouse, truckId);
            if (done != null) {
                MAIN.post(() -> done.accept(r));
            }
        });
    }

    @NonNull
    public static Result collectFromHive(@NonNull Context context, @Nullable String ownerId,
            @NonNull HiveEntity hive, @NonNull Map<String, Double> kgByFlora,
            @NonNull EconomyRepository economy) {
        return offMain(() -> collectFromHiveNow(context, ownerId, hive, kgByFlora, economy), Result.FAILED);
    }

    @NonNull
    private static Result collectFromHiveNow(@NonNull Context context, @Nullable String ownerId,
            @NonNull HiveEntity hive, @NonNull Map<String, Double> kgByFlora,
            @NonNull EconomyRepository economy) {
        Map<String, Double> cargo = cleanCargo(kgByFlora);
        if (cargo.isEmpty()) {
            return Result.FAILED;
        }
        double[] dest = hiveCollectPoint(context, hive);
        HexParcel warehouse = warehouseParcelNear(context, ownerId, dest[0], dest[1], hive.hexId);
        return collectFromHiveNow(context, ownerId, hive, cargo, economy, warehouse);
    }

    @NonNull
    public static Result collectFromHive(@NonNull Context context, @Nullable String ownerId,
            @NonNull HiveEntity hive, @NonNull Map<String, Double> kgByFlora,
            @NonNull EconomyRepository economy, @Nullable HexParcel warehouse) {
        return offMain(() -> collectFromHiveNow(context, ownerId, hive, cleanCargo(kgByFlora),
                economy, warehouse), Result.FAILED);
    }

    @NonNull
    private static Result collectFromHiveNow(@NonNull Context context, @Nullable String ownerId,
            @NonNull HiveEntity hive, @NonNull Map<String, Double> cargo,
            @NonNull EconomyRepository economy, @Nullable HexParcel warehouse) {
        return collectFromHiveNow(context, ownerId, hive, cargo, economy, warehouse, null);
    }

    @NonNull
    private static Result collectFromHiveNow(@NonNull Context context, @Nullable String ownerId,
            @NonNull HiveEntity hive, @NonNull Map<String, Double> cargo,
            @NonNull EconomyRepository economy, @Nullable HexParcel warehouse,
            @Nullable String truckId) {
        if (cargo.isEmpty()) {
            return Result.FAILED;
        }
        double[] dest = hiveCollectPoint(context, hive);
        if (warehouse == null) {
            warehouse = warehouseParcelNear(context, ownerId, dest[0], dest[1], hive.hexId);
        }
        if (warehouse == null) {
            return Result.FAILED;
        }
        if (!liveTrips(context) && !GameServer.enabled()) {
            creditHarvest(context, ownerId, warehouse.id, economy, cargo, hiveCollectLabel(context, hive));
            return Result.INSTANT;
        }
        FleetStore.Vehicle truck;
        if (truckId != null) {
            truck = truckFree(context, ownerId, truckId)
                    ? FleetStore.vehicle(context, ownerId, truckId) : null;
        } else {
            truck = freeTruckAt(context, ownerId, warehouse.id, totalKg(cargo));
        }
        if (truck == null) {
            return Result.NO_FLEET;
        }
        String destName = hiveCollectLabel(context, hive);
        List<CollectStop> stops = new ArrayList<>();
        stops.add(new CollectStop(dest[0], dest[1], destName, hive.hexId));
        return startCollectTour(context, ownerId, truck, warehouse, cargo, hive.id, stops, 1);
    }

    @Nullable
    public static CollectPreview planCollect(@NonNull Context context, @Nullable String ownerId,
            @NonNull HiveEntity hive, @NonNull Map<String, Double> kgByFlora) {
        Map<String, Double> cargo = cleanCargo(kgByFlora);
        if (cargo.isEmpty()) {
            return null;
        }
        double[] dest = hiveCollectPoint(context, hive);
        String destName = hiveCollectLabel(context, hive);
        if (!liveTrips(context)) {
            HexParcel warehouse = warehouseParcelNear(context, ownerId, dest[0], dest[1], hive.hexId);
            if (warehouse == null) {
                return null;
            }
            long roundMs = estimateRoundTripMs(context, ownerId, warehouse, dest[0], dest[1]);
            Map<String, Double> fit = trimCargo(cargo, warehouseFreeKg(context, ownerId, warehouse));
            if (fit.isEmpty()) {
                return CollectPreview.warehouseFull();
            }
            return new CollectPreview(hive.id, destName, warehouse,
                    warehouseLabel(context, ownerId, warehouse), fit, roundMs, false);
        }
        FleetStore.Vehicle truck = null;
        HexParcel warehouse = null;
        Map<String, Double> fit = null;
        double bestKm = Double.MAX_VALUE;
        boolean sawTruck = false;
        for (FleetStore.Vehicle candidate : freeTrucks(context, ownerId)) {
            sawTruck = true;
            HexParcel home = parcelFor(context, ownerId, candidate.homeId);
            if (home == null) {
                continue;
            }
            double limit = Math.min(FleetRules.honeyKg(FleetRules.Kind.TRUCK, candidate.level),
                    warehouseFreeKg(context, ownerId, home));
            Map<String, Double> slice = trimCargo(cargo, limit);
            if (slice.isEmpty()) {
                continue;
            }
            double[] dock = warehouseDock(context, ownerId, home);
            double km = TranshumanceRules.haversineKm(dock[0], dock[1], dest[0], dest[1]);
            if (km < bestKm) {
                bestKm = km;
                truck = candidate;
                warehouse = home;
                fit = slice;
            }
        }
        if (truck == null || warehouse == null || fit == null) {
            return sawTruck ? CollectPreview.warehouseFull() : CollectPreview.noFleet();
        }
        List<CollectStop> stops = new ArrayList<>();
        stops.add(new CollectStop(dest[0], dest[1], destName, hive.hexId));
        RoadPath path = roadLeg(context, warehouseDock(context, ownerId, warehouse), dest[0], dest[1]);
        double km = path.distanceKm;
        long roundMs = FleetRules.durationMs(km, FleetRules.speedKmh(FleetRules.Kind.TRUCK, truck.level)) * 2L;
        Map<String, Map<String, Double>> byHive = new LinkedHashMap<>();
        byHive.put(hive.id, fit);
        return new CollectPreview(hive.id, destName, warehouse,
                warehouseLabel(context, ownerId, warehouse), fit, roundMs, false,
                truck.id, truckName(truck), km, truck.level, 0, false, byHive, stops, false);
    }

    public static void planCollect(@NonNull Context context, @Nullable String ownerId,
            @NonNull HiveEntity hive, @NonNull Map<String, Double> kgByFlora,
            @Nullable Consumer<CollectPreview> done) {
        Context app = context.getApplicationContext();
        IO.execute(() -> {
            CollectPreview preview = planCollect(app, ownerId, hive, kgByFlora);
            if (done != null) {
                MAIN.post(() -> done.accept(preview));
            }
        });
    }

    /** Un viaje posible por cada camión libre que cabe la carga. */
    public static void planCollectOptions(@NonNull Context context, @Nullable String ownerId,
            @NonNull HiveEntity hive, @NonNull Map<String, Double> kgByFlora,
            @Nullable Consumer<List<CollectPreview>> done) {
        Context app = context.getApplicationContext();
        IO.execute(() -> {
            List<CollectPreview> options = collectOptions(app, ownerId, hive, kgByFlora);
            if (done != null) {
                MAIN.post(() -> done.accept(options));
            }
        });
    }

    @NonNull
    private static List<CollectPreview> collectOptions(@NonNull Context context, @Nullable String ownerId,
            @NonNull HiveEntity hive, @NonNull Map<String, Double> kgByFlora) {
        List<CollectPreview> options = new ArrayList<>();
        Map<String, Double> cargo = cleanCargo(kgByFlora);
        if (cargo.isEmpty()) {
            return options;
        }
        if (!liveTrips(context)) {
            CollectPreview one = planCollect(context, ownerId, hive, cargo);
            if (one != null) {
                options.add(one);
            }
            return options;
        }
        double[] dest = hiveCollectPoint(context, hive);
        String destName = hiveCollectLabel(context, hive);
        boolean sawTruck = false;
        boolean sawRoom = false;
        for (FleetStore.Vehicle truck : freeTrucks(context, ownerId)) {
            sawTruck = true;
            HexParcel warehouse = parcelFor(context, ownerId, truck.homeId);
            if (warehouse == null) {
                continue;
            }
            double limit = Math.min(FleetRules.honeyKg(FleetRules.Kind.TRUCK, truck.level),
                    warehouseFreeKg(context, ownerId, warehouse));
            Map<String, Double> slice = trimCargo(cargo, limit);
            if (slice.isEmpty()) {
                continue;
            }
            sawRoom = true;
            List<CollectStop> stops = new ArrayList<>();
            stops.add(new CollectStop(dest[0], dest[1], destName, hive.hexId));
            RoadPath path = roadLeg(context, warehouseDock(context, ownerId, warehouse), dest[0], dest[1]);
            double km = path.distanceKm;
            long roundMs = FleetRules.durationMs(km, FleetRules.speedKmh(FleetRules.Kind.TRUCK, truck.level)) * 2L;
            Map<String, Map<String, Double>> byHive = new LinkedHashMap<>();
            byHive.put(hive.id, slice);
            options.add(new CollectPreview(hive.id, destName, warehouse,
                    warehouseLabel(context, ownerId, warehouse), slice, roundMs, false,
                    truck.id, truckName(truck), km, truck.level, 0, false, byHive, stops, false));
        }
        options.sort((a, b) -> Double.compare(a.distanceKm, b.distanceKm));
        if (options.isEmpty()) {
            options.add(sawTruck && !sawRoom ? CollectPreview.warehouseFull() : CollectPreview.noFleet());
        }
        return options;
    }

    /** Apiario con miel por encima de la reserva, para elegir desde el inicio. */
    public static final class ApiaryHarvest {
        public final PlayableMapRegion region;
        public final String hexId;
        public final String siteId;
        public final String label;
        public final double kg;
        public final double lat;
        public final double lng;

        ApiaryHarvest(PlayableMapRegion region, String hexId, String siteId, String label,
                double kg, double lat, double lng) {
            this.region = region != null ? region : PlayableMapRegion.IBERIA;
            this.hexId = hexId != null ? hexId : "";
            this.siteId = siteId != null ? siteId : "";
            this.label = label != null && !label.isEmpty() ? label : "Apiario";
            this.kg = kg;
            this.lat = lat;
            this.lng = lng;
        }
    }

    /** Camión libre del mismo territorio, del más cercano al apiario al más lejano. */
    public static final class HarvestTruck {
        public final String id;
        public final String label;
        public final int level;
        public final double distanceKm;
        public final double capacityKg;

        HarvestTruck(String id, String label, int level, double distanceKm, double capacityKg) {
            this.id = id;
            this.label = label != null ? label : "Camión";
            this.level = level;
            this.distanceKm = distanceKm;
            this.capacityKg = capacityKg;
        }
    }

    public static void loadApiaryHarvests(@NonNull Context context, @Nullable String ownerId,
            @Nullable List<HiveEntity> hives, @Nullable Consumer<List<ApiaryHarvest>> done) {
        Context app = context.getApplicationContext();
        IO.execute(() -> {
            List<ApiaryHarvest> rows = apiaryHarvests(app, hives);
            if (done != null) {
                MAIN.post(() -> done.accept(rows));
            }
        });
    }

    @NonNull
    private static List<ApiaryHarvest> apiaryHarvests(@NonNull Context context,
            @Nullable List<HiveEntity> hives) {
        Map<String, double[]> point = new LinkedHashMap<>();
        Map<String, Double> kg = new LinkedHashMap<>();
        Map<String, String> label = new LinkedHashMap<>();
        if (hives == null) {
            return Collections.emptyList();
        }
        for (HiveEntity hive : hives) {
            if (hive == null || hive.inWarehouse || hive.hexId == null || hive.hexId.isEmpty()) {
                continue;
            }
            HiveHoneyStocks.ensureSeeded(hive);
            Map<String, Double> cargo = canonicalCargo(
                    HiveHoneyStocks.peekHarvestLeaving(hive, HiveHoneyRules.HARVEST_ALL_LEAVE_KG));
            double sum = totalKg(cargo);
            if (sum <= 1e-9) {
                continue;
            }
            List<HexParcelOwnershipEntity> sites = hive.ownerId != null
                    ? AppDatabase.getInstance(context).hexParcelOwnershipDao()
                    .listByHexAndOwnerSync(hive.hexId, hive.ownerId)
                    : Collections.emptyList();
            HexParcel hex = IberiaHexOverlayStore.findById(context, hive.hexId);
            String site = HexApiary.resolveSiteId(hive, hex, sites);
            String key = hive.hexId + "|" + site;
            kg.put(key, kg.getOrDefault(key, 0.0) + sum);
            if (!label.containsKey(key)) {
                label.put(key, hiveCollectLabel(context, hive));
                double[] dest = hiveCollectPoint(context, hive);
                point.put(key, dest);
            }
        }
        List<ApiaryHarvest> out = new ArrayList<>();
        for (Map.Entry<String, Double> e : kg.entrySet()) {
            String key = e.getKey();
            int bar = key.indexOf('|');
            String hex = bar >= 0 ? key.substring(0, bar) : key;
            String site = bar >= 0 ? key.substring(bar + 1) : "";
            double[] dest = point.get(key);
            out.add(new ApiaryHarvest(PlayableMapRegion.fromHexId(hex), hex, site,
                    label.get(key), e.getValue(),
                    dest != null ? dest[0] : 0.0, dest != null ? dest[1] : 0.0));
        }
        out.sort((a, b) -> Double.compare(b.kg, a.kg));
        return out;
    }

    public static void trucksForApiary(@NonNull Context context, @Nullable String ownerId,
            @NonNull ApiaryHarvest apiary, @Nullable Consumer<List<HarvestTruck>> done) {
        Context app = context.getApplicationContext();
        IO.execute(() -> {
            List<HarvestTruck> rows = trucksNear(app, ownerId, apiary);
            if (done != null) {
                MAIN.post(() -> done.accept(rows));
            }
        });
    }

    @NonNull
    private static List<HarvestTruck> trucksNear(@NonNull Context context, @Nullable String ownerId,
            @NonNull ApiaryHarvest apiary) {
        List<HarvestTruck> out = new ArrayList<>();
        for (FleetStore.Vehicle truck : freeTrucks(context, ownerId)) {
            HexParcel home = parcelFor(context, ownerId, truck.homeId);
            if (home == null || PlayableMapRegion.fromHexId(home.id) != apiary.region) {
                continue;
            }
            double[] dock = warehouseDock(context, ownerId, home);
            double km = TranshumanceRules.haversineKm(dock[0], dock[1], apiary.lat, apiary.lng);
            double capacity = FleetRules.honeyKg(FleetRules.Kind.TRUCK, truck.level);
            out.add(new HarvestTruck(truck.id, truckName(truck), truck.level, km, capacity));
        }
        out.sort((a, b) -> Double.compare(a.distanceKm, b.distanceKm));
        return out;
    }

    public static void planApiaryHarvest(@NonNull Context context, @Nullable String ownerId,
            @Nullable List<HiveEntity> hives, @NonNull ApiaryHarvest apiary, @NonNull String truckId,
            @Nullable Consumer<CollectPreview> done) {
        Context app = context.getApplicationContext();
        IO.execute(() -> {
            CollectPreview preview = planApiary(app, ownerId, hives, apiary, truckId);
            if (done != null) {
                MAIN.post(() -> done.accept(preview));
            }
        });
    }

    @Nullable
    private static CollectPreview planApiary(@NonNull Context context, @Nullable String ownerId,
            @Nullable List<HiveEntity> hives, @NonNull ApiaryHarvest apiary, @NonNull String truckId) {
        FleetStore.Vehicle truck = truckFree(context, ownerId, truckId)
                ? FleetStore.vehicle(context, ownerId, truckId) : null;
        if (truck == null || hives == null) {
            return CollectPreview.noFleet();
        }
        HexParcel warehouse = parcelFor(context, ownerId, truck.homeId);
        if (warehouse == null || PlayableMapRegion.fromHexId(warehouse.id) != apiary.region) {
            return CollectPreview.noFleet();
        }
        double room = Math.min(FleetRules.honeyKg(FleetRules.Kind.TRUCK, truck.level),
                warehouseFreeKg(context, ownerId, warehouse));
        Map<String, Map<String, Double>> byHive = new LinkedHashMap<>();
        Map<String, Double> loaded = new LinkedHashMap<>();
        List<CollectStop> stops = new ArrayList<>();
        int left = 0;
        for (HiveEntity hive : hives) {
            if (hive == null || hive.inWarehouse || !apiary.hexId.equals(hive.hexId)) {
                continue;
            }
            List<HexParcelOwnershipEntity> sites = hive.ownerId != null
                    ? AppDatabase.getInstance(context).hexParcelOwnershipDao()
                    .listByHexAndOwnerSync(hive.hexId, hive.ownerId)
                    : Collections.emptyList();
            HexParcel hex = IberiaHexOverlayStore.findById(context, hive.hexId);
            if (!apiary.siteId.equals(HexApiary.resolveSiteId(hive, hex, sites))) {
                continue;
            }
            HiveHoneyStocks.ensureSeeded(hive);
            Map<String, Double> cargo = canonicalCargo(
                    HiveHoneyStocks.peekHarvestLeaving(hive, HiveHoneyRules.HARVEST_ALL_LEAVE_KG));
            if (cargo.isEmpty()) {
                continue;
            }
            if (room <= 1e-6) {
                left++;
                continue;
            }
            Map<String, Double> slice = trimCargo(cargo, room);
            if (slice.isEmpty()) {
                left++;
                continue;
            }
            room -= totalKg(slice);
            byHive.put(hive.id, slice);
            for (Map.Entry<String, Double> flora : slice.entrySet()) {
                loaded.put(flora.getKey(), loaded.getOrDefault(flora.getKey(), 0.0) + flora.getValue());
            }
        }
        if (loaded.isEmpty()) {
            return room <= 1e-6 ? CollectPreview.warehouseFull() : null;
        }
        stops.add(new CollectStop(apiary.lat, apiary.lng, apiary.label, apiary.hexId, null, loaded));
        double[] dock = warehouseDock(context, ownerId, warehouse);
        double km = TranshumanceRules.haversineKm(dock[0], dock[1], apiary.lat, apiary.lng);
        long roundMs = FleetRules.durationMs(km, FleetRules.speedKmh(FleetRules.Kind.TRUCK, truck.level)) * 2L;
        return new CollectPreview(apiary.hexId, apiary.label, warehouse,
                warehouseLabel(context, ownerId, warehouse), loaded, roundMs, false,
                truck.id, truckName(truck), km, truck.level, left, false, byHive, stops, false);
    }

    private static boolean sameTerritory(@Nullable String truckHex, @Nullable String destHex) {
        if (truckHex == null || destHex == null || destHex.isEmpty()) {
            return false;
        }
        return PlayableMapRegion.fromHexId(truckHex) == PlayableMapRegion.fromHexId(destHex);
    }

    @NonNull
    public static HarvestPlan previewHarvestAll(@NonNull Context context, @Nullable String ownerId,
            @Nullable List<HiveEntity> hives) {
        List<CollectPreview> trips = new ArrayList<>();
        int skipped = 0;
        if (hives == null) {
            return new HarvestPlan(trips, 0);
        }
        List<DraftStop> pending = new ArrayList<>();
        for (HiveEntity h : hives) {
            if (h == null || h.inWarehouse) {
                continue;
            }
            HiveHoneyStocks.ensureSeeded(h);
            Map<String, Double> cargo = canonicalCargo(
                    HiveHoneyStocks.peekHarvestLeaving(h, HiveHoneyRules.HARVEST_ALL_LEAVE_KG));
            if (cargo.isEmpty()) {
                continue;
            }
            if (h.hexId == null || h.hexId.isEmpty()) {
                skipped++;
                continue;
            }
            DraftStop stop = null;
            String key = apiaryKey(context, h);
            for (DraftStop existing : pending) {
                if (key.equals(existing.key)) {
                    stop = existing;
                    break;
                }
            }
            if (stop == null) {
                double[] dest = hiveCollectPoint(context, h);
                stop = new DraftStop(apiaryKey(context, h), h.hexId, dest[0], dest[1],
                        hiveCollectLabel(context, h));
                pending.add(stop);
            }
            stop.add(h.id, cargo);
        }
        if (!liveTrips(context)) {
            for (DraftStop stop : pending) {
                for (Map.Entry<String, Map<String, Double>> hiveCargo : stop.byHive.entrySet()) {
                    HiveEntity hive = hiveOf(hives, hiveCargo.getKey());
                    if (hive == null) {
                        skipped++;
                        continue;
                    }
                    CollectPreview preview = planCollect(context, ownerId, hive, hiveCargo.getValue());
                    if (preview == null || preview.noFleet || preview.warehouseFull || preview.cargo.isEmpty()) {
                        skipped++;
                        continue;
                    }
                    trips.add(preview);
                }
            }
            return new HarvestPlan(trips, skipped);
        }
        List<FleetStore.Vehicle> trucks = freeTrucks(context, ownerId);
        if (trucks.isEmpty()) {
            int waiting = 0;
            for (DraftStop stop : pending) {
                waiting += stop.byHive.size();
            }
            return new HarvestPlan(trips, skipped, waiting, waiting > 0);
        }
        int honeyHives = 0;
        for (DraftStop stop : pending) {
            honeyHives += stop.byHive.size();
        }
        double room = 0.0;
        for (FleetStore.Vehicle truck : trucks) {
            HexParcel home = parcelFor(context, ownerId, truck.homeId);
            room += warehouseFreeKg(context, ownerId, home);
        }
        boolean warehouseFull = room <= 1e-6;
        trips.addAll(assignCollectFleet(context, ownerId, pending, trucks));
        int left = 0;
        for (DraftStop stop : pending) {
            left += stop.byHive.size();
        }
        boolean noFleet = trips.isEmpty() && honeyHives > 0 && !warehouseFull;
        return new HarvestPlan(trips, skipped, left, noFleet, warehouseFull && trips.isEmpty() && honeyHives > 0);
    }

    public static void previewHarvestAll(@NonNull Context context, @Nullable String ownerId,
            @Nullable List<HiveEntity> hives, @Nullable Consumer<HarvestPlan> done) {
        Context app = context.getApplicationContext();
        IO.execute(() -> {
            HarvestPlan plan = previewHarvestAll(app, ownerId, hives);
            if (done != null) {
                MAIN.post(() -> done.accept(plan));
            }
        });
    }

    public static void commitHarvest(@NonNull Context context, @Nullable String ownerId,
            @NonNull HiveRepository hiveRepository, @NonNull EconomyRepository economy,
            @Nullable List<CollectPreview> trips, @Nullable Consumer<HarvestCommit> done) {
        Context app = context.getApplicationContext();
        IO.execute(() -> {
            HarvestCommit result = commitHarvestBlocking(app, ownerId, hiveRepository, economy, trips);
            if (done != null) {
                MAIN.post(() -> done.accept(result));
            }
        });
    }

    @NonNull
    public static HarvestCommit commitHarvestBlocking(@NonNull Context context, @Nullable String ownerId,
            @NonNull HiveRepository hiveRepository, @NonNull EconomyRepository economy,
            @Nullable List<CollectPreview> trips) {
        List<HiveEntity> list = hiveRepository.getLocalHivesSync(ownerId);
        Map<String, HiveEntity> byId = new LinkedHashMap<>();
        if (list != null) {
            for (HiveEntity h : list) {
                if (h != null && h.id != null) {
                    byId.put(h.id, h);
                }
            }
        }
        double totalKg = 0.0;
        int hiveCount = 0;
        boolean deferred = false;
        Map<String, Double> byFlora = new LinkedHashMap<>();
        if (trips != null) {
            for (CollectPreview preview : trips) {
                if (preview == null || preview.noFleet || preview.cargoByHive.isEmpty()) {
                    continue;
                }
                if (liveTrips(context)
                        && !truckFree(context, ownerId, preview.truckId)) {
                    continue;
                }
                Map<String, Double> loaded = new LinkedHashMap<>();
                int hivesOnTrip = 0;
                for (Map.Entry<String, Map<String, Double>> hiveCargo : preview.cargoByHive.entrySet()) {
                    HiveEntity h = byId.get(hiveCargo.getKey());
                    if (h == null) {
                        continue;
                    }
                    HiveHoneyStocks.ensureSeeded(h);
                    double harvested = 0.0;
                    for (Map.Entry<String, Double> flora : hiveCargo.getValue().entrySet()) {
                        double got = HiveHoneyStocks.harvestType(h, flora.getKey(),
                                flora.getValue() == null ? 0.0 : flora.getValue());
                        if (got <= 1e-9) {
                            continue;
                        }
                        Double prev = loaded.get(flora.getKey());
                        loaded.put(flora.getKey(), (prev == null ? 0.0 : prev) + got);
                        Double prevAll = byFlora.get(flora.getKey());
                        byFlora.put(flora.getKey(), (prevAll == null ? 0.0 : prevAll) + got);
                        harvested += got;
                    }
                    if (harvested > 1e-9) {
                        hiveRepository.saveHive(h);
                        totalKg += harvested;
                        hiveCount++;
                        hivesOnTrip++;
                    }
                }
                if (loaded.isEmpty() || hivesOnTrip == 0) {
                    continue;
                }
                if (!liveTrips(context) && !GameServer.enabled()) {
                    creditHarvest(context, ownerId, preview.warehouseHexId, economy, loaded,
                            preview.hiveLabel);
                } else if (preview.warehouse != null) {
                    FleetStore.Vehicle truck = FleetStore.vehicle(context, ownerId, preview.truckId);
                    if (truck == null) {
                        truck = freeTruckAt(context, ownerId, preview.warehouse.id, totalKg(loaded));
                    }
                    if (truck == null) {
                        continue;
                    }
                    startCollectTour(context, ownerId, truck, preview.warehouse, loaded,
                            preview.hiveId, preview.stops, hivesOnTrip);
                    deferred = true;
                }
            }
        }
        return new HarvestCommit(totalKg > 1e-9, totalKg, hiveCount, byFlora, deferred);
    }

    @NonNull
    private static Result startCollectTour(@NonNull Context context, @Nullable String ownerId,
            @NonNull FleetStore.Vehicle truck, @NonNull HexParcel warehouse,
            @NonNull Map<String, Double> cargo, @Nullable String hiveId,
            @NonNull List<CollectStop> stops, int hiveCount) {
        if (stops.isEmpty() || cargo.isEmpty()) {
            return Result.FAILED;
        }
        CollectStop first = stops.get(0);
        CargoTripEntity trip = baseTrip(ownerId, CargoTripEntity.KIND_COLLECT, cargo);
        trip.phase = CargoTripEntity.PHASE_OUT;
        trip.hiveId = hiveId;
        trip.vehicleId = truck.id;
        double[] dock = warehouseDock(context, ownerId, warehouse);
        trip.originLabel = warehouseLabel(context, ownerId, warehouse);
        trip.destLabel = first.label;
        trip.originHexId = warehouse.id;
        trip.destHexId = first.hexId;
        trip.returnLat = dock[0];
        trip.returnLng = dock[1];
        trip.returnLabel = trip.originLabel;
        trip.returnHexId = warehouse.id;
        // Cada almacén es un obrador: las alzas vuelven a su recepción.
        boolean workshop = WorkshopRules.REQUIRED_FOR_HARVEST;
        if (stops.size() > 1) {
            try {
                JSONObject o = new JSONObject(trip.cargoJson);
                JSONArray later = new JSONArray();
                for (int i = 1; i < stops.size(); i++) {
                    CollectStop stop = stops.get(i);
                    JSONObject s = new JSONObject();
                    s.put("lat", stop.lat);
                    s.put("lng", stop.lng);
                    s.put("label", stop.label);
                    s.put("hex", stop.hexId);
                    if (stop.floraKey != null) {
                        s.put("flora", stop.floraKey);
                    }
                    if (!stop.load.isEmpty()) {
                        s.put("load", loadJson(stop.load));
                    }
                    later.put(s);
                }
                o.put("_stops", later);
                trip.cargoJson = o.toString();
            } catch (JSONException ignored) {
            }
        }
        RoadPath path = roadLeg(context, dock, first.lat, first.lng);
        maybeRouteWarn(context, path);
        CargoTripRules.applyPath(trip, path, dock[0], dock[1], first.lat, first.lng);
        applyVehicleSpeed(context, trip, path);
        if (stops.size() > 1) {
            stampCollectTour(context, trip, stops, true);
            packCollectTour(trip);
        }
        if (workshop) {
            try {
                JSONObject o = new JSONObject(trip.cargoJson != null ? trip.cargoJson : "{}");
                o.put("_workshop", warehouse.id);
                o.put("_source", first.label);
                trip.cargoJson = o.toString();
            } catch (JSONException ignored) {
            }
        }
        stampHiveCount(trip, hiveCount);
        HarvestReceipts.stage(context, trip.id, cargo, hiveCount);
        saveTrip(context, trip);
        FleetStore.bindCargo(context, ownerId, truck.id, trip.id);
        return Result.STARTED;
    }

    private static boolean advanceCollectStops(@NonNull Context context, @NonNull CargoTripEntity trip) {
        JSONArray later;
        try {
            JSONObject o = new JSONObject(trip.cargoJson != null ? trip.cargoJson : "{}");
            later = o.optJSONArray("_stops");
            if (later == null || later.length() == 0) {
                return false;
            }
            double fromLat = trip.destLat;
            double fromLng = trip.destLng;
            int nextIndex = 0;
            JSONObject next = later.getJSONObject(0);
            while (nextIndex < later.length()) {
                next = later.getJSONObject(nextIndex);
                double hop = TranshumanceRules.haversineKm(
                        fromLat, fromLng, next.optDouble("lat"), next.optDouble("lng"));
                if (hop >= 0.08) {
                    break;
                }
                nextIndex++;
            }
            if (nextIndex >= later.length()) {
                return false;
            }
            JSONArray rest = new JSONArray();
            for (int i = nextIndex + 1; i < later.length(); i++) {
                rest.put(later.get(i));
            }
            o.put("_stops", rest);
            trip.cargoJson = o.toString();
            String fromLabel = trip.destLabel;
            String fromHex = trip.destHexId;
            double toLat = next.optDouble("lat");
            double toLng = next.optDouble("lng");
            trip.originLabel = fromLabel;
            trip.originHexId = fromHex;
            trip.destLabel = next.optString("label", "Apiario");
            trip.destHexId = next.optString("hex", "");
            RoadPath planned = pathFromTour(o, fromLat, fromLng, toLat, toLng);
            RoadPath path = planned != null
                    ? planned
                    : roadLeg(context, new double[] {fromLat, fromLng}, toLat, toLng);
            CargoTripRules.applyPath(trip, path, fromLat, fromLng, toLat, toLng);
            applyVehicleSpeed(context, trip, path);
            trip.phase = CargoTripEntity.PHASE_OUT;
            saveTrip(context, trip);
            return true;
        } catch (JSONException e) {
            return false;
        }
    }

    /** Reparte la miel del jugador en sus almacenes antes de buscar camión. */
    private static void settleHoney(@NonNull Context context, @Nullable String ownerId) {
        if (ownerId == null || ownerId.isEmpty()) {
            return;
        }
        Context app = context.getApplicationContext();
        if (!(app instanceof ApicultureApp)) {
            return;
        }
        List<HexParcelOwnershipEntity> rows = AppDatabase.getInstance(app)
                .hexParcelOwnershipDao().getWarehousesForOwnerSync(ownerId);
        WarehouseHoneyStore.reconcile(app, ((ApicultureApp) app).getEconomyRepository(), ownerId, rows);
    }

    @NonNull
    private static List<FleetStore.Vehicle> freeTrucks(@NonNull Context context, @Nullable String ownerId) {
        FleetStore.releaseIdle(context, ownerId);
        List<FleetStore.Vehicle> out = new ArrayList<>();
        for (FleetStore.Vehicle v : FleetStore.vehicles(context, ownerId)) {
            if (v.isTruck() && !v.honeyBusy() && v.hiveTrips <= 0) {
                out.add(v);
            }
        }
        return out;
    }

    private static boolean truckFree(@NonNull Context context, @Nullable String ownerId,
            @Nullable String truckId) {
        if (truckId == null || truckId.isEmpty()) {
            return false;
        }
        FleetStore.releaseIdle(context, ownerId);
        FleetStore.Vehicle v = FleetStore.vehicle(context, ownerId, truckId);
        return v != null && v.isTruck() && !v.honeyBusy() && v.hiveTrips <= 0;
    }

    @Nullable
    private static FleetStore.Vehicle freeTruckAt(@NonNull Context context, @Nullable String ownerId,
            @Nullable String hexId, double kg) {
        FleetStore.Vehicle best = null;
        for (FleetStore.Vehicle v : freeTrucks(context, ownerId)) {
            if (hexId == null || !hexId.equals(v.homeId)) {
                continue;
            }
            if (FleetRules.honeyKg(FleetRules.Kind.TRUCK, v.level) + 1e-6 < kg) {
                continue;
            }
            if (best == null || v.level > best.level) {
                best = v;
            }
        }
        return best;
    }

    @Nullable
    private static FleetStore.Vehicle nearestFreeTruck(@NonNull Context context, @Nullable String ownerId,
            double lat, double lng, double kg) {
        FleetStore.Vehicle best = null;
        double bestKm = Double.MAX_VALUE;
        for (FleetStore.Vehicle v : freeTrucks(context, ownerId)) {
            if (FleetRules.honeyKg(FleetRules.Kind.TRUCK, v.level) + 1e-6 < kg) {
                continue;
            }
            HexParcel home = IberiaHexOverlayStore.findById(context.getApplicationContext(), v.homeId);
            if (home == null) {
                continue;
            }
            double[] dock = warehouseDock(context, ownerId, home);
            double km = TranshumanceRules.haversineKm(dock[0], dock[1], lat, lng);
            if (km < bestKm) {
                bestKm = km;
                best = v;
            }
        }
        return best;
    }

    @NonNull
    private static String truckName(@NonNull FleetStore.Vehicle truck) {
        if (truck.name != null && !truck.name.trim().isEmpty()) {
            return truck.name.trim();
        }
        return "Camión";
    }

    @NonNull
    private static RoadPath roadLeg(@NonNull Context context, @NonNull double[] from, double toLat, double toLng) {
        RoadPath path = LocalGraphHopper.route(context, from[0], from[1], toLat, toLng);
        if (path == null || path.points.size() < 2 || path.distanceKm <= 0) {
            return RoadPath.geodesic(from[0], from[1], toLat, toLng);
        }
        return path;
    }

    @NonNull
    private static Map<String, Double> canonicalCargo(@NonNull Map<String, Double> taken) {
        Map<String, Double> cargo = new LinkedHashMap<>();
        for (Map.Entry<String, Double> e : taken.entrySet()) {
            String floraKey = HoneyMarketEngine.canonicalFloraKey(e.getKey());
            double kg = e.getValue() == null ? 0.0 : e.getValue();
            if (kg <= 1e-9) {
                continue;
            }
            Double prev = cargo.get(floraKey);
            cargo.put(floraKey, (prev == null ? 0.0 : prev) + kg);
        }
        return cargo;
    }

    @Nullable
    private static String firstHive(@NonNull Map<String, Map<String, Double>> byHive) {
        for (String id : byHive.keySet()) {
            return id;
        }
        return null;
    }

    @Nullable
    private static HiveEntity hiveOf(@Nullable List<HiveEntity> hives, @Nullable String id) {
        if (hives == null || id == null) {
            return null;
        }
        for (HiveEntity h : hives) {
            if (h != null && id.equals(h.id)) {
                return h;
            }
        }
        return null;
    }

    /**
     * Reparte los apiarios entre los camiones libres. Cada uno sale de su almacén
     * y encadena la parada con el tramo de carretera más corto que aún le cabe.
     */
    @NonNull
    private static List<CollectPreview> assignCollectFleet(@NonNull Context context, @Nullable String ownerId,
            @NonNull List<DraftStop> pool, @NonNull List<FleetStore.Vehicle> trucks) {
        List<TruckRun> runs = new ArrayList<>();
        for (FleetStore.Vehicle truck : trucks) {
            HexParcel home = parcelFor(context, ownerId, truck.homeId);
            if (home == null) {
                continue;
            }
            double[] dock = warehouseDock(context, ownerId, home);
            runs.add(new TruckRun(truck, home, dock[0], dock[1]));
        }
        Map<String, Double> warehouseLeft = new LinkedHashMap<>();
        for (TruckRun run : runs) {
            if (!warehouseLeft.containsKey(run.home.id)) {
                warehouseLeft.put(run.home.id, warehouseFreeKg(context, ownerId, run.home));
            }
            run.room = Math.min(run.room, warehouseLeft.get(run.home.id));
        }
        Map<String, RoadPath> roads = new LinkedHashMap<>();
        while (true) {
            TruckRun bestRun = null;
            DraftStop bestStop = null;
            RoadPath bestPath = null;
            double bestKm = Double.MAX_VALUE;
            for (TruckRun run : runs) {
                if (run.room <= 1e-6) {
                    continue;
                }
                PlayableMapRegion homeRegion = PlayableMapRegion.fromHexId(run.home.id);
                for (DraftStop stop : pool) {
                    if (stop.kgFits(run.room) <= 1e-9) {
                        continue;
                    }
                    PlayableMapRegion stopRegion = PlayableMapRegion.fromHexId(stop.hexId);
                    if (homeRegion != null && stopRegion != null && homeRegion != stopRegion) {
                        continue;
                    }
                    RoadPath leg = cachedRoad(roads, context, run.cursorLat, run.cursorLng, stop.lat, stop.lng);
                    if (leg.distanceKm < bestKm) {
                        bestKm = leg.distanceKm;
                        bestRun = run;
                        bestStop = stop;
                        bestPath = leg;
                    }
                }
            }
            if (bestRun == null || bestStop == null || bestPath == null) {
                break;
            }
            Map<String, Map<String, Double>> taken = bestStop.takeUpTo(bestRun.room);
            if (taken.isEmpty()) {
                pool.remove(bestStop);
                continue;
            }
            double takenKg = 0.0;
            for (Map.Entry<String, Map<String, Double>> hiveCargo : taken.entrySet()) {
                bestRun.byHive.put(hiveCargo.getKey(), hiveCargo.getValue());
                for (Map.Entry<String, Double> flora : hiveCargo.getValue().entrySet()) {
                    Double prev = bestRun.cargo.get(flora.getKey());
                    bestRun.cargo.put(flora.getKey(), (prev == null ? 0.0 : prev) + flora.getValue());
                    takenKg += flora.getValue();
                }
            }
            bestRun.room -= takenKg;
            double leftWh = Math.max(0.0, warehouseLeft.get(bestRun.home.id) - takenKg);
            warehouseLeft.put(bestRun.home.id, leftWh);
            for (TruckRun other : runs) {
                if (bestRun.home.id.equals(other.home.id)) {
                    other.room = Math.min(other.room, leftWh);
                }
            }
            bestRun.km += bestPath.distanceKm;
            Map<String, Double> loaded = flattenCargo(taken);
            bestRun.tour.add(new CollectStop(bestStop.lat, bestStop.lng, bestStop.label,
                    bestStop.hexId, dominantFlora(taken), loaded));
            bestRun.cursorLat = bestStop.lat;
            bestRun.cursorLng = bestStop.lng;
            if (bestStop.kg() <= 1e-9) {
                pool.remove(bestStop);
            }
        }
        int left = 0;
        for (DraftStop stop : pool) {
            left += stop.byHive.size();
        }
        List<CollectPreview> trips = new ArrayList<>();
        for (TruckRun run : runs) {
            if (run.tour.isEmpty() || run.cargo.isEmpty()) {
                continue;
            }
            RoadPath back = cachedRoad(roads, context, run.cursorLat, run.cursorLng, run.dockLat, run.dockLng);
            double speed = FleetRules.speedKmh(FleetRules.Kind.TRUCK, run.truck.level);
            long roundMs = FleetRules.durationMs(run.km + back.distanceKm, speed);
            CollectStop first = run.tour.get(0);
            String label = run.tour.size() == 1 ? first.label : run.tour.size() + " apiarios";
            trips.add(new CollectPreview(firstHive(run.byHive), label, run.home,
                    warehouseLabel(context, ownerId, run.home), run.cargo, roundMs, false,
                    run.truck.id, truckName(run.truck), run.km, run.truck.level, left, false,
                    run.byHive, run.tour, false));
        }
        trips.sort((a, b) -> Double.compare(a.distanceKm, b.distanceKm));
        return trips;
    }

    @NonNull
    private static RoadPath cachedRoad(@NonNull Map<String, RoadPath> cache, @NonNull Context context,
            double fromLat, double fromLng, double toLat, double toLng) {
        String key = String.format(Locale.US, "%.5f,%.5f>%.5f,%.5f", fromLat, fromLng, toLat, toLng);
        RoadPath cached = cache.get(key);
        if (cached != null) {
            return cached;
        }
        RoadPath path = roadLeg(context, new double[] {fromLat, fromLng}, toLat, toLng);
        cache.put(key, path);
        return path;
    }

    private static final class TruckRun {
        final FleetStore.Vehicle truck;
        final HexParcel home;
        final double dockLat;
        final double dockLng;
        double room;
        double cursorLat;
        double cursorLng;
        double km;
        final List<CollectStop> tour = new ArrayList<>();
        final Map<String, Map<String, Double>> byHive = new LinkedHashMap<>();
        final Map<String, Double> cargo = new LinkedHashMap<>();

        TruckRun(@NonNull FleetStore.Vehicle truck, @NonNull HexParcel home, double dockLat, double dockLng) {
            this.truck = truck;
            this.home = home;
            this.dockLat = dockLat;
            this.dockLng = dockLng;
            this.room = FleetRules.honeyKg(FleetRules.Kind.TRUCK, truck.level);
            this.cursorLat = dockLat;
            this.cursorLng = dockLng;
        }
    }

    private static final class DraftStop {
        final String key;
        final String hexId;
        final double lat;
        final double lng;
        final String label;
        final Map<String, Map<String, Double>> byHive = new LinkedHashMap<>();

        DraftStop(String key, String hexId, double lat, double lng, String label) {
            this.key = key;
            this.hexId = hexId;
            this.lat = lat;
            this.lng = lng;
            this.label = label;
        }

        void add(String hiveId, Map<String, Double> cargo) {
            byHive.put(hiveId, cargo);
        }

        double kg() {
            double sum = 0.0;
            for (Map<String, Double> cargo : byHive.values()) {
                sum += totalKg(cargo);
            }
            return sum;
        }

        double kgFits(double room) {
            return Math.min(room, kg());
        }

        @NonNull
        Map<String, Map<String, Double>> takeUpTo(double room) {
            Map<String, Map<String, Double>> taken = new LinkedHashMap<>();
            List<String> ids = new ArrayList<>(byHive.keySet());
            for (String id : ids) {
                if (room <= 1e-6) {
                    break;
                }
                Map<String, Double> cargo = byHive.get(id);
                double hiveKg = totalKg(cargo);
                if (hiveKg <= room + 1e-9) {
                    taken.put(id, cargo);
                    byHive.remove(id);
                    room -= hiveKg;
                    continue;
                }
                Map<String, Double> slice = new LinkedHashMap<>();
                Map<String, Double> left = new LinkedHashMap<>();
                double leftRoom = room;
                for (Map.Entry<String, Double> flora : cargo.entrySet()) {
                    double kg = flora.getValue() == null ? 0.0 : flora.getValue();
                    double part = Math.min(kg, Math.max(0.0, leftRoom));
                    if (part > 1e-9) {
                        slice.put(flora.getKey(), part);
                        leftRoom -= part;
                    }
                    if (kg - part > 1e-9) {
                        left.put(flora.getKey(), kg - part);
                    }
                }
                if (!slice.isEmpty()) {
                    taken.put(id, slice);
                    byHive.put(id, left);
                }
                break;
            }
            return taken;
        }
    }

    private static long estimateRoundTripMs(@NonNull Context context, @Nullable String ownerId,
            @NonNull HexParcel warehouse, double destLat, double destLng) {
        double[] dock = warehouseDock(context, ownerId, warehouse);
        RoadPath path = LocalGraphHopper.route(context, dock[0], dock[1], destLat, destLng);
        if (path == null || path.points.size() < 2) {
            path = RoadPath.geodesic(dock[0], dock[1], destLat, destLng);
        }
        return TruckTripRules.durationMs(path) * 2L;
    }

    public static final class WholesalePreview {
        public final String warehouseLabel;
        public final double distanceKm;
        public final long durationMs;
        public final boolean missingWarehouse;
        public final boolean usedGeodesic;
        public final double travelCostB;
        public final String itinerary;
        @Nullable
        public final String blockReason;

        public WholesalePreview(String warehouseLabel, double distanceKm, long durationMs,
                boolean missingWarehouse, boolean usedGeodesic) {
            this(warehouseLabel, distanceKm, durationMs, missingWarehouse, usedGeodesic, 0, "", null);
        }

        public WholesalePreview(String warehouseLabel, double distanceKm, long durationMs,
                boolean missingWarehouse, boolean usedGeodesic, double travelCostB) {
            this(warehouseLabel, distanceKm, durationMs, missingWarehouse, usedGeodesic, travelCostB, "", null);
        }

        public WholesalePreview(String warehouseLabel, double distanceKm, long durationMs,
                boolean missingWarehouse, boolean usedGeodesic, double travelCostB,
                @Nullable String itinerary, @Nullable String blockReason) {
            this.warehouseLabel = warehouseLabel != null ? warehouseLabel : "Obrador";
            this.distanceKm = Math.max(0.0, distanceKm);
            this.durationMs = Math.max(0L, durationMs);
            this.missingWarehouse = missingWarehouse;
            this.usedGeodesic = usedGeodesic;
            this.travelCostB = Math.max(0.0, travelCostB);
            this.itinerary = itinerary != null ? itinerary : "";
            this.blockReason = blockReason;
        }
    }

    public static void previewWholesaleAsync(@NonNull Context context, @Nullable String ownerId,
            @NonNull ProvincialMarket dest, double kg, @NonNull Consumer<WholesalePreview> done) {
        previewWholesaleAsync(context, ownerId, dest, null, kg, done);
    }

    public static void previewWholesaleAsync(@NonNull Context context, @Nullable String ownerId,
            @NonNull ProvincialMarket dest, @Nullable String floraKey, double kg,
            @NonNull Consumer<WholesalePreview> done) {
        previewWholesaleAsync(context, ownerId, dest, floraKey, kg, null, done);
    }

    public static void previewWholesaleAsync(@NonNull Context context, @Nullable String ownerId,
            @NonNull ProvincialMarket dest, @Nullable String floraKey, double kg,
            @Nullable String truckId, @NonNull Consumer<WholesalePreview> done) {
        Context app = context.getApplicationContext();
        IO.execute(() -> {
            WholesalePreview p = FleetDispatch.previewSale(app, ownerId, dest, floraKey, kg, truckId);
            MAIN.post(() -> done.accept(p));
        });
    }

    @NonNull
    private static WholesalePreview previewWholesaleNow(@NonNull Context context, @Nullable String ownerId,
            @NonNull ProvincialMarket dest, @Nullable String floraKey, double kg) {
        return FleetDispatch.previewSale(context, ownerId, dest, floraKey, kg);
    }

    public static void dispatchWholesale(@NonNull Context context, @Nullable String ownerId,
            @NonNull String floraKey, double kg, double unitPrice,
            @NonNull EconomyRepository economy, @NonNull MarketRepository market,
            @Nullable Consumer<Result> done) {
        dispatchWholesaleTo(context, ownerId, floraKey, kg, unitPrice, null, economy, market, done);
    }

    public static void dispatchWholesaleTo(@NonNull Context context, @Nullable String ownerId,
            @NonNull String floraKey, double kg, double unitPrice, @Nullable ProvincialMarket dest,
            @NonNull EconomyRepository economy, @NonNull MarketRepository market,
            @Nullable Consumer<Result> done) {
        dispatchWholesaleTo(context, ownerId, floraKey, kg, unitPrice, dest, economy, market, null, done);
    }

    public static void dispatchWholesaleTo(@NonNull Context context, @Nullable String ownerId,
            @NonNull String floraKey, double kg, double unitPrice, @Nullable ProvincialMarket dest,
            @NonNull EconomyRepository economy, @NonNull MarketRepository market,
            @Nullable String truckId, @Nullable Consumer<Result> done) {
        Context app = context.getApplicationContext();
        IO.execute(() -> {
            Result r = dispatchWholesaleNow(app, ownerId, floraKey, kg, unitPrice, dest, economy, market, truckId);
            if (done != null) {
                MAIN.post(() -> done.accept(r));
            }
        });
    }

    @NonNull
    public static Result dispatchWholesale(@NonNull Context context, @Nullable String ownerId,
            @NonNull String floraKey, double kg, double unitPrice,
            @NonNull EconomyRepository economy, @NonNull MarketRepository market) {
        return offMain(() -> dispatchWholesaleNow(context, ownerId, floraKey, kg, unitPrice, null, economy, market),
                Result.FAILED);
    }

    @NonNull
    private static Result dispatchWholesaleNow(@NonNull Context context, @Nullable String ownerId,
            @NonNull String floraKey, double kg, double unitPrice, @Nullable ProvincialMarket destForced,
            @NonNull EconomyRepository economy, @NonNull MarketRepository market) {
        return dispatchWholesaleNow(context, ownerId, floraKey, kg, unitPrice, destForced, economy, market, null);
    }

    @NonNull
    private static Result dispatchWholesaleNow(@NonNull Context context, @Nullable String ownerId,
            @NonNull String floraKey, double kg, double unitPrice, @Nullable ProvincialMarket destForced,
            @NonNull EconomyRepository economy, @NonNull MarketRepository market,
            @Nullable String truckId) {
        if (kg <= 1e-9) {
            return Result.FAILED;
        }
        if (destForced != null) {
            return FleetDispatch.dispatchSale(context, ownerId, floraKey, kg, unitPrice, destForced,
                    economy, market, truckId);
        }
        if (ExoticHoneyRules.isExotic(MapRegionPrefs.get(context), floraKey)) {
            return Result.NO_DEMAND;
        }
        if (!economy.takeHoney(floraKey, kg)) {
            return Result.FAILED;
        }
        HexParcel warehouse = warehouseParcel(context, ownerId, MapRegionPrefs.get(context));
        ProvincialMarket dest = destForced != null
                ? destForced
                : pickWholesaleMarket(context, warehouse, market, floraKey, kg);
        if (warehouse == null || dest == null) {
            economy.addHoney(floraKey, kg);
            return Result.FAILED;
        }
        unitPrice = market.priceEurPerKgForFlora(floraKey, dest);
        if (market.remainingCapacityKg(dest, floraKey) + 1e-6 < kg) {
            economy.addHoney(floraKey, kg);
            return Result.NO_DEMAND;
        }
        double[] dock = warehouseDock(context, ownerId, warehouse);
        RoadPath path = LocalGraphHopper.route(context, dock[0], dock[1], dest.lat, dest.lng);
        maybeRouteWarn(context, path);
        double km = travelKm(warehouse, dest.lat, dest.lng);
        if (path != null && path.distanceKm > 1e-6) {
            km = path.distanceKm;
        }
        double travel = CargoFreightRules.costB(kg, km);
        if (travel > 0 && !economy.trySpend(travel,
                "Transporte de " + EconomyRepository.formatKg(kg) + " kg de miel de "
                        + floraKey + " al mercado")) {
            economy.addHoney(floraKey, kg);
            return Result.NO_CASH;
        }
        market.recordSaleVolume(floraKey, kg, dest);
        if (!liveTrips(context) && !GameServer.enabled()) {
            economy.creditSaleProceeds(floraKey, kg, unitPrice,
                    EconomyRepository.saleConcept("en el mercado", floraKey, kg));
            grantSaleXp(context, ownerId, false, kg);
            return Result.INSTANT;
        }
        Map<String, Double> cargo = new LinkedHashMap<>();
        cargo.put(floraKey, kg);
        CargoTripEntity trip = baseTrip(ownerId, CargoTripEntity.KIND_WHOLESALE, cargo);
        trip.phase = CargoTripEntity.PHASE_OUT;
        trip.unitPrice = unitPrice;
        trip.originLabel = warehouseLabel(context, ownerId, warehouse);
        trip.destLabel = dest.name != null ? dest.name : "Mercado";
        trip.originHexId = warehouse.id;
        trip.destHexId = dest.hexId;
        trip.returnLat = dock[0];
        trip.returnLng = dock[1];
        trip.returnLabel = trip.originLabel;
        trip.returnHexId = warehouse.id;
        CargoTripRules.applyPath(trip, path, dock[0], dock[1], dest.lat, dest.lng);
        RoadPath back = LocalGraphHopper.route(context, dest.lat, dest.lng, dock[0], dock[1]);
        rememberReturnRoad(trip, back, dest.lat, dest.lng, dock[0], dock[1],
                FleetRules.durationMs(back.distanceKm, 70));
        try {
            saveTrip(context, trip);
        } catch (RuntimeException ex) {
            economy.addHoney(floraKey, kg);
            if (travel > 0) {
                economy.addToBalance(travel, "Devolución del transporte al mercado");
            }
            return Result.FAILED;
        }
        return Result.STARTED;
    }

    /** Tarros del obrador al mercado más cercano con demanda, a precio de tarro. */
    public static void dispatchJarSale(@NonNull Context context, @Nullable String ownerId,
            @Nullable String obradorHex, @NonNull String floraKey, @NonNull WorkshopRules.Format format,
            int jars, @NonNull EconomyRepository economy, @NonNull MarketRepository market,
            @Nullable Consumer<Result> done) {
        Context app = context.getApplicationContext();
        IO.execute(() -> {
            Result r = dispatchJarSaleNow(app, ownerId, obradorHex, floraKey, format, jars, economy, market);
            if (done != null) {
                MAIN.post(() -> done.accept(r));
            }
        });
    }

    @NonNull
    private static Result dispatchJarSaleNow(@NonNull Context context, @Nullable String ownerId,
            @Nullable String obradorHex, @NonNull String floraKey, @NonNull WorkshopRules.Format format,
            int jars, @NonNull EconomyRepository economy, @NonNull MarketRepository market) {
        if (jars <= 0 || format == WorkshopRules.Format.BULK) {
            return Result.FAILED;
        }
        HexParcel shop = obradorHex == null ? null : parcelFor(context, ownerId, obradorHex);
        if (shop == null) {
            return Result.FAILED;
        }
        double kg = jars * format.jarKg;
        ProvincialMarket dest = pickWholesaleMarket(context, shop, market, floraKey, kg);
        if (dest == null || market.remainingCapacityKg(dest, floraKey) + 1e-6 < kg) {
            return Result.NO_DEMAND;
        }
        double unitPrice = Math.round(market.priceEurPerKgForFlora(floraKey, dest)
                * format.priceFactor * 100.0) / 100.0;
        boolean moving = liveTrips(context) || GameServer.enabled();
        FleetStore.Vehicle truck = moving ? freeTruckAt(context, ownerId, shop.id, kg) : null;
        if (moving && truck == null) {
            return Result.NO_FLEET;
        }
        if (WorkshopStore.takePacked(context, ownerId, shop.id, floraKey, format, jars) <= 1e-9) {
            return Result.FAILED;
        }
        double[] dock = warehouseDock(context, ownerId, shop);
        RoadPath path = LocalGraphHopper.route(context, dock[0], dock[1], dest.lat, dest.lng);
        maybeRouteWarn(context, path);
        double km = path != null && path.distanceKm > 1e-6 ? path.distanceKm : travelKm(shop, dest.lat, dest.lng);
        double travel = CargoFreightRules.costB(kg, km);
        if (travel > 0 && !economy.trySpend(travel, "Transporte de " + jars + " tarros de miel de "
                + floraKey + " al mercado")) {
            WorkshopStore.returnJars(context, ownerId, shop.id, floraKey, format, jars);
            return Result.NO_CASH;
        }
        market.recordSaleVolume(floraKey, kg, dest);
        if (!moving) {
            economy.creditSaleProceeds(floraKey, kg, unitPrice,
                    EconomyRepository.saleConcept("en el mercado", floraKey, kg));
            grantSaleXp(context, ownerId, false, kg);
            return Result.INSTANT;
        }
        Map<String, Double> cargo = new LinkedHashMap<>();
        cargo.put(floraKey, kg);
        CargoTripEntity trip = baseTrip(ownerId, CargoTripEntity.KIND_WHOLESALE, cargo);
        stampJars(trip, format, jars);
        trip.phase = CargoTripEntity.PHASE_OUT;
        trip.unitPrice = unitPrice;
        trip.priceLocked = 1;
        trip.floraKey = floraKey;
        trip.vehicleId = truck.id;
        trip.originLabel = context.getString(R.string.workshop_name);
        trip.destLabel = dest.name != null ? dest.name : "Mercado";
        trip.originHexId = shop.id;
        trip.destHexId = dest.hexId;
        trip.returnLat = dock[0];
        trip.returnLng = dock[1];
        trip.returnLabel = trip.originLabel;
        trip.returnHexId = shop.id;
        CargoTripRules.applyPath(trip, path, dock[0], dock[1], dest.lat, dest.lng);
        applyVehicleSpeed(context, trip, path);
        RoadPath back = LocalGraphHopper.route(context, dest.lat, dest.lng, dock[0], dock[1]);
        rememberReturnRoad(trip, back, dest.lat, dest.lng, dock[0], dock[1],
                FleetRules.durationMs(back.distanceKm, FleetRules.speedKmh(truck.rulesKind(), truck.level)));
        try {
            saveTrip(context, trip);
        } catch (RuntimeException ex) {
            WorkshopStore.returnJars(context, ownerId, shop.id, floraKey, format, jars);
            if (travel > 0) {
                economy.addToBalance(travel, "Devolución del transporte al mercado");
            }
            return Result.FAILED;
        }
        FleetStore.bindCargo(context, ownerId, truck.id, trip.id);
        return Result.STARTED;
    }

    public static void dispatchOrder(@NonNull Context context, @Nullable String ownerId,
            @NonNull HoneyOrder order, @NonNull EconomyRepository economy,
            @Nullable Consumer<Result> done) {
        dispatchOrder(context, ownerId, order, economy, null, done);
    }

    public static void dispatchOrder(@NonNull Context context, @Nullable String ownerId,
            @NonNull HoneyOrder order, @NonNull EconomyRepository economy,
            @Nullable String truckId, @Nullable Consumer<Result> done) {
        Context app = context.getApplicationContext();
        IO.execute(() -> {
            Result r = dispatchOrderNow(app, ownerId, order, economy, truckId);
            if (done != null) {
                MAIN.post(() -> done.accept(r));
            }
        });
    }

    @NonNull
    public static Result dispatchOrder(@NonNull Context context, @Nullable String ownerId,
            @NonNull HoneyOrder order, @NonNull EconomyRepository economy) {
        return offMain(() -> dispatchOrderNow(context, ownerId, order, economy, null), Result.FAILED);
    }

    public static void orderTruckOptions(@NonNull Context context, @Nullable String ownerId,
            @NonNull HoneyOrder order, @Nullable Consumer<OrderTruckChoice> done) {
        orderTruckOptions(context, ownerId, order, null, done);
    }

    public static void orderTruckOptions(@NonNull Context context, @Nullable String ownerId,
            @NonNull HoneyOrder order, @Nullable String warehouseHexId,
            @Nullable Consumer<OrderTruckChoice> done) {
        Context app = context.getApplicationContext();
        IO.execute(() -> {
            OrderTruckChoice choice = orderTruckChoice(app, ownerId, order, warehouseHexId);
            if (done != null) {
                MAIN.post(() -> done.accept(choice));
            }
        });
    }

    private static volatile String orderFailDetail;

    @Nullable
    public static String consumeOrderFailDetail() {
        String detail = orderFailDetail;
        orderFailDetail = null;
        return detail;
    }

    private static void failOrder(@NonNull String message) {
        orderFailDetail = message;
        Log.w("OrderDispatch", message);
    }

    @NonNull
    private static Result dispatchOrderNow(@NonNull Context context, @Nullable String ownerId,
            @NonNull HoneyOrder order, @NonNull EconomyRepository economy, @Nullable String truckId) {
        orderFailDetail = null;
        long now = System.currentTimeMillis();
        if (orderTripId(context, order.id) != null) {
            failOrder("Esa comanda ya va en un camión.");
            return Result.FAILED;
        }
        if (order.expired(now)) {
            failOrder("La comanda ya ha caducado.");
            return Result.FAILED;
        }
        if (order.kg <= 1e-9) {
            return Result.FAILED;
        }
        boolean moving = liveTrips(context) || GameServer.enabled();
        FleetStore.Vehicle truck = null;
        HexParcel warehouse;
        if (moving) {
            settleHoney(context, ownerId);
            OrderRide ride = truckId != null && !truckId.isEmpty()
                    ? rideForTruck(context, ownerId, order, truckId)
                    : orderRide(context, ownerId, order);
            if (ride == null) {
                return Result.NO_FLEET;
            }
            truck = ride.truck;
            warehouse = ride.warehouse;
        } else {
            warehouse = warehouseParcelNear(context, ownerId, order.destLat, order.destLng,
                    order.destHexId);
            if (warehouse == null) {
                return Result.FAILED;
            }
        }
        if (order.wantsJars()) {
            // Los tarros salen del obrador que los tiene; con camión, del obrador del camión.
            if (!moving) {
                HexParcel stocked = obradorWithMix(context, ownerId, order);
                if (stocked != null) {
                    warehouse = stocked;
                }
            }
            if (!WorkshopStore.hasMix(context, ownerId, warehouse.id, order.floraKey, order.mix)) {
                failOrder(context.getString(R.string.market_order_fail_jars));
                return Result.FAILED;
            }
        }
        if (!takeOrderStock(context, ownerId, economy, order, warehouse.id)) {
            if (order.wantsJars()) {
                failOrder(context.getString(R.string.market_order_fail_jars));
            }
            return Result.FAILED;
        }
        boolean bulk = !order.wantsJars();
        double travel = travelCostB(warehouse, order.destLat, order.destLng, order.kg);
        if (travel > 0 && !economy.trySpend(travel,
                "Transporte de la comanda de " + order.floraKey)) {
            returnOrderStock(context, ownerId, economy, order, null, warehouse.id);
            return Result.NO_CASH;
        }
        if (bulk && moving
                && !WarehouseHoneyStore.take(context, ownerId, warehouse.id, order.floraKey, order.kg)) {
            economy.addHoney(order.floraKey, order.kg);
            if (travel > 0) {
                economy.addToBalance(travel, "Devolución del transporte de la comanda");
            }
            failOrder(context.getString(R.string.market_order_fail_stock));
            return Result.FAILED;
        }
        String stockHex = bulk && moving ? warehouse.id : null;
        if (!claimOrder(context, order.id, ownerId)) {
            returnOrderStock(context, ownerId, economy, order, stockHex, warehouse.id);
            if (travel > 0) {
                economy.addToBalance(travel, "Devolución del transporte de la comanda");
            }
            if (orderFailDetail == null) {
                failOrder(claimFailureText());
            }
            return Result.FAILED;
        }
        HoneyOrderEntity live = AppDatabase.getInstance(context).honeyOrderDao().getById(order.id);
        if (live != null) {
            order = HoneyOrder.fromEntity(live);
        }
        // En modo servidor no se liquida una comanda localmente aunque el modo
        // visual de camiones esté desactivado: el viaje y su precio los resuelve
        // el reloj autoritativo del backend.
        if (!moving) {
            economy.creditSaleProceeds(order.floraKey, order.kg, order.unitPrice,
                    EconomyRepository.saleConcept("vía comanda", order.floraKey, order.kg));
            HoneyOrderStore.finish(context, order.id);
            grantSaleXp(context, ownerId, true, order.kg);
            OrderReceipts.publishInstant(context, order);
            return Result.INSTANT;
        }
        Map<String, Double> cargo = new LinkedHashMap<>();
        cargo.put(order.floraKey, order.kg);
        CargoTripEntity trip = baseTrip(ownerId, CargoTripEntity.KIND_ORDER, cargo);
        if (order.wantsJars()) {
            stampMix(trip, order.mix);
        }
        trip.phase = CargoTripEntity.PHASE_OUT;
        trip.unitPrice = order.unitPrice;
        trip.npcName = order.npcName;
        trip.floraKey = order.floraKey;
        trip.orderId = order.id;
        trip.vehicleId = truck.id;
        stampOrderPortrait(trip, order.portraitIndex);
        double[] dock = warehouseDock(context, ownerId, warehouse);
        trip.originLabel = warehouseLabel(context, ownerId, warehouse);
        trip.destLabel = order.destLabel;
        trip.originHexId = warehouse.id;
        trip.destHexId = order.destHexId;
        trip.returnLat = dock[0];
        trip.returnLng = dock[1];
        trip.returnLabel = trip.originLabel;
        trip.returnHexId = warehouse.id;
        RoadPath path = LocalGraphHopper.route(context, dock[0], dock[1], order.destLat, order.destLng);
        maybeRouteWarn(context, path);
        CargoTripRules.applyPath(trip, path, dock[0], dock[1], order.destLat, order.destLng);
        applyVehicleSpeed(context, trip, path);
        RoadPath back = LocalGraphHopper.route(context, order.destLat, order.destLng, dock[0], dock[1]);
        double kmh = FleetRules.speedKmh(truck.rulesKind(), truck.level);
        rememberReturnRoad(trip, back, order.destLat, order.destLng, dock[0], dock[1],
                FleetRules.durationMs(back.distanceKm, kmh));
        if (orderTripId(context, order.id) != null) {
            HoneyOrderStore.release(context, order.id);
            returnOrderStock(context, ownerId, economy, order, stockHex, warehouse.id);
            if (travel > 0) {
                economy.addToBalance(travel, "Devolución del transporte de la comanda");
            }
            failOrder("Esa comanda ya va en un camión.");
            return Result.FAILED;
        }
        try {
            saveTrip(context, trip);
        } catch (RuntimeException ex) {
            HoneyOrderStore.release(context, order.id);
            returnOrderStock(context, ownerId, economy, order, stockHex, warehouse.id);
            if (travel > 0) {
                economy.addToBalance(travel, "Devolución del transporte de la comanda");
            }
            String why = ex.getMessage();
            failOrder(why != null && !why.isEmpty()
                    ? why
                    : "El servidor no ha aceptado el viaje de la comanda.");
            return Result.FAILED;
        }
        FleetStore.bindCargo(context, ownerId, truck.id, trip.id);
        OrderReceipts.stage(context, trip.id, order);
        return Result.STARTED;
    }

    @Nullable
    private static String orderTripId(@NonNull Context context, @Nullable String orderId) {
        if (orderId == null || orderId.isEmpty()) {
            return null;
        }
        List<CargoTripEntity> rows = dao(context).getAllSync();
        if (rows == null) {
            return null;
        }
        for (CargoTripEntity trip : rows) {
            if (trip != null && CargoTripEntity.KIND_ORDER.equals(trip.kind)
                    && orderId.equals(trip.orderId)) {
                return trip.id;
            }
        }
        return null;
    }

    private static boolean claimOrder(@NonNull Context context, @NonNull String orderId,
            @Nullable String ownerId) {
        if (ownerId == null || ownerId.isEmpty()) {
            failOrder("No hay sesión. Cierra la app y vuelve a entrar.");
            return false;
        }
        if (HoneyOrderStore.claim(context, orderId, ownerId)) {
            return true;
        }
        if (GameServer.enabled()) {
            GameServer.syncOffersBlocking(context);
            HoneyOrderEntity row = AppDatabase.getInstance(context).honeyOrderDao().getById(orderId);
            if (row != null && row.taken && ownerId.equals(row.claimedBy)) {
                return true;
            }
        }
        return HoneyOrderStore.claim(context, orderId, ownerId);
    }

    @NonNull
    private static String claimFailureText() {
        int status = GameServer.lastOfferStatus();
        if (status == 401 || status == 403) {
            return "El servidor no ha reconocido la sesión.";
        }
        if (status == 409) {
            return "Esa comanda ya no está disponible.";
        }
        if (status == 0) {
            return "No hay conexión con el servidor.";
        }
        return "El servidor ha rechazado la comanda (" + status + ").";
    }

    @Nullable
    private static OrderRide orderRide(@NonNull Context context, @Nullable String ownerId,
            @NonNull HoneyOrder order) {
        Context app = context.getApplicationContext();
        FleetStore.Vehicle best = null;
        HexParcel bestHome = null;
        double bestKm = Double.MAX_VALUE;
        for (FleetStore.Vehicle truck : freeTrucks(context, ownerId)) {
            if (FleetRules.honeyKg(FleetRules.Kind.TRUCK, truck.level) + 1e-6 < order.kg) {
                continue;
            }
            HexParcel home = parcelFor(app, ownerId, truck.homeId);
            if (home == null || !warehouseCoversOrder(context, ownerId, home.id, order)) {
                continue;
            }
            double km = travelKm(home, order.destLat, order.destLng);
            if (km < bestKm) {
                best = truck;
                bestHome = home;
                bestKm = km;
            }
        }
        if (best == null || bestHome == null) {
            return null;
        }
        return new OrderRide(best, bestHome);
    }

    @Nullable
    private static OrderRide rideForTruck(@NonNull Context context, @Nullable String ownerId,
            @NonNull HoneyOrder order, @NonNull String truckId) {
        if (!truckFree(context, ownerId, truckId)) {
            return null;
        }
        FleetStore.Vehicle truck = FleetStore.vehicle(context, ownerId, truckId);
        if (truck == null || FleetRules.honeyKg(FleetRules.Kind.TRUCK, truck.level) + 1e-6 < order.kg) {
            return null;
        }
        HexParcel home = parcelFor(context, ownerId, truck.homeId);
        if (home == null || !warehouseCoversOrder(context, ownerId, home.id, order)) {
            return null;
        }
        return new OrderRide(truck, home);
    }

    private static boolean warehouseCoversOrder(@NonNull Context context, @Nullable String ownerId,
            @Nullable String hexId, @NonNull HoneyOrder order) {
        if (order.wantsJars()) {
            return hexId != null && WorkshopStore.hasMix(context, ownerId, hexId, order.floraKey, order.mix);
        }
        return WarehouseHoneyStore.kg(context, ownerId, hexId, order.floraKey) + 1e-6 >= order.kg;
    }

    /** Saca de su sitio lo que pide la comanda. */
    private static boolean takeOrderStock(@NonNull Context context, @Nullable String ownerId,
            @NonNull EconomyRepository economy, @NonNull HoneyOrder order, @Nullable String obradorHex) {
        if (order.wantsJars()) {
            return WorkshopStore.takeMix(context, ownerId, obradorHex, order.floraKey, order.mix) > 1e-9;
        }
        return economy.takeHoney(order.floraKey, order.kg);
    }

    private static void returnOrderStock(@NonNull Context context, @Nullable String ownerId,
            @NonNull EconomyRepository economy, @NonNull HoneyOrder order,
            @Nullable String warehouseHexId, @Nullable String obradorHex) {
        if (order.wantsJars()) {
            WorkshopStore.returnMix(context, ownerId, obradorHex, order.floraKey, order.mix);
            return;
        }
        economy.addHoney(order.floraKey, order.kg);
        if (warehouseHexId != null) {
            WarehouseHoneyStore.add(context, ownerId, warehouseHexId, order.floraKey, order.kg);
        }
    }

    @NonNull
    private static OrderTruckChoice orderTruckChoice(@NonNull Context context, @Nullable String ownerId,
            @NonNull HoneyOrder order) {
        return orderTruckChoice(context, ownerId, order, null);
    }

    @NonNull
    private static OrderTruckChoice orderTruckChoice(@NonNull Context context, @Nullable String ownerId,
            @NonNull HoneyOrder order, @Nullable String warehouseHexId) {
        if (!liveTrips(context) && !GameServer.enabled()) {
            return new OrderTruckChoice(true, Collections.emptyList());
        }
        Context app = context.getApplicationContext();
        settleHoney(app, ownerId);
        String requiredHome = warehouseHexId != null && !warehouseHexId.isEmpty() ? warehouseHexId : null;
        List<HexParcelOwnershipEntity> rows = AppDatabase.getInstance(app)
                .hexParcelOwnershipDao().getWarehousesForOwnerSync(ownerId);
        boolean anyStock = false;
        if (requiredHome != null) {
            anyStock = warehouseCoversOrder(context, ownerId, requiredHome, order);
        } else if (rows != null) {
            for (HexParcelOwnershipEntity row : rows) {
                if (row != null && warehouseCoversOrder(context, ownerId, row.hexId, order)) {
                    anyStock = true;
                    break;
                }
            }
        }
        List<OrderTruckOption> options = new ArrayList<>();
        for (FleetStore.Vehicle truck : freeTrucks(context, ownerId)) {
            double capacity = FleetRules.honeyKg(FleetRules.Kind.TRUCK, truck.level);
            if (capacity + 1e-6 < order.kg) {
                continue;
            }
            if (requiredHome != null && !requiredHome.equals(truck.homeId)) {
                continue;
            }
            HexParcel home = parcelFor(app, ownerId, truck.homeId);
            if (home == null || !sameTerritory(home.id, order.destHexId)
                    || !warehouseCoversOrder(context, ownerId, home.id, order)) {
                continue;
            }
            options.add(new OrderTruckOption(
                    truck.id,
                    truckName(truck),
                    truck.level,
                    warehouseLabel(context, ownerId, home),
                    capacity,
                    travelKm(home, order.destLat, order.destLng),
                    travelCostB(home, order.destLat, order.destLng, order.kg),
                    true));
        }
        options.sort((a, b) -> Double.compare(a.distanceKm, b.distanceKm));
        return new OrderTruckChoice(false, options, !anyStock && options.isEmpty());
    }

    public static void saleTruckOptions(@NonNull Context context, @Nullable String ownerId,
            @NonNull ProvincialMarket market, @NonNull String floraKey, double kg,
            @Nullable Consumer<List<OrderTruckOption>> done) {
        Context app = context.getApplicationContext();
        IO.execute(() -> {
            List<OrderTruckOption> options = saleTrucks(app, ownerId, market, floraKey, kg);
            if (done != null) {
                MAIN.post(() -> done.accept(options));
            }
        });
    }

    @NonNull
    private static List<OrderTruckOption> saleTrucks(@NonNull Context context, @Nullable String ownerId,
            @NonNull ProvincialMarket market, @NonNull String floraKey, double kg) {
        List<OrderTruckOption> options = new ArrayList<>();
        if (!liveTrips(context) && !GameServer.enabled()) {
            return options;
        }
        Context app = context.getApplicationContext();
        settleHoney(app, ownerId);
        for (FleetStore.Vehicle truck : freeTrucks(context, ownerId)) {
            double capacity = FleetRules.honeyKg(FleetRules.Kind.TRUCK, truck.level);
            if (capacity + 1e-6 < kg) {
                continue;
            }
            if (WarehouseHoneyStore.kg(context, ownerId, truck.homeId, floraKey) + 1e-6 < kg) {
                continue;
            }
            HexParcel home = parcelFor(app, ownerId, truck.homeId);
            if (home == null || !sameTerritory(home.id, market.hexId)) {
                continue;
            }
            options.add(new OrderTruckOption(
                    truck.id,
                    truckName(truck),
                    truck.level,
                    warehouseLabel(context, ownerId, home),
                    capacity,
                    travelKm(home, market.lat, market.lng),
                    travelCostB(home, market.lat, market.lng, kg),
                    true));
        }
        options.sort((a, b) -> Double.compare(a.distanceKm, b.distanceKm));
        return options;
    }

    public static final class OrderTruckOption {
        public final String truckId;
        public final String truckLabel;
        public final int level;
        public final String warehouseLabel;
        public final double capacityKg;
        public final double distanceKm;
        public final double travelCostB;
        public final boolean holdsHoney;

        public OrderTruckOption(@NonNull String truckId, @NonNull String truckLabel, int level,
                @NonNull String warehouseLabel, double capacityKg, double distanceKm,
                double travelCostB, boolean holdsHoney) {
            this.truckId = truckId;
            this.truckLabel = truckLabel;
            this.level = level;
            this.warehouseLabel = warehouseLabel;
            this.capacityKg = capacityKg;
            this.distanceKm = distanceKm;
            this.travelCostB = travelCostB;
            this.holdsHoney = holdsHoney;
        }
    }

    public static final class OrderTruckChoice {
        public final boolean instant;
        @NonNull
        public final List<OrderTruckOption> trucks;
        /** Ningún almacén tiene la miel y los kilos de la comanda. */
        public final boolean missingHoney;

        public OrderTruckChoice(boolean instant, @Nullable List<OrderTruckOption> trucks) {
            this(instant, trucks, false);
        }

        public OrderTruckChoice(boolean instant, @Nullable List<OrderTruckOption> trucks,
                boolean missingHoney) {
            this.instant = instant;
            this.trucks = trucks == null
                    ? Collections.emptyList()
                    : Collections.unmodifiableList(new ArrayList<>(trucks));
            this.missingHoney = missingHoney;
        }
    }

    private static final class OrderRide {
        final FleetStore.Vehicle truck;
        final HexParcel warehouse;

        OrderRide(@NonNull FleetStore.Vehicle truck, @NonNull HexParcel warehouse) {
            this.truck = truck;
            this.warehouse = warehouse;
        }
    }

    /** Da la vuelta al camión desde donde esté. La carga se devuelve al llegar. */
    public static void cancelTrip(@NonNull Context context, @NonNull String tripId,
            @NonNull Consumer<String> onMain) {
        IO.execute(() -> {
            String err = cancelTripBlocking(context.getApplicationContext(), tripId);
            MAIN.post(() -> onMain.accept(err));
        });
    }

    @Nullable
    private static String cancelTripBlocking(@NonNull Context context, @NonNull String tripId) {
        CargoTripEntity trip = dao(context).getById(tripId);
        if (trip == null) {
            return context.getString(R.string.trip_cancel_already);
        }
        if (CargoTripEntity.PHASE_RETURN.equals(trip.phase) || isCancelled(trip)) {
            return context.getString(R.string.trip_cancel_already);
        }
        long now = System.currentTimeMillis();
        Map<String, Double> carrying = CargoTripEntity.KIND_COLLECT.equals(trip.kind)
                ? new LinkedHashMap<>(carriedNow(trip, now))
                : new LinkedHashMap<>(cargoOf(trip));
        double homeLat = Math.abs(trip.returnLat) > 1e-8 ? trip.returnLat : trip.originLat;
        double homeLng = Math.abs(trip.returnLng) > 1e-8 ? trip.returnLng : trip.originLng;
        String homeHex = trip.returnHexId != null && !trip.returnHexId.isEmpty()
                ? trip.returnHexId : trip.originHexId;
        String homeLabel = trip.returnLabel != null && !trip.returnLabel.isEmpty()
                ? trip.returnLabel
                : (trip.originLabel != null ? trip.originLabel : "Obrador");
        double[] here = CargoTripRules.position(trip, now);
        String shipment = trip.shipmentId;
        if (shipment != null && !shipment.isEmpty()) {
            List<CargoTripEntity> rows = dao(context).getAllSync();
            if (rows != null) {
                for (CargoTripEntity other : rows) {
                    if (other != null && shipment.equals(other.shipmentId)
                            && !trip.id.equals(other.id)) {
                        dao(context).delete(other.id);
                    }
                }
            }
        }
        try {
            JSONObject kept = new JSONObject();
            kept.put("_cancelled", 1);
            for (Map.Entry<String, Double> e : carrying.entrySet()) {
                if (e.getKey() != null && e.getValue() != null && e.getValue() > 1e-9) {
                    kept.put(e.getKey(), e.getValue());
                }
            }
            trip.cargoJson = kept.toString();
        } catch (JSONException ignored) {
            trip.cargoJson = "{\"_cancelled\":1}";
        }
        trip.phase = CargoTripEntity.PHASE_RETURN;
        trip.legRole = CargoTripEntity.LEG_HAUL_TRUCK;
        trip.originLabel = "En ruta";
        trip.destLabel = homeLabel;
        trip.destHexId = homeHex;
        RoadPath path = LocalGraphHopper.route(context, here[0], here[1], homeLat, homeLng);
        CargoTripRules.applyPath(trip, path, here[0], here[1], homeLat, homeLng);
        applyVehicleSpeed(context, trip, path);
        try {
            saveTrip(context, trip);
        } catch (RuntimeException e) {
            return e.getMessage() != null ? e.getMessage()
                    : context.getString(R.string.trip_cancel_already);
        }
        return null;
    }

    private static boolean isCancelled(@Nullable CargoTripEntity trip) {
        if (trip == null || trip.cargoJson == null) {
            return false;
        }
        try {
            return new JSONObject(trip.cargoJson).optInt("_cancelled", 0) == 1;
        } catch (JSONException e) {
            return false;
        }
    }

    public static void completeDue(@NonNull Context context, @NonNull EconomyRepository economy,
            @NonNull MarketRepository market) {
        if (GameServer.enabled()) {
            GameServer.syncBlocking(context);
        }
        CargoTripDao trips = dao(context);
        for (int pass = 0; pass < 30; pass++) {
            List<CargoTripEntity> rows = trips.getAllSync();
            if (rows == null || rows.isEmpty()) {
                FleetStore.releaseIdleKnown(context);
                return;
            }
            long now = System.currentTimeMillis();
            boolean moved = false;
            for (CargoTripEntity trip : rows) {
                if (GameServer.enabled() && CargoTripEntity.KIND_ORDER.equals(trip.kind)
                        && !isCancelled(trip)) {
                    continue;
                }
                if (!tourClockDone(trip, now)) {
                    continue;
                }
                // Con servidor, la recogida se abona allí. Liquidarla aquí la duplica
                // o la pierde si el almacén local rechaza la miel.
                if (GameServer.enabled() && CargoTripEntity.KIND_COLLECT.equals(trip.kind)
                        && !isCancelled(trip)) {
                    continue;
                }
                moved = true;
                if (isCancelled(trip)) {
                    if (returnCancelledJars(context, trip)) {
                        FleetStore.releaseVehicle(context, trip.ownerId, trip.vehicleId);
                        trips.delete(trip.id);
                        continue;
                    }
                    if (toWorkshop(trip)) {
                        creditHarvest(context, trip.ownerId, trip.destHexId, economy, parseCargo(trip),
                                trip.originLabel);
                    } else {
                        creditCargo(context, trip.ownerId, trip.destHexId, economy, parseCargo(trip));
                    }
                    FleetStore.releaseVehicle(context, trip.ownerId, trip.vehicleId);
                    trips.delete(trip.id);
                    continue;
                }
                if (CargoTripEntity.KIND_DELIVERY.equals(trip.kind)) {
                    FleetStore.releaseVehicle(context, trip.ownerId, trip.vehicleId);
                    trips.delete(trip.id);
                    continue;
                }
                if (CargoTripEntity.PHASE_OUT.equals(trip.phase)) {
                    if (CargoTripEntity.LEG_PICKUP.equals(trip.legRole)) {
                        promotePickup(context, trip);
                        continue;
                    }
                    if (CargoTripEntity.LEG_TRANSFER.equals(trip.legRole)) {
                        if (advanceTransfer(context, trip, economy)) {
                            continue;
                        }
                        startReturnLeg(context, trip);
                        continue;
                    }
                    if (CargoTripEntity.LEG_HAUL_TRUCK.equals(trip.legRole)
                            || CargoTripEntity.LEG_HAUL_SHIP.equals(trip.legRole)) {
                        startReturnLeg(context, trip);
                        continue;
                    }
                    if (CargoTripEntity.KIND_COLLECT.equals(trip.kind) && advanceCollectStops(context, trip)) {
                        continue;
                    }
                    if (CargoTripEntity.KIND_COLLECT.equals(trip.kind) && collectReadyToSettle(trip)) {
                        settle(context, trip, economy, market);
                        FleetStore.releaseVehicle(context, trip.ownerId, trip.vehicleId);
                        trips.delete(trip.id);
                        continue;
                    }
                    if (!CargoTripEntity.KIND_COLLECT.equals(trip.kind)) {
                        settle(context, trip, economy, market);
                    }
                    startReturnLeg(context, trip);
                    continue;
                }
                if (CargoTripEntity.KIND_COLLECT.equals(trip.kind)) {
                    settle(context, trip, economy, market);
                }
                FleetStore.releaseVehicle(context, trip.ownerId, trip.vehicleId);
                trips.delete(trip.id);
            }
            if (!moved) {
                FleetStore.releaseIdleKnown(context);
                return;
            }
        }
    }

    /** La recogida ya está en el almacén o el recorrido entero ha cumplido su reloj. */
    private static boolean collectReadyToSettle(@NonNull CargoTripEntity trip) {
        if (!CargoTripEntity.KIND_COLLECT.equals(trip.kind)) {
            return false;
        }
        if (CargoTripEntity.PHASE_RETURN.equals(trip.phase)) {
            return true;
        }
        if (near(trip.destLat, trip.destLng, trip.returnLat, trip.returnLng)) {
            return true;
        }
        List<TourLeg> tour = readTour(trip);
        return tour.size() >= 2 && tour.get(tour.size() - 1).toWarehouse && !hasLaterStops(trip)
                && tourClockDone(trip, System.currentTimeMillis());
    }

    private static void promotePickup(@NonNull Context context, @NonNull CargoTripEntity trip) {
        double fromLat = trip.destLat;
        double fromLng = trip.destLng;
        trip.legRole = CargoTripEntity.LEG_DELIVER;
        trip.phase = CargoTripEntity.PHASE_OUT;
        trip.originLabel = trip.destLabel;
        trip.originHexId = trip.destHexId;
        trip.destLabel = trip.chainLabel != null ? trip.chainLabel : "Mercado";
        trip.destHexId = trip.chainHexId;
        RoadPath path = LocalGraphHopper.route(context, fromLat, fromLng, trip.chainLat, trip.chainLng);
        CargoTripRules.applyPath(trip, path, fromLat, fromLng, trip.chainLat, trip.chainLng);
        applyVehicleSpeed(context, trip, path);
        saveTrip(context, trip);
    }

    /** @return true si el camión sigue hacia otra parada. */
    private static boolean advanceTransfer(@NonNull Context context, @NonNull CargoTripEntity trip,
            @NonNull EconomyRepository economy) {
        double nextKg = 0.0;
        String flora = trip.floraKey;
        try {
            JSONObject o = new JSONObject(trip.cargoJson != null ? trip.cargoJson : "{}");
            nextKg = o.optDouble("_nextKg", 0.0);
            if (flora == null || flora.isEmpty()) {
                Iterator<String> keys = o.keys();
                while (keys.hasNext()) {
                    String k = keys.next();
                    if (!k.startsWith("_")) {
                        flora = k;
                        break;
                    }
                }
            }
        } catch (JSONException ignored) {
        }
        double drop = Math.max(0.0, trip.kg - nextKg);
        if (drop > 1e-9 && flora != null) {
            economy.addHoney(flora, drop);
            WarehouseHoneyStore.add(context, trip.ownerId, trip.destHexId, flora, drop);
        }
        if (nextKg <= 1e-9 || Math.abs(trip.chainLat) < 1e-8) {
            return false;
        }
        double fromLat = trip.destLat;
        double fromLng = trip.destLng;
        double toLat = trip.chainLat;
        double toLng = trip.chainLng;
        trip.kg = nextKg;
        trip.floraKey = flora;
        try {
            JSONObject left = new JSONObject();
            left.put(flora != null ? flora : "Mil flores", nextKg);
            trip.cargoJson = left.toString();
        } catch (JSONException ignored) {
        }
        trip.destLabel = trip.chainLabel != null ? trip.chainLabel : "Obrador";
        trip.destHexId = trip.chainHexId;
        trip.chainLat = 0;
        trip.chainLng = 0;
        trip.chainLabel = null;
        trip.chainHexId = null;
        RoadPath path = LocalGraphHopper.route(context, fromLat, fromLng, toLat, toLng);
        CargoTripRules.applyPath(trip, path, fromLat, fromLng, toLat, toLng);
        applyVehicleSpeed(context, trip, path);
        saveTrip(context, trip);
        return true;
    }

    private static void applyVehicleSpeed(@NonNull Context context, @NonNull CargoTripEntity trip,
            @Nullable RoadPath path) {
        FleetStore.Vehicle vehicle = FleetStore.vehicle(context, trip.ownerId, trip.vehicleId);
        double kmh = 70.0;
        if (vehicle != null) {
            kmh = FleetRules.speedKmh(vehicle.rulesKind(), vehicle.level);
        }
        double km = path != null && path.distanceKm > 1e-6 ? path.distanceKm : 0.0;
        if (km > 1e-6) {
            trip.durationMs = FleetRules.durationMs(km, kmh);
        }
    }

    private static void startReturnLeg(@NonNull Context context, @NonNull CargoTripEntity trip) {
        if (CargoTripEntity.LEG_HAUL_SHIP.equals(trip.legRole)) {
            long dur = trip.durationMs;
            List<double[]> pts = new ArrayList<>(CargoTripRules.routePoints(trip));
            Collections.reverse(pts);
            double fromLat = trip.destLat;
            double fromLng = trip.destLng;
            trip.phase = CargoTripEntity.PHASE_RETURN;
            trip.originLabel = trip.destLabel;
            trip.originHexId = trip.destHexId;
            trip.destLabel = trip.returnLabel != null ? trip.returnLabel : "Puerto";
            trip.destHexId = trip.returnHexId;
            trip.originLat = fromLat;
            trip.originLng = fromLng;
            trip.destLat = trip.returnLat;
            trip.destLng = trip.returnLng;
            trip.routePolyline = EncodedPolyline.encode(pts);
            trip.routeRoadKinds = null;
            trip.startEpochMs = System.currentTimeMillis();
            trip.durationMs = dur;
            dropDeliveredCargo(trip);
            saveTrip(context, trip);
            return;
        }
        double fromLat = trip.destLat;
        double fromLng = trip.destLng;
        String fromLabel = trip.destLabel;
        String fromHex = trip.destHexId;
        trip.phase = CargoTripEntity.PHASE_RETURN;
        trip.originLabel = fromLabel;
        trip.originHexId = fromHex;
        trip.destLabel = trip.returnLabel != null ? trip.returnLabel : "Obrador";
        trip.destHexId = trip.returnHexId;
        RoadPath planned = null;
        try {
            planned = pathFromTour(new JSONObject(trip.cargoJson != null ? trip.cargoJson : "{}"),
                    fromLat, fromLng, trip.returnLat, trip.returnLng);
        } catch (JSONException ignored) {
        }
        RoadPath path = planned != null
                ? planned
                : LocalGraphHopper.route(context, fromLat, fromLng, trip.returnLat, trip.returnLng);
        CargoTripRules.applyPath(trip, path, fromLat, fromLng, trip.returnLat, trip.returnLng);
        applyVehicleSpeed(context, trip, path);
        dropDeliveredCargo(trip);
        saveTrip(context, trip);
    }

    /** La miel de una venta ya se entregó: la vuelta al almacén va vacía. La recogida sí vuelve cargada. */
    private static void dropDeliveredCargo(@NonNull CargoTripEntity trip) {
        if (CargoTripEntity.KIND_COLLECT.equals(trip.kind)) {
            return;
        }
        trip.kg = 0;
        trip.floraKey = null;
        try {
            JSONObject o = new JSONObject(trip.cargoJson != null ? trip.cargoJson : "{}");
            JSONObject kept = new JSONObject();
            Iterator<String> keys = o.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                if (key.startsWith("_")) {
                    kept.put(key, o.get(key));
                }
            }
            trip.cargoJson = kept.toString();
        } catch (JSONException ignored) {
            trip.cargoJson = "{}";
        }
    }

    private static void settle(@NonNull Context context, @NonNull CargoTripEntity trip,
            @NonNull EconomyRepository economy, @NonNull MarketRepository market) {
        Map<String, Double> cargo = parseCargo(trip);
        if (CargoTripEntity.KIND_COLLECT.equals(trip.kind)) {
            String warehouseHex = trip.returnHexId != null && !trip.returnHexId.isEmpty()
                    ? trip.returnHexId : trip.destHexId;
            Map<String, Double> collected = collectCargo(trip);
            if (toWorkshop(trip)) {
                creditHarvest(context, trip.ownerId, warehouseHex, economy, collected, trip.originLabel);
            } else {
                creditCargo(context, trip.ownerId, warehouseHex, economy, collected);
            }
            HarvestReceipts.publish(context, trip.id, collected, hiveCountOf(trip));
            return;
        }
        if (CargoTripEntity.KIND_TRANSFER.equals(trip.kind)
                || CargoTripEntity.LEG_TRANSFER.equals(trip.legRole)) {
            for (Map.Entry<String, Double> e : cargo.entrySet()) {
                if (e.getKey() == null || e.getKey().startsWith("_") || e.getValue() == null) {
                    continue;
                }
                economy.addHoney(e.getKey(), e.getValue());
                WarehouseHoneyStore.add(context, trip.ownerId, trip.destHexId, e.getKey(), e.getValue());
            }
            return;
        }
        double creditedKg = 0.0;
        for (Map.Entry<String, Double> e : cargo.entrySet()) {
            if (e.getValue() == null || e.getValue() <= 1e-9) {
                continue;
            }
            if (CargoTripEntity.KIND_ORDER.equals(trip.kind) && orderMissedDeadline(context, trip)) {
                continue;
            }
            double price = trip.unitPrice;
            if (trip.priceLocked != 1 && CargoTripEntity.KIND_WHOLESALE.equals(trip.kind)) {
                ProvincialMarket dest = ProvincialMarketCatalog.findByName(context,
                        PlayableMapRegion.fromHexId(trip.destHexId), trip.destLabel);
                if (dest != null) {
                    price = market.priceEurPerKgForFlora(e.getKey(), dest);
                }
            }
            economy.creditSaleProceeds(e.getKey(), e.getValue(), price,
                    EconomyRepository.saleConcept(
                            CargoTripEntity.KIND_ORDER.equals(trip.kind) ? "vía comanda" : "en el mercado",
                            e.getKey(), e.getValue()));
            creditedKg += e.getValue();
        }
        if (CargoTripEntity.KIND_ORDER.equals(trip.kind)) {
            HoneyOrderStore.finish(context, trip.orderId);
            if (creditedKg > 1e-9) {
                grantSaleXp(context, trip.ownerId, true, creditedKg);
                String flora = trip.floraKey;
                if ((flora == null || flora.isEmpty()) && !cargo.isEmpty()) {
                    flora = cargo.keySet().iterator().next();
                }
                String npc = trip.npcName;
                int portrait = portraitIndexOf(trip);
                if ((npc == null || npc.isEmpty() || flora == null || flora.isEmpty() || portrait <= 0) && trip.orderId != null) {
                    HoneyOrderEntity saved = AppDatabase.getInstance(context).honeyOrderDao().getById(trip.orderId);
                    if (saved != null) {
                        if (npc == null || npc.isEmpty()) npc = saved.npcName;
                        if (flora == null || flora.isEmpty()) flora = saved.floraKey;
                        if (portrait <= 0) portrait = saved.portraitIndex;
                    }
                }
                OrderReceipts.publish(context, trip.id, trip.orderId, npc, portrait, flora,
                        creditedKg, creditedKg * trip.unitPrice);
            }
        } else if (CargoTripEntity.KIND_WHOLESALE.equals(trip.kind) && creditedKg > 1e-9) {
            grantSaleXp(context, trip.ownerId, false, creditedKg);
        }
    }

    private static void stampOrderPortrait(@NonNull CargoTripEntity trip, int portraitIndex) {
        try {
            JSONObject o = new JSONObject(trip.cargoJson != null ? trip.cargoJson : "{}");
            o.put("_portraitIndex", portraitIndex);
            trip.cargoJson = o.toString();
        } catch (JSONException ignored) {
        }
    }

    private static int portraitIndexOf(@NonNull CargoTripEntity trip) {
        if (trip.cargoJson != null) {
            try {
                JSONObject o = new JSONObject(trip.cargoJson);
                if (o.has("_portraitIndex")) {
                    return o.optInt("_portraitIndex", 0);
                }
            } catch (JSONException ignored) {
            }
        }
        return 0;
    }

    private static void stampHiveCount(@NonNull CargoTripEntity trip, int hiveCount) {
        try {
            JSONObject o = new JSONObject(trip.cargoJson != null ? trip.cargoJson : "{}");
            o.put("_hiveCount", Math.max(1, hiveCount));
            trip.cargoJson = o.toString();
        } catch (JSONException ignored) {
        }
    }

    private static int hiveCountOf(@NonNull CargoTripEntity trip) {
        try {
            JSONObject o = new JSONObject(trip.cargoJson != null ? trip.cargoJson : "{}");
            return Math.max(1, o.optInt("_hiveCount", 1));
        } catch (JSONException e) {
            return 1;
        }
    }

    /** Kilos de la recogida. Si el total no está en la raíz, se suman las paradas. */
    @NonNull
    private static Map<String, Double> collectCargo(@NonNull CargoTripEntity trip) {
        Map<String, Double> cargo = parseCargo(trip);
        if (!cargo.isEmpty()) {
            return cargo;
        }
        Map<String, Double> sum = new LinkedHashMap<>();
        for (TourLeg leg : readTour(trip)) {
            if (leg.toWarehouse || leg.load.isEmpty()) {
                continue;
            }
            for (Map.Entry<String, Double> e : leg.load.entrySet()) {
                if (e.getKey() == null || e.getValue() == null || e.getValue() <= 1e-9) {
                    continue;
                }
                Double prev = sum.get(e.getKey());
                sum.put(e.getKey(), (prev == null ? 0.0 : prev) + e.getValue());
            }
        }
        return sum;
    }

    private static void grantSaleXp(@NonNull Context context, @Nullable String ownerId, boolean order,
            double kg) {
        Context app = context.getApplicationContext();
        if (!(app instanceof ApicultureApp) || ownerId == null || ownerId.isEmpty()) {
            return;
        }
        double xp = order ? XpAwards.orderDelivered(kg) : XpAwards.marketSold(kg);
        ((ApicultureApp) app).getHiveRepository().grantXp(ownerId, xp);
    }

    private static boolean orderMissedDeadline(@NonNull Context context, @NonNull CargoTripEntity trip) {
        if (trip.orderId == null || trip.orderId.isEmpty()) {
            return true;
        }
        HoneyOrderEntity row = AppDatabase.getInstance(context).honeyOrderDao().getById(trip.orderId);
        if (row == null) {
            return true;
        }
        return row.expireEpochMs > 0L && System.currentTimeMillis() >= row.expireEpochMs;
    }

    @NonNull
    private static CargoTripEntity baseTrip(@Nullable String ownerId, @NonNull String kind,
            @NonNull Map<String, Double> cargo) {
        CargoTripEntity trip = new CargoTripEntity();
        trip.id = UUID.randomUUID().toString();
        trip.ownerId = ownerId;
        trip.kind = kind;
        Map.Entry<String, Double> first = cargo.entrySet().iterator().next();
        trip.floraKey = first.getKey();
        trip.kg = totalKg(cargo);
        trip.cargoJson = toJson(cargo);
        return trip;
    }

    @NonNull
    private static String apiaryKey(@NonNull Context context, @NonNull HiveEntity hive) {
        Context app = context.getApplicationContext();
        HexParcel hex = hive.hexId != null && !hive.hexId.isEmpty()
                ? IberiaHexOverlayStore.findById(app, hive.hexId) : null;
        List<HexParcelOwnershipEntity> sites = hive.ownerId != null && hive.hexId != null
                ? AppDatabase.getInstance(app).hexParcelOwnershipDao()
                .listByHexAndOwnerSync(hive.hexId, hive.ownerId)
                : Collections.emptyList();
        HexParcelOwnershipEntity site = HexParcelRandomPoint.nearestApiary(hex, sites, hive.lat, hive.lng);
        String siteId = "hex";
        if (site != null && WarehouseRules.isApiarySite(site)) {
            siteId = site.siteId != null && !site.siteId.isEmpty() ? site.siteId : "default";
        }
        return (hive.hexId == null ? "" : hive.hexId) + "\t" + siteId;
    }

    @NonNull
    private static double[] hiveCollectPoint(@NonNull Context context, @NonNull HiveEntity hive) {
        Context app = context.getApplicationContext();
        HexParcel hex = hive.hexId != null && !hive.hexId.isEmpty()
                ? IberiaHexOverlayStore.findById(app, hive.hexId) : null;
        List<HexParcelOwnershipEntity> sites = hive.ownerId != null && hive.hexId != null
                ? AppDatabase.getInstance(app).hexParcelOwnershipDao()
                .listByHexAndOwnerSync(hive.hexId, hive.ownerId)
                : Collections.emptyList();
        return HexParcelRandomPoint.collectPointForHive(hex, sites, hive.lat, hive.lng);
    }

    @NonNull
    private static String hiveCollectLabel(@NonNull Context context, @NonNull HiveEntity hive) {
        Context app = context.getApplicationContext();
        HexParcel hex = hive.hexId != null && !hive.hexId.isEmpty()
                ? IberiaHexOverlayStore.findById(app, hive.hexId) : null;
        List<HexParcelOwnershipEntity> sites = hive.ownerId != null && hive.hexId != null
                ? AppDatabase.getInstance(app).hexParcelOwnershipDao()
                .listByHexAndOwnerSync(hive.hexId, hive.ownerId)
                : Collections.emptyList();
        HexParcelOwnershipEntity site = HexParcelRandomPoint.nearestApiary(hex, sites, hive.lat, hive.lng);
        if (site != null && site.parcelName != null && !site.parcelName.trim().isEmpty()) {
            return site.parcelName.trim();
        }
        return hive.name != null && !hive.name.isEmpty() ? hive.name : "Apiario";
    }

    @Nullable
    public static HexParcel warehouseParcel(@NonNull Context context, @Nullable String ownerId) {
        return warehouseParcel(context, ownerId, MapRegionPrefs.get(context));
    }

    @Nullable
    public static HexParcel warehouseParcel(@NonNull Context context, @Nullable String ownerId,
            @Nullable PlayableMapRegion region) {
        if (region == null) {
            region = PlayableMapRegion.IBERIA;
        }
        return nearestWarehouse(context, ownerId, region, Double.NaN, Double.NaN);
    }

    @Nullable
    public static HexParcel warehouseParcelNear(@NonNull Context context, @Nullable String ownerId,
            double destLat, double destLng, @Nullable String destHexId) {
        PlayableMapRegion region = destHexId != null && !destHexId.isEmpty()
                ? PlayableMapRegion.fromHexId(destHexId)
                : (PlayableMapRegion.containing(destLat, destLng) != null
                ? PlayableMapRegion.containing(destLat, destLng)
                : PlayableMapRegion.IBERIA);
        return nearestWarehouse(context, ownerId, region, destLat, destLng);
    }

    @Nullable
    private static HexParcel nearestWarehouse(@NonNull Context context, @Nullable String ownerId,
            @NonNull PlayableMapRegion region, double destLat, double destLng) {
        Context app = context.getApplicationContext();
        HexParcelOwnershipDao own =
                AppDatabase.getInstance(app).hexParcelOwnershipDao();
        List<HexParcelOwnershipEntity> rows = own.getWarehousesForOwnerSync(ownerId);
        return pickNearestInRegion(app, rows, region, destLat, destLng);
    }

    @Nullable
    private static HexParcel pickNearestInRegion(@NonNull Context app,
            @Nullable List<HexParcelOwnershipEntity> rows, @NonNull PlayableMapRegion region,
            double destLat, double destLng) {
        if (rows == null || rows.isEmpty()) {
            return null;
        }
        boolean haveFocus = !Double.isNaN(destLat) && !Double.isNaN(destLng);
        HexParcel best = null;
        double bestKm = Double.MAX_VALUE;
        for (HexParcelOwnershipEntity row : rows) {
            if (row == null || row.hexId == null) {
                continue;
            }
            if (PlayableMapRegion.fromHexId(row.hexId) != region) {
                continue;
            }
            HexParcel parcel = IberiaHexOverlayStore.findById(app, row.hexId);
            if (parcel == null) {
                continue;
            }
            double[] dock = HexParcelRandomPoint.warehouseOf(parcel, row);
            double km = haveFocus
                    ? TranshumanceRules.haversineKm(destLat, destLng, dock[0], dock[1])
                    : 0.0;
            if (best == null || km < bestKm) {
                best = parcel;
                bestKm = km;
            }
        }
        return best;
    }

    /** Muelle del almacén: no coincide con el pin del mercado si comparten hex. */
    @NonNull
    public static double[] warehouseDock(@NonNull Context context, @NonNull HexParcel warehouse) {
        return warehouseDock(context, null, warehouse);
    }

    /** Muelle del almacén de este dueño. No usa el pin de un apiario del mismo hex. */
    @NonNull
    public static double[] warehouseDock(@NonNull Context context, @Nullable String ownerId,
            @NonNull HexParcel warehouse) {
        Context app = context.getApplicationContext();
        List<HexParcelOwnershipEntity> rows = AppDatabase.getInstance(app).hexParcelOwnershipDao()
                .listByHexSync(warehouse.id);
        HexParcelOwnershipEntity row = warehouseSite(rows, ownerId);
        double[] dock = HexParcelRandomPoint.warehouseOf(warehouse, row);
        double lat = dock[0];
        double lng = dock[1];
        if (ProvincialMarketCatalog.isMarketHex(context, warehouse.id)) {
            lat -= 0.0024;
            lng -= 0.0028;
        }
        return new double[]{lat, lng};
    }

    @Nullable
    private static HexParcelOwnershipEntity warehouseSite(@Nullable List<HexParcelOwnershipEntity> rows,
            @Nullable String ownerId) {
        HexParcelOwnershipEntity any = null;
        if (rows == null) {
            return null;
        }
        for (HexParcelOwnershipEntity row : rows) {
            if (row == null || !row.hasWarehouse) {
                continue;
            }
            if (ownerId != null && ownerId.equals(row.ownerId)) {
                return row;
            }
            if (any == null) {
                any = row;
            }
        }
        return any;
    }

    public static void warehouseParcelAsync(@NonNull Context context, @Nullable String ownerId,
            @Nullable Consumer<HexParcel> done) {
        Context app = context.getApplicationContext();
        IO.execute(() -> {
            HexParcel parcel = warehouseParcel(app, ownerId);
            if (done != null) {
                MAIN.post(() -> done.accept(parcel));
            }
        });
    }

    public static void warehouseParcelNearAsync(@NonNull Context context, @Nullable String ownerId,
            @Nullable HiveEntity hive, @Nullable Consumer<HexParcel> done) {
        Context app = context.getApplicationContext();
        IO.execute(() -> {
            HexParcel parcel;
            if (hive == null) {
                parcel = warehouseParcel(app, ownerId);
            } else {
                double[] dest = hiveCollectPoint(app, hive);
                parcel = warehouseParcelNear(app, ownerId, dest[0], dest[1], hive.hexId);
            }
            if (done != null) {
                MAIN.post(() -> done.accept(parcel));
            }
        });
    }

    public static double travelCostB(@Nullable HexParcel warehouse, double destLat, double destLng,
            double kg) {
        return CargoFreightRules.costB(kg, travelKm(warehouse, destLat, destLng));
    }

    public static long travelDurationMs(@Nullable HexParcel warehouse, double destLat, double destLng) {
        return TruckTripRules.durationMs(travelKm(warehouse, destLat, destLng));
    }

    /** Hex del mapa, o el punto del almacén si la malla aún no tiene ese id. */
    @Nullable
    public static HexParcel parcelFor(@NonNull Context context, @Nullable String ownerId,
            @Nullable String hexId) {
        if (hexId == null || hexId.isEmpty()) {
            return null;
        }
        Context app = context.getApplicationContext();
        HexParcel found = IberiaHexOverlayStore.findById(app, hexId);
        if (found != null) {
            return found;
        }
        List<HexParcelOwnershipEntity> rows = AppDatabase.getInstance(app)
                .hexParcelOwnershipDao().listByHexSync(hexId);
        HexParcelOwnershipEntity row = null;
        if (rows != null) {
            for (HexParcelOwnershipEntity candidate : rows) {
                if (candidate == null) {
                    continue;
                }
                if (ownerId != null && ownerId.equals(candidate.ownerId) && candidate.hasWarehouse) {
                    row = candidate;
                    break;
                }
                if (row == null || (ownerId != null && ownerId.equals(candidate.ownerId))) {
                    row = candidate;
                }
            }
        }
        if (row == null) {
            return null;
        }
        double lat = Math.abs(row.warehouseLat) > 1e-8 ? row.warehouseLat : row.siteLat;
        double lng = Math.abs(row.warehouseLng) > 1e-8 ? row.warehouseLng : row.siteLng;
        double[][] ring = new double[6][2];
        for (int i = 0; i < 6; i++) {
            ring[i][0] = lat;
            ring[i][1] = lng;
        }
        return new HexParcel(hexId, ring, lat, lng, 1, false, null, row.parcelName);
    }

    public static double travelKm(@Nullable HexParcel warehouse, double destLat, double destLng) {
        if (warehouse == null) {
            return 0.0;
        }
        return TranshumanceRules.haversineKm(warehouse.centroidLat, warehouse.centroidLon, destLat, destLng);
    }

    @Nullable
    public static ProvincialMarket nearestMarket(@NonNull Context context, @Nullable HexParcel warehouse) {
        return pickWholesaleMarket(context, warehouse, null, null, 0.0);
    }

    @Nullable
    public static ProvincialMarket pickWholesaleMarket(@NonNull Context context,
            @Nullable HexParcel warehouse, @Nullable MarketRepository market,
            @Nullable String floraKey, double kg) {
        if (warehouse == null) {
            return null;
        }
        PlayableMapRegion region = PlayableMapRegion.fromHexId(warehouse.id);
        Context app = context.getApplicationContext();
        List<ProvincialMarket> all = ProvincialMarketCatalog.resolve(app, region);
        ProvincialMarket bestCapital = null;
        ProvincialMarket bestAny = null;
        double bestCapitalKm = Double.MAX_VALUE;
        double bestAnyKm = Double.MAX_VALUE;
        for (ProvincialMarket m : all) {
            if (m == null || m.international) {
                continue;
            }
            if (market != null && kg > 1e-9 && market.remainingCapacityKg(m, floraKey) + 1e-6 < kg) {
                continue;
            }
            double km = TranshumanceRules.haversineKm(
                    warehouse.centroidLat, warehouse.centroidLon, m.lat, m.lng);
            if (!m.local && km < bestCapitalKm) {
                bestCapitalKm = km;
                bestCapital = m;
            }
            if (km < bestAnyKm) {
                bestAnyKm = km;
                bestAny = m;
            }
        }
        if (bestCapital != null) {
            return bestCapital;
        }
        if (bestAny != null) {
            return bestAny;
        }
        for (ProvincialMarket m : all) {
            if (m != null && !m.local && !m.international) {
                return m;
            }
        }
        return all.isEmpty() ? null : all.get(0);
    }

    @NonNull
    public static String formatRemaining(long remainingMs) {
        long sec = Math.max(0L, remainingMs / 1000L);
        long h = sec / 3600L;
        long m = (sec % 3600L) / 60L;
        long s = sec % 60L;
        if (h > 0) {
            return String.format(Locale.getDefault(), "%d h %02d min", h, m);
        }
        if (m > 0) {
            return String.format(Locale.getDefault(), "%d min %02d s", m, s);
        }
        return String.format(Locale.getDefault(), "%d s", s);
    }

    @NonNull
    public static String formatCountdown(long remainingMs) {
        long sec = Math.max(0L, remainingMs / 1000L);
        long h = sec / 3600L;
        long m = (sec % 3600L) / 60L;
        long s = sec % 60L;
        if (h > 0) {
            return String.format(Locale.getDefault(), "%d h %02d min %02d s", h, m, s);
        }
        if (m > 0) {
            return String.format(Locale.getDefault(), "%d min %02d s", m, s);
        }
        return String.format(Locale.getDefault(), "%d s", s);
    }

    public static boolean cargoTouchesHex(@Nullable CargoTripEntity trip, @Nullable String hexId) {
        if (trip == null || hexId == null || hexId.isEmpty()) {
            return false;
        }
        return hexId.equals(trip.originHexId)
                || hexId.equals(trip.destHexId)
                || hexId.equals(trip.returnHexId);
    }

    @NonNull
    public static List<CargoTripEntity> cargoTouchingHex(
            @NonNull Context context, @Nullable String ownerId, @Nullable String hexId) {
        List<CargoTripEntity> out = new ArrayList<>();
        if (hexId == null || hexId.isEmpty()) {
            return out;
        }
        for (CargoTripEntity trip : dao(context).getAllSync()) {
            if (trip == null) {
                continue;
            }
            if (ownerId != null && !ownerId.equals(trip.ownerId)) {
                continue;
            }
            if (cargoTouchesHex(trip, hexId)) {
                out.add(trip);
            }
        }
        return out;
    }

    @NonNull
    public static List<TruckTripEntity> trucksNearDock(
            @NonNull Context context, @Nullable String ownerId, double dockLat, double dockLng) {
        List<TruckTripEntity> out = new ArrayList<>();
        AppDatabase db = AppDatabase.getInstance(context.getApplicationContext());
        for (TruckTripEntity trip : db.truckTripDao().getAllSync()) {
            if (trip == null) {
                continue;
            }
            if (ownerId != null && !ownerId.equals(trip.ownerId)) {
                continue;
            }
            if (nearDock(trip.originLat, trip.originLng, dockLat, dockLng)
                    || nearDock(trip.destLat, trip.destLng, dockLat, dockLng)) {
                out.add(trip);
            }
        }
        return out;
    }

    private static boolean nearDock(double lat, double lng, double dockLat, double dockLng) {
        return HexParcelRandomPoint.dist2(lat, lng, dockLat, dockLng) < 4.0e-7;
    }

    @NonNull
    public static String cargoSummary(@Nullable CargoTripEntity trip) {
        if (trip == null || unloadedReturn(trip)) {
            return "";
        }
        Map<String, Double> cargo = parseCargo(trip);
        if (cargo.size() == 1) {
            Map.Entry<String, Double> e = cargo.entrySet().iterator().next();
            return String.format(Locale.getDefault(), "%.2f kg %s", e.getValue(), e.getKey());
        }
        return String.format(Locale.getDefault(), "%.2f kg", trip.kg);
    }

    @NonNull
    private static <T> T offMain(@NonNull Callable<T> work, @NonNull T fallback) {
        if (Looper.getMainLooper() != Looper.myLooper()) {
            try {
                return work.call();
            } catch (Exception e) {
                return fallback;
            }
        }
        try {
            return IO.submit(work).get();
        } catch (Exception e) {
            return fallback;
        }
    }

    private static void maybeRouteWarn(@NonNull Context context, @Nullable RoadPath path) {
        if (path != null && path.followsRoads()) {
            return;
        }
        Context app = context.getApplicationContext();
        if (Looper.myLooper() == Looper.getMainLooper()) {
            RouteErrorDialog.show(app, LocalGraphHopper.lastDiag());
        } else {
            MAIN.post(() -> RouteErrorDialog.show(app, LocalGraphHopper.lastDiag()));
        }
    }

    /** Obrador (almacén) con los tarros de la comanda, o null si ninguno los tiene. */
    @Nullable
    private static HexParcel obradorWithMix(@NonNull Context context, @Nullable String ownerId,
            @NonNull HoneyOrder order) {
        for (com.apiculture.simulator.domain.workshop.WorkshopState s : WorkshopStore.all(context, ownerId)) {
            if (s.hexId != null && WorkshopStore.hasMix(context, ownerId, s.hexId, order.floraKey, order.mix)) {
                HexParcel p = parcelFor(context, ownerId, s.hexId);
                if (p != null) {
                    return p;
                }
            }
        }
        return null;
    }

    /** No hay ningún obrador (almacén) y la recogida lo exige. */
    public static boolean harvestNeedsWorkshop(@NonNull Context context, @Nullable String ownerId) {
        return WorkshopRules.REQUIRED_FOR_HARVEST && !WorkshopStore.built(context, ownerId);
    }

    /** La miel recién cosechada entra en el obrador del almacén al que llega; si no se puede, al almacén. */
    private static void creditHarvest(@NonNull Context context, @Nullable String ownerId,
            @Nullable String warehouseHexId, @NonNull EconomyRepository economy,
            @NonNull Map<String, Double> cargo, @Nullable String source) {
        if (WorkshopRules.REQUIRED_FOR_HARVEST && warehouseHexId != null
                && WorkshopStore.receiveLines(context, ownerId, warehouseHexId, cargo, source) == null) {
            return;
        }
        creditCargo(context, ownerId, warehouseHexId, economy, cargo);
    }

    private static boolean toWorkshop(@Nullable CargoTripEntity trip) {
        if (trip == null || !CargoTripEntity.KIND_COLLECT.equals(trip.kind) || trip.cargoJson == null) {
            return false;
        }
        try {
            return new JSONObject(trip.cargoJson).has("_workshop");
        } catch (JSONException e) {
            return false;
        }
    }

    private static void stampJars(@NonNull CargoTripEntity trip, @NonNull WorkshopRules.Format format,
            int jars) {
        try {
            JSONObject o = new JSONObject(trip.cargoJson != null ? trip.cargoJson : "{}");
            o.put("_format", format.name());
            o.put("_jars", jars);
            trip.cargoJson = o.toString();
        } catch (JSONException ignored) {
        }
    }

    /** El camión de una comanda lleva su mezcla de tarros, para devolverla si se cancela. */
    private static void stampMix(@NonNull CargoTripEntity trip, @Nullable JarMix mix) {
        if (mix == null) {
            return;
        }
        try {
            JSONObject o = new JSONObject(trip.cargoJson != null ? trip.cargoJson : "{}");
            o.put("_mix", mix.encode());
            o.put("_jars", mix.total());
            trip.cargoJson = o.toString();
        } catch (JSONException ignored) {
        }
    }

    /** Un viaje de tarros cancelado devuelve los tarros al obrador. */
    private static boolean returnCancelledJars(@NonNull Context context, @NonNull CargoTripEntity trip) {
        if (trip.cargoJson == null || trip.kg <= 1e-9) {
            return false;
        }
        try {
            JSONObject o = new JSONObject(trip.cargoJson);
            JarMix mix = JarMix.parse(o.optString("_mix", ""));
            WorkshopRules.Format format = WorkshopRules.Format.parse(o.optString("_format", ""));
            int jars = o.optInt("_jars", 0);
            if (mix == null && (format == null || format == WorkshopRules.Format.BULK || jars <= 0)) {
                return false;
            }
            String flora = trip.floraKey;
            if (flora == null || flora.isEmpty()) {
                Iterator<String> keys = o.keys();
                while (keys.hasNext()) {
                    String k = keys.next();
                    if (!k.startsWith("_")) {
                        flora = k;
                        break;
                    }
                }
            }
            if (flora == null) {
                return false;
            }
            // Los tarros vuelven al obrador del que salió el camión.
            String hex = trip.originHexId;
            return mix != null ? WorkshopStore.returnMix(context, trip.ownerId, hex, flora, mix)
                    : WorkshopStore.returnJars(context, trip.ownerId, hex, flora, format, jars);
        } catch (JSONException e) {
            return false;
        }
    }

    /** Hay existencias para la comanda: tarros en el obrador o miel a granel en el almacén. */
    public static boolean hasStockForOrder(@NonNull Context context, @Nullable String ownerId,
            @NonNull EconomyRepository economy, @NonNull HoneyOrder order) {
        if (order.wantsJars()) {
            return obradorWithMix(context, ownerId, order) != null;
        }
        return economy.getHoneyStockForFlora(order.floraKey) + 1e-9 >= order.kg;
    }

    static void creditCargo(@NonNull Context context, @Nullable String ownerId,
            @Nullable String hexId, @NonNull EconomyRepository economy, @NonNull Map<String, Double> cargo) {
        List<HexParcelOwnershipEntity> rows = AppDatabase.getInstance(context.getApplicationContext())
                .hexParcelOwnershipDao().getWarehousesForOwnerSync(ownerId);
        int maxLevel = WarehouseRules.maxLevel(rows, ownerId);
        HexParcel parcel = hexId != null && !hexId.isEmpty()
                ? IberiaHexOverlayStore.findById(context.getApplicationContext(), hexId) : null;
        double room = parcel != null
                ? warehouseFreeKg(context, ownerId, parcel)
                : WarehouseRules.roomKg(rows, ownerId, economy.getHoneyStock());
        for (Map.Entry<String, Double> e : cargo.entrySet()) {
            if (e.getKey() == null || e.getKey().startsWith("_")
                    || e.getValue() == null || e.getValue() <= 1e-9 || room <= 1e-9) {
                continue;
            }
            if (!WarehouseRules.allowsFlora(maxLevel, e.getKey())) {
                continue;
            }
            double added = economy.addHoneyCapped(e.getKey(), e.getValue(), economy.getHoneyStock() + room);
            if (added > 1e-9 && hexId != null) {
                WarehouseHoneyStore.add(context, ownerId, hexId, e.getKey(), added);
            }
            room -= added;
        }
    }

    /** Hueco del almacén: su propia capacidad y el tope global de miel. */
    private static double warehouseFreeKg(@NonNull Context context, @Nullable String ownerId,
            @Nullable HexParcel warehouse) {
        if (warehouse == null || warehouse.id == null) {
            return 0.0;
        }
        Context app = context.getApplicationContext();
        List<HexParcelOwnershipEntity> rows = AppDatabase.getInstance(app)
                .hexParcelOwnershipDao().getWarehousesForOwnerSync(ownerId);
        int level = 0;
        if (rows != null) {
            for (HexParcelOwnershipEntity row : rows) {
                if (row != null && warehouse.id.equals(row.hexId) && row.hasWarehouse
                        && (ownerId == null || ownerId.equals(row.ownerId))) {
                    level = Math.max(level, WarehouseRules.levelOf(row));
                }
            }
        }
        if (level <= 0) {
            return 0.0;
        }
        double local = WarehouseRules.capacityKg(level)
                - WarehouseHoneyStore.totalAt(app, ownerId, warehouse.id);
        double global = local;
        if (app instanceof ApicultureApp) {
            EconomyRepository economy = ((ApicultureApp) app).getEconomyRepository();
            global = WarehouseRules.roomKg(rows, ownerId, economy.getHoneyStock());
        }
        return Math.max(0.0, Math.min(local, global));
    }

    @NonNull
    private static Map<String, Double> trimCargo(@NonNull Map<String, Double> cargo, double limit) {
        Map<String, Double> out = new LinkedHashMap<>();
        double left = Math.max(0.0, limit);
        for (Map.Entry<String, Double> e : cargo.entrySet()) {
            if (left <= 1e-9 || e.getKey() == null || e.getValue() == null) {
                continue;
            }
            double part = Math.min(e.getValue(), left);
            if (part > 1e-9) {
                out.put(e.getKey(), part);
                left -= part;
            }
        }
        return out;
    }

    @NonNull
    private static Map<String, Double> cleanCargo(@NonNull Map<String, Double> raw) {
        Map<String, Double> cargo = new LinkedHashMap<>();
        for (Map.Entry<String, Double> e : raw.entrySet()) {
            if (e.getKey() == null || e.getValue() == null || e.getValue() <= 1e-9) {
                continue;
            }
            cargo.put(e.getKey(), e.getValue());
        }
        return cargo;
    }

    @NonNull
    private static Map<String, Double> parseCargo(@NonNull CargoTripEntity trip) {
        Map<String, Double> cargo = new LinkedHashMap<>();
        if (trip.cargoJson != null && !trip.cargoJson.isEmpty()) {
            try {
                JSONObject o = new JSONObject(trip.cargoJson);
                Iterator<String> keys = o.keys();
                while (keys.hasNext()) {
                    String k = keys.next();
                    if (k.startsWith("_")) {
                        continue;
                    }
                    cargo.put(k, o.optDouble(k, 0.0));
                }
            } catch (JSONException ignored) {
            }
        }
        if (cargo.isEmpty() && trip.floraKey != null) {
            cargo.put(trip.floraKey, trip.kg);
        }
        return cargo;
    }

    @NonNull
    private static String toJson(@NonNull Map<String, Double> cargo) {
        JSONObject o = new JSONObject();
        for (Map.Entry<String, Double> e : cargo.entrySet()) {
            try {
                o.put(e.getKey(), e.getValue());
            } catch (JSONException ignored) {
            }
        }
        return o.toString();
    }

    private static double totalKg(@NonNull Map<String, Double> cargo) {
        double s = 0.0;
        for (Double v : cargo.values()) {
            if (v != null) {
                s += v;
            }
        }
        return s;
    }

    /**
     * Recorrido completo de una recogida con más de un apiario: almacén, cada apiario
     * y la vuelta al almacén. Vacío si el viaje no es esa recogida.
     */
    @NonNull
    public static List<TourLeg> collectTour(@Nullable CargoTripEntity trip) {
        if (trip == null || !CargoTripEntity.KIND_COLLECT.equals(trip.kind)) {
            return Collections.emptyList();
        }
        List<TourLeg> raw = readTour(trip);
        if (raw.size() < 2) {
            raw = synthesizeTour(trip);
        }
        if (raw.size() < 2) {
            return Collections.emptyList();
        }
        int current = tourIndexAt(trip, raw, System.currentTimeMillis());
        List<TourLeg> out = new ArrayList<>(raw.size());
        for (int i = 0; i < raw.size(); i++) {
            TourLeg leg = raw.get(i);
            out.add(new TourLeg(leg.fromLabel, leg.toLabel, leg.fromWarehouse, leg.toWarehouse,
                    leg.durationMs, i == current, i < current, leg.polyline, leg.roadKinds,
                    leg.fromLat, leg.fromLng, leg.toLat, leg.toLng, leg.loadFlora, leg.load));
        }
        return out;
    }

    /**
     * Posición del camión. En una recogida de varios apiarios sigue el tramo
     * que toca según el reloj, sin quedarse parado al llegar al primero.
     */
    @NonNull
    public static double[] tourPosition(@Nullable CargoTripEntity trip, long nowMs) {
        List<TourLeg> tour = collectTour(trip);
        if (trip == null || tour.size() < 2 || !followFullTour(trip, tour)) {
            return CargoTripRules.position(trip, nowMs);
        }
        int index = tourIndexAt(trip, tour, nowMs);
        TourLeg leg = tour.get(index);
        long elapsed = Math.max(0L, nowMs - trip.startEpochMs);
        for (int i = 0; i < index; i++) {
            elapsed -= Math.max(1L, tour.get(i).durationMs);
        }
        long span = Math.max(1L, leg.durationMs);
        double progress = Math.max(0.0, Math.min(1.0, elapsed / (double) span));
        List<double[]> points = decodeTour(leg.polyline);
        if (points.size() < 2) {
            points = new ArrayList<>();
            points.add(new double[] {leg.fromLat, leg.fromLng});
            points.add(new double[] {leg.toLat, leg.toLng});
        }
        return TruckTripRules.along(points, progress);
    }

    /** Tiempo que falta del tramo indicado. Los pendientes conservan su duración entera. */
    public static long legRemaining(@Nullable CargoTripEntity trip, @NonNull List<TourLeg> tour,
            int index, long nowMs) {
        if (trip == null || index < 0 || index >= tour.size()) {
            return 0L;
        }
        TourLeg leg = tour.get(index);
        if (!followFullTour(trip, tour)) {
            if (leg.done) {
                return 0L;
            }
            if (leg.current) {
                return CargoTripRules.remainingMs(trip, nowMs);
            }
            return leg.durationMs;
        }
        if (leg.done) {
            return 0L;
        }
        if (!leg.current) {
            return leg.durationMs;
        }
        long elapsed = Math.max(0L, nowMs - trip.startEpochMs);
        for (int i = 0; i < index; i++) {
            elapsed -= Math.max(1L, tour.get(i).durationMs);
        }
        return Math.max(0L, leg.durationMs - elapsed);
    }

    private static boolean packedTour(@NonNull CargoTripEntity trip, @NonNull List<TourLeg> tour) {
        long sum = 0L;
        for (TourLeg leg : tour) {
            sum += Math.max(0L, leg.durationMs);
        }
        return sum > 0L && Math.abs(sum - trip.durationMs) <= 1500L;
    }

    /**
     * Recorrido que sigue saliendo del punto de partida original. El reloj guardado
     * puede ser solo el primer tramo; mientras el origen no haya cambiado, el camión
     * continúa por los apiarios que faltan en vez de quedarse parado.
     */
    private static boolean followFullTour(@NonNull CargoTripEntity trip, @NonNull List<TourLeg> tour) {
        if (tour.size() < 2) {
            return false;
        }
        if (packedTour(trip, tour)) {
            return true;
        }
        TourLeg first = tour.get(0);
        if (!near(trip.originLat, trip.originLng, first.fromLat, first.fromLng)) {
            return false;
        }
        long sum = 0L;
        for (TourLeg leg : tour) {
            sum += Math.max(0L, leg.durationMs);
        }
        return sum > trip.durationMs + 1500L;
    }

    /** El recorrido completo ya ha cumplido su reloj. Un tramo intermedio no cuenta como llegada. */
    public static boolean tourClockDone(@Nullable CargoTripEntity trip, long nowMs) {
        if (trip == null) {
            return true;
        }
        List<TourLeg> tour = readTour(trip);
        if (followFullTour(trip, tour)) {
            long sum = 0L;
            for (TourLeg leg : tour) {
                sum += Math.max(0L, leg.durationMs);
            }
            return nowMs >= trip.startEpochMs + sum;
        }
        return CargoTripRules.wallClockDone(trip, nowMs);
    }

    private static int tourIndexAt(@NonNull CargoTripEntity trip, @NonNull List<TourLeg> raw, long nowMs) {
        if (followFullTour(trip, raw)) {
            long elapsed = Math.max(0L, nowMs - trip.startEpochMs);
            for (int i = 0; i < raw.size(); i++) {
                long span = Math.max(1L, raw.get(i).durationMs);
                if (elapsed < span) {
                    return i;
                }
                elapsed -= span;
            }
            return raw.size() - 1;
        }
        if (CargoTripEntity.PHASE_RETURN.equals(trip.phase)) {
            return raw.size() - 1;
        }
        for (int i = 0; i < raw.size(); i++) {
            TourLeg leg = raw.get(i);
            if (near(leg.toLat, leg.toLng, trip.destLat, trip.destLng)) {
                return i;
            }
        }
        return 0;
    }

    /** Toda la miel del viaje, tipo a tipo. */
    @NonNull
    public static Map<String, Double> cargoOf(@Nullable CargoTripEntity trip) {
        if (trip == null || unloadedReturn(trip)) {
            return Collections.emptyMap();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(parseCargo(trip)));
    }

    /** Vuelta de una comanda o de una venta: la miel ya se entregó. */
    public static boolean unloadedReturn(@Nullable CargoTripEntity trip) {
        return trip != null
                && CargoTripEntity.PHASE_RETURN.equals(trip.phase)
                && !CargoTripEntity.KIND_COLLECT.equals(trip.kind);
    }

    /**
     * Miel que la moto lleva en este instante: la de los apiarios a los que ya ha
     * llegado. En la vuelta al almacén es la carga completa, y sigue así hasta llegar.
     */
    @NonNull
    public static Map<String, Double> carriedNow(@Nullable CargoTripEntity trip, long now) {
        if (trip == null || !CargoTripEntity.KIND_COLLECT.equals(trip.kind)) {
            return cargoOf(trip);
        }
        List<TourLeg> tour = collectTour(trip);
        boolean detailed = false;
        for (TourLeg leg : tour) {
            if (!leg.load.isEmpty()) {
                detailed = true;
                break;
            }
        }
        if (!detailed) {
            return cargoOf(trip);
        }
        boolean back = CargoTripEntity.PHASE_RETURN.equals(trip.phase);
        boolean arrived = CargoTripRules.progress(trip, now) >= 1.0 - 1e-9;
        Map<String, Double> sum = new LinkedHashMap<>();
        for (TourLeg leg : tour) {
            if (leg.toWarehouse || leg.load.isEmpty()) {
                continue;
            }
            if (back || leg.done || (leg.current && arrived)) {
                for (Map.Entry<String, Double> e : leg.load.entrySet()) {
                    Double prev = sum.get(e.getKey());
                    sum.put(e.getKey(), (prev == null ? 0.0 : prev) + e.getValue());
                }
            }
        }
        return sum.isEmpty() ? Collections.emptyMap() : Collections.unmodifiableMap(sum);
    }

    /**
     * Miel que va en el camión durante este tramo: la recogida en los apiarios
     * anteriores. En la vuelta al almacén es toda la carga del viaje.
     */
    @NonNull
    public static Map<String, Double> carriedOnLeg(@Nullable CargoTripEntity trip,
            @NonNull List<TourLeg> tour, int index) {
        if (trip == null || tour.isEmpty() || index < 0 || index >= tour.size()) {
            return Collections.emptyMap();
        }
        boolean detailed = false;
        for (TourLeg leg : tour) {
            if (!leg.load.isEmpty()) {
                detailed = true;
                break;
            }
        }
        if (!detailed) {
            return index == tour.size() - 1 ? cargoOf(trip) : Collections.emptyMap();
        }
        Map<String, Double> sum = new LinkedHashMap<>();
        for (int i = 0; i < index; i++) {
            TourLeg leg = tour.get(i);
            if (leg.toWarehouse || leg.load.isEmpty()) {
                continue;
            }
            for (Map.Entry<String, Double> e : leg.load.entrySet()) {
                Double prev = sum.get(e.getKey());
                sum.put(e.getKey(), (prev == null ? 0.0 : prev) + e.getValue());
            }
        }
        return sum.isEmpty() ? Collections.emptyMap() : Collections.unmodifiableMap(sum);
    }

    @NonNull
    public static List<DrawnLeg> futureCollectRoutes(@Nullable CargoTripEntity trip) {
        List<TourLeg> legs = collectTour(trip);
        List<DrawnLeg> out = new ArrayList<>();
        for (TourLeg leg : legs) {
            if (leg.current) {
                continue;
            }
            List<double[]> pts = decodeTour(leg.polyline);
            if (pts.size() < 2) {
                pts = Arrays.asList(
                        new double[] {leg.fromLat, leg.fromLng},
                        new double[] {leg.toLat, leg.toLng});
            }
            out.add(new DrawnLeg(pts, leg.roadKinds));
        }
        return out;
    }

    private static final Set<String> STRAIGHT_GAVE_UP =
            ConcurrentHashMap.newKeySet();

    private static void repairCargoGeometryBlocking(@NonNull Context context,
            @Nullable List<CargoTripEntity> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        for (CargoTripEntity trip : rows) {
            if (trip == null || trip.id == null || trip.id.isEmpty()
                    || CargoTripEntity.LEG_HAUL_SHIP.equals(trip.legRole)
                    || "gap".equals(trip.routeRoadKinds)
                    || CargoTripRules.routePoints(trip).size() > 2
                    || STRAIGHT_GAVE_UP.contains(trip.id)) {
                continue;
            }
            RoadPath path = LocalGraphHopper.route(context, trip.originLat, trip.originLng,
                    trip.destLat, trip.destLng);
            if (path == null || !path.followsRoads()) {
                LocalGraphHopper.Diag diag = LocalGraphHopper.lastDiag();
                if (diag != null && diag.kind != LocalGraphHopper.Kind.GRAPH_MISSING
                        && diag.kind != LocalGraphHopper.Kind.LOAD_FALSE
                        && diag.kind != LocalGraphHopper.Kind.LOAD_EXCEPTION) {
                    STRAIGHT_GAVE_UP.add(trip.id);
                }
                continue;
            }
            if (path.encoded != null && path.encoded.equals(trip.routePolyline)) {
                continue;
            }
            trip.routePolyline = path.encoded;
            trip.routeRoadKinds = path.edgeKinds;
            // Solo geometría: el servidor conserva fase, carga y reloj.
            updateIfPresent(context, trip, true);
        }
    }

    private static void packOpenCollectTours(@NonNull Context context,
            @Nullable List<CargoTripEntity> rows) {
        if (rows == null) {
            return;
        }
        for (CargoTripEntity trip : rows) {
            if (trip == null || !CargoTripEntity.KIND_COLLECT.equals(trip.kind)) {
                continue;
            }
            List<TourLeg> tour = readTour(trip);
            if (tour.size() < 2 && hasLaterStops(trip)) {
                boolean leavingWarehouse = near(
                        trip.originLat, trip.originLng, trip.returnLat, trip.returnLng);
                stampCollectTour(context, trip, stopsOf(trip), leavingWarehouse);
                tour = readTour(trip);
            }
            if (tour.size() > 1 && !packedTour(trip, tour)) {
                packCollectTour(trip);
                updateIfPresent(context, trip, false);
            }
        }
    }

    private static boolean repairCollectTripsBlocking(@NonNull Context context,
            @Nullable List<CargoTripEntity> rows) {
        if (rows == null || rows.isEmpty()) {
            return false;
        }
        boolean changed = false;
        for (CargoTripEntity trip : rows) {
            if (trip == null || !CargoTripEntity.KIND_COLLECT.equals(trip.kind)) {
                continue;
            }
            boolean touch = false;
            if (CargoTripEntity.PHASE_OUT.equals(trip.phase)
                    && near(trip.originLat, trip.originLng, trip.returnLat, trip.returnLng)) {
                HexParcel warehouse = trip.returnHexId != null
                        ? IberiaHexOverlayStore.findById(context.getApplicationContext(), trip.returnHexId)
                        : null;
                if (warehouse != null) {
                    String name = warehouseLabel(context, trip.ownerId, warehouse);
                    if (!name.equals(trip.originLabel)) {
                        trip.originLabel = name;
                        trip.returnLabel = name;
                        touch = true;
                    }
                }
            }
            if (readTour(trip).size() < 2 && hasLaterStops(trip)) {
                boolean leavingWarehouse = near(
                        trip.originLat, trip.originLng, trip.returnLat, trip.returnLng);
                stampCollectTour(context, trip, stopsOf(trip), leavingWarehouse);
                touch = true;
            }
            List<TourLeg> tourNow = readTour(trip);
            if (tourNow.size() > 1 && !packedTour(trip, tourNow)) {
                packCollectTour(trip);
                touch = true;
            }
            if (fillTourFlora(context, trip)) {
                touch = true;
            }
            if (touch) {
                saveTrip(context, trip);
                changed = true;
            }
        }
        return changed;
    }

    private static void stampCollectTour(@NonNull Context context, @NonNull CargoTripEntity trip,
            @NonNull List<CollectStop> stops, boolean leavingWarehouse) {
        if (stops.isEmpty()) {
            return;
        }
        try {
            JSONObject o = new JSONObject(trip.cargoJson != null ? trip.cargoJson : "{}");
            JSONArray tour = new JSONArray();
            CollectStop first = stops.get(0);
            putTourLeg(tour, trip.originLabel, trip.originHexId, leavingWarehouse,
                    first.label, first.hexId, false,
                    trip.originLat, trip.originLng, first.lat, first.lng,
                    trip.routePolyline, trip.routeRoadKinds, trip.durationMs, first.floraKey, first.load);
            double prevLat = first.lat;
            double prevLng = first.lng;
            String prevLabel = first.label;
            String prevHex = first.hexId;
            for (int i = 1; i < stops.size(); i++) {
                CollectStop stop = stops.get(i);
                RoadPath path = roadLeg(context, new double[] {prevLat, prevLng}, stop.lat, stop.lng);
                long ms = legDuration(context, trip, path);
                putTourLeg(tour, prevLabel, prevHex, false, stop.label, stop.hexId, false,
                        prevLat, prevLng, stop.lat, stop.lng,
                        path.encoded, path.edgeKinds, ms, stop.floraKey, stop.load);
                prevLat = stop.lat;
                prevLng = stop.lng;
                prevLabel = stop.label;
                prevHex = stop.hexId;
            }
            RoadPath back = roadLeg(context, new double[] {prevLat, prevLng},
                    trip.returnLat, trip.returnLng);
            long backMs = legDuration(context, trip, back);
            putTourLeg(tour, prevLabel, prevHex, false,
                    trip.returnLabel != null ? trip.returnLabel : "Obrador",
                    trip.returnHexId, true,
                    prevLat, prevLng, trip.returnLat, trip.returnLng,
                    back.encoded, back.edgeKinds, backMs, null, null);
            o.put("_tour", tour);
            trip.cargoJson = o.toString();
        } catch (JSONException ignored) {
        }
    }

    /**
     * Une almacén, apiarios y vuelta en un solo reloj. Si cada parada fuera un viaje
     * aparte, al llegar a la primera el camión se quedaba quieto hasta que el servidor
     * abriera la siguiente.
     */
    private static void packCollectTour(@NonNull CargoTripEntity trip) {
        List<TourLeg> legs = readTour(trip);
        if (legs.size() < 2) {
            return;
        }
        List<double[]> all = new ArrayList<>();
        long total = 0L;
        for (TourLeg leg : legs) {
            List<double[]> pts = new ArrayList<>(decodeTour(leg.polyline));
            if (pts.size() < 2) {
                pts.clear();
                pts.add(new double[] {leg.fromLat, leg.fromLng});
                pts.add(new double[] {leg.toLat, leg.toLng});
            }
            if (!all.isEmpty() && !pts.isEmpty()) {
                double[] prev = all.get(all.size() - 1);
                double[] first = pts.get(0);
                if (near(prev[0], prev[1], first[0], first[1])) {
                    pts.remove(0);
                }
            }
            all.addAll(pts);
            total += Math.max(0L, leg.durationMs);
        }
        if (all.size() < 2 || total <= 0L) {
            return;
        }
        TourLeg last = legs.get(legs.size() - 1);
        trip.destLat = last.toLat;
        trip.destLng = last.toLng;
        trip.destLabel = last.toLabel;
        trip.destHexId = trip.returnHexId != null ? trip.returnHexId : trip.destHexId;
        trip.routePolyline = EncodedPolyline.encode(all);
        trip.routeRoadKinds = null;
        trip.durationMs = total;
        try {
            JSONObject o = new JSONObject(trip.cargoJson != null ? trip.cargoJson : "{}");
            o.remove("_stops");
            trip.cargoJson = o.toString();
        } catch (JSONException ignored) {
        }
    }

    @NonNull
    private static List<CollectStop> stopsOf(@NonNull CargoTripEntity trip) {
        List<CollectStop> stops = new ArrayList<>();
        stops.add(new CollectStop(trip.destLat, trip.destLng, trip.destLabel, trip.destHexId));
        try {
            JSONObject o = new JSONObject(trip.cargoJson != null ? trip.cargoJson : "{}");
            JSONArray later = o.optJSONArray("_stops");
            if (later != null) {
                for (int i = 0; i < later.length(); i++) {
                    JSONObject s = later.getJSONObject(i);
                    stops.add(new CollectStop(s.optDouble("lat"), s.optDouble("lng"),
                            s.optString("label", "Apiario"), s.optString("hex", ""),
                            s.optString("flora", ""), readLoad(s.optJSONObject("load"))));
                }
            }
        } catch (JSONException ignored) {
        }
        return stops;
    }

    @NonNull
    private static Map<String, Double> flattenCargo(
            @Nullable Map<String, Map<String, Double>> byHive) {
        Map<String, Double> sum = new LinkedHashMap<>();
        if (byHive == null) {
            return sum;
        }
        for (Map<String, Double> cargo : byHive.values()) {
            if (cargo == null) {
                continue;
            }
            for (Map.Entry<String, Double> flora : cargo.entrySet()) {
                double kg = flora.getValue() == null ? 0.0 : flora.getValue();
                if (flora.getKey() == null || kg <= 1e-9) {
                    continue;
                }
                String key = HoneyMarketEngine.canonicalFloraKey(flora.getKey());
                Double prev = sum.get(key);
                sum.put(key, (prev == null ? 0.0 : prev) + kg);
            }
        }
        return sum;
    }

    @NonNull
    private static Map<String, Double> freezeLoad(@Nullable Map<String, Double> raw) {
        Map<String, Double> load = new LinkedHashMap<>();
        if (raw == null) {
            return Collections.emptyMap();
        }
        for (Map.Entry<String, Double> e : raw.entrySet()) {
            if (e.getKey() == null || e.getKey().startsWith("_")
                    || e.getValue() == null || e.getValue() <= 1e-9) {
                continue;
            }
            load.put(HoneyMarketEngine.canonicalFloraKey(e.getKey()), e.getValue());
        }
        return load.isEmpty() ? Collections.emptyMap() : Collections.unmodifiableMap(load);
    }

    @NonNull
    private static JSONObject loadJson(@NonNull Map<String, Double> load) throws JSONException {
        JSONObject o = new JSONObject();
        for (Map.Entry<String, Double> e : load.entrySet()) {
            o.put(e.getKey(), e.getValue());
        }
        return o;
    }

    @NonNull
    private static Map<String, Double> readLoad(@Nullable JSONObject raw) {
        if (raw == null) {
            return Collections.emptyMap();
        }
        Map<String, Double> load = new LinkedHashMap<>();
        Iterator<String> keys = raw.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            double kg = raw.optDouble(key, 0.0);
            if (key == null || key.startsWith("_") || kg <= 1e-9) {
                continue;
            }
            load.put(HoneyMarketEngine.canonicalFloraKey(key), kg);
        }
        return load.isEmpty() ? Collections.emptyMap() : Collections.unmodifiableMap(load);
    }

    @Nullable
    private static String dominantFlora(@Nullable Map<String, Map<String, Double>> byHive) {
        if (byHive == null || byHive.isEmpty()) {
            return null;
        }
        String best = null;
        double max = 0.0;
        for (Map<String, Double> cargo : byHive.values()) {
            if (cargo == null) {
                continue;
            }
            for (Map.Entry<String, Double> flora : cargo.entrySet()) {
                double kg = flora.getValue() == null ? 0.0 : flora.getValue();
                if (flora.getKey() == null || kg <= max) {
                    continue;
                }
                max = kg;
                best = HoneyMarketEngine.canonicalFloraKey(flora.getKey());
            }
        }
        return best;
    }

    /**
     * Recorridos ya en marcha no guardaban la miel de cada apiario. Se rellena
     * con el tipo que pecorean las colmenas de ese hex.
     */
    private static boolean fillTourFlora(@NonNull Context context, @NonNull CargoTripEntity trip) {
        try {
            JSONObject o = new JSONObject(trip.cargoJson != null ? trip.cargoJson : "{}");
            JSONArray tour = o.optJSONArray("_tour");
            if (tour == null) {
                return false;
            }
            boolean touch = false;
            for (int i = 0; i < tour.length(); i++) {
                JSONObject leg = tour.optJSONObject(i);
                if (leg == null || leg.optBoolean("whTo")) {
                    continue;
                }
                if (!leg.optString("flora", "").isEmpty()) {
                    continue;
                }
                String flora = floraOnHex(context, trip.ownerId, leg.optString("toHex", ""));
                if (flora == null) {
                    continue;
                }
                leg.put("flora", flora);
                touch = true;
            }
            if (touch) {
                trip.cargoJson = o.toString();
            }
            return touch;
        } catch (JSONException e) {
            return false;
        }
    }

    @Nullable
    private static String floraOnHex(@NonNull Context context, @Nullable String ownerId,
            @Nullable String hexId) {
        if (hexId == null || hexId.isEmpty()) {
            return null;
        }
        List<HiveEntity> hives = AppDatabase.getInstance(context).hiveDao().getByHexIdSync(hexId);
        if (hives == null) {
            return null;
        }
        String best = null;
        int bestCount = 0;
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (HiveEntity hive : hives) {
            if (hive == null || hive.inWarehouse) {
                continue;
            }
            if (ownerId != null && !ownerId.equals(hive.ownerId)) {
                continue;
            }
            String flora = HiveHoneyStocks.dominantFlora(hive);
            if (flora == null || flora.isEmpty()) {
                continue;
            }
            int count = counts.getOrDefault(flora, 0) + 1;
            counts.put(flora, count);
            if (count > bestCount) {
                bestCount = count;
                best = flora;
            }
        }
        return best;
    }

    private static boolean hasLaterStops(@NonNull CargoTripEntity trip) {
        try {
            JSONObject o = new JSONObject(trip.cargoJson != null ? trip.cargoJson : "{}");
            JSONArray later = o.optJSONArray("_stops");
            return later != null && later.length() > 0;
        } catch (JSONException e) {
            return false;
        }
    }

    /** La vuelta por carretera, para que el servidor no la dibuje en línea recta. */
    static void rememberReturnRoad(@NonNull CargoTripEntity trip, @Nullable RoadPath back,
            double fromLat, double fromLng, double toLat, double toLng, long durationMs) {
        if (back == null || !back.followsRoads()) {
            return;
        }
        try {
            JSONObject o = new JSONObject(trip.cargoJson != null ? trip.cargoJson : "{}");
            JSONArray tour = o.optJSONArray("_tour");
            if (tour == null) {
                tour = new JSONArray();
            }
            for (int i = 0; i < tour.length(); i++) {
                JSONObject leg = tour.optJSONObject(i);
                if (leg != null
                        && near(leg.optDouble("fromLat"), leg.optDouble("fromLng"), fromLat, fromLng)
                        && near(leg.optDouble("toLat"), leg.optDouble("toLng"), toLat, toLng)
                        && !leg.optString("poly", "").isEmpty()) {
                    return;
                }
            }
            putTourLeg(tour, trip.destLabel, trip.destHexId, false,
                    trip.returnLabel, trip.returnHexId, true,
                    fromLat, fromLng, toLat, toLng,
                    back.encoded, back.edgeKinds, durationMs, null, null);
            o.put("_tour", tour);
            trip.cargoJson = o.toString();
        } catch (JSONException ignored) {
        }
    }

    private static void putTourLeg(@NonNull JSONArray tour, @Nullable String from, @Nullable String fromHex,
            boolean fromWarehouse, @Nullable String to, @Nullable String toHex, boolean toWarehouse,
            double fromLat, double fromLng, double toLat, double toLng,
            @Nullable String poly, @Nullable String kinds, long ms, @Nullable String flora,
            @Nullable Map<String, Double> load)
            throws JSONException {
        JSONObject leg = new JSONObject();
        leg.put("from", from != null ? from : "");
        leg.put("fromHex", fromHex != null ? fromHex : "");
        leg.put("whFrom", fromWarehouse);
        leg.put("to", to != null ? to : "");
        leg.put("toHex", toHex != null ? toHex : "");
        leg.put("whTo", toWarehouse);
        leg.put("fromLat", fromLat);
        leg.put("fromLng", fromLng);
        leg.put("toLat", toLat);
        leg.put("toLng", toLng);
        leg.put("poly", poly != null ? poly : "");
        leg.put("kinds", kinds != null ? kinds : "");
        leg.put("ms", ms);
        if (flora != null && !flora.isEmpty()) {
            leg.put("flora", flora);
        }
        if (load != null && !load.isEmpty()) {
            leg.put("load", loadJson(load));
        }
        tour.put(leg);
    }

    @Nullable
    private static RoadPath pathFromTour(@NonNull JSONObject cargo, double fromLat, double fromLng,
            double toLat, double toLng) {
        JSONArray tour = cargo.optJSONArray("_tour");
        if (tour == null) {
            return null;
        }
        for (int i = 0; i < tour.length(); i++) {
            JSONObject leg = tour.optJSONObject(i);
            if (leg == null
                    || !near(leg.optDouble("fromLat"), leg.optDouble("fromLng"), fromLat, fromLng)
                    || !near(leg.optDouble("toLat"), leg.optDouble("toLng"), toLat, toLng)) {
                continue;
            }
            String poly = leg.optString("poly", "");
            if (poly.isEmpty()) {
                return null;
            }
            return RoadPath.fromEncoded(poly, 0, leg.optString("kinds", ""));
        }
        return null;
    }

    @NonNull
    private static List<TourLeg> readTour(@NonNull CargoTripEntity trip) {
        List<TourLeg> out = new ArrayList<>();
        try {
            JSONObject o = new JSONObject(trip.cargoJson != null ? trip.cargoJson : "{}");
            JSONArray tour = o.optJSONArray("_tour");
            if (tour == null) {
                return out;
            }
            for (int i = 0; i < tour.length(); i++) {
                JSONObject leg = tour.optJSONObject(i);
                if (leg == null) {
                    continue;
                }
                out.add(new TourLeg(
                        leg.optString("from", "Origen"),
                        leg.optString("to", "Destino"),
                        leg.optBoolean("whFrom"),
                        leg.optBoolean("whTo"),
                        leg.optLong("ms"),
                        false, false,
                        leg.optString("poly", ""),
                        leg.optString("kinds", ""),
                        leg.optDouble("fromLat"),
                        leg.optDouble("fromLng"),
                        leg.optDouble("toLat"),
                        leg.optDouble("toLng"),
                        leg.optString("flora", ""),
                        readLoad(leg.optJSONObject("load"))));
            }
        } catch (JSONException ignored) {
        }
        return out;
    }

    @NonNull
    private static List<TourLeg> synthesizeTour(@NonNull CargoTripEntity trip) {
        if (!hasLaterStops(trip)) {
            return Collections.emptyList();
        }
        List<CollectStop> stops = stopsOf(trip);
        List<TourLeg> out = new ArrayList<>();
        double prevLat = trip.originLat;
        double prevLng = trip.originLng;
        String prevLabel = trip.originLabel != null ? trip.originLabel : "Obrador";
        boolean fromWarehouse = true;
        for (int i = 0; i < stops.size(); i++) {
            CollectStop stop = stops.get(i);
            long ms = i == 0 ? trip.durationMs : FleetRules.durationMs(
                    TranshumanceRules.haversineKm(prevLat, prevLng, stop.lat, stop.lng), 70);
            String poly = i == 0 ? trip.routePolyline : "";
            String kinds = i == 0 ? trip.routeRoadKinds : "";
            out.add(new TourLeg(prevLabel, stop.label, fromWarehouse, false, ms, false, false,
                    poly, kinds, prevLat, prevLng, stop.lat, stop.lng, stop.floraKey, stop.load));
            prevLat = stop.lat;
            prevLng = stop.lng;
            prevLabel = stop.label;
            fromWarehouse = false;
        }
        long back = FleetRules.durationMs(
                TranshumanceRules.haversineKm(prevLat, prevLng, trip.returnLat, trip.returnLng), 70);
        out.add(new TourLeg(prevLabel,
                trip.returnLabel != null ? trip.returnLabel : "Obrador",
                false, true, back, false, false, "", "",
                prevLat, prevLng, trip.returnLat, trip.returnLng, null, null));
        return out;
    }

    @NonNull
    private static List<double[]> decodeTour(@Nullable String encoded) {
        if (encoded == null || encoded.isEmpty()) {
            return new ArrayList<>();
        }
        try {
            return EncodedPolyline.decode(encoded);
        } catch (RuntimeException ignored) {
            return new ArrayList<>();
        }
    }

    private static long legDuration(@NonNull Context context, @NonNull CargoTripEntity trip,
            @NonNull RoadPath path) {
        FleetStore.Vehicle vehicle = FleetStore.vehicle(context, trip.ownerId, trip.vehicleId);
        double kmh = 70.0;
        if (vehicle != null) {
            kmh = FleetRules.speedKmh(vehicle.rulesKind(), vehicle.level);
        }
        double km = path.distanceKm > 1e-6 ? path.distanceKm : 0.0;
        if (km <= 1e-6) {
            return TruckTripRules.durationMs(path);
        }
        return FleetRules.durationMs(km, kmh);
    }

    private static boolean near(double lat, double lng, double otherLat, double otherLng) {
        return TranshumanceRules.haversineKm(lat, lng, otherLat, otherLng) < 0.4;
    }

    @NonNull
    private static String warehouseLabel(@NonNull Context context, @Nullable String ownerId,
            @NonNull HexParcel warehouse) {
        if (ownerId != null && !ownerId.isEmpty()) {
            List<HexParcelOwnershipEntity> rows = AppDatabase.getInstance(context.getApplicationContext())
                    .hexParcelOwnershipDao().listByHexAndOwnerSync(warehouse.id, ownerId);
            if (rows != null) {
                for (HexParcelOwnershipEntity row : rows) {
                    if (row == null || !row.hasWarehouse) {
                        continue;
                    }
                    if (row.parcelName != null && !row.parcelName.trim().isEmpty()) {
                        return row.parcelName.trim();
                    }
                }
            }
        }
        if (warehouse.placeName != null && !warehouse.placeName.trim().isEmpty()) {
            return warehouse.placeName.trim();
        }
        return "Obrador";
    }

    /** Elimina una fila local de carga ya resuelta por un efecto del servidor. */
    static void forgetLocalTrip(@NonNull Context context, @Nullable String tripId) {
        if (tripId != null && !tripId.isEmpty()) {
            dao(context).delete(tripId);
        }
    }

    private static void saveTrip(@NonNull Context context, @NonNull CargoTripEntity trip) {
        boolean existed = trip.id != null && dao(context).getById(trip.id) != null;
        if (GameServer.enabled() && !GameServer.pushCargo(context, trip)) {
            if (!existed) {
                throw new IllegalStateException(
                        "El servidor no ha aceptado el viaje de la comanda.");
            }
            return;
        }
        dao(context).upsert(trip);
    }

    /** No reescribe un viaje que la sincronización acaba de quitar. */
    private static void updateIfPresent(@NonNull Context context, @NonNull CargoTripEntity trip,
            boolean geometryOnly) {
        if (trip.id == null || trip.id.isEmpty()) {
            return;
        }
        boolean[] present = {false};
        GameServer.exclusive(() -> {
            if (dao(context).getById(trip.id) == null) {
                return;
            }
            dao(context).upsert(trip);
            present[0] = true;
        });
        if (present[0]) {
            GameServer.pushCargo(context, trip, geometryOnly);
        }
    }

    private static CargoTripDao dao(@NonNull Context context) {
        return AppDatabase.getInstance(context.getApplicationContext()).cargoTripDao();
    }
}
