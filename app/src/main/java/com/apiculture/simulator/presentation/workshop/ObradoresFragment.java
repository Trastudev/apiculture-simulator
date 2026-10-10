package com.apiculture.simulator.presentation.workshop;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.apiculture.simulator.R;
import com.apiculture.simulator.data.local.AppDatabase;
import com.apiculture.simulator.data.local.entity.HexParcelOwnershipEntity;
import com.apiculture.simulator.data.repository.IberiaHexOverlayStore;
import com.apiculture.simulator.data.repository.WarehouseHoneyStore;
import com.apiculture.simulator.data.repository.WorkshopStore;
import com.apiculture.simulator.data.session.PlayerAuth;
import com.apiculture.simulator.databinding.FragmentObradoresBinding;
import com.apiculture.simulator.databinding.ItemObradorCardBinding;
import com.apiculture.simulator.domain.parcel.HexParcel;
import com.apiculture.simulator.domain.parcel.WarehouseRules;
import com.apiculture.simulator.domain.workshop.WorkshopRules;
import com.apiculture.simulator.domain.workshop.WorkshopRules.Machine;
import com.apiculture.simulator.domain.workshop.WorkshopState;
import com.apiculture.simulator.presentation.hive.HiveSiteSummaryUi;
import com.apiculture.simulator.presentation.hive.YardClimate;
import com.apiculture.simulator.presentation.market.WorkshopFormatUi;
import com.apiculture.simulator.unity.Apiary3DActivity;
import com.apiculture.simulator.unity.UnityBridge;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Pestaña Obradores: una tarjeta por obrador, con el paisaje de su clima de fondo (como Apiarios),
 * sus máquinas con el nivel, la miel que guarda y el botón para entrar en el 3D.
 */
public class ObradoresFragment extends Fragment {

    private static final class Card {
        String hexId;
        String name;
        String place;
        int level;
        double capacityKg;
        YardClimate climate;
        WorkshopState state;
        Map<String, Double> bulk;
    }

    private static final ExecutorService IO = Executors.newSingleThreadExecutor();

