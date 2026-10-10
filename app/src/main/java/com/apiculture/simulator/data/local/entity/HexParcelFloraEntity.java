package com.apiculture.simulator.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;

/**
 * Una fila por tipo de flora en un terreno: puede estar en crecimiento hasta {@link #readyAtEpochMs}.
 */
@Entity(tableName = "hex_parcel_flora", primaryKeys = {"hexId", "floraKey", "siteId"})
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

    /**
     * Día (yyyymmdd) desde el que cuenta el año hasta el próximo mantenimiento del árbol. 0 = desde la plantación.
     * Las filas antiguas guardan aquí solo un año (&lt; 10000) y también cuentan desde la plantación.
     */
    public int lastMaintainedYear;

    /** Apiario que sembró el cultivo. Vacío en la flora silvestre, que es del hexágono. */
    @NonNull
    public String siteId = "";

    /** Año civil en que ya se vendió el fruto a Pep. 0 = nunca. */
    public int fruitSoldYear;
}
