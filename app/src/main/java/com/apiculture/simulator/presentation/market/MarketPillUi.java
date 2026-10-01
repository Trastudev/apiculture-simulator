package com.apiculture.simulator.presentation.market;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Arrays;

/** Fila del mercado: un tipo de miel. */
public final class MarketPillUi {

    public final @NonNull String floraKey;
    public final @NonNull String title;
    public final double priceEurPerKg;
    public final double userStockKg;
    public final @NonNull double[] priceHistory7d;
    @Nullable
    public final String priceNote;
    public final double demandLeftKg;

    public MarketPillUi(
            @NonNull String floraKey,
            @NonNull String title,
            double priceEurPerKg,
            double userStockKg,
            @NonNull double[] priceHistory7d) {
        this(floraKey, title, priceEurPerKg, userStockKg, priceHistory7d, null, Double.POSITIVE_INFINITY);
    }

    public MarketPillUi(
            @NonNull String floraKey,
            @NonNull String title,
            double priceEurPerKg,
            double userStockKg,
            @NonNull double[] priceHistory7d,
            @Nullable String priceNote) {
        this(floraKey, title, priceEurPerKg, userStockKg, priceHistory7d, priceNote, Double.POSITIVE_INFINITY);
    }

    public MarketPillUi(
            @NonNull String floraKey,
            @NonNull String title,
            double priceEurPerKg,
            double userStockKg,
            @NonNull double[] priceHistory7d,
            @Nullable String priceNote,
            double demandLeftKg) {
        this.floraKey = floraKey;
        this.title = title;
        this.priceEurPerKg = priceEurPerKg;
        this.userStockKg = userStockKg;
        this.priceHistory7d = priceHistory7d.length == 0 ? new double[0] : priceHistory7d.clone();
        this.priceNote = priceNote;
        this.demandLeftKg = demandLeftKg;
    }

    public double maxSellKg() {
        return Math.round(Math.max(0.0, Math.min(userStockKg, demandLeftKg)) * 100.0) / 100.0;
    }

    boolean sameVisual(MarketPillUi other) {
        if (other == null) {
            return false;
        }
        if (!floraKey.equals(other.floraKey)) {
            return false;
        }
        if (Math.abs(priceEurPerKg - other.priceEurPerKg) >= 1e-4
                || Math.abs(userStockKg - other.userStockKg) >= 1e-4) {
            return false;
        }
        return Arrays.equals(priceHistory7d, other.priceHistory7d);
    }
}
