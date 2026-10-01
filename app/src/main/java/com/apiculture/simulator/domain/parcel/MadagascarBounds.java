package com.apiculture.simulator.domain.parcel;

import java.util.ArrayList;
import java.util.List;

/**
 * Isla de Madagascar (sin Comoras, Mayotte ni Reunión).
 */
public final class MadagascarBounds {

    public static final BoundingBox BOX = new BoundingBox(
            -25.70,
            -11.85,
            43.10,
            50.55);

    private MadagascarBounds() {
    }

    /**
     * Recorta Comoras / Mayotte (NO, ~12°S 44°E) y deja Nosy Be / Antsiranana.
     */
    public static boolean keepCentroid(double lat, double lon) {
        if (lat > -12.20 && lon < 47.40) {
            return false;
        }
        if (lon < 43.18 || lon > 50.52) {
            return false;
        }
        if (lat > -11.90 || lat < -25.65) {
            return false;
        }
        return true;
    }

    public static boolean keepHex(HexParcel parcel) {
        return parcel != null && keepCentroid(parcel.centroidLat, parcel.centroidLon);
    }

    public static List<HexParcel> keepPlayable(List<HexParcel> parcels) {
        if (parcels == null || parcels.isEmpty()) {
            return parcels;
        }
        List<HexParcel> out = new ArrayList<>(parcels.size());
        for (HexParcel p : parcels) {
            if (keepHex(p)) {
                out.add(p);
            }
        }
        return out;
    }
}
