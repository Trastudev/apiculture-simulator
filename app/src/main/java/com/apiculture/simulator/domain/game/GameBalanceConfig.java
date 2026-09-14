package com.apiculture.simulator.domain.game;

import android.content.Context;
import android.util.Log;

import com.apiculture.simulator.domain.population.HivePopulationState;
import com.apiculture.simulator.domain.population.WorkerAdultLifespan;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Constantes de simulación cargadas de {@code assets/game_balance.json}.
 * Si el fichero falta o está mal formado se conservan los valores por defecto (los del modelo actual).
 */
public final class GameBalanceConfig {

    private static final String TAG = "GameBalanceConfig";
    public static final String ASSET_NAME = "game_balance.json";

    public static int maxAdultWorkersPerHive = 80_000;
    public static int minBeesCollapse = 400;
    public static double lambdaTowardK = 0.04;
    /** Contracción hacia K (otoño / colmena por encima de capacidad). Más lenta que el crecimiento. */
    public static double lambdaTowardKDown = 0.018;
    /**
     * Probabilidad diaria de muerte súbita de reina en puesta. 0.0004 ≈ 0,04 %/día ≈ 14 % anual
     * por colmena. El valor anterior 0.005 (0,5 %/día) vaciaba apiarios pequeños en semanas.
     */
    public static double queenDailyDeathP = 0.0004;
    public static int[] kDoy = {1, 46, 80, 120, 161, 201, 244, 288, 330, 365};
    public static int[] kAdults = {12_000, 13_000, 18_000, 44_000, 58_000, 48_000, 38_000, 28_000, 18_000, 12_000};
    public static double queenKFactorMin = 0.78;
    public static double queenKFactorSpan = 0.22;
    public static double healthKFactorMin = 0.55;
    public static double healthKFactorSpan = 0.45;
    public static double varroaKStartPct = 5.0;
    public static double varroaKSpanPct = 30.0;
    public static double varroaKMaxPenalty = 0.50;
    public static double notLayingKMultiplier = 0.35;
    public static double minHoneyKFactorFloor = 0.75;
    /** Suelo de esperanza de vida de adultas si el stock de miel está a 0 kg. */
    public static double lowHoneyLifespanFloor = 0.50;
    public static double feedBroodKCap = 1.12;
    public static int collapsedDailyDropDivisor = 18;
    public static int collapsedDailyDropMin = 40;
    public static int[] workerLifespanDoy = {1, 45, 75, 110, 145, 172, 200, 230, 262, 288, 315, 335, 355, 365};
    public static double[] workerLifespanDays = {132, 118, 95, 62, 44, 39, 36, 36, 37, 44, 58, 78, 105, 128};
    public static double workerLifespanClampMin = 24;
    public static double workerLifespanClampMax = 200;

    public static double eggsSpring = 1750;
    public static double eggsSummer = 1100;
    public static double eggsAutumn = 1200;
    public static double eggsWinter = 50;
    /** Curva continua de puesta (huevos/día base). Si está vacía, se usan las 4 estaciones. */
    public static int[] eggsDoy = {1, 32, 60, 80, 105, 135, 166, 196, 227, 244, 258, 288, 319, 335, 355, 365};
    public static double[] eggsBase = {
            50, 80, 220, 550, 1550, 1750, 1500, 1100, 1250, 900, 720, 1000, 1350, 500, 80, 50};
    public static double eggsStrengthRefAdults = 38_000;
    public static int eggsMaxPerDay = 2000;
    public static int eggsMaxPerDayRoomBonus = 1800;
    public static double layNoiseMin = 0.96;
    public static double layNoiseSpan = 0.08;
    /** Suelo del multiplicador de puesta si el stock de miel está bajo el mínimo. */
    public static double eggsLowHoneyFactorFloor = 0.55;

    public static int swarmBaseAdults = 60_000;
    public static int swarmStepAdults = 5_000;
    public static double swarmRiskPerStep = 0.008;
    public static double swarmDailyCap = 0.35;
    public static int swarmSeasonStartDoy = 90;
    public static int swarmSeasonEndDoy = 185;

    public static int splitMinAdults = 50_000;
    public static int splitRecommendAdults = 70_000;

    public static double foragerFraction = 0.24;
    public static double kgPerForagerFullFlow = 1.35e-4;
    public static double consumptionBaseKg = 0.03;
    public static double consumptionPerAdultKg = 4.8e-6;
    public static double consumptionPerBroodEqKg = 4.0e-6;
    public static double syrupConsumptionMultiplier = 0.55;
    public static double nectarNoiseMin = 0.94;
    public static double nectarNoiseSpan = 0.12;
    public static double minHiveStockKg = 0.5;
    public static double starterHiveStockKg = 5.0;
    public static double[] superCapKg = {8.0, 30.0, 60.0};
    public static double superPurchasePriceEur = 50.0;
    public static double chartFloraBoost = 1.15;
    public static double chartTempBoost = 1.12;

