package com.apiculture.simulator.domain.market;

import com.apiculture.simulator.domain.map.PlayableMapRegion;
import com.apiculture.simulator.domain.map.ProvincialMarket;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TerritorialMarketRulesTest {

    @Test
    public void localMarkupIs15OnLargestAnd20OnSmallest() {
        List<ProvincialMarket> all = Arrays.asList(
                local("Huge", 400),
                local("Tiny", 100));
        assertEquals(0.15, TerritorialMarketRules.localMarkup01(400, all), 1e-9);
        assertEquals(0.20, TerritorialMarketRules.localMarkup01(100, all), 1e-9);
        assertEquals(13.80, TerritorialMarketRules.priceAtMarket(12.0, all.get(0), all), 1e-9);
        assertEquals(14.40, TerritorialMarketRules.priceAtMarket(12.0, all.get(1), all), 1e-9);
        assertEquals(12.0, TerritorialMarketRules.priceAtMarket(12.0, capital("Cap", 10), all), 1e-9);
    }

    @Test
    public void largerPopularHoneyMarketsHaveMoreCompetition() {
        List<ProvincialMarket> all = Arrays.asList(
                capital("Grande", 1_000_000),
                capital("Pequeña", 100_000),
                local("Pueblo grande", 100_000),
                local("Pueblo pequeño", 10_000));
        double bigPopular = TerritorialMarketRules.competition01(
                all.get(0), "Mil flores", all);
        double smallRare = TerritorialMarketRules.competition01(
                all.get(3), "Campo de perales", all);
        assertTrue(bigPopular > smallRare);
        assertTrue(bigPopular > 0.9);
        assertTrue(smallRare < 0.4);
    }

    @Test
    public void competitionLowersCapitalPriceButLocalMarkupCanStillWin() {
        List<ProvincialMarket> all = Arrays.asList(
                capital("Grande", 1_000_000),
                capital("Pequeña", 100_000),
                local("Pueblo", 50_000));
        double capitalPopular = TerritorialMarketRules.priceAtMarket(
                12.0, all.get(0), "Mil flores", all);
        double localPopular = TerritorialMarketRules.priceAtMarket(
                12.0, all.get(2), "Mil flores", all);
        assertTrue(capitalPopular < 12.0);
        assertTrue(localPopular > capitalPopular);
    }

    @Test
    public void southernSeasonFlipsSummerToWinterDemand() {
        assertTrue(TerritorialMarketRules.seasonFor(180, PlayableMapRegion.IBERIA).name().equals("SUMMER"));
        assertTrue(TerritorialMarketRules.seasonFor(180, PlayableMapRegion.SOUTH_AFRICA).name().equals("WINTER"));
    }

    @Test
    public void salesKeyRoundTrip() {
        String key = TerritorialMarketRules.salesKey(PlayableMapRegion.IBERIA, "Romero");
        assertEquals("iberia__Romero", key);
        assertEquals("Romero", TerritorialMarketRules.floraFromSalesKey(key));
        assertEquals("Romero", TerritorialMarketRules.floraFromSalesKey("Romero"));
        assertEquals("Romero", TerritorialMarketRules.floraFromSalesKey(
                TerritorialMarketRules.marketSalesKey(PlayableMapRegion.IBERIA,
                        new ProvincialMarket("Vic", "hex", 0, 0, 1, true, PlayableMapRegion.IBERIA),
                        "Romero")));
    }

    @Test
    public void demandScalesWithRegionalActivity() {
        HoneyMarketSnapshot six = HoneyMarketEngine.computeSnapshot(
                20260920, 180, PlayableMapRegion.IBERIA, 6, 1);
        HoneyMarketSnapshot ten = HoneyMarketEngine.computeSnapshot(
                20260920, 180, PlayableMapRegion.IBERIA, 10, 1);
        assertEquals(6.0, six.activityUnits, 1e-9);
        assertEquals(10.0, ten.activityUnits, 1e-9);
        assertEquals(10.0 / 6.0, ten.demandKgByFlora.get("Mil flores") / six.demandKgByFlora.get("Mil flores"), 0.02);
    }

    private static ProvincialMarket capital(String name, int pop) {
        return new ProvincialMarket(name, "hex", 0, 0, pop, false, PlayableMapRegion.IBERIA);
    }

    private static ProvincialMarket local(String name, int pop) {
        return new ProvincialMarket(name, "hex", 0, 0, pop, true, PlayableMapRegion.IBERIA);
    }
}
