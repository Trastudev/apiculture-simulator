package com.apiculture.simulator.domain.market;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.apiculture.simulator.domain.game.GameBalanceConfig;
import com.apiculture.simulator.domain.game.Hemispheres;
import com.apiculture.simulator.domain.game.Season;
import com.apiculture.simulator.domain.map.PlayableMapRegion;
import com.apiculture.simulator.domain.map.ProvincialMarket;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Perfil territorial: cuota regional, serialización de ventas y competencia/precio local.
 */
public final class TerritorialMarketRules {

    private TerritorialMarketRules() {
    }

    @NonNull
    public static Map<String, Double> demandShares(@Nullable PlayableMapRegion region) {
        if (region == PlayableMapRegion.SOUTH_AFRICA) {
            return ZA_SHARES;
        }
        if (region == PlayableMapRegion.MADAGASCAR) {
            return MDG_SHARES;
        }
        return IBERIA_SHARES;
    }

    public static int seasonDayOfYear(int calendarDayOfYear, @Nullable PlayableMapRegion region) {
        if (region == PlayableMapRegion.SOUTH_AFRICA || region == PlayableMapRegion.MADAGASCAR) {
            return Hemispheres.biologicalDayOfYear(calendarDayOfYear, -1.0);
        }
        return Math.max(1, Math.min(366, calendarDayOfYear));
    }

    public static Season seasonFor(int calendarDayOfYear, @Nullable PlayableMapRegion region) {
        return Season.fromDayOfYear(seasonDayOfYear(calendarDayOfYear, region));
    }

    public static double localMarkup01(int population, @Nullable List<ProvincialMarket> all) {
        int min = Integer.MAX_VALUE;
        int max = 0;
        if (all != null) {
            for (ProvincialMarket m : all) {
                if (m == null || !m.local || m.international) {
                    continue;
                }
                int p = Math.max(1, m.population);
                min = Math.min(min, p);
                max = Math.max(max, p);
            }
        }
        double minMarkup = Math.max(0.0, GameBalanceConfig.honeyMarketLocalMarkupMin);
        double maxMarkup = Math.max(minMarkup, GameBalanceConfig.honeyMarketLocalMarkupMax);
        if (max <= 0 || min == Integer.MAX_VALUE || max <= min) {
            return (minMarkup + maxMarkup) / 2.0;
        }
        int pop = Math.max(min, Math.min(max, Math.max(1, population)));
        double t = (pop - min) / (double) (max - min);
        return maxMarkup - (maxMarkup - minMarkup) * t;
    }

    /** 0–1 dentro del mismo tipo de mercado (capital o local). */
    public static double populationScore01(@Nullable ProvincialMarket market,
            @Nullable List<ProvincialMarket> all) {
        if (market == null || market.international || all == null || all.isEmpty()) {
            return 0.0;
        }
        int max = 0;
        for (ProvincialMarket m : all) {
            if (m != null && !m.international && m.local == market.local) {
                max = Math.max(max, Math.max(1, m.population));
            }
        }
        if (max <= 0) {
            return 0.0;
        }
        return clamp01(Math.sqrt(Math.max(1, market.population) / (double) max));
    }

    /** Popularidad relativa 0–1 dentro de la región. */
    public static double floraPopularity01(@Nullable PlayableMapRegion region,
            @Nullable String floraCanonical) {
        Map<String, Double> shares = demandShares(region);
        if (shares == null || shares.isEmpty() || floraCanonical == null) {
            return 0.0;
        }
        String key = HoneyMarketEngine.canonicalFloraKey(floraCanonical);
        double max = 0.0;
        for (double share : shares.values()) {
            max = Math.max(max, share);
        }
        if (max <= 1e-9) {
            return 0.0;
        }
        return clamp01(shares.getOrDefault(key, 0.0) / max);
    }

    /** Competencia 0–1: tamaño del mercado y popularidad de la miel. */
    public static double competition01(@Nullable ProvincialMarket market,
            @Nullable String floraCanonical,
            @Nullable List<ProvincialMarket> all) {
        if (market == null || market.international) {
            return 0.0;
        }
        double populationWeight = Math.max(0.0, GameBalanceConfig.honeyMarketCompetitionPopulationWeight);
        double floraWeight = Math.max(0.0, GameBalanceConfig.honeyMarketCompetitionFloraWeight);
        double totalWeight = populationWeight + floraWeight;
        if (totalWeight <= 1e-9) {
            return 0.0;
        }
        return clamp01((populationScore01(market, all) * populationWeight
                + floraPopularity01(market.region, floraCanonical) * floraWeight) / totalWeight);
    }

    /** Volumen relativo aproximado; la cuota real es regional y compartida. */
    public static double volumeScore01(@Nullable ProvincialMarket market,
            @Nullable String floraCanonical,
            @Nullable List<ProvincialMarket> all) {
        if (market == null || market.international) {
            return 0.0;
        }
        return clamp01(populationScore01(market, all)
                * floraPopularity01(market.region, floraCanonical));
    }

