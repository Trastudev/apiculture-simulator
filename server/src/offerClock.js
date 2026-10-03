"use strict";

const catalog = require("./offerCatalog");

const REGIONS = ["iberia", "za", "mdg"];
const HEXES_PER_BATCH = 50;
const ORDERS_PER_BATCH = 2;
const OFFERS_PER_BATCH = 2;
const ORDER_LIFE_MS = 8 * 60 * 60 * 1000;
const ORDER_NEAR_KM = 40;
const ORDER_NEAR_FALLBACK_KM = 80;
const POLLINATION_HEXES_PER_BATCH = 70;
const PRICE_BONUS = 1.5;
let clockReady = false;

function num(value) {
  const n = Number(value);
  return Number.isFinite(n) ? n : 0;
}

function int(value) {
  return Math.trunc(num(value));
}

function floorMod(value, modulus) {
  return ((value % modulus) + modulus) % modulus;
}

function hash64(value) {
  const s = String(value);
  let h = -3750763034362895779n;
  const prime = 1099511628211n;
  const mask = 0xffffffffffffffffn;
  for (let i = 0; i < s.length; i++) {
    h ^= BigInt(s.charCodeAt(i));
    h = (h * prime) & mask;
  }
  return h;
}

function hash32(value) {
  let h = 2166136261;
  const s = String(value);
  for (let i = 0; i < s.length; i++) {
    h ^= s.charCodeAt(i);
    h = Math.imul(h, 16777619);
  }
  return h >>> 0;
}

function utcDayKey(nowMs = Date.now()) {
  const d = new Date(nowMs);
  return d.getUTCFullYear() * 10000 + (d.getUTCMonth() + 1) * 100 + d.getUTCDate();
}

function utcDayEnd(dayKey) {
  const year = Math.floor(dayKey / 10000);
  const month = Math.floor(dayKey / 100) % 100;
  const day = dayKey % 100;
  return Date.UTC(year, month - 1, day + 1, 0, 0, 0, 0);
}

// La comanda paga la media entre el mínimo y el máximo de esa flora
// (el precio con oferta = demanda) multiplicada por 1,5.
function basePrice(flora) {
  const mean = catalog.meanPriceEur(flora);
  return (mean > 0 ? mean : 12) * catalog.orderScarcity(flora);
}

function orderUnitPrice(flora) {
  return Math.round(basePrice(flora) * PRICE_BONUS * 100) / 100;
}

function cropForParcel(parcel, band, nowMs, dayKey) {
  return catalog.offerForParcel(parcel, band, nowMs, dayKey);
}

function floraForParcel(parcel, band, seed, nowMs) {
  const pool = catalog.orderFloraCandidates(parcel, band);
  return catalog.pickSeasonalFlora(parcel, pool, nowMs, `${parcel.id}:flora:${seed}`);
}

function orderCount(eligible, region) {
  if (eligible < HEXES_PER_BATCH) return 0;
  const perBatch = region === "za" ? ORDERS_PER_BATCH * 3 : ORDERS_PER_BATCH * 2;
  return Math.floor((eligible * perBatch) / HEXES_PER_BATCH);
}

function offerCount(parcelCount, band) {
  if (parcelCount <= 0) return 0;
  const density = Math.max(OFFERS_PER_BATCH,
      Math.floor((parcelCount * OFFERS_PER_BATCH) / POLLINATION_HEXES_PER_BATCH));
  return Math.min(48, Math.max(catalog.BAND_POLLINATION_COUNT[band] || 0, density));
}

function pickScattered(parcels, want, used, seed) {
  const available = (parcels || []).filter((p) => p && p.id && !used.has(p.id));
  if (!available.length || want <= 0) return [];
  available.sort((a, b) => {
    const lat = a.lat - b.lat;
    if (Math.abs(lat) > 1e-7) return lat;
    const lng = a.lng - b.lng;
    if (Math.abs(lng) > 1e-7) return lng;
    return String(a.id).localeCompare(String(b.id));
  });
  const n = available.length;
  const take = Math.min(want, n);
  const shift = floorMod(hash32(String(seed)), n);
  const out = [];
  for (let i = 0; i < take; i++) {
    const idx = Math.floor((i + 0.5) * n / take);
    const parcel = available[(idx + shift) % n];
    used.add(parcel.id);
    out.push(parcel);
  }
  return out;
}

function nearestReplacement(parcels, dead, used, band, seed) {
  if (!dead) return null;
  const picks = pickScattered(parcels, 1, used, seed);
  return picks[0] || null;
}

