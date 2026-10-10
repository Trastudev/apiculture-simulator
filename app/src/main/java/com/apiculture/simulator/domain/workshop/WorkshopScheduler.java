package com.apiculture.simulator.domain.workshop;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.apiculture.simulator.domain.workshop.WorkshopRules.Format;
import com.apiculture.simulator.domain.workshop.WorkshopRules.Machine;
import com.apiculture.simulator.domain.workshop.WorkshopState.Batch;

import java.util.Iterator;
import java.util.UUID;

/**
 * Hace avanzar las tandas evento a evento en orden de tiempo. Cada máquina atiende por orden de
 * llegada; el envasado espera a que el jugador elija envase.
 */
public final class WorkshopScheduler {

    private WorkshopScheduler() {
    }

    /** Alzas recién llegadas: entran en recepción. */
    @NonNull
    public static Batch receive(@NonNull WorkshopState s, @NonNull String flora, double kg,
            @Nullable String source, long nowMs) {
        Batch b = new Batch();
        b.id = UUID.randomUUID().toString();
        b.flora = flora;
        b.kg = Math.max(0.0, kg);
        b.source = source == null ? "" : source;
        b.createdAt = nowMs;
        b.stage = Machine.RECEPTION;
        b.waitingSince = nowMs;
        s.batches.add(b);
        return b;
    }

    /** El envase se elige antes de envasar; la tanda no adelanta a nadie por esperar. */
    public static boolean chooseFormat(@NonNull WorkshopState s, @NonNull String batchId,
            @NonNull Format format, long nowMs) {
        advance(s, nowMs);
        Batch b = s.batch(batchId);
        if (b == null || (b.stage == Machine.PACKER && b.inMachine)) {
            return false;
        }
        b.format = format;
        b.mix = null;
        if (b.stage == Machine.PACKER) {
            b.waitingSince = Math.max(b.waitingSince, nowMs);
        }
        advance(s, nowMs);
        return true;
    }

    /**
     * Reparto de una tanda: tantos tarros de cada tamaño y el resto a granel. Los tarros no pueden
     * llevarse más miel de la que tiene la tanda.
     */
    public static boolean chooseMix(@NonNull WorkshopState s, @NonNull String batchId,
            @NonNull JarMix mix, long nowMs) {
        advance(s, nowMs);
        Batch b = s.batch(batchId);
        if (b == null || (b.stage == Machine.PACKER && b.inMachine) || mix.kg() > b.kg + 1e-6) {
            return false;
        }
        b.mix = mix.total() > 0 ? mix : null;
        b.format = dominant(mix);
        if (b.stage == Machine.PACKER) {
            b.waitingSince = Math.max(b.waitingSince, nowMs);
        }
        advance(s, nowMs);
        return true;
    }

    /** El envase que más miel se lleva del reparto (el bidón si no hay tarros). */
    @NonNull
    private static Format dominant(@NonNull JarMix mix) {
        Format best = Format.BULK;
        double bestKg = 0;
        for (Format f : Format.values()) {
            double kg = f == Format.BULK ? 0 : mix.count(f) * f.jarKg;
            if (kg > bestKg) {
                bestKg = kg;
                best = f;
            }
        }
        return best;
    }

    /** Tras comprar o mejorar: lo que esperaba a esa máquina cuenta desde ahora. */
    public static void setLevel(@NonNull WorkshopState s, @NonNull Machine m, int level, long nowMs) {
        advance(s, nowMs);
        s.levels.put(m, Math.max(0, Math.min(WorkshopRules.MAX_LEVEL, level)));
        for (Batch b : s.batches) {
            if (b.stage == m && !b.inMachine) {
                b.waitingSince = Math.max(b.waitingSince, nowMs);
            }
        }
        advance(s, nowMs);
    }

    public static void addPacked(@NonNull WorkshopState s, @NonNull String flora, @NonNull Format format,
            double kg, int jars) {
        s.addPacked(flora, format, kg, jars);
    }

