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
    population_state_json: JSON.stringify({ workersAdult: 20000 }),
    varroa_pct: 1,
  };
  const summary = stepHive(hive, 20260321);
  assert.equal(summary.hiveName, "Norte");
  assert.equal(summary.floraType, "Romero");
  assert.notEqual(hive.bee_count, 20000);
  assert.ok(hive.honey_production > 0);
  assert.equal(hive.last_summary_day_key, 20260321);
  const pop = JSON.parse(hive.population_state_json);
  assert.equal(pop.workersAdult, hive.bee_count);
});
