package com.apiculture.simulator.data.repository;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONObject;

/**
 * Emblema de la empresa de miel: dibujo y color elegidos en el perfil. Se pinta en las
 * camionetas del obrador 3D. Copia local en preferencias y en el servidor (store "brand").
 */
public final class BrandStore {
    public static final int EMBLEMS = 8;
    public static final String[] COLORS = {
            "#E8A317", "#C0392B", "#3E8E41", "#2E6DB4",
            "#7D3C98", "#7B4A2A", "#2B2B2B", "#1A9E9A",
    };

    private static final String PREFS = "brand_v1";

    public static final class Brand {
        public final int emblem;
        @NonNull
        public final String color;

        public Brand(int emblem, @Nullable String color) {
            this.emblem = Math.max(0, Math.min(EMBLEMS - 1, emblem));
            this.color = valid(color) ? color : COLORS[0];
        }
    }

    private BrandStore() {
    }

    @NonNull
    public static Brand get(@NonNull Context context, @Nullable String ownerId) {
        if (ownerId == null || ownerId.isEmpty()) {
            return new Brand(0, COLORS[0]);
        }
        SharedPreferences p = prefs(context);
        return new Brand(p.getInt(ownerId + ":emblem", 0), p.getString(ownerId + ":color", COLORS[0]));
    }

    /** Guarda en local y sube al servidor en segundo plano. */
    public static void save(@NonNull Context context, @Nullable String ownerId, @NonNull Brand brand) {
        if (ownerId == null || ownerId.isEmpty()) {
            return;
        }
        prefs(context).edit()
                .putInt(ownerId + ":emblem", brand.emblem)
                .putString(ownerId + ":color", brand.color)
                .apply();
        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("emblem", brand.emblem);
                body.put("color", brand.color);
                GameServer.saveStore(ownerId, "brand", body);
            } catch (Exception ignored) {
            }
        }, "brand-push").start();
    }

    public static void applyServer(@NonNull Context context, @NonNull String ownerId, @Nullable JSONObject body) {
        if (body == null || !body.has("emblem")) {
            return;
        }
        Brand brand = new Brand(body.optInt("emblem", 0), body.optString("color", COLORS[0]));
        prefs(context).edit()
                .putInt(ownerId + ":emblem", brand.emblem)
                .putString(ownerId + ":color", brand.color)
                .apply();
    }

    private static boolean valid(@Nullable String color) {
        return color != null && color.matches("#[0-9A-Fa-f]{6}");
    }

    @NonNull
    private static SharedPreferences prefs(@NonNull Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
