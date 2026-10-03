"use strict";

const fs = require("fs");
const http = require("http");
const path = require("path");
const { Pool } = require("pg");
const { handleHive } = require("./hives");
const { handleTable } = require("./tables");
const tripClock = require("./tripClock");
const offerClock = require("./offerClock");
const productionClock = require("./productionClock");
const actions = require("./actions");
const auth = require("./auth");
const playerReset = require("./playerReset");
const legal = require("./legal");
const ranking = require("./ranking");
const mail = require("./mail");

const apiToken = process.env.API_TOKEN || "";

const port = Number(process.env.PORT || 8080);
const host = process.env.HOST || "127.0.0.1";

const pool = new Pool({
  host: process.env.PGHOST || "127.0.0.1",
  port: Number(process.env.PGPORT || 5432),
  database: process.env.PGDATABASE || "apiculture",
  user: process.env.PGUSER || "apiculture",
  password: process.env.PGPASSWORD,
  idleTimeoutMillis: 30_000,
});

// Sin este listener, una conexión ociosa que Postgres cierra de madrugada
// mata el proceso entero y Caddy se queda en 502.
pool.on("error", (err) => {
  console.error("conexión ociosa con la base de datos:", err.message);
});

const COLUMNS = {
  playerName: "player_name",
  honeyBrand: "honey_brand",
  profileComplete: "profile_complete",
  timeZoneId: "time_zone_id",
  economyBalanceEur: "economy_balance_eur",
  economyHoneyBucketsJson: "economy_honey_buckets_json",
  economyHoneySoldKgTotal: "economy_honey_sold_kg_total",
  economyHoneySoldByFloraJson: "economy_honey_sold_by_flora_json",
  playerLevel: "player_level",
  playerXp: "player_xp",
};

function send(res, status, body) {
  res.writeHead(status, { "content-type": "application/json; charset=utf-8" });
  res.end(JSON.stringify(body));
}

function sendHtml(res, html, cache) {
  res.writeHead(200, {
    "content-type": "text/html; charset=utf-8",
    "cache-control": cache,
    "x-content-type-options": "nosniff",
  });
  res.end(html);
}

function playerIdFromPath(urlPath) {
  const match = /^\/players\/([^/]+)$/.exec(urlPath);
  if (!match) return null;
  const id = decodeURIComponent(match[1]);
  if (!/^[A-Za-z0-9_-]{1,128}$/.test(id)) return null;
  return id;
}

function readBody(req) {
  return new Promise((resolve, reject) => {
    const chunks = [];
    let size = 0;
    req.on("data", (chunk) => {
      size += chunk.length;
      if (size > 1_000_000) {
        reject(Object.assign(new Error("body"), { status: 413 }));
        req.destroy();
        return;
      }
      chunks.push(chunk);
    });
    req.on("end", () => {
      if (chunks.length === 0) {
        resolve({});
        return;
      }
      try {
        resolve(JSON.parse(Buffer.concat(chunks).toString("utf8")));
      } catch (err) {
        reject(Object.assign(new Error("json"), { status: 400 }));
      }
    });
    req.on("error", reject);
  });
}

function rowToPlayer(row) {
  return {
    id: row.id,
    playerName: row.player_name,
    honeyBrand: row.honey_brand,
    profileComplete: row.profile_complete,
    timeZoneId: row.time_zone_id,
    economyBalanceEur: Number(row.economy_balance_eur),
    economyHoneyBucketsJson: row.economy_honey_buckets_json,
    economyHoneySoldKgTotal: Number(row.economy_honey_sold_kg_total),
    economyHoneySoldByFloraJson: row.economy_honey_sold_by_flora_json,
    playerLevel: row.player_level,
    playerXp: Number(row.player_xp),
    honeyStockSeq: Number(row.honey_stock_seq) || 0,
    gameLocale: row.game_locale || "",
    updatedAt: row.updated_at,
  };
}

