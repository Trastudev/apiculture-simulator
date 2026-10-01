package com.apiculture.simulator.domain.parcel;

import com.apiculture.simulator.data.local.entity.HexParcelOwnershipEntity;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HexParcelRandomPointTest {

    @Test
    public void collectPointUsesApiaryPinNotScatteredHive() {
        HexParcelOwnershipEntity warehouseOnly = new HexParcelOwnershipEntity();
        warehouseOnly.hasWarehouse = true;
        warehouseOnly.warehouseLat = 41.30;
        warehouseOnly.warehouseLng = 2.10;
        warehouseOnly.siteLat = 41.30;
        warehouseOnly.siteLng = 2.10;

        HexParcelOwnershipEntity apiary = new HexParcelOwnershipEntity();
        apiary.siteLat = 41.41;
        apiary.siteLng = 2.22;
        apiary.siteId = "yard";

        double[] p = HexParcelRandomPoint.collectPointForHive(
                null, Arrays.asList(warehouseOnly, apiary), 40.01, -3.70);
        assertEquals(41.41, p[0], 1e-6);
        assertEquals(2.22, p[1], 1e-6);
    }

    @Test
    public void belongsToSiteKeepsHivesOnNearestApiary() {
        HexParcelOwnershipEntity first = new HexParcelOwnershipEntity();
        first.siteId = "a";
        first.siteLat = 41.10;
        first.siteLng = 2.10;

        HexParcelOwnershipEntity second = new HexParcelOwnershipEntity();
        second.siteId = "b";
        second.siteLat = 41.40;
        second.siteLng = 2.40;

        java.util.List<HexParcelOwnershipEntity> sites = Arrays.asList(first, second);
        assertTrue(HexParcelRandomPoint.belongsToSite(null, sites, 41.11, 2.11, "a"));
        assertFalse(HexParcelRandomPoint.belongsToSite(null, sites, 41.11, 2.11, "b"));
        assertTrue(HexParcelRandomPoint.belongsToSite(null, sites, 41.39, 2.39, "b"));
    }

    @Test
    public void pinForOwnedApiaryUsesRequestedSite() {
        HexParcelOwnershipEntity first = new HexParcelOwnershipEntity();
        first.siteId = "a";
        first.siteLat = 41.10;
        first.siteLng = 2.10;

        HexParcelOwnershipEntity second = new HexParcelOwnershipEntity();
        second.siteId = "b";
        second.siteLat = 41.40;
        second.siteLng = 2.40;

        double[] p = HexParcelRandomPoint.pinForOwnedApiary(
                null, Arrays.asList(first, second), "b", Double.NaN, Double.NaN);
        assertEquals(41.40, p[0], 1e-6);
        assertEquals(2.40, p[1], 1e-6);
    }
}
