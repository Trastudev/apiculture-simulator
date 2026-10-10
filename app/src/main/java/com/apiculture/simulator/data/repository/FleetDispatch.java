package com.apiculture.simulator.data.repository;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.apiculture.simulator.ApicultureApp;
import com.apiculture.simulator.data.local.AppDatabase;
import com.apiculture.simulator.data.local.entity.CargoTripEntity;
import com.apiculture.simulator.data.local.entity.HexParcelOwnershipEntity;
import com.apiculture.simulator.domain.game.CargoFreightRules;
import com.apiculture.simulator.domain.game.CargoTripRules;
import com.apiculture.simulator.domain.game.ExoticHoneyRules;
import com.apiculture.simulator.domain.game.FleetPlanner;
import com.apiculture.simulator.domain.game.FleetRules;
import com.apiculture.simulator.domain.game.TradeAccessRules;
import com.apiculture.simulator.domain.game.TranshumanceRules;
import com.apiculture.simulator.domain.map.EncodedPolyline;
import com.apiculture.simulator.domain.map.LocalGraphHopper;
import com.apiculture.simulator.domain.map.PlayableMapRegion;
import com.apiculture.simulator.domain.map.ProvincialMarket;
import com.apiculture.simulator.domain.map.ProvincialMarketCatalog;
import com.apiculture.simulator.domain.map.RoadPath;
import com.apiculture.simulator.domain.map.SeaRoute;
import com.apiculture.simulator.domain.map.Seaport;
import com.apiculture.simulator.domain.map.SeaportCatalog;
import com.apiculture.simulator.domain.parcel.HexParcel;
import com.apiculture.simulator.domain.parcel.WarehouseRules;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Arma la ruta de un envío de miel: camión, y si cambia de territorio,
 * camión hasta el puerto, barco y camión que llega a la vez que el barco.
 */
public final class FleetDispatch {

    private FleetDispatch() {
    }

    private static boolean liveTrips(@NonNull Context context) {
        return TruckLivePrefs.isEnabled(context) || GameServer.enabled();
    }

    /** El camión recién comprado sale de la tienda y se queda en el almacén al llegar. */
    public static void deliverNewTruck(@NonNull Context context, @Nullable String ownerId,
            @NonNull String hexId, double shopLat, double shopLng) {
        FleetStore.Vehicle truck = null;
        for (FleetStore.Vehicle v : FleetStore.vehicles(context, ownerId)) {
            if (v.isTruck() && hexId.equals(v.homeId) && "delivery".equals(v.cargoTripId)) {
                truck = v;
            }
        }
        HexParcel parcel = parcel(context, hexId);
        if (truck == null || parcel == null) {
            if (truck != null) {
                FleetStore.releaseVehicle(context, ownerId, truck.id);
            }
            return;
        }
        if (Double.isNaN(shopLat) || Double.isNaN(shopLng)) {
            double[] shop = nearestShop(context, parcel);
            shopLat = shop[0];
            shopLng = shop[1];
        }
        double[] dock = HoneyLogistics.warehouseDock(context, ownerId, parcel);
        double[] from = new double[]{shopLat, shopLng};
        RoadPath path = road(context, from, dock);
        String name = "Obrador";
        for (HexParcelOwnershipEntity row : warehouses(context, ownerId)) {
            if (row != null && hexId.equals(row.hexId)) {
                name = label(row);
                break;
            }
        }
        insertRoad(context, ownerId, UUID.randomUUID().toString(), truck, "", 0, 0, true,
                CargoTripEntity.KIND_DELIVERY, CargoTripEntity.LEG_DELIVER,
                "Tienda", null, from,
                name, hexId, dock[0], dock[1],
                dock, name, hexId,
                path, System.currentTimeMillis(),
                null, null, 0, 0);
    }

    @NonNull
    private static double[] nearestShop(@NonNull Context context, @NonNull HexParcel parcel) {
        PlayableMapRegion region = PlayableMapRegion.fromHexId(parcel.id);
        if (region == null) {
            region = PlayableMapRegion.IBERIA;
        }
        ProvincialMarket best = null;
        double bestKm = Double.MAX_VALUE;
        for (ProvincialMarket market : ProvincialMarketCatalog.resolve(context, region)) {
            if (market == null || market.local || market.international) {
                continue;
            }
            double km = TranshumanceRules.haversineKm(parcel.centroidLat, parcel.centroidLon,
                    market.lat, market.lng);
            if (km < bestKm) {
                best = market;
                bestKm = km;
            }
        }
        if (best == null) {
            return new double[]{parcel.centroidLat, parcel.centroidLon};
        }
        return new double[]{best.lat + 0.08, best.lng + 0.08};
    }

    @NonNull
    public static HoneyLogistics.WholesalePreview previewSale(@NonNull Context context,
            @Nullable String ownerId, @NonNull ProvincialMarket dest, @Nullable String flora, double kg) {
        return previewSale(context, ownerId, dest, flora, kg, null);
    }

    @NonNull
    public static HoneyLogistics.WholesalePreview previewSale(@NonNull Context context,
            @Nullable String ownerId, @NonNull ProvincialMarket dest, @Nullable String flora, double kg,
            @Nullable String truckId) {
        Quote quote = quoteSale(context, ownerId, dest, flora, kg, truckId);
        if (quote.missing) {
            return new HoneyLogistics.WholesalePreview(null, 0, 0, true, false, 0);
        }
        return new HoneyLogistics.WholesalePreview(quote.label, quote.km, quote.durationMs,
                false, quote.geodesic, quote.costB, quote.itinerary, quote.block);
    }

    @NonNull
    public static HoneyLogistics.Result dispatchSale(@NonNull Context context, @Nullable String ownerId,
            @NonNull String flora, double kg, double offeredPrice, @NonNull ProvincialMarket dest,
            @NonNull EconomyRepository economy, @NonNull MarketRepository market) {
        return dispatchSale(context, ownerId, flora, kg, offeredPrice, dest, economy, market, null);
    }

