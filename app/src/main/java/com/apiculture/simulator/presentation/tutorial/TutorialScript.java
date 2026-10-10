package com.apiculture.simulator.presentation.tutorial;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.apiculture.simulator.R;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * Viñetas jugables. El texto largo está en {@code docs/tutorial.md}.
 * El orden del capítulo 1 adelanta la viñeta 16 (almacén) a antes de la 13,
 * porque la cosecha no sale sin almacén.
 */
public final class TutorialScript {

    public enum Screen {
        ANY, DASHBOARD, MAP, YARD, HIVE, MARKET, SHOP, WORKSHOP, OBRADORES
    }

    public enum Advance {
        /** Botón Siguiente. */
        NEXT,
        /** Llegar a {@link Step#screen}. */
        NAV,
        /** Un {@link TutorialEvent}. */
        EVENT,
        /** El botón adelanta un día de producción y entonces sigue. */
        SIMULATE_DAY,
        /** Capítulos 11 y 12. El botón hace llegar ya el viaje en marcha (una vez) y sigue. */
        FAST_TRIP,
        /** Capítulo 10. El botón acaba ya la máquina en la que está la primera tanda y sigue. */
        FAST_STAGE
    }

    public enum Anchor {
        NONE(false),
        COINS_XP(false, R.id.tv_stat_coins, R.id.progress_xp),
        WEATHER(false, R.id.tv_day, R.id.tv_season),
        BOTTOM_NAV(true),
        TAB_DASH(true, R.id.dashboardFragment),
        TAB_MAP(true, R.id.mapFragment),
        TAB_MARKET(true, R.id.marketFragment),
        TAB_HIVES(true, R.id.hivesFragment),
        TAB_OBRADORES(true, R.id.obradoresFragment),
        CLIMATE_LEGEND(false, R.id.card_climate_legend),
        MAP(false, R.id.map_container),
        YARD_BUY(false, R.id.btn_yard_buy_hive),
        YARD(false, R.id.yard_view),
        HIVE_BIO(false, R.id.tv_hive_header_honey, R.id.tv_hive_header_queen, R.id.tv_hive_header_varroa),
        HIVE_HARVEST(false, R.id.btn_harvest_top, R.id.btn_feed_top, R.id.btn_treat_top,
                R.id.btn_replace_queen_top),
        HIVE_HONEY(false, R.id.tv_honey_stock_in_hive),
        HARVEST(false, R.id.tile_quick_harvest),
        SHOP_TRUCK(false, R.id.card_shop_truck, R.id.btn_buy_truck),
        MARKET_GOODS(false, R.id.recycler_market_pills, R.id.market_tab_toggle),
        CONTRACTS_TAB(false, R.id.btn_market_contracts),
        MARKET_IBERIA(false, R.id.btn_market_iberia),
        CONTRACT_FARMER(false, R.id.ll_contract_farmer),
        CONTRACT_CROP(false, R.id.ll_contract_crop),
        CONTRACT_MIN(false, R.id.ll_contract_min),
        CONTRACT_DATES(false, R.id.ll_contract_window),
        CONTRACT_TRAVEL(false, R.id.ll_contract_travel),
        CONTRACT_REWARD(false, R.id.ll_contract_reward),
        DASH_WORKSHOP(false, R.id.tile_quick_workshop),
        WORKSHOP_BUILD(false, R.id.ll_workshop_build),
        WORKSHOP_MACHINES(false, R.id.ll_workshop_machines),
        WORKSHOP_BATCHES(false, R.id.ll_workshop_batches),
        WORKSHOP_STOCK(false, R.id.ll_workshop_stock, R.id.ll_workshop_wax),
        OBRADOR_CARD(false, R.id.rv_obradores),
        OBRADOR_ENTER(false, R.id.btn_obrador_enter);

        public final boolean onActivity;
        public final int[] viewIds;

        Anchor(boolean onActivity, int... viewIds) {
            this.onActivity = onActivity;
            this.viewIds = viewIds;
        }

