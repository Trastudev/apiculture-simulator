package com.apiculture.simulator.presentation.auth;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import com.apiculture.simulator.data.session.PlayerAuth;
import com.apiculture.simulator.data.session.SignedInUser;
import com.apiculture.simulator.presentation.common.GameNotice;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.apiculture.simulator.ApicultureApp;
import com.apiculture.simulator.R;
import com.apiculture.simulator.data.repository.LegalLinks;
import com.apiculture.simulator.data.repository.ProfileRepository;
import com.apiculture.simulator.databinding.FragmentLoginBinding;
import com.apiculture.simulator.presentation.common.SimpleViewModelFactory;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.common.api.ApiException;

public class LoginFragment extends Fragment {

    private FragmentLoginBinding binding;
    private AuthViewModel viewModel;
    private boolean googleSignInInFlight;

    private final ActivityResultLauncher<Intent> googleSignInLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                googleSignInInFlight = false;
                setGoogleBusy(false);
                try {
                    GoogleSignInAccount account = GoogleSignIn.getSignedInAccountFromIntent(result.getData())
                            .getResult(ApiException.class);
                    if (account == null || account.getIdToken() == null) {
                        GameNotice.show(requireContext(), R.string.login_google_no_token);
                        return;
                    }
                    viewModel.loginWithGoogle(account);
                } catch (ApiException e) {
                    if (e.getStatusCode() == 12501) {
                        return;
                    }
                    GameNotice.show(requireContext(),
                            getString(R.string.login_google_fail, e.getStatusCode()));
                }
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentLoginBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ApicultureApp app = (ApicultureApp) requireActivity().getApplication();
        viewModel = new ViewModelProvider(this,
                new SimpleViewModelFactory<>(() -> new AuthViewModel(app.getAuthRepository())))
                .get(AuthViewModel.class);

        viewModel.isLoggedIn().observe(getViewLifecycleOwner(), loggedIn -> {
            if (Boolean.TRUE.equals(loggedIn)) {
                navigateAfterAuth();
            }
        });
        viewModel.error().observe(getViewLifecycleOwner(), message -> {
            if (message != null && !message.isEmpty()) {
                GameNotice.show(requireContext(), message);
            }
        });

        binding.loginForm.btnGoogle.setOnClickListener(v -> startGoogleSignIn());
        bindLegalLink(binding.loginForm.tvPrivacy, LegalLinks.privacyPolicyUrl());
        bindLegalLink(binding.loginForm.tvDeleteAccountWeb, LegalLinks.accountDeletionUrl());
    }

    private void bindLegalLink(@NonNull android.widget.TextView view, @NonNull String url) {
        if (url.isEmpty()) {
            view.setVisibility(View.GONE);
            return;
        }
        view.setVisibility(View.VISIBLE);
        view.setOnClickListener(v -> LegalLinks.open(requireContext(), url));
    }

    private void startGoogleSignIn() {
        if (googleSignInInFlight) {
            return;
        }
        if (!viewModel.isGoogleSignInConfigured()) {
            GameNotice.show(requireContext(), R.string.login_google_missing_config);
            return;
        }
        googleSignInInFlight = true;
        setGoogleBusy(true);
        googleSignInLauncher.launch(viewModel.googleSignInIntent());
    }

    private void setGoogleBusy(boolean busy) {
        if (binding == null) {
            return;
        }
        binding.loginForm.btnGoogle.setEnabled(!busy);
        binding.loginForm.btnGoogle.setText(busy ? R.string.login_google_wait : R.string.login_google);
    }

    private void navigateAfterAuth() {
        ApicultureApp app = (ApicultureApp) requireActivity().getApplication();
        SignedInUser u = PlayerAuth.getInstance().getCurrentUser();
        if (u == null) {
            NavHostFragment.findNavController(LoginFragment.this).navigate(R.id.action_login_to_dashboard);
            return;
        }
        app.getProfileRepository().fetchProfileComplete(u.getUid(), gate -> requireActivity().runOnUiThread(() -> {
            if (!isAdded()) {
                return;
            }
            setGoogleBusy(false);
            if (gate == ProfileRepository.PROFILE_UNAUTHORIZED) {
                GameNotice.show(requireContext(), R.string.login_session_rejected);
                return;
            }
            if (gate == ProfileRepository.PROFILE_OFFLINE) {
                GameNotice.show(requireContext(), R.string.server_unavailable_title,
                        getString(R.string.server_unavailable_message));
                return;
            }
            if (gate == ProfileRepository.PROFILE_NEEDED) {
                NavHostFragment.findNavController(LoginFragment.this)
                        .navigate(R.id.action_login_to_profileSetup);
            } else {
                NavHostFragment.findNavController(LoginFragment.this)
                        .navigate(R.id.action_login_to_dashboard);
            }
        }));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