    @NonNull
    public static HoneyLogistics.Result dispatchSale(@NonNull Context context, @Nullable String ownerId,
            @NonNull String flora, double kg, double offeredPrice, @NonNull ProvincialMarket dest,
            @NonNull EconomyRepository economy, @NonNull MarketRepository market,
            @Nullable String truckId) {
        if (GameServer.enabled() && !GameServer.isAvailable()) {
            return HoneyLogistics.Result.FAILED;
        }
        double owned = economy.getHoneyStockForFlora(flora);
        if (owned > 1e-9 && kg > owned && kg - owned <= 0.011) {
            kg = owned;
        }
        Quote quote = quoteSale(context, ownerId, dest, flora, kg, truckId);
        if (quote.block != null) {
            return HoneyLogistics.Result.NO_FLEET;
        }
        if (quote.missing || quote.source == null) {
            return HoneyLogistics.Result.FAILED;
        }
        if (dest.international && !TradeAccessStore.marketPaid(context, ownerId, dest.salesId())) {
            return HoneyLogistics.Result.NO_DEMAND;
        }
        boolean exotic = ExoticHoneyRules.isExotic(dest.region, flora);
        if (exotic && !dest.international) {
            return HoneyLogistics.Result.NO_DEMAND;
        }
        if (!exotic && market.remainingCapacityKg(dest, flora) + 1e-6 < kg) {
            return HoneyLogistics.Result.NO_DEMAND;
        }
        double price = offeredPrice;
        if (exotic) {
            double sold = FleetStore.exoticSoldKg(context, ownerId, dest.salesId());
            if (ExoticHoneyRules.remainingKg(sold) + 1e-6 < kg) {
                return HoneyLogistics.Result.NO_DEMAND;
            }
            double capital = market.priceEurPerKgForFlora(flora, null);
            price = capital * ExoticHoneyRules.MULTIPLIER_IN_QUOTA;
        } else if (price <= 0) {
            price = market.priceEurPerKgForFlora(flora, dest);
        }
        if (!take(context, economy, ownerId, quote.source.hexId, flora, kg)) {
            return HoneyLogistics.Result.FAILED;
        }
        if (quote.costB > 0 && !economy.trySpend(quote.costB,
                "Transporte de " + EconomyRepository.formatKg(kg) + " kg de miel de "
                        + flora + " al mercado")) {
            giveBack(context, economy, ownerId, quote.source.hexId, flora, kg);
            return HoneyLogistics.Result.NO_CASH;
        }
        market.recordSaleVolume(flora, kg, dest);
        if (exotic) {
            FleetStore.addExoticSold(context, ownerId, dest.salesId(), kg);
        }
        if (!liveTrips(context) && !GameServer.enabled()) {
            economy.creditSaleProceeds(flora, kg, price,
                    EconomyRepository.saleConcept("en el mercado", flora, kg));
            return HoneyLogistics.Result.INSTANT;
        }
        List<FleetStore.Vehicle> held = new ArrayList<>();
        try {
            hold(context, ownerId, quote, held, kg);
            long now = System.currentTimeMillis();
            String shipment = UUID.randomUUID().toString();
            if (quote.cross) {
                insertRoad(context, ownerId, shipment, quote.originTruck, flora, kg, price, exotic,
                        CargoTripEntity.KIND_WHOLESALE, CargoTripEntity.LEG_HAUL_TRUCK,
                        quote.sourceLabel, quote.source.hexId, quote.originDock,
                        quote.originPort.name, quote.originPort.id, quote.originPort.lat, quote.originPort.lng,
                        quote.originDock, quote.sourceLabel, quote.source.hexId,
                        quote.road1, now + quote.plan.originDepartMs, null, null, 0, 0);
                insertSea(context, ownerId, shipment, quote.ship, flora, kg,
                        quote.originPort, quote.destPort, quote.seaPath,
                        now + quote.plan.shipDepartMs, quote.plan.seaMs);
                insertRoad(context, ownerId, shipment, quote.destTruck, flora, kg, price, exotic,
                        CargoTripEntity.KIND_WHOLESALE, CargoTripEntity.LEG_PICKUP,
                        quote.destHomeLabel, quote.destHome.hexId, quote.destHomeDock,
                        quote.destPort.name, quote.destPort.id, quote.destPort.lat, quote.destPort.lng,
                        quote.destHomeDock, quote.destHomeLabel, quote.destHome.hexId,
                        quote.emptyToPort, now + quote.plan.destDepartMs,
                        dest.name, dest.hexId, dest.lat, dest.lng);
            } else {
                insertRoad(context, ownerId, shipment, quote.originTruck, flora, kg, price, exotic,
                        CargoTripEntity.KIND_WHOLESALE, CargoTripEntity.LEG_DELIVER,
                        quote.sourceLabel, quote.source.hexId, quote.originDock,
                        dest.name, dest.hexId, dest.lat, dest.lng,
                        quote.originDock, quote.sourceLabel, quote.source.hexId,
                        quote.road1, now, null, null, 0, 0);
            }
        } catch (RuntimeException ex) {
            release(context, ownerId, held);
            giveBack(context, economy, ownerId, quote.source.hexId, flora, kg);
            if (quote.costB > 0) {
                economy.addToBalance(quote.costB, "Devolución del transporte al mercado");
            }
            return HoneyLogistics.Result.FAILED;
        }
        return HoneyLogistics.Result.STARTED;
    }

    public static final class TransferPreview {
        public final double km;
        public final long durationMs;
        public final double costB;
        @Nullable
        public final String block;

        public TransferPreview(double km, long durationMs, double costB, @Nullable String block) {
            this.km = km;
            this.durationMs = durationMs;
            this.costB = costB;
            this.block = block;
        }
    }

