package com.apiculture.simulator.presentation.ranking;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.apiculture.simulator.presentation.common.GameNotice;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.apiculture.simulator.ApicultureApp;
import com.apiculture.simulator.R;
import com.apiculture.simulator.data.repository.LeaderboardRepository;
import com.apiculture.simulator.databinding.FragmentRankingBinding;
import com.apiculture.simulator.domain.parcel.HexFlora;
import com.apiculture.simulator.presentation.common.SimpleViewModelFactory;
import com.google.android.material.chip.Chip;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RankingFragment extends Fragment {

    private FragmentRankingBinding binding;
    private RankingViewModel viewModel;
    private final RankingAdapter adapter = new RankingAdapter();
    private final Map<Integer, String> floraChipKeys = new HashMap<>();
    private boolean suppressChipCallbacks;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentRankingBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ApicultureApp app = (ApicultureApp) requireActivity().getApplication();
        viewModel = new ViewModelProvider(this,
                new SimpleViewModelFactory<>(() -> new RankingViewModel(app.getLeaderboardRepository())))
                .get(RankingViewModel.class);

        binding.rvRanking.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvRanking.setAdapter(adapter);

        buildFloraChips();

        viewModel.rows().observe(getViewLifecycleOwner(), this::onRows);
        viewModel.loading().observe(getViewLifecycleOwner(), this::onLoading);
        viewModel.error().observe(getViewLifecycleOwner(), msg -> {
            if (msg != null && !msg.isEmpty()) {
                GameNotice.show(requireContext(), getString(R.string.ranking_error, msg));
            }
        });
        viewModel.metric().observe(getViewLifecycleOwner(), this::onMetricChanged);

        binding.chipGroupRegion.setOnCheckedChangeListener((group, checkedId) -> {
            if (suppressChipCallbacks || checkedId == View.NO_ID) {
                return;
            }
            if (checkedId == R.id.chip_region_global) {
                viewModel.setRegionFilter(null);
                binding.tvRankingTitle.setText(R.string.ranking_title_global);
            } else if (checkedId == R.id.chip_region_iberia) {
                viewModel.setRegionFilter(LeaderboardRepository.REGION_IBERIA);
                binding.tvRankingTitle.setText(R.string.ranking_title_iberia);
            } else if (checkedId == R.id.chip_region_za) {
                viewModel.setRegionFilter(LeaderboardRepository.REGION_ZA);
                binding.tvRankingTitle.setText(R.string.ranking_title_za);
            }
        });

        binding.chipGroupMetric.setOnCheckedChangeListener((group, checkedId) -> {
            if (suppressChipCallbacks || checkedId == View.NO_ID) {
                return;
            }
            if (checkedId == R.id.chip_level) {
                viewModel.setMetric(LeaderboardRepository.Metric.LEVEL);
            } else if (checkedId == R.id.chip_honey) {
                viewModel.setMetric(LeaderboardRepository.Metric.HONEY_SOLD);
            } else if (checkedId == R.id.chip_hives) {
                viewModel.setMetric(LeaderboardRepository.Metric.HIVES);
            } else if (checkedId == R.id.chip_bees) {
                viewModel.setMetric(LeaderboardRepository.Metric.BEES);
            }
        });

        binding.chipGroupFlora.setOnCheckedChangeListener((group, checkedId) -> {
            if (suppressChipCallbacks || checkedId == View.NO_ID) {
                return;
            }
            if (checkedId == R.id.chip_flora_total) {
                viewModel.setFloraFilter(null);
            } else {
                String key = floraChipKeys.get(checkedId);
                viewModel.setFloraFilter(key);
            }
        });

        binding.btnRefreshRanking.setOnClickListener(v -> refreshRankingAndPublish());

        refreshRankingAndPublish();
    }

    private void buildFloraChips() {
        floraChipKeys.clear();
        // Mantener chip total; añadir una chip por flora
        for (String flora : HexFlora.FLORA_TYPES) {
            Chip chip = new Chip(requireContext());
            chip.setId(View.generateViewId());
            chip.setText(flora);
            chip.setCheckable(true);
            chip.setChecked(false);
            chip.setEnsureMinTouchTargetSize(false);
            binding.chipGroupFlora.addView(chip);
            floraChipKeys.put(chip.getId(), flora);
        }
    }

    private void onMetricChanged(LeaderboardRepository.Metric metric) {
        boolean honey = metric == LeaderboardRepository.Metric.HONEY_SOLD;
        binding.scrollFlora.setVisibility(honey ? View.VISIBLE : View.GONE);
        if (honey) {
            suppressChipCallbacks = true;
            binding.chipFloraTotal.setChecked(true);
            suppressChipCallbacks = false;
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        publishSelfIfLoggedIn();
    }

    private void refreshRankingAndPublish() {
        publishSelfIfLoggedIn();
        viewModel.fetchRanking();
    }

    private void publishSelfIfLoggedIn() {
        FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
        if (u != null) {
            ((ApicultureApp) requireActivity().getApplication()).getLeaderboardRepository()
                    .enqueuePublish(u.getUid());
        }
    }

    private void onLoading(Boolean loading) {
        boolean show = Boolean.TRUE.equals(loading);
        binding.progressRanking.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void onRows(List<com.apiculture.simulator.data.remote.RankingEntry> list) {
        adapter.submit(list);
        boolean empty = list == null || list.isEmpty();
        binding.tvRankingEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.rvRanking.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
