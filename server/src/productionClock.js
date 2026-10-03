"use strict";

const fs = require("fs");
const path = require("path");
const netWorth = require("./netWorth");
const colonyTick = require("./colonyTick");

const PRODUCTION_HOUR = 8;
const balance = JSON.parse(fs.readFileSync(
  path.join(__dirname, "..", "data", "game_balance.json"),
  "utf8"
));

function num(value, fallback = 0) {
  const n = Number(value);
  return Number.isFinite(n) ? n : fallback;
}

function clamp(n, min, max) {
  return Math.max(min, Math.min(max, n));
}

function dayKeyToUtc(dayKey) {
  const y = Math.floor(dayKey / 10000);
  const m = Math.floor(dayKey / 100) % 100;
  const d = dayKey % 100;
  return new Date(Date.UTC(y, m - 1, d));
}

function toDayKey(date) {
  return date.getUTCFullYear() * 10000 + (date.getUTCMonth() + 1) * 100 + date.getUTCDate();
}

function addDays(dayKey, days) {
  const date = dayKeyToUtc(dayKey);
  date.setUTCDate(date.getUTCDate() + days);
  return toDayKey(date);
}

function dayOfYear(dayKey) {
  const date = dayKeyToUtc(dayKey);
  const start = Date.UTC(date.getUTCFullYear(), 0, 1);
  return Math.floor((date.getTime() - start) / 86400000) + 1;
}

function portableStringHash(text) {
  let h = 0;
  const s = String(text || "");
  for (let i = 0; i < s.length; i++) {
    h = (Math.imul(31, h) + s.charCodeAt(i)) | 0;
  }
  return h;
}

function uniform01(key, dayKey) {
  const x = Math.sin(portableStringHash(key) * 12.9898 + dayKey * 78.233) * 43758.5453;
  return x - Math.floor(x);
}

function lerpKnots(doy, knots, values) {
  const x = clamp(doy, knots[0], knots[knots.length - 1]);
  for (let i = 1; i < knots.length; i++) {
    if (x <= knots[i]) {
      const span = knots[i] - knots[i - 1] || 1;
      const t = (x - knots[i - 1]) / span;
      return values[i - 1] + (values[i] - values[i - 1]) * t;
    }
  }
  return values[values.length - 1];
}

function clockParts(timeZone, nowMs) {
  let parts;
  try {
    parts = new Intl.DateTimeFormat("en-US", {
      timeZone: timeZone || "Europe/Madrid",
      year: "numeric",
      month: "2-digit",
      day: "2-digit",
      hour: "2-digit",
      hourCycle: "h23",
    }).formatToParts(new Date(nowMs));
  } catch (err) {
    parts = new Intl.DateTimeFormat("en-US", {
      timeZone: "Europe/Madrid",
      year: "numeric",
      month: "2-digit",
      day: "2-digit",
      hour: "2-digit",
      hourCycle: "h23",
    }).formatToParts(new Date(nowMs));
  }
  const pick = (type) => Number(parts.find((part) => part.type === type).value);
  return { year: pick("year"), month: pick("month"), day: pick("day"), hour: pick("hour") };
}

function dueDayKey(timeZone, nowMs) {
  const clock = clockParts(timeZone, nowMs);
  let key = clock.year * 10000 + clock.month * 100 + clock.day;
  if (clock.hour < PRODUCTION_HOUR) key = addDays(key, -1);
  return key;
}

function productionOpen(timeZone, nowMs) {
  return clockParts(timeZone, nowMs).hour >= PRODUCTION_HOUR;
}

const WORKER_BROOD_DAYS = 22;

function parsePop(hive) {
  try {
    const pop = JSON.parse(hive.population_state_json || "{}");
    return pop && typeof pop === "object" ? pop : {};
  } catch (err) {
    return {};
  }
}

function adultsOf(hive) {
  const pop = parsePop(hive);
  const fromW = num(pop.w, NaN);
  if (Number.isFinite(fromW) && fromW > 0) return Math.round(fromW);
  const fromLegacy = num(pop.workersAdult, NaN);
  if (Number.isFinite(fromLegacy) && fromLegacy > 0) return Math.round(fromLegacy);
  return Math.max(0, Math.round(num(hive.bee_count)));
}

