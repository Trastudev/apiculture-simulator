package com.apiculture.simulator.presentation.hive;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.apiculture.simulator.R;
import com.apiculture.simulator.data.local.entity.HexParcelOwnershipEntity;
import com.apiculture.simulator.data.local.entity.HiveEntity;
import com.apiculture.simulator.data.repository.IberiaHexOverlayStore;
import com.apiculture.simulator.domain.game.GameCalendar;
import com.apiculture.simulator.domain.game.NpcContractCatalog;
import com.apiculture.simulator.domain.game.TranshumanceRules;
import com.apiculture.simulator.domain.parcel.HexApiary;
import com.apiculture.simulator.domain.parcel.HexParcel;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Textos de transhumancia a contrato programada (aún no se ha ejecutado el viaje).
 */
public final class PendingContractMoveUi {

    private PendingContractMoveUi() {
    }

    @NonNull
    public static List<HiveEntity> pendingToDest(@Nullable List<HiveEntity> all, @Nullable String destHexId) {
        List<HiveEntity> out = new ArrayList<>();
        if (all == null || destHexId == null || destHexId.isEmpty()) {
            return out;
        }
        for (HiveEntity h : all) {
            if (TranshumanceRules.hasPendingContractMove(h) && destHexId.equals(h.pendingContractHexId)) {
                out.add(h);
            }
        }
        return out;
    }

    @NonNull
    public static List<HiveEntity> pendingFromHex(@Nullable List<HiveEntity> all, @Nullable String originHexId) {
        return pendingFromSite(all, originHexId, null, null, null);
    }

    @NonNull
    public static List<HiveEntity> pendingFromSite(
            @Nullable List<HiveEntity> all,
            @Nullable String originHexId,
            @Nullable String siteId,
            @Nullable HexParcel hex,
            @Nullable List<HexParcelOwnershipEntity> ownerSites) {
        List<HiveEntity> out = new ArrayList<>();
        if (all == null || originHexId == null || originHexId.isEmpty()) {
            return out;
        }
        for (HiveEntity h : all) {
            if (!TranshumanceRules.hasPendingContractMove(h) || !originHexId.equals(h.hexId)) {
                continue;
            }
            if (siteId == null || siteId.isEmpty()
                    || HexApiary.hiveOnSite(h, originHexId, siteId, hex, ownerSites)) {
                out.add(h);
            }
        }
        return out;
    }

    public static int earliestDayKey(@Nullable List<HiveEntity> pending) {
        int best = 0;
        if (pending == null) {
            return 0;
        }
        for (HiveEntity h : pending) {
            if (h == null || h.pendingContractDayKey <= 0) {
                continue;
            }
            if (best == 0 || h.pendingContractDayKey < best) {
                best = h.pendingContractDayKey;
            }
        }
        return best;
    }

    @NonNull
    public static String travelDayLabel(int dayKey) {
        return travelDayLabel(dayKey, LocalDate.now(GameCalendar.userTimeZone()));
    }

    @NonNull
    public static String travelDayLabel(int dayKey, @Nullable LocalDate today) {
        if (dayKey <= 0) {
            return "—";
        }
        LocalDate d;
        try {
            d = GameCalendar.fromDayKey(dayKey);
        } catch (RuntimeException e) {
            return "—";
        }
        if (today != null && !d.isAfter(today)) {
            return "hoy";
        }
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("d MMM", new Locale("es", "ES"));
        return "el " + fmt.format(d);
    }

    public static long remainingUntilDepartureMs(@Nullable HiveEntity hive, long nowMs) {
        if (!TranshumanceRules.hasPendingContractMove(hive)) {
            return 0L;
        }
        LocalDate day;
        try {
            day = GameCalendar.fromDayKey(hive.pendingContractDayKey);
        } catch (RuntimeException e) {
            return 0L;
        }
        long leave = day.atTime(GameCalendar.PRODUCTION_HOUR, GameCalendar.PRODUCTION_MINUTE)
                .atZone(GameCalendar.userTimeZone())
                .toInstant()
                .toEpochMilli();
        return Math.max(0L, leave - nowMs);
    }

    @NonNull
    public static String formatDepartRemaining(long remainingMs) {
        long sec = Math.max(0L, remainingMs / 1000L);
        long d = sec / 86400L;
        long h = (sec % 86400L) / 3600L;
        long m = (sec % 3600L) / 60L;
        if (d > 0) {
            return d + " d " + h + " h";
        }
        if (h > 0) {
            return h + " h " + String.format(Locale.getDefault(), "%02d min", m);
        }
        if (m > 0) {
            return m + " min";
        }
        return Math.max(0L, sec) + " s";
    }

