package com.apiculture.simulator.data.repository;

import android.content.Context;
import android.content.Intent;

import androidx.annotation.Nullable;

import com.apiculture.simulator.data.session.PlayerAuth;
import com.apiculture.simulator.data.session.SignedInUser;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;

public class AuthRepository {

    private final PlayerAuth session;

    public AuthRepository(Context context) {
        session = PlayerAuth.getInstance();
        session.install(context.getApplicationContext());
    }

    public interface AuthCallback {
        void onSuccess(@Nullable SignedInUser user);
        void onError(String message);
    }

    @Nullable
    public SignedInUser getCurrentUser() {
        return session.getCurrentUser();
    }

    public boolean isGoogleSignInConfigured() {
        return session.webClientId() != null;
    }

    public Intent googleSignInIntent() {
        return session.client().getSignInIntent();
    }

    public void signInWithGoogle(@Nullable GoogleSignInAccount account, AuthCallback callback) {
        if (account == null || account.getId() == null || account.getId().isEmpty()) {
            callback.onError("No se recibió la cuenta de Google.");
            return;
        }
        if (account.getIdToken() == null || account.getIdToken().isEmpty()) {
            callback.onError("No se recibió el token de Google.");
            return;
        }
        session.adopt(account);
        callback.onSuccess(session.getCurrentUser());
    }

    public void signOut() {
        session.signOut();
    }
}
