package com.apiculture.simulator.presentation.admin;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import com.apiculture.simulator.presentation.common.GameNotice;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.apiculture.simulator.ApicultureApp;
import com.apiculture.simulator.R;
import com.apiculture.simulator.data.repository.GlobalEventRepository;
import com.apiculture.simulator.databinding.FragmentAdminEventsBinding;
import com.apiculture.simulator.domain.game.IberianClimateZone;
import com.apiculture.simulator.domain.game.SouthernAfricanClimateZone;
import com.apiculture.simulator.domain.parcel.HexFlora;
import com.apiculture.simulator.presentation.common.SimpleViewModelFactory;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AdminEventsFragment extends Fragment {

    private FragmentAdminEventsBinding binding;
    private AdminEventsViewModel viewModel;
    private final boolean[] selectedFloras = new boolean[HexFlora.FLORA_TYPES.length];
    private final List<CheckBox> climateBoxes = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentAdminEventsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ApicultureApp app = (ApicultureApp) requireActivity().getApplication();
        viewModel = new ViewModelProvider(this, new SimpleViewModelFactory<>(
                () -> new AdminEventsViewModel(app.getGlobalEventRepository(), app.getProfileRepository())))
                .get(AdminEventsViewModel.class);

        ArrayAdapter<String> floraAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_dropdown_item, HexFlora.FLORA_TYPES);
        binding.spinnerSurgeFlora.setAdapter(floraAdapter);

        inflateClimates();

        binding.rgShiftScope.setOnCheckedChangeListener((g, id) ->
                binding.btnShiftPickFlora.setEnabled(id == R.id.rb_shift_some));
        binding.btnShiftPickFlora.setOnClickListener(v -> pickFloras());

        viewModel.isAdmin().observe(getViewLifecycleOwner(), admin -> {
            if (Boolean.FALSE.equals(admin)) {
                GameNotice.show(requireContext(), R.string.admin_not_allowed);
                Navigation.findNavController(view).popBackStack();
            }
        });
        viewModel.events().observe(getViewLifecycleOwner(), this::bindStatus);
        String uid = FirebaseAuth.getInstance().getUid();
        viewModel.checkAdmin(uid);

        binding.btnAdminBack.setOnClickListener(v ->
                Navigation.findNavController(view).popBackStack());
        binding.btnSurgeToggle.setOnClickListener(v -> onSurgeToggle());
        binding.btnShiftToggle.setOnClickListener(v -> onShiftToggle());
        binding.btnVelutinaToggle.setOnClickListener(v -> onVelutinaToggle());
    }

    private void inflateClimates() {
        binding.llVelutinaClimates.removeAllViews();
        climateBoxes.clear();
        addClimate(IberianClimateZone.ATLANTIC.name(), "Iberia · Atlántico");
        addClimate(IberianClimateZone.MOUNTAIN.name(), "Iberia · Montaña");
        addClimate(IberianClimateZone.MEDITERRANEAN.name(), "Iberia · Mediterráneo");
        addClimate(IberianClimateZone.SOUTH.name(), "Iberia · Sur");
        addClimate(IberianClimateZone.CONTINENTAL.name(), "Iberia · Continental");
        addClimate(SouthernAfricanClimateZone.FYNBOS.name(), "ZA · Fynbos");
        addClimate(SouthernAfricanClimateZone.KAROO.name(), "ZA · Karoo");
        addClimate(SouthernAfricanClimateZone.HIGHVELD.name(), "ZA · Highveld");
        addClimate(SouthernAfricanClimateZone.SUBTROPICAL.name(), "ZA · Subtropical");
        addClimate(SouthernAfricanClimateZone.BUSHVELD.name(), "ZA · Bushveld");
    }

    private void addClimate(String key, String label) {
        CheckBox cb = new CheckBox(requireContext());
        cb.setText(label);
        cb.setTag(key);
        binding.llVelutinaClimates.addView(cb);
        climateBoxes.add(cb);
    }

    private void bindStatus(GlobalEventRepository.Snapshot snap) {
        if (snap == null || binding == null) {
            return;
        }
        binding.tvAdminSurgeStatus.setText(statusLine(snap.surge));
        binding.btnSurgeToggle.setText(snap.surge.isLive()
                ? R.string.admin_deactivate : R.string.admin_activate);
        binding.tvAdminShiftStatus.setText(statusLine(snap.shift));
        binding.btnShiftToggle.setText(snap.shift.isLive()
                ? R.string.admin_deactivate : R.string.admin_activate);
        binding.tvAdminVelutinaStatus.setText(statusLine(snap.velutina));
        binding.btnVelutinaToggle.setText(snap.velutina.isLive()
                ? R.string.admin_deactivate : R.string.admin_activate);
    }

    private String statusLine(GlobalEventRepository.EventDoc d) {
        if (d == null || !d.exists()) {
            return getString(R.string.admin_status_off);
        }
        if (d.isLive()) {
            return getString(R.string.admin_status_on, Math.max(1, d.durationDays));
        }
        if (d.isEnded()) {
            return getString(R.string.admin_status_ended);
        }
        return getString(R.string.admin_status_off);
    }

    private void onSurgeToggle() {
        String uid = FirebaseAuth.getInstance().getUid();
        GlobalEventRepository.Snapshot snap = viewModel.cached();
        if (snap != null && snap.surge.isLive()) {
            viewModel.deactivateSurge(msg -> toast(msg, R.string.admin_saved));
            return;
        }
        Object sel = binding.spinnerSurgeFlora.getSelectedItem();
        String flora = sel != null ? sel.toString() : HexFlora.MIL_FLORES;
        int days = parseInt(binding.editSurgeDays.getText() != null
                ? binding.editSurgeDays.getText().toString() : "3", 3);
        viewModel.activateSurge(flora, days, uid, msg -> toast(msg, R.string.admin_saved));
    }

    private void onShiftToggle() {
        String uid = FirebaseAuth.getInstance().getUid();
        GlobalEventRepository.Snapshot snap = viewModel.cached();
        if (snap != null && snap.shift.isLive()) {
            viewModel.deactivateShift(msg -> toast(msg, R.string.admin_saved));
            return;
        }
        boolean all = binding.rbShiftAll.isChecked();
        List<String> floras = new ArrayList<>();
        if (!all) {
            for (int i = 0; i < selectedFloras.length; i++) {
                if (selectedFloras[i]) {
                    floras.add(HexFlora.FLORA_TYPES[i]);
                }
            }
            if (floras.isEmpty()) {
                GameNotice.show(requireContext(), R.string.admin_shift_need_flora);
                return;
            }
        }
        int demand = parseInt(text(binding.editShiftDemand), 20);
        int price = parseInt(text(binding.editShiftPrice), 20);
        int days = parseInt(text(binding.editShiftDays), 3);
        viewModel.activateShift(all, floras, demand, price, days, uid,
                msg -> toast(msg, R.string.admin_saved));
    }

    private void onVelutinaToggle() {
        String uid = FirebaseAuth.getInstance().getUid();
        GlobalEventRepository.Snapshot snap = viewModel.cached();
        if (snap != null && snap.velutina.isLive()) {
            viewModel.deactivateVelutina(msg -> toast(msg, R.string.admin_saved));
            return;
        }
        List<String> climates = new ArrayList<>();
        for (CheckBox cb : climateBoxes) {
            if (cb.isChecked() && cb.getTag() != null) {
                climates.add(String.valueOf(cb.getTag()));
            }
        }
        if (climates.isEmpty()) {
            GameNotice.show(requireContext(), R.string.admin_velutina_need_climate);
            return;
        }
        double loss = parseInt(text(binding.editVelutinaLoss), 15);
        int days = parseInt(text(binding.editVelutinaDays), 2);
        viewModel.activateVelutina(loss, climates, days, uid, msg -> toast(msg, R.string.admin_saved));
    }

    private void pickFloras() {
        String[] items = HexFlora.FLORA_TYPES;
        boolean[] checked = Arrays.copyOf(selectedFloras, selectedFloras.length);
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.admin_shift_pick_flora)
                .setMultiChoiceItems(items, checked, (d, which, isChecked) -> checked[which] = isChecked)
                .setPositiveButton(android.R.string.ok, (d, w) -> System.arraycopy(checked, 0, selectedFloras, 0, checked.length))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void toast(String err, int okRes) {
        if (err != null) {
            GameNotice.show(requireContext(), err);
        } else {
            GameNotice.showSuccess(requireContext(), okRes);
        }
    }

    private static String text(com.google.android.material.textfield.TextInputEditText e) {
        return e.getText() != null ? e.getText().toString() : "";
    }

    private static int parseInt(String raw, int def) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (Exception e) {
            return def;
        }
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }
}
