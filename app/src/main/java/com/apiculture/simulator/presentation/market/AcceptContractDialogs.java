package com.apiculture.simulator.presentation.market;

import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.CompoundButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.apiculture.simulator.R;
import com.apiculture.simulator.data.local.entity.HexParcelOwnershipEntity;
import com.apiculture.simulator.data.local.entity.HiveEntity;
import com.apiculture.simulator.domain.parcel.HexApiary;
import com.apiculture.simulator.domain.parcel.HexParcelRandomPoint;
import com.apiculture.simulator.domain.parcel.WarehouseRules;
import com.apiculture.simulator.data.repository.HiveRepository;
import com.apiculture.simulator.data.repository.PollinationContractRepository;
import com.apiculture.simulator.domain.population.HivePopulationState;
import com.apiculture.simulator.data.repository.TruckLiveTrips;
import com.apiculture.simulator.databinding.DialogAcceptContractBinding;
import com.apiculture.simulator.databinding.ItemAcceptContractHiveBinding;
import com.apiculture.simulator.databinding.ItemAcceptContractParcelBinding;
import com.apiculture.simulator.databinding.ItemPollinationOfferBinding;
import com.apiculture.simulator.domain.game.GameCalendar;
import com.apiculture.simulator.domain.game.PollinationContractRules;
import com.apiculture.simulator.domain.game.HiveHoneyStocks;
import com.apiculture.simulator.domain.game.TranshumanceRules;
import com.apiculture.simulator.presentation.common.GameNotice;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

public final class AcceptContractDialogs {

    private AcceptContractDialogs() {
    }

    public static final class Selection {
        public final List<String> hiveIds;
        public final List<PollinationContractRepository.HiveOrder> orders;

        public Selection(List<String> hiveIds, List<PollinationContractRepository.HiveOrder> orders) {
            this.hiveIds = hiveIds != null ? hiveIds : new ArrayList<>();
            this.orders = orders != null ? orders : new ArrayList<>();
        }

        public boolean isEmpty() {
            return hiveIds.isEmpty() && PollinationContractRepository.HiveOrder.hiveCount(orders) <= 0;
        }
    }

    public static void show(
            Context context,
            PollinationContractRepository.Offer offer,
            List<HiveEntity> hives,
            Function<String, String> parcelNameOf,
            Consumer<Selection> onConfirm) {
        show(context, offer, hives, parcelNameOf, null, onConfirm);
    }

    public static void show(
            Context context,
            PollinationContractRepository.Offer offer,
            List<HiveEntity> hives,
            Function<String, String> parcelNameOf,
            @Nullable List<HexParcelOwnershipEntity> ownerSites,
            Consumer<Selection> onConfirm) {
        show(context, offer, hives, parcelNameOf, ownerSites, null, null, true, false, onConfirm);
    }

    public static void show(
            Context context,
            PollinationContractRepository.Offer offer,
            List<HiveEntity> hives,
            Function<String, String> parcelNameOf,
            @Nullable List<String> initialHiveIds,
            boolean signOnConfirm,
            Consumer<Selection> onConfirm) {
        show(context, offer, hives, parcelNameOf, null, initialHiveIds, null, signOnConfirm, false, onConfirm);
    }

    public static void show(
            Context context,
            PollinationContractRepository.Offer offer,
            List<HiveEntity> hives,
            Function<String, String> parcelNameOf,
            @Nullable List<String> initialHiveIds,
            @Nullable List<PollinationContractRepository.HiveOrder> initialOrders,
            boolean signOnConfirm,
            Consumer<Selection> onConfirm) {
        show(context, offer, hives, parcelNameOf, null, initialHiveIds, initialOrders, signOnConfirm, false, onConfirm);
    }

    public static void show(
            Context context,
            PollinationContractRepository.Offer offer,
            List<HiveEntity> hives,
            Function<String, String> parcelNameOf,
            @Nullable List<String> initialHiveIds,
            @Nullable List<PollinationContractRepository.HiveOrder> initialOrders,
            boolean signOnConfirm,
            boolean showCard,
            Consumer<Selection> onConfirm) {
        show(context, offer, hives, parcelNameOf, null, initialHiveIds, initialOrders, signOnConfirm, showCard,
                onConfirm);
    }

