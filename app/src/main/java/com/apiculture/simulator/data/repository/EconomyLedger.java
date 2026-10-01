package com.apiculture.simulator.data.repository;

import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Historial local de entradas y salidas de beecoins. */
public final class EconomyLedger {

    private static final String KEY = "ledger_json";
    private static final int MAX = 300;

    private EconomyLedger() {
    }

    public static final class Entry {
        public final long epochMs;
        public final double amount;
        @NonNull
        public final String concept;

        Entry(long epochMs, double amount, @NonNull String concept) {
            this.epochMs = epochMs;
            this.amount = amount;
            this.concept = concept;
        }
    }

    static void add(@NonNull SharedPreferences prefs, double signed, @NonNull String concept) {
        if (Math.abs(signed) < 0.005 || concept.isEmpty()) {
            return;
        }
        List<Entry> rows = read(prefs);
        rows.add(0, new Entry(System.currentTimeMillis(), signed, concept));
        if (rows.size() > MAX) {
            rows.subList(MAX, rows.size()).clear();
        }
        write(prefs, rows);
    }

    static void clear(@NonNull SharedPreferences prefs) {
        prefs.edit().remove(KEY).commit();
    }

    @NonNull
    public static List<Entry> entries(@NonNull SharedPreferences prefs) {
        return Collections.unmodifiableList(read(prefs));
    }

    @NonNull
    private static List<Entry> read(@NonNull SharedPreferences prefs) {
        List<Entry> out = new ArrayList<>();
        String raw = prefs.getString(KEY, null);
        if (raw == null || raw.isEmpty()) {
            return out;
        }
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) {
                    continue;
                }
                String concept = o.optString("c", "").trim();
                if (concept.isEmpty()) {
                    continue;
                }
                out.add(new Entry(o.optLong("t"), o.optDouble("a"), concept));
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private static void write(@NonNull SharedPreferences prefs, @NonNull List<Entry> rows) {
        JSONArray arr = new JSONArray();
        for (Entry row : rows) {
            JSONObject o = new JSONObject();
            try {
                o.put("t", row.epochMs);
                o.put("a", row.amount);
                o.put("c", row.concept);
                arr.put(o);
            } catch (Exception ignored) {
            }
        }
        prefs.edit().putString(KEY, arr.toString()).commit();
    }
}
