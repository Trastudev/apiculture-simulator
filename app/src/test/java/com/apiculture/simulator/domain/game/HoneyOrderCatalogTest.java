package com.apiculture.simulator.domain.game;

import com.apiculture.simulator.domain.parcel.HexFlora;
import com.apiculture.simulator.domain.parcel.HexParcel;

import org.junit.Assert;
import org.junit.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class HoneyOrderCatalogTest {

    @Test
    public void bandDailyCountIsTwoOrdersPerFiftyHexes() {
        OfferBand band = OfferBand.at(0);
        Assert.assertEquals(0, HoneyOrderCatalog.bandDailyCount(band, 0));
        Assert.assertEquals(0, HoneyOrderCatalog.bandDailyCount(band, 49));
        Assert.assertEquals(2, HoneyOrderCatalog.bandDailyCount(band, 50));
        Assert.assertEquals(63, HoneyOrderCatalog.bandDailyCount(band, 1588));
        Assert.assertEquals(269, HoneyOrderCatalog.bandDailyCount(band, 6742));
    }

    @Test
    public void madagascarOrdersUseLocalPoolNotSouthAfrica() {
        HexParcel east = new HexParcel(
                "hex_mdg_0_0",
                new double[][]{{0, 0}, {0, 1}, {1, 1}, {1, 0}, {0.5, -0.5}, {-0.5, 0.5}},
                -18.1,
                49.3,
                1.0,
                true,
                80);
        Assert.assertEquals(MadagascarClimateZone.EQUATORIAL, MadagascarClimateZone.forParcel(east));
        List<String> pool = HexFlora.nativePoolForZone(MadagascarClimateZone.EQUATORIAL);
        Set<String> seen = new HashSet<>();
        for (long seed = 0; seed < 80; seed++) {
            String flora = HoneyOrderCatalog.floraForParcel(east, seed);
            Assert.assertTrue(flora, pool.contains(flora));
            Assert.assertFalse("Bosque".equals(flora));
            seen.add(flora);
        }
        Assert.assertTrue(seen.contains(HexFlora.RAVINTSARA));
        Assert.assertTrue(seen.contains(HexFlora.GIROFLE));
        Assert.assertTrue(seen.contains(HexFlora.LONGOSE));
    }

    @Test
    public void pickScatteredSpreadsAcrossIberia() {
        List<HexParcel> parcels = new java.util.ArrayList<>();
        double[][] ring = {{0, 0}, {0, 1}, {1, 1}, {1, 0}, {0.5, -0.5}, {-0.5, 0.5}};
        double minLat = com.apiculture.simulator.domain.parcel.IberiaBounds.BOX.minLat + 0.4;
        double maxLat = com.apiculture.simulator.domain.parcel.IberiaBounds.BOX.maxLat - 0.4;
        double minLon = com.apiculture.simulator.domain.parcel.IberiaBounds.BOX.minLon + 0.4;
        double maxLon = com.apiculture.simulator.domain.parcel.IberiaBounds.BOX.maxLon - 0.4;
        for (int r = 0; r < 12; r++) {
            for (int c = 0; c < 14; c++) {
                double lat = minLat + (maxLat - minLat) * r / 11.0;
                double lon = minLon + (maxLon - minLon) * c / 13.0;
                parcels.add(new HexParcel("hex_iberia_" + r + "_" + c, ring, lat, lon, 70.0, false, 80));
            }
        }
        OfferBand band = OfferBand.ofLevel(40);
        List<HexParcel> dests = HoneyOrderCatalog.pickScattered(
                parcels, band, 16, new HashSet<>(), 20260921L);
        Assert.assertTrue("got " + dests.size() + " eligible "
                + HoneyOrderCatalog.countEligible(parcels, band), dests.size() >= 8);
        double dMinLat = 90;
        double dMaxLat = -90;
        double dMinLon = 180;
        double dMaxLon = -180;
        for (int i = 0; i < dests.size(); i++) {
            dMinLat = Math.min(dMinLat, dests.get(i).centroidLat);
            dMaxLat = Math.max(dMaxLat, dests.get(i).centroidLat);
            dMinLon = Math.min(dMinLon, dests.get(i).centroidLon);
            dMaxLon = Math.max(dMaxLon, dests.get(i).centroidLon);
        }
        Assert.assertTrue("lat " + (dMaxLat - dMinLat) + " lon " + (dMaxLon - dMinLon),
                (dMaxLat - dMinLat) >= 1.5 || (dMaxLon - dMinLon) >= 3.0);
    }

    @Test
    public void pickScatteredForLowBandIgnoresLockedMadagascarWest() {
        List<HexParcel> parcels = new java.util.ArrayList<>();
        double[][] ring = {{0, 0}, {0, 1}, {1, 1}, {1, 0}, {0.5, -0.5}, {-0.5, 0.5}};
        for (int r = 0; r < 10; r++) {
            for (int c = 0; c < 8; c++) {
                double lat = -24.0 + r * 0.6;
                double westLon = 44.4 + c * 0.08;
                double eastLon = 49.0 + c * 0.08;
                parcels.add(new HexParcel("hex_mdg_w_" + r + "_" + c, ring, lat, westLon, 90.0, false, 80));
                parcels.add(new HexParcel("hex_mdg_e_" + r + "_" + c, ring, lat, eastLon, 90.0, false, 80));
            }
        }
        OfferBand band = OfferBand.ofLevel(0);
        List<HexParcel> dests = HoneyOrderCatalog.pickScattered(
                parcels, band, 12, new HashSet<>(), 20260921L);
        Assert.assertTrue("got " + dests.size(), dests.size() >= 8);
        double dMinLat = 90;
        double dMaxLat = -90;
        for (int i = 0; i < dests.size(); i++) {
            Assert.assertTrue(dests.get(i).id, dests.get(i).centroidLon >= 48.8);
            dMinLat = Math.min(dMinLat, dests.get(i).centroidLat);
            dMaxLat = Math.max(dMaxLat, dests.get(i).centroidLat);
        }
        Assert.assertTrue("lat span " + (dMaxLat - dMinLat), dMaxLat - dMinLat >= 2.0);
    }
}
