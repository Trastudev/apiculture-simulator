package com.apiculture.simulator.domain.population;

import com.apiculture.simulator.data.local.entity.HiveEntity;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;

/**
 * Estado poblacional diario de una colmena: obreras adultas, cría por cohorte (día 0–21 del ciclo
 * obrera) y máquina de estados de la reina.
 */
public final class HivePopulationState {

    public static final int WORKER_BROOD_DAYS = 22;
    /** Huevo días 0–3, larva 4–9, pupa 10–21 (emerge al pasar de 21). */
    public static final int EGG_LAST_DAY = 3;
    public static final int LARVA_LAST_DAY = 9;
    public static final int PUPA_LAST_DAY = 21;

    public static final int MIN_BEES_COLLAPSE = 400;
    static final int MIN_EGGS_TO_START_QUEEN_CELL = 150;
    static final int QUEEN_CELL_TAKE = 200;
    /** Desarrollo reina: días 0–16 (17 etapas), emerge al completar 16. */
    public static final int QUEEN_DEVELOP_LAST_DAY = 16;
    /** Post-emergencia hasta puesta: días 0–11 (12 etapas). */
    public static final int VIRGIN_LAST_DAY = 11;

    public int version = 1;
    public int workersAdult;
    /** Obreras adultas por edad en días (0 = recién emergidas); la última posición acumula edad máxima o superior. */
    public int[] workerAdultByAge = new int[WorkerAdultLifespan.WORKER_ADULT_AGE_BUCKETS];
    public int[] workerBrood = new int[WORKER_BROOD_DAYS];
    public QueenMode queenMode = QueenMode.LAYING;
    /** Días transcurridos en ventana de reemplazo (0–3 intentos; al llegar a 4 → huérfana). */
    public int replaceWindowDay;
    /** Progreso dentro de {@link QueenMode#QUEEN_DEVELOPING} o {@link QueenMode#VIRGIN_PRE_LAYING}. */
    public int queenPipelineDay;
    public int daysWithoutEggLaying;
    /** Último {@code dayKey} para el que ya se aplicó {@link HivePopulationSimulator}. */
    public int lastPopulationDayKey;
    public HivePopulationTrend lastTrend = HivePopulationTrend.STABLE;

    /** Estadísticas del último {@link HivePopulationSimulator#applyDay} (solo obreras; la cría no “muere” en este modelo salvo excepciones). */
    public int lastDayWorkerDeaths;
    public int lastDayWorkerEmergences;
    public int lastDayEggsLaid;
    /** Enjambrazón aplicada al inicio de ese tick (no incluido en {@link #toJson}). */
    public boolean lastDaySwarmed;

    public int totalBees() {
        int t = workersAdult;
        for (int c : workerBrood) {
            t += c;
        }
        return t;
    }

    /** Suma de {@link #workerAdultByAge}; debe coincidir con {@link #workersAdult} tras cada tick. */
    public static int sumWorkerAdultByAge(HivePopulationState s) {
        if (s == null || s.workerAdultByAge == null) {
            return 0;
        }
        int sum = 0;
        for (int c : s.workerAdultByAge) {
            sum += c;
        }
        return sum;
    }

    public static void syncWorkersAdultFromBuckets(HivePopulationState s) {
        if (s == null) {
            return;
        }
        s.workersAdult = sumWorkerAdultByAge(s);
    }

    /**
     * Reparte {@link #workersAdult} de forma casi uniforme entre todas las edades (misma idea que la cría en 22 días).
     */
    public static void partitionWorkerAdultsEvenly(HivePopulationState s) {
        if (s == null) {
            return;
        }
        int b = WorkerAdultLifespan.WORKER_ADULT_AGE_BUCKETS;
        if (s.workerAdultByAge == null || s.workerAdultByAge.length != b) {
            s.workerAdultByAge = new int[b];
        }
        Arrays.fill(s.workerAdultByAge, 0);
        int w = Math.max(0, s.workersAdult);
        if (w == 0) {
            s.workersAdult = 0;
            return;
        }
        int span = Math.max(12, Math.min(b, (int) Math.round(ADULT_INIT_EFFECTIVE_LIFESPAN_DAYS * 1.5)));
        int weightSum = span * (span + 1) / 2;
        int assigned = 0;
        int[] frac = new int[span];
        for (int i = 0; i < span; i++) {
            int weight = span - i;
            int scaled = w * weight;
            int quota = scaled / weightSum;
            s.workerAdultByAge[i] = quota;
            frac[i] = scaled - quota * weightSum;
            assigned += quota;
        }
        int rem = w - assigned;
        for (int k = 0; k < rem; k++) {
            int best = 0;
            for (int i = 1; i < span; i++) {
                if (frac[i] > frac[best]) {
                    best = i;
                }
            }
            s.workerAdultByAge[best]++;
            frac[best] = -1;
        }
        s.workersAdult = w;
    }

