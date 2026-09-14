package com.apiculture.simulator.ads;

import android.app.Activity;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.apiculture.simulator.R;
import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;

/**
 * Anuncios recompensados para alimentar colmenas. Usa el id de unidad de {@code R.string.admob_rewarded_unit_id}.
 */
public final class RewardedAdHelper {

    private static final String TAG = "RewardedAdHelper";

    private RewardedAdHelper() {
    }

    public interface RewardCallback {
        void onUserEarnedReward();

        void onAdFailedOrClosed();
    }

    /** Muestra un anuncio recompensado; en éxito llama {@link RewardCallback#onUserEarnedReward()}. */
    public static void showOneRewarded(@Nullable Activity activity, @NonNull RewardCallback callback) {
        if (activity == null || activity.isFinishing()) {
            callback.onAdFailedOrClosed();
            return;
        }
        String unitId = activity.getString(R.string.admob_rewarded_unit_id);
        AdRequest req = new AdRequest.Builder().build();
        RewardedAd.load(activity, unitId, req, new RewardedAdLoadCallback() {
            @Override
            public void onAdLoaded(@NonNull RewardedAd ad) {
                ad.setFullScreenContentCallback(new FullScreenContentCallback() {
                    @Override
                    public void onAdDismissedFullScreenContent() {
                        ad.setFullScreenContentCallback(null);
                    }

                    @Override
                    public void onAdFailedToShowFullScreenContent(@NonNull AdError adError) {
                        Log.w(TAG, "onAdFailedToShow: " + adError);
                        callback.onAdFailedOrClosed();
                    }
                });
                ad.show(activity, rewardItem -> callback.onUserEarnedReward());
            }

            @Override
            public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                Log.w(TAG, "onAdFailedToLoad: " + loadAdError);
                callback.onAdFailedOrClosed();
            }
        });
    }

    /** Encadena {@code count} anuncios; solo al completar el último recompensa llama {@code onAllRewards}. */
    public static void showChainedRewarded(
            @Nullable Activity activity,
            int count,
            @NonNull Runnable onAllRewards,
            @NonNull Runnable onAnyFailure) {
        if (count <= 0) {
            onAllRewards.run();
            return;
        }
        showChainedInternal(activity, count, onAllRewards, onAnyFailure);
    }

    private static void showChainedInternal(
            @Nullable Activity activity,
            int remaining,
            @NonNull Runnable onAllRewards,
            @NonNull Runnable onAnyFailure) {
        if (remaining <= 0) {
            onAllRewards.run();
            return;
        }
        showOneRewarded(activity, new RewardCallback() {
            @Override
            public void onUserEarnedReward() {
                if (remaining <= 1) {
                    onAllRewards.run();
                } else if (activity != null) {
                    activity.runOnUiThread(() -> showChainedInternal(
                            activity, remaining - 1, onAllRewards, onAnyFailure));
                } else {
                    onAnyFailure.run();
                }
            }

            @Override
            public void onAdFailedOrClosed() {
                onAnyFailure.run();
            }
        });
    }
}
