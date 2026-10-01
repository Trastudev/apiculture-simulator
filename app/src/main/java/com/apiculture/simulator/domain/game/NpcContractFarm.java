package com.apiculture.simulator.domain.game;

import com.apiculture.simulator.domain.map.PlayableMapRegion;
import com.apiculture.simulator.domain.parcel.HexParcel;

/**
 * Hex amarillo de convenio: plantación fija, NPC y (opcional) reserva en barbecho.
 */
public final class NpcContractFarm {
    public final HexParcel parcel;
    public final String flora;
    public final String npcName;
    public final String estateName;
    public final String climateLabel;
    public final boolean reserve;
    public final PollinationPayTerms terms;
    public final int portraitIndex;

    public NpcContractFarm(HexParcel parcel, String flora, String npcName, String estateName,
            String climateLabel, boolean reserve, PollinationPayTerms terms, int portraitIndex) {
        this.parcel = parcel;
        this.flora = flora;
        this.npcName = npcName;
        this.estateName = estateName;
        this.climateLabel = climateLabel;
        this.reserve = reserve;
        this.terms = terms;
        this.portraitIndex = portraitIndex;
    }

    public String hexId() {
        return parcel != null ? parcel.id : "";
    }

    public PlayableMapRegion region() {
        return PlayableMapRegion.fromHexId(hexId());
    }
}
