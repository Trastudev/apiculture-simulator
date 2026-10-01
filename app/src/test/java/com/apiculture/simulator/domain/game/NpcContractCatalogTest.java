package com.apiculture.simulator.domain.game;

import com.apiculture.simulator.domain.parcel.HexParcel;

import org.junit.Assert;
import org.junit.Test;

public class NpcContractCatalogTest {

    private static HexParcel parcel(String id, double lat, double lon, int elev, boolean coastal) {
        return new HexParcel(id,
                new double[][]{{0, 0}, {0, 1}, {1, 1}, {1, 0}, {0.5, -0.5}, {-0.5, 0.5}},
                lat, lon, 70.0, coastal, elev);
    }

    @Test
    public void farmFor_isDeterministicAndPlantation() {
        HexParcel huerta = parcel("hex_iberia_12_-3", 39.47, -0.38, 15, false);
        NpcContractFarm a = NpcContractCatalog.farmFor(huerta);
        NpcContractFarm b = NpcContractCatalog.farmFor(huerta);
        if (a != null) {
            Assert.assertEquals(a.flora, b.flora);
            Assert.assertEquals(a.npcName, b.npcName);
            Assert.assertTrue(com.apiculture.simulator.domain.parcel.HexFlora.isPlantation(a.flora));
        }
        HexParcel sameId = parcel("hex_iberia_12_-3", 39.47, -0.38, 15, false);
        NpcContractFarm c = NpcContractCatalog.farmFor(sameId);
        Assert.assertEquals(a == null, c == null);
        if (a != null && c != null) {
            Assert.assertEquals(a.estateName, c.estateName);
        }
    }

    @Test
    public void estateNamePrefersEmbeddedPlace() {
        HexParcel named = new HexParcel("hex_iberia_12_-3",
                new double[][]{{0, 0}, {0, 1}, {1, 1}, {1, 0}, {0.5, -0.5}, {-0.5, 0.5}},
                39.47, -0.38, 70.0, false, 15, "Alzira");
        Assert.assertEquals("Alzira", NpcContractCatalog.estateNameFor(named));
        HexParcel fallback = parcel("hex_iberia_12_-3", 39.47, -0.38, 15, false);
        String invented = NpcContractCatalog.estateNameFor(fallback);
        Assert.assertFalse(invented.isEmpty());
        Assert.assertNotEquals("Alzira", invented);
    }

    @Test
    public void mountainIsSparserThanHuerta() {
        int med = 0;
        int mtn = 0;
        for (int q = 0; q < 250; q++) {
            HexParcel levante = parcel("hex_iberia_" + q + "_2", 39.4, -0.3, 20, false);
            if (NpcContractCatalog.isNpcFarm(levante)) {
                med++;
            }
            HexParcel gredos = parcel("hex_iberia_" + q + "_2", 40.25, -5.2, 2100, false);
            if (NpcContractCatalog.isNpcFarm(gredos)) {
                mtn++;
            }
        }
        Assert.assertTrue("huerta should yield farms, got " + med, med > 0);
        Assert.assertTrue("mountain should be sparser: med=" + med + " mtn=" + mtn, mtn <= med);
    }

