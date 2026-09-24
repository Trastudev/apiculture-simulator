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
    && hasFiniteNumber(row.expire_epoch_ms)
    && num(row.expire_epoch_ms) > when
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
    && hasFiniteNumber(row.expire_epoch_ms)
    && num(row.expire_epoch_ms) > now
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
  await saveCargo(client, trip);
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
    "SELECT start_epoch_ms, origin_lat, origin_lng, dest_lat, dest_lng FROM "
      + table + " WHERE id = $1 FOR UPDATE",
    [id]
  );
  if (found.rowCount > 0) {
    // Las fases de un viaje de carga las decide exclusivamente el reloj del
    // servidor. Un camión activo solo admite correcciones de ruta con el mismo
    // inicio; un giro a U explícito puede actualizar su reloj.
    if (table === "cargo_trips") {
      return body && body.geometryOnly === true
        && start === num(found.rows[0].start_epoch_ms)
        && sameEndpoints(body, found.rows[0]);
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

module.exports = { tick, start, clientMayOverwrite, haversineKm, durationMs };
