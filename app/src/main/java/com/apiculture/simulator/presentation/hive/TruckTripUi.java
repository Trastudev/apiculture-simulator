package com.apiculture.simulator.presentation.hive;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.apiculture.simulator.R;
import com.apiculture.simulator.data.local.entity.TruckTripEntity;
import com.apiculture.simulator.data.repository.TruckLiveTrips;
import com.apiculture.simulator.domain.game.TranshumanceRules;
import com.apiculture.simulator.domain.game.TruckTripRules;
import com.apiculture.simulator.presentation.common.GameNotice;

public final class TruckTripUi {

    private TruckTripUi() {
    }

    @NonNull
    public static String remainingLabel(@NonNull Context ctx, @Nullable TruckTripEntity trip) {
        long ms = TruckTripRules.remainingMs(trip, System.currentTimeMillis());
        if (ms <= 0L) {
            return ctx.getString(R.string.hive_trip_remaining_soon);
        }
        long totalMin = Math.max(1L, (ms + 59_999L) / 60_000L);
        if (totalMin < 60L) {
            return ctx.getString(R.string.hive_trip_remaining_m, totalMin);
        }
        long hours = totalMin / 60L;
        long mins = totalMin % 60L;
        if (mins == 0L) {
            return ctx.getString(R.string.hive_trip_remaining_h, hours);
        }
        return ctx.getString(R.string.hive_trip_remaining_hm, hours, mins);
    }

    public static void confirmCancelLiveTrip(@Nullable Context ctx, @Nullable String hiveId,
            @Nullable Runnable onChanged) {
        if (ctx == null || hiveId == null || hiveId.isEmpty()) {
            return;
        }
        GameNotice.confirm(ctx,
                ctx.getString(R.string.map_trip_uturn_title),
                ctx.getString(R.string.map_trip_uturn_message),
                R.string.map_trip_uturn_ok,
                () -> {
                    boolean turned = TruckLiveTrips.cancel(ctx.getApplicationContext(), hiveId);
                    if (turned) {
                        GameNotice.showSuccess(ctx, R.string.map_trip_uturn_done);
                    } else {
                        GameNotice.show(ctx, R.string.map_trip_already_returning);
                    }
                    if (onChanged != null) {
                        onChanged.run();
                    }
                });
    }

    public static void showTravelOutcome(@Nullable Context ctx, @Nullable String msg) {
        if (ctx == null || msg == null || msg.isEmpty()) {
            return;
        }
        if (TranshumanceRules.ERR_AFTER_DAILY_TICK.equals(msg)) {
            GameNotice.show(ctx,
                    R.string.map_transhumance_day_change_title,
                    R.string.map_transhumance_day_change_message);
            return;
        }
        GameNotice.show(ctx, msg);
    }
}
