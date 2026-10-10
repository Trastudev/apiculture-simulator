package com.apiculture.simulator.presentation.hive;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.text.InputType;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.apiculture.simulator.R;
import com.apiculture.simulator.presentation.common.GameNotice;
import com.google.android.material.button.MaterialButton;

/** Pide un nombre de apiario, con al menos dos letras. */
public final class ApiaryNameDialog {

    private ApiaryNameDialog() {
    }

    public static void show(@NonNull Fragment fragment, @Nullable String current,
            @NonNull java.util.function.Consumer<String> onName) {
        if (!fragment.isAdded()) {
            return;
        }
        Dialog dialog = new Dialog(fragment.requireContext());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout box = new LinearLayout(fragment.requireContext());
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (22f * fragment.getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad, pad, pad);
        box.setBackgroundResource(R.drawable.bg_event_global_body);
        TextView title = new TextView(fragment.requireContext());
        title.setText(R.string.apiary_rename_title);
        title.setTextColor(fragment.getResources().getColor(R.color.event_ink));
        title.setTextSize(18f);
        title.setGravity(android.view.Gravity.CENTER);
        box.addView(title);
        EditText edit = new EditText(fragment.requireContext());
        edit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        edit.setHint(R.string.hex_purchase_name_hint);
        edit.setText(current != null ? current : "");
        edit.setSelection(edit.getText().length());
        LinearLayout.LayoutParams editLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        editLp.topMargin = pad / 2;
        box.addView(edit, editLp);
        LinearLayout row = new LinearLayout(fragment.requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        MaterialButton cancel = new MaterialButton(fragment.requireContext(), null,
                com.google.android.material.R.attr.borderlessButtonStyle);
        cancel.setText(android.R.string.cancel);
        cancel.setOnClickListener(v -> dialog.dismiss());
        MaterialButton ok = new MaterialButton(fragment.requireContext());
        ok.setText(android.R.string.ok);
        ok.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                fragment.getResources().getColor(R.color.event_gold)));
        LinearLayout.LayoutParams btn = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        btn.topMargin = pad / 2;
        row.addView(cancel, btn);
        row.addView(ok, btn);
        box.addView(row);
        dialog.setContentView(box);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        ok.setOnClickListener(v -> {
            String name = com.apiculture.simulator.domain.game.EntityNames.clean(
                    edit.getText() == null ? "" : edit.getText().toString());
            if (name == null) {
                GameNotice.show(fragment.requireContext(), R.string.hex_purchase_name_required);
                return;
            }
            dialog.dismiss();
            onName.accept(name);
        });
        dialog.show();
    }
}
