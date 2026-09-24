"use strict";

const { clientMayOverwrite } = require("./tripClock");
const { decode } = require("./polyline");

function field(jsonKey, column, type) {
  return { jsonKey, column, type };
}

function isTripTable(table) {
  return table === "truck_trips" || table === "cargo_trips";
}

function isAuthoritativeOfferTable(def) {
  return def && (def.table === "honey_orders" || def.table === "pollination_offers");
}

const TABLES = [
  {
    route: "hex-parcels",
    table: "hex_parcels",
    ownerColumn: "owner_id",
    fields: [
      field("hexId", "hex_id", "text"),
      field("ownerId", "owner_id", "text"),
      field("siteId", "site_id", "text"),
      field("parcelName", "parcel_name", "text"),
      field("forageDayKey", "forage_day_key", "int"),
      field("forageSnapshotJson", "forage_snapshot_json", "text"),
      field("isPrimary", "is_primary", "bool"),
      field("hasWarehouse", "has_warehouse", "bool"),
      field("warehouseLevel", "warehouse_level", "int"),
      field("siteLat", "site_lat", "num"),
      field("siteLng", "site_lng", "num"),
      field("warehouseLat", "warehouse_lat", "num"),
      field("warehouseLng", "warehouse_lng", "num"),
    ],
  },
  {
    route: "hex-flora",
    table: "hex_parcel_flora",
    fields: [
      field("hexId", "hex_id", "text"),
      field("floraKey", "flora_key", "text"),
      field("plantedAtEpochMs", "planted_at_epoch_ms", "int"),
      field("readyAtEpochMs", "ready_at_epoch_ms", "int"),
      field("expireAtDayKey", "expire_at_day_key", "int"),
      field("lastMaintainedYear", "last_maintained_year", "int"),
    ],
  },
  {
    route: "hive-yields",
    table: "hive_daily_yields",
    fields: [
      field("hiveId", "hive_id", "text"),
      field("dayKey", "day_key", "int"),
      field("kg", "kg", "num"),
      field("workerNetDelta", "worker_net_delta", "int"),
      field("eggsLaid", "eggs_laid", "int"),
      field("consumptionKg", "consumption_kg", "num"),
      field("forageKg", "forage_kg", "num"),
    ],
  },
  {
    route: "production-states",
    table: "production_states",
    fields: [
      field("gameStartDayKey", "game_start_day_key", "int"),
      field("lastProcessedProductionDayKey", "last_processed_production_day_key", "int"),
      field("gameRealTimeAnchorEpochMs", "game_real_time_anchor_epoch_ms", "int"),
    ],
  },
  {
    route: "honey-orders",
    table: "honey_orders",
    ownerColumn: "claimed_by",
    fields: [
      field("npcName", "npc_name", "text"),
      field("portraitIndex", "portrait_index", "int"),
      field("floraKey", "flora_key", "text"),
      field("kg", "kg", "num"),
      field("unitPrice", "unit_price", "num"),
      field("destHexId", "dest_hex_id", "text"),
      field("destLat", "dest_lat", "num"),
      field("destLng", "dest_lng", "num"),
      field("destLabel", "dest_label", "text"),
      field("region", "region", "text"),
      field("createdDayKey", "created_day_key", "int"),
      field("expireEpochMs", "expire_epoch_ms", "int"),
      field("taken", "taken", "bool"),
      field("claimedBy", "claimed_by", "text"),
      field("band", "band", "int"),
    ],
  },
  {
    route: "pollination-offers",
    table: "pollination_offers",
    fields: [
      field("hexId", "hex_id", "text"),
      field("flora", "flora", "text"),
      field("startDoy", "start_doy", "int"),
      field("endDoy", "end_doy", "int"),
      field("band", "band", "int"),
      field("region", "region", "text"),
      field("createdDayKey", "created_day_key", "int"),
      field("expireEpochMs", "expire_epoch_ms", "int"),
      field("destLat", "dest_lat", "num"),
      field("destLng", "dest_lng", "num"),
      field("npcName", "npc_name", "text"),
      field("portraitIndex", "portrait_index", "int"),
      field("taken", "taken", "bool"),
      field("claimedBy", "claimed_by", "text"),
    ],
  },
  {
    route: "pollination-contracts",
    table: "pollination_contracts",
    ownerColumn: "owner_id",
    fields: [
      field("ownerId", "owner_id", "text"),
      field("hexId", "hex_id", "text"),
      field("flora", "flora", "text"),
      field("npcName", "npc_name", "text"),
      field("estateName", "estate_name", "text"),
      field("region", "region", "text"),
      field("climateZone", "climate_zone", "text"),
      field("layer", "layer", "int"),
      field("status", "status", "text"),
      field("collectedKg", "collected_kg", "num"),
      field("poolKg", "pool_kg", "num"),
      field("sawPeak", "saw_peak", "bool"),
      field("minPct", "min_pct", "num"),
      field("payB", "pay_b", "int"),
      field("extraBPerPoint", "extra_b_per_point", "int"),
      field("travelCostPaid", "travel_cost_paid", "int"),
      field("acceptedDayKey", "accepted_day_key", "int"),
      field("workDays", "work_days", "int"),
      field("dueDayKey", "due_day_key", "int"),
      field("startDoy", "start_doy", "int"),
      field("hiveIdsJson", "hive_ids_json", "text"),
    ],
  },
  {
    route: "truck-trips",
    table: "truck_trips",
    ownerColumn: "owner_id",
    fields: [
      field("ownerId", "owner_id", "text"),
      field("originLat", "origin_lat", "num"),
      field("originLng", "origin_lng", "num"),
      field("destLat", "dest_lat", "num"),
      field("destLng", "dest_lng", "num"),
      field("destHexId", "dest_hex_id", "text"),
      field("destFlora", "dest_flora", "text"),
      field("startEpochMs", "start_epoch_ms", "int"),
      field("durationMs", "duration_ms", "int"),
      field("routePolyline", "route_polyline", "text"),
      field("routeRoadKinds", "route_road_kinds", "text"),
    ],
  },
  {
    route: "cargo-trips",
    table: "cargo_trips",
    ownerColumn: "owner_id",
    fields: [
      field("ownerId", "owner_id", "text"),
      field("kind", "kind", "text"),
      field("phase", "phase", "text"),
      field("floraKey", "flora_key", "text"),
      field("kg", "kg", "num"),
      field("cargoJson", "cargo_json", "text"),
      field("originLat", "origin_lat", "num"),
      field("originLng", "origin_lng", "num"),
      field("destLat", "dest_lat", "num"),
      field("destLng", "dest_lng", "num"),
      field("originLabel", "origin_label", "text"),
      field("destLabel", "dest_label", "text"),
      field("originHexId", "origin_hex_id", "text"),
      field("destHexId", "dest_hex_id", "text"),
      field("returnLat", "return_lat", "num"),
      field("returnLng", "return_lng", "num"),
      field("returnLabel", "return_label", "text"),
      field("returnHexId", "return_hex_id", "text"),
      field("unitPrice", "unit_price", "num"),
      field("npcName", "npc_name", "text"),
      field("hiveId", "hive_id", "text"),
      field("startEpochMs", "start_epoch_ms", "int"),
      field("durationMs", "duration_ms", "int"),
      field("routePolyline", "route_polyline", "text"),
      field("routeRoadKinds", "route_road_kinds", "text"),
      field("orderId", "order_id", "text"),
      field("legRole", "leg_role", "text"),
      field("vehicleId", "vehicle_id", "text"),
      field("shipmentId", "shipment_id", "text"),
      field("chainLat", "chain_lat", "num"),
      field("chainLng", "chain_lng", "num"),
      field("chainLabel", "chain_label", "text"),
      field("chainHexId", "chain_hex_id", "text"),
      field("priceLocked", "price_locked", "int"),
    ],
  },
  {
    route: "leaderboard",
    table: "leaderboard_entries",
    fields: [
      field("playerName", "player_name", "text"),
      field("nickname", "nickname", "text"),
      field("honeyBrand", "honey_brand", "text"),
      field("level", "level", "int"),
      field("xp", "xp", "num"),
      field("honeyStockKg", "honey_stock_kg", "num"),
      field("totalHoneyKg", "total_honey_kg", "num"),
      field("honeySoldKgTotal", "honey_sold_kg_total", "num"),
      field("honeySoldKgByFlora", "honey_sold_kg_by_flora", "text"),
      field("hiveCount", "hive_count", "int"),
      field("hivesIberia", "hives_iberia", "int"),
      field("hivesZa", "hives_za", "int"),
      field("hivesMdg", "hives_mdg", "int"),
      field("adultBeeCount", "adult_bee_count", "int"),
      field("mapRegion", "map_region", "text"),
    ],
  },
  {
    route: "unique-names",
    table: "unique_names",
    ownerColumn: "owner_id",
    fields: [
      field("kind", "kind", "text"),
      field("nameKey", "name_key", "text"),
      field("ownerId", "owner_id", "text"),
    ],
  },
  {
    route: "global-events",
    table: "global_events",
    fields: [
      field("status", "status", "text"),
      field("body", "body", "json"),
    ],
  },
  {
    route: "event-progress",
    table: "global_event_progress",
    fields: [field("body", "body", "json")],
  },
  {
    route: "event-participants",
    table: "event_participants",
    ownerColumn: "owner_id",
    fields: [
      field("progressId", "progress_id", "text"),
      field("ownerId", "owner_id", "text"),
      field("kg", "kg", "num"),
      field("body", "body", "json"),
    ],
  },
  {
    route: "event-claims",
    table: "event_claims",
    ownerColumn: "owner_id",
    fields: [
      field("ownerId", "owner_id", "text"),
      field("instanceId", "instance_id", "text"),
      field("body", "body", "json"),
    ],
  },
  {
    route: "market-sales",
    table: "market_flora_sales",
    fields: [
      field("dayKey", "day_key", "int"),
      field("floraKey", "flora_key", "text"),
      field("kgSold", "kg_sold", "num"),
    ],
  },
  {
    route: "player-stores",
    table: "player_stores",
    ownerColumn: "owner_id",
    fields: [
      field("ownerId", "owner_id", "text"),
      field("kind", "kind", "text"),
      field("body", "body", "json"),
    ],
  },
  {
    route: "locations",
    table: "locations",
    fields: [
      field("label", "label", "text"),
      field("lat", "lat", "num"),
      field("lng", "lng", "num"),
      field("floraType", "flora_type", "text"),
      field("virtualized", "virtualized", "bool"),
    ],
  },
  {
    route: "honey-batches",
    table: "honey_batches",
    fields: [
      field("hiveId", "hive_id", "text"),
      field("type", "type", "text"),
      field("quantityKg", "quantity_kg", "num"),
      field("unitPrice", "unit_price", "num"),
      field("createdAt", "created_at", "int"),
    ],
  },
  {
    route: "game-events",
    table: "game_events",
    fields: [
      field("hiveId", "hive_id", "text"),
      field("type", "type", "text"),
      field("description", "description", "text"),
      field("timestampMs", "timestamp_ms", "int"),
      field("impactValue", "impact_value", "int"),
    ],
  },
];

