# Competencia entre jugadores (más allá del ranking)

Notas de diseño para recuperar más adelante. No es una spec de implementación: prioriza ideas que encajan con el simulador actual (mapa hex, mercado global, eventos, transhumancia, marca de miel).

Hoy el ranking es lifetime (nivel, miel vendida, colmenas, obreras) y por región (global / Iberia / Sudáfrica). Eso mide antigüedad. La competencia que se busca es **asimétrica y temporal**: flora, clima, mercado y mapa.

---

## Ideas (backlog)

### 1. Melé territorial *(empezar por aquí)*

Modelo acordado: **piscina diaria de néctar por flora**. Primero pecorean en casa las obreras propias **y** las vecinas que ya no cabían en su finca, a prorrata por recolectora. **Si aún queda néctar en casa, las tuyas no salen.** Solo si la piscina se acaba y todavía te quedan recolectoras, esas van a parcelas vecinas con la misma flora (tope 5 % de tus forrajeras por lado). El informe diario enseña lo traído y lo que se llevaron.

Detalle: ver «Modelo de néctar (piscina + obreras)» más abajo.

### 2. Guerra de mercado

El precio ya baja cuando alguien vende (`HoneyMarketEngine.supplyPriceMultiplier`, ventas en `floraSalesUtc`). Eso es competencia, pero no se siente.

Hacerlo visible y táctico:

- Tablón diario: “hoy se han vertido X kg de romero; el precio ya cae un N %”.
- Prima al primero: los primeros kg del día (o de la mielada) pagan más.
- Cornering suave: inundar un tipo barato para financiar uno raro.

### 3. Ferias y vendimias (sustituye al ranking lifetime)

- Vendimia de flora: ventana semanal, gana quien más kg de esa flora venda, con tope por colmena.
- Feria de la miel: calidad (monoflora, añada `vintageFactor`, salud), no kg totales. Medalla / DOP por tipo. Encaja con la marca ya reclamada.
- Pódium del evento global: el surge de demanda es cooperativo (hitos 15–100 %). Añadir top 3 de aportación individual sin quitar el premio colectivo.

### 4. Contratos de polinización

Dos pestañas en Mercado: **Mercado** (venta al mayor, la de ahora) y **Contratos** (convenios NPC). Polinización = % de néctar recogido en su plantación; mínimo para cobrar; extra si te pasas; miel siempre tuya. Duración = floración importante; al bajar, las colmenas vuelven solas. Traslado informado en B/hex.

Detalle, reglas y 5 ofertas de ejemplo: [`docs/contratos-polinizacion.md`](contratos-polinizacion.md).

### 5. Evitar

- Combate entre colmenas, sabotaje, velutina lanzable al vecino.
- Leaderboards solo de dinero/kg lifetime.
- Griefing de mapa (bloquear hexes sin usarlos). Si hay exclusividad, hace falta uso o pérdida.

### Orden de ataque sugerido

| Prioridad | Qué | Por qué |
|---|---|---|
| 1 | Pódium en el evento global + tablón de saturación del mercado | Backend casi listo (`globalEventProgress`, `floraSalesUtc`) |
| 2 | Feria semanal por flora | Reutiliza ranking por flora y `vintageFactor` |
| 3 | Piscina de néctar + pecoreo vecinal | Convierte el mapa en competencia real y arregla el crowding |
| 4 | Contratos de polinización | Loop nuevo, coherente con cultivos y trashumancia |

La pieza que más cambia la sensación de “hay otros jugadores” no es otro ranking: es ver que el vecino te come el néctar y que tu venta de hoy le baja el precio al resto.

---

## Cómo está hoy (legacy, a sustituir)

No hay piscina de néctar. El cupo es por **colmena** (`nativeHiveSlots` 5 / `plantedHiveSlots` 3) con `crowdingFactor = 1/(1+0,28×extra)`, suelo 0,45. Solo cuenta las colmenas del dueño. Una de 12 k obreras pesa igual que una de 80 k.

La fórmula actual mezcla todo en un solo multiplicador:

