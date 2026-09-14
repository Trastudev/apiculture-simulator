package com.apiculture.simulator.domain.game;

/**
 * Hitos de un evento de demanda global y recompensas asociadas.
 */
public final class DemandSurgeMilestones {

    public static final int[] PERCENTS = {15, 30, 50, 75, 100};

    public static final int COINS_AT_15 = 200;
    public static final int TREATMENTS_AT_30 = 10;
    public static final int COINS_AT_50 = 250;
    public static final int FEED_AT_75 = 10;
    public static final int QUEENS_AT_100 = 5;
    public static final int QUEEN_QUALITY = 100;

    private DemandSurgeMilestones() {
    }

    public static int highestReached(double kgSold, double targetKg) {
        if (targetKg <= 1e-9) {
            return 0;
        }
        double pct = 100.0 * kgSold / targetKg;
        int best = 0;
        for (int p : PERCENTS) {
            if (pct + 1e-9 >= p) {
                best = p;
            }
        }
        return best;
    }

    public static int coinsForHighest(int highestPct) {
        int coins = 0;
        if (highestPct >= 15) {
            coins += COINS_AT_15;
        }
        if (highestPct >= 50) {
            coins += COINS_AT_50;
        }
        return coins;
    }

    public static int treatmentsForHighest(int highestPct) {
        return highestPct >= 30 ? TREATMENTS_AT_30 : 0;
    }

    public static int feedForHighest(int highestPct) {
        return highestPct >= 75 ? FEED_AT_75 : 0;
    }

    public static int queensForHighest(int highestPct) {
        return highestPct >= 100 ? QUEENS_AT_100 : 0;
    }

    /** Texto de los 5 hitos con marca en los ya superados. */
    public static String ticksLabel(int highestPct) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < PERCENTS.length; i++) {
            if (i > 0) {
                sb.append("  ·  ");
            }
            sb.append(PERCENTS[i]).append('%');
            if (highestPct >= PERCENTS[i]) {
                sb.append(" ✓");
            }
        }
        return sb.toString();
    }
}
