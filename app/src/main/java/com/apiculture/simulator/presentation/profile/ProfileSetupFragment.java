package com.apiculture.simulator.presentation.profile;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import com.apiculture.simulator.presentation.common.GameNotice;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.apiculture.simulator.ApicultureApp;
import com.apiculture.simulator.R;
import com.apiculture.simulator.data.repository.ProfileRepository;
import com.apiculture.simulator.databinding.FragmentProfileSetupBinding;
import com.apiculture.simulator.presentation.common.SimpleViewModelFactory;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class ProfileSetupFragment extends Fragment {

    private FragmentProfileSetupBinding binding;
    private ProfileSetupViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentProfileSetupBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ApicultureApp app = (ApicultureApp) requireActivity().getApplication();
        viewModel = new ViewModelProvider(this,
                new SimpleViewModelFactory<>(() -> new ProfileSetupViewModel(app.getProfileRepository())))
                .get(ProfileSetupViewModel.class);

        viewModel.saving().observe(getViewLifecycleOwner(), saving -> {
            boolean s = Boolean.TRUE.equals(saving);
            binding.btnContinue.setEnabled(!s);
            binding.progress.setVisibility(s ? View.VISIBLE : View.GONE);
        });
        viewModel.error().observe(getViewLifecycleOwner(), this::showError);

        binding.btnContinue.setOnClickListener(v -> submit());
    }

    private void submit() {
        FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
        if (u == null) {
            GameNotice.show(requireContext(), R.string.profile_setup_need_login);
            return;
        }
        String brand = binding.etHoneyBrand.getText() != null
                ? binding.etHoneyBrand.getText().toString().trim() : "";
        String name = binding.etPlayerName.getText() != null
                ? binding.etPlayerName.getText().toString().trim() : "";
        if (TextUtils.isEmpty(brand) || TextUtils.isEmpty(name)) {
            GameNotice.show(requireContext(), R.string.profile_setup_fill_all);
            return;
        }
        viewModel.submit(u.getUid(), brand, name, () -> {
            ((ApicultureApp) requireActivity().getApplication()).getLeaderboardRepository()
                    .enqueuePublish(u.getUid());
            NavHostFragment.findNavController(ProfileSetupFragment.this)
                    .navigate(R.id.action_profile_to_dashboard);
        });
    }

    private void showError(String code) {
        if (code == null) {
            return;
        }
        int msg;
        switch (code) {
            case ProfileRepository.ERR_BRAND_TAKEN:
                msg = R.string.profile_setup_error_brand_taken;
                break;
            case ProfileRepository.ERR_NAME_TAKEN:
                msg = R.string.profile_setup_error_name_taken;
                break;
            case "SHORT":
                msg = R.string.profile_setup_error_short;
                break;
            case "INVALID":
                msg = R.string.profile_setup_error_invalid;
                break;
            case "NO_UID":
                msg = R.string.profile_setup_need_login;
                break;
            default:
                msg = R.string.profile_setup_error_generic;
                break;
        }
        GameNotice.show(requireContext(), msg);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
