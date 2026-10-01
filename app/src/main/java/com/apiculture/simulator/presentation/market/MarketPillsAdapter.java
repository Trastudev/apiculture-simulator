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

public class MarketPillsAdapter extends RecyclerView.Adapter<MarketPillsAdapter.VH> {

    public interface Listener {
        void onSell(MarketPillUi pill);
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
        holder.binding.chartPrice7d.setPrices(p.priceHistory7d);
        holder.binding.tvPillPrice.setText(c.getString(R.string.market_pill_price, p.priceEurPerKg));
        if (p.priceNote != null && !p.priceNote.isEmpty()) {
            holder.binding.tvPillNote.setVisibility(android.view.View.VISIBLE);
            holder.binding.tvPillNote.setText(p.priceNote);
        } else {
            holder.binding.tvPillNote.setVisibility(android.view.View.GONE);
        }
        holder.binding.tvPillStock.setText(c.getString(R.string.market_pill_stock, p.userStockKg));
        boolean canSell = p.maxSellKg() > 1e-6;
        holder.binding.btnSell.setEnabled(canSell);
        holder.binding.btnSell.setAlpha(canSell ? 1f : 0.45f);
        holder.binding.btnSell.setOnClickListener(v -> {
            if (canSell) {
                listener.onSell(p);
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
