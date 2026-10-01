package com.apiculture.simulator.domain.game;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.apiculture.simulator.data.local.entity.HiveEntity;
import com.apiculture.simulator.data.local.entity.TruckTripEntity;
import com.apiculture.simulator.domain.map.EncodedPolyline;
import com.apiculture.simulator.domain.map.RoadKind;
import com.apiculture.simulator.domain.map.RoadPath;

import java.util.ArrayList;
import java.util.List;

/** Viaje de transhumancia a ~70 km/h reales, siguiendo la polyline de carretera. */
public final class TruckTripRules {

    public static final double AVG_KMH = 70.0;
    public static final long MIN_DURATION_MS = 60_000L;

    private static String cachedEncoded;
    private static List<double[]> cachedPoints;

    private TruckTripRules() {
    }

    public static long durationMs(double km) {
        if (km <= 0) {
            return MIN_DURATION_MS;
        }
        long ms = Math.round(km / AVG_KMH * 3_600_000.0);
        return Math.max(MIN_DURATION_MS, ms);
    }

    @NonNull
    public static TruckTripEntity create(HiveEntity hive, double destLat, double destLng, String destHexId,
            @Nullable RoadPath path) {
        if (path == null || path.points.size() < 2) {
            path = RoadPath.geodesic(hive.lat, hive.lng, destLat, destLng);
        }
        TruckTripEntity t = new TruckTripEntity();
        t.hiveId = hive.id;
        t.ownerId = hive.ownerId;
        t.originLat = hive.lat;
        t.originLng = hive.lng;
        t.destLat = destLat;
        t.destLng = destLng;
        t.destHexId = destHexId;
        t.startEpochMs = System.currentTimeMillis();
        t.routePolyline = path.encoded;
        t.routeRoadKinds = path.edgeKinds;
        t.durationMs = durationMs(path);
        return t;
    }

    public static long durationMs(@Nullable RoadPath path) {
        if (path == null || path.points.size() < 2) {
            return MIN_DURATION_MS;
        }
        return durationMs(path.points, path.edgeKinds, path.distanceKm);
    }

    public static long durationMs(@NonNull List<double[]> points, @Nullable String kinds,
            double fallbackKm) {
        if (RoadKind.typed(kinds) && points.size() >= 2) {
            double hours = 0;
            for (int i = 1; i < points.size(); i++) {
                double[] a = points.get(i - 1);
                double[] b = points.get(i);
                double km = TranshumanceRules.haversineKm(a[0], a[1], b[0], b[1]);
                hours += km / RoadKind.at(kinds, i - 1).cruiseKmh;
            }
            if (hours > 0) {
                return Math.max(MIN_DURATION_MS, Math.round(hours * 3_600_000.0));
            }
        }
        double km = fallbackKm > 0 ? fallbackKm : RoadPath.pathKm(points);
        return durationMs(km);
    }

    public static double progress(TruckTripEntity trip, long nowMs) {
        if (trip == null || trip.durationMs <= 0) {
            return 1;
        }
        return Math.max(0, Math.min(1, (nowMs - trip.startEpochMs) / (double) trip.durationMs));
    }

    public static boolean wallClockDone(TruckTripEntity trip, long nowMs) {
        return trip != null && nowMs >= trip.startEpochMs + trip.durationMs;
    }

    public static long etaEpochMs(@Nullable TruckTripEntity trip) {
        if (trip == null) {
            return 0L;
        }
        return trip.startEpochMs + Math.max(0L, trip.durationMs);
    }

    public static long remainingMs(@Nullable TruckTripEntity trip, long nowMs) {
        if (trip == null) {
            return 0L;
        }
        return Math.max(0L, etaEpochMs(trip) - nowMs);
    }

    public static boolean arrivesAfterNextDailyTick(long startEpochMs, long durationMs) {
        return startEpochMs + Math.max(0L, durationMs) > GameCalendar.nextProductionEpochMs(startEpochMs);
    }

    public static boolean arrivesAfterNextDailyTick(@Nullable TruckTripEntity trip) {
        return trip != null && arrivesAfterNextDailyTick(trip.startEpochMs, trip.durationMs);
    }

    /** El destino del viaje es el apiario actual de la colmena (vuelta al origen). */
    public static boolean isHeadingHome(@Nullable TruckTripEntity trip, @Nullable HiveEntity hive) {
        if (trip == null || hive == null) {
            return false;
        }
        return TranshumanceRules.haversineKm(trip.destLat, trip.destLng, hive.lat, hive.lng) < 0.2;
    }

    /**
     * Da media vuelta: recorre el tramo ya hecho en sentido contrario, hacia el origen.
     * @return false si ya iba de vuelta o no hay ruta
     */
    public static boolean applyUTurnHome(@NonNull TruckTripEntity trip, long nowMs,
            @Nullable String originHexId) {
        double homeLat = trip.originLat;
        double homeLng = trip.originLng;
        ReverseRoute reverse = reverseRemainingToOrigin(trip, nowMs);
        List<double[]> back = reverse.points;
        if (back.size() < 2) {
            return false;
        }
        double[] here = back.get(0);
        trip.originLat = here[0];
        trip.originLng = here[1];
        trip.destLat = homeLat;
        trip.destLng = homeLng;
        if (originHexId != null && !originHexId.isEmpty()) {
            trip.destHexId = originHexId;
        }
        trip.startEpochMs = nowMs;
        trip.durationMs = durationMs(back, reverse.kinds, 0);
        trip.routePolyline = EncodedPolyline.encode(back);
        trip.routeRoadKinds = reverse.kinds;
        return true;
    }

