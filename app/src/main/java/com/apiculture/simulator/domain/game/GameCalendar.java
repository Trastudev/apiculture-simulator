package com.apiculture.simulator.domain.game;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Calendario del juego alineado con la <strong>zona horaria del dispositivo del jugador</strong>
 * (ajuste del sistema: España, Nueva York, etc.). No se usa UTC fijo: {@link #userTimeZone()}
 * equivale a la zona que el usuario tiene en Ajustes → Fecha y hora.
 * <p>
 * Las claves {@code yyyymmdd} y el tick de producción (p. ej. las 8:00) se interpretan siempre en esa zona.
 */
public final class GameCalendar {

    public static final int PRODUCTION_HOUR = 8;
    public static final int PRODUCTION_MINUTE = 0;

    private GameCalendar() {
    }

    public static int toDayKey(LocalDate date) {
        return date.getYear() * 10_000 + date.getMonthValue() * 100 + date.getDayOfMonth();
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
}
