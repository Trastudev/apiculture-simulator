package com.apiculture.simulator.data.repository;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.Nullable;

import com.apiculture.simulator.domain.game.LevelSystem;
import com.apiculture.simulator.presentation.common.LevelUpDialog;

/**
 * Persiste nivel y XP del jugador por {@code uid} de Firebase (supervivencia al reiniciar la app).
 */
public class PlayerProgressRepository {

    private static final String PREFS = "player_progress_v1";
    private static final String KEY_LEVEL = "level_";
    private static final String KEY_XP = "xp_";

    private final SharedPreferences prefs;
    private final Context appContext;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Nullable
    private Runnable progressChangedCallback;

    public PlayerProgressRepository(Context context) {
        this.appContext = context.getApplicationContext();
        prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public void setProgressChangedCallback(@Nullable Runnable callback) {
        progressChangedCallback = callback;
    }

    private void notifyProgressChanged() {
        if (progressChangedCallback != null) {
            progressChangedCallback.run();
        }
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

    /**
     * Suma XP, sube de nivel si toca y persiste. Devuelve el estado resultante (o el actual si no hay uid/XP).
     */
    public LevelSystem.Result addXp(@Nullable String uid, int amount) {
        int level = getLevel(uid);
        int xp = getXp(uid);
        LevelSystem.Result result = LevelSystem.addXp(level, xp, amount);
        if (uid != null && !uid.isEmpty() && amount > 0) {
            save(uid, result.level, result.xp);
            final int prevLevel = level;
            if (result.level > prevLevel) {
                mainHandler.post(() -> LevelUpDialog.show(appContext, prevLevel, result.level));
            }
        }
        return result;
    }

    public void save(@Nullable String uid, int level, int xp) {
        if (uid == null || uid.isEmpty()) {
            return;
        }
        prefs.edit()
                .putInt(KEY_LEVEL + uid, Math.max(0, level))
                .putInt(KEY_XP + uid, Math.max(0, xp))
                .apply();
        notifyProgressChanged();
    }

    /** Nivel 0 y 0 XP al reiniciar partida (escritura síncrona para no perder el push a la nube). */
    public void resetToNewGame(@Nullable String uid) {
        if (uid == null || uid.isEmpty()) {
            return;
        }
        prefs.edit()
                .putInt(KEY_LEVEL + uid, 0)
                .putInt(KEY_XP + uid, 0)
                .commit();
        notifyProgressChanged();
    }

    /** Aplica nivel/XP desde Firestore sin disparar el callback de push. */
    public void applyFromCloud(@Nullable String uid, int level, int xp) {
        if (uid == null || uid.isEmpty()) {
            return;
        }
        prefs.edit()
                .putInt(KEY_LEVEL + uid, Math.max(0, level))
                .putInt(KEY_XP + uid, Math.max(0, xp))
                .apply();
    }
}
