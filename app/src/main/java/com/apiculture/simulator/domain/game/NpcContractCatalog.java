package com.apiculture.simulator.domain.game;

import androidx.annotation.Nullable;

import com.apiculture.simulator.domain.map.PlayableMapRegion;
import com.apiculture.simulator.domain.parcel.HexFlora;
import com.apiculture.simulator.domain.parcel.HexParcel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Catálogo determinista de fincas NPC (~2,5 % de hexes, sesgo agrícola).
 * El mismo {@code hexId} es amarillo para todos los jugadores.
 * Nombre y cara van ligados al género y a la procedencia del hex.
 */
public final class NpcContractCatalog {

    public static final int CONTRACTS_PER_FARM = 6;
    /** Anclas de floración en Iberia: nov–feb (rabaniza) y feb–nov. */
    private static final int[] IBERIA_SEASON_DOY = {1, 46, 105, 166, 227, 288};
    /** Anclas en Sudáfrica: invierno jun–ago (rabaniza) y sep–jun. */
    private static final int[] ZA_SEASON_DOY = {184, 244, 305, 15, 74, 135};

    /**
     * Índice = retrato {@code npc_face_XX}. No reordenar: las caras 0–15 ya existen.
     */
    private static final String[] NPC_NAMES = {
            "Núria Soler", "Vicente Ferrer", "Elena Martín", "Amaia Lezeaga", "Thabo Mokoena",
            "João Ferreira", "Carmen Ríos", "Iker Arana", "Fatima El Amrani", "Pieter van Zyl",
            "Laia Puig", "Manuel Ortega", "Sofia Almeida", "Nomsa Dlamini", "Ander Urrutia",
            "Rosa Beltrán", "Mei Lin", "Wei Chen", "Yuki Tanaka", "Hiroshi Nakamura",
            "Alba Cruz", "Nico Vidal", "Priya Naidoo", "Sipho Ndlovu"
    };

    private static final String[] ESTATE_MED = {
            "Huerta de Levante", "Ribera del Xúquer", "Secano de Ponent", "Camp d'Elx"
    };
    private static final String[] ESTATE_SOUTH = {
            "Vega del Guadalquivir", "Campiña jienense", "Llanos de Antequera"
    };
    private static final String[] ESTATE_CONT = {
            "Páramo de La Mancha", "Tierra de Campos", "Alcarria"
    };
    private static final String[] ESTATE_ATL = {
            "Ribeira Sacra", "Mariña lucense", "Baixo Minho"
    };
    private static final String[] ESTATE_MTN = {
            "Prepirineo", "Sierra de Gredos", "Valle del Jerte"
    };
    private static final String[] ESTATE_ZA_SUB = {
            "Lowveld", "Costa de KwaZulu", "Huerta de Mpumalanga"
    };
    private static final String[] ESTATE_ZA_HV = {
            "Highveld", "Meseta de Gauteng"
    };
    private static final String[] ESTATE_ZA_BUSH = {
            "Bushveld", "Limpopo interior"
    };
    private static final String[] ESTATE_ZA_KAROO = {
            "Karoo", "Karoo semiárido"
    };
    private static final String[] ESTATE_ZA_FYN = {
            "Overberg", "Swartland", "Cabo de Agulhas"
    };

    private NpcContractCatalog() {
    }

    @Nullable
    public static NpcContractFarm farmFor(@Nullable HexParcel parcel) {
        List<NpcContractFarm> all = farmsFor(parcel);
        return all.isEmpty() ? null : all.get(0);
    }

    @Nullable
    public static NpcContractFarm farmFor(@Nullable HexParcel parcel, @Nullable String flora) {
        return farmFor(parcel, flora, 0, java.time.LocalDate.now());
    }

    @Nullable
    public static NpcContractFarm farmFor(@Nullable HexParcel parcel, @Nullable String flora, int startDoy,
            @Nullable java.time.LocalDate today) {
        if (startDoy > 0) {
            return farmForSlot(parcel, flora, startDoy);
        }
        java.time.LocalDate day = today != null ? today : java.time.LocalDate.now();
        String k = HexFlora.canonicalKey(flora);
        List<NpcContractFarm> open = fieldOffersFor(parcel, day);
        for (int i = 0; i < open.size(); i++) {
            NpcContractFarm farm = open.get(i);
            if (farm != null && k.equals(HexFlora.canonicalKey(farm.flora))) {
                return farm;
            }
        }
        return farmFor(parcel);
    }