    public static double varroaCapPct = 40.0;
    /** Tasa relativa diaria a puesta de referencia {@link #varroaEggsRefPerDay}. */
    public static double varroaBaseRelativeDailyRate = 0.012;
    public static double varroaLoadAmplifier = 1.6;
    public static double varroaReboundRelativeDailyRate = 0.006;
    /** Huevos/día a los que la varroa se reproduce a la tasa base (primavera fuerte). */
    public static double varroaEggsRefPerDay = 1400.0;
    /** Suelo: ácaros foréticos en invierno sin cría (casi nulo, no cero). */
    public static double varroaBroodMultFloor = 0.03;
    public static double varroaBroodMultCap = 1.15;
    public static int maxHealthDeltaPerDay = 5;
    public static double honeyHealthPenaltyLowKgExtra = 1.5;
    public static double honeyHealthPenaltyMidKgExtra = 3.0;
    public static double healthHoneyLossPerMissingPoint = 0.005;
    public static double healthHoneyFloor = 0.35;
    public static double summerVarroaSeasonMult = 1.0;
    public static double autumnVarroaSeasonMult = 1.0;
    public static double winterVarroaSeasonMult = 1.0;
    public static double mountainVarroaMult = 0.55;
    public static double highElevVarroaMult = 0.70;
    public static int highElevVarroaStartM = 1500;
    public static double varroaFreezeC = 0.0;
    public static double varroaColdC = 8.0;
    public static double varroaChillyC = 12.0;
    public static double varroaFreezeMult = 0.22;
    public static double varroaColdMult = 0.40;
    public static double varroaChillyMult = 0.65;
    public static double varroaEnvMultFloor = 0.05;

    public static double skyMultSun = 1.0;
    public static double skyMultCloudy = 0.7;
    public static double skyMultWindy = 0.5;
    public static double skyMultRain = 0.0;
    public static int skyLowElevMaxM = 800;
    public static int skyMidElevMaxM = 1500;
    public static double skyLowSunEnd = 0.80;
    public static double skyLowCloudEnd = 0.90;
    public static double skyLowWindEnd = 0.95;
    public static double skyMidSunEnd = 0.65;
    public static double skyMidCloudEnd = 0.80;
    public static double skyMidWindEnd = 0.90;
    public static double skyHighSunEnd = 0.50;
    public static double skyHighCloudEnd = 0.75;
    public static double skyHighWindEnd = 0.875;

    public static List<TempBand> temperatureBands = defaultTempBands();
    public static double temperatureOverCZero = 50.0;
    public static List<TempBand> layingBands = defaultLayingBands();

    public static int nectarBgStartDoy = 70;
    public static int nectarBgEndDoy = 340;
    public static double nectarBgBase = 0.06;
    public static double nectarBgAmp = 0.08;
    public static Map<String, List<NectarPeak>> nectarFlora = defaultNectarFlora();

    public static int mountainMinM = 2000;
    public static int bloomShiftAtlantic = 8;
    public static int bloomShiftMountain = 5;
    public static int bloomShiftMediterranean = -6;
    public static int bloomShiftSouth = -10;
    public static int bloomShiftContinental = 0;
    /** Invierno, primavera, verano, otoño. */
    public static double[] nectarMultAtlantic = {1.15, 0.88, 0.52, 1.08};
    public static double[] nectarMultMountain = {0.38, 1.48, 1.65, 1.50};
    public static double[] nectarMultMediterranean = {1.38, 1.28, 0.48, 1.24};
    public static double[] nectarMultSouth = {1.30, 1.32, 0.38, 1.20};
    public static double[] nectarMultContinental = {0.78, 0.88, 0.50, 1.14};
    /** Invierno, primavera, verano, otoño. Alta montaña no recorta en verano. */
    public static double[] layingMultAtlantic = {1.00, 1.00, 0.68, 1.00};
    public static double[] layingMultMountain = {1.00, 1.00, 1.00, 1.00};
    public static double[] layingMultMediterranean = {1.00, 1.00, 0.55, 1.00};
    public static double[] layingMultSouth = {1.00, 1.00, 0.38, 1.00};
    public static double[] layingMultContinental = {1.00, 1.00, 0.68, 1.00};
    public static double vintageMin = 0.72;
    public static double vintageMax = 1.22;
    public static int nativeHiveSlots = 5;
    public static int plantedHiveSlots = 3;
    public static double crowdingFloor = 0.45;
    public static double secondaryNectarTrigger = 0.18;
    public static double secondaryNectarShare = 0.28;
    /** Tope tras clima: la montaña en verano puede pasar de 1,20 (trashumancia). */
    public static double nectarIntensityCap = 1.50;

