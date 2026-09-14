package com.apiculture.simulator.domain.parcel;

/**
 * Sudáfrica continental (incluye Lesoto y Eswatini). Sin Namibia ni Mozambique.
 * Extremos aproximados: Cabo de las Agujas ~34,83°S, Limpopo ~22,13°S,
 * Alexander Bay ~16,45°E, Kosi Bay ~32,89°E.
 */
public final class SouthAfricaBounds {

    public static final BoundingBox BOX = new BoundingBox(
            -35.15,
            -21.95,
            16.20,
            33.20);

    private SouthAfricaBounds() {
    }
}
