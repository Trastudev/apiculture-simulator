package com.apiculture.simulator.data.repository;

import android.content.Context;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;

import com.apiculture.simulator.data.local.AppDatabase;
import com.apiculture.simulator.data.local.dao.HiveDao;
import com.apiculture.simulator.data.local.dao.TruckTripDao;
import com.apiculture.simulator.data.local.entity.HiveEntity;
import com.apiculture.simulator.data.local.entity.TruckTripEntity;
import com.apiculture.simulator.data.remote.OpenMeteoElevation;
import com.apiculture.simulator.domain.game.FleetRules;
import com.apiculture.simulator.domain.game.GameCalendar;
import com.apiculture.simulator.domain.parcel.HexApiary;
import com.apiculture.simulator.domain.game.TruckTripRules;
import com.apiculture.simulator.domain.map.LocalGraphHopper;
import com.apiculture.simulator.domain.map.RoadPath;
import com.apiculture.simulator.presentation.common.RouteErrorDialog;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.time.LocalDate;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class TruckLiveTrips {

    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final ExecutorService REROUTE = Executors.newSingleThreadExecutor();
    private static final Set<String> rerouteTried = new HashSet<>();
    private static final Map<String, TruckTripEntity> CACHE = new ConcurrentHashMap<>();

    private TruckLiveTrips() {
    }

    public enum StartResult {
        /** Extra off: hay que poner ya lat/lng/hex. */
        TELEPORT,
        STARTED,
        TOO_LATE,
        ALREADY_TRAVELING,
        NO_TRUCK
    }

    /**
     * Si el extra está activo, deja la colmena en origen y registra el viaje.
     * @return {@link StartResult#STARTED} si no hay que teletransportar lat/lng/hex.
     */
    public static boolean startIfEnabled(@NonNull Context context, @NonNull HiveEntity hive,
            double destLat, double destLng, @Nullable String destHexId) {
        StartResult r = start(context, hive, destLat, destLng, destHexId);
        return r == StartResult.STARTED;
    }

    @NonNull
    public static StartResult start(@NonNull Context context, @NonNull HiveEntity hive,
            double destLat, double destLng, @Nullable String destHexId) {
        if (hive.id == null) {
            return StartResult.TELEPORT;
        }
        if (hasActive(context, hive.id)) {
            return StartResult.ALREADY_TRAVELING;
        }
        if (!TruckLivePrefs.isEnabled(context) && !GameServer.enabled()) {
            return StartResult.TELEPORT;
        }
        List<com.apiculture.simulator.data.local.entity.HexParcelOwnershipEntity> homes =
                AppDatabase.getInstance(context).hexParcelOwnershipDao().getWarehousesForOwnerSync(hive.ownerId);
        String homeHex = hive.hexId;
        if (homes != null) {
            for (com.apiculture.simulator.data.local.entity.HexParcelOwnershipEntity row : homes) {
                if (row != null && row.hasWarehouse) {
                    homeHex = row.hexId;
                    break;
                }
            }
        }
        FleetStore.ensureStarter(context, hive.ownerId, homeHex);
        FleetStore.Vehicle truck = FleetStore.reserveHive(context, hive.ownerId, hive.id);
        if (truck == null) {
            return StartResult.NO_TRUCK;
        }
        RoadPath path = LocalGraphHopper.route(context, hive.lat, hive.lng, destLat, destLng);
        if (path == null || !path.followsRoads()) {
            RouteErrorDialog.show(context, LocalGraphHopper.lastDiag());
        }
        TruckTripEntity trip = TruckTripRules.create(hive, destLat, destLng, destHexId, path);
        double km = path != null && path.distanceKm > 0 ? path.distanceKm : 0;
        trip.durationMs = FleetRules.durationMs(km, FleetRules.speedKmh(FleetRules.Kind.TRUCK, truck.level));
        if (TruckTripRules.arrivesAfterNextDailyTick(trip)) {
            FleetStore.releaseHive(context, hive.ownerId, hive.id);
            return StartResult.TOO_LATE;
        }
        persist(context, trip);
        return StartResult.STARTED;
    }

    @Nullable
    public static TruckTripEntity get(@NonNull Context context, @Nullable String hiveId) {
        if (hiveId == null || hiveId.isEmpty()) {
            return null;
        }
        if (isMainThread()) {
            return CACHE.get(hiveId);
        }
        TruckTripEntity row = dao(context).getByHiveId(hiveId);
        if (row != null && row.hiveId != null) {
            CACHE.put(row.hiveId, row);
        } else {
            CACHE.remove(hiveId);
        }
        return row;
    }

    public static void completeDue(@NonNull Context context) {
        AppDatabase db = AppDatabase.getInstance(context);
        int dayKey = GameCalendar.toDayKey(LocalDate.now(GameCalendar.userTimeZone()));
        completeDue(context, db.hiveDao(), dayKey);
    }

    public static void completeDue(@NonNull Context context, @NonNull HiveDao hiveDao, int dayKey) {
        if (GameServer.enabled()) {
            GameServer.syncBlocking(context);
        }
        TruckTripDao trips = dao(context);
        List<TruckTripEntity> rows = trips.getAllSync();
        if (rows == null || rows.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        for (TruckTripEntity trip : rows) {
            HiveEntity hive = hiveDao.getHiveByIdSync(trip.hiveId);
            if (!isDue(hive, trip, dayKey, now)) {
                continue;
            }
            if (hive != null) {
                snapToDestination(context, hive, trip);
                hiveDao.upsert(hive);
                persistHiveCloud(context, hive);
            }
            FleetStore.releaseHive(context, trip.ownerId, trip.hiveId);
            forget(trips, trip.hiveId);
        }
    }

    /** Aplica llegada al objeto en memoria (tick diario). */
    public static boolean applyDue(@NonNull Context context, @NonNull HiveEntity hive, int dayKey) {
        if (GameServer.enabled() || hive.id == null) {
            return false;
        }
        TruckTripDao trips = dao(context);
        TruckTripEntity trip = trips.getByHiveId(hive.id);
        if (trip == null) {
            return false;
        }
        if (!isDue(hive, trip, dayKey, System.currentTimeMillis())) {
            return false;
        }
        snapToDestination(context, hive, trip);
        FleetStore.releaseHive(context, hive.ownerId, hive.id);
        forget(trips, trip.hiveId);
        return true;
    }

    /**
     * Cancela el viaje de ida: el camión da media vuelta y recorre el tramo de vuelta.
     * Si ya iba al origen, no hace nada.
     * @return {@code true} si se ha invertido la ruta
     */
    public static boolean cancel(@NonNull Context context, @Nullable String hiveId) {
        if (hiveId == null || hiveId.isEmpty()) {
            return false;
        }
        TruckTripEntity trip = get(context, hiveId);
        if (trip == null) {
            return false;
        }
        HiveEntity hive = null;
        if (!isMainThread()) {
            hive = AppDatabase.getInstance(context).hiveDao().getHiveByIdSync(hiveId);
        }
        if (TruckTripRules.isHeadingHome(trip, hive)) {
            return false;
        }
        if (!TruckTripRules.applyUTurnHome(trip, System.currentTimeMillis(),
                hive != null ? hive.hexId : trip.destHexId)) {
            return false;
        }
        persist(context, trip, true);
        return true;
    }

    @NonNull
    public static Set<String> activeHiveIds(@NonNull List<TruckTripEntity> trips) {
        Set<String> ids = new HashSet<>();
        if (trips == null) {
            return ids;
        }
        for (TruckTripEntity trip : trips) {
            if (trip != null && trip.hiveId != null && !trip.hiveId.isEmpty()) {
                ids.add(trip.hiveId);
            }
        }
        return ids;
    }

    private static boolean isDue(@Nullable HiveEntity hive, TruckTripEntity trip, int dayKey, long now) {
        boolean gameArrived = hive != null && hive.transhumanceArrivesDayKey > 0
                && dayKey >= hive.transhumanceArrivesDayKey;
        return gameArrived || TruckTripRules.wallClockDone(trip, now);
    }

    private static void snapToDestination(@NonNull Context context, @NonNull HiveEntity hive,
            @NonNull TruckTripEntity trip) {
        hive.lat = trip.destLat;
        hive.lng = trip.destLng;
        if (trip.destHexId != null && !trip.destHexId.isEmpty()) {
            hive.hexId = trip.destHexId;
        }
        hive.siteId = HexApiary.resolveSiteIdAt(
                IberiaHexOverlayStore.findById(context, hive.hexId),
                AppDatabase.getInstance(context).hexParcelOwnershipDao()
                        .listByHexAndOwnerSync(hive.hexId, hive.ownerId),
                hive.hexId, hive.lat, hive.lng);
        hive.elevationMeters = OpenMeteoElevation.resolveMetersPersistedBlocking(hive.lat, hive.lng);
        if (trip.destFlora != null && !trip.destFlora.trim().isEmpty()) {
            hive.floraType = com.apiculture.simulator.domain.market.HoneyMarketEngine
                    .canonicalFloraKey(trip.destFlora);
        }
    }

    public static void attachDestFlora(@NonNull Context context, @Nullable String hiveId,
            @Nullable String flora) {
        if (hiveId == null || flora == null || flora.trim().isEmpty()) {
            return;
        }
        TruckTripEntity trip = dao(context).getByHiveId(hiveId);
        if (trip == null) {
            return;
        }
        trip.destFlora = flora.trim();
        persist(context, trip);
    }

    public static boolean hasActive(@NonNull Context context, @Nullable String hiveId) {
        if (hiveId == null) {
            return false;
        }
        return get(context, hiveId) != null;
    }

    /** Recalcula por carretera los viajes que se guardaron como línea recta. */
    public static void rerouteStraightIfNeeded(@NonNull Context context,
            @Nullable List<TruckTripEntity> trips) {
        if (trips == null || trips.isEmpty()) {
            return;
        }
        Context app = context.getApplicationContext();
        REROUTE.execute(() -> {
            TruckTripDao db = dao(app);
            for (TruckTripEntity trip : trips) {
                if (trip == null || trip.hiveId == null || trip.hiveId.isEmpty()) {
                    continue;
                }
                if (TruckTripRules.routePoints(trip).size() > 2) {
                    continue;
                }
                synchronized (rerouteTried) {
                    if (!rerouteTried.add(trip.hiveId)) {
                        continue;
                    }
                }
                RoadPath path = LocalGraphHopper.route(app, trip.originLat, trip.originLng,
                        trip.destLat, trip.destLng);
                if (path == null || !path.followsRoads()) {
                    synchronized (rerouteTried) {
                        rerouteTried.remove(trip.hiveId);
                    }
                    continue;
                }
                TruckTripEntity latest = db.getByHiveId(trip.hiveId);
                if (latest == null) {
                    synchronized (rerouteTried) {
                        rerouteTried.remove(trip.hiveId);
                    }
                    continue;
                }
                String encoded = path.encoded;
                long duration = TruckTripRules.durationMs(path);
                if (encoded != null && encoded.equals(latest.routePolyline)
                        && latest.durationMs == duration
                        && (path.edgeKinds == null ? "" : path.edgeKinds)
                        .equals(latest.routeRoadKinds == null ? "" : latest.routeRoadKinds)) {
                    synchronized (rerouteTried) {
                        rerouteTried.remove(trip.hiveId);
                    }
                    continue;
                }
                latest.routePolyline = encoded;
                latest.routeRoadKinds = path.edgeKinds;
                // Con servidor activo, el reloj y la duración son autoritativos;
                // el rerout solo corrige la geometría que se dibuja en el mapa.
                if (!GameServer.enabled()) {
                    latest.durationMs = duration;
                }
                persist(app, latest);
                synchronized (rerouteTried) {
                    rerouteTried.remove(trip.hiveId);
                }
            }
        });
    }

    @NonNull
    public static LiveData<List<TruckTripEntity>> observe(@NonNull Context context) {
        MediatorLiveData<List<TruckTripEntity>> live = new MediatorLiveData<>();
        live.addSource(dao(context).observeAll(), rows -> {
            replaceCache(rows);
            live.setValue(rows != null ? rows : Collections.emptyList());
        });
        return live;
    }

    public static void removeAllForOwnerBlocking(@NonNull Context context, @Nullable String ownerId) {
        if (ownerId == null || ownerId.isEmpty()) {
            return;
        }
        TruckTripDao trips = dao(context);
        List<TruckTripEntity> rows = trips.getAllSync();
        if (rows != null) {
            for (TruckTripEntity trip : rows) {
                if (trip != null && trip.hiveId != null && ownerId.equals(trip.ownerId)) {
                    CACHE.remove(trip.hiveId);
                }
            }
        }
        trips.deleteAllForOwner(ownerId);
    }

    @NonNull
    public static List<TruckTripEntity> allSync(@NonNull Context context) {
        List<TruckTripEntity> rows = dao(context).getAllSync();
        return rows != null ? rows : Collections.emptyList();
    }

    private static void persistHiveCloud(@NonNull Context context, @NonNull HiveEntity hive) {
        if (hive.id == null || hive.id.isEmpty()) {
            return;
        }
        Context app = context.getApplicationContext();
        if (app instanceof com.apiculture.simulator.ApicultureApp) {
            ((com.apiculture.simulator.ApicultureApp) app).getHiveRepository().publishHiveToServer(hive);
        }
    }

    private static void persist(@NonNull Context context, @NonNull TruckTripEntity trip) {
        persist(context, trip, false);
    }

    private static void persist(@NonNull Context context, @NonNull TruckTripEntity trip,
            boolean allowTimingReset) {
        if (trip.hiveId != null && !trip.hiveId.isEmpty()) {
            CACHE.put(trip.hiveId, trip);
        }
        Context app = context.getApplicationContext();
        if (isMainThread()) {
            IO.execute(() -> persistConfirmed(app, trip, allowTimingReset));
        } else {
            persistConfirmed(app, trip, allowTimingReset);
        }
    }

    private static void persistConfirmed(@NonNull Context app, @NonNull TruckTripEntity trip,
            boolean allowTimingReset) {
        boolean existed = trip.hiveId != null && dao(app).getByHiveId(trip.hiveId) != null;
        if (GameServer.enabled() && !GameServer.pushTruck(app, trip, allowTimingReset)) {
            if (!existed && trip.hiveId != null) {
                CACHE.remove(trip.hiveId);
            }
            return;
        }
        dao(app).upsert(trip);
    }

    public static void applyArrival(@NonNull Context context, @Nullable String hiveId,
            double lat, double lng, @Nullable String hexId, @Nullable String flora) {
        if (hiveId == null || hiveId.isEmpty() || isMainThread()) {
            return;
        }
        HiveEntity hive = AppDatabase.getInstance(context).hiveDao().getHiveByIdSync(hiveId);
        if (hive == null) {
            return;
        }
        TruckTripEntity trip = new TruckTripEntity();
        trip.destLat = lat;
        trip.destLng = lng;
        trip.destHexId = hexId;
        trip.destFlora = flora;
        snapToDestination(context, hive, trip);
        AppDatabase.getInstance(context).hiveDao().upsert(hive);
        persistHiveCloud(context, hive);
    }

    /** Elimina una fila local ya resuelta por el efecto autoritativo del servidor. */
    static void forgetLocalTrip(@NonNull Context context, @Nullable String hiveId) {
        if (hiveId == null || hiveId.isEmpty()) {
            return;
        }
        CACHE.remove(hiveId);
        dao(context).delete(hiveId);
    }

    private static void forget(@NonNull TruckTripDao trips, @Nullable String hiveId) {
        if (hiveId != null) {
            CACHE.remove(hiveId);
            trips.delete(hiveId);
        }
    }

    private static void replaceCache(@Nullable List<TruckTripEntity> rows) {
        Set<String> keep = new HashSet<>();
        if (rows != null) {
            for (TruckTripEntity trip : rows) {
                if (trip != null && trip.hiveId != null && !trip.hiveId.isEmpty()) {
                    CACHE.put(trip.hiveId, trip);
                    keep.add(trip.hiveId);
                }
            }
        }
        CACHE.keySet().retainAll(keep);
    }

    private static boolean isMainThread() {
        return Looper.getMainLooper() == Looper.myLooper();
    }

    private static TruckTripDao dao(Context context) {
        return AppDatabase.getInstance(context).truckTripDao();
    }
}
