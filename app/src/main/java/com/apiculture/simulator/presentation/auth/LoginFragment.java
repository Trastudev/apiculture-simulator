package com.apiculture.simulator.presentation.auth;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.apiculture.simulator.ApicultureApp;
import com.apiculture.simulator.R;
import com.apiculture.simulator.databinding.FragmentLoginBinding;
import com.apiculture.simulator.presentation.common.SimpleViewModelFactory;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class LoginFragment extends Fragment {

    private FragmentLoginBinding binding;
    private AuthViewModel viewModel;

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
        viewModel.error().observe(getViewLifecycleOwner(), message ->
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show());

        binding.btnLogin.setOnClickListener(v -> submit(false));
        binding.tvRegisterLink.setOnClickListener(v -> submit(true));
        binding.btnGoogle.setOnClickListener(v ->
                Toast.makeText(requireContext(), R.string.login_google_soon, Toast.LENGTH_SHORT).show());
    }

    private void navigateAfterAuth() {
        ApicultureApp app = (ApicultureApp) requireActivity().getApplication();
        FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
        if (u == null) {
            NavHostFragment.findNavController(LoginFragment.this).navigate(R.id.action_login_to_dashboard);
            return;
        }
        app.getProfileRepository().fetchProfileComplete(u.getUid(), complete -> requireActivity().runOnUiThread(() -> {
            if (Boolean.TRUE.equals(complete)) {
                NavHostFragment.findNavController(LoginFragment.this).navigate(R.id.action_login_to_dashboard);
            } else {
                NavHostFragment.findNavController(LoginFragment.this).navigate(R.id.action_login_to_profileSetup);
            }
        }));
    }

    private void submit(boolean register) {
        String email = binding.etEmail.getText().toString().trim();
        String password = binding.etPassword.getText().toString().trim();
        if (TextUtils.isEmpty(email) || TextUtils.isEmpty(password)) {
            Toast.makeText(requireContext(), "Email y contraseña obligatorios", Toast.LENGTH_SHORT).show();
            return;
        }
        if (register) viewModel.register(email, password);
        else viewModel.login(email, password);
    }
}
