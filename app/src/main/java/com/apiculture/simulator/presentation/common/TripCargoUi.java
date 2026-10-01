package com.apiculture.simulator.presentation.common;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.apiculture.simulator.R;
import com.apiculture.simulator.presentation.hive.HiveSiteSummaryUi;

import java.util.Map;

/** Botes de todos los tipos de miel de una carga, con sus kilos. */
public final class TripCargoUi {

    private TripCargoUi() {
    }

    public static void bind(@NonNull LayoutInflater inflater, @NonNull LinearLayout host,
            @Nullable Map<String, Double> cargo, @NonNull Context context) {
        host.removeAllViews();
        if (cargo == null || cargo.isEmpty()) {
            host.setVisibility(View.GONE);
            return;
        }
        host.setVisibility(View.VISIBLE);
        for (Map.Entry<String, Double> e : cargo.entrySet()) {
            double kg = e.getValue() == null ? 0.0 : e.getValue();
            if (e.getKey() == null || kg <= 1e-9) {
                continue;
            }
            View jar = inflater.inflate(R.layout.item_trip_honey, host, false);
            ((ImageView) jar.findViewById(R.id.iv_honey))
                    .setImageResource(HiveSiteSummaryUi.floraHoneyJarIcon(e.getKey()));
            TextView nameView = jar.findViewById(R.id.tv_honey_name);
            if (nameView != null) {
                nameView.setText(HiveSiteSummaryUi.floraLabel(context, e.getKey()));
            }
            ((TextView) jar.findViewById(R.id.tv_honey_kg))
                    .setText(context.getString(R.string.dashboard_trip_kg, kg));
            host.addView(jar);
        }
        if (host.getChildCount() == 0) {
            host.setVisibility(View.GONE);
        }
    }
}
