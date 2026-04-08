package com.apiculture.simulator.domain.parcel;

import androidx.annotation.Nullable;

import java.util.List;

public final class HexParcelResolve {

    private HexParcelResolve() {
    }

    /** Primer hex que contiene el punto; si ninguno, null. */
    @Nullable
    public static HexParcel findContaining(List<HexParcel> parcels, double lat, double lon) {
        if (parcels == null) {
            return null;
        }
        for (int i = 0; i < parcels.size(); i++) {
            HexParcel p = parcels.get(i);
            if (p != null && HexParcelPointInPolygon.contains(lat, lon, p.polygonLatLon)) {
                return p;
            }
        }
        return null;
    }

    @Nullable
    public static HexParcel findById(List<HexParcel> parcels, String hexId) {
        if (parcels == null || hexId == null) {
            return null;
        }
        for (int i = 0; i < parcels.size(); i++) {
            HexParcel p = parcels.get(i);
            if (p != null && hexId.equals(p.id)) {
                return p;
            }
        }
        return null;
    }
}
