package com.apiculture.simulator.presentation.ranking;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.apiculture.simulator.ApicultureApp;
import com.apiculture.simulator.databinding.FragmentRankingBinding;
import com.apiculture.simulator.presentation.common.SimpleViewModelFactory;

public class RankingFragment extends Fragment {

    private FragmentRankingBinding binding;

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
        RankingViewModel viewModel = new ViewModelProvider(this,
                new SimpleViewModelFactory<>(() -> new RankingViewModel(app.getMultiplayerRepository())))
                .get(RankingViewModel.class);

        viewModel.scores().observe(getViewLifecycleOwner(), scores -> {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < scores.size(); i++) {
                var score = scores.get(i);
                sb.append(i + 1)
                        .append(". ")
                        .append(score.nickname)
                        .append(" - ")
                        .append(String.format("%.2f", score.totalHoneyKg))
                        .append(" kg\n");
            }
            binding.tvRanking.setText(sb.length() == 0 ? "Sin datos de ranking todavía" : sb.toString());
        });
        viewModel.error().observe(getViewLifecycleOwner(), msg ->
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show());

        binding.btnRefreshRanking.setOnClickListener(v -> viewModel.fetchRanking());
        viewModel.fetchRanking();
    }
}
