package com.apiculture.simulator.domain.game;

/**
 * Producción diaria de miel: el cálculo es explícitamente por abeja y día,
 * multiplicado después por el número de abejas de la colmena.
 * <p>
 * Referencias orientativas de la bibliografía divulgativa: rendimientos medios de colmena
 * prorrateados entre días y población (p. ej. guías tipo BootstrapBee, BeeProfessor). Valor
 * base ~2,7×10⁻⁵ kg/(abeja·día) (= ×10 sobre la referencia divulgativa típica); cada día se aplica
 * un factor uniforme en [0,9 ; 1,1].
 */
public final class HoneyDailyProduction {

    /** kg de miel por una abeja y un día (antes de aleatorizar y factor [0,9–1,1]). */
    private static final double KG_PER_BEE_PER_DAY_BASELINE = 2.7e-5;

    private HoneyDailyProduction() {
    }

    /**
     * kg/día de la colmena = (kg por abeja y día) × número de abejas × factor,
     * con factor uniforme en [0,9 ; 1,1].
     */
    public static double randomDailyKgForHive(int beeCount) {
        if (beeCount <= 0) return 0.0;
        double perBeePerDay = KG_PER_BEE_PER_DAY_BASELINE * (0.9 + Math.random() * 0.2);
        return beeCount * perBeePerDay;
    }

    /**
     * Misma fórmula que {@link #randomDailyKgForHive} pero determinista por colmena y día.
     * Hash y pseudoaleatorio portables (iguales en Cloud Functions JS).
     */
    public static double deterministicDailyKgForHive(String hiveId, int dayKey, int beeCount) {
        if (beeCount <= 0) {
            return 0.0;
        }
        String key = (hiveId != null && !hiveId.isEmpty()) ? hiveId : "_";
        double u = deterministicUniform01(key, dayKey);
        double perBeePerDay = KG_PER_BEE_PER_DAY_BASELINE * (0.9 + u * 0.2);
        return beeCount * perBeePerDay;
    }

    /** Igual que {@code portableStringHash} en functions/index.js */
    public static int portableStringHash(String s) {
        int h = 0;
        for (int i = 0; i < s.length(); i++) {
            h = 31 * h + s.charAt(i);
        }
        return h;
    }

    /**
     * Valor pseudoaleatorio determinista en [0, 1) a partir de una clave arbitraria y el día.
     */
    public static double deterministicUniform01(String key, int dayKey) {
        if (key == null) {
            return 0.0;
        }
        int h = portableStringHash(key);
        double x = Math.sin(h * 12.9898 + dayKey * 78.233) * 43758.5453;
        return x - Math.floor(x);
    }

    /**
     * Tope de escala para gráficos: producción diaria si todas las abejas fueran recolectoras al máximo
     * del factor diario (1,1× baseline por abeja), sin otros modificadores (clima, salud, etc.).
     */
    public static double maxChartDailyKgForBeeCount(int beeCount) {
        if (beeCount <= 0) {
            return 1.0;
        }
        return beeCount * KG_PER_BEE_PER_DAY_BASELINE * 1.1;
    }
}
