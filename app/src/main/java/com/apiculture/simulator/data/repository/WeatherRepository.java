package com.apiculture.simulator.data.repository;

import android.util.Log;

import com.apiculture.simulator.domain.game.DailyWeather;

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
import java.util.HashMap;
import java.util.Map;

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
        DailyWeather w = fetchCalendarDayWeatherBlocking(lat, lng, calendarDay);
        if (w == null || w.meanTempC == null || Double.isNaN(w.meanTempC)) {
            return null;
        }
        return w.meanTempC;
    }

    /** Observación diaria (temp, precipitación, código WMO, viento). */
    public DailyWeather fetchCalendarDayWeatherBlocking(double lat, double lng, LocalDate calendarDay) {
        if (calendarDay == null) {
            return null;
        }
        String iso = calendarDay.format(DateTimeFormatter.ISO_LOCAL_DATE);
        DailyWeather v = fetchOpenMeteoDailyWeatherFromForecast(lat, lng, iso);
        if (v != null && v.meanTempC != null && !Double.isNaN(v.meanTempC)) {
            return v;
        }
        DailyWeather arch = fetchOpenMeteoDailyWeatherFromArchive(lat, lng, iso);
        return arch != null ? arch : v;
    }

    /**
     * Varios días en una sola petición (forecast con past_days). Clave = dayKey yyyyMMdd.
     */
    public Map<Integer, DailyWeather> fetchDailyWeatherRangeBlocking(
            double lat, double lng, LocalDate fromInclusive, LocalDate toInclusive) {
        Map<Integer, DailyWeather> out = new HashMap<>();
        if (fromInclusive == null || toInclusive == null || toInclusive.isBefore(fromInclusive)) {
            return out;
        }
        String urlStr = "https://api.open-meteo.com/v1/forecast?latitude=" + lat + "&longitude=" + lng
                + "&daily=" + DAILY_VARS
                + "&past_days=16&forecast_days=1&timezone=auto";
        Map<String, DailyWeather> byIso = readOpenMeteoDailyWeatherMap(urlStr);
        LocalDate d = fromInclusive;
        while (!d.isAfter(toInclusive)) {
            String iso = d.format(DateTimeFormatter.ISO_LOCAL_DATE);
            DailyWeather w = byIso.get(iso);
            if (w == null) {
                w = fetchOpenMeteoDailyWeatherFromArchive(lat, lng, iso);
            }
            if (w != null) {
                out.put(d.getYear() * 10_000 + d.getMonthValue() * 100 + d.getDayOfMonth(), w);
            }
            d = d.plusDays(1);
        }
        return out;
    }

    private static final String DAILY_VARS =
            "temperature_2m_mean,temperature_2m_max,temperature_2m_min,"
                    + "precipitation_sum,weather_code,wind_speed_10m_max";

    private static DailyWeather fetchOpenMeteoDailyWeatherFromForecast(double lat, double lng, String isoDay) {
        String urlStr = "https://api.open-meteo.com/v1/forecast?latitude=" + lat + "&longitude=" + lng
                + "&daily=" + DAILY_VARS
                + "&past_days=16&forecast_days=1&timezone=auto";
        return readOpenMeteoDailyWeatherMap(urlStr).get(isoDay);
    }

    private static DailyWeather fetchOpenMeteoDailyWeatherFromArchive(double lat, double lng, String isoDay) {
        String urlStr = "https://archive-api.open-meteo.com/v1/archive?latitude=" + lat + "&longitude=" + lng
                + "&start_date=" + isoDay + "&end_date=" + isoDay
                + "&daily=" + DAILY_VARS + "&timezone=auto";
        return readOpenMeteoDailyWeatherMap(urlStr).get(isoDay);
    }

    private static Map<String, DailyWeather> readOpenMeteoDailyWeatherMap(String urlStr) {
        Map<String, DailyWeather> map = new HashMap<>();
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
                return map;
            }
            fillDailyWeatherMap(new JSONObject(sb.toString()), map);
        } catch (Exception e) {
            Log.w(TAG, "Open-Meteo error: " + urlStr, e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
        return map;
    }

    private static void fillDailyWeatherMap(JSONObject root, Map<String, DailyWeather> map) {
        JSONObject daily = root.optJSONObject("daily");
        if (daily == null) {
            return;
        }
        JSONArray times = daily.optJSONArray("time");
        if (times == null) {
            return;
        }
        JSONArray mean = daily.optJSONArray("temperature_2m_mean");
        JSONArray max = daily.optJSONArray("temperature_2m_max");
        JSONArray min = daily.optJSONArray("temperature_2m_min");
        JSONArray rain = daily.optJSONArray("precipitation_sum");
        JSONArray wmo = daily.optJSONArray("weather_code");
        if (wmo == null) {
            wmo = daily.optJSONArray("weathercode");
        }
        JSONArray wind = daily.optJSONArray("wind_speed_10m_max");
        for (int i = 0; i < times.length(); i++) {
            String iso = times.optString(i, "");
            if (iso.isEmpty()) {
                continue;
            }
            DailyWeather w = new DailyWeather();
            if (mean != null && i < mean.length() && !mean.isNull(i)) {
                w.meanTempC = mean.optDouble(i, Double.NaN);
            } else if (max != null && min != null && i < max.length() && i < min.length()
                    && !max.isNull(i) && !min.isNull(i)) {
                w.meanTempC = (max.optDouble(i) + min.optDouble(i)) / 2.0;
            }
            if (rain != null && i < rain.length() && !rain.isNull(i)) {
                w.precipitationMm = rain.optDouble(i);
            }
            if (wmo != null && i < wmo.length() && !wmo.isNull(i)) {
                w.weatherCode = wmo.optInt(i);
            }
            if (wind != null && i < wind.length() && !wind.isNull(i)) {
                w.windMaxKmh = wind.optDouble(i);
            }
            map.put(iso, w);
        }
    }
}
