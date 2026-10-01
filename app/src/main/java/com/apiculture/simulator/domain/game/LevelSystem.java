package com.apiculture.simulator.domain.game;

/**
 * Sistema de niveles y experiencia del jugador.
 * Nivel 0 empieza con 0 XP. Cada nivel pide un 10 % más que el anterior (base 100),
 * y el cupo final incluye una reducción acumulada del 62,2 % sobre la curva base.
 */
public class LevelSystem {

    public static final int BASE_XP_PER_LEVEL = 100;
    public static final double XP_GROWTH = 1.10;
    public static final int DIFFICULTY = 2;
    /** 0,42 anterior × 0,90 adicional: 62,2 % menos que el coste original. */
    public static final double XP_COST_MULTIPLIER = 0.378;
    private static final int MAX_XP_PER_LEVEL = 4_000_000;

    private LevelSystem() {
    }

    /**
     * XP necesaria para completar un nivel concreto (pasar de {@code level} a {@code level + 1}).
     */
    public static int xpForLevel(int level) {
        int l = Math.max(0, level);
        double raw = BASE_XP_PER_LEVEL * Math.pow(XP_GROWTH, l);
        int mild = raw >= 2_000_000 ? 2_000_000 : Math.max(1, (int) Math.round(raw));
        int doubled = mild * DIFFICULTY;
        int reduced = (int) Math.round(doubled * XP_COST_MULTIPLIER);
        return Math.min(MAX_XP_PER_LEVEL, Math.max(1, reduced));
    }

    /**
     * Resultado de aplicar XP adicional: puede subir de nivel varias veces.
     */
    public static Result addXp(int currentLevel, double currentXp, double xpToAdd) {
        if (xpToAdd <= 1e-12) {
            int lvl = Math.max(0, currentLevel);
            return new Result(lvl, Math.max(0.0, currentXp), xpForLevel(lvl));
        }

        int level = Math.max(0, currentLevel);
        double xp = Math.max(0.0, currentXp) + xpToAdd;
        int maxXp = xpForLevel(level);

        while (xp + 1e-12 >= maxXp && maxXp > 0) {
            xp -= maxXp;
            level++;
            maxXp = xpForLevel(level);
        }
        if (xp < 0.0) {
            xp = 0.0;
        }

        return new Result(level, xp, maxXp);
    }

    public static final class Result {
        public final int level;
        public final double xp;
        public final int maxXp;

        public Result(int level, double xp, int maxXp) {
            this.level = level;
            this.xp = xp;
            this.maxXp = maxXp;
        }
    }
}