function parcelKm(dead, parcel) {
  const lat = num(dead.dest_lat != null ? dead.dest_lat : dead.destLat);
  const lng = num(dead.dest_lng != null ? dead.dest_lng : dead.destLng);
  const pLat = num(parcel.lat != null ? parcel.lat : parcel.centroidLat);
  const pLng = num(parcel.lng != null ? parcel.lng : parcel.centroidLon);
  const p1 = (lat * Math.PI) / 180;
  const p2 = (pLat * Math.PI) / 180;
  const dphi = ((pLat - lat) * Math.PI) / 180;
  const dl = ((pLng - lng) * Math.PI) / 180;
  const a = Math.sin(dphi / 2) ** 2 + Math.cos(p1) * Math.cos(p2) * Math.sin(dl / 2) ** 2;
  return 2 * 6371 * Math.asin(Math.min(1, Math.sqrt(a)));
}

function nearbyReplacement(parcels, dead, used, seed) {
  if (!dead || !parcels || !parcels.length) return null;
  const deadHex = dead.dest_hex_id || dead.destHexId || "";
  const pickIn = (maxKm) => {
    const hits = [];
    for (const parcel of parcels) {
      if (!parcel || !parcel.id || used.has(parcel.id) || parcel.id === deadHex) continue;
      const km = parcelKm(dead, parcel);
      if (km <= maxKm + 1e-6) hits.push(parcel);
    }
    if (!hits.length) return null;
    return hits[floorMod(hash32(String(seed)), hits.length)];
  };
  return pickIn(ORDER_NEAR_KM)
      || pickIn(ORDER_NEAR_FALLBACK_KM)
      || pickScattered(parcels, 1, used, seed)[0]
      || null;
}

function orderRow(region, band, parcel, dayKey, nowMs, seed, old, prices) {
  const flora = floraForParcel(parcel, band, seed, nowMs);
  const id = `srv-ho-${region}-${dayKey}-${band}-${hash32(`${parcel.id}:${seed}`).toString(16)}`;
  const pin = catalog.pointInParcel(parcel, `${id}:pin`);
  return {
    id,
    npc_name: catalog.npc(parcel),
    portrait_index: parcel.npcIndex,
    flora_key: flora,
    kg: kgForBand(band, seed),
    unit_price: orderUnitPrice(flora),
    dest_hex_id: parcel.id,
    dest_lat: pin.lat,
    dest_lng: pin.lng,
    dest_label: parcel.place || parcel.id,
    region,
    created_day_key: dayKey,
    expire_epoch_ms: nowMs + ORDER_LIFE_MS,
    taken: false,
    claimed_by: null,
    band,
  };
}

function kgForBand(band, seed) {
  const min = catalog.BAND_KG_MIN[band] || 0.5;
  const max = catalog.BAND_KG_MAX[band] || 1.5;
  const t = (floorMod(hash32(`kg:${seed}`), 1000) + 1) / 1000;
  return Math.round((min + (max - min) * t) * 100) / 100;
}

function offerRow(region, band, parcel, dayKey, nowMs, seed) {
  const crop = cropForParcel(parcel, band, nowMs, dayKey);
  if (!crop) return null;
  const flora = crop.flora;
  const id = `srv-po-${region}-${dayKey}-${band}-${hash32(`${parcel.id}:${seed}`).toString(16)}`;
  return {
    id,
    hex_id: parcel.id,
    flora,
    start_doy: crop.startDoy,
    end_doy: crop.endDoy,
    band,
    region,
    created_day_key: dayKey,
    expire_epoch_ms: utcDayEnd(dayKey),
    dest_lat: parcel.lat,
    dest_lng: parcel.lng,
    npc_name: catalog.npc(parcel),
    portrait_index: parcel.npcIndex,
    taken: false,
  };
}

async function insertOrders(client, rows) {
  for (let start = 0; start < rows.length; start += 100) {
    const chunk = rows.slice(start, start + 100);
    const values = [];
    const params = [];
    chunk.forEach((row, index) => {
      const base = index * 16;
      values.push(`($${base + 1},$${base + 2},$${base + 3},$${base + 4},$${base + 5},$${base + 6},$${base + 7},$${base + 8},$${base + 9},$${base + 10},$${base + 11},$${base + 12},$${base + 13},$${base + 14},$${base + 15},$${base + 16})`);
      params.push(row.id,row.npc_name,row.portrait_index,row.flora_key,row.kg,row.unit_price,
        row.dest_hex_id,row.dest_lat,row.dest_lng,row.dest_label,row.region,
        row.created_day_key,row.expire_epoch_ms,row.taken,row.claimed_by,row.band);
    });
    await client.query(
      `INSERT INTO honey_orders
        (id,npc_name,portrait_index,flora_key,kg,unit_price,dest_hex_id,dest_lat,dest_lng,
         dest_label,region,created_day_key,expire_epoch_ms,taken,claimed_by,band)
       VALUES ${values.join(",")}
       ON CONFLICT (id) DO NOTHING`,
      params
    );
  }
}

