package com.apiculture.simulator.domain.game;

import com.apiculture.simulator.data.local.entity.HiveEntity;

/**
 * Miel almacenada en la colmena ({@link com.apiculture.simulator.data.local.entity.HiveEntity#honeyProduction} kg).
 */
public final class HiveHoneyRules {

    /**
     * Por debajo de este stock (kg) caen puesta y esperanza de vida de las obreras.
     * Se puede recolectar por debajo; el jugador elige la cantidad.
     */
    public static double MIN_HIVE_STOCK_KG = 0.5;

    /** Stock inicial de miel (kg) al crear una colmena. */
    public static double STARTER_HIVE_STOCK_KG = 5.0;

    /** Reserva que deja «recolectar todas» en cada colmena. */
    public static double HARVEST_ALL_LEAVE_KG = 3.0;

    /** Precio de una alza adicional (compra en la ficha de colmena). */
    public static double SUPER_PURCHASE_PRICE_EUR = 50.0;

    /** Capacidad máx. de miel (kg) con 0 / 1 / 2 alzas. */
    public static double[] SUPER_CAP_KG = {8.0, 30.0, 60.0};

    private HiveHoneyRules() {
    }

    /**
     * Capacidad máxima de miel en colmena según alzas: 0 → 8 kg, 1 → 30 kg, 2 → 60 kg.
     */
    public static double maxHoneyKgForSuperCount(int superCount) {
        double[] caps = SUPER_CAP_KG;
        if (caps == null || caps.length == 0) {
            return 8.0;
        }
        int s = Math.max(0, Math.min(caps.length - 1, superCount));
        return caps[s];
    }

    /** 1 si hay miel suficiente; interpola hacia {@code floor} al acercarse a 0 kg. */
    public static double lowHoneyScale(double honeyKg, double floor) {
        double min = Math.max(1e-9, MIN_HIVE_STOCK_KG);
        if (honeyKg >= min - 1e-12) {
            return 1.0;
        }
        double t = Math.max(0.0, honeyKg) / min;
        double f = Math.max(0.0, Math.min(1.0, floor));
        return Math.max(f, f + (1.0 - f) * t);
    }

    public static void clampHoneyStockToCap(HiveEntity h) {
        if (h == null) {
            return;
        }
        double cap = maxHoneyKgForSuperCount(h.superCount);
        h.honeyProduction = Math.max(0.0, Math.min(cap, h.honeyProduction));
    }

    /**
     * {@code true} si la reserva está al límite (o casi) para el nivel de alzas.
     * <p>
     * Tolerancia por redondeo float y porque la producción <strong>neta</strong> del día puede ser
     * ligeramente negativa (consumo fijo por abeja). Con muchas abejas al tope de población ese consumo
     * es mayor en kg/día; en colmenas con poca capacidad (0 alzas = 8 kg) basta un día flojo para bajar
     * unos gramos y, con un umbral demasiado estricto, el aviso desaparecía aunque sigas “prácticamente lleno”.
     */
    public static boolean isHoneyAtCapacity(HiveEntity h) {
        if (h == null) {
            return false;
        }
        double cap = maxHoneyKgForSuperCount(h.superCount);
        if (cap <= 0) {
            return false;
        }
        double tol = Math.min(0.10, Math.max(0.002, cap * 0.0015));
        if (cap <= 15.0) {
            tol = Math.max(tol, 0.08);
        }
        return h.honeyProduction >= cap - tol;
    }
}
