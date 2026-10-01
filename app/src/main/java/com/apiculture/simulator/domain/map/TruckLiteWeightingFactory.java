package com.apiculture.simulator.domain.map;

import androidx.annotation.NonNull;

import com.graphhopper.config.Profile;
import com.graphhopper.routing.WeightingFactory;
import com.graphhopper.routing.ev.BooleanEncodedValue;
import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.weighting.TurnCostProvider;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.routing.weighting.custom.CustomWeighting;
import com.graphhopper.util.PMap;

/**
 * Mismo modelo que {@code truck_lite.json}, sin Janino (ART no carga esas clases).
 */
final class TruckLiteWeightingFactory implements WeightingFactory {

    private static final double DISTANCE_INFLUENCE = 90;

    private final EncodingManager encodingManager;

    TruckLiteWeightingFactory(@NonNull EncodingManager encodingManager) {
        this.encodingManager = encodingManager;
    }

    @Override
    public Weighting createWeighting(Profile profile, PMap hints, boolean disableTurnCosts) {
        BooleanEncodedValue access = encodingManager.getBooleanEncodedValue("car_access");
        DecimalEncodedValue speed = encodingManager.getDecimalEncodedValue("car_average_speed");
        double maxSpeed = speed.getMaxOrMaxStorableDecimal();
        CustomWeighting.Parameters params = new CustomWeighting.Parameters(
                (edge, reverse) -> reverse ? edge.getReverse(speed) : edge.get(speed),
                () -> maxSpeed,
                (edge, reverse) -> {
                    boolean ok = reverse ? edge.getReverse(access) : edge.get(access);
                    return ok ? 1.0 : 0.0;
                },
                () -> 1.0,
                DISTANCE_INFLUENCE,
                0);
        return new CustomWeighting(TurnCostProvider.NO_TURN_COST_PROVIDER, params);
    }
}
