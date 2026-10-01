package com.apiculture.simulator.domain.parcel;

import com.apiculture.simulator.domain.game.IberianClimateZone;
import com.apiculture.simulator.domain.market.HoneyMarketEngine;

import java.util.List;

/**
 * Coste y tiempo de siembra, y precio de terreno según el mix silvestre.
 * <p>
 * Precio de compra = {@link HexParcelGameRules#HEX_PURCHASE_BASE_EUR} + suma de la prima de cada
 * flora nativa (mil flores no suma). La prima sube con el techo de mercado €/kg de esa miel.
 */
public final class FloraProgression {

    private static final double CEILING_FLOOR = 15.30;
    private static final int PREMIUM_BASE = 400;
    private static final double PREMIUM_PER_EUR_KG = 1400.0;
    private static final int PREMIUM_MIN = 250;
    private static final int PREMIUM_MAX = 2500;

    private FloraProgression() {
    }

    /** Horas de crecimiento para la enésima flora en el terreno (1 → 24h, 2 → 48h…). */
    public static long growingDurationHoursForSlotIndex(int slotIndexOneBased) {
        int s = Math.max(1, slotIndexOneBased);
        return 24L * s;
    }

    /**
     * Coste en € de añadir una nueva flora cuando el terreno ya tiene {@code currentFloraCount} tipos
     * (listos o en curso). Primera siembra extra: 1000 €, luego 2000, 3000…
     */
    public static int plantingCostEurosForAdditionalFlora(int currentFloraCount) {
        int n = Math.max(0, currentFloraCount);
        return 1000 * n;
    }

    /**
     * Prima de una flora nativa. Mil flores no encarece el hex; el resto según valor de mercado de su miel.
     */
    public static int terrainFloraPremiumEuros(String floraKey) {
        String k = HoneyMarketEngine.canonicalFloraKey(floraKey);
        if (k.isEmpty() || HexFlora.MIL_FLORES.equals(k)) {
            return 0;
        }
        double ceiling = HoneyMarketEngine.priceCeilingEurPerKgForFlora(k);
        int value = (int) Math.round(PREMIUM_BASE + (ceiling - CEILING_FLOOR) * PREMIUM_PER_EUR_KG);
        return Math.max(PREMIUM_MIN, Math.min(PREMIUM_MAX, value));
    }

    /** Precio de un hex si solo contara una flora nativa. */
    public static int terrainPurchaseTotalEurosForNativeFlora(String floraKey) {
        return (int) HexParcelGameRules.HEX_PURCHASE_BASE_EUR + terrainFloraPremiumEuros(floraKey);
    }

    /** Precio total: base + suma de primas del mix silvestre. */
    public static int terrainPurchaseTotalEurosForNativeMix(List<String> mix) {
        int sum = 0;
        if (mix != null) {
            for (String k : mix) {
                sum += terrainFloraPremiumEuros(k);
            }
        }
        return (int) HexParcelGameRules.HEX_PURCHASE_BASE_EUR + sum;
    }

    /** Vista previa estable en mapa para hex sin filas persistidas (no escribe BD). */
    public static String previewFloraForUnownedHex(HexParcel parcel) {
        return HexFlora.nativeFloraForParcel(parcel);
    }

    public static String previewFloraForUnownedHex(String hexId, IberianClimateZone zone) {
        return HexFlora.randomNativeForZone(hexId, zone);
    }
}
