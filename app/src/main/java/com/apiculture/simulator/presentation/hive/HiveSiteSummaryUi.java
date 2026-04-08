package com.apiculture.simulator.presentation.hive;

import android.content.Context;

import androidx.annotation.DrawableRes;

import com.apiculture.simulator.R;

/**
 * Recursos visuales y textos de ubicación / tipo de miel en el detalle de colmena.
 * Fotos de flora: en su mayoría de Wikimedia Commons (licencias libres; ver {@code res/raw/flora_photos_credits.txt}).
 */
public final class HiveSiteSummaryUi {

    private HiveSiteSummaryUi() {
    }

    @DrawableRes
    public static int floraIllustrationDrawable(String floraType) {
        if (floraType == null) {
            return R.drawable.flora_photo_unknown;
        }
        switch (floraType) {
            case "Bosque":
                return R.drawable.flora_photo_bosque;
            case "Mil flores":
                return R.drawable.flora_photo_mil_flores;
            case "Romero":
                return R.drawable.flora_photo_romero;
            case "Lavanda":
                return R.drawable.flora_photo_lavanda;
            case "Tomillo":
                return R.drawable.flora_photo_tomillo;
            case "Brezo":
                return R.drawable.flora_photo_brezo;
            case "Campo de girasoles":
                return R.drawable.flora_photo_girasoles;
            case "Campo de Colza":
                return R.drawable.flora_photo_colza;
            case "Campo de naranjos":
                return R.drawable.flora_photo_naranjos;
            case "Campo de manzanos":
                return R.drawable.flora_photo_manzanos;
            case "Campo de cerezos":
                return R.drawable.flora_photo_cerezos;
            case "Campo de perales":
                return R.drawable.flora_photo_perales;
            case "Campo de almendros":
                return R.drawable.flora_photo_almendros;
            default:
                return R.drawable.flora_photo_unknown;
        }
    }

    /** Por debajo de 800 m baja; 800–1500 m media; por encima de 1500 m alta. Valores negativos: pendiente. */
    public static String elevationBandLabel(Context ctx, int elevationMeters) {
        if (elevationMeters < 0) {
            return ctx.getString(R.string.hive_elevation_band_pending);
        }
        if (elevationMeters < 800) {
            return ctx.getString(R.string.hive_elevation_band_low);
        }
        if (elevationMeters <= 1500) {
            return ctx.getString(R.string.hive_elevation_band_mid);
        }
        return ctx.getString(R.string.hive_elevation_band_high);
    }
}
