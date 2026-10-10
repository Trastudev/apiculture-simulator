package com.apiculture.simulator.data.repository;



import android.content.Context;

import android.content.SharedPreferences;

import android.os.Handler;

import android.os.Looper;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;



import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;


import com.apiculture.simulator.ApicultureApp;
import com.apiculture.simulator.data.local.AppDatabase;
import com.apiculture.simulator.data.local.dao.GameProductionStateDao;

import com.apiculture.simulator.data.local.dao.HiveDailyYieldDao;

import com.apiculture.simulator.data.local.dao.HiveDao;

import com.apiculture.simulator.data.local.entity.GameProductionStateEntity;

import com.apiculture.simulator.data.local.entity.HiveDailyYieldEntity;

import com.apiculture.simulator.data.local.entity.HiveEntity;
import com.apiculture.simulator.data.local.entity.HexParcelOwnershipEntity;
import com.apiculture.simulator.data.local.entity.TruckTripEntity;
import com.apiculture.simulator.data.local.entity.HexParcelFloraEntity;

import com.apiculture.simulator.data.remote.OpenMeteoElevation;

import com.apiculture.simulator.domain.market.HoneyMarketEngine;
import com.apiculture.simulator.domain.parcel.CropTickResult;
import com.apiculture.simulator.domain.parcel.FloraPlantingProgressRow;
import com.apiculture.simulator.domain.parcel.HexApiary;
import com.apiculture.simulator.domain.parcel.HexFlora;
import com.apiculture.simulator.domain.parcel.HexGeometry;
import com.apiculture.simulator.domain.parcel.HexParcel;
import com.apiculture.simulator.domain.parcel.HexParcelGameRules;
import com.apiculture.simulator.domain.parcel.HexParcelPointInPolygon;
import com.apiculture.simulator.domain.parcel.HexParcelRandomPoint;
import com.apiculture.simulator.domain.parcel.HexParcelResolve;
import com.apiculture.simulator.domain.parcel.LocalTangentPlane;
import com.apiculture.simulator.domain.game.HiveProductionEligibility;
import com.apiculture.simulator.domain.game.XpAwards;
import com.apiculture.simulator.domain.game.TranshumanceRules;
import com.apiculture.simulator.domain.parcel.WarehouseRules;
import com.apiculture.simulator.domain.game.GameClock;
import com.apiculture.simulator.domain.game.DailySkyCondition;
import com.apiculture.simulator.domain.game.DailyWeather;
import com.apiculture.simulator.domain.game.GameBalanceConfig;
import com.apiculture.simulator.domain.game.GameCalendar;
import com.apiculture.simulator.domain.game.ColonyGameRules;
import com.apiculture.simulator.domain.game.HiveCareRules;
import com.apiculture.simulator.domain.game.HiveDailyBiology;
import com.apiculture.simulator.domain.game.HiveFeedType;
import com.apiculture.simulator.domain.game.HiveHoneyRules;
import com.apiculture.simulator.domain.game.HiveHoneyStocks;
import com.apiculture.simulator.domain.game.Season;
import com.apiculture.simulator.domain.game.Hemispheres;
import com.apiculture.simulator.domain.game.HexNectarPool;
import com.apiculture.simulator.domain.game.NpcContractCatalog;
import com.apiculture.simulator.domain.game.HexForageSnapshot;
import com.apiculture.simulator.domain.game.HexFloraSaturation;
import com.apiculture.simulator.domain.game.HexNectarRules;
import com.apiculture.simulator.domain.game.GlobalEventEffects;
import com.apiculture.simulator.domain.market.HoneyMarketEngine;
import com.apiculture.simulator.domain.health.HiveDailyHealthSimulator;
import com.apiculture.simulator.domain.population.HivePopulationSimulator;
import com.apiculture.simulator.domain.population.HivePopulationState;
import com.apiculture.simulator.domain.population.QueenMode;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import com.google.firebase.firestore.QueryDocumentSnapshot;

import com.google.firebase.firestore.QuerySnapshot;

import com.google.firebase.firestore.SetOptions;

import com.apiculture.simulator.domain.map.PlayableMapRegion;

import java.time.LocalDate;

import java.time.LocalDateTime;

import java.time.ZoneId;

import java.time.format.DateTimeFormatter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
    import java.util.LinkedHashMap;
    import java.util.LinkedHashSet;
    import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import java.util.UUID;

import java.util.concurrent.ExecutionException;

import java.util.concurrent.ExecutorService;

import java.util.concurrent.Executors;

import java.util.function.Consumer;



public class HiveRepository {



    private static final String TAG = "HiveRepository";

    private static final double QUEEN_REPLACE_COST_EUR = HiveCareRules.QUEEN_EUR;

    /** Calidad genética inicial de reina (75–90 %). */
    private static int randomInitialQueenGeneticQuality() {
        return HiveCareRules.randomStarterQueenQuality();
    }



    private static final String MIGRATION_PREFS = "apiculture_migrations";

    private static final String KEY_BROOD_PIPELINE_RESEED = "brood_pipeline_reseed_v1";



    /** Si falta en Firestore o es 0, Room/Firestore deserializan 0; unificamos con el valor de juego. */

    public static final int DEFAULT_BEE_COUNT_PER_HIVE = 25_000;

    /** Total de abejas por colmena tras «Reiniciar juego» (reino + cría = este total). */

    public static final int STARTER_GAME_TOTAL_BEES = 25_000;

    /**
     * Primera colmena al reiniciar: total colonia (adultos + cría). Las obreras adultas van al tope
     * de {@link ColonyGameRules#MAX_ADULT_WORKERS_PER_HIVE} (valor de {@code game_balance.json}).
     */
    public static final int STARTER_GAME_FIRST_HIVE_TOTAL_BEES = 95_000;

    public static int starterFirstHiveAdultWorkers() {
        return ColonyGameRules.MAX_ADULT_WORKERS_PER_HIVE;
    }



    private final HiveDao hiveDao;

    private final HiveDailyYieldDao hiveDailyYieldDao;

    private final GameProductionStateDao gameProductionStateDao;

    private final WeatherRepository weatherRepository;

    private final HexParcelRepository hexParcelRepository;

    private final HexFloraRepository hexFloraRepository;

    private final EconomyRepository economyRepository;

    private final MarketRepository marketRepository;

    @Nullable
    private PlayerProgressRepository playerProgressRepository;

    @Nullable
    private PollinationContractRepository pollinationContractRepository;

    private final FirebaseFirestore firestore;

    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private ListenerRegistration cloudListener;
    @Nullable
    private String cloudSyncOwnerId;

    private final Context appContext;



    /**
     * Obtiene un mensaje legible para el usuario (Toast/diálogos) a partir de fallos en {@link Tasks#await}.
     * {@link ExecutionException} suele envolver la causa real (p. ej. {@link FirebaseFirestoreException}).
     */

    private static String formatThrowableForUser(Throwable t) {

        if (t == null) {

            return "Error al reiniciar.";

        }

        Throwable cur = t;

        for (int i = 0; i < 6 && cur != null; i++) {

            if (cur instanceof FirebaseFirestoreException) {

                FirebaseFirestoreException fe = (FirebaseFirestoreException) cur;

                String code = fe.getCode() != null ? fe.getCode().name() : "";

                String m = fe.getMessage();

                if (fe.getCode() == FirebaseFirestoreException.Code.PERMISSION_DENIED) {

                    return "Sin permiso en Firestore (" + code + "). Comprueba que las reglas estén publicadas y que seas el dueño de las colmenas.";

                }

                if (m != null && !m.isEmpty()) {

                    return m;

                }

                return "Firestore: " + code;

            }

            if (cur.getMessage() != null && !cur.getMessage().isEmpty()

                    && !(cur instanceof ExecutionException)) {

                return cur.getMessage();

            }

            cur = cur.getCause();

        }

        String last = t.getMessage();

        return (last != null && !last.isEmpty()) ? last : "Error al reiniciar.";

    }



    public HiveRepository(

            HiveDao hiveDao,

            HiveDailyYieldDao hiveDailyYieldDao,

            GameProductionStateDao gameProductionStateDao,

            WeatherRepository weatherRepository,

            HexParcelRepository hexParcelRepository,

            HexFloraRepository hexFloraRepository,

            EconomyRepository economyRepository,

            MarketRepository marketRepository,

            Context appContext) {

        this.hiveDao = hiveDao;

        this.hiveDailyYieldDao = hiveDailyYieldDao;

        this.gameProductionStateDao = gameProductionStateDao;

        this.weatherRepository = weatherRepository;

        this.hexParcelRepository = hexParcelRepository;

        this.hexFloraRepository = hexFloraRepository;

        this.economyRepository = economyRepository;

        this.marketRepository = marketRepository;

        this.appContext = appContext.getApplicationContext();
        firestore = null;
    }

    private void upsertCloud(@NonNull HiveEntity hive) {
        hiveDao.upsert(hive);
        publishHiveToServer(hive);
    }

    public void publishHiveToServer(@Nullable HiveEntity hive) {
        if (hive == null || hive.id == null || hive.id.isEmpty() || !GameServer.enabled()) {
            return;
        }
        try {
            GameServer.pushHive(hiveJson(hive));
        } catch (Exception ignored) {
        }
    }

    public void setPlayerProgressRepository(@Nullable PlayerProgressRepository playerProgressRepository) {
        this.playerProgressRepository = playerProgressRepository;
    }

    public void setPollinationContractRepository(@Nullable PollinationContractRepository repo) {
        this.pollinationContractRepository = repo;
    }

    public void grantXp(@Nullable String uid, double amount) {
        if (playerProgressRepository == null || uid == null || uid.isEmpty() || amount <= 1e-12) {
            return;
        }
        playerProgressRepository.addXp(uid, amount);
    }

    /**

     */

    public void runBroodPipelineReseedMigrationIfNeeded() {

        SharedPreferences p = appContext.getSharedPreferences(MIGRATION_PREFS, Context.MODE_PRIVATE);

        if (p.getBoolean(KEY_BROOD_PIPELINE_RESEED, false)) {

            return;

        }

        ioExecutor.execute(() -> {

            try {

                List<HiveEntity> all = hiveDao.getAllHivesSync();

                for (HiveEntity h : all) {

                    if (h == null || h.id == null) {

                        continue;

                    }

                    String jsonBefore = h.populationStateJson;

                    int beesBefore = h.beeCount;

                    ensurePopulationJson(h);

                    normalizeBeeCount(h);

                    if (!Objects.equals(jsonBefore, h.populationStateJson) || beesBefore != h.beeCount) {

                        upsertCloud(h);

                        if (firestore != null) {

                            firestore.collection("hives").document(h.id).set(h, SetOptions.merge());

                        }

                    }

                }

                p.edit().putBoolean(KEY_BROOD_PIPELINE_RESEED, true).apply();

            } catch (RuntimeException ignored) {

            }

        });

    }



    public LiveData<List<HiveEntity>> getLocalHives(String ownerId) {

        return hiveDao.getHivesByOwner(ownerId);

    }

    /** Lista local actual (mismo criterio que {@link #getLocalHives}) para refrescar UI sin esperar otra emisión de Room. */
    public List<HiveEntity> getLocalHivesSync(@Nullable String ownerId) {
        if (ownerId == null || ownerId.isEmpty()) {
            return new ArrayList<>();
        }
        List<HiveEntity> list = hiveDao.getHivesByOwnerSync(ownerId);
        return list != null ? list : new ArrayList<>();
    }



    public LiveData<List<HiveEntity>> getAllLocalHives() {

        return hiveDao.getAllHives();

    }



    public LiveData<HiveEntity> getHiveById(String hiveId) {

        return hiveDao.getHiveById(hiveId);

    }



    /**
     * Si hay estado poblacional JSON, sincroniza {@code beeCount} con el total simulado.
     * Si no, solo corrige {@code beeCount} ≤ 0 al valor por defecto.
     *
     * @return true si se alteró algo que conviene reenviar a la nube (solo el caso legacy beeCount).
     */
    public static boolean normalizeBeeCount(HiveEntity hive) {

        if (hive == null) {

            return false;

        }

        ensurePopulationJson(hive);

        if (hive.populationStateJson != null && !hive.populationStateJson.trim().isEmpty()) {

            HivePopulationState s = HivePopulationState.fromJson(hive.populationStateJson);

            if (s != null) {

                s.clampNonNegative();

                hive.beeCount = s.totalBees();

                hive.populationStateJson = s.toJson();

                return false;

            }

        }

        if (hive.beeCount <= 0) {

            hive.beeCount = DEFAULT_BEE_COUNT_PER_HIVE;

            return true;

        }

        return false;

    }

    private boolean applyVelutinaIfNeeded(HiveEntity h) {
        GlobalEventEffects.State st = GlobalEventEffects.get();
        if (!st.velutinaActive || st.velutinaLossPercent <= 0 || h == null || h.id == null) {
            return false;
        }
        if (!(appContext instanceof ApicultureApp)) {
            return false;
        }
        GlobalEventRepository repo =
                ((ApicultureApp) appContext).getGlobalEventRepository();
        if (repo == null || repo.alreadyHitByVelutina(h.id, st.velutinaInstanceId)) {
            return false;
        }
        String climateKey = Hemispheres.isSouthern(h.lat)
                ? HexNectarRules.southernZoneForHive(h).name()
                : HexNectarRules.zoneForHive(h).name();
        if (!st.velutinaClimateKeys.contains(climateKey)) {
            return false;
        }
        ensurePopulationJson(h);
        HivePopulationState pop = HivePopulationState.fromJson(h.populationStateJson);
        if (pop == null) {
            return false;
        }
        pop.applyAdultLossPercent(st.velutinaLossPercent);
        h.populationStateJson = pop.toJson();
        h.beeCount = pop.totalBees();
        repo.markVelutinaHit(h.id, st.velutinaInstanceId);
        return true;
    }



    public void saveHive(HiveEntity hive) {

        if (hive == null) {

            return;

        }

        if (hive.populationStateJson == null || hive.populationStateJson.trim().isEmpty()) {

            int n = hive.beeCount > 0 ? hive.beeCount : DEFAULT_BEE_COUNT_PER_HIVE;

            if (hive.id != null && !hive.id.isEmpty()) {

                hive.populationStateJson = HivePopulationState

                        .fromInitialColonyWithRandomBroodPipeline(n, hive.id).toJson();

            } else {

                hive.populationStateJson = HivePopulationState.fromLegacyBeeCount(n).toJson();

            }

        }

        normalizeBeeCount(hive);

        ioExecutor.execute(() -> {

            if (hive.elevationMeters < 0) {

                hive.elevationMeters = OpenMeteoElevation.resolveMetersPersistedBlocking(hive.lat, hive.lng);

            }

            ensureHiveSiteId(hive);

            upsertCloud(hive);

            if (firestore != null) {

                try {

                    Tasks.await(firestore.collection("hives").document(hive.id).set(hive));

                } catch (Exception e) {

                    Log.w(TAG, "Firestore saveHive", e);

                }

            }

        });

    }

    /**
     * Colmena de prueba en Madrid: flora del hex que contiene el punto (persistida por {@link HexFloraRepository}).
     */
    public void enqueueCreateStarterHive(String ownerId) {

        if (ownerId == null || ownerId.isEmpty()) {

            return;

        }

        ioExecutor.execute(() -> {

            try {

                HiveEntity hive = new HiveEntity();

                hive.id = UUID.randomUUID().toString();

                hive.ownerId = ownerId;

                hive.name = "Colmena " + (System.currentTimeMillis() % 1000);

                hive.health = 90;

                hive.beeCount = DEFAULT_BEE_COUNT_PER_HIVE;

                hive.reserves = 60;

                hive.queenAgeDays = 0;

                hive.queenGeneticQuality = randomInitialQueenGeneticQuality();

                hive.lat = 40.4168;

                hive.lng = -3.7038;

                List<HexParcel> parcels = IberiaHexOverlayStore.getParcels(appContext);

                HexParcel hex = HexParcelResolve.findContaining(parcels, hive.lat, hive.lng);

                if (hex != null) {

                    hive.hexId = hex.id;

                    long nowMs = System.currentTimeMillis();
                    List<String> ready = hexFloraRepository.listReadyFloraKeysBlocking(hex.id, nowMs);
                    hive.floraType = !ready.isEmpty() ? ready.get(0) : "Mil flores";

                } else {

                    hive.floraType = HexFlora.MIL_FLORES;

                }

                hive.superCount = 0;

                hive.honeyProduction = HiveHoneyRules.STARTER_HIVE_STOCK_KG;
                HiveHoneyStocks.seedStarter(hive);

                hive.varroaPct = 2.0;

                hive.varroaTreatmentDaysRemaining = 0;

                hive.varroaReboundDaysRemaining = 0;

                hive.lastHealthSimDayKey = 0;

                stampFirstProductionDay(hive);

                saveHiveBlocking(hive);

            } catch (Exception e) {

                Log.e(TAG, "enqueueCreateStarterHive", e);

            }

        });

    }

    /**
     * Actualiza solo el nombre leyendo la colmena actual en BD (evita pisar datos con una entidad obsoleta en UI).
     */
    public void updateHiveName(String hiveId, String newName) {

        if (hiveId == null || newName == null) {

            return;

        }

        String trimmed = newName.trim();

        if (trimmed.isEmpty()) {

            return;

        }

        if (trimmed.length() > 120) {

            trimmed = trimmed.substring(0, 120);

        }

        final String nameToSet = trimmed;

        ioExecutor.execute(() -> {

            HiveEntity h = hiveDao.getHiveByIdSync(hiveId);

            if (h == null) {

                return;

            }

            h.name = nameToSet;

            normalizeBeeCount(h);

            upsertCloud(h);

            if (firestore != null) {

                firestore.collection("hives").document(hiveId).set(h, SetOptions.merge());

            }

        });

    }

    /**
     * Reina comercial/marcada introducida: vuelve la puesta y el estado reproductivo a normal.
     */
    public void replacePurchasedQueen(HiveEntity hive) {

        if (hive == null || hive.id == null) {

            return;

        }

        prepareCommercialQueenReplacement(hive);

        saveHive(hive);

    }

    private void prepareCommercialQueenReplacement(HiveEntity hive) {

        ensurePopulationJson(hive);

        HivePopulationState s = HivePopulationState.fromJson(hive.populationStateJson);

        if (s == null) {

            int n = hive.beeCount > 0 ? hive.beeCount : DEFAULT_BEE_COUNT_PER_HIVE;

            if (hive.id != null && !hive.id.isEmpty()) {

                s = HivePopulationState.fromInitialColonyWithRandomBroodPipeline(n, hive.id);

            } else {

                s = HivePopulationState.fromLegacyBeeCount(n);

            }

        }

        s.queenMode = QueenMode.LAYING;

        s.replaceWindowDay = 0;

        s.queenPipelineDay = 0;

        s.daysWithoutEggLaying = 0;

        hive.populationStateJson = s.toJson();

        hive.beeCount = s.totalBees();

    }

