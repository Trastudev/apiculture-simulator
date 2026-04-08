package com.apiculture.simulator.data.repository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Resumen de un día de calendario aplicado en un tick encadenado. */
public final class DailyTickSummary {

    public final int dayKey;
    public final List<HiveDayStartupSummary> summaries;

    public DailyTickSummary(int dayKey, List<HiveDayStartupSummary> summaries) {
        this.dayKey = dayKey;
        this.summaries = summaries == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(summaries));
    }
}
