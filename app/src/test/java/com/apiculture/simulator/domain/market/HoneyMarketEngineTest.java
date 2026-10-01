package com.apiculture.simulator.domain.market;

import com.apiculture.simulator.domain.map.PlayableMapRegion;

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
        assertEquals(HoneyMarketEngine.typicalHivesPerPlayer(), one.activityUnits, 1e-9);
        assertEquals(HoneyMarketEngine.typicalHivesPerPlayer() * 10.0, ten.activityUnits, 1e-9);
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
    public void regionalCapacityIsFortyTimesEconomicTurnoverTarget() {
        HoneyMarketSnapshot s = HoneyMarketEngine.computeSnapshot(
                20260909, 180, PlayableMapRegion.IBERIA, 6.0, 1);
        for (String flora : s.demandKgByFlora.keySet()) {
            double capacity = s.demandKgByFlora.getOrDefault(flora, 0.0);
            double turnover = s.turnoverTargetForFloraOrDefault(flora, 0.0);
            assertTrue(flora + " capacity=" + capacity + " turnover=" + turnover,
                    capacity > turnover * 39.0);
        }
    }

    @Test
    public void sellingFiveKgDoesNotHitMinus25OnEmptyPopularMarket() {
        HoneyMarketSnapshot s = HoneyMarketEngine.computeSnapshot(20260909, 180, 1);
        double demand = s.demandKgByFlora.get("Lavanda");
        int adj = HoneyMarketEngine.supplyPriceAdjustmentPercent(demand, 5.0);
        assertTrue("adj=" + adj, adj > -25);
        assertTrue("adj=" + adj, adj < 25);
    }

    @Test
    public void postedDailyPriceUsesYesterdaySalesNotToday() {
        HoneyMarketSnapshot today = HoneyMarketEngine.computeSnapshot(20260917, 260, 1);
        HoneyMarketSnapshot yest = HoneyMarketEngine.computeSnapshot(20260916, 259, 1);
        double turnoverYest = yest.turnoverTargetForFloraOrDefault("Lavanda", 0.0);
        double base = today.priceForFloraOrDefault("Lavanda", 12.0);
        double high = HoneyMarketEngine.postedDailyPriceEurPerKg("Lavanda", today, yest, 0.0);
        double mid = HoneyMarketEngine.postedDailyPriceEurPerKg("Lavanda", today, yest, turnoverYest);
        double low = HoneyMarketEngine.postedDailyPriceEurPerKg("Lavanda", today, yest, turnoverYest * 2.0);
        assertEquals(HoneyMarketEngine.priceEurPerKgFromSupply("Lavanda", turnoverYest, 0.0, base), high, 1e-9);
        assertEquals(HoneyMarketEngine.priceEurPerKgFromSupply("Lavanda", turnoverYest, turnoverYest, base), mid, 1e-9);
        assertEquals(HoneyMarketEngine.priceEurPerKgFromSupply("Lavanda", turnoverYest, turnoverYest * 2.0, base), low, 1e-9);
        assertTrue(high > mid);
        assertTrue(mid > low);
    }

    @Test
    public void last7PostedPricesHasSevenPointsAndReactsToYesterday() {
        java.util.Map<Integer, Double> sold = new java.util.LinkedHashMap<>();
        double[] empty = HoneyMarketEngine.last7PostedPricesEurPerKg("Lavanda", 20260917, 1, sold);
        assertEquals(7, empty.length);
        HoneyMarketSnapshot yest = HoneyMarketEngine.computeSnapshot(20260916, 259, 1);
        sold.put(20260916, yest.demandKgByFlora.get("Lavanda") * 2.0);
        double[] heavy = HoneyMarketEngine.last7PostedPricesEurPerKg("Lavanda", 20260917, 1, sold);
        assertTrue(heavy[6] < empty[6]);
    }

    @Test
    public void accessLevelFollowsUnlockAndClimate() {
        assertEquals(0, HoneyMarketEngine.accessLevelForFlora("Mil flores"));
        assertEquals(2, HoneyMarketEngine.accessLevelForFlora("Campo de naranjos"));
        assertEquals(60, HoneyMarketEngine.accessLevelForFlora(com.apiculture.simulator.domain.parcel.HexFlora.AGUACATE));
        assertTrue(HoneyMarketEngine.accessLevelForFlora("Neret")
                > HoneyMarketEngine.accessLevelForFlora("Romero"));
    }

    @Test
    public void priceBaseScalesFrom12To18ByAccessLevel() {
        double cheap = HoneyMarketEngine.priceCeilingEurPerKgForFlora("Mil flores");
        double naranjos = HoneyMarketEngine.priceCeilingEurPerKgForFlora("Campo de naranjos");
        double aguacate = HoneyMarketEngine.priceCeilingEurPerKgForFlora(
                com.apiculture.simulator.domain.parcel.HexFlora.AGUACATE);
        assertEquals(HoneyMarketEngine.MIN_PRICE_EUR_PER_KG, cheap, 0.02);
        assertEquals(HoneyMarketEngine.MAX_PRICE_EUR_PER_KG, aguacate, 0.02);
        assertTrue(naranjos > cheap);
        assertTrue(aguacate > naranjos);
    }

    @Test
    public void snapshotBasePriceDoesNotChangeWithSeason() {
        HoneyMarketSnapshot summer = HoneyMarketEngine.computeSnapshot(20260701, 182, 1);
        HoneyMarketSnapshot winter = HoneyMarketEngine.computeSnapshot(20260115, 15, 1);
        assertEquals(
                summer.priceForFloraOrDefault("Lavanda", 0),
                winter.priceForFloraOrDefault("Lavanda", 0),
                1e-9);
        assertEquals(
                HoneyMarketEngine.priceCeilingEurPerKgForFlora("Lavanda"),
                summer.priceForFloraOrDefault("Lavanda", 0),
                1e-9);
    }
}
