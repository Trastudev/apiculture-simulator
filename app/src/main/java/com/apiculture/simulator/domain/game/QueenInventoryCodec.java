package com.apiculture.simulator.domain.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Serializa la lista de reinas del inventario ({@code calidad} 0–100).
 */
public final class QueenInventoryCodec {

    private QueenInventoryCodec() {
    }

    public static List<Integer> parse(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return new ArrayList<>();
        }
        String s = raw.trim();
        List<Integer> out = new ArrayList<>();
        if (s.startsWith("[")) {
            s = s.substring(1);
        }
        if (s.endsWith("]")) {
            s = s.substring(0, s.length() - 1);
        }
        if (s.trim().isEmpty()) {
            return out;
        }
        for (String part : s.split(",")) {
            try {
                int q = Integer.parseInt(part.trim());
                out.add(clamp(q));
            } catch (NumberFormatException ignored) {
            }
        }
        return out;
    }

    public static String toJson(List<Integer> qualities) {
        if (qualities == null || qualities.isEmpty()) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < qualities.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(clamp(qualities.get(i)));
        }
        sb.append(']');
        return sb.toString();
    }

    public static List<Integer> fromLegacyCount(int count, int quality) {
        int n = Math.max(0, count);
        List<Integer> out = new ArrayList<>(n);
        int q = clamp(quality);
        for (int i = 0; i < n; i++) {
            out.add(q);
        }
        return out;
    }

    public static String label(int quality) {
        return String.format(Locale.getDefault(), "Reina %d%%", clamp(quality));
    }

    public static int clamp(int quality) {
        return Math.max(0, Math.min(100, quality));
    }

    public static List<Integer> copy(List<Integer> src) {
        return src == null ? new ArrayList<>() : new ArrayList<>(src);
    }

    public static List<Integer> unmodifiable(List<Integer> src) {
        return Collections.unmodifiableList(copy(src));
    }
}
