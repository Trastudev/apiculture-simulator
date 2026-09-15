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
    /** Consumo de miel (kg) ese día; 0 en filas antiguas. */
    public double consumptionKg;
    /** Pecoreo bruto (kg) ese día; 0 en filas antiguas (se reconstruye como neto + consumo). */
    public double forageKg;
}