    private GameBalanceConfig() {
    }

    public static final class TempBand {
        public final double belowC;
        public final double mult;

        public TempBand(double belowC, double mult) {
            this.belowC = belowC;
            this.mult = mult;
        }
    }

    public static final class NectarPeak {
        public final int center;
        public final int width;
        public final double height;

        public NectarPeak(int center, int width, double height) {
            this.center = center;
            this.width = width;
            this.height = height;
        }
    }

    public static synchronized void load(Context context) {
        if (context == null) {
            syncLegacyFields();
            return;
        }
        try (InputStream in = context.getAssets().open(ASSET_NAME)) {
            applyJson(new JSONObject(readUtf8(in)));
        } catch (Exception e) {
            Log.w(TAG, "No se pudo leer " + ASSET_NAME + "; se usan valores por defecto", e);
        }
        syncLegacyFields();
    }

    /**
     * Puesta base del día (antes de reina, salud, varroa y temperatura).
     */
    public static double eggsBaseForDay(int dayOfYear) {
        if (eggsDoy != null && eggsBase != null && eggsDoy.length >= 2
                && eggsBase.length >= 2) {
            return lerpDoubleKnots(dayOfYear, eggsDoy, eggsBase);
        }
        Season season = Season.fromDayOfYear(dayOfYear);
        switch (season) {
            case SPRING:
                return eggsSpring;
            case SUMMER:
                return eggsSummer;
            case AUTUMN:
                return eggsAutumn;
            case WINTER:
            default:
                return eggsWinter;
        }
    }

    public static double nectarSeasonMultiplier(IberianClimateZone zone, Season season) {
        return seasonRow(zone, season,
                nectarMultAtlantic, nectarMultMountain, nectarMultMediterranean,
                nectarMultSouth, nectarMultContinental);
    }

    public static double layingSeasonMultiplier(IberianClimateZone zone, Season season) {
        return seasonRow(zone, season,
                layingMultAtlantic, layingMultMountain, layingMultMediterranean,
                layingMultSouth, layingMultContinental);
    }

    private static double seasonRow(
            IberianClimateZone zone,
            Season season,
            double[] atlantic,
            double[] mountain,
            double[] mediterranean,
            double[] south,
            double[] continental) {
        double[] row = continental;
        if (zone != null) {
            switch (zone) {
                case ATLANTIC:
                    row = atlantic;
                    break;
                case MOUNTAIN:
                    row = mountain;
                    break;
                case MEDITERRANEAN:
                    row = mediterranean;
                    break;
                case SOUTH:
                    row = south;
                    break;
                case CONTINENTAL:
                default:
                    row = continental;
                    break;
            }
        }
        if (row == null || row.length < 4) {
            return 1.0;
        }
        if (season == null) {
            return row[1];
        }
        switch (season) {
            case WINTER:
                return row[0];
            case SPRING:
                return row[1];
            case SUMMER:
                return row[2];
            case AUTUMN:
            default:
                return row[3];
        }
    }

    public static List<NectarPeak> peaksForFlora(String canonicalFlora) {
        if (canonicalFlora != null && nectarFlora.containsKey(canonicalFlora)) {
            return nectarFlora.get(canonicalFlora);
        }
        List<NectarPeak> mil = nectarFlora.get("Mil flores");
        return mil != null ? mil : defaultNectarFlora().get("Mil flores");
    }

    public static double lerpIntKnots(int dayOfYear, int[] xs, int[] ys) {
        int d = Math.max(1, Math.min(366, dayOfYear));
        if (xs == null || ys == null || xs.length == 0 || ys.length == 0) {
            return 0;
        }
        int n = Math.min(xs.length, ys.length);
        if (d <= xs[0]) {
            return ys[0];
        }
        for (int i = 0; i < n - 1; i++) {
            int a = xs[i];
            int b = xs[i + 1];
            if (d <= b) {
                double t = (d - a) / (double) Math.max(1, b - a);
                return ys[i] + t * (ys[i + 1] - ys[i]);
            }
        }
        return ys[n - 1];
    }