    public static void show(
            Context context,
            PollinationContractRepository.Offer offer,
            List<HiveEntity> hives,
            Function<String, String> parcelNameOf,
            @Nullable List<HexParcelOwnershipEntity> ownerSites,
            @Nullable List<String> initialHiveIds,
            @Nullable List<PollinationContractRepository.HiveOrder> initialOrders,
            boolean signOnConfirm,
            boolean showCard,
            Consumer<Selection> onConfirm) {
        if (context == null || offer == null) {
            return;
        }
        String destHex = offer.farm != null ? offer.farm.hexId() : "";
        int todayKey = GameCalendar.toDayKey(LocalDate.now(GameCalendar.userTimeZone()));
        List<HiveEntity> eligible = new ArrayList<>();
        Map<String, HiveEntity> byId = new LinkedHashMap<>();
        if (hives != null) {
            for (HiveEntity h : hives) {
                if (h == null || h.id == null) {
                    continue;
                }
                if (TranshumanceRules.isInTransit(h, todayKey)
                        || TruckLiveTrips.hasActive(context.getApplicationContext(), h.id)) {
                    continue;
                }
                if (h.linkedToContract()) {
                    continue;
                }
                if (h.inWarehouse) {
                    continue;
                }
                if (h.hexId == null || h.hexId.isEmpty()) {
                    continue;
                }
                eligible.add(h);
                byId.put(h.id, h);
            }
        }
        Map<String, List<HiveEntity>> bySite = new LinkedHashMap<>();
        Map<String, HexParcelOwnershipEntity> siteByKey = new LinkedHashMap<>();
        for (HiveEntity h : eligible) {
            HexParcelOwnershipEntity site = nearestOwnedApiary(h, ownerSites);
            String key = siteKey(h.hexId, site);
            List<HiveEntity> list = bySite.get(key);
            if (list == null) {
                list = new ArrayList<>();
                bySite.put(key, list);
            }
            list.add(h);
            if (!siteByKey.containsKey(key)) {
                siteByKey.put(key, site);
            }
        }
        double destLat = offer.farm != null && offer.farm.parcel != null ? offer.farm.parcel.centroidLat : 0;
        double destLng = offer.farm != null && offer.farm.parcel != null ? offer.farm.parcel.centroidLon : 0;
        List<ParcelRow> parcels = new ArrayList<>();
        for (Map.Entry<String, List<HiveEntity>> e : bySite.entrySet()) {
            HexParcelOwnershipEntity site = siteByKey.get(e.getKey());
            String hexId = hexIdOfKey(e.getKey());
            String name = nameOfSite(site, hexId, parcelNameOf);
            int travelEach = 0;
            int netOne = offer.payB();
            boolean here = destHex != null && destHex.equals(hexId);
            HiveEntity sample = e.getValue().isEmpty() ? null : e.getValue().get(0);
            double fromLat = sample != null ? sample.lat : 0;
            double fromLng = sample != null ? sample.lng : 0;
            if (site != null && (Math.abs(site.siteLat) > 1e-8 || Math.abs(site.siteLng) > 1e-8)) {
                fromLat = site.siteLat;
                fromLng = site.siteLng;
            }
            double km = sample == null && site == null ? 0
                    : TranshumanceRules.haversineKm(fromLat, fromLng, destLat, destLng);
            travelEach = PollinationContractRules.travelCostPerHiveKm(km);
            netOne = offer.payB() - travelEach;
            parcels.add(new ParcelRow(e.getKey(), hexId, name, e.getValue(), travelEach, netOne, here, km));
        }
        Collections.sort(parcels, Comparator
                .comparing((ParcelRow r) -> r.here)
                .thenComparing((ParcelRow r) -> r.netOne <= 0)
                .thenComparingInt(r -> r.travelEach));

        Set<String> selectedIds = new LinkedHashSet<>();
        if (initialHiveIds != null) {
            for (String id : initialHiveIds) {
                if (byId.containsKey(id)) {
                    selectedIds.add(id);
                }
            }
        }



        DialogAcceptContractBinding form = DialogAcceptContractBinding.inflate(LayoutInflater.from(context));
        if (showCard && form.cardContractEmbed != null) {
            ItemPollinationOfferBinding card = form.cardContractEmbed;
            card.getRoot().setVisibility(View.VISIBLE);
            ContractOfferBinder.bind(card, offer, null);
            card.btnAcceptContract.setVisibility(View.GONE);
            card.btnPickHives.setVisibility(View.GONE);
        }
        PickHiveAdapter hiveAdapter = new PickHiveAdapter();
        form.recyclerPickParcels.setLayoutManager(new LinearLayoutManager(context));
        form.recyclerPickHives.setLayoutManager(new LinearLayoutManager(context));
        form.recyclerPickHives.setAdapter(hiveAdapter);
        form.tvPickTitle.setText(R.string.market_contract_pick_title);
        form.tvPickHint.setText(R.string.market_contract_pick_hint);
        form.recyclerPickParcels.setVisibility(View.VISIBLE);
        form.recyclerPickHives.setVisibility(View.VISIBLE);
        form.btnConfirmPick.setVisibility(View.VISIBLE);
        form.btnPickBack.setVisibility(View.GONE);

        final ParcelRow[] selectedParcel = {null};
        final ParcelAdapter[] parcelAdapterHolder = {null};

        Runnable refreshTotals = () -> {
            List<HiveEntity> picked = new ArrayList<>();
            for (String id : selectedIds) {
                HiveEntity h = byId.get(id);
                if (h != null) {
                    picked.add(h);
                }
            }
            int travel = picked.isEmpty() ? 0 : offer.travelCostForHives(picked);
            int net = offer.payB() - travel;
            boolean empty = picked.isEmpty();
            if (empty) {
                form.tvPickHint.setText(R.string.market_contract_pick_hint);
                form.tvPickHint.setTextColor(ContextCompat.getColor(context, R.color.event_ink_muted));
                form.btnConfirmPick.setText(R.string.market_contract_pick_empty_button);
                form.btnConfirmPick.setEnabled(false);
                form.btnConfirmPick.setBackgroundTintList(solidTint(
                        ContextCompat.getColor(context, R.color.dash_muted)));
                form.btnConfirmPick.setTextColor(solidTint(
                        ContextCompat.getColor(context, R.color.white)));
                return;
            }
            if (net <= 0) {
                form.tvPickHint.setText(R.string.market_contract_travel_over_reward);
                form.tvPickHint.setTextColor(ContextCompat.getColor(context, R.color.dash_bad));
            } else {
                form.tvPickHint.setText(context.getString(
                        R.string.market_contract_pick_hives_hint_no_buy, (double) travel, (double) net));
                form.tvPickHint.setTextColor(ContextCompat.getColor(context, R.color.event_ink_muted));
            }
            if (travel > 0) {
                form.btnConfirmPick.setText(context.getString(
                        R.string.market_contract_pick_transhume_cash, (double) travel));
            } else {
                form.btnConfirmPick.setText(R.string.market_contract_pick_transhume_free);
            }
            form.btnConfirmPick.setEnabled(true);
            form.btnConfirmPick.setBackgroundTintList(solidTint(
                    ContextCompat.getColor(context, R.color.event_gold)));
            form.btnConfirmPick.setTextColor(solidTint(
                    ContextCompat.getColor(context, R.color.event_ink)));
        };

        Consumer<ParcelRow> showParcelHives = row -> {
            selectedParcel[0] = row;
            if (parcelAdapterHolder[0] != null) {
                parcelAdapterHolder[0].setSelectedKey(row != null ? row.key : null);
            }
            if (row == null || row.here) {
                hiveAdapter.setHives(null, 0, selectedIds, null);
            } else {
                hiveAdapter.setHives(row.hives, row.travelEach, selectedIds, () -> {
                    if (parcelAdapterHolder[0] != null) {
                        parcelAdapterHolder[0].notifyDataSetChanged();
                    }
                    refreshTotals.run();
                });
            }
            refreshTotals.run();
        };

        ParcelAdapter parcelAdapter = new ParcelAdapter(parcels, selectedIds, showParcelHives);
        parcelAdapterHolder[0] = parcelAdapter;
        form.recyclerPickParcels.setAdapter(parcelAdapter);
        refreshTotals.run();
        for (int i = 0; i < parcels.size(); i++) {
            if (!parcels.get(i).here) {
                showParcelHives.accept(parcels.get(i));
                break;
            }
        }

        Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(form.getRoot());
        dialog.setCancelable(true);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        form.btnPickBack.setOnClickListener(null);
        form.btnPickCancel.setOnClickListener(v -> dialog.dismiss());
        form.btnConfirmPick.setOnClickListener(v -> {
            List<String> ids = new ArrayList<>(selectedIds);
            Selection selection = new Selection(ids, new ArrayList<>());
            if (selection.isEmpty()) {
                GameNotice.show(context, R.string.market_contract_need_hive);
                return;
            }
            if (signOnConfirm && !ids.isEmpty()) {
                List<HiveEntity> picked = new ArrayList<>();
                for (String id : ids) {
                    HiveEntity h = byId.get(id);
                    if (h != null) {
                        picked.add(h);
                    }
                }
                int travel = offer.travelCostForHives(picked);
                if (offer.payB() - travel <= 0) {
                    return;
                }
            }
            dialog.dismiss();
            onConfirm.accept(selection);
        });
        dialog.show();
    }



