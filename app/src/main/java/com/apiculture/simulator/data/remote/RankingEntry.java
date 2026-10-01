package com.apiculture.simulator.data.remote;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** Una fila del ranking leída del servidor. */
public class RankingEntry {

    public final int rank;
    @NonNull
    public final String uid;
    @NonNull
    public final String honeyBrand;
    @NonNull
    public final String playerName;
    @NonNull
    public final String valueLabel;
    @Nullable
    public final String photoBase64;
    public final boolean isSelf;

    public RankingEntry(int rank, @NonNull String uid, @NonNull String honeyBrand,
            @NonNull String playerName, @NonNull String valueLabel,
            @Nullable String photoBase64, boolean isSelf) {
        this.rank = rank;
        this.uid = uid;
        this.honeyBrand = honeyBrand;
        this.playerName = playerName;
        this.valueLabel = valueLabel;
        this.photoBase64 = photoBase64;
        this.isSelf = isSelf;
    }
}
