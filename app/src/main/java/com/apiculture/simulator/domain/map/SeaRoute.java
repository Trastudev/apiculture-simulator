package com.apiculture.simulator.domain.map;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.apiculture.simulator.domain.game.TranshumanceRules;
import com.apiculture.simulator.domain.parcel.BoundingBox;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Rutas de barco por una red de puntos en el agua. Cualquier puerto enlaza
 * con cualquier otro, rodeando la costa en vez de cruzar tierra en línea recta.
 */
public final class SeaRoute {

    private static final Map<String, double[]> NODES = new HashMap<>();
    private static final Map<String, List<String>> LINKS = new HashMap<>();
    private static final Map<String, String> PORT_GATE = new HashMap<>();
    private static final List<double[][]> LAND = new ArrayList<>();

    static {
        sea("bcn_sea", 41.20, 2.70);
        sea("vlc_sea", 39.25, 0.85);
        sea("ali_sea", 38.10, 0.45);
        sea("ctg_sea", 37.15, -0.75);
        sea("pmi_sea", 39.00, 2.90);
        sea("med_s", 36.45, -2.10);
        sea("alboran", 36.10, -3.90);
        sea("gibraltar", 35.88, -5.70);
        sea("cadiz_sea", 36.30, -6.85);
        sea("lisboa_sea", 38.55, -9.80);
        sea("vincent_sea", 36.85, -9.45);
        sea("algarve_sea", 36.45, -8.10);
        sea("coruna_sea", 43.50, -9.40);
        sea("gijon_sea", 43.85, -5.90);
        sea("biscay", 44.00, -3.40);
        sea("ss_sea", 43.60, -2.15);
        sea("morocco", 32.80, -10.60);
        sea("canaries", 28.20, -17.00);
        sea("dakar", 14.50, -20.40);
        sea("guinea", 5.50, -17.00);
        sea("equator", -1.50, -8.50);
        sea("angola", -12.50, 7.20);
        sea("namibia", -24.50, 11.20);
        sea("cape_sw", -35.40, 17.20);
        sea("cape_s", -36.10, 20.20);
        sea("agulhas", -36.30, 23.80);
        sea("south_1", -35.70, 26.80);
        sea("gqeberha_sea", -34.40, 26.10);
        sea("east_1", -32.90, 29.20);
        sea("durban_sea", -30.20, 31.80);
        sea("richards_sea", -28.95, 32.70);
        sea("maputo", -26.90, 34.00);
        sea("channel_s", -25.90, 37.40);
        sea("channel_m", -20.80, 39.00);
        sea("channel_n", -16.40, 41.80);
        sea("toliara_sea", -23.50, 43.20);
        sea("mahajanga_off", -15.10, 43.80);
        sea("mahajanga_sea", -15.60, 45.70);
        sea("south_mdg", -26.40, 44.80);
        sea("tolagnaro_sea", -26.10, 47.40);
        sea("east_mdg_s", -22.20, 48.20);
        sea("toamasina_sea", -18.30, 50.05);
        sea("east_mdg_n", -15.40, 51.15);
        sea("antsiranana_sea", -11.90, 49.90);
        sea("north_mdg", -11.60, 46.50);
        sea("capetown_sea", -34.40, 18.15);
        sea("saldanha_sea", -33.10, 17.55);

        link("bcn_sea", "vlc_sea");
        link("vlc_sea", "ali_sea");
        link("ali_sea", "ctg_sea");
        link("ctg_sea", "med_s");
        link("med_s", "alboran");
        link("alboran", "gibraltar");
        link("gibraltar", "cadiz_sea");
        link("pmi_sea", "vlc_sea");
        link("ss_sea", "biscay");
        link("biscay", "gijon_sea");
        link("gijon_sea", "coruna_sea");
        link("coruna_sea", "lisboa_sea");
        link("lisboa_sea", "vincent_sea");
        link("vincent_sea", "algarve_sea");
        link("algarve_sea", "cadiz_sea");
        link("cadiz_sea", "morocco");
        link("morocco", "canaries");
        link("canaries", "dakar");
        link("dakar", "guinea");
        link("guinea", "equator");
        link("equator", "angola");
        link("angola", "namibia");
        link("namibia", "cape_sw");
        link("cape_sw", "cape_s");
        link("cape_s", "agulhas");
        link("agulhas", "south_1");
        link("south_1", "gqeberha_sea");
        link("gqeberha_sea", "east_1");
        link("east_1", "durban_sea");
        link("durban_sea", "richards_sea");
        link("richards_sea", "maputo");
        link("maputo", "channel_s");
        link("channel_s", "channel_m");
        link("channel_m", "channel_n");
        link("cape_sw", "capetown_sea");
        link("capetown_sea", "saldanha_sea");
        link("channel_s", "south_mdg");
        link("south_mdg", "tolagnaro_sea");
        link("tolagnaro_sea", "east_mdg_s");
        link("east_mdg_s", "toamasina_sea");
        link("toamasina_sea", "east_mdg_n");
        link("east_mdg_n", "antsiranana_sea");
        link("antsiranana_sea", "north_mdg");
        link("north_mdg", "channel_n");
        link("channel_s", "toliara_sea");
        link("channel_n", "mahajanga_off");
        link("mahajanga_off", "mahajanga_sea");

        gate("bcn", "bcn_sea");
        gate("vlc", "vlc_sea");
        gate("cartagena", "ctg_sea");
        gate("palma", "pmi_sea");
        gate("cadiz", "cadiz_sea");
        gate("lisboa", "lisboa_sea");
        gate("coruna", "coruna_sea");
        gate("gijon", "gijon_sea");
        gate("sansebastian", "ss_sea");
        gate("capetown", "capetown_sea");
        gate("saldanha", "saldanha_sea");
        gate("gqeberha", "gqeberha_sea");
        gate("durban", "durban_sea");
        gate("richards", "richards_sea");
        gate("toamasina", "toamasina_sea");
        gate("mahajanga", "mahajanga_sea");
        gate("antsiranana", "antsiranana_sea");
        gate("toliara", "toliara_sea");
        gate("tolagnaro", "tolagnaro_sea");

        for (Seaport port : SeaportCatalog.all()) {
            NODES.put(port.id, new double[]{port.lat, port.lng});
            link(port.id, PORT_GATE.get(port.id));
        }

        LAND.add(new double[][]{
                {43.35, -1.75}, {43.48, -2.80}, {43.55, -4.50}, {43.58, -5.75},
                {43.72, -6.90}, {43.55, -8.20}, {43.15, -9.15}, {42.10, -8.90},
                {39.50, -9.45}, {37.20, -8.95}, {37.02, -7.90}, {36.55, -6.15}, {36.02, -5.55},
                {36.72, -2.40}, {37.70, -1.10}, {38.95, -0.20}, {40.55, 0.10},
                {41.35, 1.10}, {41.48, 2.00}, {42.45, 3.05}, {43.32, -1.75}
        });
        LAND.add(new double[][]{
                {39.95, 3.15}, {39.95, 2.30}, {39.26, 2.30}, {39.26, 3.20}
        });
        LAND.add(new double[][]{
                {35.90, -5.30}, {35.40, -6.20}, {33.80, -7.50}, {30.40, -9.70},
                {26.10, -14.50}, {21.00, -17.05}, {14.70, -17.45}, {9.50, -13.70},
                {6.40, -10.60}, {5.20, -4.10}, {6.40, 3.40}, {4.05, 9.70},
                {0.40, 9.45}, {-8.80, 13.20}, {-15.20, 12.15}, {-26.60, 15.15},
                {-34.35, 18.45}, {-34.85, 20.05}, {-34.05, 25.65}, {-30.00, 31.05},
                {-26.00, 32.70}, {-19.80, 35.00}, {-15.00, 40.60}, {-10.50, 40.40},
                {5.00, 40.00}, {12.00, 43.50}, {31.20, 30.00}, {32.50, 12.00},
                {37.10, 10.00}, {36.90, 3.10}, {35.80, -0.60}, {35.40, -2.40},
                {35.80, -5.40}
        });
        LAND.add(new double[][]{
                {-12.15, 49.25}, {-15.50, 50.40}, {-18.15, 49.50}, {-22.20, 48.00},
                {-25.05, 47.05}, {-25.40, 45.80}, {-22.50, 44.40}, {-16.00, 45.40},
                {-13.80, 48.60}, {-12.15, 49.25}
        });
    }

