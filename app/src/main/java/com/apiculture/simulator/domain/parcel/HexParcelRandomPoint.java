package com.apiculture.simulator.domain.parcel;

import java.util.Random;

/**
 * Punto pseudoaleatorio dentro del polígono de un hex (rechazo en el cajón lat/lon).
 */
public final class HexParcelRandomPoint {

    private static final int MAX_ATTEMPTS = 100;

    private HexParcelRandomPoint() {
    }

    /**
     * @return {@code [lat, lon]} dentro del polígono o el centroide si no hubo suerte.
     */
    public static double[] randomLatLonInside(HexParcel hex, Random rng) {
        if (hex == null || hex.polygonLatLon == null || hex.polygonLatLon.length < 3) {
            return new double[]{hex != null ? hex.centroidLat : 0, hex != null ? hex.centroidLon : 0};
        }
        double minLat = hex.polygonLatLon[0][0];
        double maxLat = minLat;
        double minLon = hex.polygonLatLon[0][1];
        double maxLon = minLon;
        for (double[] v : hex.polygonLatLon) {
            if (v == null || v.length < 2) {
                continue;
            }
            minLat = Math.min(minLat, v[0]);
            maxLat = Math.max(maxLat, v[0]);
            minLon = Math.min(minLon, v[1]);
            maxLon = Math.max(maxLon, v[1]);
        }
        if (rng == null) {
            rng = new Random();
        }
        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            double lat = minLat + rng.nextDouble() * (maxLat - minLat);
            double lon = minLon + rng.nextDouble() * (maxLon - minLon);
            if (HexParcelPointInPolygon.contains(lat, lon, hex.polygonLatLon)) {
                return new double[]{lat, lon};
            }
        }
        return new double[]{hex.centroidLat, hex.centroidLon};
    }
}