    @Nullable
    public static String transfer(@NonNull Context context, @Nullable String ownerId,
            @NonNull EconomyRepository economy, @NonNull String fromHex, @NonNull String flora,
            double kg, @NonNull String toHex, double extraKg, @Nullable String extraHex) {
        if (GameServer.enabled() && !GameServer.isAvailable()) {
            return "No hay conexión con el servidor. No se puede realizar esta acción.";
        }
        Quote quote = stageTransfer(context, ownerId, economy, fromHex, flora, kg, toHex, extraKg, extraHex);
        if (quote.block != null) {
            return quote.block;
        }
        double moving = quote.honeyKg;
        HexParcelOwnershipEntity extra = quote.extraStop;
        if (quote.costB > 0 && !economy.trySpend(quote.costB,
                "Transporte de miel de " + flora + " entre obradores")) {
            return "Saldo insuficiente para el transporte.";
        }
        if (!take(context, economy, ownerId, fromHex, flora, moving)) {
            if (quote.costB > 0) {
                economy.addToBalance(quote.costB, "Devolución del transporte entre obradores");
            }
            return "No se pudo reservar la miel.";
        }
        if (!liveTrips(context) && !GameServer.enabled()) {
            economy.addHoney(flora, kg);
            WarehouseHoneyStore.add(context, ownerId, toHex, flora, kg);
            if (extra != null) {
                economy.addHoney(flora, extraKg);
                WarehouseHoneyStore.add(context, ownerId, extraHex, flora, extraKg);
            }
            return null;
        }
        try {
            if (quote.cross) {
                holdOverseas(context, ownerId, quote, flora, moving, 0, false,
                        CargoTripEntity.KIND_TRANSFER, label(row(warehouses(context, ownerId), toHex)),
                        toHex, quote.destLat, quote.destLng);
            } else {
                FleetStore.Vehicle held = FleetStore.reserveHoneyTruck(context, ownerId, fromHex, moving);
                quote.originTruck = held;
                String nextLabel = extra != null ? label(extra) : null;
                double[] chain = extra != null
                        ? HoneyLogistics.warehouseDock(context, ownerId, parcel(context, extra.hexId))
                        : new double[]{0, 0};
                HexParcelOwnershipEntity to = row(warehouses(context, ownerId), toHex);
                insertRoad(context, ownerId, UUID.randomUUID().toString(), held, flora, moving, 0, false,
                        CargoTripEntity.KIND_TRANSFER, CargoTripEntity.LEG_TRANSFER,
                        quote.sourceLabel, fromHex, quote.originDock,
                        label(to), toHex, to.warehouseLat, to.warehouseLng,
                        quote.originDock, quote.sourceLabel, fromHex,
                        quote.road1, System.currentTimeMillis(),
                        nextLabel, extra != null ? extra.hexId : null,
                        chain[0], chain[1], extraKg);
            }
        } catch (RuntimeException ex) {
            if (quote.originTruck != null) {
                FleetStore.releaseVehicle(context, ownerId, quote.originTruck.id);
            }
            if (quote.ship != null) {
                FleetStore.releaseVehicle(context, ownerId, quote.ship.id);
            }
            if (quote.destTruck != null) {
                FleetStore.releaseVehicle(context, ownerId, quote.destTruck.id);
            }
            giveBack(context, economy, ownerId, fromHex, flora, moving);
            if (quote.costB > 0) {
                economy.addToBalance(quote.costB, "Devolución del transporte entre obradores");
            }
            return "No se pudo salir.";
        }
        return null;
    }

    @NonNull
    public static TransferPreview previewTransfer(@NonNull Context context, @Nullable String ownerId,
            @NonNull EconomyRepository economy, @NonNull String fromHex, @NonNull String flora,
            double kg, @NonNull String toHex) {
        Quote quote = stageTransfer(context, ownerId, economy, fromHex, flora, kg, toHex, 0, null);
        if (quote.block != null) {
            return new TransferPreview(0, 0, 0, quote.block);
        }
        return new TransferPreview(quote.km, quote.durationMs, quote.costB, null);
    }

