package com.apiculture.simulator.presentation.hive;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.apiculture.simulator.ApicultureApp;
import com.apiculture.simulator.R;
import com.apiculture.simulator.data.local.entity.HexParcelOwnershipEntity;
import com.apiculture.simulator.data.repository.FleetStore;
import com.apiculture.simulator.data.repository.WarehouseHoneyStore;
import com.apiculture.simulator.databinding.DialogBuyWarehouseBinding;
import com.apiculture.simulator.databinding.DialogMapBuildChoiceBinding;
import com.apiculture.simulator.databinding.DialogWarehouseStatusBinding;
import com.apiculture.simulator.domain.game.FleetRules;
import com.apiculture.simulator.domain.parcel.WarehouseRules;
import com.apiculture.simulator.presentation.common.GameNotice;
import com.apiculture.simulator.presentation.common.TripCargoUi;
import com.apiculture.simulator.data.session.PlayerAuth;

import java.util.List;

/**
 * Elección en mapa (almacén / colmena), compra de almacén y estado de capacidad.
 */
public final class WarehouseDialogs {

    private WarehouseDialogs() {
    }

    public static void showBuildChoice(@NonNull Fragment fragment, @NonNull HiveViewModel viewModel,
            @NonNull String hexId, boolean alreadyHasWarehouse) {
        showBuildChoice(fragment, viewModel, hexId, alreadyHasWarehouse, Double.NaN, Double.NaN);
    }

