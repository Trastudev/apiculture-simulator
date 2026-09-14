package com.apiculture.simulator.domain.game;

/**
 * Factor de producción de miel según salud de la colmena (0–100).
 * Por cada 1 % de salud perdido respecto a 100, la producción cae un 0,5 %.
 */
public final class HealthHoneyModifier {

    private HealthHoneyModifier() {
    }

    /**
     * {@code 1 - ((100 - salud) * 0.005)} acotado a [0.35, 1].
     */
    public static double productionMultiplierForHealth(int health) {
        int h = Math.max(0, Math.min(100, health));
        double f = 1.0 - ((100 - h) * GameBalanceConfig.healthHoneyLossPerMissingPoint);
        if (f < GameBalanceConfig.healthHoneyFloor) {
            return GameBalanceConfig.healthHoneyFloor;
        }
        if (f > 1.0) {
            return 1.0;
        }
        return f;
    }
}
