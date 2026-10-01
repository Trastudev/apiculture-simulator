package com.apiculture.simulator.data.session;

import androidx.annotation.Nullable;

/** Cuenta de Google con la que está abierta la partida. */
public final class SignedInUser {

    private final String uid;
    @Nullable
    private final String email;
    @Nullable
    private final String displayName;
    @Nullable
    private final String idToken;

    SignedInUser(@Nullable String uid, @Nullable String email, @Nullable String displayName,
            @Nullable String idToken) {
        this.uid = uid != null ? uid : "";
        this.email = email;
        this.displayName = displayName;
        this.idToken = idToken;
    }

    public String getUid() {
        return uid;
    }

    @Nullable
    public String getEmail() {
        return email;
    }

    @Nullable
    public String getDisplayName() {
        return displayName;
    }

    @Nullable
    String idToken() {
        return idToken;
    }
}