    public static double lerpDoubleKnots(int dayOfYear, int[] xs, double[] ys) {
        int d = Math.max(1, Math.min(366, dayOfYear));
        if (xs == null || ys == null || xs.length == 0 || ys.length == 0) {
            return 0;
        }
        int n = Math.min(xs.length, ys.length);
        if (d <= xs[0]) {
            return ys[0];
        }
        for (int i = 0; i < n - 1; i++) {
            int a = xs[i];
            int b = xs[i + 1];
            if (d <= b) {
                double t = (d - a) / (double) Math.max(1, b - a);
                return ys[i] + t * (ys[i + 1] - ys[i]);
            }
        }
        return ys[n - 1];
    }

    static void applyJson(JSONObject root) {
        JSONObject pop = root.optJSONObject("population");
        if (pop != null) {
            maxAdultWorkersPerHive = pop.optInt("maxAdultWorkersPerHive", maxAdultWorkersPerHive);
            minBeesCollapse = pop.optInt("minBeesCollapse", minBeesCollapse);
            lambdaTowardK = pop.optDouble("lambdaTowardK", lambdaTowardK);
            lambdaTowardKDown = pop.optDouble("lambdaTowardKDown", lambdaTowardKDown);
            queenDailyDeathP = pop.optDouble("queenDailyDeathP", queenDailyDeathP);
            kDoy = optIntArray(pop, "kDoy", kDoy);
            kAdults = optIntArray(pop, "kAdults", kAdults);
            queenKFactorMin = pop.optDouble("queenKFactorMin", queenKFactorMin);
            queenKFactorSpan = pop.optDouble("queenKFactorSpan", queenKFactorSpan);
            healthKFactorMin = pop.optDouble("healthKFactorMin", healthKFactorMin);
            healthKFactorSpan = pop.optDouble("healthKFactorSpan", healthKFactorSpan);
            varroaKStartPct = pop.optDouble("varroaKStartPct", varroaKStartPct);
            varroaKSpanPct = pop.optDouble("varroaKSpanPct", varroaKSpanPct);
            varroaKMaxPenalty = pop.optDouble("varroaKMaxPenalty", varroaKMaxPenalty);
            notLayingKMultiplier = pop.optDouble("notLayingKMultiplier", notLayingKMultiplier);
            minHoneyKFactorFloor = pop.optDouble("minHoneyKFactorFloor", minHoneyKFactorFloor);
            lowHoneyLifespanFloor = pop.optDouble("lowHoneyLifespanFloor", lowHoneyLifespanFloor);
            feedBroodKCap = pop.optDouble("feedBroodKCap", feedBroodKCap);
            collapsedDailyDropDivisor = pop.optInt("collapsedDailyDropDivisor", collapsedDailyDropDivisor);
            collapsedDailyDropMin = pop.optInt("collapsedDailyDropMin", collapsedDailyDropMin);
            workerLifespanDoy = optIntArray(pop, "workerLifespanDoy", workerLifespanDoy);
            workerLifespanDays = optDoubleArray(pop, "workerLifespanDays", workerLifespanDays);
            workerLifespanClampMin = pop.optDouble("workerLifespanClampMin", workerLifespanClampMin);
            workerLifespanClampMax = pop.optDouble("workerLifespanClampMax", workerLifespanClampMax);
        }
        JSONObject eggs = root.optJSONObject("eggs");
        if (eggs != null) {
            eggsSpring = eggs.optDouble("spring", eggsSpring);
            eggsSummer = eggs.optDouble("summer", eggsSummer);
            eggsAutumn = eggs.optDouble("autumn", eggsAutumn);
            eggsWinter = eggs.optDouble("winter", eggsWinter);
            eggsDoy = optIntArray(eggs, "doy", eggsDoy);
            eggsBase = optDoubleArray(eggs, "base", eggsBase);
            eggsStrengthRefAdults = eggs.optDouble("strengthRefAdults", eggsStrengthRefAdults);
            eggsMaxPerDay = eggs.optInt("maxPerDay", eggsMaxPerDay);
            eggsMaxPerDayRoomBonus = eggs.optInt("maxPerDayRoomBonus", eggsMaxPerDayRoomBonus);
            layNoiseMin = eggs.optDouble("layNoiseMin", layNoiseMin);
            layNoiseSpan = eggs.optDouble("layNoiseSpan", layNoiseSpan);
            eggsLowHoneyFactorFloor = eggs.optDouble("lowHoneyFactorFloor", eggsLowHoneyFactorFloor);
        }
        JSONObject swarm = root.optJSONObject("swarm");
        if (swarm != null) {
            swarmBaseAdults = swarm.optInt("baseAdults", swarmBaseAdults);
            swarmStepAdults = swarm.optInt("stepAdults", swarmStepAdults);
            swarmRiskPerStep = swarm.optDouble("riskPerStep", swarmRiskPerStep);
            swarmDailyCap = swarm.optDouble("dailyCap", swarmDailyCap);
            swarmSeasonStartDoy = swarm.optInt("seasonStartDoy", swarmSeasonStartDoy);
            swarmSeasonEndDoy = swarm.optInt("seasonEndDoy", swarmSeasonEndDoy);
        }
        JSONObject split = root.optJSONObject("split");
        if (split != null) {
            splitMinAdults = split.optInt("minAdults", splitMinAdults);
            splitRecommendAdults = split.optInt("recommendAdults", splitRecommendAdults);
        }
        JSONObject honey = root.optJSONObject("honey");
        if (honey != null) {
            foragerFraction = honey.optDouble("foragerFraction", foragerFraction);
            kgPerForagerFullFlow = honey.optDouble("kgPerForagerFullFlow", kgPerForagerFullFlow);
            consumptionBaseKg = honey.optDouble("consumptionBaseKg", consumptionBaseKg);
            consumptionPerAdultKg = honey.optDouble("consumptionPerAdultKg", consumptionPerAdultKg);
            consumptionPerBroodEqKg = honey.optDouble("consumptionPerBroodEqKg", consumptionPerBroodEqKg);
            syrupConsumptionMultiplier = honey.optDouble("syrupConsumptionMultiplier", syrupConsumptionMultiplier);
            nectarNoiseMin = honey.optDouble("nectarNoiseMin", nectarNoiseMin);
            nectarNoiseSpan = honey.optDouble("nectarNoiseSpan", nectarNoiseSpan);
            minHiveStockKg = honey.optDouble("minHiveStockKg", minHiveStockKg);
            starterHiveStockKg = honey.optDouble("starterHiveStockKg", starterHiveStockKg);
            superCapKg = optDoubleArray(honey, "superCapKg", superCapKg);
            superPurchasePriceEur = honey.optDouble("superPurchasePriceEur", superPurchasePriceEur);
            chartFloraBoost = honey.optDouble("chartFloraBoost", chartFloraBoost);
            chartTempBoost = honey.optDouble("chartTempBoost", chartTempBoost);
        }
        JSONObject health = root.optJSONObject("health");
        if (health != null) {
            varroaCapPct = health.optDouble("varroaCapPct", varroaCapPct);
            varroaBaseRelativeDailyRate = health.optDouble("varroaBaseRelativeDailyRate", varroaBaseRelativeDailyRate);
            varroaLoadAmplifier = health.optDouble("varroaLoadAmplifier", varroaLoadAmplifier);
            varroaReboundRelativeDailyRate = health.optDouble("varroaReboundRelativeDailyRate",
                    varroaReboundRelativeDailyRate);
            varroaEggsRefPerDay = health.optDouble("eggsRefPerDay", varroaEggsRefPerDay);
            varroaBroodMultFloor = health.optDouble("broodMultFloor", varroaBroodMultFloor);
            varroaBroodMultCap = health.optDouble("broodMultCap", varroaBroodMultCap);
            maxHealthDeltaPerDay = health.optInt("maxHealthDeltaPerDay", maxHealthDeltaPerDay);
            honeyHealthPenaltyLowKgExtra = health.optDouble("honeyHealthPenaltyLowKgExtra", honeyHealthPenaltyLowKgExtra);
            honeyHealthPenaltyMidKgExtra = health.optDouble("honeyHealthPenaltyMidKgExtra", honeyHealthPenaltyMidKgExtra);
            healthHoneyLossPerMissingPoint = health.optDouble("healthHoneyLossPerMissingPoint",
                    healthHoneyLossPerMissingPoint);
            healthHoneyFloor = health.optDouble("healthHoneyFloor", healthHoneyFloor);
            summerVarroaSeasonMult = health.optDouble("summerVarroaSeasonMult", summerVarroaSeasonMult);
            autumnVarroaSeasonMult = health.optDouble("autumnVarroaSeasonMult", autumnVarroaSeasonMult);
            winterVarroaSeasonMult = health.optDouble("winterVarroaSeasonMult", winterVarroaSeasonMult);
            mountainVarroaMult = health.optDouble("mountainVarroaMult", mountainVarroaMult);
            highElevVarroaMult = health.optDouble("highElevVarroaMult", highElevVarroaMult);
            highElevVarroaStartM = health.optInt("highElevVarroaStartM", highElevVarroaStartM);
            varroaFreezeC = health.optDouble("varroaFreezeC", varroaFreezeC);
            varroaColdC = health.optDouble("varroaColdC", varroaColdC);
            varroaChillyC = health.optDouble("varroaChillyC", varroaChillyC);
            varroaFreezeMult = health.optDouble("varroaFreezeMult", varroaFreezeMult);
            varroaColdMult = health.optDouble("varroaColdMult", varroaColdMult);
            varroaChillyMult = health.optDouble("varroaChillyMult", varroaChillyMult);
            varroaEnvMultFloor = health.optDouble("varroaEnvMultFloor", varroaEnvMultFloor);
        }
        JSONObject sky = root.optJSONObject("sky");
        if (sky != null) {
            skyMultSun = sky.optDouble("multSun", skyMultSun);
            skyMultCloudy = sky.optDouble("multCloudy", skyMultCloudy);
            skyMultWindy = sky.optDouble("multWindy", skyMultWindy);
            skyMultRain = sky.optDouble("multRain", skyMultRain);
            skyLowElevMaxM = sky.optInt("lowElevMaxM", skyLowElevMaxM);
            skyMidElevMaxM = sky.optInt("midElevMaxM", skyMidElevMaxM);
            skyLowSunEnd = sky.optDouble("lowSunEnd", skyLowSunEnd);
            skyLowCloudEnd = sky.optDouble("lowCloudEnd", skyLowCloudEnd);
            skyLowWindEnd = sky.optDouble("lowWindEnd", skyLowWindEnd);
            skyMidSunEnd = sky.optDouble("midSunEnd", skyMidSunEnd);
            skyMidCloudEnd = sky.optDouble("midCloudEnd", skyMidCloudEnd);
            skyMidWindEnd = sky.optDouble("midWindEnd", skyMidWindEnd);
            skyHighSunEnd = sky.optDouble("highSunEnd", skyHighSunEnd);
            skyHighCloudEnd = sky.optDouble("highCloudEnd", skyHighCloudEnd);
            skyHighWindEnd = sky.optDouble("highWindEnd", skyHighWindEnd);
        }
        JSONArray bands = root.optJSONArray("temperatureBands");
        if (bands != null && bands.length() > 0) {
            List<TempBand> parsed = parseTempBands(bands);
            if (!parsed.isEmpty()) {
                temperatureBands = parsed;
            }
        }
        JSONArray layBands = root.optJSONArray("layingBands");
        if (layBands != null && layBands.length() > 0) {
            List<TempBand> parsedLay = parseTempBands(layBands);
            if (!parsedLay.isEmpty()) {
                layingBands = parsedLay;
            }
        }
        JSONObject bg = root.optJSONObject("nectarBackground");
        if (bg != null) {
            nectarBgStartDoy = bg.optInt("startDoy", nectarBgStartDoy);
            nectarBgEndDoy = bg.optInt("endDoy", nectarBgEndDoy);
            nectarBgBase = bg.optDouble("base", nectarBgBase);
            nectarBgAmp = bg.optDouble("amp", nectarBgAmp);
        }
        JSONObject flora = root.optJSONObject("nectarFlora");
        if (flora != null) {
            Map<String, List<NectarPeak>> map = new LinkedHashMap<>();
            JSONArray names = flora.names();
            if (names != null) {
                for (int i = 0; i < names.length(); i++) {
                    String key = names.optString(i);
                    if (key == null || key.startsWith("_")) {
                        continue;
                    }
                    JSONArray peaks = flora.optJSONArray(key);
                    if (peaks == null) {
                        continue;
                    }
                    List<NectarPeak> list = new ArrayList<>();
                    for (int p = 0; p < peaks.length(); p++) {
                        JSONObject pk = peaks.optJSONObject(p);
                        if (pk == null) {
                            continue;
                        }
                        list.add(new NectarPeak(
                                pk.optInt("center", 180),
                                pk.optInt("width", 24),
                                pk.optDouble("height", 1.0)));
                    }
                    map.put(key, list);
                }
            }
            if (!map.isEmpty()) {
                nectarFlora = map;
            }
        }
        JSONObject climate = root.optJSONObject("climate");
        if (climate != null) {
            mountainMinM = climate.optInt("mountainMinM", mountainMinM);
            bloomShiftAtlantic = climate.optInt("bloomShiftAtlantic", bloomShiftAtlantic);
            bloomShiftMountain = climate.optInt("bloomShiftMountain", bloomShiftMountain);
            bloomShiftMediterranean = climate.optInt("bloomShiftMediterranean", bloomShiftMediterranean);
            bloomShiftSouth = climate.optInt("bloomShiftSouth", bloomShiftSouth);
            bloomShiftContinental = climate.optInt("bloomShiftContinental", bloomShiftContinental);
            JSONObject nsm = climate.optJSONObject("nectarSeasonMult");
            if (nsm != null) {
                nectarMultAtlantic = optSeasonMult(nsm.optJSONObject("ATLANTIC"), nectarMultAtlantic);
                nectarMultMountain = optSeasonMult(nsm.optJSONObject("MOUNTAIN"), nectarMultMountain);
                nectarMultMediterranean = optSeasonMult(nsm.optJSONObject("MEDITERRANEAN"), nectarMultMediterranean);
                nectarMultSouth = optSeasonMult(nsm.optJSONObject("SOUTH"), nectarMultSouth);
                nectarMultContinental = optSeasonMult(nsm.optJSONObject("CONTINENTAL"), nectarMultContinental);
            }
            JSONObject lsm = climate.optJSONObject("layingSeasonMult");
            if (lsm != null) {
                layingMultAtlantic = optSeasonMult(lsm.optJSONObject("ATLANTIC"), layingMultAtlantic);
                layingMultMountain = optSeasonMult(lsm.optJSONObject("MOUNTAIN"), layingMultMountain);
                layingMultMediterranean = optSeasonMult(lsm.optJSONObject("MEDITERRANEAN"), layingMultMediterranean);
                layingMultSouth = optSeasonMult(lsm.optJSONObject("SOUTH"), layingMultSouth);
                layingMultContinental = optSeasonMult(lsm.optJSONObject("CONTINENTAL"), layingMultContinental);
            }
        }
        JSONObject vintage = root.optJSONObject("vintage");
        if (vintage != null) {
            vintageMin = vintage.optDouble("min", vintageMin);
            vintageMax = vintage.optDouble("max", vintageMax);
        }
        JSONObject forage = root.optJSONObject("forage");
        if (forage != null) {
            nativeHiveSlots = forage.optInt("nativeHiveSlots", nativeHiveSlots);
            plantedHiveSlots = forage.optInt("plantedHiveSlots", plantedHiveSlots);
            crowdingFloor = forage.optDouble("crowdingFloor", crowdingFloor);
            secondaryNectarTrigger = forage.optDouble("secondaryNectarTrigger", secondaryNectarTrigger);
            secondaryNectarShare = forage.optDouble("secondaryNectarShare", secondaryNectarShare);
            nectarIntensityCap = forage.optDouble("nectarIntensityCap", nectarIntensityCap);
        }
    }

