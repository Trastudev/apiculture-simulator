package com.apiculture.simulator.domain.game;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.apiculture.simulator.data.local.entity.HiveEntity;
import com.apiculture.simulator.domain.market.HoneyMarketEngine;
import com.apiculture.simulator.domain.parcel.HexFlora;
import com.apiculture.simulator.domain.population.HivePopulationState;
import com.apiculture.simulator.domain.population.QueenMode;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Piscina diaria de néctar por hex y flora. Las colmenas del mismo hexágono
 * (misma flora) se prorratean; no hay pecoreo ni robo entre hexes vecinos.
 */
public final class HexNectarPool {

    private HexNectarPool() {
    }

    public static double siteFactor(@Nullable Double tempC, double skyMult) {
        double tempM = TemperatureHoneyModifier.productionMultiplierForCelsius(tempC);
        double sky = Math.max(0.0, skyMult);
        return Math.max(0.0, tempM * sky);
    }

    /** El bonus de sol/claros no se usa para “salir”, solo para pecorear en destino. */
    public static double siteExit(double siteFactor) {
        if (siteFactor <= 1e-12) {
            return 0.0;
        }
        return Math.min(1.0, siteFactor);
    }

    /**
     * Curva de explosión (picos + hierbas + desfase + añada + clima de secreción).
     * Sin crowding ni tiempo de vuelo.
     */
    public static double bloom01(@Nullable HiveEntity hive, @Nullable LocalDate day) {
        if (hive == null || day == null) {
            return 0.0;
        }
        String flora = hive.floraType;
        String hexId = hive.hexId;
        double vint = HexNectarRules.vintageFactor(hexId, flora, day.getYear());
        int shift = HexNectarRules.bloomShiftDays(hive, flora);
        int doy = day.getDayOfYear();
        double n = NectarFlow.intensity01(flora, doy, shift, vint);
        if (!HexNectarRules.isSouthernHive(hive)) {
            n *= HexNectarRules.zoneForHive(hive).nectarSeasonMultiplier(doy);
        }
        return Math.max(0.0, Math.min(GameBalanceConfig.nectarIntensityCap, n));
    }

    public static double basePoolKg(@Nullable HiveEntity hive) {
        if (hive != null && HexFlora.isPlantation(hive.floraType)) {
            return Math.max(0.0, GameBalanceConfig.baseDailyNectarKgPlanted);
        }
        return Math.max(0.0, GameBalanceConfig.baseDailyNectarKgNative);
    }

    public static double poolKg(@Nullable HiveEntity hive, @Nullable LocalDate day) {
        return round3(basePoolKg(hive) * bloom01(hive, day));
    }

    /** Demanda de pecoreo sin tiempo ni piscina (obreras × salud × ruido). */
    public static double foragePotentialKg(
            @Nullable HivePopulationState s,
            @Nullable HiveEntity hive,
            int dayKey) {
        if (s == null || s.workersAdult <= 0 || s.queenMode == QueenMode.COLLAPSED) {
            return 0.0;
        }
        if (TranshumanceRules.isInTransit(hive, dayKey)) {
            return 0.0;
        }
        double healthM = HealthHoneyModifier.productionMultiplierForHealth(
                hive != null ? hive.health : 80);
        double feedM = HiveFeedingBonuses.honeyMultiplierForDay(hive, dayKey);
        String hid = hive != null && hive.id != null ? hive.id : "_";
        double noise = GameBalanceConfig.nectarNoiseMin
                + HoneyDailyProduction.deterministicUniform01(hid + ":nectar", dayKey)
                * GameBalanceConfig.nectarNoiseSpan;
        double foragers = s.workersAdult * GameBalanceConfig.foragerFraction;
        return Math.max(0.0, foragers * GameBalanceConfig.kgPerForagerFullFlow * healthM * feedM * noise);
    }

    public static double forageDemandKg(
            @Nullable HivePopulationState s,
            @Nullable HiveEntity hive,
            int dayKey,
            @Nullable Double tempC,
            double skyMult) {
        return foragePotentialKg(s, hive, dayKey) * siteFactor(tempC, skyMult);
    }

    public static final class HiveShare {
        public final String hiveId;
        public final double demandKg;
        public double collectedKg;

        public HiveShare(@NonNull String hiveId, double demandKg) {
            this.hiveId = hiveId;
            this.demandKg = Math.max(0.0, demandKg);
            this.collectedKg = 0.0;
        }
    }

    /** Un hex + una flora del jugador que está liquidando el día. */
    public static final class Patch {
        public final String hexId;
        public final String flora;
        public final String ownerId;
        public final double poolKg;
        public final double siteFactor;
        public final List<HiveShare> hives;
        public double localDemand;
        public double inboundKg;
        public double localCollected;
        public double takenByNeighbors;
        public double leftoverPool;
        public double leftoverDemand;
        public double collectedAbroad;