    @Nullable
    private static HexParcelOwnershipEntity nearestOwnedApiary(
            @Nullable HiveEntity hive, @Nullable List<HexParcelOwnershipEntity> ownerSites) {
        if (hive == null || hive.hexId == null || ownerSites == null || ownerSites.isEmpty()) {
            return null;
        }
        List<HexParcelOwnershipEntity> onHex = new ArrayList<>();
        for (HexParcelOwnershipEntity row : ownerSites) {
            if (row != null && hive.hexId.equals(row.hexId) && WarehouseRules.isApiarySite(row)
                    && (hive.ownerId == null || hive.ownerId.equals(row.ownerId))) {
                onHex.add(row);
            }
        }
        if (hive.siteId != null && !hive.siteId.isEmpty()) {
            HexParcelOwnershipEntity named = HexApiary.siteRow(hive.siteId, onHex);
            if (named != null) {
                return named;
            }
        }
        return HexParcelRandomPoint.nearestApiary(null, onHex, hive.lat, hive.lng);
    }

    @NonNull
    private static String siteKey(@Nullable String hexId, @Nullable HexParcelOwnershipEntity site) {
        String siteId = "default";
        if (site != null && site.siteId != null && !site.siteId.isEmpty()) {
            siteId = site.siteId;
        }
        return (hexId != null ? hexId : "") + "\t" + siteId;
    }

