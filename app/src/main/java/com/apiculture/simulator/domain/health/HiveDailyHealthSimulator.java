package com.apiculture.simulator.domain.health;

import com.apiculture.simulator.data.local.entity.HiveEntity;
import com.apiculture.simulator.domain.game.GameBalanceConfig;
import com.apiculture.simulator.domain.game.HiveCareRules;
import com.apiculture.simulator.domain.game.HiveHoneyRules;
import com.apiculture.simulator.domain.game.HoneyDailyProduction;
import com.apiculture.simulator.domain.game.Season;
import com.apiculture.simulator.domain.game.TemperatureStress;
import com.apiculture.simulator.domain.game.TranshumanceRules;

/**
 * Salud diaria (0–100), varroa, miel/reservas y estrés térmico; inercia limitada por día.
 * Sin tratamiento, la varroa crece en proporción a la puesta (cría operculada).
 * En invierno, con casi 0 huevos, la reproducción es residual. Con tratamiento
 * activo no hay multiplicación: baja cada día. El jugador decide cuándo tratar.
 */
public final class HiveDailyHealthSimulator {

    private HiveDailyHealthSimulator() {
    }

    public static void applyDay(HiveEntity hive, int dayKey, Season season) {
        applyDay(hive, dayKey, season, null, 0);
    }

    public static void applyDay(HiveEntity hive, int dayKey, Season season, Double tempCelsius) {
        applyDay(hive, dayKey, season, tempCelsius, 0);
    }

    public static void applyDay(
            HiveEntity hive, int dayKey, Season season, Double tempCelsius, int eggsLaid) {
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
        double varroaCap = GameBalanceConfig.varroaCapPct;
        double loadAmp = GameBalanceConfig.varroaLoadAmplifier;

        if (activeTreatmentToday) {
            hive.varroaPct = hive.varroaPct * HiveCareRules.TREAT_VARROA_KEEP_FACTOR;
            hive.varroaTreatmentDaysRemaining--;
            if (hive.varroaTreatmentDaysRemaining == 0) {
                hive.varroaReboundDaysRemaining = 0;
            }
        } else if (hive.varroaReboundDaysRemaining > 0) {
            double pReb = hive.varroaPct;
            double burdenReb = Math.min(1.0, Math.max(0.0, pReb / varroaCap));
            double env = VarroaGrowth.environmentMultiplier(hive, season, tempCelsius);
            double brood = VarroaGrowth.broodMultiplier(eggsLaid);
            double relReb = GameBalanceConfig.varroaReboundRelativeDailyRate * env * brood
                    * (1.0 + loadAmp * burdenReb);
            hive.varroaPct = pReb * (1.0 + relReb);
            hive.varroaReboundDaysRemaining--;
        } else {
            double p = hive.varroaPct;
            double burden = Math.min(1.0, Math.max(0.0, p / varroaCap));
            double relativeRate = VarroaGrowth.relativeDailyRate(hive, season, tempCelsius,
                    GameBalanceConfig.varroaBaseRelativeDailyRate, eggsLaid)
                    * (1.0 + loadAmp * burden);
            hive.varroaPct = p * (1.0 + relativeRate);
        }

        hive.varroaPct = Math.min(varroaCap, Math.max(0.0, hive.varroaPct));

        double varroaImpact = dailyVarroaHealthPenalty(hive.varroaPct, vid, dayKey);
        double mielImpact = honeyAndReservePenalty(hive);
        double treatmentBonus = activeTreatmentToday ? 0.2 : 0.0;
        double collapseStress = hive.health < 20 ? 0.5 : 0.0;
        double tempDelta = TemperatureStress.dailyHealthDelta(tempCelsius);
        if (TranshumanceRules.isInTransit(hive, dayKey)) {
            tempDelta -= 0.6;
        }

        double proposed = hive.health - varroaImpact - mielImpact + treatmentBonus - collapseStress + tempDelta;
        proposed = Math.max(0.0, Math.min(100.0, proposed));

        int delta = (int) Math.round(proposed - hive.health);
        int maxDelta = GameBalanceConfig.maxHealthDeltaPerDay;
        delta = Math.max(-maxDelta, Math.min(maxDelta, delta));
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
