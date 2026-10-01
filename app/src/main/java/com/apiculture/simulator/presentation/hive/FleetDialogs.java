package com.apiculture.simulator.presentation.hive;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.content.res.ColorStateList;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.apiculture.simulator.ApicultureApp;
import com.apiculture.simulator.R;
import com.apiculture.simulator.data.local.AppDatabase;
import com.apiculture.simulator.data.local.entity.HexParcelOwnershipEntity;
import com.apiculture.simulator.data.repository.FleetDispatch;
import com.apiculture.simulator.data.repository.FleetStore;
import com.apiculture.simulator.data.repository.HoneyLogistics;
import com.apiculture.simulator.data.repository.WarehouseHoneyStore;
import com.apiculture.simulator.domain.game.FleetRules;
import com.apiculture.simulator.domain.map.Seaport;
import com.apiculture.simulator.domain.map.SeaportCatalog;
import com.apiculture.simulator.presentation.common.GameNotice;
import com.apiculture.simulator.presentation.common.TradeGateDialogs;
import com.apiculture.simulator.data.repository.TradeAccessStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class FleetDialogs {

    private static final ExecutorService IO = Executors.newSingleThreadExecutor();

    private FleetDialogs() {
    }

    /** Del triciclo de carga al tráiler doble. */
    public static int truckDrawable(int level) {
        switch (Math.max(1, Math.min(FleetRules.TRUCK_MAX_LEVEL, level))) {
            case 1:
                return R.drawable.ic_fleet_truck_1;
            case 2:
                return R.drawable.ic_fleet_truck_2;
            case 3:
                return R.drawable.ic_fleet_truck_3;
            case 4:
                return R.drawable.ic_fleet_truck_4;
            case 5:
                return R.drawable.ic_fleet_truck_5;
            case 6:
                return R.drawable.ic_fleet_truck_6;
            case 7:
                return R.drawable.ic_fleet_truck_7;
            case 8:
                return R.drawable.ic_fleet_truck_8;
            case 9:
                return R.drawable.ic_fleet_truck_9;
            default:
                return R.drawable.ic_fleet_truck_10;
        }
    }

    /** Barcos de la lámina, del bote al carguero de miel. */
    public static int shipDrawable(int level) {
        switch (Math.max(1, Math.min(FleetRules.SHIP_MAX_LEVEL, level))) {
            case 1:
                return R.drawable.ic_fleet_ship;
            case 2:
                return R.drawable.ic_fleet_ship_2;
            case 3:
                return R.drawable.ic_fleet_ship_3;
            case 4:
                return R.drawable.ic_fleet_ship_4;
            case 5:
                return R.drawable.ic_fleet_ship_5;
            case 6:
                return R.drawable.ic_fleet_ship_6;
            case 7:
                return R.drawable.ic_fleet_ship_7;
            case 8:
                return R.drawable.ic_fleet_ship_8;
            case 9:
                return R.drawable.ic_fleet_ship_9;
            default:
                return R.drawable.ic_fleet_ship_10;
        }
    }

    @NonNull
    public static String summary(@NonNull android.content.Context context, @Nullable String ownerId,
            @Nullable String onlyHome) {
        StringBuilder sb = new StringBuilder();
        for (FleetStore.Vehicle v : FleetStore.vehicles(context, ownerId)) {
            if (onlyHome != null && !onlyHome.equals(v.homeId)) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append('\n');
            }
            String state = v.honeyBusy() || v.hiveTrips > 0
                    ? context.getString(R.string.fleet_busy)
                    : context.getString(R.string.fleet_free);
            if (v.isTruck()) {
                sb.append(titled(context, v, context.getString(R.string.fleet_truck_line, v.level,
                        FleetRules.honeyKg(FleetRules.Kind.TRUCK, v.level),
                        FleetRules.hiveSlots(FleetRules.Kind.TRUCK, v.level),
                        FleetRules.speedKmh(FleetRules.Kind.TRUCK, v.level),
                        state)));
            } else {
                Seaport port = SeaportCatalog.byId(v.homeId);
                sb.append(titled(context, v, context.getString(R.string.fleet_ship_line, v.level,
                        FleetRules.honeyKg(FleetRules.Kind.SHIP, v.level),
                        FleetRules.speedKmh(FleetRules.Kind.SHIP, v.level),
                        (port != null ? SeaportCatalog.label(context, port) : v.homeId) + " · " + state)));
            }
        }
        if (sb.length() == 0) {
            return context.getString(R.string.fleet_none);
        }
        return sb.toString();
    }

    public static void buyTruck(@NonNull Fragment fragment, @Nullable String ownerId,
            double shopLat, double shopLng) {
        if (ownerId == null || !fragment.isAdded()) {
            return;
        }
        IO.execute(() -> {
            List<HexParcelOwnershipEntity> rows = AppDatabase.getInstance(fragment.requireContext())
                    .hexParcelOwnershipDao().getWarehousesForOwnerSync(ownerId);
            List<HexParcelOwnershipEntity> homes = new ArrayList<>();
            if (rows != null) {
                for (HexParcelOwnershipEntity row : rows) {
                    if (row != null && row.hasWarehouse) {
                        homes.add(row);
                    }
                }
            }
            fragment.requireActivity().runOnUiThread(() -> {
                if (!fragment.isAdded()) {
                    return;
                }
                if (homes.isEmpty()) {
                    GameNotice.show(fragment.requireContext(), R.string.fleet_need_warehouse);
                    return;
                }
                String[] labels = new String[homes.size()];
                for (int i = 0; i < homes.size(); i++) {
                    HexParcelOwnershipEntity row = homes.get(i);
                    int slots = FleetRules.truckSlots(Math.max(1, row.warehouseLevel));
                    String home = row.parcelName != null && !row.parcelName.isEmpty()
                            ? row.parcelName
                            : fragment.getString(R.string.fleet_unnamed_warehouse);
                    labels[i] = fragment.getString(R.string.fleet_home_slots, home, slots);
                }
                showNamedChoice(fragment, R.string.fleet_buy_truck, truckDrawable(1),
                        R.string.fleet_buy_home_truck, labels,
                        (name, which) -> spendTruck(fragment, ownerId,
                                homes.get(which).hexId, shopLat, shopLng, name));
            });
        });
    }

    public static void buyShip(@NonNull Fragment fragment, @Nullable String ownerId) {
        if (ownerId == null || !fragment.isAdded()) {
            return;
        }
        List<FleetStore.PortSite> open = new ArrayList<>();
        for (FleetStore.PortSite site : FleetStore.ports(fragment.requireContext(), ownerId)) {
            if (!TradeAccessStore.portPaid(fragment.requireContext(), ownerId, site.portId)) {
                continue;
            }
            int used = 0;
            for (FleetStore.Vehicle v : FleetStore.vehicles(fragment.requireContext(), ownerId)) {
                if (!v.isTruck() && site.portId != null && site.portId.equals(v.homeId)) {
                    used++;
                }
            }
            if (used < site.berths) {
                open.add(site);
            }
        }
        if (open.isEmpty()) {
            GameNotice.show(fragment.requireContext(),
                    R.string.fleet_need_berth);
            return;
        }
        String[] labels = new String[open.size()];
        for (int i = 0; i < open.size(); i++) {
            Seaport port = SeaportCatalog.byId(open.get(i).portId);
            labels[i] = port != null
                    ? SeaportCatalog.label(fragment.requireContext(), port)
                    : open.get(i).portId;
        }
        showNamedChoice(fragment, R.string.fleet_buy_ship, shipDrawable(1),
                R.string.fleet_buy_home_ship, labels, (name, which) -> {
                    ApicultureApp app = (ApicultureApp) fragment.requireContext().getApplicationContext();
                    String err = FleetStore.buyShip(fragment.requireContext(), app.getEconomyRepository(),
                            ownerId, open.get(which).portId, name);
                    if (err == null) {
                        // Capítulo 8. Barco comprado.
                        com.apiculture.simulator.presentation.tutorial.TutorialBus.emit(
                                com.apiculture.simulator.presentation.tutorial.TutorialEvent.SHIP_BOUGHT);
                        GameNotice.showSuccess(fragment.requireContext(), R.string.fleet_bought_ship);
                    } else {
                        GameNotice.show(fragment.requireContext(), err);
                    }
                });
    }

    public static void showPort(@NonNull Fragment fragment, @Nullable String ownerId, @NonNull Seaport port) {
        if (!fragment.isAdded()) {
            return;
        }
        // Capítulo 8. Puerto.
        com.apiculture.simulator.presentation.tutorial.TutorialBus.emit(
                com.apiculture.simulator.presentation.tutorial.TutorialEvent.PORT_OPENED);
        Context context = fragment.requireContext();
        if (ownerId == null || !TradeAccessStore.portPaid(context, ownerId, port.id)) {
            TradeGateDialogs.showPort(fragment, ownerId, port,
                    () -> showPort(fragment, ownerId, port));
            return;
        }
        int mine = FleetStore.berths(context, ownerId, port.id);
        String body = mine <= 0
                ? context.getString(R.string.fleet_port_none)
                : context.getString(R.string.fleet_port_berths, mine);
        android.view.View root = LayoutInflater.from(context).inflate(R.layout.dialog_map_port, null);
        TextView title = root.findViewById(R.id.tv_port_title);
        TextView status = root.findViewById(R.id.tv_port_body);
        LinearLayout ships = root.findViewById(R.id.ll_port_ships);
        MaterialButton action = root.findViewById(R.id.btn_port_action);
        MaterialButton close = root.findViewById(R.id.btn_port_close);
        title.setText(SeaportCatalog.label(fragment.requireContext(), port));
        status.setText(body);
        fillVehicleRows(fragment, ships, ownerId, port.id, false);
        Dialog dialog = creamDialog(context, root);
        close.setOnClickListener(v -> dialog.dismiss());
        if (ownerId != null && mine < FleetRules.MAX_BERTHS) {
            action.setVisibility(android.view.View.VISIBLE);
            action.setText(fragment.getString(R.string.fleet_port_buy, FleetRules.BERTH_B));
            action.setOnClickListener(v -> {
                dialog.dismiss();
                ApicultureApp app = (ApicultureApp) context.getApplicationContext();
                String err = FleetStore.buyBerth(context, app.getEconomyRepository(), ownerId, port.id);
                toast(fragment, err, R.string.fleet_port_berth_ok);
            });
        }
        dialog.show();
    }

    public static void fillVehicleRows(@NonNull Context context, @NonNull LinearLayout host,
            @Nullable String ownerId, @Nullable String homeId, boolean trucks) {
        fillVehicleRows(context, null, host, ownerId, homeId, trucks);
    }

    public static void fillVehicleRows(@NonNull Fragment fragment, @NonNull LinearLayout host,
            @Nullable String ownerId, @Nullable String homeId, boolean trucks) {
        fillVehicleRows(fragment.requireContext(), fragment, host, ownerId, homeId, trucks);
    }

    private static void fillVehicleRows(@NonNull Context context, @Nullable Fragment fragment,
            @NonNull LinearLayout host, @Nullable String ownerId, @Nullable String homeId, boolean trucks) {
        host.removeAllViews();
        float density = context.getResources().getDisplayMetrics().density;
        int pad = Math.round(6f * density);
        int shown = 0;
        for (FleetStore.Vehicle v : FleetStore.vehicles(context, ownerId)) {
            if (v.isTruck() != trucks) {
                continue;
            }
            if (homeId != null && !homeId.equals(v.homeId)) {
                continue;
            }
            shown++;
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, pad, 0, pad);
            ImageView icon = new ImageView(context);
            int size = Math.round((trucks ? 72f : 56f) * density);
            icon.setLayoutParams(new LinearLayout.LayoutParams(size, size));
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            icon.setImageResource(trucks ? truckDrawable(v.level) : shipDrawable(v.level));
            LinearLayout textCol = new LinearLayout(context);
            textCol.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            lp.setMarginStart(Math.round(10f * density));
            textCol.setLayoutParams(lp);
            TextView label = new TextView(context);
            label.setTextColor(0xFF3E2723);
            label.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13f);
            String state = v.honeyBusy() || v.hiveTrips > 0
                    ? context.getString(R.string.fleet_busy)
                    : context.getString(R.string.fleet_free);
            if (trucks) {
                label.setText(titled(context, v, context.getString(R.string.fleet_truck_line, v.level,
                        FleetRules.honeyKg(FleetRules.Kind.TRUCK, v.level),
                        FleetRules.hiveSlots(FleetRules.Kind.TRUCK, v.level),
                        FleetRules.speedKmh(FleetRules.Kind.TRUCK, v.level),
                        state)));
            } else {
                label.setText(titled(context, v, context.getString(R.string.fleet_ship_line, v.level,
                        FleetRules.honeyKg(FleetRules.Kind.SHIP, v.level),
                        FleetRules.speedKmh(FleetRules.Kind.SHIP, v.level),
                        state)));
            }
            textCol.addView(label);
            if (fragment != null) {
                textCol.addView(upgradeButton(fragment, host, ownerId, homeId, v, trucks, density));
            }
            row.addView(icon);
            row.addView(textCol);
            host.addView(row);
        }
        if (shown == 0) {
            TextView empty = new TextView(context);
            empty.setText(trucks ? R.string.fleet_none_trucks : R.string.fleet_none_ships);
            empty.setTextColor(0xFF6D4C41);
            host.addView(empty);
        }
    }

    @NonNull
    private static MaterialButton upgradeButton(@NonNull Fragment fragment, @NonNull LinearLayout host,
            @Nullable String ownerId, @Nullable String homeId, @NonNull FleetStore.Vehicle vehicle,
            boolean trucks, float density) {
        Context context = fragment.requireContext();
        MaterialButton up = new MaterialButton(context);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        blp.topMargin = Math.round(6f * density);
        up.setLayoutParams(blp);
        up.setAllCaps(false);
        up.setTextColor(0xFF3E2723);
        up.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        up.setPadding(Math.round(10f * density), Math.round(4f * density), Math.round(10f * density), Math.round(4f * density));
        up.setMinimumHeight(0);
        up.setCornerRadius(Math.round(12f * density));
        up.setBackgroundTintList(ColorStateList.valueOf(0xFFC9892A));
        FleetRules.Kind kind = vehicle.rulesKind();
        int max = FleetRules.maxLevel(kind);
        if (vehicle.level >= max) {
            up.setText(R.string.fleet_level_max);
            up.setEnabled(false);
            up.setAlpha(0.45f);
            return up;
        }
        int cost = FleetRules.upgradeCostB(kind, vehicle.level);
        up.setText(context.getString(R.string.fleet_upgrade, vehicle.level + 1, cost));
        String vehicleId = vehicle.id;
        up.setOnClickListener(view -> {
            ApicultureApp app = (ApicultureApp) context.getApplicationContext();
            String err = FleetStore.upgradeVehicle(context, app.getEconomyRepository(), ownerId, vehicleId);
            toast(fragment, err, R.string.fleet_upgraded);
            if (err == null && fragment.isAdded()) {
                fillVehicleRows(fragment.requireContext(), fragment, host, ownerId, homeId, trucks);
            }
        });
        return up;
    }

    @NonNull
    private static Dialog creamDialog(@NonNull Context context, @NonNull android.view.View root) {
        Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(root);
        dialog.setCancelable(true);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        return dialog;
    }

    public static void showTransfer(@NonNull Fragment fragment, @Nullable String ownerId, @Nullable String fromHex) {
        if (ownerId == null || fromHex == null || !fragment.isAdded()) {
            return;
        }
        IO.execute(() -> {
            ApicultureApp app = (ApicultureApp) fragment.requireContext().getApplicationContext();
            List<HexParcelOwnershipEntity> rows = AppDatabase.getInstance(app)
                    .hexParcelOwnershipDao().getWarehousesForOwnerSync(ownerId);
            WarehouseHoneyStore.reconcile(app, app.getEconomyRepository(), ownerId, rows);
            Map<String, Double> stock = WarehouseHoneyStore.at(app, ownerId, fromHex);
            List<HexParcelOwnershipEntity> dests = new ArrayList<>();
            String fromName = fromHex;
            if (rows != null) {
                for (HexParcelOwnershipEntity row : rows) {
                    if (row == null || !row.hasWarehouse) {
                        continue;
                    }
                    if (fromHex.equals(row.hexId)) {
                        fromName = row.parcelName != null && !row.parcelName.trim().isEmpty()
                                ? row.parcelName.trim() : row.hexId;
                    } else {
                        dests.add(row);
                    }
                }
            }
            String origin = fromName;
            fragment.requireActivity().runOnUiThread(() -> {
                if (!fragment.isAdded()) {
                    return;
                }
                if (stock.isEmpty()) {
                    GameNotice.show(fragment.requireContext(), R.string.fleet_warehouse_no_honey);
                    return;
                }
                if (dests.isEmpty()) {
                    GameNotice.show(fragment.requireContext(), R.string.fleet_no_dest_warehouse);
                    return;
                }
                showTransferDialog(fragment, ownerId, fromHex, origin, stock, dests);
            });
        });
    }

    private static void showTransferDialog(@NonNull Fragment fragment, @NonNull String ownerId,
            @NonNull String fromHex, @NonNull String fromName, @NonNull Map<String, Double> stock,
            @NonNull List<HexParcelOwnershipEntity> dests) {
        View root = fragment.getLayoutInflater().inflate(R.layout.dialog_honey_transfer, null);
        ((TextView) root.findViewById(R.id.tv_transfer_from)).setText(fromName);
        LinearLayout floras = root.findViewById(R.id.ll_transfer_floras);
        LinearLayout destHost = root.findViewById(R.id.ll_transfer_dests);
        TextView kgLabel = root.findViewById(R.id.tv_transfer_kg);
        SeekBar seek = root.findViewById(R.id.seek_transfer_kg);
        View quoteBox = root.findViewById(R.id.ll_transfer_quote);
        TextView kmView = root.findViewById(R.id.tv_transfer_km);
        TextView timeView = root.findViewById(R.id.tv_transfer_time);
        TextView costView = root.findViewById(R.id.tv_transfer_cost);
        TextView blockView = root.findViewById(R.id.tv_transfer_block);
        MaterialButton sendBtn = root.findViewById(R.id.btn_transfer_send);
        String[] floraKeys = stock.keySet().toArray(new String[0]);
        String[] picked = {floraKeys[0]};
        String[] destHex = {null};
        int[] ticket = {0};
        Runnable refreshQuote = () -> refreshTransferQuote(fragment, ownerId, fromHex, picked[0],
                selectedKg(seek, stock.get(picked[0])), destHex[0], ++ticket[0], ticket,
                quoteBox, kmView, timeView, costView, blockView, sendBtn);
        for (String flora : floraKeys) {
            floras.addView(floraChip(fragment, flora, stock.get(flora), flora.equals(picked[0]), v -> {
                picked[0] = flora;
                double have = stock.get(flora);
                int steps = Math.max(1, (int) Math.round(have * 100.0));
                seek.setMax(steps);
                seek.setProgress(steps);
                kgLabel.setText(fragment.getString(R.string.fleet_transfer_kg, have, have));
                for (int i = 0; i < floras.getChildCount(); i++) {
                    floras.getChildAt(i).setAlpha(floras.getChildAt(i) == v ? 1f : 0.4f);
                }
                refreshQuote.run();
            }));
        }
        floras.getChildAt(0).setAlpha(1f);
        for (int i = 1; i < floras.getChildCount(); i++) {
            floras.getChildAt(i).setAlpha(0.4f);
        }
        double firstHave = stock.get(picked[0]);
        int steps = Math.max(1, (int) Math.round(firstHave * 100.0));
        seek.setMax(steps);
        seek.setProgress(steps);
        kgLabel.setText(fragment.getString(R.string.fleet_transfer_kg, firstHave, firstHave));
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                double have = stock.get(picked[0]);
                double kg = selectedKg(bar, have);
                kgLabel.setText(fragment.getString(R.string.fleet_transfer_kg, kg, have));
                if (fromUser) {
                    refreshQuote.run();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar bar) {
                refreshQuote.run();
            }
        });
        for (HexParcelOwnershipEntity dest : dests) {
            String name = dest.parcelName != null && !dest.parcelName.trim().isEmpty()
                    ? dest.parcelName.trim() : dest.hexId;
            LinearLayout row = new LinearLayout(fragment.requireContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setPadding(0, 8, 0, 8);
            ImageView icon = new ImageView(fragment.requireContext());
            icon.setImageResource(R.drawable.ic_almacen_miel);
            int px = Math.round(40 * fragment.getResources().getDisplayMetrics().density);
            icon.setLayoutParams(new LinearLayout.LayoutParams(px, px));
            TextView label = new TextView(fragment.requireContext());
            label.setText(name);
            label.setTextColor(0xFF4A2E10);
            label.setTextSize(15);
            label.setPadding(px / 4, 0, 0, 0);
            row.addView(icon);
            row.addView(label);
            row.setOnClickListener(v -> {
                destHex[0] = dest.hexId;
                for (int i = 0; i < destHost.getChildCount(); i++) {
                    destHost.getChildAt(i).setAlpha(destHost.getChildAt(i) == v ? 1f : 0.4f);
                }
                refreshQuote.run();
            });
            row.setAlpha(0.4f);
            destHost.addView(row);
        }
        Dialog dialog = creamDialog(fragment.requireContext(), root);
        sendBtn.setOnClickListener(v -> {
            String to = destHex[0];
            if (to == null) {
                return;
            }
            dialog.dismiss();
            send(fragment, ownerId, fromHex, picked[0], selectedKg(seek, stock.get(picked[0])), to);
        });
        root.findViewById(R.id.btn_transfer_close).setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private static double selectedKg(@NonNull SeekBar seek, double have) {
        if (seek.getMax() <= 0 || have <= 0) {
            return 0;
        }
        double kg = have * seek.getProgress() / (double) seek.getMax();
        return Math.round(kg * 100.0) / 100.0;
    }

    @NonNull
    private static View floraChip(@NonNull Fragment fragment, @NonNull String flora, double kg,
            boolean selected, @NonNull View.OnClickListener click) {
        LinearLayout box = new LinearLayout(fragment.requireContext());
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
        int pad = Math.round(8 * fragment.getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad, pad, pad);
        ImageView jar = new ImageView(fragment.requireContext());
        jar.setImageResource(HiveSiteSummaryUi.floraHoneyJarIcon(flora));
        int px = Math.round(56 * fragment.getResources().getDisplayMetrics().density);
        jar.setLayoutParams(new LinearLayout.LayoutParams(px, px));
        TextView name = new TextView(fragment.requireContext());
        name.setText(flora);
        name.setTextColor(0xFF4A2E10);
        name.setTextSize(11);
        name.setGravity(android.view.Gravity.CENTER);
        TextView amount = new TextView(fragment.requireContext());
        amount.setText(fragment.getString(R.string.fleet_transfer_stock, kg));
        amount.setTextColor(0xFF4A2E10);
        amount.setTextSize(12);
        amount.setGravity(android.view.Gravity.CENTER);
        box.addView(jar);
        box.addView(name);
        box.addView(amount);
        box.setAlpha(selected ? 1f : 0.4f);
        box.setOnClickListener(click);
        return box;
    }

    private static void refreshTransferQuote(@NonNull Fragment fragment, @NonNull String ownerId,
            @NonNull String fromHex, @NonNull String flora, double kg, @Nullable String toHex,
            int ticket, @NonNull int[] current, @NonNull View quoteBox, @NonNull TextView kmView,
            @NonNull TextView timeView, @NonNull TextView costView, @NonNull TextView blockView,
            @NonNull MaterialButton sendBtn) {
        if (toHex == null || kg <= 0) {
            quoteBox.setVisibility(View.GONE);
            blockView.setVisibility(View.GONE);
            sendBtn.setEnabled(false);
            return;
        }
        IO.execute(() -> {
            ApicultureApp app = (ApicultureApp) fragment.requireContext().getApplicationContext();
            FleetDispatch.TransferPreview preview = FleetDispatch.previewTransfer(
                    app, ownerId, app.getEconomyRepository(), fromHex, flora, kg, toHex);
            if (!fragment.isAdded()) {
                return;
            }
            fragment.requireActivity().runOnUiThread(() -> {
                if (ticket != current[0] || !fragment.isAdded()) {
                    return;
                }
                if (preview.block != null) {
                    quoteBox.setVisibility(View.GONE);
                    blockView.setVisibility(View.VISIBLE);
                    blockView.setText(preview.block);
                    sendBtn.setEnabled(false);
                    return;
                }
                blockView.setVisibility(View.GONE);
                quoteBox.setVisibility(View.VISIBLE);
                kmView.setText(fragment.getString(R.string.fleet_transfer_km, preview.km));
                timeView.setText(HoneyLogistics.formatRemaining(preview.durationMs));
                costView.setText(fragment.getString(R.string.fleet_transfer_cost, preview.costB));
                sendBtn.setEnabled(true);
            });
        });
    }

    private static void send(@NonNull Fragment fragment, @NonNull String ownerId, @NonNull String fromHex,
            @NonNull String flora, double kg, @NonNull String toHex) {
        IO.execute(() -> {
            ApicultureApp app = (ApicultureApp) fragment.requireContext().getApplicationContext();
            String err = FleetDispatch.transfer(app, ownerId, app.getEconomyRepository(),
                    fromHex, flora, kg, toHex, 0, null);
            fragment.requireActivity().runOnUiThread(() -> {
                if (!fragment.isAdded()) {
                    return;
                }
                toast(fragment, err, R.string.fleet_transfer_ok);
            });
        });
    }

    private static void spendTruck(@NonNull Fragment fragment, @NonNull String ownerId, @NonNull String hexId,
            double shopLat, double shopLng, @Nullable String name) {
        Context app = fragment.requireContext().getApplicationContext();
        IO.execute(() -> {
            String err = FleetStore.buyTruck(app, ((ApicultureApp) app).getEconomyRepository(),
                    ownerId, hexId, name);
            if (err == null) {
                FleetDispatch.deliverNewTruck(app, ownerId, hexId, shopLat, shopLng);
                // Capítulo 6. Camión comprado.
                com.apiculture.simulator.presentation.tutorial.TutorialBus.emit(
                        com.apiculture.simulator.presentation.tutorial.TutorialEvent.TRUCK_BOUGHT);
            }
            if (!fragment.isAdded()) {
                return;
            }
            fragment.requireActivity().runOnUiThread(() -> toast(fragment, err, R.string.fleet_bought_truck));
        });
    }

    private interface NamedPick {
        void pick(@NonNull String name, int index);
    }

    private static void showNamedChoice(@NonNull Fragment fragment, int titleRes, int iconRes,
            int homeRes, @NonNull String[] labels, @NonNull NamedPick pick) {
        if (!fragment.isAdded() || labels.length == 0) {
            return;
        }
        Context context = fragment.requireContext();
        View root = LayoutInflater.from(context).inflate(R.layout.dialog_named_choice, null);
        TextView title = root.findViewById(R.id.tv_named_title);
        ImageView icon = root.findViewById(R.id.iv_named_icon);
        TextView home = root.findViewById(R.id.tv_named_home);
        LinearLayout choices = root.findViewById(R.id.ll_named_choices);
        android.widget.EditText nameField = root.findViewById(R.id.edit_named);
        MaterialButton ok = root.findViewById(R.id.btn_named_ok);
        MaterialButton close = root.findViewById(R.id.btn_named_close);
        title.setText(titleRes);
        icon.setImageResource(iconRes);
        home.setText(homeRes);
        ok.setText(titleRes);
        int[] selected = {0};
        TextView[] rows = new TextView[labels.length];
        for (int i = 0; i < labels.length; i++) {
            TextView row = new TextView(context);
            row.setText(labels[i]);
            row.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 15f);
            row.setPadding(24, 18, 24, 18);
            int index = i;
            row.setOnClickListener(v -> {
                selected[0] = index;
                paintChoice(rows, selected[0]);
            });
            rows[i] = row;
            choices.addView(row);
        }
        paintChoice(rows, 0);
        Dialog dialog = creamDialog(context, root);
        boolean tutorialTruck = com.apiculture.simulator.presentation.tutorial.TutorialBus.onlyTruck();
        if (tutorialTruck) {
            dialog.setCancelable(false);
            close.setEnabled(false);
            close.setAlpha(0.4f);
        }
        close.setOnClickListener(v -> dialog.dismiss());
        ok.setOnClickListener(v -> {
            String name = nameField.getText() == null ? "" : nameField.getText().toString();
            if (com.apiculture.simulator.domain.game.EntityNames.clean(name) == null) {
                GameNotice.show(context, R.string.fleet_name_length);
                return;
            }
            dialog.dismiss();
            pick.pick(name, selected[0]);
        });
        dialog.show();
        if (tutorialTruck) {
            com.apiculture.simulator.presentation.tutorial.TutorialBus.emitDialog(
                    com.apiculture.simulator.presentation.tutorial.TutorialEvent.TRUCK_FORM,
                    dialog, nameField);
        }
    }

    private static void paintChoice(@NonNull TextView[] rows, int selected) {
        for (int i = 0; i < rows.length; i++) {
            rows[i].setTextColor(0xFF3E2723);
            rows[i].setBackgroundColor(i == selected ? 0x33C4A35A : 0x00000000);
        }
    }

    @NonNull
    private static String titled(@NonNull Context context, @NonNull FleetStore.Vehicle v,
            @NonNull String line) {
        String name = v.name;
        if (name == null || name.trim().isEmpty()) {
            name = context.getString(v.isTruck() ? R.string.fleet_unnamed_truck : R.string.fleet_unnamed_ship);
        }
        return name + " · " + line;
    }

    private static void toast(@NonNull Fragment fragment, @Nullable String err, int ok) {
        if (!fragment.isAdded()) {
            return;
        }
        if (err == null) {
            GameNotice.showSuccess(fragment.requireContext(), ok);
        } else {
            GameNotice.show(fragment.requireContext(), err);
        }
    }
}
