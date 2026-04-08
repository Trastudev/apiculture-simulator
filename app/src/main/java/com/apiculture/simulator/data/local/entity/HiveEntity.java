package com.apiculture.simulator.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "hives")
public class HiveEntity {
    @PrimaryKey
    @NonNull
    public String id;
    public String ownerId;
    public String name;
    public int beeCount;
    public int health;
    public double honeyProduction;
    public int reserves;
    public int queenAgeDays;
    public int queenGeneticQuality;
    public double lat;
    public double lng;
    /**
     * Terreno padre (hexágono Iberia, {@link com.apiculture.simulator.domain.parcel.HexParcel#id}).
     * La transhumancia a otro hex tuyo actualiza este id y la flora del hex destino.
     */
    public String hexId;
    /**
     * Altitud (m s.n.m.) en las coordenadas de la colmena, obtenida con Open-Meteo al crear o tras transhumancia.
     * {@code -1} = pendiente (legado o error); la producción diaria la rellena con API o {@link com.apiculture.simulator.data.remote.OpenMeteoElevation#FALLBACK_METERS}.
     */
    public int elevationMeters = -1;
    public String floraType;
    /** Alzas de ampliación (0–2): limitan el máximo de miel almacenable en colmena. */
    public int superCount;
    /** JSON {@link com.apiculture.simulator.domain.population.HivePopulationState}; null en datos antiguos hasta primera sincronización. */
    public String populationStateJson;
    /** Infestación de varroa (%). */
    public double varroaPct;
    /** Días restantes de tratamiento antivarroa (0 = inactivo). */
    public int varroaTreatmentDaysRemaining;
    /** Días de rebote +0,2 %/día tras fin de tratamiento. */
    public int varroaReboundDaysRemaining;
    /** Último {@code dayKey} aplicado por {@link com.apiculture.simulator.domain.health.HiveDailyHealthSimulator}. */
    public int lastHealthSimDayKey;
    /**
     * Resumen del último tick diario aplicado a esta colmena ({@code dayKey} del calendario del juego).
     * Salud/varroa: diferencia respecto al inicio del día. {@link #lastSummaryDeltaBees}: solo emergencias.
     */
    public int lastSummaryDayKey;
    public double lastSummaryHoneyKg;
    /** Nuevas obreras ese día (emergencias pupa → adulta), no huevos ni total de colonia. */
    public int lastSummaryDeltaBees;
    public int lastSummaryDeltaHealth;
    public double lastSummaryDeltaVarroa;
    /** Desglose del tick de población (solo si hubo simulación ese día). */
    public int lastSummaryWorkerDeaths;
    public int lastSummaryWorkerEmergences;
    public int lastSummaryEggsLaid;
    /** Si el último tick aplicó enjambrazón (resumen / diálogo de inicio). */
    public boolean lastSummarySwarmed;
}
