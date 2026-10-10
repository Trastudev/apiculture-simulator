package com.apiculture.simulator.unity;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.apiculture.simulator.R;
import com.apiculture.simulator.data.repository.HarvestReceipts;
import com.apiculture.simulator.data.repository.OrderReceipts;
import com.apiculture.simulator.presentation.hive.HiveSiteSummaryUi;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Map;

/**
 * Avisos de llegada mientras el jugador está en el 3D: miel recolectada al volver el camión y comandas
 * entregadas con el agradecimiento del cliente. Mismos datos y textos que los diálogos de la app; Unity
 * los pinta (clase C# Apiario.ReceiptDialog) y contesta con {@link UnityBridge#onReceiptSeen}.
 */
final class UnityReceipts {
    @Nullable
    private static String showing;

    private UnityReceipts() {
    }

    /** El 3D está delante: los avisos van a Unity en vez de a la pantalla principal. */
    static void attach() {
        HarvestReceipts.setListener(UnityReceipts::pump);
        OrderReceipts.setListener(UnityReceipts::pump);
        showing = null;
        pump();
    }

    static synchronized void ready() {
        showing = null;
        pump();
    }

    static synchronized void seen(@Nullable String kind, @Nullable String id) {
        Context ctx = UnityBridge.app();
        if (ctx != null && id != null) {
            if ("order".equals(kind)) {
                OrderReceipts.drop(ctx, id);
            } else {
                HarvestReceipts.drop(ctx, id);
            }
        }
        showing = null;
        pump();
    }

    static synchronized void pump() {
        Context ctx = UnityBridge.app();
        if (ctx == null || showing != null) {
            return;
        }
        Context text = UnityBridge.localized();
        try {
            HarvestReceipts.Receipt h = HarvestReceipts.peek(ctx);
            if (h != null) {
                showing = h.tripId;
                UnityBridge.send("ShowReceipt", harvest(text, h).toString());
                return;
            }
            OrderReceipts.Receipt o = OrderReceipts.peek(ctx);
            if (o != null) {
                showing = o.id;
                UnityBridge.send("ShowReceipt", order(text, o).toString());
            }
        } catch (JSONException e) {
            showing = null;
        }
    }

    @NonNull
    private static JSONObject harvest(@NonNull Context c, @NonNull HarvestReceipts.Receipt r) throws JSONException {
        JSONObject o = new JSONObject();
        o.put("kind", "harvest");
        o.put("id", r.tripId);
        o.put("portrait", -1);
        o.put("title", c.getString(R.string.dashboard_harvest_summary_title));
        o.put("subtitle", c.getString(R.string.dashboard_harvest_summary_subtitle, r.hiveCount));
        JSONArray lines = new JSONArray();
        for (Map.Entry<String, Double> e : r.kgByFlora.entrySet()) {
            if (e.getValue() == null) {
                continue;
            }
            JSONObject l = new JSONObject();
            l.put("label", HiveSiteSummaryUi.floraLabel(c, e.getKey()));
            l.put("value", c.getString(R.string.dashboard_harvest_summary_kg, e.getValue()));
            lines.put(l);
        }
        o.put("lines", lines);
        o.put("total", c.getString(R.string.dashboard_harvest_summary_total, r.totalKg));
        o.put("ok", c.getString(R.string.receipt_3d_ok));
        return o;
    }

    @NonNull
    private static JSONObject order(@NonNull Context c, @NonNull OrderReceipts.Receipt r) throws JSONException {
        JSONObject o = new JSONObject();
        o.put("kind", "order");
        o.put("id", r.id);
        o.put("portrait", r.portraitIndex);
        o.put("title", r.npcName.trim().isEmpty() ? c.getString(R.string.order_delivery_title) : r.npcName);
        String[] thanks = c.getResources().getStringArray(R.array.order_delivery_thanks_lines);
        String[] speeches = c.getResources().getStringArray(R.array.order_delivery_speech_lines);
        int variants = Math.min(thanks.length, speeches.length);
        int index = variants > 0 ? Math.floorMod(r.id.hashCode(), variants) : 0;
        o.put("subtitle", variants > 0 ? thanks[index] : c.getString(R.string.order_delivery_thanks));
        o.put("speech", variants > 0 ? speeches[index] : c.getString(R.string.order_delivery_npc_speech));
        JSONArray lines = new JSONArray();
        JSONObject sold = new JSONObject();
        sold.put("label", c.getString(R.string.order_delivery_sold_title));
        sold.put("value", c.getString(R.string.order_delivery_sold_amount, r.kg, HiveSiteSummaryUi.floraLabel(c, r.floraKey)));
        lines.put(sold);
        JSONObject earned = new JSONObject();
        earned.put("label", c.getString(R.string.order_delivery_earned_title));
        earned.put("value", c.getString(R.string.order_delivery_earned_amount, r.payout));
        lines.put(earned);
        o.put("lines", lines);
        o.put("total", "");
        o.put("ok", c.getString(R.string.receipt_3d_ok));
        return o;
    }
}