    /**
     * Cambio de reina: gasta 1 reina del inventario (calidad ya fijada al comprarla).
     */
    public void replaceQueenFromInventory(String hiveId, String ownerId, int queenIndex, Consumer<String> onMain) {
        if (onMain == null) {
            return;
        }
        ioExecutor.execute(() -> {
            try {
                HiveEntity h = hiveDao.getHiveByIdSync(hiveId);
                if (h == null) {
                    mainHandler.post(() -> onMain.accept("Colmena no encontrada."));
                    return;
                }
                if (ownerId == null || !ownerId.equals(h.ownerId)) {
                    mainHandler.post(() -> onMain.accept("Esta colmena no es tuya."));
                    return;
                }
                Integer quality = EventInventoryStore.tryConsumeQueenAt(appContext, queenIndex);
                if (quality == null) {
                    mainHandler.post(() -> onMain.accept("SHOP_QUEEN"));
                    return;
                }
                h.queenGeneticQuality = quality;
                prepareCommercialQueenReplacement(h);
                try {
                    saveHiveBlocking(h);
                } catch (Exception e) {
                    EventInventoryStore.restoreQueen(appContext, quality);
                    throw e;
                }
                EventInventoryStore.persistCloud(firestore, ownerId, appContext);
                grantXp(ownerId, XpAwards.REPLACE_QUEEN);
                mainHandler.post(() -> onMain.accept(null));
            } catch (Exception e) {
                Log.e(TAG, "replaceQueenFromInventory", e);
                mainHandler.post(() -> onMain.accept(formatThrowableForUser(e)));
            }
        });
    }

    /** Alimentación de pago: reduce el consumo de miel 1 o 7 días. */
    public void applyHiveFeeding(String hiveId, String ownerId, HiveFeedType type, Consumer<String> onMain) {
        if (onMain == null) {
            return;
        }
        ioExecutor.execute(() -> {
            try {
                if (type == null) {
                    mainHandler.post(() -> onMain.accept("Tipo de alimento no válido."));
                    return;
                }
                HiveEntity h = hiveDao.getHiveByIdSync(hiveId);
                if (h == null) {
                    mainHandler.post(() -> onMain.accept("Colmena no encontrada."));
                    return;
                }
                if (ownerId == null || !ownerId.equals(h.ownerId)) {
                    mainHandler.post(() -> onMain.accept("Esta colmena no es tuya."));
                    return;
                }
                int feedDays = HiveCareRules.FEED_DAYS;
                if (!EventInventoryStore.tryConsumeFeed(appContext, 1)) {
                    mainHandler.post(() -> onMain.accept("SHOP_FEED"));
                    return;
                }
                if (GameServer.enabled()) {
                    org.json.JSONObject request = new org.json.JSONObject();
                    request.put("hiveId", hiveId);
                    request.put("durationDays", feedDays);
                    String raw = GameServer.performAction(ownerId, "feed-hive", request);
                    if (raw == null) {
                        EventInventoryStore.restoreFeed(appContext, 1);
                        mainHandler.post(() -> onMain.accept(
                                "No hay conexión con el servidor. No se puede realizar esta acción."));
                        return;
                    }
                    org.json.JSONObject response = new org.json.JSONObject(raw);
                    org.json.JSONObject remote = response.optJSONObject("hive");
                    if (remote != null) {
                        h.feedHoneyBonusMultiplier = remote.optDouble(
                                "feedHoneyBonusMultiplier", h.feedHoneyBonusMultiplier);
                        h.feedHoneyBonusEndDayKeyExclusive = remote.optInt(
                                "feedHoneyBonusEndDayKeyExclusive", h.feedHoneyBonusEndDayKeyExclusive);
                        h.feedBroodBonusMultiplier = remote.optDouble(
                                "feedBroodBonusMultiplier", 1.0);
                        h.feedBroodBonusEndDayKeyExclusive = remote.optInt(
                                "feedBroodBonusEndDayKeyExclusive", 0);
                        saveHiveBlocking(h);
                        EventInventoryStore.persistCloud(firestore, ownerId, appContext);
                        mainHandler.post(() -> onMain.accept(null));
                        return;
                    }
                }
                int today = GameCalendar.toDayKey(LocalDate.now(GameCalendar.userTimeZone()));
                int start = Math.max(today, h.feedHoneyBonusEndDayKeyExclusive);
                h.feedHoneyBonusMultiplier = HiveCareRules.FEED_CONSUMPTION_MULTIPLIER;
                h.feedHoneyBonusEndDayKeyExclusive = start + feedDays;
                h.feedBroodBonusMultiplier = 1.0;
                h.feedBroodBonusEndDayKeyExclusive = 0;
                try {
                    saveHiveBlocking(h);
                } catch (Exception e) {
                    EventInventoryStore.restoreFeed(appContext, 1);
                    throw e;
                }
                EventInventoryStore.persistCloud(firestore, ownerId, appContext);
                mainHandler.post(() -> onMain.accept(null));
            } catch (Exception e) {
                Log.e(TAG, "applyHiveFeeding", e);
                mainHandler.post(() -> onMain.accept(formatThrowableForUser(e)));
            }
        });
    }

    /** Tratamiento antivarroa de pago: 7 días sin crecimiento y con bajada diaria. */
    public void treatVarroa(String hiveId, String ownerId, Consumer<String> onMain) {
        if (onMain == null) {
            return;
        }
        if (GameServer.enabled() && !GameServer.isAvailable()) {
            onMain.accept("No hay conexión con el servidor. No se puede realizar esta acción.");
            return;
        }
        ioExecutor.execute(() -> {
            try {
                HiveEntity h = hiveDao.getHiveByIdSync(hiveId);
                if (h == null) {
                    mainHandler.post(() -> onMain.accept("Colmena no encontrada."));
                    return;
                }
                if (ownerId == null || !ownerId.equals(h.ownerId)) {
                    mainHandler.post(() -> onMain.accept("Esta colmena no es tuya."));
                    return;
                }
                if (!EventInventoryStore.tryConsumeTreatment(appContext)) {
                    mainHandler.post(() -> onMain.accept("SHOP_TREAT"));
                    return;
                }
                if (GameServer.enabled()) {
                    org.json.JSONObject request = new org.json.JSONObject();
                    request.put("hiveId", hiveId);
                    String raw = GameServer.performAction(ownerId, "treat-varroa", request);
                    if (raw == null) {
                        EventInventoryStore.restoreTreatment(appContext);
                        mainHandler.post(() -> onMain.accept(
                                "No hay conexión con el servidor. No se puede realizar esta acción."));
                        return;
                    }
                    org.json.JSONObject response = new org.json.JSONObject(raw);
                    org.json.JSONObject remote = response.optJSONObject("hive");
                    if (remote != null) {
                        h.varroaTreatmentDaysRemaining = remote.optInt(
                                "varroaTreatmentDaysRemaining", HiveCareRules.TREAT_DAYS);
                        h.varroaReboundDaysRemaining = remote.optInt(
                                "varroaReboundDaysRemaining", 0);
                        saveHiveBlocking(h);
                        EventInventoryStore.persistCloud(firestore, ownerId, appContext);
                        mainHandler.post(() -> onMain.accept(null));
                        return;
                    }
                }
                h.varroaTreatmentDaysRemaining = Math.max(0, h.varroaTreatmentDaysRemaining)
                        + HiveCareRules.TREAT_DAYS;
                h.varroaReboundDaysRemaining = 0;
                try {
                    saveHiveBlocking(h);
                } catch (Exception e) {
                    EventInventoryStore.restoreTreatment(appContext);
                    throw e;
                }
                EventInventoryStore.persistCloud(firestore, ownerId, appContext);
                mainHandler.post(() -> onMain.accept(null));
            } catch (Exception e) {
                Log.e(TAG, "treatVarroa", e);
                mainHandler.post(() -> onMain.accept(formatThrowableForUser(e)));
            }
        });
    }

    /** Simulación: muerte de la reina (ventana de 3–4 días para celdilla real). */
    public void markQueenDead(String hiveId) {

        if (hiveId == null) {

            return;

        }

        ioExecutor.execute(() -> {

            HiveEntity h = hiveDao.getHiveByIdSync(hiveId);

            if (h == null) {

                return;

            }

            ensurePopulationJson(h);

            HivePopulationState s = HivePopulationState.fromJson(h.populationStateJson);

            if (s == null) {

                return;

            }

            HivePopulationSimulator.markQueenDead(s);

            h.populationStateJson = s.toJson();

            h.beeCount = s.totalBees();

            upsertCloud(h);

            if (firestore != null) {

                firestore.collection("hives").document(h.id).set(h, SetOptions.merge());

            }

        });

    }

    private static void ensurePopulationJson(HiveEntity h) {

        if (h == null) {

            return;

        }

        if (h.populationStateJson == null || h.populationStateJson.trim().isEmpty()) {

            int n = h.beeCount > 0 ? h.beeCount : DEFAULT_BEE_COUNT_PER_HIVE;

            if (h.id != null && !h.id.isEmpty()) {

                h.populationStateJson = HivePopulationState

                        .fromInitialColonyWithRandomBroodPipeline(n, h.id).toJson();

            } else {

                h.populationStateJson = HivePopulationState.fromLegacyBeeCount(n).toJson();

            }

            return;

        }

        HivePopulationState existing = HivePopulationState.fromJson(h.populationStateJson);

        if (existing != null && HivePopulationState.isLegacyBroodPipelineLayout(existing)) {

            int total = Math.max(500, Math.max(existing.totalBees(),

                    h.beeCount > 0 ? h.beeCount : DEFAULT_BEE_COUNT_PER_HIVE));

            HivePopulationState fresh = (h.id != null && !h.id.isEmpty())

                    ? HivePopulationState.fromInitialColonyWithRandomBroodPipeline(total, h.id)

                    : HivePopulationState.fromLegacyBeeCount(total);

            fresh.version = existing.version;

            fresh.lastPopulationDayKey = existing.lastPopulationDayKey;

            fresh.lastTrend = existing.lastTrend;

            fresh.queenMode = existing.queenMode;

            fresh.replaceWindowDay = existing.replaceWindowDay;

            fresh.queenPipelineDay = existing.queenPipelineDay;

            fresh.daysWithoutEggLaying = existing.daysWithoutEggLaying;

            h.populationStateJson = fresh.toJson();

            h.beeCount = fresh.totalBees();

        }

    }



    public void sellHive(@NonNull HiveEntity hive, @NonNull Consumer<String> onMainMessage) {
        if (GameServer.enabled() && !GameServer.isAvailable()) {
            onMainMessage.accept("No hay conexión con el servidor. No se puede realizar esta acción.");
            return;
        }
        ioExecutor.execute(() -> {
            try {
                if (GameServer.enabled() && !GameServer.deleteHive(hive.id)) {
                    mainHandler.post(() -> onMainMessage.accept(
                            "No se ha podido vender la colmena. Sigue en el apiario."));
                    return;
                }
                hiveDailyYieldDao.deleteAllForHive(hive.id);
                hiveDao.deleteById(hive.id);
                double val = purchasePriceEurosForSuperCount(hive.superCount);
                double sellPrice = 0.5 * val;
                String hiveLabel = hive.name == null || hive.name.trim().isEmpty()
                        ? "colmena" : hive.name.trim();
                economyRepository.addToBalance(sellPrice, "Venta de la colmena " + hiveLabel);
                mainHandler.post(() -> onMainMessage.accept(null));
            } catch (Exception e) {
                mainHandler.post(() -> onMainMessage.accept(formatThrowableForUser(e)));
            }
        });
    }

    public void deleteHive(String hiveId) {

        ioExecutor.execute(() -> {
            if (GameServer.enabled() && !GameServer.deleteHive(hiveId)) {
                return;
            }
            hiveDao.deleteById(hiveId);
        });

    }



    /**

     * Partida en blanco: borra colmenas, terrenos, contratos, viajes y comandas del dueño

     * (local + nube). Deja 45.000 beecoins y sin terrenos ni colmenas. Detiene la escucha

     * en tiempo real hasta que la UI vuelva a llamar a {@link #startRealtimeCloudSync(String)}.

     *

     * @param onMainMessage {@code null} si todo fue bien; en caso contrario texto de error (hilo principal).

     */

    public void resetGameToStarterState(String ownerId, Consumer<String> onMainMessage) {

        if (ownerId == null || ownerId.isEmpty()) {

            mainHandler.post(() -> onMainMessage.accept("Sesión no válida."));

            return;

        }

        ioExecutor.execute(() -> {

            try {

                stopRealtimeCloudSync();

                hexParcelRepository.stopRealtimeCloudSync();

                if (GameServer.enabled()) {
                    String wipeError = GameServer.wipeOwner(ownerId);
                    if (wipeError != null) {
                        throw new IllegalStateException(wipeError);
                    }
                }

                economyRepository.applyNewGameEconomyDefaults();
                FleetStore.clear(appContext, ownerId);
                RankingCounters.clear(appContext, ownerId);
                WarehouseHoneyStore.clear(appContext, ownerId);
                WorkshopStore.clear(appContext, ownerId);

                List<HiveEntity> old = hiveDao.getHivesByOwnerSync(ownerId);

                for (HiveEntity h : old) {

                    if (h == null || h.id == null) {

                        continue;

                    }

                    hiveDailyYieldDao.deleteAllForHive(h.id);

                    deleteFirestoreDailyYieldsBlocking(h.id);

                    GameServer.deleteHive(h.id);

                    hiveDao.deleteById(h.id);

                }

                deleteRemainingOwnerHivesFromCloud(ownerId);

                if (pollinationContractRepository != null) {
                    pollinationContractRepository.removeAllForOwnerBlocking(ownerId);
                }

                TruckLiveTrips.removeAllForOwnerBlocking(appContext, ownerId);
                HoneyLogistics.removeAllForOwnerBlocking(appContext, ownerId);
                HoneyOrderStore.releaseAllClaimedBy(appContext, ownerId);

                int todayKey = GameCalendar.toDayKey(LocalDate.now(GameCalendar.userTimeZone()));

                GameProductionStateEntity state = gameProductionStateDao.getByOwner(ownerId);

                if (state == null) {

                    state = new GameProductionStateEntity();

                    state.ownerId = ownerId;

                }

                state.gameStartDayKey = todayKey;

                state.lastProcessedProductionDayKey = todayKey;

                gameProductionStateDao.insert(state);

                pushProductionStateToFirestore(ownerId, state);

                hexParcelRepository.removeAllOwnershipForOwnerBlocking(ownerId);
                HeadquartersStore.clear(appContext, ownerId);
                MapRegionPrefs.set(appContext, com.apiculture.simulator.domain.map.PlayableMapRegion.IBERIA);
                appContext.getSharedPreferences("event_claims_v1", android.content.Context.MODE_PRIVATE)
                        .edit().clear().commit();
                appContext.getSharedPreferences("event_dismiss_v1", android.content.Context.MODE_PRIVATE)
                        .edit().clear().commit();
                appContext.getSharedPreferences("velutina_hits_v1", android.content.Context.MODE_PRIVATE)
                        .edit().clear().commit();
                if (firestore != null) {
                    new AdminGameResetRepository(appContext, firestore).forgetPlayerEventBlocking(ownerId);
                    Map<String, Object> hq = new HashMap<>();
                    hq.put("hqIberiaLat", com.google.firebase.firestore.FieldValue.delete());
                    hq.put("hqIberiaLng", com.google.firebase.firestore.FieldValue.delete());
                    hq.put("hqZaLat", com.google.firebase.firestore.FieldValue.delete());
                    hq.put("hqZaLng", com.google.firebase.firestore.FieldValue.delete());
                    hq.put("hqMdgLat", com.google.firebase.firestore.FieldValue.delete());
                    hq.put("hqMdgLng", com.google.firebase.firestore.FieldValue.delete());
                    Tasks.await(firestore.collection("users").document(ownerId).set(hq, SetOptions.merge()));
                }

                mainHandler.post(() -> onMainMessage.accept(null));

            } catch (Exception e) {

                Log.e(TAG, "resetGameToStarterState", e);

                String detail = formatThrowableForUser(e);

                mainHandler.post(() -> onMainMessage.accept(detail));

            }

        });

    }



    private void deleteRemainingOwnerHivesFromCloud(String ownerId) {
        if (firestore == null || ownerId == null || ownerId.isEmpty()) {
            return;
        }
        firestore.collection("hives")
                .whereEqualTo("ownerId", ownerId)
                .get()
                .addOnSuccessListener(qs -> {
                    if (qs == null) {
                        return;
                    }
                    for (QueryDocumentSnapshot doc : qs) {
                        deleteFirestoreDailyYieldsBlocking(doc.getId());
                        doc.getReference().delete()
                                .addOnFailureListener(e ->
                                        Log.w(TAG, "No se pudo borrar colmena residual: " + doc.getId(), e));
                        hiveDao.deleteById(doc.getId());
                    }
                })
                .addOnFailureListener(e -> Log.w(TAG, "Consulta de limpieza hives omitida", e));
    }

    private void deleteFirestoreDailyYieldsBlocking(String hiveId) {
        if (firestore == null || hiveId == null) {
            return;
        }
        firestore.collection("hives").document(hiveId)
                .collection("dailyYields").get()
                .addOnSuccessListener(qs -> {
                    if (qs == null) {
                        return;
                    }
                    for (QueryDocumentSnapshot doc : qs) {
                        doc.getReference().delete()
                                .addOnFailureListener(e ->
                                        Log.w(TAG, "No se pudo borrar dailyYields/" + doc.getId(), e));
                    }
                })
                .addOnFailureListener(e -> Log.w(TAG, "Listado/borrado dailyYields omitido para " + hiveId, e));
    }



    /**
     * @param starterAdultWorkersOrNull si no es null, fija esas obreras adultas y reparte el resto como cría
     *                                  (solo primera colmena del reinicio).
     */
    private void persistHiveForReset(HiveEntity hive, Integer starterAdultWorkersOrNull) throws Exception {

        int n = hive.beeCount > 0 ? hive.beeCount : STARTER_GAME_TOTAL_BEES;

        hive.beeCount = n;

        if (starterAdultWorkersOrNull != null) {

            hive.populationStateJson = HivePopulationState

                    .fromInitialColonyWithTargetAdultWorkers(n, starterAdultWorkersOrNull, hive.id).toJson();

        } else {

            hive.populationStateJson = HivePopulationState

                    .fromInitialColonyWithRandomBroodPipeline(n, hive.id).toJson();

        }

        normalizeBeeCount(hive);

        hive.elevationMeters = OpenMeteoElevation.resolveMetersPersistedBlocking(hive.lat, hive.lng);

        upsertCloud(hive);

        if (firestore != null) {

            Tasks.await(firestore.collection("hives").document(hive.id).set(hive, SetOptions.merge()));

        }

    }



