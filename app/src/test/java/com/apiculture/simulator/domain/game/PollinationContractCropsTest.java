package com.apiculture.simulator.domain.game;

import com.apiculture.simulator.domain.parcel.HexFlora;
import com.apiculture.simulator.domain.parcel.HexParcel;

import org.junit.Assert;
import org.junit.Test;

import java.util.List;

public class PollinationContractCropsTest {

    private static final double[][] RING = {{0, 0}, {0, 1}, {1, 1}, {1, 0}, {0.5, -0.5}, {-0.5, 0.5}};

    private static HexParcel parcel(String id, double lat, double lon, int elev) {
        return new HexParcel(id, RING, lat, lon, 70.0, false, elev);
    }

    @Test
    public void mediterraneanOmitsWinterRabanizaAndKeepsSpringSummer() {
        HexParcel levante = parcel("hex_iberia_1_1", 39.47, -0.38, 15);
        List<String> crops = PollinationContractCrops.forParcel(levante);
        Assert.assertTrue(crops.contains("Campo de naranjos"));
        Assert.assertTrue(crops.contains(HexFlora.FACELIA));
        Assert.assertTrue(crops.contains("Campo de girasoles"));
        Assert.assertFalse(crops.contains(HexFlora.RABANIZA));
        Assert.assertEquals(IberianClimateZone.MEDITERRANEAN, IberianClimateZone.forParcel(levante));
    }

    @Test
    public void continentalHasNoCitrus() {
        HexParcel meseta = parcel("hex_iberia_2_2", 41.0, -3.5, 700);
        Assert.assertEquals(IberianClimateZone.CONTINENTAL, IberianClimateZone.forParcel(meseta));
        List<String> crops = PollinationContractCrops.forParcel(meseta);
        Assert.assertFalse(crops.contains("Campo de naranjos"));
        Assert.assertTrue(crops.contains("Campo de Colza"));
        Assert.assertTrue(crops.contains("Campo de girasoles"));
    }

    @Test
    public void mountainHasShortSeasonCropsOnly() {
        HexParcel gredos = parcel("hex_iberia_3_3", 40.25, -5.2, 2100);
        Assert.assertEquals(IberianClimateZone.MOUNTAIN, IberianClimateZone.forParcel(gredos));
        List<String> crops = PollinationContractCrops.forParcel(gredos);
        Assert.assertFalse(crops.contains("Campo de naranjos"));
        Assert.assertFalse(crops.contains("Campo de girasoles"));
        Assert.assertTrue(crops.contains("Campo de cerezos"));
        Assert.assertTrue(crops.contains(HexFlora.FACELIA));
    }

    @Test
    public void equatorialAddsGirasolesToCoverIberianWinter() {
        HexParcel east = parcel("hex_mdg_4_4", -18.5, 49.4, 80);
        Assert.assertEquals(MadagascarClimateZone.EQUATORIAL, MadagascarClimateZone.forParcel(east));
        List<String> crops = PollinationContractCrops.forParcel(east);
        Assert.assertTrue(crops.contains("Campo de girasoles"));
        Assert.assertTrue(crops.contains(HexFlora.LITCHI));
        Assert.assertTrue(crops.contains(HexFlora.TREBOL));
        Assert.assertTrue(crops.contains("Campo de naranjos"));
    }

    @Test
    public void tropicalCoversAustralWinterWithMango() {
        HexParcel west = parcel("hex_mdg_5_5", -18.0, 44.5, 200);
        Assert.assertEquals(MadagascarClimateZone.TROPICAL, MadagascarClimateZone.forParcel(west));
        List<String> crops = PollinationContractCrops.forParcel(west);
        Assert.assertTrue(crops.contains(HexFlora.MANGO));
        Assert.assertTrue(crops.contains(HexFlora.RABANIZA));
    }

    @Test
    public void iberiaWinterKeepRateIsLowAndMadagascarSummerIsHigh() {
        HexParcel levante = parcel("hex_iberia_1_1", 39.47, -0.38, 15);
        HexParcel east = parcel("hex_mdg_4_4", -18.5, 49.4, 80);
        Assert.assertTrue(PollinationContractCrops.seasonalKeepRate(levante, 20) < 0.2);
        Assert.assertEquals(1.0, PollinationContractCrops.seasonalKeepRate(levante, 120), 0.001);
        Assert.assertEquals(1.0, PollinationContractCrops.seasonalKeepRate(east, 20), 0.001);
        Assert.assertTrue(PollinationContractCrops.seasonalKeepRate(east, 180) < 0.2);
    }
}
