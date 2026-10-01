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
            case "Campo de lavanda":
            case "Campo de facelia":
                return R.drawable.flora_photo_lavanda;
            case "Tomillo":
                return R.drawable.flora_photo_tomillo;
            case "Brezo":
                return R.drawable.flora_photo_brezo;
            case "Campo de girasoles":
                return R.drawable.flora_photo_girasoles;
            case "Campo de Colza":
            case "Campo de mostaza":
            case "Campo de rabaniza":
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
            case "Campo de trébol":
                return R.drawable.flora_photo_colza;
            case "Acacia":
                return R.drawable.flora_photo_bosque;
            case "Buchu":
                return R.drawable.flora_photo_tomillo;
            case "Protea":
                return R.drawable.flora_photo_lavanda;
            case "Boekenhout":
                return R.drawable.flora_photo_bosque;
            case "Aguacate":
                return R.drawable.flora_photo_naranjos;
            case "Marula":
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
            case "Campo de lavanda":
                return R.drawable.ic_lavanda;
            case "Campo de facelia":
                return R.drawable.ic_facelia;
            case "Tomillo":
                return R.drawable.ic_tomillo;
            case "Brezo":
                return R.drawable.ic_brezo;
            case "Campo de girasoles":
                return R.drawable.ic_girasoles;
            case "Campo de Colza":
                return R.drawable.ic_colza;
            case "Campo de mostaza":
                return R.drawable.ic_mostaza;
            case "Campo de rabaniza":
                return R.drawable.ic_rabaniza;
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
            case "Campo de trébol":
                return R.drawable.ic_trebol;
            case "Acacia":
                return R.drawable.ic_acacia;
            case "Buchu":
                return R.drawable.ic_buchu;
            case "Protea":
                return R.drawable.ic_protea;
            case "Boekenhout":
                return R.drawable.ic_boekenhout;
            case "Aguacate":
                return R.drawable.ic_aguacate;
            case "Marula":
                return R.drawable.ic_marula;
            case "Girofle":
                return R.drawable.ic_girofle;
            case "Ravintsara":
                return R.drawable.ic_ravintsara;
            case "Longose":
                return R.drawable.ic_longose;
            case "Tapia":
                return R.drawable.ic_tapia;
            case "Café":
                return R.drawable.ic_cafe;
            case "Niaouli":
                return R.drawable.ic_niaouli;
            case "Tamarindo":
                return R.drawable.ic_tamarindo;
            case "Baobab":
                return R.drawable.ic_baobab;
            case "Mango":
                return R.drawable.ic_mango;
            case "Mangle":
                return R.drawable.ic_mangle;
            case "Raketa":
                return R.drawable.ic_raketa;
            case "Jujube":
                return R.drawable.ic_jujube;
            case "Sisal":
                return R.drawable.ic_sisal;
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
            case "Campo de lavanda":
                return R.drawable.ic_flora_badge_lavanda;
            case "Campo de facelia":
                return R.drawable.ic_flora_badge_facelia;
            case "Tomillo":
                return R.drawable.ic_flora_badge_tomillo;
            case "Brezo":
                return R.drawable.ic_flora_badge_brezo;
            case "Campo de girasoles":
                return R.drawable.ic_flora_badge_girasoles;
            case "Campo de Colza":
                return R.drawable.ic_flora_badge_colza;
            case "Campo de mostaza":
                return R.drawable.ic_flora_badge_mostaza;
            case "Campo de rabaniza":
                return R.drawable.ic_flora_badge_rabaniza;
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
            case "Campo de trébol":
                return R.drawable.ic_flora_badge_trebol;
            case "Acacia":
                return R.drawable.ic_flora_badge_acacia;
            case "Buchu":
                return R.drawable.ic_flora_badge_buchu;
            case "Protea":
                return R.drawable.ic_flora_badge_protea;
            case "Boekenhout":
                return R.drawable.ic_flora_badge_boekenhout;
            case "Aguacate":
                return R.drawable.ic_flora_badge_aguacate;
            case "Marula":
                return R.drawable.ic_flora_badge_marula;
            case "Girofle":
                return R.drawable.ic_flora_badge_girofle;
            case "Ravintsara":
                return R.drawable.ic_flora_badge_ravintsara;
            case "Longose":
                return R.drawable.ic_flora_badge_longose;
            case "Tapia":
                return R.drawable.ic_flora_badge_tapia;
            case "Café":
                return R.drawable.ic_flora_badge_cafe;
            case "Niaouli":
                return R.drawable.ic_flora_badge_niaouli;
            case "Tamarindo":
                return R.drawable.ic_flora_badge_tamarindo;
            case "Baobab":
                return R.drawable.ic_flora_badge_baobab;
            case "Mango":
                return R.drawable.ic_flora_badge_mango;
            case "Mangle":
                return R.drawable.ic_flora_badge_mangle;
            case "Raketa":
                return R.drawable.ic_flora_badge_raketa;
            case "Jujube":
                return R.drawable.ic_flora_badge_jujube;
            case "Sisal":
                return R.drawable.ic_flora_badge_sisal;
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

    public static String floraLabel(Context context, String floraType) {
        if (floraType == null || floraType.isEmpty()) {
            return "—";
        }
        String key = HexFlora.canonicalKey(floraType);
        switch (key) {
            case "Romero":
                return context.getString(R.string.flora_display_romero);
            case "Tomillo":
                return context.getString(R.string.flora_display_tomillo);
            case "Bosque":
                return context.getString(R.string.flora_display_bosque);
            case "Lavanda":
            case "Campo de lavanda":
                return context.getString(R.string.flora_display_lavanda);
            case "Castaño":
                return context.getString(R.string.flora_display_castano);
            case "Eucalipto":
                return context.getString(R.string.flora_display_eucalipto);
            case "Mielato de encina y roble":
                return context.getString(R.string.flora_display_mielato);
            case "Neret":
                return context.getString(R.string.flora_display_neret);
            case "Arboç":
                return context.getString(R.string.flora_display_arboc);
            case "Fynbos":
                return context.getString(R.string.flora_display_fynbos);
            case "Aloe":
                return context.getString(R.string.flora_display_aloe);
            case "Macadamia":
                return context.getString(R.string.flora_display_macadamia);
            case "Litchi":
                return context.getString(R.string.flora_display_litchi);
            case "Lucerna":
                return context.getString(R.string.flora_display_lucerna);
            case "Acacia":
                return context.getString(R.string.flora_display_acacia);
            case "Buchu":
                return context.getString(R.string.flora_display_buchu);
            case "Protea":
                return context.getString(R.string.flora_display_protea);
            case "Boekenhout":
                return context.getString(R.string.flora_display_boekenhout);
            case "Aguacate":
                return context.getString(R.string.flora_display_aguacate);
            case "Marula":
                return context.getString(R.string.flora_display_marula);
            case "Campo de naranjos":
                return context.getString(R.string.flora_display_naranjos);
            case "Campo de almendros":
                return context.getString(R.string.flora_display_almendros);
            case "Campo de cerezos":
                return context.getString(R.string.flora_display_cerezos);
            case "Campo de manzanos":
                return context.getString(R.string.flora_display_manzanos);
            case "Campo de perales":
                return context.getString(R.string.flora_display_perales);
            case "Campo de Colza":
                return context.getString(R.string.flora_display_colza);
            case "Campo de girasoles":
                return context.getString(R.string.flora_display_girasoles);
            case "Campo de mostaza":
                return context.getString(R.string.flora_display_mostaza);
            case "Campo de trébol":
                return context.getString(R.string.flora_display_trebol);
            case "Campo de facelia":
                return context.getString(R.string.flora_display_facelia);
            case "Campo de rabaniza":
                return context.getString(R.string.flora_display_rabaniza);
            case "Mil flores":
                return context.getString(R.string.flora_display_mil_flores);
            case "Brezo":
                return context.getString(R.string.flora_display_brezo);
            case "Girofle":
                return context.getString(R.string.flora_display_girofle);
            case "Ravintsara":
                return context.getString(R.string.flora_display_ravintsara);
            case "Longose":
                return context.getString(R.string.flora_display_longose);
            case "Tapia":
                return context.getString(R.string.flora_display_tapia);
            case "Café":
                return context.getString(R.string.flora_display_cafe);
            case "Niaouli":
                return context.getString(R.string.flora_display_niaouli);
            case "Tamarindo":
                return context.getString(R.string.flora_display_tamarindo);
            case "Baobab":
                return context.getString(R.string.flora_display_baobab);
            case "Mango":
                return context.getString(R.string.flora_display_mango);
            case "Mangle":
                return context.getString(R.string.flora_display_mangle);
            case "Raketa":
                return context.getString(R.string.flora_display_raketa);
            case "Jujube":
                return context.getString(R.string.flora_display_jujube);
            case "Sisal":
                return context.getString(R.string.flora_display_sisal);
            default:
                return floraType;
        }
    }
}
