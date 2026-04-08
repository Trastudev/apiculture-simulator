package com.apiculture.simulator.domain.population;

/**
 * Tendencia poblacional resumida (actualizada cada paso diario de simulación).
 */
public enum HivePopulationTrend {
    /** Crecimiento neto claro. */
    GROWING,
    /** Nacimientos y muertes cercanos. */
    STABLE,
    /** Pérdida neta de población. */
    DECLINING,
    /** Sin reina reproductora y sin reemplazo viable (colmena huérfana). */
    CRITICAL_ORPHAN,
    /** Población por debajo del umbral mínimo. */
    COLLAPSED;

    public String labelEs() {
        switch (this) {
            case GROWING:
                return "Creciendo";
            case STABLE:
                return "Estable";
            case DECLINING:
                return "En declive";
            case CRITICAL_ORPHAN:
                return "Crítica (huérfana)";
            case COLLAPSED:
            default:
                return "Colapso";
        }
    }
}
