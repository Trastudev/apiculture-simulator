package com.apiculture.simulator.domain.health;

import com.apiculture.simulator.data.local.entity.HiveEntity;
import com.apiculture.simulator.domain.game.HiveHoneyRules;
import com.apiculture.simulator.domain.game.HoneyDailyProduction;
import com.apiculture.simulator.domain.game.Season;

/**
 * Salud diaria (0–100), dinámica de varroa, tratamiento y rebote; inercia ±3 puntos/día.
 * Sin tratamiento, la varroa crece en proporción al nivel actual (crecimiento exponencial en el tiempo),
 * con una tasa relativa que sube al aumentar el % de infestación.
 */
public final class HiveDailyHealthSimulator {

    private static final double VARROA_CAP = 40.0;
    private static final int MAX_HEALTH_DELTA_PER_DAY = 3;
    /**
     * Fracción de crecimiento respecto al nivel actual un día “tipo” (primavera/otoño); verano/invierno escalan aparte.
     * Equivale a la idea de que cada ciclo infecta más hospedadores cuando ya hay muchos ácaros.
     */
    private static final double VARROA_BASE_RELATIVE_DAILY_RATE = 0.026;
    /** A mayor carga (pct / tope), más rápida la multiplicación relativa diaria. */
    private static final double VARROA_LOAD_AMPLIFIER = 2.8;
    /** Tras cortar el tratamiento: subida multiplicativa más contenida que en crecimiento libre. */
    private static final double VARROA_REBOUND_RELATIVE_DAILY_RATE = 0.014;

    private HiveDailyHealthSimulator() {
    }

    public static void applyDay(HiveEntity hive, int dayKey, Season season) {
        if (hive == null) {
            return;
        }
        if (Double.isNaN(hive.varroaPct) || hive.varroaPct < 0) {
            hive.varroaPct = 2.0;
        }
        if (hive.lastHealthSimDayKey == 0 && hive.varroaPct <= 0) {
            hive.varroaPct = 2.0;
        }

        boolean activeTreatmentToday = hive.varroaTreatmentDaysRemaining > 0;
        String vid = hive.id != null ? hive.id : "";

        if (activeTreatmentToday) {
            double u = HoneyDailyProduction.deterministicUniform01(vid + ":vdrop", dayKey);
            double drop = 0.2 + u * 0.3;
            hive.varroaPct = Math.max(0.0, hive.varroaPct - drop);
            hive.varroaTreatmentDaysRemaining--;
            if (hive.varroaTreatmentDaysRemaining == 0) {
                double u2 = HoneyDailyProduction.deterministicUniform01(vid + ":vreb", dayKey);
                hive.varroaReboundDaysRemaining = 5 + (int) Math.floor(u2 * 6);
            }
        } else if (hive.varroaReboundDaysRemaining > 0) {
            double pReb = hive.varroaPct;
            double burdenReb = Math.min(1.0, Math.max(0.0, pReb / VARROA_CAP));
            double relReb = VARROA_REBOUND_RELATIVE_DAILY_RATE * (1.0 + VARROA_LOAD_AMPLIFIER * burdenReb);
            hive.varroaPct = pReb * (1.0 + relReb);
            hive.varroaReboundDaysRemaining--;
        } else {
            double seasonMult = season == Season.SUMMER ? 1.2 : season == Season.WINTER ? 0.75 : 1.0;
            double p = hive.varroaPct;
            double burden = Math.min(1.0, Math.max(0.0, p / VARROA_CAP));
            double relativeRate =
                    VARROA_BASE_RELATIVE_DAILY_RATE * seasonMult * (1.0 + VARROA_LOAD_AMPLIFIER * burden);
            hive.varroaPct = p * (1.0 + relativeRate);
        }

        hive.varroaPct = Math.min(VARROA_CAP, Math.max(0.0, hive.varroaPct));

        double varroaImpact = dailyVarroaHealthPenalty(hive.varroaPct, vid, dayKey);
        double mielImpact = honeyAndReservePenalty(hive);
        double treatmentBonus = activeTreatmentToday ? 0.2 : 0.0;
        double collapseStress = hive.health < 20 ? 0.5 : 0.0;

        double proposed = hive.health - varroaImpact - mielImpact + treatmentBonus - collapseStress;
        proposed = Math.max(0.0, Math.min(100.0, proposed));

        int delta = (int) Math.round(proposed - hive.health);
        delta = Math.max(-MAX_HEALTH_DELTA_PER_DAY, Math.min(MAX_HEALTH_DELTA_PER_DAY, delta));
        int next = hive.health + delta;
        hive.health = Math.max(0, Math.min(100, next));
    }

    private static double dailyVarroaHealthPenalty(double varroaPct, String hiveId, int dayKey) {
        if (varroaPct <= 2.0) {
            return 0.05;
        }
        if (varroaPct <= 5.0) {
            return 0.2;
        }
        if (varroaPct <= 10.0) {
            return 0.5;
        }
        double u = HoneyDailyProduction.deterministicUniform01(hiveId + ":vhp", dayKey);
        return 1.0 + u;
    }

    private static double honeyAndReservePenalty(HiveEntity hive) {
        double p = 0.0;
        double kg = hive.honeyProduction;
        if (kg < HiveHoneyRules.MIN_HIVE_STOCK_KG) {
            p += 0.55;
        } else if (kg < HiveHoneyRules.MIN_HIVE_STOCK_KG + 1.5) {
            p += 0.28;
        } else if (kg < HiveHoneyRules.MIN_HIVE_STOCK_KG + 3.0) {
            p += 0.12;
        }
        int r = hive.reserves;
        if (r < 12) {
            p += 0.35;
        } else if (r < 25) {
            p += 0.2;
        } else if (r < 40) {
            p += 0.08;
        }
        return p;
    }
}