    private static void starterHiveLatLngOffsets(HexParcel hex, double[][] outLatLon) {

        double side = HexGeometry.sideMetersForAreaKm2(
                PlayableMapRegion.fromHexId(hex.id).hexTargetAreaKm2());

        LocalTangentPlane plane = new LocalTangentPlane(hex.centroidLat, hex.centroidLon);

        double dist = side * 0.42;

        for (int i = 0; i < 3; i++) {

            double ang = Math.PI / 2.0 + i * 2.0 * Math.PI / 3.0;

            double dx = dist * Math.cos(ang);

            double dy = dist * Math.sin(ang);

            double lat = plane.toLatDeg(dx, dy);

            double lon = plane.toLonDeg(dx, dy);

            if (!HexParcelPointInPolygon.contains(lat, lon, hex.polygonLatLon)) {

                double d2 = dist * 0.62;

                dx = d2 * Math.cos(ang);

                dy = d2 * Math.sin(ang);

                lat = plane.toLatDeg(dx, dy);

                lon = plane.toLonDeg(dx, dy);

            }

            outLatLon[i][0] = lat;

            outLatLon[i][1] = lon;

        }

    }

    private static HiveEntity buildStarterHiveEntity(

            String ownerId, String name, String flora, String hexId, double lat, double lng,
            int initialTotalBees) {

        HiveEntity hive = new HiveEntity();

        hive.id = UUID.randomUUID().toString();

        hive.ownerId = ownerId;

        hive.name = name;

        hive.health = 90;

        hive.beeCount = Math.max(500, initialTotalBees);

        hive.reserves = 60;

        hive.queenAgeDays = 0;

        hive.queenGeneticQuality = randomInitialQueenGeneticQuality();

        hive.lat = lat;

        hive.lng = lng;

        hive.hexId = hexId;

        hive.floraType = flora;

        hive.superCount = 0;

        hive.honeyProduction = HiveHoneyRules.STARTER_HIVE_STOCK_KG;
        HiveHoneyStocks.seedStarter(hive);

        hive.varroaPct = 2.0;

        hive.varroaTreatmentDaysRemaining = 0;

        hive.varroaReboundDaysRemaining = 0;

        hive.lastHealthSimDayKey = 0;

        hive.populationStateJson = null;

        hive.lastSummaryDayKey = 0;

        hive.lastSummaryHoneyKg = 0;

        hive.lastSummaryDeltaBees = 0;

        hive.lastSummaryDeltaHealth = 0;

        hive.lastSummaryDeltaVarroa = 0;

        hive.lastSummaryWorkerDeaths = 0;

        hive.lastSummaryWorkerEmergences = 0;

        hive.lastSummaryEggsLaid = 0;

        HiveHoneyRules.clampHoneyStockToCap(hive);

        stampFirstProductionDay(hive);

        return hive;

    }



    /**

     * Aplica la producción diaria para cada día civil pendiente (una vez pasadas las 8:00 locales).

     * Idempotente por colmena y día. Conviene llamarlo al entrar en la app (p. ej. {@code onResume}).

     */

    public void tickDailyProductionForOwner(String ownerId) {

        tickDailyProductionForOwner(ownerId, null);

    }

    /**
     * Tras el tick, si se aplicó al menos un día de producción, {@code onMainThread} recibe el
     * {@link TickAppliedDayResult} con las cifras de cada colmena (hilo principal).
     */
    public void tickDailyProductionForOwner(String ownerId,
            Consumer<TickAppliedDayResult> onMainThread) {
        tickDailyProductionForOwner(ownerId, null, onMainThread);
    }

    /**
     * @param onWorkStartedOnMain si hay días pendientes, se publica en el hilo principal
     *                            justo antes de aplicarlos (p. ej. diálogo de cálculo).
     */
    public void tickDailyProductionForOwner(String ownerId,
            Runnable onWorkStartedOnMain,
            Consumer<TickAppliedDayResult> onMainThread) {

        if (ownerId == null || ownerId.isEmpty()) {

            if (onMainThread != null) {

                mainHandler.post(() -> onMainThread.accept(TickAppliedDayResult.NONE));

            }

            return;

        }

        ioExecutor.execute(() -> {

            try {

                List<DailyTickSummary> appliedDays = new ArrayList<>();

                tickDailyProductionForOwnerSync(ownerId, appliedDays, onWorkStartedOnMain);

                if (onMainThread == null) {

                    return;

                }

                if (appliedDays.isEmpty()) {

                    mainHandler.post(() -> onMainThread.accept(TickAppliedDayResult.NONE));

                    return;

                }

                TickAppliedDayResult result = new TickAppliedDayResult(appliedDays);

                mainHandler.post(() -> onMainThread.accept(result));

            } catch (RuntimeException e) {
                Log.w(TAG, "No se pudo completar el tick diario de producción", e);

                if (onMainThread != null) {

                    mainHandler.post(() -> onMainThread.accept(TickAppliedDayResult.NONE));

                }

            }

        });

    }



    /**

     * Depuración: pide al servidor el tick pendiente y aplica las colmenas que devuelva.

     *

     * @param onMainThreadMessage recibe el texto para un Toast (siempre en el hilo principal); no debe ser null.

     */

    public void debugSimulateNextProductionDay(String ownerId, Consumer<String> onMainThreadMessage) {

        if (ownerId == null || ownerId.isEmpty()) {

            mainHandler.post(() -> onMainThreadMessage.accept("Inicia sesión para simular el día."));

            return;

        }

        ioExecutor.execute(() -> {

            String msg;

            try {

                getOrCreateProductionState(ownerId);

                mergeRemoteProductionProgress(ownerId);

                GameProductionStateEntity state = gameProductionStateDao.getByOwner(ownerId);

                if (state == null) {

                    msg = "No hay estado de producción.";

                } else {

                    LocalDate gameStart = GameCalendar.fromDayKey(state.gameStartDayKey);

                    LocalDate lastProcessed = lastClosedProductionDay(state);

                    LocalDate next = lastProcessed.plusDays(1);

                    DateTimeFormatter df = DateTimeFormatter.ofPattern("dd/MM/yyyy");

                    if (next.isBefore(gameStart)) {

                        state.lastProcessedProductionDayKey = GameCalendar.toDayKey(next);

                        gameProductionStateDao.update(state);

                        pushProductionStateToFirestore(ownerId, state);

                        msg = "Avance sin producción (antes del día 1): " + next.format(df);

                    } else if (!GameServer.enabled()) {
                        msg = "El cálculo diario solo corre en el servidor.";
                    } else {
                        List<DailyTickSummary> days = new ArrayList<>();
                        boolean ok = applyAuthoritativeProduction(ownerId, state, days, null);
                        if (!ok) {
                            msg = "El servidor no respondió.";
                        } else if (days.isEmpty()) {
                            msg = "No hay un día nuevo cerrado en el servidor.";
                        } else {
                            msg = "Producción del servidor · " + next.format(df);
                        }
                    }

                }

            } catch (Exception e) {

                String detail = e.getMessage();

                msg = detail != null && !detail.isEmpty()

                        ? "Error: " + detail

                        : "No se pudo simular el día.";

            }

            String out = msg;

            mainHandler.post(() -> onMainThreadMessage.accept(out));

        });

    }



    /**

     * Recolección bruta de miel: últimos 7 días de calendario incluyendo hoy (más antiguo → índice 0).

     */

    public void loadLast7DaysProductionKg(String hiveId, String ownerId, Consumer<double[]> onMainThread) {

        if (hiveId == null || ownerId == null) {

            mainHandler.post(() -> onMainThread.accept(new double[7]));

            return;

        }

        ioExecutor.execute(() -> {

            HiveLast6DaysCharts charts = computeLast6DaysChartsSync(hiveId, ownerId);

            mainHandler.post(() -> onMainThread.accept(charts.honeyKg));

        });

    }

    public void loadLast6DaysHiveCharts(String hiveId, String ownerId,
            Consumer<HiveLast6DaysCharts> onMainThread) {

        if (hiveId == null || ownerId == null) {

            mainHandler.post(() -> onMainThread.accept(new HiveLast6DaysCharts(null, null, null, 0)));

            return;

        }

        ioExecutor.execute(() -> {

            HiveLast6DaysCharts charts = computeLast6DaysChartsSync(hiveId, ownerId);

            mainHandler.post(() -> onMainThread.accept(charts));

        });

    }

    /**
     * Fecha de calendario para la UI: hoy, o el último día simulado si el debug va por delante.
     */
    public void loadUiGameDate(String ownerId, Consumer<LocalDate> onMainThread) {
        if (ownerId == null || ownerId.isEmpty()) {
            mainHandler.post(() -> onMainThread.accept(LocalDate.now(GameCalendar.userTimeZone())));
            return;
        }
        ioExecutor.execute(() -> {
            GameProductionStateEntity state = gameProductionStateDao.getByOwner(ownerId);
            int key = state != null ? state.lastProcessedProductionDayKey : 0;
            LocalDate d = GameCalendar.uiDateForLastProcessed(key);
            mainHandler.post(() -> onMainThread.accept(d));
        });
    }

    /** Miel diaria de todas las colmenas, 7 días hasta el último día simulado. Índice 0 = el más antiguo. */
    public void loadWeeklyHoney(String ownerId, java.util.function.Consumer<double[]> onMain) {
        if (onMain == null) {
            return;
        }
        ioExecutor.execute(() -> {
            double[] days = new double[7];
            java.time.ZoneId z = GameCalendar.userTimeZone();
            java.time.LocalDate end = java.time.LocalDate.now(z);
            if (ownerId != null && !ownerId.isEmpty()) {
                GameProductionStateEntity state = gameProductionStateDao.getByOwner(ownerId);
                int last = state != null ? state.lastProcessedProductionDayKey : 0;
                end = closedChartEnd(last);
                int from = GameCalendar.toDayKey(end.minusDays(6));
                int to = GameCalendar.toDayKey(end);
                java.util.List<com.apiculture.simulator.data.local.dao.DayHoneyTotal> rows =
                        hiveDailyYieldDao.sumHoneyByDay(ownerId, from, to);
                if (rows != null) {
                    for (com.apiculture.simulator.data.local.dao.DayHoneyTotal row : rows) {
                        if (row == null) {
                            continue;
                        }
                        long index = java.time.temporal.ChronoUnit.DAYS.between(
                                end.minusDays(6), GameCalendar.fromDayKey(row.dayKey));
                        if (index >= 0 && index < 7) {
                            days[(int) index] = Math.max(0, row.kg);
                        }
                    }
                }
            }
            mainHandler.post(() -> onMain.accept(days));
        });
    }

    /**
     * Últimos 7 días de calendario incluyendo hoy (índice 0 = el más antiguo).
     */
    private HiveLast6DaysCharts computeLast6DaysChartsSync(String hiveId, String ownerId) {

        ZoneId z = GameCalendar.userTimeZone();

        LocalDate today = LocalDate.now(z);

        double[] honey = new double[7];

        int[] worker = new int[7];

        int[] eggs = new int[7];

        double[] consumption = new double[7];

        GameProductionStateEntity state = gameProductionStateDao.getByOwner(ownerId);

        LocalDate gameStart = state != null

                ? GameCalendar.fromDayKey(state.gameStartDayKey)

                : today;

        int lastProcessedKey = state != null ? state.lastProcessedProductionDayKey : 0;

        LocalDate chartEnd = closedChartEnd(lastProcessedKey);
        fillMissingYieldsFromServer(ownerId, GameCalendar.toDayKey(chartEnd.minusDays(7)));

        HiveEntity hive = hiveDao.getHiveByIdSync(hiveId);
        HivePopulationState popEst = null;
        if (hive != null) {
            ensurePopulationJson(hive);
            popEst = HivePopulationState.fromJson(hive.populationStateJson);
        }

        for (int i = 0; i < 7; i++) {

            LocalDate d = chartEnd.minusDays(6 - i);

            if (d.isBefore(gameStart)) {

                honey[i] = 0.0;

                worker[i] = 0;

                eggs[i] = 0;

                consumption[i] = 0.0;

                continue;

            }

            int dayKey = GameCalendar.toDayKey(d);

            HiveDailyYieldEntity row = hiveDailyYieldDao.getYield(hiveId, dayKey);
            if (row == null || (Math.abs(row.kg) <= 1e-9 && row.forageKg <= 1e-9 && row.eggsLaid == 0)) {
                HiveDailyYieldEntity cloud = readFirestoreDailyYieldBlocking(hiveId, dayKey);
                if (cloud != null && (Math.abs(cloud.kg) > 1e-9 || cloud.forageKg > 1e-9
                        || cloud.eggsLaid > 0 || cloud.workerNetDelta != 0)) {
                    hiveDailyYieldDao.insert(cloud);
                    row = cloud;
                }
            }
            if ((row == null || (Math.abs(row.kg) <= 1e-9 && row.forageKg <= 1e-9))
                    && hive != null && hive.lastSummaryDayKey == dayKey
                    && Math.abs(hive.lastSummaryHoneyKg) > 1e-9) {
                HiveDailyYieldEntity fromHive = new HiveDailyYieldEntity();
                fromHive.hiveId = hiveId;
                fromHive.dayKey = dayKey;
                fromHive.kg = hive.lastSummaryHoneyKg;
                fromHive.workerNetDelta = hive.lastSummaryDeltaBees;
                fromHive.eggsLaid = hive.lastSummaryEggsLaid;
                fromHive.consumptionKg = row != null ? row.consumptionKg : 0.0;
                fromHive.forageKg = 0.0;
                hiveDailyYieldDao.insert(fromHive);
                row = fromHive;
            }

            honey[i] = row != null ? row.kg : 0.0;

            worker[i] = row != null ? row.workerNetDelta : 0;

            eggs[i] = row != null ? row.eggsLaid : 0;

            double cons = row != null ? row.consumptionKg : 0.0;
            if (cons <= 1e-9 && popEst != null && row != null
                    && (Math.abs(row.kg) > 1e-9 || row.eggsLaid > 0 || row.forageKg > 1e-9)) {
                cons = HiveDailyBiology.consumptionKg(popEst, hive, row.eggsLaid, dayKey);
            }
            consumption[i] = cons;
            double forage = row != null ? row.forageKg : 0.0;
            if (forage <= 1e-9) {
                forage = Math.max(0.0, honey[i] + cons);
            }
            honey[i] = Math.max(0.0, forage);

        }

        return new HiveLast6DaysCharts(honey, worker, eggs, consumption, GameCalendar.toDayKey(chartEnd));

    }



    private GameProductionStateEntity getOrCreateProductionState(String ownerId) {

        GameProductionStateEntity s = gameProductionStateDao.getByOwner(ownerId);

        if (s == null) {

            s = new GameProductionStateEntity();

            s.ownerId = ownerId;

            s.gameStartDayKey = GameCalendar.toDayKey(LocalDate.now(GameCalendar.userTimeZone()));

            s.lastProcessedProductionDayKey = s.gameStartDayKey;

            gameProductionStateDao.insert(s);

            pushProductionStateToFirestore(ownerId, s);

        }

        return s;

    }

    private static void stampFirstProductionDay(HiveEntity hive) {
        if (hive == null) {
            return;
        }
        hive.firstProductionDayKey = HiveProductionEligibility.firstDayAtNextProduction(
                java.time.ZonedDateTime.now(GameCalendar.userTimeZone()));
    }

    /**
     * Último día que ya se puede pintar: el día cerrado a las 8:00, que es el anterior.
     * El día civil en curso no entra hasta el corte de las 8:00 del día siguiente.
     */
    private static LocalDate closedChartEnd(int lastProcessedKey) {
        LocalDate closed = GameCalendar.fromDayKey(GameCalendar.dueProductionDayKey()).minusDays(1);
        if (lastProcessedKey > 0) {
            LocalDate last = GameCalendar.fromDayKey(lastProcessedKey);
            if (last.isBefore(closed)) {
                return last;
            }
        }
        return closed;
    }

    /**
     * El día de inicio de partida no produce: {@code lastProcessed == 0} cuenta como ya cerrado.
     */
    private static LocalDate lastClosedProductionDay(GameProductionStateEntity state) {
        if (state.lastProcessedProductionDayKey == 0) {
            return GameCalendar.fromDayKey(state.gameStartDayKey);
        }
        return GameCalendar.fromDayKey(state.lastProcessedProductionDayKey);
    }

    /**
     * Trae de Firestore el progreso de producción (otro dispositivo o sesión anterior).
     * Así, si el jugador no abre la app varios días, al volver se usa el último día
     * procesado en la nube y se calculan en cadena todos los días pendientes hasta hoy.
     */
    private void mergeRemoteProductionProgress(String ownerId) {
        if (firestore == null) {
            return;
        }
        try {
            DocumentSnapshot doc = Tasks.await(firestore.collection("users").document(ownerId)
                    .collection("meta").document("productionState").get());
            if (!doc.exists()) {
                return;
            }
            Long fsLast = doc.getLong("lastProcessedProductionDayKey");
            Long fsGameStart = doc.getLong("gameStartDayKey");
            GameProductionStateEntity local = gameProductionStateDao.getByOwner(ownerId);
            if (local == null) {
                return;
            }
            boolean changed = false;
            if (fsGameStart != null) {
                int remoteGs = fsGameStart.intValue();
                if (local.gameStartDayKey == 0 || remoteGs < local.gameStartDayKey) {
                    local.gameStartDayKey = remoteGs;
                    changed = true;
                }
            }
            if (fsLast != null && fsLast > local.lastProcessedProductionDayKey) {
                local.lastProcessedProductionDayKey = fsLast.intValue();
                changed = true;
            }
            Long fsAnchor = doc.getLong("gameRealTimeAnchorEpochMs");
            if (fsAnchor != null && fsAnchor != 0) {
                if (local.gameRealTimeAnchorEpochMs == 0 || fsAnchor < local.gameRealTimeAnchorEpochMs) {
                    local.gameRealTimeAnchorEpochMs = fsAnchor;
                    changed = true;
                }
            }
            if (changed) {
                gameProductionStateDao.update(local);
            }
        } catch (Exception ignored) {
        }
    }

    private void pushProductionStateToFirestore(String ownerId, GameProductionStateEntity state) {
        if (firestore == null || ownerId == null || state == null) {
            return;
        }
        try {
            Map<String, Object> m = new HashMap<>();
            m.put("gameStartDayKey", state.gameStartDayKey);
            m.put("lastProcessedProductionDayKey", state.lastProcessedProductionDayKey);
            m.put("gameRealTimeAnchorEpochMs", state.gameRealTimeAnchorEpochMs);
            m.put("lastProductionAt", FieldValue.serverTimestamp());
            firestore.collection("users").document(ownerId)
                    .collection("meta").document("productionState")
                    .set(m, SetOptions.merge());
        } catch (Exception ignored) {
        }
    }