    @Test
    public void namesMatchOriginAndPortrait() {
        Assert.assertEquals(16, NpcContractCatalog.portraitIndexFor("Mei Lin"));
        Assert.assertEquals(17, NpcContractCatalog.portraitIndexFor("Wei Chen"));
        Assert.assertEquals(20, NpcContractCatalog.portraitIndexFor("Alba Cruz"));
        Assert.assertEquals(21, NpcContractCatalog.portraitIndexFor("Nico Vidal"));
        Assert.assertEquals(22, NpcContractCatalog.portraitIndexFor("Priya Naidoo"));

        HexParcel levante = parcel("hex_iberia_12_-3", 39.47, -0.38, 15, false);
        String levanteName = NpcContractCatalog.npcNameFor(levante);
        Assert.assertTrue(levanteName, levanteName.equals("Núria Soler")
                || levanteName.equals("Vicente Ferrer")
                || levanteName.equals("Laia Puig")
                || levanteName.equals("Mei Lin")
                || levanteName.equals("Wei Chen")
                || levanteName.equals("Nico Vidal")
                || levanteName.equals("Yuki Tanaka")
                || levanteName.equals("Carmen Ríos"));
        Assert.assertFalse(levanteName, levanteName.equals("Thabo Mokoena"));
        Assert.assertEquals(NpcContractCatalog.portraitIndexFor(levanteName),
                indexOfName(levanteName));

        HexParcel basque = parcel("hex_iberia_2_2", 43.15, -2.0, 80, false);
        String basqueName = NpcContractCatalog.npcNameFor(basque);
        Assert.assertTrue(basqueName, basqueName.equals("Amaia Lezeaga")
                || basqueName.equals("Iker Arana")
                || basqueName.equals("Ander Urrutia")
                || basqueName.equals("Elena Martín"));

        HexParcel kzn = parcel("hex_za_40_-20", -28.8, 31.0, 200, false);
        String kznName = NpcContractCatalog.npcNameFor(kzn);
        Assert.assertTrue(kznName, kznName.equals("Nomsa Dlamini")
                || kznName.equals("Sipho Ndlovu")
                || kznName.equals("Priya Naidoo")
                || kznName.equals("Thabo Mokoena"));
        Assert.assertFalse(kznName.contains("Soler"));
    }

    @Test
    public void floraFor_coversMoreThanSpring() {
        java.util.Set<Integer> quarters = new java.util.HashSet<>();
        java.util.Set<String> crops = new java.util.HashSet<>();
        for (int q = 0; q < 80; q++) {
            HexParcel p = parcel("hex_iberia_" + q + "_3", 39.4, -0.3, 20, false);
            if (!NpcContractCatalog.isNpcFarm(p)) {
                continue;
            }
            for (NpcContractFarm farm : NpcContractCatalog.farmsFor(p)) {
                crops.add(farm.flora);
                quarters.add(NpcContractCatalog.bloomQuarter(p, farm.flora));
            }
        }
        Assert.assertTrue("need off-season crops, got " + crops, crops.contains("Campo de girasoles")
                || crops.contains(com.apiculture.simulator.domain.parcel.HexFlora.LAVANDA_CAMPO)
                || crops.contains(com.apiculture.simulator.domain.parcel.HexFlora.TREBOL)
                || crops.contains(com.apiculture.simulator.domain.parcel.HexFlora.FACELIA)
                || crops.contains(com.apiculture.simulator.domain.parcel.HexFlora.MOSTAZA)
                || crops.contains(com.apiculture.simulator.domain.parcel.HexFlora.RABANIZA));
        Assert.assertTrue("contracts should span seasons, quarters=" + quarters, quarters.size() >= 3);
    }

