package com.apiculture.simulator.data.repository;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.apiculture.simulator.data.local.dao.HiveDao;
import com.apiculture.simulator.data.local.dao.PollinationContractDao;
import com.apiculture.simulator.data.local.entity.HiveEntity;
import com.apiculture.simulator.data.local.entity.PollinationContractEntity;
import com.apiculture.simulator.data.local.entity.PollinationOfferEntity;
import com.apiculture.simulator.data.local.entity.TruckTripEntity;
import com.apiculture.simulator.data.remote.OpenMeteoElevation;
import com.apiculture.simulator.R;
import com.apiculture.simulator.domain.game.ClimateUnlock;
import com.apiculture.simulator.domain.game.FloraBloomWindow;
import com.apiculture.simulator.domain.game.GameBalanceConfig;
import com.apiculture.simulator.domain.game.GameCalendar;
import com.apiculture.simulator.domain.game.NpcContractCatalog;
import com.apiculture.simulator.domain.game.NpcContractFarm;
import com.apiculture.simulator.domain.game.OfferBand;
import com.apiculture.simulator.domain.game.PollinationOfferCatalog;
import com.apiculture.simulator.domain.game.PollinationContractRules;
import com.apiculture.simulator.domain.game.PollinationPayTerms;
import com.apiculture.simulator.domain.game.TranshumanceRules;
import com.apiculture.simulator.domain.game.PollinationContractCrops;
import com.apiculture.simulator.domain.map.PlayableMapRegion;
import com.apiculture.simulator.domain.parcel.CropUnlock;
import com.apiculture.simulator.domain.parcel.HexFlora;
import com.apiculture.simulator.domain.parcel.HexParcel;
import com.apiculture.simulator.domain.parcel.HexParcelGameRules;
import com.apiculture.simulator.domain.parcel.HexParcelRandomPoint;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.SetOptions;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Convenios de polinización NPC: listado, aceptación, acumulación y liquidación.
 */
public class PollinationContractRepository {

    private static final String TAG = "PollinationContracts";
    public static final String COLLECTION = "pollinationContracts";
    private static final long OFFER_CACHE_MS = 12_000L;

    private volatile List<Offer> cachedOffers;
    private volatile String cachedOfferKey;
    private volatile String cachedPendingFp;
    private volatile long cachedOfferAtMs;

    public static final class Offer {
        public final NpcContractFarm farm;
        public final int distanceHex;
        public final int travelCostB;
        public final boolean occupied;
        public final boolean mine;
        public final boolean lockedClimate;
        public final boolean reserveHidden;
        /** Cierto si el jugador no tiene colmenas fuera del almacén para calcular el viaje. */
        public final boolean noSendableHives;
        public final String windowLabel;
        public final double collectedKg;
        public final double poolKg;
        public final List<HiveEntity> pendingHives;
        /** Km hasta el apiario propio más cercano. */
        public final double distanceKm;

        public Offer(NpcContractFarm farm, int distanceHex, int travelCostB, boolean occupied,
                boolean mine, boolean lockedClimate, boolean reserveHidden, String windowLabel) {
            this(farm, distanceHex, travelCostB, occupied, mine, lockedClimate, reserveHidden, windowLabel, 0, 0);
        }

        public Offer(NpcContractFarm farm, int distanceHex, int travelCostB, boolean occupied,
                boolean mine, boolean lockedClimate, boolean reserveHidden, String windowLabel,
                double collectedKg, double poolKg) {
            this(farm, distanceHex, travelCostB, occupied, mine, lockedClimate, reserveHidden,
                    windowLabel, collectedKg, poolKg, Collections.emptyList(), 0);
        }

        public Offer(NpcContractFarm farm, int distanceHex, int travelCostB, boolean occupied,
                boolean mine, boolean lockedClimate, boolean reserveHidden, String windowLabel,
                double collectedKg, double poolKg, @Nullable List<HiveEntity> pendingHives) {
            this(farm, distanceHex, travelCostB, occupied, mine, lockedClimate, reserveHidden,
                    windowLabel, collectedKg, poolKg, pendingHives, 0, false);
        }

        public Offer(NpcContractFarm farm, int distanceHex, int travelCostB, boolean occupied,
                boolean mine, boolean lockedClimate, boolean reserveHidden, String windowLabel,
                double collectedKg, double poolKg, @Nullable List<HiveEntity> pendingHives,
                double distanceKm) {
            this(farm, distanceHex, travelCostB, occupied, mine, lockedClimate, reserveHidden,
                    windowLabel, collectedKg, poolKg, pendingHives, distanceKm, false);
        }

        public Offer(NpcContractFarm farm, int distanceHex, int travelCostB, boolean occupied,
                boolean mine, boolean lockedClimate, boolean reserveHidden, String windowLabel,
                double collectedKg, double poolKg, @Nullable List<HiveEntity> pendingHives,
                double distanceKm, boolean noSendableHives) {
            this.farm = farm;
            this.distanceHex = distanceHex;
            this.travelCostB = travelCostB;
            this.occupied = occupied;
            this.mine = mine;
            this.lockedClimate = lockedClimate;
            this.reserveHidden = reserveHidden;
            this.noSendableHives = noSendableHives;
            this.windowLabel = windowLabel;
            this.collectedKg = Math.max(0.0, collectedKg);
            this.poolKg = Math.max(0.0, poolKg);
            this.pendingHives = pendingHives != null && !pendingHives.isEmpty()
                    ? Collections.unmodifiableList(new ArrayList<>(pendingHives))
                    : Collections.emptyList();
            this.distanceKm = distanceKm > 0 && !Double.isNaN(distanceKm) ? distanceKm : 0;
        }

        public int payB() {
            return farm != null && farm.terms != null ? farm.terms.payB : 0;
        }

        /** Pago menos el viaje más barato desde tus apiarios. */
        public int netRewardB() {
            return payB() - travelCostB;
        }

        public boolean worthTraveling() {
            return netRewardB() > 0;
        }

        public int travelCostForHives(@Nullable List<HiveEntity> hives) {
            if (farm == null || farm.parcel == null) {
                return 99_999;
            }
            return PollinationContractRules.travelCostForHives(
                    hives, farm.parcel.centroidLat, farm.parcel.centroidLon);
        }

    }

    public static final class HiveOrder {
        public final int quantity;
        public final int superCount;

        public HiveOrder(int quantity, int superCount) {
            this.quantity = Math.max(1, Math.min(8, quantity));
            this.superCount = Math.max(0, Math.min(2, superCount));
        }

        public int unitPriceB() {
            return HiveRepository.purchasePriceEurosForSuperCount(superCount);
        }

        public int totalB() {
            return unitPriceB() * quantity;
        }

        public static int hiveCount(@Nullable List<HiveOrder> orders) {
            if (orders == null || orders.isEmpty()) {
                return 0;
            }
            int n = 0;
            for (HiveOrder o : orders) {
                if (o != null) {
                    n += o.quantity;
                }
            }
            return n;
        }

        public static int totalB(@Nullable List<HiveOrder> orders) {
            if (orders == null || orders.isEmpty()) {
                return 0;
            }
            int n = 0;
            for (HiveOrder o : orders) {
                if (o != null) {
                    n += o.totalB();
                }
            }
            return n;
        }
    }

    private final PollinationContractDao dao;
    private final HiveDao hiveDao;
    private final EconomyRepository economyRepository;
    private final Context appContext;
    @Nullable
    private final FirebaseFirestore firestore;
    @Nullable
    private PlayerProgressRepository playerProgressRepository;
    @Nullable
    private HiveRepository hiveRepository;
    @Nullable
    private HexParcelRepository hexParcelRepository;
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public PollinationContractRepository(
            PollinationContractDao dao,
            HiveDao hiveDao,
            EconomyRepository economyRepository,
            Context appContext,
            @Nullable FirebaseFirestore firestore) {
        this.dao = dao;
        this.hiveDao = hiveDao;
        this.economyRepository = economyRepository;
        this.appContext = appContext.getApplicationContext();
        this.firestore = firestore;
    }

    public void setPlayerProgressRepository(@Nullable PlayerProgressRepository repo) {
        this.playerProgressRepository = repo;
    }

    public void setHiveRepository(@Nullable HiveRepository repo) {
        this.hiveRepository = repo;
    }

    public void setHexParcelRepository(@Nullable HexParcelRepository repo) {
        this.hexParcelRepository = repo;
    }

    @Nullable
    public PollinationContractEntity getOpenForOwnerSync(String ownerId) {
        if (ownerId == null || ownerId.isEmpty()) {
            return null;
        }
        return dao.getOpenForOwnerSync(ownerId);
    }

