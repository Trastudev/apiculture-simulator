package com.apiculture.simulator.domain.map;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.apiculture.simulator.data.repository.RoutingGraphDownloader;
import com.apiculture.simulator.domain.game.TranshumanceRules;
import com.apiculture.simulator.domain.game.TruckTripRules;
import com.apiculture.simulator.data.repository.LandMaskAssets;
import com.apiculture.simulator.domain.parcel.LandMask;
import com.apiculture.simulator.data.repository.TruckLivePrefs;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopper;
import com.graphhopper.GraphHopperConfig;
import com.graphhopper.ResponsePath;
import com.graphhopper.config.CHProfile;
import com.graphhopper.config.Profile;
import com.graphhopper.jackson.Jackson;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.storage.index.LocationIndexTree;
import com.graphhopper.storage.index.Snap;
import com.graphhopper.util.PointList;
import com.graphhopper.util.shapes.GHPoint;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Enrutado offline con grafos regionales (GraphHopper 10).
 */
public final class LocalGraphHopper {

    public enum Kind {
        OK,
        GRAPH_MISSING,
        LOAD_FALSE,
        LOAD_EXCEPTION,
        ROUTE_ERRORS,
        SNAP_FAIL,
        FEW_POINTS,
        ROUTE_EXCEPTION
    }

    public static final class Diag {
        public final Kind kind;
        public final String detail;

        Diag(@NonNull Kind kind, @Nullable String detail) {
            this.kind = kind;
            this.detail = detail == null ? "" : detail;
        }

        @NonNull
        public String report() {
            return detail;
        }
    }

    private static final String TAG = "LocalGraphHopper";
    private static final String IGNORED_HIGHWAYS =
            "footway,cycleway,path,pedestrian,steps,bridleway,corridor,construction,"
                    + "residential,living_street,service,track";
    private static final String CAR_PROFILE_JSON =
            "{\"name\":\"car\",\"custom_model_files\":[\"truck_lite.json\"]}";
    /** Casillas del índice que se recorren hasta encontrar una vía del camión. */
    private static final int SNAP_REGION_SEARCH = 1000;

    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final Object LOCK = new Object();
    private static final Map<PlayableMapRegion, GraphHopper> hoppers =
            new EnumMap<>(PlayableMapRegion.class);
    @Nullable
    private static volatile Diag lastDiag;

    private LocalGraphHopper() {
    }

    @Nullable
    public static Diag lastDiag() {
        return lastDiag;
    }

    public static void prepareAsync(@NonNull Context context) {
        prepareAsync(context, PlayableMapRegion.IBERIA);
    }

    public static void prepareAsync(@NonNull Context context, @NonNull PlayableMapRegion region) {
        Context app = context.getApplicationContext();
        IO.execute(() -> {
            if (RoutingGraphDownloader.isInstalled(app, region)) {
                ensureLoaded(app, region);
            }
        });
    }

    @NonNull
    public static RoadPath route(@NonNull Context context, double fromLat, double fromLng,
            double toLat, double toLng) {
        Context app = context.getApplicationContext();
        RoadPath local = routeLocal(app, fromLat, fromLng, toLat, toLng);
        if (local != null) {
            local = preferFieldTrack(app, fromLat, fromLng, toLat, toLng, local);
            lastDiag = new Diag(Kind.OK, "puntos=" + local.points.size()
                    + " km=" + local.distanceKm
                    + (local.fieldTrack ? " pista" : ""));
            return local;
        }
        if (lastDiag == null || lastDiag.kind == Kind.OK) {
            lastDiag = new Diag(Kind.ROUTE_ERRORS, graphState(app)
                    + "\nruta local nula sin detalle");
        }
        return landSafeFallback(app, fromLat, fromLng, toLat, toLng);
    }

    @Nullable
    private static PlayableMapRegion regionForRoute(double fromLat, double fromLng,
            double toLat, double toLng) {
        PlayableMapRegion a = PlayableMapRegion.containing(fromLat, fromLng);
        PlayableMapRegion b = PlayableMapRegion.containing(toLat, toLng);
        if (a != null && b != null && a != b) {
            return null;
        }
        PlayableMapRegion r = a != null ? a : b;
        if (r == null || !r.hasRoadGraph()) {
            return null;
        }
        return r;
    }