    private HiveDailyYieldEntity readFirestoreDailyYieldBlocking(String hiveId, int dayKey) {
        if (firestore == null) {
            return null;
        }
        try {
            DocumentSnapshot snap = Tasks.await(firestore.collection("hives").document(hiveId)
                    .collection("dailyYields").document(String.valueOf(dayKey)).get());
            if (!snap.exists()) {
                return null;
            }
            HiveDailyYieldEntity e = new HiveDailyYieldEntity();
            e.hiveId = hiveId;
            e.dayKey = dayKey;
            e.kg = snap.contains("kg") ? snap.getDouble("kg") : 0.0;
            Long w = snap.getLong("workerNetDelta");
            e.workerNetDelta = w != null ? w.intValue() : 0;
            Long eg = snap.getLong("eggsLaid");
            e.eggsLaid = eg != null ? eg.intValue() : 0;
            e.consumptionKg = snap.contains("consumptionKg") ? snap.getDouble("consumptionKg") : 0.0;
            e.forageKg = snap.contains("forageKg") ? snap.getDouble("forageKg") : 0.0;
            return e;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Clima del día de calendario anterior a {@code productionDay} (Open-Meteo por lat/lon).
     */
    private Map<String, DailyWeather> buildPreviousDayWeatherByHive(List<HiveEntity> hives, LocalDate productionDay) {
        LocalDate previousDay = productionDay.minusDays(1);
        Map<String, DailyWeather> byHive = new HashMap<>();
        Map<String, DailyWeather> coordDayCache = new HashMap<>();
        if (weatherRepository == null) {
            return byHive;
        }
        for (HiveEntity h : hives) {
            String ck = String.format(Locale.US, "%.4f,%.4f_%d", h.lat, h.lng,
                    GameCalendar.toDayKey(previousDay));
            DailyWeather t = coordDayCache.get(ck);
            if (t == null) {
                t = weatherRepository.fetchCalendarDayWeatherBlocking(h.lat, h.lng, previousDay);
                if (t == null) {
                    t = new DailyWeather();
                }
                coordDayCache.put(ck, t);
            }
            byHive.put(h.id, t);
        }
        return byHive;
    }

    /**
     * Sube las colmenas y deja que el servidor avance los días de producción y el saldo.
     * @return false si el servidor no respondió y hay que calcular en el teléfono.
     */
    private boolean applyAuthoritativeProduction(String ownerId, GameProductionStateEntity state,
            List<DailyTickSummary> appliedDaysOut, Runnable onWorkStartedOnMain) {
        try {
            List<HiveEntity> local = hiveDao.getHivesByOwnerSync(ownerId);
            JSONArray payload = new JSONArray();
            if (local != null) {
                for (HiveEntity hive : local) {
                    if (hive == null || hive.id == null || hive.id.isEmpty()) continue;
                    payload.put(hiveJson(hive));
                }
            }
            int due = GameCalendar.dueProductionDayKey();
            int shown = productionShownDay(ownerId);
            int since = shown > 0 && shown < due
                    ? shown
                    : GameCalendar.toDayKey(GameCalendar.fromDayKey(due).minusDays(1));
            Log.i(TAG, "production post since=" + since
                    + " shown=" + shown
                    + " due=" + due
                    + " upload=" + productionUploadPending(ownerId));
            String reportText = GameServer.runProductionClock(ownerId, payload.toString(),
                    java.time.ZoneId.systemDefault().getId(),
                    since,
                    productionUploadPending(ownerId));
            if (reportText == null) {
                Log.w(TAG, "production post sin respuesta");
                return false;
            }
            markProductionUploadPending(ownerId, false);
            JSONObject report = new JSONObject(reportText);
            JSONArray hives = report.optJSONArray("hives");
            if (hives != null && local != null) {
                for (int i = 0; i < hives.length(); i++) {
                    JSONObject row = hives.optJSONObject(i);
                    if (row == null) continue;
                    HiveEntity hive = findHive(local, row.optString("id", ""));
                    if (hive == null) continue;
                    hive.beeCount = row.optInt("beeCount", hive.beeCount);
                    hive.honeyProduction = row.optDouble("honeyProduction", hive.honeyProduction);
                    if (row.has("honeyStocksJson") && !row.isNull("honeyStocksJson")) {
                        String stocks = row.optString("honeyStocksJson", "");
                        if (!stocks.isEmpty()) {
                            hive.honeyStocksJson = stocks;
                        }
                    }
                    HiveHoneyStocks.ensureSeeded(hive);
                    hive.populationStateJson = row.optString("populationStateJson", hive.populationStateJson);
                    hive.lastSummaryDayKey = row.optInt("lastSummaryDayKey", hive.lastSummaryDayKey);
                    hive.lastSummaryHoneyKg = row.optDouble("lastSummaryHoneyKg", hive.lastSummaryHoneyKg);
                    hive.lastSummaryDeltaBees = row.optInt("lastSummaryDeltaBees", hive.lastSummaryDeltaBees);
                    hive.lastSummaryWorkerDeaths = row.optInt("lastSummaryWorkerDeaths", hive.lastSummaryWorkerDeaths);
                    hive.lastSummaryWorkerEmergences = row.optInt(
                            "lastSummaryWorkerEmergences", hive.lastSummaryWorkerEmergences);
                    hive.lastSummaryEggsLaid = row.optInt("lastSummaryEggsLaid", hive.lastSummaryEggsLaid);
                    hive.lastSummarySwarmed = row.optBoolean("lastSummarySwarmed", hive.lastSummarySwarmed);
                    hive.health = row.optInt("health", hive.health);
                    hive.varroaPct = row.optDouble("varroaPct", hive.varroaPct);
                    hive.queenGeneticQuality = row.optInt("queenGeneticQuality", hive.queenGeneticQuality);
                    hive.lastHealthSimDayKey = row.optInt("lastHealthSimDayKey", hive.lastHealthSimDayKey);
                    hive.varroaTreatmentDaysRemaining = row.optInt(
                            "varroaTreatmentDaysRemaining", hive.varroaTreatmentDaysRemaining);
                    hive.varroaReboundDaysRemaining = row.optInt(
                            "varroaReboundDaysRemaining", hive.varroaReboundDaysRemaining);
                    hive.lastSummaryDeltaHealth = row.optInt("lastSummaryDeltaHealth", hive.lastSummaryDeltaHealth);
                    hive.lastSummaryDeltaVarroa = row.optDouble("lastSummaryDeltaVarroa", hive.lastSummaryDeltaVarroa);
                    upsertCloud(hive);
                }
            }
            int through = report.optInt("settledDayKey",
                    report.optInt("throughDayKey", state.lastProcessedProductionDayKey));
            if (through > 0) {
                state.lastProcessedProductionDayKey = Math.max(state.lastProcessedProductionDayKey, through);
                gameProductionStateDao.update(state);
                pushProductionStateToFirestore(ownerId, state);
                markProductionSettledDay(ownerId, through);
            }
            JSONArray days = report.optJSONArray("days");
            Log.i(TAG, "production reply through=" + through
                    + " hives=" + (hives == null ? 0 : hives.length())
                    + " days=" + (days == null ? 0 : days.length()));
            if (days == null || days.length() == 0) {
                if (!hivesCaughtUp(ownerId, due)) {
                    Log.w(TAG, "production reply sin días y colmenas por detrás de " + due);
                    return false;
                }
                return true;
            }
            int seenThrough = productionShownDay(ownerId);
            boolean unseen = false;
            for (int i = 0; i < days.length(); i++) {
                JSONObject day = days.optJSONObject(i);
                if (day == null) {
                    continue;
                }
                int dayKey = day.optInt("dayKey", 0);
                JSONArray rows = day.optJSONArray("summaries");
                if (dayKey > seenThrough && rows != null && rows.length() > 0) {
                    unseen = true;
                    break;
                }
            }
            if (unseen && onWorkStartedOnMain != null) {
                mainHandler.post(onWorkStartedOnMain);
            }
            for (int i = 0; i < days.length(); i++) {
                JSONObject day = days.optJSONObject(i);
                if (day == null) continue;
                int dayKey = day.optInt("dayKey", through);
                List<HiveDayStartupSummary> summaries = new ArrayList<>();
                int swarms = 0;
                JSONArray rows = day.optJSONArray("summaries");
                if (rows != null) {
                    for (int j = 0; j < rows.length(); j++) {
                        JSONObject row = rows.optJSONObject(j);
                        if (row == null) continue;
                        rememberServerYield(row, dayKey);
                        if (dayKey <= seenThrough) {
                            continue;
                        }
                        boolean swarmed = row.optBoolean("swarmed", false);
                        if (swarmed) swarms++;
                        summaries.add(new HiveDayStartupSummary(
                                row.optString("hiveName", ""),
                                row.optDouble("honeyKg", 0),
                                row.optString("floraType", ""),
                                row.optInt("workerNet", 0),
                                row.optInt("eggsLaid", 0),
                                0,
                                0,
                                swarmed,
                                false,
                                false));
                    }
                }
                if (dayKey > seenThrough && !summaries.isEmpty()) {
                    appliedDaysOut.add(new DailyTickSummary(
                            dayKey, summaries, 0, 0, swarms));
                    markProductionShownDay(ownerId, dayKey);
                }
            }
            return true;
        } catch (Exception e) {
            Log.w(TAG, "No se pudo aplicar la producción del servidor", e);
            return false;
        }
    }

    @NonNull
    private static JSONObject hiveJson(@NonNull HiveEntity hive) throws org.json.JSONException {
        JSONObject o = new JSONObject();
        o.put("id", hive.id);
        o.put("ownerId", hive.ownerId);
        o.put("name", hive.name);
        o.put("beeCount", hive.beeCount);
        o.put("health", hive.health);
        o.put("honeyProduction", hive.honeyProduction);
        o.put("queenGeneticQuality", hive.queenGeneticQuality);
        o.put("lat", hive.lat);
        o.put("lng", hive.lng);
        o.put("hexId", hive.hexId);
        o.put("siteId", hive.siteId);
        o.put("floraType", hive.floraType);
        o.put("honeyStocksJson", hive.honeyStocksJson);
        o.put("superCount", hive.superCount);
        o.put("populationStateJson", hive.populationStateJson);
        o.put("varroaPct", hive.varroaPct);
        o.put("firstProductionDayKey", hive.firstProductionDayKey);
        o.put("transhumanceArrivesDayKey", hive.transhumanceArrivesDayKey);
        o.put("inWarehouse", hive.inWarehouse);
        o.put("feedHoneyBonusMultiplier", hive.feedHoneyBonusMultiplier);
        o.put("feedHoneyBonusEndDayKeyExclusive", hive.feedHoneyBonusEndDayKeyExclusive);
        o.put("feedBroodBonusMultiplier", hive.feedBroodBonusMultiplier);
        o.put("feedBroodBonusEndDayKeyExclusive", hive.feedBroodBonusEndDayKeyExclusive);
        o.put("varroaTreatmentDaysRemaining", hive.varroaTreatmentDaysRemaining);
        o.put("varroaReboundDaysRemaining", hive.varroaReboundDaysRemaining);
        o.put("reserves", hive.reserves);
        o.put("queenAgeDays", hive.queenAgeDays);
        o.put("elevationMeters", hive.elevationMeters);
        o.put("lastHealthSimDayKey", hive.lastHealthSimDayKey);
        o.put("lastSummaryDayKey", hive.lastSummaryDayKey);
        o.put("lastSummaryHoneyKg", hive.lastSummaryHoneyKg);
        o.put("lastSummaryDeltaBees", hive.lastSummaryDeltaBees);
        o.put("lastSummaryDeltaHealth", hive.lastSummaryDeltaHealth);
        o.put("lastSummaryDeltaVarroa", hive.lastSummaryDeltaVarroa);
        o.put("lastSummaryWorkerDeaths", hive.lastSummaryWorkerDeaths);
        o.put("lastSummaryWorkerEmergences", hive.lastSummaryWorkerEmergences);
        o.put("lastSummaryEggsLaid", hive.lastSummaryEggsLaid);
        o.put("lastSummarySwarmed", hive.lastSummarySwarmed);
        o.put("pendingContractHexId", hive.pendingContractHexId);
        o.put("pendingContractDayKey", hive.pendingContractDayKey);
        o.put("contractId", hive.contractId);
        o.put("contractOriginHexId", hive.contractOriginHexId);
        o.put("contractOriginFlora", hive.contractOriginFlora);
        o.put("contractOriginLat", hive.contractOriginLat);
        o.put("contractOriginLng", hive.contractOriginLng);
        o.put("returnToWarehouse", hive.returnToWarehouse);
        return o;
    }

    @Nullable
    private static HiveEntity hiveFromServer(@Nullable org.json.JSONObject row) {
        if (row == null) {
            return null;
        }
        String id = row.optString("id", "");
        if (id.isEmpty()) {
            return null;
        }
        HiveEntity hive = new HiveEntity();
        hive.id = id;
        hive.ownerId = row.optString("ownerId", "");
        hive.name = row.optString("name", "");
        hive.beeCount = row.optInt("beeCount", 0);
        hive.health = row.optInt("health", 100);
        hive.honeyProduction = row.optDouble("honeyProduction", 0);
        hive.reserves = row.optInt("reserves", 0);
        hive.queenAgeDays = row.optInt("queenAgeDays", 0);
        hive.queenGeneticQuality = row.optInt("queenGeneticQuality", 0);
        hive.lat = row.optDouble("lat", 0);
        hive.lng = row.optDouble("lng", 0);
        hive.hexId = row.optString("hexId", "");
        hive.siteId = row.optString("siteId", "");
        hive.elevationMeters = row.optInt("elevationMeters", -1);
        hive.floraType = row.optString("floraType", "");
        hive.honeyStocksJson = row.optString("honeyStocksJson", "");
        hive.superCount = row.optInt("superCount", 0);
        hive.populationStateJson = row.optString("populationStateJson", "");
        hive.varroaPct = row.optDouble("varroaPct", 0);
        hive.varroaTreatmentDaysRemaining = row.optInt("varroaTreatmentDaysRemaining", 0);
        hive.varroaReboundDaysRemaining = row.optInt("varroaReboundDaysRemaining", 0);
        hive.lastHealthSimDayKey = row.optInt("lastHealthSimDayKey", 0);
        hive.firstProductionDayKey = row.optInt("firstProductionDayKey", 0);
        hive.lastSummaryDayKey = row.optInt("lastSummaryDayKey", 0);
        hive.lastSummaryHoneyKg = row.optDouble("lastSummaryHoneyKg", 0);
        hive.lastSummaryDeltaBees = row.optInt("lastSummaryDeltaBees", 0);
        hive.lastSummaryDeltaHealth = row.optInt("lastSummaryDeltaHealth", 0);
        hive.lastSummaryDeltaVarroa = row.optDouble("lastSummaryDeltaVarroa", 0);
        hive.lastSummaryWorkerDeaths = row.optInt("lastSummaryWorkerDeaths", 0);
        hive.lastSummaryWorkerEmergences = row.optInt("lastSummaryWorkerEmergences", 0);
        hive.lastSummaryEggsLaid = row.optInt("lastSummaryEggsLaid", 0);
        hive.lastSummarySwarmed = row.optBoolean("lastSummarySwarmed", false);
        hive.feedHoneyBonusEndDayKeyExclusive = row.optInt("feedHoneyBonusEndDayKeyExclusive", 0);
        hive.feedHoneyBonusMultiplier = row.optDouble("feedHoneyBonusMultiplier", 1);
        hive.feedBroodBonusEndDayKeyExclusive = row.optInt("feedBroodBonusEndDayKeyExclusive", 0);
        hive.feedBroodBonusMultiplier = row.optDouble("feedBroodBonusMultiplier", 1);
        hive.transhumanceArrivesDayKey = row.optInt("transhumanceArrivesDayKey", 0);
        hive.pendingContractHexId = blankJson(row, "pendingContractHexId");
        hive.pendingContractDayKey = row.optInt("pendingContractDayKey", 0);
        hive.contractId = blankJson(row, "contractId");
        hive.contractOriginHexId = blankJson(row, "contractOriginHexId");
        hive.contractOriginFlora = blankJson(row, "contractOriginFlora");
        hive.contractOriginLat = row.optDouble("contractOriginLat", 0);
        hive.contractOriginLng = row.optDouble("contractOriginLng", 0);
        hive.inWarehouse = row.optBoolean("inWarehouse", false);
        hive.returnToWarehouse = row.optBoolean("returnToWarehouse", false);
        return hive;
    }

    /** org.json convierte un JSON null en el texto "null". Eso no es un contrato. */
    @NonNull
    private static String blankJson(@Nullable org.json.JSONObject row, @NonNull String key) {
        if (row == null || row.isNull(key)) {
            return "";
        }
        String value = row.optString(key, "");
        if (value == null || value.equals("null")) {
            return "";
        }
        return value;
    }

    private void pullHivesFromServer(@NonNull String ownerId) {
        org.json.JSONArray rows = GameServer.hives(ownerId);
        if (rows == null) {
            return;
        }
        for (int i = 0; i < rows.length(); i++) {
            HiveEntity hive = hiveFromServer(rows.optJSONObject(i));
            if (hive == null || hive.id == null || !ownerId.equals(hive.ownerId)) {
                continue;
            }
            hiveDao.upsert(hive);
        }
        Set<String> cloudIds = new HashSet<>();
        for (int i = 0; i < rows.length(); i++) {
            org.json.JSONObject row = rows.optJSONObject(i);
            if (row == null) {
                continue;
            }
            String id = row.optString("id", "");
            if (!id.isEmpty()) {
                cloudIds.add(id);
            }
        }
        pruneLocalHivesNotInCloud(ownerId, cloudIds);
        pruneForeignHives(ownerId);
    }

    @Nullable
    private static HiveEntity findHive(@NonNull List<HiveEntity> hives, @NonNull String id) {
        for (HiveEntity hive : hives) {
            if (hive != null && id.equals(hive.id)) return hive;
        }
        return null;
    }

    /**
     * Solo se ejecuta con la app abierta (p. ej. {@code MainActivity.onResume}): plan Spark gratuito.
     * Tras {@link #mergeRemoteProductionProgress}, pide el cálculo al servidor y aplica las colmenas
     * que devuelve. No simula población, varroa ni miel en el teléfono.
     *
     * @param appliedDaysOut se vacía al inicio; recibe un {@link DailyTickSummary} por cada día con colmenas
     *                       y datos de resumen (orden cronológico).
     */
    private void tickDailyProductionForOwnerSync(String ownerId, List<DailyTickSummary> appliedDaysOut,
            Runnable onWorkStartedOnMain) {

        appliedDaysOut.clear();

        GameBalanceConfig.load(appContext);

        GameProductionStateEntity state = getOrCreateProductionState(ownerId);

        mergeRemoteProductionProgress(ownerId);

        state = gameProductionStateDao.getByOwner(ownerId);

        if (state == null) {

            return;

        }

        try {
            TruckLiveTrips.completeDue(appContext);
        } catch (RuntimeException e) {
            Log.w(TAG, "No se pudieron cerrar los viajes de colmena antes del tick diario", e);
        }
        try {
            HoneyLogistics.completeDue(appContext, economyRepository, marketRepository);
        } catch (RuntimeException e) {
            Log.w(TAG, "No se pudieron cerrar los viajes de carga antes del tick diario", e);
        }
        try {
            HoneyOrderStore.maintainNow(appContext, marketRepository);
            PollinationOfferStore.maintainNow(appContext);
        } catch (RuntimeException e) {
            Log.w(TAG, "No se pudieron mantener las ofertas antes del tick diario", e);
        }

        if (!GameServer.enabled()) {
            Log.w(TAG, "production skip: el cálculo diario solo corre en el servidor");
            return;
        }
        int due = GameCalendar.dueProductionDayKey();
        if (state.lastProcessedProductionDayKey >= due) {
            markProductionSettledDay(ownerId, state.lastProcessedProductionDayKey);
        }
        Log.i(TAG, "production ask due=" + due
                + " local=" + state.lastProcessedProductionDayKey
                + " settled=" + productionSettledDay(ownerId)
                + " caughtUp=" + hivesCaughtUp(ownerId, due));
        if (!applyAuthoritativeProduction(ownerId, state, appliedDaysOut, onWorkStartedOnMain)) {
            Log.w(TAG, "production: el servidor no respondió; no se calcula en el teléfono");
            return;
        }
        LocalDate dueDay = GameCalendar.fromDayKey(due);
        LocalDate gameStart = GameCalendar.fromDayKey(state.gameStartDayKey);
        if (hexFloraRepository != null && !dueDay.isBefore(gameStart)) {
            CropTickResult cropTick = hexFloraRepository.tickCropsForOwnerBlocking(
                    ownerId, dueDay, economyRepository, hexParcelRepository);
            reassignForageAfterRemovedCrops(ownerId, cropTick);
            for (String hexId : cropTick.touchedHexIds) {
                hexParcelRepository.syncHexParcelFlorasToCloudBlocking(hexId);
            }
        }
    }

    private boolean productionUploadPending(@Nullable String ownerId) {
        if (ownerId == null || ownerId.isEmpty()) {
            return false;
        }
        return appContext.getSharedPreferences("production_sync", Context.MODE_PRIVATE)
                .getBoolean("pending_" + ownerId, false);
    }

    private int productionShownDay(@Nullable String ownerId) {
        if (ownerId == null || ownerId.isEmpty()) {
            return 0;
        }
        return appContext.getSharedPreferences("production_sync", Context.MODE_PRIVATE)
                .getInt("shown_day_" + ownerId, 0);
    }

    /** Copia al gráfico local un día que calculó el servidor, si el teléfono no lo tiene. */
    private void rememberServerYield(@Nullable JSONObject row, int dayKey) {
        if (row == null || dayKey <= 0) {
            return;
        }
        String hiveId = row.optString("hiveId", "");
        if (hiveId.isEmpty()) {
            return;
        }
        HiveDailyYieldEntity existing = hiveDailyYieldDao.getYield(hiveId, dayKey);
        if (existing != null && (existing.forageKg > 1e-9 || Math.abs(existing.kg) > 1e-9)) {
            return;
        }
        HiveDailyYieldEntity saved = new HiveDailyYieldEntity();
        saved.hiveId = hiveId;
        saved.dayKey = dayKey;
        saved.kg = row.optDouble("honeyKg", 0);
        saved.workerNetDelta = row.optInt("workerNet", 0);
        saved.eggsLaid = row.optInt("eggsLaid", 0);
        saved.consumptionKg = row.optDouble("consumptionKg", 0);
        saved.forageKg = row.optDouble("forageKg", 0);
        hiveDailyYieldDao.insert(saved);
    }

    private void fillMissingYieldsFromServer(@Nullable String ownerId, int sinceDayKey) {
        if (!GameServer.enabled() || ownerId == null || ownerId.isEmpty() || sinceDayKey <= 0) {
            return;
        }
        JSONArray days = GameServer.productionReports(ownerId, sinceDayKey);
        if (days == null) {
            return;
        }
        for (int i = 0; i < days.length(); i++) {
            JSONObject day = days.optJSONObject(i);
            if (day == null) continue;
            int dayKey = day.optInt("dayKey", 0);
            JSONArray rows = day.optJSONArray("summaries");
            if (rows == null) continue;
            for (int j = 0; j < rows.length(); j++) {
                rememberServerYield(rows.optJSONObject(j), dayKey);
            }
        }
    }

    private void markProductionShownDay(@Nullable String ownerId, int dayKey) {
        if (ownerId == null || ownerId.isEmpty() || dayKey <= 0) {
            return;
        }
        if (dayKey <= productionShownDay(ownerId)) {
            return;
        }
        appContext.getSharedPreferences("production_sync", Context.MODE_PRIVATE)
                .edit()
                .putInt("shown_day_" + ownerId, dayKey)
                .commit();
    }

    private void markProductionUploadPending(@Nullable String ownerId, boolean pending) {
        if (ownerId == null || ownerId.isEmpty()) {
            return;
        }
        appContext.getSharedPreferences("production_sync", Context.MODE_PRIVATE)
                .edit()
                .putBoolean("pending_" + ownerId, pending)
                .apply();
    }

    private static int oldestHiveSummaryDay(@Nullable List<HiveEntity> hives) {
        int oldest = 0;
        if (hives == null) {
            return 0;
        }
        for (HiveEntity hive : hives) {
            if (hive == null || hive.inWarehouse || hive.lastSummaryDayKey <= 0) {
                continue;
            }
            if (oldest == 0 || hive.lastSummaryDayKey < oldest) {
                oldest = hive.lastSummaryDayKey;
            }
        }
        return oldest;
    }

    /** El día local está cerrado y las colmenas ya tienen el resumen de ese día. */
    private boolean hivesCaughtUp(@Nullable String ownerId, int dueDayKey) {
        if (ownerId == null || dueDayKey <= 0) {
            return true;
        }
        List<HiveEntity> hives = hiveDao.getHivesByOwnerSync(ownerId);
        if (hives == null || hives.isEmpty()) {
            return true;
        }
        for (HiveEntity hive : hives) {
            if (hive == null || hive.inWarehouse) {
                continue;
            }
            if (hive.lastSummaryDayKey < dueDayKey) {
                return false;
            }
        }
        return true;
    }

    /** Día que el servidor ya cerró para este jugador. Hasta el siguiente no se vuelve a calcular. */
    private int productionSettledDay(@Nullable String ownerId) {
        if (ownerId == null || ownerId.isEmpty()) {
            return 0;
        }
        return appContext.getSharedPreferences("production_sync", Context.MODE_PRIVATE)
                .getInt("settled_day_" + ownerId, 0);
    }

    private void markProductionSettledDay(@Nullable String ownerId, int dayKey) {
        if (ownerId == null || ownerId.isEmpty() || dayKey <= 0) {
            return;
        }
        int current = productionSettledDay(ownerId);
        if (dayKey <= current) {
            return;
        }
        appContext.getSharedPreferences("production_sync", Context.MODE_PRIVATE)
                .edit()
                .putInt("settled_day_" + ownerId, dayKey)
                .commit();
    }

    public int countHivesOnHexBlocking(String hexId) {

        if (hexId == null || hexId.isEmpty()) {

            return 0;

        }

        return hiveDao.countByHexId(hexId);

    }

    /**
     * Colmenas de otro jugador en un hex, solo para mirar el prado.
     * No se escriben en Room: la escucha en vivo borra las ajenas.
     */
    public void loadVisitYardHives(@NonNull String ownerId, @NonNull String hexId,
            @NonNull Consumer<List<HiveEntity>> onMain) {
        ioExecutor.execute(() -> {
            List<HiveEntity> found = new ArrayList<>();
            if (GameServer.enabled()) {
                org.json.JSONArray rows = GameServer.mapHives(ownerId, hexId);
                if (rows != null) {
                    for (int i = 0; i < rows.length(); i++) {
                        org.json.JSONObject row = rows.optJSONObject(i);
                        if (row == null || !ownerId.equals(row.optString("ownerId", ""))) {
                            continue;
                        }
                        HiveEntity hive = new HiveEntity();
                        hive.id = row.optString("id", "");
                        hive.ownerId = ownerId;
                        hive.name = row.optString("name", "");
                        hive.beeCount = row.optInt("beeCount", 0);
                        hive.health = row.optInt("health", 0);
                        hive.lat = row.optDouble("lat", 0);
                        hive.lng = row.optDouble("lng", 0);
                        hive.hexId = hexId;
                        hive.siteId = row.optString("siteId", "");
                        if (!hive.id.isEmpty()) {
                            found.add(hive);
                        }
                    }
                }
            }
            if (found.isEmpty()) {
                List<HiveEntity> local = hiveDao.getByHexIdSync(hexId);
                if (local != null) {
                    for (HiveEntity hive : local) {
                        if (hive != null && ownerId.equals(hive.ownerId) && !hive.inWarehouse) {
                            found.add(hive);
                        }
                    }
                }
            }
            List<HiveEntity> result = found;
            mainHandler.post(() -> onMain.accept(result));
        });
    }

    public void syncFromCloud() {
        if (GameServer.enabled() && cloudSyncOwnerId != null && !cloudSyncOwnerId.isEmpty()) {
            String ownerId = cloudSyncOwnerId;
            ioExecutor.execute(() -> pullHivesFromServer(ownerId));
            return;
        }
        if (firestore == null || cloudSyncOwnerId == null || cloudSyncOwnerId.isEmpty()) {
            return;
        }
        final String ownerId = cloudSyncOwnerId;
        firestore.collection("hives")
                .whereEqualTo("ownerId", ownerId)
                .get()
                .addOnSuccessListener(querySnapshot -> ioExecutor.execute(() ->
                        applyCloudHiveDocuments(ownerId, querySnapshot.getDocuments())));
    }

    public void startRealtimeCloudSync() {
        /* No sincronizar la colección entera: solo startRealtimeCloudSync(uid). */
    }

    /**
     * Escucha solo las colmenas de {@code ownerId}. Si el dueño cambia, reinicia la escucha
     * y limpia de Room las colmenas ajenas.
     */
    public void startRealtimeCloudSync(String ownerId) {
        if (ownerId == null || ownerId.isEmpty()) {
            return;
        }
        if (GameServer.enabled()) {
            cloudSyncOwnerId = ownerId;
            ioExecutor.execute(() -> {
                pruneForeignHives(ownerId);
                releaseWarehouseHivesBlocking(ownerId);
                pullHivesFromServer(ownerId);
            });
            return;
        }
        if (firestore == null) {
            return;
        }
        final String syncOwnerId = ownerId;
        if (cloudListener != null) {
            if (Objects.equals(cloudSyncOwnerId, syncOwnerId)) {
                return;
            }
            cloudListener.remove();
            cloudListener = null;
        }
        cloudSyncOwnerId = syncOwnerId;
        ioExecutor.execute(() -> {
            pruneForeignHives(syncOwnerId);
            releaseWarehouseHivesBlocking(syncOwnerId);
        });
        Query query = firestore.collection("hives").whereEqualTo("ownerId", syncOwnerId);
        cloudListener = query
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null || snapshot == null) return;
                    ioExecutor.execute(() -> applyCloudHiveDocuments(syncOwnerId, snapshot.getDocuments()));
                });
    }

    /**
     * Elimina en Room las colmenas del dueño que ya no existen en Firestore (otro dispositivo las borró o reinicio).
     */
    private void pruneLocalHivesNotInCloud(String ownerId, Set<String> cloudHiveIds) {
        try {
            List<HiveEntity> local = hiveDao.getHivesByOwnerSync(ownerId);
            for (HiveEntity h : local) {
                if (h.id != null && !cloudHiveIds.contains(h.id)) {
                    hiveDailyYieldDao.deleteAllForHive(h.id);
                    hiveDao.deleteById(h.id);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "pruneLocalHivesNotInCloud", e);
        }
    }



    public void stopRealtimeCloudSync() {

        if (cloudListener != null) {

            cloudListener.remove();

            cloudListener = null;

        }
        cloudSyncOwnerId = null;

    }

    /** Quita de Room colmenas que no son del jugador (p. ej. una sync antigua de toda la colección). */
    private void pruneForeignHives(String ownerId) {
        if (ownerId == null || ownerId.isEmpty()) {
            return;
        }
        try {
            List<HiveEntity> local = hiveDao.getAllHivesSync();
            if (local == null) {
                return;
            }
            for (HiveEntity h : local) {
                if (h == null || h.id == null) {
                    continue;
                }
                if (h.ownerId == null || !ownerId.equals(h.ownerId)) {
                    hiveDailyYieldDao.deleteAllForHive(h.id);
                    hiveDao.deleteById(h.id);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "pruneForeignHives", e);
        }
    }



    /** Room ha aplicado más días de simulación que el documento recibido de Firestore. */
    private static boolean localSimulationAheadOfCloudHive(HiveEntity local, HiveEntity cloud) {
        return local.lastSummaryDayKey > cloud.lastSummaryDayKey;
    }

    /**
     * Mismo {@code lastSummaryDayKey}: la nube puede traer miel por debajo del tope por snapshot viejo;
     * no sobrescribir si local ya está al límite y tiene más kg que la nube.
     */
    private static boolean shouldRestoreHoneyWhenCloudStaleSameDay(HiveEntity local, HiveEntity cloud) {
        if (local.lastSummaryDayKey != cloud.lastSummaryDayKey) {
            return false;
        }
        if (!HiveHoneyRules.isHoneyAtCapacity(local)) {
            return false;
        }
        return cloud.honeyProduction < local.honeyProduction - 1e-6;
    }

    /** Copia el estado que debe avanzar solo con el tick local (no regresar por nube rezagada). */
    private static void copyAheadSimulationFieldsFromLocal(HiveEntity local, HiveEntity into) {
        into.honeyProduction = local.honeyProduction;
        into.honeyStocksJson = local.honeyStocksJson;
        into.beeCount = local.beeCount;
        into.populationStateJson = local.populationStateJson;
        into.lastSummaryDayKey = local.lastSummaryDayKey;
        into.lastSummaryHoneyKg = local.lastSummaryHoneyKg;
        into.lastSummaryDeltaBees = local.lastSummaryDeltaBees;
        into.lastSummaryDeltaHealth = local.lastSummaryDeltaHealth;
        into.lastSummaryDeltaVarroa = local.lastSummaryDeltaVarroa;
        into.lastSummaryWorkerDeaths = local.lastSummaryWorkerDeaths;
        into.lastSummaryWorkerEmergences = local.lastSummaryWorkerEmergences;
        into.lastSummaryEggsLaid = local.lastSummaryEggsLaid;
        into.lastSummarySwarmed = local.lastSummarySwarmed;
        into.lastHealthSimDayKey = local.lastHealthSimDayKey;
        into.health = local.health;
        into.varroaPct = local.varroaPct;
        into.varroaTreatmentDaysRemaining = local.varroaTreatmentDaysRemaining;
        into.varroaReboundDaysRemaining = local.varroaReboundDaysRemaining;
        into.reserves = local.reserves;
        into.transhumanceArrivesDayKey = local.transhumanceArrivesDayKey;
        into.pendingContractHexId = local.pendingContractHexId;
        into.pendingContractDayKey = local.pendingContractDayKey;
        into.contractId = local.contractId;
        into.contractOriginHexId = local.contractOriginHexId;
        into.contractOriginFlora = local.contractOriginFlora;
        into.contractOriginLat = local.contractOriginLat;
        into.contractOriginLng = local.contractOriginLng;
        into.inWarehouse = local.inWarehouse;
        into.returnToWarehouse = local.returnToWarehouse;
    }

    private void applyCloudHiveDocuments(@Nullable String ownerId,
            @Nullable List<DocumentSnapshot> docs) {
        if (ownerId == null || ownerId.isEmpty() || docs == null) {
            return;
        }
        AppDatabase.getInstance(appContext).runInTransaction(() -> {
            Set<String> cloudIds = new HashSet<>();
            for (DocumentSnapshot doc : docs) {
                HiveEntity hive = doc != null ? doc.toObject(HiveEntity.class) : null;
                if (hive == null || hive.id == null) {
                    continue;
                }
                cloudIds.add(hive.id);
                if (!doc.contains("elevationMeters")) {
                    hive.elevationMeters = -1;
                }
                upsertHiveFromCloud(doc, hive);
            }
            pruneLocalHivesNotInCloud(ownerId, cloudIds);
            pruneForeignHives(ownerId);
            backfillMissingHiveSiteIds(ownerId);
        });
    }

    private void backfillMissingHiveSiteIds(@Nullable String ownerId) {
        if (ownerId == null || ownerId.isEmpty()) {
            return;
        }
        List<HiveEntity> hives = hiveDao.getHivesByOwnerSync(ownerId);
        if (hives == null) {
            return;
        }
        for (HiveEntity hive : hives) {
            if (hive == null || hive.inWarehouse) {
                continue;
            }
            String before = hive.siteId;
            ensureHiveSiteId(hive);
            if (!Objects.equals(before, hive.siteId)) {
                upsertCloud(hive);
            }
        }
    }

    private static boolean samePersistedHive(@NonNull HiveEntity a, @NonNull HiveEntity b) {
        return Objects.equals(a.name, b.name)
                && Objects.equals(a.hexId, b.hexId)
                && Objects.equals(a.siteId, b.siteId)
                && Objects.equals(a.floraType, b.floraType)
                && Objects.equals(a.honeyStocksJson, b.honeyStocksJson)
                && Objects.equals(a.populationStateJson, b.populationStateJson)
                && Objects.equals(a.pendingContractHexId, b.pendingContractHexId)
                && Objects.equals(a.contractId, b.contractId)
                && a.beeCount == b.beeCount
                && a.health == b.health
                && a.superCount == b.superCount
                && a.elevationMeters == b.elevationMeters
                && a.lastSummaryDayKey == b.lastSummaryDayKey
                && a.pendingContractDayKey == b.pendingContractDayKey
                && a.transhumanceArrivesDayKey == b.transhumanceArrivesDayKey
                && a.inWarehouse == b.inWarehouse
                && Double.compare(a.lat, b.lat) == 0
                && Double.compare(a.lng, b.lng) == 0
                && Double.compare(a.honeyProduction, b.honeyProduction) == 0;
    }

    private void upsertHiveFromCloud(DocumentSnapshot doc, HiveEntity hive) {
        if (cloudSyncOwnerId != null && (hive == null || hive.ownerId == null
                || !cloudSyncOwnerId.equals(hive.ownerId))) {
            return;
        }

        HiveEntity existing = null;
        if (hive != null && hive.id != null) {
            existing = hiveDao.getHiveByIdSync(hive.id);
            if (existing != null && doc != null && !doc.contains("superCount")) {
                hive.superCount = existing.superCount;
            }
            if (existing != null && (hive.siteId == null || hive.siteId.isEmpty())
                    && existing.siteId != null && !existing.siteId.isEmpty()) {
                hive.siteId = existing.siteId;
            }
            if (existing != null && localSimulationAheadOfCloudHive(existing, hive)) {
                copyAheadSimulationFieldsFromLocal(existing, hive);
            } else if (existing != null && shouldRestoreHoneyWhenCloudStaleSameDay(existing, hive)) {
                hive.honeyProduction = existing.honeyProduction;
                hive.honeyStocksJson = existing.honeyStocksJson;
            }
            hive.inWarehouse = false;
            hive.returnToWarehouse = false;
        }

        boolean fixed = normalizeBeeCount(hive);

        if (existing != null && !fixed && samePersistedHive(existing, hive)) {
            return;
        }

        upsertCloud(hive);

        if (firestore != null && hive.id != null && doc != null && doc.contains("onYard")) {
            firestore.collection("hives").document(hive.id)
                    .update("onYard", FieldValue.delete());
        }

        if (fixed && firestore != null) {

            firestore.collection("hives").document(hive.id).set(hive, SetOptions.merge());

        }

    }

    /**
     * Ejecutar solo desde {@link #ioExecutor}: mismo tratamiento que {@link #saveHive} pero sin re-encolar.
     */
    private void saveHiveBlocking(HiveEntity hive) throws Exception {

        if (hive == null) {

            return;

        }

        if (hive.populationStateJson == null || hive.populationStateJson.trim().isEmpty()) {

            int n = hive.beeCount > 0 ? hive.beeCount : DEFAULT_BEE_COUNT_PER_HIVE;

            if (hive.id != null && !hive.id.isEmpty()) {

                hive.populationStateJson = HivePopulationState

                        .fromInitialColonyWithRandomBroodPipeline(n, hive.id).toJson();

            } else {

                hive.populationStateJson = HivePopulationState.fromLegacyBeeCount(n).toJson();

            }

        }

        normalizeBeeCount(hive);

        HiveHoneyRules.clampHoneyStockToCap(hive);

        ensureHiveSiteId(hive);

        if (GameServer.enabled() && !GameServer.pushHive(hiveJson(hive))) {
            throw new java.io.IOException(
                    "No hay conexión con el servidor. No se puede realizar esta acción.");
        }
        hiveDao.upsert(hive);

        if (firestore != null) {

            Tasks.await(firestore.collection("hives").document(hive.id).set(hive));

        }
    }

    private void ensureHiveSiteId(@Nullable HiveEntity hive) {
        if (hive == null || hive.inWarehouse) {
            return;
        }
        if (hive.siteId != null && !hive.siteId.isEmpty()
                && !HexApiary.DEFAULT_SITE.equals(hive.siteId)) {
            return;
        }
        if (hive.hexId == null || hive.hexId.isEmpty() || hive.ownerId == null) {
            return;
        }
        HexParcel hex = IberiaHexOverlayStore.findById(appContext, hive.hexId);
        hive.siteId = HexApiary.resolveSiteIdAt(hex,
                hexParcelRepository.listSitesOnHexBlocking(hive.ownerId, hive.hexId),
                hive.hexId, hive.lat, hive.lng);
    }

    private void assignHiveSiteFromPoint(@Nullable HiveEntity hive, @Nullable HexParcel hex,
            double lat, double lng) {
        if (hive == null) {
            return;
        }
        String hexId = hex != null ? hex.id : hive.hexId;
        hive.siteId = HexApiary.resolveSiteIdAt(hex,
                hexParcelRepository.listSitesOnHexBlocking(hive.ownerId, hexId),
                hexId, lat, lng);
    }

    public void createHiveAtLocationValidated(

            String ownerId, double lat, double lng, String floraType, Consumer<String> onMainMessage) {

        if (ownerId == null || ownerId.isEmpty()) {

            mainHandler.post(() -> onMainMessage.accept("Sesión no válida."));

            return;

        }

        ioExecutor.execute(() -> {

            try {

                HexParcel hex = IberiaHexOverlayStore.findContaining(appContext, lat, lng);

                if (hex == null) {

                    mainHandler.post(() -> onMainMessage.accept("Ubicación fuera de terrenos jugables."));

                    return;

                }

                if (!hexParcelRepository.hasOwnerSync(hex.id, ownerId)) {

                    mainHandler.post(() -> onMainMessage.accept("Instala un apiario aquí para crear colmenas."));

                    return;

                }

                String site = HexApiary.resolveSiteIdAt(hex,
                        hexParcelRepository.listSitesOnHexBlocking(ownerId, hex.id),
                        hex.id, lat, lng);
                int nHive = hexParcelRepository.countHivesAtSiteBlocking(ownerId, hex.id, site);

                if (nHive >= HexParcelGameRules.MAX_HIVES_PER_SITE) {

                    mainHandler.post(() -> onMainMessage.accept(

                            "Este apiario ya tiene " + HexParcelGameRules.MAX_HIVES_PER_SITE + " colmenas."));

                    return;

                }

                long nowMs = System.currentTimeMillis();
                List<String> readyFloras = hexFloraRepository.listReadyFloraKeysBlocking(hex.id, site, nowMs);
                if (readyFloras.isEmpty()) {
                    mainHandler.post(() -> onMainMessage.accept(
                            "Este terreno aún no tiene flora lista. Espera a que termine la siembra."));
                    return;
                }
                String flora = HoneyMarketEngine.canonicalFloraKey(
                        floraType != null && !floraType.trim().isEmpty() ? floraType : readyFloras.get(0));
                if (!readyFloras.contains(flora)) {
                    mainHandler.post(() -> onMainMessage.accept(
                            "Elige un tipo de flora de este terreno para la colmena."));
                    return;
                }

                HiveEntity hive = new HiveEntity();

                hive.id = UUID.randomUUID().toString();

                hive.ownerId = ownerId;

                hive.name = "Apiario " + (System.currentTimeMillis() % 10000);

                hive.beeCount = DEFAULT_BEE_COUNT_PER_HIVE;

                hive.health = 90;

                hive.reserves = 55;

                hive.queenAgeDays = 0;

                hive.queenGeneticQuality = randomInitialQueenGeneticQuality();

                hive.lat = lat;

                hive.lng = lng;

                hive.hexId = hex.id;
                hive.siteId = site;

                hive.floraType = flora;

                hive.superCount = 0;

                hive.honeyProduction = HiveHoneyRules.STARTER_HIVE_STOCK_KG;
                HiveHoneyStocks.seedStarter(hive);

                hive.varroaPct = 2.0;

                hive.varroaTreatmentDaysRemaining = 0;

                hive.varroaReboundDaysRemaining = 0;

                hive.lastHealthSimDayKey = 0;

                hive.populationStateJson = null;

                hive.elevationMeters = OpenMeteoElevation.resolveMetersPersistedBlocking(lat, lng);

                stampFirstProductionDay(hive);

                saveHiveBlocking(hive);

                mainHandler.post(() -> onMainMessage.accept(null));

            } catch (Exception e) {

                Log.e(TAG, "createHiveAtLocationValidated", e);

                mainHandler.post(() -> onMainMessage.accept(formatThrowableForUser(e)));

            }

        });

    }

    /** Opción de terreno propio para comprar colmena (id + etiqueta corta). */
    public static final class SplitDest {
        public final String hexId;
        public final String siteId;
        public final String label;
        public final int free;
        public final double km;

        public SplitDest(String hexId, String siteId, String label, int free, double km) {
            this.hexId = hexId;
            this.siteId = siteId;
            this.label = label;
            this.free = free;
            this.km = km;
        }
    }

    public void listSplitDestinations(String ownerId, @Nullable HiveEntity parent,
            Consumer<List<SplitDest>> onMain) {
        ioExecutor.execute(() -> {
            List<SplitDest> out = new ArrayList<>();
            if (ownerId != null && parent != null) {
                List<HexParcelOwnershipEntity> rows =
                        hexParcelRepositoryDao(ownerId);
                for (HexParcelOwnershipEntity row : rows) {
                    if (row == null || !WarehouseRules.isApiarySite(row)) {
                        continue;
                    }
                    if (row.hexId != null && row.hexId.equals(parent.hexId)
                            && (parent.siteId == null || parent.siteId.equals(row.siteId))) {
                        continue;
                    }
                    int n = hexParcelRepository.countHivesAtSiteBlocking(
                            ownerId, row.hexId, row.siteId);
                    int free = HexParcelGameRules.MAX_HIVES_PER_SITE - n;
                    if (free <= 0) {
                        continue;
                    }
                    double lat = Math.abs(row.siteLat) > 1e-8 ? row.siteLat : parent.lat;
                    double lng = Math.abs(row.siteLng) > 1e-8 ? row.siteLng : parent.lng;
                    HexParcel parcel = IberiaHexOverlayStore.findById(appContext, row.hexId);
                    if (Math.abs(row.siteLat) < 1e-8 && parcel != null) {
                        double[] pin = hexParcelRepository.apiaryPinForOwnerBlocking(
                                ownerId, parcel, row.siteId);
                        lat = pin[0];
                        lng = pin[1];
                    }
                    double km = TranshumanceRules.haversineKm(parent.lat, parent.lng, lat, lng);
                    String name = row.parcelName != null && !row.parcelName.trim().isEmpty()
                            ? row.parcelName.trim() : row.hexId;
                    out.add(new SplitDest(row.hexId, row.siteId, name, free, km));
                }
                out.sort(Comparator.comparingDouble(d -> d.km));
            }
            List<SplitDest> ready = out;
            mainHandler.post(() -> onMain.accept(ready));
        });
    }

    private List<HexParcelOwnershipEntity> hexParcelRepositoryDao(String ownerId) {
        return AppDatabase.getInstance(appContext).hexParcelOwnershipDao().getAllForOwnerSync(ownerId);
    }

    public static final class OwnedHexOption {
        public final String hexId;
        public final String label;

        public OwnedHexOption(String hexId, String label) {
            this.hexId = hexId;
            this.label = label;
        }
    }

    public static int purchasePriceEurosForSuperCount(int superCount) {
        return 200;
    }

    public static int nucPurchasePriceEurosForSuperCount(int superCount) {
        return HiveCareRules.emptyNucPriceEuros(superCount);
    }

    public void listOwnedHexOptionsForFloraAsync(String ownerId, String floraType,
            Consumer<List<OwnedHexOption>> onMain) {
        if (onMain == null) {
            return;
        }
        if (ownerId == null || ownerId.isEmpty() || floraType == null) {
            mainHandler.post(() -> onMain.accept(Collections.emptyList()));
            return;
        }
        ioExecutor.execute(() -> {
            try {
                List<OwnedHexOption> out = new ArrayList<>();
                long nowMs = System.currentTimeMillis();
                String want = HoneyMarketEngine.canonicalFloraKey(floraType);
                for (String hexId : hexParcelRepository.listOwnedHexIdsSync(ownerId)) {
                    if (hexFloraRepository.isFloraReadyOnHexBlocking(hexId, want, nowMs)) {
                        String label = hexParcelRepository.getParcelDisplayNameSync(hexId);
                        out.add(new OwnedHexOption(hexId, label));
                    }
                }
                out.sort(Comparator.comparing(o -> o.label));
                List<OwnedHexOption> res = out;
                mainHandler.post(() -> onMain.accept(res));
            } catch (Exception e) {
                Log.e(TAG, "listOwnedHexOptionsForFloraAsync", e);
                mainHandler.post(() -> onMain.accept(Collections.emptyList()));
            }
        });
    }

    /**
     * Compra una colmena nueva en un hex propio: cobra según alzas (0 → 200 €, 1 → 250 €, 2 → 300 €),
     * coloca la colmena en el pin del apiario.
     */
    public void purchaseHiveValidated(
            String ownerId,
            String hexId,
            String expectedFloraType,
            String hiveName,
            int superCount,
            Consumer<String> onMainMessage) {
        purchaseHiveValidated(ownerId, hexId, expectedFloraType, hiveName, superCount, null,
                onMainMessage);
    }

    public void purchaseHiveValidated(
            String ownerId,
            String hexId,
            String expectedFloraType,
            String hiveName,
            int superCount,
            @Nullable String siteId,
            Consumer<String> onMainMessage) {
        if (GameServer.enabled() && !GameServer.isAvailable()) {
            mainHandler.post(() -> onMainMessage.accept(
                    "No hay conexión con el servidor. No se puede realizar esta acción."));
            return;
        }
        if (ownerId == null || ownerId.isEmpty()) {
            mainHandler.post(() -> onMainMessage.accept("Sesión no válida."));
            return;
        }
        if (hexId == null || hexId.isEmpty()) {
            mainHandler.post(() -> onMainMessage.accept("Elige un terreno."));
            return;
        }
        int supers = 0;
        double price = purchasePriceEurosForSuperCount(supers);
        String trimmedName = hiveName == null ? "" : hiveName.trim();
        if (trimmedName.isEmpty()) {
            mainHandler.post(() -> onMainMessage.accept("Escribe un nombre para la colmena."));
            return;
        }
        String nameToSave = trimmedName.length() > 120 ? trimmedName.substring(0, 120) : trimmedName;
        ioExecutor.execute(() -> {
            try {
                if (!hexParcelRepository.hasOwnerSync(hexId, ownerId)) {
                    mainHandler.post(() -> onMainMessage.accept(
                            "Instala un apiario aquí para crear colmenas."));
                    return;
                }
                HexParcel hex = IberiaHexOverlayStore.findById(appContext, hexId);
                if (hex == null) {
                    mainHandler.post(() -> onMainMessage.accept("Terreno no encontrado en el mapa."));
                    return;
                }
                List<HexParcelOwnershipEntity> sites = hexParcelRepository.listSitesOnHexBlocking(
                        ownerId, hexId);
                String site;
                if (siteId != null && !siteId.isEmpty()) {
                    site = HexApiary.normalize(siteId);
                    if (HexApiary.siteRow(site, sites) == null) {
                        mainHandler.post(() -> onMainMessage.accept("Elige un apiario de este terreno."));
                        return;
                    }
                } else {
                    double[] pin = hexParcelRepository.apiaryPinForOwnerBlocking(ownerId, hex);
                    site = HexApiary.resolveSiteIdAt(hex, sites, hexId, pin[0], pin[1]);
                }
                if (expectedFloraType == null || expectedFloraType.trim().isEmpty()) {
                    mainHandler.post(() -> onMainMessage.accept("Elige un tipo de flora."));
                    return;
                }
                long nowMs = System.currentTimeMillis();
                String want = HoneyMarketEngine.canonicalFloraKey(expectedFloraType);
                if (!hexFloraRepository.isFloraReadyOnHexBlocking(hexId, want, nowMs)) {
                    mainHandler.post(() -> onMainMessage.accept(
                            "Esa flora no está disponible aún en este terreno."));
                    return;
                }
                String flora = want;
                int nHive = hexParcelRepository.countHivesAtSiteBlocking(ownerId, hexId, site);
                if (nHive >= HexParcelGameRules.MAX_HIVES_PER_SITE) {
                    mainHandler.post(() -> onMainMessage.accept(
                            "Este apiario ya tiene " + HexParcelGameRules.MAX_HIVES_PER_SITE
                                    + " colmenas."));
                    return;
                }
                if (!economyRepository.trySpend(price,
                        "Compra de colmena " + nameToSave + " de " + flora)) {
                    mainHandler.post(() -> onMainMessage.accept(
                            economyRepository.blockedReason(price)));
                    return;
                }
                try {
                    double[] ll = hexParcelRepository.apiaryPinForOwnerBlocking(ownerId, hex, site);
                    int health = 85 + (int) (Math.random() * 16);
                    HiveEntity hive = new HiveEntity();
                    hive.id = UUID.randomUUID().toString();
                    hive.ownerId = ownerId;
                    hive.name = nameToSave;
                    hive.beeCount = DEFAULT_BEE_COUNT_PER_HIVE;
                    hive.health = health;
                    hive.reserves = 55;
                    hive.queenAgeDays = 0;
                    hive.queenGeneticQuality = randomInitialQueenGeneticQuality();
                    hive.lat = ll[0];
                    hive.lng = ll[1];
                    hive.hexId = hex.id;
                    hive.siteId = site;
                    hive.floraType = flora;
                    hive.superCount = supers;
                    hive.honeyProduction = HiveHoneyRules.STARTER_HIVE_STOCK_KG;
                    HiveHoneyStocks.seedStarter(hive);
                    hive.varroaPct = 2.0;
                    hive.varroaTreatmentDaysRemaining = 0;
                    hive.varroaReboundDaysRemaining = 0;
                    hive.lastHealthSimDayKey = 0;
                    hive.populationStateJson = null;
                    hive.lastSummaryDayKey = 0;
                    hive.lastSummaryHoneyKg = 0;
                    hive.lastSummaryDeltaBees = 0;
                    hive.lastSummaryDeltaHealth = 0;
                    hive.lastSummaryDeltaVarroa = 0;
                    hive.lastSummaryWorkerDeaths = 0;
                    hive.lastSummaryWorkerEmergences = 0;
                    hive.lastSummaryEggsLaid = 0;
                    hive.lastSummarySwarmed = false;
                    hive.elevationMeters = OpenMeteoElevation.resolveMetersPersistedBlocking(
                            hive.lat, hive.lng);
                    stampFirstProductionDay(hive);
                    saveHiveBlocking(hive);
                    grantXp(ownerId, XpAwards.BUY_HIVE);
                    mainHandler.post(() -> onMainMessage.accept(null));
                } catch (Exception e) {
                    economyRepository.addToBalance(price,
                            "Devolución de la compra de colmena " + nameToSave);
                    Log.e(TAG, "purchaseHiveValidated", e);
                    mainHandler.post(() -> onMainMessage.accept(formatThrowableForUser(e)));
                }
            } catch (Exception e) {
                Log.e(TAG, "purchaseHiveValidated", e);
                mainHandler.post(() -> onMainMessage.accept(formatThrowableForUser(e)));
            }
        });
    }

    /**
     * Colmena ya cobrada para un contrato NPC: aparece en la finca y al liquidar va al almacén.
     */
    public HiveEntity spawnPaidContractHiveBlocking(
            String ownerId,
            HexParcel dest,
            String flora,
            String hiveName,
            int superCount,
            String contractId,
            int todayKey) throws Exception {
        if (ownerId == null || dest == null || dest.id == null) {
            throw new IllegalArgumentException("Destino de contrato no válido.");
        }
        int supers = 0;
        String nameToSave = hiveName == null ? "" : hiveName.trim();
        if (nameToSave.isEmpty()) {
            nameToSave = "Núcleo";
        }
        if (nameToSave.length() > 120) {
            nameToSave = nameToSave.substring(0, 120);
        }
        String floraKey = HoneyMarketEngine.canonicalFloraKey(flora);
        if (floraKey == null || floraKey.isEmpty()) {
            floraKey = flora;
        }
        String primary = hexParcelRepository.ensurePrimaryHexSync(ownerId);
        double[] ll = hexParcelRepository.apiaryPinForOwnerBlocking(ownerId, dest);
        int health = 85 + (int) (Math.random() * 16);
        HiveEntity hive = new HiveEntity();
        hive.id = UUID.randomUUID().toString();
        hive.ownerId = ownerId;
        hive.name = nameToSave;
        hive.beeCount = DEFAULT_BEE_COUNT_PER_HIVE;
        hive.health = health;
        hive.reserves = 55;
        hive.queenAgeDays = 0;
        hive.queenGeneticQuality = randomInitialQueenGeneticQuality();
        hive.lat = ll[0];
        hive.lng = ll[1];
        hive.hexId = dest.id;
        hive.floraType = floraKey;
        hive.superCount = supers;
        hive.honeyProduction = HiveHoneyRules.STARTER_HIVE_STOCK_KG;
        HiveHoneyStocks.seedStarter(hive);
        hive.varroaPct = 2.0;
        hive.contractId = contractId;
        hive.contractOriginHexId = primary != null ? primary : dest.id;
        hive.contractOriginFlora = floraKey;
        hive.contractOriginLat = ll[0];
        hive.contractOriginLng = ll[1];
        hive.returnToWarehouse = false;
        hive.inWarehouse = false;
        hive.transhumanceArrivesDayKey = 0;
        hive.elevationMeters = OpenMeteoElevation.resolveMetersPersistedBlocking(hive.lat, hive.lng);
        stampFirstProductionDay(hive);
        saveHiveBlocking(hive);
        grantXp(ownerId, XpAwards.BUY_HIVE);
        return hive;
    }

    public void storeHiveInWarehouseBlocking(HiveEntity hive) throws Exception {
        if (hive == null || hive.ownerId == null) {
            return;
        }
        hive.inWarehouse = false;
        hive.returnToWarehouse = false;
        saveHiveBlocking(hive);
    }

    public void releaseWarehouseHivesBlocking(@Nullable String ownerId) {
        if (ownerId == null || ownerId.isEmpty()) {
            return;
        }
        List<HiveEntity> stock = hiveDao.getWarehouseHivesSync(ownerId);
        if (stock == null || stock.isEmpty()) {
            return;
        }
        for (HiveEntity hive : stock) {
            if (hive == null) {
                continue;
            }
            hive.inWarehouse = false;
            hive.returnToWarehouse = false;
            try {
                saveHiveBlocking(hive);
            } catch (Exception e) {
                Log.e(TAG, "releaseWarehouseHivesBlocking", e);
            }
        }
    }

    public void listWarehouseHives(String ownerId, Consumer<List<HiveEntity>> onMain) {
        if (onMain == null) {
            return;
        }
        if (ownerId == null || ownerId.isEmpty()) {
            mainHandler.post(() -> onMain.accept(Collections.emptyList()));
            return;
        }
        ioExecutor.execute(() -> {
            List<HiveEntity> rows = hiveDao.getWarehouseHivesSync(ownerId);
            if (rows == null) {
                rows = Collections.emptyList();
            }
            rows.sort(Comparator.comparing(h -> h.name != null ? h.name.toLowerCase(Locale.ROOT) : ""));
            List<HiveEntity> out = rows;
            mainHandler.post(() -> onMain.accept(out));
        });
    }

    public void placeWarehouseHive(String ownerId, String hiveId, String destHexId,
            Consumer<String> onMainMessage) {
        if (onMainMessage == null) {
            return;
        }
        ioExecutor.execute(() -> {
            try {
                String err = placeWarehouseHiveBlocking(ownerId, hiveId, destHexId);
                mainHandler.post(() -> onMainMessage.accept(err));
            } catch (Exception e) {
                Log.e(TAG, "placeWarehouseHive", e);
                mainHandler.post(() -> onMainMessage.accept(formatThrowableForUser(e)));
            }
        });
    }

    @Nullable
    public String placeWarehouseHiveBlocking(String ownerId, String hiveId, String destHexId)
            throws Exception {
        if (GameServer.enabled() && !GameServer.isAvailable()) {
            return "No hay conexión con el servidor. No se puede realizar esta acción.";
        }
        if (ownerId == null || ownerId.isEmpty()) {
            return "Sesión no válida.";
        }
        HiveEntity h = hiveDao.getHiveByIdSync(hiveId);
        if (h == null || h.ownerId == null || !h.ownerId.equals(ownerId)) {
            return "Colmena no válida.";
        }
        if (!h.inWarehouse) {
            return "Esa colmena no está en el obrador.";
        }
        if (!hexParcelRepository.hasOwnerSync(destHexId, ownerId)) {
            return "Solo puedes colocar stock en un apiario tuyo.";
        }
        HexParcel dest = IberiaHexOverlayStore.findById(appContext, destHexId);
        if (dest == null) {
            return "Terreno no encontrado.";
        }
        List<HexParcelOwnershipEntity> sites = hexParcelRepository.listSitesOnHexBlocking(ownerId, destHexId);
        double[] ll = hexParcelRepository.apiaryPinForOwnerBlocking(ownerId, dest);
        String site = HexApiary.resolveSiteIdAt(dest, sites, dest.id, ll[0], ll[1]);
        ll = hexParcelRepository.apiaryPinForOwnerBlocking(ownerId, dest, site);
        int nHive = hexParcelRepository.countHivesAtSiteBlocking(ownerId, destHexId, site);
        if (nHive >= HexParcelGameRules.MAX_HIVES_PER_SITE) {
            return "Este apiario ya tiene " + HexParcelGameRules.MAX_HIVES_PER_SITE + " colmenas.";
        }
        h.inWarehouse = false;
        h.returnToWarehouse = false;
        h.hexId = dest.id;
        h.lat = ll[0];
        h.lng = ll[1];
        assignHiveSiteFromPoint(h, dest, ll[0], ll[1]);
        h.transhumanceArrivesDayKey = 0;
        if (h.floraType == null || h.floraType.isEmpty()) {
            h.floraType = HexFlora.nativeFloraForParcel(dest);
        }
        h.elevationMeters = OpenMeteoElevation.resolveMetersPersistedBlocking(h.lat, h.lng);
        saveHiveBlocking(h);
        return null;
    }

    /**
     * Compra 1 o 2 alzas (50 € cada una); máximo {@code 2} por colmena en total.
     */
    public void purchaseSupersForHive(String hiveId, String ownerId, int toBuy,
            Consumer<String> onMainMessage) {
        if (onMainMessage != null) {
            mainHandler.post(() -> onMainMessage.accept("Las alzas ya no forman parte del juego."));
        }
    }

    private static void applyDestFlora(@NonNull HiveEntity hive, @Nullable String destFlora) {
        if (destFlora == null || destFlora.trim().isEmpty()) {
            return;
        }
        hive.floraType = HoneyMarketEngine.canonicalFloraKey(destFlora);
    }

    public void transhumanceValidated(

            HiveEntity hive, double lat, double lng, @Nullable String destFlora, Consumer<String> onMainMessage) {

        if (GameServer.enabled() && !GameServer.isAvailable()) {
            mainHandler.post(() -> onMainMessage.accept(
                    "No hay conexión con el servidor. No se puede realizar esta acción."));
            return;
        }
        if (hive == null || hive.id == null) {

            mainHandler.post(() -> onMainMessage.accept("Colmena no válida."));

            return;

        }

        ioExecutor.execute(() -> {

            try {

                HiveEntity h = hiveDao.getHiveByIdSync(hive.id);

                if (h == null) {

                    mainHandler.post(() -> onMainMessage.accept("Colmena no encontrada."));

                    return;

                }

                if (h.inWarehouse) {
                    mainHandler.post(() -> onMainMessage.accept(
                            "Esa colmena está en el obrador. Colócala en un apiario primero."));
                    return;
                }

                if (TranshumanceRules.hasPendingContractMove(h)) {
                    mainHandler.post(() -> onMainMessage.accept(
                            "Esta colmena ya tiene una transhumancia programada para el cálculo diario."));
                    return;
                }
                if (TruckLiveTrips.hasActive(appContext, h.id)) {
                    mainHandler.post(() -> onMainMessage.accept(
                            "Esta colmena aún está de camino. Espera a que llegue."));
                    return;
                }
                if (h.linkedToContract()) {
                    mainHandler.post(() -> onMainMessage.accept(
                            "Esta colmena está en un contrato de polinización. Espera a que vuelva sola."));
                    return;
                }

                HexParcel hex = IberiaHexOverlayStore.findContaining(appContext, lat, lng);

                if (hex == null) {

                    mainHandler.post(() -> onMainMessage.accept("Destino fuera de terrenos jugables."));

                    return;

                }

                HexParcel hexAtOldPos = IberiaHexOverlayStore.findContaining(appContext, h.lat, h.lng);
                boolean sameHex = (h.hexId != null && hex.id.equals(h.hexId))
                        || (h.hexId == null && hexAtOldPos != null && hex.id.equals(hexAtOldPos.id));
                if (sameHex) {
                    List<HexParcelOwnershipEntity> sites = hexParcelRepository.listSitesOnHexBlocking(
                            h.ownerId, hex.id);
                    String destinationSite = HexApiary.resolveSiteIdAt(hex, sites, hex.id, lat, lng);
                    int nHive = hexParcelRepository.countHivesAtSiteBlocking(
                            h.ownerId, hex.id, destinationSite);
                    if (nHive >= HexParcelGameRules.MAX_HIVES_PER_SITE
                            && !HexApiary.sameSite(destinationSite, h.siteId)) {
                        mainHandler.post(() -> onMainMessage.accept(
                                "Ese apiario ya tiene " + HexParcelGameRules.MAX_HIVES_PER_SITE + " colmenas."));
                        return;
                    }
                    h.lat = lat;
                    h.lng = lng;
                    if (h.hexId == null) {
                        h.hexId = hex.id;
                    }
                    assignHiveSiteFromPoint(h, hex, lat, lng);
                    h.elevationMeters = OpenMeteoElevation.resolveMetersPersistedBlocking(h.lat, h.lng);
                    applyDestFlora(h, destFlora);
                    saveHiveBlocking(h);
                    mainHandler.post(() -> onMainMessage.accept(null));
                    return;
                }

                if (hexParcelRepository.hasOwnerSync(hex.id, h.ownerId)) {
                    // terreno propio
                } else if (pollinationContractRepository != null
                        && pollinationContractRepository.getOpenForOwnerAndHexSync(h.ownerId, hex.id) != null) {
                    String err = pollinationContractRepository.addHivesBlocking(
                            h.ownerId, hex.id, Collections.singletonList(h.id));
                    mainHandler.post(() -> onMainMessage.accept(err));
                    return;
                } else {
                    mainHandler.post(() -> onMainMessage.accept("El destino no es un terreno tuyo."));
                    return;
                }

                List<HexParcelOwnershipEntity> destinationSites = hexParcelRepository.listSitesOnHexBlocking(
                        h.ownerId, hex.id);
                String destinationSite = HexApiary.resolveSiteIdAt(
                        hex, destinationSites, hex.id, lat, lng);
                int nHive = hexParcelRepository.countHivesAtSiteBlocking(
                        h.ownerId, hex.id, destinationSite);

                if (nHive >= HexParcelGameRules.MAX_HIVES_PER_SITE) {

                    mainHandler.post(() -> onMainMessage.accept(

                            "Ese apiario ya tiene " + HexParcelGameRules.MAX_HIVES_PER_SITE + " colmenas."));

                    return;

                }

                h.siteId = destinationSite;
                int cost = TranshumanceRules.costEuros(h.lat, h.lng, lat, lng);
                String hiveLabel = h.name == null || h.name.trim().isEmpty()
                        ? "la colmena" : h.name.trim();
                String moveConcept = "Transhumancia de " + hiveLabel;
                if (!economyRepository.trySpend(cost, moveConcept)) {
                    mainHandler.post(() -> onMainMessage.accept(
                            economyRepository.blockedReason(cost) + " para la transhumancia."));
                    return;
                }
                try {
                    TruckLiveTrips.StartResult trip = TruckLiveTrips.start(
                            appContext, h, lat, lng, hex.id);
                    if (trip == TruckLiveTrips.StartResult.ALREADY_TRAVELING) {
                        economyRepository.addToBalance(cost, "Devolución de la transhumancia");
                        mainHandler.post(() -> onMainMessage.accept(
                                "Esta colmena aún está de camino. Espera a que llegue."));
                        return;
                    }
                    if (trip == TruckLiveTrips.StartResult.NO_TRUCK) {
                        economyRepository.addToBalance(cost, "Devolución de la transhumancia");
                        mainHandler.post(() -> onMainMessage.accept(
                                "No hay un camión libre con hueco de colmena. Cómpralo o espera a que vuelva."));
                        return;
                    }
                    if (trip != TruckLiveTrips.StartResult.STARTED) {
                        h.lat = lat;
                        h.lng = lng;
                        h.hexId = hex.id;
                        assignHiveSiteFromPoint(h, hex, lat, lng);
                        h.elevationMeters = OpenMeteoElevation.resolveMetersPersistedBlocking(h.lat, h.lng);
                        applyDestFlora(h, destFlora);
                    } else {
                        TruckLiveTrips.attachDestFlora(appContext, h.id, destFlora);
                    }
                    h.reserves = Math.max(0, h.reserves - 15);
                    h.health = Math.max(0, h.health - 2);
                    h.transhumanceArrivesDayKey = 0;
                    saveHiveBlocking(h);
                    grantXp(h.ownerId, XpAwards.transhumance(1));
                    mainHandler.post(() -> onMainMessage.accept(null));
                } catch (Exception e) {
                    economyRepository.addToBalance(cost, "Devolución de la transhumancia");
                    throw e;
                }

            } catch (Exception e) {

                Log.e(TAG, "transhumanceValidated", e);

                mainHandler.post(() -> onMainMessage.accept(formatThrowableForUser(e)));

            }

        });

    }

    /**
     * La colmena nueva se lleva un 30–60 % aleatorio de adultos, cría, miel y reservas; la original conserva el resto.
     * Salud e infestación de varroa (y días de tratamiento/rebote) se copian iguales en ambas colmenas.
     * <p>
     * La colmena nueva tiene otro {@code id}: no hereda filas de {@code hive_daily_yield} ni documentos
     * {@code dailyYields} en Firestore (historial de producción vacío). Los campos de último resumen diario
     * ({@code lastSummary*}) se ponen a cero; la madre conserva historial y último resumen.
     */
    /**
     * Compra un núcleo vacío en el terreno de la madre y pasa una fracción de obreras/miel.
     */
    public void splitHiveIntoEmptyNuc(
            String parentHiveId,
            String ownerId,
            String hexId,
            String floraType,
            String hiveName,
            int superCount,
            Consumer<String> onMainMessage) {
        if (parentHiveId == null || parentHiveId.isEmpty()) {
            if (onMainMessage != null) {
                mainHandler.post(() -> onMainMessage.accept("Colmena no válida."));
            }
            return;
        }
        ioExecutor.execute(() -> {
            try {
                String err = trySplitHiveIntoEmptyNucBlocking(
                        parentHiveId, ownerId, hexId, floraType, hiveName, superCount,
                        null, null, null);
                if (onMainMessage != null) {
                    mainHandler.post(() -> onMainMessage.accept(err));
                }
            } catch (Exception e) {
                Log.e(TAG, "splitHiveIntoEmptyNuc", e);
                if (onMainMessage != null) {
                    mainHandler.post(() -> onMainMessage.accept(formatThrowableForUser(e)));
                }
            }
        });
    }

    public void splitHiveHalf(String hiveId, Consumer<String> onMainMessage) {
        if (onMainMessage != null) {
            mainHandler.post(() -> onMainMessage.accept(
                    "Abre la ficha de la colmena y usa Dividir para comprar el núcleo."));
        }
    }

    /**
     * Divide varias colmenas en serie (mismo hilio de fondo). {@code onMainMessage}: {@code null} si todas OK;
     * si no, texto resumido (parciales o error).
     */
    public void splitHivesSequentialBlockingOrder(List<String> hiveIds, Consumer<String> onMainMessage) {
        if (hiveIds == null || hiveIds.isEmpty()) {
            if (onMainMessage != null) {
                mainHandler.post(() -> onMainMessage.accept(null));
            }
            return;
        }
        ioExecutor.execute(() -> {
            try {
                LinkedHashSet<String> unique = new LinkedHashSet<>();
                for (String id : hiveIds) {
                    if (id != null && !id.trim().isEmpty()) {
                        unique.add(id.trim());
                    }
                }
                int attempted = unique.size();
                if (attempted == 0) {
                    if (onMainMessage != null) {
                        mainHandler.post(() -> onMainMessage.accept("No hay colmenas válidas."));
                    }
                    return;
                }
                if (onMainMessage != null) {
                    mainHandler.post(() -> onMainMessage.accept(
                            "Abre la ficha de cada colmena y usa Dividir para comprar el núcleo."));
                }
            } catch (Exception e) {
                Log.e(TAG, "splitHivesSequentialBlockingOrder", e);
                if (onMainMessage != null) {
                    mainHandler.post(() -> onMainMessage.accept(formatThrowableForUser(e)));
                }
            }
        });
    }

    /**
     * @return {@code null} si la división tuvo éxito; mensaje para el usuario si no.
     */
    /**
     * Si el apiario de la madre está lleno, crea el núcleo en otro apiario y lo manda en el camión elegido.
     * El núcleo sale de donde está la madre.
     */
    public void splitAndSend(
            String parentHiveId,
            String ownerId,
            String floraType,
            String hiveName,
            String destHexId,
            String destSiteId,
            String vehicleId,
            Consumer<String> onMainMessage) {
        ioExecutor.execute(() -> {
            try {
                HiveEntity parent = hiveDao.getHiveByIdSync(parentHiveId);
                double[] stay = parent == null
                        ? null : new double[]{parent.lat, parent.lng};
                String[] created = new String[1];
                String err = trySplitHiveIntoEmptyNucBlocking(
                        parentHiveId, ownerId, destHexId, floraType, hiveName, 0,
                        destSiteId, stay, created);
                if (err == null && created[0] != null) {
                    HiveEntity child = hiveDao.getHiveByIdSync(created[0]);
                    HexParcel dest = IberiaHexOverlayStore.findById(appContext, destHexId);
                    double[] pin = hexParcelRepository.apiaryPinForOwnerBlocking(
                            ownerId, dest, destSiteId);
                    if (child != null) {
                        TruckLiveTrips.StartResult trip = TruckLiveTrips.startOnTruck(
                                appContext, child, pin[0], pin[1], destHexId, vehicleId);
                        if (trip == TruckLiveTrips.StartResult.STARTED) {
                            TruckLiveTrips.attachDestFlora(
                                    appContext, child.id, TruckTripEntity.SPLIT_MOVE);
                        } else {
                            child.lat = pin[0];
                            child.lng = pin[1];
                            saveHiveBlocking(child);
                            if (trip == TruckLiveTrips.StartResult.NO_TRUCK) {
                                err = appContext.getString(
                                        com.apiculture.simulator.R.string.hive_split_truck_taken);
                            }
                        }
                    }
                }
                if (onMainMessage != null) {
                    String msg = err;
                    mainHandler.post(() -> onMainMessage.accept(msg));
                }
            } catch (Exception e) {
                Log.e(TAG, "splitAndSend", e);
                if (onMainMessage != null) {
                    mainHandler.post(() -> onMainMessage.accept(formatThrowableForUser(e)));
                }
            }
        });
    }

    private String trySplitHiveIntoEmptyNucBlocking(
            String parentHiveId,
            String ownerId,
            String hexId,
            String floraType,
            String hiveName,
            int superCount,
            @Nullable String moveSiteId,
            @Nullable double[] stayAt,
            @Nullable String[] createdId) {
        if (parentHiveId == null || parentHiveId.isEmpty()) {
            return "Colmena no válida.";
        }
        double price = 0.0;
        boolean spent = false;
        try {
            HiveEntity h = hiveDao.getHiveByIdSync(parentHiveId);
            if (h == null) {
                return "Colmena no encontrada.";
            }
            if (ownerId == null || ownerId.isEmpty() || !ownerId.equals(h.ownerId)) {
                return "Esta colmena no es tuya.";
            }
            ensurePopulationJson(h);
            HivePopulationState s = HivePopulationState.fromJson(h.populationStateJson);
            if (s == null) {
                return "Estado de colonia no disponible.";
            }
            if (s.workersAdult < ColonyGameRules.MIN_BEES_TO_SPLIT) {
                return "Necesitas al menos 35.000 obreras adultas para dividir la colmena.";
            }
            String destHex = hexId != null && !hexId.isEmpty() ? hexId : h.hexId;
            if (destHex == null || destHex.isEmpty()) {
                return "Asigna un terreno a la colmena antes de dividirla.";
            }
            boolean relocating = moveSiteId != null && !moveSiteId.isEmpty();
            if (!relocating && (h.hexId == null || !destHex.equals(h.hexId))) {
                return "El núcleo se coloca en el mismo terreno que la colmena madre.";
            }
            if (!hexParcelRepository.hasOwnerSync(destHex, h.ownerId)) {
                return "Instala un apiario aquí para colocar colmenas.";
            }
            String trimmedName = hiveName == null ? "" : hiveName.trim();
            if (trimmedName.isEmpty()) {
                return "Escribe un nombre para la colmena.";
            }
            String nameToSave = trimmedName.length() > 120 ? trimmedName.substring(0, 120) : trimmedName;
            if (floraType == null || floraType.trim().isEmpty()) {
                return "Elige un tipo de flora.";
            }
            long nowMs = System.currentTimeMillis();
            String flora = HoneyMarketEngine.canonicalFloraKey(floraType);
            if (!relocating && !hexFloraRepository.isFloraReadyOnHexBlocking(destHex, flora, nowMs)) {
                return "Esa flora no está disponible aún en este terreno.";
            }
            String siteForCount = relocating ? moveSiteId : h.siteId;
            if (hexParcelRepository.countHivesAtSiteBlocking(
                    h.ownerId, destHex, siteForCount) >= HexParcelGameRules.MAX_HIVES_PER_SITE) {
                return "Este apiario ya tiene el máximo de colmenas permitidas.";
            }
            HexParcel hex = IberiaHexOverlayStore.findById(appContext, destHex);
            if (hex == null) {
                return "Terreno no encontrado en el mapa.";
            }
            int supers = 0;
            price = HiveCareRules.emptyNucPriceEuros(supers);
            if (!economyRepository.trySpend(price, "Núcleo " + nameToSave)) {
                return economyRepository.blockedReason(price);
            }
            spent = true;
            double spawnShare = HiveCareRules.randomSplitSpawnShare();
            HivePopulationState spawnPop = s.splitOffFraction(spawnShare);
            h.populationStateJson = s.toJson();
            h.beeCount = s.totalBees();
            double[] ll = stayAt != null
                    ? stayAt
                    : hexParcelRepository.apiaryPinForOwnerBlocking(h.ownerId, hex, siteForCount);
            HiveEntity nu = new HiveEntity();
            nu.id = UUID.randomUUID().toString();
            nu.ownerId = h.ownerId;
            nu.name = nameToSave;
            nu.lat = ll[0];
            nu.lng = ll[1];
            nu.hexId = destHex;
            nu.siteId = siteForCount;
            nu.floraType = flora;
            nu.superCount = supers;
            nu.elevationMeters = OpenMeteoElevation.resolveMetersPersistedBlocking(nu.lat, nu.lng);
            nu.populationStateJson = spawnPop.toJson();
            nu.beeCount = spawnPop.totalBees();
            nu.queenAgeDays = h.queenAgeDays;
            nu.queenGeneticQuality = h.queenGeneticQuality;
            nu.health = h.health;
            nu.varroaPct = h.varroaPct;
            nu.varroaTreatmentDaysRemaining = h.varroaTreatmentDaysRemaining;
            nu.varroaReboundDaysRemaining = h.varroaReboundDaysRemaining;
            nu.lastHealthSimDayKey = h.lastHealthSimDayKey;
            nu.lastSummaryDayKey = 0;
            nu.lastSummaryHoneyKg = 0.0;
            nu.lastSummaryDeltaBees = 0;
            nu.lastSummaryDeltaHealth = 0;
            nu.lastSummaryDeltaVarroa = 0.0;
            nu.lastSummaryWorkerDeaths = 0;
            nu.lastSummaryWorkerEmergences = 0;
            nu.lastSummaryEggsLaid = 0;
            nu.lastSummarySwarmed = false;
            stampFirstProductionDay(nu);
            HiveHoneyStocks.splitProportionally(h, nu, spawnShare);
            int rSpawn = (int) Math.round(h.reserves * spawnShare);
            rSpawn = Math.max(0, Math.min(h.reserves, rSpawn));
            nu.reserves = rSpawn;
            h.reserves -= rSpawn;
            nu.varroaPct = Math.max(0.0, nu.varroaPct);
            h.varroaPct = Math.max(0.0, h.varroaPct);
            nu.honeyProduction = Math.max(0.0, nu.honeyProduction);
            h.honeyProduction = Math.max(0.0, h.honeyProduction);
            h.reserves = Math.max(0, h.reserves);
            nu.reserves = Math.max(0, nu.reserves);
            HiveHoneyRules.clampHoneyStockToCap(h);
            HiveHoneyRules.clampHoneyStockToCap(nu);
            normalizeBeeCount(h);
            normalizeBeeCount(nu);
            upsertCloud(h);
            upsertCloud(nu);
            if (firestore != null) {
                Tasks.await(firestore.collection("hives").document(h.id).set(h, SetOptions.merge()));
                Tasks.await(firestore.collection("hives").document(nu.id).set(nu, SetOptions.merge()));
            }
            grantXp(h.ownerId, XpAwards.BUY_HIVE);
            if (createdId != null) {
                createdId[0] = nu.id;
            }
            return null;
        } catch (Exception e) {
            if (spent && price > 0.0) {
                try {
                    economyRepository.addToBalance(price, "Devolución del núcleo");
                } catch (Exception ignored) {
                }
            }
            Log.e(TAG, "trySplitHiveIntoEmptyNucBlocking", e);
            return formatThrowableForUser(e);
        }
    }

    public void listOwnedTerrainsForHivePurchaseAsync(
            String ownerId, Consumer<List<OwnedHexOption>> onMain) {
        if (onMain == null) {
            return;
        }
        if (ownerId == null || ownerId.isEmpty()) {
            mainHandler.post(() -> onMain.accept(Collections.emptyList()));
            return;
        }
        ioExecutor.execute(() -> {
            try {
                List<OwnedHexOption> out = new ArrayList<>();
                for (String hexId : hexParcelRepository.listOwnedHexIdsSync(ownerId)) {
                    String label = hexParcelRepository.getParcelDisplayNameSync(hexId);
                    out.add(new OwnedHexOption(hexId, label));
                }
                out.sort(Comparator.comparing(o -> o.label));
                mainHandler.post(() -> onMain.accept(out));
            } catch (Exception e) {
                Log.e(TAG, "listOwnedTerrainsForHivePurchaseAsync", e);
                mainHandler.post(() -> onMain.accept(Collections.emptyList()));
            }
        });
    }

    public void floraSaturationAsync(
            @Nullable String hexId,
            @Nullable String floraKey,
            Consumer<HexFloraSaturation> onMain) {
        if (onMain == null) {
            return;
        }
        ioExecutor.execute(() -> {
            HexFloraSaturation sat = floraSaturationBlocking(hexId, floraKey);
            mainHandler.post(() -> onMain.accept(sat));
        });
    }

    @NonNull
    public HexFloraSaturation floraSaturationBlocking(@Nullable String hexId, @Nullable String floraKey) {
        if (hexId == null || hexId.isEmpty()) {
            return new HexFloraSaturation(0, 0, 0);
        }
        try {
            HexParcel parcel = IberiaHexOverlayStore.findById(appContext, hexId);
            List<HiveEntity> hives = hiveDao.getByHexIdSync(hexId);
            LocalDate day = LocalDate.now(GameCalendar.userTimeZone());
            return HexFloraSaturation.compute(parcel, floraKey, hives, day);
        } catch (Exception e) {
            Log.w(TAG, "floraSaturationBlocking", e);
            return new HexFloraSaturation(0, 0, 0);
        }
    }

    @NonNull
    public List<HexFloraSaturation.Line> floraSaturationLinesBlocking(@Nullable String hexId) {
        if (hexId == null || hexId.isEmpty()) {
            return Collections.emptyList();
        }
        try {
            HexParcel parcel = IberiaHexOverlayStore.findById(appContext, hexId);
            List<HiveEntity> hives = hiveDao.getByHexIdSync(hexId);
            LocalDate day = LocalDate.now(GameCalendar.userTimeZone());
            LinkedHashSet<String> keys = new LinkedHashSet<>();
            for (String k : HexFlora.nativeMixForParcel(parcel)) {
                if (k != null && !k.trim().isEmpty()) {
                    keys.add(HoneyMarketEngine.canonicalFloraKey(k));
                }
            }
            for (HexParcelFloraEntity e : hexFloraRepository.listEntriesForHexBlocking(hexId)) {
                if (e != null && e.floraKey != null && !e.floraKey.trim().isEmpty()) {
                    keys.add(HoneyMarketEngine.canonicalFloraKey(e.floraKey));
                }
            }
            if (keys.isEmpty()) {
                String one = HexFlora.nativeFloraForParcel(parcel);
                if (one != null && !one.trim().isEmpty()) {
                    keys.add(HoneyMarketEngine.canonicalFloraKey(one));
                }
            }
            return HexFloraSaturation.computeLines(parcel, new ArrayList<>(keys), hives, day);
        } catch (Exception e) {
            Log.w(TAG, "floraSaturationLinesBlocking", e);
            return Collections.emptyList();
        }
    }

    public void listReadyFlorasForHexAsync(String hexId, @Nullable String siteId, Consumer<List<String>> onMain) {
        if (onMain == null) {
            return;
        }
        ioExecutor.execute(() -> {
            try {
                long nowMs = System.currentTimeMillis();
                List<String> keys = hexFloraRepository.listReadyFloraKeysBlocking(hexId, siteId, nowMs);
                if (hexParcelRepository != null && !keys.isEmpty()) {
                    hexParcelRepository.syncHexParcelFlorasToCloudBlocking(hexId);
                }
                mainHandler.post(() -> onMain.accept(keys));
            } catch (Exception e) {
                Log.e(TAG, "listReadyFlorasForHexAsync", e);
                mainHandler.post(() -> onMain.accept(Collections.emptyList()));
            }
        });
    }

    private void reassignForageAfterRemovedCrops(String ownerId,
            CropTickResult cropTick) {
        if (cropTick == null || cropTick.removed.isEmpty()) {
            return;
        }
        long nowMs = System.currentTimeMillis();
        List<HiveEntity> hives = hiveDao.getHivesByOwnerSync(ownerId);
        for (HiveEntity h : hives) {
            if (h == null || h.hexId == null) {
                continue;
            }
            String current = HoneyMarketEngine.canonicalFloraKey(h.floraType);
            for (CropTickResult.Removed r : cropTick.removed) {
                if (h.hexId.equals(r.hexId) && current.equals(r.floraKey)) {
                    List<String> ready = hexFloraRepository.listReadyFloraKeysBlocking(h.hexId, h.siteId, nowMs);
                    h.floraType = ready.isEmpty() ? HexFlora.MIL_FLORES : ready.get(0);
                    try {
                        saveHiveBlocking(h);
                    } catch (Exception e) {
                        Log.e(TAG, "reassignForageAfterRemovedCrops", e);
                    }
                    break;
                }
            }
        }
    }

    public void changeHiveForageFloraAsync(String hiveId, String floraKey, Consumer<String> onMainMessage) {
        if (onMainMessage == null) {
            return;
        }
        ioExecutor.execute(() -> {
            try {
                HiveEntity h = hiveDao.getHiveByIdSync(hiveId);
                if (h == null) {
                    mainHandler.post(() -> onMainMessage.accept("Colmena no encontrada."));
                    return;
                }
                String want = HoneyMarketEngine.canonicalFloraKey(floraKey);
                long nowMs = System.currentTimeMillis();
                if (h.hexId == null || !hexFloraRepository.isFloraReadyOnHexBlocking(h.hexId, h.siteId, want, nowMs)) {
                    mainHandler.post(() -> onMainMessage.accept(
                            "Esa flora no está lista en este terreno."));
                    return;
                }
                h.floraType = want;
                saveHiveBlocking(h);
                mainHandler.post(() -> onMainMessage.accept(null));
            } catch (Exception e) {
                Log.e(TAG, "changeHiveForageFloraAsync", e);
                mainHandler.post(() -> onMainMessage.accept(formatThrowableForUser(e)));
            }
        });
    }

    public void loadFloraPlantingsInProgressAsync(
            String ownerId, Consumer<List<FloraPlantingProgressRow>> onMain) {
        if (onMain == null) {
            return;
        }
        if (ownerId == null || ownerId.isEmpty()) {
            mainHandler.post(() -> onMain.accept(Collections.emptyList()));
            return;
        }
        ioExecutor.execute(() -> {
            try {
                List<FloraPlantingProgressRow> rows =
                        hexFloraRepository.listGrowingPlantingsForOwnerBlocking(
                                ownerId, hexParcelRepository, System.currentTimeMillis());
                mainHandler.post(() -> onMain.accept(rows));
            } catch (Exception e) {
                Log.e(TAG, "loadFloraPlantingsInProgressAsync", e);
                mainHandler.post(() -> onMain.accept(Collections.emptyList()));
            }
        });
    }

    public void plantAdditionalFloraAsync(
            String hexId,
            String ownerId,
            String floraKey,
            @Nullable String siteId,
            Consumer<String> onMain) {
        if (onMain == null) {
            return;
        }
        ioExecutor.execute(() -> {
            try {
                int playerLevel = playerProgressRepository != null
                        ? playerProgressRepository.getLevel(ownerId)
                        : 0;
                String site = siteId;
                if (site == null || site.isEmpty()) {
                    java.util.List<HexParcelOwnershipEntity> sites =
                            hexParcelRepository.listSitesOnHexBlocking(ownerId, hexId);
                    site = sites.isEmpty() || sites.get(0).siteId == null ? "" : sites.get(0).siteId;
                }
                String msg = hexFloraRepository.plantAdditionalFloraBlocking(
                        hexId,
                        floraKey,
                        ownerId,
                        site,
                        economyRepository,
                        System.currentTimeMillis(),
                        hexParcelRepository,
                        playerLevel);
                if (msg == null) {
                    hexParcelRepository.syncHexParcelFlorasToCloudBlocking(hexId);
                    grantXp(ownerId, XpAwards.PLANT_FLORA);
                }
                mainHandler.post(() -> onMain.accept(msg));
            } catch (Exception e) {
                Log.e(TAG, "plantAdditionalFloraAsync", e);
                mainHandler.post(() -> onMain.accept(formatThrowableForUser(e)));
            }
        });
    }

    /** Pago al payés por la poda y el abonado de un frutal; {@code null} si fue bien. */
    public void maintainTreeAsync(String hexId, String ownerId, String floraKey, @Nullable String siteId,
            Consumer<String> onMain) {
        if (onMain == null) {
            return;
        }
        ioExecutor.execute(() -> {
            try {
                String msg = hexFloraRepository.maintainTreeBlocking(hexId, floraKey, ownerId, siteId, economyRepository,
                        hexParcelRepository, LocalDate.now(GameCalendar.userTimeZone()));
                if (msg == null) {
                    hexParcelRepository.syncHexParcelFlorasToCloudBlocking(hexId);
                }
                mainHandler.post(() -> onMain.accept(msg));
            } catch (Exception e) {
                Log.e(TAG, "maintainTreeAsync", e);
                mainHandler.post(() -> onMain.accept(formatThrowableForUser(e)));
            }
        });
    }

    /** Pep compra el fruto del apiario; {@code null} si fue bien. */
    public void sellFruitAsync(String hexId, String ownerId, String floraKey, @Nullable String siteId,
            Consumer<String> onMain) {
        if (onMain == null) {
            return;
        }
        ioExecutor.execute(() -> {
            try {
                String msg = hexFloraRepository.sellFruitBlocking(hexId, floraKey, ownerId, siteId, economyRepository,
                        hexParcelRepository, LocalDate.now(GameCalendar.userTimeZone()));
                if (msg == null) {
                    hexParcelRepository.syncHexParcelFlorasToCloudBlocking(hexId);
                }
                mainHandler.post(() -> onMain.accept(msg));
            } catch (Exception e) {
                Log.e(TAG, "sellFruitAsync", e);
                mainHandler.post(() -> onMain.accept(formatThrowableForUser(e)));
            }
        });
    }

    /** Terrenos con frutales sin mantener (punto rojo y aviso del apiario). */
    public void hexesNeedingMaintenanceAsync(String ownerId, Consumer<java.util.Set<String>> onMain) {
        if (onMain == null) {
            return;
        }
        ioExecutor.execute(() -> {
            java.util.Set<String> out;
            try {
                out = hexFloraRepository.hexesNeedingMaintenanceBlocking(ownerId,
                        LocalDate.now(GameCalendar.userTimeZone()));
            } catch (Exception e) {
                Log.e(TAG, "hexesNeedingMaintenanceAsync", e);
                out = java.util.Collections.emptySet();
            }
            java.util.Set<String> result = out;
            mainHandler.post(() -> onMain.accept(result));
        });
    }

    public void listAllFloraKeysOnHexAsync(String hexId, Consumer<List<String>> onMain) {
        if (onMain == null) {
            return;
        }
        ioExecutor.execute(() -> {
            try {
                List<String> keys = new ArrayList<>();
                for (HexParcelFloraEntity e : hexFloraRepository.listEntriesForHexBlocking(hexId)) {
                    keys.add(HoneyMarketEngine.canonicalFloraKey(e.floraKey));
                }
                mainHandler.post(() -> onMain.accept(keys));
            } catch (Exception e) {
                Log.e(TAG, "listAllFloraKeysOnHexAsync", e);
                mainHandler.post(() -> onMain.accept(Collections.emptyList()));
            }
        });
    }

}


