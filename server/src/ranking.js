"use strict";

const TOP = 100;

const METRICS = new Set(["netWorth", "honeySold", "contracts", "orders", "bees"]);

function num(value) {
  const n = Number(value);
  return Number.isFinite(n) ? n : 0;
}

function int(value) {
  return Math.trunc(num(value));
}

function text(value) {
  if (value == null) return null;
  const s = String(value).trim();
  return s.length === 0 ? null : s;
}

function floraKg(raw, flora) {
  if (!flora) return 0;
  let map = raw;
  if (typeof raw === "string") {
    try {
      map = JSON.parse(raw || "{}");
    } catch (err) {
      map = {};
    }
  }
  if (!map || typeof map !== "object") return 0;
  return num(map[flora]);
}

function scoreOf(person, metric, flora) {
  if (metric === "netWorth") return num(person.netWorthB);
  if (metric === "honeySold") {
    return flora ? floraKg(person.honeySoldByFlora, flora) : num(person.honeySoldKgTotal);
  }
  if (metric === "contracts") {
    return Math.max(num(person.serverContracts), num(person.contractCount));
  }
  if (metric === "orders") return num(person.ordersDelivered);
  if (metric === "bees") {
    return num(person.serverHives) > 0 ? num(person.serverBees) : num(person.adultBeeCount);
  }
  return 0;
}

function nextNetWorth(current, data) {
  const body = data && typeof data === "object" ? data : {};
  if (!Object.prototype.hasOwnProperty.call(body, "netWorthB")) {
    return int(current);
  }
  const incoming = int(body.netWorthB);
  if (incoming > 0 || body.assetsKnown === true) {
    return incoming;
  }
  return int(current);
}

function rankRows(people, metric, flora, selfId) {
  const ranked = [];
  for (const person of people) {
    const score = scoreOf(person, metric, flora);
    const mine = Boolean(selfId) && person.id === selfId;
    if (score <= 1e-9 && metric !== "netWorth" && !mine) continue;
    ranked.push({
      id: person.id,
      playerName: person.playerName || "Jugador",
      honeyBrand: person.honeyBrand || "—",
      score,
      isSelf: mine,
    });
  }
  ranked.sort((a, b) => {
    const delta = b.score - a.score;
    if (delta !== 0) return delta;
    return String(a.playerName).localeCompare(String(b.playerName), "es");
  });
  const window = Math.min(TOP, ranked.length);
  const rows = [];
  for (let i = 0; i < window; i++) {
    rows.push(Object.assign({ rank: i + 1 }, ranked[i]));
  }
  const selfIndex = ranked.findIndex((row) => row.isSelf);
  if (selfIndex >= TOP) {
    rows.push(Object.assign({ rank: selfIndex + 1 }, ranked[selfIndex]));
  }
  return rows;
}

async function publish(pool, ownerId, body) {
  const data = body && typeof body === "object" ? body : {};
  await pool.query(
    `INSERT INTO players (id) VALUES ($1)
     ON CONFLICT (id) DO NOTHING`,
    [ownerId]
  );
  const current = await pool.query("SELECT * FROM players WHERE id = $1", [ownerId]);
  const row = current.rows[0];
  const has = (key) => Object.prototype.hasOwnProperty.call(data, key);
  await pool.query(
    `UPDATE players SET
        player_name = $2,
        honey_brand = $3,
        economy_balance_eur = $4,
        economy_honey_buckets_json = $5,
        economy_honey_sold_kg_total = $6,
        economy_honey_sold_by_flora_json = $7,
        player_level = $8,
        player_xp = $9,
        net_worth_b = $10,
        net_worth_day_key = $11,
        map_region = $12,
        orders_delivered = $13,
        adult_bee_count = $14,
        contract_count = $15,
        updated_at = now()
      WHERE id = $1`,
    [
      ownerId,
      has("playerName") ? text(data.playerName) : row.player_name,
      has("honeyBrand") ? text(data.honeyBrand) : row.honey_brand,
      has("economyBalanceEur") ? num(data.economyBalanceEur) : row.economy_balance_eur,
      has("economyHoneyBucketsJson") ? text(data.economyHoneyBucketsJson) : row.economy_honey_buckets_json,
      has("economyHoneySoldKgTotal") ? num(data.economyHoneySoldKgTotal) : row.economy_honey_sold_kg_total,
      has("economyHoneySoldByFloraJson")
        ? text(data.economyHoneySoldByFloraJson)
        : row.economy_honey_sold_by_flora_json,
      has("playerLevel") ? int(data.playerLevel) : row.player_level,
      has("playerXp") ? num(data.playerXp) : row.player_xp,
      nextNetWorth(row.net_worth_b, data),
      has("netWorthDayKey") ? int(data.netWorthDayKey) : row.net_worth_day_key,
      has("mapRegion") ? text(data.mapRegion) : row.map_region,
      has("ordersDelivered") ? int(data.ordersDelivered) : row.orders_delivered,
      has("adultBeeCount") ? int(data.adultBeeCount) : row.adult_bee_count,
      has("contractCount") ? int(data.contractCount) : row.contract_count,
    ]
  );
}

async function list(pool, options) {
  const metric = METRICS.has(options.metric) ? options.metric : "netWorth";
  const region = text(options.region) || "";
  const flora = text(options.flora) || "";
  const selfId = options.selfId ? String(options.selfId) : "";
  const result = await pool.query(
    `SELECT p.id,
            p.player_name,
            p.honey_brand,
            p.net_worth_b,
            p.economy_honey_sold_kg_total,
            p.economy_honey_sold_by_flora_json,
            p.map_region,
            p.orders_delivered,
            p.adult_bee_count,
            p.contract_count,
            COALESCE(h.hive_count, 0) AS server_hives,
            COALESCE(h.adult_bees, 0) AS server_bees,
            COALESCE(c.settled, 0) AS server_contracts
       FROM players p
       LEFT JOIN (
         SELECT owner_id,
                COUNT(*)::int AS hive_count,
                COALESCE(SUM(GREATEST(COALESCE(bee_count, 0), 0)), 0)::bigint AS adult_bees
           FROM hives
          GROUP BY owner_id
       ) h ON h.owner_id = p.id
       LEFT JOIN (
         SELECT owner_id, COUNT(*)::int AS settled
           FROM pollination_contracts
          WHERE status = 'SETTLED'
          GROUP BY owner_id
       ) c ON c.owner_id = p.id`
  );
  const people = [];
  for (const row of result.rows) {
    const mapRegion = row.map_region || "";
    if (region && mapRegion !== region) continue;
    people.push({
      id: row.id,
      playerName: row.player_name,
      honeyBrand: row.honey_brand,
      netWorthB: num(row.net_worth_b),
      honeySoldKgTotal: num(row.economy_honey_sold_kg_total),
      honeySoldByFlora: row.economy_honey_sold_by_flora_json,
      ordersDelivered: num(row.orders_delivered),
      adultBeeCount: num(row.adult_bee_count),
      contractCount: num(row.contract_count),
      serverHives: num(row.server_hives),
      serverBees: num(row.server_bees),
      serverContracts: num(row.server_contracts),
    });
  }
  return rankRows(people, metric, metric === "honeySold" ? flora : "", selfId);
}

module.exports = { publish, list, rankRows, scoreOf, nextNetWorth };
