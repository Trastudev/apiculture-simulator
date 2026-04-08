package com.apiculture.simulator.data.repository;

/**
 * Cifras del último tick mostradas al abrir la app (resumen del día simulado).
 */
public final class HiveDayStartupSummary {

    public final String hiveName;
    public final double honeyKg;
    /** Nuevas obreras − muertes de obreras adultas. */
    public final int workerNet;
    public final int eggsLaid;
    public final int healthDelta;
    public final double varroaDelta;
    public final boolean swarmed;

    public HiveDayStartupSummary(String hiveName, double honeyKg, int workerNet, int eggsLaid,
            int healthDelta, double varroaDelta, boolean swarmed) {
        this.hiveName = hiveName != null ? hiveName : "";
        this.honeyKg = honeyKg;
        this.workerNet = workerNet;
        this.eggsLaid = eggsLaid;
        this.healthDelta = healthDelta;
        this.varroaDelta = varroaDelta;
        this.swarmed = swarmed;
    }
}
