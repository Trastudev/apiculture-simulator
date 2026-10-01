package com.apiculture.simulator.presentation.map;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Handler;
import android.os.Looper;
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
import androidx.fragment.app.Fragment;

import com.apiculture.simulator.ApicultureApp;
import com.apiculture.simulator.R;
import com.apiculture.simulator.data.local.entity.CargoTripEntity;
import com.apiculture.simulator.data.local.entity.TruckTripEntity;
import com.apiculture.simulator.data.repository.FleetStore;
import com.apiculture.simulator.data.repository.HoneyLogistics;
import com.apiculture.simulator.domain.game.CargoTripRules;
import com.apiculture.simulator.domain.game.FleetRules;
import com.apiculture.simulator.domain.game.NpcContractCatalog;
import com.apiculture.simulator.domain.game.TranshumanceRules;
import com.apiculture.simulator.domain.game.TruckTripRules;
import com.apiculture.simulator.domain.map.SeaportCatalog;
import com.apiculture.simulator.presentation.common.GameNotice;
import com.apiculture.simulator.presentation.common.TripCargoUi;
import com.apiculture.simulator.presentation.hive.FleetDialogs;
import com.apiculture.simulator.presentation.market.NpcPortraitUi;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Diálogo crema al pulsar un camión o un barco en el mapa. */
public final class MapVehicleDialogs {

    private MapVehicleDialogs() {
    }

    public static void showCargo(@NonNull Fragment fragment, @NonNull CargoTripEntity focus,
            @NonNull List<CargoTripEntity> sameShipment) {
        List<CargoTripEntity> bundle = new ArrayList<>();
        for (CargoTripEntity trip : sameShipment) {
            if (trip != null) {
                bundle.add(trip);
            }
        }
        if (bundle.isEmpty()) {
            bundle.add(focus);
        }
        Collections.sort(bundle, (a, b) -> Long.compare(a.startEpochMs, b.startEpochMs));
        boolean ship = CargoTripEntity.LEG_HAUL_SHIP.equals(focus.legRole);
        FleetStore.Vehicle vehicle = FleetStore.vehicle(
                fragment.requireContext(), focus.ownerId, focus.vehicleId);
        int level = vehicle != null ? vehicle.level : 1;
        if (ship && (vehicle == null || vehicle.isTruck())) {
            level = 1;
        }
        if (!ship && vehicle != null && !vehicle.isTruck()) {
            level = 1;
        }
        int icon = ship ? FleetDialogs.shipDrawable(level) : FleetDialogs.truckDrawable(level);
        String title = vehicleTitle(fragment, vehicle, ship, level);
        Dialog dialog = open(fragment, title, icon,
                HoneyLogistics.unloadedReturn(focus) || focus.kg <= 1e-6 ? 0 : focus.kg);
        bindTruckUpgrade(fragment, dialog, vehicle);
        long now = System.currentTimeMillis();
        Map<String, Double> aboard = CargoTripEntity.KIND_COLLECT.equals(focus.kind)
                ? HoneyLogistics.carriedNow(focus, now)
                : HoneyLogistics.cargoOf(focus);
        showAboard(fragment, dialog, aboard);
        LinearLayout legs = dialog.findViewById(R.id.ll_vehicle_legs);
        List<HoneyLogistics.TourLeg> tour = HoneyLogistics.collectTour(focus);
        if (tour.size() > 1) {
            Runnable refill = () -> {
                if (!fragment.isAdded()) {
                    return;
                }
                long tickNow = System.currentTimeMillis();
                legs.removeAllViews();
                LayoutInflater inflater = fragment.getLayoutInflater();
                int hive = R.drawable.ic_compracolmena;
                int warehouse = R.drawable.ic_almacen_miel;
                List<HoneyLogistics.TourLeg> live = HoneyLogistics.collectTour(focus);
                for (int i = 0; i < live.size(); i++) {
                    HoneyLogistics.TourLeg leg = live.get(i);
                    long time = HoneyLogistics.legRemaining(focus, live, i, tickNow);
                    addLeg(fragment, inflater, legs,
                            leg.fromWarehouse ? warehouse : hive,
                            leg.toWarehouse ? warehouse : hive,
                            leg.fromLabel, leg.toLabel, time, !leg.current, leg.done,
                            HoneyLogistics.carriedOnLeg(focus, live, i));
                }
            };
            showAndTick(dialog, refill);
            return;
        }
        boolean seaLeg = false;
        for (CargoTripEntity trip : bundle) {
            if (CargoTripEntity.LEG_HAUL_SHIP.equals(trip.legRole)) {
                seaLeg = true;
                break;
            }
        }
        final boolean overseas = seaLeg;
        LayoutInflater inflater = fragment.getLayoutInflater();
        for (CargoTripEntity trip : bundle) {
            if (overseas && CargoTripEntity.LEG_PICKUP.equals(trip.legRole)) {
                continue;
            }
            boolean pending = now < trip.startEpochMs;
            long time = pending ? trip.durationMs : CargoTripRules.remainingMs(trip, now);
            addLeg(fragment, inflater, legs, iconOf(trip, true), iconOf(trip, false),
                    SeaportCatalog.present(fragment.requireContext(), trip.originLabel, trip.originHexId),
                    SeaportCatalog.present(fragment.requireContext(), trip.destLabel, trip.destHexId), time, pending, false);
        }
        for (CargoTripEntity trip : bundle) {
            if (!hasNextStop(trip)) {
                continue;
            }
            double km = TranshumanceRules.haversineKm(
                    trip.destLat, trip.destLng, trip.chainLat, trip.chainLng);
            long hop = FleetRules.durationMs(km, 70);
            addLeg(fragment, inflater, legs, iconOf(trip, false), chainIcon(trip),
                    SeaportCatalog.present(fragment.requireContext(), trip.destLabel, trip.destHexId),
                    SeaportCatalog.present(fragment.requireContext(), trip.chainLabel, trip.chainHexId), hop, true, false);
        }
        showAndTick(dialog, () -> {
            if (!fragment.isAdded()) {
                return;
            }
            long tickNow = System.currentTimeMillis();
            legs.removeAllViews();
            LayoutInflater tickInflater = fragment.getLayoutInflater();
            for (CargoTripEntity trip : bundle) {
                if (overseas && CargoTripEntity.LEG_PICKUP.equals(trip.legRole)) {
                    continue;
                }
                boolean pending = tickNow < trip.startEpochMs;
                long time = pending ? trip.durationMs : CargoTripRules.remainingMs(trip, tickNow);
                addLeg(fragment, tickInflater, legs, iconOf(trip, true), iconOf(trip, false),
                        SeaportCatalog.present(fragment.requireContext(), trip.originLabel, trip.originHexId),
                        SeaportCatalog.present(fragment.requireContext(), trip.destLabel, trip.destHexId), time, pending, false);
            }
            for (CargoTripEntity trip : bundle) {
                if (!hasNextStop(trip)) {
                    continue;
                }
                double km = TranshumanceRules.haversineKm(
                        trip.destLat, trip.destLng, trip.chainLat, trip.chainLng);
                long hop = FleetRules.durationMs(km, 70);
                addLeg(fragment, tickInflater, legs, iconOf(trip, false), chainIcon(trip),
                        SeaportCatalog.present(fragment.requireContext(), trip.destLabel, trip.destHexId),
                        SeaportCatalog.present(fragment.requireContext(), trip.chainLabel, trip.chainHexId), hop, true, false);
            }
        });
    }

