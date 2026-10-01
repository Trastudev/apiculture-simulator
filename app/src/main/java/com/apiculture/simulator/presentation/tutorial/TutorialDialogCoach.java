package com.apiculture.simulator.presentation.tutorial;

import android.app.Dialog;
import android.graphics.RectF;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.apiculture.simulator.R;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Viñeta de Ramón dibujada dentro de un diálogo (capítulo 1, viñetas 7b y 8).
 * El hueco deja pulsar el botón o el formulario que se está explicando.
 */
public final class TutorialDialogCoach {

    private static final String TAG = "tutorial_dialog_coach";
    private static final Set<Dialog> HANDOFF =
            Collections.newSetFromMap(new WeakHashMap<>());

    @Nullable
    private static Dialog showing;

    private TutorialDialogCoach() {
    }

    /** El cierre de este diálogo no es un cancelar: el jugador sigue el paso. */
    public static void handoff(@Nullable Dialog dialog) {
        if (dialog != null) {
            HANDOFF.add(dialog);
        }
    }

    public static void show(@Nullable Dialog dialog, @Nullable View highlight, @Nullable CharSequence text,
            boolean cardOnTop, @Nullable Runnable onCancel) {
        if (dialog == null || dialog.getWindow() == null) {
            return;
        }
        Window window = dialog.getWindow();
        window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        View content = dialog.findViewById(android.R.id.content);
        if (content != null && content.getLayoutParams() instanceof FrameLayout.LayoutParams) {
            FrameLayout.LayoutParams contentLp = (FrameLayout.LayoutParams) content.getLayoutParams();
            int room = Math.round(150f * dialog.getContext().getResources().getDisplayMetrics().density);
            contentLp.gravity = Gravity.TOP;
            contentLp.topMargin = cardOnTop ? room : 0;
            contentLp.bottomMargin = cardOnTop ? 0 : room;
            content.setLayoutParams(contentLp);
        }
        View decor = window.getDecorView();
        if (!(decor instanceof ViewGroup)) {
            return;
        }
        ViewGroup host = (ViewGroup) decor;
        View previous = host.findViewWithTag(TAG);
        if (previous != null) {
            host.removeView(previous);
        }
        TutorialOverlay coach = (TutorialOverlay) LayoutInflater.from(dialog.getContext())
                .inflate(R.layout.view_tutorial_overlay, host, false);
        coach.setTag(TAG);
        host.addView(coach, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        coach.setVisibility(View.VISIBLE);
        android.widget.ImageView face = coach.findViewById(R.id.tutorial_portrait);
        if (face != null) {
            face.setImageResource(TutorialFaces.drawable());
        }
        TextView body = coach.findViewById(R.id.tutorial_body);
        if (body != null && text != null) {
            body.setText(text);
        }
        View next = coach.findViewById(R.id.tutorial_next);
        if (next != null) {
            next.setVisibility(View.GONE);
        }
        View skip = coach.findViewById(R.id.tutorial_skip);
        if (skip instanceof TextView) {
            ((TextView) skip).setText(R.string.tutorial_skip_step);
        }
        if (skip != null) {
            skip.setOnClickListener(v -> {
                handoff(dialog);
                TutorialBus.skipFromDialog();
                dialog.dismiss();
            });
        }
        coach.setCardOnTop(cardOnTop);
        View pass = content != null ? content : host;
        coach.post(() -> place(coach, highlight, cardOnTop, pass));
        showing = dialog;
        dialog.setOnDismissListener(d -> {
            if (showing == dialog) {
                showing = null;
            }
            if (HANDOFF.remove(dialog)) {
                return;
            }
            if (onCancel != null) {
                onCancel.run();
            }
        });
    }

    private static void place(TutorialOverlay coach, @Nullable View highlight, boolean cardOnTop, View pass) {
        if (highlight != null && highlight.getWidth() > 0 && highlight.getHeight() > 0) {
            int[] host = new int[2];
            int[] at = new int[2];
            coach.getLocationOnScreen(host);
            highlight.getLocationOnScreen(at);
            float pad = 8f * coach.getResources().getDisplayMetrics().density;
            RectF hole = new RectF(
                    at[0] - host[0] - pad,
                    at[1] - host[1] - pad,
                    at[0] - host[0] + highlight.getWidth() + pad,
                    at[1] - host[1] + highlight.getHeight() + pad);
            coach.setHole(hole, pass);
            return;
        }
        View card = coach.findViewById(R.id.tutorial_card);
        float top = cardOnTop && card != null ? card.getBottom() : 0f;
        float bottom = !cardOnTop && card != null ? card.getTop() : coach.getHeight();
        if (bottom <= top) {
            coach.setHole(null, pass);
            return;
        }
        coach.setHole(new RectF(0f, top, coach.getWidth(), bottom), pass);
    }
}