    @Nullable
    public static NpcContractFarm farmForSlot(@Nullable HexParcel parcel, @Nullable String flora, int startDoy) {
        if (parcel == null || flora == null || startDoy <= 0) {
            return null;
        }
        List<int[]> slots = packSlots(parcel, flora);
        for (int i = 0; i < slots.size(); i++) {
            int[] slot = slots.get(i);
            if (slot[0] == startDoy) {
                return makeFarm(parcel, flora, slot[2], slot[0], slot[1]);
            }
        }
        return null;
    }

    /**
     * Tramo publicado por el servidor. La tabla local de floración puede no
     * coincidir con ese inicio; la fecha que se muestra es la de la oferta.
     */
    @Nullable
    public static NpcContractFarm farmForPublished(@Nullable HexParcel parcel, @Nullable String flora,
            int startDoy, int endDoy) {
        if (parcel == null || flora == null || flora.isEmpty() || startDoy <= 0 || endDoy <= 0) {
            return null;
        }
        int days = FloraBloomWindow.daysInSpan(new FloraBloomWindow.Span(startDoy, endDoy));
        if (days < 2) {
            return null;
        }
        return makeFarm(parcel, flora, days, startDoy, endDoy);
    }

    /**
     * Siguiente tramo que aún no ha empezado. El que incluye hoy, aunque arranque hoy, no vale.
     */
    @Nullable
    public static NpcContractFarm farmStartingAfter(@Nullable HexParcel parcel, @Nullable String flora,
            @Nullable java.time.LocalDate today) {
        if (parcel == null || flora == null || flora.isEmpty()) {
            return null;
        }
        List<int[]> slots = packSlots(parcel, flora);
        if (slots.isEmpty()) {
            return null;
        }
        java.time.LocalDate day = today != null ? today : java.time.LocalDate.now();
        int doy = Math.min(365, day.getDayOfYear());
        int[] best = null;
        int bestWait = Integer.MAX_VALUE;
        for (int s = 0; s < slots.size(); s++) {
            int[] slot = slots.get(s);
            FloraBloomWindow.Span span = new FloraBloomWindow.Span(slot[0], slot[1]);
            if (FloraBloomWindow.containsDoy(span, doy)) {
                continue;
            }
            int until = FloraBloomWindow.daysUntilStart(slot[0], doy);
            if (until < 1 || until >= bestWait) {
                continue;
            }
            bestWait = until;
            best = slot;
        }
        if (best == null) {
            return null;
        }
        return makeFarm(parcel, flora, best[2], best[0], best[1]);
    }

    /** Tramo en curso o el siguiente de un cultivo concreto. */
    @Nullable
    public static NpcContractFarm farmUpcoming(@Nullable HexParcel parcel, @Nullable String flora,
            @Nullable java.time.LocalDate today) {
        if (parcel == null || flora == null || flora.isEmpty()) {
            return null;
        }
        List<int[]> slots = packSlots(parcel, flora);
        if (slots.isEmpty()) {
            return makeFarm(parcel, flora, PollinationContractRules.workDays(parcel, flora), 0, 0);
        }
        java.time.LocalDate day = today != null ? today : java.time.LocalDate.now();
        int doy = Math.min(365, day.getDayOfYear());
        int[] best = slots.get(0);
        int bestWait = Integer.MAX_VALUE;
        for (int s = 0; s < slots.size(); s++) {
            int[] slot = slots.get(s);
            FloraBloomWindow.Span span = new FloraBloomWindow.Span(slot[0], slot[1]);
            int wait;
            if (FloraBloomWindow.containsDoy(span, doy)) {
                wait = 0;
            } else if (slot[0] <= slot[1] && doy > slot[1]) {
                wait = 365 - doy + slot[0];
            } else {
                wait = FloraBloomWindow.daysUntilStart(slot[0], doy);
            }
            if (wait < bestWait) {
                bestWait = wait;
                best = slot;
            }
        }
        return makeFarm(parcel, flora, best[2], best[0], best[1]);
    }

