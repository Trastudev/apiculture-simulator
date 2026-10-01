package com.apiculture.simulator.presentation.hive;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.apiculture.simulator.ApicultureApp;
import com.apiculture.simulator.R;
import com.apiculture.simulator.data.local.entity.HexParcelOwnershipEntity;
import com.apiculture.simulator.data.local.entity.HiveEntity;
import com.apiculture.simulator.data.local.entity.PollinationContractEntity;
import com.apiculture.simulator.data.repository.HoneyLogistics;
import com.apiculture.simulator.data.repository.IberiaHexOverlayStore;
import com.apiculture.simulator.data.repository.MapRegionPrefs;
import com.apiculture.simulator.databinding.FragmentApiariesBinding;
import com.apiculture.simulator.databinding.ItemApiaryCardBinding;
import com.apiculture.simulator.domain.game.ClimateUnlock;
import com.apiculture.simulator.domain.health.HiveAlertBadge;
import com.apiculture.simulator.domain.game.DailySkyCondition;
import com.apiculture.simulator.domain.game.DailyWeather;
import com.apiculture.simulator.domain.game.GameCalendar;
import com.apiculture.simulator.domain.game.NpcContractCatalog;
import com.apiculture.simulator.domain.game.PollinationContractRules;
import com.apiculture.simulator.domain.map.PlayableMapRegion;
import com.apiculture.simulator.domain.parcel.HexApiary;
import com.apiculture.simulator.domain.parcel.HexParcel;
import com.apiculture.simulator.domain.parcel.WarehouseRules;
import com.apiculture.simulator.domain.parcel.HexParcelRandomPoint;
import com.apiculture.simulator.presentation.common.GameNotice;
import com.apiculture.simulator.presentation.common.SimpleViewModelFactory;
import com.apiculture.simulator.presentation.map.SharedMapFragment;
import com.apiculture.simulator.presentation.market.NpcPortraitUi;
import com.apiculture.simulator.presentation.tutorial.TutorialBus;
import com.apiculture.simulator.presentation.tutorial.TutorialEvent;
import com.apiculture.simulator.data.session.PlayerAuth;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class ApiariesFragment extends Fragment {

    private FragmentApiariesBinding binding;
    private HiveViewModel viewModel;
    private String sessionOwnerId = "";
    private List<HiveEntity> cachedHives = Collections.emptyList();
    private List<HexParcelOwnershipEntity> cachedOwnerships = Collections.emptyList();
    private List<PollinationContractEntity> cachedOpenContracts = Collections.emptyList();

    private ApiaryAdapter adapter;
    private int skyFetchGen;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private final Runnable cardsDebounced = this::refreshCardsNow;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentApiariesBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ApicultureApp app = (ApicultureApp) requireActivity().getApplication();
        viewModel = new ViewModelProvider(this,
                new SimpleViewModelFactory<>(() -> new HiveViewModel(app.getHiveRepository(),
                        app.getHexParcelRepository())))
                .get(HiveViewModel.class);
        String ownerId = PlayerAuth.getInstance().getUid();
        if (ownerId == null) {
            ownerId = "guest";
        }
        sessionOwnerId = ownerId;
        if (!"guest".equals(ownerId)) {
            viewModel.startHexParcelCloudSync();
            viewModel.startRealtimeCloudSync(ownerId);
        }

        adapter = new ApiaryAdapter();
        binding.rvApiaries.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvApiaries.setAdapter(adapter);

        binding.btnApiariesGoMap.setOnClickListener(v -> {
            if (TutorialBus.firstHivePath()) {
                return;
            }
            NavHostFragment.findNavController(this).navigate(R.id.mapFragment);
        });

        viewModel.hexOwnerships().observe(getViewLifecycleOwner(), rows -> {
            List<HexParcelOwnershipEntity> mine = new ArrayList<>();
            if (rows != null) {
                for (HexParcelOwnershipEntity r : rows) {
                    if (r != null && sessionOwnerId.equals(r.ownerId)) {
                        mine.add(r);
                    }
                }
            }
            cachedOwnerships = mine;
            refreshOpenContract();
            scheduleRefreshCards();
        });
        viewModel.hives(ownerId).observe(getViewLifecycleOwner(), hives -> {
            cachedHives = hives != null ? hives : Collections.emptyList();
            refreshOpenContract();
            scheduleRefreshCards();
        });
    }

    private void refreshOpenContract() {
        ApicultureApp app = (ApicultureApp) requireActivity().getApplication();
        app.getPollinationContractRepository().getOpenListForOwner(sessionOwnerId, rows -> {
            if (!isAdded()) {
                return;
            }
            cachedOpenContracts = rows != null ? rows : Collections.emptyList();
            scheduleRefreshCards();
        });
    }

    private void scheduleRefreshCards() {
        uiHandler.removeCallbacks(cardsDebounced);
        uiHandler.postDelayed(cardsDebounced, 160);
    }

    private void refreshCardsNow() {
        refreshCards();
    }

    private void refreshCards() {
        if (adapter == null || binding == null) {
            return;
        }
        List<ApiaryCard> cards = buildCards();
        adapter.submit(cards);
        updateEmpty();
        fetchObservedSkies(cards);
    }

    private void fetchObservedSkies(@NonNull List<ApiaryCard> cards) {
        final int gen = ++skyFetchGen;
        ApicultureApp app = (ApicultureApp) requireActivity().getApplication();
        final Context appCtx = requireContext().getApplicationContext();
        new Thread(() -> {
            LocalDate yesterday = LocalDate.now(GameCalendar.userTimeZone()).minusDays(1);
            Map<String, DailyWeather> weatherByCoord = new HashMap<>();
            boolean changed = false;
            for (ApiaryCard card : cards) {
                if (card == null) {
                    continue;
                }
                HiveEntity sample = sampleHive(card.hexId, card.siteId);
                HexParcel parcel = card.hexId.isEmpty()
                        ? null
                        : IberiaHexOverlayStore.findById(appCtx, card.hexId);
                double[] ll = YardClimate.weatherLatLng(parcel, sample);
                if (ll == null) {
                    continue;
                }
                String ck = String.format(Locale.US, "%.3f,%.3f", ll[0], ll[1]);
                DailyWeather observed;
                if (weatherByCoord.containsKey(ck)) {
                    observed = weatherByCoord.get(ck);
                } else {
                    observed = app.getWeatherRepository()
                            .fetchCalendarDayWeatherBlocking(ll[0], ll[1], yesterday);
                    weatherByCoord.put(ck, observed);
                }
                DailySkyCondition next = YardClimate.yesterdaySky(card.hexId, sample, parcel, observed);
                if (next != card.sky) {
                    card.sky = next;
                    changed = true;
                }
            }
            if (!changed || gen != skyFetchGen) {
                return;
            }
            if (!isAdded()) {
                return;
            }
            requireActivity().runOnUiThread(() -> {
                if (!isAdded() || adapter == null || gen != skyFetchGen) {
                    return;
                }
                adapter.notifyDataSetChanged();
            });
        }, "apiary-card-sky").start();
    }

    private void openYard(ApiaryCard card) {
        Bundle args = new Bundle();
        args.putString("hexId", card.hexId);
        args.putString("siteId", card.siteId);
        args.putString("parcelName", card.name);
        args.putBoolean("contractYard", card.contract);
        args.putString("npcName", card.npcName);
        args.putInt("portraitIndex", card.portraitIndex);
        NavHostFragment.findNavController(this).navigate(R.id.action_hives_to_apiary_yard, args);
    }

    private void harvestApiary(@NonNull ApiaryCard card) {
        if (!isAdded()) {
            return;
        }
        List<HiveEntity> hives = hivesOnCard(card);
        if (hives.isEmpty()) {
            GameNotice.show(requireContext(), R.string.dashboard_harvest_all_none);
            return;
        }
        HoneyLogistics.previewHarvestAll(requireContext(), sessionOwnerId, hives, plan -> {
            if (!isAdded()) {
                return;
            }
            if (plan == null || plan.trips.isEmpty()) {
                if (plan != null && plan.warehouseFull) {
                    GameNotice.show(requireContext(), R.string.harvest_warehouse_full);
                } else if (plan != null && plan.noFleet) {
                    GameNotice.show(requireContext(), R.string.harvest_no_truck);
                } else if (plan != null && plan.skippedNoWarehouse > 0) {
                    WarehouseDialogs.showNeedWarehouse(this);
                } else {
                    GameNotice.show(requireContext(), R.string.dashboard_harvest_all_none);
                }
                return;
            }
            HarvestCollectDialogs.showTrips(this, plan.trips, plan.skippedNoWarehouse, plan.leftBehind, chosen -> {
                ApicultureApp app = (ApicultureApp) requireActivity().getApplication();
                HoneyLogistics.commitHarvest(requireContext(), sessionOwnerId,
                        app.getHiveRepository(), app.getEconomyRepository(), chosen, result -> {
                            if (!isAdded()) {
                                return;
                            }
                            if (result != null && result.success) {
                                // Capítulo 1, viñeta 14. Cosecha hecha.
                                TutorialBus.emit(
                                        TutorialEvent.HARVESTED);
                                if (!result.deferred) {
                                    HarvestCollectDialogs.showSummary(this, result);
                                }
                            } else {
                                GameNotice.show(requireContext(), R.string.dashboard_harvest_all_none);
                            }
                        });
            });
        });
    }

    private void openTranshumance(@NonNull ApiaryCard card) {
        // Capítulo 4. Transhumancia.
        TutorialBus.emit(
                TutorialEvent.TRANSHUMANCE);
        List<HexParcelOwnershipEntity> dests = new ArrayList<>();
        for (HexParcelOwnershipEntity row : cachedOwnerships) {
            if (row == null || row.hexId == null || !WarehouseRules.isApiarySite(row)) {
                continue;
            }
            if (card.hexId != null && card.hexId.equals(row.hexId) && sameSite(card.siteId, row.siteId)) {
                continue;
            }
            dests.add(row);
        }
        TranshumanceDialogs.show(this, viewModel, card.name, card.lat, card.lng,
                hivesOnCard(card), dests);
    }

    private static boolean sameSite(@Nullable String a, @Nullable String b) {
        String left = a == null || a.isEmpty() ? "default" : a;
        String right = b == null || b.isEmpty() ? "default" : b;
        return left.equals(right);
    }

    private void openMapAt(@NonNull ApiaryCard card) {
        focusApiaryOnMap(card.hexId, card.lat, card.lng);
    }

    void focusApiaryOnMap(@Nullable String hexId, double lat, double lng) {
        if (!isAdded()) {
            return;
        }
        if (hexId == null || hexId.isEmpty() || (Math.abs(lat) < 1e-8 && Math.abs(lng) < 1e-8)) {
            NavHostFragment.findNavController(this).navigate(R.id.mapFragment);
            return;
        }
        PlayableMapRegion region = PlayableMapRegion.fromHexId(hexId);
        if (region == PlayableMapRegion.SOUTH_AFRICA) {
            ApicultureApp app = (ApicultureApp) requireActivity().getApplication();
            int level = app.getPlayerProgressRepository().getLevel(sessionOwnerId);
            if (!ClimateUnlock.canAccessSouthAfrica(level)) {
                com.apiculture.simulator.presentation.market.MarketPickerDialogs
                        .showSouthAfricaLocked(requireContext());
                return;
            }
        }
        MapRegionPrefs.set(requireContext(), region);
        Bundle args = new Bundle();
        args.putString(SharedMapFragment.ARG_FOCUS_HEX_ID, hexId);
        args.putFloat(SharedMapFragment.ARG_FOCUS_LAT, (float) lat);
        args.putFloat(SharedMapFragment.ARG_FOCUS_LNG, (float) lng);
        args.putFloat(SharedMapFragment.ARG_FOCUS_ZOOM, SharedMapFragment.FOCUS_APIARY_ZOOM);
        NavHostFragment.findNavController(this).navigate(R.id.mapFragment, args);
    }

    @NonNull
    private List<HiveEntity> hivesOnCard(@NonNull ApiaryCard card) {
        List<HiveEntity> out = new ArrayList<>();
        boolean orphans = card.hexId == null || card.hexId.isEmpty();
        for (HiveEntity h : cachedHives) {
            if (h == null || h.inWarehouse) {
                continue;
            }
            if (hiveOnCard(h, card.hexId, card.siteId, card.contract)) {
                out.add(h);
            }
        }
        return out;
    }

    private void updateEmpty() {
        if (binding == null) {
            return;
        }
        boolean hasApiary = false;
        for (HexParcelOwnershipEntity row : cachedOwnerships) {
            if (row != null && WarehouseRules.isApiarySite(row)) {
                hasApiary = true;
                break;
            }
        }
        boolean empty = !hasApiary && orphanHiveCount() == 0 && contractHexCount() == 0;
        binding.llApiariesEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.rvApiaries.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    private int orphanHiveCount() {
        int n = 0;
        for (HiveEntity h : cachedHives) {
            if (h != null && (h.hexId == null || h.hexId.isEmpty())) {
                n++;
            }
        }
        return n;
    }

    private int contractHexCount() {
        int n = 0;
        Set<String> owned = ownedHexIds();
        Set<String> seen = new HashSet<>();
        for (HiveEntity h : cachedHives) {
            if (h == null || h.inWarehouse || h.hexId == null || h.hexId.isEmpty() || owned.contains(h.hexId)) {
                continue;
            }
            if (!seen.add(h.hexId)) {
                continue;
            }
            HexParcel parcel = IberiaHexOverlayStore.findById(requireContext(), h.hexId);
            if (NpcContractCatalog.isNpcFarm(parcel)) {
                n++;
            }
        }
        for (String hex : openContractHexes()) {
            if (!seen.contains(hex) && !owned.contains(hex)) {
                n++;
                seen.add(hex);
            }
        }
        return n;
    }

    private Set<String> openContractHexes() {
        Set<String> ids = new HashSet<>();
        for (PollinationContractEntity row : cachedOpenContracts) {
            if (row == null || row.hexId == null || row.hexId.isEmpty()) {
                continue;
            }
            if (!PollinationContractRules.STATUS_ACTIVE.equals(row.status)
                    && !PollinationContractRules.STATUS_RETURNING.equals(row.status)) {
                continue;
            }
            ids.add(row.hexId);
        }
        return ids;
    }

    private Set<String> ownedHexIds() {
        Set<String> owned = new HashSet<>();
        for (HexParcelOwnershipEntity o : cachedOwnerships) {
            if (o != null && o.hexId != null) {
                owned.add(o.hexId);
            }
        }
        return owned;
    }

    private List<ApiaryCard> buildCards() {
        Map<String, Integer> counts = new HashMap<>();
        Map<String, String> flora = new HashMap<>();
        for (HiveEntity h : cachedHives) {
            if (h == null || h.inWarehouse || h.hexId == null || h.hexId.isEmpty()) {
                continue;
            }
            counts.merge(h.hexId, 1, Integer::sum);
            if (!flora.containsKey(h.hexId) && h.floraType != null) {
                flora.put(h.hexId, h.floraType);
            }
        }
        List<HexParcelOwnershipEntity> sorted = new ArrayList<>(cachedOwnerships);
        sorted.sort(Comparator.comparing(o -> terrainDisplayTitle(o).toLowerCase(Locale.ROOT)));
        List<ApiaryCard> out = new ArrayList<>();
        for (HexParcelOwnershipEntity o : sorted) {
            if (o == null || o.hexId == null || !WarehouseRules.isApiarySite(o)) {
                continue;
            }
            HiveEntity sample = sampleHive(o.hexId, o.siteId);
            HexParcel parcel = IberiaHexOverlayStore.findById(requireContext(), o.hexId);
            double[] ll = cardLatLng(parcel, o, sample);
            List<HiveEntity> siteHives = hivesOnSite(o.hexId, o.siteId, false);
            List<String> floraTypes = floraTypesForSite(siteHives, o.hexId, flora);
            out.add(new ApiaryCard(
                    o.hexId,
                    o.siteId,
                    terrainDisplayTitle(o),
                    siteHives.size(),
                    floraTypes,
                    YardClimate.resolve(requireContext(), o.hexId, sample),
                    YardClimate.yesterdaySky(o.hexId, sample, parcel),
                    hexHasAlert(o.hexId, o.siteId),
                    false,
                    null,
                    0,
                    ll[0],
                    ll[1]));
        }
        Set<String> owned = ownedHexIds();
        Set<String> seenContract = new HashSet<>();
        for (HiveEntity h : cachedHives) {
            if (h == null || h.inWarehouse || h.hexId == null || h.hexId.isEmpty() || owned.contains(h.hexId)) {
                continue;
            }
            if (!seenContract.add(h.hexId)) {
                continue;
            }
            HexParcel parcel = IberiaHexOverlayStore.findById(requireContext(), h.hexId);
            if (!NpcContractCatalog.isNpcFarm(parcel)) {
                continue;
            }
            String npc = NpcContractCatalog.npcNameFor(parcel);
            String estate = NpcContractCatalog.estateNameFor(parcel);
            HiveEntity sample = sampleHive(h.hexId, null);
            double[] ll = cardLatLng(parcel, null, sample);
            List<HiveEntity> contractHives = hivesOnSite(h.hexId, null, true);
            List<String> floraTypes = floraTypesForSite(contractHives, h.hexId, flora);
            out.add(new ApiaryCard(
                    h.hexId,
                    null,
                    estate,
                    counts.getOrDefault(h.hexId, 0),
                    floraTypes,
                    YardClimate.resolve(requireContext(), h.hexId, sample),
                    YardClimate.yesterdaySky(h.hexId, sample, parcel),
                    hexHasAlert(h.hexId, null),
                    true,
                    npc,
                    NpcContractCatalog.portraitIndexFor(npc),
                    ll[0],
                    ll[1]));
        }
        for (PollinationContractEntity row : cachedOpenContracts) {
            if (row == null || row.hexId == null || row.hexId.isEmpty()) {
                continue;
            }
            if (!PollinationContractRules.STATUS_ACTIVE.equals(row.status)
                    && !PollinationContractRules.STATUS_RETURNING.equals(row.status)) {
                continue;
            }
            if (owned.contains(row.hexId) || seenContract.contains(row.hexId)) {
                continue;
            }
            seenContract.add(row.hexId);
            HexParcel parcel = IberiaHexOverlayStore.findById(requireContext(), row.hexId);
            String npc = row.npcName != null && !row.npcName.isEmpty()
                    ? row.npcName
                    : NpcContractCatalog.npcNameFor(parcel);
            String estate = row.estateName != null && !row.estateName.isEmpty()
                    ? row.estateName
                    : NpcContractCatalog.estateNameFor(parcel);
            HiveEntity sample = sampleHive(row.hexId, null);
            double[] ll = cardLatLng(parcel, null, sample);
            List<HiveEntity> contractHives = hivesOnSite(row.hexId, null, true);
            List<String> floraTypes = floraTypesForSite(contractHives, row.hexId, flora);
            out.add(new ApiaryCard(
                    row.hexId,
                    null,
                    estate,
                    counts.getOrDefault(row.hexId, 0),
                    floraTypes,
                    YardClimate.resolve(requireContext(), row.hexId, sample),
                    YardClimate.yesterdaySky(row.hexId, sample, parcel),
                    hexHasAlert(row.hexId, null),
                    true,
                    npc,
                    NpcContractCatalog.portraitIndexFor(npc),
                    ll[0],
                    ll[1]));
        }
        int orphans = orphanHiveCount();
        if (orphans > 0) {
            HiveEntity sample = sampleHive("", null);
            List<String> floraTypes = floraTypesForOrphans();
            out.add(new ApiaryCard("", null, getString(R.string.apiary_card_orphans), orphans, floraTypes,
                    YardClimate.CONTINENTAL, YardClimate.yesterdaySky("", sample, null),
                    hexHasAlert("", null), false, null, 0, 0, 0));
        }
        return out;
    }

    @NonNull
    private List<String> floraTypesForSite(@NonNull List<HiveEntity> siteHives, @Nullable String hexId,
            @Nullable Map<String, String> fallbackFloraMap) {
        List<String> list = new ArrayList<>();
        for (HiveEntity h : siteHives) {
            if (h != null && h.floraType != null && !h.floraType.trim().isEmpty()) {
                String f = h.floraType.trim();
                if (!list.contains(f)) {
                    list.add(f);
                }
            }
        }
        if (list.isEmpty() && hexId != null && fallbackFloraMap != null && fallbackFloraMap.containsKey(hexId)) {
            list.add(fallbackFloraMap.get(hexId));
        }
        return list;
    }

    @NonNull
    private List<String> floraTypesForOrphans() {
        List<String> list = new ArrayList<>();
        for (HiveEntity h : cachedHives) {
            if (h != null && !h.inWarehouse && (h.hexId == null || h.hexId.isEmpty())) {
                if (h.floraType != null && !h.floraType.trim().isEmpty()) {
                    String f = h.floraType.trim();
                    if (!list.contains(f)) {
                        list.add(f);
                    }
                }
            }
        }
        return list;
    }

    private boolean hexHasAlert(@Nullable String hexId, @Nullable String siteId) {
        for (HiveEntity h : cachedHives) {
            if (h == null || !hiveOnCard(h, hexId, siteId, false)) {
                continue;
            }
            if (HiveAlertBadge.needsAttention(h)) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    private HiveEntity sampleHive(@Nullable String hexId, @Nullable String siteId) {
        for (HiveEntity h : cachedHives) {
            if (h != null && hiveOnCard(h, hexId, siteId, false)) {
                return h;
            }
        }
        return null;
    }

    @NonNull
    private List<HiveEntity> hivesOnSite(@Nullable String hexId, @Nullable String siteId,
            boolean contract) {
        List<HiveEntity> out = new ArrayList<>();
        for (HiveEntity h : cachedHives) {
            if (h != null && hiveOnCard(h, hexId, siteId, contract)) {
                out.add(h);
            }
        }
        return out;
    }

    private boolean hiveOnCard(@NonNull HiveEntity h, @Nullable String hexId,
            @Nullable String siteId, boolean contract) {
        if (h.inWarehouse) {
            return false;
        }
        boolean orphans = hexId == null || hexId.isEmpty();
        if (orphans) {
            return h.hexId == null || h.hexId.isEmpty();
        }
        if (!hexId.equals(h.hexId)) {
            return false;
        }
        if (contract || siteId == null || siteId.isEmpty()) {
            return true;
        }
        HexParcel parcel = IberiaHexOverlayStore.findById(requireContext(), hexId);
        return HexApiary.hiveOnSite(h, hexId, siteId, parcel, sitesOnHex(hexId));
    }

    @NonNull
    private List<HexParcelOwnershipEntity> sitesOnHex(@Nullable String hexId) {
        List<HexParcelOwnershipEntity> out = new ArrayList<>();
        if (hexId == null || hexId.isEmpty()) {
            return out;
        }
        for (HexParcelOwnershipEntity o : cachedOwnerships) {
            if (o != null && hexId.equals(o.hexId)) {
                out.add(o);
            }
        }
        return out;
    }

    @NonNull
    private static double[] cardLatLng(@Nullable HexParcel parcel,
            @Nullable HexParcelOwnershipEntity own, @Nullable HiveEntity sample) {
        if (own != null && (Math.abs(own.siteLat) > 1e-8 || Math.abs(own.siteLng) > 1e-8)) {
            return new double[]{own.siteLat, own.siteLng};
        }
        if (parcel != null || own != null) {
            return HexParcelRandomPoint.siteOf(parcel, own);
        }
        if (sample != null && (Math.abs(sample.lat) > 1e-8 || Math.abs(sample.lng) > 1e-8)) {
            return new double[]{sample.lat, sample.lng};
        }
        return new double[]{0, 0};
    }

    static String terrainDisplayTitle(@NonNull HexParcelOwnershipEntity o) {
        String name;
        if (o.parcelName != null && !o.parcelName.trim().isEmpty()) {
            name = o.parcelName.trim();
        } else {
            String hexId = o.hexId != null ? o.hexId : "";
            if (hexId.isEmpty()) {
                name = "Terreno";
            } else {
                int u = hexId.lastIndexOf('_');
                String tail = u > 0 ? hexId.substring(u + 1) : hexId;
                name = "Terreno · " + tail;
            }
        }
        if (o.isPrimary && o.hasWarehouse && WarehouseRules.isApiarySite(o)) {
            return name + " · Almacén";
        }
        return name;
    }

    @Override
    public void onDestroyView() {
        uiHandler.removeCallbacks(cardsDebounced);
        super.onDestroyView();
        binding = null;
    }

    static final class ApiaryCard {
        final String hexId;
        @Nullable
        final String siteId;
        final String name;
        final int hiveCount;
        @NonNull
        final List<String> floraTypes;
        final YardClimate climate;
        DailySkyCondition sky;
        final boolean hasAlert;
        final boolean contract;
        @Nullable
        final String npcName;
        final int portraitIndex;
        final double lat;
        final double lng;

        ApiaryCard(String hexId, @Nullable String siteId, String name, int hiveCount,
                   @Nullable List<String> floraTypes,
                   YardClimate climate, DailySkyCondition sky, boolean hasAlert,
                   boolean contract, @Nullable String npcName, int portraitIndex,
                   double lat, double lng) {
            this.hexId = hexId;
            this.siteId = siteId;
            this.name = name;
            this.hiveCount = hiveCount;
            this.floraTypes = floraTypes != null ? floraTypes : Collections.emptyList();
            this.climate = climate != null ? climate : YardClimate.CONTINENTAL;
            this.sky = sky != null ? sky : DailySkyCondition.SUN;
            this.hasAlert = hasAlert;
            this.contract = contract;
            this.npcName = npcName;
            this.portraitIndex = portraitIndex;
            this.lat = lat;
            this.lng = lng;
        }
    }

    private final class ApiaryAdapter extends RecyclerView.Adapter<ApiaryHolder> {
        private List<ApiaryCard> items = Collections.emptyList();

        ApiaryAdapter() {
        }

        void submit(List<ApiaryCard> next) {
            items = next != null ? next : Collections.emptyList();
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ApiaryHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ItemApiaryCardBinding b = ItemApiaryCardBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false);
            return new ApiaryHolder(b);
        }

        @Override
        public void onBindViewHolder(@NonNull ApiaryHolder holder, int position) {
            ApiaryCard card = items.get(position);
            holder.bind(card);
        }

        @Override
        public int getItemCount() {
            return items.size();
        }
    }

    private final class ApiaryHolder extends RecyclerView.ViewHolder {
        private final ItemApiaryCardBinding b;

        ApiaryHolder(ItemApiaryCardBinding b) {
            super(b.getRoot());
            this.b = b;
        }

        void bind(ApiaryCard card) {
            float density = itemView.getResources().getDisplayMetrics().density;
            if (card.contract) {
                b.cardApiary.setStrokeWidth(Math.round(3f * density));
                b.cardApiary.setStrokeColor(ContextCompat.getColor(
                        itemView.getContext(), R.color.event_gold));
            } else {
                b.cardApiary.setStrokeWidth(Math.round(1f * density));
                b.cardApiary.setStrokeColor(ContextCompat.getColor(
                        itemView.getContext(), R.color.event_gold_stroke));
            }
            b.tvApiaryName.setText(card.name);
            if (card.contract) {
                b.tvApiaryClimate.setText(card.npcName != null
                        ? getString(R.string.apiary_card_contract_owner, card.npcName)
                        : getString(R.string.apiary_card_contract));
                b.llApiaryContract.setVisibility(View.VISIBLE);
                b.ivApiaryNpcFace.setImageResource(NpcPortraitUi.faceDrawable(card.portraitIndex));
            } else {
                b.tvApiaryClimate.setText(card.climate.label(requireContext()));
                b.llApiaryContract.setVisibility(View.GONE);
            }
            if (card.hiveCount == 1) {
                b.tvApiaryMeta.setText(getString(R.string.apiary_card_hives_one));
            } else {
                b.tvApiaryMeta.setText(getString(R.string.apiary_card_hives, card.hiveCount));
            }
            if (card.contract) {
                List<HiveEntity> inbound =
                        PendingContractMoveUi.pendingToDest(cachedHives, card.hexId);
                if (!inbound.isEmpty()) {
                    String day = PendingContractMoveUi.travelDayLabel(
                            PendingContractMoveUi.earliestDayKey(inbound));
                    if (card.hiveCount <= 0) {
                        b.tvApiaryMeta.setText(getString(R.string.apiary_card_hives_pending_only,
                                inbound.size(), day));
                    } else {
                        b.tvApiaryMeta.setText(getString(R.string.apiary_card_hives_and_pending,
                                card.hiveCount, inbound.size(), day));
                    }
                }
            } else {
                HexParcel parcel = IberiaHexOverlayStore.findById(itemView.getContext(), card.hexId);
                List<HiveEntity> outbound =
                        PendingContractMoveUi.pendingFromSite(cachedHives, card.hexId, card.siteId,
                                parcel, sitesOnHex(card.hexId));
                if (!outbound.isEmpty()) {
                    String day = PendingContractMoveUi.travelDayLabel(
                            PendingContractMoveUi.earliestDayKey(outbound));
                    String base = card.hiveCount == 1
                            ? getString(R.string.apiary_card_hives_one)
                            : getString(R.string.apiary_card_hives, card.hiveCount);
                    b.tvApiaryMeta.setText(getString(R.string.apiary_card_hives_leaving,
                            base, outbound.size(), day));
                }
            }
            b.llApiaryFloraList.removeAllViews();
            List<String> floras = card.floraTypes;
            if (floras.isEmpty()) {
                floras = Collections.singletonList(itemView.getContext().getString(R.string.hive_flora_unknown_label));
            }
            LayoutInflater inflater = LayoutInflater.from(itemView.getContext());
            boolean compact = floras.size() > 2;
            for (String f : floras) {
                View jarView = inflater.inflate(R.layout.item_apiary_flora, b.llApiaryFloraList, false);
                ImageView jarImg = jarView.findViewById(R.id.iv_apiary_flora_jar);
                TextView jarName = jarView.findViewById(R.id.tv_apiary_flora_name);
                if (compact) {
                    int sz = Math.round(32f * density);
                    jarImg.setLayoutParams(new LinearLayout.LayoutParams(sz, sz));
                    jarName.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f);
                    jarName.setMaxWidth(Math.round(64f * density));
                    jarView.setPadding(Math.round(2f * density), jarView.getPaddingTop(), Math.round(2f * density), jarView.getPaddingBottom());
                }
                jarImg.setImageResource(HiveSiteSummaryUi.floraHoneyJarIcon(f));
                jarName.setText(HiveSiteSummaryUi.floraLabel(itemView.getContext(), f));
                b.llApiaryFloraList.addView(jarView);
            }
            b.yardPreview.setPreviewMode(true);
            b.yardPreview.setClimate(card.climate);
            b.yardPreview.setSky(card.sky);
            b.apiaryAlertDot.setAlertVisible(card.hasAlert);
            b.btnApiaryOpen.setOnClickListener(v -> openYard(card));
            boolean guided = TutorialBus.firstHivePath();
            boolean canHarvest = !guided && card.hiveCount > 0;
            b.btnApiaryHarvest.setEnabled(canHarvest);
            b.btnApiaryHarvest.setAlpha(canHarvest ? 1f : 0.45f);
            b.btnApiaryHarvest.setOnClickListener(v -> {
                if (!TutorialBus.firstHivePath()) {
                    harvestApiary(card);
                }
            });
            boolean canMove = !guided && !card.contract && card.hiveCount > 0;
            b.btnApiaryMove.setVisibility(canMove ? View.VISIBLE : View.GONE);
            b.btnApiaryMove.setOnClickListener(v -> {
                if (!TutorialBus.firstHivePath()) {
                    openTranshumance(card);
                }
            });
            boolean canMap = !guided && card.hexId != null && !card.hexId.isEmpty();
            b.btnApiaryMap.setEnabled(canMap);
            b.btnApiaryMap.setAlpha(canMap ? 1f : 0.45f);
            b.btnApiaryMap.setOnClickListener(v -> {
                if (canMap) {
                    openMapAt(card);
                }
            });
        }
    }
}
