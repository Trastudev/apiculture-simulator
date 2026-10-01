package com.apiculture.simulator.domain.health;

import com.apiculture.simulator.data.local.entity.HiveEntity;
import com.apiculture.simulator.domain.game.GameBalanceConfig;
import com.apiculture.simulator.domain.game.HiveHoneyRules;
import com.apiculture.simulator.domain.population.HivePopulationState;
import com.apiculture.simulator.domain.population.QueenMode;

import org.junit.Assert;
import org.junit.Test;

import java.util.List;

public class HiveHealthAlertsTest {

    @Test
    public void floraBelowTwentyPercentAddsTag() {
        HiveEntity h = baseHive();
        h.health = 100;
        h.varroaPct = 0;
        h.honeyProduction = HiveHoneyRules.MIN_HIVE_STOCK_KG + 1;
        List<HiveHealthAlerts.Tag> tags = HiveHealthAlerts.alertTags(h, null, 0.19);
        Assert.assertFalse(tags.isEmpty());
        Assert.assertTrue(tags.get(tags.size() - 1).text.contains("Flora baja"));
        Assert.assertTrue(HiveHealthAlerts.needsAttention(h, null, 0.19));
        Assert.assertFalse(HiveHealthAlerts.needsAttention(h, null, 0.20));
    }

    @Test
    public void deadQueenAddsTag() {
        HiveEntity h = baseHive();
        h.health = 100;
        h.varroaPct = 0;
        h.honeyProduction = HiveHoneyRules.MIN_HIVE_STOCK_KG + 1;
        HivePopulationState pop = HivePopulationState.fromHiveEntityOrDefault(h, 1000);
        pop.queenMode = QueenMode.ORPHANED;
        Assert.assertTrue(HiveHealthAlerts.needsAttention(h, pop, 1.0));
    }

    private static HiveEntity baseHive() {
        HiveEntity h = new HiveEntity();
        h.id = "t1";
        h.beeCount = 1000;
        h.health = 100;
        h.varroaPct = Math.max(0, GameBalanceConfig.varroaKStartPct - 1);
        h.honeyProduction = HiveHoneyRules.MIN_HIVE_STOCK_KG + 5;
        h.superCount = 1;
        return h;
    }
}
