package com.apiculture.simulator.domain.game;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.apiculture.simulator.data.local.entity.CargoTripEntity;
import com.apiculture.simulator.domain.map.EncodedPolyline;
import com.apiculture.simulator.domain.map.RoadPath;
import com.apiculture.simulator.domain.map.SeaRoute;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class CargoTripRules {

    private CargoTripRules() {
    }

    public static void applyPath(@NonNull CargoTripEntity trip, @Nullable RoadPath path,
            double originLat, double originLng, double destLat, double destLng) {
        if (path == null || path.points.size() < 2) {
            path = RoadPath.geodesic(originLat, originLng, destLat, destLng);
        }
        trip.originLat = originLat;
        trip.originLng = originLng;
        trip.destLat = destLat;
        trip.destLng = destLng;
        trip.routePolyline = path.encoded;
        trip.routeRoadKinds = path.edgeKinds;
        trip.startEpochMs = System.currentTimeMillis();
        trip.durationMs = TruckTripRules.durationMs(path);
    }

    public static double progress(@Nullable CargoTripEntity trip, long nowMs) {
        if (trip == null || trip.durationMs <= 0) {
            return 1;
        }
        return Math.max(0, Math.min(1, (nowMs - trip.startEpochMs) / (double) trip.durationMs));
    }

    public static boolean wallClockDone(@Nullable CargoTripEntity trip, long nowMs) {
        return trip != null && nowMs >= trip.startEpochMs + trip.durationMs;
    }

    public static long remainingMs(@Nullable CargoTripEntity trip, long nowMs) {
        if (trip == null) {
            return 0L;
        }
        return Math.max(0L, trip.startEpochMs + Math.max(0L, trip.durationMs) - nowMs);
    }

    public static boolean arrivesAfterNextDailyTick(@Nullable CargoTripEntity trip) {
        return trip != null && TruckTripRules.arrivesAfterNextDailyTick(trip.startEpochMs, trip.durationMs);
    }

    @NonNull
    public static List<double[]> routePoints(@Nullable CargoTripEntity trip) {
        if (trip == null) {
            return Collections.emptyList();
        }
        List<double[]> pts = decode(trip.routePolyline);
        if (pts.size() >= 2) {
            return pts;
        }
        return Arrays.asList(
                new double[]{trip.originLat, trip.originLng},
                new double[]{trip.destLat, trip.destLng});
    }

    public static double[] position(@Nullable CargoTripEntity trip, long nowMs) {
        if (trip == null) {
            return new double[]{0, 0};
        }
        if (CargoTripEntity.LEG_HAUL_SHIP.equals(trip.legRole)) {
            return SeaRoute.alongVisible(routePoints(trip), progress(trip, nowMs),
                    trip.originHexId, trip.destHexId);
        }
        return TruckTripRules.along(routePoints(trip), progress(trip, nowMs));
    }

    @NonNull
    private static List<double[]> decode(@Nullable String encoded) {
        if (encoded == null || encoded.isEmpty()) {
            return new ArrayList<>();
        }
        try {
            return EncodedPolyline.decode(encoded);
        } catch (RuntimeException ignored) {
            return new ArrayList<>();
        }
    }
}
