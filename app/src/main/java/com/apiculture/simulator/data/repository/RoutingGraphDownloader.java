package com.apiculture.simulator.data.repository;

import android.app.NotificationManager;
import android.content.Context;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.apiculture.simulator.R;
import com.apiculture.simulator.domain.map.PlayableMapRegion;
import com.apiculture.simulator.notification.GameNotificationChannels;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Instala grafos de carreteras en {@code filesDir/routing-graph/<región>}.
 * El usuario no descomprime nada: la app baja el tar.gz, lo extrae y borra el archivo.
 */
public final class RoutingGraphDownloader {

    private static final String TAG = "RoutingGraph";
    public static final String RELEASE_URL = PlayableMapRegion.IBERIA.roadGraphReleaseUrl();
    public static final String FILE_NAME = PlayableMapRegion.IBERIA.roadGraphArchiveName();
    public static final String GRAPH_SUBDIR = PlayableMapRegion.IBERIA.roadGraphSubdir();
    private static final String EXTRACTED_MARK = ".extracted";
    private static final int CONNECT_MS = 20_000;
    private static final int READ_MS = 60_000;

    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final Object LOCK = new Object();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
    @Nullable
    private static volatile Progress latest;
    @Nullable
    private static volatile String lastFailMessage;

    public interface Listener {
        void onProgress(long got, long total, boolean extracting);

        void onFinished(boolean ok);
    }

    public static final class Progress {
        public final long got;
        public final long total;
        public final boolean extracting;
        @Nullable
        public final PlayableMapRegion region;

        Progress(long got, long total, boolean extracting, @Nullable PlayableMapRegion region) {
            this.got = got;
            this.total = total;
            this.extracting = extracting;
            this.region = region;
        }
    }

    public static void addListener(@NonNull Listener listener) {
        listeners.addIfAbsent(listener);
        Progress snap = latest;
        if (snap != null) {
            listener.onProgress(snap.got, snap.total, snap.extracting);
        }
    }

    public static void removeListener(@NonNull Listener listener) {
        listeners.remove(listener);
    }

    @Nullable
    public static Progress latestProgress() {
        return latest;
    }

    @Nullable
    public static String lastFailMessage() {
        return lastFailMessage;
    }

    /** Grafo de Iberia listo (el resto se baja al cambiar de mapa). */
    public static boolean isInstalled(@NonNull Context context) {
        return isGraphInstalled(context.getApplicationContext(), PlayableMapRegion.IBERIA);
    }

    public static boolean isInstalled(@NonNull Context context, @NonNull PlayableMapRegion region) {
        return isGraphInstalled(context.getApplicationContext(), region);
    }

    public static boolean allRequiredInstalled(@NonNull Context context) {
        Context app = context.getApplicationContext();
        for (PlayableMapRegion r : PlayableMapRegion.roadGraphRegions()) {
            if (!isGraphInstalled(app, r)) {
                return false;
            }
        }
        return true;
    }

    private RoutingGraphDownloader() {
    }

    @NonNull
    public static File graphDir(@NonNull Context context) {
        return graphDir(context, PlayableMapRegion.IBERIA);
    }

    @NonNull
    public static File graphDir(@NonNull Context context, @NonNull PlayableMapRegion region) {
        return new File(context.getApplicationContext().getFilesDir(), region.roadGraphSubdir());
    }

    @NonNull
    public static File archiveFile(@NonNull Context context) {
        return archiveFile(context, PlayableMapRegion.IBERIA);
    }

    @NonNull
    public static File archiveFile(@NonNull Context context, @NonNull PlayableMapRegion region) {
        return new File(context.getApplicationContext().getFilesDir(),
                "routing-graph/" + region.roadGraphArchiveName());
    }

    public static void enqueue(@NonNull Context context) {
        Context app = context.getApplicationContext();
        IO.execute(() -> installBlocking(app, null));
    }

