package com.apiculture.simulator.domain.parcel;

/**
 * Península ibérica y Baleares (sin Canarias). Más adelante un selector de país puede sustituir esto
 * por otro {@link BoundingBox} o varias regiones.
 */
public final class IberiaBounds {

    /** Aproximación holgada: Portugal continental, España peninsular, Baleares. */
    public static final BoundingBox BOX = new BoundingBox(
            35.75,
            43.98,
            -9.70,
            4.55);

    private IberiaBounds() {
    }
}
