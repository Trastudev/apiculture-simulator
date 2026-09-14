package com.apiculture.simulator.domain.map;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class PlayableMapRegionTest {

    @Test
    public void hexTargetAreaKm2_iberiaHalfOfSouthAfrica() {
        assertEquals(70.0, PlayableMapRegion.IBERIA.hexTargetAreaKm2(), 0.0);
        assertEquals(140.0, PlayableMapRegion.SOUTH_AFRICA.hexTargetAreaKm2(), 0.0);
    }
}
