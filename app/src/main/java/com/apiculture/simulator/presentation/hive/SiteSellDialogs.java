package com.apiculture.simulator.presentation.hive;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.apiculture.simulator.ApicultureApp;
import com.apiculture.simulator.R;
import com.apiculture.simulator.data.local.entity.CargoTripEntity;
import com.apiculture.simulator.data.local.entity.HexParcelOwnershipEntity;
import com.apiculture.simulator.data.local.entity.TruckTripEntity;
import com.apiculture.simulator.data.repository.HoneyLogistics;
import com.apiculture.simulator.domain.game.CargoTripRules;
import com.apiculture.simulator.domain.game.TruckTripRules;
import com.apiculture.simulator.domain.map.SeaportCatalog;
import com.apiculture.simulator.domain.parcel.HexParcel;
import com.apiculture.simulator.domain.parcel.HexParcelRandomPoint;
import com.apiculture.simulator.domain.parcel.WarehouseRules;
import com.apiculture.simulator.data.repository.IberiaHexOverlayStore;
import com.apiculture.simulator.presentation.common.GameNotice;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.apiculture.simulator.data.session.PlayerAuth;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

public final class SiteSellDialogs {

    private SiteSellDialogs() {
    }

    public static void sellApiary(@NonNull Fragment fragment, @NonNull HiveViewModel viewModel,
            @NonNull String hexId, @Nullable String siteId) {
        if (!fragment.isAdded() || fragment.getContext() == null) {
            return;
        }
        String ownerId = PlayerAuth.getInstance().getUid();
        if (ownerId == null || ownerId.isEmpty()) {
            GameNotice.show(fragment.requireContext(), R.string.hive_buy_session_invalid);
            return;
        }
        ApicultureApp app = (ApicultureApp) fragment.requireContext().getApplicationContext();
        Handler main = new Handler(Looper.getMainLooper());
        Executors.newSingleThreadExecutor().execute(() -> {
            int hives = app.getHexParcelRepository().countHivesAtSiteBlocking(ownerId, hexId, siteId);
            int paid = HiveViewModel.hexPurchasePriceEurosForHex(hexId, app);
            int refund = WarehouseRules.sellRefundB(paid);
            main.post(() -> {
                if (!fragment.isAdded()) {
                    return;
                }
                if (hives > 0) {
                    new MaterialAlertDialogBuilder(fragment.requireContext())
                            .setTitle(R.string.map_sell_apiary_blocked_title)
                            .setMessage(fragment.getString(R.string.map_sell_apiary_blocked_hives, hives))
                            .setPositiveButton(android.R.string.ok, null)
                            .show();
                    return;
                }
                new MaterialAlertDialogBuilder(fragment.requireContext())
                        .setTitle(R.string.map_sell_apiary)
                        .setMessage(fragment.getString(R.string.map_sell_apiary_confirm, (double) refund))
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(R.string.map_sell_confirm, (d, w) ->
                                viewModel.sellApiary(ownerId, hexId, siteId, msg ->
                                        onSold(fragment, msg, R.string.map_sell_apiary_ok, refund)))
                        .show();
            });
        });
    }

    public static void sellWarehouse(@NonNull Fragment fragment, @NonNull HiveViewModel viewModel,
            @Nullable List<HexParcelOwnershipEntity> ownerships,
            @Nullable String ownerId, @Nullable String hexId, @Nullable String siteId) {
        if (!fragment.isAdded() || fragment.getContext() == null || hexId == null || ownerId == null) {
            return;
        }
        ApicultureApp app = (ApicultureApp) fragment.requireContext().getApplicationContext();
        Handler main = new Handler(Looper.getMainLooper());
        Executors.newSingleThreadExecutor().execute(() -> {
            HexParcelOwnershipEntity warehouse = warehouseOf(ownerships, ownerId, hexId, siteId);
            if (warehouse == null || !warehouse.hasWarehouse) {
                main.post(() -> {
                    if (fragment.isAdded()) {
                        GameNotice.show(fragment.requireContext(), R.string.map_sell_warehouse_missing);
                    }
                });
                return;
            }
            HexParcel parcel = IberiaHexOverlayStore.findById(app, hexId);
            double[] dock = HexParcelRandomPoint.warehouseOf(parcel, warehouse);
            List<CargoTripEntity> cargo = HoneyLogistics.cargoTouchingHex(app, ownerId, hexId);
            List<TruckTripEntity> trucks = HoneyLogistics.trucksNearDock(app, ownerId, dock[0], dock[1]);
            int refund = WarehouseRules.warehouseSellRefundB(WarehouseRules.levelOf(warehouse));
            main.post(() -> {
                if (!fragment.isAdded()) {
                    return;
                }
                if (!cargo.isEmpty() || !trucks.isEmpty()) {
                    new MaterialAlertDialogBuilder(fragment.requireContext())
                            .setTitle(R.string.map_sell_warehouse_blocked_title)
                            .setMessage(tripBlockMessage(fragment, cargo, trucks))
                            .setPositiveButton(android.R.string.ok, null)
                            .show();
                    return;
                }
                new MaterialAlertDialogBuilder(fragment.requireContext())
                        .setTitle(R.string.map_sell_warehouse)
                        .setMessage(fragment.getString(R.string.map_sell_warehouse_confirm, (double) refund))
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(R.string.map_sell_confirm, (d, w) ->
                                viewModel.sellWarehouse(ownerId, hexId, siteId, msg ->
                                        onSold(fragment, msg, R.string.map_sell_warehouse_ok, refund)))
                        .show();
            });
        });
    }

