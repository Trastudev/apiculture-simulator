package com.apiculture.simulator.presentation.hive;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.text.InputType;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import com.apiculture.simulator.presentation.common.GameNotice;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.apiculture.simulator.presentation.tutorial.TutorialBus;
import com.apiculture.simulator.presentation.tutorial.TutorialEvent;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import com.apiculture.simulator.ApicultureApp;
import com.apiculture.simulator.R;
import com.apiculture.simulator.data.local.entity.HiveEntity;
import com.apiculture.simulator.data.local.entity.TruckTripEntity;
import com.apiculture.simulator.data.remote.OpenMeteoElevation;
import com.apiculture.simulator.data.repository.EventInventoryStore;
import com.apiculture.simulator.data.repository.HoneyLogistics;
import com.apiculture.simulator.data.repository.HiveLast6DaysCharts;
import com.apiculture.simulator.data.repository.HiveRepository;
import com.apiculture.simulator.data.repository.TruckLiveTrips;
import com.apiculture.simulator.data.repository.IberiaHexOverlayStore;
import com.apiculture.simulator.data.repository.WeatherRepository;
import com.apiculture.simulator.databinding.DialogSuperPurchaseBinding;
import com.apiculture.simulator.databinding.FragmentHiveDetailBinding;
import com.apiculture.simulator.domain.game.ColonyGameRules;
import com.apiculture.simulator.domain.game.DailySkyCondition;
import com.apiculture.simulator.domain.game.DailyWeather;
import com.apiculture.simulator.domain.game.GameCalendar;
import com.apiculture.simulator.domain.game.HexNectarRules;
import com.apiculture.simulator.domain.game.Hemispheres;
import com.apiculture.simulator.domain.game.IberianClimateZone;
import com.apiculture.simulator.domain.game.SouthernAfricanClimateZone;
import com.apiculture.simulator.domain.game.HiveCareRules;
import com.apiculture.simulator.domain.game.HiveFeedingBonuses;
import com.apiculture.simulator.domain.game.HiveHoneyRules;
import com.apiculture.simulator.domain.game.HiveHoneyStocks;
import com.apiculture.simulator.domain.game.NpcContractCatalog;
import com.apiculture.simulator.domain.game.TranshumanceRules;
import com.apiculture.simulator.domain.game.TruckTripRules;
import com.apiculture.simulator.domain.market.HoneyMarketEngine;
import com.apiculture.simulator.domain.health.HiveHealthAlerts;
import com.apiculture.simulator.domain.parcel.HexApiary;
import com.apiculture.simulator.domain.parcel.HexParcel;
import com.apiculture.simulator.domain.population.HivePopulationState;
import com.apiculture.simulator.presentation.common.SimpleViewModelFactory;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

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

import com.apiculture.simulator.data.session.PlayerAuth;
import com.apiculture.simulator.data.session.SignedInUser;