    public List<PollinationContractEntity> getOpenListForOwnerSync(String ownerId) {
        if (ownerId == null || ownerId.isEmpty()) {
            return Collections.emptyList();
        }
        List<PollinationContractEntity> rows = dao.getOpenListForOwnerSync(ownerId);
        return rows != null ? rows : Collections.emptyList();
    }

    @Nullable
    public PollinationContractEntity getOpenForOwnerAndHexSync(String ownerId, String hexId) {
        if (ownerId == null || ownerId.isEmpty() || hexId == null || hexId.isEmpty()) {
            return null;
        }
        return dao.getOpenForOwnerAndHexSync(ownerId, hexId);
    }

    public void getOpenListForOwner(String ownerId, Consumer<List<PollinationContractEntity>> onMain) {
        ioExecutor.execute(() -> {
            List<PollinationContractEntity> rows = getOpenListForOwnerSync(ownerId);
            mainHandler.post(() -> onMain.accept(rows));
        });
    }

    public void listOffers(
            String ownerId,
            int playerLevel,
            Consumer<List<Offer>> onMain) {
        listOffers(ownerId, playerLevel, null, onMain);
    }

    public void listOffers(
            String ownerId,
            int playerLevel,
            @Nullable PlayableMapRegion region,
            Consumer<List<Offer>> onMain) {
        ioExecutor.execute(() -> {
            List<Offer> offers = listOffersBlocking(ownerId, playerLevel, region);
            mainHandler.post(() -> onMain.accept(offers));
        });
    }

    public List<Offer> listOffersBlocking(String ownerId, int playerLevel) {
        return listOffersBlocking(ownerId, playerLevel, null);
    }

    public List<Offer> listOffersBlocking(String ownerId, int playerLevel,
            @Nullable PlayableMapRegion requestedRegion) {
        PlayableMapRegion region = requestedRegion != null
                ? requestedRegion
                : MapRegionPrefs.get(appContext);
        if (region == PlayableMapRegion.SOUTH_AFRICA && !ClimateUnlock.canAccessSouthAfrica(playerLevel)) {
            region = PlayableMapRegion.IBERIA;
        }
        PollinationOfferStore.ensureRegionNow(appContext, region, playerLevel);
        String cacheKey = (ownerId != null ? ownerId : "") + "|" + playerLevel + "|" + region.prefsValue();
        long now = System.currentTimeMillis();
        List<HiveEntity> hives = ownerId == null ? Collections.emptyList()
                : hiveDao.getHivesByOwnerSync(ownerId);
        String pendingFp = pendingMovesFingerprint(hives);
        List<Offer> hit = cachedOffers;
        if (hit != null && cacheKey.equals(cachedOfferKey) && pendingFp.equals(cachedPendingFp)
                && now - cachedOfferAtMs < OFFER_CACHE_MS) {
            return hit;
        }
        Set<String> originHexes = new HashSet<>();
        Set<String> landHexes = new HashSet<>();
        if (hives != null) {
            for (HiveEntity h : hives) {
                if (h == null || h.inWarehouse) {
                    continue;
                }
                if (h.hexId != null && !h.hexId.isEmpty()) {
                    originHexes.add(h.hexId);
                }
                if (h.contractOriginHexId != null && !h.contractOriginHexId.isEmpty()) {
                    originHexes.add(h.contractOriginHexId);
                }
            }
        }
        if (hexParcelRepository != null && ownerId != null) {
            for (String owned : hexParcelRepository.listOwnedHexIdsSync(ownerId)) {
                if (owned != null && !owned.isEmpty()) {
                    originHexes.add(owned);
                    landHexes.add(owned);
                }
            }
        }
        Set<String> rankHexes = hexesInRegion(!landHexes.isEmpty() ? landHexes : originHexes, region);
        if (rankHexes.isEmpty()) {
            rankHexes = !landHexes.isEmpty() ? landHexes : originHexes;
        }
        List<PollinationContractEntity> mineOpen = ownerId != null
                ? getOpenListForOwnerSync(ownerId) : Collections.emptyList();
        Map<String, Integer> activeByHex = fetchActiveCountByHexBlocking();
        LocalDate today = LocalDate.now(GameCalendar.userTimeZone());
        Double originLat = null;
        Double originLng = null;
        for (String hexId : rankHexes) {
            HexParcel origin = IberiaHexOverlayStore.findById(appContext, hexId);
            if (origin != null) {
                originLat = origin.centroidLat;
                originLng = origin.centroidLon;
                break;
            }
        }
        if (originLat == null) {
            originLat = region.box().centerLat();
            originLng = region.box().centerLon();
        }
        List<PollinationOfferEntity> pooled = PollinationOfferStore.openForPlayer(
                appContext, region, playerLevel);
        List<NpcContractFarm> farms = new ArrayList<>();
        for (int i = 0; i < pooled.size(); i++) {
            PollinationOfferEntity row = pooled.get(i);
            if (row == null || row.hexId == null) {
                continue;
            }
            HexParcel dest = IberiaHexOverlayStore.findById(appContext, row.hexId);
            NpcContractFarm farm = null;
            if (row.startDoy > 0 && PollinationContractCrops.startedBeforeToday(
                    row.startDoy, row.endDoy, row.createdDayKey, today)) {
                continue;
            }
            if (row.startDoy > 0 && row.endDoy > 0) {
                farm = NpcContractCatalog.farmForPublished(dest, row.flora, row.startDoy, row.endDoy);
            } else if (row.startDoy > 0) {
                farm = NpcContractCatalog.farmForSlot(dest, row.flora, row.startDoy);
            }
            if (farm == null && !GameServer.enabled()) {
                farm = NpcContractCatalog.farmStartingAfter(dest, row.flora, today);
            }
            if (farm != null) {
                farms.add(farm);
            }
        }
        if (farms.isEmpty() && !GameServer.enabled()) {
            farms.addAll(PollinationOfferCatalog.spawnOpen(appContext, region, today, playerLevel));
        }
        Map<String, int[]> bucketStats = new HashMap<>();
        for (int i = 0; i < farms.size(); i++) {
            NpcContractFarm farm = farms.get(i);
            if (farm == null || farm.parcel == null) {
                continue;
            }
            String bucket = NpcContractCatalog.occupancyBucket(farm.parcel, farm.flora);
            int[] stats = bucketStats.get(bucket);
            if (stats == null) {
                stats = new int[]{0, 0};
                bucketStats.put(bucket, stats);
            }
            stats[0] += activeByHex.getOrDefault(farm.hexId(), 0);
            stats[1] += 1;
        }
        for (int i = 0; i < mineOpen.size(); i++) {
            PollinationContractEntity mine = mineOpen.get(i);
            if (mine == null || mine.hexId == null) {
                continue;
            }
            if (PlayableMapRegion.fromHexId(mine.hexId) != region) {
                continue;
            }
            HexParcel dest = IberiaHexOverlayStore.findById(appContext, mine.hexId);
            NpcContractFarm mineFarm = NpcContractCatalog.farmForSlot(dest, mine.flora, mine.startDoy);
            if (mineFarm == null) {
                mineFarm = NpcContractCatalog.nextUpcoming(
                        NpcContractCatalog.fieldOffersFor(dest, today), today);
            }
            if (mineFarm != null) {
                farms.add(mineFarm);
            }
        }

        List<Offer> scored = new ArrayList<>();
        for (int i = 0; i < farms.size(); i++) {
            Offer offer = buildOffer(farms.get(i), originHexes, rankHexes, mineOpen, activeByHex,
                    bucketStats, false, today, hives);
            if (offer == null) {
                continue;
            }
            if (!offer.mine) {
                if (offer.farm.parcel != null && !ClimateUnlock.canBuyParcel(offer.farm.parcel, playerLevel)) {
                    continue;
                }
                if (!offer.worthTraveling()) {
                    continue;
                }
            }
            scored.add(offer);
        }
        scored.sort(Comparator
                .comparingInt((Offer o) -> o.mine ? 0 : 1)
                .thenComparingInt(o -> daysUntilOffer(o, today))
                .thenComparing(o -> o.farm.flora, Comparator.nullsLast(String::compareTo))
                .thenComparingDouble(o -> o.distanceKm));
        List<Offer> limited = new ArrayList<>();
        int cap = Math.max(GameBalanceConfig.pollinationMaxOffers, PollinationOfferCatalog.VISIBLE_NEAREST);
        int added = 0;
        for (int i = 0; i < scored.size(); i++) {
            Offer o = scored.get(i);
            if (o.mine) {
                limited.add(o);
                continue;
            }
            if (added >= cap) {
                continue;
            }
            limited.add(o);
            added++;
        }
        cachedOffers = limited;
        cachedOfferKey = cacheKey;
        cachedPendingFp = pendingFp;
        cachedOfferAtMs = now;
        return limited;
    }

