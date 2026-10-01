package com.apiculture.simulator.domain.game;

import com.apiculture.simulator.data.local.entity.HiveEntity;
import com.apiculture.simulator.domain.parcel.HexFlora;
import com.apiculture.simulator.domain.parcel.HexParcel;

import org.junit.Assert;
import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.List;

public class HiveHoneyStocksAndFloraMixTest {

    @Test
    public void nativeMix_alwaysIncludesMilFlores_noPlantations_size3to5() {
        HexParcel parcel = new HexParcel(
                "hex_iberia_12_-4",
                new double[][]{{0, 0}, {0, 1}, {1, 1}, {1, 0}, {0.5, -0.5}, {-0.5, 0.5}},
                40.4,
                -3.7,
                1.0,
                false);
        List<String> mix = HexFlora.nativeMixForParcel(parcel);
        Assert.assertTrue(mix.contains(HexFlora.MIL_FLORES));
        Assert.assertTrue(mix.size() >= 3);
        Assert.assertTrue(mix.size() <= 5);
        for (String k : mix) {
            Assert.assertFalse(HexFlora.isPlantation(k));
        }
        Assert.assertEquals(mix, HexFlora.nativeMixForParcel(parcel));
    }

    @Test
    public void harvestLeaving_isProportional() {
        HiveEntity h = new HiveEntity();
        h.id = "h1";
        h.superCount = 2;
        h.floraType = "Romero";
        LinkedHashMap<String, Double> stocks = new LinkedHashMap<>();
        stocks.put("Romero", 6.0);
        stocks.put("Mil flores", 4.0);
        h.honeyStocksJson = HiveHoneyStocks.serialize(stocks);
        h.honeyProduction = 10.0;
        LinkedHashMap<String, Double> taken = HiveHoneyStocks.harvestLeaving(h, 3.0);
        Assert.assertEquals(4.2, taken.get("Romero"), 0.05);
        Assert.assertEquals(2.8, taken.get("Mil flores"), 0.05);
        Assert.assertEquals(3.0, h.honeyProduction, 0.05);
    }

    @Test
    public void applyNet_positiveGoesToForageType() {
        HiveEntity h = new HiveEntity();
        h.id = "h2";
        h.superCount = 2;
        h.floraType = "Lavanda";
        h.honeyProduction = 2.0;
        HiveHoneyStocks.seedStarter(h);
        HiveHoneyStocks.applyNet(h, 1.5, "Tomillo");
        LinkedHashMap<String, Double> stocks = HiveHoneyStocks.parse(h.honeyStocksJson);
        Assert.assertEquals(2.0, stocks.get("Lavanda"), 0.01);
        Assert.assertEquals(1.5, stocks.get("Tomillo"), 0.01);
        Assert.assertEquals(3.5, h.honeyProduction, 0.01);
    }

    @Test
    public void bloomWindow_sunflowerHasSummerSpan() {
        List<FloraBloomWindow.Span> spans = FloraBloomWindow.spans("Campo de girasoles", 0);
        Assert.assertFalse(spans.isEmpty());
        FloraBloomWindow.Span s = spans.get(0);
        Assert.assertTrue("start=" + s.startDoy, s.startDoy >= 150 && s.startDoy <= 210);
        Assert.assertTrue("end=" + s.endDoy, s.endDoy >= 190 && s.endDoy <= 260);
        Assert.assertTrue(FloraBloomWindow.formatEs(spans).contains("–"));
    }

    @Test
    public void daysUntilBloom_almondsBeforeSunflowerInJanuary_zaSunflowerInJanuary() {
        HexParcel med = new HexParcel(
                "hex_iberia_9_2",
                new double[][]{{0, 0}, {0, 1}, {1, 1}, {1, 0}, {0.5, -0.5}, {-0.5, 0.5}},
                39.5, -0.4, 70.0, false, 15);
        java.time.LocalDate jan = java.time.LocalDate.of(2026, 1, 10);
        int almonds = FloraBloomWindow.daysUntilBloomStart("Campo de almendros", med, jan);
        int sun = FloraBloomWindow.daysUntilBloomStart("Campo de girasoles", med, jan);
        Assert.assertTrue("almonds=" + almonds + " sun=" + sun, almonds < sun);

        HexParcel cape = new HexParcel(
                "hex_za_2_2",
                new double[][]{{0, 0}, {0, 1}, {1, 1}, {1, 0}, {0.5, -0.5}, {-0.5, 0.5}},
                -34.0, 18.5, 70.0, false, 80);
        int zaSun = FloraBloomWindow.daysUntilBloomStart("Campo de girasoles", cape, jan);
        Assert.assertTrue("ZA sunflower should be in/near Jan, days=" + zaSun, zaSun < 50);
    }

    @Test
    public void mediterraneanLandPricesVaryWithMix() {
        java.util.Set<Integer> prices = new java.util.HashSet<>();
        java.util.Set<Integer> mixSizes = new java.util.HashSet<>();
        for (int i = 0; i < 48; i++) {
            HexParcel parcel = new HexParcel(
                    "hex_iberia_" + i + "_" + (i * 3),
                    new double[][]{{0, 0}, {0, 1}, {1, 1}, {1, 0}, {0.5, -0.5}, {-0.5, 0.5}},
                    39.5,
                    -0.4,
                    1.0,
                    true);
            List<String> mix = HexFlora.nativeMixForParcel(parcel);
            mixSizes.add(mix.size());
            prices.add(com.apiculture.simulator.domain.parcel.FloraProgression
                    .terrainPurchaseTotalEurosForNativeMix(mix));
        }
        Assert.assertTrue("mix sizes " + mixSizes, mixSizes.size() >= 2);
        Assert.assertTrue("prices " + prices, prices.size() >= 3);
        for (int p : prices) {
            Assert.assertTrue("price=" + p, p > 1000);
        }
    }
}
