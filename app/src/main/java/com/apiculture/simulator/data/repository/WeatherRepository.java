package com.apiculture.simulator.data.repository;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import com.apiculture.simulator.domain.game.DailyWeather;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Clima vía <a href="https://open-meteo.com/">Open-Meteo</a> (sin API key).
 */
public class WeatherRepository {

    private static final String TAG = "WeatherRepository";
    private static final int CONNECT_TIMEOUT_MS = 8_000;
    private static final int READ_TIMEOUT_MS = 8_000;
    private static final int MAX_ATTEMPTS = 2;
    private static final long FAIL_COOLDOWN_MS = 5 * 60_000L;
    private static final int RESPONSE_CACHE_MAX = 24;

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Object HTTP_LOCK = new Object();
    private static final Map<String, Map<String, DailyWeather>> RESPONSE_CACHE =
            new LinkedHashMap<String, Map<String, DailyWeather>>(32, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Map<String, DailyWeather>> eldest) {
                    return size() > RESPONSE_CACHE_MAX;
                }
            };
    private static final Map<String, DailyWeather> LAST_GOOD_BY_COORD_DAY = new ConcurrentHashMap<>();
    private static final Map<String, Long> URL_FAIL_UNTIL = new HashMap<>();
    private static final Map<String, Long> HOST_FAIL_UNTIL = new HashMap<>();
    private static final Map<String, Waiter> IN_FLIGHT = new HashMap<>();

    private static final class Waiter {
        Map<String, DailyWeather> result;
        boolean done;
    }

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
                    postCallback(() -> callback.onSuccess("--°C", "Open-Meteo"));
                } else {
                    final String label = Math.round(t) + "°C";
                    postCallback(() -> callback.onSuccess(label, "Open-Meteo"));
                }
            } catch (Throwable e) {
                Log.e(TAG, "Error Open-Meteo (actual)", e);
                if (callback != null) {
                    postCallback(() -> callback.onError(e));
                }
            }
        }, "open-meteo-current").start();
    }

    private static void postCallback(Runnable r) {
        MAIN.post(r);
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
        HttpResult http = httpGet(urlStr);
        if (http.body == null || http.code < 200 || http.code >= 300) {
            return null;
        }
        JSONObject root = new JSONObject(http.body);
        JSONObject current = root.optJSONObject("current");
        if (current == null || current.isNull("temperature_2m")) {
            return null;
        }
        return current.optDouble("temperature_2m", Double.NaN);
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
        DailyWeather cached = lastGood(lat, lng, iso);
        DailyWeather v = fetchOpenMeteoDailyWeatherFromForecast(lat, lng, iso);
        if (v != null && v.meanTempC != null && !Double.isNaN(v.meanTempC)) {
            rememberGood(lat, lng, iso, v);
            return v;
        }
        if (hostCooling("api.open-meteo.com") || hostCooling("archive-api.open-meteo.com")) {
            return v != null ? v : cached;
        }
        DailyWeather arch = fetchOpenMeteoDailyWeatherFromArchive(lat, lng, iso);
        DailyWeather chosen = arch != null ? arch : v;
        if (chosen != null && chosen.meanTempC != null && !Double.isNaN(chosen.meanTempC)) {
            rememberGood(lat, lng, iso, chosen);
            return chosen;
        }
        return chosen != null ? chosen : cached;
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
        String urlStr = forecastRangeUrl(lat, lng);
        Map<String, DailyWeather> byIso = readOpenMeteoDailyWeatherMap(urlStr);
        boolean skipArchive = byIso.isEmpty() && (hostCooling("api.open-meteo.com")
                || hostCooling("archive-api.open-meteo.com"));
        LocalDate d = fromInclusive;
        while (!d.isAfter(toInclusive)) {
            String iso = d.format(DateTimeFormatter.ISO_LOCAL_DATE);
            DailyWeather w = byIso.get(iso);
            if (w == null && !skipArchive) {
                w = fetchOpenMeteoDailyWeatherFromArchive(lat, lng, iso);
            }
            if (w == null) {
                w = lastGood(lat, lng, iso);
            }
            if (w != null) {
                if (w.meanTempC != null && !Double.isNaN(w.meanTempC)) {
                    rememberGood(lat, lng, iso, w);
                }
                out.put(d.getYear() * 10_000 + d.getMonthValue() * 100 + d.getDayOfMonth(), w);
            }
            d = d.plusDays(1);
        }
        return out;
    }

    private static final String DAILY_VARS =
            "temperature_2m_mean,temperature_2m_max,temperature_2m_min,"
                    + "precipitation_sum,weather_code,wind_speed_10m_max,cloud_cover_mean";

    private static String forecastRangeUrl(double lat, double lng) {
        return "https://api.open-meteo.com/v1/forecast?latitude=" + lat + "&longitude=" + lng
                + "&daily=" + DAILY_VARS
                + "&hourly=weather_code"
                + "&past_days=16&forecast_days=1&timezone=auto";
    }

    private static DailyWeather fetchOpenMeteoDailyWeatherFromForecast(double lat, double lng, String isoDay) {
        return readOpenMeteoDailyWeatherMap(forecastRangeUrl(lat, lng)).get(isoDay);
    }

    private static DailyWeather fetchOpenMeteoDailyWeatherFromArchive(double lat, double lng, String isoDay) {
        String urlStr = "https://archive-api.open-meteo.com/v1/archive?latitude=" + lat + "&longitude=" + lng
                + "&start_date=" + isoDay + "&end_date=" + isoDay
                + "&daily=" + DAILY_VARS
                + "&hourly=weather_code&timezone=auto";
        return readOpenMeteoDailyWeatherMap(urlStr).get(isoDay);
    }

    private static String coordDayKey(double lat, double lng, String isoDay) {
        return String.format(Locale.US, "%.4f,%.4f_%s", lat, lng, isoDay);
    }

    private static DailyWeather lastGood(double lat, double lng, String isoDay) {
        return LAST_GOOD_BY_COORD_DAY.get(coordDayKey(lat, lng, isoDay));
    }

    private static void rememberGood(double lat, double lng, String isoDay, DailyWeather w) {
        LAST_GOOD_BY_COORD_DAY.put(coordDayKey(lat, lng, isoDay), w);
    }

    private static boolean hostCooling(String host) {
        Long until;
        synchronized (HTTP_LOCK) {
            until = HOST_FAIL_UNTIL.get(host);
        }
        return until != null && until > SystemClock.elapsedRealtime();
    }

    private static Map<String, DailyWeather> readOpenMeteoDailyWeatherMap(String urlStr) {
        synchronized (HTTP_LOCK) {
            Map<String, DailyWeather> cached = RESPONSE_CACHE.get(urlStr);
            if (cached != null) {
                return cached;
            }
            long now = SystemClock.elapsedRealtime();
            Long urlUntil = URL_FAIL_UNTIL.get(urlStr);
            if (urlUntil != null && urlUntil > now) {
                return new HashMap<>();
            }
            String host = hostOf(urlStr);
            Long hostUntil = HOST_FAIL_UNTIL.get(host);
            if (hostUntil != null && hostUntil > now) {
                return new HashMap<>();
            }
            Waiter existing = IN_FLIGHT.get(urlStr);
            if (existing != null) {
                while (!existing.done) {
                    try {
                        HTTP_LOCK.wait(CONNECT_TIMEOUT_MS + READ_TIMEOUT_MS + 1_000L);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return new HashMap<>();
                    }
                }
                return existing.result != null ? existing.result : new HashMap<>();
            }
            Waiter waiter = new Waiter();
            IN_FLIGHT.put(urlStr, waiter);
        }

        Map<String, DailyWeather> map = new HashMap<>();
        try {
            Log.i(TAG, "Open-Meteo envía GET " + urlStr);
            HttpResult http = httpGet(urlStr);
            Log.i(TAG, "Open-Meteo responde HTTP " + http.code
                    + (http.body == null ? " sin cuerpo" : " " + http.body.length() + " bytes"));
            if (http.body != null && http.code >= 200 && http.code < 300) {
                fillDailyWeatherMap(new JSONObject(http.body), map);
                for (Map.Entry<String, DailyWeather> entry : map.entrySet()) {
                    DailyWeather day = entry.getValue();
                    Log.i(TAG, "Open-Meteo día " + entry.getKey()
                            + " códigoDiario=" + day.weatherCode
                            + " horasCubiertas=" + day.coveredDaylightHours
                            + " lluvia=" + day.precipitationMm + " mm"
                            + " nubes=" + day.cloudCoverPct + "%"
                            + " viento=" + day.windMaxKmh + " km/h"
                            + " temp=" + day.meanTempC + " C");
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Open-Meteo error: " + urlStr, e);
        }

        synchronized (HTTP_LOCK) {
            if (!map.isEmpty()) {
                RESPONSE_CACHE.put(urlStr, map);
                URL_FAIL_UNTIL.remove(urlStr);
            }
            Waiter waiter = IN_FLIGHT.remove(urlStr);
            if (waiter != null) {
                waiter.result = map;
                waiter.done = true;
                HTTP_LOCK.notifyAll();
            }
            return map;
        }
    }

    private static String hostOf(String urlStr) {
        try {
            return new URL(urlStr).getHost();
        } catch (Exception e) {
            return urlStr;
        }
    }

    private static final class HttpResult {
        final int code;
        final String body;

        HttpResult(int code, String body) {
            this.code = code;
            this.body = body;
        }
    }

    private static HttpResult httpGet(String urlStr) throws Exception {
        String host = hostOf(urlStr);
        synchronized (HTTP_LOCK) {
            long now = SystemClock.elapsedRealtime();
            Long hostUntil = HOST_FAIL_UNTIL.get(host);
            if (hostUntil != null && hostUntil > now) {
                return new HttpResult(0, null);
            }
            Long urlUntil = URL_FAIL_UNTIL.get(urlStr);
            if (urlUntil != null && urlUntil > now) {
                return new HttpResult(0, null);
            }
        }
        Exception last = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            HttpURLConnection connection = null;
            try {
                URL url = new URL(urlStr);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
                connection.setReadTimeout(READ_TIMEOUT_MS);
                applyOpenMeteoHeaders(connection);

                int code = connection.getResponseCode();
                InputStream is = code >= 200 && code < 300
                        ? connection.getInputStream()
                        : connection.getErrorStream();
                String body = readStream(is);
                if (code >= 200 && code < 300) {
                    return new HttpResult(code, body);
                }
                last = new Exception("HTTP " + code);
            } catch (Exception e) {
                last = e;
                if (attempt < MAX_ATTEMPTS && isTransient(e)) {
                    try {
                        Thread.sleep(400L * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                    continue;
                }
                markFailure(urlStr, host, e);
                throw e;
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }
        markFailure(urlStr, host, last);
        if (last != null) {
            throw last;
        }
        return new HttpResult(0, null);
    }

    private static boolean isTransient(Exception e) {
        if (e == null) {
            return false;
        }
        if (e instanceof SocketTimeoutException) {
            return true;
        }
        String msg = e.getMessage();
        return msg != null && msg.toLowerCase(Locale.US).contains("timeout");
    }

    private static void markFailure(String urlStr, String host, Exception e) {
        if (!isTransient(e)) {
            return;
        }
        long until = SystemClock.elapsedRealtime() + FAIL_COOLDOWN_MS;
        synchronized (HTTP_LOCK) {
            URL_FAIL_UNTIL.put(urlStr, until);
            HOST_FAIL_UNTIL.put(host, until);
        }
        Log.w(TAG, "Open-Meteo en pausa 5 min (" + host + ")");
    }

    private static String readStream(InputStream is) throws Exception {
        if (is == null) {
            return "";
        }
        BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line);
        }
        reader.close();
        return sb.toString();
    }

    private static void applyOpenMeteoHeaders(HttpURLConnection connection) {
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("User-Agent", "ApiSim-Android/1.0");
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
        JSONArray clouds = daily.optJSONArray("cloud_cover_mean");
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
            if (clouds != null && i < clouds.length() && !clouds.isNull(i)) {
                w.cloudCoverPct = clouds.optDouble(i);
            }
            map.put(iso, w);
        }
        applyHourlyCoverage(root.optJSONObject("hourly"), map);
    }

    /**
     * Cuenta, entre las 8:00 y las 20:00, las horas que no están despejadas.
     * Códigos: 0 despejado, 1 casi despejado, 2 parcial, 3 cubierto,
     * 45/48 niebla, 51–67 llovizna o lluvia, 71–77 nieve, 80–82 chubascos, 95–99 tormenta.
     */
    private static void applyHourlyCoverage(JSONObject hourly, Map<String, DailyWeather> map) {
        if (hourly == null) {
            return;
        }
        JSONArray times = hourly.optJSONArray("time");
        JSONArray codes = hourly.optJSONArray("weather_code");
        if (codes == null) {
            codes = hourly.optJSONArray("weathercode");
        }
        if (times == null || codes == null) {
            return;
        }
        for (int i = 0; i < times.length() && i < codes.length(); i++) {
            String stamp = times.optString(i, "");
            if (stamp.length() < 13 || codes.isNull(i)) {
                continue;
            }
            String iso = stamp.substring(0, 10);
            int hour = Integer.parseInt(stamp.substring(11, 13));
            DailyWeather day = map.get(iso);
            if (day == null || hour < 8 || hour >= 20) {
                continue;
            }
            if (day.coveredDaylightHours < 0) {
                day.coveredDaylightHours = 0;
            }
            int code = codes.optInt(i);
            if (code >= 2) {
                day.coveredDaylightHours++;
            }
        }
    }
}
