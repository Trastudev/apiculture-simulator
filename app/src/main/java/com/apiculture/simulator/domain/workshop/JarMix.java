package com.apiculture.simulator.domain.workshop;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.apiculture.simulator.domain.workshop.WorkshopRules.Format;

import java.util.Locale;

/**
 * Tarros que pide una comanda: tantos de kilo, de 500 g y de 250 g. Se guarda en el campo de envase
 * de la comanda como {@code JARS:2/1/0}. Debe coincidir con {@code orderMix} de server/src/offerClock.js.
 */
public final class JarMix {

    public static final String PREFIX = "JARS:";
    /** Precio por kilo del tarro de 500 g y de 250 g respecto al de kilo. */
    public static final double PREMIUM_500 = 1.2;
    public static final double PREMIUM_250 = 1.45;

    public final int kilo;
    public final int half;
    public final int quarter;

    public JarMix(int kilo, int half, int quarter) {
        this.kilo = Math.max(0, kilo);
        this.half = Math.max(0, half);
        this.quarter = Math.max(0, quarter);
    }

    /**
     * Los kilos pedidos se redondean hacia arriba al cuarto de kilo y se reparten empezando por el
     * tarro grande: 2,45 kg → 2,5 kg → 2 de kilo y 1 de 500 g.
     */
    @NonNull
    public static JarMix fromKg(double kg) {
        long quarters = Math.max(1L, (long) Math.ceil(kg * 4.0 - 1e-6));
        int kilo = (int) (quarters / 4);
        long rest = quarters % 4;
        int half = (int) (rest / 2);
        int quarter = (int) (rest % 2);
        return new JarMix(kilo, half, quarter);
    }

    /** La mezcla guardada, o null si el campo no la lleva (comanda antigua). */
    @Nullable
    public static JarMix parse(@Nullable String format) {
        if (format == null || !format.startsWith(PREFIX)) {
            return null;
        }
        String[] p = format.substring(PREFIX.length()).split("/");
        if (p.length != 3) {
            return null;
        }
        try {
            JarMix mix = new JarMix(Integer.parseInt(p[0].trim()), Integer.parseInt(p[1].trim()),
                    Integer.parseInt(p[2].trim()));
            return mix.total() > 0 ? mix : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Una comanda antigua de un solo envase, pasada a mezcla. Null si iba a granel. */
    @Nullable
    public static JarMix ofSingle(@Nullable Format format, double kg) {
        if (format == null || format == Format.BULK) {
            return null;
        }
        int n = Math.max(1, WorkshopRules.orderJars(kg, format));
        switch (format) {
            case JAR_1000: return new JarMix(n, 0, 0);
            case JAR_500: return new JarMix(0, n, 0);
            default: return new JarMix(0, 0, n);
        }
    }

    @NonNull
    public String encode() {
        return String.format(Locale.US, "%s%d/%d/%d", PREFIX, kilo, half, quarter);
    }

    public int count(@NonNull Format f) {
        switch (f) {
            case JAR_1000: return kilo;
            case JAR_500: return half;
            case JAR_250: return quarter;
            default: return 0;
        }
    }

    public int total() {
        return kilo + half + quarter;
    }

    public double kg() {
        return kilo + half * 0.5 + quarter * 0.25;
    }

    /**
     * Precio medio por kilo de la mezcla, a partir del precio por kilo del tarro de kilo.
     * Así el cobro sigue siendo kilos × precio.
     */
    public double unitPrice(double kiloPricePerKg) {
        double kg = kg();
        if (kg <= 0) {
            return kiloPricePerKg;
        }
        double pay = kiloPricePerKg * (kilo + half * 0.5 * PREMIUM_500 + quarter * 0.25 * PREMIUM_250);
        return Math.round(pay / kg * 100.0) / 100.0;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof JarMix)) {
            return false;
        }
        JarMix m = (JarMix) o;
        return m.kilo == kilo && m.half == half && m.quarter == quarter;
    }

    @Override
    public int hashCode() {
        return kilo * 961 + half * 31 + quarter;
    }

    @NonNull
    @Override
    public String toString() {
        return encode();
    }
}
