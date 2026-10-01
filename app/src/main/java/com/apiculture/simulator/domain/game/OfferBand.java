package com.apiculture.simulator.domain.game;

import androidx.annotation.NonNull;

/**
 * Diez franjas de nivel para comandas y polinización. Un jugador solo ve la suya.
 */
public final class OfferBand {

    public static final int COUNT = 10;
    public static final double REPLENISH_KM = 40.0;
    public static final double REPLENISH_FALLBACK_KM = 80.0;
    public static final long MIN_REPLENISH_REMAINING_MS = 2L * 60L * 60L * 1000L;

    private static final OfferBand[] ALL = {
            new OfferBand(0, 0, 1, 12, 0.5, 1.5, 8),
            new OfferBand(1, 2, 4, 14, 1.0, 3.0, 10),
            new OfferBand(2, 5, 9, 16, 2.0, 5.0, 12),
            new OfferBand(3, 10, 14, 18, 3.5, 8.0, 14),
            new OfferBand(4, 15, 19, 20, 6.0, 12.0, 16),
            new OfferBand(5, 20, 24, 22, 8.0, 16.0, 18),
            new OfferBand(6, 25, 29, 22, 11.0, 20.0, 20),
            new OfferBand(7, 30, 34, 24, 14.0, 24.0, 22),
            new OfferBand(8, 35, 39, 24, 18.0, 27.0, 24),
            new OfferBand(9, 40, 99, 26, 22.0, 30.0, 26)
    };

    public final int index;
    public final int minLevel;
    public final int maxLevel;
    public final int orderCount;
    public final double kgMin;
    public final double kgMax;
    public final int pollinationCount;

    private OfferBand(int index, int minLevel, int maxLevel, int orderCount,
            double kgMin, double kgMax, int pollinationCount) {
        this.index = index;
        this.minLevel = minLevel;
        this.maxLevel = maxLevel;
        this.orderCount = orderCount;
        this.kgMin = kgMin;
        this.kgMax = kgMax;
        this.pollinationCount = pollinationCount;
    }

    @NonNull
    public static OfferBand at(int index) {
        int i = Math.max(0, Math.min(COUNT - 1, index));
        return ALL[i];
    }

    @NonNull
    public static OfferBand ofLevel(int playerLevel) {
        int level = Math.max(0, playerLevel);
        for (int i = 0; i < ALL.length; i++) {
            if (level >= ALL[i].minLevel && level <= ALL[i].maxLevel) {
                return ALL[i];
            }
        }
        return ALL[ALL.length - 1];
    }

    public boolean containsLevel(int playerLevel) {
        int level = Math.max(0, playerLevel);
        return level >= minLevel && level <= maxLevel;
    }

    public double kgForSeed(long seed) {
        double span = Math.max(0.0, kgMax - kgMin);
        double t = (Math.floorMod(seed, 1000L) + 1L) / 1000.0;
        double kg = Math.round((kgMin + span * t) * 100.0) / 100.0;
        return Math.max(kgMin, Math.min(kgMax, kg));
    }
}
