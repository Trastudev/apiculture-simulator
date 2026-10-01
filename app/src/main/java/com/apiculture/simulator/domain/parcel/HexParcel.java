package com.apiculture.simulator.domain.parcel;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
    /**
     * Topónimo real (pueblo, ciudad o accidente) embebido en {@code overlay.json} ({@code place}).
     */
    @Nullable
    public final String placeName;

    public HexParcel(String id, double[][] polygonLatLon, double centroidLat, double centroidLon,
                     double areaKm2, boolean coastal) {
        this(id, polygonLatLon, centroidLat, centroidLon, areaKm2, coastal, null, null);
    }

    public HexParcel(String id, double[][] polygonLatLon, double centroidLat, double centroidLon,
                     double areaKm2, boolean coastal, @Nullable Integer maxElevationMeters) {
        this(id, polygonLatLon, centroidLat, centroidLon, areaKm2, coastal, maxElevationMeters, null);
    }

    public HexParcel(String id, double[][] polygonLatLon, double centroidLat, double centroidLon,
                     double areaKm2, boolean coastal, @Nullable Integer maxElevationMeters,
                     @Nullable String placeName) {
        this.id = id;
        this.polygonLatLon = polygonLatLon;
        this.centroidLat = centroidLat;
        this.centroidLon = centroidLon;
        this.areaKm2 = areaKm2;
        this.coastal = coastal;
        this.maxElevationMeters = maxElevationMeters;
        this.placeName = placeName != null && !placeName.isEmpty() ? placeName : null;
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

    /** Id del hexágono vecino conservando el prefijo de región ({@code hex_iberia_q_r}). */
    @Nullable
    public String idWithAxial(@Nullable HexAxialCoord c) {
        if (c == null || id == null) {
            return null;
        }
        int lastU = id.lastIndexOf('_');
        int prevU = lastU > 0 ? id.lastIndexOf('_', lastU - 1) : -1;
        if (prevU < 0) {
            return null;
        }
        return id.substring(0, prevU + 1) + c.q + "_" + c.r;
    }

    /** Ids de los 6 hexes axiales vecinos; vacío si el id no se puede parsear. */
    @NonNull
    public static List<String> neighborIds(@Nullable String hexId) {
        if (hexId == null || hexId.isEmpty()) {
            return Collections.emptyList();
        }
        HexParcel probe = new HexParcel(hexId, new double[0][0], 0, 0, 0, false);
        HexAxialCoord c = probe.parseAxialFromId();
        if (c == null) {
            return Collections.emptyList();
        }
        List<String> out = new ArrayList<>(6);
        for (HexAxialCoord n : c.neighbors()) {
            String id = probe.idWithAxial(n);
            if (id != null && !id.isEmpty()) {
                out.add(id);
            }
        }
        return out;
    }

    /** Distancia axial entre dos ids {@code hex_region_q_r}; {@link Integer#MAX_VALUE} si no se puede parsear. */
    public static int axialDistance(@Nullable String hexIdA, @Nullable String hexIdB) {
        if (hexIdA == null || hexIdB == null || hexIdA.isEmpty() || hexIdB.isEmpty()) {
            return Integer.MAX_VALUE;
        }
        HexParcel a = new HexParcel(hexIdA, new double[0][0], 0, 0, 0, false);
        HexParcel b = new HexParcel(hexIdB, new double[0][0], 0, 0, 0, false);
        HexAxialCoord ca = a.parseAxialFromId();
        HexAxialCoord cb = b.parseAxialFromId();
        if (ca == null || cb == null) {
            return Integer.MAX_VALUE;
        }
        return ca.distanceTo(cb);
    }
}
