"use strict";

const { randomUUID } = require("crypto");
const { encode, decode } = require("./polyline");

const MIN_DURATION_MS = 60_000;
const CRUISE_KMH = 70;
const CARGO_KINDS = new Set(["collect", "wholesale", "order", "transfer", "delivery"]);

function num(value) {
  const n = Number(value);
  return Number.isFinite(n) ? n : 0;
}

function sameCoordinate(bodyValue, rowValue) {
  return bodyValue == null || Math.abs(num(bodyValue) - num(rowValue)) <= 0.00001;
}

function sameEndpoints(body, row) {
  return sameCoordinate(body && body.originLat, row.origin_lat)
    && sameCoordinate(body && body.originLng, row.origin_lng)
    && sameCoordinate(body && body.destLat, row.dest_lat)
    && sameCoordinate(body && body.destLng, row.dest_lng);
}

function haversineKm(lat1, lon1, lat2, lon2) {
  const r = 6371;
  const p1 = (lat1 * Math.PI) / 180;
  const p2 = (lat2 * Math.PI) / 180;
  const dphi = ((lat2 - lat1) * Math.PI) / 180;
  const dl = ((lon2 - lon1) * Math.PI) / 180;
  const a = Math.sin(dphi / 2) ** 2 + Math.cos(p1) * Math.cos(p2) * Math.sin(dl / 2) ** 2;
  return 2 * r * Math.asin(Math.min(1, Math.sqrt(a)));
}

function durationMs(fromLat, fromLng, toLat, toLng) {
  const km = haversineKm(fromLat, fromLng, toLat, toLng);
  return Math.max(MIN_DURATION_MS, Math.round((km / CRUISE_KMH) * 3_600_000));
}

function arrivedAt(row) {
  return num(row.start_epoch_ms) + num(row.duration_ms);
}

function cargoMap(raw) {
  let parsed = {};
  try {
    parsed = JSON.parse(raw || "{}");
  } catch (err) {
    parsed = {};
  }
  const lines = [];
  for (const [key, value] of Object.entries(parsed)) {
    if (!key || key.startsWith("_")) continue;
    const kg = num(value);
    if (kg <= 1e-9) continue;
    lines.push({ flora: key, kg });
  }
  return { parsed, lines };
}

function sameNumber(a, b, tolerance = 0.001) {
  return Math.abs(num(a) - num(b)) <= tolerance;
}

function hasFiniteNumber(value) {
  return value != null && value !== "" && Number.isFinite(Number(value));
}

function sameRequiredNumber(a, b, tolerance = 0.001) {
  return hasFiniteNumber(a) && hasFiniteNumber(b) && sameNumber(a, b, tolerance);
}

async function authoritativeOrder(client, trip, when) {
  if (trip.kind !== "order" || !trip.order_id || !trip.owner_id) {
    return { valid: false, row: null };
  }
  const result = await client.query(
    "SELECT * FROM honey_orders WHERE id = $1 FOR UPDATE",
    [trip.order_id]
  );
  if (result.rowCount === 0) {
    return { valid: false, row: null };
  }
  const row = result.rows[0];
  const cargo = cargoMap(trip.cargo_json);
  const valid = Boolean(row.taken)
    && String(row.claimed_by || "") === String(trip.owner_id)
    && cargo.lines.length === 1
    && cargo.lines[0].flora === row.flora_key
    && sameRequiredNumber(cargo.lines[0].kg, row.kg)
    && sameRequiredNumber(trip.kg, row.kg)
    && trip.flora_key != null
    && trip.flora_key === row.flora_key
    && trip.dest_hex_id != null
    && trip.dest_hex_id === row.dest_hex_id
    && sameRequiredNumber(trip.dest_lat, row.dest_lat, 0.00001)
    && sameRequiredNumber(trip.dest_lng, row.dest_lng, 0.00001)
    && sameRequiredNumber(trip.unit_price, row.unit_price, 0.01);
  return { valid, row };
}

