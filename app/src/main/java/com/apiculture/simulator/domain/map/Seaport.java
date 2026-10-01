package com.apiculture.simulator.domain.map;

import androidx.annotation.NonNull;

/** Puerto público. Los amarres y los barcos son de cada jugador. */
public final class Seaport {

    public final String id;
    public final String name;
    public final PlayableMapRegion region;
    public final double lat;
    public final double lng;
    /** Grupo de costa para elegir la ruta marítima. */
    public final String lane;

    public Seaport(@NonNull String id, @NonNull String name, @NonNull PlayableMapRegion region,
            double lat, double lng, @NonNull String lane) {
        this.id = id;
        this.name = name;
        this.region = region;
        this.lat = lat;
        this.lng = lng;
        this.lane = lane;
    }
}
