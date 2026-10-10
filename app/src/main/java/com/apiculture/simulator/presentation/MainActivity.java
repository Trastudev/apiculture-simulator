package com.apiculture.simulator.presentation;

import android.Manifest;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.navigation.NavController;
import androidx.navigation.NavDestination;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.apiculture.simulator.ApicultureApp;
import com.apiculture.simulator.R;
import com.apiculture.simulator.data.local.entity.HiveEntity;
import com.apiculture.simulator.data.repository.TickAppliedDayResult;
import com.apiculture.simulator.data.repository.GameServer;
import com.apiculture.simulator.data.repository.HarvestReceipts;
import com.apiculture.simulator.data.repository.OrderReceipts;
import com.apiculture.simulator.databinding.ActivityMainBinding;
import com.apiculture.simulator.domain.health.HiveAlertBadge;
import com.apiculture.simulator.notification.DailyProductionAlarmScheduler;
import com.apiculture.simulator.data.repository.GameStartupWarmup;
import com.apiculture.simulator.presentation.common.CalculatingDailyDialog;
import com.apiculture.simulator.presentation.common.DailySummaryDialog;
import com.apiculture.simulator.presentation.common.GameLoadingDialog;
import com.apiculture.simulator.presentation.common.TruckLivePrompt;
import com.apiculture.simulator.presentation.hive.HarvestCollectDialogs;
import com.apiculture.simulator.presentation.market.HoneyOrderDialogs;
import com.apiculture.simulator.presentation.tutorial.TutorialBus;
import com.apiculture.simulator.presentation.tutorial.TutorialController;
import com.apiculture.simulator.data.session.PlayerAuth;
import com.apiculture.simulator.data.session.SignedInUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONObject;

