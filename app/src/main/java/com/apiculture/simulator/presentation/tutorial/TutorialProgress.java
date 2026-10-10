package com.apiculture.simulator.presentation.tutorial;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.HashSet;
import java.util.Set;

/** Progreso del tutorial por usuario. Las claves nombran el capítulo. */
public final class TutorialProgress {

    private static final String PREFS = "tutorial_progress";

    private final SharedPreferences prefs;

    public TutorialProgress(@NonNull Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public boolean migrated(@NonNull String uid) {
        return prefs.getBoolean(key(uid, "migrated"), false);
    }

    /** Quien ya tenía terrenos no ve el capítulo 1 hasta que lo pida en el menú. */
    public void migrateVeteran(@NonNull String uid, boolean hasParcels) {
        if (migrated(uid)) {
            return;
        }
        SharedPreferences.Editor edit = prefs.edit();
        if (hasParcels) {
            for (TutorialChapter chapter : TutorialChapter.values()) {
                // El obrador es nuevo también para quien ya jugaba.
                if (chapter != TutorialChapter.CLIMATE && chapter != TutorialChapter.ORDERS
                        && chapter != TutorialChapter.WORKSHOP
                        && chapter != TutorialChapter.WORKSHOP_PACKING) {
                    edit.putBoolean(doneKey(uid, chapter), true);
                }
            }
        }
        edit.putBoolean(key(uid, "migrated"), true).apply();
    }

    public boolean isDone(@NonNull String uid, @NonNull TutorialChapter chapter) {
        return prefs.getBoolean(doneKey(uid, chapter), false);
    }

    public void setDone(@NonNull String uid, @NonNull TutorialChapter chapter, boolean done) {
        prefs.edit().putBoolean(doneKey(uid, chapter), done).apply();
    }

    /** La primera llamada devuelve true y deja el aviso marcado. */
    public boolean consumeOnce(@NonNull String uid, @NonNull String flag) {
        String stored = key(uid, flag);
        if (prefs.getBoolean(stored, false)) {
            return false;
        }
        prefs.edit().putBoolean(stored, true).apply();
        return true;
    }

    public void clearOnce(@NonNull String uid, @NonNull String flag) {
        prefs.edit().putBoolean(key(uid, flag), false).apply();
    }

    public int stepIndex(@NonNull String uid, @NonNull TutorialChapter chapter) {
        return prefs.getInt(key(uid, chapter.name() + "_step"), 0);
    }

    public void setStepIndex(@NonNull String uid, @NonNull TutorialChapter chapter, int index) {
        prefs.edit().putInt(key(uid, chapter.name() + "_step"), Math.max(0, index)).apply();
    }

    @Nullable
    public TutorialChapter active(@NonNull String uid) {
        String name = prefs.getString(key(uid, "active"), null);
        if (name == null || name.isEmpty()) {
            return null;
        }
        try {
            return TutorialChapter.valueOf(name);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public void setActive(@NonNull String uid, @Nullable TutorialChapter chapter) {
        SharedPreferences.Editor edit = prefs.edit();
        if (chapter == null) {
            edit.remove(key(uid, "active"));
        } else {
            edit.putString(key(uid, "active"), chapter.name());
        }
        edit.apply();
    }

    public boolean climateShown(@NonNull String uid, @NonNull String label) {
        return climates(uid).contains(label);
    }

    public void markClimate(@NonNull String uid, @NonNull String label) {
        Set<String> next = new HashSet<>(climates(uid));
        next.add(label);
        prefs.edit().putStringSet(key(uid, "climates"), next).apply();
    }

    @NonNull
    private Set<String> climates(@NonNull String uid) {
        Set<String> stored = prefs.getStringSet(key(uid, "climates"), null);
        return stored != null ? stored : new HashSet<>();
    }

    @NonNull
    private static String doneKey(@NonNull String uid, @NonNull TutorialChapter chapter) {
        return key(uid, chapter.name() + "_done");
    }

    @NonNull
    private static String key(@NonNull String uid, @NonNull String name) {
        return uid + "_" + name;
    }
}
