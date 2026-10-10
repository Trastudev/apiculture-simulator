package com.apiculture.simulator.data.repository;

import android.content.Context;

import androidx.annotation.Nullable;

import com.apiculture.simulator.R;
import com.apiculture.simulator.data.local.dao.HexFloraDao;
import com.apiculture.simulator.data.local.dao.HexParcelFloraDao;
import com.apiculture.simulator.data.local.dao.HexParcelOwnershipDao;
import com.apiculture.simulator.data.local.entity.HexFloraEntity;
import com.apiculture.simulator.data.local.entity.HexParcelFloraEntity;
import com.apiculture.simulator.data.local.entity.HexParcelOwnershipEntity;
import com.apiculture.simulator.domain.game.GameCalendar;
import com.apiculture.simulator.domain.market.HoneyMarketEngine;
import com.apiculture.simulator.domain.parcel.CropRules;
import com.apiculture.simulator.domain.parcel.CropTickResult;
import com.apiculture.simulator.domain.parcel.CropUnlock;
import com.apiculture.simulator.domain.parcel.FloraPlantingProgressRow;
import com.apiculture.simulator.domain.parcel.FloraProgression;
import com.apiculture.simulator.domain.parcel.HexFlora;
import com.apiculture.simulator.domain.parcel.HexParcel;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
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
        String key = HoneyMarketEngine.canonicalFloraKey(leg.floraType);
        if (HexFlora.isPlantation(key)) {
            return;
        }
        HexParcelFloraEntity e = new HexParcelFloraEntity();
        e.hexId = hexId;
        e.floraKey = key;
        e.plantedAtEpochMs = 0L;
        e.readyAtEpochMs = 0L;
        parcelDao.upsert(e);
    }

    private static boolean isEntryReady(HexParcelFloraEntity e, long nowMs) {
        if (e == null) {
            return false;
        }
        if (!HexFlora.isPlantation(e.floraKey)) {
            return true;
        }
        if (e.plantedAtEpochMs == 0L && e.readyAtEpochMs == 0L) {
            return false;
        }
        return e.readyAtEpochMs <= nowMs;
    }

    /** Texto de cultivos (siembra del jugador) para el diálogo de terreno. Vacío si no hay. */
    @Nullable
    public String describePlantationsOnParcelForDialogBlocking(String hexId, long nowMs) {
        if (hexId == null || hexId.isEmpty()) {
            return null;
        }
        migrateLegacyIfNeededBlocking(hexId);
        ensureNativeMixReadyBlocking(hexId);
        List<HexParcelFloraEntity> rows = parcelDao.listForHexSync(hexId);
        StringBuilder sb = new StringBuilder();
        for (HexParcelFloraEntity e : rows) {
            String k = HoneyMarketEngine.canonicalFloraKey(e.floraKey);
            if (!HexFlora.isPlantation(k)) {
                continue;
            }
            if (sb.length() == 0) {
                sb.append(appContext.getString(R.string.hex_parcel_cultivos_label));
            }
            sb.append('\n');
            boolean ready = isEntryReady(e, nowMs);
            if (!ready) {
                sb.append(appContext.getString(R.string.hex_parcel_cultivos_row, k,
                        appContext.getString(R.string.hex_flora_status_growing)));
            } else if (CropRules.isAnnual(k)) {
                sb.append(appContext.getString(R.string.hex_parcel_crop_annual_ready, k));
            } else {
                sb.append(appContext.getString(R.string.hex_parcel_crop_tree_ready, k,
                        (double) CropRules.treeMaintenanceEuros(k)));
            }
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    public List<HexParcelFloraEntity> listEntriesForHexBlocking(String hexId) {
        if (hexId == null || hexId.isEmpty()) {
            return Collections.emptyList();
        }
        migrateLegacyIfNeededBlocking(hexId);
        ensureNativeMixReadyBlocking(hexId);
        return parcelDao.listForHexSync(hexId);
    }

    /**
     * Terrenos ya comprados: completa el mix silvestre (3–5) si aún falta alguna especie nativa.
     */
    public void ensureNativeMixReadyBlocking(String hexId) {
        if (hexId == null || hexId.isEmpty()) {
            return;
        }
        List<HexParcelFloraEntity> existing = parcelDao.listForHexSync(hexId);
        if (existing.isEmpty()) {
            seedNativeMixIfOwned(hexId);
            return;
        }
        for (HexParcelFloraEntity e : existing) {
            if (HexFlora.isPlantation(e.floraKey)
                    && e.plantedAtEpochMs == 0L
                    && e.readyAtEpochMs == 0L) {
                parcelDao.deleteByHexAndKey(hexId, e.floraKey);
            }
        }
        existing = parcelDao.listForHexSync(hexId);
        if (existing.isEmpty()) {
            seedNativeMixIfOwned(hexId);
            return;
        }
        HexParcel parcel = IberiaHexOverlayStore.findById(appContext, hexId);
        List<String> mix = HexFlora.nativeMixForParcel(parcel);
        long now = System.currentTimeMillis();
        for (String key : mix) {
            boolean found = false;
            for (HexParcelFloraEntity e : existing) {
                if (HoneyMarketEngine.canonicalFloraKey(e.floraKey).equals(key)) {
                    found = true;
                    if (e.readyAtEpochMs > now) {
                        e.plantedAtEpochMs = now;
                        e.readyAtEpochMs = now;
                        parcelDao.upsert(e);
                    }
                    break;
                }
            }
            if (!found) {
                addReadyFloraNowBlocking(hexId, key);
            }
        }
    }

    private void seedNativeMixIfOwned(String hexId) {
        List<HexParcelOwnershipEntity> owners = ownershipDao.listByHexSync(hexId);
        if (owners == null || owners.isEmpty()) {
            return;
        }
        addNativeMixReadyNowBlocking(hexId);
    }

    public void addNativeMixReadyNowBlocking(String hexId) {
        if (hexId == null || hexId.isEmpty()) {
            return;
        }
        HexParcel parcel = IberiaHexOverlayStore.findById(appContext, hexId);
        for (String key : HexFlora.nativeMixForParcel(parcel)) {
            addReadyFloraNowBlocking(hexId, key);
        }
    }

    public List<String> listReadyFloraKeysBlocking(String hexId, long nowMs) {
        return listReadyFloraKeysBlocking(hexId, null, nowMs);
    }

    /**
     * Flora lista para las colmenas de un apiario. La silvestre es del hexágono.
     * Un cultivo solo entra si lo sembró ese apiario ({@code siteId}).
     */
    public List<String> listReadyFloraKeysBlocking(String hexId, @Nullable String siteId, long nowMs) {
        List<HexParcelFloraEntity> rows = listEntriesForHexBlocking(hexId);
        List<String> keys = new ArrayList<>();
        for (HexParcelFloraEntity e : rows) {
            if (!isEntryReady(e, nowMs)) {
                continue;
            }
            String key = HoneyMarketEngine.canonicalFloraKey(e.floraKey);
            if (HexFlora.isPlantation(key) && (siteId == null || !siteId.equals(e.siteId))) {
                continue;
            }
            keys.add(key);
        }
        Collections.sort(keys);
        return keys;
    }

    /** Algún apiario del hexágono tiene esta flora lista (la silvestre cuenta siempre). */
    public boolean isFloraReadyOnHexBlocking(String hexId, String floraKey, long nowMs) {
        String want = HoneyMarketEngine.canonicalFloraKey(floraKey);
        for (HexParcelFloraEntity e : listEntriesForHexBlocking(hexId)) {
            if (isEntryReady(e, nowMs) && want.equals(HoneyMarketEngine.canonicalFloraKey(e.floraKey))) {
                return true;
            }
        }
        return false;
    }

    public boolean isFloraReadyOnHexBlocking(String hexId, @Nullable String siteId, String floraKey, long nowMs) {
        String want = HoneyMarketEngine.canonicalFloraKey(floraKey);
        for (String k : listReadyFloraKeysBlocking(hexId, siteId, nowMs)) {
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
            m.put("expireAtDayKey", e.expireAtDayKey);
            m.put("lastMaintainedYear", e.lastMaintainedYear);
            m.put("siteId", e.siteId != null ? e.siteId : "");
            m.put("fruitSoldYear", e.fruitSoldYear);
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
            e.expireAtDayKey = (int) firestoreLong(m.get("expireAtDayKey"));
            e.lastMaintainedYear = (int) firestoreLong(m.get("lastMaintainedYear"));
            Object site = m.get("siteId");
            e.siteId = site instanceof String ? (String) site : "";
            e.fruitSoldYear = (int) firestoreLong(m.get("fruitSoldYear"));
            if (HexFlora.isPlantation(key) && e.plantedAtEpochMs == 0L && e.readyAtEpochMs == 0L) {
                continue;
            }
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
     * Siembra adicional en un hex compartido donde el jugador tiene un apiario.
     */
    @Nullable
    public String plantAdditionalFloraBlocking(
            String hexId,
            String floraKey,
            String ownerId,
            @Nullable String siteId,
            EconomyRepository economy,
            long nowMs,
            HexParcelRepository hexParcelRepository,
            int playerLevel) {
        if (hexId == null || hexId.isEmpty() || ownerId == null || ownerId.isEmpty()
                || siteId == null || siteId.isEmpty()) {
            return "Datos no válidos.";
        }
        if (GameServer.enabled() && !GameServer.isAvailable()) {
            return "No hay conexión con el servidor. No se puede realizar esta acción.";
        }
        String key = HoneyMarketEngine.canonicalFloraKey(floraKey);
        if (!HexFlora.isPlantation(key)) {
            return "Solo puedes sembrar cultivos. La flora silvestre ya viene con el terreno.";
        }
        HexParcel parcel = IberiaHexOverlayStore.findById(appContext, hexId);
        if (!HexFlora.isAllowedOnParcel(key, parcel)) {
            return "Este cultivo no se da en el clima de este terreno.";
        }
        if (!CropUnlock.isUnlocked(key, playerLevel)) {
            return "Aún no has desbloqueado este cultivo. Sube de nivel para más siembras.";
        }
        // El hex es compartido: basta con que este jugador tenga un apiario
        // en él. No se consulta un único propietario global del terreno.
        if (!hexParcelRepository.hasOwnerSync(hexId, ownerId)) {
            return "Este terreno no es tuyo.";
        }
        migrateLegacyIfNeededBlocking(hexId);
        List<HexParcelFloraEntity> existing = parcelDao.listForHexSync(hexId);
        for (HexParcelFloraEntity e : existing) {
            if (HoneyMarketEngine.canonicalFloraKey(e.floraKey).equals(key) && siteId.equals(e.siteId)) {
                return "Este apiario ya tiene ese cultivo (o está creciendo).";
            }
        }
        int cost = CropRules.plantCostEuros(key);
        if (!economy.trySpend(cost, "Siembra de " + key)) {
            return "Saldo insuficiente (" + cost + " B).";
        }
        int growDays = CropRules.growDays(key);
        long readyAt = nowMs + TimeUnit.DAYS.toMillis(growDays);
        LocalDate readyOn = LocalDate.now(GameCalendar.userTimeZone()).plusDays(growDays);
        HexParcelFloraEntity row = new HexParcelFloraEntity();
        row.hexId = hexId;
        row.floraKey = key;
        row.siteId = siteId;
        row.plantedAtEpochMs = nowMs;
        row.readyAtEpochMs = readyAt;
        row.expireAtDayKey = CropRules.expireDayKey(key, parcel, readyOn);
        row.lastMaintainedYear = 0;
        parcelDao.upsert(row);
        return null;
    }

    /** Pep compra el fruto una vez por año, cuando el árbol ya produce y está en época de fruto. */
    @Nullable
    public String sellFruitBlocking(String hexId, String floraKey, String ownerId, @Nullable String siteId,
            EconomyRepository economy, HexParcelRepository hexParcelRepository, LocalDate today) {
        if (hexId == null || siteId == null || siteId.isEmpty() || today == null
                || !hexParcelRepository.hasOwnerSync(hexId, ownerId)) {
            return "Este terreno no es tuyo.";
        }
        if (GameServer.enabled() && !GameServer.isAvailable()) {
            return "No hay conexión con el servidor. No se puede realizar esta acción.";
        }
        String key = HoneyMarketEngine.canonicalFloraKey(floraKey);
        HexParcel parcel = IberiaHexOverlayStore.findById(appContext, hexId);
        HexParcelFloraEntity row = null;
        for (HexParcelFloraEntity e : parcelDao.listForHexSync(hexId)) {
            if (key.equals(HoneyMarketEngine.canonicalFloraKey(e.floraKey)) && siteId.equals(e.siteId)) {
                row = e;
                break;
            }
        }
        long now = System.currentTimeMillis();
        if (row == null || !CropRules.isTree(key) || !isEntryReady(row, now)
                || !CropRules.inFruit(key, parcel, today)) {
            return "Todavía no hay fruto que recoger.";
        }
        if (row.fruitSoldYear == today.getYear()) {
            return "La cosecha de este año ya está vendida.";
        }
        int pay = CropRules.fruitSaleEuros(key);
        row.fruitSoldYear = today.getYear();
        parcelDao.upsert(row);
        economy.addToBalance(pay, "Cosecha de " + key);
        return null;
    }

    /**
     * El payés poda y abona un frutal. Se puede pagar desde {@link CropRules#TREE_MAINTENANCE_EARLY_DAYS}
     * días antes de que venza; el siguiente vence un año después del pago.
     */
    @Nullable
    public String maintainTreeBlocking(String hexId, String floraKey, String ownerId, @Nullable String siteId,
            EconomyRepository economy, HexParcelRepository hexParcelRepository, LocalDate today) {
        if (hexId == null || hexId.isEmpty() || ownerId == null || ownerId.isEmpty()) {
            return "Datos no válidos.";
        }
        if (GameServer.enabled() && !GameServer.isAvailable()) {
            return "No hay conexión con el servidor. No se puede realizar esta acción.";
        }
        if (!hexParcelRepository.hasOwnerSync(hexId, ownerId)) {
            return "Este terreno no es tuyo.";
        }
        String key = HoneyMarketEngine.canonicalFloraKey(floraKey);
        HexParcelFloraEntity row = null;
        for (HexParcelFloraEntity e : parcelDao.listForHexSync(hexId)) {
            if (HoneyMarketEngine.canonicalFloraKey(e.floraKey).equals(key)
                    && (siteId == null || siteId.isEmpty() || siteId.equals(e.siteId))) {
                row = e;
                break;
            }
        }
        if (row == null || !CropRules.isTree(key) || !isEntryReady(row, System.currentTimeMillis())) {
            return "Aquí no hay árboles que mantener.";
        }
        if (!CropRules.canMaintainNow(row.plantedAtEpochMs, row.lastMaintainedYear, today)) {
            return "Todavía no toca: los árboles están bien cuidados.";
        }
        int fee = CropRules.treeMaintenanceEuros(key);
        if (!economy.trySpend(fee, "Mantenimiento de " + key)) {
            return "Saldo insuficiente (" + fee + " B).";
        }
        LocalDate due = CropRules.maintenanceDueDate(row.plantedAtEpochMs, row.lastMaintainedYear);
        row.lastMaintainedYear = GameCalendar.toDayKey(today.isBefore(due) ? due : today);
        parcelDao.upsert(row);
        return null;
    }

    /** Terrenos del jugador con algún frutal cuyo mantenimiento ya venció. */
    public java.util.Set<String> hexesNeedingMaintenanceBlocking(String ownerId, LocalDate today) {
        java.util.Set<String> out = new java.util.HashSet<>();
        if (ownerId == null || ownerId.isEmpty()) {
            return out;
        }
        long now = System.currentTimeMillis();
        for (HexParcelOwnershipEntity own : ownershipDao.getAllForOwnerSync(ownerId)) {
            if (own == null || own.hexId == null || out.contains(own.hexId)) {
                continue;
            }
            for (HexParcelFloraEntity e : parcelDao.listForHexSync(own.hexId)) {
                if (CropRules.isTree(e.floraKey) && isEntryReady(e, now)
                        && CropRules.isMaintenanceDue(e.plantedAtEpochMs, e.lastMaintainedYear, today)) {
                    out.add(own.hexId);
                    break;
                }
            }
        }
        return out;
    }

    public CropTickResult tickCropsForOwnerBlocking(
            String ownerId,
            LocalDate day,
            EconomyRepository economy,
            HexParcelRepository hexParcelRepository) {
        if (ownerId == null || ownerId.isEmpty() || day == null) {
            return CropTickResult.empty();
        }
        int dayKey = GameCalendar.toDayKey(day);
        int doy = Math.min(365, day.getDayOfYear());
        int year = day.getYear();
        long nowMs = System.currentTimeMillis();
        List<String> notes = new ArrayList<>();
        List<CropTickResult.Removed> removed = new ArrayList<>();
        LinkedHashSet<String> touched = new LinkedHashSet<>();
        for (HexParcelOwnershipEntity own : ownershipDao.getAllForOwnerSync(ownerId)) {
            if (own == null || own.hexId == null) {
                continue;
            }
            String hexId = own.hexId;
            HexParcel parcel = IberiaHexOverlayStore.findById(appContext, hexId);
            String parcelName = hexParcelRepository.getParcelDisplayNameSync(hexId);
            List<HexParcelFloraEntity> rows = new ArrayList<>(parcelDao.listForHexSync(hexId));
            for (HexParcelFloraEntity e : rows) {
                String key = HoneyMarketEngine.canonicalFloraKey(e.floraKey);
                if (!HexFlora.isPlantation(key)) {
                    continue;
                }
                if (CropRules.isAnnual(key)) {
                    if (e.expireAtDayKey <= 0 && isEntryReady(e, nowMs)) {
                        long ms = Math.max(e.readyAtEpochMs, e.plantedAtEpochMs);
                        LocalDate readyOn = Instant.ofEpochMilli(Math.max(1L, ms))
                                .atZone(GameCalendar.userTimeZone()).toLocalDate();
                        e.expireAtDayKey = CropRules.expireDayKey(key, parcel, readyOn);
                        parcelDao.upsert(e);
                        touched.add(hexId);
                    }
                    if (e.expireAtDayKey > 0 && dayKey >= e.expireAtDayKey) {
                        parcelDao.deleteByHexKeySite(hexId, e.floraKey, e.siteId != null ? e.siteId : "");
                        removed.add(new CropTickResult.Removed(hexId, key));
                        touched.add(hexId);
                        notes.add(appContext.getString(R.string.crop_tick_annual_ended, key, parcelName));
                    }
                    continue;
                }
                if (!CropRules.isTree(key) || !isEntryReady(e, nowMs)) {
                    continue;
                }
                if (CropRules.maintenanceDueDate(e.plantedAtEpochMs, e.lastMaintainedYear).equals(day)) {
                    notes.add(appContext.getString(R.string.crop_tick_tree_due, key, parcelName));
                }
            }
        }
        return new CropTickResult(notes, removed, new ArrayList<>(touched));
    }

    public List<FloraPlantingProgressRow> listGrowingPlantingsForOwnerBlocking(
            String ownerId,
            HexParcelRepository hexParcelRepository,
            long nowMs) {
        if (ownerId == null || ownerId.isEmpty()) {
            return Collections.emptyList();
        }
        List<FloraPlantingProgressRow> out = new ArrayList<>();
        java.util.Set<String> seenHex = new java.util.HashSet<>();
        for (HexParcelOwnershipEntity own : ownershipDao.getAllForOwnerSync(ownerId)) {
            if (own == null || own.hexId == null || !seenHex.add(own.hexId)) {
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
