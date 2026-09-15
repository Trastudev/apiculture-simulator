package com.apiculture.simulator.presentation.hive;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.SparseArray;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.apiculture.simulator.R;
import com.apiculture.simulator.data.local.entity.HiveEntity;
import com.apiculture.simulator.domain.game.DailySkyCondition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Prado del apiario: paisaje según el clima, tiempo del día anterior superpuesto, colmenas en dos filas.
 */
public class ApiaryYardView extends View {

    public interface OnHiveTapListener {
        void onHiveTap(@NonNull HiveEntity hive);
    }

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint namePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Path hillPath = new Path();
    private final RectF tmp = new RectF();
    private final Matrix beeMatrix = new Matrix();

    private Bitmap hiveBmp;
    private Bitmap beeBmp;
    private Bitmap sunBmp;
    @Nullable
    private Bitmap climateBmp;
    @Nullable
    private YardClimate climateBmpKey;
    private final SparseArray<Bitmap> floraIconCache = new SparseArray<>();

    private DailySkyCondition sky = DailySkyCondition.SUN;
    private YardClimate climate = YardClimate.CONTINENTAL;
    private boolean previewMode;
    private List<HiveEntity> hives = Collections.emptyList();
    private final List<RectF> hiveRects = new ArrayList<>();

    private boolean animRunning;
    private long animStartMs;
    private float animSec;
    private float lastTouchX;
    private float lastTouchY;
    @Nullable
    private OnHiveTapListener hiveTapListener;

    private final Runnable animTick = new Runnable() {
        @Override
        public void run() {
            if (!animRunning) {
                return;
            }
            animSec = (SystemClock.elapsedRealtime() - animStartMs) / 1000f;
            invalidate();
            postOnAnimation(this);
        }
    };

    public ApiaryYardView(Context context) {
        super(context);
        init();
    }

