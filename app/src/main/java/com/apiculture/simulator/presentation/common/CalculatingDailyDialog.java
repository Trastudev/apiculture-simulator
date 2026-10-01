package com.apiculture.simulator.presentation.common;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Window;

import androidx.annotation.Nullable;

import com.apiculture.simulator.R;

import java.lang.ref.WeakReference;

/**
 * Indicador no cancelable mientras corre el tick de producción diaria.
 */
public final class CalculatingDailyDialog {

    private static WeakReference<Dialog> showing = new WeakReference<>(null);

    private CalculatingDailyDialog() {
    }

    public static void show(@Nullable Context context) {
        Activity activity = resolveActivity(context);
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            return;
        }
        activity.runOnUiThread(() -> present(activity));
    }

    public static void dismiss() {
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
        dialog.setContentView(R.layout.dialog_calculating_daily);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        }
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
