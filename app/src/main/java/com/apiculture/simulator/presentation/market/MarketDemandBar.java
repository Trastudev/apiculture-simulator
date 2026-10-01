package com.apiculture.simulator.presentation.market;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.apiculture.simulator.R;
import com.apiculture.simulator.domain.map.ProvincialMarket;
import com.apiculture.simulator.domain.map.ProvincialMarketCatalog;
import com.apiculture.simulator.domain.market.HoneyMarketEngine;
import com.apiculture.simulator.domain.market.TerritorialMarketRules;
import com.apiculture.simulator.domain.parcel.HexFlora;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Perfil de volumen y competencia por mercado y tipo de miel. */
public final class MarketDemandBar {

    public static final int PAGE_SIZE = 5;

    private MarketDemandBar() {
    }

    @NonNull
    public static List<Row> collect(@NonNull Context context,
            @Nullable ProvincialMarket market) {
        List<Row> rows = new ArrayList<>();
        if (market == null || market.international) {
            return rows;
        }
        List<ProvincialMarket> all = ProvincialMarketCatalog.resolve(context, market.region);
        Map<String, Double> shares = TerritorialMarketRules.demandShares(market.region);
        for (String flora : HexFlora.FLORA_TYPES) {
            String key = HoneyMarketEngine.canonicalFloraKey(flora);
            if (shares == null || !shares.containsKey(key)) {
                continue;
            }
            rows.add(new Row(
                    flora,
                    TerritorialMarketRules.volumeScore01(market, key, all),
                    TerritorialMarketRules.competition01(market, key, all)));
        }
        Collections.sort(rows, (a, b) -> Double.compare(
                b.volume01 * 0.45 + b.competition01 * 0.55,
                a.volume01 * 0.45 + a.competition01 * 0.55));
        return rows;
    }

    public static void bind(@NonNull ViewGroup container, @Nullable ProvincialMarket market,
            @NonNull Context context) {
        bindPage(container, collect(context, market));
    }

    public static void bindPage(@NonNull ViewGroup container, @Nullable List<Row> rows) {
        if (container == null) {
            return;
        }
        container.removeAllViews();
        if (rows == null || rows.isEmpty()) {
            container.setVisibility(View.GONE);
            return;
        }
        container.setVisibility(View.VISIBLE);
        LayoutInflater inflater = LayoutInflater.from(container.getContext());
        for (Row row : rows) {
            View v = inflater.inflate(R.layout.item_flora_saturation, container, false);
            TextView name = v.findViewById(R.id.tv_flora_saturation_name);
            TextView value = v.findViewById(R.id.tv_flora_saturation_value);
            ProgressBar bar = v.findViewById(R.id.bar_flora_saturation);
            if (name != null) {
                name.setText(row.label);
            }
            apply(v, value, bar, row);
            container.addView(v);
        }
    }

    static void apply(@NonNull View host, @Nullable TextView value, @Nullable ProgressBar bar,
            @NonNull Row row) {
        if (value == null || bar == null) {
            return;
        }
        Context context = host.getContext();
        int competitionPct = (int) Math.round(Math.max(0.0, Math.min(1.0, row.competition01)) * 100.0);
        int tone = competitionPct >= 67 ? 2 : (competitionPct >= 34 ? 1 : 0);
        int color = ContextCompat.getColor(context,
                tone == 0 ? R.color.dash_good : (tone == 1 ? R.color.dash_warning : R.color.dash_bad));
        value.setText(context.getString(
                R.string.map_market_profile_row,
                bandLabel(context, row.volume01),
                bandLabel(context, row.competition01)));
        value.setTextColor(color);
        bar.setMax(100);
        bar.setProgress(competitionPct);
        bar.setProgressTintList(android.content.res.ColorStateList.valueOf(color));
    }

    private static String bandLabel(@NonNull Context context, double score) {
        if (score >= 0.67) {
            return context.getString(R.string.map_market_band_high);
        }
        if (score >= 0.34) {
            return context.getString(R.string.map_market_band_medium);
        }
        return context.getString(R.string.map_market_band_low);
    }

    static final class Row {
        final String label;
        final double volume01;
        final double competition01;

        Row(String label, double volume01, double competition01) {
            this.label = label;
            this.volume01 = Math.max(0.0, Math.min(1.0, volume01));
            this.competition01 = Math.max(0.0, Math.min(1.0, competition01));
        }
    }
}