    @NonNull
    private static String hexIdOfKey(@Nullable String key) {
        if (key == null || key.isEmpty()) {
            return "";
        }
        int tab = key.indexOf('\t');
        return tab < 0 ? key : key.substring(0, tab);
    }

    @NonNull
    private static String nameOfSite(
            @Nullable HexParcelOwnershipEntity site,
            @Nullable String hexId,
            @Nullable Function<String, String> parcelNameOf) {
        if (site != null && site.parcelName != null && !site.parcelName.trim().isEmpty()) {
            return site.parcelName.trim();
        }
        if (site == null && parcelNameOf != null && hexId != null && !hexId.isEmpty()) {
            try {
                String name = parcelNameOf.apply(hexId);
                if (name != null && !name.trim().isEmpty()) {
                    return name.trim();
                }
            } catch (RuntimeException ignored) {
            }
        }
        if (site != null && site.siteId != null && !site.siteId.isEmpty()
                && !"default".equals(site.siteId)) {
            String sid = site.siteId;
            if (sid.length() > 8) {
                sid = sid.substring(0, 6);
            }
            return "Apiario · " + sid;
        }
        return "Apiario";
    }

    private static ColorStateList solidTint(int color) {
        return new ColorStateList(
                new int[][]{
                        new int[]{android.R.attr.state_enabled},
                        new int[]{-android.R.attr.state_enabled}
                },
                new int[]{color, color});
    }

    private static final class ParcelRow {
        final String key;
        final String hexId;
        final String name;
        final List<HiveEntity> hives;
        final int travelEach;
        final int netOne;
        final boolean here;
        final double km;

        ParcelRow(String key, String hexId, String name, List<HiveEntity> hives, int travelEach, int netOne,
                boolean here, double km) {
            this.key = key;
            this.hexId = hexId;
            this.name = name;
            this.hives = hives;
            this.travelEach = travelEach;
            this.netOne = netOne;
            this.here = here;
            this.km = km;
        }
    }

    private static final class ParcelAdapter extends RecyclerView.Adapter<ParcelAdapter.Holder> {
        private final List<ParcelRow> rows;
        private final Set<String> selectedIds;
        private final Consumer<ParcelRow> onPick;
        private String selectedKey;

        ParcelAdapter(List<ParcelRow> rows, Set<String> selectedIds, Consumer<ParcelRow> onPick) {
            this.rows = rows;
            this.selectedIds = selectedIds;
            this.onPick = onPick;
        }