    static void syncLegacyFields() {
        ColonyGameRules.MAX_ADULT_WORKERS_PER_HIVE = maxAdultWorkersPerHive;
        ColonyGameRules.SWARM_RISK_BASE_BEES = swarmBaseAdults;
        ColonyGameRules.SWARM_RISK_STEP_BEES = swarmStepAdults;
        ColonyGameRules.SWARM_RISK_PER_STEP = swarmRiskPerStep;
        ColonyGameRules.SWARM_RISK_DAILY_CAP = swarmDailyCap;
        ColonyGameRules.MIN_BEES_TO_SPLIT = splitMinAdults;
        ColonyGameRules.SPLIT_RECOMMEND_BEES = splitRecommendAdults;
        HiveHoneyRules.MIN_HIVE_STOCK_KG = minHiveStockKg;
        HiveHoneyRules.STARTER_HIVE_STOCK_KG = starterHiveStockKg;
        HiveHoneyRules.SUPER_PURCHASE_PRICE_EUR = superPurchasePriceEur;
        HiveHoneyRules.SUPER_CAP_KG = superCapKg;
        HivePopulationState.MIN_BEES_COLLAPSE = minBeesCollapse;
        WorkerAdultLifespan.DOY_KNOT = workerLifespanDoy;
        WorkerAdultLifespan.MEAN_LIFE_DAYS = workerLifespanDays;
        HiveCareRules.QUEEN_DAILY_DEATH_P = queenDailyDeathP;
    }

