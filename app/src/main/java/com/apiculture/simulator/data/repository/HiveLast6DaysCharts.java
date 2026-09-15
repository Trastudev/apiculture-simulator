package com.apiculture.simulator.data.repository;

/**
 * Serie de los últimos 7 días de juego incluyendo el día de cierre ({@link #chartEndDayKey}).
 * Índice 0 = más antiguo.
 */
public final class HiveLast6DaysCharts {

    /** Pecoreo bruto (recolección), no el neto tras consumo. */
    public final double[] honeyKg;
    public final int[] workerNetDelta;
    public final int[] eggsLaid;
    public final double[] consumptionKg;
    /** {@code yyyymmdd} del último día de la serie (índice 6); 0 si no hay ancla. */
    public final int chartEndDayKey;

    public HiveLast6DaysCharts(double[] honeyKg, int[] workerNetDelta, int[] eggsLaid) {
        this(honeyKg, workerNetDelta, eggsLaid, null, 0);
    }

    public HiveLast6DaysCharts(double[] honeyKg, int[] workerNetDelta, int[] eggsLaid, int chartEndDayKey) {
        this(honeyKg, workerNetDelta, eggsLaid, null, chartEndDayKey);
    }

    public HiveLast6DaysCharts(double[] honeyKg, int[] workerNetDelta, int[] eggsLaid,
                               double[] consumptionKg, int chartEndDayKey) {
        this.honeyKg = honeyKg != null ? honeyKg : new double[7];
        this.workerNetDelta = workerNetDelta != null ? workerNetDelta : new int[7];
        this.eggsLaid = eggsLaid != null ? eggsLaid : new int[7];
        this.consumptionKg = consumptionKg != null ? consumptionKg : new double[7];
        this.chartEndDayKey = chartEndDayKey;
    }
}
