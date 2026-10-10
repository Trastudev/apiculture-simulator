package com.apiculture.simulator.domain.game;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Horarios de un envío. Las distancias ya vienen medidas; aquí solo se
 * reparte el tiempo para que el camión de destino llegue al puerto a la vez
 * que el barco.
 */
public final class FleetPlanner {

    public static final class Leg {
        public final String title;
        public final String detail;
        public final long departOffsetMs;
        public final long durationMs;

        public Leg(String title, String detail, long departOffsetMs, long durationMs) {
            this.title = title;
            this.detail = detail;
            this.departOffsetMs = Math.max(0L, departOffsetMs);
            this.durationMs = Math.max(0L, durationMs);
        }
    }

    public static final class Plan {
        public final boolean ok;
        @Nullable
        public final String block;
        public final List<Leg> legs;
        public final double costB;
        public final long originDepartMs;
        public final long shipDepartMs;
        public final long destDepartMs;
        public final long road1Ms;
        public final long seaMs;
        public final long emptyToPortMs;

        private Plan(boolean ok, @Nullable String block, List<Leg> legs, double costB,
                long originDepartMs, long shipDepartMs, long destDepartMs,
                long road1Ms, long seaMs, long emptyToPortMs) {
            this.ok = ok;
            this.block = block;
            this.legs = legs == null ? Collections.emptyList() : Collections.unmodifiableList(legs);
            this.costB = Math.max(0.0, costB);
            this.originDepartMs = originDepartMs;
            this.shipDepartMs = shipDepartMs;
            this.destDepartMs = destDepartMs;
            this.road1Ms = road1Ms;
            this.seaMs = seaMs;
            this.emptyToPortMs = emptyToPortMs;
        }

        @NonNull
        public String itinerary() {
            StringBuilder sb = new StringBuilder();
            int n = 1;
            for (Leg leg : legs) {
                if (sb.length() > 0) {
                    sb.append('\n');
                }
                sb.append(n++).append(". ").append(leg.title).append(": ").append(leg.detail);
            }
            return sb.toString();
        }
    }

    private FleetPlanner() {
    }

    @NonNull
    public static Plan blocked(@NonNull String reason) {
        return new Plan(false, reason, Collections.emptyList(), 0, 0, 0, 0, 0, 0, 0);
    }

    /**
     * Mismo territorio: ida cargada y vuelta en vacío.
     */
    @NonNull
    public static Plan road(@NonNull String fromLabel, @NonNull String toLabel,
            double loadedKm, double emptyKm, double truckKmh, double kg, boolean transfer,
            boolean hasTruck, boolean fits) {
        if (!hasTruck) {
            return blocked("No hay un camión libre en ese obrador.");
        }
        if (!fits) {
            return blocked("El camión no tiene capacidad para esa carga.");
        }
        long go = FleetRules.durationMs(loadedKm, truckKmh);
        List<Leg> legs = new ArrayList<>();
        legs.add(new Leg("Camión", fromLabel + " → " + toLabel
                + String.format(java.util.Locale.getDefault(), " (%.2f km)", loadedKm), 0L, go));
        double cost = CargoFreightRules.costB(kg, loadedKm, transfer, emptyKm);
        return new Plan(true, null, legs, cost, 0L, 0L, 0L, go, 0L, 0L);
    }

    /**
     * Tres tramos. {@code emptyToPortKm} es el camión de destino yendo vacío al puerto.
     * Si ese tramo dura más que la travesía más la primera carretera, sale antes que el barco.
     */
    @NonNull
    public static Plan overseas(@NonNull String originWarehouse, @NonNull String originPort,
            @NonNull String destPort, @NonNull String destPlace,
            double road1Km, double seaKm, double seaVisibleKm, double portToDestKm, double emptyToPortKm,
            double emptyReturnKm, double truck1Kmh, double shipKmh, double truck2Kmh,
            double kg, boolean transfer,
            boolean hasOriginTruck, boolean originFits,
            boolean hasShip, boolean shipFits,
            boolean hasDestTruck, boolean destFits) {
        if (!hasShip) {
            return blocked("No hay un barco libre en el puerto de salida.");
        }
        if (!shipFits) {
            return blocked("El barco no tiene capacidad para esa carga.");
        }
        if (!hasOriginTruck || !hasDestTruck) {
            return blocked("Hace falta un camión libre en el obrador de salida y en el de llegada.");
        }
        if (!originFits || !destFits) {
            return blocked("Alguno de los camiones no tiene capacidad para esa carga.");
        }
        long road1 = FleetRules.durationMs(road1Km, truck1Kmh);
        long sea = FleetRules.durationMs(Math.min(seaKm, Math.max(0.0, seaVisibleKm)), shipKmh);
        long loaded2 = FleetRules.durationMs(portToDestKm, truck2Kmh);
        long empty2 = FleetRules.durationMs(emptyToPortKm, truck2Kmh);
        long shipArrive = road1 + sea;
        long destDepart = shipArrive - empty2;
        long shift = destDepart < 0L ? -destDepart : 0L;
        long originDepart = shift;
        long shipDepart = shift + road1;
        destDepart += shift;
        shipArrive += shift;

        List<Leg> legs = new ArrayList<>();
        legs.add(new Leg("Camión al puerto", originWarehouse + " → " + originPort
                + ". Deja la miel y vuelve a su obrador.", originDepart, road1));
        legs.add(new Leg("Barco", originPort + " → " + destPort
                + String.format(java.util.Locale.getDefault(), " (%.2f km de mar)", seaKm), shipDepart, sea));
        legs.add(new Leg("Camión al destino",
                "Sale hacia " + destPort + " y llega a la vez que el barco. Luego va a " + destPlace + ".",
                destDepart, empty2 + loaded2));
        double roadLoaded = road1Km + portToDestKm;
        double roadEmpty = Math.max(0.0, emptyReturnKm - seaKm);
        double cost = CargoFreightRules.costB(kg, roadLoaded, transfer, roadEmpty)
                + CargoFreightRules.seaCostB(kg, seaKm, transfer, seaKm);
        return new Plan(true, null, legs, cost, originDepart, shipDepart, destDepart, road1, sea, empty2);
    }
}
