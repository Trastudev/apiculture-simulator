package com.apiculture.simulator.domain.game;

import androidx.annotation.Nullable;

/**
 * Observación diaria Open-Meteo en el punto de la colmena (día D−1 al liquidar).
 */
public final class DailyWeather {

    @Nullable
    public Double meanTempC;
    @Nullable
    public Double precipitationMm;
    @Nullable
    public Integer weatherCode;
    @Nullable
    public Double windMaxKmh;

    public boolean hasSkyObservation() {
        return weatherCode != null
                || (precipitationMm != null && precipitationMm > 0.05)
                || (windMaxKmh != null && windMaxKmh >= 35);
    }
}
