package com.apiculture.simulator.domain.parcel;

/**
 * Plano tangente local (equirectangular con escala por cos(lat0)): metros respecto al ancla,
 * adecuado para rejillas de decenas de km en una región.
 */
public final class LocalTangentPlane {
    private static final double EARTH_RADIUS_M = 6_371_000.0;

    private final double lat0;
    private final double lon0;
    private final double cosLat0;

    public LocalTangentPlane(double anchorLatDeg, double anchorLonDeg) {
        this.lat0 = anchorLatDeg;
        this.lon0 = anchorLonDeg;
        this.cosLat0 = Math.cos(Math.toRadians(anchorLatDeg));
    }

    public double toX(double latDeg, double lonDeg) {
        return EARTH_RADIUS_M * cosLat0 * Math.toRadians(lonDeg - lon0);
    }

    public double toY(double latDeg, double lonDeg) {
        return EARTH_RADIUS_M * Math.toRadians(latDeg - lat0);
    }

    public double toLatDeg(double xMeters, double yMeters) {
        return lat0 + Math.toDegrees(yMeters / EARTH_RADIUS_M);
    }

    public double toLonDeg(double xMeters, double yMeters) {
        if (Math.abs(cosLat0) < 1e-9) {
            return lon0;
        }
        return lon0 + Math.toDegrees(xMeters / (EARTH_RADIUS_M * cosLat0));
    }
}
