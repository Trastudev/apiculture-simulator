package com.apiculture.simulator.presentation.map;

import android.content.Context;

import androidx.annotation.Nullable;

import com.apiculture.simulator.domain.parcel.BoundingBox;
import com.apiculture.simulator.domain.parcel.HexParcel;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.PolygonOptions;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Caché persistente bajo {@link Context#getFilesDir()} del overlay de hexágonos por viewport (sin Firestore).
 * No usa {@link Context#getCacheDir()} para que no desaparezca al limpiar caché del sistema.
 * Invalida al cambiar la versión de la app ({@code versionCode} en la huella), la configuración
 * que entra en {@link #configFingerprint}, o tras {@link #MAX_AGE_MS}.
 */
public final class HexOverlayDiskCache {

    private static final int SCHEMA = 1;
    private static final long MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000;
    private static final double QUANT_STEP_DEG = 0.025;

    private HexOverlayDiskCache() {
    }

    /**
     * @param appVersionCode {@link android.content.pm.PackageInfo#versionCode} (p. ej. {@code BuildConfig.VERSION_CODE})
     */
    public static String configFingerprint(
            String landAssetPath,
            double targetAreaKm2,
            int landSamplesPerAxis,
            double landFracMin,
            double anchorLat,
            double anchorLon,
            int appVersionCode
    ) {
        return String.format(Locale.US,
                "vc=%d|%s|a~%.4f|s=%d|f~%.4f|al~%.6f|ao~%.6f",
                appVersionCode,
                landAssetPath, targetAreaKm2, landSamplesPerAxis, landFracMin, anchorLat, anchorLon);
    }

    public static BoundingBox quantizeBounds(BoundingBox b) {
        return new BoundingBox(
                quant(b.minLat),
                quant(b.maxLat),
                quant(b.minLon),
                quant(b.maxLon));
    }

    private static double quant(double deg) {
        return Math.round(deg / QUANT_STEP_DEG) * QUANT_STEP_DEG;
    }

    private static float quantizeZoom(float zoom) {
        return Math.round(zoom * 8f) / 8f;
    }

    /** Directorio de entradas de caché (también destino de {@code assets/hex_overlay_seed/}). */
    public static File getStorageDir(Context appContext) {
        File d = new File(appContext.getFilesDir(), "hex_overlay");
        if (!d.isDirectory()) {
            //noinspection ResultOfMethodCallIgnored
            d.mkdirs();
        }
        return d;
    }

    private static String fileNameForKey(String key) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(key.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(24);
            for (int i = 0; i < 12; i++) {
                sb.append(String.format(Locale.US, "%02x", digest[i]));
            }
            return sb.append(".json").toString();
        } catch (Exception e) {
            return Integer.toHexString(key.hashCode()) + ".json";
        }
    }

    private static String buildLookupKey(String fingerprint, BoundingBox qb, float zoom, int maxHex) {
        return SCHEMA + "|" + fingerprint + "|z=" + String.format(Locale.US, "%.3f", quantizeZoom(zoom))
                + "|m=" + maxHex + "|b=" + String.format(Locale.US, "%.5f,%.5f,%.5f,%.5f",
                qb.minLat, qb.maxLat, qb.minLon, qb.maxLon);
    }

    @Nullable
    public static List<PolygonOptions> tryLoad(
            Context appContext,
            String fingerprint,
            BoundingBox clipped,
            float zoom,
            int maxHex,
            int fillColor,
            int strokeColor,
            float strokeWidth
    ) {
        BoundingBox qb = quantizeBounds(clipped);
        String lookupKey = buildLookupKey(fingerprint, qb, zoom, maxHex);
        File f = new File(getStorageDir(appContext), fileNameForKey(lookupKey));
        if (!f.isFile()) {
            return null;
        }
        try {
            byte[] raw = readAllBytes(f);
            JSONObject root = new JSONObject(new String(raw, StandardCharsets.UTF_8));
            if (root.optInt("schema", 0) != SCHEMA) {
                return null;
            }
            long savedAt = root.optLong("savedAt", 0);
            if (savedAt <= 0 || System.currentTimeMillis() - savedAt > MAX_AGE_MS) {
                return null;
            }
            if (!lookupKey.equals(root.optString("key", ""))) {
                return null;
            }
            JSONArray hexes = root.getJSONArray("hexes");
            List<PolygonOptions> out = new ArrayList<>(hexes.length());
            for (int h = 0; h < hexes.length(); h++) {
                JSONArray ring = hexes.getJSONArray(h);
                if (ring.length() != 6) {
                    return null;
                }
                LatLng[] path = new LatLng[7];
                for (int i = 0; i < 6; i++) {
                    JSONArray pt = ring.getJSONArray(i);
                    path[i] = new LatLng(pt.getDouble(0), pt.getDouble(1));
                }
                path[6] = path[0];
                out.add(new PolygonOptions()
                        .add(path)
                        .strokeWidth(strokeWidth)
                        .strokeColor(strokeColor)
                        .fillColor(fillColor)
                        .clickable(false));
            }
            return out;
        } catch (Exception e) {
            return null;
        }
    }

    public static void save(
            Context appContext,
            String fingerprint,
            BoundingBox clipped,
            float zoom,
            int maxHex,
            List<HexParcel> parcels
    ) {
        BoundingBox qb = quantizeBounds(clipped);
        String lookupKey = buildLookupKey(fingerprint, qb, zoom, maxHex);
        File dir = getStorageDir(appContext);
        File tmp = new File(dir, fileNameForKey(lookupKey) + ".tmp");
        File dest = new File(dir, fileNameForKey(lookupKey));
        try {
            JSONArray hexes = new JSONArray();
            for (HexParcel p : parcels) {
                JSONArray ring = new JSONArray();
                for (int i = 0; i < 6; i++) {
                    JSONArray pt = new JSONArray();
                    pt.put(p.polygonLatLon[i][0]);
                    pt.put(p.polygonLatLon[i][1]);
                    ring.put(pt);
                }
                hexes.put(ring);
            }
            JSONObject root = new JSONObject();
            root.put("schema", SCHEMA);
            root.put("savedAt", System.currentTimeMillis());
            root.put("key", lookupKey);
            root.put("hexes", hexes);
            byte[] utf8 = root.toString().getBytes(StandardCharsets.UTF_8);
            try (FileOutputStream fos = new FileOutputStream(tmp)) {
                fos.write(utf8);
                fos.flush();
                fos.getFD().sync();
            }
            if (!tmp.renameTo(dest)) {
                //noinspection ResultOfMethodCallIgnored
                dest.delete();
                //noinspection ResultOfMethodCallIgnored
                tmp.renameTo(dest);
            }
        } catch (Exception e) {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
        }
    }

    private static byte[] readAllBytes(File f) throws java.io.IOException {
        try (FileInputStream in = new FileInputStream(f);
             ByteArrayOutputStream buf = new ByteArrayOutputStream(Math.min(1 << 20, (int) f.length()))) {
            byte[] b = new byte[8192];
            int n;
            while ((n = in.read(b)) != -1) {
                buf.write(b, 0, n);
            }
            return buf.toByteArray();
        }
    }
}
