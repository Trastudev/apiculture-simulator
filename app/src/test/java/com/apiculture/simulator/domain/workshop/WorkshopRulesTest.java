package com.apiculture.simulator.domain.workshop;

import com.apiculture.simulator.domain.workshop.WorkshopRules.Format;

import org.junit.Assert;
import org.junit.Test;

public class WorkshopRulesTest {

    @Test
    public void smallOrdersAskForSmallJars() {
        for (int i = 0; i < 50; i++) {
            Format f = WorkshopRules.orderFormat("o-" + i, 1.2);
            Assert.assertTrue(f == Format.JAR_250 || f == Format.JAR_500);
        }
    }

    @Test
    public void bigOrdersNeverAskForSmallJars() {
        for (int i = 0; i < 50; i++) {
            Format f = WorkshopRules.orderFormat("o-" + i, 20);
            Assert.assertTrue(f == Format.JAR_1000 || f == Format.BULK);
        }
    }

    @Test
    public void orderKgIsWholeJars() {
        Assert.assertEquals(1.25, WorkshopRules.orderKg(1.3, Format.JAR_250), 1e-9);
        Assert.assertEquals(5, WorkshopRules.orderJars(1.25, Format.JAR_250));
        Assert.assertEquals(0.5, WorkshopRules.orderKg(0.1, Format.JAR_500), 1e-9);
        Assert.assertEquals(7.37, WorkshopRules.orderKg(7.37, Format.BULK), 1e-9);
        Assert.assertEquals(0, WorkshopRules.orderJars(7.37, Format.BULK));
    }

    @Test
    public void smallerJarsPayMorePerKilo() {
        Assert.assertTrue(Format.JAR_250.priceFactor > Format.JAR_500.priceFactor);
        Assert.assertTrue(Format.JAR_500.priceFactor > Format.JAR_1000.priceFactor);
        Assert.assertTrue(Format.JAR_1000.priceFactor > Format.BULK.priceFactor);
    }

    @Test
    public void receptionIsNotUpgradable() {
        Assert.assertFalse(WorkshopRules.upgradable(WorkshopRules.Machine.RECEPTION));
        for (WorkshopRules.Machine m : WorkshopRules.Machine.values()) {
            if (m != WorkshopRules.Machine.RECEPTION) {
                Assert.assertTrue(WorkshopRules.upgradable(m));
            }
        }
    }

    @Test
    public void receptionTimeDoesNotDependOnLevel() {
        Assert.assertEquals(
                WorkshopRules.durationMs(WorkshopRules.Machine.RECEPTION, 1, 300, null),
                WorkshopRules.durationMs(WorkshopRules.Machine.RECEPTION, 5, 300, null));
    }
}
