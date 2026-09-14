package com.apiculture.simulator.domain.game;

/**
 * El calendario biológico del juego está calibrado al hemisferio norte (Iberia).
 * En el sur se desplaza medio año: invierno ibérico = verano sudafricano.
 */
public final class Hemispheres {

    /** Mitad de año civil usada para invertir estaciones. */
    public static final int SEASON_FLIP_DAYS = 183;

    private Hemispheres() {
    }

    public static boolean isSouthern(Double lat) {
        return lat != null && !Double.isNaN(lat) && lat < 0.0;
    }

    public static boolean isSouthern(double lat) {
        return !Double.isNaN(lat) && lat < 0.0;
    }

    /**
     * Día del año (1–365) para capacidad de carga, puesta, enjambrazón y varroa.
     */
    public static int biologicalDayOfYear(int calendarDayOfYear, Double lat) {
        int d = Math.max(1, Math.min(366, calendarDayOfYear));
        if (d > 365) {
            d = 365;
        }
        if (!isSouthern(lat)) {
            return d;
        }
        int x = d + SEASON_FLIP_DAYS;
        while (x > 365) {
            x -= 365;
        }
        return Math.max(1, x);
    }
}
