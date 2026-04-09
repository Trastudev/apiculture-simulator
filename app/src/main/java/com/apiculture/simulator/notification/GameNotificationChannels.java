package com.apiculture.simulator.notification;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

import androidx.annotation.NonNull;

import com.apiculture.simulator.R;

/**
 * Canal de notificaciones para el recordatorio diario (producción a las 8:00).
 */
public final class GameNotificationChannels {

    public static final String ID_DAILY_PRODUCTION = "daily_production";

    private GameNotificationChannels() {
    }

    public static void ensureCreated(@NonNull Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) {
            return;
        }
        NotificationChannel ch = new NotificationChannel(
                ID_DAILY_PRODUCTION,
                context.getString(R.string.notification_channel_daily_title),
                NotificationManager.IMPORTANCE_DEFAULT);
        ch.setDescription(context.getString(R.string.notification_channel_daily_desc));
        nm.createNotificationChannel(ch);
    }
}
