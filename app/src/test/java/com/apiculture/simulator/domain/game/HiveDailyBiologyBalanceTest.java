package com.apiculture.simulator.domain.game;

import com.apiculture.simulator.data.local.entity.HiveEntity;
import com.apiculture.simulator.domain.population.HivePopulationSimulator;
import com.apiculture.simulator.domain.population.HivePopulationState;
import com.apiculture.simulator.domain.population.QueenMode;

import org.junit.Test;

import java.time.LocalDate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HiveDailyBiologyBalanceTest {

    private static final LocalDate MID_SEP = LocalDate.of(2026, 9, 14);
    private static final int MID_SEP_KEY = 20260914;

    @Test
    public void queenDailyDeathOddsArePerMilleNotHalfPercent() {
        assertEquals(0.0004, HiveCareRules.QUEEN_DAILY_DEATH_P, 1e-12);
        assertEquals(0.0004, GameBalanceConfig.queenDailyDeathP, 1e-12);
        assertTrue(HiveCareRules.QUEEN_DAILY_DEATH_P < 0.001);
    }

    @Test
    public void recordedQueenDeathRollsWouldNotTriggerWithRebalancedOdds() {
        double pirineu = HoneyDailyProduction.deterministicUniform01(
                "96c5e0ea-1253-45bc-8650-959d9c17548e:queenDeath", 20260911);
        double colmena2 = HoneyDailyProduction.deterministicUniform01(
                "fe0af607-15a2-468c-9898-ba037d685678:queenDeath", 20260913);
        assertTrue("Pirineu 1 era un 0,40 %; solo moría con p=0,5 %", pirineu > 0.0004);
        assertTrue("Colmena 2 era un 0,042 %; tampoco debe morir con p=0,04 %",
                colmena2 > HiveCareRules.QUEEN_DAILY_DEATH_P);
        assertTrue(pirineu < 0.005);
        assertTrue(colmena2 < 0.005);
    }

    @Test
    public void strongSeptemberHiveShrinksSlowlyNotAThousandADay() {
        HiveEntity hive = colmena1Like(8.0);
        HivePopulationState s = layingAdults(40_063);
        HivePopulationSimulator.applyDay(s, hive, MID_SEP, MID_SEP_KEY, 22.0);
        int delta = s.workersAdult - 40_063;
        assertTrue("aún puede bajar en otoño, delta=" + delta, delta < 0);
        assertTrue("no debe perder ~1000 obreras/día, delta=" + delta, delta > -450);
        assertEquals(QueenMode.LAYING, s.queenMode);
        assertTrue("septiembre aún pone, huevos=" + s.lastDayEggsLaid, s.lastDayEggsLaid > 250);
    }

    @Test
    public void smallerSeptemberHiveCanStillGrow() {
        HiveEntity hive = colmena1Like(8.0);
        hive.queenGeneticQuality = 86;
        HivePopulationState s = layingAdults(14_275);
        HivePopulationSimulator.applyDay(s, hive, MID_SEP, MID_SEP_KEY, 22.0);
        assertTrue(s.workersAdult > 14_275);
        assertEquals(QueenMode.LAYING, s.queenMode);
    }

    @Test
    public void lowHoneyDoesNotHalveEggLayingAboveHalfKg() {
        HiveEntity rich = colmena1Like(8.0);
        HiveEntity modest = colmena1Like(1.64);
        HivePopulationState s = layingAdults(40_063);
        int eggsRich = HiveDailyBiology.eggsLaidToday(s, rich, MID_SEP, MID_SEP_KEY);
        int eggsModest = HiveDailyBiology.eggsLaidToday(s, modest, MID_SEP, MID_SEP_KEY);
        assertTrue(eggsRich > 0);
        assertTrue(eggsModest > 0);
        assertEquals("por encima de 0,5 kg no debe penalizar la puesta", eggsRich, eggsModest);
    }

    @Test
    public void honeyBelowHalfKgCutsEggsAndAdultLifespan() {
        HiveEntity rich = colmena1Like(8.0);
        HiveEntity empty = colmena1Like(0.1);
        HivePopulationState s = layingAdults(40_063);
        int eggsRich = HiveDailyBiology.eggsLaidToday(s, rich, MID_SEP, MID_SEP_KEY);
        int eggsEmpty = HiveDailyBiology.eggsLaidToday(s, empty, MID_SEP, MID_SEP_KEY);
        assertTrue(eggsEmpty < eggsRich);
        int deathsRich = HiveDailyBiology.expectedNaturalDeaths(40_063, 257, 90, 22.0, 8.0);
        int deathsEmpty = HiveDailyBiology.expectedNaturalDeaths(40_063, 257, 90, 22.0, 0.1);
        assertTrue(deathsEmpty > deathsRich);
    }

    @Test
    public void midNovemberMilFloresIsNetPositiveOnMediterraneanCoast() {
        HiveEntity hive = colmena1Like(8.0);
        HivePopulationState s = layingAdults(40_063);
        LocalDate nov = LocalDate.of(2026, 11, 15);
        double kg = HiveDailyBiology.netHoneyKg(s, hive, nov, 20261115, 18.0, 1.0, 1, null);
        assertTrue("miel neta nov kg=" + kg, kg > 0.15);
    }

    private static HivePopulationState layingAdults(int adults) {
        HivePopulationState s = new HivePopulationState();
        s.queenMode = QueenMode.LAYING;
        s.workersAdult = adults;
        HivePopulationState.partitionWorkerAdultsEvenly(s);
        return s;
    }

    private static HiveEntity colmena1Like(double honeyKg) {
        HiveEntity h = new HiveEntity();
        h.id = "81cae1d5-31d2-43bc-80ad-b4e8c97bf524";
        h.health = 90;
        h.queenGeneticQuality = 78;
        h.honeyProduction = honeyKg;
        h.varroaPct = 2.37;
        h.floraType = "Mil flores";
        h.hexId = "hex_iberia_55_28";
        h.lat = 41.40021191998623;
        h.lng = 2.1496635763757332;
        h.elevationMeters = 81;
        h.superCount = 2;
        return h;
    }
}
