package com.apiculture.simulator.domain.game;

/**
 * Frío de montaña y calor del sur: salud, capacidad de la colonia y mortalidad extra.
 * La miel ya se recorta en {@link TemperatureHoneyModifier}; esto es el motivo para transhumar.
 */
public final class TemperatureStress {

    private TemperatureStress() {
    }

    /**
     * Cambio diario de salud por temperatura media (°C). Zona cómoda ~16–28 °C (leve recuperación).
     */
    public static double dailyHealthDelta(Double tempCelsius) {
        if (tempCelsius == null || Double.isNaN(tempCelsius)) {
            return 0.0;
        }
        double t = tempCelsius;
        if (t < -5.0) {
            return -4.5;
        }
        if (t < 0.0) {
            return -3.6;
        }
        if (t < 4.0) {
            return -2.8;
        }
        if (t < 8.0) {
            return -0.35;
        }
        if (t < 12.0) {
            return -0.1;
        }
        if (t < 16.0) {
            return 0.0;
        }
        if (t <= 28.0) {
            return 0.45;
        }
        if (t < 32.0) {
            return -0.4;
        }
        if (t < 36.0) {
            return -1.5;
        }
        if (t < 40.0) {
            return -2.8;
        }
        if (t < 45.0) {
            return -3.8;
        }
        return -4.5;
    }

    /** Multiplicador de capacidad de carga de obreras (invierno helado / canícula). */
    public static double carryingCapacityMultiplier(Double tempCelsius) {
        if (tempCelsius == null || Double.isNaN(tempCelsius)) {
            return 1.0;
        }
        double t = tempCelsius;
        if (t < 0.0) {
            return 0.22;
        }
        if (t < 5.0) {
            return 0.42;
        }
        if (t < 10.0) {
            return 0.72;
        }
        if (t < 14.0) {
            return 0.90;
        }
        if (t <= 32.0) {
            return 1.0;
        }
        if (t < 36.0) {
            return 0.70;
        }
        if (t < 40.0) {
            return 0.42;
        }
        return 0.22;
    }

    /**
     * Multiplicador de muertes naturales diarias. Salud baja y temperaturas extremas aceleran la mortalidad.
     */
    public static double mortalityMultiplier(int health, Double tempCelsius) {
        int h = Math.max(0, Math.min(100, health));
        double fromHealth = 1.0 + (100 - h) / 100.0 * 1.15;
        double fromTemp = 1.0;
        if (tempCelsius != null && !Double.isNaN(tempCelsius)) {
            double t = tempCelsius;
            if (t < 8.0) {
                fromTemp += Math.min(1.4, (8.0 - t) / 8.0 * 1.1);
            } else if (t > 34.0) {
                fromTemp += Math.min(1.6, (t - 34.0) / 8.0 * 1.2);
            }
        }
        return fromHealth * fromTemp;
    }
}
