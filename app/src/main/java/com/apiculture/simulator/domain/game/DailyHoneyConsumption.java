package com.apiculture.simulator.domain.game;

/**
 * Consumo diario de miel en la colmena: cantidad <strong>fija por abeja y día</strong>, sin depender
 * de temperatura ni del cielo.
 */
public final class DailyHoneyConsumption {

    /**
     * kg de miel consumidos por cada abeja al día (constante de balance).
     */
    public static final double KG_CONSUMPTION_PER_BEE_PER_DAY = 1.35e-7;

    private DailyHoneyConsumption() {
    }

    /**
     * @param grossKg  producción bruta del día (base × temperatura × cielo)
     * @param beeCount número de abejas en la colmena
     * @return kg netos del día (bruta − consumo); puede ser negativo si la colonia consume más de lo
     *         producido (se resta del stock en colmena, acotado a ≥0 al persistir).
     */
    public static double netKgAfterConsumption(double grossKg, int beeCount) {
        if (beeCount <= 0) {
            return grossKg;
        }
        double consumption = beeCount * KG_CONSUMPTION_PER_BEE_PER_DAY;
        return grossKg - consumption;
    }
}
