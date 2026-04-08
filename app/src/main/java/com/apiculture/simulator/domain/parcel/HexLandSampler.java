package com.apiculture.simulator.domain.parcel;

/**
 * Estima fracción de tierra dentro de un hexágono muestreando puntos en el plano local.
 */
public final class HexLandSampler {

    private HexLandSampler() {
    }

    /**
     * Muestra sólo el centro y los 6 vértices (7 puntos). Mucho más barato que una rejilla densa
     * y suele bastar para filtrar tierra/agua en vistas de mapa.
     */
    public static double estimateLandFractionVertices(
            LandMask landMask,
            HexAxialCoord axial,
            double sideMeters,
            LocalTangentPlane plane
    ) {
        double[][] corners = new double[6][2];
        HexGeometry.cornersLatLon(axial, sideMeters, plane, corners);
        double[] cen = new double[2];
        HexGeometry.centroidLatLon(axial, sideMeters, plane, cen);
        int land = 0;
        if (landMask.isLand(cen[0], cen[1])) {
            land++;
        }
        for (int i = 0; i < 6; i++) {
            if (landMask.isLand(corners[i][0], corners[i][1])) {
                land++;
            }
        }
        return land / 7.0;
    }

    /**
     * @param gridPerAxis puntos por eje en la caja englobante (p. ej. 8 → hasta ~64 muestras internas).
     */
    public static double estimateLandFraction(
            LandMask landMask,
            HexAxialCoord axial,
            double sideMeters,
            LocalTangentPlane plane,
            int gridPerAxis
    ) {
        double[][] corners = new double[6][2];
        HexGeometry.cornersLatLon(axial, sideMeters, plane, corners);
        double xmin = Double.POSITIVE_INFINITY;
        double xmax = Double.NEGATIVE_INFINITY;
        double ymin = Double.POSITIVE_INFINITY;
        double ymax = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < 6; i++) {
            double lat = corners[i][0];
            double lon = corners[i][1];
            double x = plane.toX(lat, lon);
            double y = plane.toY(lat, lon);
            xmin = Math.min(xmin, x);
            xmax = Math.max(xmax, x);
            ymin = Math.min(ymin, y);
            ymax = Math.max(ymax, y);
        }
        int insideHex = 0;
        int landInHex = 0;
        int g = Math.max(2, gridPerAxis);
        for (int a = 0; a <= g; a++) {
            for (int b = 0; b <= g; b++) {
                double t1 = a / (double) g;
                double t2 = b / (double) g;
                double x = xmin + t1 * (xmax - xmin);
                double y = ymin + t2 * (ymax - ymin);
                if (!HexGeometry.containsPointMeters(x, y, axial, sideMeters)) {
                    continue;
                }
                insideHex++;
                double plat = plane.toLatDeg(x, y);
                double plon = plane.toLonDeg(x, y);
                if (landMask.isLand(plat, plon)) {
                    landInHex++;
                }
            }
        }
        if (insideHex == 0) {
            double[] cen = new double[2];
            HexGeometry.centroidLatLon(axial, sideMeters, plane, cen);
            return landMask.isLand(cen[0], cen[1]) ? 1.0 : 0.0;
        }
        return landInHex / (double) insideHex;
    }
}
