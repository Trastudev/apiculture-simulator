package com.apiculture.simulator.presentation.map;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.apiculture.simulator.data.repository.FleetStore;
import com.apiculture.simulator.data.repository.GameServer;
import com.apiculture.simulator.domain.game.FloraBloomWindow;
import com.apiculture.simulator.domain.game.HexFloraSaturation;
import com.apiculture.simulator.domain.map.Seaport;
import com.apiculture.simulator.domain.map.SeaportCatalog;
import com.apiculture.simulator.domain.parcel.CropRules;
import com.apiculture.simulator.presentation.common.FloraSaturationBar;
import com.apiculture.simulator.presentation.common.GameNotice;
import com.apiculture.simulator.presentation.hive.TruckTripUi;

import androidx.annotation.ColorRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import androidx.lifecycle.ViewModelProvider;

import com.apiculture.simulator.ApicultureApp;
import com.apiculture.simulator.R;
import com.apiculture.simulator.data.local.entity.HexParcelFloraEntity;
import com.apiculture.simulator.data.local.entity.HexParcelOwnershipEntity;
import com.apiculture.simulator.data.local.entity.HiveEntity;
import com.apiculture.simulator.data.local.entity.HoneyOrderEntity;
import com.apiculture.simulator.data.local.entity.PollinationContractEntity;
import com.apiculture.simulator.data.local.entity.CargoTripEntity;
import com.apiculture.simulator.data.local.entity.TruckTripEntity;
import com.apiculture.simulator.data.repository.GameStartupWarmup;
import com.apiculture.simulator.data.repository.HeadquartersStore;
import com.apiculture.simulator.data.repository.HexParcelRepository;
import com.apiculture.simulator.data.repository.IberiaHexOverlayStore;
import com.apiculture.simulator.presentation.market.MarketFragment;
import com.apiculture.simulator.presentation.market.MarketPickerDialogs;
import com.apiculture.simulator.presentation.profile.ProfilePhoto;
import com.apiculture.simulator.data.repository.MapOverlayPrefs;
import com.apiculture.simulator.data.repository.MapRegionPrefs;
import com.apiculture.simulator.data.repository.RoutingGraphDownloader;
import com.apiculture.simulator.data.repository.TruckLivePrefs;
import com.apiculture.simulator.presentation.common.GraphInstallDialog;
import com.apiculture.simulator.data.repository.HoneyLogistics;
import com.apiculture.simulator.data.repository.HoneyOrderStore;
import com.apiculture.simulator.data.repository.PollinationContractRepository;
import com.apiculture.simulator.data.repository.TruckLiveTrips;
import com.apiculture.simulator.databinding.DialogHexPurchaseBinding;
import com.apiculture.simulator.databinding.DialogMapHiveBinding;
import com.apiculture.simulator.databinding.DialogMapParcelBinding;
import com.apiculture.simulator.databinding.DialogPlantFloraBinding;
import com.apiculture.simulator.databinding.FragmentSharedMapBinding;
import com.apiculture.simulator.domain.game.ClimateUnlock;
import com.apiculture.simulator.domain.game.FleetRules;
import com.apiculture.simulator.domain.game.GameCalendar;
import com.apiculture.simulator.domain.game.HoneyOrder;
import com.apiculture.simulator.domain.game.OfferBand;
import com.apiculture.simulator.domain.game.NpcContractCatalog;
import com.apiculture.simulator.domain.market.HoneyMarketEngine;
import com.apiculture.simulator.domain.game.NpcContractFarm;
import com.apiculture.simulator.domain.game.PollinationContractRules;
import com.apiculture.simulator.domain.game.TranshumanceRules;
import com.apiculture.simulator.domain.game.CargoTripRules;
import com.apiculture.simulator.domain.game.TruckTripRules;
import com.apiculture.simulator.domain.game.IberianClimateZone;
import com.apiculture.simulator.domain.game.MadagascarClimateZone;
import com.apiculture.simulator.domain.game.SouthernAfricanClimateZone;
import com.apiculture.simulator.domain.map.PlayableMapRegion;
import com.apiculture.simulator.domain.map.ProvincialMarket;
import com.apiculture.simulator.domain.map.ProvincialMarketCatalog;
import com.apiculture.simulator.domain.map.RoadKind;
import com.apiculture.simulator.domain.parcel.BoundingBox;
import com.apiculture.simulator.domain.parcel.CropUnlock;
import com.apiculture.simulator.domain.parcel.HexParcel;
import com.apiculture.simulator.domain.parcel.WarehouseRules;
import com.apiculture.simulator.domain.parcel.HexParcelRandomPoint;
import com.apiculture.simulator.domain.parcel.HexFlora;
import com.apiculture.simulator.domain.parcel.HexParcelGameRules;
import com.apiculture.simulator.presentation.common.SimpleViewModelFactory;
import com.apiculture.simulator.presentation.market.AcceptContractDialogs;
import com.apiculture.simulator.presentation.market.ContractCardDialogs;
import com.apiculture.simulator.presentation.market.HoneyOrderDialogs;
import com.apiculture.simulator.presentation.market.LocalMarketDialogs;
import com.apiculture.simulator.presentation.market.MarketContractsAdapter;
import com.apiculture.simulator.presentation.market.NpcPortraitUi;
import com.apiculture.simulator.presentation.hive.BuyHiveDialogs;
import com.apiculture.simulator.presentation.hive.SiteSellDialogs;
import com.apiculture.simulator.presentation.hive.FleetDialogs;
import com.apiculture.simulator.presentation.hive.WarehouseDialogs;
import com.apiculture.simulator.presentation.hive.HiveSiteSummaryUi;
import com.apiculture.simulator.presentation.hive.HiveViewModel;
import com.apiculture.simulator.presentation.tutorial.TutorialBus;
import com.apiculture.simulator.presentation.tutorial.TutorialEvent;
import com.google.android.gms.maps.model.VisibleRegion;
import com.google.android.material.button.MaterialButton;
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
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.apiculture.simulator.data.session.PlayerAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class SharedMapFragment extends Fragment implements OnMapReadyCallback, OnMarkerClickListener {

    public static final String ARG_HINT_TRANSHUMANCE = "hintTranshumance";
    public static final String ARG_FOCUS_LAT = "focusLat";
    public static final String ARG_FOCUS_LNG = "focusLng";
    public static final String ARG_FOCUS_ZOOM = "focusZoom";
    public static final String ARG_FOCUS_TRIP_ID = "focusTripId";
    public static final String ARG_FOCUS_HEX_ID = "focusHexId";

    /** Opacidad relleno ~50&nbsp;% (ARGB: 128/255). */
    private static final int HEX_FILL_ALPHA = 128;
    private static final int HEX_FREE_FILL = Color.argb(HEX_FILL_ALPHA, 129, 199, 132);
    /** Hex libre cuya flora nativa aún no desbloquea el jugador: no se puede comprar. */
    private static final int HEX_FREE_LOCKED_FILL = Color.argb(HEX_FILL_ALPHA, 229, 115, 115);
    private static final int HEX_RESERVE_FILL = Color.argb(HEX_FILL_ALPHA, 255, 152, 0);
    private static final int HEX_CONTRACT_FILL = Color.argb(HEX_FILL_ALPHA, 255, 235, 59);
    private static final int HEX_OWN_FILL = Color.argb(HEX_FILL_ALPHA, 186, 104, 200);
    /** Terrenos comprados por otros jugadores: azul. */
    private static final int HEX_OTHER_FILL = Color.argb(HEX_FILL_ALPHA, 41, 121, 255);
    private static final float HEX_STROKE_WIDTH = 4.6f;
    /**
     * Por debajo no se dibujan hexágonos.
     * Alineado con el zoom mínimo regional para que la malla se vea al abrir el mapa.
     */
    private static final float MIN_ZOOM_HEX = 6.5f;
    /** Máximo de hex dibujados; se eligen los más cercanos al centro de pantalla. */
    private static final int MAX_HEX_VISIBLE = 450;
    private static final float ZOOM_FULL_PENINSULA_HEX = 8f;
    /** Por debajo no se pintan apiarios, comandas ni contratos. */
    private static final float ZOOM_PIN_MIN = 8f;
    /** Por debajo, los apiarios que se pisan se agrupan. */
    private static final float ZOOM_PIN_SPLIT = 11f;
    /** A partir de aquí el apiario lleva nombre y icono grande. */
    private static final float ZOOM_PIN_DETAIL = 16f;
    /** Zoom al abrir el mapa (sin foco concreto): se ven las ofertas de comanda. */
    public static final float OPEN_MAP_ZOOM = 8.6f;
    private static final int OVERLAY_DEBOUNCE_MS = 220;
    /** Polígonos por frame: addPolygon en bloque congela Maps varios segundos. */
    private static final int HEX_ADD_BATCH = 24;
    private static final int APIARY_REFRESH_DEBOUNCE_MS = 80;
    private static final int HIVE_MARKER_DEBOUNCE_MS = 180;
    private static final long TRUCK_TICK_MS = 1000L;
    private static final float FOCUS_TRIP_ZOOM = 16f;
    public static final float FOCUS_APIARY_ZOOM = 16.6f;
    private static final int TRUCK_ROUTE_COLOR = Color.argb(200, 33, 150, 243);

    private FragmentSharedMapBinding binding;
    private GoogleMap googleMap;
    private HiveViewModel hiveViewModel;
    private String currentUserId = "guest";
    private final Map<String, HiveEntity> hiveById = new HashMap<>();
    private String selectedOwnHiveId;
    private final Set<String> lastOpenContractHexIds = new HashSet<>();
    private final List<Polygon> hexOverlayPolygons = new ArrayList<>();
    private final List<Marker> hiveMarkers = new ArrayList<>();
    private final List<Marker> apiaryMarkers = new ArrayList<>();
    private final Map<String, Marker> truckMarkers = new HashMap<>();
    private final Map<String, List<Polyline>> truckRoutes = new HashMap<>();
    private List<TruckTripEntity> lastTrips = Collections.emptyList();
    private List<CargoTripEntity> lastCargo = Collections.emptyList();
    private final Map<String, Marker> cargoMarkers = new HashMap<>();
    private final Map<String, List<Polyline>> cargoRoutes = new HashMap<>();
    private BitmapDescriptor truckIcon;
    private BitmapDescriptor honeyTruckIcon;
    private final List<Marker> marketMarkers = new ArrayList<>();
    private final List<Marker> shopMarkers = new ArrayList<>();
    private final List<Marker> portMarkers = new ArrayList<>();
    private final List<Marker> warehouseMarkers = new ArrayList<>();
    @Nullable
    private BitmapDescriptor shopIcon;
    @Nullable
    private BitmapDescriptor portIcon;
    @Nullable
    private Marker hqMarker;
    @Nullable
    private Bitmap profileFaceBitmap;
    @Nullable
    private BitmapDescriptor hqIcon;
    private int hqIconDp;
    @Nullable
    private GameStartupWarmup.Listener overlayWarmupListener;
    private final List<Marker> orderMarkers = new ArrayList<>();
    private final List<Marker> contractMarkers = new ArrayList<>();
    private List<HoneyOrderEntity> lastOrders = Collections.emptyList();
    private List<PollinationContractRepository.Offer> lastContractOffers = Collections.emptyList();
    private final Map<String, BitmapDescriptor> contractFloraIcons = new HashMap<>();
    @Nullable
    private List<HexFloraSaturation.Line> lastFloraSaturations =
            Collections.emptyList();
    private final Map<String, BitmapDescriptor> orderFaceIcons = new HashMap<>();
    private final Map<String, BitmapDescriptor> clusterIcons = new HashMap<>();
    private final Map<Integer, BitmapDescriptor> warehouseIcons = new HashMap<>();
    private final Runnable orderExpireTick = this::pruneExpiredOrderMarkers;
    private BitmapDescriptor marketIcon;
    private final Map<String, Bitmap> ownerFaces = new HashMap<>();
    private final Map<String, BitmapDescriptor> otherApiaryIcons = new HashMap<>();

    @Nullable
    private Bitmap fallbackFaceBitmap;
    @Nullable
    private Bitmap hiveIconSrc;
    private final Map<String, BitmapDescriptor> labeledHiveIcons = new HashMap<>();
    @Nullable
    private List<Pair<PolygonOptions, String>> pendingHexSpecs;
    private int pendingHexIndex;
    private int pendingHexGeneration = -1;
    @Nullable
    private GoogleMap pendingHexMap;
    private final Runnable hexAddNextBatch = this::addNextHexPolygonBatch;
    private final Runnable apiaryRefreshDebounced = this::refreshApiaryMarkers;
    private final Runnable hiveMarkersDebounced = this::renderLastHiveMarkers;
    private final Runnable truckTick = this::tickTrucks;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private ExecutorService hexOverlayExecutor;
    private ExecutorService tripExecutor;
    private final AtomicBoolean tripUpkeep = new AtomicBoolean(false);
    private final AtomicInteger hexOverlayGeneration = new AtomicInteger(0);
    private PlayableMapRegion activeRegion = PlayableMapRegion.IBERIA;
    private List<HiveEntity> lastHives = Collections.emptyList();
    private List<HexParcelOwnershipEntity> lastOwnerships = Collections.emptyList();
    private boolean cameraPlacedForRegion;
    private double pendingFocusLat;
    private double pendingFocusLng;
    private float pendingFocusZoom;
    @Nullable
    private String pendingFocusHexId;
    private boolean suppressRegionToggle;
    private boolean mapOrderAcceptOpen;
    private boolean suppressFloraSpinner;
    private float lastMarkerZoomBucket = Float.NaN;
    private boolean lastMarketLocalsVisible;
    private double lastTapLat = Double.NaN;
    private double lastTapLng = Double.NaN;
    private float lastHiveMarkerAnchorY = 0.78f;
    private final Runnable overlayDebounced = this::refreshHexParcelOverlayNow;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        hexOverlayExecutor = Executors.newSingleThreadExecutor();
        tripExecutor = Executors.newSingleThreadExecutor();
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
        String uid = PlayerAuth.getInstance().getUid();
        if (uid != null) currentUserId = uid;

        activeRegion = MapRegionPrefs.get(requireContext());
        if (activeRegion == PlayableMapRegion.SOUTH_AFRICA && !zaUnlocked()) {
            activeRegion = PlayableMapRegion.IBERIA;
            MapRegionPrefs.set(requireContext(), PlayableMapRegion.IBERIA);
        }
        setupRegionToggle();
        setupMapHud();

        hiveViewModel.hexOwnerships().observe(getViewLifecycleOwner(), rows -> {
            lastOwnerships = rows != null ? rows : Collections.emptyList();
            app.getHexParcelRepository().repairSplitWarehouseSites(currentUserId);
            scheduleHexOverlayRefresh();
            refreshWarehouseMarkers();
            refreshHqMarker();
            prefetchOwnerFaces();
            scheduleApiaryRefresh();
            scheduleContractPins();
            tryApplyPendingFocus();
        });
        loadHeadquartersAndFace();
        HoneyOrderStore.observeMapFaces(requireContext(), currentUserId).observe(getViewLifecycleOwner(), rows -> {
            lastOrders = rows != null ? rows : Collections.emptyList();
            refreshOrderMarkers();
            mainHandler.removeCallbacks(orderExpireTick);
            mainHandler.post(orderExpireTick);
        });
        TruckLiveTrips.observe(requireContext()).observe(getViewLifecycleOwner(), trips -> {
            lastTrips = ownTruckTrips(trips);
            TruckLiveTrips.rerouteStraightIfNeeded(requireContext(), lastTrips);
            if (googleMap != null) {
                scheduleHiveMarkerRefresh();
                syncTruckOverlay();
                startTruckTicker();
            }
        });
        HoneyLogistics.observe(requireContext()).observe(getViewLifecycleOwner(), trips -> {
            lastCargo = ownCargoTrips(trips);
            if (googleMap != null) {
                syncCargoOverlay();
                startTruckTicker();
            }
        });

        SupportMapFragment mapFragment = (SupportMapFragment)
                getChildFragmentManager().findFragmentById(R.id.map_container);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }
    }

    @Override
    public void onMapReady(@NonNull GoogleMap map) {
        googleMap = map;
        // El primer observer puede llegar antes de que termine el warmup del
        // router. Reintentar aquí evita que una línea recta quede permanente.
        Context app = requireContext().getApplicationContext();
        List<TruckTripEntity> tripsAtReady = new ArrayList<>(lastTrips);
        TruckLiveTrips.rerouteStraightIfNeeded(app, tripsAtReady);
        mainHandler.postDelayed(
                () -> TruckLiveTrips.rerouteStraightIfNeeded(app, tripsAtReady), 1500L);
        googleMap.setOnMarkerClickListener(this);
        googleMap.setOnCameraIdleListener(this::onCameraIdle);
        googleMap.setOnPolygonClickListener(this::onHexPolygonClick);
        ingestFocusArgs();
        applyCameraConstraints(!hasPendingFocus());
        observeHivesAndRenderMarkers();
        setupMapInteractions();
        tryApplyPendingFocus();
        googleMap.setOnMapLoadedCallback(this::drawDeferredMapOverlays);
        mainHandler.post(this::drawDeferredMapOverlays);
    }

    private void drawDeferredMapOverlays() {
        if (!isAdded() || googleMap == null) {
            return;
        }
        syncTruckOverlay();
        syncCargoOverlay();
        startTruckTicker();
        refreshMarketMarkers();
        refreshPortMarkers();
        refreshWarehouseMarkers();
        refreshHqMarker();
        refreshOrderMarkers();
        scheduleContractPins();
        scheduleHexOverlayRefresh();
    }

    @NonNull
    private List<TruckTripEntity> ownTruckTrips(@Nullable List<TruckTripEntity> trips) {
        if (trips == null || trips.isEmpty()) {
            return Collections.emptyList();
        }
        List<TruckTripEntity> mine = new ArrayList<>();
        for (TruckTripEntity trip : trips) {
            if (trip != null && currentUserId.equals(trip.ownerId)) {
                mine.add(trip);
            }
        }
        return mine;
    }

    @NonNull
    private List<CargoTripEntity> ownCargoTrips(@Nullable List<CargoTripEntity> trips) {
        if (trips == null || trips.isEmpty()) {
            return Collections.emptyList();
        }
        List<CargoTripEntity> mine = new ArrayList<>();
        for (CargoTripEntity trip : trips) {
            if (trip != null && currentUserId.equals(trip.ownerId)) {
                mine.add(trip);
            }
        }
        return mine;
    }

    private void observeHivesAndRenderMarkers() {
        hiveViewModel.hives(currentUserId).observe(getViewLifecycleOwner(), hives -> {
            lastHives = hives != null ? hives : Collections.emptyList();
            scheduleHiveMarkerRefresh();
            if (!cameraPlacedForRegion && !hasPendingFocus()) {
                moveCameraToOwnOrFirstHive(lastHives);
                cameraPlacedForRegion = true;
            }
            scheduleHexOverlayRefresh();
            scheduleContractPins();
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
            refreshApiaryMarkers();
            return;
        }
        for (HiveEntity hive : hives) {
            if (hive == null || hive.inWarehouse) {
                continue;
            }
            if (!activeRegion.containsHive(hive.lat, hive.lng)) {
                continue;
            }
            hiveById.put(hive.id, hive);
        }
        refreshApiaryMarkers();
        syncTruckOverlay();
    }

    private void renderLastHiveMarkers() {
        renderHiveMarkers(lastHives);
    }

    private void scheduleHiveMarkerRefresh() {
        mainHandler.removeCallbacks(hiveMarkersDebounced);
        mainHandler.postDelayed(hiveMarkersDebounced, HIVE_MARKER_DEBOUNCE_MS);
    }

    private void scheduleApiaryRefresh() {
        mainHandler.removeCallbacks(apiaryRefreshDebounced);
        mainHandler.postDelayed(apiaryRefreshDebounced, APIARY_REFRESH_DEBOUNCE_MS);
    }

    private void refreshApiaryMarkers() {
        if (googleMap == null || !isAdded()) {
            return;
        }
        for (Marker marker : apiaryMarkers) {
            marker.remove();
        }
        apiaryMarkers.clear();
        if (mapZoom() < ZOOM_PIN_MIN) {
            return;
        }
        Context app = requireContext().getApplicationContext();
        List<HexParcelOwnershipEntity> ownRows = new ArrayList<>();
        List<MapPinGroups.Pin> ownPins = new ArrayList<>();
        List<HexParcelOwnershipEntity> otherRows = new ArrayList<>();
        List<double[]> otherSites = new ArrayList<>();
        for (HexParcelOwnershipEntity row : lastOwnerships) {
            if (row == null || row.hexId == null || row.ownerId == null) {
                continue;
            }
            HexParcel parcel = IberiaHexOverlayStore.findById(app, row.hexId);
            if (parcel == null || !activeRegion.containsHive(parcel.centroidLat, parcel.centroidLon)) {
                continue;
            }
            if (!WarehouseRules.isApiarySite(row)) {
                continue;
            }
            double[] site = HexParcelRandomPoint.siteOf(parcel, row);
            if (currentUserId.equals(row.ownerId)) {
                ownPins.add(new MapPinGroups.Pin(site[0], site[1], ownPins.size()));
                ownRows.add(row);
            } else {
                otherSites.add(site);
                otherRows.add(row);
            }
        }
        float zoom = mapZoom();
        boolean named = zoom >= ZOOM_PIN_DETAIL;
        List<MapPinGroups.Group> groups = zoom < ZOOM_PIN_SPLIT
                ? MapPinGroups.group(googleMap.getProjection(), ownPins, 56f * density())
                : MapPinGroups.singles(ownPins);
        for (MapPinGroups.Group group : groups) {
            if (group.pins.size() > 1) {
                Marker cluster = addClusterMarker(group, 2.4f, R.color.event_gold, R.color.event_ink);
                if (cluster != null) {
                    apiaryMarkers.add(cluster);
                }
                continue;
            }
            placeApiaryMarker(ownRows.get(group.pins.get(0).index), group.lat, group.lng, named);
        }
        for (int i = 0; i < otherRows.size(); i++) {
            double[] site = otherSites.get(i);
            placeApiaryMarker(otherRows.get(i), site[0], site[1], named);
        }
    }

    private void placeApiaryMarker(@NonNull HexParcelOwnershipEntity row, double lat, double lng,
            boolean named) {
        boolean own = currentUserId.equals(row.ownerId);
        String otherName = own ? null : HexParcelRepository.playerNameFor(row.ownerId);
        String label = otherName != null && !otherName.isEmpty()
                ? otherName
                : (row.parcelName != null && !row.parcelName.trim().isEmpty()
                ? row.parcelName.trim()
                : (own
                ? getString(R.string.hex_own_parcel_title)
                : getString(R.string.map_other_player_hive)));
        BitmapDescriptor icon = own
                ? createLabeledHiveIcon(true, label, named)
                : otherApiaryIcon(row.ownerId, label, named);
        Marker marker = googleMap.addMarker(new MarkerOptions()
                .position(new LatLng(lat, lng))
                .title(named ? label : null)
                .anchor(0.5f, lastHiveMarkerAnchorY)
                .zIndex(own ? 2.2f : 2.0f)
                .icon(icon));
        if (marker != null) {
            marker.setTag(apiaryTag(own, row));
            apiaryMarkers.add(marker);
        }
    }

    private void prefetchOwnerFaces() {
        if (!isAdded()) {
            return;
        }
        ApicultureApp app = (ApicultureApp) requireContext().getApplicationContext();
        for (HexParcelOwnershipEntity row : lastOwnerships) {
            if (row == null || row.ownerId == null || currentUserId.equals(row.ownerId)) {
                continue;
            }
            if (ownerFaces.containsKey(row.ownerId)) {
                continue;
            }
            ownerFaces.put(row.ownerId, null);
            final String ownerId = row.ownerId;
            app.getProfileRepository().fetchDisplayProfile(ownerId, profile -> {
                if (!isAdded()) {
                    return;
                }
                ownerFaces.put(ownerId, ProfilePhoto.decodeBase64(profile.photoBase64));
                String prefix = ownerId + "|";
                otherApiaryIcons.entrySet().removeIf(entry -> entry.getKey().startsWith(prefix));
                scheduleApiaryRefresh();
            });
        }
    }

    @NonNull
    private BitmapDescriptor otherApiaryIcon(@Nullable String ownerId, @Nullable String rawName,
            boolean showLabel) {
        String label = showLabel ? markerLabel(rawName) : "";
        int iconSize = apiaryGraphicPx();
        String key = (ownerId != null ? ownerId : "") + "|" + iconSize + "|" + label;
        BitmapDescriptor cached = otherApiaryIcons.get(key);
        if (cached != null) {
            lastHiveMarkerAnchorY = showLabel ? hiveAnchorY(iconSize, label, iconSize) : 0.5f;
            return cached;
        }
        Bitmap src = hiveIconSrc();
        if (src == null) {
            return BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE);
        }
        Bitmap scaled = Bitmap.createScaledBitmap(src, iconSize, iconSize, true);
        Bitmap gray = Bitmap.createBitmap(iconSize, iconSize, Bitmap.Config.ARGB_8888);
        Canvas grayCanvas = new Canvas(gray);
        Paint grayPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        ColorMatrix matrix = new ColorMatrix();
        matrix.setSaturation(0f);
        grayPaint.setColorFilter(new ColorMatrixColorFilter(matrix));
        grayCanvas.drawBitmap(scaled, 0, 0, grayPaint);
        Bitmap faced = ProfilePhoto.composeOtherApiary(
                gray, ownerId != null ? ownerFaces.get(ownerId) : null, fallbackFaceBitmap());
        BitmapDescriptor icon = finishApiaryIcon(faced, label, showLabel);
        otherApiaryIcons.put(key, icon);
        return icon;
    }

    @NonNull
    private Bitmap fallbackFaceBitmap() {
        if (fallbackFaceBitmap != null) {
            return fallbackFaceBitmap;
        }
        Bitmap src = BitmapFactory.decodeResource(getResources(), R.drawable.ic_apicultor);
        if (src == null) {
            fallbackFaceBitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888);
            return fallbackFaceBitmap;
        }
        fallbackFaceBitmap = src;
        return fallbackFaceBitmap;
    }

    private void startTruckTicker() {
        mainHandler.removeCallbacks(truckTick);
        if (lastTrips.isEmpty() && lastCargo.isEmpty()) {
            return;
        }
        mainHandler.post(truckTick);
    }

    private void tickTrucks() {
        if (!isAdded() || googleMap == null) {
            return;
        }
        long now = System.currentTimeMillis();
        boolean anyDue = false;
        for (TruckTripEntity trip : lastTrips) {
            if (trip == null) {
                continue;
            }
            if (TruckTripRules.wallClockDone(trip, now)) {
                anyDue = true;
                continue;
            }
            Marker marker = truckMarkers.get(trip.hiveId);
            if (marker != null) {
                double[] p = TruckTripRules.position(trip, now);
                marker.setPosition(new LatLng(p[0], p[1]));
            }
        }
        boolean revealCargo = false;
        for (CargoTripEntity trip : lastCargo) {
            if (trip == null) {
                continue;
            }
            if (HoneyLogistics.tourClockDone(trip, now)) {
                anyDue = true;
                Marker done = cargoMarkers.get(trip.id);
                if (done != null) {
                    poseCargoMarker(done, trip, HoneyLogistics.tourPosition(trip, now));
                }
                continue;
            }
            if (now < trip.startEpochMs) {
                Marker waiting = cargoMarkers.remove(trip.id);
                if (waiting != null) {
                    waiting.remove();
                }
                continue;
            }
            Marker marker = cargoMarkers.get(trip.id);
            if (marker == null) {
                revealCargo = true;
                continue;
            }
            double[] p = HoneyLogistics.tourPosition(trip, now);
            poseCargoMarker(marker, trip, p);
        }
        if (revealCargo) {
            syncCargoOverlay();
        }
        if (anyDue) {
            completeDueTripsAsync();
        }
        if (!lastTrips.isEmpty() || !lastCargo.isEmpty()) {
            mainHandler.postDelayed(truckTick, TRUCK_TICK_MS);
        }
    }

    private void completeDueTripsAsync() {
        ExecutorService io = tripExecutor;
        if (io == null || io.isShutdown() || !tripUpkeep.compareAndSet(false, true)) {
            return;
        }
        Context app = requireContext().getApplicationContext();
        io.execute(() -> {
            try {
                ApicultureApp game = (ApicultureApp) app;
                TruckLiveTrips.completeDue(app);
                HoneyLogistics.completeDue(app, game.getEconomyRepository(), game.getMarketRepository());
            } finally {
                tripUpkeep.set(false);
            }
        });
    }

    private void syncTruckOverlay() {
        if (googleMap == null) {
            return;
        }
        Set<String> live = TruckLiveTrips.activeHiveIds(lastTrips);
        Iterator<Map.Entry<String, Marker>> markerIt = truckMarkers.entrySet().iterator();
        while (markerIt.hasNext()) {
            Map.Entry<String, Marker> e = markerIt.next();
            if (!live.contains(e.getKey())) {
                e.getValue().remove();
                markerIt.remove();
            }
        }
        Iterator<Map.Entry<String, List<Polyline>>> routeIt = truckRoutes.entrySet().iterator();
        while (routeIt.hasNext()) {
            Map.Entry<String, List<Polyline>> e = routeIt.next();
            if (!live.contains(e.getKey())) {
                removePolylines(e.getValue());
                routeIt.remove();
            }
        }
        long now = System.currentTimeMillis();
        for (TruckTripEntity trip : lastTrips) {
            if (trip == null || trip.hiveId == null) {
                continue;
            }
            double[] p = TruckTripRules.position(trip, now);
            if (!truckVisibleInRegion(trip, p)) {
                Marker hidden = truckMarkers.remove(trip.hiveId);
                if (hidden != null) {
                    hidden.remove();
                }
                List<Polyline> hiddenRoute = truckRoutes.remove(trip.hiveId);
                removePolylines(hiddenRoute);
                continue;
            }
            LatLng pos = new LatLng(p[0], p[1]);
            BitmapDescriptor icon = fleetTruckIcon(truckLevelForHive(trip.hiveId));
            Marker marker = truckMarkers.get(trip.hiveId);
            if (marker == null) {
                HiveEntity hive = hiveById.get(trip.hiveId);
                String title = hive != null && hive.name != null ? hive.name : getString(R.string.truck_live_ask_title);
                marker = googleMap.addMarker(new MarkerOptions()
                        .position(pos)
                        .title(title)
                        .anchor(0.5f, 0.5f)
                        .rotation(0f)
                        .flat(false)
                        .zIndex(5f)
                        .icon(icon));
                if (marker != null) {
                    marker.setTag("truck:" + trip.hiveId);
                    truckMarkers.put(trip.hiveId, marker);
                }
            } else {
                marker.setPosition(pos);
                marker.setRotation(0f);
                marker.setIcon(icon);
                marker.setTag("truck:" + trip.hiveId);
            }
            List<double[]> pts = TruckTripRules.routePoints(trip);
            List<LatLng> latLngs = new ArrayList<>(pts.size());
            for (double[] pt : pts) {
                latLngs.add(new LatLng(pt[0], pt[1]));
            }
            syncKindedRoute(truckRoutes, trip.hiveId, latLngs, trip.routeRoadKinds, 1.4f);
        }
    }

    private boolean truckVisibleInRegion(@NonNull TruckTripEntity trip, @NonNull double[] pos) {
        return activeRegion.containsHive(pos[0], pos[1])
                || activeRegion.containsHive(trip.originLat, trip.originLng)
                || activeRegion.containsHive(trip.destLat, trip.destLng);
    }

    private void syncKindedRoute(@NonNull Map<String, List<Polyline>> store, @NonNull String id,
            @NonNull List<LatLng> latLngs, @Nullable String kinds, float zIndex) {
        if (googleMap == null || latLngs.size() < 2) {
            return;
        }
        syncStrokes(store, id, routeStrokes(latLngs, kinds, zIndex));
    }

    private void syncStrokes(@NonNull Map<String, List<Polyline>> store, @NonNull String id,
            @NonNull List<PolylineOptions> strokes) {
        if (googleMap == null || strokes.isEmpty()) {
            return;
        }
        List<Polyline> existing = store.get(id);
        if (existing != null && existing.size() == strokes.size() && colorsMatch(existing, strokes)) {
            for (int i = 0; i < strokes.size(); i++) {
                existing.get(i).setPoints(strokes.get(i).getPoints());
            }
            return;
        }
        removePolylines(existing);
        List<Polyline> next = new ArrayList<>(strokes.size());
        for (PolylineOptions opt : strokes) {
            next.add(googleMap.addPolyline(opt));
        }
        store.put(id, next);
    }

    @NonNull
    private static List<PolylineOptions> routeStrokes(@NonNull List<LatLng> pts,
            @Nullable String kinds, float zIndex) {
        List<PolylineOptions> out = new ArrayList<>();
        int edges = pts.size() - 1;
        if (edges <= 0) {
            return out;
        }
        PolylineOptions line = new PolylineOptions()
                .color(TRUCK_ROUTE_COLOR)
                .width(5f)
                .geodesic(false)
                .zIndex(zIndex);
        for (LatLng pt : pts) {
            line.add(pt);
        }
        out.add(line);
        return out;
    }

    private static boolean colorsMatch(@NonNull List<Polyline> existing,
            @NonNull List<PolylineOptions> strokes) {
        if (existing.size() != strokes.size()) {
            return false;
        }
        for (int i = 0; i < existing.size(); i++) {
            if (existing.get(i).getColor() != strokes.get(i).getColor()) {
                return false;
            }
        }
        return true;
    }

    private static void removePolylines(@Nullable List<Polyline> lines) {
        if (lines == null) {
            return;
        }
        for (Polyline p : lines) {
            if (p != null) {
                p.remove();
            }
        }
    }

    private void refreshRoadLegend() {
        if (binding == null || binding.cardMapLegend == null) {
            return;
        }
        binding.cardMapLegend.setVisibility(View.VISIBLE);
        ViewGroup.LayoutParams raw = binding.cardMapLegend.getLayoutParams();
        if (!(raw instanceof ViewGroup.MarginLayoutParams)) {
            return;
        }
        ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) raw;
        boolean climate = binding.cardClimateLegend != null
                && binding.cardClimateLegend.getVisibility() == View.VISIBLE;
        float density = getResources().getDisplayMetrics().density;
        lp.bottomMargin = Math.round((climate ? 96f : 12f) * density);
        binding.cardMapLegend.setLayoutParams(lp);
    }

    @NonNull
    private BitmapDescriptor truckIcon() {
        if (truckIcon != null) {
            return truckIcon;
        }
        Drawable d = ContextCompat.getDrawable(requireContext(), R.drawable.ic_pickup_colmenas);
        float density = getResources().getDisplayMetrics().density;
        int w = Math.round(72f * density);
        int h = Math.round(40f * density);
        if (d != null) {
            int iw = d.getIntrinsicWidth();
            int ih = d.getIntrinsicHeight();
            if (iw > 0 && ih > 0) {
                h = Math.max(1, Math.round(w * (ih / (float) iw)));
            }
        }
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        if (d != null) {
            d = d.mutate();
            d.setBounds(0, 0, w, h);
            d.draw(canvas);
        }
        truckIcon = BitmapDescriptorFactory.fromBitmap(bmp);
        return truckIcon;
    }

    private void poseCargoMarker(@NonNull Marker marker, @NonNull CargoTripEntity trip, @NonNull double[] p) {
        boolean atSea = CargoTripEntity.LEG_HAUL_SHIP.equals(trip.legRole);
        boolean onThisMap = activeRegion == null || activeRegion.box().containsLatLon(p[0], p[1]);
        marker.setPosition(new LatLng(p[0], p[1]));
        marker.setVisible(!atSea || onThisMap);
        if (atSea && nearPort(p[0], p[1])) {
            marker.setAnchor(0.5f, 0f);
            marker.setZIndex(3.2f);
        } else {
            marker.setAnchor(0.5f, 0.5f);
            marker.setZIndex(5.2f);
        }
    }

    private boolean nearPort(double lat, double lng) {
        for (Seaport port
                : SeaportCatalog.all()) {
            if (TranshumanceRules.haversineKm(
                    lat, lng, port.lat, port.lng) < 8.0) {
                return true;
            }
        }
        return false;
    }

    private void syncCargoOverlay() {
        if (googleMap == null) {
            return;
        }
        Set<String> live = new HashSet<>();
        for (CargoTripEntity trip : lastCargo) {
            if (trip != null && trip.id != null) {
                live.add(trip.id);
            }
        }
        Iterator<Map.Entry<String, Marker>> markerIt = cargoMarkers.entrySet().iterator();
        while (markerIt.hasNext()) {
            Map.Entry<String, Marker> e = markerIt.next();
            if (!live.contains(e.getKey())) {
                e.getValue().remove();
                markerIt.remove();
            }
        }
        Iterator<Map.Entry<String, List<Polyline>>> routeIt = cargoRoutes.entrySet().iterator();
        while (routeIt.hasNext()) {
            Map.Entry<String, List<Polyline>> e = routeIt.next();
            if (!live.contains(e.getKey())) {
                removePolylines(e.getValue());
                routeIt.remove();
            }
        }
        long now = System.currentTimeMillis();
        for (CargoTripEntity trip : lastCargo) {
            if (trip == null || trip.id == null) {
                continue;
            }
            if (now < trip.startEpochMs) {
                Marker waiting = cargoMarkers.remove(trip.id);
                if (waiting != null) {
                    waiting.remove();
                }
                List<Polyline> waitingRoute = cargoRoutes.remove(trip.id);
                if (waitingRoute != null) {
                    removePolylines(waitingRoute);
                }
                continue;
            }
            double[] p = HoneyLogistics.tourPosition(trip, now);
            boolean atSea = CargoTripEntity.LEG_HAUL_SHIP.equals(trip.legRole);
            BitmapDescriptor icon = atSea ? movingShipIcon(shipLevel(trip)) : fleetTruckIcon(truckLevel(trip));
            LatLng pos = new LatLng(p[0], p[1]);
            Marker marker = cargoMarkers.get(trip.id);
            if (marker == null) {
                marker = googleMap.addMarker(new MarkerOptions()
                        .position(pos)
                        .title(HoneyLogistics.cargoSummary(trip))
                        .anchor(0.5f, 0.5f)
                        .zIndex(5.2f)
                        .icon(icon));
                if (marker != null) {
                    marker.setTag("cargo:" + trip.id);
                    cargoMarkers.put(trip.id, marker);
                }
            } else {
                marker.setIcon(icon);
            }
            if (marker != null) {
                poseCargoMarker(marker, trip, p);
            }
            List<double[]> pts = CargoTripRules.routePoints(trip);
            List<LatLng> latLngs = new ArrayList<>(pts.size());
            for (double[] pt : pts) {
                latLngs.add(new LatLng(pt[0], pt[1]));
            }
            List<PolylineOptions> strokes = routeStrokes(latLngs, trip.routeRoadKinds, 1.5f);
            for (HoneyLogistics.DrawnLeg extra : HoneyLogistics.futureCollectRoutes(trip)) {
                List<LatLng> more = new ArrayList<>(extra.points.size());
                for (double[] pt : extra.points) {
                    more.add(new LatLng(pt[0], pt[1]));
                }
                strokes.addAll(routeStrokes(more, extra.roadKinds, 1.5f));
            }
            syncStrokes(cargoRoutes, trip.id, strokes);
        }
    }

    @NonNull
    private BitmapDescriptor honeyTruckIcon() {
        if (honeyTruckIcon != null) {
            return honeyTruckIcon;
        }
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
        opts.inScaled = false;
        Bitmap src = BitmapFactory.decodeResource(getResources(), R.drawable.ic_pickup_miel, opts);
        float density = getResources().getDisplayMetrics().density;
        int w = Math.round(72f * density);
        int h = Math.round(40f * density);
        if (src != null && src.getWidth() > 0 && src.getHeight() > 0) {
            h = Math.max(1, Math.round(w * (src.getHeight() / (float) src.getWidth())));
        }
        Bitmap bmp;
        if (src != null) {
            Bitmap scaled = Bitmap.createScaledBitmap(src, w, h, true);
            if (scaled != src) {
                src.recycle();
            }
            bmp = scaled.isMutable() ? scaled : scaled.copy(Bitmap.Config.ARGB_8888, true);
            if (bmp != scaled) {
                scaled.recycle();
            }
        } else {
            bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        }
        punchWhiteBackground(bmp);
        honeyTruckIcon = BitmapDescriptorFactory.fromBitmap(bmp);
        return honeyTruckIcon;
    }

    private int hiveCountOnHex(@Nullable String hexId) {
        if (hexId == null || hexId.isEmpty() || lastHives == null) {
            return 0;
        }
        int n = 0;
        for (HiveEntity hive : lastHives) {
            if (hive != null && !hive.inWarehouse && hexId.equals(hive.hexId)
                    && currentUserId.equals(hive.ownerId)) {
                n++;
            }
        }
        return n;
    }

    private void ingestFocusArgs() {
        Bundle args = getArguments();
        if (args == null) {
            return;
        }
        String hexId = args.getString(ARG_FOCUS_HEX_ID, "");
        float lat = args.getFloat(ARG_FOCUS_LAT, 0f);
        float lng = args.getFloat(ARG_FOCUS_LNG, 0f);
        float zoom = args.getFloat(ARG_FOCUS_ZOOM, 0f);
        args.putString(ARG_FOCUS_HEX_ID, "");
        args.putFloat(ARG_FOCUS_LAT, 0f);
        args.putFloat(ARG_FOCUS_LNG, 0f);
        args.putFloat(ARG_FOCUS_ZOOM, 0f);
        if ((hexId == null || hexId.isEmpty()) && Math.abs(lat) < 1e-6 && Math.abs(lng) < 1e-6) {
            return;
        }
        pendingFocusHexId = hexId != null && !hexId.isEmpty() ? hexId : null;
        pendingFocusLat = lat;
        pendingFocusLng = lng;
        pendingFocusZoom = zoom;
        cameraPlacedForRegion = true;
    }

    private boolean hasPendingFocus() {
        return (pendingFocusHexId != null && !pendingFocusHexId.isEmpty())
                || Math.abs(pendingFocusLat) > 1e-6
                || Math.abs(pendingFocusLng) > 1e-6;
    }

    private void tryApplyPendingFocus() {
        if (googleMap == null || !isAdded() || !hasPendingFocus()) {
            return;
        }
        double lat = pendingFocusLat;
        double lng = pendingFocusLng;
        boolean havePoint = Math.abs(lat) > 1e-6 || Math.abs(lng) > 1e-6;
        if (!havePoint && pendingFocusHexId != null && !pendingFocusHexId.isEmpty()) {
            HexParcelOwnershipEntity row = apiaryOwnershipForHex(pendingFocusHexId);
            if (row == null) {
                return;
            }
            HexParcel parcel = IberiaHexOverlayStore.findById(
                    requireContext().getApplicationContext(), pendingFocusHexId);
            double[] site = HexParcelRandomPoint.siteOf(parcel, row);
            lat = site[0];
            lng = site[1];
        }
        if (Math.abs(lat) < 1e-8 && Math.abs(lng) < 1e-8) {
            return;
        }
        float zoom = pendingFocusZoom >= MIN_ZOOM_HEX ? pendingFocusZoom : FOCUS_APIARY_ZOOM;
        PlayableMapRegion want = PlayableMapRegion.containing(lat, lng);
        if (want != null && want != activeRegion) {
            if (want == PlayableMapRegion.SOUTH_AFRICA && !zaUnlocked()) {
                showZaLockedDialog();
                pendingFocusHexId = null;
                pendingFocusLat = 0;
                pendingFocusLng = 0;
                pendingFocusZoom = 0f;
                return;
            }
            pendingFocusLat = lat;
            pendingFocusLng = lng;
            pendingFocusZoom = zoom;
            switchToRegion(want);
            return;
        }
        googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(lat, lng), zoom));
        pendingFocusHexId = null;
        pendingFocusLat = 0;
        pendingFocusLng = 0;
        pendingFocusZoom = 0f;
        cameraPlacedForRegion = true;
    }

    @Nullable
    private HexParcelOwnershipEntity apiaryOwnershipForHex(@Nullable String hexId) {
        if (hexId == null || lastOwnerships == null) {
            return null;
        }
        for (HexParcelOwnershipEntity row : lastOwnerships) {
            if (row != null && hexId.equals(row.hexId) && currentUserId.equals(row.ownerId)
                    && WarehouseRules.isApiarySite(row)) {
                return row;
            }
        }
        return null;
    }

    private void applyFocusFromArgs() {
        ingestFocusArgs();
        tryApplyPendingFocus();
    }

    private void refreshMarketMarkers() {
        ExecutorService io = tripExecutor != null ? tripExecutor : hexOverlayExecutor;
        if (googleMap == null || !isAdded() || io == null || io.isShutdown()) {
            return;
        }
        Context app = requireContext().getApplicationContext();
        PlayableMapRegion region = activeRegion;
        io.execute(() -> {
            List<ProvincialMarket> list = ProvincialMarketCatalog.resolve(app, region);
            mainHandler.post(() -> drawMarketMarkers(list));
        });
    }

    private void drawMarketMarkers(@Nullable List<ProvincialMarket> list) {
        if (googleMap == null || !isAdded()) {
            return;
        }
        for (Marker marker : marketMarkers) {
            marker.remove();
        }
        marketMarkers.clear();
        for (Marker marker : shopMarkers) {
            marker.remove();
        }
        shopMarkers.clear();
        if (list == null || list.isEmpty()) {
            return;
        }
        BitmapDescriptor icon = marketIcon();
        boolean showLocals = showLocalMarkets();
        lastMarketLocalsVisible = showLocals;
        for (ProvincialMarket market : list) {
            if (market == null || (market.local && !showLocals)) {
                continue;
            }
            String title = market.international
                    ? market.name + " · " + getString(R.string.map_market_international_kicker)
                    : (market.local ? market.name + " · local" : market.name);
            Marker marker = googleMap.addMarker(new MarkerOptions()
                    .position(new LatLng(market.lat, market.lng))
                    .title(title)
                    .anchor(0.5f, 0.5f)
                    .zIndex(market.international ? 4.1f : (market.local ? 3.4f : 3.6f))
                    .icon(market.international ? internationalMarketIcon() : icon));
            if (marker != null) {
                marker.setTag("market:" + market.name);
                marketMarkers.add(marker);
            }
            if (!market.local && !market.international) {
                Marker shop = googleMap.addMarker(new MarkerOptions()
                        .position(new LatLng(market.lat + 0.08, market.lng + 0.08))
                        .title(getString(R.string.map_shop_title))
                        .anchor(0.5f, 0.5f)
                        .zIndex(3.7f)
                        .icon(shopIcon()));
                if (shop != null) {
                    shop.setTag("shop");
                    shopMarkers.add(shop);
                }
            }
        }
    }

    /** Capítulo 1. Centra el mapa en el apiario que acaba de instalar. */
    public void focusTutorialApiary(float zoom) {
        if (googleMap == null || lastOwnerships == null) {
            return;
        }
        for (HexParcelOwnershipEntity row : lastOwnerships) {
            if (row == null || !currentUserId.equals(row.ownerId) || !WarehouseRules.isApiarySite(row)) {
                continue;
            }
            HexParcel parcel = IberiaHexOverlayStore.findById(
                    requireContext().getApplicationContext(), row.hexId);
            double lat = row.siteLat;
            double lng = row.siteLng;
            if (parcel != null) {
                double[] site = HexParcelRandomPoint.siteOf(parcel, row);
                lat = site[0];
                lng = site[1];
            }
            googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(lat, lng), zoom));
            return;
        }
    }

    /** Capítulo 1. Acerca la tienda más próxima al almacén. */
    public void focusTutorialShop() {
        if (googleMap == null || shopMarkers.isEmpty()) {
            return;
        }
        double lat = Double.NaN;
        double lng = Double.NaN;
        if (lastOwnerships != null) {
            for (HexParcelOwnershipEntity row : lastOwnerships) {
                if (row != null && currentUserId.equals(row.ownerId) && row.hasWarehouse) {
                    lat = Math.abs(row.warehouseLat) > 1e-6 ? row.warehouseLat : row.siteLat;
                    lng = Math.abs(row.warehouseLng) > 1e-6 ? row.warehouseLng : row.siteLng;
                    break;
                }
            }
        }
        Marker best = shopMarkers.get(0);
        if (!Double.isNaN(lat)) {
            double bestD = Double.MAX_VALUE;
            for (Marker marker : shopMarkers) {
                double dLat = marker.getPosition().latitude - lat;
                double dLng = marker.getPosition().longitude - lng;
                double d = dLat * dLat + dLng * dLng;
                if (d < bestD) {
                    bestD = d;
                    best = marker;
                }
            }
        }
        googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(best.getPosition(), 13f));
    }

    private void refreshPortMarkers() {
        if (googleMap == null || !isAdded()) {
            return;
        }
        for (Marker marker : portMarkers) {
            marker.remove();
        }
        portMarkers.clear();
        BitmapDescriptor icon = portIcon();
        for (Seaport port
                : SeaportCatalog.in(activeRegion)) {
            Marker marker = googleMap.addMarker(new MarkerOptions()
                    .position(new LatLng(port.lat, port.lng))
                    .title(SeaportCatalog.label(requireContext(), port))
                    .anchor(0.5f, 0.5f)
                    .zIndex(6.5f)
                    .icon(icon));
            if (marker != null) {
                marker.setTag("port:" + port.id);
                portMarkers.add(marker);
            }
        }
        final BitmapDescriptor shown = icon;
        mainHandler.post(() -> {
            if (!isAdded() || googleMap == null) {
                return;
            }
            for (Marker marker : portMarkers) {
                marker.setIcon(shown);
                marker.setVisible(true);
            }
        });
    }

    @NonNull
    private BitmapDescriptor shopIcon() {
        if (shopIcon != null) {
            return shopIcon;
        }
        shopIcon = scaledIcon(R.drawable.ic_fleet_shop, 40);
        return shopIcon;
    }

    @NonNull
    private BitmapDescriptor portIcon() {
        if (portIcon != null) {
            return portIcon;
        }
        portIcon = scaledIcon(R.drawable.ic_fleet_port, 44);
        return portIcon;
    }

    @Nullable
    private BitmapDescriptor internationalIcon;

    @NonNull
    private BitmapDescriptor internationalMarketIcon() {
        if (internationalIcon != null) {
            return internationalIcon;
        }
        internationalIcon = scaledIcon(R.drawable.ic_market_international, 48);
        return internationalIcon;
    }

    private final Map<Integer, BitmapDescriptor> fleetTruckIcons = new HashMap<>();
    private final Map<Integer, BitmapDescriptor> fleetShipIcons = new HashMap<>();

    @NonNull
    private BitmapDescriptor fleetTruckIcon(int level) {
        int rank = Math.max(1, Math.min(FleetRules.TRUCK_MAX_LEVEL, level));
        int width = Math.round(vehicleWidthDp());
        int key = rank * 1000 + width;
        BitmapDescriptor cached = fleetTruckIcons.get(key);
        if (cached != null) {
            return cached;
        }
        BitmapDescriptor icon = scaledIconFitWidth(FleetDialogs.truckDrawable(rank), width);
        fleetTruckIcons.put(key, icon);
        return icon;
    }

    private int truckLevel(@NonNull CargoTripEntity trip) {
        FleetStore.Vehicle vehicle =
                FleetStore.vehicle(
                        requireContext(), trip.ownerId, trip.vehicleId);
        return vehicle != null && vehicle.isTruck() ? vehicle.level : 1;
    }

    private int truckLevelForHive(@NonNull String hiveId) {
        for (FleetStore.Vehicle vehicle
                : FleetStore.vehicles(requireContext(), currentUserId)) {
            if (!vehicle.isTruck() || vehicle.hiveIds == null) {
                continue;
            }
            for (String part : vehicle.hiveIds.split(",")) {
                if (hiveId.equals(part)) {
                    return vehicle.level;
                }
            }
        }
        return 1;
    }

    private int shipLevel(@NonNull CargoTripEntity trip) {
        FleetStore.Vehicle vehicle =
                FleetStore.vehicle(
                        requireContext(), trip.ownerId, trip.vehicleId);
        return vehicle != null && !vehicle.isTruck() ? vehicle.level : 1;
    }

    @NonNull
    private BitmapDescriptor movingShipIcon(int level) {
        int rank = Math.max(1, Math.min(FleetRules.SHIP_MAX_LEVEL, level));
        int width = Math.round(vehicleWidthDp() + 8f);
        int key = rank * 1000 + width;
        BitmapDescriptor cached = fleetShipIcons.get(key);
        if (cached != null) {
            return cached;
        }
        BitmapDescriptor icon = scaledIconFitWidth(FleetDialogs.shipDrawable(rank), width);
        fleetShipIcons.put(key, icon);
        return icon;
    }

    @NonNull
    private BitmapDescriptor scaledIcon(int res, float dp) {
        float density = getResources().getDisplayMetrics().density;
        int size = Math.max(1, Math.round(dp * density));
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeResource(getResources(), res, bounds);
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
        opts.inSampleSize = iconSampleSize(bounds.outWidth, bounds.outHeight, size);
        Bitmap src = BitmapFactory.decodeResource(getResources(), res, opts);
        if (src == null) {
            return BitmapDescriptorFactory.defaultMarker();
        }
        Bitmap scaled = Bitmap.createScaledBitmap(src, size, size, true);
        if (scaled != src) {
            src.recycle();
        }
        Bitmap copy = scaled.copy(Bitmap.Config.ARGB_8888, false);
        if (copy != null && copy != scaled) {
            scaled.recycle();
            scaled = copy;
        }
        return BitmapDescriptorFactory.fromBitmap(scaled);
    }

    private static int iconSampleSize(int width, int height, int targetPx) {
        int sample = 1;
        int longest = Math.max(width, height);
        while (longest / (sample * 2) >= targetPx && sample < 32) {
            sample *= 2;
        }
        return sample;
    }

    /** Ancho fijo y alto según la imagen, para que el camión no quede aplastado. */
    @NonNull
    private BitmapDescriptor scaledIconFitWidth(int res, float widthDp) {
        Bitmap src = BitmapFactory.decodeResource(getResources(), res);
        if (src == null || src.getWidth() <= 0 || src.getHeight() <= 0) {
            return BitmapDescriptorFactory.defaultMarker();
        }
        float density = getResources().getDisplayMetrics().density;
        int w = Math.max(1, Math.round(widthDp * density));
        int h = Math.max(1, Math.round(w * (src.getHeight() / (float) src.getWidth())));
        Bitmap scaled = Bitmap.createScaledBitmap(src, w, h, true);
        if (scaled != src) {
            src.recycle();
        }
        return BitmapDescriptorFactory.fromBitmap(scaled);
    }

    @NonNull
    private BitmapDescriptor marketIcon() {
        if (marketIcon != null) {
            return marketIcon;
        }
        Drawable d = ContextCompat.getDrawable(requireContext(), R.drawable.ic_venta);
        float density = getResources().getDisplayMetrics().density;
        int size = Math.round(36f * density);
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        if (d != null) {
            d = d.mutate();
            d.setBounds(0, 0, size, size);
            d.draw(canvas);
        }
        marketIcon = BitmapDescriptorFactory.fromBitmap(bmp);
        return marketIcon;
    }

    private void refreshWarehouseMarkers() {
        if (googleMap == null || !isAdded()) {
            return;
        }
        for (Marker marker : warehouseMarkers) {
            marker.remove();
        }
        warehouseMarkers.clear();
        Context app = requireContext().getApplicationContext();
        BitmapDescriptor icon = warehouseIcon(placeIconDp());
        for (HexParcelOwnershipEntity row : lastOwnerships) {
            if (row == null || !row.hasWarehouse || !currentUserId.equals(row.ownerId)) {
                continue;
            }
            HexParcel parcel = IberiaHexOverlayStore.findById(app, row.hexId);
            if (parcel == null) {
                continue;
            }
            double[] dock = HexParcelRandomPoint.warehouseOf(parcel, row);
            Marker marker = googleMap.addMarker(new MarkerOptions()
                    .position(new LatLng(dock[0], dock[1]))
                    .title(getString(R.string.map_warehouse_title))
                    .anchor(0.5f, 0.5f)
                    .zIndex(3.4f)
                    .icon(icon));
            if (marker != null) {
                marker.setTag("warehouse:" + row.hexId + "\t"
                        + (row.siteId != null && !row.siteId.isEmpty() ? row.siteId : "default"));
                warehouseMarkers.add(marker);
            }
        }
    }

    @NonNull
    private BitmapDescriptor warehouseIcon(int dp) {
        BitmapDescriptor cached = warehouseIcons.get(dp);
        if (cached != null) {
            return cached;
        }
        Drawable d = ContextCompat.getDrawable(requireContext(), R.drawable.ic_obrador);
        int size = Math.max(1, Math.round(dp * density()));
        int touch = Math.max(size, Math.round(48f * density()));
        Bitmap bmp = Bitmap.createBitmap(touch, touch, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        if (d != null) {
            d = d.mutate();
            int inset = (touch - size) / 2;
            d.setBounds(inset, inset, inset + size, inset + size);
            d.draw(canvas);
        }
        punchWhiteBackground(bmp);
        BitmapDescriptor icon = BitmapDescriptorFactory.fromBitmap(bmp);
        warehouseIcons.put(dp, icon);
        return icon;
    }

    private void loadHeadquartersAndFace() {
        if (!isAdded()) {
            return;
        }
        ApicultureApp app = (ApicultureApp) requireActivity().getApplication();
        app.getProfileRepository().fetchDisplayProfile(currentUserId, p -> {
            if (!isAdded()) {
                return;
            }
            profileFaceBitmap = ProfilePhoto.decodeBase64(p.photoBase64);
            hqIcon = null;
            hqIconDp = 0;
            refreshHqMarker();
        });
        HeadquartersStore.hydrateFromCloud(requireContext(), currentUserId, null, () -> {
            if (isAdded()) {
                refreshHqMarker();
            }
        });
    }

    private void refreshHqMarker() {
        if (googleMap == null || !isAdded()) {
            return;
        }
        if (hqMarker != null) {
            hqMarker.remove();
            hqMarker = null;
        }
        HeadquartersStore.Hq hq = HeadquartersStore.get(requireContext(), currentUserId, activeRegion);
        if (hq == null) {
            return;
        }
        Marker marker = googleMap.addMarker(new MarkerOptions()
                .position(new LatLng(hq.lat, hq.lng))
                .title(getString(R.string.map_hq_title))
                .anchor(0.5f, 0.92f)
                .zIndex(4.2f)
                .icon(hqIcon()));
        if (marker != null) {
            marker.setTag("hq");
            hqMarker = marker;
        }
    }

    @NonNull
    private BitmapDescriptor hqIcon() {
        int dp = Math.max(36, placeIconDp());
        if (hqIcon != null && hqIconDp == dp) {
            return hqIcon;
        }
        int graphic = Math.round(dp * density());
        int touch = Math.max(graphic, Math.round(48f * density()));
        Bitmap face = ProfilePhoto.composeHeadquarters(profileFaceBitmap, graphic);
        hqIconDp = dp;
        hqIcon = BitmapDescriptorFactory.fromBitmap(padTouch(face, touch));
        return hqIcon;
    }

    private void refreshOrderMarkers() {
        if (googleMap == null || !isAdded()) {
            return;
        }
        for (Marker marker : orderMarkers) {
            marker.remove();
        }
        orderMarkers.clear();
        if (lastOrders == null || lastOrders.isEmpty()) {
            return;
        }
        if (mapZoom() < ZOOM_PIN_MIN) {
            return;
        }
        VisibleRegion vis = googleMap.getProjection().getVisibleRegion();
        LatLngBounds bounds = vis != null ? vis.latLngBounds : null;
        String region = activeRegion.prefsValue();
        long now = System.currentTimeMillis();
        int level = 0;
        if (currentUserId != null) {
            ApicultureApp app = (ApicultureApp) requireActivity().getApplication();
            level = app.getPlayerProgressRepository().getLevel(currentUserId);
        }
        int band = OfferBand.ofLevel(level).index;
        List<HoneyOrderEntity> visible = new ArrayList<>();
        List<MapPinGroups.Pin> pins = new ArrayList<>();
        for (HoneyOrderEntity e : lastOrders) {
            if (!orderFaceVisible(e, now)) {
                continue;
            }
            if (!e.taken && e.band != band) {
                continue;
            }
            if (region != null && !region.equals(e.region)) {
                continue;
            }
            LatLng pos = new LatLng(e.destLat, e.destLng);
            if (bounds != null && !bounds.contains(pos)) {
                continue;
            }
            pins.add(new MapPinGroups.Pin(e.destLat, e.destLng, pins.size()));
            visible.add(e);
        }
        List<MapPinGroups.Group> groups = mapZoom() < ZOOM_PIN_SPLIT
                ? MapPinGroups.group(googleMap.getProjection(), pins, 56f * density())
                : MapPinGroups.singles(pins);
        int face = poiGraphicPx();
        for (MapPinGroups.Group group : groups) {
            if (group.pins.size() > 1) {
                Marker marker = addClusterMarker(group, 3.7f,
                        R.color.map_cluster_order, R.color.event_cream);
                if (marker != null) {
                    orderMarkers.add(marker);
                }
                continue;
            }
            HoneyOrderEntity e = visible.get(group.pins.get(0).index);
            Marker marker = googleMap.addMarker(new MarkerOptions()
                    .position(new LatLng(group.lat, group.lng))
                    .title(e.npcName)
                    .anchor(0.5f, 0.5f)
                    .zIndex(3.7f)
                    .icon(orderFaceIcon(e.portraitIndex, face)));
            if (marker != null) {
                marker.setTag("order:" + e.id);
                orderMarkers.add(marker);
            }
        }
    }

    @NonNull
    private BitmapDescriptor orderFaceIcon(int portraitIndex, int sizePx) {
        String key = portraitIndex + ":" + sizePx;
        BitmapDescriptor cached = orderFaceIcons.get(key);
        if (cached != null) {
            return cached;
        }
        Drawable d = ContextCompat.getDrawable(requireContext(), NpcPortraitUi.faceDrawable(portraitIndex));
        float density = density();
        int size = Math.max(1, sizePx);
        int touch = Math.max(size, Math.round(48f * density));
        Bitmap bmp = Bitmap.createBitmap(touch, touch, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        int inset = (touch - size) / 2;
        Path clip = new Path();
        float r = size / 2f - 2f * density;
        clip.addCircle(inset + size / 2f, inset + size / 2f, Math.max(1f, r), Path.Direction.CW);
        canvas.save();
        canvas.clipPath(clip);
        if (d != null) {
            d = d.mutate();
            d.setBounds(inset, inset, inset + size, inset + size);
            d.draw(canvas);
        }
        canvas.restore();
        Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(2.5f * density);
        stroke.setColor(ContextCompat.getColor(requireContext(), R.color.event_gold));
        canvas.drawCircle(inset + size / 2f, inset + size / 2f, Math.max(1f, r), stroke);
        BitmapDescriptor icon = BitmapDescriptorFactory.fromBitmap(bmp);
        orderFaceIcons.put(key, icon);
        return icon;
    }

    private void scheduleContractPins() {
        if (!isAdded() || googleMap == null) {
            return;
        }
        ApicultureApp app = (ApicultureApp) requireContext().getApplicationContext();
        int level = app.getPlayerProgressRepository().getLevel(currentUserId);
        app.getPollinationContractRepository().listOffers(currentUserId, level, activeRegion, offers -> {
            if (!isAdded()) {
                return;
            }
            lastContractOffers = offers != null ? offers : Collections.emptyList();
            refreshContractMarkers();
        });
    }

    private void refreshContractMarkers() {
        if (googleMap == null || !isAdded()) {
            return;
        }
        for (Marker marker : contractMarkers) {
            marker.remove();
        }
        contractMarkers.clear();
        if (lastContractOffers == null || lastContractOffers.isEmpty()) {
            return;
        }
        if (mapZoom() < ZOOM_PIN_MIN) {
            return;
        }
        VisibleRegion vis = googleMap.getProjection().getVisibleRegion();
        LatLngBounds bounds = vis != null ? vis.latLngBounds : null;
        List<PollinationContractRepository.Offer> visible = new ArrayList<>();
        List<MapPinGroups.Pin> pins = new ArrayList<>();
        List<Integer> starts = new ArrayList<>();
        for (PollinationContractRepository.Offer offer : lastContractOffers) {
            if (offer == null || offer.farm == null || offer.farm.parcel == null) {
                continue;
            }
            if (offer.occupied && !offer.mine) {
                continue;
            }
            if (PlayableMapRegion.fromHexId(offer.farm.hexId()) != activeRegion) {
                continue;
            }
            int start = offer.farm.terms != null ? offer.farm.terms.startDoy : 0;
            double[] pin = HexParcelRandomPoint.pinOf(offer.farm.parcel, offer.farm.flora + "|" + start);
            LatLng pos = new LatLng(pin[0], pin[1]);
            if (bounds != null && !bounds.contains(pos)) {
                continue;
            }
            pins.add(new MapPinGroups.Pin(pin[0], pin[1], pins.size()));
            visible.add(offer);
            starts.add(start);
        }
        List<MapPinGroups.Group> groups = mapZoom() < ZOOM_PIN_SPLIT
                ? MapPinGroups.group(googleMap.getProjection(), pins, 56f * density())
                : MapPinGroups.singles(pins);
        int graphic = poiGraphicPx();
        for (MapPinGroups.Group group : groups) {
            if (group.pins.size() > 1) {
                Marker marker = addClusterMarker(group, 3.8f,
                        R.color.map_cluster_contract, R.color.event_cream);
                if (marker != null) {
                    contractMarkers.add(marker);
                }
                continue;
            }
            int at = group.pins.get(0).index;
            PollinationContractRepository.Offer offer = visible.get(at);
            Marker marker = googleMap.addMarker(new MarkerOptions()
                    .position(new LatLng(group.lat, group.lng))
                    .title(mapZoom() >= ZOOM_PIN_DETAIL ? offer.farm.flora : null)
                    .anchor(0.5f, 0.5f)
                    .zIndex(3.8f)
                    .icon(contractFloraIcon(offer.farm.flora, graphic)));
            if (marker != null) {
                marker.setTag(contractTag(offer.farm.hexId(), offer.farm.flora, starts.get(at)));
                contractMarkers.add(marker);
            }
        }
    }

    @NonNull
    private static String contractTag(@Nullable String hexId, @Nullable String flora, int startDoy) {
        return "pollination:" + (hexId != null ? hexId : "") + "\u001f"
                + (flora != null ? flora : "") + "\u001f" + startDoy;
    }

    @NonNull
    private BitmapDescriptor contractFloraIcon(@Nullable String flora, int sizePx) {
        int res = HiveSiteSummaryUi.floraBadgeIcon(flora);
        String key = res + ":" + sizePx;
        BitmapDescriptor cached = contractFloraIcons.get(key);
        if (cached != null) {
            return cached;
        }
        Drawable d = ContextCompat.getDrawable(requireContext(), res);
        float density = density();
        int size = Math.max(1, sizePx);
        int touch = Math.max(size, Math.round(48f * density));
        Bitmap bmp = Bitmap.createBitmap(touch, touch, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        int inset = (touch - size) / 2;
        Path clip = new Path();
        float r = size / 2f - 2f * density;
        clip.addCircle(inset + size / 2f, inset + size / 2f, Math.max(1f, r), Path.Direction.CW);
        canvas.save();
        canvas.clipPath(clip);
        if (d != null) {
            d = d.mutate();
            d.setBounds(inset, inset, inset + size, inset + size);
            d.draw(canvas);
        }
        canvas.restore();
        Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(2.4f * density);
        stroke.setColor(ContextCompat.getColor(requireContext(), R.color.event_gold));
        canvas.drawCircle(inset + size / 2f, inset + size / 2f, Math.max(1f, r), stroke);
        BitmapDescriptor icon = BitmapDescriptorFactory.fromBitmap(bmp);
        contractFloraIcons.put(key, icon);
        return icon;
    }

    private boolean orderFaceVisible(@Nullable HoneyOrderEntity e, long nowMs) {
        if (e == null) {
            return false;
        }
        if (e.taken) {
            return currentUserId != null && currentUserId.equals(e.claimedBy);
        }
        return e.expireEpochMs > nowMs;
    }

    private void pruneExpiredOrderMarkers() {
        if (!isAdded()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (googleMap != null && !orderMarkers.isEmpty()) {
            for (int i = orderMarkers.size() - 1; i >= 0; i--) {
                Marker marker = orderMarkers.get(i);
                Object tag = marker.getTag();
                if (!(tag instanceof String) || !((String) tag).startsWith("order:")) {
                    continue;
                }
                String id = ((String) tag).substring("order:".length());
                HoneyOrderEntity found = null;
                for (HoneyOrderEntity e : lastOrders) {
                    if (e != null && id.equals(e.id)) {
                        found = e;
                        break;
                    }
                }
                if (found == null || !orderFaceVisible(found, now)) {
                    marker.remove();
                    orderMarkers.remove(i);
                }
            }
        }
        boolean anyOpen = false;
        for (HoneyOrderEntity e : lastOrders) {
            if (orderFaceVisible(e, now)) {
                anyOpen = true;
                break;
            }
        }
        if (anyOpen) {
            mainHandler.postDelayed(orderExpireTick, 1000L);
        }
    }

    private static void punchWhiteBackground(@NonNull Bitmap bmp) {
        int w = bmp.getWidth();
        int h = bmp.getHeight();
        int n = w * h;
        int[] pixels = new int[n];
        bmp.getPixels(pixels, 0, w, 0, 0, w, h);
        boolean[] seen = new boolean[n];
        int[] q = new int[n];
        int qs = 0;
        int qe = 0;
        for (int i = 0; i < n; i++) {
            int x = i % w;
            int y = i / w;
            boolean edge = x == 0 || y == 0 || x == w - 1 || y == h - 1;
            if (isPunchableBg(pixels[i]) && (edge || Color.alpha(pixels[i]) == 0)) {
                seen[i] = true;
                q[qe++] = i;
            }
        }
        int[] dx = {-1, 1, 0, 0};
        int[] dy = {0, 0, -1, 1};
        while (qs < qe) {
            int i = q[qs++];
            pixels[i] = Color.TRANSPARENT;
            int x = i % w;
            int y = i / w;
            for (int k = 0; k < 4; k++) {
                int nx = x + dx[k];
                int ny = y + dy[k];
                if (nx < 0 || ny < 0 || nx >= w || ny >= h) {
                    continue;
                }
                int j = ny * w + nx;
                if (seen[j] || !isPunchableBg(pixels[j])) {
                    continue;
                }
                seen[j] = true;
                q[qe++] = j;
            }
        }
        bmp.setPixels(pixels, 0, w, 0, 0, w, h);
    }

    private static boolean isPunchableBg(int c) {
        int a = Color.alpha(c);
        if (a == 0) {
            return true;
        }
        int r = Color.red(c);
        int g = Color.green(c);
        int b = Color.blue(c);
        int min = Math.min(r, Math.min(g, b));
        int max = Math.max(r, Math.max(g, b));
        return min >= 185 && (max - min) <= 45;
    }

    private void onCameraIdle() {
        maybeRefreshHiveMarkerScale();
        if (mapZoom() < ZOOM_PIN_SPLIT) {
            refreshApiaryMarkers();
        }
        scheduleHexOverlayRefresh();
        refreshOrderMarkers();
        refreshContractMarkers();
        if (googleMap != null && lastMarketLocalsVisible != showLocalMarkets()) {
            refreshMarketMarkers();
        }
    }

    private boolean showLocalMarkets() {
        if (googleMap == null) {
            return false;
        }
        return googleMap.getCameraPosition().zoom >= activeRegion.defaultZoom() + 0.35f;
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
        refreshWarehouseMarkers();
        refreshHqMarker();
    }

    private float mapZoom() {
        if (googleMap == null) {
            return 6f;
        }
        return googleMap.getCameraPosition().zoom;
    }

    private float density() {
        return getResources().getDisplayMetrics().density;
    }

    private int placeIconDp() {
        float zoom = mapZoom();
        if (zoom < ZOOM_PIN_MIN) {
            return 28;
        }
        if (zoom < ZOOM_PIN_SPLIT) {
            return 36;
        }
        if (zoom < ZOOM_PIN_DETAIL) {
            return 44;
        }
        return 52;
    }

    private int apiaryGraphicPx() {
        float zoom = mapZoom();
        float dp = zoom >= ZOOM_PIN_DETAIL ? 52f : zoom >= ZOOM_PIN_SPLIT ? 40f : 26f;
        return Math.max(1, Math.round(dp * density()));
    }

    private int poiGraphicPx() {
        float zoom = mapZoom();
        float dp = zoom >= ZOOM_PIN_DETAIL ? 44f : zoom >= ZOOM_PIN_SPLIT ? 36f : 28f;
        return Math.max(1, Math.round(dp * density()));
    }

    private boolean apiaryShowsName() {
        return mapZoom() >= ZOOM_PIN_DETAIL;
    }

    private float vehicleWidthDp() {
        float zoom = mapZoom();
        if (zoom < ZOOM_PIN_MIN) {
            return 42f;
        }
        if (zoom < ZOOM_PIN_SPLIT) {
            return 54f;
        }
        if (zoom < ZOOM_PIN_DETAIL) {
            return 66f;
        }
        return 78f;
    }

    @Nullable
    private Marker addClusterMarker(@NonNull MapPinGroups.Group group, float zIndex,
                                    @ColorRes int fillColor, @ColorRes int textColor) {
        List<LatLng> points = new ArrayList<>(group.pins.size());
        for (MapPinGroups.Pin pin : group.pins) {
            points.add(new LatLng(pin.lat, pin.lng));
        }
        Marker marker = googleMap.addMarker(new MarkerOptions()
                .position(new LatLng(group.lat, group.lng))
                .anchor(0.5f, 0.5f)
                .zIndex(zIndex)
                .icon(clusterIcon(group.pins.size(), fillColor, textColor)));
        if (marker != null) {
            marker.setTag(new MapPinGroups.Focus(points));
        }
        return marker;
    }

    @NonNull
    private BitmapDescriptor clusterIcon(int count, @ColorRes int fillColor,
            @ColorRes int textColor) {
        int shown = Math.min(99, Math.max(2, count));
        String key = fillColor + ":" + shown;
        BitmapDescriptor cached = clusterIcons.get(key);
        if (cached != null) {
            return cached;
        }
        float density = density();
        int size = Math.max(Math.round(44f * density), Math.round(48f * density));
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        fill.setColor(ContextCompat.getColor(requireContext(), fillColor));
        float r = size / 2f - 2f * density;
        canvas.drawCircle(size / 2f, size / 2f, r, fill);
        Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(2f * density);
        stroke.setColor(ContextCompat.getColor(requireContext(), textColor));
        canvas.drawCircle(size / 2f, size / 2f, r, stroke);
        Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        text.setColor(ContextCompat.getColor(requireContext(), textColor));
        text.setTextAlign(Paint.Align.CENTER);
        text.setTextSize(16f * density);
        text.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        String label = Integer.toString(shown);
        Rect bounds = new Rect();
        text.getTextBounds(label, 0, label.length(), bounds);
        canvas.drawText(label, size / 2f, size / 2f - bounds.exactCenterY(), text);
        BitmapDescriptor icon = BitmapDescriptorFactory.fromBitmap(bmp);
        clusterIcons.put(key, icon);
        return icon;
    }

    private void zoomToPins(@NonNull List<LatLng> points) {
        if (googleMap == null || points.isEmpty()) {
            return;
        }
        if (points.size() == 1) {
            googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(
                    points.get(0), Math.min(ZOOM_PIN_DETAIL, mapZoom() + 2f)));
            return;
        }
        LatLngBounds.Builder builder = new LatLngBounds.Builder();
        for (LatLng point : points) {
            builder.include(point);
        }
        try {
            googleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(
                    builder.build(), Math.round(72f * density())));
        } catch (RuntimeException ignored) {
            googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(
                    points.get(0), Math.min(ZOOM_PIN_DETAIL, Math.max(mapZoom() + 2f, ZOOM_PIN_SPLIT))));
        }
    }

    @NonNull
    private static Bitmap padTouch(@NonNull Bitmap src, int touchPx) {
        int side = Math.max(touchPx, Math.max(src.getWidth(), src.getHeight()));
        if (src.getWidth() >= side && src.getHeight() >= side) {
            return src;
        }
        Bitmap out = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out);
        canvas.drawBitmap(src, (side - src.getWidth()) / 2f, (side - src.getHeight()) / 2f, null);
        return out;
    }

    private int hiveIconSizePx() {
        return apiaryGraphicPx();
    }

    @Nullable
    private Bitmap hiveIconSrc() {
        if (hiveIconSrc == null) {
            hiveIconSrc = BitmapFactory.decodeResource(getResources(), R.drawable.ic_compracolmena);
        }
        return hiveIconSrc;
    }

    @NonNull
    private BitmapDescriptor createLabeledHiveIcon(boolean own, @Nullable String rawName, boolean showLabel) {
        String label = showLabel ? markerLabel(rawName) : "";
        int iconSize = hiveIconSizePx();
        int touch = Math.max(iconSize, Math.round(48f * density()));
        String key = (own ? "1|" : "0|") + iconSize + "|" + touch + "|" + label;
        BitmapDescriptor cached = labeledHiveIcons.get(key);
        if (cached != null) {
            lastHiveMarkerAnchorY = showLabel ? hiveAnchorY(iconSize, label, touch) : 0.5f;
            return cached;
        }
        Bitmap src = hiveIconSrc();
        if (src == null) {
            return BitmapDescriptorFactory.defaultMarker(
                    own ? BitmapDescriptorFactory.HUE_ORANGE : BitmapDescriptorFactory.HUE_AZURE);
        }
        Bitmap scaled = Bitmap.createScaledBitmap(src, iconSize, iconSize, true);
        if (!own) {
            Bitmap gray = Bitmap.createBitmap(iconSize, iconSize, Bitmap.Config.ARGB_8888);
            Canvas grayCanvas = new Canvas(gray);
            Paint grayPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
            ColorMatrix matrix = new ColorMatrix();
            matrix.setSaturation(0f);
            grayPaint.setColorFilter(new ColorMatrixColorFilter(matrix));
            grayCanvas.drawBitmap(scaled, 0, 0, grayPaint);
            scaled = gray;
        }
        BitmapDescriptor icon = finishApiaryIcon(scaled, label, showLabel);
        if (labeledHiveIcons.size() > 96) {
            labeledHiveIcons.clear();
        }
        labeledHiveIcons.put(key, icon);
        return icon;
    }

    @NonNull
    private BitmapDescriptor finishApiaryIcon(@NonNull Bitmap graphic, @NonNull String label,
            boolean showLabel) {
        int iconSize = graphic.getWidth();
        int touch = Math.max(iconSize, Math.round(48f * density()));
        if (!showLabel || label.isEmpty()) {
            lastHiveMarkerAnchorY = 0.5f;
            return BitmapDescriptorFactory.fromBitmap(padTouch(graphic, touch));
        }
        float density = density();
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
        int padX = Math.round(4f * density);
        int padY = Math.round(3f * density);
        int width = Math.max(touch, Math.max(iconSize, bounds.width() + padX * 2));
        int height = graphic.getHeight() + padY + bounds.height() + padY;
        lastHiveMarkerAnchorY = (graphic.getHeight() / 2f) / height;
        Bitmap out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out);
        canvas.drawBitmap(graphic, (width - iconSize) / 2f, 0, null);
        float ty = graphic.getHeight() + padY - bounds.top;
        canvas.drawText(label, width / 2f, ty, stroke);
        canvas.drawText(label, width / 2f, ty, fill);
        return BitmapDescriptorFactory.fromBitmap(out);
    }

    private float hiveAnchorY(int iconSize, @NonNull String label, int touch) {
        float density = getResources().getDisplayMetrics().density;
        float textSize = Math.max(9f * density, iconSize * 0.28f);
        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        fill.setTextSize(textSize);
        fill.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        Rect bounds = new Rect();
        fill.getTextBounds(label, 0, label.length(), bounds);
        int padY = Math.round(3f * density);
        int height = iconSize + padY + bounds.height() + padY;
        return (iconSize / 2f) / Math.max(1, height);
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
        cancelPendingHexAdds();
        for (Polygon p : hexOverlayPolygons) {
            try {
                p.remove();
            } catch (RuntimeException ignored) {
            }
        }
        hexOverlayPolygons.clear();
    }

    private void cancelPendingHexAdds() {
        mainHandler.removeCallbacks(hexAddNextBatch);
        pendingHexSpecs = null;
        pendingHexIndex = 0;
        pendingHexGeneration = -1;
        pendingHexMap = null;
    }

    private void startHexPolygonBatches(
            @NonNull List<Pair<PolygonOptions, String>> toDraw,
            int generation,
            @NonNull GoogleMap map) {
        removeHexOverlayPolygons();
        pendingHexSpecs = toDraw;
        pendingHexIndex = 0;
        pendingHexGeneration = generation;
        pendingHexMap = map;
        mainHandler.post(hexAddNextBatch);
    }

    private void addNextHexPolygonBatch() {
        List<Pair<PolygonOptions, String>> specs = pendingHexSpecs;
        GoogleMap map = pendingHexMap;
        if (specs == null || map == null || googleMap == null || map != googleMap
                || !isAdded() || pendingHexGeneration != hexOverlayGeneration.get()) {
            return;
        }
        int end = Math.min(pendingHexIndex + HEX_ADD_BATCH, specs.size());
        for (int i = pendingHexIndex; i < end; i++) {
            Pair<PolygonOptions, String> spec = specs.get(i);
            Polygon poly = map.addPolygon(spec.first);
            poly.setTag(spec.second);
            hexOverlayPolygons.add(poly);
        }
        pendingHexIndex = end;
        if (end < specs.size()) {
            mainHandler.post(hexAddNextBatch);
        } else {
            pendingHexSpecs = null;
            pendingHexMap = null;
        }
    }

    private void detachOverlayWarmup() {
        if (overlayWarmupListener != null) {
            GameStartupWarmup.removeListener(overlayWarmupListener);
            overlayWarmupListener = null;
        }
    }

    private void waitForRegionOverlay(@NonNull PlayableMapRegion region, @Nullable Runnable then) {
        if (!isAdded()) {
            return;
        }
        if (IberiaHexOverlayStore.isLoaded(region)) {
            setOverlayBusy(false);
            if (then != null) {
                then.run();
            }
            return;
        }
        setOverlayBusy(true);
        if (!GameStartupWarmup.isReady()) {
            detachOverlayWarmup();
            overlayWarmupListener = new GameStartupWarmup.Listener() {
                @Override
                public void onProgress(int done, int total, @NonNull String status) {
                }

                @Override
                public void onReady() {
                    detachOverlayWarmup();
                    if (isAdded()) {
                        waitForRegionOverlay(region, then);
                    }
                }
            };
            GameStartupWarmup.addListener(overlayWarmupListener);
            return;
        }
        IberiaHexOverlayStore.ensureLoadedAsync(
                requireContext().getApplicationContext(),
                region,
                () -> {
                    if (!isAdded() || activeRegion != region) {
                        setOverlayBusy(false);
                        return;
                    }
                    setOverlayBusy(false);
                    if (then != null) {
                        then.run();
                    }
                });
    }

    private void refreshHexParcelOverlayNow() {
        if (googleMap == null || !isAdded() || hexOverlayExecutor == null) {
            return;
        }
        final PlayableMapRegion regionForHex = activeRegion;
        if (!IberiaHexOverlayStore.isLoaded(regionForHex)) {
            waitForRegionOverlay(regionForHex, () -> {
                if (isAdded() && activeRegion == regionForHex) {
                    refreshHexParcelOverlayNow();
                    scheduleContractPins();
                }
            });
            return;
        }
        hexOverlayGeneration.incrementAndGet();
        cancelPendingHexAdds();
        setOverlayBusy(false);
        final String floraFilter = MapOverlayPrefs.getFloraFilter(requireContext());
        final boolean showMesh = floraFilter == null;
        final float zoom = googleMap.getCameraPosition().zoom;
        if (showMesh && zoom < MIN_ZOOM_HEX) {
            removeHexOverlayPolygons();
            if (binding != null && binding.cardClimateLegend != null) {
                binding.cardClimateLegend.setVisibility(View.GONE);
            }
            return;
        }
        if (binding != null && binding.cardClimateLegend != null) {
            binding.cardClimateLegend.setVisibility(View.VISIBLE);
            applyLegendForActiveRegion();
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
        if (floraFilter != null) {
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
        final boolean hexPolygonsClickable = false;
        final List<HiveEntity> hivesSnapshot = lastHives;

        hexOverlayExecutor.execute(() -> {
            List<HexParcel> all = IberiaHexOverlayStore.getParcels(appCtx, regionForHex);
            Map<String, String> ownership =
                    ((ApicultureApp) appCtx).getHexParcelRepository().getOwnershipMapSync();
            Set<String> ownHexIds = collectOwnHexIds(ownership, uidForHex, hivesSnapshot, appCtx);
            ownHexIds.addAll(((ApicultureApp) appCtx).getHexParcelRepository().listOwnedHexIdsSync(uidForHex));
            List<PollinationContractEntity> openRows = ((ApicultureApp) appCtx)
                    .getPollinationContractRepository().getOpenListForOwnerSync(uidForHex);
            Set<String> openHexes = new HashSet<>();
            for (int i = 0; i < openRows.size(); i++) {
                PollinationContractEntity open = openRows.get(i);
                if (open != null && open.hexId != null
                        && (PollinationContractRules.STATUS_ACTIVE.equals(open.status)
                        || PollinationContractRules.STATUS_RETURNING.equals(open.status))) {
                    openHexes.add(open.hexId);
                }
            }
            mainHandler.post(() -> {
                lastOpenContractHexIds.clear();
                lastOpenContractHexIds.addAll(openHexes);
            });
            ownHexIds.removeIf(id -> PlayableMapRegion.fromHexId(id) != regionForHex);
            List<HexParcel> parcels = new ArrayList<>();
            if (floraFilter != null) {
                parcels.addAll(visibleMatchingFlora(
                        all, viewInRegion, maxHex, focus.latitude, focus.longitude, floraFilter));
            } else {
                List<HexParcel> inView = IberiaHexOverlayStore.visibleInViewport(
                        all, viewInRegion, maxHex, focus.latitude, focus.longitude);
                parcels.addAll(inView);
            }
            List<Pair<PolygonOptions, String>> specs = new ArrayList<>(parcels.size());
            for (int i = 0; i < parcels.size(); i++) {
                HexParcel parcel = parcels.get(i);
                boolean isOwn = ownHexIds.contains(parcel.id);
                int fill;
                if (isOwn) {
                    fill = HEX_OWN_FILL;
                } else {
                    boolean canBuy = ClimateUnlock.canBuyParcel(parcel, playerLevelForHex);
                    fill = canBuy ? HEX_FREE_FILL : HEX_FREE_LOCKED_FILL;
                }
                int stroke = climateStrokeColor(parcel);
                boolean polygonClickable = hexPolygonsClickable;
                specs.add(new Pair<>(
                        hexPolygonOptions(parcel, fill, stroke, HEX_STROKE_WIDTH, polygonClickable),
                        parcel.id));
            }
            final List<Pair<PolygonOptions, String>> toDraw = specs;
            mainHandler.post(() -> {
                if (getView() == null || !isAdded() || googleMap == null || googleMap != mapWhenScheduled
                        || generation != hexOverlayGeneration.get()) {
                    return;
                }
                startHexPolygonBatches(toDraw, generation, mapWhenScheduled);
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
            return 200;
        }
        float t = zoom - ZOOM_FULL_PENINSULA_HEX;
        int n = (int) (250 + t * 110);
        return Math.min(MAX_HEX_VISIBLE, Math.max(180, n));
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
        return hexPolygonOptions(parcel, fillColor, strokeColor, HEX_STROKE_WIDTH, clickable);
    }

    private static PolygonOptions hexPolygonOptions(
            HexParcel parcel, int fillColor, int strokeColor, float strokeWidth, boolean clickable) {
        return new PolygonOptions()
                .add(latLngRing(parcel))
                .strokeWidth(strokeWidth)
                .strokeColor(strokeColor)
                .fillColor(fillColor)
                .clickable(clickable);
    }

    /** Borde del hex = zona climática (el relleno sigue siendo libre / propio / ajeno). */
    private static int climateStrokeColor(HexParcel parcel) {
        if (HexFlora.isMadagascarParcel(parcel)) {
            return climateStrokeColorMdg(MadagascarClimateZone.forParcel(parcel));
        }
        if (HexFlora.isZaParcel(parcel)) {
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
                return Color.argb(255, 8, 120, 145);
            case MOUNTAIN:
                return Color.argb(255, 148, 172, 204);
            case MEDITERRANEAN:
                return Color.argb(255, 210, 140, 18);
            case SOUTH:
                return Color.argb(255, 200, 72, 36);
            case CONTINENTAL:
            default:
                return Color.argb(255, 98, 128, 48);
        }
    }

    private static int climateStrokeColorZa(SouthernAfricanClimateZone zone) {
        if (zone == null) {
            zone = SouthernAfricanClimateZone.HIGHVELD;
        }
        switch (zone) {
            case FYNBOS:
                return Color.argb(255, 123, 63, 160);
            case KAROO:
                return Color.argb(255, 196, 154, 60);
            case HIGHVELD:
                return Color.argb(255, 212, 160, 23);
            case SUBTROPICAL:
                return Color.argb(255, 27, 138, 122);
            case BUSHVELD:
            default:
                return Color.argb(255, 90, 122, 56);
        }
    }

    private static int climateStrokeColorMdg(MadagascarClimateZone zone) {
        if (zone == null) {
            zone = MadagascarClimateZone.TROPICAL;
        }
        switch (zone) {
            case EQUATORIAL:
                return Color.argb(255, 16, 122, 72);
            case HIGHLANDS:
                return Color.argb(255, 92, 118, 176);
            case DESERT:
                return Color.argb(255, 196, 132, 48);
            case TROPICAL:
            default:
                return Color.argb(255, 212, 96, 36);
        }
    }

    private String climateLineForParcel(@Nullable HexParcel parcel) {
        if (HexFlora.isMadagascarParcel(parcel)) {
            return MadagascarClimateZone.forParcel(parcel).dialogLineEs();
        }
        if (HexFlora.isZaParcel(parcel)) {
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

    private static boolean floraMatches(HexParcel parcel, @Nullable String floraFilter) {
        if (floraFilter == null || floraFilter.isEmpty()) {
            return true;
        }
        return HexFlora.nativeMixContains(parcel, floraFilter)
                || npcFarmMatchesFlora(parcel, floraFilter);
    }

    private static boolean npcFarmMatchesFlora(HexParcel parcel, String floraFilter) {
        String want = HexFlora.canonicalKey(floraFilter);
        for (NpcContractFarm f : NpcContractCatalog.farmsFor(parcel)) {
            if (f != null && want.equals(HexFlora.canonicalKey(f.flora))) {
                return true;
            }
        }
        return false;
    }

    private static List<HexParcel> visibleMatchingFlora(
            List<HexParcel> all,
            BoundingBox view,
            int maxHex,
            double focusLat,
            double focusLon,
            String floraFilter) {
        if (all == null || view == null || maxHex <= 0) {
            return Collections.emptyList();
        }
        List<HexParcel> cand = new ArrayList<>();
        for (int i = 0; i < all.size(); i++) {
            HexParcel parcel = all.get(i);
            if (parcel != null
                    && view.containsLatLon(parcel.centroidLat, parcel.centroidLon)
                    && floraMatches(parcel, floraFilter)) {
                cand.add(parcel);
            }
        }
        double cosLat = Math.cos(Math.toRadians(focusLat));
        cand.sort((a, b) -> {
            double daLat = a.centroidLat - focusLat;
            double daLon = (a.centroidLon - focusLon) * cosLat;
            double dbLat = b.centroidLat - focusLat;
            double dbLon = (b.centroidLon - focusLon) * cosLat;
            return Double.compare(daLat * daLat + daLon * daLon, dbLat * dbLat + dbLon * dbLon);
        });
        if (cand.size() <= maxHex) {
            return cand;
        }
        return new ArrayList<>(cand.subList(0, maxHex));
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
                if (hive == null || hive.inWarehouse || !uid.equals(hive.ownerId)) {
                    continue;
                }
                HexParcel underHive = IberiaHexOverlayStore.findContaining(appCtx, hive.lat, hive.lng);
                if (underHive != null && (hive.contractId == null || hive.contractId.isEmpty())) {
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
        if (hexId == null || !isAdded()) {
            return;
        }
        if (TutorialBus.enterApiaryByMarker()) {
            // Capítulo 1, viñeta 9. Hay que pulsar la imagen del apiario.
            GameNotice.show(requireContext(), R.string.tutorial_c1_v09);
            return;
        }
        HexParcel tapParcel = IberiaHexOverlayStore.findById(
                requireContext().getApplicationContext(), hexId);
        if (tapParcel != null) {
            HexParcel underTap = Double.isNaN(lastTapLat) ? null
                    : IberiaHexOverlayStore.findContaining(
                    requireContext().getApplicationContext(), lastTapLat, lastTapLng);
            if (underTap == null || !hexId.equals(underTap.id)) {
                lastTapLat = tapParcel.centroidLat;
                lastTapLng = tapParcel.centroidLon;
            }
        }
        showOwnedHexInstallChoice(hexId);
    }

    private String climateLabelForParcel(@Nullable HexParcel parcel) {
        return ClimateUnlock.climateLabelForParcel(parcel);
    }

    private void showOwnedHexInstallChoice(@NonNull String hexId) {
        WarehouseDialogs.showInstallChoice(this,
                () -> installApiaryAtTap(hexId),
                () -> installWarehouseAtTap(hexId),
                this::placeHeadquartersAtTap);
    }

    private void placeHeadquartersAtTap() {
        if (!isAdded() || Double.isNaN(lastTapLat) || Double.isNaN(lastTapLng)) {
            return;
        }
        PlayableMapRegion at = PlayableMapRegion.containing(lastTapLat, lastTapLng);
        if (at == null || at != activeRegion) {
            GameNotice.show(requireContext(),
                    getString(R.string.map_hq_wrong_region, regionLabel(activeRegion)));
            return;
        }
        HexParcel under = IberiaHexOverlayStore.findContaining(
                requireContext().getApplicationContext(), lastTapLat, lastTapLng);
        if (under == null) {
            GameNotice.show(requireContext(), R.string.map_hq_not_free);
            return;
        }
        final double lat = lastTapLat;
        final double lng = lastTapLng;
        final boolean moving = HeadquartersStore.has(requireContext(), currentUserId, activeRegion);
        Runnable save = () -> new Thread(() -> {
            String err = HeadquartersStore.place(requireContext(), currentUserId, lat, lng);
            if (!isAdded()) {
                return;
            }
            requireActivity().runOnUiThread(() -> {
                if (!isAdded()) {
                    return;
                }
                if (err != null) {
                    GameNotice.show(requireContext(), err);
                    return;
                }
                GameNotice.showSuccess(requireContext(), getString(
                        moving ? R.string.map_hq_moved : R.string.map_hq_placed, regionLabel(activeRegion)));
                refreshHqMarker();
            });
        }, "hq-place").start();
        if (!moving) {
            save.run();
            return;
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.map_hq_move_title)
                .setMessage(getString(R.string.map_hq_move_message, regionLabel(activeRegion)))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.map_install_choice_hq, (d, w) -> save.run())
                .show();
    }

    @NonNull
    private String regionLabel(@Nullable PlayableMapRegion region) {
        if (region == PlayableMapRegion.SOUTH_AFRICA) {
            return getString(R.string.map_region_south_africa);
        }
        if (region == PlayableMapRegion.MADAGASCAR) {
            return getString(R.string.map_region_madagascar);
        }
        return getString(R.string.map_region_iberia);
    }

    private void installWarehouseAtTap(@NonNull String hexId) {
        if (hexHasWarehouse(hexId)) {
            GameNotice.show(requireContext(), R.string.map_tap_warehouse_exists);
            return;
        }
        WarehouseDialogs.showBuy(this, hiveViewModel, hexId, lastTapLat, lastTapLng);
    }

    private void installApiaryAtTap(@NonNull String hexId) {
        if (hexOverlayExecutor == null || hexOverlayExecutor.isShutdown()) {
            return;
        }
        ApicultureApp app = (ApicultureApp) requireContext().getApplicationContext();
        hexOverlayExecutor.execute(() -> {
            HexParcel tapParcel = IberiaHexOverlayStore.findById(app, hexId);
            int pl = app.getPlayerProgressRepository().getLevel(currentUserId);
            if (!ClimateUnlock.canBuyParcel(tapParcel, pl)) {
                final List<String> wildMix = HexFlora.nativeMixForParcel(tapParcel);
                final String climateLabel = climateLabelForParcel(tapParcel);
                lastFloraSaturations = app.getHiveRepository().floraSaturationLinesBlocking(hexId);
                final String iconFlora = HexFlora.nativeFloraForParcel(tapParcel);
                int needLevel = ClimateUnlock.minLevelForParcel(tapParcel);
                String climateName = ClimateUnlock.climateLabelForParcel(tapParcel);
                mainHandler.post(() -> {
                    if (!isAdded()) {
                        return;
                    }
                    showMapParcelDialog(
                            getString(R.string.hex_locked_climate_title),
                            getString(R.string.hex_locked_climate_kicker),
                            joinFloraLabels(wildMix),
                            iconFlora,
                            getString(R.string.hex_purchase_hives_value, HexParcelGameRules.MAX_HIVES_PER_SITE),
                            climateLabel,
                            null,
                            getString(R.string.hex_locked_climate_note, climateName, needLevel),
                            null,
                            null,
                            null,
                            null,
                            null);
                });
                return;
            }
            lastFloraSaturations = app.getHiveRepository().floraSaturationLinesBlocking(hexId);
            final List<HexFloraSaturation.Line> sats =
                    lastFloraSaturations;
            mainHandler.post(() -> {
                if (isAdded()) {
                    confirmPurchaseHex(hexId, false, sats);
                }
            });
        });
    }

    private boolean hasDistinctApiarySite(@Nullable HexParcelOwnershipEntity row) {
        if (row == null) {
            return false;
        }
        if (Math.abs(row.siteLat) < 1e-8 && Math.abs(row.siteLng) < 1e-8) {
            return false;
        }
        if (!row.hasWarehouse) {
            return true;
        }
        double dLat = row.siteLat - row.warehouseLat;
        double dLng = row.siteLng - row.warehouseLng;
        return (dLat * dLat + dLng * dLng) > 1.6e-9;
    }

    @NonNull
    private static String apiaryTag(boolean own, @NonNull HexParcelOwnershipEntity row) {
        String site = row.siteId != null && !row.siteId.isEmpty() ? row.siteId : "default";
        return (own ? "apiary:" : "other-apiary:") + row.hexId + "\t" + row.ownerId + "\t" + site;
    }

    @Nullable
    private static String[] parseApiaryTag(@Nullable Object tag, @NonNull String prefix) {
        if (!(tag instanceof String) || !((String) tag).startsWith(prefix)) {
            return null;
        }
        String rest = ((String) tag).substring(prefix.length());
        String[] parts = rest.split("\t", 3);
        if (parts.length == 1) {
            return new String[]{parts[0], "", "default"};
        }
        if (parts.length == 2) {
            return new String[]{parts[0], parts[1], "default"};
        }
        return parts;
    }

    private void showHeadquartersDialog() {
        if (!isAdded()) {
            return;
        }
        View root = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_map_hq, null);
        TextView title = root.findViewById(R.id.tv_hq_title);
        TextView body = root.findViewById(R.id.tv_hq_body);
        ImageView banner = root.findViewById(R.id.iv_hq_banner);
        MaterialButton close = root.findViewById(R.id.btn_hq_close);
        title.setText(getString(R.string.map_hq_region, regionLabel(activeRegion)));
        body.setText(R.string.map_hq_info);
        int size = Math.round(120f * getResources().getDisplayMetrics().density);
        banner.setImageBitmap(ProfilePhoto.composeHeadquarters(profileFaceBitmap, size));
        Dialog dialog = new Dialog(requireContext());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(root);
        dialog.setCancelable(true);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        close.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void showOwnedApiaryDialog(@NonNull String hexId, @Nullable String siteId) {
        if (hexOverlayExecutor == null || hexOverlayExecutor.isShutdown()) {
            return;
        }
        final String site = siteId != null && !siteId.isEmpty() ? siteId : "default";
        ApicultureApp app = (ApicultureApp) requireContext().getApplicationContext();
        hexOverlayExecutor.execute(() -> {
            HexParcel tapParcel = IberiaHexOverlayStore.findById(app, hexId);
            final List<String> wildMix = HexFlora.nativeMixForParcel(tapParcel);
            long nowMs = System.currentTimeMillis();
            final String plantations = app.getHexFloraRepository()
                    .describePlantationsOnParcelForDialogBlocking(hexId, nowMs);
            final String climateLabel = climateLabelForParcel(tapParcel);
            int atSite = app.getHexParcelRepository()
                    .countHivesAtSiteBlocking(currentUserId, hexId, site);
            int remainingSlots = Math.max(0, HexParcelGameRules.MAX_HIVES_PER_SITE - atSite);
            lastFloraSaturations = app.getHiveRepository().floraSaturationLinesBlocking(hexId);
            final String iconFlora = HexFlora.nativeFloraForParcel(tapParcel);
            String title = getString(R.string.hex_own_parcel_title);
            HexParcelOwnershipEntity named = ownershipForSite(hexId, currentUserId, site);
            if (named != null && named.parcelName != null && !named.parcelName.trim().isEmpty()) {
                title = named.parcelName.trim();
            }
            final String titleFinal = title;
            final String slotsLine = getString(R.string.map_apiary_hives_and_hex_slots,
                    getResources().getQuantityString(R.plurals.map_apiary_hive_count, atSite, atSite),
                    remainingSlots);
            mainHandler.post(() -> {
                if (!isAdded()) {
                    return;
                }
                showMapParcelDialog(
                        titleFinal,
                        null,
                        joinFloraLabels(wildMix),
                        iconFlora,
                        slotsLine,
                        climateLabel,
                        plantations,
                        null,
                        getString(R.string.hex_open_apiary_3d),
                        () -> openApiaryYard(hexId, site, false, true),
                        // Sembrar se hace con Pep, el payés, dentro del apiario.
                        null,
                        null,
                        null,
                        null,
                        getString(R.string.map_sell_apiary),
                        () -> SiteSellDialogs.sellApiary(this, hiveViewModel, hexId, site));
            });
        });
    }

    private void showOtherApiaryDialog(@NonNull String hexId, @Nullable String ownerId,
            @Nullable String siteId) {
        if (ownerId == null || ownerId.isEmpty() || !isAdded()) {
            return;
        }
        HexParcelOwnershipEntity row = ownershipForSite(hexId, ownerId, siteId);
        String name = row != null && row.parcelName != null && !row.parcelName.trim().isEmpty()
                ? row.parcelName.trim()
                : hexId;
        Bundle args = new Bundle();
        args.putString("hexId", hexId);
        args.putString("siteId", siteId != null && !siteId.isEmpty() ? siteId : null);
        args.putString("parcelName", name);
        args.putString("visitOwnerId", ownerId);
        NavHostFragment.findNavController(this).navigate(R.id.apiaryYardFragment, args);
    }

    @Nullable
    private HexParcelOwnershipEntity ownershipForSite(@Nullable String hexId, @Nullable String ownerId,
            @Nullable String siteId) {
        if (hexId == null || lastOwnerships == null) {
            return null;
        }
        String site = siteId != null && !siteId.isEmpty() ? siteId : "default";
        for (HexParcelOwnershipEntity row : lastOwnerships) {
            if (row == null || !hexId.equals(row.hexId)) {
                continue;
            }
            if (ownerId != null && !ownerId.isEmpty() && !ownerId.equals(row.ownerId)) {
                continue;
            }
            String rowSite = row.siteId != null && !row.siteId.isEmpty() ? row.siteId : "default";
            if (site.equals(rowSite)) {
                return row;
            }
        }
        return null;
    }

    private void showMapParcelDialog(
            @NonNull String title,
            @Nullable String kicker,
            @Nullable String floraLine,
            @Nullable String iconFloraKey,
            @NonNull String hivesText,
            @NonNull String climateLabel,
            @Nullable String floraDetail,
            @Nullable String note,
            @Nullable String actionLabel,
            @Nullable Runnable onAction,
            @Nullable String secondaryLabel,
            @Nullable Runnable onSecondary,
            @Nullable NpcContractFarm npc) {
        showMapParcelDialog(title, kicker, floraLine, iconFloraKey, hivesText, climateLabel,
                floraDetail, note, actionLabel, onAction, secondaryLabel, onSecondary, npc, null);
    }

    private void showMapParcelDialog(
            @NonNull String title,
            @Nullable String kicker,
            @Nullable String floraLine,
            @Nullable String iconFloraKey,
            @NonNull String hivesText,
            @NonNull String climateLabel,
            @Nullable String floraDetail,
            @Nullable String note,
            @Nullable String actionLabel,
            @Nullable Runnable onAction,
            @Nullable String secondaryLabel,
            @Nullable Runnable onSecondary,
            @Nullable NpcContractFarm npc,
            @Nullable String pendingMove) {
        showMapParcelDialog(title, kicker, floraLine, iconFloraKey, hivesText, climateLabel,
                floraDetail, note, actionLabel, onAction, secondaryLabel, onSecondary, npc,
                pendingMove, null, null);
    }

    private void showMapParcelDialog(
            @NonNull String title,
            @Nullable String kicker,
            @Nullable String floraLine,
            @Nullable String iconFloraKey,
            @NonNull String hivesText,
            @NonNull String climateLabel,
            @Nullable String floraDetail,
            @Nullable String note,
            @Nullable String actionLabel,
            @Nullable Runnable onAction,
            @Nullable String secondaryLabel,
            @Nullable Runnable onSecondary,
            @Nullable NpcContractFarm npc,
            @Nullable String pendingMove,
            @Nullable String sellLabel,
            @Nullable Runnable onSell) {
        if (!isAdded()) {
            return;
        }
        DialogMapParcelBinding form = DialogMapParcelBinding.inflate(getLayoutInflater());
        form.tvMapParcelTitle.setText(title);
        if (kicker != null && !kicker.trim().isEmpty()) {
            form.tvMapParcelKicker.setVisibility(View.VISIBLE);
            form.tvMapParcelKicker.setText(kicker);
        } else {
            form.tvMapParcelKicker.setVisibility(View.GONE);
        }
        if (npc != null) {
            form.llMapParcelNpc.setVisibility(View.VISIBLE);
            form.flMapParcelBanner.setVisibility(View.GONE);
            form.ivMapParcelNpc.setImageResource(
                    NpcPortraitUi.faceDrawable(npc.portraitIndex));
            form.tvMapParcelNpcName.setText(npc.npcName);
        } else {
            form.llMapParcelNpc.setVisibility(View.GONE);
            form.flMapParcelBanner.setVisibility(View.VISIBLE);
        }
        form.tvMapParcelFlora.setText(floraLine != null && !floraLine.isEmpty() ? floraLine : "—");
        form.ivMapParcelFlora.setImageResource(HiveSiteSummaryUi.floraHoneyJarIcon(iconFloraKey));
        form.tvMapParcelHives.setText(hivesText);
        form.tvMapParcelClimate.setText(climateLabel);
        FloraSaturationBar.bindLines(
                form.llMapParcelSaturations, lastFloraSaturations, this::floraLabelForUi);
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
        if (pendingMove != null && !pendingMove.trim().isEmpty()) {
            form.tvMapParcelPending.setVisibility(View.VISIBLE);
            form.tvMapParcelPending.setText(pendingMove);
        } else {
            form.tvMapParcelPending.setVisibility(View.GONE);
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
        if (secondaryLabel != null && onSecondary != null) {
            form.btnMapParcelSecondary.setVisibility(View.VISIBLE);
            form.btnMapParcelSecondary.setText(secondaryLabel);
            form.btnMapParcelSecondary.setOnClickListener(v -> {
                dialog.dismiss();
                onSecondary.run();
            });
        } else {
            form.btnMapParcelSecondary.setVisibility(View.GONE);
        }
        if (sellLabel != null && onSell != null) {
            form.btnMapParcelSell.setVisibility(View.VISIBLE);
            form.btnMapParcelSell.setText(sellLabel);
            form.btnMapParcelSell.setOnClickListener(v -> {
                dialog.dismiss();
                onSell.run();
            });
        } else {
            form.btnMapParcelSell.setVisibility(View.GONE);
        }
        form.btnMapParcelClose.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void markPrimaryHex(String hexId) {
        if (!isAdded() || hexId == null) {
            return;
        }
        ApicultureApp app = (ApicultureApp) requireActivity().getApplication();
        app.getHexParcelRepository().setPrimaryHex(currentUserId, hexId, msg -> {
            if (!isAdded()) {
                return;
            }
            if (msg == null) {
                GameNotice.showSuccess(requireContext(), R.string.hex_primary_ok);
                refreshHexParcelOverlayNow();
            } else {
                GameNotice.show(requireContext(), msg);
            }
        });
    }

    private void openMarketContracts() {
        if (!isAdded()) {
            return;
        }
        Bundle args = new Bundle();
        args.putBoolean(MarketFragment.ARG_OPEN_CONTRACTS, true);
        NavHostFragment.findNavController(this).navigate(R.id.marketFragment, args);
    }

    private void openContractPin(@NonNull String tag) {
        String raw = tag.substring("pollination:".length());
        String[] parts = raw.split("\u001f", 3);
        String hexId = parts.length > 0 ? parts[0] : "";
        String flora = parts.length > 1 ? parts[1] : "";
        int start = 0;
        if (parts.length > 2) {
            try {
                start = Integer.parseInt(parts[2]);
            } catch (NumberFormatException ignored) {
                start = 0;
            }
        }
        PollinationContractRepository.Offer found = null;
        for (PollinationContractRepository.Offer o : lastContractOffers) {
            if (o == null || o.farm == null) {
                continue;
            }
            int oStart = o.farm.terms != null ? o.farm.terms.startDoy : 0;
            if (hexId.equals(o.farm.hexId()) && flora.equals(o.farm.flora) && start == oStart) {
                found = o;
                break;
            }
        }
        if (found == null) {
            scheduleContractPins();
            GameNotice.show(requireContext(), R.string.hex_npc_contract_unavailable);
            return;
        }
        showContractPoint(found);
    }

    private void showContractPoint(@NonNull PollinationContractRepository.Offer offer) {
        if (!isAdded()) {
            return;
        }
        ContractCardDialogs.show(requireContext(), offer, new MarketContractsAdapter.Listener() {
            @Override
            public void onSign(PollinationContractRepository.Offer signed) {
                AcceptContractDialogs.show(requireContext(), signed, lastHives, SharedMapFragment.this::parcelNameOfHex,
                        lastOwnerships, selection -> {
                            if (selection == null || selection.isEmpty()) {
                                GameNotice.show(requireContext(), R.string.market_contract_need_hive_or_buy);
                                return;
                            }
                            acceptMapContract(signed, selection);
                        });
            }

            @Override
            public void onPendingChanged() {
                scheduleHexOverlayRefresh();
                scheduleContractPins();
            }
        });
    }

    @Nullable
    private String parcelNameOfHex(@Nullable String hexId) {
        if (hexId == null) {
            return null;
        }
        for (HexParcelOwnershipEntity row : lastOwnerships) {
            if (row != null && hexId.equals(row.hexId) && row.parcelName != null
                    && !row.parcelName.trim().isEmpty()) {
                return row.parcelName.trim();
            }
        }
        HexParcel p = IberiaHexOverlayStore.findById(requireContext().getApplicationContext(), hexId);
        if (p != null && p.placeName != null && !p.placeName.trim().isEmpty()) {
            return p.placeName.trim();
        }
        return getString(R.string.hex_own_parcel_title);
    }

    private void acceptMapContract(
            @NonNull PollinationContractRepository.Offer offer,
            @NonNull AcceptContractDialogs.Selection selection) {
        if (!isAdded()) {
            return;
        }
        ApicultureApp app = (ApicultureApp) requireActivity().getApplication();
        String uid = currentUserId;
        int level = app.getPlayerProgressRepository().getLevel(uid);
        app.getPollinationContractRepository().accept(uid, offer.farm.hexId(), offer.farm.flora,
                offer.farm.terms != null ? offer.farm.terms.startDoy : 0,
                selection.hiveIds, selection.orders, level, msg -> {
                    if (!isAdded()) {
                        return;
                    }
                    if (msg == null) {
                        GameNotice.showSuccess(requireContext(), R.string.market_contract_accept_ok);
                        scheduleHexOverlayRefresh();
                        scheduleContractPins();
                    } else {
                        GameNotice.show(requireContext(), msg);
                    }
                });
    }

    private boolean isMyContractHex(@Nullable String hexId) {
        if (hexId == null || hexId.isEmpty()) {
            return false;
        }
        if (lastOpenContractHexIds.contains(hexId)) {
            return true;
        }
        for (HiveEntity h : lastHives) {
            if (h == null || !currentUserId.equals(h.ownerId)) {
                continue;
            }
            if (hexId.equals(h.hexId) && h.linkedToContract()) {
                return true;
            }
        }
        return false;
    }

    private String floraLabelForUi(String floraKey) {
        return HiveSiteSummaryUi.floraLabel(requireContext(), floraKey);
    }

    private String joinFloraLabels(List<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return "—";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < keys.size(); i++) {
            if (i > 0) {
                sb.append(" · ");
            }
            sb.append(floraLabelForUi(keys.get(i)));
        }
        return sb.toString();
    }

    private void confirmPurchaseHex(String hexId) {
        confirmPurchaseHex(hexId, false);
    }

    private void confirmPurchaseHex(String hexId, boolean asWarehouse) {
        if (hexOverlayExecutor == null || hexOverlayExecutor.isShutdown() || !isAdded()) {
            return;
        }
        ApicultureApp app = (ApicultureApp) requireContext().getApplicationContext();
        hexOverlayExecutor.execute(() -> {
            List<HexFloraSaturation.Line> sats =
                    app.getHiveRepository().floraSaturationLinesBlocking(hexId);
            lastFloraSaturations = sats;
            mainHandler.post(() -> {
                if (isAdded()) {
                    confirmPurchaseHex(hexId, asWarehouse, sats);
                }
            });
        });
    }

    private void confirmPurchaseHex(String hexId, boolean asWarehouse,
            @Nullable List<HexFloraSaturation.Line> saturations) {
        ApicultureApp app = (ApicultureApp) requireContext().getApplicationContext();
        int playerLevel = app.getPlayerProgressRepository().getLevel(currentUserId);
        HexParcel buyParcel = IberiaHexOverlayStore.findById(
                requireContext().getApplicationContext(), hexId);
        String nativeFlora = HexFlora.nativeFloraForParcel(buyParcel);
        List<String> mix = HexFlora.nativeMixForParcel(buyParcel);
        int landPrice = HiveViewModel.hexPurchasePriceEurosForHex(
                hexId, requireContext().getApplicationContext());
        int price = asWarehouse
                ? landPrice + WarehouseRules.COST_B
                : landPrice;
        int maxHives = HiveViewModel.maxHivesPerParcel();
        DialogHexPurchaseBinding purchaseForm = DialogHexPurchaseBinding.inflate(getLayoutInflater());
        purchaseForm.tvHexPurchaseTitle.setText(asWarehouse
                ? R.string.hex_purchase_warehouse_title
                : R.string.hex_purchase_title);
        purchaseForm.tvHexPurchaseKicker.setText(asWarehouse
                ? R.string.hex_purchase_warehouse_kicker
                : R.string.hex_purchase_kicker);
        purchaseForm.tvHexPurchasePrice.setText(getString(R.string.hex_purchase_price_chip, (double) price));
        purchaseForm.tvHexPurchaseFlora.setText(joinFloraLabels(mix));
        purchaseForm.ivHexPurchaseFlora.setImageResource(
                HiveSiteSummaryUi.floraHoneyJarIcon(nativeFlora));
        purchaseForm.tvHexPurchaseHives.setText(getString(R.string.hex_purchase_hives_value, maxHives));
        purchaseForm.tvHexPurchaseClimate.setText(climateLabelForHex(hexId));
        FloraSaturationBar.bindLines(
                purchaseForm.llHexPurchaseSaturations, saturations, this::floraLabelForUi);
        purchaseForm.cbHexPurchaseWarehouse.setVisibility(View.GONE);
        purchaseForm.editParcelName.setText("");

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
            String name = com.apiculture.simulator.domain.game.EntityNames.clean(
                    purchaseForm.editParcelName.getText().toString());
            if (name == null) {
                GameNotice.show(requireContext(), R.string.hex_purchase_name_required);
                return;
            }
            boolean withWarehouse = asWarehouse;
            double lat = Double.isNaN(lastTapLat)
                    ? (buyParcel != null ? buyParcel.centroidLat : 0)
                    : lastTapLat;
            double lng = Double.isNaN(lastTapLng)
                    ? (buyParcel != null ? buyParcel.centroidLon : 0)
                    : lastTapLng;
            hiveViewModel.purchaseHex(hexId, currentUserId, name, playerLevel, lat, lng, withWarehouse, msg -> {
                if (!isAdded()) {
                    return;
                }
                if (msg == null) {
                    // Capítulo 1, viñeta 8. Apiario instalado.
                    TutorialBus.handoff(dialog);
                    TutorialBus.emit(
                            TutorialEvent.APIARY_INSTALLED);
                    dialog.dismiss();
                    if (withWarehouse) {
                        // Capítulo 1, viñeta 16. Almacén instalado junto al apiario.
                        TutorialBus.emit(
                                TutorialEvent.WAREHOUSE_BOUGHT);
                    }
                    GameNotice.show(requireContext(), withWarehouse
                            ? R.string.hex_purchase_ok_warehouse
                            : R.string.hex_purchase_ok);
                    scheduleHexOverlayRefresh();
                    refreshWarehouseMarkers();
                    refreshApiaryMarkers();
                } else {
                    GameNotice.show(requireContext(), msg);
                }
            });
        });
        if (asWarehouse && TutorialBus.wantsWarehouseChoice()) {
            dialog.setCancelable(false);
            dialog.setCanceledOnTouchOutside(false);
            purchaseForm.btnHexPurchaseCancel.setVisibility(View.GONE);
        }
        dialog.show();
        // Capítulo 1, viñeta 8. Explica flora, saturación y nombre.
        TutorialBus.emitDialog(
                TutorialEvent.PURCHASE_FORM,
                dialog, null);
    }

    private void showPlantAdditionalFloraDialog(String hexId) {
        if (!isAdded()) {
            return;
        }
        // Capítulo 3. Siembra.
        TutorialBus.emit(
                TutorialEvent.PLANTING);
        final Context appCtx = requireContext().getApplicationContext();
        hexOverlayExecutor.execute(() -> {
            ApicultureApp app = (ApicultureApp) appCtx;
            List<String> occupied = new ArrayList<>();
            for (HexParcelFloraEntity e : app.getHexFloraRepository().listEntriesForHexBlocking(hexId)) {
                occupied.add(HoneyMarketEngine.canonicalFloraKey(e.floraKey));
            }
            HexParcel plantParcel = IberiaHexOverlayStore.findById(appCtx, hexId);
            int playerLevel = app.getPlayerProgressRepository().getLevel(currentUserId);
            List<String> allPlantable = new ArrayList<>();
            List<String> nearbyCandidates = new ArrayList<>();

            LocalDate today = LocalDate.now();
            for (String f : CropUnlock.plantableOnParcel(plantParcel, playerLevel)) {
                if (occupied.contains(f)) {
                    continue;
                }
                allPlantable.add(f);
                int daysToBloom = FloraBloomWindow.daysUntilBloomStart(f, plantParcel, today);
                if (daysToBloom <= 60) {
                    nearbyCandidates.add(f);
                }
            }
            List<String> candidates = !nearbyCandidates.isEmpty() ? nearbyCandidates : allPlantable;

            List<String> itemTexts = new ArrayList<>();
            List<String> itemDetails = new ArrayList<>();
            List<Integer> jarIcons = new ArrayList<>();
            for (String f : candidates) {
                int cost = CropRules.plantCostEuros(f);
                int days = CropRules.growDays(f);
                String bloom = FloraBloomWindow.formatEsForParcel(f, plantParcel);
                itemTexts.add(floraLabelForUi(f));
                if (CropRules.isAnnual(f)) {
                    itemDetails.add(getString(R.string.map_plant_flora_item_annual,
                            floraLabelForUi(f), (double) cost, days, bloom));
                } else {
                    itemDetails.add(getString(R.string.map_plant_flora_item_tree,
                            floraLabelForUi(f), (double) cost, days, bloom,
                            (double) CropRules.treeMaintenanceEuros(f)));
                }
                jarIcons.add(HiveSiteSummaryUi.floraHoneyJarIcon(f));
            }

            boolean lockedRemain = false;
            if (allPlantable.isEmpty()) {
                for (String f : HexFlora.plantationPoolForParcel(plantParcel)) {
                    if (occupied.contains(f)) {
                        continue;
                    }
                    if (!CropUnlock.isUnlocked(f, playerLevel)) {
                        lockedRemain = true;
                        break;
                    }
                }
            }
            final boolean showLocked = lockedRemain;
            mainHandler.post(() -> {
                if (!isAdded()) {
                    return;
                }
                if (candidates.isEmpty()) {
                    GameNotice.show(requireContext(),
                            showLocked ? R.string.map_plant_flora_locked : R.string.map_plant_flora_none);
                    return;
                }

                DialogPlantFloraBinding form = DialogPlantFloraBinding.inflate(getLayoutInflater());
                Dialog dialog = new Dialog(requireContext());
                dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
                dialog.setContentView(form.getRoot());
                dialog.setCancelable(true);
                Window window = dialog.getWindow();
                if (window != null) {
                    window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                    window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                }

                for (int i = 0; i < candidates.size(); i++) {
                    final int idx = i;
                    String pick = candidates.get(idx);
                    View row = getLayoutInflater().inflate(R.layout.item_plant_flora, form.llPlantItems, false);
                    ((ImageView) row.findViewById(R.id.iv_plant_item_jar)).setImageResource(jarIcons.get(idx));
                    ((TextView) row.findViewById(R.id.tv_plant_item_title)).setText(itemTexts.get(idx));
                    ((TextView) row.findViewById(R.id.tv_plant_item_details)).setText(itemDetails.get(idx));
                    row.setOnClickListener(v -> {
                        dialog.dismiss();
                        hiveViewModel.plantAdditionalFlora(hexId, currentUserId, pick, msg -> {
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
                    });
                    form.llPlantItems.addView(row);
                }

                form.btnPlantClose.setOnClickListener(v -> dialog.dismiss());
                dialog.show();
            });
        });
    }

    private void moveCameraToOwnOrFirstHive(List<HiveEntity> hives) {
        if (googleMap == null) {
            return;
        }
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultCameraTarget(hives), OPEN_MAP_ZOOM));
    }

    @NonNull
    private LatLng defaultCameraTarget(@Nullable List<HiveEntity> hives) {
        HeadquartersStore.Hq hq = HeadquartersStore.get(requireContext(), currentUserId, activeRegion);
        if (hq != null) {
            return new LatLng(hq.lat, hq.lng);
        }
        HiveEntity own = findOwnHiveInRegion(hives);
        if (own != null) {
            return new LatLng(own.lat, own.lng);
        }
        return new LatLng(activeRegion.defaultLookLat(), activeRegion.defaultLookLon());
    }

    @Nullable
    private HiveEntity findOwnHiveInRegion(@Nullable List<HiveEntity> hives) {
        if (hives == null) {
            return null;
        }
        for (HiveEntity hive : hives) {
            if (hive != null
                    && currentUserId.equals(hive.ownerId)
                    && activeRegion.containsHive(hive.lat, hive.lng)) {
                return hive;
            }
        }
        return null;
    }

    private void setupRegionToggle() {
        if (binding == null || binding.mapRegionToggle == null) {
            return;
        }
        suppressRegionToggle = true;
        binding.mapRegionToggle.check(regionButtonId(activeRegion));
        suppressRegionToggle = false;
        applyLegendForActiveRegion();
        bindZaLockState();
        binding.mapRegionToggle.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked || suppressRegionToggle) {
                return;
            }
            PlayableMapRegion next = regionFromButton(checkedId);
            if (next == PlayableMapRegion.SOUTH_AFRICA && !zaUnlocked()) {
                suppressRegionToggle = true;
                group.check(regionButtonId(activeRegion));
                suppressRegionToggle = false;
                showZaLockedDialog();
                return;
            }
            if (next == activeRegion) {
                return;
            }
            switchToRegion(next);
        });
    }

    /** Tras un desbloqueo de clima: quita el candado de ZA y recolorea hexes. */
    public void onClimateUnlockChanged() {
        if (!isAdded() || binding == null) {
            return;
        }
        bindZaLockState();
        scheduleHexOverlayRefresh();
    }

    private static int regionButtonId(PlayableMapRegion region) {
        if (region == PlayableMapRegion.SOUTH_AFRICA) {
            return R.id.btn_map_za;
        }
        if (region == PlayableMapRegion.MADAGASCAR) {
            return R.id.btn_map_mdg;
        }
        return R.id.btn_map_iberia;
    }

    private static PlayableMapRegion regionFromButton(int checkedId) {
        if (checkedId == R.id.btn_map_za) {
            return PlayableMapRegion.SOUTH_AFRICA;
        }
        if (checkedId == R.id.btn_map_mdg) {
            return PlayableMapRegion.MADAGASCAR;
        }
        return PlayableMapRegion.IBERIA;
    }

    private boolean zaUnlocked() {
        ApicultureApp app = (ApicultureApp) requireContext().getApplicationContext();
        int level = app.getPlayerProgressRepository().getLevel(currentUserId);
        return ClimateUnlock.canAccessSouthAfrica(level);
    }

    private void bindZaLockState() {
        if (binding == null || binding.btnMapZa == null) {
            return;
        }
        boolean unlocked = zaUnlocked();
        if (unlocked) {
            binding.btnMapZa.setIcon(null);
        } else {
            binding.btnMapZa.setIconResource(R.drawable.ic_lock_padlock);
        }
    }

    private void showZaLockedDialog() {
        MarketPickerDialogs.showSouthAfricaLocked(requireContext());
    }

    private void switchToRegion(PlayableMapRegion next) {
        if (next == null) {
            next = PlayableMapRegion.IBERIA;
        }
        final PlayableMapRegion target = next;
        activeRegion = target;
        MapRegionPrefs.set(requireContext(), target);
        applyLegendForActiveRegion();
        bindZaLockState();
        bindFloraSpinner();
        cameraPlacedForRegion = false;
        selectedOwnHiveId = null;
        lastMarkerZoomBucket = Float.NaN;
        hexOverlayGeneration.incrementAndGet();
        removeHexOverlayPolygons();
        boolean ready = IberiaHexOverlayStore.isLoaded(target);
        setOverlayBusy(!ready);
        if (!ready && GameStartupWarmup.isReady()) {
            GameNotice.show(requireContext(), R.string.map_region_loading);
        }
        if (target.hasRoadGraph()
                && TruckLivePrefs.isEnabled(requireContext())
                && !RoutingGraphDownloader.isInstalled(requireContext(), target)) {
            GraphInstallDialog.show(requireContext(), target);
            RoutingGraphDownloader.enqueue(requireContext(), target);
        }
        finishRegionCameraAndDraw();
    }

    private void finishRegionCameraAndDraw() {
        renderHiveMarkers(lastHives);
        cameraPlacedForRegion = true;
        applyCameraConstraints(true);
        if (googleMap != null && hasPendingFocus()) {
            tryApplyPendingFocus();
        }
        refreshHexParcelOverlayNow();
        refreshHqMarker();
        refreshPortMarkers();
        refreshMarketMarkers();
        scheduleContractPins();
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
        if (moveNow && !hasPendingFocus()) {
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
        boolean mdg = activeRegion == PlayableMapRegion.MADAGASCAR;
        if (binding.legendIberia != null) {
            binding.legendIberia.setVisibility(!za && !mdg ? View.VISIBLE : View.GONE);
        }
        if (binding.legendZa != null) {
            binding.legendZa.setVisibility(za ? View.VISIBLE : View.GONE);
        }
        if (binding.legendMdg != null) {
            binding.legendMdg.setVisibility(mdg ? View.VISIBLE : View.GONE);
        }
        if (binding.tvClimateLegend != null) {
            binding.tvClimateLegend.setText(za || mdg
                    ? R.string.map_climate_legend_za
                    : R.string.map_climate_legend);
        }
    }

    private void setupMapInteractions() {
        googleMap.setOnMapLongClickListener(latLng -> {
            HexParcel hex = IberiaHexOverlayStore.findContaining(
                    requireContext().getApplicationContext(), latLng.latitude, latLng.longitude);
            if (hex == null) {
                GameNotice.show(requireContext(), R.string.hive_buy_outside_playable);
                return;
            }
            if (!ownsHex(hex.id)) {
                GameNotice.show(requireContext(), R.string.hive_buy_need_own_hex);
                return;
            }
            lastTapLat = latLng.latitude;
            lastTapLng = latLng.longitude;
            WarehouseDialogs.showBuildChoice(this, hiveViewModel, hex.id, hexHasWarehouse(hex.id),
                    latLng.latitude, latLng.longitude);
        });

        googleMap.setOnMapClickListener(latLng -> {
            if (selectedOwnHiveId != null) {
                HiveEntity hive = hiveById.get(selectedOwnHiveId);
                if (hive == null) {
                    return;
                }
                if (TranshumanceRules.hasPendingContractMove(hive)) {
                    GameNotice.show(requireContext(), R.string.map_transhumance_pending);
                    selectedOwnHiveId = null;
                    scheduleHexOverlayRefresh();
                    return;
                }
                if (TruckLiveTrips.hasActive(requireContext(), hive.id)) {
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
                            boolean contractDest = destHex != null && isMyContractHex(destHex.id);
                            GameNotice.show(requireContext(), contractDest
                                    ? R.string.hive_transhumance_scheduled_ok
                                    : R.string.hive_transhumance_ok);
                        }
                    } else {
                        TruckTripUi.showTravelOutcome(requireContext(), msg);
                    }
                    selectedOwnHiveId = null;
                    scheduleHexOverlayRefresh();
                });
                if (sameHex) {
                    go.run();
                    return;
                }
                boolean contractDest = destHex != null && isMyContractHex(destHex.id);
                int cost;
                if (contractDest) {
                    cost = PollinationContractRules.travelCostPerHiveKm(TranshumanceRules.haversineKm(
                            hive.lat, hive.lng, latLng.latitude, latLng.longitude));
                } else {
                    cost = TranshumanceRules.costEuros(hive.lat, hive.lng, latLng.latitude, latLng.longitude);
                }
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle(R.string.map_transhumance_confirm_title)
                        .setMessage(getString(contractDest
                                ? R.string.map_transhumance_contract_confirm_message
                                : R.string.map_transhumance_confirm_message, (double) cost))
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
            waitForRegionOverlay(activeRegion, () -> {
                if (isAdded()) {
                    handleMapTapForHex(latLng);
                }
            });
            return;
        }
        HexParcel hex = IberiaHexOverlayStore.findContaining(
                requireContext().getApplicationContext(), latLng.latitude, latLng.longitude);
        if (hex == null) {
            return;
        }
        lastTapLat = latLng.latitude;
        lastTapLng = latLng.longitude;
        onHexSelected(hex.id);
    }

    private void setupMapHud() {
        bindFloraSpinner();
        if (binding.btnMapHelp != null) {
            binding.btnMapHelp.setOnClickListener(v -> new MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.map_help_title)
                    .setMessage(R.string.map_help_text)
                    .setPositiveButton(android.R.string.ok, null)
                    .show());
        }
    }

    private void bindFloraSpinner() {
        if (binding == null || binding.spinnerMapFlora == null) {
            return;
        }
        List<FloraOption> options = new ArrayList<>();
        options.add(new FloraOption(null, getString(R.string.map_flora_filter_all)));
        for (String key : HexFlora.nativeKeysForRegion(activeRegion)) {
            options.add(new FloraOption(key, floraLabelForUi(key)));
        }
        ArrayAdapter<FloraOption> adapter = new ArrayAdapter<>(
                requireContext(), R.layout.item_map_flora_spinner, options);
        adapter.setDropDownViewResource(R.layout.item_map_flora_spinner_dropdown);
        suppressFloraSpinner = true;
        binding.spinnerMapFlora.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (suppressFloraSpinner) {
                    return;
                }
                FloraOption option = (FloraOption) parent.getItemAtPosition(position);
                String key = option == null ? null : option.key;
                MapOverlayPrefs.setFloraFilter(requireContext(), key);
                scheduleHexOverlayRefresh();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        binding.spinnerMapFlora.setAdapter(adapter);
        String saved = MapOverlayPrefs.getFloraFilter(requireContext());
        int selected = 0;
        if (saved != null) {
            for (int i = 0; i < options.size(); i++) {
                if (saved.equals(options.get(i).key)) {
                    selected = i;
                    break;
                }
            }
            if (selected == 0) {
                MapOverlayPrefs.setFloraFilter(requireContext(), null);
            }
        }
        binding.spinnerMapFlora.setSelection(selected, false);
        binding.spinnerMapFlora.post(() -> suppressFloraSpinner = false);
    }

    private static final class FloraOption {
        @Nullable
        final String key;
        final String label;

        FloraOption(@Nullable String key, String label) {
            this.key = key;
            this.label = label;
        }

        @NonNull
        @Override
        public String toString() {
            return label;
        }
    }

    private boolean ownsHex(@Nullable String hexId) {
        if (hexId == null || lastOwnerships == null) {
            return false;
        }
        for (HexParcelOwnershipEntity row : lastOwnerships) {
            if (row != null && hexId.equals(row.hexId) && currentUserId.equals(row.ownerId)) {
                return true;
            }
        }
        return false;
    }

    private boolean hexHasWarehouse(@Nullable String hexId) {
        if (hexId == null || lastOwnerships == null) {
            return false;
        }
        for (HexParcelOwnershipEntity row : lastOwnerships) {
            if (row != null && hexId.equals(row.hexId) && row.hasWarehouse
                    && currentUserId.equals(row.ownerId)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean onMarkerClick(@NonNull Marker marker) {
        Object tag = marker.getTag();
        if (tag instanceof MapPinGroups.Focus) {
            zoomToPins(((MapPinGroups.Focus) tag).points);
            return true;
        }
        if (tag instanceof String && ((String) tag).startsWith("pollination:")) {
            openContractPin((String) tag);
            return true;
        }
        if (tag instanceof String && ((String) tag).startsWith("order:")) {
            String id = ((String) tag).substring("order:".length());
            HoneyOrderEntity found = null;
            for (HoneyOrderEntity e : lastOrders) {
                if (e != null && id.equals(e.id)) {
                    found = e;
                    break;
                }
            }
            if (found == null) {
                refreshOrderMarkers();
                return true;
            }
            boolean mine = found.taken && currentUserId != null && currentUserId.equals(found.claimedBy);
            if (!mine && found.expireEpochMs <= System.currentTimeMillis()) {
                GameNotice.show(requireContext(), R.string.market_order_fail_deadline);
                refreshOrderMarkers();
                return true;
            }
            if (found.taken && !mine) {
                refreshOrderMarkers();
                return true;
            }
            HoneyOrder order = HoneyOrder.fromEntity(found);
            if (!mine) {
                ApicultureApp app = (ApicultureApp) requireActivity().getApplication();
                int level = app.getPlayerProgressRepository().getLevel(currentUserId);
                if (!HoneyMarketEngine.playerCanAccessFlora(order.floraKey, level)
                        || OfferBand.ofLevel(level).index != order.band) {
                    int need = HoneyMarketEngine.accessLevelForFlora(order.floraKey);
                    GameNotice.show(requireContext(), getString(R.string.market_order_flora_locked,
                            floraLabelForUi(order.floraKey), need));
                    return true;
                }
            }
            HoneyLogistics.warehouseParcelAsync(requireContext(), currentUserId, warehouse -> {
                if (!isAdded()) {
                    return;
                }
                HoneyOrderDialogs.show(this, order, warehouse, mine ? null : this::acceptMapOrder, mine);
            });
            return true;
        }
        if (tag instanceof String && ((String) tag).startsWith("apiary:")) {
            String[] parts = parseApiaryTag(tag, "apiary:");
            if (parts != null) {
                if (TutorialBus.enterApiaryByMarker()) {
                    // Capítulo 1, viñeta 9. La imagen del apiario abre el apiario.
                    openApiaryYard(parts[0], parts[2], false);
                } else {
                    showOwnedApiaryDialog(parts[0], parts[2]);
                }
            }
            return true;
        }
        if (tag instanceof String && ((String) tag).startsWith("other-apiary:")) {
            String[] parts = parseApiaryTag(tag, "other-apiary:");
            if (parts != null) {
                showOtherApiaryDialog(parts[0], parts[1], parts[2]);
            }
            return true;
        }
        if (tag instanceof String && "hq".equals(tag)) {
            showHeadquartersDialog();
            return true;
        }
        if (tag instanceof String && ((String) tag).startsWith("warehouse:")) {
            String rest = ((String) tag).substring("warehouse:".length());
            String[] parts = rest.split("\t", 2);
            String hexId = parts[0];
            String siteId = parts.length > 1 ? parts[1] : "default";
            WarehouseDialogs.showEnter(this, hiveViewModel, lastOwnerships, currentUserId, hexId, siteId);
            return true;
        }
        if (tag instanceof String && "shop".equals(tag)) {
            if (TutorialBus.expectsShop()) {
                TutorialBus.emit(TutorialEvent.SHOP_OPENED);
            }
            Bundle args = new Bundle();
            if (marker != null) {
                args.putDouble("shopLat", marker.getPosition().latitude);
                args.putDouble("shopLng", marker.getPosition().longitude);
            }
            NavHostFragment.findNavController(this)
                    .navigate(R.id.shopFragment, args);
            return true;
        }
        if (tag instanceof String && ((String) tag).startsWith("cargo:")) {
            String id = ((String) tag).substring("cargo:".length());
            CargoTripEntity found = null;
            for (CargoTripEntity trip : lastCargo) {
                if (trip != null && id.equals(trip.id)) {
                    found = trip;
                    break;
                }
            }
            if (found == null) {
                return true;
            }
            List<CargoTripEntity> bundle = new ArrayList<>();
            String shipment = found.shipmentId;
            for (CargoTripEntity trip : lastCargo) {
                if (trip == null) {
                    continue;
                }
                if (shipment != null && !shipment.isEmpty() && shipment.equals(trip.shipmentId)) {
                    bundle.add(trip);
                } else if ((shipment == null || shipment.isEmpty()) && id.equals(trip.id)) {
                    bundle.add(trip);
                }
            }
            MapVehicleDialogs.showCargo(this, found, bundle);
            return true;
        }
        if (tag instanceof String && ((String) tag).startsWith("truck:")) {
            String hiveId = ((String) tag).substring("truck:".length());
            for (TruckTripEntity trip : lastTrips) {
                if (trip != null && hiveId.equals(trip.hiveId)) {
                    MapVehicleDialogs.showHiveTruck(this, trip, truckLevelForHive(hiveId));
                    return true;
                }
            }
            return true;
        }
        if (tag instanceof String && ((String) tag).startsWith("port:")) {
            String portId = ((String) tag).substring("port:".length());
            Seaport port =
                    SeaportCatalog.byId(portId);
            if (port != null) {
                FleetDialogs.showPort(this, currentUserId, port);
            }
            return true;
        }
        if (tag instanceof String && ((String) tag).startsWith("market:")) {
            String name = ((String) tag).substring("market:".length());
            ProvincialMarket market = ProvincialMarketCatalog.findByName(
                    requireContext(), activeRegion, name);
            if (market == null) {
                GameNotice.show(requireContext(), getString(R.string.map_market_title, name));
                return true;
            }
            LocalMarketDialogs.show(this, market);
            return true;
        }
        if (!(tag instanceof String)) {
            return true;
        }
        HiveEntity hive = hiveById.get((String) tag);
        if (hive == null) {
            for (HiveEntity row : lastHives) {
                if (row != null && ((String) tag).equals(row.id)) {
                    hive = row;
                    break;
                }
            }
        }
        if (hive == null) {
            return true;
        }
        final HiveEntity selected = hive;
        if (!currentUserId.equals(selected.ownerId)) {
            com.apiculture.simulator.presentation.dashboard.MailDialogs.showHiveFriend(
                    this, selected.ownerId == null ? "" : selected.ownerId);
            return true;
        }
        DialogMapHiveBinding form = DialogMapHiveBinding.inflate(getLayoutInflater());
        String hiveTitle = selected.name != null && !selected.name.trim().isEmpty()
                ? selected.name.trim()
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
            openHiveDetail(selected.id);
        });
        form.btnMapHiveTranshumance.setOnClickListener(v -> {
            dialog.dismiss();
            startTranshumancePick(selected);
        });
        form.btnMapHiveCancel.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
        return true;
    }

    private void acceptMapOrder(@NonNull HoneyOrder order, double travelB) {
        ApicultureApp app = (ApicultureApp) requireActivity().getApplication();
        int level = app.getPlayerProgressRepository().getLevel(currentUserId);
        if (!HoneyMarketEngine.playerCanAccessFlora(order.floraKey, level)
                || OfferBand.ofLevel(level).index != order.band) {
            GameNotice.show(requireContext(), getString(R.string.market_order_flora_locked,
                    floraLabelForUi(order.floraKey),
                    HoneyMarketEngine.accessLevelForFlora(order.floraKey)));
            return;
        }
        if (mapOrderAcceptOpen) {
            return;
        }
        mapOrderAcceptOpen = true;
        HoneyLogistics.orderTruckOptions(requireContext(), currentUserId, order, choice -> {
            if (!isAdded()) {
                mapOrderAcceptOpen = false;
                return;
            }
            if (choice.instant) {
                mapOrderAcceptOpen = false;
                sendMapOrder(order, travelB, null);
                return;
            }
            if (choice.trucks.isEmpty()) {
                mapOrderAcceptOpen = false;
                GameNotice.show(requireContext(), choice.missingHoney
                        ? R.string.market_order_fail_stock
                        : R.string.market_order_fail_truck);
                return;
            }
            HoneyOrderDialogs.showTrucks(this, choice.trucks,
                    pick -> sendMapOrder(order, pick.travelCostB, pick.truckId),
                    () -> mapOrderAcceptOpen = false);
        });
    }

    private void sendMapOrder(@NonNull HoneyOrder order, double travelB, @Nullable String truckId) {
        ApicultureApp app = (ApicultureApp) requireActivity().getApplication();
        HoneyLogistics.dispatchOrder(
                requireContext(), currentUserId, order, app.getEconomyRepository(), truckId, r -> {
                    if (!isAdded()) {
                        return;
                    }
                    if (r == HoneyLogistics.Result.STARTED) {
                        GameNotice.showSuccess(requireContext(), getString(R.string.market_order_ok, order.destLabel));
                    } else if (r == HoneyLogistics.Result.INSTANT) {
                        GameNotice.showSuccess(requireContext(),
                                getString(R.string.market_order_instant, order.payout()));
                    } else if (r == HoneyLogistics.Result.TOO_SLOW) {
                        GameNotice.show(requireContext(), R.string.market_order_fail_slow);
                    } else if (r == HoneyLogistics.Result.NO_CASH) {
                        GameNotice.show(requireContext(), R.string.market_order_fail_travel);
                    } else if (order.expired(System.currentTimeMillis())) {
                        GameNotice.show(requireContext(), R.string.market_order_fail_deadline);
                    } else if (!HoneyLogistics.hasStockForOrder(requireContext(), currentUserId,
                            app.getEconomyRepository(), order)) {
                        GameNotice.show(requireContext(), order.wantsJars()
                                ? R.string.market_order_fail_jars : R.string.market_order_fail_stock);
                    } else if (r == HoneyLogistics.Result.NO_FLEET) {
                        GameNotice.show(requireContext(), R.string.market_order_fail_truck);
                    } else if (app.getEconomyRepository().getBalance() + 1e-9 < travelB) {
                        GameNotice.show(requireContext(), R.string.market_order_fail_travel);
                    } else {
                        String why = HoneyLogistics.consumeOrderFailDetail();
                        GameNotice.show(requireContext(),
                                why != null && !why.isEmpty() ? why : getString(R.string.market_order_fail));
                    }
                    refreshOrderMarkers();
                });
    }

    private void openApiaryYard(@Nullable String hexId, boolean contractYard) {
        openApiaryYard(hexId, null, contractYard);
    }

    private void openApiaryYard(@Nullable String hexId, @Nullable String siteId, boolean contractYard) {
        openApiaryYard(hexId, siteId, contractYard, false);
    }

    private void openApiaryYard(@Nullable String hexId, @Nullable String siteId, boolean contractYard,
            boolean open3d) {
        if (hexId == null || hexId.isEmpty() || !isAdded()) {
            return;
        }
        String name = hexId;
        String wantSite = siteId != null && !siteId.isEmpty() ? siteId : null;
        for (HexParcelOwnershipEntity row : lastOwnerships) {
            if (row == null || !hexId.equals(row.hexId)) {
                continue;
            }
            if (wantSite != null) {
                String got = row.siteId != null && !row.siteId.isEmpty() ? row.siteId : "default";
                if (!wantSite.equals(got)) {
                    continue;
                }
            }
            if (row.parcelName != null && !row.parcelName.trim().isEmpty()) {
                name = row.parcelName.trim();
                break;
            }
        }
        Bundle args = new Bundle();
        args.putString("hexId", hexId);
        args.putString("siteId", wantSite);
        args.putString("parcelName", name);
        args.putBoolean("contractYard", contractYard);
        args.putBoolean(com.apiculture.simulator.presentation.hive.ApiaryYardFragment.ARG_OPEN_3D, open3d);
        NavHostFragment.findNavController(this).navigate(R.id.apiaryYardFragment, args);
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
        // Capítulo 4. Transhumancia.
        TutorialBus.emit(
                TutorialEvent.TRANSHUMANCE);
        if (hive == null || hive.id == null) {
            return;
        }
        if (TranshumanceRules.hasPendingContractMove(hive)) {
            GameNotice.show(requireContext(), R.string.map_transhumance_pending);
            return;
        }
        if (TruckLiveTrips.hasActive(requireContext(), hive.id)) {
            GameNotice.show(requireContext(), R.string.map_transhumance_busy);
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

    @Override
    public void onResume() {
        super.onResume();
        startTruckTicker();
        RoutingGraphDownloader.refreshStatus(requireContext());
        if (binding != null) {
            bindZaLockState();
        }
        Bundle args = getArguments();
        if (args != null && args.getBoolean(ARG_HINT_TRANSHUMANCE, false)) {
            args.putBoolean(ARG_HINT_TRANSHUMANCE, false);
            GameNotice.show(requireContext(), R.string.map_transhumance_pick_destination);
        }
        applyFocusFromArgs();
        ApicultureApp app = (ApicultureApp) requireActivity().getApplication();
        app.getPollinationContractRepository().getOpenListForOwner(currentUserId, rows -> {
            lastOpenContractHexIds.clear();
            if (rows != null) {
                for (PollinationContractEntity row : rows) {
                    if (row != null && row.hexId != null
                            && (PollinationContractRules.STATUS_ACTIVE.equals(row.status)
                            || PollinationContractRules.STATUS_RETURNING.equals(row.status))) {
                        lastOpenContractHexIds.add(row.hexId);
                    }
                }
            }
            scheduleHexOverlayRefresh();
        });
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
        detachOverlayWarmup();
        hexOverlayGeneration.incrementAndGet();
        if (hexOverlayExecutor != null) {
            hexOverlayExecutor.shutdownNow();
            hexOverlayExecutor = null;
        }
        if (tripExecutor != null) {
            tripExecutor.shutdownNow();
            tripExecutor = null;
        }
        mainHandler.removeCallbacksAndMessages(null);
        super.onDestroyView();
    }
}
