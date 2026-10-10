package com.apiculture.simulator.presentation.market;

import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.apiculture.simulator.data.local.entity.HoneyOrderEntity;
import com.apiculture.simulator.data.repository.IberiaHexOverlayStore;
import com.apiculture.simulator.domain.game.ClimateUnlock;
import com.apiculture.simulator.presentation.common.GameNotice;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;

import com.apiculture.simulator.ApicultureApp;
import com.apiculture.simulator.R;
import com.apiculture.simulator.data.local.entity.HexParcelOwnershipEntity;
import com.apiculture.simulator.data.local.entity.HiveEntity;
import com.apiculture.simulator.data.repository.PollinationContractRepository;
import com.apiculture.simulator.data.repository.TruckLiveTrips;
import com.apiculture.simulator.data.repository.HoneyLogistics;
import com.apiculture.simulator.data.repository.MapRegionPrefs;
import com.apiculture.simulator.databinding.FragmentMarketBinding;
import com.apiculture.simulator.databinding.ItemHoneyOrderBinding;
import com.apiculture.simulator.databinding.ItemPollinationOfferBinding;
import com.apiculture.simulator.domain.game.HoneyOrder;
import com.apiculture.simulator.domain.game.HoneyOrderCatalog;
import com.apiculture.simulator.domain.market.HoneyMarketEngine;
import com.apiculture.simulator.data.repository.HoneyOrderStore;
import com.apiculture.simulator.data.repository.WarehouseHoneyStore;
import com.apiculture.simulator.data.repository.PollinationOfferStore;
import com.apiculture.simulator.domain.game.GameCalendar;
import com.apiculture.simulator.domain.map.PlayableMapRegion;
import com.apiculture.simulator.domain.parcel.HexParcel;
import com.apiculture.simulator.presentation.hive.HiveSiteSummaryUi;
import com.apiculture.simulator.presentation.common.SimpleViewModelFactory;
import com.apiculture.simulator.data.session.PlayerAuth;
import com.apiculture.simulator.data.session.SignedInUser;
import com.apiculture.simulator.presentation.tutorial.TutorialBus;
import com.apiculture.simulator.presentation.tutorial.TutorialEvent;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import android.content.Context;
import com.google.android.material.button.MaterialButton;
import androidx.core.content.ContextCompat;

import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import com.apiculture.simulator.data.repository.IberiaHexOverlayStore;

public class MarketFragment extends Fragment {
    public static final String ARG_OPEN_CONTRACTS = "openContracts";
    private FragmentMarketBinding binding;
    private MarketViewModel viewModel;
    private MarketPillsAdapter adapter;
    private MarketContractsAdapter.Listener contractsListener;
    private List<HiveEntity> cachedHives = Collections.emptyList();
    private List<HexParcelOwnershipEntity> cachedOwnerships = Collections.emptyList();
    private List<PollinationContractRepository.Offer> allContractOffers = Collections.emptyList();
    private List<HoneyOrderEntity> lastOrders = Collections.emptyList();
    private List<HoneyOrder> visibleOrders = Collections.emptyList();
    private HexParcel orderWarehouse;
    @Nullable
    private PlayableMapRegion orderWarehouseRegion;
    private final Handler orderTick = new Handler(Looper.getMainLooper());
    private final Runnable orderTickRun = this::tickOrderCards;
    private final Runnable ordersRefreshRun = this::refreshOrdersNow;
    private boolean warehouseLookupInFlight;
    @Nullable
    private String lastOrdersBindFp;
    private final List<ItemHoneyOrderBinding> boundOrderRows = new ArrayList<>();
    private int contractsPage;
    private int ordersPage;
    /** Flora elegida en el filtro. Nulo enseña todas, de la más cercana a la más lejana. */
    @Nullable
    private String selectedOrderFlora;
    private boolean contractsLoading;
    private boolean contractsRefreshQueued;
    private boolean suppressRegionToggle;
    private boolean suppressWarehouseSpinner;
    private boolean suppressMarketTab;
    private final List<HexParcelOwnershipEntity> currentRegionWarehouses = new ArrayList<>();
    private HexParcelOwnershipEntity selectedWarehouseEntity;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentMarketBinding.inflate(inflater, container, false);
        ApicultureApp app = (ApicultureApp) requireActivity().getApplication();

        viewModel = new ViewModelProvider(this,
                new SimpleViewModelFactory<>(() -> new MarketViewModel(
                        requireActivity().getApplication(),
                        app.getEconomyRepository(), app.getMarketRepository())))
                .get(MarketViewModel.class);

        adapter = new MarketPillsAdapter(requireContext(), pill ->
                MarketSellDialog.show(requireContext(), pill, kg ->
                        viewModel.sellFloraKg(pill.floraKey, kg, result -> toastSellResult(kg, result))));
        int span = getResources().getInteger(R.integer.market_grid_span);
        binding.recyclerMarketPills.setLayoutManager(new GridLayoutManager(requireContext(), span));
        binding.recyclerMarketPills.setAdapter(adapter);

