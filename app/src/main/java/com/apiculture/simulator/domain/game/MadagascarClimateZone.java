package com.apiculture.simulator.domain.game;

import android.content.Context;

import com.apiculture.simulator.R;
import com.apiculture.simulator.domain.parcel.HexParcel;

/**
 * Cuatro climas de Madagascar (verano austral dic–feb).
 * Distribución aproximada: costa este húmeda, altiplano, oeste tropical y sudoeste árido.
 */
public enum MadagascarClimateZone {
    EQUATORIAL,
    HIGHLANDS,
    TROPICAL,
    DESERT;

    public String label(Context context) {
        switch (this) {
            case EQUATORIAL:
                return context.getString(R.string.map_climate_mdg_equatorial);
            case HIGHLANDS:
                return context.getString(R.string.map_climate_mdg_highlands);
            case DESERT:
                return context.getString(R.string.map_climate_mdg_desert);
            case TROPICAL:
            default:
                return context.getString(R.string.map_climate_mdg_tropical);
        }
    }

    public String labelEs() {
        switch (this) {
            case EQUATORIAL:
                return "Ecuatorial";
            case HIGHLANDS:
                return "Altiplano";
            case DESERT:
                return "Desierto";
            case TROPICAL:
            default:
                return "Tropical";
        }
    }

    public int bloomShiftDays() {
        switch (this) {
            case EQUATORIAL:
                return -4;
            case HIGHLANDS:
                return 2;
            case DESERT:
                return 5;
            case TROPICAL:
            default:
                return 0;
        }
    }

    public String bloomHintEs() {
        int s = bloomShiftDays();
        if (s < 0) {
            return "floración " + (-s) + " d antes";
        }
        if (s > 0) {
            return "floración +" + s + " d";
        }
        return "verano en dic–feb";
    }

    public String dialogLineEs() {
        return "Clima: " + labelEs() + " (" + bloomHintEs() + ")";
    }

    public static MadagascarClimateZone forParcel(HexParcel parcel) {
        if (parcel == null) {
            return TROPICAL;
        }
        int elev = parcel.maxElevationMeters != null ? parcel.maxElevationMeters : 400;
        return fromLatLonElev(parcel.centroidLat, parcel.centroidLon, elev);
    }

    public static MadagascarClimateZone forHive(Double lat, Double lon, int elevM) {
        if (lat == null || lon == null || Double.isNaN(lat) || Double.isNaN(lon)) {
            return TROPICAL;
        }
        return fromLatLonElev(lat, lon, elevM);
    }

    public static MadagascarClimateZone fromLatLonElev(double lat, double lon, int elevM) {
        int elev = Math.max(0, elevM);
        if (lat <= -21.8 && lon <= 45.55) {
            return DESERT;
        }
        if (lat <= -23.2 && lon <= 47.05) {
            return DESERT;
        }
        if (lat <= -24.15 && lon <= 47.65) {
            return DESERT;
        }
        if (elev >= 900) {
            return HIGHLANDS;
        }
        if (elev >= 700 && lon >= 46.15 && lon <= 48.25 && lat <= -17.15 && lat >= -22.85) {
            return HIGHLANDS;
        }
        if (lon >= 48.85) {
            return EQUATORIAL;
        }
        if (lon >= 48.15 && elev < 520 && lat >= -23.6) {
            return EQUATORIAL;
        }
        return TROPICAL;
    }
}
