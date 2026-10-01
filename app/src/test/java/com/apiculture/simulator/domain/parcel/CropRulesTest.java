package com.apiculture.simulator.domain.parcel;

import com.apiculture.simulator.domain.game.FloraBloomWindow;

import org.junit.Assert;
import org.junit.Test;

import java.time.LocalDate;

public class CropRulesTest {

    @Test
    public void colzaIsCheapAnnual_treesAreSlowAndPricier() {
        Assert.assertTrue(CropRules.isAnnual("Campo de Colza"));
        Assert.assertTrue(CropRules.isAnnual("Campo de girasoles"));
        Assert.assertTrue(CropRules.isAnnual(HexFlora.LUCERNA));
        Assert.assertTrue(CropRules.isTree("Campo de perales"));
        Assert.assertTrue(CropRules.isTree("Campo de naranjos"));
        Assert.assertTrue(CropRules.isTree(HexFlora.AGUACATE));
        Assert.assertTrue(CropRules.isTree(HexFlora.MANGO));
        Assert.assertTrue(CropRules.isTree(HexFlora.CAFE));
        Assert.assertTrue(CropRules.isAnnual(HexFlora.SISAL));
        Assert.assertFalse(HexFlora.isPlantation("Mil flores"));
        Assert.assertEquals(CropRules.ANNUAL_GROW_DAYS, CropRules.growDays("Campo de Colza"));
        Assert.assertEquals(CropRules.TREE_GROW_DAYS, CropRules.growDays("Campo de perales"));
        Assert.assertTrue(CropRules.plantCostEuros("Campo de Colza") < CropRules.plantCostEuros("Campo de perales"));
        Assert.assertEquals(0, CropRules.treeMaintenanceEuros("Campo de Colza"));
        Assert.assertTrue(CropRules.treeMaintenanceEuros("Campo de perales") > 0);
    }

    @Test
    public void nativeMixNeverIncludesPlantations() {
        HexParcel med = new HexParcel(
                "hex_iberia_9_2",
                new double[][]{{0, 0}, {0, 1}, {1, 1}, {1, 0}, {0.5, -0.5}, {-0.5, 0.5}},
                39.5,
                -0.4,
                1.0,
                true);
        for (String k : HexFlora.nativeMixForParcel(med)) {
            Assert.assertFalse(k, HexFlora.isPlantation(k));
        }
        Assert.assertFalse(HexFlora.nativeKeysForRegion(false).contains("Campo de perales"));
    }

    @Test
    public void annualExpireIsAfterBloomWindow() {
        HexParcel med = new HexParcel(
                "hex_iberia_9_2",
                new double[][]{{0, 0}, {0, 1}, {1, 1}, {1, 0}, {0.5, -0.5}, {-0.5, 0.5}},
                39.5,
                -0.4,
                1.0,
                true);
        LocalDate planted = LocalDate.of(2026, 1, 10);
        int expire = CropRules.expireDayKey("Campo de Colza", med, planted);
        Assert.assertTrue(expire > 20260110);
        LocalDate end = FloraBloomWindow.bloomEndDateOnOrAfter("Campo de Colza", med, planted);
        Assert.assertFalse(end.isBefore(planted));
    }
}
