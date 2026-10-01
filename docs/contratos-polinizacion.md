# Contratos de polinización (punto 4)

Diseño acordado para implementar más adelante. El mercado de venta al mayor **no se sustituye**: conviven dos pestañas.

Hoy el ranking mide antigüedad. Estos convenios son competencia **temporal y geográfica**: calendario de floración, distancia y cupo de ofertas.

---

## 1. Dos mercados

| Pestaña | Qué es | Qué no es |
|---|---|---|
| Mercado | Venta al mayor actual (`HoneyMarketEngine`, pills de flora, precio global) | No paga polinización |
| Contratos | Convenios con propietarios NPC de plantaciones | No es compra de terreno |

Misma pieza de UI que Iberia | Sudáfrica: un `ToggleGroup` de dos botones, una sola selección.

---

## 2. Fincas NPC en el mapa (amarillo)

Son **hexes del mundo**, no un cupo abstracto ni las fincas moradas/azules de los jugadores.

Hoy el mapa pinta: libre (verde), bloqueado (rojo), tuya (morado), de otro jugador (azul). Las de convenio llevan un **color nuevo: amarillo**. Se reparte un conjunto fijo (o semi-fijo) por el territorio, más densas en zonas agrícolas (naranjos al sur, girasoles en meseta, macadamia en lowveld…).

- No se compran. No cambian de dueño.
- Cada hex amarillo = un NPC + una plantación.
- Aceptar el convenio es llevar colmenas **a ese hex**.
- Un jugador, un convenio activo. Un hex amarillo, **un contratista** mientras dura la floración (el que aceptó). Los demás ven la finca ocupada y eligen otra amarilla.

“Plaza” aquí, si acaso, es el patio de cajas de la finca (`MAX_HIVES_PER_SITE` = 10), igual que en cualquier apiario: son **tus** colmenas en la finca amarilla, no asientos de matchmaking.

Más adelante: jugador-con-cultivo publicando demanda.

---

## 3. Qué pide el convenio

Llevar colmenas a su hex durante la **floración importante** de esa variedad. El NPC no paga por caja colocada: paga por **polinización**, medida con la miel recogida en esa finca y esa flora.

Tope de cajas de la finca: `MAX_HIVES_PER_SITE` = 10. La carta puede sugerir un número (“3–6 colmenas”) pero el mínimo contractual es el **%**, no el recuento.

---

## 4. Polinización = % de néctar recogido

Misma piscina diaria que el mele territorial, **solo** en el hex del NPC y **solo** esa plantación:

```
polinizacion = Σ mielRecogidaKg / Σ piscinaKg
               (días de la ventana importante con piscina > 0)
```

Acumulado, no media de porcentajes diarios: un día de lluvia no tumba el contrato si el pico se cubre.

- 100 % = se ha recolectado toda la piscina de esa flora en esa finca a lo largo de la ventana.
- El clima del **sitio** cuenta (lluvia = 0 pecoreo ese día).
- En esa finca amarilla pecorean **solo** las colmenas de quien tiene el convenio. Otro jugador no entra.

La miel entra en las colmenas del jugador **siempre**. El convenio no se la queda.

---

## 5. Pago

Cada oferta trae:

| Campo | Significado |
|---|---|
| Mínimo de polinización | Umbral (p. ej. 45 %). Por debajo: **no cobra el convenio**. |
| Pago por cumplimiento | B fijados si `polinizacion ≥ mínimo`. |
| Extra | Si se pasa del mínimo: `extraB = extraPorPunto × (polinizacion − mínimo)` en puntos porcentuales, tope al 100 %. |

Liquidación al **cerrar** el contrato (floración importante ha bajado):

1. Si no llega al mínimo → 0 B de convenio. Se queda la miel. Ya pagó el traslado de ida.
2. Si llega o lo supera → pago + extra. La miel sigue suya y puede venderla al mayor (pestaña Mercado).

No hay penalización extra ni pérdida de colmenas.

---

## 6. Calendario: floración importante

La faena **no** es un número fijo de días en la carta: dura mientras esa plantación esté en floración importante en ese hex.

Definición propuesta (calibrable en `game_balance.json`):

- Empieza el primer día con `bloom01 ≥ 0,40` (tras el desfase climático del hex).
- Termina el primer día, **después del pico**, con `bloom01 < 0,40`.
- Si el jugador acepta ya empezada la ventana, cuenta solo el resto (el % se calcula sobre las piscinas **desde la llegada**).

Al terminar:

1. Se liquida el convenio (§ 5).
2. Las colmenas **vuelven solas** al hex de origen (el que tenían al aceptar).
3. 1 día de viaje de vuelta, sin pecoreo (igual que `TranshumanceRules.TRAVEL_DAYS`).
4. La vuelta **no cobra** de nuevo: el coste informado es solo la ida.

