package com.apiculture.simulator.presentation.workshop;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.apiculture.simulator.ApicultureApp;
import com.apiculture.simulator.R;
import com.apiculture.simulator.data.local.AppDatabase;
import com.apiculture.simulator.data.local.entity.HexParcelOwnershipEntity;
import com.apiculture.simulator.data.repository.EconomyRepository;
import com.apiculture.simulator.data.repository.HoneyLogistics;
import com.apiculture.simulator.data.repository.WorkshopStore;
import com.apiculture.simulator.data.session.PlayerAuth;
import com.apiculture.simulator.databinding.FragmentWorkshopBinding;
import com.apiculture.simulator.domain.workshop.WorkshopRules;
import com.apiculture.simulator.domain.workshop.WorkshopRules.Format;
import com.apiculture.simulator.domain.workshop.WorkshopRules.Machine;
import com.apiculture.simulator.domain.workshop.WorkshopState;
import com.apiculture.simulator.presentation.common.GameNotice;
import com.apiculture.simulator.presentation.hive.HiveSiteSummaryUi;
import com.apiculture.simulator.presentation.market.WorkshopFormatUi;
import com.apiculture.simulator.presentation.tutorial.TutorialBus;
import com.apiculture.simulator.presentation.tutorial.TutorialEvent;
import com.apiculture.simulator.unity.Apiary3DActivity;
import com.apiculture.simulator.unity.UnityBridge;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.slider.Slider;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Pantalla del obrador: construirlo junto a un almacén, comprar y mejorar máquinas, seguir las
 * tandas, elegir envase y vender tarros y cera. Las escrituras van al servidor, fuera del hilo
 * principal.
 */
public class WorkshopFragment extends Fragment {

    private static final long TICK_MS = 1000L;
    private static final long SERVER_REFRESH_MS = 30_000L;

    private FragmentWorkshopBinding binding;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    /** Cuenta atrás de cada tanda en máquina, para refrescar sin redibujar. */
    private final List<Countdown> countdowns = new ArrayList<>();
    /** hexId → nombre de los almacenes del jugador. */
    private final Map<String, String> warehouses = new LinkedHashMap<>();
    @Nullable
    private WorkshopState state;
    /** Obrador (almacén) que se está viendo; null hasta saber cuál. */
    @Nullable
    private String hexId;
    private long nextChangeAt = Long.MAX_VALUE;
    private long lastServerRefresh;
    private boolean busy;

    private static final class Countdown {
        final TextView label;
        final ProgressBar bar;
        final String machine;
        final long startAt;
        final long endAt;

