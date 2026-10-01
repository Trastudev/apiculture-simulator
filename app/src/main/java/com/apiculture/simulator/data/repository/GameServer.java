package com.apiculture.simulator.data.repository;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.apiculture.simulator.ApicultureApp;
import com.apiculture.simulator.BuildConfig;
import com.apiculture.simulator.data.local.AppDatabase;
import com.apiculture.simulator.data.local.entity.CargoTripEntity;
import com.apiculture.simulator.data.local.entity.HiveEntity;
import com.apiculture.simulator.data.local.entity.HoneyOrderEntity;
import com.apiculture.simulator.data.local.entity.PollinationOfferEntity;
import com.apiculture.simulator.data.local.entity.PollinationContractEntity;
import com.apiculture.simulator.data.local.entity.TruckTripEntity;
import com.apiculture.simulator.domain.game.CargoTripRules;
import com.apiculture.simulator.domain.game.TruckTripRules;
import com.apiculture.simulator.domain.game.XpAwards;
import com.apiculture.simulator.domain.map.PlayableMapRegion;
import com.apiculture.simulator.domain.map.ProvincialMarket;
import com.apiculture.simulator.domain.map.ProvincialMarketCatalog;
import com.apiculture.simulator.data.session.PlayerAuth;
import com.apiculture.simulator.data.session.SignedInUser;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
/**
 * El teléfono publica los viajes y aplica lo que el servidor ya ha resuelto.
 * Con la URL vacía, el juego sigue cerrando los tramos en el propio teléfono;
 * con servidor activo, el cierre y las fases son siempre autoritativos del servidor.
 */
public final class GameServer {

    private static final Object LOCK = new Object();
    private static final long SYNC_BUDGET_MS = 15_000L;
    private static final long OFFER_SYNC_BUDGET_MS = 8_000L;
    private static final ThreadLocal<Long> SYNC_DEADLINE_NANOS = new ThreadLocal<>();
    private static final ThreadLocal<String> SYNC_TOKEN = new ThreadLocal<>();
    private static final String PREFS = "game_server";
    private static final String APPLIED = "applied_effects";
    private static volatile long lastOfferSyncMs;
    private static volatile boolean serverAvailable = true;
    private static final CopyOnWriteArrayList<ConnectionListener> CONNECTION_LISTENERS =
            new CopyOnWriteArrayList<>();
    private static final ExecutorService CONNECTION_IO = Executors.newSingleThreadExecutor();

    private GameServer() {
    }

    /** Escrituras de viajes y la sincronización no se pisan: un viaje ya borrado no se reinserta. */
    static void exclusive(@NonNull Runnable action) {
        synchronized (LOCK) {
            action.run();
        }
    }

    public interface ConnectionListener {
        void onServerAvailabilityChanged(boolean available);
    }

    public static boolean enabled() {
        return baseUrl() != null;
    }

    public static boolean isAvailable() {
        return !enabled() || serverAvailable;
    }

    public static void addConnectionListener(@NonNull ConnectionListener listener) {
        CONNECTION_LISTENERS.addIfAbsent(listener);
        listener.onServerAvailabilityChanged(isAvailable());
    }

    public static void removeConnectionListener(@NonNull ConnectionListener listener) {
        CONNECTION_LISTENERS.remove(listener);
    }

    /** Comprueba /health fuera del hilo principal y notifica el estado a la UI. */
    /**
     * Borra la cuenta autenticada y toda su partida en el servidor.
     * Hay que llamarlo fuera del hilo principal.
     */
    public static boolean deleteAccount() {
        if (!enabled() || isMainThread()) {
            return false;
        }
        return request("POST", "/account/delete", "{}").status == 200;
    }

    /** Borra en el servidor la partida de un jugador. El perfil no se toca. */
    @Nullable
    public static String wipeOwner(@NonNull String ownerId) {
        if (!enabled()) {
            return null;
        }
        if (ownerId.isEmpty() || isMainThread()) {
            return "No se ha podido reiniciar la partida en el servidor.";
        }
        try {
            JSONObject body = new JSONObject();
            body.put("ownerId", ownerId);
            HttpResult result = request("POST", "/player-reset", body.toString());
            if (result.status / 100 == 2) {
                return null;
            }
            String detail = result.body != null ? result.body : "";
            if (detail.length() > 180) {
                detail = detail.substring(0, 180);
            }
            return "No se ha podido reiniciar la partida en el servidor (" + result.status + "). "
                    + detail;
        } catch (JSONException ignored) {
            return "No se ha podido reiniciar la partida en el servidor.";
        }
    }

    /** Borra en el servidor la partida de todos. Solo lo acepta la cuenta administradora. */
    public static void wipeEveryone() {
        if (!enabled() || isMainThread()) {
            return;
        }
        request("POST", "/player-reset", "{\"all\":true}");
    }

    public static void checkServerAsync() {
        if (!enabled()) {
            markServerAvailability(true);
            return;
        }
        CONNECTION_IO.execute(() -> request("GET", "/health", null));
    }

    private static void markServerAvailability(boolean available) {
        if (serverAvailable == available && available) {
            return;
        }
        serverAvailable = available;
        for (ConnectionListener listener : CONNECTION_LISTENERS) {
            listener.onServerAvailabilityChanged(available);
        }
    }

    public static boolean pushTruck(@NonNull Context context, @Nullable TruckTripEntity trip) {
        return pushTruck(context, trip, false);
    }

    public static boolean pushTruck(@NonNull Context context, @Nullable TruckTripEntity trip,
            boolean allowTimingReset) {
        if (!enabled() || trip == null || trip.hiveId.isEmpty() || isMainThread()) {
            return false;
        }
        return put("/truck-trips/" + enc(trip.hiveId), truckJson(trip, allowTimingReset)) / 100 == 2;
    }

    public static boolean pushCargo(@NonNull Context context, @Nullable CargoTripEntity trip) {
        return pushCargo(context, trip, false);
    }

    public static boolean pushCargo(@NonNull Context context, @Nullable CargoTripEntity trip,
            boolean geometryOnly) {
        if (!enabled() || trip == null || trip.id.isEmpty() || isMainThread()) {
            return false;
        }
        return put("/cargo-trips/" + enc(trip.id), cargoJson(trip, geometryOnly)) / 100 == 2;
    }

