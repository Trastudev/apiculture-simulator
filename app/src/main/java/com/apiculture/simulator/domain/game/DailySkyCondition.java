package com.apiculture.simulator.domain.game;

import androidx.annotation.Nullable;

/**
 * Clima simulado por día (determinista), en función de la <strong>altitud en el emplazamiento de la colmena</strong>
 * (m s.n.m., típicamente Open-Meteo al crear o tras transhumancia):
 * <ul>
 *   <li>0–800 m: soleado 80 %, nublado 10 %, viento 5 %, lluvia 5 %</li>
 *   <li>801–1500 m: soleado 65 %, nublado 15 %, viento 10 %, lluvia 10 %</li>
 *   <li>&gt; 1500 m: soleado 50 %, nublado 25 %, viento 12,5 %, lluvia 12,5 %</li>
 * </ul>
 * Multiplicadores de producción: soleado ×1; nublado ×0,7; viento ×0,5; lluvia ×0.
 * <p>
 * {@link #forOwnerAndDay} conserva la leyenda anterior (70/15/10/5 %) solo para compatibilidad puntual.
 */
public enum DailySkyCondition {
    SUN(1.0, "☀️"),
    CLOUDY(0.7, "☁️"),
    WINDY(0.5, "💨"),
    RAINY(0.0, "🌧️");

    private final double productionMultiplier;
    private final String emoji;

    DailySkyCondition(double productionMultiplier, String emoji) {
        this.productionMultiplier = productionMultiplier;
        this.emoji = emoji;
    }

    public double productionMultiplier() {
        return productionMultiplier;
    }

    public String emoji() {
        return emoji;
    }

    /**
     * Clima por hex (o clave estable) y día: mismo resultado en todos los dispositivos.
     *
     * @param skyKey     clave estable por colmena, p. ej. {@code hive:} + id
     * @param dayKey     día de calendario del juego
     * @param elevM      altitud del punto de la colmena en metros
     */
    public static DailySkyCondition forHexElevationAndDay(
            @Nullable String skyKey, int dayKey, int elevM) {
        String key = "skyHex:" + (skyKey != null ? skyKey : "");
        double u = HoneyDailyProduction.deterministicUniform01(key, dayKey);
        double sunEnd;
        double cloudEnd;
        double windEnd;
        if (elevM <= 800) {
            sunEnd = 0.80;
            cloudEnd = 0.90;
            windEnd = 0.95;
        } else if (elevM <= 1500) {
            sunEnd = 0.65;
            cloudEnd = 0.80;
            windEnd = 0.90;
        } else {
            sunEnd = 0.50;
            cloudEnd = 0.75;
            windEnd = 0.875;
        }
        if (u < sunEnd) {
            return SUN;
        }
        if (u < cloudEnd) {
            return CLOUDY;
        }
        if (u < windEnd) {
            return WINDY;
        }
        return RAINY;
    }

    /**
     * Leyenda legada 70 / 15 / 10 / 5 % por jugador y día (una sola serie para todas las colmenas).
     */
    public static DailySkyCondition forOwnerAndDay(String ownerId, int dayKey) {
        String key = "sky:" + (ownerId != null ? ownerId : "");
        double u = HoneyDailyProduction.deterministicUniform01(key, dayKey);
        if (u < 0.70) {
            return SUN;
        }
        if (u < 0.85) {
            return CLOUDY;
        }
        if (u < 0.95) {
            return WINDY;
        }
        return RAINY;
    }
}