function writeAdults(hive, adults, dayKey, deaths, emergences, laid) {
  const next = Math.max(0, Math.round(adults));
  const eggs = Math.max(0, Math.round(laid || 0));
  const pop = parsePop(hive);
  pop.w = next;
  pop.workersAdult = next;
  pop.b = Array.from({ length: WORKER_BROOD_DAYS }, () => eggs);
  pop.lpk = dayKey;
  pop.lwd = Math.max(0, Math.round(deaths || 0));
  pop.lwe = Math.max(0, Math.round(emergences || 0));
  pop.lel = eggs;
  hive.population_state_json = JSON.stringify(pop);
  hive.bee_count = next + eggs * WORKER_BROOD_DAYS;
}

function superCapKg() {
  const caps = balance.honey.superCapKg;
  if (!caps || !caps.length) return 60;
  return caps[caps.length - 1];
}

function dayKeyToIso(dayKey) {
  const y = Math.floor(dayKey / 10000);
  const m = Math.floor(dayKey / 100) % 100;
  const d = dayKey % 100;
  const pad = (n) => String(n).padStart(2, "0");
  return `${y}-${pad(m)}-${pad(d)}`;
}

const precipCache = new Map();

async function precipitationOf(lat, lon, dayKey) {
  const key = `${Number(lat).toFixed(2)},${Number(lon).toFixed(2)},${dayKey}`;
  if (precipCache.has(key)) return precipCache.get(key);
  const date = dayKeyToIso(dayKey);
  const url = "https://api.open-meteo.com/v1/forecast?latitude=" + encodeURIComponent(lat)
    + "&longitude=" + encodeURIComponent(lon)
      + "&daily=precipitation_sum,weather_code,cloud_cover_mean,temperature_2m_mean&timezone=Europe%2FMadrid"
    + "&start_date=" + date + "&end_date=" + date;
  console.log("Open-Meteo envía GET", url);
  let observed = { mm: 0, code: null, clouds: null };
  try {
    const response = await fetch(url);
    const text = await response.text();
    console.log("Open-Meteo responde HTTP", response.status, text.slice(0, 500));
    if (response.ok) {
      const body = JSON.parse(text);
      const mm = body.daily && body.daily.precipitation_sum ? Number(body.daily.precipitation_sum[0]) : 0;
      const code = body.daily && body.daily.weather_code ? Number(body.daily.weather_code[0]) : null;
      const clouds = body.daily && body.daily.cloud_cover_mean ? Number(body.daily.cloud_cover_mean[0]) : null;
      const tempC = body.daily && body.daily.temperature_2m_mean ? Number(body.daily.temperature_2m_mean[0]) : null;
      observed = {
        mm: Number.isFinite(mm) ? mm : 0,
        code: Number.isFinite(code) ? code : null,
        clouds: Number.isFinite(clouds) ? clouds : null,
        tempC: Number.isFinite(tempC) ? tempC : null,
      };
      console.log("Open-Meteo día", date, "código", observed.code, "lluvia", observed.mm, "mm nubes", observed.clouds, "%");
    }
  } catch (err) {
    console.error("clima", key, err.message);
  }
  precipCache.set(key, observed);
  return observed;
}

function rainStopsForage(observed) {
  return observed.mm >= 5;
}

function stepHive(hive, dayKey, rainFactor, weather) {
  const observed = weather && typeof weather === "object" ? { ...weather } : {};
  if (rainFactor === 0) observed.mm = Math.max(num(observed.mm), 5);
  const summary = colonyTick.applyColonyDay(hive, dayKey, observed, {
    balance,
    lerpKnots,
    dayOfYear,
    uniform01,
    superCapKg,
  });
  alignHoneyStocks(hive);
  return summary;
}

/** El total de la colmena y el desglose por flora tienen que coincidir. */
function alignHoneyStocks(hive) {
  const target = Math.round(num(hive.honey_production) * 1000) / 1000;
  let stocks = {};
  if (hive.honey_stocks_json) {
    try {
      const parsed = typeof hive.honey_stocks_json === "string"
        ? JSON.parse(hive.honey_stocks_json)
        : hive.honey_stocks_json;
      if (parsed && typeof parsed === "object" && !Array.isArray(parsed)) {
        stocks = parsed;
      }
    } catch (err) {
      stocks = {};
    }
  }
  let sum = 0;
  for (const key of Object.keys(stocks)) {
    const value = num(stocks[key]);
    if (value <= 1e-9) delete stocks[key];
    else sum += value;
  }
  if (sum <= 1e-9) {
    stocks = {};
    if (target > 1e-9) {
      stocks[hive.flora_type || "Mil flores"] = target;
    }
  } else if (target > 1e-9 && Math.abs(sum - target) > 0.001) {
    const factor = target / sum;
    for (const key of Object.keys(stocks)) {
      const value = Math.round(num(stocks[key]) * factor * 1000) / 1000;
      if (value <= 1e-9) delete stocks[key];
      else stocks[key] = value;
    }
  }
  hive.honey_stocks_json = JSON.stringify(stocks);
}

