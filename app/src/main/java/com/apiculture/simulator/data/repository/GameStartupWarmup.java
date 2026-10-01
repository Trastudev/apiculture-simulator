package com.apiculture.simulator.data.repository;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.apiculture.simulator.ApicultureApp;
import com.apiculture.simulator.R;
import com.apiculture.simulator.domain.game.GameBalanceConfig;
import com.apiculture.simulator.domain.map.PlayableMapRegion;
import com.apiculture.simulator.domain.map.ProvincialMarketCatalog;
import com.apiculture.simulator.domain.parcel.HexParcel;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Precarga en segundo plano lo que luego usan mapa, mercado y apiarios.
 */
public final class GameStartupWarmup {

    public interface Listener {
        @MainThread
        void onProgress(int done, int total, @NonNull String status);

        @MainThread
        void onReady();
    }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final CopyOnWriteArrayList<Listener> LISTENERS = new CopyOnWriteArrayList<>();
    private static final Object LOCK = new Object();

    private static volatile boolean started;
    private static volatile boolean ready;
    private static volatile int done;
    private static volatile int total = 1;
    private static volatile String status = "";

    private GameStartupWarmup() {
    }

    public static boolean isReady() {
        return ready;
    }

    public static void start(@NonNull ApicultureApp app) {
        synchronized (LOCK) {
            if (started) {
                return;
            }
            started = true;
        }
        new Thread(() -> run(app), "game-startup-warmup").start();
    }

    public static void addListener(@Nullable Listener listener) {
        if (listener == null) {
            return;
        }
        LISTENERS.add(listener);
        MAIN.post(() -> {
            if (ready) {
                listener.onReady();
            } else {
                listener.onProgress(done, Math.max(1, total), status != null ? status : "");
            }
        });
    }

    public static void removeListener(@Nullable Listener listener) {
        if (listener != null) {
            LISTENERS.remove(listener);
        }
    }

    private static void run(@NonNull ApicultureApp app) {
        String[] labels = {
                app.getString(R.string.game_loading_status_balance),
                app.getString(R.string.game_loading_status_seeds),
                app.getString(R.string.game_loading_status_map),
                app.getString(R.string.game_loading_status_other_maps),
                app.getString(R.string.game_loading_status_markets),
                app.getString(R.string.game_loading_status_orders),
                app.getString(R.string.game_loading_status_routes),
        };
        total = labels.length;
        PlayableMapRegion first = MapRegionPrefs.get(app);
        stepSafe(0, labels[0], () -> GameBalanceConfig.load(app));
        stepSafe(1, labels[1], () -> HexOverlaySeedInstaller.installFromAssets(app));
        stepSafe(2, labels[2], () -> loadRegion(app, first));
        stepSafe(3, labels[3], () -> {
            for (PlayableMapRegion r : PlayableMapRegion.values()) {
                if (r != first) {
                    loadRegion(app, r);
                }
            }
        });
        stepSafe(4, labels[4], () -> {
            for (PlayableMapRegion r : PlayableMapRegion.values()) {
                ProvincialMarketCatalog.resolve(app, r);
            }
        });
        stepSafe(5, labels[5], () -> HoneyOrderStore.preloadAroundIberiaHeadquarters(app));
        stepSafe(6, labels[6], () -> {
            if (TruckLivePrefs.isEnabled(app)) {
                RoutingGraphDownloader.refreshStatus(app);
            }
            GameServer.syncBlocking(app);
            app.getHexParcelRepository().syncOwnershipWithGameServerBlocking();
        });
        ready = true;
        done = total;
        MAIN.post(() -> {
            for (Listener l : LISTENERS) {
                l.onReady();
            }
        });
    }

    private static void stepSafe(int index, @NonNull String label, @NonNull Runnable work) {
        done = index;
        status = label;
        MAIN.post(() -> {
            for (Listener l : LISTENERS) {
                l.onProgress(index, total, label);
            }
        });
        try {
            work.run();
        } catch (RuntimeException ignored) {
        }
        done = index + 1;
    }

    private static void loadRegion(@NonNull ApicultureApp app, @NonNull PlayableMapRegion region) {
        List<HexParcel> parcels = IberiaHexOverlayStore.getParcels(app, region);
        app.getHexFloraRepository().seedAllParcelsBlocking(parcels);
        if (parcels != null && !parcels.isEmpty()) {
            IberiaHexOverlayStore.visibleInViewport(
                    parcels, region.box(), 450, region.defaultLookLat(), region.defaultLookLon());
            IberiaHexOverlayStore.findContaining(app, region.defaultLookLat(), region.defaultLookLon());
        }
    }
}