    @NonNull
    private static Quote stageTransfer(@NonNull Context context, @Nullable String ownerId,
            @NonNull EconomyRepository economy, @NonNull String fromHex, @NonNull String flora,
            double kg, @NonNull String toHex, double extraKg, @Nullable String extraHex) {
        Quote quote = new Quote();
        if (ownerId == null || kg <= 1e-9 || fromHex.equals(toHex)) {
            quote.block = "Elige otro obrador y una cantidad.";
            return quote;
        }
        List<HexParcelOwnershipEntity> rows = warehouses(context, ownerId);
        WarehouseHoneyStore.reconcile(context, economy, ownerId, rows);
        HexParcelOwnershipEntity from = row(rows, fromHex);
        HexParcelOwnershipEntity to = row(rows, toHex);
        if (from == null || to == null) {
            quote.block = "No se encuentra el obrador.";
            return quote;
        }
        double moving = kg;
        HexParcelOwnershipEntity extra = null;
        if (extraHex != null && extraKg > 1e-9) {
            extra = row(rows, extraHex);
            if (extra == null) {
                quote.block = "La parada extra no existe.";
                return quote;
            }
            if (PlayableMapRegion.fromHexId(fromHex) != PlayableMapRegion.fromHexId(toHex)
                    || PlayableMapRegion.fromHexId(fromHex) != PlayableMapRegion.fromHexId(extraHex)) {
                quote.block = "La parada extra solo vale dentro del mismo territorio.";
                return quote;
            }
            moving += extraKg;
        }
        if (WarehouseHoneyStore.kg(context, ownerId, fromHex, flora) + 1e-6 < moving) {
            quote.block = "En ese obrador no hay tanta miel de ese tipo.";
            return quote;
        }
        double room = WarehouseRules.capacityKg(WarehouseRules.levelOf(to))
                - WarehouseHoneyStore.totalAt(context, ownerId, toHex);
        if (room + 1e-6 < kg) {
            quote.block = "El obrador de destino no tiene sitio.";
            return quote;
        }
        if (extra != null) {
            double room2 = WarehouseRules.capacityKg(WarehouseRules.levelOf(extra))
                    - WarehouseHoneyStore.totalAt(context, ownerId, extraHex);
            if (room2 + 1e-6 < extraKg) {
                quote.block = "La parada extra no tiene sitio.";
                return quote;
            }
        }
        FleetStore.ensureStarter(context, ownerId, fromHex);
        FleetStore.Vehicle truck = freeTruck(context, ownerId, fromHex, moving);
        if (liveTrips(context) && truck == null) {
            quote.block = "No hay un camión libre con capacidad en el obrador de salida.";
            return quote;
        }
        boolean cross = PlayableMapRegion.fromHexId(fromHex) != PlayableMapRegion.fromHexId(toHex);
        if (cross && extra != null) {
            quote.block = "La parada extra solo vale dentro del mismo territorio.";
            return quote;
        }
        quote.source = from;
        quote.sourceLabel = label(from);
        quote.honeyKg = moving;
        quote.extraStop = extra;
        quote.extraKg = extraKg;
        quote.originDock = HoneyLogistics.warehouseDock(context, ownerId, parcel(context, fromHex));
        quote.destLat = to.warehouseLat;
        quote.destLng = to.warehouseLng;
        if (!cross) {
            double[] destDock = HoneyLogistics.warehouseDock(context, ownerId, parcel(context, toHex));
            RoadPath path = road(context, quote.originDock, destDock);
            double via = path.distanceKm;
            double empty = via;
            if (extra != null) {
                double[] extraDock = HoneyLogistics.warehouseDock(context, ownerId, parcel(context, extraHex));
                RoadPath leg2 = road(context, destDock, extraDock);
                RoadPath direct = road(context, quote.originDock, extraDock);
                if (!FleetRules.detourWithinLimit(direct.distanceKm, via + leg2.distanceKm)) {
                    quote.block = "Esa parada alarga la ruta más de un 25 %.";
                    return quote;
                }
                via += leg2.distanceKm;
                empty = leg2.distanceKm + road(context, extraDock, quote.originDock).distanceKm;
            }
            quote.km = via;
            quote.costB = CargoFreightRules.costB(moving, via, true, empty);
            quote.road1 = path;
            quote.plan = FleetPlanner.road(quote.sourceLabel, label(to), path.distanceKm, empty,
                    truck != null ? FleetRules.speedKmh(FleetRules.Kind.TRUCK, truck.level) : 70,
                    moving, true, truck != null || !liveTrips(context), true);
            quote.durationMs = quote.plan.road1Ms;
            quote.cross = false;
        } else {
            Quote overseas = fillOverseas(context, ownerId, from, quote.originDock, to.warehouseLat, to.warehouseLng,
                    label(to), toHex, moving, true);
            overseas.honeyKg = moving;
            overseas.extraStop = extra;
            overseas.extraKg = extraKg;
            overseas.destLat = to.warehouseLat;
            overseas.destLng = to.warehouseLng;
            return overseas;
        }
        return quote;
    }

    @NonNull
    private static Quote quoteSale(@NonNull Context context, @Nullable String ownerId,
            @NonNull ProvincialMarket dest, @Nullable String flora, double kg,
            @Nullable String truckId) {
        Quote quote = new Quote();
        FleetStore.releaseIdle(context, ownerId);
        List<HexParcelOwnershipEntity> rows = warehouses(context, ownerId);
        if (ownerId != null) {
                EconomyRepository economy = economyOf(context);
            if (economy != null) {
                WarehouseHoneyStore.reconcile(context, economy, ownerId, rows);
            }
        }
        FleetStore.Vehicle chosen = null;
        if (truckId != null && !truckId.isEmpty()) {
            chosen = FleetStore.vehicle(context, ownerId, truckId);
            if (chosen == null || !chosen.isTruck() || chosen.honeyBusy() || chosen.hiveTrips > 0
                    || !fits(chosen, kg)) {
                quote.block = "Ese camión no está libre.";
                quote.itinerary = quote.block;
                return quote;
            }
        }
        String sourceHex = chosen != null ? chosen.homeId : flora == null
                ? nearestHex(rows, dest.lat, dest.lng)
                : WarehouseHoneyStore.hexWith(context, ownerId, flora, kg, dest.lat, dest.lng, rows);
        if (sourceHex == null) {
            sourceHex = nearestHex(rows, dest.lat, dest.lng);
        }
        if (chosen != null && flora != null
                && WarehouseHoneyStore.kg(context, ownerId, chosen.homeId, flora) + 1e-6 < kg) {
            quote.block = "En el obrador de ese camión no hay tanta miel.";
            quote.itinerary = quote.block;
            return quote;
        }
        HexParcelOwnershipEntity source = row(rows, sourceHex);
        if (source == null || parcel(context, sourceHex) == null) {
            quote.missing = true;
            return quote;
        }
        quote.source = source;
        quote.sourceLabel = label(source);
        quote.label = quote.sourceLabel;
        quote.originDock = HoneyLogistics.warehouseDock(context, ownerId, parcel(context, sourceHex));
        boolean cross = PlayableMapRegion.fromHexId(sourceHex) != dest.region;
        if (!cross) {
            RoadPath path = road(context, quote.originDock, new double[]{dest.lat, dest.lng});
            quote.road1 = path;
            quote.geodesic = path.points.size() <= 2;
            quote.km = path.distanceKm;
            FleetStore.Vehicle truck = chosen != null ? chosen : freeTruck(context, ownerId, sourceHex, kg);
            boolean live = liveTrips(context);
            double kmh = truck != null ? FleetRules.speedKmh(FleetRules.Kind.TRUCK, truck.level) : 70;
            quote.plan = FleetPlanner.road(quote.sourceLabel, dest.name, path.distanceKm, path.distanceKm,
                    kmh, kg, false, truck != null || !live, truck == null || fits(truck, kg));
            quote.costB = quote.plan.costB;
            quote.durationMs = quote.plan.road1Ms;
            quote.itinerary = quote.plan.itinerary();
            quote.block = live ? quote.plan.block : null;
            quote.originTruck = truck;
            return quote;
        }
        Quote over = fillOverseas(context, ownerId, source, quote.originDock, dest.lat, dest.lng,
                dest.name, dest.hexId, kg, false, chosen);
        over.label = quote.sourceLabel;
        return over;
    }