    public static void enqueue(@NonNull Context context, @NonNull PlayableMapRegion region) {
        if (!region.hasRoadGraph()) {
            return;
        }
        Context app = context.getApplicationContext();
        IO.execute(() -> installBlocking(app, region));
    }

    public static void refreshStatus(@NonNull Context context) {
        Context app = context.getApplicationContext();
        if (TruckLivePrefs.isEnabled(app) && !isInstalled(app)) {
            IO.execute(() -> installBlocking(app, PlayableMapRegion.IBERIA));
            return;
        }
        TruckLivePrefs.setGraphReady(app, isInstalled(app));
    }

    @Nullable
    public static File ensureGraphDir(@NonNull Context context) {
        return ensureGraphDir(context, PlayableMapRegion.IBERIA);
    }

    @Nullable
    public static File ensureGraphDir(@NonNull Context context, @NonNull PlayableMapRegion region) {
        Context app = context.getApplicationContext();
        synchronized (LOCK) {
            if (isGraphInstalled(app, region)) {
                File dir = graphDir(app, region);
                copyCustomModel(app, dir);
                return dir;
            }
            return extractIfArchiveReady(app, region);
        }
    }

    private static void installBlocking(@NonNull Context app, @Nullable PlayableMapRegion only) {
        synchronized (LOCK) {
            lastFailMessage = null;
            PlayableMapRegion[] packs = only != null
                    ? new PlayableMapRegion[]{only}
                    : new PlayableMapRegion[]{PlayableMapRegion.IBERIA};
            boolean ok = true;
            boolean didWork = false;
            for (PlayableMapRegion region : packs) {
                if (!region.hasRoadGraph()) {
                    continue;
                }
                if (isGraphInstalled(app, region)) {
                    continue;
                }
                didWork = true;
                if (!installOne(app, region)) {
                    ok = false;
                    break;
                }
            }
            TruckLivePrefs.setGraphReady(app, isInstalled(app));
            if (!didWork) {
                publishFinished(true);
                return;
            }
            if (ok) {
                PlayableMapRegion ready = only != null ? only : PlayableMapRegion.IBERIA;
                com.apiculture.simulator.domain.map.LocalGraphHopper.prepareAsync(app, ready);
            }
            publishFinished(ok);
        }
    }

    private static boolean installOne(@NonNull Context app, @NonNull PlayableMapRegion region) {
        adoptLegacyArchive(app, region);
        File archive = archiveFile(app, region);
        if (!isCompleteArchive(archive)) {
            publishProgress(0, -1, false, region);
            if (!downloadToGameFolder(app, archive, region)) {
                cancelNotif(app, region);
                return false;
            }
        }
        publishProgress(Math.max(archive.length(), 1), Math.max(archive.length(), 1), true, region);
        File dir = extractIfArchiveReady(app, region);
        cancelNotif(app, region);
        if (dir != null && archive.isFile()) {
            //noinspection ResultOfMethodCallIgnored
            archive.delete();
        }
        return dir != null;
    }

    private static void publishProgress(long got, long total, boolean extracting,
            @Nullable PlayableMapRegion region) {
        latest = new Progress(got, total, extracting, region);
        MAIN.post(() -> {
            for (Listener l : listeners) {
                l.onProgress(got, total, extracting);
            }
        });
    }

    private static void publishFinished(boolean ok) {
        latest = ok ? null : latest;
        MAIN.post(() -> {
            for (Listener l : listeners) {
                l.onFinished(ok);
            }
        });
    }

    private static boolean isGraphInstalled(@NonNull Context app, @NonNull PlayableMapRegion region) {
        File dir = graphDir(app, region);
        File mark = new File(dir, EXTRACTED_MARK);
        return mark.isFile() && (new File(dir, "properties").isFile()
                || new File(dir, "properties.txt").isFile());
    }