    private static List<TempBand> defaultLayingBands() {
        List<TempBand> list = new ArrayList<>();
        list.add(new TempBand(5, 0.12));
        list.add(new TempBand(8, 0.28));
        list.add(new TempBand(12, 0.62));
        list.add(new TempBand(16, 0.90));
        list.add(new TempBand(28, 1.00));
        list.add(new TempBand(32, 0.82));
        list.add(new TempBand(36, 0.35));
        list.add(new TempBand(40, 0.16));
        list.add(new TempBand(50.0001, 0.08));
        return list;
    }

    private static List<TempBand> parseTempBands(JSONArray bands) {
        List<TempBand> parsed = new ArrayList<>();
        if (bands == null) {
            return parsed;
        }
        for (int i = 0; i < bands.length(); i++) {
            JSONObject b = bands.optJSONObject(i);
            if (b == null) {
                continue;
            }
            parsed.add(new TempBand(b.optDouble("belowC", 10), b.optDouble("mult", 1)));
        }
        return parsed;
    }

    private static List<TempBand> defaultTempBands() {
        List<TempBand> list = new ArrayList<>();
        list.add(new TempBand(10, 0.15));
        list.add(new TempBand(15, 0.40));
        list.add(new TempBand(20, 0.75));
        list.add(new TempBand(25, 1.00));
        list.add(new TempBand(30, 0.90));
        list.add(new TempBand(35, 0.55));
        list.add(new TempBand(40, 0.25));
        list.add(new TempBand(45, 0.10));
        list.add(new TempBand(50.0001, 0.10));
        return list;
    }

