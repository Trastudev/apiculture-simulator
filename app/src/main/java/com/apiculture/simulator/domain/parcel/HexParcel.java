package com.apiculture.simulator.domain.parcel;

import androidx.annotation.Nullable;

/**
 * Parcela comprable hexagonal.
 * Polígono en orden [lat, lon] por vértice (6 vértices, conviene cerrar el anillo en cliente si hace falta).
 */
public final class HexParcel {
    public final String id;
    public final double[][] polygonLatLon;
    public final double centroidLat;
    public final double centroidLon;
    public final double areaKm2;
    /** Parcela interior (casi 100&nbsp;% tierra) o también usado como no costera fuerte. */
    public final boolean coastal;
    /**
     * Altitud máxima (m s.n.m.) embebida en {@code overlay.json} ({@code elev}).
     * Si es {@code null}, se obtiene por API/Open-Meteo y caché Room.
     */
    @Nullable
    public final Integer maxElevationMeters;

    public HexParcel(String id, double[][] polygonLatLon, double centroidLat, double centroidLon,
                     double areaKm2, boolean coastal) {
        this(id, polygonLatLon, centroidLat, centroidLon, areaKm2, coastal, null);
    }

    public HexParcel(String id, double[][] polygonLatLon, double centroidLat, double centroidLon,
                     double areaKm2, boolean coastal, @Nullable Integer maxElevationMeters) {
        this.id = id;
        this.polygonLatLon = polygonLatLon;
        this.centroidLat = centroidLat;
        this.centroidLon = centroidLon;
        this.areaKm2 = areaKm2;
        this.coastal = coastal;
        this.maxElevationMeters = maxElevationMeters;
    }

    public HexAxialCoord parseAxialFromId() {
        int lastU = id.lastIndexOf('_');
        int prevU = id.lastIndexOf('_', lastU - 1);
        if (prevU < 0 || lastU <= prevU + 1) {
            return null;
        }
        try {
            int q = Integer.parseInt(id.substring(prevU + 1, lastU));
            int r = Integer.parseInt(id.substring(lastU + 1));
            return new HexAxialCoord(q, r);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
