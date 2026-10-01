package com.apiculture.simulator.presentation.hive;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.view.Window;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.apiculture.simulator.R;
import com.apiculture.simulator.data.local.entity.HiveEntity;
import com.apiculture.simulator.databinding.DialogStockHivesBinding;
import com.apiculture.simulator.databinding.ItemStockHiveCardBinding;
import com.apiculture.simulator.domain.population.HivePopulationState;
import com.apiculture.simulator.data.repository.HiveRepository;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

public final class PlaceStockDialogs {

    private PlaceStockDialogs() {
    }

    public static void show(Fragment fragment, List<HiveEntity> stock, Consumer<HiveEntity> onPlace) {
        if (fragment == null || !fragment.isAdded()) {
            return;
        }
        Context context = fragment.requireContext();
        DialogStockHivesBinding form = DialogStockHivesBinding.inflate(LayoutInflater.from(context));
        List<HiveEntity> items = stock != null ? new ArrayList<>(stock) : new ArrayList<>();
        form.tvStockEmpty.setVisibility(items.isEmpty() ? android.view.View.VISIBLE : android.view.View.GONE);
        form.recyclerStockHives.setVisibility(items.isEmpty() ? android.view.View.GONE : android.view.View.VISIBLE);

        Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(form.getRoot());
        dialog.setCancelable(true);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        form.recyclerStockHives.setLayoutManager(new LinearLayoutManager(context));
        form.recyclerStockHives.setAdapter(new Adapter(items, hive -> {
            dialog.dismiss();
            if (onPlace != null) {
                onPlace.accept(hive);
            }
        }));
        form.btnStockClose.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private static final class Adapter extends RecyclerView.Adapter<Adapter.Holder> {
        private final List<HiveEntity> items;
        private final Consumer<HiveEntity> onPlace;

        Adapter(List<HiveEntity> items, Consumer<HiveEntity> onPlace) {
            this.items = items;
            this.onPlace = onPlace;
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(ItemStockHiveCardBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            HiveEntity h = items.get(position);
            Context ctx = holder.itemView.getContext();
            NumberFormat nf = NumberFormat.getNumberInstance(new Locale("es", "ES"));
            String name = h.name != null && !h.name.trim().isEmpty()
                    ? h.name.trim()
                    : ctx.getString(R.string.hive_unnamed);
            holder.b.tvStockName.setText(name);
            holder.b.tvStockFlora.setText(h.floraType != null ? h.floraType : "—");
            int pop = HivePopulationState.adultWorkersForUi(h, HiveRepository.DEFAULT_BEE_COUNT_PER_HIVE);
            holder.b.tvStockPop.setText(nf.format(pop));
            holder.b.tvStockHoney.setText(String.format(Locale.getDefault(), "%.1f kg", h.honeyProduction));
            holder.b.tvStockSupers.setText(String.valueOf(Math.max(0, h.superCount)));
            holder.b.tvStockHealth.setText(h.health + "%");
            holder.b.tvStockVarroa.setText(String.format(Locale.getDefault(), "%.1f%%", h.varroaPct));
            int healthColor = h.health >= 80
                    ? ContextCompat.getColor(ctx, R.color.dash_good)
                    : h.health >= 50
                    ? ContextCompat.getColor(ctx, R.color.event_ink)
                    : ContextCompat.getColor(ctx, R.color.dash_bad);
            holder.b.tvStockHealth.setTextColor(healthColor);
            int varroaColor = h.varroaPct >= 5.0
                    ? ContextCompat.getColor(ctx, R.color.dash_bad)
                    : ContextCompat.getColor(ctx, R.color.dash_good);
            holder.b.tvStockVarroa.setTextColor(varroaColor);
            holder.b.btnStockPlace.setOnClickListener(v -> onPlace.accept(h));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static final class Holder extends RecyclerView.ViewHolder {
            final ItemStockHiveCardBinding b;

            Holder(ItemStockHiveCardBinding b) {
                super(b.getRoot());
                this.b = b;
            }
        }
    }
}