const BY_ROUTE = new Map(TABLES.map((item) => [item.route, item]));

function emptyValue(type) {
  if (type === "bool") return false;
  if (type === "json") return {};
  if (type === "text") return null;
  return 0;
}

function coerce(type, value) {
  if (value == null) return type === "text" ? null : emptyValue(type);
  if (type === "bool") return Boolean(value);
  if (type === "int") return Math.trunc(Number(value));
  if (type === "num") return Number(value);
  if (type === "json") return value;
  return String(value);
}

function recordId(urlPath, route) {
  const prefix = "/" + route + "/";
  if (!urlPath.startsWith(prefix)) return null;
  const id = decodeURIComponent(urlPath.slice(prefix.length));
  if (!id || id.includes("/") || id.length > 240) return null;
  return id;
}

function toJson(def, row) {
  const out = { id: row.id, updatedAt: row.updated_at };
  for (const item of def.fields) {
    const value = row[item.column];
    if (item.type === "int" || item.type === "num") {
      out[item.jsonKey] = value == null ? 0 : Number(value);
    } else if (item.type === "json") {
      out[item.jsonKey] = value == null ? {} : value;
    } else {
      out[item.jsonKey] = value;
    }
  }
  return out;
}

async function readRow(pool, def, id, lock = false) {
  const suffix = lock ? " FOR UPDATE" : "";
  const result = await pool.query("SELECT * FROM " + def.table + " WHERE id = $1" + suffix, [id]);
  return result.rows[0] || null;
}

