package com.apiculture.simulator.domain.map;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class GraphProfileHashTest {

    @Test
    public void parseText_readsCarHash() {
        String txt = "#graphhopper\nprofiles=car|475363489\ngraph.dimension=2\n";
        assertEquals(Integer.valueOf(475363489), GraphProfileHash.parseText(txt));
    }

    @Test
    public void parseText_ignoresOtherProfiles() {
        assertEquals(Integer.valueOf(12), GraphProfileHash.parseText("profiles=bike|9,car|12\n"));
    }

    @Test
    public void parseText_missing() {
        assertNull(GraphProfileHash.parseText("graph.dimension=2\n"));
    }
}
