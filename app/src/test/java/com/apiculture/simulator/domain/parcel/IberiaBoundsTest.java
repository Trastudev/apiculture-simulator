package com.apiculture.simulator.domain.parcel;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class IberiaBoundsTest {

    @Test
    public void keepsSpainAndBalearics() {
        assertTrue(IberiaBounds.keepCentroid(41.3874, 2.1686));
        assertTrue(IberiaBounds.keepCentroid(40.4168, -3.7038));
        assertTrue(IberiaBounds.keepCentroid(43.37, -8.40));
        assertTrue(IberiaBounds.keepCentroid(43.79, -7.68));
        assertTrue(IberiaBounds.keepCentroid(36.02, -5.61));
        assertTrue(IberiaBounds.keepCentroid(36.84, -2.46));
        assertTrue(IberiaBounds.keepCentroid(37.60, -0.98));
        assertTrue(IberiaBounds.keepCentroid(39.57, 2.65));
    }

    @Test
    public void dropsAfrica() {
        assertFalse(IberiaBounds.keepCentroid(35.76, -5.83));
        assertFalse(IberiaBounds.keepCentroid(35.89, -5.32));
        assertFalse(IberiaBounds.keepCentroid(35.29, -2.94));
        assertFalse(IberiaBounds.keepCentroid(36.75, 3.06));
        assertFalse(IberiaBounds.keepCentroid(36.51, 1.31));
        assertFalse(IberiaBounds.keepCentroid(36.91, 3.91));
    }

    @Test
    public void dropsDeepFranceKeepsBorderOverflow() {
        assertFalse(IberiaBounds.keepCentroid(43.60, 1.44));
        assertFalse(IberiaBounds.keepCentroid(43.18, 3.00));
        assertTrue(IberiaBounds.keepCentroid(43.36, -1.78));
        assertTrue(IberiaBounds.keepCentroid(42.50, 3.10));
    }
}