    public static void showHiveTruck(@NonNull Fragment fragment, @NonNull TruckTripEntity trip, int level) {
        FleetStore.Vehicle vehicle = truckForHive(fragment, trip.ownerId, trip.hiveId);
        if (vehicle != null) {
            level = vehicle.level;
        }
        Dialog dialog = open(fragment,
                vehicleTitle(fragment, vehicle, false, level),
                FleetDialogs.truckDrawable(level), 0);
        bindTruckUpgrade(fragment, dialog, vehicle);
        LinearLayout legs = dialog.findViewById(R.id.ll_vehicle_legs);
        showAndTick(dialog, () -> {
            legs.removeAllViews();
            String tickDest = trip.destHexId != null && !trip.destHexId.isEmpty() ? trip.destHexId : "Destino";
            addLeg(fragment, fragment.getLayoutInflater(), legs,
                    R.drawable.ic_compracolmena, R.drawable.ic_compracolmena,
                    "Apiario", tickDest, TruckTripRules.remainingMs(trip, System.currentTimeMillis()),
                    false, false);
        });
    }

    private static void showAndTick(@NonNull Dialog dialog, @NonNull Runnable refill) {
        Handler handler = new Handler(Looper.getMainLooper());
        Runnable task = new Runnable() {
            @Override
            public void run() {
                if (!dialog.isShowing()) {
                    return;
                }
                refill.run();
                handler.postDelayed(this, 1000L);
            }
        };
        dialog.setOnDismissListener(ignored -> handler.removeCallbacksAndMessages(null));
        refill.run();
        dialog.show();
        handler.postDelayed(task, 1000L);
    }

    @NonNull
    private static Dialog open(@NonNull Fragment fragment, @NonNull String title, int icon, double kg) {
        View root = fragment.getLayoutInflater().inflate(R.layout.dialog_map_vehicle, null);
        ((TextView) root.findViewById(R.id.tv_vehicle_title)).setText(title);
        ((ImageView) root.findViewById(R.id.iv_vehicle)).setImageResource(icon);
        TextView kgView = root.findViewById(R.id.tv_vehicle_kg);
        if (kg > 1e-6) {
            kgView.setText(fragment.getString(R.string.dashboard_trip_kg, kg));
        } else {
            kgView.setVisibility(View.GONE);
        }
        Dialog dialog = new Dialog(fragment.requireContext());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(root);
        dialog.setCancelable(true);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        root.findViewById(R.id.btn_vehicle_close).setOnClickListener(v -> dialog.dismiss());
        return dialog;
    }

