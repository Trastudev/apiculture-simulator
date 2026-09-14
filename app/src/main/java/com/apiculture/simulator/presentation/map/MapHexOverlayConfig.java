package com.apiculture.simulator.presentation.map;

/**
 * Parámetros compartidos entre el mapa y {@link com.apiculture.simulator.data.repository.IberiaHexOverlayStore}.
 * Al añadir más regiones (selector de países), conviene seguir centralizando aquí o en un modelo por región.
 */
public final class MapHexOverlayConfig {

    public static final double HEX_GRID_ANCHOR_LAT = 40.0;
    public static final double HEX_GRID_ANCHOR_LON = -3.0;
    public static final int MAP_HEX_LAND_SAMPLES_PER_AXIS = 0;
    public static final double MAP_HEX_MIN_LAND_FRACTION = 0.42;
    /** Iberia; Sudáfrica usa el doble vía {@link com.apiculture.simulator.domain.map.PlayableMapRegion#hexTargetAreaKm2()}. */
    public static final double MAP_HEX_TARGET_AREA_KM2 = 70.0;

    private MapHexOverlayConfig() {
    }
}
