package com.apiculture.simulator.domain.game;

/**
 * XP por acciones de apicultor. Cosechar, alimentar, varroa, terreno y el día no dan XP.
 * La miel vendida da 0,2 XP por cada 0,2 kg (menos de 0,2 kg = 0).
 */
public final class XpAwards {

    public static final double HONEY_STEP_KG = 0.2;
    public static final double HONEY_STEP_XP = 0.2;
    public static final double BUY_HIVE = 2.0;
    public static final double PLACE_APIARY = 15.0;
    public static final double BUY_WAREHOUSE = 10.0;
    public static final double PLANT_FLORA = 10.0;
    public static final double TRANSHUMANCE_PER_HIVE = 4.0;
    public static final double REPLACE_QUEEN = 4.0;
    public static final double ORDER_FLAT = 2.0;

    private XpAwards() {
    }

    /** 0,2 XP por cada 0,2 kg completos; por debajo de 0,2 kg no hay XP. */
    public static double honeySold(double kg) {
        if (kg + 1e-9 < HONEY_STEP_KG) {
            return 0.0;
        }
        int chunks = (int) Math.floor((kg + 1e-9) / HONEY_STEP_KG);
        return chunks * HONEY_STEP_XP;
    }

    public static double marketSold(double kg) {
        return honeySold(kg);
    }

    public static double orderDelivered(double kg) {
        return ORDER_FLAT + honeySold(kg);
    }

    public static double transhumance(int hiveCount) {
        return TRANSHUMANCE_PER_HIVE * Math.max(0, hiveCount);
    }
}