        contractsListener = new MarketContractsAdapter.Listener() {
            @Override
            public void onSign(PollinationContractRepository.Offer offer) {
                acceptOffer(offer);
            }

            @Override
            public void onPendingChanged() {
                if (viewModel != null) {
                    viewModel.refresh();
                }
                refreshContracts();
            }
        };
        binding.btnContractsPrev.setOnClickListener(v -> showContractsPage(contractsPage - 1));
        binding.btnContractsNext.setOnClickListener(v -> showContractsPage(contractsPage + 1));
        binding.btnOrdersPrev.setOnClickListener(v -> showOrdersPage(ordersPage - 1, true));
        binding.btnOrdersNext.setOnClickListener(v -> showOrdersPage(ordersPage + 1, true));
        binding.btnOrdersPrevBottom.setOnClickListener(v -> showOrdersPage(ordersPage - 1, true));
        binding.btnOrdersNextBottom.setOnClickListener(v -> showOrdersPage(ordersPage + 1, true));
        binding.tileStatHoney.setOnClickListener(v -> HoneyReservesDialogs.show(requireContext(), app.getEconomyRepository(), currentUid(), viewModel.getSelectedWarehouseHexId()));
        setupRegionToggle();
        refreshWarehouseSelector();
        setupOrdersSortSelector();

