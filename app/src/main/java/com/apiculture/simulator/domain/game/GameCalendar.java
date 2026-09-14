package com.apiculture.simulator.domain.game;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * Dos relojes distintos a propósito, para un juego mundial:
 * <ul>
 *   <li>Colmenas, tick, alarmas: {@link #userTimeZone()} y las
 *       {@link #PRODUCTION_HOUR}:00 <strong>locales</strong> de cada jugador.</li>
 *   <li>Mercado de miel (oferta/demanda compartida): {@link #globalMarketTimeZone()} (UTC),
 *       para que España, Sudáfrica y América escriban en el mismo día de mercado.</li>
 * </ul>
 */
public final class GameCalendar {

    /** Hora local a la que se liquida el día de juego (producción, resúmenes, aviso). */
    public static final int PRODUCTION_HOUR = 8;
    public static final int PRODUCTION_MINUTE = 0;

    private GameCalendar() {
    }

    public static int toDayKey(LocalDate date) {
        return date.getYear() * 10_000 + date.getMonthValue() * 100 + date.getDayOfMonth();
    }

    /** Día civil de hoy en la zona del dispositivo ({@code yyyymmdd}). */
    public static int currentCivilDayKey() {
        return toDayKey(LocalDate.now(userTimeZone()));
    }

    /** Día de mercado mundial ({@code yyyymmdd} en UTC). */
    public static int currentGlobalMarketDayKey() {
        return toDayKey(LocalDate.now(globalMarketTimeZone()));
    }

    public static LocalDate fromDayKey(int dayKey) {
        int y = dayKey / 10_000;
        int m = (dayKey / 100) % 100;
        int d = dayKey % 100;
        return LocalDate.of(y, m, d);
    }

    /**
     * Zona horaria local del usuario (la del teléfono / tablet), la misma que usan reloj y calendario del sistema.
     */
    public static ZoneId userTimeZone() {
        return ZoneId.systemDefault();
    }

    /** Reloj único del mercado global. */
    public static ZoneId globalMarketTimeZone() {
        return ZoneOffset.UTC;
    }

    /**
     * Fecha de UI: si el tick (p. ej. «Simular un día») va por delante del calendario real, se muestra
     * esa fecha de simulación; si no, el día civil de hoy.
     */
    public static LocalDate uiDateForLastProcessed(int lastProcessedDayKey) {
        LocalDate today = LocalDate.now(userTimeZone());
        if (lastProcessedDayKey <= 0) {
            return today;
        }
        LocalDate last = fromDayKey(lastProcessedDayKey);
        return last.isAfter(today) ? last : today;
    }
}
