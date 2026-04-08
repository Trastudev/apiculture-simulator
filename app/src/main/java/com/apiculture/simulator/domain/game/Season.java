package com.apiculture.simulator.domain.game;

public enum Season {
    SPRING,
    SUMMER,
    AUTUMN,
    WINTER;

    /** Etiqueta corta en español para la UI. */
    public String labelEs() {
        switch (this) {
            case SPRING:
                return "Primavera";
            case SUMMER:
                return "Verano";
            case AUTUMN:
                return "Otoño";
            case WINTER:
            default:
                return "Invierno";
        }
    }

    public String emoji() {
        switch (this) {
            case SPRING:
                return "\uD83C\uDF38";
            case SUMMER:
                return "\u2600\uFE0F";
            case AUTUMN:
                return "\uD83C\uDF42";
            case WINTER:
            default:
                return "\u2744\uFE0F";
        }
    }

    public static Season fromDayOfYear(int dayOfYear) {
        if (dayOfYear < 80) return WINTER;
        if (dayOfYear < 172) return SPRING;
        if (dayOfYear < 264) return SUMMER;
        if (dayOfYear < 355) return AUTUMN;
        return WINTER;
    }
}
