package com.apiculture.simulator.domain.parcel;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

/**
 * Serialización cercana al formato pedido para multijugador / API.
 * Los anillos usan [lat, lon] como en el ejemplo del proyecto (GeoJSON estándar sería lon, lat).
 */
public final class HexParcelJson {

    private HexParcelJson() {
    }

    public static JSONObject toJsonObject(HexParcel p) throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", p.id);
        JSONArray ring = new JSONArray();
        for (int i = 0; i < p.polygonLatLon.length; i++) {
            JSONArray pt = new JSONArray();
            pt.put(p.polygonLatLon[i][0]);
            pt.put(p.polygonLatLon[i][1]);
            ring.put(pt);
        }
        o.put("polygon", ring);
        JSONArray cen = new JSONArray();
        cen.put(p.centroidLat);
        cen.put(p.centroidLon);
        o.put("centroid", cen);
        o.put("area_km2", round3(p.areaKm2));
        o.put("coastal", p.coastal);
        if (p.maxElevationMeters != null) {
            o.put("max_elevation_m", p.maxElevationMeters);
        }
        return o;
    }

    public static JSONArray toJsonArray(List<HexParcel> parcels) throws JSONException {
        JSONArray arr = new JSONArray();
        for (HexParcel p : parcels) {
            arr.put(toJsonObject(p));
        }
        return arr;
    }

    private static double round3(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }
}
