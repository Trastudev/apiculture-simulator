package com.apiculture.simulator.presentation.hive;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.apiculture.simulator.ApicultureApp;
import com.apiculture.simulator.R;
import com.apiculture.simulator.data.local.entity.HiveEntity;
import com.apiculture.simulator.data.repository.HiveRepository;
import com.apiculture.simulator.data.repository.IberiaHexOverlayStore;
import com.apiculture.simulator.databinding.DialogBuyHiveBinding;
import com.apiculture.simulator.domain.game.GameCalendar;
import com.apiculture.simulator.domain.game.HexNectarRules;
import com.apiculture.simulator.presentation.common.FloraSaturationBar;
import com.apiculture.simulator.domain.game.HiveCareRules;
import com.apiculture.simulator.domain.parcel.HexParcel;
import com.apiculture.simulator.domain.parcel.HexParcelGameRules;
import com.apiculture.simulator.domain.game.FleetRules;
import com.apiculture.simulator.domain.game.TranshumanceRules;
import com.apiculture.simulator.data.repository.FleetStore;
import com.apiculture.simulator.data.local.entity.HexParcelOwnershipEntity;
import com.apiculture.simulator.presentation.common.GameNotice;
import com.apiculture.simulator.data.session.PlayerAuth;

import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Diálogo de compra de colmena compartido por apiarios, prado y pulsación larga en el mapa.
 * Al elegir flora abre un panel con bote y gráfico anual de floración.
 */
public final class BuyHiveDialogs {

    private BuyHiveDialogs() {
    }

    public static void show(Fragment fragment, HiveViewModel viewModel, @Nullable String preselectHexId) {
        show(fragment, viewModel, preselectHexId, null);
    }

    public static void show(Fragment fragment, HiveViewModel viewModel, @Nullable String preselectHexId,
            @Nullable String siteId) {
        if (fragment == null || !fragment.isAdded() || fragment.getContext() == null) {
            return;
        }
        String ownerId = PlayerAuth.getInstance().getUid();
        if (ownerId == null || ownerId.isEmpty()) {
            GameNotice.show(fragment.requireContext(), R.string.hive_buy_session_invalid);
            return;
        }
        viewModel.listOwnedTerrainsForHivePurchase(ownerId, options -> {
            if (!fragment.isAdded()) {
                return;
            }
            List<HiveRepository.OwnedHexOption> opts =
                    options != null ? options : Collections.emptyList();
            if (opts.isEmpty()) {
                GameNotice.show(fragment.requireContext(), R.string.hive_buy_no_hexes);
                return;
            }
            if (preselectHexId != null && !preselectHexId.isEmpty()) {
                boolean owned = false;
                for (HiveRepository.OwnedHexOption o : opts) {
                    if (preselectHexId.equals(o.hexId)) {
                        owned = true;
                        break;
                    }
                }
                if (!owned) {
                    GameNotice.show(fragment.requireContext(), R.string.hive_buy_need_own_hex);
                    return;
                }
            }
            showForm(fragment, viewModel, ownerId, preselectHexId, opts, null, siteId);
        });
    }

    public static void showSplit(Fragment fragment, HiveViewModel viewModel, @Nullable HiveEntity parent) {
        if (fragment == null || !fragment.isAdded() || fragment.getContext() == null) {
            return;
        }
        if (parent == null || parent.id == null || parent.id.isEmpty()) {
            GameNotice.show(fragment.requireContext(), R.string.hive_buy_session_invalid);
            return;
        }
        String ownerId = PlayerAuth.getInstance().getUid();
        if (ownerId == null || ownerId.isEmpty() || !ownerId.equals(parent.ownerId)) {
            GameNotice.show(fragment.requireContext(), R.string.hive_buy_session_invalid);
            return;
        }
        if (parent.hexId == null || parent.hexId.isEmpty()) {
            GameNotice.show(fragment.requireContext(), R.string.hive_split_need_hex);
            return;
        }
        String parentHexId = parent.hexId;
        viewModel.listOwnedTerrainsForHivePurchase(ownerId, options -> {
            if (!fragment.isAdded()) {
                return;
            }
            List<HiveRepository.OwnedHexOption> opts =
                    options != null ? options : Collections.emptyList();
            HiveRepository.OwnedHexOption mine = null;
            for (HiveRepository.OwnedHexOption o : opts) {
                if (parentHexId.equals(o.hexId)) {
                    mine = o;
                    break;
                }
            }
            if (mine == null) {
                GameNotice.show(fragment.requireContext(), R.string.hive_buy_need_own_hex);
                return;
            }
            showForm(fragment, viewModel, ownerId, parentHexId,
                    Collections.singletonList(mine), parent, null);
        });
    }

