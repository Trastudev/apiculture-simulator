package com.apiculture.simulator.presentation.market;

import android.content.Context;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.apiculture.simulator.R;
import com.apiculture.simulator.data.repository.HoneyLogistics;
import com.apiculture.simulator.databinding.ItemHoneyOrderBinding;
import com.apiculture.simulator.domain.game.HoneyOrder;
import com.apiculture.simulator.domain.parcel.HexParcel;
import com.apiculture.simulator.presentation.hive.HiveSiteSummaryUi;

import java.text.DateFormat;
import java.util.Date;

public final class OrderCardBinder {

    public interface AcceptListener {
        void onAccept(@NonNull HoneyOrder order, double travelB);
    }

    private OrderCardBinder() {
    }

    public static void bind(@NonNull ItemHoneyOrderBinding row, @NonNull HoneyOrder order,
            @Nullable HexParcel warehouse, @Nullable AcceptListener listener) {
        bind(row, order, warehouse, listener, false);
    }

    public static void bind(@NonNull ItemHoneyOrderBinding row, @NonNull HoneyOrder order,
            @Nullable HexParcel warehouse, @Nullable AcceptListener listener, boolean claimed) {
        Context c = row.getRoot().getContext();
        long now = System.currentTimeMillis();
        row.ivOrderFace.setImageResource(NpcPortraitUi.faceDrawable(order.portraitIndex));
        row.ivOrderJar.setImageResource(HiveSiteSummaryUi.floraHoneyJarIcon(order.floraKey));
        row.tvOrderNpc.setText(order.npcName);
        row.tvOrderPlace.setText(order.destLabel);
        row.tvOrderAsk.setText(c.getString(R.string.market_order_ask, order.kg,
                HiveSiteSummaryUi.floraLabel(c, order.floraKey)));
        row.tvOrderPay.setText(c.getString(R.string.market_order_pay_amount, order.payout()));
        row.tvOrderUnit.setText(c.getString(R.string.market_order_pay_unit, order.unitPrice));
        double km = HoneyLogistics.travelKm(warehouse, order.destLat, order.destLng);
        double travel = HoneyLogistics.travelCostB(warehouse, order.destLat, order.destLng, order.kg);
        long eta = HoneyLogistics.travelDurationMs(warehouse, order.destLat, order.destLng);
        row.tvOrderDistance.setText(c.getString(R.string.market_order_distance, km));
        row.tvOrderTravel.setText(c.getString(R.string.market_order_travel, travel));
        row.tvOrderEta.setText(HoneyLogistics.formatRemaining(eta));
        if (order.expireEpochMs > 0L) {
            row.tvOrderDeadline.setText(DateFormat.getDateTimeInstance(
                    DateFormat.MEDIUM, DateFormat.SHORT).format(new Date(order.expireEpochMs)));
        } else {
            row.tvOrderDeadline.setText("—");
        }
        row.btnAcceptOrder.setTag(claimed ? "claimed" : null);
        if (claimed) {
            row.btnAcceptOrder.setText(R.string.market_order_en_route);
            row.btnAcceptOrder.setOnClickListener(null);
        } else {
            row.btnAcceptOrder.setText(R.string.market_order_accept);
            row.btnAcceptOrder.setOnClickListener(v -> {
                if (listener != null && !order.expired(System.currentTimeMillis())) {
                    listener.onAccept(order, travel);
                }
            });
        }
        updateCountdown(row, order, now);
        row.getRoot().setVisibility(View.VISIBLE);
    }

    public static void updateCountdown(@NonNull ItemHoneyOrderBinding row, @NonNull HoneyOrder order,
            long now) {
        Context c = row.getRoot().getContext();
        if (order.expireEpochMs > 0L) {
            row.tvOrderCountdown.setText(c.getString(R.string.market_order_countdown,
                    HoneyLogistics.formatCountdown(order.remainingMs(now))));
        } else {
            row.tvOrderCountdown.setText("");
        }
        boolean expired = order.expired(now);
        boolean claimed = "claimed".equals(row.btnAcceptOrder.getTag());
        row.btnAcceptOrder.setEnabled(!expired && !claimed);
        row.btnAcceptOrder.setAlpha(expired || claimed ? 0.45f : 1f);
    }
}
