package com.apiculture.simulator.presentation.admin;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.apiculture.simulator.ApicultureApp;
import com.apiculture.simulator.R;
import com.apiculture.simulator.data.local.entity.HiveEntity;
import com.apiculture.simulator.data.repository.AdminGameResetRepository;
import com.apiculture.simulator.data.repository.ProfileRepository;
import com.apiculture.simulator.databinding.FragmentAdminGrantsBinding;
import com.apiculture.simulator.domain.game.HiveHoneyStocks;
import com.apiculture.simulator.domain.parcel.HexFlora;
import com.apiculture.simulator.presentation.common.GameNotice;
import com.apiculture.simulator.presentation.common.SimpleViewModelFactory;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.apiculture.simulator.data.session.PlayerAuth;
import com.apiculture.simulator.data.session.SignedInUser;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AdminGrantsFragment extends Fragment {

    private FragmentAdminGrantsBinding binding;
    private AdminGrantsViewModel viewModel;
    private boolean globalResetBusy;
    private boolean grantBusy;
    private final Map<String, AdminGrantsViewModel.Target> targetsByLabel = new LinkedHashMap<>();
    private final Map<String, Boolean> hiveChecked = new LinkedHashMap<>();
    private List<HiveEntity> adminHives = new ArrayList<>();
    private ArrayAdapter<String> playerAdapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentAdminGrantsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ApicultureApp app = (ApicultureApp) requireActivity().getApplication();
        viewModel = new ViewModelProvider(this, new SimpleViewModelFactory<>(
                () -> new AdminGrantsViewModel(
                        app.getEconomyRepository(),
                        app.getProfileRepository(),
                        app.getPlayerProgressRepository(),
                        app.getUserGameStateRepository())))
                .get(AdminGrantsViewModel.class);

        ArrayAdapter<String> floraAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_dropdown_item, HexFlora.FLORA_TYPES);
        binding.spinnerGrantFlora.setAdapter(floraAdapter);
        int mil = floraAdapter.getPosition(HexFlora.MIL_FLORES);
        if (mil >= 0) {
            binding.spinnerGrantFlora.setSelection(mil);
        }
        ArrayAdapter<String> hiveFloraAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_dropdown_item, HexFlora.FLORA_TYPES);
        binding.spinnerHiveHoneyFlora.setAdapter(hiveFloraAdapter);
        if (mil >= 0) {
            binding.spinnerHiveHoneyFlora.setSelection(mil);
        }

        binding.btnGrantCoins.setEnabled(false);
        binding.btnGrantHoney.setEnabled(false);
        binding.btnGrantXp.setEnabled(false);
        binding.btnGrantLevel.setEnabled(false);
        binding.btnAdminGlobalReset.setEnabled(false);
        viewModel.isAdmin().observe(getViewLifecycleOwner(), admin -> {
            if (binding == null) {
                return;
            }
            if (Boolean.FALSE.equals(admin)) {
                GameNotice.show(requireContext(), R.string.admin_not_allowed);
                Navigation.findNavController(view).popBackStack();
                return;
            }
            applyGrantButtonsEnabled();
        });
        playerAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_dropdown_item_1line, new ArrayList<>());
        binding.actGrantPlayer.setAdapter(playerAdapter);
        binding.actGrantPlayer.setOnItemClickListener((p, v, pos, id) -> {
            String label = playerAdapter.getItem(pos);
            AdminGrantsViewModel.Target t = targetsByLabel.get(label);
            if (t != null) {
                viewModel.setTarget(t);
                binding.actGrantPlayer.setText(t.label, false);
            }
        });
        binding.actGrantPlayer.setOnFocusChangeListener((v, has) -> {
            if (has && binding != null) {
                binding.actGrantPlayer.showDropDown();
            }
        });
        viewModel.snapshot().observe(getViewLifecycleOwner(), this::bindSnapshot);
        viewModel.players().observe(getViewLifecycleOwner(), this::bindPlayers);
        viewModel.checkAdmin(PlayerAuth.getInstance().getUid());

        binding.btnAdminBack.setOnClickListener(v ->
                Navigation.findNavController(view).popBackStack());
        binding.btnGrantCoins.setOnClickListener(v -> onGrantCoins());
        binding.btnGrantHoney.setOnClickListener(v -> onGrantHoney());
        binding.btnGrantXp.setOnClickListener(v -> onGrantXp());
        binding.btnGrantLevel.setOnClickListener(v -> onGrantLevel());
        binding.btnAdminGlobalReset.setOnClickListener(v -> confirmGlobalReset(app));
        binding.btnHiveHoneyAll.setOnClickListener(v -> setAllHivesChecked(true));
        binding.btnHiveHoneyNone.setOnClickListener(v -> setAllHivesChecked(false));
        binding.btnHiveHoneyAdd.setOnClickListener(v -> addHoneyToCheckedHives(app));
        loadAdminHives(app);
    }

    private void loadAdminHives(@NonNull ApicultureApp app) {
        String uid = PlayerAuth.getInstance().getUid();
        if (uid == null) {
            return;
        }
        new Thread(() -> {
            List<HiveEntity> rows = app.getHiveRepository().getLocalHivesSync(uid);
            if (!isAdded()) {
                return;
            }
            requireActivity().runOnUiThread(() -> bindHiveChecks(rows));
        }).start();
    }

    private void bindHiveChecks(@Nullable List<HiveEntity> rows) {
        if (binding == null) {
            return;
        }
        adminHives = new ArrayList<>();
        if (rows != null) {
            for (HiveEntity hive : rows) {
                if (hive != null && hive.id != null) {
                    adminHives.add(hive);
                }
            }
        }
        adminHives.sort((a, b) -> hiveLabel(a).compareToIgnoreCase(hiveLabel(b)));
        binding.llAdminHives.removeAllViews();
        boolean empty = adminHives.isEmpty();
        binding.tvAdminHivesEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        for (HiveEntity hive : adminHives) {
            CheckBox box = new CheckBox(requireContext());
            String flora = hive.floraType != null && !hive.floraType.trim().isEmpty()
                    ? hive.floraType.trim() : "—";
            String line = getString(R.string.admin_hive_honey_line, hiveLabel(hive), flora,
                    Math.max(0.0, hive.honeyProduction));
            if (hive.inWarehouse) {
                line = line + " · " + getString(R.string.admin_hive_honey_stored);
            }
            box.setText(line);
            box.setTextColor(requireContext().getColor(R.color.dash_text_card));
            box.setChecked(Boolean.TRUE.equals(hiveChecked.get(hive.id)));
            box.setOnCheckedChangeListener((button, checked) -> hiveChecked.put(hive.id, checked));
            binding.llAdminHives.addView(box);
        }
    }

    private void setAllHivesChecked(boolean checked) {
        for (HiveEntity hive : adminHives) {
            hiveChecked.put(hive.id, checked);
        }
        bindHiveChecks(adminHives);
    }

    private void addHoneyToCheckedHives(@NonNull ApicultureApp app) {
        if (!Boolean.TRUE.equals(viewModel.isAdmin().getValue()) || binding == null || grantBusy) {
            return;
        }
        double kg = parseAmount(text(binding.editHiveHoneyKg));
        if (kg <= 0.0) {
            GameNotice.show(requireContext(), R.string.admin_grants_need_amount);
            return;
        }
        Object floraItem = binding.spinnerHiveHoneyFlora.getSelectedItem();
        String flora = floraItem != null ? floraItem.toString() : HexFlora.MIL_FLORES;
        List<HiveEntity> chosen = new ArrayList<>();
        for (HiveEntity hive : adminHives) {
            if (Boolean.TRUE.equals(hiveChecked.get(hive.id))) {
                chosen.add(hive);
            }
        }
        if (chosen.isEmpty()) {
            GameNotice.show(requireContext(), R.string.admin_hive_honey_need);
            return;
        }
        grantBusy = true;
        applyGrantButtonsEnabled();
        new Thread(() -> {
            int updated = 0;
            int full = 0;
            double added = 0.0;
            for (HiveEntity hive : chosen) {
                double got = HiveHoneyStocks.depositType(hive, flora, kg);
                if (got <= 1e-9) {
                    full++;
                    continue;
                }
                app.getHiveRepository().saveHive(hive);
                updated++;
                added += got;
            }
            double shown = added;
            int done = updated;
            int skipped = full;
            if (!isAdded()) {
                return;
            }
            requireActivity().runOnUiThread(() -> {
                grantBusy = false;
                applyGrantButtonsEnabled();
                if (!isAdded() || binding == null) {
                    return;
                }
                if (done == 0) {
                    GameNotice.show(requireContext(), R.string.admin_hive_honey_full);
                } else {
                    String msg = getString(R.string.admin_hive_honey_ok, shown, flora, done);
                    if (skipped > 0) {
                        msg = msg + " " + getString(R.string.admin_hive_honey_partial, skipped);
                    }
                    GameNotice.showSuccess(requireContext(), msg);
                }
                loadAdminHives(app);
            });
        }).start();
    }

    @NonNull
    private static String hiveLabel(@NonNull HiveEntity hive) {
        if (hive.name != null && !hive.name.trim().isEmpty()) {
            return hive.name.trim();
        }
        String id = hive.id != null ? hive.id : "";
        return id.length() > 8 ? id.substring(0, 8) : id;
    }

    private void bindSnapshot(AdminGrantsViewModel.Snapshot snap) {
        if (snap == null || binding == null) {
            return;
        }
        binding.tvAdminGrantBalance.setText(getString(R.string.admin_grants_balance,
                snap.balance));
        binding.tvAdminGrantHoneyStock.setText(getString(R.string.admin_grants_honey_stock,
                snap.honeyKg));
        binding.tvAdminGrantXp.setText(getString(R.string.admin_grants_xp_line,
                snap.level, snap.xp, snap.maxXp));
    }

    private void confirmGlobalReset(@NonNull ApicultureApp app) {
        if (!Boolean.TRUE.equals(viewModel.isAdmin().getValue()) || globalResetBusy) {
            return;
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.admin_global_reset_confirm_title)
                .setMessage(R.string.admin_global_reset_confirm_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.admin_global_reset_action, (d, w) -> runGlobalReset(app))
                .show();
    }

    private void runGlobalReset(@NonNull ApicultureApp app) {
        SignedInUser user = PlayerAuth.getInstance().getCurrentUser();
        if (user == null) {
            GameNotice.show(requireContext(), R.string.admin_not_allowed);
            return;
        }
        setGlobalResetBusy(true);
        GameNotice.show(requireContext(), R.string.admin_global_reset_busy);
        AdminGameResetRepository repo = app.getAdminGameResetRepository();
        repo.issueGlobalPlayerReset(user.getUid(), err -> {
            if (!isAdded() || binding == null) {
                return;
            }
            if (err != null) {
                setGlobalResetBusy(false);
                GameNotice.show(requireContext(), err);
            }
        }, okMsg -> {
            if (!isAdded() || binding == null) {
                return;
            }
            GameNotice.showSuccess(requireContext(), okMsg);
            repo.fetchRemoteGeneration(gen -> {
                if (!isAdded()) {
                    return;
                }
                app.resetPlayerToStarterState(user.getUid(), gen, localErr -> {
                    if (!isAdded() || binding == null) {
                        return;
                    }
                    setGlobalResetBusy(false);
                    if (localErr == null) {
                        app.getHiveRepository().startRealtimeCloudSync(user.getUid());
                        app.getHexParcelRepository().startRealtimeCloudSync();
                        viewModel.refresh();
                        GameNotice.showSuccess(requireContext(), R.string.admin_global_reset_local_ok);
                    } else {
                        GameNotice.show(requireContext(), localErr);
                    }
                });
            });
        });
    }

    private void setGlobalResetBusy(boolean busy) {
        globalResetBusy = busy;
        applyGrantButtonsEnabled();
    }

    private void applyGrantButtonsEnabled() {
        if (binding == null) {
            return;
        }
        boolean ok = Boolean.TRUE.equals(viewModel.isAdmin().getValue()) && !globalResetBusy && !grantBusy;
        binding.btnGrantCoins.setEnabled(ok);
        binding.btnGrantHoney.setEnabled(ok);
        binding.btnGrantXp.setEnabled(ok);
        binding.btnGrantLevel.setEnabled(ok);
        binding.btnHiveHoneyAdd.setEnabled(ok);
        binding.btnAdminGlobalReset.setEnabled(ok);
    }

    private void bindPlayers(@Nullable List<ProfileRepository.PlayerOption> list) {
        if (binding == null || playerAdapter == null) {
            return;
        }
        targetsByLabel.clear();
        List<String> labels = new ArrayList<>();
        String allLabel = getString(R.string.admin_grants_target_all);
        targetsByLabel.put(allLabel, AdminGrantsViewModel.Target.all(allLabel));
        labels.add(allLabel);
        String me = PlayerAuth.getInstance().getUid();
        if (list != null) {
            for (int i = 0; i < list.size(); i++) {
                ProfileRepository.PlayerOption p = list.get(i);
                if (p == null || p.uid.isEmpty()) {
                    continue;
                }
                String label = p.label();
                if (me != null && me.equals(p.uid)) {
                    label = getString(R.string.admin_grants_target_me, p.label());
                }
                if (targetsByLabel.containsKey(label)) {
                    label = label + " · " + p.uid.substring(0, Math.min(6, p.uid.length()));
                }
                targetsByLabel.put(label, AdminGrantsViewModel.Target.player(p.uid, label));
                labels.add(label);
            }
        }
        playerAdapter.clear();
        playerAdapter.addAll(labels);
        playerAdapter.notifyDataSetChanged();
        if (viewModel.target() == null && me != null) {
            for (Map.Entry<String, AdminGrantsViewModel.Target> e : targetsByLabel.entrySet()) {
                if (!e.getValue().all && me.equals(e.getValue().uid)) {
                    viewModel.setTarget(e.getValue());
                    binding.actGrantPlayer.setText(e.getKey(), false);
                    break;
                }
            }
        }
    }

    private void onGrantCoins() {
        double amount = parseAmount(text(binding.editGrantCoins));
        if (amount <= 0.0) {
            GameNotice.show(requireContext(), R.string.admin_grants_need_amount);
            return;
        }
        runGrant(() -> {
            if (!viewModel.grantCoins(amount, err -> finishGrant(err, true))) {
                finishGrant(getString(R.string.admin_not_allowed), false);
            }
        });
    }

    private void onGrantHoney() {
        double kg = parseAmount(text(binding.editGrantHoney));
        if (kg <= 0.0) {
            GameNotice.show(requireContext(), R.string.admin_grants_need_amount);
            return;
        }
        Object sel = binding.spinnerGrantFlora.getSelectedItem();
        String flora = sel != null ? sel.toString() : HexFlora.MIL_FLORES;
        runGrant(() -> {
            if (!viewModel.grantHoney(flora, kg, err -> finishGrant(err, true))) {
                finishGrant(getString(R.string.admin_not_allowed), false);
            }
        });
    }

    private void onGrantXp() {
        int amount = parseIntAmount(text(binding.editGrantXp));
        if (amount <= 0) {
            GameNotice.show(requireContext(), R.string.admin_grants_need_amount);
            return;
        }
        runGrant(() -> {
            if (!viewModel.grantXp(amount, err -> finishGrant(err, true))) {
                finishGrant(getString(R.string.admin_not_allowed), false);
            }
        });
    }

    private void onGrantLevel() {
        runGrant(() -> {
            if (!viewModel.grantLevel(err -> finishGrant(err, true))) {
                finishGrant(getString(R.string.admin_not_allowed), false);
            }
        });
    }

    private void runGrant(Runnable action) {
        if (!syncTargetFromField()) {
            GameNotice.show(requireContext(), R.string.admin_grants_need_player);
            return;
        }
        AdminGrantsViewModel.Target dest = viewModel.target();
        if (dest != null && dest.all) {
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.admin_grants_all_confirm_title)
                    .setMessage(R.string.admin_grants_all_confirm_message)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(android.R.string.ok, (d, w) -> startGrant(action))
                    .show();
            return;
        }
        startGrant(action);
    }

    private void startGrant(Runnable action) {
        grantBusy = true;
        applyGrantButtonsEnabled();
        GameNotice.show(requireContext(), R.string.admin_grants_busy);
        action.run();
    }

    private void finishGrant(@Nullable String err, boolean allowed) {
        grantBusy = false;
        if (binding != null) {
            applyGrantButtonsEnabled();
        }
        if (!isAdded()) {
            return;
        }
        if (!allowed) {
            GameNotice.show(requireContext(), err != null ? err : getString(R.string.admin_not_allowed));
            return;
        }
        if (err != null) {
            GameNotice.show(requireContext(), err);
            return;
        }
        AdminGrantsViewModel.Target dest = viewModel.target();
        GameNotice.showSuccess(requireContext(), dest != null && dest.all
                ? R.string.admin_grants_all_ok
                : R.string.admin_grants_remote_ok);
    }

    private boolean syncTargetFromField() {
        if (binding == null) {
            return false;
        }
        String typed = binding.actGrantPlayer.getText() != null
                ? binding.actGrantPlayer.getText().toString().trim() : "";
        AdminGrantsViewModel.Target t = targetsByLabel.get(typed);
        if (t == null) {
            for (Map.Entry<String, AdminGrantsViewModel.Target> e : targetsByLabel.entrySet()) {
                if (e.getKey().equalsIgnoreCase(typed)) {
                    t = e.getValue();
                    break;
                }
            }
        }
        if (t == null) {
            return viewModel.target() != null;
        }
        viewModel.setTarget(t);
        return true;
    }

    private static String text(com.google.android.material.textfield.TextInputEditText e) {
        return e.getText() != null ? e.getText().toString() : "";
    }

    private static int parseIntAmount(String raw) {
        double v = parseAmount(raw);
        if (v <= 0.0 || v > Integer.MAX_VALUE) {
            return 0;
        }
        return (int) Math.round(v);
    }

    private static double parseAmount(String raw) {
        if (raw == null) {
            return 0.0;
        }
        try {
            return Double.parseDouble(raw.trim().replace(',', '.'));
        } catch (Exception e) {
            return 0.0;
        }
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }
}
