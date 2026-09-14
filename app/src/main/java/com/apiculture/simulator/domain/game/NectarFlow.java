package com.apiculture.simulator.domain.game;

import com.apiculture.simulator.domain.parcel.HexFlora;

import java.util.List;

/**
 * Intensidad de mielada (0–~1) según flora y día del año.
 * Calendario ibérico por defecto; en el hemisferio sur {@link HexNectarRules} desplaza
 * las especies compartidas medio año, y las sudafricanas usan picos locales.
 */
public final class NectarFlow {

    private NectarFlow() {
    }

    /**
     * Factor de flujo de néctar. 0 = nada que pecorear; 1 = mielada plena de esa flora.
     */
    public static double intensity01(String floraType, int dayOfYear) {
        return intensity01(floraType, dayOfYear, 0, 1.0);
    }

    /**
     * @param bloomShiftDays negativo = floración más temprana (sur / mediterráneo)
     * @param vintage        factor de añada (cosecha del año)
     */
    public static double intensity01(String floraType, int dayOfYear, int bloomShiftDays, double vintage) {
        int d = clampDoy(dayOfYear);
        String k = canonicalFlora(floraType);
        double v = backgroundWeeds(clampDoy(d - bloomShiftDays));
        List<GameBalanceConfig.NectarPeak> peaks = GameBalanceConfig.peaksForFlora(k);
        if (peaks != null) {
            double hMul = Math.max(0.35, vintage);
            for (GameBalanceConfig.NectarPeak p : peaks) {
                int center = p.center + bloomShiftDays;
                while (center < 1) {
                    center += 365;
                }
                while (center > 365) {
                    center -= 365;
                }
                v += gauss(d, center, p.width, p.height * hMul);
            }
        }
        return Math.max(0.0, Math.min(1.20, v));
    }

    private static double backgroundWeeds(int d) {
        if (d < GameBalanceConfig.nectarBgStartDoy || d > GameBalanceConfig.nectarBgEndDoy) {
            return 0.0;
        }
        double span = Math.max(1, GameBalanceConfig.nectarBgEndDoy - GameBalanceConfig.nectarBgStartDoy);
        return GameBalanceConfig.nectarBgBase
                + GameBalanceConfig.nectarBgAmp * Math.sin(Math.PI * (d - GameBalanceConfig.nectarBgStartDoy) / span);
    }

    /** Campana periódica anual (distancia circular en días). */
    private static double gauss(int doy, int center, int width, double height) {
        if (width <= 0) {
            return 0.0;
        }
        int delta = Math.abs(doy - center);
        int circ = Math.min(delta, 365 - delta);
        double x = circ / (double) width;
        if (x > 2.4) {
            return 0.0;
        }
        return height * Math.exp(-0.5 * x * x);
    }

    private static String canonicalFlora(String floraType) {
        return HexFlora.canonicalKey(floraType);
    }

    private static int clampDoy(int dayOfYear) {
        return Math.max(1, Math.min(366, dayOfYear));
    }
}
