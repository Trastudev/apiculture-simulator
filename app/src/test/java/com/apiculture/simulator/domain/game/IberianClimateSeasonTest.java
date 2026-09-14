package com.apiculture.simulator.domain.game;

import com.apiculture.simulator.data.local.entity.HiveEntity;
import com.apiculture.simulator.domain.population.HivePopulationState;
import com.apiculture.simulator.domain.population.QueenMode;

import org.junit.Test;

import java.time.LocalDate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class IberianClimateSeasonTest {

    private static final int KEY = 20261115;

    @Test
    public void autumnLayingPulseExceedsLateJulyAtMildTemp() {
        HiveEntity hive = inlandHive();
        HivePopulationState s = strong();
        int july = HiveDailyBiology.eggsLaidToday(
                s, hive, LocalDate.of(2026, 7, 15), 20260715, 22.0);
        int nov = HiveDailyBiology.eggsLaidToday(
                s, hive, LocalDate.of(2026, 11, 15), KEY, 22.0);
        assertTrue("julio=" + july + " nov=" + nov, nov > july);
        assertTrue("pico de noviembre demasiado bajo: " + nov, nov > 900);
    }

    @Test
    public void midAugustPeakExceedsEarlySeptemberTrough() {
        HiveEntity hive = inlandHive();
        HivePopulationState s = strong();
        int aug = HiveDailyBiology.eggsLaidToday(
                s, hive, LocalDate.of(2026, 8, 15), 20260815, 22.0);
        int sep = HiveDailyBiology.eggsLaidToday(
                s, hive, LocalDate.of(2026, 9, 15), 20260915, 22.0);
        assertTrue("ago=" + aug + " sep=" + sep, aug > sep);
    }

    @Test
    public void heatCutsLayingLikeForage() {
        HiveEntity hive = inlandHive();
        HivePopulationState s = strong();
        LocalDate d = LocalDate.of(2026, 8, 15);
        int mild = HiveDailyBiology.eggsLaidToday(s, hive, d, 20260815, 22.0);
        int hot = HiveDailyBiology.eggsLaidToday(s, hive, d, 20260815, 36.0);
        assertTrue("22°C=" + mild + " 36°C=" + hot, hot < mild * 0.5);
    }

    @Test
    public void mountainFrostCutsLayingHarderThanCoastWinter() {
        HiveEntity hive = inlandHive();
        HivePopulationState s = strong();
        LocalDate d = LocalDate.of(2026, 1, 15);
        int coast = HiveDailyBiology.eggsLaidToday(s, hive, d, 20260115, 12.0);
        int mountain = HiveDailyBiology.eggsLaidToday(s, hive, d, 20260115, -2.0);
        assertTrue("costa 12°C=" + coast + " montaña -2°C=" + mountain, mountain < coast);
        assertTrue(TemperatureLayingModifier.layingMultiplierForCelsius(12.0)
                > TemperatureLayingModifier.layingMultiplierForCelsius(-2.0));
    }

    @Test
    public void milFloresHasAugustAndNovemberPeaks() {
        double spring = NectarFlow.intensity01("Mil flores", 112, 0, 1.0);
        double aug = NectarFlow.intensity01("Mil flores", 227, 0, 1.0);
        double trough = NectarFlow.intensity01("Mil flores", 270, 0, 1.0);
        double nov = NectarFlow.intensity01("Mil flores", 319, 0, 1.0);
        assertTrue("ago=" + aug + " valle sep=" + trough, aug > trough);
        assertTrue("nov=" + nov + " valle sep=" + trough, nov > trough);
        assertTrue("primavera sigue siendo mielada: " + spring, spring > 0.5);
    }

    @Test
    public void mountainSummerNectarBeatsLowlandHeatAndSouthIsDriest() {
        double mtn = IberianClimateZone.MOUNTAIN.nectarSeasonMultiplier(200);
        double atl = IberianClimateZone.ATLANTIC.nectarSeasonMultiplier(200);
        double med = IberianClimateZone.MEDITERRANEAN.nectarSeasonMultiplier(200);
        double cont = IberianClimateZone.CONTINENTAL.nectarSeasonMultiplier(200);
        double south = IberianClimateZone.SOUTH.nectarSeasonMultiplier(200);
        assertTrue("montaña verano > atlántico", mtn > atl);
        assertTrue("montaña verano > mediterráneo", mtn > med);
        assertTrue("montaña verano > continental", mtn > cont);
        assertTrue("sur es el verano más seco", south < atl && south < med && south < cont && south < mtn);
        double mountainWinter = IberianClimateZone.MOUNTAIN.nectarSeasonMultiplier(15);
        double medWinter = IberianClimateZone.MEDITERRANEAN.nectarSeasonMultiplier(15);
        assertTrue("invierno de costa más vivo que alta montaña", medWinter > mountainWinter);
    }

    @Test
    public void springNectarFavoursMediterraneanAndSouthOverMountain() {
        int springDoy = 100;
        double med = IberianClimateZone.MEDITERRANEAN.nectarSeasonMultiplier(springDoy);
        double south = IberianClimateZone.SOUTH.nectarSeasonMultiplier(springDoy);
        double atl = IberianClimateZone.ATLANTIC.nectarSeasonMultiplier(springDoy);
        double cont = IberianClimateZone.CONTINENTAL.nectarSeasonMultiplier(springDoy);
        assertTrue("mediterráneo y sur más primavera", med > atl && south > atl);
        assertTrue(med > cont && south > cont);
    }

    @Test
    public void summerLayingCutsSouthMostThenMedThenAtlanticAndContinentalMountainUncut() {
        HivePopulationState s = strong();
        LocalDate july = LocalDate.of(2026, 7, 15);
        int key = 20260715;
        double mild = 22.0;
        int south = HiveDailyBiology.eggsLaidToday(s, zoneHive(37.39, -5.99, 20), july, key, mild);
        int med = HiveDailyBiology.eggsLaidToday(s, zoneHive(39.47, -0.38, 15), july, key, mild);
        int atl = HiveDailyBiology.eggsLaidToday(s, zoneHive(43.37, -8.40, 40), july, key, mild);
        int cont = HiveDailyBiology.eggsLaidToday(s, zoneHive(40.42, -3.70, 650), july, key, mild);
        int mtn = HiveDailyBiology.eggsLaidToday(s, zoneHive(42.70, 0.10, 2100), july, key, mild);
        assertTrue("sur < mediterráneo: " + south + " vs " + med, south < med);
        assertTrue("mediterráneo < atlántico: " + med + " vs " + atl, med < atl);
        assertTrue("atlántico ≈ continental: " + atl + " vs " + cont, Math.abs(atl - cont) < 40);
        assertTrue("montaña no recorta: " + mtn + " vs atlántico " + atl, mtn > atl);
        assertTrue("montaña > mediterráneo", mtn > med);
        assertEquals(1.0, IberianClimateZone.MOUNTAIN.layingSeasonMultiplier(200), 1e-9);
        assertTrue(IberianClimateZone.SOUTH.layingSeasonMultiplier(200)
                < IberianClimateZone.MEDITERRANEAN.layingSeasonMultiplier(200));
        assertTrue(IberianClimateZone.MEDITERRANEAN.layingSeasonMultiplier(200)
                < IberianClimateZone.ATLANTIC.layingSeasonMultiplier(200));
        assertEquals(
                IberianClimateZone.ATLANTIC.layingSeasonMultiplier(200),
                IberianClimateZone.CONTINENTAL.layingSeasonMultiplier(200),
                1e-9);
        int mtnSpring = HiveDailyBiology.eggsLaidToday(
                s, zoneHive(42.70, 0.10, 2100), LocalDate.of(2026, 4, 15), 20260415, mild);
        int southSpring = HiveDailyBiology.eggsLaidToday(
                s, zoneHive(37.39, -5.99, 20), LocalDate.of(2026, 4, 15), 20260415, mild);
        assertTrue("primavera no recorta por clima: mtn=" + mtnSpring + " sur=" + southSpring,
                Math.abs(mtnSpring - southSpring) < 40);
    }

    private static HivePopulationState strong() {
        HivePopulationState s = new HivePopulationState();
        s.queenMode = QueenMode.LAYING;
        s.workersAdult = 40_000;
        HivePopulationState.partitionWorkerAdultsEvenly(s);
        return s;
    }

    private static HiveEntity zoneHive(double lat, double lon, int elevM) {
        HiveEntity h = inlandHive();
        h.lat = lat;
        h.lng = lon;
        h.elevationMeters = elevM;
        return h;
    }

    private static HiveEntity inlandHive() {
        HiveEntity h = new HiveEntity();
        h.id = "climate-test";
        h.health = 90;
        h.queenGeneticQuality = 82;
        h.honeyProduction = 8.0;
        h.varroaPct = 2.0;
        h.floraType = "Mil flores";
        h.hexId = "hex_test";
        h.lat = 40.42;
        h.lng = -3.70;
        h.elevationMeters = 650;
        return h;
    }
}
