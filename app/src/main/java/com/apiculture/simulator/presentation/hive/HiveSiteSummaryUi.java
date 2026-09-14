package com.apiculture.simulator.presentation.hive;

import android.content.Context;

import androidx.annotation.DrawableRes;

import com.apiculture.simulator.R;
import com.apiculture.simulator.domain.parcel.HexFlora;

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
        switch (HexFlora.canonicalKey(floraType)) {
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
            case "Castaño":
            case "Eucalipto":
            case "Mielato de encina y roble":
                return R.drawable.flora_photo_bosque;
            case "Neret":
                return R.drawable.flora_photo_brezo;
            case "Arboç":
                return R.drawable.flora_photo_mil_flores;
            case "Fynbos":
                return R.drawable.flora_photo_mil_flores;
            case "Aloe":
                return R.drawable.flora_photo_lavanda;
            case "Macadamia":
                return R.drawable.flora_photo_bosque;
            case "Litchi":
                return R.drawable.flora_photo_naranjos;
            case "Lucerna":
                return R.drawable.flora_photo_colza;
            case "Acacia":
                return R.drawable.flora_photo_bosque;
            default:
                return R.drawable.flora_photo_unknown;
        }
    }

    /**
     * Bote de miel ilustrado según el tipo de flora.
     */
    @DrawableRes
    public static int floraHoneyJarIcon(String floraType) {
        switch (HexFlora.canonicalKey(floraType)) {
            case "Bosque":
                return R.drawable.ic_bosque;
            case "Mil flores":
                return R.drawable.ic_mil_flores;
            case "Romero":
                return R.drawable.ic_romero;
            case "Lavanda":
                return R.drawable.ic_lavanda;
            case "Tomillo":
                return R.drawable.ic_tomillo;
            case "Brezo":
                return R.drawable.ic_brezo;
            case "Campo de girasoles":
                return R.drawable.ic_girasoles;
            case "Campo de Colza":
                return R.drawable.ic_colza;
            case "Campo de naranjos":
                return R.drawable.ic_naranjos;
            case "Campo de manzanos":
                return R.drawable.ic_manzanos;
            case "Campo de cerezos":
                return R.drawable.ic_cerezos;
            case "Campo de perales":
                return R.drawable.ic_perales;
            case "Campo de almendros":
                return R.drawable.ic_almendros;
            case "Castaño":
                return R.drawable.ic_castano;
            case "Eucalipto":
                return R.drawable.ic_eucalipto;
            case "Mielato de encina y roble":
                return R.drawable.ic_mielato;
            case "Neret":
                return R.drawable.ic_neret;
            case "Arboç":
                return R.drawable.ic_arboc;
            case "Fynbos":
                return R.drawable.ic_fynbos;
            case "Aloe":
                return R.drawable.ic_aloe;
            case "Macadamia":
                return R.drawable.ic_macadamia;
            case "Litchi":
                return R.drawable.ic_litchi;
            case "Lucerna":
                return R.drawable.ic_lucerna;
            case "Acacia":
                return R.drawable.ic_acacia;
            default:
                return R.drawable.ic_mil_flores;
        }
    }

    /**
     * Badge circular (círculo crema + flor) para marcar la flora en la colmena del prado.
     */
    @DrawableRes
    public static int floraBadgeIcon(String floraType) {
        switch (HexFlora.canonicalKey(floraType)) {
            case "Bosque":
                return R.drawable.ic_flora_badge_bosque;
            case "Mil flores":
                return R.drawable.ic_flora_badge_mil_flores;
            case "Romero":
                return R.drawable.ic_flora_badge_romero;
            case "Lavanda":
                return R.drawable.ic_flora_badge_lavanda;
            case "Tomillo":
                return R.drawable.ic_flora_badge_tomillo;
            case "Brezo":
                return R.drawable.ic_flora_badge_brezo;
            case "Campo de girasoles":
                return R.drawable.ic_flora_badge_girasoles;
            case "Campo de Colza":
                return R.drawable.ic_flora_badge_colza;
            case "Campo de naranjos":
                return R.drawable.ic_flora_badge_naranjos;
            case "Campo de manzanos":
                return R.drawable.ic_flora_badge_manzanos;
            case "Campo de cerezos":
                return R.drawable.ic_flora_badge_cerezos;
            case "Campo de perales":
                return R.drawable.ic_flora_badge_perales;
            case "Campo de almendros":
                return R.drawable.ic_flora_badge_almendros;
            case "Castaño":
                return R.drawable.ic_flora_badge_castano;
            case "Eucalipto":
                return R.drawable.ic_flora_badge_eucalipto;
            case "Mielato de encina y roble":
                return R.drawable.ic_flora_badge_mielato;
            case "Neret":
                return R.drawable.ic_flora_badge_neret;
            case "Arboç":
                return R.drawable.ic_flora_badge_arboc;
            case "Fynbos":
                return R.drawable.ic_flora_badge_fynbos;
            case "Aloe":
                return R.drawable.ic_flora_badge_aloe;
            case "Macadamia":
                return R.drawable.ic_flora_badge_macadamia;
            case "Litchi":
                return R.drawable.ic_flora_badge_litchi;
            case "Lucerna":
                return R.drawable.ic_flora_badge_lucerna;
            case "Acacia":
                return R.drawable.ic_flora_badge_acacia;
            default:
                return R.drawable.ic_flora_badge_mil_flores;
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
