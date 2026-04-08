package com.apiculture.simulator.domain.parcel;

import org.junit.Assert;
import org.junit.Test;

import java.util.List;

public class HexGeometryTest {

    @Test
    public void areaFromSide_matchesFormula() {
        double s = 3000;
        double expected = 1.5 * Math.sqrt(3) * s * s;
        Assert.assertEquals(expected, HexGeometry.areaM2FromSide(s), 1e-6);
    }

    @Test
    public void generator_producesParcels_whenAllLand() {
        BoundingBox gen = new BoundingBox(40.35, 40.65, -3.75, -3.35);
        LandMask allLand = (lat, lon) -> true;
        List<HexParcel> parcels = new HexParcelGenerator(allLand, 70.0).generate(gen, "test-all");
        Assert.assertFalse(parcels.isEmpty());
    }

    @Test
    public void generator_producesParcels_insideRectangleLand() {
        // Margen amplio: los hexág. sobresalen del bbox de generación; si “tierra” es muy ajustada,
        // el muestreo marca parcelas costeras o las descarta.
        BoundingBox land = new BoundingBox(35.0, 45.0, -10.0, 3.0);
        LandMask mask = new RectangleLandMask(land);
        BoundingBox gen = new BoundingBox(40.35, 40.65, -3.75, -3.35);
        HexParcelGenerator g = new HexParcelGenerator(mask, 70.0);
        List<HexParcel> parcels = g.generate(gen, "test-es");
        Assert.assertFalse(parcels.isEmpty());
        for (HexParcel p : parcels) {
            Assert.assertTrue(p.areaKm2 >= HexParcelGenerator.MIN_AREA_KM2 - 1
                    && p.areaKm2 <= HexParcelGenerator.MAX_AREA_KM2 + 1);
            Assert.assertFalse(p.coastal);
        }
    }
}
