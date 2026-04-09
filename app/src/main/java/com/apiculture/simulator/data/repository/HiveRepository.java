package com.apiculture.simulator.data.repository;



import android.content.Context;

import android.content.SharedPreferences;

import android.os.Handler;

import android.os.Looper;

import android.util.Log;



import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;



import com.apiculture.simulator.data.local.dao.GameProductionStateDao;

import com.apiculture.simulator.data.local.dao.HiveDailyYieldDao;

import com.apiculture.simulator.data.local.dao.HiveDao;

import com.apiculture.simulator.data.local.entity.GameProductionStateEntity;

import com.apiculture.simulator.data.local.entity.HiveDailyYieldEntity;

import com.apiculture.simulator.data.local.entity.HiveEntity;

import com.apiculture.simulator.data.remote.OpenMeteoElevation;

import com.apiculture.simulator.domain.parcel.HexFlora;
import com.apiculture.simulator.domain.parcel.HexGeometry;
import com.apiculture.simulator.domain.parcel.HexParcel;
import com.apiculture.simulator.domain.parcel.HexParcelGameRules;
import com.apiculture.simulator.domain.parcel.HexParcelPointInPolygon;
import com.apiculture.simulator.domain.parcel.HexParcelRandomPoint;
import com.apiculture.simulator.domain.parcel.HexParcelResolve;
import com.apiculture.simulator.domain.parcel.LocalTangentPlane;
import com.apiculture.simulator.domain.game.DailyHoneyConsumption;
import com.apiculture.simulator.domain.game.GameBalanceEngine;
import com.apiculture.simulator.domain.game.GameClock;
import com.apiculture.simulator.domain.game.DailySkyCondition;
import com.apiculture.simulator.domain.game.GameCalendar;
import com.apiculture.simulator.domain.game.ColonyGameRules;

import com.apiculture.simulator.domain.game.HealthHoneyModifier;
import com.apiculture.simulator.domain.game.HiveHoneyRules;
import com.apiculture.simulator.domain.game.HoneyDailyProduction;
import com.apiculture.simulator.domain.game.Season;
import com.apiculture.simulator.domain.game.TemperatureHoneyModifier;
import com.apiculture.simulator.domain.health.HiveDailyHealthSimulator;
import com.apiculture.simulator.domain.population.HivePopulationSimulator;
import com.apiculture.simulator.domain.population.HivePopulationState;
import com.apiculture.simulator.domain.population.HivePopulationTrend;
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

import com.apiculture.simulator.presentation.map.MapHexOverlayConfig;

import java.time.LocalDate;

import java.time.LocalDateTime;

import java.time.ZoneId;

