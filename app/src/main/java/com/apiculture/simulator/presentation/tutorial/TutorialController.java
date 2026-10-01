package com.apiculture.simulator.presentation.tutorial;

import android.graphics.Rect;
import android.graphics.RectF;
import android.view.Gravity;
import android.view.View;
import android.view.ViewParent;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.widget.NestedScrollView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;

import com.apiculture.simulator.ApicultureApp;
import com.apiculture.simulator.R;
import com.apiculture.simulator.databinding.ActivityMainBinding;
import com.apiculture.simulator.presentation.MainActivity;
import com.apiculture.simulator.presentation.common.GameNotice;
import com.apiculture.simulator.presentation.hive.ApiaryYardFragment;
import com.apiculture.simulator.presentation.hive.ApiaryYardView;
import com.apiculture.simulator.presentation.tutorial.TutorialScript.Advance;
import com.apiculture.simulator.presentation.tutorial.TutorialScript.Anchor;
import com.apiculture.simulator.presentation.tutorial.TutorialScript.Screen;
import com.apiculture.simulator.presentation.tutorial.TutorialScript.Step;
import com.google.android.material.button.MaterialButton;
import com.apiculture.simulator.data.session.PlayerAuth;
import com.apiculture.simulator.data.session.SignedInUser;

import java.util.ArrayDeque;

/**
 * Muestra a Ramón y avanza las viñetas de {@link TutorialScript}.
 * El guion escrito está en {@code docs/tutorial.md}.
 */
public final class TutorialController implements TutorialBus.Listener {

    private static final class Pending {
        final TutorialChapter chapter;
        @Nullable final String detail;

        Pending(TutorialChapter chapter, @Nullable String detail) {
            this.chapter = chapter;
            this.detail = detail;
        }
    }

    private final MainActivity activity;
    private final ActivityMainBinding binding;
    private final NavController navController;
    private final TutorialProgress progress;
    private final TutorialOverlay overlay;
    private final ImageView portrait;
    private final TextView body;
    private final View weather;
    private final MaterialButton next;
    private final MaterialButton skip;
    private final ArrayDeque<Pending> queue = new ArrayDeque<>();

    @Nullable private String uid;
    private boolean booted;
    private int destId;
    @Nullable private TutorialChapter active;
    @Nullable private String activeDetail;
    private int index;
    @Nullable private View harvestWatch;
    @Nullable private View.OnLayoutChangeListener harvestWatchListener;
    @Nullable private View hiveGuideWatch;
    @Nullable private View.OnLayoutChangeListener hiveGuideListener;

    public TutorialController(@NonNull MainActivity activity, @NonNull ActivityMainBinding binding,
            @NonNull NavController navController) {
        this.activity = activity;
        this.binding = binding;
        this.navController = navController;
        this.progress = new TutorialProgress(activity);
        this.overlay = activity.findViewById(R.id.tutorial_overlay);
        this.portrait = overlay.findViewById(R.id.tutorial_portrait);
        this.body = overlay.findViewById(R.id.tutorial_body);
        this.weather = overlay.findViewById(R.id.tutorial_weather);
        this.next = overlay.findViewById(R.id.tutorial_next);
        this.skip = overlay.findViewById(R.id.tutorial_skip);
        next.setOnClickListener(v -> onNext());
        skip.setOnClickListener(v -> onSkip());
        TutorialBus.setListener(this);
    }

    public void detach() {
        clearHarvestWatch();
        TutorialBus.setListener(null);
    }

    public void onDestination(int destinationId) {
        destId = destinationId;
        if (isAuth(destinationId)) {
            overlay.setVisibility(View.GONE);
            return;
        }
        if (booted) {
            refresh();
        }
    }

    public void onSession(@NonNull String userId) {
        if (userId.equals(uid) && booted) {
            refresh();
            return;
        }
        uid = userId;
        booted = false;
        ApicultureApp app = (ApicultureApp) activity.getApplication();
        new Thread(() -> {
            boolean veteran = false;
            try {
                veteran = !app.getHexParcelRepository().listOwnedHexIdsSync(userId).isEmpty();
            } catch (RuntimeException ignored) {
                veteran = false;
            }
            boolean hasParcels = veteran;
            activity.runOnUiThread(() -> {
                if (!userId.equals(uid) || activity.isFinishing()) {
                    return;
                }
                progress.migrateVeteran(userId, hasParcels);
                booted = true;
                restore();
            });
        }, "tutorial-boot").start();
    }

