package com.apiculture.simulator.data.remote;

import androidx.annotation.NonNull;

/** Una fila del ranking global leída de Firestore. */
public class RankingEntry {

    public final int rank;
    @NonNull
    public final String honeyBrand;
    @NonNull
    public final String playerName;
    @NonNull
    public final String valueLabel;

    public RankingEntry(int rank, @NonNull String honeyBrand, @NonNull String playerName,
            @NonNull String valueLabel) {
        this.rank = rank;
        this.honeyBrand = honeyBrand;
        this.playerName = playerName;
        this.valueLabel = valueLabel;
    }
}
