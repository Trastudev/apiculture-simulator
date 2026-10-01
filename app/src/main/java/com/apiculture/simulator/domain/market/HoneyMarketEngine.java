package com.apiculture.simulator.domain.market;

import androidx.annotation.NonNull;

import com.apiculture.simulator.domain.game.ClimateUnlock;
import com.apiculture.simulator.domain.game.GameBalanceConfig;
import com.apiculture.simulator.domain.game.GameCalendar;
import com.apiculture.simulator.domain.game.HoneyDailyProduction;
import com.apiculture.simulator.domain.game.IberianClimateZone;
import com.apiculture.simulator.domain.game.Season;
import com.apiculture.simulator.domain.game.MadagascarClimateZone;
import com.apiculture.simulator.domain.game.SouthernAfricanClimateZone;
import com.apiculture.simulator.domain.map.PlayableMapRegion;
import com.apiculture.simulator.domain.parcel.CropUnlock;
import com.apiculture.simulator.domain.parcel.HexFlora;

import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mercado por territorio. La actividad regional suma colmenas × multiplicador de nivel.
 * Cada tipo tiene un cupo regional amplio y un objetivo de rotación más pequeño para el precio.
 * El precio del día se fija con las ventas del objetivo del día UTC anterior.
 */
public final class HoneyMarketEngine {

    public static final double MIN_PRICE_EUR_PER_KG = 12.0;
    /** Precio base (neutro) de la miel más cara, antes del ±25 % de mercado. */
    public static final double MAX_PRICE_EUR_PER_KG = 18.0;
    /** Tope configurado del recargo/descuento por oferta. */
    public static double supplyPriceSpan() {
        return Math.max(0.0, GameBalanceConfig.honeyMarketSupplyPriceSpan);
    }

    private static final Object ACCESS_LOCK = new Object();
    private static volatile boolean accessCacheReady;
    private static int cachedMaxAccessLevel = 1;
    private static final Map<String, Integer> ACCESS_BY_FLORA = new LinkedHashMap<>();
    private static final Map<String, Double> BASE_PRICE_BY_FLORA = new LinkedHashMap<>();
    private static final Map<String, Double> DEMAND_SHARES = buildDemandShares();

    private HoneyMarketEngine() {
    }

    public static int typicalHivesPerPlayer() {
        return Math.max(1, GameBalanceConfig.honeyMarketTypicalHivesPerPlayer);
    }

    public static HoneyMarketSnapshot computeSnapshot(int dayKey, int dayOfYear) {
        return computeSnapshot(dayKey, dayOfYear, 1);
    }

    public static HoneyMarketSnapshot computeSnapshot(int dayKey, int dayOfYear, int playerCount) {
        int players = Math.max(1, playerCount);
        return computeSnapshot(dayKey, dayOfYear, PlayableMapRegion.IBERIA,
                (double) players * typicalHivesPerPlayer(), players);
    }

    public static HoneyMarketSnapshot computeSnapshot(int dayKey, int dayOfYear,
            @NonNull PlayableMapRegion region, int activityUnits, int playerCount) {
        return computeSnapshot(dayKey, dayOfYear, region, (double) activityUnits, playerCount);
    }

    public static HoneyMarketSnapshot computeSnapshot(int dayKey, int dayOfYear,
            @NonNull PlayableMapRegion region, double activityUnits, int playerCount) {
        int players = Math.max(1, playerCount);
        double activity = Math.max(1.0, activityUnits);
        Season season = TerritorialMarketRules.seasonFor(dayOfYear, region);
        double demandSeason = demandSeasonFactor(season);
        double noise = dailyNoiseMultiplier(dayKey);
        double priceTension = clamp01(priceSeasonTension01(season) * noise);

        Map<String, Double> shares = TerritorialMarketRules.demandShares(region);
        double maxShare = 0.0;
        for (double sh : shares.values()) {
            maxShare = Math.max(maxShare, sh);
        }
        if (maxShare < 1e-6) {
            maxShare = 1.0;
        }

        Map<String, Double> demandByFlora = new LinkedHashMap<>();
        Map<String, Double> turnoverByFlora = new LinkedHashMap<>();
        double totalDemandKg = 0.0;
        for (String flora : HexFlora.FLORA_TYPES) {
            String k = canonicalFloraKey(flora);
            double sh = shares.getOrDefault(k, 0.0);
            double relative = Math.max(
                    Math.max(0.0, GameBalanceConfig.honeyMarketMinFloraDemandVsTop),
                    sh / maxShare);
            double capacity = activity
                    * HoneyMarketDemandRules.capacityKgPerActivityUnit(relative)
                    * demandSeason * noise;
            double turnover = activity
                    * HoneyMarketDemandRules.turnoverKgPerActivityUnit(relative)
                    * demandSeason * noise;
            double kg = round2(Math.max(1.0, capacity));
            double target = round2(Math.max(1.0, turnover));
            demandByFlora.put(k, kg);
            turnoverByFlora.put(k, target);
            totalDemandKg += kg;
        }

        Map<String, Double> prices = new LinkedHashMap<>();
        for (String flora : HexFlora.FLORA_TYPES) {
            prices.put(flora, priceCeilingEurPerKgForFlora(flora));
        }

        return new HoneyMarketSnapshot(
                dayKey, round2(totalDemandKg), demandSeason, noise, priceTension,
                demandByFlora, turnoverByFlora, prices, players, activity);
    }

