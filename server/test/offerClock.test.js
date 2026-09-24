"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const clock = require("../src/offerClock");

function fakePool(responses) {
  const state = { queries: [] };
  const client = {
    async query(sql, params) {
      state.queries.push({ sql, params });
      if (/SELECT \* FROM honey_orders WHERE id = \$1 FOR UPDATE/i.test(sql)) {
        return { rowCount: 1, rows: [{ id: params[0], taken: false, claimed_by: null, expire_epoch_ms: Date.now() + 100000 }] };
      }
      if (/UPDATE honey_orders SET taken=true/i.test(sql)) {
        return { rowCount: 1, rows: [{ id: params[0], taken: true, claimed_by: params[1] }] };
      }
      if (/SELECT id,taken,claimed_by FROM pollination_offers/i.test(sql)) {
        return { rowCount: 1, rows: [{ id: "p1", taken: false, claimed_by: null }] };
      }
      if (/UPDATE pollination_offers SET taken=true/i.test(sql)) {
        return { rowCount: 1, rows: [{ id: params[0], taken: true }] };
      }
      if (/SELECT id FROM pollination_offers/i.test(sql)) {
        return { rowCount: 1, rows: [{ id: "p1" }] };
      }
      if (/SELECT id,npc_name/i.test(sql)) return { rows: responses.orders || [] };
      if (/SELECT id,hex_id/i.test(sql)) return { rows: responses.offers || [] };
      return { rowCount: 0, rows: [] };
    },
    release() {},
  };
  return {
    state,
    async connect() { return client; },
    async query(sql, params) { return client.query(sql, params); },
  };
}

test("offer actions are atomic and map a successful claim", async () => {
  const pool = fakePool({});
  const result = await clock.action(pool, { type: "claim-order", id: "o1", ownerId: "p1" });
  assert.equal(result.ok, true);
  assert.equal(result.order.taken, true);
  assert.equal(result.order.claimed_by, "p1");
  assert.ok(pool.state.queries.some((q) => /pg_advisory_xact_lock/i.test(q.sql)));
  assert.ok(pool.state.queries.some((q) => /COMMIT/i.test(q.sql)));
});

test("pollination claims bind the exact hex, band, flora and start slot", async () => {
  const pool = fakePool({});
  const result = await clock.action(pool, {
    type: "claim-pollination-offer",
    hexId: "hex-1",
    band: 2,
    flora: "Campo de naranjos",
    startDoy: 120,
    ownerId: "p1",
  });
  assert.equal(result.ok, true);
  const select = pool.state.queries.find((q) =>
    /SELECT id,taken,claimed_by FROM pollination_offers/i.test(q.sql));
  assert.deepEqual(select.params.slice(0, 4), ["hex-1", 2, "Campo de naranjos", 120]);
  assert.equal(select.params[5], "p1");
});

test("an offer can be claimed by hex even if the local cache is stale", async () => {
  const pool = fakePool({});
  const result = await clock.action(pool, {
    type: "take-offer-by-hex", hexId: "hex-1", band: 2, ownerId: "p1",
  });
  assert.equal(result.ok, true);
  assert.equal(result.offerId, "p1");
  const select = pool.state.queries.find((q) => /SELECT id FROM pollination_offers/i.test(q.sql));
  assert.equal(select.params[0], "hex-1");
  assert.equal(select.params[2], 2);
});

test("offer snapshot uses the camel-case contract consumed by the app", async () => {
  const pool = fakePool({
    orders: [{
      id: "o1", npc_name: "Núria Soler", portrait_index: 0,
      flora_key: "Mil flores", kg: "1.2", unit_price: "5.04",
      dest_hex_id: "hex_iberia_1", dest_lat: 40, dest_lng: -3,
      dest_label: "Centro", region: "iberia", created_day_key: 20260924,
      expire_epoch_ms: "123", taken: false, claimed_by: null, band: 0,
    }],
    offers: [{
      id: "p1", hex_id: "hex_iberia_1", flora: "Campo de naranjos",
      start_doy: 100, end_doy: 120, band: 2, region: "iberia",
      created_day_key: 20260924, expire_epoch_ms: "123", dest_lat: 40,
      dest_lng: -3, npc_name: "Núria Soler", portrait_index: 0, taken: false,
    }],
  });
  const result = await clock.snapshot(pool, "iberia");
  assert.equal(result.ready, false);
  assert.equal(result.orders[0].npcName, "Núria Soler");
  assert.equal(result.orders[0].destHexId, "hex_iberia_1");
  assert.equal(result.offers[0].startDoy, 100);
});