public class HiveDetailFragment extends Fragment {
    private FragmentHiveDetailBinding binding;
    private HiveViewModel hiveViewModel;
    private String hiveId;
    /** Evita aplicar una respuesta de Open-Meteo obsoleta si el usuario cambia de colmena rápido. */
    private int weatherFetchSeq;
    private String lastChartsHiveId;
    private int lastChartsSummaryDayKey = Integer.MIN_VALUE;
    private List<HiveEntity> yardHives = Collections.emptyList();
    private HiveEntity lastBoundHive;
    private final Handler tripHandler = new Handler(Looper.getMainLooper());
    private final Runnable tripTick = this::refreshTravelBanner;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentHiveDetailBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onDestroyView() {
        tripHandler.removeCallbacks(tripTick);
        binding = null;
        super.onDestroyView();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ApicultureApp app = (ApicultureApp) requireActivity().getApplication();
        hiveViewModel = new ViewModelProvider(this,
                new SimpleViewModelFactory<>(() -> new HiveViewModel(app.getHiveRepository(),
                        app.getHexParcelRepository())))
                .get(HiveViewModel.class);

        hiveId = getArguments() != null ? getArguments().getString("hiveId") : null;
        if (hiveId == null) {
            GameNotice.show(requireContext(), R.string.hive_missing_id);
            return;
        }

        binding.btnHivePrev.setOnClickListener(v -> stepYardHive(-1));
        binding.btnHiveNext.setOnClickListener(v -> stepYardHive(1));

        SignedInUser user = PlayerAuth.getInstance().getCurrentUser();
        final String uid = user != null ? user.getUid() : "";
        hiveViewModel.hives(uid).observe(getViewLifecycleOwner(), list -> {
            yardHives = list != null ? list : Collections.emptyList();
            bindYardNav(lastBoundHive);
        });

        TruckLiveTrips.observe(requireContext()).observe(getViewLifecycleOwner(), trips -> refreshTravelBanner());

        hiveViewModel.setViewingHiveId(hiveId);
        hiveViewModel.viewingHive().observe(getViewLifecycleOwner(), hive -> {
            if (hive == null) {
                return;
            }
            if (uid.isEmpty() || hive.ownerId == null || !uid.equals(hive.ownerId)) {
                GameNotice.show(requireContext(), R.string.map_other_player_hive);
                Navigation.findNavController(binding.getRoot()).popBackStack();
                return;
            }
            if (!hive.id.equals(hiveId)) {
                hiveId = hive.id;
                weatherFetchSeq++;
                lastChartsHiveId = null;
                lastChartsSummaryDayKey = Integer.MIN_VALUE;
                if (getArguments() != null) {
                    getArguments().putString("hiveId", hiveId);
                }
                binding.getRoot().scrollTo(0, 0);
            }
            lastBoundHive = hive;
            bindYardNav(hive);
            bindPendingContractMove(hive);

            HivePopulationState pop = HivePopulationState.fromHiveEntityOrDefault(hive,
                    HiveRepository.DEFAULT_BEE_COUNT_PER_HIVE);

            // Cabecera
            binding.tvHiveName.setText(hive.name);
            binding.ivHiveHoneyCapWarn.setVisibility(View.GONE);
            binding.tvHiveHoneyCapWarn.setVisibility(View.GONE);
            binding.ivHiveSwarmDangerIcon.setVisibility(View.GONE);
            binding.tvHiveSwarmWarn.setVisibility(View.GONE);
            binding.tvHiveHeaderHoney.setText(String.format(Locale.getDefault(), "%.2f kg",
                    Math.max(0.0, hive.honeyProduction)));
            binding.tvHiveHeaderBees.setText(String.format(Locale.getDefault(), "%,d", pop.workersAdult));
            bindHeaderQueen(hive, pop);
            bindHiveAlertChips(hive, pop);

            int elevForHeader = hive.elevationMeters >= 0
                    ? hive.elevationMeters
                    : OpenMeteoElevation.FALLBACK_METERS;
            String skyKey = "hive:" + hive.id;
            int productionDayKey = hive.lastSummaryDayKey > 0
                    ? hive.lastSummaryDayKey
                    : GameCalendar.toDayKey(LocalDate.now(GameCalendar.userTimeZone()));
            bindFloraMonthChart(hive, GameCalendar.fromDayKey(productionDayKey));
            DailySkyCondition headerSky = DailySkyCondition.forHexElevationAndDay(
                    skyKey, productionDayKey, elevForHeader);
            binding.ivHiveHeaderWeather.setImageResource(weatherIconRes(headerSky));
            binding.ivHiveHeaderWeather.setContentDescription(
                    getString(R.string.hive_detail_weather_icon_cd) + " (" + headerSky.emoji() + ")");

            String zoneLabel;
            if (Hemispheres.isSouthern(hive.lat)) {
                SouthernAfricanClimateZone za = SouthernAfricanClimateZone.forHive(
                        hive.lat, hive.lng,
                        hive.elevationMeters >= 0 ? hive.elevationMeters : -1);
                zoneLabel = za.label(requireContext());
            } else {
                IberianClimateZone zone = IberianClimateZone.forHive(hive.lat, hive.lng, elevForHeader);
                zoneLabel = zone.label(requireContext());
            }
            int year = GameCalendar.fromDayKey(productionDayKey).getYear();
            double vint = HexNectarRules.vintageFactor(hive.hexId, hive.floraType, year);
            int vintPct = (int) Math.round(vint * 100);
            binding.tvClimateLine.setText(getString(R.string.hive_climate_line,
                    zoneLabel, HexNectarRules.vintageLabel(requireContext(), vint), vintPct));

            LocalDate meanTempDay = GameCalendar.fromDayKey(productionDayKey).minusDays(1);
            boolean sameDayCharts = hive.id.equals(lastChartsHiveId)
                    && hive.lastSummaryDayKey == lastChartsSummaryDayKey;
            if (!sameDayCharts) {
                lastChartsHiveId = hive.id;
                lastChartsSummaryDayKey = hive.lastSummaryDayKey;
                binding.tvHiveHeaderWeatherTemp.setText("—");
                final int fetchId = ++weatherFetchSeq;
                final double lat = hive.lat;
                final double lng = hive.lng;
                WeatherRepository weatherRepo = app.getWeatherRepository();
                new Thread(() -> {
                    DailyWeather dayW = weatherRepo.fetchCalendarDayWeatherBlocking(
                            lat, lng, meanTempDay);
                    requireActivity().runOnUiThread(() -> {
                        if (!isAdded() || binding == null || fetchId != weatherFetchSeq) {
                            return;
                        }
                        Double mean = dayW != null ? dayW.meanTempC : null;
                        if (mean == null || Double.isNaN(mean)) {
                            binding.tvHiveHeaderWeatherTemp.setText("—");
                        } else {
                            binding.tvHiveHeaderWeatherTemp.setText(
                                    String.format(Locale.getDefault(), "%.0f°C", mean));
                        }
                        DailySkyCondition obs = DailySkyCondition.forHiveDay(
                                skyKey, productionDayKey, elevForHeader, dayW);
                        binding.ivHiveHeaderWeather.setImageResource(weatherIconRes(obs));
                        binding.ivHiveHeaderWeather.setContentDescription(
                                getString(R.string.hive_detail_weather_icon_cd) + " (" + obs.emoji() + ")");
                    });
                }).start();
            }

            NumberFormat popNf = NumberFormat.getNumberInstance(new Locale("es", "ES"));
            binding.tvPopHeadline.setText(getString(R.string.hive_detail_pop_headline_obreras,
                    popNf.format(pop.workersAdult)));
            binding.tvPopCompactEggs.setText(popNf.format(pop.broodEggs()));
            binding.tvPopCompactLarvae.setText(popNf.format(pop.broodLarvae()));
            binding.tvPopCompactPupae.setText(popNf.format(pop.broodPupae()));
            binding.tvPopCompactWorkers.setText(popNf.format(pop.workersAdult));
            binding.tvBroodEgg.setText(popNf.format(pop.broodEggs()));
            binding.tvBroodLarva.setText(popNf.format(pop.broodLarvae()));
            binding.tvBroodPupa.setText(popNf.format(pop.broodPupae()));
            binding.tvPopQueenTrend.setText(String.format(Locale.getDefault(),
                    "%s  ·  Tendencia: %s",
                    pop.queenStatusLabelEs(),
                    pop.lastTrend.labelEs()));
            binding.tvQueenGenetic.setText(
                    getString(R.string.hive_detail_queen_genetic_pct, hive.queenGeneticQuality));
            binding.tvQueenGeneticHint.setText(getString(R.string.hive_detail_queen_genetic_hint,
                    HiveCareRules.STARTER_QUEEN_QUALITY_MIN,
                    HiveCareRules.STARTER_QUEEN_QUALITY_MAX,
                    HiveCareRules.QUEEN_QUALITY_MIN,
                    HiveCareRules.QUEEN_QUALITY_MAX));
            int todayKey = GameCalendar.toDayKey(LocalDate.now(GameCalendar.userTimeZone()));
            int feedLeft = HiveFeedingBonuses.feedingDaysRemaining(hive, todayKey);
            binding.tvFeedingBonusStatus.setVisibility(View.GONE);
            bindCareStatus(hive, feedLeft);

            int healthColor = healthBarColor(requireContext(), hive.health);
            binding.barHealth.setProgress(hive.health);
            binding.barHealth.setProgressTintList(ColorStateList.valueOf(healthColor));
            binding.tvHealthPercent.setText(String.format(Locale.getDefault(), "%d %%", hive.health));
            binding.tvHealthPercent.setTextColor(healthColor);

            int varroaProg = (int) Math.min(40, Math.max(0, Math.round(hive.varroaPct)));
            int varroaColor = varroaBarColor(requireContext(), hive.varroaPct);
            binding.barVarroa.setProgress(varroaProg);
            binding.barVarroa.setProgressTintList(ColorStateList.valueOf(varroaColor));
            binding.tvVarroaPercent.setText(HiveHealthAlerts.formatVarroaPct(hive));
            binding.tvVarroaPercent.setTextColor(varroaColor);
            binding.tvHiveHeaderHealth.setText(String.format(Locale.getDefault(), "%d %%", hive.health));
            binding.tvHiveHeaderHealth.setTextColor(healthColor);
            binding.tvHiveHeaderVarroa.setText(HiveHealthAlerts.formatVarroaPct(hive));
            binding.tvHiveHeaderVarroa.setTextColor(varroaColor);
            binding.tvHiveHeaderSupers.setVisibility(View.GONE);
            binding.btnBuySuperTop.setVisibility(View.GONE);
            binding.tvHiveName.setContentDescription(getString(R.string.hive_rename_title));
            binding.tvHiveName.setOnClickListener(v -> showRenameHiveDialog(hive));

            binding.tvCompactLastEggs.setText(getString(R.string.hive_detail_compact_last_eggs,
                    popNf.format(pop.lastDayEggsLaid)));
            int netWorkers = pop.lastDayWorkerEmergences - pop.lastDayWorkerDeaths;
            String deltaStr;
            if (netWorkers > 0) {
                deltaStr = "+" + popNf.format(netWorkers);
            } else {
                deltaStr = popNf.format(netWorkers);
            }
            if (netWorkers < 0) {
                binding.tvCompactWorkerChange.setText(
                        getString(R.string.hive_detail_compact_worker_decrease, deltaStr));
            } else {
                binding.tvCompactWorkerChange.setText(
                        getString(R.string.hive_detail_compact_worker_change, deltaStr));
            }
            if (netWorkers > 0) {
                binding.ivCompactWorkerTrend.setVisibility(View.VISIBLE);
                binding.ivCompactWorkerTrend.setImageResource(R.drawable.ic_trend_arrow_up);
                binding.ivCompactWorkerTrend.setContentDescription(
                        getString(R.string.cd_worker_trend_up));
                binding.tvCompactWorkerChange.setTextColor(
                        ContextCompat.getColor(requireContext(), R.color.dash_good));
            } else if (netWorkers < 0) {
                binding.ivCompactWorkerTrend.setVisibility(View.VISIBLE);
                binding.ivCompactWorkerTrend.setImageResource(R.drawable.ic_trend_arrow_down);
                binding.ivCompactWorkerTrend.setContentDescription(
                        getString(R.string.cd_worker_trend_down));
                binding.tvCompactWorkerChange.setTextColor(
                        ContextCompat.getColor(requireContext(), R.color.dash_bad));
            } else {
                binding.ivCompactWorkerTrend.setVisibility(View.GONE);
                binding.tvCompactWorkerChange.setTextColor(
                        ContextCompat.getColor(requireContext(), R.color.dash_text_card));
            }

            if (hive.varroaTreatmentDaysRemaining > 0) {
                binding.tvTreatStatus.setBackgroundColor(
                        ContextCompat.getColor(requireContext(), R.color.dash_soft_green));
                binding.tvTreatStatus.setText(String.format(Locale.getDefault(),
                        "Tratamiento antivarroa activo · %d días restantes",
                        hive.varroaTreatmentDaysRemaining));
            } else if (hive.varroaReboundDaysRemaining > 0) {
                binding.tvTreatStatus.setBackgroundColor(
                        ContextCompat.getColor(requireContext(), R.color.dash_soft_yellow));
                binding.tvTreatStatus.setText(String.format(Locale.getDefault(),
                        "Rebote tras tratamiento · vigilar %d días",
                        hive.varroaReboundDaysRemaining));
            } else {
                binding.tvTreatStatus.setBackgroundColor(
                        ContextCompat.getColor(requireContext(), R.color.dash_soft_blue));
                binding.tvTreatStatus.setText(getString(R.string.hive_treat_none));
            }

            List<HiveHealthAlerts.Tag> alertTags = HiveHealthAlerts.alertTags(
                    requireContext(), hive, pop, forage01(hive));
            if (alertTags.isEmpty()) {
                binding.tvHealthAlerts.setText(getString(R.string.hive_alerts_none));
            } else {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < alertTags.size(); i++) {
                    if (i > 0) {
                        sb.append(" · ");
                    }
                    sb.append(alertTags.get(i).text);
                }
                binding.tvHealthAlerts.setText(sb.toString());
            }

            // Pastillas: miel / flora y elevación
            String floraLabel = hive.floraType != null
                    ? hive.floraType
                    : getString(R.string.hive_flora_unknown_label);
            setFloraHeroJar(HiveSiteSummaryUi.floraHoneyJarIcon(hive.floraType));
            binding.tvHoneyFloraTitle.setText(getString(R.string.hive_detail_miel_de, floraLabel));
            bindForageBloom(hive);
            binding.btnChangeForage.setOnClickListener(v -> showChangeForageDialog(hive));
            bindHoneyPantry(hive);
            if (hive.elevationMeters >= 0) {
                binding.tvElevationMeters.setText(getString(R.string.hive_elevation_meters, hive.elevationMeters));
            } else {
                binding.tvElevationMeters.setText(R.string.hive_elevation_pending_meters);
            }
            binding.tvElevationBand.setText(HiveSiteSummaryUi.elevationBandLabel(requireContext(),
                    hive.elevationMeters));

            // Stock actual en la colmena (no es producción diaria)
            NumberFormat nf = NumberFormat.getNumberInstance(new Locale("es", "ES"));
            nf.setMinimumFractionDigits(1);
            nf.setMaximumFractionDigits(2);
            double stockKg = Math.max(0.0, hive.honeyProduction);
            binding.tvHoneyStockInHive.setText(getString(R.string.hive_honey_in_hive, nf.format(stockKg)));

            if (!sameDayCharts) {
            hiveViewModel.loadLast6DaysHiveCharts(hive.id, hive.ownerId, charts -> {
                if (!isAdded() || binding == null) {
                    return;
                }
                // Recolección bruta (pecoreo). El consumo va en el gráfico de abajo.
                double[] values = charts.honeyKg;
                int n = values.length;
                double totalPeriod = 0.0;
                for (double v : values) {
                    totalPeriod += v;
                }
                double avgDaily = n > 0 ? totalPeriod / n : 0.0;
                NumberFormat gramNf = NumberFormat.getIntegerInstance(new Locale("es", "ES"));
                binding.tvHoneyTotal7d.setText(getString(R.string.hive_chart_honey_period_total,
                        gramNf.format(Math.round(totalPeriod * 1000.0))));
                binding.tvHoneyAvg7d.setText(getString(R.string.hive_chart_honey_avg_daily,
                        gramNf.format(Math.round(avgDaily * 1000.0))));
                double consTotal = 0.0;
                if (charts.consumptionKg != null) {
                    for (double c : charts.consumptionKg) {
                        consTotal += c;
                    }
                }
                int consN = charts.consumptionKg != null ? charts.consumptionKg.length : 0;
                binding.tvConsumptionAvg7d.setText(getString(R.string.hive_chart_consumption_avg,
                        gramNf.format(Math.round((consN > 0 ? consTotal / consN : 0.0) * 1000.0))));

                TextView[] barValueViews = new TextView[]{
                        binding.tvBarValue1, binding.tvBarValue2, binding.tvBarValue3, binding.tvBarValue4,
                        binding.tvBarValue5, binding.tvBarValue6, binding.tvBarValue7
                };
                for (int i = 0; i < n; i++) {
                    barValueViews[i].setText(gramNf.format(Math.round(values[i] * 1000.0)));
                }

                LocalDate chartEnd = charts.chartEndDayKey > 0
                        ? GameCalendar.fromDayKey(charts.chartEndDayKey)
                        : LocalDate.now(GameCalendar.userTimeZone());
                DateTimeFormatter dayFmt = DateTimeFormatter.ofPattern("dd/MM");
                TextView[] dayViews = new TextView[]{
                        binding.tvDay1, binding.tvDay2, binding.tvDay3, binding.tvDay4,
                        binding.tvDay5, binding.tvDay6, binding.tvDay7
                };
                TextView[] skyViews = new TextView[]{
                        binding.tvSky1, binding.tvSky2, binding.tvSky3, binding.tvSky4,
                        binding.tvSky5, binding.tvSky6, binding.tvSky7
                };
                int elevForUi = hive.elevationMeters >= 0
                        ? hive.elevationMeters
                        : OpenMeteoElevation.FALLBACK_METERS;
                for (int i = 0; i < n; i++) {
                    LocalDate d = chartEnd.minusDays(6 - i);
                    dayViews[i].setText(d.format(dayFmt));
                    int dayKey = GameCalendar.toDayKey(d);
                    skyViews[i].setText(DailySkyCondition.forHexElevationAndDay(skyKey, dayKey, elevForUi)
                            .emoji());
                }
                final LocalDate chartFrom = chartEnd.minusDays(6);
                final LocalDate chartTo = chartEnd;
                final int nDays = n;
                final String skyKeyCharts = skyKey;
                final int elevCharts = elevForUi;
                final double latCharts = hive.lat;
                final double lngCharts = hive.lng;
                final int skyFetchId = weatherFetchSeq;
                WeatherRepository weatherForCharts = app.getWeatherRepository();
                new Thread(() -> {
                    Map<Integer, DailyWeather> series = weatherForCharts.fetchDailyWeatherRangeBlocking(
                            latCharts, lngCharts, chartFrom, chartTo);
                    requireActivity().runOnUiThread(() -> {
                        if (!isAdded() || binding == null || skyFetchId != weatherFetchSeq) {
                            return;
                        }
                        TextView[] skies = new TextView[]{
                                binding.tvSky1, binding.tvSky2, binding.tvSky3, binding.tvSky4,
                                binding.tvSky5, binding.tvSky6, binding.tvSky7
                        };
                        for (int i = 0; i < nDays && i < skies.length; i++) {
                            LocalDate d = chartTo.minusDays(6 - i);
                            int dayKey = GameCalendar.toDayKey(d);
                            DailyWeather w = series.get(dayKey);
                            skies[i].setText(DailySkyCondition.forHiveDay(
                                    skyKeyCharts, dayKey, elevCharts, w).emoji());
                        }
                    });
                }).start();

                View[] bars = new View[]{
                        binding.bar1, binding.bar2, binding.bar3, binding.bar4,
                        binding.bar5, binding.bar6, binding.bar7
                };
                double scaleMax = 0.05;
                for (double v : values) {
                    if (v > scaleMax) {
                        scaleMax = v;
                    }
                }
                float density = getResources().getDisplayMetrics().density;
                for (int i = 0; i < n && i < bars.length; i++) {
                    double ratio = values[i] > 0 ? Math.min(1.0, values[i] / scaleMax) : 0.0;
                    int heightDp = 24 + (int) (56 * ratio);
                    ViewGroup.LayoutParams lp = bars[i].getLayoutParams();
                    lp.height = (int) (heightDp * density);
                    bars[i].setLayoutParams(lp);
                }
                fillChartDayRow(binding.layoutWorkerNetDays, chartEnd);
                fillChartDayRow(binding.layoutEggsDays, chartEnd);
                fillChartDayRow(binding.layoutConsumptionDays, chartEnd);
                bindWorkerNetChart(charts.workerNetDelta);
                bindEggsChart(charts.eggsLaid);
                bindConsumptionChart(charts.consumptionKg);
            });
            }

            Runnable doFeed = () -> {
                // Capítulo 2. Alimentar.
                TutorialBus.emit(
                        TutorialEvent.CARE_FEED);
                showFeedHiveDialog(hive);
            };
            Runnable doTreat = () -> {
                // Capítulo 2. Tratar varroa.
                TutorialBus.emit(
                        TutorialEvent.CARE_TREAT);
                showTreatHiveDialog(hive);
            };
            Runnable doSplit = () -> BuyHiveDialogs.showSplit(this, hiveViewModel, hive);
            Runnable doReplaceQueen = () -> {
                // Capítulo 2. Cambiar reina.
                TutorialBus.emit(
                        TutorialEvent.CARE_QUEEN);
                showReplaceQueenDialog(hive);
            };
            Runnable doHarvest = () -> requireWarehouseThen(hive, () -> showHarvestHoneyDialog(hive));

            binding.btnFeed.setOnClickListener(v -> doFeed.run());
            binding.btnFeedTop.setOnClickListener(v -> doFeed.run());
            binding.btnTreat.setOnClickListener(v -> doTreat.run());
            binding.btnTreatTop.setOnClickListener(v -> doTreat.run());

            boolean canSplit = pop.workersAdult >= ColonyGameRules.MIN_BEES_TO_SPLIT;
            binding.btnSplit.setEnabled(canSplit);
            binding.btnSplitTop.setEnabled(canSplit);
            binding.btnSplit.setOnClickListener(v -> doSplit.run());
            binding.btnSplitTop.setOnClickListener(v -> doSplit.run());

            binding.btnReplaceQueen.setOnClickListener(v -> doReplaceQueen.run());
            binding.btnReplaceQueenTop.setOnClickListener(v -> doReplaceQueen.run());
            bindQueenActionButtons(pop.needsQueenIntroduction());
            binding.btnHarvest.setOnClickListener(v -> doHarvest.run());
            binding.btnHarvestTop.setOnClickListener(v -> doHarvest.run());

            int purchasePrice = HiveViewModel.hivePurchasePriceEuros(hive.superCount);
            double sellPrice = 0.5 * purchasePrice;
            binding.btnSellHive.setText(getString(R.string.hive_sell_button, sellPrice));
            binding.btnSellHive.setOnClickListener(v -> {
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle(R.string.hive_sell_title)
                        .setMessage(getString(R.string.hive_sell_confirm_message, sellPrice))
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(R.string.hive_sell_confirm_positive, (dialog, which) -> {
                            hiveViewModel.sellHive(hive, msg -> {
                                if (!isAdded()) {
                                    return;
                                }
                                if (msg == null) {
                                    GameNotice.showSuccess(requireContext(), getString(R.string.hive_sold_ok, sellPrice));
                                    Navigation.findNavController(requireView()).popBackStack();
                                } else {
                                    GameNotice.show(requireContext(), msg);
                                }
                            });
                        })
                        .show();
            });
        });
    }

    private void showFeedHiveDialog(HiveEntity hive) {
        if (!isAdded()) {
            return;
        }
        HiveCareDialogs.showFeed(this, hive, hiveViewModel, this::goShop);
    }

    private void showTreatHiveDialog(HiveEntity hive) {
        if (!isAdded()) {
            return;
        }
        HiveCareDialogs.showTreat(this, hive, hiveViewModel, this::goShop);
    }

    private void bindCareStatus(HiveEntity hive, int feedLeft) {
        boolean treat = hive.varroaTreatmentDaysRemaining > 0;
        boolean rebound = !treat && hive.varroaReboundDaysRemaining > 0;
        boolean feed = feedLeft > 0;
        binding.llHiveCareStatus.setVisibility(treat || rebound || feed ? View.VISIBLE : View.GONE);
        binding.ivCareTreat.setVisibility(treat || rebound ? View.VISIBLE : View.GONE);
        binding.tvCareTreat.setVisibility(treat || rebound ? View.VISIBLE : View.GONE);
        if (treat) {
            binding.ivCareTreat.setImageBitmap(
                    IconBitmaps.decode(getResources(), R.drawable.ic_tratamiento, 128));
            binding.tvCareTreat.setText(getString(R.string.hive_care_treat_chip,
                    hive.varroaTreatmentDaysRemaining));
        } else if (rebound) {
            binding.ivCareTreat.setImageBitmap(
                    IconBitmaps.decode(getResources(), R.drawable.ic_tratamiento, 128));
            binding.tvCareTreat.setText(getString(R.string.hive_care_rebound_chip,
                    hive.varroaReboundDaysRemaining));
        }
        binding.ivCareFeed.setVisibility(feed ? View.VISIBLE : View.GONE);
        binding.tvCareFeed.setVisibility(feed ? View.VISIBLE : View.GONE);
        if (feed) {
            binding.ivCareFeed.setImageBitmap(
                    IconBitmaps.decode(getResources(), R.drawable.ic_apialimento, 128));
            binding.tvCareFeed.setText(getString(R.string.hive_care_feed_chip, feedLeft));
        }
    }

    private void bindHiveAlertChips(@NonNull HiveEntity hive, @Nullable HivePopulationState pop) {
        ChipGroup group = binding.chipGroupHiveAlerts;
        group.removeAllViews();
        List<HiveHealthAlerts.Tag> tags = HiveHealthAlerts.alertTags(requireContext(), hive, pop, forage01(hive));
        if (tags.isEmpty()) {
            group.setVisibility(View.GONE);
            return;
        }
        group.setVisibility(View.VISIBLE);
        Context ctx = requireContext();
        int padH = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8f,
                ctx.getResources().getDisplayMetrics());
        int padV = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4f,
                ctx.getResources().getDisplayMetrics());
        for (HiveHealthAlerts.Tag tag : tags) {
            Chip chip = new Chip(ctx);
            chip.setText(tag.text);
            chip.setClickable(false);
            chip.setCheckable(false);
            chip.setEnsureMinTouchTargetSize(false);
            chip.setChipMinHeight(padV * 6);
            chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
            chip.setTextColor(Color.WHITE);
            chip.setChipBackgroundColor(ColorStateList.valueOf(
                    ContextCompat.getColor(ctx, tag.backgroundRes == R.drawable.bg_swarm_warn_pill
                            ? R.color.dash_bad
                            : R.color.dash_warning)));
            chip.setPadding(padH, padV, padH, padV);
            group.addView(chip);
        }
    }

    private void bindHeaderQueen(HiveEntity hive, HivePopulationState pop) {
        boolean missing = pop != null && pop.needsQueenIntroduction();
        if (missing) {
            binding.tvHiveHeaderQueen.setText(R.string.hive_detail_queen_missing);
            binding.tvHiveHeaderQueen.setTextColor(ContextCompat.getColor(requireContext(), R.color.dash_bad));
        } else {
            binding.tvHiveHeaderQueen.setText(getString(R.string.hive_detail_queen_quality_short,
                    hive.queenGeneticQuality));
            binding.tvHiveHeaderQueen.setTextColor(ContextCompat.getColor(requireContext(), R.color.dash_text_card));
        }
        binding.ivHiveHeaderQueen.setImageResource(R.drawable.ic_queen);
    }

    private void bindForageBloom(HiveEntity hive) {
        double n = forage01(hive);
        int pct = (int) Math.round(Math.max(0.0, Math.min(1.0, n)) * 100.0);
        if (pct < 20) {
            binding.tvForageBloom.setText(getString(R.string.hive_forage_bloom_low, pct));
            binding.tvForageBloom.setTextColor(ContextCompat.getColor(requireContext(), R.color.dash_warning));
        } else {
            binding.tvForageBloom.setText(getString(R.string.hive_forage_bloom_pct, pct));
            binding.tvForageBloom.setTextColor(ContextCompat.getColor(requireContext(), R.color.dash_muted));
        }
    }

    private static double forage01(HiveEntity hive) {
        LocalDate day = LocalDate.now(GameCalendar.userTimeZone());
        if (hive != null && hive.lastSummaryDayKey > 0) {
            day = GameCalendar.fromDayKey(hive.lastSummaryDayKey);
        }
        return HexNectarRules.nectar01(hive, day, 1, java.util.Collections.emptyList());
    }

    private void bindHoneyPantry(HiveEntity hive) {
        LinearLayout row = binding.llHoneyPantry;
        row.removeAllViews();
        HiveHoneyStocks.ensureSeeded(hive);
        LinkedHashMap<String, Double> stocks = HiveHoneyStocks.parse(hive.honeyStocksJson);
        LayoutInflater inflater = getLayoutInflater();
        NumberFormat nf = NumberFormat.getNumberInstance(new Locale("es", "ES"));
        nf.setMinimumFractionDigits(1);
        nf.setMaximumFractionDigits(2);
        if (stocks.isEmpty()) {
            TextView empty = new TextView(requireContext());
            empty.setText("—");
            empty.setTextColor(ContextCompat.getColor(requireContext(), R.color.dash_muted));
            empty.setTextSize(12);
            row.addView(empty);
            return;
        }
        for (Map.Entry<String, Double> e : stocks.entrySet()) {
            View item = inflater.inflate(R.layout.item_hive_honey_type, row, false);
            ImageView jar = item.findViewById(R.id.iv_honey_type_jar);
            TextView name = item.findViewById(R.id.tv_honey_type_name);
            TextView kg = item.findViewById(R.id.tv_honey_type_kg);
            jar.setImageResource(HiveSiteSummaryUi.floraHoneyJarIcon(e.getKey()));
            name.setText(e.getKey());
            kg.setText(nf.format(e.getValue()) + " kg");
            String flora = e.getKey();
            item.setOnClickListener(v -> requireWarehouseThen(hive, () -> showHarvestHoneyDialog(hive, flora)));
            row.addView(item);
        }
    }

    private void showChangeForageDialog(HiveEntity hive) {
        if (!isAdded() || hive == null || hive.hexId == null) {
            return;
        }
        hiveViewModel.listReadyFlorasForHex(hive.hexId, hive.siteId, floras -> {
            if (!isAdded() || floras == null || floras.isEmpty()) {
                GameNotice.show(requireContext(), R.string.hive_buy_no_flora_ready);
                return;
            }
            String[] labels = floras.toArray(new String[0]);
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.hive_change_forage_title)
                    .setItems(labels, (d, which) -> {
                        String pick = floras.get(which);
                        hiveViewModel.changeHiveForageFlora(hive.id, pick, msg -> {
                            if (!isAdded()) {
                                return;
                            }
                            if (msg == null) {
                                hive.floraType = pick;
                                GameNotice.showSuccess(requireContext(),
                                        getString(R.string.hive_change_forage_ok, pick));
                            } else {
                                GameNotice.show(requireContext(), msg);
                            }
                        });
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
        });
    }

    private void requireWarehouseThen(@Nullable HiveEntity hive, @NonNull Runnable next) {
        if (!isAdded() || hive == null) {
            return;
        }
        HoneyLogistics.warehouseParcelNearAsync(requireContext(), hive.ownerId, hive, warehouse -> {
            if (!isAdded()) {
                return;
            }
            if (warehouse == null) {
                WarehouseDialogs.showNeedWarehouse(this);
                return;
            }
            next.run();
        });
    }

    private void showHarvestHoneyDialog(HiveEntity hive) {
        if (!isAdded() || hive == null) {
            return;
        }
        HiveHoneyStocks.ensureSeeded(hive);
        LinkedHashMap<String, Double> stocks = HiveHoneyStocks.parse(hive.honeyStocksJson);
        if (stocks.isEmpty() || hive.honeyProduction <= 1e-9) {
            GameNotice.show(requireContext(), R.string.hive_harvest_need_stock);
            return;
        }
        if (stocks.size() == 1) {
            showHarvestHoneyDialog(hive, stocks.keySet().iterator().next());
            return;
        }
        ArrayList<String> keys = new ArrayList<>(stocks.keySet());
        String[] rows = new String[keys.size()];
        for (int i = 0; i < keys.size(); i++) {
            String k = keys.get(i);
            rows[i] = getString(R.string.hive_harvest_type_row, k, stocks.get(k));
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.hive_harvest_pick_type_title)
                .setItems(rows, (d, which) -> showHarvestHoneyDialog(hive, keys.get(which)))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showHarvestHoneyDialog(HiveEntity hive, String floraKey) {
        if (!isAdded() || hive == null) {
            return;
        }
        HiveHoneyStocks.ensureSeeded(hive);
        String flora = HoneyMarketEngine.canonicalFloraKey(floraKey);
        LinkedHashMap<String, Double> stocks = HiveHoneyStocks.parse(hive.honeyStocksJson);
        double stock = stocks.getOrDefault(flora, 0.0);
        if (stock <= 1e-9) {
            GameNotice.show(requireContext(), R.string.hive_harvest_need_stock);
            return;
        }
        EditText input = new EditText(requireContext());
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setHint(R.string.hive_harvest_amount_hint);
        NumberFormat nf = NumberFormat.getNumberInstance(new Locale("es", "ES"));
        nf.setMaximumFractionDigits(2);
        nf.setMinimumFractionDigits(stock > 0 && stock < 1 ? 2 : 1);
        input.setText(nf.format(stock));
        input.setSelectAllOnFocus(true);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        LinearLayout wrap = new LinearLayout(requireContext());
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(pad, pad / 2, pad, 0);
        TextView msg = new TextView(requireContext());
        msg.setText(getString(R.string.hive_harvest_choose_message_type, flora, stock));
        wrap.addView(msg);
        wrap.addView(input);
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.hive_harvest_choose_title)
                .setView(wrap)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.hive_action_harvest, (d, w) -> {
                    Double kg = parseHarvestKg(input.getText() != null ? input.getText().toString() : "");
                    if (kg == null || kg <= 1e-9) {
                        GameNotice.show(requireContext(), R.string.hive_harvest_invalid_amount);
                        return;
                    }
                    if (kg > stock + 1e-6) {
                        GameNotice.show(requireContext(), getString(R.string.hive_harvest_too_much, stock));
                        return;
                    }
                    if (com.apiculture.simulator.presentation.workshop.WorkshopUi.blocksHarvest(requireContext(), hive.ownerId)) {
                        com.apiculture.simulator.presentation.workshop.WorkshopUi.showNeedWorkshop(this);
                        return;
                    }
                    java.util.Map<String, Double> cargo = new java.util.LinkedHashMap<>();
                    cargo.put(flora, kg);
                    HoneyLogistics.planCollectOptions(requireContext(), hive.ownerId, hive, cargo, options -> {
                        if (!isAdded()) {
                            return;
                        }
                        if (options == null || options.isEmpty()) {
                            WarehouseDialogs.showNeedWarehouse(this);
                            return;
                        }
                        if (options.get(0).warehouseFull) {
                            GameNotice.show(requireContext(), R.string.harvest_warehouse_full);
                            return;
                        }
                        if (options.get(0).noFleet) {
                            GameNotice.show(requireContext(), R.string.harvest_no_truck);
                            return;
                        }
                        HarvestCollectDialogs.showTrips(this, options, 0, 0, chosen -> {
                            HoneyLogistics.CollectPreview pick = chosen.get(0);
                            java.util.Map<String, Double> send = new java.util.LinkedHashMap<>(pick.cargo);
                            dispatchHiveHarvest(hive, flora, pick.kg, send, pick.warehouse, pick.truckId);
                        }, true);
                    });
                })
                .show();
    }

    private void dispatchHiveHarvest(@NonNull HiveEntity hive, @NonNull String flora, double kg,
            @NonNull java.util.Map<String, Double> cargo,
            @Nullable com.apiculture.simulator.domain.parcel.HexParcel warehouse,
            @Nullable String truckId) {
        double harvested = hiveViewModel.harvestHoney(hive, flora, kg);
        if (harvested <= 0.0) {
            GameNotice.show(requireContext(), R.string.hive_harvest_need_stock);
            return;
        }
        cargo.put(flora, harvested);
        ApicultureApp app = (ApicultureApp) requireActivity().getApplication();
        HoneyLogistics.collectFromHive(
                requireContext(), hive.ownerId, hive, cargo, app.getEconomyRepository(), warehouse, truckId, trip -> {
                    if (!isAdded()) {
                        return;
                    }
                    if (trip == HoneyLogistics.Result.STARTED) {
                        GameNotice.showSuccess(requireContext(),
                                getString(R.string.hive_harvest_ok_truck, harvested));
                    } else if (trip == HoneyLogistics.Result.NO_FLEET) {
                        GameNotice.show(requireContext(), R.string.harvest_no_truck);
                    } else {
                        GameNotice.showSuccess(requireContext(),
                                getString(R.string.hive_harvest_ok_kg, harvested));
                    }
                });
    }

    @Nullable
    private static Double parseHarvestKg(String raw) {
        if (raw == null) {
            return null;
        }
        String t = raw.trim().replace(" ", "").replace(',', '.');
        if (t.isEmpty()) {
            return null;
        }
        try {
            return Double.parseDouble(t);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void bindQueenActionButtons(boolean needsQueen) {
        int label = needsQueen ? R.string.hive_action_introduce_queen : R.string.hive_action_replace_queen;
        int bg = needsQueen ? R.color.dash_bad : R.color.dash_soft_blue;
        int fg = needsQueen ? R.color.white : R.color.dash_text_card;
        ColorStateList bgList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), bg));
        ColorStateList fgList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), fg));
        binding.btnReplaceQueen.setText(label);
        binding.btnReplaceQueenTop.setText(label);
        binding.btnReplaceQueen.setBackgroundTintList(bgList);
        binding.btnReplaceQueenTop.setBackgroundTintList(bgList);
        binding.btnReplaceQueen.setTextColor(fgList);
        binding.btnReplaceQueenTop.setTextColor(fgList);
        binding.btnReplaceQueen.setIconTint(fgList);
        // Icono a color en el tile superior; solo tinte blanco si hay urgencia.
        binding.btnReplaceQueenTop.setIconTint(needsQueen ? fgList : null);
    }

    private void showReplaceQueenDialog(HiveEntity hive) {
        if (!isAdded()) {
            return;
        }
        HivePopulationState pop = HivePopulationState.fromHiveEntityOrDefault(
                hive, HiveRepository.DEFAULT_BEE_COUNT_PER_HIVE);
        boolean needsIntroduce = pop.needsQueenIntroduction();
        java.util.List<Integer> queens = EventInventoryStore.queens(requireContext());
        int title = needsIntroduce
                ? R.string.hive_introduce_queen_title
                : R.string.hive_replace_queen_title;
        if (queens.isEmpty()) {
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle(title)
                    .setMessage(R.string.hive_need_shop_queen)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(R.string.inventory_go_shop, (d, w) -> goShop())
                    .show();
            return;
        }
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        LinearLayout wrap = new LinearLayout(requireContext());
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(pad, pad / 2, pad, 0);
        TextView msg = new TextView(requireContext());
        msg.setText(R.string.hive_replace_queen_message_inv);
        wrap.addView(msg);
        final int[] selectedIndex = {0};
        if (queens.size() == 1) {
            TextView one = new TextView(requireContext());
            one.setPadding(0, pad / 2, 0, 0);
            one.setText(getString(R.string.inventory_queen_row, 1, queens.get(0)));
            wrap.addView(one);
        } else {
            Spinner spinner = new Spinner(requireContext());
            String[] items = new String[queens.size()];
            for (int i = 0; i < queens.size(); i++) {
                items[i] = getString(R.string.inventory_queen_row, i + 1, queens.get(i));
            }
            ArrayAdapter<String> adapter = new ArrayAdapter<>(
                    requireContext(), android.R.layout.simple_spinner_dropdown_item, items);
            spinner.setAdapter(adapter);
            spinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                    selectedIndex[0] = position;
                }

                @Override
                public void onNothingSelected(android.widget.AdapterView<?> parent) {
                }
            });
            wrap.addView(spinner);
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(title)
                .setView(wrap)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.hive_replace_queen_confirm_inv, (d, w) ->
                        hiveViewModel.replaceQueenFromInventory(hive, selectedIndex[0],
                                msgResult -> handleCareResult(msgResult, R.string.hive_replace_queen_ok)))
                .show();
    }

    private void handleCareResult(String msg, int okRes) {
        if (!isAdded()) {
            return;
        }
        if (msg == null) {
            GameNotice.showSuccess(requireContext(), okRes);
            return;
        }
        if ("SHOP_FEED".equals(msg)) {
            GameNotice.show(requireContext(), R.string.hive_need_shop_feed);
            goShop();
            return;
        }
        if ("SHOP_TREAT".equals(msg)) {
            GameNotice.show(requireContext(), R.string.hive_need_shop_treat);
            goShop();
            return;
        }
        if ("SHOP_QUEEN".equals(msg)) {
            GameNotice.show(requireContext(), R.string.hive_need_shop_queen);
            goShop();
            return;
        }
        GameNotice.show(requireContext(), msg);
    }

    private void goShop() {
        if (!isAdded()) {
            return;
        }
        Navigation.findNavController(requireView()).navigate(R.id.shopFragment);
    }

    @DrawableRes
    private static int weatherIconRes(DailySkyCondition sky) {
        if (sky == null) {
            return R.drawable.ic_weather_cloud;
        }
        switch (sky) {
            case SUN:
                return R.drawable.ic_sol_prado;
            case VARIABLE:
                return R.drawable.ic_weather_variable;
            case CLOUDY:
                return R.drawable.ic_weather_cloud;
            case WINDY:
                return R.drawable.ic_weather_wind;
            case RAINY:
            default:
                return R.drawable.ic_weather_rain;
        }
    }

    private void setFloraHeroJar(@DrawableRes int resId) {
        android.widget.ImageView iv = binding.ivHoneyFloraHero;
        iv.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
        iv.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        iv.setImageResource(resId);
    }

    /**
     * Carga la miniatura con {@link BitmapFactory} y submuestreo para PNG/JPG grandes; evita OOM y acota decodificación.
     */
    private void setFloraHeroScaled(@DrawableRes int resId) {
        android.widget.ImageView iv = binding.ivHoneyFloraHero;
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeResource(getResources(), resId, bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                iv.setImageResource(resId);
                return;
            }
            int maxSidePx = (int) (360 * getResources().getDisplayMetrics().density);
            int inSample = 1;
            int maxDim = Math.max(bounds.outWidth, bounds.outHeight);
            while (maxDim / (inSample * 2) > maxSidePx) {
                inSample *= 2;
            }
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = inSample;
            opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
            Bitmap bmp = BitmapFactory.decodeResource(getResources(), resId, opts);
            if (bmp != null) {
                iv.setImageBitmap(bmp);
                return;
            }
        } catch (OutOfMemoryError ignored) {
        }
        iv.setImageResource(resId);
    }

    private void fillChartDayRow(LinearLayout row, LocalDate today) {
        row.removeAllViews();
        Context ctx = requireContext();
        DateTimeFormatter dayFmt = DateTimeFormatter.ofPattern("dd/MM");
        for (int i = 0; i < 7; i++) {
            LocalDate d = today.minusDays(6 - i);
            TextView tv = new TextView(ctx);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            tv.setLayoutParams(lp);
            tv.setGravity(Gravity.CENTER);
            tv.setText(d.format(dayFmt));
            tv.setTextColor(ContextCompat.getColor(ctx, R.color.dash_muted));
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
            row.addView(tv);
        }
    }

    private void bindWorkerNetChart(int[] nets) {
        if (nets == null || nets.length < 7) {
            binding.layoutWorkerNetBars.removeAllViews();
            return;
        }
        Context ctx = requireContext();
        LinearLayout host = binding.layoutWorkerNetBars;
        host.removeAllViews();
        float density = ctx.getResources().getDisplayMetrics().density;
        int chartPx = (int) (100 * density);
        int halfPx = chartPx / 2;
        int maxAbs = 1;
        for (int n : nets) {
            maxAbs = Math.max(maxAbs, Math.abs(n));
        }
        NumberFormat nf = NumberFormat.getNumberInstance(new Locale("es", "ES"));
        nf.setMaximumFractionDigits(0);
        for (int i = 0; i < 7; i++) {
            int net = nets[i];
            LinearLayout col = new LinearLayout(ctx);
            col.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(Gravity.CENTER_HORIZONTAL);
            TextView tvVal = new TextView(ctx);
            tvVal.setText(nf.format(net));
            tvVal.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9);
            tvVal.setGravity(Gravity.CENTER);
            if (net > 0) {
                tvVal.setTextColor(ContextCompat.getColor(ctx, R.color.dash_good));
            } else if (net < 0) {
                tvVal.setTextColor(ContextCompat.getColor(ctx, R.color.dash_bad));
            } else {
                tvVal.setTextColor(ContextCompat.getColor(ctx, R.color.dash_text_card));
            }
            col.addView(tvVal);
            FrameLayout chartArea = new FrameLayout(ctx);
            chartArea.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, chartPx));
            LinearLayout split = new LinearLayout(ctx);
            split.setLayoutParams(new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            split.setOrientation(LinearLayout.VERTICAL);
            FrameLayout upper = new FrameLayout(ctx);
            upper.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
            FrameLayout lower = new FrameLayout(ctx);
            lower.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
            View barUp = new View(ctx);
            View barDown = new View(ctx);
            barUp.setBackground(ContextCompat.getDrawable(ctx, R.drawable.bg_chart_bar_green));
            barDown.setBackground(ContextCompat.getDrawable(ctx, R.drawable.bg_chart_bar_red));
            double ratio = maxAbs > 0 ? Math.abs(net) / (double) maxAbs : 0;
            int barH = (int) (halfPx * ratio);
            if (net > 0 && barH > 0) {
                FrameLayout.LayoutParams lpUp = new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, barH);
                lpUp.gravity = Gravity.BOTTOM;
                upper.addView(barUp, lpUp);
            } else if (net < 0 && barH > 0) {
                FrameLayout.LayoutParams lpDown = new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, barH);
                lpDown.gravity = Gravity.TOP;
                lower.addView(barDown, lpDown);
            }
            split.addView(upper);
            split.addView(lower);
            chartArea.addView(split);
            col.addView(chartArea);
            host.addView(col);
        }
    }

    private void bindEggsChart(int[] eggs) {
        if (eggs == null || eggs.length < 7) {
            binding.layoutEggsBars.removeAllViews();
            return;
        }
        Context ctx = requireContext();
        LinearLayout host = binding.layoutEggsBars;
        host.removeAllViews();
        float density = ctx.getResources().getDisplayMetrics().density;
        int maxEggs = 1;
        for (int e : eggs) {
            if (e > maxEggs) {
                maxEggs = e;
            }
        }
        NumberFormat nf = NumberFormat.getNumberInstance(new Locale("es", "ES"));
        nf.setMaximumFractionDigits(0);
        for (int i = 0; i < 6; i++) {
            int v = eggs[i];
            LinearLayout col = new LinearLayout(ctx);
            col.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            TextView tvVal = new TextView(ctx);
            tvVal.setText(nf.format(v));
            tvVal.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9);
            tvVal.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM);
            tvVal.setTextColor(ContextCompat.getColor(ctx, R.color.dash_text_card));
            col.addView(tvVal);
            FrameLayout barWrap = new FrameLayout(ctx);
            LinearLayout.LayoutParams wrapLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
            barWrap.setLayoutParams(wrapLp);
            barWrap.setMinimumHeight((int) (20 * density));
            View bar = new View(ctx);
            bar.setBackground(ContextCompat.getDrawable(ctx, R.drawable.bg_chart_bar_eggs));
            double ratio = maxEggs > 0 ? v / (double) maxEggs : 0;
            int heightDp = 24 + (int) (56 * ratio);
            FrameLayout.LayoutParams barLp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, (int) (heightDp * density));
            barLp.gravity = Gravity.BOTTOM;
            barWrap.addView(bar, barLp);
            col.addView(barWrap);
            host.addView(col);
        }
    }

    private void bindConsumptionChart(double[] kg) {
        if (binding.layoutConsumptionBars == null) {
            return;
        }
        if (kg == null || kg.length < 7) {
            binding.layoutConsumptionBars.removeAllViews();
            return;
        }
        Context ctx = requireContext();
        LinearLayout host = binding.layoutConsumptionBars;
        host.removeAllViews();
        float density = ctx.getResources().getDisplayMetrics().density;
        double maxKg = 0.05;
        for (double v : kg) {
            if (v > maxKg) {
                maxKg = v;
            }
        }
        NumberFormat nf = NumberFormat.getIntegerInstance(new Locale("es", "ES"));
        for (int i = 0; i < 7; i++) {
            double v = kg[i];
            LinearLayout col = new LinearLayout(ctx);
            col.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            TextView tvVal = new TextView(ctx);
            tvVal.setText(nf.format(Math.round(v * 1000.0)));
            tvVal.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9);
            tvVal.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM);
            tvVal.setTextColor(ContextCompat.getColor(ctx, R.color.dash_text_card));
            col.addView(tvVal);
            FrameLayout barWrap = new FrameLayout(ctx);
            LinearLayout.LayoutParams wrapLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
            barWrap.setLayoutParams(wrapLp);
            barWrap.setMinimumHeight((int) (20 * density));
            View bar = new View(ctx);
            bar.setBackground(ContextCompat.getDrawable(ctx, R.drawable.bg_chart_bar_red));
            double ratio = maxKg > 0 ? v / maxKg : 0;
            int heightDp = 24 + (int) (56 * ratio);
            FrameLayout.LayoutParams barLp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, (int) (heightDp * density));
            barLp.gravity = Gravity.BOTTOM;
            barWrap.addView(bar, barLp);
            col.addView(barWrap);
            host.addView(col);
        }
    }

    private void bindFloraMonthChart(HiveEntity hive, LocalDate yearDay) {
        if (binding.floraMonthChart == null || hive == null || yearDay == null) {
            return;
        }
        LocalDate start = yearDay.withDayOfYear(1);
        int days = start.lengthOfYear();
        double[] nectars = new double[days];
        double sum = 0.0;
        double peak = 0.0;
        for (int i = 0; i < days; i++) {
            double n = Math.max(0.0, HexNectarRules.nectar01(hive, start.plusDays(i), 1, null));
            nectars[i] = Math.min(1.0, n);
            sum += nectars[i];
            if (nectars[i] > peak) {
                peak = nectars[i];
            }
        }
        binding.tvFloraMonthTitle.setText(getString(R.string.hive_chart_flora_year_title, start.getYear()));
        String floraLabel = hive.floraType != null && !hive.floraType.trim().isEmpty()
                ? hive.floraType.trim()
                : getString(R.string.hive_flora_unknown_label);
        Locale es = new Locale("es", "ES");
        NumberFormat pctNf = NumberFormat.getPercentInstance(es);
        pctNf.setMaximumFractionDigits(0);
        binding.tvFloraMonthAvg.setText(getString(R.string.hive_chart_flora_month_avg,
                floraLabel, pctNf.format(days > 0 ? sum / days : 0.0), pctNf.format(peak)));
        binding.floraMonthChart.setSeries(nectars, yearDay.getDayOfYear() - 1);
    }

    private void showRenameHiveDialog(HiveEntity hive) {
        if (hive == null || hive.id == null) {
            return;
        }
        EditText input = new EditText(requireContext());
        input.setText(hive.name != null ? hive.name : "");
        input.setHint(R.string.hive_rename_hint);
        input.setSingleLine(true);
        input.post(() -> input.selectAll());
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        FrameLayout wrap = new FrameLayout(requireContext());
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = pad;
        lp.rightMargin = pad;
        input.setLayoutParams(lp);
        wrap.addView(input);
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.hive_rename_title)
                .setView(wrap)
                .setPositiveButton(R.string.hive_rename_save, (d, w) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) {
                        GameNotice.show(requireContext(), R.string.hive_rename_empty);
                        return;
                    }
                    hiveViewModel.updateHiveName(hive.id, name);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showBuySuperDialog(HiveEntity hive) {
        if (hive == null || hive.id == null) {
            return;
        }
        int current = Math.max(0, Math.min(2, hive.superCount));
        if (current >= 2) {
            GameNotice.show(requireContext(), R.string.hive_super_already_max);
            return;
        }
        int room = 2 - current;
        int price = HiveViewModel.singleSuperPurchasePriceEuros();
        DialogSuperPurchaseBinding f = DialogSuperPurchaseBinding.inflate(getLayoutInflater());
        double capKg = HiveHoneyRules.maxHoneyKgForSuperCount(current);
        f.tvSuperDialogSummary.setText(getString(R.string.hive_super_purchase_summary,
                hive.name != null ? hive.name : "",
                current,
                capKg));
        f.rbSuperBuyOne.setText(getString(R.string.hive_super_purchase_one, (double) price));
        f.rbSuperBuyTwo.setText(getString(R.string.hive_super_purchase_two, (double) (price * 2)));
        if (room < 2) {
            f.rbSuperBuyTwo.setVisibility(View.GONE);
            f.rbSuperBuyOne.setChecked(true);
        } else {
            f.rbSuperBuyTwo.setVisibility(View.VISIBLE);
        }
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.hive_super_purchase_title)
                .setView(f.getRoot())
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.hive_super_purchase_confirm, null);
        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(di -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            int want = f.rbSuperBuyTwo.getVisibility() == View.VISIBLE && f.rbSuperBuyTwo.isChecked() ? 2 : 1;
            if (want > room) {
                want = room;
            }
            hiveViewModel.purchaseSupers(hive, want, msg -> {
                if (!isAdded()) {
                    return;
                }
                if (msg == null) {
                    dialog.dismiss();
                    GameNotice.show(requireContext(), R.string.hive_super_purchase_ok);
                } else {
                    GameNotice.show(requireContext(), msg);
                }
            });
        }));
        dialog.show();
    }

    private static int healthBarColor(android.content.Context ctx, int health) {
        int h = Math.max(0, Math.min(100, health));
        if (h >= 80) {
            return ContextCompat.getColor(ctx, R.color.dash_good);
        }
        if (h >= 60) {
            return ContextCompat.getColor(ctx, R.color.dash_warning);
        }
        if (h >= 40) {
            return Color.parseColor("#F57C00");
        }
        return ContextCompat.getColor(ctx, R.color.dash_bad);
    }

    private static int varroaBarColor(android.content.Context ctx, double varroaPct) {
        if (varroaPct < 3) {
            return ContextCompat.getColor(ctx, R.color.dash_good);
        }
        if (varroaPct < 8) {
            return ContextCompat.getColor(ctx, R.color.dash_warning);
        }
        return ContextCompat.getColor(ctx, R.color.dash_bad);
    }

    private void stepYardHive(int direction) {
        if (lastBoundHive == null) {
            return;
        }
        List<HiveEntity> sibs = siblingsInYard(yardHives, lastBoundHive);
        if (sibs.size() < 2) {
            return;
        }
        int idx = indexOfHive(sibs, lastBoundHive.id);
        if (idx < 0) {
            return;
        }
        int n = sibs.size();
        int next = (idx + direction % n + n) % n;
        hiveViewModel.setViewingHiveId(sibs.get(next).id);
    }

    private void bindYardNav(@Nullable HiveEntity hive) {
        if (binding == null) {
            return;
        }
        List<HiveEntity> sibs = siblingsInYard(yardHives, hive);
        boolean show = sibs.size() >= 2;
        binding.btnHivePrev.setVisibility(show ? View.VISIBLE : View.GONE);
        binding.btnHiveNext.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show && hive != null) {
            int idx = indexOfHive(sibs, hive.id);
            binding.tvHiveYardIndex.setVisibility(View.VISIBLE);
            binding.tvHiveYardIndex.setText(getString(R.string.hive_detail_yard_index,
                    Math.max(1, idx + 1), sibs.size()));
        } else {
            binding.tvHiveYardIndex.setVisibility(View.GONE);
        }
    }

    private void refreshTravelBanner() {
        bindPendingContractMove(lastBoundHive);
    }

    private void bindPendingContractMove(@Nullable HiveEntity hive) {
        if (binding == null) {
            return;
        }
        tripHandler.removeCallbacks(tripTick);
        if (hive != null && hive.id != null) {
            TruckTripEntity trip = TruckLiveTrips.get(requireContext(), hive.id);
            if (trip != null && !TruckTripRules.wallClockDone(trip, System.currentTimeMillis())) {
                String remain = TruckTripUi.remainingLabel(requireContext(), trip);
                boolean homebound = TruckTripRules.isHeadingHome(trip, hive);
                String line;
                if (homebound) {
                    line = getString(R.string.hive_trip_returning, remain);
                } else {
                    String dest = PendingContractMoveUi.destLabel(requireContext(), trip.destHexId);
                    line = dest == null || dest.isEmpty()
                            ? getString(R.string.hive_trip_detail, remain)
                            : getString(R.string.hive_trip_detail_to, dest, remain);
                }
                binding.llHivePendingContract.setVisibility(View.VISIBLE);
                binding.tvHivePendingContract.setText(line);
                boolean contractDest = !homebound && hive.pendingContractHexId != null
                        && hive.pendingContractHexId.equals(trip.destHexId);
                binding.btnHivePendingYard.setVisibility(contractDest ? View.VISIBLE : View.GONE);
                binding.btnHivePendingYard.setOnClickListener(contractDest
                        ? v -> openLiveTripYard(hive, trip.destHexId)
                        : null);
                binding.btnHiveTripCancel.setVisibility(homebound ? View.GONE : View.VISIBLE);
                binding.btnHiveTripCancel.setOnClickListener(homebound
                        ? null
                        : v -> TruckTripUi.confirmCancelLiveTrip(requireContext(), hive.id,
                                this::refreshTravelBanner));
                tripHandler.postDelayed(tripTick, 1_000L);
                return;
            }
        }
        String line = PendingContractMoveUi.hiveDetail(requireContext(), hive);
        if (line == null || line.isEmpty()) {
            binding.llHivePendingContract.setVisibility(View.GONE);
            binding.btnHivePendingYard.setOnClickListener(null);
            binding.btnHiveTripCancel.setVisibility(View.GONE);
            binding.btnHiveTripCancel.setOnClickListener(null);
            return;
        }
        binding.llHivePendingContract.setVisibility(View.VISIBLE);
        binding.tvHivePendingContract.setText(line);
        binding.btnHivePendingYard.setVisibility(View.VISIBLE);
        binding.btnHivePendingYard.setOnClickListener(v -> openPendingContractYard(hive));
        binding.btnHiveTripCancel.setVisibility(View.GONE);
        binding.btnHiveTripCancel.setOnClickListener(null);
    }

    private void openLiveTripYard(@Nullable HiveEntity hive, @Nullable String destHex) {
        if (!isAdded() || destHex == null || destHex.isEmpty()) {
            return;
        }
        HexParcel parcel = IberiaHexOverlayStore.findById(requireContext(), destHex);
        String npc = NpcContractCatalog.npcNameFor(parcel);
        String estate = PendingContractMoveUi.destLabel(requireContext(), destHex);
        Bundle args = new Bundle();
        args.putString("hexId", destHex);
        args.putString("parcelName", estate);
        args.putBoolean("contractYard", true);
        args.putString("npcName", npc);
        args.putInt("portraitIndex", NpcContractCatalog.portraitIndexFor(npc));
        Navigation.findNavController(requireView()).navigate(R.id.action_hive_detail_to_apiary_yard, args);
    }

    private void openPendingContractYard(@Nullable HiveEntity hive) {
        if (!isAdded() || hive == null || !TranshumanceRules.hasPendingContractMove(hive)) {
            return;
        }
        String destHex = hive.pendingContractHexId;
        HexParcel parcel = IberiaHexOverlayStore.findById(requireContext(), destHex);
        String npc = NpcContractCatalog.npcNameFor(parcel);
        String estate = PendingContractMoveUi.destLabel(requireContext(), destHex);
        Bundle args = new Bundle();
        args.putString("hexId", destHex);
        args.putString("parcelName", estate);
        args.putBoolean("contractYard", true);
        args.putString("npcName", npc);
        args.putInt("portraitIndex", NpcContractCatalog.portraitIndexFor(npc));
        Navigation.findNavController(requireView()).navigate(R.id.action_hive_detail_to_apiary_yard, args);
    }

    private static List<HiveEntity> siblingsInYard(List<HiveEntity> all, @Nullable HiveEntity current) {
        if (all == null || all.isEmpty() || current == null) {
            return Collections.emptyList();
        }
        String yard = yardKey(current.hexId);
        List<HiveEntity> out = new ArrayList<>();
        for (HiveEntity h : all) {
            if (h != null && yardKey(h.hexId).equals(yard)
                    && HexApiary.sameSite(current.siteId, h.siteId)) {
                out.add(h);
            }
        }
        Collections.sort(out, Comparator
                .comparing((HiveEntity h) -> h.name == null ? "" : h.name, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(h -> h.id));
        return out;
    }

    private static int indexOfHive(List<HiveEntity> list, String hiveId) {
        if (list == null || hiveId == null) {
            return -1;
        }
        for (int i = 0; i < list.size(); i++) {
            HiveEntity h = list.get(i);
            if (h != null && hiveId.equals(h.id)) {
                return i;
            }
        }
        return -1;
    }

    private static String yardKey(@Nullable String hexId) {
        return hexId == null ? "" : hexId;
    }
}