    private static Map<String, List<NectarPeak>> defaultNectarFlora() {
        Map<String, List<NectarPeak>> m = new LinkedHashMap<>();
        m.put("Romero", peaks(p(40, 32, 1.00), p(90, 20, 0.30)));
        m.put("Campo de almendros", peaks(p(52, 16, 1.05)));
        m.put("Campo de cerezos", peaks(p(85, 14, 1.00)));
        m.put("Campo de manzanos", peaks(p(102, 16, 0.95)));
        m.put("Campo de perales", peaks(p(98, 15, 0.92)));
        m.put("Campo de Colza", peaks(p(95, 18, 1.05)));
        m.put("Campo de naranjos", peaks(p(118, 22, 1.05)));
        m.put("Tomillo", peaks(p(145, 28, 0.95)));
        m.put("Lavanda", peaks(p(178, 24, 1.10)));
        m.put("Campo de girasoles", peaks(p(200, 18, 1.05)));
        m.put("Bosque", peaks(p(188, 36, 0.80)));
        m.put("Brezo", peaks(p(258, 26, 1.00)));
        m.put("Castaño", peaks(p(185, 22, 1.05)));
        m.put("Eucalipto", peaks(p(25, 18, 0.40), p(200, 28, 1.05)));
        m.put("Mielato de encina y roble", peaks(p(220, 36, 0.88)));
        m.put("Neret", peaks(p(195, 18, 1.00)));
        m.put("Arboç", peaks(p(310, 28, 1.05)));
        m.put("Fynbos", peaks(p(165, 28, 1.05), p(210, 18, 0.45)));
        m.put("Aloe", peaks(p(185, 24, 1.00)));
        m.put("Macadamia", peaks(p(250, 22, 1.02)));
        m.put("Litchi", peaks(p(350, 18, 1.08)));
        m.put("Lucerna", peaks(p(350, 26, 0.92), p(40, 20, 0.55)));
        m.put("Acacia", peaks(p(280, 24, 0.95)));
        m.put("Mil flores", peaks(p(112, 38, 0.72), p(227, 28, 0.88), p(319, 32, 0.92)));
        return m;
    }

