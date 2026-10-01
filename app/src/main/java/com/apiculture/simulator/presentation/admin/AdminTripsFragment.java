package com.apiculture.simulator.presentation.admin;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.apiculture.simulator.ApicultureApp;
import com.apiculture.simulator.R;
import com.apiculture.simulator.data.repository.GameServer;
import com.apiculture.simulator.databinding.FragmentAdminTripsBinding;
import com.apiculture.simulator.domain.admin.AdminRoles;
import com.apiculture.simulator.presentation.common.GameNotice;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.apiculture.simulator.data.session.PlayerAuth;
import com.apiculture.simulator.data.session.SignedInUser;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Lista los viajes del servidor y adelanta el que elija el administrador. */
public class AdminTripsFragment extends Fragment {

    private static final ExecutorService IO = Executors.newSingleThreadExecutor();

    private FragmentAdminTripsBinding binding;
    private boolean busy;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentAdminTripsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        binding.btnAdminBack.setOnClickListener(v ->
                Navigation.findNavController(v).popBackStack());
        SignedInUser user = PlayerAuth.getInstance().getCurrentUser();
        if (user == null) {
            GameNotice.show(requireContext(), R.string.admin_not_allowed);
            Navigation.findNavController(view).popBackStack();
            return;
        }
        ApicultureApp app = (ApicultureApp) requireActivity().getApplication();
        app.getProfileRepository().fetchDisplayProfile(user.getUid(), profile -> {
            if (!isAdded() || binding == null) {
                return;
            }
            if (!AdminRoles.isAdminPlayerName(profile.playerName)) {
                GameNotice.show(requireContext(), R.string.admin_not_allowed);
                Navigation.findNavController(binding.getRoot()).popBackStack();
                return;
            }
            reload();
        });
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }

    private void reload() {
        if (binding == null || busy) {
            return;
        }
        binding.pbAdminTrips.setVisibility(View.VISIBLE);
        binding.tvAdminTripsEmpty.setVisibility(View.GONE);
        IO.execute(() -> {
            JSONObject payload = GameServer.adminLiveTrips();
            if (!isAdded()) {
                return;
            }
            requireActivity().runOnUiThread(() -> showTrips(payload));
        });
    }

    private void showTrips(@Nullable JSONObject payload) {
        if (binding == null) {
            return;
        }
        binding.pbAdminTrips.setVisibility(View.GONE);
        binding.llAdminTrips.removeAllViews();
        JSONArray trips = payload != null ? payload.optJSONArray("trips") : null;
        if (trips == null || trips.length() == 0) {
            binding.tvAdminTripsEmpty.setVisibility(View.VISIBLE);
            if (payload == null) {
                binding.tvAdminTripsEmpty.setText(R.string.admin_trips_fail);
            } else {
                binding.tvAdminTripsEmpty.setText(R.string.admin_trips_empty);
            }
            return;
        }
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (int i = 0; i < trips.length(); i++) {
            JSONObject trip = trips.optJSONObject(i);
            if (trip == null) {
                continue;
            }
            View row = inflater.inflate(R.layout.item_admin_trip, binding.llAdminTrips, false);
            TextView title = row.findViewById(R.id.tv_admin_trip_title);
            TextView route = row.findViewById(R.id.tv_admin_trip_route);
            TextView meta = row.findViewById(R.id.tv_admin_trip_meta);
            MaterialButton finish = row.findViewById(R.id.btn_admin_trip_finish);
            String player = trip.optString("playerName", "");
            title.setText(getString(R.string.admin_trips_line, kindLabel(trip),
                    player.isEmpty() ? trip.optString("ownerId", "") : player));
            String origin = trip.optString("origin", "");
            String dest = trip.optString("dest", "");
            route.setText(getString(R.string.admin_trips_route, origin, dest));
            String phase = "return".equals(trip.optString("phase"))
                    ? getString(R.string.admin_trips_phase_return)
                    : getString(R.string.admin_trips_phase_out);
            double kg = trip.optDouble("kg", 0);
            String flora = trip.optString("flora", "");
            if (kg > 1e-6 && !flora.isEmpty()) {
                meta.setText(phase + " · " + getString(R.string.admin_trips_cargo, kg, flora));
            } else {
                meta.setText(phase);
            }
            String kind = trip.optString("kind", "");
            boolean goingHome = "return".equals(trip.optString("phase"));
            String id = trip.optString("id", "");
            finish.setOnClickListener(v -> confirm(id, kind, goingHome));
            binding.llAdminTrips.addView(row);
        }
    }

    private void confirm(@NonNull String tripId, @NonNull String kind, boolean goingHome) {
        if (tripId.isEmpty() || busy || binding == null) {
            return;
        }
        int message = "hive".equals(kind)
                ? R.string.admin_trips_confirm_hive
                : (goingHome ? R.string.admin_trips_confirm_home : R.string.admin_trips_confirm_work);
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.admin_trips_confirm_title)
                .setMessage(message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.admin_trips_finish, (d, w) -> finish(tripId, kind))
                .show();
    }

    private void finish(@NonNull String tripId, @NonNull String kind) {
        if (binding == null) {
            return;
        }
        busy = true;
        binding.pbAdminTrips.setVisibility(View.VISIBLE);
        IO.execute(() -> {
            JSONObject result = GameServer.adminFinishTrip(tripId);
            if (result != null) {
                GameServer.syncBlocking(requireContext().getApplicationContext());
            }
            if (!isAdded()) {
                busy = false;
                return;
            }
            requireActivity().runOnUiThread(() -> {
                busy = false;
                if (!isAdded() || binding == null) {
                    return;
                }
                if (result == null) {
                    GameNotice.show(requireContext(), R.string.admin_trips_fail);
                } else if ("hive".equals(kind)) {
                    GameNotice.showSuccess(requireContext(), R.string.admin_trips_ok_hive);
                } else if (result.optBoolean("finished")) {
                    GameNotice.showSuccess(requireContext(), R.string.admin_trips_ok_home);
                } else {
                    GameNotice.showSuccess(requireContext(), R.string.admin_trips_ok_return);
                }
                reload();
            });
        });
    }

    @NonNull
    private String kindLabel(@NonNull JSONObject trip) {
        String kind = trip.optString("kind", "");
        String role = trip.optString("legRole", "");
        if ("haul-ship".equals(role)) {
            return getString(R.string.admin_trips_kind_ship);
        }
        if ("collect".equals(kind)) {
            return getString(R.string.admin_trips_kind_collect);
        }
        if ("wholesale".equals(kind)) {
            return getString(R.string.admin_trips_kind_wholesale);
        }
        if ("order".equals(kind)) {
            return getString(R.string.admin_trips_kind_order);
        }
        if ("transfer".equals(kind)) {
            return getString(R.string.admin_trips_kind_transfer);
        }
        if ("hive".equals(kind)) {
            return getString(R.string.admin_trips_kind_hive);
        }
        return getString(R.string.admin_trips_kind_other);
    }
}