async function insertOffers(client, rows) {
  for (let start = 0; start < rows.length; start += 100) {
    const chunk = rows.slice(start, start + 100);
    const values = [];
    const params = [];
    chunk.forEach((row, index) => {
      const base = index * 15;
      values.push(`($${base + 1},$${base + 2},$${base + 3},$${base + 4},$${base + 5},$${base + 6},$${base + 7},$${base + 8},$${base + 9},$${base + 10},$${base + 11},$${base + 12},$${base + 13},$${base + 14},$${base + 15})`);
      params.push(row.id,row.hex_id,row.flora,row.start_doy,row.end_doy,row.band,row.region,
        row.created_day_key,row.expire_epoch_ms,row.dest_lat,row.dest_lng,row.npc_name,
        row.portrait_index,row.taken,row.claimed_by || null);
    });
    await client.query(
      `INSERT INTO pollination_offers
        (id,hex_id,flora,start_doy,end_doy,band,region,created_day_key,expire_epoch_ms,
         dest_lat,dest_lng,npc_name,portrait_index,taken,claimed_by)
       VALUES ${values.join(",")}
       ON CONFLICT (id) DO NOTHING`,
      params
    );
  }
}

async function repriceOpenOrders(client, region) {
  const rows = await client.query(
    `SELECT id, flora_key, unit_price FROM honey_orders
      WHERE region=$1 AND taken=false`,
    [region]
  );
  for (const row of rows.rows) {
    const want = orderUnitPrice(row.flora_key);
    if (!(want > 0) || Math.abs(num(row.unit_price) - want) < 0.009) continue;
    await client.query(
      `UPDATE honey_orders SET unit_price=$2, updated_at=now() WHERE id=$1 AND taken=false`,
      [row.id, want]
    );
  }
}

async function scatterOpenOrders(client, region, parcels) {
  const byId = new Map(parcels.map((parcel) => [parcel.id, parcel]));
  const rows = await client.query(
    `SELECT id, dest_hex_id, dest_lat, dest_lng
       FROM honey_orders
      WHERE region=$1 AND taken=false`,
    [region]
  );
  for (const row of rows.rows) {
    const parcel = byId.get(row.dest_hex_id);
    if (!parcel) continue;
    const lat = num(row.dest_lat);
    const lng = num(row.dest_lng);
    if (Math.abs(lat - parcel.lat) > 1e-6 || Math.abs(lng - parcel.lng) > 1e-6) continue;
    const pin = catalog.pointInParcel(parcel, `${row.id}:pin`);
    if (Math.abs(pin.lat - lat) < 1e-7 && Math.abs(pin.lng - lng) < 1e-7) continue;
    await client.query(
      `UPDATE honey_orders
          SET dest_lat=$2, dest_lng=$3, updated_at=now()
        WHERE id=$1 AND taken=false`,
      [row.id, pin.lat, pin.lng]
    );
  }
}

