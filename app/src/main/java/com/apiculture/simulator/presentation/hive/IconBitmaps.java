package com.apiculture.simulator.presentation.hive;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import androidx.annotation.Nullable;

/**
 * Las fotos de varroa y apialimento pesan varios megapíxeles. Dibujarlas enteras
 * revienta el lienzo («trying to draw too large bitmap»).
 */
public final class IconBitmaps {

    private IconBitmaps() {
    }

    @Nullable
    public static Bitmap decode(Resources res, int resId, int maxPx) {
        if (res == null || resId == 0 || maxPx < 1) {
            return null;
        }
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeResource(res, resId, bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return null;
        }
        int sample = 1;
        while (bounds.outWidth / sample > maxPx * 2 || bounds.outHeight / sample > maxPx * 2) {
            sample *= 2;
        }
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sample;
        opts.inScaled = false;
        Bitmap decoded = BitmapFactory.decodeResource(res, resId, opts);
        if (decoded == null) {
            return null;
        }
        int edge = Math.max(decoded.getWidth(), decoded.getHeight());
        if (edge <= maxPx) {
            return decoded;
        }
        float scale = maxPx / (float) edge;
        Bitmap small = Bitmap.createScaledBitmap(decoded,
                Math.max(1, Math.round(decoded.getWidth() * scale)),
                Math.max(1, Math.round(decoded.getHeight() * scale)),
                true);
        if (small != decoded) {
            decoded.recycle();
        }
        return small;
    }
}
