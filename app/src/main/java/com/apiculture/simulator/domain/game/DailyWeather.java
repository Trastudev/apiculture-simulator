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
    /** Cobertura nubosa media del día, 0–100. */
    @Nullable
    public Double cloudCoverPct;
    /** Horas de 8 a 20 con código 2, 3, niebla o precipitación. */
    public int coveredDaylightHours = -1;

    public boolean hasSkyObservation() {
        return weatherCode != null
                || cloudCoverPct != null
                || (precipitationMm != null && precipitationMm > 0.05)
                || (windMaxKmh != null && windMaxKmh >= 35);
    }
}
