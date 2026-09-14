package com.apiculture.simulator.domain.health;

import com.apiculture.simulator.data.local.entity.HiveEntity;
import com.apiculture.simulator.domain.game.GameBalanceConfig;
import com.apiculture.simulator.domain.game.HiveCareRules;
import com.apiculture.simulator.domain.game.Season;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class HiveDailyHealthSimulatorTest {

    @Test
    public void treatmentReducesVarroaWithoutGrowth() {
        HiveEntity h = hive(10.0, 7, 200, 40.4, -3.7);
        HiveDailyHealthSimulator.applyDay(h, 1, Season.SPRING, 20.0, 1600);
        assertEquals(6, h.varroaTreatmentDaysRemaining);
        assertEquals(10.0 * HiveCareRules.TREAT_VARROA_KEEP_FACTOR, h.varroaPct, 1e-9);
    }

    @Test
    public void untreatedVarroaGrowsWhenThereIsBrood() {
        HiveEntity h = hive(10.0, 0, 200, 40.4, -3.7);
        HiveDailyHealthSimulator.applyDay(h, 1, Season.SPRING, 20.0, 1600);
        assertTrue(h.varroaPct > 10.0);
    }

    @Test
    public void winterBroodGrowsMuchLessThanSpringBrood() {
        HiveEntity spring = hive(10.0, 0, 200, 40.4, -3.7);
        HiveEntity winter = hive(10.0, 0, 200, 40.4, -3.7);
        HiveDailyHealthSimulator.applyDay(spring, 1, Season.SPRING, 18.0, 1600);
        HiveDailyHealthSimulator.applyDay(winter, 1, Season.WINTER, 18.0, 20);
        assertTrue(winter.varroaPct < spring.varroaPct);
        assertTrue(winter.varroaPct - 10.0 < 0.15);
    }

    @Test
    public void noEggsBarelyGrowsVarroa() {
        HiveEntity h = hive(5.0, 0, 200, 40.4, -3.7);
        HiveDailyHealthSimulator.applyDay(h, 1, Season.SPRING, 20.0, 0);
        assertTrue(h.varroaPct < 5.1);
    }

    @Test
    public void highMountainGrowsSlowerThanLowland() {
        HiveEntity low = hive(10.0, 0, 200, 40.4, -3.7);
        HiveEntity high = hive(10.0, 0, 1800, 40.4, -3.7);
        HiveDailyHealthSimulator.applyDay(low, 1, Season.SPRING, 18.0, 1600);
        HiveDailyHealthSimulator.applyDay(high, 1, Season.SPRING, 18.0, 1600);
        assertTrue(high.varroaPct < low.varroaPct);
    }

    @Test
    public void coldDayGrowsSlowerThanWarmDay() {
        HiveEntity warm = hive(10.0, 0, 200, 40.4, -3.7);
        HiveEntity cold = hive(10.0, 0, 200, 40.4, -3.7);
        HiveDailyHealthSimulator.applyDay(warm, 1, Season.SPRING, 22.0, 1600);
        HiveDailyHealthSimulator.applyDay(cold, 1, Season.SPRING, 3.0, 1600);
        assertTrue(cold.varroaPct < warm.varroaPct);
    }

    @Test
    public void freezeMultiplierIsLowest() {
        assertTrue(VarroaGrowth.temperatureMultiplier(-2.0)
                < VarroaGrowth.temperatureMultiplier(5.0));
        assertTrue(VarroaGrowth.temperatureMultiplier(5.0)
                < VarroaGrowth.temperatureMultiplier(10.0));
        assertEquals(1.0, VarroaGrowth.temperatureMultiplier(20.0), 1e-9);
        assertTrue(VarroaGrowth.broodMultiplier(20) < VarroaGrowth.broodMultiplier(1600));
        assertEquals(GameBalanceConfig.varroaBroodMultFloor, VarroaGrowth.broodMultiplier(0), 1e-9);
    }

    private static HiveEntity hive(double varroa, int treatmentDays, int elevM, double lat, double lng) {
        HiveEntity h = new HiveEntity();
        h.id = "v";
        h.health = 80;
        h.honeyProduction = 20.0;
        h.reserves = 50;
        h.varroaPct = varroa;
        h.varroaTreatmentDaysRemaining = treatmentDays;
        h.varroaReboundDaysRemaining = 0;
        h.elevationMeters = elevM;
        h.lat = lat;
        h.lng = lng;
        return h;
    }
}