    private static void offerAnotherApiary(
            Fragment fragment, HiveViewModel viewModel, HiveEntity parent,
            String flora, String name, java.util.function.Consumer<String> onBought) {
        viewModel.listSplitDestinations(parent, dests -> {
            if (!fragment.isAdded()) {
                return;
            }
            if (dests == null || dests.isEmpty()) {
                GameNotice.show(fragment.requireContext(), R.string.hive_split_no_room);
                return;
            }
            showChoice(fragment, fragment.getString(R.string.hive_split_full_title),
                    fragment.getString(R.string.hive_split_full_message),
                    dests, d -> fragment.getString(R.string.hive_split_dest_row, d.label, d.free, d.km),
                    dest -> showTrucks(fragment, viewModel, parent, flora, name, dest, onBought));
        });
    }

    private static void showTrucks(
            Fragment fragment, HiveViewModel viewModel, HiveEntity parent,
            String flora, String name, HiveRepository.SplitDest dest,
            java.util.function.Consumer<String> onBought) {
        Context ctx = fragment.requireContext();
        List<FleetStore.Vehicle> trucks = new ArrayList<>();
        for (FleetStore.Vehicle v : FleetStore.vehicles(ctx, parent.ownerId)) {
            if (v == null || !v.isTruck() || v.honeyBusy()) {
                continue;
            }
            int slots = FleetRules.hiveSlots(FleetRules.Kind.TRUCK, v.level);
            if (v.hiveTrips >= slots) {
                continue;
            }
            trucks.add(v);
        }
        trucks.sort((a, b) -> Double.compare(truckKm(ctx, parent, a), truckKm(ctx, parent, b)));
        if (trucks.isEmpty()) {
            GameNotice.show(ctx, R.string.hive_split_no_truck);
            return;
        }
        showChoice(fragment, fragment.getString(R.string.hive_split_truck_title),
                fragment.getString(R.string.hive_split_truck_message, dest.label),
                trucks,
                v -> {
                    int free = FleetRules.hiveSlots(FleetRules.Kind.TRUCK, v.level) - v.hiveTrips;
                    String label = v.name != null && !v.name.isEmpty() ? v.name : v.id;
                    return fragment.getString(R.string.hive_split_truck_row, label, free,
                            truckKm(ctx, parent, v));
                },
                truck -> GameNotice.confirm(ctx,
                        fragment.getString(R.string.hive_split_send_title),
                        fragment.getString(R.string.hive_split_send_message, dest.label,
                                truck.name != null && !truck.name.isEmpty() ? truck.name : truck.id),
                        android.R.string.ok,
                        android.R.string.cancel,
                        () -> viewModel.splitAndSend(parent, flora, name, dest.hexId, dest.siteId,
                                truck.id, msg -> onBought.accept(msg == null ? "SENT" : msg)),
                        null));
    }

    private static double truckKm(Context ctx, HiveEntity parent, FleetStore.Vehicle truck) {
        HexParcel home = IberiaHexOverlayStore.findById(ctx, truck.homeId);
        if (home == null) {
            return 0;
        }
        return TranshumanceRules.haversineKm(parent.lat, parent.lng, home.centroidLat, home.centroidLon);
    }

