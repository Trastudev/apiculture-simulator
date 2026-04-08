package com.apiculture.simulator.domain.parcel;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Máscara de tierra a partir de GeoJSON {@code FeatureCollection} con polígonos de tierra
 * (p. ej. Natural Earth <code>ne_10m_land.geojson</code> o <code>ne_110m_land.geojson</code>, dominio público).
 * <p>
 * Soporta {@code Polygon} y {@code MultiPolygon} con agujeros (anillos interiores).
 * Usa rejilla fija lon/lat para acotar polígonos candidatos en {@link #isLand};
 * el océano suele resolver en O(1) sin ray casting.
 */
public final class GeoJsonLandMask implements LandMask {

    private static final double CELL_LON_DEG = 15.0;
    private static final double CELL_LAT_DEG = 15.0;

    private final List<PolygonRings> polygons;
    private final int nLon;
    private final int nLat;
    /** {@code cellPolys[iy * nLon + ix]} = índices de polígono en esa celda. */
    private final int[][] cellPolys;

    private GeoJsonLandMask(List<PolygonRings> polygons) {
        this.polygons = polygons;
        this.nLon = (int) Math.ceil(360.0 / CELL_LON_DEG);
        this.nLat = (int) Math.ceil(180.0 / CELL_LAT_DEG);
        this.cellPolys = buildCellIndex(polygons, nLon, nLat);
    }

    private static int[][] buildCellIndex(List<PolygonRings> polys, int nLon, int nLat) {
        int nCells = nLon * nLat;
        ArrayList<ArrayList<Integer>> buckets = new ArrayList<>(nCells);
        for (int i = 0; i < nCells; i++) {
            buckets.add(new ArrayList<>());
        }
        for (int pi = 0; pi < polys.size(); pi++) {
            PolygonRings pr = polys.get(pi);
            int iy0 = latDegToCellIy(pr.minLat, nLat);
            int iy1 = latDegToCellIy(pr.maxLat, nLat);
            if (pr.minLon <= pr.maxLon) {
                int ix0 = lonDegToCellIx(pr.minLon, nLon);
                int ix1 = lonDegToCellIx(pr.maxLon, nLon);
                for (int iy = iy0; iy <= iy1; iy++) {
                    int row = iy * nLon;
                    for (int ix = ix0; ix <= ix1; ix++) {
                        buckets.get(row + ix).add(pi);
                    }
                }
            } else {
                for (int iy = iy0; iy <= iy1; iy++) {
                    int row = iy * nLon;
                    for (int ix = 0; ix < nLon; ix++) {
                        buckets.get(row + ix).add(pi);
                    }
                }
            }
        }
        int[][] out = new int[nCells][];
        for (int c = 0; c < nCells; c++) {
            ArrayList<Integer> b = buckets.get(c);
            int m = b.size();
            out[c] = new int[m];
            for (int j = 0; j < m; j++) {
                out[c][j] = b.get(j);
            }
        }
        return out;
    }

    private static int lonDegToCellIx(double lonDeg, int nLon) {
        int ix = (int) Math.floor((lonDeg + 180.0) / CELL_LON_DEG);
        return clamp(ix, 0, nLon - 1);
    }

    private static int latDegToCellIy(double latDeg, int nLat) {
        int iy = (int) Math.floor((latDeg + 90.0) / CELL_LAT_DEG);
        return clamp(iy, 0, nLat - 1);
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    public static GeoJsonLandMask fromUtf8Json(String json) throws org.json.JSONException {
        JSONObject root = new JSONObject(json);
        List<PolygonRings> polys = new ArrayList<>();
        String type = root.optString("type", "");
        if ("FeatureCollection".equals(type)) {
            JSONArray feats = root.getJSONArray("features");
            for (int i = 0; i < feats.length(); i++) {
                JSONObject f = feats.getJSONObject(i);
                JSONObject geom = f.optJSONObject("geometry");
                if (geom != null) {
                    collectPolygons(geom, polys);
                }
            }
        } else if ("Feature".equals(type)) {
            JSONObject geom = root.optJSONObject("geometry");
            if (geom != null) {
                collectPolygons(geom, polys);
            }
        } else {
            collectPolygons(root, polys);
        }
        return new GeoJsonLandMask(polys);
    }

    public static GeoJsonLandMask fromInputStream(InputStream in) throws IOException, org.json.JSONException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] b = new byte[8192];
        int n;
        while ((n = in.read(b)) != -1) {
            buf.write(b, 0, n);
        }
        return fromUtf8Json(new String(buf.toByteArray(), StandardCharsets.UTF_8));
    }

    private static void collectPolygons(JSONObject geometry, List<PolygonRings> out) throws org.json.JSONException {
        String t = geometry.getString("type");
        if ("Polygon".equals(t)) {
            JSONArray coords = geometry.getJSONArray("coordinates");
            out.add(parsePolygonCoords(coords));
        } else if ("MultiPolygon".equals(t)) {
            JSONArray polys = geometry.getJSONArray("coordinates");
            for (int p = 0; p < polys.length(); p++) {
                out.add(parsePolygonCoords(polys.getJSONArray(p)));
            }
        }
    }

    private static PolygonRings parsePolygonCoords(JSONArray rings) throws org.json.JSONException {
        List<double[]> outer = ringToPoints(rings.getJSONArray(0));
        List<List<double[]>> holes = new ArrayList<>();
        for (int r = 1; r < rings.length(); r++) {
            holes.add(ringToPoints(rings.getJSONArray(r)));
        }
        return new PolygonRings(outer, holes);
    }

    private static List<double[]> ringToPoints(JSONArray ring) throws org.json.JSONException {
        List<double[]> pts = new ArrayList<>(ring.length());
        for (int i = 0; i < ring.length(); i++) {
            JSONArray p = ring.getJSONArray(i);
            double lon = p.getDouble(0);
            double lat = p.getDouble(1);
            pts.add(new double[]{lat, lon});
        }
        return pts;
    }

    @Override
    public boolean isLand(double latDeg, double lonDeg) {
        int ix = lonDegToCellIx(lonDeg, nLon);
        int iy = latDegToCellIy(latDeg, nLat);
        int[] cand = cellPolys[iy * nLon + ix];
        for (int idx : cand) {
            if (polygons.get(idx).contains(latDeg, lonDeg)) {
                return true;
            }
        }
        return false;
    }

    private static final class PolygonRings {
        final double minLat;
        final double maxLat;
        final double minLon;
        final double maxLon;
        final List<double[]> outer;
        final List<List<double[]>> holes;

        PolygonRings(List<double[]> outer, List<List<double[]>> holes) {
            this.outer = outer;
            this.holes = holes;
            double minL = Double.POSITIVE_INFINITY;
            double maxL = Double.NEGATIVE_INFINITY;
            double minLo = Double.POSITIVE_INFINITY;
            double maxLo = Double.NEGATIVE_INFINITY;
            for (double[] p : outer) {
                double la = p[0];
                double lo = p[1];
                minL = Math.min(minL, la);
                maxL = Math.max(maxL, la);
                minLo = Math.min(minLo, lo);
                maxLo = Math.max(maxLo, lo);
            }
            this.minLat = minL;
            this.maxLat = maxL;
            this.minLon = minLo;
            this.maxLon = maxLo;
        }

        boolean contains(double lat, double lon) {
            if (lat < minLat || lat > maxLat || lon < minLon || lon > maxLon) {
                return false;
            }
            if (!pointInPolygon(lat, lon, outer)) {
                return false;
            }
            for (List<double[]> hole : holes) {
                if (pointInPolygon(lat, lon, hole)) {
                    return false;
                }
            }
            return true;
        }
    }

    /** Ray casting; ring: [lat,lon] puntos. */
    private static boolean pointInPolygon(double lat, double lon, List<double[]> ring) {
        boolean inside = false;
        int n = ring.size();
        if (n < 3) {
            return false;
        }
        for (int i = 0, j = n - 1; i < n; j = i++) {
            double yi = ring.get(i)[0];
            double xi = ring.get(i)[1];
            double yj = ring.get(j)[0];
            double xj = ring.get(j)[1];
            boolean intersect = (yi > lat) != (yj > lat)
                    && lon < (xj - xi) * (lat - yi) / (yj - yi + 1e-30) + xi;
            if (intersect) {
                inside = !inside;
            }
        }
        return inside;
    }
}
