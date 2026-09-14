package com.apiculture.simulator.domain.game;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class QueenInventoryCodecTest {

    @Test
    public void roundTrip() {
        List<Integer> src = Arrays.asList(100, 72, 88);
        String json = QueenInventoryCodec.toJson(src);
        assertEquals("[100,72,88]", json);
        assertEquals(src, QueenInventoryCodec.parse(json));
    }

    @Test
    public void parseEmptyAndLegacy() {
        assertTrue(QueenInventoryCodec.parse(null).isEmpty());
        assertTrue(QueenInventoryCodec.parse("[]").isEmpty());
        assertEquals(Arrays.asList(100, 100, 100),
                QueenInventoryCodec.fromLegacyCount(3, 100));
    }

    @Test
    public void clampQuality() {
        assertEquals(0, QueenInventoryCodec.clamp(-4));
        assertEquals(100, QueenInventoryCodec.clamp(140));
    }
}
