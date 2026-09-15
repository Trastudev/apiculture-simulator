package com.apiculture.simulator.domain.game;

import com.apiculture.simulator.data.local.entity.HiveEntity;
import com.apiculture.simulator.domain.population.HivePopulationState;
import com.apiculture.simulator.domain.population.QueenMode;
import com.apiculture.simulator.domain.population.WorkerAdultLifespan;

import java.time.LocalDate;
import java.util.List;

/**
 * Fórmula diaria unificada de colonia (sin envejecer cohortes ni mover una cola de cría).
 * <p>
 * Población: las obreras adultas se acercan a una capacidad de carga estacional K
 * (logística suave; la bajada es más lenta que la subida). La cría de la UI es una
 * instantánea a partir de la puesta de hoy.
 * <p>
 * Miel neta: pecoreo (nodrizas no cuentan) menos consumo de mantenimiento y cría.
 * En invierno o con lluvia el pecoreo cae a casi 0 y se come de las reservas.
 */
public final class HiveDailyBiology {

    private HiveDailyBiology() {
    }

    public static boolean isSwarmSeason(int dayOfYear) {
        int d = Math.max(1, Math.min(366, dayOfYear));
        return d >= GameBalanceConfig.swarmSeasonStartDoy && d <= GameBalanceConfig.swarmSeasonEndDoy;
    }

    /**
     * Capacidad de carga de obreras adultas (Iberia, hemisferio norte) antes de modificadores.
     */
    public static int seasonalCarryingCapacityAdults(int dayOfYear) {
        return (int) Math.round(GameBalanceConfig.lerpIntKnots(
                dayOfYear, GameBalanceConfig.kDoy, GameBalanceConfig.kAdults));
    }

    public static int effectiveCarryingCapacityAdults(HivePopulationState s, HiveEntity hive, int dayOfYear) {
        return effectiveCarryingCapacityAdults(s, hive, dayOfYear, null);
    }

    public static int effectiveCarryingCapacityAdults(
            HivePopulationState s, HiveEntity hive, int dayOfYear, Double tempCelsius) {
        double k = seasonalCarryingCapacityAdults(dayOfYear);
        int q = hive != null ? Math.max(0, Math.min(100, hive.queenGeneticQuality)) : 50;
        k *= GameBalanceConfig.queenKFactorMin + GameBalanceConfig.queenKFactorSpan * (q / 100.0);
        int health = hive != null ? Math.max(0, Math.min(100, hive.health)) : 80;
        k *= GameBalanceConfig.healthKFactorMin + GameBalanceConfig.healthKFactorSpan * (health / 100.0);
        k *= TemperatureStress.carryingCapacityMultiplier(tempCelsius);
        double varroa = hive != null ? Math.max(0.0, hive.varroaPct) : 0.0;
        if (varroa > GameBalanceConfig.varroaKStartPct) {
            double t = Math.min(1.0, (varroa - GameBalanceConfig.varroaKStartPct) / GameBalanceConfig.varroaKSpanPct);
            k *= 1.0 - GameBalanceConfig.varroaKMaxPenalty * t;
        }
        double honey = hive != null ? Math.max(0.0, hive.honeyProduction) : HiveHoneyRules.MIN_HIVE_STOCK_KG;
        k *= HiveHoneyRules.lowHoneyScale(honey, GameBalanceConfig.minHoneyKFactorFloor);
        QueenMode mode = s != null ? s.queenMode : QueenMode.LAYING;
        if (mode != QueenMode.LAYING) {
            k *= GameBalanceConfig.notLayingKMultiplier;
        }
        if (mode == QueenMode.COLLAPSED) {
            k = HivePopulationState.MIN_BEES_COLLAPSE * 0.25;
        }
        int cap = ColonyGameRules.MAX_ADULT_WORKERS_PER_HIVE;
        return Math.max(0, Math.min(cap, (int) Math.round(k)));
    }

    /**
     * Puesta diaria estimada (huevos/día). 0 si la reina no está en puesta.
     * Pico de primavera, segundo pulso a mediados de agosto, valle de calor y
     * pico de otoño a mediados de noviembre. El clima recorta la puesta en verano
     * (sur el que más; alta montaña no); la temperatura recorta en canícula o helada.
     */
    public static int eggsLaidToday(HivePopulationState s, HiveEntity hive, LocalDate day, int dayKey) {
        return eggsLaidToday(s, hive, day, dayKey, null);
    }

