package com.apiculture.simulator.domain.parcel;

import androidx.annotation.Nullable;

import com.apiculture.simulator.data.local.entity.HexParcelOwnershipEntity;

import java.util.List;
import java.util.Random;

/**
 * Punto pseudoaleatorio dentro del polígono de un hex (rechazo en el cajón lat/lon).
 */
public final class HexParcelRandomPoint {

    private static final int MAX_ATTEMPTS = 100;

    private HexParcelRandomPoint() {
    }

    /**
     * @return {@code [lat, lon]} dentro del polígono o el centroide si no hubo suerte.
     */
    public static double[] randomLatLonInside(HexParcel hex, Random rng) {
        if (hex == null || hex.polygonLatLon == null || hex.polygonLatLon.length < 3) {
            return new double[]{hex != null ? hex.centroidLat : 0, hex != null ? hex.centroidLon : 0};
        }
        double minLat = hex.polygonLatLon[0][0];
        double maxLat = minLat;
        double minLon = hex.polygonLatLon[0][1];
        double maxLon = minLon;
        for (double[] v : hex.polygonLatLon) {
            if (v == null || v.length < 2) {
                continue;
            }
            minLat = Math.min(minLat, v[0]);
            maxLat = Math.max(maxLat, v[0]);
            minLon = Math.min(minLon, v[1]);
            maxLon = Math.max(maxLon, v[1]);
        }
        if (rng == null) {
            rng = new Random();
        }
        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            double lat = minLat + rng.nextDouble() * (maxLat - minLat);
            double lon = minLon + rng.nextDouble() * (maxLon - minLon);
            if (HexParcelPointInPolygon.contains(lat, lon, hex.polygonLatLon)) {
                return new double[]{lat, lon};
            }
        }
        return new double[]{hex.centroidLat, hex.centroidLon};
    }

    /** Distancia al cuadrado en grados (solo para comparar pins cercanos). */
    public static double dist2(double lat1, double lng1, double lat2, double lng2) {
        double dLat = lat1 - lat2;
        double dLng = lng1 - lng2;
        return dLat * dLat + dLng * dLng;
    }

    @Nullable
    public static HexParcelOwnershipEntity nearestSite(
            @Nullable HexParcel hex,
            @Nullable List<HexParcelOwnershipEntity> sites,
            double lat,
            double lng) {
        if (sites == null || sites.isEmpty()) {
            return null;
        }
        HexParcelOwnershipEntity best = null;
        double bestD = Double.POSITIVE_INFINITY;
        for (HexParcelOwnershipEntity row : sites) {
            if (row == null) {
                continue;
            }
            double[] p = siteOf(hex, row);
            double d = dist2(lat, lng, p[0], p[1]);
            if (d < bestD) {
                bestD = d;
                best = row;
            }
        }
        return best;
    }

    /** Pin del apiario al que pertenece la colmena (no el punto aleatorio de la colmena). */
    @Nullable
    public static HexParcelOwnershipEntity nearestApiary(
            @Nullable HexParcel hex,
            @Nullable List<HexParcelOwnershipEntity> sites,
            double lat,
            double lng) {
        if (sites == null || sites.isEmpty()) {
            return null;
        }
        List<HexParcelOwnershipEntity> apiaries = new java.util.ArrayList<>();
        for (HexParcelOwnershipEntity row : sites) {
            if (row != null && WarehouseRules.isApiarySite(row)) {
                apiaries.add(row);
            }
        }
        return nearestSite(hex, apiaries.isEmpty() ? sites : apiaries, lat, lng);
    }

    public static boolean belongsToSite(
            @Nullable HexParcel hex,
            @Nullable List<HexParcelOwnershipEntity> ownerSites,
            double hiveLat,
            double hiveLng,
            @Nullable String siteId) {
        if (siteId == null || siteId.isEmpty()) {
            return true;
        }
        HexParcelOwnershipEntity nearest = nearestApiary(hex, ownerSites, hiveLat, hiveLng);
        if (nearest == null) {
            return false;
        }
        String got = nearest.siteId != null && !nearest.siteId.isEmpty() ? nearest.siteId : "default";
        return siteId.equals(got);
    }

    /**
     * Destino de camión / mapa para una colmena: el pin del apiario más cercano.
     * Si no hay apiario propio, el centroide del hex (o las coords de la colmena).
     */
    public static double[] collectPointForHive(
            @Nullable HexParcel hex,
            @Nullable List<HexParcelOwnershipEntity> ownerSites,
            double hiveLat,
            double hiveLng) {
        HexParcelOwnershipEntity site = nearestApiary(hex, ownerSites, hiveLat, hiveLng);
        if (site != null && WarehouseRules.isApiarySite(site)) {
            return siteOf(hex, site);
        }
        if (hex != null) {
            return new double[]{hex.centroidLat, hex.centroidLon};
        }
        return new double[]{hiveLat, hiveLng};
    }

    /** Coordenadas al crear o colocar una colmena en un hex propio. */
    public static double[] pinForOwnedApiary(
            @Nullable HexParcel hex,
            @Nullable List<HexParcelOwnershipEntity> ownerSites,
            @Nullable String siteId,
            double hintLat,
            double hintLng) {
        if (siteId != null && !siteId.isEmpty() && ownerSites != null) {
            for (HexParcelOwnershipEntity row : ownerSites) {
                if (row == null) {
                    continue;
                }
                String got = row.siteId != null && !row.siteId.isEmpty() ? row.siteId : "default";
                if (siteId.equals(got)) {
                    return siteOf(hex, row);
                }
            }
        }
        if (!Double.isNaN(hintLat) && !Double.isNaN(hintLng)) {
            return collectPointForHive(hex, ownerSites, hintLat, hintLng);
        }
        if (ownerSites != null) {
            for (HexParcelOwnershipEntity row : ownerSites) {
                if (row != null && WarehouseRules.isApiarySite(row)) {
                    return siteOf(hex, row);
                }
            }
        }
        if (hex != null) {
            return new double[]{hex.centroidLat, hex.centroidLon};
        }
        return new double[]{0, 0};
    }

    public static double[] pinForOwnedApiary(
            @Nullable HexParcel hex,
            @Nullable List<HexParcelOwnershipEntity> ownerSites,
            double hintLat,
            double hintLng) {
        return pinForOwnedApiary(hex, ownerSites, null, hintLat, hintLng);
    }

    /** Pin guardado o, si no hay, un punto estable y distinto por dueño dentro del hex. */
    public static double[] siteOf(@Nullable HexParcel hex, @Nullable HexParcelOwnershipEntity row) {
        if (row != null && (Math.abs(row.siteLat) > 1e-8 || Math.abs(row.siteLng) > 1e-8)) {
            return new double[]{row.siteLat, row.siteLng};
        }
        long seed = 17L;
        if (row != null) {
            seed = 31L * (row.hexId != null ? row.hexId.hashCode() : 0)
                    + (row.ownerId != null ? row.ownerId.hashCode() : 0)
                    + (row.siteId != null ? row.siteId.hashCode() : 0);
        }
        return randomLatLonInside(hex, new Random(seed));
    }

    /** Punto estable dentro del hex, distinto por {@code salt} (p. ej. flora + tramo). */
    public static double[] pinOf(@Nullable HexParcel hex, @Nullable String salt) {
        long seed = 17L;
        if (hex != null && hex.id != null) {
            seed = 31L * hex.id.hashCode();
        }
        if (salt != null) {
            seed = 31L * seed + salt.hashCode();
        }
        return randomLatLonInside(hex, new Random(seed));
    }

    public static double[] warehouseOf(@Nullable HexParcel hex, @Nullable HexParcelOwnershipEntity row) {
        if (row != null && (Math.abs(row.warehouseLat) > 1e-8 || Math.abs(row.warehouseLng) > 1e-8)) {
            return new double[]{row.warehouseLat, row.warehouseLng};
        }
        double[] site = siteOf(hex, row);
        return new double[]{site[0] + 0.00034, site[1] + 0.00022};
    }
}
