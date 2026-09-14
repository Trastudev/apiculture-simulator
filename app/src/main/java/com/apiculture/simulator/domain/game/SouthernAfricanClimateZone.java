package com.apiculture.simulator.domain.game;

import com.apiculture.simulator.domain.parcel.HexParcel;

/**
 * Zonas climáticas jugables de Sudáfrica (hemisferio sur: invierno cuando en Iberia es verano).
 */
public enum SouthernAfricanClimateZone {
    FYNBOS,
    KAROO,
    HIGHVELD,
    SUBTROPICAL,
    BUSHVELD;

    public String labelEs() {
        switch (this) {
            case FYNBOS:
                return "Fynbos (Cabo)";
            case KAROO:
                return "Karoo";
            case HIGHVELD:
                return "Highveld";
            case SUBTROPICAL:
                return "Costa subtropical";
            case BUSHVELD:
            default:
                return "Bushveld";
        }
    }

    public int bloomShiftDays() {
        switch (this) {
            case FYNBOS:
                return 0;
            case KAROO:
                return 4;
            case HIGHVELD:
                return 0;
            case SUBTROPICAL:
                return -6;
            case BUSHVELD:
            default:
                return -3;
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

    public static SouthernAfricanClimateZone forParcel(HexParcel parcel) {
        if (parcel == null) {
            return HIGHVELD;
        }
        int elev = parcel.maxElevationMeters != null ? parcel.maxElevationMeters : 800;
        return fromLatLonElev(parcel.centroidLat, parcel.centroidLon, elev);
    }

    public static SouthernAfricanClimateZone forHive(Double lat, Double lon, int elevM) {
        if (lat == null || lon == null || Double.isNaN(lat) || Double.isNaN(lon)) {
            return HIGHVELD;
        }
        return fromLatLonElev(lat, lon, elevM);
    }

    public static SouthernAfricanClimateZone fromLatLonElev(double lat, double lon, int elevM) {
        int elev = Math.max(0, elevM);
        if (elev >= 1600) {
            return HIGHVELD;
        }
        if (lat <= -32.15 && lon <= 22.2) {
            return FYNBOS;
        }
        if (lat <= -30.4 && lon >= 19.0 && lon <= 26.2 && elev < 1300) {
            return KAROO;
        }
        if (lon >= 29.7 && elev < 750) {
            return SUBTROPICAL;
        }
        if (lat >= -25.6) {
            return BUSHVELD;
        }
        return HIGHVELD;
    }
}