function hasRoadPolyline(value) {
  if (typeof value !== "string" || value.length === 0) return false;
  try {
    return decode(value).length > 2;
  } catch (err) {
    return false;
  }
}

async function saveRow(pool, def, id, body) {
  const current = await readRow(pool, def, id, isTripTable(def.table));
  if (!current && isTripTable(def.table)) {
    const start = body && Number(body.startEpochMs);
    const blocked = await pool.query(
      `SELECT 1 FROM trip_effects
       WHERE payload->>'tripId' = $1
         AND COALESCE((payload->>'finishedStartEpochMs')::bigint, 0) >= $2
       UNION ALL
       SELECT 1 FROM trip_completions
       WHERE trip_id = $1 AND finished_start_epoch_ms >= $2
       LIMIT 1`,
      [id, Number.isFinite(start) ? start : 0]
    );
    if (blocked.rowCount > 0) return null;
  }
  const values = {};
  for (const item of def.fields) {
    if (body && Object.prototype.hasOwnProperty.call(body, item.jsonKey)) {
      values[item.column] = coerce(item.type, body[item.jsonKey]);
    } else if (current) {
      values[item.column] = current[item.column];
    } else {
      values[item.column] = emptyValue(item.type);
    }
  }
  // El reloj del servidor conserva el inicio y la duración de un viaje ya publicado.
  // El cliente puede corregir la polyline, pero no reiniciar el reloj al abrir la app.
  if (current && def.route === "truck-trips" && !(body && body.allowTimingReset === true)) {
    for (const column of [
      "owner_id", "origin_lat", "origin_lng", "dest_lat", "dest_lng",
      "dest_hex_id", "start_epoch_ms", "duration_ms",
    ]) {
      values[column] = current[column];
    }
    // Una línea recta de dos puntos no debe reemplazar una ruta válida.
    if (!hasRoadPolyline(body && body.routePolyline)) {
      values.route_polyline = current.route_polyline;
      values.route_road_kinds = current.route_road_kinds;
    }
  }
  if (current && def.route === "cargo-trips" && body && body.geometryOnly === true) {
    // Un parche de geometría no puede cambiar la fase, el reloj ni la carga.
    for (const item of def.fields) {
      if (item.column !== "route_polyline" && item.column !== "route_road_kinds") {
        values[item.column] = current[item.column];
      }
    }
    if (!hasRoadPolyline(body.routePolyline)) {
      values.route_polyline = current.route_polyline;
      values.route_road_kinds = current.route_road_kinds;
    }
  }
  const columns = def.fields.map((item) => item.column);
  const placeholders = columns.map((_, index) => "$" + (index + 2));
  const updates = columns.map((column) => column + " = EXCLUDED." + column);
  const params = columns.map((column) => {
    const item = def.fields.find((entry) => entry.column === column);
    return item.type === "json" ? JSON.stringify(values[column]) : values[column];
  });
  const casts = columns.map((column) => {
    const item = def.fields.find((entry) => entry.column === column);
    return item.type === "json" ? "::jsonb" : "";
  });
  const valueSql = placeholders.map((token, index) => token + casts[index]).join(", ");
  const result = await pool.query(
    `INSERT INTO ${def.table} (id, ${columns.join(", ")}, updated_at)
     VALUES ($1, ${valueSql}, now())
     ON CONFLICT (id) DO UPDATE SET ${updates.join(", ")}, updated_at = now()
     RETURNING *`,
    [id, ...params]
  );
  return result.rows[0];
}

