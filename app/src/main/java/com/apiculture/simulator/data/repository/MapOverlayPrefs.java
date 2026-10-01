package com.apiculture.simulator.data.repository;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;

import com.apiculture.simulator.domain.parcel.HexFlora;

/**
 * Preferencias de presentación del mapa hexagonal.
 */
public final class MapOverlayPrefs {

    private static final String PREFS = "map_overlay_v1";
    private static final String KEY_FLORA = "flora_filter";

    private MapOverlayPrefs() {
    }

    private static SharedPreferences prefs(Context appContext) {
        return appContext.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Flora nativa a pintar; {@code null} = todas. */
    @Nullable
    public static String getFloraFilter(Context appContext) {
        String raw = prefs(appContext).getString(KEY_FLORA, "");
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        return HexFlora.canonicalKey(raw);
    }

    public static void setFloraFilter(Context appContext, @Nullable String floraKey) {
        String store = floraKey == null || floraKey.trim().isEmpty()
                ? ""
                : HexFlora.canonicalKey(floraKey);
        prefs(appContext).edit().putString(KEY_FLORA, store).apply();
    }

    private static final String KEY_HUD_TX = "hud_tx";
    private static final String KEY_HUD_TY = "hud_ty";
    private static final String KEY_HUD_MOVED = "hud_moved";

    public static boolean hasHudOffset(Context appContext) {
        return prefs(appContext).getBoolean(KEY_HUD_MOVED, false);
    }

    public static float getHudOffsetX(Context appContext) {
        return prefs(appContext).getFloat(KEY_HUD_TX, 0f);
    }

    public static float getHudOffsetY(Context appContext) {
        return prefs(appContext).getFloat(KEY_HUD_TY, 0f);
    }

    public static void setHudOffset(Context appContext, float translationX, float translationY) {
        prefs(appContext).edit()
                .putBoolean(KEY_HUD_MOVED, true)
                .putFloat(KEY_HUD_TX, translationX)
                .putFloat(KEY_HUD_TY, translationY)
                .apply();
    }
}
