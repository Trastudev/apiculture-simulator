"use strict";

/**
 * Reglas de economía alineadas con FloraProgression / XpAwards / HexParcelGameRules.
 */

const FLORA_CEILINGS = {
  Neret: 16.2,
  Arboç: 16.12,
  Fynbos: 16.25,
  Litchi: 16.1,
  Macadamia: 15.98,
  "Mielato de encina y roble": 16.08,
  Lavanda: 16.0,
  Romero: 15.96,
  Tomillo: 15.93,
  "Campo de naranjos": 15.91,
  Castaño: 15.9,
  Bosque: 15.89,
  "Mil flores": 15.62,
  Eucalipto: 15.55,
  Aloe: 15.7,
  Acacia: 15.5,
  Lucerna: 15.42,
  Brezo: 15.52,
  "Campo de girasoles": 15.46,
  "Campo de Colza": 15.43,
  "Campo de manzanos": 15.4,
  "Campo de cerezos": 15.38,
  "Campo de perales": 15.35,
  "Campo de almendros": 15.33,
};

const FLORA_TYPES = Object.keys(FLORA_CEILINGS);

const UNLOCK_ORDER = (() => {
  const rest = FLORA_TYPES.filter((k) => k !== "Mil flores").sort(
    (a, b) => (FLORA_CEILINGS[b] || 0) - (FLORA_CEILINGS[a] || 0)
  );
  return ["Mil flores", ...rest];
})();

const RULES = {
  STARTING_BALANCE: 10000,
  TERRAIN_BASE: 1000,
  HIVE_PRICE: [200, 250, 300],
  SUPER_PRICE: 50,
  MAX_SUPERS: 2,
  MAX_HIVES_PER_HEX: 10,
  STARTER_HONEY_KG: 5,
  STARTER_BEES: 25000,
  SUPER_CAP_KG: [8, 30, 60],
  MIN_PRICE: 8,
  XP_HARVEST_PER_KG: 8,
  XP_BUY_HIVE_BASE: 40,
  XP_BUY_HIVE_PER_SUPER: 10,
  XP_BUY_TERRAIN_BASE: 80,
  XP_BUY_TERRAIN_PER_1000: 10,
  XP_BUY_SUPER: 15,
  WAREHOUSE_COST: 300,
  CLIMATE_ZA_LEVEL: 25,
};

function unlockIndex(flora) {
  const i = UNLOCK_ORDER.indexOf(flora);
  return i < 0 ? 0 : i;
}

function isFloraUnlocked(flora, level) {
  return unlockIndex(flora) <= Math.max(0, level | 0);
}

function terrainPrice(flora) {
  const idx = unlockIndex(flora);
  const premium = idx <= 0 ? 0 : 1000 * idx;
  return RULES.TERRAIN_BASE + premium;
}

function terrainXp(totalPrice) {
  const premiumThousands = Math.max(0, Math.floor((totalPrice - 1000) / 1000));
  return RULES.XP_BUY_TERRAIN_BASE + RULES.XP_BUY_TERRAIN_PER_1000 * premiumThousands;
}

function hivePrice(supers) {
  const s = Math.max(0, Math.min(2, supers | 0));
  return RULES.HIVE_PRICE[s];
}

function hiveXp(supers) {
  const s = Math.max(0, Math.min(2, supers | 0));
  return RULES.XP_BUY_HIVE_BASE + RULES.XP_BUY_HIVE_PER_SUPER * s;
}

function honeyCap(supers) {
  const s = Math.max(0, Math.min(2, supers | 0));
  return RULES.SUPER_CAP_KG[s];
}

function sellPriceEurPerKg(flora, daySeed) {
  const ceiling = FLORA_CEILINGS[flora] || 15.5;
  const mid = (RULES.MIN_PRICE + ceiling) / 2;
  const wobble = ((daySeed % 17) - 8) * 0.08;
  return Math.max(RULES.MIN_PRICE, Math.round((mid + wobble) * 100) / 100);
}

function xpForNextLevel(level) {
  const raw = 100 * Math.pow(1.12, Math.max(0, level));
  return Math.min(2000000, Math.max(1, Math.round(raw)));
}

function addXp(bot, amount) {
  if (!(amount > 0)) return;
  bot.xp = (bot.xp || 0) + amount;
  bot.level = bot.level || 0;
  while (bot.xp >= xpForNextLevel(bot.level)) {
    bot.xp -= xpForNextLevel(bot.level);
    bot.level += 1;
  }
}

function floraValue(flora) {
  return FLORA_CEILINGS[flora] || 15.5;
}

function isZaHex(hexId) {
  const id = String(hexId || "");
  return id.includes("_za_") || id.startsWith("za_") || id.startsWith("hex_za_");
}

function canUseHex(bot, hexId) {
  if (!isZaHex(hexId)) return true;
  if (bot && bot.timeZoneId && String(bot.timeZoneId).startsWith("Africa")) return true;
  return (bot.level || 0) >= RULES.CLIMATE_ZA_LEVEL;
}

function affordableFloraForLevel(level, preferred) {
  const list = [];
  for (const f of preferred || []) {
    if (isFloraUnlocked(f, level)) list.push(f);
  }
  if (!list.length && isFloraUnlocked("Mil flores", level)) list.push("Mil flores");
  return list;
}

module.exports = {
  FLORA_CEILINGS,
  FLORA_TYPES,
  UNLOCK_ORDER,
  RULES,
  unlockIndex,
  isFloraUnlocked,
  terrainPrice,
  terrainXp,
  hivePrice,
  hiveXp,
  honeyCap,
  sellPriceEurPerKg,
  xpForNextLevel,
  addXp,
  affordableFloraForLevel,
  floraValue,
  isZaHex,
  canUseHex,
};
