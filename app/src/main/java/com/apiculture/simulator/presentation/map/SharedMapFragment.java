package com.apiculture.simulator.presentation.map;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.apiculture.simulator.ApicultureApp;
import com.apiculture.simulator.R;
import com.apiculture.simulator.data.local.entity.HiveEntity;
import com.apiculture.simulator.data.repository.HexParcelRepository;
import com.apiculture.simulator.data.repository.IberiaHexOverlayStore;
import com.apiculture.simulator.databinding.DialogHexPurchaseBinding;
import com.apiculture.simulator.databinding.FragmentSharedMapBinding;
import com.apiculture.simulator.domain.parcel.BoundingBox;
import com.apiculture.simulator.domain.parcel.HexParcel;
import com.apiculture.simulator.domain.parcel.HexParcelGameRules;
import com.apiculture.simulator.domain.parcel.HexParcelResolve;
import com.apiculture.simulator.domain.parcel.IberiaBounds;
import com.apiculture.simulator.presentation.common.SimpleViewModelFactory;
import com.apiculture.simulator.presentation.hive.HiveViewModel;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.gms.maps.GoogleMap.OnMarkerClickListener;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polygon;
import com.google.android.gms.maps.model.PolygonOptions;
import com.google.firebase.auth.FirebaseAuth;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class SharedMapFragment extends Fragment implements OnMapReadyCallback, OnMarkerClickListener {

    /** Opacidad relleno ~50&nbsp;% (ARGB: 128/255). */
    private static final int HEX_FILL_ALPHA = 128;
    private static final int HEX_FREE_FILL = Color.argb(HEX_FILL_ALPHA, 129, 199, 132);
    private static final int HEX_FREE_STROKE = Color.argb(200, 56, 142, 60);
    private static final int HEX_OWN_FILL = Color.argb(HEX_FILL_ALPHA, 255, 152, 0);
    private static final int HEX_OWN_STROKE = Color.argb(220, 230, 120, 0);
    private static final int HEX_OTHER_FILL = Color.argb(HEX_FILL_ALPHA, 66, 165, 245);
    private static final int HEX_OTHER_STROKE = Color.argb(220, 25, 118, 210);
    private static final float HEX_STROKE_WIDTH = 1f;
    /**
     * Por debajo no se dibujan hexágonos.
     * Valor bajo = más zoom out; ~2 niveles más permisivo que antes para ver la península.
     */
    private static final float MIN_ZOOM_HEX = 6.5f;
    /** Máximo de hex dibujados; se eligen los más cercanos al centro de pantalla. */
    private static final int MAX_HEX_VISIBLE = 500;
    private static final float ZOOM_FULL_PENINSULA_HEX = 8f;

    private FragmentSharedMapBinding binding;
    private GoogleMap googleMap;
    private HiveViewModel hiveViewModel;
    private String currentUserId = "guest";
    private final Map<String, HiveEntity> hiveById = new HashMap<>();
    private String selectedOwnHiveId;
    private final List<Polygon> hexOverlayPolygons = new ArrayList<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private ExecutorService hexOverlayExecutor;
    private final AtomicInteger hexOverlayGeneration = new AtomicInteger(0);

    private final ActivityResultLauncher<String> locationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted && googleMap != null) enableMyLocation();
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        hexOverlayExecutor = Executors.newSingleThreadExecutor();
        binding = FragmentSharedMapBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ApicultureApp app = (ApicultureApp) requireActivity().getApplication();
        hiveViewModel = new ViewModelProvider(this,
                new SimpleViewModelFactory<>(() -> new HiveViewModel(app.getHiveRepository(),
                        app.getHexParcelRepository())))
                .get(HiveViewModel.class);
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid != null) currentUserId = uid;

        hiveViewModel.hexOwnerships().observe(getViewLifecycleOwner(), rows -> refreshHexParcelOverlay());

        SupportMapFragment mapFragment = (SupportMapFragment)
                getChildFragmentManager().findFragmentById(R.id.map_container);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }
    }

    @Override
    public void onMapReady(@NonNull GoogleMap map) {
        googleMap = map;
        googleMap.setOnMarkerClickListener(this);
        googleMap.setOnCameraIdleListener(this::refreshHexParcelOverlay);
        googleMap.setOnPolygonClickListener(this::onHexPolygonClick);
        enableMyLocation();
        observeHivesAndRenderMarkers();
        setupMapInteractions();
    }

    private void observeHivesAndRenderMarkers() {
        hiveViewModel.allHives().observe(getViewLifecycleOwner(), hives -> {
            googleMap.clear();
            hexOverlayPolygons.clear();
            hiveById.clear();
            for (HiveEntity hive : hives) {
                LatLng position = new LatLng(hive.lat, hive.lng);
                boolean isOwn = currentUserId.equals(hive.ownerId);
                Marker marker = googleMap.addMarker(new MarkerOptions()
                        .position(position)
                        .title(hive.name)
                        .icon(BitmapDescriptorFactory.defaultMarker(
                                isOwn ? BitmapDescriptorFactory.HUE_ORANGE : BitmapDescriptorFactory.HUE_AZURE))
                        .snippet("Miel/día: " + String.format("%.2f", hive.honeyProduction) + "kg"));
                if (marker != null) marker.setTag(hive.id);
                hiveById.put(hive.id, hive);
            }
            moveCameraToOwnOrFirstHive(hives);
            refreshHexParcelOverlay();
        });
    }

    private void refreshHexParcelOverlay() {
        if (googleMap == null || !isAdded() || hexOverlayExecutor == null) {
            return;
        }
        for (Polygon p : hexOverlayPolygons) {
            p.remove();
        }
        hexOverlayPolygons.clear();
        final float zoom = googleMap.getCameraPosition().zoom;
        if (zoom < MIN_ZOOM_HEX) {
            return;
        }
        LatLngBounds vb = googleMap.getProjection().getVisibleRegion().latLngBounds;
        LatLng sw = vb.southwest;
        LatLng ne = vb.northeast;
        double latPad = Math.max(1e-6, (ne.latitude - sw.latitude) * 0.02);
        double lonPad = Math.max(1e-6, (ne.longitude - sw.longitude) * 0.02);
        BoundingBox gen = new BoundingBox(
                sw.latitude + latPad,
                ne.latitude - latPad,
                sw.longitude + lonPad,
                ne.longitude - lonPad);
        final BoundingBox clipped = clipBoundingBoxForHexDraw(gen, zoom);
        final BoundingBox viewInIberia = clipped.intersect(IberiaBounds.BOX);
        if (viewInIberia == null) {
            return;
        }
        final int maxHex = maxHexForZoom(zoom);
        final LatLng focus = googleMap.getCameraPosition().target;
        final int generation = hexOverlayGeneration.incrementAndGet();
        final GoogleMap mapWhenScheduled = googleMap;
        final Context appCtx = requireContext().getApplicationContext();
        final String uidForHex = currentUserId;
        /** Con colmena elegida para transhumancia, los hex no interceptan el toque: el mapa recibe el destino. */
        final boolean hexPolygonsClickable = selectedOwnHiveId == null;

        hexOverlayExecutor.execute(() -> {
            List<HexParcel> all = IberiaHexOverlayStore.getParcels(appCtx);
            List<HexParcel> parcels = IberiaHexOverlayStore.visibleInViewport(
                    all, viewInIberia, maxHex, focus.latitude, focus.longitude);
            Map<String, String> ownership =
                    ((ApicultureApp) appCtx).getHexParcelRepository().getOwnershipMapSync();
            List<Pair<PolygonOptions, String>> specs = new ArrayList<>(parcels.size());
            for (int i = 0; i < parcels.size(); i++) {
                HexParcel parcel = parcels.get(i);
                String o = ownership.get(parcel.id);
                int fill;
                int stroke;
                if (o == null) {
                    fill = HEX_FREE_FILL;
                    stroke = HEX_FREE_STROKE;
                } else if (uidForHex.equals(o)) {
                    fill = HEX_OWN_FILL;
                    stroke = HEX_OWN_STROKE;
                } else {
                    fill = HEX_OTHER_FILL;
                    stroke = HEX_OTHER_STROKE;
                }
                specs.add(new Pair<>(
                        hexPolygonOptions(parcel, fill, stroke, hexPolygonsClickable), parcel.id));
            }
            final List<Pair<PolygonOptions, String>> toDraw = specs;
            mainHandler.post(() -> {
                if (getView() == null || !isAdded() || googleMap == null || googleMap != mapWhenScheduled
                        || generation != hexOverlayGeneration.get()) {
                    return;
                }
                for (Polygon p : hexOverlayPolygons) {
                    p.remove();
                }
                hexOverlayPolygons.clear();
                for (Pair<PolygonOptions, String> spec : toDraw) {
                    Polygon poly = googleMap.addPolygon(spec.first);
                    poly.setTag(spec.second);
                    hexOverlayPolygons.add(poly);
                }
            });
        });
    }

    /**
     * Con zoom amplio no recortamos el viewport al centro: así puede mostrarse toda la península.
     * Con zoom cercano sigue un tope en grados para no pasar trabajo innecesario al filtro.
     */
    private BoundingBox clipBoundingBoxForHexDraw(BoundingBox gen, float zoom) {
        if (zoom <= ZOOM_FULL_PENINSULA_HEX) {
            return gen;
        }
        double latSpan = gen.maxLat - gen.minLat;
        double lonSpan = gen.maxLon - gen.minLon;
        double maxSpan = Math.max(0.35, Math.min(4.6, 36.0 / Math.max(zoom, MIN_ZOOM_HEX)));
        if (latSpan <= maxSpan && lonSpan <= maxSpan) {
            return gen;
        }
        double cLat = gen.centerLat();
        double cLon = gen.centerLon();
        double half = maxSpan / 2.0;
        return new BoundingBox(cLat - half, cLat + half, cLon - half, cLon + half);
    }

    /** Nunca supera {@link #MAX_HEX_VISIBLE}; zoom alto puede pedir menos trabajo de ordenación. */
    private static int maxHexForZoom(float zoom) {
        if (zoom <= ZOOM_FULL_PENINSULA_HEX) {
            return MAX_HEX_VISIBLE;
        }
        float t = zoom - ZOOM_FULL_PENINSULA_HEX;
        int n = (int) (120 + t * 55);
        return Math.min(MAX_HEX_VISIBLE, Math.max(80, n));
    }

    private static LatLng[] latLngRing(HexParcel parcel) {
        double[][] poly = parcel.polygonLatLon;
        LatLng[] ring = new LatLng[poly.length + 1];
        for (int i = 0; i < poly.length; i++) {
            ring[i] = new LatLng(poly[i][0], poly[i][1]);
        }
        ring[poly.length] = ring[0];
        return ring;
    }

    private static PolygonOptions hexPolygonOptions(
            HexParcel parcel, int fillColor, int strokeColor, boolean clickable) {
        return new PolygonOptions()
                .add(latLngRing(parcel))
                .strokeWidth(HEX_STROKE_WIDTH)
                .strokeColor(strokeColor)
                .fillColor(fillColor)
                .clickable(clickable);
    }

    private void onHexPolygonClick(Polygon polygon) {
        Object tag = polygon.getTag();
        if (!(tag instanceof String)) {
            return;
        }
        final String hexId = (String) tag;
        ApicultureApp app = (ApicultureApp) requireContext().getApplicationContext();
        hexOverlayExecutor.execute(() -> {
            String owner = app.getHexParcelRepository().getOwnerSync(hexId);
            final String floraKey = app.getHexFloraRepository().getOrCreateFloraForHexBlocking(hexId);
            final String parcelNameLine = app.getHexParcelRepository().getParcelDisplayNameSync(hexId);
            int usedHives = app.getHiveRepository().countHivesOnHexBlocking(hexId);
            int remainingSlots = Math.max(0, HexParcelGameRules.MAX_HIVES_PER_HEX - usedHives);
            mainHandler.post(() -> {
                if (!isAdded()) {
                    return;
                }
                if (owner != null && owner.equals(currentUserId)) {
                    String floraLine = floraLabelForUi(floraKey);
                    new MaterialAlertDialogBuilder(requireContext())
                            .setTitle(R.string.hex_own_parcel_title)
                            .setMessage(getString(R.string.hex_own_parcel_message,
                                    parcelNameLine, remainingSlots, floraLine))
                            .setPositiveButton(android.R.string.ok, null)
                            .show();
                    return;
                }
                if (owner != null) {
                    Toast.makeText(requireContext(), R.string.hex_owned_by_other, Toast.LENGTH_SHORT).show();
                    return;
                }
                confirmPurchaseHex(hexId, floraKey);
            });
        });
    }

    private String floraLabelForUi(String floraKey) {
        if (floraKey == null || floraKey.isEmpty()) {
            return "—";
        }
        switch (floraKey.toLowerCase(Locale.ROOT)) {
            case "romero":
                return getString(R.string.flora_display_romero);
            case "tomillo":
                return getString(R.string.flora_display_tomillo);
            case "bosque":
                return getString(R.string.flora_display_bosque);
            default:
                return floraKey;
        }
    }

    private void confirmPurchaseHex(String hexId, String floraKey) {
        int price = HiveViewModel.hexPurchasePriceEuros();
        int maxHives = HiveViewModel.maxHivesPerParcel();
        String floraLine = floraLabelForUi(floraKey);
        DialogHexPurchaseBinding purchaseForm = DialogHexPurchaseBinding.inflate(getLayoutInflater());
        purchaseForm.tvHexPurchaseSummary.setText(
                getString(R.string.hex_purchase_message, price, maxHives, floraLine));
        purchaseForm.editParcelName.setText(HexParcelRepository.newDefaultTerrenoName());
        purchaseForm.editParcelName.post(() -> purchaseForm.editParcelName.selectAll());

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.hex_purchase_title)
                .setView(purchaseForm.getRoot())
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.hex_purchase_confirm_buy, null);
        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String name = HexParcelRepository.sanitizeParcelName(
                    purchaseForm.editParcelName.getText().toString());
            if (name.isEmpty()) {
                Toast.makeText(requireContext(), R.string.hex_purchase_name_required, Toast.LENGTH_SHORT)
                        .show();
                return;
            }
            hiveViewModel.purchaseHex(hexId, currentUserId, name, msg -> {
                if (!isAdded()) {
                    return;
                }
                if (msg == null) {
                    dialog.dismiss();
                    Toast.makeText(requireContext(), R.string.hex_purchase_ok, Toast.LENGTH_SHORT).show();
                    refreshHexParcelOverlay();
                } else {
                    Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show();
                }
            });
        }));
        dialog.show();
    }

    private void moveCameraToOwnOrFirstHive(List<HiveEntity> hives) {
        if (!hives.isEmpty()) {
            HiveEntity first = hives.get(0);
            for (HiveEntity hive : hives) {
                if (currentUserId.equals(hive.ownerId)) {
                    first = hive;
                    break;
                }
            }
                googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(
                        new LatLng(first.lat, first.lng), MIN_ZOOM_HEX));
        }
    }

    private void setupMapInteractions() {
        googleMap.setOnMapLongClickListener(latLng ->
                hiveViewModel.createHiveAtLocation(currentUserId, latLng.latitude, latLng.longitude, null,
                        msg -> {
                            if (!isAdded()) {
                                return;
                            }
                            if (msg == null) {
                                Toast.makeText(requireContext(), R.string.hive_created_ok, Toast.LENGTH_SHORT)
                                        .show();
                            } else {
                                Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show();
                            }
                        }));

        googleMap.setOnMapClickListener(latLng -> {
            if (selectedOwnHiveId == null) {
                return;
            }
            HiveEntity hive = hiveById.get(selectedOwnHiveId);
            if (hive == null) {
                return;
            }
            hiveViewModel.transhumance(hive, latLng.latitude, latLng.longitude, null, msg -> {
                if (!isAdded()) {
                    return;
                }
                if (msg == null) {
                    Toast.makeText(requireContext(), R.string.hive_transhumance_ok, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show();
                }
                selectedOwnHiveId = null;
                refreshHexParcelOverlay();
            });
        });
    }

    @Override
    public boolean onMarkerClick(@NonNull Marker marker) {
        Object tag = marker.getTag();
        if (!(tag instanceof String)) return false;
        HiveEntity hive = hiveById.get((String) tag);
        if (hive == null) return false;
        if (currentUserId.equals(hive.ownerId)) {
            if (hive.id.equals(selectedOwnHiveId)) {
                selectedOwnHiveId = null;
                Toast.makeText(requireContext(), R.string.map_transhumance_cancelled, Toast.LENGTH_SHORT).show();
            } else {
                selectedOwnHiveId = hive.id;
                Toast.makeText(requireContext(), R.string.map_transhumance_pick_destination, Toast.LENGTH_LONG)
                        .show();
            }
            refreshHexParcelOverlay();
        } else {
            Toast.makeText(requireContext(), R.string.map_other_player_hive, Toast.LENGTH_SHORT).show();
        }
        return false;
    }

    private void enableMyLocation() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            googleMap.setMyLocationEnabled(true);
        } else {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
            Toast.makeText(requireContext(), "Permiso de ubicación requerido para centrado", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        hiveViewModel.startRealtimeCloudSync(currentUserId);
        hiveViewModel.startHexParcelCloudSync();
    }

    @Override
    public void onStop() {
        hiveViewModel.stopHexParcelCloudSync();
        hiveViewModel.stopRealtimeCloudSync();
        super.onStop();
    }

    @Override
    public void onDestroyView() {
        hexOverlayGeneration.incrementAndGet();
        if (hexOverlayExecutor != null) {
            hexOverlayExecutor.shutdownNow();
            hexOverlayExecutor = null;
        }
        mainHandler.removeCallbacksAndMessages(null);
        super.onDestroyView();
    }
}
