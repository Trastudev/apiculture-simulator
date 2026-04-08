package com.apiculture.simulator.domain.parcel;

/**
 * Geometría hexagonal <strong>pointy-top</strong> (vértice superior) en metros; {@code sideMeters} es la longitud
 * del lado. Radio circunscrito = lado. Área regular: (3√3 / 2) · lado².
 */
public final class HexGeometry {

    private HexGeometry() {
    }

    public static double areaM2FromSide(double sideMeters) {
        return 1.5 * Math.sqrt(3.0) * sideMeters * sideMeters;
    }

    public static double areaKm2FromSide(double sideMeters) {
        return areaM2FromSide(sideMeters) / 1_000_000.0;
    }

    /** Lado (m) para un área objetivo en km². */
    public static double sideMetersForAreaKm2(double areaKm2) {
        double areaM2 = areaKm2 * 1_000_000.0;
        return Math.sqrt(2.0 * areaM2 / (3.0 * Math.sqrt(3.0)));
    }

    /**
     * Centro del hexágono en metros (origen en ancla de la proyección).
     * <a href="https://www.redblobgames.com/grids/hexagons/#hex-to-pixel-axial">RedBlobGames</a> pointy-top.
     */
    public static void axialToCenterMeters(HexAxialCoord axial, double sideMeters, double[] outXy) {
        double q = axial.q;
        double r = axial.r;
        double s = sideMeters;
        outXy[0] = s * Math.sqrt(3.0) * (q + r / 2.0);
        outXy[1] = s * (3.0 / 2.0) * r;
    }

    /** Los 6 vértices [lat, lon], orden antihorario desde la proyección local. */
    public static void cornersLatLon(HexAxialCoord axial, double sideMeters, LocalTangentPlane plane,
                                     double[][] outLatLon) {
        double[] c = new double[2];
        axialToCenterMeters(axial, sideMeters, c);
        double cx = c[0];
        double cy = c[1];
        for (int i = 0; i < 6; i++) {
            double angle = Math.PI / 2.0 + i * Math.PI / 3.0;
            double xm = cx + sideMeters * Math.cos(angle);
            double ym = cy + sideMeters * Math.sin(angle);
            outLatLon[i][0] = plane.toLatDeg(xm, ym);
            outLatLon[i][1] = plane.toLonDeg(xm, ym);
        }
    }

    public static void centroidLatLon(HexAxialCoord axial, double sideMeters, LocalTangentPlane plane,
                                      double[] outLatLon) {
        double[] c = new double[2];
        axialToCenterMeters(axial, sideMeters, c);
        outLatLon[0] = plane.toLatDeg(c[0], c[1]);
        outLatLon[1] = plane.toLonDeg(c[0], c[1]);
    }

    /** Punto interior del hexágono (convexo): abanico de triángulos desde el centro. */
    public static boolean containsPointMeters(double px, double py, HexAxialCoord axial, double sideMeters) {
        double[] c = new double[2];
        axialToCenterMeters(axial, sideMeters, c);
        double cx = c[0];
        double cy = c[1];
        double[] xCor = new double[6];
        double[] yCor = new double[6];
        for (int i = 0; i < 6; i++) {
            double a = Math.PI / 2.0 + i * Math.PI / 3.0;
            xCor[i] = cx + sideMeters * Math.cos(a);
            yCor[i] = cy + sideMeters * Math.sin(a);
        }
        for (int i = 0; i < 6; i++) {
            int j = (i + 1) % 6;
            if (pointInTriangle(px, py, cx, cy, xCor[i], yCor[i], xCor[j], yCor[j])) {
                return true;
            }
        }
        return false;
    }

    private static boolean pointInTriangle(
            double px, double py,
            double ax, double ay, double bx, double by, double cx, double cy
    ) {
        double eps = 1e-10;
        double s1 = cross(bx - ax, by - ay, px - ax, py - ay);
        double s2 = cross(cx - bx, cy - by, px - bx, py - by);
        double s3 = cross(ax - cx, ay - cy, px - cx, py - cy);
        boolean hasNeg = s1 < -eps || s2 < -eps || s3 < -eps;
        boolean hasPos = s1 > eps || s2 > eps || s3 > eps;
        return !(hasNeg && hasPos);
    }

    private static double cross(double x1, double y1, double x2, double y2) {
        return x1 * y2 - y1 * x2;
    }
}
