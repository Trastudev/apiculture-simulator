package com.apiculture.simulator.domain.game;

/**
 * El primer tick de una colmena es el próximo cierre de las
 * {@link GameCalendar#PRODUCTION_HOUR}:00 locales. Si se compra antes de esa hora,
 * entra en el cierre de ese mismo día; si se compra después, en el del día siguiente.
 */
public final class HiveProductionEligibility {

    private HiveProductionEligibility() {
    }

    /**
     * Primer {@code dayKey} en el que la colmena puede forrajear.
     * {@code 0} (legado) = sin restricción.
     */
    public static boolean participatesOnDay(int firstProductionDayKey, int dayKey) {
        if (firstProductionDayKey <= 0) {
            return true;
        }
        return dayKey >= firstProductionDayKey;
    }

    public static int firstDayAfter(int purchaseDayKey) {
        return GameCalendar.toDayKey(GameCalendar.fromDayKey(purchaseDayKey).plusDays(1));
    }

    /**
     * Día del próximo cierre de las 8:00. Antes de esa hora es el día civil actual.
     */
    public static int firstDayAtNextProduction(java.time.ZonedDateTime now) {
        java.time.LocalDate day = now.toLocalDate();
        boolean beforeCut = now.getHour() < GameCalendar.PRODUCTION_HOUR
                || (now.getHour() == GameCalendar.PRODUCTION_HOUR
                && now.getMinute() < GameCalendar.PRODUCTION_MINUTE);
        if (!beforeCut) {
            day = day.plusDays(1);
        }
        return GameCalendar.toDayKey(day);
    }
}
