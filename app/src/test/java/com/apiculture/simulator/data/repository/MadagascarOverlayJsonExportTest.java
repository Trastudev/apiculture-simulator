package com.apiculture.simulator.data.repository;

import com.apiculture.simulator.BuildConfig;
import com.apiculture.simulator.domain.map.PlayableMapRegion;
import com.apiculture.simulator.domain.parcel.GeoJsonLandMask;
import com.apiculture.simulator.domain.parcel.HexParcel;
import com.apiculture.simulator.domain.parcel.HexParcelGenerator;
import com.apiculture.simulator.domain.parcel.LandMask;
import com.apiculture.simulator.domain.parcel.MadagascarBounds;
import com.apiculture.simulator.presentation.map.MapHexOverlayConfig;

import org.json.JSONObject;
import org.junit.Ignore;
import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

/**
 * Regenerar {@code app/src/main/assets/mdg_hex/overlay.json}:
 * {@code ./gradlew :app:testDebugUnitTest --tests MadagascarOverlayJsonExportTest}
 */
@Ignore("Solo ejecutar manualmente para volcar overlay.json a assets")
public class MadagascarOverlayJsonExportTest {

    private static final int GENERATE_CAP = 32000;

    private static File resolveGeoJson() throws Exception {
        File cwd = new File(System.getProperty("user.dir"));
        File[] cands = {
                new File(cwd, "app/src/main/assets/land/ne_10m_land.geojson"),
                new File(cwd, "src/main/assets/land/ne_10m_land.geojson"),
        };
        for (File f : cands) {
            if (f.isFile()) {
                return f;
            }
        }
        throw new IllegalStateException("ne_10m_land.geojson no encontrado desde " + cwd.getAbsolutePath());
    }

    private static File resolveOverlayOut() {
        File cwd = new File(System.getProperty("user.dir"));
        File out = new File(cwd, "app/src/main/assets/mdg_hex/overlay.json");
        if (!out.getParentFile().exists() && new File(cwd, "src/main/assets").exists()) {
            out = new File(cwd, "src/main/assets/mdg_hex/overlay.json");
        }
        File parent = out.getParentFile();
        if (parent != null) {
            //noinspection ResultOfMethodCallIgnored
            parent.mkdirs();
        }
        return out;
    }

    @Test
    public void writeMadagascarOverlayJsonToAssets() throws Exception {
        PlayableMapRegion region = PlayableMapRegion.MADAGASCAR;
        File geoFile = resolveGeoJson();
        LandMask land;
        try (FileInputStream in = new FileInputStream(geoFile)) {
            land = GeoJsonLandMask.fromInputStream(in);
        }
        HexParcelGenerator generator = new HexParcelGenerator(
                land,
                region.hexTargetAreaKm2(),
                MapHexOverlayConfig.MAP_HEX_LAND_SAMPLES_PER_AXIS,
                HexParcelGenerator.DEFAULT_LAND_FRACTION_INLAND,
                MapHexOverlayConfig.MAP_HEX_MIN_LAND_FRACTION,
                region.gridAnchorLat(),
                region.gridAnchorLon());
        List<HexParcel> parcels = MadagascarBounds.keepPlayable(
                generator.generate(MadagascarBounds.BOX, region.hexPrefix(), GENERATE_CAP));

        JSONObject root = IberiaHexOverlayStore.toOverlayJsonDocument(
                parcels, region, BuildConfig.VERSION_CODE);
        root.put("seed", true);

        File out = resolveOverlayOut();
        out.getParentFile().mkdirs();
        Files.write(out.toPath(), root.toString().getBytes(StandardCharsets.UTF_8));

        System.out.println("Madagascar overlay: " + parcels.size() + " parcelas -> "
                + out.getAbsolutePath());
    }
}