    @Nullable
    private static File extractIfArchiveReady(@NonNull Context app, @NonNull PlayableMapRegion region) {
        if (isGraphInstalled(app, region)) {
            File dir = graphDir(app, region);
            copyCustomModel(app, dir);
            return dir;
        }
        File archive = archiveFile(app, region);
        adoptLegacyArchive(app, region);
        if (!isCompleteArchive(archive)) {
            return null;
        }
        File dir = graphDir(app, region);
        File tmp = new File(dir.getParentFile(), region.prefsValue() + ".tmp");
        try {
            deleteRecursive(tmp);
            TarGzipExtractor.extract(archive, tmp);
            deleteRecursive(dir);
            File parent = dir.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                return null;
            }
            if (!tmp.renameTo(dir)) {
                deleteRecursive(dir);
                if (!tmp.renameTo(dir)) {
                    return null;
                }
            }
            copyCustomModel(app, dir);
            try (FileOutputStream fos = new FileOutputStream(new File(dir, EXTRACTED_MARK))) {
                fos.write("v1".getBytes());
            }
            //noinspection ResultOfMethodCallIgnored
            archive.delete();
            File legacy = legacyArchive(app, region);
            if (legacy.isFile()) {
                //noinspection ResultOfMethodCallIgnored
                legacy.delete();
            }
            return dir;
        } catch (Exception e) {
            Log.e(TAG, "extract " + region.prefsValue(), e);
            lastFailMessage = app.getString(R.string.truck_live_install_extract_fail);
            deleteRecursive(tmp);
            return null;
        }
    }

    private static boolean isCompleteArchive(@Nullable File archive) {
        return archive != null && archive.isFile() && archive.length() > 200_000L;
    }

    private static void adoptLegacyArchive(@NonNull Context app, @NonNull PlayableMapRegion region) {
        File dest = archiveFile(app, region);
        if (isCompleteArchive(dest)) {
            return;
        }
        File legacy = legacyArchive(app, region);
        if (!isCompleteArchive(legacy)) {
            return;
        }
        File parent = dest.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            return;
        }
        if (legacy.renameTo(dest)) {
            return;
        }
        try (InputStream in = new FileInputStream(legacy);
             OutputStream out = new FileOutputStream(dest)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) >= 0) {
                out.write(buf, 0, n);
            }
        } catch (Exception e) {
            Log.w(TAG, "adopt legacy archive", e);
        }
    }

    @NonNull
    private static File legacyArchive(@NonNull Context app, @NonNull PlayableMapRegion region) {
        File dir = app.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (dir == null) {
            return new File(app.getFilesDir(), region.roadGraphArchiveName());
        }
        return new File(dir, region.roadGraphArchiveName());
    }

    private static boolean downloadToGameFolder(@NonNull Context app, @NonNull File dest,
            @NonNull PlayableMapRegion region) {
        File parent = dest.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            Log.e(TAG, "no se pudo crear " + parent);
            return false;
        }
        HttpURLConnection conn = null;
        try {
            URL url = new URL(region.roadGraphReleaseUrl());
            conn = open(url);
            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) {
                Log.e(TAG, "HTTP " + code + " " + region.prefsValue());
                lastFailMessage = httpFailMessage(app, region, code);
                return false;
            }
            long total = conn.getContentLengthLong();
            notifyProgress(app, region, 0, total > 0 ? total : -1);
            publishProgress(0, total > 0 ? total : -1, false, region);
            try (InputStream in = conn.getInputStream();
                 OutputStream out = new FileOutputStream(dest)) {
                byte[] buf = new byte[64 * 1024];
                long got = 0;
                int n;
                long lastUi = 0;
                while ((n = in.read(buf)) >= 0) {
                    out.write(buf, 0, n);
                    got += n;
                    if (got - lastUi > 400_000L) {
                        notifyProgress(app, region, got, total);
                        publishProgress(got, total, false, region);
                        lastUi = got;
                    }
                }
            }
            notifyProgress(app, region, dest.length(), dest.length());
            publishProgress(dest.length(), Math.max(dest.length(), total), false, region);
            return dest.length() > 200_000L;
        } catch (Exception e) {
            Log.e(TAG, "download " + region.prefsValue(), e);
            lastFailMessage = app.getString(R.string.truck_live_install_fail);
            return false;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    @NonNull
    private static HttpURLConnection open(@NonNull URL url) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setInstanceFollowRedirects(true);
        conn.setConnectTimeout(CONNECT_MS);
        conn.setReadTimeout(READ_MS);
        conn.setRequestProperty("User-Agent", "ApicultureSimulator/1.0 (Android)");
        conn.setRequestProperty("Accept", "*/*");
        int code = conn.getResponseCode();
        if (code == HttpURLConnection.HTTP_MOVED_PERM || code == HttpURLConnection.HTTP_MOVED_TEMP
                || code == HttpURLConnection.HTTP_SEE_OTHER || code == 307 || code == 308) {
            String loc = conn.getHeaderField("Location");
            conn.disconnect();
            if (loc == null || loc.isEmpty()) {
                throw new IllegalStateException("redirect sin Location");
            }
            return open(new URL(url, loc));
        }
        return conn;
    }

    private static void notifyProgress(@NonNull Context app, @NonNull PlayableMapRegion region,
            long got, long total) {
        GameNotificationChannels.ensureCreated(app);
        NotificationManager nm = (NotificationManager) app.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) {
            return;
        }
        boolean known = total > 0;
        int max = known ? 1000 : 0;
        int progress = known ? (int) Math.min(1000, got * 1000 / total) : 0;
        String title = app.getString(R.string.truck_live_download_title_region, regionLabel(app, region));
        NotificationCompat.Builder b = new NotificationCompat.Builder(app, GameNotificationChannels.ID_GRAPH)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle(title)
                .setContentText(app.getString(R.string.truck_live_download_desc))
                .setOnlyAlertOnce(true)
                .setOngoing(got < total || !known)
                .setProgress(max, progress, !known);
        nm.notify(region.roadGraphNotificationId(), b.build());
    }

    private static void cancelNotif(@NonNull Context app, @NonNull PlayableMapRegion region) {
        NotificationManager nm = (NotificationManager) app.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.cancel(region.roadGraphNotificationId());
        }
    }

    @NonNull
    private static String httpFailMessage(@NonNull Context app, @NonNull PlayableMapRegion region,
            int code) {
        if (code == HttpURLConnection.HTTP_NOT_FOUND) {
            return app.getString(R.string.truck_live_install_missing, regionLabel(app, region));
        }
        return app.getString(R.string.truck_live_install_http, regionLabel(app, region), code);
    }

    @NonNull
    private static String regionLabel(@NonNull Context app, @NonNull PlayableMapRegion region) {
        if (region == PlayableMapRegion.MADAGASCAR) {
            return app.getString(R.string.map_region_madagascar);
        }
        if (region == PlayableMapRegion.SOUTH_AFRICA) {
            return app.getString(R.string.map_region_south_africa);
        }
        return app.getString(R.string.map_region_iberia);
    }

    private static void copyCustomModel(@NonNull Context app, @NonNull File dir) {
        File dest = new File(dir, "truck_lite.json");
        if (dest.isFile() && dest.length() > 0) {
            return;
        }
        try (InputStream in = app.getAssets().open("truck_lite.json");
             OutputStream out = new FileOutputStream(dest)) {
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) >= 0) {
                out.write(buf, 0, n);
            }
        } catch (Exception e) {
            Log.w(TAG, "truck_lite.json", e);
        }
    }

    private static void deleteRecursive(@Nullable File file) {
        if (file == null || !file.exists()) {
            return;
        }
        File[] kids = file.listFiles();
        if (kids != null) {
            for (File kid : kids) {
                deleteRecursive(kid);
            }
        }
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }
}
