package com.apiculture.simulator.domain.game;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HexNectarPoolTest {

    @Test
    public void siteFactor_rainIsZero_sunIsAboveOne() {
        Assert.assertEquals(0.0, HexNectarPool.siteFactor(22.0, 0.0), 1e-9);
        Assert.assertEquals(1.25, HexNectarPool.siteFactor(22.0, 1.25), 1e-9);
        Assert.assertEquals(1.0, HexNectarPool.siteExit(1.25), 1e-9);
        Assert.assertEquals(0.7, HexNectarPool.siteExit(0.7), 1e-9);
    }

    @Test
    public void demandFitsPool_collectsAll() {
        HexNectarPool.Patch p = patch("hex_iberia_0_0", "me", 22.0, 1.0, 10.0);
        HexNectarPool.Totals t = HexNectarPool.resolve(Collections.singletonList(p), Collections.emptyMap());
        Assert.assertEquals(10.0, p.localCollected, 1e-6);
        Assert.assertEquals(12.0, p.leftoverPool, 1e-6);
        Assert.assertEquals(0.0, p.leftoverDemand, 1e-9);
        Assert.assertEquals(0.0, p.inboundKg, 1e-9);
        Assert.assertEquals(0.0, p.takenByNeighbors, 1e-9);
        Assert.assertEquals(0.0, p.collectedAbroad, 1e-9);
        Assert.assertEquals(0.0, t.fromNeighborsKg, 1e-9);
        Assert.assertEquals(0.0, t.takenByNeighborsKg, 1e-9);
        Assert.assertEquals(10.0, p.hives.get(0).collectedKg, 1e-6);
    }

    @Test
    public void demandExceedsPool_capsAtPool() {
        HexNectarPool.Patch p = patch("hex_iberia_0_0", "me", 22.0, 1.0, 43.0);
        HexNectarPool.resolve(Collections.singletonList(p), Collections.emptyMap());
        Assert.assertEquals(22.0, p.localCollected, 1e-6);
        Assert.assertEquals(21.0, p.leftoverDemand, 1e-6);
        Assert.assertEquals(0.0, p.leftoverPool, 1e-9);
        Assert.assertEquals(0.0, p.collectedAbroad, 1e-9);
    }

    @Test
    public void sameHexHives_splitPoolByDemand() {
        List<HexNectarPool.HiveShare> hives = new ArrayList<>();
        hives.add(new HexNectarPool.HiveShare("a", 30.0));
        hives.add(new HexNectarPool.HiveShare("b", 10.0));
        HexNectarPool.Patch p = new HexNectarPool.Patch(
                "hex_iberia_0_0", "Romero", "me", 20.0, 1.0, hives);
        HexNectarPool.resolve(Collections.singletonList(p), Collections.emptyMap());
        Assert.assertEquals(20.0, p.localCollected, 1e-6);
        Assert.assertEquals(15.0, p.hives.get(0).collectedKg, 1e-6);
        Assert.assertEquals(5.0, p.hives.get(1).collectedKg, 1e-6);
    }

    @Test
    public void neighborSnapshots_doNotStealOrDonate() {
        HexNectarPool.Patch home = patch("hex_iberia_0_0", "me", 22.0, 1.0, 43.0);
        HexNectarPool.NeighborSnap crowded = snap("hex_iberia_1_0", "other", 1.0, 43.0, 21.0, 0.0);
        HexNectarPool.NeighborSnap leftover = snap("hex_iberia_0_1", "other", 1.0, 4.0, 0.0, 12.0);
        Map<String, HexNectarPool.NeighborSnap> foreign = new HashMap<>();
        foreign.put(crowded.hexId, crowded);
        foreign.put(leftover.hexId, leftover);
        HexNectarPool.resolve(Collections.singletonList(home), foreign);
        Assert.assertEquals(0.0, home.inboundKg, 1e-9);
        Assert.assertEquals(0.0, home.takenByNeighbors, 1e-9);
        Assert.assertEquals(0.0, home.collectedAbroad, 1e-9);
        Assert.assertEquals(22.0, home.localCollected, 1e-6);
    }

    @Test
    public void rainAtHome_collectsNothing() {
        HexNectarPool.Patch home = patch("hex_iberia_0_0", "me", 22.0, 0.0, 10.0);
        HexNectarPool.resolve(Collections.singletonList(home), Collections.emptyMap());
        Assert.assertEquals(0.0, home.localCollected, 1e-9);
        Assert.assertEquals(0.0, home.collectedAbroad, 1e-9);
        Assert.assertEquals(22.0, home.leftoverPool, 1e-6);
    }

    private static HexNectarPool.Patch patch(String hex, String owner, double pool, double site, double demand) {
        List<HexNectarPool.HiveShare> hives = new ArrayList<>();
        hives.add(new HexNectarPool.HiveShare("h1", demand));
        return new HexNectarPool.Patch(hex, "Romero", owner, pool, site, hives);
    }

    private static HexNectarPool.NeighborSnap snap(
            String hex, String owner, double site, double demand, double leftoverDemand, double leftoverPool) {
        Map<String, Double> d = new HashMap<>();
        d.put("Romero", demand);
        Map<String, Double> ld = new HashMap<>();
        ld.put("Romero", leftoverDemand);
        Map<String, Double> lp = new HashMap<>();
        lp.put("Romero", leftoverPool);
        return new HexNectarPool.NeighborSnap(hex, owner, site, d, ld, lp);
    }
}
