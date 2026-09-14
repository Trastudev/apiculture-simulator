package com.apiculture.simulator.domain.game;

/**
 * XP por acciones de apicultor. La venta de miel y el paso de día no dan XP.
 */
public final class XpAwards {

    public static final int XP_PER_HARVEST_KG = 8;
    public static final int BUY_HIVE_BASE = 40;
    public static final int BUY_HIVE_PER_SUPER = 10;
    public static final int BUY_TERRAIN_BASE = 80;
    public static final int BUY_TERRAIN_PER_1000_PREMIUM = 10;
    public static final int PLANT_FLORA_BASE = 50;
    public static final int PLANT_FLORA_PER_SLOT = 15;
    public static final int BUY_SUPER = 15;
    public static final int TRANSHUMANCE = 25;
    public static final int FEED = 10;

    private XpAwards() {
    }

    public static int harvest(double kg) {
        if (kg <= 1e-6) {
            return 0;
        }
        return Math.max(XP_PER_HARVEST_KG, (int) Math.round(kg * XP_PER_HARVEST_KG));
    }

    public static int buyHive(int superCount) {
        int s = Math.max(0, Math.min(2, superCount));
        return BUY_HIVE_BASE + BUY_HIVE_PER_SUPER * s;
    }

    public static int buyTerrain(int totalPriceEuros) {
        int premiumThousands = Math.max(0, (totalPriceEuros - 1000) / 1000);
        return BUY_TERRAIN_BASE + BUY_TERRAIN_PER_1000_PREMIUM * premiumThousands;
    }

    /** {@code florasAlreadyOnHex} = tipos presentes justo antes de sembrar. */
    public static int plantFlora(int florasAlreadyOnHex) {
        return PLANT_FLORA_BASE + PLANT_FLORA_PER_SLOT * Math.max(0, florasAlreadyOnHex);
    }

    public static int buySupers(int count) {
        return BUY_SUPER * Math.max(0, count);
    }
}