    @NonNull
    private static Quote fillOverseas(@NonNull Context context, @Nullable String ownerId,
            @NonNull HexParcelOwnershipEntity source, @NonNull double[] originDock,
            double destLat, double destLng, @NonNull String destLabel, @Nullable String destHex,
            double kg, boolean transfer) {
        return fillOverseas(context, ownerId, source, originDock, destLat, destLng, destLabel, destHex,
                kg, transfer, null);
    }

    @NonNull
    private static Quote fillOverseas(@NonNull Context context, @Nullable String ownerId,
            @NonNull HexParcelOwnershipEntity source, @NonNull double[] originDock,
            double destLat, double destLng, @NonNull String destLabel, @Nullable String destHex,
            double kg, boolean transfer, @Nullable FleetStore.Vehicle originPref) {
        Quote quote = new Quote();
        quote.cross = true;
        quote.source = source;
        quote.sourceLabel = label(source);
        quote.label = quote.sourceLabel;
        quote.originDock = originDock;
        PlayableMapRegion fromRegion = PlayableMapRegion.fromHexId(source.hexId);
        PlayableMapRegion toRegion = destHex != null && !destHex.isEmpty()
                ? PlayableMapRegion.fromHexId(destHex)
                : PlayableMapRegion.containing(destLat, destLng);
        if (toRegion == null) {
            toRegion = PlayableMapRegion.IBERIA;
        }
        int level = ((ApicultureApp) context.getApplicationContext())
                .getPlayerProgressRepository().getLevel(ownerId);
        if (!TradeAccessRules.portLevelReached(level)) {
            quote.block = "El uso del puerto se desbloquea al nivel " + TradeAccessRules.PORT_LEVEL + ".";
            quote.itinerary = quote.block;
            return quote;
        }
        List<HexParcelOwnershipEntity> rows = warehouses(context, ownerId);
        Seaport bestFrom = null;
        Seaport bestTo = null;
        HexParcelOwnershipEntity bestHome = null;
        double bestScore = Double.MAX_VALUE;
        for (Seaport fromPort : SeaportCatalog.in(fromRegion)) {
            if (!TradeAccessStore.portPaid(context, ownerId, fromPort.id)) {
                continue;
            }
            if (freeShip(context, ownerId, fromPort.id, kg) == null && liveTrips(context)) {
                continue;
            }
            for (Seaport toPort : SeaportCatalog.in(toRegion)) {
                if (!TradeAccessStore.portPaid(context, ownerId, toPort.id)) {
                    continue;
                }
                HexParcelOwnershipEntity home = truckHomeNear(context, ownerId, rows, toRegion, toPort, kg);
                if (home == null && liveTrips(context)) {
                    continue;
                }
                double r1 = TranshumanceRules.haversineKm(originDock[0], originDock[1], fromPort.lat, fromPort.lng);
                double sea = SeaRoute.km(fromPort, toPort);
                double loaded = TranshumanceRules.haversineKm(toPort.lat, toPort.lng, destLat, destLng);
                double empty = home == null ? loaded
                        : TranshumanceRules.haversineKm(home.warehouseLat, home.warehouseLng, toPort.lat, toPort.lng);
                double score = r1 + sea + loaded + empty;
                if (score < bestScore) {
                    bestScore = score;
                    bestFrom = fromPort;
                    bestTo = toPort;
                    bestHome = home;
                }
            }
        }
        boolean live = liveTrips(context);
        if (bestFrom == null || bestTo == null || (live && bestHome == null)) {
            boolean paidFrom = paidPortIn(context, ownerId, fromRegion);
            boolean paidTo = fromRegion == toRegion ? paidFrom : paidPortIn(context, ownerId, toRegion);
            quote.block = paidFrom && paidTo
                    ? "Hace falta un barco libre en un puerto y un camión en los dos territorios."
                    : "Paga la cuota del puerto de salida y del de llegada.";
            quote.itinerary = quote.block;
            return quote;
        }
        quote.originPort = bestFrom;
        quote.destPort = bestTo;
        quote.destHome = bestHome != null ? bestHome : source;
        quote.destHomeLabel = label(quote.destHome);
        quote.destHomeDock = bestHome != null
                ? HoneyLogistics.warehouseDock(context, ownerId, parcel(context, bestHome.hexId))
                : originDock;
        quote.road1 = road(context, originDock, new double[]{bestFrom.lat, bestFrom.lng});
        quote.emptyToPort = road(context, quote.destHomeDock, new double[]{bestTo.lat, bestTo.lng});
        RoadPath loaded = road(context, new double[]{bestTo.lat, bestTo.lng}, new double[]{destLat, destLng});
        quote.seaPath = SeaRoute.trace(bestFrom, bestTo);
        double seaKm = SeaRoute.pathKm(quote.seaPath);
        double seaVisibleKm = SeaRoute.visibleKm(quote.seaPath, bestFrom.region, bestTo.region);
        quote.originTruck = originPref != null
                ? originPref
                : freeTruck(context, ownerId, source.hexId, kg);
        quote.ship = freeShip(context, ownerId, bestFrom.id, kg);
        quote.destTruck = bestHome != null ? freeTruck(context, ownerId, bestHome.hexId, kg) : null;
        double truck1 = speed(quote.originTruck);
        double ship = quote.ship != null
                ? FleetRules.speedKmh(FleetRules.Kind.SHIP, quote.ship.level) : 120;
        double truck2 = speed(quote.destTruck);
        double backHome = TranshumanceRules.haversineKm(destLat, destLng,
                quote.destHomeDock[0], quote.destHomeDock[1]);
        double emptyReturn = quote.road1.distanceKm + seaKm + quote.emptyToPort.distanceKm + backHome;
        quote.plan = FleetPlanner.overseas(quote.sourceLabel, bestFrom.name, bestTo.name, destLabel,
                quote.road1.distanceKm, seaKm, seaVisibleKm, loaded.distanceKm, quote.emptyToPort.distanceKm, emptyReturn,
                truck1, ship, truck2, kg, transfer,
                quote.originTruck != null || !live, fits(quote.originTruck, kg) || !live,
                quote.ship != null || !live, fitsShip(quote.ship, kg) || !live,
                quote.destTruck != null || !live, fits(quote.destTruck, kg) || !live);
        quote.costB = quote.plan.costB;
        quote.km = quote.road1.distanceKm + seaKm + loaded.distanceKm;
        quote.durationMs = quote.plan.shipDepartMs + quote.plan.seaMs + FleetRules.durationMs(loaded.distanceKm, truck2);
        quote.itinerary = quote.plan.itinerary();
        quote.block = live ? quote.plan.block : null;
        quote.geodesic = quote.road1.points.size() <= 2;
        return quote;
    }

