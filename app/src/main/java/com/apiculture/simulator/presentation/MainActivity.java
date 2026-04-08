package com.apiculture.simulator.presentation;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavController;
import androidx.navigation.NavDestination;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.apiculture.simulator.ApicultureApp;
import com.apiculture.simulator.R;
import com.apiculture.simulator.data.repository.DailyTickSummary;
import com.apiculture.simulator.data.repository.HiveDayStartupSummary;
import com.apiculture.simulator.data.repository.TickAppliedDayResult;
import com.apiculture.simulator.databinding.ActivityMainBinding;
import com.apiculture.simulator.domain.game.GameCalendar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private static final String PREF_STARTUP_SIM_DAY = "startup_sim_summary_day_key";

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
                boolean onDashboard = destination.getId() == R.id.dashboardFragment;
                boolean onHives = destination.getId() == R.id.hivesFragment;
                boolean onHiveDetail = destination.getId() == R.id.hiveDetailFragment;
                boolean onMap = destination.getId() == R.id.mapFragment;
                boolean onMarket = destination.getId() == R.id.marketFragment;

                // Sin barra superior en estas pantallas (más espacio; mercado sin título en toolbar)
                binding.toolbar.setVisibility(
                        onLogin || onDashboard || onHives || onHiveDetail || onMap || onMarket
                                ? View.GONE : View.VISIBLE);
                binding.bottomNav.setVisibility(onLogin ? View.GONE : View.VISIBLE);

                // Sincronizar pestaña inferior (el listener custom no lo hace solo)
                if (!onLogin) {
                    int tabId = onHiveDetail ? R.id.hivesFragment : destination.getId();
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
            Map<String, Object> profile = new HashMap<>();
            profile.put("timeZoneId", ZoneId.systemDefault().getId());
            FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(user.getUid())
                    .set(profile, SetOptions.merge());
            ((ApicultureApp) getApplication()).getHiveRepository().tickDailyProductionForOwner(
                    user.getUid(), this::maybeShowStartupSimulationSummary);
        }
    }

    private void maybeShowStartupSimulationSummary(TickAppliedDayResult result) {
        if (isFinishing() || result == null || result.days.isEmpty()) {
            return;
        }
        int maxDay = result.maxDayKey();
        if (maxDay <= 0) {
            return;
        }
        SharedPreferences prefs = getPreferences(MODE_PRIVATE);
        if (prefs.getInt(PREF_STARTUP_SIM_DAY, 0) >= maxDay) {
            return;
        }
        prefs.edit().putInt(PREF_STARTUP_SIM_DAY, maxDay).apply();
        DateTimeFormatter df = DateTimeFormatter.ofPattern("dd/MM/yyyy").withLocale(new Locale("es", "ES"));
        String title;
        if (result.days.size() == 1) {
            LocalDate d = GameCalendar.fromDayKey(result.days.get(0).dayKey);
            title = getString(R.string.startup_sim_summary_title, d.format(df));
        } else {
            DailyTickSummary first = result.days.get(0);
            DailyTickSummary last = result.days.get(result.days.size() - 1);
            title = getString(R.string.startup_sim_summary_title_multi, result.days.size(),
                    GameCalendar.fromDayKey(first.dayKey).format(df),
                    GameCalendar.fromDayKey(last.dayKey).format(df));
        }
        NumberFormat nf = NumberFormat.getNumberInstance(new Locale("es", "ES"));
        nf.setMinimumFractionDigits(1);
        nf.setMaximumFractionDigits(2);
        NumberFormat nfVar = NumberFormat.getNumberInstance(new Locale("es", "ES"));
        nfVar.setMinimumFractionDigits(2);
        nfVar.setMaximumFractionDigits(2);
        NumberFormat intNf = NumberFormat.getNumberInstance(new Locale("es", "ES"));
        StringBuilder body = new StringBuilder();
        for (DailyTickSummary day : result.days) {
            body.append("\n══ ");
            body.append(GameCalendar.fromDayKey(day.dayKey).format(df));
            body.append(" ══\n\n");
            for (HiveDayStartupSummary s : day.summaries) {
                body.append("— ").append(s.hiveName).append("\n");
                body.append(getString(R.string.startup_sim_summary_line_miel, nf.format(s.honeyKg))).append('\n');
                String obreras = (s.workerNet >= 0 ? "+" : "") + intNf.format(s.workerNet);
                body.append(getString(R.string.startup_sim_summary_line_obreras, obreras)).append('\n');
                body.append(getString(R.string.startup_sim_summary_line_huevos, intNf.format(s.eggsLaid))).append('\n');
                String salud = formatSignedIntPercent(s.healthDelta);
                body.append(getString(R.string.startup_sim_summary_line_salud, salud)).append('\n');
                String varroa = formatSignedDoublePercent(s.varroaDelta, nfVar);
                body.append(getString(R.string.startup_sim_summary_line_varroa, varroa)).append('\n');
                if (s.swarmed) {
                    body.append(getString(R.string.startup_sim_summary_line_swarm, s.hiveName)).append("\n");
                }
                body.append('\n');
            }
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(title)
                .setMessage(body.toString().trim())
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private static String formatSignedIntPercent(int delta) {
        if (delta > 0) {
            return "+" + delta + " %";
        }
        if (delta < 0) {
            return "−" + Math.abs(delta) + " %";
        }
        return "0 %";
    }

    private static String formatSignedDoublePercent(double delta, NumberFormat nfVar) {
        if (delta > 0) {
            return "+" + nfVar.format(delta) + " %";
        }
        if (delta < 0) {
            return "−" + nfVar.format(Math.abs(delta)) + " %";
        }
        return "0 %";
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
        ((ApicultureApp) getApplication()).getAuthRepository().signOut();
        if (navController != null) {
            NavOptions opts = new NavOptions.Builder()
                    .setPopUpTo(R.id.nav_graph, true)
                    .build();
            navController.navigate(R.id.loginFragment, null, opts);
        }
    }
}