```
foragers = obreras × 0,24 × temp × cielo
kg = foragers × kgPerForagerFullFlow × néctar01 × salud × pienso × ruido
```

`néctar01` ya incluye floración + añada + clima + crowding. El gráfico de flora pinta `nectar01` con 1 colmena (no refleja saturación). `secondaryNectarShare` está en JSON y no se usa.

A 80 k adultas y néctar 1,0: ~4,3 kg/colmena·día. Cinco colmenas nativas ≈ 21,5 kg/hex.

---

## Modelo de néctar (piscina + obreras)

Tres capas separadas. El secreto floral no depende de si las abejas salen; el vuelo sí.

### 1. Piscina diaria (las flores)

El gráfico de floración es **solo** la explosión: `NectarFlow.intensity01` (0–~120 %). `height` de cada pico en `nectarFlora` es ese porcentaje (lavanda 1,10, bosque 0,80, segundo pico de romero 0,30). El gráfico no debe meter crowding ni clima de vuelo.

```
bloom01     = curva de la flora (picos + hierbas de fondo + desfase climático)
piscinaKg   = baseDailyNectarKg × bloom01 × vintage × nectarSeasonMult
```

Parámetros `baseDailyNectarKgNative` / `baseDailyNectarKgPlanted` (por tipo de flora):

| | Valor de calibración (bloom = 1,0) | Equivale a |
|---|---|---|
| Flora nativa | 20 kg/hex·día | ~4–5 colmenas a 80 k, tiempo neutro |
| Cultivo plantado | 20 kg/hex·día | igual, a floración plena |

Una colmena a 80 k pide ~4,3 kg: cinco ya rozan el cupo (21,5 > 20) y desbordan un poco. El néctar no usado **no se guarda**: se secreta cada día.

`MAX_HIVES_PER_SITE = 10` es el tope de cajas por apiario (`siteId`), no por hexágono ni por flores.

### 2. Demanda de las obreras (las colmenas)

Solo pecorean las forrajeras, no toda la colonia. Salud y pienso van aquí (colonia enferma / alimentada), no a las flores.

```
forrajeras   = obrerasAdultas × foragerFraction          // 0,24
potencialKg  = forrajeras × kgPerForagerFullFlow × salud × pienso
```

`kgPerForagerFullFlow` (0,0002244375) es el **consumo de néctar por obrera y día** a vuelo pleno. Una colmena a 80 k pide ~4,3 kg. Una a 12 k pide ~0,65 kg: el invierno no satura el hex.

### 3. Vuelo a posteriori (el tiempo)

Los **cinco** cielos de `DailySkyCondition` afectan el pecoreo, en casa y al cruzar. No cambian la piscina de néctar; cambian cuánto se puede volar **en ese hex**.

| Cielo | Enum | `cieloM` |
|---|---|---|
| Sol | `SUN` | 1,25 |
| Claros / variable | `VARIABLE` | 1,10 |
| Nubes | `CLOUDY` | 0,85 |
| Viento | `WINDY` | 0,70 |
| Lluvia | `RAINY` | **0** |

```
factorSitio(hex) = tempM(hex) × cieloM(hex)
salida(hex)      = min(1, factorSitio(hex))   // sol/claros = salir a pleno; nubes/viento recortan; lluvia = 0
```

Temp: óptimo 20–25 °C, casi nulo <10 °C o >40 °C. Se multiplica igual que el cielo.

`salida` evita sol×sol (1,25×1,25). El bonus de sol/claros solo cuenta **donde se pecorea**. Nubes, viento y lluvia recortan tanto al salir de casa como al llegar.

### Recolección (casa primero, fuera solo el sobrante)

Las abejas no recorren el barrio por deporte. Flujo diario, por hex y flora:

**Ola 1 — en casa, a prorrata**

En tu piscina pecorean a la vez:

- el 100 % de **tus** recolectoras (ya con `factorSitio` de tu hex: sol, claros, nubes, viento, lluvia, temp);
- las recolectoras **vecinas** que en *su* finca ya no tenían néctar (su sobrante de demanda), con tope **5 % de las forrajeras de ese vecino por lado** y solo si la flora coincide y `factorSitio` tuyo > 0.