    public static void showBuildChoice(@NonNull Fragment fragment, @NonNull HiveViewModel viewModel,
            @NonNull String hexId, boolean alreadyHasWarehouse, double tapLat, double tapLng) {
        if (!fragment.isAdded() || fragment.getContext() == null) {
            return;
        }
        DialogMapBuildChoiceBinding d = DialogMapBuildChoiceBinding.inflate(fragment.getLayoutInflater());
        Dialog dialog = creamDialog(fragment, d.getRoot());
        d.tvMapBuildChoiceTitle.setText(R.string.map_build_choice_title);
        d.btnChoiceHive.setText(R.string.map_build_choice_hive);
        d.btnChoiceWarehouse.setText(R.string.map_buy_warehouse);
        if (alreadyHasWarehouse) {
            d.btnChoiceWarehouse.setEnabled(false);
            d.btnChoiceWarehouse.setAlpha(0.45f);
        }
        d.btnChoiceWarehouse.setOnClickListener(v -> {
            dialog.dismiss();
            showBuy(fragment, viewModel, hexId, tapLat, tapLng);
        });
        d.btnChoiceHive.setOnClickListener(v -> {
            dialog.dismiss();
            BuyHiveDialogs.show(fragment, viewModel, hexId);
        });
        d.btnChoiceCancel.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    public static void showInstallChoice(@NonNull Fragment fragment,
            @NonNull Runnable onApiary, @NonNull Runnable onWarehouse) {
        showInstallChoice(fragment, onApiary, onWarehouse, null);
    }

    public static void showInstallChoice(@NonNull Fragment fragment,
            @NonNull Runnable onApiary, @NonNull Runnable onWarehouse, @Nullable Runnable onHeadquarters) {
        if (!fragment.isAdded() || fragment.getContext() == null) {
            return;
        }
        DialogMapBuildChoiceBinding d = DialogMapBuildChoiceBinding.inflate(fragment.getLayoutInflater());
        Dialog dialog = creamDialog(fragment, d.getRoot());
        d.tvMapBuildChoiceTitle.setText(R.string.map_install_choice_title);
        d.btnChoiceHive.setText(R.string.map_install_choice_apiary);
        d.btnChoiceWarehouse.setText(R.string.map_install_choice_warehouse);
        d.btnChoiceHive.setOnClickListener(v -> {
            // Capítulo 1, viñeta 7b. Pasa al diálogo Instalar apiario.
            com.apiculture.simulator.presentation.tutorial.TutorialBus.handoff(dialog);
            dialog.dismiss();
            onApiary.run();
        });
        d.btnChoiceWarehouse.setOnClickListener(v -> {
            // Capítulo 1, viñeta 16. Pasa a comprar el almacén.
            if (com.apiculture.simulator.presentation.tutorial.TutorialBus.wantsWarehouseChoice()) {
                com.apiculture.simulator.presentation.tutorial.TutorialBus.handoff(dialog);
            }
            dialog.dismiss();
            onWarehouse.run();
        });
        if (onHeadquarters != null) {
            d.btnChoiceHq.setVisibility(android.view.View.VISIBLE);
            d.btnChoiceHq.setText(R.string.map_install_choice_hq);
            d.btnChoiceHq.setOnClickListener(v -> {
                dialog.dismiss();
                onHeadquarters.run();
            });
        } else {
            d.btnChoiceHq.setVisibility(android.view.View.GONE);
        }
        if (com.apiculture.simulator.presentation.tutorial.TutorialBus.wantsWarehouseChoice()) {
            d.btnChoiceHive.setEnabled(false);
            d.btnChoiceHive.setAlpha(0.4f);
            d.btnChoiceCancel.setEnabled(false);
            d.btnChoiceCancel.setAlpha(0.4f);
            d.btnChoiceHq.setEnabled(false);
            d.btnChoiceHq.setAlpha(0.4f);
        }
        d.btnChoiceCancel.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
        // Capítulo 1, viñeta 7b o 16. Resalta apiario o almacén según el paso.
        boolean warehouseStep = com.apiculture.simulator.presentation.tutorial.TutorialBus.wantsWarehouseChoice();
        com.apiculture.simulator.presentation.tutorial.TutorialBus.emitDialog(
                com.apiculture.simulator.presentation.tutorial.TutorialEvent.INSTALL_CHOICE,
                dialog, warehouseStep ? d.btnChoiceWarehouse : d.btnChoiceHive);
    }

    public static void showNeedWarehouse(@NonNull Fragment fragment) {
        if (!fragment.isAdded() || fragment.getContext() == null) {
            return;
        }
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(fragment.requireContext())
                .setTitle(R.string.harvest_need_warehouse_title)
                .setMessage(R.string.harvest_need_warehouse_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.harvest_need_warehouse_go_map, (d, w) -> {
                    if (!fragment.isAdded()) {
                        return;
                    }
                    androidx.navigation.fragment.NavHostFragment.findNavController(fragment)
                            .navigate(R.id.mapFragment);
                })
                .show();
    }

    public static void showBuy(@NonNull Fragment fragment, @NonNull HiveViewModel viewModel,
            @NonNull String hexId) {
        showBuy(fragment, viewModel, hexId, Double.NaN, Double.NaN);
    }

    public static void showBuy(@NonNull Fragment fragment, @NonNull HiveViewModel viewModel,
            @NonNull String hexId, double tapLat, double tapLng) {
        if (!fragment.isAdded() || fragment.getContext() == null) {
            return;
        }
        String ownerId = PlayerAuth.getInstance().getUid();
        if (ownerId == null || ownerId.isEmpty()) {
            GameNotice.show(fragment.requireContext(), R.string.hive_buy_session_invalid);
            return;
        }
        DialogBuyWarehouseBinding d = DialogBuyWarehouseBinding.inflate(fragment.getLayoutInflater());
        Dialog dialog = creamDialog(fragment, d.getRoot());
        boolean guided = com.apiculture.simulator.presentation.tutorial.TutorialBus.wantsWarehouseChoice();
        d.btnConfirmWarehouse.setOnClickListener(v -> {
            String name = d.editWarehouseName.getText() == null
                    ? "" : d.editWarehouseName.getText().toString().trim();
            if (guided && name.length() < 2) {
                GameNotice.show(fragment.requireContext(), R.string.hex_purchase_name_required);
                return;
            }
            viewModel.buyWarehouse(ownerId, hexId, tapLat, tapLng, name, msg -> {
                if (!fragment.isAdded()) {
                    return;
                }
                if (msg == null) {
                    com.apiculture.simulator.presentation.tutorial.TutorialBus.handoff(dialog);
                    dialog.dismiss();
                    // Capítulo 1, viñeta 16. Almacén instalado.
                    com.apiculture.simulator.presentation.tutorial.TutorialBus.emit(
                            com.apiculture.simulator.presentation.tutorial.TutorialEvent.WAREHOUSE_BOUGHT);
                    GameNotice.showSuccess(fragment.requireContext(), R.string.map_buy_warehouse_ok);
                } else {
                    GameNotice.show(fragment.requireContext(), msg);
                }
            });
        });
        d.btnCancelWarehouse.setOnClickListener(v -> dialog.dismiss());
        if (guided) {
            dialog.setCancelable(false);
            dialog.setCanceledOnTouchOutside(false);
            d.btnCancelWarehouse.setVisibility(android.view.View.GONE);
        }
        dialog.show();
        // Capítulo 1, viñeta 16. El nombre del almacén es obligatorio.
        com.apiculture.simulator.presentation.tutorial.TutorialBus.emitDialog(
                com.apiculture.simulator.presentation.tutorial.TutorialEvent.PURCHASE_FORM,
                dialog, d.editWarehouseName);
        if (guided) {
            d.editWarehouseName.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                }

                @Override
                public void afterTextChanged(Editable s) {
                    String name = s == null ? "" : s.toString().trim();
                    if (name.length() < 2) {
                        return;
                    }
                    com.apiculture.simulator.presentation.tutorial.TutorialDialogCoach.focus(
                            dialog, d.btnConfirmWarehouse,
                            fragment.getString(R.string.tutorial_c1_v16_buy));
                }
            });
        }
    }

    public static void showStatus(@NonNull Fragment fragment, @NonNull HiveViewModel viewModel,
            @Nullable List<HexParcelOwnershipEntity> ownerships,
            @Nullable String ownerId, @Nullable String hexId) {
        showStatus(fragment, viewModel, ownerships, ownerId, hexId, null);
    }

    public static void showStatus(@NonNull Fragment fragment, @NonNull HiveViewModel viewModel,
            @Nullable List<HexParcelOwnershipEntity> ownerships,
            @Nullable String ownerId, @Nullable String hexId, @Nullable String siteId) {
        if (!fragment.isAdded() || fragment.getContext() == null) {
            return;
        }
        ApicultureApp app = (ApicultureApp) fragment.requireContext().getApplicationContext();
        HexParcelOwnershipEntity row = null;
        if (ownerships != null && hexId != null) {
            String want = siteId != null && !siteId.isEmpty() ? siteId : null;
            for (HexParcelOwnershipEntity o : ownerships) {
                if (o == null || !hexId.equals(o.hexId) || !o.hasWarehouse) {
                    continue;
                }
                if (ownerId != null && !ownerId.equals(o.ownerId)) {
                    continue;
                }
                String got = o.siteId != null && !o.siteId.isEmpty() ? o.siteId : "default";
                if (want == null || want.equals(got)) {
                    row = o;
                    break;
                }
            }
            if (row == null) {
                for (HexParcelOwnershipEntity o : ownerships) {
                    if (o != null && hexId.equals(o.hexId) && o.hasWarehouse
                            && (ownerId == null || ownerId.equals(o.ownerId))) {
                        row = o;
                        break;
                    }
                }
            }
        }
        int level = WarehouseRules.levelOf(row);
        double cap = WarehouseRules.capacityKg(level);
        WarehouseHoneyStore.reconcile(fragment.requireContext(), app.getEconomyRepository(),
                ownerId, ownerships);
        double here = WarehouseHoneyStore.totalAt(fragment.requireContext(), ownerId, hexId);
        DialogWarehouseStatusBinding d = DialogWarehouseStatusBinding.inflate(fragment.getLayoutInflater());
        Dialog dialog = creamDialog(fragment, d.getRoot());
        String warehouseName = row != null && row.parcelName != null && !row.parcelName.trim().isEmpty()
                ? row.parcelName.trim()
                : fragment.getString(R.string.map_warehouse_title);
        d.tvWarehouseTitle.setText(warehouseName);
        d.tvWarehouseLevel.setText(fragment.getString(R.string.map_warehouse_level, level));
        d.tvWarehouseKg.setText(fragment.getString(R.string.map_warehouse_kg, here, cap));
        java.util.Map<String, Double> jars = WarehouseHoneyStore.at(fragment.requireContext(), ownerId, hexId);
        TripCargoUi.bind(fragment.getLayoutInflater(), d.llWarehouseHoney, jars, fragment.requireContext());
        int jarsVisible = d.llWarehouseHoney.getVisibility();
        d.hsWarehouseHoney.setVisibility(jarsVisible);
        // Miel a granel (bidones) y, aparte, la envasada en tarros de cada tamaño.
        StringBuilder packed = new StringBuilder();
        if (ownerId != null && hexId != null) {
            for (com.apiculture.simulator.domain.workshop.WorkshopState.Packed p
                    : com.apiculture.simulator.data.repository.WorkshopStore.get(fragment.requireContext(), ownerId, hexId).packed) {
                if (p.jars <= 0) {
                    continue;
                }
                if (packed.length() > 0) {
                    packed.append('\n');
                }
                packed.append(fragment.getString(R.string.map_warehouse_packed_line,
                        HiveSiteSummaryUi.floraLabel(fragment.requireContext(), p.flora), p.jars,
                        com.apiculture.simulator.presentation.market.WorkshopFormatUi.label(fragment.requireContext(), p.format)));
            }
        }
        boolean hasBulk = jarsVisible == android.view.View.VISIBLE;
        boolean hasJars = packed.length() > 0;
        d.tvWarehouseJars.setText(hasBulk ? R.string.map_warehouse_bulk
                : hasJars ? R.string.map_warehouse_no_bulk : R.string.map_warehouse_empty);
        d.tvWarehousePackedTitle.setVisibility(hasJars ? android.view.View.VISIBLE : android.view.View.GONE);
        d.tvWarehousePacked.setVisibility(hasJars ? android.view.View.VISIBLE : android.view.View.GONE);
        d.tvWarehousePacked.setText(packed.toString());
        int slots = FleetRules.truckSlots(level);
        int used = 0;
        for (FleetStore.Vehicle vehicle : FleetStore.vehicles(fragment.requireContext(), ownerId)) {
            if (vehicle != null && vehicle.isTruck() && hexId != null && hexId.equals(vehicle.homeId)) {
                used++;
            }
        }
        int freeSlots = Math.max(0, slots - used);
        d.tvWarehouseSlots.setVisibility(freeSlots > 0 ? android.view.View.VISIBLE : android.view.View.GONE);
        if (freeSlots > 0) {
            d.tvWarehouseSlots.setText(fragment.getString(R.string.map_warehouse_truck_slots,
                    freeSlots, slots));
        }
        FleetDialogs.fillVehicleRows(fragment, d.llWarehouseFleet, ownerId, hexId, true);
        Context fleetContext = fragment.requireContext().getApplicationContext();
        new Thread(() -> {
            FleetStore.releaseIdle(fleetContext, ownerId);
            if (fragment.getActivity() == null) {
                return;
            }
            fragment.requireActivity().runOnUiThread(() -> {
                if (!fragment.isAdded()) {
                    return;
                }
                FleetDialogs.fillVehicleRows(fragment, d.llWarehouseFleet, ownerId, hexId, true);
            });
        }, "warehouse-fleet").start();
        d.btnWarehouseTransfer.setOnClickListener(v -> {
            dialog.dismiss();
            FleetDialogs.showTransfer(fragment, ownerId, hexId);
        });
        // Botón principal: entrar al obrador en 3D. Ampliarlo se hace con Ramón, en el patio.
        d.btnWarehouseWorkshop.setVisibility(hexId != null && ownerId != null ? android.view.View.VISIBLE : android.view.View.GONE);
        d.btnWarehouseWorkshop.setOnClickListener(v -> {
            dialog.dismiss();
            if (hexId == null || ownerId == null) {
                return;
            }
            com.apiculture.simulator.unity.UnityBridge.prepareWorkshop(fragment.requireContext(), ownerId,
                    warehouseName, hexId);
            com.apiculture.simulator.unity.Apiary3DActivity.open(fragment.requireContext());
        });
        d.barWarehouseKg.setMax(1000);
        d.barWarehouseKg.setProgress(cap <= 1e-9 ? 0 : (int) Math.round(1000.0 * Math.min(1.0, here / cap)));
        String sellSite = row != null && row.siteId != null ? row.siteId : siteId;
        d.btnWarehouseSell.setOnClickListener(v -> {
            dialog.dismiss();
            SiteSellDialogs.sellWarehouse(fragment, viewModel, ownerships, ownerId, hexId, sellSite);
        });
        d.btnWarehouseClose.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    /** Toque en el obrador del mapa: pregunta si se entra a la escena 3D o se ven los detalles. */
    public static void showEnter(@NonNull Fragment fragment, @NonNull HiveViewModel viewModel,
            @Nullable List<HexParcelOwnershipEntity> ownerships,
            @Nullable String ownerId, @NonNull String hexId, @Nullable String siteId) {
        if (!fragment.isAdded() || fragment.getContext() == null) {
            return;
        }
        String name = null;
        if (ownerships != null) {
            for (HexParcelOwnershipEntity o : ownerships) {
                if (o != null && hexId.equals(o.hexId) && o.hasWarehouse
                        && (ownerId == null || ownerId.equals(o.ownerId))
                        && o.parcelName != null && !o.parcelName.trim().isEmpty()) {
                    name = o.parcelName.trim();
                    break;
                }
            }
        }
        String shown = name != null ? name : fragment.getString(R.string.workshop_name);
        DialogMapBuildChoiceBinding d = DialogMapBuildChoiceBinding.inflate(fragment.getLayoutInflater());
        Dialog dialog = creamDialog(fragment, d.getRoot());
        d.tvMapBuildChoiceTitle.setText(fragment.getString(R.string.map_obrador_enter_title, shown));
        d.btnChoiceHive.setText(R.string.map_obrador_enter);
        d.btnChoiceWarehouse.setText(R.string.map_obrador_details);
        d.btnChoiceHive.setOnClickListener(v -> {
            dialog.dismiss();
            if (ownerId == null || ownerId.isEmpty()) {
                return;
            }
            com.apiculture.simulator.unity.UnityBridge.prepareWorkshop(fragment.requireContext(),
                    ownerId, shown, hexId);
            com.apiculture.simulator.unity.Apiary3DActivity.open(fragment.requireContext());
        });
        d.btnChoiceWarehouse.setOnClickListener(v -> {
            dialog.dismiss();
            showStatus(fragment, viewModel, ownerships, ownerId, hexId, siteId);
        });
        d.btnChoiceCancel.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    @NonNull
    private static Dialog creamDialog(@NonNull Fragment fragment, @NonNull android.view.View root) {
        Dialog dialog = new Dialog(fragment.requireContext());
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
}