    public int broodEggs() {
        int s = 0;
        for (int i = 0; i <= EGG_LAST_DAY; i++) {
            s += workerBrood[i];
        }
        return s;
    }

    public int broodLarvae() {
        int s = 0;
        for (int i = EGG_LAST_DAY + 1; i <= LARVA_LAST_DAY; i++) {
            s += workerBrood[i];
        }
        return s;
    }

    public int broodPupae() {
        int s = 0;
        for (int i = LARVA_LAST_DAY + 1; i <= PUPA_LAST_DAY; i++) {
            s += workerBrood[i];
        }
        return s;
    }

    public String queenStatusLabelEs() {
        switch (queenMode) {
            case LAYING:
                return "Viva (puesta)";
            case REPLACE_WINDOW:
                return "Sin reina · ventana reemplazo";
            case QUEEN_DEVELOPING:
                return "Nueva reina en desarrollo";
            case VIRGIN_PRE_LAYING:
                return "Reina joven (sin puesta)";
            case ORPHANED:
                return "Sin reina (huérfana)";
            case COLLAPSED:
            default:
                return "Colapso";
        }
    }

    public void clampNonNegative() {
        workersAdult = Math.max(0, workersAdult);
        if (workerAdultByAge != null) {
            for (int i = 0; i < workerAdultByAge.length; i++) {
                workerAdultByAge[i] = Math.max(0, workerAdultByAge[i]);
            }
        }
        for (int i = 0; i < WORKER_BROOD_DAYS; i++) {
            workerBrood[i] = Math.max(0, workerBrood[i]);
        }
        replaceWindowDay = Math.max(0, replaceWindowDay);
        queenPipelineDay = Math.max(0, queenPipelineDay);
        daysWithoutEggLaying = Math.max(0, daysWithoutEggLaying);
    }

    /**
     * Detecta el reparto antiguo de {@link #fromLegacyBeeCount}: solo pupas en días 11–20, sin huevos ni larvas
     * y sin cohorte en el último día de pupa (donde emerge al simular). Esas colmenas hay que resembrar.
     */
    public static boolean isLegacyBroodPipelineLayout(HivePopulationState s) {
        if (s == null) {
            return false;
        }
        if (s.broodEggs() > 0) {
            return false;
        }
        if (s.broodLarvae() > 0) {
            return false;
        }
        if (s.workerBrood[PUPA_LAST_DAY] > 0) {
            return false;
        }
        return s.broodPupae() > 0;
    }

    /**
     * Mortalidad adulta diaria “típica” (primavera/verano) para dimensionar la cría inicial.
     * Con una cola llena (huevos→pupas en los 22 días), la emergencia diaria ≈ L y la mortalidad ≈ m·adultos;
     * igualando L ≈ m·adultos y N = adultos + 22·L se obtiene el reparto equilibrado.
     * {@code m ≈ 1/L} con L ~ 40 días en época activa ({@link WorkerAdultLifespan}).
     */
    private static final double STEADY_STATE_ADULT_MORTALITY_RATIO = 1.0 / 40.0;
    private static final int ADULT_INIT_EFFECTIVE_LIFESPAN_DAYS = 40;
    private static final int MAX_POTENTIAL_EGGS_PER_DAY = 2000;
    private static final int MIN_POTENTIAL_EGGS_PER_DAY = 600;

    /** Adultos mínimos al partir solo de {@code beeCount} (picos de cría no deben dejar casi solo cría). */
    private static final int MIN_INITIAL_ADULT_WORKERS = 300;

