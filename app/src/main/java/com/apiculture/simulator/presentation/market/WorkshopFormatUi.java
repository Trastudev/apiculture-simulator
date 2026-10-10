package com.apiculture.simulator.presentation.market;

import android.content.Context;

import androidx.annotation.NonNull;

import com.apiculture.simulator.R;
import com.apiculture.simulator.domain.workshop.JarMix;
import com.apiculture.simulator.domain.workshop.WorkshopRules.Format;

public final class WorkshopFormatUi {

    private WorkshopFormatUi() {
    }

    /** "2 × tarro de 1 kg + 1 × tarro de 500 g". */
    @NonNull
    public static String mixLabel(@NonNull Context c, @NonNull JarMix mix) {
        StringBuilder sb = new StringBuilder();
        for (Format f : new Format[] { Format.JAR_1000, Format.JAR_500, Format.JAR_250 }) {
            int n = mix.count(f);
            if (n <= 0) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(" + ");
            }
            sb.append(c.getString(R.string.workshop_mix_part, n, label(c, f)));
        }
        return sb.toString();
    }

    /** Reparto de una tanda: "2 × tarro de 1 kg + 3 × tarro de 250 g + 1,5 kg a granel". */
    @NonNull
    public static String splitLabel(@NonNull Context c, @NonNull JarMix mix, double batchKg) {
        String jars = mixLabel(c, mix);
        double bulk = Math.max(0.0, batchKg - mix.kg());
        if (bulk < 0.05) {
            return jars;
        }
        String rest = c.getString(R.string.workshop_mix_bulk,
                String.format(java.util.Locale.getDefault(), bulk >= 10 ? "%.0f" : "%.1f", bulk));
        return jars.isEmpty() ? rest : jars + " + " + rest;
    }

    @NonNull
    public static String label(@NonNull Context c, @NonNull Format format) {
        switch (format) {
            case JAR_1000: return c.getString(R.string.workshop_format_jar_1000);
            case JAR_500: return c.getString(R.string.workshop_format_jar_500);
            case JAR_250: return c.getString(R.string.workshop_format_jar_250);
            default: return c.getString(R.string.workshop_format_bulk);
        }
    }
}