Se reparte `min(piscina, demandaTuya + Σ inbound)` **en proporción al número de recolectoras** (en la práctica, a su `potencialKg` de vuelo).

**Ola 2 — fuera, solo si casa está limpia**

- Si **queda néctar** en tu piscina → tus abejas **no salen**. Están saciadas o el campo sobraba; el resto de flores se pierde al anochecer.
- Si la piscina **llegó a 0** y a tus recolectoras **aún les cabe miel** → ese hueco va a vecinos con la misma flora **y néctar que les haya sobrado**, otra vez a prorrata con quien esté pecoreando allí. Tope **5 % de tus forrajeras por hex** (6 lados → 30 % del censo, no más).

Poca disponibilidad de esa flor (bloom bajo, añada floja, cultivo pequeño, muchas colmenas) encoge `piscinaKg` frente a la demanda → la ola 1 vacía casa antes → **más salidas**. No es un flag aparte: sale del ratio piscina/recolectoras. Matices: en invierno hay pocas obreras, un campo flojo puede bastar y no sales; si el vecino tiene la misma flora igual de escasa, desbordas y no encuentras nada; si no coincide la flora, no vas.

El 5 % ya no es un peaje fijo cada día: es el **máximo que puede desbordar** por frontera. Un apiario que no llena su campo no manda a nadie y no recibe visitas de otros que tampoco llenan.

Sin tick lockstep: usas `demandaKg`, `piscinaKg` y, del tick anterior del vecino, `sobranteDemandaKg` / `sobrantePiscinaKg`.

```
shareMax          = 0,05
demanda           = potencialKg × factorSitio(mío)
inbound_j         = min(shareMax × demanda_j, max(0, sobranteDemanda_j))
                  × (flora coincide) × (factorSitio(mío) > 0)

demandaOla1       = demanda + Σ inbound_j
kgDeMiPiscina     = min(piscina, demandaOla1) × demanda / demandaOla1
kgVecinosSeLlevan = min(piscina, demandaOla1) × (Σ inbound) / demandaOla1
huecoTuyo         = max(0, demanda - kgDeMiPiscina)

# Ola 2 solo si piscina agotada
si piscina - demandaOla1 > 1e-6:
    kgFuera = 0
si no:
    raid_j = min(shareMax × demanda, huecoTuyo_repartido_entre_vecinos_con_sobrante)
           × factorSitio(j) / max(factorSitio(mío), ε)   # pecoreo con el cielo DE ELLOS
    kgRobadoA_j = min(raid_j, sobrantePiscina_j × raid_j / demandaEnSuOla2)
    kgFuera = Σ kgRobadoA_j

kg colmena = kgDeMiPiscina + kgFuera
```

Ya no hace falta `neighborForageEfficiency ×0,50`: la merma sale de **compartir la piscina en la ola 1** cuando hay desborde vecinal. Campo vacío / pocas colmenas → nadie cruza, no hay duelo.

| Situación (piscina 20 kg, tiempo neutro) | Ola 1 | Ola 2 | Neto |
|---|---|---|---|
| 5 colmenas (21,5 kg), sin vecinos | llenan casa (20) | 1,5 kg de demanda suelta, sin destino | 20 |
| 5 colmenas, bloom al 30 % (piscina 6) | llenan casa | desbordas si el vecino tiene esa flora y resto | más salidas |
| 10 colmenas (43 kg), solo | llenas casa (20) | no hay a dónde ir | 20 |
| 10 colmenas + **1** vecino igual saturado | inbound 5 % → te dejan ~19,0 | desbordas a su campo ya vacío | ~19,0 (−5 %) |
| 10 colmenas + **6** vecinos saturados | inbound 30 % → ~15,4 en casa | sus campos también a 0 | ~15,4 (**−23 %**) |
| Tú a 4 (17,2), vecino a 10 | él desborda; cabe en tu sobrante | tú no sales | 17,2 (te pican el resto, no tu bote) |
| Tú a 5 (21,5), vecino a 10 | ya no cabe; inbound a prorrata | él también vacío | ~18,2 |
| Lluvia en un hex | `factorSitio = 0` | nadie pecorea ahí | 0 en ese campo |