    /** @return true si cambió algo. */
    public static boolean advance(@NonNull WorkshopState s, long nowMs) {
        boolean changed = false;
        long clock = 0L;
        while (true) {
            Batch finish = null;
            for (Batch b : s.batches) {
                if (b.inMachine && (finish == null || b.endAt < finish.endAt)) {
                    finish = b;
                }
            }
            Batch start = null;
            long startAt = Long.MAX_VALUE;
            for (Machine m : Machine.values()) {
                if (busy(s, m) >= WorkshopRules.slots(m, s.level(m))) {
                    continue;
                }
                Batch c = firstWaiting(s, m);
                long at = c == null ? Long.MAX_VALUE : Math.max(c.waitingSince, clock);
                if (c != null && at < startAt) {
                    start = c;
                    startAt = at;
                }
            }
            boolean canFinish = finish != null && finish.endAt <= nowMs;
            boolean canStart = start != null && startAt <= nowMs;
            if (canFinish && (!canStart || finish.endAt <= startAt)) {
                clock = Math.max(clock, finish.endAt);
                complete(s, finish);
            } else if (canStart) {
                clock = startAt;
                Machine m = start.stage;
                start.inMachine = true;
                start.startAt = startAt;
                start.endAt = startAt + WorkshopRules.durationMs(m, s.level(m), start.kg, start.format, start.mix);
            } else {
                return changed;
            }
            changed = true;
        }
    }

    private static int busy(@NonNull WorkshopState s, @NonNull Machine m) {
        int n = 0;
        for (Batch b : s.batches) {
            if (b.inMachine && b.stage == m) {
                n++;
            }
        }
        return n;
    }

    @Nullable
    private static Batch firstWaiting(@NonNull WorkshopState s, @NonNull Machine m) {
        Batch best = null;
        for (Batch b : s.batches) {
            if (b.inMachine || b.stage != m || b.waitingFormat()) {
                continue;
            }
            if (best == null || b.waitingSince < best.waitingSince
                    || (b.waitingSince == best.waitingSince && b.createdAt < best.createdAt)) {
                best = b;
            }
        }
        return best;
    }

    private static void complete(@NonNull WorkshopState s, @NonNull Batch b) {
        Machine done = b.stage;
        b.inMachine = false;
        b.waitingSince = b.endAt;
        if (done == Machine.UNCAPPER) {
            b.waxKg = WorkshopRules.waxKg(b.kg);
            s.waxKg += b.waxKg;
        }
        Machine next = WorkshopRules.next(done);
        if (next != null) {
            b.stage = next;
            return;
        }
        if (b.mix != null) {
            // Cada tamaño de tarro a la estantería; lo que no se envasó, a granel.
            double jarKg = 0;
            for (Format jar : Format.values()) {
                int n = jar == Format.BULK ? 0 : b.mix.count(jar);
                if (n > 0) {
                    s.addPacked(b.flora, jar, n * jar.jarKg, n);
                    jarKg += n * jar.jarKg;
                }
            }
            double bulk = Math.max(0.0, b.kg - jarKg);
            if (bulk > 1e-6) {
                s.addPacked(b.flora, Format.BULK, bulk, 0);
            }
            s.batches.remove(b);
            return;
        }
        Format f = b.format != null ? b.format : Format.BULK;
        int jars = WorkshopRules.jars(b.kg, f);
        if (f == Format.BULK) {
            s.addPacked(b.flora, Format.BULK, b.kg, 0);
        } else {
            s.addPacked(b.flora, f, jars * f.jarKg, jars);
            s.addPacked(b.flora, Format.BULK, WorkshopRules.leftoverBulkKg(b.kg, f), 0);
        }
        for (Iterator<Batch> it = s.batches.iterator(); it.hasNext(); ) {
            if (it.next() == b) {
                it.remove();
                break;
            }
        }
    }
}