function publishedOrderMatches(body, row, now) {
  if (!row || !body || body.kind !== "order" || !body.orderId) return false;
  const cargo = cargoMap(body.cargoJson);
  return row.taken === true
    && String(row.claimed_by || "") === String(body.ownerId || "")
    && cargo.lines.length === 1
    && cargo.lines[0].flora === row.flora_key
    && sameRequiredNumber(cargo.lines[0].kg, row.kg)
    && sameRequiredNumber(body.kg, row.kg)
    && body.floraKey != null
    && body.floraKey === row.flora_key
    && body.destHexId != null
    && body.destHexId === row.dest_hex_id
    && sameRequiredNumber(body.destLat, row.dest_lat, 0.00001)
    && sameRequiredNumber(body.destLng, row.dest_lng, 0.00001)
    && sameRequiredNumber(body.unitPrice, row.unit_price, 0.01);
}

async function insertEffect(client, ownerId, payload) {
  await client.query(
    "INSERT INTO trip_effects (id, owner_id, payload) VALUES ($1, $2, $3::jsonb)",
    [randomUUID(), ownerId, JSON.stringify(payload)]
  );
}

async function recordCompletion(client, trip) {
  await client.query(
    `INSERT INTO trip_completions (trip_id, owner_id, finished_start_epoch_ms)
     VALUES ($1, $2, $3)
     ON CONFLICT (trip_id, finished_start_epoch_ms) DO NOTHING`,
    [trip.id, trip.owner_id, num(trip.start_epoch_ms)]
  );
}

async function saveCargo(client, trip) {
  await client.query(
    `UPDATE cargo_trips SET
        phase = $2,
        leg_role = $3,
        flora_key = $4,
        kg = $5,
        cargo_json = $6,
        origin_lat = $7,
        origin_lng = $8,
        dest_lat = $9,
        dest_lng = $10,
        origin_label = $11,
        dest_label = $12,
        origin_hex_id = $13,
        dest_hex_id = $14,
        route_polyline = $15,
        route_road_kinds = $16,
        start_epoch_ms = $17,
        duration_ms = $18,
        chain_lat = $19,
        chain_lng = $20,
        chain_label = $21,
        chain_hex_id = $22,
        updated_at = now()
      WHERE id = $1`,
    [
      trip.id,
      trip.phase,
      trip.leg_role,
      trip.flora_key,
      trip.kg,
      trip.cargo_json,
      trip.origin_lat,
      trip.origin_lng,
      trip.dest_lat,
      trip.dest_lng,
      trip.origin_label,
      trip.dest_label,
      trip.origin_hex_id,
      trip.dest_hex_id,
      trip.route_polyline,
      trip.route_road_kinds,
      trip.start_epoch_ms,
      trip.duration_ms,
      trip.chain_lat,
      trip.chain_lng,
      trip.chain_label,
      trip.chain_hex_id,
    ]
  );
}

function setGeodesic(trip, fromLat, fromLng, toLat, toLng, when) {
  trip.origin_lat = fromLat;
  trip.origin_lng = fromLng;
  trip.dest_lat = toLat;
  trip.dest_lng = toLng;
  trip.route_polyline = encode([[fromLat, fromLng], [toLat, toLng]]);
  trip.route_road_kinds = null;
  trip.start_epoch_ms = when;
  trip.duration_ms = durationMs(fromLat, fromLng, toLat, toLng);
}

function tourLegNear(parsed, fromLat, fromLng, lat, lng) {
  const tour = parsed && parsed._tour;
  if (!Array.isArray(tour)) return null;
  let candidate = null;
  for (const leg of tour) {
    if (!leg) continue;
    const fromDistance = haversineKm(num(leg.fromLat), num(leg.fromLng), fromLat, fromLng);
    const toDistance = haversineKm(num(leg.toLat), num(leg.toLng), lat, lng);
    if (fromDistance <= 0.08 && toDistance <= 0.08) return leg;
    if (fromDistance < 0.4 && toDistance < 0.4) {
      // No se elige un tramo arbitrario cuando dos paradas están demasiado cerca.
      if (candidate) return null;
      candidate = leg;
    }
  }
  return candidate;
}

