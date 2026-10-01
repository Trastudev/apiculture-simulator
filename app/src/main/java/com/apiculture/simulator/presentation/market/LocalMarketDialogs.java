package com.apiculture.simulator.presentation.market;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.apiculture.simulator.ApicultureApp;
import com.apiculture.simulator.R;
import com.apiculture.simulator.data.repository.EconomyRepository;
import com.apiculture.simulator.data.repository.HoneyLogistics;
import com.apiculture.simulator.data.repository.MarketRepository;
import com.apiculture.simulator.data.repository.TruckLivePrefs;
import com.apiculture.simulator.data.repository.FleetStore;
import com.apiculture.simulator.domain.game.ExoticHoneyRules;
import com.apiculture.simulator.domain.map.ProvincialMarket;
import com.apiculture.simulator.domain.map.ProvincialMarketCatalog;
import com.apiculture.simulator.domain.market.HoneyMarketEngine;
import com.apiculture.simulator.domain.market.TerritorialMarketRules;
import com.apiculture.simulator.domain.parcel.HexFlora;
import com.apiculture.simulator.data.repository.TradeAccessStore;
import com.apiculture.simulator.data.repository.GameServer;
import com.apiculture.simulator.presentation.hive.FleetDialogs;
import com.apiculture.simulator.presentation.common.GameNotice;
import com.apiculture.simulator.presentation.common.TradeGateDialogs;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class LocalMarketDialogs {

    private LocalMarketDialogs() {
    }

    public static void show(@NonNull Fragment fragment, @NonNull ProvincialMarket market) {
        if (!fragment.isAdded() || fragment.getContext() == null) {
            return;
        }
        ApicultureApp app = (ApicultureApp) fragment.requireActivity().getApplication();
        if (market.international && !TradeAccessStore.marketPaid(
                fragment.requireContext(), currentUid(), market.salesId())) {
            TradeGateDialogs.showMarket(fragment, currentUid(), market,
                    () -> show(fragment, market));
            return;
        }
        MarketRepository marketRepo = app.getMarketRepository();
        EconomyRepository economy = app.getEconomyRepository();
        List<ProvincialMarket> all = ProvincialMarketCatalog.resolve(fragment.requireContext(), market.region);
        Map<String, Double> stocks = economy.copyHoneyBuckets();
        Map<String, double[]> histories = marketRepo.last7PostedPricesByFlora();
        List<MarketPillUi> pills = new ArrayList<>();
        for (String flora : HexFlora.FLORA_TYPES) {
            String key = HoneyMarketEngine.canonicalFloraKey(flora);
            boolean exotic = ExoticHoneyRules.isExotic(market.region, key);
            if (market.international != exotic) {
                continue;
            }
            double stock = stocks.getOrDefault(key, 0.0);
            if (stock <= 1e-6) {
                continue;
            }
            double left = marketRepo.remainingCapacityKg(market, key);
            double capital = marketRepo.priceEurPerKgForFlora(key, null);
            double local = marketRepo.priceEurPerKgForFlora(key, market);
            String note = null;
            if (exotic) {
                double sold = FleetStore.exoticSoldKg(fragment.requireContext(), currentUid(), market.salesId());
                left = ExoticHoneyRules.remainingKg(sold);
                local = capital * ExoticHoneyRules.MULTIPLIER_IN_QUOTA;
                note = fragment.getString(R.string.map_market_exotic_note, left);
            } else if (capital > 1e-6) {
                double pct = (local / capital - 1.0) * 100.0;
                double competition = TerritorialMarketRules.competition01(
                        market, key, all);
                note = fragment.getString(
                        R.string.map_market_competition_note,
                        competitionBand(fragment.requireContext(), competition),
                        pct);
            }
            double[] history = histories.get(key);
            if (history == null) {
                history = new double[0];
            } else if (!exotic && capital > 1e-6) {
                double factor = local / capital;
                double[] scaled = new double[history.length];
                for (int i = 0; i < history.length; i++) {
                    scaled[i] = history[i] * factor;
                }
                history = scaled;
            }
            pills.add(new MarketPillUi(key, flora, local, stock, history, note, left));
        }

        Dialog dialog = new Dialog(fragment.requireContext());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_local_market);
        dialog.setCancelable(true);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        TextView kicker = dialog.findViewById(R.id.tv_local_kicker);
        TextView title = dialog.findViewById(R.id.tv_local_title);
        TextView hint = dialog.findViewById(R.id.tv_local_hint);
        TextView empty = dialog.findViewById(R.id.tv_local_empty);
        RecyclerView recycler = dialog.findViewById(R.id.recycler_local_pills);
        dialog.findViewById(R.id.btn_local_accept).setOnClickListener(v -> dialog.dismiss());

        android.widget.ImageView banner = dialog.findViewById(R.id.iv_local_banner);
        if (banner != null) {
            banner.setVisibility(market.international ? View.VISIBLE : View.GONE);
        }
        if (market.international) {
            kicker.setText(R.string.map_market_international_kicker);
        } else {
            kicker.setText(market.local ? R.string.map_market_local_kicker : R.string.map_market_capital_kicker);
        }
        title.setText(market.name);
        if (market.international) {
            hint.setText(fragment.getString(R.string.map_market_international_hint,
                    (int) ExoticHoneyRules.DAILY_QUOTA_KG));
        } else if (market.local) {
            hint.setText(R.string.map_market_local_competition_hint);
        } else {
            hint.setText(R.string.map_market_capital_competition_hint);
        }
        ViewGroup demandHost = dialog.findViewById(R.id.ll_local_demand);
        List<MarketDemandBar.Row> demandRows = market.international
                ? new ArrayList<>()
                : MarketDemandBar.collect(fragment.requireContext(), market);
        View demandLabel = dialog.findViewById(R.id.tv_local_demand_label);
        View demandPager = dialog.findViewById(R.id.ll_demand_pager);
        int demandVis = demandRows.isEmpty() ? View.GONE : View.VISIBLE;
        if (demandLabel != null) {
            demandLabel.setVisibility(demandVis);
        }
        if (demandHost != null) {
            demandHost.setVisibility(demandVis);
        }
        final int[] demandPage = {0};
        bindDemandPage(fragment, dialog, demandHost, demandRows, demandPage[0]);
        if (demandPager != null) {
            demandPager.setVisibility(demandRows.size() > MarketDemandBar.PAGE_SIZE
                    ? View.VISIBLE : View.GONE);
            dialog.findViewById(R.id.btn_demand_prev).setOnClickListener(v -> {
                demandPage[0] = bindDemandPage(fragment, dialog, demandHost, demandRows, demandPage[0] - 1);
            });
            dialog.findViewById(R.id.btn_demand_next).setOnClickListener(v -> {
                demandPage[0] = bindDemandPage(fragment, dialog, demandHost, demandRows, demandPage[0] + 1);
            });
        }

        if (pills.isEmpty()) {
            empty.setText(market.international
                    ? R.string.map_market_empty_exotic
                    : R.string.map_market_empty_stock);
            empty.setVisibility(View.VISIBLE);
            recycler.setVisibility(View.GONE);
            View pillsPager = dialog.findViewById(R.id.ll_pills_pager);
            if (pillsPager != null) {
                pillsPager.setVisibility(View.GONE);
            }
        } else {
            empty.setVisibility(View.GONE);
            recycler.setVisibility(View.VISIBLE);
            int span = fragment.getResources().getInteger(R.integer.market_grid_span);
            recycler.setLayoutManager(new GridLayoutManager(fragment.requireContext(), span));
            MarketPillsAdapter adapter = new MarketPillsAdapter(fragment.requireContext(), pill ->
                    MarketSellDialog.show(fragment.requireContext(), pill, kg -> {
                        dialog.dismiss();
                        confirmThenSell(fragment, market, pill, kg, app);
                    }));
            recycler.setAdapter(adapter);
            final int[] pillsPage = {0};
            int pillsPageSize = Math.max(2, span);
            bindPillsPage(fragment, dialog, adapter, pills, pillsPage[0], pillsPageSize);
            View pillsPager = dialog.findViewById(R.id.ll_pills_pager);
            if (pillsPager != null) {
                pillsPager.setVisibility(pills.size() > pillsPageSize ? View.VISIBLE : View.GONE);
                dialog.findViewById(R.id.btn_pills_prev).setOnClickListener(v -> {
                    pillsPage[0] = bindPillsPage(fragment, dialog, adapter, pills,
                            pillsPage[0] - 1, pillsPageSize);
                });
                dialog.findViewById(R.id.btn_pills_next).setOnClickListener(v -> {
                    pillsPage[0] = bindPillsPage(fragment, dialog, adapter, pills,
                            pillsPage[0] + 1, pillsPageSize);
                });
            }
        }
        dialog.show();
    }

    private static int bindDemandPage(@NonNull Fragment fragment, @NonNull Dialog dialog,
            @Nullable ViewGroup host, @NonNull List<MarketDemandBar.Row> rows, int page) {
        int pageCount = pageCount(rows.size(), MarketDemandBar.PAGE_SIZE);
        int safe = Math.max(0, Math.min(page, pageCount - 1));
        int from = safe * MarketDemandBar.PAGE_SIZE;
        int to = Math.min(rows.size(), from + MarketDemandBar.PAGE_SIZE);
        MarketDemandBar.bindPage(host, from < to ? rows.subList(from, to) : new ArrayList<>());
        TextView label = dialog.findViewById(R.id.tv_demand_page);
        if (label != null) {
            label.setText(fragment.getString(R.string.market_contracts_page, safe + 1, Math.max(1, pageCount)));
        }
        View prev = dialog.findViewById(R.id.btn_demand_prev);
        View next = dialog.findViewById(R.id.btn_demand_next);
        if (prev != null) {
            prev.setEnabled(safe > 0);
            prev.setAlpha(safe > 0 ? 1f : 0.35f);
        }
        if (next != null) {
            next.setEnabled(safe < pageCount - 1);
            next.setAlpha(safe < pageCount - 1 ? 1f : 0.35f);
        }
        return safe;
    }

    private static int bindPillsPage(@NonNull Fragment fragment, @NonNull Dialog dialog,
            @NonNull MarketPillsAdapter adapter, @NonNull List<MarketPillUi> pills, int page, int pageSize) {
        int pageCount = pageCount(pills.size(), pageSize);
        int safe = Math.max(0, Math.min(page, pageCount - 1));
        int from = safe * pageSize;
        int to = Math.min(pills.size(), from + pageSize);
        adapter.setPills(from < to ? pills.subList(from, to) : new ArrayList<>());
        TextView label = dialog.findViewById(R.id.tv_pills_page);
        if (label != null) {
            label.setText(fragment.getString(R.string.market_contracts_page, safe + 1, Math.max(1, pageCount)));
        }
        View prev = dialog.findViewById(R.id.btn_pills_prev);
        View next = dialog.findViewById(R.id.btn_pills_next);
        if (prev != null) {
            prev.setEnabled(safe > 0);
            prev.setAlpha(safe > 0 ? 1f : 0.35f);
        }
        if (next != null) {
            next.setEnabled(safe < pageCount - 1);
            next.setAlpha(safe < pageCount - 1 ? 1f : 0.35f);
        }
        return safe;
    }

    private static int pageCount(int size, int pageSize) {
        if (size <= 0 || pageSize <= 0) {
            return 1;
        }
        return (size + pageSize - 1) / pageSize;
    }

    private static String competitionBand(@NonNull android.content.Context context, double score) {
        if (score >= 0.67) {
            return context.getString(R.string.map_market_band_high);
        }
        if (score >= 0.34) {
            return context.getString(R.string.map_market_band_medium);
        }
        return context.getString(R.string.map_market_band_low);
    }

    private static void confirmThenSell(@NonNull Fragment fragment, @NonNull ProvincialMarket market,
            @NonNull MarketPillUi pill, double kg, @NonNull ApicultureApp app) {
        if (!fragment.isAdded()) {
            return;
        }
        String uid = currentUid();
        boolean live = TruckLivePrefs.isEnabled(fragment.requireContext()) || GameServer.enabled();
        if (!live) {
            HoneyLogistics.previewWholesaleAsync(fragment.requireContext(), uid, market, pill.floraKey, kg,
                    preview -> openConfirm(fragment, market, pill, kg, preview, null, app));
            return;
        }
        HoneyLogistics.saleTruckOptions(fragment.requireContext(), uid, market, pill.floraKey, kg, trucks -> {
            if (!fragment.isAdded()) {
                return;
            }
            if (trucks == null || trucks.isEmpty()) {
                GameNotice.show(fragment.requireContext(), R.string.market_sell_fail_fleet);
                return;
            }
            HoneyOrderDialogs.showTrucks(fragment, trucks, pick ->
                    HoneyLogistics.previewWholesaleAsync(fragment.requireContext(), uid, market,
                            pill.floraKey, kg, pick.truckId, preview ->
                                    openConfirm(fragment, market, pill, kg, preview, pick, app)),
                    null, R.string.market_sell_truck_subtitle);
        });
    }

    private static void openConfirm(@NonNull Fragment fragment, @NonNull ProvincialMarket market,
            @NonNull MarketPillUi pill, double kg, @NonNull HoneyLogistics.WholesalePreview preview,
            @Nullable HoneyLogistics.OrderTruckOption truck, @NonNull ApicultureApp app) {
        if (!fragment.isAdded()) {
            return;
        }
        if (preview.missingWarehouse) {
            GameNotice.show(fragment.requireContext(), R.string.map_market_no_warehouse);
            return;
        }
        showConfirm(fragment, market, pill, kg, preview, truck, () -> {
            MarketViewModel vm = new MarketViewModel(
                    fragment.requireActivity().getApplication(),
                    app.getEconomyRepository(), app.getMarketRepository());
            vm.sellFloraKg(pill.floraKey, kg, market, truck != null ? truck.truckId : null, result -> {
                if (!fragment.isAdded()) {
                    return;
                }
                toast(fragment, kg, result);
            });
        });
    }

    private static void showConfirm(@NonNull Fragment fragment, @NonNull ProvincialMarket market,
            @NonNull MarketPillUi pill, double kg, @NonNull HoneyLogistics.WholesalePreview preview,
            @Nullable HoneyLogistics.OrderTruckOption truck, @NonNull Runnable onSend) {
        Dialog dialog = new Dialog(fragment.requireContext());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_truck_confirm);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        TextView origin = dialog.findViewById(R.id.tv_truck_origin);
        TextView dest = dialog.findViewById(R.id.tv_truck_dest);
        TextView cargo = dialog.findViewById(R.id.tv_truck_cargo);
        TextView road = dialog.findViewById(R.id.tv_truck_road);
        TextView grossView = dialog.findViewById(R.id.tv_truck_gross);
        TextView travelView = dialog.findViewById(R.id.tv_truck_travel);
        TextView netView = dialog.findViewById(R.id.tv_truck_net);
        MaterialButton send = dialog.findViewById(R.id.btn_truck_send);
        MaterialButton back = dialog.findViewById(R.id.btn_truck_back);
        ImageView art = dialog.findViewById(R.id.iv_truck_confirm);
        if (art != null) {
            art.setImageResource(FleetDialogs.truckDrawable(truck != null ? truck.level : 1));
        }

        String originLabel = preview.warehouseLabel != null ? preview.warehouseLabel : "";
        if (truck != null) {
            originLabel = truck.truckLabel + " · " + originLabel;
        }
        origin.setText(fragment.getString(R.string.truck_confirm_origin, originLabel));
        dest.setText(fragment.getString(R.string.truck_confirm_dest, market.name));
        cargo.setText(fragment.getString(R.string.truck_confirm_line, pill.title, kg, pill.priceEurPerKg));
        String eta = HoneyLogistics.formatRemaining(preview.durationMs);
        if (preview.usedGeodesic) {
            road.setText(fragment.getString(R.string.truck_confirm_road_fail, preview.distanceKm, eta));
        } else {
            road.setText(fragment.getString(R.string.truck_confirm_road, preview.distanceKm, eta));
        }
        if (preview.blockReason != null) {
            road.setText(preview.blockReason);
            send.setEnabled(false);
        } else if (!preview.itinerary.isEmpty()) {
            road.setText(preview.itinerary);
        }
        if (!TruckLivePrefs.isEnabled(fragment.requireContext())) {
            road.setText(R.string.truck_confirm_instant);
            send.setText(R.string.market_sell_confirm);
            send.setEnabled(preview.blockReason == null);
        }
        double gross = Math.round(kg * pill.priceEurPerKg * 100.0) / 100.0;
        double travel = Math.round(Math.max(0.0, preview.travelCostB) * 100.0) / 100.0;
        double net = Math.round(Math.max(0.0, gross - travel) * 100.0) / 100.0;
        grossView.setText(fragment.getString(R.string.truck_confirm_coins, gross));
        travelView.setText(fragment.getString(R.string.truck_confirm_coins, travel));
        netView.setText(fragment.getString(R.string.truck_confirm_coins, net));
        back.setOnClickListener(v -> {
            dialog.dismiss();
            show(fragment, market);
        });
        send.setOnClickListener(v -> {
            dialog.dismiss();
            onSend.run();
        });
        dialog.show();
    }

    private static void toast(@NonNull Fragment fragment, double kg, @NonNull MarketSellResult result) {
        if (result.success) {
            if (result.truckDispatched) {
                GameNotice.showSuccess(fragment.requireContext(),
                        fragment.getString(R.string.market_sell_truck, kg));
            } else {
                GameNotice.showSuccess(fragment.requireContext(),
                        fragment.getString(R.string.market_sell_ok, kg, result.unitPriceEurPerKg));
            }
        } else if ("stock".equals(result.errorMessage)) {
            GameNotice.show(fragment.requireContext(), R.string.market_sell_fail_stock);
        } else if ("travel".equals(result.errorMessage)) {
            GameNotice.show(fragment.requireContext(), R.string.market_order_fail_travel);
        } else if ("demand".equals(result.errorMessage)) {
            GameNotice.show(fragment.requireContext(), R.string.market_sell_fail_demand);
        } else if ("fleet".equals(result.errorMessage)) {
            GameNotice.show(fragment.requireContext(), R.string.market_sell_fail_fleet);
        } else {
            GameNotice.show(fragment.requireContext(),
                    fragment.getString(R.string.market_sell_fail_reason, result.errorMessage));
        }
    }

    @Nullable
    private static String currentUid() {
        com.apiculture.simulator.data.session.SignedInUser u =
                com.apiculture.simulator.data.session.PlayerAuth.getInstance().getCurrentUser();
        return u != null ? u.getUid() : "";
    }
}
