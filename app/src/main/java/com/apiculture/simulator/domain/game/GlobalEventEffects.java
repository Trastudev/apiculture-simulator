package com.apiculture.simulator.domain.game;

import androidx.annotation.Nullable;

import com.apiculture.simulator.domain.market.HoneyMarketEngine;
import com.apiculture.simulator.domain.market.HoneyMarketSnapshot;
import com.apiculture.simulator.domain.parcel.HexFlora;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Multiplicadores de mercado y ataque de velutina aplicados por eventos globales.
 */
public final class GlobalEventEffects {

    public static final class State {
        public final Map<String, Double> demandMultByFlora;
        public final Map<String, Double> priceMultByFlora;
        public final boolean velutinaActive;
        public final double velutinaLossPercent;
        public final Set<String> velutinaClimateKeys;
        public final String velutinaInstanceId;

        public State(
                Map<String, Double> demandMultByFlora,
                Map<String, Double> priceMultByFlora,
                boolean velutinaActive,
                double velutinaLossPercent,
                Set<String> velutinaClimateKeys,
                String velutinaInstanceId) {
            this.demandMultByFlora = demandMultByFlora != null
                    ? Collections.unmodifiableMap(demandMultByFlora) : Collections.emptyMap();
            this.priceMultByFlora = priceMultByFlora != null
                    ? Collections.unmodifiableMap(priceMultByFlora) : Collections.emptyMap();
            this.velutinaActive = velutinaActive;
            this.velutinaLossPercent = velutinaLossPercent;
            this.velutinaClimateKeys = velutinaClimateKeys != null
                    ? Collections.unmodifiableSet(velutinaClimateKeys) : Collections.emptySet();
            this.velutinaInstanceId = velutinaInstanceId != null ? velutinaInstanceId : "";
        }

        public static State none() {
            return new State(Collections.emptyMap(), Collections.emptyMap(),
                    false, 0, Collections.emptySet(), "");
        }
    }

    private static volatile State current = State.none();

    private GlobalEventEffects() {
    }

    public static State get() {
        State s = current;
        return s != null ? s : State.none();
    }

    public static void set(@Nullable State state) {
        current = state != null ? state : State.none();
    }

    public static HoneyMarketSnapshot applyToMarket(@Nullable HoneyMarketSnapshot snap) {
        if (snap == null) {
            return null;
        }
        State st = get();
        if (st.demandMultByFlora.isEmpty() && st.priceMultByFlora.isEmpty()) {
            return snap;
        }
        Map<String, Double> demand = new LinkedHashMap<>();
        Map<String, Double> prices = new LinkedHashMap<>();
        double total = 0.0;
        for (String flora : HexFlora.FLORA_TYPES) {
            String k = HoneyMarketEngine.canonicalFloraKey(flora);
            double d = snap.demandKgByFlora.getOrDefault(k, 0.0)
                    * st.demandMultByFlora.getOrDefault(k, 1.0);
            d = Math.round(d * 100.0) / 100.0;
            demand.put(k, d);
            total += d;
            double p = snap.priceEurPerKgByFlora.getOrDefault(k, HoneyMarketEngine.MIN_PRICE_EUR_PER_KG)
                    * st.priceMultByFlora.getOrDefault(k, 1.0);
            prices.put(k, Math.round(p * 100.0) / 100.0);
        }
        return new HoneyMarketSnapshot(
                snap.dayKey,
                Math.round(total * 100.0) / 100.0,
                snap.demandSeasonFactor,
                snap.dailyNoiseMultiplier,
                snap.priceTension01,
                demand,
                prices,
                snap.playerCount);
    }
}
