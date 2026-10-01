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

    /** Obreras adultas: 0 % de enjambrazón en este umbral; sube en línea hasta {@link #SWARM_RISK_CAP_BEES}. */
    public static int SWARM_RISK_BASE_BEES = 40_000;

    /** Obreras adultas a las que el riesgo diario alcanza {@link #SWARM_RISK_DAILY_CAP}. */
    public static int SWARM_RISK_CAP_BEES = 52_000;

    /** Cada 1.000 adultas por encima de {@link #SWARM_RISK_BASE_BEES} suma un tramo de riesgo. */
    public static int SWARM_RISK_STEP_BEES = 1_000;

    /** Legado (JSON); el riesgo diario se deriva de cap / nº de tramos. */
    public static double SWARM_RISK_PER_STEP = 0.0291666667;

    /** Probabilidad diaria al llegar a {@link #SWARM_RISK_CAP_BEES}. */
    public static double SWARM_RISK_DAILY_CAP = 0.35;

    /** Mínimo de obreras adultas para permitir división en dos colmenas. */
    public static int MIN_BEES_TO_SPLIT = 35_000;

    /** Umbral (abejas en colmena) a partir del cual el inicio recomienda dividir. */
    public static int SPLIT_RECOMMEND_BEES = 70_000;

    private ColonyGameRules() {
    }

    /**
     * Probabilidad de enjambrazón el siguiente tick (0–1).
     * 0 % hasta {@link #SWARM_RISK_BASE_BEES}; un tramo por cada
     * {@link #SWARM_RISK_STEP_BEES} adultas; tope {@link #SWARM_RISK_DAILY_CAP}
     * en {@link #SWARM_RISK_CAP_BEES}.
     */
    public static double swarmRiskForAdultWorkers(int adultWorkers) {
        if (adultWorkers <= SWARM_RISK_BASE_BEES) {
            return 0.0;
        }
        int step = Math.max(1, SWARM_RISK_STEP_BEES);
        int capBees = Math.max(SWARM_RISK_BASE_BEES + step, SWARM_RISK_CAP_BEES);
        int maxSteps = (capBees - SWARM_RISK_BASE_BEES) / step;
        if (maxSteps <= 0) {
            return SWARM_RISK_DAILY_CAP;
        }
        int steps = (adultWorkers - SWARM_RISK_BASE_BEES) / step;
        if (steps <= 0) {
            return 0.0;
        }
        if (steps >= maxSteps) {
            return SWARM_RISK_DAILY_CAP;
        }
        return steps * (SWARM_RISK_DAILY_CAP / (double) maxSteps);
    }
}
