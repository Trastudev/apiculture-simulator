package com.apiculture.simulator.presentation.common;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;

import androidx.annotation.Nullable;

import com.apiculture.simulator.R;

import java.lang.ref.WeakReference;

/** Cubre la pantalla en gris mientras llegan el nombre y la foto del apicultor. */
public final class ProfileLoadingCover {

    private static WeakReference<View> showing = new WeakReference<>(null);

    private ProfileLoadingCover() {
    }

    public static void show(@Nullable Activity activity) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            return;
        }
        View current = showing.get();
        if (current != null && current.isAttachedToWindow()) {
            return;
        }
        ViewGroup content = activity.findViewById(android.R.id.content);
        if (content == null) {
            return;
        }
        View cover = LayoutInflater.from(activity).inflate(R.layout.view_profile_loading, content, false);
        ImageView art = cover.findViewById(R.id.iv_profile_loading);
        Animation spin = AnimationUtils.loadAnimation(activity, R.anim.spin_loading);
        art.startAnimation(spin);
        content.addView(cover);
        showing = new WeakReference<>(cover);
    }

    public static void hide() {
        View cover = showing.get();
        showing = new WeakReference<>(null);
        if (cover == null) {
            return;
        }
        ImageView art = cover.findViewById(R.id.iv_profile_loading);
        if (art != null) {
            art.clearAnimation();
        }
        ViewGroup parent = cover.getParent() instanceof ViewGroup ? (ViewGroup) cover.getParent() : null;
        if (parent != null) {
            parent.removeView(cover);
        }
    }
}
