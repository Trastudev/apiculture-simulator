package com.apiculture.simulator.data.repository;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

/** Idioma de la partida. Vacío: el del teléfono. */
public final class GameLocale {

    private static final String PREFS = "game_locale";
    private static final String KEY = "tag";

    private GameLocale() {
    }

    public static void apply(@NonNull Context context) {
        AppCompatDelegate.setApplicationLocales(list(saved(context)));
    }

    public static void choose(@NonNull Context context, @NonNull String tag) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, tag).apply();
        AppCompatDelegate.setApplicationLocales(list(tag));
    }

    @NonNull
    public static String saved(@NonNull Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String tag = prefs.getString(KEY, "");
        return tag != null ? tag : "";
    }

    @NonNull
    private static LocaleListCompat list(@NonNull String tag) {
        if (tag.isEmpty()) {
            return LocaleListCompat.getEmptyLocaleList();
        }
        return LocaleListCompat.forLanguageTags(tag);
    }
}