        public boolean cardOnTop() {
            return this == BOTTOM_NAV || this == TAB_DASH || this == TAB_MAP || this == TAB_OBRADORES
                    || this == TAB_MARKET || this == TAB_HIVES || this == CLIMATE_LEGEND
                    || this == MAP || this == YARD_BUY || this == HARVEST
                    || this == SHOP_TRUCK
                    || this == CONTRACT_CROP || this == CONTRACT_MIN
                    || this == CONTRACT_DATES || this == CONTRACT_TRAVEL
                    || this == CONTRACT_REWARD || this == DASH_WORKSHOP
                    || this == WORKSHOP_MACHINES;
        }
    }

    public static final class Step {
        public final TutorialChapter chapter;
        /** Número de viñeta en docs/tutorial.md. */
        public final int vignette;
        @StringRes public final int textRes;
        public final Screen screen;
        public final Anchor anchor;
        public final Advance advance;
        @Nullable public final TutorialEvent event;
        /** La viñeta se dibuja encima de un diálogo, no sobre el mapa. */
        public final boolean inDialog;
        /** Ficha de Ramón en la parte alta del diálogo. */
        public final boolean dialogCardOnTop;

        Step(TutorialChapter chapter, int vignette, @StringRes int textRes,
                Screen screen, Anchor anchor, Advance advance, @Nullable TutorialEvent event) {
            this(chapter, vignette, textRes, screen, anchor, advance, event, false, false);
        }

        Step(TutorialChapter chapter, int vignette, @StringRes int textRes,
                Screen screen, Anchor anchor, Advance advance, @Nullable TutorialEvent event,
                boolean inDialog, boolean dialogCardOnTop) {
            this.chapter = chapter;
            this.vignette = vignette;
            this.textRes = textRes;
            this.screen = screen;
            this.anchor = anchor;
            this.advance = advance;
            this.event = event;
            this.inDialog = inDialog;
            this.dialogCardOnTop = dialogCardOnTop;
        }
    }

    private static final Map<TutorialChapter, Step[]> STEPS = new EnumMap<>(TutorialChapter.class);

