package com.apiculture.simulator.domain.game;

/**
 * Sistema de niveles y experiencia del jugador.
 * Nivel 0 empieza con 0 XP. Cada nivel pide ×1,5 respecto al anterior (base 100).
 */
public class LevelSystem {

    public static final int BASE_XP_PER_LEVEL = 100;
    public static final double XP_GROWTH = 1.5;
    private static final int MAX_XP_PER_LEVEL = 2_000_000;

    private LevelSystem() {
    }

    /**
     * XP necesaria para completar un nivel concreto (pasar de {@code level} a {@code level + 1}).
     */
    public static int xpForLevel(int level) {
        int l = Math.max(0, level);
        double raw = BASE_XP_PER_LEVEL * Math.pow(XP_GROWTH, l);
        if (raw >= MAX_XP_PER_LEVEL) {
            return MAX_XP_PER_LEVEL;
        }
        return Math.max(1, (int) Math.round(raw));
    }

    /**
     * Resultado de aplicar XP adicional: puede subir de nivel varias veces.
     */
    public static Result addXp(int currentLevel, int currentXp, int xpToAdd) {
        if (xpToAdd <= 0) {
            int lvl = Math.max(0, currentLevel);
            return new Result(lvl, Math.max(0, currentXp), xpForLevel(lvl));
        }

        int level = Math.max(0, currentLevel);
        int xp = Math.max(0, currentXp) + xpToAdd;
        int maxXp = xpForLevel(level);

        while (xp >= maxXp && maxXp > 0) {
            xp -= maxXp;
            level++;
            maxXp = xpForLevel(level);
        }

        return new Result(level, xp, maxXp);
    }

    public static final class Result {
        public final int level;
        public final int xp;
        public final int maxXp;

        public Result(int level, int xp, int maxXp) {
            this.level = level;
            this.xp = xp;
            this.maxXp = maxXp;
        }
    }
}
