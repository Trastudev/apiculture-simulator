package com.apiculture.simulator.presentation.hive;

import android.view.LayoutInflater;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.apiculture.simulator.R;
import com.apiculture.simulator.data.repository.HiveRepository;
import com.apiculture.simulator.databinding.DialogBuyHiveBinding;
import com.apiculture.simulator.databinding.DialogTitleBuyHiveBinding;
import com.apiculture.simulator.presentation.common.GameNotice;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Diálogo de compra de colmena compartido por el listado de apiarios y el prado.
 */
final class BuyHiveDialogs {

    private BuyHiveDialogs() {
    }

    static void show(Fragment fragment, HiveViewModel viewModel, @Nullable String preselectHexId) {
        if (fragment == null || !fragment.isAdded() || fragment.getContext() == null) {
            return;
        }
        String ownerId = FirebaseAuth.getInstance().getUid();
        if (ownerId == null || ownerId.isEmpty()) {
            GameNotice.show(fragment.requireContext(), R.string.hive_buy_session_invalid);
            return;
        }
        LayoutInflater inflater = fragment.getLayoutInflater();
        DialogBuyHiveBinding d = DialogBuyHiveBinding.inflate(inflater);
        DialogTitleBuyHiveBinding titleBinding = DialogTitleBuyHiveBinding.inflate(inflater);
        titleBinding.tvTitleHiveBuy.setText(fragment.getString(R.string.hive_buy_title));
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(fragment.requireContext())
                .setCustomTitle(titleBinding.getRoot())
                .setView(d.getRoot())
                .setPositiveButton(R.string.hive_buy_confirm, null)
                .setNegativeButton(android.R.string.cancel, null);
        AlertDialog dialog = builder.create();

        int spinRow = R.layout.item_spinner_dialog_light;
        ArrayAdapter<String> emptyFloraAdapter =
                new ArrayAdapter<>(fragment.requireContext(), spinRow, new ArrayList<>());
        emptyFloraAdapter.setDropDownViewResource(spinRow);
        d.spinnerFlora.setAdapter(emptyFloraAdapter);

        final List<HiveRepository.OwnedHexOption>[] hexBuffer = new List[]{Collections.emptyList()};

        Runnable refreshFloraForSelectedHex = () -> {
            if (!fragment.isAdded()) {
                return;
            }
            int hexIdx = d.spinnerHex.getSelectedItemPosition();
            List<HiveRepository.OwnedHexOption> opts = hexBuffer[0];
            if (opts == null || opts.isEmpty() || hexIdx < 0 || hexIdx >= opts.size()) {
                ArrayAdapter<String> emp = new ArrayAdapter<>(fragment.requireContext(), spinRow, new ArrayList<>());
                emp.setDropDownViewResource(spinRow);
                d.spinnerFlora.setAdapter(emp);
                return;
            }
            String hexId = opts.get(hexIdx).hexId;
            viewModel.listReadyFlorasForHex(hexId, floras -> {
                if (!fragment.isAdded()) {
                    return;
                }
                List<String> list = floras != null ? floras : Collections.emptyList();
                ArrayAdapter<String> fa = new ArrayAdapter<>(fragment.requireContext(), spinRow, list);
                fa.setDropDownViewResource(spinRow);
                d.spinnerFlora.setAdapter(fa);
            });
        };

        Runnable loadOwnedHexes = () -> viewModel.listOwnedTerrainsForHivePurchase(ownerId, options -> {
            if (!fragment.isAdded()) {
                return;
            }
            hexBuffer[0] = options != null ? options : Collections.emptyList();
            List<String> labels = new ArrayList<>();
            int select = 0;
            for (int i = 0; i < hexBuffer[0].size(); i++) {
                HiveRepository.OwnedHexOption o = hexBuffer[0].get(i);
                labels.add(o.label);
                if (preselectHexId != null && preselectHexId.equals(o.hexId)) {
                    select = i;
                }
            }
            ArrayAdapter<String> hexAd = new ArrayAdapter<>(fragment.requireContext(), spinRow, labels);
            hexAd.setDropDownViewResource(spinRow);
            d.spinnerHex.setAdapter(hexAd);
            if (!labels.isEmpty()) {
                d.spinnerHex.setSelection(select);
            }
            refreshFloraForSelectedHex.run();
        });

        d.spinnerHex.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, android.view.View view, int position, long id) {
                refreshFloraForSelectedHex.run();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        Runnable updatePrice = () -> {
            int checked = d.rgSupers.getCheckedRadioButtonId();
            int superCount = checked == d.rbSuper2.getId() ? 2
                    : (checked == d.rbSuper1.getId() ? 1 : 0);
            d.tvHiveBuyPrice.setText(fragment.getString(R.string.hive_buy_price_template,
                    HiveViewModel.hivePurchasePriceEuros(superCount)));
        };
        d.rgSupers.setOnCheckedChangeListener((g, checkedId) -> updatePrice.run());
        updatePrice.run();

        dialog.setOnShowListener(di -> {
            if (dialog.getWindow() != null) {
                dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            }
            d.editHiveName.post(() -> {
                d.editHiveName.requestFocus();
                android.view.inputmethod.InputMethodManager imm =
                        (android.view.inputmethod.InputMethodManager) fragment.requireContext()
                                .getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.showSoftInput(d.editHiveName, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
                }
            });
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                int checked = d.rgSupers.getCheckedRadioButtonId();
                int superCount = checked == d.rbSuper2.getId() ? 2
                        : (checked == d.rbSuper1.getId() ? 1 : 0);
                String name = d.editHiveName.getText() != null ? d.editHiveName.getText().toString().trim() : "";
                if (name.isEmpty()) {
                    GameNotice.show(fragment.requireContext(), R.string.hive_buy_name_required);
                    return;
                }
                int hexIdx = d.spinnerHex.getSelectedItemPosition();
                List<HiveRepository.OwnedHexOption> opts = hexBuffer[0];
                if (opts == null || opts.isEmpty()) {
                    GameNotice.show(fragment.requireContext(), R.string.hive_buy_no_hexes);
                    return;
                }
                if (hexIdx < 0 || hexIdx >= opts.size()) {
                    GameNotice.show(fragment.requireContext(), R.string.hive_buy_need_hex);
                    return;
                }
                String hexId = opts.get(hexIdx).hexId;
                Object floraSel = d.spinnerFlora.getSelectedItem();
                if (!(floraSel instanceof String) || ((String) floraSel).isEmpty()) {
                    GameNotice.show(fragment.requireContext(), R.string.hive_buy_no_flora_ready);
                    return;
                }
                String flora = (String) floraSel;
                viewModel.purchaseHive(ownerId, hexId, flora, name, superCount, msg -> {
                    if (!fragment.isAdded()) {
                        return;
                    }
                    if (msg == null) {
                        dialog.dismiss();
                        GameNotice.show(fragment.requireContext(), R.string.hive_created_ok);
                    } else {
                        GameNotice.show(fragment.requireContext(), msg);
                    }
                });
            });
        });

        dialog.show();
        loadOwnedHexes.run();
    }
}