import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private static final String PREF_NOTIF_PERM_PROMPTED = "notification_permission_prompted";

    /** Raíz de la zona “logueado”: todo lo demás se apila encima y al cambiar de tab se desapila hasta aquí. */
    private static final int MAIN_ROOT_ID = R.id.dashboardFragment;

    private static final int[] BOTTOM_NAV_DESTINATIONS = {
            R.id.dashboardFragment,
            R.id.hivesFragment,
            R.id.mapFragment,
            R.id.marketFragment,
            R.id.obradoresFragment,
    };

    private ActivityMainBinding binding;
    private NavController navController;
    @Nullable
    private TutorialController tutorial;
    private int pendingBottomNavTarget;
    private boolean activityResumed;
    private boolean tickedThisResume;
    private boolean dailyTickInFlight;
    @Nullable
    private TickAppliedDayResult pendingDailyResult;
    @Nullable
    private String dailyTickUserId;
    @Nullable
    private GameStartupWarmup.Listener warmupListener;
    @Nullable
    private AlertDialog serverUnavailableDialog;
    private final GameServer.ConnectionListener serverConnectionListener =
            available -> runOnUiThread(() -> onServerAvailabilityChanged(available));

    private ActivityResultLauncher<String> notificationPermissionLauncher;
    @Nullable
    private WindowInsetsCompat windowInsets;
    private int toolbarContentHeight;
    private int bottomNavPadTop = -1;
    private int bottomNavPadBottom = -1;
    private int tutorialCardBaseMargin = -1;

    private static boolean isBottomNavDestination(int destinationId) {
        for (int id : BOTTOM_NAV_DESTINATIONS) {
            if (id == destinationId) {
                return true;
            }
        }
        return false;
    }

    /**
     * Cambio de pestaña inferior: una sola regla para evitar estados incoherentes con NavigationUI + restoreState.
     * Desapila hasta dejar {@link #MAIN_ROOT_ID} arriba (sin quitarlo) y navega al destino del tab.
     */
    private void navigateToBottomNavTab(int targetId) {
        if (navController == null || binding == null) {
            return;
        }
        if (GameServer.enabled() && !GameServer.isAvailable()) {
            showServerUnavailableDialog();
            return;
        }
        pendingBottomNavTarget = targetId;
        binding.navHostFragment.animate().cancel();
        binding.navHostFragment.setAlpha(0f);
        navController.navigate(
                targetId,
                null,
                new NavOptions.Builder()
                        .setPopUpTo(MAIN_ROOT_ID, false)
                        .setLaunchSingleTop(true)
                        .setRestoreState(false)
                        .setEnterAnim(0)
                        .setExitAnim(0)
                        .setPopEnterAnim(0)
                        .setPopExitAnim(0)
                        .build());
        binding.navHostFragment.postDelayed(() -> {
            if (binding == null || binding.navHostFragment.getAlpha() >= 0.99f) {
                return;
            }
            pendingBottomNavTarget = 0;
            binding.navHostFragment.animate().alpha(1f).setDuration(90).start();
        }, 350);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        notificationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> DailyProductionAlarmScheduler.scheduleNext(MainActivity.this));
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            windowInsets = insets;
            applySystemBarInsets();
            return insets;
        });
        binding.bottomNav.setItemIconTintList(null);
        GameServer.addConnectionListener(serverConnectionListener);
        GameServer.checkServerAsync();

        setSupportActionBar(binding.toolbar);

        NavHostFragment navHostFragment =
                (NavHostFragment) getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment);
        if (navHostFragment != null) {
            navController = navHostFragment.getNavController();
            AppBarConfiguration appBarConfiguration = new AppBarConfiguration.Builder(
                    R.id.dashboardFragment,
                    R.id.hivesFragment,
                    R.id.apiaryYardFragment,
                    R.id.hiveDetailFragment,
                    R.id.mapFragment,
                    R.id.marketFragment,
                    R.id.obradoresFragment,
                    R.id.eventsFragment
            ).build();
            NavigationUI.setupWithNavController(binding.toolbar, navController, appBarConfiguration);
            tutorial = new TutorialController(MainActivity.this, binding, navController);
            // Menú inferior: solo NavigationUI aquí falla (restoreState, detalle colmena, etc.)
            binding.bottomNav.setOnItemSelectedListener(item -> {
                if (navController == null) {
                    return false;
                }
                int targetId = item.getItemId();
                if (!isBottomNavDestination(targetId)) {
                    return false;
                }
                if (TutorialBus.firstHivePath() && targetId != R.id.hivesFragment) {
                    return false;
                }
                NavDestination current = navController.getCurrentDestination();
                if (current != null && current.getId() == targetId) {
                    return true;
                }
                if (targetId == R.id.mapFragment && !GameStartupWarmup.isReady()) {
                    GameLoadingDialog.show(MainActivity.this);
                    GameStartupWarmup.addListener(new GameStartupWarmup.Listener() {
                        @Override
                        public void onProgress(int done, int total, @NonNull String status) {
                        }

                        @Override
                        public void onReady() {
                            GameStartupWarmup.removeListener(this);
                            GameLoadingDialog.dismiss();
                            if (!isFinishing() && !isDestroyed()) {
                                navigateToBottomNavTab(R.id.mapFragment);
                            }
                        }
                    });
                    return false;
                }
                navigateToBottomNavTab(targetId);
                return true;
            });

            navController.addOnDestinationChangedListener((controller, destination, arguments) -> {
                if (pendingBottomNavTarget != 0 && destination.getId() != pendingBottomNavTarget) {
                    return;
                }
                if (pendingBottomNavTarget != 0 && destination.getId() == pendingBottomNavTarget) {
                    pendingBottomNavTarget = 0;
                    if (binding.navHostFragment.getAlpha() < 1f) {
                        binding.navHostFragment.animate().alpha(1f).setDuration(90).start();
                    }
                }
                boolean onLogin = destination.getId() == R.id.loginFragment;
                boolean onProfileSetup = destination.getId() == R.id.profileSetupFragment;
                boolean onDashboard = destination.getId() == R.id.dashboardFragment;
                boolean onHives = destination.getId() == R.id.hivesFragment;
                boolean onApiaryYard = destination.getId() == R.id.apiaryYardFragment;
                boolean onHiveDetail = destination.getId() == R.id.hiveDetailFragment;
                boolean onMap = destination.getId() == R.id.mapFragment;
                boolean onMarket = destination.getId() == R.id.marketFragment;
                boolean onAdminEvents = destination.getId() == R.id.adminEventsFragment;
                boolean onAdminGrants = destination.getId() == R.id.adminGrantsFragment;
                boolean onShop = destination.getId() == R.id.shopFragment;
                boolean onWorkshop = destination.getId() == R.id.workshopFragment
                        || destination.getId() == R.id.obradoresFragment;
                if (tutorial != null) {
                    tutorial.onDestination(destination.getId());
                }

                // Sin barra superior en estas pantallas (más espacio; mercado sin título en toolbar)
                binding.toolbar.setVisibility(
                        onLogin || onProfileSetup || onDashboard || onHives || onApiaryYard || onHiveDetail || onMap || onMarket
                                || onAdminEvents || onAdminGrants || onShop || onWorkshop
                                ? View.GONE : View.VISIBLE);
                binding.bottomNav.setVisibility(onLogin || onProfileSetup ? View.GONE : View.VISIBLE);
                applySystemBarInsets();
                if (onLogin || onProfileSetup) {
                    binding.navHivesAlertDot.setVisibility(View.GONE);
                    binding.navObradoresAlertDot.setVisibility(View.GONE);
                } else {
                    positionHivesAlertDot();
                    refreshObradoresAlert();
                }

                // Sincronizar pestaña inferior (el listener custom no lo hace solo)
                if (!onLogin && !onProfileSetup) {
                    int tabId = (onHiveDetail || onApiaryYard) ? R.id.hivesFragment : destination.getId();
                    MenuItem tab = binding.bottomNav.getMenu().findItem(tabId);
                    if (tab != null && tab.isCheckable()) {
                        tab.setChecked(true);
                    }
                    maybeEnterSession();
                }
            });
        }

        observeHiveAlerts();
        binding.getRoot().post(this::positionHivesAlertDot);
        binding.bottomNav.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) ->
                positionHivesAlertDot());
    }

    /** Android 15+ dibuja detrás de las barras. El contenido queda en la zona segura. */
    private void applySystemBarInsets() {
        if (binding == null || windowInsets == null) {
            return;
        }
        Insets bars = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
        Insets ime = windowInsets.getInsets(WindowInsetsCompat.Type.ime());
        boolean toolbarVisible = binding.toolbar.getVisibility() == View.VISIBLE;
        boolean navVisible = binding.bottomNav.getVisibility() == View.VISIBLE;
        if (toolbarContentHeight == 0) {
            toolbarContentHeight = binding.toolbar.getLayoutParams().height;
        }
        if (bottomNavPadTop < 0) {
            bottomNavPadTop = binding.bottomNav.getPaddingTop();
            bottomNavPadBottom = binding.bottomNav.getPaddingBottom();
        }
        View column = (View) binding.navHostFragment.getParent();
        column.setPadding(bars.left, 0, bars.right, navVisible ? ime.bottom : 0);
        ViewGroup.LayoutParams toolbarLp = binding.toolbar.getLayoutParams();
        toolbarLp.height = toolbarContentHeight + (toolbarVisible ? bars.top : 0);
        binding.toolbar.setLayoutParams(toolbarLp);
        binding.toolbar.setPadding(0, toolbarVisible ? bars.top : 0, 0, 0);
        int contentBottom = navVisible ? 0 : Math.max(bars.bottom, ime.bottom);
        binding.navHostFragment.setPadding(0, toolbarVisible ? 0 : bars.top, 0, contentBottom);
        binding.bottomNav.setPadding(0, bottomNavPadTop, 0, bottomNavPadBottom
                + (navVisible && ime.bottom == 0 ? bars.bottom : 0));
        View tutorialCard = binding.getRoot().findViewById(R.id.tutorial_card);
        if (tutorialCard != null && tutorialCard.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams cardLp =
                    (ViewGroup.MarginLayoutParams) tutorialCard.getLayoutParams();
            if (tutorialCardBaseMargin < 0) {
                tutorialCardBaseMargin = cardLp.bottomMargin;
            }
            cardLp.bottomMargin = tutorialCardBaseMargin + Math.max(bars.bottom, ime.bottom);
            tutorialCard.setLayoutParams(cardLp);
        }
    }

    private void observeHiveAlerts() {
        ApicultureApp app = (ApicultureApp) getApplication();
        String uid = PlayerAuth.getInstance().getUid();
        if (uid == null || uid.isEmpty()) {
            uid = "guest";
        }
        app.getHiveRepository().getLocalHives(uid).observe(this, this::updateHivesAlertDot);
    }

    private void updateHivesAlertDot(@Nullable List<HiveEntity> hives) {
        if (binding == null) {
            return;
        }
        if (binding.bottomNav.getVisibility() != View.VISIBLE) {
            binding.navHivesAlertDot.setVisibility(View.GONE);
            return;
        }
        boolean show = HiveAlertBadge.anyNeedsAttention(hives);
        binding.navHivesAlertDot.setAlertVisible(show);
        if (show) {
            binding.getRoot().post(this::positionHivesAlertDot);
        }
    }

    private void positionHivesAlertDot() {
        if (binding == null) {
            return;
        }
        positionNavDot(binding.navHivesAlertDot, R.id.hivesFragment);
        positionNavDot(binding.navObradoresAlertDot, R.id.obradoresFragment);
    }

    // ---------- Punto rojo de Obradores: Toni tiene la exclamación en el 3D ----------

    private static final long OBRADOR_ALERT_EVERY_MS = 20_000L;
    private final android.os.Handler obradorAlertHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final java.util.concurrent.ExecutorService obradorAlertIo =
            java.util.concurrent.Executors.newSingleThreadExecutor();
    private final Runnable obradorAlertTick = new Runnable() {
        @Override
        public void run() {
            refreshObradoresAlert();
            obradorAlertHandler.postDelayed(this, OBRADOR_ALERT_EVERY_MS);
        }
    };

    /** Las tandas avanzan con el reloj: se mira cada poco y al cambiar de pantalla. */
    private void refreshObradoresAlert() {
        String uid = PlayerAuth.getInstance().getUid();
        android.content.Context app = getApplicationContext();
        obradorAlertIo.execute(() -> {
            boolean show = uid != null && !uid.isEmpty()
                    && com.apiculture.simulator.data.repository.WorkshopStore.anyNeedsAttention(app, uid);
            runOnUiThread(() -> {
                if (binding == null || isFinishing()) {
                    return;
                }
                boolean visible = show && binding.bottomNav.getVisibility() == View.VISIBLE;
                binding.navObradoresAlertDot.setAlertVisible(visible);
                if (visible) {
                    binding.getRoot().post(this::positionHivesAlertDot);
                }
            });
        });
    }

    private void positionNavDot(@NonNull View dot, int itemId) {
        View item = binding.bottomNav.findViewById(itemId);
        if (item == null || dot.getVisibility() != View.VISIBLE) {
            return;
        }
        int[] itemLoc = new int[2];
        int[] rootLoc = new int[2];
        item.getLocationInWindow(itemLoc);
        binding.getRoot().getLocationInWindow(rootLoc);
        float x = itemLoc[0] - rootLoc[0] + item.getWidth() * 0.62f - dot.getWidth() / 2f;
        float y = itemLoc[1] - rootLoc[1] + item.getHeight() * 0.12f;
        dot.setX(x);
        dot.setY(y);
    }

    @Override
    public void onUserInteraction() {
        super.onUserInteraction();
    }

    @Override
    protected void onResume() {
        super.onResume();
        activityResumed = true;
        tickedThisResume = false;
        showPendingDailyResult();
        maybeEnterSession();
        GameServer.checkServerAsync();
        HarvestReceipts.setListener(this::showPendingHarvestReceipts);
        showPendingHarvestReceipts();
        OrderReceipts.setListener(this::showPendingOrderReceipts);
        showPendingOrderReceipts();
        obradorAlertHandler.removeCallbacks(obradorAlertTick);
        obradorAlertHandler.post(obradorAlertTick);
    }

    private boolean harvestReceiptShowing;
    private boolean orderReceiptShowing;

    private void showPendingHarvestReceipts() {
        if (harvestReceiptShowing || isFinishing() || isDestroyed()) {
            return;
        }
        HarvestReceipts.Receipt receipt = HarvestReceipts.peek(this);
        if (receipt == null) {
            return;
        }
        harvestReceiptShowing = true;
        HarvestCollectDialogs.showSummary(this, receipt.hiveCount, receipt.totalKg, receipt.kgByFlora,
                () -> {
                    HarvestReceipts.drop(this, receipt.tripId);
                    harvestReceiptShowing = false;
                    showPendingHarvestReceipts();
                });
    }

    private void showPendingOrderReceipts() {
        if (orderReceiptShowing || isFinishing() || isDestroyed()) {
            return;
        }
        OrderReceipts.Receipt receipt = OrderReceipts.peek(this);
        if (receipt == null) {
            return;
        }
        orderReceiptShowing = true;
        HoneyOrderDialogs.showDeliverySummary(this, receipt, () -> {
            OrderReceipts.drop(this, receipt.id);
            orderReceiptShowing = false;
            showPendingOrderReceipts();
        });
    }

    private void onServerAvailabilityChanged(boolean available) {
        if (isFinishing() || isDestroyed() || binding == null) {
            return;
        }
        if (!GameServer.enabled()) {
            dismissServerUnavailableDialog();
            return;
        }
        binding.bottomNav.setEnabled(available);
        binding.navHostFragment.setAlpha(available ? 1f : 0.35f);
        if (available) {
            dismissServerUnavailableDialog();
            if (activityResumed) {
                maybeEnterSession();
            }
        } else {
            showServerUnavailableDialog();
        }
    }

    private void showServerUnavailableDialog() {
        if (!GameServer.enabled() || isFinishing() || isDestroyed()) {
            return;
        }
        if (serverUnavailableDialog != null && serverUnavailableDialog.isShowing()) {
            if (serverUnavailableDialog.getButton(AlertDialog.BUTTON_POSITIVE) != null) {
                serverUnavailableDialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
            }
            return;
        }
        serverUnavailableDialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.server_unavailable_title)
                .setMessage(R.string.server_unavailable_message)
                .setCancelable(false)
                .setPositiveButton(R.string.server_retry, null)
                .create();
        serverUnavailableDialog.setCanceledOnTouchOutside(false);
        serverUnavailableDialog.setOnShowListener(ignored -> {
            if (serverUnavailableDialog == null) {
                return;
            }
            serverUnavailableDialog.getButton(AlertDialog.BUTTON_POSITIVE)
                    .setOnClickListener(view -> {
                        serverUnavailableDialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                        GameServer.checkServerAsync();
                    });
        });
        serverUnavailableDialog.show();
    }

    private void dismissServerUnavailableDialog() {
        if (serverUnavailableDialog != null) {
            if (serverUnavailableDialog.isShowing()) {
                serverUnavailableDialog.dismiss();
            }
            serverUnavailableDialog = null;
        }
    }

    private boolean onAuthScreens() {
        if (navController == null) {
            return true;
        }
        NavDestination dest = navController.getCurrentDestination();
        if (dest == null) {
            return true;
        }
        int id = dest.getId();
        return id == R.id.loginFragment || id == R.id.profileSetupFragment;
    }

    private void maybeEnterSession() {
        SignedInUser user = PlayerAuth.getInstance().getCurrentUser();
        if (user == null) {
            dailyTickInFlight = false;
            dailyTickUserId = null;
            pendingDailyResult = null;
            DailyProductionAlarmScheduler.markSessionActive(this, false);
            GameLoadingDialog.dismiss();
            return;
        }
        if (onAuthScreens()) {
            return;
        }
        if (GameServer.enabled() && !GameServer.isAvailable()) {
            showServerUnavailableDialog();
            return;
        }
        DailyProductionAlarmScheduler.markSessionActive(this, true);
        if (GameStartupWarmup.isReady()) {
            runDailyTick(user);
            maybeRequestNotificationPermission();
            if (tutorial != null) {
                tutorial.onSession(user.getUid());
            }
            return;
        }
        GameLoadingDialog.show(this);
        if (warmupListener != null) {
            GameStartupWarmup.removeListener(warmupListener);
        }
        warmupListener = new GameStartupWarmup.Listener() {
            @Override
            public void onProgress(int done, int total, @NonNull String status) {
            }

            @Override
            public void onReady() {
                GameStartupWarmup.removeListener(this);
                warmupListener = null;
                GameLoadingDialog.dismiss();
                if (activityResumed && !isFinishing() && !isDestroyed()) {
                    runDailyTick(user);
                    maybeRequestNotificationPermission();
                    if (tutorial != null) {
                        tutorial.onSession(user.getUid());
                    }
                }
            }
        };
        GameStartupWarmup.addListener(warmupListener);
    }

    private void runDailyTick(@NonNull SignedInUser user) {
        if (tickedThisResume || dailyTickInFlight || isFinishing() || isDestroyed()) {
            return;
        }
        tickedThisResume = true;
        dailyTickInFlight = true;
        dailyTickUserId = user.getUid();
        String timeZoneId = ZoneId.systemDefault().getId();
        String uid = user.getUid();
        new Thread(() -> {
            try {
                JSONObject profile = new JSONObject();
                profile.put("timeZoneId", timeZoneId);
                GameServer.savePlayer(uid, profile);
            } catch (Exception ignored) {
            }
        }, "tz-sync").start();
        ApicultureApp app = (ApicultureApp) getApplication();
        app.applyAdminForcedResetIfNeeded(user.getUid(), () ->
                app.getUserGameStateRepository().pullAndApplyThen(user.getUid(),
                        () -> app.getHiveRepository().tickDailyProductionForOwner(
                                user.getUid(),
                                () -> CalculatingDailyDialog.show(MainActivity.this),
                                result -> {
                                    CalculatingDailyDialog.dismiss();
                                    dailyTickInFlight = false;
                                    Log.i("MainActivity", "Daily tick completed: days=" + result.days.size());
                                    if (!isCurrentDailyUser(user.getUid())) {
                                        return;
                                    }
                                    presentDailyResult(result);
                                })));
    }

    private boolean isCurrentDailyUser(@Nullable String uid) {
        SignedInUser current = PlayerAuth.getInstance().getCurrentUser();
        return uid != null && current != null && uid.equals(current.getUid())
                && uid.equals(dailyTickUserId);
    }

    private void presentDailyResult(@Nullable TickAppliedDayResult result) {
        if (result == null || result.days.isEmpty()) {
            if (!activityResumed && !isFinishing() && !isDestroyed()) {
                pendingDailyResult = result != null ? result : TickAppliedDayResult.NONE;
                return;
            }
            if (activityResumed && !isFinishing() && !isDestroyed()) {
                DailySummaryDialog.show(this, result,
                        () -> TruckLivePrompt.maybeAsk(MainActivity.this));
            }
            return;
        }
        if (!activityResumed || isFinishing() || isDestroyed()) {
            if (!isFinishing() && !isDestroyed()) {
                pendingDailyResult = result;
            }
            return;
        }
        pendingDailyResult = null;
        DailySummaryDialog.show(this, result,
                () -> TruckLivePrompt.maybeAsk(MainActivity.this));
    }

    private void showPendingDailyResult() {
        TickAppliedDayResult result = pendingDailyResult;
        if (result == null) {
            return;
        }
        pendingDailyResult = null;
        presentDailyResult(result);
    }

    @Override
    protected void onPause() {
        activityResumed = false;
        obradorAlertHandler.removeCallbacks(obradorAlertTick);
        HarvestReceipts.setListener(null);
        OrderReceipts.setListener(null);
        CalculatingDailyDialog.dismiss();
        super.onPause();
        SignedInUser user = PlayerAuth.getInstance().getCurrentUser();
        if (user != null) {
            ((ApicultureApp) getApplication()).getUserGameStateRepository().pushImmediate(user.getUid());
        }
    }

    private void maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return;
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) {
            return;
        }
        SharedPreferences p = getPreferences(MODE_PRIVATE);
        if (p.getBoolean(PREF_NOTIF_PERM_PROMPTED, false)) {
            return;
        }
        p.edit().putBoolean(PREF_NOTIF_PERM_PROMPTED, true).apply();
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.toolbar_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_events) {
            if (GameServer.enabled() && !GameServer.isAvailable()) {
                showServerUnavailableDialog();
                return true;
            }
            if (navController != null) {
                navController.navigate(R.id.eventsFragment);
            }
            return true;
        }
        if (item.getItemId() == R.id.action_logout) {
            logout();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        GameServer.removeConnectionListener(serverConnectionListener);
        dismissServerUnavailableDialog();
        if (warmupListener != null) {
            GameStartupWarmup.removeListener(warmupListener);
            warmupListener = null;
        }
        if (tutorial != null) {
            tutorial.detach();
            tutorial = null;
        }
        GameLoadingDialog.dismiss();
        super.onDestroy();
    }

    public void logout() {
        ((ApicultureApp) getApplication()).getAuthRepository().signOut();
        showLogin();
    }

    /** Vuelve al inicio de sesión después de borrar la cuenta. La sesión ya está cerrada. */
    public void showLoginAfterAccountDeletion() {
        showLogin();
    }

    private void showLogin() {
        tickedThisResume = false;
        dailyTickInFlight = false;
        dailyTickUserId = null;
        pendingDailyResult = null;
        DailyProductionAlarmScheduler.markSessionActive(this, false);
        if (navController != null) {
            NavOptions opts = new NavOptions.Builder()
                    .setPopUpTo(R.id.nav_graph, true)
                    .build();
            navController.navigate(R.id.loginFragment, null, opts);
        }
    }
}
