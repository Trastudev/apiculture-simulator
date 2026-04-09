package com.apiculture.simulator.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "game_production_state")
public class GameProductionStateEntity {

    @PrimaryKey
    @NonNull
    public String ownerId;
    /** Primer día de juego (día 0): producción y gráfica cuentan desde aquí. */
    public int gameStartDayKey;
    /**
     * Último día de simulación (fecha civil del día de juego) para el que ya se aplicó el tick.
     * 0 = ninguno aún (el primer día elegible es {@link #gameStartDayKey}).
     */
    public int lastProcessedProductionDayKey;
    /**
     * Legado / sincronización opcional; el tick de producción usa calendario civil y las 8:00 locales.
     */
    public long gameRealTimeAnchorEpochMs;
}
