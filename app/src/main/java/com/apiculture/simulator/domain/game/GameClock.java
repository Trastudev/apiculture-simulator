package com.apiculture.simulator.domain.game;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Fecha y hora según la <strong>zona horaria del dispositivo</strong> del jugador (no UTC forzado).
 */
public class GameClock {

    private final Locale localeEs = new Locale("es", "ES");

    public int currentDayOfYear() {
        return Calendar.getInstance(TimeZone.getDefault()).get(Calendar.DAY_OF_YEAR);
    }

    public Season currentSeason() {
        return Season.fromDayOfYear(currentDayOfYear());
    }

    /**
     * Fecha actual del sistema, formateada en español (por ejemplo "1 abr 2026").
     */
    public String currentDateLabel() {
        Date now = new Date();
        SimpleDateFormat sdf = new SimpleDateFormat("d MMM yyyy", localeEs);
        sdf.setTimeZone(TimeZone.getDefault());
        return sdf.format(now);
    }

    /**
     * Hora actual del sistema (por ejemplo "14:37").
     * Útil como reloj de referencia para las acciones del juego.
     */
    public String currentTimeLabel() {
        Date now = new Date();
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", localeEs);
        sdf.setTimeZone(TimeZone.getDefault());
        return sdf.format(now);
    }
}