async function maintainRegion(client, region, nowMs, dayKey, prices) {
  const all = catalog.getParcels(region);
  await scatterOpenOrders(client, region, all);
  await repriceOpenOrders(client, region);
  const pendingOrders = [];
  const pendingOffers = [];
  // La primera pasada tras desplegar el reloj descarta el pool legado de
  // Firestore, pero conserva las comandas reclamadas para que un viaje en curso
  // pueda liquidarse. Las nuevas filas siempre llevan el prefijo srv-.
  await client.query(
    `DELETE FROM honey_orders
      WHERE region=$1 AND taken=false AND id NOT LIKE 'srv-ho-%'`,
    [region]
  );
  await client.query(
    `DELETE FROM pollination_offers
      WHERE region=$1 AND taken=false AND id NOT LIKE 'srv-po-%'`,
    [region]
  );
  await client.query(
    `UPDATE honey_orders
        SET expire_epoch_ms=$2, updated_at=now()
      WHERE region=$1 AND taken=false AND expire_epoch_ms > $3`,
    [region, nowMs + ORDER_LIFE_MS, nowMs + ORDER_LIFE_MS]
  );
  const staleBefore = nowMs - 7 * 24 * 60 * 60 * 1000;
  await client.query(
    `DELETE FROM honey_orders h
      WHERE h.region=$1 AND h.taken=true AND h.expire_epoch_ms < $2
        AND NOT EXISTS (SELECT 1 FROM cargo_trips c WHERE c.order_id=h.id)`,
    [region, staleBefore]
  );
  await client.query(
    `DELETE FROM pollination_offers
      WHERE region=$1 AND taken=true AND expire_epoch_ms < $2`,
    [region, staleBefore]
  );
  const playableIds = new Set(all.map((p) => p.id));
  const outsideOrders = await client.query(
    `SELECT id, dest_hex_id, taken FROM honey_orders WHERE region=$1`,
    [region]
  );
  for (const row of outsideOrders.rows) {
    if (playableIds.has(row.dest_hex_id)) continue;
    if (row.taken) {
      const trip = await client.query(
        "SELECT 1 FROM cargo_trips WHERE order_id=$1 LIMIT 1",
        [row.id]
      );
      if (trip.rowCount > 0) continue;
    }
    await client.query("DELETE FROM honey_orders WHERE id=$1", [row.id]);
  }
  const outsideOffers = await client.query(
    `SELECT id, hex_id, taken FROM pollination_offers WHERE region=$1`,
    [region]
  );
  for (const row of outsideOffers.rows) {
    if (playableIds.has(row.hex_id)) continue;
    if (row.taken) {
      const contract = await client.query(
        `SELECT 1 FROM pollination_contracts
          WHERE hex_id=$1 AND region=$2 AND status IN ('ACTIVE','RETURNING') LIMIT 1`,
        [row.hex_id, region]
      );
      if (contract.rowCount > 0) continue;
    }
    await client.query("DELETE FROM pollination_offers WHERE id=$1", [row.id]);
  }
  const orders = await client.query(
    `SELECT * FROM honey_orders WHERE region=$1 AND taken=false ORDER BY created_day_key,id`,
    [region]
  );
  const offers = await client.query(
    `SELECT * FROM pollination_offers WHERE region=$1 AND taken=false ORDER BY created_day_key,id`,
    [region]
  );
  // Las filas tomadas reservean su destino mientras el viaje/contrato pueda
  // seguir vivo; así una reposición no reutiliza inmediatamente la misma
  // comanda o finca.
  const reservedOrders = await client.query(
    `SELECT dest_hex_id FROM honey_orders WHERE region=$1 AND taken=true`,
    [region]
  );
  const reservedOffers = await client.query(
    `SELECT hex_id FROM pollination_offers WHERE region=$1 AND taken=true`,
    [region]
  );
  const activeContracts = await client.query(
    `SELECT hex_id FROM pollination_contracts
      WHERE region=$1 AND status IN ('ACTIVE','RETURNING')`,
    [region]
  );
  const usedOrders = new Set(orders.rows.map((r) => r.dest_hex_id));
  for (const row of reservedOrders.rows) usedOrders.add(row.dest_hex_id);
  const usedOffers = new Set(offers.rows.map((r) => r.hex_id));
  for (const row of reservedOffers.rows) usedOffers.add(row.hex_id);
  for (const row of activeContracts.rows) usedOffers.add(row.hex_id);

  for (const dead of orders.rows.filter((r) => num(r.expire_epoch_ms) <= nowMs)) {
    const band = Math.max(0, Math.min(9, int(dead.band)));
    const eligible = all.filter((p) => catalog.orderEligible(p, band));
    const replacement = nearbyReplacement(eligible, dead, usedOrders,
        `${nowMs}:${dead.id}`);
    await client.query("DELETE FROM honey_orders WHERE id=$1", [dead.id]);
    if (replacement) {
      usedOrders.add(replacement.id);
      pendingOrders.push(orderRow(region, band, replacement, dayKey, nowMs,
          `${nowMs}:${dead.id}`, null, prices));
    }
  }
  for (const dead of offers.rows.filter((r) => num(r.expire_epoch_ms) <= nowMs)) {
    const band = Math.max(0, Math.min(9, int(dead.band)));
    const eligible = all.filter((p) => catalog.offerEligible(p, band)
      && cropForParcel(p, band, nowMs, dayKey) != null);
    const replacement = nearestReplacement(eligible, dead, usedOffers, band,
        `${dayKey}:${dead.id}`);
    await client.query("DELETE FROM pollination_offers WHERE id=$1", [dead.id]);
    if (replacement) {
      const row = offerRow(region, band, replacement, dayKey, nowMs, `${dayKey}:${dead.id}`);
      if (row) pendingOffers.push(row);
    }
  }
  await insertOrders(client, pendingOrders);
  await insertOffers(client, pendingOffers);

  // Las sustituciones anteriores también cuentan para el cupo del día. Volvemos
  // a leer dentro de la misma transacción para no generar una segunda tanda.
  const currentOrders = await client.query(
    `SELECT * FROM honey_orders WHERE region=$1 AND taken=false ORDER BY created_day_key,id`,
    [region]
  );
  const currentOffers = await client.query(
    `SELECT * FROM pollination_offers WHERE region=$1 AND taken=false ORDER BY created_day_key,id`,
    [region]
  );

  const globalUsedOrders = new Set(currentOrders.rows.map((r) => r.dest_hex_id));
  for (const row of reservedOrders.rows) globalUsedOrders.add(row.dest_hex_id);
  const globalUsedOffers = new Set(currentOffers.rows.map((r) => r.hex_id));
  for (const row of reservedOffers.rows) globalUsedOffers.add(row.hex_id);
  for (const row of activeContracts.rows) globalUsedOffers.add(row.hex_id);

  for (let band = 0; band < catalog.BAND_MAX_LEVEL.length; band++) {
    const orderParcels = all.filter((p) => catalog.orderEligible(p, band));
    const wantOrders = orderCount(orderParcels.length, region);
    const openOrders = currentOrders.rows.filter((r) => int(r.band) === band
        && num(r.expire_epoch_ms) > nowMs);
    if (openOrders.length > wantOrders) {
      for (const extra of openOrders.slice(wantOrders)) {
        await client.query("DELETE FROM honey_orders WHERE id=$1", [extra.id]);
        globalUsedOrders.delete(extra.dest_hex_id);
      }
    } else if (openOrders.length < wantOrders) {
      const picks = pickScattered(orderParcels, wantOrders - openOrders.length, globalUsedOrders,
          `${dayKey}:orders:${region}:${band}`);
      for (const parcel of picks) {
        globalUsedOrders.add(parcel.id);
        pendingOrders.push(orderRow(region, band, parcel, dayKey, nowMs,
            `${dayKey}:orders:${region}:${band}:${parcel.id}`, null, prices));
      }
    }

    const offerParcels = all.filter((p) => catalog.offerEligible(p, band)
      && cropForParcel(p, band, nowMs, dayKey) != null);
    const wantOffers = offerCount(offerParcels.length, band);
    const openOffers = currentOffers.rows.filter((r) => int(r.band) === band
        && num(r.expire_epoch_ms) > nowMs);
    if (openOffers.length > wantOffers) {
      for (const extra of openOffers.slice(wantOffers)) {
        await client.query("DELETE FROM pollination_offers WHERE id=$1", [extra.id]);
        globalUsedOffers.delete(extra.hex_id);
      }
    } else if (openOffers.length < wantOffers) {
      const picks = pickScattered(offerParcels, wantOffers - openOffers.length, globalUsedOffers,
          `${dayKey}:offers:${region}:${band}`);
      for (const parcel of picks) {
        globalUsedOffers.add(parcel.id);
        const row = offerRow(region, band, parcel, dayKey, nowMs,
            `${dayKey}:offers:${region}:${band}:${parcel.id}`);
        if (row) pendingOffers.push(row);
      }
    }
  }
  await insertOrders(client, pendingOrders);
  await insertOffers(client, pendingOffers);
}

