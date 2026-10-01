package com.apiculture.simulator.presentation.common;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Window;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.apiculture.simulator.R;
import com.apiculture.simulator.presentation.hive.HiveSiteSummaryUi;
import com.google.android.material.button.MaterialButton;

import java.util.List;

/** Anuncia cultivos desbloqueados al subir de nivel (bote + nombre, uno tras otro). */
public final class CropUnlockDialog {

    private CropUnlockDialog() {
    }

    public static void show(
            @NonNull Activity activity,
            @NonNull List<String> crops,
            @Nullable Runnable onComplete) {
        if (activity.isFinishing() || activity.isDestroyed() || crops == null || crops.isEmpty()) {
            if (onComplete != null) {
                onComplete.run();
            }
            return;
        }
        showStep(activity, crops, 0, onComplete);
    }

    private static void showStep(
            @NonNull Activity activity,
            @NonNull List<String> crops,
            int index,
            @Nullable Runnable onComplete) {
        if (activity.isFinishing() || activity.isDestroyed() || index >= crops.size()) {
            if (onComplete != null) {
                onComplete.run();
            }
            return;
        }
        String cropKey = crops.get(index);
        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(false);
        dialog.setContentView(R.layout.dialog_crop_unlock);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        ImageView jar = dialog.findViewById(R.id.iv_crop_unlock_jar);
        TextView name = dialog.findViewById(R.id.tv_crop_unlock_name);
        MaterialButton next = dialog.findViewById(R.id.btn_crop_unlock_next);

        int icon = HiveSiteSummaryUi.floraHoneyJarIcon(cropKey);
        if (icon != 0) {
            jar.setImageResource(icon);
        } else {
            jar.setImageResource(R.drawable.flora_photo_unknown);
        }
        name.setText(displayName(activity, cropKey));

        next.setOnClickListener(v -> {
            dialog.dismiss();
            showStep(activity, crops, index + 1, onComplete);
        });
        dialog.show();
    }

    private static String displayName(@NonNull Activity activity, String cropKey) {
        String key = com.apiculture.simulator.domain.parcel.HexFlora.canonicalKey(cropKey);
        switch (key) {
            case com.apiculture.simulator.domain.parcel.HexFlora.LUCERNA:
                return activity.getString(R.string.flora_display_lucerna);
            case com.apiculture.simulator.domain.parcel.HexFlora.LITCHI:
                return activity.getString(R.string.flora_display_litchi);
            case com.apiculture.simulator.domain.parcel.HexFlora.MACADAMIA:
                return activity.getString(R.string.flora_display_macadamia);
            case com.apiculture.simulator.domain.parcel.HexFlora.AGUACATE:
                return activity.getString(R.string.flora_display_aguacate);
            default:
                return key;
        }
    }
}
