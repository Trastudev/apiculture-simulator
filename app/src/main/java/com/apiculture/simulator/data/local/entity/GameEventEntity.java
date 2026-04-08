package com.apiculture.simulator.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "events")
public class GameEventEntity {
    @PrimaryKey
    @NonNull
    public String id;
    public String hiveId;
    public String type;
    public String description;
    public long timestamp;
    public int impactValue;
}
