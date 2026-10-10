package com.apiculture.simulator.domain.parcel;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.apiculture.simulator.data.local.entity.HexParcelOwnershipEntity;
import com.apiculture.simulator.domain.market.HoneyMarketEngine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Un almacén por terreno. El nivel del edificio usa los mismos umbrales de miel
 * que el nivel de jugador ({@link HoneyMarketEngine#accessLevelForFlora}).
 */
public final class WarehouseRules {
    public static final int COST_B = 1500;
    public static final int BASE_CAPACITY_KG = 50;
    public static final int CAPACITY_STEP_KG = 25;
    public static final int UPGRADE_COST_PER_LEVEL_B = 250;

    private WarehouseRules() {
    }

    public static int levelOf(@Nullable HexParcelOwnershipEntity row) {
        if (row == null || !row.hasWarehouse) {
            return 0;
        }
        return Math.max(1, row.warehouseLevel);
    }

    public static double capacityKg(int warehouseLevel) {
        if (warehouseLevel <= 0) {
            return 0.0;
        }
        return BASE_CAPACITY_KG + (double) CAPACITY_STEP_KG * (warehouseLevel - 1);
    }

    public static int upgradeCostB(int fromLevel) {
        return UPGRADE_COST_PER_LEVEL_B * Math.max(1, fromLevel);
    }

    /** Suma de ampliaciones pagadas para llegar a {@code level} (nivel 1 no tiene ampliaciones). */
    public static int upgradeInvestedB(int level) {
        int sum = 0;
        for (int from = 1; from < Math.max(1, level); from++) {
            sum += upgradeCostB(from);
        }
        return sum;
    }

    public static int investedB(int warehouseLevel) {
        if (warehouseLevel <= 0) {
            return 0;
        }
        return COST_B + upgradeInvestedB(warehouseLevel);
    }

    public static int sellRefundB(int paidB) {
        return Math.max(0, paidB / 2);
    }

    public static int warehouseSellRefundB(int warehouseLevel) {
        return sellRefundB(investedB(warehouseLevel));
    }

    /** Fila que representa un apiario (no un almacén suelto en el mismo hex). */
    public static boolean isApiarySite(@Nullable HexParcelOwnershipEntity row) {
        if (row == null) {
            return false;
        }
        if (!row.hasWarehouse) {
            return true;
        }
        if (Math.abs(row.siteLat) < 1e-8 && Math.abs(row.siteLng) < 1e-8) {
            return false;
        }
        double dLat = row.siteLat - row.warehouseLat;
        double dLng = row.siteLng - row.warehouseLng;
        return (dLat * dLat + dLng * dLng) > 1.6e-9;
    }

    public static boolean allowsFlora(int warehouseLevel, @Nullable String floraKey) {
        if (warehouseLevel <= 0) {
            return false;
        }
        return HoneyMarketEngine.accessLevelForFlora(floraKey) <= warehouseLevel;
    }

    public static int maxLevel(@Nullable List<HexParcelOwnershipEntity> rows, @Nullable String ownerId) {
        int max = 0;
        for (HexParcelOwnershipEntity row : warehouses(rows, ownerId)) {
            max = Math.max(max, levelOf(row));
        }
        return max;
    }

    public static double totalCapacityKg(@Nullable List<HexParcelOwnershipEntity> rows,
            @Nullable String ownerId) {
        double sum = 0.0;
        for (HexParcelOwnershipEntity row : warehouses(rows, ownerId)) {
            sum += capacityKg(levelOf(row));
        }
        return sum;
    }

    public static double roomKg(@Nullable List<HexParcelOwnershipEntity> rows, @Nullable String ownerId,
            double stockKg) {
        return Math.max(0.0, totalCapacityKg(rows, ownerId) - Math.max(0.0, stockKg));
    }

    @NonNull
    public static List<String> florasUnlockedAt(int warehouseLevel) {
        int lvl = Math.max(1, warehouseLevel);
        List<String> out = new ArrayList<>();
        for (String flora : HexFlora.FLORA_TYPES) {
            int access = HoneyMarketEngine.accessLevelForFlora(flora);
            if (lvl == 1) {
                if (access <= 1) {
                    out.add(flora);
                }
            } else if (access == lvl) {
                out.add(flora);
            }
        }
        return Collections.unmodifiableList(out);
    }

    @NonNull
    public static List<String> warehouseHexIds(@Nullable List<HexParcelOwnershipEntity> rows,
            @Nullable String ownerId) {
        List<String> ids = new ArrayList<>();
        for (HexParcelOwnershipEntity row : warehouses(rows, ownerId)) {
            ids.add(row.hexId);
        }
        Collections.sort(ids);
        return ids;
    }

    public static double storedOnHex(double totalStockKg,
            @Nullable List<HexParcelOwnershipEntity> rows,
            @Nullable String ownerId, @Nullable String hexId) {
        double left = Math.max(0.0, totalStockKg);
        List<HexParcelOwnershipEntity> wh = new ArrayList<>(warehouses(rows, ownerId));
        wh.sort((a, b) -> String.valueOf(a.hexId).compareTo(String.valueOf(b.hexId)));
        for (HexParcelOwnershipEntity row : wh) {
            double here = Math.min(capacityKg(levelOf(row)), left);
            left -= here;
            if (row.hexId != null && row.hexId.equals(hexId)) {
                return here;
            }
        }
        return 0.0;
    }

    @NonNull
    private static List<HexParcelOwnershipEntity> warehouses(
            @Nullable List<HexParcelOwnershipEntity> rows, @Nullable String ownerId) {
        List<HexParcelOwnershipEntity> out = new ArrayList<>();
        if (rows == null) {
            return out;
        }
        for (HexParcelOwnershipEntity row : rows) {
            if (row == null || !row.hasWarehouse || row.hexId == null) {
                continue;
            }
            if (ownerId != null && !ownerId.equals(row.ownerId)) {
                continue;
            }
            out.add(row);
        }
        return out;
    }
}
