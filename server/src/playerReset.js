"use strict";

function foldName(raw) {
  return String(raw || "")
    .trim()
    .toLowerCase()
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/[^a-z0-9]+/g, "");
}

async function isAleix(pool, uid) {
  if (!uid) return false;
  const row = await pool.query("SELECT player_name FROM players WHERE id = $1", [uid]);
  return foldName(row.rows[0] && row.rows[0].player_name) === "aleix";
}

async function subtractEvents(client, ownerId) {
  const parts = await client.query(
    "SELECT progress_id, kg FROM event_participants WHERE owner_id = $1",
    [ownerId]
  );
  for (const row of parts.rows) {
    const kg = Number(row.kg) || 0;
    if (!row.progress_id || kg <= 0) continue;
    await client.query(
      `UPDATE global_event_progress
          SET body = jsonb_set(
                body,
                '{kgSold}',
                to_jsonb(GREATEST(0, COALESCE((body->>'kgSold')::numeric, 0) - $2::numeric)),
                true
              ),
              updated_at = now()
        WHERE id = $1`,
      [row.progress_id, kg]
    );
  }
}

async function eraseGameplay(client, ownerId) {
  await subtractEvents(client, ownerId);
  await client.query("DELETE FROM event_participants WHERE owner_id = $1", [ownerId]);
  await client.query("DELETE FROM event_claims WHERE owner_id = $1", [ownerId]);
  await client.query("DELETE FROM player_stores WHERE owner_id = $1", [ownerId]);
  await client.query("DELETE FROM production_reports WHERE owner_id = $1", [ownerId]);
  await client.query("DELETE FROM trip_effects WHERE owner_id = $1", [ownerId]);
  await client.query("SAVEPOINT trip_completions_wipe");
  try {
    await client.query("DELETE FROM trip_completions WHERE owner_id = $1", [ownerId]);
  } catch (err) {
    await client.query("ROLLBACK TO SAVEPOINT trip_completions_wipe");
  }
  await client.query(
    "DELETE FROM hive_daily_yields WHERE hive_id IN (SELECT id FROM hives WHERE owner_id = $1)",
    [ownerId]
  );
  await client.query("DELETE FROM hives WHERE owner_id = $1", [ownerId]);
  await client.query("DELETE FROM hex_parcels WHERE owner_id = $1", [ownerId]);
  await client.query("DELETE FROM pollination_contracts WHERE owner_id = $1", [ownerId]);
  await client.query("DELETE FROM truck_trips WHERE owner_id = $1", [ownerId]);
  await client.query("DELETE FROM cargo_trips WHERE owner_id = $1", [ownerId]);
  await client.query(
    `UPDATE honey_orders
        SET taken = false, claimed_by = NULL, updated_at = now()
      WHERE claimed_by = $1`,
    [ownerId]
  );
  await client.query(
    `UPDATE pollination_offers
        SET taken = false, claimed_by = NULL
      WHERE claimed_by = $1`,
    [ownerId]
  );
}

async function wipeOwner(pool, ownerId) {
  const client = await pool.connect();
  try {
    await client.query("BEGIN");
    await eraseGameplay(client, ownerId);
    await client.query(
      `UPDATE players SET
          economy_balance_eur = 20000,
          economy_honey_buckets_json = '{}',
          economy_honey_sold_kg_total = 0,
          economy_honey_sold_by_flora_json = '{}',
          player_level = 0,
          player_xp = 0,
          net_worth_b = 0,
          net_worth_day_key = 0,
          last_production_day_key = 0,
          map_region = NULL,
          orders_delivered = 0,
          adult_bee_count = 0,
          contract_count = 0,
          updated_at = now()
        WHERE id = $1`,
      [ownerId]
    );
    await client.query("COMMIT");
  } catch (err) {
    await client.query("ROLLBACK");
    throw err;
  } finally {
    client.release();
  }
}

async function wipeEveryone(pool) {
  const client = await pool.connect();
  try {
    await client.query("BEGIN");
    await client.query("DELETE FROM event_participants");
    await client.query("DELETE FROM event_claims");
    await client.query("DELETE FROM player_stores");
    await client.query("DELETE FROM production_reports");
    await client.query("DELETE FROM trip_effects");
    await client.query("DELETE FROM hive_daily_yields");
    await client.query("DELETE FROM hives");
    await client.query("DELETE FROM hex_parcels");
    await client.query("DELETE FROM pollination_contracts");
    await client.query("DELETE FROM truck_trips");
    await client.query("DELETE FROM cargo_trips");
    await client.query("DELETE FROM honey_orders");
    await client.query("DELETE FROM pollination_offers");
    await client.query("UPDATE market_flora_sales SET kg_sold = 0, updated_at = now()");
    await client.query(
      `UPDATE global_event_progress
          SET body = jsonb_set(body, '{kgSold}', '0'::jsonb, true),
              updated_at = now()`
    );
    await client.query(
      `UPDATE players SET
          economy_balance_eur = 20000,
          economy_honey_buckets_json = '{}',
          economy_honey_sold_kg_total = 0,
          economy_honey_sold_by_flora_json = '{}',
          player_level = 0,
          player_xp = 0,
          net_worth_b = 0,
          net_worth_day_key = 0,
          last_production_day_key = 0,
          map_region = NULL,
          orders_delivered = 0,
          adult_bee_count = 0,
          contract_count = 0,
          updated_at = now()`
    );
    await client.query("COMMIT");
  } catch (err) {
    await client.query("ROLLBACK");
    throw err;
  } finally {
    client.release();
  }
}

async function deleteAccount(pool, ownerId) {
  const client = await pool.connect();
  try {
    await client.query("BEGIN");
    await eraseGameplay(client, ownerId);
    await client.query("DELETE FROM unique_names WHERE owner_id = $1", [ownerId]);
    await client.query(
      "DELETE FROM mail_messages WHERE from_id = $1 OR to_id = $1",
      [ownerId]
    );
    await client.query(
      "DELETE FROM friendships WHERE requester_id = $1 OR addressee_id = $1",
      [ownerId]
    );
    await client.query("DELETE FROM leaderboard_entries WHERE id = $1", [ownerId]);
    await client.query("DELETE FROM production_states WHERE id = $1", [ownerId]);
    await client.query("DELETE FROM players WHERE id = $1", [ownerId]);
    await client.query("COMMIT");
  } catch (err) {
    await client.query("ROLLBACK");
    throw err;
  } finally {
    client.release();
  }
}

module.exports = { isAleix, wipeOwner, wipeEveryone, deleteAccount };