    private static void bindTruckUpgrade(@NonNull Fragment fragment, @NonNull Dialog dialog,
            @Nullable FleetStore.Vehicle vehicle) {
        MaterialButton btn = dialog.findViewById(R.id.btn_vehicle_upgrade);
        if (vehicle == null) {
            btn.setVisibility(View.GONE);
            return;
        }
        paintTruckUpgrade(fragment, dialog, btn, vehicle);
    }

    private static void paintTruckUpgrade(@NonNull Fragment fragment, @NonNull Dialog dialog,
            @NonNull MaterialButton btn, @NonNull FleetStore.Vehicle vehicle) {
        btn.setVisibility(View.VISIBLE);
        boolean ship = !vehicle.isTruck();
        ((ImageView) dialog.findViewById(R.id.iv_vehicle))
                .setImageResource(ship
                        ? FleetDialogs.shipDrawable(vehicle.level)
                        : FleetDialogs.truckDrawable(vehicle.level));
        ((TextView) dialog.findViewById(R.id.tv_vehicle_title))
                .setText(vehicleTitle(fragment, vehicle, ship, vehicle.level));
        FleetRules.Kind kind = vehicle.rulesKind();
        if (vehicle.level >= FleetRules.maxLevel(kind)) {
            btn.setText(R.string.fleet_level_max);
            btn.setEnabled(false);
            btn.setAlpha(0.45f);
            btn.setOnClickListener(null);
            return;
        }
        int cost = FleetRules.upgradeCostB(kind, vehicle.level);
        btn.setText(fragment.getString(R.string.fleet_upgrade, vehicle.level + 1, cost));
        btn.setEnabled(true);
        btn.setAlpha(1f);
        String ownerId = vehicle.ownerId;
        String vehicleId = vehicle.id;
        btn.setOnClickListener(v -> {
            if (!fragment.isAdded()) {
                return;
            }
            ApicultureApp app = (ApicultureApp) fragment.requireContext().getApplicationContext();
            String err = FleetStore.upgradeVehicle(
                    fragment.requireContext(), app.getEconomyRepository(), ownerId, vehicleId);
            if (err != null) {
                GameNotice.show(fragment.requireContext(), err);
                return;
            }
            GameNotice.showSuccess(fragment.requireContext(), R.string.fleet_upgraded);
            FleetStore.Vehicle fresh = FleetStore.vehicle(fragment.requireContext(), ownerId, vehicleId);
            if (fresh != null) {
                paintTruckUpgrade(fragment, dialog, btn, fresh);
            }
        });
    }

    private static void showAboard(@NonNull Fragment fragment, @NonNull Dialog dialog,
            @NonNull Map<String, Double> aboard) {
        TextView kgView = dialog.findViewById(R.id.tv_vehicle_kg);
        View scroll = dialog.findViewById(R.id.hs_vehicle_honey);
        LinearLayout jars = dialog.findViewById(R.id.ll_vehicle_honey);
        if (aboard.isEmpty()) {
            kgView.setVisibility(View.GONE);
            scroll.setVisibility(View.GONE);
            return;
        }
        kgView.setVisibility(View.GONE);
        TripCargoUi.bind(fragment.getLayoutInflater(), jars, aboard, fragment.requireContext());
        scroll.setVisibility(jars.getVisibility());
    }

    private static void addLeg(@NonNull Fragment fragment, @NonNull LayoutInflater inflater,
            @NonNull LinearLayout host, int fromIcon, int toIcon,
            @Nullable String fromLabel, @Nullable String toLabel, long timeMs, boolean pending,
            boolean done) {
        addLeg(fragment, inflater, host, fromIcon, toIcon, fromLabel, toLabel, timeMs, pending, done, null);
    }