    static {
        STEPS.put(TutorialChapter.FIRST_APIARY, new Step[] {
                // Capítulo 1, viñeta 1. Bienvenida.
                step(1, R.string.tutorial_c1_v01, Screen.DASHBOARD, Anchor.NONE, Advance.NEXT, null),
                // Capítulo 1, viñeta 2. Saldo, nivel y experiencia.
                step(2, R.string.tutorial_c1_v02, Screen.DASHBOARD, Anchor.COINS_XP, Advance.NEXT, null),
                // Capítulo 1, viñeta 3. Clima del día.
                step(3, R.string.tutorial_c1_v03, Screen.DASHBOARD, Anchor.WEATHER, Advance.NEXT, null),
                // Capítulo 1, viñeta 4. Menú inferior.
                step(4, R.string.tutorial_c1_v04, Screen.DASHBOARD, Anchor.BOTTOM_NAV, Advance.NEXT, null),
                // Capítulo 1, viñeta 5. Abrir el mapa.
                step(5, R.string.tutorial_c1_v05, Screen.MAP, Anchor.TAB_MAP, Advance.NAV, null),
                // Capítulo 1, viñeta 6. Verde disponible, rojo bloqueado, borde = clima.
                step(6, R.string.tutorial_c1_v06, Screen.MAP, Anchor.CLIMATE_LEGEND, Advance.NEXT, null),
                // Capítulo 1, viñeta 7. Pulsar un punto dentro de un hexágono verde.
                step(7, R.string.tutorial_c1_v07, Screen.MAP, Anchor.MAP, Advance.EVENT, TutorialEvent.INSTALL_CHOICE),
                // Capítulo 1, viñeta 7b. Diálogo «¿Qué quieres instalar?»: botón Instalar un apiario.
                dialogStep(70, R.string.tutorial_c1_v07b, TutorialEvent.PURCHASE_FORM, false),
                // Capítulo 1, viñeta 8. Ficha Instalar apiario: flora, saturación y nombre.
                dialogStep(8, R.string.tutorial_c1_v08, TutorialEvent.APIARY_INSTALLED, true),
                // Capítulo 1, viñeta 9. Entrar al apiario pulsando su imagen en el mapa.
                step(9, R.string.tutorial_c1_v09, Screen.YARD, Anchor.MAP, Advance.NAV, null),
                // Capítulo 1, viñeta 10. Abrir Nueva colmena.
                step(10, R.string.tutorial_c1_v10, Screen.YARD, Anchor.YARD_BUY, Advance.EVENT, TutorialEvent.BUY_HIVE_DIALOG),
                // Capítulo 1, viñeta 10b. Elegir la flor y el gráfico de mieladas del año.
                dialogStep(102, R.string.tutorial_c1_v10b, TutorialEvent.HIVE_BOUGHT, true),
                // Capítulo 1, viñeta 11. Abrir la ficha (el texto de la viñeta sale al llegar).
                step(11, R.string.tutorial_c1_v11_open, Screen.HIVE, Anchor.YARD, Advance.NAV, null),
                // Capítulo 1, viñeta 11. Miel, reina y varroa del encabezado.
                step(11, R.string.tutorial_c1_v11, Screen.HIVE, Anchor.HIVE_BIO, Advance.NEXT, null),
                // Capítulo 1, viñeta 12. Botón Recolectar y reserva de 3 kg.
                step(12, R.string.tutorial_c1_v12, Screen.HIVE, Anchor.HIVE_HARVEST, Advance.NEXT, null),
                // Capítulo 1, viñeta 16. Almacén, antes de cosechar.
                step(16, R.string.tutorial_c1_v16, Screen.ANY, Anchor.NONE, Advance.NEXT, null),
                step(161, R.string.tutorial_c1_v16_pick, Screen.MAP, Anchor.MAP, Advance.EVENT, TutorialEvent.INSTALL_CHOICE),
                dialogStep(162, R.string.tutorial_c1_v16_choice, TutorialEvent.PURCHASE_FORM, false),
                dialogStep(163, R.string.tutorial_c1_v16_name, TutorialEvent.WAREHOUSE_BOUGHT, true),
                step(17, R.string.tutorial_c1_v17_shop, Screen.MAP, Anchor.MAP, Advance.EVENT, TutorialEvent.SHOP_OPENED),
                step(171, R.string.tutorial_c1_v17_buy, Screen.SHOP, Anchor.SHOP_TRUCK, Advance.EVENT, TutorialEvent.TRUCK_FORM),
                dialogStep(172, R.string.tutorial_c1_v17_name, TutorialEvent.TRUCK_BOUGHT, true),
                step(173, R.string.tutorial_c1_v17_fleet, Screen.ANY, Anchor.NONE, Advance.NEXT, null),
                // Capítulo 1, viñeta 14. Recolectar hacia el almacén.
                step(14, R.string.tutorial_c1_v14, Screen.DASHBOARD, Anchor.HARVEST, Advance.EVENT, TutorialEvent.HARVESTED),
                // Capítulo 1, viñeta 15. Abrir el mercado.
                step(15, R.string.tutorial_c1_v15_open, Screen.MARKET, Anchor.TAB_MARKET, Advance.NAV, null),
                // Capítulo 1, viñeta 15. Precios, pedidos y contratos. No se vende: el camión aún no ha recogido.
                step(15, R.string.tutorial_c1_v15, Screen.MARKET, Anchor.MARKET_GOODS, Advance.NEXT, null),
                // Capítulo 1, viñeta 18. Cierre.
                step(18, R.string.tutorial_c1_v18, Screen.ANY, Anchor.NONE, Advance.NEXT, null),
        });

        // Capítulo 2. Cuidado de la colmena.
        STEPS.put(TutorialChapter.HIVE_CARE, new Step[] {
                step(TutorialChapter.HIVE_CARE, 1, R.string.tutorial_c2_v01),
                step(TutorialChapter.HIVE_CARE, 2, R.string.tutorial_c2_v02),
                step(TutorialChapter.HIVE_CARE, 3, R.string.tutorial_c2_v03),
        });

        // Capítulo 3. Siembra.
        STEPS.put(TutorialChapter.PLANTING, new Step[] {
                step(TutorialChapter.PLANTING, 1, R.string.tutorial_c3_v01),
                step(TutorialChapter.PLANTING, 2, R.string.tutorial_c3_v02),
        });

        // Capítulo 4. Transhumancia.
        STEPS.put(TutorialChapter.TRANSHUMANCE, new Step[] {
                step(TutorialChapter.TRANSHUMANCE, 1, R.string.tutorial_c4_v01),
                step(TutorialChapter.TRANSHUMANCE, 2, R.string.tutorial_c4_v02),
        });

        // Capítulo 5. Pedidos y contratos.
        STEPS.put(TutorialChapter.ORDERS, new Step[] {
                step(TutorialChapter.ORDERS, 1, R.string.tutorial_c5_v01),
                step(TutorialChapter.ORDERS, 2, R.string.tutorial_c5_v02),
                step(TutorialChapter.ORDERS, 3, R.string.tutorial_c5_v03,
                        Screen.MARKET, Anchor.TAB_MARKET, Advance.NAV, null),
                step(TutorialChapter.ORDERS, 4, R.string.tutorial_c5_v04,
                        Screen.MARKET, Anchor.CONTRACTS_TAB, Advance.EVENT, TutorialEvent.CONTRACTS_TAB),
                step(TutorialChapter.ORDERS, 5, R.string.tutorial_c5_v05,
                        Screen.MARKET, Anchor.MARKET_IBERIA, Advance.EVENT, TutorialEvent.CONTRACTS_IBERIA),
                step(TutorialChapter.ORDERS, 6, R.string.tutorial_c5_v06,
                        Screen.MARKET, Anchor.CONTRACT_FARMER, Advance.NEXT, null),
                step(TutorialChapter.ORDERS, 7, R.string.tutorial_c5_v07,
                        Screen.MARKET, Anchor.CONTRACT_CROP, Advance.NEXT, null),
                step(TutorialChapter.ORDERS, 8, R.string.tutorial_c5_v08,
                        Screen.MARKET, Anchor.CONTRACT_MIN, Advance.NEXT, null),
                step(TutorialChapter.ORDERS, 9, R.string.tutorial_c5_v09,
                        Screen.MARKET, Anchor.CONTRACT_DATES, Advance.NEXT, null),
                step(TutorialChapter.ORDERS, 10, R.string.tutorial_c5_v10,
                        Screen.MARKET, Anchor.CONTRACT_TRAVEL, Advance.NEXT, null),
                step(TutorialChapter.ORDERS, 11, R.string.tutorial_c5_v11,
                        Screen.MARKET, Anchor.CONTRACT_REWARD, Advance.NEXT, null),
                step(TutorialChapter.ORDERS, 12, R.string.tutorial_c5_v12,
                        Screen.MARKET, Anchor.NONE, Advance.NEXT, null),
                step(TutorialChapter.ORDERS, 13, R.string.tutorial_c5_v13,
                        Screen.MARKET, Anchor.NONE, Advance.NEXT, null),
        });

        // Capítulo 6. Camión.
        STEPS.put(TutorialChapter.TRUCK, new Step[] {
                step(TutorialChapter.TRUCK, 1, R.string.tutorial_c6_v01),
                step(TutorialChapter.TRUCK, 2, R.string.tutorial_c6_v02),
        });

        // Capítulo 7. Clima nuevo. El nombre del clima entra en el texto.
        STEPS.put(TutorialChapter.CLIMATE, new Step[] {
                new Step(TutorialChapter.CLIMATE, 1, R.string.tutorial_c7_v01,
                        Screen.ANY, Anchor.NONE, Advance.NEXT, null),
        });

        // Capítulo 8. Mercado internacional (puerto, barco y mercados).
        STEPS.put(TutorialChapter.INTERNATIONAL, new Step[] {
                step(TutorialChapter.INTERNATIONAL, 1, R.string.tutorial_c8_v01),
                step(TutorialChapter.INTERNATIONAL, 2, R.string.tutorial_c8_v02),
                step(TutorialChapter.INTERNATIONAL, 3, R.string.tutorial_c8_v03),
                step(TutorialChapter.INTERNATIONAL, 4, R.string.tutorial_c8_v04),
        });

        // Capítulo 9. Obrador. Sale al acabar el capítulo 1: sin obrador no se cosecha.
        STEPS.put(TutorialChapter.WORKSHOP, new Step[] {
                // Capítulo 9, viñeta 1. La miel nueva pasa por el obrador.
                step(TutorialChapter.WORKSHOP, 1, R.string.tutorial_c9_v01),
                // Capítulo 9, viñeta 2. La pestaña Obradores.
                step(TutorialChapter.WORKSHOP, 2, R.string.tutorial_c9_v02,
                        Screen.OBRADORES, Anchor.TAB_OBRADORES, Advance.NAV, null),
                // Capítulo 9, viñeta 3. La tarjeta: máquinas y miel guardada.
                step(TutorialChapter.WORKSHOP, 3, R.string.tutorial_c9_v03,
                        Screen.OBRADORES, Anchor.OBRADOR_CARD, Advance.NEXT, null),
                // Capítulo 9, viñeta 4. Entrar y comprarle a Toni las máquinas (o en la pantalla del obrador).
                step(TutorialChapter.WORKSHOP, 4, R.string.tutorial_c9_v04,
                        Screen.OBRADORES, Anchor.OBRADOR_ENTER, Advance.EVENT, TutorialEvent.WORKSHOP_READY),
                // Capítulo 9, viñeta 5. El recorrido de una tanda.
                step(TutorialChapter.WORKSHOP, 5, R.string.tutorial_c9_v05,
                        Screen.ANY, Anchor.NONE, Advance.NEXT, null),
                // Capítulo 9, viñeta 6. Cosechar: el camión vuelve al obrador.
                step(TutorialChapter.WORKSHOP, 6, R.string.tutorial_c9_v06),
        });

        // Capítulo 10. Primera tanda. Sale cuando entra la primera tanda en el obrador. Se explica cada
        // máquina y, esta primera vez, el botón adelanta su tiempo.
        STEPS.put(TutorialChapter.WORKSHOP_PACKING, new Step[] {
                // Capítulo 10, viñeta 1. Primera tanda: te adelanto los tiempos.
                step(TutorialChapter.WORKSHOP_PACKING, 1, R.string.tutorial_c10_v01),
                // Capítulo 10, viñeta 2. Sala de recepción.
                step(TutorialChapter.WORKSHOP_PACKING, 2, R.string.tutorial_c10_v05,
                        Screen.ANY, Anchor.NONE, Advance.FAST_STAGE, null),
                // Capítulo 10, viñeta 3. Desoperculadora y cera.
                step(TutorialChapter.WORKSHOP_PACKING, 3, R.string.tutorial_c10_v06,
                        Screen.ANY, Anchor.NONE, Advance.FAST_STAGE, null),
                // Capítulo 10, viñeta 4. Extractor.
                step(TutorialChapter.WORKSHOP_PACKING, 4, R.string.tutorial_c10_v07,
                        Screen.ANY, Anchor.NONE, Advance.FAST_STAGE, null),
                // Capítulo 10, viñeta 5. Madurador: lo más largo.
                step(TutorialChapter.WORKSHOP_PACKING, 5, R.string.tutorial_c10_v08,
                        Screen.ANY, Anchor.NONE, Advance.FAST_STAGE, null),
                // Capítulo 10, viñeta 6. Repartir la tanda con Toni (punto rojo en Obradores).
                step(TutorialChapter.WORKSHOP_PACKING, 6, R.string.tutorial_c10_v02,
                        Screen.OBRADORES, Anchor.OBRADOR_ENTER, Advance.EVENT, TutorialEvent.WORKSHOP_FORMAT),
                // Capítulo 10, viñeta 7. Envasadora.
                step(TutorialChapter.WORKSHOP_PACKING, 7, R.string.tutorial_c10_v09,
                        Screen.ANY, Anchor.NONE, Advance.FAST_STAGE, null),
                // Capítulo 10, viñeta 8. Tarros y cera.
                step(TutorialChapter.WORKSHOP_PACKING, 8, R.string.tutorial_c10_v03,
                        Screen.ANY, Anchor.NONE, Advance.NEXT, null),
                // Capítulo 10, viñeta 9. Mejoras con Toni.
                step(TutorialChapter.WORKSHOP_PACKING, 9, R.string.tutorial_c10_v04),
        });

        // Capítulo 11. Primera recogida. Sale cuando hay en marcha la primera recogida de alzas.
        STEPS.put(TutorialChapter.FIRST_COLLECT, new Step[] {
                // Capítulo 11, viñeta 1. El camión va a por las alzas: esta vez llega ya.
                step(TutorialChapter.FIRST_COLLECT, 1, R.string.tutorial_c11_v01,
                        Screen.ANY, Anchor.NONE, Advance.FAST_TRIP, TutorialEvent.FIRST_COLLECT_TRIP),
                // Capítulo 11, viñeta 2. Desde ahora los viajes tardan lo suyo.
                step(TutorialChapter.FIRST_COLLECT, 2, R.string.tutorial_c11_v02),
        });

        // Capítulo 12. Primera venta. Sale cuando hay en marcha el primer viaje de venta o de comanda.
        STEPS.put(TutorialChapter.FIRST_SALE, new Step[] {
                // Capítulo 12, viñeta 1. El camión lleva la miel al comprador: esta vez llega ya.
                step(TutorialChapter.FIRST_SALE, 1, R.string.tutorial_c12_v01,
                        Screen.ANY, Anchor.NONE, Advance.FAST_TRIP, TutorialEvent.FIRST_SALE_TRIP),
                // Capítulo 12, viñeta 2. Cobrado; desde ahora cada viaje tarda lo suyo.
                step(TutorialChapter.FIRST_SALE, 2, R.string.tutorial_c12_v02),
        });
    }

