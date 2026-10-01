package com.apiculture.simulator.data.repository;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.Transaction;

import org.json.JSONObject;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Perfil de jugador en Firestore y registro global de marcas / nombres únicos.
 */
public class ProfileRepository {

    private static final String USERS = "users";
    private static final String UNIQUE_BRANDS = "uniqueHoneyBrands";
    private static final String UNIQUE_NAMES = "uniquePlayerNames";

    public static final String ERR_BRAND_TAKEN = "BRAND_TAKEN";
    public static final String ERR_NAME_TAKEN = "NAME_TAKEN";

    private final FirebaseFirestore firestore;
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    public ProfileRepository(@Nullable FirebaseFirestore firestore) {
        this.firestore = firestore;
    }

    public void shutdown() {
        io.shutdown();
    }

    public static final int PROFILE_READY = 1;
    /** No hay ficha, o la hay pero falta marca y nombre. */
    public static final int PROFILE_NEEDED = 0;
    /** El servidor no respondió: no se puede saber si el perfil existe. */
    public static final int PROFILE_OFFLINE = -1;
    /** El servidor respondió, pero no acepta el token de Google. */
    public static final int PROFILE_UNAUTHORIZED = -2;

    /**
     * {@link #PROFILE_READY} si el perfil está completo, {@link #PROFILE_NEEDED} solo con 404
     * o ficha incompleta, {@link #PROFILE_OFFLINE} si la red falla.
     */
    public void fetchProfileComplete(@Nullable String uid, @NonNull Consumer<Integer> callback) {
        if (!GameServer.enabled() || uid == null || uid.isEmpty()) {
            callback.accept(PROFILE_READY);
            return;
        }
        io.execute(() -> {
            try {
                GameServer.PlayerLoad load = GameServer.loadPlayerStatus(uid);
                if (load.status == 404) {
                    runOnMain(callback, PROFILE_NEEDED);
                    return;
                }
                if (load.status == 401 || load.status == 403) {
                    runOnMain(callback, PROFILE_UNAUTHORIZED);
                    return;
                }
                if (load.status == 0 || load.status >= 500 || load.body == null) {
                    runOnMain(callback, PROFILE_OFFLINE);
                    return;
                }
                boolean done = load.body.optBoolean("profileComplete", false);
                runOnMain(callback, done ? PROFILE_READY : PROFILE_NEEDED);
            } catch (Exception e) {
                runOnMain(callback, PROFILE_OFFLINE);
            }
        });
    }

    public void fetchDisplayProfile(@Nullable String uid, @NonNull Consumer<ProfileDisplay> callback) {
        if (!GameServer.enabled() || uid == null || uid.isEmpty()) {
            callback.accept(ProfileDisplay.empty());
            return;
        }
        io.execute(() -> {
            try {
                JSONObject doc = GameServer.loadPlayer(uid);
                if (doc == null) {
                    doc = GameServer.loadPlayerCard(uid);
                }
                if (doc == null) {
                    runOnMain(callback, ProfileDisplay.empty());
                    return;
                }
                JSONObject photoStore = GameServer.loadStore(uid, "photo");
                String photo = photoStore != null ? photoStore.optString("photoBase64", "") : "";
                runOnMain(callback, new ProfileDisplay(
                        doc.optString("playerName", ""),
                        doc.optString("honeyBrand", ""),
                        photo));
            } catch (Exception e) {
                runOnMain(callback, ProfileDisplay.empty());
            }
        });
    }

