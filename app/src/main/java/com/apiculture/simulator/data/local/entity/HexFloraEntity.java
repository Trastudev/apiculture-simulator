package com.apiculture.simulator.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "hex_flora")
public class HexFloraEntity {

    @PrimaryKey
    @NonNull
    public String hexId;

    @NonNull
    public String floraType;
}
