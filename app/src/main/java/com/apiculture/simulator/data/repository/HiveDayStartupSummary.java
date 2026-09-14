package com.apiculture.simulator.data.repository;

/**
 * Cifras del último tick mostradas al abrir la app (resumen del día simulado).
 */
public final class HiveDayStartupSummary {

    public final String hiveName;
    public final double honeyKg;
    /** Tipo de miel / flora de la colmena ese día. */
    public final String floraType;
    /** Nuevas obreras − muertes de obreras adultas. */
    public final int workerNet;
    public final int eggsLaid;
    public final int healthDelta;
    public final double varroaDelta;
    public final boolean swarmed;
    public final boolean queenMissing;
    public final boolean velutinaHit;

    public HiveDayStartupSummary(String hiveName, double honeyKg, String floraType, int workerNet,
            int eggsLaid, int healthDelta, double varroaDelta, boolean swarmed,
            boolean queenMissing, boolean velutinaHit) {
        this.hiveName = hiveName != null ? hiveName : "";
        this.honeyKg = honeyKg;
        this.floraType = floraType != null && !floraType.isEmpty() ? floraType : "Mil flores";
        this.workerNet = workerNet;
        this.eggsLaid = eggsLaid;
        this.healthDelta = healthDelta;
        this.varroaDelta = varroaDelta;
        this.swarmed = swarmed;
        this.queenMissing = queenMissing;
        this.velutinaHit = velutinaHit;
    }
}
