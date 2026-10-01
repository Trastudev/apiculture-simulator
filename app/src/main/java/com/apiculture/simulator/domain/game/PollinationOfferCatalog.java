package com.apiculture.simulator.domain.game;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.apiculture.simulator.data.repository.IberiaHexOverlayStore;
import com.apiculture.simulator.domain.map.PlayableMapRegion;
import com.apiculture.simulator.domain.parcel.BoundingBox;
import com.apiculture.simulator.domain.parcel.HexFlora;
import com.apiculture.simulator.domain.parcel.HexParcel;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Puntos de contrato de polinización: el cultivo en flor (o el más próximo) de la
 * rotación NPC del clima, repartido por la malla.
 */
public final class PollinationOfferCatalog {

    public static final int HEXES_PER_BATCH = 70;
    public static final int OFFERS_PER_BATCH = 2;
    public static final int VISIBLE_NEAREST = 64;
    static final int GRID_COLS = 12;
    static final int GRID_ROWS = 10;

    private PollinationOfferCatalog() {
    }

    public static int dailyCount(int parcelCount) {
        if (parcelCount <= 0) {
            return 0;
        }
        return Math.max(16, Math.min(VISIBLE_NEAREST, (parcelCount * OFFERS_PER_BATCH) / HEXES_PER_BATCH));
    }

    /** Densidad ~2 ofertas cada 70 hex, como mínimo el cupo de la banda. */
    public static int bandDailyCount(@NonNull OfferBand band, int parcelCount) {
        if (band.pollinationCount <= 0) {
            return 0;
        }
        int density = parcelCount <= 0 ? 0
                : Math.max(OFFERS_PER_BATCH, (parcelCount * OFFERS_PER_BATCH) / HEXES_PER_BATCH);
        return Math.min(48, Math.max(band.pollinationCount, density));
    }

    @NonNull
    public static List<NpcContractFarm> spawnOpen(
            @NonNull Context context,
            @NonNull PlayableMapRegion region,
            @NonNull LocalDate today,
            int playerLevel) {
        return spawnOpen(context, region, today, playerLevel, null, null);
    }

    @NonNull
    public static List<NpcContractFarm> spawnOpen(
            @NonNull Context context,
            @NonNull PlayableMapRegion region,
            @NonNull LocalDate today,
            int playerLevel,
            @Nullable Double originLat,
            @Nullable Double originLng) {
        List<HexParcel> parcels = IberiaHexOverlayStore.getParcels(context.getApplicationContext(), region);
        return spawnFromParcels(parcels, region.box(), today, playerLevel);
    }

    @NonNull
    public static List<NpcContractFarm> spawnFromParcels(
            @Nullable List<HexParcel> parcels,
            @Nullable BoundingBox box,
            @NonNull LocalDate today,
            int playerLevel) {
        List<NpcContractFarm> out = new ArrayList<>();
        if (parcels == null || parcels.isEmpty() || playerLevel < 2) {
            return out;
        }
        int want = dailyCount(parcels.size());
        Map<Integer, List<HexParcel>> cells = bucketCells(parcels, box);
        if (cells.isEmpty()) {
            return out;
        }
        List<Integer> keys = new ArrayList<>(cells.keySet());
        int dayKey = GameCalendar.toDayKey(today);
        keys.sort((a, b) -> Long.compare(
                Math.abs(hash64("cell-order:" + dayKey + ":" + a)),
                Math.abs(hash64("cell-order:" + dayKey + ":" + b))));
        int stride = Math.max(1, keys.size() / want);
        for (int i = 0; i < keys.size() && out.size() < want; i += stride) {
            Integer key = keys.get(i);
            List<HexParcel> bucket = cells.get(key);
            NpcContractFarm farm = pickInCell(bucket, today, playerLevel, dayKey, key);
            if (farm != null) {
                out.add(farm);
            }
        }
        if (out.size() < Math.min(want, keys.size())) {
            for (int i = 0; i < keys.size() && out.size() < want; i++) {
                if (i % stride == 0) {
                    continue;
                }
                Integer key = keys.get(i);
                NpcContractFarm farm = pickInCell(cells.get(key), today, playerLevel, dayKey, key);
                if (farm != null) {
                    out.add(farm);
                }
            }
        }
        return out;
    }