function collectArrivesHome(row) {
  let parsed;
  try {
    parsed = JSON.parse(row.cargo_json || "{}");
  } catch (err) {
    return false;
  }
  const tour = parsed && parsed._tour;
  const later = parsed && parsed._stops;
  if (!Array.isArray(tour) || tour.length < 2) return false;
  if (Array.isArray(later) && later.length > 0) return false;
  const back = tour[tour.length - 1];
  if (!back || back.whTo !== true) return false;
  return haversineKm(num(row.dest_lat), num(row.dest_lng), num(row.return_lat), num(row.return_lng)) < 0.4;
}

function applyStored(trip, leg, fromLat, fromLng, toLat, toLng, when) {
  if (!leg || !leg.poly) {
    setGeodesic(trip, fromLat, fromLng, toLat, toLng, when);
    return;
  }
  trip.origin_lat = fromLat;
  trip.origin_lng = fromLng;
  trip.dest_lat = toLat;
  trip.dest_lng = toLng;
  trip.route_polyline = String(leg.poly);
  trip.route_road_kinds = leg.kinds || null;
  trip.start_epoch_ms = when;
  const ms = num(leg.ms);
  trip.duration_ms = ms > 0 ? ms : durationMs(fromLat, fromLng, toLat, toLng);
}

async function arriveTruck(client, row) {
  const flora = row.dest_flora && String(row.dest_flora).trim() ? String(row.dest_flora).trim() : null;
  const hexId = row.dest_hex_id && String(row.dest_hex_id).trim() ? String(row.dest_hex_id).trim() : null;
  await client.query(
    `UPDATE hives SET
        lat = $2,
        lng = $3,
        hex_id = COALESCE($4, hex_id),
        flora_type = COALESCE($5, flora_type),
        updated_at = now()
      WHERE id = $1`,
    [row.id, num(row.dest_lat), num(row.dest_lng), hexId, flora]
  );
  await recordCompletion(client, row);
  await insertEffect(client, row.owner_id, {
    type: "truck-arrive",
    tripId: row.id,
    hiveId: row.id,
    lat: num(row.dest_lat),
    lng: num(row.dest_lng),
    hexId,
    flora,
    finishedStartEpochMs: num(row.start_epoch_ms),
  });
  await client.query("DELETE FROM truck_trips WHERE id = $1", [row.id]);
}

async function promotePickup(client, trip, when) {
  const fromLat = num(trip.dest_lat);
  const fromLng = num(trip.dest_lng);
  trip.leg_role = "deliver";
  trip.phase = "out";
  trip.origin_label = trip.dest_label;
  trip.origin_hex_id = trip.dest_hex_id;
  trip.dest_label = trip.chain_label || "Mercado";
  trip.dest_hex_id = trip.chain_hex_id;
  setGeodesic(trip, fromLat, fromLng, num(trip.chain_lat), num(trip.chain_lng), when);
  await saveCargo(client, trip);
}

async function advanceTransfer(client, trip, when) {
  const { parsed, lines } = cargoMap(trip.cargo_json);
  const nextKg = num(parsed._nextKg);
  const flora = trip.flora_key || (lines[0] && lines[0].flora) || null;
  const drop = Math.max(0, num(trip.kg) - nextKg);
  if (drop > 1e-9 && flora) {
    await insertEffect(client, trip.owner_id, {
      type: "warehouse-credit",
      tripId: trip.id,
      hexId: trip.dest_hex_id,
      flora,
      kg: drop,
      finishedStartEpochMs: num(trip.start_epoch_ms),
    });
  }
  if (nextKg <= 1e-9 || Math.abs(num(trip.chain_lat)) < 1e-8) {
    return false;
  }
  const fromLat = num(trip.dest_lat);
  const fromLng = num(trip.dest_lng);
  const toLat = num(trip.chain_lat);
  const toLng = num(trip.chain_lng);
  trip.kg = nextKg;
  trip.flora_key = flora;
  trip.cargo_json = JSON.stringify({ [flora || "Mil flores"]: nextKg });
  trip.dest_label = trip.chain_label || "Almacén";
  trip.dest_hex_id = trip.chain_hex_id;
  trip.chain_lat = 0;
  trip.chain_lng = 0;
  trip.chain_label = null;
  trip.chain_hex_id = null;
  setGeodesic(trip, fromLat, fromLng, toLat, toLng, when);
  await saveCargo(client, trip);
  return true;
}