    /** Modula la demanda base: &gt;1 en frío, &lt;1 en primavera–verano. */
    public static double demandSeasonFactor(Season season) {
        switch (season) {
            case WINTER:
                return GameBalanceConfig.honeyMarketDemandWinter;
            case AUTUMN:
                return GameBalanceConfig.honeyMarketDemandAutumn;
            case SPRING:
                return GameBalanceConfig.honeyMarketDemandSpring;
            case SUMMER:
            default:
                return GameBalanceConfig.honeyMarketDemandSummer;
        }
    }

    /** Base 0–1 antes del ruido; otoño–invierno más cara, primavera–verano más barata. */
    public static double priceSeasonTension01(Season season) {
        switch (season) {
            case WINTER:
                return 0.97;
            case AUTUMN:
                return 0.90;
            case SPRING:
                return 0.58;
            case SUMMER:
            default:
                return 0.48;
        }
    }

    /** Ruido diario determinista ~ ±10% respecto a 1. */
    public static double dailyNoiseMultiplier(int dayKey) {
        double u = HoneyDailyProduction.deterministicUniform01("globalHoneyMarketNoise", dayKey);
        double min = Math.max(0.0, GameBalanceConfig.honeyMarketDailyNoiseMin);
        double max = Math.max(min, GameBalanceConfig.honeyMarketDailyNoiseMax);
        return min + u * (max - min);
    }

    public static String canonicalFloraKey(String floraType) {
        return HexFlora.canonicalKey(floraType);
    }

    /**
     * Precio publicado ese día: base de temporada de {@code daySnap} × oferta/demanda
     * del día anterior ({@code previousDaySnap} + kg vendidos ayer).
     */
    public static double postedDailyPriceEurPerKg(
            String floraType,
            HoneyMarketSnapshot daySnap,
            HoneyMarketSnapshot previousDaySnap,
            double previousDaySoldKg) {
        String k = canonicalFloraKey(floraType);
        double base = 12.0;
        if (daySnap != null) {
            base = daySnap.priceForFloraOrDefault(k, 12.0);
        }
        double turnoverTargetPrev = 0.0;
        if (previousDaySnap != null) {
            turnoverTargetPrev = previousDaySnap.turnoverTargetForFloraOrDefault(k, 0.0);
        }
        return priceEurPerKgFromSupply(k, turnoverTargetPrev, previousDaySoldKg, base);
    }

    /**
     * Serie de 7 días (el último es hoy UTC): cada punto usa la venta del día anterior.
     *
     * @param soldKgByDay kg vendidos ese {@code dayKey} para este tipo; ausencia = 0.
     */
    public static double[] last7PostedPricesEurPerKg(
            String floraType,
            int todayKey,
            int playerCount,
            Map<Integer, Double> soldKgByDay) {
        String k = canonicalFloraKey(floraType);
        Map<Integer, Map<String, Double>> nested = new LinkedHashMap<>();
        if (soldKgByDay != null) {
            for (Map.Entry<Integer, Double> e : soldKgByDay.entrySet()) {
                if (e.getKey() == null || e.getValue() == null) {
                    continue;
                }
                Map<String, Double> one = new LinkedHashMap<>();
                one.put(k, e.getValue());
                nested.put(e.getKey(), one);
            }
        }
        Map<String, double[]> all = last7PostedPricesByFlora(todayKey, playerCount, nested);
        double[] series = all.get(k);
        return series != null ? series : new double[7];
    }

    /**
     * Un cálculo para todas las mieles: 8 snapshots (hoy y 7 días atrás) reutilizados.
     */
    public static Map<String, double[]> last7PostedPricesByFlora(
            int todayKey,
            int playerCount,
            Map<Integer, Map<String, Double>> soldKgByDayAndFlora) {
        int players = Math.max(1, playerCount);
        return last7PostedPricesByFlora(todayKey, PlayableMapRegion.IBERIA,
                (double) players * typicalHivesPerPlayer(), players, soldKgByDayAndFlora);
    }

