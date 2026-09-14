package com.apiculture.simulator.domain.game;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class IberianClimateZoneTest {

    @Test
    public void sierraNevadaIsMountain() {
        assertEquals(IberianClimateZone.MOUNTAIN,
                IberianClimateZone.fromLatLonElev(37.05, -3.31, -1));
        assertTrue(IberianClimateZone.isHighSierraOver2000m(37.05, -3.31));
    }

    @Test
    public void anetoIsMountain() {
        assertEquals(IberianClimateZone.MOUNTAIN,
                IberianClimateZone.fromLatLonElev(42.63, 0.66, -1));
    }

    @Test
    public void cadiMoixeroRibesAndCavalleraAreMountain() {
        assertEquals(IberianClimateZone.MOUNTAIN,
                IberianClimateZone.fromLatLonElev(42.28, 1.70, -1));
        assertEquals(IberianClimateZone.MOUNTAIN,
                IberianClimateZone.fromLatLonElev(42.306, 2.168, -1));
        assertEquals(IberianClimateZone.MOUNTAIN,
                IberianClimateZone.fromLatLonElev(42.37, 2.27, -1));
        assertTrue(IberianClimateZone.isHighSierraOver2000m(42.28, 1.70));
        assertTrue(IberianClimateZone.isHighSierraOver2000m(42.306, 2.168));
        assertTrue(IberianClimateZone.isHighSierraOver2000m(42.37, 2.27));
    }

    @Test
    public void picosDeEuropaAreMountain() {
        assertEquals(IberianClimateZone.MOUNTAIN,
                IberianClimateZone.fromLatLonElev(43.20, -4.85, -1));
    }

    @Test
    public void gredosAndGuadarramaAreMountain() {
        assertEquals(IberianClimateZone.MOUNTAIN,
                IberianClimateZone.fromLatLonElev(40.25, -5.30, -1));
        assertEquals(IberianClimateZone.MOUNTAIN,
                IberianClimateZone.fromLatLonElev(40.85, -3.96, -1));
    }

    @Test
    public void madridAndSevilleAreNotMountain() {
        assertFalse(IberianClimateZone.isHighSierraOver2000m(40.42, -3.70));
        assertEquals(IberianClimateZone.SOUTH,
                IberianClimateZone.fromLatLonElev(37.39, -5.99, -1));
    }

    @Test
    public void measuredElevationOver2000ForcesMountain() {
        assertEquals(IberianClimateZone.MOUNTAIN,
                IberianClimateZone.fromLatLonElev(40.42, -3.70, 2100));
    }
}