    @Test
    public void farmsFor_sixDistinctCropsCoverSeasonIncludingWinter() {
        HexParcel iberia = null;
        for (int q = 0; q < 200 && iberia == null; q++) {
            HexParcel p = parcel("hex_iberia_" + q + "_4", 39.4, -0.3, 20, false);
            if (NpcContractCatalog.isNpcFarm(p)) {
                iberia = p;
            }
        }
        Assert.assertNotNull(iberia);
        java.util.List<NpcContractFarm> iberiaFarms = NpcContractCatalog.farmsFor(iberia);
        Assert.assertEquals(NpcContractCatalog.CONTRACTS_PER_FARM, iberiaFarms.size());
        java.util.Set<String> iberiaCrops = new java.util.HashSet<>();
        int iberiaMin = 365;
        int iberiaMax = 1;
        for (NpcContractFarm f : iberiaFarms) {
            Assert.assertTrue(iberiaCrops.add(f.flora));
            int c = NpcContractCatalog.bloomCenterDoy(iberia, f.flora);
            iberiaMin = Math.min(iberiaMin, c);
            iberiaMax = Math.max(iberiaMax, c);
        }
        Assert.assertFalse("Iberia winter should stay scarce (no rabaniza), crops=" + iberiaCrops,
                iberiaCrops.contains(com.apiculture.simulator.domain.parcel.HexFlora.RABANIZA));
        Assert.assertTrue(iberiaCrops.contains("Campo de naranjos"));
        Assert.assertTrue(iberiaCrops.contains(com.apiculture.simulator.domain.parcel.HexFlora.FACELIA));
        Assert.assertTrue("Iberia should start by spring, min=" + iberiaMin, iberiaMin <= 100);
        Assert.assertTrue("Iberia should span into late season, max=" + iberiaMax + " min=" + iberiaMin,
                iberiaMax >= 170);

        HexParcel za = null;
        for (int q = 0; q < 200 && za == null; q++) {
            HexParcel p = parcel("hex_za_" + q + "_-8", -25.7, 28.2, 1400, false);
            if (NpcContractCatalog.isNpcFarm(p)) {
                za = p;
            }
        }
        Assert.assertNotNull(za);
        java.util.List<NpcContractFarm> zaFarms = NpcContractCatalog.farmsFor(za);
        Assert.assertEquals(NpcContractCatalog.CONTRACTS_PER_FARM, zaFarms.size());
        java.util.Set<String> zaCrops = new java.util.HashSet<>();
        boolean hasSepPlus = false;
        boolean hasThroughJune = false;
        boolean hasZaWinter = false;
        for (NpcContractFarm f : zaFarms) {
            Assert.assertTrue(zaCrops.add(f.flora));
            int c = NpcContractCatalog.bloomCenterDoy(za, f.flora);
            if (c >= 240 || c <= 20) {
                hasSepPlus = true;
            }
            if (c >= 60 && c <= 180) {
                hasThroughJune = true;
            }
            if (c >= 150 && c <= 230) {
                hasZaWinter = true;
            }
        }
        Assert.assertTrue("ZA winter gap should be rabaniza, crops=" + zaCrops,
                zaCrops.contains(com.apiculture.simulator.domain.parcel.HexFlora.RABANIZA));
        Assert.assertTrue("ZA should cover Jun–Aug winter", hasZaWinter);
        Assert.assertTrue("ZA should cover Sep start", hasSepPlus);
        Assert.assertTrue("ZA should cover through June", hasThroughJune);
    }