    public static Map<String, double[]> last7PostedPricesByFlora(
            int todayKey,
            @NonNull PlayableMapRegion region,
            int activityUnits,
            int playerCount,
            Map<Integer, Map<String, Double>> soldKgByDayAndFlora) {
        return last7PostedPricesByFlora(todayKey, region,
                (double) activityUnits, playerCount, soldKgByDayAndFlora);
    }

    public static Map<String, double[]> last7PostedPricesByFlora(
            int todayKey,
            @NonNull PlayableMapRegion region,
            double activityUnits,
            int playerCount,
            Map<Integer, Map<String, Double>> soldKgByDayAndFlora) {
        int players = Math.max(1, playerCount);
        double activity = Math.max(1.0, activityUnits);
        LocalDate today = GameCalendar.fromDayKey(todayKey);
        Map<Integer, HoneyMarketSnapshot> snaps = new LinkedHashMap<>();
        for (int i = 0; i <= 7; i++) {
            LocalDate d = today.minusDays(i);
            int key = GameCalendar.toDayKey(d);
            snaps.put(key, computeSnapshot(key, d.getDayOfYear(), region, activity, players));
        }
        Map<Integer, Map<String, Double>> sold =
                soldKgByDayAndFlora != null ? soldKgByDayAndFlora : Collections.emptyMap();
        Map<String, double[]> out = new LinkedHashMap<>();
        for (String flora : HexFlora.FLORA_TYPES) {
            String k = canonicalFloraKey(flora);
            double[] series = new double[7];
            for (int i = 0; i < 7; i++) {
                LocalDate day = today.minusDays(6 - i);
                LocalDate prev = day.minusDays(1);
                int prevKey = GameCalendar.toDayKey(prev);
                double soldPrev = 0.0;
                Map<String, Double> daySold = sold.get(prevKey);
                if (daySold != null) {
                    soldPrev = daySold.getOrDefault(k, 0.0);
                }
                series[i] = postedDailyPriceEurPerKg(
                        k,
                        snaps.get(GameCalendar.toDayKey(day)),
                        snaps.get(prevKey),
                        soldPrev);
            }
            out.put(k, series);
        }
        return out;
    }

    /**
     * 0 kg en oferta → +25 %; oferta = demanda de los N jugadores → 0 %;
     * oferta ≥ 2× demanda → −25 %.
     */
    public static double supplyPriceMultiplier(double demandKg, double soldKg) {
        if (demandKg <= 1e-6) {
            return 1.0 + supplyPriceSpan();
        }
        double ratio = soldKg / demandKg;
        double clamped = Math.max(0.0, Math.min(2.0, ratio));
        return 1.0 + supplyPriceSpan() * (1.0 - clamped);
    }

    public static int supplyPriceAdjustmentPercent(double demandKg, double soldKg) {
        return (int) Math.round((supplyPriceMultiplier(demandKg, soldKg) - 1.0) * 100.0);
    }

    /**
     * Precio de temporada × multiplicador por oferta (tope configurable).
     */
    public static double priceEurPerKgFromSupply(
            String floraType, double demandKg, double soldKg, double seasonalBaseEurPerKg) {
        double base = seasonalBaseEurPerKg;
        if (base <= 1e-6) {
            base = priceCeilingEurPerKgForFlora(floraType);
        }
        return round2(base * supplyPriceMultiplier(demandKg, soldKg));
    }

    /**
     * Compatibilidad: sin precio de temporada, usa el techo del tipo como base.
     */
    public static double priceEurPerKgFromGlobalCoverage(
            String floraType, double demandKg, double soldKgGlobally) {
        return priceEurPerKgFromSupply(
                floraType, demandKg, soldKgGlobally, priceCeilingEurPerKgForFlora(floraType));
    }

