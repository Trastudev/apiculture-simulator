"use strict";

const { rowToHive } = require("./hives");

const FEED_MULTIPLIER = 0.5;
const TREAT_DAYS = 7;

function dayKey(nowMs) {
  const date = new Date(nowMs);
  return date.getUTCFullYear() * 10000 + (date.getUTCMonth() + 1) * 100 + date.getUTCDate();
}

async function loadHive(client, hiveId, ownerId) {
  const result = await client.query(
    "SELECT * FROM hives WHERE id = $1 FOR UPDATE",
    [hiveId]
  );
  const hive = result.rows[0];
  if (!hive) return { error: "Colmena no encontrada.", status: 404 };
  if (String(hive.owner_id || "") !== String(ownerId || "")) {
    return { error: "Esta colmena no es tuya.", status: 403 };
  }
  return { hive };
}

async function saveCare(client, hive) {
  await client.query(
    `UPDATE hives SET
        feed_honey_bonus_multiplier = $2,
        feed_honey_bonus_end_day_key_exclusive = $3,
        feed_brood_bonus_multiplier = $4,
        feed_brood_bonus_end_day_key_exclusive = $5,
        varroa_treatment_days_remaining = $6,
        varroa_rebound_days_remaining = $7,
        updated_at = now()
      WHERE id = $1`,
    [
      hive.id,
      hive.feed_honey_bonus_multiplier,
      hive.feed_honey_bonus_end_day_key_exclusive,
      hive.feed_brood_bonus_multiplier,
      hive.feed_brood_bonus_end_day_key_exclusive,
      hive.varroa_treatment_days_remaining,
      hive.varroa_rebound_days_remaining,
    ]
  );
}

async function handle(pool, body, ownerId) {
  const type = String(body && body.type || "");
  const hiveId = String(body && body.hiveId || "");
  if (!ownerId) return { ok: false, status: 400, error: "OWNER_REQUIRED" };
  if (type !== "feed-hive" && type !== "treat-varroa") {
    return { ok: true, echoed: true, type };
  }
  if (!hiveId) return { ok: false, status: 400, error: "Colmena no válida." };
  const client = await pool.connect();
  try {
    await client.query("BEGIN");
    const loaded = await loadHive(client, hiveId, ownerId);
    if (loaded.error) {
      await client.query("ROLLBACK");
      return { ok: false, status: loaded.status, error: loaded.error };
    }
    const hive = loaded.hive;
    const today = dayKey(Date.now());
    if (type === "feed-hive") {
      const duration = Math.max(1, Math.round(Number(body.durationDays) || 0));
      const start = Math.max(today, Number(hive.feed_honey_bonus_end_day_key_exclusive) || 0);
      hive.feed_honey_bonus_multiplier = FEED_MULTIPLIER;
      hive.feed_honey_bonus_end_day_key_exclusive = start + duration;
      hive.feed_brood_bonus_multiplier = 1;
      hive.feed_brood_bonus_end_day_key_exclusive = 0;
    } else {
      hive.varroa_treatment_days_remaining = TREAT_DAYS;
      hive.varroa_rebound_days_remaining = 0;
    }
    await saveCare(client, hive);
    await client.query("COMMIT");
    return { ok: true, echoed: false, hive: rowToHive(hive) };
  } catch (err) {
    await client.query("ROLLBACK");
    throw err;
  } finally {
    client.release();
  }
}

module.exports = { handle };
