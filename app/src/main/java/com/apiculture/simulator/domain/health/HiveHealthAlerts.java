package com.apiculture.simulator.domain.health;

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
 * Alertas de salud / varroa / reservas para la UI.
 */
public final class HiveHealthAlerts {

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
        if (h == null) {
            return Collections.emptyList();
        }
        List<Tag> out = new ArrayList<>();
        int workers = pop != null ? pop.workersAdult : Math.max(0, h.beeCount);

        if (pop != null && (pop.queenMode == QueenMode.ORPHANED
                || pop.queenMode == QueenMode.REPLACE_WINDOW
                || pop.queenMode == QueenMode.COLLAPSED)) {
            out.add(new Tag("Reina muerta", R.drawable.bg_swarm_warn_pill));
        }

        boolean swarm = ColonyGameRules.swarmRiskForAdultWorkers(workers) > 0.0
                || workers >= ColonyGameRules.SPLIT_RECOMMEND_BEES;
        if (swarm) {
            out.add(new Tag("Alerta enjambrazón", R.drawable.bg_swarm_warn_pill));
        }

        if (h.health < 40) {
            out.add(new Tag(String.format(Locale.getDefault(),
                    "Salud crítica (%d%%)", h.health), R.drawable.bg_swarm_warn_pill));
        } else if (h.health < 60) {
            out.add(new Tag(String.format(Locale.getDefault(),
                    "Salud baja (%d%%)", h.health), R.drawable.bg_honey_cap_warn_pill));
        } else if (h.health < 80) {
            out.add(new Tag(String.format(Locale.getDefault(),
                    "Salud regular (%d%%)", h.health), R.drawable.bg_honey_cap_warn_pill));
        }

        double varroaStart = GameBalanceConfig.varroaKStartPct;
        if (h.varroaPct >= 10) {
            out.add(new Tag(String.format(Locale.getDefault(),
                    "Varroa crítica (%.1f%%)", h.varroaPct), R.drawable.bg_swarm_warn_pill));
        } else if (h.varroaPct >= varroaStart) {
            out.add(new Tag(String.format(Locale.getDefault(),
                    "Varroa afectando (%.1f%%)", h.varroaPct), R.drawable.bg_honey_cap_warn_pill));
        }

        if (HiveHoneyRules.isHoneyAtCapacity(h)) {
            out.add(new Tag("Reservas de miel a tope", R.drawable.bg_honey_cap_warn_pill));
        } else if (h.honeyProduction < HiveHoneyRules.MIN_HIVE_STOCK_KG) {
            out.add(new Tag("Reservas de miel al mínimo", R.drawable.bg_swarm_warn_pill));
        }

        return out;
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
