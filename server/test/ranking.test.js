"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const { rankRows, scoreOf, nextNetWorth } = require("../src/ranking");

test("la miel por flora no usa el total", () => {
  const person = {
    honeySoldKgTotal: 40,
    honeySoldByFlora: JSON.stringify({ romero: 12.5, azahar: 3 }),
  };
  assert.equal(scoreOf(person, "honeySold", ""), 40);
  assert.equal(scoreOf(person, "honeySold", "romero"), 12.5);
  assert.equal(scoreOf(person, "honeySold", "tomillo"), 0);
});

test("las abejas salen de las colmenas del servidor si existen", () => {
  assert.equal(scoreOf({ serverHives: 2, serverBees: 800, adultBeeCount: 10 }, "bees", ""), 800);
  assert.equal(scoreOf({ serverHives: 0, serverBees: 0, adultBeeCount: 10 }, "bees", ""), 10);
});

test("el top incluye al propio jugador aunque quede fuera", () => {
  const people = [];
  for (let i = 0; i < 101; i++) {
    people.push({
      id: "p" + i,
      playerName: "Jugador " + i,
      honeyBrand: "Marca",
      ordersDelivered: 200 - i,
    });
  }
  people.push({
    id: "nuria",
    playerName: "Nuria",
    honeyBrand: "Nuria",
    ordersDelivered: 1,
  });
  const rows = rankRows(people, "orders", "", "nuria");
  assert.equal(rows.length, 101);
  assert.equal(rows[100].id, "nuria");
  assert.equal(rows[100].isSelf, true);
  assert.equal(rows[100].rank, 102);
});

test("un teléfono vacío no borra el patrimonio ya publicado", () => {
  assert.equal(nextNetWorth(2000, { netWorthB: 0 }), 2000);
  assert.equal(nextNetWorth(2000, { netWorthB: 0, assetsKnown: true }), 0);
  assert.equal(nextNetWorth(2000, { netWorthB: 3500, assetsKnown: true }), 3500);
  assert.equal(nextNetWorth(2000, {}), 2000);
});

test("un cero no entra salvo patrimonio o el propio jugador", () => {
  const rows = rankRows([
    { id: "a", playerName: "Ana", honeyBrand: "A", ordersDelivered: 0 },
    { id: "b", playerName: "Bea", honeyBrand: "B", ordersDelivered: 2 },
    { id: "me", playerName: "Nuria", honeyBrand: "N", ordersDelivered: 0 },
  ], "orders", "", "me");
  assert.deepEqual(rows.map((row) => row.id), ["b", "me"]);
});
