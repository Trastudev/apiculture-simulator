package com.apiculture.simulator.domain.game;

import com.apiculture.simulator.domain.parcel.HexParcel;

import org.junit.Assert;
import org.junit.Test;

public class PollinationContractRulesTest {

    @Test
    public void travelCost_scalesWithKmAndEachHive() {
        Assert.assertEquals(0, PollinationContractRules.travelCostPerHiveKm(0));
        Assert.assertEquals(4, PollinationContractRules.travelCostPerHiveKm(10));
        Assert.assertEquals(40, PollinationContractRules.travelCostPerHiveKm(100));
        Assert.assertEquals(99999, PollinationContractRules.travelCostPerHiveKm(Double.NaN));
        com.apiculture.simulator.data.local.entity.HiveEntity a =
                new com.apiculture.simulator.data.local.entity.HiveEntity();
        a.lat = 40.0;
        a.lng = 0.0;
        com.apiculture.simulator.data.local.entity.HiveEntity b =
                new com.apiculture.simulator.data.local.entity.HiveEntity();
        b.lat = 40.0;
        b.lng = 0.0;
        com.apiculture.simulator.data.local.entity.HiveEntity c =
                new com.apiculture.simulator.data.local.entity.HiveEntity();
        c.lat = 42.0;
        c.lng = 0.0;
        int one = PollinationContractRules.travelCostOne(a, 40.5, 0.0);
        int samePlace = PollinationContractRules.travelCostForHives(java.util.Arrays.asList(a, b), 40.5, 0.0);
        int twoPlaces = PollinationContractRules.travelCostForHives(java.util.Arrays.asList(a, c), 40.5, 0.0);
        Assert.assertEquals(one * 2, samePlace);
        Assert.assertTrue(twoPlaces != samePlace);
        int near = PollinationContractRules.travelCostPerHiveKm(20);
        int far = PollinationContractRules.travelCostPerHiveKm(400);
        Assert.assertTrue(far > near);
    }

    @Test
    public void payout_belowMinIsZero_aboveAddsExtra() {
        PollinationPayTerms naranjos = new PollinationPayTerms(0.55, 680, 8);
        Assert.assertEquals(0.40, PollinationContractRules.minPctForFlora("Campo de naranjos"), 1e-9);
        Assert.assertEquals(0, PollinationContractRules.payoutB(10, 20, naranjos));
        Assert.assertEquals(680, PollinationContractRules.payoutB(11, 20, naranjos));
        Assert.assertEquals(1040, PollinationContractRules.payoutB(20, 20, naranjos));
        PollinationPayTerms almendros = new PollinationPayTerms(0.45, 420, 6);
        Assert.assertEquals(570, PollinationContractRules.payoutB(14, 20, almendros));
    }

    @Test
    public void payScalesWithDaysAndFieldPrice() {
        PollinationPayTerms shortCheap = PollinationContractRules.termsForFlora("Campo de naranjos", 10);
        PollinationPayTerms longCheap = PollinationContractRules.termsForFlora("Campo de naranjos", 20);
        PollinationPayTerms shortRich = PollinationContractRules.termsForFlora(
                com.apiculture.simulator.domain.parcel.HexFlora.AGUACATE, 10);
        Assert.assertEquals((int) Math.round(shortCheap.payPerDayB * 20)
                + GameBalanceConfig.pollinationCalloutB, longCheap.payB);
        Assert.assertTrue(shortRich.payPerDayB > shortCheap.payPerDayB);
        Assert.assertTrue(shortRich.payB > shortCheap.payB);
        Assert.assertEquals(GameBalanceConfig.pollinationPayPerDayMax, PollinationContractRules.payPerDayB(
                com.apiculture.simulator.domain.parcel.HexFlora.AGUACATE), 0.02);
        Assert.assertTrue(PollinationContractRules.payPerDayB("Campo de naranjos")
                >= GameBalanceConfig.pollinationPayPerDayMin);
        Assert.assertTrue(PollinationContractRules.payPerDayB("Campo de naranjos")
                < GameBalanceConfig.pollinationPayPerDayMax);
        Assert.assertEquals((int) Math.round(shortCheap.payPerDayB * 10)
                + GameBalanceConfig.pollinationCalloutB, shortCheap.payB);
        Assert.assertEquals(1.0, PollinationContractRules.payMultiplierForMinPoints(40), 1e-9);
        Assert.assertEquals(1.15, PollinationContractRules.payMultiplierForMinPoints(50), 1e-9);
        Assert.assertEquals(1.30, PollinationContractRules.payMultiplierForMinPoints(60), 1e-9);
        int a = PollinationContractRules.minPollinationPoints("hex_a", "Campo de girasoles", 180);
        int b = PollinationContractRules.minPollinationPoints("hex_b", "Campo de girasoles", 180);
        Assert.assertTrue(a >= 40 && a <= 60);
        Assert.assertTrue(b >= 40 && b <= 60);
        Assert.assertEquals(a, PollinationContractRules.minPollinationPoints("hex_a", "Campo de girasoles", 180));
        java.util.Set<Integer> pts = new java.util.HashSet<>();
        for (int i = 0; i < 80; i++) {
            pts.add(PollinationContractRules.minPollinationPoints(
                    "hex_iberia_" + i + "_4", "Campo de girasoles", 180));
        }
        Assert.assertTrue(pts.size() >= 5);
        Assert.assertFalse(pts.size() == 1 && pts.contains(50));
        PollinationPayTerms base = PollinationContractRules.termsForFlora("Campo de naranjos", 10, 180, 190, "hex_x");
        int points = PollinationContractRules.minPollinationPoints("hex_x", "Campo de naranjos", 180);
        PollinationPayTerms floor = PollinationContractRules.termsForFlora("Campo de naranjos", 10);
        Assert.assertEquals((int) Math.round(floor.payB * PollinationContractRules.payMultiplierForMinPoints(points)),
                base.payB);
    }