Un vecino flojo no te toca. Un barrio de romero a tope sí: primero os partís *tu* néctar, y fuera ya no queda.

### Informe diario

En `DailySummaryDialog`, debajo de la miel por flora, dos filas (ocultas si kg = 0):

| | Texto | Icono |
|---|---|---|
| Lo que traes (ola 2) | «Tus abejas en otras fincas: +X,X kg» | `ic_abeja` (buena) |
| Lo que te quitan (ola 1) | «Abejas vecinas en tus fincas: −Y,Y kg» | abeja “con cara de haberla liado” (`ic_abeja_vecina`) |

La miel por flora es el **neto** (`casa + otras fincas`). La fila mala es la merma ya descontada de casa. Si Y > X, el barrio te ha salido a deber.

Campos en `DailyTickSummary`: `forageFromNeighborKg`, `forageTakenByNeighborsKg`.

### Competencia vecinal

- Vecinos = 6 hexes axiales. Flora distinta, vacío, tránsito o lluvia en destino → ese lado no existe.
- Casa primero. Fuera solo recolectoras que no han llenado **después** de compartir tu piscina.
- Tope de desborde 5 % del censo de forrajeras por frontera.
- Cielo y temp del **hex donde se pecorea**.
- Publicar: `demandaKgByFlora`, `piscinaKgByFlora`, `sobranteDemandaKg`, `sobrantePiscinaKg`, `factorSitio`.

### Ejemplos (por tipo, bloom 1,0, piscina 20 kg, tiempo neutro)

| Situación | Demanda | Recoges |
|---|---|---|
| 5 colmenas a 80 k, solo | 21,5 | 20 (rozan el cupo) |
| 5 a 80 k, día de sol (×1,25) | 26,9 | 20 (pico + sol = saturación) |
| 5 a 80 k, nublado (×0,85) | 18,3 | 18,3 (el tiempo alivia el cupo) |
| 10 a 80 k, solo | 43 | 20 (el extra no suma) |
| 5 a 12 k invierno | 3,2 | 3,2 (no hay “5 huecos”) |
| 5 a 80 k + vecinos también a 5 | ambos desbordan ~1,5 kg | melee suave a prorrata |
| 10 a 80 k + **1** vecino saturado | ola 1 a prorrata | ~19,0 (−5 %) |
| 10 a 80 k + **6** vecinos saturados | ola 1 a prorrata | ~15,4 (−23 %) |
| Sol en tu hex, **lluvia** en el vecino | no se pecorea allí | ni tú desbordas hacia el charco |
| **Viento** en casa, **sol** en el vecino | ola 1 con tu 0,70; ola 2 con su 1,25 | solo si casa se ha vaciado |

### Gráfico

El de floración sigue siendo **% de explosión** (0–100, a veces >100 si `height` > 1). En el hex, una línea o chip aparte: “Néctar hoy: 18 kg · demanda: 24 kg (6 kg vecinos)”. Mezclar saturación en la curva anual era el fallo actual.

### Flora secundaria (después)

`secondaryNectarShare` 0,28: si tu flora está por debajo de `secondaryNectarTrigger` (0,18) y hay otra lista en el hex (o en el vecino), un % del vuelo va allí → miel milflores. No hace falta para la v1 de la piscina.

### Qué se deja de usar

`nativeHiveSlots`, `plantedHiveSlots`, `crowdingFactor`, `crowdingFloor`. El cupo emerge de `piscina vs obreras × tiempo`.

### Qué no se toca en v1

Exclusividad del hex, `MAX_HIVES_PER_SITE`, calendario `nectarFlora`, añada, mercado, consumo interno de la colonia (`consumptionKg`: eso es miel de despensa, no néctar del campo).