    @Test
    public void openFarmsFor_onlyHorizonAndShortSlots() {
        HexParcel iberia = null;
        for (int q = 0; q < 200 && iberia == null; q++) {
            HexParcel p = parcel("hex_iberia_" + q + "_4", 39.4, -0.3, 20, false);
            if (NpcContractCatalog.isNpcFarm(p)) {
                iberia = p;
            }
        }
        Assert.assertNotNull(iberia);
        java.util.List<int[]> slots = NpcContractCatalog.packSlots(iberia, "Campo de girasoles");
        Assert.assertTrue("need several short tramos, got " + slots.size(), slots.size() >= 2);
        for (int[] slot : slots) {
            Assert.assertTrue(slot[2] >= 5 && slot[2] <= 21);
        }
        java.time.LocalDate inBloom = java.time.LocalDate.ofYearDay(2026, 200);
        java.util.List<NpcContractFarm> open = NpcContractCatalog.openFarmsFor(iberia, inBloom);
        for (NpcContractFarm f : open) {
            Assert.assertTrue(f.terms.workDays >= 5 && f.terms.workDays <= 21);
            int until = FloraBloomWindow.daysUntilStart(f.terms.startDoy, 200);
            Assert.assertTrue(until <= PollinationContractRules.horizonDays());
            Assert.assertFalse(FloraBloomWindow.nextDateOfDoy(inBloom, f.terms.startDoy).isBefore(inBloom));
            int minPts = (int) Math.round(f.terms.minPct * 100);
            Assert.assertTrue(minPts >= 40 && minPts <= 60);
        }
        int sunflowerStart = slots.get(0)[0];
        java.time.LocalDate startDay = java.time.LocalDate.ofYearDay(2026, Math.min(365, sunflowerStart));
        java.util.List<NpcContractFarm> onStart = NpcContractCatalog.openFarmsFor(iberia, startDay);
        boolean listedSunflower = false;
        for (NpcContractFarm f : onStart) {
            Assert.assertFalse(FloraBloomWindow.nextDateOfDoy(startDay, f.terms.startDoy).isBefore(startDay));
            if ("Campo de girasoles".equals(f.flora) && f.terms.startDoy == sunflowerStart) {
                listedSunflower = true;
            }
        }
        Assert.assertTrue(listedSunflower);
        java.time.LocalDate afterStart = startDay.plusDays(1);
        java.util.List<NpcContractFarm> gone = NpcContractCatalog.openFarmsFor(iberia, afterStart);
        for (NpcContractFarm f : gone) {
            Assert.assertFalse("Campo de girasoles".equals(f.flora) && f.terms.startDoy == sunflowerStart);
        }
        java.time.LocalDate offSeason = java.time.LocalDate.ofYearDay(2026, 320);
        java.util.List<NpcContractFarm> winter = NpcContractCatalog.openFarmsFor(iberia, offSeason);
        for (NpcContractFarm f : winter) {
            int until = FloraBloomWindow.daysUntilStart(f.terms.startDoy, 320);
            Assert.assertTrue("listed far start " + f.flora + " until=" + until, until <= 14);
        }
    }

    @Test
    public void farmForSlot_matchesExactStartNotLaterTramo() {
        HexParcel iberia = null;
        for (int q = 0; q < 200 && iberia == null; q++) {
            HexParcel p = parcel("hex_iberia_" + q + "_4", 39.4, -0.3, 20, false);
            if (NpcContractCatalog.isNpcFarm(p)) {
                iberia = p;
            }
        }
        Assert.assertNotNull(iberia);
        java.util.List<int[]> slots = NpcContractCatalog.packSlots(iberia, "Campo de girasoles");
        Assert.assertTrue(slots.size() >= 2);
        NpcContractFarm first = NpcContractCatalog.farmForSlot(iberia, "Campo de girasoles", slots.get(0)[0]);
        NpcContractFarm second = NpcContractCatalog.farmForSlot(iberia, "Campo de girasoles", slots.get(1)[0]);
        Assert.assertNotNull(first);
        Assert.assertNotNull(second);
        Assert.assertEquals(slots.get(0)[0], first.terms.startDoy);
        Assert.assertEquals(slots.get(1)[0], second.terms.startDoy);
        Assert.assertNotEquals(first.terms.startDoy, second.terms.startDoy);
        Assert.assertNull(NpcContractCatalog.farmFor(iberia, "Campo de girasoles", 999, null));
    }

    private static int indexOfName(String name) {
        String[] all = {
                "Núria Soler", "Vicente Ferrer", "Elena Martín", "Amaia Lezeaga", "Thabo Mokoena",
                "João Ferreira", "Carmen Ríos", "Iker Arana", "Fatima El Amrani", "Pieter van Zyl",
                "Laia Puig", "Manuel Ortega", "Sofia Almeida", "Nomsa Dlamini", "Ander Urrutia",
                "Rosa Beltrán", "Mei Lin", "Wei Chen", "Yuki Tanaka", "Hiroshi Nakamura",
                "Alba Cruz", "Nico Vidal", "Priya Naidoo", "Sipho Ndlovu"
        };
        for (int i = 0; i < all.length; i++) {
            if (all[i].equals(name)) {
                return i;
            }
        }
        return -1;
    }
}
