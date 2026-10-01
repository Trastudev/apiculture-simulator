package com.apiculture.simulator.presentation.common;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import com.apiculture.simulator.R;
import com.apiculture.simulator.domain.game.ClimateUnlock;
import com.apiculture.simulator.domain.game.IberianClimateZone;
import com.apiculture.simulator.presentation.hive.YardBackdropCleaner;
import com.apiculture.simulator.presentation.hive.YardClimate;
import com.apiculture.simulator.presentation.map.SharedMapFragment;

import java.util.ArrayList;
import java.util.List;

/**
 * Abeja con maleta viajando por los climas recién desbloqueados.
 */
public final class ClimateJourneyDialog {

    private ClimateJourneyDialog() {
    }

    public static void show(@NonNull Activity activity, @NonNull List<ClimateUnlock.Unlock> unlocks) {
        show(activity, unlocks, null);
    }

    public static void show(@NonNull Activity activity, @NonNull List<ClimateUnlock.Unlock> unlocks,
            @Nullable Runnable onComplete) {
        if (activity.isFinishing() || activity.isDestroyed() || unlocks == null || unlocks.isEmpty()) {
            if (onComplete != null) {
                onComplete.run();
            }
            return;
        }
        Dialog dialog = new Dialog(activity, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(true);
        dialog.setContentView(R.layout.dialog_climate_journey);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.BLACK));
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
        }
        ImageView backdrop = dialog.findViewById(R.id.iv_climate_backdrop);
        TextView kicker = dialog.findViewById(R.id.tv_climate_journey_kicker);
        TextView name = dialog.findViewById(R.id.tv_climate_journey_name);
        View flagWrap = dialog.findViewById(R.id.wrap_za_flag);
        View titleBlock = dialog.findViewById(R.id.block_climate_title);
        FrameLayout swarm = dialog.findViewById(R.id.layer_climate_travelers);
        boolean[] finished = {false};
        dialog.setOnDismissListener(d -> {
            refreshMapClimateGates(activity);
            if (!finished[0]) {
                finished[0] = true;
                if (onComplete != null) {
                    onComplete.run();
                }
            }
        });
        dialog.show();
        swarm.post(() -> playStep(dialog, unlocks, 0, backdrop, kicker, name, flagWrap, titleBlock, swarm));
    }

    private static void refreshMapClimateGates(@NonNull Activity activity) {
        if (!(activity instanceof FragmentActivity)) {
            return;
        }
        refreshMapClimateGates(((FragmentActivity) activity).getSupportFragmentManager().getFragments());
    }

    private static void refreshMapClimateGates(List<Fragment> fragments) {
        for (Fragment f : fragments) {
            if (f instanceof SharedMapFragment) {
                ((SharedMapFragment) f).onClimateUnlockChanged();
            }
            if (f.isAdded()) {
                refreshMapClimateGates(f.getChildFragmentManager().getFragments());
            }
        }
    }

    private static void playStep(
            Dialog dialog,
            List<ClimateUnlock.Unlock> unlocks,
            int index,
            ImageView backdrop,
            TextView kicker,
            TextView name,
            View flagWrap,
            View titleBlock,
            FrameLayout swarm) {
        if (!dialog.isShowing() || index >= unlocks.size()) {
            dialog.dismiss();
            return;
        }
        ClimateUnlock.Unlock u = unlocks.get(index);
        boolean zaWelcome = u.southAfricaRegion;
        backdrop.setImageBitmap(YardBackdropCleaner.decodeCleaned(backdrop.getResources(), backdropRes(u)));
        kicker.setText(zaWelcome ? R.string.climate_journey_za_kicker : R.string.climate_journey_kicker);
        if (zaWelcome) {
            name.setText(R.string.climate_journey_za_title);
        } else {
            name.setText(u.labelEs);
        }
        flagWrap.setVisibility(zaWelcome ? View.VISIBLE : View.GONE);
        backdrop.setAlpha(0f);
        titleBlock.setAlpha(0f);

        int beeCount = zaWelcome ? 6 : 1;
        ensureTravelers(swarm, beeCount);
        int w = backdrop.getWidth();
        if (w <= 0) {
            w = backdrop.getResources().getDisplayMetrics().widthPixels;
        }

        List<Animator> flights = new ArrayList<>();
        for (int i = 0; i < swarm.getChildCount(); i++) {
            View bee = swarm.getChildAt(i);
            boolean reverse = zaWelcome && (i % 2 == 1);
            bee.setScaleX(reverse ? -1f : 1f);
            bee.setAlpha(1f);
            float start = reverse ? -dp(bee, 110) : w + dp(bee, 48);
            float end = reverse ? w + dp(bee, 48) : -dp(bee, 110);
            bee.setTranslationX(start);
            float baseY = zaWelcome ? dp(bee, (i - 2.5f) * 36f) : 0f;
            bee.setTranslationY(baseY);
            ObjectAnimator fly = ObjectAnimator.ofFloat(bee, View.TRANSLATION_X, start, end);
            fly.setDuration(zaWelcome ? 2100 + i * 160L : 2300);
            fly.setStartDelay(zaWelcome ? i * 70L : 0L);
            fly.setInterpolator(new AccelerateDecelerateInterpolator());
            final int idx = i;
            fly.addUpdateListener(a -> {
                float t = a.getAnimatedFraction();
                bee.setTranslationY(baseY + (float) Math.sin(t * Math.PI * 3.0 + idx) * dp(bee, 16));
                bee.setRotation((float) Math.sin(t * Math.PI * 2.0) * (reverse ? -8f : 8f));
            });
            flights.add(fly);
        }

        ObjectAnimator fadeBg = ObjectAnimator.ofFloat(backdrop, View.ALPHA, 0f, 1f);
        ObjectAnimator fadeTitle = ObjectAnimator.ofFloat(titleBlock, View.ALPHA, 0f, 1f);
        fadeBg.setDuration(420);
        fadeTitle.setDuration(520);

        AnimatorSet flySet = new AnimatorSet();
        flySet.playTogether(flights);

        AnimatorSet set = new AnimatorSet();
        set.play(fadeBg).with(fadeTitle);
        set.play(flySet).after(fadeBg);
        set.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                ObjectAnimator outBg = ObjectAnimator.ofFloat(backdrop, View.ALPHA, 1f, 0f);
                ObjectAnimator outTitle = ObjectAnimator.ofFloat(titleBlock, View.ALPHA, 1f, 0f);
                outBg.setDuration(320);
                outTitle.setDuration(320);
                AnimatorSet fadeOut = new AnimatorSet();
                fadeOut.playTogether(outBg, outTitle);
                fadeOut.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        playStep(dialog, unlocks, index + 1, backdrop, kicker, name, flagWrap, titleBlock, swarm);
                    }
                });
                fadeOut.start();
            }
        });
        set.start();
    }

    private static void ensureTravelers(FrameLayout swarm, int count) {
        LayoutInflater inflater = LayoutInflater.from(swarm.getContext());
        while (swarm.getChildCount() < count) {
            inflater.inflate(R.layout.item_climate_traveler, swarm, true);
        }
        while (swarm.getChildCount() > count) {
            swarm.removeViewAt(swarm.getChildCount() - 1);
        }
        for (int i = 0; i < swarm.getChildCount(); i++) {
            View child = swarm.getChildAt(i);
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) child.getLayoutParams();
            lp.gravity = Gravity.CENTER_VERTICAL;
            child.setLayoutParams(lp);
        }
    }

    private static int backdropRes(ClimateUnlock.Unlock u) {
        if (u.southAfricaRegion) {
            return YardClimate.FYNBOS.backdropRes();
        }
        if (u.madagascar()) {
            return YardClimate.fromMdg(u.mdg).backdropRes();
        }
        if (u.southern()) {
            return YardClimate.fromZa(u.za).backdropRes();
        }
        IberianClimateZone z = u.iberia != null ? u.iberia : IberianClimateZone.CONTINENTAL;
        return YardClimate.fromIberia(z).backdropRes();
    }

    private static float dp(View v, float dps) {
        return dps * v.getResources().getDisplayMetrics().density;
    }
}
