package com.apiculture.simulator.data.repository;

import android.content.Context;
import android.content.SharedPreferences;

import com.apiculture.simulator.domain.map.PlayableMapRegion;

public final class MapRegionPrefs {

    private static final String PREFS = "map_region_v1";
    private static final String KEY = "region";

    private MapRegionPrefs() {
    }

    public static PlayableMapRegion get(Context appContext) {
        SharedPreferences p = appContext.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return PlayableMapRegion.fromPrefsValue(p.getString(KEY, PlayableMapRegion.IBERIA.prefsValue()));
    }

    public static void set(Context appContext, PlayableMapRegion region) {
        if (region == null) {
            region = PlayableMapRegion.IBERIA;
        }
        appContext.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY, region.prefsValue())
                .apply();
    }
}