    private static void addLeg(@NonNull Fragment fragment, @NonNull LayoutInflater inflater,
            @NonNull LinearLayout host, int fromIcon, int toIcon,
            @Nullable String fromLabel, @Nullable String toLabel, long timeMs, boolean pending,
            boolean done, @Nullable Map<String, Double> load) {
        View leg = inflater.inflate(R.layout.item_trip_leg, host, false);
        ((ImageView) leg.findViewById(R.id.iv_leg_from)).setImageResource(fromIcon);
        ((ImageView) leg.findViewById(R.id.iv_leg_to)).setImageResource(toIcon);
        View honeyScroll = leg.findViewById(R.id.hs_leg_honey);
        LinearLayout honey = leg.findViewById(R.id.ll_leg_honey);
        if (load != null && !load.isEmpty()) {
            TripCargoUi.bind(inflater, honey, load, fragment.requireContext());
            honeyScroll.setVisibility(honey.getVisibility());
        }
        ((TextView) leg.findViewById(R.id.tv_leg_route)).setText(fragment.getString(
                R.string.dashboard_trip_route,
                fromLabel != null ? fromLabel : "Origen",
                toLabel != null ? toLabel : "Destino"));
        TextView time = leg.findViewById(R.id.tv_leg_time);
        if (done) {
            time.setText("");
        } else {
            time.setText(HoneyLogistics.formatRemaining(timeMs));
            time.setTextColor(ContextCompat.getColor(fragment.requireContext(),
                    pending ? R.color.dash_muted : R.color.event_ink));
        }
        host.addView(leg);
    }

    @NonNull
    private static String vehicleTitle(@NonNull Fragment fragment, @Nullable FleetStore.Vehicle vehicle,
            boolean ship, int level) {
        String name = vehicle != null && vehicle.name != null && !vehicle.name.trim().isEmpty()
                ? vehicle.name.trim()
                : fragment.getString(ship ? R.string.fleet_unnamed_ship : R.string.fleet_unnamed_truck);
        return fragment.getString(R.string.map_vehicle_named, name, level);
    }

    @Nullable
    private static FleetStore.Vehicle truckForHive(@NonNull Fragment fragment, @Nullable String ownerId,
            @Nullable String hiveId) {
        if (hiveId == null || hiveId.isEmpty()) {
            return null;
        }
        for (FleetStore.Vehicle vehicle : FleetStore.vehicles(fragment.requireContext(), ownerId)) {
            if (vehicle == null || !vehicle.isTruck() || vehicle.hiveIds == null) {
                continue;
            }
            for (String part : vehicle.hiveIds.split(",")) {
                if (hiveId.equals(part)) {
                    return vehicle;
                }
            }
        }
        return null;
    }

    private static boolean hasNextStop(@NonNull CargoTripEntity trip) {
        return trip.chainLabel != null && !trip.chainLabel.isEmpty()
                && (Math.abs(trip.chainLat) > 1e-8 || Math.abs(trip.chainLng) > 1e-8)
                && !trip.chainLabel.equals(trip.destLabel);
    }

    private static int iconOf(@NonNull CargoTripEntity trip, boolean origin) {
        if (CargoTripEntity.KIND_DELIVERY.equals(trip.kind)) {
            return origin ? R.drawable.ic_fleet_shop : R.drawable.ic_almacen_miel;
        }
        if (CargoTripEntity.KIND_ORDER.equals(trip.kind)) {
            boolean back = CargoTripEntity.PHASE_RETURN.equals(trip.phase);
            if (origin == back) {
                return NpcPortraitUi.faceDrawable(NpcContractCatalog.portraitIndexFor(trip.npcName));
            }
            return R.drawable.ic_almacen_miel;
        }
        if (CargoTripEntity.LEG_HAUL_SHIP.equals(trip.legRole)) {
            return R.drawable.ic_fleet_port;
        }
        if (CargoTripEntity.LEG_HAUL_TRUCK.equals(trip.legRole)
                || CargoTripEntity.LEG_PICKUP.equals(trip.legRole)) {
            return origin ? R.drawable.ic_almacen_miel : R.drawable.ic_fleet_port;
        }
        if (CargoTripEntity.LEG_DELIVER.equals(trip.legRole)) {
            if (origin) {
                return R.drawable.ic_fleet_port;
            }
            if (CargoTripEntity.KIND_WHOLESALE.equals(trip.kind)) {
                return R.drawable.ic_venta;
            }
            return R.drawable.ic_almacen_miel;
        }
        if (CargoTripEntity.LEG_TRANSFER.equals(trip.legRole)) {
            return R.drawable.ic_almacen_miel;
        }
        if (CargoTripEntity.KIND_WHOLESALE.equals(trip.kind)) {
            return origin ? R.drawable.ic_almacen_miel : R.drawable.ic_venta;
        }
        return origin ? R.drawable.ic_almacen_miel : R.drawable.ic_compracolmena;
    }

    private static int chainIcon(@NonNull CargoTripEntity trip) {
        if (CargoTripEntity.KIND_ORDER.equals(trip.kind)) {
            String name = trip.chainLabel != null && !trip.chainLabel.isEmpty()
                    ? trip.chainLabel : trip.npcName;
            return NpcPortraitUi.faceDrawable(NpcContractCatalog.portraitIndexFor(name));
        }
        if (CargoTripEntity.KIND_WHOLESALE.equals(trip.kind)) {
            return R.drawable.ic_venta;
        }
        return R.drawable.ic_almacen_miel;
    }
}
