package com.apiculture.simulator.domain.game;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.apiculture.simulator.data.local.entity.HoneyOrderEntity;
import com.apiculture.simulator.data.repository.IberiaHexOverlayStore;
import com.apiculture.simulator.domain.map.PlayableMapRegion;
import com.apiculture.simulator.domain.map.ProvincialMarket;
import com.apiculture.simulator.domain.map.ProvincialMarketCatalog;
import com.apiculture.simulator.domain.market.HoneyMarketEngine;
import com.apiculture.simulator.domain.parcel.HexFlora;
import com.apiculture.simulator.domain.parcel.HexParcel;
import com.apiculture.simulator.domain.parcel.HexParcelRandomPoint;
import com.apiculture.simulator.domain.workshop.JarMix;
import com.apiculture.simulator.domain.workshop.WorkshopRules;
import com.apiculture.simulator.presentation.hive.HiveSiteSummaryUi;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class HoneyOrderCatalog {

    public static final int HEXES_PER_BATCH = 50;
    public static final int ORDERS_PER_BATCH = 4;
    /** Sudáfrica mantiene el triple del cupo original (2), no solo el doble. */
    public static final int ORDERS_PER_BATCH_ZA = 6;
    public static final int VISIBLE_NEAREST = 28;
    public static final int PAGE_SIZE = 5;
    public static final double PRICE_BONUS = 1.5;

    /**
     * Dentro de un mismo nivel de desbloqueo, de la miel más fácil de encontrar
     * a la más escasa. El factor va de 1 a 1,2.
     */
    private static final String[][] SCARCITY_BANDS = {
            {"Mil flores", "Eucalipto", "Romero", "Tomillo", "Lavanda", "Bosque", "Litchi", "Arboç", "Girofle", "Ravintsara", "Longose"},
            {"Mango", "Mielato de encina y roble"},
            {"Campo de rabaniza", "Café", "Niaouli", "Tapia"},
            {"Castaño", "Brezo"},
            {"Campo de facelia", "Tamarindo", "Baobab", "Mangle"},
            {"Sisal", "Jujube", "Raketa"},
            {"Acacia", "Aloe", "Marula", "Boekenhout"},
            {"Fynbos", "Protea", "Buchu"},
    };

    private HoneyOrderCatalog() {
    }

    public static double orderScarcity(@Nullable String flora) {
        if (flora == null) {
            return 1.0;
        }
        Double factor = scarcityByFlora().get(HexFlora.canonicalKey(flora));
        return factor != null ? factor : 1.0;
    }

    private static Map<String, Double> scarcityByFlora() {
        Map<String, Double> out = new LinkedHashMap<>();
        for (String[] band : SCARCITY_BANDS) {
            int last = Math.max(1, band.length - 1);
            for (int i = 0; i < band.length; i++) {
                double factor = band.length <= 1 ? 1.0 : 1.0 + 0.2 * i / last;
                out.put(HexFlora.canonicalKey(band[i]), Math.round(factor * 1000.0) / 1000.0);
            }
        }
        return out;
    }

    public static int dailyCount(int parcelCount) {
        return bandDailyCount(OfferBand.at(0), parcelCount);
    }

    /** 4 comandas por cada 50 hex (6 en Sudáfrica). Por debajo de 50 no se abre una aislada. */
    public static int bandDailyCount(@NonNull OfferBand band, int eligibleParcelCount) {
        return bandDailyCount(band, eligibleParcelCount, null);
    }

    public static int bandDailyCount(@NonNull OfferBand band, int eligibleParcelCount,
            @Nullable PlayableMapRegion region) {
        if (eligibleParcelCount < HEXES_PER_BATCH) {
            return 0;
        }
        int perBatch = region == PlayableMapRegion.SOUTH_AFRICA
                ? ORDERS_PER_BATCH_ZA : ORDERS_PER_BATCH;
        return (eligibleParcelCount * perBatch) / HEXES_PER_BATCH;
    }

    @NonNull
    public static List<HoneyOrder> spawnDay(@NonNull Context context, int dayKey,
            @NonNull PlayableMapRegion region, @Nullable PriceLookup prices) {
        List<HexParcel> parcels = IberiaHexOverlayStore.getParcels(context.getApplicationContext(), region);
        List<HoneyOrder> out = new ArrayList<>();
        if (parcels == null || parcels.isEmpty()) {
            return out;
        }
        Set<String> used = new HashSet<>();
        String regionKey = region.prefsValue();
        long expire = System.currentTimeMillis() + OfferReplenish.ORDER_LIFE_MS;
        for (int b = 0; b < OfferBand.COUNT; b++) {
            OfferBand band = OfferBand.at(b);
            int want = bandDailyCount(band, countEligible(parcels, band), region);
            List<HexParcel> dests = pickScattered(parcels, band, want, used, dayKey * 31L + b);
            for (int i = 0; i < dests.size(); i++) {
                HexParcel dest = dests.get(i);
                long seed = ((long) dayKey * 1_000_003L) + (long) i * 97_027L
                        + region.ordinal() * 13L + (long) b * 1_001L;
                used.add(dest.id);
                String id = String.format(Locale.US, "ho-%s-%d-%d-%d", regionKey, dayKey, band.index, i);
                HoneyOrder order = buildAt(id, dest, band, regionKey, dayKey, expire, seed, prices);
                if (order != null) {
                    out.add(order);
                }
            }
        }
        return out;
    }

    @NonNull
    public static List<HoneyOrder> spawnBand(@NonNull Context context, int dayKey,
            @NonNull PlayableMapRegion region, @Nullable PriceLookup prices,
            @NonNull OfferBand band, int alreadyHave, @Nullable Set<String> usedHexIds) {
        List<HoneyOrder> out = new ArrayList<>();
        List<HexParcel> parcels = IberiaHexOverlayStore.getParcels(context.getApplicationContext(), region);
        if (parcels == null || parcels.isEmpty()) {
            return out;
        }
        int want = Math.max(0, bandDailyCount(band, countEligible(parcels, band), region) - Math.max(0, alreadyHave));
        if (want <= 0) {
            return out;
        }
        Set<String> used = usedHexIds != null ? new HashSet<>(usedHexIds) : new HashSet<>();
        String regionKey = region.prefsValue();
        long expire = System.currentTimeMillis() + OfferReplenish.ORDER_LIFE_MS;
        List<HexParcel> dests = pickScattered(parcels, band, want, used,
                ((long) dayKey << 8) + band.index);
        for (int i = 0; i < dests.size(); i++) {
            HexParcel dest = dests.get(i);
            int slot = Math.max(0, alreadyHave) + i;
            long seed = ((long) dayKey * 1_000_003L) + (long) slot * 97_027L
                    + region.ordinal() * 13L + (long) band.index * 1_001L + 77L;
            used.add(dest.id);
            String id = String.format(Locale.US, "ho-%s-%d-%d-%d", regionKey, dayKey, band.index, slot);
            HoneyOrder order = buildAt(id, dest, band, regionKey, dayKey, expire, seed, prices);
            if (order != null) {
                out.add(order);
            }
        }
        return out;
    }

    @Nullable
    public static HoneyOrder replenishFrom(@NonNull Context context, @NonNull HoneyOrderEntity dead,
            @Nullable PriceLookup prices, long nowMs, @Nullable Set<String> usedHexIds) {
        if (dead == null) {
            return null;
        }
        int dayKey = dead.createdDayKey > 0
                ? dead.createdDayKey
                : GameCalendar.currentGlobalMarketDayKey();
        if (!OfferReplenish.canReplenish(nowMs, dayKey)) {
            return null;
        }
        PlayableMapRegion region = PlayableMapRegion.fromPrefsValue(dead.region);
        if (region == null) {
            region = PlayableMapRegion.fromHexId(dead.destHexId);
        }
        if (region == null) {
            return null;
        }
        List<HexParcel> parcels = IberiaHexOverlayStore.getParcels(context.getApplicationContext(), region);
        OfferBand band = OfferBand.at(dead.band);
        Set<String> used = usedHexIds != null ? usedHexIds : new HashSet<>();
        if (dead.destHexId != null) {
            used.add(dead.destHexId);
        }
        long seed = (dead.id != null ? dead.id.hashCode() : 0) * 31L + nowMs;
        HexParcel dest = OfferReplenish.pickNearby(parcels, dead.destLat, dead.destLng, used, seed,
                p -> parcelOk(p, band));
        if (dest == null) {
            List<ProvincialMarket> markets = ProvincialMarketCatalog.resolve(
                    context.getApplicationContext(), region);
            dest = OfferReplenish.nearestMarketParcel(parcels, markets, dead.destLat, dead.destLng,
                    used, region, p -> parcelOk(p, band));
        }
        if (dest == null) {
            return null;
        }
        long expire = nowMs + OfferReplenish.ORDER_LIFE_MS;
        String regionKey = region.prefsValue();
        String id = String.format(Locale.US, "ho-r-%s-%d-%d-%s", regionKey, dayKey, band.index,
                Integer.toHexString((dead.id != null ? dead.id : dest.id).hashCode()));
        return buildAt(id, dest, band, regionKey, dayKey, expire, seed, prices);
    }

    @Nullable
    private static HoneyOrder buildAt(@NonNull String id, @NonNull HexParcel dest, @NonNull OfferBand band,
            @NonNull String regionKey, int dayKey, long expire, long seed, @Nullable PriceLookup prices) {
        String flora = floraForBand(dest, seed, band);
        double rawKg = band.kgForSeed(seed);
        // La comanda pide tarros: los kilos se redondean hacia arriba y se reparten de mayor a menor.
        JarMix mix = JarMix.fromKg(rawKg);
        double kg = mix.kg();
        double base = HoneyMarketEngine.priceCeilingEurPerKgForFlora(flora);
        if (base <= 0.0) {
            base = HoneyMarketEngine.MIN_PRICE_EUR_PER_KG;
        }
        base *= orderScarcity(flora);
        String npc = NpcContractCatalog.npcNameFor(dest);
        if (npc == null || npc.isEmpty()) {
            npc = "Cliente";
        }
        String place = dest.placeName != null && !dest.placeName.trim().isEmpty()
                ? dest.placeName
                : dest.id;
        double[] pin = HexParcelRandomPoint.pinOf(dest, id);
        return new HoneyOrder(
                id,
                npc,
                NpcContractCatalog.portraitIndexFor(npc),
                flora,
                kg,
                mix.unitPrice(Math.round(base * PRICE_BONUS * 100.0) / 100.0),
                dest.id,
                pin[0],
                pin[1],
                place,
                regionKey,
                dayKey,
                expire,
                band.index,
                WorkshopRules.Format.BULK,
                mix);
    }

    @NonNull
    static List<HexParcel> pickScattered(
            @Nullable List<HexParcel> parcels,
            @NonNull OfferBand band,
            int want,
            @NonNull Set<String> used,
            long seedBase) {
        List<HexParcel> out = new ArrayList<>();
        if (parcels == null || parcels.isEmpty() || want <= 0) {
            return out;
        }
        List<HexParcel> eligible = new ArrayList<>();
        for (int i = 0; i < parcels.size(); i++) {
            HexParcel p = parcels.get(i);
            if (p != null && p.id != null && !used.contains(p.id) && parcelOk(p, band)) {
                eligible.add(p);
            }
        }
        if (eligible.isEmpty()) {
            return out;
        }
        eligible.sort((a, b) -> {
            int lat = Double.compare(a.centroidLat, b.centroidLat);
            if (lat != 0) {
                return lat;
            }
            int lon = Double.compare(a.centroidLon, b.centroidLon);
            if (lon != 0) {
                return lon;
            }
            return a.id.compareTo(b.id);
        });
        int n = eligible.size();
        int take = Math.min(want, n);
        int shift = (int) Math.floorMod(seedBase, n);
        for (int i = 0; i < take; i++) {
            int idx = (int) Math.floor((i + 0.5) * n / (double) take);
            HexParcel dest = eligible.get(Math.floorMod(idx + shift, n));
            used.add(dest.id);
            out.add(dest);
        }
        return out;
    }

    @Nullable
    static HexParcel pickScatteredOne(
            @Nullable List<HexParcel> parcels,
            @NonNull OfferBand band,
            @NonNull Set<String> used,
            long seed) {
        List<HexParcel> one = pickScattered(parcels, band, 1, used, seed);
        return one.isEmpty() ? null : one.get(0);
    }

    public static int countEligible(@Nullable List<HexParcel> parcels, @NonNull OfferBand band) {
        if (parcels == null || parcels.isEmpty()) {
            return 0;
        }
        int n = 0;
        for (int i = 0; i < parcels.size(); i++) {
            HexParcel p = parcels.get(i);
            if (p != null && p.id != null && parcelOk(p, band)) {
                n++;
            }
        }
        return n;
    }

    static boolean parcelOk(@NonNull HexParcel dest, @NonNull OfferBand band) {
        return ClimateUnlock.canBuyParcel(dest, band.maxLevel);
    }

    @NonNull
    public static String floraForParcel(@NonNull HexParcel dest, long seed) {
        return floraForBand(dest, seed, OfferBand.at(0));
    }

    @NonNull
    static String floraForBand(@NonNull HexParcel dest, long seed, @NonNull OfferBand band) {
        List<String> pool;
        if (HexFlora.isMadagascarParcel(dest)) {
            pool = HexFlora.nativePoolForZone(MadagascarClimateZone.forParcel(dest));
        } else if (HexFlora.isZaParcel(dest) || HexFlora.isSouthernParcel(dest)) {
            pool = HexFlora.nativePoolForZone(SouthernAfricanClimateZone.forParcel(dest));
        } else {
            pool = HexFlora.nativePoolForZone(IberianClimateZone.forParcel(dest));
        }
        List<String> allowed = new ArrayList<>();
        if (pool != null) {
            for (int i = 0; i < pool.size(); i++) {
                String flora = pool.get(i);
                if (HoneyMarketEngine.playerCanAccessFlora(flora, band.maxLevel)) {
                    allowed.add(flora);
                }
            }
        }
        if (allowed.isEmpty()) {
            return HexFlora.MIL_FLORES;
        }
        return allowed.get((int) Math.floorMod(seed >> 3, allowed.size()));
    }

    @NonNull
    public static List<HoneyOrder> openNearest(@Nullable List<HoneyOrderEntity> rows,
            @Nullable String region, @Nullable HexParcel origin, long nowMs, int limit) {
        return openNearest(rows, region, origin, nowMs, limit, Integer.MAX_VALUE);
    }

    @NonNull
    public static List<HoneyOrder> openNearest(@Nullable List<HoneyOrderEntity> rows,
            @Nullable String region, @Nullable HexParcel origin, long nowMs, int limit,
            int playerLevel) {
        Double lat = origin != null ? origin.centroidLat : null;
        Double lng = origin != null ? origin.centroidLon : null;
        return openNearest(rows, region, lat, lng, nowMs, limit, playerLevel);
    }

    @NonNull
    public static List<HoneyOrder> openNearest(@Nullable List<HoneyOrderEntity> rows,
            @Nullable String region, @Nullable Double originLat, @Nullable Double originLng,
            long nowMs, int limit) {
        return openNearest(rows, region, originLat, originLng, nowMs, limit, Integer.MAX_VALUE);
    }

    @NonNull
    public static List<HoneyOrder> openNearest(@Nullable List<HoneyOrderEntity> rows,
            @Nullable String region, @Nullable Double originLat, @Nullable Double originLng,
            long nowMs, int limit, int playerLevel) {
        List<HoneyOrder> offers = new ArrayList<>();
        if (rows == null || rows.isEmpty()) {
            return offers;
        }
        OfferBand band = playerLevel == Integer.MAX_VALUE ? null : OfferBand.ofLevel(playerLevel);
        for (HoneyOrderEntity e : rows) {
            if (e == null || e.taken || e.expireEpochMs <= nowMs) {
                continue;
            }
            if (region != null && !region.equals(e.region)) {
                continue;
            }
            if (band != null && e.band != band.index) {
                continue;
            }
            if (playerLevel != Integer.MAX_VALUE
                    && !HoneyMarketEngine.playerCanAccessFlora(e.floraKey, playerLevel)) {
                continue;
            }
            offers.add(HoneyOrder.fromEntity(e));
        }
        sortByNearest(offers, originLat, originLng);
        if (limit > 0 && offers.size() > limit) {
            return new ArrayList<>(offers.subList(0, limit));
        }
        return offers;
    }

    public static void sortByNearest(@NonNull List<HoneyOrder> orders,
            @Nullable Double originLat, @Nullable Double originLng) {
        orders.sort((a, b) -> {
            int km = Double.compare(
                    distanceKm(originLat, originLng, a),
                    distanceKm(originLat, originLng, b));
            if (km != 0) {
                return km;
            }
            return Long.compare(a.expireEpochMs, b.expireEpochMs);
        });
    }

    public static void sortByFloraAndDistance(@NonNull List<HoneyOrder> orders,
            @Nullable Double originLat, @Nullable Double originLng, @NonNull Context context) {
        orders.sort((a, b) -> {
            String labelA = HiveSiteSummaryUi.floraLabel(context, a.floraKey);
            String labelB = HiveSiteSummaryUi.floraLabel(context, b.floraKey);
            int flora = labelA.compareToIgnoreCase(labelB);
            if (flora != 0) {
                return flora;
            }
            int km = Double.compare(
                    distanceKm(originLat, originLng, a),
                    distanceKm(originLat, originLng, b));
            if (km != 0) {
                return km;
            }
            return Long.compare(a.expireEpochMs, b.expireEpochMs);
        });
    }

    public static double distanceKm(@Nullable Double originLat, @Nullable Double originLng,
            @NonNull HoneyOrder order) {
        if (originLat == null || originLng == null) {
            return Double.MAX_VALUE;
        }
        return TranshumanceRules.haversineKm(originLat, originLng, order.destLat, order.destLng);
    }

    public interface PriceLookup {
        double eurPerKg(String floraKey);
    }
}
