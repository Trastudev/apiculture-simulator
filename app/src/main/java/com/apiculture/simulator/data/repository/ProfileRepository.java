package com.apiculture.simulator.data.repository;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.Transaction;

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

    /** Si no hay Firestore o falla la lectura, se asume perfil completo para no bloquear el juego. */
    public void fetchProfileComplete(@Nullable String uid, @NonNull Consumer<Boolean> callback) {
        if (firestore == null || uid == null || uid.isEmpty()) {
            callback.accept(true);
            return;
        }
        io.execute(() -> {
            try {
                DocumentSnapshot doc = Tasks.await(
                        firestore.collection(USERS).document(uid).get());
                Boolean c = doc != null && doc.exists() ? doc.getBoolean("profileComplete") : null;
                boolean done = Boolean.TRUE.equals(c);
                runOnMain(callback, done);
            } catch (Exception e) {
                runOnMain(callback, true);
            }
        });
    }

    public void fetchDisplayProfile(@Nullable String uid, @NonNull Consumer<ProfileDisplay> callback) {
        if (firestore == null || uid == null || uid.isEmpty()) {
            callback.accept(ProfileDisplay.empty());
            return;
        }
        io.execute(() -> {
            try {
                DocumentSnapshot doc = Tasks.await(
                        firestore.collection(USERS).document(uid).get());
                if (doc == null || !doc.exists()) {
                    runOnMain(callback, ProfileDisplay.empty());
                    return;
                }
                String name = doc.getString("playerName");
                String brand = doc.getString("honeyBrand");
                runOnMain(callback, new ProfileDisplay(
                        name != null ? name : "",
                        brand != null ? brand : ""));
            } catch (Exception e) {
                runOnMain(callback, ProfileDisplay.empty());
            }
        });
    }

    public void saveProfile(@NonNull String uid, @NonNull String honeyBrandRaw, @NonNull String playerNameRaw,
            @NonNull Runnable onSuccess, @NonNull Consumer<String> onError) {
        if (firestore == null) {
            onError.accept("Sin conexión a la nube.");
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

        DocumentReference userRef = firestore.collection(USERS).document(uid);
        DocumentReference brandRef = firestore.collection(UNIQUE_BRANDS).document(brandKey);
        DocumentReference nameRef = firestore.collection(UNIQUE_NAMES).document(nameKey);

        io.execute(() -> {
            try {
                Tasks.await(firestore.runTransaction((Transaction transaction) -> {
                    DocumentSnapshot bSnap = transaction.get(brandRef);
                    if (bSnap.exists()) {
                        String ou = bSnap.getString("ownerUid");
                        if (ou != null && !ou.equals(uid)) {
                            throw new RuntimeException(ERR_BRAND_TAKEN);
                        }
                    }
                    DocumentSnapshot nSnap = transaction.get(nameRef);
                    if (nSnap.exists()) {
                        String ou = nSnap.getString("ownerUid");
                        if (ou != null && !ou.equals(uid)) {
                            throw new RuntimeException(ERR_NAME_TAKEN);
                        }
                    }
                    Map<String, Object> u = new HashMap<>();
                    u.put("honeyBrand", honeyBrand);
                    u.put("playerName", playerName);
                    u.put("profileComplete", true);
                    transaction.set(userRef, u, SetOptions.merge());
                    Map<String, Object> claim = new HashMap<>();
                    claim.put("ownerUid", uid);
                    transaction.set(brandRef, claim);
                    transaction.set(nameRef, claim);
                    return null;
                }));
                runOnMain(onSuccess);
            } catch (Exception e) {
                String code = findCode(e);
                if (ERR_BRAND_TAKEN.equals(code)) {
                    runOnMain(() -> onError.accept(ERR_BRAND_TAKEN));
                } else if (ERR_NAME_TAKEN.equals(code)) {
                    runOnMain(() -> onError.accept(ERR_NAME_TAKEN));
                } else {
                    runOnMain(() -> onError.accept(e.getMessage() != null ? e.getMessage() : "SAVE_FAIL"));
                }
            }
        });
    }

    private void runOnMain(Runnable r) {
        android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
        main.post(r);
    }

    private void runOnMain(@NonNull Consumer<Boolean> c, boolean v) {
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

    public static final class ProfileDisplay {
        public final String playerName;
        public final String honeyBrand;

        public ProfileDisplay(String playerName, String honeyBrand) {
            this.playerName = playerName;
            this.honeyBrand = honeyBrand;
        }

        public static ProfileDisplay empty() {
            return new ProfileDisplay("", "");
        }
    }
}
