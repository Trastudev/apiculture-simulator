"use strict";

/**
 * Tres arquetipos inspirados en bots de idle/strategy (Civ AI, Farmville bots, Clash NPC):
 * - casual: irregular, poco agresivo, solo región natal
 * - regular: ritmo humano tipico
 * - competitive: maximiza expansión y ventas
 */

const PERSONAS = {
  casual: {
    id: "casual",
    label: "Poco activo",
    /** Probabilidad de “conectarse” un día concreto. */
    loginChance: 0.38,
    /** Si no entró ayer, bonus de login (vuelve cada 2–3 días). */
    skipBonusAfterMiss: 0.22,
    maxParcels: 6,
    maxHivesTotal: 18,
    targetHivesPerHex: 4,
    buyHiveChance: 0.42,
    buyTerrainChance: 0.22,
    buySuperChance: 0.22,
    buyWarehouseChance: 0.12,
    moveHiveChance: 0.08,
    sellThresholdKg: 3,
    sellFraction: [0.45, 0.65],
    expandSecondaryRegion: false,
    reserveEur: 500,
    productionMult: 0.88,
  },
  regular: {
    id: "regular",
    label: "Activo",
    loginChance: 0.72,
    skipBonusAfterMiss: 0.15,
    maxParcels: 12,
    maxHivesTotal: 40,
    targetHivesPerHex: 7,
    buyHiveChance: 0.7,
    buyTerrainChance: 0.48,
    buySuperChance: 0.4,
    buyWarehouseChance: 0.28,
    moveHiveChance: 0.18,
    sellThresholdKg: 1.5,
    sellFraction: [0.6, 0.82],
    expandSecondaryRegion: false,
    reserveEur: 350,
    productionMult: 1.0,
  },
  competitive: {
    id: "competitive",
    label: "Competitivo",
    loginChance: 0.94,
    skipBonusAfterMiss: 0.05,
    maxParcels: 28,
    maxHivesTotal: 90,
    targetHivesPerHex: 10,
    buyHiveChance: 0.9,
    buyTerrainChance: 0.72,
    buySuperChance: 0.7,
    buyWarehouseChance: 0.45,
    moveHiveChance: 0.28,
    sellThresholdKg: 0.8,
    sellFraction: [0.75, 0.95],
    expandSecondaryRegion: true,
    reserveEur: 400,
    productionMult: 1.12,
  },
};

/** Asigna arquetipo estable por id de bot (1–20). */
function personaForBotId(id) {
  const n = Number(id) || 1;
  // 7 casual, 7 regular, 6 competitive
  if (n % 3 === 1) return PERSONAS.casual;
  if (n % 3 === 2) return PERSONAS.regular;
  return PERSONAS.competitive;
}

function shouldLoginToday(bot, persona, rng) {
  const p = persona || PERSONAS.regular;
  let chance = p.loginChance;
  if (bot.missedDays >= 2) chance = Math.min(0.98, chance + p.skipBonusAfterMiss * bot.missedDays);
  return rng() < chance;
}

module.exports = {
  PERSONAS,
  personaForBotId,
  shouldLoginToday,
};