async function advanceCollectStops(client, trip, when) {
  let parsed;
  try {
    parsed = JSON.parse(trip.cargo_json || "{}");
  } catch (err) {
    return false;
  }
  let later = parsed._stops;
  if (!Array.isArray(later) || later.length === 0) return false;
  const fromLat = num(trip.dest_lat);
  const fromLng = num(trip.dest_lng);
  let nextIndex = 0;
  while (nextIndex < later.length) {
    const hop = later[nextIndex] || {};
    if (haversineKm(fromLat, fromLng, num(hop.lat), num(hop.lng)) >= 0.08) break;
    nextIndex += 1;
  }
  if (nextIndex >= later.length) return false;
  const next = later[nextIndex] || {};
  parsed._stops = later.slice(nextIndex + 1);
  trip.cargo_json = JSON.stringify(parsed);
  const fromLabel = trip.dest_label;
  const fromHex = trip.dest_hex_id;
  const toLat = num(next.lat);
  const toLng = num(next.lng);
  trip.origin_label = fromLabel;
  trip.origin_hex_id = fromHex;
  trip.dest_label = next.label || "Apiario";
  trip.dest_hex_id = next.hex || "";
  trip.phase = "out";
  applyStored(trip, tourLegNear(parsed, fromLat, fromLng, toLat, toLng), fromLat, fromLng, toLat, toLng, when);
  await saveCargo(client, trip);
  return true;
}

async function startReturn(client, trip, when) {
  if (trip.leg_role === "haul-ship") {
    const pts = decode(trip.route_polyline);
    pts.reverse();
    const fromLat = num(trip.dest_lat);
    const fromLng = num(trip.dest_lng);
    const dur = num(trip.duration_ms);
    trip.phase = "return";
    trip.origin_label = trip.dest_label;
    trip.origin_hex_id = trip.dest_hex_id;
    trip.dest_label = trip.return_label || "Puerto";
    trip.dest_hex_id = trip.return_hex_id;
    trip.origin_lat = fromLat;
    trip.origin_lng = fromLng;
    trip.dest_lat = num(trip.return_lat);
    trip.dest_lng = num(trip.return_lng);
    trip.route_polyline = pts.length >= 2 ? encode(pts) : encode([[fromLat, fromLng], [num(trip.return_lat), num(trip.return_lng)]]);
    trip.route_road_kinds = null;
    trip.start_epoch_ms = when;
    trip.duration_ms = dur > 0 ? dur : MIN_DURATION_MS;
    dropDeliveredCargo(trip);
    await saveCargo(client, trip);
    return;
  }
  const fromLat = num(trip.dest_lat);
  const fromLng = num(trip.dest_lng);
  trip.phase = "return";
  trip.origin_label = trip.dest_label;
  trip.origin_hex_id = trip.dest_hex_id;
  trip.dest_label = trip.return_label || "Almacén";
  trip.dest_hex_id = trip.return_hex_id;
  let parsed = {};
  try {
    parsed = JSON.parse(trip.cargo_json || "{}");
  } catch (err) {
    parsed = {};
  }
  applyStored(
    trip,
    tourLegNear(parsed, fromLat, fromLng, num(trip.return_lat), num(trip.return_lng)),
    fromLat,
    fromLng,
    num(trip.return_lat),
    num(trip.return_lng),
    when
  );
  dropDeliveredCargo(trip);
  await saveCargo(client, trip);
}

function dropDeliveredCargo(trip) {
  if (!trip || trip.kind === "collect") return;
  trip.kg = 0;
  trip.flora_key = null;
  let parsed = {};
  try {
    parsed = JSON.parse(trip.cargo_json || "{}");
  } catch (err) {
    parsed = {};
  }
  const kept = {};
  for (const key of Object.keys(parsed)) {
    if (key.startsWith("_")) kept[key] = parsed[key];
  }
  trip.cargo_json = JSON.stringify(kept);
}

