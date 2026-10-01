package com.apiculture.simulator.domain.parcel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Resultado del tick de cultivos (anuales caducados, árboles mantenidos o perdidos). */
public final class CropTickResult {

    public static final class Removed {
        public final String hexId;
        public final String floraKey;

        public Removed(String hexId, String floraKey) {
            this.hexId = hexId;
            this.floraKey = floraKey;
        }
    }

    public final List<String> notes;
    public final List<Removed> removed;
    public final List<String> touchedHexIds;

    public CropTickResult(List<String> notes, List<Removed> removed, List<String> touchedHexIds) {
        this.notes = notes == null ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(notes));
        this.removed = removed == null ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(removed));
        this.touchedHexIds = touchedHexIds == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(touchedHexIds));
    }

    public static CropTickResult empty() {
        return new CropTickResult(Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
    }
}
