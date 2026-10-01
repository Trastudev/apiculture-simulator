package com.apiculture.simulator.presentation.market;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;

import com.apiculture.simulator.data.repository.PollinationContractRepository;
import com.apiculture.simulator.databinding.ItemPollinationOfferBinding;

public final class ContractCardDialogs {

    private ContractCardDialogs() {
    }

    public static void show(
            Context context,
            PollinationContractRepository.Offer offer,
            @Nullable MarketContractsAdapter.Listener onAccept) {
        if (context == null || offer == null) {
            return;
        }
        Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        ItemPollinationOfferBinding card = ItemPollinationOfferBinding.inflate(LayoutInflater.from(context));
        float d = context.getResources().getDisplayMetrics().density;
        int pad = Math.round(16f * d);
        FrameLayout wrap = new FrameLayout(context);
        wrap.setPadding(pad, pad, pad, pad);
        card.getRoot().setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        wrap.addView(card.getRoot());
        dialog.setContentView(wrap);
        dialog.setCancelable(true);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        MarketContractsAdapter.Listener local = new MarketContractsAdapter.Listener() {
            @Override
            public void onSign(PollinationContractRepository.Offer signed) {
                dialog.dismiss();
                if (onAccept != null) {
                    onAccept.onSign(signed);
                }
            }

            @Override
            public void onPendingChanged() {
                dialog.dismiss();
                if (onAccept != null) {
                    onAccept.onPendingChanged();
                }
            }
        };
        ContractOfferBinder.bind(card, offer, local);
        dialog.show();
    }
}
