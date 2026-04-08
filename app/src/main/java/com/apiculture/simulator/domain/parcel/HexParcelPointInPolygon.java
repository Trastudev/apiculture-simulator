package com.apiculture.simulator.domain.parcel;

/**
 * Punto [lat, lon] dentro de polígono plano (suficiente para hexágono pequeño en Iberia).
 */
public final class HexParcelPointInPolygon {

    private HexParcelPointInPolygon() {
    }

    /** {@code ring} vértices [lat, lon], sin repetir cierre. */
    public static boolean contains(double lat, double lon, double[][] ring) {
        if (ring == null || ring.length < 3) {
            return false;
        }
        boolean inside = false;
        int n = ring.length;
        for (int i = 0, j = n - 1; i < n; j = i++) {
            double yi = ring[i][0];
            double xi = ring[i][1];
            double yj = ring[j][0];
            double xj = ring[j][1];
            if (((yi > lat) != (yj > lat))
                    && (lon < (xj - xi) * (lat - yi) / (yj - yi + 1e-18) + xi)) {
                inside = !inside;
            }
        }
        return inside;
    }
}
