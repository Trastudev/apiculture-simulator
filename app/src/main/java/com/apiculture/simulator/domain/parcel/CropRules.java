package com.apiculture.simulator.domain.parcel;

import com.apiculture.simulator.domain.game.FloraBloomWindow;
import com.apiculture.simulator.domain.game.GameCalendar;
import com.apiculture.simulator.domain.market.HoneyMarketEngine;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Cultivos del jugador: anuales (baratos, se retiran al acabar la floración) y árboles
 * (más caros, permanentes, con mantenimiento anual que se paga al payés en el 3D).
 */
public final class CropRules {

    public enum Kind {
        WILD,
        ANNUAL,
        TREE
    }

    public static final int ANNUAL_GROW_DAYS = 2;
    public static final int TREE_GROW_DAYS = 10;
    public static final int ANNUAL_PLANT_EUR = 150;
    public static final int TREE_PLANT_EUR = 640;
    public static final int TREE_MAINTENANCE_EUR = 100;
    /** Lo que paga Pep por la cosecha de fruto de un frutal, una vez por año. */
    public static final int TREE_FRUIT_EUR = 380;
    public static final int ANNUAL_FRUIT_EUR = 90;
    /** Días antes del vencimiento en que el payés ya acepta hacer la poda y el abonado. */
    public static final int TREE_MAINTENANCE_EARLY_DAYS = 30;

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

    public static int fruitSaleEuros(String floraKey) {
        if (isTree(floraKey)) {
            return TREE_FRUIT_EUR;
        }
        return isAnnual(floraKey) ? ANNUAL_FRUIT_EUR : 0;
    }

    /**
     * Fruto vendible: el árbol ya está en producción, no está en flor, y han pasado los días
     * que tarda el fruto desde el fin de la floración (los mismos que usa el apiario 3D).
     */
    public static boolean inFruit(String floraKey, HexParcel parcel, LocalDate today) {
        if (!isTree(floraKey) || today == null || parcel == null) {
            return false;
        }
        if (FloraBloomWindow.daysUntilBloomStart(floraKey, parcel, today) == 0) {
            return false;
        }
        int[] window = fruitDaysAfterBloom(floraKey);
        if (window == null) {
            return false;
        }
        int doy = Math.min(365, today.getDayOfYear());
        int since = 999;
        for (FloraBloomWindow.Span s : FloraBloomWindow.spansForParcel(floraKey, parcel)) {
            int d = Math.floorMod(doy - s.endDoy, 365);
            if (d < since) {
                since = d;
            }
        }
        return since >= window[0] && since <= window[1];
    }

    /** Días tras el fin de flor en que hay fruto, alineados con FarmRules. */
    @androidx.annotation.Nullable
    private static int[] fruitDaysAfterBloom(String floraKey) {
        String k = HoneyMarketEngine.canonicalFloraKey(floraKey);
        if ("Campo de almendros".equals(k)) return new int[] {25, 195};
        if ("Campo de cerezos".equals(k)) return new int[] {25, 70};
        if ("Campo de perales".equals(k)) return new int[] {60, 165};
        if ("Campo de manzanos".equals(k)) return new int[] {70, 175};
        if ("Campo de naranjos".equals(k)) return new int[] {190, 330};
        return null;
    }

    /**
     * Día en que vence el mantenimiento: un año después de plantar o del último pago.
     * {@code lastMaintained} es un día yyyymmdd; las filas antiguas guardaban solo el año (&lt; 10000)
     * y cuentan desde la plantación.
     */
    public static LocalDate maintenanceDueDate(long plantedAtEpochMs, int lastMaintained) {
        LocalDate base = lastMaintained > 10000
                ? GameCalendar.fromDayKey(lastMaintained)
                : Instant.ofEpochMilli(Math.max(1L, plantedAtEpochMs))
                        .atZone(GameCalendar.userTimeZone()).toLocalDate();
        return base.plusYears(1);
    }

    /** Días hasta el vencimiento; negativo si ya pasó. */
    public static long daysUntilMaintenance(long plantedAtEpochMs, int lastMaintained, LocalDate today) {
        return ChronoUnit.DAYS.between(today, maintenanceDueDate(plantedAtEpochMs, lastMaintained));
    }

    public static boolean isMaintenanceDue(long plantedAtEpochMs, int lastMaintained, LocalDate today) {
        return daysUntilMaintenance(plantedAtEpochMs, lastMaintained, today) <= 0;
    }

    public static boolean canMaintainNow(long plantedAtEpochMs, int lastMaintained, LocalDate today) {
        return daysUntilMaintenance(plantedAtEpochMs, lastMaintained, today) <= TREE_MAINTENANCE_EARLY_DAYS;
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