    @NonNull
    static ReverseRoute reverseRemainingToOrigin(@NonNull TruckTripEntity trip, long nowMs) {
        List<double[]> pts = routePoints(trip);
        double t = progress(trip, nowMs);
        double[] here = along(pts, t);
        List<double[]> back = new ArrayList<>();
        back.add(here);
        String srcKinds = trip.routeRoadKinds;
        if (pts.size() < 2 || t <= 1e-6) {
            back.add(new double[]{trip.originLat, trip.originLng});
            return new ReverseRoute(back, RoadKind.fill(1, RoadKind.OTRO));
        }
        int n = pts.size();
        double[] cum = new double[n];
        for (int i = 1; i < n; i++) {
            double[] a = pts.get(i - 1);
            double[] b = pts.get(i);
            cum[i] = cum[i - 1] + TranshumanceRules.haversineKm(a[0], a[1], b[0], b[1]);
        }
        double total = cum[n - 1];
        if (total <= 1e-9) {
            back.add(new double[]{trip.originLat, trip.originLng});
            return new ReverseRoute(back, RoadKind.fill(1, RoadKind.OTRO));
        }
        double target = t * total;
        int seg = n - 1;
        for (int i = 1; i < n; i++) {
            if (cum[i] >= target) {
                seg = i;
                break;
            }
        }
        for (int j = seg - 1; j >= 0; j--) {
            back.add(pts.get(j));
        }
        StringBuilder kinds = new StringBuilder(Math.max(0, back.size() - 1));
        if (RoadKind.typed(srcKinds)) {
            kinds.append(RoadKind.at(srcKinds, seg - 1).code);
            for (int j = seg - 2; j >= 0; j--) {
                kinds.append(RoadKind.at(srcKinds, j).code);
            }
        }
        return new ReverseRoute(back, kinds.toString());
    }

    static final class ReverseRoute {
        @NonNull
        final List<double[]> points;
        @NonNull
        final String kinds;

        ReverseRoute(@NonNull List<double[]> points, @NonNull String kinds) {
            this.points = points;
            this.kinds = kinds;
        }
    }

    @NonNull
    public static List<double[]> routePoints(@Nullable TruckTripEntity trip) {
        if (trip == null) {
            return java.util.Collections.emptyList();
        }
        List<double[]> pts = pointsOf(trip);
        if (pts.size() >= 2) {
            return pts;
        }
        return java.util.Arrays.asList(
                new double[]{trip.originLat, trip.originLng},
                new double[]{trip.destLat, trip.destLng});
    }

    public static double[] position(TruckTripEntity trip, long nowMs) {
        double t = progress(trip, nowMs);
        List<double[]> pts = routePoints(trip);
        return along(pts, t);
    }

    @NonNull
    public static double[] along(@NonNull List<double[]> pts, double t) {
        t = Math.max(0, Math.min(1, t));
        if (pts.isEmpty()) {
            return new double[]{0, 0};
        }
        if (pts.size() == 1 || t <= 0) {
            return pts.get(0);
        }
        if (t >= 1) {
            return pts.get(pts.size() - 1);
        }
        int n = pts.size();
        double[] cum = new double[n];
        for (int i = 1; i < n; i++) {
            double[] a = pts.get(i - 1);
            double[] b = pts.get(i);
            cum[i] = cum[i - 1] + TranshumanceRules.haversineKm(a[0], a[1], b[0], b[1]);
        }
        double total = cum[n - 1];
        if (total <= 1e-9) {
            return pts.get(n - 1);
        }
        double target = t * total;
        for (int i = 1; i < n; i++) {
            if (cum[i] >= target) {
                double seg = cum[i] - cum[i - 1];
                double u = seg <= 1e-9 ? 1 : (target - cum[i - 1]) / seg;
                double[] a = pts.get(i - 1);
                double[] b = pts.get(i);
                return interpolate(a[0], a[1], b[0], b[1], u);
            }
        }
        return pts.get(n - 1);
    }

    @NonNull
    private static List<double[]> pointsOf(@NonNull TruckTripEntity trip) {
        String encoded = trip.routePolyline;
        if (encoded == null || encoded.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        if (encoded.equals(cachedEncoded) && cachedPoints != null) {
            return cachedPoints;
        }
        List<double[]> pts = EncodedPolyline.decode(encoded);
        cachedEncoded = encoded;
        cachedPoints = pts;
        return pts;
    }

    public static double[] interpolate(double lat1, double lon1, double lat2, double lon2, double t) {
        t = Math.max(0, Math.min(1, t));
        if (t <= 0) {
            return new double[]{lat1, lon1};
        }
        if (t >= 1) {
            return new double[]{lat2, lon2};
        }
        double p1 = Math.toRadians(lat1);
        double l1 = Math.toRadians(lon1);
        double p2 = Math.toRadians(lat2);
        double l2 = Math.toRadians(lon2);
        double d = 2 * Math.asin(Math.sqrt(haversin(p2 - p1)
                + Math.cos(p1) * Math.cos(p2) * haversin(l2 - l1)));
        if (d < 1e-9) {
            return new double[]{lat2, lon2};
        }
        double a = Math.sin((1 - t) * d) / Math.sin(d);
        double b = Math.sin(t * d) / Math.sin(d);
        double x = a * Math.cos(p1) * Math.cos(l1) + b * Math.cos(p2) * Math.cos(l2);
        double y = a * Math.cos(p1) * Math.sin(l1) + b * Math.cos(p2) * Math.sin(l2);
        double z = a * Math.sin(p1) + b * Math.sin(p2);
        return new double[]{
                Math.toDegrees(Math.atan2(z, Math.sqrt(x * x + y * y))),
                Math.toDegrees(Math.atan2(y, x))
        };
    }

    private static double haversin(double x) {
        double s = Math.sin(x / 2);
        return s * s;
    }
}
