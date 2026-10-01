package com.apiculture.simulator.domain.map;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class RoadPathTest {

    @Test
    public void withEndpoints_prependsAndAppendsFieldAccess() {
        List<double[]> road = new ArrayList<>();
        road.add(new double[]{40.42, -3.70});
        road.add(new double[]{40.43, -3.69});
        road.add(new double[]{40.44, -3.68});
        RoadPath path = new RoadPath(road, EncodedPolyline.encode(road), RoadPath.pathKm(road), "AN");
        RoadPath full = RoadPath.withEndpoints(path, 40.41, -3.71, 40.45, -3.67);
        assertTrue(full.followsRoads());
        assertEquals(5, full.points.size());
        assertEquals(40.41, full.points.get(0)[0], 1e-6);
        assertEquals(40.45, full.points.get(4)[0], 1e-6);
        assertEquals("OANO", full.edgeKinds);
    }

    @Test
    public void geodesic_isTwoPoints() {
        RoadPath g = RoadPath.geodesic(40, -3, 41, -2);
        assertEquals(2, g.points.size());
        assertTrue(!g.followsRoads());
    }
}