    private static NpcContractFarm makeFarm(HexParcel parcel, String flora, int days, int startDoy, int endDoy) {
        String npc = npcNameFor(parcel);
        PollinationPayTerms terms = PollinationContractRules.termsForFlora(flora, days, startDoy, endDoy,
                parcel != null ? parcel.id : null);
        return new NpcContractFarm(parcel, flora, npc, estateNameFor(parcel),
                ClimateUnlock.climateLabelForParcel(parcel), isReserve(parcel.id), terms, portraitIndexFor(npc));
    }

    public static List<NpcContractFarm> farmsFor(@Nullable HexParcel parcel) {
        if (!isNpcFarm(parcel)) {
            return Collections.emptyList();
        }
        return farmsFromCrops(parcel, pickSeasonCrops(parcel, CONTRACTS_PER_FARM), false, null);
    }

    /** Ofertas de cultivo en cualquier hex (puntos de mapa; ya no hace falta finca amarilla). */
    public static List<NpcContractFarm> fieldOffersFor(@Nullable HexParcel parcel,
            @Nullable java.time.LocalDate today) {
        return farmsFromCrops(parcel, pickSeasonCrops(parcel, CONTRACTS_PER_FARM), false, today);
    }

    /**
     * Tramos de ≥40 % de floración que empiezan hoy o en el horizonte (no los ya empezados).
     */
    public static List<NpcContractFarm> openFarmsFor(@Nullable HexParcel parcel,
            @Nullable java.time.LocalDate today) {
        return farmsFromCrops(parcel, pickSeasonCrops(parcel, CONTRACTS_PER_FARM), true, today);
    }

    private static List<NpcContractFarm> farmsFromCrops(
            @Nullable HexParcel parcel,
            List<String> crops,
            boolean horizonOnly,
            @Nullable java.time.LocalDate today) {
        if (parcel == null || crops == null || crops.isEmpty()) {
            return Collections.emptyList();
        }
        String npc = npcNameFor(parcel);
        String estate = estateNameFor(parcel);
        String climate = ClimateUnlock.climateLabelForParcel(parcel);
        boolean reserve = isReserve(parcel.id);
        int portrait = portraitIndexFor(npc);
        java.time.LocalDate day = today != null ? today : java.time.LocalDate.now();
        int doy = Math.min(365, day.getDayOfYear());
        int horizon = PollinationContractRules.horizonDays();
        int minDays = Math.max(2, GameBalanceConfig.pollinationSlotDaysMin);
        List<NpcContractFarm> out = new ArrayList<>();
        for (int i = 0; i < crops.size(); i++) {
            String flora = crops.get(i);
            List<int[]> slots = packSlots(parcel, flora);
            if (slots.isEmpty()) {
                if (horizonOnly) {
                    continue;
                }
                PollinationPayTerms terms = PollinationContractRules.termsFor(parcel, flora);
                out.add(new NpcContractFarm(parcel, flora, npc, estate, climate, reserve, terms, portrait));
                continue;
            }
            boolean added = false;
            for (int s = 0; s < slots.size(); s++) {
                int[] slot = slots.get(s);
                int start = slot[0];
                int end = slot[1];
                int until = FloraBloomWindow.daysUntilStart(start, doy);
                int days = slot[2];
                if (days < minDays) {
                    continue;
                }
                if (horizonOnly && (until > horizon || start < doy && until == 0)) {
                    continue;
                }
                if (horizonOnly || !added) {
                    PollinationPayTerms terms = PollinationContractRules.termsForFlora(
                            flora, days, start, end, parcel.id);
                    out.add(new NpcContractFarm(parcel, flora, npc, estate, climate, reserve, terms, portrait));
                    added = true;
                    if (!horizonOnly) {
                        break;
                    }
                }
            }
            if (!added && !horizonOnly) {
                int[] first = slots.get(0);
                PollinationPayTerms terms = PollinationContractRules.termsForFlora(
                        flora, first[2], first[0], first[1]);
                out.add(new NpcContractFarm(parcel, flora, npc, estate, climate, reserve, terms, portrait));
            }
        }
        return out;
    }

