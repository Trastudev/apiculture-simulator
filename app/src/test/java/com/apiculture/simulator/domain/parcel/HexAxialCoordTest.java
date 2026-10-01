package com.apiculture.simulator.domain.parcel;

import org.junit.Assert;
import org.junit.Test;

public class HexAxialCoordTest {

    @Test
    public void neighbors_areSixAxialOffsets() {
        HexAxialCoord origin = new HexAxialCoord(3, -1);
        HexAxialCoord[] n = origin.neighbors();
        Assert.assertEquals(6, n.length);
        Assert.assertEquals(new HexAxialCoord(4, -1), n[0]);
        Assert.assertEquals(new HexAxialCoord(4, -2), n[1]);
        Assert.assertEquals(new HexAxialCoord(3, -2), n[2]);
        Assert.assertEquals(new HexAxialCoord(2, -1), n[3]);
        Assert.assertEquals(new HexAxialCoord(2, 0), n[4]);
        Assert.assertEquals(new HexAxialCoord(3, 0), n[5]);
    }

    @Test
    public void idWithAxial_keepsRegionPrefix() {
        HexParcel parcel = new HexParcel(
                "hex_iberia_3_-1",
                new double[][]{{0, 0}, {0, 1}, {1, 1}, {1, 0}, {0.5, -0.5}, {-0.5, 0.5}},
                40.0,
                -3.0,
                1.0,
                false);
        Assert.assertEquals("hex_iberia_4_-1", parcel.idWithAxial(new HexAxialCoord(4, -1)));
    }

    @Test
    public void distance_cubeMetricOnHexGrid() {
        HexAxialCoord origin = new HexAxialCoord(0, 0);
        Assert.assertEquals(0, origin.distanceTo(origin));
        Assert.assertEquals(1, origin.distanceTo(new HexAxialCoord(1, 0)));
        Assert.assertEquals(1, origin.distanceTo(new HexAxialCoord(0, -1)));
        Assert.assertEquals(2, origin.distanceTo(new HexAxialCoord(2, -1)));
        Assert.assertEquals(3, HexParcel.axialDistance("hex_iberia_0_0", "hex_iberia_3_0"));
        Assert.assertEquals(Integer.MAX_VALUE, HexParcel.axialDistance("hex_iberia_0_0", "nope"));
    }

    @Test
    public void neighborIds_sixAroundIberiaOrigin() {
        java.util.List<String> ids = HexParcel.neighborIds("hex_iberia_0_0");
        Assert.assertEquals(6, ids.size());
        Assert.assertTrue(ids.contains("hex_iberia_1_0"));
        Assert.assertTrue(ids.contains("hex_iberia_1_-1"));
        Assert.assertTrue(ids.contains("hex_iberia_0_-1"));
        Assert.assertTrue(ids.contains("hex_iberia_-1_0"));
        Assert.assertTrue(ids.contains("hex_iberia_-1_1"));
        Assert.assertTrue(ids.contains("hex_iberia_0_1"));
    }

    @Test
    public void nativeKeysForRegion_iberiaAndZaAreDistinct() {
        java.util.List<String> iberia = HexFlora.nativeKeysForRegion(false);
        java.util.List<String> za = HexFlora.nativeKeysForRegion(true);
        Assert.assertTrue(iberia.contains(HexFlora.CASTANO));
        Assert.assertTrue(iberia.contains("Romero"));
        Assert.assertTrue(za.contains(HexFlora.FYNBOS));
        Assert.assertTrue(za.contains(HexFlora.ALOE));
        Assert.assertTrue(za.contains(HexFlora.BUCHU));
        Assert.assertTrue(za.contains(HexFlora.PROTEA));
        Assert.assertTrue(za.contains(HexFlora.BOEKENHOUT));
        Assert.assertTrue(za.contains(HexFlora.MARULA));
        Assert.assertFalse(iberia.contains(HexFlora.FYNBOS));
        Assert.assertFalse(iberia.contains(HexFlora.PROTEA));
        Assert.assertFalse(iberia.contains(HexFlora.MARULA));
    }

    @Test
    public void canonicalKey_zaFloralsAreDistinctFromFynbos() {
        Assert.assertEquals(HexFlora.PROTEA, HexFlora.canonicalKey("protea"));
        Assert.assertEquals(HexFlora.FYNBOS, HexFlora.canonicalKey("fynbos"));
        Assert.assertEquals(HexFlora.BUCHU, HexFlora.canonicalKey("Agathosma"));
        Assert.assertEquals(HexFlora.BOEKENHOUT, HexFlora.canonicalKey("African beech"));
        Assert.assertEquals(HexFlora.AGUACATE, HexFlora.canonicalKey("avocado"));
        Assert.assertEquals(HexFlora.MARULA, HexFlora.canonicalKey("Marula"));
    }
}
