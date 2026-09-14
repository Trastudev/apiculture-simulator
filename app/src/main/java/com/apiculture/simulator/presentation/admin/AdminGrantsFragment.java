package com.apiculture.simulator.presentation.admin;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.apiculture.simulator.ApicultureApp;
import com.apiculture.simulator.R;
import com.apiculture.simulator.databinding.FragmentAdminGrantsBinding;
import com.apiculture.simulator.domain.parcel.HexFlora;
import com.apiculture.simulator.presentation.common.GameNotice;
import com.apiculture.simulator.presentation.common.SimpleViewModelFactory;
import com.google.firebase.auth.FirebaseAuth;

public class AdminGrantsFragment extends Fragment {

    private FragmentAdminGrantsBinding binding;
    private AdminGrantsViewModel viewModel;

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
                () -> new AdminGrantsViewModel(app.getEconomyRepository(), app.getProfileRepository())))
                .get(AdminGrantsViewModel.class);

        ArrayAdapter<String> floraAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_dropdown_item, HexFlora.FLORA_TYPES);
        binding.spinnerGrantFlora.setAdapter(floraAdapter);
        int mil = floraAdapter.getPosition(HexFlora.MIL_FLORES);
        if (mil >= 0) {
            binding.spinnerGrantFlora.setSelection(mil);
        }

        binding.btnGrantCoins.setEnabled(false);
        binding.btnGrantHoney.setEnabled(false);
        viewModel.isAdmin().observe(getViewLifecycleOwner(), admin -> {
            if (binding == null) {
                return;
            }
            if (Boolean.FALSE.equals(admin)) {
                GameNotice.show(requireContext(), R.string.admin_not_allowed);
                Navigation.findNavController(view).popBackStack();
                return;
            }
            boolean ok = Boolean.TRUE.equals(admin);
            binding.btnGrantCoins.setEnabled(ok);
            binding.btnGrantHoney.setEnabled(ok);
        });
        viewModel.snapshot().observe(getViewLifecycleOwner(), this::bindSnapshot);
        viewModel.checkAdmin(FirebaseAuth.getInstance().getUid());

        binding.btnAdminBack.setOnClickListener(v ->
                Navigation.findNavController(view).popBackStack());
        binding.btnGrantCoins.setOnClickListener(v -> onGrantCoins());
        binding.btnGrantHoney.setOnClickListener(v -> onGrantHoney());
    }

    private void bindSnapshot(AdminGrantsViewModel.Snapshot snap) {
        if (snap == null || binding == null) {
            return;
        }
        binding.tvAdminGrantBalance.setText(getString(R.string.admin_grants_balance,
                Math.round(snap.balance)));
        binding.tvAdminGrantHoneyStock.setText(getString(R.string.admin_grants_honey_stock,
                snap.honeyKg));
    }

    private void onGrantCoins() {
        double amount = parseAmount(text(binding.editGrantCoins));
        if (amount <= 0.0) {
            GameNotice.show(requireContext(), R.string.admin_grants_need_amount);
            return;
        }
        if (!viewModel.grantCoins(amount)) {
            GameNotice.show(requireContext(), R.string.admin_not_allowed);
            return;
        }
        AdminGrantsViewModel.Snapshot snap = viewModel.snapshot().getValue();
        double after = snap != null ? snap.balance : 0.0;
        GameNotice.showSuccess(requireContext(),
                getString(R.string.admin_grants_coins_ok, Math.round(amount), Math.round(after)));
    }

    private void onGrantHoney() {
        double kg = parseAmount(text(binding.editGrantHoney));
        if (kg <= 0.0) {
            GameNotice.show(requireContext(), R.string.admin_grants_need_amount);
            return;
        }
        Object sel = binding.spinnerGrantFlora.getSelectedItem();
        String flora = sel != null ? sel.toString() : HexFlora.MIL_FLORES;
        if (!viewModel.grantHoney(flora, kg)) {
            GameNotice.show(requireContext(), R.string.admin_not_allowed);
            return;
        }
        AdminGrantsViewModel.Snapshot snap = viewModel.snapshot().getValue();
        double after = snap != null ? snap.honeyKg : 0.0;
        GameNotice.showSuccess(requireContext(),
                getString(R.string.admin_grants_honey_ok, kg, flora, after));
    }

    private static String text(com.google.android.material.textfield.TextInputEditText e) {
        return e.getText() != null ? e.getText().toString() : "";
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
