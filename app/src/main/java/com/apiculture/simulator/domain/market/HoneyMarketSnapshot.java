package com.apiculture.simulator.domain.market;

import java.util.Collections;
import java.util.Map;

/**
 * Estado del mercado global de miel para un {@code dayKey} de juego (sin acumulación día a día).
 */
public final class HoneyMarketSnapshot {

    public final int dayKey;
    /** Demanda global ese día (kg), proporcional a {@link #playerCount}. */
    public final double totalDemandKg;
    /** Factor estacional aplicado a la demanda (nominal ~1). */
    public final double demandSeasonFactor;
    /** Multiplicador diario ~0,9–1,1. */
    public final double dailyNoiseMultiplier;
    /** 0–1 para interpolar precio de temporada entre mínimo y techo por tipo. */
    public final double priceTension01;
    public final Map<String, Double> demandKgByFlora;
    /** Precio de temporada €/kg (antes del ±25 % por oferta). */
    public final Map<String, Double> priceEurPerKgByFlora;
    /** Jugadores reales usados para dimensionar la demanda. */
    public final int playerCount;

    public HoneyMarketSnapshot(
            int dayKey,
            double totalDemandKg,
            double demandSeasonFactor,
            double dailyNoiseMultiplier,
            double priceTension01,
            Map<String, Double> demandKgByFlora,
            Map<String, Double> priceEurPerKgByFlora) {
        this(dayKey, totalDemandKg, demandSeasonFactor, dailyNoiseMultiplier, priceTension01,
                demandKgByFlora, priceEurPerKgByFlora, 1);
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
        this.dayKey = dayKey;
        this.totalDemandKg = totalDemandKg;
        this.demandSeasonFactor = demandSeasonFactor;
        this.dailyNoiseMultiplier = dailyNoiseMultiplier;
        this.priceTension01 = priceTension01;
        this.demandKgByFlora = Collections.unmodifiableMap(demandKgByFlora);
        this.priceEurPerKgByFlora = Collections.unmodifiableMap(priceEurPerKgByFlora);
        this.playerCount = Math.max(1, playerCount);
    }

    public double priceForFloraOrDefault(String floraType, double fallbackEurPerKg) {
        if (floraType == null || floraType.isEmpty()) {
            return fallbackEurPerKg;
        }
        String k = HoneyMarketEngine.canonicalFloraKey(floraType);
        Double p = priceEurPerKgByFlora.get(k);
        return p != null ? p : fallbackEurPerKg;
    }
}
