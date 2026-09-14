package com.apiculture.simulator.presentation.market;

import androidx.annotation.NonNull;

/** Fila del mercado: un tipo de miel. */
public final class MarketPillUi {

    public final @NonNull String floraKey;
    public final @NonNull String title;
    public final double demandKg;
    public final double filledKg;
    public final int fillPercent;
    public final double priceEurPerKg;
    /** Recargo/descuento por oferta, −25…+25. */
    public final int priceAdjPercent;
    public final double userStockKg;

    public MarketPillUi(
            @NonNull String floraKey,
            @NonNull String title,
            double demandKg,
            double filledKg,
            int fillPercent,
            double priceEurPerKg,
            int priceAdjPercent,
            double userStockKg) {
        this.floraKey = floraKey;
        this.title = title;
        this.demandKg = demandKg;
        this.filledKg = filledKg;
        this.fillPercent = fillPercent;
        this.priceEurPerKg = priceEurPerKg;
        this.priceAdjPercent = priceAdjPercent;
        this.userStockKg = userStockKg;
    }
}
