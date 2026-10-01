package com.apiculture.simulator.domain.game;

import com.apiculture.simulator.domain.parcel.HexFlora;
import com.apiculture.simulator.domain.parcel.HexParcel;
import com.apiculture.simulator.domain.parcel.IberiaBounds;
import org.junit.Assert;
import org.junit.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PollinationOfferCatalogTest {

    private static final double[][] RING = {{0, 0}, {0, 1}, {1, 1}, {1, 0}, {0.5, -0.5}, {-0.5, 0.5}};

    @Test
    public void levelZeroSpawnsNothing() {
        List<HexParcel> parcels = iberiaGrid();
        List<NpcContractFarm> out = PollinationOfferCatalog.spawnFromParcels(
                parcels, IberiaBounds.BOX, LocalDate.of(2026, 4, 20), 0);
        Assert.assertTrue(out.isEmpty());
    }

    @Test
    public void levelTwoAprilIberiaOffersNaranjosOnMediterranean() {
        List<HexParcel> parcels = iberiaGrid();
        List<NpcContractFarm> out = PollinationOfferCatalog.spawnFromParcels(
                parcels, IberiaBounds.BOX, LocalDate.of(2026, 4, 20), 2);
        Assert.assertTrue("expected spring offers, got " + out.size(), out.size() >= 6);
        Set<String> floras = new HashSet<>();
        for (int i = 0; i < out.size(); i++) {
            NpcContractFarm farm = out.get(i);
            Assert.assertNotNull(farm);
            Assert.assertTrue(farm.parcel.id, ClimateUnlock.canBuyParcel(farm.parcel, 2));
            floras.add(HexFlora.canonicalKey(farm.flora));
            Assert.assertTrue(PollinationContractCrops.isOfferedOnParcel(farm.flora, farm.parcel));
            Assert.assertTrue(PollinationContractCrops.daysUntilWork(farm, LocalDate.of(2026, 4, 20))
                    <= PollinationContractCrops.MAX_AHEAD_DAYS);
        }
        Assert.assertTrue("spring Med should offer naranjos, got " + floras,
                floras.contains("Campo de naranjos"));
        Assert.assertFalse(floras.contains(HexFlora.RABANIZA));
    }

    @Test
    public void levelTwoDecemberIberiaIsAlmostEmpty() {
        List<HexParcel> parcels = iberiaGrid();
        List<NpcContractFarm> out = PollinationOfferCatalog.spawnFromParcels(
                parcels, IberiaBounds.BOX, LocalDate.of(2026, 12, 15), 2);
        Assert.assertTrue("Iberia winter should be scarce, got " + out.size() + " " + florasOf(out),
                out.size() <= 3);
    }

    @Test
    public void levelTwoDecemberMadagascarCoversIberianWinter() {
        List<HexParcel> parcels = mdgEastGrid();
        List<NpcContractFarm> out = PollinationOfferCatalog.spawnFromParcels(
                parcels, new com.apiculture.simulator.domain.parcel.BoundingBox(-22.3, -17.6, 48.85, 49.9),
                LocalDate.of(2026, 12, 15), 2);
        Assert.assertTrue("expected austral-summer offers, got " + out.size(), out.size() >= 8);
        Set<String> floras = new HashSet<>();
        for (int i = 0; i < out.size(); i++) {
            Assert.assertTrue(ClimateUnlock.canBuyParcel(out.get(i).parcel, 2));
            floras.add(HexFlora.canonicalKey(out.get(i).flora));
        }
        Assert.assertTrue("Madagascar Dec should cover with summer-shifted annuals, got " + floras,
                floras.contains("Campo de girasoles")
                        || floras.contains(HexFlora.TREBOL)
                        || floras.contains(HexFlora.LITCHI));
        Assert.assertFalse(floras.contains(HexFlora.RABANIZA));
    }

    @Test
    public void septemberMediterraneanOffersFaceliaNotNextSpringTrees() {
        List<HexParcel> parcels = iberiaGrid();
        List<NpcContractFarm> out = PollinationOfferCatalog.spawnFromParcels(
                parcels, IberiaBounds.BOX, LocalDate.of(2026, 9, 20), 4);
        Assert.assertTrue("expected autumn offers, got " + out.size(), out.size() >= 6);
        Set<String> floras = new HashSet<>();
        Set<Integer> cells = new HashSet<>();
        for (int i = 0; i < out.size(); i++) {
            NpcContractFarm farm = out.get(i);
            floras.add(HexFlora.canonicalKey(farm.flora));
            int row = (int) Math.floor((farm.parcel.centroidLat - IberiaBounds.BOX.minLat)
                    / Math.max(0.01, IberiaBounds.BOX.maxLat - IberiaBounds.BOX.minLat)
                    * PollinationOfferCatalog.GRID_ROWS);
            int col = (int) Math.floor((farm.parcel.centroidLon - IberiaBounds.BOX.minLon)
                    / Math.max(0.01, IberiaBounds.BOX.maxLon - IberiaBounds.BOX.minLon)
                    * PollinationOfferCatalog.GRID_COLS);
            cells.add(row * PollinationOfferCatalog.GRID_COLS + col);
            Assert.assertTrue(ClimateUnlock.canBuyParcel(farm.parcel, 4));
        }
        Assert.assertTrue("offers should occupy several grid cells, got " + cells.size(),
                cells.size() >= 4);
        Assert.assertTrue("Sep should be facelia, got " + floras, floras.contains(HexFlora.FACELIA));
        Assert.assertFalse("should not advertise March naranjos in September, got " + floras,
                floras.contains("Campo de naranjos"));
        Assert.assertFalse(floras.contains("Campo de almendros"));
    }

    @Test
    public void bandTwoPicksUnlockedCropNotGirasol() {
        HexParcel med = new HexParcel("hex_0_0", RING, 39.5, -0.4, 1.0, false, 20);
        NpcContractFarm farm = PollinationOfferCatalog.pickForBand(
                med, LocalDate.of(2026, 4, 20), OfferBand.ofLevel(2), 20260420);
        Assert.assertNotNull(farm);
        Assert.assertFalse("Campo de girasoles".equals(HexFlora.canonicalKey(farm.flora)));
    }

    @Test
    public void septemberLowLevelStillHasNpcRotationCrop() {
        HexParcel med = new HexParcel("hex_0_0", RING, 39.5, -0.4, 1.0, false, 20);
        NpcContractFarm farm = PollinationOfferCatalog.pickForBand(
                med, LocalDate.of(2026, 9, 21), OfferBand.ofLevel(2), 20260921);
        Assert.assertNotNull(farm);
        Assert.assertTrue(HexFlora.isPlantation(farm.flora));
    }

    @Test
    public void spawnForBandSpreadsAcrossIberia() {
        List<HexParcel> parcels = iberiaGrid();
        List<NpcContractFarm> out = PollinationOfferCatalog.spawnForBand(
                parcels, OfferBand.ofLevel(5), LocalDate.of(2026, 9, 21), 20260921, new HashSet<>(), 16);
        Assert.assertTrue("expected scattered farms, got " + out.size(), out.size() >= 8);
        double minLat = 90;
        double maxLat = -90;
        double minLon = 180;
        double maxLon = -180;
        Set<Integer> cells = new HashSet<>();
        for (int i = 0; i < out.size(); i++) {
            HexParcel p = out.get(i).parcel;
            minLat = Math.min(minLat, p.centroidLat);
            maxLat = Math.max(maxLat, p.centroidLat);
            minLon = Math.min(minLon, p.centroidLon);
            maxLon = Math.max(maxLon, p.centroidLon);
            int row = (int) Math.floor((p.centroidLat - IberiaBounds.BOX.minLat)
                    / Math.max(0.01, IberiaBounds.BOX.maxLat - IberiaBounds.BOX.minLat)
                    * PollinationOfferCatalog.GRID_ROWS);
            int col = (int) Math.floor((p.centroidLon - IberiaBounds.BOX.minLon)
                    / Math.max(0.01, IberiaBounds.BOX.maxLon - IberiaBounds.BOX.minLon)
                    * PollinationOfferCatalog.GRID_COLS);
            cells.add(row * PollinationOfferCatalog.GRID_COLS + col);
        }
        Assert.assertTrue("lat span " + (maxLat - minLat), maxLat - minLat >= 2.5);
        Assert.assertTrue("lon span " + (maxLon - minLon), maxLon - minLon >= 4.0);
        Assert.assertTrue("cells " + cells.size(), cells.size() >= 6);
    }

    private static String florasOf(List<NpcContractFarm> out) {
        Set<String> floras = new HashSet<>();
        for (int i = 0; i < out.size(); i++) {
            floras.add(HexFlora.canonicalKey(out.get(i).flora));
        }
        return floras.toString();
    }

    private static List<HexParcel> iberiaGrid() {
        List<HexParcel> out = new ArrayList<>();
        double minLat = IberiaBounds.BOX.minLat + 0.4;
        double maxLat = IberiaBounds.BOX.maxLat - 0.4;
        double minLon = IberiaBounds.BOX.minLon + 0.4;
        double maxLon = IberiaBounds.BOX.maxLon - 0.4;
        int n = 0;
        for (int r = 0; r < 12; r++) {
            for (int c = 0; c < 14; c++) {
                double lat = minLat + (maxLat - minLat) * r / 11.0;
                double lon = minLon + (maxLon - minLon) * c / 13.0;
                out.add(new HexParcel("hex_iberia_" + r + "_" + c, RING, lat, lon, 70.0, false, 80));
                n++;
            }
        }
        Assert.assertTrue(n > 80);
        return out;
    }

    private static List<HexParcel> mdgEastGrid() {
        List<HexParcel> out = new ArrayList<>();
        for (int r = 0; r < 10; r++) {
            for (int c = 0; c < 12; c++) {
                double lat = -22.0 + r * 0.45;
                double lon = 48.9 + c * 0.08;
                out.add(new HexParcel("hex_mdg_" + r + "_" + c, RING, lat, lon, 70.0, false, 90));
            }
        }
        return out;
    }
}
