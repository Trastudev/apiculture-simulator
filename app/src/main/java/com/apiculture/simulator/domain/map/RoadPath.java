package com.apiculture.simulator.domain.map;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

public final class RoadPath {

    @NonNull
    public final List<double[]> points;
    @NonNull
    public final String encoded;
    public final double distanceKm;
    /** Un carácter por arista (puntos-1): A autopista, N nacional, C comarcal, O resto. */
    @NonNull
    public final String edgeKinds;
    /** Recta de pista elegida a propósito en una red de carreteras muy escasa. */
    public final boolean fieldTrack;

    public RoadPath(@NonNull List<double[]> points, @NonNull String encoded, double distanceKm) {
        this(points, encoded, distanceKm, "");
    }

    public RoadPath(@NonNull List<double[]> points, @NonNull String encoded, double distanceKm,
            @Nullable String edgeKinds) {
        this(points, encoded, distanceKm, edgeKinds, false);
    }

    private RoadPath(@NonNull List<double[]> points, @NonNull String encoded, double distanceKm,
            @Nullable String edgeKinds, boolean fieldTrack) {
        this.points = points;
        this.encoded = encoded;
        this.distanceKm = distanceKm;
        this.edgeKinds = RoadKind.fit(edgeKinds, Math.max(0, points.size() - 1));
        this.fieldTrack = fieldTrack;
    }

    @NonNull
    public static RoadPath geodesic(double fromLat, double fromLng, double toLat, double toLng) {
        List<double[]> pts = new java.util.ArrayList<>(2);
        pts.add(new double[]{fromLat, fromLng});
        pts.add(new double[]{toLat, toLng});
        return new RoadPath(pts, EncodedPolyline.encode(pts),
                com.apiculture.simulator.domain.game.TranshumanceRules.haversineKm(
                        fromLat, fromLng, toLat, toLng),
                RoadKind.fill(1, RoadKind.OTRO));
    }

    /** Pista en línea recta, a la velocidad del gris. Cuenta como ruta aceptada. */
    @NonNull
    public static RoadPath fieldTrack(double fromLat, double fromLng, double toLat, double toLng) {
        List<double[]> pts = new java.util.ArrayList<>(2);
        pts.add(new double[]{fromLat, fromLng});
        pts.add(new double[]{toLat, toLng});
        return fieldTrack(pts);
    }

    @NonNull
    public static RoadPath fieldTrack(@NonNull List<double[]> pts) {
        if (pts.size() < 2) {
            throw new IllegalArgumentException("fieldTrack needs two points");
        }
        return new RoadPath(pts, EncodedPolyline.encode(pts), pathKm(pts),
                RoadKind.fill(pts.size() - 1, RoadKind.OTRO), true);
    }

    /**
     * Enlaza el apiario y el destino al tramo de carretera (el camión sale del campo).
     */
    @NonNull
    public static RoadPath withEndpoints(@NonNull RoadPath path, double fromLat, double fromLng,
            double toLat, double toLng) {
        List<double[]> pts = new java.util.ArrayList<>(path.points.size() + 2);
        int prepend = 0;
        double[] first = path.points.isEmpty() ? null : path.points.get(0);
        if (first == null || farEnough(fromLat, fromLng, first[0], first[1])) {
            pts.add(new double[]{fromLat, fromLng});
            prepend = 1;
        }
        pts.addAll(path.points);
        int append = 0;
        double[] last = path.points.isEmpty() ? null : path.points.get(path.points.size() - 1);
        if (last == null || farEnough(toLat, toLng, last[0], last[1])) {
            pts.add(new double[]{toLat, toLng});
            append = 1;
        }
        if (pts.size() < 2) {
            return geodesic(fromLat, fromLng, toLat, toLng);
        }
        String mid = RoadKind.fit(path.edgeKinds, Math.max(0, path.points.size() - 1));
        String kinds = RoadKind.fill(prepend, RoadKind.OTRO) + mid
                + RoadKind.fill(append, RoadKind.OTRO);
        return new RoadPath(pts, EncodedPolyline.encode(pts), pathKm(pts), kinds);
    }

    public boolean followsRoads() {
        return fieldTrack || points.size() > 2;
    }

    private static boolean farEnough(double lat1, double lng1, double lat2, double lng2) {
        return com.apiculture.simulator.domain.game.TranshumanceRules.haversineKm(
                lat1, lng1, lat2, lng2) > 0.03;
    }

    @Nullable
    public static RoadPath fromEncoded(@Nullable String encoded, double fallbackKm) {
        return fromEncoded(encoded, fallbackKm, null);
    }

    @Nullable
    public static RoadPath fromEncoded(@Nullable String encoded, double fallbackKm,
            @Nullable String edgeKinds) {
        List<double[]> pts = EncodedPolyline.decode(encoded);
        if (pts.size() < 2) {
            return null;
        }
        double km = fallbackKm;
        if (km <= 0) {
            km = pathKm(pts);
        }
        return new RoadPath(pts, encoded == null ? EncodedPolyline.encode(pts) : encoded, km, edgeKinds);
    }

    public static double pathKm(@NonNull List<double[]> points) {
        double km = 0;
        for (int i = 1; i < points.size(); i++) {
            double[] a = points.get(i - 1);
            double[] b = points.get(i);
            km += com.apiculture.simulator.domain.game.TranshumanceRules.haversineKm(
                    a[0], a[1], b[0], b[1]);
        }
        return km;
    }
}
