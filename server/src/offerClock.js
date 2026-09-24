"use strict";

const catalog = require("./offerCatalog");

const REGIONS = ["iberia", "za", "mdg"];
const HEXES_PER_BATCH = 50;
const ORDERS_PER_BATCH = 2;
const OFFERS_PER_BATCH = 2;
const POLLINATION_HEXES_PER_BATCH = 70;
const PRICE_BONUS = 1.12;
const GRID_COLS = 12;
const GRID_ROWS = 10;
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

function haversineKm(lat1, lon1, lat2, lon2) {
  const r = 6371;
  const p1 = lat1 * Math.PI / 180;
  const p2 = lat2 * Math.PI / 180;
  const dLat = (lat2 - lat1) * Math.PI / 180;
  const dLon = (lon2 - lon1) * Math.PI / 180;
  const a = Math.sin(dLat / 2) ** 2
    + Math.cos(p1) * Math.cos(p2) * Math.sin(dLon / 2) ** 2;
  return 2 * r * Math.asin(Math.min(1, Math.sqrt(a)));
}

// El precio se toma del snapshot de mercado almacenado por la API. El mínimo
// de 12 €/kg es el fallback económico cuando todavía no hay snapshot del día.
function basePrice(flora, prices) {
  const value = prices && prices.get(catalog.canonicalFlora(flora));
  return Number.isFinite(value) && value > 0 ? value : 12.0;
}

function cropForParcel(parcel, band, nowMs, dayKey) {
  return catalog.offerForParcel(parcel, band, nowMs, dayKey);
}

function floraForParcel(parcel, band, seed) {
  const maxLevel = catalog.BAND_MAX_LEVEL[band] || 99;
  const candidates = (parcel.nativePool || [])
    .filter((flora) => catalog.floraAccessLevel(flora) <= maxLevel);
  const pool = candidates.length ? candidates : ["Mil flores"];
  return pool[floorMod(hash32(`${parcel.id}:flora:${seed}`), pool.length)];
}

function orderCount(eligible) {
  return eligible < HEXES_PER_BATCH ? 0 : Math.floor((eligible * ORDERS_PER_BATCH) / HEXES_PER_BATCH);
}

function offerCount(parcelCount, band) {
  if (parcelCount <= 0) return 0;
  const density = Math.max(OFFERS_PER_BATCH,
      Math.floor((parcelCount * OFFERS_PER_BATCH) / POLLINATION_HEXES_PER_BATCH));
  return Math.min(48, Math.max(catalog.BAND_POLLINATION_COUNT[band] || 0, density));
}

function bucketCells(parcels) {
  if (!parcels.length) return new Map();
  let minLat = 90, maxLat = -90, minLng = 180, maxLng = -180;
  for (const p of parcels) {
    minLat = Math.min(minLat, p.lat);
    maxLat = Math.max(maxLat, p.lat);
    minLng = Math.min(minLng, p.lng);
    maxLng = Math.max(maxLng, p.lng);
  }
  const latSpan = Math.max(0.01, maxLat - minLat);
  const lngSpan = Math.max(0.01, maxLng - minLng);
  const cells = new Map();
  for (const p of parcels) {
    const row = Math.max(0, Math.min(GRID_ROWS - 1,
        Math.floor((p.lat - minLat) / latSpan * GRID_ROWS)));
    const col = Math.max(0, Math.min(GRID_COLS - 1,
        Math.floor((p.lng - minLng) / lngSpan * GRID_COLS)));
    const key = row * GRID_COLS + col;
    if (!cells.has(key)) cells.set(key, []);
    cells.get(key).push(p);
  }
  return cells;
}

