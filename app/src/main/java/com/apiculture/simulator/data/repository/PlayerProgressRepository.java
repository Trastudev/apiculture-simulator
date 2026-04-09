package com.apiculture.simulator.data.repository;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;

/**
 * Persiste nivel y XP del jugador por {@code uid} de Firebase (supervivencia al reiniciar la app).
 */
public class PlayerProgressRepository {

    private static final String PREFS = "player_progress_v1";
    private static final String KEY_LEVEL = "level_";
    private static final String KEY_XP = "xp_";

    private final SharedPreferences prefs;

    public PlayerProgressRepository(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public int getLevel(@Nullable String uid) {
        if (uid == null || uid.isEmpty()) {
            return 0;
        }
        return prefs.getInt(KEY_LEVEL + uid, 0);
    }

    public int getXp(@Nullable String uid) {
        if (uid == null || uid.isEmpty()) {
            return 0;
        }
        return prefs.getInt(KEY_XP + uid, 0);
    }

    public void save(@Nullable String uid, int level, int xp) {
        if (uid == null || uid.isEmpty()) {
            return;
        }
        prefs.edit()
                .putInt(KEY_LEVEL + uid, Math.max(0, level))
                .putInt(KEY_XP + uid, Math.max(0, xp))
                .apply();
    }
}
