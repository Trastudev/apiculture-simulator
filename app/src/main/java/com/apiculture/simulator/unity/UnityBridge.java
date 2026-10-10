package com.apiculture.simulator.unity;

import android.content.Context;
import android.content.res.Configuration;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

import com.apiculture.simulator.ApicultureApp;
import com.apiculture.simulator.R;
import com.apiculture.simulator.data.local.AppDatabase;
import com.apiculture.simulator.data.local.entity.HexParcelFloraEntity;
import com.apiculture.simulator.data.local.entity.HiveEntity;
import com.apiculture.simulator.data.repository.GameLocale;
import com.apiculture.simulator.data.repository.HiveRepository;
import com.apiculture.simulator.data.repository.IberiaHexOverlayStore;
import com.apiculture.simulator.data.repository.WorkshopStore;
import com.apiculture.simulator.domain.workshop.WorkshopRules;
import com.apiculture.simulator.domain.workshop.JarMix;
import com.apiculture.simulator.data.repository.HoneyLogistics;
import com.apiculture.simulator.data.repository.TruckLiveTrips;
import com.apiculture.simulator.data.repository.FleetDispatch;
import com.apiculture.simulator.data.repository.FleetStore;
import com.apiculture.simulator.presentation.tutorial.TutorialBus;
import com.apiculture.simulator.presentation.tutorial.TutorialEvent;
import com.apiculture.simulator.domain.game.ColonyGameRules;
import com.apiculture.simulator.domain.game.FloraBloomWindow;
import com.apiculture.simulator.domain.game.GameCalendar;
import com.apiculture.simulator.domain.market.HoneyMarketEngine;
import com.apiculture.simulator.domain.parcel.CropRules;
import com.apiculture.simulator.domain.parcel.CropUnlock;
import com.apiculture.simulator.domain.parcel.HexFlora;
import com.apiculture.simulator.domain.parcel.HexParcel;
import com.apiculture.simulator.presentation.hive.HiveSiteSummaryUi;
import com.apiculture.simulator.domain.game.HiveFeedType;
import com.apiculture.simulator.domain.game.HiveFeedingBonuses;
import com.apiculture.simulator.domain.game.HiveHoneyRules;
import com.apiculture.simulator.domain.game.HiveHoneyStocks;
import com.apiculture.simulator.domain.game.TranshumanceRules;
import com.apiculture.simulator.domain.population.HivePopulationState;
import com.unity3d.player.UnityPlayer;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Puente con la escena 3D (Unity, clase C# Apiario.AndroidBridge).
 * Unity llama a los métodos estáticos por JNI desde su propio hilo; las respuestas
 * vuelven con UnitySendMessage al objeto "ApiarioBridge".
 */
public final class UnityBridge {

    private static final String TAG = "UnityBridge";
    private static final String UNITY_OBJECT = "ApiarioBridge";

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();

    private static Context appContext;
    private static String ownerId = "";

    static String owner() {
        return ownerId;
    }

    static Context app() {
        return appContext;
    }
    private static String apiaryId = "";
    /** Id del apiario en el terreno (el de las colmenas), no el compuesto hex/sitio de la sesión 3D. */
    private static String farmSiteId = "";
    private static String apiaryName = "";
    private static String floraType = "";
    private static String climate = "";
    private static String sky = "";
    private static List<String> hiveIds = new ArrayList<>();
    /** Terreno propio cuyas siembras gestiona el payés; vacío si el apiario no tiene campo. */
    private static String farmHexId = "";
    private static int session;
    private static final String MODE_APIARY = "apiary";
    private static final String MODE_WORKSHOP = "workshop";
    /** Escena que pidió la app la última vez: patio del apiario u obrador. */
    private static String mode = MODE_APIARY;
    /** Almacén cuyo obrador se ve en 3D. */
    private static String workshopHex = "";

    private static final String PREFS = "apiary_3d";
    private static final String KEY_STUNG = "stung_out|";

    private UnityBridge() {
    }

    /** Fija el apiario que abrirá la escena; llamar antes de lanzar {@link Apiary3DActivity}. */
    public static synchronized void prepare(@NonNull Context ctx, @NonNull String owner,
            @NonNull String id, @NonNull String site, @NonNull String name, @NonNull String climateName,
            @NonNull String skyName, @NonNull List<HiveEntity> hives, @Nullable String farmHex) {
        appContext = ctx.getApplicationContext();
        ownerId = owner;
        apiaryId = id;
        farmSiteId = site != null ? site : "";
        farmHexId = farmHex != null ? farmHex : "";
        apiaryName = name;
        climate = climateName;
        sky = skyName;
        List<String> ids = new ArrayList<>();
        String flora = "";
        for (HiveEntity h : hives) {
            if (h != null && h.id != null) {
                ids.add(h.id);
                if (flora.isEmpty() && h.floraType != null) {
                    flora = h.floraType;
                }
            }
        }
        hiveIds = ids;
        floraType = flora;
        mode = MODE_APIARY;
        session++;
    }

    /** Fija el obrador como escena que abrirá {@link Apiary3DActivity}. */
    public static synchronized void prepareWorkshop(@NonNull Context ctx, @NonNull String owner,
            @NonNull String placeName, @Nullable String hexId) {
        appContext = ctx.getApplicationContext();
        ownerId = owner;
        apiaryName = placeName;
        workshopHex = hexId != null ? hexId : "";
        mode = MODE_WORKSHOP;
        session++;
    }

    /** "apiary" o "workshop": Unity lo pide al arrancar y al volver para saber qué escena cargar. */
    @SuppressWarnings("unused")
    public static synchronized String getMode() {
        return mode;
    }

    /** Unity lo pide al cargar el obrador. Lee la copia local, sin red. */
    @SuppressWarnings("unused")
    public static synchronized String getWorkshopJson() {
        try {
            return UnityWorkshopJson.build(appContext, ownerId, workshopHex, apiaryName, session, langCode(), localized());
        } catch (Exception e) {
            Log.e(TAG, "getWorkshopJson", e);
            return "";
        }
    }

    /** El almacenero del 3D pide mercado, comandas o traspasos; contesta a "MarketReply". */
    @SuppressWarnings("unused")
    public static void onMarketQuery(String json) {
        UnityMarket.query(json);
    }

    /** La operaria pregunta: trae la copia del servidor (las recogidas añaden tandas allí) y la manda. */
    @SuppressWarnings("unused")
    public static void onWorkshopRefresh() {
        IO.execute(() -> {
            Context ctx = appContext;
            if (ctx == null) {
                return;
            }
            ApicultureApp game = (ApicultureApp) ctx;
            // Con el 3D delante no corre el panel ni el mapa: aquí se cierran los viajes que ya han
            // llegado, para que el camión vuelva al patio y salgan los avisos de miel y de comandas.
            try {
                TruckLiveTrips.completeDue(ctx);
                HoneyLogistics.completeDue(ctx, game.getEconomyRepository(), game.getMarketRepository());
            } catch (RuntimeException e) {
                Log.w(TAG, "completeDue", e);
            }
            WorkshopStore.refresh(ctx, ownerId, game.getEconomyRepository());
            sendWorkshop();
        });
    }

    /** El jugador elige con la operaria el envase de una tanda. */
    @SuppressWarnings("unused")
    public static void onWorkshopFormat(String batchId, String format) {
        IO.execute(() -> {
            Context ctx = appContext;
            WorkshopRules.Format f = WorkshopRules.Format.parse(format);
            if (ctx == null || f == null) {
                return;
            }
            String error = WorkshopStore.chooseFormat(ctx, ownerId, batchId, f);
            if (error == null) {
                // Capítulo 10, viñeta 2. También vale elegir el envase en el 3D.
                TutorialBus.emit(TutorialEvent.WORKSHOP_FORMAT);
            }
            sendWorkshop();
            send("WorkshopResult", "Format|" + batchId + "|" + (error == null ? "ok" : error));
        });
    }

    /** El jugador reparte con Toni una tanda: "kilo/medio/cuarto" tarros; el resto va a granel. */
    @SuppressWarnings("unused")
    public static void onWorkshopMix(String batchId, String counts) {
        IO.execute(() -> {
            Context ctx = appContext;
            if (ctx == null || counts == null) {
                return;
            }
            String[] p = counts.split("/");
            String error;
            try {
                JarMix mix = new JarMix(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]));
                error = WorkshopStore.chooseMix(ctx, ownerId, batchId, mix);
            } catch (RuntimeException e) {
                error = ctx.getString(R.string.workshop_err_packing);
            }
            if (error == null) {
                // Capítulo 10, viñeta 2. También vale elegir el envase en el 3D.
                TutorialBus.emit(TutorialEvent.WORKSHOP_FORMAT);
            }
            sendWorkshop();
            send("WorkshopResult", "Format|" + batchId + "|" + (error == null ? "ok" : error));
        });
    }

    /** Toni vende o mejora una máquina de este obrador. */
    @SuppressWarnings("unused")
    public static void onWorkshopBuy(String machineKey) {
        IO.execute(() -> {
            Context ctx = appContext;
            if (ctx == null) {
                return;
            }
            String error;
            try {
                WorkshopRules.Machine m = WorkshopRules.Machine.valueOf(machineKey);
                error = WorkshopStore.buyOrUpgrade(ctx, ((ApicultureApp) ctx).getEconomyRepository(), ownerId, workshopHex, m);
                if (error == null && WorkshopStore.get(ctx, ownerId, workshopHex).complete()) {
                    // Capítulo 9. Con todas las máquinas, el obrador está listo (también desde el 3D).
                    TutorialBus.emit(TutorialEvent.WORKSHOP_READY);
                }
            } catch (IllegalArgumentException e) {
                error = ctx.getString(R.string.workshop_err_packing);
            }
            sendWorkshop();
            send("WorkshopResult", "Buy|" + machineKey + "|" + (error == null ? "ok" : error));
        });
    }

    /** La operaria vende toda la cera acumulada. */
    @SuppressWarnings("unused")
    public static void onWorkshopSellWax() {
        IO.execute(() -> {
            Context ctx = appContext;
            if (ctx == null) {
                return;
            }
            String error = WorkshopStore.sellWax(ctx, ((ApicultureApp) ctx).getEconomyRepository(), ownerId, workshopHex);
            sendWorkshop();
            send("WorkshopResult", "Wax||" + (error == null ? "ok" : error));
        });
    }

    /** Unity ha cargado una escena y puede pintar avisos: se vuelve a mandar el pendiente. */
    @SuppressWarnings("unused")
    public static void onReceiptsReady() {
        IO.execute(UnityReceipts::ready);
    }

    /** El jugador ha cerrado en el 3D el aviso de llegada (cosecha o comanda). */
    @SuppressWarnings("unused")
    public static void onReceiptSeen(String kind, String id) {
        IO.execute(() -> UnityReceipts.seen(kind, id));
    }

    /** El vendedor del patio: camión nuevo con base en este obrador. Llega desde la tienda más cercana. */
    @SuppressWarnings("unused")
    public static void onYardBuyTruck(String name) {
        IO.execute(() -> {
            Context ctx = appContext;
            if (ctx == null || workshopHex == null) {
                return;
            }
            String error = FleetStore.buyTruck(ctx, ((ApicultureApp) ctx).getEconomyRepository(),
                    ownerId, workshopHex, name);
            if (error == null) {
                FleetDispatch.deliverNewTruck(ctx, ownerId, workshopHex, Double.NaN, Double.NaN);
                // Capítulo 6. Camión comprado.
                TutorialBus.emit(TutorialEvent.TRUCK_BOUGHT);
            }
            sendWorkshop();
            send("WorkshopResult", "Buy||" + (error == null ? "ok" : error));
        });
    }

    /** El vendedor del patio sube un nivel un camión. */
    @SuppressWarnings("unused")
    public static void onYardUpgrade(String vehicleId) {
        IO.execute(() -> {
            Context ctx = appContext;
            if (ctx == null) {
                return;
            }
            String error = FleetStore.upgradeVehicle(ctx, ((ApicultureApp) ctx).getEconomyRepository(),
                    ownerId, vehicleId);
            sendWorkshop();
            send("WorkshopResult", "Upgrade|" + vehicleId + "|" + (error == null ? "ok" : error));
        });
    }

    private static void sendWorkshop() {
        String json = getWorkshopJson();
        if (!json.isEmpty()) {
            send("LoadWorkshop", json);
        }
    }

    /** Unity lo pide al cargar el patio. Corre en el hilo de Unity, así que puede leer Room. */
    @SuppressWarnings("unused")
    public static synchronized String getApiaryJson() {
        try {
            return buildJson();
        } catch (Exception e) {
            Log.e(TAG, "getApiaryJson", e);
            return "";
        }
    }

    @SuppressWarnings("unused")
    public static void onHiveAction(String hiveId, String action) {
        MAIN.post(() -> runAction(hiveId, action));
    }

    @SuppressWarnings("unused")
    public static void onExitApiary() {
        MAIN.post(Apiary3DActivity::leaveToMap);
    }

    /** Diez picaduras en la visita: se cierra el 3D y el apiario queda vetado hasta mañana. */
    @SuppressWarnings("unused")
    public static void onStungOut() {
        Context ctx = appContext;
        if (ctx != null) {
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putInt(KEY_STUNG + ownerId + "|" + apiaryId, GameCalendar.currentCivilDayKey())
                    .apply();
        }
        MAIN.post(Apiary3DActivity::leaveToMap);
    }

    /** El jugador acepta el trato del payés: se cobra y empieza la siembra. */
    @SuppressWarnings("unused")
    public static void onPlantCrop(String floraKey) {
        MAIN.post(() -> {
            Context ctx = appContext;
            if (ctx == null || farmHexId.isEmpty()) {
                return;
            }
            ((ApicultureApp) ctx).getHiveRepository().plantAdditionalFloraAsync(farmHexId, ownerId, floraKey,
                    farmSiteId, msg -> farmResult("Plant", floraKey, msg));
        });
    }

    /** Pago al payés por podar y abonar un frutal. */
    @SuppressWarnings("unused")
    public static void onMaintainCrop(String floraKey) {
        MAIN.post(() -> {
            Context ctx = appContext;
            if (ctx == null || farmHexId.isEmpty()) {
                return;
            }
            ((ApicultureApp) ctx).getHiveRepository().maintainTreeAsync(farmHexId, ownerId, floraKey, farmSiteId,
                    msg -> farmResult("Maintain", floraKey, msg));
        });
    }

    /** Pep compra el fruto de un frutal de este apiario. */
    @SuppressWarnings("unused")
    public static void onSellFruit(String floraKey) {
        MAIN.post(() -> {
            Context ctx = appContext;
            if (ctx == null || farmHexId.isEmpty()) {
                return;
            }
            ((ApicultureApp) ctx).getHiveRepository().sellFruitAsync(farmHexId, ownerId, floraKey, farmSiteId,
                    msg -> farmResult("Sell", floraKey, msg));
        });
    }

    /** "Accion|clave|ok" o "Accion|clave|mensaje de error"; antes manda el apiario actualizado. */
    private static void farmResult(String action, String floraKey, @Nullable String msg) {
        IO.execute(() -> {
            String json = getApiaryJson();
            if (!json.isEmpty()) {
                send("LoadApiary", json);
            }
            send("FarmResult", action + "|" + floraKey + "|" + (msg == null ? "ok" : msg));
        });
    }

    /** True si hoy ya echaron al jugador de este apiario por las picaduras. */
    public static boolean isStungOutToday(@NonNull Context ctx, @NonNull String owner, @NonNull String id) {
        int day = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getInt(KEY_STUNG + owner + "|" + id, Integer.MIN_VALUE);
        return day == GameCalendar.currentCivilDayKey();
    }

    private static void runAction(String hiveId, String action) {
        Context ctx = appContext;
        if (ctx == null) {
            return;
        }
        HiveRepository repo = ((ApicultureApp) ctx).getHiveRepository();
        switch (action) {
            case "Treat":
                repo.treatVarroa(hiveId, ownerId, careResult(action, R.string.hive_treat_ok,
                        R.string.hive_care_no_treat));
                break;
            case "Feed":
                repo.applyHiveFeeding(hiveId, ownerId, HiveFeedType.DAYS_7,
                        careResult(action, R.string.hive_feed_ok, R.string.hive_care_no_feed));
                break;
            default:
                send("ActionResult", action + "|" + ctx.getString(R.string.apiary_3d_use_hive_card));
                break;
        }
    }

    @NonNull
    private static Consumer<String> careResult(String action, int okRes, int noStockRes) {
        return msg -> {
            Context ctx = appContext;
            String text;
            if (msg == null) {
                text = ctx.getString(okRes);
            } else if ("SHOP_TREAT".equals(msg) || "SHOP_FEED".equals(msg)) {
                text = ctx.getString(noStockRes);
            } else {
                text = msg;
            }
            if (msg == null) {
                refreshUnity();
            }
            send("ActionResult", action + "|" + text);
        };
    }

    /** Unity sigue cargado de otra visita: que vuelva a pedir el apiario y empiece en el patio. */
    static void reopen() {
        send("Reopen", "");
    }

    private static void refreshUnity() {
        IO.execute(() -> {
            String json = getApiaryJson();
            if (!json.isEmpty()) {
                send("LoadApiary", json);
            }
        });
    }

    static void send(String method, String payload) {
        try {
            UnityPlayer.UnitySendMessage(UNITY_OBJECT, method, payload);
        } catch (Throwable t) {
            Log.w(TAG, "UnitySendMessage " + method, t);
        }
    }

    @NonNull
    private static String buildJson() throws JSONException {
        JSONObject root = new JSONObject();
        root.put("session", session);
        root.put("id", apiaryId);
        root.put("name", apiaryName);
        root.put("floraType", floraType);
        root.put("lang", langCode());
        if (appContext != null && !floraType.isEmpty()) {
            root.put("floraLabel", HiveSiteSummaryUi.floraLabel(localized(), floraType));
        }
        root.put("climate", climate);
        root.put("sky", sky);
        JSONArray arr = new JSONArray();
        if (appContext != null) {
            int today = GameCalendar.currentCivilDayKey();
            AppDatabase db = AppDatabase.getInstance(appContext);
            for (String id : hiveIds) {
                HiveEntity h = db.hiveDao().getHiveByIdSync(id);
                if (h != null) {
                    arr.put(hiveJson(h, today));
                }
            }
        }
        root.put("hives", arr);
        if (appContext != null && !farmHexId.isEmpty()) {
            root.put("farm", farmJson());
        }
        return root.toString();
    }

    /** El cultivo vale si es de este apiario, tanto con el id del terreno como con el de la sesión 3D. */
    private static boolean sameSite(String stored) {
        return stored.equals(farmSiteId) || stored.equals(apiaryId);
    }

    /** Cultivos del terreno y lo que el payés puede sembrar, con sus fechas para dibujar cada estadio. */
    @NonNull
    private static JSONObject farmJson() throws JSONException {
        ApicultureApp app = (ApicultureApp) appContext;
        HexParcel parcel = IberiaHexOverlayStore.findById(appContext, farmHexId);
        LocalDate today = LocalDate.now(GameCalendar.userTimeZone());
        long now = System.currentTimeMillis();
        int level = app.getPlayerProgressRepository().getLevel(ownerId);
        JSONObject farm = new JSONObject();
        farm.put("enabled", true);
        farm.put("nowMs", now);
        farm.put("doy", today.getDayOfYear());
        farm.put("southern", parcel != null && HexFlora.isSouthernParcel(parcel));
        farm.put("level", level);
        farm.put("balance", app.getEconomyRepository().getBalance());

        JSONArray crops = new JSONArray();
        List<String> occupied = new ArrayList<>();
        for (HexParcelFloraEntity e : app.getHexFloraRepository().listEntriesForHexBlocking(farmHexId)) {
            String key = HoneyMarketEngine.canonicalFloraKey(e.floraKey);
            if (!HexFlora.isPlantation(key)) {
                continue;
            }
            if (e.siteId != null && !e.siteId.isEmpty() && !sameSite(e.siteId)) {
                continue;
            }
            occupied.add(key);
            JSONObject c = cropBase(key, parcel);
            c.put("plantedAt", e.plantedAtEpochMs);
            c.put("readyAt", e.readyAtEpochMs);
            if (CropRules.isTree(key)) {
                long days = CropRules.daysUntilMaintenance(e.plantedAtEpochMs, e.lastMaintainedYear, today);
                c.put("maintDays", days);
                c.put("canMaintain", e.readyAtEpochMs <= now
                        && CropRules.canMaintainNow(e.plantedAtEpochMs, e.lastMaintainedYear, today));
                boolean fruit = e.readyAtEpochMs <= now && CropRules.inFruit(key, parcel, today)
                        && e.fruitSoldYear != today.getYear();
                c.put("canSell", fruit);
                c.put("sellPrice", CropRules.fruitSaleEuros(key));
            }
            crops.put(c);
        }
        farm.put("crops", crops);

        JSONArray offers = new JSONArray();
        for (String f : HexFlora.plantationPoolForParcel(parcel)) {
            String key = HoneyMarketEngine.canonicalFloraKey(f);
            if (occupied.contains(key)) {
                continue;
            }
            JSONObject o = cropBase(key, parcel);
            o.put("cost", CropRules.plantCostEuros(key));
            o.put("days", CropRules.growDays(key));
            o.put("unlocked", CropUnlock.isUnlocked(key, level));
            o.put("level", CropUnlock.requiredLevel(key));
            o.put("bloomText", FloraBloomWindow.formatForParcel(key, parcel, gameLocale()));
            offers.put(o);
        }
        farm.put("offers", offers);
        return farm;
    }

    @NonNull
    private static JSONObject cropBase(String key, @Nullable HexParcel parcel) throws JSONException {
        JSONObject c = new JSONObject();
        c.put("key", key);
        c.put("label", cropLabel(key));
        c.put("species", speciesCode(key));
        c.put("tree", CropRules.isTree(key));
        c.put("maintFee", CropRules.treeMaintenanceEuros(key));
        JSONArray starts = new JSONArray();
        JSONArray ends = new JSONArray();
        for (FloraBloomWindow.Span s : FloraBloomWindow.spansForParcel(key, parcel)) {
            starts.put(s.startDoy);
            ends.put(s.endDoy);
        }
        c.put("bloomStart", starts);
        c.put("bloomEnd", ends);
        return c;
    }

    /** Nombre de la plantación: el naranjo, no su miel de azahar. */
    @NonNull
    private static String cropLabel(String key) {
        Context ctx = localized();
        if ("Campo de naranjos".equals(HexFlora.canonicalKey(key))) {
            return ctx.getString(R.string.crop_name_naranjos);
        }
        return HiveSiteSummaryUi.floraLabel(ctx, key);
    }

    /** Idioma de la partida; el 3D solo trae textos en estos cinco y si no, castellano. */
    @NonNull
    private static Locale gameLocale() {
        LocaleListCompat app = AppCompatDelegate.getApplicationLocales();
        if (!app.isEmpty() && app.get(0) != null) {
            return app.get(0);
        }
        String tag = appContext != null ? GameLocale.saved(appContext) : "";
        return tag.isEmpty() ? Locale.getDefault() : Locale.forLanguageTag(tag);
    }

    @NonNull
    static String langCode() {
        String lang = gameLocale().getLanguage();
        switch (lang) {
            case "ca":
            case "en":
            case "eu":
            case "gl":
                return lang;
            default:
                return "es";
        }
    }

    /** El contexto de la aplicación no siempre sigue el idioma elegido en la partida. */
    @NonNull
    static Context localized() {
        Configuration cfg = new Configuration(appContext.getResources().getConfiguration());
        cfg.setLocale(gameLocale());
        return appContext.createConfigurationContext(cfg);
    }

    /** Especie que Unity sabe dibujar (modelo y calendario de hojas y fruto). */
    @NonNull
    private static String speciesCode(String key) {
        String k = key.toLowerCase(java.util.Locale.ROOT);
        String[][] map = {
                {"almendr", "almond"}, {"naranj", "orange"}, {"cerez", "cherry"}, {"peral", "pear"},
                {"manzan", "apple"}, {"girasol", "sunflower"}, {"colza", "rapeseed"}, {"lavanda", "lavender"},
                {"mostaza", "mustard"}, {"trébol", "clover"}, {"facelia", "phacelia"}, {"rabaniza", "radish"},
        };
        for (String[] m : map) {
            if (k.contains(m[0])) {
                return m[1];
            }
        }
        return "generic";
    }

    @NonNull
    private static JSONObject hiveJson(@NonNull HiveEntity h, int today) throws JSONException {
        HivePopulationState pop = HivePopulationState.fromHiveEntityOrDefault(h,
                HiveRepository.DEFAULT_BEE_COUNT_PER_HIVE);
        int total = Math.max(1, pop.totalBees());
        int brood = pop.broodEggs() + pop.broodLarvae() + pop.broodPupae();
        JSONObject o = new JSONObject();
        o.put("id", h.id);
        o.put("name", h.name != null ? h.name : "");
        o.put("beeCount", pop.totalBees());
        o.put("health", h.health);
        o.put("honeyKg", HiveHoneyStocks.total(HiveHoneyStocks.parse(h.honeyStocksJson)));
        o.put("honeyCapKg", HiveHoneyRules.maxHoneyKgForSuperCount(h.superCount));
        o.put("reserveKg", HiveHoneyRules.HARVEST_ALL_LEAVE_KG);
        o.put("superCount", h.superCount);
        o.put("varroaPct", h.varroaPct);
        o.put("queenAgeDays", h.queenAgeDays);
        o.put("needsQueen", pop.needsQueenIntroduction());
        o.put("canSplit", pop.workersAdult >= ColonyGameRules.MIN_BEES_TO_SPLIT);
        o.put("feedDaysLeft", HiveFeedingBonuses.feedingDaysRemaining(h, today));
        o.put("treatmentDaysLeft", Math.max(0, h.varroaTreatmentDaysRemaining));
        o.put("inTransit", TranshumanceRules.isInTransit(h, today));
        o.put("broodRatio", clamp(brood * 1.5 / total, 0.05, 0.7));
        o.put("pollenRatio", 0.12);
        return o;
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
