package com.apiculture.simulator.domain.game;

import com.apiculture.simulator.data.local.entity.HiveEntity;

/**
 * Alimentación activa: baja el consumo de miel; no altera pecoreo ni puesta.
 */
public final class HiveFeedingBonuses {

    private HiveFeedingBonuses() {
    }

    public static boolean isFeedingActive(HiveEntity h, int dayKey) {
        return feedingDaysRemaining(h, dayKey) > 0;
    }

    /** Días de alimentación restantes desde {@code todayKey} (0 si no hay). */
    public static int feedingDaysRemaining(HiveEntity h, int todayKey) {
        if (h == null || h.feedHoneyBonusEndDayKeyExclusive <= 0) {
            return 0;
        }
        return Math.max(0, h.feedHoneyBonusEndDayKeyExclusive - todayKey);
    }

    public static double consumptionMultiplierForDay(HiveEntity h, int dayKey) {
        return isFeedingActive(h, dayKey) ? HiveCareRules.FEED_CONSUMPTION_MULTIPLIER : 1.0;
    }

    /** Ya no hay bono de pecoreo; se mantiene por compatibilidad con llamadas existentes. */
    public static double honeyMultiplierForDay(HiveEntity h, int dayKey) {
        return 1.0;
    }

    public static double broodMultiplierForDay(HiveEntity h, int dayKey) {
        if (h == null || h.feedBroodBonusEndDayKeyExclusive <= 0) {
            return 1.0;
        }
        if (dayKey >= h.feedBroodBonusEndDayKeyExclusive) {
            return 1.0;
        }
        double m = h.feedBroodBonusMultiplier;
        return m > 1.0001 ? m : 1.0;
    }

    public static int honeyBonusDaysRemaining(HiveEntity h, int todayKey) {
        return feedingDaysRemaining(h, todayKey);
    }

    public static int broodBonusDaysRemaining(HiveEntity h, int todayKey) {
        if (h == null || h.feedBroodBonusEndDayKeyExclusive <= 0) {
            return 0;
        }
        int rem = h.feedBroodBonusEndDayKeyExclusive - todayKey;
        return Math.max(0, rem);
    }
}