---

## 7. Coste de trashumancia (obligatorio en la carta)

La trashumancia actual del mapa propio es por **km** (80–250 B). En convenios se informa por **hexágonos de distancia axial** (lo que el jugador ve en el mapa).

```
distHex     = distancia axial entre hex de origen (apiario elegido) y hex NPC
costeIda    = distHex × B_por_hex
```

Propuesta de calibración (no implementada): **20 B / hex**, mínimo 1 hex.

En la carta:

- Distancia en hex (desde el apiario más cercano del jugador, o “elige colmenas” al aceptar).
- Precio unitario.
- Total de ida.
- Texto claro: “1 día de viaje, sin pecoreo. Vuelta automática y gratuita al cerrar.”

Se cobra **al aceptar**, antes de mover. Saldo insuficiente → no se acepta.

Sudáfrica: el contrato ZA solo aparece si el clima está desbloqueado (`ClimateUnlock`).

---

## 8. Flujo al aceptar

1. Carta → **Aceptar**.
2. Diálogo: elegir 1…N colmenas propias (no en tránsito, no en otro convenio).
3. Recalcular coste con el hex de origen de esas cajas (si salen de varios hexes, suma de idas). v1 más simple: **todas salen del mismo hex**.
4. Cobrar ida. Guardar origen. `transhumanceArrivesDayKey` = mañana.
5. Flora de pecoreo de esas colmenas = la plantación del contrato mientras dure.
6. Al cerrar: liquidación + vuelta al origen + restaurar flora anterior.

Un jugador, **un convenio activo** en v1 (evita solapes de calendario).

---

## 9. Carta de oferta (UI)

Listado vertical de tarjetas. Cada una:

| Pieza | Contenido |
|---|---|
| Foto carnet | Cara cartoon del NPC (drawable por personaje) |
| Nombre | Propietario |
| Terreno | Zona / nombre de finca (no hace falta el id técnico) |
| Plantación | Tipo (`Campo de naranjos`, `Macadamia`…) + icono de flora |
| Ventana | “Floración importante · ~5 abr – 21 may” (orientativa; el cierre es por bloom) |
| Mínimo | “Polinización mín. 55 %” |
| Pago | “Cumplir: 680 B” |
| Extra | “+8 B por cada % por encima del mínimo” |
| Traslado | “11 hex × 20 B = 220 B · 1 día” |
| Acción | Botón Aceptar |

---

## 10. Qué no entra en v1

- Contratos entre jugadores.
- Mele vecinal sobre el hex NPC.
- Quedarse en la finca NPC después de la floración.
- Penalizar la miel si no se llega al mínimo.
- Trashumancia de convenio cobrada por km (se usa hex).

---

## 11. Encaje con el simulador actual

- Piscina: `HexNectarPool` / `baseDailyNectarKgPlanted` (20 kg a bloom 1,0).
- Ventana: `NectarFlow` + `FloraBloomWindow` + desfase de zona.
- Viaje: `TranshumanceRules.TRAVEL_DAYS` (1 día).
- Cultivos: `CropUnlock` / `HexFlora` plantaciones (naranjos, almendros, cerezos, girasoles, macadamia…).
- Mapa: hex NPC no sale en compra de terreno.
- Mercado mayor: pestaña intacta.

---

## 12. Cinco ofertas de ejemplo

Cifras **ilustrativas** (no son datos de partida). Distancia desde un apiario tipo en costa mediterránea (ej. 1–3 hexes alrededor de Barcelona) salvo el de Sudáfrica. 20 B/hex. Extra lineal hasta 100 %.

### A. Núria Soler — almendros (Lleida)

| | |
|---|---|
| Terreno | Secano de Ponent · mediterráneo interior |
| Plantación | Campo de almendros |
| Ventana importante | ~6 feb – 9 mar (pico ~21 feb, `center` 52) |
| Mínimo | 45 % |
| Cumplir | 420 B |
| Extra | +6 B / % por encima |
| Distancia | 7 hex |
| Traslado | 140 B |
| Si haces 45 % | 420 B + miel |
| Si haces 70 % | 420 + 25×6 = 570 B + miel |
| Si haces 30 % | 0 B de convenio; miel tuya; 140 B ya pagados |

Floración corta: pocas cajas fuertes bastan; llegar tarde come días de piscina.

### B. Vicente Ferrer — naranjos (Ribera del Xúquer)

