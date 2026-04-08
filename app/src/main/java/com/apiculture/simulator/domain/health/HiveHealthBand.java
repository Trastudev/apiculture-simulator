package com.apiculture.simulator.domain.health;

/**
 * Banda de salud colmena (0–100).
 */
public enum HiveHealthBand {
    HEALTHY,
    STABLE,
    AT_RISK,
    CRITICAL,
    IMMINENT_COLLAPSE;

    public static HiveHealthBand fromHealth(int health) {
        int h = Math.max(0, Math.min(100, health));
        if (h >= 80) {
            return HEALTHY;
        }
        if (h >= 60) {
            return STABLE;
        }
        if (h >= 40) {
            return AT_RISK;
        }
        if (h >= 20) {
            return CRITICAL;
        }
        return IMMINENT_COLLAPSE;
    }

    public String labelEs() {
        switch (this) {
            case HEALTHY:
                return "Saludable";
            case STABLE:
                return "Estable";
            case AT_RISK:
                return "En riesgo";
            case CRITICAL:
                return "Crítico";
            case IMMINENT_COLLAPSE:
            default:
                return "Colapso inminente";
        }
    }
}