    public static void syncBlocking(@NonNull Context context) {
        if (!enabled() || !isAvailable() || isMainThread()) {
            return;
        }
        SignedInUser user = PlayerAuth.getInstance().getCurrentUser();
        if (user == null) {
            return;
        }
        synchronized (LOCK) {
            SYNC_DEADLINE_NANOS.set(System.nanoTime() + SYNC_BUDGET_MS * 1_000_000L);
            try {
                Context app = context.getApplicationContext();
                String owner = user.getUid();
                SYNC_TOKEN.set(firebaseToken());
                // El reloj del servidor cierra lo que ya llegó. Luego el teléfono
                // sustituye su copia por esa lista y no vuelve a publicar un viaje
                // que el servidor ya no tiene.
                post("/trip-clock/run", "{}");
                JSONArray trucks = getArray("/truck-trips?ownerId=" + enc(owner));
                JSONArray cargos = getArray("/cargo-trips?ownerId=" + enc(owner));
                if (trucks == null || cargos == null) {
                    return;
                }
                Set<String> serverTrucks = idSet(trucks);
                Set<String> serverCargos = idSet(cargos);
                long now = System.currentTimeMillis();
                Set<String> truckSnapshot = new HashSet<>();
                Set<String> cargoSnapshot = new HashSet<>();
                Set<String> keepTrucks = pushMissingLiveTrucks(app, serverTrucks, truckSnapshot, now);
                Set<String> keepCargo = pushMissingLiveCargos(app, serverCargos, cargoSnapshot, now);
                if (!keepTrucks.isEmpty() || !keepCargo.isEmpty()) {
                    JSONArray trucksAgain = getArray("/truck-trips?ownerId=" + enc(owner));
                    JSONArray cargosAgain = getArray("/cargo-trips?ownerId=" + enc(owner));
                    if (trucksAgain != null && cargosAgain != null) {
                        trucks = trucksAgain;
                        cargos = cargosAgain;
                        keepTrucks.clear();
                        keepCargo.clear();
                    }
                }
                replaceTrucks(app, trucks, keepTrucks, truckSnapshot);
                replaceCargos(app, cargos, keepCargo, cargoSnapshot);
                applyEffects(app, owner);
                FleetStore.releaseIdle(app, owner);
            } finally {
                SYNC_TOKEN.remove();
                SYNC_DEADLINE_NANOS.remove();
            }
        }
    }

    /**
     * Sincroniza el catálogo autoritativo de comandas y ofertas. Si la API no
     * responde, conserva la caché local y devuelve false para que el cliente no
     * regenere ni elimine filas por su cuenta.
     */
    public static boolean syncOffersBlocking(@NonNull Context context) {
        return syncOffersBlocking(context, Double.NaN, Double.NaN, 0);
    }

    /**
     * @param radiusKm radio alrededor de la sede. Con radio &gt; 0 solo entran esas
     *                  comandas y no se borra el resto del catálogo local.
     */
    public static boolean syncOffersBlocking(@NonNull Context context, double lat, double lng,
            double radiusKm) {
        if (!enabled() || !isAvailable() || isMainThread()) {
            return false;
        }
        boolean nearbyOnly = radiusKm > 0 && !Double.isNaN(lat) && !Double.isNaN(lng);
        synchronized (LOCK) {
            long now = System.currentTimeMillis();
            if (!nearbyOnly && lastOfferSyncMs > 0L && now - lastOfferSyncMs < 750L) {
                return true;
            }
            Long previous = SYNC_DEADLINE_NANOS.get();
            SYNC_DEADLINE_NANOS.set(System.nanoTime() + OFFER_SYNC_BUDGET_MS * 1_000_000L);
            try {
                String uid = currentUid();
                String snapshotPath = "/offer-snapshot"
                        + (uid != null ? "?ownerId=" + enc(uid) : "");
                if (nearbyOnly) {
                    snapshotPath += (snapshotPath.contains("?") ? "&" : "?")
                            + "nearLat=" + lat
                            + "&nearLng=" + lng
                            + "&nearKm=" + radiusKm;
                }
                JSONObject payload = getObject(snapshotPath);
                if (payload == null || !payload.optBoolean("ready", false)) {
                    return false;
                }
                JSONArray orders = payload.optJSONArray("orders");
                JSONArray offers = payload.optJSONArray("offers");
                if (orders == null || offers == null) {
                    return false;
                }
                Context app = context.getApplicationContext();
                Set<String> liveOrders = new HashSet<>();
                Set<String> liveOffers = new HashSet<>();
                AppDatabase db = AppDatabase.getInstance(app);
                Map<String, HoneyOrderEntity> savedOrders = new HashMap<>();
                List<HoneyOrderEntity> localOrders = db.honeyOrderDao().getAllSync();
                if (localOrders != null) {
                    for (HoneyOrderEntity row : localOrders) {
                        if (row != null) {
                            savedOrders.put(row.id, row);
                        }
                    }
                }
                Map<String, PollinationOfferEntity> savedOffers = new HashMap<>();
                List<PollinationOfferEntity> localOffers = db.pollinationOfferDao().getOpenSync();
                if (localOffers != null) {
                    for (PollinationOfferEntity row : localOffers) {
                        if (row != null) {
                            savedOffers.put(row.id, row);
                        }
                    }
                }
                db.runInTransaction(() -> {
                    long snapshotNow = System.currentTimeMillis();
                    List<HoneyOrderEntity> orderWrites = new ArrayList<>();
                    for (int i = 0; i < orders.length(); i++) {
                        HoneyOrderEntity row = honeyOrderFromServer(orders.optJSONObject(i));
                        if (row == null || (row.taken && (uid == null || !uid.equals(row.claimedBy)))) {
                            continue;
                        }
                        if (!row.taken && row.expireEpochMs <= snapshotNow) {
                            continue;
                        }
                        liveOrders.add(row.id);
                        if (!sameOrder(savedOrders.get(row.id), row)) {
                            orderWrites.add(row);
                        }
                    }
                    if (!orderWrites.isEmpty()) {
                        db.honeyOrderDao().upsertAll(orderWrites);
                    }
                    if (!nearbyOnly) {
                        for (HoneyOrderEntity row : savedOrders.values()) {
                            if (!liveOrders.contains(row.id)) {
                                db.honeyOrderDao().delete(row.id);
                            }
                        }
                    }
                    List<PollinationOfferEntity> offerWrites = new ArrayList<>();
                    for (int i = 0; i < offers.length(); i++) {
                        PollinationOfferEntity row = pollinationOfferFromServer(
                                offers.optJSONObject(i));
                        if (row == null || row.taken || row.expireEpochMs <= snapshotNow) {
                            continue;
                        }
                        liveOffers.add(row.id);
                        if (!sameOffer(savedOffers.get(row.id), row)) {
                            offerWrites.add(row);
                        }
                    }
                    if (!offerWrites.isEmpty()) {
                        db.pollinationOfferDao().upsertAll(offerWrites);
                    }
                    if (!nearbyOnly) {
                        for (PollinationOfferEntity row : savedOffers.values()) {
                            if (!liveOffers.contains(row.id)) {
                                db.pollinationOfferDao().delete(row.id);
                            }
                        }
                    }
                });
                if (!nearbyOnly) {
                    lastOfferSyncMs = System.currentTimeMillis();
                }
                return true;
            } finally {
                if (previous == null) {
                    SYNC_DEADLINE_NANOS.remove();
                } else {
                    SYNC_DEADLINE_NANOS.set(previous);
                }
            }
        }
    }

