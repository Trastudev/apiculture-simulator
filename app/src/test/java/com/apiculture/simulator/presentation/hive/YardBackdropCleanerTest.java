package com.apiculture.simulator.presentation.hive;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class YardBackdropCleanerTest {

    @Test
    public void punchesDarkRoseFringeNextToSkyAndKeepsFoliage() {
        int w = 3;
        int h = 2;
        int[] px = new int[]{
                0,
                argb(255, 131, 0, 81),
                argb(255, 40, 160, 50),
                argb(255, 40, 160, 50),
                argb(255, 40, 160, 50),
                argb(255, 255, 120, 170)
        };
        YardBackdropCleaner.stripChromaMagenta(px, w, h);
        assertEquals(0, px[0]);
        assertEquals(0, px[1]);
        assertEquals(argb(255, 40, 160, 50), px[2]);
        assertEquals(argb(255, 40, 160, 50), px[3]);
        assertEquals(argb(255, 40, 160, 50), px[4]);
        assertEquals(argb(255, 255, 120, 170), px[5]);
    }

    @Test
    public void keepsPinkFlowerSurroundedBySand() {
        int w = 3;
        int h = 3;
        int sand = argb(255, 210, 150, 60);
        int flower = argb(255, 255, 90, 140);
        int[] px = new int[]{
                sand, sand, sand,
                sand, flower, sand,
                sand, sand, sand
        };
        YardBackdropCleaner.stripChromaMagenta(px, w, h);
        assertEquals(flower, px[4]);
        assertEquals(sand, px[0]);
    }

    @Test
    public void doesNotEatPinkFlowerWithDarkCenter() {
        int w = 3;
        int h = 3;
        int trans = 0;
        int green = argb(255, 40, 160, 50);
        int pink = argb(255, 131, 0, 81);
        int dark = argb(255, 10, 8, 10);
        int[] px = new int[]{
                trans, trans, trans,
                green, green, green,
                pink, dark, pink
        };
        YardBackdropCleaner.stripChromaMagenta(px, w, h);
        assertEquals(0, px[0]);
        assertEquals(green, px[4]);
        assertEquals(pink, px[6]);
        assertEquals(dark, px[7]);
        assertEquals(pink, px[8]);
    }

    @Test
    public void chromaHeuristicsMatchDarkRoseNotGreen() {
        assertTrue(YardBackdropCleaner.isSkyFringe(131, 0, 81));
        assertFalse(YardBackdropCleaner.isSkyFringe(255, 0, 255));
        assertFalse(YardBackdropCleaner.isSkyFringe(40, 160, 50));
        assertFalse(YardBackdropCleaner.isSkyFringe(255, 120, 170));
        assertFalse(YardBackdropCleaner.isSkyFringe(255, 90, 140));
    }

    private static int argb(int a, int r, int g, int b) {
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