    @NonNull
    static Map<Integer, List<HexParcel>> bucketCells(
            @NonNull List<HexParcel> parcels, @Nullable BoundingBox box) {
        double minLat = box != null ? box.minLat : 90;
        double maxLat = box != null ? box.maxLat : -90;
        double minLon = box != null ? box.minLon : 180;
        double maxLon = box != null ? box.maxLon : -180;
        if (box == null) {
            for (int i = 0; i < parcels.size(); i++) {
                HexParcel p = parcels.get(i);
                if (p == null) {
                    continue;
                }
                minLat = Math.min(minLat, p.centroidLat);
                maxLat = Math.max(maxLat, p.centroidLat);
                minLon = Math.min(minLon, p.centroidLon);
                maxLon = Math.max(maxLon, p.centroidLon);
            }
        }
        double latSpan = Math.max(0.01, maxLat - minLat);
        double lonSpan = Math.max(0.01, maxLon - minLon);
        Map<Integer, List<HexParcel>> cells = new LinkedHashMap<>();
        for (int i = 0; i < parcels.size(); i++) {
            HexParcel p = parcels.get(i);
            if (p == null || p.id == null) {
                continue;
            }
            int row = (int) Math.min(GRID_ROWS - 1,
                    Math.max(0, Math.floor((p.centroidLat - minLat) / latSpan * GRID_ROWS)));
            int col = (int) Math.min(GRID_COLS - 1,
                    Math.max(0, Math.floor((p.centroidLon - minLon) / lonSpan * GRID_COLS)));
            int key = row * GRID_COLS + col;
            List<HexParcel> bucket = cells.get(key);
            if (bucket == null) {
                bucket = new ArrayList<>();
                cells.put(key, bucket);
            }
            bucket.add(p);
        }
        return cells;
    }

    @Nullable
    private static NpcContractFarm pickInCell(
            @Nullable List<HexParcel> bucket,
            @NonNull LocalDate today,
            int playerLevel,
            int dayKey,
            int cellKey) {
        if (bucket == null || bucket.isEmpty()) {
            return null;
        }
        int start = (int) Math.floorMod(hash64("cell-hex:" + dayKey + ":" + cellKey), bucket.size());
        for (int n = 0; n < bucket.size(); n++) {
            HexParcel dest = bucket.get((start + n) % bucket.size());
            NpcContractFarm farm = pickContractCrop(dest, today, playerLevel, dayKey);
            if (farm != null) {
                return farm;
            }
        }
        return null;
    }

    @Nullable
    public static NpcContractFarm pickContractCrop(
            @Nullable HexParcel dest,
            @NonNull LocalDate today,
            int playerLevel,
            int dayKey) {
        if (dest == null || !ClimateUnlock.canBuyParcel(dest, playerLevel)) {
            return null;
        }
        List<String> crops = PollinationContractCrops.forParcel(dest);
        if (crops.isEmpty()) {
            return null;
        }
        int doy = Math.min(365, today.getDayOfYear());
        NpcContractFarm best = null;
        int bestWait = Integer.MAX_VALUE;
        for (int i = 0; i < crops.size(); i++) {
            String flora = crops.get(i);
            if (!HexFlora.isPlantation(flora)) {
                continue;
            }
            NpcContractFarm farm = NpcContractCatalog.farmStartingAfter(dest, flora, today);
            int wait = PollinationContractCrops.daysUntilWork(farm, today);
            if (farm == null || wait < 1 || wait == Integer.MAX_VALUE) {
                continue;
            }
            if (wait < bestWait) {
                bestWait = wait;
                best = farm;
            }
        }
        NpcContractFarm pick = best;
        if (pick == null || bestWait > PollinationContractCrops.MAX_AHEAD_DAYS) {
            return null;
        }
        double keep = PollinationContractCrops.seasonalKeepRate(dest, doy);
        if (keep < 0.999) {
            long h = Math.abs(hash64("season-keep:" + dayKey + ":" + dest.id));
            if ((h % 1000L) >= (long) Math.round(keep * 1000.0)) {
                return null;
            }
        }
        return pick;
    }

    @NonNull
    public static List<NpcContractFarm> spawnForBand(
            @Nullable List<HexParcel> parcels,
            @NonNull OfferBand band,
            @NonNull LocalDate today,
            int utcDayKey,
            @Nullable Set<String> occupiedHexIds) {
        return spawnForBand(parcels, band, today, utcDayKey, occupiedHexIds, band.pollinationCount);
    }

