package com.apiculture.simulator.data.repository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Resultado de un tick: uno o varios días de simulación aplicados al abrir la app
 * (p. ej. tras varios días sin abrirla).
 */
public final class TickAppliedDayResult {

    public static final TickAppliedDayResult NONE = new TickAppliedDayResult(Collections.emptyList());

    public final List<DailyTickSummary> days;

    public TickAppliedDayResult(List<DailyTickSummary> days) {
        if (days == null || days.isEmpty()) {
            this.days = Collections.emptyList();
        } else {
            this.days = Collections.unmodifiableList(new ArrayList<>(days));
        }
    }

    /** Mayor {@code dayKey} incluido en este lote; 0 si {@link #days} está vacía. */
    public int maxDayKey() {
        int m = 0;
        for (DailyTickSummary d : days) {
            if (d.dayKey > m) {
                m = d.dayKey;
            }
        }
        return m;
    }
}