function collectLines(trip) {
  const cargo = cargoMap(trip.cargo_json);
  if (cargo.lines.length > 0) return cargo.lines;
  const tour = cargo.parsed && cargo.parsed._tour;
  if (!Array.isArray(tour)) return [];
  const sum = new Map();
  for (const leg of tour) {
    if (!leg || leg.whTo === true || !leg.load || typeof leg.load !== "object") continue;
    for (const [flora, value] of Object.entries(leg.load)) {
      const kg = num(value);
      if (!flora || flora.startsWith("_") || kg <= 1e-9) continue;
      sum.set(flora, (sum.get(flora) || 0) + kg);
    }
  }
  const lines = [];
  for (const [flora, kg] of sum) {
    lines.push({ flora, kg: Math.round(kg * 1000) / 1000 });
  }
  return lines;
}

async function creditWarehouseStore(client, ownerId, hexId, lines, seq) {
  const id = ownerId + ":warehouse";
  const found = await client.query("SELECT body FROM player_stores WHERE id = $1 FOR UPDATE", [id]);
  let body = found.rowCount > 0 ? found.rows[0].body : {};
  if (typeof body === "string") {
    try { body = JSON.parse(body); } catch (err) { body = {}; }
  }
  if (!body || typeof body !== "object" || Array.isArray(body)) body = {};
  const stock = body.stock && typeof body.stock === "object" && !Array.isArray(body.stock)
    ? body.stock : {};
  if (hexId) {
    const hex = stock[hexId] && typeof stock[hexId] === "object" ? stock[hexId] : {};
    for (const line of lines) {
      hex[line.flora] = Math.round(((Number(hex[line.flora]) || 0) + line.kg) * 1000) / 1000;
    }
    stock[hexId] = hex;
  }
  body.stock = stock;
  body.honeyStockSeq = seq;
  await client.query(
    `INSERT INTO player_stores (id, owner_id, kind, body, updated_at)
     VALUES ($1, $2, 'warehouse', $3::jsonb, now())
     ON CONFLICT (id) DO UPDATE SET body = EXCLUDED.body, updated_at = now()`,
    [id, ownerId, JSON.stringify(body)]
  );
  return stock;
}

async function creditCollect(client, ownerId, hexId, lines) {
  if (!ownerId || !lines || lines.length === 0) return null;
  const player = await client.query(
    "SELECT economy_honey_buckets_json FROM players WHERE id = $1 FOR UPDATE",
    [ownerId]
  );
  if (player.rowCount === 0) return null;
  let buckets = {};
  try {
    buckets = JSON.parse(player.rows[0].economy_honey_buckets_json || "{}");
  } catch (err) {
    buckets = {};
  }
  if (!buckets || typeof buckets !== "object" || Array.isArray(buckets)) buckets = {};
  for (const line of lines) {
    buckets[line.flora] = Math.round(((Number(buckets[line.flora]) || 0) + line.kg) * 1000) / 1000;
  }
  const updated = await client.query(
    `UPDATE players
        SET economy_honey_buckets_json = $2,
            honey_stock_seq = honey_stock_seq + 1,
            updated_at = now()
      WHERE id = $1
      RETURNING economy_honey_buckets_json, honey_stock_seq`,
    [ownerId, JSON.stringify(buckets)]
  );
  const seq = Number(updated.rows[0].honey_stock_seq);
  const warehouseStock = await creditWarehouseStore(client, ownerId, hexId, lines, seq);
  return {
    honeyBuckets: updated.rows[0].economy_honey_buckets_json,
    honeyStockSeq: seq,
    warehouseStock,
  };
}

