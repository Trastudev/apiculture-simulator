package com.apiculture.simulator.data.repository;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.Nullable;

import com.apiculture.simulator.BuildConfig;
import com.apiculture.simulator.domain.map.PlayableMapRegion;
import com.apiculture.simulator.domain.parcel.BoundingBox;
import com.apiculture.simulator.domain.parcel.HexParcel;
import com.apiculture.simulator.domain.parcel.HexParcelGenerator;
import com.apiculture.simulator.domain.parcel.LandMask;
import com.apiculture.simulator.presentation.map.HexOverlayDiskCache;
import com.apiculture.simulator.presentation.map.MapHexOverlayConfig;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Malla hexagonal precalculada por región jugable (Iberia, Sudáfrica).
 * Se genera una sola vez (o se copia desde {@code assets/.../overlay.json}) y se guarda en filesDir.
 */
public final class IberiaHexOverlayStore {

    private static final String TAG = "HexOverlay";
    private static final int SCHEMA = 2;
    private static final int GENERATE_CAP = 32000;

    private static final Object LOCK = new Object();
    private static final Map<PlayableMapRegion, List<HexParcel>> memoryCache =
            new EnumMap<>(PlayableMapRegion.class);
    private static final ExecutorService GEN_EXEC = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "hex-overlay-gen");
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });
    private static volatile Handler mainHandler;

    private IberiaHexOverlayStore() {
    }

    public static boolean isLoaded(PlayableMapRegion region) {
        PlayableMapRegion r = region != null ? region : PlayableMapRegion.IBERIA;
        return memoryCache.get(r) != null;
    }

    /**
     * Carga la malla en un hilo propio (no el del mapa). {@code onDone} se ejecuta en el hilo principal.
     */
    public static void ensureLoadedAsync(
            Context appContext, PlayableMapRegion region, @Nullable Runnable onDone) {
        PlayableMapRegion r = region != null ? region : PlayableMapRegion.IBERIA;
        if (isLoaded(r)) {
            if (onDone != null) {
                onDone.run();
            }
            return;
        }
        Context app = appContext.getApplicationContext();
        GEN_EXEC.execute(() -> {
            getParcels(app, r);
            if (onDone != null) {
                mainHandler().post(onDone);
            }
        });
    }

    private static Handler mainHandler() {
        Handler h = mainHandler;
        if (h == null) {
            synchronized (LOCK) {
                h = mainHandler;
                if (h == null) {
                    h = new Handler(Looper.getMainLooper());
                    mainHandler = h;
                }
            }
        }
        return h;
    }

    public static List<HexParcel> getParcels(Context appContext) {
        return getParcels(appContext, PlayableMapRegion.IBERIA);
    }

    public static List<HexParcel> getParcels(Context appContext, PlayableMapRegion region) {
        PlayableMapRegion r = region != null ? region : PlayableMapRegion.IBERIA;
        List<HexParcel> c = memoryCache.get(r);
        if (c != null) {
            return c;
        }
        synchronized (LOCK) {
            List<HexParcel> again = memoryCache.get(r);
            if (again != null) {
                return again;
            }
            Context app = appContext.getApplicationContext();
            File f = storageFile(app, r);
            List<HexParcel> loaded = tryLoadDisk(app, f, r);
            if (loaded == null) {
                loaded = tryLoadBundledAssetAndPersist(app, f, r);
            }
            if (loaded == null) {
                loaded = generateAndSave(app, f, r);
            }
            memoryCache.put(r, loaded);
            return loaded;
        }
    }

    @Nullable
    public static HexParcel findById(Context appContext, String hexId) {
        if (hexId == null || hexId.isEmpty()) {
            return null;
        }
        PlayableMapRegion r = PlayableMapRegion.fromHexId(hexId);
        HexParcel found = findIn(getParcels(appContext, r), hexId);
        if (found != null || hexId.startsWith("za_") || hexId.startsWith("iberia_")) {
            return found;
        }
        PlayableMapRegion other = r == PlayableMapRegion.SOUTH_AFRICA
                ? PlayableMapRegion.IBERIA : PlayableMapRegion.SOUTH_AFRICA;
        return findIn(getParcels(appContext, other), hexId);
    }

    @Nullable
    public static HexParcel findContaining(Context appContext, double lat, double lon) {
        PlayableMapRegion r = PlayableMapRegion.containing(lat, lon);
        if (r == null) {
            return null;
        }
        return com.apiculture.simulator.domain.parcel.HexParcelResolve.findContaining(
                getParcels(appContext, r), lat, lon);
    }

    @Nullable
    private static HexParcel findIn(List<HexParcel> all, String hexId) {
        for (int i = 0; i < all.size(); i++) {
            HexParcel p = all.get(i);
            if (hexId.equals(p.id)) {
                return p;
            }
        }
        return null;
    }

    /**
     * Hex que cortan la vista, ordenados por distancia al punto de enfoque (centro de cámara / pantalla),
     * limitados a {@code maxHex}.
     */
    public static List<HexParcel> visibleInViewport(
            List<HexParcel> all,
            BoundingBox view,
            int maxHex,
            double focusLat,
            double focusLon
    ) {
        if (maxHex <= 0) {
            return Collections.emptyList();
        }
        List<HexParcel> cand = new ArrayList<>();
        for (HexParcel p : all) {
            if (parcelIntersectsView(p, view)) {
                cand.add(p);
            }
        }
        double cosLat = Math.cos(Math.toRadians(focusLat));
        Comparator<HexParcel> byDist = Comparator.comparingDouble(p -> {
            double dLat = p.centroidLat - focusLat;
            double dLon = (p.centroidLon - focusLon) * cosLat;
            return dLat * dLat + dLon * dLon;
        });
        cand.sort(byDist);
        if (cand.size() <= maxHex) {
            return cand;
        }
        return new ArrayList<>(cand.subList(0, maxHex));
    }

    private static boolean parcelIntersectsView(HexParcel p, BoundingBox view) {
        double minLat = Double.POSITIVE_INFINITY;
        double maxLat = Double.NEGATIVE_INFINITY;
        double minLon = Double.POSITIVE_INFINITY;
        double maxLon = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < 6; i++) {
            double la = p.polygonLatLon[i][0];
            double lo = p.polygonLatLon[i][1];
            minLat = Math.min(minLat, la);
            maxLat = Math.max(maxLat, la);
            minLon = Math.min(minLon, lo);
            maxLon = Math.max(maxLon, lo);
        }
        return !(maxLat < view.minLat || minLat > view.maxLat
                || maxLon < view.minLon || minLon > view.maxLon);
    }

    private static String datasetKey(Context app, PlayableMapRegion region) {
        return overlayDatasetKey(region, BuildConfig.VERSION_CODE);
    }

    /**
     * Misma clave que se guarda en el JSON; {@code versionCode} debe coincidir con {@link BuildConfig#VERSION_CODE}
     * del APK que consumirá el overlay.
     */
    public static String overlayDatasetKey(PlayableMapRegion region, int versionCode) {
        PlayableMapRegion r = region != null ? region : PlayableMapRegion.IBERIA;
        return r.hexPrefix() + "-grid-v2|" + HexOverlayDiskCache.configFingerprint(
                LandMaskAssets.DEFAULT_LAND_GEOJSON_ASSET,
                r.hexTargetAreaKm2(),
                MapHexOverlayConfig.MAP_HEX_LAND_SAMPLES_PER_AXIS,
                MapHexOverlayConfig.MAP_HEX_MIN_LAND_FRACTION,
                r.gridAnchorLat(),
                r.gridAnchorLon(),
                versionCode);
    }

    public static String iberiaOverlayDatasetKey(int versionCode) {
        return overlayDatasetKey(PlayableMapRegion.IBERIA, versionCode);
    }

    public static JSONObject toOverlayJsonDocument(List<HexParcel> parcels, int versionCode)
            throws org.json.JSONException {
        return toOverlayJsonDocument(parcels, PlayableMapRegion.IBERIA, versionCode);
    }

    public static JSONObject toOverlayJsonDocument(
            List<HexParcel> parcels, PlayableMapRegion region, int versionCode)
            throws org.json.JSONException {
        return buildJson(parcels, overlayDatasetKey(region, versionCode));
    }

    private static String jsonFileName(String datasetKey) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(datasetKey.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(28);
            for (int i = 0; i < 12; i++) {
                sb.append(String.format(Locale.US, "%02x", digest[i]));
            }
            return sb.append(".json").toString();
        } catch (Exception e) {
            return Integer.toHexString(datasetKey.hashCode()) + ".json";
        }
    }

    private static File storageFile(Context app, PlayableMapRegion region) {
        PlayableMapRegion r = region != null ? region : PlayableMapRegion.IBERIA;
        File dir = new File(app.getFilesDir(), r.overlaySubdir());
        if (!dir.isDirectory()) {
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
        }
        return new File(dir, jsonFileName(datasetKey(app, r)));
    }

    @Nullable
    private static List<HexParcel> tryLoadDisk(Context app, File f, PlayableMapRegion region) {
        if (!f.isFile()) {
            return null;
        }
        try {
            JSONObject root = new JSONObject(new String(readAllBytes(f), StandardCharsets.UTF_8));
            return parseParcelsJson(root, datasetKey(app, region));
        } catch (Exception e) {
            return null;
        }
    }

    @Nullable
    private static List<HexParcel> tryLoadBundledAssetAndPersist(
            Context app, File dest, PlayableMapRegion region) {
        PlayableMapRegion r = region != null ? region : PlayableMapRegion.IBERIA;
        try (InputStream in = app.getAssets().open(r.overlayAssetPath())) {
            byte[] raw = readAllBytes(in);
            JSONObject root = new JSONObject(new String(raw, StandardCharsets.UTF_8));
            List<HexParcel> list = parseParcelsJson(root, datasetKey(app, r));
            if (list != null) {
                writeBytesAtomically(dest, raw);
                return list;
            }
            Log.w(TAG, r.hexPrefix() + " bundled overlay.json key mismatch; expected "
                    + datasetKey(app, r));
        } catch (Exception ignored) {
        }
        return null;
    }

    private static List<HexParcel> generateAndSave(Context app, File dest, PlayableMapRegion region) {
        PlayableMapRegion r = region != null ? region : PlayableMapRegion.IBERIA;
        long t0 = SystemClock.elapsedRealtime();
        LandMask land = LandMaskAssets.getOrLoadDefaultLandMask(app);
        HexParcelGenerator generator = new HexParcelGenerator(
                land,
                r.hexTargetAreaKm2(),
                MapHexOverlayConfig.MAP_HEX_LAND_SAMPLES_PER_AXIS,
                HexParcelGenerator.DEFAULT_LAND_FRACTION_INLAND,
                MapHexOverlayConfig.MAP_HEX_MIN_LAND_FRACTION,
                r.gridAnchorLat(),
                r.gridAnchorLon());
        List<HexParcel> list = generator.generate(r.box(), r.hexPrefix(), GENERATE_CAP);
        Log.i(TAG, r.hexPrefix() + " generate " + list.size() + " hex in "
                + (SystemClock.elapsedRealtime() - t0) + " ms");
        final String key = datasetKey(app, r);
        GEN_EXEC.execute(() -> {
            long tPersist = SystemClock.elapsedRealtime();
            try {
                JSONObject root = buildJson(list, key);
                writeBytesAtomically(dest, root.toString().getBytes(StandardCharsets.UTF_8));
                Log.i(TAG, r.hexPrefix() + " persist in "
                        + (SystemClock.elapsedRealtime() - tPersist) + " ms");
            } catch (Exception e) {
                Log.w(TAG, r.hexPrefix() + " persist failed", e);
            }
        });
        return list;
    }

    private static JSONObject buildJson(List<HexParcel> parcels, String key) throws org.json.JSONException {
        JSONArray arr = new JSONArray();
        for (HexParcel p : parcels) {
            JSONObject o = new JSONObject();
            o.put("id", p.id);
            o.put("co", p.coastal);
            o.put("a", p.areaKm2);
            o.put("clat", p.centroidLat);
            o.put("clon", p.centroidLon);
            JSONArray ring = new JSONArray();
            for (int i = 0; i < 6; i++) {
                JSONArray pt = new JSONArray();
                pt.put(p.polygonLatLon[i][0]);
                pt.put(p.polygonLatLon[i][1]);
                ring.put(pt);
            }
            o.put("ring", ring);
            if (p.maxElevationMeters != null) {
                o.put("elev", p.maxElevationMeters);
            }
            arr.put(o);
        }
        JSONObject root = new JSONObject();
        root.put("schema", SCHEMA);
        root.put("key", key);
        root.put("savedAt", System.currentTimeMillis());
        root.put("parcels", arr);
        return root;
    }

    /**
     * Parsea «parcels» con schema actual, sin validar «key». Para injerto de elevación offline / tests.
     */
    public static List<HexParcel> parseParcelsWithoutKeyCheck(JSONObject root) throws JSONException {
        if (root.optInt("schema", 0) != SCHEMA) {
            throw new JSONException("schema must be " + SCHEMA);
        }
        return parseParcelsFromJsonArray(root.getJSONArray("parcels"));
    }

    private static List<HexParcel> parseParcelsFromJsonArray(JSONArray arr) throws JSONException {
        List<HexParcel> out = new ArrayList<>(arr.length());
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.getJSONObject(i);
            String id = o.getString("id");
            boolean co = o.getBoolean("co");
            double a = o.getDouble("a");
            double clat = o.getDouble("clat");
            double clon = o.getDouble("clon");
            JSONArray ring = o.getJSONArray("ring");
            double[][] poly = new double[6][2];
            for (int k = 0; k < 6; k++) {
                JSONArray pt = ring.getJSONArray(k);
                poly[k][0] = pt.getDouble(0);
                poly[k][1] = pt.getDouble(1);
            }
            Integer elevM = null;
            if (o.has("elev") && !o.isNull("elev")) {
                elevM = (int) Math.round(o.getDouble("elev"));
            }
            out.add(new HexParcel(id, poly, clat, clon, a, co, elevM));
        }
        return out;
    }

    @Nullable
    private static List<HexParcel> parseParcelsJson(JSONObject root, String expectedKey) {
        try {
            if (root.optInt("schema", 0) != SCHEMA) {
                return null;
            }
            if (!expectedKey.equals(root.getString("key"))) {
                return null;
            }
            return parseParcelsFromJsonArray(root.getJSONArray("parcels"));
        } catch (Exception e) {
            return null;
        }
    }

    private static void writeBytesAtomically(File dest, byte[] utf8) throws java.io.IOException {
        File tmp = new File(dest.getParentFile(), dest.getName() + ".tmp");
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
    }

    private static byte[] readAllBytes(File f) throws java.io.IOException {
        try (FileInputStream in = new FileInputStream(f);
             ByteArrayOutputStream buf = new ByteArrayOutputStream(
                     Math.min(1 << 22, (int) Math.min(f.length(), Integer.MAX_VALUE)))) {
            byte[] b = new byte[8192];
            int n;
            while ((n = in.read(b)) != -1) {
                buf.write(b, 0, n);
            }
            return buf.toByteArray();
        }
    }

    private static byte[] readAllBytes(InputStream in) throws java.io.IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] b = new byte[8192];
        int n;
        while ((n = in.read(b)) != -1) {
            buf.write(b, 0, n);
        }
        return buf.toByteArray();
    }
}
