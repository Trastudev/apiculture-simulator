package com.apiculture.simulator.data.repository;

import android.content.Context;
import android.content.Intent;

import androidx.annotation.Nullable;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;

public class AuthRepository {
    private final Context appContext;
    private final FirebaseAuth firebaseAuth;

    public AuthRepository(Context context) {
        this.appContext = context.getApplicationContext();
        FirebaseAuth instance;
        try {
            instance = FirebaseAuth.getInstance();
        } catch (Exception e) {
            instance = null;
        }
        firebaseAuth = instance;
    }

    public interface AuthCallback {
        void onSuccess(FirebaseUser user);
        void onError(String message);
    }

    public FirebaseUser getCurrentUser() {
        if (firebaseAuth == null) return null;
        return firebaseAuth.getCurrentUser();
    }

    public void login(String email, String password, AuthCallback callback) {
        if (firebaseAuth == null) {
            // Offline mode fallback when Firebase is not configured.
            callback.onSuccess(null);
            return;
        }
        firebaseAuth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(result -> callback.onSuccess(result.getUser()))
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    public void register(String email, String password, AuthCallback callback) {
        if (firebaseAuth == null) {
            callback.onSuccess(null);
            return;
        }
        firebaseAuth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(result -> callback.onSuccess(result.getUser()))
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    /** Client ID web de Firebase (`default_web_client_id`); vacío si no hay google-services.json. */
    @Nullable
    public String googleWebClientId() {
        int id = appContext.getResources().getIdentifier(
                "default_web_client_id", "string", appContext.getPackageName());
        if (id == 0) {
            return null;
        }
        String value = appContext.getString(id);
        if (value == null || value.trim().isEmpty() || value.startsWith("YOUR_")) {
            return null;
        }
        return value.trim();
    }

    public boolean isGoogleSignInConfigured() {
        return firebaseAuth != null && googleWebClientId() != null;
    }

    public Intent googleSignInIntent() {
        return googleSignInClient().getSignInIntent();
    }

    public void signInWithGoogleIdToken(String idToken, AuthCallback callback) {
        if (firebaseAuth == null) {
            callback.onError("Firebase Auth no está configurado.");
            return;
        }
        if (idToken == null || idToken.isEmpty()) {
            callback.onError("No se recibió el token de Google.");
            return;
        }
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        firebaseAuth.signInWithCredential(credential)
                .addOnSuccessListener(result -> callback.onSuccess(result.getUser()))
                .addOnFailureListener(e -> callback.onError(
                        e.getMessage() != null ? e.getMessage() : "Error al entrar con Google."));
    }

    /** Cierra la sesión en Firebase y en Google (si está configurado). */
    public void signOut() {
        if (firebaseAuth != null) {
            firebaseAuth.signOut();
        }
        try {
            googleSignInClient().signOut();
        } catch (Exception ignored) {
        }
    }

    private GoogleSignInClient googleSignInClient() {
        GoogleSignInOptions.Builder b = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail();
        String webId = googleWebClientId();
        if (webId != null) {
            b.requestIdToken(webId);
        }
        return GoogleSignIn.getClient(appContext, b.build());
    }
}
