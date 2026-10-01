package com.apiculture.simulator.domain.parcel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Desbloqueos de cultivos por nivel, en el orden definido por diseño. */
public final class CropUnlock {

    private static final String[] CROPS = {
            "Campo de naranjos",
            "Campo de almendros",
            HexFlora.MANGO,
            "Campo de cerezos",
            "Campo de perales",
            HexFlora.MOSTAZA,
            HexFlora.RABANIZA,
            "Campo de manzanos",
            HexFlora.TREBOL,
            "Campo de girasoles",
            HexFlora.LAVANDA_CAMPO,
            HexFlora.CAFE,
            "Campo de Colza",
            HexFlora.FACELIA,
            HexFlora.LUCERNA,
            HexFlora.SISAL,
            HexFlora.LITCHI,
            HexFlora.MACADAMIA,
            HexFlora.AGUACATE
    };

    private static final int[] LEVELS = {
            2, 4, 5, 6, 8, 9, 10, 11, 13, 14, 16, 16, 17, 20, 27, 30, 43, 48, 60
    };

    private CropUnlock() {
    }

    public static int unlockedSlotCount(int playerLevel) {
        int level = Math.max(0, playerLevel);
        int count = 0;
        for (int requiredLevel : LEVELS) {
            if (level >= requiredLevel) {
                count++;
            }
        }
        return count;
    }

    public static List<String> unlockedCatalog(int playerLevel) {
        int count = unlockedSlotCount(playerLevel);
        List<String> unlocked = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            unlocked.add(CROPS[i]);
        }
        return Collections.unmodifiableList(unlocked);
    }

    public static boolean isUnlocked(String floraKey, int playerLevel) {
        String key = HexFlora.canonicalKey(floraKey);
        if (key.isEmpty() || !HexFlora.isPlantation(key)) {
            return false;
        }
        return unlockedCatalog(playerLevel).contains(key);
    }

    /** Nivel mínimo para sembrar este cultivo; {@code 0} si no es un cultivo del catálogo. */
    public static int requiredLevel(String floraKey) {
        String key = HexFlora.canonicalKey(floraKey);
        if (key.isEmpty()) {
            return 0;
        }
        for (int i = 0; i < CROPS.length; i++) {
            if (key.equals(HexFlora.canonicalKey(CROPS[i]))) {
                return LEVELS[i];
            }
        }
        return 0;
    }

    /** Cultivos que se acaban de desbloquear al pasar de {@code prevLevel} a {@code newLevel}. */
    public static List<String> newlyUnlocked(int prevLevel, int newLevel) {
        if (newLevel <= prevLevel) {
            return Collections.emptyList();
        }
        List<String> out = new ArrayList<>();
        for (int i = 0; i < LEVELS.length; i++) {
            if (LEVELS[i] > prevLevel && LEVELS[i] <= newLevel) {
                out.add(CROPS[i]);
            }
        }
        return Collections.unmodifiableList(out);
    }

    public static List<String> plantableOnParcel(HexParcel parcel, int playerLevel) {
        List<String> unlocked = unlockedCatalog(playerLevel);
        if (parcel == null || unlocked.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> out = new ArrayList<>();
        for (String crop : HexFlora.plantationPoolForParcel(parcel)) {
            String key = HexFlora.canonicalKey(crop);
            if (unlocked.contains(key)) {
                out.add(crop);
            }
        }
        return Collections.unmodifiableList(out);
    }

}
