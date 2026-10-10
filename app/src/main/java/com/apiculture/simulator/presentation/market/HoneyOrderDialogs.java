package com.apiculture.simulator.presentation.market;

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
import com.apiculture.simulator.data.repository.OrderReceipts;
import com.apiculture.simulator.databinding.ItemHoneyOrderBinding;
import com.apiculture.simulator.domain.game.HoneyOrder;
import com.apiculture.simulator.domain.parcel.HexParcel;
import com.apiculture.simulator.presentation.hive.FleetDialogs;
import com.apiculture.simulator.presentation.hive.HiveSiteSummaryUi;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class HoneyOrderDialogs {

    private HoneyOrderDialogs() {
    }

    public static void show(@NonNull Fragment fragment, @NonNull HoneyOrder order,
            @Nullable HexParcel warehouse, @Nullable OrderCardBinder.AcceptListener listener) {
        show(fragment, order, warehouse, listener, false);
    }

    public static void show(@NonNull Fragment fragment, @NonNull HoneyOrder order,
            @Nullable HexParcel warehouse, @Nullable OrderCardBinder.AcceptListener listener,
            boolean claimed) {
        if (!fragment.isAdded() || fragment.getContext() == null) {
            return;
        }
        ItemHoneyOrderBinding row = ItemHoneyOrderBinding.inflate(fragment.getLayoutInflater());
        Dialog dialog = new Dialog(fragment.requireContext());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(row.getRoot());
        dialog.setCancelable(true);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        OrderCardBinder.bind(row, order, warehouse, claimed ? null : (o, travel) -> {
            dialog.dismiss();
            if (listener != null) {
                listener.onAccept(o, travel);
            }
        }, claimed);
        dialog.show();
    }

    public static void showTrucks(@NonNull Fragment fragment,
            @NonNull List<HoneyLogistics.OrderTruckOption> trucks,
            @NonNull Consumer<HoneyLogistics.OrderTruckOption> onPick) {
        showTrucks(fragment, trucks, onPick, null);
    }

    public static void showTrucks(@NonNull Fragment fragment,
            @NonNull List<HoneyLogistics.OrderTruckOption> trucks,
            @NonNull Consumer<HoneyLogistics.OrderTruckOption> onPick,
            @Nullable Runnable onClose) {
        showTrucks(fragment, trucks, onPick, onClose, R.string.order_truck_subtitle);
    }

    public static void showTrucks(@NonNull Fragment fragment,
            @NonNull List<HoneyLogistics.OrderTruckOption> trucks,
            @NonNull Consumer<HoneyLogistics.OrderTruckOption> onPick,
            @Nullable Runnable onClose, int subtitleRes) {
        if (!fragment.isAdded() || fragment.getContext() == null || trucks.isEmpty()) {
            if (onClose != null) {
                onClose.run();
            }
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
        TextView title = dialog.findViewById(R.id.tv_harvest_trips_title);
        TextView subtitle = dialog.findViewById(R.id.tv_harvest_trips_subtitle);
        title.setText(R.string.order_truck_title);
        subtitle.setText(subtitleRes);
        dialog.findViewById(R.id.tv_harvest_trips_skipped).setVisibility(View.GONE);
        LinearLayout list = dialog.findViewById(R.id.ll_harvest_trips);
        LayoutInflater inflater = fragment.getLayoutInflater();
        List<RadioButton> radios = new ArrayList<>();
        int[] selected = {0};
        for (int i = 0; i < trucks.size(); i++) {
            HoneyLogistics.OrderTruckOption truck = trucks.get(i);
            View row = inflater.inflate(R.layout.item_harvest_trip, list, false);
            TextView route = row.findViewById(R.id.tv_harvest_trip_route);
            TextView level = row.findViewById(R.id.tv_harvest_trip_level);
            TextView meta = row.findViewById(R.id.tv_harvest_trip_meta);
            ImageView icon = row.findViewById(R.id.iv_harvest_truck);
            RadioButton radio = row.findViewById(R.id.rb_harvest_truck);
            route.setText(fragment.getString(R.string.harvest_trips_route,
                    truck.truckLabel, truck.warehouseLabel));
            icon.setImageResource(FleetDialogs.truckDrawable(truck.level));
            level.setText(truck.holdsHoney
                    ? fragment.getString(R.string.order_truck_level_here, truck.level)
                    : fragment.getString(R.string.harvest_trips_truck_level, truck.level));
            meta.setText(fragment.getString(R.string.order_truck_meta,
                    truck.capacityKg, truck.distanceKm, truck.travelCostB));
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
            if (i < trucks.size() - 1) {
                View divider = new View(fragment.requireContext());
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, 1);
                divider.setLayoutParams(lp);
                divider.setBackgroundColor(ContextCompat.getColor(fragment.requireContext(),
                        R.color.event_gold_stroke));
                list.addView(divider);
            }
        }
        MaterialButton ok =
                dialog.findViewById(R.id.btn_harvest_trips_ok);
        ok.setText(R.string.order_truck_confirm);
        ok.setOnClickListener(v -> {
            ok.setEnabled(false);
            dialog.dismiss();
            int index = selected[0];
            if (index < 0 || index >= trucks.size()) {
                return;
            }
            onPick.accept(trucks.get(index));
        });
        dialog.findViewById(R.id.btn_harvest_trips_cancel).setOnClickListener(v -> dialog.dismiss());
        if (onClose != null) {
            dialog.setOnDismissListener(d -> onClose.run());
        }
        dialog.show();
    }

    public static void showDeliverySummary(@NonNull Activity activity,
            @NonNull OrderReceipts.Receipt receipt,
            @NonNull Runnable onDismiss) {
        if (activity.isFinishing() || activity.isDestroyed()) {
            onDismiss.run();
            return;
        }
        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_order_delivery_summary);
        dialog.setCancelable(true);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        ImageView ivNpc = dialog.findViewById(R.id.iv_order_npc_portrait);
        TextView tvNpcName = dialog.findViewById(R.id.tv_order_npc_name);
        ImageView ivJar = dialog.findViewById(R.id.iv_order_honey_jar);
        TextView tvSoldAmount = dialog.findViewById(R.id.tv_order_sold_amount);
        ImageView ivCoin = dialog.findViewById(R.id.iv_order_beecoin);
        TextView tvEarnedAmount = dialog.findViewById(R.id.tv_order_earned_amount);

        if (ivNpc != null) {
            ivNpc.setImageResource(NpcPortraitUi.faceDrawable(receipt.portraitIndex));
        }
        if (tvNpcName != null) {
            tvNpcName.setText(receipt.npcName != null && !receipt.npcName.trim().isEmpty()
                    ? receipt.npcName : activity.getString(R.string.order_delivery_title));
        }
        if (ivJar != null) {
            ivJar.setImageResource(HiveSiteSummaryUi.floraHoneyJarIcon(receipt.floraKey));
        }
        if (tvSoldAmount != null) {
            String floraLabel = HiveSiteSummaryUi.floraLabel(activity, receipt.floraKey);
            tvSoldAmount.setText(activity.getString(R.string.order_delivery_sold_amount,
                    receipt.kg, floraLabel));
        }
        if (ivCoin != null) {
            ivCoin.setImageResource(R.drawable.ic_beecoin);
        }
        if (tvEarnedAmount != null) {
            tvEarnedAmount.setText(activity.getString(R.string.order_delivery_earned_amount,
                    receipt.payout));
        }
        String[] thanks = activity.getResources().getStringArray(R.array.order_delivery_thanks_lines);
        String[] speeches = activity.getResources().getStringArray(R.array.order_delivery_speech_lines);
        int variants = Math.min(thanks.length, speeches.length);
        if (variants > 0) {
            int seed = receipt.id != null ? receipt.id.hashCode() : 0;
            int index = Math.floorMod(seed, variants);
            TextView tvThanks = dialog.findViewById(R.id.tv_order_thanks);
            TextView tvSpeech = dialog.findViewById(R.id.tv_order_speech);
            if (tvThanks != null) {
                tvThanks.setText(thanks[index]);
            }
            if (tvSpeech != null) {
                tvSpeech.setText(speeches[index]);
            }
        }

        dialog.setOnDismissListener(d -> onDismiss.run());
        View btnOk = dialog.findViewById(R.id.btn_order_summary_ok);
        if (btnOk != null) {
            btnOk.setOnClickListener(v -> dialog.dismiss());
        }
        dialog.show();
    }
}
