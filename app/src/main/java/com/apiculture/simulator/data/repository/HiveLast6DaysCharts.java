package com.apiculture.simulator.data.repository;

/**
 * Serie de los últimos 6 días de calendario incluyendo hoy (índice 0 = más antiguo).
 */
public final class HiveLast6DaysCharts {

    public final double[] honeyKg;
    public final int[] workerNetDelta;
    public final int[] eggsLaid;

    public HiveLast6DaysCharts(double[] honeyKg, int[] workerNetDelta, int[] eggsLaid) {
        this.honeyKg = honeyKg != null ? honeyKg : new double[6];
        this.workerNetDelta = workerNetDelta != null ? workerNetDelta : new int[6];
        this.eggsLaid = eggsLaid != null ? eggsLaid : new int[6];
    }
}
