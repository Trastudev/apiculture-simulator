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

import com.apiculture.simulator.ApicultureApp;
import com.apiculture.simulator.R;
import com.apiculture.simulator.data.repository.EconomyRepository;
import com.apiculture.simulator.data.repository.EventInventoryStore;
import com.apiculture.simulator.domain.game.LevelUpRewards;
import com.google.android.material.button.MaterialButton;

import java.lang.ref.WeakReference;

/**
 * Ventana de subida de nivel con recompensas (BeeCoins, tratamiento, apialimento).
 */
public final class LevelUpDialog {

    private static WeakReference<Activity> resumedActivity = new WeakReference<>(null);
    private static WeakReference<Dialog> showing = new WeakReference<>(null);

    private LevelUpDialog() {
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

    /**
     * Muestra el diálogo de recompensa. Si no hay Activity visible, otorga al momento.
     *
     * @param prevLevel nivel antes del salto
     * @param newLevel  nivel tras el salto
     */
    public static void show(@Nullable Context context, int prevLevel, int newLevel) {
        if (newLevel <= prevLevel) {
            return;
        }
        int coins = LevelUpRewards.totalCoins(prevLevel, newLevel);
        int treat = LevelUpRewards.totalTreatments(prevLevel, newLevel);
        int feed = LevelUpRewards.totalFeed(prevLevel, newLevel);
        Activity activity = resolveActivity(context);
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            grant(context, coins, treat, feed);
            return;
        }
        final Context appCtx = activity.getApplicationContext();
        final int levelsGained = newLevel - prevLevel;
        activity.runOnUiThread(() -> present(activity, appCtx, newLevel, levelsGained, coins, treat, feed));
    }

    private static void present(@NonNull Activity activity, @NonNull Context appCtx,
                                int newLevel, int levelsGained, int coins, int treat, int feed) {
        if (activity.isFinishing() || activity.isDestroyed()) {
            grant(appCtx, coins, treat, feed);
            return;
        }
        Dialog previous = showing.get();
        if (previous != null && previous.isShowing()) {
            previous.dismiss();
        }
        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(false);
        dialog.setContentView(R.layout.dialog_level_up);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        TextView tvTitle = dialog.findViewById(R.id.tv_level_up_title);
        TextView tvSubtitle = dialog.findViewById(R.id.tv_level_up_subtitle);
        TextView tvCoins = dialog.findViewById(R.id.tv_reward_coins);
        TextView tvTreat = dialog.findViewById(R.id.tv_reward_treat);
        TextView tvFeed = dialog.findViewById(R.id.tv_reward_feed);
        MaterialButton btnClaim = dialog.findViewById(R.id.btn_level_up_claim);

        tvTitle.setText(activity.getString(R.string.level_up_title, newLevel));
        if (levelsGained <= 1) {
            tvSubtitle.setText(R.string.level_up_subtitle_one);
        } else {
            tvSubtitle.setText(activity.getString(R.string.level_up_subtitle_many, levelsGained, newLevel));
        }
        tvCoins.setText(activity.getString(R.string.level_up_reward_coins, coins));
        tvTreat.setText(treat == 1
                ? activity.getString(R.string.level_up_reward_treat_one)
                : activity.getString(R.string.level_up_reward_treat_many, treat));
        tvFeed.setText(feed == 1
                ? activity.getString(R.string.level_up_reward_feed_one)
                : activity.getString(R.string.level_up_reward_feed_many, feed));

        final boolean[] claimed = {false};
        btnClaim.setOnClickListener(v -> {
            if (claimed[0]) {
                return;
            }
            claimed[0] = true;
            grant(appCtx, coins, treat, feed);
            dialog.dismiss();
        });
        dialog.setOnDismissListener(d -> {
            if (!claimed[0]) {
                grant(appCtx, coins, treat, feed);
                claimed[0] = true;
            }
            if (showing.get() == dialog) {
                showing = new WeakReference<>(null);
            }
        });
        showing = new WeakReference<>(dialog);
        dialog.show();
    }

    private static void grant(@Nullable Context context, int coins, int treat, int feed) {
        if (context == null) {
            return;
        }
        Context appCtx = context.getApplicationContext();
        try {
            ApicultureApp app = (ApicultureApp) appCtx;
            EconomyRepository economy = app.getEconomyRepository();
            if (economy != null && coins > 0) {
                economy.addToBalance(coins);
            }
        } catch (ClassCastException ignored) {
        }
        if (treat > 0) {
            EventInventoryStore.addTreatments(appCtx, treat);
        }
        if (feed > 0) {
            EventInventoryStore.addFeed(appCtx, feed);
        }
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
