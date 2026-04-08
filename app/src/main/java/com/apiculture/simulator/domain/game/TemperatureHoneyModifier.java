package com.apiculture.simulator.domain.game;

/**
 * Ajusta la producción diaria de miel según la temperatura media (°C) del día anterior
 * en la zona de la colmena (liquidación a las 8:00 del día D con datos meteorológicos del día D−1).
 * <p>
 * “Baja X%” = se conserva el (100−X)% de la producción base. Rangos en °C (límite inferior
 * inclusive, superior exclusivo, salvo donde se indica):
 * <ul>
 *   <li>&lt; 10 → −85% → factor 0,15</li>
 *   <li>[10, 15) → −60% → 0,40</li>
 *   <li>[15, 20) → −25% → 0,75</li>
 *   <li>[20, 25) → sin reducción → 1,00</li>
 *   <li>[25, 30) → −10% → 0,90</li>
 *   <li>[30, 35) → −45% → 0,55</li>
 *   <li>[35, 40) → −75% → 0,25</li>
 *   <li>[40, 45) → −90% → 0,10</li>
 *   <li>[45, 50] → mismo que 40–45 (no especificado) → 0,10</li>
 *   <li>&gt; 50 → sin producción → 0</li>
 * </ul>
 * Si no hay temperatura disponible ({@link Double#NaN} o null), factor 1 (sin cambio).
 */
public final class TemperatureHoneyModifier {

    private TemperatureHoneyModifier() {
    }

    /**
     * Multiplicador [0, 1] sobre la producción ya calculada por abejas y día.
     */
    public static double productionMultiplierForCelsius(Double tempCelsius) {
        if (tempCelsius == null || Double.isNaN(tempCelsius)) {
            return 1.0;
        }
        double t = tempCelsius;
        if (t > 50) {
            return 0.0;
        }
        if (t < 10) {
            return 0.15;
        }
        if (t < 15) {
            return 0.40;
        }
        if (t < 20) {
            return 0.75;
        }
        if (t < 25) {
            return 1.00;
        }
        if (t < 30) {
            return 0.90;
        }
        if (t < 35) {
            return 0.55;
        }
        if (t < 40) {
            return 0.25;
        }
        if (t < 45) {
            return 0.10;
        }
        // [45, 50]
        return 0.10;
    }
}
