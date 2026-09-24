"use strict";

const fs = require("fs");
const http = require("http");
const path = require("path");
const { Pool } = require("pg");
const { handleHive } = require("./hives");
const { handleTable } = require("./tables");
const tripClock = require("./tripClock");
const offerClock = require("./offerClock");
const auth = require("./auth");

const apiToken = process.env.API_TOKEN || "";

const port = Number(process.env.PORT || 8080);
const host = process.env.HOST || "127.0.0.1";

const pool = new Pool({
  host: process.env.PGHOST || "127.0.0.1",
  port: Number(process.env.PGPORT || 5432),
  database: process.env.PGDATABASE || "apiculture",
  user: process.env.PGUSER || "apiculture",
  password: process.env.PGPASSWORD,
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
    economy_balance_eur: current ? current.economy_balance_eur : 10000,
    economy_honey_buckets_json: current ? current.economy_honey_buckets_json : null,
    economy_honey_sold_kg_total: current ? current.economy_honey_sold_kg_total : 0,
    economy_honey_sold_by_flora_json: current ? current.economy_honey_sold_by_flora_json : null,
    player_level: current ? current.player_level : 0,
    player_xp: current ? current.player_xp : 0,
  };
  for (const [jsonKey, column] of Object.entries(COLUMNS)) {
    if (Object.prototype.hasOwnProperty.call(body, jsonKey)) {
      values[column] = body[jsonKey];
    }
  }
  const result = await pool.query(
    `INSERT INTO players (
        id, player_name, honey_brand, profile_complete, time_zone_id,
        economy_balance_eur, economy_honey_buckets_json, economy_honey_sold_kg_total,
        economy_honey_sold_by_flora_json, player_level, player_xp, updated_at
      ) VALUES (
        $1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, now()
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
  if (req.headers.authorization === "Bearer " + apiToken) return true;
  return false;
}

const server = http.createServer(async (req, res) => {
  const urlPath = (req.url || "/").split("?")[0];
  try {
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

    if (req.method === "POST" && urlPath === "/trip-clock/run") {
      await tripClock.tick(pool);
      send(res, 200, { ok: true });
      return;
    }

    if (req.method === "PUT" && urlPath === "/market-prices") {
      const body = await readBody(req);
      const result = await offerClock.saveMarketPrices(pool, body);
      setImmediate(() => {
        offerClock.tick(pool).catch((err) => console.error("reloj de ofertas:", err.message));
      });
      send(res, 200, result);
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
      const result = await offerClock.snapshot(pool, region, ownerId);
      send(res, 200, result);
      return;
    }

    if (req.method === "POST" && urlPath === "/offer-actions") {
      const body = await readBody(req);
      const result = await offerClock.action(pool, body);
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
      const ownerId = new URL(req.url, "http://localhost").searchParams.get("ownerId");
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
      await pool.query("DELETE FROM trip_effects WHERE id = $1", [id]);
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
    server.listen(port, host, () => {
      console.log("API escuchando en http://" + host + ":" + port);
    });
  })
  .catch((err) => {
    console.error("no se pudo preparar la base de datos:", err.message);
    process.exit(1);
  });
