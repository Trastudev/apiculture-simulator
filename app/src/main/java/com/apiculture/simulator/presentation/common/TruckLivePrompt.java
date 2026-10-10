package com.apiculture.simulator.presentation.common;

import android.app.Activity;

import androidx.annotation.NonNull;

import com.apiculture.simulator.data.repository.RoutingGraphDownloader;
import com.apiculture.simulator.data.repository.TruckLivePrefs;

public final class TruckLivePrompt {

    private TruckLivePrompt() {
    }

    public static void maybeAsk(@NonNull Activity activity) {
        if (activity.isFinishing() || activity.isDestroyed()) {
            return;
        }
        RoutingGraphDownloader.refreshStatus(activity);
        if (RoutingGraphDownloader.isInstalled(activity)) {
            if (!TruckLivePrefs.isEnabled(activity)) {
                TruckLivePrefs.setChoice(activity, TruckLivePrefs.CHOICE_YES);
            }
            return;
        }
        TruckLivePrefs.setChoice(activity, TruckLivePrefs.CHOICE_YES);
        GraphInstallDialog.show(activity);
        RoutingGraphDownloader.enqueue(activity);
    }
}
