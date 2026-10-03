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
