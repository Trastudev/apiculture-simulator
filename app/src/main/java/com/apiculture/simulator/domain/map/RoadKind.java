package com.apiculture.simulator.domain.map;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Clasificación de vías para pintar la ruta y estimar el tiempo.
 * Autopista / nacional / comarcal / resto (acceso a campo, terciarias…).
 */
public enum RoadKind {
    AUTOPISTA('A', 110, 0xC81976D2, 7f),
    NACIONAL('N', 90, 0xC8D32F2F, 6f),
    COMARCAL('C', 70, 0xC8F9A825, 5f),
    OTRO('O', 45, 0xC89E9E9E, 4f);

    public final char code;
    public final double cruiseKmh;
    public final int argb;
    public final float width;

    RoadKind(char code, double cruiseKmh, int argb, float width) {
        this.code = code;
        this.cruiseKmh = cruiseKmh;
        this.argb = argb;
        this.width = width;
    }

    @NonNull
    public static RoadKind ofCode(char code) {
        switch (code) {
            case 'A':
            case 'a':
                return AUTOPISTA;
            case 'N':
            case 'n':
                return NACIONAL;
            case 'C':
            case 'c':
                return COMARCAL;
            default:
                return OTRO;
        }
    }

    @NonNull
    public static RoadKind at(@Nullable String packed, int edge) {
        if (packed == null || packed.isEmpty() || edge < 0) {
            return OTRO;
        }
        int i = Math.min(edge, packed.length() - 1);
        return ofCode(packed.charAt(i));
    }

    private static final Pattern AUTOPISTA_REF = Pattern.compile(
            "\\b(?:AP-\\d|A-\\d{1,3}|R-[2-5]|M-(?:11|12|13|14|21|23|30|31|40|45|50)|"
                    + "B-(?:10|20|21|22|23|24|25|30)|V-3[01])\\b"
                    + "|\\b(?:AUTOPISTA|AUTOVIA|AUTOESTRADA)\\b"
                    + "|\\bA[1-9]\\d?\\b");
    private static final Pattern NACIONAL_REF = Pattern.compile(
            "\\bN-\\d|\\bN-[IVX]+\\b|\\bNACIONAL\\b|\\bEN[-\\s]?\\d|\\bIP[-\\s]?\\d|\\bIC[-\\s]?\\d"
                    + "|\\bRN[-\\s]?\\d|\\bN[1-9]\\d{0,2}\\b");
    private static final Pattern COMARCAL_REF = Pattern.compile(
            "\\bC-\\d|\\bCOMARCAL\\b|\\b(?:EM|ER)-?\\d|\\bR[1-9]\\d{1,2}\\b"
                    + "|\\b[A-Z]{2}-\\d{1,4}\\b|\\bM-\\d{3,4}\\b");
    private static final Pattern LOCAL_NAME = Pattern.compile(
            "\\b(?:CALLE|CARRER|CAMINO|CAMI|RUA|TRAVES[IÍ]A|PASEO|PLAZA|PISTA|SENDERO)\\b");

    /**
     * OSM {@code highway}: primary no es nacional (en Iberia suelen ser autonómicas).
     * Nacional solo con código N- / EN- / RN.
     */
    @NonNull
    public static RoadKind fromOsm(@Nullable Object raw) {
        String s = raw == null ? "" : String.valueOf(raw).trim().toUpperCase(Locale.US)
                .replace('-', '_');
        if (s.contains("MOTORWAY") || s.contains("TRUNK")) {
            return AUTOPISTA;
        }
        if (s.contains("PRIMARY") || s.contains("SECONDARY")) {
            return COMARCAL;
        }
        return OTRO;
    }

    /**
     * Nombre o ref OSM (N-340, AP-7, CV-35…). {@code null} si no se puede afirmar.
     */
    @Nullable
    public static RoadKind fromRef(@Nullable String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        String s = Normalizer.normalize(raw, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toUpperCase(Locale.ROOT);
        if (AUTOPISTA_REF.matcher(s).find()) {
            return AUTOPISTA;
        }
        if (NACIONAL_REF.matcher(s).find()) {
            return NACIONAL;
        }
        if (COMARCAL_REF.matcher(s).find()) {
            return COMARCAL;
        }
        if (LOCAL_NAME.matcher(s).find()) {
            return OTRO;
        }
        return null;
    }

    /**
     * Solo si no hay ref: la velocidad no distingue nacional, comarcal ni local (todas a ~90).
     */
    @NonNull
    public static RoadKind fromSpeedKmh(double kmh) {
        if (kmh >= 118) {
            return AUTOPISTA;
        }
        return OTRO;
    }

    @NonNull
    public static String fit(@Nullable String packed, int edgeCount) {
        if (edgeCount <= 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder(edgeCount);
        for (int i = 0; i < edgeCount; i++) {
            if (packed != null && i < packed.length()) {
                sb.append(ofCode(packed.charAt(i)).code);
            } else {
                sb.append(OTRO.code);
            }
        }
        return sb.toString();
    }

    @NonNull
    public static String fill(int edgeCount, @NonNull RoadKind kind) {
        if (edgeCount <= 0) {
            return "";
        }
        char[] c = new char[edgeCount];
        java.util.Arrays.fill(c, kind.code);
        return new String(c);
    }

    public static boolean typed(@Nullable String packed) {
        return packed != null && !packed.isEmpty();
    }
}
