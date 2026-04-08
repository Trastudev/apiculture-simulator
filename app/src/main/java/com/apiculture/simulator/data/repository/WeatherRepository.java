package com.apiculture.simulator.data.repository;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Clima vía <a href="https://open-meteo.com/">Open-Meteo</a> (sin API key).
 */
public class WeatherRepository {

    private static final String TAG = "WeatherRepository";

    public interface Callback {
        void onSuccess(String temperatureLabel, String stationLabel);

        void onError(Throwable t);
    }

    public void fetchCurrentTemperature(double lat, double lng, Callback callback) {
        new Thread(() -> {
            try {
                Double t = fetchOpenMeteoCurrentCelsiusBlocking(lat, lng);
                if (callback == null) {
                    return;
                }
                if (t == null || Double.isNaN(t)) {
                    callback.onSuccess("--°C", "Open-Meteo");
                } else {
                    callback.onSuccess(Math.round(t) + "°C", "Open-Meteo");
                }
            } catch (Throwable e) {
                Log.e(TAG, "Error Open-Meteo (actual)", e);
                if (callback != null) {
                    callback.onError(e);
                }
            }
        }).start();
    }

    /**
     * Temperatura actual (°C) en superficie. Solo Open-Meteo.
     */
    public Double fetchCurrentTemperatureCelsiusBlocking(double lat, double lng) {
        try {
            return fetchOpenMeteoCurrentCelsiusBlocking(lat, lng);
        } catch (Exception e) {
            Log.e(TAG, "Open-Meteo (bloqueante) error", e);
            return null;
        }
    }

    private static Double fetchOpenMeteoCurrentCelsiusBlocking(double lat, double lng) throws Exception {
        String urlStr = "https://api.open-meteo.com/v1/forecast?latitude=" + lat + "&longitude=" + lng
                + "&current=temperature_2m"
                + "&timezone=auto";

        HttpURLConnection connection = null;
        try {
            URL url = new URL(urlStr);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(12_000);
            connection.setReadTimeout(12_000);
            applyOpenMeteoHeaders(connection);

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
                return null;
            }
            JSONObject root = new JSONObject(sb.toString());
            JSONObject current = root.optJSONObject("current");
            if (current == null) {
                return null;
            }
            if (current.isNull("temperature_2m")) {
                return null;
            }
            return current.optDouble("temperature_2m", Double.NaN);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static void applyOpenMeteoHeaders(HttpURLConnection connection) {
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("User-Agent", "ApiSim-Android/1.0");
    }

    /**
     * Temperatura media diaria (°C) para un día de calendario: forecast con días pasados o archivo.
     */
    public Double fetchCalendarDayMeanTemperatureCelsiusBlocking(double lat, double lng, LocalDate calendarDay) {
        if (calendarDay == null) {
            return null;
        }
        String iso = calendarDay.format(DateTimeFormatter.ISO_LOCAL_DATE);
        Double v = fetchOpenMeteoDailyMeanFromForecast(lat, lng, iso);
        if (v != null && !Double.isNaN(v)) {
            return v;
        }
        return fetchOpenMeteoDailyMeanFromArchive(lat, lng, iso);
    }

    private static Double fetchOpenMeteoDailyMeanFromForecast(double lat, double lng, String isoDay) {
        String urlStr = "https://api.open-meteo.com/v1/forecast?latitude=" + lat + "&longitude=" + lng
                + "&daily=temperature_2m_mean,temperature_2m_max,temperature_2m_min"
                + "&past_days=16&forecast_days=1&timezone=auto";
        return readOpenMeteoDailyMean(urlStr, isoDay);
    }

    private static Double fetchOpenMeteoDailyMeanFromArchive(double lat, double lng, String isoDay) {
        String urlStr = "https://archive-api.open-meteo.com/v1/archive?latitude=" + lat + "&longitude=" + lng
                + "&start_date=" + isoDay + "&end_date=" + isoDay
                + "&daily=temperature_2m_mean,temperature_2m_max,temperature_2m_min";
        return readOpenMeteoDailyMean(urlStr, isoDay);
    }

    private static Double readOpenMeteoDailyMean(String urlStr, String isoDay) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(urlStr);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(12_000);
            connection.setReadTimeout(12_000);
            applyOpenMeteoHeaders(connection);

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
                return null;
            }
            return extractDailyMeanForDay(new JSONObject(sb.toString()), isoDay);
        } catch (Exception e) {
            Log.w(TAG, "Open-Meteo error: " + urlStr, e);
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static Double extractDailyMeanForDay(JSONObject root, String isoDay) {
        JSONObject daily = root.optJSONObject("daily");
        if (daily == null) {
            return null;
        }
        JSONArray times = daily.optJSONArray("time");
        if (times == null) {
            return null;
        }
        for (int i = 0; i < times.length(); i++) {
            if (!isoDay.equals(times.optString(i, ""))) {
                continue;
            }
            JSONArray mean = daily.optJSONArray("temperature_2m_mean");
            if (mean != null && i < mean.length() && !mean.isNull(i)) {
                return mean.optDouble(i, Double.NaN);
            }
            JSONArray max = daily.optJSONArray("temperature_2m_max");
            JSONArray min = daily.optJSONArray("temperature_2m_min");
            if (max != null && min != null && i < max.length() && i < min.length()
                    && !max.isNull(i) && !min.isNull(i)) {
                return (max.optDouble(i) + min.optDouble(i)) / 2.0;
            }
            return null;
        }
        return null;
    }
}
