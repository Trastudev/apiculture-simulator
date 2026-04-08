package com.apiculture.simulator.data.repository;

import android.content.Context;

import com.apiculture.simulator.presentation.map.HexOverlayDiskCache;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Copia JSON precalculados desde {@code assets/hex_overlay_seed/} a
 * {@link HexOverlayDiskCache#getStorageDir(Context)} la primera vez (o si faltan),
 * para evitar regenerar la malla al abrir el mapa tras instalar.
 * <p>
 * Los nombres de archivo deben coincidir con los hashes que genera la app
 * (copiar desde {@code files/hex_overlay} tras una visita al mapa con la misma
 * {@code versionCode} y mismos parámetros de hex). Añadir {@code "seed": true}
 * en el JSON para que no caduque a los 7 días.
 */
public final class HexOverlaySeedInstaller {

    private static final String ASSET_DIR = "hex_overlay_seed";

    private HexOverlaySeedInstaller() {
    }

    public static void installFromAssets(Context context) {
        Context app = context.getApplicationContext();
        try {
            String[] names = app.getAssets().list(ASSET_DIR);
            if (names == null) {
                return;
            }
            File dir = HexOverlayDiskCache.getStorageDir(app);
            byte[] buf = new byte[8192];
            for (String name : names) {
                if (!name.endsWith(".json")) {
                    continue;
                }
                File out = new File(dir, name);
                if (out.exists()) {
                    continue;
                }
                try (InputStream in = app.getAssets().open(ASSET_DIR + "/" + name);
                     FileOutputStream fos = new FileOutputStream(out)) {
                    int n;
                    while ((n = in.read(buf)) != -1) {
                        fos.write(buf, 0, n);
                    }
                    fos.flush();
                    fos.getFD().sync();
                }
            }
        } catch (IOException ignored) {
        }
    }
}
