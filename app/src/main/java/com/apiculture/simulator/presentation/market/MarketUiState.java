package com.apiculture.simulator.presentation.market;

import androidx.annotation.NonNull;

import java.util.Collections;
import java.util.List;

public final class MarketUiState {

    public final double balanceEur;
    public final double totalHoneyKg;
    public final @NonNull List<MarketPillUi> pills;

    public MarketUiState(double balanceEur, double totalHoneyKg, @NonNull List<MarketPillUi> pills) {
        this.balanceEur = balanceEur;
        this.totalHoneyKg = totalHoneyKg;
        this.pills = Collections.unmodifiableList(pills);
    }
}
