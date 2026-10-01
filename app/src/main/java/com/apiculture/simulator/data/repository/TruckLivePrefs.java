package com.apiculture.simulator.data.repository;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

/** Extra opcional: camión en vivo y paquete de carreteras (~160 MB). */
public final class TruckLivePrefs {

    public static final String CHOICE_UNSET = "unset";
    public static final String CHOICE_YES = "yes";
    public static final String CHOICE_NO = "no";

    private static final String PREFS = "truck_live_prefs";
    private static final String KEY_CHOICE = "choice";
    private static final String KEY_DOWNLOAD_ID = "download_id";
    private static final String KEY_GRAPH_READY = "graph_ready";

    private TruckLivePrefs() {
    }

    public static boolean needsAsk(@NonNull Context context) {
        return CHOICE_UNSET.equals(choice(context));
    }

    public static boolean isEnabled(@NonNull Context context) {
        return CHOICE_YES.equals(choice(context));
    }

    @NonNull
    public static String choice(@NonNull Context context) {
        return prefs(context).getString(KEY_CHOICE, CHOICE_UNSET);
    }

    public static void setChoice(@NonNull Context context, @NonNull String choice) {
        prefs(context).edit().putString(KEY_CHOICE, choice).apply();
    }

    public static long downloadId(@NonNull Context context) {
        return prefs(context).getLong(KEY_DOWNLOAD_ID, -1L);
    }

    public static void setDownloadId(@NonNull Context context, long id) {
        prefs(context).edit().putLong(KEY_DOWNLOAD_ID, id).apply();
    }

    public static boolean isGraphReady(@NonNull Context context) {
        return prefs(context).getBoolean(KEY_GRAPH_READY, false);
    }

    public static void setGraphReady(@NonNull Context context, boolean ready) {
        prefs(context).edit().putBoolean(KEY_GRAPH_READY, ready).apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