    private SeaRoute() {
    }

    @NonNull
    public static List<double[]> trace(@NonNull Seaport from, @NonNull Seaport to) {
        if (from.id.equals(to.id)) {
            return Collections.singletonList(new double[]{from.lat, from.lng});
        }
        Map<String, Double> dist = new HashMap<>();
        Map<String, String> prev = new HashMap<>();
        PriorityQueue<String> queue = new PriorityQueue<>((a, b) ->
                Double.compare(dist.getOrDefault(a, Double.MAX_VALUE), dist.getOrDefault(b, Double.MAX_VALUE)));
        dist.put(from.id, 0.0);
        queue.add(from.id);
        while (!queue.isEmpty()) {
            String at = queue.poll();
            if (at.equals(to.id)) {
                break;
            }
            double base = dist.getOrDefault(at, Double.MAX_VALUE);
            List<String> next = LINKS.get(at);
            if (next == null) {
                continue;
            }
            double[] here = NODES.get(at);
            for (String hop : next) {
                double[] there = NODES.get(hop);
                double step = TranshumanceRules.haversineKm(here[0], here[1], there[0], there[1]);
                double score = base + step;
                if (score + 1e-6 < dist.getOrDefault(hop, Double.MAX_VALUE)) {
                    dist.put(hop, score);
                    prev.put(hop, at);
                    queue.remove(hop);
                    queue.add(hop);
                }
            }
        }
        List<String> ids = new ArrayList<>();
        String cursor = to.id;
        if (!prev.containsKey(cursor) && !cursor.equals(from.id)) {
            ids.add(from.id);
            ids.add(to.id);
        } else {
            while (cursor != null) {
                ids.add(cursor);
                if (cursor.equals(from.id)) {
                    break;
                }
                cursor = prev.get(cursor);
            }
            Collections.reverse(ids);
        }
        List<double[]> pts = new ArrayList<>();
        for (String id : ids) {
            double[] p = NODES.get(id);
            if (p != null) {
                pts.add(new double[]{p[0], p[1]});
            }
        }
        return pts;
    }