    private static Map<String, Double> buildDemandShares() {
        Map<String, Double> m = new LinkedHashMap<>();
        m.put("Mil flores", 0.14);
        m.put("Campo de naranjos", 0.08);
        m.put("Romero", 0.07);
        m.put("Lavanda", 0.07);
        m.put("Tomillo", 0.06);
        m.put("Brezo", 0.06);
        m.put("Bosque", 0.05);
        m.put("Castaño", 0.05);
        m.put("Eucalipto", 0.04);
        m.put("Mielato de encina y roble", 0.04);
        m.put("Campo de girasoles", 0.03);
        m.put("Campo de Colza", 0.03);
        m.put("Campo de lavanda", 0.02);
        m.put("Campo de mostaza", 0.015);
        m.put("Campo de trébol", 0.02);
        m.put("Campo de facelia", 0.015);
        m.put("Campo de rabaniza", 0.015);
        m.put("Arboç", 0.03);
        m.put("Campo de manzanos", 0.025);
        m.put("Campo de cerezos", 0.025);
        m.put("Neret", 0.02);
        m.put("Campo de perales", 0.015);
        m.put("Campo de almendros", 0.015);
        m.put("Fynbos", 0.035);
        m.put("Aloe", 0.025);
        m.put("Macadamia", 0.025);
        m.put("Litchi", 0.025);
        m.put("Lucerna", 0.02);
        m.put("Acacia", 0.02);
        m.put("Buchu", 0.02);
        m.put("Protea", 0.025);
        m.put("Boekenhout", 0.015);
        m.put("Aguacate", 0.025);
        m.put("Marula", 0.02);
        return Collections.unmodifiableMap(m);
    }

    /** Precio base €/kg: 12 en la miel más accesible, 18 en la de mayor nivel. */
    public static double priceCeilingEurPerKgForFlora(String floraType) {
        ensureAccessCache();
        String k = canonicalFloraKey(floraType);
        Double p = BASE_PRICE_BY_FLORA.get(k);
        return p != null ? p : MIN_PRICE_EUR_PER_KG;
    }

    /**
     * Nivel mínimo para obtener esa miel: cultivo del catálogo o el clima más temprano
     * donde aparece en silvestre.
     */
    public static int accessLevelForFlora(String floraType) {
        ensureAccessCache();
        String k = canonicalFloraKey(floraType);
        Integer lvl = ACCESS_BY_FLORA.get(k);
        return lvl != null ? lvl : 0;
    }

    /** Si el jugador ya puede producir / vender esa miel por nivel. */
    public static boolean playerCanAccessFlora(String floraType, int playerLevel) {
        return Math.max(0, playerLevel) >= accessLevelForFlora(floraType);
    }

    private static void ensureAccessCache() {
        if (accessCacheReady) {
            return;
        }
        synchronized (ACCESS_LOCK) {
            if (accessCacheReady) {
                return;
            }
            int max = 1;
            ACCESS_BY_FLORA.clear();
            BASE_PRICE_BY_FLORA.clear();
            for (String flora : HexFlora.FLORA_TYPES) {
                String k = canonicalFloraKey(flora);
                int lvl = computeAccessLevelUncached(k);
                ACCESS_BY_FLORA.put(k, lvl);
                max = Math.max(max, lvl);
            }
            cachedMaxAccessLevel = Math.max(1, max);
            for (String flora : HexFlora.FLORA_TYPES) {
                String k = canonicalFloraKey(flora);
                int lvl = ACCESS_BY_FLORA.getOrDefault(k, 0);
                double t = Math.min(1.0, lvl / (double) cachedMaxAccessLevel);
                BASE_PRICE_BY_FLORA.put(k,
                        round2(MIN_PRICE_EUR_PER_KG + (MAX_PRICE_EUR_PER_KG - MIN_PRICE_EUR_PER_KG) * t));
            }
            accessCacheReady = true;
        }
    }

    private static int computeAccessLevelUncached(String canonicalKey) {
        if (canonicalKey == null || canonicalKey.isEmpty()) {
            return 0;
        }
        int min = Integer.MAX_VALUE;
        if (HexFlora.isPlantation(canonicalKey)) {
            min = Math.min(min, Math.max(0, CropUnlock.requiredLevel(canonicalKey)));
        }
        for (IberianClimateZone zone : IberianClimateZone.values()) {
            if (nativeContains(HexFlora.nativePoolForZone(zone), canonicalKey)) {
                min = Math.min(min, ClimateUnlock.minLevel(zone));
            }
        }
        for (SouthernAfricanClimateZone zone : SouthernAfricanClimateZone.values()) {
            if (nativeContains(HexFlora.nativePoolForZone(zone), canonicalKey)) {
                min = Math.min(min, ClimateUnlock.minLevel(zone));
            }
        }
        for (MadagascarClimateZone zone : MadagascarClimateZone.values()) {
            if (nativeContains(HexFlora.nativePoolForZone(zone), canonicalKey)) {
                min = Math.min(min, ClimateUnlock.minLevel(zone));
            }
        }
        return min == Integer.MAX_VALUE ? 0 : min;
    }

    private static boolean nativeContains(java.util.List<String> pool, String canonical) {
        if (pool == null) {
            return false;
        }
        for (String s : pool) {
            if (canonical.equals(canonicalFloraKey(s))) {
                return true;
            }
        }
        return false;
    }

    private static double clamp01(double x) {
        return Math.max(0.0, Math.min(1.0, x));
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
