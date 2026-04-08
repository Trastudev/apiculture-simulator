package com.apiculture.simulator.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;

@Entity(tableName = "hive_daily_yield", primaryKeys = {"hiveId", "dayKey"})
public class HiveDailyYieldEntity {

    @NonNull
    public String hiveId;
    public int dayKey;
    public double kg;
    /** Nuevas obreras − muertes de obreras ese día (persistido para gráficos). */
    public int workerNetDelta;
    public int eggsLaid;
}
