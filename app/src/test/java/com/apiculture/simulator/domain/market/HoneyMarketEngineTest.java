package com.apiculture.simulator.domain.market;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class HoneyMarketEngineTest {

    @Test
    public void supplyMultiplierIsPlus25WhenNothingSold() {
        assertEquals(1.25, HoneyMarketEngine.supplyPriceMultiplier(10.0, 0.0), 1e-9);
        assertEquals(25, HoneyMarketEngine.supplyPriceAdjustmentPercent(10.0, 0.0));
    }

    @Test
    public void supplyMultiplierIsNeutralWhenSoldEqualsDemand() {
        assertEquals(1.0, HoneyMarketEngine.supplyPriceMultiplier(10.0, 10.0), 1e-9);
        assertEquals(0, HoneyMarketEngine.supplyPriceAdjustmentPercent(10.0, 10.0));
    }

    @Test
    public void supplyMultiplierIsMinus25WhenSoldIsTwiceDemand() {
        assertEquals(0.75, HoneyMarketEngine.supplyPriceMultiplier(10.0, 20.0), 1e-9);
        assertEquals(-25, HoneyMarketEngine.supplyPriceAdjustmentPercent(10.0, 20.0));
        assertEquals(0.75, HoneyMarketEngine.supplyPriceMultiplier(10.0, 50.0), 1e-9);
    }

    @Test
    public void demandScalesLinearlyWithPlayerCount() {
        HoneyMarketSnapshot one = HoneyMarketEngine.computeSnapshot(20260909, 180, 1);
        HoneyMarketSnapshot ten = HoneyMarketEngine.computeSnapshot(20260909, 180, 10);
        assertEquals(1, one.playerCount);
        assertEquals(10, ten.playerCount);
        assertTrue(one.totalDemandKg > 0.0);
        assertEquals(10.0, ten.totalDemandKg / one.totalDemandKg, 0.02);
    }

    @Test
    public void eachFloraHasVisibleDemandForOnePlayer() {
        HoneyMarketSnapshot s = HoneyMarketEngine.computeSnapshot(20260909, 180, 1);
        for (Double kg : s.demandKgByFlora.values()) {
            assertTrue(kg >= 1.0);
        }
        assertTrue(s.demandKgByFlora.get("Mil flores") > s.demandKgByFlora.get("Campo de perales"));
    }

    @Test
    public void sellingFiveKgDoesNotHitMinus25OnEmptyPopularMarket() {
        HoneyMarketSnapshot s = HoneyMarketEngine.computeSnapshot(20260909, 180, 1);
        double demand = s.demandKgByFlora.get("Lavanda");
        int adj = HoneyMarketEngine.supplyPriceAdjustmentPercent(demand, 5.0);
        assertTrue("adj=" + adj, adj > -25);
        assertTrue("adj=" + adj, adj < 25);
    }
}