function pickScattered(parcels, want, used, seed) {
  const available = parcels.filter((p) => p && !used.has(p.id));
  if (!available.length || want <= 0) return [];
  const cells = bucketCells(available);
  const keys = Array.from(cells.keys()).sort((a, b) => {
    const ah = hash64(`offer-cell:${seed}:${a}`) & 0x7fffffffffffffffn;
    const bh = hash64(`offer-cell:${seed}:${b}`) & 0x7fffffffffffffffn;
    return ah < bh ? -1 : ah > bh ? 1 : 0;
  });
  const out = [];
  const visited = new Set();
  const stride = Math.max(1, Math.floor(keys.length / Math.max(1, want)));
  for (let pass = 0; pass < 2 && out.length < want; pass++) {
    for (let i = pass; i < keys.length && out.length < want; i += stride) {
      const key = keys[i];
      if (visited.has(key)) continue;
      visited.add(key);
      const bucket = cells.get(key) || [];
      const start = floorMod(hash32(`${seed}:${key}:${pass}`), bucket.length);
      for (let n = 0; n < bucket.length; n++) {
        const p = bucket[(start + n) % bucket.length];
        if (!p || used.has(p.id)) continue;
        used.add(p.id);
        out.push(p);
        break;
      }
    }
  }
  for (const key of keys) {
    if (out.length >= want) break;
    if (visited.has(key)) continue;
    visited.add(key);
    const bucket = cells.get(key) || [];
    const p = bucket[floorMod(hash32(`${seed}:${key}:fill`), bucket.length)];
    if (p && !used.has(p.id)) {
      used.add(p.id);
      out.push(p);
    }
  }
  // Si la malla tiene menos celdas que el cupo, completa con parcels libres
  // sin duplicar el mismo destino.
  for (const p of available) {
    if (out.length >= want) break;
    if (used.has(p.id)) continue;
    used.add(p.id);
    out.push(p);
  }
  return out;
}

function nearestReplacement(parcels, dead, used, band, seed) {
  if (!dead) return null;
  const ranked = parcels
    .filter((p) => !used.has(p.id))
    .map((p) => ({ p, km: haversineKm(dead.dest_lat, dead.dest_lng, p.lat, p.lng) }))
    .sort((a, b) => a.km - b.km);
  for (const radius of [40, 80, 180, 10000]) {
    const hit = ranked.find((x) => x.km <= radius);
    if (hit) {
      used.add(hit.p.id);
      return hit.p;
    }
  }
  return null;
}

