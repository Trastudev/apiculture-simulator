package com.apiculture.simulator.domain.health;

import androidx.annotation.Nullable;

import com.apiculture.simulator.data.local.entity.HiveEntity;
import com.apiculture.simulator.domain.game.GameCalendar;
import com.apiculture.simulator.domain.game.HexNectarRules;
import com.apiculture.simulator.domain.population.HivePopulationState;

import java.time.LocalDate;
import java.util.List;

/** Utilidades para el circulito rojo de avisos en listas / menú / prado. */
public final class HiveAlertBadge {

    private HiveAlertBadge() {
    }

    public static boolean needsAttention(@Nullable HiveEntity hive) {
        if (hive == null) {
            return false;
        }
        HivePopulationState pop = HivePopulationState.fromHiveEntityOrDefault(hive, Math.max(0, hive.beeCount));
        LocalDate day = LocalDate.now(GameCalendar.userTimeZone());
        if (hive.lastSummaryDayKey > 0) {
            day = GameCalendar.fromDayKey(hive.lastSummaryDayKey);
        }
        double forage01 = HexNectarRules.nectar01(hive, day, 1, null);
        return HiveHealthAlerts.needsAttention(hive, pop, forage01);
    }

    public static boolean anyNeedsAttention(@Nullable List<HiveEntity> hives) {
        if (hives == null || hives.isEmpty()) {
            return false;
        }
        for (HiveEntity h : hives) {
            if (needsAttention(h)) {
                return true;
            }
        }
        return false;
    }
}
