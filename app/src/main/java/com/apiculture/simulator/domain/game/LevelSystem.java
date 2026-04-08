package com.apiculture.simulator.domain.game;

/**
 * Sistema de niveles y experiencia del jugador.
 *
 * Nivel 0 empieza con 0 XP. La XP máxima del nivel actual se calcula con
 * una progresión simple para poder escalar fácilmente en el futuro.
 */
public class LevelSystem {

    private static final int BASE_XP_PER_LEVEL = 1_000;

    private LevelSystem() {
        // Utilidad estática
    }

    /**
     * XP necesaria para completar un nivel concreto.
     */
    public static int xpForLevel(int level) {
        if (level < 0) {
            level = 0;
        }
        // Progresión lineal simple: (nivel + 1) * BASE
        return (level + 1) * BASE_XP_PER_LEVEL;
    }

    /**
     * Resultado de aplicar XP adicional: puede subir de nivel varias veces.
     */
    public static Result addXp(int currentLevel, int currentXp, int xpToAdd) {
        if (xpToAdd <= 0) {
            return new Result(currentLevel, currentXp, xpForLevel(currentLevel));
        }

        int level = Math.max(0, currentLevel);
        int xp = Math.max(0, currentXp) + xpToAdd;
        int maxXp = xpForLevel(level);

        while (xp >= maxXp) {
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