| | |
|---|---|
| Terreno | Huerta de València · sur / mediterráneo |
| Plantación | Campo de naranjos |
| Ventana importante | ~5 abr – 21 may (pico ~28 abr, `center` 118, más ancha) |
| Mínimo | 55 % |
| Cumplir | 680 B |
| Extra | +8 B / % |
| Distancia | 11 hex |
| Traslado | 220 B |
| A 55 % | 680 B |
| A 100 % | 680 + 45×8 = 1 040 B |

Ventana larga: más días de piscina (más kg posibles y más riesgo de lluvia).

### C. Elena Martín — girasoles (La Mancha)

| | |
|---|---|
| Terreno | Meseta · continental |
| Plantación | Campo de girasoles |
| Ventana importante | ~1 jul – 6 ago (pico ~19 jul, `center` 200) |
| Mínimo | 40 % |
| Cumplir | 350 B |
| Extra | +5 B / % |
| Distancia | 4 hex |
| Traslado | 80 B |
| A 40 % | 350 B |
| A 80 % | 350 + 40×5 = 550 B |

Cerca y barato; el calor continental recorta pecoreo (`factorSitio`). Mínimo bajo a propósito.

### D. Thabo Mokoena — macadamia (Mpumalanga)

| | |
|---|---|
| Terreno | Lowveld · subtropical ZA |
| Plantación | Macadamia |
| Ventana importante | pico `center` 250 (~3–4 semanas de floración importante en calendario austral) |
| Mínimo | 50 % |
| Cumplir | 900 B |
| Extra | +10 B / % |
| Distancia | 18 hex (otro continente de juego) |
| Traslado | 360 B |
| A 50 % | 900 B |
| A 90 % | 900 + 40×10 = 1 300 B |

Solo si Sudáfrica está desbloqueada. Traslado caro: el convenio tiene que compensar.

### E. Amaia Lezeaga — cerezos (Prepirineo)

| | |
|---|---|
| Terreno | Prepirineo navarro · montaña |
| Plantación | Campo de cerezos |
| Ventana importante | ~12 mar – 8 abr (pico ~26 mar, `center` 85, estrecha) |
| Mínimo | 60 % |
| Cumplir | 510 B |
| Extra | +9 B / % |
| Distancia | 9 hex |
| Traslado | 180 B |
| A 60 % | 510 B |
| A 85 % | 510 + 25×9 = 735 B |
| A 50 % | 0 B de convenio |

Pico corto y mínimo alto: hay que llegar al inicio de la floración con colonia fuerte; la montaña no recorta puesta en verano, pero en marzo el frío sí recorta vuelo.

---

## 13. Resumen de reglas (checklist)

1. Mercado | Contratos, como Iberia | Sudáfrica.
2. Hexes **amarillos** NPC repartidos por el mapa (no se compran).
3. % = néctar recogido / piscina de esa flora en esa finca (acumulado).
4. Por debajo del mínimo: no cobra; miel sí.
5. En o por encima: pago + extra; miel vendible al mayor.
6. Duración = floración importante (`bloom01` umbral 0,40); al bajar, fin automático.
7. Vuelta sola al origen; 1 día de viaje; ida de pago, vuelta gratis.
8. Carta: finca, cultivo, NPC con foto carnet, mínimo, pago, extra, hex × B, Aceptar.
9. Un convenio activo por jugador; de salida, un contratista visible por hex amarillo (§ 15 si hay que abrir una segunda capa).
10. ZA solo con clima desbloqueado.
11. Habrá muchas fincas amarillas en el territorio (§ 14); no un tablón de 5 cartas.

---

## 14. Poca gente al inicio, demasiada después

**“Plazas compartidas” no es un sistema aparte.** Era una forma confusa de decir “varios jugadores a la vez en la misma finca NPC”. Eso **no** va: la competencia son los hexes **amarillos** del mapa.

| Jugadores | Qué pasa si solo hay 5 convenios en todo el servidor |
|---|---|
| Pocos | Casi nadie se cruza. Da igual. |
| Muchos | Cinco personas trabajan; el resto no. |

Por eso las fincas amarillas **se reparte por el territorio** (decenas/cientos, no cinco cartas). La pestaña Contratos lista las amarillas **en rango** (p. ej. las N más cercanas a tus apiarios, o las de tu zona climática).

### Con pocos

Sobran hexes amarillos vacíos. Eliges el que te encaje (cultivo, distancia, mínimo). Compites contra la floración y el viaje, no contra un lobby vacío.

### Con muchos

Las amarillas **de tu zona** se ocupan. Tienes que:

- ir más lejos (más B de traslado), o
- esperar a que cierre una floración y vuelva a quedar libre, o
- coger un cultivo menos goloso que aún esté vacío.

Sigue habiendo, de cara al jugador, un dueño de la faena por finca: no ve a otro apicultor en el patio. La escasez es **geográfica**. Si una zona se satura, entra el escalado (§ 15).

