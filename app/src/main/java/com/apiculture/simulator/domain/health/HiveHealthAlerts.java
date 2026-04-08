package com.apiculture.simulator.domain.health;

import com.apiculture.simulator.data.local.entity.HiveEntity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Alertas de salud / varroa / reservas para la UI.
 */
public final class HiveHealthAlerts {

    private HiveHealthAlerts() {
    }

    public static List<String> alertsEs(HiveEntity h) {
        if (h == null) {
            return Collections.emptyList();
        }
        List<String> out = new ArrayList<>();
        double v = h.varroaPct;
        if (v >= 10) {
            out.add("Infestación crítica de varroa");
        } else if (v >= 5) {
            out.add("Infestación alta (varroa)");
        } else if (v >= 2.5) {
            out.add("Infestación moderada (varroa)");
        }
        if (h.honeyProduction < com.apiculture.simulator.domain.game.HiveHoneyRules.MIN_HIVE_STOCK_KG) {
            out.add("Reservas de miel bajo el mínimo (3 kg)");
        }
        if (h.reserves < 18) {
            out.add("Reservas de pienso bajas");
        }
        if (h.varroaTreatmentDaysRemaining <= 0
                && h.varroaReboundDaysRemaining > 0) {
            out.add("Rebote de varroa tras tratamiento");
        }
        if (h.varroaTreatmentDaysRemaining == 0 && v >= 6 && h.health < 70) {
            out.add("Tratamiento antivarroa recomendado");
        }
        return out;
    }

    public static String alertsSummaryLineEs(HiveEntity h) {
        List<String> a = alertsEs(h);
        if (a.isEmpty()) {
            return "Sin alertas";
        }
        return String.join(" · ", a);
    }

    public static String formatVarroaPct(HiveEntity h) {
        if (h == null) {
            return "—";
        }
        return String.format(Locale.getDefault(), "%.1f %%", h.varroaPct);
    }
}
