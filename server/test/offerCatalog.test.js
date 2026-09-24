"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const catalog = require("../src/offerCatalog");
const clock = require("../src/offerClock");

const DAY_KEY = 20260924;
const NOW = Date.UTC(2026, 8, 24, 12, 0, 0);

test("the server ships the same three map overlays and climate buckets", () => {
  assert.deepEqual(catalog.stats(), { iberia: 10236, za: 14317, mdg: 6742 });
  const counts = (region) => catalog.getParcels(region).reduce((out, parcel) => {
    out[parcel.climate] = (out[parcel.climate] || 0) + 1;
    return out;
  }, {});
  assert.deepEqual(counts("iberia"), {
    ATLANTIC: 1137, CONTINENTAL: 4429, MOUNTAIN: 356,
    SOUTH: 1243, MEDITERRANEAN: 3071,
  });
  assert.deepEqual(counts("za"), {
    BUSHVELD: 5218, HIGHVELD: 6835, KAROO: 1535, FYNBOS: 729,
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
        assert.ok(catalog.cropAccessLevel(offer.flora) <= catalog.BAND_MIN_LEVEL[band]);
        checked++;
        break;
      }
    }
    assert.ok(checked > 0, `no offer found for ${region}`);
  }
});
