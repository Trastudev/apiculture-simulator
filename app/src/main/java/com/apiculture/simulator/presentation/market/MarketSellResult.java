package com.apiculture.simulator.presentation.market;

/** Resultado de una venta al mercado (precio según cobertura global en el momento de la venta). */
public final class MarketSellResult {

    public final boolean success;
    public final double unitPriceEurPerKg;
    public final String errorMessage;
    public final boolean truckDispatched;

    private MarketSellResult(boolean success, double unitPriceEurPerKg, String errorMessage,
            boolean truckDispatched) {
        this.success = success;
        this.unitPriceEurPerKg = unitPriceEurPerKg;
        this.errorMessage = errorMessage;
        this.truckDispatched = truckDispatched;
    }

    public static MarketSellResult ok(double unitPriceEurPerKg) {
        return new MarketSellResult(true, unitPriceEurPerKg, null, false);
    }

    public MarketSellResult withDispatched(boolean dispatched) {
        return new MarketSellResult(success, unitPriceEurPerKg, errorMessage, dispatched);
    }

    public static MarketSellResult fail(String message) {
        return new MarketSellResult(false, 0.0, message, false);
    }
}
