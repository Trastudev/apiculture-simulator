package com.apiculture.simulator.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "cargo_trips")
public class CargoTripEntity {

    public static final String KIND_COLLECT = "collect";
    public static final String KIND_WHOLESALE = "wholesale";
    public static final String KIND_ORDER = "order";
    public static final String KIND_TRANSFER = "transfer";
    public static final String KIND_DELIVERY = "delivery";

    public static final String LEG_HAUL_TRUCK = "haul-truck";
    public static final String LEG_HAUL_SHIP = "haul-ship";
    public static final String LEG_PICKUP = "pickup";
    public static final String LEG_DELIVER = "deliver";
    public static final String LEG_TRANSFER = "transfer";

    public static final String PHASE_OUT = "out";
    public static final String PHASE_RETURN = "return";

    @PrimaryKey
    @NonNull
    public String id = "";

    public String ownerId;
    public String kind;
    public String phase;

    public String floraKey;
    public double kg;
    public String cargoJson;

    public double originLat;
    public double originLng;
    public double destLat;
    public double destLng;
    public String originLabel;
    public String destLabel;
    public String originHexId;
    public String destHexId;

    public double returnLat;
    public double returnLng;
    public String returnLabel;
    public String returnHexId;

    public double unitPrice;
    public String npcName;
    public String hiveId;

    public long startEpochMs;
    public long durationMs;
    public String routePolyline;
    public String routeRoadKinds;
    public String orderId;

    /** haul-truck, haul-ship, pickup, deliver, transfer. Vacío: viaje antiguo. */
    public String legRole;
    public String vehicleId;
    public String shipmentId;
    public double chainLat;
    public double chainLng;
    public String chainLabel;
    public String chainHexId;
    /** 1 = no recalcular el precio al cobrar (miel foránea ya pactada). */
    public int priceLocked;
}
