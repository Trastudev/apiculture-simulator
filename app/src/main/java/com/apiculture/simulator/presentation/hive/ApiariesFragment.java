package com.apiculture.simulator.presentation.hive;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.apiculture.simulator.ApicultureApp;
import com.apiculture.simulator.R;
import com.apiculture.simulator.data.local.entity.HexParcelOwnershipEntity;
import com.apiculture.simulator.data.local.entity.HiveEntity;
import com.apiculture.simulator.data.repository.IberiaHexOverlayStore;
import com.apiculture.simulator.databinding.FragmentApiariesBinding;
import com.apiculture.simulator.databinding.ItemApiaryCardBinding;
import com.apiculture.simulator.domain.game.DailySkyCondition;
import com.apiculture.simulator.domain.game.DailyWeather;
import com.apiculture.simulator.domain.game.GameCalendar;
import com.apiculture.simulator.domain.parcel.HexParcel;
import com.apiculture.simulator.presentation.common.SimpleViewModelFactory;
import com.google.firebase.auth.FirebaseAuth;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ApiariesFragment extends Fragment {

    private FragmentApiariesBinding binding;
    private HiveViewModel viewModel;
    private String sessionOwnerId = "";
    private List<HiveEntity> cachedHives = Collections.emptyList();
    private List<HexParcelOwnershipEntity> cachedOwnerships = Collections.emptyList();

    private ApiaryAdapter adapter;
    private int skyFetchGen;

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
        String ownerId = FirebaseAuth.getInstance().getUid();
        if (ownerId == null) {
            ownerId = "guest";
        }
        sessionOwnerId = ownerId;
        if (!"guest".equals(ownerId)) {
            viewModel.startHexParcelCloudSync();
            viewModel.startRealtimeCloudSync(ownerId);
        }

        adapter = new ApiaryAdapter(this::openYard);
        binding.rvApiaries.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvApiaries.setAdapter(adapter);

        binding.btnApiariesNewHive.setOnClickListener(v ->
                BuyHiveDialogs.show(this, viewModel, null));
        binding.btnApiariesGoMap.setOnClickListener(v ->
                NavHostFragment.findNavController(this).navigate(R.id.mapFragment));

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
            refreshCards();
        });
        viewModel.hives(ownerId).observe(getViewLifecycleOwner(), hives -> {
            cachedHives = hives != null ? hives : Collections.emptyList();
            refreshCards();
        });
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
        final android.content.Context appCtx = requireContext().getApplicationContext();
        new Thread(() -> {
            LocalDate yesterday = LocalDate.now(GameCalendar.userTimeZone()).minusDays(1);
            Map<String, DailyWeather> weatherByCoord = new HashMap<>();
            boolean changed = false;
            for (ApiaryCard card : cards) {
                if (card == null) {
                    continue;
                }
                HiveEntity sample = sampleHive(card.hexId);
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
        args.putString("parcelName", card.name);
        NavHostFragment.findNavController(this).navigate(R.id.action_hives_to_apiary_yard, args);
    }

    private void updateEmpty() {
        if (binding == null) {
            return;
        }
        boolean empty = cachedOwnerships.isEmpty() && orphanHiveCount() == 0;
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

    private List<ApiaryCard> buildCards() {
        Map<String, Integer> counts = new HashMap<>();
        Map<String, String> flora = new HashMap<>();
        for (HiveEntity h : cachedHives) {
            if (h == null || h.hexId == null || h.hexId.isEmpty()) {
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
            if (o == null || o.hexId == null) {
                continue;
            }
            HiveEntity sample = sampleHive(o.hexId);
            HexParcel parcel = IberiaHexOverlayStore.findById(requireContext(), o.hexId);
            out.add(new ApiaryCard(
                    o.hexId,
                    terrainDisplayTitle(o),
                    counts.getOrDefault(o.hexId, 0),
                    flora.get(o.hexId),
                    YardClimate.resolve(requireContext(), o.hexId, sample),
                    YardClimate.yesterdaySky(o.hexId, sample, parcel)));
        }
        int orphans = orphanHiveCount();
        if (orphans > 0) {
            HiveEntity sample = sampleHive("");
            out.add(new ApiaryCard("", getString(R.string.apiary_card_orphans), orphans, null,
                    YardClimate.CONTINENTAL, YardClimate.yesterdaySky("", sample, null)));
        }
        return out;
    }

    @Nullable
    private HiveEntity sampleHive(@Nullable String hexId) {
        boolean orphans = hexId == null || hexId.isEmpty();
        for (HiveEntity h : cachedHives) {
            if (h == null) {
                continue;
            }
            if (orphans) {
                if (h.hexId == null || h.hexId.isEmpty()) {
                    return h;
                }
            } else if (hexId.equals(h.hexId)) {
                return h;
            }
        }
        return null;
    }

    static String terrainDisplayTitle(@NonNull HexParcelOwnershipEntity o) {
        if (o.parcelName != null && !o.parcelName.trim().isEmpty()) {
            return o.parcelName.trim();
        }
        String hexId = o.hexId != null ? o.hexId : "";
        if (hexId.isEmpty()) {
            return "Terreno";
        }
        int u = hexId.lastIndexOf('_');
        String tail = u > 0 ? hexId.substring(u + 1) : hexId;
        return "Terreno · " + tail;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    static final class ApiaryCard {
        final String hexId;
        final String name;
        final int hiveCount;
        @Nullable
        final String floraType;
        final YardClimate climate;
        DailySkyCondition sky;

        ApiaryCard(String hexId, String name, int hiveCount, @Nullable String floraType,
                   YardClimate climate, DailySkyCondition sky) {
            this.hexId = hexId;
            this.name = name;
            this.hiveCount = hiveCount;
            this.floraType = floraType;
            this.climate = climate != null ? climate : YardClimate.CONTINENTAL;
            this.sky = sky != null ? sky : DailySkyCondition.SUN;
        }
    }

    private final class ApiaryAdapter extends RecyclerView.Adapter<ApiaryHolder> {
        interface Listener {
            void onCard(ApiaryCard card);
        }

        private final Listener listener;
        private List<ApiaryCard> items = Collections.emptyList();

        ApiaryAdapter(Listener listener) {
            this.listener = listener;
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
            holder.itemView.setOnClickListener(v -> listener.onCard(card));
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
            b.tvApiaryName.setText(card.name);
            b.tvApiaryClimate.setText(card.climate.labelEs());
            if (card.hiveCount == 1) {
                b.tvApiaryMeta.setText(getString(R.string.apiary_card_hives_one));
            } else {
                b.tvApiaryMeta.setText(getString(R.string.apiary_card_hives, card.hiveCount));
            }
            b.ivApiaryFlora.setImageResource(HiveSiteSummaryUi.floraHoneyJarIcon(card.floraType));
            String floraLabel = card.floraType != null && !card.floraType.trim().isEmpty()
                    ? card.floraType.trim()
                    : getString(R.string.hive_flora_unknown_label);
            b.tvApiaryFlora.setText(floraLabel);
            b.yardPreview.setPreviewMode(true);
            b.yardPreview.setClimate(card.climate);
            b.yardPreview.setSky(card.sky);
        }
    }
}
