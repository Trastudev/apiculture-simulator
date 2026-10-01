package com.apiculture.simulator.domain.parcel;

import com.apiculture.simulator.data.local.entity.HexParcelOwnershipEntity;
import com.apiculture.simulator.data.local.entity.HiveEntity;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HexApiaryTest {

    @Test
    public void persistedSiteIdWinsOverNearestPin() {
        HexParcelOwnershipEntity a = site("a", 41.10, 2.10);
        HexParcelOwnershipEntity b = site("b", 41.40, 2.40);
        HiveEntity hive = hive("hex", 41.39, 2.39);
        hive.siteId = "a";
        assertTrue(HexApiary.hiveOnSite(hive, "hex", "a", null, Arrays.asList(a, b)));
        assertFalse(HexApiary.hiveOnSite(hive, "hex", "b", null, Arrays.asList(a, b)));
    }

    @Test
    public void legacyHiveUsesNearestApiary() {
        HexParcelOwnershipEntity a = site("a", 41.10, 2.10);
        HexParcelOwnershipEntity b = site("b", 41.40, 2.40);
        HiveEntity hive = hive("hex", 41.11, 2.11);
        assertEquals("a", HexApiary.resolveSiteId(hive, null, Arrays.asList(a, b)));
        assertTrue(HexApiary.hiveOnSite(hive, "hex", "a", null, Arrays.asList(a, b)));
        assertFalse(HexApiary.hiveOnSite(hive, "hex", "b", null, Arrays.asList(a, b)));
    }

    private static HexParcelOwnershipEntity site(String id, double lat, double lng) {
        HexParcelOwnershipEntity row = new HexParcelOwnershipEntity();
        row.hexId = "hex";
        row.siteId = id;
        row.siteLat = lat;
        row.siteLng = lng;
        return row;
    }

    private static HiveEntity hive(String hexId, double lat, double lng) {
        HiveEntity h = new HiveEntity();
        h.id = "h";
        h.hexId = hexId;
        h.lat = lat;
        h.lng = lng;
        return h;
    }
}
