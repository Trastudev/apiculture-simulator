package com.apiculture.simulator.unity;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.apiculture.simulator.ApicultureApp;
import com.apiculture.simulator.data.repository.WorkshopStore;
import com.apiculture.simulator.domain.workshop.WorkshopRules;
import com.apiculture.simulator.domain.workshop.WorkshopRules.Format;
import com.apiculture.simulator.domain.workshop.WorkshopRules.Machine;
import com.apiculture.simulator.domain.workshop.WorkshopState;
import com.apiculture.simulator.presentation.hive.HiveSiteSummaryUi;
import com.apiculture.simulator.presentation.market.WorkshopFormatUi;
import com.apiculture.simulator.presentation.workshop.WorkshopUi;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Obrador para la escena 3D (clase C# Apiario.WorkshopSnapshot). Los textos ya van en el idioma
 * de la partida; Unity solo dibuja y cuenta el tiempo hasta {@code endAt}.
 */
final class UnityWorkshopJson {

    private UnityWorkshopJson() {
    }

    @NonNull
    static String build(@Nullable Context app, @Nullable String ownerId, @Nullable String hexId, @NonNull String name, int session,
            @NonNull String lang, @NonNull Context text) throws JSONException {
        if (app == null) {
            return "";
        }
        WorkshopState s = WorkshopStore.get(app, ownerId, hexId);
        JSONObject root = new JSONObject();
        root.put("session", session);
        root.put("lang", lang);
        root.put("name", name);
        root.put("built", s.built());
        root.put("nowMs", System.currentTimeMillis());
        root.put("balance", ((ApicultureApp) app).getEconomyRepository().getBalance());
        root.put("waxKg", s.waxKg);
        root.put("waxPrice", WorkshopRules.WAX_PRICE_B_PER_KG);

        JSONArray machines = new JSONArray();
        for (Machine m : Machine.values()) {
            int level = s.level(m);
            JSONObject o = new JSONObject();
            o.put("key", m.name());
            o.put("name", WorkshopUi.machineName(text, m));
            o.put("level", level);
            o.put("maxLevel", WorkshopRules.upgradable(m) ? WorkshopRules.MAX_LEVEL : 1);
            o.put("slots", m == Machine.RECEPTION ? 0 : WorkshopRules.slots(m, level));
            // Tienda de Toni: precio del siguiente paso (comprar o mejorar) y qué cambia.
            int max = WorkshopRules.upgradable(m) ? WorkshopRules.MAX_LEVEL : 1;
            int price = level >= max ? 0 : level == 0 ? WorkshopRules.buyCostB(m) : WorkshopRules.upgradeCostB(m, level);
            o.put("price", price);
            o.put("info", level > 0
                    ? com.apiculture.simulator.presentation.workshop.WorkshopFragment.machineInfo(text, m, level) : "");
            o.put("nextInfo", level < max
                    ? com.apiculture.simulator.presentation.workshop.WorkshopFragment.machineInfo(text, m, level + 1) : "");
            machines.put(o);
        }
        root.put("machines", machines);

        List<WorkshopState.Batch> list = new ArrayList<>(s.batches);
        list.sort((a, b) -> Long.compare(a.createdAt, b.createdAt));
        JSONArray batches = new JSONArray();
        for (WorkshopState.Batch b : list) {
            JSONObject o = new JSONObject();
            o.put("id", b.id);
            o.put("floraLabel", HiveSiteSummaryUi.floraLabel(text, b.flora));
            o.put("kg", b.kg);
            o.put("source", b.source == null ? "" : b.source);
            o.put("stage", b.stage.name());
            o.put("stageName", WorkshopUi.machineName(text, b.stage));
            o.put("inMachine", b.inMachine);
            o.put("startAt", b.startAt);
            o.put("endAt", b.endAt);
            o.put("waitingFormat", b.waitingFormat());
            o.put("blocked", !b.inMachine && !b.waitingFormat() && s.level(b.stage) <= 0);
            o.put("format", b.format == null ? "" : b.format.name());
            o.put("formatLabel", b.format == null ? ""
                    : b.mix != null ? WorkshopFormatUi.splitLabel(text, b.mix, b.kg)
                    : WorkshopFormatUi.label(text, b.format));
            // Reparto elegido (0/0/0 si todo va a un solo envase): el selector de Toni parte de aquí.
            o.put("mixKilo", b.mix != null ? b.mix.kilo : 0);
            o.put("mixHalf", b.mix != null ? b.mix.half : 0);
            o.put("mixQuarter", b.mix != null ? b.mix.quarter : 0);
            o.put("canChoose", !(b.stage == Machine.PACKER && b.inMachine));
            JSONArray options = new JSONArray();
            for (Format f : Format.values()) {
                JSONObject opt = new JSONObject();
                opt.put("key", f.name());
                opt.put("label", WorkshopFormatUi.label(text, f));
                opt.put("jars", WorkshopRules.jars(b.kg, f));
                opt.put("factor", f.priceFactor);
                options.put(opt);
            }
            o.put("options", options);
            batches.put(o);
        }
        root.put("batches", batches);

        JSONArray packed = new JSONArray();
        for (WorkshopState.Packed p : s.packed) {
            if (p.format == Format.BULK || p.jars <= 0) {
                continue;
            }
            JSONObject o = new JSONObject();
            o.put("floraLabel", HiveSiteSummaryUi.floraLabel(text, p.flora));
            o.put("format", p.format.name());
            o.put("formatLabel", WorkshopFormatUi.label(text, p.format));
            o.put("jars", p.jars);
            o.put("kg", p.kg);
            packed.put(o);
        }
        root.put("packed", packed);

        // El almacén es el mismo edificio: miel a granel en bidones.
        JSONArray bulk = new JSONArray();
        if (hexId != null) {
            for (java.util.Map.Entry<String, Double> e
                    : com.apiculture.simulator.data.repository.WarehouseHoneyStore.at(app, ownerId, hexId).entrySet()) {
                if (e.getKey() == null || e.getValue() == null || e.getValue() <= 1e-6) {
                    continue;
                }
                JSONObject o = new JSONObject();
                o.put("floraLabel", HiveSiteSummaryUi.floraLabel(text, e.getKey()));
                o.put("kg", e.getValue());
                bulk.put(o);
            }
        }
        root.put("bulk", bulk);
        root.put("hex", hexId == null ? "" : hexId);
        root.put("region", hexId == null ? "" : com.apiculture.simulator.domain.map.PlayableMapRegion.fromHexId(hexId).prefsValue());
        root.put("yard", UnityYard.build(app, ownerId, hexId));
        return root.toString();
    }
}
