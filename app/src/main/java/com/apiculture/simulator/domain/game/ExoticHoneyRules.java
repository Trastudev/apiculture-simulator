package com.apiculture.simulator.domain.game;

import androidx.annotation.Nullable;

import com.apiculture.simulator.domain.map.PlayableMapRegion;
import com.apiculture.simulator.domain.market.HoneyMarketEngine;
import com.apiculture.simulator.domain.market.TerritorialMarketRules;

/**
 * La miel de otro territorio solo se vende en un mercado internacional:
 * ×4 hasta el cupo diario de ese mercado.
 */
public final class ExoticHoneyRules {

    public static final double DAILY_QUOTA_KG = 400.0;
    public static final double MULTIPLIER_IN_QUOTA = 4.0;
    public static final double MULTIPLIER_OVER_QUOTA = 1.5;

    private ExoticHoneyRules() {
    }

    public static boolean isExotic(@Nullable PlayableMapRegion region, @Nullable String floraKey) {
        if (region == null || floraKey == null) {
            return false;
        }
        String key = HoneyMarketEngine.canonicalFloraKey(floraKey);
        return !TerritorialMarketRules.demandShares(region).containsKey(key);
    }

    public static double remainingKg(double soldTodayKg) {
        return Math.max(0.0, DAILY_QUOTA_KG - Math.max(0.0, soldTodayKg));
    }

    /**
     * Precio medio por kg. Lo que cabe en el cupo va a ×4 y el resto a ×1,5.
     * La venta se rechaza si supera el cupo; este reparto cubre un lote que lo roza.
     */
    public static double blendedPerKg(double basePerKg, double soldTodayKg, double kg) {
        double base = Math.max(0.0, basePerKg);
        if (kg <= 1e-9) {
            return base * MULTIPLIER_IN_QUOTA;
        }
        double room = Math.max(0.0, DAILY_QUOTA_KG - Math.max(0.0, soldTodayKg));
        double atHigh = Math.min(kg, room);
        double atLow = Math.max(0.0, kg - atHigh);
        return base * (atHigh * MULTIPLIER_IN_QUOTA + atLow * MULTIPLIER_OVER_QUOTA) / kg;
    }
}
