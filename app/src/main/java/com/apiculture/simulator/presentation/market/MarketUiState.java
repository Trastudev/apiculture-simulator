package com.apiculture.simulator.presentation.market;

import androidx.annotation.NonNull;

import java.util.Collections;
import java.util.List;

public final class MarketUiState {

    public final double balanceEur;
    public final double totalHoneyKg;
    public final int playerCount;
    public final @NonNull List<MarketPillUi> pills;

    public MarketUiState(
            double balanceEur,
            double totalHoneyKg,
            int playerCount,
            @NonNull List<MarketPillUi> pills) {
        this.balanceEur = balanceEur;
        this.totalHoneyKg = totalHoneyKg;
        this.playerCount = Math.max(1, playerCount);
        this.pills = Collections.unmodifiableList(pills);
    }

    boolean sameVisual(MarketUiState other) {
        if (other == null) {
            return false;
        }
        if (playerCount != other.playerCount) {
            return false;
        }
        if (Math.abs(balanceEur - other.balanceEur) > 1e-6) {
            return false;
        }
        if (Math.abs(totalHoneyKg - other.totalHoneyKg) > 1e-6) {
            return false;
        }
        if (pills.size() != other.pills.size()) {
            return false;
        }
        for (int i = 0; i < pills.size(); i++) {
            if (!pills.get(i).sameVisual(other.pills.get(i))) {
                return false;
            }
        }
        return true;
    }
}