        binding.marketTabToggle.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked || suppressMarketTab) {
                return;
            }
            contractsPage = 0;
            ordersPage = 0;
            lastOrdersBindFp = null;
            boolean contracts = checkedId == R.id.btn_market_contracts;
            if (contracts && !contractsUnlocked()) {
                suppressMarketTab = true;
                group.check(R.id.btn_market_wholesale);
                suppressMarketTab = false;
                MarketPickerDialogs.showLocked(requireContext(),
                        R.string.market_contracts_locked_title,
                        R.string.market_contracts_locked);
                return;
            }
            boolean orders = checkedId == R.id.btn_market_orders;
            boolean wholesale = !contracts && !orders;
            binding.recyclerMarketPills.setVisibility(View.GONE);
            setMarketListVisible(wholesale);
            if (wholesale) {
                bindMarketList();
            }
            syncWarehouseVisibility();
            binding.tvContractsHint.setVisibility(contracts ? View.VISIBLE : View.GONE);
            binding.llContracts.setVisibility(contracts ? View.VISIBLE : View.GONE);
            binding.tvOrdersHint.setVisibility(orders ? View.VISIBLE : View.GONE);
            if (orders) {
                setupOrdersSortSelector();
            } else if (binding.inputLayoutOrdersSort != null) {
                binding.inputLayoutOrdersSort.setVisibility(View.GONE);
            }
            binding.llOrders.setVisibility(orders ? View.VISIBLE : View.GONE);
            if (contracts) {
                // Capítulo 5. Pestaña de contratos.
                TutorialBus.emit(
                        TutorialEvent.CONTRACTS_TAB);
                refreshContracts();
            } else {
                binding.tvContractsEmpty.setVisibility(View.GONE);
                binding.llContractsPager.setVisibility(View.GONE);
                binding.pbContracts.setVisibility(View.GONE);
            }
            if (orders) {
                // Capítulo 5. Pestaña de pedidos.
                TutorialBus.emit(
                        TutorialEvent.ORDERS_TAB);
                refreshOrders();
                orderTick.removeCallbacks(orderTickRun);
                orderTick.post(orderTickRun);
            } else {
                orderTick.removeCallbacks(orderTickRun);
                binding.tvOrdersEmpty.setVisibility(View.GONE);
                binding.llOrdersPager.setVisibility(View.GONE);
                binding.llOrdersPagerBottom.setVisibility(View.GONE);
            }
        });

        HoneyOrderStore.maintain(requireContext(), app.getMarketRepository());
        PollinationOfferStore.maintain(requireContext());
        HoneyOrderStore.observeOpen(requireContext()).observe(getViewLifecycleOwner(), rows -> {
            lastOrders = rows != null ? rows : Collections.emptyList();
            if (binding != null && binding.btnMarketOrders.isChecked()) {
                refreshOrders();
            }
        });
        viewModel.uiState().observe(getViewLifecycleOwner(), state -> {
            if (state == null) {
                return;
            }
            binding.tvStatBalance.setText(requireContext().getString(R.string.market_stat_balance_value,
                    state.balanceEur));
            binding.tvStatHoney.setText(requireContext().getString(R.string.market_stat_honey_value,
                    state.totalHoneyKg));
            adapter.setPills(state.pills);
        });

        app.getHiveRepository().getLocalHives(currentUid()).observe(getViewLifecycleOwner(), hives -> {
            cachedHives = hives != null ? hives : Collections.emptyList();
            if (binding.btnMarketContracts.isChecked()) {
                refreshContracts();
            }
        });
        app.getHexParcelRepository().observeOwnerships().observe(getViewLifecycleOwner(), rows -> {
            List<HexParcelOwnershipEntity> mine = new ArrayList<>();
            String uid = currentUid();
            if (rows != null && !uid.isEmpty()) {
                for (HexParcelOwnershipEntity r : rows) {
                    if (r != null && uid.equals(r.ownerId)) {
                        mine.add(r);
                    }
                }
            }
            cachedOwnerships = mine;
            refreshWarehouseSelector();
        });
        TruckLiveTrips.observe(requireContext()).observe(getViewLifecycleOwner(), trips -> {
            if (binding != null && binding.btnMarketContracts.isChecked()) {
                refreshContracts();
            }
        });

        Bundle args = getArguments();
        if (args != null && args.getBoolean(ARG_OPEN_CONTRACTS, false)) {
            args.putBoolean(ARG_OPEN_CONTRACTS, false);
            if (contractsUnlocked()) {
                binding.marketTabToggle.check(R.id.btn_market_contracts);
            }
        }
        bindContractsLockState();
        offerContractsTutorial();

        return binding.getRoot();
    }

    /** Desde el nivel 2, el capítulo de contratos guía el menú sin abrirlo solo. */
    private void offerContractsTutorial() {
        if (playerLevel() < 2) {
            return;
        }
        TutorialBus.emit(
                TutorialEvent.CONTRACTS_OFFER);
    }

    private void acceptOffer(PollinationContractRepository.Offer offer) {
        if (offer == null || !isAdded()) {
            return;
        }
        ApicultureApp app = (ApicultureApp) requireActivity().getApplication();
        String uid = currentUid();
        int level = app.getPlayerProgressRepository().getLevel(uid);
        AcceptContractDialogs.show(requireContext(), offer, cachedHives, this::parcelNameOf,
                cachedOwnerships, selection -> {
            if (selection == null || selection.isEmpty()) {
                GameNotice.show(requireContext(), R.string.market_contract_need_hive_or_buy);
                return;
            }
            app.getPollinationContractRepository().accept(uid, offer.farm.hexId(), offer.farm.flora,
                    offer.farm.terms != null ? offer.farm.terms.startDoy : 0,
                    selection.hiveIds, selection.orders, level, msg -> {
                        if (!isAdded()) {
                            return;
                        }
                        if (msg == null) {
                            GameNotice.showSuccess(requireContext(), R.string.market_contract_accept_ok);
                            viewModel.refresh();
                            refreshContracts();
                        } else {
                            GameNotice.show(requireContext(), msg);
                        }
                    });
        });
    }

    private void refreshContracts() {
        if (!isAdded() || binding == null) {
            return;
        }
        if (contractsLoading) {
            contractsRefreshQueued = true;
            return;
        }
        contractsLoading = true;
        binding.pbContracts.setVisibility(View.VISIBLE);
        binding.tvContractsEmpty.setVisibility(View.GONE);
        binding.llContractsPager.setVisibility(View.GONE);
        ApicultureApp app = (ApicultureApp) requireActivity().getApplication();
        String uid = currentUid();
        int level = app.getPlayerProgressRepository().getLevel(uid);
        app.getPollinationContractRepository().listOffers(uid, level, selectedRegion(), offers -> {
            contractsLoading = false;
            if (!isAdded() || binding == null) {
                return;
            }
            binding.pbContracts.setVisibility(View.GONE);
            allContractOffers = unsignedContractOffers(offers);
            showContractsPage(0);
            if (contractsRefreshQueued) {
                contractsRefreshQueued = false;
                refreshContracts();
            }
        });
    }

    @NonNull
    private static List<PollinationContractRepository.Offer> unsignedContractOffers(
            @Nullable List<PollinationContractRepository.Offer> offers) {
        if (offers == null || offers.isEmpty()) {
            return new ArrayList<>();
        }
        List<PollinationContractRepository.Offer> out = new ArrayList<>();
        for (PollinationContractRepository.Offer offer : offers) {
            if (offer != null && !offer.mine) {
                out.add(offer);
            }
        }
        return out;
    }

    private void showContractsPage(int page) {
        if (binding == null) {
            return;
        }
        int size = allContractOffers.size();
        int pageSize = Math.max(allContractOffers.size(), 1);
        int pages = size == 0 ? 1 : (size + pageSize - 1) / pageSize;
        int nextPage = Math.max(0, Math.min(page, pages - 1));
        boolean pageChanged = nextPage != contractsPage;
        contractsPage = nextPage;
        int from = contractsPage * pageSize;
        int to = Math.min(size, from + pageSize);
        List<PollinationContractRepository.Offer> slice = from < to
                ? allContractOffers.subList(from, to)
                : Collections.emptyList();
        bindContractCards(slice);
        boolean contractsTab = binding.btnMarketContracts.isChecked();
        boolean empty = size == 0;
        binding.tvContractsEmpty.setVisibility(contractsTab && empty && !contractsLoading
                ? View.VISIBLE : View.GONE);
        boolean pager = contractsTab && size > pageSize;
        binding.llContractsPager.setVisibility(pager ? View.VISIBLE : View.GONE);
        if (pager) {
            binding.tvContractsPage.setText(getString(R.string.market_contracts_page,
                    contractsPage + 1, pages));
            binding.btnContractsPrev.setEnabled(contractsPage > 0);
            binding.btnContractsNext.setEnabled(contractsPage < pages - 1);
            binding.btnContractsPrev.setAlpha(contractsPage > 0 ? 1f : 0.35f);
            binding.btnContractsNext.setAlpha(contractsPage < pages - 1 ? 1f : 0.35f);
        }
        if (pageChanged) {
            binding.getRoot().scrollTo(0, 0);
        }
    }

    private void bindContractCards(List<PollinationContractRepository.Offer> slice) {
        if (binding == null) {
            return;
        }
        binding.llContracts.removeAllViews();
        if (slice == null || slice.isEmpty()) {
            return;
        }
        LayoutInflater inflater = getLayoutInflater();
        for (int i = 0; i < slice.size(); i++) {
            ItemPollinationOfferBinding row = ItemPollinationOfferBinding.inflate(
                    inflater, binding.llContracts, false);
            ContractOfferBinder.bind(row, slice.get(i), contractsListener);
            binding.llContracts.addView(row.getRoot());
        }
    }

    private void refreshOrders() {
        if (!isAdded() || binding == null) {
            return;
        }
        orderTick.removeCallbacks(ordersRefreshRun);
        orderTick.postDelayed(ordersRefreshRun, 80);
    }

    private void refreshOrdersNow() {
        if (!isAdded() || binding == null || !binding.btnMarketOrders.isChecked()) {
            return;
        }
        PlayableMapRegion region = selectedRegion();
        bindOrderCards(orderWarehouse);
        if (orderWarehouseRegion == region && !warehouseLookupInFlight) {
            return;
        }
        if (warehouseLookupInFlight) {
            return;
        }
        warehouseLookupInFlight = true;
        HoneyLogistics.warehouseParcelAsync(requireContext(), currentUid(), warehouse -> {
            warehouseLookupInFlight = false;
            if (!isAdded() || binding == null) {
                return;
            }
            orderWarehouse = warehouse;
            orderWarehouseRegion = selectedRegion();
            bindOrderCards(warehouse);
        });
    }

    private void tickOrderCards() {
        if (!isAdded() || binding == null || !binding.btnMarketOrders.isChecked()) {
            return;
        }
        long now = System.currentTimeMillis();
        boolean expired = false;
        int from = ordersPage * HoneyOrderCatalog.PAGE_SIZE;
        for (int i = 0; i < boundOrderRows.size(); i++) {
            int idx = from + i;
            if (idx >= visibleOrders.size()) {
                break;
            }
            HoneyOrder order = visibleOrders.get(idx);
            OrderCardBinder.updateCountdown(boundOrderRows.get(i), order, now);
            if (order.expired(now)) {
                expired = true;
            }
        }
        if (expired) {
            rebuildVisibleOrders(orderWarehouse);
            showOrdersPage(ordersPage, false);
        }
        orderTick.postDelayed(orderTickRun, 1000);
    }

    private void rebuildVisibleOrders(@Nullable HexParcel warehouse) {
        String region = selectedRegion().prefsValue();
        HexParcel origin = selectedWarehouseParcel();
        if (origin == null) {
            origin = warehouse;
        }
        Double lat = origin != null ? origin.centroidLat : null;
        Double lng = origin != null ? origin.centroidLon : null;
        if (lat == null) {
            PlayableMapRegion map = MapRegionPrefs.get(requireContext());
            for (HiveEntity hive : cachedHives) {
                if (hive != null && PlayableMapRegion.fromHexId(hive.hexId) == map) {
                    lat = hive.lat;
                    lng = hive.lng;
                    break;
                }
            }
        }
        visibleOrders = HoneyOrderCatalog.openNearest(
                lastOrders, region, lat, lng, System.currentTimeMillis(),
                Integer.MAX_VALUE, playerLevel());
        if (selectedOrderFlora != null) {
            String want = HoneyMarketEngine.canonicalFloraKey(selectedOrderFlora);
            for (int i = visibleOrders.size() - 1; i >= 0; i--) {
                HoneyOrder order = visibleOrders.get(i);
                String key = order == null ? "" : HoneyMarketEngine.canonicalFloraKey(order.floraKey);
                if (!want.equals(key)) {
                    visibleOrders.remove(i);
                }
            }
        }
    }

    private void setupOrdersSortSelector() {
        if (binding == null || binding.spinnerOrdersSort == null) {
            return;
        }
        String hexId = selectedWarehouseEntity != null ? selectedWarehouseEntity.hexId : null;
        java.util.Map<String, Double> stock = WarehouseHoneyStore.at(
                requireContext(), currentUid(), hexId);
        List<String> floraKeys = new ArrayList<>();
        for (java.util.Map.Entry<String, Double> entry : stock.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null && entry.getValue() > 1e-9) {
                floraKeys.add(entry.getKey());
            }
        }
        floraKeys.sort((a, b) -> HiveSiteSummaryUi.floraLabel(requireContext(), a)
                .compareToIgnoreCase(HiveSiteSummaryUi.floraLabel(requireContext(), b)));
        boolean show = binding.btnMarketOrders.isChecked() && !floraKeys.isEmpty();
        binding.inputLayoutOrdersSort.setVisibility(show ? View.VISIBLE : View.GONE);
        if (!show) {
            if (floraKeys.isEmpty()) {
                selectedOrderFlora = null;
            }
            return;
        }
        if (selectedOrderFlora == null || !floraKeys.contains(selectedOrderFlora)) {
            selectedOrderFlora = floraKeys.get(0);
        }
        List<String> labels = new ArrayList<>();
        for (int i = 0; i < floraKeys.size(); i++) {
            labels.add(HiveSiteSummaryUi.floraLabel(requireContext(), floraKeys.get(i)));
        }
        int selected = floraKeys.indexOf(selectedOrderFlora);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                requireContext(), android.R.layout.simple_dropdown_item_1line, labels);
        binding.spinnerOrdersSort.setAdapter(adapter);
        binding.spinnerOrdersSort.setText(labels.get(selected), false);
        binding.spinnerOrdersSort.setOnItemClickListener((parent, view, position, id) -> {
            if (position < 0 || position >= floraKeys.size()) {
                return;
            }
            String next = floraKeys.get(position);
            if (next.equals(selectedOrderFlora)) {
                return;
            }
            selectedOrderFlora = next;
            ordersPage = 0;
            lastOrdersBindFp = null;
            rebuildVisibleOrders(orderWarehouse);
            showOrdersPage(0, true);
        });
    }

    private void bindOrderCards(@Nullable HexParcel warehouse) {
        if (binding == null) {
            return;
        }
        rebuildVisibleOrders(warehouse);
        String fp = ordersBindFingerprint(visibleOrders, ordersPage, warehouse);
        if (fp.equals(lastOrdersBindFp)) {
            return;
        }
        lastOrdersBindFp = fp;
        showOrdersPage(ordersPage, false);
    }

    @NonNull
    private String ordersBindFingerprint(@NonNull List<HoneyOrder> orders, int page,
            @Nullable HexParcel warehouse) {
        StringBuilder sb = new StringBuilder(64);
        sb.append(page).append('|');
        sb.append(selectedOrderFlora != null ? selectedOrderFlora : "-").append('|');
        sb.append(warehouse != null && warehouse.id != null ? warehouse.id : "-").append('|');
        for (HoneyOrder o : orders) {
            if (o == null) {
                continue;
            }
            sb.append(o.id).append(':').append(o.floraKey).append(':').append(o.expireEpochMs).append(';');
        }
        return sb.toString();
    }

    private void showOrdersPage(int page, boolean scrollTop) {
        if (binding == null) {
            return;
        }
        int size = visibleOrders.size();
        int pageSize = HoneyOrderCatalog.PAGE_SIZE;
        int pages = size == 0 ? 1 : (size + pageSize - 1) / pageSize;
        int nextPage = Math.max(0, Math.min(page, pages - 1));
        boolean pageChanged = nextPage != ordersPage;
        ordersPage = nextPage;
        int from = ordersPage * pageSize;
        int to = Math.min(size, from + pageSize);
        List<HoneyOrder> slice = from < to ? visibleOrders.subList(from, to) : Collections.emptyList();
        binding.llOrders.removeAllViews();
        boundOrderRows.clear();
        if (slice.isEmpty()) {
            binding.tvOrdersEmpty.setVisibility(View.VISIBLE);
            binding.llOrdersPager.setVisibility(View.GONE);
            binding.llOrdersPagerBottom.setVisibility(View.GONE);
            return;
        }
        binding.tvOrdersEmpty.setVisibility(View.GONE);
        LayoutInflater inflater = getLayoutInflater();
        for (HoneyOrder order : slice) {
            ItemHoneyOrderBinding row = ItemHoneyOrderBinding.inflate(inflater, binding.llOrders, false);
            OrderCardBinder.bind(row, order, orderWarehouse, this::acceptOrder);
            binding.llOrders.addView(row.getRoot());
            boundOrderRows.add(row);
        }
        boolean pager = size > pageSize;
        binding.llOrdersPager.setVisibility(pager ? View.VISIBLE : View.GONE);
        binding.llOrdersPagerBottom.setVisibility(pager ? View.VISIBLE : View.GONE);
        if (pager) {
            String pageText = getString(R.string.market_contracts_page, ordersPage + 1, pages);
            binding.tvOrdersPage.setText(pageText);
            binding.tvOrdersPageBottom.setText(pageText);

            boolean canPrev = ordersPage > 0;
            boolean canNext = ordersPage < pages - 1;
            binding.btnOrdersPrev.setEnabled(canPrev);
            binding.btnOrdersNext.setEnabled(canNext);
            binding.btnOrdersPrevBottom.setEnabled(canPrev);
            binding.btnOrdersNextBottom.setEnabled(canNext);

            float prevAlpha = canPrev ? 1f : 0.35f;
            float nextAlpha = canNext ? 1f : 0.35f;
            binding.btnOrdersPrev.setAlpha(prevAlpha);
            binding.btnOrdersNext.setAlpha(nextAlpha);
            binding.btnOrdersPrevBottom.setAlpha(prevAlpha);
            binding.btnOrdersNextBottom.setAlpha(nextAlpha);
        }
        if (scrollTop && pageChanged) {
            binding.getRoot().scrollTo(0, 0);
        }
    }

    private int playerLevel() {
        ApicultureApp app = (ApicultureApp) requireActivity().getApplication();
        return app.getPlayerProgressRepository().getLevel(currentUid());
    }

    @NonNull
    private PlayableMapRegion selectedRegion() {
        PlayableMapRegion region = MapRegionPrefs.get(requireContext());
        if (region == PlayableMapRegion.SOUTH_AFRICA && !zaUnlocked()) {
            region = PlayableMapRegion.IBERIA;
            MapRegionPrefs.set(requireContext(), region);
        }
        return region;
    }

    private void setupRegionToggle() {
        if (binding == null || binding.marketRegionToggle == null) {
            return;
        }
        PlayableMapRegion region = selectedRegion();
        suppressRegionToggle = true;
        binding.marketRegionToggle.check(regionButtonId(region));
        suppressRegionToggle = false;
        bindZaLockState();
        binding.marketRegionToggle.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked || suppressRegionToggle) {
                return;
            }
            PlayableMapRegion next = regionFromButton(checkedId);
            if (next == PlayableMapRegion.SOUTH_AFRICA && !zaUnlocked()) {
                suppressRegionToggle = true;
                group.check(regionButtonId(selectedRegion()));
                suppressRegionToggle = false;
                MarketPickerDialogs.showSouthAfricaLocked(requireContext());
                return;
            }
            ordersPage = 0;
            contractsPage = 0;
            MapRegionPrefs.set(requireContext(), next);
            if (next == PlayableMapRegion.IBERIA) {
                // Capítulo 5. Iberia en contratos.
                TutorialBus.emit(
                        TutorialEvent.CONTRACTS_IBERIA);
            }
            orderWarehouse = null;
            orderWarehouseRegion = null;
            lastOrdersBindFp = null;
            refreshWarehouseSelector();
            if (binding.btnMarketContracts.isChecked()) {
                refreshContracts();
            } else if (binding.btnMarketOrders.isChecked()) {
                refreshOrders();
            } else if (binding.btnMarketWholesale.isChecked()) {
                bindMarketList();
            }
        });
        bindMarketList();
    }

    private void refreshWarehouseSelector() {
        if (binding == null || binding.spinnerMarketWarehouse == null || binding.inputLayoutMarketWarehouse == null) {
            return;
        }
        PlayableMapRegion region = selectedRegion();
        currentRegionWarehouses.clear();
        for (HexParcelOwnershipEntity wh : cachedOwnerships) {
            if (wh != null && wh.hasWarehouse && !wh.hexId.isEmpty()) {
                if (PlayableMapRegion.fromHexId(wh.hexId) == region) {
                    currentRegionWarehouses.add(wh);
                }
            }
        }
        currentRegionWarehouses.sort(Comparator.comparing(r -> r.parcelName != null ? r.parcelName : ""));

        if (currentRegionWarehouses.isEmpty()) {
            binding.inputLayoutMarketWarehouse.setVisibility(View.GONE);
            selectedWarehouseEntity = null;
            viewModel.setSelectedWarehouse(null);
        } else {
            List<String> names = new ArrayList<>();
            for (int i = 0; i < currentRegionWarehouses.size(); i++) {
                HexParcelOwnershipEntity wh = currentRegionWarehouses.get(i);
                String name = wh.parcelName != null && !wh.parcelName.trim().isEmpty()
                        ? wh.parcelName.trim()
                        : getString(R.string.fleet_unnamed_warehouse) + " " + (i + 1);
                names.add(name);
            }
            ArrayAdapter<String> adapter = new ArrayAdapter<>(
                    requireContext(), android.R.layout.simple_dropdown_item_1line, names);
            binding.spinnerMarketWarehouse.setAdapter(adapter);

            suppressWarehouseSpinner = true;
            selectedWarehouseEntity = currentRegionWarehouses.get(0);
            binding.spinnerMarketWarehouse.setText(names.get(0), false);
            suppressWarehouseSpinner = false;

            viewModel.setSelectedWarehouse(selectedWarehouseEntity.hexId);
            syncWarehouseVisibility();

            binding.spinnerMarketWarehouse.setOnItemClickListener((parent, view, position, id) -> {
                if (suppressWarehouseSpinner) {
                    return;
                }
                if (position >= 0 && position < currentRegionWarehouses.size()) {
                    selectedWarehouseEntity = currentRegionWarehouses.get(position);
                    viewModel.setSelectedWarehouse(selectedWarehouseEntity.hexId);
                    ordersPage = 0;
                    applyWarehouseToCurrentTab();
                }
            });
        }
        setupOrdersSortSelector();
    }

    /** El almacén solo ordena Mercados y Comandas. En Contratos no se muestra. */
    private void syncWarehouseVisibility() {
        if (binding == null || binding.inputLayoutMarketWarehouse == null) {
            return;
        }
        boolean tabUsesWarehouse = binding.btnMarketWholesale.isChecked()
                || binding.btnMarketOrders.isChecked();
        boolean show = tabUsesWarehouse && !currentRegionWarehouses.isEmpty();
        binding.inputLayoutMarketWarehouse.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void applyWarehouseToCurrentTab() {
        if (binding == null) {
            return;
        }
        if (binding.btnMarketWholesale.isChecked()) {
            bindMarketList();
        } else         if (binding.btnMarketOrders.isChecked()) {
            orderWarehouse = selectedWarehouseParcel();
            orderWarehouseRegion = selectedRegion();
            lastOrdersBindFp = null;
            setupOrdersSortSelector();
            bindOrderCards(orderWarehouse);
        }
    }

    @Nullable
    private HexParcel selectedWarehouseParcel() {
        if (selectedWarehouseEntity == null || selectedWarehouseEntity.hexId == null) {
            return null;
        }
        return IberiaHexOverlayStore.findById(requireContext(), selectedWarehouseEntity.hexId);
    }

    @NonNull
    public static double[] warehouseCoordinates(@NonNull Context context, @NonNull HexParcelOwnershipEntity row) {
        if (Math.abs(row.siteLat) > 1e-8 || Math.abs(row.siteLng) > 1e-8) {
            return new double[]{row.siteLat, row.siteLng};
        }
        HexParcel p = IberiaHexOverlayStore.findById(context, row.hexId);
        if (p != null) {
            return new double[]{p.centroidLat, p.centroidLon};
        }
        return new double[]{0.0, 0.0};
    }

    private void setMarketListVisible(boolean visible) {
        if (binding == null) {
            return;
        }
        int show = visible ? View.VISIBLE : View.GONE;
        binding.recyclerMarketList.setVisibility(show);
        if (!visible) {
            binding.tvMarketListEmpty.setVisibility(View.GONE);
            binding.llMarketListPager.setVisibility(View.GONE);
        }
    }

    private void bindMarketList() {
        if (binding == null || !isAdded()) {
            return;
        }
        double originLat = Double.NaN;
        double originLng = Double.NaN;
        if (selectedWarehouseEntity != null) {
            double[] coords = warehouseCoordinates(requireContext(), selectedWarehouseEntity);
            originLat = coords[0];
            originLng = coords[1];
        }
        MarketPickerDialogs.bindList(this, binding.recyclerMarketList, binding.tvMarketListEmpty,
                binding.llMarketListPager, binding.tvMarketListPage, binding.btnMarketListPrev,
                binding.btnMarketListNext, selectedRegion(), originLat, originLng);
    }

    private static int regionButtonId(PlayableMapRegion region) {
        if (region == PlayableMapRegion.SOUTH_AFRICA) {
            return R.id.btn_market_za;
        }
        if (region == PlayableMapRegion.MADAGASCAR) {
            return R.id.btn_market_mdg;
        }
        return R.id.btn_market_iberia;
    }

    private static PlayableMapRegion regionFromButton(int checkedId) {
        if (checkedId == R.id.btn_market_za) {
            return PlayableMapRegion.SOUTH_AFRICA;
        }
        if (checkedId == R.id.btn_market_mdg) {
            return PlayableMapRegion.MADAGASCAR;
        }
        return PlayableMapRegion.IBERIA;
    }

    private boolean zaUnlocked() {
        return ClimateUnlock.canAccessSouthAfrica(playerLevel());
    }

    private boolean contractsUnlocked() {
        return playerLevel() >= 2;
    }

    private void bindZaLockState() {
        if (binding == null || binding.btnMarketZa == null) {
            return;
        }
        if (zaUnlocked()) {
            binding.btnMarketZa.setIcon(null);
        } else {
            binding.btnMarketZa.setIconResource(R.drawable.ic_lock_padlock);
        }
    }

    private void bindContractsLockState() {
        if (binding == null || binding.btnMarketContracts == null) {
            return;
        }
        if (contractsUnlocked()) {
            binding.btnMarketContracts.setIcon(null);
        } else {
            binding.btnMarketContracts.setIconTint(null);
            binding.btnMarketContracts.setIconResource(R.drawable.ic_lock_padlock);
            binding.btnMarketContracts.setIconGravity(
                    MaterialButton.ICON_GRAVITY_TEXT_END);
            binding.btnMarketContracts.setIconSize(
                    (int) (16 * getResources().getDisplayMetrics().density));
        }
    }

    private boolean orderAcceptOpen;

    private void acceptOrder(HoneyOrder order, double travelB) {
        if (orderAcceptOpen) {
            return;
        }
        int need = HoneyMarketEngine.accessLevelForFlora(order.floraKey);
        if (!HoneyMarketEngine.playerCanAccessFlora(order.floraKey, playerLevel())) {
            GameNotice.show(requireContext(), getString(R.string.market_order_flora_locked,
                    HiveSiteSummaryUi.floraLabel(requireContext(), order.floraKey), need));
            return;
        }
        orderAcceptOpen = true;
        HoneyLogistics.orderTruckOptions(requireContext(), currentUid(), order,
                viewModel.getSelectedWarehouseHexId(), choice -> {
            if (!isAdded()) {
                orderAcceptOpen = false;
                return;
            }
            if (choice.instant) {
                orderAcceptOpen = false;
                sendOrder(order, travelB, null);
                return;
            }
            if (choice.trucks.isEmpty()) {
                orderAcceptOpen = false;
                GameNotice.show(requireContext(), choice.missingHoney
                        ? R.string.market_order_fail_stock
                        : R.string.market_order_fail_truck);
                return;
            }
            HoneyOrderDialogs.showTrucks(this, choice.trucks, pick ->
                    sendOrder(order, pick.travelCostB, pick.truckId),
                    () -> orderAcceptOpen = false);
        });
    }

    private void sendOrder(HoneyOrder order, double travelB, @Nullable String truckId) {
        ApicultureApp app = (ApicultureApp) requireActivity().getApplication();
        HoneyLogistics.dispatchOrder(
                requireContext(), currentUid(), order, app.getEconomyRepository(), truckId, r -> {
                    if (!isAdded()) {
                        return;
                    }
                    viewModel.refresh();
                    if (r == HoneyLogistics.Result.STARTED) {
                        GameNotice.showSuccess(requireContext(), getString(R.string.market_order_ok, order.destLabel));
                    } else if (r == HoneyLogistics.Result.INSTANT) {
                        GameNotice.showSuccess(requireContext(),
                                getString(R.string.market_order_instant, order.payout()));
                    } else if (r == HoneyLogistics.Result.TOO_SLOW) {
                        GameNotice.show(requireContext(), R.string.market_order_fail_slow);
                    } else if (r == HoneyLogistics.Result.NO_CASH) {
                        GameNotice.show(requireContext(), R.string.market_order_fail_travel);
                    } else if (order.expired(System.currentTimeMillis())) {
                        GameNotice.show(requireContext(), R.string.market_order_fail_deadline);
                    } else if (!HoneyLogistics.hasStockForOrder(requireContext(), currentUid(),
                            app.getEconomyRepository(), order)) {
                        GameNotice.show(requireContext(), order.wantsJars()
                                ? R.string.market_order_fail_jars : R.string.market_order_fail_stock);
                    } else if (r == HoneyLogistics.Result.NO_FLEET) {
                        GameNotice.show(requireContext(), R.string.market_order_fail_truck);
                    } else if (app.getEconomyRepository().getBalance() + 1e-9 < travelB) {
                        GameNotice.show(requireContext(), R.string.market_order_fail_travel);
                    } else {
                        String why = HoneyLogistics.consumeOrderFailDetail();
                        GameNotice.show(requireContext(),
                                why != null && !why.isEmpty() ? why : getString(R.string.market_order_fail));
                    }
                });
    }

    @Nullable
    private String parcelNameOf(@Nullable String hexId) {
        if (hexId == null || hexId.isEmpty()) {
            return null;
        }
        HexParcel p = IberiaHexOverlayStore
                .findById(requireContext().getApplicationContext(), hexId);
        if (p != null && p.placeName != null && !p.placeName.trim().isEmpty()) {
            return p.placeName.trim();
        }
        return getString(R.string.hex_own_parcel_title);
    }

    private String currentUid() {
        SignedInUser u = PlayerAuth.getInstance().getCurrentUser();
        return u != null ? u.getUid() : "";
    }

    private void toastSellResult(double kg, MarketSellResult result) {
        if (!isAdded()) {
            return;
        }
        if (result.success) {
            if (result.truckDispatched) {
                GameNotice.showSuccess(requireContext(),
                        getString(R.string.market_sell_truck, kg));
            } else {
                GameNotice.showSuccess(requireContext(),
                        getString(R.string.market_sell_ok, kg, result.unitPriceEurPerKg));
            }
        } else if ("stock".equals(result.errorMessage)) {
            GameNotice.show(requireContext(), R.string.market_sell_fail_stock);
        } else if ("travel".equals(result.errorMessage)) {
            GameNotice.show(requireContext(), R.string.market_order_fail_travel);
        } else if ("demand".equals(result.errorMessage)) {
            GameNotice.show(requireContext(), R.string.market_sell_fail_demand);
        } else {
            GameNotice.show(requireContext(),
                    getString(R.string.market_sell_fail_reason, result.errorMessage));
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        ApicultureApp app = (ApicultureApp) requireActivity().getApplication();
        LocalDate today = LocalDate.now(GameCalendar.globalMarketTimeZone());
        viewModel.attachGlobalSalesStream();
        app.getMarketRepository().refreshGlobalMarketForDay(
                GameCalendar.toDayKey(today), today.getDayOfYear());
        if (binding != null) {
            bindZaLockState();
            bindContractsLockState();
            if (binding.btnMarketContracts.isChecked() && !contractsUnlocked()) {
                suppressMarketTab = true;
                binding.marketTabToggle.check(R.id.btn_market_wholesale);
                suppressMarketTab = false;
            }
        }
        if (binding != null && binding.btnMarketContracts.isChecked()) {
            refreshContracts();
        }
        if (binding != null && binding.btnMarketOrders.isChecked()) {
            orderWarehouseRegion = null;
            refreshOrders();
            orderTick.removeCallbacks(orderTickRun);
            orderTick.post(orderTickRun);
        }
    }

    @Override
    public void onPause() {
        orderTick.removeCallbacks(ordersRefreshRun);
        orderTick.removeCallbacks(orderTickRun);
        viewModel.detachGlobalSalesStream();
        super.onPause();
    }
}
