package com.apiculture.simulator.presentation.dashboard;

import android.app.Application;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.Transformations;

import com.apiculture.simulator.ApicultureApp;
import com.apiculture.simulator.R;
import com.apiculture.simulator.data.local.entity.HiveEntity;
import com.apiculture.simulator.data.repository.ProfileRepository;
import com.apiculture.simulator.data.repository.EconomyRepository;
import com.apiculture.simulator.data.repository.HoneyLogistics;
import com.apiculture.simulator.data.repository.HiveRepository;
import com.apiculture.simulator.data.repository.LeaderboardRepository;
import com.apiculture.simulator.data.repository.PlayerProgressRepository;
import com.apiculture.simulator.data.repository.GlobalEventRepository;
import com.apiculture.simulator.data.repository.EventInventoryStore;
import com.apiculture.simulator.data.repository.MarketRepository;
import com.apiculture.simulator.domain.admin.AdminRoles;
import com.apiculture.simulator.domain.game.DemandSurgeMilestones;
import com.apiculture.simulator.domain.game.ColonyGameRules;
import com.apiculture.simulator.domain.game.GameCalendar;
import com.apiculture.simulator.domain.game.HiveHoneyRules;
import com.apiculture.simulator.domain.game.HiveHoneyStocks;
import com.apiculture.simulator.domain.market.HoneyMarketEngine;
import com.apiculture.simulator.domain.market.HoneyMarketSnapshot;
import com.apiculture.simulator.domain.population.HivePopulationState;
import com.apiculture.simulator.domain.game.GameClock;
import com.apiculture.simulator.domain.game.LevelSystem;
import com.apiculture.simulator.domain.game.Season;
import com.apiculture.simulator.domain.parcel.FloraPlantingProgressRow;
import com.apiculture.simulator.presentation.hive.HiveSiteSummaryUi;
import com.apiculture.simulator.presentation.profile.ProfilePhoto;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class DashboardViewModel extends AndroidViewModel {

    private final MutableLiveData<String> statCoins = new MutableLiveData<>();
    private final MutableLiveData<String> statHoney = new MutableLiveData<>();
    private final MutableLiveData<String> ownerIdForHives = new MutableLiveData<>();
    private final LiveData<String> statNectar;
    private final MutableLiveData<String> seasonText = new MutableLiveData<>();
    private final MutableLiveData<String> dayText = new MutableLiveData<>();
    private final MutableLiveData<String> profileName = new MutableLiveData<>();
    private final MutableLiveData<Bitmap> profilePhoto = new MutableLiveData<>();
    /** Falso mientras el nombre y la foto aún no han llegado ni hay copia local. */
    private final MutableLiveData<Boolean> profileReady = new MutableLiveData<>(true);
    private final MutableLiveData<String> headerHoneyBrand = new MutableLiveData<>();
    private final MutableLiveData<String> profileSubtitle = new MutableLiveData<>();
    private final MutableLiveData<String> xpLabel = new MutableLiveData<>();
    private final MutableLiveData<Integer> xpCurrent = new MutableLiveData<>();
    private final MutableLiveData<Integer> xpMax = new MutableLiveData<>();
    private final MutableLiveData<String> eventTitle = new MutableLiveData<>();
    private final MutableLiveData<String> eventSubtitle = new MutableLiveData<>();
    private final MutableLiveData<GlobalEventBanner> globalEventBanner = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isAdmin = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> resetBusy = new MutableLiveData<>(false);
    private final MutableLiveData<Integer> queenInventoryCount = new MutableLiveData<>(0);
    private final MutableLiveData<Integer> treatInventoryCount = new MutableLiveData<>(0);
    private final MutableLiveData<Integer> feedInventoryCount = new MutableLiveData<>(0);
    private final MediatorLiveData<SwarmRiskBanner> swarmRiskBanner = new MediatorLiveData<>();
    private final MediatorLiveData<QueenDeathBanner> queenDeathBanner = new MediatorLiveData<>();
    private final MediatorLiveData<HoneyCapBanner> honeyCapBanner = new MediatorLiveData<>();
    private final MutableLiveData<List<FloraPlantingProgressRow>> floraPlantingsInProgress =
            new MutableLiveData<>(Collections.emptyList());

    // Estado interno sencillo de progreso del jugador
    private int level = 0;
    private double xp = 0;
    private int xpMaxInternal;

    private final HiveRepository hiveRepository;
    private final EconomyRepository economyRepository;
    private final ProfileRepository profileRepository;
    private final PlayerProgressRepository playerProgressRepository;
    private final LeaderboardRepository leaderboardRepository;
    private final GlobalEventRepository globalEventRepository;
    private final MarketRepository marketRepository;
    /** Room prohíbe consultas síncronas en el hilo principal; el refresco del aviso de enjambrazón va aquí. */
    private final ExecutorService swarmBannerIo = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Observer<GlobalEventRepository.Snapshot> eventObserver = this::applyGlobalEventSnapshot;

    public DashboardViewModel(@NonNull Application application) {
        super(application);
        ApicultureApp app = (ApicultureApp) application;
        hiveRepository = app.getHiveRepository();
        economyRepository = app.getEconomyRepository();
        profileRepository = app.getProfileRepository();
        playerProgressRepository = app.getPlayerProgressRepository();
        leaderboardRepository = app.getLeaderboardRepository();
        globalEventRepository = app.getGlobalEventRepository();
        marketRepository = app.getMarketRepository();
        LiveData<List<HiveEntity>> hivesLive =
                Transformations.switchMap(ownerIdForHives, hiveRepository::getLocalHives);
        statNectar = Transformations.map(hivesLive, this::formatTotalBees);
        swarmRiskBanner.addSource(hivesLive, this::rebuildSwarmRiskBannerFromList);
        queenDeathBanner.addSource(hivesLive, this::rebuildQueenDeathBannerFromList);
        honeyCapBanner.addSource(hivesLive, this::rebuildHoneyCapBannerFromList);

        GameClock clock = new GameClock();
        applyGameDateToUi(LocalDate.now(), clock);

        refreshEconomyDisplay();

        profileName.setValue(application.getString(R.string.dashboard_default_player));
        headerHoneyBrand.setValue("—");
        xpMaxInternal = LevelSystem.xpForLevel(level);
        publishXpUi();
        profileSubtitle.setValue(buildProfileSubtitle());

        eventTitle.setValue(getApplication().getString(R.string.dashboard_no_event_title));
        eventSubtitle.setValue(getApplication().getString(R.string.dashboard_no_event_subtitle));
        refreshInventoryDisplay();
        if (globalEventRepository != null) {
            globalEventRepository.snapshot().observeForever(eventObserver);
            applyGlobalEventSnapshot(globalEventRepository.cached());
        }

        ownerIdForHives.setValue("");
    }

    /**
     * Enlaza las colmenas locales del usuario para el total de obreras (UI) y resumen (sesión iniciada).
     */
    public void bindHivesForUser(@Nullable String firebaseUid) {
        ownerIdForHives.setValue(firebaseUid != null ? firebaseUid : "");
        loadAndApplyProgress(firebaseUid);
        refreshPlayerProfile(firebaseUid);
        refreshFloraPlantings(firebaseUid);
        refreshGameClock();
        if (firebaseUid != null && !firebaseUid.isEmpty()) {
            leaderboardRepository.enqueuePublish(firebaseUid);
        }
        if (globalEventRepository != null) {
            applyGlobalEventSnapshot(globalEventRepository.cached());
        }
    }

    /** Siembras de flora con cuenta atrás (dashboard). */
    public void refreshFloraPlantings(@Nullable String firebaseUid) {
        if (firebaseUid == null || firebaseUid.isEmpty()) {
            floraPlantingsInProgress.postValue(Collections.emptyList());
            return;
        }
        hiveRepository.loadFloraPlantingsInProgressAsync(firebaseUid, rows ->
                floraPlantingsInProgress.postValue(rows != null ? rows : Collections.emptyList()));
    }

    public LiveData<List<FloraPlantingProgressRow>> floraPlantings() {
        return floraPlantingsInProgress;
    }

    private void loadAndApplyProgress(@Nullable String firebaseUid) {
        if (firebaseUid == null || firebaseUid.isEmpty()) {
            level = 0;
            xp = 0;
        } else {
            level = playerProgressRepository.getLevel(firebaseUid);
            xp = playerProgressRepository.getXp(firebaseUid);
        }
        xpMaxInternal = LevelSystem.xpForLevel(level);
        publishXpUi();
        profileSubtitle.setValue(buildProfileSubtitle());
    }

    /** Carga nombre de jugador y marca de miel para cabecera y tarjeta de perfil. */
    public void refreshPlayerProfile(@Nullable String firebaseUid) {
        if (profileRepository == null || firebaseUid == null || firebaseUid.isEmpty()) {
            profileName.postValue(getApplication().getString(R.string.dashboard_default_player));
            headerHoneyBrand.postValue("—");
            isAdmin.postValue(false);
            profilePhoto.postValue(null);
            profileReady.postValue(true);
            return;
        }
        if (!applyCachedProfile(firebaseUid)) {
            profileReady.setValue(false);
        }
        final String uid = firebaseUid;
        profileRepository.fetchDisplayProfile(uid, p -> swarmBannerIo.execute(() -> {
            publishProfile(p.playerName, p.honeyBrand,
                    ProfilePhoto.decodeBase64(p.photoBase64));
            saveCachedProfile(uid, p.playerName, p.honeyBrand, p.photoBase64);
            profileReady.postValue(true);
        }));
    }

    private static final String PROFILE_CACHE = "profile_header_cache";

    private void publishProfile(@Nullable String playerName, @Nullable String honeyBrand,
            @Nullable Bitmap photo) {
        String defPlayer = getApplication().getString(R.string.dashboard_default_player);
        String name = playerName == null || playerName.isEmpty() ? defPlayer : playerName;
        profileName.postValue(name);
        headerHoneyBrand.postValue(honeyBrand == null || honeyBrand.isEmpty() ? "—" : honeyBrand);
        isAdmin.postValue(AdminRoles.isAdminPlayerName(playerName));
        profilePhoto.postValue(photo);
    }

    /** @return true si ya había nombre y foto de una visita anterior. */
    private boolean applyCachedProfile(@NonNull String uid) {
        String raw = getApplication().getSharedPreferences(PROFILE_CACHE, Context.MODE_PRIVATE)
                .getString(uid, null);
        if (raw == null || raw.isEmpty()) {
            return false;
        }
        try {
            JSONObject o = new JSONObject(raw);
            String name = o.optString("playerName", "");
            String brand = o.optString("honeyBrand", "");
            Bitmap photo = ProfilePhoto.decodeBase64(o.optString("photoBase64", ""));
            String defPlayer = getApplication().getString(R.string.dashboard_default_player);
            profileName.setValue(name.isEmpty() ? defPlayer : name);
            headerHoneyBrand.setValue(brand.isEmpty() ? "—" : brand);
            isAdmin.setValue(AdminRoles.isAdminPlayerName(name));
            profilePhoto.setValue(photo);
            profileReady.setValue(true);
            return true;
        } catch (JSONException e) {
            return false;
        }
    }

    private void saveCachedProfile(@NonNull String uid, @Nullable String playerName,
            @Nullable String honeyBrand, @Nullable String photoBase64) {
        JSONObject o = new JSONObject();
        try {
            o.put("playerName", playerName != null ? playerName : "");
            o.put("honeyBrand", honeyBrand != null ? honeyBrand : "");
            o.put("photoBase64", photoBase64 != null ? photoBase64 : "");
        } catch (JSONException e) {
            return;
        }
        getApplication().getSharedPreferences(PROFILE_CACHE, Context.MODE_PRIVATE)
                .edit()
                .putString(uid, o.toString())
                .apply();
    }

    /**
     * Mismo saldo y stock que compra de terrenos / mercado.
     * Enteros sin separador de miles para no confundir “2.000” (dos mil) con “200”.
     */
    public void refreshEconomyDisplay() {
        statCoins.setValue(Long.toString(Math.round(economyRepository.getBalance())));
        statHoney.setValue(String.format(Locale.getDefault(), "%.2f kg", economyRepository.getHoneyStock()));
    }

    /**
     * Fecha/temporada del panel: si «Simular un día» adelantó el tick, muestra esa fecha de juego.
     */
    public void refreshGameClock() {
        String uid = ownerIdForHives.getValue();
        GameClock clock = new GameClock();
        if (uid == null || uid.isEmpty()) {
            applyGameDateToUi(LocalDate.now(), clock);
            return;
        }
        hiveRepository.loadUiGameDate(uid, date -> applyGameDateToUi(date, clock));
    }

    public void loadWeeklyHoney(String ownerId, Consumer<double[]> onMain) {
        hiveRepository.loadWeeklyHoney(ownerId, onMain);
    }

    public static Season southernSeason(Season northern) {
        switch (northern) {
            case SPRING: return Season.AUTUMN;
            case SUMMER: return Season.WINTER;
            case AUTUMN: return Season.SPRING;
            case WINTER:
            default: return Season.SUMMER;
        }
    }

    private void applyGameDateToUi(@Nullable LocalDate gameDate, @NonNull GameClock clock) {
        LocalDate today = LocalDate.now();
        LocalDate d = gameDate != null ? gameDate : today;
        Season north = Season.fromDayOfYear(d.getDayOfYear());
        Season south = southernSeason(north);
        String northLabel = north.emoji() + " " + north.label(getApplication());
        String southLabel = south.emoji() + " " + south.label(getApplication());
        seasonText.setValue(getApplication().getString(
                R.string.dashboard_season_pair, northLabel, southLabel));
        if (d.isAfter(today)) {
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault());
            dayText.setValue(getApplication().getString(R.string.dashboard_date_simulated, d.format(fmt)));
        } else {
            dayText.setValue("");
        }
    }

    /**
     * Fuerza a recalcular la tarjeta de enjambrazón desde Room (volver al dashboard no siempre re-emite {@code hivesLive}).
     * La lectura a Room no puede hacerse en el hilo UI ({@link IllegalStateException} de Room).
     */
    public void refreshSwarmRiskBannerNow() {
        String uid = ownerIdForHives.getValue();
        if (uid == null || uid.isEmpty()) {
            swarmRiskBanner.postValue(null);
            queenDeathBanner.postValue(null);
            honeyCapBanner.postValue(null);
            return;
        }
        swarmBannerIo.execute(() -> {
            List<HiveEntity> list = hiveRepository.getLocalHivesSync(uid);
            swarmRiskBanner.postValue(buildSwarmRiskBanner(list));
            queenDeathBanner.postValue(buildQueenDeathBanner(list));
            honeyCapBanner.postValue(buildHoneyCapBanner(list));
        });
    }

    private void rebuildSwarmRiskBannerFromList(List<HiveEntity> list) {
        swarmRiskBanner.setValue(buildSwarmRiskBanner(list));
    }

    private void rebuildQueenDeathBannerFromList(List<HiveEntity> list) {
        queenDeathBanner.setValue(buildQueenDeathBanner(list));
    }

    private void rebuildHoneyCapBannerFromList(List<HiveEntity> list) {
        honeyCapBanner.setValue(buildHoneyCapBanner(list));
    }

    @Override
    protected void onCleared() {
        if (globalEventRepository != null) {
            globalEventRepository.snapshot().removeObserver(eventObserver);
        }
        swarmBannerIo.shutdown();
        super.onCleared();
    }

    /**
     * Build de depuración: ejecuta un tick de producción para el siguiente día pendiente (ignora la hora de las 8:00).
     */
    public void debugSimulateNextProductionDay(String ownerId, Consumer<String> onMainThreadMessage) {
        hiveRepository.debugSimulateNextProductionDay(ownerId, onMainThreadMessage);
    }

    public void resetGameToStarterState(String ownerId, Consumer<String> onResult) {
        if (ownerId == null || ownerId.isEmpty()) {
            resetBusy.setValue(false);
            onResult.accept("Sesión no válida.");
            return;
        }
        resetBusy.setValue(true);
        ApicultureApp app = (ApicultureApp) getApplication();
        app.resetPlayerToStarterState(ownerId, 0L, msg -> {
            try {
                if (msg == null) {
                    loadAndApplyProgress(ownerId);
                    refreshEconomyDisplay();
                    refreshFloraPlantings(ownerId);
                    refreshGameClock();
                    refreshSwarmRiskBannerNow();
                    refreshInventoryDisplay();
                }
            } finally {
                resetBusy.setValue(false);
            }
            onResult.accept(msg);
        });
    }

    public interface ActionNoticeCallback {
        void onDone(boolean success, String message);
    }

    /** Resultado de «Recolectar» en el dashboard, con desglose por flora. */
    public static final class HarvestAllResult {
        public final boolean success;
        public final String message;
        public final double totalKg;
        public final int hiveCount;
        /** kg por tipo de flora (solo si {@link #success}). */
        @NonNull
        public final Map<String, Double> kgByFlora;

        public final boolean needWarehouse;
        /** El camión va de camino: el resumen espera a que el viaje termine. */
        public final boolean deferred;

        public HarvestAllResult(boolean success, String message, double totalKg, int hiveCount,
                @Nullable Map<String, Double> kgByFlora) {
            this(success, message, totalKg, hiveCount, kgByFlora, false);
        }

        public HarvestAllResult(boolean success, String message, double totalKg, int hiveCount,
                @Nullable Map<String, Double> kgByFlora, boolean needWarehouse) {
            this(success, message, totalKg, hiveCount, kgByFlora, needWarehouse, false);
        }

        public HarvestAllResult(boolean success, String message, double totalKg, int hiveCount,
                @Nullable Map<String, Double> kgByFlora, boolean needWarehouse, boolean deferred) {
            this.success = success;
            this.message = message != null ? message : "";
            this.totalKg = totalKg;
            this.hiveCount = hiveCount;
            this.kgByFlora = kgByFlora == null
                    ? Collections.emptyMap()
                    : Collections.unmodifiableMap(new LinkedHashMap<>(kgByFlora));
            this.needWarehouse = needWarehouse;
            this.deferred = deferred;
        }
    }

    public interface HarvestAllCallback {
        void onDone(@NonNull HarvestAllResult result);
    }

    /**
     * Recolecta de cada colmena la miel por encima de 3 kg de reserva habitual.
     */
    public void previewHarvestAll(@Nullable Consumer<HoneyLogistics.HarvestPlan> onDone) {
        Application ap = getApplication();
        String uid = ownerIdForHives.getValue();
        if (uid == null || uid.isEmpty()) {
            if (onDone != null) {
                onDone.accept(new HoneyLogistics.HarvestPlan(null, 0));
            }
            return;
        }
        swarmBannerIo.execute(() -> {
            List<HiveEntity> list = hiveRepository.getLocalHivesSync(uid);
            HoneyLogistics.HarvestPlan plan = HoneyLogistics.previewHarvestAll(ap, uid, list);
            mainHandler.post(() -> {
                if (onDone != null) {
                    onDone.accept(plan);
                }
            });
        });
    }

    public void withLocalHives(@Nullable Consumer<List<HiveEntity>> onDone) {
        String uid = ownerIdForHives.getValue();
        if (uid == null || uid.isEmpty()) {
            if (onDone != null) {
                onDone.accept(Collections.emptyList());
            }
            return;
        }
        swarmBannerIo.execute(() -> {
            List<HiveEntity> list = hiveRepository.getLocalHivesSync(uid);
            mainHandler.post(() -> {
                if (onDone != null) {
                    onDone.accept(list != null ? list : Collections.emptyList());
                }
            });
        });
    }

    public void commitHarvestAll(@Nullable List<HoneyLogistics.CollectPreview> trips,
            @Nullable HarvestAllCallback onDone) {
        Application ap = getApplication();
        String uid = ownerIdForHives.getValue();
        if (uid == null || uid.isEmpty()) {
            if (onDone != null) {
                onDone.onDone(new HarvestAllResult(false,
                        ap.getString(R.string.dashboard_harvest_all_session), 0, 0, null));
            }
            return;
        }
        swarmBannerIo.execute(() -> {
            HoneyLogistics.HarvestCommit done = HoneyLogistics.commitHarvestBlocking(
                    ap, uid, hiveRepository, economyRepository, trips);
            mainHandler.post(() -> {
                refreshEconomyDisplay();
                refreshSwarmRiskBannerNow();
                refreshEventBanner();
                if (onDone == null) {
                    return;
                }
                if (!done.success) {
                    onDone.onDone(new HarvestAllResult(false,
                            ap.getString(R.string.dashboard_harvest_all_none), 0, 0, null));
                } else {
                    onDone.onDone(new HarvestAllResult(true, "", done.totalKg, done.hiveCount,
                            done.kgByFlora, false, done.deferred));
                }
            });
        });
    }

    /**
     * Pone a la venta en el mercado global todo el stock de miel del almacén.
     */
    public void sellAllHoneyToMarket(@Nullable ActionNoticeCallback onDone) {
        Application ap = getApplication();
        if (onDone == null) {
            return;
        }
        Map<String, Double> buckets = economyRepository.copyHoneyBuckets();
        List<String> keys = new ArrayList<>();
        for (Map.Entry<String, Double> e : buckets.entrySet()) {
            if (e.getValue() != null && e.getValue() > 1e-6) {
                keys.add(e.getKey());
            }
        }
        if (keys.isEmpty()) {
            onDone.onDone(false, ap.getString(R.string.dashboard_sell_all_none));
            return;
        }
        if (marketRepository == null) {
            onDone.onDone(false, ap.getString(R.string.dashboard_sell_all_market));
            return;
        }
        LocalDate today = LocalDate.now(GameCalendar.globalMarketTimeZone());
        marketRepository.refreshGlobalMarketForDay(GameCalendar.toDayKey(today), today.getDayOfYear());
        sellNextBucket(keys, 0, 0.0, 0.0, onDone);
    }

    /**
     * Vende todo el stock de la miel del evento global (el tipo en demanda).
     */
    public void sellEventFloraToMarket(@Nullable ActionNoticeCallback onDone) {
        Application ap = getApplication();
        if (onDone == null) {
            return;
        }
        GlobalEventBanner banner = globalEventBanner.getValue();
        if (banner == null || !banner.canSellEventHoney || banner.floraKey == null
                || banner.floraKey.isEmpty()) {
            onDone.onDone(false, ap.getString(R.string.dashboard_global_event_sell_not_live));
            return;
        }
        String flora = HoneyMarketEngine.canonicalFloraKey(banner.floraKey);
        double kg = economyRepository.getHoneyStockForFlora(flora);
        if (kg <= 1e-6) {
            onDone.onDone(false, ap.getString(R.string.dashboard_global_event_sell_none, flora));
            return;
        }
        if (marketRepository == null) {
            onDone.onDone(false, ap.getString(R.string.dashboard_sell_all_market));
            return;
        }
        LocalDate today = LocalDate.now(GameCalendar.globalMarketTimeZone());
        marketRepository.refreshGlobalMarketForDay(GameCalendar.toDayKey(today), today.getDayOfYear());
        HoneyMarketSnapshot snap = marketRepository.getSnapshot();
        if (snap == null) {
            onDone.onDone(false, ap.getString(R.string.dashboard_sell_all_market));
            return;
        }
        marketRepository.executeGlobalSale(flora, kg, snap, economyRepository,
                new MarketRepository.MarketSaleExecutionCallback() {
                    @Override
                    public void onSuccess(double unitPriceEurPerKg) {
                        refreshEconomyDisplay();
                        refreshEventBanner();
                        onDone.onDone(true, ap.getString(R.string.dashboard_global_event_sell_ok,
                                kg, flora, kg * unitPriceEurPerKg));
                    }

                    @Override
                    public void onFailure(String reasonCodeOrMessage) {
                        String reason = reasonCodeOrMessage != null ? reasonCodeOrMessage : "error";
                        if ("stock".equals(reason)) {
                            onDone.onDone(false, ap.getString(R.string.dashboard_global_event_sell_none, flora));
                            return;
                        }
                        if ("snapshot".equals(reason) || "invalid".equals(reason)) {
                            reason = ap.getString(R.string.dashboard_sell_all_market);
                        }
                        onDone.onDone(false, ap.getString(R.string.market_sell_fail_reason, reason));
                    }
                });
    }

    private void refreshEventBanner() {
        if (globalEventRepository != null) {
            applyGlobalEventSnapshot(globalEventRepository.cached());
        }
    }

    private void sellNextBucket(List<String> keys, int index, double kgSold, double eurSold,
            @NonNull ActionNoticeCallback onDone) {
        Application ap = getApplication();
        if (index >= keys.size()) {
            refreshEconomyDisplay();
            refreshEventBanner();
            onDone.onDone(true, ap.getString(R.string.dashboard_sell_all_ok, kgSold, eurSold));
            return;
        }
        String flora = keys.get(index);
        double kg = economyRepository.getHoneyStockForFlora(flora);
        if (kg <= 1e-6) {
            sellNextBucket(keys, index + 1, kgSold, eurSold, onDone);
            return;
        }
        double price = marketRepository.priceEurPerKgForFlora(flora);
        HoneyLogistics.Result r = HoneyLogistics.dispatchWholesale(
                ap, ownerIdForHives.getValue(), flora, kg, price, economyRepository, marketRepository);
        if (r == HoneyLogistics.Result.NO_CASH) {
            finishSellAll(kgSold, eurSold, ap.getString(R.string.market_order_fail_travel), onDone);
            return;
        }
        if (r == HoneyLogistics.Result.FAILED) {
            finishSellAll(kgSold, eurSold, ap.getString(R.string.market_sell_fail_stock), onDone);
            return;
        }
        sellNextBucket(keys, index + 1, kgSold + kg, eurSold + kg * price, onDone);
    }

    private void finishSellAll(double kgSold, double eurSold, String reason,
            @NonNull ActionNoticeCallback onDone) {
        Application ap = getApplication();
        refreshEconomyDisplay();
        refreshEventBanner();
        if (kgSold > 1e-6) {
            onDone.onDone(true, ap.getString(R.string.dashboard_sell_all_partial, kgSold, eurSold, reason));
        } else {
            onDone.onDone(false, ap.getString(R.string.market_sell_fail_reason, reason));
        }
    }

    private String formatTotalBees(List<HiveEntity> list) {
        NumberFormat nf = NumberFormat.getNumberInstance(new Locale("es", "ES"));
        if (list == null || list.isEmpty()) {
            return nf.format(0);
        }
        int sum = 0;
        for (HiveEntity h : list) {
            if (h == null || h.inWarehouse) {
                continue;
            }
            sum += HivePopulationState.adultWorkersForUi(h, HiveRepository.DEFAULT_BEE_COUNT_PER_HIVE);
        }
        return nf.format(sum);
    }

    private static final class HiveSwarmRisk implements Comparable<HiveSwarmRisk> {
        final String hiveId;
        final String hiveLabel;
        /** Riesgo diario 0–1; puede ser 0 con obreras en el umbral de división (70k) antes del primer escalón. */
        final double formulaRisk;
        /** Incluida en el aviso por población ≥ umbral de división recomendado. */
        final boolean atSplitRecommend;

        HiveSwarmRisk(String hiveId, String hiveLabel, double formulaRisk, boolean atSplitRecommend) {
            this.hiveId = hiveId;
            this.hiveLabel = hiveLabel;
            this.formulaRisk = formulaRisk;
            this.atSplitRecommend = atSplitRecommend;
        }

        @Override
        public int compareTo(HiveSwarmRisk o) {
            double a = Math.max(formulaRisk, atSplitRecommend ? 1e-9 : 0.0);
            double b = Math.max(o.formulaRisk, o.atSplitRecommend ? 1e-9 : 0.0);
            return Double.compare(b, a);
        }
    }

    /**
     * Colmenas con riesgo &gt; 0, ordenadas de mayor a menor probabilidad de enjambrazón.
     */
    private List<HiveSwarmRisk> hivesWithSwarmRiskSorted(List<HiveEntity> list) {
        if (list == null || list.isEmpty()) {
            return Collections.emptyList();
        }
        Application app = getApplication();
        String fallbackName = app.getString(R.string.dashboard_swarm_risk_unnamed_colmena);
        List<HiveSwarmRisk> out = new ArrayList<>();
        for (HiveEntity h : list) {
            if (h == null || h.inWarehouse || h.id == null || h.id.trim().isEmpty()) {
                continue;
            }
            HivePopulationState p = HivePopulationState.fromHiveEntityOrDefault(h,
                    HiveRepository.DEFAULT_BEE_COUNT_PER_HIVE);
            double formulaR = ColonyGameRules.swarmRiskForAdultWorkers(p.workersAdult);
            boolean atRecommend = p.workersAdult >= ColonyGameRules.SPLIT_RECOMMEND_BEES;
            if (formulaR <= 0.0 && !atRecommend) {
                continue;
            }
            String name = (h.name != null && !h.name.trim().isEmpty()) ? h.name.trim() : fallbackName;
            out.add(new HiveSwarmRisk(h.id, name, formulaR, atRecommend));
        }
        Collections.sort(out);
        return out;
    }

    private SwarmRiskBanner buildSwarmRiskBanner(List<HiveEntity> list) {
        Application app = getApplication();
        List<HiveSwarmRisk> atRisk = hivesWithSwarmRiskSorted(list);
        if (atRisk.isEmpty()) {
            return null;
        }
        List<SwarmRiskHiveRow> rows = new ArrayList<>(atRisk.size());
        for (HiveSwarmRisk r : atRisk) {
            rows.add(new SwarmRiskHiveRow(r.hiveId, r.hiveLabel));
        }
        String title = atRisk.size() == 1
                ? app.getString(R.string.dashboard_swarm_risk_title)
                : app.getString(R.string.dashboard_swarm_risk_title_multi, atRisk.size());
        return new SwarmRiskBanner(title, Collections.unmodifiableList(rows));
    }

    private QueenDeathBanner buildQueenDeathBanner(List<HiveEntity> list) {
        if (list == null || list.isEmpty()) {
            return null;
        }
        Application app = getApplication();
        String fallbackName = app.getString(R.string.dashboard_swarm_risk_unnamed_colmena);
        List<QueenDeathHiveRow> rows = new ArrayList<>();
        for (HiveEntity h : list) {
            if (h == null || h.inWarehouse || h.id == null || h.id.trim().isEmpty()) {
                continue;
            }
            HivePopulationState p = HivePopulationState.fromHiveEntityOrDefault(h,
                    HiveRepository.DEFAULT_BEE_COUNT_PER_HIVE);
            if (!p.needsQueenIntroduction()) {
                continue;
            }
            String name = (h.name != null && !h.name.trim().isEmpty()) ? h.name.trim() : fallbackName;
            rows.add(new QueenDeathHiveRow(h.id, name));
        }
        if (rows.isEmpty()) {
            return null;
        }
        String title = rows.size() == 1
                ? app.getString(R.string.dashboard_queen_dead_title)
                : app.getString(R.string.dashboard_queen_dead_title_multi, rows.size());
        return new QueenDeathBanner(title, Collections.unmodifiableList(rows));
    }

    /**
     * Divide todas las colmenas indicadas (p. ej. las del aviso de enjambrazón), en orden.
     */
    public void splitSwarmBannerHives(List<String> hiveIds, Consumer<String> onMainThreadMessage) {
        if (hiveIds == null || hiveIds.isEmpty()) {
            if (onMainThreadMessage != null) {
                onMainThreadMessage.accept(null);
            }
            return;
        }
        hiveRepository.splitHivesSequentialBlockingOrder(hiveIds, msg -> {
            if (onMainThreadMessage != null) {
                onMainThreadMessage.accept(msg);
            }
        });
    }

    public LiveData<SwarmRiskBanner> swarmRiskBanner() {
        return swarmRiskBanner;
    }

    public LiveData<QueenDeathBanner> queenDeathBanner() {
        return queenDeathBanner;
    }

    public LiveData<HoneyCapBanner> honeyCapBanner() {
        return honeyCapBanner;
    }

    @Nullable
    private HoneyCapBanner buildHoneyCapBanner(@Nullable List<HiveEntity> list) {
        if (list == null || list.isEmpty()) {
            return null;
        }
        Application app = getApplication();
        String fallbackName = app.getString(R.string.dashboard_swarm_risk_unnamed_colmena);
        List<HoneyCapHiveRow> rows = new ArrayList<>();
        for (HiveEntity h : list) {
            if (h == null || h.inWarehouse || h.id == null) {
                continue;
            }
            if (!HiveHoneyRules.isHoneyAtCapacity(h)) {
                continue;
            }
            String name = (h.name != null && !h.name.trim().isEmpty()) ? h.name.trim() : fallbackName;
            rows.add(new HoneyCapHiveRow(h.id, name));
        }
        if (rows.isEmpty()) {
            return null;
        }
        rows.sort(Comparator.comparing(r ->
                r.displayName != null ? r.displayName.toLowerCase(Locale.ROOT) : ""));
        int total = rows.size();
        String title = total == 1
                ? app.getString(R.string.dashboard_honey_cap_title)
                : app.getString(R.string.dashboard_honey_cap_title_multi, total);
        return new HoneyCapBanner(title, Collections.unmodifiableList(rows));
    }

    /**
     * Método preparado para que cualquier acción del juego pueda sumar experiencia.
     */
    public void addExperience(int amount) {
        String uid = ownerIdForHives.getValue();
        if (uid == null || uid.isEmpty() || amount <= 0) {
            return;
        }
        LevelSystem.Result result = playerProgressRepository.addXp(uid, amount);
        level = result.level;
        xp = result.xp;
        xpMaxInternal = result.maxXp;
        publishXpUi();
        profileSubtitle.setValue(buildProfileSubtitle());
    }

    private String buildProfileSubtitle() {
        // Texto simple de rango según el nivel actual
        Application app = getApplication();
        String rango;
        if (level >= 10) {
            rango = app.getString(R.string.rank_expert);
        } else if (level >= 5) {
            rango = app.getString(R.string.rank_advanced);
        } else if (level >= 1) {
            rango = app.getString(R.string.rank_beginner);
        } else {
            rango = app.getString(R.string.rank_apprentice);
        }
        return app.getString(R.string.profile_rank_line, level, rango);
    }

    private void publishXpUi() {
        xpMax.setValue(Math.max(1, xpMaxInternal) * 10);
        xpCurrent.setValue((int) Math.round(xp * 10.0));
        xpLabel.setValue(displayXp(xp) + " / " + xpMaxInternal + " XP");
    }

    private static int displayXp(double value) {
        return (int) Math.floor(value + 1e-9);
    }

    public LiveData<String> statCoins() {
        return statCoins;
    }

    public LiveData<String> statHoney() {
        return statHoney;
    }

    public LiveData<Integer> economyRevision() {
        return economyRepository.revision();
    }

    public LiveData<String> statNectar() {
        return statNectar;
    }

    public LiveData<String> seasonText() {
        return seasonText;
    }

    public LiveData<String> dayText() {
        return dayText;
    }

    public LiveData<String> profileName() {
        return profileName;
    }

    public LiveData<Bitmap> profilePhoto() {
        return profilePhoto;
    }

    public LiveData<Boolean> profileReady() {
        return profileReady;
    }

    public LiveData<String> headerHoneyBrand() {
        return headerHoneyBrand;
    }

    public LiveData<String> profileSubtitle() {
        return profileSubtitle;
    }

    public LiveData<String> xpLabel() {
        return xpLabel;
    }

    public LiveData<Integer> xpCurrent() {
        return xpCurrent;
    }

    public LiveData<Integer> xpMax() {
        return xpMax;
    }

    public LiveData<String> eventTitle() {
        return eventTitle;
    }

    public LiveData<String> eventSubtitle() {
        return eventSubtitle;
    }

    public LiveData<GlobalEventBanner> globalEventBanner() {
        return globalEventBanner;
    }

    public LiveData<Boolean> isAdmin() {
        return isAdmin;
    }

    public LiveData<Boolean> resetBusy() {
        return resetBusy;
    }

    public LiveData<Integer> queenInventoryCount() {
        return queenInventoryCount;
    }

    public LiveData<Integer> treatInventoryCount() {
        return treatInventoryCount;
    }

    public LiveData<Integer> feedInventoryCount() {
        return feedInventoryCount;
    }

    public void refreshInventoryDisplay() {
        Application ap = getApplication();
        queenInventoryCount.setValue(EventInventoryStore.queens(ap).size());
        treatInventoryCount.setValue(EventInventoryStore.treatments(ap));
        feedInventoryCount.setValue(EventInventoryStore.feed(ap));
    }

    public List<Integer> queenQualities() {
        return EventInventoryStore.queens(getApplication());
    }

    public void claimGlobalEventReward(@Nullable String uid, Consumer<String> onMain) {
        if (uid == null || globalEventRepository == null) {
            if (onMain != null) {
                onMain.accept("Sesión no válida.");
            }
            return;
        }
        globalEventRepository.claimSurgeRewards(uid, msg -> {
            applyGlobalEventSnapshot(globalEventRepository.cached());
            refreshEconomyDisplay();
            refreshInventoryDisplay();
            if (onMain != null) {
                onMain.accept(msg);
            }
        });
    }

    public void dismissGlobalEvent(@Nullable String uid) {
        if (globalEventRepository == null) {
            return;
        }
        GlobalEventRepository.Snapshot snap = globalEventRepository.cached();
        if (snap == null || !snap.surge.exists()) {
            return;
        }
        globalEventRepository.markDismissed(uid, snap.surge.instanceId());
        applyGlobalEventSnapshot(snap);
    }

    private void applyGlobalEventSnapshot(@Nullable GlobalEventRepository.Snapshot snap) {
        Application ap = getApplication();
        if (snap == null) {
            eventTitle.setValue(ap.getString(R.string.dashboard_no_event_title));
            eventSubtitle.setValue(ap.getString(R.string.dashboard_no_event_subtitle));
            globalEventBanner.setValue(GlobalEventBanner.none(ap));
            return;
        }
        String uid = ownerIdForHives.getValue();
        boolean includeEnded = snap.myKgSold > 1e-6;
        GlobalEventBanner banner = GlobalEventBanner.from(
                ap, snap, globalEventRepository, economyRepository, uid, includeEnded);
        eventTitle.setValue(banner.title);
        eventSubtitle.setValue(banner.subtitle);
        globalEventBanner.setValue(banner);
        if (globalEventRepository == null) {
            return;
        }
        String instanceId = snap.surge.instanceId();
        boolean finished = snap.surge.exists() && snap.surge.isFinished(snap.kgSoldTowardGoal);
        if (!finished || instanceId.isEmpty() || uid == null || uid.isEmpty()) {
            return;
        }
        if (globalEventRepository.hasClaimedLocal(instanceId)
                || globalEventRepository.hasDismissed(uid, instanceId)) {
            return;
        }
        int highest = DemandSurgeMilestones.highestReached(
                snap.kgSoldTowardGoal, snap.surge.targetDemandKg);
        globalEventRepository.hasParticipated(uid, instanceId, participated -> {
            if (!participated) {
                if (!includeEnded) {
                    GlobalEventBanner hidden = GlobalEventBanner.from(
                            ap, snap, globalEventRepository, economyRepository, uid, false);
                    eventTitle.setValue(hidden.title);
                    eventSubtitle.setValue(hidden.subtitle);
                    globalEventBanner.setValue(hidden);
                }
                return;
            }
            GlobalEventBanner shown = GlobalEventBanner.from(
                    ap, snap, globalEventRepository, economyRepository, uid, true);
            eventTitle.setValue(shown.title);
            eventSubtitle.setValue(shown.subtitle);
            globalEventBanner.setValue(shown.withCanClaim(highest >= 15));
        });
    }

    /**
     * Eventos globales para todos los jugadores (tarjeta «Evento activo»). La enjambrazón va aparte en
     * {@link #swarmRiskBanner()}.
     */
    public void setGlobalEventTexts(@Nullable String title, @Nullable String subtitle) {
        Application ap = getApplication();
        eventTitle.setValue(title != null && !title.isEmpty()
                ? title
                : ap.getString(R.string.dashboard_no_event_title));
        eventSubtitle.setValue(subtitle != null && !subtitle.isEmpty()
                ? subtitle
                : ap.getString(R.string.dashboard_no_event_subtitle));
    }

    public static final class GlobalEventBanner {
        public final String title;
        public final String subtitle;
        public final boolean visible;
        public final boolean showProgress;
        public final int progressPct;
        public final String progressLabel;
        public final boolean ended;
        public final boolean canClaim;
        public final boolean claimed;
        public final int highestMilestone;
        public final String myKgLabel;
        public final String statusLabel;
        public final int floraIconRes;
        public final String floraKey;
        public final boolean canSellEventHoney;
        public final String sellButtonLabel;
        public final boolean canDismiss;

        public GlobalEventBanner(String title, String subtitle, boolean visible, boolean showProgress,
                int progressPct, String progressLabel, boolean ended, boolean canClaim, boolean claimed,
                int highestMilestone, String myKgLabel, String statusLabel, int floraIconRes,
                String floraKey, boolean canSellEventHoney, String sellButtonLabel, boolean canDismiss) {
            this.title = title;
            this.subtitle = subtitle;
            this.visible = visible;
            this.showProgress = showProgress;
            this.progressPct = progressPct;
            this.progressLabel = progressLabel != null ? progressLabel : "";
            this.ended = ended;
            this.canClaim = canClaim;
            this.claimed = claimed;
            this.highestMilestone = highestMilestone;
            this.myKgLabel = myKgLabel != null ? myKgLabel : "";
            this.statusLabel = statusLabel != null ? statusLabel : "";
            this.floraIconRes = floraIconRes;
            this.floraKey = floraKey != null ? floraKey : "";
            this.canSellEventHoney = canSellEventHoney;
            this.sellButtonLabel = sellButtonLabel != null ? sellButtonLabel : "";
            this.canDismiss = canDismiss;
        }

        GlobalEventBanner withCanClaim(boolean canClaim) {
            return new GlobalEventBanner(title, subtitle, visible, showProgress, progressPct,
                    progressLabel, ended, canClaim, claimed, highestMilestone, myKgLabel, statusLabel,
                    floraIconRes, floraKey, canSellEventHoney, sellButtonLabel, canDismiss);
        }

        static GlobalEventBanner none(Application ap) {
            return new GlobalEventBanner(
                    ap.getString(R.string.dashboard_no_event_title),
                    ap.getString(R.string.dashboard_no_event_subtitle),
                    false, false, 0, "", false, false, false, 0, "", "", 0, "", false, "", false);
        }

        static GlobalEventBanner from(
                Application ap,
                GlobalEventRepository.Snapshot snap,
                GlobalEventRepository repo,
                @Nullable EconomyRepository economy,
                @Nullable String uid,
                boolean includeFinished) {
            boolean surgeClaimed = snap.surge.exists()
                    && repo != null
                    && repo.hasClaimedLocal(snap.surge.instanceId());
            boolean dismissed = snap.surge.exists()
                    && repo != null
                    && repo.hasDismissed(uid, snap.surge.instanceId());
            boolean surgeGoal = snap.surge.exists()
                    && snap.surge.isGoalComplete(snap.kgSoldTowardGoal);
            boolean surgeFinished = snap.surge.exists()
                    && snap.surge.isFinished(snap.kgSoldTowardGoal);
            boolean showSurge = snap.surge.exists()
                    && !surgeClaimed
                    && !dismissed
                    && (snap.surge.isLive() || (surgeFinished && includeFinished));
            if (showSurge) {
                String flora = snap.surge.floraKey.isEmpty() ? "miel" : snap.surge.floraKey;
                String floraKey = snap.surge.floraKey.isEmpty()
                        ? ""
                        : HoneyMarketEngine.canonicalFloraKey(snap.surge.floraKey);
                int floraIcon = HiveSiteSummaryUi.floraHoneyJarIcon(flora);
                String demandX = formatDemandMult(snap.surge.demandMult);
                String title = floraIcon != 0
                        ? ap.getString(R.string.dashboard_global_event_surge_graphic, demandX)
                        : ap.getString(R.string.dashboard_global_event_surge, flora, demandX);
                int highest = DemandSurgeMilestones.highestReached(
                        snap.kgSoldTowardGoal, snap.surge.targetDemandKg);
                int pct = snap.surge.targetDemandKg <= 1e-9 ? 0
                        : (int) Math.min(100, Math.round(100.0 * snap.kgSoldTowardGoal / snap.surge.targetDemandKg));
                boolean ended = surgeFinished;
                boolean canSell = !ended && !floraKey.isEmpty() && !"miel".equalsIgnoreCase(floraKey);
                double stock = (canSell && economy != null)
                        ? economy.getHoneyStockForFlora(floraKey)
                        : 0.0;
                String sellLabel = "";
                if (canSell) {
                    sellLabel = stock > 1e-6
                            ? ap.getString(R.string.dashboard_global_event_sell_kg, flora, stock)
                            : ap.getString(R.string.dashboard_global_event_sell_flora, flora);
                }
                StringBuilder sub = new StringBuilder();
                if (surgeGoal) {
                    appendLine(sub, ap.getString(R.string.dashboard_global_event_achieved_body));
                } else if (ended) {
                    appendLine(sub, ap.getString(R.string.dashboard_global_event_ended));
                }
                if (snap.shift.isLive()) {
                    appendLine(sub, ap.getString(R.string.dashboard_global_event_shift,
                            signed(snap.shift.demandDeltaPercent),
                            signed(snap.shift.priceDeltaPercent)));
                }
                if (snap.velutina.isLive()) {
                    appendLine(sub, ap.getString(R.string.dashboard_global_event_velutina,
                            snap.velutina.lossPercent));
                }
                String progressLabel = ap.getString(R.string.dashboard_global_event_progress_short,
                        snap.kgSoldTowardGoal, snap.surge.targetDemandKg, pct);
                String myKg = ap.getString(R.string.dashboard_global_event_my_kg_short, snap.myKgSold);
                String status;
                if (surgeGoal) {
                    status = ap.getString(R.string.dashboard_global_event_achieved);
                } else if (ended) {
                    status = ap.getString(R.string.dashboard_global_event_ended_badge);
                } else {
                    status = ap.getString(R.string.dashboard_global_event_live);
                }
                return new GlobalEventBanner(title, sub.toString(), true, true, pct, progressLabel,
                        ended, false, false, highest, myKg, status, floraIcon,
                        floraKey, canSell, sellLabel, ended);
            }
            String live = ap.getString(R.string.dashboard_global_event_live);
            boolean shiftLive = snap.shift.isLive();
            boolean velLive = snap.velutina.isLive();
            if (shiftLive && velLive) {
                if (snap.velutina.startsAtMs >= snap.shift.startsAtMs) {
                    return velutinaBanner(ap, snap, live);
                }
                return shiftBanner(ap, snap, live);
            }
            if (shiftLive) {
                return shiftBanner(ap, snap, live);
            }
            if (velLive) {
                return velutinaBanner(ap, snap, live);
            }
            return none(ap);
        }

        private static GlobalEventBanner shiftBanner(
                Application ap, GlobalEventRepository.Snapshot snap, String live) {
            return new GlobalEventBanner(
                    ap.getString(R.string.dashboard_global_event_kicker),
                    ap.getString(R.string.dashboard_global_event_shift,
                            signed(snap.shift.demandDeltaPercent),
                            signed(snap.shift.priceDeltaPercent)),
                    true, false, 0, "", false, false, false, 0, "", live, 0, "", false, "", false);
        }

        private static GlobalEventBanner velutinaBanner(
                Application ap, GlobalEventRepository.Snapshot snap, String live) {
            return new GlobalEventBanner(
                    ap.getString(R.string.dashboard_global_event_kicker),
                    ap.getString(R.string.dashboard_global_event_velutina, snap.velutina.lossPercent),
                    true, false, 0, "", false, false, false, 0, "", live, 0, "", false, "", false);
        }

        private static void appendLine(StringBuilder sb, String line) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(line);
        }

        private static String signed(int pct) {
            return (pct >= 0 ? "+" : "") + pct;
        }

        @NonNull
        private static String formatDemandMult(double raw) {
            double m = GlobalEventRepository.clampSurgeDemandMult(raw);
            if (Math.abs(m - Math.round(m)) < 0.05) {
                return String.valueOf(Math.round(m));
            }
            return String.format(Locale.getDefault(), "%.1f", m);
        }
    }

    /**
     * Tarjeta de enjambrazón en el dashboard (no confundir con eventos globales).
     */
    public static final class SwarmRiskBanner {
        public final String title;
        public final List<SwarmRiskHiveRow> hiveRows;

        public SwarmRiskBanner(String title, List<SwarmRiskHiveRow> hiveRows) {
            this.title = title;
            this.hiveRows = hiveRows;
        }
    }

    public static final class SwarmRiskHiveRow {
        public final String hiveId;
        public final String displayName;

        public SwarmRiskHiveRow(String hiveId, String displayName) {
            this.hiveId = hiveId;
            this.displayName = displayName;
        }
    }

    /**
     * Aviso de reina muerta o ausente en el dashboard (enlace al detalle para introducir reina).
     */
    public static final class QueenDeathBanner {
        public final String title;
        public final List<QueenDeathHiveRow> hiveRows;

        public QueenDeathBanner(String title, List<QueenDeathHiveRow> hiveRows) {
            this.title = title;
            this.hiveRows = hiveRows;
        }
    }

    public static final class QueenDeathHiveRow {
        public final String hiveId;
        public final String displayName;

        public QueenDeathHiveRow(String hiveId, String displayName) {
            this.hiveId = hiveId;
            this.displayName = displayName;
        }
    }

    /** Aviso de miel al límite de capacidad: título + enlaces al detalle por colmena. */
    public static final class HoneyCapBanner {
        public final String title;
        public final List<HoneyCapHiveRow> hiveRows;

        public HoneyCapBanner(String title, List<HoneyCapHiveRow> hiveRows) {
            this.title = title;
            this.hiveRows = hiveRows;
        }
    }

    public static final class HoneyCapHiveRow {
        public final String hiveId;
        public final String displayName;

        public HoneyCapHiveRow(String hiveId, String displayName) {
            this.hiveId = hiveId;
            this.displayName = displayName;
        }
    }
}
