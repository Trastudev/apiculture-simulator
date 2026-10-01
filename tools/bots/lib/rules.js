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
  WAREHOUSE_UPGRADE: 250,
  LAND_BASE: 1000,
};

function unlockIndex(flora) {
  const i = UNLOCK_ORDER.indexOf(flora);
  return i < 0 ? 0 : i;
}

/** Silvestre en Iberia o Madagascar. Sudáfrica no cuenta: los bots no entran. */
const WILD_BY_LEVEL = [
  [0, ["Mil flores", "Romero", "Tomillo", "Lavanda", "Arboç", "Eucalipto", "Bosque",
    "Litchi", "Girofle", "Ravintsara", "Longose"]],
  [5, ["Mielato de encina y roble"]],
  [10, ["Tapia", "Café", "Niaouli"]],
  [15, ["Castaño", "Brezo"]],
  [20, ["Tamarindo", "Baobab", "Mango", "Mangle"]],
  [25, ["Neret"]],
  [30, ["Raketa", "Jujube", "Sisal"]],
];

/**
 * Cultivo y nivel del clima más bajo (Iberia o Madagascar) donde se puede sembrar.
 * Lo que solo se planta en Sudáfrica no se desbloquea.
 */
const CROP_ACCESS = {
  "Campo de naranjos": 2,
  "Campo de almendros": 4,
  "Campo de cerezos": 6,
  "Campo de perales": 8,
  "Campo de mostaza": 9,
  "Campo de rabaniza": 10,
  "Campo de manzanos": 11,
  "Campo de trébol": 13,
  "Campo de girasoles": 14,
  "Campo de lavanda": 16,
  Café: 16,
  "Campo de Colza": 17,
  "Campo de facelia": 20,
  Mango: 20,
  Lucerna: 30,
  Sisal: 30,
};

function accessLevelForFlora(flora) {
  const name = String(flora || "");
  let level = Number.POSITIVE_INFINITY;
  for (const [need, list] of WILD_BY_LEVEL) {
    if (list.includes(name)) level = Math.min(level, need);
  }
  if (Object.prototype.hasOwnProperty.call(CROP_ACCESS, name)) {
    level = Math.min(level, CROP_ACCESS[name]);
  }
  return Number.isFinite(level) ? level : null;
}

function isFloraUnlocked(flora, level) {
  const need = accessLevelForFlora(flora);
  if (need == null) return false;
  return Math.max(0, level | 0) >= need;
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

function isMdgHex(hexId) {
  const id = String(hexId || "");
  return id.includes("_mdg_") || id.startsWith("mdg_") || id.startsWith("hex_mdg_");
}

function canUseHex(bot, hexId) {
  if (isZaHex(hexId)) return false;
  return true;
}

function warehouseInvested(level) {
  const lvl = Math.max(0, level | 0);
  if (lvl <= 0) return 0;
  let sum = RULES.WAREHOUSE_COST;
  for (let from = 1; from < lvl; from++) sum += RULES.WAREHOUSE_UPGRADE * Math.max(1, from);
  return sum;
}

/** Precio de compra al 100 % de lo que el bot tiene: terreno, almacén y colmenas. */
function netWorthB(bot) {
  let worth = 0;
  const seen = new Set();
  for (const hexId of (bot && bot.ownedHexIds) || []) {
    if (!hexId || seen.has(hexId) || isZaHex(hexId)) continue;
    seen.add(hexId);
    const flora = bot.hexFlora && bot.hexFlora[hexId];
    worth += terrainPrice(flora || "Mil flores");
  }
  const warehouses = new Set();
  for (const hexId of (bot && bot.warehouseHexIds) || []) {
    if (!hexId || isZaHex(hexId) || warehouses.has(hexId)) continue;
    warehouses.add(hexId);
    worth += warehouseInvested(1);
  }
  for (const hive of (bot && bot.hives) || []) {
    if (!hive || isZaHex(hive.hexId)) continue;
    worth += hivePrice(hive.superCount);
  }
  return worth;
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
  isMdgHex,
  canUseHex,
  accessLevelForFlora,
  warehouseInvested,
  netWorthB,
};