    @Nullable
    private static HexParcelOwnershipEntity warehouseOf(
            @Nullable List<HexParcelOwnershipEntity> ownerships,
            @Nullable String ownerId, @Nullable String hexId, @Nullable String siteId) {
        if (ownerships == null || hexId == null) {
            return null;
        }
        String want = siteId != null && !siteId.isEmpty() ? siteId : null;
        HexParcelOwnershipEntity fallback = null;
        for (HexParcelOwnershipEntity row : ownerships) {
            if (row == null || !hexId.equals(row.hexId) || !row.hasWarehouse) {
                continue;
            }
            if (ownerId != null && !ownerId.equals(row.ownerId)) {
                continue;
            }
            if (want != null) {
                String got = row.siteId != null && !row.siteId.isEmpty() ? row.siteId : "default";
                if (want.equals(got)) {
                    return row;
                }
            }
            if (fallback == null) {
                fallback = row;
            }
        }
        return fallback;
    }

    @NonNull
    private static String tripBlockMessage(
            @NonNull Fragment fragment,
            @NonNull List<CargoTripEntity> cargo,
            @NonNull List<TruckTripEntity> trucks) {
        long now = System.currentTimeMillis();
        StringBuilder sb = new StringBuilder(fragment.getString(R.string.map_sell_warehouse_blocked_trips));
        List<String> lines = new ArrayList<>();
        for (CargoTripEntity trip : cargo) {
            String kind = cargoKind(fragment, trip);
            String from = SeaportCatalog.present(fragment.requireContext(), 
                    trip.originLabel != null ? trip.originLabel : "Origen", trip.originHexId);
            String to = SeaportCatalog.present(fragment.requireContext(), 
                    trip.destLabel != null ? trip.destLabel : "Destino", trip.destHexId);
            String eta = HoneyLogistics.formatRemaining(CargoTripRules.remainingMs(trip, now));
            String cargoLine = HoneyLogistics.cargoSummary(trip);
            if (cargoLine.isEmpty()) {
                lines.add(fragment.getString(R.string.map_sell_trip_line, kind, from, to, eta));
            } else {
                lines.add(fragment.getString(R.string.map_sell_trip_line_cargo,
                        kind, cargoLine, from, to, eta));
            }
        }
        for (TruckTripEntity trip : trucks) {
            String eta = HoneyLogistics.formatRemaining(TruckTripRules.remainingMs(trip, now));
            lines.add(fragment.getString(R.string.map_sell_trip_line_hive, eta));
        }
        for (String line : lines) {
            sb.append("\n\n").append(line);
        }
        return sb.toString();
    }

    @NonNull
    private static String cargoKind(@NonNull Fragment fragment, @NonNull CargoTripEntity trip) {
        if (CargoTripEntity.KIND_WHOLESALE.equals(trip.kind)) {
            return fragment.getString(R.string.map_sell_trip_kind_wholesale);
        }
        if (CargoTripEntity.KIND_ORDER.equals(trip.kind)) {
            return fragment.getString(R.string.map_sell_trip_kind_order);
        }
        if (CargoTripEntity.PHASE_RETURN.equals(trip.phase)) {
            return fragment.getString(R.string.map_sell_trip_kind_collect_back);
        }
        return fragment.getString(R.string.map_sell_trip_kind_collect);
    }

    private static void onSold(@NonNull Fragment fragment, @Nullable String msg, int okRes, int refund) {
        if (!fragment.isAdded()) {
            return;
        }
        if ("HAS_HIVES".equals(msg)) {
            new MaterialAlertDialogBuilder(fragment.requireContext())
                    .setTitle(R.string.map_sell_apiary_blocked_title)
                    .setMessage(R.string.map_sell_apiary_blocked_hives_generic)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
            return;
        }
        if ("HAS_TRIPS".equals(msg)) {
            new MaterialAlertDialogBuilder(fragment.requireContext())
                    .setTitle(R.string.map_sell_warehouse_blocked_title)
                    .setMessage(R.string.map_sell_warehouse_blocked_trips)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
            return;
        }
        if (msg == null) {
            GameNotice.showSuccess(fragment.requireContext(),
                    fragment.getString(okRes, (double) refund));
        } else {
            GameNotice.show(fragment.requireContext(), msg);
        }
    }
}
