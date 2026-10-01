package com.apiculture.simulator.data.repository;

import org.junit.Assert;
import org.junit.Test;

public class PollinationContractRepositoryTest {

    @Test
    public void removeHiveId_dropsOnlyThatHive() {
        Assert.assertEquals("a,c", PollinationContractRepository.removeHiveId("a,b,c", "b"));
        Assert.assertEquals("", PollinationContractRepository.removeHiveId("x", "x"));
        Assert.assertEquals("", PollinationContractRepository.removeHiveId(null, "x"));
        Assert.assertEquals("a,b", PollinationContractRepository.removeHiveId("a, b", "nope"));
    }

    @Test
    public void hiveIdSet_splitsAndTrims() {
        Assert.assertTrue(PollinationContractRepository.hiveIdSet(null).isEmpty());
        Assert.assertEquals(2, PollinationContractRepository.hiveIdSet("a, b").size());
        Assert.assertTrue(PollinationContractRepository.hiveIdSet("a, b").contains("a"));
        Assert.assertTrue(PollinationContractRepository.hiveIdSet("a, b").contains("b"));
    }

}
