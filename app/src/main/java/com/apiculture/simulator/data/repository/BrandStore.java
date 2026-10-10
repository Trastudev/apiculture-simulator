package com.apiculture.simulator.data.repository;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.Base64;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;

/**
 * Emblema de la empresa de miel: dibujo y color elegidos en el perfil, o una imagen propia del
 * jugador. Se pinta en la fachada y en los camiones del obrador 3D. Copia local en preferencias y
 * en el servidor (store "brand").
 */
public final class BrandStore {
    public static final int EMBLEMS = 8;
    public static final String[] COLORS = {
            "#E8A317", "#C0392B", "#3E8E41", "#2E6DB4",
            "#7D3C98", "#7B4A2A", "#2B2B2B", "#1A9E9A",
    };
    /** Lado del logo propio, en píxeles. */
    public static final int LOGO_SIZE = 256;

    private static final String PREFS = "brand_v1";

    public static final class Brand {
        public final int emblem;
        @NonNull
        public final String color;
        /** Logo propio en PNG base64; si hay, sustituye al dibujo. */
        @Nullable
        public final String logo;

        public Brand(int emblem, @Nullable String color) {
            this(emblem, color, null);
        }

        public Brand(int emblem, @Nullable String color, @Nullable String logo) {
            this.emblem = Math.max(0, Math.min(EMBLEMS - 1, emblem));
            this.color = valid(color) ? color : COLORS[0];
            this.logo = logo != null && !logo.isEmpty() ? logo : null;
        }

        @NonNull
        public Brand withLogo(@Nullable String next) {
            return new Brand(emblem, color, next);
        }

        @Nullable
        public Bitmap logoBitmap() {
            if (logo == null) {
                return null;
            }
            try {
                byte[] bytes = Base64.decode(logo, Base64.DEFAULT);
                return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            } catch (IllegalArgumentException e) {
                return null;
            }
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
        return new Brand(p.getInt(ownerId + ":emblem", 0), p.getString(ownerId + ":color", COLORS[0]),
                p.getString(ownerId + ":logo", null));
    }

    /** Guarda en local y sube al servidor en segundo plano. */
    public static void save(@NonNull Context context, @Nullable String ownerId, @NonNull Brand brand) {
        if (ownerId == null || ownerId.isEmpty()) {
            return;
        }
        store(context, ownerId, brand);
        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("emblem", brand.emblem);
                body.put("color", brand.color);
                body.put("logo", brand.logo != null ? brand.logo : "");
                GameServer.saveStore(ownerId, "brand", body);
            } catch (Exception ignored) {
            }
        }, "brand-push").start();
    }

    public static void applyServer(@NonNull Context context, @NonNull String ownerId, @Nullable JSONObject body) {
        if (body == null || !body.has("emblem")) {
            return;
        }
        store(context, ownerId, new Brand(body.optInt("emblem", 0), body.optString("color", COLORS[0]),
                body.optString("logo", "")));
    }

    /**
     * Imagen elegida por el jugador encajada entera en un cuadrado transparente (un logo apaisado no
     * se recorta), en PNG base64.
     */
    @Nullable
    public static String encodeLogo(@Nullable Bitmap src) {
        if (src == null || src.getWidth() <= 0 || src.getHeight() <= 0) {
            return null;
        }
        Bitmap out = Bitmap.createBitmap(LOGO_SIZE, LOGO_SIZE, Bitmap.Config.ARGB_8888);
        float scale = Math.min(LOGO_SIZE / (float) src.getWidth(), LOGO_SIZE / (float) src.getHeight());
        int w = Math.round(src.getWidth() * scale), h = Math.round(src.getHeight() * scale);
        int x = (LOGO_SIZE - w) / 2, y = (LOGO_SIZE - h) / 2;
        new Canvas(out).drawBitmap(src, null, new Rect(x, y, x + w, y + h), new Paint(Paint.FILTER_BITMAP_FLAG));
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        if (!out.compress(Bitmap.CompressFormat.PNG, 100, bytes)) {
            return null;
        }
        return Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP);
    }

    /** El logo propio en un archivo que Unity puede leer; null si el jugador no tiene. */
    @Nullable
    public static String logoFile(@NonNull Context context, @Nullable String ownerId) {
        Brand brand = get(context, ownerId);
        if (brand.logo == null || ownerId == null) {
            return null;
        }
        File dir = new File(context.getApplicationContext().getFilesDir(), "brand");
        File file = new File(dir, ownerId.replaceAll("[^A-Za-z0-9_-]", "_") + "_" + Integer.toHexString(brand.logo.hashCode()) + ".png");
        if (file.exists()) {
            return file.getAbsolutePath();
        }
        try {
            if (!dir.isDirectory() && !dir.mkdirs()) {
                return null;
            }
            File[] old = dir.listFiles();
            if (old != null) {
                for (File f : old) {
                    // Solo los de este jugador: el nombre empieza por su id.
                    if (f.getName().startsWith(ownerId.replaceAll("[^A-Za-z0-9_-]", "_") + "_")) {
                        //noinspection ResultOfMethodCallIgnored
                        f.delete();
                    }
                }
            }
            try (FileOutputStream out = new FileOutputStream(file)) {
                out.write(Base64.decode(brand.logo, Base64.DEFAULT));
            }
            return file.getAbsolutePath();
        } catch (Exception e) {
            return null;
        }
    }

    private static void store(@NonNull Context context, @NonNull String ownerId, @NonNull Brand brand) {
        SharedPreferences.Editor e = prefs(context).edit()
                .putInt(ownerId + ":emblem", brand.emblem)
                .putString(ownerId + ":color", brand.color);
        if (brand.logo != null) {
            e.putString(ownerId + ":logo", brand.logo);
        } else {
            e.remove(ownerId + ":logo");
        }
        e.apply();
    }

    private static boolean valid(@Nullable String color) {
        return color != null && color.matches("#[0-9A-Fa-f]{6}");
    }

    @NonNull
    private static SharedPreferences prefs(@NonNull Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
