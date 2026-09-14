package com.apiculture.simulator.domain.health;

import com.apiculture.simulator.data.local.entity.HiveEntity;
import com.apiculture.simulator.domain.game.GameBalanceConfig;
import com.apiculture.simulator.domain.game.Hemispheres;
import com.apiculture.simulator.domain.game.HexNectarRules;
import com.apiculture.simulator.domain.game.IberianClimateZone;
import com.apiculture.simulator.domain.game.Season;

/**
 * Reproducción de varroa: proporcional a la puesta (cría operculada), más lenta en
 * montaña y frío. El tratamiento (no este módulo) corta la multiplicación.
 */
public final class VarroaGrowth {

    private VarroaGrowth() {
    }

    /**
     * Tasa relativa diaria ya recortada por cría, montaña y temperatura.
     */
    public static double relativeDailyRate(
            HiveEntity hive, Season season, Double tempCelsius, double baseRate, int eggsLaid) {
        double base = baseRate > 0 ? baseRate : GameBalanceConfig.varroaBaseRelativeDailyRate;
        return base * environmentMultiplier(hive, season, tempCelsius) * broodMultiplier(eggsLaid);
    }

    /**
     * 0 huevos → suelo forético; puesta de referencia → 1.0.
     */
    public static double broodMultiplier(int eggsLaid) {
        double floor = GameBalanceConfig.varroaBroodMultFloor;
        double cap = GameBalanceConfig.varroaBroodMultCap;
        double ref = Math.max(1.0, GameBalanceConfig.varroaEggsRefPerDay);
        if (eggsLaid <= 0) {
            return floor;
        }
        return Math.max(floor, Math.min(cap, eggsLaid / ref));
    }

    public static double environmentMultiplier(HiveEntity hive, Season season, Double tempCelsius) {
        double m = mountainMultiplier(hive);
        m *= temperatureMultiplier(tempCelsius);
        return Math.max(GameBalanceConfig.varroaEnvMultFloor, m);
    }

    /** Conservado por compatibilidad; la estación ya no escala la varroa (lo hace la puesta). */
    static double seasonMultiplier(Season season) {
        if (season == Season.SUMMER) {
            return GameBalanceConfig.summerVarroaSeasonMult;
        }
        if (season == Season.WINTER) {
            return GameBalanceConfig.winterVarroaSeasonMult;
        }
        if (season == Season.AUTUMN) {
            return GameBalanceConfig.autumnVarroaSeasonMult;
        }
        return 1.0;
    }

    static double mountainMultiplier(HiveEntity hive) {
        if (hive == null) {
            return 1.0;
        }
        boolean mountainZone = !Hemispheres.isSouthern(hive.lat)
                && HexNectarRules.zoneForHive(hive) == IberianClimateZone.MOUNTAIN;
        int elev = hive.elevationMeters;
        if (mountainZone) {
            return GameBalanceConfig.mountainVarroaMult;
        }
        if (elev >= GameBalanceConfig.highElevVarroaStartM) {
            return GameBalanceConfig.highElevVarroaMult;
        }
        return 1.0;
    }

    static double temperatureMultiplier(Double tempCelsius) {
        if (tempCelsius == null || Double.isNaN(tempCelsius)) {
            return 1.0;
        }
        double t = tempCelsius;
        if (t < GameBalanceConfig.varroaFreezeC) {
            return GameBalanceConfig.varroaFreezeMult;
        }
        if (t < GameBalanceConfig.varroaColdC) {
            return GameBalanceConfig.varroaColdMult;
        }
        if (t < GameBalanceConfig.varroaChillyC) {
            return GameBalanceConfig.varroaChillyMult;
        }
        return 1.0;
    }
}