async function loadPlayer(client, ownerId) {
  const result = await client.query("SELECT * FROM players WHERE id = $1 FOR UPDATE", [ownerId]);
  return result.rows[0] || null;
}

async function applyCheckpoint(client, ownerId, throughDayKey, hives) {
  if (Array.isArray(hives)) {
    for (const hive of hives) {
      if (!hive || !hive.id) continue;
      if (hive.ownerId && String(hive.ownerId) !== String(ownerId)) continue;
      const existingOwner = await client.query("SELECT owner_id FROM hives WHERE id = $1", [hive.id]);
      if (existingOwner.rows[0] && String(existingOwner.rows[0].owner_id) !== String(ownerId)) {
        continue;
      }
      await client.query(
        `INSERT INTO hives (
            id, owner_id, name, bee_count, health, honey_production, queen_genetic_quality,
            lat, lng, hex_id, site_id, flora_type, honey_stocks_json, super_count, population_state_json,
            varroa_pct, first_production_day_key, transhumance_arrives_day_key, in_warehouse,
            feed_honey_bonus_multiplier, feed_honey_bonus_end_day_key_exclusive,
            feed_brood_bonus_multiplier, feed_brood_bonus_end_day_key_exclusive,
            varroa_treatment_days_remaining, varroa_rebound_days_remaining, updated_at
          ) VALUES (
            $1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13,$14,$15,$16,$17,$18,$19,$20,$21,$22,$23,$24,$25,now()
          )
          ON CONFLICT (id) DO UPDATE SET
            owner_id = EXCLUDED.owner_id,
            name = EXCLUDED.name,
            bee_count = EXCLUDED.bee_count,
            health = EXCLUDED.health,
            honey_production = EXCLUDED.honey_production,
            queen_genetic_quality = EXCLUDED.queen_genetic_quality,
            lat = EXCLUDED.lat,
            lng = EXCLUDED.lng,
            hex_id = EXCLUDED.hex_id,
            site_id = EXCLUDED.site_id,
            flora_type = EXCLUDED.flora_type,
            honey_stocks_json = EXCLUDED.honey_stocks_json,
            super_count = EXCLUDED.super_count,
            population_state_json = EXCLUDED.population_state_json,
            varroa_pct = EXCLUDED.varroa_pct,
            first_production_day_key = EXCLUDED.first_production_day_key,
            transhumance_arrives_day_key = EXCLUDED.transhumance_arrives_day_key,
            in_warehouse = EXCLUDED.in_warehouse,
            feed_honey_bonus_multiplier = EXCLUDED.feed_honey_bonus_multiplier,
            feed_honey_bonus_end_day_key_exclusive = EXCLUDED.feed_honey_bonus_end_day_key_exclusive,
            feed_brood_bonus_multiplier = EXCLUDED.feed_brood_bonus_multiplier,
            feed_brood_bonus_end_day_key_exclusive = EXCLUDED.feed_brood_bonus_end_day_key_exclusive,
            varroa_treatment_days_remaining = EXCLUDED.varroa_treatment_days_remaining,
            varroa_rebound_days_remaining = EXCLUDED.varroa_rebound_days_remaining,
            updated_at = now()`,
        [
          hive.id,
          ownerId,
          hive.name || null,
          num(hive.beeCount),
          num(hive.health, 100),
          num(hive.honeyProduction),
          num(hive.queenGeneticQuality, 50),
          num(hive.lat),
          num(hive.lng),
          hive.hexId || null,
          hive.siteId || null,
          hive.floraType || null,
          hive.honeyStocksJson || null,
          num(hive.superCount),
          hive.populationStateJson || null,
          num(hive.varroaPct),
          num(hive.firstProductionDayKey),
          num(hive.transhumanceArrivesDayKey),
          Boolean(hive.inWarehouse),
          num(hive.feedHoneyBonusMultiplier, 1),
          num(hive.feedHoneyBonusEndDayKeyExclusive),
          num(hive.feedBroodBonusMultiplier, 1),
          num(hive.feedBroodBonusEndDayKeyExclusive),
          num(hive.varroaTreatmentDaysRemaining),
          num(hive.varroaReboundDaysRemaining),
        ]
      );
    }
  }
  await client.query(
    "DELETE FROM production_reports WHERE owner_id = $1 AND day_key > $2",
    [ownerId, throughDayKey]
  );
  await client.query(
    "UPDATE players SET last_production_day_key = $2, updated_at = now() WHERE id = $1",
    [ownerId, throughDayKey]
  );
}

