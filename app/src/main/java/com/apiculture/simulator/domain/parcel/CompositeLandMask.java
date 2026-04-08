package com.apiculture.simulator.domain.parcel;

import java.util.Arrays;
import java.util.List;

/** Tierra si cualquier máscara hijo da tierra (OR). */
public final class CompositeLandMask implements LandMask {
    private final List<LandMask> masks;

    public CompositeLandMask(LandMask... masks) {
        this.masks = Arrays.asList(masks);
    }

    @Override
    public boolean isLand(double latDeg, double lonDeg) {
        for (LandMask m : masks) {
            if (m.isLand(latDeg, lonDeg)) {
                return true;
            }
        }
        return false;
    }
}
