package com.apiculture.simulator.notification;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.apiculture.simulator.domain.game.GameCalendar;
import com.apiculture.simulator.presentation.MainActivity;

import java.time.ZonedDateTime;

/**
 * Programa la siguiente alarma a las {@link GameCalendar#PRODUCTION_HOUR}:00 en la zona horaria del sistema.
 */
public final class DailyProductionAlarmScheduler {

    private static final String PREF_FILE = "apiculture_alarms";
    private static final String KEY_ACTIVE_SESSION = "active_session";

    private static final int RC_ALARM = 91001;
    private static final int RC_SHOW_ACTIVITY = 91002;

    public static final String ACTION_DAILY_8AM = "com.apiculture.simulator.action.DAILY_8AM";

    private DailyProductionAlarmScheduler() {
    }

    public static void markSessionActive(Context context, boolean active) {
        context.getApplicationContext()
                .getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_ACTIVE_SESSION, active)
                .commit();
        if (!active) {
            cancel(context);
        } else {
            scheduleNext(context);
        }
    }

    public static boolean hasActiveSession(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
                .getBoolean(KEY_ACTIVE_SESSION, false);
    }

    public static void scheduleNext(Context context) {
        Context app = context.getApplicationContext();
        if (!hasActiveSession(app)) {
            return;
        }
        AlarmManager am = (AlarmManager) app.getSystemService(Context.ALARM_SERVICE);
        if (am == null) {
            return;
        }
        PendingIntent pi = alarmPendingIntent(app);
        am.cancel(pi);
        long when = nextEightAmEpochMillis();
        Intent show = new Intent(app, MainActivity.class);
        show.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent showPi = PendingIntent.getActivity(app, RC_SHOW_ACTIVITY, show,
                pendingFlags(PendingIntent.FLAG_UPDATE_CURRENT));
        AlarmManager.AlarmClockInfo info = new AlarmManager.AlarmClockInfo(when, showPi);
        try {
            am.setAlarmClock(info, pi);
        } catch (SecurityException e) {
            // Sin SCHEDULE_EXACT_ALARM (API 31+) o revocado: alarma inexacta para no cerrar la app.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                try {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pi);
                } catch (SecurityException ignored) {
                    am.set(AlarmManager.RTC_WAKEUP, when, pi);
                }
            } else {
                am.set(AlarmManager.RTC_WAKEUP, when, pi);
            }
        }
    }

    public static void cancel(Context context) {
        Context app = context.getApplicationContext();
        AlarmManager am = (AlarmManager) app.getSystemService(Context.ALARM_SERVICE);
        if (am == null) {
            return;
        }
        am.cancel(alarmPendingIntent(app));
    }

    private static PendingIntent alarmPendingIntent(Context app) {
        Intent intent = new Intent(app, ProductionDailyNotificationReceiver.class);
        intent.setAction(ACTION_DAILY_8AM);
        return PendingIntent.getBroadcast(app, RC_ALARM, intent,
                pendingFlags(PendingIntent.FLAG_UPDATE_CURRENT));
    }

    private static int pendingFlags(int base) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return base | PendingIntent.FLAG_IMMUTABLE;
        }
        return base;
    }

    static long nextEightAmEpochMillis() {
        ZonedDateTime now = ZonedDateTime.now(GameCalendar.userTimeZone());
        ZonedDateTime next = now.withHour(GameCalendar.PRODUCTION_HOUR)
                .withMinute(GameCalendar.PRODUCTION_MINUTE)
                .withSecond(0)
                .withNano(0);
        if (!next.isAfter(now)) {
            next = next.plusDays(1);
        }
        return next.toInstant().toEpochMilli();
    }
}
