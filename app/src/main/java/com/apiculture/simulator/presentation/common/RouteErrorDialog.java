package com.apiculture.simulator.presentation.common;

import android.app.Activity;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Window;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.apiculture.simulator.R;
import com.apiculture.simulator.domain.map.LocalGraphHopper;
import com.google.android.material.button.MaterialButton;

public final class RouteErrorDialog {

    private RouteErrorDialog() {
    }

    public static void show(@Nullable Context context, @Nullable LocalGraphHopper.Diag diag) {
        if (diag == null || diag.kind == LocalGraphHopper.Kind.OK) {
            return;
        }
        Activity found = resolveActivity(context);
        if (found == null) {
            found = GameNotice.currentActivity();
        }
        if (found == null || found.isFinishing() || found.isDestroyed()) {
            return;
        }
        Activity activity = found;
        String type = "TIPO: " + diag.kind.name();
        String body = diag.report();
        activity.runOnUiThread(() -> present(activity, type, body));
    }

    private static void present(@NonNull Activity activity, @NonNull String type, @NonNull String body) {
        if (activity.isFinishing() || activity.isDestroyed()) {
            return;
        }
        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_route_error);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        TextView tvType = dialog.findViewById(R.id.tv_route_error_type);
        TextView tvDetail = dialog.findViewById(R.id.tv_route_error_detail);
        MaterialButton btnCopy = dialog.findViewById(R.id.btn_route_error_copy);
        MaterialButton btnOk = dialog.findViewById(R.id.btn_route_error_ok);
        if (tvType == null || tvDetail == null || btnCopy == null || btnOk == null) {
            return;
        }
        String shown = body.length() > 4000 ? body.substring(0, 4000) : body;
        tvType.setText(type);
        tvDetail.setText(shown);
        btnCopy.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText("ruta", type + "\n\n" + body));
            }
            dialog.dismiss();
            GameNotice.showSuccess(activity, R.string.truck_route_error_copied);
        });
        btnOk.setOnClickListener(v -> dialog.dismiss());
        try {
            dialog.show();
        } catch (RuntimeException ignored) {
            dialog.dismiss();
        }
    }

    @Nullable
    private static Activity resolveActivity(@Nullable Context context) {
        Context walk = context;
        while (walk instanceof ContextWrapper) {
            if (walk instanceof Activity) {
                Activity a = (Activity) walk;
                if (!a.isFinishing() && !a.isDestroyed()) {
                    return a;
                }
            }
            walk = ((ContextWrapper) walk).getBaseContext();
        }
        return null;
    }
}
