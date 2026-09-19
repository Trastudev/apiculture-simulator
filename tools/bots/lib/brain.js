"use strict";

/**
 * Cerebro de un jugador bueno: produce, cosecha, vende al mayor,
 * instala apiarios, compra colmenas con alzas y mueve a hexes más
 * productivos. No acepta comandas ni contratos de polinización.
 */

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
  floraValue,
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

function cash(bot) {
  return bot.balanceEur || 0;
}

function hexFloraOf(bot, hexId) {
  bot.hexFlora = bot.hexFlora || {};
  if (bot.hexFlora[hexId]) return bot.hexFlora[hexId];
  const fromHive = (bot.hives || []).find((h) => h.hexId === hexId && h.floraType);
  if (fromHive) {
    bot.hexFlora[hexId] = fromHive.floraType;
    return fromHive.floraType;
  }
  return (bot.homeFloras && bot.homeFloras[0]) || "Mil flores";
}

function scoreFlora(flora) {
  return floraValue(flora);
}

function scoreHex(bot, hexId) {
  const flora = hexFloraOf(bot, hexId);
  const n = hivesOnHex(bot, hexId);
  const sat = n / Math.max(1, RULES.MAX_HIVES_PER_HEX);
  return scoreFlora(flora) * (1.15 - sat * 0.55);
}

function bestOwnedHex(bot) {
  let best = null;
  let bestScore = -1;
  for (const hexId of bot.ownedHexIds || []) {
    const s = scoreHex(bot, hexId);
    if (s > bestScore) {
      bestScore = s;
      best = hexId;
    }
  }
  return best;
}

function ensureBotShape(bot) {
  bot.ownedHexIds = bot.ownedHexIds || [];
  bot.hives = bot.hives || [];
  bot.honeyByFlora = bot.honeyByFlora || {};
  bot.honeySoldByFlora = bot.honeySoldByFlora || {};
  bot.honeySoldKgTotal = bot.honeySoldKgTotal || 0;
  bot.hexFlora = bot.hexFlora || {};
  bot.apiarySites = bot.apiarySites || {};
  bot.warehouseHexIds = bot.warehouseHexIds || [];
  bot.persona = bot.persona || personaForBotId(bot.id).id;
  bot.missedDays = bot.missedDays || 0;
  bot.hiveCount = bot.hives.length || bot.hiveCount || 0;
  bot.adultBeeCount = bot.adultBeeCount || 0;
  if (!bot.hives.length && bot.hiveCount > 0 && bot.ownedHexIds.length) {
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
  const site = (bot.apiarySites && bot.apiarySites[hexId]) || null;
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
    lat: site ? site.lat : 0,
    lng: site ? site.lng : 0,
    needsPublish: true,
  };
}

function hivesOnHex(bot, hexId) {
  return (bot.hives || []).filter((h) => h.hexId === hexId).length;
}

function totalHives(bot) {
  return (bot.hives || []).length;
}

function pickBestFlora(bot, rng) {
  const floras = affordableFloraForLevel(bot.level, bot.homeFloras);
  if (!floras.length) return "Mil flores";
  let best = floras[0];
  let bestV = scoreFlora(best);
  for (const f of floras) {
    const v = scoreFlora(f) + rng() * 0.12;
    if (v > bestV) {
      bestV = v;
      best = f;
    }
  }
  return best;
}

