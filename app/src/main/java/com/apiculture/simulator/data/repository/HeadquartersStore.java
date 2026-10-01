package com.apiculture.simulator.data.repository;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.apiculture.simulator.domain.map.PlayableMapRegion;
import com.google.firebase.firestore.FirebaseFirestore;

import org.json.JSONObject;

/**
 * Una sede central por territorio jugable. El jugador la coloca y la puede mover.
 */
public final class HeadquartersStore {

    public static final class Hq {
        public final PlayableMapRegion region;
        public final double lat;
        public final double lng;

        public Hq(@NonNull PlayableMapRegion region, double lat, double lng) {
            this.region = region;
            this.lat = lat;
            this.lng = lng;
        }
    }

    private static final String PREFS = "headquarters_v1";

    private HeadquartersStore() {
    }

    @Nullable
    public static Hq get(@NonNull Context context, @Nullable String ownerId,
            @Nullable PlayableMapRegion region) {
        PlayableMapRegion r = region != null ? region : PlayableMapRegion.IBERIA;
        SharedPreferences p = prefs(context);
        String latK = latKey(ownerId, r);
        String lngK = lngKey(ownerId, r);
        if (!p.contains(latK) || !p.contains(lngK)) {
            return null;
        }
        double lat = Double.longBitsToDouble(p.getLong(latK, 0L));
        double lng = Double.longBitsToDouble(p.getLong(lngK, 0L));
        if (Math.abs(lat) < 1e-8 && Math.abs(lng) < 1e-8) {
            return null;
        }
        if (!r.containsHive(lat, lng)) {
            return null;
        }
        return new Hq(r, lat, lng);
    }

    public static boolean has(@NonNull Context context, @Nullable String ownerId,
            @Nullable PlayableMapRegion region) {
        return get(context, ownerId, region) != null;
    }

    public static void clear(@NonNull Context context, @Nullable String ownerId) {
        SharedPreferences.Editor edit = prefs(context).edit();
        for (PlayableMapRegion region : PlayableMapRegion.values()) {
            edit.remove(latKey(ownerId, region));
            edit.remove(lngKey(ownerId, region));
        }
        edit.commit();
    }

    @Nullable
    public static String place(@NonNull Context context, @Nullable String ownerId,
            double lat, double lng) {
        PlayableMapRegion r = PlayableMapRegion.containing(lat, lng);
        if (r == null || !r.containsHive(lat, lng)) {
            return "La sede tiene que estar en un territorio jugable.";
        }
        if (!persistCloud(ownerId, r, lat, lng)) {
            return EconomyRepository.OFFLINE_ACTION;
        }
        SharedPreferences p = prefs(context);
        p.edit()
                .putLong(latKey(ownerId, r), Double.doubleToRawLongBits(lat))
                .putLong(lngKey(ownerId, r), Double.doubleToRawLongBits(lng))
                .apply();
        return null;
    }

    public static void hydrateFromCloud(@NonNull Context context, @Nullable String ownerId,
            @Nullable FirebaseFirestore firestore, @Nullable Runnable onDone) {
        if (ownerId == null || ownerId.isEmpty() || "guest".equals(ownerId) || !GameServer.enabled()) {
            if (onDone != null) {
                onDone.run();
            }
            return;
        }
        Context app = context.getApplicationContext();
        Handler main = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            JSONObject body = GameServer.loadStore(ownerId, "hq");
            if (body != null) {
                applyServer(app, ownerId, body);
            }
            if (onDone != null) {
                main.post(onDone);
            }
        }, "hq-pull").start();
    }

    public static void applyServer(@NonNull Context app, @NonNull String ownerId, @NonNull JSONObject body) {
        applyCloudField(app, ownerId, PlayableMapRegion.IBERIA,
                number(body, "hqIberiaLat"), number(body, "hqIberiaLng"));
        applyCloudField(app, ownerId, PlayableMapRegion.SOUTH_AFRICA,
                number(body, "hqZaLat"), number(body, "hqZaLng"));
        applyCloudField(app, ownerId, PlayableMapRegion.MADAGASCAR,
                number(body, "hqMdgLat"), number(body, "hqMdgLng"));
    }

    @Nullable
    private static Double number(@NonNull JSONObject body, @NonNull String key) {
        if (!body.has(key) || body.isNull(key)) {
            return null;
        }
        return body.optDouble(key);
    }

    private static void applyCloudField(@NonNull Context app, @NonNull String ownerId,
            @NonNull PlayableMapRegion region, @Nullable Object latObj, @Nullable Object lngObj) {
        if (!(latObj instanceof Number) || !(lngObj instanceof Number)) {
            return;
        }
        if (has(app, ownerId, region)) {
            return;
        }
        double lat = ((Number) latObj).doubleValue();
        double lng = ((Number) lngObj).doubleValue();
        if (!region.containsHive(lat, lng)) {
            return;
        }
        prefs(app).edit()
                .putLong(latKey(ownerId, region), Double.doubleToRawLongBits(lat))
                .putLong(lngKey(ownerId, region), Double.doubleToRawLongBits(lng))
                .apply();
    }

    private static boolean persistCloud(@Nullable String ownerId, @NonNull PlayableMapRegion region,
            double lat, double lng) {
        if (!GameServer.enabled()) {
            return true;
        }
        if (ownerId == null || ownerId.isEmpty() || "guest".equals(ownerId)
                || android.os.Looper.getMainLooper().isCurrentThread()) {
            return false;
        }
        try {
            JSONObject body = GameServer.loadStore(ownerId, "hq");
            if (body == null) {
                body = new JSONObject();
            }
            if (region == PlayableMapRegion.SOUTH_AFRICA) {
                body.put("hqZaLat", lat);
                body.put("hqZaLng", lng);
            } else if (region == PlayableMapRegion.MADAGASCAR) {
                body.put("hqMdgLat", lat);
                body.put("hqMdgLng", lng);
            } else {
                body.put("hqIberiaLat", lat);
                body.put("hqIberiaLng", lng);
            }
            return GameServer.saveStore(ownerId, "hq", body);
        } catch (Exception ignored) {
            return false;
        }
    }

    @NonNull
    private static SharedPreferences prefs(@NonNull Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    @NonNull
    private static String ownerKey(@Nullable String ownerId) {
        return ownerId == null || ownerId.isEmpty() ? "guest" : ownerId;
    }

    @NonNull
    private static String latKey(@Nullable String ownerId, @NonNull PlayableMapRegion region) {
        return ownerKey(ownerId) + "_" + region.prefsValue() + "_lat";
    }

    @NonNull
    private static String lngKey(@Nullable String ownerId, @NonNull PlayableMapRegion region) {
        return ownerKey(ownerId) + "_" + region.prefsValue() + "_lng";
    }
}
