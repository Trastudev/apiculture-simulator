package com.apiculture.simulator.domain.map;

import com.apiculture.simulator.domain.parcel.BoundingBox;
import com.apiculture.simulator.domain.parcel.LandMask;
import com.apiculture.simulator.domain.parcel.RectangleLandMask;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class LandSafeRouteTest {

    /** Tierra continua con un golfo de agua que corta la recta. */
    private static LandMask gulf() {
        final LandMask land = new RectangleLandMask(new BoundingBox(-26.2, -23.0, 45.0, 48.2));
        final LandMask hole = new RectangleLandMask(new BoundingBox(-25.4, -23.6, 46.05, 46.95));
        return (latDeg, lonDeg) -> land.isLand(latDeg, lonDeg) && !hole.isLand(latDeg, lonDeg);
    }

    @Test
    public void geodesicAcrossGulfIsWater() {
        assertTrue(LandSafeRoute.geodesicCrossesWater(gulf(), -24.5, 45.4, -24.5, 47.6));
    }

    @Test
    public void fieldTrackDetoursAroundWater() {
        RoadPath path = LandSafeRoute.fieldTrackOnLand(gulf(), -24.5, 45.4, -24.5, 47.6);
        assertNotNull(path);
        assertTrue(path.points.size() > 2);
        LandMask mask = gulf();
        List<double[]> pts = path.points;
        for (int i = 1; i < pts.size(); i++) {
            double[] a = pts.get(i - 1);
            double[] b = pts.get(i);
            assertFalse(LandSafeRoute.segmentCrossesWater(mask, a[0], a[1], b[0], b[1]));
        }
    }
}
