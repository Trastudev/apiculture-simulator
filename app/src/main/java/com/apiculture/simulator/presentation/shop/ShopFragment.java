package com.apiculture.simulator.presentation.shop;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import com.apiculture.simulator.presentation.common.GameNotice;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.apiculture.simulator.R;
import com.apiculture.simulator.databinding.FragmentShopBinding;
import com.apiculture.simulator.domain.game.HiveCareRules;
import com.apiculture.simulator.data.session.PlayerAuth;

public class ShopFragment extends Fragment {

    private FragmentShopBinding binding;
    private ShopViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentShopBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(ShopViewModel.class);
        binding.btnShopBack.setOnClickListener(v -> Navigation.findNavController(view).popBackStack());
        binding.tvShopTreatPrice.setText(getString(R.string.shop_price_eur, HiveCareRules.TREAT_EUR));
        binding.tvShopFeedPrice.setText(getString(R.string.shop_price_eur, HiveCareRules.FEED_7_DAYS_EUR));
        binding.tvShopQueenPrice.setText(getString(R.string.shop_queen_price,
                HiveCareRules.QUEEN_EUR,
                HiveCareRules.QUEEN_QUALITY_MIN,
                HiveCareRules.QUEEN_QUALITY_MAX));
        viewModel.stock().observe(getViewLifecycleOwner(), this::bindStock);
        String uid = PlayerAuth.getInstance().getUid();
        binding.btnBuyTreat.setOnClickListener(v ->
                viewModel.buyTreatment(uid, msg -> toastBuy(msg, R.string.shop_bought_treat)));
        binding.btnBuyFeed.setOnClickListener(v ->
                viewModel.buyFeed(uid, msg -> toastBuy(msg, R.string.shop_bought_feed)));
        binding.btnBuyQueen.setOnClickListener(v ->
                viewModel.buyQueen(uid, this::toastQueen));
        double shopLat = Double.NaN;
        double shopLng = Double.NaN;
        Bundle args = getArguments();
        if (args != null && args.containsKey("shopLat")) {
            shopLat = args.getDouble("shopLat");
            shopLng = args.getDouble("shopLng", Double.NaN);
        }
        final double buyLat = shopLat;
        final double buyLng = shopLng;
        binding.tvShopTruckPrice.setText(getString(R.string.shop_price_eur,
                (double) com.apiculture.simulator.domain.game.FleetRules.purchaseCostB(
                        com.apiculture.simulator.domain.game.FleetRules.Kind.TRUCK)));
        binding.tvShopShipPrice.setText(getString(R.string.shop_price_eur,
                (double) com.apiculture.simulator.domain.game.FleetRules.purchaseCostB(
                        com.apiculture.simulator.domain.game.FleetRules.Kind.SHIP)));
        bindFleetOwned(uid);
        boolean onlyTruck = com.apiculture.simulator.presentation.tutorial.TutorialBus.onlyTruck();
        if (onlyTruck) {
            binding.btnShopBack.setEnabled(false);
            binding.btnBuyTreat.setEnabled(false);
            binding.btnBuyFeed.setEnabled(false);
            binding.btnBuyQueen.setEnabled(false);
            binding.btnBuyShip.setEnabled(false);
        }
        binding.btnBuyTruck.setOnClickListener(v ->
                com.apiculture.simulator.presentation.hive.FleetDialogs.buyTruck(this, uid, buyLat, buyLng));
        binding.btnBuyShip.setOnClickListener(v ->
                com.apiculture.simulator.presentation.hive.FleetDialogs.buyShip(this, uid));
    }

    @Override
    public void onResume() {
        super.onResume();
        if (binding != null) {
            bindFleetOwned(PlayerAuth.getInstance().getUid());
        }
    }

    private void bindFleetOwned(@Nullable String uid) {
        if (binding == null) {
            return;
        }
        int trucks = 0;
        int ships = 0;
        for (com.apiculture.simulator.data.repository.FleetStore.Vehicle v
                : com.apiculture.simulator.data.repository.FleetStore.vehicles(requireContext(), uid)) {
            if (v.isTruck()) {
                trucks++;
            } else {
                ships++;
            }
        }
        binding.tvShopTruckOwned.setText(getString(R.string.shop_owned, trucks));
        binding.tvShopShipOwned.setText(getString(R.string.shop_owned, ships));
    }

    private void bindStock(ShopViewModel.ShopStock s) {
        if (binding == null || s == null) {
            return;
        }
        binding.tvShopBalance.setText(getString(R.string.shop_balance, s.balance));
        binding.tvShopTreatOwned.setText(getString(R.string.shop_owned, s.treatments));
        binding.tvShopFeedOwned.setText(getString(R.string.shop_owned, s.feed));
        binding.tvShopQueenOwned.setText(getString(R.string.shop_owned, s.queens.size()));
        if (s.queens.isEmpty()) {
            binding.tvShopQueenList.setText(R.string.shop_queens_empty);
        } else {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < s.queens.size(); i++) {
                if (i > 0) {
                    sb.append('\n');
                }
                sb.append(getString(R.string.shop_queen_row, i + 1, s.queens.get(i)));
            }
            binding.tvShopQueenList.setText(sb.toString());
        }
    }

    private void toastBuy(String err, int okRes) {
        if (err != null) {
            GameNotice.show(requireContext(), err);
        } else {
            GameNotice.showSuccess(requireContext(), okRes);
        }
    }

    private void toastQueen(String msg) {
        if (msg == null) {
            GameNotice.showSuccess(requireContext(), R.string.shop_bought_queen);
            return;
        }
        if (msg.startsWith("QUEEN:")) {
            try {
                int q = Integer.parseInt(msg.substring(6));
                GameNotice.showSuccess(requireContext(),
                        getString(R.string.shop_bought_queen_quality, q));
                return;
            } catch (NumberFormatException ignored) {
            }
        }
        GameNotice.show(requireContext(), msg);
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }
}