    private final Handler main = new Handler(Looper.getMainLooper());
    private FragmentObradoresBinding binding;
    private final Adapter adapter = new Adapter();

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        binding = FragmentObradoresBinding.inflate(inflater, container, false);
        binding.rvObradores.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvObradores.setAdapter(adapter);
        return binding.getRoot();
    }

    @Override
    public void onResume() {
        super.onResume();
        load();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private void load() {
        String owner = PlayerAuth.getInstance().getUid();
        Context app = requireContext().getApplicationContext();
        IO.execute(() -> {
            List<Card> cards = owner == null ? Collections.emptyList() : build(app, owner);
            main.post(() -> {
                if (binding == null) {
                    return;
                }
                adapter.submit(cards);
                binding.llObradoresEmpty.setVisibility(cards.isEmpty() ? View.VISIBLE : View.GONE);
            });
        });
    }

    @NonNull
    private static List<Card> build(@NonNull Context app, @NonNull String owner) {
        List<Card> out = new ArrayList<>();
        List<HexParcelOwnershipEntity> rows = AppDatabase.getInstance(app).hexParcelOwnershipDao()
                .getWarehousesForOwnerSync(owner);
        if (rows == null) {
            return out;
        }
        Set<String> seen = new HashSet<>();
        for (HexParcelOwnershipEntity row : rows) {
            if (row == null || !row.hasWarehouse || row.hexId == null || !seen.add(row.hexId)) {
                continue;
            }
            Card c = new Card();
            c.hexId = row.hexId;
            c.name = row.parcelName != null && !row.parcelName.trim().isEmpty()
                    ? row.parcelName.trim() : app.getString(R.string.workshop_name);
            c.level = WarehouseRules.levelOf(row);
            c.capacityKg = WarehouseRules.capacityKg(c.level);
            HexParcel parcel = IberiaHexOverlayStore.findById(app, row.hexId);
            c.place = parcel != null && parcel.placeName != null ? parcel.placeName : "";
            c.climate = YardClimate.resolve(app, row.hexId, null);
            c.state = WorkshopStore.get(app, owner, row.hexId);
            c.bulk = WarehouseHoneyStore.at(app, owner, row.hexId);
            out.add(c);
        }
        return out;
    }

    @NonNull
    private static String kg(double v) {
        return String.format(java.util.Locale.getDefault(), v >= 10 ? "%.0f" : "%.1f", v);
    }

    private void enter(@NonNull Card c) {
        String owner = PlayerAuth.getInstance().getUid();
        if (owner == null || owner.isEmpty()) {
            return;
        }
        UnityBridge.prepareWorkshop(requireContext(), owner, c.name, c.hexId);
        Apiary3DActivity.open(requireContext());
    }

    // ---------- Tarjeta ----------

    private final class Adapter extends RecyclerView.Adapter<Holder> {
        private List<Card> items = Collections.emptyList();

        void submit(@NonNull List<Card> next) {
            items = next;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(ItemObradorCardBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            holder.bind(items.get(position));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }
    }

    private final class Holder extends RecyclerView.ViewHolder {
        private final ItemObradorCardBinding b;

        Holder(@NonNull ItemObradorCardBinding b) {
            super(b.getRoot());
            this.b = b;
        }

        void bind(@NonNull Card c) {
            Context ctx = itemView.getContext();
            b.tvObradorName.setText(c.name);
            String where = c.place.isEmpty() ? "" : c.place + " · ";
            b.tvObradorPlace.setText(where + ctx.getString(R.string.obradores_level_capacity, c.level,
                    kg(c.capacityKg)));
            b.obradorYard.setPreviewMode(true);
            b.obradorYard.setClimate(c.climate);
            // Toni tiene la exclamación en el 3D: una tanda espera envase o le falta una máquina.
            b.obradorAlertDot.setAlertVisible(c.state.needsAttention());
            bindMachines(ctx, c.state);
            bindHoney(ctx, c);
            b.btnObradorEnter.setOnClickListener(v -> enter(c));
        }

        private void bindMachines(@NonNull Context ctx, @NonNull WorkshopState s) {
            b.llObradorMachines.removeAllViews();
            for (Machine m : Machine.values()) {
                int level = s.level(m);
                int max = WorkshopRules.upgradable(m) ? WorkshopRules.MAX_LEVEL : 1;
                String value;
                if (level <= 0) {
                    value = ctx.getString(R.string.obradores_machine_missing);
                } else if (max == 1) {
                    value = ctx.getString(R.string.obradores_machine_ready);
                } else {
                    StringBuilder dots = new StringBuilder();
                    for (int i = 1; i <= max; i++) {
                        dots.append(i <= level ? '●' : '○');
                    }
                    value = dots + "  " + ctx.getString(R.string.obradores_machine_level, level);
                }
                b.llObradorMachines.addView(row(ctx, WorkshopUi.machineName(ctx, m), value, level <= 0));
            }
        }

        private void bindHoney(@NonNull Context ctx, @NonNull Card c) {
            b.llObradorHoney.removeAllViews();
            double total = 0;
            for (Map.Entry<String, Double> e : c.bulk.entrySet()) {
                if (e.getValue() == null || e.getValue() < 0.05) {
                    continue;
                }
                total += e.getValue();
                b.llObradorHoney.addView(row(ctx, HiveSiteSummaryUi.floraLabel(ctx, e.getKey()),
                        ctx.getString(R.string.obradores_honey_bulk, kg(e.getValue())), false));
            }
            for (WorkshopState.Packed p : c.state.packed) {
                if (p.jars <= 0) {
                    continue;
                }
                total += p.kg;
                b.llObradorHoney.addView(row(ctx, HiveSiteSummaryUi.floraLabel(ctx, p.flora),
                        ctx.getString(R.string.obradores_honey_jars, p.jars, WorkshopFormatUi.label(ctx, p.format)), false));
            }
            if (b.llObradorHoney.getChildCount() == 0) {
                b.llObradorHoney.addView(row(ctx, ctx.getString(R.string.obradores_honey_empty), "", true));
            } else {
                b.llObradorHoney.addView(row(ctx, ctx.getString(R.string.obradores_honey_total),
                        ctx.getString(R.string.obradores_honey_total_value, kg(total),
                                kg(c.capacityKg)), false));
            }
        }

        @NonNull
        private View row(@NonNull Context ctx, @NonNull String label, @NonNull String value, boolean muted) {
            LinearLayout line = new LinearLayout(ctx);
            line.setOrientation(LinearLayout.HORIZONTAL);
            int pad = Math.round(3 * ctx.getResources().getDisplayMetrics().density);
            line.setPadding(0, pad, 0, pad);
            TextView name = new TextView(ctx);
            name.setText(label);
            name.setTextSize(13);
            name.setTextColor(ContextCompat.getColor(ctx, muted ? R.color.event_ink_muted : R.color.event_ink));
            line.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            TextView val = new TextView(ctx);
            val.setText(value);
            val.setTextSize(13);
            val.setTextColor(ContextCompat.getColor(ctx, muted ? R.color.event_ink_muted : R.color.event_gold_dark));
            line.addView(val, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return line;
        }
    }
}
