package com.apiculture.simulator.domain.game;

/**
 * La reina sigue poniendo con frío templado (el nido está a ~35 °C); se para
 * con helada o canícula. No usa las bandas de pecoreo: a 12–16 °C aún hay cría.
 */
public final class TemperatureLayingModifier {

    private TemperatureLayingModifier() {
    }

    public static double layingMultiplierForCelsius(Double tempCelsius) {
        if (tempCelsius == null || Double.isNaN(tempCelsius)) {
            return 1.0;
        }
        double t = tempCelsius;
        if (t > GameBalanceConfig.temperatureOverCZero) {
            return 0.0;
        }
        java.util.List<GameBalanceConfig.TempBand> bands = GameBalanceConfig.layingBands;
        if (bands != null) {
            for (GameBalanceConfig.TempBand b : bands) {
                if (t < b.belowC) {
                    return b.mult;
                }
            }
        }
        return 0.0;
    }
}
