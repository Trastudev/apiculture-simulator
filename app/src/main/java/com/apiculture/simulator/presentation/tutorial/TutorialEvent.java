package com.apiculture.simulator.presentation.tutorial;

/**
 * Hechos del juego que avanzan una viñeta o abren un capítulo.
 * Quien lo dispara deja el comentario del capítulo junto a la llamada.
 */
public enum TutorialEvent {
    /** Capítulo 1, viñeta 7. El jugador ha pulsado un hexágono y sale «¿Qué quieres instalar?». */
    INSTALL_CHOICE,
    /** Capítulo 1, viñeta 7b. Se ha abierto el diálogo Instalar apiario. */
    PURCHASE_FORM,
    /** Capítulo 1, viñeta 8. Apiario instalado. */
    APIARY_INSTALLED,
    /** Capítulo 1, viñeta 10. Se abre el diálogo de comprar colmena. */
    BUY_HIVE_DIALOG,
    /** Capítulo 1, viñeta 10b. Gráfico de mieladas al elegir la flor. */
    FLORA_CHART,
    /** Capítulo 1, viñeta 10b. Colmena comprada y colocada. */
    HIVE_BOUGHT,
    /** Capítulo 1, viñeta 16. Almacén instalado. */
    WAREHOUSE_BOUGHT,
    /** Capítulo 1. El jugador abre la tienda del mapa para comprar el camión. */
    SHOP_OPENED,
    /** Capítulo 1. Diálogo de nombre y almacén del camión. */
    TRUCK_FORM,
    /** Capítulo 1, viñeta 13. Día adelantado solo en el tutorial. */
    DAY_SIMULATED,
    /** Capítulo 1, viñeta 14. Cosecha hecha. */
    HARVESTED,
    /** Capítulo 2. Alimentar. */
    CARE_FEED,
    /** Capítulo 2. Tratar varroa. */
    CARE_TREAT,
    /** Capítulo 2. Cambiar reina. */
    CARE_QUEEN,
    /** Capítulo 3. Abrir la siembra. */
    PLANTING,
    /** Capítulo 4. Transhumar. */
    TRANSHUMANCE,
    /** Capítulo 5. Pestaña de pedidos. */
    ORDERS_TAB,
    /** Capítulo 5. El jugador ya puede ver contratos (nivel 2). */
    CONTRACTS_OFFER,
    /** Capítulo 5. Pestaña de contratos. */
    CONTRACTS_TAB,
    /** Capítulo 5. Región Iberia en el mercado. */
    CONTRACTS_IBERIA,
    /** Capítulo 6. Camión comprado. */
    TRUCK_BOUGHT,
    /** Capítulo 7. Un nivel acaba de abrir un clima. El detalle es el nombre. */
    CLIMATE,
    /** Capítulo 8. El jugador abre un puerto. */
    PORT_OPENED,
    /** Capítulo 8. El jugador compra un barco. */
    SHIP_BOUGHT
}
