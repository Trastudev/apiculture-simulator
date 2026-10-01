package com.apiculture.simulator.presentation.market;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.apiculture.simulator.ApicultureApp;
import com.apiculture.simulator.R;
import com.apiculture.simulator.data.session.PlayerAuth;
import com.apiculture.simulator.presentation.common.GameNotice;
import com.apiculture.simulator.data.repository.PollinationContractRepository;
import com.apiculture.simulator.databinding.ItemPollinationOfferBinding;
import com.apiculture.simulator.presentation.hive.HiveSiteSummaryUi;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

final class ContractOfferBinder {

    private ContractOfferBinder() {
    }

    static void bind(
            ItemPollinationOfferBinding b,
            PollinationContractRepository.Offer offer,
            @Nullable MarketContractsAdapter.Listener listener) {
        Context ctx = b.getRoot().getContext();
        b.tvNpcName.setText(offer.farm.npcName);
        b.tvEstate.setText(offer.farm.estateName + " · "
                + com.apiculture.simulator.domain.game.ClimateUnlock.climateLabel(ctx, offer.farm.parcel));
        b.tvPlantation.setText(HiveSiteSummaryUi.floraLabel(ctx, offer.farm.flora));
        b.ivContractFlora.setImageResource(HiveSiteSummaryUi.floraHoneyJarIcon(offer.farm.flora));
        b.ivNpcFace.setImageResource(NpcPortraitUi.faceDrawable(offer.farm.portraitIndex));
        int minPct = (int) Math.round(offer.farm.terms.minPct * 100);
        b.tvMinPct.setText(ctx.getString(R.string.market_contract_min_value, minPct));
        b.tvWindow.setText(ctx.getString(R.string.market_contract_window_km,
                offer.windowLabel != null ? offer.windowLabel : "", offer.distanceKm));

        boolean accepted = offer.mine;
        int travel = offer.travelCostB;
        int reward = offer.payB();
        b.tvTravel.setText(ctx.getString(R.string.market_contract_travel_amount, (double) travel));
        b.tvTravel.setTextColor(ContextCompat.getColor(ctx, R.color.dash_bad));
        b.tvTravelNoHives.setVisibility(offer.noSendableHives ? View.VISIBLE : View.GONE);
        b.llContractReward.setVisibility(View.VISIBLE);
        b.tvReward.setText(ctx.getString(R.string.market_contract_reward_amount, (double) reward));
        b.tvReward.setTextColor(ContextCompat.getColor(ctx,
                reward > 0 ? R.color.dash_good : R.color.dash_bad));

        if (accepted) {
            b.llContractProgress.setVisibility(View.VISIBLE);
            double pool = offer.poolKg;
            double got = offer.collectedKg;
            int pct = (int) Math.round(100.0 * com.apiculture.simulator.domain.game.PollinationContractRules
                    .pollinationPct(got, pool));
            b.tvProgressLabel.setText(ctx.getString(R.string.market_contract_progress, got, pool, pct));
            b.barContractProgress.setProgress(Math.max(0, Math.min(1000,
                    (int) Math.round(10.0 * pct))));
            bindPendingHives(b, offer, listener);
        } else {
            b.llContractProgress.setVisibility(View.GONE);
            b.llPendingHives.setVisibility(View.GONE);
            b.llPendingHiveRows.removeAllViews();
        }

        MaterialCardView card = b.cardContract;
        float density = ctx.getResources().getDisplayMetrics().density;
        b.ivAcceptedStamp.setVisibility(accepted ? View.VISIBLE : View.GONE);
        if (accepted) {
            card.setStrokeWidth(Math.round(3f * density));
            card.setStrokeColor(ContextCompat.getColor(ctx, R.color.event_gold));
        } else {
            card.setStrokeWidth(Math.round(1.5f * density));
            card.setStrokeColor(ContextCompat.getColor(ctx, R.color.event_gold_stroke));
        }

        b.btnPickHives.setVisibility(View.GONE);
        b.btnPickHives.setOnClickListener(null);

        if (accepted) {
            styleFilled(b.btnAcceptContract, ctx, R.string.market_contract_accepted,
                    R.color.dash_good, R.color.white, false);
            b.btnAcceptContract.setOnClickListener(null);
            return;
        }
        if (offer.reserveHidden) {
            styleFilled(b.btnAcceptContract, ctx, R.string.market_contract_reserve_closed,
                    R.color.dash_muted, R.color.white, false);
            b.btnAcceptContract.setOnClickListener(null);
            return;
        }
        if (offer.occupied) {
            styleFilled(b.btnAcceptContract, ctx, R.string.market_contract_occupied,
                    R.color.dash_muted, R.color.white, false);
            b.btnAcceptContract.setOnClickListener(null);
            return;
        }
        if (!offer.worthTraveling()) {
            styleFilled(b.btnAcceptContract, ctx, R.string.market_contract_too_far,
                    R.color.dash_soft_red, R.color.dash_bad, false);
            b.btnAcceptContract.setOnClickListener(null);
            return;
        }
        if (!com.apiculture.simulator.domain.parcel.CropUnlock.isUnlocked(
                offer.farm.flora, playerLevel(ctx))) {
            styleFilled(b.btnAcceptContract, ctx, R.string.market_contract_transhume,
                    R.color.dash_muted, R.color.white, true);
            b.btnAcceptContract.setAlpha(0.55f);
            b.btnAcceptContract.setOnClickListener(v -> {
                int unlock = com.apiculture.simulator.domain.parcel.CropUnlock
                        .requiredLevel(offer.farm.flora);
                CharSequence body = unlock > 0
                        ? ctx.getString(R.string.market_contract_crop_locked, unlock)
                        : ctx.getString(R.string.market_contract_crop_locked_plain);
                GameNotice.show(ctx, R.string.market_contract_crop_locked_title, body);
            });
            return;
        }
        styleFilled(b.btnAcceptContract, ctx, R.string.market_contract_transhume,
                R.color.login_primary_orange, R.color.white, true);
        b.btnAcceptContract.setOnClickListener(v -> {
            if (listener != null) {
                listener.onSign(offer);
            }
        });
    }

