# Tutorial de Ramón

Guion del mentor. El retrato es `ic_apicultor`. El nombre en pantalla es Ramón.
El código que lo ejecuta está en `presentation/tutorial/`, y cada viñeta lleva un comentario
`Capítulo N, viñeta M` para localizarla. Los textos viven en `strings.xml` con el prefijo `tutorial_`.

Para volver a ver el capítulo 1 en una partida ya empezada: menú del panel → Tutorial.

## Cómo se ve

La pantalla se oscurece y queda un hueco sobre el control del que se habla.
Abajo (o arriba, si el control está en la barra inferior) sale la ficha de Ramón:

- Retrato.
- Bocadillo.
- **Siguiente**, cuando solo hay que leer.
- El botón pasa a **Adelantar un día** en la viñeta 13.
- **Saltar este capítulo**. No interrumpe una compra a medias: solo cierra el capítulo.

El capítulo 1 sale al entrar al panel si el jugador todavía no tiene terrenos.
Quien ya tenía apiarios al instalar esta versión no lo ve, salvo que lo abra desde el menú.

## Capítulo 1 — El primer colmenar

| # | Pantalla | Qué se ilumina | Qué dice Ramón | Cómo se avanza |
|---|---|---|---|---|
| 1 | Panel | Solo el diálogo | Soy Ramón. Esta explotación es tuya. Empezamos con 10.000 B y un calendario que avanza día a día. Las abejas trabajan mientras tú no estás. | Siguiente |
| 2 | Panel | Saldo y experiencia | Arriba están tu dinero y tu nivel. Subir de nivel abre climas nuevos. Vender miel, instalar apiarios y comprar colmenas dan experiencia. | Siguiente |
| 3 | Panel | Día y estación | El clima del día decide cuánto néctar hay. Un día malo produce menos. La colonia sigue. | Siguiente |
| 4 | Panel | Menú inferior | Inicio es el resumen. Apiarios son tus fincas. El mapa es donde instalas. El mercado es donde sale la miel. Desde Inicio también puedes elegir un mercado y vender. | Siguiente |
| 5 | Panel | Pestaña Mapa | El primer paso es un apiario. Abre el mapa. | Abre el mapa |
| 6 | Mapa | Leyenda de clima | El relleno verde está disponible. El rojo está bloqueado: ese clima pide más nivel. El color del borde es el clima, y la leyenda de abajo te dice cuál es cada uno. Un terreno tuyo se pinta de violeta. | Siguiente |
| 7 | Mapa | El mapa | Pulsa un punto dentro de un hexágono verde, donde quieras instalar tu primer apiario. | Pulsa un punto disponible |
| 7b | ¿Qué quieres instalar? | Botón Instalar un apiario | Pulsa Instalar un apiario. | Pulsa ese botón |
| 8 | Instalar apiario | Flora, saturación y nombre | La flora silvestre ya está. La ficha muestra plazas, clima y saturación. El precio es el pago del apiario. Escribe el nombre y confirma. | Instala el apiario |
| 9 | Mapa | Imagen del apiario | Pulsa la imagen del apiario en el mapa para entrar. | Pulsa la imagen |
| 10 | Apiario | Nueva colmena, viñeta arriba | Compra la primera colmena y colócala en el apiario. | Abre el diálogo |
| 10b | Comprar colmena | Flor y gráfico del año | Elige la flor del néctar. El gráfico muestra las mieladas y la disponibilidad durante el año. Elige una flor con mielada ahora. | Compra la colmena |
| 11 | Ficha de la colmena | Miel, reina y varroa, a todo el ancho | Arriba ves la miel, la reina y la varroa. | Siguiente |
| 12 | Ficha | Botón Recolectar | Recolectar deja siempre 3 kg de reserva. | Siguiente |
| 16 | Mapa y ¿Qué quieres instalar? | Botón Instalar un almacén | Sin almacén no hay envío. En el diálogo solo queda libre Instalar un almacén. | Instala el almacén |
| 14 | Inicio | Botón Recolectar | En Inicio, pulsa Recolectar. | Recolecta |
| 13 | Cualquiera | Botón del propio diálogo | Adelanto un día para que veas el ciclo. En la partida normal el día corre solo. | Adelantar un día |
| 15 | Mercado | Tipos de miel, pedidos y contratos | Cada tipo tiene su precio y su demanda. El envío sale del almacén cuando el camión ya ha recogido la miel. También verás pedidos y contratos. | Siguiente, después de abrir el mercado |
| 18 | Cualquiera | Solo el diálogo | Eso es el oficio: apiario, colmena, día, cosecha, almacén y mercado. Cuando hagas algo nuevo, vuelvo. | Cierra |

La viñeta 16 del guion se juega antes de la 13. La recolección del juego no sale si no hay almacén.

El capítulo 1 no pide confirmar un envío: el camión todavía no ha recogido la miel.

El botón Vender de Inicio no forma parte del tutorial. Sigue en el panel hasta que se quite aparte.

## Capítulos que se activan con la acción

Salen una sola vez, la primera vez que ocurre la acción, y solo cuando el capítulo 1 ya terminó o se saltó.
Si Ramón está hablando, el capítulo nuevo espera.

**Cuidado de la colmena.** Al pulsar Alimentar, Tratar o Cambiar reina.

1. Alimentar cubre 1 día o 7. Baja el consumo de miel.
2. El tratamiento antivarroa dura 7 días.
3. Una reina nueva cambia la calidad de la cría.

**Siembra.** Al abrir la siembra de flora en un hexágono propio.

1. La flora silvestre ya venía con el terreno.
2. El cultivo se siembra, espera y da otro tipo de miel.

**Transhumancia.** Al pulsar Transhumar.

1. Las colmenas viajan por carretera. Mientras van de camino no producen en el destino.
2. Sirve para seguir una floración o para cumplir un contrato.

**Pedidos y contratos.** Al abrir la pestaña Pedidos o Contratos del mercado.

1. El pedido se cumple con miel del almacén y un camión.
2. El contrato se cumple llevando colmenas al apiario de esa finca. Cobras por el servicio.

**Camión.** Al comprar el primer camión.

1. Lleva miel y, según el nivel, colmenas. La plaza sale del nivel del almacén.
2. Antes de salir se ve origen, destino, cobro, viaje y neto. La ruta queda en el mapa.

**Clima nuevo.** Una viñeta cada vez que un nivel abre un clima. Esos hexágonos pasan de rojo a verde.
Continental al 5, altiplano de Madagascar al 10, atlántico al 15, tropical al 20, alta montaña al 25,
desierto al 30, sur al 35, Sudáfrica al 40.

## Mercado internacional

Se activa al abrir un puerto o al comprar el primer barco.

| # | Pantalla | Qué dice Ramón | Cómo se avanza |
|---|---|---|---|
| 1 | Puerto o tienda | A partir del nivel 30 puedes usar el puerto. La cuota son 10.000 B. Ahí amarras un barco. El amarre es tuyo y cuesta 2.500 B. | Siguiente |
| 2 | | El barco mueve miel entre regiones. No mueve colmenas. El camión lleva la carga del almacén al puerto, y el barco sigue por mar. | Siguiente |
| 3 | | La ruta del barco se ve en el mapa, igual que el camión en la carretera. | Siguiente |
| 4 | | Al nivel 35 pagas 15.000 B por cada mercado internacional. El precio ya no es el de tu provincia: vendes donde la miel vale más, descontando el viaje. | Siguiente |

Ranking, eventos globales y admin quedan fuera del tutorial.
