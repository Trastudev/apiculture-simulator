package com.apiculture.simulator.domain.game;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ColonyGameRulesTest {

    @Test
    public void swarmRiskIsZeroUntilNextThousandAboveFortyK() {
        assertEquals(0.0, ColonyGameRules.swarmRiskForAdultWorkers(40_000), 1e-12);
        assertEquals(0.0, ColonyGameRules.swarmRiskForAdultWorkers(40_999), 1e-12);
        assertEquals(0.0, ColonyGameRules.swarmRiskForAdultWorkers(0), 1e-12);
    }

    @Test
    public void swarmRiskAddsOneStepPerThousandAdults() {
        double step = 0.35 / 12.0;
        assertEquals(step, ColonyGameRules.swarmRiskForAdultWorkers(41_000), 1e-12);
        assertEquals(step, ColonyGameRules.swarmRiskForAdultWorkers(41_999), 1e-12);
        assertEquals(2 * step, ColonyGameRules.swarmRiskForAdultWorkers(42_000), 1e-12);
        assertEquals(0.175, ColonyGameRules.swarmRiskForAdultWorkers(46_000), 1e-12);
        assertEquals(10 * step, ColonyGameRules.swarmRiskForAdultWorkers(50_000), 1e-12);
    }

    @Test
    public void swarmRiskCapsAtThirtyFivePercentFromFiftyTwoThousand() {
        assertEquals(0.35, ColonyGameRules.swarmRiskForAdultWorkers(52_000), 1e-12);
        assertEquals(0.35, ColonyGameRules.swarmRiskForAdultWorkers(80_000), 1e-12);
    }

    @Test
    public void emptyNucPriceIsOneHundredPlusFiftyPerSuper() {
        assertEquals(100, HiveCareRules.emptyNucPriceEuros(0));
        assertEquals(150, HiveCareRules.emptyNucPriceEuros(1));
        assertEquals(200, HiveCareRules.emptyNucPriceEuros(2));
        assertEquals(200, HiveCareRules.emptyNucPriceEuros(9));
    }
}
