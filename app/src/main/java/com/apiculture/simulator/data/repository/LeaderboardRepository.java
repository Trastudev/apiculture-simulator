package com.apiculture.simulator.data.repository;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.apiculture.simulator.data.local.AppDatabase;
import com.apiculture.simulator.data.local.entity.HexParcelOwnershipEntity;
import com.apiculture.simulator.data.local.entity.HiveEntity;
import com.apiculture.simulator.data.local.entity.PollinationContractEntity;
import com.apiculture.simulator.data.remote.RankingEntry;
import com.apiculture.simulator.domain.game.FleetRules;
import com.apiculture.simulator.domain.game.GameCalendar;
import com.apiculture.simulator.domain.game.PollinationContractRules;
import com.apiculture.simulator.domain.game.TradeAccessRules;
import com.apiculture.simulator.domain.market.HoneyMarketEngine;
import com.apiculture.simulator.domain.parcel.FloraProgression;
import com.apiculture.simulator.domain.parcel.HexFlora;
import com.apiculture.simulator.domain.parcel.HexParcel;
import com.apiculture.simulator.domain.parcel.WarehouseRules;
import com.apiculture.simulator.domain.population.HivePopulationState;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LeaderboardRepository {

    public enum Metric {
        /** Valor de compra al 100 %, sin desgaste. */
        NET_WORTH("netWorth"),
        /** Miel vendida lifetime (no stock). */
        HONEY_SOLD("honeySold"),
        CONTRACTS("contracts"),
        ORDERS("orders"),
        BEES("bees");

        public final String serverMetric;

        Metric(String serverMetric) {
            this.serverMetric = serverMetric;
        }
    }

    /** {@code null} = Global; {@code "iberia"} / {@code "za"}. */
    public static final String REGION_IBERIA = "iberia";
    public static final String REGION_ZA = "za";
    public static final String REGION_MDG = "mdg";

    private static final String USERS = "users";

    private final Context appContext;
    private final FirebaseFirestore firestore;
    private final HiveRepository hiveRepository;
    private final EconomyRepository economyRepository;
    private final PlayerProgressRepository progressRepository;
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    public LeaderboardRepository(
            @NonNull Context appContext,
            @Nullable FirebaseFirestore firestore,
            @NonNull HiveRepository hiveRepository,
            @NonNull EconomyRepository economyRepository,
            @NonNull PlayerProgressRepository progressRepository) {
        this.appContext = appContext.getApplicationContext();
        this.firestore = firestore;
        this.hiveRepository = hiveRepository;
        this.economyRepository = economyRepository;
        this.progressRepository = progressRepository;
    }

    public void shutdown() {
        io.shutdown();
    }

    public interface FetchCallback {
        void onSuccess(@NonNull List<RankingEntry> rows);

        void onError(@NonNull String message);
    }

    /**
     * Sube a Postgres la fila de este jugador (miel vendida, patrimonio, comandas, región).
     * Abejas y contratos liquidados los cuenta el servidor con sus tablas.
     */
    public void enqueuePublish(@Nullable String uid) {
        if (!GameServer.enabled() || uid == null || uid.isEmpty()) {
            return;
        }
        io.execute(() -> publishBlocking(uid));
    }

    private void publishBlocking(String uid) {
        try {
            String honeyBrand = "";
            String playerName = "";
            JSONObject userDoc = GameServer.loadPlayer(uid);
            if (userDoc != null) {
                String hb = userDoc.optString("honeyBrand", "");
                String pn = userDoc.optString("playerName", "");
                if (!hb.trim().isEmpty()) {
                    honeyBrand = hb.trim();
                }
                if (!pn.trim().isEmpty()) {
                    playerName = pn.trim();
                }
            }
            int level = progressRepository.getLevel(uid);
            double xp = progressRepository.getXp(uid);
            double honeySoldKg = economyRepository.getHoneySoldTotalKg();
            List<HiveEntity> hives = hiveRepository.getLocalHivesSync(uid);
            int adultBees = 0;
            if (hives != null) {
                for (HiveEntity h : hives) {
                    if (h != null) {
                        adultBees += HivePopulationState.adultWorkersForUi(h,
                                HiveRepository.DEFAULT_BEE_COUNT_PER_HIVE);
                    }
                }
            }
            JSONObject body = new JSONObject();
            if (!playerName.isEmpty()) {
                body.put("playerName", playerName);
            }
            if (!honeyBrand.isEmpty()) {
                body.put("honeyBrand", honeyBrand);
            }
            body.put("economyBalanceEur", economyRepository.getBalance());
            body.put("economyHoneyBucketsJson", economyRepository.snapshotHoneyBucketsJsonForCloud());
            body.put("economyHoneySoldKgTotal", honeySoldKg);
            body.put("economyHoneySoldByFloraJson", economyRepository.snapshotHoneySoldByFloraJsonForCloud());
            body.put("playerLevel", level);
            body.put("playerXp", xp);
            boolean assetsKnown = HexParcelRepository.ownershipKnown(appContext, uid);
            if (assetsKnown) {
                body.put("netWorthB", netWorthB(uid, hives));
                body.put("netWorthDayKey", GameCalendar.toDayKey(LocalDate.now(GameCalendar.userTimeZone())));
                body.put("assetsKnown", true);
            }
            body.put("mapRegion", MapRegionPrefs.get(appContext).prefsValue());
            body.put("ordersDelivered", RankingCounters.orders(appContext, uid));
            body.put("adultBeeCount", adultBees);
            body.put("contractCount", settledContracts(uid));
            GameServer.publishRanking(body);
        } catch (Exception ignored) {
        }
    }

    @Nullable
    private DocumentSnapshot profileDoc(@NonNull String uid) {
        if (firestore == null) {
            return null;
        }
        try {
            return Tasks.await(firestore.collection(USERS).document(uid).get());
        } catch (Exception ignored) {
            return null;
        }
    }

    public void fetchLeaderboard(@NonNull Metric metric, @NonNull FetchCallback callback) {
        fetchLeaderboard(metric, null, null, callback);
    }

    /**
     * @param regionFilter {@code null}/vacío = global; {@link #REGION_IBERIA}, {@link #REGION_ZA} o {@link #REGION_MDG}
     * @param floraKey     solo con {@link Metric#HONEY_SOLD}: flora concreta; null = total vendido
     */
    public void fetchLeaderboard(
            @NonNull Metric metric,
            @Nullable String regionFilter,
            @Nullable String floraKey,
            @NonNull FetchCallback callback) {
        if (!GameServer.enabled()) {
            callback.onError("Sin servidor");
            return;
        }
        io.execute(() -> {
            try {
                String flora = metric == Metric.HONEY_SOLD && floraKey != null && !floraKey.trim().isEmpty()
                        ? HoneyMarketEngine.canonicalFloraKey(floraKey)
                        : null;
                JSONObject payload = GameServer.fetchRanking(metric.serverMetric, regionFilter, flora);
                if (payload == null) {
                    int status = GameServer.lastRankingStatus();
                    runOnMain(() -> callback.onError(status == 0
                            ? "Sin respuesta del servidor"
                            : "El servidor ha respondido " + status));
                    return;
                }
                JSONArray rows = payload.optJSONArray("rows");
                List<RankingEntry> list = new ArrayList<>();
                NumberFormat nf = NumberFormat.getNumberInstance(new Locale("es", "ES"));
                if (rows != null) {
                    for (int i = 0; i < rows.length(); i++) {
                        JSONObject row = rows.optJSONObject(i);
                        if (row == null) {
                            continue;
                        }
                        list.add(toEntry(row, metric, nf));
                    }
                }
                runOnMain(() -> callback.onSuccess(list));
            } catch (Exception e) {
                String msg = e.getMessage() != null ? e.getMessage() : "fetch";
                runOnMain(() -> callback.onError(msg));
            }
        });
    }

    @NonNull
    private static RankingEntry toEntry(@NonNull JSONObject row, @NonNull Metric metric,
            @NonNull NumberFormat nf) {
        String brand = row.optString("honeyBrand", "").trim();
        if (brand.isEmpty()) {
            brand = "—";
        }
        String name = row.optString("playerName", "").trim();
        if (name.isEmpty()) {
            name = "Jugador";
        }
        double score = row.optDouble("score", 0);
        return new RankingEntry(row.optInt("rank", 0), row.optString("id", ""), brand, name,
                formatValue(metric, score, nf), null, row.optBoolean("isSelf", false));
    }

    @NonNull
    private static String formatValue(@NonNull Metric metric, double score, @NonNull NumberFormat nf) {
        switch (metric) {
            case NET_WORTH:
                return nf.format(Math.round(score)) + " B";
            case HONEY_SOLD:
                return nf.format(Math.round(score * 10.0) / 10.0) + " kg";
            case CONTRACTS:
            case ORDERS:
            case BEES:
                return nf.format(Math.round(score));
            default:
                return "—";
        }
    }

    private long netWorthB(@NonNull String uid, @Nullable List<HiveEntity> hives) {
        long worth = 0L;
        AppDatabase db = AppDatabase.getInstance(appContext);
        HashSet<String> hexes = new HashSet<>();
        List<HexParcelOwnershipEntity> owned = db.hexParcelOwnershipDao().getAllForOwnerSync(uid);
        if (owned != null) {
            for (HexParcelOwnershipEntity row : owned) {
                if (row == null) {
                    continue;
                }
                if (row.hexId != null && hexes.add(row.hexId)) {
                    HexParcel parcel = IberiaHexOverlayStore.findById(appContext, row.hexId);
                    worth += FloraProgression.terrainPurchaseTotalEurosForNativeMix(
                            HexFlora.nativeMixForParcel(parcel));
                }
                if (row.hasWarehouse) {
                    worth += WarehouseRules.investedB(Math.max(1, row.warehouseLevel));
                }
            }
        }
        if (hives != null) {
            for (HiveEntity hive : hives) {
                if (hive != null) {
                    worth += HiveRepository.purchasePriceEurosForSuperCount(hive.superCount);
                }
            }
        }
        for (FleetStore.Vehicle vehicle : FleetStore.vehicles(appContext, uid)) {
            if (vehicle == null) {
                continue;
            }
            FleetRules.Kind kind = vehicle.rulesKind();
            worth += FleetRules.purchaseCostB(kind);
            int level = Math.max(1, vehicle.level);
            for (int from = 1; from < level; from++) {
                worth += FleetRules.upgradeCostB(kind, from);
            }
        }
        for (FleetStore.PortSite site : FleetStore.ports(appContext, uid)) {
            if (site != null && site.berths > 0) {
                worth += (long) site.berths * FleetRules.BERTH_B;
            }
        }
        worth += (long) TradeAccessStore.paidPortCount(appContext, uid) * TradeAccessRules.PORT_FEE_B;
        worth += (long) TradeAccessStore.paidMarketCount(appContext, uid) * TradeAccessRules.MARKET_FEE_B;
        return worth;
    }

    private int settledContracts(@NonNull String uid) {
        int n = 0;
        List<PollinationContractEntity> rows =
                AppDatabase.getInstance(appContext).pollinationContractDao().getAllForOwnerSync(uid);
        if (rows == null) {
            return 0;
        }
        for (PollinationContractEntity row : rows) {
            if (row != null && PollinationContractRules.STATUS_SETTLED.equals(row.status)) {
                n++;
            }
        }
        return n;
    }

    private void runOnMain(Runnable r) {
        new android.os.Handler(android.os.Looper.getMainLooper()).post(r);
    }

}
