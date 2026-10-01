package com.apiculture.simulator.domain.map;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PlayableMapRegionTest {

    @Test
    public void hexTargetAreaKm2_iberiaHalfOfSouthAfrica() {
        assertEquals(70.0, PlayableMapRegion.IBERIA.hexTargetAreaKm2(), 0.0);
        assertEquals(140.0, PlayableMapRegion.SOUTH_AFRICA.hexTargetAreaKm2(), 0.0);
        assertEquals(90.0, PlayableMapRegion.MADAGASCAR.hexTargetAreaKm2(), 0.0);
        assertEquals(PlayableMapRegion.MADAGASCAR, PlayableMapRegion.fromHexId("hex_mdg_1_2"));
        assertEquals(PlayableMapRegion.MADAGASCAR, PlayableMapRegion.containing(-18.88, 47.51));
    }

    @Test
    public void madagascarHasOwnRoadGraphPack() {
        assertTrue(PlayableMapRegion.MADAGASCAR.hasRoadGraph());
        assertTrue(PlayableMapRegion.IBERIA.hasRoadGraph());
        assertTrue(PlayableMapRegion.SOUTH_AFRICA.hasRoadGraph());
        assertTrue(PlayableMapRegion.MADAGASCAR.roadGraphArchiveName().contains("madagascar"));
        assertTrue(PlayableMapRegion.MADAGASCAR.roadGraphReleaseUrl().contains("madagascar-car-lite"));
        assertTrue(PlayableMapRegion.SOUTH_AFRICA.roadGraphArchiveName().contains("za-car-lite"));
        assertTrue(PlayableMapRegion.SOUTH_AFRICA.roadGraphReleaseUrl().contains("routing-graph-za-v1"));
    }

    @Test
    public void minZoom_isTighterThanPeninsulaOverview() {
        assertTrue(PlayableMapRegion.IBERIA.minZoom() >= 6.85f);
        assertTrue(PlayableMapRegion.SOUTH_AFRICA.minZoom() >= 6.65f);
        assertTrue(PlayableMapRegion.IBERIA.defaultZoom() >= PlayableMapRegion.IBERIA.minZoom());
        assertTrue(PlayableMapRegion.SOUTH_AFRICA.defaultZoom()
                >= PlayableMapRegion.SOUTH_AFRICA.minZoom());
    }
}
