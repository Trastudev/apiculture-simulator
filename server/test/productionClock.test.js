"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const { stepHive } = require("../src/productionClock");

test("a laying colony changes bees and honey stock on the server", () => {
  const hive = {
    id: "hive-1",
    name: "Norte",
    flora_type: "Romero",
    bee_count: 20000,
    health: 90,
    queen_genetic_quality: 70,
    honey_production: 8,
    super_count: 1,
    population_state_json: JSON.stringify({ w: 20000 }),
    varroa_pct: 1,
  };
  const summary = stepHive(hive, 20260321);
  assert.equal(summary.hiveName, "Norte");
  assert.equal(summary.floraType, "Romero");
  assert.notEqual(hive.bee_count, 20000);
  assert.ok(hive.honey_production > 0);
  const stocks = JSON.parse(hive.honey_stocks_json);
  const stockSum = Object.values(stocks).reduce((sum, kg) => sum + kg, 0);
  assert.ok(Math.abs(stockSum - hive.honey_production) < 0.02);
  assert.equal(hive.last_summary_day_key, 20260321);
  const pop = JSON.parse(hive.population_state_json);
  assert.equal(pop.w, 20000 + summary.workerNet);
  assert.equal(hive.last_summary_delta_bees, summary.workerNet);
  assert.equal(hive.bee_count, pop.w + (pop.lel || 0) * 22);
});

test("reads compact w, not bee_count total, for adult net", () => {
  const hive = {
    id: "hive-2",
    bee_count: 40000,
    health: 90,
    queen_genetic_quality: 86,
    honey_production: 8,
    super_count: 1,
    population_state_json: JSON.stringify({ w: 12039, b: Array(22).fill(1271) }),
    varroa_pct: 1,
  };
  const summary = stepHive(hive, 20261003);
  const pop = JSON.parse(hive.population_state_json);
  assert.equal(pop.w, 12039 + summary.workerNet);
  assert.ok(summary.workerNet !== 0, "adults should move toward October K");
  assert.ok(Math.abs(pop.w - 12039) < 4000);
});

test("remote tick grows varroa and writes health like the client", () => {
  const hive = {
    id: "hive-varroa",
    bee_count: 25000,
    health: 90,
    queen_genetic_quality: 80,
    honey_production: 8,
    reserves: 40,
    super_count: 1,
    varroa_pct: 2,
    last_health_sim_day_key: 0,
    population_state_json: JSON.stringify({ w: 14000, qm: "LAYING" }),
  };
  const summary = stepHive(hive, 20260520, 1, { mm: 0, tempC: 20, code: 1, clouds: 10 });
  assert.ok(hive.varroa_pct > 2, "varroa should rise without treatment");
  assert.equal(hive.last_health_sim_day_key, 20260520);
  assert.ok(Number.isFinite(hive.health));
  assert.ok(summary.varroaPct > 2);
});
