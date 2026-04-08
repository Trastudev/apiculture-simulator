package com.apiculture.simulator.data.repository;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.apiculture.simulator.domain.parcel.BoundingBox;
import com.apiculture.simulator.domain.parcel.GeoJsonLandMask;
import com.apiculture.simulator.domain.parcel.LandMask;
import com.apiculture.simulator.domain.parcel.RectangleLandMask;

import java.io.IOException;
import java.io.InputStream;

/**
 * Carga una {@link LandMask} desde {@code assets} (Natural Earth, dominio público).
 */
public final class LandMaskAssets {

    /** Tierra 10m: costa más alineada con mapas detallados; APK ~10&nbsp;MB más. */
    public static final String DEFAULT_LAND_GEOJSON_ASSET = "land/ne_10m_land.geojson";
    /** Tierra 110m: muy ligero (~200&nbsp;KB), costa muy simplificada. */
    public static final String LAND_GEOJSON_110M_ASSET = "land/ne_110m_land.geojson";

    private static volatile LandMask defaultLandMask;
    private static final Object defaultLandMaskLock = new Object();

    private LandMaskAssets() {
    }

    /**
     * Carga y memoriza la máscara 10m (o rectángulo ibérico si falla el asset). Seguro multihilo;
     * conviene precargar desde {@link android.app.Application#onCreate()} en un hilo de fondo.
     */
    @NonNull
    public static LandMask getOrLoadDefaultLandMask(Context context) {
        LandMask cached = defaultLandMask;
        if (cached != null) {
            return cached;
        }
        synchronized (defaultLandMaskLock) {
            if (defaultLandMask != null) {
                return defaultLandMask;
            }
            Context app = context.getApplicationContext();
            LandMask loaded = loadGeoJson(app, DEFAULT_LAND_GEOJSON_ASSET);
            if (loaded != null) {
                defaultLandMask = loaded;
            } else {
                defaultLandMask = new RectangleLandMask(new BoundingBox(35.0, 45.0, -10.0, 5.0));
            }
            return defaultLandMask;
        }
    }

    /**
     * @param assetPath ruta bajo {@code assets/} (sin prefijo "assets/")
     * @return {@code null} si el fichero no existe o el parseo falla
     */
    @Nullable
    public static LandMask loadGeoJson(Context context, String assetPath) {
        try (InputStream in = context.getAssets().open(assetPath)) {
            return GeoJsonLandMask.fromInputStream(in);
        } catch (IOException | org.json.JSONException e) {
            return null;
        }
    }
}