    @Nullable
    public static String hiveChip(@Nullable Context ctx, @Nullable HiveEntity hive) {
        if (ctx == null || !TranshumanceRules.hasPendingContractMove(hive)) {
            return null;
        }
        return ctx.getString(R.string.hive_pending_contract_chip,
                travelDayLabel(hive.pendingContractDayKey),
                destLabel(ctx, hive.pendingContractHexId));
    }

    @Nullable
    public static String hiveDetail(@Nullable Context ctx, @Nullable HiveEntity hive) {
        if (ctx == null || !TranshumanceRules.hasPendingContractMove(hive)) {
            return null;
        }
        return ctx.getString(R.string.hive_pending_contract_detail,
                travelDayLabel(hive.pendingContractDayKey),
                destLabel(ctx, hive.pendingContractHexId));
    }

    @Nullable
    public static String destinationBanner(@Nullable Context ctx, @Nullable List<HiveEntity> all,
            @Nullable String destHexId) {
        if (ctx == null) {
            return null;
        }
        List<HiveEntity> pending = pendingToDest(all, destHexId);
        if (pending.isEmpty()) {
            return null;
        }
        String day = travelDayLabel(earliestDayKey(pending));
        String headline = pending.size() == 1
                ? ctx.getString(R.string.contract_pending_headline_one, day)
                : ctx.getString(R.string.contract_pending_headline, pending.size(), day);
        StringBuilder sb = new StringBuilder(headline);
        String names = joinHiveNames(pending);
        if (!names.isEmpty()) {
            sb.append('\n').append(names);
        }
        String from = joinOriginLabels(ctx, pending);
        if (!from.isEmpty()) {
            sb.append('\n').append(pending.size() == 1
                    ? ctx.getString(R.string.contract_pending_from_one, from)
                    : ctx.getString(R.string.contract_pending_from, from));
        }
        sb.append('\n').append(ctx.getString(R.string.contract_pending_forage));
        return sb.toString();
    }

    @NonNull
    static String joinHiveNames(@Nullable List<HiveEntity> hives) {
        if (hives == null || hives.isEmpty()) {
            return "";
        }
        int shown = Math.min(4, hives.size());
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < shown; i++) {
            HiveEntity h = hives.get(i);
            String name = h != null && h.name != null && !h.name.trim().isEmpty()
                    ? h.name.trim()
                    : "Colmena";
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(name);
        }
        int extra = hives.size() - shown;
        if (extra > 0) {
            sb.append(" y ").append(extra).append(" más");
        }
        return sb.toString();
    }

    @NonNull
    public static String destLabel(@Nullable Context ctx, @Nullable String destHexId) {
        if (destHexId == null || destHexId.isEmpty()) {
            return "finca de contrato";
        }
        HexParcel parcel = ctx != null ? IberiaHexOverlayStore.findById(ctx, destHexId) : null;
        String estate = NpcContractCatalog.estateNameFor(parcel);
        if (estate != null && !estate.trim().isEmpty()) {
            return estate.trim();
        }
        return hexFallback(destHexId);
    }

    @NonNull
    public static String originLabel(@Nullable Context ctx, @Nullable String hexId) {
        if (hexId == null || hexId.isEmpty()) {
            return "tu apiario";
        }
        HexParcel parcel = ctx != null ? IberiaHexOverlayStore.findById(ctx, hexId) : null;
        if (parcel != null && parcel.placeName != null && !parcel.placeName.trim().isEmpty()) {
            return parcel.placeName.trim();
        }
        if (NpcContractCatalog.isNpcFarm(parcel)) {
            String estate = NpcContractCatalog.estateNameFor(parcel);
            if (estate != null && !estate.trim().isEmpty()) {
                return estate.trim();
            }
        }
        return hexFallback(hexId);
    }

    @NonNull
    private static String joinOriginLabels(@Nullable Context ctx, @NonNull List<HiveEntity> pending) {
        Set<String> labels = new LinkedHashSet<>();
        for (HiveEntity h : pending) {
            if (h == null) {
                continue;
            }
            labels.add(originLabel(ctx, h.hexId));
        }
        if (labels.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        int i = 0;
        for (String label : labels) {
            if (i > 0) {
                sb.append(i == labels.size() - 1 ? " y " : ", ");
            }
            sb.append(label);
            i++;
        }
        return sb.toString();
    }

    @NonNull
    private static String hexFallback(@NonNull String hexId) {
        int u = hexId.lastIndexOf('_');
        String tail = u > 0 ? hexId.substring(u + 1) : hexId;
        return "Terreno · " + tail;
    }
}
