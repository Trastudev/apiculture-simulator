package com.apiculture.simulator.domain.game;

import com.apiculture.simulator.domain.parcel.HexParcel;

import org.junit.Assert;
import org.junit.Test;

import java.util.List;

public class ClimateUnlockTest {

    @Test
    public void iberiaUnlocksAtFiveFifteenTwentyFive() {
        Assert.assertTrue(ClimateUnlock.canBuy(IberianClimateZone.MEDITERRANEAN, 0));
        Assert.assertFalse(ClimateUnlock.canBuy(IberianClimateZone.CONTINENTAL, 4));
        Assert.assertTrue(ClimateUnlock.canBuy(IberianClimateZone.CONTINENTAL, 5));
        Assert.assertFalse(ClimateUnlock.canBuy(IberianClimateZone.ATLANTIC, 14));
        Assert.assertTrue(ClimateUnlock.canBuy(IberianClimateZone.ATLANTIC, 15));
        Assert.assertFalse(ClimateUnlock.canBuy(IberianClimateZone.SOUTH, 34));
        Assert.assertTrue(ClimateUnlock.canBuy(IberianClimateZone.SOUTH, 35));
        Assert.assertFalse(ClimateUnlock.canBuy(IberianClimateZone.MOUNTAIN, 24));
        Assert.assertTrue(ClimateUnlock.canBuy(IberianClimateZone.MOUNTAIN, 25));
    }

    @Test
    public void madagascarZonesUnlockAtZeroTenTwentyThirty() {
        Assert.assertTrue(ClimateUnlock.canBuy(MadagascarClimateZone.EQUATORIAL, 0));
        Assert.assertFalse(ClimateUnlock.canBuy(MadagascarClimateZone.HIGHLANDS, 9));
        Assert.assertTrue(ClimateUnlock.canBuy(MadagascarClimateZone.HIGHLANDS, 10));
        Assert.assertFalse(ClimateUnlock.canBuy(MadagascarClimateZone.TROPICAL, 19));
        Assert.assertTrue(ClimateUnlock.canBuy(MadagascarClimateZone.TROPICAL, 20));
        Assert.assertFalse(ClimateUnlock.canBuy(MadagascarClimateZone.DESERT, 29));
        Assert.assertTrue(ClimateUnlock.canBuy(MadagascarClimateZone.DESERT, 30));
    }

    @Test
    public void southAfricaOpensAt40ThenRestEveryFive() {
        Assert.assertFalse(ClimateUnlock.canAccessSouthAfrica(39));
        Assert.assertTrue(ClimateUnlock.canAccessSouthAfrica(40));
        Assert.assertTrue(ClimateUnlock.canBuy(SouthernAfricanClimateZone.HIGHVELD, 40));
        Assert.assertTrue(ClimateUnlock.canBuy(SouthernAfricanClimateZone.BUSHVELD, 40));
        Assert.assertFalse(ClimateUnlock.canBuy(SouthernAfricanClimateZone.KAROO, 44));
        Assert.assertTrue(ClimateUnlock.canBuy(SouthernAfricanClimateZone.KAROO, 45));
        Assert.assertTrue(ClimateUnlock.canBuy(SouthernAfricanClimateZone.SUBTROPICAL, 50));
        Assert.assertTrue(ClimateUnlock.canBuy(SouthernAfricanClimateZone.FYNBOS, 55));
    }

    @Test
    public void newlyUnlockedListsOnlyCrossedThresholds() {
        List<ClimateUnlock.Unlock> jump = ClimateUnlock.newlyUnlocked(4, 10);
        Assert.assertEquals(2, jump.size());
        Assert.assertEquals("Continental", jump.get(0).labelEs);
        Assert.assertEquals("Altiplano", jump.get(1).labelEs);
        Assert.assertTrue(jump.get(1).madagascar());
        Assert.assertTrue(ClimateUnlock.newlyUnlocked(25, 25).isEmpty());
        List<ClimateUnlock.Unlock> mountain = ClimateUnlock.newlyUnlocked(24, 25);
        Assert.assertEquals(1, mountain.size());
        Assert.assertEquals("Alta montaña", mountain.get(0).labelEs);
        List<ClimateUnlock.Unlock> south = ClimateUnlock.newlyUnlocked(34, 35);
        Assert.assertEquals(1, south.size());
        Assert.assertEquals("Sur", south.get(0).labelEs);
        List<ClimateUnlock.Unlock> za = ClimateUnlock.newlyUnlocked(39, 40);
        Assert.assertEquals(3, za.size());
        Assert.assertTrue(za.get(0).southAfricaRegion);
        Assert.assertEquals("Sudáfrica", za.get(0).labelEs);
        Assert.assertEquals("Highveld", za.get(1).labelEs);
        Assert.assertEquals("Bushveld", za.get(2).labelEs);
    }

    @Test
    public void mediterraneanParcelBuyableAtStart() {
        HexParcel med = new HexParcel(
                "hex_iberia_0_0",
                new double[][]{{0, 0}, {0, 1}, {1, 1}, {1, 0}, {0.5, -0.5}, {-0.5, 0.5}},
                39.5,
                -0.4,
                1.0,
                true);
        Assert.assertTrue(ClimateUnlock.canBuyParcel(med, 0));
        HexParcel mountain = new HexParcel(
                "hex_iberia_1_1",
                new double[][]{{0, 0}, {0, 1}, {1, 1}, {1, 0}, {0.5, -0.5}, {-0.5, 0.5}},
                42.7,
                -0.3,
                1.0,
                false,
                2200);
        Assert.assertFalse(ClimateUnlock.canBuyParcel(mountain, 20));
        Assert.assertTrue(ClimateUnlock.canBuyParcel(mountain, 25));
    }

    @Test
    public void madagascarEquatorialOpenFromStartOthersGated() {
        HexParcel east = new HexParcel(
                "hex_mdg_0_0",
                new double[][]{{0, 0}, {0, 1}, {1, 1}, {1, 0}, {0.5, -0.5}, {-0.5, 0.5}},
                -18.1,
                49.3,
                1.0,
                true,
                80);
        Assert.assertEquals(MadagascarClimateZone.EQUATORIAL, MadagascarClimateZone.forParcel(east));
        Assert.assertTrue(ClimateUnlock.canBuyParcel(east, 0));
        Assert.assertEquals(0, ClimateUnlock.minLevelForParcel(east));
        HexParcel highlands = new HexParcel(
                "hex_mdg_1_1",
                new double[][]{{0, 0}, {0, 1}, {1, 1}, {1, 0}, {0.5, -0.5}, {-0.5, 0.5}},
                -19.0,
                47.0,
                1.0,
                false,
                1200);
        Assert.assertEquals(MadagascarClimateZone.HIGHLANDS, MadagascarClimateZone.forParcel(highlands));
        Assert.assertFalse(ClimateUnlock.canBuyParcel(highlands, 9));
        Assert.assertTrue(ClimateUnlock.canBuyParcel(highlands, 10));
    }
}