    /**
     * En Madagascar y Sudáfrica, si la carretera mide al menos
     * {@link LandSafeRoute#FIELD_TRACK_MIN_ROAD_RATIO} veces la recta (el doble, 200 %)
     * y tarda más que esa pista, el camión va campo a través. Si la recta cruza mar,
     * rodea por tierra o se queda en carretera.
     */
    @NonNull
    private static RoadPath preferFieldTrack(@NonNull Context app, double fromLat, double fromLng,
            double toLat, double toLng, @NonNull RoadPath road) {
        PlayableMapRegion region = regionForRoute(fromLat, fromLng, toLat, toLng);
        if (region != PlayableMapRegion.SOUTH_AFRICA && region != PlayableMapRegion.MADAGASCAR) {
            return road;
        }
        double straightKm = TranshumanceRules.haversineKm(fromLat, fromLng, toLat, toLng);
        if (straightKm < 1.0
                || road.distanceKm < straightKm * LandSafeRoute.FIELD_TRACK_MIN_ROAD_RATIO) {
            return road;
        }
        LandMask mask = LandMaskAssets.getOrLoadDefaultLandMask(app);
        RoadPath track = LandSafeRoute.fieldTrackOnLand(mask, fromLat, fromLng, toLat, toLng);
        if (track == null) {
            return road;
        }
        if (TruckTripRules.durationMs(road) <= TruckTripRules.durationMs(track)) {
            return road;
        }
        return track;
    }

    @NonNull
    private static RoadPath landSafeFallback(@NonNull Context app, double fromLat, double fromLng,
            double toLat, double toLng) {
        LandMask mask = LandMaskAssets.getOrLoadDefaultLandMask(app);
        RoadPath track = LandSafeRoute.fieldTrackOnLand(mask, fromLat, fromLng, toLat, toLng);
        return track != null ? track : RoadPath.geodesic(fromLat, fromLng, toLat, toLng);
    }

    private static RoadPath routeLocal(@NonNull Context app, double fromLat, double fromLng,
            double toLat, double toLng) {
        PlayableMapRegion region = regionForRoute(fromLat, fromLng, toLat, toLng);
        if (region == null) {
            lastDiag = new Diag(Kind.GRAPH_MISSING, "sin grafo para este tramo (regiones distintas)");
            return null;
        }
        GraphHopper gh = ensureLoaded(app, region);
        if (gh == null) {
            return null;
        }
        try {
            boolean wantClass = RouteRoadClass.hopperHasRoadClass(gh);
            GHResponse rsp = routeWithDetails(gh, fromLat, fromLng, toLat, toLng, wantClass);
            String firstErr = errorsOf(rsp);
            if (rsp.hasErrors() || rsp.getAll().isEmpty()) {
                Log.w(TAG, "ruta directa: " + firstErr);
                GHResponse snapped = routeFromNearestRoad(gh, fromLat, fromLng, toLat, toLng,
                        wantClass);
                if (snapped == null) {
                    lastDiag = new Diag(Kind.SNAP_FAIL, graphState(app)
                            + "\ncoords " + fromLat + "," + fromLng + " -> " + toLat + "," + toLng
                            + "\nruta: " + firstErr
                            + "\nsnap: no hay vía cerca o índice nulo");
                    return null;
                }
                if (snapped.hasErrors() || snapped.getAll().isEmpty()) {
                    lastDiag = new Diag(Kind.ROUTE_ERRORS, graphState(app)
                            + "\ncoords " + fromLat + "," + fromLng + " -> " + toLat + "," + toLng
                            + "\nruta: " + firstErr
                            + "\nsnap-ruta: " + errorsOf(snapped));
                    return null;
                }
                rsp = snapped;
            }
            ResponsePath best = rsp.getBest();
            PointList pl = best.getPoints();
            if (pl == null || pl.size() < 2) {
                lastDiag = new Diag(Kind.FEW_POINTS, graphState(app)
                        + "\npuntos=" + (pl == null ? "null" : pl.size())
                        + " dist=" + best.getDistance());
                return null;
            }
            List<double[]> pts = new ArrayList<>(pl.size());
            for (int i = 0; i < pl.size(); i++) {
                pts.add(new double[]{pl.getLat(i), pl.getLon(i)});
            }
            double km = best.getDistance() / 1000.0;
            if (km <= 0) {
                km = RoadPath.pathKm(pts);
            }
            String kinds = RouteRoadClass.classify(gh, best, pts.size());
            return RoadPath.withEndpoints(
                    new RoadPath(pts, EncodedPolyline.encode(pts), km, kinds),
                    fromLat, fromLng, toLat, toLng);
        } catch (Throwable e) {
            lastDiag = new Diag(Kind.ROUTE_EXCEPTION, graphState(app) + "\n" + stackOf(e));
            Log.w(TAG, "ruta local falló", e);
            return null;
        }
    }