    @Test
    public void targetPool_isDailyFlowerHoneyTimesDays() {
        double daily = PollinationContractRules.dailyFlowerHoneyKg("Campo de girasoles");
        Assert.assertEquals(GameBalanceConfig.baseDailyNectarKgPlanted, daily, 1e-9);
        Assert.assertEquals(daily * 12.0, PollinationContractRules.targetPoolKg("Campo de girasoles", 12), 1e-9);
        Assert.assertEquals(daily * 6.0, PollinationContractRules.targetPoolKg("Campo de girasoles", 12) * 0.50, 1e-9);
    }

    @Test
    public void occupancy_opensReservesThenSilentLayer() {
        Assert.assertTrue(PollinationContractRules.reservesOpen(0.0));
        Assert.assertTrue(PollinationContractRules.reservesOpen(0.70));
        Assert.assertEquals(1, PollinationContractRules.maxLayers(0.80, true));
        Assert.assertEquals(2, PollinationContractRules.maxLayers(0.90, true));
        Assert.assertEquals(1, PollinationContractRules.maxLayers(0.95, false));
        Assert.assertEquals(0.5, PollinationContractRules.occupancy(4, 8), 1e-9);
    }

    @Test
    public void shouldClose_onlyAfterPeakDrops() {
        Assert.assertFalse(PollinationContractRules.shouldClose(false, 0.2));
        Assert.assertFalse(PollinationContractRules.shouldClose(true, 0.55));
        Assert.assertTrue(PollinationContractRules.shouldClose(true, 0.39));
        Assert.assertTrue(PollinationContractRules.shouldClose(false, 0.90, 100, 100));
        Assert.assertFalse(PollinationContractRules.shouldClose(false, 0.90, 99, 100));
    }

    @Test
    public void bloomWindow_from40PercentCoversPastPeak() {
        java.util.List<FloraBloomWindow.Span> spans = FloraBloomWindow.spansAtLeast(
                "Campo de girasoles", 0, PollinationContractRules.MIN_BLOOM01);
        Assert.assertFalse(spans.isEmpty());
        FloraBloomWindow.Span s = spans.get(0);
        Assert.assertTrue(s.startDoy < 200);
        Assert.assertTrue(s.endDoy > 200);
    }

    @Test
    public void bloom01_plantationHasSpringWindow() {
        HexParcel parcel = new HexParcel(
                "hex_iberia_10_4",
                new double[][]{{0, 0}, {0, 1}, {1, 1}, {1, 0}, {0.5, -0.5}, {-0.5, 0.5}},
                39.47,
                -0.38,
                70.0,
                false,
                15);
        double april = PollinationContractRules.bloom01(parcel, "Campo de naranjos",
                java.time.LocalDate.of(2026, 4, 28));
        double january = PollinationContractRules.bloom01(parcel, "Campo de naranjos",
                java.time.LocalDate.of(2026, 1, 10));
        Assert.assertTrue(april > january);
        Assert.assertTrue(april >= 0.0);
    }

    @Test
    public void canAddHives_fromAcceptedDay() {
        Assert.assertTrue(PollinationContractRules.canAddHives(10, 10, PollinationContractRules.STATUS_ACTIVE));
        Assert.assertTrue(PollinationContractRules.canAddHives(10, 11, PollinationContractRules.STATUS_ACTIVE));
        Assert.assertFalse(PollinationContractRules.canAddHives(10, 9, PollinationContractRules.STATUS_ACTIVE));
        Assert.assertFalse(PollinationContractRules.canAddHives(10, 11, PollinationContractRules.STATUS_RETURNING));
    }
}
