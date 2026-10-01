package com.apiculture.simulator.domain.game;

import androidx.annotation.Nullable;

/** Nombre visible de almacén, camión o barco. No se repite dentro de la misma clase. */
public final class EntityNames {

    public static final int MIN = 2;
    public static final int MAX = 32;

    private EntityNames() {
    }

    @Nullable
    public static String clean(@Nullable String raw) {
        if (raw == null) {
            return null;
        }
        String name = raw.trim().replaceAll("\\s+", " ");
        if (name.length() < MIN || name.length() > MAX) {
            return null;
        }
        return name;
    }

    public static boolean same(@Nullable String a, @Nullable String b) {
        if (a == null || b == null) {
            return false;
        }
        return a.trim().equalsIgnoreCase(b.trim());
    }
}
