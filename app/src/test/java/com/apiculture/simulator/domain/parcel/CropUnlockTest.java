package com.apiculture.simulator.domain.parcel;

import org.junit.Assert;
import org.junit.Test;

import java.util.List;

public class CropUnlockTest {

    @Test
    public void mediterraneanCropsUnlockAtConfiguredLevels() {
        Assert.assertTrue(CropUnlock.unlockedCatalog(1).isEmpty());
        Assert.assertTrue(CropUnlock.isUnlocked("Campo de naranjos", 2));
        Assert.assertTrue(CropUnlock.isUnlocked("Campo de almendros", 4));
        Assert.assertFalse(CropUnlock.isUnlocked(HexFlora.MANGO, 4));
        Assert.assertTrue(CropUnlock.isUnlocked(HexFlora.MANGO, 5));
        Assert.assertFalse(CropUnlock.isUnlocked(HexFlora.CAFE, 15));
        Assert.assertTrue(CropUnlock.isUnlocked(HexFlora.CAFE, 16));
        Assert.assertFalse(CropUnlock.isUnlocked(HexFlora.SISAL, 29));
        Assert.assertTrue(CropUnlock.isUnlocked(HexFlora.SISAL, 30));
        Assert.assertTrue(CropUnlock.isUnlocked("Campo de cerezos", 6));
        Assert.assertTrue(CropUnlock.isUnlocked("Campo de perales", 8));
        Assert.assertTrue(CropUnlock.isUnlocked("Campo de manzanos", 11));
        Assert.assertTrue(CropUnlock.isUnlocked("Campo de girasoles", 14));
        Assert.assertTrue(CropUnlock.isUnlocked("Campo de Colza", 17));
        Assert.assertTrue(CropUnlock.isUnlocked(HexFlora.MOSTAZA, 9));
        Assert.assertTrue(CropUnlock.isUnlocked(HexFlora.RABANIZA, 10));
        Assert.assertTrue(CropUnlock.isUnlocked(HexFlora.TREBOL, 13));
        Assert.assertTrue(CropUnlock.isUnlocked(HexFlora.LAVANDA_CAMPO, 16));
        Assert.assertTrue(CropUnlock.isUnlocked(HexFlora.FACELIA, 20));
    }

    @Test
    public void southAfricanCropsUnlockAtConfiguredLevels() {
        Assert.assertFalse(CropUnlock.isUnlocked(HexFlora.LUCERNA, 26));
        Assert.assertTrue(CropUnlock.isUnlocked(HexFlora.LUCERNA, 27));
        Assert.assertTrue(CropUnlock.isUnlocked(HexFlora.LITCHI, 43));
        Assert.assertTrue(CropUnlock.isUnlocked(HexFlora.MACADAMIA, 48));
        Assert.assertTrue(CropUnlock.isUnlocked(HexFlora.AGUACATE, 60));
    }

    @Test
    public void newlyUnlockedListsCrossedThresholdsOnly() {
        Assert.assertTrue(CropUnlock.newlyUnlocked(1, 1).isEmpty());
        List<String> one = CropUnlock.newlyUnlocked(1, 2);
        Assert.assertEquals(1, one.size());
        Assert.assertEquals("Campo de naranjos", one.get(0));
        List<String> jump = CropUnlock.newlyUnlocked(3, 8);
        Assert.assertEquals(4, jump.size());
        Assert.assertEquals("Campo de almendros", jump.get(0));
        Assert.assertEquals(HexFlora.MANGO, jump.get(1));
        Assert.assertEquals("Campo de cerezos", jump.get(2));
        Assert.assertEquals("Campo de perales", jump.get(3));
    }

    @Test
    public void catalogKeepsUnlockOrder() {
        List<String> catalog = CropUnlock.unlockedCatalog(60);
        Assert.assertEquals(19, catalog.size());
        Assert.assertEquals("Campo de naranjos", catalog.get(0));
        Assert.assertEquals(HexFlora.MANGO, catalog.get(2));
        Assert.assertEquals(HexFlora.CAFE, catalog.get(11));
        Assert.assertEquals(HexFlora.LUCERNA, catalog.get(14));
        Assert.assertEquals(HexFlora.SISAL, catalog.get(15));
        Assert.assertEquals(HexFlora.AGUACATE, catalog.get(18));
    }

    @Test
    public void requiredLevelMatchesUnlockTable() {
        Assert.assertEquals(2, CropUnlock.requiredLevel("Campo de naranjos"));
        Assert.assertEquals(5, CropUnlock.requiredLevel(HexFlora.MANGO));
        Assert.assertEquals(16, CropUnlock.requiredLevel(HexFlora.LAVANDA_CAMPO));
        Assert.assertEquals(16, CropUnlock.requiredLevel(HexFlora.CAFE));
        Assert.assertEquals(30, CropUnlock.requiredLevel(HexFlora.SISAL));
        Assert.assertEquals(60, CropUnlock.requiredLevel(HexFlora.AGUACATE));
        Assert.assertEquals(0, CropUnlock.requiredLevel("Mil flores"));
    }
}