async function spawnNearbyOrder(client, dead, nowMs, dayKey, prices) {
  if (!dead || !dead.region) return;
  const band = Math.max(0, Math.min(9, int(dead.band)));
  const region = dead.region;
  const all = catalog.getParcels(region);
  const eligible = all.filter((p) => catalog.orderEligible(p, band));
  const used = new Set();
  const open = await client.query(
    `SELECT dest_hex_id FROM honey_orders WHERE region=$1`,
    [region]
  );
  for (const row of open.rows) used.add(row.dest_hex_id);
  if (dead.dest_hex_id) used.add(dead.dest_hex_id);
  const replacement = nearbyReplacement(eligible, dead, used, `${nowMs}:done:${dead.id}`);
  if (!replacement) return;
  await insertOrders(client, [orderRow(region, band, replacement, dayKey, nowMs,
      `${nowMs}:done:${dead.id}`, null, prices)]);
}

async function runWithLock(pool, work) {
  const client = await pool.connect();
  try {
    await client.query("BEGIN");
    await client.query("SELECT pg_advisory_xact_lock(734621)");
    const result = await work(client);
    await client.query("COMMIT");
    return result;
  } catch (err) {
    try { await client.query("ROLLBACK"); } catch (_) { /* connection closed */ }
    throw err;
  } finally {
    client.release();
  }
}

