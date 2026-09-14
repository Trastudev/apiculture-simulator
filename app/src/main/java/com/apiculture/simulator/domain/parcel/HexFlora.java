package com.apiculture.simulator.domain.parcel;

import com.apiculture.simulator.domain.game.Hemispheres;
import com.apiculture.simulator.domain.game.IberianClimateZone;
import com.apiculture.simulator.domain.game.SouthernAfricanClimateZone;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Flora asignable a un hex: nativa elegida al azar de forma estable según el id y la zona climática.
 */
public final class HexFlora {

    public static final String MIL_FLORES = "Mil flores";
    public static final String CASTANO = "Castaño";
    public static final String EUCALIPTO = "Eucalipto";
    public static final String MIELATO = "Mielato de encina y roble";
    public static final String NERET = "Neret";
    public static final String ARBOC = "Arboç";
    public static final String FYNBOS = "Fynbos";
    public static final String ALOE = "Aloe";
    public static final String MACADAMIA = "Macadamia";
    public static final String LITCHI = "Litchi";
    public static final String LUCERNA = "Lucerna";
    public static final String ACACIA = "Acacia";

    public static final String[] FLORA_TYPES = {
            "Bosque",
            MIL_FLORES,
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
            CASTANO,
            EUCALIPTO,
            MIELATO,
            NERET,
            ARBOC,
            FYNBOS,
            ALOE,
            MACADAMIA,
            LITCHI,
            LUCERNA,
            ACACIA,
    };

    private static final String[] ATLANTIC = {
            MIL_FLORES, CASTANO, "Brezo", EUCALIPTO, ARBOC, "Bosque"
    };
    private static final String[] MOUNTAIN = {
            MIL_FLORES, "Brezo", "Bosque", CASTANO, MIELATO, NERET, ARBOC
    };
    private static final String[] MEDITERRANEAN = {
            MIL_FLORES, "Campo de naranjos", "Romero", "Tomillo",
            "Campo de almendros", "Campo de manzanos", "Campo de cerezos", "Campo de perales"
    };
    private static final String[] SOUTH = {
            MIL_FLORES, "Campo de naranjos", EUCALIPTO, "Campo de girasoles",
            "Romero", "Tomillo", CASTANO, ARBOC, "Campo de almendros"
    };
    private static final String[] CONTINENTAL = {
            MIL_FLORES, "Romero", "Tomillo", "Lavanda", "Campo de girasoles",
            "Campo de Colza", MIELATO, "Campo de almendros", "Campo de cerezos"
    };

    private static final String[] ZA_FYNBOS = {
            FYNBOS, ALOE, EUCALIPTO, "Campo de Colza", MIL_FLORES
    };
    private static final String[] ZA_KAROO = {
            ALOE, LUCERNA, MIL_FLORES, EUCALIPTO, "Campo de Colza"
    };
    private static final String[] ZA_HIGHVELD = {
            "Campo de girasoles", EUCALIPTO, ACACIA, LUCERNA, ALOE, MIL_FLORES
    };
    private static final String[] ZA_SUBTROPICAL = {
            LITCHI, MACADAMIA, "Campo de naranjos", EUCALIPTO, MIL_FLORES
    };
    private static final String[] ZA_BUSHVELD = {
            ALOE, ACACIA, EUCALIPTO, "Campo de girasoles", MIL_FLORES, "Bosque"
    };

    private HexFlora() {
    }

    public static List<String> nativePoolForZone(IberianClimateZone zone) {
        return Collections.unmodifiableList(Arrays.asList(pool(zone)));
    }

    public static List<String> nativePoolForZone(SouthernAfricanClimateZone zone) {
        return Collections.unmodifiableList(Arrays.asList(pool(zone)));
    }

    public static boolean isSouthernParcel(HexParcel parcel) {
        if (parcel == null) {
            return false;
        }
        if (parcel.id != null && parcel.id.startsWith("za_")) {
            return true;
        }
        return Hemispheres.isSouthern(parcel.centroidLat);
    }

    /**
     * Flora sudafricana con calendario local (no se invierte medio año).
     * El resto, en el hemisferio sur, usa el calendario ibérico + 183 días.
     */
    public static boolean usesSouthernCalendar(String floraKey) {
        String k = canonicalKey(floraKey);
        return FYNBOS.equals(k) || ALOE.equals(k) || MACADAMIA.equals(k)
                || LITCHI.equals(k) || LUCERNA.equals(k) || ACACIA.equals(k);
    }

    public static boolean isAllowedInZone(String floraKey, IberianClimateZone zone) {
        return containsKey(pool(zone), floraKey);
    }

    public static boolean isAllowedInZone(String floraKey, SouthernAfricanClimateZone zone) {
        return containsKey(pool(zone), floraKey);
    }

