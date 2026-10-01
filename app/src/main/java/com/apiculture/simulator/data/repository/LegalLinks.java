package com.apiculture.simulator.data.repository;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.annotation.NonNull;

import com.apiculture.simulator.BuildConfig;

/** Direcciones públicas que Google Play exige enlazar desde la app. */
public final class LegalLinks {

    private LegalLinks() {
    }

    @NonNull
    public static String privacyPolicyUrl() {
        return configured(BuildConfig.PRIVACY_POLICY_URL);
    }

    @NonNull
    public static String accountDeletionUrl() {
        return configured(BuildConfig.ACCOUNT_DELETION_URL);
    }

    public static void open(@NonNull Context context, @NonNull String url) {
        if (url.isEmpty()) {
            return;
        }
        context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
    }

    @NonNull
    private static String configured(@NonNull String value) {
        String trimmed = value.trim();
        if (!trimmed.startsWith("https://")) {
            return "";
        }
        return trimmed;
    }
}
