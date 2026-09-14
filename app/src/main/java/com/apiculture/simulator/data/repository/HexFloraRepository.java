package com.apiculture.simulator.data.repository;

import android.content.Context;

import androidx.annotation.Nullable;

import com.apiculture.simulator.data.local.dao.HexFloraDao;
import com.apiculture.simulator.data.local.dao.HexParcelFloraDao;
import com.apiculture.simulator.data.local.dao.HexParcelOwnershipDao;
import com.apiculture.simulator.data.local.entity.HexFloraEntity;
import com.apiculture.simulator.data.local.entity.HexParcelFloraEntity;
import com.apiculture.simulator.data.local.entity.HexParcelOwnershipEntity;
import com.apiculture.simulator.domain.market.HoneyMarketEngine;
import com.apiculture.simulator.domain.parcel.FloraPlantingProgressRow;
import com.apiculture.simulator.domain.parcel.FloraProgression;
import com.apiculture.simulator.domain.parcel.HexFlora;
import com.apiculture.simulator.domain.parcel.HexParcel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Flora por terreno: varias especies por hex, siembras con cuenta atrás y migración desde {@code hex_flora}.
 */
public class HexFloraRepository {

    private final HexFloraDao legacyDao;
    private final HexParcelFloraDao parcelDao;
    private final HexParcelOwnershipDao ownershipDao;
    private final Context appContext;

    public HexFloraRepository(
            HexFloraDao legacyDao,
            HexParcelFloraDao parcelDao,
            HexParcelOwnershipDao ownershipDao,
            Context appContext) {
        this.legacyDao = legacyDao;
        this.parcelDao = parcelDao;
        this.ownershipDao = ownershipDao;
        this.appContext = appContext.getApplicationContext();
    }

    /** Ya no se precargan miles de hex: el mapa usa vista previa sin persistir hasta compra o migración. */
    public void seedAllParcelsBlocking(@Nullable List<HexParcel> parcels) {
    }

    private void migrateLegacyIfNeededBlocking(String hexId) {
        if (hexId == null || hexId.isEmpty()) {
            return;
        }
        if (!parcelDao.listForHexSync(hexId).isEmpty()) {
            return;
        }
        HexFloraEntity leg = legacyDao.getByHexIdSync(hexId);
        if (leg == null || leg.floraType == null || leg.floraType.isEmpty()) {
            return;
        }
        HexParcelFloraEntity e = new HexParcelFloraEntity();
        e.hexId = hexId;
        e.floraKey = HoneyMarketEngine.canonicalFloraKey(leg.floraType);
        e.plantedAtEpochMs = 0L;
        e.readyAtEpochMs = 0L;
        parcelDao.upsert(e);
    }

    private static boolean isEntryReady(HexParcelFloraEntity e, long nowMs) {
        if (e.plantedAtEpochMs == 0L && e.readyAtEpochMs == 0L) {
            return true;
        }
        return e.readyAtEpochMs <= nowMs;
    }

    /** Texto multilínea para el diálogo de terreno propio (lista vs. en crecimiento). */
    public String describeFlorasOnParcelForDialogBlocking(String hexId, long nowMs) {
        if (hexId == null || hexId.isEmpty()) {
            return "—";
        }
        migrateLegacyIfNeededBlocking(hexId);
        List<HexParcelFloraEntity> rows = parcelDao.listForHexSync(hexId);
        if (rows.isEmpty()) {
            return "Sin flora.";
        }
        StringBuilder sb = new StringBuilder();
        for (HexParcelFloraEntity e : rows) {
            String k = HoneyMarketEngine.canonicalFloraKey(e.floraKey);
            sb.append(k);
            sb.append(isEntryReady(e, nowMs) ? " — lista" : " — en crecimiento");
            sb.append('\n');
        }
        return sb.toString().trim();
    }

    public List<HexParcelFloraEntity> listEntriesForHexBlocking(String hexId) {
        if (hexId == null || hexId.isEmpty()) {
            return Collections.emptyList();
        }
        migrateLegacyIfNeededBlocking(hexId);
        return parcelDao.listForHexSync(hexId);
    }

    public List<String> listReadyFloraKeysBlocking(String hexId, long nowMs) {
        List<HexParcelFloraEntity> rows = listEntriesForHexBlocking(hexId);
        List<String> keys = new ArrayList<>();
        for (HexParcelFloraEntity e : rows) {
            if (isEntryReady(e, nowMs)) {
                keys.add(HoneyMarketEngine.canonicalFloraKey(e.floraKey));
            }
        }
        Collections.sort(keys);
        return keys;
    }

