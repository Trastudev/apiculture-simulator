package com.apiculture.simulator.presentation.hive;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.apiculture.simulator.R;

/**
 * Néctar del mes (0–100 % de mielada plena) como línea, con eje Y de rango y eje X de días.
 */
public class FloraMonthLineChartView extends View {

    private double[] values = new double[0];
    private int todayIndex = -1;

    private final Paint axisPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint todayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint todayLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path linePath = new Path();
    private final Path fillPath = new Path();

    public FloraMonthLineChartView(Context context) {
        super(context);
        init();
    }

    public FloraMonthLineChartView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public FloraMonthLineChartView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        float d = getResources().getDisplayMetrics().density;
        int text = ContextCompat.getColor(getContext(), R.color.dash_text_card);
        int muted = ContextCompat.getColor(getContext(), R.color.dash_muted);
        int good = ContextCompat.getColor(getContext(), R.color.dash_good);
        int gold = ContextCompat.getColor(getContext(), R.color.dash_warning);

        axisPaint.setColor(muted);
        axisPaint.setStrokeWidth(d);
        axisPaint.setStyle(Paint.Style.STROKE);

        gridPaint.setColor(muted);
        gridPaint.setAlpha(70);
        gridPaint.setStrokeWidth(d);
        gridPaint.setStyle(Paint.Style.STROKE);

        labelPaint.setColor(text);
        labelPaint.setTextSize(10f * d);

        linePaint.setColor(good);
        linePaint.setStrokeWidth(2.2f * d);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeJoin(Paint.Join.ROUND);
        linePaint.setStrokeCap(Paint.Cap.ROUND);

        fillPaint.setColor(good);
        fillPaint.setAlpha(40);
        fillPaint.setStyle(Paint.Style.FILL);

        todayPaint.setColor(gold);
        todayPaint.setStyle(Paint.Style.FILL);

        todayLinePaint.setColor(gold);
        todayLinePaint.setStrokeWidth(d);
        todayLinePaint.setStyle(Paint.Style.STROKE);
        todayLinePaint.setPathEffect(new DashPathEffect(new float[]{6f * d, 4f * d}, 0f));
    }

    public void setSeries(double[] values01, int todayIndex) {
        this.values = values01 != null ? values01 : new double[0];
        this.todayIndex = todayIndex;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float d = getResources().getDisplayMetrics().density;
        float left = 36f * d;
        float right = getWidth() - 8f * d;
        float top = 8f * d;
        float bottom = getHeight() - 18f * d;
        if (right <= left || bottom <= top) {
            return;
        }

        labelPaint.setTextAlign(Paint.Align.RIGHT);
        labelPaint.setColor(ContextCompat.getColor(getContext(), R.color.dash_text_card));
        drawYTick(canvas, left, top, bottom, 1.0, "100 %");
        drawYTick(canvas, left, top, bottom, 0.5, "50 %");
        drawYTick(canvas, left, top, bottom, 0.0, "0 %");

        canvas.drawLine(left, top, left, bottom, axisPaint);
        canvas.drawLine(left, bottom, right, bottom, axisPaint);

        int n = values.length;
        if (n < 2) {
            return;
        }
        float span = right - left;
        linePath.reset();
        fillPath.reset();
        for (int i = 0; i < n; i++) {
            float x = left + span * (i / (float) (n - 1));
            float y = yFor(Math.max(0.0, Math.min(1.0, values[i])), top, bottom);
            if (i == 0) {
                linePath.moveTo(x, y);
                fillPath.moveTo(x, bottom);
                fillPath.lineTo(x, y);
            } else {
                linePath.lineTo(x, y);
                fillPath.lineTo(x, y);
            }
        }
        fillPath.lineTo(right, bottom);
        fillPath.close();
        canvas.drawPath(fillPath, fillPaint);
        canvas.drawPath(linePath, linePaint);

        if (todayIndex >= 0 && todayIndex < n) {
            float tx = left + span * (todayIndex / (float) (n - 1));
            canvas.drawLine(tx, top, tx, bottom, todayLinePaint);
            float ty = yFor(Math.max(0.0, Math.min(1.0, values[todayIndex])), top, bottom);
            canvas.drawCircle(tx, ty, 4f * d, todayPaint);
        }

        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setColor(ContextCompat.getColor(getContext(), R.color.dash_muted));
        canvas.drawText("1", left, getHeight() - 4f * d, labelPaint);
        canvas.drawText(String.valueOf(n), right, getHeight() - 4f * d, labelPaint);
        if (todayIndex > 0 && todayIndex < n - 1) {
            float tx = left + span * (todayIndex / (float) (n - 1));
            labelPaint.setColor(ContextCompat.getColor(getContext(), R.color.dash_text_card));
            canvas.drawText(String.valueOf(todayIndex + 1), tx, getHeight() - 4f * d, labelPaint);
        }
    }

    private void drawYTick(Canvas canvas, float left, float top, float bottom, double v, String label) {
        float y = yFor(v, top, bottom);
        canvas.drawLine(left, y, getWidth() - 8f * getResources().getDisplayMetrics().density, y, gridPaint);
        canvas.drawText(label, left - 6f * getResources().getDisplayMetrics().density, y + 4f
                * getResources().getDisplayMetrics().density, labelPaint);
    }

    private static float yFor(double v01, float top, float bottom) {
        return (float) (bottom - v01 * (bottom - top));
    }
}
