package com.apiculture.simulator.notification;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Vuelve a programar la alarma de las 8:00 tras reiniciar el dispositivo.
 */
public class BootCompletedReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            return;
        }
        if (DailyProductionAlarmScheduler.hasActiveSession(context)) {
            DailyProductionAlarmScheduler.scheduleNext(context);
        }
    }
}
