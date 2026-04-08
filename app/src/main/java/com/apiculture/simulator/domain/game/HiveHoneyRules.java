package com.apiculture.simulator.domain.game;

import com.apiculture.simulator.data.local.entity.HiveEntity;

/**
 * Miel almacenada en la colmena ({@link com.apiculture.simulator.data.local.entity.HiveEntity#honeyProduction} kg).
 */
public final class HiveHoneyRules {

    /** Reserva mínima que debe quedar en colmena; por encima se puede recolectar. */
    public static final double MIN_HIVE_STOCK_KG = 3.0;

    /** Precio de una alza adicional (compra en la ficha de colmena). */
    public static final double SUPER_PURCHASE_PRICE_EUR = 50.0;

    private HiveHoneyRules() {
    }

    /**
     * Capacidad máxima de miel en colmena según alzas: 0 → 5 kg, 1 → 30 kg, 2 → 60 kg.
     */
    public static double maxHoneyKgForSuperCount(int superCount) {
        int s = Math.max(0, Math.min(2, superCount));
        switch (s) {
            case 0:
                return 5.0;
            case 1:
                return 30.0;
            default:
                return 60.0;
        }
    }

    public static void clampHoneyStockToCap(HiveEntity h) {
        if (h == null) {
            return;
        }
        double cap = maxHoneyKgForSuperCount(h.superCount);
        h.honeyProduction = Math.max(0.0, Math.min(cap, h.honeyProduction));
    }

    /** {@code true} si la reserva está al límite (o casi) para el nivel de alzas. */
    public static boolean isHoneyAtCapacity(HiveEntity h) {
        if (h == null) {
            return false;
        }
        double cap = maxHoneyKgForSuperCount(h.superCount);
        return h.honeyProduction >= cap - 1e-3;
    }
}
