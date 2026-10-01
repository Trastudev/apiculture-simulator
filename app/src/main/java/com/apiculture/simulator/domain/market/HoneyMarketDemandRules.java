package com.apiculture.simulator.domain.market;

import com.apiculture.simulator.domain.game.GameBalanceConfig;

/**
 * Reglas puras de escala del mercado: actividad por jugador, cuota regional y
 * separación entre cupo disponible y objetivo económico de rotación.
 */
public final class HoneyMarketDemandRules {

    private HoneyMarketDemandRules() {
    }

    /** Multiplicador lineal y aditivo por nivel: 1 + factor × min(nivel, tope). */
    public static double levelMultiplier(int playerLevel) {
        int cap = Math.max(0, GameBalanceConfig.honeyMarketLevelDemandCap);
        int level = Math.max(0, Math.min(cap, playerLevel));
        return 1.0 + Math.max(0.0, GameBalanceConfig.honeyMarketLevelDemandFactor) * level;
    }

    /**
     * Actividad de un jugador en una región. Una presence mínima evita que una
     * cuenta recién creada desaparezca del mercado; las colmenas reales mandan
     * cuando ya hay producción.
     */
    public static double playerRegionActivity(int hiveCount, int playerLevel) {
        int floor = Math.max(0, GameBalanceConfig.honeyMarketMinActivityPerPlayerRegion);
        double hives = Math.max(floor, hiveCount);
        return hives * levelMultiplier(playerLevel);
    }

    /** Cupo regional por unidad de actividad y tipo de miel. */
    public static double capacityKgPerActivityUnit(double floraRelativeDemand) {
        double peakKg = com.apiculture.simulator.domain.game.HiveDailyBiology.maxChartDailyKgForAdults(
                com.apiculture.simulator.domain.game.ColonyGameRules.MAX_ADULT_WORKERS_PER_HIVE);
        return peakKg
                * Math.max(0.0, GameBalanceConfig.honeyMarketTypicalOutputFraction)
                * Math.max(0.0, GameBalanceConfig.honeyMarketCustomerAbsorptionFraction)
                * Math.max(0.0, GameBalanceConfig.honeyMarketCapacityDaysBuffer)
                * Math.max(0.0, GameBalanceConfig.honeyMarketCapacityExtraMultiplier)
                * Math.max(0.0, floraRelativeDemand);
    }

    /** Objetivo diario que usa el precio; no bloquea ventas como el cupo regional. */
    public static double turnoverKgPerActivityUnit(double floraRelativeDemand) {
        double peakKg = com.apiculture.simulator.domain.game.HiveDailyBiology.maxChartDailyKgForAdults(
                com.apiculture.simulator.domain.game.ColonyGameRules.MAX_ADULT_WORKERS_PER_HIVE);
        return peakKg
                * Math.max(0.0, GameBalanceConfig.honeyMarketTypicalOutputFraction)
                * Math.max(0.0, GameBalanceConfig.honeyMarketCustomerAbsorptionFraction)
                * Math.max(0.0, floraRelativeDemand);
    }
}
