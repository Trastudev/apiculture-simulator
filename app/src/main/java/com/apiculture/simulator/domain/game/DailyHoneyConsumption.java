package com.apiculture.simulator.domain.game;

/**
 * Consumo diario de miel. El valor de juego lo calcula {@link HiveDailyBiology#consumptionKg}
 * (nido + adultas + cría; el jarabe reduce el consumo).
 */
public final class DailyHoneyConsumption {

    private DailyHoneyConsumption() {
    }

    /**
     * @deprecated Usar {@link HiveDailyBiology#netHoneyKg}.
     */
    @Deprecated
    public static double netKgAfterConsumption(double grossKg, int beeCount) {
        if (beeCount <= 0) {
            return grossKg;
        }
        double consumption = 0.03 + beeCount * 6.5e-6;
        return grossKg - consumption;
    }
}
