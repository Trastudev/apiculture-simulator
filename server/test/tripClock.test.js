"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const clock = require("../src/tripClock");

test("an order trip can land anywhere inside the same hex", () => {
  const row = {
    taken: true,
    claimed_by: "p1",
    flora_key: "Ravintsara",
    kg: 2.16,
    dest_hex_id: "hex_mdg_-2_56",
    dest_lat: -16.95,
    dest_lng: 46.82,
    unit_price: 18.4,
  };
  const ok = clock.publishedOrderMatches({
    kind: "order",
    orderId: "srv-ho-1",
    ownerId: "p1",
    floraKey: "Ravintsara",
    kg: 2.16,
    destHexId: "hex_mdg_-2_56",
    destLat: -16.91,
    destLng: 46.88,
    unitPrice: 18.4,
    cargoJson: JSON.stringify({ Ravintsara: 2.16 }),
  }, row);
  assert.equal(ok, true);
});

// Pool de mentira: devuelve el viaje y el store del tutorial que se le den.
function fakePool(trip, fastBody) {
  return {
    async query(sql) {
      if (sql.includes("FROM cargo_trips")) return { rowCount: trip ? 1 : 0, rows: trip ? [trip] : [] };
      if (sql.includes("FROM player_stores")) {
        return { rowCount: fastBody ? 1 : 0, rows: fastBody ? [{ body: fastBody }] : [] };
      }
      throw new Error("consulta inesperada: " + sql);
    },
  };
}

test("tutorial fast trip only works on the player's own trips", async () => {
  const pool = fakePool({ owner_id: "otro", kind: "collect" }, null);
  const r = await clock.tutorialFinish(pool, "p1", "t1", "collect");
  assert.equal(r.ok, false);
  assert.equal(r.error, "NOT_OWNER");
});

test("tutorial fast trip needs a trip of its group", async () => {
  const pool = fakePool({ owner_id: "p1", kind: "transfer" }, null);
  const r = await clock.tutorialFinish(pool, "p1", "t1", "sale");
  assert.equal(r.error, "WRONG_KIND");
});

test("tutorial fast trip can be used once per group", async () => {
  const pool = fakePool({ owner_id: "p1", kind: "order" }, JSON.stringify({ sale: true }));
  const r = await clock.tutorialFinish(pool, "p1", "t1", "sale");
  assert.equal(r.error, "USED");
});
