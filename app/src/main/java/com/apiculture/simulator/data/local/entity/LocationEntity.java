package com.apiculture.simulator.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "locations")
public class LocationEntity {
    @PrimaryKey
    @NonNull
    public String id;
    public String label;
    public double lat;
    public double lng;
    public String floraType;
    public boolean virtualized;
}
