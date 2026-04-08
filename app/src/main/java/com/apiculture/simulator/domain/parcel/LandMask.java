package com.apiculture.simulator.domain.parcel;

/**
 * Consulta tierra firme vs agua. Implementaciones típicas: polígonos GeoJSON (Natural Earth, OSM),
 * o servicios remotos preprocesados.
 */
public interface LandMask {
    /** {@code true} si el punto está en tierra firme (no océano, mar ni lago grande según la fuente). */
    boolean isLand(double latDeg, double lonDeg);
}
