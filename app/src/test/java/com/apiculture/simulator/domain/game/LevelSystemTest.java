package com.apiculture.simulator.domain.game;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class LevelSystemTest {

    @Test
    public void firstLevelsUseTheReducedCurve() {
        assertEquals(76, LevelSystem.xpForLevel(0));
        assertEquals(83, LevelSystem.xpForLevel(1));
        assertEquals(91, LevelSystem.xpForLevel(2));
        assertEquals(101, LevelSystem.xpForLevel(3));
    }

    @Test
    public void addXpCanLevelUp() {
        LevelSystem.Result r = LevelSystem.addXp(0, 60, 20);
        assertEquals(1, r.level);
        assertEquals(4.0, r.xp, 1e-9);
        assertEquals(83, r.maxXp);
    }

    @Test
    public void highLevelCostIsAlsoReduced() {
        assertEquals(1_041_814, LevelSystem.xpForLevel(100));
    }
}
