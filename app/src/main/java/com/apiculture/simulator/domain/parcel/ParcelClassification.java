package com.apiculture.simulator.domain.parcel;

/** Resultado del umbral de tierra sobre un hexágono. */
public enum ParcelClassification {
    /** ≥ umbral interior (típicamente 90&nbsp;% tierra): parcela válida no costera. */
    VALID_INLAND,
    /** Entre costa mínima e interior: válida costera (recorte opcional no implementado en geometría). */
    COASTAL,
    /** Por debajo del mínimo: descartar. */
    DISCARD
}