    private static <T> void showChoice(
            Fragment fragment, String title, String message, List<T> items,
            java.util.function.Function<T, String> label, java.util.function.Consumer<T> onPick) {
        Dialog dialog = new Dialog(fragment.requireContext());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        android.widget.LinearLayout box = new android.widget.LinearLayout(fragment.requireContext());
        box.setOrientation(android.widget.LinearLayout.VERTICAL);
        int pad = (int) (18f * fragment.getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad, pad, pad);
        box.setBackgroundResource(R.drawable.bg_event_global_body);
        TextView heading = new TextView(fragment.requireContext());
        heading.setText(title);
        heading.setTextColor(fragment.getResources().getColor(R.color.event_ink));
        heading.setTextSize(18f);
        heading.setGravity(android.view.Gravity.CENTER);
        box.addView(heading);
        TextView body = new TextView(fragment.requireContext());
        body.setText(message);
        body.setTextColor(fragment.getResources().getColor(R.color.event_ink_muted));
        body.setPadding(0, pad / 2, 0, pad / 2);
        box.addView(body);
        android.widget.ScrollView scroll = new android.widget.ScrollView(fragment.requireContext());
        android.widget.LinearLayout list = new android.widget.LinearLayout(fragment.requireContext());
        list.setOrientation(android.widget.LinearLayout.VERTICAL);
        for (T item : items) {
            com.google.android.material.button.MaterialButton btn =
                    new com.google.android.material.button.MaterialButton(fragment.requireContext());
            btn.setText(label.apply(item));
            btn.setAllCaps(false);
            btn.setOnClickListener(v -> {
                dialog.dismiss();
                onPick.accept(item);
            });
            list.addView(btn);
        }
        scroll.addView(list);
        box.addView(scroll, new android.widget.LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (int) (280f * fragment.getResources().getDisplayMetrics().density)));
        com.google.android.material.button.MaterialButton cancel =
                new com.google.android.material.button.MaterialButton(fragment.requireContext(), null,
                        com.google.android.material.R.attr.borderlessButtonStyle);
        cancel.setText(android.R.string.cancel);
        cancel.setOnClickListener(v -> dialog.dismiss());
        box.addView(cancel);
        dialog.setContentView(box);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        dialog.show();
    }

    private static void showForm(
            Fragment fragment,
            HiveViewModel viewModel,
            String ownerId,
            @Nullable String preselectHexId,
            List<HiveRepository.OwnedHexOption> hexOptions,
            @Nullable HiveEntity splitFrom,
            @Nullable String siteId) {
        DialogBuyHiveBinding d = DialogBuyHiveBinding.inflate(fragment.getLayoutInflater());
        boolean split = splitFrom != null;
        boolean lockHex = (preselectHexId != null && !preselectHexId.isEmpty()) || split;

        if (split) {
            d.tvHiveBuyTitle.setText(R.string.hive_split_title);
            d.tvHiveBuyKicker.setText(R.string.hive_split_kicker);
            d.tvHiveBuyIntro.setText(R.string.hive_split_intro);
            d.rbSuper0.setText(R.string.hive_split_super_0);
            d.rbSuper1.setText(R.string.hive_split_super_1);
            d.rbSuper2.setText(R.string.hive_split_super_2);
            d.btnHiveBuyConfirm.setText(R.string.hive_split_confirm);
            d.editHiveName.setHint(R.string.hive_split_name_hint);
            String base = splitFrom.name != null ? splitFrom.name.trim() : "";
            if (!base.isEmpty()) {
                d.editHiveName.setText(base + " · II");
            }
        }

        Dialog dialog = new Dialog(fragment.requireContext());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(d.getRoot());
        dialog.setCancelable(true);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }

        int spinRow = R.layout.item_dialog_buy_spinner;
        List<String> hexLabels = new ArrayList<>();
        int preselectedIndex = 0;
        for (int i = 0; i < hexOptions.size(); i++) {
            HiveRepository.OwnedHexOption o = hexOptions.get(i);
            hexLabels.add(o.label);
            if (lockHex && preselectHexId.equals(o.hexId)) {
                preselectedIndex = i;
            }
        }
        final int lockedHexIndex = preselectedIndex;
        ArrayAdapter<String> hexAd = new ArrayAdapter<>(fragment.requireContext(), spinRow, hexLabels);
        hexAd.setDropDownViewResource(spinRow);
        d.spinnerHex.setAdapter(hexAd);
        d.spinnerHex.setSelection(lockedHexIndex);

        d.tvHiveBuyHexLabel.setVisibility(View.GONE);
        d.spinnerHex.setVisibility(View.GONE);
        d.tvHiveBuyHexValue.setVisibility(View.GONE);
        d.tvHiveBuySupersLabel.setVisibility(View.GONE);
        d.rgSupers.setVisibility(View.GONE);

        final boolean[] userChoseFlora = {false};

        Runnable refreshFloraForSelectedHex = () -> {
            if (!fragment.isAdded()) {
                return;
            }
            userChoseFlora[0] = false;
            int hexIdx = lockedHexIndex;
            if (hexIdx < 0 || hexIdx >= hexOptions.size()) {
                bindFloraSpinner(fragment, d, Collections.emptyList());
                return;
            }
            String hexId = hexOptions.get(hexIdx).hexId;
            viewModel.listReadyFlorasForHex(hexId, siteId, floras -> {
                if (!fragment.isAdded()) {
                    return;
                }
                bindFloraSpinner(fragment, d, floras != null ? floras : Collections.emptyList());
                refreshSaturation(fragment, viewModel, d, hexOptions, lockHex, lockedHexIndex);
            });
        };

        d.spinnerHex.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                refreshFloraForSelectedHex.run();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        d.spinnerFlora.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                userChoseFlora[0] = true;
            }
            return false;
        });
        d.spinnerFlora.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (!userChoseFlora[0]) {
                    return;
                }
                userChoseFlora[0] = false;
                Object floraSel = d.spinnerFlora.getSelectedItem();
                if (!(floraSel instanceof String) || ((String) floraSel).isEmpty()) {
                    return;
                }
                int hexIdx = lockedHexIndex;
                if (hexIdx < 0 || hexIdx >= hexOptions.size()) {
                    return;
                }
                showFloraInfoDialog(fragment, hexOptions.get(hexIdx).hexId, (String) floraSel);
                refreshSaturation(fragment, viewModel, d, hexOptions, lockHex, lockedHexIndex);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        int euros = split
                ? HiveCareRules.emptyNucPriceEuros(0)
                : HiveViewModel.hivePurchasePriceEuros(0);
        d.tvHiveBuyPrice.setText(fragment.getString(R.string.hex_purchase_price_chip, (double) euros));

        d.btnBuyWarehouse.setVisibility(View.GONE);

        d.btnHiveBuyCancel.setOnClickListener(v -> dialog.dismiss());
        d.btnHiveBuyConfirm.setOnClickListener(v -> {
            String name = d.editHiveName.getText() != null ? d.editHiveName.getText().toString().trim() : "";
            if (name.isEmpty()) {
                GameNotice.show(fragment.requireContext(), R.string.hive_buy_name_required);
                return;
            }
            int hexIdx = lockedHexIndex;
            if (hexIdx < 0 || hexIdx >= hexOptions.size()) {
                GameNotice.show(fragment.requireContext(), R.string.hive_buy_need_hex);
                return;
            }
            String hexId = hexOptions.get(hexIdx).hexId;
            Object floraSel = d.spinnerFlora.getSelectedItem();
            if (!(floraSel instanceof String) || ((String) floraSel).isEmpty()) {
                GameNotice.show(fragment.requireContext(), R.string.hive_buy_no_flora_ready);
                return;
            }
            String flora = (String) floraSel;
            Consumer<String> onBought = msg -> {
                if (!fragment.isAdded()) {
                    return;
                }
                if (msg == null || "SENT".equals(msg)) {
                    if (!split) {
                        // Capítulo 1, viñeta 10b. Colmena comprada y colocada.
                        com.apiculture.simulator.presentation.tutorial.TutorialBus.handoff(dialog);
                        com.apiculture.simulator.presentation.tutorial.TutorialBus.emit(
                                com.apiculture.simulator.presentation.tutorial.TutorialEvent.HIVE_BOUGHT);
                    }
                    dialog.dismiss();
                    if ("SENT".equals(msg)) {
                        GameNotice.showSuccess(fragment.requireContext(), R.string.hive_split_sent);
                    } else if (split) {
                        GameNotice.showSuccess(fragment.requireContext(), R.string.hive_split_ok);
                    } else {
                        GameNotice.show(fragment.requireContext(), R.string.hive_created_ok);
                    }
                } else {
                    GameNotice.show(fragment.requireContext(), msg);
                }
            };
            if (split) {
                ApicultureApp app = (ApicultureApp) fragment.requireActivity().getApplication();
                new Thread(() -> {
                    int n = app.getHexParcelRepository().countHivesAtSiteBlocking(
                            ownerId, splitFrom.hexId, splitFrom.siteId);
                    fragment.requireActivity().runOnUiThread(() -> {
                        if (!fragment.isAdded()) {
                            return;
                        }
                        if (n >= HexParcelGameRules.MAX_HIVES_PER_SITE) {
                            offerAnotherApiary(fragment, viewModel, splitFrom, flora, name, onBought);
                        } else {
                            viewModel.splitHiveIntoEmptyNuc(splitFrom, hexId, flora, name, 0, onBought);
                        }
                    });
                }, "split-room").start();
            } else {
                viewModel.purchaseHive(ownerId, hexId, flora, name, 0, siteId, onBought);
            }
        });

        dialog.show();
        // Capítulo 1, viñeta 10b. Elegir la flor del néctar.
        com.apiculture.simulator.presentation.tutorial.TutorialBus.emitDialog(
                com.apiculture.simulator.presentation.tutorial.TutorialEvent.BUY_HIVE_DIALOG,
                dialog, null);
        refreshFloraForSelectedHex.run();
        d.editHiveName.post(d.editHiveName::requestFocus);
    }

    private static void showFloraInfoDialog(
            @NonNull Fragment fragment,
            @Nullable String hexId,
            @NonNull String floraKey) {
        if (!fragment.isAdded() || fragment.getContext() == null) {
            return;
        }
        Context ctx = fragment.requireContext();
        View root = LayoutInflater.from(ctx).inflate(R.layout.dialog_buy_flora_info, null, false);
        TextView title = root.findViewById(R.id.tv_flora_info_title);
        ImageView jar = root.findViewById(R.id.iv_flora_info_jar);
        TextView chartTitle = root.findViewById(R.id.tv_flora_info_chart_title);
        TextView chartAvg = root.findViewById(R.id.tv_flora_info_chart_avg);
        FloraMonthLineChartView chart = root.findViewById(R.id.flora_info_month_chart);

        title.setText(floraKey);
        int icon = HiveSiteSummaryUi.floraHoneyJarIcon(floraKey);
        jar.setImageResource(icon != 0 ? icon : R.drawable.flora_photo_unknown);

        LocalDate today = LocalDate.now(GameCalendar.userTimeZone());
        LocalDate start = today.withDayOfYear(1);
        int days = start.lengthOfYear();
        HiveEntity probe = probeHiveForFlora(ctx, hexId, floraKey);
        double[] nectars = new double[days];
        double sum = 0.0;
        double peak = 0.0;
        for (int i = 0; i < days; i++) {
            double n = Math.max(0.0, HexNectarRules.nectar01(probe, start.plusDays(i), 1, null));
            nectars[i] = Math.min(1.0, n);
            sum += nectars[i];
            if (nectars[i] > peak) {
                peak = nectars[i];
            }
        }
        chartTitle.setText(ctx.getString(R.string.hive_chart_flora_year_title, start.getYear()));
        NumberFormat pctNf = NumberFormat.getPercentInstance(new Locale("es", "ES"));
        pctNf.setMaximumFractionDigits(0);
        chartAvg.setText(ctx.getString(R.string.hive_chart_flora_month_avg,
                floraKey,
                pctNf.format(days > 0 ? sum / days : 0.0),
                pctNf.format(peak)));
        chart.setSeries(nectars, today.getDayOfYear() - 1);

        Dialog info = new Dialog(ctx);
        info.requestWindowFeature(Window.FEATURE_NO_TITLE);
        info.setContentView(root);
        info.setCancelable(true);
        Window w = info.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        root.findViewById(R.id.btn_flora_info_ok).setOnClickListener(v -> info.dismiss());
        info.show();
        // Capítulo 1, viñeta 10b. Gráfico de mieladas y disponibilidad en el año.
        com.apiculture.simulator.presentation.tutorial.TutorialBus.emitDialog(
                com.apiculture.simulator.presentation.tutorial.TutorialEvent.FLORA_CHART,
                info, null);
    }

    @NonNull
    private static HiveEntity probeHiveForFlora(
            @NonNull Context ctx,
            @Nullable String hexId,
            @NonNull String floraKey) {
        HiveEntity h = new HiveEntity();
        h.id = "probe";
        h.floraType = floraKey;
        h.hexId = hexId != null ? hexId : "";
        h.elevationMeters = 400;
        HexParcel parcel = IberiaHexOverlayStore.findById(ctx.getApplicationContext(), hexId);
        if (parcel != null) {
            h.lat = parcel.centroidLat;
            h.lng = parcel.centroidLon;
            if (parcel.maxElevationMeters != null && parcel.maxElevationMeters >= 0) {
                h.elevationMeters = parcel.maxElevationMeters;
            }
        }
        return h;
    }

    private static void refreshSaturation(
            @NonNull Fragment fragment,
            @NonNull HiveViewModel viewModel,
            @NonNull DialogBuyHiveBinding d,
            @NonNull List<HiveRepository.OwnedHexOption> hexOptions,
            boolean lockHex,
            int lockedHexIndex) {
        int hexIdx = lockedHexIndex;
        if (hexIdx < 0 || hexIdx >= hexOptions.size()) {
            FloraSaturationBar.bind(d.getRoot(), null);
            return;
        }
        Object floraSel = d.spinnerFlora.getSelectedItem();
        String flora = floraSel instanceof String ? (String) floraSel : null;
        viewModel.floraSaturation(hexOptions.get(hexIdx).hexId, flora, sat -> {
            if (!fragment.isAdded()) {
                return;
            }
            FloraSaturationBar.bind(d.getRoot(), sat);
        });
    }

    private static void bindFloraSpinner(
            Fragment fragment,
            DialogBuyHiveBinding d,
            List<String> floras) {
        boolean empty = floras == null || floras.isEmpty();
        d.tvHiveBuyFloraEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        d.spinnerFlora.setVisibility(empty ? View.GONE : View.VISIBLE);
        d.spinnerFlora.setEnabled(!empty);
        d.btnHiveBuyConfirm.setEnabled(!empty);
        d.btnHiveBuyConfirm.setAlpha(empty ? 0.45f : 1f);
        if (empty) {
            return;
        }
        d.spinnerFlora.setAdapter(new FloraRowAdapter(fragment.requireContext(), floras));
    }

    private static final class FloraRowAdapter extends ArrayAdapter<String> {

        FloraRowAdapter(@NonNull Context context, @NonNull List<String> items) {
            super(context, R.layout.item_dialog_buy_flora, items);
        }

        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            return bind(position, convertView, parent);
        }

        @Override
        public View getDropDownView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            return bind(position, convertView, parent);
        }

        private View bind(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            View row = convertView;
            if (row == null) {
                row = LayoutInflater.from(getContext()).inflate(R.layout.item_dialog_buy_flora, parent, false);
            }
            String key = getItem(position);
            ImageView icon = row.findViewById(R.id.iv_dialog_buy_flora);
            TextView label = row.findViewById(R.id.tv_dialog_buy_flora);
            if (icon != null) {
                icon.setImageResource(HiveSiteSummaryUi.floraHoneyJarIcon(key));
            }
            if (label != null) {
                label.setText(key != null ? key : "");
            }
            return row;
        }
    }
}
