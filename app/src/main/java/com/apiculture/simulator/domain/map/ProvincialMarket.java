package com.apiculture.simulator.domain.map;

import androidx.annotation.NonNull;

public final class ProvincialMarket {
    public final String name;
    public final String hexId;
    public final double lat;
    public final double lng;
    public final int population;
    public final boolean local;
    public final boolean international;
    public final PlayableMapRegion region;

    public ProvincialMarket(String name, String hexId, double lat, double lng) {
        this(name, hexId, lat, lng, 0, false, PlayableMapRegion.IBERIA);
    }

    public ProvincialMarket(String name, String hexId, double lat, double lng,
            int population, boolean local, @NonNull PlayableMapRegion region) {
        this(name, hexId, lat, lng, population, local, region, false);
    }

    public ProvincialMarket(String name, String hexId, double lat, double lng,
            int population, boolean local, @NonNull PlayableMapRegion region, boolean international) {
        this.name = name;
        this.hexId = hexId;
        this.lat = lat;
        this.lng = lng;
        this.population = Math.max(0, population);
        this.international = international;
        this.local = local && !international;
        this.region = region != null ? region : PlayableMapRegion.IBERIA;
    }

    @NonNull
    public String salesId() {
        String raw = name != null ? name : hexId;
        return (raw != null ? raw : "market").replace('|', ' ').trim();
    }

    @NonNull
    @Override
    public String toString() {
        return name;
    }
}
