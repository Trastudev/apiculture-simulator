package com.apiculture.simulator.presentation.hive;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.apiculture.simulator.ApicultureApp;
import com.apiculture.simulator.R;
import com.apiculture.simulator.data.local.entity.HexParcelOwnershipEntity;
import com.apiculture.simulator.data.local.entity.HiveEntity;
import com.apiculture.simulator.data.repository.HexParcelRepository;
import com.apiculture.simulator.data.repository.IberiaHexOverlayStore;
import com.apiculture.simulator.data.repository.TruckLiveTrips;
import com.apiculture.simulator.databinding.FragmentApiaryYardBinding;
import com.apiculture.simulator.domain.game.DailySkyCondition;
import com.apiculture.simulator.domain.game.DailyWeather;
import com.apiculture.simulator.domain.game.GameCalendar;
import com.apiculture.simulator.domain.game.NpcContractCatalog;
import com.apiculture.simulator.domain.game.TranshumanceRules;
import com.apiculture.simulator.domain.parcel.HexApiary;
import com.apiculture.simulator.domain.parcel.HexParcel;
import com.apiculture.simulator.presentation.common.GameNotice;
import com.apiculture.simulator.presentation.common.SimpleViewModelFactory;
import com.apiculture.simulator.presentation.map.SharedMapFragment;
import com.apiculture.simulator.presentation.market.NpcPortraitUi;
import com.apiculture.simulator.presentation.profile.ProfilePhoto;
import com.apiculture.simulator.presentation.tutorial.TutorialBus;
import com.apiculture.simulator.data.session.PlayerAuth;
import com.apiculture.simulator.unity.Apiary3DActivity;
import com.apiculture.simulator.unity.UnityBridge;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

public class ApiaryYardFragment extends Fragment {

    private FragmentApiaryYardBinding binding;
    private HiveViewModel viewModel;
    private String hexId = "";
    private String siteId = "";
    private String parcelName = "";
    private boolean contractYard;
    private boolean visitYard;
    private String visitOwnerId = "";
    private String npcName = "";
    private int portraitIndex;
    private List<HiveEntity> lastHives = Collections.emptyList();
    private List<HiveEntity> yardHives = Collections.emptyList();
    private List<HexParcelOwnershipEntity> lastSites = Collections.emptyList();
    private Set<String> lastTravelingHiveIds = Collections.emptySet();
    private String sessionOwnerId = "";
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private final Runnable bindDebounced = () -> bindHivesNow(lastHives);
    @Nullable
    private String lastYardFp;
    private DailySkyCondition currentSky = DailySkyCondition.SUN;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentApiaryYardBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Bundle args = getArguments();
        hexId = args != null && args.getString("hexId") != null ? args.getString("hexId") : "";
        siteId = args != null && args.getString("siteId") != null ? args.getString("siteId") : "";
        parcelName = args != null && args.getString("parcelName") != null
                ? args.getString("parcelName") : getString(R.string.apiaries_title);
        contractYard = args != null && args.getBoolean("contractYard", false);
        visitOwnerId = args != null && args.getString("visitOwnerId") != null
                ? args.getString("visitOwnerId") : "";
        visitYard = !visitOwnerId.isEmpty();
        npcName = args != null && args.getString("npcName") != null ? args.getString("npcName") : "";
        portraitIndex = args != null ? args.getInt("portraitIndex", 0) : 0;
        if (!visitYard && contractYard && !hexId.isEmpty()
                && (npcName == null || npcName.isEmpty())) {
            HexParcel parcel = IberiaHexOverlayStore.findById(requireContext(), hexId);
            npcName = NpcContractCatalog.npcNameFor(parcel);
            portraitIndex = NpcContractCatalog.portraitIndexFor(npcName);
            if (parcelName == null || parcelName.isEmpty()
                    || parcelName.equals(getString(R.string.apiaries_title))) {
                parcelName = NpcContractCatalog.estateNameFor(parcel);
            }
        }
        binding.tvYardTitle.setText(parcelName);
        if (!visitYard && !contractYard && hexId != null && !hexId.isEmpty()) {
            binding.tvYardTitle.setOnClickListener(v -> promptRename());
        }
        if (visitYard) {
            bindVisitHeader();
        } else if (contractYard) {
            binding.ivYardNpcFace.setVisibility(View.VISIBLE);
            binding.ivYardNpcFace.setImageResource(NpcPortraitUi.faceDrawable(portraitIndex));
            binding.btnYardBuyHive.setText(R.string.apiary_yard_transhume);
            binding.tvYardEmpty.setText(R.string.apiary_yard_contract_empty);
        } else {
            binding.ivYardNpcFace.setVisibility(View.GONE);
            binding.btnYardBuyHive.setText(R.string.apiary_yard_buy_hive);
            binding.tvYardEmpty.setText(R.string.apiary_yard_empty);
        }