    static List<int[]> packSlots(@Nullable HexParcel parcel, @Nullable String flora) {
        int shift = HexNectarRules.bloomShiftDaysForParcel(parcel, flora);
        List<FloraBloomWindow.Span> windows =
                FloraBloomWindow.spansAtLeast(flora, shift, PollinationContractRules.MIN_BLOOM01);
        List<int[]> out = new ArrayList<>();
        String hex = parcel != null && parcel.id != null ? parcel.id : "";
        int slot = 0;
        int minDays = Math.max(2, GameBalanceConfig.pollinationSlotDaysMin);
        for (int w = 0; w < windows.size(); w++) {
            FloraBloomWindow.Span window = windows.get(w);
            int remaining = FloraBloomWindow.daysInSpan(window);
            int cursor = window.startDoy;
            while (remaining >= minDays) {
                int want = slotWorkDays(hex, flora, slot);
                int take = Math.min(want, remaining);
                if (take < minDays) {
                    break;
                }
                int end = FloraBloomWindow.addDays(cursor, take - 1);
                out.add(new int[]{cursor, end, take});
                cursor = FloraBloomWindow.addDays(end, 1);
                remaining -= take;
                slot++;
            }
        }
        return out;
    }

    static int slotWorkDays(@Nullable String hexId, @Nullable String flora, int slotIndex) {
        int min = Math.max(2, GameBalanceConfig.pollinationSlotDaysMin);
        int max = Math.max(min, GameBalanceConfig.pollinationSlotDaysMax);
        long h = Math.abs(stableHash64("npc-slot:" + (hexId != null ? hexId : "")
                + ":" + HexFlora.canonicalKey(flora) + ":" + slotIndex));
        return min + (int) (h % (max - min + 1));
    }

    @Nullable
    public static NpcContractFarm nextUpcoming(@Nullable List<NpcContractFarm> farms, @Nullable java.time.LocalDate today) {
        if (farms == null || farms.isEmpty()) {
            return null;
        }
        java.time.LocalDate day = today != null ? today : java.time.LocalDate.now();
        NpcContractFarm best = farms.get(0);
        int bestDays = Integer.MAX_VALUE;
        for (int i = 0; i < farms.size(); i++) {
            NpcContractFarm farm = farms.get(i);
            if (farm == null) {
                continue;
            }
            int d;
            if (farm.terms != null && farm.terms.startDoy > 0) {
                int doy = Math.min(365, day.getDayOfYear());
                if (farm.terms.endDoy > 0 && FloraBloomWindow.containsDoy(
                        new FloraBloomWindow.Span(farm.terms.startDoy, farm.terms.endDoy), doy)) {
                    d = 0;
                } else {
                    d = FloraBloomWindow.daysUntilStart(farm.terms.startDoy, doy);
                }
            } else {
                d = FloraBloomWindow.daysUntilBloomStart(farm.flora, farm.parcel, day);
            }
            if (d < bestDays) {
                bestDays = d;
                best = farm;
            }
        }
        return best;
    }

    public static boolean isNpcFarm(@Nullable HexParcel parcel) {
        if (parcel == null || parcel.id == null || parcel.id.isEmpty()) {
            return false;
        }
        List<String> pool = HexFlora.plantationPoolForParcel(parcel);
        if (pool == null || pool.isEmpty()) {
            return false;
        }
        int permille = densityPermille(parcel);
        if (parcel.coastal) {
            permille = Math.max(4, (int) Math.round(permille * 0.45));
        }
        long h = Math.abs(stableHash64("npc-farm:" + parcel.id));
        return (h % 1000L) < permille;
    }

    public static boolean isReserve(@Nullable String hexId) {
        if (hexId == null || hexId.isEmpty()) {
            return false;
        }
        int pct = Math.max(0, Math.min(90, GameBalanceConfig.pollinationReservePct));
        long h = Math.abs(stableHash64("npc-reserve:" + hexId));
        return (h % 100L) < pct;
    }

    public static String floraFor(HexParcel parcel) {
        List<String> crops = pickSeasonCrops(parcel, CONTRACTS_PER_FARM);
        if (crops.isEmpty()) {
            return "Campo de girasoles";
        }
        return crops.get(0);
    }

