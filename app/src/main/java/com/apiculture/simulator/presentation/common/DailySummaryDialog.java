package com.apiculture.simulator.presentation.common;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.apiculture.simulator.R;
import com.apiculture.simulator.data.repository.DailyTickSummary;
import com.apiculture.simulator.data.repository.HiveDayStartupSummary;
import com.apiculture.simulator.data.repository.TickAppliedDayResult;
import com.apiculture.simulator.domain.game.GameCalendar;
import com.apiculture.simulator.domain.market.HoneyMarketEngine;
import com.apiculture.simulator.presentation.hive.HiveSiteSummaryUi;
import com.google.android.material.button.MaterialButton;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Resumen crema tras el tick diario matutino: miel por flora + población adulta.
 */
public final class DailySummaryDialog {

    private DailySummaryDialog() {
    }

    public static void show(@Nullable Context context, @Nullable TickAppliedDayResult result) {
        if (context == null || result == null || result.days.isEmpty()) {
            return;
        }
        Activity activity = resolveActivity(context);
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            return;
        }
        activity.runOnUiThread(() -> present(activity, result));
    }

    private static void present(@NonNull Activity activity, @NonNull TickAppliedDayResult result) {
        if (activity.isFinishing() || activity.isDestroyed()) {
            return;
        }

        Map<String, Double> honeyByFlora = new LinkedHashMap<>();
        int workerNet = 0;
        int swarm = 0;
        int velutina = 0;
        int queenMissing = 0;
        int minDay = Integer.MAX_VALUE;
        int maxDay = 0;
        for (DailyTickSummary day : result.days) {
            workerNet += day.totalWorkerNet();
            swarm += day.swarmCount;
            velutina += day.velutinaHiveCount;
            queenMissing += day.queenMissingCount;
            if (day.dayKey < minDay) {
                minDay = day.dayKey;
            }
            if (day.dayKey > maxDay) {
                maxDay = day.dayKey;
            }
            for (HiveDayStartupSummary s : day.summaries) {
                if (s.honeyKg <= 0.0005) {
                    continue;
                }
                String flora = HoneyMarketEngine.canonicalFloraKey(s.floraType);
                Double prev = honeyByFlora.get(flora);
                honeyByFlora.put(flora, (prev == null ? 0.0 : prev) + s.honeyKg);
            }
        }

        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_daily_summary);
        dialog.setCancelable(true);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        TextView tvTitle = dialog.findViewById(R.id.tv_daily_summary_title);
        LinearLayout honeyRows = dialog.findViewById(R.id.ll_daily_honey_rows);
        TextView tvHoneyEmpty = dialog.findViewById(R.id.tv_daily_honey_empty);
        TextView tvPopArrow = dialog.findViewById(R.id.tv_daily_pop_arrow);
        TextView tvPopText = dialog.findViewById(R.id.tv_daily_pop_text);
        TextView tvAlerts = dialog.findViewById(R.id.tv_daily_summary_alerts);
        MaterialButton btnOk = dialog.findViewById(R.id.btn_daily_summary_ok);

        tvTitle.setText(titleFor(activity, result.days.size(), minDay, maxDay));

        LayoutInflater inflater = LayoutInflater.from(activity);
        if (honeyByFlora.isEmpty()) {
            honeyRows.setVisibility(View.GONE);
            tvHoneyEmpty.setVisibility(View.VISIBLE);
        } else {
            honeyRows.setVisibility(View.VISIBLE);
            tvHoneyEmpty.setVisibility(View.GONE);
            honeyRows.removeAllViews();
            List<Map.Entry<String, Double>> entries = new ArrayList<>(honeyByFlora.entrySet());
            for (int i = 0; i < entries.size(); i++) {
                Map.Entry<String, Double> e = entries.get(i);
                View row = inflater.inflate(R.layout.item_daily_honey_row, honeyRows, false);
                ImageView iv = row.findViewById(R.id.iv_daily_honey_flora);
                TextView tvFlora = row.findViewById(R.id.tv_daily_honey_flora);
                TextView tvKg = row.findViewById(R.id.tv_daily_honey_kg);
                iv.setImageResource(HiveSiteSummaryUi.floraHoneyJarIcon(e.getKey()));
                tvFlora.setText(e.getKey());
                tvKg.setText(activity.getString(R.string.startup_sim_summary_honey_kg,
                        String.format(Locale.getDefault(), "%.1f", e.getValue())));
                honeyRows.addView(row);
                if (i < entries.size() - 1) {
                    View divider = new View(activity);
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, 1);
                    divider.setLayoutParams(lp);
                    divider.setBackgroundColor(ContextCompat.getColor(activity, R.color.event_gold_stroke));
                    honeyRows.addView(divider);
                }
            }
        }

        if (workerNet > 0) {
            tvPopArrow.setText("↑");
            tvPopText.setText(activity.getString(R.string.startup_sim_summary_pop_up,
                    formatSigned(workerNet)));
        } else if (workerNet < 0) {
            tvPopArrow.setText("↓");
            tvPopText.setText(activity.getString(R.string.startup_sim_summary_pop_down,
                    formatSigned(workerNet)));
        } else {
            tvPopArrow.setText("·");
            tvPopText.setText(R.string.startup_sim_summary_pop_flat);
        }

        StringBuilder alerts = new StringBuilder();
        if (swarm > 0) {
            alerts.append(activity.getString(R.string.startup_sim_summary_totals_swarm, swarm));
        }
        if (velutina > 0) {
            if (alerts.length() > 0) {
                alerts.append('\n');
            }
            alerts.append(activity.getString(R.string.startup_sim_summary_totals_velutina, velutina));
        }
        if (queenMissing > 0) {
            if (alerts.length() > 0) {
                alerts.append('\n');
            }
            alerts.append(activity.getString(R.string.startup_sim_summary_totals_queen, queenMissing));
        }
        if (alerts.length() > 0) {
            tvAlerts.setVisibility(View.VISIBLE);
            tvAlerts.setText(alerts.toString());
        } else {
            tvAlerts.setVisibility(View.GONE);
        }

        btnOk.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private static String titleFor(@NonNull Context context, int dayCount, int minDay, int maxDay) {
        DateTimeFormatter fmt = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                .withLocale(Locale.getDefault());
        if (dayCount <= 1) {
            LocalDate d = GameCalendar.fromDayKey(maxDay);
            return context.getString(R.string.startup_sim_summary_title, fmt.format(d));
        }
        LocalDate a = GameCalendar.fromDayKey(minDay);
        LocalDate b = GameCalendar.fromDayKey(maxDay);
        return context.getString(R.string.startup_sim_summary_title_multi,
                dayCount, fmt.format(a), fmt.format(b));
    }

    private static String formatSigned(int n) {
        if (n > 0) {
            return "+" + n;
        }
        return String.valueOf(n);
    }

    @Nullable
    private static Activity resolveActivity(@Nullable Context context) {
        Context walk = context;
        while (walk instanceof ContextWrapper) {
            if (walk instanceof Activity) {
                Activity a = (Activity) walk;
                if (!a.isFinishing() && !a.isDestroyed()) {
                    return a;
                }
            }
            walk = ((ContextWrapper) walk).getBaseContext();
        }
        return null;
    }
}
