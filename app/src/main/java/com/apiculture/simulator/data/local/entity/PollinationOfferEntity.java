package com.apiculture.simulator.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "pollination_offers")
public class PollinationOfferEntity {
    @PrimaryKey
    @NonNull
    public String id = "";
    public String hexId;
    public String flora;
    public int startDoy;
    public int endDoy;
    public int band;
    public String region;
    public int createdDayKey;
    public long expireEpochMs;
    public double destLat;
    public double destLng;
    public String npcName;
    public int portraitIndex;
    public boolean taken;
}
