package com.apiculture.simulator.presentation.map;

import android.graphics.Point;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.gms.maps.Projection;
import com.google.android.gms.maps.model.LatLng;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Agrupa marcadores que se pisan en pantalla. */
public final class MapPinGroups {

    public static final class Pin {
        public final double lat;
        public final double lng;
        public final int index;

        public Pin(double lat, double lng, int index) {
            this.lat = lat;
            this.lng = lng;
            this.index = index;
        }
    }

    public static final class Group {
        @NonNull
        public final List<Pin> pins;
        public final double lat;
        public final double lng;

        Group(@NonNull List<Pin> pins, double lat, double lng) {
            this.pins = pins;
            this.lat = lat;
            this.lng = lng;
        }
    }

    /** Al pulsar, la cámara se acerca a estos puntos. */
    public static final class Focus {
        @NonNull
        public final List<LatLng> points;

        public Focus(@NonNull List<LatLng> points) {
            this.points = points;
        }
    }

    private MapPinGroups() {
    }

    @NonNull
    public static List<Group> singles(@NonNull List<Pin> pins) {
        List<Group> out = new ArrayList<>(pins.size());
        for (Pin pin : pins) {
            out.add(new Group(Collections.singletonList(pin), pin.lat, pin.lng));
        }
        return out;
    }

    @NonNull
    public static List<Group> group(@Nullable Projection projection, @NonNull List<Pin> pins,
            float radiusPx) {
        if (projection == null || pins.size() < 2 || radiusPx <= 1f) {
            return singles(pins);
        }
        int n = pins.size();
        float[] x = new float[n];
        float[] y = new float[n];
        try {
            for (int i = 0; i < n; i++) {
                Pin pin = pins.get(i);
                Point p = projection.toScreenLocation(new LatLng(pin.lat, pin.lng));
                x[i] = p.x;
                y[i] = p.y;
            }
        } catch (RuntimeException ignored) {
            return singles(pins);
        }
        boolean[] used = new boolean[n];
        float r2 = radiusPx * radiusPx;
        List<Group> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (used[i]) {
                continue;
            }
            List<Pin> members = new ArrayList<>();
            members.add(pins.get(i));
            used[i] = true;
            boolean grew = true;
            while (grew) {
                grew = false;
                for (int j = 0; j < n; j++) {
                    if (used[j]) {
                        continue;
                    }
                    if (!nearAny(x, y, j, members, r2)) {
                        continue;
                    }
                    used[j] = true;
                    members.add(pins.get(j));
                    grew = true;
                }
            }
            double lat = 0.0;
            double lng = 0.0;
            for (Pin pin : members) {
                lat += pin.lat;
                lng += pin.lng;
            }
            out.add(new Group(members, lat / members.size(), lng / members.size()));
        }
        return out;
    }

    private static boolean nearAny(@NonNull float[] x, @NonNull float[] y, int index,
            @NonNull List<Pin> members, float r2) {
        for (Pin member : members) {
            float dx = x[index] - x[member.index];
            float dy = y[index] - y[member.index];
            if (dx * dx + dy * dy <= r2) {
                return true;
            }
        }
        return false;
    }
}