async function settle(client, trip, when) {
  const authority = trip.kind === "order"
    ? await authoritativeOrder(client, trip, when)
    : { valid: true, row: null };
  const cargo = cargoMap(trip.cargo_json);
  const order = authority.row;
  const lines = order
    ? [{ flora: order.flora_key, kg: num(order.kg) }]
    : cargo.lines;
  const payload = {
    type: trip.kind === "collect" ? "collect-credit" : trip.kind === "transfer" || trip.leg_role === "transfer" ? "warehouse-lines" : "sale",
    tripId: trip.id,
    kind: trip.kind,
    orderId: trip.order_id,
    destHexId: order ? order.dest_hex_id : trip.dest_hex_id,
    destLabel: order ? order.dest_label : trip.dest_label,
    unitPrice: order ? num(order.unit_price) : num(trip.unit_price),
    priceLocked: order ? true : num(trip.price_locked) === 1,
    lines,
    finishedStartEpochMs: num(trip.start_epoch_ms),
  };
  if (trip.kind === "collect") {
    payload.lines = collectLines(trip);
    payload.hiveCount = num(cargo.parsed && cargo.parsed._hiveCount) || 0;
    const hex = trip.return_hex_id || trip.dest_hex_id || "";
    if (hex) payload.destHexId = hex;
    const credited = await creditCollect(client, trip.owner_id, hex, payload.lines);
    if (credited) {
      payload.honeyBuckets = credited.honeyBuckets;
      payload.honeyStockSeq = credited.honeyStockSeq;
      payload.warehouseStock = credited.warehouseStock;
    }
  }
  if (payload.type === "sale" && trip.kind === "order") {
    payload.missed = !authority.valid;
  }
  if (payload.type === "sale" && !payload.missed) {
    let euros = 0;
    for (const line of lines) euros += num(line.kg) * num(payload.unitPrice);
    euros = Math.round(euros * 100) / 100;
    if (euros > 0 && trip.owner_id) {
      const credited = await client.query(
        `UPDATE players
            SET economy_balance_eur = economy_balance_eur + $2, updated_at = now()
          WHERE id = $1
          RETURNING economy_balance_eur`,
        [trip.owner_id, euros]
      );
      if (credited.rowCount > 0) {
        payload.balanceCredited = true;
        payload.creditedEur = euros;
        payload.balanceEur = num(credited.rows[0].economy_balance_eur);
      }
    }
  }
  await insertEffect(client, trip.owner_id, payload);
  // Solo una comanda válida y vinculada al owner puede cerrar la fila.
  if (trip.kind === "order" && authority.valid && trip.order_id) {
    await client.query("DELETE FROM honey_orders WHERE id = $1", [trip.order_id]);
  }
}

async function finishCargo(client, trip) {
  await recordCompletion(client, trip);
  await insertEffect(client, trip.owner_id, {
    type: "release-vehicle",
    tripId: trip.id,
    vehicleId: trip.vehicle_id,
    finishedStartEpochMs: num(trip.start_epoch_ms),
  });
  await client.query("DELETE FROM cargo_trips WHERE id = $1", [trip.id]);
}

async function advanceCargo(client, row) {
  const when = arrivedAt(row);
  if (row.kind === "delivery") {
    await finishCargo(client, row);
    return;
  }
  if (row.phase === "out") {
    if (row.leg_role === "pickup") {
      await promotePickup(client, row, when);
      return;
    }
    if (row.leg_role === "transfer") {
      if (!(await advanceTransfer(client, row, when))) {
        await startReturn(client, row, when);
      }
      return;
    }
    if (row.leg_role === "haul-truck" || row.leg_role === "haul-ship") {
      await startReturn(client, row, when);
      return;
    }
    if (row.kind === "collect" && (await advanceCollectStops(client, row, when))) {
      return;
    }
    if (row.kind === "collect" && collectArrivesHome(row)) {
      await settle(client, row, when);
      await finishCargo(client, row);
      return;
    }
    if (row.kind !== "collect") {
      await settle(client, row, when);
    }
    await startReturn(client, row, when);
    return;
  }
  if (row.kind === "collect") {
    await settle(client, row, when);
  }
  await finishCargo(client, row);
}

