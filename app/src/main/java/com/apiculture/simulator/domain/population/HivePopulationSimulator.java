package com.apiculture.simulator.domain.population;

import com.apiculture.simulator.data.local.entity.HiveEntity;
import com.apiculture.simulator.domain.game.ColonyGameRules;
import com.apiculture.simulator.domain.game.Hemispheres;
import com.apiculture.simulator.domain.game.HiveCareRules;
import com.apiculture.simulator.domain.game.HiveDailyBiology;
import com.apiculture.simulator.domain.game.HiveHoneyRules;
import com.apiculture.simulator.domain.game.HoneyDailyProduction;

import java.time.LocalDate;

/**
 * Paso diario de colonia: capacidad de carga estacional + máquina de estados de la reina.
 * Ya no envejece obreras por edad ni desplaza una cola de cría día a día.
 */
public final class HivePopulationSimulator {

    private HivePopulationSimulator() {
    }

    /** Marca la reina como muerta e inicia ventana de reemplazo (días 0–3). */
    public static void markQueenDead(HivePopulationState s) {
        if (s == null || s.queenMode == QueenMode.COLLAPSED) {
            return;
        }
        s.queenMode = QueenMode.REPLACE_WINDOW;
        s.replaceWindowDay = 0;
        s.queenPipelineDay = 0;
    }

    public static void applyDay(HivePopulationState s, HiveEntity hive, LocalDate day, int dayKey) {
        applyDay(s, hive, day, dayKey, null);
    }

    public static void applyDay(
            HivePopulationState s, HiveEntity hive, LocalDate day, int dayKey, Double tempC) {
        if (s == null) {
            return;
        }
        String hid = hive != null && hive.id != null ? hive.id : "";
        int doy = Hemispheres.biologicalDayOfYear(
                day.getDayOfYear(), hive != null ? hive.lat : null);

        s.lastDayWorkerDeaths = 0;
        s.lastDayWorkerEmergences = 0;
        s.lastDayEggsLaid = 0;
        s.lastDaySwarmed = false;
        s.lastDayQueenDied = false;

        int n0 = Math.max(0, s.workersAdult);

        int swarmLoss = 0;
        if (s.queenMode == QueenMode.LAYING
                && HiveDailyBiology.isSwarmSeason(doy)
                && n0 > ColonyGameRules.SWARM_RISK_BASE_BEES) {
            double risk = ColonyGameRules.swarmRiskForAdultWorkers(n0);
            double u = HoneyDailyProduction.deterministicUniform01(hid + ":swarm", dayKey);
            if (u < risk) {
                swarmLoss = n0 / 2;
                s.workersAdult = n0 - swarmLoss;
                s.lastDaySwarmed = true;
            }
        }

        if (s.queenMode == QueenMode.LAYING) {
            double uq = HoneyDailyProduction.deterministicUniform01(hid + ":queenDeath", dayKey);
            if (uq < HiveCareRules.QUEEN_DAILY_DEATH_P) {
                markQueenDead(s);
                s.lastDayQueenDied = true;
            }
        }

        if (s.queenMode == QueenMode.COLLAPSED) {
            applyAdultChange(s, hive, doy, dayKey, swarmLoss, tempC);
            s.applyEstimatedBroodFromDailyLaying(0);
            s.lastTrend = HivePopulationTrend.COLLAPSED;
            s.clampNonNegative();
            HivePopulationState.partitionWorkerAdultsEvenly(s);
            return;
        }

        advanceQueenReplacementPipeline(s);
        if (s.queenMode == QueenMode.REPLACE_WINDOW && s.replaceWindowDay < 4) {
            tryStartQueenCell(s);
        }

        int next = HiveDailyBiology.nextAdultWorkers(s, hive, doy, dayKey, tempC);
        int n1 = Math.max(0, s.workersAdult);
        int net = next - n1;
        int health = hive != null ? hive.health : 80;
        double honey = hive != null ? Math.max(0.0, hive.honeyProduction) : HiveHoneyRules.MIN_HIVE_STOCK_KG;
        int naturalDeaths = HiveDailyBiology.expectedNaturalDeaths(n1, doy, health, tempC, honey);
        int emergences = naturalDeaths + net;
        if (emergences < 0) {
            naturalDeaths -= emergences;
            emergences = 0;
        }
        s.workersAdult = next;
        if (s.workersAdult > ColonyGameRules.MAX_ADULT_WORKERS_PER_HIVE) {
            int over = s.workersAdult - ColonyGameRules.MAX_ADULT_WORKERS_PER_HIVE;
            s.workersAdult = ColonyGameRules.MAX_ADULT_WORKERS_PER_HIVE;
            naturalDeaths += over;
        }

        s.lastDayWorkerDeaths = swarmLoss + naturalDeaths;
        s.lastDayWorkerEmergences = emergences;

        int eggs = HiveDailyBiology.eggsLaidToday(s, hive, day, dayKey, tempC);
        s.lastDayEggsLaid = eggs;
        if (s.queenMode == QueenMode.LAYING) {
            s.daysWithoutEggLaying = 0;
        } else {
            s.daysWithoutEggLaying++;
        }
        s.applyEstimatedBroodFromDailyLaying(eggs);

        if (s.queenMode == QueenMode.REPLACE_WINDOW) {
            s.replaceWindowDay++;
            if (s.replaceWindowDay >= 4) {
                s.queenMode = QueenMode.ORPHANED;
            }
        }

        if (s.workersAdult < HivePopulationState.MIN_BEES_COLLAPSE) {
            s.queenMode = QueenMode.COLLAPSED;
            s.lastDayEggsLaid = 0;
            s.applyEstimatedBroodFromDailyLaying(0);
        }

        s.lastTrend = computeTrend(n0, s.workersAdult, s.queenMode);
        s.clampNonNegative();
        HivePopulationState.partitionWorkerAdultsEvenly(s);
    }