async function loadMarketPrices(client, dayKey) {
  const result = await client.query(
    `SELECT flora_key, price_eur FROM server_market_prices WHERE day_key=$1`,
    [dayKey]
  );
  const prices = new Map();
  for (const row of result.rows) {
    const key = catalog.canonicalFlora(row.flora_key);
    const value = num(row.price_eur);
    if (key && value > 0) prices.set(key, value);
  }
  return prices;
}

async function tick(pool) {
  const nowMs = Date.now();
  const dayKey = utcDayKey(nowMs);
  const result = await runWithLock(pool, async (client) => {
    const prices = await loadMarketPrices(client, dayKey);
    for (const region of REGIONS) await maintainRegion(client, region, nowMs, dayKey, prices);
    return { ok: true, dayKey, catalog: catalog.stats() };
  });
  clockReady = true;
  return result;
}

async function action(pool, body, authUid) {
  const nowMs = Date.now();
  const dayKey = utcDayKey(nowMs);
  return runWithLock(pool, async (client) => {
    const type = String(body && body.type || "");
    if (authUid) {
      const requestedOwner = String(body && body.ownerId || "");
      if (requestedOwner && requestedOwner !== authUid) {
        throw Object.assign(new Error("owner mismatch"), { status: 403 });
      }
      body = Object.assign({}, body || {}, { ownerId: authUid });
    }
    const id = String(body && body.id || "");
    if (type === "claim-pollination-offer") {
      const hexId = String(body && body.hexId || "");
      const band = int(body && body.band);
      const flora = String(body && body.flora || "");
      const startDoy = int(body && body.startDoy);
      const owner = String(body && body.ownerId || "");
      if (!hexId || flora.length === 0 || owner.length === 0
          || band < 0 || band >= catalog.BAND_MIN_LEVEL.length
          || startDoy < 1 || startDoy > 365) {
        throw Object.assign(new Error("offer claim data required"), { status: 400 });
      }
      const found = await client.query(
        `SELECT id,taken,claimed_by FROM pollination_offers
          WHERE hex_id=$1 AND band=$2 AND flora=$3 AND start_doy=$4
            AND expire_epoch_ms>$5
            AND (taken=false OR claimed_by=$6)
          ORDER BY taken,created_day_key,id LIMIT 1 FOR UPDATE`,
        [hexId, band, flora, startDoy, nowMs, owner]
      );
      if (found.rowCount === 0) return { ok: false, reason: "unavailable" };
      if (found.rows[0].taken && found.rows[0].claimed_by === owner) {
        return { ok: true, alreadyOwned: true, offerId: found.rows[0].id };
      }
      await client.query(
        `UPDATE pollination_offers SET taken=true, claimed_by=$2, updated_at=now()
          WHERE id=$1`,
        [found.rows[0].id, owner]
      );
      return { ok: true, offerId: found.rows[0].id };
    }
    if (type === "release-pollination-offer") {
      const hexId = String(body && body.hexId || "");
      const band = int(body && body.band);
      const flora = String(body && body.flora || "");
      const startDoy = int(body && body.startDoy);
      const owner = String(body && body.ownerId || "");
      if (!owner || !hexId || flora.length === 0
          || band < 0 || band >= catalog.BAND_MIN_LEVEL.length
          || startDoy < 1 || startDoy > 365) {
        return { ok: false, reason: "invalid data" };
      }
      const result = await client.query(
        `UPDATE pollination_offers
            SET taken=false, claimed_by=NULL, updated_at=now()
          WHERE hex_id=$1 AND band=$2 AND flora=$3 AND start_doy=$4
            AND taken=true AND claimed_by=$5`,
        [hexId, band, flora, startDoy, owner]
      );
      return { ok: result.rowCount > 0 };
    }
    if (type === "take-offer-by-hex") {
      const hexId = String(body && body.hexId || "");
      const band = body && body.band != null ? int(body.band) : -1;
      const owner = String(body && body.ownerId || "");
      if (!hexId || !owner) {
        throw Object.assign(new Error("hexId and ownerId required"), { status: 400 });
      }
      const found = await client.query(
        `SELECT id FROM pollination_offers
          WHERE hex_id=$1 AND taken=false AND expire_epoch_ms>$2
            AND ($3::int < 0 OR band=$3)
          ORDER BY created_day_key,id LIMIT 1 FOR UPDATE`,
        [hexId, nowMs, band]
      );
      if (found.rowCount === 0) return { ok: false, reason: "unavailable" };
      await client.query(
        `UPDATE pollination_offers SET taken=true, claimed_by=$2, updated_at=now() WHERE id=$1`,
        [found.rows[0].id, owner]
      );
      return { ok: true, offerId: found.rows[0].id };
    }
    if (!id) throw Object.assign(new Error("id required"), { status: 400 });
    if (type === "claim-order") {
      const owner = String(body.ownerId || "");
      if (!owner) return { ok: false, reason: "owner required" };
      const found = await client.query(
        "SELECT * FROM honey_orders WHERE id = $1 FOR UPDATE",
        [id]
      );
      if (found.rowCount === 0) return { ok: false, reason: "unavailable" };
      const order = found.rows[0];
      if (order.taken) {
        return order.claimed_by === owner
          ? { ok: true, alreadyOwned: true, order: orderJson(order) }
          : { ok: false, reason: "unavailable" };
      }
      if (num(order.expire_epoch_ms) <= nowMs) {
        return { ok: false, reason: "unavailable" };
      }
      const result = await client.query(
        `UPDATE honey_orders SET taken=true, claimed_by=$2, updated_at=now()
          WHERE id=$1 RETURNING *`,
        [id, owner]
      );
      return { ok: true, order: orderJson(result.rows[0]) };
    }
    if (type === "release-order") {
      const owner = String(body.ownerId || "");
      if (!owner) return { ok: false, reason: "owner required" };
      const result = await client.query(
        `UPDATE honey_orders SET taken=false, claimed_by=NULL, updated_at=now()
          WHERE id=$1 AND taken=true AND claimed_by=$2`,
        [id, owner]
      );
      return { ok: result.rowCount > 0 };
    }
    if (type === "finish-order") {
      const owner = String(body.ownerId || "");
      if (!owner) return { ok: false, reason: "owner required" };
      const found = await client.query(
        "SELECT * FROM honey_orders WHERE id=$1 AND taken=true AND claimed_by=$2",
        [id, owner]
      );
      const result = await client.query(
        "DELETE FROM honey_orders WHERE id=$1 AND taken=true AND claimed_by=$2",
        [id, owner]
      );
      if (result.rowCount > 0 && found.rowCount > 0) {
        const dead = found.rows[0];
        const prices = await loadMarketPrices(client, dayKey);
        await spawnNearbyOrder(client, dead, nowMs, dayKey, prices);
      }
      return { ok: result.rowCount > 0 };
    }
    if (type === "take-offer") {
      const owner = String(body.ownerId || "");
      if (!owner) throw Object.assign(new Error("ownerId required"), { status: 400 });
      const found = await client.query(
        `SELECT id,taken,claimed_by FROM pollination_offers
          WHERE id=$1 FOR UPDATE`,
        [id]
      );
      if (found.rowCount === 0) return { ok: false, reason: "unavailable" };
      if (found.rows[0].taken) {
        return found.rows[0].claimed_by === owner
          ? { ok: true, alreadyOwned: true, offer: found.rows[0] }
          : { ok: false, reason: "unavailable" };
      }
      if (num(found.rows[0].expire_epoch_ms) <= nowMs) {
        return { ok: false, reason: "unavailable" };
      }
      const result = await client.query(
        `UPDATE pollination_offers SET taken=true, claimed_by=$2, updated_at=now()
          WHERE id=$1 RETURNING *`,
        [id, owner]
      );
      return { ok: true, offer: result.rows[0] };
    }
    throw Object.assign(new Error("unknown action"), { status: 400 });
  });
}