    private static void holdOverseas(@NonNull Context context, @Nullable String ownerId, @NonNull Quote quote,
            @NonNull String flora, double kg, double price, boolean exotic, @NonNull String kind,
            @NonNull String destLabel, @Nullable String destHex, double destLat, double destLng) {
        List<FleetStore.Vehicle> held = new ArrayList<>();
        hold(context, ownerId, quote, held, kg);
        long now = System.currentTimeMillis();
        String shipment = UUID.randomUUID().toString();
        insertRoad(context, ownerId, shipment, quote.originTruck, flora, kg, price, exotic,
                kind, CargoTripEntity.LEG_HAUL_TRUCK,
                quote.sourceLabel, quote.source.hexId, quote.originDock,
                quote.originPort.name, quote.originPort.id, quote.originPort.lat, quote.originPort.lng,
                quote.originDock, quote.sourceLabel, quote.source.hexId,
                quote.road1, now + quote.plan.originDepartMs, null, null, 0, 0);
        insertSea(context, ownerId, shipment, quote.ship, flora, kg,
                quote.originPort, quote.destPort, quote.seaPath, now + quote.plan.shipDepartMs, quote.plan.seaMs);
        insertRoad(context, ownerId, shipment, quote.destTruck, flora, kg, price, exotic,
                kind, CargoTripEntity.LEG_PICKUP,
                quote.destHomeLabel, quote.destHome.hexId, quote.destHomeDock,
                quote.destPort.name, quote.destPort.id, quote.destPort.lat, quote.destPort.lng,
                quote.destHomeDock, quote.destHomeLabel, quote.destHome.hexId,
                quote.emptyToPort, now + quote.plan.destDepartMs,
                destLabel, destHex, destLat, destLng);
    }

    private static void hold(@NonNull Context context, @Nullable String ownerId, @NonNull Quote quote,
            @NonNull List<FleetStore.Vehicle> held, double kg) {
        if (quote.originTruck != null) {
            FleetStore.Vehicle v = FleetStore.holdVehicle(context, ownerId, quote.originTruck.id);
            if (v == null) {
                throw new IllegalStateException("truck");
            }
            quote.originTruck = v;
            held.add(v);
        } else if (liveTrips(context)) {
            throw new IllegalStateException("truck");
        }
        if (quote.ship != null) {
            FleetStore.Vehicle v = FleetStore.reserveShip(context, ownerId, quote.ship.homeId, kg);
            if (v == null) {
                throw new IllegalStateException("ship");
            }
            quote.ship = v;
            held.add(v);
        }
        if (quote.destTruck != null) {
            FleetStore.Vehicle v = FleetStore.holdVehicle(context, ownerId, quote.destTruck.id);
            if (v == null) {
                throw new IllegalStateException("truck");
            }
            quote.destTruck = v;
            held.add(v);
        }
    }

    private static void insertRoad(@NonNull Context context, @Nullable String ownerId, @NonNull String shipment,
            @Nullable FleetStore.Vehicle vehicle, @NonNull String flora, double kg, double price, boolean lock,
            @NonNull String kind, @NonNull String role,
            @NonNull String fromLabel, @Nullable String fromHex, @NonNull double[] from,
            @NonNull String toLabel, @Nullable String toHex, double toLat, double toLng,
            @NonNull double[] returnAt, @NonNull String returnLabel, @Nullable String returnHex,
            @Nullable RoadPath path, long startMs,
            @Nullable String chainLabel, @Nullable String chainHex, double chainLat, double chainLng,
            double nextKg) {
        CargoTripEntity trip = new CargoTripEntity();
        trip.id = UUID.randomUUID().toString();
        trip.ownerId = ownerId;
        trip.kind = kind;
        trip.phase = CargoTripEntity.PHASE_OUT;
        trip.legRole = role;
        trip.shipmentId = shipment;
        trip.vehicleId = vehicle != null ? vehicle.id : null;
        trip.floraKey = flora;
        trip.kg = kg;
        trip.unitPrice = price;
        trip.priceLocked = lock ? 1 : 0;
        trip.cargoJson = cargoJson(flora, kg, nextKg, chainHex);
        trip.originLabel = fromLabel;
        trip.originHexId = fromHex;
        trip.destLabel = toLabel;
        trip.destHexId = toHex;
        trip.returnLat = returnAt[0];
        trip.returnLng = returnAt[1];
        trip.returnLabel = returnLabel;
        trip.returnHexId = returnHex;
        trip.chainLabel = chainLabel;
        trip.chainHexId = chainHex;
        trip.chainLat = chainLat;
        trip.chainLng = chainLng;
        boolean chained = Math.abs(chainLat) > 1e-6 || Math.abs(chainLng) > 1e-6;
        double backFromLat = chained ? chainLat : toLat;
        double backFromLng = chained ? chainLng : toLng;
        if (TranshumanceRules.haversineKm(backFromLat, backFromLng, returnAt[0], returnAt[1]) > 0.08) {
            RoadPath back = road(context, new double[] {backFromLat, backFromLng}, returnAt);
            HoneyLogistics.rememberReturnRoad(trip, back, backFromLat, backFromLng,
                    returnAt[0], returnAt[1], FleetRules.durationMs(back.distanceKm, speed(vehicle)));
        }
        CargoTripRules.applyPath(trip, path, from[0], from[1], toLat, toLng);
        trip.startEpochMs = startMs;
        if (vehicle != null && path != null) {
            trip.durationMs = FleetRules.durationMs(path.distanceKm, speed(vehicle));
            FleetStore.bindCargo(context, ownerId, vehicle.id, trip.id);
        }
        AppDatabase.getInstance(context).cargoTripDao().upsert(trip);
    }

