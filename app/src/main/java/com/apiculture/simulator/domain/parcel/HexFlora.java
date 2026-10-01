package com.apiculture.simulator.domain.parcel;

import com.apiculture.simulator.domain.game.Hemispheres;
import com.apiculture.simulator.domain.game.IberianClimateZone;
import com.apiculture.simulator.domain.game.MadagascarClimateZone;
import com.apiculture.simulator.domain.game.SouthernAfricanClimateZone;
import com.apiculture.simulator.domain.map.PlayableMapRegion;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
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
    public static final String BUCHU = "Buchu";
    public static final String PROTEA = "Protea";
    public static final String BOEKENHOUT = "Boekenhout";
    public static final String AGUACATE = "Aguacate";
    public static final String MARULA = "Marula";
    public static final String LAVANDA_CAMPO = "Campo de lavanda";
    public static final String MOSTAZA = "Campo de mostaza";
    public static final String TREBOL = "Campo de trébol";
    public static final String FACELIA = "Campo de facelia";
    public static final String RABANIZA = "Campo de rabaniza";
    public static final String GIROFLE = "Girofle";
    public static final String RAVINTSARA = "Ravintsara";
    public static final String LONGOSE = "Longose";
    public static final String TAPIA = "Tapia";
    public static final String CAFE = "Café";
    public static final String NIAOULI = "Niaouli";
    public static final String TAMARINDO = "Tamarindo";
    public static final String BAOBAB = "Baobab";
    public static final String MANGO = "Mango";
    public static final String MANGLE = "Mangle";
    public static final String RAKETA = "Raketa";
    public static final String JUJUBE = "Jujube";
    public static final String SISAL = "Sisal";

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
            LAVANDA_CAMPO,
            MOSTAZA,
            TREBOL,
            FACELIA,
            RABANIZA,
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
            BUCHU,
            PROTEA,
            BOEKENHOUT,
            AGUACATE,
            MARULA,
            GIROFLE,
            RAVINTSARA,
            LONGOSE,
            TAPIA,
            CAFE,
            NIAOULI,
            TAMARINDO,
            BAOBAB,
            MANGO,
            MANGLE,
            RAKETA,
            JUJUBE,
            SISAL,
    };

    /** Flora silvestre (nativa). Los cultivos no entran en el mix aleatorio del terreno. */
    private static final String[] ATLANTIC = {
            MIL_FLORES, CASTANO, "Brezo", EUCALIPTO, ARBOC, "Bosque"
    };
    private static final String[] MOUNTAIN = {
            MIL_FLORES, "Brezo", "Bosque", CASTANO, MIELATO, NERET, ARBOC
    };
    private static final String[] MEDITERRANEAN = {
            MIL_FLORES, "Romero", "Tomillo", "Lavanda", ARBOC, EUCALIPTO, "Bosque"
    };
    private static final String[] SOUTH = {
            MIL_FLORES, EUCALIPTO, "Romero", "Tomillo", CASTANO, ARBOC
    };
    private static final String[] CONTINENTAL = {
            MIL_FLORES, "Romero", "Tomillo", "Lavanda", MIELATO
    };

    private static final String[] ZA_FYNBOS = {
            FYNBOS, PROTEA, BUCHU, ALOE, EUCALIPTO, MIL_FLORES
    };
    private static final String[] ZA_KAROO = {
            ALOE, MIL_FLORES, EUCALIPTO
    };
    private static final String[] ZA_HIGHVELD = {
            EUCALIPTO, ACACIA, BOEKENHOUT, ALOE, MIL_FLORES
    };
    private static final String[] ZA_SUBTROPICAL = {
            EUCALIPTO, MIL_FLORES, "Bosque", ACACIA
    };
    private static final String[] ZA_BUSHVELD = {
            ALOE, ACACIA, MARULA, BOEKENHOUT, EUCALIPTO, MIL_FLORES, "Bosque"
    };
    private static final String[] MDG_EQUATORIAL = {
            LITCHI, GIROFLE, RAVINTSARA, LONGOSE, MIL_FLORES
    };
    private static final String[] MDG_HIGHLANDS = {
            EUCALIPTO, TAPIA, CAFE, NIAOULI, MIL_FLORES
    };
    private static final String[] MDG_TROPICAL = {
            TAMARINDO, BAOBAB, MANGO, MANGLE, MIL_FLORES
    };
    private static final String[] MDG_DESERT = {
            RAKETA, JUJUBE, SISAL, MIL_FLORES
    };

    private static final String[] PLANT_ATLANTIC = {
            "Campo de manzanos", "Campo de perales", "Campo de Colza",
            "Campo de girasoles", MOSTAZA, TREBOL, FACELIA, LAVANDA_CAMPO, RABANIZA
    };
    private static final String[] PLANT_MOUNTAIN = {
            "Campo de cerezos", "Campo de manzanos", "Campo de perales",
            LAVANDA_CAMPO, TREBOL, FACELIA, MOSTAZA, RABANIZA
    };
    private static final String[] PLANT_MEDITERRANEAN = {
            "Campo de naranjos", "Campo de almendros", "Campo de cerezos",
            "Campo de perales", "Campo de manzanos", "Campo de girasoles", "Campo de Colza",
            LAVANDA_CAMPO, MOSTAZA, TREBOL, FACELIA, RABANIZA
    };
    private static final String[] PLANT_SOUTH = {
            "Campo de naranjos", "Campo de almendros", "Campo de girasoles",
            "Campo de Colza", "Campo de cerezos", LAVANDA_CAMPO, MOSTAZA, TREBOL, FACELIA, RABANIZA
    };
    private static final String[] PLANT_CONTINENTAL = {
            "Campo de girasoles", "Campo de Colza", "Campo de almendros",
            "Campo de cerezos", "Campo de manzanos", "Campo de perales",
            LAVANDA_CAMPO, TREBOL, FACELIA, MOSTAZA, RABANIZA
    };
    private static final String[] PLANT_ZA_FYNBOS = {
            "Campo de Colza", "Campo de naranjos", "Campo de girasoles",
            LUCERNA, MOSTAZA, TREBOL, FACELIA, RABANIZA
    };
    private static final String[] PLANT_ZA_KAROO = {
            LUCERNA, "Campo de girasoles", MOSTAZA, TREBOL, RABANIZA
    };
    private static final String[] PLANT_ZA_HIGHVELD = {
            "Campo de girasoles", LUCERNA, "Campo de Colza", TREBOL, MOSTAZA, FACELIA, RABANIZA
    };
    private static final String[] PLANT_ZA_SUBTROPICAL = {
            LITCHI, MACADAMIA, AGUACATE, "Campo de naranjos", FACELIA, TREBOL, RABANIZA
    };
    private static final String[] PLANT_ZA_BUSHVELD = {
            "Campo de girasoles", LUCERNA, "Campo de Colza", TREBOL, MOSTAZA, RABANIZA
    };
    private static final String[] PLANT_MDG_EQUATORIAL = {
            LITCHI, "Campo de naranjos", FACELIA, TREBOL
    };
    private static final String[] PLANT_MDG_HIGHLANDS = {
            CAFE, "Campo de naranjos", TREBOL, FACELIA
    };
    private static final String[] PLANT_MDG_TROPICAL = {
            MANGO, "Campo de naranjos", TREBOL, RABANIZA
    };
    private static final String[] PLANT_MDG_DESERT = {
            SISAL, LUCERNA, MOSTAZA
    };

    private HexFlora() {
    }

    public static List<String> nativePoolForZone(IberianClimateZone zone) {
        return Collections.unmodifiableList(Arrays.asList(wildPool(zone)));
    }

    public static List<String> nativePoolForZone(SouthernAfricanClimateZone zone) {
        return Collections.unmodifiableList(Arrays.asList(wildPool(zone)));
    }

    public static List<String> plantationPoolForZone(IberianClimateZone zone) {
        return Collections.unmodifiableList(Arrays.asList(plantationPool(zone)));
    }

    public static List<String> plantationPoolForZone(SouthernAfricanClimateZone zone) {
        return Collections.unmodifiableList(Arrays.asList(plantationPool(zone)));
    }

    public static List<String> nativePoolForZone(MadagascarClimateZone zone) {
        return Collections.unmodifiableList(Arrays.asList(wildPool(zone)));
    }

    public static List<String> plantationPoolForZone(MadagascarClimateZone zone) {
        return Collections.unmodifiableList(Arrays.asList(plantationPool(zone)));
    }

    /** Cultivos que el jugador siembra; no salen en el mix silvestre inicial. */
    public static boolean isPlantation(String floraKey) {
        String k = canonicalKey(floraKey);
        return k.startsWith("Campo de")
                || MACADAMIA.equals(k)
                || LITCHI.equals(k)
                || AGUACATE.equals(k)
                || LUCERNA.equals(k)
                || CAFE.equals(k)
                || MANGO.equals(k)
                || SISAL.equals(k);
    }

    public static boolean isZaParcel(HexParcel parcel) {
        if (parcel == null) {
            return false;
        }
        if (parcel.id != null && (parcel.id.startsWith("hex_za_") || parcel.id.startsWith("za_"))) {
            return true;
        }
        return PlayableMapRegion.fromHexId(parcel.id) == PlayableMapRegion.SOUTH_AFRICA
                || PlayableMapRegion.containing(parcel.centroidLat, parcel.centroidLon)
                == PlayableMapRegion.SOUTH_AFRICA;
    }

    public static boolean isMadagascarParcel(HexParcel parcel) {
        if (parcel == null) {
            return false;
        }
        if (parcel.id != null && (parcel.id.startsWith("hex_mdg_") || parcel.id.startsWith("mdg_"))) {
            return true;
        }
        return PlayableMapRegion.fromHexId(parcel.id) == PlayableMapRegion.MADAGASCAR
                || PlayableMapRegion.containing(parcel.centroidLat, parcel.centroidLon)
                == PlayableMapRegion.MADAGASCAR;
    }

    public static boolean isSouthernParcel(HexParcel parcel) {
        if (parcel == null) {
            return false;
        }
        if (isZaParcel(parcel) || isMadagascarParcel(parcel)) {
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
                || LITCHI.equals(k) || LUCERNA.equals(k) || ACACIA.equals(k)
                || BUCHU.equals(k) || PROTEA.equals(k) || BOEKENHOUT.equals(k)
                || AGUACATE.equals(k) || MARULA.equals(k)
                || GIROFLE.equals(k) || RAVINTSARA.equals(k) || LONGOSE.equals(k)
                || TAPIA.equals(k) || CAFE.equals(k) || NIAOULI.equals(k)
                || TAMARINDO.equals(k) || BAOBAB.equals(k) || MANGO.equals(k)
                || MANGLE.equals(k) || RAKETA.equals(k) || JUJUBE.equals(k)
                || SISAL.equals(k);
    }

    public static boolean isAllowedInZone(String floraKey, IberianClimateZone zone) {
        return containsKey(wildPool(zone), floraKey) || containsKey(plantationPool(zone), floraKey);
    }

    public static boolean isAllowedInZone(String floraKey, SouthernAfricanClimateZone zone) {
        return containsKey(wildPool(zone), floraKey) || containsKey(plantationPool(zone), floraKey);
    }

    public static boolean isAllowedInZone(String floraKey, MadagascarClimateZone zone) {
        return containsKey(wildPool(zone), floraKey) || containsKey(plantationPool(zone), floraKey);
    }

    public static boolean isAllowedOnParcel(String floraKey, HexParcel parcel) {
        if (isMadagascarParcel(parcel)) {
            return isAllowedInZone(floraKey, MadagascarClimateZone.forParcel(parcel));
        }
        if (isZaParcel(parcel)) {
            return isAllowedInZone(floraKey, SouthernAfricanClimateZone.forParcel(parcel));
        }
        return isAllowedInZone(floraKey, IberianClimateZone.forParcel(parcel));
    }

    /** Floras silvestres posibles en Iberia o en Sudáfrica, sin cultivos. */
    public static List<String> nativeKeysForRegion(boolean southern) {
        return nativeKeysForRegion(southern ? PlayableMapRegion.SOUTH_AFRICA : PlayableMapRegion.IBERIA);
    }

    public static List<String> nativeKeysForRegion(PlayableMapRegion region) {
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        if (region == PlayableMapRegion.SOUTH_AFRICA) {
            for (SouthernAfricanClimateZone zone : SouthernAfricanClimateZone.values()) {
                keys.addAll(nativePoolForZone(zone));
            }
        } else if (region == PlayableMapRegion.MADAGASCAR) {
            for (MadagascarClimateZone zone : MadagascarClimateZone.values()) {
                keys.addAll(nativePoolForZone(zone));
            }
        } else {
            for (IberianClimateZone zone : IberianClimateZone.values()) {
                keys.addAll(nativePoolForZone(zone));
            }
        }
        return Collections.unmodifiableList(new ArrayList<>(keys));
    }

    public static String nativeFloraForParcel(HexParcel parcel) {
        List<String> mix = nativeMixForParcel(parcel);
        for (int i = 0; i < mix.size(); i++) {
            if (!MIL_FLORES.equals(mix.get(i))) {
                return mix.get(i);
            }
        }
        return mix.isEmpty() ? MIL_FLORES : mix.get(0);
    }

    /**
     * Mix silvestre estable del hex: siempre incluye mil flores, más 2–4 especies del clima
     * (total 3–5 si el pool lo permite). Sin cultivos.
     */
    public static List<String> nativeMixForParcel(HexParcel parcel) {
        if (parcel == null) {
            return Collections.singletonList(MIL_FLORES);
        }
        if (isMadagascarParcel(parcel)) {
            return nativeMixForZone(parcel.id, MadagascarClimateZone.forParcel(parcel));
        }
        if (isZaParcel(parcel)) {
            return nativeMixForZone(parcel.id, SouthernAfricanClimateZone.forParcel(parcel));
        }
        return nativeMixForZone(parcel.id, IberianClimateZone.forParcel(parcel));
    }

    public static List<String> nativeMixForZone(String hexId, MadagascarClimateZone zone) {
        return buildNativeMix(hexId, wildPool(zone));
    }

    public static List<String> nativeMixForZone(String hexId, IberianClimateZone zone) {
        return buildNativeMix(hexId, wildPool(zone));
    }

    public static List<String> nativeMixForZone(String hexId, SouthernAfricanClimateZone zone) {
        return buildNativeMix(hexId, wildPool(zone));
    }

    public static boolean nativeMixContains(HexParcel parcel, String floraKey) {
        String want = canonicalKey(floraKey);
        List<String> mix = nativeMixForParcel(parcel);
        for (int i = 0; i < mix.size(); i++) {
            if (want.equals(mix.get(i))) {
                return true;
            }
        }
        return false;
    }

    public static List<String> plantationPoolForParcel(HexParcel parcel) {
        if (parcel == null) {
            return Collections.emptyList();
        }
        if (isMadagascarParcel(parcel)) {
            return plantationPoolForZone(MadagascarClimateZone.forParcel(parcel));
        }
        if (isZaParcel(parcel)) {
            return plantationPoolForZone(SouthernAfricanClimateZone.forParcel(parcel));
        }
        return plantationPoolForZone(IberianClimateZone.forParcel(parcel));
    }

    private static List<String> buildNativeMix(String hexId, String[] wild) {
        LinkedHashSet<String> extras = new LinkedHashSet<>();
        if (wild != null) {
            for (int i = 0; i < wild.length; i++) {
                String k = canonicalKey(wild[i]);
                if (!MIL_FLORES.equals(k) && !isPlantation(k)) {
                    extras.add(k);
                }
            }
        }
        List<String> extraList = new ArrayList<>(extras);
        Random r = new Random(stableHash64("mix:" + (hexId != null ? hexId : "")));
        Collections.shuffle(extraList, r);
        int extraCount = extraList.isEmpty() ? 0 : Math.min(extraList.size(), 2 + r.nextInt(3));
        LinkedHashSet<String> mix = new LinkedHashSet<>();
        mix.add(MIL_FLORES);
        for (int i = 0; i < extraCount; i++) {
            mix.add(extraList.get(i));
        }
        return Collections.unmodifiableList(new ArrayList<>(mix));
    }

    /**
     * Índice pseudoaleatorio en el pool de la zona, reproducible para el mismo {@code hexId}.
     */
    public static String randomNativeForZone(String hexId, IberianClimateZone zone) {
        return pickFromPool(hexId, wildPool(zone));
    }

    public static String randomNativeForZone(String hexId, SouthernAfricanClimateZone zone) {
        return pickFromPool(hexId, wildPool(zone));
    }

    public static String randomNativeForZone(String hexId, MadagascarClimateZone zone) {
        return pickFromPool(hexId, wildPool(zone));
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
        if (lower.equals("arboç") || lower.equals("arboc") || lower.equals("arbós") || lower.equals("arbos")
                || lower.equals("madroño") || lower.equals("madrono") || lower.equals("madroñer")
                || lower.equals("arboçer") || lower.equals("strawberry tree")) {
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
        if (lower.equals("protea") || lower.equals("protéa") || lower.contains("king protea")) {
            return PROTEA;
        }
        if (lower.equals("fynbos") || lower.equals("cape flora")) {
            return FYNBOS;
        }
        if (lower.equals("buchu") || lower.equals("buchú") || lower.equals("agathosma")) {
            return BUCHU;
        }
        if (lower.equals("boekenhout") || lower.equals("faurea")
                || lower.equals("haya africana") || lower.equals("african beech")) {
            return BOEKENHOUT;
        }
        if (lower.equals("aguacate") || lower.equals("avocado") || lower.equals("palta")) {
            return AGUACATE;
        }
        if (lower.equals("marula") || lower.startsWith("sclerocarya")) {
            return MARULA;
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
        if (lower.equals("campo de lavanda")) {
            return LAVANDA_CAMPO;
        }
        if (lower.contains("mostaza") || lower.contains("mustard")) {
            return MOSTAZA;
        }
        if (lower.contains("trébol") || lower.contains("trebol") || lower.contains("clover")) {
            return TREBOL;
        }
        if (lower.contains("facelia") || lower.contains("phacelia")) {
            return FACELIA;
        }
        if (lower.contains("rabaniza") || lower.contains("diplotaxis") || lower.contains("jaramago")) {
            return RABANIZA;
        }
        if (lower.equals("acacia") || lower.equals("wattle") || lower.startsWith("acacia")) {
            return ACACIA;
        }
        if (lower.equals("girofle") || lower.equals("clavo") || lower.contains("syzygium aromaticum")) {
            return GIROFLE;
        }
        if (lower.equals("ravintsara") || lower.contains("cinnamomum camphora")) {
            return RAVINTSARA;
        }
        if (lower.equals("longose") || lower.equals("longoze") || lower.contains("hedychium")) {
            return LONGOSE;
        }
        if (lower.equals("tapia") || lower.contains("uapaca")) {
            return TAPIA;
        }
        if (lower.equals("café") || lower.equals("cafe") || lower.equals("coffee")) {
            return CAFE;
        }
        if (lower.equals("niaouli") || lower.contains("melaleuca")) {
            return NIAOULI;
        }
        if (lower.equals("tamarindo") || lower.equals("tamarind") || lower.equals("kily")) {
            return TAMARINDO;
        }
        if (lower.equals("baobab") || lower.contains("adansonia")) {
            return BAOBAB;
        }
        if (lower.equals("mango") || lower.contains("mangifera")) {
            return MANGO;
        }
        if (lower.equals("mangle") || lower.equals("manglar") || lower.equals("mangrove")) {
            return MANGLE;
        }
        if (lower.equals("raketa") || lower.equals("higo chumbo") || lower.contains("opuntia")) {
            return RAKETA;
        }
        if (lower.equals("jujube") || lower.equals("azufaifo") || lower.contains("ziziphus")) {
            return JUJUBE;
        }
        if (lower.equals("sisal") || lower.contains("agave sisalana")) {
            return SISAL;
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

    private static String[] wildPool(IberianClimateZone zone) {
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

    private static String[] wildPool(SouthernAfricanClimateZone zone) {
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

    private static String[] wildPool(MadagascarClimateZone zone) {
        if (zone == null) {
            return MDG_TROPICAL;
        }
        switch (zone) {
            case EQUATORIAL:
                return MDG_EQUATORIAL;
            case HIGHLANDS:
                return MDG_HIGHLANDS;
            case DESERT:
                return MDG_DESERT;
            case TROPICAL:
            default:
                return MDG_TROPICAL;
        }
    }

    private static String[] plantationPool(IberianClimateZone zone) {
        if (zone == null) {
            return PLANT_CONTINENTAL;
        }
        switch (zone) {
            case ATLANTIC:
                return PLANT_ATLANTIC;
            case MOUNTAIN:
                return PLANT_MOUNTAIN;
            case MEDITERRANEAN:
                return PLANT_MEDITERRANEAN;
            case SOUTH:
                return PLANT_SOUTH;
            case CONTINENTAL:
            default:
                return PLANT_CONTINENTAL;
        }
    }

    private static String[] plantationPool(SouthernAfricanClimateZone zone) {
        if (zone == null) {
            return PLANT_ZA_HIGHVELD;
        }
        switch (zone) {
            case FYNBOS:
                return PLANT_ZA_FYNBOS;
            case KAROO:
                return PLANT_ZA_KAROO;
            case SUBTROPICAL:
                return PLANT_ZA_SUBTROPICAL;
            case BUSHVELD:
                return PLANT_ZA_BUSHVELD;
            case HIGHVELD:
            default:
                return PLANT_ZA_HIGHVELD;
        }
    }

    private static String[] plantationPool(MadagascarClimateZone zone) {
        if (zone == null) {
            return PLANT_MDG_TROPICAL;
        }
        switch (zone) {
            case EQUATORIAL:
                return PLANT_MDG_EQUATORIAL;
            case HIGHLANDS:
                return PLANT_MDG_HIGHLANDS;
            case DESERT:
                return PLANT_MDG_DESERT;
            case TROPICAL:
            default:
                return PLANT_MDG_TROPICAL;
        }
    }

    private static String[] pool(IberianClimateZone zone) {
        return concat(wildPool(zone), plantationPool(zone));
    }

    private static String[] pool(SouthernAfricanClimateZone zone) {
        return concat(wildPool(zone), plantationPool(zone));
    }

    private static String[] concat(String[] a, String[] b) {
        String[] out = new String[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
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
