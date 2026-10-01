package com.apiculture.simulator.data.session;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.tasks.Tasks;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Sesión de Google en el teléfono. El identificador del jugador es el de la cuenta
 * de Google, no un usuario de Firebase.
 */
public final class PlayerAuth {

    private static final String PREFS = "google_player_session";
    private static final PlayerAuth INSTANCE = new PlayerAuth();

    private final List<Consumer<PlayerAuth>> listeners = new CopyOnWriteArrayList<>();
    @Nullable
    private Context app;
    @Nullable
    private SignedInUser user;

    private PlayerAuth() {
    }

    public static PlayerAuth getInstance() {
        return INSTANCE;
    }

    public void install(@Nullable Context context) {
        if (context == null || app != null) {
            return;
        }
        app = context.getApplicationContext();
        user = read();
    }

    @Nullable
    public SignedInUser getCurrentUser() {
        return user != null && !user.getUid().isEmpty() ? user : null;
    }

    @Nullable
    public String getUid() {
        SignedInUser current = getCurrentUser();
        return current != null ? current.getUid() : null;
    }

    public void addAuthStateListener(@Nullable Consumer<PlayerAuth> listener) {
        if (listener == null) {
            return;
        }
        listeners.add(listener);
        listener.accept(this);
    }

    public void adopt(@Nullable GoogleSignInAccount account) {
        if (account == null || account.getId() == null || account.getId().isEmpty()) {
            return;
        }
        String previous = getUid();
        user = new SignedInUser(account.getId(), account.getEmail(), account.getDisplayName(),
                account.getIdToken());
        write();
        if (previous == null || !previous.equals(user.getUid())) {
            notifyListeners();
        }
    }

    public void signOut() {
        clearSession(() -> client().signOut());
    }

    /** Cierra la sesión y retira el permiso que la app tenía sobre la cuenta de Google. */
    public void disconnect() {
        clearSession(() -> client().revokeAccess());
    }

    private void clearSession(@NonNull Runnable google) {
        user = null;
        if (app != null) {
            prefs().edit().clear().commit();
            try {
                google.run();
            } catch (Exception ignored) {
            }
        }
        notifyListeners();
    }

    /** Token vigente para el servidor. Renueva la sesión de Google si el anterior caducó. */
    @Nullable
    public String freshIdToken() {
        SignedInUser current = getCurrentUser();
        if (current != null && current.idToken() != null && !expired(current.idToken())) {
            return current.idToken();
        }
        if (app == null) {
            return current != null ? current.idToken() : null;
        }
        try {
            GoogleSignInAccount account = Tasks.await(client().silentSignIn(), 8, TimeUnit.SECONDS);
            if (account != null && account.getIdToken() != null) {
                adopt(account);
                return account.getIdToken();
            }
        } catch (Exception ignored) {
        }
        return current != null ? current.idToken() : null;
    }

    public GoogleSignInClient client() {
        GoogleSignInOptions.Builder options = new GoogleSignInOptions.Builder(
                GoogleSignInOptions.DEFAULT_SIGN_IN).requestEmail();
        String webId = webClientId();
        if (webId != null) {
            options.requestIdToken(webId);
        }
        return GoogleSignIn.getClient(app, options.build());
    }

    @Nullable
    public String webClientId() {
        if (app == null) {
            return null;
        }
        int id = app.getResources().getIdentifier(
                "default_web_client_id", "string", app.getPackageName());
        if (id == 0) {
            return null;
        }
        String value = app.getString(id);
        if (value == null || value.trim().isEmpty() || value.startsWith("YOUR_")) {
            return null;
        }
        return value.trim();
    }

    private void notifyListeners() {
        for (Consumer<PlayerAuth> listener : listeners) {
            listener.accept(this);
        }
    }

    @Nullable
    private SignedInUser read() {
        if (app == null) {
            return null;
        }
        SharedPreferences prefs = prefs();
        String uid = prefs.getString("uid", "");
        if (uid == null || uid.isEmpty()) {
            return null;
        }
        return new SignedInUser(uid, prefs.getString("email", null),
                prefs.getString("name", null), prefs.getString("token", null));
    }

    private void write() {
        if (app == null || user == null) {
            return;
        }
        prefs().edit()
                .putString("uid", user.getUid())
                .putString("email", user.getEmail())
                .putString("name", user.getDisplayName())
                .putString("token", user.idToken())
                .apply();
    }

    private SharedPreferences prefs() {
        return app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static boolean expired(@Nullable String jwt) {
        if (jwt == null || jwt.isEmpty()) {
            return true;
        }
        try {
            String[] parts = jwt.split("\\.");
            if (parts.length < 2) {
                return true;
            }
            byte[] json = Base64.decode(parts[1], Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
            JSONObject payload = new JSONObject(new String(json, StandardCharsets.UTF_8));
            long expMs = payload.optLong("exp", 0L) * 1000L;
            return expMs < System.currentTimeMillis() + 120_000L;
        } catch (Exception e) {
            return true;
        }
    }
}