    public static double km(@NonNull Seaport from, @NonNull Seaport to) {
        return pathKm(trace(from, to));
    }

    /**
     * Kilómetros del trazado que caen dentro del mapa de salida o del de llegada.
     * El océano entre ambos no se ve y no cuenta para el tiempo.
     */
    public static double visibleKm(@NonNull List<double[]> points,
            @Nullable PlayableMapRegion origin, @Nullable PlayableMapRegion dest) {
        BoundingBox a = origin != null ? origin.box() : null;
        BoundingBox b = dest != null ? dest.box() : null;
        double sum = 0.0;
        for (int i = 1; i < points.size(); i++) {
            double km = TranshumanceRules.haversineKm(
                    points.get(i - 1)[0], points.get(i - 1)[1], points.get(i)[0], points.get(i)[1]);
            sum += km * visibleFraction(points.get(i - 1), points.get(i), a, b);
        }
        return sum;
    }

    /** Avanza solo por el agua visible. El hueco entre mapas se salta. */
    @NonNull
    public static double[] alongVisible(@NonNull List<double[]> pts, double t,
            @Nullable String fromPortId, @Nullable String toPortId) {
        t = Math.max(0.0, Math.min(1.0, t));
        if (pts.isEmpty()) {
            return new double[]{0, 0};
        }
        if (pts.size() == 1 || t <= 0) {
            return pts.get(0);
        }
        Seaport from = SeaportCatalog.byId(fromPortId);
        Seaport to = SeaportCatalog.byId(toPortId);
        BoundingBox a = from != null ? from.region.box() : null;
        BoundingBox b = to != null ? to.region.box() : null;
        int n = pts.size();
        double[] cum = new double[n];
        for (int i = 1; i < n; i++) {
            double km = TranshumanceRules.haversineKm(pts.get(i - 1)[0], pts.get(i - 1)[1], pts.get(i)[0], pts.get(i)[1]);
            cum[i] = cum[i - 1] + km * visibleFraction(pts.get(i - 1), pts.get(i), a, b);
        }
        double total = cum[n - 1];
        if (total <= 1e-6) {
            return pts.get(n - 1);
        }
        double target = t * total;
        for (int i = 1; i < n; i++) {
            if (cum[i] + 1e-9 < target) {
                continue;
            }
            double span = cum[i] - cum[i - 1];
            double u = span > 1e-9 ? (target - cum[i - 1]) / span : 1.0;
            u = Math.max(0.0, Math.min(1.0, u));
            double[] p = pts.get(i - 1);
            double[] q = pts.get(i);
            return new double[]{p[0] + (q[0] - p[0]) * u, p[1] + (q[1] - p[1]) * u};
        }
        return pts.get(n - 1);
    }

