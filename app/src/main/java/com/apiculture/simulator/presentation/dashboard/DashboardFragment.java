package com.apiculture.simulator.presentation.dashboard;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;

import com.apiculture.simulator.ApicultureApp;
import com.apiculture.simulator.R;
import com.apiculture.simulator.databinding.FragmentDashboardBinding;
import com.apiculture.simulator.databinding.IncludeGlobalEventCardBinding;
import com.apiculture.simulator.presentation.common.GameNotice;
import com.apiculture.simulator.presentation.hive.HiveSiteSummaryUi;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import com.apiculture.simulator.domain.parcel.FloraPlantingProgressRow;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class DashboardFragment extends Fragment {

    private FragmentDashboardBinding binding;
    private DashboardViewModel dashboardViewModel;
    private final Handler dashFloraHandler = new Handler(Looper.getMainLooper());
    private Runnable dashFloraTickRunnable;
    private List<FloraPlantingProgressRow> lastFloraPlantingRows = Collections.emptyList();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentDashboardBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (dashboardViewModel != null) {
            FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
            if (u != null) {
                dashboardViewModel.bindHivesForUser(u.getUid());
                dashboardViewModel.refreshFloraPlantings(u.getUid());
            }
            dashboardViewModel.refreshEconomyDisplay();
            dashboardViewModel.refreshSwarmRiskBannerNow();
            dashboardViewModel.refreshGameClock();
            dashboardViewModel.refreshInventoryDisplay();
        }
        startFloraPlantingTicker();
    }

    @Override
    public void onPause() {
        stopFloraPlantingTicker();
        super.onPause();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        dashboardViewModel = new ViewModelProvider(this).get(DashboardViewModel.class);
        DashboardViewModel viewModel = dashboardViewModel;
        FirebaseUser sessionUser = FirebaseAuth.getInstance().getCurrentUser();
        if (sessionUser != null) {
            viewModel.bindHivesForUser(sessionUser.getUid());
        }

        viewModel.statCoins().observe(getViewLifecycleOwner(), binding.tvStatCoins::setText);
        viewModel.statHoney().observe(getViewLifecycleOwner(), binding.tvStatHoney::setText);
        viewModel.statNectar().observe(getViewLifecycleOwner(), binding.tvStatNectar::setText);
        viewModel.queenInventoryCount().observe(getViewLifecycleOwner(), n -> {
            if (n != null) {
                binding.tvStatQueens.setText(String.valueOf(n));
            }
        });
        viewModel.treatInventoryCount().observe(getViewLifecycleOwner(), n -> {
            if (n != null) {
                binding.tvStatTreatments.setText(String.valueOf(n));
            }
        });
        viewModel.feedInventoryCount().observe(getViewLifecycleOwner(), n -> {
            if (n != null) {
                binding.tvStatFeed.setText(String.valueOf(n));
            }
        });
        viewModel.seasonText().observe(getViewLifecycleOwner(), binding.tvSeason::setText);
        viewModel.dayText().observe(getViewLifecycleOwner(), binding.tvDay::setText);
        viewModel.profileName().observe(getViewLifecycleOwner(), binding.tvProfileName::setText);
        viewModel.headerHoneyBrand().observe(getViewLifecycleOwner(), binding.tvHeaderBrand::setText);
        viewModel.profileSubtitle().observe(getViewLifecycleOwner(), binding.tvProfileSubtitle::setText);
        viewModel.xpLabel().observe(getViewLifecycleOwner(), binding.tvXpLabel::setText);
        viewModel.xpMax().observe(getViewLifecycleOwner(), max -> {
            if (max != null) {
                binding.progressXp.setMax(max);
            }
        });
        viewModel.xpCurrent().observe(getViewLifecycleOwner(), cur -> {
            if (cur != null) {
                binding.progressXp.setProgress(cur);
            }
        });
        viewModel.globalEventBanner().observe(getViewLifecycleOwner(), banner -> {
            if (binding == null) {
                return;
            }
            IncludeGlobalEventCardBinding event = binding.globalEvent;
            if (banner == null || !banner.visible) {
                event.getRoot().setVisibility(View.GONE);
                return;
            }
            event.getRoot().setVisibility(View.VISIBLE);
            event.tvEventTitle.setText(banner.title);
            if (banner.floraIconRes != 0) {
                event.ivEventFlora.setImageResource(banner.floraIconRes);
                event.ivEventFlora.setVisibility(View.VISIBLE);
                event.ivEventMedal.setVisibility(View.GONE);
            } else {
                event.ivEventFlora.setVisibility(View.GONE);
                event.ivEventMedal.setVisibility(View.VISIBLE);
            }
            boolean hasSub = banner.subtitle != null && !banner.subtitle.isEmpty();
            event.tvEventSubtitle.setVisibility(hasSub ? View.VISIBLE : View.GONE);
            event.tvEventSubtitle.setText(hasSub ? banner.subtitle : "");
            event.tvEventStatus.setText(banner.statusLabel);
            boolean hasKg = banner.myKgLabel != null && !banner.myKgLabel.isEmpty();
            event.tvEventMyKg.setVisibility(hasKg ? View.VISIBLE : View.GONE);
            event.tvEventMyKg.setText(hasKg ? banner.myKgLabel : "");
            event.llEventProgress.setVisibility(banner.showProgress ? View.VISIBLE : View.GONE);
            if (banner.showProgress) {
                event.progressEventDemand.setProgress(banner.progressPct);
                event.tvEventProgress.setText(banner.progressLabel);
                bindEventMilestones(banner.highestMilestone);
            }
            event.btnClaimEventReward.setVisibility(banner.canClaim ? View.VISIBLE : View.GONE);
            event.btnSellEventHoney.setVisibility(banner.canSellEventHoney ? View.VISIBLE : View.GONE);
            if (banner.canSellEventHoney) {
                String sellLabel = banner.sellButtonLabel;
                event.btnSellEventHoney.setText(sellLabel != null && !sellLabel.isEmpty()
                        ? sellLabel
                        : getString(R.string.dashboard_global_event_sell));
            }
        });
        viewModel.honeyCapBanner().observe(getViewLifecycleOwner(), honey -> {
            if (honey == null) {
                binding.cardHoneyCap.setVisibility(View.GONE);
                binding.llHoneyCapHives.removeAllViews();
            } else {
                binding.cardHoneyCap.setVisibility(View.VISIBLE);
                binding.tvHoneyCapTitle.setText(honey.title);
                binding.llHoneyCapHives.removeAllViews();
                LayoutInflater inflater = LayoutInflater.from(requireContext());
                NavController nav = Navigation.findNavController(requireActivity(), R.id.nav_host_fragment);
                for (DashboardViewModel.HoneyCapHiveRow row : honey.hiveRows) {
                    if (row == null || row.hiveId == null) {
                        continue;
                    }
                    MaterialButton b = (MaterialButton) inflater.inflate(R.layout.item_honey_cap_link,
                            binding.llHoneyCapHives, false);
                    b.setText(row.displayName != null ? row.displayName : "");
                    b.setContentDescription(row.displayName);
                    final String hid = row.hiveId;
                    b.setOnClickListener(v -> {
                        Bundle args = new Bundle();
                        args.putString("hiveId", hid);
                        nav.navigate(R.id.hiveDetailFragment, args);
                    });
                    binding.llHoneyCapHives.addView(b);
                }
            }
        });

        viewModel.swarmRiskBanner().observe(getViewLifecycleOwner(), banner -> {
            if (banner == null) {
                binding.cardSwarmRisk.setVisibility(View.GONE);
                binding.llSwarmRiskHives.removeAllViews();
            } else {
                binding.cardSwarmRisk.setVisibility(View.VISIBLE);
                binding.tvSwarmTitle.setText(banner.title);
                binding.llSwarmRiskHives.removeAllViews();
                LayoutInflater inflater = LayoutInflater.from(requireContext());
                NavController nav = Navigation.findNavController(requireActivity(), R.id.nav_host_fragment);
                for (DashboardViewModel.SwarmRiskHiveRow row : banner.hiveRows) {
                    if (row == null || row.hiveId == null) {
                        continue;
                    }
                    MaterialButton b = (MaterialButton) inflater.inflate(R.layout.item_swarm_risk_link,
                            binding.llSwarmRiskHives, false);
                    b.setText(row.displayName != null ? row.displayName : "");
                    final String hid = row.hiveId;
                    b.setOnClickListener(v -> {
                        Bundle args = new Bundle();
                        args.putString("hiveId", hid);
                        nav.navigate(R.id.hiveDetailFragment, args);
                    });
                    binding.llSwarmRiskHives.addView(b);
                }
            }
        });

        viewModel.queenDeathBanner().observe(getViewLifecycleOwner(), banner -> {
            if (banner == null) {
                binding.cardQueenDead.setVisibility(View.GONE);
                binding.llQueenDeadHives.removeAllViews();
            } else {
                binding.cardQueenDead.setVisibility(View.VISIBLE);
                binding.tvQueenDeadTitle.setText(banner.title);
                binding.llQueenDeadHives.removeAllViews();
                LayoutInflater inflater = LayoutInflater.from(requireContext());
                NavController nav = Navigation.findNavController(requireActivity(), R.id.nav_host_fragment);
                for (DashboardViewModel.QueenDeathHiveRow row : banner.hiveRows) {
                    if (row == null || row.hiveId == null) {
                        continue;
                    }
                    MaterialButton b = (MaterialButton) inflater.inflate(R.layout.item_swarm_risk_link,
                            binding.llQueenDeadHives, false);
                    b.setText(row.displayName != null ? row.displayName : "");
                    final String hid = row.hiveId;
                    b.setOnClickListener(v -> {
                        Bundle args = new Bundle();
                        args.putString("hiveId", hid);
                        nav.navigate(R.id.hiveDetailFragment, args);
                    });
                    binding.llQueenDeadHives.addView(b);
                }
            }
        });

        viewModel.floraPlantings().observe(getViewLifecycleOwner(), rows -> {
            lastFloraPlantingRows = rows != null ? rows : Collections.emptyList();
            inflateFloraPlantingRows(lastFloraPlantingRows);
            tickFloraPlantingRowsUi();
        });

        if (sessionUser != null && sessionUser.getDisplayName() != null
                && !sessionUser.getDisplayName().isEmpty()) {
            binding.tvProfileName.setText(sessionUser.getDisplayName());
        }

        NavController nav = Navigation.findNavController(view);
        binding.tileQuickHarvest.setOnClickListener(v -> {
            setQuickActionsEnabled(false);
            viewModel.harvestAllHives(result -> {
                if (binding == null || !isAdded()) {
                    return;
                }
                setQuickActionsEnabled(true);
                if (result.success) {
                    showHarvestSummaryDialog(result);
                } else {
                    GameNotice.show(requireContext(), result.message);
                }
            });
        });
        binding.tileQuickSell.setOnClickListener(v -> {
            setQuickActionsEnabled(false);
            viewModel.sellAllHoneyToMarket((ok, message) -> {
                if (binding == null || !isAdded()) {
                    return;
                }
                setQuickActionsEnabled(true);
                if (ok) {
                    GameNotice.showSuccess(requireContext(), message);
                } else {
                    GameNotice.show(requireContext(), message);
                }
            });
        });
        binding.btnInventoryQueens.setOnClickListener(v -> showQueensInventory(viewModel, nav));
        binding.btnInventoryTreatments.setOnClickListener(v -> showCountInventory(
                viewModel.treatInventoryCount().getValue(),
                R.string.inventory_treatments_title,
                R.string.inventory_treatments_count,
                nav));
        binding.btnInventoryFeed.setOnClickListener(v -> showCountInventory(
                viewModel.feedInventoryCount().getValue(),
                R.string.inventory_feed_title,
                R.string.inventory_feed_count,
                nav));
        binding.globalEvent.btnTreatEvent.setOnClickListener(v -> nav.navigate(R.id.eventsFragment));

        binding.btnResetGame.setOnClickListener(v -> confirmResetGame(viewModel));
        binding.globalEvent.btnSellEventHoney.setOnClickListener(v -> {
            binding.globalEvent.btnSellEventHoney.setEnabled(false);
            viewModel.sellEventFloraToMarket((ok, message) -> {
                if (binding == null || !isAdded()) {
                    return;
                }
                binding.globalEvent.btnSellEventHoney.setEnabled(true);
                if (ok) {
                    GameNotice.showSuccess(requireContext(), message);
                } else {
                    GameNotice.show(requireContext(), message);
                }
            });
        });
        binding.globalEvent.btnClaimEventReward.setOnClickListener(v -> {
            FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
            if (u == null) {
                GameNotice.show(requireContext(), R.string.dashboard_reset_error);
                return;
            }
            binding.globalEvent.btnClaimEventReward.setEnabled(false);
            viewModel.claimGlobalEventReward(u.getUid(), msg -> {
                if (binding == null || !isAdded()) {
                    return;
                }
                binding.globalEvent.btnClaimEventReward.setEnabled(true);
                if (msg == null) {
                    GameNotice.showSuccess(requireContext(), R.string.dashboard_global_event_claim_ok);
                } else {
                    GameNotice.show(requireContext(), msg);
                }
            });
        });

        binding.btnDashboardSettings.setOnClickListener(v -> {
            PopupMenu popup = new PopupMenu(requireContext(), v);
            popup.getMenuInflater().inflate(R.menu.menu_dashboard_settings, popup.getMenu());
            android.view.MenuItem adminMenu = popup.getMenu().findItem(R.id.action_admin);
            if (adminMenu != null) {
                adminMenu.setVisible(Boolean.TRUE.equals(viewModel.isAdmin().getValue()));
            }
            popup.setOnMenuItemClickListener(item -> {
                if (item.getItemId() == R.id.action_shop) {
                    nav.navigate(R.id.shopFragment);
                    return true;
                }
                if (item.getItemId() == R.id.action_admin_events) {
                    nav.navigate(R.id.adminEventsFragment);
                    return true;
                }
                if (item.getItemId() == R.id.action_admin_grants) {
                    nav.navigate(R.id.adminGrantsFragment);
                    return true;
                }
                if (item.getItemId() == R.id.action_reset_game) {
                    confirmResetGame(viewModel);
                    return true;
                }
                return false;
            });
            popup.show();
        });
    }

    private void showCountInventory(@Nullable Integer count, int titleRes, int messageRes,
            @NonNull NavController nav) {
        int n = count != null ? count : 0;
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(titleRes)
                .setMessage(getString(messageRes, n))
                .setNeutralButton(R.string.inventory_go_shop, (d, w) -> nav.navigate(R.id.shopFragment))
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private void showQueensInventory(@NonNull DashboardViewModel viewModel, @NonNull NavController nav) {
        List<Integer> queens = viewModel.queenQualities();
        MaterialAlertDialogBuilder b = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.inventory_queens_title)
                .setNeutralButton(R.string.inventory_go_shop, (d, w) -> nav.navigate(R.id.shopFragment))
                .setPositiveButton(android.R.string.ok, null);
        if (queens == null || queens.isEmpty()) {
            b.setMessage(R.string.inventory_queens_empty);
        } else {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < queens.size(); i++) {
                if (i > 0) {
                    sb.append('\n');
                }
                sb.append(getString(R.string.inventory_queen_row, i + 1, queens.get(i)));
            }
            b.setMessage(sb.toString());
        }
        b.show();
    }

    private void confirmResetGame(@NonNull DashboardViewModel viewModel) {
        FirebaseUser sessionUser = FirebaseAuth.getInstance().getCurrentUser();
        if (sessionUser == null) {
            GameNotice.show(requireContext(), R.string.dashboard_reset_error);
            return;
        }
        final String uid = sessionUser.getUid();
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.dashboard_reset_confirm_title)
                .setMessage(R.string.dashboard_reset_confirm_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    setResetControlsEnabled(false);
                    viewModel.resetGameToStarterState(uid, msg -> {
                        if (!isAdded() || binding == null) {
                            return;
                        }
                        setResetControlsEnabled(true);
                        if (msg == null) {
                            GameNotice.showSuccess(requireContext(), R.string.dashboard_reset_done);
                            ApicultureApp app = (ApicultureApp) requireActivity().getApplication();
                            app.getHiveRepository().startRealtimeCloudSync(uid);
                            app.getHexParcelRepository().startRealtimeCloudSync();
                            app.getUserGameStateRepository().pushImmediate(uid);
                            viewModel.bindHivesForUser(uid);
                        } else {
                            GameNotice.show(requireContext(), msg);
                        }
                    });
                })
                .show();
    }

    private void showHarvestSummaryDialog(@NonNull DashboardViewModel.HarvestAllResult result) {
        if (!isAdded()) {
            return;
        }
        Dialog dialog = new Dialog(requireContext());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_harvest_summary);
        dialog.setCancelable(true);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        TextView tvSub = dialog.findViewById(R.id.tv_harvest_summary_subtitle);
        TextView tvTotal = dialog.findViewById(R.id.tv_harvest_summary_total);
        LinearLayout rows = dialog.findViewById(R.id.ll_harvest_honey_rows);
        MaterialButton btnOk = dialog.findViewById(R.id.btn_harvest_summary_ok);

        tvSub.setText(getString(R.string.dashboard_harvest_summary_subtitle, result.hiveCount));
        tvTotal.setText(getString(R.string.dashboard_harvest_summary_total, result.totalKg));

        LayoutInflater inflater = getLayoutInflater();
        rows.removeAllViews();
        List<Map.Entry<String, Double>> entries = new ArrayList<>(result.kgByFlora.entrySet());
        for (int i = 0; i < entries.size(); i++) {
            Map.Entry<String, Double> e = entries.get(i);
            View row = inflater.inflate(R.layout.item_daily_honey_row, rows, false);
            ImageView iv = row.findViewById(R.id.iv_daily_honey_flora);
            TextView tvFlora = row.findViewById(R.id.tv_daily_honey_flora);
            TextView tvKg = row.findViewById(R.id.tv_daily_honey_kg);
            iv.setImageResource(HiveSiteSummaryUi.floraHoneyJarIcon(e.getKey()));
            tvFlora.setText(e.getKey());
            tvKg.setText(getString(R.string.dashboard_harvest_summary_kg, e.getValue()));
            rows.addView(row);
            if (i < entries.size() - 1) {
                View divider = new View(requireContext());
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, 1);
                divider.setLayoutParams(lp);
                divider.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.event_gold_stroke));
                rows.addView(divider);
            }
        }

        btnOk.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void setQuickActionsEnabled(boolean enabled) {
        if (binding == null) {
            return;
        }
        binding.tileQuickHarvest.setEnabled(enabled);
        binding.tileQuickSell.setEnabled(enabled);
        float alpha = enabled ? 1f : 0.55f;
        binding.tileQuickHarvest.setAlpha(alpha);
        binding.tileQuickSell.setAlpha(alpha);
    }

    private void setResetControlsEnabled(boolean enabled) {
        if (binding == null) {
            return;
        }
        binding.btnResetGame.setEnabled(enabled);
        binding.btnDashboardSettings.setEnabled(enabled);
    }

    private void startFloraPlantingTicker() {
        stopFloraPlantingTicker();
        dashFloraTickRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isAdded() || binding == null) {
                    return;
                }
                tickFloraPlantingRowsUi();
                dashFloraHandler.postDelayed(this, 1000L);
            }
        };
        dashFloraHandler.post(dashFloraTickRunnable);
    }

    private void stopFloraPlantingTicker() {
        if (dashFloraTickRunnable != null) {
            dashFloraHandler.removeCallbacks(dashFloraTickRunnable);
            dashFloraTickRunnable = null;
        }
    }

    private void inflateFloraPlantingRows(List<FloraPlantingProgressRow> rows) {
        if (binding == null) {
            return;
        }
        binding.llFloraPlantings.removeAllViews();
        if (rows == null || rows.isEmpty()) {
            binding.cardFloraPlantings.setVisibility(View.GONE);
            return;
        }
        binding.cardFloraPlantings.setVisibility(View.VISIBLE);
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (FloraPlantingProgressRow r : rows) {
            if (r == null) {
                continue;
            }
            View row = inflater.inflate(R.layout.item_dashboard_flora_planting,
                    binding.llFloraPlantings, false);
            row.setTag(r);
            TextView tvParcel = row.findViewById(R.id.tv_planting_parcel);
            TextView tvFlora = row.findViewById(R.id.tv_planting_flora);
            ImageView ivFlora = row.findViewById(R.id.iv_planting_flora);
            tvParcel.setText(r.parcelLabel);
            tvFlora.setText(r.floraKey);
            ivFlora.setImageResource(
                    com.apiculture.simulator.presentation.hive.HiveSiteSummaryUi.floraHoneyJarIcon(r.floraKey));
            binding.llFloraPlantings.addView(row);
        }
    }

    private void tickFloraPlantingRowsUi() {
        if (binding == null) {
            return;
        }
        long now = System.currentTimeMillis();
        for (int i = 0; i < binding.llFloraPlantings.getChildCount(); i++) {
            View row = binding.llFloraPlantings.getChildAt(i);
            Object tag = row.getTag();
            if (!(tag instanceof FloraPlantingProgressRow)) {
                continue;
            }
            FloraPlantingProgressRow r = (FloraPlantingProgressRow) tag;
            TextView cd = row.findViewById(R.id.tv_planting_countdown);
            ProgressBar bar = row.findViewById(R.id.bar_planting_progress);
            long rem = Math.max(0L, r.readyAtEpochMs - now);
            long total = Math.max(1L, r.readyAtEpochMs - r.plantedAtEpochMs);
            long elapsed = Math.min(total, Math.max(0L, now - r.plantedAtEpochMs));
            int prog = (int) Math.min(1000L, (1000L * elapsed) / total);
            bar.setProgress(prog);
            long hours = rem / 3_600_000L;
            long mins = (rem % 3_600_000L) / 60_000L;
            long secs = (rem % 60_000L) / 1000L;
            cd.setText(String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, mins, secs));
        }
    }

    private void bindEventMilestones(int highest) {
        IncludeGlobalEventCardBinding event = binding.globalEvent;
        bindEventMilestone(event.ivEventMs15, event.llEventMs15, 15, highest);
        bindEventMilestone(event.ivEventMs30, event.llEventMs30, 30, highest);
        bindEventMilestone(event.ivEventMs50, event.llEventMs50, 50, highest);
        bindEventMilestone(event.ivEventMs75, event.llEventMs75, 75, highest);
        bindEventMilestone(event.ivEventMs100, event.llEventMs100, 100, highest);
    }

    private void bindEventMilestone(ImageView hex, View reward, int threshold, int highest) {
        boolean on = highest >= threshold;
        hex.setImageResource(on ? R.drawable.ic_event_ms_on : R.drawable.ic_event_ms_off);
    }
}