    public void offerForHex(
            String ownerId,
            String hexId,
            int playerLevel,
            Consumer<Offer> onMain) {
        ioExecutor.execute(() -> {
            Offer offer = offerForHexBlocking(ownerId, hexId, playerLevel);
            mainHandler.post(() -> onMain.accept(offer));
        });
    }

    @Nullable
    public Offer offerForHexBlocking(String ownerId, String hexId, int playerLevel) {
        HexParcel dest = IberiaHexOverlayStore.findById(appContext, hexId);
        if (dest == null) {
            return null;
        }
        List<HiveEntity> hives = ownerId == null ? Collections.emptyList()
                : hiveDao.getHivesByOwnerSync(ownerId);
        Set<String> originHexes = new HashSet<>();
        if (hives != null) {
            for (HiveEntity h : hives) {
                if (h == null) {
                    continue;
                }
                if (h.hexId != null && !h.hexId.isEmpty()) {
                    originHexes.add(h.hexId);
                }
                if (h.contractOriginHexId != null && !h.contractOriginHexId.isEmpty()) {
                    originHexes.add(h.contractOriginHexId);
                }
            }
        }
        PlayableMapRegion region = PlayableMapRegion.fromHexId(hexId);
        if (region == PlayableMapRegion.SOUTH_AFRICA && !ClimateUnlock.canAccessSouthAfrica(playerLevel)) {
            region = PlayableMapRegion.IBERIA;
        }
        List<HexParcel> parcels = IberiaHexOverlayStore.getParcels(appContext, region);
        List<PollinationContractEntity> mineOpen = ownerId != null
                ? getOpenListForOwnerSync(ownerId) : Collections.emptyList();
        Map<String, Integer> activeByHex = fetchActiveCountByHexBlocking();
        Map<String, int[]> bucketStats = computeBucketStats(parcels, activeByHex);
        NpcContractFarm pick = null;
        PollinationContractEntity mineHereRow = mineOnHex(mineOpen, dest.id);
        if (mineHereRow != null) {
            pick = NpcContractCatalog.farmFor(dest, mineHereRow.flora, mineHereRow.startDoy,
                    LocalDate.now(GameCalendar.userTimeZone()));
        }
        if (pick == null && GameServer.enabled()) {
            OfferBand serverBand = OfferBand.ofLevel(playerLevel);
            List<PollinationOfferEntity> serverRows = PollinationOfferStore.openForPlayer(
                    appContext, region, playerLevel);
            for (PollinationOfferEntity row : serverRows) {
                if (row == null || !hexId.equals(row.hexId) || row.band != serverBand.index) {
                    continue;
                }
                pick = row.endDoy > 0
                        ? NpcContractCatalog.farmForPublished(dest, row.flora, row.startDoy, row.endDoy)
                        : NpcContractCatalog.farmForSlot(dest, row.flora, row.startDoy);
                if (pick != null) {
                    break;
                }
            }
        }
        if (pick == null && !GameServer.enabled()) {
            pick = NpcContractCatalog.nextUpcoming(
                    NpcContractCatalog.fieldOffersFor(dest, LocalDate.now(GameCalendar.userTimeZone())),
                    LocalDate.now(GameCalendar.userTimeZone()));
            if (pick != null && !PollinationContractCrops.isOfferedOnParcel(pick.flora, dest)) {
                pick = null;
            }
            if (pick != null && playerLevel < 2) {
                pick = null;
            }
            if (pick != null
                    && PollinationContractCrops.daysUntilWork(pick,
                    LocalDate.now(GameCalendar.userTimeZone())) > PollinationContractCrops.MAX_AHEAD_DAYS) {
                pick = null;
            }
            if (pick != null && dest != null && !ClimateUnlock.canBuyParcel(dest, playerLevel)) {
                pick = null;
            }
        }
        return buildOffer(pick, originHexes, originHexes, mineOpen, activeByHex, bucketStats, true,
                LocalDate.now(GameCalendar.userTimeZone()), hives);
    }

    @Nullable
    private Offer buildOffer(
            NpcContractFarm farm,
            Set<String> originHexes,
            Set<String> rankHexes,
            @Nullable List<PollinationContractEntity> mineOpen,
            Map<String, Integer> activeByHex,
            Map<String, int[]> bucketStats,
            boolean includeHiddenReserve,
            @Nullable LocalDate today,
            @Nullable List<HiveEntity> ownerHives) {
        if (farm == null || farm.parcel == null) {
            return null;
        }
        HexParcel parcel = farm.parcel;
        Set<String> rank = rankHexes != null && !rankHexes.isEmpty() ? rankHexes : originHexes;
        int dist = nearestDistance(rank, parcel.id);
        double travelKm = nearestKm(originHexes, parcel);
        int travel = PollinationContractRules.travelCostPerHiveKm(travelKm);
        PollinationContractEntity mine = mineOnHex(mineOpen, parcel.id);
        boolean sameHexFlora = mine != null
                && HexFlora.canonicalKey(farm.flora).equals(HexFlora.canonicalKey(mine.flora));
        boolean mineHere = false;
        LocalDate day = today != null ? today : LocalDate.now(GameCalendar.userTimeZone());
        if (sameHexFlora) {
            int slotStart = farm.terms != null ? farm.terms.startDoy : 0;
            if (mine.startDoy > 0 && slotStart > 0) {
                mineHere = mine.startDoy == slotStart;
            } else if (farm.terms != null) {
                int doy = Math.min(365, day.getDayOfYear());
                mineHere = FloraBloomWindow.containsDoy(
                        new FloraBloomWindow.Span(farm.terms.startDoy, farm.terms.endDoy), doy);
            } else {
                mineHere = true;
            }
        }
        if (mine != null && !mineHere && mine.startDoy > 0) {
            return null;
        }
        String bucket = NpcContractCatalog.occupancyBucket(parcel, farm.flora);
        int[] stats = bucketStats.get(bucket);
        int activeBucket = stats != null ? stats[0] : 0;
        int openFarms = stats != null ? stats[1] : 1;
        double occ = PollinationContractRules.occupancy(activeBucket, Math.max(1, openFarms));
        boolean reservesOpen = PollinationContractRules.reservesOpen(occ);
        int layers = PollinationContractRules.maxLayers(occ, reservesOpen);
        int taken = activeByHex.getOrDefault(parcel.id, 0);
        boolean occupied = !mineHere && taken >= layers;
        String window;
        if (farm.terms != null && farm.terms.startDoy > 0 && farm.terms.endDoy > 0) {
            List<FloraBloomWindow.Span> one = new ArrayList<>();
            one.add(new FloraBloomWindow.Span(farm.terms.startDoy, farm.terms.endDoy));
            window = FloraBloomWindow.formatUpcomingEs(one, day);
        } else {
            window = FloraBloomWindow.formatEsForParcel(farm.flora, parcel);
        }
        double collected = mineHere ? mine.collectedKg : 0.0;
        double pool = mineHere ? mine.poolKg : 0.0;
        List<HiveEntity> pending = pendingHivesForOffer(mine, mineHere, parcel.id, ownerHives);
        boolean noHives = true;
        if (ownerHives != null) {
            for (int i = 0; i < ownerHives.size(); i++) {
                HiveEntity hive = ownerHives.get(i);
                if (hive != null && !hive.inWarehouse) {
                    noHives = false;
                    break;
                }
            }
        }
        return new Offer(farm, dist, travel, occupied, mineHere, false, false, window,
                collected, pool, pending, travelKm, noHives);
    }

    @Nullable
    private static PollinationContractEntity mineOnHex(
            @Nullable List<PollinationContractEntity> mineOpen, @Nullable String hexId) {
        if (mineOpen == null || hexId == null || hexId.isEmpty()) {
            return null;
        }
        for (int i = 0; i < mineOpen.size(); i++) {
            PollinationContractEntity row = mineOpen.get(i);
            if (row != null && hexId.equals(row.hexId)) {
                return row;
            }
        }
        return null;
    }

    public void accept(
            String ownerId,
            String hexId,
            String flora,
            int startDoy,
            List<String> hiveIds,
            int playerLevel,
            Consumer<String> onMainMessage) {
        accept(ownerId, hexId, flora, startDoy, hiveIds, null, playerLevel, onMainMessage);
    }

