package com.apiculture.simulator.domain.map;

import com.graphhopper.GraphHopper;
import com.graphhopper.routing.WeightingFactory;

/** GraphHopper que no usa Janino al crear el weighting del perfil {@code car}. */
final class AndroidGraphHopper extends GraphHopper {

    @Override
    protected WeightingFactory createWeightingFactory() {
        return new TruckLiteWeightingFactory(getEncodingManager());
    }
}
