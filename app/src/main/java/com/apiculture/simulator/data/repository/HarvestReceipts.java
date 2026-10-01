package com.apiculture.simulator.data.repository;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Recibos de recolección. Se guardan al salir el camión y se muestran cuando
 * la miel entra en el almacén, también si el jugador abre el juego más tarde.
 */
public final class HarvestReceipts {

    public interface Listener {
        void onPending();
    }

    public static final class Receipt {
        @NonNull
        public final String tripId;
        public final int hiveCount;
        public final double totalKg;
        @NonNull
        public final Map<String, Double> kgByFlora;

        Receipt(@NonNull String tripId, int hiveCount, double totalKg,
                @NonNull Map<String, Double> kgByFlora) {
            this.tripId = tripId;
            this.hiveCount = hiveCount;
            this.totalKg = totalKg;
            this.kgByFlora = kgByFlora;
        }
    }

    private static final String PREFS = "harvest_receipts";
    private static final String STAGED = "staged";
    private static final String PENDING = "pending";
    private static final String DONE = "done";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    @Nullable
    private static volatile Listener listener;

    private HarvestReceipts() {
    }

    public static void setListener(@Nullable Listener next) {
        listener = next;
    }

    /** Datos del viaje, antes de que el camión vuelva. */
    public static void stage(@NonNull Context context, @NonNull String tripId,
            @NonNull Map<String, Double> cargo, int hiveCount) {
        if (tripId.isEmpty() || cargo.isEmpty()) {
            return;
        }
        synchronized (HarvestReceipts.class) {
            SharedPreferences prefs = prefs(context);
            JSONObject staged = object(prefs.getString(STAGED, "{}"));
            JSONObject item = new JSONObject();
            try {
                item.put("hiveCount", Math.max(1, hiveCount));
                item.put("lines", lines(cargo));
                staged.put(tripId, item);
            } catch (JSONException ignored) {
                return;
            }
            prefs.edit().putString(STAGED, staged.toString()).apply();
        }
    }

    /** El viaje ya dejó la miel. Pasa el recibo a la cola visible. */
    public static void publish(@NonNull Context context, @Nullable String tripId,
            @NonNull Map<String, Double> fallback, int fallbackHives) {
        if (tripId == null || tripId.isEmpty()) {
            return;
        }
        boolean added;
        synchronized (HarvestReceipts.class) {
            SharedPreferences prefs = prefs(context);
            JSONArray done = array(prefs.getString(DONE, "[]"));
            if (contains(done, tripId)) {
                return;
            }
            JSONObject staged = object(prefs.getString(STAGED, "{}"));
            JSONObject item = staged.optJSONObject(tripId);
            staged.remove(tripId);
            if (item == null) {
                if (fallback.isEmpty()) {
                    return;
                }
                item = new JSONObject();
                try {
                    item.put("hiveCount", Math.max(1, fallbackHives));
                    item.put("lines", lines(fallback));
                } catch (JSONException ignored) {
                    return;
                }
            }
            try {
                item.put("tripId", tripId);
            } catch (JSONException ignored) {
                return;
            }
            JSONArray pending = array(prefs.getString(PENDING, "[]"));
            pending.put(item);
            done.put(tripId);
            while (done.length() > 200) {
                done.remove(0);
            }
            prefs.edit()
                    .putString(STAGED, staged.toString())
                    .putString(PENDING, pending.toString())
                    .putString(DONE, done.toString())
                    .apply();
            added = true;
        }
        if (added) {
            MAIN.post(() -> {
                Listener current = listener;
                if (current != null) {
                    current.onPending();
                }
            });
        }
    }

    @Nullable
    public static Receipt peek(@NonNull Context context) {
        synchronized (HarvestReceipts.class) {
            JSONArray pending = array(prefs(context).getString(PENDING, "[]"));
            if (pending.length() == 0) {
                return null;
            }
            return receipt(pending.optJSONObject(0));
        }
    }

    public static void drop(@NonNull Context context, @NonNull String tripId) {
        synchronized (HarvestReceipts.class) {
            SharedPreferences prefs = prefs(context);
            JSONArray pending = array(prefs.getString(PENDING, "[]"));
            JSONArray next = new JSONArray();
            for (int i = 0; i < pending.length(); i++) {
                JSONObject item = pending.optJSONObject(i);
                if (item == null || tripId.equals(item.optString("tripId", ""))) {
                    continue;
                }
                next.put(item);
            }
            prefs.edit().putString(PENDING, next.toString()).apply();
        }
    }

    @NonNull
    private static SharedPreferences prefs(@NonNull Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    @NonNull
    private static JSONObject lines(@NonNull Map<String, Double> cargo) throws JSONException {
        JSONObject lines = new JSONObject();
        for (Map.Entry<String, Double> entry : cargo.entrySet()) {
            if (entry.getKey() == null || entry.getKey().startsWith("_") || entry.getValue() == null
                    || entry.getValue() <= 1e-9) {
                continue;
            }
            lines.put(entry.getKey(), entry.getValue());
        }
        return lines;
    }

    @Nullable
    private static Receipt receipt(@Nullable JSONObject item) {
        if (item == null) {
            return null;
        }
        String tripId = item.optString("tripId", "");
        JSONObject lines = item.optJSONObject("lines");
        Map<String, Double> flora = new LinkedHashMap<>();
        double total = 0.0;
        if (lines != null) {
            Iterator<String> keys = lines.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                double kg = lines.optDouble(key, 0.0);
                if (kg > 1e-9) {
                    flora.put(key, kg);
                    total += kg;
                }
            }
        }
        if (tripId.isEmpty() || flora.isEmpty()) {
            return null;
        }
        return new Receipt(tripId, Math.max(1, item.optInt("hiveCount", 1)), total, flora);
    }

    @NonNull
    private static JSONObject object(@Nullable String raw) {
        try {
            return new JSONObject(raw == null ? "{}" : raw);
        } catch (JSONException e) {
            return new JSONObject();
        }
    }

    @NonNull
    private static JSONArray array(@Nullable String raw) {
        try {
            return new JSONArray(raw == null ? "[]" : raw);
        } catch (JSONException e) {
            return new JSONArray();
        }
    }

    private static boolean contains(@NonNull JSONArray rows, @NonNull String id) {
        for (int i = 0; i < rows.length(); i++) {
            if (id.equals(rows.optString(i, ""))) {
                return true;
            }
        }
        return false;
    }
}