        public Patch(
                @NonNull String hexId,
                @NonNull String flora,
                @Nullable String ownerId,
                double poolKg,
                double siteFactor,
                @NonNull List<HiveShare> hives) {
            this.hexId = hexId;
            this.flora = HoneyMarketEngine.canonicalFloraKey(flora);
            this.ownerId = ownerId != null ? ownerId : "";
            this.poolKg = Math.max(0.0, poolKg);
            this.siteFactor = Math.max(0.0, siteFactor);
            this.hives = hives;
            double d = 0.0;
            for (HiveShare h : hives) {
                d += h.demandKg;
            }
            this.localDemand = d;
        }

        public String key() {
            return keyOf(hexId, flora);
        }
    }

    /** Estado publicado de un hex (propio de ayer u otro jugador). */
    public static final class NeighborSnap {
        public final String hexId;
        public final String ownerId;
        public final double siteFactor;
        public final Map<String, Double> demandByFlora;
        public final Map<String, Double> leftoverDemandByFlora;
        public final Map<String, Double> leftoverPoolByFlora;

        public NeighborSnap(
                @NonNull String hexId,
                @Nullable String ownerId,
                double siteFactor,
                @Nullable Map<String, Double> demandByFlora,
                @Nullable Map<String, Double> leftoverDemandByFlora,
                @Nullable Map<String, Double> leftoverPoolByFlora) {
            this.hexId = hexId;
            this.ownerId = ownerId != null ? ownerId : "";
            this.siteFactor = Math.max(0.0, siteFactor);
            this.demandByFlora = demandByFlora != null ? demandByFlora : Collections.emptyMap();
            this.leftoverDemandByFlora = leftoverDemandByFlora != null
                    ? leftoverDemandByFlora : Collections.emptyMap();
            this.leftoverPoolByFlora = leftoverPoolByFlora != null
                    ? leftoverPoolByFlora : Collections.emptyMap();
        }

        public double demand(@NonNull String flora) {
            return Math.max(0.0, get(demandByFlora, flora));
        }

        public double leftoverDemand(@NonNull String flora) {
            return Math.max(0.0, get(leftoverDemandByFlora, flora));
        }

        public double leftoverPool(@NonNull String flora) {
            return Math.max(0.0, get(leftoverPoolByFlora, flora));
        }
    }

    public static final class Totals {
        public double fromNeighborsKg;
        public double takenByNeighborsKg;
    }

    public static String keyOf(@Nullable String hexId, @Nullable String flora) {
        String h = hexId != null && !hexId.isEmpty() ? hexId : "_";
        return h + "|" + HoneyMarketEngine.canonicalFloraKey(flora);
    }

    public static Totals resolve(@NonNull List<Patch> ownPatches, @Nullable Map<String, NeighborSnap> ignored) {
        Totals totals = new Totals();
        if (ownPatches.isEmpty()) {
            return totals;
        }
        for (Patch p : ownPatches) {
            p.inboundKg = 0.0;
            p.takenByNeighbors = 0.0;
            p.collectedAbroad = 0.0;
            if (p.siteFactor <= 1e-9) {
                p.localCollected = 0.0;
                p.leftoverPool = p.poolKg;
                p.leftoverDemand = 0.0;
                splitAmongHives(p);
                continue;
            }
            double taken = Math.min(p.poolKg, p.localDemand);
            p.localCollected = taken;
            p.leftoverPool = Math.max(0.0, p.poolKg - taken);
            p.leftoverDemand = Math.max(0.0, p.localDemand - taken);
            splitAmongHives(p);
        }
        return totals;
    }

    private static void splitAmongHives(Patch p) {
        double hiveTotal = p.localCollected + p.collectedAbroad;
        if (p.localDemand <= 1e-9 || p.hives.isEmpty()) {
            return;
        }
        for (HiveShare h : p.hives) {
            h.collectedKg = hiveTotal * (h.demandKg / p.localDemand);
        }
    }

    private static double get(Map<String, Double> m, String flora) {
        if (m == null || flora == null) {
            return 0.0;
        }
        Double v = m.get(HoneyMarketEngine.canonicalFloraKey(flora));
        if (v != null) {
            return v;
        }
        for (Map.Entry<String, Double> e : m.entrySet()) {
            if (HoneyMarketEngine.canonicalFloraKey(e.getKey()).equals(
                    HoneyMarketEngine.canonicalFloraKey(flora))) {
                return e.getValue() != null ? e.getValue() : 0.0;
            }
        }
        return 0.0;
    }

    private static double round3(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }
}
