"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const clock = require("../src/offerClock");

function memoryClient(orders, jobs) {
  return {
    async query(sql, params) {
      if (/INSERT INTO honey_order_expirations/i.test(sql) && /VALUES \(\$1/i.test(sql)) {
        if (!jobs.has(params[0])) {
          jobs.set(params[0], { due: params[1], state: "pending" });
        }
        return { rowCount: 1, rows: [] };
      }
      if (/UPDATE honey_order_expirations/i.test(sql)) {
        const job = jobs.get(params[0]);
        if (!job || job.state !== "pending") return { rowCount: 0, rows: [] };
        job.state = params[1];
        return { rowCount: 1, rows: [{ order_id: params[0] }] };
      }
      if (/DELETE FROM honey_orders/i.test(sql)) {
        const existed = orders.delete(params[0]);
        return { rowCount: existed ? 1 : 0, rows: [] };
      }
      if (/server_market_prices/i.test(sql)) return { rowCount: 0, rows: [] };
      if (/SELECT o\.\*/i.test(sql)) {
        const now = params[0];
        const rows = [];
        for (const [id, order] of orders) {
          const job = jobs.get(id);
          if (job && job.state === "pending" && job.due <= now) rows.push(order);
        }
        rows.sort((a, b) => String(a.id).localeCompare(String(b.id)));
        return { rowCount: rows.length, rows };
      }
      if (/pg_advisory_xact_lock|BEGIN|COMMIT|ROLLBACK/i.test(sql)) {
        return { rowCount: 0, rows: [] };
      }
      return { rowCount: 0, rows: [] };
    },
    release() {},
  };
}

function openOrder(id, due) {
  return {
    id,
    region: "iberia",
    band: 0,
    dest_lat: 0,
    dest_lng: 0,
    dest_hex_id: "hex",
    expire_epoch_ms: due,
    taken: true,
    claimed_by: "p1",
  };
}

test("cobrar una comanda la cierra una sola vez", async () => {
  const due = Date.now() + 60_000;
  const orders = new Map([["o1", openOrder("o1", due)]]);
  const jobs = new Map();
  const client = memoryClient(orders, jobs);
  const first = await clock.closeOrder(client, orders.get("o1"), Date.now(), "satisfied");
  const second = await clock.closeOrder(client, openOrder("o1", due), Date.now(), "satisfied");
  assert.equal(first.payable, true);
  assert.equal(first.closed, true);
  assert.equal(second.closed, false);
  assert.equal(second.payable, false);
  assert.equal(jobs.get("o1").state, "satisfied");
  assert.equal(orders.has("o1"), false);
});

test("una comanda vencida no se cobra y el reintento no abre otra", async () => {
  const due = Date.now() - 1000;
  const orders = new Map([["o2", openOrder("o2", due)]]);
  const jobs = new Map([["o2", { due, state: "pending" }]]);
  const client = memoryClient(orders, jobs);
  const now = Date.now();
  const first = await clock.closeOrder(client, orders.get("o2"), now, "satisfied");
  const second = await clock.closeOrder(client, openOrder("o2", due), now, "expired");
  assert.equal(first.payable, false);
  assert.equal(first.closed, true);
  assert.equal(jobs.get("o2").state, "expired");
  assert.equal(second.closed, false);
});

test("el vencimiento programado cierra solo las comandas debidas", async () => {
  const now = Date.now();
  const dueOrder = openOrder("due", now - 5);
  const liveOrder = openOrder("live", now + 60_000);
  const orders = new Map([["due", dueOrder], ["live", liveOrder]]);
  const jobs = new Map([
    ["due", { due: dueOrder.expire_epoch_ms, state: "pending" }],
    ["live", { due: liveOrder.expire_epoch_ms, state: "pending" }],
  ]);
  const client = memoryClient(orders, jobs);
  const pool = {
    async connect() { return client; },
  };
  const closed = await clock.expireDue(pool, now);
  const again = await clock.expireDue(pool, now);
  assert.equal(closed, 1);
  assert.equal(again, 0);
  assert.equal(orders.has("due"), false);
  assert.equal(orders.has("live"), true);
  assert.equal(jobs.get("due").state, "expired");
  assert.equal(jobs.get("live").state, "pending");
});

test("aceptar una comanda no inserta otra oferta", async () => {
  const queries = [];
  const client = {
    async query(sql, params) {
      queries.push(sql);
      if (/SELECT \* FROM honey_orders WHERE id/i.test(sql)) {
        return {
          rowCount: 1,
          rows: [{ id: params[0], taken: false, claimed_by: null, expire_epoch_ms: Date.now() + 1000 }],
        };
      }
      if (/UPDATE honey_orders SET taken=true/i.test(sql)) {
        return { rowCount: 1, rows: [{ id: params[0], taken: true, claimed_by: params[1], expire_epoch_ms: Date.now() + 1000 }] };
      }
      if (/pg_advisory_xact_lock|BEGIN|COMMIT/i.test(sql)) return { rowCount: 0, rows: [] };
      return { rowCount: 0, rows: [] };
    },
    release() {},
  };
  const result = await clock.action({ async connect() { return client; } }, {
    type: "claim-order", id: "o1", ownerId: "p1",
  });
  assert.equal(result.ok, true);
  assert.equal(queries.some((sql) => /INSERT INTO honey_orders/i.test(sql)), false);
});