function orderRow(region, band, parcel, dayKey, nowMs, seed, old, prices) {
  const flora = floraForParcel(parcel, band, seed);
  const oldPrice = old && num(old.unit_price) > 0 ? num(old.unit_price) : 0;
  const id = `srv-ho-${region}-${dayKey}-${band}-${hash32(`${parcel.id}:${seed}`).toString(16)}`;
  return {
    id,
    npc_name: catalog.npc(parcel),
    portrait_index: parcel.npcIndex,
    flora_key: flora,
    kg: kgForBand(band, seed),
    unit_price: oldPrice > 0
      ? oldPrice
      : Math.round(basePrice(flora, prices) * PRICE_BONUS * 100) / 100,
    dest_hex_id: parcel.id,
    dest_lat: parcel.lat,
    dest_lng: parcel.lng,
    dest_label: parcel.place || parcel.id,
    region,
    created_day_key: dayKey,
    expire_epoch_ms: utcDayEnd(dayKey),
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

async function insertOrder(client, row) {
  await client.query(
    `INSERT INTO honey_orders
      (id,npc_name,portrait_index,flora_key,kg,unit_price,dest_hex_id,dest_lat,dest_lng,
       dest_label,region,created_day_key,expire_epoch_ms,taken,claimed_by,band)
     VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13,$14,$15,$16)
     ON CONFLICT (id) DO NOTHING`,
    [row.id,row.npc_name,row.portrait_index,row.flora_key,row.kg,row.unit_price,
      row.dest_hex_id,row.dest_lat,row.dest_lng,row.dest_label,row.region,
      row.created_day_key,row.expire_epoch_ms,row.taken,row.claimed_by,row.band]
  );
}

async function insertOffer(client, row) {
  await client.query(
    `INSERT INTO pollination_offers
      (id,hex_id,flora,start_doy,end_doy,band,region,created_day_key,expire_epoch_ms,
       dest_lat,dest_lng,npc_name,portrait_index,taken,claimed_by)
     VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13,$14,$15)
     ON CONFLICT (id) DO NOTHING`,
    [row.id,row.hex_id,row.flora,row.start_doy,row.end_doy,row.band,row.region,
      row.created_day_key,row.expire_epoch_ms,row.dest_lat,row.dest_lng,
      row.npc_name,row.portrait_index,row.taken,row.claimed_by || null]
  );
}

async function maintainRegion(client, region, nowMs, dayKey, prices) {
  const all = catalog.getParcels(region);
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
    const replacement = nearestReplacement(eligible, dead, usedOrders, band,
        `${dayKey}:${dead.id}`);
    await client.query("DELETE FROM honey_orders WHERE id=$1", [dead.id]);
    if (replacement) {
      await insertOrder(client, orderRow(region, band, replacement, dayKey, nowMs,
          `${dayKey}:${dead.id}`, dead, prices));
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
      if (row) await insertOffer(client, row);
    }
  }

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
    const wantOrders = orderCount(orderParcels.length);
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
        await insertOrder(client, orderRow(region, band, parcel, dayKey, nowMs,
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
        if (row) await insertOffer(client, row);
      }
    }
  }
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

async function saveMarketPrices(pool, body) {
  const dayKey = int(body && body.dayKey);
  const prices = body && body.prices;
  if (dayKey <= 0 || !prices || typeof prices !== "object") {
    throw Object.assign(new Error("market prices required"), { status: 400 });
  }
  const client = await pool.connect();
  try {
    await client.query("BEGIN");
    for (const [flora, raw] of Object.entries(prices)) {
      const price = num(raw);
      if (!flora || price <= 0) continue;
      await client.query(
        `INSERT INTO server_market_prices(day_key,flora_key,price_eur)
         VALUES ($1,$2,$3)
         ON CONFLICT(day_key,flora_key) DO UPDATE SET price_eur=EXCLUDED.price_eur,updated_at=now()`,
        [dayKey, flora, price]
      );
    }
    await client.query("COMMIT");
    return { ok: true, dayKey };
  } catch (err) {
    try { await client.query("ROLLBACK"); } catch (_) { /* connection closed */ }
    throw err;
  } finally {
    client.release();
  }
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

async function action(pool, body) {
  const nowMs = Date.now();
  return runWithLock(pool, async (client) => {
    const type = String(body && body.type || "");
    const id = String(body && body.id || "");
    if (type === "claim-pollination-offer") {
      const hexId = String(body && body.hexId || "");
      const band = int(body && body.band);
      const flora = String(body && body.flora || "");
      const startDoy = int(body && body.startDoy);
      const owner = String(body && body.ownerId || "");
      if (!hexId || flora.isEmpty() || owner.isEmpty()) {
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
      if (!hexId) throw Object.assign(new Error("hexId required"), { status: 400 });
      const found = await client.query(
        `SELECT id FROM pollination_offers
          WHERE hex_id=$1 AND taken=false AND expire_epoch_ms>$2
            AND ($3::int < 0 OR band=$3)
          ORDER BY created_day_key,id LIMIT 1 FOR UPDATE`,
        [hexId, nowMs, band]
      );
      if (found.rowCount === 0) return { ok: false, reason: "unavailable" };
      await client.query(
        `UPDATE pollination_offers SET taken=true, updated_at=now() WHERE id=$1`,
        [found.rows[0].id]
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
          ? { ok: true, alreadyOwned: true, order }
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
      return { ok: true, order: result.rows[0] };
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
      const result = await client.query(
        "DELETE FROM honey_orders WHERE id=$1 AND taken=true AND claimed_by=$2",
        [id, owner]
      );
      return { ok: result.rowCount > 0 };
    }
    if (type === "take-offer") {
      const result = await client.query(
        `UPDATE pollination_offers SET taken=true, updated_at=now()
         WHERE id=$1 AND taken=false AND expire_epoch_ms>$2 RETURNING *`,
        [id, nowMs]
      );
      if (result.rowCount === 0) return { ok: false, reason: "unavailable" };
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

async function snapshot(pool, region, ownerId) {
  const result = await pool.query(
    `SELECT id,npc_name,portrait_index,flora_key,kg,unit_price,dest_hex_id,
            dest_lat,dest_lng,dest_label,region,created_day_key,expire_epoch_ms,
            taken,claimed_by,band
       FROM honey_orders
      WHERE ($1::text IS NULL OR region=$1)
         AND ($2::text IS NULL OR taken=false OR claimed_by=$2)
      ORDER BY region,band,created_day_key,id`,
    [region || null, ownerId || null]
  );
  const offers = await pool.query(
    `SELECT id,hex_id,flora,start_doy,end_doy,band,region,created_day_key,
            expire_epoch_ms,dest_lat,dest_lng,npc_name,portrait_index,taken,claimed_by
       FROM pollination_offers
      WHERE ($1::text IS NULL OR region=$1)
         AND ($2::text IS NULL OR taken=false OR claimed_by=$2)
      ORDER BY region,band,created_day_key,id`,
    [region || null, ownerId || null]
  );
  return {
    ready: clockReady,
    dayKey: utcDayKey(),
    orders: result.rows.map(orderJson),
    offers: offers.rows.map(offerJson),
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
  saveMarketPrices,
  snapshot,
  pickScattered,
  orderCount,
  offerCount,
  utcDayKey,
  stats: catalog.stats,
};