    private static NectarPeak p(int c, int w, double h) {
        return new NectarPeak(c, w, h);
    }

    @SafeVarargs
    private static List<NectarPeak> peaks(NectarPeak... items) {
        List<NectarPeak> list = new ArrayList<>();
        for (NectarPeak item : items) {
            list.add(item);
        }
        return list;
    }

    private static double[] optSeasonMult(JSONObject o, double[] fallback) {
        if (o == null) {
            return fallback;
        }
        double[] out = fallback != null && fallback.length >= 4
                ? fallback.clone()
                : new double[] {1.0, 1.0, 1.0, 1.0};
        out[0] = o.optDouble("winter", out[0]);
        out[1] = o.optDouble("spring", out[1]);
        out[2] = o.optDouble("summer", out[2]);
        out[3] = o.optDouble("autumn", out[3]);
        return out;
    }

    private static int[] optIntArray(JSONObject o, String key, int[] fallback) {
        JSONArray a = o.optJSONArray(key);
        if (a == null || a.length() == 0) {
            return fallback;
        }
        int[] out = new int[a.length()];
        for (int i = 0; i < a.length(); i++) {
            out[i] = a.optInt(i);
        }
        return out;
    }

    private static double[] optDoubleArray(JSONObject o, String key, double[] fallback) {
        JSONArray a = o.optJSONArray(key);
        if (a == null || a.length() == 0) {
            return fallback;
        }
        double[] out = new double[a.length()];
        for (int i = 0; i < a.length(); i++) {
            out[i] = a.optDouble(i);
        }
        return out;
    }

    private static String readUtf8(InputStream in) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) >= 0) {
            bos.write(buf, 0, n);
        }
        return new String(bos.toByteArray(), StandardCharsets.UTF_8);
    }
}