    public ApiaryYardView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ApiaryYardView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setClickable(true);
        hiveBmp = BitmapFactory.decodeResource(getResources(), R.drawable.ic_colmena_prado);
        beeBmp = BitmapFactory.decodeResource(getResources(), R.drawable.ic_abeja);
        sunBmp = BitmapFactory.decodeResource(getResources(), R.drawable.ic_sol_prado);
        namePaint.setColor(Color.parseColor("#2C2419"));
        namePaint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        namePaint.setTextAlign(Paint.Align.CENTER);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
    }

    public void setOnHiveTapListener(@Nullable OnHiveTapListener listener) {
        this.hiveTapListener = listener;
    }

    public void setPreviewMode(boolean preview) {
        this.previewMode = preview;
        setClickable(!preview);
        setFocusable(!preview);
        invalidate();
    }

    public void setSky(@Nullable DailySkyCondition condition) {
        this.sky = condition != null ? condition : DailySkyCondition.SUN;
        invalidate();
    }

    public void setClimate(@Nullable YardClimate next) {
        this.climate = next != null ? next : YardClimate.CONTINENTAL;
        invalidate();
    }

    public void setHives(@Nullable List<HiveEntity> next) {
        this.hives = next != null ? next : Collections.emptyList();
        layoutHives();
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        layoutHives();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (animStartMs == 0L) {
            animStartMs = SystemClock.elapsedRealtime();
        }
        animRunning = true;
        removeCallbacks(animTick);
        postOnAnimation(animTick);
    }

    @Override
    protected void onDetachedFromWindow() {
        animRunning = false;
        removeCallbacks(animTick);
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        float meadowTop = groundTop(h);
        drawSky(canvas, w, h);
        Bitmap backdrop = climateBitmap(w, h);
        if (backdrop != null) {
            drawBackdrop(canvas, backdrop, w, h);
            drawWeatherWash(canvas, w, h, meadowTop);
        } else {
            drawClimate(canvas, w, h, meadowTop);
            drawClimateDecor(canvas, w, h, meadowTop);
        }
        drawSun(canvas, w, meadowTop);
        drawClouds(canvas, w, meadowTop);
        drawRain(canvas, w, h);
        if (!previewMode) {
            drawHivesAndBees(canvas);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (previewMode) {
            return false;
        }
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            lastTouchX = event.getX();
            lastTouchY = event.getY();
            return true;
        }
        if (event.getAction() == MotionEvent.ACTION_UP) {
            if (Math.hypot(event.getX() - lastTouchX, event.getY() - lastTouchY)
                    < getResources().getDisplayMetrics().density * 18f) {
                HiveEntity hit = hitHive(event.getX(), event.getY());
                if (hit != null && hiveTapListener != null) {
                    hiveTapListener.onHiveTap(hit);
                    performClick();
                    return true;
                }
            }
        }
        return super.onTouchEvent(event);
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    @Nullable
    private HiveEntity hitHive(float x, float y) {
        for (int i = 0; i < hiveRects.size(); i++) {
            if (hiveRects.get(i).contains(x, y) && i < hives.size()) {
                return hives.get(i);
            }
        }
        return null;
    }

    private float groundTop(int h) {
        return h * (previewMode ? 0.50f : 0.40f);
    }

    @Nullable
    private Bitmap climateBitmap(int viewW, int viewH) {
        if (climateBmp != null && climateBmpKey == climate && !climateBmp.isRecycled()) {
            return climateBmp;
        }
        if (climateBmp != null && !climateBmp.isRecycled()) {
            climateBmp.recycle();
        }
        climateBmp = null;
        climateBmpKey = climate;
        int res = climate.backdropRes();
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeResource(getResources(), res, bounds);
        int maxSide = Math.max(viewW, viewH);
        if (maxSide <= 0) {
            maxSide = 1080;
        }
        int sample = 1;
        int src = Math.max(1, Math.max(bounds.outWidth, bounds.outHeight));
        while (src / (sample * 2) >= maxSide) {
            sample *= 2;
        }
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = Math.max(1, sample);
        opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
        climateBmp = BitmapFactory.decodeResource(getResources(), res, opts);
        return climateBmp;
    }

    private void drawBackdrop(Canvas canvas, Bitmap bmp, int w, int h) {
        float bw = bmp.getWidth();
        float bh = bmp.getHeight();
        if (bw <= 0f || bh <= 0f) {
            return;
        }
        float scale = Math.max(w / bw, h / bh);
        float dw = bw * scale;
        float dh = bh * scale;
        float left = (w - dw) / 2f;
        float top;
        if (previewMode) {
            // Tarjetas anchas y bajas: encuadrar la franja media del paisaje (no el cielo).
            float focusY = 0.62f;
            top = (h * 0.50f) - (dh * focusY);
            float minTop = h - dh;
            if (top < minTop) {
                top = minTop;
            }
            if (top > 0f) {
                top = 0f;
            }
        } else {
            top = (h - dh) / 2f;
        }
        fill.setAlpha(255);
        tmp.set(left, top, left + dw, top + dh);
        canvas.drawBitmap(bmp, null, tmp, fill);
    }

    private void drawWeatherWash(Canvas canvas, int w, int h, float meadowTop) {
        int color;
        switch (sky) {
            case RAINY:
            case CLOUDY:
                color = Color.argb(70, 70, 86, 100);
                break;
            case WINDY:
                color = Color.argb(45, 90, 104, 116);
                break;
            case VARIABLE:
            case SUN:
            default:
                return;
        }
        fill.setColor(color);
        canvas.drawRect(0, 0, w, h, fill);
        if (sky == DailySkyCondition.CLOUDY) {
            // Misma cortina suave que la lluvia, sin gotas.
            fill.setColor(Color.argb(previewMode ? 48 : 34, 150, 175, 195));
            canvas.drawRect(0, 0, w, h, fill);
        }
        fill.setAlpha(255);
    }

    private void drawClouds(Canvas canvas, int w, float meadowTop) {
        boolean overcast = sky == DailySkyCondition.RAINY || sky == DailySkyCondition.CLOUDY;
        int n;
        if (sky == DailySkyCondition.SUN) {
            n = 2;
        } else if (sky == DailySkyCondition.VARIABLE) {
            n = 4;
        } else if (overcast) {
            n = 6;
        } else {
            n = 4;
        }
        float pxPerSec = sky == DailySkyCondition.WINDY ? w * 0.055f : w * 0.028f;
        float band = Math.max(meadowTop, getHeight() * 0.42f);
        for (int i = 0; i < n; i++) {
            float scale = 0.72f + 0.18f * (i % 3);
            float s = w * 0.18f * scale;
            float cloudW = s * 2.05f;
            float travel = w + cloudW;
            float x = wrap(animSec * pxPerSec + i * (travel / n), travel) - cloudW;
            float y = band * (0.10f + 0.12f * (i % 3));
            int color = overcast || sky == DailySkyCondition.WINDY
                    ? Color.parseColor("#90A4AE")
                    : Color.WHITE;
            int alpha = (sky == DailySkyCondition.SUN || sky == DailySkyCondition.VARIABLE) ? 210 : 240;
            drawCloud(canvas, x, y, s, color, alpha);
        }
    }

    private void drawRain(Canvas canvas, int w, int h) {
        if (sky != DailySkyCondition.RAINY) {
            return;
        }
        if (w <= 1 || h <= 1) {
            return;
        }
        fill.setColor(Color.argb(previewMode ? 48 : 34, 150, 175, 195));
        canvas.drawRect(0, 0, w, h, fill);

        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStyle(Paint.Style.STROKE);

        int layers = 3;
        for (int layer = 0; layer < layers; layer++) {
            float len = dp(9f + layer * 5f);
            float drift = dp(1.2f + layer * 0.7f);
            float baseSpeed = 140f + layer * 75f;
            float thickness = Math.max(1.5f, dp(1.2f + layer * 0.4f));
            int alpha = previewMode ? (150 + layer * 35) : (130 + layer * 40);
            int count = previewMode ? (40 + layer * 24) : (60 + layer * 36);
            float travel = h + len + dp(16f);
            stroke.setStrokeWidth(thickness);
            stroke.setColor(Color.argb(alpha, 236, 245, 255));
            for (int i = 0; i < count; i++) {
                float u1 = rainUnit(i, layer, 1);
                float u2 = rainUnit(i, layer, 2);
                float u3 = rainUnit(i, layer, 3);
                float x = u1 * w;
                float phase = u2 * travel;
                float speed = baseSpeed * (0.70f + 0.60f * u3);
                float y = wrap(phase + animSec * speed, travel) - len;
                float localDrift = drift * (0.65f + 0.7f * rainUnit(i, layer, 4));
                canvas.drawLine(x, y, x + localDrift, y + len, stroke);
            }
        }
        fill.setColor(Color.argb(previewMode ? 140 : 110, 220, 235, 248));
        int splashes = previewMode ? 16 : 26;
        float ground = h * (previewMode ? 0.55f : 0.65f);
        for (int i = 0; i < splashes; i++) {
            float x = rainUnit(i, 9, 1) * w;
            float y = ground + rainUnit(i, 9, 2) * (h - ground) * 0.85f;
            float pulse = 0.55f + 0.45f * (0.5f + 0.5f * (float) Math.sin(animSec * 5.2 + i * 1.7));
            canvas.drawCircle(x, y, dp(1.4f) * pulse, fill);
        }
        fill.setAlpha(255);
        stroke.setAlpha(255);
    }

    /** Valor estable en [0,1) por gota/capa/canal, sin alinear filas ni columnas. */
    private static float rainUnit(int index, int layer, int channel) {
        int h = index * 374761393 + layer * 668265263 + channel * 1274126177;
        h = (h ^ (h >>> 13)) * 1274126177;
        h ^= (h >>> 16);
        return (h & 0xffff) / 65535f;
    }

    private void layoutHives() {
        hiveRects.clear();
        int n = hives.size();
        int w = getWidth();
        int h = getHeight();
        if (n <= 0 || w <= 0 || h <= 0 || previewMode) {
            return;
        }
        int topN = (n + 1) / 2;
        int botN = n / 2;
        float meadowTop = groundTop(h);
        float meadowH = h - meadowTop;
        float hiveH = Math.min(meadowH * 0.30f, w / Math.max(topN, 1) * 0.72f);
        float hiveW = hiveH * 0.92f;
        float topCy = meadowTop + meadowH * 0.26f;
        float botCy = meadowTop + meadowH * 0.58f;
        placeRow(0, topN, topCy, hiveW, hiveH, w);
        if (botN > 0) {
            placeRow(topN, botN, botCy, hiveW, hiveH, w);
        }
    }

    private void placeRow(int start, int count, float cy, float hiveW, float hiveH, int w) {
        float gap = hiveW * 0.12f;
        float total = count * hiveW + Math.max(0, count - 1) * gap;
        float x0 = (w - total) / 2f;
        for (int i = 0; i < count; i++) {
            float left = x0 + i * (hiveW + gap);
            hiveRects.add(new RectF(left, cy - hiveH / 2f, left + hiveW, cy + hiveH / 2f));
        }
    }

    private void drawSky(Canvas canvas, int w, int h) {
        int top;
        int bot;
        switch (sky) {
            case RAINY:
            case CLOUDY:
                top = Color.parseColor("#5B6E7D");
                bot = Color.parseColor("#90A4AE");
                break;
            case WINDY:
                top = Color.parseColor("#90A4AE");
                bot = Color.parseColor("#CFD8DC");
                break;
            case VARIABLE:
                top = Color.parseColor("#6BB8E8");
                bot = Color.parseColor("#F7F3EA");
                break;
            case SUN:
            default:
                top = Color.parseColor("#5EB3E4");
                bot = Color.parseColor("#F7F3EA");
                break;
        }
        // Cielo a pantalla completa: el PNG del clima lleva alfa arriba y deja ver esto.
        fill.setShader(new LinearGradient(0, 0, 0, h * 0.58f, top, bot, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, h, fill);
        fill.setShader(null);
    }

    private void drawSun(Canvas canvas, int w, float meadowTop) {
        if (sky == DailySkyCondition.WINDY
                || sky == DailySkyCondition.RAINY
                || sky == DailySkyCondition.CLOUDY) {
            return;
        }
        float cx = w * 0.80f;
        float cy = meadowTop * 0.30f;
        float size = Math.min(w, meadowTop) * (previewMode ? 0.52f : 0.36f);
        float pulse = 1f + 0.03f * (float) Math.sin(animSec * 1.4);
        size *= pulse;
        fill.setColor(Color.WHITE);
        fill.setAlpha(sky == DailySkyCondition.CLOUDY ? 195 : 255);
        if (sunBmp != null) {
            tmp.set(cx - size / 2f, cy - size / 2f, cx + size / 2f, cy + size / 2f);
            canvas.drawBitmap(sunBmp, null, tmp, fill);
        } else {
            fill.setColor(Color.parseColor("#F9A825"));
            canvas.drawCircle(cx, cy, size * 0.28f, fill);
        }
        fill.setAlpha(255);
    }

    private void drawCloud(Canvas canvas, float x, float y, float s, int color, int alpha) {
        fill.setColor(color);
        fill.setAlpha(alpha);
        canvas.drawOval(x, y, x + s * 1.8f, y + s * 0.72f, fill);
        canvas.drawCircle(x + s * 0.42f, y + s * 0.08f, s * 0.42f, fill);
        canvas.drawCircle(x + s * 1.05f, y + s * 0.02f, s * 0.48f, fill);
        canvas.drawCircle(x + s * 1.55f, y + s * 0.22f, s * 0.32f, fill);
        fill.setAlpha(255);
    }

    private void drawClimate(Canvas canvas, int w, int h, float meadowTop) {
        switch (climate) {
            case MOUNTAIN:
                drawMountainScene(canvas, w, h, meadowTop);
                break;
            case MEDITERRANEAN:
                drawMediterraneanScene(canvas, w, h, meadowTop);
                break;
            case ATLANTIC:
                drawAtlanticScene(canvas, w, h, meadowTop);
                break;
            case SOUTH:
                drawSouthScene(canvas, w, h, meadowTop);
                break;
            case FYNBOS:
                drawFynbosScene(canvas, w, h, meadowTop);
                break;
            case KAROO:
                drawKarooScene(canvas, w, h, meadowTop);
                break;
            case HIGHVELD:
                drawHighveldScene(canvas, w, h, meadowTop);
                break;
            case SUBTROPICAL:
                drawSubtropicalScene(canvas, w, h, meadowTop);
                break;
            case BUSHVELD:
                drawBushveldScene(canvas, w, h, meadowTop);
                break;
            case CONTINENTAL:
            default:
                drawContinentalScene(canvas, w, h, meadowTop);
                break;
        }
        if (sky == DailySkyCondition.RAINY) {
            fill.setColor(Color.argb(48, 40, 52, 64));
            canvas.drawRect(0, meadowTop - dp(28f), w, h, fill);
        }
    }

    private void drawMountainScene(Canvas canvas, int w, int h, float meadowTop) {
        drawRidge(canvas, w, meadowTop, 0.12f, 0.62f, Color.parseColor("#6B7C8A"), -22f);
        drawSnowPeak(canvas, w * 0.22f, meadowTop, w * 0.34f, meadowTop * 0.78f,
                Color.parseColor("#7A868F"));
        drawSnowPeak(canvas, w * 0.52f, meadowTop, w * 0.48f, meadowTop * 0.92f,
                Color.parseColor("#8A949C"));
        drawSnowPeak(canvas, w * 0.78f, meadowTop, w * 0.30f, meadowTop * 0.62f,
                Color.parseColor("#6E7A84"));
        drawMeadow(canvas, w, h, meadowTop,
                Color.parseColor("#4E6B3A"), Color.parseColor("#7A9A58"),
                Color.parseColor("#5D7A45"), Color.parseColor("#3E562E"));
    }

    private void drawMediterraneanScene(Canvas canvas, int w, int h, float meadowTop) {
        drawSea(canvas, w, meadowTop - meadowTop * 0.34f, meadowTop + dp(10f),
                Color.parseColor("#1E6FA8"), Color.parseColor("#4FC3E0"));
        drawIsland(canvas, w * 0.18f, meadowTop - meadowTop * 0.10f, w * 0.16f);
        fill.setColor(Color.parseColor("#E8D5A3"));
        canvas.drawRect(0, meadowTop, w, meadowTop + dp(14f), fill);
        drawMeadow(canvas, w, h, meadowTop,
                Color.parseColor("#8FBC68"), Color.parseColor("#C5D97A"),
                Color.parseColor("#A5C75B"), Color.parseColor("#7A9E3E"));
    }

    private void drawAtlanticScene(Canvas canvas, int w, int h, float meadowTop) {
        drawSea(canvas, w, meadowTop - meadowTop * 0.28f, meadowTop + dp(8f),
                Color.parseColor("#1A4F7A"), Color.parseColor("#3A7CA8"));
        drawSnowPeak(canvas, w * 0.46f, meadowTop, w * 0.30f, meadowTop * 0.70f,
                Color.parseColor("#8B9399"));
        drawSnowPeak(canvas, w * 0.68f, meadowTop, w * 0.40f, meadowTop * 0.90f,
                Color.parseColor("#9AA3A8"));
        drawSnowPeak(canvas, w * 0.88f, meadowTop, w * 0.26f, meadowTop * 0.58f,
                Color.parseColor("#7D868C"));
        drawRidge(canvas, w, meadowTop, 0.08f, 0.55f, Color.parseColor("#6B8F4E"), 10f);
        drawMeadow(canvas, w, h, meadowTop,
                Color.parseColor("#6B9A46"), Color.parseColor("#A8C96A"),
                Color.parseColor("#7CB342"), Color.parseColor("#558B2F"));
    }

    private void drawSouthScene(Canvas canvas, int w, int h, float meadowTop) {
        drawRidge(canvas, w, meadowTop, 0.10f, 0.70f, Color.parseColor("#C4A574"), -8f);
        drawRidge(canvas, w, meadowTop, 0.04f, 0.48f, Color.parseColor("#D7B98A"), 16f);
        drawMeadow(canvas, w, h, meadowTop,
                Color.parseColor("#C6B04A"), Color.parseColor("#E3D48A"),
                Color.parseColor("#D4C05C"), Color.parseColor("#A89032"));
    }

    private void drawContinentalScene(Canvas canvas, int w, int h, float meadowTop) {
        drawRidge(canvas, w, meadowTop, 0.16f, 0.58f, Color.parseColor("#3E6B32"), -18f);
        drawRidge(canvas, w, meadowTop, 0.08f, 0.40f, Color.parseColor("#4E803C"), 22f);
        drawMeadow(canvas, w, h, meadowTop,
                Color.parseColor("#3D6B2C"), Color.parseColor("#5E8F40"),
                Color.parseColor("#4C7A34"), Color.parseColor("#2E541F"));
    }

    private void drawFynbosScene(Canvas canvas, int w, int h, float meadowTop) {
        drawSea(canvas, w, meadowTop - meadowTop * 0.36f, meadowTop + dp(8f),
                Color.parseColor("#1565A8"), Color.parseColor("#4DB6D6"));
        drawTableMountain(canvas, w * 0.58f, meadowTop, w * 0.52f, meadowTop * 0.70f);
        drawMeadow(canvas, w, h, meadowTop,
                Color.parseColor("#7A9A4A"), Color.parseColor("#C5C86A"),
                Color.parseColor("#9AAA52"), Color.parseColor("#5E7A32"));
    }

    private void drawKarooScene(Canvas canvas, int w, int h, float meadowTop) {
        drawKoppie(canvas, w * 0.22f, meadowTop, w * 0.22f, meadowTop * 0.28f);
        drawKoppie(canvas, w * 0.72f, meadowTop, w * 0.30f, meadowTop * 0.38f);
        drawMeadow(canvas, w, h, meadowTop,
                Color.parseColor("#D2B48C"), Color.parseColor("#E8D5B0"),
                Color.parseColor("#C4A574"), Color.parseColor("#A88858"));
    }

    private void drawHighveldScene(Canvas canvas, int w, int h, float meadowTop) {
        drawRidge(canvas, w, meadowTop, 0.10f, 0.62f, Color.parseColor("#8B9A62"), -6f);
        drawKoppie(canvas, w * 0.78f, meadowTop, w * 0.24f, meadowTop * 0.22f);
        drawMeadow(canvas, w, h, meadowTop,
                Color.parseColor("#6B8F3A"), Color.parseColor("#B8C85A"),
                Color.parseColor("#8FAA48"), Color.parseColor("#5A7A2C"));
    }

    private void drawSubtropicalScene(Canvas canvas, int w, int h, float meadowTop) {
        drawSea(canvas, w, meadowTop - meadowTop * 0.38f, meadowTop + dp(10f),
                Color.parseColor("#0277BD"), Color.parseColor("#4DD0E1"));
        fill.setColor(Color.parseColor("#E8C99A"));
        canvas.drawRect(0, meadowTop, w, meadowTop + dp(12f), fill);
        drawMeadow(canvas, w, h, meadowTop,
                Color.parseColor("#43A047"), Color.parseColor("#9CCC65"),
                Color.parseColor("#66BB6A"), Color.parseColor("#2E7D32"));
    }

    private void drawBushveldScene(Canvas canvas, int w, int h, float meadowTop) {
        drawRidge(canvas, w, meadowTop, 0.12f, 0.55f, Color.parseColor("#A67C52"), 12f);
        drawMeadow(canvas, w, h, meadowTop,
                Color.parseColor("#C4A035"), Color.parseColor("#E0C868"),
                Color.parseColor("#D4B24A"), Color.parseColor("#8A6A22"));
        fill.setColor(Color.parseColor("#A0522D"));
        fill.setAlpha(70);
        for (int i = 0; i < 4; i++) {
            float cx = w * (0.12f + 0.22f * i);
            float cy = meadowTop + (h - meadowTop) * (0.22f + 0.12f * (i % 2));
            canvas.drawOval(cx, cy, cx + w * 0.18f, cy + (h - meadowTop) * 0.10f, fill);
        }
        fill.setAlpha(255);
    }

    private void drawSea(Canvas canvas, int w, float top, float bot, int deep, int shallow) {
        fill.setShader(new LinearGradient(0, top, 0, bot, deep, shallow, Shader.TileMode.CLAMP));
        canvas.drawRect(0, top, w, bot, fill);
        fill.setShader(null);
        stroke.setColor(Color.argb(90, 255, 255, 255));
        stroke.setStrokeWidth(dp(1.6f));
        float wave = (float) Math.sin(animSec * 1.1) * dp(2.5f);
        for (int i = 0; i < 4; i++) {
            float y = top + (bot - top) * (0.28f + i * 0.16f) + wave * ((i % 2 == 0) ? 1 : -1);
            canvas.drawLine(0, y, w, y + dp(2f), stroke);
        }
        stroke.setAlpha(255);
    }

    private void drawIsland(Canvas canvas, float cx, float cy, float s) {
        fill.setColor(Color.parseColor("#6B8F4E"));
        canvas.drawOval(cx - s * 0.5f, cy - s * 0.18f, cx + s * 0.5f, cy + s * 0.22f, fill);
        fill.setColor(Color.parseColor("#4E6B32"));
        canvas.drawCircle(cx, cy - s * 0.08f, s * 0.16f, fill);
    }

    private void drawRidge(Canvas canvas, int w, float meadowTop, float rise, float midX, int color, float skew) {
        fill.setColor(color);
        hillPath.reset();
        hillPath.moveTo(0, meadowTop + 18f);
        hillPath.quadTo(w * midX, meadowTop - meadowTop * rise + skew, w, meadowTop + 8f);
        hillPath.lineTo(w, meadowTop + 48f);
        hillPath.lineTo(0, meadowTop + 48f);
        hillPath.close();
        canvas.drawPath(hillPath, fill);
    }

    private void drawSnowPeak(Canvas canvas, float cx, float baseY, float width, float height, int rock) {
        fill.setColor(rock);
        hillPath.reset();
        hillPath.moveTo(cx - width / 2f, baseY + 12f);
        hillPath.lineTo(cx, baseY - height);
        hillPath.lineTo(cx + width / 2f, baseY + 12f);
        hillPath.close();
        canvas.drawPath(hillPath, fill);
        fill.setColor(Color.parseColor("#F5F8FB"));
        hillPath.reset();
        float snowH = height * 0.38f;
        float snowW = width * 0.34f;
        hillPath.moveTo(cx - snowW / 2f, baseY - height + snowH);
        hillPath.lineTo(cx, baseY - height);
        hillPath.lineTo(cx + snowW / 2f, baseY - height + snowH * 0.85f);
        hillPath.quadTo(cx, baseY - height + snowH * 1.05f, cx - snowW / 2f, baseY - height + snowH);
        hillPath.close();
        canvas.drawPath(hillPath, fill);
    }

    private void drawTableMountain(Canvas canvas, float cx, float baseY, float width, float height) {
        fill.setColor(Color.parseColor("#8A8478"));
        hillPath.reset();
        float topY = baseY - height;
        hillPath.moveTo(cx - width * 0.48f, baseY + 10f);
        hillPath.lineTo(cx - width * 0.32f, topY + height * 0.12f);
        hillPath.lineTo(cx - width * 0.22f, topY);
        hillPath.lineTo(cx + width * 0.28f, topY + height * 0.04f);
        hillPath.lineTo(cx + width * 0.38f, topY + height * 0.18f);
        hillPath.lineTo(cx + width * 0.50f, baseY + 10f);
        hillPath.close();
        canvas.drawPath(hillPath, fill);
        fill.setColor(Color.parseColor("#C5C0B4"));
        canvas.drawRect(cx - width * 0.22f, topY, cx + width * 0.28f, topY + height * 0.10f, fill);
    }

    private void drawKoppie(Canvas canvas, float cx, float baseY, float width, float height) {
        fill.setColor(Color.parseColor("#A67C52"));
        hillPath.reset();
        hillPath.moveTo(cx - width / 2f, baseY + 8f);
        hillPath.quadTo(cx - width * 0.18f, baseY - height, cx, baseY - height * 0.85f);
        hillPath.quadTo(cx + width * 0.20f, baseY - height * 0.95f, cx + width / 2f, baseY + 8f);
        hillPath.close();
        canvas.drawPath(hillPath, fill);
        fill.setColor(Color.parseColor("#8B5A2B"));
        canvas.drawOval(cx - width * 0.12f, baseY - height * 0.55f,
                cx + width * 0.10f, baseY - height * 0.22f, fill);
    }

    private void drawMeadow(Canvas canvas, int w, int h, float meadowTop,
                            int topC, int botC, int tuftC, int bladeC) {
        fill.setShader(new LinearGradient(0, meadowTop, 0, h, topC, botC, Shader.TileMode.CLAMP));
        canvas.drawRect(0, meadowTop, w, h, fill);
        fill.setShader(null);
        fill.setColor(tuftC);
        fill.setAlpha(120);
        for (int i = 0; i < 7; i++) {
            float cx = w * (0.08f + 0.13f * i);
            float cy = meadowTop + (h - meadowTop) * (0.18f + 0.1f * (i % 3));
            canvas.drawOval(cx, cy, cx + w * 0.22f, cy + (h - meadowTop) * 0.12f, fill);
        }
        fill.setAlpha(255);
        stroke.setStrokeWidth(dp(1.4f));
        stroke.setColor(bladeC);
        float sway = (float) Math.sin(animSec * 1.3) * dp(3f);
        for (int i = 0; i < 18; i++) {
            float x = w * (0.03f + i * 0.055f);
            float y1 = h - dp(18 + (i % 4) * 6);
            canvas.drawLine(x, h, x + sway * ((i % 2 == 0) ? 1 : -1), y1, stroke);
        }
    }

    private void drawClimateDecor(Canvas canvas, int w, int h, float meadowTop) {
        float meadowH = h - meadowTop;
        switch (climate) {
            case MOUNTAIN:
                drawRock(canvas, w * 0.10f, meadowTop + meadowH * 0.78f, dp(16f));
                drawRock(canvas, w * 0.88f, meadowTop + meadowH * 0.72f, dp(20f));
                drawDaisy(canvas, w * 0.18f, meadowTop + meadowH * 0.90f, dp(7f), 0.2f);
                break;
            case MEDITERRANEAN:
                drawUmbrellaPine(canvas, w * 0.12f, meadowTop + meadowH * 0.86f, dp(36f));
                drawUmbrellaPine(canvas, w * 0.90f, meadowTop + meadowH * 0.80f, dp(42f));
                drawDaisy(canvas, w * 0.28f, meadowTop + meadowH * 0.92f, dp(8f), 0.8f);
                break;
            case ATLANTIC:
                drawBush(canvas, w * 0.10f, meadowTop + meadowH * 0.82f, dp(32f),
                        Color.parseColor("#1B5E20"), Color.parseColor("#43A047"));
                drawBush(canvas, w * 0.88f, meadowTop + meadowH * 0.78f, dp(36f),
                        Color.parseColor("#1B5E20"), Color.parseColor("#66BB6A"));
                drawDaisy(canvas, w * 0.22f, meadowTop + meadowH * 0.90f, dp(8f), 0.4f);
                break;
            case SOUTH:
                drawDryClump(canvas, w * 0.12f, meadowTop + meadowH * 0.86f, dp(22f));
                drawDryClump(canvas, w * 0.86f, meadowTop + meadowH * 0.80f, dp(26f));
                drawDryClump(canvas, w * 0.30f, meadowTop + meadowH * 0.93f, dp(16f));
                break;
            case FYNBOS:
                drawProtea(canvas, w * 0.14f, meadowTop + meadowH * 0.86f, dp(16f));
                drawProtea(canvas, w * 0.86f, meadowTop + meadowH * 0.80f, dp(18f));
                drawBush(canvas, w * 0.24f, meadowTop + meadowH * 0.92f, dp(22f),
                        Color.parseColor("#558B2F"), Color.parseColor("#9CCC65"));
                break;
            case KAROO:
                drawDryClump(canvas, w * 0.16f, meadowTop + meadowH * 0.88f, dp(14f));
                drawDryClump(canvas, w * 0.82f, meadowTop + meadowH * 0.84f, dp(18f));
                drawRock(canvas, w * 0.40f, meadowTop + meadowH * 0.90f, dp(10f));
                break;
            case HIGHVELD:
                drawBush(canvas, w * 0.08f, meadowTop + meadowH * 0.84f, dp(24f),
                        Color.parseColor("#33691E"), Color.parseColor("#9CCC65"));
                drawDaisy(canvas, w * 0.20f, meadowTop + meadowH * 0.90f, dp(8f), 0.5f);
                drawDaisy(canvas, w * 0.78f, meadowTop + meadowH * 0.88f, dp(7f), 1.2f);
                break;
            case SUBTROPICAL:
                drawPalm(canvas, w * 0.10f, meadowTop + meadowH * 0.88f, dp(44f));
                drawPalm(canvas, w * 0.88f, meadowTop + meadowH * 0.82f, dp(50f));
                break;
            case BUSHVELD:
                drawAcacia(canvas, w * 0.14f, meadowTop + meadowH * 0.78f, dp(40f));
                drawAcacia(canvas, w * 0.86f, meadowTop + meadowH * 0.72f, dp(48f));
                drawDryClump(canvas, w * 0.32f, meadowTop + meadowH * 0.92f, dp(14f));
                break;
            case CONTINENTAL:
            default:
                drawPoplar(canvas, w * 0.08f, meadowTop + meadowH * 0.82f, dp(52f));
                drawPoplar(canvas, w * 0.92f, meadowTop + meadowH * 0.76f, dp(58f));
                drawBush(canvas, w * 0.22f, meadowTop + meadowH * 0.90f, dp(26f),
                        Color.parseColor("#1B5E20"), Color.parseColor("#33691E"));
                drawDaisy(canvas, w * 0.30f, meadowTop + meadowH * 0.93f, dp(8f), 0.7f);
                break;
        }
    }

    private void drawBush(Canvas canvas, float x, float y, float r, int dark, int light) {
        fill.setColor(dark);
        canvas.drawCircle(x, y, r, fill);
        fill.setColor(light);
        canvas.drawCircle(x - r * 0.45f, y + r * 0.1f, r * 0.72f, fill);
        canvas.drawCircle(x + r * 0.4f, y + r * 0.12f, r * 0.68f, fill);
    }

    private void drawDaisy(Canvas canvas, float x, float y, float r, float phase) {
        float wobble = (float) Math.sin(animSec * 2.2 + phase) * dp(1.2f);
        fill.setColor(Color.parseColor("#FFFDE7"));
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4.0;
            float px = x + (float) Math.cos(a) * (r * 0.85f);
            float py = y + wobble + (float) Math.sin(a) * (r * 0.85f);
            canvas.drawCircle(px, py, r * 0.42f, fill);
        }
        fill.setColor(Color.parseColor("#F9A825"));
        canvas.drawCircle(x, y + wobble, r * 0.45f, fill);
    }

    private void drawRock(Canvas canvas, float x, float y, float r) {
        fill.setColor(Color.parseColor("#8D8D8D"));
        canvas.drawOval(x - r, y - r * 0.55f, x + r * 1.1f, y + r * 0.45f, fill);
        fill.setColor(Color.parseColor("#BDBDBD"));
        canvas.drawOval(x - r * 0.3f, y - r * 0.4f, x + r * 0.4f, y, fill);
    }

    private void drawDryClump(Canvas canvas, float x, float y, float h) {
        stroke.setColor(Color.parseColor("#A89032"));
        stroke.setStrokeWidth(dp(1.5f));
        float sway = (float) Math.sin(animSec * 1.1 + x) * dp(2f);
        for (int i = 0; i < 6; i++) {
            float dx = (i - 2.5f) * dp(3.2f);
            canvas.drawLine(x, y, x + dx + sway, y - h * (0.7f + 0.08f * (i % 3)), stroke);
        }
    }

    private void drawUmbrellaPine(Canvas canvas, float x, float y, float h) {
        stroke.setColor(Color.parseColor("#6D4C41"));
        stroke.setStrokeWidth(dp(4f));
        canvas.drawLine(x, y, x, y - h * 0.72f, stroke);
        fill.setColor(Color.parseColor("#2E7D32"));
        canvas.drawOval(x - h * 0.42f, y - h, x + h * 0.42f, y - h * 0.55f, fill);
        fill.setColor(Color.parseColor("#43A047"));
        canvas.drawOval(x - h * 0.28f, y - h * 0.92f, x + h * 0.28f, y - h * 0.62f, fill);
    }

    private void drawPoplar(Canvas canvas, float x, float y, float h) {
        stroke.setColor(Color.parseColor("#5D4037"));
        stroke.setStrokeWidth(dp(3.4f));
        canvas.drawLine(x, y, x, y - h * 0.55f, stroke);
        fill.setColor(Color.parseColor("#1B5E20"));
        canvas.drawOval(x - h * 0.16f, y - h, x + h * 0.16f, y - h * 0.22f, fill);
        fill.setColor(Color.parseColor("#2E7D32"));
        canvas.drawOval(x - h * 0.12f, y - h * 0.85f, x + h * 0.12f, y - h * 0.30f, fill);
    }

    private void drawPalm(Canvas canvas, float x, float y, float h) {
        stroke.setColor(Color.parseColor("#8D6E63"));
        stroke.setStrokeWidth(dp(4.2f));
        canvas.drawLine(x, y, x + dp(4f), y - h * 0.72f, stroke);
        fill.setColor(Color.parseColor("#2E7D32"));
        float topX = x + dp(4f);
        float topY = y - h * 0.72f;
        for (int i = 0; i < 6; i++) {
            double a = -Math.PI * 0.9 + i * Math.PI * 0.32;
            hillPath.reset();
            hillPath.moveTo(topX, topY);
            hillPath.quadTo(
                    topX + (float) Math.cos(a) * h * 0.35f,
                    topY + (float) Math.sin(a) * h * 0.18f,
                    topX + (float) Math.cos(a) * h * 0.55f,
                    topY + (float) Math.sin(a) * h * 0.08f);
            stroke.setStrokeWidth(dp(3.2f));
            stroke.setColor(Color.parseColor("#388E3C"));
            canvas.drawPath(hillPath, stroke);
        }
    }

    private void drawAcacia(Canvas canvas, float x, float y, float h) {
        stroke.setColor(Color.parseColor("#6D4C41"));
        stroke.setStrokeWidth(dp(3.6f));
        canvas.drawLine(x, y, x, y - h * 0.55f, stroke);
        canvas.drawLine(x, y - h * 0.32f, x - h * 0.18f, y - h * 0.48f, stroke);
        canvas.drawLine(x, y - h * 0.32f, x + h * 0.18f, y - h * 0.48f, stroke);
        fill.setColor(Color.parseColor("#7CB342"));
        canvas.drawOval(x - h * 0.48f, y - h * 0.78f, x + h * 0.48f, y - h * 0.42f, fill);
        fill.setColor(Color.parseColor("#9CCC65"));
        canvas.drawOval(x - h * 0.32f, y - h * 0.72f, x + h * 0.34f, y - h * 0.50f, fill);
    }

    private void drawProtea(Canvas canvas, float x, float y, float r) {
        stroke.setColor(Color.parseColor("#6B8F3A"));
        stroke.setStrokeWidth(dp(2.4f));
        canvas.drawLine(x, y + r * 0.8f, x, y, stroke);
        fill.setColor(Color.parseColor("#C2185B"));
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4.0;
            canvas.drawOval(
                    x + (float) Math.cos(a) * r * 0.35f - r * 0.22f,
                    y + (float) Math.sin(a) * r * 0.28f - r * 0.55f,
                    x + (float) Math.cos(a) * r * 0.35f + r * 0.22f,
                    y + (float) Math.sin(a) * r * 0.28f + r * 0.05f,
                    fill);
        }
        fill.setColor(Color.parseColor("#F8BBD0"));
        canvas.drawCircle(x, y - r * 0.15f, r * 0.28f, fill);
    }

    private void drawHivesAndBees(Canvas canvas) {
        if (hiveBmp == null) {
            return;
        }
        namePaint.setTextSize(dp(11f));
        fill.setColor(Color.WHITE);
        fill.setAlpha(255);
        boolean beesOut = sky != DailySkyCondition.RAINY;
        for (int i = 0; i < hiveRects.size() && i < hives.size(); i++) {
            RectF r = hiveRects.get(i);
            tmp.set(r.left, r.top, r.right, r.bottom);
            canvas.drawBitmap(hiveBmp, null, tmp, fill);

            HiveEntity hive = hives.get(i);
            drawHiveFloraBadge(canvas, r, hive.floraType);

            String label = hive.name != null && !hive.name.trim().isEmpty() ? hive.name.trim() : "Colmena";
            CharSequence ellipsized = TextUtils.ellipsize(label, namePaint, r.width() * 0.92f, TextUtils.TruncateAt.END);
            float cx = r.centerX();
            float cy = r.bottom + dp(12f);
            float tw = namePaint.measureText(ellipsized, 0, ellipsized.length());
            fill.setColor(Color.parseColor("#FFF8EC"));
            fill.setAlpha(242);
            canvas.drawRoundRect(cx - tw / 2f - dp(7f), cy - dp(9f), cx + tw / 2f + dp(7f), cy + dp(8f),
                    dp(8f), dp(8f), fill);
            fill.setAlpha(255);
            stroke.setStrokeWidth(dp(1f));
            stroke.setColor(Color.parseColor("#E8D5A3"));
            canvas.drawRoundRect(cx - tw / 2f - dp(7f), cy - dp(9f), cx + tw / 2f + dp(7f), cy + dp(8f),
                    dp(8f), dp(8f), stroke);
            canvas.drawText(ellipsized, 0, ellipsized.length(), cx, cy + dp(4f), namePaint);
            if (beesOut && beeBmp != null) {
                int bees = 2 + (i % 2);
                drawOrbitBees(canvas, r, bees, i);
            }
        }
    }

    private void drawHiveFloraBadge(Canvas canvas, RectF hive, @Nullable String floraType) {
        Bitmap badge = floraIconBitmap(HiveSiteSummaryUi.floraBadgeIcon(floraType));
        if (badge == null) {
            return;
        }
        float size = Math.min(hive.width(), hive.height()) * 0.44f;
        float cx = hive.centerX();
        float cy = hive.top + hive.height() * 0.48f;
        fill.setAlpha(255);
        tmp.set(cx - size / 2f, cy - size / 2f, cx + size / 2f, cy + size / 2f);
        canvas.drawBitmap(badge, null, tmp, fill);
    }

    @Nullable
    private Bitmap floraIconBitmap(int resId) {
        Bitmap cached = floraIconCache.get(resId);
        if (cached != null && !cached.isRecycled()) {
            return cached;
        }
        Bitmap decoded = BitmapFactory.decodeResource(getResources(), resId);
        if (decoded != null) {
            floraIconCache.put(resId, decoded);
        }
        return decoded;
    }

    private void drawOrbitBees(Canvas canvas, RectF hive, int count, int hiveIndex) {
        float beeS = hive.width() * 0.22f;
        float rx = hive.width() * 0.58f;
        float ry = hive.height() * 0.34f;
        float cy = hive.top + hive.height() * 0.28f;
        for (int b = 0; b < count; b++) {
            float angle = animSec * 1.05f + hiveIndex * 0.85f + b * ((float) (Math.PI * 2.0) / count);
            float bx = hive.centerX() + (float) Math.cos(angle) * rx;
            float by = cy + (float) Math.sin(angle) * ry;
            boolean facingRight = Math.sin(angle) < 0;
            beeMatrix.reset();
            float scale = beeS / Math.max(1, beeBmp.getWidth());
            beeMatrix.postScale(facingRight ? scale : -scale, scale);
            beeMatrix.postTranslate(facingRight ? bx - beeS / 2f : bx + beeS / 2f, by - beeS / 2f);
            canvas.drawBitmap(beeBmp, beeMatrix, fill);
        }
    }

    private static float wrap(float value, float period) {
        if (period <= 0f) {
            return 0f;
        }
        float m = value % period;
        return m < 0f ? m + period : m;
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }
}
