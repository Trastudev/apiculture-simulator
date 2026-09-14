package com.apiculture.simulator.presentation.market;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.apiculture.simulator.R;
import com.apiculture.simulator.databinding.ItemMarketHoneyPillBinding;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MarketPillsAdapter extends RecyclerView.Adapter<MarketPillsAdapter.VH> {

    public interface Listener {
        void onSell5(String floraKey);

        void onSellAll(String floraKey, double stockKg);
    }

    private final LayoutInflater inflater;
    private final Listener listener;
    private List<MarketPillUi> pills = new ArrayList<>();

    public MarketPillsAdapter(Context context, Listener listener) {
        this.inflater = LayoutInflater.from(context);
        this.listener = listener;
    }

    public void setPills(List<MarketPillUi> pills) {
        this.pills = pills != null ? pills : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemMarketHoneyPillBinding b = ItemMarketHoneyPillBinding.inflate(inflater, parent, false);
        return new VH(b);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        MarketPillUi p = pills.get(position);
        Context c = holder.binding.getRoot().getContext();
        holder.binding.tvPillTitle.setText(p.title);
        int floraIcon = com.apiculture.simulator.presentation.hive.HiveSiteSummaryUi.floraHoneyJarIcon(p.floraKey);
        if (floraIcon != 0) {
            holder.binding.ivPillFlora.setImageResource(floraIcon);
            holder.binding.ivPillFlora.setVisibility(android.view.View.VISIBLE);
        } else {
            holder.binding.ivPillFlora.setVisibility(android.view.View.GONE);
        }
        holder.binding.progressDemand.setMax(100);
        holder.binding.progressDemand.setProgress(Math.min(100, Math.max(0, p.fillPercent)));
        holder.binding.tvPillDemand.setText(c.getString(R.string.market_pill_demand_line,
                p.filledKg, p.demandKg));
        String adj = String.format(Locale.getDefault(), "%+d%%", p.priceAdjPercent);
        holder.binding.tvPillPrice.setText(c.getString(R.string.market_pill_price,
                p.priceEurPerKg, adj));
        holder.binding.tvPillStock.setText(c.getString(R.string.market_pill_stock, p.userStockKg));
        holder.binding.btnSell5.setText(c.getString(R.string.market_sell_5));
        holder.binding.btnSellAll.setText(c.getString(R.string.market_sell_all, p.userStockKg));
        boolean canSell5 = p.userStockKg >= 5.0 - 1e-6;
        boolean canSellAny = p.userStockKg > 1e-6;
        holder.binding.btnSell5.setEnabled(canSell5);
        holder.binding.btnSellAll.setEnabled(canSellAny);
        holder.binding.btnSell5.setOnClickListener(v -> {
            if (canSell5) {
                listener.onSell5(p.floraKey);
            }
        });
        holder.binding.btnSellAll.setOnClickListener(v -> {
            if (canSellAny) {
                listener.onSellAll(p.floraKey, p.userStockKg);
            }
        });
    }

    @Override
    public int getItemCount() {
        return pills.size();
    }

    static final class VH extends RecyclerView.ViewHolder {
        final ItemMarketHoneyPillBinding binding;

        VH(ItemMarketHoneyPillBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