import java.time.format.DateTimeFormatter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
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



    private static final String MIGRATION_PREFS = "apiculture_migrations";

    private static final String KEY_BROOD_PIPELINE_RESEED = "brood_pipeline_reseed_v1";



    /** Si falta en Firestore o es 0, Room/Firestore deserializan 0; unificamos con el valor de juego. */

    public static final int DEFAULT_BEE_COUNT_PER_HIVE = 25_000;

    /** Total de abejas por colmena tras «Reiniciar juego» (reino + cría = este total). */

    public static final int STARTER_GAME_TOTAL_BEES = 25_000;

    /**
     * Primera colmena al reiniciar: total colonia (adultos + cría). Las obreras adultas van al tope
     * {@link ColonyGameRules#MAX_ADULT_WORKERS_PER_HIVE} para probar enjambrazón y división.
     */
    public static final int STARTER_GAME_FIRST_HIVE_TOTAL_BEES = 95_000;

    public static final int STARTER_GAME_FIRST_HIVE_ADULT_WORKERS =
            ColonyGameRules.MAX_ADULT_WORKERS_PER_HIVE;



    private final HiveDao hiveDao;

    private final HiveDailyYieldDao hiveDailyYieldDao;

    private final GameProductionStateDao gameProductionStateDao;

    private final WeatherRepository weatherRepository;

    private final HexParcelRepository hexParcelRepository;

    private final HexFloraRepository hexFloraRepository;

    private final EconomyRepository economyRepository;

    private final MarketRepository marketRepository;

    private final FirebaseFirestore firestore;

    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private ListenerRegistration cloudListener;

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

        FirebaseFirestore instance;

        try {

            instance = FirebaseFirestore.getInstance();

        } catch (Exception e) {

            instance = null;

        }

        firestore = instance;

    }



    /**

     * Una sola vez: actualiza colmenas locales (y Firestore en la nube si hay sesión) con tubería de cría completa.

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

                        hiveDao.upsert(h);

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
            return new java.util.ArrayList<>();
        }
        List<HiveEntity> list = hiveDao.getHivesByOwnerSync(ownerId);
        return list != null ? list : new java.util.ArrayList<>();
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

            hiveDao.upsert(hive);

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

                hive.queenAgeDays = 120;

                hive.queenGeneticQuality = 75;

                hive.lat = 40.4168;

                hive.lng = -3.7038;

                List<HexParcel> parcels = IberiaHexOverlayStore.getParcels(appContext);

                HexParcel hex = HexParcelResolve.findContaining(parcels, hive.lat, hive.lng);

                if (hex != null) {

                    hive.hexId = hex.id;

                    hive.floraType = hexFloraRepository.getOrCreateFloraForHexBlocking(hex.id);

                } else {

                    hive.floraType = HexFlora.FLORA_TYPES[0];

                }

                hive.superCount = 0;

                hive.honeyProduction = 4.0 + Math.random() * 4.0;

                hive.varroaPct = 2.0;

                hive.varroaTreatmentDaysRemaining = 0;

                hive.varroaReboundDaysRemaining = 0;

                hive.lastHealthSimDayKey = 0;

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

            hiveDao.upsert(h);

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

        saveHive(hive);

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

            hiveDao.upsert(h);

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



    public void deleteHive(String hiveId) {

        ioExecutor.execute(() -> hiveDao.deleteById(hiveId));

        if (firestore != null) {

            firestore.collection("hives").document(hiveId).delete();

        }

    }



    /**

     * Elimina todas las colmenas del dueño (local + Firestore + {@code dailyYields}), reinicia producción

     * y crea 3 colmenas iniciales (≈25.000 abejas cada una). Detiene la escucha en tiempo real hasta que

     * la UI vuelva a llamar a {@link #startRealtimeCloudSync(String)} con el {@code uid} del jugador.

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

                economyRepository.applyNewGameEconomyDefaults();

                List<HiveEntity> old = hiveDao.getHivesByOwnerSync(ownerId);

                for (HiveEntity h : old) {

                    if (h == null || h.id == null) {

                        continue;

                    }

                    hiveDailyYieldDao.deleteAllForHive(h.id);

                    deleteFirestoreDailyYieldsBlocking(h.id);

                    if (firestore != null) {

                        try {

                            Tasks.await(firestore.collection("hives").document(h.id).delete());

                        } catch (Exception e) {

                            Log.w(TAG, "No se pudo borrar colmena en la nube: " + h.id, e);

                        }

                    }

                    hiveDao.deleteById(h.id);

                }

                int todayKey = GameCalendar.toDayKey(LocalDate.now(GameCalendar.userTimeZone()));

                GameProductionStateEntity state = gameProductionStateDao.getByOwner(ownerId);

                if (state == null) {

                    state = new GameProductionStateEntity();

                    state.ownerId = ownerId;

                }

                state.gameStartDayKey = todayKey;

                state.lastProcessedProductionDayKey = 0;

                gameProductionStateDao.insert(state);

                pushProductionStateToFirestore(ownerId, state);

                hexParcelRepository.removeAllOwnershipForOwnerBlocking(ownerId);

                List<HexParcel> parcels = IberiaHexOverlayStore.getParcels(appContext);

                HexParcel starter = HexParcelResolve.findContaining(parcels, 41.3874, 2.1686);

                if (starter == null) {

                    throw new IllegalStateException("No hay terreno jugable cerca de Barcelona.");

                }

                hexParcelRepository.seedOwnershipLocalPreferCloud(starter.id, ownerId,
                        HexParcelRepository.newDefaultTerrenoName());

                String flora = hexFloraRepository.getOrCreateFloraForHexBlocking(starter.id);

                double[][] coords = new double[3][2];

                starterHiveLatLngOffsets(starter, coords);

                String[] names = new String[]{"Colmena 1", "Colmena 2", "Colmena 3"};

                int[] starterBeeTotals = new int[]{
                        STARTER_GAME_FIRST_HIVE_TOTAL_BEES,
                        STARTER_GAME_TOTAL_BEES,
                        STARTER_GAME_TOTAL_BEES,
                };

                for (int i = 0; i < 3; i++) {

                    persistHiveForReset(buildStarterHiveEntity(

                            ownerId, names[i], flora, starter.id, coords[i][0], coords[i][1],
                            starterBeeTotals[i]),
                            i == 0 ? Integer.valueOf(STARTER_GAME_FIRST_HIVE_ADULT_WORKERS) : null);

                }

                mainHandler.post(() -> onMainMessage.accept(null));

            } catch (Exception e) {

                Log.e(TAG, "resetGameToStarterState", e);

                String detail = formatThrowableForUser(e);

                mainHandler.post(() -> onMainMessage.accept(detail));

            }

        });

    }



    private void deleteFirestoreDailyYieldsBlocking(String hiveId) {

        if (firestore == null || hiveId == null) {

            return;

        }

        try {

            QuerySnapshot qs = Tasks.await(firestore.collection("hives").document(hiveId)

                    .collection("dailyYields").get());

            for (QueryDocumentSnapshot doc : qs) {

                try {

                    Tasks.await(doc.getReference().delete());

                } catch (Exception e) {

                    Log.w(TAG, "No se pudo borrar dailyYields/" + doc.getId(), e);

                }

            }

        } catch (Exception e) {

            Log.w(TAG, "Listado/borrado dailyYields omitido para " + hiveId, e);

        }

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

        hiveDao.upsert(hive);

        if (firestore != null) {

            Tasks.await(firestore.collection("hives").document(hive.id).set(hive, SetOptions.merge()));

        }

    }



    private static void starterHiveLatLngOffsets(HexParcel hex, double[][] outLatLon) {

        double side = HexGeometry.sideMetersForAreaKm2(MapHexOverlayConfig.MAP_HEX_TARGET_AREA_KM2);

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

        hive.queenAgeDays = 120;

        hive.queenGeneticQuality = 75;

        hive.lat = lat;

        hive.lng = lng;

        hive.hexId = hexId;

        hive.floraType = flora;

        hive.superCount = 0;

        double initialHoneyKg = 4.0 + Math.random() * 4.0;

        hive.honeyProduction = initialHoneyKg;

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

        if (ownerId == null || ownerId.isEmpty()) {

            if (onMainThread != null) {

                mainHandler.post(() -> onMainThread.accept(TickAppliedDayResult.NONE));

            }

            return;

        }

        ioExecutor.execute(() -> {

            try {

                List<DailyTickSummary> appliedDays = new ArrayList<>();

                tickDailyProductionForOwnerSync(ownerId, appliedDays);

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

                if (onMainThread != null) {

                    mainHandler.post(() -> onMainThread.accept(TickAppliedDayResult.NONE));

                }

            }

        });

    }



    /**

     * Solo para depuración: aplica <strong>un</strong> día de producción (siguiente tras {@code lastProcessed}),

     * sin exigir las 8:00 locales ni el día de calendario actual. Permite comprobar miel, población, etc.

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

                    LocalDate lastProcessed = state.lastProcessedProductionDayKey == 0

                            ? gameStart.minusDays(1)

                            : GameCalendar.fromDayKey(state.lastProcessedProductionDayKey);

                    LocalDate next = lastProcessed.plusDays(1);

                    DateTimeFormatter df = DateTimeFormatter.ofPattern("dd/MM/yyyy");

                    if (next.isBefore(gameStart)) {

                        state.lastProcessedProductionDayKey = GameCalendar.toDayKey(next);

                        gameProductionStateDao.update(state);

                        pushProductionStateToFirestore(ownerId, state);

                        msg = "Avance sin producción (antes del día 1): " + next.format(df);

                    } else {

                        applyProductionForCalendarDay(ownerId, next, state);

                        msg = "Producción simulada · " + next.format(df);

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

     * Producción de miel: últimos 7 días de calendario incluyendo hoy (más antiguo → índice 0).

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

            mainHandler.post(() -> onMainThread.accept(new HiveLast6DaysCharts(null, null, null)));

            return;

        }

        ioExecutor.execute(() -> {

            HiveLast6DaysCharts charts = computeLast6DaysChartsSync(hiveId, ownerId);

            mainHandler.post(() -> onMainThread.accept(charts));

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

        GameProductionStateEntity state = gameProductionStateDao.getByOwner(ownerId);

        LocalDate gameStart = state != null

                ? GameCalendar.fromDayKey(state.gameStartDayKey)

                : today;

        for (int i = 0; i < 7; i++) {

            LocalDate d = today.minusDays(6 - i);

            if (d.isBefore(gameStart)) {

                honey[i] = 0.0;

                worker[i] = 0;

                eggs[i] = 0;

                continue;

            }

            int dayKey = GameCalendar.toDayKey(d);

            HiveDailyYieldEntity row = hiveDailyYieldDao.getYield(hiveId, dayKey);

            honey[i] = row != null ? row.kg : 0.0;

            worker[i] = row != null ? row.workerNetDelta : 0;

            eggs[i] = row != null ? row.eggsLaid : 0;

        }

        return new HiveLast6DaysCharts(honey, worker, eggs);

    }



    private GameProductionStateEntity getOrCreateProductionState(String ownerId) {

        GameProductionStateEntity s = gameProductionStateDao.getByOwner(ownerId);

        if (s == null) {

            s = new GameProductionStateEntity();

            s.ownerId = ownerId;

            s.gameStartDayKey = GameCalendar.toDayKey(LocalDate.now(GameCalendar.userTimeZone()));

            s.lastProcessedProductionDayKey = 0;

            gameProductionStateDao.insert(s);

            pushProductionStateToFirestore(ownerId, s);

        }

        return s;

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
            Tasks.await(firestore.collection("users").document(ownerId)
                    .collection("meta").document("productionState")
                    .set(m, SetOptions.merge()));
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
            return e;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Temperatura media del día de calendario anterior a {@code productionDay} (para el tick de producción de ese día).
     */
    private Map<String, Double> buildPreviousDayMeanTempByHive(List<HiveEntity> hives, LocalDate productionDay) {
        LocalDate previousDay = productionDay.minusDays(1);
        Map<String, Double> byHive = new HashMap<>();
        Map<String, Double> coordDayCache = new HashMap<>();
        if (weatherRepository == null) {
            return byHive;
        }
        for (HiveEntity h : hives) {
            String ck = String.format(Locale.US, "%.4f,%.4f_%d", h.lat, h.lng,
                    GameCalendar.toDayKey(previousDay));
            Double t = coordDayCache.get(ck);
            if (t == null) {
                Double fetched = weatherRepository.fetchCalendarDayMeanTemperatureCelsiusBlocking(
                        h.lat, h.lng, previousDay);
                t = fetched != null ? fetched : Double.NaN;
                coordDayCache.put(ck, t);
            }
            byHive.put(h.id, t);
        }
        return byHive;
    }

    /**
     * Solo se ejecuta con la app abierta (p. ej. {@code MainActivity.onResume}): plan Spark gratuito.
     * Tras {@link #mergeRemoteProductionProgress}, recorre cada día civil desde el último procesado hasta hoy;
     * para cada uno solo aplica el tick si ya son las {@link GameCalendar#PRODUCTION_HOUR}:00 o posteriores
     * en la zona horaria del dispositivo.
     *
     * @param appliedDaysOut se vacía al inicio; recibe un {@link DailyTickSummary} por cada día con colmenas
     *                       y datos de resumen (orden cronológico).
     */
    private void tickDailyProductionForOwnerSync(String ownerId, List<DailyTickSummary> appliedDaysOut) {

        appliedDaysOut.clear();

        ZoneId z = GameCalendar.userTimeZone();

        LocalDate today = LocalDate.now(z);

        LocalDateTime now = LocalDateTime.now(z);

        GameProductionStateEntity state = getOrCreateProductionState(ownerId);

        mergeRemoteProductionProgress(ownerId);

        state = gameProductionStateDao.getByOwner(ownerId);

        if (state == null) {

            return;

        }

        LocalDate gameStart = GameCalendar.fromDayKey(state.gameStartDayKey);

        LocalDate lastProcessed = state.lastProcessedProductionDayKey == 0

                ? gameStart.minusDays(1)

                : GameCalendar.fromDayKey(state.lastProcessedProductionDayKey);

        LocalDate cursor = lastProcessed.plusDays(1);

        while (!cursor.isAfter(today)) {

            LocalDateTime deadline = cursor.atTime(GameCalendar.PRODUCTION_HOUR, GameCalendar.PRODUCTION_MINUTE);

            if (now.isBefore(deadline)) {

                break;

            }

            if (cursor.isBefore(gameStart)) {

                state.lastProcessedProductionDayKey = GameCalendar.toDayKey(cursor);

                gameProductionStateDao.update(state);

                pushProductionStateToFirestore(ownerId, state);

                cursor = cursor.plusDays(1);

                continue;

            }

            int dayKeyJustApplied = GameCalendar.toDayKey(cursor);

            applyProductionForCalendarDay(ownerId, cursor, state);

            List<HiveDayStartupSummary> summaries = collectStartupSummariesForDay(ownerId, dayKeyJustApplied);

            if (!summaries.isEmpty()) {

                appliedDaysOut.add(new DailyTickSummary(dayKeyJustApplied, summaries));

            }

            cursor = cursor.plusDays(1);

        }

    }

    private List<HiveDayStartupSummary> collectStartupSummariesForDay(String ownerId, int dayKey) {

        List<HiveEntity> hives = hiveDao.getHivesByOwnerSync(ownerId);

        List<HiveDayStartupSummary> summaries = new ArrayList<>();

        if (hives == null) {

            return summaries;

        }

        for (HiveEntity h : hives) {

            if (h.lastSummaryDayKey == dayKey) {

                int net = h.lastSummaryWorkerEmergences - h.lastSummaryWorkerDeaths;

                summaries.add(new HiveDayStartupSummary(

                        h.name,

                        h.lastSummaryHoneyKg,

                        net,

                        h.lastSummaryEggsLaid,

                        h.lastSummaryDeltaHealth,

                        h.lastSummaryDeltaVarroa,

                        h.lastSummarySwarmed));

            }

        }

        return summaries;

    }

    private void applyProductionForCalendarDay(String ownerId, LocalDate day, GameProductionStateEntity state) {

        int dayKey = GameCalendar.toDayKey(day);

        List<HiveEntity> hives = hiveDao.getHivesByOwnerSync(ownerId);

        if (hives == null || hives.isEmpty()) {

            state.lastProcessedProductionDayKey = dayKey;

            gameProductionStateDao.update(state);

            pushProductionStateToFirestore(ownerId, state);

            if (marketRepository != null) {

                marketRepository.refreshGlobalMarketForDay(dayKey, day.getDayOfYear());

            }

            return;

        }

        Map<String, Double> tempByHiveId = buildPreviousDayMeanTempByHive(hives, day);

        for (HiveEntity h : hives) {

            h.lastSummarySwarmed = false;

            int elevM = h.elevationMeters;

            if (elevM < 0) {

                elevM = OpenMeteoElevation.resolveMetersPersistedBlocking(h.lat, h.lng);

                h.elevationMeters = elevM;

                hiveDao.upsert(h);

            }

            String skyKey = "hive:" + h.id;

            DailySkyCondition sky = DailySkyCondition.forHexElevationAndDay(skyKey, dayKey, elevM);

            double skyMult = sky.productionMultiplier();

            int healthStart = h.health;

            double varroaStart = h.varroaPct;

            ensurePopulationJson(h);

            HivePopulationState pop = HivePopulationState.fromJson(h.populationStateJson);

            if (pop == null) {

                int nPop = h.beeCount > 0 ? h.beeCount : DEFAULT_BEE_COUNT_PER_HIVE;

                if (h.id != null && !h.id.isEmpty()) {

                    pop = HivePopulationState.fromInitialColonyWithRandomBroodPipeline(nPop, h.id);

                } else {

                    pop = HivePopulationState.fromLegacyBeeCount(nPop);

                }

            } else if (h.beeCount >= 200 && pop.totalBees() < h.beeCount) {

                int lpk = pop.lastPopulationDayKey;

                QueenMode qm = pop.queenMode;

                int rwd = pop.replaceWindowDay;

                int qpd = pop.queenPipelineDay;

                int dwel = pop.daysWithoutEggLaying;

                HivePopulationTrend tr = pop.lastTrend;

                int tot = Math.max(500, h.beeCount);

                pop = (h.id != null && !h.id.isEmpty())

                        ? HivePopulationState.fromInitialColonyWithRandomBroodPipeline(tot, h.id)

                        : HivePopulationState.fromLegacyBeeCount(tot);

                pop.lastPopulationDayKey = lpk;

                pop.queenMode = qm;

                pop.replaceWindowDay = rwd;

                pop.queenPipelineDay = qpd;

                pop.daysWithoutEggLaying = dwel;

                pop.lastTrend = tr;

            }

            Season season = Season.fromDayOfYear(day.getDayOfYear());

            if (h.lastHealthSimDayKey < dayKey) {

                HiveDailyHealthSimulator.applyDay(h, dayKey, season);

                h.lastHealthSimDayKey = dayKey;

            }

            boolean ranPopulationSim = pop.lastPopulationDayKey < dayKey;

            if (ranPopulationSim) {

                HivePopulationSimulator.applyDay(pop, h, day, dayKey);

                pop.lastPopulationDayKey = dayKey;

                h.populationStateJson = pop.toJson();

                h.beeCount = pop.totalBees();

                h.lastSummarySwarmed = pop.lastDaySwarmed;

            }

            if (ranPopulationSim) {

                h.lastSummaryWorkerDeaths = pop.lastDayWorkerDeaths;

                h.lastSummaryWorkerEmergences = pop.lastDayWorkerEmergences;

                h.lastSummaryEggsLaid = pop.lastDayEggsLaid;

                h.lastSummaryDeltaBees = pop.lastDayWorkerEmergences;

            } else {

                h.lastSummaryWorkerDeaths = 0;

                h.lastSummaryWorkerEmergences = 0;

                h.lastSummaryEggsLaid = 0;

                h.lastSummaryDeltaBees = 0;

            }

            boolean honeyDone = hiveDailyYieldDao.getYield(h.id, dayKey) != null;

            if (!honeyDone) {

                HiveDailyYieldEntity cloudRow = readFirestoreDailyYieldBlocking(h.id, dayKey);

                if (cloudRow != null) {

                    hiveDailyYieldDao.insert(cloudRow);

                    honeyDone = true;

                }

            }

            if (!honeyDone) {

                Double tempRaw = tempByHiveId.get(h.id);

                Double tempC = (tempRaw != null && !Double.isNaN(tempRaw)) ? tempRaw : null;

                double mult = TemperatureHoneyModifier.productionMultiplierForCelsius(tempC);

                double baseKg = HoneyDailyProduction.deterministicDailyKgForHive(h.id, dayKey, h.beeCount);

                double healthHoney = HealthHoneyModifier.productionMultiplierForHealth(h.health);

                double grossKg = baseKg * mult * skyMult * healthHoney;

                double kg = DailyHoneyConsumption.netKgAfterConsumption(grossKg, h.beeCount);

                HiveDailyYieldEntity row = new HiveDailyYieldEntity();

                row.hiveId = h.id;

                row.dayKey = dayKey;

                row.kg = kg;

                int netWorkers = ranPopulationSim

                        ? (h.lastSummaryWorkerEmergences - h.lastSummaryWorkerDeaths)

                        : 0;

                row.workerNetDelta = netWorkers;

                row.eggsLaid = ranPopulationSim ? h.lastSummaryEggsLaid : 0;

                hiveDailyYieldDao.insert(row);

                h.honeyProduction = Math.max(0.0, Math.max(0.0, h.honeyProduction) + kg);

                HiveHoneyRules.clampHoneyStockToCap(h);

            }

            h.lastSummaryDayKey = dayKey;

            h.lastSummaryDeltaHealth = h.health - healthStart;

            h.lastSummaryDeltaVarroa = h.varroaPct - varroaStart;

            HiveDailyYieldEntity yieldForDay = hiveDailyYieldDao.getYield(h.id, dayKey);

            h.lastSummaryHoneyKg = yieldForDay != null ? yieldForDay.kg : 0.0;

            normalizeBeeCount(h);

            hiveDao.upsert(h);

            if (firestore != null) {

                try {

                    Tasks.await(firestore.collection("hives").document(h.id).set(h, SetOptions.merge()));

                    if (yieldForDay != null) {

                        Map<String, Object> y = new HashMap<>();

                        y.put("kg", yieldForDay.kg);

                        y.put("workerNetDelta", yieldForDay.workerNetDelta);

                        y.put("eggsLaid", yieldForDay.eggsLaid);

                        y.put("dayKey", dayKey);

                        y.put("hiveId", h.id);

                        y.put("ownerId", h.ownerId);

                        y.put("sky", sky.name());

                        Tasks.await(firestore.collection("hives").document(h.id)

                                .collection("dailyYields").document(String.valueOf(dayKey))

                                .set(y, SetOptions.merge()));

                    }

                } catch (Exception ignored) {

                }

            }

        }

        state.lastProcessedProductionDayKey = dayKey;

        gameProductionStateDao.update(state);

        pushProductionStateToFirestore(ownerId, state);

        if (marketRepository != null) {

            marketRepository.refreshGlobalMarketForDay(dayKey, day.getDayOfYear());

        }

    }



    /**
     * Número de colmenas en el hex. Solo hilo de fondo.
     */
    public int countHivesOnHexBlocking(String hexId) {

        if (hexId == null || hexId.isEmpty()) {

            return 0;

        }

        return hiveDao.countByHexId(hexId);

    }

    public void syncFromCloud() {

        if (firestore == null) return;

        firestore.collection("hives")

                .get()

                .addOnSuccessListener(querySnapshot -> ioExecutor.execute(() -> {

                    for (var doc : querySnapshot.getDocuments()) {

                        HiveEntity hive = doc.toObject(HiveEntity.class);

                        if (hive != null && hive.id != null) {

                            if (!doc.contains("elevationMeters")) {

                                hive.elevationMeters = -1;

                            }

                            upsertHiveFromCloud(doc, hive);

                        }

                    }

                }));

    }



    public void startRealtimeCloudSync() {

        startRealtimeCloudSync(null);

    }



    /**
     * Escucha cambios en colmenas del jugador. Si {@code ownerId} es no nulo y no vacío, solo se sincronizan
     * documentos con ese {@code ownerId} (recomendado tras login o reinicio); si es null, se mantiene el
     * comportamiento anterior (toda la colección).
     */

    public void startRealtimeCloudSync(String ownerId) {

        if (firestore == null || cloudListener != null) return;

        Query query = firestore.collection("hives");

        if (ownerId != null && !ownerId.isEmpty()) {

            query = query.whereEqualTo("ownerId", ownerId);

        }

        cloudListener = query

                .addSnapshotListener((snapshot, error) -> {

                    if (error != null || snapshot == null) return;

                    ioExecutor.execute(() -> {

                        for (var doc : snapshot.getDocuments()) {

                            HiveEntity hive = doc.toObject(HiveEntity.class);

                            if (hive != null && hive.id != null) {

                                if (!doc.contains("elevationMeters")) {

                                    hive.elevationMeters = -1;

                                }

                                upsertHiveFromCloud(doc, hive);

                            }

                        }

                    });

                });

    }



    public void stopRealtimeCloudSync() {

        if (cloudListener != null) {

            cloudListener.remove();

            cloudListener = null;

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
    }

    private void upsertHiveFromCloud(DocumentSnapshot doc, HiveEntity hive) {

        HiveEntity existing = null;
        if (hive != null && hive.id != null) {
            existing = hiveDao.getHiveByIdSync(hive.id);
            if (existing != null && doc != null && !doc.contains("superCount")) {
                hive.superCount = existing.superCount;
            }
            /*
             * La simulación diaria actualiza Room y luego Firestore. El snapshot puede llegar con un
             * documento rezagado (caché u orden de escritura) y bajar honeyProduction / población.
             * Colmena 1 (~80k abejas) llena el tope de miel muy rápido (5 kg sin alzas): el fallo se nota ahí.
             */
            if (existing != null && localSimulationAheadOfCloudHive(existing, hive)) {
                copyAheadSimulationFieldsFromLocal(existing, hive);
            } else if (existing != null && shouldRestoreHoneyWhenCloudStaleSameDay(existing, hive)) {
                hive.honeyProduction = existing.honeyProduction;
            }
        }

        boolean fixed = normalizeBeeCount(hive);

        hiveDao.upsert(hive);

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

        hiveDao.upsert(hive);

        if (firestore != null) {

            Tasks.await(firestore.collection("hives").document(hive.id).set(hive));

        }

    }

    public void createHiveAtLocationValidated(

            String ownerId, double lat, double lng, Consumer<String> onMainMessage) {

        if (ownerId == null || ownerId.isEmpty()) {

            mainHandler.post(() -> onMainMessage.accept("Sesión no válida."));

            return;

        }

        ioExecutor.execute(() -> {

            try {

                List<HexParcel> all = IberiaHexOverlayStore.getParcels(appContext);

                HexParcel hex = HexParcelResolve.findContaining(all, lat, lng);

                if (hex == null) {

                    mainHandler.post(() -> onMainMessage.accept("Ubicación fuera de terrenos jugables."));

                    return;

                }

                String parcelOwner = hexParcelRepository.getOwnerSync(hex.id);

                if (parcelOwner == null || !parcelOwner.equals(ownerId)) {

                    mainHandler.post(() -> onMainMessage.accept("Compra este terreno para crear colmenas aquí."));

                    return;

                }

                int nHive = hiveDao.countByHexId(hex.id);

                if (nHive >= HexParcelGameRules.MAX_HIVES_PER_HEX) {

                    mainHandler.post(() -> onMainMessage.accept(

                            "Este terreno ya tiene " + HexParcelGameRules.MAX_HIVES_PER_HEX + " colmenas."));

                    return;

                }

                String flora = hexFloraRepository.getOrCreateFloraForHexBlocking(hex.id);

                HiveEntity hive = new HiveEntity();

                hive.id = UUID.randomUUID().toString();

                hive.ownerId = ownerId;

                hive.name = "Apiario " + (System.currentTimeMillis() % 10000);

                hive.beeCount = DEFAULT_BEE_COUNT_PER_HIVE;

                hive.health = 90;

                hive.reserves = 55;

                hive.queenAgeDays = 90;

                hive.queenGeneticQuality = 72;

                hive.lat = lat;

                hive.lng = lng;

                hive.hexId = hex.id;

                hive.floraType = flora;

                hive.superCount = 0;

                hive.honeyProduction = 4.0 + Math.random() * 4.0;

                hive.varroaPct = 2.0;

                hive.varroaTreatmentDaysRemaining = 0;

                hive.varroaReboundDaysRemaining = 0;

                hive.lastHealthSimDayKey = 0;

                hive.populationStateJson = null;

                hive.elevationMeters = OpenMeteoElevation.resolveMetersPersistedBlocking(lat, lng);

                saveHiveBlocking(hive);

                mainHandler.post(() -> onMainMessage.accept(null));

            } catch (Exception e) {

                Log.e(TAG, "createHiveAtLocationValidated", e);

                mainHandler.post(() -> onMainMessage.accept(formatThrowableForUser(e)));

            }

        });

    }

    /** Opción de terreno propio para comprar colmena (id + etiqueta corta). */
    public static final class OwnedHexOption {
        public final String hexId;
        public final String label;

        public OwnedHexOption(String hexId, String label) {
            this.hexId = hexId;
            this.label = label;
        }
    }

    public static int purchasePriceEurosForSuperCount(int superCount) {
        int s = Math.max(0, Math.min(2, superCount));
        return 200 + 50 * s;
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
                for (String hexId : hexParcelRepository.listOwnedHexIdsSync(ownerId)) {
                    String f = hexFloraRepository.getOrCreateFloraForHexBlocking(hexId);
                    if (floraType.equals(f)) {
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
     * coloca la colmena en coordenadas aleatorias dentro del hex.
     */
    public void purchaseHiveValidated(
            String ownerId,
            String hexId,
            String expectedFloraType,
            String hiveName,
            int superCount,
            Consumer<String> onMainMessage) {
        if (ownerId == null || ownerId.isEmpty()) {
            mainHandler.post(() -> onMainMessage.accept("Sesión no válida."));
            return;
        }
        if (hexId == null || hexId.isEmpty()) {
            mainHandler.post(() -> onMainMessage.accept("Elige un terreno."));
            return;
        }
        int supers = Math.max(0, Math.min(2, superCount));
        double price = purchasePriceEurosForSuperCount(supers);
        String trimmedName = hiveName == null ? "" : hiveName.trim();
        if (trimmedName.isEmpty()) {
            mainHandler.post(() -> onMainMessage.accept("Escribe un nombre para la colmena."));
            return;
        }
        String nameToSave = trimmedName.length() > 120 ? trimmedName.substring(0, 120) : trimmedName;
        ioExecutor.execute(() -> {
            try {
                String parcelOwner = hexParcelRepository.getOwnerSync(hexId);
                if (parcelOwner == null || !parcelOwner.equals(ownerId)) {
                    mainHandler.post(() -> onMainMessage.accept(
                            "Compra este terreno para crear colmenas aquí."));
                    return;
                }
                String flora = hexFloraRepository.getOrCreateFloraForHexBlocking(hexId);
                if (expectedFloraType == null || !expectedFloraType.equals(flora)) {
                    mainHandler.post(() -> onMainMessage.accept(
                            "La flora del terreno no coincide con la selección."));
                    return;
                }
                int nHive = hiveDao.countByHexId(hexId);
                if (nHive >= HexParcelGameRules.MAX_HIVES_PER_HEX) {
                    mainHandler.post(() -> onMainMessage.accept(
                            "Este terreno ya tiene " + HexParcelGameRules.MAX_HIVES_PER_HEX
                                    + " colmenas."));
                    return;
                }
                List<HexParcel> all = IberiaHexOverlayStore.getParcels(appContext);
                HexParcel hex = HexParcelResolve.findById(all, hexId);
                if (hex == null) {
                    mainHandler.post(() -> onMainMessage.accept("Terreno no encontrado en el mapa."));
                    return;
                }
                if (!economyRepository.trySpend(price)) {
                    mainHandler.post(() -> onMainMessage.accept("Saldo insuficiente."));
                    return;
                }
                try {
                    double[] ll = HexParcelRandomPoint.randomLatLonInside(hex, new Random());
                    int health = 85 + (int) (Math.random() * 16);
                    HiveEntity hive = new HiveEntity();
                    hive.id = UUID.randomUUID().toString();
                    hive.ownerId = ownerId;
                    hive.name = nameToSave;
                    hive.beeCount = DEFAULT_BEE_COUNT_PER_HIVE;
                    hive.health = health;
                    hive.reserves = 55;
                    hive.queenAgeDays = 90;
                    hive.queenGeneticQuality = 72;
                    hive.lat = ll[0];
                    hive.lng = ll[1];
                    hive.hexId = hex.id;
                    hive.floraType = flora;
                    hive.superCount = supers;
                    hive.honeyProduction = 3.0;
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
                    saveHiveBlocking(hive);
                    mainHandler.post(() -> onMainMessage.accept(null));
                } catch (Exception e) {
                    economyRepository.addToBalance(price);
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
     * Compra 1 o 2 alzas (50 € cada una); máximo {@code 2} por colmena en total.
     */
    public void purchaseSupersForHive(String hiveId, String ownerId, int toBuy,
            Consumer<String> onMainMessage) {
        if (hiveId == null || hiveId.isEmpty() || ownerId == null || ownerId.isEmpty()) {
            mainHandler.post(() -> onMainMessage.accept("Sesión no válida."));
            return;
        }
        if (toBuy < 1 || toBuy > 2) {
            mainHandler.post(() -> onMainMessage.accept("Cantidad de alzas no válida."));
            return;
        }
        ioExecutor.execute(() -> {
            try {
                HiveEntity h = hiveDao.getHiveByIdSync(hiveId);
                if (h == null) {
                    mainHandler.post(() -> onMainMessage.accept("Colmena no encontrada."));
                    return;
                }
                if (!ownerId.equals(h.ownerId)) {
                    mainHandler.post(() -> onMainMessage.accept("Esta colmena no es tuya."));
                    return;
                }
                int current = Math.max(0, Math.min(2, h.superCount));
                int room = 2 - current;
                if (room <= 0) {
                    mainHandler.post(() -> onMainMessage.accept(
                            "Esta colmena ya tiene el máximo de alzas (2)."));
                    return;
                }
                if (toBuy > room) {
                    mainHandler.post(() -> onMainMessage.accept(
                            "Solo puedes comprar " + room + " alza(s) más."));
                    return;
                }
                double cost = toBuy * HiveHoneyRules.SUPER_PURCHASE_PRICE_EUR;
                if (!economyRepository.trySpend(cost)) {
                    mainHandler.post(() -> onMainMessage.accept("Saldo insuficiente."));
                    return;
                }
                try {
                    h.superCount = Math.min(2, current + toBuy);
                    saveHiveBlocking(h);
                    mainHandler.post(() -> onMainMessage.accept(null));
                } catch (Exception e) {
                    economyRepository.addToBalance(cost);
                    throw e;
                }
            } catch (Exception e) {
                Log.e(TAG, "purchaseSupersForHive", e);
                mainHandler.post(() -> onMainMessage.accept(formatThrowableForUser(e)));
            }
        });
    }

    public void transhumanceValidated(

            HiveEntity hive, double lat, double lng, Consumer<String> onMainMessage) {

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

                List<HexParcel> all = IberiaHexOverlayStore.getParcels(appContext);

                HexParcel hex = HexParcelResolve.findContaining(all, lat, lng);

                if (hex == null) {

                    mainHandler.post(() -> onMainMessage.accept("Destino fuera de terrenos jugables."));

                    return;

                }

                String parcelOwner = hexParcelRepository.getOwnerSync(hex.id);

                if (parcelOwner == null || !parcelOwner.equals(h.ownerId)) {

                    mainHandler.post(() -> onMainMessage.accept("El destino no es un terreno tuyo."));

                    return;

                }

                HexParcel hexAtOldPos = HexParcelResolve.findContaining(all, h.lat, h.lng);

                boolean sameHex = (h.hexId != null && hex.id.equals(h.hexId))

                        || (h.hexId == null && hexAtOldPos != null && hex.id.equals(hexAtOldPos.id));

                if (sameHex) {

                    h.lat = lat;

                    h.lng = lng;

                    if (h.hexId == null) {

                        h.hexId = hex.id;

                    }

                    h.elevationMeters = OpenMeteoElevation.resolveMetersPersistedBlocking(h.lat, h.lng);

                    saveHiveBlocking(h);

                    mainHandler.post(() -> onMainMessage.accept(null));

                    return;

                }

                int nHive = hiveDao.countByHexId(hex.id);

                if (nHive >= HexParcelGameRules.MAX_HIVES_PER_HEX) {

                    mainHandler.post(() -> onMainMessage.accept(

                            "Ese terreno ya tiene el máximo de colmenas."));

                    return;

                }

                h.lat = lat;

                h.lng = lng;

                h.hexId = hex.id;

                h.floraType = hexFloraRepository.getOrCreateFloraForHexBlocking(hex.id);

                h.reserves = Math.max(0, h.reserves - 15);

                h.elevationMeters = OpenMeteoElevation.resolveMetersPersistedBlocking(h.lat, h.lng);

                saveHiveBlocking(h);

                mainHandler.post(() -> onMainMessage.accept(null));

            } catch (Exception e) {

                Log.e(TAG, "transhumanceValidated", e);

                mainHandler.post(() -> onMainMessage.accept(formatThrowableForUser(e)));

            }

        });

    }

    /**
     * Mitad de población (adultos + cría), miel en colmena y reservas repartidas; salud e infestación de varroa
     * (y días de tratamiento/rebote) se copian iguales en ambas colmenas.
     * <p>
     * La colmena nueva tiene otro {@code id}: no hereda filas de {@code hive_daily_yield} ni documentos
     * {@code dailyYields} en Firestore (historial de producción vacío). Los campos de último resumen diario
     * ({@code lastSummary*}) se ponen a cero; la madre conserva historial y último resumen.
     */
    public void splitHiveHalf(String hiveId, Consumer<String> onMainMessage) {
        if (hiveId == null || hiveId.isEmpty()) {
            if (onMainMessage != null) {
                mainHandler.post(() -> onMainMessage.accept("Colmena no válida."));
            }
            return;
        }
        ioExecutor.execute(() -> {
            try {
                String err = trySplitHiveHalfBlocking(hiveId);
                if (onMainMessage != null) {
                    mainHandler.post(() -> onMainMessage.accept(err));
                }
            } catch (Exception e) {
                Log.e(TAG, "splitHiveHalf", e);
                if (onMainMessage != null) {
                    mainHandler.post(() -> onMainMessage.accept(formatThrowableForUser(e)));
                }
            }
        });
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
                java.util.LinkedHashSet<String> unique = new java.util.LinkedHashSet<>();
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
                int ok = 0;
                String firstError = null;
                for (String id : unique) {
                    String err = trySplitHiveHalfBlocking(id);
                    if (err == null) {
                        ok++;
                    } else if (firstError == null) {
                        firstError = err;
                    }
                }
                if (onMainMessage != null) {
                    final String out;
                    if (ok == attempted) {
                        out = null;
                    } else if (ok > 0) {
                        out = "Divididas " + ok + " de " + attempted + ". "
                                + (firstError != null ? firstError : "");
                    } else {
                        out = firstError != null ? firstError : "No se pudo dividir ninguna colmena.";
                    }
                    mainHandler.post(() -> onMainMessage.accept(out));
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
    private String trySplitHiveHalfBlocking(String hiveId) {
        if (hiveId == null || hiveId.isEmpty()) {
            return "Colmena no válida.";
        }
        try {
            HiveEntity h = hiveDao.getHiveByIdSync(hiveId);
            if (h == null) {
                return "Colmena no encontrada.";
            }
            ensurePopulationJson(h);
            HivePopulationState s = HivePopulationState.fromJson(h.populationStateJson);
            if (s == null) {
                return "Estado de colonia no disponible.";
            }
            if (s.workersAdult < ColonyGameRules.MIN_BEES_TO_SPLIT) {
                return "Necesitas al menos 50.000 obreras adultas para dividir la colmena.";
            }
            if (h.hexId == null || h.hexId.isEmpty()) {
                return "Asigna un terreno a la colmena antes de dividirla.";
            }
            if (countHivesOnHexBlocking(h.hexId) >= HexParcelGameRules.MAX_HIVES_PER_HEX) {
                return "Este terreno ya tiene el máximo de colmenas permitidas.";
            }
            HivePopulationState spawnPop = s.splitOffFairHalf();
            h.populationStateJson = s.toJson();
            h.beeCount = s.totalBees();
            HiveEntity nu = new HiveEntity();
            nu.id = UUID.randomUUID().toString();
            nu.ownerId = h.ownerId;
            nu.name = h.name + " · II";
            nu.lat = h.lat;
            nu.lng = h.lng;
            nu.hexId = h.hexId;
            nu.floraType = h.floraType;
            nu.superCount = h.superCount;
            nu.elevationMeters = h.elevationMeters;
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
            double halfHoney = h.honeyProduction / 2.0;
            nu.honeyProduction = halfHoney;
            h.honeyProduction -= halfHoney;
            int rSpawn = h.reserves / 2;
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
            hiveDao.upsert(h);
            hiveDao.upsert(nu);
            if (firestore != null) {
                Tasks.await(firestore.collection("hives").document(h.id).set(h, SetOptions.merge()));
                Tasks.await(firestore.collection("hives").document(nu.id).set(nu, SetOptions.merge()));
            }
            return null;
        } catch (Exception e) {
            Log.e(TAG, "trySplitHiveHalfBlocking", e);
            return formatThrowableForUser(e);
        }
    }

}


