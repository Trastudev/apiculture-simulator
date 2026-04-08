package com.apiculture.simulator.domain.parcel;

/**
 * Máscara de demostración / tests: tierra dentro de un rectángulo lat/lon.
 */
public final class RectangleLandMask implements LandMask {
    private final BoundingBox landBox;

    public RectangleLandMask(BoundingBox landBox) {
        this.landBox = landBox;
    }

    @Override
    public boolean isLand(double latDeg, double lonDeg) {
        return landBox.containsLatLon(latDeg, lonDeg);
    }
}
