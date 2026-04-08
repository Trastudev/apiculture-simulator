package com.apiculture.simulator.presentation.market;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.apiculture.simulator.data.repository.EconomyRepository;
import com.apiculture.simulator.data.repository.MarketRepository;
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

    public MarketViewModel(EconomyRepository economyRepository, MarketRepository marketRepository) {
        this.economyRepository = economyRepository;
        this.marketRepository = marketRepository;
    }

    public LiveData<MarketUiState> uiState() {
        return ui;
    }

    public void attachGlobalSalesStream() {
        HoneyMarketSnapshot s = marketRepository.getSnapshot();
        if (s == null) {
            pushUi();
            return;
        }
        marketRepository.attachGlobalSoldListener(s.dayKey, map -> {
            globalSoldByFlora = map != null ? new HashMap<>(map) : new HashMap<>();
            pushUi();
        });
    }

    public void detachGlobalSalesStream() {
        marketRepository.clearGlobalSoldListener();
    }

    public void refresh() {
        pushUi();
    }

    private void pushUi() {
        HoneyMarketSnapshot s = marketRepository.getSnapshot();
        List<MarketPillUi> pills = new ArrayList<>();
        if (s != null) {
            Map<String, Double> sold = globalSoldByFlora;
            for (String flora : HexFlora.FLORA_TYPES) {
                double demand = s.demandKgByFlora.getOrDefault(flora, 0.0);
                double g = sold.getOrDefault(flora, 0.0);
                double price = HoneyMarketEngine.priceEurPerKgFromGlobalCoverage(flora, demand, g);
                double stock = economyRepository.getHoneyStockForFlora(flora);
                int pct = 0;
                if (demand > 1e-6) {
                    pct = (int) Math.min(100, Math.round(100.0 * g / demand));
                }
                pills.add(new MarketPillUi(flora, flora, demand, g, pct, price, stock));
            }
        }
        ui.setValue(new MarketUiState(
                economyRepository.getBalance(),
                economyRepository.getHoneyStock(),
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
        marketRepository.clearGlobalSoldListener();
    }
}
