package com.apiculture.simulator.domain.parcel;

public final class HexParcelGameRules {

    /** Base fija del terreno; el precio total suma la prima según flora nativa del hex ({@link FloraProgression}). */
    public static final double HEX_PURCHASE_BASE_EUR = 1000.0;
    /** Límite de colmenas por apiario (siteId), no por hexágono. */
    public static final int MAX_HIVES_PER_SITE = 10;

    private HexParcelGameRules() {
    }
}
