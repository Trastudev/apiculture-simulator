package com.apiculture.simulator.presentation.tutorial;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

/** Oscurece la pantalla y deja un hueco redondeado sobre el control del tutorial. */
public class TutorialScrimView extends View {

    private final Paint clearPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF hole = new RectF();
    private boolean holeVisible;
    private float radius;

    public TutorialScrimView(Context context) {
        super(context);
        init();
    }

    public TutorialScrimView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        setWillNotDraw(false);
        clearPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        radius = 16f * getResources().getDisplayMetrics().density;
    }

    public void setHole(@Nullable RectF localHole) {
        holeVisible = localHole != null && localHole.width() > 1f && localHole.height() > 1f;
        if (holeVisible) {
            hole.set(localHole);
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int layer = canvas.saveLayer(0f, 0f, getWidth(), getHeight(), null);
        canvas.drawColor(0xB3000000);
        if (holeVisible) {
            canvas.drawRoundRect(hole, radius, radius, clearPaint);
        }
        canvas.restoreToCount(layer);
    }
}
