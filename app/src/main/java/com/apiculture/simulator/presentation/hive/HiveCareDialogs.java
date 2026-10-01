package com.apiculture.simulator.presentation.hive;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.ViewGroup;
import android.view.Window;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.apiculture.simulator.R;
import com.apiculture.simulator.data.local.entity.HiveEntity;
import com.apiculture.simulator.data.repository.EventInventoryStore;
import com.apiculture.simulator.databinding.DialogHiveCareBinding;
import com.apiculture.simulator.domain.game.GameCalendar;
import com.apiculture.simulator.domain.game.HiveCareRules;
import com.apiculture.simulator.domain.game.HiveFeedType;
import com.apiculture.simulator.domain.game.HiveFeedingBonuses;

import java.time.LocalDate;

/**
 * Diálogos crema de tratamiento y alimentación: días restantes, ampliar con stock, o ir a la tienda.
 */
public final class HiveCareDialogs {

    public interface ShopNavigator {
        void openShop();
    }

    private HiveCareDialogs() {
    }

    public static void showTreat(@NonNull Fragment fragment, @NonNull HiveEntity hive,
            @NonNull HiveViewModel viewModel, @NonNull ShopNavigator shop) {
        if (!fragment.isAdded()) {
            return;
        }
        int left = Math.max(0, hive.varroaTreatmentDaysRemaining);
        int add = HiveCareRules.TREAT_DAYS;
        String message = left > 0
                ? fragment.getString(R.string.hive_care_treat_active, left, add)
                : fragment.getString(R.string.hive_care_treat_start, add,
                        EventInventoryStore.treatments(fragment.requireContext()));
        String action = left > 0
                ? fragment.getString(R.string.hive_care_extend_days, add)
                : fragment.getString(R.string.hive_treat_confirm_inv);
        show(fragment, fragment.getString(R.string.hive_treat_title), R.drawable.ic_tratamiento, message,
                action, null, () -> {
                    if (EventInventoryStore.treatments(fragment.requireContext()) < 1) {
                        showOutOfStock(fragment, R.string.hive_care_no_treat, shop);
                        return;
                    }
                    viewModel.treatDisease(hive, msg -> onCareResult(fragment, msg, R.string.hive_treat_ok, shop,
                            R.string.hive_care_no_treat));
                }, null);
    }

    public static void showFeed(@NonNull Fragment fragment, @NonNull HiveEntity hive,
            @NonNull HiveViewModel viewModel, @NonNull ShopNavigator shop) {
        if (!fragment.isAdded()) {
            return;
        }
        int today = GameCalendar.toDayKey(LocalDate.now(GameCalendar.userTimeZone()));
        int left = HiveFeedingBonuses.feedingDaysRemaining(hive, today);
        int owned = EventInventoryStore.feed(fragment.requireContext());
        String message = left > 0
                ? fragment.getString(R.string.hive_care_feed_active, left)
                : fragment.getString(R.string.hive_care_feed_start, owned);
        show(fragment, fragment.getString(R.string.hive_feed_title), R.drawable.ic_apialimento, message,
                fragment.getString(R.string.hive_care_extend_days, HiveCareRules.FEED_DAYS),
                null,
                () -> spendFeed(fragment, hive, viewModel, shop),
                null);
    }

    private static void spendFeed(@NonNull Fragment fragment, @NonNull HiveEntity hive,
            @NonNull HiveViewModel viewModel, @NonNull ShopNavigator shop) {
        if (!fragment.isAdded()) {
            return;
        }
        if (EventInventoryStore.feed(fragment.requireContext()) < 1) {
            showOutOfStock(fragment, R.string.hive_care_no_feed, shop);
            return;
        }
        viewModel.applyHiveFeeding(hive, HiveFeedType.DAYS_7,
                msg -> onCareResult(fragment, msg, R.string.hive_feed_ok, shop, R.string.hive_care_no_feed));
    }

    public static void showOutOfStock(@NonNull Fragment fragment, int messageRes,
            @NonNull ShopNavigator shop) {
        if (!fragment.isAdded()) {
            return;
        }
        show(fragment, fragment.getString(R.string.hive_care_no_stock_title), 0,
                fragment.getString(messageRes),
                fragment.getString(R.string.inventory_go_shop),
                null,
                shop::openShop,
                null);
    }

    private static void onCareResult(@NonNull Fragment fragment, @Nullable String msg, int okRes,
            @NonNull ShopNavigator shop, int noStockRes) {
        if (!fragment.isAdded()) {
            return;
        }
        if (msg == null) {
            com.apiculture.simulator.presentation.common.GameNotice.showSuccess(
                    fragment.requireContext(), okRes);
            return;
        }
        if ("SHOP_TREAT".equals(msg) || "SHOP_FEED".equals(msg)) {
            showOutOfStock(fragment, noStockRes, shop);
            return;
        }
        com.apiculture.simulator.presentation.common.GameNotice.show(fragment.requireContext(), msg);
    }

    private static void show(@NonNull Fragment fragment, @NonNull String title, int imageRes,
            @NonNull String message, @NonNull String primary, @Nullable String secondary,
            @NonNull Runnable onPrimary, @Nullable Runnable onSecondary) {
        DialogHiveCareBinding d = DialogHiveCareBinding.inflate(fragment.getLayoutInflater());
        d.tvCareTitle.setText(title);
        d.tvCareMessage.setText(message);
        if (imageRes != 0) {
            android.graphics.Bitmap art = IconBitmaps.decode(fragment.getResources(), imageRes, 256);
            if (art != null) {
                d.ivCareArt.setImageBitmap(art);
            }
            d.ivCareArt.setVisibility(android.view.View.VISIBLE);
        } else {
            d.ivCareArt.setVisibility(android.view.View.GONE);
        }
        d.btnCarePrimary.setText(primary);
        if (secondary != null) {
            d.btnCareSecondary.setVisibility(android.view.View.VISIBLE);
            d.btnCareSecondary.setText(secondary);
        } else {
            d.btnCareSecondary.setVisibility(android.view.View.GONE);
        }
        Dialog dialog = new Dialog(fragment.requireContext());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(d.getRoot());
        dialog.setCancelable(true);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        d.btnCareCancel.setOnClickListener(v -> dialog.dismiss());
        d.btnCarePrimary.setOnClickListener(v -> {
            dialog.dismiss();
            onPrimary.run();
        });
        d.btnCareSecondary.setOnClickListener(v -> {
            dialog.dismiss();
            if (onSecondary != null) {
                onSecondary.run();
            }
        });
        dialog.show();
    }
}
