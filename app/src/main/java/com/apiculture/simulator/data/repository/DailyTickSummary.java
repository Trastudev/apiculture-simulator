package com.apiculture.simulator.data.repository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Resumen de un día de calendario aplicado en un tick encadenado. */
public final class DailyTickSummary {

    public final int dayKey;
    public final List<HiveDayStartupSummary> summaries;
    public final int velutinaHiveCount;
    public final int queenMissingCount;
    public final int swarmCount;
    public final List<String> cropNotes;
    public final double forageFromNeighborKg;
    public final double forageTakenByNeighborsKg;

    public DailyTickSummary(int dayKey, List<HiveDayStartupSummary> summaries) {
        this(dayKey, summaries, 0, 0, 0, Collections.emptyList(), 0, 0);
    }

    public DailyTickSummary(int dayKey, List<HiveDayStartupSummary> summaries,
            int velutinaHiveCount, int queenMissingCount, int swarmCount) {
        this(dayKey, summaries, velutinaHiveCount, queenMissingCount, swarmCount, Collections.emptyList(), 0, 0);
    }

    public DailyTickSummary(int dayKey, List<HiveDayStartupSummary> summaries,
            int velutinaHiveCount, int queenMissingCount, int swarmCount,
            List<String> cropNotes) {
        this(dayKey, summaries, velutinaHiveCount, queenMissingCount, swarmCount, cropNotes, 0, 0);
    }

    public DailyTickSummary(int dayKey, List<HiveDayStartupSummary> summaries,
            int velutinaHiveCount, int queenMissingCount, int swarmCount,
            List<String> cropNotes, double forageFromNeighborKg, double forageTakenByNeighborsKg) {
        this.dayKey = dayKey;
        this.summaries = summaries == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(summaries));
        this.velutinaHiveCount = Math.max(0, velutinaHiveCount);
        this.queenMissingCount = Math.max(0, queenMissingCount);
        this.swarmCount = Math.max(0, swarmCount);
        this.cropNotes = cropNotes == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(cropNotes));
        this.forageFromNeighborKg = Math.max(0.0, forageFromNeighborKg);
        this.forageTakenByNeighborsKg = Math.max(0.0, forageTakenByNeighborsKg);
    }

    public double totalHoneyKg() {
        double t = 0;
        for (HiveDayStartupSummary s : summaries) {
            t += s.honeyKg;
        }
        return t;
    }

    public int totalWorkerNet() {
        int t = 0;
        for (HiveDayStartupSummary s : summaries) {
            t += s.workerNet;
        }
        return t;
    }
}
