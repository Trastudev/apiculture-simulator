package com.apiculture.simulator.presentation.market;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.apiculture.simulator.R;

import java.util.Locale;

/** Mini gráfico lineal de 7 precios diarios (B/kg) con mínimo y máximo en el eje Y. */
public class MarketPriceSparkView extends View {

    private double[] prices = new double[0];

    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint todayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint axisPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path linePath = new Path();
    private final Path fillPath = new Path();

    public MarketPriceSparkView(Context context) {
        super(context);
        init();
    }

    public MarketPriceSparkView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public MarketPriceSparkView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        float d = getResources().getDisplayMetrics().density;
        int gold = ContextCompat.getColor(getContext(), R.color.event_gold);
        int ink = ContextCompat.getColor(getContext(), R.color.event_ink);
        int muted = ContextCompat.getColor(getContext(), R.color.event_ink_muted);
        linePaint.setColor(gold);
        linePaint.setStrokeWidth(2f * d);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeJoin(Paint.Join.ROUND);
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        fillPaint.setColor(gold);
        fillPaint.setAlpha(40);
        fillPaint.setStyle(Paint.Style.FILL);
        dotPaint.setColor(ink);
        dotPaint.setStyle(Paint.Style.FILL);
        todayPaint.setColor(gold);
        todayPaint.setStyle(Paint.Style.FILL);
        axisPaint.setColor(ink);
        axisPaint.setAlpha(50);
        axisPaint.setStrokeWidth(d);
        gridPaint.setColor(muted);
        gridPaint.setAlpha(80);
        gridPaint.setStrokeWidth(d);
        labelPaint.setColor(muted);
        labelPaint.setTextSize(9f * d);
        labelPaint.setTextAlign(Paint.Align.RIGHT);
        setContentDescription(getContext().getString(R.string.market_price_spark_cd));
    }

    public void setPrices(@Nullable double[] values) {
        this.prices = values != null ? values.clone() : new double[0];
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int h = (int) (68f * getResources().getDisplayMetrics().density);
        int specH = MeasureSpec.getSize(heightMeasureSpec);
        if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY && specH > 0) {
            h = specH;
        }
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), h);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int n = prices.length;
        if (n < 2) {
            return;
        }
        float d = getResources().getDisplayMetrics().density;
        double dataMin = prices[0];
        double dataMax = prices[0];
        for (int i = 1; i < n; i++) {
            dataMin = Math.min(dataMin, prices[i]);
            dataMax = Math.max(dataMax, prices[i]);
        }

        float left = 38f * d;
        float right = getWidth() - 6f * d;
        float top = 10f * d;
        float bottom = getHeight() - 10f * d;
        if (right <= left || bottom <= top) {
            return;
        }

        double scaleMin = dataMin;
        double scaleMax = dataMax;
        if (scaleMax - scaleMin < 1e-6) {
            scaleMin -= 0.2;
            scaleMax += 0.2;
        } else {
            double pad = (scaleMax - scaleMin) * 0.12;
            scaleMin -= pad;
            scaleMax += pad;
        }
        double spanV = scaleMax - scaleMin;
        float spanX = right - left;

        float yMax = yFor(dataMax, top, bottom, scaleMin, spanV);
        float yMin = yFor(dataMin, top, bottom, scaleMin, spanV);
        canvas.drawLine(left, yMax, right, yMax, gridPaint);
        canvas.drawLine(left, yMin, right, yMin, gridPaint);
        canvas.drawLine(left, top, left, bottom, axisPaint);
        canvas.drawLine(left, bottom, right, bottom, axisPaint);

        String maxLabel = formatPrice(dataMax);
        String minLabel = formatPrice(dataMin);
        canvas.drawText(maxLabel, left - 4f * d, yMax + 3f * d, labelPaint);
        if (Math.abs(dataMax - dataMin) >= 0.005) {
            canvas.drawText(minLabel, left - 4f * d, yMin + 3f * d, labelPaint);
        }

        linePath.reset();
        fillPath.reset();
        for (int i = 0; i < n; i++) {
            float x = left + spanX * (i / (float) (n - 1));
            float y = yFor(prices[i], top, bottom, scaleMin, spanV);
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
        for (int i = 0; i < n; i++) {
            float x = left + spanX * (i / (float) (n - 1));
            float y = yFor(prices[i], top, bottom, scaleMin, spanV);
            boolean last = i == n - 1;
            canvas.drawCircle(x, y, (last ? 3.4f : 2.2f) * d, last ? todayPaint : dotPaint);
        }
    }

    private static float yFor(double price, float top, float bottom, double scaleMin, double spanV) {
        return (float) (bottom - ((price - scaleMin) / spanV) * (bottom - top));
    }

    private static String formatPrice(double v) {
        return String.format(Locale.getDefault(), "%.2f", v);
    }
}
