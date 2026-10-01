package com.apiculture.simulator.presentation.dashboard;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.apiculture.simulator.ApicultureApp;
import com.apiculture.simulator.R;
import com.apiculture.simulator.data.repository.EconomyLedger;
import com.apiculture.simulator.databinding.DialogEconomyLedgerBinding;
import com.apiculture.simulator.databinding.ItemEconomyRowBinding;

import java.text.DateFormat;
import java.text.NumberFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class EconomyDialog {

    private EconomyDialog() {
    }

    public static void show(@NonNull Context context) {
        Context appCtx = context.getApplicationContext();
        if (!(appCtx instanceof ApicultureApp)) {
            return;
        }
        List<EconomyLedger.Entry> rows = ((ApicultureApp) appCtx).getEconomyRepository().movements();
        Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        DialogEconomyLedgerBinding binding = DialogEconomyLedgerBinding.inflate(LayoutInflater.from(context));
        dialog.setContentView(binding.getRoot());
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        binding.tvEconomyEmpty.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
        DateFormat when = DateFormat.getDateTimeInstance(
                DateFormat.SHORT, DateFormat.SHORT, new Locale("es", "ES"));
        NumberFormat money = NumberFormat.getNumberInstance(new Locale("es", "ES"));
        money.setMaximumFractionDigits(0);
        LayoutInflater inflater = LayoutInflater.from(context);
        int good = ContextCompat.getColor(context, R.color.dash_good);
        int bad = ContextCompat.getColor(context, R.color.dash_bad);
        for (EconomyLedger.Entry row : rows) {
            ItemEconomyRowBinding item = ItemEconomyRowBinding.inflate(
                    inflater, binding.llEconomyRows, true);
            item.tvEconomyConcept.setText(row.concept);
            boolean income = row.amount > 0;
            item.tvEconomyAmount.setText((income ? "+" : "−")
                    + money.format(Math.abs(Math.round(row.amount))) + " B");
            item.tvEconomyAmount.setTextColor(income ? good : bad);
            item.tvEconomyWhen.setText(when.format(new Date(row.epochMs)));
        }
        binding.btnEconomyClose.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }
}