    @NonNull
    private static GHRequest request(double fromLat, double fromLng, double toLat, double toLng,
            boolean streetName, boolean roadClass) {
        GHRequest req = new GHRequest(fromLat, fromLng, toLat, toLng).setProfile("car");
        if (streetName || roadClass) {
            List<String> details = new ArrayList<>(2);
            if (streetName) {
                details.add("street_name");
            }
            if (roadClass) {
                details.add("road_class");
            }
            req.setPathDetails(details);
        }
        return req;
    }

    private static boolean routeFailed(@Nullable GHResponse rsp) {
        return rsp == null || rsp.hasErrors() || rsp.getAll().isEmpty();
    }

    @NonNull
    private static GHResponse routeWithDetails(@NonNull GraphHopper gh,
            double fromLat, double fromLng, double toLat, double toLng, boolean roadClass) {
        GHResponse rsp = gh.route(request(fromLat, fromLng, toLat, toLng, true, roadClass));
        if (!routeFailed(rsp)) {
            return rsp;
        }
        rsp = gh.route(request(fromLat, fromLng, toLat, toLng, true, false));
        if (!routeFailed(rsp)) {
            return rsp;
        }
        return gh.route(request(fromLat, fromLng, toLat, toLng, false, false));
    }

    private static GHResponse routeFromNearestRoad(@NonNull GraphHopper gh,
            double fromLat, double fromLng, double toLat, double toLng, boolean roadClass) {
        LocationIndex index = gh.getLocationIndex();
        if (index == null) {
            return null;
        }
        if (index instanceof LocationIndexTree) {
            ((LocationIndexTree) index).setMaxRegionSearch(SNAP_REGION_SEARCH);
        }
        Snap from = index.findClosest(fromLat, fromLng, EdgeFilter.ALL_EDGES);
        Snap to = index.findClosest(toLat, toLng, EdgeFilter.ALL_EDGES);
        if (from == null || to == null || !from.isValid() || !to.isValid()) {
            return null;
        }
        GHPoint a = from.getSnappedPoint();
        GHPoint b = to.getSnappedPoint();
        return routeWithDetails(gh, a.lat, a.lon, b.lat, b.lon, roadClass);
    }

    @Nullable
    private static GraphHopper ensureLoaded(@NonNull Context app, @NonNull PlayableMapRegion region) {
        synchronized (LOCK) {
            GraphHopper cached = hoppers.get(region);
            if (cached != null) {
                return cached;
            }
            File graphDir = RoutingGraphDownloader.ensureGraphDir(app, region);
            if (graphDir == null) {
                lastDiag = new Diag(Kind.GRAPH_MISSING, graphState(app, region)
                        + "\nensureGraphDir=null (no extraído o sin properties)");
                return null;
            }
            Integer storedHash = GraphProfileHash.readCarHash(graphDir);
            try {
                GraphHopper next = new AndroidGraphHopper();
                next.init(loadConfig(graphDir, "MMAP_STORE", storedHash,
                        readEncodedValues(graphDir)));
                next.setAllowWrites(false);
                if (!next.load()) {
                    lastDiag = new Diag(Kind.LOAD_FALSE, graphState(app, region)
                            + "\nMMAP load()=false hash=" + storedHash);
                    return null;
                }
                widenSnap(next);
                hoppers.put(region, next);
                lastDiag = new Diag(Kind.OK, "grafo cargado MMAP hash=" + storedHash
                        + " " + graphDir.getAbsolutePath());
                Log.i(TAG, "grafo " + region.prefsValue() + " listo (MMAP)");
                return next;
            } catch (Throwable t) {
                Log.e(TAG, "carga GraphHopper MMAP " + region.prefsValue(), t);
                lastDiag = new Diag(Kind.LOAD_EXCEPTION, graphState(app, region)
                        + "\nhash_leido=" + storedHash + "\n" + stackOf(t));
                return null;
            }
        }
    }

    @NonNull
    private static String readEncodedValues(@NonNull File graphDir) {
        String fromTxt = encodedValuesIn(new File(graphDir, "properties.txt"));
        if (fromTxt != null) {
            return fromTxt;
        }
        String fromProps = encodedValuesIn(new File(graphDir, "properties"));
        return fromProps != null ? fromProps : "car_access, car_average_speed";
    }

