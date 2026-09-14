package com.apiculture.simulator.domain.game;

import com.apiculture.simulator.data.local.entity.HiveEntity;
import com.apiculture.simulator.domain.population.HivePopulationState;

import java.time.LocalDate;

public class GameBalanceEngine {

    /**
     * Producción diaria máxima teórica por colmena (población tope, mielada plena).
     * Usado para dimensionar el mercado global; no depende de néctar del hex.
     */
    public static double globalMaxTheoreticalDailyKgPerHive() {
        return HiveDailyBiology.maxChartDailyKgForAdults(ColonyGameRules.MAX_ADULT_WORKERS_PER_HIVE);
    }

    public double calculateDailyHoneyKg(HiveEntity hive, Season season) {
        if (hive == null) {
            return 0.0;
        }
        HivePopulationState pop = HivePopulationState.fromHiveEntityOrDefault(hive, hive.beeCount);
        int doy;
        switch (season) {
            case SPRING:
                doy = 120;
                break;
            case SUMMER:
                doy = 180;
                break;
            case AUTUMN:
                doy = 280;
                break;
            case WINTER:
            default:
                doy = 15;
                break;
        }
        LocalDate day = LocalDate.ofYearDay(2024, doy);
        return HiveDailyBiology.netHoneyKg(pop, hive, day, 0, 22.0, 1.0);
    }
}
