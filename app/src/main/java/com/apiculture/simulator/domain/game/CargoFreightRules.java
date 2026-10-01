package com.apiculture.simulator.domain.game;

/**
 * Flete de miel. La tarifa por kg y km baja al subir los kilos, con un suelo.
 * En carretera y en el mar, cada tramo más largo cobra menos por km.
 * El mar va un 75 % por debajo de la tarifa anterior. Hasta 1.000 km cobra el 100 %
 * y después sigue la escala de tramos, sin bajar del 70 %.
 * Se cobra la ida cargada y el 35 % del regreso en vacío.
 * El traslado entre almacenes usa una base más baja que la venta.
 * El traslado de colmenas de polinización sigue otra tarifa (por hex).
 */
public final class CargoFreightRules {

    public static final double REF_KG = 80.0;
    public static final double ALPHA = 0.45;
    public static final double TRANSFER_BASE = 0.012;
    public static final double FLOOR_RATIO = 0.25;
    public static final double EMPTY_RETURN_FACTOR = 0.35;
    /**
     * El barco factura esta fracción de la tarifa de carretera.
     * Un 75 % por debajo de la tarifa de mar anterior. El descuento por tramos no baja del 70 %.
     */
    public static final double SEA_RATE_RATIO = 0.02734375;

    /** Tope de cada tramo de carretera, en km. El último llega hasta cualquier distancia. */
    private static final double[] ROAD_BAND_KM = {
            20, 50, 100, 200, 400, 800, 1200, 2000, 5000, Double.POSITIVE_INFINITY
    };
    /** Porcentaje de la tarifa en ese tramo: 100, 90, 80, 70, 60, 50, 42, 34, 26 y 18. */
    private static final double[] ROAD_BAND_FACTOR = {
            1.00, 0.90, 0.80, 0.70, 0.60, 0.50, 0.42, 0.34, 0.26, 0.18
    };
    /** Tramos de mar: 100, 95, 90, 85, 80, 70, 75 y 70 %. */
    private static final double[] SEA_BAND_KM = {
            1000, 1500, 2000, 3000, 5000, 8000, 12000, 20000, Double.POSITIVE_INFINITY
    };
    private static final double[] SEA_BAND_FACTOR = {
            1.00, 0.95, 0.90, 0.85, 0.80, 0.70, 0.75, 0.70, 0.70
    };

    private CargoFreightRules() {
    }

    public static double bPerKgKm() {
        return Math.max(0.0, GameBalanceConfig.cargoFreightBPerKgKm);
    }

    /** Venta: el km es la ida, y el regreso en vacío se cuenta al 35 %. */
    public static double costB(double kg, double km) {
        return costB(kg, km, false, km);
    }

    public static double costB(double kg, double loadedKm, boolean transfer, double emptyKm) {
        loadedKm = billedKm(loadedKm);
        emptyKm = billedKm(emptyKm);
        return bill(kg, loadedKm, emptyKm, ratePerKgKm(kg, transfer) * roadDistanceFactor(loadedKm));
    }

    /**
     * Media de la tarifa de carretera según los km de ida.
     * Los primeros 20 km van al 100 %; cada tramo siguiente es más barato.
     */
    public static double roadDistanceFactor(double loadedKm) {
        return distanceFactor(loadedKm, ROAD_BAND_KM, ROAD_BAND_FACTOR);
    }

    /** Los primeros 1.000 km valen 1. Los tramos siguientes siguen la escala del mar. */
    public static double seaDistanceFactor(double loadedKm) {
        return distanceFactor(loadedKm, SEA_BAND_KM, SEA_BAND_FACTOR);
    }

    private static double distanceFactor(double loadedKm, double[] bandKm, double[] bandFactor) {
        if (loadedKm <= 1e-6) {
            return 1.0;
        }
        double left = loadedKm;
        double prev = 0.0;
        double weighted = 0.0;
        for (int i = 0; i < bandKm.length && left > 1e-9; i++) {
            double width = Math.min(left, bandKm[i] - prev);
            weighted += width * bandFactor[i];
            left -= width;
            prev = bandKm[i];
        }
        return weighted / loadedKm;
    }

    /** Travesía: tarifa de barco. El 100 % cubre 1.000 km y el suelo es el 70 %. */
    public static double seaCostB(double kg, double loadedKm, boolean transfer, double emptyKm) {
        loadedKm = billedKm(loadedKm);
        emptyKm = billedKm(emptyKm);
        return bill(kg, loadedKm, emptyKm,
                ratePerKgKm(kg, transfer) * seaRateRatio() * seaDistanceFactor(loadedKm));
    }

    /** 200 m son 0,20 km y se cobran. Por debajo de 5 m queda en cero. */
    public static double billedKm(double km) {
        if (km <= 0.0) {
            return 0.0;
        }
        return Math.round(km * 100.0) / 100.0;
    }

    public static double seaRateRatio() {
        double ratio = GameBalanceConfig.cargoSeaFreightRatio;
        if (ratio <= 0.0) {
            ratio = SEA_RATE_RATIO;
        }
        return ratio;
    }

    private static double bill(double kg, double loadedKm, double emptyKm, double rate) {
        if (kg <= 1e-9 || loadedKm <= 1e-6 || rate <= 0.0) {
            return 0.0;
        }
        double billable = loadedKm + EMPTY_RETURN_FACTOR * Math.max(0.0, emptyKm);
        double cost = Math.round(kg * billable * rate * 100.0) / 100.0;
        if (cost <= 0.0) {
            return 0.01;
        }
        return cost;
    }

    public static double ratePerKgKm(double kg, boolean transfer) {
        double base = transfer ? TRANSFER_BASE : bPerKgKm();
        if (base <= 0.0) {
            base = transfer ? TRANSFER_BASE : 0.02;
        }
        double use = Math.max(kg, REF_KG);
        double rate = base * Math.pow(REF_KG / use, ALPHA);
        return Math.max(base * FLOOR_RATIO, rate);
    }
}