async function tick(pool) {
  const client = await pool.connect();
  try {
    await client.query("BEGIN");
    const now = Date.now();
    for (let pass = 0; pass < 30; pass++) {
      const trucks = await client.query(
        `SELECT * FROM truck_trips
         WHERE start_epoch_ms + duration_ms <= $1
         FOR UPDATE SKIP LOCKED`,
        [now]
      );
      const cargos = await client.query(
        `SELECT * FROM cargo_trips
         WHERE start_epoch_ms + duration_ms <= $1
         ORDER BY start_epoch_ms
         FOR UPDATE SKIP LOCKED`,
        [now]
      );
      if (trucks.rowCount === 0 && cargos.rowCount === 0) break;
      for (const row of trucks.rows) {
        await arriveTruck(client, row);
      }
      for (const row of cargos.rows) {
        await advanceCargo(client, row);
      }
    }
    await client.query("COMMIT");
  } catch (err) {
    await client.query("ROLLBACK");
    throw err;
  } finally {
    client.release();
  }
}

async function clientMayOverwrite(pool, table, id, body) {
  if (table !== "truck_trips" && table !== "cargo_trips") return true;
  if (table === "cargo_trips") {
    const kind = String(body && body.kind || "");
    if (!CARGO_KINDS.has(kind) || !body.ownerId) return false;
  }
  const start = num(body && body.startEpochMs);
  const completed = await pool.query(
    `SELECT 1 FROM trip_completions
     WHERE trip_id = $1 AND finished_start_epoch_ms >= $2
     LIMIT 1`,
    [id, start]
  );
  if (completed.rowCount > 0) return false;
  const found = await pool.query(
    "SELECT start_epoch_ms, duration_ms, origin_lat, origin_lng, dest_lat, dest_lng FROM "
      + table + " WHERE id = $1 FOR UPDATE",
    [id]
  );
  if (found.rowCount === 0 && table === "cargo_trips") {
    const vehicleId = body && body.vehicleId ? String(body.vehicleId) : "";
    if (vehicleId) {
      const busy = await pool.query(
        `SELECT 1 FROM cargo_trips
          WHERE owner_id=$1 AND vehicle_id=$2 AND id<>$3
          LIMIT 1`,
        [String(body.ownerId), vehicleId, id]
      );
      if (busy.rowCount > 0) return false;
    }
  }
  if (found.rowCount > 0) {
    // Las fases de un viaje de carga las decide exclusivamente el reloj del
    // servidor. Un camión activo solo admite correcciones de ruta con el mismo
    // inicio; un giro a U explícito puede actualizar su reloj.
    if (table === "cargo_trips") {
      const row = found.rows[0];
      const sameStart = start === num(row.start_epoch_ms);
      if (body && body.geometryOnly === true && sameStart && sameEndpoints(body, row)) {
        return true;
      }
      return body && body.kind === "collect" && sameStart
        && sameCoordinate(body.originLat, row.origin_lat)
        && sameCoordinate(body.originLng, row.origin_lng)
        && num(body.durationMs) >= num(row.duration_ms);
    }
    if (body && body.allowTimingReset === true) {
      return start >= num(found.rows[0].start_epoch_ms);
    }
    return start === num(found.rows[0].start_epoch_ms)
      && sameEndpoints(body, found.rows[0]);
  }
  if (table === "cargo_trips" && body && body.kind === "order" && body.orderId) {
    const order = await pool.query(
      "SELECT * FROM honey_orders WHERE id = $1",
      [body.orderId]
    );
    if (order.rowCount === 0 || !publishedOrderMatches(body, order.rows[0], Date.now())) {
      return false;
    }
  }
  if (body && body.geometryOnly === true) return false;
  const blocked = await pool.query(
    `SELECT 1 FROM trip_effects
     WHERE payload->>'tripId' = $1
       AND COALESCE((payload->>'finishedStartEpochMs')::bigint, 0) >= $2
     UNION ALL
     SELECT 1 FROM trip_completions
     WHERE trip_id = $1 AND finished_start_epoch_ms >= $2
     LIMIT 1`,
    [id, start]
  );
  return blocked.rowCount === 0;
}

function foldName(raw) {
  return String(raw || "")
    .trim()
    .toLowerCase()
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/[^a-z0-9]+/g, "");
}

