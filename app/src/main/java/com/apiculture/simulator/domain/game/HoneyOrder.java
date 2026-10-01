package com.apiculture.simulator.domain.game;

import androidx.annotation.NonNull;

import com.apiculture.simulator.data.local.entity.HoneyOrderEntity;

public final class HoneyOrder {
    public final String id;
    public final String npcName;
    public final int portraitIndex;
    public final String floraKey;
    public final double kg;
    public final double unitPrice;
    public final String destHexId;
    public final double destLat;
    public final double destLng;
    public final String destLabel;
    public final String region;
    public final int createdDayKey;
    public final long expireEpochMs;
    public final int band;

    public HoneyOrder(String id, String npcName, int portraitIndex, String floraKey, double kg,
            double unitPrice, String destHexId, double destLat, double destLng, String destLabel,
            String region, int createdDayKey, long expireEpochMs) {
        this(id, npcName, portraitIndex, floraKey, kg, unitPrice, destHexId, destLat, destLng,
                destLabel, region, createdDayKey, expireEpochMs, 0);
    }

    public HoneyOrder(String id, String npcName, int portraitIndex, String floraKey, double kg,
            double unitPrice, String destHexId, double destLat, double destLng, String destLabel,
            String region, int createdDayKey, long expireEpochMs, int band) {
        this.id = id;
        this.npcName = npcName;
        this.portraitIndex = portraitIndex;
        this.floraKey = floraKey;
        this.kg = kg;
        this.unitPrice = unitPrice;
        this.destHexId = destHexId;
        this.destLat = destLat;
        this.destLng = destLng;
        this.destLabel = destLabel;
        this.region = region;
        this.createdDayKey = createdDayKey;
        this.expireEpochMs = expireEpochMs;
        this.band = Math.max(0, Math.min(OfferBand.COUNT - 1, band));
    }

    public double payout() {
        return Math.round(kg * unitPrice * 100.0) / 100.0;
    }

    public boolean expired(long nowMs) {
        return expireEpochMs > 0L && nowMs >= expireEpochMs;
    }

    public long remainingMs(long nowMs) {
        return Math.max(0L, expireEpochMs - nowMs);
    }

    @NonNull
    public HoneyOrderEntity toEntity() {
        HoneyOrderEntity e = new HoneyOrderEntity();
        e.id = id != null ? id : "";
        e.npcName = npcName;
        e.portraitIndex = portraitIndex;
        e.floraKey = floraKey;
        e.kg = kg;
        e.unitPrice = unitPrice;
        e.destHexId = destHexId;
        e.destLat = destLat;
        e.destLng = destLng;
        e.destLabel = destLabel;
        e.region = region;
        e.createdDayKey = createdDayKey;
        e.expireEpochMs = expireEpochMs;
        e.taken = false;
        e.claimedBy = "";
        e.band = band;
        return e;
    }

    @NonNull
    public static HoneyOrder fromEntity(@NonNull HoneyOrderEntity e) {
        return new HoneyOrder(
                e.id,
                e.npcName,
                e.portraitIndex,
                e.floraKey,
                e.kg,
                e.unitPrice,
                e.destHexId,
                e.destLat,
                e.destLng,
                e.destLabel,
                e.region,
                e.createdDayKey,
                e.expireEpochMs,
                e.band);
    }

    @NonNull
    @Override
    public String toString() {
        return id;
    }
}
