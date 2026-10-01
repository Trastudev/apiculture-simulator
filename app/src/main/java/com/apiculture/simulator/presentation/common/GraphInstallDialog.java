package com.apiculture.simulator.presentation.common;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Window;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.apiculture.simulator.R;
import com.apiculture.simulator.data.repository.RoutingGraphDownloader;

import java.lang.ref.WeakReference;
import java.util.Locale;

/**
 * Diálogo no cancelable: el usuario espera a que el grafo se instale en la carpeta del juego.
 */
public final class GraphInstallDialog {

    private static WeakReference<Dialog> showing = new WeakReference<>(null);
    private static WeakReference<ProgressBar> barRef = new WeakReference<>(null);
    private static WeakReference<TextView> statusRef = new WeakReference<>(null);
    private static WeakReference<TextView> percentRef = new WeakReference<>(null);
    private static WeakReference<TextView> bytesRef = new WeakReference<>(null);

    private static final RoutingGraphDownloader.Listener LISTENER = new RoutingGraphDownloader.Listener() {
        @Override
        public void onProgress(long got, long total, boolean extracting) {
            bindProgress(got, total, extracting);
        }

        @Override
        public void onFinished(boolean ok) {
            Activity activity = activityOfShowing();
            dismiss();
            if (activity == null) {
                return;
            }
            if (ok) {
                GameNotice.showSuccess(activity, R.string.truck_live_install_done);
            } else {
                String detail = RoutingGraphDownloader.lastFailMessage();
                GameNotice.show(activity, detail != null && !detail.isEmpty()
                        ? detail
                        : activity.getString(R.string.truck_live_install_fail));
            }
        }
    };

    private GraphInstallDialog() {
    }

    public static void show(@Nullable Context context) {
        show(context, null);
    }

    public static void show(@Nullable Context context,
            @Nullable com.apiculture.simulator.domain.map.PlayableMapRegion region) {
        Activity activity = resolveActivity(context);
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            return;
        }
        RoutingGraphDownloader.addListener(LISTENER);
        activity.runOnUiThread(() -> present(activity, region));
    }

    public static void dismiss() {
        RoutingGraphDownloader.removeListener(LISTENER);
        Dialog d = showing.get();
        if (d != null && d.isShowing()) {
            try {
                d.dismiss();
            } catch (RuntimeException ignored) {
            }
        }
        showing = new WeakReference<>(null);
        barRef = new WeakReference<>(null);
        statusRef = new WeakReference<>(null);
        percentRef = new WeakReference<>(null);
        bytesRef = new WeakReference<>(null);
    }

    private static void bindProgress(long got, long total, boolean extracting) {
        Dialog d = showing.get();
        if (d == null || !d.isShowing()) {
            return;
        }
        ProgressBar bar = barRef.get();
        TextView status = statusRef.get();
        TextView percent = percentRef.get();
        TextView bytes = bytesRef.get();
        if (bar == null || status == null) {
            return;
        }
        Context ctx = d.getContext();
        if (extracting) {
            bar.setIndeterminate(true);
            status.setText(R.string.truck_live_install_extracting);
            if (percent != null) {
                percent.setText(R.string.truck_live_install_extracting_pct);
            }
            if (bytes != null) {
                bytes.setText("");
            }
            return;
        }
        boolean known = total > 0;
        bar.setIndeterminate(!known);
        status.setText(R.string.truck_live_install_wait);
        if (known) {
            int p = (int) Math.min(1000, got * 1000 / Math.max(1, total));
            bar.setMax(1000);
            bar.setProgress(p);
            if (percent != null) {
                percent.setText(ctx.getString(R.string.truck_live_install_percent,
                        (int) Math.min(100, got * 100 / Math.max(1, total))));
            }
            if (bytes != null) {
                bytes.setText(ctx.getString(R.string.truck_live_install_bytes,
                        mb(got), mb(total)));
            }
        } else if (percent != null) {
            percent.setText(R.string.truck_live_install_connecting);
            if (bytes != null) {
                bytes.setText(got > 0
                        ? ctx.getString(R.string.truck_live_install_bytes_unknown, mb(got))
                        : "");
            }
        }
    }

    private static String mb(long bytes) {
        return String.format(Locale.getDefault(), "%.0f", bytes / 1_000_000.0);
    }

    private static void present(@NonNull Activity activity,
            @Nullable com.apiculture.simulator.domain.map.PlayableMapRegion region) {
        if (activity.isFinishing() || activity.isDestroyed()) {
            return;
        }
        boolean ready = region != null
                ? RoutingGraphDownloader.isInstalled(activity, region)
                : RoutingGraphDownloader.isInstalled(activity);
        if (ready) {
            RoutingGraphDownloader.removeListener(LISTENER);
            GameNotice.showSuccess(activity, R.string.truck_live_install_done);
            return;
        }
        Dialog previous = showing.get();
        if (previous != null && previous.isShowing()) {
            RoutingGraphDownloader.Progress snap = RoutingGraphDownloader.latestProgress();
            if (snap != null) {
                bindProgress(snap.got, snap.total, snap.extracting);
            }
            return;
        }
        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(false);
        dialog.setCanceledOnTouchOutside(false);
        dialog.setContentView(R.layout.dialog_graph_install);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        showing = new WeakReference<>(dialog);
        barRef = new WeakReference<>(dialog.findViewById(R.id.pb_graph_install));
        statusRef = new WeakReference<>(dialog.findViewById(R.id.tv_graph_install_status));
        percentRef = new WeakReference<>(dialog.findViewById(R.id.tv_graph_install_percent));
        bytesRef = new WeakReference<>(dialog.findViewById(R.id.tv_graph_install_bytes));
        dialog.show();
        RoutingGraphDownloader.Progress snap = RoutingGraphDownloader.latestProgress();
        if (snap != null) {
            bindProgress(snap.got, snap.total, snap.extracting);
        }
    }

    @Nullable
    private static Activity activityOfShowing() {
        Dialog d = showing.get();
        if (d == null) {
            return null;
        }
        return resolveActivity(d.getContext());
    }

    @Nullable
    private static Activity resolveActivity(@Nullable Context context) {
        Context walk = context;
        while (walk instanceof ContextWrapper) {
            if (walk instanceof Activity) {
                Activity a = (Activity) walk;
                if (!a.isFinishing() && !a.isDestroyed()) {
                    return a;
                }
            }
            walk = ((ContextWrapper) walk).getBaseContext();
        }
        return null;
    }
}