    /**
     * Fija adultos + total de cría según el equilibrio habitual (misma lógica que antes); no reparte aún por días.
     *
     * @return Número de obreras en huevo/larva/pupa ({@code ∑ workerBrood} antes de repartir).
     */
    private static int applyEquilibriumAdultsAndBroodTotal(HivePopulationState s, int beeCount) {
        int n = Math.max(500, beeCount);
        double m = STEADY_STATE_ADULT_MORTALITY_RATIO;
        double denom = 1.0 + WORKER_BROOD_DAYS * m;
        int broodByMortalityEq = (int) Math.round(n * WORKER_BROOD_DAYS * m / denom);

        // Pirámide inicial alineada con una puesta potencial que escala con fuerza de colonia (hasta 2000/día).
        // Se mezcla con el equilibrio por mortalidad para evitar saltos bruscos al iniciar/cargar.
        double strength = Math.max(0.0, Math.min(1.0, (n - 12_000) / 68_000.0));
        int targetLayPerDay = (int) Math.round(
                MIN_POTENTIAL_EGGS_PER_DAY
                        + strength * (MAX_POTENTIAL_EGGS_PER_DAY - MIN_POTENTIAL_EGGS_PER_DAY));
        int broodByLayPotential = targetLayPerDay * WORKER_BROOD_DAYS;

        int broodTotal = (int) Math.round(0.60 * broodByMortalityEq + 0.40 * broodByLayPotential);
        broodTotal = Math.max(0, Math.min(broodTotal, n - MIN_INITIAL_ADULT_WORKERS));
        s.workersAdult = Math.max(MIN_INITIAL_ADULT_WORKERS, n - broodTotal);
        return n - s.workersAdult;
    }

    /**
     * Reparte la cría en 22 cohortes de tamaño casi idéntico (régimen estacionario del pipeline).
     * Cada cohorte corresponde a un día de desarrollo: con puesta diaria ~{@code L}, en equilibrio ~{@code L}
     * huevos pasan a larva, ~{@code L} larvas a pupa y ~{@code L} pupas emergen por día.
     * Los totales por fase siguen las duraciones del modelo: huevos días 0–3 (4 cohortes), larvas 4–9 (6), pupas 10–21 (12).
     */
    private static void partitionBroodEvenly(HivePopulationState s, int broodTotal) {
        Arrays.fill(s.workerBrood, 0);
        if (broodTotal <= 0) {
            return;
        }
        int base = broodTotal / WORKER_BROOD_DAYS;
        int rem = broodTotal % WORKER_BROOD_DAYS;
        for (int i = 0; i < WORKER_BROOD_DAYS; i++) {
            s.workerBrood[i] = base + (i < rem ? 1 : 0);
        }
    }

    /**
     * Colmena nueva o migración desde solo {@code beeCount}: reparto <strong>uniforme</strong> por edad (solo conviene
     * para plantillas internas, p. ej. reparar JSON incompleto con {@link #rebalanceBroodPipelineAfterJsonLoad}).
     */
    public static HivePopulationState fromLegacyBeeCount(int beeCount) {
        HivePopulationState s = new HivePopulationState();
        int broodTotal = applyEquilibriumAdultsAndBroodTotal(s, beeCount);
        partitionBroodEvenly(s, broodTotal);
        partitionWorkerAdultsEvenly(s);
        s.queenMode = QueenMode.LAYING;
        s.lastPopulationDayKey = 0;
        s.clampNonNegative();
        return s;
    }

    /**
     * Inicio de colonia con el mismo total adultos/cría que {@link #fromLegacyBeeCount} y cohortes equilibradas
     * (mismo orden de magnitud en cada día del pipeline, coherente con flujo diario de puesta y emergencias).
     * {@code entropyKey} se mantiene en la firma por compatibilidad; el reparto ya no depende de él.
     */
    public static HivePopulationState fromInitialColonyWithRandomBroodPipeline(int beeCount, String entropyKey) {
        HivePopulationState s = new HivePopulationState();
        int broodTotal = applyEquilibriumAdultsAndBroodTotal(s, beeCount);
        partitionBroodEvenly(s, broodTotal);
        partitionWorkerAdultsEvenly(s);
        s.queenMode = QueenMode.LAYING;
        s.lastPopulationDayKey = 0;
        s.clampNonNegative();
        return s;
    }

    /**
     * Misma suma total que {@code totalBees}, pero con un número concreto de obreras adultas y el resto en cría
     * repartida en cohortes equilibradas (misma lógica que {@link #fromInitialColonyWithRandomBroodPipeline}).
     */
    public static HivePopulationState fromInitialColonyWithTargetAdultWorkers(
            int totalBees, int targetAdultWorkers, String entropyKey) {
        HivePopulationState s = new HivePopulationState();
        int n = Math.max(500, totalBees);
        int adults = Math.max(MIN_INITIAL_ADULT_WORKERS, targetAdultWorkers);
        adults = Math.min(adults, n);
        int broodTotal = n - adults;
        partitionBroodEvenly(s, broodTotal);
        s.workersAdult = adults;
        partitionWorkerAdultsEvenly(s);
        s.queenMode = QueenMode.LAYING;
        s.lastPopulationDayKey = 0;
        s.clampNonNegative();
        return s;
    }

