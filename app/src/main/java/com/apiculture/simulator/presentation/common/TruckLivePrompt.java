package com.apiculture.simulator.presentation.common;

import android.app.Activity;

import androidx.annotation.NonNull;

import com.apiculture.simulator.R;
import com.apiculture.simulator.data.repository.RoutingGraphDownloader;
import com.apiculture.simulator.data.repository.TruckLivePrefs;

public final class TruckLivePrompt {

    private TruckLivePrompt() {
    }

    public static void maybeAsk(@NonNull Activity activity) {
        if (activity.isFinishing() || activity.isDestroyed()) {
            return;
        }
        if (TruckLivePrefs.isEnabled(activity) && !RoutingGraphDownloader.isInstalled(activity)) {
            GraphInstallDialog.show(activity);
            RoutingGraphDownloader.refreshStatus(activity);
            return;
        }
        RoutingGraphDownloader.refreshStatus(activity);
        if (!TruckLivePrefs.needsAsk(activity)) {
            return;
        }
        GameNotice.confirm(
                activity,
                activity.getString(R.string.truck_live_ask_title),
                activity.getString(R.string.truck_live_ask_message),
                R.string.truck_live_ask_yes,
                R.string.truck_live_ask_no,
                () -> {
                    TruckLivePrefs.setChoice(activity, TruckLivePrefs.CHOICE_YES);
                    GraphInstallDialog.show(activity);
                    RoutingGraphDownloader.enqueue(activity);
                },
                () -> TruckLivePrefs.setChoice(activity, TruckLivePrefs.CHOICE_NO));
    }
}
