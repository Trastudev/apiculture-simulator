package com.apiculture.simulator.domain.game;

/**
 * Alimentación de pago: reduce el consumo de miel de la colonia mientras dura.
 */
public enum HiveFeedType {
    DAYS_1(1, HiveCareRules.FEED_1_DAY_EUR),
    DAYS_7(7, HiveCareRules.FEED_7_DAYS_EUR);

    public final int durationDays;
    public final double costEur;

    HiveFeedType(int durationDays, double costEur) {
        this.durationDays = durationDays;
        this.costEur = costEur;
    }
}
