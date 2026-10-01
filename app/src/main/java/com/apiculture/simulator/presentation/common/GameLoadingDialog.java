package com.apiculture.simulator.presentation.common;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Window;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.apiculture.simulator.R;
import com.apiculture.simulator.data.repository.GameStartupWarmup;

import java.lang.ref.WeakReference;

/**
 * Diálogo no cancelable mientras se precargan mapas y catálogos al entrar a la partida.
 */
public final class GameLoadingDialog {

    private static final long TIP_MS = 4500L;
    private static final Handler TIPS = new Handler(Looper.getMainLooper());

    private static WeakReference<Dialog> showing = new WeakReference<>(null);
    private static int tipIndex;
    @Nullable
    private static GameStartupWarmup.Listener boundListener;

    private GameLoadingDialog() {
    }

    public static void show(@Nullable Context context) {
        Activity activity = resolveActivity(context);
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            return;
        }
        activity.runOnUiThread(() -> present(activity));
    }

    public static void dismiss() {
        TIPS.removeCallbacksAndMessages(null);
        if (boundListener != null) {
            GameStartupWarmup.removeListener(boundListener);
            boundListener = null;
        }
        Dialog d = showing.get();
        if (d != null && d.isShowing()) {
            try {
                d.dismiss();
            } catch (RuntimeException ignored) {
            }
        }
        showing = new WeakReference<>(null);
    }

    private static void present(Activity activity) {
        if (activity.isFinishing() || activity.isDestroyed()) {
            return;
        }
        Dialog previous = showing.get();
        if (previous != null && previous.isShowing()) {
            return;
        }
        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(false);
        dialog.setCanceledOnTouchOutside(false);
        dialog.setContentView(R.layout.dialog_game_loading);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(0xCC1C140C));
            window.setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT);
            window.setDimAmount(0.72f);
        }
        ProgressBar bar = dialog.findViewById(R.id.pb_game_loading);
        TextView status = dialog.findViewById(R.id.tv_game_loading_status);
        TextView tip = dialog.findViewById(R.id.tv_game_loading_tip);
        String[] tips = activity.getResources().getStringArray(R.array.game_loading_tips);
        tipIndex = (int) (System.currentTimeMillis() % Math.max(1, tips.length));
        if (tip != null && tips.length > 0) {
            tip.setText(tips[tipIndex]);
        }
        Runnable rotate = new Runnable() {
            @Override
            public void run() {
                if (tip == null || tips.length == 0) {
                    return;
                }
                tipIndex = (tipIndex + 1) % tips.length;
                tip.setText(tips[tipIndex]);
                TIPS.postDelayed(this, TIP_MS);
            }
        };
        TIPS.postDelayed(rotate, TIP_MS);

        boundListener = new GameStartupWarmup.Listener() {
            @Override
            public void onProgress(int done, int total, @androidx.annotation.NonNull String message) {
                if (bar != null) {
                    bar.setMax(Math.max(1, total));
                    bar.setProgress(done);
                }
                if (status != null && !message.isEmpty()) {
                    status.setText(message);
                }
            }

            @Override
            public void onReady() {
                if (bar != null) {
                    bar.setProgress(bar.getMax());
                }
            }
        };
        GameStartupWarmup.addListener(boundListener);
        showing = new WeakReference<>(dialog);
        dialog.show();
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