    public void accept(
            String ownerId,
            String hexId,
            String flora,
            int startDoy,
            List<String> hiveIds,
            @Nullable List<HiveOrder> orders,
            int playerLevel,
            Consumer<String> onMainMessage) {
        ioExecutor.execute(() -> {
            try {
                String err = acceptBlocking(ownerId, hexId, flora, startDoy, hiveIds, orders, playerLevel);
                mainHandler.post(() -> onMainMessage.accept(err));
            } catch (Exception e) {
                Log.e(TAG, "accept", e);
                mainHandler.post(() -> onMainMessage.accept(
                        e.getMessage() != null ? e.getMessage() : "No se pudo aceptar el contrato."));
            }
        });
    }

    public void accept(
            String ownerId,
            String hexId,
            String flora,
            List<String> hiveIds,
            int playerLevel,
            Consumer<String> onMainMessage) {
        accept(ownerId, hexId, flora, 0, hiveIds, null, playerLevel, onMainMessage);
    }

    @Nullable
    public String acceptBlocking(String ownerId, String hexId, String flora, List<String> hiveIds, int playerLevel)
            throws Exception {
        return acceptBlocking(ownerId, hexId, flora, 0, hiveIds, null, playerLevel);
    }

    @Nullable
    public String acceptBlocking(String ownerId, String hexId, String flora, int startDoy, List<String> hiveIds,
            int playerLevel)
            throws Exception {
        return acceptBlocking(ownerId, hexId, flora, startDoy, hiveIds, null, playerLevel);
    }

    @Nullable
    private NpcContractFarm farmForAccept(@Nullable HexParcel dest, @Nullable String flora, int startDoy,
            int playerLevel) {
        if (dest == null) {
            return null;
        }
        if (startDoy > 0 && GameServer.enabled()) {
            PlayableMapRegion region = PlayableMapRegion.fromHexId(dest.id);
            if (region != null) {
                List<PollinationOfferEntity> rows = PollinationOfferStore.openForPlayer(
                        appContext, region, playerLevel);
                if (rows != null) {
                    for (int i = 0; i < rows.size(); i++) {
                        PollinationOfferEntity row = rows.get(i);
                        if (row == null || !dest.id.equals(row.hexId) || row.endDoy <= 0) {
                            continue;
                        }
                        if (flora != null && !HexFlora.canonicalKey(flora)
                                .equals(HexFlora.canonicalKey(row.flora))) {
                            continue;
                        }
                        if (row.startDoy != startDoy) {
                            continue;
                        }
                        return NpcContractCatalog.farmForPublished(dest, row.flora, row.startDoy, row.endDoy);
                    }
                }
            }
        }
        if (startDoy > 0) {
            return NpcContractCatalog.farmForSlot(dest, flora, startDoy);
        }
        return NpcContractCatalog.farmFor(dest, flora, 0, LocalDate.now(GameCalendar.userTimeZone()));
    }

    @Nullable
    public String acceptBlocking(String ownerId, String hexId, String flora, int startDoy, List<String> hiveIds,
            @Nullable List<HiveOrder> orders, int playerLevel)
            throws Exception {
        if (ownerId == null || ownerId.isEmpty()) {
            return "Sesión no válida.";
        }
        int buyCount = HiveOrder.hiveCount(orders);
        int buyCost = HiveOrder.totalB(orders);
        boolean hasMove = hiveIds != null && !hiveIds.isEmpty();
        HexParcel dest = IberiaHexOverlayStore.findById(appContext, hexId);
        NpcContractFarm farm = farmForAccept(dest, flora, startDoy, playerLevel);
        if (farm == null) {
            return "Ese punto no ofrece ese tramo de contrato.";
        }
        if (playerLevel < 2 || OfferBand.ofLevel(playerLevel).pollinationCount <= 0) {
            return "Aún no hay campos de polinización para tu nivel.";
        }
        if (!CropUnlock.isUnlocked(farm.flora, playerLevel)) {
            int unlock = CropUnlock.requiredLevel(farm.flora);
            return unlock > 0
                    ? appContext.getString(R.string.market_contract_crop_locked, unlock)
                    : appContext.getString(R.string.market_contract_crop_locked_plain);
        }
        if (!GameServer.enabled() && !PollinationContractCrops.isOfferedOnParcel(farm.flora, dest)) {
            return "Ese campo no ofrece ese cultivo aquí.";
        }
        if (dest != null && !ClimateUnlock.canBuyParcel(dest, playerLevel)) {
            return "Ese clima aún no está desbloqueado.";
        }
        if (dao.getOpenForOwnerAndHexSync(ownerId, hexId) != null) {
            return "Ya tienes un contrato en este campo.";
        }
        Map<String, Integer> activeByHex = fetchActiveCountByHexBlocking();
        List<HexParcel> parcels = IberiaHexOverlayStore.getParcels(appContext,
                PlayableMapRegion.fromHexId(hexId));
        Map<String, int[]> buckets = computeBucketStats(parcels, activeByHex);
        String bucket = NpcContractCatalog.occupancyBucket(dest, farm.flora);
        int[] stats = buckets.get(bucket);
        double occ = PollinationContractRules.occupancy(
                stats != null ? stats[0] : 0, stats != null ? Math.max(1, stats[1]) : 1);
        boolean reservesOpen = PollinationContractRules.reservesOpen(occ);
        int layers = PollinationContractRules.maxLayers(occ, reservesOpen);
        int taken = activeByHex.getOrDefault(hexId, 0);
        if (taken >= layers) {
            return "Ese contrato ya está cubierto. Elige otro punto del mapa.";
        }
        int layer = taken;
        int serverBand = OfferBand.ofLevel(playerLevel).index;
        int serverStartDoy = startDoy > 0 ? startDoy
                : (farm.terms != null ? farm.terms.startDoy : 0);
        boolean serverOfferClaimed = false;
        if (GameServer.enabled()) {
            if (!GameServer.claimPollinationOffer(hexId, serverBand, farm.flora,
                    serverStartDoy, ownerId)) {
                return "Esta oferta ya no está disponible o el servidor no responde.";
            }
            serverOfferClaimed = true;
        }

        int todayKey = GameCalendar.toDayKey(LocalDate.now(GameCalendar.userTimeZone()));
        MovingGroup moving = hasMove
                ? collectMovingHives(ownerId, hexId, hiveIds, todayKey)
                : new MovingGroup();
        if (moving.error != null) {
            if (serverOfferClaimed) {
                releaseServerOfferClaim(hexId, serverBand, farm.flora, serverStartDoy, ownerId);
            }
            return moving.error;
        }
        int ownOnDest = hiveDao.countByHexIdAndOwner(hexId, ownerId)
                + countPendingToHex(ownerId, hexId);
        if (ownOnDest + moving.hives.size() + buyCount > HexParcelGameRules.MAX_HIVES_PER_SITE) {
            if (serverOfferClaimed) {
                releaseServerOfferClaim(hexId, serverBand, farm.flora, serverStartDoy, ownerId);
            }
            return "No caben tantas colmenas en esa finca (máx. "
                    + HexParcelGameRules.MAX_HIVES_PER_SITE + ").";
        }
        int travel = hasMove && dest != null
                ? PollinationContractRules.travelCostForHives(moving.hives, dest.centroidLat, dest.centroidLon)
                : 0;
        int cashOut = travel + buyCost;
        String contractConcept = "Contrato de polinización"
                + (farm.flora == null ? "" : " de " + farm.flora)
                + (travel > 0 && buyCost > 0 ? ": viaje y colmenas"
                : travel > 0 ? ": viaje" : ": compra de colmenas");
        if (cashOut > 0 && !economyRepository.trySpend(cashOut, contractConcept)) {
            if (serverOfferClaimed) {
                releaseServerOfferClaim(hexId, serverBand, farm.flora, serverStartDoy, ownerId);
            }
            return "Saldo insuficiente (" + cashOut + " B) para viaje y compra.";
        }
        if (buyCount > 0 && hiveRepository == null) {
            if (cashOut > 0) {
                economyRepository.addToBalance(cashOut, "Devolución del contrato de polinización");
            }
            if (serverOfferClaimed) {
                releaseServerOfferClaim(hexId, serverBand, farm.flora, serverStartDoy, ownerId);
            }
            return "No se pueden comprar colmenas ahora.";
        }
        String contractId = UUID.randomUUID().toString();
        PollinationPayTerms terms = farm.terms;
        PollinationContractEntity row = new PollinationContractEntity();
        row.id = contractId;
        row.ownerId = ownerId;
        row.hexId = hexId;
        row.flora = farm.flora;
        row.npcName = farm.npcName;
        row.estateName = farm.estateName;
        row.region = PlayableMapRegion.fromHexId(hexId).prefsValue();
        row.climateZone = NpcContractCatalog.climateKey(dest);
        row.layer = layer;
        row.status = PollinationContractRules.STATUS_ACTIVE;
        row.minPct = terms.minPct;
        row.payB = terms.payB;
        row.extraBPerPoint = terms.extraBPerPoint;
        int days = terms.workDays > 0 ? terms.workDays : PollinationContractRules.workDays(dest, farm.flora);
        row.poolKg = PollinationContractRules.targetPoolKg(farm.flora, days);
        row.collectedKg = 0.0;
        row.travelCostPaid = travel;
        row.acceptedDayKey = todayKey;
        row.workDays = days;
        row.startDoy = startDoy > 0 ? startDoy : terms.startDoy;
        LocalDate today = LocalDate.now(GameCalendar.userTimeZone());
        LocalDate startDate = row.startDoy > 0
                ? FloraBloomWindow.nextDateOfDoy(today, row.startDoy)
                : today;
        LocalDate endDate = terms.endDoy > 0
                ? FloraBloomWindow.nextDateOfDoy(startDate, terms.endDoy)
                : startDate.plusDays(Math.max(0, days - 1));
        row.dueDayKey = GameCalendar.toDayKey(endDate) + 1;
        StringBuilder ids = new StringBuilder();
        try {
            int moveKey = contractMoveDayKey(row, todayKey);
            scheduleHives(moving.hives, hexId, moveKey);
            for (HiveEntity h : moving.hives) {
                if (ids.length() > 0) {
                    ids.append(',');
                }
                ids.append(h.id);
            }
            appendSpawnedOrders(ids, ownerId, dest, farm.flora, orders, contractId, todayKey);
            row.hiveIdsJson = ids.toString();
            dao.upsert(row);
            persistContractCloud(row);
        } catch (Exception e) {
            if (cashOut > 0) {
                economyRepository.addToBalance(cashOut, "Devolución del contrato de polinización");
            }
            if (serverOfferClaimed) {
                releaseServerOfferClaim(hexId, serverBand, farm.flora, serverStartDoy, ownerId);
            }
            throw e;
        }
        invalidateOfferCache();
        if (!GameServer.enabled()) {
            PollinationOfferStore.takeAndReplenish(appContext, hexId);
        }
        return null;
    }

