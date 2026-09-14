package com.apiculture.simulator.domain.game;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TemperatureStressTest {

    @Test
    public void comfortTempsRecoverHealth() {
        assertTrue(TemperatureStress.dailyHealthDelta(22.0) > 0.0);
        assertEquals(1.0, TemperatureStress.carryingCapacityMultiplier(22.0), 1e-9);
    }

    @Test
    public void mountainFrostHurtsHealthAndCapacity() {
        assertTrue(TemperatureStress.dailyHealthDelta(-2.0) <= -3.0);
        assertTrue(TemperatureStress.carryingCapacityMultiplier(-2.0) < 0.3);
        assertTrue(TemperatureStress.mortalityMultiplier(80, -2.0)
                > TemperatureStress.mortalityMultiplier(80, 20.0));
    }

    @Test
    public void southernHeatHurtsHealthAndCapacity() {
        assertTrue(TemperatureStress.dailyHealthDelta(39.0) <= -2.0);
        assertTrue(TemperatureStress.carryingCapacityMultiplier(39.0) < 0.5);
        assertTrue(TemperatureStress.mortalityMultiplier(70, 39.0)
                > TemperatureStress.mortalityMultiplier(70, 24.0));
    }

    @Test
    public void lowHealthRaisesMortality() {
        assertTrue(TemperatureStress.mortalityMultiplier(30, 22.0)
                > TemperatureStress.mortalityMultiplier(90, 22.0));
    }
}
