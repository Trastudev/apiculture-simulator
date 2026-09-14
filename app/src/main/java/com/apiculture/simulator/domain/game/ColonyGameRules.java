package com.apiculture.simulator.domain.game;

/**
 * Límites y reglas de colonia (población máx., enjambrazón, división).
 */
public final class ColonyGameRules {

    /**
     * Máximo de obreras <strong>adultas</strong> por colmena. La cría (huevos, larvas, pupas) puede sumar
     * por encima; el tope no aplica al total adultos + cría.
     */
    public static int MAX_ADULT_WORKERS_PER_HIVE = 80_000;

    /**
     * Techo holgado de colonia total (adultos + cría) para limitar la puesta diaria cuando el tope duro
     * es solo de adultas; evita crecimiento ilimitado de la cría.
     */
    public static final int MAX_TOTAL_COLONY_BEES_SOFT_CAP = 200_000;

    /** Obreras adultas: 0 % de enjambrazón en este umbral; sube en línea hasta el tope de adultas. */
    public static int SWARM_RISK_BASE_BEES = 60_000;

    public static int SWARM_RISK_STEP_BEES = 5_000;

    /** Legado (JSON); la curva actual es lineal, no por tramos. */
    public static double SWARM_RISK_PER_STEP = 0.008;

    /** Probabilidad diaria al llegar al máximo de obreras adultas ({@link #MAX_ADULT_WORKERS_PER_HIVE}). */
    public static double SWARM_RISK_DAILY_CAP = 0.35;

    /** Mínimo de abejas totales para permitir división en dos colmenas. */
    public static int MIN_BEES_TO_SPLIT = 50_000;

    /** Umbral (abejas en colmena) a partir del cual el inicio recomienda dividir. */
    public static int SPLIT_RECOMMEND_BEES = 70_000;

    private ColonyGameRules() {
    }

    /**
     * Probabilidad de enjambrazón el siguiente tick (0–1), lineal entre
     * {@link #SWARM_RISK_BASE_BEES} (0 %) y {@link #MAX_ADULT_WORKERS_PER_HIVE} ({@link #SWARM_RISK_DAILY_CAP}).
     */
    public static double swarmRiskForAdultWorkers(int adultWorkers) {
        if (adultWorkers <= SWARM_RISK_BASE_BEES) {
            return 0.0;
        }
        int span = MAX_ADULT_WORKERS_PER_HIVE - SWARM_RISK_BASE_BEES;
        if (span <= 0) {
            return SWARM_RISK_DAILY_CAP;
        }
        double t = (adultWorkers - SWARM_RISK_BASE_BEES) / (double) span;
        return Math.min(SWARM_RISK_DAILY_CAP, t * SWARM_RISK_DAILY_CAP);
    }
}
