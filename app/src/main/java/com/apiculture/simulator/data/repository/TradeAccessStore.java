package com.apiculture.simulator.data.repository;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.apiculture.simulator.ApicultureApp;
import com.apiculture.simulator.domain.game.TradeAccessRules;

import org.json.JSONArray;

/** Cuotas ya pagadas. Cada puerto y cada mercado internacional se pagan aparte. */
public final class TradeAccessStore {

    private static final String PREFS = "trade_access";

    private TradeAccessStore() {
    }

    public static void clear(@NonNull Context context, @Nullable String ownerId) {
        if (ownerId == null || ownerId.isEmpty()) {
            return;
        }
        prefs(context).edit()
                .remove(key(ownerId, "port"))
                .remove(key(ownerId, "ports"))
                .remove(key(ownerId, "markets"))
                .commit();
    }

    public static boolean portPaid(@NonNull Context context, @Nullable String ownerId,
            @Nullable String portId) {
        return contains(context, ownerId, "ports", portId);
    }

    public static boolean marketPaid(@NonNull Context context, @Nullable String ownerId,
            @Nullable String marketId) {
        return contains(context, ownerId, "markets", marketId);
    }

    public static int paidPortCount(@NonNull Context context, @Nullable String ownerId) {
        return paidCount(context, ownerId, "ports");
    }

    public static int paidMarketCount(@NonNull Context context, @Nullable String ownerId) {
        return paidCount(context, ownerId, "markets");
    }

    @Nullable
    public static String payPort(@NonNull Context context, @NonNull EconomyRepository economy,
            @Nullable String ownerId, @Nullable String portId) {
        if (ownerId == null || portId == null || portId.isEmpty()) {
            return "Puerto desconocido.";
        }
        if (portPaid(context, ownerId, portId)) {
            return null;
        }
        int level = level(context, ownerId);
        if (!TradeAccessRules.portLevelReached(level)) {
            return "Este puerto se desbloquea al nivel " + TradeAccessRules.PORT_LEVEL + ".";
        }
        if (!economy.trySpend(TradeAccessRules.PORT_FEE_B, "Cuota de puerto")) {
            return "Saldo insuficiente (" + TradeAccessRules.PORT_FEE_B + " B).";
        }
        add(context, ownerId, "ports", portId);
        return null;
    }

    @Nullable
    public static String payMarket(@NonNull Context context, @NonNull EconomyRepository economy,
            @Nullable String ownerId, @Nullable String marketId) {
        if (ownerId == null || marketId == null || marketId.isEmpty()) {
            return "Mercado desconocido.";
        }
        if (marketPaid(context, ownerId, marketId)) {
            return null;
        }
        int level = level(context, ownerId);
        if (!TradeAccessRules.marketLevelReached(level)) {
            return "El mercado internacional se desbloquea al nivel " + TradeAccessRules.MARKET_LEVEL + ".";
        }
        if (!economy.trySpend(TradeAccessRules.MARKET_FEE_B, "Cuota de mercado internacional")) {
            return "Saldo insuficiente (" + TradeAccessRules.MARKET_FEE_B + " B).";
        }
        add(context, ownerId, "markets", marketId);
        return null;
    }

    private static int paidCount(@NonNull Context context, @Nullable String ownerId,
            @NonNull String field) {
        if (ownerId == null || ownerId.isEmpty()) {
            return 0;
        }
        try {
            return new JSONArray(prefs(context).getString(key(ownerId, field), "[]")).length();
        } catch (Exception e) {
            return 0;
        }
    }

    private static boolean contains(@NonNull Context context, @Nullable String ownerId,
            @NonNull String field, @Nullable String id) {
        if (ownerId == null || id == null || id.isEmpty()) {
            return false;
        }
        try {
            JSONArray arr = new JSONArray(prefs(context).getString(key(ownerId, field), "[]"));
            for (int i = 0; i < arr.length(); i++) {
                if (id.equals(arr.optString(i))) {
                    return true;
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private static void add(@NonNull Context context, @NonNull String ownerId,
            @NonNull String field, @NonNull String id) {
        JSONArray arr;
        try {
            arr = new JSONArray(prefs(context).getString(key(ownerId, field), "[]"));
        } catch (Exception e) {
            arr = new JSONArray();
        }
        arr.put(id);
        prefs(context).edit().putString(key(ownerId, field), arr.toString()).commit();
    }

    private static int level(@NonNull Context context, @NonNull String ownerId) {
        return ((ApicultureApp) context.getApplicationContext())
                .getPlayerProgressRepository().getLevel(ownerId);
    }

    @NonNull
    private static SharedPreferences prefs(@NonNull Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String key(String ownerId, String field) {
        return ownerId + "." + field;
    }
}
