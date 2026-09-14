package com.apiculture.simulator.data.repository;

import com.apiculture.simulator.BuildConfig;
import com.apiculture.simulator.domain.map.PlayableMapRegion;
import com.apiculture.simulator.domain.parcel.GeoJsonLandMask;
import com.apiculture.simulator.domain.parcel.HexParcel;
import com.apiculture.simulator.domain.parcel.HexParcelGenerator;
import com.apiculture.simulator.domain.parcel.LandMask;
import com.apiculture.simulator.domain.parcel.SouthAfricaBounds;
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
 * Regenerar {@code app/src/main/assets/za_hex/overlay.json}: usar JDK 17 y ejecutar
 * <pre>{@code ./gradlew :app:testDebugUnitTest --tests SouthAfricaOverlayJsonExportTest }</pre>
 * (tarda ~1–2 min). Después volver a poner {@code @Ignore} para no ralentizar CI.
 */
@Ignore("Solo ejecutar manualmente para volcar overlay.json a assets")
public class SouthAfricaOverlayJsonExportTest {

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
        File out = new File(cwd, "app/src/main/assets/za_hex/overlay.json");
        if (!out.getParentFile().exists() && new File(cwd, "src/main/assets").exists()) {
            out = new File(cwd, "src/main/assets/za_hex/overlay.json");
        }
        File parent = out.getParentFile();
        if (parent != null) {
            //noinspection ResultOfMethodCallIgnored
            parent.mkdirs();
        }
        return out;
    }

    @Test
    public void writeSouthAfricaOverlayJsonToAssets() throws Exception {
        PlayableMapRegion region = PlayableMapRegion.SOUTH_AFRICA;
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
        List<HexParcel> parcels = generator.generate(SouthAfricaBounds.BOX, region.hexPrefix(), GENERATE_CAP);

        double maxLat = Double.NEGATIVE_INFINITY;
        double minLat = Double.POSITIVE_INFINITY;
        double maxLon = Double.NEGATIVE_INFINITY;
        double minLon = Double.POSITIVE_INFINITY;
        for (HexParcel p : parcels) {
            maxLat = Math.max(maxLat, p.centroidLat);
            minLat = Math.min(minLat, p.centroidLat);
            maxLon = Math.max(maxLon, p.centroidLon);
            minLon = Math.min(minLon, p.centroidLon);
        }

        JSONObject root = IberiaHexOverlayStore.toOverlayJsonDocument(
                parcels, region, BuildConfig.VERSION_CODE);
        root.put("seed", true);

        File out = resolveOverlayOut();
        //noinspection ResultOfMethodCallIgnored
        out.getParentFile().mkdirs();
        Files.write(out.toPath(), root.toString().getBytes(StandardCharsets.UTF_8));

        System.out.println("Sudáfrica overlay: " + parcels.size() + " parcelas ("
                + region.hexTargetAreaKm2() + " km²) bbox centroides lat "
                + minLat + ".." + maxLat + " lon " + minLon + ".." + maxLon
                + " -> " + out.getAbsolutePath());
    }
}
