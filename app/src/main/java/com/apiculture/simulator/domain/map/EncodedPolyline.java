package com.apiculture.simulator.domain.map;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Polyline de Google/OSRM (precisión 1e-5). */
public final class EncodedPolyline {

    private EncodedPolyline() {
    }

    @NonNull
    public static String encode(@Nullable List<double[]> points) {
        if (points == null || points.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        int lastLat = 0;
        int lastLng = 0;
        for (double[] p : points) {
            if (p == null || p.length < 2) {
                continue;
            }
            int lat = (int) Math.round(p[0] * 1e5);
            int lng = (int) Math.round(p[1] * 1e5);
            encodeDelta(out, lat - lastLat);
            encodeDelta(out, lng - lastLng);
            lastLat = lat;
            lastLng = lng;
        }
        return out.toString();
    }

    @NonNull
    public static List<double[]> decode(@Nullable String encoded) {
        if (encoded == null || encoded.isEmpty()) {
            return Collections.emptyList();
        }
        List<double[]> out = new ArrayList<>();
        int index = 0;
        int lat = 0;
        int lng = 0;
        int len = encoded.length();
        while (index < len) {
            int[] latR = decodeDelta(encoded, index);
            index = latR[1];
            lat += latR[0];
            if (index >= len) {
                break;
            }
            int[] lngR = decodeDelta(encoded, index);
            index = lngR[1];
            lng += lngR[0];
            out.add(new double[]{lat / 1e5, lng / 1e5});
        }
        return out;
    }

    private static void encodeDelta(StringBuilder out, int value) {
        int v = value < 0 ? ~(value << 1) : (value << 1);
        while (v >= 0x20) {
            out.append((char) ((0x20 | (v & 0x1f)) + 63));
            v >>= 5;
        }
        out.append((char) (v + 63));
    }

    @NonNull
    private static int[] decodeDelta(@NonNull String encoded, int index) {
        int result = 0;
        int shift = 0;
        int b;
        do {
            b = encoded.charAt(index++) - 63;
            result |= (b & 0x1f) << shift;
            shift += 5;
        } while (b >= 0x20 && index < encoded.length());
        int delta = (result & 1) != 0 ? ~(result >> 1) : (result >> 1);
        return new int[]{delta, index};
    }
}
