package com.apiculture.simulator.presentation.hive;

import com.apiculture.simulator.data.local.entity.HiveEntity;
import com.apiculture.simulator.domain.game.GameCalendar;
import com.apiculture.simulator.domain.game.TranshumanceRules;

import org.junit.Assert;
import org.junit.Test;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;

public class PendingContractMoveUiTest {

    @Test
    public void travelDayLabel_todayAndFuture() {
        LocalDate today = LocalDate.of(2026, 9, 18);
        int todayKey = GameCalendar.toDayKey(today);
        int laterKey = GameCalendar.toDayKey(today.plusDays(3));
        Assert.assertEquals("hoy", PendingContractMoveUi.travelDayLabel(todayKey, today));
        Assert.assertTrue(PendingContractMoveUi.travelDayLabel(laterKey, today).startsWith("el "));
        Assert.assertEquals("—", PendingContractMoveUi.travelDayLabel(0, today));
    }

    @Test
    public void pendingToDest_filtersScheduledHives() {
        HiveEntity going = hive("a", "hex_src", "hex_dest", 20260921);
        HiveEntity staying = hive("b", "hex_src", null, 0);
        HiveEntity other = hive("c", "hex_src", "hex_other", 20260921);
        Assert.assertEquals(1, PendingContractMoveUi.pendingToDest(
                Arrays.asList(going, staying, other), "hex_dest").size());
        Assert.assertTrue(PendingContractMoveUi.pendingFromHex(
                Collections.singletonList(going), "hex_src").contains(going));
        Assert.assertTrue(TranshumanceRules.hasPendingContractMove(going));
        Assert.assertFalse(TranshumanceRules.hasPendingContractMove(staying));
    }

    @Test
    public void formatDepartRemaining_usesDaysAndHours() {
        Assert.assertEquals("2 d 3 h", PendingContractMoveUi.formatDepartRemaining(
                (2L * 24 + 3) * 3600_000L + 12_000L));
        Assert.assertTrue(PendingContractMoveUi.formatDepartRemaining(90_000L).contains("min"));
    }

    @Test
    public void joinHiveNames_truncatesAfterFour() {
        HiveEntity a = hive("1", "s", "d", 1);
        a.name = "Núcleo 1";
        HiveEntity b = hive("2", "s", "d", 1);
        b.name = "Núcleo 2";
        HiveEntity c = hive("3", "s", "d", 1);
        c.name = "Núcleo 3";
        HiveEntity d = hive("4", "s", "d", 1);
        d.name = "Núcleo 4";
        HiveEntity e = hive("5", "s", "d", 1);
        e.name = "Núcleo 5";
        String names = PendingContractMoveUi.joinHiveNames(Arrays.asList(a, b, c, d, e));
        Assert.assertTrue(names.contains("Núcleo 1"));
        Assert.assertTrue(names.contains("Núcleo 4"));
        Assert.assertTrue(names.contains("1 más"));
        Assert.assertFalse(names.contains("Núcleo 5"));
    }

    private static HiveEntity hive(String id, String hex, String dest, int dayKey) {
        HiveEntity h = new HiveEntity();
        h.id = id;
        h.hexId = hex;
        h.pendingContractHexId = dest;
        h.pendingContractDayKey = dayKey;
        return h;
    }
}
