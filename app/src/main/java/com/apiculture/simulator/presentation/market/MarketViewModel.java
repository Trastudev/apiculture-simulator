package com.apiculture.simulator.presentation.market;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.apiculture.simulator.data.repository.EconomyRepository;
import com.apiculture.simulator.data.repository.HoneyLogistics;
import com.apiculture.simulator.data.repository.MapRegionPrefs;
import com.apiculture.simulator.data.repository.MarketRepository;
import com.apiculture.simulator.data.repository.WarehouseHoneyStore;
import com.apiculture.simulator.domain.game.ExoticHoneyRules;
import com.apiculture.simulator.domain.map.PlayableMapRegion;
import com.apiculture.simulator.domain.map.ProvincialMarket;
import com.apiculture.simulator.domain.market.HoneyMarketEngine;
import com.apiculture.simulator.domain.market.HoneyMarketSnapshot;
import com.apiculture.simulator.domain.parcel.HexFlora;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class MarketViewModel extends ViewModel {
    private final Application app;
    private final EconomyRepository economyRepository;
    private final MarketRepository marketRepository;
    private final MutableLiveData<MarketUiState> ui = new MutableLiveData<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService compute = Executors.newSingleThreadExecutor();
    private boolean pushScheduled;
    private int pushGeneration;
    private MarketUiState lastPosted;

    private String selectedWarehouseHexId;

    public MarketViewModel(Application app, EconomyRepository economyRepository,
            MarketRepository marketRepository) {
        this.app = app;
        this.economyRepository = economyRepository;
        this.marketRepository = marketRepository;
    }

    public void setSelectedWarehouse(@Nullable String hexId) {
        if (this.selectedWarehouseHexId == null ? hexId == null : this.selectedWarehouseHexId.equals(hexId)) {
            return;
        }
        this.selectedWarehouseHexId = hexId;
        refresh();
    }

    @Nullable
    public String getSelectedWarehouseHexId() {
        return selectedWarehouseHexId;
    }

    public LiveData<MarketUiState> uiState() {
        return ui;
    }

    public void attachGlobalSalesStream() {
        marketRepository.setSnapshotChangedListener(this::refresh);
        schedulePushUi();
    }

    public void detachGlobalSalesStream() {
        marketRepository.setSnapshotChangedListener(null);
        mainHandler.removeCallbacksAndMessages(null);
        pushScheduled = false;
    }

    public void refresh() {
        schedulePushUi();
    }

    private void schedulePushUi() {
        if (pushScheduled) {
            return;
        }
        pushScheduled = true;
        mainHandler.post(() -> {
            pushScheduled = false;
            pushUi();
        });
    }

    private void pushUi() {
        final int gen = ++pushGeneration;
        compute.execute(() -> {
            MarketUiState next = buildUi();
            mainHandler.post(() -> {
                if (gen != pushGeneration) {
                    return;
                }
                if (next.sameVisual(lastPosted)) {
                    return;
                }
                lastPosted = next;
                ui.setValue(next);
            });
        });
    }

    private MarketUiState buildUi() {
        HoneyMarketSnapshot s = marketRepository.getSnapshot();
        List<MarketPillUi> pills = new ArrayList<>();
        Map<String, Double> stocks;
        double totalHoney;
        String uid = currentUid();
        if (selectedWarehouseHexId != null && !selectedWarehouseHexId.isEmpty()) {
            stocks = WarehouseHoneyStore.at(app, uid, selectedWarehouseHexId);
            totalHoney = WarehouseHoneyStore.totalAt(app, uid, selectedWarehouseHexId);
        } else {
            stocks = economyRepository.copyHoneyBuckets();
            totalHoney = economyRepository.getHoneyStock();
        }
        if (s != null) {
            Map<String, double[]> histories = marketRepository.last7PostedPricesByFlora();
            PlayableMapRegion region = MapRegionPrefs.get(app);
            for (String flora : HexFlora.FLORA_TYPES) {
                String key = HoneyMarketEngine.canonicalFloraKey(flora);
                if (ExoticHoneyRules.isExotic(region, key)) {
                    continue;
                }
                double[] history = histories.get(key);
                if (history == null) {
                    history = new double[0];
                } else {
                    history = history.clone();
                }
                double price = history.length > 0
                        ? history[history.length - 1]
                        : marketRepository.priceEurPerKgForFlora(key);
                double stock = stocks.getOrDefault(key, 0.0);
                pills.add(new MarketPillUi(flora, flora, price, stock, history));
            }
        }
        int players = s != null ? s.playerCount : 1;
        return new MarketUiState(
                economyRepository.getBalance(),
                totalHoney,
                players,
                pills);
    }

    public void sellFloraKg(String floraKey, double kg, Consumer<MarketSellResult> done) {
        sellFloraKg(floraKey, kg, null, done);
    }

    public void sellFloraKg(String floraKey, double kg,
            @Nullable ProvincialMarket destMarket,
            Consumer<MarketSellResult> done) {
        sellFloraKg(floraKey, kg, destMarket, null, done);
    }

    public void sellFloraKg(String floraKey, double kg,
            @Nullable ProvincialMarket destMarket,
            @Nullable String truckId,
            Consumer<MarketSellResult> done) {
        if (kg <= 0.0) {
            done.accept(MarketSellResult.fail("invalid"));
            return;
        }
        HoneyMarketSnapshot s = marketRepository.getSnapshot();
        if (s == null) {
            done.accept(MarketSellResult.fail("snapshot"));
            return;
        }
        String flora = HoneyMarketEngine.canonicalFloraKey(floraKey);
        double price = marketRepository.priceEurPerKgForFlora(flora, destMarket);
        HoneyLogistics.dispatchWholesaleTo(
                app, currentUid(), flora, kg, price, destMarket, economyRepository, marketRepository,
                truckId, r -> {
                    schedulePushUi();
                    if (r == HoneyLogistics.Result.STARTED || r == HoneyLogistics.Result.INSTANT) {
                        done.accept(MarketSellResult.ok(price).withDispatched(r == HoneyLogistics.Result.STARTED));
                    } else if (r == HoneyLogistics.Result.NO_CASH) {
                        done.accept(MarketSellResult.fail("travel"));
                    } else if (r == HoneyLogistics.Result.NO_DEMAND) {
                        done.accept(MarketSellResult.fail("demand"));
                    } else if (r == HoneyLogistics.Result.NO_FLEET) {
                        done.accept(MarketSellResult.fail("fleet"));
                    } else {
                        done.accept(MarketSellResult.fail("stock"));
                    }
                });
    }

    @Nullable
    private String currentUid() {
        com.apiculture.simulator.data.session.SignedInUser u =
                com.apiculture.simulator.data.session.PlayerAuth.getInstance().getCurrentUser();
        return u != null ? u.getUid() : "";
    }

    @Override
    protected void onCleared() {
        detachGlobalSalesStream();
        compute.shutdownNow();
    }
}