    private static void insertRoad(@NonNull Context context, @Nullable String ownerId, @NonNull String shipment,
            @Nullable FleetStore.Vehicle vehicle, @NonNull String flora, double kg, double price, boolean lock,
            @NonNull String kind, @NonNull String role,
            @NonNull String fromLabel, @Nullable String fromHex, @NonNull double[] from,
            @NonNull String toLabel, @Nullable String toHex, double toLat, double toLng,
            @NonNull double[] returnAt, @NonNull String returnLabel, @Nullable String returnHex,
            @Nullable RoadPath path, long startMs,
            @Nullable String chainLabel, @Nullable String chainHex, double chainLat, double chainLng) {
        insertRoad(context, ownerId, shipment, vehicle, flora, kg, price, lock, kind, role,
                fromLabel, fromHex, from, toLabel, toHex, toLat, toLng, returnAt, returnLabel, returnHex,
                path, startMs, chainLabel, chainHex, chainLat, chainLng, 0);
    }

    private static void insertSea(@NonNull Context context, @Nullable String ownerId, @NonNull String shipment,
            @Nullable FleetStore.Vehicle ship, @NonNull String flora, double kg,
            @NonNull Seaport from, @NonNull Seaport to, @NonNull List<double[]> path,
            long startMs, long durationMs) {
        CargoTripEntity trip = new CargoTripEntity();
        trip.id = UUID.randomUUID().toString();
        trip.ownerId = ownerId;
        trip.kind = CargoTripEntity.KIND_WHOLESALE;
        trip.phase = CargoTripEntity.PHASE_OUT;
        trip.legRole = CargoTripEntity.LEG_HAUL_SHIP;
        trip.shipmentId = shipment;
        trip.vehicleId = ship != null ? ship.id : null;
        trip.floraKey = flora;
        trip.kg = kg;
        trip.cargoJson = cargoJson(flora, kg, 0, null);
        trip.originLabel = from.name;
        trip.originHexId = from.id;
        trip.destLabel = to.name;
        trip.destHexId = to.id;
        trip.originLat = from.lat;
        trip.originLng = from.lng;
        trip.destLat = to.lat;
        trip.destLng = to.lng;
        trip.returnLat = from.lat;
        trip.returnLng = from.lng;
        trip.returnLabel = from.name;
        trip.returnHexId = from.id;
        trip.routePolyline = EncodedPolyline.encode(path);
        trip.routeRoadKinds = "gap";
        trip.startEpochMs = startMs;
        trip.durationMs = durationMs;
        if (ship != null) {
            FleetStore.bindCargo(context, ownerId, ship.id, trip.id);
        }
        AppDatabase.getInstance(context).cargoTripDao().upsert(trip);
    }

    private static String cargoJson(String flora, double kg, double nextKg, @Nullable String nextHex) {
        JSONObject o = new JSONObject();
        try {
            o.put(flora, kg);
            if (nextKg > 1e-9 && nextHex != null) {
                o.put("_nextKg", nextKg);
                o.put("_nextHex", nextHex);
            }
        } catch (JSONException ignored) {
        }
        return o.toString();
    }

    private static boolean take(@NonNull Context context, @NonNull EconomyRepository economy,
            @Nullable String ownerId, @Nullable String hex, @NonNull String flora, double kg) {
        boolean fromWarehouse = WarehouseHoneyStore.take(context, ownerId, hex, flora, kg);
        if (!economy.takeHoney(flora, kg)) {
            if (fromWarehouse) {
                WarehouseHoneyStore.add(context, ownerId, hex, flora, kg);
            }
            return false;
        }
        return true;
    }

    private static void giveBack(@NonNull Context context, @NonNull EconomyRepository economy,
            @Nullable String ownerId, @Nullable String hex, @NonNull String flora, double kg) {
        economy.addHoney(flora, kg);
        WarehouseHoneyStore.add(context, ownerId, hex, flora, kg);
    }

    private static void release(@NonNull Context context, @Nullable String ownerId,
            @NonNull List<FleetStore.Vehicle> held) {
        for (FleetStore.Vehicle v : held) {
            FleetStore.releaseVehicle(context, ownerId, v.id);
        }
    }

    @Nullable
    private static FleetStore.Vehicle freeTruck(@NonNull Context context, @Nullable String ownerId,
            @Nullable String hex, double kg) {
        FleetStore.Vehicle best = null;
        for (FleetStore.Vehicle v : FleetStore.vehicles(context, ownerId)) {
            if (!v.isTruck() || hex == null || !hex.equals(v.homeId) || v.honeyBusy() || v.hiveTrips > 0) {
                continue;
            }
            if (!fits(v, kg)) {
                continue;
            }
            if (best == null || v.level > best.level) {
                best = v;
            }
        }
        return best;
    }

