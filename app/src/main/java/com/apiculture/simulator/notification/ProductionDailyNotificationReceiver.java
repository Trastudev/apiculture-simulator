package com.apiculture.simulator.notification;

import android.Manifest;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.apiculture.simulator.R;
import com.apiculture.simulator.presentation.MainActivity;

/**
 * Muestra el recordatorio de las 8:00 y reprograma la siguiente alarma.
 */
public class ProductionDailyNotificationReceiver extends BroadcastReceiver {

    private static final int NOTIF_ID_DAILY = 48001;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !DailyProductionAlarmScheduler.ACTION_DAILY_8AM.equals(intent.getAction())) {
            return;
        }
        Context app = context.getApplicationContext();
        if (!DailyProductionAlarmScheduler.hasActiveSession(app)) {
            return;
        }
        GameNotificationChannels.ensureCreated(app);
        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                DailyProductionAlarmScheduler.scheduleNext(app);
                return;
            }
        }
        Intent tap = new Intent(app, MainActivity.class);
        tap.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent content = PendingIntent.getActivity(app, NOTIF_ID_DAILY, tap,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder b = new NotificationCompat.Builder(app, GameNotificationChannels.ID_DAILY_PRODUCTION)
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle(app.getString(R.string.notification_daily_title))
                .setContentText(app.getString(R.string.notification_daily_text))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(content);
        NotificationManager nm = (NotificationManager) app.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.notify(NOTIF_ID_DAILY, b.build());
        }
        DailyProductionAlarmScheduler.scheduleNext(app);
    }
}