async function requireAdmin(pool, uid) {
  if (!uid) return false;
  const row = await pool.query("SELECT player_name FROM players WHERE id = $1", [uid]);
  return row.rowCount > 0 && foldName(row.rows[0].player_name) === "aleix";
}

function headingHome(row) {
  if (!row) return false;
  if (row.phase === "return") return true;
  if (row.kind === "delivery") return true;
  return row.kind === "collect" && collectArrivesHome(row);
}

function forceDue(row) {
  row.duration_ms = 0;
  row.start_epoch_ms = Date.now();
}

function summarizeCargo(row) {
  return {
    id: row.id,
    ownerId: row.owner_id,
    playerName: row.player_name || "",
    kind: row.kind,
    phase: row.phase,
    legRole: row.leg_role || "",
    origin: row.origin_label || "",
    dest: row.dest_label || "",
    returnLabel: row.return_label || "",
    flora: row.flora_key || "",
    kg: num(row.kg),
  };
}

function summarizeHive(row) {
  return {
    id: row.id,
    ownerId: row.owner_id,
    playerName: row.player_name || "",
    kind: "hive",
    phase: "out",
    legRole: "",
    origin: "Colmenar",
    dest: row.dest_hex_id || "Destino",
    returnLabel: "",
    flora: row.dest_flora || "",
    kg: 0,
  };
}

async function listLive(pool) {
  const cargos = await pool.query(
    `SELECT c.*, p.player_name
       FROM cargo_trips c
       LEFT JOIN players p ON p.id = c.owner_id
      ORDER BY c.start_epoch_ms`
  );
  const trucks = await pool.query(
    `SELECT t.*, p.player_name
       FROM truck_trips t
       LEFT JOIN players p ON p.id = t.owner_id
      ORDER BY t.start_epoch_ms`
  );
  return {
    ok: true,
    trips: cargos.rows.map(summarizeCargo).concat(trucks.rows.map(summarizeHive)),
  };
}

/**
 * Adelanta el trabajo que queda (venta, recogida, entregas) y deja el camión
 * de vuelta al almacén. Si ya iba de vuelta, llega ahora.
 */
async function finishEarly(pool, tripId) {
  const client = await pool.connect();
  try {
    await client.query("BEGIN");
    const cargo = await client.query(
      "SELECT * FROM cargo_trips WHERE id = $1 FOR UPDATE",
      [tripId]
    );
    if (cargo.rowCount === 0) {
      const truck = await client.query(
        "SELECT * FROM truck_trips WHERE id = $1 FOR UPDATE",
        [tripId]
      );
      if (truck.rowCount === 0) {
        await client.query("ROLLBACK");
        return { ok: false, status: 404, error: "NOT_FOUND" };
      }
      await arriveTruck(client, truck.rows[0]);
      await client.query("COMMIT");
      return { ok: true, finished: true };
    }
    const first = cargo.rows[0];
    if (headingHome(first)) {
      forceDue(first);
      await advanceCargo(client, first);
      await client.query("COMMIT");
      return { ok: true, finished: true };
    }
    for (let step = 0; step < 40; step += 1) {
      const current = await client.query(
        "SELECT * FROM cargo_trips WHERE id = $1 FOR UPDATE",
        [tripId]
      );
      if (current.rowCount === 0) {
        await client.query("COMMIT");
        return { ok: true, finished: true };
      }
      const row = current.rows[0];
      if (headingHome(row)) {
        await client.query("COMMIT");
        return { ok: true, returning: true };
      }
      forceDue(row);
      await advanceCargo(client, row);
    }
    await client.query("COMMIT");
    return { ok: true, returning: true };
  } catch (err) {
    await client.query("ROLLBACK");
    throw err;
  } finally {
    client.release();
  }
}

function start(pool) {
  const run = () => {
    tick(pool).catch((err) => {
      console.error("reloj de viajes:", err.message);
    });
  };
  run();
  const timer = setInterval(run, 2000);
  if (typeof timer.unref === "function") timer.unref();
}

module.exports = {
  tick,
  start,
  clientMayOverwrite,
  haversineKm,
  durationMs,
  requireAdmin,
  listLive,
  finishEarly,
};
