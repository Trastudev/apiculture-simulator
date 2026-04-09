package com.apiculture.simulator.presentation.hive;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.apiculture.simulator.ApicultureApp;
import com.apiculture.simulator.R;
import com.apiculture.simulator.data.local.entity.HiveEntity;
import com.apiculture.simulator.data.remote.OpenMeteoElevation;
import com.apiculture.simulator.data.repository.HiveLast6DaysCharts;
import com.apiculture.simulator.databinding.DialogSuperPurchaseBinding;
import com.apiculture.simulator.databinding.FragmentHiveDetailBinding;
import com.apiculture.simulator.domain.game.ColonyGameRules;
import com.apiculture.simulator.domain.game.DailySkyCondition;
import com.apiculture.simulator.domain.game.GameCalendar;
import com.apiculture.simulator.domain.game.HiveHoneyRules;
import com.apiculture.simulator.domain.game.HoneyDailyProduction;
import com.apiculture.simulator.domain.health.HiveHealthAlerts;
import com.apiculture.simulator.domain.health.HiveHealthBand;
import com.apiculture.simulator.domain.population.HivePopulationState;
import com.apiculture.simulator.presentation.common.SimpleViewModelFactory;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class HiveDetailFragment extends Fragment {
    private FragmentHiveDetailBinding binding;
    private HiveViewModel hiveViewModel;
    private String hiveId;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentHiveDetailBinding.inflate(inflater, container, false);
        return binding.getRoot();
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
            Toast.makeText(requireContext(), "No se recibió colmena.", Toast.LENGTH_SHORT).show();
            return;
        }

        hiveViewModel.hiveById(hiveId).observe(getViewLifecycleOwner(), hive -> {
            if (hive == null) {
                Toast.makeText(requireContext(), "Colmena no encontrada.", Toast.LENGTH_SHORT).show();
                return;
            }

            HivePopulationState pop = HivePopulationState.fromHiveEntityOrDefault(hive,
                    com.apiculture.simulator.data.repository.HiveRepository.DEFAULT_BEE_COUNT_PER_HIVE);

            // Cabecera
            binding.tvHiveName.setText(hive.name);
            boolean honeyAtCap = HiveHoneyRules.isHoneyAtCapacity(hive);
            binding.ivHiveHoneyCapWarn.setVisibility(honeyAtCap ? View.VISIBLE : View.GONE);
            binding.tvHiveHoneyCapWarn.setVisibility(honeyAtCap ? View.VISIBLE : View.GONE);
            binding.tvHiveHeaderHoney.setText(String.format(Locale.getDefault(), "%.1f kg",
                    Math.max(0.0, hive.honeyProduction)));
            binding.tvHiveHeaderBees.setText(String.format(Locale.getDefault(), "%,d", pop.workersAdult));
            boolean swarmRisk = ColonyGameRules.swarmRiskForAdultWorkers(pop.workersAdult) > 0.0
                    || pop.workersAdult >= ColonyGameRules.SPLIT_RECOMMEND_BEES;
            binding.ivHiveSwarmDangerIcon.setVisibility(swarmRisk ? View.VISIBLE : View.GONE);
            binding.tvHiveSwarmWarn.setVisibility(swarmRisk ? View.VISIBLE : View.GONE);
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

            HiveHealthBand band = HiveHealthBand.fromHealth(hive.health);
            binding.tvHiveStatus.setText(band.labelEs());
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
            int superCount = Math.max(0, Math.min(2, hive.superCount));
            binding.tvHiveHeaderSupers.setText(String.format(Locale.getDefault(), "%d", superCount));
            binding.tvHiveName.setContentDescription(getString(R.string.hive_rename_title));
            binding.tvHiveName.setOnClickListener(v -> showRenameHiveDialog(hive));
            boolean canBuySuper = superCount < 2;
            binding.btnBuySuperTop.setEnabled(canBuySuper);
            binding.btnBuySuperTop.setAlpha(canBuySuper ? 1f : 0.45f);
            binding.btnBuySuperTop.setOnClickListener(v -> showBuySuperDialog(hive));

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
                binding.tvTreatStatus.setText("Sin tratamiento antivarroa en curso");
            }

            String alerts = HiveHealthAlerts.alertsSummaryLineEs(hive);
            if ("Sin alertas".equals(alerts)) {
                binding.tvHealthAlerts.setText("Sin alertas");
            } else {
                binding.tvHealthAlerts.setText("Alertas: " + alerts);
            }

            // Pastillas: miel / flora y elevación
            String floraLabel = hive.floraType != null
                    ? hive.floraType
                    : getString(R.string.hive_flora_unknown_label);
            setFloraHeroScaled(HiveSiteSummaryUi.floraIllustrationDrawable(hive.floraType));
            binding.tvHoneyFloraTitle.setText(getString(R.string.hive_detail_miel_de, floraLabel));
            if (hive.elevationMeters >= 0) {
                binding.tvElevationMeters.setText(String.format(Locale.getDefault(),
                        "%,d m s.n.m.", hive.elevationMeters));
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
            binding.tvHoneyStockInHive.setText("En colmena: " + nf.format(stockKg) + " kg");

            hiveViewModel.loadLast6DaysHiveCharts(hive.id, hive.ownerId, charts -> {
                if (!isAdded() || binding == null) {
                    return;
                }
                double[] values = charts.honeyKg;
                int n = values.length;
                double totalPeriod = 0.0;
                for (double v : values) {
                    totalPeriod += v;
                }
                double avgDaily = n > 0 ? totalPeriod / n : 0.0;
                binding.tvHoneyTotal7d.setText(getString(R.string.hive_chart_honey_period_total,
                        nf.format(totalPeriod)));
                binding.tvHoneyAvg7d.setText(getString(R.string.hive_chart_honey_avg_daily,
                        nf.format(avgDaily)));

                NumberFormat barValNf = NumberFormat.getNumberInstance(new Locale("es", "ES"));
                barValNf.setMinimumFractionDigits(0);
                barValNf.setMaximumFractionDigits(2);
                TextView[] barValueViews = new TextView[]{
                        binding.tvBarValue1, binding.tvBarValue2, binding.tvBarValue3, binding.tvBarValue4,
                        binding.tvBarValue5, binding.tvBarValue6, binding.tvBarValue7
                };
                for (int i = 0; i < n; i++) {
                    barValueViews[i].setText(barValNf.format(values[i]));
                }

                LocalDate today = LocalDate.now(GameCalendar.userTimeZone());
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
                String skyKey = "hive:" + hive.id;
                for (int i = 0; i < n; i++) {
                    LocalDate d = today.minusDays(6 - i);
                    dayViews[i].setText(d.format(dayFmt));
                    int dayKey = GameCalendar.toDayKey(d);
                    skyViews[i].setText(DailySkyCondition.forHexElevationAndDay(skyKey, dayKey, elevForUi)
                            .emoji());
                }

                View[] bars = new View[]{
                        binding.bar1, binding.bar2, binding.bar3, binding.bar4,
                        binding.bar5, binding.bar6, binding.bar7
                };
                double scaleMax = HoneyDailyProduction.maxChartDailyKgForBeeCount(
                        ColonyGameRules.MAX_ADULT_WORKERS_PER_HIVE);
                if (scaleMax <= 0) {
                    scaleMax = 1.0;
                }
                float density = getResources().getDisplayMetrics().density;
                for (int i = 0; i < n && i < bars.length; i++) {
                    double ratio = values[i] > 0 ? Math.min(1.0, values[i] / scaleMax) : 0.0;
                    int heightDp = 24 + (int) (56 * ratio);
                    ViewGroup.LayoutParams lp = bars[i].getLayoutParams();
                    lp.height = (int) (heightDp * density);
                    bars[i].setLayoutParams(lp);
                }
                fillChartDayRow(binding.layoutWorkerNetDays, today);
                fillChartDayRow(binding.layoutEggsDays, today);
                bindWorkerNetChart(charts.workerNetDelta);
                bindEggsChart(charts.eggsLaid);
            });

            Runnable doFeed = () -> hiveViewModel.feedHive(hive);
            Runnable doTreat = () -> hiveViewModel.treatDisease(hive);
            Runnable doSplit = () -> hiveViewModel.splitHive(hive, msg -> {
                if (!isAdded() || binding == null) {
                    return;
                }
                if (msg != null) {
                    Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(requireContext(), R.string.hive_split_ok, Toast.LENGTH_SHORT).show();
                }
            });
            Runnable doReplaceQueen = () -> hiveViewModel.replaceQueen(hive);
            Runnable doHarvest = () -> {
                double harvested = hiveViewModel.harvestHoney(hive);
                if (harvested <= 0.0) {
                    Toast.makeText(requireContext(), R.string.hive_harvest_need_stock, Toast.LENGTH_LONG).show();
                    return;
                }
                String flora = hive.floraType != null && !hive.floraType.isEmpty()
                        ? hive.floraType
                        : "Mil flores";
                app.getEconomyRepository().addHoney(flora, harvested);
                Toast.makeText(requireContext(),
                        getString(R.string.hive_harvest_ok_kg, harvested),
                        Toast.LENGTH_SHORT).show();
            };

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
            binding.btnHarvest.setOnClickListener(v -> doHarvest.run());
            binding.btnHarvestTop.setOnClickListener(v -> doHarvest.run());
        });
    }

    /**
     * Carga la miniatura con {@link BitmapFactory} y submuestreo para PNG/JPG grandes; evita OOM y acota decodificación.
     */
    private void setFloraHeroScaled(@DrawableRes int resId) {
        com.google.android.material.imageview.ShapeableImageView iv = binding.ivHoneyFloraHero;
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
            opts.inPreferredConfig = Bitmap.Config.RGB_565;
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
                        Toast.makeText(requireContext(), R.string.hive_rename_empty, Toast.LENGTH_SHORT).show();
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
            Toast.makeText(requireContext(), R.string.hive_super_already_max, Toast.LENGTH_SHORT).show();
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
        f.rbSuperBuyOne.setText(getString(R.string.hive_super_purchase_one, price));
        f.rbSuperBuyTwo.setText(getString(R.string.hive_super_purchase_two, price * 2));
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
                .setPositiveButton(R.string.hex_purchase_confirm_buy, null);
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
                    Toast.makeText(requireContext(), R.string.hive_super_purchase_ok, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show();
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
}
