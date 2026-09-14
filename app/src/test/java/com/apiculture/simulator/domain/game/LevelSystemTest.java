package com.apiculture.simulator.domain.game;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class LevelSystemTest {

    @Test
    public void firstLevelsFollowOnePointFive() {
        assertEquals(100, LevelSystem.xpForLevel(0));
        assertEquals(150, LevelSystem.xpForLevel(1));
        assertEquals(225, LevelSystem.xpForLevel(2));
        assertEquals(338, LevelSystem.xpForLevel(3));
    }

    @Test
    public void addXpCanLevelUp() {
        LevelSystem.Result r = LevelSystem.addXp(0, 90, 20);
        assertEquals(1, r.level);
        assertEquals(10, r.xp);
        assertEquals(150, r.maxXp);
    }
}
