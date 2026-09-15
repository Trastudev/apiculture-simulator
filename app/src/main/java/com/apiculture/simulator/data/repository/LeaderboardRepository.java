package com.apiculture.simulator.data.repository;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.apiculture.simulator.data.local.entity.HiveEntity;
import com.apiculture.simulator.data.remote.RankingEntry;
import com.apiculture.simulator.domain.market.HoneyMarketEngine;
import com.apiculture.simulator.domain.population.HivePopulationState;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.SetOptions;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LeaderboardRepository {

    public enum Metric {
        LEVEL("level"),
        /** Miel vendida lifetime (no stock). */
        HONEY_SOLD("honeySoldKgTotal"),
        HIVES("hiveCount"),
        BEES("adultBeeCount");

        public final String firestoreField;

        Metric(String firestoreField) {
            this.firestoreField = firestoreField;
        }
    }

    /** {@code null} = Global; {@code "iberia"} / {@code "za"}. */
    public static final String REGION_IBERIA = "iberia";
    public static final String REGION_ZA = "za";

    private static final String PLAYERS = "players";
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
     * Publica en {@code players/{uid}} datos para ranking (nivel, miel vendida, colmenas, región…).
     */
    public void enqueuePublish(@Nullable String uid) {
        if (firestore == null || uid == null || uid.isEmpty()) {
            return;
        }
        io.execute(() -> publishBlocking(uid));
    }

    private void publishBlocking(String uid) {
        try {
            String honeyBrand = "";
            String playerName = "Jugador";
            DocumentSnapshot userDoc = Tasks.await(firestore.collection(USERS).document(uid).get());
            if (userDoc.exists()) {
                String hb = userDoc.getString("honeyBrand");
                String pn = userDoc.getString("playerName");
                if (hb != null && !hb.trim().isEmpty()) {
                    honeyBrand = hb.trim();
                }
                if (pn != null && !pn.trim().isEmpty()) {
                    playerName = pn.trim();
                }
            }
            int level = progressRepository.getLevel(uid);
            int xp = progressRepository.getXp(uid);
            double honeyStockKg = economyRepository.getHoneyStock();
            double honeySoldKg = economyRepository.getHoneySoldTotalKg();
            Map<String, Double> soldByFlora = economyRepository.copyHoneySoldByFlora();
            List<HiveEntity> hives = hiveRepository.getLocalHivesSync(uid);
            int hiveCount = hives != null ? hives.size() : 0;
            int adultBees = 0;
            if (hives != null) {
                for (HiveEntity h : hives) {
                    if (h != null) {
                        adultBees += HivePopulationState.adultWorkersForUi(h,
                                HiveRepository.DEFAULT_BEE_COUNT_PER_HIVE);
                    }
                }
            }
            String mapRegion = MapRegionPrefs.get(appContext).prefsValue();
            Map<String, Object> m = new HashMap<>();
            m.put("playerName", playerName);
            m.put("nickname", playerName);
            m.put("honeyBrand", honeyBrand.isEmpty() ? "—" : honeyBrand);
            m.put("level", level);
            m.put("xp", xp);
            m.put("honeyStockKg", honeyStockKg);
            m.put("totalHoneyKg", honeyStockKg);
            m.put("honeySoldKgTotal", honeySoldKg);
            m.put("honeySoldKgByFlora", soldByFlora);
            m.put("hiveCount", hiveCount);
            m.put("adultBeeCount", adultBees);
            m.put("mapRegion", mapRegion);
            m.put("updatedAt", FieldValue.serverTimestamp());
            Tasks.await(firestore.collection(PLAYERS).document(uid).set(m, SetOptions.merge()));
        } catch (Exception ignored) {
        }
    }

    public void fetchLeaderboard(@NonNull Metric metric, @NonNull FetchCallback callback) {
        fetchLeaderboard(metric, null, null, callback);
    }

    /**
     * @param regionFilter {@code null}/vacío = global; {@link #REGION_IBERIA} o {@link #REGION_ZA}
     * @param floraKey     solo con {@link Metric#HONEY_SOLD}: flora concreta; null = total vendido
     */
    public void fetchLeaderboard(
            @NonNull Metric metric,
            @Nullable String regionFilter,
            @Nullable String floraKey,
            @NonNull FetchCallback callback) {
        if (firestore == null) {
            callback.onSuccess(new ArrayList<>());
            return;
        }
        io.execute(() -> {
            try {
                String orderField = metric.firestoreField;
                List<DocumentSnapshot> docs;
                try {
                    Query q = firestore.collection(PLAYERS)
                            .orderBy(orderField, Query.Direction.DESCENDING)
                            .limit(500);
                    docs = new ArrayList<>(Tasks.await(q.get()).getDocuments());
                } catch (Exception primary) {
                    // Campo nuevo aún sin índice/docs: cae a level y reordena en cliente
                    Query q = firestore.collection(PLAYERS)
                            .orderBy("level", Query.Direction.DESCENDING)
                            .limit(500);
                    docs = new ArrayList<>(Tasks.await(q.get()).getDocuments());
                }

                String region = regionFilter != null ? regionFilter.trim() : "";
                if (!region.isEmpty()) {
                    List<DocumentSnapshot> filtered = new ArrayList<>();
                    for (DocumentSnapshot d : docs) {
                        String mr = d.getString("mapRegion");
                        if (mr == null || mr.isEmpty()) {
                            // Legado: sin región → solo en Global
                            continue;
                        }
                        if (region.equals(mr)) {
                            filtered.add(d);
                        }
                    }
                    docs = filtered;
                }

                final String floraCanonical = floraKey != null && !floraKey.trim().isEmpty()
                        ? HoneyMarketEngine.canonicalFloraKey(floraKey)
                        : null;

                if (metric == Metric.HONEY_SOLD && floraCanonical != null) {
                    docs.sort((a, b) -> Double.compare(
                            floraSoldKg(b, floraCanonical),
                            floraSoldKg(a, floraCanonical)));
                } else if (metric == Metric.LEVEL) {
                    docs.sort((a, b) -> Long.compare(longOr0(b, "level"), longOr0(a, "level")));
                } else if (metric == Metric.HONEY_SOLD) {
                    docs.sort((a, b) -> Double.compare(honeySoldTotal(b), honeySoldTotal(a)));
                }

                List<RankingEntry> rows = new ArrayList<>(docs.size());
                NumberFormat nf = NumberFormat.getNumberInstance(new Locale("es", "ES"));
                int rank = 1;
                for (DocumentSnapshot doc : docs) {
                    double score = scoreFor(metric, doc, floraCanonical);
                    if (metric == Metric.HONEY_SOLD && score <= 1e-9) {
                        continue;
                    }
                    String brand = doc.getString("honeyBrand");
                    if (brand == null || brand.trim().isEmpty()) {
                        brand = "—";
                    }
                    String name = doc.getString("playerName");
                    if (name == null || name.trim().isEmpty()) {
                        name = doc.getString("nickname");
                    }
                    if (name == null || name.trim().isEmpty()) {
                        name = "Jugador";
                    }
                    String valueLabel = formatValue(metric, doc, nf, floraCanonical);
                    rows.add(new RankingEntry(rank++, brand.trim(), name.trim(), valueLabel));
                }
                runOnMain(() -> callback.onSuccess(rows));
            } catch (Exception e) {
                String msg = e.getMessage() != null ? e.getMessage() : "fetch";
                runOnMain(() -> callback.onError(msg));
            }
        });
    }

    private static double scoreFor(Metric metric, DocumentSnapshot doc, @Nullable String flora) {
        switch (metric) {
            case LEVEL:
                return longOr0(doc, "level");
            case HONEY_SOLD:
                return flora != null ? floraSoldKg(doc, flora) : honeySoldTotal(doc);
            case HIVES:
                return longOr0(doc, "hiveCount");
            case BEES:
                return longOr0(doc, "adultBeeCount");
            default:
                return 0;
        }
    }

    private static double honeySoldTotal(DocumentSnapshot doc) {
        Double d = doc.getDouble("honeySoldKgTotal");
        if (d != null) {
            return d;
        }
        // Sin historial de ventas: no usar stock como sustituto
        return 0.0;
    }

    @SuppressWarnings("unchecked")
    private static double floraSoldKg(DocumentSnapshot doc, String floraCanonical) {
        Object raw = doc.get("honeySoldKgByFlora");
        if (!(raw instanceof Map)) {
            return 0.0;
        }
        Map<String, Object> map = (Map<String, Object>) raw;
        Object v = map.get(floraCanonical);
        if (v instanceof Number) {
            return ((Number) v).doubleValue();
        }
        return 0.0;
    }

    private static String formatValue(Metric metric, DocumentSnapshot doc, NumberFormat nf,
            @Nullable String floraCanonical) {
        switch (metric) {
            case LEVEL: {
                long lv = longOr0(doc, "level");
                return "Nv. " + lv;
            }
            case HONEY_SOLD: {
                double kg = floraCanonical != null
                        ? floraSoldKg(doc, floraCanonical)
                        : honeySoldTotal(doc);
                return nf.format(Math.round(kg * 10.0) / 10.0) + " kg";
            }
            case HIVES: {
                return nf.format(longOr0(doc, "hiveCount"));
            }
            case BEES: {
                return nf.format(longOr0(doc, "adultBeeCount"));
            }
            default:
                return "—";
        }
    }

    private void runOnMain(Runnable r) {
        new android.os.Handler(android.os.Looper.getMainLooper()).post(r);
    }

    private static long longOr0(DocumentSnapshot d, String key) {
        Long l = d.getLong(key);
        return l != null ? l : 0L;
    }
}
