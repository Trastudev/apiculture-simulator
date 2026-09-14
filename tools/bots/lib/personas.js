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
    maxHivesTotal: 8,
    targetHivesPerHex: 3,
    buyHiveChance: 0.28,
    buyTerrainChance: 0.18,
    buySuperChance: 0.08,
    sellThresholdKg: 4,
    sellFraction: [0.35, 0.55],
    expandSecondaryRegion: false,
    reserveEur: 400,
    productionMult: 0.85,
  },
  regular: {
    id: "regular",
    label: "Activo",
    loginChance: 0.72,
    skipBonusAfterMiss: 0.15,
    maxParcels: 10,
    maxHivesTotal: 24,
    targetHivesPerHex: 6,
    buyHiveChance: 0.55,
    buyTerrainChance: 0.4,
    buySuperChance: 0.25,
    sellThresholdKg: 2,
    sellFraction: [0.5, 0.75],
    expandSecondaryRegion: false,
    reserveEur: 250,
    productionMult: 1.0,
  },
  competitive: {
    id: "competitive",
    label: "Competitivo",
    loginChance: 0.94,
    skipBonusAfterMiss: 0.05,
    maxParcels: 30,
    maxHivesTotal: 60,
    targetHivesPerHex: 10,
    buyHiveChance: 0.85,
    buyTerrainChance: 0.7,
    buySuperChance: 0.55,
    sellThresholdKg: 1,
    sellFraction: [0.7, 0.95],
    expandSecondaryRegion: true,
    reserveEur: 150,
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
