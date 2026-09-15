package com.apiculture.simulator.domain.game;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class DailySkyConditionTest {

    @Test
    public void wmoCode2IsVariableUnlessWindy() {
        DailyWeather mild = observed(2, 0.0, 10.0);
        assertEquals(DailySkyCondition.VARIABLE, DailySkyCondition.fromObserved(mild));
        DailyWeather gale = observed(2, 0.0, 50.0);
        assertEquals(DailySkyCondition.WINDY, DailySkyCondition.fromObserved(gale));
    }

    @Test
    public void wmoCode3IsCloudyUnlessWindy() {
        DailyWeather overcast = observed(3, 0.0, 10.0);
        assertEquals(DailySkyCondition.CLOUDY, DailySkyCondition.fromObserved(overcast));
        DailyWeather gale = observed(3, 0.0, 50.0);
        assertEquals(DailySkyCondition.WINDY, DailySkyCondition.fromObserved(gale));
    }

    @Test
    public void unknownCodeFallsBackToRainWindOrCloudy() {
        assertEquals(DailySkyCondition.RAINY, DailySkyCondition.fromObserved(observed(85, 2.0, 10.0)));
        assertEquals(DailySkyCondition.WINDY, DailySkyCondition.fromObserved(observed(85, 0.0, 42.0)));
        assertEquals(DailySkyCondition.CLOUDY, DailySkyCondition.fromObserved(observed(85, 0.0, 10.0)));
    }

    @Test
    public void rescaleCloudyToVariableScalesForageNotConsumption() {
        double netCloudy = -0.09;
        double consumption = 0.40;
        double variable = HiveDailyBiology.rescaleNetForSky(
                netCloudy, consumption,
                GameBalanceConfig.skyMultCloudy, GameBalanceConfig.skyMultVariable);
        double forageCloudy = netCloudy + consumption;
        double expected = forageCloudy / GameBalanceConfig.skyMultCloudy
                * GameBalanceConfig.skyMultVariable - consumption;
        assertEquals(expected, variable, 1e-9);
    }

    @Test
    public void pecoreoMultipliers() {
        assertEquals(1.25, DailySkyCondition.SUN.productionMultiplier(), 1e-9);
        assertEquals(1.1, DailySkyCondition.VARIABLE.productionMultiplier(), 1e-9);
        assertEquals(0.85, DailySkyCondition.CLOUDY.productionMultiplier(), 1e-9);
        assertEquals(0.7, DailySkyCondition.WINDY.productionMultiplier(), 1e-9);
        assertEquals(0.0, DailySkyCondition.RAINY.productionMultiplier(), 1e-9);
    }

    private static DailyWeather observed(int code, double rainMm, double windKmh) {
        DailyWeather w = new DailyWeather();
        w.weatherCode = code;
        w.precipitationMm = rainMm;
        w.windMaxKmh = windKmh;
        return w;
    }
}