    public void addHives(String ownerId, String destHexId, List<String> hiveIds, Consumer<String> onMainMessage) {
        addHives(ownerId, destHexId, hiveIds, null, onMainMessage);
    }

    public void addHives(String ownerId, String destHexId, List<String> hiveIds,
            @Nullable List<HiveOrder> orders, Consumer<String> onMainMessage) {
        ioExecutor.execute(() -> {
            try {
                String err = addHivesBlocking(ownerId, destHexId, hiveIds, orders);
                mainHandler.post(() -> onMainMessage.accept(err));
            } catch (Exception e) {
                Log.e(TAG, "addHives", e);
                mainHandler.post(() -> onMainMessage.accept(
                        e.getMessage() != null ? e.getMessage() : "No se pudieron añadir las colmenas."));
            }
        });
    }

    public void getOpenForOwner(String ownerId, Consumer<PollinationContractEntity> onMain) {
        ioExecutor.execute(() -> {
            PollinationContractEntity row = getOpenForOwnerSync(ownerId);
            mainHandler.post(() -> onMain.accept(row));
        });
    }

    @Nullable
    public String addHivesBlocking(String ownerId, String destHexId, List<String> hiveIds) throws Exception {
        return addHivesBlocking(ownerId, destHexId, hiveIds, null);
    }

    @Nullable
    public String addHivesBlocking(String ownerId, String destHexId, List<String> hiveIds,
            @Nullable List<HiveOrder> orders) throws Exception {
        if (ownerId == null || ownerId.isEmpty()) {
            return "Sesión no válida.";
        }
        int buyCount = HiveOrder.hiveCount(orders);
        int buyCost = HiveOrder.totalB(orders);
        boolean hasMove = hiveIds != null && !hiveIds.isEmpty();
        if (!hasMove && buyCount <= 0) {
            return "Elige colmenas o cómpralas para esta finca.";
        }
        PollinationContractEntity row = dao.getOpenForOwnerAndHexSync(ownerId, destHexId);
        if (row == null || !PollinationContractRules.STATUS_ACTIVE.equals(row.status)) {
            return "No tienes un contrato en curso en esa finca.";
        }
        int todayKey = GameCalendar.toDayKey(LocalDate.now(GameCalendar.userTimeZone()));
        if (!PollinationContractRules.canAddHives(row.acceptedDayKey, todayKey, row.status)) {
            return "Podrás añadir más colmenas a partir de mañana.";
        }
        HexParcel dest = IberiaHexOverlayStore.findById(appContext, destHexId);
        if (dest == null) {
            return "Esa finca no está disponible.";
        }
        MovingGroup moving = hasMove
                ? collectMovingHives(ownerId, destHexId, hiveIds, todayKey)
                : new MovingGroup();
        if (moving.error != null) {
            return moving.error;
        }
        int ownOnDest = hiveDao.countByHexIdAndOwner(destHexId, ownerId)
                + countPendingToHex(ownerId, destHexId);
        if (ownOnDest + moving.hives.size() + buyCount > HexParcelGameRules.MAX_HIVES_PER_SITE) {
            return "No caben tantas colmenas en esa finca (máx. "
                    + HexParcelGameRules.MAX_HIVES_PER_SITE + ").";
        }
        int travel = PollinationContractRules.travelCostForHives(
                moving.hives, dest.centroidLat, dest.centroidLon);
        int cashOut = travel + buyCost;
        String contractConcept = "Contrato de polinización"
                + (row.flora == null ? "" : " de " + row.flora)
                + (travel > 0 && buyCost > 0 ? ": viaje y colmenas"
                : travel > 0 ? ": viaje" : ": compra de colmenas");
        if (cashOut > 0 && !economyRepository.trySpend(cashOut, contractConcept)) {
            return "Saldo insuficiente (" + cashOut + " B) para viaje y compra.";
        }
        if (buyCount > 0 && hiveRepository == null) {
            if (cashOut > 0) {
                economyRepository.addToBalance(cashOut, "Devolución del contrato de polinización");
            }
            return "No se pueden comprar colmenas ahora.";
        }
        try {
            int moveKey = contractMoveDayKey(row, todayKey);
            scheduleHives(moving.hives, destHexId, moveKey);
            StringBuilder ids = new StringBuilder(row.hiveIdsJson != null ? row.hiveIdsJson : "");
            for (HiveEntity h : moving.hives) {
                if (ids.length() > 0) {
                    ids.append(',');
                }
                ids.append(h.id);
            }
            appendSpawnedOrders(ids, ownerId, dest, row.flora, orders, row.id, todayKey);
            row.hiveIdsJson = ids.toString();
            row.travelCostPaid += travel;
            dao.upsert(row);
            persistContractCloud(row);
        } catch (Exception e) {
            if (cashOut > 0) {
                economyRepository.addToBalance(cashOut, "Devolución del contrato de polinización");
            }
            throw e;
        }
        invalidateOfferCache();
        return null;
    }

    public void cancelPendingHive(String ownerId, String hiveId, Consumer<String> onMainMessage) {
        ioExecutor.execute(() -> {
            try {
                String err = cancelPendingHiveBlocking(ownerId, hiveId);
                mainHandler.post(() -> onMainMessage.accept(err));
            } catch (Exception e) {
                Log.e(TAG, "cancelPendingHive", e);
                mainHandler.post(() -> onMainMessage.accept(
                        e.getMessage() != null ? e.getMessage() : "No se pudo cancelar el viaje."));
            }
        });
    }

    private int refundTravel(@Nullable HiveEntity hive, @Nullable String destHexId) {
        if (hive == null || destHexId == null || destHexId.isEmpty()) {
            return 0;
        }
        HexParcel dest = IberiaHexOverlayStore.findById(appContext, destHexId);
        if (dest == null) {
            return 0;
        }
        int refund = PollinationContractRules.travelCostOne(hive, dest.centroidLat, dest.centroidLon);
        return refund >= 99_999 ? 0 : refund;
    }

