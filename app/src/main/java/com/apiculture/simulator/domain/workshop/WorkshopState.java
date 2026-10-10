package com.apiculture.simulator.domain.workshop;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.apiculture.simulator.domain.workshop.WorkshopRules.Format;
import com.apiculture.simulator.domain.workshop.WorkshopRules.Machine;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Obrador de un jugador: edificio, máquinas, tandas en curso y lo ya envasado. */
public final class WorkshopState {

    public static final class Batch {
        public String id;
        public String flora;
        public double kg;
        /** Apiario de origen, para mostrarlo. */
        public String source;
        public long createdAt;
        /** Máquina en la que está o a la que espera. */
        public Machine stage;
        public boolean inMachine;
        /** Desde cuándo puede entrar en {@link #stage}. */
        public long waitingSince;
        public long startAt;
        public long endAt;
        @Nullable
        public Format format;
        public double waxKg;

        public boolean waitingFormat() {
            return stage == Machine.PACKER && !inMachine && format == null;
        }
    }

    public static final class Packed {
        public String flora;
        public Format format;
        public double kg;
        public int jars;
    }

    @Nullable
    public String hexId;
    public final Map<Machine, Integer> levels = new EnumMap<>(Machine.class);
    public final List<Batch> batches = new ArrayList<>();
    public final List<Packed> packed = new ArrayList<>();
    public double waxKg;

    public boolean built() {
        return hexId != null && !hexId.isEmpty();
    }

    /** Todas las máquinas a nivel 1 o más: una tanda puede llegar al final sin atascarse. */
    public boolean complete() {
        for (Machine m : Machine.values()) {
            if (level(m) <= 0) {
                return false;
            }
        }
        return true;
    }

    public int level(@NonNull Machine m) {
        Integer l = levels.get(m);
        return l == null ? 0 : l;
    }

    @Nullable
    public Batch batch(@Nullable String id) {
        if (id == null) {
            return null;
        }
        for (Batch b : batches) {
            if (id.equals(b.id)) {
                return b;
            }
        }
        return null;
    }

    @Nullable
    public Packed packed(@Nullable String flora, @NonNull Format format) {
        for (Packed p : packed) {
            if (p.format == format && p.flora != null && p.flora.equals(flora)) {
                return p;
            }
        }
        return null;
    }

    void addPacked(@NonNull String flora, @NonNull Format format, double kg, int jars) {
        if (kg <= 1e-9 && jars <= 0) {
            return;
        }
        Packed p = packed(flora, format);
        if (p == null) {
            p = new Packed();
            p.flora = flora;
            p.format = format;
            packed.add(p);
        }
        p.kg += kg;
        p.jars += jars;
    }
}