    static List<String> pickSeasonCrops(@Nullable HexParcel parcel, int want) {
        int n = Math.max(1, want);
        List<String> pool = new ArrayList<>();
        List<String> zone = PollinationContractCrops.forParcel(parcel);
        if (zone != null) {
            pool.addAll(zone);
        }
        if (pool.isEmpty()) {
            List<String> planted = HexFlora.plantationPoolForParcel(parcel);
            if (planted != null) {
                pool.addAll(planted);
            }
        }
        if (pool.size() < n) {
            for (String extra : HexFlora.FLORA_TYPES) {
                if (!HexFlora.isPlantation(extra) || pool.contains(extra)) {
                    continue;
                }
                if (parcel != null && HexFlora.isAllowedOnParcel(extra, parcel)) {
                    pool.add(extra);
                }
            }
        }
        if (pool.isEmpty()) {
            pool.add("Campo de girasoles");
        }
        int[] targets = HexFlora.isSouthernParcel(parcel) ? ZA_SEASON_DOY : IBERIA_SEASON_DOY;
        List<String> picked = new ArrayList<>();
        for (int t = 0; t < targets.length && picked.size() < n; t++) {
            String best = null;
            int bestDist = Integer.MAX_VALUE;
            for (int i = 0; i < pool.size(); i++) {
                String crop = pool.get(i);
                if (picked.contains(crop)) {
                    continue;
                }
                int dist = doyDistance(bloomCenterDoy(parcel, crop), targets[t]);
                if (dist < bestDist) {
                    bestDist = dist;
                    best = crop;
                }
            }
            if (best != null) {
                picked.add(best);
            }
        }
        for (int i = 0; i < pool.size() && picked.size() < n; i++) {
            String crop = pool.get(i);
            if (!picked.contains(crop)) {
                picked.add(crop);
            }
        }
        return picked;
    }

    static int bloomCenterDoy(@Nullable HexParcel parcel, @Nullable String flora) {
        List<GameBalanceConfig.NectarPeak> peaks =
                GameBalanceConfig.peaksForFlora(HexFlora.canonicalKey(flora));
        int center = 120;
        if (peaks != null && !peaks.isEmpty()) {
            center = peaks.get(0).center;
        }
        int d = center + HexNectarRules.bloomShiftDaysForParcel(parcel, flora);
        while (d < 1) {
            d += 365;
        }
        while (d > 365) {
            d -= 365;
        }
        return d;
    }

    static int doyDistance(int a, int b) {
        int d = Math.abs(a - b);
        return Math.min(d, 365 - d);
    }

    public static String npcNameFor(@Nullable HexParcel parcel) {
        String hexId = parcel != null && parcel.id != null ? parcel.id : "";
        int[] pool = personaPool(parcel);
        long h = Math.abs(stableHash64("npc-name:" + hexId));
        int idx = pool[(int) (h % pool.length)];
        return NPC_NAMES[idx];
    }

    public static int portraitIndexFor(@Nullable String npcName) {
        if (npcName != null) {
            for (int i = 0; i < NPC_NAMES.length; i++) {
                if (NPC_NAMES[i].equals(npcName)) {
                    return i;
                }
            }
        }
        long h = Math.abs(stableHash64("npc-face:" + (npcName != null ? npcName : "")));
        return (int) (h % NPC_NAMES.length);
    }

    public static String climateKey(@Nullable HexParcel parcel) {
        if (parcel == null) {
            return "CONTINENTAL";
        }
        if (HexFlora.isSouthernParcel(parcel)) {
            return SouthernAfricanClimateZone.forParcel(parcel).name();
        }
        return IberianClimateZone.forParcel(parcel).name();
    }

    public static String occupancyBucket(@Nullable HexParcel parcel, @Nullable String flora) {
        PlayableMapRegion region = PlayableMapRegion.fromHexId(parcel != null ? parcel.id : null);
        String zone = climateKey(parcel);
        String crop = HexFlora.canonicalKey(flora);
        return (region != null ? region.prefsValue() : "iberia") + "|" + zone + "|" + crop;
    }

    public static String estateNameFor(HexParcel parcel) {
        if (parcel != null && parcel.placeName != null && !parcel.placeName.trim().isEmpty()) {
            return parcel.placeName.trim();
        }
        String[] pool;
        if (HexFlora.isSouthernParcel(parcel)) {
            switch (SouthernAfricanClimateZone.forParcel(parcel)) {
                case SUBTROPICAL:
                    pool = ESTATE_ZA_SUB;
                    break;
                case HIGHVELD:
                    pool = ESTATE_ZA_HV;
                    break;
                case BUSHVELD:
                    pool = ESTATE_ZA_BUSH;
                    break;
                case KAROO:
                    pool = ESTATE_ZA_KAROO;
                    break;
                case FYNBOS:
                default:
                    pool = ESTATE_ZA_FYN;
                    break;
            }
        } else {
            switch (IberianClimateZone.forParcel(parcel)) {
                case MEDITERRANEAN:
                    pool = ESTATE_MED;
                    break;
                case SOUTH:
                    pool = ESTATE_SOUTH;
                    break;
                case ATLANTIC:
                    pool = ESTATE_ATL;
                    break;
                case MOUNTAIN:
                    pool = ESTATE_MTN;
                    break;
                case CONTINENTAL:
                default:
                    pool = ESTATE_CONT;
                    break;
            }
        }
        long h = Math.abs(stableHash64("npc-estate:" + parcel.id));
        return pool[(int) (h % pool.length)];
    }

