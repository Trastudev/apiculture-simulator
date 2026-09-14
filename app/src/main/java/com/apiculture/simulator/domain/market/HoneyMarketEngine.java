package com.apiculture.simulator.domain.market;

import com.apiculture.simulator.domain.game.ColonyGameRules;
import com.apiculture.simulator.domain.game.HiveDailyBiology;
import com.apiculture.simulator.domain.game.HoneyDailyProduction;
import com.apiculture.simulator.domain.game.Season;
import com.apiculture.simulator.domain.parcel.HexFlora;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mercado global: los clientes crecen con el número de jugadores reales.
 * Cada jugador representa un bolsillo de demanda local (apiario típico);
 * a más jugadores, más kg se pueden absorber antes de que baje el precio.
 * El precio de temporada se modula ±25 % según la oferta vendida ese día.
 */
public final class HoneyMarketEngine {

    public static final double MIN_PRICE_EUR_PER_KG = 8.0;
    /** Tope del recargo/descuento por oferta respecto al precio de temporada. */
    public static final double SUPPLY_PRICE_SPAN = 0.25;

    /**
     * Apiario medio por jugador (no el potencial del mapa entero).
     * La demanda diaria de clientes ≈ esta producción típica.
     */
    public static final int TYPICAL_HIVES_PER_PLAYER = 6;
    /** Las colmenas reales no están siempre en pico de mielada. */
    public static final double TYPICAL_OUTPUT_FRACTION = 0.40;
    /** Fracción de esa producción que los clientes locales absorberían a precio neutro. */
    public static final double CUSTOMER_ABSORPTION_FRACTION = 0.75;
    /** Días de producción típica que el mercado de cada tipo absorbe a precio neutro. */
    public static final double MARKET_DAYS_BUFFER = 10.0;
    /** Un tipo raro no baja de este % del bolsillo del tipo más demandado. */
    public static final double MIN_FLORA_DEMAND_VS_TOP = 0.35;

    private HoneyMarketEngine() {
    }

    public static HoneyMarketSnapshot computeSnapshot(int dayKey, int dayOfYear) {
        return computeSnapshot(dayKey, dayOfYear, 1);
    }

    public static HoneyMarketSnapshot computeSnapshot(int dayKey, int dayOfYear, int playerCount) {
        int players = Math.max(1, playerCount);
        Season season = Season.fromDayOfYear(dayOfYear);
        double peakKg = Math.max(1.0, HiveDailyBiology.maxChartDailyKgForAdults(
                ColonyGameRules.MAX_ADULT_WORKERS_PER_HIVE));
        double pocketKg = TYPICAL_HIVES_PER_PLAYER
                * peakKg
                * TYPICAL_OUTPUT_FRACTION
                * CUSTOMER_ABSORPTION_FRACTION
                * MARKET_DAYS_BUFFER;

        double demandSeason = demandSeasonFactor(season);
        double noise = dailyNoiseMultiplier(dayKey);

        double priceTension = clamp01(priceSeasonTension01(season) * noise);

        Map<String, Double> shares = demandSharesByFlora();
        double maxShare = 0.0;
        for (double sh : shares.values()) {
            maxShare = Math.max(maxShare, sh);
        }
        if (maxShare < 1e-6) {
            maxShare = 1.0;
        }

        Map<String, Double> demandByFlora = new LinkedHashMap<>();
        double totalDemandKg = 0.0;
        for (String flora : HexFlora.FLORA_TYPES) {
            double sh = shares.getOrDefault(flora, 0.0);
            double relative = Math.max(MIN_FLORA_DEMAND_VS_TOP, sh / maxShare);
            double kg = Math.max(1.0, players * pocketKg * relative * demandSeason * noise);
            kg = round2(kg);
            demandByFlora.put(flora, kg);
            totalDemandKg += kg;
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
                dayKey, round2(totalDemandKg), demandSeason, noise, priceTension,
                demandByFlora, prices, players);
    }