function plannedSupers(bot, persona, rng) {
  if ((bot.level || 0) >= 4 && cash(bot) > hivePrice(2) + persona.reserveEur * 1.4 && rng() < 0.45) {
    return 2;
  }
  if ((bot.level || 0) >= 1 && cash(bot) > hivePrice(1) + persona.reserveEur && rng() < 0.7) {
    return 1;
  }
  return 0;
}

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

  // 1) Producción diaria (más miel si la flora vale más y hay alzas).
  for (const hive of bot.hives) {
    const cap = honeyCap(hive.superCount);
    const floraMult = scoreFlora(hive.floraType) / 15.6;
    const base = (0.42 + rng() * 1.55) * persona.productionMult * floraMult;
    const beesFactor = Math.min(1.28, (hive.beeCount || RULES.STARTER_BEES) / 30000);
    let gain = round2(base * beesFactor);
    const room = Math.max(0, cap - (hive.honeyProduction || 0));
    gain = Math.min(gain, room);
    if (gain > 0.01) {
      hive.honeyProduction = round2((hive.honeyProduction || 0) + gain);
      hive.needsPublish = true;
      actions.push({ type: "produce", hiveId: hive.id, flora: hive.floraType, kg: gain });
    }
    const delta = Math.floor((rng() - 0.4) * 420);
    hive.beeCount = Math.max(8000, Math.min(80000, (hive.beeCount || RULES.STARTER_BEES) + delta));
  }

  // 2) Cosecha (jugador bueno: no deja llenar del todo).
  for (const hive of bot.hives) {
    const cap = honeyCap(hive.superCount);
    const stock = hive.honeyProduction || 0;
    const nearFull = stock >= cap * 0.58;
    const hasSpare = stock >= Math.max(1.6, persona.sellThresholdKg);
    if (!nearFull && !hasSpare) continue;
    const keep = Math.min(1.2, stock * 0.12);
    const harvest = round2(Math.max(0, stock - keep));
    if (harvest < 0.35) continue;
    hive.honeyProduction = round2(stock - harvest);
    hive.needsPublish = true;
    const flora = hive.floraType || "Mil flores";
    bot.honeyByFlora[flora] = round2((bot.honeyByFlora[flora] || 0) + harvest);
    addXp(bot, Math.max(RULES.XP_HARVEST_PER_KG, Math.round(harvest * RULES.XP_HARVEST_PER_KG)));
    actions.push({ type: "harvest", hiveId: hive.id, flora, kg: harvest });
  }

  // 3) Venta al mayor (nunca comandas).
  for (const [flora, stock] of Object.entries(bot.honeyByFlora)) {
    if (stock < persona.sellThresholdKg) continue;
    const [lo, hi] = persona.sellFraction;
    const frac = lo + rng() * (hi - lo);
    const sellKg = round2(stock * frac);
    if (sellKg < 0.4) continue;
    const price = sellPriceEurPerKg(flora, dayKey + bot.id);
    bot.honeyByFlora[flora] = round2(stock - sellKg);
    bot.balanceEur = round2(cash(bot) + sellKg * price);
    bot.honeySoldByFlora[flora] = round2((bot.honeySoldByFlora[flora] || 0) + sellKg);
    bot.honeySoldKgTotal = round2((bot.honeySoldKgTotal || 0) + sellKg);
    actions.push({ type: "sell", flora, kg: sellKg, price, revenue: round2(sellKg * price) });
  }

  // 4) Alzas en las colmenas que más producen / más llenas.
  if (rng() < persona.buySuperChance) {
    const needy = [...bot.hives].sort(
      (a, b) =>
        (b.honeyProduction || 0) / honeyCap(b.superCount) -
        (a.honeyProduction || 0) / honeyCap(a.superCount)
    );
    let bought = 0;
    for (const hive of needy) {
      if ((hive.superCount || 0) >= RULES.MAX_SUPERS) continue;
      if ((hive.honeyProduction || 0) < honeyCap(hive.superCount) * 0.4 && bought > 0) continue;
      if (cash(bot) < RULES.SUPER_PRICE + persona.reserveEur) break;
      bot.balanceEur = round2(cash(bot) - RULES.SUPER_PRICE);
      hive.superCount = (hive.superCount || 0) + 1;
      hive.needsPublish = true;
      addXp(bot, RULES.XP_BUY_SUPER);
      actions.push({ type: "buy_super", hiveId: hive.id, supers: hive.superCount });
      bought += 1;
      if (persona.id === "casual") break;
      if (persona.id === "regular" && bought >= 2) break;
      if (bought >= 4) break;
    }
  }

  // 5) Mover colmenas a un apiario propio con mejor flora / menos saturación.
  if ((bot.ownedHexIds || []).length >= 2 && rng() < (persona.moveHiveChance || 0)) {
    const dest = bestOwnedHex(bot);
    if (dest) {
      const destScore = scoreHex(bot, dest);
      const destFlora = hexFloraOf(bot, dest);
      const movable = bot.hives
        .filter((h) => h.hexId !== dest && hivesOnHex(bot, dest) < RULES.MAX_HIVES_PER_HEX)
        .sort((a, b) => scoreHex(bot, a.hexId) - scoreHex(bot, b.hexId));
      const hive = movable[0];
      if (hive && destScore > scoreHex(bot, hive.hexId) + 0.08) {
        const from = hive.hexId;
        hive.hexId = dest;
        hive.floraType = destFlora;
        hive.needsPublish = true;
        actions.push({ type: "move_hive", hiveId: hive.id, from, to: dest, flora: destFlora });
      }
    }
  }

  // 6) Comprar colmenas (con alzas si el saldo da) en el mejor apiario con hueco.
  const wantHive =
    totalHives(bot) < persona.maxHivesTotal &&
    cash(bot) >= hivePrice(0) + persona.reserveEur &&
    rng() < persona.buyHiveChance;
  if (wantHive && (bot.ownedHexIds || []).length) {
    const hexes = [...bot.ownedHexIds].sort((a, b) => scoreHex(bot, b) - scoreHex(bot, a));
    let bought = 0;
    for (const hexId of hexes) {
      if (hivesOnHex(bot, hexId) >= persona.targetHivesPerHex) continue;
      if (hivesOnHex(bot, hexId) >= RULES.MAX_HIVES_PER_HEX) continue;
      const flora = hexFloraOf(bot, hexId);
      if (!isFloraUnlocked(flora, bot.level)) continue;
      const supers = plannedSupers(bot, persona, rng);
      const price = hivePrice(supers);
      if (cash(bot) < price + persona.reserveEur) {
        if (supers > 0 && cash(bot) >= hivePrice(0) + persona.reserveEur) {
          const cheap = hivePrice(0);
          bot.balanceEur = round2(cash(bot) - cheap);
          const hive = makeLocalHive(bot, hexId, flora, 0, bot.hives.length);
          bot.hives.push(hive);
          addXp(bot, hiveXp(0));
          actions.push({ type: "buy_hive", hexId, flora, supers: 0, price: cheap, hiveId: hive.id });
          bought += 1;
        }
        break;
      }
      bot.balanceEur = round2(cash(bot) - price);
      const hive = makeLocalHive(bot, hexId, flora, supers, bot.hives.length);
      bot.hives.push(hive);
      addXp(bot, hiveXp(supers));
      actions.push({ type: "buy_hive", hexId, flora, supers, price, hiveId: hive.id });
      bought += 1;
      if (persona.id === "casual") break;
      if (persona.id === "regular" && bought >= 2) break;
      if (bought >= 3) break;
    }
  }

  // 7) Instalar apiario nuevo (no comandas / no contratos). Elige flora cara asequible.
  const wantApiary =
    bot.ownedHexIds.length < persona.maxParcels &&
    (bot.ownedHexIds.length === 0 || rng() < persona.buyTerrainChance);
  if (wantApiary) {
    const flora = pickBestFlora(bot, rng);
    const price = terrainPrice(flora);
    const withWarehouse =
      rng() < (persona.buyWarehouseChance || 0) &&
      !(bot.warehouseHexIds || []).length &&
      cash(bot) >= price + RULES.WAREHOUSE_COST + persona.reserveEur;
    const total = price + (withWarehouse ? RULES.WAREHOUSE_COST : 0);
    if (cash(bot) >= total + Math.max(80, persona.reserveEur * 0.4)) {
      actions.push({
        type: "want_apiary",
        flora,
        price,
        withWarehouse,
        expandSecondary: persona.expandSecondaryRegion && bot.level >= RULES.CLIMATE_ZA_LEVEL,
      });
    }
  }

  // 8) Almacén aparte si ya hay apiario y aún no tiene (y no lo pide el apiario nuevo).
  const pendingWarehouse = actions.some((a) => a.type === "want_apiary" && a.withWarehouse);
  if (
    !pendingWarehouse &&
    (bot.ownedHexIds || []).length &&
    !(bot.warehouseHexIds || []).length &&
    rng() < (persona.buyWarehouseChance || 0) &&
    cash(bot) >= RULES.WAREHOUSE_COST + persona.reserveEur
  ) {
    const hexId = bestOwnedHex(bot) || bot.ownedHexIds[0];
    bot.balanceEur = round2(cash(bot) - RULES.WAREHOUSE_COST);
    bot.warehouseHexIds.push(hexId);
    actions.push({ type: "buy_warehouse", hexId, price: RULES.WAREHOUSE_COST });
  }

  bot.hiveCount = bot.hives.length;
  bot.adultBeeCount = bot.hives.reduce((s, h) => s + (h.beeCount || 0), 0);
  return { actions, persona, loggedIn: true };
}

function applyTerrainPurchase(bot, hexId, flora, price, extra) {
  extra = extra || {};
  bot.balanceEur = round2((bot.balanceEur || 0) - price);
  bot.ownedHexIds = bot.ownedHexIds || [];
  if (!bot.ownedHexIds.includes(hexId)) bot.ownedHexIds.push(hexId);
  bot.hexFlora = bot.hexFlora || {};
  bot.hexFlora[hexId] = flora || bot.hexFlora[hexId] || "Mil flores";
  if (extra.site) {
    bot.apiarySites = bot.apiarySites || {};
    bot.apiarySites[hexId] = extra.site;
  }
  if (extra.withWarehouse) {
    bot.warehouseHexIds = bot.warehouseHexIds || [];
    if (!bot.warehouseHexIds.includes(hexId)) bot.warehouseHexIds.push(hexId);
    bot.balanceEur = round2((bot.balanceEur || 0) - RULES.WAREHOUSE_COST);
  }
  addXp(bot, terrainXp(price));
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
  hexFloraOf,
  scoreHex,
};
