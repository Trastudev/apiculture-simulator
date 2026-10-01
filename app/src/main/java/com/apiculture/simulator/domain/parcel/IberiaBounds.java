package com.apiculture.simulator.domain.parcel;

import java.util.ArrayList;
import java.util.List;

/**
 * Península ibérica y Baleares (sin Canarias). Más adelante un selector de país puede sustituir esto
 * por otro {@link BoundingBox} o varias regiones.
 */
public final class IberiaBounds {

    /**
     * Sur en el Estrecho (sin Marruecos). Norte holgado para Galicia (Estaca de Bares ≈ 43,79°).
     * El recorte fino de Francia y África está en {@link #keepHex}.
     */
    public static final BoundingBox BOX = new BoundingBox(
            36.00,
            43.90,
            -9.70,
            4.55);

    /** Por debajo, Magreb occidental (Tánger ≈ 35,76°, Ceuta ≈ 35,89°, Orán ≈ 35,70°). */
    public static final double AFRICA_MAX_LAT = 36.00;

    /**
     * Costa argelina (Argel ≈ 36,75° / 3,06°) entra en el bbox de Iberia.
     * Cartagena ≈ 37,60° queda al norte; Baleares ≈ 38,7°+.
     */
    private static final double ALGERIA_WEST_LON = -1.50;
    /** Por encima de Dellys (≈ 36,91°) y por debajo de Cartagena (≈ 37,60°). */
    private static final double ALGERIA_MAX_LAT = 37.30;

    /**
     * Al oeste, la costa cantábrica es España (hasta ≈ 43,8°). Al este empieza la frontera
     * con Francia (Hendaya ≈ −1,78°).
     */
    private static final double FRANCE_CLIP_WEST_LON = -1.92;

    /**
     * Techo de latitud permitido al este de Hendaya: frontera + ~20–25 km
     * (mejor pasarse un poco a Francia que recortar hexes españoles).
     * Pares lon, maxLat.
     */
    private static final double[] FRANCE_MAX_LAT = {
            -1.92, 43.60,
            -1.78, 43.58,
            -1.40, 43.50,
            -0.70, 43.20,
            -0.20, 43.02,
            0.70, 42.92,
            1.50, 42.88,
            2.20, 42.74,
            3.00, 42.72,
            3.40, 42.74,
            4.55, 42.80
    };

    private IberiaBounds() {
    }

    public static boolean keepCentroid(double lat, double lon) {
        if (isNorthAfrica(lat, lon)) {
            return false;
        }
        if (lon <= FRANCE_CLIP_WEST_LON) {
            return true;
        }
        return lat <= franceMaxLat(lon);
    }

    public static boolean isNorthAfrica(double lat, double lon) {
        if (lat < AFRICA_MAX_LAT) {
            return true;
        }
        return lon > ALGERIA_WEST_LON && lat < ALGERIA_MAX_LAT;
    }

    public static boolean keepHex(HexParcel parcel) {
        if (parcel == null) {
            return false;
        }
        if (parcel.polygonLatLon != null) {
            for (int i = 0; i < parcel.polygonLatLon.length; i++) {
                double[] pt = parcel.polygonLatLon[i];
                if (pt != null && pt.length >= 1 && pt[0] < AFRICA_MAX_LAT) {
                    return false;
                }
            }
        }
        return keepCentroid(parcel.centroidLat, parcel.centroidLon);
    }

    public static List<HexParcel> keepPlayable(List<HexParcel> parcels) {
        if (parcels == null || parcels.isEmpty()) {
            return parcels == null ? new ArrayList<>() : parcels;
        }
        List<HexParcel> out = new ArrayList<>(parcels.size());
        for (int i = 0; i < parcels.size(); i++) {
            HexParcel p = parcels.get(i);
            if (keepHex(p)) {
                out.add(p);
            }
        }
        return out;
    }

    static double franceMaxLat(double lon) {
        if (lon <= FRANCE_MAX_LAT[0]) {
            return FRANCE_MAX_LAT[1];
        }
        int n = FRANCE_MAX_LAT.length / 2;
        for (int i = 0; i < n - 1; i++) {
            double lon0 = FRANCE_MAX_LAT[i * 2];
            double lat0 = FRANCE_MAX_LAT[i * 2 + 1];
            double lon1 = FRANCE_MAX_LAT[(i + 1) * 2];
            double lat1 = FRANCE_MAX_LAT[(i + 1) * 2 + 1];
            if (lon <= lon1) {
                double t = (lon - lon0) / (lon1 - lon0);
                return lat0 + t * (lat1 - lat0);
            }
        }
        return FRANCE_MAX_LAT[FRANCE_MAX_LAT.length - 1];
    }
}
