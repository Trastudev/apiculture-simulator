package com.apiculture.simulator.presentation.hive;

import android.os.Bundle;
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
import com.apiculture.simulator.data.local.entity.HiveEntity;
import com.apiculture.simulator.data.repository.IberiaHexOverlayStore;
import com.apiculture.simulator.databinding.FragmentApiaryYardBinding;
import com.apiculture.simulator.domain.game.DailySkyCondition;
import com.apiculture.simulator.domain.game.DailyWeather;
import com.apiculture.simulator.domain.game.GameCalendar;
import com.apiculture.simulator.domain.parcel.HexParcel;
import com.apiculture.simulator.presentation.common.SimpleViewModelFactory;
import com.google.firebase.auth.FirebaseAuth;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class ApiaryYardFragment extends Fragment {

    private FragmentApiaryYardBinding binding;
    private HiveViewModel viewModel;
    private String hexId = "";
    private String parcelName = "";

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
        parcelName = args != null && args.getString("parcelName") != null
                ? args.getString("parcelName") : getString(R.string.apiaries_title);
        binding.tvYardTitle.setText(parcelName);

        ApicultureApp app = (ApicultureApp) requireActivity().getApplication();
        viewModel = new ViewModelProvider(this,
                new SimpleViewModelFactory<>(() -> new HiveViewModel(app.getHiveRepository(),
                        app.getHexParcelRepository())))
                .get(HiveViewModel.class);
        String ownerId = FirebaseAuth.getInstance().getUid();
        if (ownerId == null) {
            ownerId = "guest";
        }
        if (!"guest".equals(ownerId)) {
            viewModel.startRealtimeCloudSync(ownerId);
        }

        binding.btnYardBack.setOnClickListener(v ->
                NavHostFragment.findNavController(this).popBackStack());
        binding.btnYardBuyHive.setOnClickListener(v ->
                BuyHiveDialogs.show(this, viewModel, hexId.isEmpty() ? null : hexId));
        binding.yardView.setOnHiveTapListener(this::openHive);

        viewModel.hives(ownerId).observe(getViewLifecycleOwner(), this::bindHives);
    }

    private void bindHives(@Nullable List<HiveEntity> all) {
        if (binding == null) {
            return;
        }
        List<HiveEntity> mine = new ArrayList<>();
        if (all != null) {
            for (HiveEntity h : all) {
                if (h == null) {
                    continue;
                }
                if (hexId.isEmpty()) {
                    if (h.hexId == null || h.hexId.isEmpty()) {
                        mine.add(h);
                    }
                } else if (hexId.equals(h.hexId)) {
                    mine.add(h);
                }
            }
        }
        mine.sort(Comparator.comparing(h -> h.name != null ? h.name.toLowerCase() : ""));
        binding.yardView.setHives(mine);
        binding.tvYardEmpty.setVisibility(mine.isEmpty() ? View.VISIBLE : View.GONE);

        HiveEntity sample = mine.isEmpty() ? null : mine.get(0);
        HexParcel parcel = hexId.isEmpty() ? null : IberiaHexOverlayStore.findById(requireContext(), hexId);
        binding.yardView.setClimate(YardClimate.resolve(requireContext(), hexId, sample));
        applySky(YardClimate.yesterdaySky(hexId, sample, parcel));
        refreshObservedSky(sample, parcel);
    }

    private void applySky(@Nullable DailySkyCondition sky) {
        if (binding == null) {
            return;
        }
        DailySkyCondition next = sky != null ? sky : DailySkyCondition.SUN;
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

    private void openHive(HiveEntity hive) {
        if (hive == null || hive.id == null || !isAdded()) {
            return;
        }
        Bundle args = new Bundle();
        args.putString("hiveId", hive.id);
        NavHostFragment.findNavController(this).navigate(R.id.action_yard_to_hive_detail, args);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
