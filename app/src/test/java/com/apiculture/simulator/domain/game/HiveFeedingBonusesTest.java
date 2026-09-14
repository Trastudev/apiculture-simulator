package com.apiculture.simulator.domain.game;

import com.apiculture.simulator.data.local.entity.HiveEntity;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class HiveFeedingBonusesTest {

    @Test
    public void feedingHalvesConsumptionWhileActive() {
        HiveEntity h = new HiveEntity();
        h.id = "feed";
        h.feedHoneyBonusEndDayKeyExclusive = 10;
        assertEquals(HiveCareRules.FEED_CONSUMPTION_MULTIPLIER,
                HiveFeedingBonuses.consumptionMultiplierForDay(h, 9), 1e-9);
        assertEquals(1.0, HiveFeedingBonuses.consumptionMultiplierForDay(h, 10), 1e-9);
        assertEquals(1, HiveFeedingBonuses.feedingDaysRemaining(h, 9));
        assertEquals(1.0, HiveFeedingBonuses.honeyMultiplierForDay(h, 9), 1e-9);
    }
}