    private static void bindPendingHives(
            ItemPollinationOfferBinding b,
            PollinationContractRepository.Offer offer,
            @Nullable MarketContractsAdapter.Listener listener) {
        if (offer.pendingHives == null || offer.pendingHives.isEmpty()) {
            b.llPendingHives.setVisibility(View.GONE);
            b.llPendingHiveRows.removeAllViews();
            return;
        }
        b.llPendingHives.setVisibility(View.VISIBLE);
        String destHex = offer.farm != null ? offer.farm.hexId() : null;
        PendingContractCancelUi.bindRows(b.llPendingHiveRows, offer.pendingHives, destHex, () -> {
            if (listener != null) {
                listener.onPendingChanged();
            }
        });
    }

    private static int playerLevel(Context ctx) {
        Context app = ctx.getApplicationContext();
        if (!(app instanceof ApicultureApp)) {
            return 1;
        }
        String uid = PlayerAuth.getInstance().getUid();
        return ((ApicultureApp) app).getPlayerProgressRepository().getLevel(uid);
    }

    private static void styleFilled(
            MaterialButton btn,
            Context ctx,
            int textRes,
            int bgColorRes,
            int textColorRes,
            boolean enabled) {
        int bg = ContextCompat.getColor(ctx, bgColorRes);
        int fg = ContextCompat.getColor(ctx, textColorRes);
        btn.setText(textRes);
        btn.setEnabled(enabled);
        btn.setAlpha(1f);
        btn.setBackgroundTintList(solidTint(bg));
        btn.setTextColor(solidTint(fg));
    }

    private static ColorStateList solidTint(int color) {
        return new ColorStateList(
                new int[][]{
                        new int[]{android.R.attr.state_enabled},
                        new int[]{-android.R.attr.state_enabled}
                },
                new int[]{color, color});
    }
}
