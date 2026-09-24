"use strict";

const auth = require("./auth");

const FIELDS = [
  ["ownerId", "owner_id"],
  ["name", "name"],
  ["beeCount", "bee_count"],
  ["health", "health"],
  ["honeyProduction", "honey_production"],
  ["reserves", "reserves"],
  ["queenAgeDays", "queen_age_days"],
  ["queenGeneticQuality", "queen_genetic_quality"],
  ["lat", "lat"],
  ["lng", "lng"],
  ["hexId", "hex_id"],
  ["siteId", "site_id"],
  ["elevationMeters", "elevation_meters"],
  ["floraType", "flora_type"],
  ["honeyStocksJson", "honey_stocks_json"],
  ["superCount", "super_count"],
  ["populationStateJson", "population_state_json"],
  ["varroaPct", "varroa_pct"],
  ["varroaTreatmentDaysRemaining", "varroa_treatment_days_remaining"],
  ["varroaReboundDaysRemaining", "varroa_rebound_days_remaining"],
  ["lastHealthSimDayKey", "last_health_sim_day_key"],
  ["firstProductionDayKey", "first_production_day_key"],
  ["lastSummaryDayKey", "last_summary_day_key"],
  ["lastSummaryHoneyKg", "last_summary_honey_kg"],
  ["lastSummaryDeltaBees", "last_summary_delta_bees"],
  ["lastSummaryDeltaHealth", "last_summary_delta_health"],
  ["lastSummaryDeltaVarroa", "last_summary_delta_varroa"],
  ["lastSummaryWorkerDeaths", "last_summary_worker_deaths"],
  ["lastSummaryWorkerEmergences", "last_summary_worker_emergences"],
  ["lastSummaryEggsLaid", "last_summary_eggs_laid"],
  ["lastSummarySwarmed", "last_summary_swarmed"],
  ["feedHoneyBonusEndDayKeyExclusive", "feed_honey_bonus_end_day_key_exclusive"],
  ["feedHoneyBonusMultiplier", "feed_honey_bonus_multiplier"],
  ["feedBroodBonusEndDayKeyExclusive", "feed_brood_bonus_end_day_key_exclusive"],
  ["feedBroodBonusMultiplier", "feed_brood_bonus_multiplier"],
  ["transhumanceArrivesDayKey", "transhumance_arrives_day_key"],
  ["pendingContractHexId", "pending_contract_hex_id"],
  ["pendingContractDayKey", "pending_contract_day_key"],
  ["contractId", "contract_id"],
  ["contractOriginHexId", "contract_origin_hex_id"],
  ["contractOriginFlora", "contract_origin_flora"],
  ["contractOriginLat", "contract_origin_lat"],
  ["contractOriginLng", "contract_origin_lng"],
  ["inWarehouse", "in_warehouse"],
  ["returnToWarehouse", "return_to_warehouse"],
];

const DEFAULTS = {
  owner_id: null,
  name: null,
  bee_count: 0,
  health: 100,
  honey_production: 0,
  reserves: 0,
  queen_age_days: 0,
  queen_genetic_quality: 0,
  lat: 0,
  lng: 0,
  hex_id: null,
  site_id: null,
  elevation_meters: -1,
  flora_type: null,
  honey_stocks_json: null,
  super_count: 0,
  population_state_json: null,
  varroa_pct: 0,
  varroa_treatment_days_remaining: 0,
  varroa_rebound_days_remaining: 0,
  last_health_sim_day_key: 0,
  first_production_day_key: 0,
  last_summary_day_key: 0,
  last_summary_honey_kg: 0,
  last_summary_delta_bees: 0,
  last_summary_delta_health: 0,
  last_summary_delta_varroa: 0,
  last_summary_worker_deaths: 0,
  last_summary_worker_emergences: 0,
  last_summary_eggs_laid: 0,
  last_summary_swarmed: false,
  feed_honey_bonus_end_day_key_exclusive: 0,
  feed_honey_bonus_multiplier: 1,
  feed_brood_bonus_end_day_key_exclusive: 0,
  feed_brood_bonus_multiplier: 1,
  transhumance_arrives_day_key: 0,
  pending_contract_hex_id: null,
  pending_contract_day_key: 0,
  contract_id: null,
  contract_origin_hex_id: null,
  contract_origin_flora: null,
  contract_origin_lat: 0,
  contract_origin_lng: 0,
  in_warehouse: false,
  return_to_warehouse: false,
};

