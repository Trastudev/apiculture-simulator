package com.apiculture.simulator.domain.game;

import com.apiculture.simulator.domain.parcel.HexParcel;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class OfferBandTest {

    @Test
    public void tenBandsCoverZeroToForty() {
        Assert.assertEquals(0, OfferBand.ofLevel(0).index);
        Assert.assertEquals(0, OfferBand.ofLevel(1).index);
        Assert.assertEquals(1, OfferBand.ofLevel(2).index);
        Assert.assertEquals(1, OfferBand.ofLevel(4).index);
        Assert.assertEquals(2, OfferBand.ofLevel(5).index);
        Assert.assertEquals(8, OfferBand.ofLevel(35).index);
        Assert.assertEquals(9, OfferBand.ofLevel(40).index);
        Assert.assertEquals(9, OfferBand.ofLevel(80).index);
        Assert.assertTrue(OfferBand.ofLevel(1).pollinationCount > 0);
        Assert.assertTrue(OfferBand.ofLevel(2).pollinationCount > 0);
        Assert.assertTrue(OfferBand.ofLevel(0).orderCount >= 12);
        Assert.assertEquals(0.5, OfferBand.at(0).kgMin, 1e-9);
        Assert.assertEquals(30.0, OfferBand.at(9).kgMax, 1e-9);
    }

    @Test
    public void kgStaysInsideBand() {
        OfferBand b = OfferBand.at(1);
        for (long s = 0; s < 50; s++) {
            double kg = b.kgForSeed(s);
            Assert.assertTrue(kg + "", kg + 1e-6 >= b.kgMin && kg - 1e-6 <= b.kgMax);
        }
    }

    @Test
    public void replenishStopsTwoHoursBeforeUtcMidnight() {
        int day = 20260921;
        long end = OfferReplenish.utcDayEndEpochMs(day);
        Assert.assertFalse(OfferReplenish.canReplenish(end - 60_000L, day));
        Assert.assertTrue(OfferReplenish.canReplenish(end - 3L * 60 * 60 * 1000, day));
        Assert.assertEquals(24L * 60 * 60 * 1000, end - OfferReplenish.utcDayStartEpochMs(day));
    }

    @Test
    public void pickNearbyStaysInsideRadius() {
        List<HexParcel> parcels = new ArrayList<>();
        parcels.add(p("a", 40.0, -3.0));
        parcels.add(p("b", 40.15, -3.0));
        parcels.add(p("c", 41.5, -3.0));
        HexParcel hit = OfferReplenish.pickNearby(parcels, 40.0, -3.0,
                Collections.singleton("a"), 1L, x -> true);
        Assert.assertNotNull(hit);
        Assert.assertEquals("b", hit.id);
    }

    private static HexParcel p(String id, double lat, double lng) {
        return new HexParcel(id, new double[][]{{0, 0}, {0, 1}, {1, 1}, {1, 0}, {0.5, -0.5}, {-0.5, 0.5}},
                lat, lng, 1.0, false, 80);
    }
}
