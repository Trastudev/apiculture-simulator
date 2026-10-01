package com.apiculture.simulator.presentation.market;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
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
import com.apiculture.simulator.data.repository.EconomyRepository;
import com.apiculture.simulator.data.repository.WarehouseHoneyStore;
import com.apiculture.simulator.presentation.hive.HiveSiteSummaryUi;
import com.google.android.material.button.MaterialButton;

import java.util.Locale;
import java.util.Map;

public final class HoneyReservesDialogs {

    private HoneyReservesDialogs() {
    }

    public static void show(@NonNull Context context, @NonNull EconomyRepository economy) {
        show(context, economy, null, null);
    }

    public static void show(@NonNull Context context, @NonNull EconomyRepository economy,
            @Nullable String ownerId, @Nullable String warehouseHexId) {
        Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        View root = LayoutInflater.from(context).inflate(R.layout.dialog_honey_reserves, null, false);
        ImageView banner = root.findViewById(R.id.iv_honey_banner);
        TextView title = root.findViewById(R.id.tv_honey_title);
        TextView total = root.findViewById(R.id.tv_honey_total);
        LinearLayout itemsContainer = root.findViewById(R.id.ll_honey_items);
        MaterialButton close = root.findViewById(R.id.btn_honey_close);

        banner.setImageResource(R.drawable.ic_recolectar);
        title.setText(R.string.honey_reserves_title);

        Map<String, Double> stocks;
        double totalKg;
        if (warehouseHexId != null && !warehouseHexId.isEmpty() && ownerId != null) {
            stocks = WarehouseHoneyStore.at(context, ownerId, warehouseHexId);
            totalKg = WarehouseHoneyStore.totalAt(context, ownerId, warehouseHexId);
        } else {
            stocks = economy.copyHoneyBuckets();
            totalKg = economy.getHoneyStock();
        }
        total.setText(context.getString(R.string.honey_reserves_total, totalKg));

        itemsContainer.removeAllViews();
        boolean hasAny = false;
        LayoutInflater inflater = LayoutInflater.from(context);

        for (Map.Entry<String, Double> entry : stocks.entrySet()) {
            String flora = entry.getKey();
            Double val = entry.getValue();
            double kg = val != null ? val : 0.0;
            if (kg > 1e-6) {
                hasAny = true;
                View row = inflater.inflate(R.layout.item_honey_reserve, itemsContainer, false);
                ImageView icon = row.findViewById(R.id.iv_reserve_flora);
                TextView nameView = row.findViewById(R.id.tv_reserve_flora_name);
                TextView qtyView = row.findViewById(R.id.tv_reserve_flora_qty);

                icon.setImageResource(HiveSiteSummaryUi.floraHoneyJarIcon(flora));
                nameView.setText(HiveSiteSummaryUi.floraLabel(context, flora));
                qtyView.setText(String.format(Locale.getDefault(), "%.1f kg", kg));
                itemsContainer.addView(row);
            }
        }
        if (!hasAny) {
            TextView empty = new TextView(context);
            empty.setText(R.string.honey_reserves_empty);
            empty.setTextColor(ContextCompat.getColor(context, R.color.event_ink_muted));
            empty.setTextSize(14f);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, 24, 0, 24);
            itemsContainer.addView(empty);
        }

        dialog.setContentView(root);
        dialog.setCancelable(true);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        close.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }
}
