package com.apiculture.simulator.domain.game;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ColonyGameRulesTest {

    @Test
    public void swarmRiskIsZeroAtSixtyThousandAdults() {
        assertEquals(0.0, ColonyGameRules.swarmRiskForAdultWorkers(60_000), 1e-12);
        assertEquals(0.0, ColonyGameRules.swarmRiskForAdultWorkers(0), 1e-12);
    }

    @Test
    public void swarmRiskIsThirtyFivePercentAtEightyThousandAdults() {
        assertEquals(0.35, ColonyGameRules.swarmRiskForAdultWorkers(80_000), 1e-12);
        assertEquals(0.35, ColonyGameRules.swarmRiskForAdultWorkers(90_000), 1e-12);
    }

    @Test
    public void swarmRiskRampsLinearlyBetweenSixtyAndEightyThousand() {
        assertEquals(0.175, ColonyGameRules.swarmRiskForAdultWorkers(70_000), 1e-12);
        assertEquals(0.0875, ColonyGameRules.swarmRiskForAdultWorkers(65_000), 1e-12);
    }
}