        ApicultureApp app = (ApicultureApp) requireActivity().getApplication();
        viewModel = new ViewModelProvider(this,
                new SimpleViewModelFactory<>(() -> new HiveViewModel(app.getHiveRepository(),
                        app.getHexParcelRepository())))
                .get(HiveViewModel.class);
        String ownerId = PlayerAuth.getInstance().getUid();
        if (ownerId == null) {
            ownerId = "guest";
        }
        sessionOwnerId = ownerId;
        if (!"guest".equals(ownerId)) {
            viewModel.startHexParcelCloudSync();
            viewModel.startRealtimeCloudSync(ownerId);
        }

        binding.btnYardBack.setOnClickListener(v -> {
            if (TutorialBus.firstHivePath()) {
                return;
            }
            NavHostFragment.findNavController(this).popBackStack();
        });
        binding.btnYard3d.setOnClickListener(v -> open3d());
        binding.tvYardMaintBanner.setOnClickListener(v -> open3d());
        binding.btnYardBuyHive.setOnClickListener(v -> {
            if (TutorialBus.firstHiveOpen()) {
                return;
            }
            if (contractYard) {
                openTranshumanceMap();
            } else {
                BuyHiveDialogs.show(this, viewModel, hexId.isEmpty() ? null : hexId,
                        siteId.isEmpty() ? null : siteId);
            }
        });
        if (visitYard) {
            binding.yardView.setOnHiveTapListener(null);
            binding.yardView.setOnHiveCareListener(null);
            viewModel.loadVisitYardHives(visitOwnerId, hexId, list -> {
                if (!isAdded() || binding == null) {
                    return;
                }
                lastHives = list != null ? list : Collections.emptyList();
                lastYardFp = null;
                scheduleBindHives();
            });
        } else if (TutorialBus.firstHivePath() && !TutorialBus.firstHiveOpen()) {
            binding.yardView.setOnHiveTapListener(this::openHive);
            binding.yardView.setOnHiveCareListener(null);
        } else {
            binding.yardView.setOnHiveTapListener(this::openHive);
            if (TutorialBus.firstHiveOpen()) {
                binding.yardView.setOnHiveCareListener(null);
            } else {
                binding.yardView.setOnHiveCareListener(new ApiaryYardView.OnHiveCareListener() {
                    @Override
                    public void onTreatBadge(@NonNull HiveEntity hive) {
                        HiveCareDialogs.showTreat(ApiaryYardFragment.this, hive, viewModel,
                                ApiaryYardFragment.this::openShop);
                    }

                    @Override
                    public void onFeedBadge(@NonNull HiveEntity hive) {
                        HiveCareDialogs.showFeed(ApiaryYardFragment.this, hive, viewModel,
                                ApiaryYardFragment.this::openShop);
                    }
                });
            }
        }

