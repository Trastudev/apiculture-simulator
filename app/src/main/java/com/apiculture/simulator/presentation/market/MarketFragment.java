package com.apiculture.simulator.presentation.market;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;

import com.apiculture.simulator.ApicultureApp;
import com.apiculture.simulator.R;
import com.apiculture.simulator.databinding.FragmentMarketBinding;
import com.apiculture.simulator.domain.game.GameCalendar;
import com.apiculture.simulator.presentation.common.SimpleViewModelFactory;

import java.time.LocalDate;
import java.time.ZoneId;

public class MarketFragment extends Fragment {
    private FragmentMarketBinding binding;
    private MarketViewModel viewModel;
    private MarketPillsAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentMarketBinding.inflate(inflater, container, false);
        ApicultureApp app = (ApicultureApp) requireActivity().getApplication();

        viewModel = new ViewModelProvider(this,
                new SimpleViewModelFactory<>(() -> new MarketViewModel(
                        app.getEconomyRepository(), app.getMarketRepository())))
                .get(MarketViewModel.class);

        adapter = new MarketPillsAdapter(requireContext(), new MarketPillsAdapter.Listener() {
            @Override
            public void onSell5(String floraKey) {
                viewModel.sellFloraKg(floraKey, 5.0, result -> toastSellResult(5.0, result));
            }

            @Override
            public void onSellAll(String floraKey, double stockKg) {
                viewModel.sellFloraKg(floraKey, stockKg, result -> toastSellResult(stockKg, result));
            }
        });
        binding.recyclerMarketPills.setLayoutManager(new GridLayoutManager(requireContext(), 3));
        binding.recyclerMarketPills.setAdapter(adapter);

        viewModel.uiState().observe(getViewLifecycleOwner(), state -> {
            if (state == null) {
                return;
            }
            binding.tvMarketSummary.setText(requireContext().getString(R.string.market_summary_line,
                    state.balanceEur, state.totalHoneyKg));
            adapter.setPills(state.pills);
        });

        return binding.getRoot();
    }

    private void toastSellResult(double kg, MarketSellResult result) {
        if (result.success) {
            Toast.makeText(requireContext(),
                    getString(R.string.market_sell_ok, kg, result.unitPriceEurPerKg),
                    Toast.LENGTH_SHORT).show();
        } else if ("stock".equals(result.errorMessage)) {
            Toast.makeText(requireContext(), R.string.market_sell_fail_stock, Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(requireContext(),
                    getString(R.string.market_sell_fail_reason, result.errorMessage),
                    Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        ApicultureApp app = (ApicultureApp) requireActivity().getApplication();
        ZoneId z = GameCalendar.userTimeZone();
        LocalDate today = LocalDate.now(z);
        app.getMarketRepository().refreshGlobalMarketForDay(
                GameCalendar.toDayKey(today), today.getDayOfYear());
        viewModel.attachGlobalSalesStream();
    }

    @Override
    public void onPause() {
        viewModel.detachGlobalSalesStream();
        super.onPause();
    }
}