    @Override
    public void onEvent(TutorialEvent event, @Nullable String detail) {
        if (!booted || uid == null || event == null) {
            return;
        }
        if (event == TutorialEvent.CONTRACTS_OFFER
                || (event == TutorialEvent.ORDERS_TAB && active == null)) {
            beginContractsChapter();
            return;
        }
        Step step = currentStep();
        if (step != null && completes(step, event)) {
            advance();
            return;
        }
        TutorialChapter chapter = chapterFor(event);
        if (chapter == null) {
            return;
        }
        if (chapter == TutorialChapter.CLIMATE) {
            if (detail == null || detail.isEmpty() || progress.climateShown(uid, detail)) {
                return;
            }
            offer(chapter, detail);
            return;
        }
        if (progress.isDone(uid, chapter)) {
            return;
        }
        offer(chapter, null);
    }

    @Override
    public void onDialog(TutorialEvent event, @Nullable android.app.Dialog dialog, @Nullable android.view.View highlight) {
        Step step = currentStep();
        if (event == TutorialEvent.FLORA_CHART) {
            // Capítulo 1, viñeta 10b. El gráfico de mieladas se ve encima del diálogo de compra.
            if (step != null && step.inDialog && step.event == TutorialEvent.HIVE_BOUGHT && dialog != null) {
                overlay.setVisibility(View.GONE);
                TutorialDialogCoach.show(dialog, highlight, activity.getString(R.string.tutorial_c1_v10b),
                        true, null);
            }
            return;
        }
        if (!booted || step == null || step.event != event || step.advance != Advance.EVENT) {
            return;
        }
        advance();
        Step next = currentStep();
        TutorialFaces.use(index);
        if (dialog == null || next == null || !next.inDialog) {
            return;
        }
        overlay.setVisibility(View.GONE);
        TutorialDialogCoach.show(dialog, highlight, activity.getString(next.textRes), next.dialogCardOnTop,
                () -> {
                    Step now = currentStep();
                    if (now != null && now.inDialog) {
                        rewindTo(TutorialEvent.INSTALL_CHOICE);
                    }
                });
    }

    @Override
    public void noteHandoff(@Nullable android.app.Dialog dialog) {
        TutorialDialogCoach.handoff(dialog);
    }

    @Override
    public void skipFromDialog() {
        skipStep();
    }

    @Override
    public boolean enterApiaryByMarker() {
        Step step = currentStep();
        return step != null && step.vignette == 9 && step.advance == Advance.NAV;
    }

    @Override
    public boolean wantsWarehouseChoice() {
        Step step = currentStep();
        return step != null && (step.vignette == 161 || step.vignette == 162);
    }

    @Override
    public boolean expectsShop() {
        Step step = currentStep();
        return step != null && step.event == TutorialEvent.SHOP_OPENED;
    }

    @Override
    public boolean onlyTruck() {
        Step step = currentStep();
        return step != null && (step.vignette == 171 || step.vignette == 172);
    }

    @Override
    public boolean firstHivePath() {
        Step step = currentStep();
        return step != null && (step.vignette == 10 || step.vignette == 11);
    }

    @Override
    public boolean firstHiveOpen() {
        Step step = currentStep();
        return step != null && step.vignette == 11;
    }

