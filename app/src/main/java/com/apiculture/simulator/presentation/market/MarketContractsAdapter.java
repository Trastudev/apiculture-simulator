package com.apiculture.simulator.presentation.market;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.apiculture.simulator.data.repository.PollinationContractRepository;
import com.apiculture.simulator.databinding.ItemPollinationOfferBinding;

import java.util.ArrayList;
import java.util.List;

public class MarketContractsAdapter extends RecyclerView.Adapter<MarketContractsAdapter.Holder> {

    public interface Listener {
        void onSign(PollinationContractRepository.Offer offer);

        default void onPendingChanged() {
        }
    }

    private final List<PollinationContractRepository.Offer> items = new ArrayList<>();
    private final Listener listener;

    public MarketContractsAdapter(Listener listener) {
        this.listener = listener;
    }

    public void setOffers(List<PollinationContractRepository.Offer> offers) {
        items.clear();
        if (offers != null) {
            items.addAll(offers);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemPollinationOfferBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    class Holder extends RecyclerView.ViewHolder {
        private final ItemPollinationOfferBinding b;

        Holder(ItemPollinationOfferBinding b) {
            super(b.getRoot());
            this.b = b;
        }

        void bind(PollinationContractRepository.Offer offer) {
            ContractOfferBinder.bind(b, offer, listener);
        }
    }
}
