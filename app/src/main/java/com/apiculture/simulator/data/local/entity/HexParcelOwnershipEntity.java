package com.apiculture.simulator.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;

@Entity(tableName = "hex_parcel_ownership", primaryKeys = {"hexId", "ownerId", "siteId"})
public class HexParcelOwnershipEntity {
    @NonNull
    public String hexId;
    @NonNull
    public String ownerId;
    /** Distingue varios apiarios del mismo jugador en el mismo hex. */
    @NonNull
    public String siteId = "default";
    /** Nombre elegido por el jugador ({@code null} en datos antiguos hasta migración/lectura). */
    public String parcelName;
    public int forageDayKey;
    public String forageSnapshotJson;
    /** Terreno principal / almacén (marrón en el mapa). */
    public boolean isPrimary;
    /** Edificio de almacén comprado (300 B). */
    public boolean hasWarehouse;
    /** Nivel del almacén: mismos umbrales de miel que el jugador. */
    public int warehouseLevel;
    /** Pin del apiario (0 = sin fijar: se usa un punto aleatorio estable). */
    public double siteLat;
    public double siteLng;
    public double warehouseLat;
    public double warehouseLng;
}
