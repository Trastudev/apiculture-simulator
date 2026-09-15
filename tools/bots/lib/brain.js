"use strict";

const {
  RULES,
  terrainPrice,
  terrainXp,
  hivePrice,
  hiveXp,
  honeyCap,
  sellPriceEurPerKg,
  addXp,
  affordableFloraForLevel,
  isFloraUnlocked,
} = require("./rules");
const { personaForBotId, shouldLoginToday } = require("./personas");

function rngFrom(seed) {
  let s = (seed >>> 0) || 1;
  return () => {
    s = (s * 1664525 + 1013904223) >>> 0;
    return s / 0x100000000;
  };
}

function round2(n) {
  return Math.round(n * 100) / 100;
}

function ensureBotShape(bot) {
  bot.ownedHexIds = bot.ownedHexIds || [];
  bot.hives = bot.hives || [];
  bot.honeyByFlora = bot.honeyByFlora || {};
  bot.honeySoldByFlora = bot.honeySoldByFlora || {};
  bot.honeySoldKgTotal = bot.honeySoldKgTotal || 0;
  bot.persona = bot.persona || personaForBotId(bot.id).id;
  bot.missedDays = bot.missedDays || 0;
  bot.hiveCount = bot.hives.length || bot.hiveCount || 0;
  bot.adultBeeCount = bot.adultBeeCount || 0;
  if (!bot.hives.length && bot.hiveCount > 0 && bot.ownedHexIds.length) {
    // Migración: materializa colmenas locales a partir del contador antiguo.
    const flora = (bot.homeFloras && bot.homeFloras[0]) || "Mil flores";
    const hex = bot.ownedHexIds[0];
    for (let i = 0; i < bot.hiveCount; i++) {
      bot.hives.push(makeLocalHive(bot, hex, flora, 0, i));
    }
  }
  return bot;
}

function makeLocalHive(bot, hexId, flora, supers, idx) {
  const id =
    bot.hives && bot.hives[idx] && bot.hives[idx].id
      ? bot.hives[idx].id
      : `bot_${bot.uid || bot.id}_${hexId}_${Date.now()}_${idx}_${Math.floor(Math.random() * 1e6)}`;
  return {
    id,
    hexId,
    floraType: flora,
    name: `Colmena ${(bot.playerName || "Bot").split(" ")[0]} ${idx + 1}`,
    superCount: supers | 0,
    honeyProduction: RULES.STARTER_HONEY_KG,
    beeCount: RULES.STARTER_BEES,
    health: 80 + Math.floor(Math.random() * 15),
    varroaPct: round2(1 + Math.random() * 3),
    needsPublish: true,
  };
}

function hivesOnHex(bot, hexId) {
  return (bot.hives || []).filter((h) => h.hexId === hexId).length;
}

function totalHives(bot) {
  return (bot.hives || []).length;
}

/**
 * Un día de decisiones autónomas (sin I/O). Devuelve lista de acciones.
 * El caller aplica persistencia Firestore si live=true.
 */