function orderJson(row) {
  return {
    id: row.id,
    npcName: row.npc_name,
    portraitIndex: int(row.portrait_index),
    floraKey: row.flora_key,
    kg: num(row.kg),
    unitPrice: num(row.unit_price),
    destHexId: row.dest_hex_id,
    destLat: num(row.dest_lat),
    destLng: num(row.dest_lng),
    destLabel: row.dest_label,
    region: row.region,
    createdDayKey: int(row.created_day_key),
    expireEpochMs: num(row.expire_epoch_ms),
    taken: Boolean(row.taken),
    claimedBy: row.claimed_by || "",
    band: int(row.band),
  };
}

function offerJson(row) {
  return {
    id: row.id,
    hexId: row.hex_id,
    flora: row.flora,
    startDoy: int(row.start_doy),
    endDoy: int(row.end_doy),
    band: int(row.band),
    region: row.region,
    createdDayKey: int(row.created_day_key),
    expireEpochMs: num(row.expire_epoch_ms),
    destLat: num(row.dest_lat),
    destLng: num(row.dest_lng),
    npcName: row.npc_name,
    portraitIndex: int(row.portrait_index),
    taken: Boolean(row.taken),
    claimedBy: row.claimed_by || "",
  };
}

function nearBox(near) {
  if (!near) return null;
  const lat = Number(near.lat);
  const lng = Number(near.lng);
  const km = Number(near.km);
  if (!Number.isFinite(lat) || !Number.isFinite(lng) || !Number.isFinite(km) || km <= 0) {
    return null;
  }
  const dLat = km / 111;
  const cos = Math.cos((lat * Math.PI) / 180);
  const dLng = km / (111 * Math.max(0.2, Math.abs(cos)));
  return { lat, lng, km, minLat: lat - dLat, maxLat: lat + dLat, minLng: lng - dLng, maxLng: lng + dLng };
}