    /** Publica el estado del contrato para que el reloj de ofertas reserve su finca. */
    public static void pushPollinationContract(@NonNull Context context,
            @Nullable PollinationContractEntity contract) {
        if (!enabled() || contract == null || contract.id == null || contract.id.isEmpty()
                || isMainThread()) {
            return;
        }
        try {
            JSONObject body = new JSONObject();
            body.put("ownerId", contract.ownerId);
            body.put("hexId", contract.hexId);
            body.put("flora", contract.flora);
            body.put("npcName", contract.npcName);
            body.put("estateName", contract.estateName);
            body.put("region", contract.region);
            body.put("climateZone", contract.climateZone);
            body.put("layer", contract.layer);
            body.put("status", contract.status);
            body.put("collectedKg", contract.collectedKg);
            body.put("poolKg", contract.poolKg);
            body.put("sawPeak", contract.sawPeak);
            body.put("minPct", contract.minPct);
            body.put("payB", contract.payB);
            body.put("extraBPerPoint", contract.extraBPerPoint);
            body.put("travelCostPaid", contract.travelCostPaid);
            body.put("acceptedDayKey", contract.acceptedDayKey);
            body.put("workDays", contract.workDays);
            body.put("dueDayKey", contract.dueDayKey);
            body.put("startDoy", contract.startDoy);
            body.put("hiveIdsJson", contract.hiveIdsJson);
            put("/pollination-contracts/" + enc(contract.id), body);
        } catch (Exception ignored) {
        }
    }

    public static boolean claimOrder(@NonNull String orderId, @NonNull String ownerId) {
        return offerAction("claim-order", orderId, ownerId);
    }

    public static boolean releaseOrder(@NonNull String orderId, @NonNull String ownerId) {
        return offerAction("release-order", orderId, ownerId);
    }

    public static boolean finishOrder(@NonNull String orderId, @NonNull String ownerId) {
        return offerAction("finish-order", orderId, ownerId);
    }

    public static boolean takePollinationOffer(@NonNull String offerId) {
        return offerAction("take-offer", offerId, null);
    }

    public static boolean claimPollinationOffer(@NonNull String hexId, int band,
            @NonNull String flora, int startDoy, @NonNull String ownerId) {
        if (!enabled() || hexId.isEmpty() || flora.isEmpty() || ownerId.isEmpty()
                || isMainThread()) {
            return false;
        }
        try {
            JSONObject body = new JSONObject();
            body.put("type", "claim-pollination-offer");
            body.put("hexId", hexId);
            body.put("band", band);
            body.put("flora", flora);
            body.put("startDoy", startDoy);
            body.put("ownerId", ownerId);
            return postOfferAction(body, "claim-pollination-offer");
        } catch (Exception ignored) {
            return false;
        }
    }

    public static boolean releasePollinationOffer(@NonNull String hexId, int band,
            @NonNull String flora, int startDoy, @NonNull String ownerId) {
        if (!enabled() || hexId.isEmpty() || flora.isEmpty() || ownerId.isEmpty()
                || isMainThread()) {
            return false;
        }
        try {
            JSONObject body = new JSONObject();
            body.put("type", "release-pollination-offer");
            body.put("hexId", hexId);
            body.put("band", band);
            body.put("flora", flora);
            body.put("startDoy", startDoy);
            body.put("ownerId", ownerId);
            return postOfferAction(body, "release-pollination-offer");
        } catch (Exception ignored) {
            return false;
        }
    }

