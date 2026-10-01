"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const catalog = require("../src/offerCatalog");
const clock = require("../src/offerClock");

const DAY_KEY = 20260924;
const NOW = Date.UTC(2026, 8, 24, 12, 0, 0);

test("the server ships the same three map overlays and climate buckets", () => {
  assert.deepEqual(catalog.stats(), { iberia: 8697, za: 14317, mdg: 6742 });
  const counts = (region) => catalog.getParcels(region).reduce((out, parcel) => {
    out[parcel.climate] = (out[parcel.climate] || 0) + 1;
    return out;
  }, {});
  assert.deepEqual(counts("iberia"), {
    ATLANTIC: 1134, CONTINENTAL: 4373, MOUNTAIN: 343,
    SOUTH: 1223, MEDITERRANEAN: 1624,
  });
  assert.deepEqual(counts("za"), {
    BUSHVELD: 4114, HIGHVELD: 5846, KAROO: 1535, FYNBOS: 729,
    SUBTROPICAL: 2093,
  });
  assert.deepEqual(counts("mdg"), {
    TROPICAL: 3821, EQUATORIAL: 1588, DESERT: 1333,
  });
});

test("offer bands keep the 2-per-50 order rule and climate gates", () => {
  const iberia = catalog.getParcels("iberia");
  const southAfrica = catalog.getParcels("za");
  const madagascar = catalog.getParcels("mdg");
  assert.equal(catalog.bandDailyOrderCount(49), 0);
  assert.equal(catalog.bandDailyOrderCount(50), 2);
  assert.equal(catalog.bandDailyOrderCount(1588), 63);
  assert.equal(southAfrica.filter((p) => catalog.orderEligible(p, 8)).length, 0);
  assert.equal(southAfrica.filter((p) => catalog.orderEligible(p, 9)).length, southAfrica.length);
  assert.equal(madagascar.filter((p) => catalog.orderEligible(p, 0)).length > 0, true);
  assert.equal(iberia.filter((p) => catalog.orderEligible(p, 0)).length > 0, true);
});

test("server scatter selection keeps low-level Madagascar offers spread out", () => {
  const eligible = catalog.getParcels("mdg")
    .filter((p) => catalog.orderEligible(p, 0));
  const picked = clock.pickScattered(eligible, 12, new Set(), "test-mdg");
  assert.equal(picked.length, 12);
  const lats = picked.map((p) => p.lat);
  assert.ok(Math.max(...lats) - Math.min(...lats) >= 2,
    `lat span ${Math.max(...lats) - Math.min(...lats)}`);
});

test("la comanda cae dentro del hexágono, no en el centro", () => {
  const parcel = catalog.getParcels("iberia").find((p) => p.ring && p.ring.length >= 6);
  assert.ok(parcel);
  const pins = new Set();
  for (let i = 0; i < 12; i++) {
    const pin = catalog.pointInParcel(parcel, "order-pin-" + i);
    assert.equal(catalog.ringContains(pin.lat, pin.lng, parcel.ring), true);
    pins.add(pin.lat.toFixed(5) + "," + pin.lng.toFixed(5));
  }
  assert.ok(pins.size > 1);
  const again = catalog.pointInParcel(parcel, "order-pin-0");
  const first = catalog.pointInParcel(parcel, "order-pin-0");
  assert.equal(again.lat, first.lat);
  assert.equal(again.lng, first.lng);
});

test("25 de septiembre: la comanda sale 75/15/10 según el clima del hexágono", () => {
  const now = Date.UTC(2026, 8, 25, 12, 0, 0);
  const parcel = catalog.getParcels("iberia").find((p) => p.climate === "MEDITERRANEAN");
  const pool = catalog.orderFloraCandidates(parcel, 9);
  const counts = { bloom: 0, edge: 0, out: 0 };
  const samples = 2000;
  for (let i = 0; i < samples; i++) {
    const flora = catalog.pickSeasonalFlora(parcel, pool, now, "sept:" + i);
    const height = catalog.bloomHeight(parcel, flora, 268);
    if (height >= 0.4) counts.bloom++;
    else if (height >= 0.15) counts.edge++;
    else counts.out++;
  }
  assert.ok(counts.bloom > counts.edge && counts.edge > counts.out);
  assert.ok(counts.bloom / samples > 0.68 && counts.bloom / samples < 0.82);
  assert.ok(counts.edge / samples > 0.08 && counts.edge / samples < 0.22);
  assert.ok(counts.out / samples > 0.04 && counts.out / samples < 0.16);
  const again = catalog.pickSeasonalFlora(parcel, pool, now, "sept:0");
  assert.equal(again, catalog.pickSeasonalFlora(parcel, pool, now, "sept:0"));
});

test("server offers use a future crop slot from the server catalog", () => {
  for (const region of ["iberia", "za", "mdg"]) {
    const parcels = catalog.getParcels(region);
    let checked = 0;
    for (const parcel of parcels) {
      for (let band = 0; band < 10 && checked < 3; band++) {
        const offer = catalog.offerForParcel(parcel, band, NOW, DAY_KEY);
        if (!offer) continue;
        assert.ok(offer.startDoy >= 1 && offer.startDoy <= 365);
        assert.ok(offer.endDoy >= 1 && offer.endDoy <= 365);
        assert.ok(offer.flora && offer.flora.length > 0);
        if (catalog.BAND_MIN_LEVEL[band] < 2) {
          assert.ok(catalog.cropAccessLevel(offer.flora) <= catalog.BAND_MIN_LEVEL[band]);
        }
        checked++;
        break;
      }
    }
    assert.ok(checked > 0, `no offer found for ${region}`);
  }
});