    public static HivePopulationState fromHiveEntityOrDefault(HiveEntity hive, int defaultBeeCount) {
        if (hive.populationStateJson != null && !hive.populationStateJson.isEmpty()) {
            HivePopulationState p = fromJson(hive.populationStateJson);
            if (p != null) {
                return p;
            }
        }
        int n = hive.beeCount > 0 ? hive.beeCount : defaultBeeCount;
        if (hive.id != null && !hive.id.isEmpty()) {
            return fromInitialColonyWithRandomBroodPipeline(n, hive.id);
        }
        return fromLegacyBeeCount(n);
    }

    /**
     * Obreras adultas para listas, cabeceras y totales de UI (misma lógica que {@link #fromHiveEntityOrDefault}).
     */
    public static int adultWorkersForUi(HiveEntity hive, int defaultBeeCount) {
        if (hive == null) {
            return 0;
        }
        return fromHiveEntityOrDefault(hive, defaultBeeCount).workersAdult;
    }

    public static HivePopulationState fromJson(String json) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        try {
            JSONObject o = new JSONObject(json);
            HivePopulationState s = new HivePopulationState();
            s.version = o.optInt("v", 1);
            s.workersAdult = o.optInt("w", 0);
            JSONArray arr = o.optJSONArray("b");
            if (arr != null) {
                for (int i = 0; i < WORKER_BROOD_DAYS && i < arr.length(); i++) {
                    s.workerBrood[i] = arr.optInt(i, 0);
                }
            }
            String qm = o.optString("qm", QueenMode.LAYING.name());
            try {
                s.queenMode = QueenMode.valueOf(qm);
            } catch (IllegalArgumentException e) {
                s.queenMode = QueenMode.LAYING;
            }
            s.replaceWindowDay = o.optInt("rwd", 0);
            s.queenPipelineDay = o.optInt("qpd", 0);
            s.daysWithoutEggLaying = o.optInt("dwel", 0);
            s.lastPopulationDayKey = o.optInt("lpk", 0);
            s.lastDayWorkerDeaths = o.optInt("lwd", 0);
            s.lastDayWorkerEmergences = o.optInt("lwe", 0);
            s.lastDayEggsLaid = o.optInt("lel", 0);
            String tr = o.optString("tr", HivePopulationTrend.STABLE.name());
            try {
                s.lastTrend = HivePopulationTrend.valueOf(tr);
            } catch (IllegalArgumentException e) {
                s.lastTrend = HivePopulationTrend.STABLE;
            }
            s.clampNonNegative();
            rebalanceBroodPipelineAfterJsonLoad(s, arr);
            rebalanceAdultBucketsAfterJsonLoad(s, o.optJSONArray("aa"), s.version);
            return s;
        } catch (JSONException e) {
            return null;
        }
    }

    /**
     * Firestore u orígenes antiguos a veces guardan sólo {@code w} sin {@code b}, o un array {@code b} recortado.
     * Sin cohortes en toda la tubería (en especial día 21) las emergencias diarias quedan siempre en 0.
     */
    private static void rebalanceBroodPipelineAfterJsonLoad(HivePopulationState s, JSONArray arr) {
        if (s == null || s.queenMode == QueenMode.COLLAPSED) {
            return;
        }
        int bsum = 0;
        for (int c : s.workerBrood) {
            bsum += c;
        }
        boolean truncated = arr != null && arr.length() < WORKER_BROOD_DAYS;
        boolean missingBrood = bsum == 0 && s.workersAdult >= 200;
        boolean legacyGap = isLegacyBroodPipelineLayout(s);
        if (!truncated && !missingBrood && !legacyGap) {
            return;
        }
        int tot = Math.max(500, s.totalBees());
        if (tot < 200) {
            return;
        }
        HivePopulationState template = fromLegacyBeeCount(tot);
        s.workersAdult = template.workersAdult;
        System.arraycopy(template.workerBrood, 0, s.workerBrood, 0, WORKER_BROOD_DAYS);
        System.arraycopy(template.workerAdultByAge, 0, s.workerAdultByAge, 0, WorkerAdultLifespan.WORKER_ADULT_AGE_BUCKETS);
        s.clampNonNegative();
    }

    /**
     * Reparte exactamente {@code targetSum} abejas en {@code dest} proporcionalmente a {@code sourceRemain}
     * (mayor resto fraccionario primero) y las resta de {@code sourceRemain}.
     */
    private static void allocateProportionalAdultSplit(int[] sourceRemain, int[] dest, int targetSum) {
        Arrays.fill(dest, 0);
        if (sourceRemain == null || dest == null || sourceRemain.length != dest.length) {
            return;
        }
        int w = 0;
        for (int c : sourceRemain) {
            w += c;
        }
        if (w <= 0 || targetSum <= 0) {
            return;
        }
        targetSum = Math.min(targetSum, w);
        int b = sourceRemain.length;
        Integer[] ord = new Integer[b];
        double[] frac = new double[b];
        int assigned = 0;
        for (int i = 0; i < b; i++) {
            ord[i] = i;
            double exact = sourceRemain[i] * (double) targetSum / w;
            int gi = (int) Math.floor(exact);
            dest[i] = gi;
            frac[i] = exact - gi;
            assigned += gi;
        }
        int need = targetSum - assigned;
        Arrays.sort(ord, (i, j) -> Double.compare(frac[j], frac[i]));
        for (int k = 0; k < need; k++) {
            dest[ord[k]]++;
        }
        for (int i = 0; i < b; i++) {
            sourceRemain[i] -= dest[i];
        }
    }

    private static void rebalanceAdultBucketsAfterJsonLoad(HivePopulationState s, JSONArray aa, int version) {
        if (s == null || s.queenMode == QueenMode.COLLAPSED) {
            return;
        }
        int b = WorkerAdultLifespan.WORKER_ADULT_AGE_BUCKETS;
        if (s.workerAdultByAge == null || s.workerAdultByAge.length != b) {
            s.workerAdultByAge = new int[b];
        }
        if (aa != null) {
            for (int i = 0; i < b && i < aa.length(); i++) {
                s.workerAdultByAge[i] = aa.optInt(i, 0);
            }
        }
        boolean truncated = aa == null || aa.length() < b;
        int sum = sumWorkerAdultByAge(s);
        boolean legacyAdultDistribution = version < 3;
        if (legacyAdultDistribution || truncated || (sum == 0 && s.workersAdult > 0) || (sum > 0 && sum != s.workersAdult)) {
            partitionWorkerAdultsEvenly(s);
        } else {
            syncWorkersAdultFromBuckets(s);
        }
    }

    public String toJson() {
        try {
            JSONObject o = new JSONObject();
            o.put("v", Math.max(version, 3));
            o.put("w", workersAdult);
            JSONArray ageArr = new JSONArray();
            if (workerAdultByAge != null) {
                for (int c : workerAdultByAge) {
                    ageArr.put(c);
                }
            }
            o.put("aa", ageArr);
            JSONArray arr = new JSONArray();
            for (int i = 0; i < WORKER_BROOD_DAYS; i++) {
                arr.put(workerBrood[i]);
            }
            o.put("b", arr);
            o.put("qm", queenMode.name());
            o.put("rwd", replaceWindowDay);
            o.put("qpd", queenPipelineDay);
            o.put("dwel", daysWithoutEggLaying);
            o.put("lpk", lastPopulationDayKey);
            o.put("lwd", lastDayWorkerDeaths);
            o.put("lwe", lastDayWorkerEmergences);
            o.put("lel", lastDayEggsLaid);
            o.put("tr", lastTrend.name());
            return o.toString();
        } catch (JSONException e) {
            return "{}";
        }
    }

    /**
     * Crea una segunda colonia con la mitad “justa” de adultos y de cada cohorte de cría; {@code this} conserva el resto.
     * Copia estado de reina y tendencia; útil para división de colmena en el mismo hexágono.
     */
    public HivePopulationState splitOffFairHalf() {
        HivePopulationState spawn = new HivePopulationState();
        spawn.version = version;
        spawn.queenMode = queenMode;
        spawn.replaceWindowDay = replaceWindowDay;
        spawn.queenPipelineDay = queenPipelineDay;
        spawn.daysWithoutEggLaying = daysWithoutEggLaying;
        spawn.lastPopulationDayKey = lastPopulationDayKey;
        spawn.lastTrend = lastTrend;
        int w = workersAdult;
        int targetSpawn = w / 2;
        if (workerAdultByAge != null && w > 0 && targetSpawn >= 0) {
            allocateProportionalAdultSplit(workerAdultByAge, spawn.workerAdultByAge, targetSpawn);
            syncWorkersAdultFromBuckets(spawn);
            syncWorkersAdultFromBuckets(this);
        } else {
            spawn.workersAdult = targetSpawn;
            workersAdult = w - targetSpawn;
            partitionWorkerAdultsEvenly(spawn);
            partitionWorkerAdultsEvenly(this);
        }
        for (int i = 0; i < WORKER_BROOD_DAYS; i++) {
            int c = workerBrood[i];
            int c2 = c / 2;
            spawn.workerBrood[i] = c2;
            workerBrood[i] = c - c2;
        }
        spawn.clampNonNegative();
        clampNonNegative();
        return spawn;
    }
}
