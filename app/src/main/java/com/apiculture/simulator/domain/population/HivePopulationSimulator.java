package com.apiculture.simulator.domain.population;

import com.apiculture.simulator.data.local.entity.HiveEntity;
import com.apiculture.simulator.domain.game.ColonyGameRules;
import com.apiculture.simulator.domain.game.HoneyDailyProduction;
import com.apiculture.simulator.domain.game.Season;

import java.time.LocalDate;

/**
 * Simulación poblacional diaria (cola de cría obrera + estados de reina).
 */
public final class HivePopulationSimulator {

    private HivePopulationSimulator() {
    }

    /** Marca la reina como muerta e inicia ventana de reemplazo (días 0–3 con huevos válidos). */
    public static void markQueenDead(HivePopulationState s) {
        if (s == null || s.queenMode == QueenMode.COLLAPSED) {
            return;
        }
        s.queenMode = QueenMode.REPLACE_WINDOW;
        s.replaceWindowDay = 0;
        s.queenPipelineDay = 0;
    }

    public static void applyDay(HivePopulationState s, HiveEntity hive, LocalDate day, int dayKey) {
        Season season = Season.fromDayOfYear(day.getDayOfYear());
        String hid = hive.id != null ? hive.id : "";

        s.lastDayWorkerDeaths = 0;
        s.lastDayWorkerEmergences = 0;
        s.lastDayEggsLaid = 0;
        s.lastDaySwarmed = false;

        ensureWorkerAdultAgeCohorts(s);

        int adultWorkersBefore = s.workersAdult;
        int swarmWorkerLoss = 0;
        if (adultWorkersBefore > ColonyGameRules.SWARM_RISK_BASE_BEES) {
            int steps = (adultWorkersBefore - ColonyGameRules.SWARM_RISK_BASE_BEES)
                    / ColonyGameRules.SWARM_RISK_STEP_BEES;
            if (steps > 0) {
                double risk = Math.min(1.0, steps * ColonyGameRules.SWARM_RISK_PER_STEP);
                double u = HoneyDailyProduction.deterministicUniform01(hid + ":swarm", dayKey);
                if (u < risk) {
                    swarmWorkerLoss = applySwarmLoseHalfAdults(s);
                    s.lastDaySwarmed = true;
                }
            }
        }

        int trendBaseline = s.totalBees();

        if (s.queenMode == QueenMode.COLLAPSED) {
            s.lastDayWorkerDeaths = swarmWorkerLoss + applyWorkerMortality(s, day, hid, dayKey);
            s.lastDayWorkerEmergences = applyEmergenceBroodShiftAndWorkerAging(s);
            capAdultWorkersAtMax(s);
            s.lastTrend = HivePopulationTrend.COLLAPSED;
            s.clampNonNegative();
            return;
        }

        s.lastDayWorkerDeaths = swarmWorkerLoss + applyWorkerMortality(s, day, hid, dayKey);
        s.lastDayWorkerEmergences = applyEmergenceBroodShiftAndWorkerAging(s);
        capAdultWorkersAtMax(s);

        advanceQueenReplacementPipeline(s);

        if (s.queenMode == QueenMode.REPLACE_WINDOW && s.replaceWindowDay < 4) {
            tryStartQueenCell(s);
        }

        if (s.queenMode == QueenMode.LAYING) {
            int eggs = computeEggsToLay(s, season, hive, hid, dayKey);
            s.workerBrood[0] += eggs;
            s.lastDayEggsLaid = eggs;
            s.daysWithoutEggLaying = 0;
        } else {
            s.daysWithoutEggLaying++;
        }

        if (s.queenMode == QueenMode.REPLACE_WINDOW) {
            s.replaceWindowDay++;
            if (s.replaceWindowDay >= 4) {
                s.queenMode = QueenMode.ORPHANED;
            }
        }

        if (s.totalBees() < HivePopulationState.MIN_BEES_COLLAPSE) {
            s.queenMode = QueenMode.COLLAPSED;
        }

        s.lastTrend = computeTrend(trendBaseline, s.totalBees(), s.queenMode);
        s.clampNonNegative();
    }