    private static void applyAdultChange(
            HivePopulationState s, HiveEntity hive, int doy, int dayKey, int swarmLoss, Double tempC) {
        int n1 = Math.max(0, s.workersAdult);
        int next = HiveDailyBiology.nextAdultWorkers(s, hive, doy, dayKey, tempC);
        int net = next - n1;
        int health = hive != null ? hive.health : 80;
        double honey = hive != null ? Math.max(0.0, hive.honeyProduction) : HiveHoneyRules.MIN_HIVE_STOCK_KG;
        int naturalDeaths = HiveDailyBiology.expectedNaturalDeaths(n1, doy, health, tempC, honey);
        int emergences = naturalDeaths + net;
        if (emergences < 0) {
            naturalDeaths -= emergences;
            emergences = 0;
        }
        s.workersAdult = Math.max(0, next);
        s.lastDayWorkerDeaths = swarmLoss + naturalDeaths;
        s.lastDayWorkerEmergences = emergences;
        s.lastDayEggsLaid = 0;
    }

    private static void tryStartQueenCell(HivePopulationState s) {
        if (s.workersAdult < 2_500 || s.daysWithoutEggLaying > 4) {
            return;
        }
        s.queenMode = QueenMode.QUEEN_DEVELOPING;
        s.queenPipelineDay = 0;
    }

    private static void advanceQueenReplacementPipeline(HivePopulationState s) {
        if (s.queenMode == QueenMode.QUEEN_DEVELOPING) {
            s.queenPipelineDay++;
            if (s.queenPipelineDay > HivePopulationState.QUEEN_DEVELOP_LAST_DAY) {
                s.queenMode = QueenMode.VIRGIN_PRE_LAYING;
                s.queenPipelineDay = 0;
            }
        } else if (s.queenMode == QueenMode.VIRGIN_PRE_LAYING) {
            s.queenPipelineDay++;
            if (s.queenPipelineDay > HivePopulationState.VIRGIN_LAST_DAY) {
                s.queenMode = QueenMode.LAYING;
                s.queenPipelineDay = 0;
                s.daysWithoutEggLaying = 0;
            }
        }
    }

    private static HivePopulationTrend computeTrend(int beforeTotal, int afterTotal, QueenMode mode) {
        if (mode == QueenMode.COLLAPSED || afterTotal < HivePopulationState.MIN_BEES_COLLAPSE) {
            return HivePopulationTrend.COLLAPSED;
        }
        if (mode == QueenMode.ORPHANED) {
            return HivePopulationTrend.CRITICAL_ORPHAN;
        }
        int delta = afterTotal - beforeTotal;
        int th = Math.max(80, beforeTotal / 200);
        if (delta > th) {
            return HivePopulationTrend.GROWING;
        }
        if (delta < -th) {
            return HivePopulationTrend.DECLINING;
        }
        return HivePopulationTrend.STABLE;
    }
}
