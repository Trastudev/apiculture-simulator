package com.apiculture.simulator.data.repository;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** Comandas entregadas. Los contratos liquidados se cuentan en la base local. */
public final class RankingCounters {

    private static final String PREFS = "ranking_counters";

    private RankingCounters() {
    }

    public static void recordOrder(@NonNull Context context, @Nullable String ownerId) {
        if (ownerId == null || ownerId.isEmpty()) {
            return;
        }
        SharedPreferences prefs = prefs(context);
        String key = ownerId + ":orders";
        prefs.edit().putInt(key, prefs.getInt(key, 0) + 1).apply();
    }

    public static int orders(@NonNull Context context, @Nullable String ownerId) {
        if (ownerId == null || ownerId.isEmpty()) {
            return 0;
        }
        return prefs(context).getInt(ownerId + ":orders", 0);
    }

    public static void clear(@NonNull Context context, @Nullable String ownerId) {
        if (ownerId == null || ownerId.isEmpty()) {
            return;
        }
        prefs(context).edit().remove(ownerId + ":orders").apply();
    }

    @NonNull
    private static SharedPreferences prefs(@NonNull Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