    /**
     * Enjambrazón: se marcha la mitad de las obreras voladoras; la cría permanece en la colmena.
     *
     * @return obreras perdidas ese día por el enjambre (para el balance neto del día).
     */
    private static void ensureWorkerAdultAgeCohorts(HivePopulationState s) {
        if (s == null) {
            return;
        }
        int b = WorkerAdultLifespan.WORKER_ADULT_AGE_BUCKETS;
        if (s.workerAdultByAge == null || s.workerAdultByAge.length != b) {
            s.workerAdultByAge = new int[b];
        }
        int sum = HivePopulationState.sumWorkerAdultByAge(s);
        if (sum != s.workersAdult) {
            HivePopulationState.partitionWorkerAdultsEvenly(s);
        }
    }

    /** Quita {@code count} obreras empezando por las cohortes de mayor edad (enjambre ≈ mitad voladora). */
    private static void removeAdultWorkersFromOldestFirst(HivePopulationState s, int count) {
        if (s == null || s.workerAdultByAge == null || count <= 0) {
            return;
        }
        int left = count;
        for (int i = s.workerAdultByAge.length - 1; i >= 0 && left > 0; i--) {
            int t = Math.min(s.workerAdultByAge[i], left);
            s.workerAdultByAge[i] -= t;
            left -= t;
        }
        HivePopulationState.syncWorkersAdultFromBuckets(s);
    }

    private static int applySwarmLoseHalfAdults(HivePopulationState s) {
        int before = s.workersAdult;
        removeAdultWorkersFromOldestFirst(s, before / 2);
        s.clampNonNegative();
        return before - s.workersAdult;
    }

    /**
     * Sobrepoblación de <strong>obreras adultas</strong>: las que excedan el tope se eliminan por edad
     * (las más viejas primero). La cría no se recorta por este tope.
     */
    private static void capAdultWorkersAtMax(HivePopulationState s) {
        int cap = ColonyGameRules.MAX_ADULT_WORKERS_PER_HIVE;
        int over = s.workersAdult - cap;
        if (over <= 0) {
            return;
        }
        removeAdultWorkersFromOldestFirst(s, over);
        s.lastDayWorkerDeaths += over;
    }

    /**
     * Mortalidad por cohorte según esperanza de vida media del día del año (suave a lo largo del año);
     * riesgo base ~1/L con mayor peso en edades altas. El % de salud no modifica aún la mortalidad.
     */
    private static int applyWorkerMortality(HivePopulationState s, LocalDate day, String hiveId, int dayKey) {
        if (s.workersAdult <= 0 || s.workerAdultByAge == null) {
            return 0;
        }
        double L = WorkerAdultLifespan.smoothedMeanLifespanDays(day.getDayOfYear());
        L = Math.max(24.0, Math.min(200.0, L));
        int totalDeaths = 0;
        int b = s.workerAdultByAge.length;
        for (int age = 0; age < b; age++) {
            int n = s.workerAdultByAge[age];
            if (n <= 0) {
                continue;
            }
            double ageNorm = age / Math.max(1.0, L);
            double mult = 1.0 + ageNorm * ageNorm;
            double p = (1.0 / L) * mult;
            p = Math.min(0.35, p);
            double expected = n * p;
            int d = (int) Math.floor(expected);
            double frac = expected - d;
            double u = HoneyDailyProduction.deterministicUniform01(hiveId + ":wm:" + age, dayKey);
            if (u < frac) {
                d++;
            }
            d = Math.min(n, d);
            s.workerAdultByAge[age] -= d;
            totalDeaths += d;
        }
        HivePopulationState.syncWorkersAdultFromBuckets(s);
        return totalDeaths;
    }

