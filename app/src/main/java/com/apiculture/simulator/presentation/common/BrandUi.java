package com.apiculture.simulator.presentation.common;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.apiculture.simulator.R;
import com.apiculture.simulator.data.repository.BrandStore;

import java.util.function.Consumer;

/** Dibuja el emblema de la empresa (círculo de color, aro dorado y dibujo blanco) y su selector. */
public final class BrandUi {
    private static final int[] EMBLEMS = {
            R.drawable.brand_emblem_0, R.drawable.brand_emblem_1, R.drawable.brand_emblem_2,
            R.drawable.brand_emblem_3, R.drawable.brand_emblem_4, R.drawable.brand_emblem_5,
            R.drawable.brand_emblem_6, R.drawable.brand_emblem_7,
    };
    private static final int RING = Color.rgb(214, 170, 60);

    private BrandUi() {
    }

    @NonNull
    public static Bitmap badge(@NonNull Context context, @NonNull BrandStore.Brand brand, int sizePx) {
        Bitmap bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        float r = sizePx / 2f;
        paint.setColor(RING);
        canvas.drawCircle(r, r, r, paint);
        paint.setColor(Color.parseColor(brand.color));
        canvas.drawCircle(r, r, r * 0.88f, paint);
        Bitmap logo = brand.logoBitmap();
        if (logo != null) {
            int inset = Math.round(sizePx * 0.18f);
            canvas.drawBitmap(logo, null, new android.graphics.Rect(inset, inset, sizePx - inset, sizePx - inset),
                    new Paint(Paint.FILTER_BITMAP_FLAG));
            return bmp;
        }
        Drawable glyph = ContextCompat.getDrawable(context, EMBLEMS[brand.emblem]);
        if (glyph != null) {
            int inset = Math.round(sizePx * 0.2f);
            glyph.setBounds(inset, inset, sizePx - inset, sizePx - inset);
            glyph.draw(canvas);
        }
        return bmp;
    }

    /** Rellena las filas de dibujos y colores; cada toque avisa con la marca nueva. */
    public static void bindPicker(@NonNull Context context, @NonNull ImageView preview,
            @NonNull LinearLayout emblems, @NonNull LinearLayout colors,
            @NonNull BrandStore.Brand initial, @NonNull Consumer<BrandStore.Brand> onChange) {
        final BrandStore.Brand[] current = {initial};
        float density = context.getResources().getDisplayMetrics().density;
        int tile = Math.round(44 * density);
        int gap = Math.round(6 * density);
        Runnable[] refresh = new Runnable[1];
        refresh[0] = () -> {
            preview.setImageBitmap(badge(context, current[0], Math.round(64 * density)));
            for (int i = 0; i < emblems.getChildCount(); i++) {
                emblems.getChildAt(i).setAlpha(current[0].logo == null && i == current[0].emblem ? 1f : 0.45f);
            }
            for (int i = 0; i < colors.getChildCount(); i++) {
                colors.getChildAt(i).setAlpha(BrandStore.COLORS[i].equalsIgnoreCase(current[0].color) ? 1f : 0.45f);
            }
        };
        emblems.removeAllViews();
        for (int i = 0; i < BrandStore.EMBLEMS; i++) {
            final int emblem = i;
            ImageView v = new ImageView(context);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(tile, tile);
            lp.setMarginEnd(gap);
            v.setLayoutParams(lp);
            v.setImageBitmap(badge(context, new BrandStore.Brand(emblem, "#5A4632"), tile));
            v.setOnClickListener(x -> {
                current[0] = new BrandStore.Brand(emblem, current[0].color, null);
                refresh[0].run();
                onChange.accept(current[0]);
            });
            emblems.addView(v);
        }
        colors.removeAllViews();
        int dot = Math.round(32 * density);
        for (String color : BrandStore.COLORS) {
            ImageView v = new ImageView(context);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dot, dot);
            lp.setMarginEnd(gap);
            v.setLayoutParams(lp);
            Bitmap b = Bitmap.createBitmap(dot, dot, Bitmap.Config.ARGB_8888);
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setColor(RING);
            new Canvas(b).drawCircle(dot / 2f, dot / 2f, dot / 2f, p);
            p.setColor(Color.parseColor(color));
            new Canvas(b).drawCircle(dot / 2f, dot / 2f, dot * 0.42f, p);
            v.setImageBitmap(b);
            v.setOnClickListener(x -> {
                current[0] = new BrandStore.Brand(current[0].emblem, color, current[0].logo);
                refresh[0].run();
                onChange.accept(current[0]);
            });
            colors.addView(v);
        }
        refresh[0].run();
    }
}
