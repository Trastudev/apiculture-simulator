package com.apiculture.simulator.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "pollination_contracts")
public class PollinationContractEntity {
    @PrimaryKey
    @NonNull
    public String id = "";
    public String ownerId;
    public String hexId;
    public String flora;
    public String npcName;
    public String estateName;
    public String region;
    public String climateZone;
    public int layer;
    public String status;
    public double collectedKg;
    public double poolKg;
    public boolean sawPeak;
    public double minPct;
    public int payB;
    public int extraBPerPoint;
    public int travelCostPaid;
    public int acceptedDayKey;
    public int workDays;
    public int dueDayKey;
    public int startDoy;
    public String hiveIdsJson;
}
