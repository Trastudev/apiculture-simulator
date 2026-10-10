package com.apiculture.simulator.domain.workshop;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Obrador: las alzas llegan del apiario y pasan por recepción, desoperculado, extracción,
 * maduración y envasado. Cada máquina tiene nivel (0 = no comprada); el nivel acorta el tiempo
 * y sube los kilos por tanda. Los tiempos son reales y corren con la app cerrada.
 */
public final class WorkshopRules {

    public enum Machine {
        /** Sala templada: reposo antes de abrir los cuadros. Sin límite de tandas. */
        RECEPTION,
        /** Corta los opérculos; aquí sale la cera. */
        UNCAPPER,
        EXTRACTOR,
        /** Un depósito por nivel: varias tandas reposan a la vez. */
        MATURER,
        PACKER
    }

    /**
     * Precio por kilo de cada envase respecto al bidón a granel del mercado. El bidón vale 0,6 veces
     * el tarro de kilo; el de 500 g, un 20 % más por kilo que el de kilo, y el de 250 g, un 45 % más.
     */
    public enum Format {
        BULK(0.0, 1.00, 15),
        JAR_1000(1.0, 1.0 / 0.6, 40),
        JAR_500(0.5, 1.2 / 0.6, 60),
        JAR_250(0.25, 1.45 / 0.6, 90);

        /** Kilos por tarro; 0 en el bidón, que se vende a peso. */
        public final double jarKg;
        /** Precio por kilo respecto a la miel a granel. */
        public final double priceFactor;
        /** Minutos de envasado de una tanda llena con la envasadora a nivel 1. */
        public final int packMinutes;

        Format(double jarKg, double priceFactor, int packMinutes) {
            this.jarKg = jarKg;
            this.priceFactor = priceFactor;
            this.packMinutes = packMinutes;
        }

        @Nullable
        public static Format parse(@Nullable String name) {
            if (name == null || name.isEmpty()) {
                return null;
            }
            for (Format f : values()) {
                if (f.name().equals(name)) {
                    return f;
                }
            }
            return null;
        }
    }

    public static final int MAX_LEVEL = 5;
    public static final int BUILDING_COST_B = 6000;
    /** Cera por kilo de miel al desopercular. */
    public static final double WAX_PER_HONEY_KG = 0.012;
    public static final double WAX_PRICE_B_PER_KG = 9.0;
    /**
     * La recogida exige obrador. Durante el capítulo 1 del tutorial no se pide (lo decide la
     * interfaz), porque allí se cosecha antes de tener almacén.
     */
    public static final boolean REQUIRED_FOR_HARVEST = true;

    private static final double SPEED_PER_LEVEL = 0.85;
    private static final long MINUTE_MS = 60_000L;

    private WorkshopRules() {
    }

    /** Orden del proceso. {@code null} tras el envasado. */
    @Nullable
    public static Machine next(@NonNull Machine m) {
        int i = m.ordinal() + 1;
        return i < Machine.values().length ? Machine.values()[i] : null;
    }

    /** La sala de recepción viene con el edificio; las demás se compran. */
    public static boolean comesWithBuilding(@NonNull Machine m) {
        return m == Machine.RECEPTION;
    }

    /** La sala de recepción solo marca un reposo fijo: subirla no cambiaría nada. */
    public static boolean upgradable(@NonNull Machine m) {
        return m != Machine.RECEPTION;
    }

    public static int buyCostB(@NonNull Machine m) {
        switch (m) {
            case UNCAPPER: return 1500;
            case EXTRACTOR: return 4000;
            case MATURER: return 2000;
            case PACKER: return 3000;
            default: return 0;
        }
    }

    /** Lo que cuesta pasar de {@code fromLevel} al siguiente. */
    public static int upgradeCostB(@NonNull Machine m, int fromLevel) {
        int base = comesWithBuilding(m) ? 1200 : buyCostB(m);
        return base * Math.max(1, fromLevel);
    }

    /** Edificio más la maquinaria básica: lo que cubre el dinero extra del tutorial. */
    public static int starterCostB() {
        int sum = BUILDING_COST_B;
        for (Machine m : Machine.values()) {
            sum += buyCostB(m);
        }
        return sum;
    }

    /** Tandas a la vez. 0 si la máquina no está. */
    public static int slots(@NonNull Machine m, int level) {
        if (level <= 0) {
            return 0;
        }
        if (m == Machine.RECEPTION) {
            return Integer.MAX_VALUE;
        }
        return m == Machine.MATURER ? level : 1;
    }

