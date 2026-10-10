package com.apiculture.simulator.domain.map;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.apiculture.simulator.domain.game.TranshumanceRules;
import com.apiculture.simulator.domain.parcel.LandMask;

import java.util.ArrayList;
import java.util.List;

/**
 * Evita pistas en línea recta que cruzan mar. Si hace falta, rodea por tierra.
 */
public final class LandSafeRoute {

    /** La carretera tiene que medir al menos esto × la recta para plantear pista. */
    public static final double FIELD_TRACK_MIN_ROAD_RATIO = 2.0;

    private LandSafeRoute() {
    }

    public static boolean geodesicCrossesWater(@Nullable LandMask mask,
            double fromLat, double fromLng, double toLat, double toLng) {
        if (mask == null) {
            return false;
        }
        return segmentCrossesWater(mask, fromLat, fromLng, toLat, toLng);
    }

    @Nullable
    public static RoadPath fieldTrackOnLand(@Nullable LandMask mask,
            double fromLat, double fromLng, double toLat, double toLng) {
        if (mask == null || !geodesicCrossesWater(mask, fromLat, fromLng, toLat, toLng)) {
            return RoadPath.fieldTrack(fromLat, fromLng, toLat, toLng);
        }
        List<double[]> pts = hugLand(mask, fromLat, fromLng, toLat, toLng);
        if (pts == null || pts.size() < 2) {
            return null;
        }
        return RoadPath.fieldTrack(pts);
    }

    static boolean segmentCrossesWater(@NonNull LandMask mask,
            double aLat, double aLng, double bLat, double bLng) {
        double km = TranshumanceRules.haversineKm(aLat, aLng, bLat, bLng);
        int n = Math.max(8, (int) Math.ceil(km / 6.0));
        for (int i = 1; i < n; i++) {
            double t = i / (double) n;
            double lat = aLat + (bLat - aLat) * t;
            double lng = aLng + (bLng - aLng) * t;
            if (!mask.isLand(lat, lng)) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    static List<double[]> hugLand(@NonNull LandMask mask,
            double fromLat, double fromLng, double toLat, double toLng) {
        double km = TranshumanceRules.haversineKm(fromLat, fromLng, toLat, toLng);
        int n = Math.max(20, (int) Math.ceil(km / 8.0));
        List<double[]> raw = new ArrayList<>();
        raw.add(new double[]{fromLat, fromLng});
        for (int i = 1; i < n; i++) {
            double t = i / (double) n;
            double lat = fromLat + (toLat - fromLat) * t;
            double lng = fromLng + (toLng - fromLng) * t;
            if (mask.isLand(lat, lng)) {
                raw.add(new double[]{lat, lng});
            } else {
                double[] land = nearestLand(mask, lat, lng);
                if (land != null) {
                    raw.add(land);
                }
            }
        }
        raw.add(new double[]{toLat, toLng});

        List<double[]> pts = new ArrayList<>();
        pts.add(raw.get(0));
        for (int i = 1; i < raw.size(); i++) {
            if (!appendOnLand(mask, pts, raw.get(i), 0)) {
                return null;
            }
        }
        return pts;
    }

    private static boolean appendOnLand(@NonNull LandMask mask, @NonNull List<double[]> pts,
            @NonNull double[] dest, int depth) {
        double[] cur = pts.get(pts.size() - 1);
        if (!segmentCrossesWater(mask, cur[0], cur[1], dest[0], dest[1])) {
            pts.add(dest);
            return true;
        }
        if (depth > 6) {
            return false;
        }
        double midLat = (cur[0] + dest[0]) / 2.0;
        double midLng = (cur[1] + dest[1]) / 2.0;
        double dx = dest[0] - cur[0];
        double dy = dest[1] - cur[1];
        double[] left = tryInland(mask, midLat, midLng, -dy, dx);
        double[] right = tryInland(mask, midLat, midLng, dy, -dx);
        double[] via = pickVia(mask, cur, dest, left, right);
        if (via == null) {
            via = nearestLand(mask, midLat, midLng);
        }
        if (via == null) {
            return false;
        }
        return appendOnLand(mask, pts, via, depth + 1) && appendOnLand(mask, pts, dest, depth + 1);
    }

    @Nullable
    private static double[] pickVia(@NonNull LandMask mask, @NonNull double[] a, @NonNull double[] b,
            @Nullable double[] left, @Nullable double[] right) {
        boolean lOk = left != null
                && !segmentCrossesWater(mask, a[0], a[1], left[0], left[1])
                && !segmentCrossesWater(mask, left[0], left[1], b[0], b[1]);
        boolean rOk = right != null
                && !segmentCrossesWater(mask, a[0], a[1], right[0], right[1])
                && !segmentCrossesWater(mask, right[0], right[1], b[0], b[1]);
        if (lOk && rOk) {
            double lKm = TranshumanceRules.haversineKm(a[0], a[1], left[0], left[1])
                    + TranshumanceRules.haversineKm(left[0], left[1], b[0], b[1]);
            double rKm = TranshumanceRules.haversineKm(a[0], a[1], right[0], right[1])
                    + TranshumanceRules.haversineKm(right[0], right[1], b[0], b[1]);
            return lKm <= rKm ? left : right;
        }
        if (lOk) {
            return left;
        }
        if (rOk) {
            return right;
        }
        return left != null ? left : right;
    }

    @Nullable
    private static double[] tryInland(@NonNull LandMask mask, double lat, double lng,
            double dirLat, double dirLng) {
        double len = Math.hypot(dirLat, dirLng);
        if (len < 1e-9) {
            return nearestLand(mask, lat, lng);
        }
        double uLat = dirLat / len;
        double uLng = dirLng / len;
        for (int km = 8; km <= 180; km += 8) {
            double[] p = offsetKm(lat, lng, km, Math.atan2(uLng, uLat));
            if (mask.isLand(p[0], p[1])) {
                return p;
            }
        }
        return null;
    }

    @Nullable
    private static double[] nearestLand(@NonNull LandMask mask, double lat, double lng) {
        if (mask.isLand(lat, lng)) {
            return new double[]{lat, lng};
        }
        for (int step = 4; step <= 220; step += 6) {
            double[] best = null;
            double bestKm = Double.MAX_VALUE;
            for (int dir = 0; dir < 16; dir++) {
                double ang = dir * (Math.PI / 8.0);
                double[] p = offsetKm(lat, lng, step, ang);
                if (!mask.isLand(p[0], p[1])) {
                    continue;
                }
                double d = TranshumanceRules.haversineKm(lat, lng, p[0], p[1]);
                if (d < bestKm) {
                    bestKm = d;
                    best = p;
                }
            }
            if (best != null) {
                return best;
            }
        }
        return null;
    }

    @NonNull
    private static double[] offsetKm(double lat, double lng, double km, double bearingRad) {
        double dLat = (km / 111.32) * Math.cos(bearingRad);
        double cos = Math.cos(Math.toRadians(lat));
        double dLng = (km / (111.32 * Math.max(0.2, Math.abs(cos)))) * Math.sin(bearingRad);
        return new double[]{lat + dLat, lng + dLng};
    }
}
