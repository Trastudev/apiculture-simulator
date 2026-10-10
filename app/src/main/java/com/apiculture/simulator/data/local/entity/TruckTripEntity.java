package com.apiculture.simulator.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "truck_trips")
public class TruckTripEntity {

    @PrimaryKey
    @NonNull
    public String hiveId = "";

    public String ownerId;

    public double originLat;
    public double originLng;
    public double destLat;
    public double destLng;
    public String destHexId;

    /** Flora que pecoreará al llegar. Vacío = no cambia la miel. */
    public String destFlora;

    /** Viaje de una división con el apiario lleno: no se puede cancelar. */
    public static final String SPLIT_MOVE = "split-move";

    public long startEpochMs;
    public long durationMs;

    /** Polyline OSM (precisión 5). Vacío = línea geodésica origen-destino. */
    public String routePolyline;

    /** Un carácter por arista de la polyline: A/N/C/O. */
    public String routeRoadKinds;
}
