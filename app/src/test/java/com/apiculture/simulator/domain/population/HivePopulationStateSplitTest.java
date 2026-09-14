package com.apiculture.simulator.domain.population;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class HivePopulationStateSplitTest {

    @Test
    public void splitOffFractionKeepsComplementOnOriginal() {
        HivePopulationState original = HivePopulationState.fromLegacyBeeCount(80_000);
        int adults = original.workersAdult;
        int brood = original.totalBees() - adults;
        HivePopulationState spawn = original.splitOffFraction(0.40);
        assertEquals(Math.round(adults * 0.40), spawn.workersAdult);
        assertEquals(adults - spawn.workersAdult, original.workersAdult);
        int spawnBrood = spawn.totalBees() - spawn.workersAdult;
        int origBrood = original.totalBees() - original.workersAdult;
        assertEquals(brood, spawnBrood + origBrood);
        assertTrue(spawnBrood >= 0);
        assertTrue(origBrood >= 0);
    }
}
