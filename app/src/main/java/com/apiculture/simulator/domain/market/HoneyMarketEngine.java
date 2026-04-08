package com.apiculture.simulator.domain.market;

import com.apiculture.simulator.domain.game.GameBalanceEngine;
import com.apiculture.simulator.domain.game.HoneyDailyProduction;
import com.apiculture.simulator.domain.game.Season;
import com.apiculture.simulator.domain.parcel.HexFlora;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mercado sintético: ~20k hex × 10 colmenas = 200k huecos; demanda diaria basada en el 70% del potencial
 * si todas las colmenas produjeran al máximo; reparto por tipos; precios €/kg entre 8 y ~16.
 */
public final class HoneyMarketEngine {

    public static final int MARKET_HEX_SLOTS = 20_000;
    public static final int HIVES_PER_HEX_MARKET = 10;
    public static final int GLOBAL_HIVE_CAPACITY = MARKET_HEX_SLOTS * HIVES_PER_HEX_MARKET;
    public static final double GLOBAL_DEMAND_FRACTION_OF_CAPACITY = 0.70;

    public static final double MIN_PRICE_EUR_PER_KG = 8.0;

    private HoneyMarketEngine() {
    }

    public static HoneyMarketSnapshot computeSnapshot(int dayKey, int dayOfYear) {
        Season season = Season.fromDayOfYear(dayOfYear);
        double maxKgPerIdealHive = GameBalanceEngine.globalMaxTheoreticalDailyKgPerHive();
        double globalPotentialKgPerDay = GLOBAL_HIVE_CAPACITY * maxKgPerIdealHive;

        double demandSeason = demandSeasonFactor(season);
        double noise = dailyNoiseMultiplier(dayKey);
        double totalDemandKg =
                globalPotentialKgPerDay * GLOBAL_DEMAND_FRACTION_OF_CAPACITY * demandSeason * noise;

        double priceTension = clamp01(priceSeasonTension01(season) * noise);

        Map<String, Double> shares = demandSharesByFlora();
        Map<String, Double> demandByFlora = new LinkedHashMap<>();
        for (String flora : HexFlora.FLORA_TYPES) {
            double sh = shares.getOrDefault(flora, 0.0);
            demandByFlora.put(flora, round2(totalDemandKg * sh));
        }

        Map<String, Double> ceilings = priceCeilingsEurPerKg();
        Map<String, Double> prices = new LinkedHashMap<>();
        for (String flora : HexFlora.FLORA_TYPES) {
            double maxType = ceilings.getOrDefault(flora, 15.50);
            maxType = Math.max(maxType, MIN_PRICE_EUR_PER_KG + 0.01);
            double p = MIN_PRICE_EUR_PER_KG + (maxType - MIN_PRICE_EUR_PER_KG) * priceTension;
            prices.put(flora, round2(p));
        }

        return new HoneyMarketSnapshot(
                dayKey, round2(totalDemandKg), demandSeason, noise, priceTension, demandByFlora, prices);
    }

    /** Modula el 70% base: >1 en frío, &lt;1 en primavera–verano (lento). */
    public static double demandSeasonFactor(Season season) {
        switch (season) {
            case WINTER:
                return 1.12;
            case AUTUMN:
                return 1.06;
            case SPRING:
                return 0.93;
            case SUMMER:
            default:
                return 0.87;
        }
    }

    /** Base 0–1 antes del ruido; otoño–invierno más cara, primavera–verano más barata. */
    public static double priceSeasonTension01(Season season) {
        switch (season) {
            case WINTER:
                return 0.97;
            case AUTUMN:
                return 0.90;
            case SPRING:
                return 0.58;
            case SUMMER:
            default:
                return 0.48;
        }
    }

    /** Ruido diario determinista ~ ±10% respecto a 1. */
    public static double dailyNoiseMultiplier(int dayKey) {
        double u = HoneyDailyProduction.deterministicUniform01("globalHoneyMarketNoise", dayKey);
        return 0.90 + u * 0.20;
    }

    public static String canonicalFloraKey(String floraType) {
        if (floraType == null || floraType.trim().isEmpty()) {
            return "Mil flores";
        }
        String t = floraType.trim();
        for (String s : HexFlora.FLORA_TYPES) {
            if (s.equalsIgnoreCase(t)) {
                return s;
            }
        }
        return "Mil flores";
    }

    private static Map<String, Double> demandSharesByFlora() {
        Map<String, Double> m = new LinkedHashMap<>();
        m.put("Mil flores", 0.26);
        m.put("Campo de naranjos", 0.14);
        m.put("Romero", 0.11);
        m.put("Lavanda", 0.10);
        m.put("Tomillo", 0.09);
        m.put("Brezo", 0.08);
        m.put("Bosque", 0.07);
        m.put("Campo de girasoles", 0.035);
        m.put("Campo de Colza", 0.035);
        m.put("Campo de manzanos", 0.025);
        m.put("Campo de cerezos", 0.025);
        m.put("Campo de perales", 0.015);
        m.put("Campo de almendros", 0.015);
        return m;
    }

    /** Techo €/kg de cada tipo (demanda global casi sin cubrir). */
    public static double priceCeilingEurPerKgForFlora(String floraType) {
        String k = canonicalFloraKey(floraType);
        double c = priceCeilingsEurPerKg().getOrDefault(k, 15.50);
        return Math.max(c, MIN_PRICE_EUR_PER_KG + 0.01);
    }

    /**
     * Precio €/kg según cuánta demanda diaria de ese tipo lleva cubierta el mercado global ({@code soldKg} / {@code demandKg}).
     * Sin cubrir (0) → techo del tipo; totalmente cubierta (1+) → precio mínimo ({@link #MIN_PRICE_EUR_PER_KG}).
     */
    public static double priceEurPerKgFromGlobalCoverage(
            String floraType, double demandKg, double soldKgGlobally) {
        double ceiling = priceCeilingEurPerKgForFlora(floraType);
        if (demandKg <= 1e-6) {
            return round2(ceiling);
        }
        double cov = clamp01(soldKgGlobally / demandKg);
        return round2(ceiling - (ceiling - MIN_PRICE_EUR_PER_KG) * cov);
    }

    private static Map<String, Double> priceCeilingsEurPerKg() {
        Map<String, Double> m = new LinkedHashMap<>();
        m.put("Lavanda", 16.00);
        m.put("Romero", 15.96);
        m.put("Tomillo", 15.93);
        m.put("Campo de naranjos", 15.91);
        m.put("Bosque", 15.89);
        m.put("Mil flores", 15.62);
        m.put("Brezo", 15.52);
        m.put("Campo de girasoles", 15.46);
        m.put("Campo de Colza", 15.43);
        m.put("Campo de manzanos", 15.40);
        m.put("Campo de cerezos", 15.38);
        m.put("Campo de perales", 15.35);
        m.put("Campo de almendros", 15.33);
        return m;
    }

    private static double clamp01(double x) {
        return Math.max(0.0, Math.min(1.0, x));
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
