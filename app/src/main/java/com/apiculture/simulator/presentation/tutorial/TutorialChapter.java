package com.apiculture.simulator.presentation.tutorial;

/**
 * Capítulos del tutorial. El guion está en {@code docs/tutorial.md}.
 * Cada viñeta se comenta en {@link TutorialScript} como «Capítulo N, viñeta M».
 */
public enum TutorialChapter {
    /** Capítulo 1. El primer colmenar. */
    FIRST_APIARY,
    /** Capítulo 2. Cuidado de la colmena. Se abre al alimentar, tratar o cambiar la reina. */
    HIVE_CARE,
    /** Capítulo 3. Siembra. Se abre al plantar flora en un terreno propio. */
    PLANTING,
    /** Capítulo 4. Transhumancia. Se abre al mover colmenas. */
    TRANSHUMANCE,
    /** Capítulo 5. Pedidos y contratos. Se abre en esas pestañas del mercado. */
    ORDERS,
    /** Capítulo 6. Camión. Se abre al comprar el primero. */
    TRUCK,
    /** Capítulo 7. Clima nuevo. Una viñeta por cada clima que abre el nivel. */
    CLIMATE,
    /** Capítulo 8. Mercado internacional: puerto, barco y cuota del mercado. */
    INTERNATIONAL
}