        Countdown(TextView label, ProgressBar bar, String machine, long startAt, long endAt) {
            this.label = label;
            this.bar = bar;
            this.machine = machine;
            this.startAt = startAt;
            this.endAt = endAt;
        }
    }

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (binding == null) {
                return;
            }
            long now = System.currentTimeMillis();
            if (now - lastServerRefresh >= SERVER_REFRESH_MS) {
                refreshFromServer();
            } else if (now >= nextChangeAt) {
                renderLocal();
            } else {
                updateCountdowns(now);
            }
            main.postDelayed(this, TICK_MS);
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        binding = FragmentWorkshopBinding.inflate(inflater, container, false);
        binding.btnWorkshopBack.setOnClickListener(v ->
                NavHostFragment.findNavController(this).popBackStack());
        binding.tvWorkshopBuildIntro.setText(getString(R.string.workshop_build_intro,
                WorkshopRules.BUILDING_COST_B));
        binding.btnWorkshop3d.setOnClickListener(v -> open3d());
        hexId = getArguments() != null ? getArguments().getString(WorkshopUi.ARG_HEX) : null;
        binding.tvWorkshopWhere.setOnClickListener(v -> pickObrador());
        return binding.getRoot();
    }

    /** Escena 3D del obrador, con la operaria. Comparte la actividad de Unity con el apiario. */
    private void open3d() {
        String owner = uid();
        if (owner == null || owner.isEmpty() || state == null || !state.built()) {
            return;
        }
        String where = warehouses.get(state.hexId);
        UnityBridge.prepareWorkshop(requireContext(), owner,
                where != null ? where : getString(R.string.workshop_name), state.hexId);
        Apiary3DActivity.open(requireContext());
    }

    @Override
    public void onResume() {
        super.onResume();
        renderLocal();
        refreshFromServer();
        main.removeCallbacks(tick);
        main.postDelayed(tick, TICK_MS);
    }

    @Override
    public void onPause() {
        super.onPause();
        main.removeCallbacks(tick);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        main.removeCallbacksAndMessages(null);
        countdowns.clear();
        binding = null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        io.shutdown();
    }

    @Nullable
    private String uid() {
        return PlayerAuth.getInstance().getUid();
    }

    @NonNull
    private EconomyRepository economy() {
        return ((ApicultureApp) requireActivity().getApplication()).getEconomyRepository();
    }

    // ---------------------------------------------------------------- carga

    private void renderLocal() {
        if (binding == null) {
            return;
        }
        if (hexId == null) {
            hexId = WorkshopStore.firstHex(requireContext(), uid());
        }
        state = hexId == null ? new WorkshopState() : WorkshopStore.get(requireContext(), uid(), hexId);
        render();
    }

    private void refreshFromServer() {
        lastServerRefresh = System.currentTimeMillis();
        if (binding == null || io.isShutdown()) {
            return;
        }
        Context app = requireContext().getApplicationContext();
        String owner = uid();
        EconomyRepository economy = economy();
        if (state == null || !state.built()) {
            binding.pbWorkshopLoading.setVisibility(View.VISIBLE);
        }
        io.execute(() -> {
            WorkshopStore.refresh(app, owner, economy);
            String hex = hexId != null ? hexId : WorkshopStore.firstHex(app, owner);
            WorkshopState s = hex == null ? new WorkshopState() : WorkshopStore.get(app, owner, hex);
            Map<String, String> sites = loadWarehouses(app, owner);
            main.post(() -> {
                if (binding == null) {
                    return;
                }
                binding.pbWorkshopLoading.setVisibility(View.GONE);
                warehouses.clear();
                warehouses.putAll(sites);
                if (hexId == null) {
                    hexId = hex;
                }
                state = s;
                render();
            });
        });
    }

    @NonNull
    private static Map<String, String> loadWarehouses(@NonNull Context app, @Nullable String owner) {
        Map<String, String> out = new LinkedHashMap<>();
        if (owner == null || owner.isEmpty()) {
            return out;
        }
        List<HexParcelOwnershipEntity> rows =
                AppDatabase.getInstance(app).hexParcelOwnershipDao().getWarehousesForOwnerSync(owner);
        if (rows == null) {
            return out;
        }
        for (HexParcelOwnershipEntity row : rows) {
            if (row == null || !row.hasWarehouse || row.hexId == null || out.containsKey(row.hexId)) {
                continue;
            }
            String name = row.parcelName != null && !row.parcelName.trim().isEmpty()
                    ? row.parcelName.trim() : app.getString(R.string.map_warehouse_title);
            out.put(row.hexId, name);
        }
        return out;
    }

    /** Lanza una escritura fuera del hilo principal; {@code work} devuelve un error o null. */
    private void runAction(@NonNull Action work, @Nullable String okMessage) {
        if (busy || binding == null || io.isShutdown()) {
            return;
        }
        busy = true;
        Context app = requireContext().getApplicationContext();
        String owner = uid();
        EconomyRepository economy = economy();
        io.execute(() -> {
            String error;
            try {
                error = work.run(app, owner, economy);
            } catch (RuntimeException e) {
                error = getStringSafe(app, R.string.workshop_sell_failed);
            }
            WorkshopStore.flushBulk(app, owner, economy);
            String hex = hexId;
            WorkshopState s = hex == null ? new WorkshopState() : WorkshopStore.get(app, owner, hex);
            String result = error;
            main.post(() -> {
                busy = false;
                if (binding == null) {
                    return;
                }
                state = s;
                render();
                if (result != null) {
                    GameNotice.show(requireContext(), result);
                } else if (okMessage != null) {
                    GameNotice.showSuccess(requireContext(), okMessage);
                }
            });
        });
    }

    @NonNull
    private static String getStringSafe(@NonNull Context c, int res) {
        return c.getString(res);
    }

    private interface Action {
        @Nullable
        String run(@NonNull Context app, @Nullable String owner, @NonNull EconomyRepository economy);
    }

    // ---------------------------------------------------------------- dibujo

    private void render() {
        if (binding == null || state == null) {
            return;
        }
        binding.tvWorkshopBalance.setText(getString(R.string.shop_balance, economy().getBalance()));
        countdowns.clear();
        nextChangeAt = Long.MAX_VALUE;
        boolean built = state.built();
        binding.btnWorkshop3d.setVisibility(built ? View.VISIBLE : View.GONE);
        binding.llWorkshopBuild.setVisibility(built ? View.GONE : View.VISIBLE);
        binding.llWorkshopMain.setVisibility(built ? View.VISIBLE : View.GONE);
        if (!built) {
            binding.tvWorkshopWhere.setVisibility(View.GONE);
            renderBuild();
            return;
        }
        String where = warehouses.get(state.hexId);
        binding.tvWorkshopWhere.setVisibility(where != null ? View.VISIBLE : View.GONE);
        if (where != null) {
            String text = getString(R.string.workshop_built_at, where);
            binding.tvWorkshopWhere.setText(warehouses.size() > 1
                    ? text + " · " + getString(R.string.workshop_change_obrador) : text);
        }
        renderBatches();
        renderStock();
        renderWax();
        renderMachines();
        // Capítulo 9, viñetas 3 y 4, y apertura del capítulo 10.
        TutorialBus.emit(TutorialEvent.WORKSHOP_BUILT);
        if (state.complete()) {
            TutorialBus.emit(TutorialEvent.WORKSHOP_READY);
        }
        if (!state.batches.isEmpty()) {
            TutorialBus.emit(TutorialEvent.WORKSHOP_BATCH);
        }
    }

    /** Sin almacén no hay obrador: se manda al mapa a comprar uno. */
    private void renderBuild() {
        LinearLayout box = binding.llWorkshopBuildSites;
        box.removeAllViews();
        binding.tvWorkshopBuildIntro.setVisibility(View.GONE);
        if (binding.pbWorkshopLoading.getVisibility() == View.VISIBLE) {
            return;
        }
        box.addView(text(getString(R.string.workshop_build_no_warehouse), 14, false, R.color.event_ink));
        MaterialButton go = button(getString(R.string.workshop_go_map), false);
        go.setOnClickListener(v -> NavHostFragment.findNavController(this).navigate(R.id.mapFragment));
        box.addView(go);
    }

    /** Con varios almacenes, cada uno con su obrador, se elige cuál ver. */
    private void pickObrador() {
        if (warehouses.size() < 2 || binding == null) {
            return;
        }
        List<String> hexes = new ArrayList<>(warehouses.keySet());
        String[] names = new String[hexes.size()];
        for (int i = 0; i < names.length; i++) {
            names[i] = warehouses.get(hexes.get(i));
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.workshop_change_obrador)
                .setItems(names, (d, which) -> {
                    hexId = hexes.get(which);
                    renderLocal();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void renderBatches() {
        LinearLayout box = binding.llWorkshopBatches;
        box.removeAllViews();
        List<WorkshopState.Batch> list = new ArrayList<>(state.batches);
        list.sort((a, b) -> Long.compare(a.createdAt, b.createdAt));
        if (list.isEmpty()) {
            box.addView(text(getString(R.string.workshop_batches_empty), 14, false, R.color.event_ink_muted));
            return;
        }
        long now = System.currentTimeMillis();
        for (WorkshopState.Batch b : list) {
            LinearLayout body = card(box);
            body.addView(text(getString(R.string.workshop_batch_title,
                    HiveSiteSummaryUi.floraLabel(requireContext(), b.flora),
                    EconomyRepository.formatKg(b.kg)), 16, true, R.color.event_ink));
            if (b.source != null && !b.source.isEmpty()) {
                body.addView(text(getString(R.string.workshop_batch_from, b.source), 13, false,
                        R.color.event_ink_muted));
            }
            String machine = WorkshopUi.machineName(requireContext(), b.stage);
            if (b.inMachine) {
                TextView status = text("", 14, true, R.color.event_gold_dark);
                ProgressBar bar = new ProgressBar(requireContext(), null,
                        android.R.attr.progressBarStyleHorizontal);
                bar.setMax(1000);
                bar.setProgressTintList(ColorStateList.valueOf(
                        ContextCompat.getColor(requireContext(), R.color.event_gold)));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(10));
                lp.topMargin = dp(6);
                body.addView(status);
                body.addView(bar, lp);
                Countdown c = new Countdown(status, bar, machine, b.startAt, b.endAt);
                countdowns.add(c);
                nextChangeAt = Math.min(nextChangeAt, b.endAt);
                updateCountdown(c, now);
            } else if (b.waitingFormat()) {
                body.addView(text(getString(R.string.workshop_batch_need_format), 14, true, R.color.dash_warning));
            } else if (state.level(b.stage) <= 0) {
                body.addView(text(getString(R.string.workshop_batch_no_machine, machine), 14, true,
                        R.color.dash_warning));
            } else {
                body.addView(text(getString(R.string.workshop_batch_queued, machine), 14, false,
                        R.color.event_ink));
                if (b.waitingSince > now) {
                    nextChangeAt = Math.min(nextChangeAt, b.waitingSince);
                }
            }
            boolean packing = b.stage == Machine.PACKER && b.inMachine;
            if (b.format != null) {
                body.addView(text(getString(R.string.workshop_batch_format,
                        b.mix != null ? WorkshopFormatUi.splitLabel(requireContext(), b.mix, b.kg) : WorkshopFormatUi.label(requireContext(), b.format)), 13, false, R.color.event_ink));
            }
            if (!packing) {
                MaterialButton choose = button(getString(b.format == null
                        ? R.string.workshop_batch_choose : R.string.workshop_batch_change), b.waitingFormat());
                choose.setOnClickListener(v -> showFormatDialog(b));
                body.addView(choose);
            }
        }
    }

    private void renderStock() {
        LinearLayout box = binding.llWorkshopStock;
        box.removeAllViews();
        boolean any = false;
        for (WorkshopState.Packed p : state.packed) {
            if (p.format == Format.BULK || p.jars <= 0) {
                continue;
            }
            any = true;
            LinearLayout body = card(box);
            String label = WorkshopFormatUi.label(requireContext(), p.format);
            String flora = HiveSiteSummaryUi.floraLabel(requireContext(), p.flora);
            body.addView(text(getString(R.string.workshop_stock_row, p.jars, label, flora), 15, true,
                    R.color.event_ink));
            MaterialButton sell = button(getString(R.string.workshop_sell_jars), true);
            String floraKey = p.flora;
            Format format = p.format;
            int jars = p.jars;
            sell.setOnClickListener(v -> showSellDialog(floraKey, format, jars));
            body.addView(sell);
        }
        if (!any) {
            box.addView(text(getString(R.string.workshop_stock_empty), 14, false, R.color.event_ink_muted));
        }
    }

    private void renderWax() {
        LinearLayout box = binding.llWorkshopWax;
        box.removeAllViews();
        LinearLayout body = card(box);
        body.addView(text(getString(R.string.workshop_wax_kg, state.waxKg, WorkshopRules.WAX_PRICE_B_PER_KG),
                15, true, R.color.event_ink));
        MaterialButton sell = button(getString(R.string.workshop_sell_wax), false);
        boolean can = state.waxKg >= 0.01;
        sell.setEnabled(can);
        sell.setAlpha(can ? 1f : 0.45f);
        sell.setOnClickListener(v -> runAction((app, owner, economy) ->
                WorkshopStore.sellWax(app, economy, owner, hexId), getString(R.string.workshop_wax_sold)));
        body.addView(sell);
    }

    private void renderMachines() {
        LinearLayout box = binding.llWorkshopMachines;
        box.removeAllViews();
        for (Machine m : Machine.values()) {
            int level = state.level(m);
            LinearLayout body = card(box);
            body.addView(text(WorkshopUi.machineName(requireContext(), m), 16, true, R.color.event_ink));
            body.addView(text(level <= 0 ? getString(R.string.workshop_machine_missing)
                    : getString(R.string.workshop_machine_level, level,
                    WorkshopRules.upgradable(m) ? WorkshopRules.MAX_LEVEL : 1),
                    13, level > 0, level > 0 ? R.color.event_gold_dark : R.color.dash_warning));
            body.addView(text(machineInfo(requireContext(), m, Math.max(1, level)), 13, false, R.color.event_ink_muted));
            if (level <= 0) {
                MaterialButton buy = button(getString(R.string.workshop_machine_buy, WorkshopRules.buyCostB(m)), true);
                buy.setOnClickListener(v -> runAction((app, owner, economy) ->
                        WorkshopStore.buyOrUpgrade(app, economy, owner, hexId, m),
                        getString(R.string.workshop_machine_bought)));
                body.addView(buy);
            } else if (WorkshopRules.upgradable(m)) {
                if (level >= WorkshopRules.MAX_LEVEL) {
                    MaterialButton max = button(getString(R.string.workshop_machine_max), false);
                    max.setEnabled(false);
                    max.setAlpha(0.45f);
                    body.addView(max);
                } else {
                    MaterialButton up = button(getString(R.string.workshop_machine_upgrade, level + 1,
                            WorkshopRules.upgradeCostB(m, level)), false);
                    up.setOnClickListener(v -> runAction((app, owner, economy) ->
                            WorkshopStore.buyOrUpgrade(app, economy, owner, hexId, m),
                            getString(R.string.workshop_machine_bought)));
                    body.addView(up);
                }
            }
        }
    }

    @NonNull
    public static String machineInfo(@NonNull android.content.Context c, @NonNull Machine m, int level) {
        switch (m) {
            case RECEPTION:
                return c.getString(R.string.workshop_machine_info_reception,
                        duration(WorkshopRules.durationMs(m, level, 0, null)));
            case MATURER:
                return c.getString(R.string.workshop_machine_info_maturer, WorkshopRules.slots(m, level),
                        duration(WorkshopRules.durationMs(m, level, 0, null)));
            case PACKER:
                return c.getString(R.string.workshop_machine_info_packer,
                        (int) Math.round(WorkshopRules.capacityKg(m, level)));
            default:
                double cap = WorkshopRules.capacityKg(m, level);
                return c.getString(R.string.workshop_machine_info_time,
                        duration(WorkshopRules.durationMs(m, level, cap, null)), (int) Math.round(cap));
        }
    }

    /** "2 h", "45 min", "1 h 30 min". */
    @NonNull
    static String duration(long ms) {
        long min = Math.max(0L, Math.round(ms / 60_000.0));
        long h = min / 60;
        long m = min % 60;
        if (h > 0 && m > 0) {
            return h + " h " + m + " min";
        }
        return h > 0 ? h + " h" : m + " min";
    }

    private void updateCountdowns(long now) {
        for (Countdown c : countdowns) {
            updateCountdown(c, now);
        }
    }

    private void updateCountdown(@NonNull Countdown c, long now) {
        c.label.setText(getString(R.string.workshop_batch_running, c.machine,
                HoneyLogistics.formatCountdown(c.endAt - now)));
        long span = Math.max(1L, c.endAt - c.startAt);
        double f = Math.max(0.0, Math.min(1.0, (now - c.startAt) / (double) span));
        c.bar.setProgress((int) Math.round(f * 1000));
    }

    // ---------------------------------------------------------------- diálogos

    private void showFormatDialog(@NonNull WorkshopState.Batch b) {
        Format[] formats = Format.values();
        String[] items = new String[formats.length];
        for (int i = 0; i < formats.length; i++) {
            Format f = formats[i];
            String label = capitalize(WorkshopFormatUi.label(requireContext(), f));
            items[i] = f == Format.BULK
                    ? getString(R.string.workshop_format_option_bulk, label, f.priceFactor)
                    : getString(R.string.workshop_format_option_jars, label,
                    WorkshopRules.jars(b.kg, f), f.priceFactor);
        }
        String batchId = b.id;
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.workshop_format_title, EconomyRepository.formatKg(b.kg),
                        HiveSiteSummaryUi.floraLabel(requireContext(), b.flora)))
                .setItems(items, (d, which) -> {
                    Format f = formats[which];
                    runAction((app, owner, economy) -> {
                        String error = WorkshopStore.chooseFormat(app, owner, batchId, f);
                        if (error == null) {
                            // Capítulo 10, viñeta 2. Envase elegido.
                            TutorialBus.emit(TutorialEvent.WORKSHOP_FORMAT);
                        }
                        return error;
                    }, null);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showSellDialog(@NonNull String floraKey, @NonNull Format format, int maxJars) {
        if (maxJars <= 0) {
            return;
        }
        LinearLayout content = new LinearLayout(requireContext());
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(24), dp(8), dp(24), 0);
        content.addView(text(getString(R.string.workshop_sell_hint), 13, false, R.color.event_ink_muted));
        TextView amount = text(getString(R.string.workshop_sell_amount, maxJars, maxJars), 16, true,
                R.color.event_ink);
        content.addView(amount);
        int[] chosen = {maxJars};
        if (maxJars > 1) {
            Slider slider = new Slider(requireContext());
            slider.setValueFrom(1f);
            slider.setValueTo(maxJars);
            slider.setStepSize(1f);
            slider.setValue(maxJars);
            slider.addOnChangeListener((s, value, fromUser) -> {
                chosen[0] = Math.round(value);
                amount.setText(getString(R.string.workshop_sell_amount, chosen[0], maxJars));
            });
            content.addView(slider);
        }
        String label = WorkshopFormatUi.label(requireContext(), format);
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.workshop_sell_title, label,
                        HiveSiteSummaryUi.floraLabel(requireContext(), floraKey)))
                .setView(content)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.workshop_sell_confirm, (d, w) -> sellJars(floraKey, format, chosen[0]))
                .show();
    }

    private void sellJars(@NonNull String floraKey, @NonNull Format format, int jars) {
        if (busy) {
            return;
        }
        busy = true;
        ApicultureApp app = (ApicultureApp) requireActivity().getApplication();
        HoneyLogistics.dispatchJarSale(requireContext(), uid(), hexId, floraKey, format, jars,
                app.getEconomyRepository(), app.getMarketRepository(), r -> {
                    busy = false;
                    if (!isAdded() || binding == null) {
                        return;
                    }
                    renderLocal();
                    if (r == HoneyLogistics.Result.STARTED) {
                        GameNotice.showSuccess(requireContext(), getString(R.string.workshop_sell_started, jars));
                    } else if (r == HoneyLogistics.Result.INSTANT) {
                        GameNotice.showSuccess(requireContext(), getString(R.string.workshop_sell_instant, jars));
                    } else if (r == HoneyLogistics.Result.NO_DEMAND) {
                        GameNotice.show(requireContext(), R.string.workshop_sell_no_demand);
                    } else if (r == HoneyLogistics.Result.NO_CASH) {
                        GameNotice.show(requireContext(), R.string.workshop_sell_no_cash);
                    } else if (r == HoneyLogistics.Result.NO_FLEET) {
                        GameNotice.show(requireContext(), R.string.workshop_sell_no_truck);
                    } else {
                        GameNotice.show(requireContext(), R.string.workshop_sell_failed);
                    }
                });
    }

    // ---------------------------------------------------------------- vistas

    @NonNull
    private LinearLayout card(@NonNull LinearLayout parent) {
        MaterialCardView card = new MaterialCardView(requireContext());
        card.setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.event_cream));
        card.setRadius(dp(16));
        card.setCardElevation(0f);
        card.setStrokeColor(ContextCompat.getColor(requireContext(), R.color.event_gold_stroke));
        card.setStrokeWidth(dp(1));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(8);
        parent.addView(card, lp);
        LinearLayout body = new LinearLayout(requireContext());
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.addView(body);
        return body;
    }

    @NonNull
    private TextView text(@NonNull String s, int sp, boolean bold, int colorRes) {
        TextView t = new TextView(requireContext());
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(ContextCompat.getColor(requireContext(), colorRes));
        if (bold) {
            t.setTypeface(t.getTypeface(), Typeface.BOLD);
        }
        t.setPadding(0, dp(2), 0, dp(2));
        return t;
    }

    @NonNull
    private MaterialButton button(@NonNull String label, boolean primary) {
        MaterialButton b = new MaterialButton(requireContext());
        b.setText(label);
        b.setAllCaps(false);
        b.setTypeface(b.getTypeface(), Typeface.BOLD);
        b.setTextColor(ContextCompat.getColor(requireContext(), R.color.event_ink));
        b.setCornerRadius(dp(14));
        b.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(requireContext(),
                primary ? R.color.event_gold : R.color.event_cream)));
        if (!primary) {
            b.setStrokeColor(ColorStateList.valueOf(ContextCompat.getColor(requireContext(),
                    R.color.event_gold_stroke)));
            b.setStrokeWidth(dp(1));
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        lp.topMargin = dp(8);
        b.setLayoutParams(lp);
        return b;
    }

    @NonNull
    private static String capitalize(@NonNull String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