    /**
     * Emergencia de pupa, avance del pipeline de cría y envejecimiento de adultas (un día); las emergidas entran en edad 0.
     *
     * @return obreras que emergen de pupa ese día.
     */
    private static int applyEmergenceBroodShiftAndWorkerAging(HivePopulationState s) {
        int emerged = s.workerBrood[HivePopulationState.PUPA_LAST_DAY];
        for (int i = HivePopulationState.PUPA_LAST_DAY; i >= 1; i--) {
            s.workerBrood[i] = s.workerBrood[i - 1];
        }
        s.workerBrood[0] = 0;

        if (s.workerAdultByAge == null) {
            s.workersAdult += emerged;
            return emerged;
        }
        int b = s.workerAdultByAge.length;
        int[] next = new int[b];
        next[0] = emerged;
        for (int a = 1; a < b - 1; a++) {
            next[a] = s.workerAdultByAge[a - 1];
        }
        next[b - 1] = s.workerAdultByAge[b - 2] + s.workerAdultByAge[b - 1];
        System.arraycopy(next, 0, s.workerAdultByAge, 0, b);
        HivePopulationState.syncWorkersAdultFromBuckets(s);
        return emerged;
    }

    private static void tryStartQueenCell(HivePopulationState s) {
        int eggs = 0;
        for (int i = 0; i <= HivePopulationState.EGG_LAST_DAY; i++) {
            eggs += s.workerBrood[i];
        }
        if (eggs < HivePopulationState.MIN_EGGS_TO_START_QUEEN_CELL) {
            return;
        }
        int take = Math.min(HivePopulationState.QUEEN_CELL_TAKE, eggs);
        subtractFromYoungestBrood(s, HivePopulationState.EGG_LAST_DAY, take);
        s.queenMode = QueenMode.QUEEN_DEVELOPING;
        s.queenPipelineDay = 0;
    }

    private static void subtractFromYoungestBrood(HivePopulationState s, int maxStage, int amount) {
        int left = amount;
        for (int i = 0; i <= maxStage && left > 0; i++) {
            int t = Math.min(s.workerBrood[i], left);
            s.workerBrood[i] -= t;
            left -= t;
        }
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

    private static int computeEggsToLay(HivePopulationState s, Season season, HiveEntity hive,
                                        String hiveId, int dayKey) {
        int min;
        int max;
        switch (season) {
            case SPRING:
                min = 1_000;
                max = 1_800;
                break;
            case SUMMER:
                min = 1_400;
                max = 2_000;
                break;
            case AUTUMN:
                min = 600;
                max = 1_200;
                break;
            case WINTER:
            default:
                min = 100;
                max = 450;
                break;
        }
        double u = HoneyDailyProduction.deterministicUniform01(hiveId + ":lay", dayKey);
        int target = min + (int) Math.floor(u * (max - min + 1));
        if (target > max) {
            target = max;
        }
        // Genética 0–100 en tramos de 10 puntos: 50 % de puesta en tramo 0–9 y +10 % por cada tramo (100 → 150 %).
        int q = hive != null ? hive.queenGeneticQuality : 50;
        q = Math.max(0, Math.min(100, q));
        int geneticBracket = q / 10;
        double geneticFactor = 0.5 + 0.1 * geneticBracket;
        target = (int) Math.floor(target * geneticFactor);
        double reserves = hive != null ? hive.reserves : 0;
        double factor = Math.min(1.0, Math.max(0.25, reserves / 45.0));
        target = (int) Math.floor(target * factor);
        int room = ColonyGameRules.MAX_TOTAL_COLONY_BEES_SOFT_CAP - s.totalBees();
        target = Math.max(0, Math.min(target, room));
        double stressMult = 1.0;
        if (hive != null && hive.varroaPct > 8.0) {
            double t = Math.min(1.0, (hive.varroaPct - 8.0) / 22.0);
            stressMult *= 1.0 - 0.2 * t;
        }
        if (hive != null && hive.health < 50) {
            stressMult *= 0.5 + 0.3 * (hive.health / 50.0);
        }
        target = (int) Math.floor(target * stressMult);
        return Math.max(0, target);
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
