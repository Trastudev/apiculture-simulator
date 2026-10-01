package com.apiculture.simulator.domain.parcel;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.apiculture.simulator.data.local.entity.HexParcelOwnershipEntity;
import com.apiculture.simulator.data.local.entity.HiveEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * Un hex es zona (flora, un almacén). La unidad jugable es el apiario ({@code siteId}).
 */
public final class HexApiary {

    public static final String DEFAULT_SITE = "default";

    private HexApiary() {
    }

    @NonNull
    public static String normalize(@Nullable String siteId) {
        return siteId == null || siteId.isEmpty() ? DEFAULT_SITE : siteId;
    }

    public static boolean sameSite(@Nullable String a, @Nullable String b) {
        return normalize(a).equals(normalize(b));
    }

    @Nullable
    public static HexParcelOwnershipEntity siteRow(
            @Nullable String siteId,
            @Nullable List<HexParcelOwnershipEntity> ownerSites) {
        if (ownerSites == null || ownerSites.isEmpty()) {
            return null;
        }
        String want = normalize(siteId);
        for (HexParcelOwnershipEntity row : ownerSites) {
            if (row != null && WarehouseRules.isApiarySite(row) && sameSite(row.siteId, want)) {
                return row;
            }
        }
        return null;
    }

    @NonNull
    public static List<HexParcelOwnershipEntity> apiariesOnHex(
            @Nullable String hexId,
            @Nullable List<HexParcelOwnershipEntity> ownerSites) {
        List<HexParcelOwnershipEntity> out = new ArrayList<>();
        if (hexId == null || ownerSites == null) {
            return out;
        }
        for (HexParcelOwnershipEntity row : ownerSites) {
            if (row != null && hexId.equals(row.hexId) && WarehouseRules.isApiarySite(row)) {
                out.add(row);
            }
        }
        return out;
    }

    @NonNull
    public static String resolveSiteId(
            @Nullable HiveEntity hive,
            @Nullable HexParcel hex,
            @Nullable List<HexParcelOwnershipEntity> ownerSites) {
        if (hive != null && hive.siteId != null && !hive.siteId.isEmpty()) {
            return hive.siteId;
        }
        if (hive == null) {
            return DEFAULT_SITE;
        }
        List<HexParcelOwnershipEntity> onHex = apiariesOnHex(hive.hexId, ownerSites);
        HexParcelOwnershipEntity nearest = HexParcelRandomPoint.nearestApiary(
                hex, onHex, hive.lat, hive.lng);
        if (nearest != null && nearest.siteId != null && !nearest.siteId.isEmpty()) {
            return nearest.siteId;
        }
        return DEFAULT_SITE;
    }

    @NonNull
    public static String resolveSiteIdAt(
            @Nullable HexParcel hex,
            @Nullable List<HexParcelOwnershipEntity> ownerSites,
            @Nullable String hexId,
            double lat,
            double lng) {
        List<HexParcelOwnershipEntity> onHex = apiariesOnHex(hexId, ownerSites);
        HexParcelOwnershipEntity nearest = HexParcelRandomPoint.nearestApiary(hex, onHex, lat, lng);
        if (nearest != null && nearest.siteId != null && !nearest.siteId.isEmpty()) {
            return nearest.siteId;
        }
        return DEFAULT_SITE;
    }

    public static boolean hiveOnSite(
            @Nullable HiveEntity hive,
            @Nullable String hexId,
            @Nullable String siteId,
            @Nullable HexParcel hex,
            @Nullable List<HexParcelOwnershipEntity> ownerSites) {
        if (hive == null || hive.inWarehouse) {
            return false;
        }
        boolean orphans = hexId == null || hexId.isEmpty();
        if (orphans) {
            return hive.hexId == null || hive.hexId.isEmpty();
        }
        if (!hexId.equals(hive.hexId)) {
            return false;
        }
        if (siteId == null || siteId.isEmpty()) {
            return true;
        }
        return sameSite(resolveSiteId(hive, hex, ownerSites), siteId);
    }

    public static boolean pointOnSite(
            @Nullable HexParcel hex,
            @Nullable List<HexParcelOwnershipEntity> ownerSites,
            @Nullable String hexId,
            double lat,
            double lng,
            @Nullable String siteId) {
        if (siteId == null || siteId.isEmpty()) {
            return true;
        }
        return sameSite(resolveSiteIdAt(hex, ownerSites, hexId, lat, lng), siteId);
    }
}
