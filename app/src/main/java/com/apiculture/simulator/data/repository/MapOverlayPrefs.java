package com.apiculture.simulator.data.repository;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Visibilidad y filtros de la malla hexagonal en el mapa (independiente de la región).
 */
public final class MapOverlayPrefs {

    private static final String PREFS = "map_overlay_v1";
    private static final String KEY_MESH = "mesh_visible";
    private static final String KEY_OWN = "filter_own";
    private static final String KEY_OTHERS = "filter_others";
    private static final String KEY_BUYABLE = "filter_buyable";

    private MapOverlayPrefs() {
    }

    private static SharedPreferences prefs(Context appContext) {
        return appContext.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** La malla está oculta por defecto: el mapa se lee como Google Maps + iconos de colmena. */
    public static boolean isMeshVisible(Context appContext) {
        return prefs(appContext).getBoolean(KEY_MESH, false);
    }

    public static void setMeshVisible(Context appContext, boolean visible) {
        prefs(appContext).edit().putBoolean(KEY_MESH, visible).apply();
    }

    public static boolean isOwnFilter(Context appContext) {
        return prefs(appContext).getBoolean(KEY_OWN, false);
    }

    public static void setOwnFilter(Context appContext, boolean on) {
        prefs(appContext).edit().putBoolean(KEY_OWN, on).apply();
    }

    public static boolean isOthersFilter(Context appContext) {
        return prefs(appContext).getBoolean(KEY_OTHERS, false);
    }

    public static void setOthersFilter(Context appContext, boolean on) {
        prefs(appContext).edit().putBoolean(KEY_OTHERS, on).apply();
    }

    public static boolean isBuyableFilter(Context appContext) {
        return prefs(appContext).getBoolean(KEY_BUYABLE, false);
    }

    public static void setBuyableFilter(Context appContext, boolean on) {
        prefs(appContext).edit().putBoolean(KEY_BUYABLE, on).apply();
    }
}
