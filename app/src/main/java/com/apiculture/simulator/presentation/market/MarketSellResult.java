package com.apiculture.simulator.presentation.market;

/** Resultado de una venta al mercado (precio según cobertura global en el momento de la venta). */
public final class MarketSellResult {

    public final boolean success;
    public final double unitPriceEurPerKg;
    public final String errorMessage;

    private MarketSellResult(boolean success, double unitPriceEurPerKg, String errorMessage) {
        this.success = success;
        this.unitPriceEurPerKg = unitPriceEurPerKg;
        this.errorMessage = errorMessage;
    }

    public static MarketSellResult ok(double unitPriceEurPerKg) {
        return new MarketSellResult(true, unitPriceEurPerKg, null);
    }

    public static MarketSellResult fail(String message) {
        return new MarketSellResult(false, 0.0, message);
    }
}
