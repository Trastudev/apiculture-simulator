package com.apiculture.simulator.data.repository;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;

import com.apiculture.simulator.domain.game.DemandSurgeMilestones;
import com.apiculture.simulator.domain.game.QueenInventoryCodec;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * Inventario de tienda y recompensas: tratamientos, apialimento y reinas (cada una con calidad).
 */
public final class EventInventoryStore {

    private static final String PREFS = "event_inventory_v1";
    private static final String KEY_TREAT = "inv_treatments";
    private static final String KEY_FEED = "inv_feed";
    private static final String KEY_QUEENS_LEGACY = "inv_queens100";
    private static final String KEY_QUEENS_JSON = "inv_queens_json";

    private EventInventoryStore() {
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static int treatments(Context ctx) {
        return prefs(ctx).getInt(KEY_TREAT, 0);
    }

    public static int feed(Context ctx) {
        return prefs(ctx).getInt(KEY_FEED, 0);
    }

    public static int queens100(Context ctx) {
        return queens(ctx).size();
    }

    public static List<Integer> queens(Context ctx) {
        SharedPreferences p = prefs(ctx);
        String json = p.getString(KEY_QUEENS_JSON, null);
        if (json != null && !json.trim().isEmpty()) {
            return QueenInventoryCodec.parse(json);
        }
        int legacy = p.getInt(KEY_QUEENS_LEGACY, 0);
        if (legacy > 0) {
            List<Integer> migrated = QueenInventoryCodec.fromLegacyCount(
                    legacy, DemandSurgeMilestones.QUEEN_QUALITY);
            saveQueens(ctx, migrated);
            p.edit().remove(KEY_QUEENS_LEGACY).apply();
            return migrated;
        }
        return new ArrayList<>();
    }

    public static void add(Context ctx, int treatments, int feed, int queens100) {
        addTreatments(ctx, treatments);
        addFeed(ctx, feed);
        for (int i = 0; i < Math.max(0, queens100); i++) {
            addQueen(ctx, DemandSurgeMilestones.QUEEN_QUALITY);
        }
    }

    public static void addTreatments(Context ctx, int n) {
        if (n <= 0) {
            return;
        }
        prefs(ctx).edit().putInt(KEY_TREAT, treatments(ctx) + n).apply();
    }

    public static void addFeed(Context ctx, int n) {
        if (n <= 0) {
            return;
        }
        prefs(ctx).edit().putInt(KEY_FEED, feed(ctx) + n).apply();
    }

    public static void addQueen(Context ctx, int quality) {
        List<Integer> list = queens(ctx);
        list.add(QueenInventoryCodec.clamp(quality));
        saveQueens(ctx, list);
    }

    public static boolean tryConsumeTreatment(Context ctx) {
        int n = treatments(ctx);
        if (n <= 0) {
            return false;
        }
        prefs(ctx).edit().putInt(KEY_TREAT, n - 1).apply();
        return true;
    }

    public static boolean tryConsumeFeed(Context ctx, int units) {
        int need = Math.max(1, units);
        int n = feed(ctx);
        if (n < need) {
            return false;
        }
        prefs(ctx).edit().putInt(KEY_FEED, n - need).apply();
        return true;
    }

    @Nullable
    public static Integer tryConsumeQueenAt(Context ctx, int index) {
        List<Integer> list = queens(ctx);
        if (index < 0 || index >= list.size()) {
            return null;
        }
        int q = list.remove(index);
        saveQueens(ctx, list);
        return q;
    }

    public static void restoreTreatment(Context ctx) {
        addTreatments(ctx, 1);
    }

    public static void restoreFeed(Context ctx, int units) {
        addFeed(ctx, Math.max(1, units));
    }

    public static void restoreQueen(Context ctx, int quality) {
        addQueen(ctx, quality);
    }

    public static void clearAll(Context ctx) {
        prefs(ctx).edit()
                .putInt(KEY_TREAT, 0)
                .putInt(KEY_FEED, 0)
                .putString(KEY_QUEENS_JSON, "[]")
                .remove(KEY_QUEENS_LEGACY)
                .apply();
    }

    public static void persistCloud(@Nullable FirebaseFirestore firestore, @Nullable String uid, Context ctx) {
        if (firestore == null || uid == null || uid.isEmpty() || ctx == null) {
            return;
        }
        Map<String, Object> m = new HashMap<>();
        m.put("invTreatments", treatments(ctx));
        m.put("invFeed", feed(ctx));
        List<Integer> qs = queens(ctx);
        m.put("invQueensJson", QueenInventoryCodec.toJson(qs));
        m.put("invQueens100", qs.size());
        firestore.collection("users").document(uid).set(m, SetOptions.merge());
    }

    public static void applyFromCloudIfHigher(@Nullable DocumentSnapshot snap, Context ctx) {
        if (snap == null || !snap.exists()) {
            return;
        }
        int t = readInt(snap, "invTreatments");
        int f = readInt(snap, "invFeed");
        SharedPreferences p = prefs(ctx);
        p.edit()
                .putInt(KEY_TREAT, Math.max(treatments(ctx), t))
                .putInt(KEY_FEED, Math.max(feed(ctx), f))
                .apply();
        String cloudJson = snap.getString("invQueensJson");
        List<Integer> local = queens(ctx);
        List<Integer> cloud = QueenInventoryCodec.parse(cloudJson);
        if (cloud.isEmpty()) {
            int legacyCloud = readInt(snap, "invQueens100");
            if (legacyCloud > local.size()) {
                saveQueens(ctx, QueenInventoryCodec.fromLegacyCount(
                        legacyCloud, DemandSurgeMilestones.QUEEN_QUALITY));
            }
            return;
        }
        if (cloud.size() > local.size()) {
            saveQueens(ctx, cloud);
        }
    }

    private static void saveQueens(Context ctx, List<Integer> qualities) {
        prefs(ctx).edit()
                .putString(KEY_QUEENS_JSON, QueenInventoryCodec.toJson(qualities))
                .remove(KEY_QUEENS_LEGACY)
                .apply();
    }

    private static int readInt(DocumentSnapshot snap, String field) {
        Object o = snap.get(field);
        if (o instanceof Number) {
            return Math.max(0, ((Number) o).intValue());
        }
        return 0;
    }
}
