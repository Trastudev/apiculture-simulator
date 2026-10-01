package com.apiculture.simulator.domain.game;

/** Términos de pago de un contrato de polinización. */
public final class PollinationPayTerms {
    public final double minPct;
    public final int payB;
    public final int extraBPerPoint;
    public final int workDays;
    public final double payPerDayB;
    public final int startDoy;
    public final int endDoy;
    public final double bloomMin;

    public PollinationPayTerms(double minPct, int payB, int extraBPerPoint) {
        this(minPct, payB, extraBPerPoint, 0, 0.0, 0, 0, 0.0);
    }

    public PollinationPayTerms(double minPct, int payB, int extraBPerPoint, int workDays, double payPerDayB) {
        this(minPct, payB, extraBPerPoint, workDays, payPerDayB, 0, 0, 0.0);
    }

    public PollinationPayTerms(double minPct, int payB, int extraBPerPoint, int workDays, double payPerDayB,
            int startDoy, int endDoy, double bloomMin) {
        this.minPct = minPct;
        this.payB = payB;
        this.extraBPerPoint = extraBPerPoint;
        this.workDays = Math.max(0, workDays);
        this.payPerDayB = Math.max(0.0, payPerDayB);
        this.startDoy = startDoy;
        this.endDoy = endDoy;
        this.bloomMin = Math.max(0.0, bloomMin);
    }
}