    @NonNull
    public static List<NpcContractFarm> spawnForBand(
            @Nullable List<HexParcel> parcels,
            @NonNull OfferBand band,
            @NonNull LocalDate today,
            int utcDayKey,
            @Nullable Set<String> occupiedHexIds,
            int want) {
        List<NpcContractFarm> out = new ArrayList<>();
        int need = Math.max(0, want);
        if (parcels == null || parcels.isEmpty() || need <= 0) {
            return out;
        }
        Set<String> used = occupiedHexIds != null ? new java.util.HashSet<>(occupiedHexIds)
                : new java.util.HashSet<>();
        Map<Integer, List<HexParcel>> cells = bucketCells(parcels, null);
        if (cells.isEmpty()) {
            return out;
        }
        List<Integer> keys = new ArrayList<>(cells.keySet());
        keys.sort((a, b) -> Long.compare(
                Math.abs(hash64("band-cell:" + utcDayKey + ":" + band.index + ":" + a)),
                Math.abs(hash64("band-cell:" + utcDayKey + ":" + band.index + ":" + b))));
        int rounds = Math.max(1, (need + keys.size() - 1) / Math.max(1, keys.size()));
        for (int round = 0; round < rounds && out.size() < need; round++) {
            for (int i = 0; i < keys.size() && out.size() < need; i++) {
                Integer key = keys.get(i);
                NpcContractFarm farm = pickInCellForBand(
                        cells.get(key), today, band, utcDayKey, key, round, used);
                if (farm == null || farm.parcel == null || farm.parcel.id == null) {
                    continue;
                }
                used.add(farm.parcel.id);
                out.add(farm);
            }
        }
        return out;
    }

    @Nullable
    private static NpcContractFarm pickInCellForBand(
            @Nullable List<HexParcel> bucket,
            @NonNull LocalDate today,
            @NonNull OfferBand band,
            int dayKey,
            int cellKey,
            int round,
            @NonNull Set<String> used) {
        if (bucket == null || bucket.isEmpty()) {
            return null;
        }
        int start = (int) Math.floorMod(
                hash64("band-hex:" + dayKey + ":" + band.index + ":" + cellKey + ":" + round),
                bucket.size());
        for (int n = 0; n < bucket.size(); n++) {
            HexParcel dest = bucket.get((start + n) % bucket.size());
            if (dest == null || dest.id == null || used.contains(dest.id)) {
                continue;
            }
            NpcContractFarm farm = pickForBand(dest, today, band, dayKey);
            if (farm != null) {
                return farm;
            }
        }
        return null;
    }

    @Nullable
    public static NpcContractFarm replenishNear(
            @Nullable List<HexParcel> parcels,
            @Nullable HexParcel from,
            @NonNull OfferBand band,
            @NonNull LocalDate today,
            int utcDayKey,
            @Nullable Set<String> occupiedHexIds) {
        if (from == null) {
            return null;
        }
        Set<String> used = occupiedHexIds != null ? new java.util.HashSet<>(occupiedHexIds)
                : new java.util.HashSet<>();
        used.add(from.id);
        long seed = (from.id != null ? from.id.hashCode() : 0) + utcDayKey * 17L + band.index;
        HexParcel dest = OfferReplenish.pickNearby(parcels, from.centroidLat, from.centroidLon, used, seed,
                p -> pickForBand(p, today, band, utcDayKey) != null);
        if (dest == null) {
            dest = OfferReplenish.pickNearby(parcels, from.centroidLat, from.centroidLon, used, seed + 1,
                    p -> ClimateUnlock.canBuyParcel(p, band.maxLevel));
        }
        if (dest == null) {
            return null;
        }
        return pickForBand(dest, today, band, utcDayKey);
    }

    @Nullable
    public static NpcContractFarm pickForBand(
            @Nullable HexParcel dest,
            @NonNull LocalDate today,
            @NonNull OfferBand band,
            int dayKey) {
        if (dest == null || !ClimateUnlock.canBuyParcel(dest, band.minLevel)) {
            return null;
        }
        List<String> crops = PollinationContractCrops.forParcel(dest);
        if (crops.isEmpty()) {
            return null;
        }
        int doy = Math.min(365, today.getDayOfYear());
        NpcContractFarm best = null;
        int bestWait = Integer.MAX_VALUE;
        for (int i = 0; i < crops.size(); i++) {
            String flora = crops.get(i);
            if (!HexFlora.isPlantation(flora)) {
                continue;
            }
            NpcContractFarm farm = NpcContractCatalog.farmStartingAfter(dest, flora, today);
            int wait = PollinationContractCrops.daysUntilWork(farm, today);
            if (farm == null || wait < 1 || wait == Integer.MAX_VALUE) {
                continue;
            }
            if (wait < bestWait) {
                bestWait = wait;
                best = farm;
            }
        }
        NpcContractFarm pick = best;
        if (pick == null || bestWait > PollinationContractCrops.MAX_AHEAD_DAYS) {
            return null;
        }
        double keep = PollinationContractCrops.seasonalKeepRate(dest, doy);
        if (keep < 0.999) {
            long h = Math.abs(hash64("season-keep:" + dayKey + ":" + dest.id + ":" + band.index));
            if ((h % 1000L) >= (long) Math.round(keep * 1000.0)) {
                return null;
            }
        }
        return pick;
    }

    static long hash64(String s) {
        long h = -3750763034362895779L;
        for (int i = 0; i < s.length(); i++) {
            h ^= s.charAt(i);
            h *= 1099511628211L;
        }
        return h;
    }
}
