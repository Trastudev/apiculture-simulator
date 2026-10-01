package com.apiculture.simulator.domain.game;

import androidx.annotation.Nullable;

import com.apiculture.simulator.data.local.entity.HiveEntity;
import com.apiculture.simulator.domain.market.HoneyMarketEngine;
import com.apiculture.simulator.domain.parcel.HexFlora;
import com.apiculture.simulator.domain.parcel.HexParcel;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Convenios NPC: umbral de floración importante, pago, capas silenciosas y coste por km y colmena.
 */
public final class PollinationContractRules {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_RETURNING = "RETURNING";
    public static final String STATUS_SETTLED = "SETTLED";

    public static boolean canAddHives(int acceptedDayKey, int todayKey, @Nullable String status) {
        return STATUS_ACTIVE.equals(status) && acceptedDayKey > 0 && todayKey >= acceptedDayKey;
    }

    private PollinationContractRules() {
    }

    public static double bloomThreshold() {
        return Math.max(0.05, Math.min(1.0, GameBalanceConfig.pollinationBloomThreshold));
    }

    public static double bPerKmPerHive() {
        return Math.max(0.0, GameBalanceConfig.pollinationBPerKmPerHive);
    }

    /** Coste de una colmena para esa distancia en km. */
    public static int travelCostPerHiveKm(double km) {
        if (Double.isNaN(km) || km < 0 || km > 20_000) {
            return 99_999;
        }
        return (int) Math.round(km * bPerKmPerHive());
    }

    public static int travelCostOne(@Nullable HiveEntity hive, double destLat, double destLng) {
        if (hive == null || Double.isNaN(destLat) || Double.isNaN(destLng)) {
            return 99_999;
        }
        return travelCostPerHiveKm(TranshumanceRules.haversineKm(hive.lat, hive.lng, destLat, destLng));
    }

    /** Cada colmena paga sus propios km hasta el campo. */
    public static int travelCostForHives(@Nullable List<HiveEntity> hives, double destLat, double destLng) {
        if (hives == null || hives.isEmpty() || Double.isNaN(destLat) || Double.isNaN(destLng)) {
            return 99_999;
        }
        int sum = 0;
        for (int i = 0; i < hives.size(); i++) {
            int one = travelCostOne(hives.get(i), destLat, destLng);
            if (one >= 99_999 || sum > Integer.MAX_VALUE - one) {
                return 99_999;
            }
            sum += one;
        }
        return sum;
    }

    public static double bloom01(@Nullable HexParcel parcel, @Nullable String flora, @Nullable LocalDate day) {
        if (parcel == null || flora == null || day == null) {
            return 0.0;
        }
        HiveEntity probe = new HiveEntity();
        probe.id = "probe";
        probe.floraType = flora;
        probe.hexId = parcel.id;
        probe.lat = parcel.centroidLat;
        probe.lng = parcel.centroidLon;
        probe.elevationMeters = parcel.maxElevationMeters != null ? parcel.maxElevationMeters : 200;
        return HexNectarPool.bloom01(probe, day);
    }

    public static boolean isImportantBloom(@Nullable HexParcel parcel, @Nullable String flora,
            @Nullable LocalDate day) {
        return bloom01(parcel, flora, day) + 1e-9 >= bloomThreshold();
    }

    /**
     * Cierra tras haber visto el pico ({@code sawPeak}) y bajar del umbral.
     */
    public static boolean shouldClose(boolean sawPeak, double bloom01) {
        return shouldClose(sawPeak, bloom01, 0, 0);
    }

    /**
     * Cierra al cumplir los días del tramo, o si la floración cae del 40 % (también en la bajada).
     */
    public static boolean shouldClose(boolean sawPeak, double bloom01, int dayKey, int dueDayKey) {
        if (dueDayKey > 0 && dayKey >= dueDayKey) {
            return true;
        }
        return sawPeak && bloom01 < MIN_BLOOM01;
    }

    public static int horizonDays() {
        return Math.max(1, GameBalanceConfig.pollinationHorizonDays);
    }