    private static boolean paidPortIn(@NonNull Context context, @Nullable String ownerId,
            @NonNull PlayableMapRegion region) {
        for (Seaport port : SeaportCatalog.in(region)) {
            if (TradeAccessStore.portPaid(context, ownerId, port.id)) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    private static FleetStore.Vehicle freeShip(@NonNull Context context, @Nullable String ownerId,
            @Nullable String portId, double kg) {
        for (FleetStore.Vehicle v : FleetStore.vehicles(context, ownerId)) {
            if (v.isTruck() || portId == null || !portId.equals(v.homeId) || v.honeyBusy()) {
                continue;
            }
            if (fitsShip(v, kg)) {
                return v;
            }
        }
        return null;
    }

    @Nullable
    private static HexParcelOwnershipEntity truckHomeNear(@NonNull Context context, @Nullable String ownerId,
            @NonNull List<HexParcelOwnershipEntity> rows, @NonNull PlayableMapRegion region,
            @NonNull Seaport port, double kg) {
        HexParcelOwnershipEntity best = null;
        double bestKm = Double.MAX_VALUE;
        for (HexParcelOwnershipEntity row : rows) {
            if (row == null || PlayableMapRegion.fromHexId(row.hexId) != region) {
                continue;
            }
            if (freeTruck(context, ownerId, row.hexId, kg) == null) {
                continue;
            }
            double km = TranshumanceRules.haversineKm(row.warehouseLat, row.warehouseLng, port.lat, port.lng);
            if (km < bestKm) {
                bestKm = km;
                best = row;
            }
        }
        return best;
    }

    private static boolean fits(@Nullable FleetStore.Vehicle truck, double kg) {
        if (truck == null) {
            return kg <= FleetRules.honeyKg(FleetRules.Kind.TRUCK, 1);
        }
        return FleetRules.honeyKg(FleetRules.Kind.TRUCK, truck.level) + 1e-6 >= kg;
    }

    private static boolean fitsShip(@Nullable FleetStore.Vehicle ship, double kg) {
        if (ship == null) {
            return kg <= FleetRules.honeyKg(FleetRules.Kind.SHIP, 1);
        }
        return FleetRules.honeyKg(FleetRules.Kind.SHIP, ship.level) + 1e-6 >= kg;
    }

    private static double speed(@Nullable FleetStore.Vehicle vehicle) {
        if (vehicle == null) {
            return 70;
        }
        return FleetRules.speedKmh(vehicle.rulesKind(), vehicle.level);
    }

    @NonNull
    private static RoadPath road(@NonNull Context context, @NonNull double[] from, @NonNull double[] to) {
        RoadPath path = LocalGraphHopper.route(context, from[0], from[1], to[0], to[1]);
        if (path == null || path.points.size() < 2 || path.distanceKm <= 0) {
            return RoadPath.geodesic(from[0], from[1], to[0], to[1]);
        }
        return path;
    }

    @Nullable
    private static HexParcel parcel(@NonNull Context context, @Nullable String hexId) {
        if (hexId == null) {
            return null;
        }
        return HoneyLogistics.parcelFor(context, null, hexId);
    }

    @NonNull
    private static List<HexParcelOwnershipEntity> warehouses(@NonNull Context context, @Nullable String ownerId) {
        List<HexParcelOwnershipEntity> rows = AppDatabase.getInstance(context)
                .hexParcelOwnershipDao().getWarehousesForOwnerSync(ownerId);
        return rows != null ? rows : new ArrayList<>();
    }

    @Nullable
    private static HexParcelOwnershipEntity row(@NonNull List<HexParcelOwnershipEntity> rows, @Nullable String hex) {
        if (hex == null) {
            return null;
        }
        for (HexParcelOwnershipEntity row : rows) {
            if (row != null && hex.equals(row.hexId) && row.hasWarehouse) {
                return row;
            }
        }
        return null;
    }

    @Nullable
    private static String nearestHex(@NonNull List<HexParcelOwnershipEntity> rows, double lat, double lng) {
        String best = null;
        double bestKm = Double.MAX_VALUE;
        for (HexParcelOwnershipEntity row : rows) {
            if (row == null || !row.hasWarehouse) {
                continue;
            }
            double km = TranshumanceRules.haversineKm(row.warehouseLat, row.warehouseLng, lat, lng);
            if (km < bestKm) {
                bestKm = km;
                best = row.hexId;
            }
        }
        return best;
    }

    @NonNull
    private static String label(@NonNull HexParcelOwnershipEntity row) {
        if (row.parcelName != null && !row.parcelName.trim().isEmpty()) {
            return row.parcelName.trim();
        }
        return "Obrador";
    }

    @Nullable
    private static EconomyRepository economyOf(@NonNull Context context) {
        Context app = context.getApplicationContext();
        if (app instanceof com.apiculture.simulator.ApicultureApp) {
            return ((com.apiculture.simulator.ApicultureApp) app).getEconomyRepository();
        }
        return null;
    }

    private static final class Quote {
        boolean missing;
        boolean cross;
        boolean geodesic;
        @Nullable String block;
        String itinerary = "";
        String label = "Obrador";
        String sourceLabel = "Obrador";
        String destHomeLabel = "Obrador";
        double costB;
        double km;
        long durationMs;
        double honeyKg;
        double extraKg;
        double destLat;
        double destLng;
        @Nullable HexParcelOwnershipEntity extraStop;
        @Nullable HexParcelOwnershipEntity source;
        @Nullable HexParcelOwnershipEntity destHome;
        double[] originDock = {0, 0};
        double[] destHomeDock = {0, 0};
        @Nullable Seaport originPort;
        @Nullable Seaport destPort;
        @Nullable RoadPath road1;
        @Nullable RoadPath emptyToPort;
        List<double[]> seaPath = new ArrayList<>();
        @Nullable FleetStore.Vehicle originTruck;
        @Nullable FleetStore.Vehicle destTruck;
        @Nullable FleetStore.Vehicle ship;
        @Nullable FleetPlanner.Plan plan;
    }
}
