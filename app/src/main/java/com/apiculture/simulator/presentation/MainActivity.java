package com.apiculture.simulator.presentation;

import android.Manifest;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.navigation.NavController;
import androidx.navigation.NavDestination;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.apiculture.simulator.ApicultureApp;
import com.apiculture.simulator.R;
import com.apiculture.simulator.databinding.ActivityMainBinding;
import com.apiculture.simulator.notification.DailyProductionAlarmScheduler;
import com.apiculture.simulator.presentation.common.DailySummaryDialog;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.time.ZoneId;
import java.util.HashMap;
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
            R.id.rankingFragment,
    };

    private ActivityMainBinding binding;
    private NavController navController;

    private ActivityResultLauncher<String> notificationPermissionLauncher;

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
        if (navController == null) {
            return;
        }
        navController.navigate(
                targetId,
                null,
                new NavOptions.Builder()
                        .setPopUpTo(MAIN_ROOT_ID, false)
                        .setLaunchSingleTop(true)
                        .setRestoreState(false)
                        .build());
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        notificationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> DailyProductionAlarmScheduler.scheduleNext(MainActivity.this));
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

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
                    R.id.rankingFragment,
                    R.id.eventsFragment
            ).build();
            NavigationUI.setupWithNavController(binding.toolbar, navController, appBarConfiguration);
            // Menú inferior: solo NavigationUI aquí falla (restoreState, detalle colmena, etc.)
            binding.bottomNav.setOnItemSelectedListener(item -> {
                if (navController == null) {
                    return false;
                }
                int targetId = item.getItemId();
                if (!isBottomNavDestination(targetId)) {
                    return false;
                }
                NavDestination current = navController.getCurrentDestination();
                if (current != null && current.getId() == targetId) {
                    return true;
                }
                navigateToBottomNavTab(targetId);
                return true;
            });

            navController.addOnDestinationChangedListener((controller, destination, arguments) -> {
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

                // Sin barra superior en estas pantallas (más espacio; mercado sin título en toolbar)
                binding.toolbar.setVisibility(
                        onLogin || onProfileSetup || onDashboard || onHives || onApiaryYard || onHiveDetail || onMap || onMarket
                                || onAdminEvents || onAdminGrants || onShop
                                ? View.GONE : View.VISIBLE);
                binding.bottomNav.setVisibility(onLogin || onProfileSetup ? View.GONE : View.VISIBLE);

                // Sincronizar pestaña inferior (el listener custom no lo hace solo)
                if (!onLogin && !onProfileSetup) {
                    int tabId = (onHiveDetail || onApiaryYard) ? R.id.hivesFragment : destination.getId();
                    MenuItem tab = binding.bottomNav.getMenu().findItem(tabId);
                    if (tab != null && tab.isCheckable()) {
                        tab.setChecked(true);
                    }
                }
            });
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            DailyProductionAlarmScheduler.markSessionActive(this, true);
            maybeRequestNotificationPermission();
            Map<String, Object> profile = new HashMap<>();
            profile.put("timeZoneId", ZoneId.systemDefault().getId());
            FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(user.getUid())
                    .set(profile, SetOptions.merge());
            ApicultureApp app = (ApicultureApp) getApplication();
            app.getUserGameStateRepository().pullAndApplyThen(user.getUid(),
                    () -> app.getHiveRepository().tickDailyProductionForOwner(
                            user.getUid(),
                            result -> DailySummaryDialog.show(MainActivity.this, result)));
        } else {
            DailyProductionAlarmScheduler.markSessionActive(this, false);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
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

    private void logout() {
        DailyProductionAlarmScheduler.markSessionActive(this, false);
        ((ApicultureApp) getApplication()).getAuthRepository().signOut();
        if (navController != null) {
            NavOptions opts = new NavOptions.Builder()
                    .setPopUpTo(R.id.nav_graph, true)
                    .build();
            navController.navigate(R.id.loginFragment, null, opts);
        }
    }
}
