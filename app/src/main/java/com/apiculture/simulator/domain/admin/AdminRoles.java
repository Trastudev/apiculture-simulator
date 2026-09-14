package com.apiculture.simulator.domain.admin;

import androidx.annotation.Nullable;

import java.text.Normalizer;
import java.util.Locale;

/**
 * La cuenta cuyo nombre de jugador es «Aleix» tiene rol administrador.
 */
public final class AdminRoles {

    public static final String ADMIN_PLAYER_NAME = "Aleix";

    private AdminRoles() {
    }

    public static boolean isAdminPlayerName(@Nullable String playerName) {
        if (playerName == null) {
            return false;
        }
        return fold(playerName).equals(fold(ADMIN_PLAYER_NAME));
    }

    /** Id de {@code uniquePlayerNames} para las reglas de Firestore. */
    public static String uniqueNameDocId() {
        return fold(ADMIN_PLAYER_NAME);
    }

    static String fold(String raw) {
        String n = Normalizer.normalize(raw.trim().toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
        n = n.replaceAll("\\p{M}+", "");
        n = n.replaceAll("[^a-z0-9]+", "");
        return n;
    }
}
