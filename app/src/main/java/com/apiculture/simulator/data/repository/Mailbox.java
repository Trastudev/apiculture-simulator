package com.apiculture.simulator.data.repository;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;

import org.json.JSONObject;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/** Buzón y amistades en el servidor. Las llamadas no salen del hilo principal. */
public final class Mailbox {

    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private Mailbox() {
    }

    public static void unread(Consumer<Integer> onMain) {
        IO.execute(() -> {
            int n = 0;
            String raw = GameServer.getJson("/mail/unread");
            if (raw != null) {
                try {
                    n = new JSONObject(raw).optInt("unread", 0);
                } catch (Exception ignored) {
                    n = 0;
                }
            }
            int count = n;
            MAIN.post(() -> onMain.accept(count));
        });
    }

    public static void get(String path, Consumer<String> onMain) {
        IO.execute(() -> {
            String raw = GameServer.getJson(path);
            MAIN.post(() -> onMain.accept(raw));
        });
    }

    public static void post(String path, @Nullable JSONObject body, Consumer<String> onMain) {
        IO.execute(() -> {
            String raw = null;
            try {
                raw = GameServer.postJson(path, body == null ? "{}" : body.toString());
            } catch (Exception ignored) {
                raw = null;
            }
            String result = raw;
            MAIN.post(() -> onMain.accept(result));
        });
    }
}