### Proporción (calibrable)

No un tablón de 5 cartas ni un mar amarillo.

Hay **~10 200 hexes** en Iberia (70 km²) y **~14 300** en Sudáfrica (140 km²). Pintar 1 de cada 10 sería un tapiz; 1 de cada 200 dejaría el mapa vacío a zoom normal.

**Objetivo: ~2,5 % de los hexes = finca NPC amarilla**, sesgada a cinturones agrícolas (casi 0 en alta montaña / marisma; 4–6 % en huerta y meseta).

| Mapa | Amarillos (orden de magnitud) | 1 de cada |
|---|---|---|
| Iberia | **~250** (rango 220–300) | ~40 hexes |
| Sudáfrica | **~280** (rango 240–320) | ~50 hexes |

A distancia de traslado razonable (radio 8 hex ≈ 160 B): un jugador ve **unas 5 fincas amarillas** a su alrededor. Suficiente para elegir cultivo; con el mapa lleno las buenas de la costa se ocupan y hay que irse al interior.

Los ~250 no son 250 naranjos: se reparte por plantación típica de la zona (almendro/naranjo al este y sur, girasol/colza en meseta, cerezo en prepirineo, macadamia/litchi/aguacate en ZA subtropical).

### Qué no hacer

- Instanciar una “Núria virtual” distinta por jugador (rompería el mapa compartido: el amarillo tiene que ser el mismo hex para todos).
- Meter a dos apicultores en el mismo hex amarillo.
- Un tablón global de cinco ofertas.
- Un jugador con cinco convenios a la vez.
- Partir la piscina de néctar entre dos jugadores **sin decírselo** (parece un bug de clima).

### Sensación buscada

- **Solo:** hay amarillos cerca; ¿llego a los almendros a tiempo?
- **Barrio lleno:** los naranjos amarillos de la costa ya están cogidos; o viajas 11 hex, o te quedas los girasoles de interior.

---

## 15. Escalar si crece el número de jugadores

Sí se puede escalar. El mapa amarillo de ~250 / ~280 es el **suelo**. Cuando una zona climática (p. ej. mediterráneo en naranja) pasa de un umbral de ocupación, no hace falta pintar más amarillo ni poner dos apiarios a la vista en el mismo campo.

### Orden de palancas

1. **Reservas ya colocadas.** Parte de los hexes amarillos empiezan “en barbecho” (no salen en Contratos ni se pueden aceptar). Al superar p. ej. 70 % de ocupación en esa zona y cultivo, se abren. El mapa gana faena sin cambiar de reglas. Tope visual: no pasar de ~4–5 % de hexes amarillos en huerta.
2. **Segunda capa en el mismo hex (la que comentas).** Si las reservas ya están abiertas y sigue saturado: el mismo terreno NPC admite **un segundo convenio**. Cada uno tiene **su propia piscina, su propio % y su propio pago**. No se ven las colmenas del otro (ni en el mapa, ni en el patio, ni en el informe). Para ti eres el apicultor de Vicente; el NPC “ha contratado a otro” en una capa que no existe en tu cliente.
3. **Tope de capas.** 2 de salida; 3 solo si la zona sigue al 90 %+ (calibrable). Nunca un melee oculto.

### Qué es y qué no es

| | |
|---|---|
| Sí | Dos simulaciones independientes en el mismo pin amarillo. Mismo cultivo, mismas fechas, mismo coste de hex. |
| No | Os partís los 20 kg de néctar. Eso se notaría (“no llego al 55 % y no sé por qué”) y no es realista: no son dos explotaciones mezcladas. |
| No | Texto de “plaza 2/2” ni nombre del otro jugador. |

El jugador no “sabe que comparte” porque **no comparte el campo**: comparte el *sitio en el mapa*. Como dos turnos de polinización en la misma finca que no se cruzan.

### Cuándo se enciende

Por zona + cultivo + ventana de floración, no por “jugadores online en el servidor”.

```
ocupación = convenios activos de ese cultivo en esa zona / hexes amarillos abiertos de ese cultivo
si ocupación ≥ 0,70 → abrir reservas
si ocupación ≥ 0,90 y no quedan reservas → permitir capa 2 en esos hexes
```

Con 30 jugadores en Iberia casi no se nota. Con 300 en naranja de costa, primero salen más amarillos; luego, si hace falta, la capa 2.

### Implementación (cuando toque)

`contractLayer` 0 o 1 en el convenio. Forage y snapshot de piscina clavean `hexId + layer + flora`. El overlay del mapa sigue siendo un solo polígono amarillo. El patio NPC filtra `ownerId == yo`.

