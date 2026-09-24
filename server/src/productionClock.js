"use strict";

const fs = require("fs");
const path = require("path");

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

function adultsOf(hive) {
  try {
    const pop = JSON.parse(hive.population_state_json || "{}");
    const adults = num(pop.workersAdult, NaN);
    if (Number.isFinite(adults) && adults > 0) return Math.round(adults);
  } catch (err) {
    /* la colmena legacy solo tiene bee_count */
  }
  return Math.max(0, Math.round(num(hive.bee_count)));
}

function writeAdults(hive, adults) {
  hive.bee_count = adults;
  let pop = {};
  try {
    pop = JSON.parse(hive.population_state_json || "{}") || {};
  } catch (err) {
    pop = {};
  }
  if (pop && typeof pop === "object") {
    pop.workersAdult = adults;
    hive.population_state_json = JSON.stringify(pop);
  }
}

function superCapKg(superCount) {
  const caps = balance.honey.superCapKg;
  const index = clamp(Math.round(num(superCount)), 0, caps.length - 1);
  return caps[index];
}

function stepHive(hive, dayKey) {
  const pop = balance.population;
  const eggs = balance.eggs;
  const honey = balance.honey;
  const before = adultsOf(hive);
  const doy = dayOfYear(dayKey);
  const health = clamp(num(hive.health, 80), 0, 100);
  const queen = clamp(num(hive.queen_genetic_quality, 50), 0, 100);
  let k = lerpKnots(doy, pop.kDoy, pop.kAdults);
  k *= pop.queenKFactorMin + pop.queenKFactorSpan * (queen / 100);
  k *= pop.healthKFactorMin + pop.healthKFactorSpan * (health / 100);
  const varroa = Math.max(0, num(hive.varroa_pct));
  if (varroa > pop.varroaKStartPct) {
    const t = Math.min(1, (varroa - pop.varroaKStartPct) / pop.varroaKSpanPct);
    k *= 1 - pop.varroaKMaxPenalty * t;
  }
  k = clamp(Math.round(k), 0, pop.maxAdultWorkersPerHive);
  const life = clamp(
    lerpKnots(doy, pop.workerLifespanDoy, pop.workerLifespanDays),
    pop.workerLifespanClampMin,
    pop.workerLifespanClampMax
  );
  const deaths = Math.min(before, Math.round(before / life));
  const lambda = k >= before ? pop.lambdaTowardK : pop.lambdaTowardKDown;
  let next = before + Math.round(lambda * (k - before));
  next = clamp(next, 0, pop.maxAdultWorkersPerHive);
  const layBase = lerpKnots(doy, eggs.doy, eggs.base);
  const strength = Math.min(1, before / eggs.strengthRefAdults);
  let laid = layBase * (0.55 + 0.45 * (queen / 100)) * (0.35 + 0.65 * strength);
  laid *= 0.5 + 0.5 * (health / 100);
  laid *= eggs.layNoiseMin + uniform01((hive.id || "_") + ":lay", dayKey) * eggs.layNoiseSpan;
  laid = clamp(Math.round(laid), 0, eggs.maxPerDay);
  const noise = honey.nectarNoiseMin
    + uniform01((hive.id || "_") + ":nectar", dayKey) * honey.nectarNoiseSpan;
  const forage = Math.max(0, before * honey.foragerFraction * honey.kgPerForagerFullFlow
    * (0.55 + 0.45 * (health / 100)) * noise);
  const brood = laid * 21;
  const consumption = honey.consumptionBaseKg
    + before * honey.consumptionPerAdultKg
    + brood * honey.consumptionPerBroodEqKg;
  const net = forage - consumption;
  const cap = superCapKg(hive.super_count);
  const stock = clamp(num(hive.honey_production) + net, 0, cap);
  writeAdults(hive, next);
  hive.honey_production = Math.round(stock * 1000) / 1000;
  hive.last_summary_day_key = dayKey;
  hive.last_summary_honey_kg = Math.round(net * 1000) / 1000;
  hive.last_summary_delta_bees = next - before;
  hive.last_summary_worker_deaths = deaths;
  hive.last_summary_eggs_laid = laid;
  hive.last_summary_swarmed = false;
  return {
    hiveId: hive.id,
    hiveName: hive.name || "",
    floraType: hive.flora_type || "Mil flores",
    honeyKg: hive.last_summary_honey_kg,
    workerNet: hive.last_summary_delta_bees,
    eggsLaid: laid,
    beeCount: next,
    honeyStockKg: hive.honey_production,
  };
}

async function loadPlayer(client, ownerId) {
  const result = await client.query("SELECT * FROM players WHERE id = $1 FOR UPDATE", [ownerId]);
  return result.rows[0] || null;
}

async function applyCheckpoint(client, ownerId, throughDayKey, hives) {
  if (Array.isArray(hives)) {
    for (const hive of hives) {
      if (!hive || !hive.id) continue;
      await client.query(
        `INSERT INTO hives (
            id, owner_id, name, bee_count, health, honey_production, queen_genetic_quality,
            lat, lng, hex_id, site_id, flora_type, super_count, population_state_json,
            varroa_pct, first_production_day_key, transhumance_arrives_day_key, in_warehouse,
            feed_honey_bonus_multiplier, feed_honey_bonus_end_day_key_exclusive,
            feed_brood_bonus_multiplier, feed_brood_bonus_end_day_key_exclusive,
            varroa_treatment_days_remaining, varroa_rebound_days_remaining, updated_at
          ) VALUES (
            $1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13,$14,$15,$16,$17,$18,$19,$20,$21,$22,$23,$24,now()
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
          hive.ownerId || ownerId,
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
      await client.query(
        "UPDATE players SET last_production_day_key = $2, updated_at = now() WHERE id = $1",
        [ownerId, due]
      );
      await client.query("COMMIT");
      return { ok: true, adopted: true, settledDayKey: due, throughDayKey: due, days: [] };
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
        if (num(hive.transhumance_arrives_day_key) > last) continue;
        summaries.push(stepHive(hive, last));
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
            population_state_json = $4,
            last_summary_day_key = $5,
            last_summary_honey_kg = $6,
            last_summary_delta_bees = $7,
            last_summary_worker_deaths = $8,
            last_summary_eggs_laid = $9,
            last_summary_swarmed = $10,
            updated_at = now()
          WHERE id = $1`,
        [
          hive.id,
          hive.bee_count,
          hive.honey_production,
          hive.population_state_json,
          hive.last_summary_day_key,
          hive.last_summary_honey_kg,
          hive.last_summary_delta_bees,
          hive.last_summary_worker_deaths,
          hive.last_summary_eggs_laid,
          hive.last_summary_swarmed,
        ]
      );
    }
    await client.query(
      "UPDATE players SET last_production_day_key = $2, updated_at = now() WHERE id = $1",
      [ownerId, last]
    );
    await client.query("COMMIT");
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
// se cierra el día. Como mucho dos cálculos a la vez y una tanda por minuto.
const MAX_PARALLEL = 2;
const MAX_PER_PASS = 20;
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
    const batch = pending.slice(0, MAX_PER_PASS);
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
    return { ok: true, updated: batch.length, waiting: pending.length - batch.length };
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
