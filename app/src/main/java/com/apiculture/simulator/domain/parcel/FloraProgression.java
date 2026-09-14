package com.apiculture.simulator.domain.parcel;

import com.apiculture.simulator.domain.game.IberianClimateZone;
import com.apiculture.simulator.domain.market.HoneyMarketEngine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Desbloqueo de flora por nivel de jugador y reglas de siembra (tiempo y coste).
 * <p>
 * Orden: primero {@code Mil flores} (nivel 0); el resto por techo de precio €/kg del mercado (mayor a menor),
 * alineado con {@link HoneyMarketEngine}.
 */
public final class FloraProgression {

    private static final List<String> UNLOCK_ORDER;

    static {
        Map<String, Double> ceilings = honeyCeilingsCopy();
        List<String> rest = new ArrayList<>();
        for (String k : HexFlora.FLORA_TYPES) {
            if (!"Mil flores".equals(k)) {
                rest.add(k);
            }
        }
        rest.sort(Comparator.comparing((String k) -> ceilings.getOrDefault(k, 0.0)).reversed());
        List<String> order = new ArrayList<>();
        order.add("Mil flores");
        order.addAll(rest);
        UNLOCK_ORDER = Collections.unmodifiableList(order);
    }

    private static Map<String, Double> honeyCeilingsCopy() {
        Map<String, Double> m = new LinkedHashMap<>();
        for (String k : HexFlora.FLORA_TYPES) {
            m.put(k, HoneyMarketEngine.priceCeilingEurPerKgForFlora(k));
        }
        return m;
    }

    private FloraProgression() {
    }

    public static List<String> unlockOrder() {
        return UNLOCK_ORDER;
    }

    public static int indexInUnlockOrder(String floraKey) {
        String k = HoneyMarketEngine.canonicalFloraKey(floraKey);
        for (int i = 0; i < UNLOCK_ORDER.size(); i++) {
            if (UNLOCK_ORDER.get(i).equals(k)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * La flora en índice {@code i} exige nivel de jugador {@code >= i} (nivel 0 solo Mil flores).
     */
    public static boolean isFloraUnlockedForPlayerLevel(String floraKey, int playerLevel) {
        int idx = indexInUnlockOrder(floraKey);
        if (idx < 0) {
            return false;
        }
        int lvl = Math.max(0, playerLevel);
        return lvl >= idx;
    }

    /**
     * Nivel mínimo del jugador para poder usar esta flora (coincide con el índice en el orden de desbloqueo).
     */
    public static int minLevelRequiredForFlora(String floraKey) {
        int idx = indexInUnlockOrder(floraKey);
        return Math.max(0, idx);
    }

    public static List<String> florasUnlockedAtLevel(int playerLevel) {
        int lvl = Math.max(0, playerLevel);
        List<String> out = new ArrayList<>();
        for (int i = 0; i < UNLOCK_ORDER.size() && i <= lvl; i++) {
            out.add(UNLOCK_ORDER.get(i));
        }
        return out;
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
     * Prima € sobre la base del terreno por la flora “nativa” del hex (misma escala que siembras: índice 1 → 1000 €…).
     * Mil flores (índice 0) → 0 €.
     */
    public static int terrainFloraPremiumEuros(String floraKey) {
        int idx = indexInUnlockOrder(floraKey);
        if (idx <= 0) {
            return 0;
        }
        return 1000 * idx;
    }

    /** Precio total de compra de terreno libre: base 1000 € + prima por tipo de flora del hex. */
    public static int terrainPurchaseTotalEurosForNativeFlora(String floraKey) {
        return (int) HexParcelGameRules.HEX_PURCHASE_BASE_EUR + terrainFloraPremiumEuros(floraKey);
    }

    /** Vista previa estable en mapa para hex sin filas persistidas (no escribe BD). */
    public static String previewFloraForUnownedHex(HexParcel parcel) {
        return HexFlora.nativeFloraForParcel(parcel);
    }

    public static String previewFloraForUnownedHex(String hexId, IberianClimateZone zone) {
        return HexFlora.randomNativeForZone(hexId, zone);
    }
}