const NUMBERS = new Set([
  "bee_count", "health", "honey_production", "reserves", "queen_age_days",
  "queen_genetic_quality", "lat", "lng", "elevation_meters", "super_count",
  "varroa_pct", "varroa_treatment_days_remaining", "varroa_rebound_days_remaining",
  "last_health_sim_day_key", "first_production_day_key", "last_summary_day_key",
  "last_summary_honey_kg", "last_summary_delta_bees", "last_summary_delta_health",
  "last_summary_delta_varroa", "last_summary_worker_deaths",
  "last_summary_worker_emergences", "last_summary_eggs_laid",
  "feed_honey_bonus_end_day_key_exclusive", "feed_honey_bonus_multiplier",
  "feed_brood_bonus_end_day_key_exclusive", "feed_brood_bonus_multiplier",
  "transhumance_arrives_day_key", "pending_contract_day_key",
  "contract_origin_lat", "contract_origin_lng",
]);

function rowToHive(row) {
  const out = { id: row.id, updatedAt: row.updated_at };
  for (const [jsonKey, column] of FIELDS) {
    const value = row[column];
    out[jsonKey] = NUMBERS.has(column) && value != null ? Number(value) : value;
  }
  return out;
}

function hiveId(urlPath) {
  const match = /^\/hives\/([^/]+)$/.exec(urlPath);
  if (!match) return null;
  const id = decodeURIComponent(match[1]);
  if (!/^[A-Za-z0-9_-]{1,128}$/.test(id)) return null;
  return id;
}

async function getHive(pool, id) {
  const result = await pool.query("SELECT * FROM hives WHERE id = $1", [id]);
  return result.rows[0] || null;
}

async function saveHive(pool, id, body) {
  const current = await getHive(pool, id);
  const values = Object.assign({}, DEFAULTS);
  if (current) {
    for (const column of Object.keys(DEFAULTS)) {
      values[column] = current[column];
    }
  }
  for (const [jsonKey, column] of FIELDS) {
    if (Object.prototype.hasOwnProperty.call(body, jsonKey)) {
      values[column] = body[jsonKey];
    }
  }
  const columns = Object.keys(DEFAULTS);
  const placeholders = columns.map((_, i) => "$" + (i + 2));
  const updates = columns.map((column) => column + " = EXCLUDED." + column);
  const result = await pool.query(
    `INSERT INTO hives (id, ${columns.join(", ")}, updated_at)
     VALUES ($1, ${placeholders.join(", ")}, now())
     ON CONFLICT (id) DO UPDATE SET ${updates.join(", ")}, updated_at = now()
     RETURNING *`,
    [id, ...columns.map((column) => values[column])]
  );
  return result.rows[0];
}

async function handleHive(req, res, pool, send, readBody) {
  const urlPath = (req.url || "/").split("?")[0];
  if (req.method === "GET" && urlPath === "/hives") {
    let ownerId = new URL(req.url, "http://localhost").searchParams.get("ownerId");
    if (auth.isConfigured() && req.authUid) {
      if (ownerId && !auth.sameUid(req.authUid, ownerId)) {
        send(res, 403, { ok: false, error: "OWNER_MISMATCH" });
        return true;
      }
      ownerId = req.authUid;
    }
    if (!ownerId) {
      send(res, 400, { ok: false });
      return true;
    }
    const result = await pool.query(
      "SELECT * FROM hives WHERE owner_id = $1 ORDER BY name",
      [ownerId]
    );
    send(res, 200, result.rows.map(rowToHive));
    return true;
  }

  const id = hiveId(urlPath);
  if (!id) return false;

  if (req.method === "GET") {
    const row = await getHive(pool, id);
    if (!row) {
      send(res, 404, { ok: false });
      return true;
    }
    if (auth.isConfigured() && req.authUid && !auth.sameUid(req.authUid, row.owner_id)) {
      send(res, 403, { ok: false, error: "OWNER_MISMATCH" });
      return true;
    }
    send(res, 200, rowToHive(row));
    return true;
  }

  if (req.method === "PUT") {
    const body = await readBody(req);
    if (body == null || typeof body !== "object" || Array.isArray(body)) {
      send(res, 400, { ok: false });
      return true;
    }
    if (auth.isConfigured() && req.authUid) {
      if (!auth.sameUid(req.authUid, body.ownerId)) {
        send(res, 403, { ok: false, error: "OWNER_MISMATCH" });
        return true;
      }
      const existing = await getHive(pool, id);
      if (existing && !auth.sameUid(req.authUid, existing.owner_id)) {
        send(res, 403, { ok: false, error: "OWNER_MISMATCH" });
        return true;
      }
    }
    const row = await saveHive(pool, id, body);
    send(res, 200, rowToHive(row));
    return true;
  }

  return false;
}

module.exports = { handleHive, rowToHive };
