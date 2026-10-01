package com.apiculture.simulator.presentation.hive;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.apiculture.simulator.ApicultureApp;
import com.apiculture.simulator.R;
import com.apiculture.simulator.data.local.entity.HexParcelOwnershipEntity;
import com.apiculture.simulator.data.local.entity.HiveEntity;
import com.apiculture.simulator.data.repository.IberiaHexOverlayStore;
import com.apiculture.simulator.domain.game.HexFloraSaturation;
import com.apiculture.simulator.domain.game.TranshumanceRules;
import com.apiculture.simulator.domain.parcel.HexFlora;
import com.apiculture.simulator.domain.parcel.HexParcel;
import com.apiculture.simulator.domain.parcel.WarehouseRules;
import com.apiculture.simulator.presentation.common.FloraSaturationBar;
import com.apiculture.simulator.presentation.common.GameNotice;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class TranshumanceDialogs {

    private static final ExecutorService IO = Executors.newSingleThreadExecutor();

    private TranshumanceDialogs() {
    }

    public static void show(@NonNull Fragment fragment, @NonNull HiveViewModel viewModel,
            @NonNull String originName, double originLat, double originLng,
            @NonNull List<HiveEntity> hives, @NonNull List<HexParcelOwnershipEntity> destinations) {
        if (!fragment.isAdded()) {
            return;
        }
        List<HiveEntity> movable = new ArrayList<>();
        for (HiveEntity hive : hives) {
            if (canMove(hive)) {
                movable.add(hive);
            }
        }
        if (movable.isEmpty()) {
            GameNotice.show(fragment.requireContext(), R.string.transhumance_no_free);
            return;
        }
        List<HexParcelOwnershipEntity> dests = new ArrayList<>();
        for (HexParcelOwnershipEntity row : destinations) {
            if (row != null && WarehouseRules.isApiarySite(row) && row.hexId != null) {
                dests.add(row);
            }
        }
        if (dests.isEmpty()) {
            GameNotice.show(fragment.requireContext(), R.string.transhumance_none_dest);
            return;
        }
        View root = fragment.getLayoutInflater().inflate(R.layout.dialog_transhumance, null);
        TextView from = root.findViewById(R.id.tv_move_from);
        LinearLayout hiveBox = root.findViewById(R.id.ll_move_hives);
        LinearLayout destBox = root.findViewById(R.id.ll_move_dest);
        LinearLayout satBox = root.findViewById(R.id.ll_move_saturation);
        TextView quote = root.findViewById(R.id.tv_move_quote);
        MaterialButton send = root.findViewById(R.id.btn_move_send);
        MaterialButton close = root.findViewById(R.id.btn_move_close);
        from.setText(fragment.getString(R.string.transhumance_from, originName));
        final int[] selectedDest = {-1};
        final String[] selectedFlora = {null};
        Set<String> picked = new LinkedHashSet<>();
        float density = fragment.getResources().getDisplayMetrics().density;
        int hiveSize = Math.round(40f * density);
        for (HiveEntity hive : movable) {
            LinearLayout row = new LinearLayout(fragment.requireContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            ImageView icon = new ImageView(fragment.requireContext());
            icon.setImageResource(R.drawable.ic_compracolmena);
            icon.setLayoutParams(new LinearLayout.LayoutParams(hiveSize, hiveSize));
            CheckBox box = new CheckBox(fragment.requireContext());
            String name = hive.name != null && !hive.name.trim().isEmpty()
                    ? hive.name.trim()
                    : fragment.getString(R.string.hive_unnamed);
            box.setText(name);
            box.setTextColor(ContextCompat.getColor(fragment.requireContext(), R.color.event_ink));
            box.setClickable(false);
            box.setFocusable(false);
            box.setOnCheckedChangeListener((button, checked) -> {
                if (checked) {
                    picked.add(hive.id);
                } else {
                    picked.remove(hive.id);
                }
                refreshQuote(fragment, quote, movable, picked, dests, selectedDest[0]);
            });
            row.setOnClickListener(v -> box.toggle());
            row.addView(icon);
            row.addView(box);
            hiveBox.addView(row);
        }
        Dialog dialog = cream(fragment, root);
        View[] destRows = new View[dests.size()];
        int mapSize = Math.round(36f * density);
        for (int i = 0; i < dests.size(); i++) {
            HexParcelOwnershipEntity site = dests.get(i);
            double[] ll = pin(fragment, site);
            double km = TranshumanceRules.haversineKm(originLat, originLng, ll[0], ll[1]);
            LinearLayout row = new LinearLayout(fragment.requireContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setPadding(12, 8, 8, 8);
            ImageView apiary = new ImageView(fragment.requireContext());
            int apiarySize = Math.round(48f * density);
            apiary.setImageResource(R.drawable.ic_apiario_otro);
            apiary.setScaleType(ImageView.ScaleType.FIT_CENTER);
            apiary.setContentDescription(fragment.getString(R.string.transhumance_dest));
            LinearLayout.LayoutParams apiaryLp = new LinearLayout.LayoutParams(apiarySize, apiarySize);
            apiaryLp.setMarginEnd(Math.round(10f * density));
            apiary.setLayoutParams(apiaryLp);
            TextView label = new TextView(fragment.requireContext());
            label.setLayoutParams(new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            label.setText(fragment.getString(R.string.transhumance_dest_km,
                    ApiariesFragment.terrainDisplayTitle(site), km));
            label.setTextColor(0xFF3E2723);
            label.setTextSize(15f);
            ImageView map = new ImageView(fragment.requireContext());
            map.setImageResource(R.drawable.ic_map_pin);
            map.setContentDescription(fragment.getString(R.string.transhumance_map));
            LinearLayout.LayoutParams mapLp = new LinearLayout.LayoutParams(mapSize, mapSize);
            mapLp.setMarginStart(Math.round(8f * density));
            map.setLayoutParams(mapLp);
            map.setOnClickListener(v -> {
                dialog.dismiss();
                if (fragment instanceof ApiariesFragment) {
                    ((ApiariesFragment) fragment).focusApiaryOnMap(site.hexId, ll[0], ll[1]);
                }
            });
            int index = i;
            row.setOnClickListener(v -> {
                selectedDest[0] = index;
                paint(destRows, selectedDest[0]);
                selectedFlora[0] = null;
                refreshQuote(fragment, quote, movable, picked, dests, selectedDest[0]);
                loadDest(fragment, dests.get(index), satBox, selectedFlora, () ->
                        refreshQuote(fragment, quote, movable, picked, dests, selectedDest[0]));
            });
            row.addView(apiary);
            row.addView(label);
            row.addView(map);
            destRows[i] = row;
            destBox.addView(row);
        }
        close.setOnClickListener(v -> dialog.dismiss());
        send.setOnClickListener(v -> {
            if (picked.isEmpty() || selectedDest[0] < 0 || selectedFlora[0] == null) {
                GameNotice.show(fragment.requireContext(), R.string.transhumance_need);
                return;
            }
            HexParcelOwnershipEntity dest = dests.get(selectedDest[0]);
            double[] ll = pin(fragment, dest);
            List<HiveEntity> going = new ArrayList<>();
            for (HiveEntity hive : movable) {
                if (picked.contains(hive.id)) {
                    going.add(hive);
                }
            }
            send.setEnabled(false);
            dispatch(fragment, viewModel, dialog, going, 0, ll[0], ll[1], selectedFlora[0]);
        });
        dialog.show();
    }

    private static void loadDest(@NonNull Fragment fragment, @NonNull HexParcelOwnershipEntity dest,
            @NonNull LinearLayout satBox, @NonNull String[] selectedFlora, @NonNull Runnable onFlora) {
        ApicultureApp app = (ApicultureApp) fragment.requireContext().getApplicationContext();
        IO.execute(() -> {
            List<String> keys = app.getHexFloraRepository().listReadyFloraKeysBlocking(
                    dest.hexId, System.currentTimeMillis());
            if (keys == null || keys.isEmpty()) {
                HexParcel parcel = IberiaHexOverlayStore.findById(app, dest.hexId);
                keys = new ArrayList<>(HexFlora.nativeMixForParcel(parcel));
            }
            List<HexFloraSaturation.Line> lines = app.getHiveRepository().floraSaturationLinesBlocking(dest.hexId);
            List<String> floras = keys;
            fragment.requireActivity().runOnUiThread(() -> {
                if (!fragment.isAdded()) {
                    return;
                }
                satBox.removeAllViews();
                android.view.LayoutInflater inflater = fragment.getLayoutInflater();
                float density = fragment.getResources().getDisplayMetrics().density;
                int jar = Math.round(40f * density);
                View[] rows = new View[floras.size()];
                for (int i = 0; i < floras.size(); i++) {
                    String key = floras.get(i);
                    View row = inflater.inflate(R.layout.item_flora_saturation, satBox, false);
                    LinearLayout head = (LinearLayout) ((LinearLayout) row).getChildAt(0);
                    ImageView icon = new ImageView(fragment.requireContext());
                    icon.setImageResource(HiveSiteSummaryUi.floraHoneyJarIcon(key));
                    LinearLayout.LayoutParams jarLp = new LinearLayout.LayoutParams(jar, jar);
                    jarLp.setMarginEnd(Math.round(8f * density));
                    icon.setLayoutParams(jarLp);
                    head.addView(icon, 0);
                    TextView name = row.findViewById(R.id.tv_flora_saturation_name);
                    name.setText(key);
                    HexFloraSaturation sat = saturationOf(lines, key);
                    FloraSaturationBar.bindItem(row, sat);
                    int index = i;
                    row.setOnClickListener(v -> {
                        selectedFlora[0] = floras.get(index);
                        paint(rows, index);
                        onFlora.run();
                    });
                    rows[i] = row;
                    satBox.addView(row);
                }
                if (!floras.isEmpty()) {
                    selectedFlora[0] = floras.get(0);
                    paint(rows, 0);
                    onFlora.run();
                }
            });
        });
    }

    private static void dispatch(@NonNull Fragment fragment, @NonNull HiveViewModel viewModel,
            @NonNull Dialog dialog, @NonNull List<HiveEntity> going, int index,
            double lat, double lng, @NonNull String flora) {
        if (!fragment.isAdded()) {
            return;
        }
        if (index >= going.size()) {
            dialog.dismiss();
            GameNotice.showSuccess(fragment.requireContext(), R.string.transhumance_ok);
            return;
        }
        viewModel.transhumance(going.get(index), lat, lng, flora, msg -> {
            if (!fragment.isAdded()) {
                return;
            }
            if (msg != null) {
                GameNotice.show(fragment.requireContext(), msg);
                return;
            }
            dispatch(fragment, viewModel, dialog, going, index + 1, lat, lng, flora);
        });
    }

    private static void refreshQuote(@NonNull Fragment fragment, @NonNull TextView quote,
            @NonNull List<HiveEntity> hives, @NonNull Set<String> picked,
            @NonNull List<HexParcelOwnershipEntity> dests, int destIndex) {
        if (destIndex < 0 || picked.isEmpty()) {
            quote.setText("");
            return;
        }
        double[] ll = pin(fragment, dests.get(destIndex));
        int cost = 0;
        double km = 0;
        int n = 0;
        for (HiveEntity hive : hives) {
            if (!picked.contains(hive.id)) {
                continue;
            }
            n++;
            km = TranshumanceRules.haversineKm(hive.lat, hive.lng, ll[0], ll[1]);
            cost += TranshumanceRules.costEuros(hive.lat, hive.lng, ll[0], ll[1]);
        }
        quote.setText(fragment.getString(R.string.transhumance_quote, n, km, cost));
    }

    @NonNull
    private static double[] pin(@NonNull Fragment fragment, @NonNull HexParcelOwnershipEntity row) {
        if (Math.abs(row.siteLat) > 1e-6 || Math.abs(row.siteLng) > 1e-6) {
            return new double[]{row.siteLat, row.siteLng};
        }
        HexParcel parcel = IberiaHexOverlayStore.findById(fragment.requireContext(), row.hexId);
        if (parcel == null) {
            return new double[]{0, 0};
        }
        return new double[]{parcel.centroidLat, parcel.centroidLon};
    }

    private static boolean canMove(@Nullable HiveEntity hive) {
        if (hive == null || hive.id == null || hive.inWarehouse) {
            return false;
        }
        if (hive.linkedToContract()) {
            return false;
        }
        if (TranshumanceRules.hasPendingContractMove(hive)) {
            return false;
        }
        return true;
    }

    @Nullable
    private static HexFloraSaturation saturationOf(@Nullable List<HexFloraSaturation.Line> lines,
            @NonNull String key) {
        if (lines == null) {
            return new HexFloraSaturation(0, 0, 0);
        }
        for (HexFloraSaturation.Line line : lines) {
            if (line != null && key.equals(line.floraKey)) {
                return line.sat;
            }
        }
        return new HexFloraSaturation(0, 0, 0);
    }

    private static void paint(@NonNull View[] rows, int selected) {
        for (int i = 0; i < rows.length; i++) {
            if (rows[i] != null) {
                rows[i].setBackgroundColor(i == selected ? 0x33C4A35A : 0x00000000);
            }
        }
    }

    @NonNull
    private static Dialog cream(@NonNull Fragment fragment, @NonNull View root) {
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
