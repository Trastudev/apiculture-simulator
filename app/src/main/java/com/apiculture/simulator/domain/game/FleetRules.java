package com.apiculture.simulator.domain.game;

/**
 * Camiones y barcos comprados en la tienda. El nivel 1 es la compra.
 * Camión y barco llegan al nivel 10. Cada fila de mejora
 * es el precio para salir de ese nivel.
 */
public final class FleetRules {

    public enum Kind {
        TRUCK, SHIP
    }

    public static final int TRUCK_MAX_LEVEL = 10;
    public static final int SHIP_MAX_LEVEL = 10;
    /** Tope compartido de la flota. */
    public static final int MAX_LEVEL = 10;
    /** Precio de un amarre propio en un puerto público. */
    public static final int BERTH_B = 2_500;
    /** Amarres que un jugador puede tener en el mismo puerto. */
    public static final int MAX_BERTHS = 20;

    private static final int[] TRUCK_HONEY_KG = {
            100, 180, 300, 480, 750, 1_100, 1_550, 2_100, 2_800, 3_600};
    private static final int[] TRUCK_SPEED = {70, 80, 90, 100, 110, 118, 126, 134, 142, 150};
    private static final int[] TRUCK_UPGRADE_FROM = {
            800, 1_400, 2_200, 3_500, 5_200, 7_500, 10_500, 14_500, 19_000};

    private static final int[] SHIP_HONEY_KG = {
            2_000, 3_500, 5_500, 8_000, 12_000, 17_000, 23_000, 31_000, 41_000, 54_000};
    private static final int[] SHIP_SPEED = {120, 135, 150, 165, 180, 190, 200, 210, 220, 230};
    private static final int[] SHIP_UPGRADE_FROM = {
            3_000, 5_000, 8_000, 12_000, 18_000, 26_000, 36_000, 50_000, 68_000};

    private FleetRules() {
    }

    public static int purchaseCostB(Kind kind) {
        return kind == Kind.SHIP ? 6_000 : 1_200;
    }

    public static int maxLevel(Kind kind) {
        return kind == Kind.SHIP ? SHIP_MAX_LEVEL : TRUCK_MAX_LEVEL;
    }

    public static int upgradeCostB(Kind kind, int fromLevel) {
        if (fromLevel < 1 || fromLevel >= maxLevel(kind)) {
            return 0;
        }
        int[] table = kind == Kind.SHIP ? SHIP_UPGRADE_FROM : TRUCK_UPGRADE_FROM;
        return table[fromLevel - 1];
    }

    public static double honeyKg(Kind kind, int level) {
        int i = index(kind, level);
        return kind == Kind.SHIP ? SHIP_HONEY_KG[i] : TRUCK_HONEY_KG[i];
    }

    /** Huecos de colmena. Un barco no mueve colmenas. */
    public static int hiveSlots(Kind kind, int level) {
        if (kind != Kind.TRUCK) {
            return 0;
        }
        return index(Kind.TRUCK, level) + 1;
    }

    public static double speedKmh(Kind kind, int level) {
        int i = index(kind, level);
        return kind == Kind.SHIP ? SHIP_SPEED[i] : TRUCK_SPEED[i];
    }

    /** Plazas de camión: nivel 1 tiene 2, y cada nivel suma otras 2. */
    public static int truckSlots(int warehouseLevel) {
        return Math.max(1, warehouseLevel) * 2;
    }

    public static long durationMs(double km, double kmh) {
        if (km <= 1e-6 || kmh <= 1e-6) {
            return TruckTripRules.MIN_DURATION_MS;
        }
        long ms = Math.round(km / kmh * 3_600_000.0);
        return Math.max(TruckTripRules.MIN_DURATION_MS, ms);
    }

    /**
     * Una parada extra cabe si no alarga la ruta directa más de un 25 %.
     */
    public static boolean detourWithinLimit(double directKm, double viaKm) {
        if (viaKm <= 1e-6) {
            return false;
        }
        if (directKm <= 1e-6) {
            return true;
        }
        return viaKm <= directKm * 1.25 + 1e-6;
    }

    private static int index(Kind kind, int level) {
        int lvl = Math.max(1, Math.min(maxLevel(kind), level));
        return lvl - 1;
    }
}
