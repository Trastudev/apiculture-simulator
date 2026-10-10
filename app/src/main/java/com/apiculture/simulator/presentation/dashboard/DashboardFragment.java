package com.apiculture.simulator.presentation.dashboard;

import android.app.Dialog;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;

import com.apiculture.simulator.ApicultureApp;
import com.apiculture.simulator.presentation.MainActivity;
import com.apiculture.simulator.R;
import com.apiculture.simulator.data.local.entity.HiveEntity;
import com.apiculture.simulator.data.local.entity.CargoTripEntity;
import com.apiculture.simulator.data.local.entity.TruckTripEntity;
import com.apiculture.simulator.data.repository.AccountErasure;
import com.apiculture.simulator.data.repository.GameLocale;
import com.apiculture.simulator.data.repository.LegalLinks;
import com.apiculture.simulator.data.repository.Mailbox;
import com.apiculture.simulator.data.repository.HoneyLogistics;
import com.apiculture.simulator.data.repository.ProfileRepository;
import com.apiculture.simulator.data.repository.TruckLiveTrips;
import com.apiculture.simulator.databinding.DialogEditProfileBinding;
import com.apiculture.simulator.databinding.FragmentDashboardBinding;
import com.apiculture.simulator.domain.game.CargoTripRules;
import com.apiculture.simulator.domain.map.SeaportCatalog;
import com.apiculture.simulator.domain.game.FleetRules;
import com.apiculture.simulator.domain.game.NpcContractCatalog;
import com.apiculture.simulator.domain.game.TranshumanceRules;
import com.apiculture.simulator.domain.game.TruckTripRules;
import com.apiculture.simulator.databinding.IncludeGlobalEventCardBinding;
import com.apiculture.simulator.presentation.common.GameNotice;
import com.apiculture.simulator.presentation.common.ProfileLoadingCover;
import com.apiculture.simulator.domain.map.PlayableMapRegion;
import com.apiculture.simulator.presentation.hive.HarvestCollectDialogs;
import com.apiculture.simulator.presentation.hive.WarehouseDialogs;
import com.apiculture.simulator.presentation.profile.ProfilePhoto;
import com.apiculture.simulator.presentation.common.TripCargoUi;
import com.apiculture.simulator.presentation.hive.HiveSiteSummaryUi;
import com.apiculture.simulator.presentation.market.MarketPickerDialogs;
import com.apiculture.simulator.presentation.market.NpcPortraitUi;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.apiculture.simulator.data.session.PlayerAuth;
import com.apiculture.simulator.data.session.SignedInUser;

import com.apiculture.simulator.domain.parcel.FloraPlantingProgressRow;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Locale;
import java.util.Map;

public class DashboardFragment extends Fragment {

    private FragmentDashboardBinding binding;
    private final Runnable showProfileCover = () -> {
        if (isAdded()) {
            ProfileLoadingCover.show(getActivity());
        }
    };
    private DashboardViewModel dashboardViewModel;
    private final Handler dashFloraHandler = new Handler(Looper.getMainLooper());
    private Runnable dashFloraTickRunnable;
    private List<FloraPlantingProgressRow> lastFloraPlantingRows = Collections.emptyList();
    private List<TruckTripEntity> lastHiveTrips = Collections.emptyList();
    private List<CargoTripEntity> lastCargoTrips = Collections.emptyList();
    private Runnable tripTickRunnable;
    private final ExecutorService tripUpkeep = Executors.newSingleThreadExecutor();
    private final AtomicBoolean tripUpkeepRunning = new AtomicBoolean(false);
    @Nullable
    private DialogEditProfileBinding editProfileForm;
    @Nullable
    private Bitmap pendingProfilePhoto;

