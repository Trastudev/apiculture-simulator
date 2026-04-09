package com.apiculture.simulator.domain.game;

/**
 * Límites y reglas de colonia (población máx., enjambrazón, división).
 */
public final class ColonyGameRules {

    /**
     * Máximo de obreras <strong>adultas</strong> por colmena. La cría (huevos, larvas, pupas) puede sumar
     * por encima; el tope no aplica al total adultos + cría.
     */
    public static final int MAX_ADULT_WORKERS_PER_HIVE = 80_000;

    /**
     * Techo holgado de colonia total (adultos + cría) para limitar la puesta diaria cuando el tope duro
     * es solo de adultas; evita crecimiento ilimitado de la cría.
     */
    public static final int MAX_TOTAL_COLONY_BEES_SOFT_CAP = 200_000;

    /** Obreras adultas: a partir de este número + tramos de {@link #SWARM_RISK_STEP_BEES} sube el riesgo diario. */
    public static final int SWARM_RISK_BASE_BEES = 70_000;

    public static final int SWARM_RISK_STEP_BEES = 500;

    /** Probabilidad añadida por cada tramo completo de 500 abejas por encima de la base. */
    public static final double SWARM_RISK_PER_STEP = 0.03;

    /** Mínimo de abejas totales para permitir división en dos colmenas. */
    public static final int MIN_BEES_TO_SPLIT = 50_000;

    /** Umbral (abejas en colmena) a partir del cual el inicio recomienda dividir. */
    public static final int SPLIT_RECOMMEND_BEES = 70_000;

    private ColonyGameRules() {
    }

    /** Probabilidad de enjambrazón el siguiente tick (0–1), según obreras adultas. */
    public static double swarmRiskForAdultWorkers(int adultWorkers) {
        if (adultWorkers <= SWARM_RISK_BASE_BEES) {
            return 0.0;
        }
        int steps = (adultWorkers - SWARM_RISK_BASE_BEES) / SWARM_RISK_STEP_BEES;
        return Math.min(1.0, steps * SWARM_RISK_PER_STEP);
    }
}
