package com.apiculture.simulator.domain.game;

import com.apiculture.simulator.data.local.entity.HiveEntity;

/**
 * Coste en € y 1 día de viaje. El beneficio está en el clima del destino
 * (temperatura → salud, mortalidad y miel).
 */
public final class TranshumanceRules {

    public static final int BASE_EUR = 80;
    public static final double EUR_PER_KM = 0.40;
    public static final int MIN_EUR = 80;
    public static final int MAX_EUR = 250;
    public static final int TRAVEL_DAYS = 1;

    private TranshumanceRules() {
    }

    public static int costEuros(double fromLat, double fromLng, double toLat, double toLng) {
        double km = haversineKm(fromLat, fromLng, toLat, toLng);
        int c = (int) Math.round(BASE_EUR + EUR_PER_KM * km);
        return Math.max(MIN_EUR, Math.min(MAX_EUR, c));
    }

    public static boolean isInTransit(HiveEntity hive, int dayKey) {
        return hive != null && hive.transhumanceArrivesDayKey > dayKey;
    }

    public static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double r = 6371.0;
        double p1 = Math.toRadians(lat1);
        double p2 = Math.toRadians(lat2);
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(p1) * Math.cos(p2) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return r * 2.0 * Math.atan2(Math.sqrt(a), Math.sqrt(Math.max(0.0, 1.0 - a)));
    }
}
