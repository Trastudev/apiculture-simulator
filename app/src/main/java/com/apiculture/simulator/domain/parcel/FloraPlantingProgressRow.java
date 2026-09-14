package com.apiculture.simulator.domain.parcel;

import androidx.annotation.NonNull;

/** Una siembra en curso para mostrar en el dashboard (cuenta atrás + barra). */
public final class FloraPlantingProgressRow {

    public final @NonNull String hexId;
    public final @NonNull String parcelLabel;
    public final @NonNull String floraKey;
    public final long plantedAtEpochMs;
    public final long readyAtEpochMs;

    public FloraPlantingProgressRow(
            @NonNull String hexId,
            @NonNull String parcelLabel,
            @NonNull String floraKey,
            long plantedAtEpochMs,
            long readyAtEpochMs) {
        this.hexId = hexId;
        this.parcelLabel = parcelLabel;
        this.floraKey = floraKey;
        this.plantedAtEpochMs = plantedAtEpochMs;
        this.readyAtEpochMs = readyAtEpochMs;
    }
}
