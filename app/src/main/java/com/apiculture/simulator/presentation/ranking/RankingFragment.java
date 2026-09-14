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
import com.apiculture.simulator.presentation.common.SimpleViewModelFactory;
import com.google.android.material.chip.ChipGroup;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.List;

public class RankingFragment extends Fragment {

    private FragmentRankingBinding binding;
    private RankingViewModel viewModel;
    private final RankingAdapter adapter = new RankingAdapter();

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

        viewModel.rows().observe(getViewLifecycleOwner(), this::onRows);
        viewModel.loading().observe(getViewLifecycleOwner(), this::onLoading);
        viewModel.error().observe(getViewLifecycleOwner(), msg -> {
            if (msg != null && !msg.isEmpty()) {
                GameNotice.show(requireContext(), getString(R.string.ranking_error, msg));
            }
        });

        binding.chipGroupMetric.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == View.NO_ID) {
                return;
            }
            if (checkedId == R.id.chip_level) {
                viewModel.setMetric(LeaderboardRepository.Metric.LEVEL);
            } else if (checkedId == R.id.chip_honey) {
                viewModel.setMetric(LeaderboardRepository.Metric.HONEY);
            } else if (checkedId == R.id.chip_hives) {
                viewModel.setMetric(LeaderboardRepository.Metric.HIVES);
            } else if (checkedId == R.id.chip_bees) {
                viewModel.setMetric(LeaderboardRepository.Metric.BEES);
            }
        });

        binding.btnRefreshRanking.setOnClickListener(v -> refreshRankingAndPublish());

        refreshRankingAndPublish();
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
