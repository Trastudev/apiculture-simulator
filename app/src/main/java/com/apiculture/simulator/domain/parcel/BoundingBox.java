package com.apiculture.simulator.domain.parcel;

import androidx.annotation.Nullable;

/**
 * Área geográfica alineada a norte (min/max lat/lon).
 */
public final class BoundingBox {
    public final double minLat;
    public final double maxLat;
    public final double minLon;
    public final double maxLon;

    public BoundingBox(double minLat, double maxLat, double minLon, double maxLon) {
        this.minLat = Math.min(minLat, maxLat);
        this.maxLat = Math.max(minLat, maxLat);
        this.minLon = Math.min(minLon, maxLon);
        this.maxLon = Math.max(minLon, maxLon);
    }

    public double centerLat() {
        return (minLat + maxLat) / 2.0;
    }

    public double centerLon() {
        return (minLon + maxLon) / 2.0;
    }

    public boolean containsLatLon(double lat, double lon) {
        return lat >= minLat && lat <= maxLat && lon >= minLon && lon <= maxLon;
    }

    /**
     * Intersección con otro rectángulo alineado a norte, o {@code null} si no hay solape.
     */
    @Nullable
    public BoundingBox intersect(BoundingBox o) {
        double nMinLat = Math.max(minLat, o.minLat);
        double nMaxLat = Math.min(maxLat, o.maxLat);
        double nMinLon = Math.max(minLon, o.minLon);
        double nMaxLon = Math.min(maxLon, o.maxLon);
        if (nMinLat >= nMaxLat || nMinLon >= nMaxLon) {
            return null;
        }
        return new BoundingBox(nMinLat, nMaxLat, nMinLon, nMaxLon);
    }
}
