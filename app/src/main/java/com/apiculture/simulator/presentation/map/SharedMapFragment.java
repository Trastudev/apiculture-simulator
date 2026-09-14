package com.apiculture.simulator.presentation.map;

import android.Manifest;
import android.app.Dialog;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import com.apiculture.simulator.presentation.common.GameNotice;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import androidx.lifecycle.ViewModelProvider;

import com.apiculture.simulator.ApicultureApp;
import com.apiculture.simulator.R;
import com.apiculture.simulator.data.local.entity.HexParcelFloraEntity;
import com.apiculture.simulator.data.local.entity.HiveEntity;
import com.apiculture.simulator.data.repository.HexParcelRepository;
import com.apiculture.simulator.data.repository.IberiaHexOverlayStore;
import com.apiculture.simulator.data.repository.MapOverlayPrefs;
import com.apiculture.simulator.data.repository.MapRegionPrefs;
import com.apiculture.simulator.databinding.DialogHexPurchaseBinding;
import com.apiculture.simulator.databinding.DialogMapHiveBinding;
import com.apiculture.simulator.databinding.DialogMapParcelBinding;
import com.apiculture.simulator.databinding.FragmentSharedMapBinding;
import com.apiculture.simulator.domain.game.GameCalendar;
import com.apiculture.simulator.domain.game.TranshumanceRules;
import com.apiculture.simulator.domain.game.IberianClimateZone;
import com.apiculture.simulator.domain.game.SouthernAfricanClimateZone;
import com.apiculture.simulator.domain.map.PlayableMapRegion;
import com.apiculture.simulator.domain.parcel.BoundingBox;
import com.apiculture.simulator.domain.parcel.HexParcel;
import com.apiculture.simulator.domain.market.HoneyMarketEngine;
import com.apiculture.simulator.domain.parcel.FloraProgression;
import com.apiculture.simulator.domain.parcel.HexFlora;
import com.apiculture.simulator.domain.parcel.HexParcelGameRules;
import com.apiculture.simulator.presentation.common.SimpleViewModelFactory;
import com.apiculture.simulator.presentation.hive.HiveSiteSummaryUi;
import com.apiculture.simulator.presentation.hive.HiveViewModel;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.gms.maps.GoogleMap.OnMarkerClickListener;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polygon;
import com.google.android.gms.maps.model.PolygonOptions;
import com.google.firebase.auth.FirebaseAuth;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class SharedMapFragment extends Fragment implements OnMapReadyCallback, OnMarkerClickListener {

    /** Opacidad relleno ~50&nbsp;% (ARGB: 128/255). */
    private static final int HEX_FILL_ALPHA = 128;
    private static final int HEX_FREE_FILL = Color.argb(HEX_FILL_ALPHA, 129, 199, 132);
    /** Hex libre cuya flora nativa aún no desbloquea el jugador: no se puede comprar. */
    private static final int HEX_FREE_LOCKED_FILL = Color.argb(HEX_FILL_ALPHA, 229, 115, 115);
    private static final int HEX_OWN_FILL = Color.argb(HEX_FILL_ALPHA, 186, 104, 200);
    /** Terrenos comprados por otros jugadores: azul. */
    private static final int HEX_OTHER_FILL = Color.argb(HEX_FILL_ALPHA, 41, 121, 255);
    private static final float HEX_STROKE_WIDTH = 2.4f;
    /**
     * Por debajo no se dibujan hexágonos.
     * Alineado con el zoom regional por defecto para que la malla se vea al abrir el mapa.
     */
    private static final float MIN_ZOOM_HEX = 5.5f;
    /** Máximo de hex dibujados; se eligen los más cercanos al centro de pantalla. */
    private static final int MAX_HEX_VISIBLE = 220;
    private static final float ZOOM_FULL_PENINSULA_HEX = 8f;
    private static final int OVERLAY_DEBOUNCE_MS = 220;

    private FragmentSharedMapBinding binding;
    private GoogleMap googleMap;
    private HiveViewModel hiveViewModel;
    private String currentUserId = "guest";
    private final Map<String, HiveEntity> hiveById = new HashMap<>();
    private String selectedOwnHiveId;
    private final List<Polygon> hexOverlayPolygons = new ArrayList<>();
    private final List<Marker> hiveMarkers = new ArrayList<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private ExecutorService hexOverlayExecutor;
    private final AtomicInteger hexOverlayGeneration = new AtomicInteger(0);
    private PlayableMapRegion activeRegion = PlayableMapRegion.IBERIA;
    private List<HiveEntity> lastHives = Collections.emptyList();
    private boolean cameraPlacedForRegion;
    private boolean suppressRegionToggle;
    private boolean suppressHexToggle;
    private float lastMarkerZoomBucket = Float.NaN;
    private float lastHiveMarkerAnchorY = 0.78f;
    private final Runnable overlayDebounced = this::refreshHexParcelOverlayNow;

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

        activeRegion = MapRegionPrefs.get(requireContext());
        setupRegionToggle();
        setupHexOverlayToggles();

        hiveViewModel.hexOwnerships().observe(getViewLifecycleOwner(), rows -> scheduleHexOverlayRefresh());

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
        googleMap.setOnCameraIdleListener(this::onCameraIdle);
        googleMap.setOnPolygonClickListener(this::onHexPolygonClick);
        applyCameraConstraints(true);
        enableMyLocation();
        observeHivesAndRenderMarkers();
        setupMapInteractions();
    }

    private void observeHivesAndRenderMarkers() {
        hiveViewModel.allHives().observe(getViewLifecycleOwner(), hives -> {
            lastHives = hives != null ? hives : Collections.emptyList();
            renderHiveMarkers(lastHives);
            if (!cameraPlacedForRegion) {
                moveCameraToOwnOrFirstHive(lastHives);
                cameraPlacedForRegion = true;
            }
            scheduleHexOverlayRefresh();
        });
    }

    private void renderHiveMarkers(List<HiveEntity> hives) {
        if (googleMap == null) {
            return;
        }
        for (Marker marker : hiveMarkers) {
            marker.remove();
        }
        hiveMarkers.clear();
        hiveById.clear();
        if (hives == null) {
            return;
        }
        for (HiveEntity hive : hives) {
            if (!activeRegion.containsHive(hive.lat, hive.lng)) {
                continue;
            }
            LatLng position = new LatLng(hive.lat, hive.lng);
            boolean isOwn = currentUserId.equals(hive.ownerId);
            BitmapDescriptor icon = createLabeledHiveIcon(isOwn, hive.name);
            Marker marker = googleMap.addMarker(new MarkerOptions()
                    .position(position)
                    .title(hive.name)
                    .anchor(0.5f, lastHiveMarkerAnchorY)
                    .zIndex(isOwn ? 2f : 1f)
                    .icon(icon));
            if (marker != null) {
                marker.setTag(hive.id);
                hiveMarkers.add(marker);
            }
            hiveById.put(hive.id, hive);
        }
    }

    private void onCameraIdle() {
        maybeRefreshHiveMarkerScale();
        scheduleHexOverlayRefresh();
    }

    private void maybeRefreshHiveMarkerScale() {
        if (googleMap == null) {
            return;
        }
        float bucket = Math.round(googleMap.getCameraPosition().zoom * 2f) / 2f;
        if (!Float.isNaN(lastMarkerZoomBucket) && bucket == lastMarkerZoomBucket) {
            return;
        }
        lastMarkerZoomBucket = bucket;
        renderHiveMarkers(lastHives);
    }

    private int hiveIconSizePx() {
        float density = getResources().getDisplayMetrics().density;
        float zoom = 6.4f;
        if (googleMap != null) {
            zoom = googleMap.getCameraPosition().zoom;
        }
        float t = (zoom - 5.5f) / 5.5f;
        if (t < 0f) {
            t = 0f;
        } else if (t > 1f) {
            t = 1f;
        }
        float dp = 22f + t * 26f;
        return Math.max(18, Math.round(dp * density));
    }

    @NonNull
    private BitmapDescriptor createLabeledHiveIcon(boolean own, @Nullable String rawName) {
        Bitmap src = BitmapFactory.decodeResource(getResources(), R.drawable.ic_compracolmena);
        if (src == null) {
            return BitmapDescriptorFactory.defaultMarker(
                    own ? BitmapDescriptorFactory.HUE_ORANGE : BitmapDescriptorFactory.HUE_AZURE);
        }
        int iconSize = hiveIconSizePx();
        Bitmap scaled = Bitmap.createScaledBitmap(src, iconSize, iconSize, true);
        if (!own) {
            Bitmap tinted = Bitmap.createBitmap(iconSize, iconSize, Bitmap.Config.ARGB_8888);
            Canvas tintCanvas = new Canvas(tinted);
            Paint tintPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
            tintPaint.setColorFilter(new PorterDuffColorFilter(Color.rgb(66, 165, 245), PorterDuff.Mode.MULTIPLY));
            tintCanvas.drawBitmap(scaled, 0, 0, tintPaint);
            scaled = tinted;
        }
        String label = markerLabel(rawName);
        float density = getResources().getDisplayMetrics().density;
        float textSize = Math.max(9f * density, iconSize * 0.28f);
        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        fill.setColor(Color.WHITE);
        fill.setTextAlign(Paint.Align.CENTER);
        fill.setTextSize(textSize);
        fill.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        Paint stroke = new Paint(fill);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(Math.max(2f, density * 2.2f));
        stroke.setColor(Color.argb(230, 45, 32, 18));
        Rect bounds = new Rect();
        fill.getTextBounds(label, 0, label.length(), bounds);
        int textW = bounds.width();
        int textH = bounds.height();
        int padX = Math.round(4f * density);
        int padY = Math.round(3f * density);
        int width = Math.max(iconSize, textW + padX * 2);
        int height = iconSize + padY + textH + padY;
        lastHiveMarkerAnchorY = iconSize / (float) height;
        Bitmap out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out);
        canvas.drawBitmap(scaled, (width - iconSize) / 2f, 0, null);
        float tx = width / 2f;
        float ty = iconSize + padY - bounds.top;
        canvas.drawText(label, tx, ty, stroke);
        canvas.drawText(label, tx, ty, fill);
        return BitmapDescriptorFactory.fromBitmap(out);
    }

    @NonNull
    private static String markerLabel(@Nullable String name) {
        String n = name == null ? "" : name.trim();
        if (n.isEmpty()) {
            n = "Colmena";
        }
        if (n.length() > 14) {
            return n.substring(0, 13) + "...";
        }
        return n;
    }

    private void scheduleHexOverlayRefresh() {
        mainHandler.removeCallbacks(overlayDebounced);
        mainHandler.postDelayed(overlayDebounced, OVERLAY_DEBOUNCE_MS);
    }

    private void removeHexOverlayPolygons() {
        for (Polygon p : hexOverlayPolygons) {
            try {
                p.remove();
            } catch (RuntimeException ignored) {
            }
        }
        hexOverlayPolygons.clear();
    }

    private void refreshHexParcelOverlayNow() {
        if (googleMap == null || !isAdded() || hexOverlayExecutor == null) {
            return;
        }
        hexOverlayGeneration.incrementAndGet();
        removeHexOverlayPolygons();
        final PlayableMapRegion regionForHex = activeRegion;
        if (!IberiaHexOverlayStore.isLoaded(regionForHex)) {
            setOverlayBusy(true);
            IberiaHexOverlayStore.ensureLoadedAsync(
                    requireContext().getApplicationContext(),
                    regionForHex,
                    () -> {
                        if (!isAdded() || activeRegion != regionForHex) {
                            setOverlayBusy(false);
                            return;
                        }
                        setOverlayBusy(false);
                        refreshHexParcelOverlayNow();
                    });
            return;
        }
        setOverlayBusy(false);
        final boolean showMesh = MapOverlayPrefs.isMeshVisible(requireContext());
        final boolean filterOwn = MapOverlayPrefs.isOwnFilter(requireContext());
        final boolean filterOthers = MapOverlayPrefs.isOthersFilter(requireContext());
        final boolean filterBuyable = MapOverlayPrefs.isBuyableFilter(requireContext());
        final boolean anyOwnershipLayer = filterOwn || filterOthers || filterBuyable;
        final boolean drawHexes = showMesh || anyOwnershipLayer;
        final float zoom = googleMap.getCameraPosition().zoom;
        if (!drawHexes || (showMesh && !anyOwnershipLayer && zoom < MIN_ZOOM_HEX)) {
            if (binding != null && binding.cardClimateLegend != null) {
                binding.cardClimateLegend.setVisibility(View.GONE);
            }
            return;
        }
        if (binding != null && binding.cardClimateLegend != null) {
            binding.cardClimateLegend.setVisibility(showMesh ? View.VISIBLE : View.GONE);
            if (showMesh) {
                applyLegendForActiveRegion();
            }
        }
        LatLngBounds vb = googleMap.getProjection().getVisibleRegion().latLngBounds;
        LatLng sw = vb.southwest;
        LatLng ne = vb.northeast;
        double latPad = Math.max(1e-6, (ne.latitude - sw.latitude) * 0.12);
        double lonPad = Math.max(1e-6, (ne.longitude - sw.longitude) * 0.12);
        BoundingBox gen = new BoundingBox(
                sw.latitude - latPad,
                ne.latitude + latPad,
                sw.longitude - lonPad,
                ne.longitude + lonPad);
        final BoundingBox clipped = clipBoundingBoxForHexDraw(gen, zoom);
        final BoundingBox viewInRegion = clipped.intersect(activeRegion.box());
        if (viewInRegion == null) {
            return;
        }
        int computedMax = maxHexForZoom(zoom);
        if (filterOwn || filterOthers) {
            computedMax = Math.max(computedMax, 2500);
        }
        final int maxHex = computedMax;
        final LatLng focus = googleMap.getCameraPosition().target;
        final int generation = hexOverlayGeneration.get();
        final GoogleMap mapWhenScheduled = googleMap;
        final Context appCtx = requireContext().getApplicationContext();
        final String uidForHex = currentUserId;
        final int playerLevelForHex =
                ((ApicultureApp) appCtx).getPlayerProgressRepository().getLevel(uidForHex);
        /** Con colmena elegida para transhumancia, los hex no interceptan el toque: el mapa recibe el destino. */
        final boolean hexPolygonsClickable = selectedOwnHiveId == null;
        final List<HiveEntity> hivesSnapshot = lastHives;

        hexOverlayExecutor.execute(() -> {
            List<HexParcel> all = IberiaHexOverlayStore.getParcels(appCtx, regionForHex);
            Map<String, String> ownership =
                    ((ApicultureApp) appCtx).getHexParcelRepository().getOwnershipMapSync();
            Set<String> ownHexIds = collectOwnHexIds(ownership, uidForHex, hivesSnapshot, appCtx);
            List<HexParcel> parcels = new ArrayList<>();
            if (filterOwn) {
                for (String hexId : ownHexIds) {
                    HexParcel owned = parcelById(all, hexId);
                    if (owned != null
                            && viewInRegion.containsLatLon(owned.centroidLat, owned.centroidLon)) {
                        parcels.add(owned);
                    }
                }
            }
            if (showMesh || filterBuyable || filterOthers) {
                List<HexParcel> inView = IberiaHexOverlayStore.visibleInViewport(
                        all, viewInRegion, maxHex, focus.latitude, focus.longitude);
                for (HexParcel parcel : inView) {
                    if (parcelById(parcels, parcel.id) == null) {
                        parcels.add(parcel);
                    }
                }
            }
            List<Pair<PolygonOptions, String>> specs = new ArrayList<>(parcels.size());
            for (int i = 0; i < parcels.size(); i++) {
                HexParcel parcel = parcels.get(i);
                String o = ownership.get(parcel.id);
                boolean isOwn = ownHexIds.contains(parcel.id) || uidForHex.equals(o);
                boolean canBuy = false;
                int fill;
                if (isOwn) {
                    fill = HEX_OWN_FILL;
                } else if (o == null) {
                    String previewFlora = HexFlora.nativeFloraForParcel(parcel);
                    canBuy = FloraProgression.isFloraUnlockedForPlayerLevel(
                            previewFlora, playerLevelForHex);
                    fill = canBuy ? HEX_FREE_FILL : HEX_FREE_LOCKED_FILL;
                } else {
                    fill = HEX_OTHER_FILL;
                }
                if (!hexPassesFilter(showMesh, filterOwn, filterOthers, filterBuyable,
                        isOwn, o, canBuy)) {
                    continue;
                }
                int stroke = climateStrokeColor(parcel);
                boolean polygonClickable = hexPolygonsClickable;
                specs.add(new Pair<>(
                        hexPolygonOptions(parcel, fill, stroke, polygonClickable), parcel.id));
            }
            final List<Pair<PolygonOptions, String>> toDraw = specs;
            mainHandler.post(() -> {
                if (getView() == null || !isAdded() || googleMap == null || googleMap != mapWhenScheduled
                        || generation != hexOverlayGeneration.get()) {
                    return;
                }
                removeHexOverlayPolygons();
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
            return 90;
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

    /** Borde del hex = zona climática (el relleno sigue siendo libre / propio / ajeno). */
    private static int climateStrokeColor(HexParcel parcel) {
        if (HexFlora.isSouthernParcel(parcel)) {
            return climateStrokeColorZa(SouthernAfricanClimateZone.forParcel(parcel));
        }
        return climateStrokeColorIberia(IberianClimateZone.forParcel(parcel));
    }

    private static int climateStrokeColorIberia(IberianClimateZone zone) {
        if (zone == null) {
            zone = IberianClimateZone.CONTINENTAL;
        }
        switch (zone) {
            case ATLANTIC:
                return Color.argb(235, 8, 120, 145);
            case MOUNTAIN:
                return Color.argb(235, 168, 188, 214);
            case MEDITERRANEAN:
                return Color.argb(235, 210, 140, 18);
            case SOUTH:
                return Color.argb(235, 200, 72, 36);
            case CONTINENTAL:
            default:
                return Color.argb(235, 98, 128, 48);
        }
    }

    private static int climateStrokeColorZa(SouthernAfricanClimateZone zone) {
        if (zone == null) {
            zone = SouthernAfricanClimateZone.HIGHVELD;
        }
        switch (zone) {
            case FYNBOS:
                return Color.argb(235, 123, 63, 160);
            case KAROO:
                return Color.argb(235, 196, 154, 60);
            case HIGHVELD:
                return Color.argb(235, 212, 160, 23);
            case SUBTROPICAL:
                return Color.argb(235, 27, 138, 122);
            case BUSHVELD:
            default:
                return Color.argb(235, 90, 122, 56);
        }
    }

    private String climateLineForParcel(@Nullable HexParcel parcel) {
        if (HexFlora.isSouthernParcel(parcel)) {
            return SouthernAfricanClimateZone.forParcel(parcel).dialogLineEs();
        }
        return IberianClimateZone.forParcel(parcel).dialogLineEs();
    }

    private String climateLabelForHex(String hexId) {
        HexParcel parcel = IberiaHexOverlayStore.findById(requireContext().getApplicationContext(), hexId);
        return climateLabelForParcel(parcel);
    }

    private String climateLineForHex(String hexId) {
        HexParcel parcel = IberiaHexOverlayStore.findById(requireContext().getApplicationContext(), hexId);
        return climateLineForParcel(parcel);
    }

    private static boolean hexPassesFilter(
            boolean showMesh,
            boolean filterOwn,
            boolean filterOthers,
            boolean filterBuyable,
            boolean isOwn,
            @Nullable String ownerId,
            boolean canBuy) {
        boolean anyFilter = filterOwn || filterOthers || filterBuyable;
        if (!anyFilter) {
            return showMesh;
        }
        boolean isBuyable = ownerId == null && !isOwn && canBuy;
        boolean isOther = ownerId != null && !isOwn;
        return (filterOwn && isOwn) || (filterBuyable && isBuyable) || (filterOthers && isOther);
    }

    @Nullable
    private static HexParcel parcelById(List<HexParcel> all, String hexId) {
        if (all == null || hexId == null) {
            return null;
        }
        for (int i = 0; i < all.size(); i++) {
            HexParcel p = all.get(i);
            if (hexId.equals(p.id)) {
                return p;
            }
        }
        return null;
    }

    private static Set<String> collectOwnHexIds(
            Map<String, String> ownership,
            String uid,
            List<HiveEntity> hives,
            Context appCtx) {
        Set<String> ids = new HashSet<>();
        if (uid == null) {
            return ids;
        }
        for (Map.Entry<String, String> e : ownership.entrySet()) {
            if (uid.equals(e.getValue()) && e.getKey() != null) {
                ids.add(e.getKey());
            }
        }
        if (hives != null) {
            for (HiveEntity hive : hives) {
                if (hive == null || !uid.equals(hive.ownerId)) {
                    continue;
                }
                HexParcel underHive = IberiaHexOverlayStore.findContaining(appCtx, hive.lat, hive.lng);
                if (underHive != null) {
                    ids.add(underHive.id);
                }
            }
        }
        return ids;
    }

    private void onHexPolygonClick(Polygon polygon) {
        Object tag = polygon.getTag();
        if (!(tag instanceof String)) {
            return;
        }
        onHexSelected((String) tag);
    }

    private void onHexSelected(final String hexId) {
        if (hexOverlayExecutor == null || hexId == null) {
            return;
        }
        ApicultureApp app = (ApicultureApp) requireContext().getApplicationContext();
        hexOverlayExecutor.execute(() -> {
            String owner = app.getHexParcelRepository().getOwnerSync(hexId);
            long nowMs = System.currentTimeMillis();
            final String describeFloras = app.getHexFloraRepository()
                    .describeFlorasOnParcelForDialogBlocking(hexId, nowMs);
            final String displayFlora = app.getHexFloraRepository().getDisplayFloraForHexBlocking(hexId);
            HexParcel tapParcel = IberiaHexOverlayStore.findById(app, hexId);
            final String climateLabel = climateLabelForParcel(tapParcel);
            int usedHives = app.getHiveRepository().countHivesOnHexBlocking(hexId);
            int remainingSlots = Math.max(0, HexParcelGameRules.MAX_HIVES_PER_HEX - usedHives);

            if (owner != null && owner.equals(currentUserId)) {
                mainHandler.post(() -> {
                    if (!isAdded()) {
                        return;
                    }
                    showMapParcelDialog(
                            getString(R.string.hex_own_parcel_title),
                            getString(R.string.hex_own_parcel_kicker),
                            displayFlora,
                            getString(R.string.hex_own_parcel_slots, remainingSlots),
                            climateLabel,
                            describeFloras,
                            null,
                            getString(R.string.map_plant_flora_button),
                            () -> showPlantAdditionalFloraDialog(hexId));
                });
                return;
            }

            if (owner != null) {
                app.getProfileRepository().fetchDisplayProfile(owner, profile -> {
                    if (!isAdded()) {
                        return;
                    }
                    String playerName = profile.playerName == null || profile.playerName.trim().isEmpty()
                            ? getString(R.string.hex_other_player_anonymous)
                            : profile.playerName.trim();
                    String brand = profile.honeyBrand == null || profile.honeyBrand.trim().isEmpty()
                            ? "—"
                            : profile.honeyBrand.trim();
                    showMapParcelDialog(
                            getString(R.string.hex_other_parcel_title, playerName),
                            getString(R.string.hex_other_parcel_kicker),
                            displayFlora,
                            getString(R.string.hex_own_parcel_slots, remainingSlots),
                            climateLabel,
                            describeFloras,
                            getString(R.string.hex_other_parcel_brand, brand),
                            null,
                            null);
                });
                return;
            }

            int pl = app.getPlayerProgressRepository().getLevel(currentUserId);
            String previewFlora = HexFlora.nativeFloraForParcel(tapParcel);
            if (!FloraProgression.isFloraUnlockedForPlayerLevel(previewFlora, pl)) {
                int needLevel = FloraProgression.minLevelRequiredForFlora(previewFlora);
                final String previewFloraFinal = previewFlora;
                mainHandler.post(() -> {
                    if (!isAdded()) {
                        return;
                    }
                    showMapParcelDialog(
                            getString(R.string.hex_locked_flora_title),
                            getString(R.string.hex_locked_flora_kicker),
                            previewFloraFinal,
                            getString(R.string.hex_purchase_hives_value, HexParcelGameRules.MAX_HIVES_PER_HEX),
                            climateLabel,
                            null,
                            getString(R.string.hex_locked_flora_note, needLevel),
                            null,
                            null);
                });
                return;
            }

            mainHandler.post(() -> {
                if (!isAdded()) {
                    return;
                }
                confirmPurchaseHex(hexId);
            });
        });
    }

    private String climateLabelForParcel(@Nullable HexParcel parcel) {
        if (HexFlora.isSouthernParcel(parcel)) {
            return SouthernAfricanClimateZone.forParcel(parcel).labelEs();
        }
        return IberianClimateZone.forParcel(parcel).labelEs();
    }

    private void showMapParcelDialog(
            @NonNull String title,
            @NonNull String kicker,
            @Nullable String floraKey,
            @NonNull String hivesText,
            @NonNull String climateLabel,
            @Nullable String floraDetail,
            @Nullable String note,
            @Nullable String actionLabel,
            @Nullable Runnable onAction) {
        if (!isAdded()) {
            return;
        }
        DialogMapParcelBinding form = DialogMapParcelBinding.inflate(getLayoutInflater());
        form.tvMapParcelTitle.setText(title);
        form.tvMapParcelKicker.setText(kicker);
        form.tvMapParcelFlora.setText(floraLabelForUi(floraKey));
        form.ivMapParcelFlora.setImageResource(HiveSiteSummaryUi.floraHoneyJarIcon(floraKey));
        form.tvMapParcelHives.setText(hivesText);
        form.tvMapParcelClimate.setText(climateLabel);
        if (floraDetail != null && !floraDetail.trim().isEmpty()) {
            form.tvMapParcelDetail.setVisibility(View.VISIBLE);
            form.tvMapParcelDetail.setText(floraDetail);
        } else {
            form.tvMapParcelDetail.setVisibility(View.GONE);
        }
        if (note != null && !note.trim().isEmpty()) {
            form.tvMapParcelNote.setVisibility(View.VISIBLE);
            form.tvMapParcelNote.setText(note);
        } else {
            form.tvMapParcelNote.setVisibility(View.GONE);
        }

        Dialog dialog = new Dialog(requireContext());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(form.getRoot());
        dialog.setCancelable(true);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        if (actionLabel != null && onAction != null) {
            form.btnMapParcelAction.setVisibility(View.VISIBLE);
            form.btnMapParcelAction.setText(actionLabel);
            form.btnMapParcelAction.setOnClickListener(v -> {
                dialog.dismiss();
                onAction.run();
            });
        } else {
            form.btnMapParcelAction.setVisibility(View.GONE);
        }
        form.btnMapParcelClose.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
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
            case "lavanda":
                return getString(R.string.flora_display_lavanda);
            case "castaño":
            case "castano":
                return getString(R.string.flora_display_castano);
            case "eucalipto":
                return getString(R.string.flora_display_eucalipto);
            case "mielato de encina y roble":
                return getString(R.string.flora_display_mielato);
            case "neret":
                return getString(R.string.flora_display_neret);
            case "arboç":
            case "arboc":
                return getString(R.string.flora_display_arboc);
            case "fynbos":
                return getString(R.string.flora_display_fynbos);
            case "aloe":
            case "áloe":
                return getString(R.string.flora_display_aloe);
            case "macadamia":
                return getString(R.string.flora_display_macadamia);
            case "litchi":
            case "lychee":
                return getString(R.string.flora_display_litchi);
            case "lucerna":
            case "alfalfa":
                return getString(R.string.flora_display_lucerna);
            case "acacia":
                return getString(R.string.flora_display_acacia);
            default:
                return floraKey;
        }
    }

    private void confirmPurchaseHex(String hexId) {
        ApicultureApp app = (ApicultureApp) requireContext().getApplicationContext();
        int playerLevel = app.getPlayerProgressRepository().getLevel(currentUserId);
        HexParcel buyParcel = IberiaHexOverlayStore.findById(
                requireContext().getApplicationContext(), hexId);
        String nativeFlora = HexFlora.nativeFloraForParcel(buyParcel);
        int price = HiveViewModel.hexPurchasePriceEurosForHex(hexId, requireContext().getApplicationContext());
        int maxHives = HiveViewModel.maxHivesPerParcel();
        DialogHexPurchaseBinding purchaseForm = DialogHexPurchaseBinding.inflate(getLayoutInflater());
        purchaseForm.tvHexPurchasePrice.setText(getString(R.string.hex_purchase_price_chip, price));
        purchaseForm.tvHexPurchaseFlora.setText(floraLabelForUi(nativeFlora));
        purchaseForm.ivHexPurchaseFlora.setImageResource(
                HiveSiteSummaryUi.floraHoneyJarIcon(nativeFlora));
        purchaseForm.tvHexPurchaseHives.setText(getString(R.string.hex_purchase_hives_value, maxHives));
        purchaseForm.tvHexPurchaseClimate.setText(climateLabelForHex(hexId));
        purchaseForm.editParcelName.setText(HexParcelRepository.newDefaultTerrenoName());
        purchaseForm.editParcelName.post(() -> purchaseForm.editParcelName.selectAll());

        Dialog dialog = new Dialog(requireContext());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(purchaseForm.getRoot());
        dialog.setCancelable(true);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        purchaseForm.btnHexPurchaseCancel.setOnClickListener(v -> dialog.dismiss());
        purchaseForm.btnHexPurchaseConfirm.setOnClickListener(v -> {
            String name = HexParcelRepository.sanitizeParcelName(
                    purchaseForm.editParcelName.getText().toString());
            if (name.isEmpty()) {
                GameNotice.show(requireContext(), R.string.hex_purchase_name_required);
                return;
            }
            hiveViewModel.purchaseHex(hexId, currentUserId, name, playerLevel, msg -> {
                if (!isAdded()) {
                    return;
                }
                if (msg == null) {
                    dialog.dismiss();
                    GameNotice.show(requireContext(), R.string.hex_purchase_ok);
                    scheduleHexOverlayRefresh();
                } else {
                    GameNotice.show(requireContext(), msg);
                }
            });
        });
        dialog.show();
    }

    private void showPlantAdditionalFloraDialog(String hexId) {
        if (!isAdded()) {
            return;
        }
        final Context appCtx = requireContext().getApplicationContext();
        hexOverlayExecutor.execute(() -> {
            ApicultureApp app = (ApicultureApp) appCtx;
            int level = app.getPlayerProgressRepository().getLevel(currentUserId);
            List<String> occupied = new ArrayList<>();
            for (HexParcelFloraEntity e : app.getHexFloraRepository().listEntriesForHexBlocking(hexId)) {
                occupied.add(HoneyMarketEngine.canonicalFloraKey(e.floraKey));
            }
            int n = occupied.size();
            int cost = FloraProgression.plantingCostEurosForAdditionalFlora(n);
            long hours = FloraProgression.growingDurationHoursForSlotIndex(n + 1);
            HexParcel plantParcel = IberiaHexOverlayStore.findById(appCtx, hexId);
            List<String> candidates = new ArrayList<>();
            for (String f : FloraProgression.unlockOrder()) {
                if (!FloraProgression.isFloraUnlockedForPlayerLevel(f, level)) {
                    continue;
                }
                if (occupied.contains(f)) {
                    continue;
                }
                if (!HexFlora.isAllowedOnParcel(f, plantParcel)) {
                    continue;
                }
                candidates.add(f);
            }
            String detail = getString(R.string.map_plant_flora_cost_time, cost, (int) hours);
            mainHandler.post(() -> {
                if (!isAdded()) {
                    return;
                }
                if (candidates.isEmpty()) {
                    GameNotice.show(requireContext(), R.string.map_plant_flora_none);
                    return;
                }
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle(R.string.map_plant_flora_title)
                        .setMessage(getString(R.string.map_plant_flora_pick) + "\n\n" + detail)
                        .setItems(candidates.toArray(new String[0]), (dialog, which) -> {
                            String pick = candidates.get(which);
                            hiveViewModel.plantAdditionalFlora(hexId, currentUserId, pick, level, msg -> {
                                if (!isAdded()) {
                                    return;
                                }
                                if (msg == null) {
                                    GameNotice.show(requireContext(), R.string.map_plant_flora_scheduled);
                                    scheduleHexOverlayRefresh();
                                } else {
                                    GameNotice.show(requireContext(), msg);
                                }
                            });
                        })
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
            });
        });
    }

    private void moveCameraToOwnOrFirstHive(List<HiveEntity> hives) {
        if (googleMap == null) {
            return;
        }
        HiveEntity first = null;
        if (hives != null) {
            for (HiveEntity hive : hives) {
                if (!activeRegion.containsHive(hive.lat, hive.lng)) {
                    continue;
                }
                if (currentUserId.equals(hive.ownerId)) {
                    first = hive;
                    break;
                }
                if (first == null) {
                    first = hive;
                }
            }
        }
        if (first != null) {
            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(
                    new LatLng(first.lat, first.lng), Math.max(MIN_ZOOM_HEX, activeRegion.defaultZoom())));
        } else {
            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(
                    new LatLng(activeRegion.defaultLookLat(), activeRegion.defaultLookLon()),
                    activeRegion.defaultZoom()));
        }
    }

    private void setupRegionToggle() {
        if (binding == null || binding.mapRegionToggle == null) {
            return;
        }
        suppressRegionToggle = true;
        binding.mapRegionToggle.check(activeRegion == PlayableMapRegion.SOUTH_AFRICA
                ? R.id.btn_map_za : R.id.btn_map_iberia);
        suppressRegionToggle = false;
        applyLegendForActiveRegion();
        binding.mapRegionToggle.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked || suppressRegionToggle) {
                return;
            }
            PlayableMapRegion next = checkedId == R.id.btn_map_za
                    ? PlayableMapRegion.SOUTH_AFRICA
                    : PlayableMapRegion.IBERIA;
            if (next == activeRegion) {
                return;
            }
            switchToRegion(next);
        });
    }

    private void switchToRegion(PlayableMapRegion next) {
        if (next == null) {
            next = PlayableMapRegion.IBERIA;
        }
        final PlayableMapRegion target = next;
        activeRegion = target;
        MapRegionPrefs.set(requireContext(), target);
        applyLegendForActiveRegion();
        cameraPlacedForRegion = false;
        selectedOwnHiveId = null;
        lastMarkerZoomBucket = Float.NaN;
        hexOverlayGeneration.incrementAndGet();
        removeHexOverlayPolygons();
        boolean ready = IberiaHexOverlayStore.isLoaded(target);
        setOverlayBusy(!ready);
        if (!ready) {
            GameNotice.show(requireContext(), R.string.map_region_loading);
        }
        finishRegionCameraAndDraw();
    }

    private void finishRegionCameraAndDraw() {
        renderHiveMarkers(lastHives);
        cameraPlacedForRegion = true;
        applyCameraConstraints(true);
        refreshHexParcelOverlayNow();
    }

    private void setOverlayBusy(boolean busy) {
        if (binding != null && binding.progressMapRegion != null) {
            binding.progressMapRegion.setVisibility(busy ? View.VISIBLE : View.GONE);
        }
    }

    private void applyCameraConstraints(boolean moveNow) {
        if (googleMap == null) {
            return;
        }
        googleMap.resetMinMaxZoomPreference();
        try {
            googleMap.setLatLngBoundsForCameraTarget(null);
        } catch (RuntimeException ignored) {
        }
        if (moveNow) {
            moveCameraToOwnOrFirstHive(lastHives);
        }
        BoundingBox box = activeRegion.box();
        LatLngBounds bounds = new LatLngBounds(
                new LatLng(box.minLat, box.minLon),
                new LatLng(box.maxLat, box.maxLon));
        googleMap.setLatLngBoundsForCameraTarget(bounds);
        googleMap.setMinZoomPreference(activeRegion.minZoom());
    }

    private void applyLegendForActiveRegion() {
        if (binding == null) {
            return;
        }
        boolean za = activeRegion == PlayableMapRegion.SOUTH_AFRICA;
        if (binding.legendIberia != null) {
            binding.legendIberia.setVisibility(za ? View.GONE : View.VISIBLE);
        }
        if (binding.legendZa != null) {
            binding.legendZa.setVisibility(za ? View.VISIBLE : View.GONE);
        }
        if (binding.tvClimateLegend != null) {
            binding.tvClimateLegend.setText(za
                    ? R.string.map_climate_legend_za
                    : R.string.map_climate_legend);
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
                                GameNotice.show(requireContext(), R.string.hive_created_ok);
                            } else {
                                GameNotice.show(requireContext(), msg);
                            }
                        }));

        googleMap.setOnMapClickListener(latLng -> {
            if (selectedOwnHiveId != null) {
                HiveEntity hive = hiveById.get(selectedOwnHiveId);
                if (hive == null) {
                    return;
                }
                int today = GameCalendar.toDayKey(java.time.LocalDate.now(GameCalendar.userTimeZone()));
                if (TranshumanceRules.isInTransit(hive, today)) {
                    GameNotice.show(requireContext(), R.string.map_transhumance_busy);
                    selectedOwnHiveId = null;
                    scheduleHexOverlayRefresh();
                    return;
                }
                HexParcel destHex = IberiaHexOverlayStore.findContaining(
                        requireContext().getApplicationContext(), latLng.latitude, latLng.longitude);
                boolean sameHex = destHex != null && hive.hexId != null && destHex.id.equals(hive.hexId);
                Runnable go = () -> hiveViewModel.transhumance(hive, latLng.latitude, latLng.longitude, null, msg -> {
                    if (!isAdded()) {
                        return;
                    }
                    if (msg == null) {
                        if (!sameHex) {
                            GameNotice.show(requireContext(), R.string.hive_transhumance_ok);
                        }
                    } else {
                        GameNotice.show(requireContext(), msg);
                    }
                    selectedOwnHiveId = null;
                    scheduleHexOverlayRefresh();
                });
                if (sameHex) {
                    go.run();
                    return;
                }
                int cost = TranshumanceRules.costEuros(hive.lat, hive.lng, latLng.latitude, latLng.longitude);
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle(R.string.map_transhumance_confirm_title)
                        .setMessage(getString(R.string.map_transhumance_confirm_message, cost))
                        .setNegativeButton(android.R.string.cancel, (d, w) -> {
                            selectedOwnHiveId = null;
                            scheduleHexOverlayRefresh();
                        })
                        .setPositiveButton(R.string.map_transhumance_confirm_ok, (d, w) -> go.run())
                        .show();
                return;
            }
            handleMapTapForHex(latLng);
        });
    }

    private void handleMapTapForHex(LatLng latLng) {
        if (!IberiaHexOverlayStore.isLoaded(activeRegion)) {
            GameNotice.show(requireContext(), R.string.map_region_loading);
            return;
        }
        HexParcel hex = IberiaHexOverlayStore.findContaining(
                requireContext().getApplicationContext(), latLng.latitude, latLng.longitude);
        if (hex == null) {
            return;
        }
        onHexSelected(hex.id);
    }

    private void setupHexOverlayToggles() {
        if (binding == null) {
            return;
        }
        Context ctx = requireContext();
        suppressHexToggle = true;
        binding.btnMapHexMesh.setChecked(MapOverlayPrefs.isMeshVisible(ctx));
        binding.btnMapHexOwn.setChecked(MapOverlayPrefs.isOwnFilter(ctx));
        binding.btnMapHexOther.setChecked(MapOverlayPrefs.isOthersFilter(ctx));
        binding.btnMapHexBuy.setChecked(MapOverlayPrefs.isBuyableFilter(ctx));
        suppressHexToggle = false;
        binding.btnMapHexMesh.addOnCheckedChangeListener((button, isChecked) -> {
            if (suppressHexToggle) {
                return;
            }
            MapOverlayPrefs.setMeshVisible(requireContext(), isChecked);
            scheduleHexOverlayRefresh();
        });
        binding.btnMapHexOwn.addOnCheckedChangeListener((button, isChecked) -> {
            if (suppressHexToggle) {
                return;
            }
            MapOverlayPrefs.setOwnFilter(requireContext(), isChecked);
            scheduleHexOverlayRefresh();
        });
        binding.btnMapHexOther.addOnCheckedChangeListener((button, isChecked) -> {
            if (suppressHexToggle) {
                return;
            }
            MapOverlayPrefs.setOthersFilter(requireContext(), isChecked);
            scheduleHexOverlayRefresh();
        });
        binding.btnMapHexBuy.addOnCheckedChangeListener((button, isChecked) -> {
            if (suppressHexToggle) {
                return;
            }
            MapOverlayPrefs.setBuyableFilter(requireContext(), isChecked);
            scheduleHexOverlayRefresh();
        });
    }

    @Override
    public boolean onMarkerClick(@NonNull Marker marker) {
        Object tag = marker.getTag();
        if (!(tag instanceof String)) {
            return true;
        }
        HiveEntity hive = hiveById.get((String) tag);
        if (hive == null) {
            return true;
        }
        if (!currentUserId.equals(hive.ownerId)) {
            GameNotice.show(requireContext(), R.string.map_other_player_hive);
            return true;
        }
        DialogMapHiveBinding form = DialogMapHiveBinding.inflate(getLayoutInflater());
        String hiveTitle = hive.name != null && !hive.name.trim().isEmpty()
                ? hive.name.trim()
                : getString(R.string.map_hive_open_detail);
        form.tvMapHiveTitle.setText(hiveTitle);

        Dialog dialog = new Dialog(requireContext());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(form.getRoot());
        dialog.setCancelable(true);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        form.btnMapHiveDetail.setOnClickListener(v -> {
            dialog.dismiss();
            openHiveDetail(hive.id);
        });
        form.btnMapHiveTranshumance.setOnClickListener(v -> {
            dialog.dismiss();
            startTranshumancePick(hive);
        });
        form.btnMapHiveCancel.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
        return true;
    }

    private void openHiveDetail(String hiveId) {
        if (hiveId == null || !isAdded()) {
            return;
        }
        Bundle args = new Bundle();
        args.putString("hiveId", hiveId);
        NavHostFragment.findNavController(this).navigate(R.id.hiveDetailFragment, args);
    }

    private void startTranshumancePick(HiveEntity hive) {
        if (hive == null || hive.id == null) {
            return;
        }
        if (hive.id.equals(selectedOwnHiveId)) {
            selectedOwnHiveId = null;
            GameNotice.show(requireContext(), R.string.map_transhumance_cancelled);
        } else {
            selectedOwnHiveId = hive.id;
            GameNotice.show(requireContext(), R.string.map_transhumance_pick_destination);
        }
        scheduleHexOverlayRefresh();
    }

    private void enableMyLocation() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            googleMap.setMyLocationEnabled(true);
        } else {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
            GameNotice.show(requireContext(), R.string.map_location_permission_center);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        scheduleHexOverlayRefresh();
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
