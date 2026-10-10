package com.apiculture.simulator.presentation.shop;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.apiculture.simulator.data.repository.FleetStore;
import com.apiculture.simulator.databinding.IncludeShopQtyBinding;
import com.apiculture.simulator.domain.game.FleetRules;
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
import com.apiculture.simulator.presentation.hive.FleetDialogs;
import com.apiculture.simulator.presentation.tutorial.TutorialBus;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

public class ShopFragment extends Fragment {

    private FragmentShopBinding binding;
    private ShopViewModel viewModel;
    private int treatQty = 1;
    private int feedQty = 1;
    private int queenQty = 1;
    private double balance;

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
        wireQty(binding.qtyTreat, binding.tvShopTreatWarn, () -> treatQty, q -> treatQty = q, HiveCareRules.TREAT_EUR);
        wireQty(binding.qtyFeed, binding.tvShopFeedWarn, () -> feedQty, q -> feedQty = q, HiveCareRules.FEED_7_DAYS_EUR);
        wireQty(binding.qtyQueen, binding.tvShopQueenWarn, () -> queenQty, q -> queenQty = q, HiveCareRules.QUEEN_EUR);
        binding.btnBuyTreat.setOnClickListener(v ->
                viewModel.buyTreatment(uid, treatQty, msg -> toastUnits(msg, treatQty, R.string.shop_bought_treat)));
        binding.btnBuyFeed.setOnClickListener(v ->
                viewModel.buyFeed(uid, feedQty, msg -> toastUnits(msg, feedQty, R.string.shop_bought_feed)));
        binding.btnBuyQueen.setOnClickListener(v ->
                viewModel.buyQueen(uid, queenQty, this::toastQueen));
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
                (double) FleetRules.purchaseCostB(
                        FleetRules.Kind.TRUCK)));
        binding.tvShopShipPrice.setText(getString(R.string.shop_price_eur,
                (double) FleetRules.purchaseCostB(
                        FleetRules.Kind.SHIP)));
        bindFleetOwned(uid);
        boolean onlyTruck = TutorialBus.onlyTruck();
        if (onlyTruck) {
            binding.btnShopBack.setEnabled(false);
            binding.btnBuyTreat.setEnabled(false);
            binding.btnBuyFeed.setEnabled(false);
            binding.btnBuyQueen.setEnabled(false);
            binding.btnBuyShip.setEnabled(false);
            setQtyEnabled(binding.qtyTreat, false);
            setQtyEnabled(binding.qtyFeed, false);
            setQtyEnabled(binding.qtyQueen, false);
        }
        binding.btnBuyTruck.setOnClickListener(v ->
                FleetDialogs.buyTruck(this, uid, buyLat, buyLng));
        binding.btnBuyShip.setOnClickListener(v ->
                FleetDialogs.buyShip(this, uid));
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
        for (FleetStore.Vehicle v
                : FleetStore.vehicles(requireContext(), uid)) {
            if (v.isTruck()) {
                trucks++;
            } else {
                ships++;
            }
        }
        binding.tvShopTruckOwned.setText(getString(R.string.shop_owned, trucks));
        binding.tvShopShipOwned.setText(getString(R.string.shop_owned, ships));
    }

    private void bindVehicleWarnings() {
        if (binding == null) {
            return;
        }
        double truckCost = FleetRules.purchaseCostB(FleetRules.Kind.TRUCK);
        double shipCost = FleetRules.purchaseCostB(FleetRules.Kind.SHIP);
        binding.tvShopTruckWarn.setVisibility(balance < truckCost ? View.VISIBLE : View.GONE);
        binding.tvShopShipWarn.setVisibility(balance < shipCost ? View.VISIBLE : View.GONE);
    }

    private void bindStock(ShopViewModel.ShopStock s) {
        if (binding == null || s == null) {
            return;
        }
        balance = s.balance;
        binding.tvShopBalance.setText(getString(R.string.shop_balance, s.balance));
        treatQty = clampQty(treatQty, maxUnits(HiveCareRules.TREAT_EUR));
        feedQty = clampQty(feedQty, maxUnits(HiveCareRules.FEED_7_DAYS_EUR));
        queenQty = clampQty(queenQty, maxUnits(HiveCareRules.QUEEN_EUR));
        paintQty(binding.qtyTreat, binding.tvShopTreatWarn, treatQty, maxUnits(HiveCareRules.TREAT_EUR), HiveCareRules.TREAT_EUR);
        paintQty(binding.qtyFeed, binding.tvShopFeedWarn, feedQty, maxUnits(HiveCareRules.FEED_7_DAYS_EUR), HiveCareRules.FEED_7_DAYS_EUR);
        paintQty(binding.qtyQueen, binding.tvShopQueenWarn, queenQty, maxUnits(HiveCareRules.QUEEN_EUR), HiveCareRules.QUEEN_EUR);
        bindVehicleWarnings();
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

    private static int clampQty(int qty, int max) {
        if (max <= 0) {
            return 0;
        }
        return Math.min(Math.max(1, qty), max);
    }

    private int maxUnits(double unitPrice) {
        if (unitPrice <= 0) {
            return 0;
        }
        return (int) Math.floor(balance / unitPrice);
    }

    private void wireQty(IncludeShopQtyBinding row, TextView tvWarn,
                         IntSupplier read, IntConsumer write, double unitPrice) {
        row.sliderShopQty.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser) {
                int max = maxUnits(unitPrice);
                int limit = Math.max(0, max);
                int q = limit <= 0 ? 0 : Math.min(Math.max(1, (int) value), limit);
                write.accept(q);
                row.tvShopQty.setText(String.valueOf(q));
                boolean tutorial = TutorialBus.onlyTruck();
                row.btnShopLess.setEnabled(!tutorial && q > 1);
                row.btnShopMore.setEnabled(!tutorial && q < max);
            }
        });

        row.btnShopLess.setOnClickListener(v -> {
            int max = maxUnits(unitPrice);
            if (max <= 0) {
                write.accept(0);
                paintQty(row, tvWarn, 0, 0, unitPrice);
                return;
            }
            int next = Math.max(1, read.getAsInt() - 1);
            write.accept(next);
            paintQty(row, tvWarn, next, max, unitPrice);
        });

        row.btnShopMore.setOnClickListener(v -> {
            int max = maxUnits(unitPrice);
            if (max <= 0) {
                write.accept(0);
                paintQty(row, tvWarn, 0, 0, unitPrice);
                return;
            }
            int next = Math.min(max, read.getAsInt() + 1);
            write.accept(next);
            paintQty(row, tvWarn, next, max, unitPrice);
        });
    }

    private void paintQty(IncludeShopQtyBinding row, TextView tvWarn,
                          int qty, int max, double unitPrice) {
        boolean insufficient = balance < unitPrice;
        if (tvWarn != null) {
            tvWarn.setVisibility(insufficient ? View.VISIBLE : View.GONE);
        }

        int shown = insufficient || max <= 0 ? 0 : Math.min(Math.max(1, qty), max);
        boolean tutorial = TutorialBus.onlyTruck();
        boolean enabled = !tutorial && !insufficient && max >= 1;

        if (max > 1) {
            row.sliderShopQty.setValueFrom(1f);
            row.sliderShopQty.setValueTo((float) max);
            row.sliderShopQty.setStepSize(1f);
            row.sliderShopQty.setValue((float) shown);
        } else if (max == 1) {
            row.sliderShopQty.setValueFrom(0f);
            row.sliderShopQty.setValueTo(2f);
            row.sliderShopQty.setStepSize(1f);
            row.sliderShopQty.setValue(1f);
        } else {
            row.sliderShopQty.setValueFrom(0f);
            row.sliderShopQty.setValueTo(1f);
            row.sliderShopQty.setStepSize(1f);
            row.sliderShopQty.setValue(0f);
        }

        row.sliderShopQty.setEnabled(enabled);
        row.tvShopQty.setText(String.valueOf(shown));
        row.btnShopLess.setEnabled(enabled && shown > 1);
        row.btnShopMore.setEnabled(enabled && shown < max);
    }

    private void setQtyEnabled(IncludeShopQtyBinding row,
                               boolean enabled) {
        row.sliderShopQty.setEnabled(enabled);
        row.btnShopLess.setEnabled(enabled);
        row.btnShopMore.setEnabled(enabled);
    }

    private void toastUnits(String err, int count, int oneRes) {
        if (err != null) {
            GameNotice.show(requireContext(), err);
        } else if (count > 1) {
            GameNotice.showSuccess(requireContext(), getString(R.string.shop_bought_units, count));
        } else {
            GameNotice.showSuccess(requireContext(), oneRes);
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
        if (msg.startsWith("QUEENS:")) {
            try {
                int n = Integer.parseInt(msg.substring(7));
                GameNotice.showSuccess(requireContext(), getString(R.string.shop_bought_units, n));
                return;
            } catch (NumberFormatException ignored) {
            }
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