async function handleTable(req, res, pool, send, readBody) {
  const urlPath = (req.url || "/").split("?")[0];
  if (req.method === "GET" && urlPath === "/tables") {
    const names = await pool.query(
      `SELECT c.relname AS name
       FROM pg_class c
       JOIN pg_namespace n ON n.oid = c.relnamespace
       WHERE n.nspname = 'public' AND c.relkind = 'r'
       ORDER BY c.relname`
    );
    send(res, 200, names.rows.map((row) => row.name));
    return true;
  }

  let def = null;
  let id = null;
  for (const item of TABLES) {
    if (urlPath === "/" + item.route) {
      def = item;
      break;
    }
    const found = recordId(urlPath, item.route);
    if (found) {
      def = item;
      id = found;
      break;
    }
  }
  if (!def) return false;

  // Las ofertas y comandas las mantiene offerClock; la tabla genérica solo
  // queda disponible para una importación administrativa explícita.
  if (req.method === "PUT" && isAuthoritativeOfferTable(def)
      && process.env.ALLOW_OFFER_IMPORT !== "1") {
    send(res, 409, { ok: false, error: "SERVER_AUTHORITATIVE_OFFERS" });
    return true;
  }

  if (req.method === "GET" && !id) {
    const ownerId = new URL(req.url, "http://localhost").searchParams.get("ownerId");
    if (ownerId && def.ownerColumn) {
      const result = await pool.query(
        "SELECT * FROM " + def.table + " WHERE " + def.ownerColumn + " = $1 ORDER BY updated_at DESC",
        [ownerId]
      );
      send(res, 200, result.rows.map((row) => toJson(def, row)));
      return true;
    }
    const result = await pool.query(
      "SELECT * FROM " + def.table + " ORDER BY updated_at DESC LIMIT 200"
    );
    send(res, 200, result.rows.map((row) => toJson(def, row)));
    return true;
  }

  if (!id) return false;

  if (req.method === "GET") {
    const row = await readRow(pool, def, id);
    if (!row) {
      send(res, 404, { ok: false });
      return true;
    }
    send(res, 200, toJson(def, row));
    return true;
  }

  if (req.method === "PUT") {
    const body = await readBody(req);
    if (body == null || typeof body !== "object" || Array.isArray(body)) {
      send(res, 400, { ok: false });
      return true;
    }
    if (isTripTable(def.table)) {
      const client = await pool.connect();
      try {
        await client.query("BEGIN");
        await client.query("SET TRANSACTION ISOLATION LEVEL SERIALIZABLE");
        if (!(await clientMayOverwrite(client, def.table, id, body))) {
          await client.query("ROLLBACK");
          send(res, 409, { ok: false });
          return true;
        }
        const row = await saveRow(client, def, id, body);
        if (!row) {
          await client.query("ROLLBACK");
          send(res, 409, { ok: false });
          return true;
        }
        await client.query("COMMIT");
        send(res, 200, toJson(def, row));
        return true;
      } catch (err) {
        try {
          await client.query("ROLLBACK");
        } catch (rollbackError) {
          // La conexión ya puede estar cerrada; se conserva el error original.
        }
        if (err && (err.code === "40001" || err.code === "40P01")) {
          send(res, 409, { ok: false });
          return true;
        }
        throw err;
      } finally {
        client.release();
      }
    }
    if (!(await clientMayOverwrite(pool, def.table, id, body))) {
      send(res, 409, { ok: false });
      return true;
    }
    const row = await saveRow(pool, def, id, body);
    send(res, 200, toJson(def, row));
    return true;
  }

  return false;
}

module.exports = { handleTable, BY_ROUTE, saveRow, TABLES };