async function migrate() {
  const client = await pool.connect();
  try {
    // Una sola conexión mantiene el lock durante toda la comprobación/aplicación
    // para que dos réplicas no ejecuten la misma migración a la vez.
    await client.query("SELECT pg_advisory_lock(90210)");
    await client.query(`
      CREATE TABLE IF NOT EXISTS schema_migrations (
        id text PRIMARY KEY,
        applied_at timestamptz NOT NULL DEFAULT now()
      )
    `);
    const dir = path.join(__dirname, "..", "sql");
    const files = fs.readdirSync(dir).filter((name) => name.endsWith(".sql")).sort();
    for (const file of files) {
      const done = await client.query("SELECT 1 FROM schema_migrations WHERE id = $1", [file]);
      if (done.rowCount > 0) continue;
      const sql = fs.readFileSync(path.join(dir, file), "utf8");
      const statements = sql.split(";").map((part) => part.trim()).filter(Boolean);
      await client.query("BEGIN");
      try {
        for (const statement of statements) {
          await client.query(statement);
        }
        await client.query("INSERT INTO schema_migrations (id) VALUES ($1)", [file]);
        await client.query("COMMIT");
        console.log("migración aplicada:", file);
      } catch (err) {
        await client.query("ROLLBACK");
        throw err;
      }
    }
  } finally {
    try { await client.query("SELECT pg_advisory_unlock(90210)"); } catch (_) { /* closed */ }
    client.release();
  }
}

async function getPlayer(id) {
  const result = await pool.query("SELECT * FROM players WHERE id = $1", [id]);
  return result.rows[0] || null;
}

async function savePlayer(id, body) {
  const current = await getPlayer(id);
  const values = {
    player_name: current ? current.player_name : null,
    honey_brand: current ? current.honey_brand : null,
    profile_complete: current ? current.profile_complete : false,
    time_zone_id: current ? current.time_zone_id : null,
    economy_balance_eur: current ? current.economy_balance_eur : 20000,
    economy_honey_buckets_json: current ? current.economy_honey_buckets_json : null,
    economy_honey_sold_kg_total: current ? current.economy_honey_sold_kg_total : 0,
    economy_honey_sold_by_flora_json: current ? current.economy_honey_sold_by_flora_json : null,
    player_level: current ? current.player_level : 0,
    player_xp: current ? current.player_xp : 0,
    honey_stock_seq: current ? Number(current.honey_stock_seq) || 0 : 0,
  };
  for (const [jsonKey, column] of Object.entries(COLUMNS)) {
    if (Object.prototype.hasOwnProperty.call(body, jsonKey)) {
      values[column] = body[jsonKey];
    }
  }
  // La recogida suma miel en el servidor y sube honey_stock_seq. Un teléfono
  // con la cartera de antes no puede volver a dejar el almacén vacío.
  const serverSeq = current ? Number(current.honey_stock_seq) || 0 : 0;
  const clientSeq = Object.prototype.hasOwnProperty.call(body, "honeyStockSeq")
    ? Number(body.honeyStockSeq)
    : null;
  const stale = current && (
    (clientSeq == null && serverSeq > 0)
    || (clientSeq != null && !(clientSeq >= serverSeq))
  );
  if (stale) {
    values.economy_honey_buckets_json = current.economy_honey_buckets_json;
  }
  values.honey_stock_seq = serverSeq;
  const gameLocale = Object.prototype.hasOwnProperty.call(body, "gameLocale")
    ? String(body.gameLocale || "")
    : (current && current.game_locale) || "";
  const result = await pool.query(
    `INSERT INTO players (
        id, player_name, honey_brand, profile_complete, time_zone_id,
        economy_balance_eur, economy_honey_buckets_json, economy_honey_sold_kg_total,
        economy_honey_sold_by_flora_json, player_level, player_xp, honey_stock_seq,
        game_locale, updated_at
      ) VALUES (
        $1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13, now()
      )
      ON CONFLICT (id) DO UPDATE SET
        player_name = EXCLUDED.player_name,
        honey_brand = EXCLUDED.honey_brand,
        profile_complete = EXCLUDED.profile_complete,
        time_zone_id = EXCLUDED.time_zone_id,
        economy_balance_eur = EXCLUDED.economy_balance_eur,
        economy_honey_buckets_json = EXCLUDED.economy_honey_buckets_json,
        economy_honey_sold_kg_total = EXCLUDED.economy_honey_sold_kg_total,
        economy_honey_sold_by_flora_json = EXCLUDED.economy_honey_sold_by_flora_json,
        player_level = EXCLUDED.player_level,
        player_xp = EXCLUDED.player_xp,
        honey_stock_seq = EXCLUDED.honey_stock_seq,
        game_locale = EXCLUDED.game_locale,
        updated_at = now()
      RETURNING *`,
    [
      id,
      values.player_name,
      values.honey_brand,
      values.profile_complete,
      values.time_zone_id,
      values.economy_balance_eur,
      values.economy_honey_buckets_json,
      values.economy_honey_sold_kg_total,
      values.economy_honey_sold_by_flora_json,
      values.player_level,
      values.player_xp,
      values.honey_stock_seq,
      gameLocale,
    ]
  );
  return result.rows[0];
}

