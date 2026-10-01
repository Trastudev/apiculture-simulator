package com.apiculture.simulator.presentation.hive;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.apiculture.simulator.R;
import com.apiculture.simulator.data.repository.HoneyLogistics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public final class HarvestCollectDialogs {

    private HarvestCollectDialogs() {
    }

    public static void showTrips(@NonNull Fragment fragment,
            @NonNull List<HoneyLogistics.CollectPreview> trips, int skippedNoWarehouse,
            @NonNull Runnable onConfirm) {
        showTrips(fragment, trips, skippedNoWarehouse, 0, chosen -> onConfirm.run(), false);
    }

    public static void showTrips(@NonNull Fragment fragment,
            @NonNull List<HoneyLogistics.CollectPreview> trips, int skippedNoWarehouse,
            int leftBehind, @NonNull Consumer<List<HoneyLogistics.CollectPreview>> onConfirm) {
        showTrips(fragment, trips, skippedNoWarehouse, leftBehind, onConfirm, false);
    }

    public static void showTrips(@NonNull Fragment fragment,
            @NonNull List<HoneyLogistics.CollectPreview> trips, int skippedNoWarehouse,
            int leftBehind, @NonNull Consumer<List<HoneyLogistics.CollectPreview>> onConfirm,
            boolean pickOne) {
        if (!fragment.isAdded() || fragment.getContext() == null || trips.isEmpty()) {
            return;
        }
        Dialog dialog = new Dialog(fragment.requireContext());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_harvest_trips);
        dialog.setCancelable(true);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        TextView subtitle = dialog.findViewById(R.id.tv_harvest_trips_subtitle);
        final boolean chooseTruck = pickOne && hasTruckChoice(trips);
        subtitle.setText(chooseTruck || !hasTruckChoice(trips)
                ? R.string.harvest_trips_subtitle
                : R.string.harvest_trips_plan);
        LinearLayout list = dialog.findViewById(R.id.ll_harvest_trips);
        TextView skipped = dialog.findViewById(R.id.tv_harvest_trips_skipped);
        LayoutInflater inflater = fragment.getLayoutInflater();
        List<RadioButton> radios = new ArrayList<>();
        int[] selected = {0};
        for (int i = 0; i < trips.size(); i++) {
            HoneyLogistics.CollectPreview trip = trips.get(i);
            View row = inflater.inflate(R.layout.item_harvest_trip, list, false);
            TextView route = row.findViewById(R.id.tv_harvest_trip_route);
            TextView level = row.findViewById(R.id.tv_harvest_trip_level);
            TextView meta = row.findViewById(R.id.tv_harvest_trip_meta);
            ImageView icon = row.findViewById(R.id.iv_harvest_truck);
            RadioButton radio = row.findViewById(R.id.rb_harvest_truck);
            route.setText(fragment.getString(R.string.harvest_trips_route,
                    trip.truckLabel, trip.warehouseLabel));
            if (trip.truckLevel > 0) {
                icon.setImageResource(FleetDialogs.truckDrawable(trip.truckLevel));
                level.setText(fragment.getString(R.string.harvest_trips_truck_level, trip.truckLevel));
                radio.setVisibility(chooseTruck ? View.VISIBLE : View.GONE);
                radio.setChecked(chooseTruck && i == 0);
            } else {
                icon.setVisibility(View.GONE);
                level.setVisibility(View.GONE);
                radio.setVisibility(View.GONE);
            }
            if (trip.distanceKm > 1e-6) {
                meta.setText(fragment.getString(R.string.harvest_trips_meta_km, trip.kg,
                        trip.distanceKm, trip.hiveLabel));
            } else if (trip.instantToday) {
                meta.setText(fragment.getString(R.string.harvest_trips_meta_instant, trip.kg));
            } else {
                meta.setText(fragment.getString(R.string.harvest_trips_meta, trip.kg,
                        HoneyLogistics.formatRemaining(trip.roundTripMs)));
            }
            radios.add(radio);
            final int index = i;
            if (chooseTruck) {
                row.setOnClickListener(v -> {
                    selected[0] = index;
                    for (int j = 0; j < radios.size(); j++) {
                        radios.get(j).setChecked(j == index);
                    }
                    bindSkipped(fragment, skipped, skippedNoWarehouse, trips.get(index).leftBehind);
                });
            }
            list.addView(row);
            if (i < trips.size() - 1) {
                View divider = new View(fragment.requireContext());
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, 1);
                divider.setLayoutParams(lp);
                divider.setBackgroundColor(ContextCompat.getColor(fragment.requireContext(),
                        R.color.event_gold_stroke));
                list.addView(divider);
            }
        }
        int initialLeft = chooseTruck ? trips.get(0).leftBehind : leftBehind;
        bindSkipped(fragment, skipped, skippedNoWarehouse, initialLeft);
        com.google.android.material.button.MaterialButton ok = dialog.findViewById(R.id.btn_harvest_trips_ok);
        ok.setText(!chooseTruck && trips.size() > 1
                ? R.string.harvest_trips_confirm
                : R.string.harvest_trips_confirm_one);
        ok.setOnClickListener(v -> {
            dialog.dismiss();
            if (chooseTruck) {
                onConfirm.accept(Collections.singletonList(trips.get(selected[0])));
            } else {
                onConfirm.accept(trips);
            }
        });
        dialog.findViewById(R.id.btn_harvest_trips_cancel).setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    public static void pick(@NonNull Fragment fragment, int title, int subtitle,
            @NonNull List<String> labels, @NonNull Consumer<Integer> onPick) {
        pick(fragment, title, subtitle, labels, null, onPick);
    }

    public static void pick(@NonNull Fragment fragment, int title, int subtitle,
            @NonNull List<String> labels, @Nullable List<String> metas,
            @NonNull Consumer<Integer> onPick) {
        if (!fragment.isAdded() || labels.isEmpty()) {
            return;
        }
        Dialog dialog = new Dialog(fragment.requireContext());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_harvest_trips);
        dialog.setCancelable(true);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        TextView titleView = dialog.findViewById(R.id.tv_harvest_trips_title);
        TextView subtitleView = dialog.findViewById(R.id.tv_harvest_trips_subtitle);
        titleView.setText(title);
        subtitleView.setText(subtitle);
        LinearLayout list = dialog.findViewById(R.id.ll_harvest_trips);
        dialog.findViewById(R.id.tv_harvest_trips_skipped).setVisibility(View.GONE);
        LayoutInflater inflater = fragment.getLayoutInflater();
        List<RadioButton> radios = new ArrayList<>();
        int[] selected = {0};
        for (int i = 0; i < labels.size(); i++) {
            View row = inflater.inflate(R.layout.item_harvest_trip, list, false);
            TextView route = row.findViewById(R.id.tv_harvest_trip_route);
            TextView level = row.findViewById(R.id.tv_harvest_trip_level);
            TextView meta = row.findViewById(R.id.tv_harvest_trip_meta);
            ImageView icon = row.findViewById(R.id.iv_harvest_truck);
            RadioButton radio = row.findViewById(R.id.rb_harvest_truck);
            route.setText(labels.get(i));
            icon.setVisibility(View.GONE);
            level.setVisibility(View.GONE);
            if (metas != null && i < metas.size() && metas.get(i) != null && !metas.get(i).isEmpty()) {
                meta.setText(metas.get(i));
            } else {
                meta.setVisibility(View.GONE);
            }
            radio.setVisibility(View.VISIBLE);
            radio.setChecked(i == 0);
            radios.add(radio);
            final int index = i;
            row.setOnClickListener(v -> {
                selected[0] = index;
                for (int j = 0; j < radios.size(); j++) {
                    radios.get(j).setChecked(j == index);
                }
            });
            list.addView(row);
        }
        com.google.android.material.button.MaterialButton ok = dialog.findViewById(R.id.btn_harvest_trips_ok);
        ok.setText(R.string.harvest_trips_confirm_one);
        ok.setOnClickListener(v -> {
            dialog.dismiss();
            onPick.accept(selected[0]);
        });
        dialog.findViewById(R.id.btn_harvest_trips_cancel).setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private static boolean hasTruckChoice(@NonNull List<HoneyLogistics.CollectPreview> trips) {
        for (HoneyLogistics.CollectPreview trip : trips) {
            if (trip.truckLevel > 0) {
                return true;
            }
        }
        return false;
    }

    private static void bindSkipped(@NonNull Fragment fragment, @NonNull TextView skipped,
            int skippedNoWarehouse, int leftBehind) {
        if (skippedNoWarehouse > 0) {
            skipped.setVisibility(View.VISIBLE);
            skipped.setText(fragment.getString(R.string.harvest_trips_skipped, skippedNoWarehouse));
        } else if (leftBehind > 0) {
            skipped.setVisibility(View.VISIBLE);
            skipped.setText(fragment.getString(R.string.harvest_trips_left, leftBehind));
        } else {
            skipped.setVisibility(View.GONE);
        }
    }

    public static void showSummary(@NonNull Fragment fragment, @NonNull HoneyLogistics.HarvestCommit result) {
        if (!fragment.isAdded() || fragment.getContext() == null || !result.success) {
            return;
        }
        Dialog dialog = new Dialog(fragment.requireContext());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_harvest_summary);
        dialog.setCancelable(true);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        TextView tvSub = dialog.findViewById(R.id.tv_harvest_summary_subtitle);
        TextView tvTotal = dialog.findViewById(R.id.tv_harvest_summary_total);
        LinearLayout rows = dialog.findViewById(R.id.ll_harvest_honey_rows);
        tvSub.setText(fragment.getString(R.string.dashboard_harvest_summary_subtitle, result.hiveCount));
        tvTotal.setText(fragment.getString(R.string.dashboard_harvest_summary_total, result.totalKg));
        rows.removeAllViews();
        LayoutInflater inflater = fragment.getLayoutInflater();
        java.util.List<java.util.Map.Entry<String, Double>> entries =
                new java.util.ArrayList<>(result.kgByFlora.entrySet());
        for (int i = 0; i < entries.size(); i++) {
            java.util.Map.Entry<String, Double> e = entries.get(i);
            View row = inflater.inflate(R.layout.item_daily_honey_row, rows, false);
            android.widget.ImageView iv = row.findViewById(R.id.iv_daily_honey_flora);
            TextView tvFlora = row.findViewById(R.id.tv_daily_honey_flora);
            TextView tvKg = row.findViewById(R.id.tv_daily_honey_kg);
            iv.setImageResource(HiveSiteSummaryUi.floraHoneyJarIcon(e.getKey()));
            tvFlora.setText(e.getKey());
            tvKg.setText(fragment.getString(R.string.dashboard_harvest_summary_kg, e.getValue()));
            rows.addView(row);
            if (i < entries.size() - 1) {
                View divider = new View(fragment.requireContext());
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, 1);
                divider.setLayoutParams(lp);
                divider.setBackgroundColor(ContextCompat.getColor(fragment.requireContext(),
                        R.color.event_gold_stroke));
                rows.addView(divider);
            }
        }
        dialog.findViewById(R.id.btn_harvest_summary_ok).setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    /** Resumen de una recolección que ya llegó al almacén. */
    public static void showSummary(@NonNull Activity activity, int hiveCount, double totalKg,
            @NonNull Map<String, Double> kgByFlora, @NonNull Runnable onDismiss) {
        if (activity.isFinishing() || kgByFlora.isEmpty()) {
            onDismiss.run();
            return;
        }
        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_harvest_summary);
        dialog.setCancelable(true);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        TextView tvSub = dialog.findViewById(R.id.tv_harvest_summary_subtitle);
        TextView tvTotal = dialog.findViewById(R.id.tv_harvest_summary_total);
        LinearLayout rows = dialog.findViewById(R.id.ll_harvest_honey_rows);
        tvSub.setText(activity.getString(R.string.dashboard_harvest_summary_subtitle, hiveCount));
        tvTotal.setText(activity.getString(R.string.dashboard_harvest_summary_total, totalKg));
        rows.removeAllViews();
        LayoutInflater inflater = activity.getLayoutInflater();
        List<Map.Entry<String, Double>> entries = new ArrayList<>(kgByFlora.entrySet());
        for (int i = 0; i < entries.size(); i++) {
            Map.Entry<String, Double> e = entries.get(i);
            View row = inflater.inflate(R.layout.item_daily_honey_row, rows, false);
            ImageView iv = row.findViewById(R.id.iv_daily_honey_flora);
            TextView tvFlora = row.findViewById(R.id.tv_daily_honey_flora);
            TextView tvKg = row.findViewById(R.id.tv_daily_honey_kg);
            iv.setImageResource(HiveSiteSummaryUi.floraHoneyJarIcon(e.getKey()));
            tvFlora.setText(e.getKey());
            tvKg.setText(activity.getString(R.string.dashboard_harvest_summary_kg, e.getValue()));
            rows.addView(row);
            if (i < entries.size() - 1) {
                View divider = new View(activity);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, 1);
                divider.setLayoutParams(lp);
                divider.setBackgroundColor(ContextCompat.getColor(activity, R.color.event_gold_stroke));
                rows.addView(divider);
            }
        }
        Runnable close = () -> {
            if (dialog.isShowing()) {
                dialog.dismiss();
            }
        };
        dialog.setOnDismissListener(d -> onDismiss.run());
        dialog.findViewById(R.id.btn_harvest_summary_ok).setOnClickListener(v -> close.run());
        dialog.show();
    }
}
