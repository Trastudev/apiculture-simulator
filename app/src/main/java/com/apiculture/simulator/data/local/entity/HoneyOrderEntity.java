package com.apiculture.simulator.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "honey_orders")
public class HoneyOrderEntity {
    @PrimaryKey
    @NonNull
    public String id = "";
    public String npcName;
    public int portraitIndex;
    public String floraKey;
    public double kg;
    public double unitPrice;
    public String destHexId;
    public double destLat;
    public double destLng;
    public String destLabel;
    public String region;
    public int createdDayKey;
    public long expireEpochMs;
    public boolean taken;
    public String claimedBy;
    public int band;
    /** Envase pedido; vacío en comandas antiguas, que van a granel. */
    public String format;
}