    /** Modula la demanda base: &gt;1 en frío, &lt;1 en primavera–verano. */
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
        return HexFlora.canonicalKey(floraType);
    }

    /**
     * 0 kg en oferta → +25 %; oferta = demanda de los N jugadores → 0 %;
     * oferta ≥ 2× demanda → −25 %.
     */
    public static double supplyPriceMultiplier(double demandKg, double soldKg) {
        if (demandKg <= 1e-6) {
            return 1.0 + SUPPLY_PRICE_SPAN;
        }
        double ratio = soldKg / demandKg;
        double clamped = Math.max(0.0, Math.min(2.0, ratio));
        return 1.0 + SUPPLY_PRICE_SPAN * (1.0 - clamped);
    }

    public static int supplyPriceAdjustmentPercent(double demandKg, double soldKg) {
        return (int) Math.round((supplyPriceMultiplier(demandKg, soldKg) - 1.0) * 100.0);
    }

    /**
     * Precio de temporada × multiplicador por oferta (tope {@link #SUPPLY_PRICE_SPAN}).
     */
    public static double priceEurPerKgFromSupply(
            String floraType, double demandKg, double soldKg, double seasonalBaseEurPerKg) {
        double base = seasonalBaseEurPerKg;
        if (base <= 1e-6) {
            base = priceCeilingEurPerKgForFlora(floraType);
        }
        return round2(base * supplyPriceMultiplier(demandKg, soldKg));
    }

    /**
     * Compatibilidad: sin precio de temporada, usa el techo del tipo como base.
     */
    public static double priceEurPerKgFromGlobalCoverage(
            String floraType, double demandKg, double soldKgGlobally) {
        return priceEurPerKgFromSupply(
                floraType, demandKg, soldKgGlobally, priceCeilingEurPerKgForFlora(floraType));
    }

    private static Map<String, Double> demandSharesByFlora() {
        Map<String, Double> m = new LinkedHashMap<>();
        m.put("Mil flores", 0.14);
        m.put("Campo de naranjos", 0.08);
        m.put("Romero", 0.07);
        m.put("Lavanda", 0.07);
        m.put("Tomillo", 0.06);
        m.put("Brezo", 0.06);
        m.put("Bosque", 0.05);
        m.put("Castaño", 0.05);
        m.put("Eucalipto", 0.04);
        m.put("Mielato de encina y roble", 0.04);
        m.put("Campo de girasoles", 0.03);
        m.put("Campo de Colza", 0.03);
        m.put("Arboç", 0.03);
        m.put("Campo de manzanos", 0.025);
        m.put("Campo de cerezos", 0.025);
        m.put("Neret", 0.02);
        m.put("Campo de perales", 0.015);
        m.put("Campo de almendros", 0.015);
        m.put("Fynbos", 0.035);
        m.put("Aloe", 0.025);
        m.put("Macadamia", 0.025);
        m.put("Litchi", 0.025);
        m.put("Lucerna", 0.02);
        m.put("Acacia", 0.02);
        return m;
    }

    /** Techo €/kg de cada tipo (precio de temporada alto). */
    public static double priceCeilingEurPerKgForFlora(String floraType) {
        String k = canonicalFloraKey(floraType);
        double c = priceCeilingsEurPerKg().getOrDefault(k, 15.50);
        return Math.max(c, MIN_PRICE_EUR_PER_KG + 0.01);
    }

    private static Map<String, Double> priceCeilingsEurPerKg() {
        Map<String, Double> m = new LinkedHashMap<>();
        m.put("Neret", 16.20);
        m.put("Arboç", 16.12);
        m.put("Fynbos", 16.25);
        m.put("Litchi", 16.10);
        m.put("Macadamia", 15.98);
        m.put("Mielato de encina y roble", 16.08);
        m.put("Lavanda", 16.00);
        m.put("Romero", 15.96);
        m.put("Tomillo", 15.93);
        m.put("Campo de naranjos", 15.91);
        m.put("Castaño", 15.90);
        m.put("Bosque", 15.89);
        m.put("Mil flores", 15.62);
        m.put("Eucalipto", 15.55);
        m.put("Aloe", 15.70);
        m.put("Acacia", 15.50);
        m.put("Lucerna", 15.42);
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