    public void saveProfile(@NonNull String uid, @NonNull String honeyBrandRaw, @NonNull String playerNameRaw,
            @NonNull Runnable onSuccess, @NonNull Consumer<String> onError) {
        if (!GameServer.enabled()) {
            onError.accept("Sin conexión al servidor.");
            return;
        }
        String honeyBrand = honeyBrandRaw.trim();
        String playerName = playerNameRaw.trim();
        if (honeyBrand.length() < 2 || playerName.length() < 2) {
            onError.accept("SHORT");
            return;
        }
        String brandKey = docIdForLookup(honeyBrand);
        String nameKey = docIdForLookup(playerName);
        if (brandKey.isEmpty() || nameKey.isEmpty()) {
            onError.accept("INVALID");
            return;
        }

        io.execute(() -> {
            int brand = GameServer.claimUnique("brand", brandKey, uid);
            if (brand == 403) {
                runOnMain(() -> onError.accept(ERR_BRAND_TAKEN));
                return;
            }
            int name = GameServer.claimUnique("name", nameKey, uid);
            if (name == 403) {
                runOnMain(() -> onError.accept(ERR_NAME_TAKEN));
                return;
            }
            if (brand / 100 != 2 || name / 100 != 2) {
                runOnMain(() -> onError.accept("SAVE_FAIL"));
                return;
            }
            try {
                JSONObject body = new JSONObject();
                body.put("honeyBrand", honeyBrand);
                body.put("playerName", playerName);
                body.put("profileComplete", true);
                if (!GameServer.savePlayer(uid, body)) {
                    runOnMain(() -> onError.accept("SAVE_FAIL"));
                    return;
                }
                runOnMain(onSuccess);
            } catch (Exception e) {
                runOnMain(() -> onError.accept(e.getMessage() != null ? e.getMessage() : "SAVE_FAIL"));
            }
        });
    }

    private void runOnMain(Runnable r) {
        android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
        main.post(r);
    }

    private void runOnMain(@NonNull Consumer<Integer> c, int v) {
        runOnMain(() -> c.accept(v));
    }

    private void runOnMain(@NonNull Consumer<ProfileDisplay> c, ProfileDisplay p) {
        runOnMain(() -> c.accept(p));
    }

    private static String findCode(Throwable e) {
        Throwable t = e;
        while (t != null) {
            String m = t.getMessage();
            if (ERR_BRAND_TAKEN.equals(m) || ERR_NAME_TAKEN.equals(m)) {
                return m;
            }
            t = t.getCause();
        }
        return null;
    }

