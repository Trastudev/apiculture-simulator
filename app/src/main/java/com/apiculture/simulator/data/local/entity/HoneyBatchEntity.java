package com.apiculture.simulator.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "honey_batches")
public class HoneyBatchEntity {
    @PrimaryKey
    @NonNull
    public String id;
    public String hiveId;
    public String type;
    public double quantityKg;
    public double unitPrice;
    public long createdAt;
}
