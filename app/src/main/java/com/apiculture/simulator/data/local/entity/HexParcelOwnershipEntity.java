package com.apiculture.simulator.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "hex_parcel_ownership")
public class HexParcelOwnershipEntity {
    @PrimaryKey
    @NonNull
    public String hexId;
    @NonNull
    public String ownerId;
    /** Nombre elegido por el jugador ({@code null} en datos antiguos hasta migración/lectura). */
    public String parcelName;
}
