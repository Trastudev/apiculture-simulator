package com.apiculture.simulator.domain.market;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Estado del mercado global de miel para un {@code dayKey} de juego.
 * El cupo regional es amplio; el objetivo de_rotation determina la presión de precio.
 */
public final class HoneyMarketSnapshot {

    public final int dayKey;
    /** Cupo regional compartido por tipo de miel (kg). */
    public final double totalDemandKg;
    /** Objetivo económico diario; no se usa como límite de venta. */
    public final double totalTurnoverTargetKg;
    /** Factor estacional aplicado a la demanda (nominal ~1). */
    public final double demandSeasonFactor;
    /** Multiplicador diario ~0,9–1,1. */
    public final double dailyNoiseMultiplier;
    /** 0–1 para interpolar precio de temporada entre mínimo y techo por tipo. */
    public final double priceTension01;
    public final Map<String, Double> demandKgByFlora;
    /** Objetivo de rotación por tipo, usado por la oferta/demanda del día anterior. */
    public final Map<String, Double> turnoverTargetKgByFlora;
    /** Precio de temporada €/kg (antes del ±25 % por oferta). */
    public final Map<String, Double> priceEurPerKgByFlora;
    /** Jugadores activos (contexto UI). */
    public final int playerCount;
    /** Colmenas × multiplicador de nivel, sumadas por región. */
    public final double activityUnits;

    public HoneyMarketSnapshot(
            int dayKey,
            double totalDemandKg,
            double demandSeasonFactor,
            double dailyNoiseMultiplier,
            double priceTension01,
            Map<String, Double> demandKgByFlora,
            Map<String, Double> priceEurPerKgByFlora) {
        this(dayKey, totalDemandKg, demandSeasonFactor, dailyNoiseMultiplier, priceTension01,
                demandKgByFlora, Collections.emptyMap(), priceEurPerKgByFlora,
                1, HoneyMarketEngine.typicalHivesPerPlayer());
    }

    public HoneyMarketSnapshot(
            int dayKey,
            double totalDemandKg,
            double demandSeasonFactor,
            double dailyNoiseMultiplier,
            double priceTension01,
            Map<String, Double> demandKgByFlora,
            Map<String, Double> priceEurPerKgByFlora,
            int playerCount) {
        this(dayKey, totalDemandKg, demandSeasonFactor, dailyNoiseMultiplier, priceTension01,
                demandKgByFlora, Collections.emptyMap(), priceEurPerKgByFlora,
                playerCount, Math.max(1, playerCount) * HoneyMarketEngine.typicalHivesPerPlayer());
    }

    public HoneyMarketSnapshot(
            int dayKey,
            double totalDemandKg,
            double demandSeasonFactor,
            double dailyNoiseMultiplier,
            double priceTension01,
            Map<String, Double> demandKgByFlora,
            Map<String, Double> priceEurPerKgByFlora,
            int playerCount,
            int activityUnits) {
        this(dayKey, totalDemandKg, demandSeasonFactor, dailyNoiseMultiplier, priceTension01,
                demandKgByFlora, Collections.emptyMap(), priceEurPerKgByFlora,
                playerCount, activityUnits);
    }

    public HoneyMarketSnapshot(
            int dayKey,
            double totalDemandKg,
            double demandSeasonFactor,
            double dailyNoiseMultiplier,
            double priceTension01,
            Map<String, Double> demandKgByFlora,
            Map<String, Double> turnoverTargetKgByFlora,
            Map<String, Double> priceEurPerKgByFlora,
            int playerCount,
            double activityUnits) {
        this.dayKey = dayKey;
        this.totalDemandKg = totalDemandKg;
        this.demandSeasonFactor = demandSeasonFactor;
        this.dailyNoiseMultiplier = dailyNoiseMultiplier;
        this.priceTension01 = priceTension01;
        this.demandKgByFlora = immutableCopy(demandKgByFlora);
        this.turnoverTargetKgByFlora = immutableCopy(turnoverTargetKgByFlora);
        this.priceEurPerKgByFlora = immutableCopy(priceEurPerKgByFlora);
        this.playerCount = Math.max(1, playerCount);
        this.activityUnits = Math.max(1.0, activityUnits);

        double turnoverTotal = 0.0;
        for (double value : this.turnoverTargetKgByFlora.values()) {
            turnoverTotal += Math.max(0.0, value);
        }
        this.totalTurnoverTargetKg = round2(turnoverTotal);
    }

    public double priceForFloraOrDefault(String floraType, double fallbackEurPerKg) {
        if (floraType == null || floraType.isEmpty()) {
            return fallbackEurPerKg;
        }
        String k = HoneyMarketEngine.canonicalFloraKey(floraType);
        Double p = priceEurPerKgByFlora.get(k);
        return p != null ? p : fallbackEurPerKg;
    }

    public double turnoverTargetForFloraOrDefault(String floraType, double fallbackKg) {
        if (floraType == null || floraType.isEmpty()) {
            return fallbackKg;
        }
        String k = HoneyMarketEngine.canonicalFloraKey(floraType);
        Double value = turnoverTargetKgByFlora.get(k);
        return value != null ? value : fallbackKg;
    }

    private static Map<String, Double> immutableCopy(Map<String, Double> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyMap();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
