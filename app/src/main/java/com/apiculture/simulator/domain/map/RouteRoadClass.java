package com.apiculture.simulator.domain.map;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.graphhopper.GraphHopper;
import com.graphhopper.ResponsePath;
import com.graphhopper.util.Instruction;
import com.graphhopper.util.InstructionList;
import com.graphhopper.util.PointList;
import com.graphhopper.util.details.PathDetail;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** Extrae el tipo de vía de un {@link ResponsePath} de GraphHopper. */
final class RouteRoadClass {

    private RouteRoadClass() {
    }

    @NonNull
    static String classify(@Nullable GraphHopper gh, @Nullable ResponsePath path, int pointCount) {
        int edges = Math.max(0, pointCount - 1);
        if (edges == 0 || path == null) {
            return "";
        }
        char[] out = new char[edges];
        Arrays.fill(out, RoadKind.OTRO.code);
        fillFromDetails(path, out);
        fillFromInstructions(path, out);
        return new String(out);
    }

    static boolean hopperHasRoadClass(@Nullable GraphHopper gh) {
        if (gh == null) {
            return false;
        }
        try {
            return gh.getEncodingManager() != null
                    && gh.getEncodingManager().hasEncodedValue("road_class");
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void fillFromDetails(@NonNull ResponsePath path, @NonNull char[] out) {
        Map<String, List<PathDetail>> all = path.getPathDetails();
        if (all == null || all.isEmpty()) {
            return;
        }
        List<PathDetail> rc = all.get("road_class");
        if (rc != null) {
            for (PathDetail d : rc) {
                paint(out, d.getFirst(), d.getLast(), RoadKind.fromOsm(d.getValue()).code);
            }
        }
        List<PathDetail> names = all.get("street_name");
        if (names == null) {
            return;
        }
        for (PathDetail d : names) {
            RoadKind named = RoadKind.fromRef(d.getValue() == null ? null : String.valueOf(d.getValue()));
            if (named != null) {
                paint(out, d.getFirst(), d.getLast(), named.code);
            }
        }
    }

    private static void fillFromInstructions(@NonNull ResponsePath path, @NonNull char[] out) {
        InstructionList list = path.getInstructions();
        if (list == null || list.isEmpty()) {
            return;
        }
        int cursor = 0;
        for (Instruction in : list) {
            if (in == null || in.getSign() == Instruction.FINISH) {
                continue;
            }
            PointList pl = in.getPoints();
            int nEdges = pl == null ? 0 : Math.max(0, pl.size() - 1);
            if (nEdges == 0 && in.getDistance() > 1) {
                nEdges = 1;
            }
            if (nEdges <= 0 || cursor >= out.length) {
                continue;
            }
            int end = Math.min(out.length, cursor + nEdges);
            RoadKind named = RoadKind.fromRef(in.getName());
            if (named != null) {
                Arrays.fill(out, cursor, end, named.code);
            } else {
                double timeMs = in.getTime();
                double kmh = timeMs > 0 ? in.getDistance() * 3600.0 / timeMs : 0;
                char speed = RoadKind.fromSpeedKmh(kmh).code;
                if (speed != RoadKind.OTRO.code) {
                    for (int i = cursor; i < end; i++) {
                        if (out[i] == RoadKind.OTRO.code) {
                            out[i] = speed;
                        }
                    }
                }
            }
            cursor = end;
        }
    }

    private static void paint(@NonNull char[] out, int first, int last, char code) {
        int from = Math.max(0, first);
        int to = last;
        if (to <= from) {
            to = from + 1;
        }
        to = Math.min(out.length, to);
        if (from >= out.length) {
            return;
        }
        Arrays.fill(out, from, to, code);
    }
}
