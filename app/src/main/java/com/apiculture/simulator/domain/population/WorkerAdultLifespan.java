package com.apiculture.simulator.domain.population;

/**
 * Esperanza de vida media (días) de las obreras adultas según la época del año, con transiciones suaves.
 * <p>
 * Referencias divulgativas habituales: en primavera/verano la actividad de campo acorta la vida
 * (del orden de 4–7 semanas); en otoño/invierno las obreras “de invierno” pueden vivir muchos meses.
 * Los valores son aproximaciones para el simulador, no constantes biológicas exactas.
 */
public final class WorkerAdultLifespan {

    /** Número de franjas de edad (días 0 … {@code WORKER_ADULT_AGE_BUCKETS - 1}); la última acumula longevidades altas. */
    public static final int WORKER_ADULT_AGE_BUCKETS = 200;

    /**
     * Nudos (día del año 1–365, {@link java.time.LocalDate#getDayOfYear()}) y esperanza de vida media en días.
     * Interpolación lineal entre nudos consecutivos; el tramo 355→365 se une suavemente con el inicio del año.
     */
    public static int[] DOY_KNOT = {
            1, 45, 75, 110, 145, 172, 200, 230, 262, 288, 315, 335, 355, 365
    };

    public static double[] MEAN_LIFE_DAYS = {
            132, 118, 95, 62, 44, 39, 36, 36, 37, 44, 58, 78, 105, 128
    };

    private WorkerAdultLifespan() {
    }

    /**
     * Esperanza de vida media en días para la fecha indicada (hemisferio norte, calendario anual del juego).
     */
    public static double smoothedMeanLifespanDays(int dayOfYear) {
        int d = Math.max(1, Math.min(366, dayOfYear));
        if (d <= DOY_KNOT[0]) {
            return MEAN_LIFE_DAYS[0];
        }
        for (int i = 0; i < DOY_KNOT.length - 1; i++) {
            int a = DOY_KNOT[i];
            int b = DOY_KNOT[i + 1];
            if (d <= b) {
                double t = (d - a) / (double) Math.max(1, b - a);
                return MEAN_LIFE_DAYS[i] + t * (MEAN_LIFE_DAYS[i + 1] - MEAN_LIFE_DAYS[i]);
            }
        }
        // Entre último nudo y cierre del año: tender hacia el valor del 1 de enero (cierre suave).
        int last = DOY_KNOT[DOY_KNOT.length - 2];
        double t = (d - last) / (double) Math.max(1, 365 - last);
        double lLast = MEAN_LIFE_DAYS[MEAN_LIFE_DAYS.length - 2];
        double l365 = MEAN_LIFE_DAYS[MEAN_LIFE_DAYS.length - 1];
        return lLast + t * (l365 - lLast);
    }
}