        viewModel.hives(ownerId).observe(getViewLifecycleOwner(), all -> {
            if (visitYard) {
                return;
            }
            lastHives = all != null ? all : Collections.emptyList();
            scheduleBindHives();
        });
        TruckLiveTrips.observe(requireContext()).observe(getViewLifecycleOwner(), trips -> {
            lastTravelingHiveIds = TruckLiveTrips.activeHiveIds(trips);
            scheduleBindHives();
        });
        viewModel.hexOwnerships().observe(getViewLifecycleOwner(), rows -> {
            List<HexParcelOwnershipEntity> mine = new ArrayList<>();
            if (rows != null && !hexId.isEmpty()) {
                for (HexParcelOwnershipEntity r : rows) {
                    String siteOwner = visitYard ? visitOwnerId : sessionOwnerId;
                    if (r != null && hexId.equals(r.hexId) && siteOwner.equals(r.ownerId)) {
                        mine.add(r);
                    }
                }
            }
            lastSites = mine;
            scheduleBindHives();
        });
    }

    private void openTranshumanceMap() {
        if (!isAdded()) {
            return;
        }
        // Capítulo 4. Transhumancia.
        com.apiculture.simulator.presentation.tutorial.TutorialBus.emit(
                com.apiculture.simulator.presentation.tutorial.TutorialEvent.TRANSHUMANCE);
        Bundle args = new Bundle();
        args.putBoolean(SharedMapFragment.ARG_HINT_TRANSHUMANCE, true);
        NavHostFragment.findNavController(this).navigate(R.id.mapFragment, args);
    }

    private void open3d() {
        if (!isAdded() || yardHives.isEmpty()) {
            return;
        }
        String apiaryId = hexId.isEmpty() ? "home" : hexId + (siteId.isEmpty() ? "" : "/" + siteId);
        if (UnityBridge.isStungOutToday(requireContext(), sessionOwnerId, apiaryId)) {
            GameNotice.show(requireContext(), R.string.apiary_3d_stung_locked);
            return;
        }
        YardClimate climate = YardClimate.resolve(requireContext(), hexId, yardHives.get(0));
        UnityBridge.prepare(requireContext(), sessionOwnerId, apiaryId, siteId, parcelName, climate.name(),
                currentSky.name(), yardHives, ownFarm() ? hexId : null);
        Apiary3DActivity.open(requireContext());
    }

    /** Terreno propio: el payés siembra y cuida sus cultivos. */
    private boolean ownFarm() {
        return !visitYard && !contractYard && hexId != null && !hexId.isEmpty();
    }

    private void refreshMaintenanceBanner() {
        if (binding == null || !ownFarm() || "guest".equals(sessionOwnerId)) {
            return;
        }
        ApicultureApp app = (ApicultureApp) requireActivity().getApplication();
        final String yardHex = hexId;
        app.getHiveRepository().hexesNeedingMaintenanceAsync(sessionOwnerId, hexes -> {
            if (!isAdded() || binding == null || !yardHex.equals(hexId)) {
                return;
            }
            binding.tvYardMaintBanner.setVisibility(hexes.contains(yardHex) ? View.VISIBLE : View.GONE);
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshMaintenanceBanner();
    }

    private void scheduleBindHives() {
        uiHandler.removeCallbacks(bindDebounced);
        uiHandler.postDelayed(bindDebounced, 80);
    }

    private void bindHivesNow(@Nullable List<HiveEntity> all) {
        if (binding == null) {
            return;
        }
        lastHives = all != null ? all : Collections.emptyList();
        String fp = yardFingerprint();
        if (fp.equals(lastYardFp)) {
            return;
        }
        lastYardFp = fp;
        int today = GameCalendar.currentCivilDayKey();
        List<HiveEntity> mine = new ArrayList<>();
        if (all != null) {
            for (HiveEntity h : all) {
                if (h == null || leftThisYard(h, today)) {
                    continue;
                }
                if (hexId.isEmpty()) {
                    if (!h.inWarehouse && (h.hexId == null || h.hexId.isEmpty())) {
                        mine.add(h);
                    }
                } else if (hexId.equals(h.hexId) && !h.inWarehouse) {
                    if (siteId.isEmpty() || belongsToThisSite(h)) {
                        mine.add(h);
                    }
                }
            }
        }
        mine.sort(Comparator.comparing(h -> h.name != null ? h.name.toLowerCase() : ""));
        binding.yardView.setHives(mine);
        binding.tvYardEmpty.setVisibility(mine.isEmpty() ? View.VISIBLE : View.GONE);
        yardHives = mine;
        boolean can3d = !visitYard && !mine.isEmpty() && !TutorialBus.firstHivePath();
        binding.btnYard3d.setVisibility(can3d ? View.VISIBLE : View.GONE);

        HiveEntity sample = mine.isEmpty() ? null : mine.get(0);
        HexParcel parcel = hexId.isEmpty() ? null : IberiaHexOverlayStore.findById(requireContext(), hexId);
        binding.yardView.setClimate(YardClimate.resolve(requireContext(), hexId, sample));
        applySky(YardClimate.yesterdaySky(hexId, sample, parcel));
        refreshObservedSky(sample, parcel);
    }

    @NonNull
    private String yardFingerprint() {
        StringBuilder sb = new StringBuilder();
        sb.append(hexId).append('|').append(siteId).append('|');
        for (HiveEntity h : lastHives) {
            if (h == null || h.id == null) {
                continue;
            }
            sb.append(h.id).append(',')
                    .append(h.hexId).append(',')
                    .append(h.siteId).append(',')
                    .append(h.inWarehouse).append(',')
                    .append(h.pendingContractHexId).append(',')
                    .append(h.pendingContractDayKey).append(',')
                    .append(h.transhumanceArrivesDayKey).append(';');
        }
        sb.append('#');
        for (String id : lastTravelingHiveIds) {
            sb.append(id).append(',');
        }
        sb.append('#');
        for (HexParcelOwnershipEntity s : lastSites) {
            if (s != null) {
                sb.append(s.siteId).append(',');
            }
        }
        return sb.toString();
    }

    private boolean leftThisYard(@NonNull HiveEntity h, int today) {
        if (!visitYard && h.id != null && lastTravelingHiveIds.contains(h.id)) {
            return true;
        }
        return TranshumanceRules.isInTransit(h, today);
    }

    private boolean belongsToThisSite(@NonNull HiveEntity h) {
        HexParcel parcel = IberiaHexOverlayStore.findById(requireContext(), hexId);
        return HexApiary.hiveOnSite(h, hexId, siteId, parcel, lastSites);
    }

    private void applySky(@Nullable DailySkyCondition sky) {
        if (binding == null) {
            return;
        }
        DailySkyCondition next = sky != null ? sky : DailySkyCondition.SUN;
        currentSky = next;
        binding.yardView.setSky(next);
        binding.ivYardWeather.setImageResource(weatherIcon(next));
    }

    private void refreshObservedSky(@Nullable HiveEntity sample, @Nullable HexParcel parcel) {
        double[] ll = YardClimate.weatherLatLng(parcel, sample);
        if (ll == null) {
            return;
        }
        ApicultureApp app = (ApicultureApp) requireActivity().getApplication();
        final String yardHex = hexId;
        new Thread(() -> {
            LocalDate yesterday = LocalDate.now(GameCalendar.userTimeZone()).minusDays(1);
            DailyWeather observed = app.getWeatherRepository()
                    .fetchCalendarDayWeatherBlocking(ll[0], ll[1], yesterday);
            if (!isAdded() || binding == null) {
                return;
            }
            DailySkyCondition sky = YardClimate.yesterdaySky(yardHex, sample, parcel, observed);
            requireActivity().runOnUiThread(() -> {
                if (!isAdded() || binding == null || !yardHex.equals(hexId)) {
                    return;
                }
                applySky(sky);
            });
        }, "yard-sky").start();
    }

    @DrawableRes
    private static int weatherIcon(DailySkyCondition sky) {
        if (sky == null) {
            return R.drawable.ic_sol_prado;
        }
        switch (sky) {
            case CLOUDY:
                return R.drawable.ic_weather_cloud;
            case VARIABLE:
                return R.drawable.ic_weather_variable;
            case WINDY:
                return R.drawable.ic_weather_wind;
            case RAINY:
                return R.drawable.ic_weather_rain;
            case SUN:
            default:
                return R.drawable.ic_sol_prado;
        }
    }

    private void promptRename() {
        String uid = com.apiculture.simulator.data.session.PlayerAuth.getInstance().getUid();
        if (uid == null || uid.isEmpty() || !isAdded()) {
            return;
        }
        ApiaryNameDialog.show(this, parcelName, name -> {
            ApicultureApp app = (ApicultureApp) requireActivity().getApplication();
            app.getHexParcelRepository().renameApiary(hexId, uid, siteId, name, msg -> {
                if (!isAdded() || binding == null) {
                    return;
                }
                if (msg != null) {
                    com.apiculture.simulator.presentation.common.GameNotice.show(requireContext(), msg);
                    return;
                }
                parcelName = name;
                binding.tvYardTitle.setText(name);
            });
        });
    }

    private void bindVisitHeader() {
        binding.btnYardBuyHive.setVisibility(View.GONE);
        binding.tvYardTitle.setMaxLines(2);
        binding.ivYardNpcFace.setVisibility(View.VISIBLE);
        int facePx = (int) (44f * getResources().getDisplayMetrics().density);
        ViewGroup.LayoutParams faceLp = binding.ivYardNpcFace.getLayoutParams();
        faceLp.width = facePx;
        faceLp.height = facePx;
        binding.ivYardNpcFace.setLayoutParams(faceLp);
        binding.ivYardNpcFace.setImageResource(R.drawable.ic_apicultor);
        binding.tvYardEmpty.setText(R.string.apiary_yard_visit_empty);
        String place = parcelName != null ? parcelName.trim() : "";
        if (place.regionMatches(true, 0, "Apiario ", 0, "Apiario ".length())) {
            place = place.substring("Apiario ".length()).trim();
        }
        if (place.isEmpty()) {
            place = hexId;
        }
        final String apiaryName = place;
        String known = HexParcelRepository.playerNameFor(visitOwnerId);
        if (known != null && !known.isEmpty()) {
            binding.tvYardTitle.setText(getString(R.string.apiary_yard_visit_title, apiaryName, known));
        } else {
            binding.tvYardTitle.setText(apiaryName);
        }
        ApicultureApp app = (ApicultureApp) requireActivity().getApplication();
        app.getProfileRepository().fetchDisplayProfile(visitOwnerId, profile -> {
            if (!isAdded() || binding == null || profile == null) {
                return;
            }
            String who = profile.playerName != null ? profile.playerName.trim() : "";
            if (who.isEmpty()) {
                who = known != null ? known : "";
            }
            if (!who.isEmpty()) {
                HexParcelRepository.rememberPlayerName(visitOwnerId, who);
                binding.tvYardTitle.setText(getString(R.string.apiary_yard_visit_title, apiaryName, who));
            }
            Bitmap face = ProfilePhoto.decodeBase64(profile.photoBase64);
            if (face != null) {
                binding.ivYardNpcFace.setImageBitmap(face);
            }
        });
    }

    private void openShop() {
        if (!isAdded()) {
            return;
        }
        NavHostFragment.findNavController(this).navigate(R.id.shopFragment);
    }

    /** Capítulo 1, viñeta 11. Reactiva el toque si el prado se abrió en el paso anterior. */
    public void guideFirstHive() {
        if (binding == null || visitYard) {
            return;
        }
        binding.yardView.setOnHiveTapListener(this::openHive);
        binding.yardView.setOnHiveCareListener(null);
        binding.yardView.setGuideFirstHive(true);
        binding.yardView.invalidate();
    }

    private void openHive(HiveEntity hive) {
        if (visitYard || hive == null || hive.id == null || !isAdded()) {
            return;
        }
        if (TutorialBus.firstHivePath() && !TutorialBus.firstHiveOpen()) {
            return;
        }
        Bundle args = new Bundle();
        args.putString("hiveId", hive.id);
        NavHostFragment.findNavController(this).navigate(R.id.action_yard_to_hive_detail, args);
    }

    @Override
    public void onDestroyView() {
        uiHandler.removeCallbacks(bindDebounced);
        super.onDestroyView();
        binding = null;
    }
}
