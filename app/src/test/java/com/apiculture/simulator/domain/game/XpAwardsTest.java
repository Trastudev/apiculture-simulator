package com.apiculture.simulator.domain.game;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class XpAwardsTest {

    @Test
    public void honeyBelowStepGivesNothing() {
        assertEquals(0.0, XpAwards.honeySold(0.19), 1e-9);
        assertEquals(0.0, XpAwards.marketSold(0.0), 1e-9);
    }

    @Test
    public void honeyStepsByTwoTenths() {
        assertEquals(0.2, XpAwards.honeySold(0.2), 1e-9);
        assertEquals(0.2, XpAwards.honeySold(0.39), 1e-9);
        assertEquals(1.0, XpAwards.honeySold(1.0), 1e-9);
        assertEquals(4.0, XpAwards.honeySold(4.0), 1e-9);
    }

    @Test
    public void orderAddsFlatPlusHoney() {
        assertEquals(2.2, XpAwards.orderDelivered(0.2), 1e-9);
        assertEquals(4.0, XpAwards.orderDelivered(2.0), 1e-9);
    }
}
