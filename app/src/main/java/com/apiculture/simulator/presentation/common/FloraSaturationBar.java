package com.apiculture.simulator.presentation.common;

import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.apiculture.simulator.R;
import com.apiculture.simulator.domain.game.HexFloraSaturation;

import java.util.List;
import java.util.function.Function;

public final class FloraSaturationBar {

    private FloraSaturationBar() {
    }

    public static void bindItem(@NonNull View row, @Nullable HexFloraSaturation sat) {
        applyRow(row.getContext(),
                row.findViewById(R.id.tv_flora_saturation_value),
                row.findViewById(R.id.bar_flora_saturation),
                sat);
    }

    public static void bind(@Nullable View host, @Nullable HexFloraSaturation sat) {
        if (host == null) {
            return;
        }
        View block = host.findViewById(R.id.ll_flora_saturation);
        if (block == null) {
            block = host.findViewById(R.id.include_flora_saturation);
        }
        TextView value = host.findViewById(R.id.tv_flora_saturation_value);
        ProgressBar bar = host.findViewById(R.id.bar_flora_saturation);
        if (value == null || bar == null) {
            return;
        }
        if (block != null) {
            block.setVisibility(View.VISIBLE);
        }
        applyRow(host.getContext(), value, bar, sat);
    }

    public static void bindLines(
            @Nullable ViewGroup container,
            @Nullable List<HexFloraSaturation.Line> lines,
            @Nullable Function<String, String> floraLabel) {
        if (container == null) {
            return;
        }
        container.removeAllViews();
        if (lines == null || lines.isEmpty()) {
            container.setVisibility(View.GONE);
            return;
        }
        container.setVisibility(View.VISIBLE);
        LayoutInflater inflater = LayoutInflater.from(container.getContext());
        TextView header = new TextView(container.getContext());
        header.setText(R.string.flora_saturation_label);
        header.setTextColor(ContextCompat.getColor(container.getContext(), R.color.event_ink_muted));
        header.setTextSize(11f);
        container.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        for (HexFloraSaturation.Line line : lines) {
            if (line == null) {
                continue;
            }
            View row = inflater.inflate(R.layout.item_flora_saturation, container, false);
            TextView name = row.findViewById(R.id.tv_flora_saturation_name);
            TextView value = row.findViewById(R.id.tv_flora_saturation_value);
            ProgressBar bar = row.findViewById(R.id.bar_flora_saturation);
            String key = line.floraKey;
            String label = floraLabel != null ? floraLabel.apply(key) : key;
            if (name != null) {
                name.setText(label != null && !label.isEmpty() ? label : key);
            }
            applyRow(container.getContext(), value, bar, line.sat);
            container.addView(row);
        }
    }

    private static void applyRow(
            @NonNull android.content.Context ctx,
            @Nullable TextView value,
            @Nullable ProgressBar bar,
            @Nullable HexFloraSaturation sat) {
        if (value == null || bar == null) {
            return;
        }
        HexFloraSaturation s = sat != null ? sat : new HexFloraSaturation(0, 0, 0);
        int tone = s.tone();
        int color = ContextCompat.getColor(ctx,
                tone == 0 ? R.color.dash_good : (tone == 1 ? R.color.dash_warning : R.color.dash_bad));
        int label = tone == 0
                ? R.string.flora_saturation_loose
                : (tone == 1 ? R.string.flora_saturation_tight : R.string.flora_saturation_full);
        value.setText(ctx.getString(R.string.flora_saturation_value, s.barPercent(),
                ctx.getString(label)));
        value.setTextColor(color);
        bar.setProgress(s.barPercent());
        bar.setProgressTintList(ColorStateList.valueOf(color));
    }
}
