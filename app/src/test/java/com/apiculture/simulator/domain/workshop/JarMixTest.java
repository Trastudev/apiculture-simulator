package com.apiculture.simulator.domain.workshop;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class JarMixTest {

    @Test
    public void roundsUpAndFillsWithBigJarsFirst() {
        JarMix m = JarMix.fromKg(2.45);
        assertEquals(new JarMix(2, 1, 0), m);
        assertEquals(2.5, m.kg(), 1e-9);
    }

    @Test
    public void smallOrdersGetSmallJars() {
        assertEquals(new JarMix(0, 1, 0), JarMix.fromKg(0.3));
        assertEquals(new JarMix(0, 0, 1), JarMix.fromKg(0.2));
        assertEquals(new JarMix(1, 1, 1), JarMix.fromKg(1.7));
        assertEquals(new JarMix(3, 0, 0), JarMix.fromKg(3.0));
    }

    @Test
    public void encodesAndParses() {
        JarMix m = new JarMix(2, 1, 1);
        assertEquals("JARS:2/1/1", m.encode());
        assertEquals(m, JarMix.parse(m.encode()));
        assertNull(JarMix.parse("JAR_500"));
        assertNull(JarMix.parse("JARS:0/0/0"));
    }

    @Test
    public void smallerJarsPayMorePerKg() {
        double kilo = 20.0;
        assertEquals(20.0, new JarMix(1, 0, 0).unitPrice(kilo), 1e-9);
        assertEquals(24.0, new JarMix(0, 1, 0).unitPrice(kilo), 1e-9);
        assertEquals(29.0, new JarMix(0, 0, 1).unitPrice(kilo), 1e-9);
        double mixed = new JarMix(2, 1, 0).unitPrice(kilo);
        assertTrue(mixed > 20.0 && mixed < 24.0);
    }

    @Test
    public void bulkIsSixTenthsOfTheKiloJar() {
        assertEquals(0.6, 1.0 / WorkshopRules.Format.JAR_1000.priceFactor, 1e-9);
        assertTrue(WorkshopRules.Format.JAR_250.priceFactor > WorkshopRules.Format.JAR_500.priceFactor);
    }
}
