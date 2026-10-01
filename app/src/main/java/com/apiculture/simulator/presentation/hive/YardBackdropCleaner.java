package com.apiculture.simulator.presentation.hive;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

/**
 * Solo quita el ribete de croma (rosa oscuro) pegado al cielo transparente.
 * No inunda el dibujo: un par de píxeles de borde, sin comer flores ni copas.
 */
public final class YardBackdropCleaner {

    private static final int FRINGE_PASSES = 2;

    private YardBackdropCleaner() {
    }

    @Nullable
    public static Bitmap decodeCleaned(@Nullable Resources res, @DrawableRes int resId) {
        if (res == null || resId == 0) {
            return null;
        }
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
        opts.inMutable = true;
        opts.inScaled = false;
        Bitmap decoded = BitmapFactory.decodeResource(res, resId, opts);
        if (decoded == null) {
            return null;
        }
        Bitmap copy = decoded.copy(Bitmap.Config.ARGB_8888, true);
        if (copy != null && copy != decoded) {
            decoded.recycle();
            decoded = copy;
        }
        stripChromaMagenta(decoded);
        return decoded;
    }

    public static void stripChromaMagenta(@Nullable Bitmap bmp) {
        if (bmp == null || bmp.isRecycled() || bmp.getWidth() <= 0 || bmp.getHeight() <= 0) {
            return;
        }
        if (!bmp.isMutable()) {
            return;
        }
        int w = bmp.getWidth();
        int h = bmp.getHeight();
        int[] pixels = new int[w * h];
        bmp.getPixels(pixels, 0, w, 0, 0, w, h);
        stripChromaMagenta(pixels, w, h);
        bmp.setPixels(pixels, 0, w, 0, 0, w, h);
    }

    @VisibleForTesting
    public static void stripChromaMagenta(int[] pixels, int w, int h) {
        if (pixels == null || w <= 0 || h <= 0 || pixels.length < w * h) {
            return;
        }
        boolean[] sky = new boolean[w * h];
        for (int i = 0; i < w * h; i++) {
            sky[i] = ((pixels[i] >>> 24) & 255) < 12;
        }
        for (int pass = 0; pass < FRINGE_PASSES; pass++) {
            boolean[] extra = new boolean[w * h];
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int i = y * w + x;
                    if (sky[i]) {
                        continue;
                    }
                    int c = pixels[i];
                    int r = (c >>> 16) & 255;
                    int g = (c >>> 8) & 255;
                    int b = c & 255;
                    if (!isSkyFringe(r, g, b)) {
                        continue;
                    }
                    if (touchesSky(sky, x, y, w, h)) {
                        extra[i] = true;
                    }
                }
            }
            for (int i = 0; i < extra.length; i++) {
                if (extra[i]) {
                    sky[i] = true;
                    pixels[i] = 0;
                }
            }
        }
    }

    @VisibleForTesting
    public static boolean isChromaMagenta(int r, int g, int b) {
        return isSkyFringe(r, g, b);
    }

    @VisibleForTesting
    public static boolean isMagentaFringe(int r, int g, int b) {
        return isSkyFringe(r, g, b);
    }

    @VisibleForTesting
    public static boolean isKeySpill(int r, int g, int b) {
        return isSkyFringe(r, g, b);
    }

    /**
     * Rosa oscuro del recorte sobre magenta, no flores brillantes ni verde.
     */
    @VisibleForTesting
    public static boolean isSkyFringe(int r, int g, int b) {
        if (g > 48) {
            return false;
        }
        int mag = Math.min(r, b);
        if (mag < 50 || mag > 175) {
            return false;
        }
        if (mag - g < 40) {
            return false;
        }
        if (r > 210) {
            return false;
        }
        float hue = hue(r, g, b);
        return hue >= 318f && hue <= 338f;
    }

    private static boolean touchesSky(boolean[] sky, int x, int y, int w, int h) {
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (dx == 0 && dy == 0) {
                    continue;
                }
                int nx = x + dx;
                int ny = y + dy;
                if (nx < 0 || ny < 0 || nx >= w || ny >= h) {
                    continue;
                }
                if (sky[ny * w + nx]) {
                    return true;
                }
            }
        }
        return false;
    }

    private static float hue(int r, int g, int b) {
        float rf = r / 255f;
        float gf = g / 255f;
        float bf = b / 255f;
        float max = Math.max(rf, Math.max(gf, bf));
        float min = Math.min(rf, Math.min(gf, bf));
        if (max - min < 1e-6f) {
            return 0f;
        }
        float hue;
        if (max == rf) {
            hue = 60f * (((gf - bf) / (max - min)) % 6f);
        } else if (max == gf) {
            hue = 60f * (((bf - rf) / (max - min)) + 2f);
        } else {
            hue = 60f * (((rf - gf) / (max - min)) + 4f);
        }
        if (hue < 0f) {
            hue += 360f;
        }
        return hue;
    }
}
