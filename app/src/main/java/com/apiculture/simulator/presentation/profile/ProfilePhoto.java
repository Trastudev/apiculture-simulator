package com.apiculture.simulator.presentation.profile;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.net.Uri;
import android.util.Base64;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

public final class ProfilePhoto {

    private static final int SIZE = 192;

    private ProfilePhoto() {
    }

    @Nullable
    public static Bitmap decodeBase64(@Nullable String b64) {
        if (b64 == null || b64.isEmpty()) {
            return null;
        }
        try {
            byte[] raw = Base64.decode(b64, Base64.NO_WRAP);
            return BitmapFactory.decodeByteArray(raw, 0, raw.length);
        } catch (RuntimeException e) {
            return null;
        }
    }

    @Nullable
    public static String encodeJpeg(@Nullable Bitmap src) {
        if (src == null) {
            return null;
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (!src.compress(Bitmap.CompressFormat.JPEG, 78, out)) {
            return null;
        }
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
    }

    @Nullable
    public static Bitmap squareFromUri(@NonNull Context ctx, @NonNull Uri uri) {
        try (InputStream in = ctx.getContentResolver().openInputStream(uri)) {
            if (in == null) {
                return null;
            }
            Bitmap src = BitmapFactory.decodeStream(in);
            if (src == null) {
                return null;
            }
            return squareCrop(src, SIZE);
        } catch (Exception e) {
            return null;
        }
    }

    @NonNull
    public static Bitmap squareCrop(@NonNull Bitmap src, int size) {
        int w = src.getWidth();
        int h = src.getHeight();
        int side = Math.min(w, h);
        int x = (w - side) / 2;
        int y = (h - side) / 2;
        Bitmap cropped = Bitmap.createBitmap(src, x, y, side, side);
        if (cropped.getWidth() == size && cropped.getHeight() == size) {
            return cropped;
        }
        return Bitmap.createScaledBitmap(cropped, size, size, true);
    }

    @NonNull
    public static Bitmap circle(@NonNull Bitmap src) {
        Bitmap sq = squareCrop(src, src.getWidth());
        int s = sq.getWidth();
        Bitmap out = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out);
        Path path = new Path();
        path.addCircle(s / 2f, s / 2f, s / 2f, Path.Direction.CW);
        canvas.clipPath(path);
        canvas.drawBitmap(sq, 0, 0, new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG));
        return out;
    }

    @NonNull
    public static Bitmap composeOtherApiary(
            @NonNull Bitmap hive,
            @Nullable Bitmap face,
            @NonNull Bitmap fallbackFace) {
        int width = hive.getWidth();
        int height = hive.getHeight();
        int faceSize = Math.max(16, Math.round(Math.min(width, height) * 0.40f));
        Bitmap out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        canvas.drawBitmap(hive, 0, 0, paint);
        Bitmap rawFace = face != null ? face : fallbackFace;
        Bitmap circ = circle(squareCrop(rawFace, faceSize));
        float fx = (width - faceSize) / 2f;
        float fy = (height - faceSize) / 2f;
        Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(Math.max(2f, faceSize * 0.08f));
        ring.setColor(0xFFFFFFFF);
        canvas.drawCircle(fx + faceSize / 2f, fy + faceSize / 2f, faceSize / 2f - ring.getStrokeWidth() * 0.5f, ring);
        canvas.drawBitmap(circ, fx, fy, paint);
        return out;
    }

    /** Casa de sede: tejado y muro, con la foto de perfil en el centro. */
    @NonNull
    public static Bitmap composeHeadquarters(@Nullable Bitmap face, int sizePx) {
        int size = Math.max(48, sizePx);
        Bitmap out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out);
        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        stroke.setColor(0xFF5D4037);
        stroke.setStrokeWidth(Math.max(2f, size * 0.035f));

        float roofTop = size * 0.06f;
        float eaves = size * 0.46f;
        float wallLeft = size * 0.16f;
        float wallRight = size * 0.84f;
        float wallBottom = size * 0.94f;
        Path roof = new Path();
        roof.moveTo(size * 0.50f, roofTop);
        roof.lineTo(size * 0.04f, eaves);
        roof.lineTo(size * 0.96f, eaves);
        roof.close();
        fill.setColor(0xFFC9892A);
        canvas.drawPath(roof, fill);
        canvas.drawPath(roof, stroke);

        fill.setColor(0xFFF3E2C7);
        canvas.drawRect(wallLeft, eaves - 1f, wallRight, wallBottom, fill);
        canvas.drawRect(wallLeft, eaves - 1f, wallRight, wallBottom, stroke);

        int faceSize = Math.max(18, Math.round(size * 0.42f));
        Bitmap raw = face != null ? face : placeholderFace(faceSize);
        Bitmap circ = circle(squareCrop(raw, faceSize));
        float fx = (size - faceSize) / 2f;
        float fy = eaves + (wallBottom - eaves - faceSize) * 0.42f;
        Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(Math.max(2f, faceSize * 0.08f));
        ring.setColor(0xFFFFFFFF);
        canvas.drawCircle(fx + faceSize / 2f, fy + faceSize / 2f,
                faceSize / 2f - ring.getStrokeWidth() * 0.5f, ring);
        canvas.drawBitmap(circ, fx, fy, new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG));
        return out;
    }

    @NonNull
    private static Bitmap placeholderFace(int size) {
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(0xFF8D6E63);
        c.drawCircle(size / 2f, size / 2f, size / 2f, p);
        p.setColor(0xFFFFF3E0);
        c.drawCircle(size / 2f, size * 0.40f, size * 0.22f, p);
        c.drawOval(size * 0.22f, size * 0.58f, size * 0.78f, size * 1.05f, p);
        return bmp;
    }
}
