package com.apiculture.simulator.domain.game;

import androidx.annotation.Nullable;

/**
 * Cielo del día para pecoreo. Si hay observación Open-Meteo (lluvia, código WMO, viento)
 * se usa esa; si no, un modelo determinista por altitud.
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
        switch (this) {
            case SUN:
                return GameBalanceConfig.skyMultSun;
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
        Integer code = w.weatherCode;
        if (code != null) {
            int c = code;
            if ((c >= 61 && c <= 67) || (c >= 80 && c <= 82) || (c >= 95 && c <= 99)
                    || (c >= 71 && c <= 77)) {
                return RAINY;
            }
            if (c >= 51 && c <= 57) {
                return rain >= 1.0 ? RAINY : CLOUDY;
            }
            if (c == 45 || c == 48) {
                return CLOUDY;
            }
            if (c == 0 || c == 1) {
                return wind >= 40.0 ? WINDY : SUN;
            }
            if (c == 2 || c == 3) {
                return wind >= 45.0 ? WINDY : CLOUDY;
            }
        }
        if (rain >= 1.5) {
            return RAINY;
        }
        if (wind >= 40.0) {
            return WINDY;
        }
        return CLOUDY;
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