    public static int eggsLaidToday(
            HivePopulationState s, HiveEntity hive, LocalDate day, int dayKey, Double tempCelsius) {
        if (s == null || s.queenMode != QueenMode.LAYING || s.workersAdult <= 0) {
            return 0;
        }
        int doy = Hemispheres.biologicalDayOfYear(
                day.getDayOfYear(), hive != null ? hive.lat : null);
        double base = GameBalanceConfig.eggsBaseForDay(doy);
        int q = hive != null ? Math.max(0, Math.min(100, hive.queenGeneticQuality)) : 50;
        base *= 0.55 + 0.45 * (q / 100.0);
        double strength = Math.min(1.0, s.workersAdult / GameBalanceConfig.eggsStrengthRefAdults);
        base *= 0.35 + 0.65 * strength;
        int health = hive != null ? Math.max(0, Math.min(100, hive.health)) : 80;
        base *= 0.50 + 0.50 * (health / 100.0);
        if (hive != null && hive.varroaPct > 8.0) {
            double t = Math.min(1.0, (hive.varroaPct - 8.0) / 22.0);
            base *= 1.0 - 0.35 * t;
        }
        double honey = hive != null ? Math.max(0.0, hive.honeyProduction) : HiveHoneyRules.MIN_HIVE_STOCK_KG;
        base *= HiveHoneyRules.lowHoneyScale(honey, GameBalanceConfig.eggsLowHoneyFactorFloor);
        base *= TemperatureLayingModifier.layingMultiplierForCelsius(tempCelsius);
        if (!HexNectarRules.isSouthernHive(hive)) {
            base *= HexNectarRules.zoneForHive(hive).layingSeasonMultiplier(doy);
        }
        base *= HiveFeedingBonuses.broodMultiplierForDay(hive, dayKey);
        String hid = hive != null && hive.id != null ? hive.id : "_";
        double u = HoneyDailyProduction.deterministicUniform01(hid + ":lay", dayKey);
        base *= GameBalanceConfig.layNoiseMin + u * GameBalanceConfig.layNoiseSpan;
        int room = ColonyGameRules.MAX_ADULT_WORKERS_PER_HIVE - s.workersAdult;
        int maxEggs = Math.max(0, Math.min(GameBalanceConfig.eggsMaxPerDay,
                room + GameBalanceConfig.eggsMaxPerDayRoomBonus));
        return Math.max(0, Math.min(maxEggs, (int) Math.round(base)));
    }

    public static int expectedNaturalDeaths(int adultWorkers, int dayOfYear) {
        return expectedNaturalDeaths(adultWorkers, dayOfYear, 80, null, HiveHoneyRules.MIN_HIVE_STOCK_KG);
    }

    public static int expectedNaturalDeaths(int adultWorkers, int dayOfYear, int health, Double tempCelsius) {
        return expectedNaturalDeaths(adultWorkers, dayOfYear, health, tempCelsius, HiveHoneyRules.MIN_HIVE_STOCK_KG);
    }

    public static int expectedNaturalDeaths(
            int adultWorkers, int dayOfYear, int health, Double tempCelsius, double honeyKg) {
        if (adultWorkers <= 0) {
            return 0;
        }
        double life = WorkerAdultLifespan.smoothedMeanLifespanDays(dayOfYear);
        life *= HiveHoneyRules.lowHoneyScale(honeyKg, GameBalanceConfig.lowHoneyLifespanFloor);
        life = Math.max(GameBalanceConfig.workerLifespanClampMin,
                Math.min(GameBalanceConfig.workerLifespanClampMax, life));
        double base = adultWorkers / life;
        base *= TemperatureStress.mortalityMultiplier(health, tempCelsius);
        return Math.min(adultWorkers, (int) Math.round(base));
    }

    /**
     * Obreras adultas al final del día (sin enjambre; el llamador lo aplica antes).
     */
    public static int nextAdultWorkers(HivePopulationState s, HiveEntity hive, int dayOfYear, int dayKey) {
        return nextAdultWorkers(s, hive, dayOfYear, dayKey, null);
    }

    public static int nextAdultWorkers(
            HivePopulationState s, HiveEntity hive, int dayOfYear, int dayKey, Double tempCelsius) {
        if (s == null) {
            return 0;
        }
        int n = Math.max(0, s.workersAdult);
        if (s.queenMode == QueenMode.COLLAPSED) {
            int drop = Math.max(GameBalanceConfig.collapsedDailyDropMin,
                    n / Math.max(1, GameBalanceConfig.collapsedDailyDropDivisor));
            return Math.max(0, n - drop);
        }
        int health = hive != null ? hive.health : 80;
        double honey = hive != null ? Math.max(0.0, hive.honeyProduction) : HiveHoneyRules.MIN_HIVE_STOCK_KG;
        int deaths = expectedNaturalDeaths(n, dayOfYear, health, tempCelsius, honey);
        if (s.queenMode != QueenMode.LAYING) {
            double rate = (s.queenMode == QueenMode.ORPHANED) ? 1.0 : 0.55;
            int drop = Math.max(deaths, (int) Math.round(deaths * rate));
            if (s.queenMode == QueenMode.QUEEN_DEVELOPING || s.queenMode == QueenMode.VIRGIN_PRE_LAYING
                    || s.queenMode == QueenMode.REPLACE_WINDOW) {
                drop = Math.max(1, (int) Math.round(deaths * 0.55));
            }
            return Math.max(0, n - drop);
        }
        int k = effectiveCarryingCapacityAdults(s, hive, dayOfYear, tempCelsius);
        double feed = HiveFeedingBonuses.broodMultiplierForDay(hive, dayKey);
        if (feed > 1.0001) {
            k = Math.min(ColonyGameRules.MAX_ADULT_WORKERS_PER_HIVE,
                    (int) Math.round(k * Math.min(GameBalanceConfig.feedBroodKCap, feed)));
        }
        double lambda = (k >= n) ? GameBalanceConfig.lambdaTowardK : GameBalanceConfig.lambdaTowardKDown;
        int delta = (int) Math.round(lambda * (k - n));
        int next = n + delta;
        int extraStarvation = deaths - expectedNaturalDeaths(
                n, dayOfYear, health, tempCelsius, HiveHoneyRules.MIN_HIVE_STOCK_KG);
        if (extraStarvation > 0) {
            next -= extraStarvation;
        }
        return Math.max(0, Math.min(ColonyGameRules.MAX_ADULT_WORKERS_PER_HIVE, next));
    }