    public boolean isFloraReadyOnHexBlocking(String hexId, String floraKey, long nowMs) {
        String want = HoneyMarketEngine.canonicalFloraKey(floraKey);
        for (String k : listReadyFloraKeysBlocking(hexId, nowMs)) {
            if (k.equals(want)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Etiqueta de flora para mapa / diálogos: primera lista para colmena; si solo hay en crecimiento, la primera;
     * si no hay filas, vista previa pseudoaleatoria (sin guardar).
     */
    public String getOrCreateFloraForHexBlocking(@Nullable String hexId) {
        return getDisplayFloraForHexBlocking(hexId);
    }

    public String getDisplayFloraForHexBlocking(@Nullable String hexId) {
        if (hexId == null || hexId.isEmpty()) {
            return HexFlora.MIL_FLORES;
        }
        migrateLegacyIfNeededBlocking(hexId);
        List<HexParcelFloraEntity> rows = parcelDao.listForHexSync(hexId);
        long now = System.currentTimeMillis();
        List<String> ready = new ArrayList<>();
        List<String> growing = new ArrayList<>();
        for (HexParcelFloraEntity e : rows) {
            String k = HoneyMarketEngine.canonicalFloraKey(e.floraKey);
            if (isEntryReady(e, now)) {
                ready.add(k);
            } else {
                growing.add(k);
            }
        }
        Collections.sort(ready);
        Collections.sort(growing);
        if (!ready.isEmpty()) {
            return ready.get(0);
        }
        if (!growing.isEmpty()) {
            return growing.get(0);
        }
        return previewNativeFlora(hexId);
    }

    /**
     * Tras comprar terreno: primera siembra (24 h por defecto para ranura 1).
     */
    public void startInitialPlantingAfterPurchaseBlocking(String hexId, String floraKey, long nowMs) {
        if (hexId == null || hexId.isEmpty()) {
            return;
        }
        String key = HoneyMarketEngine.canonicalFloraKey(floraKey);
        if (!parcelDao.listForHexSync(hexId).isEmpty()) {
            return;
        }
        long hours = FloraProgression.growingDurationHoursForSlotIndex(1);
        long readyAt = nowMs + TimeUnit.HOURS.toMillis(hours);
        HexParcelFloraEntity e = new HexParcelFloraEntity();
        e.hexId = hexId;
        e.floraKey = key;
        e.plantedAtEpochMs = nowMs;
        e.readyAtEpochMs = readyAt;
        parcelDao.upsert(e);
    }

    /** Tutorial / reinicio: flora lista sin espera. */
    public void addReadyFloraNowBlocking(String hexId, String floraKey) {
        if (hexId == null || hexId.isEmpty()) {
            return;
        }
        String key = HoneyMarketEngine.canonicalFloraKey(floraKey);
        long now = System.currentTimeMillis();
        HexParcelFloraEntity e = new HexParcelFloraEntity();
        e.hexId = hexId;
        e.floraKey = key;
        e.plantedAtEpochMs = now;
        e.readyAtEpochMs = now;
        parcelDao.upsert(e);
    }

    public void clearAllFlorasForHexBlocking(String hexId) {
        if (hexId == null || hexId.isEmpty()) {
            return;
        }
        parcelDao.deleteAllForHex(hexId);
    }

    /** Serializa las filas locales para el campo {@code floras} en Firestore. */
    public List<Map<String, Object>> parcelFlorasToFirestoreListBlocking(String hexId) {
        if (hexId == null || hexId.isEmpty()) {
            return Collections.emptyList();
        }
        migrateLegacyIfNeededBlocking(hexId);
        List<Map<String, Object>> out = new ArrayList<>();
        for (HexParcelFloraEntity e : parcelDao.listForHexSync(hexId)) {
            Map<String, Object> m = new HashMap<>();
            m.put("floraKey", HoneyMarketEngine.canonicalFloraKey(e.floraKey));
            m.put("plantedAt", e.plantedAtEpochMs);
            m.put("readyAt", e.readyAtEpochMs);
            out.add(m);
        }
        return out;
    }

    /**
     * Sustituye la flora local por la lista de la nube (si el doc no trae {@code floras}, no llamar).
     */
    public void replaceParcelFlorasFromFirestoreMapsBlocking(String hexId, @Nullable List<?> rawList) {
        if (hexId == null || hexId.isEmpty()) {
            return;
        }
        parcelDao.deleteAllForHex(hexId);
        if (rawList == null) {
            return;
        }
        for (Object o : rawList) {
            if (!(o instanceof Map)) {
                continue;
            }
            Map<?, ?> m = (Map<?, ?>) o;
            Object fk = m.get("floraKey");
            if (!(fk instanceof String)) {
                continue;
            }
            String key = HoneyMarketEngine.canonicalFloraKey((String) fk);
            HexParcelFloraEntity e = new HexParcelFloraEntity();
            e.hexId = hexId;
            e.floraKey = key;
            e.plantedAtEpochMs = firestoreLong(m.get("plantedAt"));
            e.readyAtEpochMs = firestoreLong(m.get("readyAt"));
            parcelDao.upsert(e);
        }
    }

    private static long firestoreLong(Object v) {
        if (v instanceof Number) {
            return ((Number) v).longValue();
        }
        return 0L;
    }

    /**
     * Siembra adicional en terreno propio. Devuelve null si OK, o mensaje de error para el usuario.
     */
    @Nullable
    public String plantAdditionalFloraBlocking(
            String hexId,
            String floraKey,
            String ownerId,
            int playerLevel,
            EconomyRepository economy,
            long nowMs,
            HexParcelRepository hexParcelRepository) {
        if (hexId == null || hexId.isEmpty() || ownerId == null || ownerId.isEmpty()) {
            return "Datos no válidos.";
        }
        String key = HoneyMarketEngine.canonicalFloraKey(floraKey);
        HexParcel parcel = IberiaHexOverlayStore.findById(appContext, hexId);
        if (!HexFlora.isAllowedOnParcel(key, parcel)) {
            return "Esta flora no se da en el clima de este terreno.";
        }
        if (!FloraProgression.isFloraUnlockedForPlayerLevel(key, playerLevel)) {
            return "Aún no has desbloqueado esta flora (sube de nivel).";
        }
        String parcelOwner = hexParcelRepository.getOwnerSync(hexId);
        if (parcelOwner == null || !parcelOwner.equals(ownerId)) {
            return "Este terreno no es tuyo.";
        }
        migrateLegacyIfNeededBlocking(hexId);
        List<HexParcelFloraEntity> existing = parcelDao.listForHexSync(hexId);
        for (HexParcelFloraEntity e : existing) {
            if (HoneyMarketEngine.canonicalFloraKey(e.floraKey).equals(key)) {
                return "Esta flora ya está en el terreno (o en crecimiento).";
            }
        }
        int count = existing.size();
        int cost = FloraProgression.plantingCostEurosForAdditionalFlora(count);
        if (!economy.trySpend(cost)) {
            return "Saldo insuficiente (" + cost + " B).";
        }
        int slot = count + 1;
        long hours = FloraProgression.growingDurationHoursForSlotIndex(slot);
        long readyAt = nowMs + TimeUnit.HOURS.toMillis(hours);
        HexParcelFloraEntity row = new HexParcelFloraEntity();
        row.hexId = hexId;
        row.floraKey = key;
        row.plantedAtEpochMs = nowMs;
        row.readyAtEpochMs = readyAt;
        parcelDao.upsert(row);
        return null;
    }

    public List<FloraPlantingProgressRow> listGrowingPlantingsForOwnerBlocking(
            String ownerId,
            HexParcelRepository hexParcelRepository,
            long nowMs) {
        if (ownerId == null || ownerId.isEmpty()) {
            return Collections.emptyList();
        }
        List<FloraPlantingProgressRow> out = new ArrayList<>();
        for (HexParcelOwnershipEntity own : ownershipDao.getAllForOwnerSync(ownerId)) {
            if (own == null || own.hexId == null) {
                continue;
            }
            migrateLegacyIfNeededBlocking(own.hexId);
            String label = hexParcelRepository.getParcelDisplayNameSync(own.hexId);
            for (HexParcelFloraEntity e : parcelDao.listForHexSync(own.hexId)) {
                if (!isEntryReady(e, nowMs)) {
                    out.add(new FloraPlantingProgressRow(
                            own.hexId,
                            label,
                            HoneyMarketEngine.canonicalFloraKey(e.floraKey),
                            e.plantedAtEpochMs,
                            e.readyAtEpochMs));
                }
            }
        }
        out.sort(Comparator.comparing((FloraPlantingProgressRow r) -> r.readyAtEpochMs)
                .thenComparing(r -> r.parcelLabel));
        return out;
    }

    private String previewNativeFlora(String hexId) {
        return HexFlora.nativeFloraForParcel(IberiaHexOverlayStore.findById(appContext, hexId));
    }
}