    private final ActivityResultLauncher<String> pickProfilePhoto =
            registerForActivityResult(new ActivityResultContracts.GetContent(), this::onProfilePhotoPicked);

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
            SignedInUser u = PlayerAuth.getInstance().getCurrentUser();
            if (u != null) {
                dashboardViewModel.bindHivesForUser(u.getUid());
                dashboardViewModel.refreshFloraPlantings(u.getUid());
            }
            dashboardViewModel.refreshEconomyDisplay();
            dashboardViewModel.refreshSwarmRiskBannerNow();
            dashboardViewModel.refreshGameClock();
            dashboardViewModel.refreshInventoryDisplay();
            refreshWeekChart(u != null ? u.getUid() : "");
            refreshMailBadge();
        }
        startFloraPlantingTicker();
        startTripTicker();
        refreshWorkshopCard();
    }

    private long workshopCardAt;

    /** Aviso del obrador en el inicio: construirlo, elegir envase, máquina que falta o algo que vender. */
    private void refreshWorkshopCard() {
        if (binding == null || !isAdded()) {
            return;
        }
        workshopCardAt = System.currentTimeMillis();
        String uid = PlayerAuth.getInstance().getUid();
        android.content.Context c = requireContext();
        java.util.List<com.apiculture.simulator.domain.workshop.WorkshopState> all =
                com.apiculture.simulator.data.repository.WorkshopStore.all(c, uid);
        java.util.List<String> alerts = new java.util.ArrayList<>();
        java.util.List<String> info = new java.util.ArrayList<>();
        if (all.isEmpty()) {
            if (com.apiculture.simulator.presentation.workshop.WorkshopUi.blocksHarvest(c, uid)) {
                alerts.add(getString(R.string.workshop_dash_build));
            }
        } else {
            // Todos los obradores juntos: tandas, tarros y cera.
            int waitingFormat = 0;
            String missing = null;
            int batchCount = 0;
            double wax = 0;
            java.util.List<com.apiculture.simulator.domain.workshop.WorkshopState.Packed> packed = new java.util.ArrayList<>();
            for (com.apiculture.simulator.domain.workshop.WorkshopState o : all) {
                batchCount += o.batches.size();
                wax += o.waxKg;
                packed.addAll(o.packed);
                for (com.apiculture.simulator.domain.workshop.WorkshopState.Batch b : o.batches) {
                    if (b.waitingFormat()) {
                        waitingFormat++;
                    } else if (!b.inMachine && missing == null && o.level(b.stage) <= 0) {
                        missing = com.apiculture.simulator.presentation.workshop.WorkshopUi.machineName(c, b.stage);
                    }
                }
            }
            com.apiculture.simulator.domain.workshop.WorkshopState s = new com.apiculture.simulator.domain.workshop.WorkshopState();
            s.waxKg = wax;
            s.packed.addAll(packed);
            for (int i = 0; i < batchCount; i++) {
                s.batches.add(new com.apiculture.simulator.domain.workshop.WorkshopState.Batch());
            }
            if (missing != null) {
                alerts.add(getString(R.string.workshop_dash_machine, missing));
            }
            if (waitingFormat > 0) {
                alerts.add(getString(R.string.workshop_dash_format, waitingFormat));
            }
            if (!s.batches.isEmpty()) {
                // Capítulo 10. Primera tanda en el obrador.
                com.apiculture.simulator.presentation.tutorial.TutorialBus.emit(
                        com.apiculture.simulator.presentation.tutorial.TutorialEvent.WORKSHOP_BATCH);
            }
            int running = s.batches.size() - waitingFormat;
            if (running > 0) {
                info.add(getString(R.string.workshop_dash_running, running));
            }
            int jars = 0;
            for (com.apiculture.simulator.domain.workshop.WorkshopState.Packed p : s.packed) {
                if (p.format != com.apiculture.simulator.domain.workshop.WorkshopRules.Format.BULK) {
                    jars += Math.max(0, p.jars);
                }
            }
            if (jars > 0) {
                info.add(getString(R.string.workshop_dash_stock, jars));
            }
            if (s.waxKg >= 0.5) {
                info.add(getString(R.string.workshop_dash_wax, s.waxKg));
            }
        }
        binding.llWorkshopLines.removeAllViews();
        if (alerts.isEmpty() && info.isEmpty()) {
            binding.cardWorkshop.setVisibility(View.GONE);
            return;
        }
        binding.cardWorkshop.setVisibility(View.VISIBLE);
        for (String line : alerts) {
            binding.llWorkshopLines.addView(workshopLine(line, R.color.dash_warning));
        }
        for (String line : info) {
            binding.llWorkshopLines.addView(workshopLine(line, R.color.dash_text_card));
        }
    }

    @NonNull
    private TextView workshopLine(@NonNull String text, int colorRes) {
        TextView t = new TextView(requireContext());
        t.setText(text);
        t.setTextSize(15);
        t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
        t.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), colorRes));
        t.setPadding(0, 2, 0, 2);
        return t;
    }

    private void refreshWeekChart(String ownerId) {
        if (dashboardViewModel == null || binding == null) {
            return;
        }
        dashboardViewModel.loadWeeklyHoney(ownerId, values -> {
            if (!isAdded() || binding == null || values == null) {
                return;
            }
            LinearLayout chart = binding.weekChart;
            int cols = Math.min(values.length, chart.getChildCount());
            double max = 0.01;
            for (int i = 0; i < cols; i++) {
                max = Math.max(max, values[i]);
            }
            float density = getResources().getDisplayMetrics().density;
            int full = chart.getHeight();
            if (full <= 0) {
                full = Math.round(110f * density);
            }
            int labelSpace = Math.round(16f * density);
            int usable = Math.max(labelSpace, full - labelSpace);
            for (int i = 0; i < cols; i++) {
                View col = chart.getChildAt(i);
                if (!(col instanceof FrameLayout) || ((FrameLayout) col).getChildCount() == 0) {
                    continue;
                }
                FrameLayout frame = (FrameLayout) col;
                View bar = frame.getChildAt(0);
                ViewGroup.LayoutParams lp = bar.getLayoutParams();
                lp.height = Math.max(Math.round(4f * density),
                        (int) (usable * (values[i] / max)));
                bar.setLayoutParams(lp);
                TextView kg = frame.getChildCount() > 1 && frame.getChildAt(1) instanceof TextView
                        ? (TextView) frame.getChildAt(1)
                        : new TextView(frame.getContext());
                if (kg.getParent() == null) {
                    FrameLayout.LayoutParams labelLp = new FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT);
                    labelLp.gravity = android.view.Gravity.TOP | android.view.Gravity.CENTER_HORIZONTAL;
                    kg.setLayoutParams(labelLp);
                    kg.setGravity(android.view.Gravity.CENTER);
                    kg.setMaxLines(1);
                    kg.setTextColor(ContextCompat.getColor(frame.getContext(), R.color.dash_text_card));
                    kg.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 9f);
                    frame.addView(kg);
                }
                kg.setText(String.format(Locale.getDefault(), "%.2f kg", values[i]));
            }
        });
    }

    void refreshMailBadge() {
        if (binding == null) {
            return;
        }
        Mailbox.unread(count -> {
            if (!isAdded() || binding == null) {
                return;
            }
            binding.mailUnreadDot.setVisibility(count > 0 ? View.VISIBLE : View.GONE);
        });
    }

    @Override
    public void onPause() {
        stopFloraPlantingTicker();
        stopTripTicker();
        super.onPause();
    }

    @Override
    public void onDestroyView() {
        View root = getView();
        if (root != null) {
            root.removeCallbacks(showProfileCover);
        }
        ProfileLoadingCover.hide();
        super.onDestroyView();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        dashboardViewModel = new ViewModelProvider(this).get(DashboardViewModel.class);
        DashboardViewModel viewModel = dashboardViewModel;
        SignedInUser sessionUser = PlayerAuth.getInstance().getCurrentUser();
        if (sessionUser != null) {
            viewModel.bindHivesForUser(sessionUser.getUid());
        }

        viewModel.resetBusy().observe(getViewLifecycleOwner(), busy ->
                setResetControlsEnabled(!Boolean.TRUE.equals(busy)));
        viewModel.statCoins().observe(getViewLifecycleOwner(), binding.tvStatCoins::setText);
        View.OnClickListener openEconomy = v -> EconomyDialog.show(requireContext());
        binding.tvStatCoins.setOnClickListener(openEconomy);
        binding.ivStatCoins.setOnClickListener(openEconomy);
        viewModel.statHoney().observe(getViewLifecycleOwner(), binding.tvStatHoney::setText);
        viewModel.economyRevision().observe(getViewLifecycleOwner(), tick -> viewModel.refreshEconomyDisplay());
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
        viewModel.dayText().observe(getViewLifecycleOwner(), text -> {
            boolean show = text != null && !text.isEmpty();
            binding.tvDay.setVisibility(show ? View.VISIBLE : View.GONE);
            binding.tvDay.setText(show ? text : "");
        });
        viewModel.profileName().observe(getViewLifecycleOwner(), binding.tvProfileName::setText);
        viewModel.profileReady().observe(getViewLifecycleOwner(), ready -> {
            View root = getView();
            if (root != null) {
                root.removeCallbacks(showProfileCover);
            }
            if (Boolean.TRUE.equals(ready)) {
                ProfileLoadingCover.hide();
            } else if (root != null) {
                root.postDelayed(showProfileCover, 280);
            }
        });
        viewModel.profilePhoto().observe(getViewLifecycleOwner(), bmp -> {
            if (binding == null) {
                return;
            }
            if (bmp != null) {
                binding.ivProfilePhoto.setImageBitmap(bmp);
            } else {
                binding.ivProfilePhoto.setImageResource(R.drawable.ic_apicultor);
            }
        });
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
            event.btnDismissEvent.setVisibility(banner.canDismiss ? View.VISIBLE : View.GONE);
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

        TruckLiveTrips.observe(requireContext()).observe(getViewLifecycleOwner(), trips -> {
            lastHiveTrips = trips != null ? trips : Collections.emptyList();
            bindTripRows();
        });
        HoneyLogistics.observe(requireContext()).observe(getViewLifecycleOwner(), trips -> {
            lastCargoTrips = trips != null ? trips : Collections.emptyList();
            bindTripRows();
        });

        if (sessionUser != null && sessionUser.getDisplayName() != null
                && !sessionUser.getDisplayName().isEmpty()) {
            binding.tvProfileName.setText(sessionUser.getDisplayName());
        }

        NavController nav = Navigation.findNavController(view);
        binding.tileQuickHarvest.setOnClickListener(v -> startHarvestWizard(viewModel));
        binding.tileQuickSell.setOnClickListener(v -> MarketPickerDialogs.show(this));
        binding.tileQuickWorkshop.setOnClickListener(v ->
                com.apiculture.simulator.presentation.workshop.WorkshopUi.open(this));
        binding.cardWorkshop.setOnClickListener(v ->
                com.apiculture.simulator.presentation.workshop.WorkshopUi.open(this));
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
        binding.globalEvent.btnDismissEvent.setOnClickListener(v -> {
            SignedInUser closer = PlayerAuth.getInstance().getCurrentUser();
            viewModel.dismissGlobalEvent(closer != null ? closer.getUid() : null);
        });
        binding.globalEvent.btnClaimEventReward.setOnClickListener(v -> {
            SignedInUser u = PlayerAuth.getInstance().getCurrentUser();
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

        binding.btnDashboardMail.setOnClickListener(v -> MailDialogs.show(this));
        binding.btnDashboardSettings.setOnClickListener(v -> {
            PopupMenu popup = new PopupMenu(
                    new android.view.ContextThemeWrapper(
                            requireContext(), R.style.ThemeOverlay_Apiculture_SettingsMenu),
                    v);
            popup.getMenuInflater().inflate(R.menu.menu_dashboard_settings, popup.getMenu());
            android.view.MenuItem adminMenu = popup.getMenu().findItem(R.id.action_admin);
            if (adminMenu != null) {
                adminMenu.setVisible(Boolean.TRUE.equals(viewModel.isAdmin().getValue()));
            }
            android.view.MenuItem privacyMenu = popup.getMenu().findItem(R.id.action_privacy);
            if (privacyMenu != null) {
                privacyMenu.setVisible(!LegalLinks.privacyPolicyUrl().isEmpty());
            }
            popup.setOnMenuItemClickListener(item -> {
                if (item.getItemId() == R.id.action_profile) {
                    showEditProfileDialog(viewModel);
                    return true;
                }
                // Capítulo 1. Repetir el primer colmenar desde el menú del panel.
                if (item.getItemId() == R.id.action_tutorial) {
                    com.apiculture.simulator.presentation.tutorial.TutorialBus.replayFirstChapter();
                    return true;
                }
                if (item.getItemId() == R.id.action_ranking) {
                    nav.navigate(R.id.rankingFragment);
                    return true;
                }
                if (item.getItemId() == R.id.action_shop) {
                    nav.navigate(R.id.shopFragment);
                    return true;
                }
                if (item.getItemId() == R.id.action_economy) {
                    EconomyDialog.show(requireContext());
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
                if (item.getItemId() == R.id.action_admin_trips) {
                    nav.navigate(R.id.adminTripsFragment);
                    return true;
                }
                if (item.getItemId() == R.id.action_admin_contracts_tutorial) {
                    com.apiculture.simulator.presentation.tutorial.TutorialBus.replayContractsChapter();
                    return true;
                }
                if (item.getItemId() == R.id.action_reset_game) {
                    confirmResetGame(viewModel);
                    return true;
                }
                if (item.getItemId() == R.id.action_privacy) {
                    LegalLinks.open(requireContext(), LegalLinks.privacyPolicyUrl());
                    return true;
                }
                if (item.getItemId() == R.id.action_delete_account) {
                    confirmDeleteAccount();
                    return true;
                }
                if (item.getItemId() == R.id.action_logout) {
                    if (getActivity() instanceof MainActivity) {
                        ((MainActivity) getActivity()).logout();
                    }
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

    private boolean accountDeletionInFlight;

    private void confirmDeleteAccount() {
        if (accountDeletionInFlight) {
            return;
        }
        MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.account_delete_title)
                .setMessage(R.string.account_delete_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.account_delete_confirm, (d, w) -> deleteAccount());
        String web = LegalLinks.accountDeletionUrl();
        if (!web.isEmpty()) {
            dialog.setNeutralButton(R.string.account_delete_web,
                    (d, w) -> LegalLinks.open(requireContext(), web));
        }
        dialog.show();
    }

    private void deleteAccount() {
        if (accountDeletionInFlight || !isAdded()) {
            return;
        }
        accountDeletionInFlight = true;
        ApicultureApp app = (ApicultureApp) requireActivity().getApplication();
        AccountErasure.run(app, ok -> {
            accountDeletionInFlight = false;
            if (!Boolean.TRUE.equals(ok)) {
                if (isAdded()) {
                    GameNotice.show(requireContext(), R.string.account_delete_error);
                }
                return;
            }
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).showLoginAfterAccountDeletion();
            }
        });
    }

    private void confirmResetGame(@NonNull DashboardViewModel viewModel) {
        SignedInUser sessionUser = PlayerAuth.getInstance().getCurrentUser();
        if (sessionUser == null) {
            GameNotice.show(requireContext(), R.string.dashboard_reset_error);
            return;
        }
        final String uid = sessionUser.getUid();
        boolean admin = Boolean.TRUE.equals(viewModel.isAdmin().getValue());
        GameNotice.confirm(requireContext(),
                getString(admin
                        ? R.string.dashboard_reset_all_title
                        : R.string.dashboard_reset_confirm_title),
                getString(admin
                        ? R.string.dashboard_reset_all_message
                        : R.string.dashboard_reset_confirm_message),
                android.R.string.ok,
                android.R.string.cancel,
                () -> {
                    if (admin) {
                        resetEveryone(viewModel, uid);
                    } else {
                        resetMine(viewModel, uid);
                    }
                },
                null);
    }

    private void resetEveryone(@NonNull DashboardViewModel viewModel, @NonNull String uid) {
        ApicultureApp app = (ApicultureApp) requireActivity().getApplication();
        app.getAdminGameResetRepository().issueGlobalPlayerReset(uid, err -> {
            if (!isAdded()) {
                return;
            }
            if (err != null) {
                GameNotice.show(requireContext(), err);
            }
        }, ok -> {
            if (!isAdded()) {
                return;
            }
            app.getAdminGameResetRepository().fetchRemoteGeneration(gen ->
                    app.resetPlayerToStarterState(uid, gen == null ? 0L : gen,
                            msg -> finishReset(viewModel, uid, msg)));
        });
    }

    private void resetMine(@NonNull DashboardViewModel viewModel, @NonNull String uid) {
        viewModel.resetGameToStarterState(uid, msg -> finishReset(viewModel, uid, msg));
    }

    private void finishReset(@NonNull DashboardViewModel viewModel, @NonNull String uid,
            @Nullable String msg) {
        if (!isAdded() || binding == null) {
            return;
        }
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
    }

    private void startHarvestWizard(@NonNull DashboardViewModel viewModel) {
        setQuickActionsEnabled(false);
        viewModel.withLocalHives(hives -> {
            if (!isAdded() || binding == null) {
                return;
            }
            SignedInUser user = PlayerAuth.getInstance().getCurrentUser();
            if (user == null) {
                setQuickActionsEnabled(true);
                GameNotice.show(requireContext(), R.string.dashboard_harvest_all_session);
                return;
            }
            String uid = user.getUid();
            HoneyLogistics.loadApiaryHarvests(requireContext(), uid, hives, apiaries -> {
                if (!isAdded() || binding == null) {
                    return;
                }
                setQuickActionsEnabled(true);
                if (apiaries == null || apiaries.isEmpty()) {
                    GameNotice.show(requireContext(), R.string.dashboard_harvest_all_none);
                    return;
                }
                List<PlayableMapRegion> regions = new ArrayList<>();
                for (HoneyLogistics.ApiaryHarvest row : apiaries) {
                    if (!regions.contains(row.region)) {
                        regions.add(row.region);
                    }
                }
                if (regions.size() == 1) {
                    showHarvestApiaries(viewModel, uid, hives, apiaries, regions.get(0));
                    return;
                }
                HarvestCollectDialogs.pick(this, R.string.harvest_pick_region_title,
                        R.string.harvest_pick_region_sub, regionLabels(regions), index ->
                        showHarvestApiaries(viewModel, uid, hives, apiaries, regions.get(index)));
            });
        });
    }

    private void showHarvestApiaries(@NonNull DashboardViewModel viewModel, @NonNull String uid,
            @NonNull List<HiveEntity> hives, @NonNull List<HoneyLogistics.ApiaryHarvest> apiaries,
            @NonNull PlayableMapRegion region) {
        if (com.apiculture.simulator.presentation.workshop.WorkshopUi.blocksHarvest(requireContext(), uid)) {
            com.apiculture.simulator.presentation.workshop.WorkshopUi.showNeedWorkshop(this);
            return;
        }
        List<HoneyLogistics.ApiaryHarvest> inRegion = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        List<String> metas = new ArrayList<>();
        for (HoneyLogistics.ApiaryHarvest row : apiaries) {
            if (row.region != region) {
                continue;
            }
            inRegion.add(row);
            labels.add(row.label);
            metas.add(getString(R.string.harvest_pick_apiary_kg, row.kg));
        }
        HarvestCollectDialogs.pick(this, R.string.harvest_pick_apiary_title,
                R.string.harvest_pick_apiary_sub, labels, metas, apiaryIndex -> {
                    HoneyLogistics.ApiaryHarvest apiary = inRegion.get(apiaryIndex);
                                        HoneyLogistics.trucksForApiary(requireContext(), uid, apiary, trucks -> {
                                            if (!isAdded()) {
                                                return;
                                            }
                                            if (trucks == null || trucks.isEmpty()) {
                                                GameNotice.show(requireContext(), R.string.harvest_no_truck_region);
                                                return;
                                            }
                                            List<String> truckLabels = new ArrayList<>();
                                            List<String> truckMetas = new ArrayList<>();
                                            for (HoneyLogistics.HarvestTruck truck : trucks) {
                                                truckLabels.add(truck.label);
                                                truckMetas.add(getString(R.string.harvest_pick_truck_line,
                                                        truck.level, truck.distanceKm, truck.capacityKg));
                                            }
                                            HarvestCollectDialogs.pick(this, R.string.harvest_pick_truck_title,
                                                    R.string.harvest_pick_truck_sub, truckLabels, truckMetas, truckIndex -> {
                                                        setQuickActionsEnabled(false);
                                                        HoneyLogistics.planApiaryHarvest(requireContext(), uid, hives,
                                                                apiary, trucks.get(truckIndex).id, preview -> {
                                                                    if (preview == null || preview.cargoByHive.isEmpty()) {
                                                                        setQuickActionsEnabled(true);
                                                                        GameNotice.show(requireContext(),
                                                                                preview != null && preview.warehouseFull
                                                                                        ? R.string.harvest_warehouse_full
                                                                                        : R.string.dashboard_harvest_all_none);
                                                                        return;
                                                                    }
                                                                    viewModel.commitHarvestAll(
                                                                            Collections.singletonList(preview), result -> {
                                                                                if (!isAdded() || binding == null) {
                                                                                    return;
                                                                                }
                                                                                setQuickActionsEnabled(true);
                                                                                if (result.success) {
                                                                                    com.apiculture.simulator.presentation.tutorial.TutorialBus.emit(
                                                                                            com.apiculture.simulator.presentation.tutorial.TutorialEvent.HARVESTED);
                                                                                    if (!result.deferred) {
                                                                                        showHarvestSummaryDialog(result);
                                                                                    }
                                                                                } else {
                                                                                    GameNotice.show(requireContext(), result.message);
                                                                                }
                                                                            });
                                                                });
                                                    });
                                        });
                                    });
    }

    @NonNull
    private List<String> regionLabels(@NonNull List<PlayableMapRegion> regions) {
        List<String> labels = new ArrayList<>();
        for (PlayableMapRegion region : regions) {
            if (region == PlayableMapRegion.MADAGASCAR) {
                labels.add(getString(R.string.harvest_region_madagascar));
            } else if (region == PlayableMapRegion.SOUTH_AFRICA) {
                labels.add(getString(R.string.harvest_region_za));
            } else {
                labels.add(getString(R.string.harvest_region_iberia));
            }
        }
        return labels;
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
        binding.tileQuickWorkshop.setEnabled(enabled);
        float alpha = enabled ? 1f : 0.55f;
        binding.tileQuickHarvest.setAlpha(alpha);
        binding.tileQuickSell.setAlpha(alpha);
        binding.tileQuickWorkshop.setAlpha(alpha);
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
            tvFlora.setText(plantingFloraLabel(r.floraKey));
            ivFlora.setImageResource(
                    com.apiculture.simulator.presentation.hive.HiveSiteSummaryUi.floraHoneyJarIcon(r.floraKey));
            binding.llFloraPlantings.addView(row);
        }
    }

    /** El naranjo se nombra como árbol; el resto usa el nombre de la flora ya traducido. */
    private String plantingFloraLabel(String floraKey) {
        if ("Campo de naranjos".equals(floraKey)) {
            return getString(R.string.crop_name_naranjos);
        }
        return com.apiculture.simulator.presentation.hive.HiveSiteSummaryUi.floraLabel(
                requireContext(), floraKey);
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
            cd.setText(HoneyLogistics.formatCountdown(rem));
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

    private void startTripTicker() {
        stopTripTicker();
        tripTickRunnable = () -> {
            tickTripCountdowns();
            kickDueTrips();
            if (System.currentTimeMillis() - workshopCardAt > 15_000L) {
                refreshWorkshopCard();
            }
            dashFloraHandler.postDelayed(tripTickRunnable, 1000L);
        };
        dashFloraHandler.post(tripTickRunnable);
    }

    private void stopTripTicker() {
        if (tripTickRunnable != null) {
            dashFloraHandler.removeCallbacks(tripTickRunnable);
            tripTickRunnable = null;
        }
    }

    /** Si un tramo ya llegó, pasa al siguiente aunque el jugador esté en el inicio. */
    private void kickDueTrips() {
        if (!isAdded()) {
            return;
        }
        long now = System.currentTimeMillis();
        boolean due = false;
        for (TruckTripEntity trip : lastHiveTrips) {
            if (trip != null && TruckTripRules.wallClockDone(trip, now)) {
                due = true;
                break;
            }
        }
        if (!due) {
            for (CargoTripEntity trip : lastCargoTrips) {
                if (trip != null && HoneyLogistics.tourClockDone(trip, now)) {
                    due = true;
                    break;
                }
            }
        }
        if (!due || !tripUpkeepRunning.compareAndSet(false, true)) {
            return;
        }
        android.content.Context app = requireContext().getApplicationContext();
        tripUpkeep.execute(() -> {
            try {
                ApicultureApp game = (ApicultureApp) app;
                TruckLiveTrips.completeDue(app);
                HoneyLogistics.completeDue(app, game.getEconomyRepository(), game.getMarketRepository());
            } finally {
                tripUpkeepRunning.set(false);
            }
        });
    }

    /** Solo cambia el texto de las cuentas atrás. La fila se reconstruye cuando cambia el viaje. */
    private void tickTripCountdowns() {
        if (binding == null) {
            return;
        }
        refreshCountdownLabels(binding.dashTrips.llTripRows);
    }

    private void refreshCountdownLabels(@NonNull View root) {
        Object tag = root.getTag(R.id.tv_trip_eta);
        if (tag instanceof java.util.function.LongSupplier && root instanceof TextView) {
            TextView label = (TextView) root;
            String next = HoneyLogistics.formatRemaining(
                    ((java.util.function.LongSupplier) tag).getAsLong());
            if (!next.contentEquals(label.getText())) {
                label.setText(next);
            }
        }
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                refreshCountdownLabels(group.getChildAt(i));
            }
        }
    }

    private void stampCountdown(@NonNull TextView label, @NonNull java.util.function.LongSupplier remainingMs) {
        label.setTag(R.id.tv_trip_eta, remainingMs);
        label.setText(HoneyLogistics.formatRemaining(remainingMs.getAsLong()));
    }

    private void bindTripRows() {
        if (binding == null) {
            return;
        }
        LinearLayout host = binding.dashTrips.llTripRows;
        int keptScroll = binding.dashTrips.svTripRows.getScrollY();
        host.removeAllViews();
        long now = System.currentTimeMillis();
        NavController nav = Navigation.findNavController(requireActivity(), R.id.nav_host_fragment);
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        String uid = PlayerAuth.getInstance().getUid();
        java.util.ArrayList<TripLine> lines = new java.util.ArrayList<>();
        for (TruckTripEntity trip : lastHiveTrips) {
            if (trip == null || uid == null || !uid.equals(trip.ownerId)) {
                continue;
            }
            double[] pos = TruckTripRules.position(trip, now);
            View row = inflater.inflate(R.layout.item_trip_row, host, false);
            bindHiveTripRow(row, trip, now);
            row.setOnClickListener(v -> openMapAt(nav, pos[0], pos[1], trip.hiveId));
            if (TruckTripEntity.SPLIT_MOVE.equals(trip.destFlora)) {
                View cancel = row.findViewById(R.id.btn_trip_cancel);
                if (cancel != null) {
                    cancel.setVisibility(View.GONE);
                }
            } else {
                wireTripCancel(row, () -> confirmCancelHive(trip.hiveId));
            }
            lines.add(new TripLine(TruckTripRules.remainingMs(trip, now), row));
        }
        for (List<CargoTripEntity> bundle : cargoBundles(uid)) {
            if (bundle.isEmpty()) {
                continue;
            }
            CargoTripEntity focus = activeCargo(bundle, now);
            double[] pos = HoneyLogistics.tourPosition(focus, now);
            View row = inflater.inflate(R.layout.item_trip_row, host, false);
            java.util.List<HoneyLogistics.TourLeg> tour = HoneyLogistics.collectTour(focus);
            if (tour.size() > 1) {
                bindCollectTour(row, focus, tour, now);
            } else if (bundle.size() > 1 || hasNextStop(focus)) {
                bindCargoLegs(row, bundle, now);
            } else {
                bindCargoTripRow(row, focus, now);
            }
            row.setOnClickListener(v -> openMapAt(nav, pos[0], pos[1], focus.id));
            if (!CargoTripEntity.PHASE_RETURN.equals(focus.phase)) {
                wireTripCancel(row, () -> confirmCancelCargo(focus.id));
            } else {
                View cancel = row.findViewById(R.id.btn_trip_cancel);
                if (cancel != null) {
                    cancel.setVisibility(View.GONE);
                }
            }
            lines.add(new TripLine(CargoTripRules.remainingMs(focus, now), row));
        }
        lines.sort((a, b) -> Long.compare(a.remainingMs, b.remainingMs));
        for (TripLine line : lines) {
            host.addView(line.row);
        }
        binding.dashTrips.tvTripsEmpty.setVisibility(lines.isEmpty() ? View.VISIBLE : View.GONE);
        binding.dashTrips.llTripRows.post(() -> fitTripViewport(0, keptScroll));
    }

    /** La tarjeta enseña como mucho seis viajes; el resto se desplaza dentro. */
    private void fitTripViewport(int attempt, int keptScroll) {
        if (binding == null) {
            return;
        }
        LinearLayout host = binding.dashTrips.llTripRows;
        int count = host.getChildCount();
        ViewGroup.LayoutParams lp = binding.dashTrips.svTripRows.getLayoutParams();
        if (count == 0) {
            if (lp.height != ViewGroup.LayoutParams.WRAP_CONTENT) {
                lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                binding.dashTrips.svTripRows.setLayoutParams(lp);
            }
            return;
        }
        int limit = Math.min(6, count);
        int height = 0;
        for (int i = 0; i < limit; i++) {
            int childHeight = host.getChildAt(i).getHeight();
            if (childHeight <= 0) {
                if (attempt < 5) {
                    host.post(() -> fitTripViewport(attempt + 1, keptScroll));
                }
                return;
            }
            height += childHeight;
        }
        if (lp.height != height) {
            lp.height = height;
            binding.dashTrips.svTripRows.setLayoutParams(lp);
        }
        binding.dashTrips.svTripRows.scrollTo(0, keptScroll);
    }

    private void wireTripCancel(@NonNull View row, @NonNull Runnable confirm) {
        View cancel = row.findViewById(R.id.btn_trip_cancel);
        if (cancel == null) {
            return;
        }
        cancel.setOnClickListener(v -> confirm.run());
    }

    private void confirmCancelCargo(@NonNull String tripId) {
        if (!isAdded()) {
            return;
        }
        GameNotice.confirm(requireContext(),
                getString(R.string.trip_cancel_title),
                getString(R.string.trip_cancel_message),
                R.string.trip_cancel_keep,
                R.string.trip_cancel_ok,
                null,
                () -> HoneyLogistics.cancelTrip(requireContext(), tripId, err -> {
                    if (!isAdded()) {
                        return;
                    }
                    if (err == null) {
                        GameNotice.showSuccess(requireContext(), R.string.trip_cancel_done);
                    } else {
                        GameNotice.show(requireContext(), err);
                    }
                }));
    }

    private void confirmCancelHive(@NonNull String hiveId) {
        if (!isAdded()) {
            return;
        }
        GameNotice.confirm(requireContext(),
                getString(R.string.trip_cancel_title),
                getString(R.string.trip_cancel_message),
                R.string.trip_cancel_keep,
                R.string.trip_cancel_ok,
                null,
                () -> {
                    android.content.Context app = requireContext().getApplicationContext();
                    tripUpkeep.execute(() -> {
                        boolean turned = TruckLiveTrips.cancel(app, hiveId);
                        if (!isAdded()) {
                            return;
                        }
                        requireActivity().runOnUiThread(() -> {
                            if (!isAdded()) {
                                return;
                            }
                            GameNotice.show(requireContext(), turned
                                    ? R.string.trip_cancel_done
                                    : R.string.trip_cancel_already);
                        });
                    });
                });
    }

    private void bindHiveTripRow(@NonNull View row, @NonNull TruckTripEntity trip, long now) {
        ImageView cargo = row.findViewById(R.id.iv_trip_cargo);
        ImageView from = row.findViewById(R.id.iv_trip_from);
        ImageView to = row.findViewById(R.id.iv_trip_to);
        cargo.setVisibility(View.GONE);
        row.findViewById(R.id.ll_trip_cargo).setVisibility(View.GONE);
        row.findViewById(R.id.tv_trip_kg).setVisibility(View.GONE);
        from.setImageResource(R.drawable.ic_compracolmena);
        to.setImageResource(R.drawable.ic_compracolmena);
        to.setScaleType(ImageView.ScaleType.FIT_CENTER);
        bindTripLeg(row, false);
        ((TextView) row.findViewById(R.id.tv_trip_route)).setText(getString(
                R.string.dashboard_trip_route,
                getString(R.string.dashboard_place_apiary), tripLabel(trip.destHexId)));
        stampCountdown(row.findViewById(R.id.tv_trip_eta),
                () -> TruckTripRules.remainingMs(trip, System.currentTimeMillis()));
    }

    private void showTripCargo(@NonNull View row, @Nullable Map<String, Double> cargo) {
        View block = row.findViewById(R.id.ll_trip_cargo);
        ImageView one = row.findViewById(R.id.iv_trip_cargo);
        TextView kg = row.findViewById(R.id.tv_trip_kg);
        LinearLayout jars = row.findViewById(R.id.ll_trip_jars);
        if (cargo == null || cargo.isEmpty()) {
            block.setVisibility(View.GONE);
            return;
        }
        block.setVisibility(View.VISIBLE);
        if (cargo.size() == 1) {
            jars.setVisibility(View.GONE);
            Map.Entry<String, Double> only = cargo.entrySet().iterator().next();
            one.setVisibility(View.VISIBLE);
            one.setImageResource(HiveSiteSummaryUi.floraHoneyJarIcon(only.getKey()));
            kg.setVisibility(View.VISIBLE);
            kg.setText(getString(R.string.dashboard_trip_kg,
                    only.getValue() == null ? 0.0 : only.getValue()));
            return;
        }
        one.setVisibility(View.GONE);
        kg.setVisibility(View.GONE);
        TripCargoUi.bind(LayoutInflater.from(requireContext()), jars, cargo, requireContext());
    }

    private void bindCargoTripRow(@NonNull View row, @NonNull CargoTripEntity trip, long now) {
        ImageView from = row.findViewById(R.id.iv_trip_from);
        ImageView to = row.findViewById(R.id.iv_trip_to);
        boolean back = CargoTripEntity.PHASE_RETURN.equals(trip.phase);
        boolean emptyReturn = back && !CargoTripEntity.KIND_COLLECT.equals(trip.kind);
        if (emptyReturn) {
            row.findViewById(R.id.ll_trip_cargo).setVisibility(View.INVISIBLE);
        } else if (CargoTripEntity.KIND_COLLECT.equals(trip.kind)) {
            showTripCargo(row, HoneyLogistics.carriedNow(trip, now));
        } else {
            showTripCargo(row, HoneyLogistics.cargoOf(trip));
        }
        String fromLabel = placeLabel(SeaportCatalog.present(requireContext(),
                trip.originLabel != null ? trip.originLabel : getString(R.string.dashboard_place_origin),
                trip.originHexId));
        String toLabel = placeLabel(SeaportCatalog.present(requireContext(),
                trip.destLabel != null ? trip.destLabel : getString(R.string.dashboard_place_dest),
                trip.destHexId));
        to.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int warehouse = R.drawable.ic_almacen_miel;
        int hive = R.drawable.ic_compracolmena;
        int market = R.drawable.ic_venta;
        int npc = NpcPortraitUi.faceDrawable(NpcContractCatalog.portraitIndexFor(trip.npcName));
        if (CargoTripEntity.KIND_ORDER.equals(trip.kind)) {
            from.setImageResource(back ? npc : warehouse);
            to.setImageResource(back ? warehouse : npc);
            from.setScaleType(back ? ImageView.ScaleType.CENTER_CROP : ImageView.ScaleType.FIT_CENTER);
            to.setScaleType(back ? ImageView.ScaleType.FIT_CENTER : ImageView.ScaleType.CENTER_CROP);
        } else if (CargoTripEntity.KIND_DELIVERY.equals(trip.kind)) {
            from.setImageResource(R.drawable.ic_fleet_shop);
            to.setImageResource(warehouse);
        } else if (CargoTripEntity.KIND_WHOLESALE.equals(trip.kind)) {
            from.setImageResource(back ? market : warehouse);
            to.setImageResource(back ? warehouse : market);
        } else if (back) {
            from.setImageResource(hive);
            to.setImageResource(warehouse);
        } else {
            from.setImageResource(warehouse);
            to.setImageResource(hive);
        }
        bindTripLeg(row, back);
        ((TextView) row.findViewById(R.id.tv_trip_route)).setText(
                getString(R.string.dashboard_trip_route, fromLabel, toLabel));
        stampCountdown(row.findViewById(R.id.tv_trip_eta),
                () -> CargoTripRules.remainingMs(trip, System.currentTimeMillis()));
    }

    @NonNull
    private List<List<CargoTripEntity>> cargoBundles(@Nullable String uid) {
        java.util.LinkedHashMap<String, List<CargoTripEntity>> groups = new java.util.LinkedHashMap<>();
        for (CargoTripEntity trip : lastCargoTrips) {
            if (trip == null || uid == null || !uid.equals(trip.ownerId)) {
                continue;
            }
            String key = trip.shipmentId != null && !trip.shipmentId.isEmpty() ? trip.shipmentId : trip.id;
            groups.computeIfAbsent(key, ignored -> new java.util.ArrayList<>()).add(trip);
        }
        return new java.util.ArrayList<>(groups.values());
    }

    @NonNull
    private static CargoTripEntity activeCargo(@NonNull List<CargoTripEntity> bundle, long now) {
        CargoTripEntity best = bundle.get(0);
        for (CargoTripEntity trip : bundle) {
            if (now >= trip.startEpochMs && now < trip.startEpochMs + trip.durationMs) {
                return trip;
            }
            if (trip.startEpochMs < best.startEpochMs) {
                best = trip;
            }
        }
        return best;
    }

    private static boolean hasNextStop(@NonNull CargoTripEntity trip) {
        return trip.chainLabel != null && !trip.chainLabel.isEmpty()
                && (Math.abs(trip.chainLat) > 1e-8 || Math.abs(trip.chainLng) > 1e-8)
                && !trip.chainLabel.equals(trip.destLabel);
    }

    private void bindCollectTour(@NonNull View row, @NonNull CargoTripEntity head,
            @NonNull List<HoneyLogistics.TourLeg> tour, long now) {
        row.findViewById(R.id.ll_trip_icons).setVisibility(View.GONE);
        row.findViewById(R.id.tv_trip_route).setVisibility(View.GONE);
        row.findViewById(R.id.tv_trip_eta).setVisibility(View.GONE);
        row.findViewById(R.id.tv_trip_leg).setVisibility(View.GONE);
        row.findViewById(R.id.ll_trip_cargo).setVisibility(View.GONE);
        LinearLayout legs = row.findViewById(R.id.ll_trip_legs);
        legs.setVisibility(View.VISIBLE);
        legs.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        int hive = R.drawable.ic_compracolmena;
        int warehouse = R.drawable.ic_almacen_miel;
        for (int i = 0; i < tour.size(); i++) {
            HoneyLogistics.TourLeg leg = tour.get(i);
            int index = i;
            long time = HoneyLogistics.legRemaining(head, tour, index, now);
            addTripLeg(inflater, legs,
                    leg.fromWarehouse ? warehouse : hive,
                    leg.toWarehouse ? warehouse : hive,
                    leg.fromLabel, leg.toLabel, time, !leg.current, leg.done,
                    HoneyLogistics.carriedOnLeg(head, tour, index),
                    leg.current && !leg.done
                            ? () -> HoneyLogistics.legRemaining(head, tour, index, System.currentTimeMillis())
                            : null);
        }
    }

    private void bindCargoLegs(@NonNull View row, @NonNull List<CargoTripEntity> bundle, long now) {
        row.findViewById(R.id.ll_trip_icons).setVisibility(View.GONE);
        row.findViewById(R.id.tv_trip_route).setVisibility(View.GONE);
        row.findViewById(R.id.tv_trip_eta).setVisibility(View.GONE);
        row.findViewById(R.id.tv_trip_leg).setVisibility(View.GONE);
        CargoTripEntity head = bundle.get(0);
        boolean cargo = !CargoTripEntity.KIND_DELIVERY.equals(head.kind) && head.kg > 1e-6;
        if (!cargo) {
            row.findViewById(R.id.ll_trip_cargo).setVisibility(View.GONE);
        } else {
            showTripCargo(row, HoneyLogistics.cargoOf(head));
        }
        LinearLayout legs = row.findViewById(R.id.ll_trip_legs);
        legs.setVisibility(View.VISIBLE);
        legs.removeAllViews();
        List<CargoTripEntity> ordered = new java.util.ArrayList<>(bundle);
        ordered.sort((a, b) -> Long.compare(a.startEpochMs, b.startEpochMs));
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        boolean overseas = false;
        for (CargoTripEntity trip : ordered) {
            if (CargoTripEntity.LEG_HAUL_SHIP.equals(trip.legRole)) {
                overseas = true;
                break;
            }
        }
        for (CargoTripEntity trip : ordered) {
            if (overseas && CargoTripEntity.LEG_PICKUP.equals(trip.legRole)) {
                continue;
            }
            boolean pending = now < trip.startEpochMs;
            long time = pending ? trip.durationMs : CargoTripRules.remainingMs(trip, now);
            addTripLeg(inflater, legs, tripIcon(trip, true), tripIcon(trip, false),
                    SeaportCatalog.present(requireContext(), trip.originLabel, trip.originHexId),
                    SeaportCatalog.present(requireContext(), trip.destLabel, trip.destHexId), time, pending,
                    false, null,
                    pending ? null : () -> CargoTripRules.remainingMs(trip, System.currentTimeMillis()));
        }
        for (CargoTripEntity trip : ordered) {
            if (!hasNextStop(trip)) {
                continue;
            }
            double km = TranshumanceRules.haversineKm(
                    trip.destLat, trip.destLng, trip.chainLat, trip.chainLng);
            long hop = FleetRules.durationMs(km, 70);
            int fromIcon = tripIcon(trip, false);
            int toIcon = chainIcon(trip);
            addTripLeg(inflater, legs, fromIcon, toIcon,
                    SeaportCatalog.present(requireContext(), trip.destLabel, trip.destHexId),
                    SeaportCatalog.present(requireContext(), trip.chainLabel, trip.chainHexId), hop, true);
        }
    }

    private void addTripLeg(@NonNull LayoutInflater inflater, @NonNull LinearLayout host,
            int fromIcon, int toIcon, @Nullable String fromLabel, @Nullable String toLabel,
            long timeMs, boolean pending) {
        addTripLeg(inflater, host, fromIcon, toIcon, fromLabel, toLabel, timeMs, pending, false, null, null);
    }

    private void addTripLeg(@NonNull LayoutInflater inflater, @NonNull LinearLayout host,
            int fromIcon, int toIcon, @Nullable String fromLabel, @Nullable String toLabel,
            long timeMs, boolean pending, boolean done, @Nullable Map<String, Double> load,
            @Nullable java.util.function.LongSupplier liveRemaining) {
        View leg = inflater.inflate(R.layout.item_trip_leg, host, false);
        ((ImageView) leg.findViewById(R.id.iv_leg_from)).setImageResource(fromIcon);
        ((ImageView) leg.findViewById(R.id.iv_leg_to)).setImageResource(toIcon);
        View honeyScroll = leg.findViewById(R.id.hs_leg_honey);
        LinearLayout honey = leg.findViewById(R.id.ll_leg_honey);
        if (load != null && !load.isEmpty()) {
            TripCargoUi.bind(inflater, honey, load, requireContext());
            honeyScroll.setVisibility(honey.getVisibility());
        }
        ((TextView) leg.findViewById(R.id.tv_leg_route)).setText(getString(
                R.string.dashboard_trip_route,
                placeLabel(fromLabel),
                placeLabel(toLabel)));
        TextView time = leg.findViewById(R.id.tv_leg_time);
        if (done) {
            time.setText("");
            time.setTag(R.id.tv_trip_eta, null);
        } else if (pending) {
            time.setTag(R.id.tv_trip_eta, null);
            time.setText(HoneyLogistics.formatRemaining(timeMs));
            time.setTextColor(ContextCompat.getColor(requireContext(), R.color.dash_muted));
        } else {
            time.setTextColor(ContextCompat.getColor(requireContext(), R.color.dash_text_card));
            if (liveRemaining != null) {
                stampCountdown(time, liveRemaining);
            } else {
                time.setTag(R.id.tv_trip_eta, null);
                time.setText(HoneyLogistics.formatRemaining(timeMs));
            }
        }
        host.addView(leg);
    }

    private static int tripIcon(@NonNull CargoTripEntity trip, boolean origin) {
        if (CargoTripEntity.KIND_DELIVERY.equals(trip.kind)) {
            return origin ? R.drawable.ic_fleet_shop : R.drawable.ic_almacen_miel;
        }
        if (CargoTripEntity.KIND_ORDER.equals(trip.kind)) {
            boolean back = CargoTripEntity.PHASE_RETURN.equals(trip.phase);
            if (origin == back) {
                return NpcPortraitUi.faceDrawable(NpcContractCatalog.portraitIndexFor(trip.npcName));
            }
            return R.drawable.ic_almacen_miel;
        }
        if (CargoTripEntity.LEG_HAUL_SHIP.equals(trip.legRole)) {
            return R.drawable.ic_fleet_port;
        }
        if (CargoTripEntity.LEG_HAUL_TRUCK.equals(trip.legRole)
                || CargoTripEntity.LEG_PICKUP.equals(trip.legRole)) {
            return origin ? R.drawable.ic_almacen_miel : R.drawable.ic_fleet_port;
        }
        if (CargoTripEntity.LEG_DELIVER.equals(trip.legRole)) {
            if (origin) {
                return CargoTripEntity.KIND_TRANSFER.equals(trip.kind)
                        ? R.drawable.ic_fleet_port : R.drawable.ic_fleet_port;
            }
            return placeIcon(trip.kind, false);
        }
        if (CargoTripEntity.LEG_TRANSFER.equals(trip.legRole)) {
            return R.drawable.ic_almacen_miel;
        }
        return placeIcon(trip.kind, origin);
    }

    private static int chainIcon(@NonNull CargoTripEntity trip) {
        if (CargoTripEntity.KIND_ORDER.equals(trip.kind)) {
            String name = trip.chainLabel != null && !trip.chainLabel.isEmpty()
                    ? trip.chainLabel : trip.npcName;
            return NpcPortraitUi.faceDrawable(NpcContractCatalog.portraitIndexFor(name));
        }
        if (CargoTripEntity.KIND_WHOLESALE.equals(trip.kind)) {
            return R.drawable.ic_venta;
        }
        return R.drawable.ic_almacen_miel;
    }

    private static int placeIcon(@Nullable String kind, boolean origin) {
        boolean back = false;
        if (CargoTripEntity.KIND_ORDER.equals(kind)) {
            return origin ? R.drawable.ic_almacen_miel
                    : NpcPortraitUi.faceDrawable(0);
        }
        if (CargoTripEntity.KIND_WHOLESALE.equals(kind)) {
            return origin ? R.drawable.ic_almacen_miel : R.drawable.ic_venta;
        }
        if (CargoTripEntity.KIND_COLLECT.equals(kind)) {
            return origin == back ? R.drawable.ic_almacen_miel : R.drawable.ic_compracolmena;
        }
        return origin ? R.drawable.ic_almacen_miel : R.drawable.ic_compracolmena;
    }

    private void bindTripLeg(@NonNull View row, boolean back) {
        TextView leg = row.findViewById(R.id.tv_trip_leg);
        leg.setText(back ? R.string.dashboard_trip_leg_back : R.string.dashboard_trip_leg_out);
        leg.setTextColor(ContextCompat.getColor(requireContext(),
                back ? R.color.dash_bad : R.color.dash_good));
    }

    @NonNull
    private String tripLabel(@Nullable String hexId) {
        return hexId != null && !hexId.isEmpty() ? hexId : getString(R.string.dashboard_place_dest);
    }

    @NonNull
    private String placeLabel(@Nullable String label) {
        if (label == null || label.trim().isEmpty()) {
            return getString(R.string.dashboard_place_origin);
        }
        String text = label.trim();
        if ("Obrador".equals(text) || "Almacen".equals(text)) {
            return getString(R.string.map_warehouse_title);
        }
        if ("Apiario".equals(text)) {
            return getString(R.string.dashboard_place_apiary);
        }
        if ("Origen".equals(text)) {
            return getString(R.string.dashboard_place_origin);
        }
        if ("Destino".equals(text)) {
            return getString(R.string.dashboard_place_dest);
        }
        return text;
    }

    private void openMapAt(@NonNull NavController nav, double lat, double lng, @Nullable String tripId) {
        Bundle args = new Bundle();
        args.putFloat("focusLat", (float) lat);
        args.putFloat("focusLng", (float) lng);
        args.putString("focusTripId", tripId != null ? tripId : "");
        nav.navigate(R.id.mapFragment, args);
    }

    private void onProfilePhotoPicked(@Nullable Uri uri) {
        if (uri == null || !isAdded() || editProfileForm == null) {
            return;
        }
        Bitmap bmp = ProfilePhoto.squareFromUri(requireContext(), uri);
        if (bmp == null) {
            GameNotice.show(requireContext(), R.string.profile_edit_photo_fail);
            return;
        }
        pendingProfilePhoto = bmp;
        editProfileForm.ivProfileEditPhoto.setImageBitmap(bmp);
    }

    private void showEditProfileDialog(@NonNull DashboardViewModel viewModel) {
        SignedInUser u = PlayerAuth.getInstance().getCurrentUser();
        if (u == null) {
            GameNotice.show(requireContext(), R.string.profile_setup_need_login);
            return;
        }
        pendingProfilePhoto = null;
        editProfileForm = DialogEditProfileBinding.inflate(getLayoutInflater());
        String currentName = viewModel.profileName().getValue();
        if (currentName != null) {
            editProfileForm.editProfileName.setText(currentName);
        }
        Bitmap currentPhoto = viewModel.profilePhoto().getValue();
        if (currentPhoto != null) {
            editProfileForm.ivProfileEditPhoto.setImageBitmap(currentPhoto);
        } else {
            editProfileForm.ivProfileEditPhoto.setImageResource(R.drawable.ic_apicultor);
        }
        final com.apiculture.simulator.data.repository.BrandStore.Brand[] brand = {
                com.apiculture.simulator.data.repository.BrandStore.get(requireContext(), u.getUid())};
        com.apiculture.simulator.presentation.common.BrandUi.bindPicker(requireContext(),
                editProfileForm.ivProfileBrand, editProfileForm.llProfileBrandEmblems,
                editProfileForm.llProfileBrandColors, brand[0], b -> brand[0] = b);
        String lang = GameLocale.saved(requireContext());
        if ("ca".equals(lang)) {
            editProfileForm.rbLangCa.setChecked(true);
        } else if ("en".equals(lang)) {
            editProfileForm.rbLangEn.setChecked(true);
        } else if ("gl".equals(lang)) {
            editProfileForm.rbLangGl.setChecked(true);
        } else if ("eu".equals(lang)) {
            editProfileForm.rbLangEu.setChecked(true);
        } else if ("es".equals(lang)) {
            editProfileForm.rbLangEs.setChecked(true);
        } else {
            editProfileForm.rbLangSystem.setChecked(true);
        }
        editProfileForm.btnProfileLangAccept.setOnClickListener(v -> {
            int checkedId = editProfileForm.rgProfileLanguage.getCheckedRadioButtonId();
            String tag;
            if (checkedId == R.id.rb_lang_ca) {
                tag = "ca";
            } else if (checkedId == R.id.rb_lang_en) {
                tag = "en";
            } else if (checkedId == R.id.rb_lang_gl) {
                tag = "gl";
            } else if (checkedId == R.id.rb_lang_eu) {
                tag = "eu";
            } else if (checkedId == R.id.rb_lang_es) {
                tag = "es";
            } else {
                tag = "";
            }
            editProfileForm.btnProfileLangAccept.setEnabled(false);
            ApicultureApp app = (ApicultureApp) requireActivity().getApplication();
            app.getProfileRepository().saveGameLocale(u.getUid(), tag, () -> {
                if (!isAdded()) {
                    return;
                }
                GameLocale.choose(requireContext(), tag);
            }, () -> {
                if (!isAdded() || editProfileForm == null) {
                    return;
                }
                editProfileForm.btnProfileLangAccept.setEnabled(true);
                GameNotice.show(requireContext(), R.string.server_unavailable_message);
            });
        });
        Dialog dialog = new Dialog(requireContext());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(editProfileForm.getRoot());
        dialog.setCancelable(true);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        dialog.setOnDismissListener(d -> {
            editProfileForm = null;
            pendingProfilePhoto = null;
        });
        editProfileForm.btnProfilePickPhoto.setOnClickListener(v -> pickProfilePhoto.launch("image/*"));
        editProfileForm.btnProfileCancel.setOnClickListener(v -> dialog.dismiss());
        editProfileForm.btnProfileSave.setOnClickListener(v -> {
            String name = editProfileForm.editProfileName.getText() != null
                    ? editProfileForm.editProfileName.getText().toString().trim() : "";
            if (name.length() < 2) {
                GameNotice.show(requireContext(), R.string.profile_setup_fill_all);
                return;
            }
            com.apiculture.simulator.data.repository.BrandStore.save(requireContext(), u.getUid(), brand[0]);
            editProfileForm.btnProfileSave.setEnabled(false);
            ApicultureApp app = (ApicultureApp) requireActivity().getApplication();
            app.getProfileRepository().updatePlayerName(u.getUid(), name, () -> {
                if (!isAdded()) {
                    return;
                }
                if (pendingProfilePhoto != null) {
                    String b64 = ProfilePhoto.encodeJpeg(pendingProfilePhoto);
                    if (b64 == null) {
                        editProfileForm.btnProfileSave.setEnabled(true);
                        GameNotice.show(requireContext(), R.string.profile_edit_photo_fail);
                        return;
                    }
                    app.getProfileRepository().savePhotoBase64(u.getUid(), b64, () -> {
                        if (!isAdded()) {
                            return;
                        }
                        dialog.dismiss();
                        viewModel.refreshPlayerProfile(u.getUid());
                        GameNotice.showSuccess(requireContext(), R.string.profile_edit_ok);
                    }, err -> onProfileSaveError(err, editProfileForm));
                } else {
                    dialog.dismiss();
                    viewModel.refreshPlayerProfile(u.getUid());
                    GameNotice.showSuccess(requireContext(), R.string.profile_edit_ok);
                }
            }, err -> onProfileSaveError(err, editProfileForm));
        });
        dialog.show();
    }

    private void onProfileSaveError(@Nullable String err, @Nullable DialogEditProfileBinding form) {
        if (!isAdded()) {
            return;
        }
        if (form != null) {
            form.btnProfileSave.setEnabled(true);
        }
        if (ProfileRepository.ERR_NAME_TAKEN.equals(err)) {
            GameNotice.show(requireContext(), R.string.profile_setup_error_name_taken);
        } else if ("SHORT".equals(err) || "INVALID".equals(err)) {
            GameNotice.show(requireContext(), R.string.profile_setup_error_short);
        } else {
            GameNotice.show(requireContext(), R.string.profile_setup_error_generic);
        }
    }

    private static final class TripLine {
        final long remainingMs;
        @NonNull final View row;

        TripLine(long remainingMs, @NonNull View row) {
            this.remainingMs = remainingMs;
            this.row = row;
        }
    }
}
