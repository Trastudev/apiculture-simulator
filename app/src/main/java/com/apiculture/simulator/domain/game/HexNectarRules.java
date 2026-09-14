package com.apiculture.simulator.domain.game;

import com.apiculture.simulator.data.local.entity.HiveEntity;
import com.apiculture.simulator.domain.parcel.HexFlora;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

/**
 * Mielada de un hex: añada anual por planta, desfase climático y cupo si hay demasiadas colmenas
 * sobre la misma plantación.
 */
public final class HexNectarRules {

    private HexNectarRules() {
    }

    public static IberianClimateZone zoneForHive(HiveEntity hive) {
        if (hive == null) {
            return IberianClimateZone.CONTINENTAL;
        }
        int elev = hive.elevationMeters >= 0 ? hive.elevationMeters : 400;
        return IberianClimateZone.forHive(hive.lat, hive.lng, elev);
    }

    public static SouthernAfricanClimateZone southernZoneForHive(HiveEntity hive) {
        if (hive == null) {
            return SouthernAfricanClimateZone.HIGHVELD;
        }
        int elev = hive.elevationMeters >= 0 ? hive.elevationMeters : 800;
        return SouthernAfricanClimateZone.forHive(hive.lat, hive.lng, elev);
    }

    public static boolean isSouthernHive(HiveEntity hive) {
        return hive != null && Hemispheres.isSouthern(hive.lat);
    }

    /**
     * Desfase fenológico respecto al calendario de esa flora.
     * En el hemisferio sur, las especies de calendario ibérico se invierten medio año.
     */
    public static int bloomShiftDays(HiveEntity hive, String floraType) {
        if (isSouthernHive(hive)) {
            int shift = southernZoneForHive(hive).bloomShiftDays();
            if (!HexFlora.usesSouthernCalendar(floraType)) {
                shift += Hemispheres.SEASON_FLIP_DAYS;
            }
            return shift;
        }
        return zoneForHive(hive).bloomShiftDays();
    }

    /** Factor de cosecha del año (misma flora + mismo hex + mismo año → mismo valor en todos los dispositivos). */
    public static double vintageFactor(String hexId, String floraType, int year) {
        String hex = hexId != null && !hexId.isEmpty() ? hexId : "_";
        String flora = floraType != null ? floraType : "Mil flores";
        double u = HoneyDailyProduction.deterministicUniform01(
                "vintage:" + hex + ":" + flora, year * 10_000 + 101);
        double min = GameBalanceConfig.vintageMin;
        double max = GameBalanceConfig.vintageMax;
        return min + u * (max - min);
    }

    public static String vintageLabelEs(double vintage) {
        if (vintage >= 1.08) {
            return "buena";
        }
        if (vintage <= 0.88) {
            return "floja";
        }
        return "media";
    }

    public static boolean isNativeFlora(String hexId, String floraType) {
        return isNativeFlora(hexId, floraType, IberianClimateZone.CONTINENTAL);
    }

    public static boolean isNativeFlora(String hexId, String floraType, IberianClimateZone zone) {
        if (floraType == null || floraType.isEmpty()) {
            return true;
        }
        String nativeKey = HexFlora.randomNativeForZone(hexId, zone);
        return nativeKey.equalsIgnoreCase(HexFlora.canonicalKey(floraType));
    }

    public static boolean isNativeFlora(HiveEntity hive) {
        if (hive == null || hive.floraType == null || hive.floraType.isEmpty()) {
            return true;
        }
        String want = HexFlora.canonicalKey(hive.floraType);
        if (isSouthernHive(hive)) {
            String nativeKey = HexFlora.randomNativeForZone(hive.hexId, southernZoneForHive(hive));
            return want.equalsIgnoreCase(nativeKey);
        }
        return isNativeFlora(hive.hexId, hive.floraType, zoneForHive(hive));
    }

    public static int hiveSlotsForFlora(boolean nativeFlora) {
        return nativeFlora ? GameBalanceConfig.nativeHiveSlots : GameBalanceConfig.plantedHiveSlots;
    }

    /**
     * 1 = sin competencia. Por encima del cupo el pecoreo cae con suavidad (nunca a cero).
     */
    public static double crowdingFactor(int hivesOnSameFlora, boolean nativeFlora) {
        int n = Math.max(1, hivesOnSameFlora);
        int slots = Math.max(1, hiveSlotsForFlora(nativeFlora));
        if (n <= slots) {
            return 1.0;
        }
        double extra = n - slots;
        double f = 1.0 / (1.0 + 0.28 * extra);
        return Math.max(GameBalanceConfig.crowdingFloor, f);
    }

    /**
     * Intensidad de néctar pecoreable (0–~1,15) ya con clima, añada, cupo y un poco de flora secundaria del hex.
     */
    public static double nectar01(
            HiveEntity hive,
            LocalDate day,
            int hivesOnSameFlora,
            List<String> readyFlorasOnHex) {
        if (hive == null || day == null) {
            return 0.0;
        }
        String primary = hive.floraType;
        String hexId = hive.hexId;
        int year = day.getYear();
        double vint = vintageFactor(hexId, primary, year);
        int shift = bloomShiftDays(hive, primary);
        int doy = day.getDayOfYear();
        double primaryN = NectarFlow.intensity01(primary, doy, shift, vint);
        if (!isSouthernHive(hive)) {
            primaryN *= zoneForHive(hive).nectarSeasonMultiplier(doy);
        }
        boolean nativeFlora = isNativeFlora(hive);
        primaryN *= crowdingFactor(hivesOnSameFlora, nativeFlora);

        List<String> ready = readyFlorasOnHex != null ? readyFlorasOnHex : Collections.emptyList();
        if (primaryN < GameBalanceConfig.secondaryNectarTrigger && !ready.isEmpty()) {
            double best = 0.0;
            String want = primary != null ? primary.trim() : "";
            for (String other : ready) {
                if (other == null || other.equalsIgnoreCase(want)) {
                    continue;
                }
                double ov = vintageFactor(hexId, other, year);
                int oShift = bloomShiftDays(hive, other);
                best = Math.max(best, NectarFlow.intensity01(other, day.getDayOfYear(), oShift, ov));
            }
            primaryN += GameBalanceConfig.secondaryNectarShare * best;
        }
        return Math.max(0.0, Math.min(GameBalanceConfig.nectarIntensityCap, primaryN));
    }
}
