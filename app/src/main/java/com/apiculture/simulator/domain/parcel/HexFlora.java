package com.apiculture.simulator.domain.parcel;

import java.util.Random;

/**
 * Flora asignable a un hex: valor elegido al azar de forma estable según {@link #randomFloraForHexId(String)}.
 */
public final class HexFlora {

    public static final String[] FLORA_TYPES = {
            "Bosque",
            "Mil flores",
            "Romero",
            "Lavanda",
            "Tomillo",
            "Brezo",
            "Campo de girasoles",
            "Campo de Colza",
            "Campo de naranjos",
            "Campo de manzanos",
            "Campo de cerezos",
            "Campo de perales",
            "Campo de almendros",
    };

    private HexFlora() {
    }

    /**
     * Índice pseudoaleatorio en {@link #FLORA_TYPES}, reproducible para el mismo {@code hexId}.
     */
    public static int randomIndexForHexId(String hexId) {
        if (hexId == null || hexId.isEmpty()) {
            return 0;
        }
        long seed = stableHash64(hexId);
        Random r = new Random(seed);
        return r.nextInt(FLORA_TYPES.length);
    }

    public static String randomFloraForHexId(String hexId) {
        return FLORA_TYPES[randomIndexForHexId(hexId)];
    }

    private static long stableHash64(String s) {
        long h = -3750763034362895779L;
        for (int i = 0; i < s.length(); i++) {
            h ^= s.charAt(i);
            h *= 1099511628211L;
        }
        return h;
    }
}
