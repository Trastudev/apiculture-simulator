# Ofertas autoritativas en el servidor

Con `GAME_SERVER_URL` configurado, la API es la autoridad de:

- `honey_orders` (comandas de miel).
- `pollination_offers` (ofertas NPC de polinización).

El servidor carga sus propios overlays y `game_balance.json` desde
`server/data/`. Cada 30 segundos, y después de una acción de aceptar,
reclamar o terminar, mantiene el cupo de cada región y banda:

1. Expira y elimina ofertas abiertas vencidas.
2. Consolida el pool antiguo importado de Firestore.
3. Reparte destinos elegibles por clima/nivel y malla geográfica.
4. Inserta la fila nueva en la misma transacción.
5. Conserva las comandas reclamadas que todavía pueden tener un viaje en
   curso.

Un contrato ya aceptado (`pollination_contracts`) no se sustituye: es una
obligación del jugador. Lo que se repone es la oferta abierta que lo originó.

La importación administrativa de una partida anterior requiere
`ALLOW_OFFER_IMPORT=1`; después se debe volver a `0` y reiniciar la API.

La app:

- No escribe ofertas/comandas en Firestore cuando la API está activa.
- Publica el estado de los contratos aceptados para que el servidor reserve la
  finca ocupada.
- No publica precios desde el móvil: el backend usa su fuente de mercado y, si
  no hay snapshot diario, el fallback autoritativo de 4,5 €/kg (5,04 €/kg con
  la bonificación de comanda).
- Si `/health` o una acción no responde, muestra un diálogo modal y bloquea la
  interfaz hasta recuperar la conexión.
- Consume `/offer-snapshot`.
- Reclama mediante `/offer-actions`.
- Si la API no responde, conserva la caché local solo para lectura temporal,
  pero no permite acciones nuevas ni genera un pool paralelo.
- Sin `GAME_SERVER_URL`, conserva el modo local histórico como fallback.

Para regenerar la copia local ejecuta `npm run sync:catalog` desde `server/`.
Despliegue: copia `server/data`, `server/src/offerCatalog.js`,
`server/src/offerClock.js`, `server/src/auth.js`, `server/src/index.js`,
`server/src/tables.js`, `server/src/hives.js`,
`server/src/tripClock.js`, `server/package.json`, `server/sql/006_offer_clock_indexes.sql`,
`server/sql/007_offer_hex_index.sql`,
`server/sql/008_active_contract_offers.sql`,
`server/sql/009_pollination_offer_claims.sql`,
`server/sql/010_trip_order_indexes.sql`,
`server/sql/011_market_price_snapshots.sql`,
`server/sql/012_exact_offer_claim_index.sql` y el `Dockerfile`; después reconstruye y
reinicia la API. El primer tick elimina las filas abiertas antiguas que no
llevan el prefijo `srv-`; no elimina comandas reclamadas.
