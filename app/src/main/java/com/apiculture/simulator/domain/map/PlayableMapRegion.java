package com.apiculture.simulator.domain.map;

import com.apiculture.simulator.domain.parcel.BoundingBox;
import com.apiculture.simulator.domain.parcel.IberiaBounds;
import com.apiculture.simulator.domain.parcel.MadagascarBounds;
import com.apiculture.simulator.domain.parcel.SouthAfricaBounds;

import androidx.annotation.Nullable;

/**
 * Mapas jugables: la cámara y la malla hexagonal se limitan a una región.
 */
public enum PlayableMapRegion {
    IBERIA,
    SOUTH_AFRICA,
    MADAGASCAR;

    public String prefsValue() {
        switch (this) {
            case SOUTH_AFRICA:
                return "za";
            case MADAGASCAR:
                return "mdg";
            case IBERIA:
            default:
                return "iberia";
        }
    }

    public static PlayableMapRegion fromPrefsValue(@Nullable String raw) {
        if (raw != null) {
            String v = raw.trim().toLowerCase();
            if ("za".equals(v) || "south_africa".equals(v)) {
                return SOUTH_AFRICA;
            }
            if ("mdg".equals(v) || "madagascar".equals(v)) {
                return MADAGASCAR;
            }
        }
        return IBERIA;
    }

    public BoundingBox box() {
        switch (this) {
            case SOUTH_AFRICA:
                return SouthAfricaBounds.BOX;
            case MADAGASCAR:
                return MadagascarBounds.BOX;
            case IBERIA:
            default:
                return IberiaBounds.BOX;
        }
    }

    public double hexTargetAreaKm2() {
        switch (this) {
            case SOUTH_AFRICA:
                return 140.0;
            case MADAGASCAR:
                return 90.0;
            case IBERIA:
            default:
                return 70.0;
        }
    }

    public String hexPrefix() {
        switch (this) {
            case SOUTH_AFRICA:
                return "za";
            case MADAGASCAR:
                return "mdg";
            case IBERIA:
            default:
                return "iberia";
        }
    }

    public String overlaySubdir() {
        switch (this) {
            case SOUTH_AFRICA:
                return "za_hex";
            case MADAGASCAR:
                return "mdg_hex";
            case IBERIA:
            default:
                return "iberia_hex";
        }
    }

    public String overlayAssetPath() {
        return overlaySubdir() + "/overlay.json";
    }

    public double gridAnchorLat() {
        switch (this) {
            case SOUTH_AFRICA:
                return -28.5;
            case MADAGASCAR:
                return -19.0;
            case IBERIA:
            default:
                return 40.0;
        }
    }

    public double gridAnchorLon() {
        switch (this) {
            case SOUTH_AFRICA:
                return 24.8;
            case MADAGASCAR:
                return 46.8;
            case IBERIA:
            default:
                return -3.0;
        }
    }

    public float minZoom() {
        switch (this) {
            case SOUTH_AFRICA:
                return 8.05f;
            case MADAGASCAR:
                return 6.85f;
            case IBERIA:
            default:
                return 8.25f;
        }
    }

    public float defaultZoom() {
        switch (this) {
            case SOUTH_AFRICA:
                return 8.2f;
            case MADAGASCAR:
                return 7.35f;
            case IBERIA:
            default:
                return 8.4f;
        }
    }

    public double defaultLookLat() {
        switch (this) {
            case SOUTH_AFRICA:
                return -33.92;
            case MADAGASCAR:
                return -18.88;
            case IBERIA:
            default:
                return 40.42;
        }
    }

    public double defaultLookLon() {
        switch (this) {
            case SOUTH_AFRICA:
                return 18.42;
            case MADAGASCAR:
                return 47.51;
            case IBERIA:
            default:
                return -3.70;
        }
    }

    public static PlayableMapRegion fromHexId(@Nullable String hexId) {
        if (hexId != null) {
            if (hexId.startsWith("hex_za_") || hexId.startsWith("za_")) {
                return SOUTH_AFRICA;
            }
            if (hexId.startsWith("hex_mdg_") || hexId.startsWith("mdg_")) {
                return MADAGASCAR;
            }
        }
        return IBERIA;
    }

    @Nullable
    public static PlayableMapRegion containing(double lat, double lon) {
        if (MADAGASCAR.box().containsLatLon(lat, lon) && MadagascarBounds.keepCentroid(lat, lon)) {
            return MADAGASCAR;
        }
        if (SOUTH_AFRICA.box().containsLatLon(lat, lon)) {
            return SOUTH_AFRICA;
        }
        if (IBERIA.box().containsLatLon(lat, lon) && IberiaBounds.keepCentroid(lat, lon)) {
            return IBERIA;
        }
        return null;
    }

    /** Regiones con paquete de carreteras descargable (GraphHopper). */
    public static PlayableMapRegion[] roadGraphRegions() {
        return new PlayableMapRegion[]{IBERIA, SOUTH_AFRICA, MADAGASCAR};
    }

    public boolean hasRoadGraph() {
        return this == IBERIA || this == SOUTH_AFRICA || this == MADAGASCAR;
    }

    public String roadGraphSubdir() {
        switch (this) {
            case SOUTH_AFRICA:
                return "routing-graph/za";
            case MADAGASCAR:
                return "routing-graph/madagascar";
            case IBERIA:
            default:
                return "routing-graph/iberia";
        }
    }

    public String roadGraphArchiveName() {
        switch (this) {
            case SOUTH_AFRICA:
                return "za-car-lite.tar.gz";
            case MADAGASCAR:
                return "madagascar-car-lite.tar.gz";
            case IBERIA:
            default:
                return "iberia-car-lite.tar.gz";
        }
    }

    public String roadGraphReleaseUrl() {
        switch (this) {
            case SOUTH_AFRICA:
                return "https://github.com/Trastudev/apiculture-simulator/releases/download/"
                        + "routing-graph-za-v1/za-car-lite.tar.gz";
            case MADAGASCAR:
                return "https://github.com/Trastudev/apiculture-simulator/releases/download/"
                        + "routing-graph-madagascar-v1/madagascar-car-lite.tar.gz";
            case IBERIA:
            default:
                return "https://github.com/Trastudev/apiculture-simulator/releases/download/"
                        + "routing-graph-iberia-v1/iberia-car-lite.tar.gz";
        }
    }

    public int roadGraphNotificationId() {
        if (this == MADAGASCAR) {
            return 48202;
        }
        if (this == SOUTH_AFRICA) {
            return 48203;
        }
        return 48201;
    }

    public boolean containsHive(double lat, double lon) {
        if (this == IBERIA) {
            return IberiaBounds.BOX.containsLatLon(lat, lon) && IberiaBounds.keepCentroid(lat, lon);
        }
        if (this == MADAGASCAR) {
            return MadagascarBounds.BOX.containsLatLon(lat, lon) && MadagascarBounds.keepCentroid(lat, lon);
        }
        return box().containsLatLon(lat, lon);
    }
}
