package com.apiculture.simulator.domain.game;

/**
 * Utilidades deterministas de miel (hash portable) y escala de gráficos.
 * El kg/día real lo calcula {@link HiveDailyBiology} (pecoreo − consumo).
 */
public final class HoneyDailyProduction {

    private HoneyDailyProduction() {
    }

    /**
     * @deprecated Usar {@link HiveDailyBiology#netHoneyKg}. Conservado por si queda algún llamador legado.
     */
    @Deprecated
    public static double randomDailyKgForHive(int beeCount) {
        if (beeCount <= 0) return 0.0;
        return HiveDailyBiology.maxChartDailyKgForAdults(beeCount) * (0.25 + Math.random() * 0.15);
    }

    /**
     * @deprecated Usar {@link HiveDailyBiology#netHoneyKg}.
     */
    @Deprecated
    public static double deterministicDailyKgForHive(String hiveId, int dayKey, int beeCount) {
        if (beeCount <= 0) {
            return 0.0;
        }
        String key = (hiveId != null && !hiveId.isEmpty()) ? hiveId : "_";
        double u = deterministicUniform01(key, dayKey);
        return HiveDailyBiology.maxChartDailyKgForAdults(beeCount) * (0.20 + u * 0.20);
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
     * Tope de escala para gráficos: mielada plena de una colonia de ese tamaño (sin consumo).
     */
    public static double maxChartDailyKgForBeeCount(int beeCount) {
        return HiveDailyBiology.maxChartDailyKgForAdults(beeCount);
    }
}