    static String docIdForLookup(String s) {
        if (s == null) {
            return "";
        }
        String n = Normalizer.normalize(s.trim().toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
        n = n.replaceAll("\\p{M}+", "");
        n = n.replaceAll("[^a-z0-9]+", "_");
        while (n.startsWith("_")) {
            n = n.substring(1);
        }
        while (n.endsWith("_")) {
            n = n.substring(0, n.length() - 1);
        }
        if (n.length() > 600) {
            n = n.substring(0, 600);
        }
        return n;
    }

    public void updatePlayerName(
            @NonNull String uid,
            @NonNull String playerNameRaw,
            @NonNull Runnable onSuccess,
            @NonNull Consumer<String> onError) {
        if (!GameServer.enabled()) {
            onError.accept("Sin conexión al servidor.");
            return;
        }
        String playerName = playerNameRaw.trim();
        if (playerName.length() < 2) {
            onError.accept("SHORT");
            return;
        }
        String nameKey = docIdForLookup(playerName);
        if (nameKey.isEmpty()) {
            onError.accept("INVALID");
            return;
        }
        io.execute(() -> {
            try {
                JSONObject current = GameServer.loadPlayer(uid);
                String oldName = current != null ? current.optString("playerName", "") : "";
                String oldKey = docIdForLookup(oldName);
                if (!nameKey.equals(oldKey)) {
                    int claimed = GameServer.claimUnique("name", nameKey, uid);
                    if (claimed == 403) {
                        runOnMain(() -> onError.accept(ERR_NAME_TAKEN));
                        return;
                    }
                    if (claimed / 100 != 2) {
                        runOnMain(() -> onError.accept("SAVE_FAIL"));
                        return;
                    }
                    if (!oldKey.isEmpty()) {
                        GameServer.deletePath("/unique-names/" + oldKeyPath(oldKey));
                    }
                }
                JSONObject body = new JSONObject();
                body.put("playerName", playerName);
                if (!GameServer.savePlayer(uid, body)) {
                    runOnMain(() -> onError.accept("SAVE_FAIL"));
                    return;
                }
                runOnMain(onSuccess);
            } catch (Exception e) {
                runOnMain(() -> onError.accept(e.getMessage() != null ? e.getMessage() : "SAVE_FAIL"));
            }
        });
    }

    public void saveGameLocale(
            @NonNull String uid,
            @NonNull String tag,
            @NonNull Runnable onSuccess,
            @NonNull Runnable onError) {
        if (!GameServer.enabled()) {
            onError.run();
            return;
        }
        io.execute(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("gameLocale", tag);
                if (!GameServer.savePlayer(uid, body)) {
                    runOnMain(onError);
                    return;
                }
                runOnMain(onSuccess);
            } catch (Exception e) {
                runOnMain(onError);
            }
        });
    }

    public void savePhotoBase64(
            @NonNull String uid,
            @NonNull String photoBase64,
            @NonNull Runnable onSuccess,
            @NonNull Consumer<String> onError) {
        if (!GameServer.enabled()) {
            onError.accept("Sin conexión al servidor.");
            return;
        }
        io.execute(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("photoBase64", photoBase64);
                GameServer.saveStore(uid, "photo", body);
                runOnMain(onSuccess);
            } catch (Exception e) {
                runOnMain(() -> onError.accept(e.getMessage() != null ? e.getMessage() : "SAVE_FAIL"));
            }
        });
    }

    @NonNull
    private static String oldKeyPath(@NonNull String key) {
        String id = "name_" + key;
        if (id.length() > 200) {
            id = id.substring(0, 200);
        }
        try {
            return java.net.URLEncoder.encode(id, "UTF-8");
        } catch (Exception e) {
            return id;
        }
    }

    public static final class ProfileDisplay {
        public final String playerName;
        public final String honeyBrand;
        public final String photoBase64;

        public ProfileDisplay(String playerName, String honeyBrand) {
            this(playerName, honeyBrand, "");
        }

        public ProfileDisplay(String playerName, String honeyBrand, String photoBase64) {
            this.playerName = playerName;
            this.honeyBrand = honeyBrand;
            this.photoBase64 = photoBase64 != null ? photoBase64 : "";
        }

        public static ProfileDisplay empty() {
            return new ProfileDisplay("", "", "");
        }
    }

    public static final class PlayerOption {
        public final String uid;
        public final String playerName;
        public final String honeyBrand;

        public PlayerOption(String uid, String playerName, String honeyBrand) {
            this.uid = uid != null ? uid : "";
            this.playerName = playerName != null ? playerName : "";
            this.honeyBrand = honeyBrand != null ? honeyBrand : "";
        }

        public String label() {
            String name = playerName.isEmpty() ? "Sin nombre" : playerName;
            if (honeyBrand.isEmpty()) {
                return name;
            }
            return name + " · " + honeyBrand;
        }
    }

    public void listPlayers(@NonNull Consumer<java.util.List<PlayerOption>> callback) {
        if (!GameServer.enabled()) {
            callback.accept(java.util.Collections.emptyList());
            return;
        }
        io.execute(() -> {
            java.util.List<PlayerOption> out = new java.util.ArrayList<>();
            try {
                org.json.JSONArray snap = GameServer.fetchArray("/players");
                if (snap != null) {
                    for (int i = 0; i < snap.length(); i++) {
                        JSONObject d = snap.optJSONObject(i);
                        if (d == null) {
                            continue;
                        }
                        out.add(new PlayerOption(d.optString("id", ""),
                                d.optString("playerName", ""),
                                d.optString("honeyBrand", "")));
                    }
                }
                out.sort((a, b) -> a.label().compareToIgnoreCase(b.label()));
            } catch (Exception ignored) {
            }
            runOnMain(() -> callback.accept(out));
        });
    }
}
