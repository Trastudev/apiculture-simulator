package com.apiculture.simulator.domain.game;

/**
 * Costes y reglas de las acciones de manejo de colmena (alimentar, tratar, reina, división).
 */
public final class HiveCareRules {

    public static final double FEED_1_DAY_EUR = 8.0;
    public static final double FEED_7_DAYS_EUR = 42.0;
    /** Multiplicador del consumo de miel mientras la colmena está alimentada. */
    public static final double FEED_CONSUMPTION_MULTIPLIER = 0.50;

    public static final double TREAT_EUR = 25.0;
    public static final int TREAT_DAYS = 7;
    /** Cada día de tratamiento: varroa se multiplica por este factor (sin crecimiento). */
    public static final double TREAT_VARROA_KEEP_FACTOR = 0.78;

    public static final double QUEEN_EUR = 50.0;
    public static final int QUEEN_QUALITY_MIN = 65;
    public static final int QUEEN_QUALITY_MAX = 100;
    /** Calidad de la reina al crear una colmena nueva. */
    public static final int STARTER_QUEEN_QUALITY_MIN = 75;
    public static final int STARTER_QUEEN_QUALITY_MAX = 90;
    /** Sobrescrito desde {@link GameBalanceConfig#queenDailyDeathP} al cargar el JSON. */
    public static double QUEEN_DAILY_DEATH_P = 0.0004;

    public static final double SPLIT_SPAWN_MIN = 0.30;
    public static final double SPLIT_SPAWN_MAX = 0.60;

    private HiveCareRules() {
    }

    public static int randomCommercialQueenQuality() {
        int span = QUEEN_QUALITY_MAX - QUEEN_QUALITY_MIN + 1;
        return QUEEN_QUALITY_MIN + (int) (Math.random() * span);
    }

    public static int randomStarterQueenQuality() {
        int span = STARTER_QUEEN_QUALITY_MAX - STARTER_QUEEN_QUALITY_MIN + 1;
        return STARTER_QUEEN_QUALITY_MIN + (int) (Math.random() * span);
    }

    /** Fracción de abejas (y miel/reservas) que se lleva la colmena nueva. */
    public static double randomSplitSpawnShare() {
        return SPLIT_SPAWN_MIN + Math.random() * (SPLIT_SPAWN_MAX - SPLIT_SPAWN_MIN);
    }
}