async function tickOwner(pool, ownerId, nowMs = Date.now(), timeZoneId, checkpoint) {
  if (!ownerId) return { ok: false, error: "OWNER_REQUIRED" };
  const client = await pool.connect();
  try {
    await client.query("BEGIN");
    let player = await loadPlayer(client, ownerId);
    if (!player) {
      await client.query(
        `INSERT INTO players (id, time_zone_id, last_production_day_key)
         VALUES ($1, $2, 0)
         ON CONFLICT (id) DO NOTHING`,
        [ownerId, timeZoneId || "Europe/Madrid"]
      );
      player = await loadPlayer(client, ownerId);
    }
    if (!player) {
      await client.query("ROLLBACK");
      return { ok: false, error: "PLAYER_NOT_FOUND" };
    }
    const due = dueDayKey(player.time_zone_id || timeZoneId, nowMs);
    const checkpointDay = checkpoint ? num(checkpoint.throughDayKey) : 0;
    if (checkpointDay > 0) {
      await applyCheckpoint(client, ownerId, checkpointDay, checkpoint.hives);
    }
    let last = checkpointDay > 0 ? checkpointDay : num(player.last_production_day_key);
    if (last <= 0) {
      const existing = await client.query(
        "SELECT COUNT(*)::int AS n FROM hives WHERE owner_id = $1",
        [ownerId]
      );
      const hiveCount = existing.rows[0] ? num(existing.rows[0].n) : 0;
      if (hiveCount === 0) {
        await client.query(
          "UPDATE players SET last_production_day_key = $2, updated_at = now() WHERE id = $1",
          [ownerId, due]
        );
        await client.query("COMMIT");
        return { ok: true, adopted: true, settledDayKey: due, throughDayKey: due, days: [] };
      }
      last = addDays(due, -1);
    }
    if (last >= due) {
      await client.query("COMMIT");
      return { ok: true, adopted: false, settled: true, settledDayKey: last, throughDayKey: last, days: [] };
    }
    const hives = await client.query(
      "SELECT * FROM hives WHERE owner_id = $1 FOR UPDATE",
      [ownerId]
    );
    const days = [];
    while (last < due) {
      last = addDays(last, 1);
      const summaries = [];
      for (const hive of hives.rows) {
        if (hive.in_warehouse) continue;
        const first = num(hive.first_production_day_key);
        if (first > 0 && last < first) continue;
        const weather = await precipitationOf(hive.lat, hive.lng, last);
        const rainFactor = rainStopsForage(weather) ? 0 : 1;
        summaries.push(stepHive(hive, last, rainFactor, weather));
      }
      days.push({ dayKey: last, summaries });
      await client.query(
        `INSERT INTO production_reports (owner_id, day_key, summary_json)
         VALUES ($1, $2, $3)
         ON CONFLICT (owner_id, day_key) DO UPDATE SET summary_json = EXCLUDED.summary_json`,
        [ownerId, last, JSON.stringify(summaries)]
      );
    }
    for (const hive of hives.rows) {
      await client.query(
        `UPDATE hives SET
            bee_count = $2,
            honey_production = $3,
            honey_stocks_json = $4,
            population_state_json = $5,
            health = $6,
            varroa_pct = $7,
            queen_genetic_quality = $8,
            last_health_sim_day_key = $9,
            varroa_treatment_days_remaining = $10,
            varroa_rebound_days_remaining = $11,
            feed_honey_bonus_end_day_key_exclusive = $12,
            feed_honey_bonus_multiplier = $13,
            feed_brood_bonus_end_day_key_exclusive = $14,
            feed_brood_bonus_multiplier = $15,
            transhumance_arrives_day_key = $16,
            last_summary_day_key = $17,
            last_summary_honey_kg = $18,
            last_summary_delta_bees = $19,
            last_summary_worker_deaths = $20,
            last_summary_worker_emergences = $21,
            last_summary_eggs_laid = $22,
            last_summary_swarmed = $23,
            last_summary_delta_health = $24,
            last_summary_delta_varroa = $25,
            updated_at = now()
          WHERE id = $1`,
        [
          hive.id,
          hive.bee_count,
          hive.honey_production,
          hive.honey_stocks_json,
          hive.population_state_json,
          hive.health,
          hive.varroa_pct,
          hive.queen_genetic_quality,
          hive.last_health_sim_day_key,
          hive.varroa_treatment_days_remaining,
          hive.varroa_rebound_days_remaining,
          hive.feed_honey_bonus_end_day_key_exclusive,
          hive.feed_honey_bonus_multiplier,
          hive.feed_brood_bonus_end_day_key_exclusive,
          hive.feed_brood_bonus_multiplier,
          hive.transhumance_arrives_day_key,
          hive.last_summary_day_key,
          hive.last_summary_honey_kg,
          hive.last_summary_delta_bees,
          hive.last_summary_worker_deaths,
          hive.last_summary_worker_emergences,
          hive.last_summary_eggs_laid,
          hive.last_summary_swarmed,
          hive.last_summary_delta_health,
          hive.last_summary_delta_varroa,
        ]
      );
    }
    await client.query(
      "UPDATE players SET last_production_day_key = $2, updated_at = now() WHERE id = $1",
      [ownerId, last]
    );
    await client.query("COMMIT");
    try {
      await netWorth.saveDaily(pool, ownerId, last);
    } catch (err) {
      console.error("patrimonio de", ownerId, err.message);
    }
    return { ok: true, adopted: false, settledDayKey: last, throughDayKey: last, days };
  } catch (err) {
    await client.query("ROLLBACK");
    throw err;
  } finally {
    client.release();
  }
}

