package com.apiculture.simulator.unity;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.apiculture.simulator.ApicultureApp;
import com.apiculture.simulator.R;
import com.apiculture.simulator.data.local.AppDatabase;
import com.apiculture.simulator.data.local.entity.HexParcelOwnershipEntity;
import com.apiculture.simulator.data.local.entity.HoneyOrderEntity;
import com.apiculture.simulator.data.repository.EconomyRepository;
import com.apiculture.simulator.data.repository.FleetDispatch;
import com.apiculture.simulator.data.repository.HoneyLogistics;
import com.apiculture.simulator.data.repository.HoneyOrderStore;
import com.apiculture.simulator.data.repository.MarketRepository;
import com.apiculture.simulator.data.repository.WarehouseHoneyStore;
import com.apiculture.simulator.data.repository.WorkshopStore;
import com.apiculture.simulator.domain.game.ClimateUnlock;
import com.apiculture.simulator.domain.game.HoneyOrder;
import com.apiculture.simulator.domain.game.HoneyOrderCatalog;
import com.apiculture.simulator.domain.map.PlayableMapRegion;
import com.apiculture.simulator.domain.map.ProvincialMarket;
import com.apiculture.simulator.domain.map.ProvincialMarketCatalog;
import com.apiculture.simulator.domain.market.HoneyMarketEngine;
import com.apiculture.simulator.domain.parcel.HexParcel;
import com.apiculture.simulator.domain.workshop.WorkshopRules.Format;
import com.apiculture.simulator.domain.workshop.WorkshopState;
import com.apiculture.simulator.presentation.hive.HiveSiteSummaryUi;
import com.apiculture.simulator.presentation.market.MarketViewModel;
import com.apiculture.simulator.presentation.market.WorkshopFormatUi;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Mercado y comandas para el almacenero del 3D (clase C# Apiario.MarketDialog). Unity pregunta con
 * {@link #query(String)} y la respuesta llega a "MarketReply" con el mismo {@code req}. Las acciones
 * (vender al por mayor, enviar una comanda, traspasar entre obradores) usan la lógica de la app.
 */
final class UnityMarket {

    private static final String TAG = "UnityMarket";
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private UnityMarket() {
    }

    static void query(@Nullable String raw) {
        IO.execute(() -> {
            JSONObject q;
            try {
                q = new JSONObject(raw == null ? "{}" : raw);
            } catch (JSONException e) {
                return;
            }
            String req = q.optString("req", "");
            String cmd = q.optString("cmd", "");
            try {
                switch (cmd) {
                    case "overview": reply(req, cmd, overview(q)); break;
                    case "market": reply(req, cmd, market(q)); break;
                    case "salePreview": salePreview(req, cmd, q); break;
                    case "sell": sell(req, cmd, q); break;
                    case "sendOrder": sendOrder(req, cmd, q); break;
                    case "transferPreview": reply(req, cmd, transferPreview(q)); break;
                    case "transfer": reply(req, cmd, transfer(q)); break;
                    default: break;
                }
            } catch (Exception e) {
                Log.e(TAG, cmd, e);
                reply(req, cmd, error(e.getMessage() != null ? e.getMessage() : "error"));
            }
        });
    }

    private static void reply(@NonNull String req, @NonNull String cmd, @NonNull JSONObject data) {
        try {
            data.put("req", req);
            data.put("cmd", cmd);
        } catch (JSONException ignored) {
        }
        UnityBridge.send("MarketReply", data.toString());
    }

    @NonNull
    private static JSONObject error(@NonNull String msg) {
        JSONObject o = new JSONObject();
        try {
            o.put("ok", false);
            o.put("message", msg);
        } catch (JSONException ignored) {
        }
        return o;
    }

    @Nullable
    private static Context app() {
        return UnityBridge.app();
    }

    private static String owner() {
        return UnityBridge.owner();
    }

    @NonNull
    private static String flora(@NonNull String key) {
        return HiveSiteSummaryUi.floraLabel(UnityBridge.localized(), key);
    }

    private static int level(@NonNull Context ctx) {
        return ((ApicultureApp) ctx).getPlayerProgressRepository().getLevel(owner());
    }

    // ---------------------------------------------------------------- resumen

    /** Región, obradores, existencias del obrador de origen, mercados cercanos y comandas abiertas. */
    @NonNull
    private static JSONObject overview(@NonNull JSONObject q) throws JSONException {
        Context ctx = app();
        JSONObject o = new JSONObject();
        if (ctx == null) {
            return error("no app");
        }
        ApicultureApp a = (ApicultureApp) ctx;
        Context text = UnityBridge.localized();
        int level = level(ctx);
        PlayableMapRegion region = PlayableMapRegion.fromPrefsValue(q.optString("region", ""));
        if (region == PlayableMapRegion.SOUTH_AFRICA && !ClimateUnlock.canAccessSouthAfrica(level)) {
            region = PlayableMapRegion.IBERIA;
        }
        o.put("ok", true);
        o.put("balance", a.getEconomyRepository().getBalance());
        o.put("region", region.prefsValue());
        JSONArray regions = new JSONArray();
        for (PlayableMapRegion r : new PlayableMapRegion[] {
                PlayableMapRegion.IBERIA, PlayableMapRegion.MADAGASCAR, PlayableMapRegion.SOUTH_AFRICA }) {
            JSONObject j = new JSONObject();
            j.put("key", r.prefsValue());
            j.put("label", text.getString(r == PlayableMapRegion.IBERIA ? R.string.map_region_iberia
                    : r == PlayableMapRegion.MADAGASCAR ? R.string.map_region_madagascar
                    : R.string.map_region_south_africa));
            j.put("unlocked", r != PlayableMapRegion.SOUTH_AFRICA || ClimateUnlock.canAccessSouthAfrica(level));
            regions.put(j);
        }
        o.put("regions", regions);

        // Obradores de la región; el de origen es el pedido o el primero.
        List<HexParcelOwnershipEntity> rows =
                AppDatabase.getInstance(ctx).hexParcelOwnershipDao().getWarehousesForOwnerSync(owner());
        JSONArray obradores = new JSONArray();
        JSONArray everywhere = new JSONArray();
        String origin = q.optString("origin", "");
        String first = null;
        boolean originOk = false;
        if (rows != null) {
            rows.sort(Comparator.comparing(r -> r.parcelName != null ? r.parcelName : ""));
            for (HexParcelOwnershipEntity row : rows) {
                if (row == null || !row.hasWarehouse || row.hexId == null) {
                    continue;
                }
                JSONObject j = new JSONObject();
                j.put("hex", row.hexId);
                j.put("name", row.parcelName != null && !row.parcelName.trim().isEmpty()
                        ? row.parcelName.trim() : text.getString(R.string.map_warehouse_title));
                j.put("region", PlayableMapRegion.fromHexId(row.hexId).prefsValue());
                everywhere.put(j);
                if (PlayableMapRegion.fromHexId(row.hexId) != region) {
                    continue;
                }
                obradores.put(j);
                if (first == null) {
                    first = row.hexId;
                }
                originOk |= row.hexId.equals(origin);
            }
        }
        if (!originOk) {
            origin = first != null ? first : "";
        }
        o.put("obradores", obradores);
        o.put("allObradores", everywhere);
        o.put("origin", origin);

        // A granel en el almacén del obrador de origen, y tarros en sus estanterías.
        JSONArray stock = new JSONArray();
        if (!origin.isEmpty()) {
            for (Map.Entry<String, Double> e : WarehouseHoneyStore.at(ctx, owner(), origin).entrySet()) {
                if (e.getKey() == null || e.getValue() == null || e.getValue() <= 1e-6) {
                    continue;
                }
                JSONObject j = new JSONObject();
                j.put("flora", e.getKey());
                j.put("label", flora(e.getKey()));
                j.put("kg", e.getValue());
                stock.put(j);
            }
        }
        o.put("stock", stock);
        JSONArray jars = new JSONArray();
        if (!origin.isEmpty()) {
            WorkshopState s = WorkshopStore.get(ctx, owner(), origin);
            for (WorkshopState.Packed p : s.packed) {
                if (p.format == Format.BULK || p.jars <= 0) {
                    continue;
                }
                JSONObject j = new JSONObject();
                j.put("flora", p.flora);
                j.put("label", flora(p.flora));
                j.put("format", p.format.name());
                j.put("formatLabel", WorkshopFormatUi.label(text, p.format));
                j.put("jars", p.jars);
                jars.put(j);
            }
        }
        o.put("jars", jars);

        HexParcel originParcel = origin.isEmpty() ? null : HoneyLogistics.parcelFor(ctx, owner(), origin);
        double lat = originParcel != null ? originParcel.centroidLat : region.defaultLookLat();
        double lng = originParcel != null ? originParcel.centroidLon : region.defaultLookLon();

        // Mercados provinciales y locales, del más cercano al más lejano.
        JSONArray markets = new JSONArray();
        List<ProvincialMarket> list = new ArrayList<>(ProvincialMarketCatalog.resolve(ctx, region));
        list.removeIf(m -> m == null || m.international);
        list.sort(Comparator.comparingDouble(m -> km(lat, lng, m.lat, m.lng)));
        for (ProvincialMarket m : list) {
            JSONObject j = new JSONObject();
            j.put("id", m.salesId());
            j.put("name", m.name);
            j.put("kind", text.getString(m.local ? R.string.market_picker_local : R.string.market_picker_provincial));
            j.put("km", km(lat, lng, m.lat, m.lng));
            markets.put(j);
        }
        o.put("markets", markets);

        // Comandas: las de la región, de la más cercana a la más lejana, filtradas por flora si se pide.
        HoneyOrderStore.maintain(ctx, a.getMarketRepository());
        List<HoneyOrderEntity> open = AppDatabase.getInstance(ctx).honeyOrderDao().getOpenSync();
        List<HoneyOrder> orders = HoneyOrderCatalog.openNearest(open, region.prefsValue(), lat, lng,
                System.currentTimeMillis(), Integer.MAX_VALUE, level);
        String want = q.optString("flora", "");
        JSONArray orderFloras = new JSONArray();
        List<String> seen = new ArrayList<>();
        JSONArray out = new JSONArray();
        EconomyRepository economy = a.getEconomyRepository();
        for (HoneyOrder order : orders) {
            String key = HoneyMarketEngine.canonicalFloraKey(order.floraKey);
            if (!seen.contains(key)) {
                seen.add(key);
                JSONObject f = new JSONObject();
                f.put("flora", key);
                f.put("label", flora(key));
                orderFloras.put(f);
            }
            if (!want.isEmpty() && !want.equals(key)) {
                continue;
            }
            out.put(orderJson(ctx, text, order, originParcel, economy));
        }
        o.put("orderFloras", orderFloras);
        o.put("orders", out);
        return o;
    }

    @NonNull
    private static JSONObject orderJson(@NonNull Context ctx, @NonNull Context text, @NonNull HoneyOrder order,
            @Nullable HexParcel origin, @NonNull EconomyRepository economy) throws JSONException {
        JSONObject j = new JSONObject();
        String label = flora(order.floraKey);
        j.put("id", order.id);
        j.put("npc", order.npcName);
        j.put("portrait", order.portraitIndex);
        j.put("place", order.destLabel);
        j.put("flora", HoneyMarketEngine.canonicalFloraKey(order.floraKey));
        j.put("ask", order.wantsJars() && order.mix != null
                ? text.getString(R.string.market_order_ask_mix, WorkshopFormatUi.mixLabel(text, order.mix), label)
                : text.getString(R.string.market_order_ask_drum, order.kg, label));
        j.put("kg", order.kg);
        j.put("pay", order.payout());
        j.put("unit", order.unitPrice);
        j.put("km", HoneyLogistics.travelKm(origin, order.destLat, order.destLng));
        j.put("travel", HoneyLogistics.travelCostB(origin, order.destLat, order.destLng, order.kg));
        j.put("etaMs", HoneyLogistics.travelDurationMs(origin, order.destLat, order.destLng));
        j.put("expireMs", order.expireEpochMs);
        j.put("canSend", HoneyLogistics.hasStockForOrder(ctx, owner(), economy, order));
        return j;
    }

    private static double km(double lat1, double lng1, double lat2, double lng2) {
        double r = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1), dLng = Math.toRadians(lng2 - lng1);
        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * r * Math.asin(Math.min(1.0, Math.sqrt(h)));
    }

    // ---------------------------------------------------------------- mercado

    @Nullable
    private static ProvincialMarket findMarket(@NonNull Context ctx, @NonNull JSONObject q) {
        PlayableMapRegion region = PlayableMapRegion.fromPrefsValue(q.optString("region", ""));
        String id = q.optString("market", "");
        for (ProvincialMarket m : ProvincialMarketCatalog.resolve(ctx, region)) {
            if (m != null && id.equals(m.salesId())) {
                return m;
            }
        }
        return null;
    }

    /** Precio a granel de cada flora que el jugador tiene, en ese mercado. */
    @NonNull
    private static JSONObject market(@NonNull JSONObject q) throws JSONException {
        Context ctx = app();
        if (ctx == null) {
            return error("no app");
        }
        ProvincialMarket m = findMarket(ctx, q);
        if (m == null) {
            return error("market");
        }
        ApicultureApp a = (ApicultureApp) ctx;
        MarketRepository repo = a.getMarketRepository();
        Map<String, Double> stocks = a.getEconomyRepository().copyHoneyBuckets();
        JSONObject o = new JSONObject();
        o.put("ok", true);
        o.put("market", m.salesId());
        o.put("name", m.name);
        JSONArray pills = new JSONArray();
        for (Map.Entry<String, Double> e : stocks.entrySet()) {
            String key = HoneyMarketEngine.canonicalFloraKey(e.getKey());
            double stock = e.getValue() == null ? 0 : e.getValue();
            if (stock <= 1e-6) {
                continue;
            }
            JSONObject j = new JSONObject();
            j.put("flora", key);
            j.put("label", flora(key));
            j.put("price", repo.priceEurPerKgForFlora(key, m));
            j.put("stock", stock);
            j.put("left", repo.remainingCapacityKg(m, key));
            pills.put(j);
        }
        o.put("pills", pills);
        return o;
    }

    /** Camión más barato del obrador y coste del viaje para vender {@code kg} de una flora. */
    private static void salePreview(@NonNull String req, @NonNull String cmd, @NonNull JSONObject q) {
        Context ctx = app();
        ProvincialMarket m = ctx == null ? null : findMarket(ctx, q);
        String floraKey = q.optString("flora", "");
        double kg = q.optDouble("kg", 0);
        if (ctx == null || m == null || floraKey.isEmpty() || kg <= 0) {
            reply(req, cmd, error(ctx == null ? "no app" : UnityBridge.localized().getString(R.string.map_market_empty_stock)));
            return;
        }
        HoneyLogistics.saleTruckOptions(ctx, owner(), m, floraKey, kg, trucks -> {
            if (trucks == null || trucks.isEmpty()) {
                reply(req, cmd, error(UnityBridge.localized().getString(R.string.market_sell_fail_fleet)));
                return;
            }
            HoneyLogistics.OrderTruckOption truck = trucks.get(0);
            HoneyLogistics.previewWholesaleAsync(ctx, owner(), m, floraKey, kg, truck.truckId, p -> {
                JSONObject o = new JSONObject();
                try {
                    double price = ((ApicultureApp) ctx).getMarketRepository().priceEurPerKgForFlora(floraKey, m);
                    double gross = Math.round(kg * price * 100.0) / 100.0;
                    o.put("ok", p.blockReason == null && !p.missingWarehouse);
                    if (p.blockReason != null) {
                        o.put("message", p.blockReason);
                    }
                    o.put("truck", truck.truckId);
                    o.put("truckLabel", truck.truckLabel);
                    o.put("from", p.warehouseLabel);
                    o.put("km", p.distanceKm);
                    o.put("etaMs", p.durationMs);
                    o.put("price", price);
                    o.put("gross", gross);
                    o.put("travel", p.travelCostB);
                    o.put("net", Math.round((gross - p.travelCostB) * 100.0) / 100.0);
                } catch (JSONException ignored) {
                }
                reply(req, cmd, o);
            });
        });
    }

    private static void sell(@NonNull String req, @NonNull String cmd, @NonNull JSONObject q) {
        Context ctx = app();
        ProvincialMarket m = ctx == null ? null : findMarket(ctx, q);
        String floraKey = q.optString("flora", "");
        double kg = q.optDouble("kg", 0);
        String truck = q.optString("truck", "");
        if (ctx == null || m == null || floraKey.isEmpty() || kg <= 0) {
            reply(req, cmd, error("sell"));
            return;
        }
        ApicultureApp a = (ApicultureApp) ctx;
        MAIN.post(() -> {
            MarketViewModel vm = new MarketViewModel(a, a.getEconomyRepository(), a.getMarketRepository());
            vm.sellFloraKg(floraKey, kg, m, truck.isEmpty() ? null : truck, result -> {
                JSONObject o = new JSONObject();
                Context text = UnityBridge.localized();
                try {
                    o.put("ok", result.success);
                    o.put("message", !result.success
                            ? text.getString(R.string.market_sell_fail_reason, String.valueOf(result.errorMessage))
                            : result.truckDispatched
                            ? text.getString(R.string.market_sell_truck, kg)
                            : text.getString(R.string.market_sell_ok, kg, result.unitPriceEurPerKg));
                } catch (JSONException ignored) {
                }
                reply(req, cmd, o);
            });
        });
    }

    // ---------------------------------------------------------------- comandas

    private static void sendOrder(@NonNull String req, @NonNull String cmd, @NonNull JSONObject q) {
        Context ctx = app();
        if (ctx == null) {
            reply(req, cmd, error("no app"));
            return;
        }
        HoneyOrderEntity row = AppDatabase.getInstance(ctx).honeyOrderDao().getById(q.optString("order", ""));
        if (row == null) {
            reply(req, cmd, error(UnityBridge.localized().getString(R.string.market_order_fail_stock)));
            return;
        }
        HoneyOrder order = HoneyOrder.fromEntity(row);
        EconomyRepository economy = ((ApicultureApp) ctx).getEconomyRepository();
        HoneyLogistics.dispatchOrder(ctx, owner(), order, economy, null, r -> {
            Context text = UnityBridge.localized();
            String detail = HoneyLogistics.consumeOrderFailDetail();
            JSONObject o = new JSONObject();
            try {
                boolean ok = r == HoneyLogistics.Result.STARTED || r == HoneyLogistics.Result.INSTANT;
                o.put("ok", ok);
                o.put("result", r.name());
                o.put("message", ok ? "" : detail != null ? detail
                        : r == HoneyLogistics.Result.NO_FLEET ? text.getString(R.string.market_order_fail_truck)
                        : r == HoneyLogistics.Result.NO_CASH ? text.getString(R.string.workshop_sell_no_cash)
                        : text.getString(R.string.market_order_fail_stock));
            } catch (JSONException ignored) {
            }
            reply(req, cmd, o);
        });
    }

    // ---------------------------------------------------------------- traspasos

    @NonNull
    private static JSONObject transferPreview(@NonNull JSONObject q) throws JSONException {
        Context ctx = app();
        if (ctx == null) {
            return error("no app");
        }
        EconomyRepository economy = ((ApicultureApp) ctx).getEconomyRepository();
        FleetDispatch.TransferPreview p = FleetDispatch.previewTransfer(ctx, owner(), economy,
                q.optString("from", ""), q.optString("flora", ""), q.optDouble("kg", 0), q.optString("to", ""));
        JSONObject o = new JSONObject();
        o.put("ok", p.block == null);
        if (p.block != null) {
            o.put("message", p.block);
        }
        o.put("km", p.km);
        o.put("etaMs", p.durationMs);
        o.put("travel", p.costB);
        return o;
    }

    @NonNull
    private static JSONObject transfer(@NonNull JSONObject q) throws JSONException {
        Context ctx = app();
        if (ctx == null) {
            return error("no app");
        }
        EconomyRepository economy = ((ApicultureApp) ctx).getEconomyRepository();
        String error = FleetDispatch.transfer(ctx, owner(), economy, q.optString("from", ""),
                q.optString("flora", ""), q.optDouble("kg", 0), q.optString("to", ""), 0, null);
        JSONObject o = new JSONObject();
        o.put("ok", error == null);
        o.put("message", error == null ? "" : error);
        return o;
    }
}
