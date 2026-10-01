package com.apiculture.simulator.domain.game;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.apiculture.simulator.data.local.entity.HiveEntity;
import com.apiculture.simulator.domain.market.HoneyMarketEngine;
import com.apiculture.simulator.domain.parcel.HexParcel;
import com.apiculture.simulator.domain.population.HivePopulationState;

import java.time.LocalDate;
import java.util.List;

/**
 * Saturación de pecoreo: demanda de obreras (mejor sol) frente a la piscina de néctar del hex.
 */
public final class HexFloraSaturation {

    public final double demandKg;
    public final double poolKg;
    /** Demanda / piscina; 0 si no hay pecoreo. Puede ser &gt; 1. */
    public final double ratio;
    public final int hiveCount;

    public static final class Line {
        @NonNull
        public final String floraKey;
        @NonNull
        public final HexFloraSaturation sat;

        public Line(@NonNull String floraKey, @NonNull HexFloraSaturation sat) {
            this.floraKey = floraKey;
            this.sat = sat;
        }
    }

    public HexFloraSaturation(double demandKg, double poolKg, int hiveCount) {
        this.demandKg = Math.max(0.0, demandKg);
        this.poolKg = Math.max(0.0, poolKg);
        this.hiveCount = Math.max(0, hiveCount);
        if (this.demandKg <= 1e-9) {
            this.ratio = 0.0;
        } else if (this.poolKg <= 1e-9) {
            this.ratio = 1.0;
        } else {
            this.ratio = this.demandKg / this.poolKg;
        }
    }

    public int barPercent() {
        return Math.max(0, Math.min(100, (int) Math.round(ratio * 100.0)));
    }

    /** Verde / ámbar / rojo según holgura. */
    public int tone() {
        if (ratio < 0.55) {
            return 0;
        }
        if (ratio < 1.0) {
            return 1;
        }
        return 2;
    }

    @NonNull
    public static HexFloraSaturation compute(
            @Nullable HexParcel parcel,
            @Nullable String floraFilter,
            @Nullable List<HiveEntity> hivesOnHex,
            @Nullable LocalDate day) {
        LocalDate d = day != null ? day : LocalDate.now(GameCalendar.userTimeZone());
        int dayKey = GameCalendar.toDayKey(d);
        String want = floraFilter == null || floraFilter.trim().isEmpty()
                ? null
                : HoneyMarketEngine.canonicalFloraKey(floraFilter);
        double demand = 0.0;
        double pool = 0.0;
        int n = 0;
        boolean poolOnce = false;
        if (hivesOnHex != null) {
            for (HiveEntity hive : hivesOnHex) {
                if (hive == null || hive.inWarehouse) {
                    continue;
                }
                String flora = HoneyMarketEngine.canonicalFloraKey(hive.floraType);
                if (want != null && !want.equals(flora)) {
                    continue;
                }
                n++;
                HivePopulationState pop = HivePopulationState.fromHiveEntityOrDefault(
                        hive, Math.max(0, hive.beeCount));
                demand += bestForageKg(pop, hive, dayKey);
                if (want != null && !poolOnce) {
                    pool = HexNectarPool.poolKg(probe(parcel, hive, flora), d);
                    poolOnce = true;
                }
            }
        }
        if (want != null && !poolOnce) {
            pool = HexNectarPool.poolKg(probe(parcel, null, want), d);
        }
        if (want == null) {
            pool = poolOfForagedFloras(parcel, hivesOnHex, d);
        }
        return new HexFloraSaturation(demand, pool, n);
    }

    @NonNull
    public static List<Line> computeLines(
            @Nullable HexParcel parcel,
            @Nullable List<String> floraKeys,
            @Nullable List<HiveEntity> hivesOnHex,
            @Nullable LocalDate day) {
        List<Line> out = new java.util.ArrayList<>();
        if (floraKeys == null) {
            return out;
        }
        java.util.LinkedHashSet<String> seen = new java.util.LinkedHashSet<>();
        for (String raw : floraKeys) {
            String key = HoneyMarketEngine.canonicalFloraKey(raw);
            if (key.isEmpty() || !seen.add(key)) {
                continue;
            }
            out.add(new Line(key, compute(parcel, key, hivesOnHex, day)));
        }
        return out;
    }

    private static double poolOfForagedFloras(
            @Nullable HexParcel parcel,
            @Nullable List<HiveEntity> hives,
            @NonNull LocalDate day) {
        if (hives == null) {
            return 0.0;
        }
        java.util.HashSet<String> seen = new java.util.HashSet<>();
        double sum = 0.0;
        for (HiveEntity hive : hives) {
            if (hive == null || hive.inWarehouse) {
                continue;
            }
            String flora = HoneyMarketEngine.canonicalFloraKey(hive.floraType);
            if (flora.isEmpty() || !seen.add(flora)) {
                continue;
            }
            sum += HexNectarPool.poolKg(probe(parcel, hive, flora), day);
        }
        return sum;
    }

    /** Mejor caso: sol pleno, salud y ruido neutros (1.0). */
    private static double bestForageKg(
            @Nullable HivePopulationState s,
            @Nullable HiveEntity hive,
            int dayKey) {
        if (s == null || s.workersAdult <= 0) {
            return 0.0;
        }
        if (TranshumanceRules.isInTransit(hive, dayKey)) {
            return 0.0;
        }
        double foragers = s.workersAdult * GameBalanceConfig.foragerFraction;
        return Math.max(0.0, foragers * GameBalanceConfig.kgPerForagerFullFlow);
    }

    @NonNull
    private static HiveEntity probe(
            @Nullable HexParcel parcel,
            @Nullable HiveEntity sample,
            @NonNull String flora) {
        HiveEntity h = new HiveEntity();
        h.id = "sat";
        h.floraType = flora;
        if (sample != null) {
            h.hexId = sample.hexId;
            h.lat = sample.lat;
            h.lng = sample.lng;
            h.elevationMeters = sample.elevationMeters;
        } else if (parcel != null) {
            h.hexId = parcel.id;
            h.lat = parcel.centroidLat;
            h.lng = parcel.centroidLon;
            h.elevationMeters = parcel.maxElevationMeters != null ? parcel.maxElevationMeters : 400;
        } else {
            h.hexId = "";
            h.elevationMeters = 400;
        }
        return h;
    }
}