    public static boolean takePollinationOfferByHex(@NonNull String hexId, int band,
            @NonNull String ownerId) {
        if (!enabled() || hexId.isEmpty() || ownerId.isEmpty() || isMainThread()) {
            return false;
        }
        try {
            JSONObject body = new JSONObject();
            body.put("type", "take-offer-by-hex");
            body.put("hexId", hexId);
            body.put("band", band);
            body.put("ownerId", ownerId);
            return postOfferAction(body, "take-offer-by-hex");
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean offerAction(@NonNull String type, @NonNull String id,
            @Nullable String ownerId) {
        if (!enabled() || id.isEmpty() || isMainThread()) {
            lastOfferStatus = 0;
            lastOfferReason = !enabled() ? "disabled" : "local";
            return false;
        }
        try {
            JSONObject body = new JSONObject();
            body.put("type", type);
            body.put("id", id);
            if (ownerId != null) {
                body.put("ownerId", ownerId);
            }
            return postOfferAction(body, type);
        } catch (Exception ignored) {
            return false;
        }
    }

    private static volatile int lastOfferStatus;
    @Nullable
    private static volatile String lastOfferReason;

    public static int lastOfferStatus() {
        return lastOfferStatus;
    }

    @Nullable
    public static String lastOfferReason() {
        return lastOfferReason;
    }

    private static boolean postOfferAction(@NonNull JSONObject body, @NonNull String type) {
        if (SYNC_TOKEN.get() == null || SYNC_TOKEN.get().trim().isEmpty()) {
            String idToken = firebaseToken();
            if (idToken != null && !idToken.trim().isEmpty()) {
                SYNC_TOKEN.set(idToken);
            }
        }
        Long previous = SYNC_DEADLINE_NANOS.get();
        SYNC_DEADLINE_NANOS.set(System.nanoTime() + OFFER_SYNC_BUDGET_MS * 1_000_000L);
        try {
            HttpResult result = request("POST", "/offer-actions", body.toString());
            lastOfferStatus = result.status;
            lastOfferReason = offerReason(result.body);
            Log.w("OrderDispatch",
                    "offer-actions " + type + " status=" + result.status
                            + " reason=" + lastOfferReason);
            return result.status / 100 == 2;
        } finally {
            if (previous == null) {
                SYNC_DEADLINE_NANOS.remove();
            } else {
                SYNC_DEADLINE_NANOS.set(previous);
            }
        }
    }

    @Nullable
    private static String offerReason(@Nullable String body) {
        if (body == null || body.isEmpty()) {
            return null;
        }
        try {
            JSONObject o = new JSONObject(body);
            String reason = o.optString("reason", "");
            if (reason.isEmpty()) {
                reason = o.optString("error", "");
            }
            return reason.isEmpty() ? null : reason;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static boolean sameOrder(@Nullable HoneyOrderEntity saved, @NonNull HoneyOrderEntity row) {
        if (saved == null) {
            return false;
        }
        return saved.portraitIndex == row.portraitIndex
                && saved.createdDayKey == row.createdDayKey
                && saved.expireEpochMs == row.expireEpochMs
                && saved.taken == row.taken
                && saved.band == row.band
                && Double.compare(saved.kg, row.kg) == 0
                && Double.compare(saved.unitPrice, row.unitPrice) == 0
                && Double.compare(saved.destLat, row.destLat) == 0
                && Double.compare(saved.destLng, row.destLng) == 0
                && Objects.equals(saved.npcName, row.npcName)
                && Objects.equals(saved.floraKey, row.floraKey)
                && Objects.equals(saved.destHexId, row.destHexId)
                && Objects.equals(saved.destLabel, row.destLabel)
                && Objects.equals(saved.region, row.region)
                && Objects.equals(saved.claimedBy, row.claimedBy);
    }

    private static boolean sameOffer(@Nullable PollinationOfferEntity saved,
            @NonNull PollinationOfferEntity row) {
        if (saved == null) {
            return false;
        }
        return saved.startDoy == row.startDoy
                && saved.endDoy == row.endDoy
                && saved.band == row.band
                && saved.createdDayKey == row.createdDayKey
                && saved.expireEpochMs == row.expireEpochMs
                && saved.portraitIndex == row.portraitIndex
                && saved.taken == row.taken
                && Double.compare(saved.destLat, row.destLat) == 0
                && Double.compare(saved.destLng, row.destLng) == 0
                && Objects.equals(saved.hexId, row.hexId)
                && Objects.equals(saved.flora, row.flora)
                && Objects.equals(saved.region, row.region)
                && Objects.equals(saved.npcName, row.npcName);
    }

    @Nullable
    private static HoneyOrderEntity honeyOrderFromServer(@Nullable JSONObject row) {
        if (row == null) {
            return null;
        }
        String id = row.optString("id", "");
        if (id.isEmpty()) {
            return null;
        }
        HoneyOrderEntity order = new HoneyOrderEntity();
        order.id = id;
        order.npcName = optText(row, "npcName");
        order.portraitIndex = row.optInt("portraitIndex");
        order.floraKey = optText(row, "floraKey");
        order.kg = row.optDouble("kg");
        order.unitPrice = row.optDouble("unitPrice");
        order.destHexId = optText(row, "destHexId");
        order.destLat = row.optDouble("destLat");
        order.destLng = row.optDouble("destLng");
        order.destLabel = optText(row, "destLabel");
        order.region = optText(row, "region");
        order.createdDayKey = row.optInt("createdDayKey");
        order.expireEpochMs = row.optLong("expireEpochMs");
        order.taken = row.optBoolean("taken");
        order.claimedBy = optText(row, "claimedBy");
        order.band = row.optInt("band");
        return order;
    }

    @Nullable
    private static PollinationOfferEntity pollinationOfferFromServer(@Nullable JSONObject row) {
        if (row == null) {
            return null;
        }
        String id = row.optString("id", "");
        String hexId = optText(row, "hexId");
        if (id.isEmpty() || hexId == null) {
            return null;
        }
        PollinationOfferEntity offer = new PollinationOfferEntity();
        offer.id = id;
        offer.hexId = hexId;
        offer.flora = optText(row, "flora");
        offer.startDoy = row.optInt("startDoy");
        offer.endDoy = row.optInt("endDoy");
        offer.band = row.optInt("band");
        offer.region = optText(row, "region");
        offer.createdDayKey = row.optInt("createdDayKey");
        offer.expireEpochMs = row.optLong("expireEpochMs");
        offer.destLat = row.optDouble("destLat");
        offer.destLng = row.optDouble("destLng");
        offer.npcName = optText(row, "npcName");
        offer.portraitIndex = row.optInt("portraitIndex");
        offer.taken = row.optBoolean("taken");
        return offer;
    }

    @Nullable
    private static String currentUid() {
        try {
            SignedInUser user = PlayerAuth.getInstance().getCurrentUser();
            return user != null ? user.getUid() : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static boolean localArrivalAlreadyApplied(@NonNull Context app,
            @NonNull TruckTripEntity trip) {
        if (trip.startEpochMs <= 0 || trip.durationMs <= 0
                || !TruckTripRules.wallClockDone(trip, System.currentTimeMillis())) {
            return false;
        }
        HiveEntity hive = AppDatabase.getInstance(app).hiveDao().getHiveByIdSync(trip.hiveId);
        return hive != null && TruckTripRules.isHeadingHome(trip, hive);
    }

    @NonNull
    private static Set<String> idSet(@NonNull JSONArray rows) {
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i);
            if (row == null) {
                continue;
            }
            String id = row.optString("id", "");
            if (!id.isEmpty()) {
                ids.add(id);
            }
        }
        return ids;
    }

    @NonNull
    private static Set<String> pushMissingLiveTrucks(@NonNull Context app,
            @NonNull Set<String> onServer, @NonNull Set<String> snapshot, long now) {
        Set<String> keep = new HashSet<>();
        List<TruckTripEntity> rows = AppDatabase.getInstance(app).truckTripDao().getAllSync();
        if (rows == null) {
            return keep;
        }
        for (TruckTripEntity trip : rows) {
            if (trip == null || trip.hiveId.isEmpty()) {
                continue;
            }
            snapshot.add(trip.hiveId);
            if (onServer.contains(trip.hiveId) || TruckTripRules.wallClockDone(trip, now)
                    || localArrivalAlreadyApplied(app, trip)) {
                continue;
            }
            if (keepLocal(put("/truck-trips/" + enc(trip.hiveId), truckJson(trip, false)))) {
                keep.add(trip.hiveId);
            }
        }
        return keep;
    }

    @NonNull
    private static Set<String> pushMissingLiveCargos(@NonNull Context app,
            @NonNull Set<String> onServer, @NonNull Set<String> snapshot, long now) {
        Set<String> keep = new HashSet<>();
        List<CargoTripEntity> rows = AppDatabase.getInstance(app).cargoTripDao().getAllSync();
        if (rows == null) {
            return keep;
        }
        for (CargoTripEntity trip : rows) {
            if (trip == null || trip.id.isEmpty()) {
                continue;
            }
            snapshot.add(trip.id);
            if (onServer.contains(trip.id) || CargoTripRules.wallClockDone(trip, now)) {
                continue;
            }
            if (keepLocal(put("/cargo-trips/" + enc(trip.id), cargoJson(trip, false)))) {
                keep.add(trip.id);
            }
        }
        return keep;
    }

    private static void replaceTrucks(@NonNull Context app, @NonNull JSONArray rows,
            @NonNull Set<String> keepOnFailure, @NonNull Set<String> snapshot) {
        AppDatabase db = AppDatabase.getInstance(app);
        Set<String> live = new HashSet<>();
        for (int i = 0; i < rows.length(); i++) {
            TruckTripEntity trip = truckFrom(rows.optJSONObject(i));
            if (trip == null) {
                continue;
            }
            live.add(trip.hiveId);
            db.truckTripDao().upsert(trip);
        }
        List<TruckTripEntity> local = db.truckTripDao().getAllSync();
        if (local == null) {
            return;
        }
        for (TruckTripEntity trip : local) {
            if (trip == null || !snapshot.contains(trip.hiveId)
                    || live.contains(trip.hiveId) || keepOnFailure.contains(trip.hiveId)) {
                continue;
            }
            db.truckTripDao().delete(trip.hiveId);
        }
    }

    private static void replaceCargos(@NonNull Context app, @NonNull JSONArray rows,
            @NonNull Set<String> keepOnFailure, @NonNull Set<String> snapshot) {
        AppDatabase db = AppDatabase.getInstance(app);
        Set<String> live = new HashSet<>();
        for (int i = 0; i < rows.length(); i++) {
            CargoTripEntity trip = cargoFrom(rows.optJSONObject(i));
            if (trip == null) {
                continue;
            }
            live.add(trip.id);
            db.cargoTripDao().upsert(trip);
        }
        List<CargoTripEntity> local = db.cargoTripDao().getAllSync();
        if (local == null) {
            return;
        }
        for (CargoTripEntity trip : local) {
            if (trip == null || !snapshot.contains(trip.id)
                    || live.contains(trip.id) || keepOnFailure.contains(trip.id)) {
                continue;
            }
            db.cargoTripDao().delete(trip.id);
        }
    }

    private static void applyEffects(@NonNull Context app, @NonNull String ownerId) {
        JSONArray rows = getArray("/trip-effects?ownerId=" + enc(ownerId));
        if (rows == null) {
            return;
        }
        SharedPreferences prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        Set<String> applied = new HashSet<>(prefs.getStringSet(APPLIED, new HashSet<>()));
        ApicultureApp game = (ApicultureApp) app;
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i);
            if (row == null) {
                continue;
            }
            String id = row.optString("id", "");
            JSONObject payload = row.optJSONObject("payload");
            if (id.isEmpty() || payload == null) {
                continue;
            }
            if (!applied.contains(id)) {
                applyOne(app, game, ownerId, payload);
                applied.add(id);
                prefs.edit().putStringSet(APPLIED, new HashSet<>(applied)).apply();
            }
            // También se limpia una fila local si el efecto ya estaba marcado como
            // aplicado por una sincronización anterior que no pudo eliminarla.
            String effectType = payload.optString("type", "");
            if ("truck-arrive".equals(effectType)) {
                TruckLiveTrips.forgetLocalTrip(app, payload.optString("hiveId", ""));
            } else if ("release-vehicle".equals(effectType)) {
                HoneyLogistics.forgetLocalTrip(app, payload.optString("tripId", ""));
            }
            delete("/trip-effects/" + enc(id));
        }
    }

    private static void applyOne(@NonNull Context app, @NonNull ApicultureApp game,
            @NonNull String ownerId, @NonNull JSONObject payload) {
        String type = payload.optString("type", "");
        if ("truck-arrive".equals(type)) {
            String hiveId = payload.optString("hiveId", "");
            TruckLiveTrips.applyArrival(app, hiveId,
                    payload.optDouble("lat"), payload.optDouble("lng"),
                    optText(payload, "hexId"), optText(payload, "flora"));
            FleetStore.releaseHive(app, ownerId, hiveId);
            return;
        }
        if ("release-vehicle".equals(type)) {
            FleetStore.releaseVehicle(app, ownerId, optText(payload, "vehicleId"));
            return;
        }
        if ("collect-credit".equals(type)) {
            Map<String, Double> collected = lines(payload);
            String buckets = payload.optString("honeyBuckets", "");
            if (!buckets.isEmpty()) {
                game.getEconomyRepository().applyFromCloud(
                        game.getEconomyRepository().getBalance(), buckets);
                if (payload.has("honeyStockSeq")) {
                    game.getEconomyRepository().setHoneyStockSeq(payload.optLong("honeyStockSeq"));
                }
                JSONObject stock = payload.optJSONObject("warehouseStock");
                if (stock != null) {
                    WarehouseHoneyStore.applyServer(app, ownerId, stock);
                }
            } else {
                HoneyLogistics.creditCargo(app, ownerId, optText(payload, "destHexId"),
                        game.getEconomyRepository(), collected);
            }
            HarvestReceipts.publish(app, optText(payload, "tripId"), collected,
                    payload.optInt("hiveCount", 1));
            return;
        }
        if ("warehouse-credit".equals(type)) {
            creditWarehouse(app, game, ownerId, optText(payload, "flora"),
                    payload.optDouble("kg"), optText(payload, "hexId"));
            return;
        }
        if ("warehouse-lines".equals(type)) {
            String hexId = optText(payload, "destHexId");
            for (Map.Entry<String, Double> line : lines(payload).entrySet()) {
                creditWarehouse(app, game, ownerId, line.getKey(), line.getValue(), hexId);
            }
            return;
        }
        if ("sale".equals(type)) {
            applySale(app, game, ownerId, payload);
        }
    }

    private static void applySale(@NonNull Context app, @NonNull ApicultureApp game,
            @NonNull String ownerId, @NonNull JSONObject payload) {
        String kind = payload.optString("kind", "");
        boolean missed = payload.optBoolean("missed", false);
        double credited = 0.0;
        double balanceBeforeSale = game.getEconomyRepository().getBalance();
        if (!missed) {
            boolean locked = payload.optBoolean("priceLocked", false);
            double unit = payload.optDouble("unitPrice", 0.0);
            for (Map.Entry<String, Double> line : lines(payload).entrySet()) {
                double price = unit;
                if (!locked && CargoTripEntity.KIND_WHOLESALE.equals(kind)) {
                    ProvincialMarket dest = ProvincialMarketCatalog.findByName(app,
                            PlayableMapRegion.fromHexId(optText(payload, "destHexId")),
                            optText(payload, "destLabel"));
                    if (dest != null) {
                        price = game.getMarketRepository().priceEurPerKgForFlora(line.getKey(), dest);
                    }
                }
                game.getEconomyRepository().creditSaleProceeds(line.getKey(), line.getValue(), price,
                        EconomyRepository.saleConcept(
                                CargoTripEntity.KIND_ORDER.equals(kind) ? "vía comanda" : "en el mercado",
                                line.getKey(), line.getValue()));
                credited += line.getValue();
            }
            if (payload.optBoolean("balanceCredited")) {
                game.getEconomyRepository().setBalance(
                        balanceBeforeSale + payload.optDouble("creditedEur", 0.0));
            }
        }
        if (CargoTripEntity.KIND_ORDER.equals(kind)) {
            HoneyOrderStore.finish(app, optText(payload, "orderId"));
            if (credited > 1e-9) {
                game.getHiveRepository().grantXp(ownerId, XpAwards.orderDelivered(credited));
            }
        } else if (CargoTripEntity.KIND_WHOLESALE.equals(kind) && credited > 1e-9) {
            game.getHiveRepository().grantXp(ownerId, XpAwards.marketSold(credited));
        }
    }

    private static void creditWarehouse(@NonNull Context app, @NonNull ApicultureApp game,
            @NonNull String ownerId, @Nullable String flora, double kg, @Nullable String hexId) {
        if (flora == null || kg <= 1e-9) {
            return;
        }
        game.getEconomyRepository().addHoney(flora, kg);
        WarehouseHoneyStore.add(app, ownerId, hexId, flora, kg);
    }

    @NonNull
    private static Map<String, Double> lines(@NonNull JSONObject payload) {
        Map<String, Double> out = new LinkedHashMap<>();
        JSONArray rows = payload.optJSONArray("lines");
        if (rows == null) {
            return out;
        }
        for (int i = 0; i < rows.length(); i++) {
            JSONObject line = rows.optJSONObject(i);
            if (line == null) {
                continue;
            }
            String flora = optText(line, "flora");
            double kg = line.optDouble("kg", 0.0);
            if (flora != null && kg > 1e-9) {
                out.put(flora, kg);
            }
        }
        return out;
    }

    @Nullable
    private static TruckTripEntity truckFrom(@Nullable JSONObject row) {
        if (row == null) {
            return null;
        }
        String id = row.optString("id", "");
        if (id.isEmpty()) {
            return null;
        }
        TruckTripEntity trip = new TruckTripEntity();
        trip.hiveId = id;
        trip.ownerId = optText(row, "ownerId");
        trip.originLat = row.optDouble("originLat");
        trip.originLng = row.optDouble("originLng");
        trip.destLat = row.optDouble("destLat");
        trip.destLng = row.optDouble("destLng");
        trip.destHexId = optText(row, "destHexId");
        trip.destFlora = optText(row, "destFlora");
        trip.startEpochMs = row.optLong("startEpochMs");
        trip.durationMs = row.optLong("durationMs");
        trip.routePolyline = optText(row, "routePolyline");
        trip.routeRoadKinds = optText(row, "routeRoadKinds");
        return trip;
    }

    @Nullable
    private static CargoTripEntity cargoFrom(@Nullable JSONObject row) {
        if (row == null) {
            return null;
        }
        String id = row.optString("id", "");
        if (id.isEmpty()) {
            return null;
        }
        CargoTripEntity trip = new CargoTripEntity();
        trip.id = id;
        trip.ownerId = optText(row, "ownerId");
        trip.kind = optText(row, "kind");
        trip.phase = optText(row, "phase");
        trip.floraKey = optText(row, "floraKey");
        trip.kg = row.optDouble("kg");
        trip.cargoJson = optText(row, "cargoJson");
        trip.originLat = row.optDouble("originLat");
        trip.originLng = row.optDouble("originLng");
        trip.destLat = row.optDouble("destLat");
        trip.destLng = row.optDouble("destLng");
        trip.originLabel = optText(row, "originLabel");
        trip.destLabel = optText(row, "destLabel");
        trip.originHexId = optText(row, "originHexId");
        trip.destHexId = optText(row, "destHexId");
        trip.returnLat = row.optDouble("returnLat");
        trip.returnLng = row.optDouble("returnLng");
        trip.returnLabel = optText(row, "returnLabel");
        trip.returnHexId = optText(row, "returnHexId");
        trip.unitPrice = row.optDouble("unitPrice");
        trip.npcName = optText(row, "npcName");
        trip.hiveId = optText(row, "hiveId");
        trip.startEpochMs = row.optLong("startEpochMs");
        trip.durationMs = row.optLong("durationMs");
        trip.routePolyline = optText(row, "routePolyline");
        trip.routeRoadKinds = optText(row, "routeRoadKinds");
        trip.orderId = optText(row, "orderId");
        trip.legRole = optText(row, "legRole");
        trip.vehicleId = optText(row, "vehicleId");
        trip.shipmentId = optText(row, "shipmentId");
        trip.chainLat = row.optDouble("chainLat");
        trip.chainLng = row.optDouble("chainLng");
        trip.chainLabel = optText(row, "chainLabel");
        trip.chainHexId = optText(row, "chainHexId");
        trip.priceLocked = row.optInt("priceLocked");
        return trip;
    }

    @NonNull
    private static JSONObject truckJson(@NonNull TruckTripEntity trip, boolean allowTimingReset) {
        JSONObject body = new JSONObject();
        try {
            body.put("ownerId", trip.ownerId);
            body.put("originLat", trip.originLat);
            body.put("originLng", trip.originLng);
            body.put("destLat", trip.destLat);
            body.put("destLng", trip.destLng);
            body.put("destHexId", trip.destHexId);
            body.put("destFlora", trip.destFlora);
            body.put("startEpochMs", trip.startEpochMs);
            body.put("durationMs", trip.durationMs);
            body.put("routePolyline", trip.routePolyline);
            body.put("routeRoadKinds", trip.routeRoadKinds);
            body.put("allowTimingReset", allowTimingReset);
        } catch (Exception ignored) {
        }
        return body;
    }

    @NonNull
    private static JSONObject cargoJson(@NonNull CargoTripEntity trip, boolean geometryOnly) {
        JSONObject body = new JSONObject();
        try {
            body.put("ownerId", trip.ownerId);
            body.put("kind", trip.kind);
            body.put("phase", trip.phase);
            body.put("floraKey", trip.floraKey);
            body.put("kg", trip.kg);
            body.put("cargoJson", trip.cargoJson);
            body.put("originLat", trip.originLat);
            body.put("originLng", trip.originLng);
            body.put("destLat", trip.destLat);
            body.put("destLng", trip.destLng);
            body.put("originLabel", trip.originLabel);
            body.put("destLabel", trip.destLabel);
            body.put("originHexId", trip.originHexId);
            body.put("destHexId", trip.destHexId);
            body.put("returnLat", trip.returnLat);
            body.put("returnLng", trip.returnLng);
            body.put("returnLabel", trip.returnLabel);
            body.put("returnHexId", trip.returnHexId);
            body.put("unitPrice", trip.unitPrice);
            body.put("npcName", trip.npcName);
            body.put("hiveId", trip.hiveId);
            body.put("startEpochMs", trip.startEpochMs);
            body.put("durationMs", trip.durationMs);
            body.put("routePolyline", trip.routePolyline);
            body.put("routeRoadKinds", trip.routeRoadKinds);
            body.put("orderId", trip.orderId);
            body.put("legRole", trip.legRole);
            body.put("vehicleId", trip.vehicleId);
            body.put("shipmentId", trip.shipmentId);
            body.put("chainLat", trip.chainLat);
            body.put("chainLng", trip.chainLng);
            body.put("chainLabel", trip.chainLabel);
            body.put("chainHexId", trip.chainHexId);
            body.put("priceLocked", trip.priceLocked);
            body.put("geometryOnly", geometryOnly);
        } catch (Exception ignored) {
        }
        return body;
    }

    @Nullable
    private static String baseUrl() {
        String url = BuildConfig.GAME_SERVER_URL;
        if (url == null) {
            return null;
        }
        url = url.trim();
        if (url.isEmpty()) {
            return null;
        }
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return url;
    }

    @Nullable
    private static JSONArray getArray(@NonNull String path) {
        HttpResult result = request("GET", path, null);
        if (result.status / 100 != 2 || result.body == null) {
            return null;
        }
        try {
            return new JSONArray(result.body);
        } catch (Exception ignored) {
            return null;
        }
    }

    @Nullable
    public static JSONArray productionReports(@NonNull String ownerId, int sinceDayKey) {
        if (!enabled()) {
            return null;
        }
        JSONObject stored = getObject(
                "/production-reports?ownerId=" + enc(ownerId) + "&since=" + sinceDayKey);
        return stored == null ? null : stored.optJSONArray("days");
    }

    @Nullable
    private static JSONObject getObject(@NonNull String path) {
        HttpResult result = request("GET", path, null);
        if (result.status / 100 != 2 || result.body == null) {
            return null;
        }
        try {
            return new JSONObject(result.body);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static boolean keepLocal(int status) {
        return status != 409 && status / 100 != 2;
    }

    private static int put(@NonNull String path, @NonNull JSONObject body) {
        return request("PUT", path, body.toString()).status;
    }

    @Nullable
    public static String runProductionClock(@NonNull String ownerId, @NonNull String hiveBodiesJson,
            @NonNull String timeZoneId, int sinceDayKey, boolean uploadLocalProgress) {
        if (!enabled() || ownerId.isEmpty() || isMainThread()) {
            return null;
        }
        try {
            JSONArray hives = new JSONArray(hiveBodiesJson);
            boolean upload = uploadLocalProgress && sinceDayKey > 0;
            if (!upload) {
                HttpResult existing = request("GET", "/hives?ownerId=" + enc(ownerId), null);
                boolean missing = existing.status != 200 || existing.body == null
                        || new JSONArray(existing.body).length() == 0;
                if (missing) {
                    for (int i = 0; i < hives.length(); i++) {
                        JSONObject hive = hives.optJSONObject(i);
                        if (hive == null) continue;
                        String id = hive.optString("id", "");
                        if (id.isEmpty()) continue;
                        if (put("/hives/" + enc(id), hive) == 0) {
                            return null;
                        }
                    }
                }
            }
            JSONObject body = new JSONObject();
            body.put("ownerId", ownerId);
            body.put("timeZoneId", timeZoneId);
            if (upload) {
                body.put("checkpointDayKey", sinceDayKey);
                body.put("hives", hives);
            }
            body.put("clientDayKey", sinceDayKey);
            HttpResult result = request("POST", "/production-clock/run", body.toString());
            Log.i("ProductionSync", "POST /production-clock/run status="
                    + result.status + " since=" + sinceDayKey
                    + " bytes=" + (result.body == null ? 0 : result.body.length()));
            if (result.status != 200 || result.body == null) {
                return null;
            }
            JSONObject report = new JSONObject(result.body);
            int through = report.optInt("settledDayKey", report.optInt("throughDayKey", 0));
            JSONArray tickDays = report.optJSONArray("days");
            if (through > 0) {
                HttpResult fresh = request("GET", "/hives?ownerId=" + enc(ownerId), null);
                if (fresh.status == 200 && fresh.body != null) {
                    report.put("hives", new JSONArray(fresh.body));
                }
                if (through > sinceDayKey && (tickDays == null || tickDays.length() == 0)) {
                    HttpResult reports = request("GET",
                            "/production-reports?ownerId=" + enc(ownerId) + "&since=" + sinceDayKey, null);
                    if (reports.status == 200 && reports.body != null) {
                        JSONObject stored = new JSONObject(reports.body);
                        if (stored.optJSONArray("days") != null) {
                            report.put("days", stored.optJSONArray("days"));
                        }
                    }
                }
            }
            return report.toString();
        } catch (Exception e) {
            return null;
        }
    }

    @Nullable
    public static String performAction(@NonNull String ownerId, @NonNull String type,
            @NonNull JSONObject body) {
        if (!enabled() || ownerId.isEmpty() || isMainThread()) {
            return null;
        }
        try {
            body.put("ownerId", ownerId);
            body.put("type", type);
            HttpResult result = request("POST", "/actions", body.toString());
            if (result.status != 200 || result.body == null) {
                return null;
            }
            return result.body;
        } catch (Exception e) {
            return null;
        }
    }

    @Nullable
    public static JSONArray hexParcels(@NonNull String ownerId) {
        if (!enabled() || ownerId.isEmpty() || isMainThread()) {
            return null;
        }
        return getArray("/hex-parcels?ownerId=" + enc(ownerId));
    }

    public static boolean pushHexParcel(@Nullable JSONObject body, @NonNull String id) {
        if (!enabled() || body == null || id.isEmpty() || isMainThread()) {
            return false;
        }
        return put("/hex-parcels/" + enc(id), body) / 100 == 2;
    }

    public static boolean deleteHexParcel(@NonNull String id) {
        if (!enabled() || id.isEmpty() || isMainThread()) {
            return false;
        }
        return request("DELETE", "/hex-parcels/" + enc(id), null).status / 100 == 2;
    }

    @Nullable
    public static JSONObject fetchJson(@NonNull String path) {
        if (!enabled() || isMainThread()) {
            return null;
        }
        return getObject(path);
    }

    @Nullable
    public static JSONArray fetchArray(@NonNull String path) {
        if (!enabled() || isMainThread()) {
            return null;
        }
        return getArray(path);
    }

    public static int putJson(@NonNull String path, @NonNull JSONObject body) {
        if (!enabled() || isMainThread()) {
            return 0;
        }
        return put(path, body);
    }

    public static int postJson(@NonNull String path, @NonNull JSONObject body) {
        if (!enabled() || isMainThread()) {
            return 0;
        }
        return request("POST", path, body.toString()).status;
    }

    public static void deletePath(@NonNull String path) {
        if (!enabled() || isMainThread() || path.isEmpty()) {
            return;
        }
        delete(path);
    }

    @Nullable
    public static JSONObject loadPlayerCard(@NonNull String uid) {
        if (uid.isEmpty()) {
            return null;
        }
        return fetchJson("/player-cards/" + enc(uid));
    }

    @Nullable
    public static JSONObject loadPlayer(@NonNull String uid) {
        return loadPlayerStatus(uid).body;
    }

    /** Distingue «no hay ficha» (404) de un fallo de red (estado 0 u otro). */
    @NonNull
    public static PlayerLoad loadPlayerStatus(@NonNull String uid) {
        if (uid.isEmpty() || !enabled() || isMainThread()) {
            return new PlayerLoad(0, null);
        }
        HttpResult result = request("GET", "/players/" + enc(uid), null);
        JSONObject body = null;
        if (result.status / 100 == 2 && result.body != null) {
            try {
                body = new JSONObject(result.body);
            } catch (Exception ignored) {
                return new PlayerLoad(0, null);
            }
        }
        return new PlayerLoad(result.status, body);
    }

    public static final class PlayerLoad {
        public final int status;
        @Nullable
        public final JSONObject body;

        PlayerLoad(int status, @Nullable JSONObject body) {
            this.status = status;
            this.body = body;
        }
    }

    public static boolean savePlayer(@NonNull String uid, @NonNull JSONObject body) {
        if (uid.isEmpty()) {
            return false;
        }
        return putJson("/players/" + enc(uid), body) / 100 == 2;
    }

    @Nullable
    public static JSONArray mapApiaries() {
        return fetchArray("/map-apiaries");
    }

    @Nullable
    public static JSONArray mapHives(@NonNull String ownerId, @NonNull String hexId) {
        if (ownerId.isEmpty() || hexId.isEmpty()) {
            return null;
        }
        return fetchArray("/map-hives?ownerId=" + enc(ownerId) + "&hexId=" + enc(hexId));
    }

    @Nullable
    public static JSONArray hives(@NonNull String ownerId) {
        if (ownerId.isEmpty()) {
            return null;
        }
        return fetchArray("/hives?ownerId=" + enc(ownerId));
    }

    public static boolean pushHive(@Nullable JSONObject hive) {
        if (hive == null || !enabled() || isMainThread()) {
            return false;
        }
        String id = hive.optString("id", "");
        if (id.isEmpty()) {
            return false;
        }
        return putJson("/hives/" + enc(id), hive) / 100 == 2;
    }

    public static boolean deleteHive(@Nullable String id) {
        if (id == null || id.isEmpty() || !enabled() || isMainThread()) {
            return false;
        }
        return request("DELETE", "/hives/" + enc(id), null).status / 100 == 2;
    }

    public static boolean saveStore(@NonNull String ownerId, @NonNull String kind, @Nullable JSONObject body) {
        if (ownerId.isEmpty() || kind.isEmpty() || !enabled() || isMainThread()) {
            return false;
        }
        try {
            JSONObject row = new JSONObject();
            row.put("ownerId", ownerId);
            row.put("kind", kind);
            row.put("body", body != null ? body : new JSONObject());
            return putJson("/player-stores/" + enc(ownerId + ":" + kind), row) / 100 == 2;
        } catch (Exception ignored) {
            return false;
        }
    }

    @Nullable
    public static JSONObject loadStore(@NonNull String ownerId, @NonNull String kind) {
        if (ownerId.isEmpty() || kind.isEmpty()) {
            return null;
        }
        JSONObject row = fetchJson("/player-stores/" + enc(ownerId + ":" + kind));
        if (row == null) {
            return null;
        }
        JSONObject body = row.optJSONObject("body");
        return body != null ? body : new JSONObject();
    }

    /** 2xx si el nombre queda reservado; 403 si pertenece a otro jugador. */
    public static int claimUnique(@NonNull String kind, @NonNull String key, @NonNull String ownerId) {
        try {
            JSONObject row = new JSONObject();
            row.put("kind", kind);
            row.put("nameKey", key);
            row.put("ownerId", ownerId);
            String id = kind + "_" + key;
            if (id.length() > 200) {
                id = id.substring(0, 200);
            }
            return putJson("/unique-names/" + enc(id), row);
        } catch (Exception e) {
            return 0;
        }
    }

    public static void addMarketSale(int dayKey, @NonNull String floraKey, double kg) {
        if (floraKey.isEmpty() || kg <= 0) {
            return;
        }
        try {
            JSONObject body = new JSONObject();
            body.put("dayKey", dayKey);
            body.put("floraKey", floraKey);
            body.put("kg", kg);
            postJson("/market-sales", body);
        } catch (Exception ignored) {
        }
    }

    public static void recordEventSale(double kg, @Nullable String instanceId) {
        if (kg <= 0) {
            return;
        }
        try {
            JSONObject body = new JSONObject();
            body.put("kg", kg);
            body.put("instanceId", instanceId != null ? instanceId : "");
            postJson("/events/sale", body);
        } catch (Exception ignored) {
        }
    }

    @Nullable
    public static JSONArray activeContractCounts() {
        return fetchArray("/pollination-contracts/active-counts");
    }

    /** Sube la fila de ranking de quien tiene la sesión. El listado lo calcula el servidor. */
    public static boolean publishRanking(@NonNull JSONObject body) {
        if (!enabled() || isMainThread()) {
            return false;
        }
        return put("/ranking/me", body) / 100 == 2;
    }

    /** Ranking ya ordenado en el servidor. {@code null} si la API no responde. */
    @Nullable
    public static JSONObject fetchRanking(@NonNull String metric, @Nullable String region,
            @Nullable String flora) {
        if (!enabled() || isMainThread()) {
            lastRankingStatus = 0;
            return null;
        }
        String path = "/ranking?metric=" + enc(metric);
        if (region != null && !region.trim().isEmpty()) {
            path += "&region=" + enc(region.trim());
        }
        if (flora != null && !flora.trim().isEmpty()) {
            path += "&flora=" + enc(flora.trim());
        }
        HttpResult result = request("GET", path, null);
        lastRankingStatus = result.status;
        if (result.status / 100 != 2 || result.body == null) {
            return null;
        }
        try {
            return new JSONObject(result.body);
        } catch (Exception ignored) {
            return null;
        }
    }

    public static int lastRankingStatus() {
        return lastRankingStatus;
    }

    private static volatile int lastRankingStatus;

    @Nullable
    public static JSONObject adminLiveTrips() {
        if (!enabled()) {
            return null;
        }
        HttpResult result = request("GET", "/admin/live-trips", null);
        if (result.status != 200 || result.body == null) {
            return null;
        }
        try {
            return new JSONObject(result.body);
        } catch (JSONException e) {
            return null;
        }
    }

    /** Cuerpo con {@code finished} o {@code returning}, o null si el servidor lo rechaza. */
    @Nullable
    public static JSONObject adminFinishTrip(@NonNull String tripId) {
        if (!enabled() || tripId.isEmpty()) {
            return null;
        }
        try {
            JSONObject body = new JSONObject();
            body.put("id", tripId);
            HttpResult result = request("POST", "/admin/live-trips/finish", body.toString());
            if (result.status != 200 || result.body == null) {
                return null;
            }
            return new JSONObject(result.body);
        } catch (JSONException e) {
            return null;
        }
    }

    private static int post(@NonNull String path, @NonNull String body) {
        return request("POST", path, body).status;
    }

    private static void delete(@NonNull String path) {
        request("DELETE", path, null);
    }

    @Nullable
    private static String firebaseToken() {
        return PlayerAuth.getInstance().freshIdToken();
    }

    @NonNull
    @Nullable
    public static String getJson(@NonNull String path) {
        if (!enabled() || isMainThread()) {
            return null;
        }
        HttpResult result = request("GET", path, null);
        return result.status == 200 ? result.body : null;
    }

    @Nullable
    public static String postJson(@NonNull String path, @Nullable String body) {
        if (!enabled() || isMainThread()) {
            return null;
        }
        HttpResult result = request("POST", path, body);
        return result.status == 200 ? result.body : null;
    }

    private static HttpResult request(@NonNull String method, @NonNull String path, @Nullable String body) {
        String base = baseUrl();
        if (base == null) {
            return new HttpResult(0, null);
        }
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(base + path).openConnection();
            int timeoutMs = 8000;
            Long deadline = SYNC_DEADLINE_NANOS.get();
            if (deadline != null) {
                long remainingMs = (deadline - System.nanoTime()) / 1_000_000L;
                if (remainingMs <= 0) {
                    markServerAvailability(false);
                    return new HttpResult(0, null);
                }
                timeoutMs = (int) Math.max(1L, Math.min(timeoutMs, remainingMs));
            }
            conn.setConnectTimeout(timeoutMs);
            conn.setReadTimeout(timeoutMs);
            conn.setRequestMethod(method);
            String apiToken = BuildConfig.GAME_SERVER_TOKEN;
            String idToken = null;
            if (!"/health".equals(path)) {
                idToken = SYNC_TOKEN.get();
                if (idToken == null || idToken.trim().isEmpty()) {
                    idToken = firebaseToken();
                }
                if (idToken != null) {
                    idToken = idToken.trim();
                }
                if (idToken != null && idToken.isEmpty()) {
                    idToken = null;
                }
            }
            if (idToken != null) {
                conn.setRequestProperty("Authorization", "Bearer " + idToken);
                conn.setRequestProperty("X-Firebase-Id-Token", idToken);
            } else if (apiToken != null && !apiToken.trim().isEmpty()) {
                conn.setRequestProperty("Authorization", "Bearer " + apiToken.trim());
            }
            if (body != null) {
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                conn.getOutputStream().write(bytes);
            }
            int status = conn.getResponseCode();
            markServerAvailability(status > 0 && status < 500);
            InputStream in = status / 100 == 2 ? conn.getInputStream() : conn.getErrorStream();
            return new HttpResult(status, read(in));
        } catch (Exception e) {
            Log.w("GameServer", method + " " + path + " failed", e);
            markServerAvailability(false);
            return new HttpResult(0, null);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    @Nullable
    private static String read(@Nullable InputStream in) throws Exception {
        if (in == null) {
            return null;
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) >= 0) {
            out.write(buf, 0, n);
        }
        return out.toString(StandardCharsets.UTF_8.name());
    }

    @NonNull
    private static String enc(@NonNull String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            return value;
        }
    }

    @Nullable
    private static String optText(@NonNull JSONObject row, @NonNull String key) {
        if (!row.has(key) || row.isNull(key)) {
            return null;
        }
        String value = row.optString(key, "");
        return value.isEmpty() ? null : value;
    }

    private static boolean isMainThread() {
        return Looper.getMainLooper() == Looper.myLooper();
    }

    private static final class HttpResult {
        final int status;
        @Nullable
        final String body;

        HttpResult(int status, @Nullable String body) {
            this.status = status;
            this.body = body;
        }
    }
}
