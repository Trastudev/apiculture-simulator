package com.apiculture.simulator.data.repository;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import com.apiculture.simulator.ApicultureApp;
import com.apiculture.simulator.data.local.AppDatabase;
import com.apiculture.simulator.data.session.PlayerAuth;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/** Borra la cuenta en el servidor y la copia local de la partida. */
public final class AccountErasure {

    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final String KEEP_PREFS = "game_locale.xml";

    private AccountErasure() {
    }

    public static void run(@NonNull ApicultureApp app, @NonNull Consumer<Boolean> onMain) {
        Handler main = new Handler(Looper.getMainLooper());
        IO.execute(() -> {
            boolean ok = !GameServer.enabled() || GameServer.deleteAccount();
            if (ok) {
                try {
                    wipeDevice(app);
                } catch (RuntimeException ignored) {
                    ok = false;
                }
            }
            boolean deleted = ok;
            main.post(() -> {
                if (deleted) {
                    PlayerAuth.getInstance().disconnect();
                }
                onMain.accept(deleted);
            });
        });
    }

    private static void wipeDevice(@NonNull Context context) {
        ApicultureApp app = (ApicultureApp) context.getApplicationContext();
        app.getHiveRepository().stopRealtimeCloudSync();
        app.getHexParcelRepository().stopRealtimeCloudSync();
        AppDatabase.getInstance(app).clearAllTables();
        File prefs = new File(app.getApplicationInfo().dataDir, "shared_prefs");
        File[] files = prefs.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            String name = file.getName();
            if (!name.endsWith(".xml") || KEEP_PREFS.equals(name)) {
                continue;
            }
            app.getSharedPreferences(name.substring(0, name.length() - 4), Context.MODE_PRIVATE)
                    .edit()
                    .clear()
                    .commit();
        }
    }
}
