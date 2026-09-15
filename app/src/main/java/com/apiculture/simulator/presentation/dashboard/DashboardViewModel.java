package com.apiculture.simulator.presentation.dashboard;

import android.app.Application;
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
import com.apiculture.simulator.domain.game.XpAwards;
import com.apiculture.simulator.domain.market.HoneyMarketEngine;
import com.apiculture.simulator.domain.market.HoneyMarketSnapshot;
import com.apiculture.simulator.domain.population.HivePopulationState;
import com.apiculture.simulator.domain.game.GameClock;
import com.apiculture.simulator.domain.game.LevelSystem;
import com.apiculture.simulator.domain.game.Season;
import com.apiculture.simulator.domain.parcel.FloraPlantingProgressRow;
import com.apiculture.simulator.presentation.hive.HiveSiteSummaryUi;

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
    private final MutableLiveData<String> headerHoneyBrand = new MutableLiveData<>();
    private final MutableLiveData<String> profileSubtitle = new MutableLiveData<>();
    private final MutableLiveData<String> xpLabel = new MutableLiveData<>();
    private final MutableLiveData<Integer> xpCurrent = new MutableLiveData<>();
    private final MutableLiveData<Integer> xpMax = new MutableLiveData<>();
    private final MutableLiveData<String> eventTitle = new MutableLiveData<>();
    private final MutableLiveData<String> eventSubtitle = new MutableLiveData<>();
    private final MutableLiveData<GlobalEventBanner> globalEventBanner = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isAdmin = new MutableLiveData<>(false);
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
    private int xp = 0;
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

        NumberFormat nf = NumberFormat.getNumberInstance(new Locale("es", "ES"));
        refreshEconomyDisplay();

        profileName.setValue(application.getString(R.string.dashboard_default_player));
        headerHoneyBrand.setValue("—");
        xpMaxInternal = LevelSystem.xpForLevel(level);
        xpMax.setValue(xpMaxInternal);
        xpCurrent.setValue(xp);
        profileSubtitle.setValue(buildProfileSubtitle());
        xpLabel.setValue(buildXpLabel(nf));

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
        xpMax.setValue(xpMaxInternal);
        xpCurrent.setValue(xp);
        NumberFormat nf = NumberFormat.getNumberInstance(new Locale("es", "ES"));
        xpLabel.setValue(buildXpLabel(nf));
        profileSubtitle.setValue(buildProfileSubtitle());
    }

    /** Carga nombre de jugador y marca de miel desde Firestore para cabecera y tarjeta de perfil. */
    public void refreshPlayerProfile(@Nullable String firebaseUid) {
        if (profileRepository == null || firebaseUid == null || firebaseUid.isEmpty()) {
            profileName.postValue(getApplication().getString(R.string.dashboard_default_player));
            headerHoneyBrand.postValue("—");
            isAdmin.postValue(false);
            return;
        }
        profileRepository.fetchDisplayProfile(firebaseUid, p -> {
            String defPlayer = getApplication().getString(R.string.dashboard_default_player);
            profileName.postValue(p.playerName.isEmpty() ? defPlayer : p.playerName);
            headerHoneyBrand.postValue(p.honeyBrand.isEmpty() ? "—" : p.honeyBrand);
            isAdmin.postValue(AdminRoles.isAdminPlayerName(p.playerName));
        });
    }

    /**
     * Mismo saldo y stock que compra de terrenos / mercado.
     * Enteros sin separador de miles para no confundir “2.000” (dos mil) con “200”.
     */
    public void refreshEconomyDisplay() {
        statCoins.setValue(Long.toString(Math.round(economyRepository.getBalance())));
        statHoney.setValue(Long.toString(Math.round(economyRepository.getHoneyStock())) + " kg");
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

    private void applyGameDateToUi(@Nullable LocalDate gameDate, @NonNull GameClock clock) {
        LocalDate today = LocalDate.now();
        LocalDate d = gameDate != null ? gameDate : today;
        Season season = Season.fromDayOfYear(d.getDayOfYear());
        seasonText.setValue(season.emoji() + " " + season.labelEs());
        if (d.isAfter(today)) {
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("d MMM yyyy", new Locale("es", "ES"));
            dayText.setValue(getApplication().getString(R.string.dashboard_date_simulated, d.format(fmt)));
        } else {
            dayText.setValue(clock.currentDateLabel() + " · " + clock.currentTimeLabel());
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
            onResult.accept("Sesión no válida.");
            return;
        }
        ApicultureApp app = (ApicultureApp) getApplication();
        app.resetPlayerToStarterState(ownerId, 0L, msg -> {
            if (msg == null) {
                loadAndApplyProgress(ownerId);
                refreshEconomyDisplay();
                refreshFloraPlantings(ownerId);
                refreshGameClock();
                refreshSwarmRiskBannerNow();
                refreshInventoryDisplay();
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

        public HarvestAllResult(boolean success, String message, double totalKg, int hiveCount,
                @Nullable Map<String, Double> kgByFlora) {
            this.success = success;
            this.message = message != null ? message : "";
            this.totalKg = totalKg;
            this.hiveCount = hiveCount;
            this.kgByFlora = kgByFlora == null
                    ? Collections.emptyMap()
                    : Collections.unmodifiableMap(new LinkedHashMap<>(kgByFlora));
        }
    }

    public interface HarvestAllCallback {
        void onDone(@NonNull HarvestAllResult result);
    }

    /**
     * Recolecta de cada colmena la miel por encima de 3 kg de reserva habitual.
     */
    public void harvestAllHives(@Nullable HarvestAllCallback onDone) {
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
            List<HiveEntity> list = hiveRepository.getLocalHivesSync(uid);
            double totalKg = 0.0;
            int hiveCount = 0;
            Map<String, Double> byFlora = new LinkedHashMap<>();
            if (list != null) {
                for (HiveEntity h : list) {
                    if (h == null) {
                        continue;
                    }
                    double stock = Math.max(0.0, h.honeyProduction);
                    double min = HiveHoneyRules.HARVEST_ALL_LEAVE_KG;
                    if (stock <= min + 1e-9) {
                        continue;
                    }
                    double harvested = stock - min;
                    h.honeyProduction = min;
                    hiveRepository.saveHive(h);
                    String flora = (h.floraType != null && !h.floraType.isEmpty())
                            ? h.floraType
                            : "Mil flores";
                    String floraKey = HoneyMarketEngine.canonicalFloraKey(flora);
                    economyRepository.addHoney(floraKey, harvested);
                    Double prev = byFlora.get(floraKey);
                    byFlora.put(floraKey, (prev == null ? 0.0 : prev) + harvested);
                    totalKg += harvested;
                    hiveCount++;
                }
            }
            if (totalKg > 1e-9) {
                hiveRepository.grantXp(uid, XpAwards.harvest(totalKg));
            }
            final double kgDone = totalKg;
            final int nDone = hiveCount;
            final Map<String, Double> floraDone = byFlora;
            mainHandler.post(() -> {
                refreshEconomyDisplay();
                refreshSwarmRiskBannerNow();
                refreshEventBanner();
                if (onDone == null) {
                    return;
                }
                if (kgDone <= 1e-9) {
                    onDone.onDone(new HarvestAllResult(false,
                            ap.getString(R.string.dashboard_harvest_all_none), 0, 0, null));
                } else {
                    onDone.onDone(new HarvestAllResult(true, "", kgDone, nDone, floraDone));
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
        HoneyMarketSnapshot snap = marketRepository.getSnapshot();
        if (snap == null) {
            finishSellAll(kgSold, eurSold, ap.getString(R.string.dashboard_sell_all_market), onDone);
            return;
        }
        marketRepository.executeGlobalSale(flora, kg, snap, economyRepository,
                new MarketRepository.MarketSaleExecutionCallback() {
                    @Override
                    public void onSuccess(double unitPriceEurPerKg) {
                        sellNextBucket(keys, index + 1, kgSold + kg, eurSold + kg * unitPriceEurPerKg, onDone);
                    }

                    @Override
                    public void onFailure(String reasonCodeOrMessage) {
                        String reason = reasonCodeOrMessage != null ? reasonCodeOrMessage : "error";
                        if ("stock".equals(reason)) {
                            reason = ap.getString(R.string.market_sell_fail_stock);
                        } else if ("snapshot".equals(reason) || "invalid".equals(reason)) {
                            reason = ap.getString(R.string.dashboard_sell_all_market);
                        }
                        finishSellAll(kgSold, eurSold, reason, onDone);
                    }
                });
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
            if (h == null || h.id == null || h.id.trim().isEmpty()) {
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
            if (h == null || h.id == null || h.id.trim().isEmpty()) {
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
            if (h == null || h.id == null) {
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

        NumberFormat nf = NumberFormat.getNumberInstance(new Locale("es", "ES"));
        xpCurrent.setValue(xp);
        xpMax.setValue(xpMaxInternal);
        xpLabel.setValue(buildXpLabel(nf));
        profileSubtitle.setValue(buildProfileSubtitle());
    }

    private String buildProfileSubtitle() {
        // Texto simple de rango según el nivel actual
        String rango;
        if (level >= 10) {
            rango = "Apicultor experto";
        } else if (level >= 5) {
            rango = "Apicultor avanzado";
        } else if (level >= 1) {
            rango = "Apicultor principiante";
        } else {
            rango = "Aprendiz";
        }
        return "Nivel " + level + " · " + rango;
    }

    private String buildXpLabel(NumberFormat nf) {
        return nf.format(xp) + " / " + nf.format(xpMaxInternal) + " XP";
    }

    public LiveData<String> statCoins() {
        return statCoins;
    }

    public LiveData<String> statHoney() {
        return statHoney;
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

    private void applyGlobalEventSnapshot(@Nullable GlobalEventRepository.Snapshot snap) {
        Application ap = getApplication();
        if (snap == null) {
            eventTitle.setValue(ap.getString(R.string.dashboard_no_event_title));
            eventSubtitle.setValue(ap.getString(R.string.dashboard_no_event_subtitle));
            globalEventBanner.setValue(GlobalEventBanner.none(ap));
            return;
        }
        GlobalEventBanner banner = GlobalEventBanner.from(ap, snap, globalEventRepository, economyRepository);
        eventTitle.setValue(banner.title);
        eventSubtitle.setValue(banner.subtitle);
        globalEventBanner.setValue(banner);
        if (!banner.visible || !banner.ended || !banner.showProgress || banner.claimed
                || globalEventRepository == null) {
            return;
        }
        String uid = ownerIdForHives.getValue();
        String instanceId = snap.surge.instanceId();
        if (uid == null || uid.isEmpty() || instanceId.isEmpty()) {
            return;
        }
        int highest = DemandSurgeMilestones.highestReached(
                snap.kgSoldTowardGoal, snap.surge.targetDemandKg);
        globalEventRepository.hasParticipated(uid, instanceId, participated -> {
            globalEventBanner.setValue(banner.withCanClaim(participated && highest >= 15));
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

        public GlobalEventBanner(String title, String subtitle, boolean visible, boolean showProgress,
                int progressPct, String progressLabel, boolean ended, boolean canClaim, boolean claimed,
                int highestMilestone, String myKgLabel, String statusLabel, int floraIconRes,
                String floraKey, boolean canSellEventHoney, String sellButtonLabel) {
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
        }

        GlobalEventBanner withCanClaim(boolean canClaim) {
            return new GlobalEventBanner(title, subtitle, visible, showProgress, progressPct,
                    progressLabel, ended, canClaim, claimed, highestMilestone, myKgLabel, statusLabel,
                    floraIconRes, floraKey, canSellEventHoney, sellButtonLabel);
        }

        static GlobalEventBanner none(Application ap) {
            return new GlobalEventBanner(
                    ap.getString(R.string.dashboard_no_event_title),
                    ap.getString(R.string.dashboard_no_event_subtitle),
                    false, false, 0, "", false, false, false, 0, "", "", 0, "", false, "");
        }

        static GlobalEventBanner from(
                Application ap,
                GlobalEventRepository.Snapshot snap,
                GlobalEventRepository repo,
                @Nullable EconomyRepository economy) {
            if (snap.surge.exists() && (snap.surge.isLive() || snap.surge.isEnded())) {
                String flora = snap.surge.floraKey.isEmpty() ? "miel" : snap.surge.floraKey;
                String floraKey = snap.surge.floraKey.isEmpty()
                        ? ""
                        : HoneyMarketEngine.canonicalFloraKey(snap.surge.floraKey);
                int floraIcon = HiveSiteSummaryUi.floraHoneyJarIcon(flora);
                String title = floraIcon != 0
                        ? ap.getString(R.string.dashboard_global_event_surge_graphic)
                        : ap.getString(R.string.dashboard_global_event_surge, flora);
                int highest = DemandSurgeMilestones.highestReached(
                        snap.kgSoldTowardGoal, snap.surge.targetDemandKg);
                int pct = snap.surge.targetDemandKg <= 1e-9 ? 0
                        : (int) Math.min(100, Math.round(100.0 * snap.kgSoldTowardGoal / snap.surge.targetDemandKg));
                boolean ended = snap.surge.isEnded();
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
                if (ended) {
                    appendLine(sub, ap.getString(R.string.dashboard_global_event_ended));
                }
                boolean claimed = repo != null && repo.hasClaimedLocal(snap.surge.instanceId());
                if (claimed) {
                    appendLine(sub, ap.getString(R.string.dashboard_global_event_claimed));
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
                String status = ended
                        ? ap.getString(R.string.dashboard_global_event_ended_badge)
                        : ap.getString(R.string.dashboard_global_event_live);
                return new GlobalEventBanner(title, sub.toString(), true, true, pct, progressLabel,
                        ended, false, claimed, highest, myKg, status, floraIcon,
                        floraKey, canSell, sellLabel);
            }
            String live = ap.getString(R.string.dashboard_global_event_live);
            if (snap.shift.isLive()) {
                return new GlobalEventBanner(
                        ap.getString(R.string.dashboard_global_event_kicker),
                        ap.getString(R.string.dashboard_global_event_shift,
                                signed(snap.shift.demandDeltaPercent),
                                signed(snap.shift.priceDeltaPercent)),
                        true, false, 0, "", false, false, false, 0, "", live, 0, "", false, "");
            }
            if (snap.velutina.isLive()) {
                return new GlobalEventBanner(
                        ap.getString(R.string.dashboard_global_event_kicker),
                        ap.getString(R.string.dashboard_global_event_velutina, snap.velutina.lossPercent),
                        true, false, 0, "", false, false, false, 0, "", live, 0, "", false, "");
            }
            return none(ap);
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