async function authorized(req) {
  const firebaseUid = await auth.verify(req);
  if (firebaseUid) {
    req.authUid = firebaseUid;
    return true;
  }
  if (auth.isConfigured()) return false;
  if (!apiToken) return false;
  if (req.headers.authorization === "Bearer " + apiToken) {
    req.legacyAdmin = true;
    return true;
  }
  return false;
}

function ownerAllowed(req, ownerId) {
  return !req.authUid || String(ownerId || "") === String(req.authUid);
}

const server = http.createServer(async (req, res) => {
  const urlPath = (req.url || "/").split("?")[0];
  try {
    if (req.method === "GET" && (urlPath === "/privacy" || urlPath === "/privacy/")) {
      sendHtml(res, legal.privacyHtml(), "public, max-age=3600");
      return;
    }

    if (req.method === "GET" && (urlPath === "/delete-account" || urlPath === "/account-deletion")) {
      sendHtml(res, legal.deleteHtml(auth.webClientId()), "no-store");
      return;
    }

    if (req.method === "POST" && urlPath === "/account/delete") {
      const body = await readBody(req);
      const headerToken = req.headers["x-google-id-token"] || req.headers["x-firebase-id-token"];
      const token = headerToken || (body && body.credential);
      const uid = await auth.verifyToken(token);
      if (!uid) {
        send(res, 401, { ok: false, error: "AUTH" });
        return;
      }
      await playerReset.deleteAccount(pool, uid);
      send(res, 200, { ok: true });
      return;
    }

    if (req.method === "GET" && urlPath === "/health") {
      const result = await pool.query(
        "SELECT current_database() AS database, now() AS time"
      );
      const row = result.rows[0];
      send(res, 200, { ok: true, database: row.database, time: row.time });
      return;
    }

    if (!(await authorized(req))) {
      send(res, 401, { ok: false });
      return;
    }

    if (req.method === "GET" && urlPath === "/production-reports") {
      const params = new URL(req.url, "http://localhost").searchParams;
      const requested = params.get("ownerId") || "";
      if (req.authUid && requested && requested !== req.authUid) {
        send(res, 403, { ok: false, error: "OWNER_MISMATCH" });
        return;
      }
      const ownerId = req.authUid || requested;
      const days = await productionClock.reportsSince(pool, ownerId, params.get("since"));
      send(res, 200, { ok: true, days });
      return;
    }

    if (req.method === "POST" && urlPath === "/player-reset") {
      const body = await readBody(req);
      if (body && body.all === true) {
        const allowed = req.legacyAdmin || await playerReset.isAleix(pool, req.authUid);
        if (!allowed) {
          send(res, 403, { ok: false, error: "NOT_ADMIN" });
          return;
        }
        await playerReset.wipeEveryone(pool);
        send(res, 200, { ok: true, all: true });
        return;
      }
      const requested = body && body.ownerId ? String(body.ownerId) : "";
      if (req.authUid && requested && requested !== req.authUid && !req.legacyAdmin) {
        send(res, 403, { ok: false, error: "OWNER_MISMATCH" });
        return;
      }
      const ownerId = req.authUid || requested;
      if (!ownerId) {
        send(res, 400, { ok: false });
        return;
      }
      await playerReset.wipeOwner(pool, ownerId);
      send(res, 200, { ok: true });
      return;
    }

    if (req.method === "POST" && urlPath === "/actions") {
      const body = await readBody(req);
      const requested = body && body.ownerId ? String(body.ownerId) : "";
      if (req.authUid && requested && requested !== req.authUid) {
        send(res, 403, { ok: false, error: "OWNER_MISMATCH" });
        return;
      }
      const result = await actions.handle(pool, body, req.authUid || requested);
      send(res, result.ok ? 200 : (result.status || 409), result);
      return;
    }

    if (req.method === "POST" && urlPath === "/production-clock/run") {
      const body = await readBody(req);
      const requested = body && body.ownerId ? String(body.ownerId) : "";
      if (req.authUid && requested && requested !== req.authUid) {
        send(res, 403, { ok: false, error: "OWNER_MISMATCH" });
        return;
      }
      const ownerId = req.authUid || requested;
      const checkpointDay = body && Number(body.checkpointDayKey) > 0
        ? Number(body.checkpointDayKey) : 0;
      const result = await productionClock.tickOwner(
        pool,
        ownerId,
        Date.now(),
        body && body.timeZoneId ? String(body.timeZoneId) : "",
        checkpointDay > 0 ? { throughDayKey: checkpointDay, hives: body.hives || [] } : null
      );
      const clientDay = body && Number(body.clientDayKey) > 0 ? Number(body.clientDayKey) : 0;
      if (result.ok && clientDay > 0 && Number(result.settledDayKey) > clientDay
          && (!Array.isArray(result.days) || result.days.length === 0)) {
        result.days = await productionClock.reportsSince(pool, ownerId, clientDay);
      }
      console.log("production-clock", JSON.stringify({
        ownerTail: String(ownerId || "").slice(-6),
        clientDay,
        ok: result.ok === true,
        settled: result.settled === true,
        settledDayKey: result.settledDayKey || 0,
        days: Array.isArray(result.days) ? result.days.length : 0,
        error: result.error || "",
      }));
      send(res, result.ok ? 200 : 404, result);
      return;
    }

    if (req.method === "GET" && urlPath === "/admin/live-trips") {
      if (!(await tripClock.requireAdmin(pool, req.authUid))) {
        send(res, 403, { ok: false, error: "NOT_ADMIN" });
        return;
      }
      send(res, 200, await tripClock.listLive(pool));
      return;
    }

    if (req.method === "POST" && urlPath === "/admin/live-trips/finish") {
      if (!(await tripClock.requireAdmin(pool, req.authUid))) {
        send(res, 403, { ok: false, error: "NOT_ADMIN" });
        return;
      }
      const body = await readBody(req);
      const tripId = body && body.id ? String(body.id) : "";
      if (!tripId || tripId.length > 240) {
        send(res, 400, { ok: false, error: "TRIP_REQUIRED" });
        return;
      }
      const result = await tripClock.finishEarly(pool, tripId);
      send(res, result.ok ? 200 : (result.status || 409), result);
      return;
    }

    if (req.method === "POST" && urlPath === "/trip-clock/run") {
      await tripClock.tick(pool);
      send(res, 200, { ok: true });
      return;
    }

    if (req.method === "POST" && urlPath === "/offer-clock/run") {
      const result = await offerClock.tick(pool);
      send(res, 200, result);
      return;
    }

    if (req.method === "GET" && urlPath === "/offer-snapshot") {
      const params = new URL(req.url, "http://localhost").searchParams;
      const region = params.get("region");
      const ownerId = req.authUid || params.get("ownerId");
      const nearLat = Number(params.get("nearLat"));
      const nearLng = Number(params.get("nearLng"));
      const nearKm = Number(params.get("nearKm"));
      const near = Number.isFinite(nearLat) && Number.isFinite(nearLng) && nearKm > 0
        ? { lat: nearLat, lng: nearLng, km: nearKm }
        : null;
      const result = await offerClock.snapshot(pool, region, ownerId, near);
      send(res, 200, result);
      return;
    }

    if (req.method === "GET" && urlPath === "/ranking") {
      const params = new URL(req.url, "http://localhost").searchParams;
      const rows = await ranking.list(pool, {
        metric: params.get("metric"),
        region: params.get("region"),
        flora: params.get("flora"),
        selfId: req.authUid || "",
      });
      send(res, 200, { rows });
      return;
    }

    if (req.method === "PUT" && urlPath === "/ranking/me") {
      if (!req.authUid) {
        send(res, 403, { ok: false, error: "OWNER_REQUIRED" });
        return;
      }
      const body = await readBody(req);
      await ranking.publish(pool, req.authUid, body);
      send(res, 200, { ok: true });
      return;
    }

    if (req.method === "POST" && urlPath === "/offer-actions") {
      const body = await readBody(req);
      const result = await offerClock.action(pool, body, req.authUid || null);
      if (result.ok) {
        // La reposición puede recorrer el mapa completo; no bloquea la
        // confirmación de claim/oferta que está esperando la app.
        setImmediate(() => {
          offerClock.tick(pool).catch((err) => console.error("reloj de ofertas:", err.message));
        });
      }
      send(res, result.ok ? 200 : 409, result);
      return;
    }

    if (req.method === "GET" && urlPath === "/trip-effects") {
      const requestedOwner = new URL(req.url, "http://localhost").searchParams.get("ownerId");
      if (req.authUid && requestedOwner && !ownerAllowed(req, requestedOwner)) {
        send(res, 403, { ok: false, error: "OWNER_MISMATCH" });
        return;
      }
      const ownerId = req.authUid || requestedOwner;
      const result = ownerId
        ? await pool.query(
          "SELECT id, owner_id, payload, created_at FROM trip_effects WHERE owner_id = $1 ORDER BY created_at",
          [ownerId]
        )
        : await pool.query(
          "SELECT id, owner_id, payload, created_at FROM trip_effects ORDER BY created_at LIMIT 200"
        );
      send(res, 200, result.rows.map((row) => ({
        id: row.id,
        ownerId: row.owner_id,
        payload: row.payload,
        createdAt: row.created_at,
      })));
      return;
    }

    const effectId = /^\/trip-effects\/([^/]+)$/.exec(urlPath);
    if (effectId && req.method === "DELETE") {
      const id = decodeURIComponent(effectId[1]);
      if (req.authUid) {
        const owner = await pool.query("SELECT owner_id FROM trip_effects WHERE id = $1", [id]);
        if (owner.rowCount === 0) {
          send(res, 404, { ok: false });
          return;
        }
        if (!ownerAllowed(req, owner.rows[0].owner_id)) {
          send(res, 403, { ok: false, error: "OWNER_MISMATCH" });
          return;
        }
      }
      await pool.query("DELETE FROM trip_effects WHERE id = $1", [id]);
      send(res, 200, { ok: true });
      return;
    }

    if (req.method === "GET" && urlPath === "/players") {
      const allowed = req.legacyAdmin || await playerReset.isAleix(pool, req.authUid);
      if (!allowed) {
        send(res, 403, { ok: false, error: "NOT_ADMIN" });
        return;
      }
      const listed = await pool.query(
        "SELECT id, player_name, honey_brand FROM players ORDER BY player_name LIMIT 800"
      );
      send(res, 200, listed.rows.map((row) => ({
        id: row.id,
        playerName: row.player_name,
        honeyBrand: row.honey_brand,
      })));
      return;
    }

    if (req.method === "GET" && urlPath.startsWith("/player-cards/")) {
      const cardId = decodeURIComponent(urlPath.slice("/player-cards/".length));
      if (!/^[A-Za-z0-9_-]{1,128}$/.test(cardId)) {
        send(res, 400, { ok: false });
        return;
      }
      const card = await getPlayer(cardId);
      if (!card) {
        send(res, 404, { ok: false });
        return;
      }
      send(res, 200, {
        id: card.id,
        playerName: card.player_name,
        honeyBrand: card.honey_brand,
      });
      return;
    }

    if (req.method === "GET" && urlPath === "/map-apiaries") {
      const pins = await pool.query(
        `SELECT h.id, h.hex_id, h.owner_id, h.site_id, h.parcel_name, h.site_lat, h.site_lng,
                h.has_warehouse, h.warehouse_level, h.warehouse_lat, h.warehouse_lng, h.is_primary,
                p.player_name
         FROM hex_parcels h
         LEFT JOIN players p ON p.id = h.owner_id
         WHERE h.owner_id IS NOT NULL AND h.hex_id IS NOT NULL`
      );
      send(res, 200, pins.rows.map((row) => ({
        id: row.id,
        hexId: row.hex_id,
        ownerId: row.owner_id,
        siteId: row.site_id,
        parcelName: row.parcel_name,
        playerName: row.player_name,
        siteLat: Number(row.site_lat) || 0,
        siteLng: Number(row.site_lng) || 0,
        hasWarehouse: row.has_warehouse === true,
        warehouseLevel: row.warehouse_level || 0,
        warehouseLat: Number(row.warehouse_lat) || 0,
        warehouseLng: Number(row.warehouse_lng) || 0,
        isPrimary: row.is_primary === true,
      })));
      return;
    }

    if (req.method === "GET" && urlPath === "/map-hives") {
      const params = new URL(req.url, "http://localhost").searchParams;
      const ownerId = params.get("ownerId") || "";
      const hexId = params.get("hexId") || "";
      if (!ownerId || !hexId) {
        send(res, 400, { ok: false });
        return;
      }
      const visible = await pool.query(
        `SELECT id, owner_id, name, bee_count, health, lat, lng, hex_id, site_id
         FROM hives
         WHERE owner_id = $1 AND hex_id = $2 AND in_warehouse = false`,
        [ownerId, hexId]
      );
      send(res, 200, visible.rows.map((row) => ({
        id: row.id,
        ownerId: row.owner_id,
        name: row.name,
        beeCount: row.bee_count || 0,
        health: row.health || 0,
        lat: Number(row.lat) || 0,
        lng: Number(row.lng) || 0,
        hexId: row.hex_id,
        siteId: row.site_id,
      })));
      return;
    }

    if (req.method === "GET" && urlPath === "/pollination-contracts/active-counts") {
      const counted = await pool.query(
        "SELECT hex_id, COUNT(*)::int AS n FROM pollination_contracts WHERE status = 'active' GROUP BY hex_id"
      );
      send(res, 200, counted.rows.map((row) => ({ hexId: row.hex_id, n: row.n })));
      return;
    }

    if (req.method === "POST" && urlPath === "/market-sales") {
      const body = await readBody(req);
      const dayKey = Number(body && body.dayKey) || 0;
      const floraKey = String((body && body.floraKey) || "").slice(0, 160);
      const kg = Number(body && body.kg) || 0;
      if (!floraKey || kg <= 0) {
        send(res, 400, { ok: false });
        return;
      }
      const id = dayKey + ":" + floraKey;
      await pool.query(
        `INSERT INTO market_flora_sales (id, day_key, flora_key, kg_sold, updated_at)
         VALUES ($1, $2, $3, $4, now())
         ON CONFLICT (id) DO UPDATE SET
           kg_sold = market_flora_sales.kg_sold + EXCLUDED.kg_sold,
           updated_at = now()`,
        [id, dayKey, floraKey, kg]
      );
      send(res, 200, { ok: true });
      return;
    }

    if (req.method === "POST" && urlPath === "/events/sale") {
      const body = await readBody(req);
      const kg = Number(body && body.kg) || 0;
      const instanceId = String((body && body.instanceId) || "");
      const ownerId = req.authUid || String((body && body.ownerId) || "");
      if (!ownerId || kg <= 0) {
        send(res, 400, { ok: false });
        return;
      }
      await pool.query(
        `INSERT INTO global_event_progress (id, body, updated_at)
         VALUES ('demand_surge', jsonb_build_object('kgSold', $1::numeric, 'instanceId', $2::text), now())
         ON CONFLICT (id) DO UPDATE SET
           body = jsonb_set(
             COALESCE(global_event_progress.body, '{}'::jsonb),
             '{kgSold}',
             to_jsonb(COALESCE((global_event_progress.body->>'kgSold')::numeric, 0) + $1::numeric)
           ),
           updated_at = now()`,
        [kg, instanceId]
      );
      await pool.query(
        `INSERT INTO event_participants (id, progress_id, owner_id, kg, body, updated_at)
         VALUES ($1, 'demand_surge', $2, $3, '{}', now())
         ON CONFLICT (id) DO UPDATE SET
           kg = event_participants.kg + EXCLUDED.kg,
           updated_at = now()`,
        ["demand_surge:" + ownerId, ownerId, kg]
      );
      send(res, 200, { ok: true });
      return;
    }

    if (await handleHive(req, res, pool, send, readBody)) {
      return;
    }

    if (await handleTable(req, res, pool, send, readBody)) {
      return;
    }

    const playerId = playerIdFromPath(urlPath);
    if (playerId && req.authUid && !ownerAllowed(req, playerId)) {
      send(res, 403, { ok: false, error: "OWNER_MISMATCH" });
      return;
    }
    if (playerId && req.method === "GET") {
      const row = await getPlayer(playerId);
      if (!row) {
        send(res, 404, { ok: false });
        return;
      }
      send(res, 200, rowToPlayer(row));
      return;
    }

    if (playerId && req.method === "PUT") {
      const body = await readBody(req);
      if (body == null || typeof body !== "object" || Array.isArray(body)) {
        send(res, 400, { ok: false });
        return;
      }
      const row = await savePlayer(playerId, body);
      send(res, 200, rowToPlayer(row));
      return;
    }

    if (urlPath.startsWith("/mail") || urlPath.startsWith("/friends")) {
      if (!(await authorized(req))) {
        send(res, 401, { ok: false, error: "AUTH" });
        return;
      }
      const handled = await mail.handle(pool, req, res, urlPath, send, readBody);
      if (handled) return;
    }

    send(res, 404, { ok: false });
  } catch (err) {
    if (err && err.code === "23505") {
      send(res, 409, { ok: false, error: "NAME_OR_BRAND_TAKEN" });
      return;
    }
    if (err && err.status) {
      send(res, err.status, { ok: false });
      return;
    }
    console.error(req.method, urlPath, err.message);
    send(res, 500, { ok: false });
  }
});

migrate()
  .then(() => {
    tripClock.start(pool);
    offerClock.start(pool);
    productionClock.start(pool);
    server.listen(port, host, () => {
      console.log("API escuchando en http://" + host + ":" + port);
    });
  })
  .catch((err) => {
    console.error("no se pudo preparar la base de datos:", err.message);
    process.exit(1);
  });
