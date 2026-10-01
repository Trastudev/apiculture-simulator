package com.apiculture.simulator.domain.game;

import com.apiculture.simulator.domain.parcel.HexFlora;
import com.apiculture.simulator.domain.parcel.HexParcel;

import androidx.annotation.Nullable;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Ventana de floración (día del año) a partir de los picos de néctar, con desfase climático.
 */
public final class FloraBloomWindow {

    public static final class Span {
        public final int startDoy;
        public final int endDoy;

        public Span(int startDoy, int endDoy) {
            this.startDoy = startDoy;
            this.endDoy = endDoy;
        }
    }

    private FloraBloomWindow() {
    }

    public static List<Span> spansForParcel(String floraKey, HexParcel parcel) {
        return spans(floraKey, HexNectarRules.bloomShiftDaysForParcel(parcel, floraKey));
    }

    public static List<Span> spans(String floraKey, int bloomShiftDays) {
        return spansAtLeast(floraKey, bloomShiftDays, 0.0);
    }

    /**
     * Tramos en los que la campana de néctar llega al umbral (subida, pico y bajada).
     * {@code minBloom01 <= 0} usa la ventana ancha de picos.
     */
    public static List<Span> spansAtLeast(String floraKey, int bloomShiftDays, double minBloom01) {
        String k = HexFlora.canonicalKey(floraKey);
        List<GameBalanceConfig.NectarPeak> peaks = GameBalanceConfig.peaksForFlora(k);
        List<int[]> raw = new ArrayList<>();
        if (peaks != null) {
            for (GameBalanceConfig.NectarPeak p : peaks) {
                int half;
                if (minBloom01 <= 1e-9) {
                    half = Math.max(8, (int) Math.round(p.width * 1.65));
                } else {
                    double h = Math.max(0.0, p.height);
                    if (h + 1e-12 < minBloom01) {
                        continue;
                    }
                    double x = Math.sqrt(-2.0 * Math.log(minBloom01 / h));
                    half = Math.max(1, (int) Math.round(x * Math.max(1, p.width)));
                }
                int center = wrapDoy(p.center + bloomShiftDays);
                int start = center - half;
                int end = center + half;
                if (start < 1 && end > 365) {
                    raw.add(new int[]{1, 365});
                } else if (start < 1) {
                    raw.add(new int[]{wrapDoy(start), 365});
                    raw.add(new int[]{1, end});
                } else if (end > 365) {
                    raw.add(new int[]{start, 365});
                    raw.add(new int[]{1, wrapDoy(end)});
                } else {
                    raw.add(new int[]{start, end});
                }
            }
        }
        return merge(raw);
    }

    public static int addDays(int doy, int days) {
        return wrapDoy(doy + days);
    }

    /** Días inclusive desde {@code doy} hasta el fin del tramo, si hoy está dentro. */
    public static int remainingInclusive(@Nullable Span span, int doy) {
        if (span == null) {
            return 0;
        }
        if (!containsDoy(span, doy)) {
            return daysInSpan(span);
        }
        if (span.startDoy <= span.endDoy) {
            return Math.max(1, span.endDoy - doy + 1);
        }
        if (doy >= span.startDoy) {
            return (365 - doy + 1) + span.endDoy;
        }
        return span.endDoy - doy + 1;
    }

    public static int daysUntilStart(int startDoy, int fromDoy) {
        int from = wrapDoy(fromDoy);
        int start = wrapDoy(startDoy);
        if (start >= from) {
            return start - from;
        }
        return 365 - from + start;
    }