    /**
     * Permil de hexes amarillos por zona. Media ~25 ‰ (2,5 %), más en huerta/meseta.
     */
    private static int densityPermille(HexParcel parcel) {
        if (HexFlora.isSouthernParcel(parcel)) {
            switch (SouthernAfricanClimateZone.forParcel(parcel)) {
                case SUBTROPICAL:
                    return 68;
                case HIGHVELD:
                    return 40;
                case BUSHVELD:
                    return 46;
                case KAROO:
                    return 16;
                case FYNBOS:
                default:
                    return 30;
            }
        }
        switch (IberianClimateZone.forParcel(parcel)) {
            case MEDITERRANEAN:
                return 70;
            case SOUTH:
                return 76;
            case CONTINENTAL:
                return 52;
            case ATLANTIC:
                return 26;
            case MOUNTAIN:
            default:
                return 14;
        }
    }

    /**
     * Caras ligadas al lugar: ibéricas por zona, sudafricanas en ZA,
     * diáspora china/japonesa en Levante y meseta, punk en ciudades del este/sur.
     */
    private static int[] personaPool(@Nullable HexParcel parcel) {
        if (parcel != null && HexFlora.isSouthernParcel(parcel)) {
            switch (SouthernAfricanClimateZone.forParcel(parcel)) {
                case FYNBOS:
                    return new int[]{9, 22, 13, 4, 19, 18};
                case KAROO:
                    return new int[]{9, 4, 23};
                case SUBTROPICAL:
                    return new int[]{13, 23, 22, 4};
                case BUSHVELD:
                    return new int[]{4, 23, 13};
                case HIGHVELD:
                default:
                    return new int[]{4, 23, 13, 9};
            }
        }
        double lat = parcel != null ? parcel.centroidLat : 40.0;
        double lon = parcel != null ? parcel.centroidLon : -3.0;
        if (lat >= 42.35 && lon >= -3.2 && lon <= -1.15) {
            return new int[]{3, 7, 14, 2};
        }
        if (lon <= -6.7 && lat >= 41.7) {
            return new int[]{5, 12, 15, 21};
        }
        if (lon <= -6.7) {
            return new int[]{5, 12, 16, 17, 21};
        }
        if (lat < 36.9 || (lat < 37.45 && lon > -6.2 && lon < -2.0)) {
            return new int[]{8, 6, 11, 15, 20};
        }
        switch (IberianClimateZone.forParcel(parcel)) {
            case MEDITERRANEAN:
                return new int[]{0, 1, 10, 16, 17, 21, 18, 6};
            case SOUTH:
                return new int[]{6, 11, 15, 8, 20, 16, 17};
            case ATLANTIC:
                return new int[]{5, 12, 15, 21};
            case MOUNTAIN:
                return new int[]{3, 7, 14, 2, 20};
            case CONTINENTAL:
            default:
                return new int[]{2, 11, 15, 19, 20, 16, 17};
        }
    }

    static int bloomQuarter(@Nullable HexParcel parcel, @Nullable String flora) {
        List<GameBalanceConfig.NectarPeak> peaks =
                GameBalanceConfig.peaksForFlora(HexFlora.canonicalKey(flora));
        int center = 120;
        if (peaks != null && !peaks.isEmpty()) {
            center = peaks.get(0).center;
        }
        int d = center + HexNectarRules.bloomShiftDaysForParcel(parcel, flora);
        while (d < 1) {
            d += 365;
        }
        while (d > 365) {
            d -= 365;
        }
        if (d <= 91) {
            return 0;
        }
        if (d <= 182) {
            return 1;
        }
        if (d <= 273) {
            return 2;
        }
        return 3;
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