    /** Kilos que la máquina trabaja en su tiempo base; una tanda mayor tarda en proporción. */
    public static double capacityKg(@NonNull Machine m, int level) {
        int l = Math.max(1, level);
        switch (m) {
            case UNCAPPER: return 40 + 20 * (l - 1);
            case EXTRACTOR: return 40 + 25 * (l - 1);
            case PACKER: return 40 + 20 * (l - 1);
            default: return Double.MAX_VALUE;
        }
    }

    private static double baseMinutes(@NonNull Machine m, double kg, @Nullable Format format, @Nullable JarMix mix) {
        switch (m) {
            case RECEPTION: return 120;
            case UNCAPPER: return 30;
            case EXTRACTOR: return 45;
            case MATURER: return 240;
            case PACKER: return packMinutes(kg, format, mix);
            default: return 0;
        }
    }

    /**
     * Minutos de envasado de una tanda llena a nivel 1. Con reparto, cada envase cuenta según los kilos
     * que se lleva: los tarros pequeños tardan más que el bidón.
     */
    public static double packMinutes(double kg, @Nullable Format format, @Nullable JarMix mix) {
        if (mix == null || kg <= 1e-9) {
            return (format != null ? format : Format.BULK).packMinutes;
        }
        double minutes = 0;
        double jarKg = 0;
        for (Format f : Format.values()) {
            if (f == Format.BULK) {
                continue;
            }
            double part = mix.count(f) * f.jarKg;
            jarKg += part;
            minutes += part / kg * f.packMinutes;
        }
        minutes += Math.max(0.0, kg - jarKg) / kg * Format.BULK.packMinutes;
        return minutes;
    }

    /** Tiempo de una tanda de {@code kg} en la máquina. */
    public static long durationMs(@NonNull Machine m, int level, double kg, @Nullable Format format) {
        return durationMs(m, level, kg, format, null);
    }

    public static long durationMs(@NonNull Machine m, int level, double kg, @Nullable Format format,
            @Nullable JarMix mix) {
        int l = Math.max(1, level);
        double load = Math.max(1.0, kg / capacityKg(m, l));
        double speed = m == Machine.RECEPTION || m == Machine.MATURER ? 1.0 : Math.pow(SPEED_PER_LEVEL, l - 1);
        return Math.round(baseMinutes(m, kg, format, mix) * MINUTE_MS * load * speed);
    }

    public static double waxKg(double honeyKg) {
        return Math.max(0.0, honeyKg) * WAX_PER_HONEY_KG;
    }

    /** Tarros enteros; lo que no llena un tarro queda a granel. En bidón, 0. */
    public static int jars(double kg, @NonNull Format format) {
        if (format.jarKg <= 0) {
            return 0;
        }
        return (int) Math.floor(kg / format.jarKg + 1e-9);
    }

    /** Envase que pide una comanda: las pequeñas, tarro pequeño; las grandes, tarro de kilo o bidón. */
    @NonNull
    public static Format orderFormat(@Nullable String orderId, double kg) {
        int h = Math.floorMod(("fmt:" + (orderId == null ? "" : orderId)).hashCode(), 100);
        if (kg < 3) {
            return h < 50 ? Format.JAR_250 : Format.JAR_500;
        }
        if (kg < 12) {
            return h < 35 ? Format.JAR_500 : h < 70 ? Format.JAR_1000 : Format.BULK;
        }
        return h < 40 ? Format.JAR_1000 : Format.BULK;
    }

    /** Kilos de la comanda redondeados a tarros enteros. */
    public static double orderKg(double kg, @NonNull Format format) {
        if (format.jarKg <= 0) {
            return kg;
        }
        long jars = Math.max(1, Math.round(kg / format.jarKg));
        return Math.round(jars * format.jarKg * 100.0) / 100.0;
    }

    /** Tarros que pide una comanda de {@code kg}; 0 en bidón. */
    public static int orderJars(double kg, @NonNull Format format) {
        if (format.jarKg <= 0) {
            return 0;
        }
        return (int) Math.round(kg / format.jarKg);
    }

    public static double leftoverBulkKg(double kg, @NonNull Format format) {
        if (format.jarKg <= 0) {
            return Math.max(0.0, kg);
        }
        return Math.max(0.0, kg - jars(kg, format) * format.jarKg);
    }
}
