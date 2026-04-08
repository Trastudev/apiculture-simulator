package com.apiculture.simulator.data.repository;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class AuthRepository {
    private final FirebaseAuth firebaseAuth;

    public AuthRepository() {
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

    /** Cierra la sesión en Firebase (si está configurado). */
    public void signOut() {
        if (firebaseAuth != null) {
            firebaseAuth.signOut();
        }
    }
}
