package com.apiculture.simulator.domain.game;

import androidx.annotation.Nullable;

/**
 * Cielo del día para pecoreo. Si hay observación Open-Meteo (lluvia, código WMO, viento)
 * se usa esa; si no, un modelo determinista por altitud.
 */
public enum DailySkyCondition {
    SUN(1.25, "☀️"),
    VARIABLE(1.1, "⛅"),
    CLOUDY(0.85, "☁️"),
    WINDY(0.7, "💨"),
    RAINY(0.0, "🌧️");

    private final double productionMultiplier;
    private final String emoji;

    DailySkyCondition(double productionMultiplier, String emoji) {
        this.productionMultiplier = productionMultiplier;
        this.emoji = emoji;
    }

    public double productionMultiplier() {
        switch (this) {
            case SUN:
                return GameBalanceConfig.skyMultSun;
            case VARIABLE:
                return GameBalanceConfig.skyMultVariable;
            case CLOUDY:
                return GameBalanceConfig.skyMultCloudy;
            case WINDY:
                return GameBalanceConfig.skyMultWindy;
            case RAINY:
                return GameBalanceConfig.skyMultRain;
            default:
                return productionMultiplier;
        }
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
        if (elevM <= GameBalanceConfig.skyLowElevMaxM) {
            sunEnd = GameBalanceConfig.skyLowSunEnd;
            cloudEnd = GameBalanceConfig.skyLowCloudEnd;
            windEnd = GameBalanceConfig.skyLowWindEnd;
        } else if (elevM <= GameBalanceConfig.skyMidElevMaxM) {
            sunEnd = GameBalanceConfig.skyMidSunEnd;
            cloudEnd = GameBalanceConfig.skyMidCloudEnd;
            windEnd = GameBalanceConfig.skyMidWindEnd;
        } else {
            sunEnd = GameBalanceConfig.skyHighSunEnd;
            cloudEnd = GameBalanceConfig.skyHighCloudEnd;
            windEnd = GameBalanceConfig.skyHighWindEnd;
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

    /**
     * Cielo observado (Open-Meteo). {@code null} si no hay datos: el llamador usa el modelo por altitud.
     */
    @Nullable
    public static DailySkyCondition fromObserved(@Nullable DailyWeather w) {
        if (w == null || !w.hasSkyObservation()) {
            return null;
        }
        double rain = w.precipitationMm != null ? w.precipitationMm : 0.0;
        double wind = w.windMaxKmh != null ? w.windMaxKmh : 0.0;
        if (rain >= 5.0) {
            return RAINY;
        }
        if (wind >= 40.0) {
            return WINDY;
        }
        if (w.coveredDaylightHours >= 0) {
            int covered = w.coveredDaylightHours;
            if (covered <= 4) {
                return SUN;
            }
            if (covered <= 8) {
                return VARIABLE;
            }
            return CLOUDY;
        }
        if (w.weatherCode != null && w.weatherCode <= 1) {
            return SUN;
        }
        return VARIABLE;
    }

    public static DailySkyCondition forHiveDay(
            @Nullable String skyKey, int dayKey, int elevM, @Nullable DailyWeather observed) {
        DailySkyCondition fromApi = fromObserved(observed);
        if (fromApi != null) {
            return fromApi;
        }
        return forHexElevationAndDay(skyKey, dayKey, elevM);
    }
}