    private void steerMap(@NonNull Step step) {
        if (step.vignette != 9 && step.vignette != 161 && step.vignette != 17) {
            return;
        }
        if (destId != R.id.mapFragment) {
            navController.navigate(R.id.mapFragment);
        }
        overlay.post(() -> {
            if (currentStep() != step) {
                return;
            }
            Fragment host = activity.getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment);
            Fragment current = host != null
                    ? host.getChildFragmentManager().getPrimaryNavigationFragment() : null;
            if (!(current instanceof com.apiculture.simulator.presentation.map.SharedMapFragment)) {
                return;
            }
            com.apiculture.simulator.presentation.map.SharedMapFragment map =
                    (com.apiculture.simulator.presentation.map.SharedMapFragment) current;
            if (step.vignette == 17) {
                map.focusTutorialShop();
            } else if (step.vignette == 9) {
                map.focusTutorialApiary(16f);
            } else {
                map.focusTutorialApiary(11f);
            }
        });
    }

    @Override
    public void replayFirstChapter() {
        if (uid == null) {
            return;
        }
        booted = true;
        progress.setDone(uid, TutorialChapter.FIRST_APIARY, false);
        progress.setStepIndex(uid, TutorialChapter.FIRST_APIARY, 0);
        queue.clear();
        begin(TutorialChapter.FIRST_APIARY, null, 0);
    }

    @Override
    public void replayContractsChapter() {
        if (uid == null) {
            return;
        }
        booted = true;
        progress.clearOnce(uid, "contracts_from_2");
        progress.setDone(uid, TutorialChapter.ORDERS, false);
        progress.setStepIndex(uid, TutorialChapter.ORDERS, 0);
        queue.clear();
        begin(TutorialChapter.ORDERS, null, 0);
    }

    private void restore() {
        if (uid == null) {
            return;
        }
        TutorialChapter saved = progress.active(uid);
        if (saved != null && !progress.isDone(uid, saved) && saved != TutorialChapter.CLIMATE) {
            begin(saved, null, progress.stepIndex(uid, saved));
            return;
        }
        if (!progress.isDone(uid, TutorialChapter.FIRST_APIARY)) {
            begin(TutorialChapter.FIRST_APIARY, null, progress.stepIndex(uid, TutorialChapter.FIRST_APIARY));
            return;
        }
        beginContractsChapter();
        if (active == null) {
            overlay.setVisibility(View.GONE);
        }
    }

    private void offer(TutorialChapter chapter, @Nullable String detail) {
        if (active != null || (chapter != TutorialChapter.FIRST_APIARY
                && !progress.isDone(uid, TutorialChapter.FIRST_APIARY))) {
            for (Pending pending : queue) {
                if (pending.chapter == chapter) {
                    return;
                }
            }
            queue.add(new Pending(chapter, detail));
            return;
        }
        begin(chapter, detail, 0);
    }

    private void begin(TutorialChapter chapter, @Nullable String detail, int at) {
        active = chapter;
        activeDetail = detail;
        index = Math.max(0, at);
        if (uid != null) {
            if (chapter == TutorialChapter.ORDERS) {
                progress.consumeOnce(uid, "contracts_from_2");
            }
            progress.setActive(uid, chapter);
            progress.setStepIndex(uid, chapter, index);
        }
        refresh();
    }

    private void refresh() {
        if (!booted || uid == null || isAuth(destId)) {
            overlay.setVisibility(View.GONE);
            return;
        }
        Step step = currentStep();
        if (active == null || step == null) {
            overlay.setVisibility(View.GONE);
            pump();
            return;
        }
        if (step.advance == Advance.NAV && screenMatches(step.screen)) {
            advance();
            return;
        }
        if (step.inDialog) {
            overlay.setVisibility(View.GONE);
            return;
        }
        TutorialFaces.use(index);
        portrait.setImageResource(TutorialFaces.drawable());
        show(step);
        steerMap(step);
    }

    private void show(@NonNull Step step) {
        overlay.setVisibility(View.VISIBLE);
        if (step.chapter == TutorialChapter.CLIMATE && activeDetail != null) {
            body.setText(activity.getString(step.textRes, activeDetail));
        } else {
            body.setText(step.textRes);
        }
        weather.setVisibility(step.vignette == 3 && step.chapter == TutorialChapter.FIRST_APIARY
                ? View.VISIBLE : View.GONE);
        boolean simulate = step.advance == Advance.SIMULATE_DAY;
        boolean reading = step.advance == Advance.NEXT || simulate || alreadySatisfied(step);
        next.setVisibility(reading ? View.VISIBLE : View.GONE);
        next.setText(simulate ? R.string.tutorial_day : R.string.tutorial_next);
        if (step.chapter == TutorialChapter.FIRST_APIARY && step.advance != Advance.NEXT) {
            skip.setText(R.string.tutorial_skip_step);
        } else if (step.chapter == TutorialChapter.FIRST_APIARY) {
            skip.setText(R.string.tutorial_skip_all);
        } else {
            skip.setText(R.string.tutorial_skip);
        }
        overlay.post(() -> placeHole(step, true, 0));
    }

    @Nullable
    private View hivePathTarget(@NonNull Step step) {
        if (step.vignette != 10 && step.vignette != 11) {
            return null;
        }
        Fragment host = activity.getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment);
        Fragment current = host != null
                ? host.getChildFragmentManager().getPrimaryNavigationFragment() : null;
        View root = current != null ? current.getView() : null;
        if (destId == R.id.hivesFragment && root != null) {
            View open = root.findViewById(R.id.btn_apiary_open);
            return open != null && open.getWidth() > 0 ? open : null;
        }
        if (destId == R.id.apiaryYardFragment && root != null && step.vignette != 11) {
            View target = root.findViewById(R.id.btn_yard_buy_hive);
            return target != null && target.getWidth() > 0 ? target : null;
        }
        View tab = binding.bottomNav.findViewById(R.id.hivesFragment);
        return tab != null && tab.getWidth() > 0 ? tab : null;
    }

    private void moveHiveHole(@NonNull View yard, @NonNull RectF viewLocal, @NonNull View pass) {
        int[] hostAt = new int[2];
        int[] yardAt = new int[2];
        overlay.getLocationOnScreen(hostAt);
        yard.getLocationOnScreen(yardAt);
        RectF union = new RectF(
                yardAt[0] - hostAt[0] + viewLocal.left,
                yardAt[1] - hostAt[1] + viewLocal.top,
                yardAt[0] - hostAt[0] + viewLocal.right,
                yardAt[1] - hostAt[1] + viewLocal.bottom);
        float pad = 12f * activity.getResources().getDisplayMetrics().density;
        union.inset(-pad, -pad);
        moveCard(true);
        overlay.setHole(union, pass);
    }

    private void placeHole(@NonNull Step step, boolean scroll, int attempt) {
        if (currentStep() != step) {
            return;
        }
        if (step.vignette == 11 && destId == R.id.apiaryYardFragment) {
            View pass = binding.navHostFragment.getParent() instanceof View
                    ? (View) binding.navHostFragment.getParent() : binding.getRoot();
            Fragment host = activity.getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment);
            Fragment current = host != null
                    ? host.getChildFragmentManager().getPrimaryNavigationFragment() : null;
            RectF local = new RectF();
            ApiaryYardView yard = null;
            if (current instanceof ApiaryYardFragment) {
                ApiaryYardFragment yardFragment =
                        (ApiaryYardFragment) current;
                yardFragment.guideFirstHive();
                if (current.getView() != null) {
                    yard = current.getView().findViewById(R.id.yard_view);
                }
            }
            final ApiaryYardView targetYard = yard;
            if (targetYard != null) {
                targetYard.setGuideRectListener(viewLocal -> {
                    if (currentStep() != step) {
                        return;
                    }
                    moveHiveHole(targetYard, viewLocal, pass);
                });
            }
            if (yard == null || !yard.copyFirstHiveRect(local)) {
                overlay.setHole(null, pass);
                if (attempt < 40) {
                    overlay.postDelayed(() -> placeHole(step, false, attempt + 1), 80);
                }
                return;
            }
            moveHiveHole(yard, local, pass);
            if (hiveGuideWatch != yard) {
                if (hiveGuideWatch != null && hiveGuideListener != null) {
                    hiveGuideWatch.removeOnLayoutChangeListener(hiveGuideListener);
                }
                hiveGuideWatch = yard;
                hiveGuideListener = (v, l, t, r, b, ol, ot, or, ob) -> {
                    if (currentStep() == step && (l != ol || t != ot || r != or || b != ob)) {
                        placeHole(step, false, attempt);
                    }
                };
                yard.addOnLayoutChangeListener(hiveGuideListener);
            }
            if (attempt < 12) {
                overlay.postDelayed(() -> placeHole(step, false, attempt + 1), 200);
            }
            return;
        }
        if (step.vignette == 10 || step.vignette == 11) {
            View target = hivePathTarget(step);
            View pass = binding.navHostFragment.getParent() instanceof View
                    ? (View) binding.navHostFragment.getParent() : binding.getRoot();
            if (target == null) {
                overlay.setHole(null, pass);
                if (attempt < 40) {
                    overlay.postDelayed(() -> placeHole(step, false, attempt + 1), 80);
                }
                return;
            }
            RectF union = new RectF();
            if (unionOf(target, union)) {
                float pad = 8f * activity.getResources().getDisplayMetrics().density;
                union.inset(-pad, -pad);
                moveCard(true);
                overlay.setHole(union, pass);
            }
            return;
        }
        Anchor anchor = effectiveAnchor(step);
        boolean liftCard = anchor == Anchor.HARVEST || anchor == Anchor.CONTRACT_DATES
                || anchor == Anchor.CONTRACT_TRAVEL || anchor == Anchor.CONTRACT_REWARD;
        if (!liftCard) {
            clearHarvestWatch();
        } else {
            moveCard(true);
        }
        if (scroll && liftCard) {
            overlay.post(() -> {
                if (currentStep() != step) {
                    return;
                }
                int targetId = contractLiftTarget(anchor);
                if (!liftIntoFreeBand(step, targetId, attempt)) {
                    placeHole(step, false, attempt);
                }
            });
            return;
        }
        RectF union = new RectF();
        boolean any = false;
        View scrolled = null;
        int[] ids = anchor.viewIds;
        if (anchor == Anchor.BOTTOM_NAV) {
            any = unionOf(binding.bottomNav, union);
        } else {
            for (int id : ids) {
                View target = findTarget(anchor.onActivity, id);
                if (target == null || target.getWidth() <= 0 || target.getHeight() <= 0) {
                    continue;
                }
                if (scroll && scrolled == null) {
                    Rect visible = new Rect();
                    if (!target.getGlobalVisibleRect(visible)
                            || visible.height() < target.getHeight() / 2) {
                        target.requestRectangleOnScreen(
                                new Rect(0, 0, target.getWidth(), target.getHeight()), false);
                        scrolled = target;
                    }
                }
                any = unionOf(target, union) || any;
            }
        }
        if (!any && anchor == Anchor.MAP) {
            Fragment host = activity.getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment);
            Fragment current = host != null
                    ? host.getChildFragmentManager().getPrimaryNavigationFragment() : null;
            if (current != null && current.getView() != null && current.getView().getWidth() > 0) {
                any = unionOf(current.getView(), union);
            }
        }
        if (scrolled != null) {
            overlay.post(() -> placeHole(step, false, attempt));
            return;
        }
        if (!any && attempt < (waitsForContractCard(anchor) ? 40 : 8)) {
            overlay.setHole(null, binding.getRoot());
            overlay.postDelayed(() -> placeHole(step, true, attempt + 1), 80);
            return;
        }
        float pad = 8f * activity.getResources().getDisplayMetrics().density;
        if (any && anchor == Anchor.HIVE_BIO) {
            union.left = 0f;
            union.right = overlay.getWidth();
        }
        if (any && (union.bottom < 0f || union.top > overlay.getHeight()) && attempt < 8) {
            overlay.postDelayed(() -> placeHole(step, true, attempt + 1), 80);
            return;
        }
        if (any) {
            union.inset(-pad, -pad);
        }
        moveCard(anchor.cardOnTop());
        View pass = binding.navHostFragment.getParent() instanceof View
                ? (View) binding.navHostFragment.getParent() : binding.getRoot();
        overlay.setHole(any ? union : null, pass);
    }

    private static int contractLiftTarget(@NonNull Anchor anchor) {
        switch (anchor) {
            case CONTRACT_DATES:
                return R.id.card_contract;
            case CONTRACT_TRAVEL:
                return R.id.ll_contract_travel;
            case CONTRACT_REWARD:
                return R.id.ll_contract_reward;
            case HARVEST:
            default:
                return R.id.tile_quick_harvest;
        }
    }
    private boolean liftIntoFreeBand(@NonNull Step step, int targetId, int attempt) {
        View target = findTarget(false, targetId);
        NestedScrollView scroller = scrollParent(target);
        if (target == null || scroller == null || target.getHeight() <= 0 || scroller.getHeight() <= 0) {
            return false;
        }
        View content = scroller.getChildAt(0);
        if (content != null && targetId == R.id.tile_quick_harvest) {
            watchHarvestContent(step, content);
        }
        View card = overlay.findViewById(R.id.tutorial_card);
        if (card.getTop() > overlay.getHeight() / 3 && attempt < 8) {
            overlay.post(() -> placeHole(step, true, attempt + 1));
            return true;
        }
        int[] targetAt = new int[2];
        int[] scrollAt = new int[2];
        int[] navAt = new int[2];
        int[] overlayAt = new int[2];
        target.getLocationOnScreen(targetAt);
        scroller.getLocationOnScreen(scrollAt);
        binding.bottomNav.getLocationOnScreen(navAt);
        overlay.getLocationOnScreen(overlayAt);
        float density = activity.getResources().getDisplayMetrics().density;
        int gap = Math.round(12f * density);
        int extra = targetId == R.id.tile_quick_harvest ? 0 : Math.round(72f * density);
        int bandTop = Math.max(scrollAt[1], overlayAt[1] + card.getBottom() + gap);
        int bandBottom = Math.min(scrollAt[1] + scroller.getHeight(), navAt[1] - gap - extra);
        if (bandBottom - bandTop < target.getHeight()) {
            bandTop = scrollAt[1];
            bandBottom = Math.min(scrollAt[1] + scroller.getHeight(), navAt[1] - gap - extra);
        }
        int delta = (targetAt[1] + target.getHeight() / 2) - ((bandTop + bandBottom) / 2);
        int maxScroll = content == null ? 0 : Math.max(0, content.getHeight() - scroller.getHeight());
        int desired = Math.max(0, Math.min(maxScroll, scroller.getScrollY() + delta));
        if (Math.abs(desired - scroller.getScrollY()) <= 2) {
            return false;
        }
        scroller.scrollTo(0, desired);
        scroller.post(() -> placeHole(step, false, attempt));
        return true;
    }

    private void watchHarvestContent(@NonNull Step step, @NonNull View content) {
        if (harvestWatch == content) {
            return;
        }
        clearHarvestWatch();
        harvestWatch = content;
        harvestWatchListener = (v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            if (bottom - top == oldBottom - oldTop || currentStep() != step) {
                if (currentStep() != step) {
                    clearHarvestWatch();
                }
                return;
            }
            overlay.post(() -> {
                if (currentStep() == step) {
                    placeHole(step, true, 0);
                }
            });
        };
        content.addOnLayoutChangeListener(harvestWatchListener);
    }

    private void clearHarvestWatch() {
        if (harvestWatch != null && harvestWatchListener != null) {
            harvestWatch.removeOnLayoutChangeListener(harvestWatchListener);
        }
        harvestWatch = null;
        harvestWatchListener = null;
    }

    @Nullable
    private static NestedScrollView scrollParent(@Nullable View target) {
        ViewParent parent = target == null ? null : target.getParent();
        while (parent != null && !(parent instanceof NestedScrollView)) {
            parent = parent.getParent();
        }
        return parent instanceof NestedScrollView ? (NestedScrollView) parent : null;
    }

    private boolean unionOf(@NonNull View target, @NonNull RectF union) {
        int[] host = new int[2];
        int[] at = new int[2];
        overlay.getLocationOnScreen(host);
        target.getLocationOnScreen(at);
        float left = at[0] - host[0];
        float top = at[1] - host[1];
        float right = left + target.getWidth();
        float bottom = top + target.getHeight();
        if (!union.isEmpty() || union.right > union.left) {
            union.union(left, top, right, bottom);
        } else {
            union.set(left, top, right, bottom);
        }
        return true;
    }

    @Nullable
    private View findTarget(boolean onActivity, int id) {
        if (onActivity) {
            View tab = binding.bottomNav.findViewById(id);
            return tab != null ? tab : binding.bottomNav;
        }
        Fragment host = activity.getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment);
        if (host == null) {
            return null;
        }
        Fragment current = host.getChildFragmentManager().getPrimaryNavigationFragment();
        if (current == null || current.getView() == null) {
            return null;
        }
        return current.getView().findViewById(id);
    }

    private void moveCard(boolean onTop) {
        View card = overlay.findViewById(R.id.tutorial_card);
        if (!(card.getLayoutParams() instanceof FrameLayout.LayoutParams)) {
            return;
        }
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) card.getLayoutParams();
        int margin = Math.round(12f * activity.getResources().getDisplayMetrics().density);
        int aboveNav = Math.round(76f * activity.getResources().getDisplayMetrics().density);
        lp.gravity = onTop ? Gravity.TOP : Gravity.BOTTOM;
        lp.topMargin = onTop ? margin : 0;
        lp.bottomMargin = onTop ? 0 : aboveNav;
        card.setLayoutParams(lp);
    }

    @NonNull
    private Anchor effectiveAnchor(@NonNull Step step) {
        if (step.advance == Advance.NAV || step.screen == Screen.ANY || screenMatches(step.screen)) {
            return step.anchor;
        }
        switch (step.screen) {
            case DASHBOARD:
                return Anchor.TAB_DASH;
            case MAP:
                return Anchor.TAB_MAP;
            case MARKET:
                return Anchor.TAB_MARKET;
            case YARD:
            case HIVE:
                return Anchor.TAB_HIVES;
            case ANY:
            default:
                return step.anchor;
        }
    }

    private void onNext() {
        Step step = currentStep();
        if (step == null) {
            return;
        }
        if (step.advance == Advance.SIMULATE_DAY) {
            simulateDay();
            return;
        }
        if (step.advance == Advance.NEXT || alreadySatisfied(step)) {
            advance();
        }
    }

    /** Contratos o Iberia ya marcados: se puede seguir con Siguiente. */
    private boolean alreadySatisfied(@NonNull Step step) {
        if (step.event == TutorialEvent.CONTRACTS_TAB) {
            return checked(R.id.btn_market_contracts);
        }
        if (step.event == TutorialEvent.CONTRACTS_IBERIA) {
            return checked(R.id.btn_market_iberia);
        }
        return false;
    }

    private boolean checked(int id) {
        View view = findTarget(false, id);
        return view instanceof android.widget.Checkable && ((android.widget.Checkable) view).isChecked();
    }

    private static boolean waitsForContractCard(@NonNull Anchor anchor) {
        switch (anchor) {
            case CONTRACT_FARMER:
            case CONTRACT_CROP:
            case CONTRACT_MIN:
            case CONTRACT_DATES:
            case CONTRACT_TRAVEL:
            case CONTRACT_REWARD:
                return true;
            default:
                return false;
        }
    }

    /** Capítulo 5. Desde el nivel 2, si aún no se ha hecho. */
    private void beginContractsChapter() {
        if (uid == null || active == TutorialChapter.ORDERS) {
            return;
        }
        int level = ((ApicultureApp) activity.getApplication())
                .getPlayerProgressRepository().getLevel(uid);
        if (level < 2 || progress.isDone(uid, TutorialChapter.ORDERS)) {
            return;
        }
        progress.consumeOnce(uid, "contracts_from_2");
        progress.setDone(uid, TutorialChapter.ORDERS, false);
        offer(TutorialChapter.ORDERS, null);
    }

    private void simulateDay() {
        SignedInUser user = PlayerAuth.getInstance().getCurrentUser();
        String owner = user != null ? user.getUid() : uid;
        if (owner == null) {
            return;
        }
        next.setEnabled(false);
        ApicultureApp app = (ApicultureApp) activity.getApplication();
        app.getHiveRepository().debugSimulateNextProductionDay(owner, message -> {
            next.setEnabled(true);
            if (message != null && (message.startsWith("Producción simulada")
                    || message.startsWith("Avance sin producción"))) {
                onEvent(TutorialEvent.DAY_SIMULATED, null);
            } else if (message != null) {
                GameNotice.show(activity, message);
            }
        });
    }

    private void advance() {
        if (active == null || uid == null) {
            return;
        }
        index++;
        progress.setStepIndex(uid, active, index);
        if (index >= TutorialScript.steps(active).length) {
            finishChapter();
        } else {
            refresh();
        }
    }

    private void onSkip() {
        Step step = currentStep();
        if (step != null && step.chapter == TutorialChapter.FIRST_APIARY
                && step.advance != Advance.NEXT) {
            skipStep();
            return;
        }
        skipChapter();
    }

    /** Salta la viñeta actual y las que solo se ven dentro del diálogo que explica. */
    private void skipStep() {
        if (active == null || uid == null) {
            return;
        }
        Step[] steps = TutorialScript.steps(active);
        index++;
        while (index < steps.length && steps[index].inDialog) {
            index++;
        }
        progress.setStepIndex(uid, active, index);
        if (index >= steps.length) {
            finishChapter();
        } else {
            refresh();
        }
    }

    private void skipChapter() {
        finishChapter();
    }

    /** Si cierra el diálogo de instalar, vuelve a pedir el toque en el hexágono. */
    private void rewindTo(TutorialEvent event) {
        if (active == null || uid == null) {
            return;
        }
        Step[] steps = TutorialScript.steps(active);
        for (int i = 0; i < steps.length; i++) {
            if (steps[i].event == event && !steps[i].inDialog) {
                index = i;
                progress.setStepIndex(uid, active, index);
                refresh();
                return;
            }
        }
    }

    private void finishChapter() {
        if (uid != null && active != null) {
            if (active == TutorialChapter.CLIMATE && activeDetail != null) {
                progress.markClimate(uid, activeDetail);
            } else {
                progress.setDone(uid, active, true);
            }
            progress.setActive(uid, null);
            progress.setStepIndex(uid, active, 0);
        }
        active = null;
        activeDetail = null;
        index = 0;
        pump();
    }

    private void pump() {
        while (!queue.isEmpty()) {
            Pending nextChapter = queue.removeFirst();
            if (uid == null) {
                return;
            }
            if (nextChapter.chapter == TutorialChapter.CLIMATE) {
                if (nextChapter.detail == null || progress.climateShown(uid, nextChapter.detail)) {
                    continue;
                }
            } else if (progress.isDone(uid, nextChapter.chapter)) {
                continue;
            }
            if (nextChapter.chapter != TutorialChapter.FIRST_APIARY
                    && !progress.isDone(uid, TutorialChapter.FIRST_APIARY)) {
                queue.addFirst(nextChapter);
                overlay.setVisibility(View.GONE);
                return;
            }
            begin(nextChapter.chapter, nextChapter.detail, 0);
            return;
        }
        beginContractsChapter();
        if (active == null) {
            overlay.setVisibility(View.GONE);
        }
    }

    @Nullable
    private Step currentStep() {
        if (active == null) {
            return null;
        }
        Step[] steps = TutorialScript.steps(active);
        if (index < 0 || index >= steps.length) {
            return null;
        }
        return steps[index];
    }

    private boolean completes(@NonNull Step step, @NonNull TutorialEvent event) {
        if (step.event != event) {
            return false;
        }
        return step.advance == Advance.EVENT || step.advance == Advance.SIMULATE_DAY;
    }

    @Nullable
    private static TutorialChapter chapterFor(@NonNull TutorialEvent event) {
        switch (event) {
            case CARE_FEED:
            case CARE_TREAT:
            case CARE_QUEEN:
                return TutorialChapter.HIVE_CARE;
            case PLANTING:
                return TutorialChapter.PLANTING;
            case TRANSHUMANCE:
                return TutorialChapter.TRANSHUMANCE;
            case TRUCK_BOUGHT:
                return TutorialChapter.TRUCK;
            case CLIMATE:
                return TutorialChapter.CLIMATE;
            case PORT_OPENED:
            case SHIP_BOUGHT:
                return TutorialChapter.INTERNATIONAL;
            case APIARY_INSTALLED:
            case HIVE_BOUGHT:
            case WAREHOUSE_BOUGHT:
            case DAY_SIMULATED:
            case HARVESTED:
            default:
                return null;
        }
    }

    private boolean screenMatches(Screen screen) {
        switch (screen) {
            case DASHBOARD:
                return destId == R.id.dashboardFragment;
            case MAP:
                return destId == R.id.mapFragment;
            case YARD:
                return destId == R.id.apiaryYardFragment;
            case HIVE:
                return destId == R.id.hiveDetailFragment;
            case MARKET:
                return destId == R.id.marketFragment;
            case SHOP:
                return destId == R.id.shopFragment;
            case ANY:
            default:
                return true;
        }
    }

    private static boolean isAuth(int destinationId) {
        return destinationId == R.id.loginFragment || destinationId == R.id.profileSetupFragment
                || destinationId == 0;
    }
}