    public static double grossForageKg(HivePopulationState s, HiveEntity hive, LocalDate day, int dayKey,
                                       Double tempC, double skyMult) {
        return grossForageKg(s, hive, day, dayKey, tempC, skyMult, 1, null);
    }

    public static double grossForageKg(HivePopulationState s, HiveEntity hive, LocalDate day, int dayKey,
                                       Double tempC, double skyMult, int hivesOnSameFlora,
                                       List<String> readyFlorasOnHex) {
        if (s == null || s.workersAdult <= 0 || s.queenMode == QueenMode.COLLAPSED) {
            return 0.0;
        }
        if (TranshumanceRules.isInTransit(hive, dayKey)) {
            return 0.0;
        }
        double nectar = HexNectarRules.nectar01(hive, day, hivesOnSameFlora, readyFlorasOnHex);
        if (nectar <= 1e-6) {
            return 0.0;
        }
        double tempM = TemperatureHoneyModifier.productionMultiplierForCelsius(tempC);
        double healthM = HealthHoneyModifier.productionMultiplierForHealth(
                hive != null ? hive.health : 80);
        double feedM = HiveFeedingBonuses.honeyMultiplierForDay(hive, dayKey);
        double sky = Math.max(0.0, skyMult);
        String hid = hive != null && hive.id != null ? hive.id : "_";
        double noise = GameBalanceConfig.nectarNoiseMin
                + HoneyDailyProduction.deterministicUniform01(hid + ":nectar", dayKey) * GameBalanceConfig.nectarNoiseSpan;
        double foragers = s.workersAdult * GameBalanceConfig.foragerFraction * tempM * sky;
        return foragers * GameBalanceConfig.kgPerForagerFullFlow * nectar * healthM * feedM * noise;
    }

    public static double consumptionKg(HivePopulationState s, HiveEntity hive, int eggsLaid, int dayKey) {
        if (s == null || s.workersAdult <= 0) {
            return 0.0;
        }
        int broodEq = Math.max(0, eggsLaid) * HivePopulationState.WORKER_BROOD_DAYS;
        double c = GameBalanceConfig.consumptionBaseKg
                + s.workersAdult * GameBalanceConfig.consumptionPerAdultKg
                + broodEq * GameBalanceConfig.consumptionPerBroodEqKg;
        c *= HiveFeedingBonuses.consumptionMultiplierForDay(hive, dayKey);
        return Math.max(0.0, c);
    }

    /**
     * kg netos del día (puede ser negativo: se resta del stock de la colmena).
     */
    public static double netHoneyKg(HivePopulationState s, HiveEntity hive, LocalDate day, int dayKey,
                                    Double tempC, double skyMult) {
        return netHoneyKg(s, hive, day, dayKey, tempC, skyMult, 1, null);
    }

    public static double netHoneyKg(HivePopulationState s, HiveEntity hive, LocalDate day, int dayKey,
                                    Double tempC, double skyMult, int hivesOnSameFlora,
                                    List<String> readyFlorasOnHex) {
        int eggs = eggsLaidToday(s, hive, day, dayKey, tempC);
        double gross = grossForageKg(s, hive, day, dayKey, tempC, skyMult, hivesOnSameFlora, readyFlorasOnHex);
        return gross - consumptionKg(s, hive, eggs, dayKey);
    }

    /**
     * Qué neto habría salido el mismo día si el pecoreo hubiera usado otro multiplicador de cielo.
     * El consumo no depende del tiempo.
     */
    public static double rescaleNetForSky(double netKg, double consumptionKg,
                                         double fromSkyMult, double toSkyMult) {
        double from = Math.max(0.0, fromSkyMult);
        double cons = Math.max(0.0, consumptionKg);
        double forage = Math.max(0.0, netKg + cons);
        if (from <= 1e-12) {
            return -cons;
        }
        double base = forage / from;
        return base * Math.max(0.0, toSkyMult) - cons;
    }

    /**
     * Tope de gráfico: mielada plena, sol (× pecoreo de sol), 22 °C, flora lavanda, sin consumo.
     */
    public static double maxChartDailyKgForAdults(int adultWorkers) {
        if (adultWorkers <= 0) {
            return 1.0;
        }
        return adultWorkers * GameBalanceConfig.foragerFraction * GameBalanceConfig.kgPerForagerFullFlow
                * GameBalanceConfig.chartFloraBoost * GameBalanceConfig.chartTempBoost
                * GameBalanceConfig.skyMultSun;
    }
}
