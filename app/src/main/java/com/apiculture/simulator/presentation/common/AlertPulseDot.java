package com.apiculture.simulator.presentation.common;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;

import androidx.annotation.Nullable;

/** Circulito rojo con pulso suave para avisos de colmena/apiario. */
public class AlertPulseDot extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private ObjectAnimator pulse;
    private float pulseScale = 1f;

    public AlertPulseDot(Context context) {
        super(context);
        init();
    }

    public AlertPulseDot(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public AlertPulseDot(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        paint.setColor(0xFFE53935);
        paint.setStyle(Paint.Style.FILL);
    }

    public void setAlertVisible(boolean visible) {
        setVisibility(visible ? VISIBLE : GONE);
        if (visible) {
            startPulse();
        } else {
            stopPulse();
        }
    }

    private void startPulse() {
        if (pulse != null && pulse.isRunning()) {
            return;
        }
        pulse = ObjectAnimator.ofFloat(this, "pulseScale", 0.82f, 1.18f);
        pulse.setDuration(900);
        pulse.setRepeatMode(ValueAnimator.REVERSE);
        pulse.setRepeatCount(ValueAnimator.INFINITE);
        pulse.setInterpolator(new AccelerateDecelerateInterpolator());
        pulse.start();
    }

    private void stopPulse() {
        if (pulse != null) {
            pulse.cancel();
            pulse = null;
        }
        pulseScale = 1f;
    }

    /** Usado por ObjectAnimator. */
    @SuppressWarnings("unused")
    public void setPulseScale(float scale) {
        pulseScale = scale;
        invalidate();
    }

    @SuppressWarnings("unused")
    public float getPulseScale() {
        return pulseScale;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (getVisibility() == VISIBLE) {
            startPulse();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        stopPulse();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float r = Math.min(cx, cy) * 0.72f * pulseScale;
        canvas.drawCircle(cx, cy, r, paint);
    }
}