    @Nullable
    private static String encodedValuesIn(@NonNull File file) {
        if (!file.isFile()) {
            return null;
        }
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (!line.startsWith("graph.encoded_values=")) {
                    continue;
                }
                String v = line.substring("graph.encoded_values=".length()).trim();
                return v.isEmpty() ? null : v;
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }

    private static GraphHopperConfig loadConfig(@NonNull File graphDir, @NonNull String daType,
            @Nullable Integer storedHash, @NonNull String encodedValues) throws Exception {
        ObjectMapper om = Jackson.newObjectMapper();
        Profile parsed = om.readValue(CAR_PROFILE_JSON, Profile.class);
        Profile car = storedHash == null ? parsed : new Profile(parsed) {
            @Override
            public int getVersion() {
                return storedHash;
            }
        };
        GraphHopperConfig cfg = new GraphHopperConfig();
        cfg.putObject("graph.location", graphDir.getAbsolutePath());
        cfg.putObject("graph.dataaccess.default_type", daType);
        cfg.putObject("graph.locktype", "simple");
        cfg.putObject("custom_models.directory", graphDir.getAbsolutePath());
        cfg.putObject("graph.encoded_values", encodedValues);
        cfg.putObject("import.osm.ignored_highways", IGNORED_HIGHWAYS);
        cfg.putObject("index.max_region_search", SNAP_REGION_SEARCH);
        cfg.setProfiles(Collections.singletonList(car));
        cfg.setCHProfiles(Collections.singletonList(new CHProfile("car")));
        return cfg;
    }

    private static void widenSnap(@NonNull GraphHopper gh) {
        try {
            LocationIndex index = gh.getLocationIndex();
            if (index instanceof LocationIndexTree) {
                ((LocationIndexTree) index).setMaxRegionSearch(SNAP_REGION_SEARCH);
            }
        } catch (RuntimeException e) {
            Log.w(TAG, "snap region", e);
        }
    }

    @NonNull
    private static String errorsOf(@Nullable GHResponse rsp) {
        if (rsp == null) {
            return "respuesta nula";
        }
        if (!rsp.hasErrors()) {
            return rsp.getAll().isEmpty() ? "sin caminos" : "ok";
        }
        StringBuilder sb = new StringBuilder();
        for (Throwable t : rsp.getErrors()) {
            if (sb.length() > 0) {
                sb.append(" | ");
            }
            sb.append(t.getClass().getSimpleName()).append(": ").append(t.getMessage());
        }
        return sb.toString();
    }

    @NonNull
    private static String graphState(@NonNull Context app) {
        return graphState(app, PlayableMapRegion.IBERIA);
    }

    @NonNull
    private static String graphState(@NonNull Context app, @NonNull PlayableMapRegion region) {
        File dir = RoutingGraphDownloader.graphDir(app, region);
        File archive = RoutingGraphDownloader.archiveFile(app, region);
        StringBuilder sb = new StringBuilder();
        sb.append("region=").append(region.prefsValue());
        sb.append(" camion=").append(TruckLivePrefs.isEnabled(app));
        sb.append(" prefsReady=").append(TruckLivePrefs.isGraphReady(app));
        sb.append(" instalado=").append(RoutingGraphDownloader.isInstalled(app, region));
        sb.append("\ndir=").append(dir.getAbsolutePath());
        sb.append(" existe=").append(dir.isDirectory());
        sb.append("\narchivo=").append(archive.getAbsolutePath());
        sb.append(" bytes=").append(archive.isFile() ? archive.length() : 0);
        sb.append("\narchivos:");
        File[] kids = dir.listFiles();
        if (kids == null || kids.length == 0) {
            sb.append(" (vacío)");
        } else {
            for (File f : kids) {
                sb.append("\n- ").append(f.getName()).append(" ").append(f.length());
            }
        }
        File props = new File(dir, "properties");
        if (!props.isFile()) {
            props = new File(dir, "properties.txt");
        }
        if (props.isFile()) {
            sb.append("\nproperties:\n").append(headFile(props, 40));
        }
        return sb.toString();
    }

    @NonNull
    private static String headFile(@NonNull File file, int maxLines) {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            int n = 0;
            while (n < maxLines && (line = br.readLine()) != null) {
                sb.append(line).append('\n');
                n++;
            }
        } catch (Exception e) {
            return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        }
        return sb.toString();
    }

    @NonNull
    private static String stackOf(@NonNull Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        String s = sw.toString();
        if (s.length() > 4000) {
            return s.substring(0, 4000);
        }
        return s;
    }
}