    /** Km visibles ya recorridos cuando el progreso lineal del trazado completo es {@code tFull}. */
    public static double visibleKmBefore(@NonNull List<double[]> points, double tFull,
            @Nullable PlayableMapRegion origin, @Nullable PlayableMapRegion dest) {
        tFull = Math.max(0.0, Math.min(1.0, tFull));
        BoundingBox a = origin != null ? origin.box() : null;
        BoundingBox b = dest != null ? dest.box() : null;
        double full = pathKm(points);
        if (full <= 1e-6) {
            return 0.0;
        }
        double target = tFull * full;
        double walked = 0.0;
        double visible = 0.0;
        for (int i = 1; i < points.size(); i++) {
            double[] p = points.get(i - 1);
            double[] q = points.get(i);
            double km = TranshumanceRules.haversineKm(p[0], p[1], q[0], q[1]);
            double frac = visibleFraction(p, q, a, b);
            if (walked + km >= target) {
                double take = Math.max(0.0, target - walked);
                visible += take * frac;
                break;
            }
            walked += km;
            visible += km * frac;
        }
        return visible;
    }

    private static double visibleFraction(@NonNull double[] a, @NonNull double[] b,
            @Nullable BoundingBox origin, @Nullable BoundingBox dest) {
        int samples = 8;
        int inside = 0;
        for (int s = 0; s <= samples; s++) {
            double u = s / (double) samples;
            double lat = a[0] + (b[0] - a[0]) * u;
            double lng = a[1] + (b[1] - a[1]) * u;
            if ((origin != null && origin.containsLatLon(lat, lng))
                    || (dest != null && dest.containsLatLon(lat, lng))) {
                inside++;
            }
        }
        return inside / (double) (samples + 1);
    }

    public static double pathKm(@NonNull List<double[]> points) {
        double sum = 0.0;
        for (int i = 1; i < points.size(); i++) {
            double[] a = points.get(i - 1);
            double[] b = points.get(i);
            sum += TranshumanceRules.haversineKm(a[0], a[1], b[0], b[1]);
        }
        return sum;
    }

    /** El tramo pisa tierra lejos de los puertos de salida y llegada. */
    public static boolean crossesLand(@NonNull List<double[]> path) {
        return landHit(path) != null;
    }

    @androidx.annotation.Nullable
    private static String landHit(@NonNull List<double[]> path) {
        if (path.size() < 2) {
            return null;
        }
        double[] start = path.get(0);
        double[] end = path.get(path.size() - 1);
        for (int i = 1; i < path.size(); i++) {
            double[] a = path.get(i - 1);
            double[] b = path.get(i);
            for (int step = 1; step <= 9; step++) {
                double t = step / 10.0;
                double lat = a[0] + (b[0] - a[0]) * t;
                double lng = a[1] + (b[1] - a[1]) * t;
                if (TranshumanceRules.haversineKm(lat, lng, start[0], start[1]) < 45
                        || TranshumanceRules.haversineKm(lat, lng, end[0], end[1]) < 45) {
                    continue;
                }
                if (onLand(lat, lng)) {
                    return String.format(java.util.Locale.US, "%.2f, %.2f", lat, lng);
                }
            }
        }
        return null;
    }

    private static boolean onLand(double lat, double lng) {
        for (double[][] polygon : LAND) {
            if (inside(lat, lng, polygon)) {
                return true;
            }
        }
        return false;
    }

    private static boolean inside(double lat, double lng, @NonNull double[][] polygon) {
        boolean in = false;
        for (int i = 0, j = polygon.length - 1; i < polygon.length; j = i++) {
            double yi = polygon[i][0];
            double xi = polygon[i][1];
            double yj = polygon[j][0];
            double xj = polygon[j][1];
            if ((yi > lat) != (yj > lat)
                    && lng < (xj - xi) * (lat - yi) / (yj - yi) + xi) {
                in = !in;
            }
        }
        return in;
    }

    private static void sea(@NonNull String id, double lat, double lng) {
        NODES.put(id, new double[]{lat, lng});
    }

    private static void link(@NonNull String a, @NonNull String b) {
        LINKS.computeIfAbsent(a, key -> new ArrayList<>()).add(b);
        LINKS.computeIfAbsent(b, key -> new ArrayList<>()).add(a);
    }

    private static void gate(@NonNull String portId, @NonNull String seaId) {
        PORT_GATE.put(portId, seaId);
    }
}
