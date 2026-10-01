package com.apiculture.simulator.presentation.market;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Window;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.apiculture.simulator.R;
import com.apiculture.simulator.presentation.common.GameNotice;
import com.apiculture.simulator.presentation.hive.HiveSiteSummaryUi;
import com.google.android.material.button.MaterialButton;

import java.util.Locale;
import java.util.function.Consumer;

/** Venta de un tipo de miel: el jugador elige kilos hasta el stock. */
public final class MarketSellDialog {

    private MarketSellDialog() {
    }

    public static void show(@Nullable Context context, @NonNull MarketPillUi pill,
            @NonNull Consumer<Double> onConfirmKg) {
        Activity activity = resolveActivity(context);
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            return;
        }
        activity.runOnUiThread(() -> present(activity, pill, onConfirmKg));
    }

    private static void present(@NonNull Activity activity, @NonNull MarketPillUi pill,
            @NonNull Consumer<Double> onConfirmKg) {
        if (activity.isFinishing() || activity.isDestroyed()) {
            return;
        }
        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_market_sell);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        ImageView jar = dialog.findViewById(R.id.iv_sell_flora);
        TextView title = dialog.findViewById(R.id.tv_sell_flora);
        TextView priceLine = dialog.findViewById(R.id.tv_sell_price);
        EditText etKg = dialog.findViewById(R.id.et_sell_kg);
        SeekBar seek = dialog.findViewById(R.id.seek_sell_kg);
        MaterialButton btnMax = dialog.findViewById(R.id.btn_sell_max);
        TextView tvTotal = dialog.findViewById(R.id.tv_sell_total);
        MaterialButton btnConfirm = dialog.findViewById(R.id.btn_sell_confirm);
        MaterialButton btnCancel = dialog.findViewById(R.id.btn_sell_cancel);

        int icon = HiveSiteSummaryUi.floraHoneyJarIcon(pill.floraKey);
        if (icon != 0) {
            jar.setImageResource(icon);
        }
        title.setText(pill.title);
        final double stock = Math.max(0.0, pill.maxSellKg());
        if (Double.isFinite(pill.demandLeftKg)) {
            priceLine.setText(activity.getString(R.string.market_sell_price_stock_demand,
                    pill.priceEurPerKg, pill.userStockKg, pill.demandLeftKg, stock));
        } else {
            priceLine.setText(activity.getString(R.string.market_sell_price_stock,
                    pill.priceEurPerKg, pill.userStockKg));
        }
        final int steps = Math.max(1, (int) Math.round(stock * 100.0));
        seek.setMax(steps);

        final boolean[] syncing = {false};

        Runnable refreshTotal = () -> {
            double kg = parseKg(etKg.getText().toString(), stock);
            double coins = Math.round(kg * pill.priceEurPerKg * 100.0) / 100.0;
            tvTotal.setText(activity.getString(R.string.market_sell_total, coins));
        };

        Consumer<Double> applyKg = kg -> {
            double capped = clampKg(kg, stock);
            syncing[0] = true;
            etKg.setText(formatKg(capped));
            etKg.setSelection(etKg.getText().length());
            int progress = (int) Math.round(capped * 100.0);
            seek.setProgress(Math.max(0, Math.min(steps, progress)));
            syncing[0] = false;
            refreshTotal.run();
        };

        double initial = stock >= 1.0 ? 1.0 : stock;
        applyKg.accept(initial);

        etKg.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (syncing[0]) {
                    return;
                }
                double kg = parseKg(s.toString(), stock);
                syncing[0] = true;
                int progress = (int) Math.round(kg * 100.0);
                seek.setProgress(Math.max(0, Math.min(steps, progress)));
                syncing[0] = false;
                refreshTotal.run();
            }
        });

        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser || syncing[0]) {
                    return;
                }
                double kg = Math.min(stock, progress / 100.0);
                syncing[0] = true;
                etKg.setText(formatKg(kg));
                etKg.setSelection(etKg.getText().length());
                syncing[0] = false;
                refreshTotal.run();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        btnMax.setOnClickListener(v -> applyKg.accept(stock));
        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnConfirm.setOnClickListener(v -> {
            double kg = parseKg(etKg.getText().toString(), stock);
            if (kg <= 1e-6) {
                GameNotice.show(activity, R.string.market_sell_need_amount);
                return;
            }
            if (kg > stock + 1e-6) {
                if (Double.isFinite(pill.demandLeftKg) && kg > pill.demandLeftKg + 1e-6) {
                    GameNotice.show(activity, activity.getString(
                            R.string.market_sell_over_demand, pill.demandLeftKg, pill.title));
                } else {
                    GameNotice.show(activity, R.string.market_sell_over_stock);
                }
                return;
            }
            dialog.dismiss();
            onConfirmKg.accept(kg);
        });
        dialog.show();
    }

    private static double parseKg(@Nullable String raw, double stock) {
        if (raw == null) {
            return 0.0;
        }
        String t = raw.trim().replace(',', '.');
        if (t.isEmpty()) {
            return 0.0;
        }
        try {
            return clampKg(Double.parseDouble(t), stock);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private static double clampKg(double kg, double stock) {
        if (Double.isNaN(kg) || kg < 0.0) {
            return 0.0;
        }
        return Math.min(stock, kg);
    }

    private static String formatKg(double kg) {
        return String.format(Locale.getDefault(), "%.2f", Math.round(kg * 100.0) / 100.0);
    }

    @Nullable
    private static Activity resolveActivity(@Nullable Context context) {
        Context walk = context;
        while (walk instanceof ContextWrapper) {
            if (walk instanceof Activity) {
                Activity a = (Activity) walk;
                if (!a.isFinishing() && !a.isDestroyed()) {
                    return a;
                }
            }
            walk = ((ContextWrapper) walk).getBaseContext();
        }
        return null;
    }
}
