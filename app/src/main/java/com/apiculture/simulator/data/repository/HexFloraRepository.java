package com.apiculture.simulator.data.repository;

import androidx.annotation.Nullable;

import com.apiculture.simulator.data.local.dao.HexFloraDao;
import com.apiculture.simulator.data.local.entity.HexFloraEntity;
import com.apiculture.simulator.domain.parcel.HexFlora;
import com.apiculture.simulator.domain.parcel.HexParcel;

import java.util.List;

/**
 * Flora por {@code hexId}: asignación pseudoaleatoria fijada al persistir en Room (mismo hex → misma flora).
 */
public class HexFloraRepository {

    private final HexFloraDao dao;

    public HexFloraRepository(HexFloraDao dao) {
        this.dao = dao;
    }

    /** Obtiene la flora guardada o la genera, persiste y devuelve. Solo hilo de fondo. */
    public String getOrCreateFloraForHexBlocking(@Nullable String hexId) {
        if (hexId == null || hexId.isEmpty()) {
            return HexFlora.FLORA_TYPES[0];
        }
        HexFloraEntity row = dao.getByHexIdSync(hexId);
        if (row != null && row.floraType != null && !row.floraType.isEmpty()) {
            return row.floraType;
        }
        String flora = HexFlora.randomFloraForHexId(hexId);
        HexFloraEntity e = new HexFloraEntity();
        e.hexId = hexId;
        e.floraType = flora;
        dao.upsert(e);
        return flora;
    }

    /** Precarga filas para todos los hex del overlay (omitir si ya existen). */
    public void seedAllParcelsBlocking(@Nullable List<HexParcel> parcels) {
        if (parcels == null) {
            return;
        }
        for (HexParcel p : parcels) {
            if (p == null || p.id == null || p.id.isEmpty()) {
                continue;
            }
            if (dao.getByHexIdSync(p.id) != null) {
                continue;
            }
            HexFloraEntity e = new HexFloraEntity();
            e.hexId = p.id;
            e.floraType = HexFlora.randomFloraForHexId(p.id);
            dao.upsert(e);
        }
    }
}