function planDay(bot, dayKey, opts) {
  ensureBotShape(bot);
  const persona = personaForBotId(bot.id);
  bot.persona = persona.id;
  const rng = rngFrom((dayKey | 0) * 10007 + (bot.id | 0) * 97 + 13);
  const actions = [];

  if (!shouldLoginToday(bot, persona, rng)) {
    bot.missedDays = (bot.missedDays || 0) + 1;
    actions.push({ type: "skip", reason: "no_login" });
    return { actions, persona, loggedIn: false };
  }
  bot.missedDays = 0;

  // 1) Producción en colmenas
  for (const hive of bot.hives) {
    const cap = honeyCap(hive.superCount);
    const base = (0.35 + rng() * 1.4) * persona.productionMult;
    const beesFactor = Math.min(1.25, (hive.beeCount || RULES.STARTER_BEES) / 30000);
    let gain = round2(base * beesFactor);
    const room = Math.max(0, cap - (hive.honeyProduction || 0));
    gain = Math.min(gain, room);
    if (gain > 0.01) {
      hive.honeyProduction = round2((hive.honeyProduction || 0) + gain);
      actions.push({ type: "produce", hiveId: hive.id, flora: hive.floraType, kg: gain });
    }
    // deriva leve de población
    const delta = Math.floor((rng() - 0.42) * 400);
    hive.beeCount = Math.max(5000, Math.min(80000, (hive.beeCount || RULES.STARTER_BEES) + delta));
  }

  // 2) Cosecha cuando cerca del tope o stock alto
  for (const hive of bot.hives) {
    const cap = honeyCap(hive.superCount);
    const stock = hive.honeyProduction || 0;
    const nearFull = stock >= cap * 0.72;
    const hasSpare = stock >= Math.max(2, persona.sellThresholdKg);
    if (!nearFull && !hasSpare) continue;
    const keep = Math.min(RULES.STARTER_HONEY_KG * 0.4, stock * 0.15);
    const harvest = round2(Math.max(0, stock - keep));
    if (harvest < 0.4) continue;
    hive.honeyProduction = round2(stock - harvest);
    const flora = hive.floraType || "Mil flores";
    bot.honeyByFlora[flora] = round2((bot.honeyByFlora[flora] || 0) + harvest);
    addXp(bot, Math.max(RULES.XP_HARVEST_PER_KG, Math.round(harvest * RULES.XP_HARVEST_PER_KG)));
    actions.push({ type: "harvest", hiveId: hive.id, flora, kg: harvest });
  }

  // 3) Venta a mercado
  for (const [flora, stock] of Object.entries(bot.honeyByFlora)) {
    if (stock < persona.sellThresholdKg) continue;
    const [lo, hi] = persona.sellFraction;
    const frac = lo + rng() * (hi - lo);
    const sellKg = round2(stock * frac);
    if (sellKg < 0.5) continue;
    const price = sellPriceEurPerKg(flora, dayKey + bot.id);
    bot.honeyByFlora[flora] = round2(stock - sellKg);
    bot.balanceEur = round2((bot.balanceEur || 0) + sellKg * price);
    bot.honeySoldByFlora = bot.honeySoldByFlora || {};
    bot.honeySoldByFlora[flora] = round2((bot.honeySoldByFlora[flora] || 0) + sellKg);
    bot.honeySoldKgTotal = round2((bot.honeySoldKgTotal || 0) + sellKg);
    actions.push({ type: "sell", flora, kg: sellKg, price, revenue: round2(sellKg * price) });
  }

  // 4) Comprar alzas en colmenas casi llenas
  if (rng() < persona.buySuperChance) {
    for (const hive of bot.hives) {
      if ((hive.superCount || 0) >= RULES.MAX_SUPERS) continue;
      if ((hive.honeyProduction || 0) < honeyCap(hive.superCount) * 0.55) continue;
      if ((bot.balanceEur || 0) < RULES.SUPER_PRICE + persona.reserveEur) break;
      bot.balanceEur = round2(bot.balanceEur - RULES.SUPER_PRICE);
      hive.superCount = (hive.superCount || 0) + 1;
      hive.needsPublish = true;
      addXp(bot, RULES.XP_BUY_SUPER);
      actions.push({ type: "buy_super", hiveId: hive.id, supers: hive.superCount });
      if (persona.id !== "competitive") break;
    }
  }

  // 5) Comprar colmenas en terrenos con hueco
  const canBuyHive =
    totalHives(bot) < persona.maxHivesTotal &&
    (bot.balanceEur || 0) >= hivePrice(0) + persona.reserveEur &&
    rng() < persona.buyHiveChance;
  if (canBuyHive) {
    const hexes = [...bot.ownedHexIds].sort(
      (a, b) => hivesOnHex(bot, a) - hivesOnHex(bot, b)
    );
    for (const hexId of hexes) {
      if (hivesOnHex(bot, hexId) >= persona.targetHivesPerHex) continue;
      if (hivesOnHex(bot, hexId) >= RULES.MAX_HIVES_PER_HEX) continue;
      const floras = affordableFloraForLevel(bot.level, bot.homeFloras);
      const flora = floras[Math.floor(rng() * floras.length)] || "Mil flores";
      const supers = persona.id === "competitive" && bot.level >= 2 && rng() < 0.35 ? 1 : 0;
      const price = hivePrice(supers);
      if ((bot.balanceEur || 0) < price + persona.reserveEur) break;
      bot.balanceEur = round2(bot.balanceEur - price);
      const hive = makeLocalHive(bot, hexId, flora, supers, bot.hives.length);
      bot.hives.push(hive);
      addXp(bot, hiveXp(supers));
      actions.push({ type: "buy_hive", hexId, flora, supers, price, hiveId: hive.id });
      if (persona.id === "casual") break;
      if (persona.id === "regular" && actions.filter((a) => a.type === "buy_hive").length >= 2) break;
      if (actions.filter((a) => a.type === "buy_hive").length >= 3) break;
    }
  }

  // 6) Comprar terreno (solo planifica; el caller elige hex libre)
  const wantTerrain =
    bot.ownedHexIds.length < persona.maxParcels &&
    (bot.ownedHexIds.length === 0 || rng() < persona.buyTerrainChance);
  if (wantTerrain) {
    const floras = affordableFloraForLevel(bot.level, bot.homeFloras);
    // Elige la flora más barata asequible para no quedarse sin liquidez
    let best = null;
    for (const f of floras) {
      const price = terrainPrice(f);
      if ((bot.balanceEur || 0) < price + persona.reserveEur) continue;
      if (!best || price < best.price) best = { flora: f, price };
    }
    // Competitivos a veces pagan prima por flora preferida desbloqueada
    if (persona.id === "competitive" && floras.length) {
      const pref = bot.homeFloras.find((f) => isFloraUnlocked(f, bot.level)) || floras[0];
      const price = terrainPrice(pref);
      if ((bot.balanceEur || 0) >= price + persona.reserveEur) {
        best = { flora: pref, price };
      }
    }
    if (best) {
      actions.push({
        type: "want_terrain",
        flora: best.flora,
        price: best.price,
        expandSecondary: persona.expandSecondaryRegion && bot.ownedHexIds.length >= 2,
      });
    }
  }

  bot.hiveCount = bot.hives.length;
  bot.adultBeeCount = bot.hives.reduce((s, h) => s + (h.beeCount || 0), 0);
  return { actions, persona, loggedIn: true };
}

function applyTerrainPurchase(bot, hexId, flora, price) {
  bot.balanceEur = round2((bot.balanceEur || 0) - price);
  bot.ownedHexIds = bot.ownedHexIds || [];
  if (!bot.ownedHexIds.includes(hexId)) bot.ownedHexIds.push(hexId);
  addXp(bot, terrainXp(price));
  // Primera colmena gratis de arranque solo si no tenía ninguna
  if (!bot.hives.length) {
    const hive = makeLocalHive(bot, hexId, flora, 0, 0);
    bot.hives.push(hive);
    bot.hiveCount = 1;
    bot.adultBeeCount = hive.beeCount;
  }
}

module.exports = {
  ensureBotShape,
  planDay,
  applyTerrainPurchase,
  makeLocalHive,
  hivesOnHex,
  totalHives,
  rngFrom,
  round2,
};