    public static boolean isAllowedOnParcel(String floraKey, HexParcel parcel) {
        if (isSouthernParcel(parcel)) {
            return isAllowedInZone(floraKey, SouthernAfricanClimateZone.forParcel(parcel));
        }
        return isAllowedInZone(floraKey, IberianClimateZone.forParcel(parcel));
    }

    public static String nativeFloraForParcel(HexParcel parcel) {
        if (parcel == null) {
            return MIL_FLORES;
        }
        if (isSouthernParcel(parcel)) {
            return pickFromPool(parcel.id, pool(SouthernAfricanClimateZone.forParcel(parcel)));
        }
        return pickFromPool(parcel.id, pool(IberianClimateZone.forParcel(parcel)));
    }

    /**
     * Índice pseudoaleatorio en el pool de la zona, reproducible para el mismo {@code hexId}.
     */
    public static String randomNativeForZone(String hexId, IberianClimateZone zone) {
        return pickFromPool(hexId, pool(zone));
    }

    public static String randomNativeForZone(String hexId, SouthernAfricanClimateZone zone) {
        return pickFromPool(hexId, pool(zone));
    }

    /**
     * @deprecated usar {@link #randomNativeForZone(String, IberianClimateZone)} o
     * {@link #nativeFloraForParcel(HexParcel)}.
     */
    @Deprecated
    public static String randomFloraForHexId(String hexId) {
        return randomNativeForZone(hexId, IberianClimateZone.CONTINENTAL);
    }

    public static int randomIndexForHexId(String hexId) {
        String k = randomFloraForHexId(hexId);
        for (int i = 0; i < FLORA_TYPES.length; i++) {
            if (FLORA_TYPES[i].equals(k)) {
                return i;
            }
        }
        return 0;
    }

    public static String canonicalKey(String floraType) {
        if (floraType == null || floraType.trim().isEmpty()) {
            return MIL_FLORES;
        }
        String t = floraType.trim();
        String lower = t.toLowerCase(Locale.ROOT);
        if (lower.equals("arboç") || lower.equals("arboc") || lower.equals("madroño")
                || lower.equals("madrono") || lower.equals("madroñer") || lower.equals("arboçer")) {
            return ARBOC;
        }
        if (lower.equals("neret") || lower.equals("rododendro") || lower.equals("rhododendron")) {
            return NERET;
        }
        if (lower.contains("mielato") || lower.equals("encina y roble")) {
            return MIELATO;
        }
        if (lower.equals("castaño") || lower.equals("castano") || lower.equals("castanyer")) {
            return CASTANO;
        }
        if (lower.startsWith("eucalipt")) {
            return EUCALIPTO;
        }
        if (lower.equals("fynbos") || lower.contains("protea") || lower.equals("cape flora")) {
            return FYNBOS;
        }
        if (lower.startsWith("aloe") || lower.equals("áloe") || lower.equals("aloes")) {
            return ALOE;
        }
        if (lower.startsWith("macadamia")) {
            return MACADAMIA;
        }
        if (lower.equals("litchi") || lower.equals("lychee") || lower.equals("lichi")) {
            return LITCHI;
        }
        if (lower.equals("lucerna") || lower.equals("alfalfa") || lower.equals("medicago")) {
            return LUCERNA;
        }
        if (lower.equals("acacia") || lower.equals("wattle") || lower.startsWith("acacia")) {
            return ACACIA;
        }
        for (String s : FLORA_TYPES) {
            if (s.equalsIgnoreCase(t)) {
                return s;
            }
        }
        return MIL_FLORES;
    }

    private static boolean containsKey(String[] p, String floraKey) {
        String k = canonicalKey(floraKey);
        for (int i = 0; i < p.length; i++) {
            if (p[i].equals(k)) {
                return true;
            }
        }
        return false;
    }

    private static String pickFromPool(String hexId, String[] p) {
        if (p == null || p.length == 0) {
            return MIL_FLORES;
        }
        if (hexId == null || hexId.isEmpty()) {
            return p[0];
        }
        Random r = new Random(stableHash64(hexId));
        return p[r.nextInt(p.length)];
    }

    private static String[] pool(IberianClimateZone zone) {
        if (zone == null) {
            return CONTINENTAL;
        }
        switch (zone) {
            case ATLANTIC:
                return ATLANTIC;
            case MOUNTAIN:
                return MOUNTAIN;
            case MEDITERRANEAN:
                return MEDITERRANEAN;
            case SOUTH:
                return SOUTH;
            case CONTINENTAL:
            default:
                return CONTINENTAL;
        }
    }

    private static String[] pool(SouthernAfricanClimateZone zone) {
        if (zone == null) {
            return ZA_HIGHVELD;
        }
        switch (zone) {
            case FYNBOS:
                return ZA_FYNBOS;
            case KAROO:
                return ZA_KAROO;
            case SUBTROPICAL:
                return ZA_SUBTROPICAL;
            case BUSHVELD:
                return ZA_BUSHVELD;
            case HIGHVELD:
            default:
                return ZA_HIGHVELD;
        }
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
