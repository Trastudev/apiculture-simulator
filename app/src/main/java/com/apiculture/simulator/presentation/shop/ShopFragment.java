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
import com.google.firebase.auth.FirebaseAuth;

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
        binding.tvShopTreatPrice.setText(getString(R.string.shop_price_eur, (int) HiveCareRules.TREAT_EUR));
        binding.tvShopFeedPrice.setText(getString(R.string.shop_price_eur, (int) HiveCareRules.FEED_1_DAY_EUR));
        binding.tvShopQueenPrice.setText(getString(R.string.shop_queen_price,
                (int) HiveCareRules.QUEEN_EUR,
                HiveCareRules.QUEEN_QUALITY_MIN,
                HiveCareRules.QUEEN_QUALITY_MAX));
        viewModel.stock().observe(getViewLifecycleOwner(), this::bindStock);
        String uid = FirebaseAuth.getInstance().getUid();
        binding.btnBuyTreat.setOnClickListener(v ->
                viewModel.buyTreatment(uid, msg -> toastBuy(msg, R.string.shop_bought_treat)));
        binding.btnBuyFeed.setOnClickListener(v ->
                viewModel.buyFeed(uid, msg -> toastBuy(msg, R.string.shop_bought_feed)));
        binding.btnBuyQueen.setOnClickListener(v ->
                viewModel.buyQueen(uid, this::toastQueen));
    }

    private void bindStock(ShopViewModel.ShopStock s) {
        if (binding == null || s == null) {
            return;
        }
        binding.tvShopBalance.setText(getString(R.string.shop_balance, (int) Math.round(s.balance)));
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
