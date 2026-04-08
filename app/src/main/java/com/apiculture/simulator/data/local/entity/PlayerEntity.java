package com.apiculture.simulator.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "players")
public class PlayerEntity {
    @PrimaryKey
    @NonNull
    public String id;
    public String nickname;
    public double balance;
    public double totalHoneyKg;
}
