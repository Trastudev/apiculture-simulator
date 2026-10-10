package com.apiculture.simulator.data.repository;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.apiculture.simulator.domain.game.HoneyOrder;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Recibos de entrega de comandas. Se guardan al despachar la comanda y se
 * muestran cuando la entrega se completa.
 */
public final class OrderReceipts {

    public interface Listener {
        void onPending();
    }

    public static final class Receipt {
        @NonNull
        public final String id;
        @NonNull
        public final String npcName;
        public final int portraitIndex;
        @NonNull
        public final String floraKey;
        public final double kg;
        public final double payout;

        Receipt(@NonNull String id, @NonNull String npcName, int portraitIndex,
                @NonNull String floraKey, double kg, double payout) {
            this.id = id;
            this.npcName = npcName;
            this.portraitIndex = portraitIndex;
            this.floraKey = floraKey;
            this.kg = kg;
            this.payout = payout;
        }
    }

    private static final String PREFS = "order_receipts";
    private static final String STAGED = "staged";
    private static final String PENDING = "pending";
    private static final String DONE = "done";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    @Nullable
    private static volatile Listener listener;

    private OrderReceipts() {
    }

    public static void setListener(@Nullable Listener next) {
        listener = next;
    }

    /** Datos de la comanda en camino. */
    public static void stage(@NonNull Context context, @NonNull String tripId,
            @NonNull HoneyOrder order) {
        if (tripId.isEmpty()) {
            return;
        }
        synchronized (OrderReceipts.class) {
            SharedPreferences prefs = prefs(context);
            JSONObject staged = object(prefs.getString(STAGED, "{}"));
            JSONObject item = new JSONObject();
            try {
                item.put("orderId", order.id != null ? order.id : "");
                item.put("npcName", order.npcName != null ? order.npcName : "");
                item.put("portraitIndex", order.portraitIndex);
                item.put("floraKey", order.floraKey != null ? order.floraKey : "");
                item.put("kg", order.kg);
                item.put("payout", order.payout());
                staged.put(tripId, item);
            } catch (JSONException ignored) {
                return;
            }
            prefs.edit().putString(STAGED, staged.toString()).apply();
        }
    }

    /** Publica una comanda entregada al instante (sin camión). */
    public static void publishInstant(@NonNull Context context, @NonNull HoneyOrder order) {
        String id = "instant_" + order.id + "_" + System.currentTimeMillis();
        boolean added = false;
        synchronized (OrderReceipts.class) {
            SharedPreferences prefs = prefs(context);
            JSONArray pending = array(prefs.getString(PENDING, "[]"));
            JSONObject item = new JSONObject();
            try {
                item.put("id", id);
                item.put("orderId", order.id != null ? order.id : "");
                item.put("npcName", order.npcName != null ? order.npcName : "");
                item.put("portraitIndex", order.portraitIndex);
                item.put("floraKey", order.floraKey != null ? order.floraKey : "");
                item.put("kg", order.kg);
                item.put("payout", order.payout());
                pending.put(item);
                prefs.edit().putString(PENDING, pending.toString()).apply();
                added = true;
            } catch (JSONException ignored) {
            }
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

    /** La comanda ya se entregó. Pasa el recibo a la cola visible. */
    public static void publish(@NonNull Context context, @Nullable String tripId,
            @Nullable String orderId, @Nullable String npcName, int portraitIndex,
            @Nullable String floraKey, double kg, double payout) {
        if (tripId == null || tripId.isEmpty()) {
            return;
        }
        boolean added = false;
        synchronized (OrderReceipts.class) {
            SharedPreferences prefs = prefs(context);
            JSONArray done = array(prefs.getString(DONE, "[]"));
            if (contains(done, tripId)) {
                return;
            }
            JSONObject staged = object(prefs.getString(STAGED, "{}"));
            JSONObject item = staged.optJSONObject(tripId);
            staged.remove(tripId);
            if (item == null) {
                if (kg <= 1e-9) {
                    return;
                }
                item = new JSONObject();
                try {
                    item.put("orderId", orderId != null ? orderId : "");
                    item.put("npcName", npcName != null ? npcName : "");
                    item.put("portraitIndex", portraitIndex);
                    item.put("floraKey", floraKey != null ? floraKey : "");
                    item.put("kg", kg);
                    item.put("payout", payout);
                } catch (JSONException ignored) {
                    return;
                }
            } else {
                try {
                    if (item.optString("floraKey", "").isEmpty() && floraKey != null && !floraKey.isEmpty()) {
                        item.put("floraKey", floraKey);
                    }
                    if (item.optString("npcName", "").isEmpty() && npcName != null && !npcName.isEmpty()) {
                        item.put("npcName", npcName);
                    }
                    if (item.optInt("portraitIndex", 0) <= 0 && portraitIndex > 0) {
                        item.put("portraitIndex", portraitIndex);
                    }
                } catch (JSONException ignored) {
                }
            }
            try {
                item.put("id", tripId);
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
        synchronized (OrderReceipts.class) {
            JSONArray pending = array(prefs(context).getString(PENDING, "[]"));
            if (pending.length() == 0) {
                return null;
            }
            return receipt(pending.optJSONObject(0));
        }
    }

    public static void drop(@NonNull Context context, @NonNull String id) {
        synchronized (OrderReceipts.class) {
            SharedPreferences prefs = prefs(context);
            JSONArray pending = array(prefs.getString(PENDING, "[]"));
            JSONArray next = new JSONArray();
            for (int i = 0; i < pending.length(); i++) {
                JSONObject item = pending.optJSONObject(i);
                if (item == null || id.equals(item.optString("id", ""))) {
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

    @Nullable
    private static Receipt receipt(@Nullable JSONObject item) {
        if (item == null) {
            return null;
        }
        String id = item.optString("id", "");
        String npcName = item.optString("npcName", "");
        int portraitIndex = item.optInt("portraitIndex", 0);
        String floraKey = item.optString("floraKey", "");
        double kg = item.optDouble("kg", 0.0);
        double payout = item.optDouble("payout", 0.0);
        if (id.isEmpty()) {
            return null;
        }
        return new Receipt(id, npcName, portraitIndex, floraKey, kg, payout);
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
