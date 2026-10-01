package com.apiculture.simulator.domain.game;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HiveProductionEligibilityTest {

    @Test
    public void purchaseDayDoesNotProduce() {
        int bought = GameCalendar.toDayKey(java.time.LocalDate.of(2026, 9, 20));
        int first = HiveProductionEligibility.firstDayAfter(bought);
        assertEquals(20260921, first);
        assertFalse(HiveProductionEligibility.participatesOnDay(first, bought));
        assertTrue(HiveProductionEligibility.participatesOnDay(first, first));
    }

    @Test
    public void boughtBeforeEightEntersThatMorning() {
        java.time.ZonedDateTime atSeven = java.time.ZonedDateTime.of(
                2026, 9, 29, 7, 30, 0, 0, java.time.ZoneId.of("Europe/Madrid"));
        assertEquals(20260929, HiveProductionEligibility.firstDayAtNextProduction(atSeven));
    }

    @Test
    public void boughtAfterEightWaitsForNextMorning() {
        java.time.ZonedDateTime atNine = java.time.ZonedDateTime.of(
                2026, 9, 29, 9, 0, 0, 0, java.time.ZoneId.of("Europe/Madrid"));
        assertEquals(20260930, HiveProductionEligibility.firstDayAtNextProduction(atNine));
    }

    @Test
    public void legacyZeroIsAlwaysEligible() {
        assertTrue(HiveProductionEligibility.participatesOnDay(0, 20260920));
    }
}