    public static String formatEs(List<Span> spans) {
        if (spans == null || spans.isEmpty()) {
            return "—";
        }
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("d MMM", new Locale("es", "ES"));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < spans.size(); i++) {
            if (i > 0) {
                sb.append(" y ");
            }
            Span s = spans.get(i);
            sb.append(fmt.format(dateOf(s.startDoy)));
            sb.append(" – ");
            sb.append(fmt.format(dateOf(s.endDoy)));
        }
        return sb.toString();
    }

    public static String formatUpcomingEs(@Nullable List<Span> spans, @Nullable LocalDate from) {
        if (spans == null || spans.isEmpty()) {
            return "—";
        }
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("d MMM", new Locale("es", "ES"));
        LocalDate day = from != null ? from : LocalDate.now();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < spans.size(); i++) {
            if (i > 0) {
                sb.append(" y ");
            }
            Span s = spans.get(i);
            LocalDate start = nextDateOfDoy(day, s.startDoy);
            LocalDate end = nextDateOfDoy(start, s.endDoy);
            sb.append(fmt.format(start));
            sb.append(" – ");
            sb.append(fmt.format(end));
        }
        return sb.toString();
    }

    public static String formatEsForParcel(String floraKey, HexParcel parcel) {
        return formatEs(spansForParcel(floraKey, parcel));
    }

    /**
     * 0 si hoy está en mielada; si no, días hasta el próximo inicio (da la vuelta al año).
     */
    public static int daysUntilBloomStart(String floraKey, HexParcel parcel, LocalDate from) {
        if (from == null) {
            from = LocalDate.now();
        }
        List<Span> spans = spansForParcel(floraKey, parcel);
        if (spans.isEmpty()) {
            return 40;
        }
        int doy = Math.min(365, from.getDayOfYear());
        for (Span s : spans) {
            if (containsDoy(s, doy)) {
                return 0;
            }
        }
        int best = Integer.MAX_VALUE;
        for (Span s : spans) {
            int delta = s.startDoy >= doy ? s.startDoy - doy : 365 - doy + s.startDoy;
            if (delta < best) {
                best = delta;
            }
        }
        return best;
    }

    /**
     * Fin de la ventana de floración que cubre {@code from} o, si ya pasó, la siguiente.
     */
    public static LocalDate bloomEndDateOnOrAfter(String floraKey, HexParcel parcel, LocalDate from) {
        if (from == null) {
            from = LocalDate.now();
        }
        List<Span> spans = spansForParcel(floraKey, parcel);
        if (spans.isEmpty()) {
            return from.plusDays(40);
        }
        int doy = Math.min(365, from.getDayOfYear());
        for (Span s : spans) {
            if (containsDoy(s, doy)) {
                return endDateOfSpan(from, s);
            }
        }
        Span next = null;
        int best = Integer.MAX_VALUE;
        for (Span s : spans) {
            if (s.startDoy >= doy && s.startDoy < best) {
                best = s.startDoy;
                next = s;
            }
        }
        if (next != null) {
            return endDateOfSpan(dateOfYearDoy(from.getYear(), next.startDoy), next);
        }
        Span first = spans.get(0);
        for (Span s : spans) {
            if (s.startDoy < first.startDoy) {
                first = s;
            }
        }
        return endDateOfSpan(dateOfYearDoy(from.getYear() + 1, first.startDoy), first);
    }

    public static boolean containsDoy(Span span, int doy) {
        if (span == null) {
            return false;
        }
        if (span.startDoy <= span.endDoy) {
            return doy >= span.startDoy && doy <= span.endDoy;
        }
        return doy >= span.startDoy || doy <= span.endDoy;
    }

    /** Días inclusive de la ventana (incluye tramos que cruzan año). */
    public static int daysInSpan(@Nullable Span span) {
        if (span == null) {
            return 0;
        }
        if (span.startDoy <= span.endDoy) {
            return Math.max(1, span.endDoy - span.startDoy + 1);
        }
        return Math.max(1, (365 - span.startDoy + 1) + span.endDoy);
    }

    private static LocalDate endDateOfSpan(LocalDate context, Span span) {
        int year = context.getYear();
        int doy = Math.min(365, context.getDayOfYear());
        if (span.startDoy <= span.endDoy) {
            LocalDate end = dateOfYearDoy(year, span.endDoy);
            if (end.isBefore(context)) {
                end = dateOfYearDoy(year + 1, span.endDoy);
            }
            return end;
        }
        if (doy >= span.startDoy) {
            return dateOfYearDoy(year + 1, span.endDoy);
        }
        return dateOfYearDoy(year, span.endDoy);
    }

    public static LocalDate nextDateOfDoy(@Nullable LocalDate from, int doy) {
        LocalDate day = from != null ? from : LocalDate.now();
        LocalDate cand = dateOfYearDoy(day.getYear(), doy);
        if (cand.isBefore(day)) {
            cand = dateOfYearDoy(day.getYear() + 1, doy);
        }
        return cand;
    }

    private static LocalDate dateOfYearDoy(int year, int doy) {
        int max = LocalDate.of(year, 1, 1).isLeapYear() ? 366 : 365;
        int d = Math.max(1, Math.min(max, doy));
        return LocalDate.ofYearDay(year, d);
    }

    private static List<Span> merge(List<int[]> raw) {
        if (raw.isEmpty()) {
            return Collections.emptyList();
        }
        raw.sort(Comparator.comparingInt(a -> a[0]));
        List<Span> out = new ArrayList<>();
        int start = raw.get(0)[0];
        int end = raw.get(0)[1];
        for (int i = 1; i < raw.size(); i++) {
            int s = raw.get(i)[0];
            int e = raw.get(i)[1];
            if (s <= end + 1) {
                end = Math.max(end, e);
            } else {
                out.add(new Span(start, end));
                start = s;
                end = e;
            }
        }
        out.add(new Span(start, end));
        return out;
    }

    private static int wrapDoy(int d) {
        int x = d;
        while (x < 1) {
            x += 365;
        }
        while (x > 365) {
            x -= 365;
        }
        return x;
    }

    private static LocalDate dateOf(int doy) {
        int d = Math.max(1, Math.min(365, doy));
        return LocalDate.ofYearDay(2026, d);
    }
}
