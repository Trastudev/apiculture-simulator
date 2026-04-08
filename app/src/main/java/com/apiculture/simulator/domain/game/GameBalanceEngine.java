package com.apiculture.simulator.domain.game;

import com.apiculture.simulator.data.local.entity.HiveEntity;

import java.util.Locale;

public class GameBalanceEngine {

    /**
     * Producción diaria máxima teórica por colmena (población tope, primavera, salud y reina óptimas, flora intensiva).
     * Usado para dimensionar el mercado global.
     */
    public static double globalMaxTheoreticalDailyKgPerHive() {
        HiveEntity ideal = new HiveEntity();
        ideal.beeCount = ColonyGameRules.MAX_BEES_PER_HIVE;
        ideal.health = 100;
        ideal.queenGeneticQuality = 100;
        ideal.floraType = "Lavanda";
        return new GameBalanceEngine().calculateDailyHoneyKg(ideal, Season.SPRING);
    }

    public double calculateDailyHoneyKg(HiveEntity hive, Season season) {
        double seasonFactor = switch (season) {
            case SPRING -> 1.3;
            case SUMMER -> 1.1;
            case AUTUMN -> 0.8;
            case WINTER -> 0.2;
        };
        double healthHoney = HealthHoneyModifier.productionMultiplierForHealth(hive.health);
        double queenFactor = 0.6 + (hive.queenGeneticQuality / 100.0);
        double floraFactor = floraFactor(hive.floraType);
        return Math.max(0.0, hive.beeCount * 0.00016 * seasonFactor * healthHoney * queenFactor * floraFactor);
    }

    private double floraFactor(String floraType) {
        if (floraType == null) {
            return 1.0;
        }
        String f = floraType.toLowerCase(Locale.ROOT);
        return switch (f) {
            case "romero" -> 1.2;
            case "lavanda" -> 1.25;
            case "mil flores" -> 1.15;
            case "tomillo" -> 1.1;
            case "brezo" -> 1.05;
            case "bosque" -> 1.0;
            case "campo de girasoles", "campo de colza" -> 1.12;
            case "campo de naranjos", "campo de manzanos", "campo de cerezos",
                    "campo de perales", "campo de almendros" -> 1.08;
            default -> 1.0;
        };
    }
}
