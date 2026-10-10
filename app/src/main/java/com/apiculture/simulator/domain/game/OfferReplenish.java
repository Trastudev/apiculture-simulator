package com.apiculture.simulator.domain.game;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.apiculture.simulator.domain.map.PlayableMapRegion;
import com.apiculture.simulator.domain.map.ProvincialMarket;
import com.apiculture.simulator.domain.parcel.HexParcel;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Recambio a 40/80 km y cierre del día UTC. */
public final class OfferReplenish {

    private OfferReplenish() {
    }

    public static long utcDayStartEpochMs(int utcDayKey) {
        LocalDate day = GameCalendar.fromDayKey(utcDayKey);
        return ZonedDateTime.of(day, LocalTime.MIDNIGHT, ZoneOffset.UTC).toInstant().toEpochMilli();
    }

    /** Radio para considerar una oferta «cerca» de la sede al rellenar el pool. */
    public static final double NEAR_SPAWN_KM = 180.0;
    /** Vida de una comanda desde que aparece. */
    public static final long ORDER_LIFE_MS = 8L * 60L * 60L * 1000L;

    public static long utcDayEndEpochMs(int utcDayKey) {
        LocalDate day = GameCalendar.fromDayKey(utcDayKey);
        return ZonedDateTime.of(day.plusDays(1), LocalTime.MIDNIGHT, ZoneOffset.UTC)
                .toInstant().toEpochMilli();
    }

    public static boolean canReplenish(long nowMs, int utcDayKey) {
        return utcDayEndEpochMs(utcDayKey) - nowMs >= OfferBand.MIN_REPLENISH_REMAINING_MS;
    }

    @Nullable
    public static HexParcel pickNearThenAnywhere(
            @Nullable List<HexParcel> parcels,
            @Nullable Double lat,
            @Nullable Double lng,
            @Nullable Set<String> excludeHexIds,
            long seed,
            @NonNull ParcelOk ok) {
        if (lat != null && lng != null) {
            double[] rings = {55.0, 110.0, 180.0, 280.0};
            for (int i = 0; i < rings.length; i++) {
                HexParcel hit = pickInRadius(parcels, lat, lng, excludeHexIds, rings[i], seed + i, ok);
                if (hit != null) {
                    return hit;
                }
            }
        }
        if (parcels == null || parcels.isEmpty()) {
            return null;
        }
        int start = (int) Math.floorMod(seed, parcels.size());
        int max = Math.min(parcels.size(), 800);
        for (int n = 0; n < max; n++) {
            HexParcel p = parcels.get((start + n) % parcels.size());
            if (p == null || p.id == null) {
                continue;
            }
            if (excludeHexIds != null && excludeHexIds.contains(p.id)) {
                continue;
            }
            if (ok.test(p)) {
                return p;
            }
        }
        return null;
    }

    @Nullable
    public static HexParcel pickNearby(
            @Nullable List<HexParcel> parcels,
            double lat,
            double lng,
            @Nullable Set<String> excludeHexIds,
            long seed,
            @NonNull ParcelOk ok) {
        HexParcel hit = pickInRadius(parcels, lat, lng, excludeHexIds, OfferBand.REPLENISH_KM, seed, ok);
        if (hit != null) {
            return hit;
        }
        return pickInRadius(parcels, lat, lng, excludeHexIds, OfferBand.REPLENISH_FALLBACK_KM, seed, ok);
    }

    @Nullable
    public static HexParcel pickInRadius(
            @Nullable List<HexParcel> parcels,
            double lat,
            double lng,
            @Nullable Set<String> excludeHexIds,
            double maxKm,
            long seed,
            @NonNull ParcelOk ok) {
        if (parcels == null || parcels.isEmpty() || maxKm <= 0) {
            return null;
        }
        List<HexParcel> hits = new ArrayList<>();
        double latPad = maxKm / 111.0;
        double cos = Math.max(0.2, Math.cos(Math.toRadians(lat)));
        double lngPad = maxKm / (111.0 * cos);
        for (int i = 0; i < parcels.size(); i++) {
            HexParcel p = parcels.get(i);
            if (p == null || p.id == null) {
                continue;
            }
            if (excludeHexIds != null && excludeHexIds.contains(p.id)) {
                continue;
            }
            if (Math.abs(p.centroidLat - lat) > latPad || Math.abs(p.centroidLon - lng) > lngPad) {
                continue;
            }
            if (TranshumanceRules.haversineKm(lat, lng, p.centroidLat, p.centroidLon) > maxKm + 1e-6) {
                continue;
            }
            if (ok.test(p)) {
                hits.add(p);
            }
        }
        if (hits.isEmpty()) {
            return null;
        }
        return hits.get((int) Math.floorMod(seed, hits.size()));
    }

    @Nullable
    public static HexParcel nearestMarketParcel(
            @Nullable List<HexParcel> parcels,
            @Nullable List<ProvincialMarket> markets,
            double lat,
            double lng,
            @Nullable Set<String> excludeHexIds,
            @Nullable PlayableMapRegion region,
            @NonNull ParcelOk ok) {
        if (markets == null || markets.isEmpty()) {
            return nearestOk(parcels, lat, lng, excludeHexIds, ok);
        }
        ProvincialMarket best = null;
        double bestKm = Double.MAX_VALUE;
        for (int i = 0; i < markets.size(); i++) {
            ProvincialMarket m = markets.get(i);
            if (m == null) {
                continue;
            }
            if (region != null && m.region != region) {
                continue;
            }
            double km = TranshumanceRules.haversineKm(lat, lng, m.lat, m.lng);
            if (km < bestKm) {
                bestKm = km;
                best = m;
            }
        }
        if (best == null) {
            return nearestOk(parcels, lat, lng, excludeHexIds, ok);
        }
        if (parcels != null) {
            for (int i = 0; i < parcels.size(); i++) {
                HexParcel p = parcels.get(i);
                if (p != null && best.hexId != null && best.hexId.equals(p.id)
                        && (excludeHexIds == null || !excludeHexIds.contains(p.id))
                        && ok.test(p)) {
                    return p;
                }
            }
        }
        return pickNearby(parcels, best.lat, best.lng, excludeHexIds, best.hexId != null
                ? best.hexId.hashCode() : 1L, ok);
    }

    @Nullable
    private static HexParcel nearestOk(
            @Nullable List<HexParcel> parcels,
            double lat,
            double lng,
            @Nullable Set<String> excludeHexIds,
            @NonNull ParcelOk ok) {
        if (parcels == null) {
            return null;
        }
        HexParcel best = null;
        double bestKm = Double.MAX_VALUE;
        for (int i = 0; i < parcels.size(); i++) {
            HexParcel p = parcels.get(i);
            if (p == null || p.id == null) {
                continue;
            }
            if (excludeHexIds != null && excludeHexIds.contains(p.id)) {
                continue;
            }
            if (!ok.test(p)) {
                continue;
            }
            double km = TranshumanceRules.haversineKm(lat, lng, p.centroidLat, p.centroidLon);
            if (km < bestKm) {
                bestKm = km;
                best = p;
            }
        }
        return best;
    }

    public interface ParcelOk {
        boolean test(@NonNull HexParcel parcel);
    }
}
