package com.apiculture.simulator.presentation.common;

import android.app.Activity;
import android.app.Application;
import android.app.Dialog;
import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.Window;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.apiculture.simulator.R;
import com.google.android.material.button.MaterialButton;

import java.lang.ref.WeakReference;

/**
 * Avisos emergentes al estilo del apiario (crema/oro). Sustituye a {@link android.widget.Toast}.
 */
public final class GameNotice {

    private static WeakReference<Activity> resumedActivity = new WeakReference<>(null);
    private static WeakReference<Dialog> showing = new WeakReference<>(null);

    private GameNotice() {
    }

    public static void install(@NonNull Application app) {
        app.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
            }

            @Override
            public void onActivityStarted(@NonNull Activity activity) {
            }

            @Override
            public void onActivityResumed(@NonNull Activity activity) {
                resumedActivity = new WeakReference<>(activity);
            }

            @Override
            public void onActivityPaused(@NonNull Activity activity) {
                Activity current = resumedActivity.get();
                if (current == activity) {
                    resumedActivity = new WeakReference<>(null);
                }
            }

            @Override
            public void onActivityStopped(@NonNull Activity activity) {
            }

            @Override
            public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {
            }

            @Override
            public void onActivityDestroyed(@NonNull Activity activity) {
            }
        });
    }

    @Nullable
    public static Activity currentActivity() {
        Activity resumed = resumedActivity.get();
        if (resumed != null && !resumed.isFinishing() && !resumed.isDestroyed()) {
            return resumed;
        }
        return null;
    }

    public static void show(@Nullable Context context, @StringRes int messageRes) {
        if (context == null) {
            return;
        }
        show(context, context.getString(R.string.game_notice_title), context.getString(messageRes));
    }

    public static void show(@Nullable Context context, @Nullable CharSequence message) {
        if (context == null) {
            return;
        }
        show(context, context.getString(R.string.game_notice_title), message);
    }

    public static void show(@Nullable Context context, @StringRes int titleRes, @StringRes int messageRes) {
        if (context == null) {
            return;
        }
        show(context, context.getString(titleRes), context.getString(messageRes));
    }

    public static void show(@Nullable Context context, @StringRes int titleRes, @Nullable CharSequence message) {
        if (context == null) {
            return;
        }
        show(context, context.getString(titleRes), message);
    }

    public static void showSuccess(@Nullable Context context, @StringRes int messageRes) {
        if (context == null) {
            return;
        }
        show(context, context.getString(R.string.game_notice_success), context.getString(messageRes));
    }

    public static void showSuccess(@Nullable Context context, @Nullable CharSequence message) {
        if (context == null) {
            return;
        }
        show(context, context.getString(R.string.game_notice_success), message);
    }

    public static void show(@Nullable Context context, @Nullable CharSequence title,
            @Nullable CharSequence message) {
        Activity activity = resolveActivity(context);
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            return;
        }
        CharSequence body = message != null ? message : "";
        if (body.length() == 0) {
            return;
        }
        CharSequence heading = title != null && title.length() > 0
                ? title
                : activity.getString(R.string.game_notice_title);
        activity.runOnUiThread(() -> present(activity, heading, body, R.string.game_notice_ok, false, null));
    }

    public static void confirm(@Nullable Context context, @Nullable CharSequence title,
            @Nullable CharSequence message, @StringRes int positiveRes, @Nullable Runnable onConfirm) {
        confirm(context, title, message, positiveRes, R.string.game_notice_back, onConfirm, null);
    }

    public static void confirm(@Nullable Context context, @Nullable CharSequence title,
            @Nullable CharSequence message, @StringRes int positiveRes, @StringRes int negativeRes,
            @Nullable Runnable onConfirm, @Nullable Runnable onCancel) {
        Activity activity = resolveActivity(context);
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            return;
        }
        CharSequence body = message != null ? message : "";
        if (body.length() == 0) {
            return;
        }
        CharSequence heading = title != null && title.length() > 0
                ? title
                : activity.getString(R.string.game_notice_title);
        int ok = positiveRes != 0 ? positiveRes : R.string.game_notice_ok;
        int cancel = negativeRes != 0 ? negativeRes : R.string.game_notice_back;
        activity.runOnUiThread(() -> present(activity, heading, body, ok, cancel, true, onConfirm, onCancel));
    }

    private static void present(@NonNull Activity activity, @NonNull CharSequence title,
            @NonNull CharSequence message, @StringRes int okRes, boolean showCancel,
            @Nullable Runnable onOk) {
        present(activity, title, message, okRes, R.string.game_notice_back, showCancel, onOk, null);
    }

    private static void present(@NonNull Activity activity, @NonNull CharSequence title,
            @NonNull CharSequence message, @StringRes int okRes, @StringRes int cancelRes,
            boolean showCancel, @Nullable Runnable onOk, @Nullable Runnable onCancel) {
        if (activity.isFinishing() || activity.isDestroyed()) {
            return;
        }
        Dialog previous = showing.get();
        if (previous != null && previous.isShowing()) {
            previous.dismiss();
        }
        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_game_notice);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        TextView tvTitle = dialog.findViewById(R.id.tv_notice_title);
        TextView tvMessage = dialog.findViewById(R.id.tv_notice_message);
        MaterialButton btnOk = dialog.findViewById(R.id.btn_notice_ok);
        MaterialButton btnCancel = dialog.findViewById(R.id.btn_notice_cancel);
        tvTitle.setText(title);
        tvMessage.setText(message);
        btnOk.setText(okRes);
        btnOk.setOnClickListener(v -> {
            dialog.dismiss();
            if (onOk != null) {
                onOk.run();
            }
        });
        if (btnCancel != null) {
            btnCancel.setText(cancelRes);
            btnCancel.setVisibility(showCancel ? android.view.View.VISIBLE : android.view.View.GONE);
            btnCancel.setOnClickListener(v -> {
                dialog.dismiss();
                if (onCancel != null) {
                    onCancel.run();
                }
            });
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
        Activity resumed = resumedActivity.get();
        if (resumed != null && !resumed.isFinishing() && !resumed.isDestroyed()) {
            return resumed;
        }
        return null;
    }
}
