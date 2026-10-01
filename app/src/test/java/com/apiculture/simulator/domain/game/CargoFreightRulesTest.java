package com.apiculture.simulator.domain.game;

import org.junit.Assert;
import org.junit.Test;

public class CargoFreightRulesTest {

    @Test
    public void emptyLoadOrZeroKmIsFree() {
        Assert.assertEquals(0.0, CargoFreightRules.costB(0, 40), 1e-9);
        Assert.assertEquals(0.0, CargoFreightRules.costB(10, 0), 1e-9);
    }

    @Test
    public void smallLoadKeepsBaseRateAndBillsEmptyReturn() {
        double oneKm = CargoFreightRules.costB(1, 1);
        Assert.assertEquals(0.02, oneKm, 1e-9);
        Assert.assertEquals(1.02, CargoFreightRules.costB(1, 50), 1e-9);
        Assert.assertTrue(CargoFreightRules.roadDistanceFactor(800)
                < CargoFreightRules.roadDistanceFactor(50));
        Assert.assertTrue(CargoFreightRules.costB(1, 40) > CargoFreightRules.costB(1, 20));
    }

    @Test
    public void heavierLoadsPayLessPerKg() {
        double light = CargoFreightRules.ratePerKgKm(80, false);
        double heavy = CargoFreightRules.ratePerKgKm(750, false);
        double ship = CargoFreightRules.ratePerKgKm(2000, false);
        Assert.assertEquals(0.016, light, 1e-9);
        Assert.assertTrue(heavy < light);
        Assert.assertTrue(ship <= heavy);
        Assert.assertTrue(ship + 1e-9 >= 0.016 * CargoFreightRules.FLOOR_RATIO);
        double transfer = CargoFreightRules.ratePerKgKm(80, true);
        Assert.assertEquals(CargoFreightRules.TRANSFER_BASE, transfer, 1e-9);
        Assert.assertTrue(CargoFreightRules.costB(100, 40, true, 40)
                < CargoFreightRules.costB(100, 40, false, 40));
    }

    @Test
    public void capeCrossingOfFiftyKgCostsLessThanTheExoticPayout() {
        double sea = CargoFreightRules.seaCostB(50, 13712, false, 13712);
        double asTruck = CargoFreightRules.costB(50, 13712, false, 13712);
        Assert.assertEquals(1.0, CargoFreightRules.seaDistanceFactor(1000), 1e-9);
        Assert.assertTrue(CargoFreightRules.seaDistanceFactor(2000)
                < CargoFreightRules.seaDistanceFactor(1500));
        Assert.assertTrue(CargoFreightRules.seaDistanceFactor(20000) >= 0.70);
        Assert.assertEquals(315.20, sea, 0.05);
        Assert.assertTrue(asTruck > sea);
        Assert.assertTrue(sea < 1830);
    }
}
