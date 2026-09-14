package com.apiculture.simulator.domain.game;

/**
 * Recompensas al subir de nivel (por cada nivel ganado en un mismo salto de XP).
 */
public final class LevelUpRewards {

    private LevelUpRewards() {
    }

    /** BeeCoins por nivel ganado (crece un poco con el nivel alcanzado). */
    public static int coinsForLevel(int newLevel) {
        int lvl = Math.max(1, newLevel);
        return 50 + Math.min(100, (lvl - 1) * 5);
    }

    public static int treatmentsForLevel(int newLevel) {
        return 1 + ((Math.max(1, newLevel) - 1) / 5);
    }

    public static int feedForLevel(int newLevel) {
        return 2 + ((Math.max(1, newLevel) - 1) / 3);
    }

    public static int totalCoins(int fromLevelExclusive, int toLevelInclusive) {
        int sum = 0;
        for (int lvl = fromLevelExclusive + 1; lvl <= toLevelInclusive; lvl++) {
            sum += coinsForLevel(lvl);
        }
        return sum;
    }

    public static int totalTreatments(int fromLevelExclusive, int toLevelInclusive) {
        int sum = 0;
        for (int lvl = fromLevelExclusive + 1; lvl <= toLevelInclusive; lvl++) {
            sum += treatmentsForLevel(lvl);
        }
        return sum;
    }

    public static int totalFeed(int fromLevelExclusive, int toLevelInclusive) {
        int sum = 0;
        for (int lvl = fromLevelExclusive + 1; lvl <= toLevelInclusive; lvl++) {
            sum += feedForLevel(lvl);
        }
        return sum;
    }
}
