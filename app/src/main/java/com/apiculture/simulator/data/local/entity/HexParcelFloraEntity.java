package com.apiculture.simulator.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;

/**
 * Una fila por tipo de flora en un terreno: puede estar en crecimiento hasta {@link #readyAtEpochMs}.
 */
@Entity(tableName = "hex_parcel_flora", primaryKeys = {"hexId", "floraKey"})
public class HexParcelFloraEntity {

    @NonNull
    public String hexId;

    @NonNull
    public String floraKey;

    /** Marca de inicio de siembra. */
    public long plantedAtEpochMs;

    /** Cuando {@code System.currentTimeMillis() >= readyAtEpochMs}, la flora está disponible. */
    public long readyAtEpochMs;

    /** Día civil (yyyymmdd) en que se retira un cultivo anual. 0 = no caduca. */
    public int expireAtDayKey;

    /** Último año de calendario en que se pagó el mantenimiento del árbol. 0 = nunca. */
    public int lastMaintainedYear;
}