    public static double priceAdjustmentAtMarket(@Nullable ProvincialMarket market,
            @Nullable String floraCanonical,
            @Nullable List<ProvincialMarket> all) {
        if (market == null || market.international) {
            return 0.0;
        }
        double localMarkup = market.local ? localMarkup01(market.population, all) : 0.0;
        double penalty = Math.max(0.0, GameBalanceConfig.honeyMarketCompetitionPricePenaltyMax)
                * competition01(market, floraCanonical, all);
        return localMarkup - penalty;
    }

    public static double priceAtMarket(double capitalPriceEur, @Nullable ProvincialMarket market,
            @Nullable List<ProvincialMarket> all) {
        double base = Math.max(0.0, capitalPriceEur);
        if (market == null || !market.local) {
            return round2(base);
        }
        return round2(base * (1.0 + localMarkup01(market.population, all)));
    }

    public static double priceAtMarket(double capitalPriceEur, @Nullable ProvincialMarket market,
            @Nullable String floraCanonical, @Nullable List<ProvincialMarket> all) {
        double base = Math.max(0.0, capitalPriceEur);
        if (market == null || market.international) {
            return round2(base);
        }
        return round2(base * (1.0 + priceAdjustmentAtMarket(market, floraCanonical, all)));
    }

    @NonNull
    public static String salesKey(@Nullable PlayableMapRegion region, @Nullable String floraCanonical) {
        String r = region != null ? region.prefsValue() : PlayableMapRegion.IBERIA.prefsValue();
        String f = floraCanonical != null ? floraCanonical : "Mil flores";
        return r + "__" + f;
    }

    @NonNull
    public static String marketSalesKey(@Nullable PlayableMapRegion region,
            @Nullable ProvincialMarket market, @Nullable String floraCanonical) {
        String id = market != null ? market.salesId() : "";
        return salesKey(region, floraCanonical) + "@@" + id;
    }

    @Nullable
    public static String floraFromSalesKey(@Nullable String docId) {
        if (docId == null || docId.isEmpty()) {
            return null;
        }
        int at = docId.indexOf("@@");
        String body = at < 0 ? docId : docId.substring(0, at);
        int i = body.indexOf("__");
        if (i < 0) {
            return body;
        }
        return body.substring(i + 2);
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private static final Map<String, Double> IBERIA_SHARES = shares(
            "Mil flores", 0.14,
            "Campo de naranjos", 0.08,
            "Romero", 0.07,
            "Lavanda", 0.07,
            "Tomillo", 0.06,
            "Brezo", 0.06,
            "Bosque", 0.05,
            "Castaño", 0.05,
            "Eucalipto", 0.04,
            "Mielato de encina y roble", 0.04,
            "Campo de girasoles", 0.03,
            "Campo de Colza", 0.03,
            "Arboç", 0.03,
            "Campo de manzanos", 0.025,
            "Campo de cerezos", 0.025,
            "Campo de lavanda", 0.02,
            "Campo de trébol", 0.02,
            "Neret", 0.02,
            "Campo de mostaza", 0.015,
            "Campo de facelia", 0.015,
            "Campo de rabaniza", 0.015,
            "Campo de perales", 0.015,
            "Campo de almendros", 0.015);

    private static final Map<String, Double> ZA_SHARES = shares(
            "Mil flores", 0.12,
            "Fynbos", 0.12,
            "Eucalipto", 0.10,
            "Aloe", 0.08,
            "Protea", 0.07,
            "Acacia", 0.07,
            "Macadamia", 0.06,
            "Litchi", 0.06,
            "Aguacate", 0.06,
            "Buchu", 0.05,
            "Marula", 0.05,
            "Lucerna", 0.05,
            "Boekenhout", 0.04,
            "Campo de girasoles", 0.04,
            "Campo de Colza", 0.03,
            "Campo de naranjos", 0.03);

    private static final Map<String, Double> MDG_SHARES = shares(
            "Mil flores", 0.14,
            "Litchi", 0.10,
            "Girofle", 0.10,
            "Café", 0.09,
            "Mango", 0.08,
            "Eucalipto", 0.07,
            "Ravintsara", 0.07,
            "Tapia", 0.06,
            "Niaouli", 0.06,
            "Longose", 0.05,
            "Tamarindo", 0.05,
            "Baobab", 0.05,
            "Campo de naranjos", 0.04,
            "Mangle", 0.04,
            "Raketa", 0.04,
            "Jujube", 0.03,
            "Sisal", 0.03);

    private static Map<String, Double> shares(Object... pairs) {
        Map<String, Double> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            m.put((String) pairs[i], (Double) pairs[i + 1]);
        }
        return Collections.unmodifiableMap(m);
    }
}