        void setSelectedKey(String key) {
            selectedKey = key;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(ItemAcceptContractParcelBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            ParcelRow row = rows.get(position);
            Context ctx = holder.itemView.getContext();
            holder.b.tvParcelPickName.setText(row.name);
            int marked = 0;
            for (HiveEntity h : row.hives) {
                if (h != null && selectedIds.contains(h.id)) {
                    marked++;
                }
            }
            String hiveLabel = ctx.getResources().getQuantityString(
                    R.plurals.market_contract_parcel_hives, row.hives.size(), row.hives.size());
            if (marked > 0) {
                hiveLabel = hiveLabel + " · " + ctx.getString(R.string.market_contract_picked_hives, marked);
            }
            String extra;
            int color;
            if (row.here) {
                extra = ctx.getString(R.string.market_contract_pick_already_here);
                color = ContextCompat.getColor(ctx, R.color.event_ink_muted);
            } else if (row.netOne > 0) {
                extra = ctx.getString(R.string.market_contract_pick_travel_ok, row.km, (double) row.travelEach);
                color = ContextCompat.getColor(ctx, R.color.dash_good);
            } else {
                extra = ctx.getString(R.string.market_contract_pick_travel_far, row.km, (double) row.travelEach);
                color = ContextCompat.getColor(ctx, R.color.dash_bad);
            }
            holder.b.tvParcelPickMeta.setText(hiveLabel + " · " + extra);
            holder.b.tvParcelPickMeta.setTextColor(color);
            boolean selected = selectedKey != null && selectedKey.equals(row.key);
            holder.itemView.setAlpha(row.here ? 0.55f : 1f);
            holder.itemView.setBackgroundTintList(selected
                    ? solidTint(ContextCompat.getColor(ctx, R.color.event_gold_stroke))
                    : null);
            holder.itemView.setOnClickListener(v -> onPick.accept(row));
        }

        @Override
        public int getItemCount() {
            return rows.size();
        }

        static final class Holder extends RecyclerView.ViewHolder {
            final ItemAcceptContractParcelBinding b;

            Holder(ItemAcceptContractParcelBinding b) {
                super(b.getRoot());
                this.b = b;
            }
        }
    }

    private static final class PickHiveAdapter extends RecyclerView.Adapter<PickHiveAdapter.Holder> {
        private List<HiveEntity> hives = new ArrayList<>();
        private int travelEach;
        private Set<String> selectedIds = new LinkedHashSet<>();
        @Nullable
        private Runnable onChanged;

        void setHives(List<HiveEntity> next, int travelEach, Set<String> selectedIds, @Nullable Runnable onChanged) {
            this.hives = next != null ? new ArrayList<>(next) : new ArrayList<>();
            this.travelEach = travelEach;
            this.selectedIds = selectedIds != null ? selectedIds : new LinkedHashSet<>();
            this.onChanged = onChanged;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(ItemAcceptContractHiveBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            HiveEntity h = hives.get(position);
            String name = h.name != null && !h.name.trim().isEmpty() ? h.name.trim() : "Colmena";
            holder.b.tvHivePick.setText(name);
            Context ctx = holder.itemView.getContext();
            String honey = HiveHoneyStocks.dominantFlora(h);
            if (honey == null || honey.trim().isEmpty()) {
                honey = ctx.getString(R.string.market_contract_hive_honey_none);
            }
            int workers = HivePopulationState.adultWorkersForUi(
                    h, HiveRepository.DEFAULT_BEE_COUNT_PER_HIVE);
            holder.b.tvHivePickTravel.setText(ctx.getString(
                    R.string.market_contract_hive_meta, workers, honey));
            holder.b.checkHive.setOnCheckedChangeListener(null);
            holder.b.checkHive.setChecked(selectedIds.contains(h.id));
            CompoundButton.OnCheckedChangeListener l = (button, isChecked) -> {
                if (isChecked) {
                    selectedIds.add(h.id);
                } else {
                    selectedIds.remove(h.id);
                }
                if (onChanged != null) {
                    onChanged.run();
                }
            };
            holder.b.checkHive.setOnCheckedChangeListener(l);
            holder.itemView.setOnClickListener(v -> holder.b.checkHive.toggle());
        }

        @Override
        public int getItemCount() {
            return hives.size();
        }

        static final class Holder extends RecyclerView.ViewHolder {
            final ItemAcceptContractHiveBinding b;

            Holder(ItemAcceptContractHiveBinding b) {
                super(b.getRoot());
                this.b = b;
            }
        }
    }
}
