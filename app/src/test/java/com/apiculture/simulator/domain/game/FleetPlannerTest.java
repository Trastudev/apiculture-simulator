package com.apiculture.simulator.domain.game;

import com.apiculture.simulator.domain.map.PlayableMapRegion;
import com.apiculture.simulator.domain.map.SeaRoute;
import com.apiculture.simulator.domain.map.Seaport;
import com.apiculture.simulator.domain.map.SeaportCatalog;

import org.junit.Assert;
import org.junit.Test;

import java.util.List;

public class FleetPlannerTest {

    @Test
    public void catalogHasThePlannedPorts() {
        Assert.assertEquals(9, SeaportCatalog.in(PlayableMapRegion.IBERIA).size());
        Assert.assertEquals(5, SeaportCatalog.in(PlayableMapRegion.MADAGASCAR).size());
        Assert.assertEquals(5, SeaportCatalog.in(PlayableMapRegion.SOUTH_AFRICA).size());
    }

    @Test
    public void seaRoutesStayOffLand() {
        List<Seaport> ports = SeaportCatalog.all();
        for (Seaport from : ports) {
            for (Seaport to : ports) {
                if (from.id.equals(to.id)) {
                    continue;
                }
                List<double[]> path = SeaRoute.trace(from, to);
                Assert.assertTrue(from.name + " → " + to.name, path.size() >= 2);
                Assert.assertFalse(from.name + " → " + to.name, SeaRoute.crossesLand(path));
            }
        }
    }

    @Test
    public void mozambiqueIsShorterThanTheCapeRoute() {
        double channel = SeaRoute.km(SeaportCatalog.byId("durban"), SeaportCatalog.byId("toamasina"));
        double cape = SeaRoute.km(SeaportCatalog.byId("lisboa"), SeaportCatalog.byId("capetown"));
        Assert.assertTrue(channel > 500);
        Assert.assertTrue(cape > channel * 2);
    }

    @Test
    public void receivingTruckMeetsTheShip() {
        FleetPlanner.Plan plan = FleetPlanner.overseas(
                "Toamasina", "Toamasina", "Barcelona", "Barcelona",
                100, 120, 120, 50, 200,
                400,
                100, 120, 100,
                400, false,
                true, true, true, true, true, true);
        Assert.assertTrue(plan.ok);
        long shipArrive = plan.shipDepartMs + plan.seaMs;
        long truckArrive = plan.destDepartMs + plan.emptyToPortMs;
        Assert.assertEquals(shipArrive, truckArrive);
        Assert.assertEquals(0L, Math.min(plan.originDepartMs, plan.destDepartMs));
    }

    @Test
    public void slowReceivingTruckLeavesBeforeTheShip() {
        FleetPlanner.Plan plan = FleetPlanner.overseas(
                "A", "P1", "P2", "Mercado",
                100, 120, 120, 30, 500,
                600,
                100, 120, 100,
                80, true,
                true, true, true, true, true, true);
        Assert.assertTrue(plan.destDepartMs < plan.shipDepartMs);
    }

    @Test
    public void exoticBlendDropsAfterTheQuota() {
        double base = 10;
        Assert.assertEquals(40, ExoticHoneyRules.blendedPerKg(base, 0, 10), 1e-6);
        Assert.assertEquals(350, ExoticHoneyRules.remainingKg(50), 1e-6);
        double mixed = ExoticHoneyRules.blendedPerKg(base, 390, 20);
        Assert.assertEquals((10 * 40 + 10 * 15) / 20.0, mixed, 1e-6);
        Assert.assertFalse(ExoticHoneyRules.isExotic(PlayableMapRegion.IBERIA, "Romero"));
        Assert.assertTrue(ExoticHoneyRules.isExotic(PlayableMapRegion.IBERIA, "Litchi"));
    }

    @Test
    public void detourLimitIsTwentyFivePercent() {
        Assert.assertTrue(FleetRules.detourWithinLimit(100, 125));
        Assert.assertFalse(FleetRules.detourWithinLimit(100, 126));
        Assert.assertEquals(2, FleetRules.truckSlots(1));
        Assert.assertEquals(4, FleetRules.truckSlots(2));
        Assert.assertEquals(10, FleetRules.truckSlots(5));
        Assert.assertEquals(120.0, FleetRules.speedKmh(FleetRules.Kind.SHIP, 1), 1e-9);
        Assert.assertEquals(2000.0, FleetRules.honeyKg(FleetRules.Kind.SHIP, 1), 1e-9);
        Assert.assertEquals(3600.0, FleetRules.honeyKg(FleetRules.Kind.TRUCK, 10), 1e-9);
        Assert.assertEquals(10, FleetRules.hiveSlots(FleetRules.Kind.TRUCK, 10));
        Assert.assertEquals(0, FleetRules.upgradeCostB(FleetRules.Kind.TRUCK, 10));
        Assert.assertEquals(0, FleetRules.upgradeCostB(FleetRules.Kind.SHIP, 10));
        Assert.assertEquals(18000, FleetRules.upgradeCostB(FleetRules.Kind.SHIP, 5));
        Assert.assertEquals(54000.0, FleetRules.honeyKg(FleetRules.Kind.SHIP, 10), 1e-9);
        Assert.assertEquals(19000, FleetRules.upgradeCostB(FleetRules.Kind.TRUCK, 9));
    }
}
