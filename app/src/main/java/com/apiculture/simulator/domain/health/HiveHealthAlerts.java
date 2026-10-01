package com.apiculture.simulator.domain.health;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.apiculture.simulator.R;
import com.apiculture.simulator.data.local.entity.HiveEntity;
import com.apiculture.simulator.domain.game.ColonyGameRules;
import com.apiculture.simulator.domain.game.GameBalanceConfig;
import com.apiculture.simulator.domain.game.HiveHoneyRules;
import com.apiculture.simulator.domain.population.HivePopulationState;
import com.apiculture.simulator.domain.population.QueenMode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Alertas de salud / varroa / reservas / flora para la UI.
 */
public final class HiveHealthAlerts {

    /** Floración por debajo de este umbral (0–1) dispara aviso. */
    public static final double FLORA_LOW_THRESHOLD = 0.20;

    public static final class Tag {
        @NonNull
        public final String text;
        /** Drawable de fondo tipo pill. */
        public final int backgroundRes;

        public Tag(@NonNull String text, int backgroundRes) {
            this.text = text;
            this.backgroundRes = backgroundRes;
        }
    }

    private HiveHealthAlerts() {
    }

    /**
     * Etiquetas visibles bajo la cabecera de la colmena.
     */
    @NonNull
    public static List<Tag> alertTags(@Nullable HiveEntity h, @Nullable HivePopulationState pop) {
        return alertTags(null, h, pop, Double.NaN);
    }

    @NonNull
    public static List<Tag> alertTags(
            @Nullable HiveEntity h,
            @Nullable HivePopulationState pop,
            double forage01) {
        return alertTags(null, h, pop, forage01);
    }

    @NonNull
    public static List<Tag> alertTags(
            @Nullable Context context,
            @Nullable HiveEntity h,
            @Nullable HivePopulationState pop,
            double forage01) {
        if (h == null) {
            return Collections.emptyList();
        }
        List<Tag> out = new ArrayList<>();
        int workers = pop != null ? pop.workersAdult : Math.max(0, h.beeCount);

        if (pop != null && (pop.queenMode == QueenMode.ORPHANED
                || pop.queenMode == QueenMode.REPLACE_WINDOW
                || pop.queenMode == QueenMode.COLLAPSED)) {
            out.add(new Tag(text(context, R.string.hive_alert_queen_dead, "Reina muerta"),
                    R.drawable.bg_swarm_warn_pill));
        }

        boolean swarm = ColonyGameRules.swarmRiskForAdultWorkers(workers) > 0.0
                || workers >= ColonyGameRules.SPLIT_RECOMMEND_BEES;
        if (swarm) {
            out.add(new Tag(text(context, R.string.hive_alert_swarm, "Alerta enjambrazón"),
                    R.drawable.bg_swarm_warn_pill));
        }

        if (h.health < 40) {
            out.add(new Tag(text(context, R.string.hive_alert_health_critical, "Salud crítica (%d%%)", h.health),
                    R.drawable.bg_swarm_warn_pill));
        } else if (h.health < 60) {
            out.add(new Tag(text(context, R.string.hive_alert_health_low, "Salud baja (%d%%)", h.health),
                    R.drawable.bg_honey_cap_warn_pill));
        } else if (h.health < 80) {
            out.add(new Tag(text(context, R.string.hive_alert_health_fair, "Salud regular (%d%%)", h.health),
                    R.drawable.bg_honey_cap_warn_pill));
        }

        double varroaStart = GameBalanceConfig.varroaKStartPct;
        if (h.varroaPct >= 10) {
            out.add(new Tag(text(context, R.string.hive_alert_varroa_critical, "Varroa crítica (%.1f%%)", h.varroaPct),
                    R.drawable.bg_swarm_warn_pill));
        } else if (h.varroaPct >= varroaStart) {
            out.add(new Tag(text(context, R.string.hive_alert_varroa, "Varroa afectando (%.1f%%)", h.varroaPct),
                    R.drawable.bg_honey_cap_warn_pill));
        }

        if (HiveHoneyRules.isHoneyAtCapacity(h)) {
            out.add(new Tag(text(context, R.string.hive_alert_honey_full, "Reservas de miel a tope"),
                    R.drawable.bg_honey_cap_warn_pill));
        } else if (h.honeyProduction < HiveHoneyRules.MIN_HIVE_STOCK_KG) {
            out.add(new Tag(text(context, R.string.hive_alert_honey_min, "Reservas de miel al mínimo"),
                    R.drawable.bg_swarm_warn_pill));
        }

        if (!Double.isNaN(forage01) && forage01 < FLORA_LOW_THRESHOLD) {
            int pct = (int) Math.round(Math.max(0.0, Math.min(1.0, forage01)) * 100.0);
            out.add(new Tag(text(context, R.string.hive_alert_flora_low, "Flora baja (%d%%)", pct),
                    R.drawable.bg_honey_cap_warn_pill));
        }

        return out;
    }

    @NonNull
    private static String text(@Nullable Context context, int res, @NonNull String fallback, Object... args) {
        if (context == null) {
            return args.length == 0 ? fallback : String.format(Locale.getDefault(), fallback, args);
        }
        return args.length == 0 ? context.getString(res) : context.getString(res, args);
    }

    public static boolean needsAttention(
            @Nullable HiveEntity h,
            @Nullable HivePopulationState pop,
            double forage01) {
        return !alertTags(h, pop, forage01).isEmpty();
    }

    public static List<String> alertsEs(HiveEntity h) {
        List<Tag> tags = alertTags(h, null);
        List<String> out = new ArrayList<>(tags.size());
        for (Tag t : tags) {
            out.add(t.text);
        }
        return out;
    }

    public static String alertsSummaryLineEs(HiveEntity h) {
        List<String> a = alertsEs(h);
        if (a.isEmpty()) {
            return "Sin alertas";
        }
        return String.join(" · ", a);
    }

    public static String formatVarroaPct(HiveEntity h) {
        if (h == null) {
            return "—";
        }
        return String.format(Locale.getDefault(), "%.1f %%", h.varroaPct);
    }
}