    @Nullable
    public String cancelPendingHiveBlocking(String ownerId, String hiveId) throws Exception {
        if (ownerId == null || ownerId.isEmpty() || hiveId == null || hiveId.isEmpty()) {
            return "Sesión no válida.";
        }
        HiveEntity h = hiveDao.getHiveByIdSync(hiveId);
        if (h == null || h.ownerId == null || !h.ownerId.equals(ownerId)) {
            return "Colmena no válida.";
        }
        if (!TranshumanceRules.hasPendingContractMove(h)) {
            return "Esa colmena no tiene un viaje programado.";
        }
        String destHexId = h.pendingContractHexId;
        int refund = refundTravel(h, destHexId);
        PollinationContractEntity row = dao.getOpenForOwnerAndHexSync(ownerId, destHexId);
        clearPendingMove(h);
        if (row != null) {
            row.hiveIdsJson = removeHiveId(row.hiveIdsJson, hiveId);
            row.travelCostPaid = Math.max(0, row.travelCostPaid - refund);
            dao.upsert(row);
            persistContractCloud(row);
        }
        if (refund > 0) {
            economyRepository.addToBalance(refund, "Devolución del viaje al contrato");
        }
        invalidateOfferCache();
        return null;
    }

    static String removeHiveId(@Nullable String hiveIdsJson, @Nullable String hiveId) {
        if (hiveId == null || hiveId.isEmpty()) {
            return hiveIdsJson != null ? hiveIdsJson : "";
        }
        if (hiveIdsJson == null || hiveIdsJson.isEmpty()) {
            return "";
        }
        String[] parts = hiveIdsJson.split(",");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            String id = parts[i] != null ? parts[i].trim() : "";
            if (id.isEmpty() || id.equals(hiveId)) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(id);
        }
        return sb.toString();
    }

    private static final class MovingGroup {
        final List<HiveEntity> hives = new ArrayList<>();
        String originHex;
        String error;
    }

    private MovingGroup collectMovingHives(String ownerId, String destHexId, List<String> hiveIds, int todayKey) {
        MovingGroup out = new MovingGroup();
        for (String hiveId : hiveIds) {
            HiveEntity h = hiveDao.getHiveByIdSync(hiveId);
            if (h == null || h.ownerId == null || !h.ownerId.equals(ownerId)) {
                out.error = "Colmena no válida.";
                return out;
            }
            if (h.inWarehouse) {
                out.error = "Esa colmena está en el obrador.";
                return out;
            }
            if (TranshumanceRules.hasPendingContractMove(h)) {
                out.error = "Hay colmenas con transhumancia ya programada.";
                return out;
            }
            if (TruckLiveTrips.hasActive(appContext, h.id)) {
                out.error = "Esa colmena aún está de camino.";
                return out;
            }
            if (h.linkedToContract()) {
                out.error = "Esa colmena ya está en un contrato.";
                return out;
            }
            if (h.hexId == null || h.hexId.isEmpty()) {
                out.error = "La colmena no tiene terreno de origen.";
                return out;
            }
            if (h.hexId.equals(destHexId)) {
                out.error = "Ya estás en esa finca.";
                return out;
            }
            if (out.originHex == null) {
                out.originHex = h.hexId;
            }
            out.hives.add(h);
        }
        if (out.originHex == null) {
            out.error = "La colmena no tiene terreno de origen.";
            return out;
        }
        if (out.originHex.equals(destHexId)) {
            out.error = "Ya estás en esa finca.";
            return out;
        }
        return out;
    }

    private int countPendingToHex(String ownerId, String destHexId) {
        if (ownerId == null || destHexId == null) {
            return 0;
        }
        List<HiveEntity> hives = hiveDao.getHivesByOwnerSync(ownerId);
        if (hives == null) {
            return 0;
        }
        int n = 0;
        for (HiveEntity h : hives) {
            if (h != null && destHexId.equals(h.pendingContractHexId)) {
                n++;
            }
        }
        return n;
    }

    private static int contractMoveDayKey(PollinationContractEntity row, int todayKey) {
        if (row == null || row.startDoy <= 0) {
            return todayKey;
        }
        LocalDate today = GameCalendar.fromDayKey(todayKey);
        if (today == null) {
            today = LocalDate.now(GameCalendar.userTimeZone());
        }
        int startKey = GameCalendar.toDayKey(FloraBloomWindow.nextDateOfDoy(today, row.startDoy));
        return Math.max(todayKey, startKey);
    }

    private void scheduleHives(List<HiveEntity> moving, String destHexId, int moveDayKey) throws Exception {
        if (moving == null) {
            return;
        }
        for (HiveEntity h : moving) {
            h.pendingContractHexId = destHexId;
            h.pendingContractDayKey = moveDayKey;
            hiveDao.upsert(h);
            persistHiveCloud(h);
        }
    }

    /**
     * Ejecuta las transhumancias a contrato programadas para este día (cálculo diario).
     */
    public List<String> applyScheduledContractMovesBlocking(String ownerId, int dayKey) {
        List<String> notes = new ArrayList<>();
        if (ownerId == null || ownerId.isEmpty()) {
            return notes;
        }
        List<HiveEntity> hives = hiveDao.getHivesByOwnerSync(ownerId);
        if (hives == null || hives.isEmpty()) {
            return notes;
        }
        Map<String, List<HiveEntity>> byDest = new LinkedHashMap<>();
        for (HiveEntity h : hives) {
            if (h == null || !TranshumanceRules.hasPendingContractMove(h)) {
                continue;
            }
            if (h.pendingContractDayKey > dayKey) {
                continue;
            }
            String dest = h.pendingContractHexId;
            List<HiveEntity> group = byDest.get(dest);
            if (group == null) {
                group = new ArrayList<>();
                byDest.put(dest, group);
            }
            group.add(h);
        }
        int moved = 0;
        for (Map.Entry<String, List<HiveEntity>> e : byDest.entrySet()) {
            String destHexId = e.getKey();
            List<HiveEntity> group = e.getValue();
            PollinationContractEntity row = dao.getOpenForOwnerAndHexSync(ownerId, destHexId);
            HexParcel dest = IberiaHexOverlayStore.findById(appContext, destHexId);
            if (row == null || !PollinationContractRules.STATUS_ACTIVE.equals(row.status) || dest == null) {
                for (HiveEntity h : group) {
                    int refund = refundTravel(h, destHexId);
                    if (refund > 0 && refund < 99_999) {
                        economyRepository.addToBalance(refund, "Devolución del viaje al contrato");
                    }
                    clearPendingMove(h);
                }
                notes.add("Una transhumancia programada se canceló: el contrato ya no está activo.");
                continue;
            }
            try {
                dispatchHives(group, dest, row.flora, row.id, destHexId, dayKey);
                moved += group.size();
            } catch (Exception ex) {
                Log.e(TAG, "applyScheduledContractMoves", ex);
                notes.add("No se pudo completar una transhumancia programada.");
            }
        }
        if (moved > 0) {
            notes.add(moved == 1
                    ? "1 colmena ha llegado a la finca de contrato."
                    : moved + " colmenas han llegado a la finca de contrato.");
        }
        return notes;
    }

    private void clearPendingMove(HiveEntity h) {
        h.pendingContractHexId = null;
        h.pendingContractDayKey = 0;
        hiveDao.upsert(h);
        persistHiveCloud(h);
    }

    private void dispatchHives(List<HiveEntity> moving, HexParcel dest, String flora,
            String contractId, String hexId, int todayKey) throws Exception {
        Random rng = new Random();
        for (HiveEntity h : moving) {
            h.contractId = contractId;
            h.contractOriginHexId = h.hexId;
            h.contractOriginFlora = h.floraType;
            h.contractOriginLat = h.lat;
            h.contractOriginLng = h.lng;
            double[] ll = HexParcelRandomPoint.randomLatLonInside(dest, rng);
            TruckLiveTrips.StartResult trip = TruckLiveTrips.start(appContext, h, ll[0], ll[1], hexId);
            if (trip != TruckLiveTrips.StartResult.STARTED) {
                h.lat = ll[0];
                h.lng = ll[1];
                h.hexId = hexId;
                h.elevationMeters = OpenMeteoElevation.resolveMetersPersistedBlocking(h.lat, h.lng);
            }
            h.floraType = flora;
            h.transhumanceArrivesDayKey = 0;
            h.pendingContractHexId = null;
            h.pendingContractDayKey = 0;
            hiveDao.upsert(h);
            persistHiveCloud(h);
        }
    }

    private void appendSpawnedOrders(StringBuilder ids, String ownerId, HexParcel dest, String flora,
            @Nullable List<HiveOrder> orders, String contractId, int todayKey) throws Exception {
        if (orders == null || orders.isEmpty() || hiveRepository == null || dest == null) {
            return;
        }
        int seq = 1;
        for (HiveOrder order : orders) {
            if (order == null) {
                continue;
            }
            for (int i = 0; i < order.quantity; i++) {
                String name = "Núcleo " + seq;
                seq++;
                HiveEntity spawned = hiveRepository.spawnPaidContractHiveBlocking(
                        ownerId, dest, flora, name, order.superCount, contractId, todayKey);
                if (spawned != null && spawned.id != null) {
                    if (ids.length() > 0) {
                        ids.append(',');
                    }
                    ids.append(spawned.id);
                }
            }
        }
    }

    private void invalidateOfferCache() {
        cachedOffers = null;
        cachedOfferKey = null;
        cachedPendingFp = null;
        cachedOfferAtMs = 0L;
    }

    @NonNull
    private boolean isLiveTripTo(@Nullable HiveEntity h, @Nullable String destHexId) {
        if (h == null || destHexId == null || destHexId.isEmpty()) {
            return false;
        }
        TruckTripEntity trip = TruckLiveTrips.get(appContext, h.id);
        return trip != null && destHexId.equals(trip.destHexId);
    }

    private List<HiveEntity> pendingHivesForOffer(
            @Nullable PollinationContractEntity mine,
            boolean mineHere,
            @Nullable String destHexId,
            @Nullable List<HiveEntity> ownerHives) {
        List<HiveEntity> pending = new ArrayList<>();
        if (!mineHere || destHexId == null || destHexId.isEmpty() || ownerHives == null) {
            return pending;
        }
        Set<String> seen = new HashSet<>();
        for (HiveEntity h : ownerHives) {
            if (h == null || h.id == null || h.inWarehouse) {
                continue;
            }
            if (TranshumanceRules.hasPendingContractMove(h) && destHexId.equals(h.pendingContractHexId)) {
                pending.add(h);
                seen.add(h.id);
            } else if (isLiveTripTo(h, destHexId)) {
                pending.add(h);
                seen.add(h.id);
            }
        }
        Set<String> assigned = hiveIdSet(mine != null ? mine.hiveIdsJson : null);
        if (assigned.isEmpty()) {
            return pending;
        }
        Map<String, HiveEntity> byId = new HashMap<>();
        for (HiveEntity h : ownerHives) {
            if (h != null && h.id != null) {
                byId.put(h.id, h);
            }
        }
        for (String id : assigned) {
            if (id == null || id.isEmpty() || seen.contains(id)) {
                continue;
            }
            HiveEntity h = byId.get(id);
            if (h == null || h.inWarehouse) {
                continue;
            }
            if (TranshumanceRules.hasPendingContractMove(h) || isLiveTripTo(h, destHexId)) {
                pending.add(h);
                seen.add(id);
            }
        }
        return pending;
    }

    @NonNull
    private String pendingMovesFingerprint(@Nullable List<HiveEntity> hives) {
        if (hives == null || hives.isEmpty()) {
            return "";
        }
        List<String> bits = new ArrayList<>();
        for (HiveEntity h : hives) {
            if (h == null || h.id == null) {
                continue;
            }
            if (TranshumanceRules.hasPendingContractMove(h)) {
                bits.add(h.id + ":" + h.pendingContractHexId + ":" + h.pendingContractDayKey);
            }
            TruckTripEntity live = TruckLiveTrips.get(appContext, h.id);
            if (live != null) {
                bits.add(h.id + ":trip:" + live.destHexId + ":" + live.startEpochMs + ":" + live.durationMs);
            }
        }
        Collections.sort(bits);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bits.size(); i++) {
            if (i > 0) {
                sb.append('|');
            }
            sb.append(bits.get(i));
        }
        return sb.toString();
    }

    @NonNull
    static Set<String> hiveIdSet(@Nullable String hiveIdsJson) {
        Set<String> out = new HashSet<>();
        if (hiveIdsJson == null || hiveIdsJson.isEmpty()) {
            return out;
        }
        String[] parts = hiveIdsJson.split(",");
        for (int i = 0; i < parts.length; i++) {
            String id = parts[i] != null ? parts[i].trim() : "";
            if (!id.isEmpty()) {
                out.add(id);
            }
        }
        return out;
    }

    /**
     * Acumula néctar del día y cierra el convenio al bajar la floración importante.
     * @return notas para el resumen diario
     */
    public List<String> tickAfterForageBlocking(
            String ownerId,
            LocalDate day,
            int dayKey,
            Map<String, Double> collectedByHive) {
        List<String> notes = new ArrayList<>();
        if (ownerId == null || ownerId.isEmpty()) {
            return notes;
        }
        List<PollinationContractEntity> open = getOpenListForOwnerSync(ownerId);
        for (int i = 0; i < open.size(); i++) {
            notes.addAll(tickOneContractBlocking(open.get(i), day, dayKey, collectedByHive));
        }
        return notes;
    }

    private List<String> tickOneContractBlocking(
            PollinationContractEntity row,
            LocalDate day,
            int dayKey,
            Map<String, Double> collectedByHive) {
        List<String> notes = new ArrayList<>();
        if (row == null) {
            return notes;
        }
        List<HiveEntity> hives = hiveDao.getHivesByContractSync(row.id);
        if (PollinationContractRules.STATUS_RETURNING.equals(row.status)) {
            boolean allHome = true;
            for (HiveEntity h : hives) {
                if (h != null && (TruckLiveTrips.hasActive(appContext, h.id)
                        || TranshumanceRules.isInTransit(h, dayKey))) {
                    allHome = false;
                    break;
                }
            }
            if (allHome) {
                clearHiveContractFields(hives);
                row.status = PollinationContractRules.STATUS_SETTLED;
                dao.upsert(row);
                persistContractCloud(row);
            }
            return notes;
        }
        if (!PollinationContractRules.STATUS_ACTIVE.equals(row.status)) {
            return notes;
        }
        HexParcel parcel = IberiaHexOverlayStore.findById(appContext, row.hexId);
        double bloom = PollinationContractRules.bloom01(parcel, row.flora, day);
        boolean anyWorking = false;
        double collected = 0;
        for (HiveEntity h : hives) {
            if (h == null) {
                continue;
            }
            if (TruckLiveTrips.hasActive(appContext, h.id) || TranshumanceRules.isInTransit(h, dayKey)) {
                continue;
            }
            anyWorking = true;
            if (collectedByHive != null && h.id != null) {
                Double kg = collectedByHive.get(h.id);
                if (kg != null) {
                    collected += kg;
                }
            }
        }
        if (anyWorking) {
            row.collectedKg += Math.max(0.0, collected);
            if (bloom + 1e-9 >= PollinationContractRules.MIN_BLOOM01) {
                row.sawPeak = true;
            }
            dao.upsert(row);
            persistContractCloud(row);
        }
        if (PollinationContractRules.shouldClose(row.sawPeak, bloom, dayKey, row.dueDayKey)) {
            String settleNote = settleAndReturnBlocking(row, hives, dayKey);
            if (settleNote != null) {
                notes.add(settleNote);
            }
        }
        return notes;
    }

    public void removeAllForOwnerBlocking(String ownerId) {
        if (ownerId == null || ownerId.isEmpty()) {
            return;
        }
        List<PollinationContractEntity> rows = dao.getAllForOwnerSync(ownerId);
        dao.deleteAllForOwner(ownerId);
        if (firestore == null || rows == null) {
            return;
        }
        for (PollinationContractEntity row : rows) {
            if (row == null || row.id == null) {
                continue;
            }
            try {
                firestore.collection(COLLECTION).document(row.id).delete()
                        .addOnFailureListener(e -> Log.w(TAG, "removeAllForOwnerBlocking", e));
            } catch (Exception e) {
                Log.w(TAG, "removeAllForOwnerBlocking", e);
            }
        }
    }

    private String settleAndReturnBlocking(
            PollinationContractEntity row,
            List<HiveEntity> hives,
            int dayKey) {
        int pay = PollinationContractRules.payoutB(row.collectedKg, row.poolKg,
                new PollinationPayTerms(row.minPct, row.payB, row.extraBPerPoint));
        if (pay > 0) {
            String flora = row.flora == null ? "" : " de " + row.flora;
            economyRepository.addToBalance(pay, "Cobro del contrato" + flora);
        }
        Random rng = new Random();
        boolean anyReturning = false;
        for (HiveEntity h : hives) {
            if (h == null) {
                continue;
            }
            if (h.returnToWarehouse) {
                h.returnToWarehouse = false;
            }
            anyReturning = true;
            String originHex = h.contractOriginHexId != null ? h.contractOriginHexId : h.hexId;
            HexParcel origin = IberiaHexOverlayStore.findById(appContext, originHex);
            if (origin != null) {
                double[] ll = HexParcelRandomPoint.randomLatLonInside(origin, rng);
                snapOrStartTrip(h, ll[0], ll[1], origin.id);
            } else {
                snapOrStartTrip(h, h.contractOriginLat, h.contractOriginLng, originHex);
            }
            if (h.contractOriginFlora != null && !h.contractOriginFlora.isEmpty()) {
                h.floraType = h.contractOriginFlora;
            }
            h.transhumanceArrivesDayKey = 0;
            hiveDao.upsert(h);
            persistHiveCloud(h);
        }
        if (anyReturning) {
            row.status = PollinationContractRules.STATUS_RETURNING;
        } else {
            row.status = PollinationContractRules.STATUS_SETTLED;
        }
        dao.upsert(row);
        persistContractCloud(row);
        int pct = (int) Math.round(100.0 * PollinationContractRules.pollinationPct(row.collectedKg, row.poolKg));
        if (pay > 0) {
            return "Contrato con " + row.npcName + ": " + pct + " % de polinización. +" + pay + " B.";
        }
        return "Contrato con " + row.npcName + ": " + pct + " % (mín. "
                + (int) Math.round(row.minPct * 100) + " %). Sin pago de contrato; la miel es tuya.";
    }

    private void snapOrStartTrip(HiveEntity h, double lat, double lng, @Nullable String hexId) {
        TruckLiveTrips.StartResult trip = TruckLiveTrips.start(appContext, h, lat, lng, hexId);
        if (trip != TruckLiveTrips.StartResult.STARTED) {
            h.lat = lat;
            h.lng = lng;
            if (hexId != null && !hexId.isEmpty()) {
                h.hexId = hexId;
            }
            h.elevationMeters = OpenMeteoElevation.resolveMetersPersistedBlocking(h.lat, h.lng);
        }
    }

    private void clearHiveContractFields(List<HiveEntity> hives) {
        for (HiveEntity h : hives) {
            if (h == null) {
                continue;
            }
            h.contractId = null;
            h.contractOriginHexId = null;
            h.contractOriginFlora = null;
            h.contractOriginLat = 0;
            h.contractOriginLng = 0;
            hiveDao.upsert(h);
            persistHiveCloud(h);
        }
    }

    private static Set<String> hexesInRegion(Set<String> hexes, PlayableMapRegion region) {
        Set<String> out = new HashSet<>();
        if (hexes == null || region == null) {
            return out;
        }
        for (String hexId : hexes) {
            if (hexId != null && PlayableMapRegion.fromHexId(hexId) == region) {
                out.add(hexId);
            }
        }
        return out;
    }

    private static int nearestDistance(Set<String> originHexes, String destHex) {
        int best = Integer.MAX_VALUE;
        for (String origin : originHexes) {
            if (origin == null || origin.equals(destHex)) {
                continue;
            }
            int d = HexParcel.axialDistance(origin, destHex);
            if (d < best) {
                best = d;
            }
        }
        return best == Integer.MAX_VALUE ? 0 : best;
    }

    private double nearestKm(@Nullable Set<String> originHexes, @NonNull HexParcel dest) {
        if (originHexes == null || originHexes.isEmpty()) {
            return 0;
        }
        double best = Double.MAX_VALUE;
        for (String origin : originHexes) {
            if (origin == null || origin.equals(dest.id)) {
                continue;
            }
            HexParcel from = IberiaHexOverlayStore.findById(appContext, origin);
            if (from == null) {
                continue;
            }
            double km = TranshumanceRules.haversineKm(
                    from.centroidLat, from.centroidLon, dest.centroidLat, dest.centroidLon);
            if (km < best) {
                best = km;
            }
        }
        return best == Double.MAX_VALUE ? 0 : best;
    }

    private static int daysUntilOffer(Offer o, LocalDate today) {
        if (o == null || o.farm == null) {
            return 40;
        }
        if (o.farm.terms != null && o.farm.terms.startDoy > 0) {
            int doy = Math.min(365, today.getDayOfYear());
            return FloraBloomWindow.daysUntilStart(o.farm.terms.startDoy, doy);
        }
        return FloraBloomWindow.daysUntilBloomStart(o.farm.flora, o.farm.parcel, today);
    }

    private Map<String, Integer> fetchActiveCountByHexBlocking() {
        Map<String, Integer> out = new HashMap<>();
        if (GameServer.enabled()) {
            try {
                org.json.JSONArray rows = GameServer.activeContractCounts();
                if (rows != null) {
                    for (int i = 0; i < rows.length(); i++) {
                        org.json.JSONObject row = rows.optJSONObject(i);
                        if (row == null) {
                            continue;
                        }
                        String hexId = row.optString("hexId", "");
                        if (!hexId.isEmpty()) {
                            out.put(hexId, row.optInt("n", 0));
                        }
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "fetchActiveCountByHexBlocking", e);
            }
            return out;
        }
        if (firestore == null) {
            return out;
        }
        try {
            QuerySnapshot snap = Tasks.await(firestore.collection(COLLECTION)
                    .whereEqualTo("status", PollinationContractRules.STATUS_ACTIVE)
                    .get(), 3, java.util.concurrent.TimeUnit.SECONDS);
            for (QueryDocumentSnapshot doc : snap) {
                String hexId = doc.getString("hexId");
                if (hexId == null || hexId.isEmpty()) {
                    continue;
                }
                Integer n = out.get(hexId);
                out.put(hexId, n == null ? 1 : n + 1);
            }
        } catch (Exception e) {
            Log.w(TAG, "fetchActiveCountByHexBlocking", e);
        }
        return out;
    }

    private Map<String, int[]> computeBucketStats(List<HexParcel> parcels, Map<String, Integer> activeByHex) {
        Map<String, int[]> out = new HashMap<>();
        if (parcels == null) {
            return out;
        }
        for (HexParcel parcel : parcels) {
            List<NpcContractFarm> farms = NpcContractCatalog.farmsFor(parcel);
            for (int i = 0; i < farms.size(); i++) {
                NpcContractFarm farm = farms.get(i);
                if (farm == null) {
                    continue;
                }
                String bucket = NpcContractCatalog.occupancyBucket(parcel, farm.flora);
                int[] stats = out.get(bucket);
                if (stats == null) {
                    stats = new int[]{0, 0};
                    out.put(bucket, stats);
                }
                stats[0] += activeByHex.getOrDefault(parcel.id, 0);
                stats[1] += 1;
            }
        }
        return out;
    }

    private void releaseServerOfferClaim(@NonNull String hexId, int band,
            @NonNull String flora, int startDoy, @NonNull String ownerId) {
        if (GameServer.enabled()) {
            GameServer.releasePollinationOffer(hexId, band, flora, startDoy, ownerId);
        }
    }

    private void persistContractCloud(PollinationContractEntity row) {
        if (row == null || row.id == null) {
            return;
        }
        // El servidor necesita conocer el contrato activo para no ofrecer
        // inmediatamente la misma finca al resto de jugadores.
        GameServer.pushPollinationContract(appContext, row);
        if (firestore == null) {
            return;
        }
        try {
            Map<String, Object> m = new HashMap<>();
            m.put("ownerId", row.ownerId);
            m.put("hexId", row.hexId);
            m.put("flora", row.flora);
            m.put("npcName", row.npcName);
            m.put("estateName", row.estateName);
            m.put("region", row.region);
            m.put("climateZone", row.climateZone);
            m.put("layer", row.layer);
            m.put("status", row.status);
            m.put("collectedKg", row.collectedKg);
            m.put("poolKg", row.poolKg);
            m.put("sawPeak", row.sawPeak);
            m.put("minPct", row.minPct);
            m.put("payB", row.payB);
            m.put("extraBPerPoint", row.extraBPerPoint);
            m.put("travelCostPaid", row.travelCostPaid);
            m.put("acceptedDayKey", row.acceptedDayKey);
            m.put("workDays", row.workDays);
            m.put("dueDayKey", row.dueDayKey);
            m.put("startDoy", row.startDoy);
            m.put("hiveIdsJson", row.hiveIdsJson);
            Tasks.await(firestore.collection(COLLECTION).document(row.id).set(m, SetOptions.merge()));
        } catch (Exception e) {
            Log.w(TAG, "persistContractCloud", e);
        }
    }

    private void persistHiveCloud(HiveEntity h) {
        if (hiveRepository != null && h != null) {
            hiveRepository.publishHiveToServer(h);
        }
        if (firestore == null || h == null || h.id == null) {
            return;
        }
        try {
            Tasks.await(firestore.collection("hives").document(h.id).set(h, SetOptions.merge()));
        } catch (Exception e) {
            Log.w(TAG, "persistHiveCloud", e);
        }
    }
}
