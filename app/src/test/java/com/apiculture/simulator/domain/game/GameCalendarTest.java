package com.apiculture.simulator.domain.game;

import org.junit.Test;

import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.junit.Assert.assertEquals;

public class GameCalendarTest {

    @Test
    public void globalMarketUsesUtcNotDeviceZone() {
        assertEquals(ZoneOffset.UTC, GameCalendar.globalMarketTimeZone());
        int expected = GameCalendar.toDayKey(LocalDate.now(ZoneOffset.UTC));
        assertEquals(expected, GameCalendar.currentGlobalMarketDayKey());
    }

    @Test
    public void toDayKeyIsYyyyMmDd() {
        assertEquals(20260910, GameCalendar.toDayKey(LocalDate.of(2026, 9, 10)));
        assertEquals(LocalDate.of(2026, 9, 10), GameCalendar.fromDayKey(20260910));
    }
}
