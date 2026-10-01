package com.apiculture.simulator.presentation.common;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.apiculture.simulator.ApicultureApp;
import com.apiculture.simulator.R;
import com.apiculture.simulator.data.repository.TradeAccessStore;
import com.apiculture.simulator.domain.game.TradeAccessRules;
import com.apiculture.simulator.domain.map.ProvincialMarket;
import com.apiculture.simulator.domain.map.Seaport;
import com.apiculture.simulator.domain.map.SeaportCatalog;
import com.google.android.material.button.MaterialButton;

import java.util.Locale;

public final class TradeGateDialogs {

    private TradeGateDialogs() {
    }

    public static void showPort(@NonNull Fragment fragment, @Nullable String ownerId, @NonNull Seaport port,
            @NonNull Runnable onOpen) {
        if (!fragment.isAdded()) {
            return;
        }
        Context context = fragment.requireContext();
        int level = level(context, ownerId);
        boolean ready = TradeAccessRules.portLevelReached(level);
        String fee = coins(TradeAccessRules.PORT_FEE_B);
        String body = ready
                ? context.getString(R.string.trade_port_fee, SeaportCatalog.label(context, port), fee)
                : context.getString(R.string.trade_port_locked, TradeAccessRules.PORT_LEVEL, level);
        String pay = ready ? context.getString(R.string.trade_port_pay, fee) : null;
        show(fragment, SeaportCatalog.label(context, port), R.drawable.ic_fleet_port, body, pay, () -> {
            ApicultureApp app = (ApicultureApp) context.getApplicationContext();
            String err = TradeAccessStore.payPort(context, app.getEconomyRepository(), ownerId, port.id);
            if (err != null) {
                GameNotice.show(context, err);
                return false;
            }
            GameNotice.showSuccess(context, R.string.trade_port_ok);
            onOpen.run();
            return true;
        });
    }

    public static void showMarket(@NonNull Fragment fragment, @Nullable String ownerId,
            @NonNull ProvincialMarket market, @NonNull Runnable onOpen) {
        if (!fragment.isAdded()) {
            return;
        }
        Context context = fragment.requireContext();
        int level = level(context, ownerId);
        boolean ready = TradeAccessRules.marketLevelReached(level);
        String fee = coins(TradeAccessRules.MARKET_FEE_B);
        String body = ready
                ? context.getString(R.string.trade_market_fee, market.name, fee)
                : context.getString(R.string.trade_market_locked, TradeAccessRules.MARKET_LEVEL, level);
        String pay = ready ? context.getString(R.string.trade_market_pay, fee) : null;
        show(fragment, market.name, R.drawable.ic_market_international, body, pay, () -> {
            ApicultureApp app = (ApicultureApp) context.getApplicationContext();
            String err = TradeAccessStore.payMarket(context, app.getEconomyRepository(),
                    ownerId, market.salesId());
            if (err != null) {
                GameNotice.show(context, err);
                return false;
            }
            GameNotice.showSuccess(context, R.string.trade_market_ok);
            onOpen.run();
            return true;
        });
    }

    private static void show(@NonNull Fragment fragment, @NonNull String title, int imageRes,
            @NonNull String body, @Nullable String payLabel, @NonNull PayAction pay) {
        Context context = fragment.requireContext();
        View root = LayoutInflater.from(context).inflate(R.layout.dialog_trade_gate, null);
        TextView titleView = root.findViewById(R.id.tv_gate_title);
        ImageView banner = root.findViewById(R.id.iv_gate_banner);
        TextView bodyView = root.findViewById(R.id.tv_gate_body);
        MaterialButton action = root.findViewById(R.id.btn_gate_pay);
        MaterialButton close = root.findViewById(R.id.btn_gate_close);
        titleView.setText(title);
        banner.setImageResource(imageRes);
        banner.setContentDescription(title);
        bodyView.setText(body);
        Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(root);
        dialog.setCancelable(true);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        close.setOnClickListener(v -> dialog.dismiss());
        if (payLabel != null) {
            action.setVisibility(View.VISIBLE);
            action.setText(payLabel);
            action.setOnClickListener(v -> {
                if (pay.run()) {
                    dialog.dismiss();
                }
            });
        }
        dialog.show();
    }

    private static int level(@NonNull Context context, @Nullable String ownerId) {
        if (ownerId == null) {
            return 1;
        }
        return ((ApicultureApp) context.getApplicationContext())
                .getPlayerProgressRepository().getLevel(ownerId);
    }

    @NonNull
    private static String coins(int amount) {
        return String.format(Locale.GERMANY, "%,d", amount);
    }

    private interface PayAction {
        boolean run();
    }
}
