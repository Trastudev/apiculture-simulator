package com.apiculture.simulator.presentation.map;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewParent;

import com.apiculture.simulator.data.repository.MapOverlayPrefs;
import com.google.android.material.card.MaterialCardView;

/**
 * Tarjeta HUD que se arrastra manteniendo pulsado y moviendo el dedo.
 * Un toque corto sigue llegando a botones y al selector de flora.
 */
public class DraggableMapHudCard extends MaterialCardView {

    private static final float REST_ELEVATION_DP = 6f;
    private static final float DRAG_ELEVATION_DP = 12f;

    private float downRawX;
    private float downRawY;
    private float startTransX;
    private float startTransY;
    private boolean dragging;
    private int touchSlop;
    private float restElevation;

    public DraggableMapHudCard(Context context) {
        super(context);
        init();
    }

    public DraggableMapHudCard(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public DraggableMapHudCard(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setClickable(true);
        touchSlop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
        restElevation = REST_ELEVATION_DP * getResources().getDisplayMetrics().density;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        post(this::restoreSavedOffset);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (!dragging && (oldw != 0 || oldh != 0)) {
            post(this::clampCurrentTranslation);
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downRawX = ev.getRawX();
                downRawY = ev.getRawY();
                startTransX = getTranslationX();
                startTransY = getTranslationY();
                dragging = false;
                return false;
            case MotionEvent.ACTION_MOVE:
                if (movedPastSlop(ev)) {
                    beginDrag();
                    return true;
                }
                return false;
            default:
                return false;
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downRawX = event.getRawX();
                downRawY = event.getRawY();
                startTransX = getTranslationX();
                startTransY = getTranslationY();
                dragging = false;
                return true;
            case MotionEvent.ACTION_MOVE:
                if (!dragging && movedPastSlop(event)) {
                    beginDrag();
                }
                if (dragging) {
                    applyClampedTranslation(
                            startTransX + (event.getRawX() - downRawX),
                            startTransY + (event.getRawY() - downRawY));
                    return true;
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (dragging) {
                    dragging = false;
                    setCardElevation(restElevation);
                    MapOverlayPrefs.setHudOffset(getContext(), getTranslationX(), getTranslationY());
                    return true;
                }
                break;
            default:
                break;
        }
        return super.onTouchEvent(event);
    }

    private boolean movedPastSlop(MotionEvent ev) {
        float dx = ev.getRawX() - downRawX;
        float dy = ev.getRawY() - downRawY;
        return dx * dx + dy * dy > touchSlop * touchSlop;
    }

    private void beginDrag() {
        dragging = true;
        ViewParent parent = getParent();
        if (parent != null) {
            parent.requestDisallowInterceptTouchEvent(true);
        }
        setCardElevation(DRAG_ELEVATION_DP * getResources().getDisplayMetrics().density);
        bringToFront();
    }

    private void restoreSavedOffset() {
        if (!MapOverlayPrefs.hasHudOffset(getContext())) {
            return;
        }
        applyClampedTranslation(
                MapOverlayPrefs.getHudOffsetX(getContext()),
                MapOverlayPrefs.getHudOffsetY(getContext()));
    }

    private void clampCurrentTranslation() {
        applyClampedTranslation(getTranslationX(), getTranslationY());
    }

    private void applyClampedTranslation(float tx, float ty) {
        ViewParent parent = getParent();
        if (!(parent instanceof View) || getWidth() == 0 || getHeight() == 0) {
            setTranslationX(tx);
            setTranslationY(ty);
            return;
        }
        View host = (View) parent;
        if (host.getWidth() == 0 || host.getHeight() == 0) {
            setTranslationX(tx);
            setTranslationY(ty);
            return;
        }
        float margin = 8f * getResources().getDisplayMetrics().density;
        float left = getLeft() + tx;
        float top = getTop() + ty;
        float maxLeft = Math.max(margin, host.getWidth() - getWidth() - margin);
        float maxTop = Math.max(margin, host.getHeight() - getHeight() - margin);
        left = Math.max(margin, Math.min(left, maxLeft));
        top = Math.max(margin, Math.min(top, maxTop));
        setTranslationX(left - getLeft());
        setTranslationY(top - getTop());
    }
}
