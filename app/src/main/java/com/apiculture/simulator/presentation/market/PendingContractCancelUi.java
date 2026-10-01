package com.apiculture.simulator.presentation.market;

import android.content.Context;
import android.view.LayoutInflater;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;

import com.apiculture.simulator.ApicultureApp;
import com.apiculture.simulator.R;
import com.apiculture.simulator.data.local.entity.HiveEntity;
import com.apiculture.simulator.data.local.entity.TruckTripEntity;
import com.apiculture.simulator.data.repository.PollinationContractRepository;
import com.apiculture.simulator.data.repository.TruckLiveTrips;
import com.apiculture.simulator.databinding.ItemContractPendingHiveBinding;
import com.apiculture.simulator.domain.game.PollinationContractRules;
import com.apiculture.simulator.domain.game.TranshumanceRules;
import com.apiculture.simulator.domain.game.TruckTripRules;
import com.apiculture.simulator.presentation.common.GameNotice;
import com.apiculture.simulator.presentation.hive.PendingContractMoveUi;
import com.apiculture.simulator.presentation.hive.TruckTripUi;
import com.apiculture.simulator.data.session.PlayerAuth;
import com.apiculture.simulator.data.session.SignedInUser;

import java.util.List;

public final class PendingContractCancelUi {

    private PendingContractCancelUi() {
    }

    public static void bindRows(
            @Nullable LinearLayout host,
            @Nullable List<HiveEntity> hives,
            @Nullable String yardHexId,
            @Nullable Runnable onChanged) {
        if (host == null) {
            return;
        }
        host.removeAllViews();
        if (hives == null || hives.isEmpty()) {
            return;
        }
        Context ctx = host.getContext();
        LayoutInflater inflater = LayoutInflater.from(ctx);
        for (HiveEntity hive : hives) {
            if (hive == null) {
                continue;
            }
            ItemContractPendingHiveBinding row = ItemContractPendingHiveBinding.inflate(
                    inflater, host, false);
            String name = hive.name != null && !hive.name.trim().isEmpty() ? hive.name.trim() : "Colmena";
            row.tvPendingHiveName.setText(name);
            TruckTripEntity live = TruckLiveTrips.get(ctx, hive.id);
            if (live != null) {
                boolean home = TruckTripRules.isHeadingHome(live, hive);
                row.tvPendingHiveSub.setText(ctx.getString(home
                                ? R.string.hive_trip_returning
                                : R.string.market_contract_pending_hive_live,
                        TruckTripUi.remainingLabel(ctx, live)));
                if (home) {
                    row.btnPendingHiveCancel.setVisibility(android.view.View.GONE);
                    row.btnPendingHiveCancel.setOnClickListener(null);
                } else {
                    row.btnPendingHiveCancel.setVisibility(android.view.View.VISIBLE);
                    row.btnPendingHiveCancel.setOnClickListener(v ->
                            TruckTripUi.confirmCancelLiveTrip(ctx, hive.id, onChanged));
                }
            } else {
                row.tvPendingHiveSub.setText(rowSubtitle(ctx, hive, yardHexId));
                row.btnPendingHiveCancel.setVisibility(android.view.View.VISIBLE);
                row.btnPendingHiveCancel.setOnClickListener(v -> confirm(ctx, hive, onChanged));
            }
            host.addView(row.getRoot());
        }
    }

    static void confirm(
            Context ctx,
            PollinationContractRepository.Offer offer,
            HiveEntity hive,
            @Nullable Runnable onChanged) {
        confirm(ctx, hive, onChanged);
    }

    public static void confirm(Context ctx, HiveEntity hive, @Nullable Runnable onChanged) {
        if (ctx == null || hive == null || !TranshumanceRules.hasPendingContractMove(hive)) {
            return;
        }
        String dest = hive.pendingContractHexId;
        com.apiculture.simulator.domain.parcel.HexParcel destParcel =
                com.apiculture.simulator.data.repository.IberiaHexOverlayStore.findById(
                        ctx.getApplicationContext(), dest);
        int refund = destParcel == null ? 0
                : PollinationContractRules.travelCostOne(hive, destParcel.centroidLat, destParcel.centroidLon);
        if (refund >= 99_999) {
            refund = 0;
        }
        String name = hive.name != null && !hive.name.trim().isEmpty() ? hive.name.trim() : "Colmena";
        final int refundB = refund;
        GameNotice.confirm(ctx,
                ctx.getString(R.string.market_contract_cancel_title),
                ctx.getString(R.string.market_contract_cancel_message, name, (double) refundB),
                R.string.market_contract_cancel_ok,
                () -> runCancel(ctx, hive, refundB, onChanged));
    }

    private static void runCancel(Context ctx, HiveEntity hive, int refundB, @Nullable Runnable onChanged) {
        ApicultureApp app = (ApicultureApp) ctx.getApplicationContext();
        SignedInUser user = PlayerAuth.getInstance().getCurrentUser();
        String uid = user != null ? user.getUid() : "";
        app.getPollinationContractRepository().cancelPendingHive(uid, hive.id, err -> {
            if (err != null) {
                GameNotice.show(ctx, err);
                return;
            }
            GameNotice.showSuccess(ctx, ctx.getString(R.string.market_contract_cancel_done, (double) refundB));
            if (onChanged != null) {
                onChanged.run();
            }
        });
    }

    static String rowSubtitle(Context ctx, HiveEntity hive) {
        return rowSubtitle(ctx, hive, hive != null ? hive.pendingContractHexId : null);
    }

    static String rowSubtitle(Context ctx, HiveEntity hive, @Nullable String yardHexId) {
        if (ctx == null || hive == null) {
            return "";
        }
        String day = PendingContractMoveUi.travelDayLabel(hive.pendingContractDayKey);
        boolean arrivingHere = yardHexId != null && yardHexId.equals(hive.pendingContractHexId);
        if (arrivingHere) {
            String from = PendingContractMoveUi.originLabel(ctx, hive.hexId);
            return ctx.getString(R.string.market_contract_pending_hive_sub, day, from);
        }
        String dest = PendingContractMoveUi.destLabel(ctx, hive.pendingContractHexId);
        return ctx.getString(R.string.market_contract_pending_hive_sub_to, day, dest);
    }
}