    public static double pollinationPct(double collectedKg, double poolKg) {
        if (poolKg <= 1e-9) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, collectedKg / poolKg));
    }

    public static int payoutB(double collectedKg, double poolKg, PollinationPayTerms terms) {
        if (terms == null) {
            return 0;
        }
        double pct = pollinationPct(collectedKg, poolKg);
        if (pct + 1e-9 < terms.minPct) {
            return 0;
        }
        double extraPoints = Math.max(0.0, (Math.min(1.0, pct) - terms.minPct) * 100.0);
        return terms.payB + (int) Math.round(extraPoints * terms.extraBPerPoint);
    }

    public static boolean reservesOpen(double occupancy01) {
        return true;
    }

    public static int maxLayers(double occupancy01, boolean reservesAlreadyOpen) {
        if (occupancy01 + 1e-9 >= GameBalanceConfig.pollinationLayer2Occupancy && reservesAlreadyOpen) {
            return Math.max(1, GameBalanceConfig.pollinationMaxLayersSaturated);
        }
        return Math.max(1, GameBalanceConfig.pollinationMaxLayers);
    }

    public static double occupancy(int activeContracts, int openFarms) {
        if (openFarms <= 0) {
            return 0.0;
        }
        return Math.max(0.0, activeContracts / (double) openFarms);
    }

    /**
     * Términos según el tipo de campo y los días de floración de esa finca.
     * Pago = tarifa diaria (miel barata–cara) × días de trabajo + prima de llamada.
     */
    public static PollinationPayTerms termsFor(@Nullable HexParcel parcel, @Nullable String flora) {
        return termsForFlora(flora, workDays(parcel, flora));
    }

    public static PollinationPayTerms termsForFlora(@Nullable String flora) {
        return termsForFlora(flora, workDays(null, flora));
    }

    public static final double MIN_POLLINATION_PCT = 0.40;
    public static final double MAX_MIN_POLLINATION_PCT = 0.60;
    public static final double MIN_PAY_BONUS_PER_POINT = 0.015;
    /** Suelo de floración de un tramo: 40 % y hacia arriba, también después del pico. */
    public static final double MIN_BLOOM01 = 0.40;

    public static PollinationPayTerms termsForFlora(@Nullable String flora, int workDays) {
        return termsForFlora(flora, workDays, 0, 0);
    }

    public static PollinationPayTerms termsForFlora(@Nullable String flora, int workDays,
            int startDoy, int endDoy) {
        return termsForFlora(flora, workDays, startDoy, endDoy, null);
    }

    public static PollinationPayTerms termsForFlora(@Nullable String flora, int workDays,
            int startDoy, int endDoy, @Nullable String hexId) {
        int days = Math.max(1, workDays);
        double daily = payPerDayB(flora);
        int callout = Math.max(0, GameBalanceConfig.pollinationCalloutB);
        int minPoints = minPollinationPoints(hexId, flora, startDoy);
        double minPct = minPoints / 100.0;
        int pay = (int) Math.round((daily * days + callout) * payMultiplierForMinPoints(minPoints));
        int extra = Math.max(1, (int) Math.round(daily / 3.0));
        return new PollinationPayTerms(minPct, pay, extra, days, daily,
                startDoy, endDoy, MIN_BLOOM01);
    }

    /** 40–60 % estable por finca, cultivo y tramo. */
    public static int minPollinationPoints(@Nullable String hexId, @Nullable String flora, int startDoy) {
        if (startDoy <= 0) {
            return 40;
        }
        String key = (hexId != null ? hexId : "") + "|" + HexFlora.canonicalKey(flora) + "|" + startDoy;
        int h = key.hashCode();
        if (h == Integer.MIN_VALUE) {
            h = 0;
        }
        return 40 + (Math.abs(h) % 21);
    }

    public static double payMultiplierForMinPoints(int minPoints) {
        int extra = Math.max(0, Math.min(20, minPoints - 40));
        return 1.0 + MIN_PAY_BONUS_PER_POINT * extra;
    }

    /** Kg/día de néctar de esa plantación a mielada plena. */
    public static double dailyFlowerHoneyKg(@Nullable String flora) {
        if (HexFlora.isPlantation(flora)) {
            return Math.max(0.0, GameBalanceConfig.baseDailyNectarKgPlanted);
        }
        return Math.max(0.0, GameBalanceConfig.baseDailyNectarKgNative);
    }

    /** 100 % de polinización = producción diaria de las flores × días de contrato. */
    public static double targetPoolKg(@Nullable String flora, int workDays) {
        return Math.round(dailyFlowerHoneyKg(flora) * Math.max(1, workDays) * 100.0) / 100.0;
    }

    /**
     * Tarifa diaria: mínimo en la miel más barata de la tabla, máximo en la más cara.
     */
    public static double payPerDayB(@Nullable String flora) {
        double minDay = Math.max(1.0, GameBalanceConfig.pollinationPayPerDayMin);
        double maxDay = Math.max(minDay, GameBalanceConfig.pollinationPayPerDayMax);
        double honey = HoneyMarketEngine.priceCeilingEurPerKgForFlora(flora);
        double minH = HoneyMarketEngine.MIN_PRICE_EUR_PER_KG;
        double maxH = HoneyMarketEngine.MAX_PRICE_EUR_PER_KG;
        double t = 0.0;
        if (maxH - minH > 1e-9) {
            t = (honey - minH) / (maxH - minH);
        }
        t = Math.max(0.0, Math.min(1.0, t));
        return Math.round((minDay + (maxDay - minDay) * t) * 100.0) / 100.0;
    }

    /** Días de floración (ventana de néctar) en los que las colmenas tienen que estar. */
    public static int workDays(@Nullable HexParcel parcel, @Nullable String flora) {
        if (flora == null || flora.isEmpty()) {
            return 1;
        }
        List<FloraBloomWindow.Span> spans = parcel != null
                ? FloraBloomWindow.spansForParcel(flora, parcel)
                : FloraBloomWindow.spans(flora, 0);
        int best = 0;
        for (int i = 0; i < spans.size(); i++) {
            best = Math.max(best, FloraBloomWindow.daysInSpan(spans.get(i)));
        }
        return Math.max(1, best);
    }

    public static double minPctForFlora(@Nullable String flora) {
        return MIN_POLLINATION_PCT;
    }

    /**
     * Primer tramo del año (doy 1–365) con floración importante; vacío si no hay.
     */
    public static FloraBloomWindow.Span importantSpan(@Nullable HexParcel parcel, @Nullable String flora) {
        if (parcel == null || flora == null) {
            return null;
        }
        int start = -1;
        int end = -1;
        LocalDate year = LocalDate.of(2026, 1, 1);
        for (int doy = 1; doy <= 365; doy++) {
            boolean on = isImportantBloom(parcel, flora, year.withDayOfYear(doy));
            if (on && start < 0) {
                start = doy;
            }
            if (start > 0) {
                if (on) {
                    end = doy;
                } else {
                    break;
                }
            }
        }
        if (start < 0 || end < 0) {
            return null;
        }
        return new FloraBloomWindow.Span(start, end);
    }

    public static String formatImportantWindowEs(@Nullable HexParcel parcel, @Nullable String flora) {
        return FloraBloomWindow.formatEsForParcel(flora, parcel);
    }
}
