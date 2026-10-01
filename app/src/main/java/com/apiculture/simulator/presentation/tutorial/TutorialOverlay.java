package com.apiculture.simulator.presentation.tutorial;

import android.content.Context;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;

import com.apiculture.simulator.R;

/**
 * Capa del tutorial. El toque dentro del hueco pasa al juego.
 * El toque en la ficha de Ramón se queda en los botones.
 */
public class TutorialOverlay extends FrameLayout {

    private final RectF hole = new RectF();
    private boolean holeActive;
    @Nullable
    private View passthrough;

    public TutorialOverlay(Context context) {
        super(context);
    }

    public TutorialOverlay(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public TutorialOverlay(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setCardOnTop(boolean onTop) {
        View card = findViewById(R.id.tutorial_card);
        if (card == null || !(card.getLayoutParams() instanceof LayoutParams)) {
            return;
        }
        LayoutParams lp = (LayoutParams) card.getLayoutParams();
        int margin = Math.round(12f * getResources().getDisplayMetrics().density);
        int aboveNav = Math.round(8f * getResources().getDisplayMetrics().density);
        lp.gravity = onTop ? android.view.Gravity.TOP : android.view.Gravity.BOTTOM;
        lp.topMargin = onTop ? margin : 0;
        lp.bottomMargin = onTop ? 0 : aboveNav;
        card.setLayoutParams(lp);
    }

    public void setHole(@Nullable RectF localHole, @Nullable View passthrough) {
        this.passthrough = passthrough;
        holeActive = localHole != null && localHole.width() > 1f && localHole.height() > 1f;
        if (holeActive) {
            hole.set(localHole);
        }
        TutorialScrimView scrim = findViewById(R.id.tutorial_scrim);
        if (scrim != null) {
            scrim.setHole(holeActive ? hole : null);
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (getVisibility() != VISIBLE) {
            return false;
        }
        View card = findViewById(R.id.tutorial_card);
        if (card != null && hitTest(card, ev)) {
            return super.dispatchTouchEvent(ev);
        }
        if (holeActive && hole.contains(ev.getX(), ev.getY()) && passthrough != null) {
            int[] host = new int[2];
            int[] pass = new int[2];
            getLocationOnScreen(host);
            passthrough.getLocationOnScreen(pass);
            MotionEvent copy = MotionEvent.obtain(ev);
            copy.offsetLocation(host[0] - pass[0], host[1] - pass[1]);
            boolean handled = passthrough.dispatchTouchEvent(copy);
            copy.recycle();
            return handled;
        }
        return true;
    }

    private static boolean hitTest(View child, MotionEvent ev) {
        return ev.getX() >= child.getLeft() && ev.getX() < child.getRight()
                && ev.getY() >= child.getTop() && ev.getY() < child.getBottom();
    }
}
