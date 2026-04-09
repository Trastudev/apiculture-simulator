package com.apiculture.simulator.presentation.ranking;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.apiculture.simulator.data.remote.RankingEntry;
import com.apiculture.simulator.databinding.ItemRankingRowBinding;

import java.util.ArrayList;
import java.util.List;

public class RankingAdapter extends RecyclerView.Adapter<RankingAdapter.Holder> {

    private final List<RankingEntry> items = new ArrayList<>();

    public void submit(List<RankingEntry> rows) {
        items.clear();
        if (rows != null) {
            items.addAll(rows);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemRankingRowBinding b = ItemRankingRowBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new Holder(b);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static final class Holder extends RecyclerView.ViewHolder {
        private final ItemRankingRowBinding binding;

        Holder(ItemRankingRowBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(RankingEntry e) {
            binding.tvRank.setText(String.valueOf(e.rank));
            binding.tvBrand.setText(e.honeyBrand);
            binding.tvPlayer.setText(e.playerName);
            binding.tvValue.setText(e.valueLabel);
        }
    }
}
