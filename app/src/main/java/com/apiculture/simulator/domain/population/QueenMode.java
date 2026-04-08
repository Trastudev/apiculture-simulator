package com.apiculture.simulator.domain.population;

/**
 * Estado reproductivo / de reemplazo de la reina (resolución diaria).
 */
public enum QueenMode {
    /** Reina pone huevos de obrera. */
    LAYING,
    /**
     * Tras muerte de reina: ventana de 4 días (índices 0–3) para iniciar celdilla real con huevos
     * de obrera; si pasa sin éxito → {@link #ORPHANED}.
     */
    REPLACE_WINDOW,
    /** Desarrollo de nueva reina (día 0–16, emerge al cerrar 16). */
    QUEEN_DEVELOPING,
    /** Post-emergencia: maduración, vuelo nupcial, hasta primera puesta (día 0–11). */
    VIRGIN_PRE_LAYING,
    /** Sin reina ni celdilla viable: solo mueren obreras y emerge cría acumulada. */
    ORPHANED,
    /** Población total bajo umbral: colonia funcionalmente extinta. */
    COLLAPSED
}
