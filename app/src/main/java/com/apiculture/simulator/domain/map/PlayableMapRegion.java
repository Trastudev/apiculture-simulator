package com.apiculture.simulator.domain.map;

import com.apiculture.simulator.domain.parcel.BoundingBox;
import com.apiculture.simulator.domain.parcel.IberiaBounds;
import com.apiculture.simulator.domain.parcel.SouthAfricaBounds;

import androidx.annotation.Nullable;

/**
 * Mapas jugables: la cámara y la malla hexagonal se limitan a una región para no ver
 * el resto del mundo (p. ej. Alemania desde Iberia).
 */
public enum PlayableMapRegion {
    IBERIA,
    SOUTH_AFRICA;

    public String prefsValue() {
        return this == SOUTH_AFRICA ? "za" : "iberia";
    }

    public static PlayableMapRegion fromPrefsValue(@Nullable String raw) {
        if (raw != null && (raw.equals("za") || raw.equals("south_africa"))) {
            return SOUTH_AFRICA;
        }
        return IBERIA;
    }

    public BoundingBox box() {
        return this == SOUTH_AFRICA ? SouthAfricaBounds.BOX : IberiaBounds.BOX;
    }

    /** Área objetivo del hexágono: en Sudáfrica el doble que en Iberia (70 km²). */
    public double hexTargetAreaKm2() {
        return this == SOUTH_AFRICA ? 140.0 : 70.0;
    }

    public String hexPrefix() {
        return this == SOUTH_AFRICA ? "za" : "iberia";
    }

    public String overlaySubdir() {
        return this == SOUTH_AFRICA ? "za_hex" : "iberia_hex";
    }

    public String overlayAssetPath() {
        return overlaySubdir() + "/overlay.json";
    }

    public double gridAnchorLat() {
        return this == SOUTH_AFRICA ? -28.5 : 40.0;
    }

    public double gridAnchorLon() {
        return this == SOUTH_AFRICA ? 24.8 : -3.0;
    }

    /** Zoom mínimo: la pantalla no cabe Europa / todo África. */
    public float minZoom() {
        return this == SOUTH_AFRICA ? 5.55f : 5.8f;
    }

    public float defaultZoom() {
        return 6.4f;
    }

    public double defaultLookLat() {
        return this == SOUTH_AFRICA ? -33.92 : 40.42;
    }

    public double defaultLookLon() {
        return this == SOUTH_AFRICA ? 18.42 : -3.70;
    }

    public static PlayableMapRegion fromHexId(@Nullable String hexId) {
        if (hexId != null && (hexId.startsWith("hex_za_") || hexId.startsWith("za_"))) {
            return SOUTH_AFRICA;
        }
        return IBERIA;
    }

    @Nullable
    public static PlayableMapRegion containing(double lat, double lon) {
        if (SOUTH_AFRICA.box().containsLatLon(lat, lon)) {
            return SOUTH_AFRICA;
        }
        if (IBERIA.box().containsLatLon(lat, lon)) {
            return IBERIA;
        }
        return null;
    }

    public boolean containsHive(double lat, double lon) {
        return box().containsLatLon(lat, lon);
    }
}
