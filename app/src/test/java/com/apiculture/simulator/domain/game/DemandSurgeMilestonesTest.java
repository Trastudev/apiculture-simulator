package com.apiculture.simulator.domain.game;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.apiculture.simulator.domain.admin.AdminRoles;

public class DemandSurgeMilestonesTest {

    @Test
    public void highestReached_thresholds() {
        assertEquals(0, DemandSurgeMilestones.highestReached(0, 1000));
        assertEquals(15, DemandSurgeMilestones.highestReached(150, 1000));
        assertEquals(30, DemandSurgeMilestones.highestReached(300, 1000));
        assertEquals(50, DemandSurgeMilestones.highestReached(500, 1000));
        assertEquals(75, DemandSurgeMilestones.highestReached(750, 1000));
        assertEquals(100, DemandSurgeMilestones.highestReached(1000, 1000));
        assertEquals(100, DemandSurgeMilestones.highestReached(1200, 1000));
    }

    @Test
    public void rewardsAccumulate() {
        assertEquals(0, DemandSurgeMilestones.coinsForHighest(0));
        assertEquals(200, DemandSurgeMilestones.coinsForHighest(15));
        assertEquals(200, DemandSurgeMilestones.coinsForHighest(30));
        assertEquals(450, DemandSurgeMilestones.coinsForHighest(50));
        assertEquals(10, DemandSurgeMilestones.treatmentsForHighest(30));
        assertEquals(0, DemandSurgeMilestones.feedForHighest(50));
        assertEquals(10, DemandSurgeMilestones.feedForHighest(75));
        assertEquals(5, DemandSurgeMilestones.queensForHighest(100));
    }

    @Test
    public void ticksLabelMarksReached() {
        assertEquals("15%  ·  30%  ·  50%  ·  75%  ·  100%", DemandSurgeMilestones.ticksLabel(0));
        assertTrue(DemandSurgeMilestones.ticksLabel(30).contains("15% ✓"));
        assertTrue(DemandSurgeMilestones.ticksLabel(30).contains("30% ✓"));
        assertFalse(DemandSurgeMilestones.ticksLabel(30).contains("50% ✓"));
    }

    @Test
    public void aleixIsAdmin() {
        assertTrue(AdminRoles.isAdminPlayerName("Aleix"));
        assertTrue(AdminRoles.isAdminPlayerName(" aleix "));
        assertFalse(AdminRoles.isAdminPlayerName("Maria"));
        assertEquals("aleix", AdminRoles.uniqueNameDocId());
    }
}