function withinNear(box, lat, lng) {
  if (!box) return true;
  const p1 = (box.lat * Math.PI) / 180;
  const p2 = (lat * Math.PI) / 180;
  const dphi = ((lat - box.lat) * Math.PI) / 180;
  const dl = ((lng - box.lng) * Math.PI) / 180;
  const a = Math.sin(dphi / 2) ** 2 + Math.cos(p1) * Math.cos(p2) * Math.sin(dl / 2) ** 2;
  const km = 2 * 6371 * Math.asin(Math.min(1, Math.sqrt(a)));
  return km <= box.km;
}

async function snapshot(pool, region, ownerId, near) {
  const box = nearBox(near);
  const params = [region || null, ownerId || null];
  let boxSql = "";
  if (box) {
    params.push(box.minLat, box.maxLat, box.minLng, box.maxLng);
    boxSql = " AND dest_lat BETWEEN $3 AND $4 AND dest_lng BETWEEN $5 AND $6";
  }
  const result = await pool.query(
    `SELECT id,npc_name,portrait_index,flora_key,kg,unit_price,dest_hex_id,
            dest_lat,dest_lng,dest_label,region,created_day_key,expire_epoch_ms,
            taken,claimed_by,band
       FROM honey_orders
      WHERE ($1::text IS NULL OR region=$1)
         AND ($2::text IS NULL OR taken=false OR claimed_by=$2)
         ${boxSql}
      ORDER BY region,band,created_day_key,id`,
    params
  );
  const offers = await pool.query(
    `SELECT id,hex_id,flora,start_doy,end_doy,band,region,created_day_key,
            expire_epoch_ms,dest_lat,dest_lng,npc_name,portrait_index,taken,claimed_by
       FROM pollination_offers
      WHERE ($1::text IS NULL OR region=$1)
         AND ($2::text IS NULL OR taken=false OR claimed_by=$2)
         ${boxSql}
      ORDER BY region,band,created_day_key,id`,
    params
  );
  const orderRows = box
    ? result.rows.filter((row) => withinNear(box, num(row.dest_lat), num(row.dest_lng)))
    : result.rows;
  const offerRows = box
    ? offers.rows.filter((row) => withinNear(box, num(row.dest_lat), num(row.dest_lng)))
    : offers.rows;
  return {
    ready: clockReady,
    dayKey: utcDayKey(),
    partial: Boolean(box),
    orders: orderRows.map(orderJson),
    offers: offerRows.map(offerJson),
  };
}

function start(pool) {
  let running = false;
  const run = () => {
    if (running) return;
    running = true;
    tick(pool)
      .catch((err) => console.error("reloj de ofertas:", err.message))
      .finally(() => { running = false; });
  };
  run();
  const timer = setInterval(run, 30000);
  if (typeof timer.unref === "function") timer.unref();
}

module.exports = {
  start,
  tick,
  action,
  snapshot,
  pickScattered,
  nearbyReplacement,
  orderUnitPrice,
  orderCount,
  offerCount,
  utcDayKey,
  ORDER_LIFE_MS,
  stats: catalog.stats,
};
