package com.apiculture.simulator.presentation.market;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.apiculture.simulator.data.repository.EconomyRepository;
import com.apiculture.simulator.data.repository.MarketRepository;
import com.apiculture.simulator.domain.game.GameCalendar;
import com.apiculture.simulator.domain.market.HoneyMarketEngine;
import com.apiculture.simulator.domain.market.HoneyMarketSnapshot;
import com.apiculture.simulator.domain.parcel.HexFlora;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class MarketViewModel extends ViewModel {
    private final EconomyRepository economyRepository;
    private final MarketRepository marketRepository;
    private final MutableLiveData<MarketUiState> ui = new MutableLiveData<>();
    private Map<String, Double> globalSoldByFlora = new HashMap<>();
    private int boundSalesDayKey = Integer.MIN_VALUE;

    public MarketViewModel(EconomyRepository economyRepository, MarketRepository marketRepository) {
        this.economyRepository = economyRepository;
        this.marketRepository = marketRepository;
    }

    public LiveData<MarketUiState> uiState() {
        return ui;
    }

    public void attachGlobalSalesStream() {
        marketRepository.setSnapshotChangedListener(this::refresh);
        HoneyMarketSnapshot s = marketRepository.getSnapshot();
        int todayKey = GameCalendar.currentGlobalMarketDayKey();
        if (s == null || s.dayKey != todayKey) {
            boundSalesDayKey = Integer.MIN_VALUE;
            globalSoldByFlora = new HashMap<>();
            pushUi();
            return;
        }
        if (boundSalesDayKey != todayKey) {
            boundSalesDayKey = todayKey;
            globalSoldByFlora = new HashMap<>();
        }
        marketRepository.attachGlobalSoldListener(todayKey, map -> {
            if (GameCalendar.currentGlobalMarketDayKey() != boundSalesDayKey) {
                globalSoldByFlora = new HashMap<>();
                boundSalesDayKey = Integer.MIN_VALUE;
                refresh();
                return;
            }
            globalSoldByFlora = map != null ? new HashMap<>(map) : new HashMap<>();
            pushUi();
        });
        pushUi();
    }

    public void detachGlobalSalesStream() {
        marketRepository.setSnapshotChangedListener(null);
        marketRepository.clearGlobalSoldListener();
    }

    public void refresh() {
        HoneyMarketSnapshot s = marketRepository.getSnapshot();
        int todayKey = GameCalendar.currentGlobalMarketDayKey();
        if (s == null || s.dayKey != todayKey || boundSalesDayKey != todayKey) {
            attachGlobalSalesStream();
            return;
        }
        pushUi();
    }

    private void pushUi() {
        HoneyMarketSnapshot s = marketRepository.getSnapshot();
        int todayKey = GameCalendar.currentGlobalMarketDayKey();
        List<MarketPillUi> pills = new ArrayList<>();
        if (s != null) {
            boolean sameDay = boundSalesDayKey == todayKey && s.dayKey == todayKey;
            Map<String, Double> sold = sameDay ? globalSoldByFlora : new HashMap<>();
            for (String flora : HexFlora.FLORA_TYPES) {
                double demand = s.demandKgByFlora.getOrDefault(flora, 0.0);
                double g = sold.getOrDefault(flora, 0.0);
                double base = s.priceForFloraOrDefault(flora, 12.0);
                double price = HoneyMarketEngine.priceEurPerKgFromSupply(flora, demand, g, base);
                int adj = HoneyMarketEngine.supplyPriceAdjustmentPercent(demand, g);
                double stock = economyRepository.getHoneyStockForFlora(flora);
                int pct = 0;
                if (demand > 1e-6) {
                    pct = (int) Math.min(100, Math.round(100.0 * g / demand));
                }
                pills.add(new MarketPillUi(flora, flora, demand, g, pct, price, adj, stock));
            }
        }
        int players = s != null ? s.playerCount : 1;
        ui.setValue(new MarketUiState(
                economyRepository.getBalance(),
                economyRepository.getHoneyStock(),
                players,
                pills));
    }

    public void sellFloraKg(String floraKey, double kg, Consumer<MarketSellResult> done) {
        if (kg <= 0.0) {
            done.accept(MarketSellResult.fail("invalid"));
            return;
        }
        HoneyMarketSnapshot s = marketRepository.getSnapshot();
        if (s == null) {
            done.accept(MarketSellResult.fail("snapshot"));
            return;
        }
        marketRepository.executeGlobalSale(floraKey, kg, s, economyRepository,
                new MarketRepository.MarketSaleExecutionCallback() {
                    @Override
                    public void onSuccess(double unitPriceEurPerKg) {
                        pushUi();
                        done.accept(MarketSellResult.ok(unitPriceEurPerKg));
                    }

                    @Override
                    public void onFailure(String reasonCodeOrMessage) {
                        pushUi();
                        done.accept(MarketSellResult.fail(
                                reasonCodeOrMessage != null ? reasonCodeOrMessage : "error"));
                    }
                });
    }

    @Override
    protected void onCleared() {
        marketRepository.setSnapshotChangedListener(null);
        marketRepository.clearGlobalSoldListener();
    }
}
