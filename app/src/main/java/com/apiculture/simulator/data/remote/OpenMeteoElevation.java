package com.apiculture.simulator.data.remote;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Elevación (m s.n.m.) en un punto vía
 * <a href="https://open-meteo.com/en/docs/elevation-api">Open-Meteo</a>.
 * Solo llamar desde hilo de fondo.
 */
public final class OpenMeteoElevation {

    private static final String TAG = "OpenMeteoElevation";
    private static final String ELEVATION_URL = "https://api.open-meteo.com/v1/elevation";

    /** Valor devuelto si falla la petición o la respuesta. */
    public static final int MISSING = -1;
    /** Cota por defecto si no hay dato (banda baja–media). */
    public static final int FALLBACK_METERS = 500;

    private OpenMeteoElevation() {
    }

    /**
     * @return metros sobre la marcha, o {@link #MISSING} si error
     */
    public static int fetchMetersBlocking(double lat, double lng) {
        String urlStr = ELEVATION_URL
                + "?latitude=" + String.format(Locale.US, "%.6f", lat)
                + "&longitude=" + String.format(Locale.US, "%.6f", lng);
        HttpURLConnection connection = null;
        try {
            URL url = new URL(urlStr);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(15_000);
            connection.setReadTimeout(20_000);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", "ApicultureSimulator/1.0");

            int code = connection.getResponseCode();
            InputStream is = code >= 200 && code < 300
                    ? connection.getInputStream()
                    : connection.getErrorStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();
            if (code < 200 || code >= 300) {
                return MISSING;
            }
            JSONObject root = new JSONObject(sb.toString());
            JSONArray arr = root.optJSONArray("elevation");
            if (arr == null || arr.length() == 0) {
                return MISSING;
            }
            int m = (int) Math.round(arr.optDouble(0, 0.0));
            return Math.max(0, m);
        } catch (Exception e) {
            Log.w(TAG, "Elevación", e);
            return MISSING;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /**
     * Resuelve cota para persistir: API o {@link #FALLBACK_METERS}.
     */
    public static int resolveMetersPersistedBlocking(double lat, double lng) {
        int m = fetchMetersBlocking(lat, lng);
        return m >= 0 ? m : FALLBACK_METERS;
    }
}
