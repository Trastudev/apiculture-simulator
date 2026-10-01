package com.apiculture.simulator.domain.market;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class HoneyMarketDemandRulesTest {

    @Test
    public void levelMultiplierIsLinearAndCapped() {
        assertEquals(1.0, HoneyMarketDemandRules.levelMultiplier(0), 1e-9);
        assertEquals(1.6, HoneyMarketDemandRules.levelMultiplier(20), 1e-9);
        assertEquals(2.8, HoneyMarketDemandRules.levelMultiplier(60), 1e-9);
        assertEquals(4.0, HoneyMarketDemandRules.levelMultiplier(100), 1e-9);
        assertEquals(4.0, HoneyMarketDemandRules.levelMultiplier(500), 1e-9);
    }

    @Test
    public void playerRegionActivityUsesRealHivesWithOneUnitFloor() {
        assertEquals(1.0, HoneyMarketDemandRules.playerRegionActivity(0, 0), 1e-9);
        assertEquals(1.6, HoneyMarketDemandRules.playerRegionActivity(0, 20), 1e-9);
        assertEquals(14.0, HoneyMarketDemandRules.playerRegionActivity(5, 60), 1e-9);
    }

    @Test
    public void capacityIsFortyTimesTurnoverPerActivityUnit() {
        double capacity = HoneyMarketDemandRules.capacityKgPerActivityUnit(1.0);
        double turnover = HoneyMarketDemandRules.turnoverKgPerActivityUnit(1.0);
        assertEquals(40.0, capacity / turnover, 1e-9);
    }
}
