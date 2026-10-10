package com.apiculture.simulator.data.remote;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Iterator;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Foto de un lugar conocido cerca de unas coordenadas: la página de Wikipedia con foto más
 * visitada en 10 km. Se guarda en disco por hexágono; si no hay nada, se vuelve a probar en una semana.
 */
public final class LandmarkPhoto {
    public static final class Result {
        @NonNull
        public final Bitmap bitmap;
        @NonNull
        public final String title;

        Result(@NonNull Bitmap bitmap, @NonNull String title) {
            this.bitmap = bitmap;
            this.title = title;
        }
    }

    private static final String PREFS = "landmark_photo_v1";
    private static final long RETRY_MS = 7L * 24 * 3600 * 1000;
    private static final String AGENT = "ApicultureSimulator/1.0 (Android game)";
    /** Escudos, banderas y mapas de situación no sirven de foto del lugar. */
    private static final Pattern NOT_A_PHOTO = Pattern.compile(
            "(?i)(escudo|escut|bandera|flag|coat|blason|mapa|map|locator|situaci|logo|\\.svg|\\.png)");

    private LandmarkPhoto() {
    }

    @WorkerThread
    @Nullable
    public static Result get(@NonNull Context context, @NonNull String hexId, double lat, double lon,
            @NonNull String... langs) {
        Context app = context.getApplicationContext();
        SharedPreferences prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        File file = new File(new File(app.getFilesDir(), "landmarks"), safe(hexId) + ".jpg");
        String title = prefs.getString(hexId + ":title", null);
        if (title != null && file.exists()) {
            Bitmap bmp = BitmapFactory.decodeFile(file.getAbsolutePath());
            if (bmp != null) {
                return new Result(bmp, title);
            }
        }
        long missedAt = prefs.getLong(hexId + ":none", 0L);
        if (missedAt > 0 && System.currentTimeMillis() - missedAt < RETRY_MS) {
            return null;
        }
        for (String lang : langs) {
            try {
                String[] pick = pick(lang, lat, lon);
                if (pick == null) {
                    continue;
                }
                byte[] bytes = download(pick[1]);
                Bitmap bmp = bytes != null ? BitmapFactory.decodeByteArray(bytes, 0, bytes.length) : null;
                if (bmp == null) {
                    continue;
                }
                File dir = file.getParentFile();
                if (dir != null && (dir.isDirectory() || dir.mkdirs())) {
                    try (FileOutputStream out = new FileOutputStream(file)) {
                        out.write(bytes);
                    }
                }
                prefs.edit().putString(hexId + ":title", pick[0]).remove(hexId + ":none").apply();
                return new Result(bmp, pick[0]);
            } catch (Exception ignored) {
                // Sin red o respuesta rara: se prueba otra lengua o se queda sin foto.
            }
        }
        prefs.edit().putLong(hexId + ":none", System.currentTimeMillis()).apply();
        return null;
    }

    /** {título, url de la miniatura} de la página con foto más visitada cerca, o null. */
    @Nullable
    private static String[] pick(@NonNull String lang, double lat, double lon) throws Exception {
        String coord = String.format(Locale.ROOT, "%.5f|%.5f", lat, lon);
        String url = "https://" + lang + ".wikipedia.org/w/api.php?action=query&format=json"
                + "&generator=geosearch&ggsradius=10000&ggslimit=30&ggscoord=" + URLEncoder.encode(coord, "UTF-8")
                + "&prop=pageimages%7Cpageviews&piprop=thumbnail%7Cname&pithumbsize=900&pvipdays=30";
        byte[] body = download(url);
        if (body == null) {
            return null;
        }
        JSONObject query = new JSONObject(new String(body, "UTF-8")).optJSONObject("query");
        JSONObject pages = query != null ? query.optJSONObject("pages") : null;
        if (pages == null) {
            return null;
        }
        String[] best = null;
        long bestViews = -1;
        int bestIndex = Integer.MAX_VALUE;
        Iterator<String> keys = pages.keys();
        while (keys.hasNext()) {
            JSONObject page = pages.optJSONObject(keys.next());
            JSONObject thumb = page != null ? page.optJSONObject("thumbnail") : null;
            if (thumb == null) {
                continue;
            }
            String src = thumb.optString("source", "");
            String image = page.optString("pageimage", "");
            if (src.isEmpty() || NOT_A_PHOTO.matcher(image).find() || NOT_A_PHOTO.matcher(src).find()) {
                continue;
            }
            long views = 0;
            JSONObject pv = page.optJSONObject("pageviews");
            if (pv != null) {
                Iterator<String> days = pv.keys();
                while (days.hasNext()) {
                    views += pv.optLong(days.next(), 0L);
                }
            }
            // Las fotos apaisadas quedan mejor de fondo: las verticales cuentan la mitad.
            if (thumb.optInt("width", 0) < thumb.optInt("height", 0)) {
                views /= 2;
            }
            int index = page.optInt("index", 0);
            if (views > bestViews || views == bestViews && index < bestIndex) {
                bestViews = views;
                bestIndex = index;
                best = new String[]{page.optString("title", ""), src};
            }
        }
        return best;
    }

    @Nullable
    private static byte[] download(@NonNull String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(12000);
        c.setRequestProperty("User-Agent", AGENT);
        try {
            if (c.getResponseCode() / 100 != 2) {
                return null;
            }
            try (InputStream in = c.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buf = new byte[16384];
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                    if (out.size() > 4_000_000) {
                        return null;
                    }
                }
                return out.toByteArray();
            }
        } finally {
            c.disconnect();
        }
    }

    @NonNull
    private static String safe(@NonNull String s) {
        return s.replaceAll("[^A-Za-z0-9_-]", "_");
    }
}