async function reportsSince(pool, ownerId, sinceDayKey) {
  const result = await pool.query(
    `SELECT day_key, summary_json FROM production_reports
      WHERE owner_id = $1 AND day_key > $2
      ORDER BY day_key`,
    [ownerId, num(sinceDayKey)]
  );
  return result.rows.map((row) => ({
    dayKey: num(row.day_key),
    summaries: JSON.parse(row.summary_json || "[]"),
  }));
}

// A partir de las 8:00 locales de cada jugador, cada minuto. Antes de esa hora no
// se cierra el día. Una pasada cierra a todos los que estén pendientes.
const MAX_PARALLEL = 4;
let ticking = false;

async function tickAll(pool, nowMs = Date.now()) {
  if (ticking) return { ok: true, skipped: true, updated: 0 };
  ticking = true;
  try {
    const players = await pool.query(
      "SELECT id, time_zone_id, last_production_day_key FROM players ORDER BY last_production_day_key ASC, id ASC"
    );
    const pending = [];
    for (const player of players.rows) {
      if (!productionOpen(player.time_zone_id, nowMs)) continue;
      const due = dueDayKey(player.time_zone_id, nowMs);
      if (num(player.last_production_day_key) >= due) continue;
      pending.push(player);
    }
    const batch = pending;
    let index = 0;
    const worker = async () => {
      while (index < batch.length) {
        const player = batch[index++];
        try {
          await tickOwner(pool, player.id, nowMs, player.time_zone_id);
        } catch (err) {
          console.error("producción de", player.id, err.message);
        }
      }
    };
    const workers = [];
    for (let i = 0; i < Math.min(MAX_PARALLEL, batch.length); i++) {
      workers.push(worker());
    }
    await Promise.all(workers);
    return { ok: true, updated: batch.length, waiting: 0 };
  } finally {
    ticking = false;
  }
}

function start(pool) {
  const run = () => {
    tickAll(pool).catch((err) => {
      console.error("reloj de producción:", err.message);
    });
  };
  run();
  const timer = setInterval(run, 60_000);
  if (typeof timer.unref === "function") timer.unref();
}

module.exports = { tickOwner, tickAll, reportsSince, stepHive, dueDayKey, start };
