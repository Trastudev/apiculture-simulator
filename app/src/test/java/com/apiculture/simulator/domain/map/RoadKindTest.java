package com.apiculture.simulator.domain.map;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class RoadKindTest {

    @Test
    public void fromOsm_mapsSpanishClasses() {
        assertEquals(RoadKind.AUTOPISTA, RoadKind.fromOsm("MOTORWAY"));
        assertEquals(RoadKind.AUTOPISTA, RoadKind.fromOsm("motorway_link"));
        assertEquals(RoadKind.AUTOPISTA, RoadKind.fromOsm("trunk"));
        assertEquals(RoadKind.COMARCAL, RoadKind.fromOsm("PRIMARY"));
        assertEquals(RoadKind.COMARCAL, RoadKind.fromOsm("SECONDARY"));
        assertEquals(RoadKind.OTRO, RoadKind.fromOsm("tertiary"));
        assertEquals(RoadKind.OTRO, RoadKind.fromOsm("unclassified"));
    }

    @Test
    public void fromRef_usesOfficialCodes() {
        assertEquals(RoadKind.AUTOPISTA, RoadKind.fromRef("AP-7"));
        assertEquals(RoadKind.AUTOPISTA, RoadKind.fromRef("Autovía A-3"));
        assertEquals(RoadKind.AUTOPISTA, RoadKind.fromRef("M-40"));
        assertEquals(RoadKind.NACIONAL, RoadKind.fromRef("N-340"));
        assertEquals(RoadKind.NACIONAL, RoadKind.fromRef("Carretera Nacional N-II"));
        assertEquals(RoadKind.NACIONAL, RoadKind.fromRef("EN 125"));
        assertEquals(RoadKind.COMARCAL, RoadKind.fromRef("C-32"));
        assertEquals(RoadKind.COMARCAL, RoadKind.fromRef("CV-35"));
        assertEquals(RoadKind.COMARCAL, RoadKind.fromRef("M-501"));
        assertEquals(RoadKind.OTRO, RoadKind.fromRef("Calle Mayor"));
        assertEquals(RoadKind.OTRO, RoadKind.fromRef("Camino del Prado"));
        assertNull(RoadKind.fromRef(""));
        assertNull(RoadKind.fromRef("sin nombre"));
    }

    @Test
    public void fromSpeed_neverCallsUnnamedRoadsNacional() {
        assertEquals(RoadKind.AUTOPISTA, RoadKind.fromSpeedKmh(120));
        assertEquals(RoadKind.OTRO, RoadKind.fromSpeedKmh(90));
        assertEquals(RoadKind.OTRO, RoadKind.fromSpeedKmh(80));
        assertEquals(RoadKind.OTRO, RoadKind.fromSpeedKmh(40));
    }

    @Test
    public void fit_padsAndTruncates() {
        assertEquals("ANOO", RoadKind.fit("AN", 4));
        assertEquals("A", RoadKind.fit("ANC", 1));
        assertEquals("", RoadKind.fit("A", 0));
    }
}
