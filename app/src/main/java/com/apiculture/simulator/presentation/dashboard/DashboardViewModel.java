package com.apiculture.simulator.presentation.dashboard;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.apiculture.simulator.ApicultureApp;
import com.apiculture.simulator.R;
import com.apiculture.simulator.data.local.entity.HiveEntity;
import com.apiculture.simulator.data.repository.ApiaryLocationProvider;
import com.apiculture.simulator.data.repository.EconomyRepository;
import com.apiculture.simulator.data.repository.HiveRepository;
import com.apiculture.simulator.data.repository.WeatherRepository;
import com.apiculture.simulator.domain.game.ColonyGameRules;
import com.apiculture.simulator.domain.game.HiveHoneyRules;
import com.apiculture.simulator.domain.population.HivePopulationState;
import com.apiculture.simulator.domain.game.GameClock;
import com.apiculture.simulator.domain.game.LevelSystem;
import com.apiculture.simulator.domain.game.Season;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
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
    private final MutableLiveData<String> profileSubtitle = new MutableLiveData<>();
    private final MutableLiveData<String> xpLabel = new MutableLiveData<>();
    private final MutableLiveData<Integer> xpCurrent = new MutableLiveData<>();
    private final MutableLiveData<Integer> xpMax = new MutableLiveData<>();
    private final MutableLiveData<String> weatherTemp = new MutableLiveData<>();
    private final MutableLiveData<String> weatherHint = new MutableLiveData<>();
    private final MutableLiveData<String> weatherLocation = new MutableLiveData<>();
    private final LiveData<String> summaryText;
    private final MutableLiveData<String> eventTitle = new MutableLiveData<>();
    private final MutableLiveData<String> eventSubtitle = new MutableLiveData<>();
    private final LiveData<Boolean> showSplitHiveHint;
    private final MediatorLiveData<SwarmRiskBanner> swarmRiskBanner = new MediatorLiveData<>();
    private final MediatorLiveData<HoneyCapBanner> honeyCapBanner = new MediatorLiveData<>();

    // Estado interno sencillo de progreso del jugador
    private int level = 0;
    private int xp = 0;
    private int xpMaxInternal;

    private final WeatherRepository weatherRepository = new WeatherRepository();
    private final ApiaryLocationProvider apiaryLocationProvider;
    private final HiveRepository hiveRepository;
    private final EconomyRepository economyRepository;
    /** Room prohíbe consultas síncronas en el hilo principal; el refresco del aviso de enjambrazón va aquí. */
    private final ExecutorService swarmBannerIo = Executors.newSingleThreadExecutor();

    public DashboardViewModel(@NonNull Application application) {
        super(application);
        ApicultureApp app = (ApicultureApp) application;
        hiveRepository = app.getHiveRepository();
        economyRepository = app.getEconomyRepository();
        LiveData<List<HiveEntity>> hivesLive =
                Transformations.switchMap(ownerIdForHives, hiveRepository::getLocalHives);
        statNectar = Transformations.map(hivesLive, this::formatTotalBees);
        summaryText = Transformations.map(hivesLive, this::formatColmenasSummary);
        showSplitHiveHint = Transformations.map(hivesLive, this::anyHiveOverSplitRecommend);
        swarmRiskBanner.addSource(hivesLive, this::rebuildSwarmRiskBannerFromList);
        honeyCapBanner.addSource(hivesLive, this::rebuildHoneyCapBannerFromList);

        apiaryLocationProvider = new ApiaryLocationProvider(application);
        GameClock clock = new GameClock();
        Season season = clock.currentSeason();
        seasonText.setValue(season.emoji() + " " + season.labelEs());
        // Calendario y reloj reales
        dayText.setValue(clock.currentDateLabel() + " · " + clock.currentTimeLabel());

        NumberFormat nf = NumberFormat.getNumberInstance(new Locale("es", "ES"));
        refreshEconomyDisplay();

        profileName.setValue("Apicultor");
        xpMaxInternal = LevelSystem.xpForLevel(level);
        xpMax.setValue(xpMaxInternal);
        xpCurrent.setValue(xp);
        profileSubtitle.setValue(buildProfileSubtitle());
        xpLabel.setValue(buildXpLabel(nf));

        weatherTemp.setValue("—");
        weatherHint.setValue("Clima hoy en tu zona");
        weatherLocation.setValue("Buscando estación y apiario…");

        // Coordenadas reales del apiario: usamos una colmena del jugador como referencia.
        apiaryLocationProvider.resolveApiaryLocation(new ApiaryLocationProvider.Callback() {
            @Override
            public void onLocationAvailable(double lat, double lng, String apiaryLabel) {
                weatherLocation.postValue(apiaryLabel);
                weatherRepository.fetchCurrentTemperature(lat, lng, new WeatherRepository.Callback() {
                    @Override
                    public void onSuccess(String temperatureLabel, String stationLabel) {
                        weatherTemp.postValue(temperatureLabel);
                        weatherHint.postValue("Clima hoy en tu zona");
                        weatherLocation.postValue(stationLabel + " · " + apiaryLabel);
                    }

                    @Override
                    public void onError(Throwable t) {
                        weatherHint.postValue("No se pudo cargar el clima");
                    }
                });
            }

            @Override
            public void onNoLocation() {
                weatherHint.postValue("Añade al menos una colmena para ver el clima del apiario");
                weatherLocation.postValue("Sin apiario configurado");
            }
        });

        eventTitle.setValue(getApplication().getString(R.string.dashboard_no_event_title));
        eventSubtitle.setValue(getApplication().getString(R.string.dashboard_no_event_subtitle));

        ownerIdForHives.setValue("");
    }

    /**
     * Enlaza las colmenas locales del usuario para total de abejas y resumen (debe llamarse desde la UI con sesión iniciada).
     */
    public void bindHivesForUser(@Nullable String firebaseUid) {
        ownerIdForHives.setValue(firebaseUid != null ? firebaseUid : "");
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
     * Fuerza a recalcular la tarjeta de enjambrazón desde Room (volver al dashboard no siempre re-emite {@code hivesLive}).
     * La lectura a Room no puede hacerse en el hilo UI ({@link IllegalStateException} de Room).
     */
    public void refreshSwarmRiskBannerNow() {
        String uid = ownerIdForHives.getValue();
        if (uid == null || uid.isEmpty()) {
            swarmRiskBanner.postValue(null);
            honeyCapBanner.postValue(null);
            return;
        }
        swarmBannerIo.execute(() -> {
            List<HiveEntity> list = hiveRepository.getLocalHivesSync(uid);
            swarmRiskBanner.postValue(buildSwarmRiskBanner(list));
            honeyCapBanner.postValue(buildHoneyCapBanner(list));
        });
    }

    private void rebuildSwarmRiskBannerFromList(List<HiveEntity> list) {
        swarmRiskBanner.setValue(buildSwarmRiskBanner(list));
    }

    private void rebuildHoneyCapBannerFromList(List<HiveEntity> list) {
        honeyCapBanner.setValue(buildHoneyCapBanner(list));
    }

    @Override
    protected void onCleared() {
        swarmBannerIo.shutdown();
        super.onCleared();
    }

    /**
     * Build de depuración: ejecuta un tick de producción para el siguiente día pendiente (ignora la hora de las 8:00).
     */
    public void debugSimulateNextProductionDay(String ownerId, Consumer<String> onMainThreadMessage) {
        hiveRepository.debugSimulateNextProductionDay(ownerId, onMainThreadMessage);
    }

    /**
     * Reinicia colmenas y producción a estado inicial (3 colmenas × ~25k abejas). {@code onResult} recibe
     * {@code null} si hubo éxito.
     */
    public void resetGameToStarterState(String ownerId, Consumer<String> onResult) {
        hiveRepository.resetGameToStarterState(ownerId, onResult);
    }

    private String formatTotalBees(List<HiveEntity> list) {
        NumberFormat nf = NumberFormat.getNumberInstance(new Locale("es", "ES"));
        if (list == null || list.isEmpty()) {
            return nf.format(0);
        }
        int sum = 0;
        for (HiveEntity h : list) {
            sum += h.beeCount;
        }
        return nf.format(sum);
    }

    private boolean anyHiveOverSplitRecommend(List<HiveEntity> list) {
        if (list == null || list.isEmpty()) {
            return false;
        }
        for (HiveEntity h : list) {
            if (h == null) {
                continue;
            }
            HivePopulationState p = HivePopulationState.fromHiveEntityOrDefault(h,
                    HiveRepository.DEFAULT_BEE_COUNT_PER_HIVE);
            if (p.workersAdult >= ColonyGameRules.SPLIT_RECOMMEND_BEES) {
                return true;
            }
        }
        return false;
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

    private String formatColmenasSummary(List<HiveEntity> list) {
        NumberFormat nf = NumberFormat.getNumberInstance(new Locale("es", "ES"));
        nf.setMinimumFractionDigits(0);
        nf.setMaximumFractionDigits(2);
        if (list == null || list.isEmpty()) {
            return "0 colmenas activas · 0 abejas · Miel total 0 kg.";
        }
        int sumBees = 0;
        double honeyKg = 0.0;
        for (HiveEntity h : list) {
            sumBees += h.beeCount;
            honeyKg += Math.max(0.0, h.honeyProduction);
        }
        return list.size() + " colmenas activas · " + nf.format(sumBees) + " abejas · Miel total "
                + nf.format(honeyKg) + " kg.";
    }

    /**
     * Método preparado para que cualquier acción del juego pueda sumar experiencia.
     */
    public void addExperience(int amount) {
        LevelSystem.Result result = LevelSystem.addXp(level, xp, amount);
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

    public LiveData<String> weatherTemp() {
        return weatherTemp;
    }

    public LiveData<String> weatherHint() {
        return weatherHint;
    }

    public LiveData<String> weatherLocation() {
        return weatherLocation;
    }

    public LiveData<String> summaryText() {
        return summaryText;
    }

    public LiveData<String> eventTitle() {
        return eventTitle;
    }

    public LiveData<String> eventSubtitle() {
        return eventSubtitle;
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

    public LiveData<Boolean> showSplitHiveHint() {
        return showSplitHiveHint;
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
