package com.apiculture.simulator.domain.parcel;

import com.apiculture.simulator.domain.game.FloraBloomWindow;
import com.apiculture.simulator.domain.game.GameCalendar;
import com.apiculture.simulator.domain.market.HoneyMarketEngine;

import java.time.LocalDate;

/**
 * Cultivos del jugador: anuales (baratos, se retiran al acabar la floración) y árboles
 * (más caros, permanentes, con mantenimiento anual).
 */
public final class CropRules {

    public enum Kind {
        WILD,
        ANNUAL,
        TREE
    }

    public static final int ANNUAL_GROW_DAYS = 2;
    public static final int TREE_GROW_DAYS = 10;
    public static final int ANNUAL_PLANT_EUR = 450;
    public static final int TREE_PLANT_EUR = 1600;
    public static final int TREE_MAINTENANCE_EUR = 350;
    /** Día del año en que se cobra la poda/abono de los árboles. */
    public static final int TREE_MAINTENANCE_DOY = 20;

    private CropRules() {
    }

    public static Kind kind(String floraKey) {
        String k = HoneyMarketEngine.canonicalFloraKey(floraKey);
        if (!HexFlora.isPlantation(k)) {
            return Kind.WILD;
        }
        if (isAnnualKey(k)) {
            return Kind.ANNUAL;
        }
        return Kind.TREE;
    }

    public static boolean isAnnual(String floraKey) {
        return kind(floraKey) == Kind.ANNUAL;
    }

    public static boolean isTree(String floraKey) {
        return kind(floraKey) == Kind.TREE;
    }

    public static int growDays(String floraKey) {
        return isAnnual(floraKey) ? ANNUAL_GROW_DAYS : TREE_GROW_DAYS;
    }

    public static int plantCostEuros(String floraKey) {
        return isAnnual(floraKey) ? ANNUAL_PLANT_EUR : TREE_PLANT_EUR;
    }

    public static int treeMaintenanceEuros(String floraKey) {
        return isTree(floraKey) ? TREE_MAINTENANCE_EUR : 0;
    }

    /**
     * Día civil (yyyymmdd) posterior al fin de la floración en la que entra el cultivo.
     * 0 si no es anual.
     */
    public static int expireDayKey(String floraKey, HexParcel parcel, LocalDate readyOn) {
        if (!isAnnual(floraKey) || readyOn == null) {
            return 0;
        }
        LocalDate bloomEnd = FloraBloomWindow.bloomEndDateOnOrAfter(floraKey, parcel, readyOn);
        return GameCalendar.toDayKey(bloomEnd.plusDays(1));
    }

    private static boolean isAnnualKey(String k) {
        return "Campo de Colza".equals(k)
                || "Campo de girasoles".equals(k)
                || HexFlora.LUCERNA.equals(k)
                || HexFlora.MOSTAZA.equals(k)
                || HexFlora.TREBOL.equals(k)
                || HexFlora.FACELIA.equals(k)
                || HexFlora.RABANIZA.equals(k)
                || HexFlora.SISAL.equals(k);
    }
}
