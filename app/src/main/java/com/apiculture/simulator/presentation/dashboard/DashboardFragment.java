package com.apiculture.simulator.presentation.dashboard;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.google.android.material.button.MaterialButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;

import com.apiculture.simulator.ApicultureApp;
import com.apiculture.simulator.BuildConfig;
import com.apiculture.simulator.R;
import com.apiculture.simulator.databinding.FragmentDashboardBinding;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class DashboardFragment extends Fragment {

    private FragmentDashboardBinding binding;
    private DashboardViewModel dashboardViewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentDashboardBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (dashboardViewModel != null) {
            FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
            if (u != null) {
                dashboardViewModel.bindHivesForUser(u.getUid());
            }
            dashboardViewModel.refreshEconomyDisplay();
            dashboardViewModel.refreshSwarmRiskBannerNow();
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        dashboardViewModel = new ViewModelProvider(this).get(DashboardViewModel.class);
        DashboardViewModel viewModel = dashboardViewModel;
        FirebaseUser sessionUser = FirebaseAuth.getInstance().getCurrentUser();
        if (sessionUser != null) {
            viewModel.bindHivesForUser(sessionUser.getUid());
        }

        viewModel.statCoins().observe(getViewLifecycleOwner(), binding.tvStatCoins::setText);
        viewModel.statHoney().observe(getViewLifecycleOwner(), binding.tvStatHoney::setText);
        viewModel.statNectar().observe(getViewLifecycleOwner(), binding.tvStatNectar::setText);
        viewModel.seasonText().observe(getViewLifecycleOwner(), binding.tvSeason::setText);
        viewModel.dayText().observe(getViewLifecycleOwner(), binding.tvDay::setText);
        viewModel.profileName().observe(getViewLifecycleOwner(), binding.tvProfileName::setText);
        viewModel.headerHoneyBrand().observe(getViewLifecycleOwner(), binding.tvHeaderBrand::setText);
        viewModel.profileSubtitle().observe(getViewLifecycleOwner(), binding.tvProfileSubtitle::setText);
        viewModel.xpLabel().observe(getViewLifecycleOwner(), binding.tvXpLabel::setText);
        viewModel.xpMax().observe(getViewLifecycleOwner(), max -> {
            if (max != null) {
                binding.progressXp.setMax(max);
            }
        });
        viewModel.xpCurrent().observe(getViewLifecycleOwner(), cur -> {
            if (cur != null) {
                binding.progressXp.setProgress(cur);
            }
        });
        viewModel.weatherTemp().observe(getViewLifecycleOwner(), binding.tvWeatherTemp::setText);
        viewModel.weatherHint().observe(getViewLifecycleOwner(), binding.tvWeatherHint::setText);
        viewModel.weatherLocation().observe(getViewLifecycleOwner(), binding.tvWeatherLocation::setText);
        viewModel.summaryText().observe(getViewLifecycleOwner(), binding.tvSummary::setText);
        viewModel.eventTitle().observe(getViewLifecycleOwner(), binding.tvEventTitle::setText);
        viewModel.eventSubtitle().observe(getViewLifecycleOwner(), binding.tvEventSubtitle::setText);
        viewModel.showSplitHiveHint().observe(getViewLifecycleOwner(), show -> {
            binding.tvSplitHiveHint.setVisibility(Boolean.TRUE.equals(show) ? View.VISIBLE : View.GONE);
        });

        viewModel.honeyCapBanner().observe(getViewLifecycleOwner(), honey -> {
            if (honey == null) {
                binding.cardHoneyCap.setVisibility(View.GONE);
                binding.llHoneyCapHives.removeAllViews();
            } else {
                binding.cardHoneyCap.setVisibility(View.VISIBLE);
                binding.tvHoneyCapTitle.setText(honey.title);
                binding.llHoneyCapHives.removeAllViews();
                LayoutInflater inflater = LayoutInflater.from(requireContext());
                NavController nav = Navigation.findNavController(requireActivity(), R.id.nav_host_fragment);
                for (DashboardViewModel.HoneyCapHiveRow row : honey.hiveRows) {
                    if (row == null || row.hiveId == null) {
                        continue;
                    }
                    MaterialButton b = (MaterialButton) inflater.inflate(R.layout.item_honey_cap_link,
                            binding.llHoneyCapHives, false);
                    b.setText(row.displayName != null ? row.displayName : "");
                    b.setContentDescription(row.displayName);
                    final String hid = row.hiveId;
                    b.setOnClickListener(v -> {
                        Bundle args = new Bundle();
                        args.putString("hiveId", hid);
                        nav.navigate(R.id.hiveDetailFragment, args);
                    });
                    binding.llHoneyCapHives.addView(b);
                }
            }
        });

        viewModel.swarmRiskBanner().observe(getViewLifecycleOwner(), banner -> {
            if (banner == null) {
                binding.cardSwarmRisk.setVisibility(View.GONE);
                binding.llSwarmRiskHives.removeAllViews();
            } else {
                binding.cardSwarmRisk.setVisibility(View.VISIBLE);
                binding.tvSwarmTitle.setText(banner.title);
                binding.llSwarmRiskHives.removeAllViews();
                LayoutInflater inflater = LayoutInflater.from(requireContext());
                NavController nav = Navigation.findNavController(requireActivity(), R.id.nav_host_fragment);
                for (DashboardViewModel.SwarmRiskHiveRow row : banner.hiveRows) {
                    if (row == null || row.hiveId == null) {
                        continue;
                    }
                    MaterialButton b = (MaterialButton) inflater.inflate(R.layout.item_swarm_risk_link,
                            binding.llSwarmRiskHives, false);
                    b.setText(row.displayName != null ? row.displayName : "");
                    final String hid = row.hiveId;
                    b.setOnClickListener(v -> {
                        Bundle args = new Bundle();
                        args.putString("hiveId", hid);
                        nav.navigate(R.id.hiveDetailFragment, args);
                    });
                    binding.llSwarmRiskHives.addView(b);
                }
            }
        });

        if (sessionUser != null && sessionUser.getDisplayName() != null
                && !sessionUser.getDisplayName().isEmpty()) {
            binding.tvProfileName.setText(sessionUser.getDisplayName());
        }

        NavController nav = Navigation.findNavController(view);
        binding.tileQuickHives.setOnClickListener(v -> nav.navigate(R.id.hivesFragment));
        binding.tileQuickMap.setOnClickListener(v -> nav.navigate(R.id.mapFragment));
        binding.tileQuickHarvest.setOnClickListener(v ->
                Toast.makeText(requireContext(), R.string.dashboard_action_soon, Toast.LENGTH_SHORT).show());
        binding.tileQuickTranshumance.setOnClickListener(v ->
                Toast.makeText(requireContext(), R.string.dashboard_action_soon, Toast.LENGTH_SHORT).show());
        binding.tileQuickMarket.setOnClickListener(v -> nav.navigate(R.id.marketFragment));
        binding.tileQuickRanking.setOnClickListener(v -> nav.navigate(R.id.rankingFragment));
        binding.btnTreatEvent.setOnClickListener(v -> nav.navigate(R.id.eventsFragment));

        binding.btnDebugSimulateDay.setVisibility(BuildConfig.DEBUG ? View.VISIBLE : View.GONE);
        if (BuildConfig.DEBUG && sessionUser != null) {
            binding.btnDebugSimulateDay.setOnClickListener(v ->
                    viewModel.debugSimulateNextProductionDay(sessionUser.getUid(), msg -> {
                        Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show();
                        // Room puede agrupar emisiones; forzamos el mismo refresco que en onResume.
                        viewModel.refreshSwarmRiskBannerNow();
                    }));
        }

        binding.btnDashboardSettings.setOnClickListener(v -> {
            PopupMenu popup = new PopupMenu(requireContext(), v);
            popup.getMenuInflater().inflate(R.menu.menu_dashboard_settings, popup.getMenu());
            popup.setOnMenuItemClickListener(item -> onDashboardSettingsItem(item, sessionUser, viewModel));
            popup.show();
        });
    }

    private boolean onDashboardSettingsItem(@NonNull MenuItem item, @Nullable FirebaseUser sessionUser,
                                            @NonNull DashboardViewModel viewModel) {
        if (item.getItemId() != R.id.action_reset_game) {
            return false;
        }
        if (sessionUser == null) {
            Toast.makeText(requireContext(), R.string.dashboard_reset_error, Toast.LENGTH_SHORT).show();
            return true;
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.dashboard_reset_confirm_title)
                .setMessage(R.string.dashboard_reset_confirm_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (d, w) -> viewModel.resetGameToStarterState(
                        sessionUser.getUid(),
                        msg -> {
                            if (msg == null) {
                                Toast.makeText(requireContext(), R.string.dashboard_reset_done,
                                        Toast.LENGTH_LONG).show();
                                ApicultureApp app = (ApicultureApp) requireActivity().getApplication();
                                app.getHiveRepository().startRealtimeCloudSync(sessionUser.getUid());
                                app.getHexParcelRepository().startRealtimeCloudSync();
                                viewModel.refreshEconomyDisplay();
                            } else {
                                Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show();
                            }
                        }))
                .show();
        return true;
    }
}