    private TutorialScript() {
    }

    public static Step[] steps(TutorialChapter chapter) {
        Step[] list = STEPS.get(chapter);
        return list != null ? list : new Step[0];
    }

    public static Map<TutorialChapter, Step[]> all() {
        return Collections.unmodifiableMap(STEPS);
    }

    private static Step step(int vignette, @StringRes int text, Screen screen, Anchor anchor,
            Advance advance, @Nullable TutorialEvent event) {
        return new Step(TutorialChapter.FIRST_APIARY, vignette, text, screen, anchor, advance, event);
    }

    /** Capítulo 1. Viñeta sobre un diálogo ya abierto. */
    private static Step dialogStep(int vignette, @StringRes int text, TutorialEvent event, boolean cardOnTop) {
        return new Step(TutorialChapter.FIRST_APIARY, vignette, text,
                Screen.MAP, Anchor.NONE, Advance.EVENT, event, true, cardOnTop);
    }

    private static Step step(TutorialChapter chapter, int vignette, @StringRes int text) {
        return step(chapter, vignette, text, Screen.ANY, Anchor.NONE, Advance.NEXT, null);
    }

    private static Step step(TutorialChapter chapter, int vignette, @StringRes int text,
            Screen screen, Anchor anchor, Advance advance, @Nullable TutorialEvent event) {
        return new Step(chapter, vignette, text, screen, anchor, advance, event);
    }
}
