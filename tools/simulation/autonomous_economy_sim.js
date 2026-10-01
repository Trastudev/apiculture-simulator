#!/usr/bin/env node
"use strict";

/**
 * Simulador económico de jugadores autónomos.
 *
 * No se conecta a Firebase ni ejecuta acciones reales. Es un "bot" de decisión
 * dentro de una simulación local: cada jugador produce, cosecha, vende, acepta
 * comandas/contratos, compra apiarios, colmenas, almacenes, camiones, alzas y
 * cultivos, y aplica el mismo modelo de XP que la app.
 * No incluye ferias ni publicidad: ambas quedan fuera del modelo.
 *
 * Ejemplos:
 *   node tools/simulation/autonomous_economy_sim.js --players 100 --days 365
 *   node tools/simulation/autonomous_economy_sim.js --players 100 --days 365 --profiles bots
 *   node tools/simulation/autonomous_economy_sim.js --players 20 --days 90 --persona mixed
 *
 * Salida por defecto: tools/simulation/results/<run-id>/
 */

const fs = require("fs");
const path = require("path");

const ROOT = path.resolve(__dirname, "..", "..");
const BALANCE_PATH = path.join(ROOT, "app", "src", "main", "assets", "game_balance.json");
const BOTS_STATE_PATH = path.join(ROOT, "tools", "bots", "state.json");

const balance = JSON.parse(fs.readFileSync(BALANCE_PATH, "utf8"));
const cfg = {
  population: balance.population || {},
  honey: balance.honey || {},
  forage: balance.forage || {},
  pollination: balance.pollination || {},
  logistics: balance.logistics || {},
  market: balance.honeyMarket || {},
};

const STARTING_BALANCE = 10000;
const TERRAIN_BASE = 1000;
const MAX_HIVES_PER_SITE = 10;
const STARTER_HONEY_KG = Number(cfg.honey.starterHiveStockKg || 5);
const STARTER_BEES = 25000;
const SUPER_CAP = [8, 30, 60];
const HIVE_PRICE = [200, 250, 300];
const SUPER_PRICE = Number(cfg.honey.superPurchasePriceEur || 50);
const WAREHOUSE_COST = 300;
const WAREHOUSE_BASE = 10;
const WAREHOUSE_STEP = 5;
const WAREHOUSE_UPGRADE = 250;
const TRUCK_COST = 1200;
const TRUCK_UPGRADE = [800, 1400, 2200, 3500, 5200, 7500, 10500, 14500, 19000];
const TRUCK_CAPACITY = [100, 180, 300, 480, 750, 1100, 1550, 2100, 2800, 3600];
const SHIP_COST = 6000;
const SHIP_UPGRADE = [3000, 5000, 8000, 12000, 18000, 26000, 36000, 50000, 68000];
const SHIP_CAPACITY = [2000, 3500, 5500, 8000, 12000, 17000, 23000, 31000, 41000, 54000];
const FEED_COST = 42;
const TREAT_COST = 25;
const QUEEN_COST = 50;
const SPLIT_BASE_COST = 100;
const SPLIT_SUPER_COST = 50;
const TRANS_COST_BASE = 80;
const TRANS_COST_PER_KM = 0.4;
const TRANS_MAX_COST = 250;
const ANNUAL_PLANT_COST = 450;
const TREE_PLANT_COST = 1600;
const TREE_MAINTENANCE_COST = 350;
const ORDER_PRICE_BONUS = 1.12;
const XP_GROWTH = 1.10;
const XP_COST_MULTIPLIER = 0.378;
const POLLINATION_PAY_MIN = Number(cfg.pollination.payPerDayMin || 45);
const POLLINATION_PAY_MAX = Number(cfg.pollination.payPerDayMax || 90);
const POLLINATION_CALLOUT = Number(cfg.pollination.calloutB || 250);
const POLLINATION_MIN_DAYS = Number(cfg.pollination.slotDaysMin || 5);
const POLLINATION_MAX_DAYS = Number(cfg.pollination.slotDaysMax || 21);

const PEAK_KG = Number(cfg.population.maxAdultWorkersPerHive || 80000)
  * Number(cfg.honey.foragerFraction || 0.24)
  * Number(cfg.honey.kgPerForagerFullFlow || 0.0002244375)
  * Number(cfg.honey.chartFloraBoost || 1.15)
  * Number(cfg.honey.chartTempBoost || 1.12)
  * 1.25;
const TYPICAL_OUTPUT = PEAK_KG * Number(cfg.market.typicalOutputFraction || 0.4);
const TURNOVER_PER_ACTIVITY = PEAK_KG
  * Number(cfg.market.typicalOutputFraction || 0.4)
  * Number(cfg.market.customerAbsorptionFraction || 0.75);
const CAPACITY_MULTIPLIER = Number(cfg.market.capacityDaysBuffer || 20)
  * Number(cfg.market.capacityExtraMultiplier || 2);
const LEVEL_FACTOR = Number(cfg.market.levelDemandFactor || 0.03);
const LEVEL_CAP = Number(cfg.market.levelDemandCap || 100);
const SUPPLY_SPAN = Number(cfg.market.supplyPriceSpan || 0.25);
const MIN_FLOOR = Number(cfg.market.minFloraDemandVsTop || 0.35);
const MIN_ACTIVITY_PER_PLAYER_REGION = Number(cfg.market.minActivityPerPlayerRegion || 1);
const DAILY_NOISE_MIN = Number(cfg.market.dailyNoiseMin || 0.9);
const DAILY_NOISE_MAX = Number(cfg.market.dailyNoiseMax || 1.1);
const SEASON_DEMAND = {
  WINTER: Number(cfg.market.demandWinter || 1.12),
  SPRING: Number(cfg.market.demandSpring || 0.93),
  SUMMER: Number(cfg.market.demandSummer || 0.87),
  AUTUMN: Number(cfg.market.demandAutumn || 1.06),
};

const REGION_SHARES = {
  iberia: {
    "Mil flores": 0.14, "Campo de naranjos": 0.08, "Romero": 0.07,
    "Lavanda": 0.07, "Tomillo": 0.06, "Brezo": 0.06, "Bosque": 0.05,
    "Castaño": 0.05, "Eucalipto": 0.04, "Mielato de encina y roble": 0.04,
    "Campo de girasoles": 0.03, "Campo de Colza": 0.03, "Arboç": 0.03,
    "Campo de manzanos": 0.025, "Campo de cerezos": 0.025,
    "Campo de lavanda": 0.02, "Campo de trébol": 0.02, "Neret": 0.02,
    "Campo de mostaza": 0.015, "Campo de facelia": 0.015,
    "Campo de rabaniza": 0.015, "Campo de perales": 0.015,
    "Campo de almendros": 0.015,
  },
  za: {
    "Mil flores": 0.12, "Fynbos": 0.12, "Eucalipto": 0.10, "Aloe": 0.08,
    "Protea": 0.07, "Acacia": 0.07, "Macadamia": 0.06, "Litchi": 0.06,
    "Aguacate": 0.06, "Buchu": 0.05, "Marula": 0.05, "Lucerna": 0.05,
    "Boekenhout": 0.04, "Campo de girasoles": 0.04, "Campo de Colza": 0.03,
    "Campo de naranjos": 0.03,
  },
  mdg: {
    "Mil flores": 0.14, "Litchi": 0.10, "Girofle": 0.10, "Café": 0.09,
    "Mango": 0.08, "Eucalipto": 0.07, "Ravintsara": 0.07, "Tapia": 0.06,
    "Niaouli": 0.06, "Longose": 0.05, "Tamarindo": 0.05, "Baobab": 0.05,
    "Campo de naranjos": 0.04, "Mangle": 0.04, "Raketa": 0.04,
    "Jujube": 0.03, "Sisal": 0.03,
  },
};

const LAND_OPTIONS = {
  iberia: [
    { level: 0, floras: ["Mil flores", "Romero", "Tomillo", "Lavanda"], label: "Mediterráneo" },
    { level: 5, floras: ["Mil flores", "Romero", "Tomillo", "Lavanda", "Mielato de encina y roble"], label: "Continental" },
    { level: 15, floras: ["Mil flores", "Castaño", "Brezo", "Eucalipto", "Arboç", "Bosque"], label: "Atlántico" },
    { level: 25, floras: ["Mil flores", "Brezo", "Bosque", "Castaño", "Mielato de encina y roble", "Neret", "Arboç"], label: "Alta montaña" },
    { level: 35, floras: ["Mil flores", "Eucalipto", "Romero", "Tomillo", "Castaño", "Arboç"], label: "Sur" },
  ],
  za: [
    { level: 40, floras: ["Eucalipto", "Acacia", "Boekenhout", "Aloe", "Mil flores"], label: "Highveld" },
    { level: 40, floras: ["Aloe", "Acacia", "Marula", "Boekenhout", "Eucalipto", "Mil flores"], label: "Bushveld" },
    { level: 45, floras: ["Aloe", "Mil flores", "Eucalipto"], label: "Karoo" },
    { level: 50, floras: ["Eucalipto", "Mil flores", "Bosque", "Acacia"], label: "Costa subtropical" },
    { level: 55, floras: ["Fynbos", "Protea", "Buchu", "Aloe", "Eucalipto", "Mil flores"], label: "Fynbos" },
  ],
  mdg: [
    { level: 0, floras: ["Litchi", "Girofle", "Ravintsara", "Longose", "Mil flores"], label: "Ecuatorial" },
    { level: 10, floras: ["Eucalipto", "Tapia", "Café", "Niaouli", "Mil flores"], label: "Altiplano" },
    { level: 20, floras: ["Tamarindo", "Baobab", "Mango", "Mangle", "Mil flores"], label: "Tropical" },
    { level: 30, floras: ["Raketa", "Jujube", "Sisal", "Mil flores"], label: "Desierto" },
  ],
};

const CROP_UNLOCK = {
  "Campo de naranjos": 2, "Campo de almendros": 4, "Mango": 5,
  "Campo de cerezos": 6, "Campo de perales": 8, "Campo de mostaza": 9,
  "Campo de rabaniza": 10, "Campo de manzanos": 11, "Campo de trébol": 13,
  "Campo de girasoles": 14, "Campo de lavanda": 16, "Café": 16,
  "Campo de Colza": 17, "Campo de facelia": 20, "Lucerna": 27,
  "Sisal": 30, "Litchi": 43, "Macadamia": 48, "Aguacate": 60,
};
const ANNUAL_CROPS = new Set([
  "Campo de Colza", "Campo de girasoles", "Lucerna", "Campo de mostaza",
  "Campo de trébol", "Campo de facelia", "Campo de rabaniza", "Sisal",
]);
const CROP_CLIMATES = {
  "Campo de naranjos": ["med", "south", "za_sub", "mdg_eq", "mdg_high", "mdg_trop"],
  "Campo de almendros": ["med", "south", "cont"],
  "Mango": ["mdg_trop"], "Campo de cerezos": ["med", "mountain", "south", "cont"],
  "Campo de perales": ["med", "mountain", "south", "cont"],
  "Campo de mostaza": ["med", "mountain", "south", "cont", "za_fynbos", "za_karoo"],
  "Campo de rabaniza": ["med", "mountain", "south", "cont", "za_fynbos", "za_karoo"],
  "Campo de manzanos": ["med", "mountain", "south", "cont"],
  "Campo de trébol": ["med", "mountain", "cont", "za_fynbos", "za_karoo", "za_high", "za_sub", "za_bush", "mdg_eq", "mdg_high", "mdg_trop"],
  "Campo de girasoles": ["med", "south", "cont", "za_high", "za_karoo", "za_bush"],
  "Campo de lavanda": ["med", "mountain", "south", "cont"],
  "Café": ["mdg_high"], "Campo de Colza": ["med", "south", "cont", "za_fynbos", "za_high", "za_sub", "za_bush"],
  "Campo de facelia": ["med", "mountain", "cont", "za_fynbos", "za_karoo", "za_sub", "za_bush", "mdg_eq", "mdg_high", "mdg_trop"],
  "Lucerna": ["za_fynbos", "za_karoo", "za_high", "za_bush", "mdg_desert"],
  "Sisal": ["za_karoo", "mdg_desert"], "Litchi": ["za_sub", "mdg_eq"],
  "Macadamia": ["za_sub", "mdg_eq"], "Aguacate": ["za_sub", "mdg_eq"],
};
const FLORA_ACCESS = {
  "Mil flores": 0, "Romero": 0, "Tomillo": 0, "Lavanda": 0, "Eucalipto": 0,
  "Arboç": 0, "Bosque": 0, "Brezo": 0, "Castaño": 0,
  "Mielato de encina y roble": 25, "Neret": 25,
  "Fynbos": 40, "Aloe": 40, "Acacia": 40, "Boekenhout": 40, "Macadamia": 40,
  "Litchi": 40, "Buchu": 40, "Protea": 40, "Marula": 40, "Lucerna": 40,
  "Campo de Colza": 17, "Campo de girasoles": 14, "Campo de manzanos": 11, "Campo de trébol": 13,
  "Campo de cerezos": 6, "Campo de perales": 8, "Campo de almendros": 4,
  "Campo de naranjos": 2, "Campo de mostaza": 9, "Campo de rabaniza": 10,
  "Campo de trébol": 13, "Campo de lavanda": 16, "Campo de facelia": 20,
  "Lucerna": 27, "Sisal": 30, "Litchi": 43, "Macadamia": 48, "Aguacate": 60,
  "Mango": 5, "Café": 16, "Ravintsara": 0, "Longose": 0, "Tapia": 10,
  "Niaouli": 10, "Tamarindo": 20, "Baobab": 20, "Mangle": 20, "Raketa": 30,
  "Jujube": 30, "Girofle": 0,
};

const PERSONAS = {
  smart: {
    label: "IA expansiva", login: 0.96, reserve: 400, maxParcels: 0,
    maxHives: 0, targetHives: 8, buyChance: 0.92, plantChance: 0.65,
    contractChance: 0.75, orderChance: 0.85, sellFraction: [0.72, 0.96],
  },
  steady: {
    label: "IA equilibrada", login: 0.88, reserve: 650, maxParcels: 0,
    maxHives: 0, targetHives: 6, buyChance: 0.65, plantChance: 0.35,
    contractChance: 0.45, orderChance: 0.65, sellFraction: [0.55, 0.82],
  },
  mixed: {
    label: "IA mixta", login: 0.82, reserve: 550, maxParcels: 0,
    maxHives: 0, targetHives: 7, buyChance: 0.78, plantChance: 0.5,
    contractChance: 0.6, orderChance: 0.75, sellFraction: [0.62, 0.9],
  },
};

const OFFER_BANDS = [
  { min: 0, max: 1, orderCount: 12, kgMin: 0.5, kgMax: 1.5, contracts: 8 },
  { min: 2, max: 4, orderCount: 14, kgMin: 1, kgMax: 3, contracts: 10 },
  { min: 5, max: 9, orderCount: 16, kgMin: 2, kgMax: 5, contracts: 12 },
  { min: 10, max: 14, orderCount: 18, kgMin: 3.5, kgMax: 8, contracts: 14 },
  { min: 15, max: 19, orderCount: 20, kgMin: 6, kgMax: 12, contracts: 16 },
  { min: 20, max: 24, orderCount: 22, kgMin: 8, kgMax: 16, contracts: 18 },
  { min: 25, max: 29, orderCount: 22, kgMin: 11, kgMax: 20, contracts: 20 },
  { min: 30, max: 34, orderCount: 24, kgMin: 14, kgMax: 24, contracts: 22 },
  { min: 35, max: 39, orderCount: 24, kgMin: 18, kgMax: 27, contracts: 24 },
  { min: 40, max: 999, orderCount: 26, kgMin: 22, kgMax: 30, contracts: 26 },
];

function arg(name, fallback) {
  const i = process.argv.indexOf(`--${name}`);
  return i >= 0 && process.argv[i + 1] !== undefined ? process.argv[i + 1] : fallback;
}
function boolArg(name) { return process.argv.includes(`--${name}`); }
function nonNegativeIntArg(name, fallback) {
  const value = arg(name, null);
  if (value === null) return fallback;
  const parsed = parseInt(value, 10);
  return Number.isFinite(parsed) && parsed >= 0 ? parsed : fallback;
}
function intArg(name, fallback) { return Math.max(1, parseInt(arg(name, String(fallback)), 10) || fallback); }
function parseHiveRamp(spec) {
  if (!spec) return null;
  const points = String(spec).split(",").map(part => {
    const [actionsText, dayText] = part.trim().split("@");
    const actions = parseInt(actionsText, 10);
    const day = parseInt(dayText, 10);
    if (!Number.isFinite(actions) || actions < 1 || !Number.isFinite(day) || day < 1) {
      throw new Error(`Rampa de colmenas inválida: ${part}`);
    }
    return { actions, day };
  }).sort((a, b) => a.day - b.day);
  return points.length ? points : null;
}
function hiveActionsForDay(ramp, dayNumber, fallback) {
  if (!ramp || !ramp.length) return fallback;
  for (const point of ramp) {
    if (dayNumber <= point.day) return point.actions;
  }
  return ramp[ramp.length - 1].actions;
}
function round2(n) { return Math.round(n * 100) / 100; }
function clamp(n, lo, hi) { return Math.max(lo, Math.min(hi, n)); }
function sum(values) { return values.reduce((a, b) => a + b, 0); }
function avg(values) { return values.length ? sum(values) / values.length : 0; }
function median(values) { return percentile(values, 0.5); }
function percentile(values, p) {
  if (!values.length) return 0;
  const a = [...values].sort((x, y) => x - y);
  const at = (a.length - 1) * p;
  const lo = Math.floor(at), hi = Math.ceil(at);
  return a[lo] + (a[hi] - a[lo]) * (at - lo);
}
function hashString(s) {
  let h = 2166136261;
  for (let i = 0; i < s.length; i++) {
    h ^= s.charCodeAt(i);
    h = Math.imul(h, 16777619);
  }
  return h >>> 0;
}
class RNG {
  constructor(seed) { this.state = (seed >>> 0) || 1; }
  next() { this.state = (Math.imul(this.state, 1664525) + 1013904223) >>> 0; return this.state / 0x100000000; }
  int(min, max) { return Math.floor(this.next() * (max - min + 1)) + min; }
  chance(p) { return this.next() < p; }
  pick(values) { return values[Math.floor(this.next() * values.length)]; }
}
function dayKey(date) { return date.getUTCFullYear() * 10000 + (date.getUTCMonth() + 1) * 100 + date.getUTCDate(); }
function dayOfYear(date) { return Math.floor((date.getTime() - Date.UTC(date.getUTCFullYear(), 0, 0)) / 86400000); }
function seasonFor(doy) {
  if (doy < 80) return "WINTER";
  if (doy < 172) return "SPRING";
  if (doy < 264) return "SUMMER";
  return "AUTUMN";
}
function productionSeason(doy) {
  if (doy < 80 || doy >= 355) return 0.65;
  if (doy < 172) return 1.10;
  if (doy < 244) return 0.85;
  return 1.05;
}
function floraBasePrice(flora) {
  const access = clamp(Number(FLORA_ACCESS[flora] || 0), 0, 60);
  return round2(12 + 6 * access / 60);
}
function floraValue(flora) { return floraBasePrice(flora) / 15.6; }
function terrainPremium(flora) {
  if (flora === "Mil flores") return 0;
  return clamp(Math.round(400 + (floraBasePrice(flora) - 15.3) * 1400), 250, 2500);
}
function terrainCost(floras) { return TERRAIN_BASE + sum(floras.map(terrainPremium)); }
function xpForLevel(level) {
  const raw = 100 * Math.pow(XP_GROWTH, Math.max(0, level));
  const mild = raw >= 2000000 ? 2000000 : Math.max(1, Math.round(raw));
  return Math.min(4000000, Math.max(1, Math.round(mild * 2 * XP_COST_MULTIPLIER)));
}
function addXpTo(player, amount) {
  if (!(amount > 0)) return;
  player.xp += amount;
  while (player.xp >= xpForLevel(player.level)) {
    player.xp -= xpForLevel(player.level);
    player.level += 1;
    player.stats.levelUps += 1;
  }
}
function cargoCost(kg, km = 50) {
  if (kg <= 0) return 0;
  const roundedKm = Math.round(km * 100) / 100;
  const rate = Number(cfg.logistics.freightBPerKgKm || 0.016)
    * Math.pow(80 / Math.max(80, kg), 0.45);
  // 50 km: 20 km al 100 % + 30 km al 90 %.
  const roadFactor = 0.94;
  return round2(Math.max(0.01, kg * (1 + 0.35) * rate * roundedKm * roadFactor));
}
function wageBand(level) {
  return OFFER_BANDS.find(b => level >= b.min && level <= b.max) || OFFER_BANDS[0];
}
function marketKey(region, flora) { return `${region}|${flora}`; }
function relativeDemand(region, flora) {
  const shares = REGION_SHARES[region] || REGION_SHARES.iberia;
  const max = Math.max(...Object.values(shares));
  return Math.max(MIN_FLOOR, (shares[flora] || 0) / max);
}
function cropKind(flora) { return ANNUAL_CROPS.has(flora) ? "ANNUAL" : "TREE"; }
function cropCost(flora) { return cropKind(flora) === "ANNUAL" ? ANNUAL_PLANT_COST : TREE_PLANT_COST; }
function cropGrowDays(flora) { return cropKind(flora) === "ANNUAL" ? 2 : 10; }
function cropAllowedForParcel(flora, parcel) {
  if (!CROP_UNLOCK[flora] || !parcel.climates) return false;
  return CROP_CLIMATES[flora]?.some(c => parcel.climates.includes(c)) || false;
}

class Market {
  constructor() {
    this.activity = { iberia: 0, za: 0, mdg: 0 };
    this.target = new Map();
    this.cap = new Map();
    this.soldToday = new Map();
    this.prevSold = new Map();
    this.prevTarget = new Map();
    this.price = new Map();
  }
  prepare(date, players) {
    this.activity = { iberia: 0, za: 0, mdg: 0 };
    for (const p of players) {
      const byRegion = { iberia: 0, za: 0, mdg: 0 };
      for (const h of p.hives()) byRegion[h.region] += 1;
      const total = byRegion.iberia + byRegion.za + byRegion.mdg;
      if (total === 0) {
        byRegion[p.region] += MIN_ACTIVITY_PER_PLAYER_REGION;
      }
      for (const region of Object.keys(byRegion)) {
        if (byRegion[region] > 0) {
          const level = Math.min(LEVEL_CAP, Math.max(0, p.level));
          this.activity[region] += byRegion[region] * (1 + LEVEL_FACTOR * level);
        }
      }
    }
    const factor = SEASON_DEMAND[seasonFor(dayOfYear(date))];
    const u = deterministicNoise("globalHoneyMarketNoise", dayKey(date));
    const noise = DAILY_NOISE_MIN + u * (DAILY_NOISE_MAX - DAILY_NOISE_MIN);
    this.target.clear(); this.cap.clear(); this.soldToday.clear(); this.price.clear();
    for (const region of Object.keys(REGION_SHARES)) {
      for (const flora of Object.keys(REGION_SHARES[region])) {
        const key = marketKey(region, flora);
        const target = Math.max(1, this.activity[region] * TURNOVER_PER_ACTIVITY
          * relativeDemand(region, flora) * factor * noise);
        this.target.set(key, target);
        this.cap.set(key, target * CAPACITY_MULTIPLIER);
        this.soldToday.set(key, 0);
        const previousTarget = this.prevTarget.get(key) || target;
        const previousSold = this.prevSold.get(key) || 0;
        const ratio = previousTarget > 0 ? clamp(previousSold / previousTarget, 0, 2) : 0;
        const pressure = 1 + SUPPLY_SPAN * (1 - ratio);
        this.price.set(key, round2(floraBasePrice(flora) * pressure));
      }
    }
  }
  ensureFlora(region, flora) {
    const key = marketKey(region, flora);
    if (this.cap.has(key)) return;
    const fallbackShare = 0.02;
    const target = Math.max(1, this.activity[region] * TURNOVER_PER_ACTIVITY * fallbackShare);
    this.target.set(key, target);
    this.cap.set(key, target * CAPACITY_MULTIPLIER);
    this.soldToday.set(key, 0);
    this.price.set(key, floraBasePrice(flora));
  }
  priceFor(region, flora) { return this.price.get(marketKey(region, flora)) || floraBasePrice(flora); }
  remaining(region, flora) {
    this.ensureFlora(region, flora);
    const key = marketKey(region, flora);
    return Math.max(0, (this.cap.get(key) || 0) - (this.soldToday.get(key) || 0));
  }
  recordSale(region, flora, requested) {
    const key = marketKey(region, flora);
    const accepted = Math.min(Math.max(0, requested), this.remaining(region, flora));
    this.soldToday.set(key, (this.soldToday.get(key) || 0) + accepted);
    return accepted;
  }
  closeDay() {
    this.prevSold = new Map(this.soldToday);
    this.prevTarget = new Map(this.target);
  }
}
function deterministicNoise(key, keyDay) {
  let h = 0;
  for (let i = 0; i < key.length; i++) h = (Math.imul(h, 31) + key.charCodeAt(i)) | 0;
  const x = Math.sin(h * 12.9898 + keyDay * 78.233) * 43758.5453;
  return x - Math.floor(x);
}

class Player {
  constructor(id, personaName, region, rng, startingBalance = STARTING_BALANCE) {
    this.id = id;
    this.personaName = personaName;
    this.persona = PERSONAS[personaName] || PERSONAS.smart;
    this.region = region;
    this.rng = rng;
    this.hiveActionsPerDay = 1;
    this.hivePriority = false;
    this.cash = startingBalance;
    this.level = 0;
    this.xp = 0;
    this.parcels = [];
    this.hiveList = [];
    this.warehouseStock = Object.create(null);
    this.truckLevel = 0;
    this.shipLevel = 0;
    this.contracts = [];
    this.stats = {
      revenueMarket: 0, revenueOrders: 0, revenueContracts: 0,
      expensesLand: 0, expensesHives: 0, expensesWarehouse: 0,
      expensesFleet: 0, expensesCrops: 0, expensesCare: 0, expensesOther: 0,
      kgProduced: 0, kgSold: 0, orders: 0, contracts: 0,
      contractsFailed: 0, contractRevenue: 0, levelUps: 0,
      treesPlanted: 0, cropsPlanted: 0, treeMaintenance: 0, splits: 0, loggedDays: 0,
    };
    this.landPaid = 0;
    this.hivePaid = 0;
    this.warehousePaid = 0;
    this.fleetPaid = 0;
    this.cropPaid = 0;
    this.otherPaid = 0;
  }
  spend(amount, category) {
    amount = Math.max(0, round2(amount));
    if (amount > this.cash) return false;
    this.cash = round2(this.cash - amount);
    if (category === "land") this.stats.expensesLand += amount;
    if (category === "hives") this.stats.expensesHives += amount;
    if (category === "warehouse") this.stats.expensesWarehouse += amount;
    if (category === "fleet") this.stats.expensesFleet += amount;
    if (category === "crops") this.stats.expensesCrops += amount;
    if (category === "care") this.stats.expensesCare += amount;
    if (category === "other") this.stats.expensesOther += amount;
    return true;
  }
  earn(amount, source) {
    amount = Math.max(0, round2(amount));
    this.cash = round2(this.cash + amount);
    if (source === "market") this.stats.revenueMarket += amount;
    if (source === "orders") this.stats.revenueOrders += amount;
    if (source === "contracts") {
      this.stats.revenueContracts += amount;
      this.stats.contractRevenue += amount;
    }
  }
  hives() { return this.hiveList; }
  warehouseCapacity() {
    return sum(this.parcels.map(p => p.warehouseLevel > 0 ? WAREHOUSE_BASE + WAREHOUSE_STEP * (p.warehouseLevel - 1) : 0));
  }
  warehouseUsed() { return sum(Object.values(this.warehouseStock)); }
  stock(flora) { return Number(this.warehouseStock[flora] || 0); }
  addWarehouseStock(flora, kg) {
    const room = Math.max(0, this.warehouseCapacity() - this.warehouseUsed());
    const accepted = Math.min(Math.max(0, kg), room);
    this.warehouseStock[flora] = round2(this.stock(flora) + accepted);
    return accepted;
  }
  takeStock(flora, kg) {
    const available = this.stock(flora);
    const taken = Math.min(available, Math.max(0, kg));
    this.warehouseStock[flora] = round2(available - taken);
    return taken;
  }
  hiveStock() { return sum(this.hives().map(h => h.stock)); }
  totalStock() { return round2(this.warehouseUsed() + this.hiveStock()); }
  hasTruck() { return this.truckLevel > 0; }
  netWorth(market) {
    let value = this.cash;
    for (const flora of Object.keys(this.warehouseStock)) value += this.stock(flora) * market.priceFor(this.region, flora);
    for (const hive of this.hives()) value += hive.stock * market.priceFor(this.region, hive.flora);
    value += this.landPaid * 0.5 + this.warehousePaid * 0.5 + this.hivePaid * 0.5 + this.fleetPaid * 0.5 + this.cropPaid * 0.25;
    return round2(value);
  }
  addParcel(option, withWarehouse) {
    const id = `${this.id}-parcel-${this.parcels.length + 1}`;
    const climates = climatesForOption(this.region, option.label);
    const landCost = terrainCost(option.floras);
    const warehouseCost = withWarehouse ? WAREHOUSE_COST : 0;
    if (this.cash < landCost + warehouseCost) return null;
    const parcel = { id, floras: option.floras.slice(), climates, warehouseLevel: withWarehouse ? 1 : 0, crops: [] };
    this.parcels.push(parcel);
    this.spend(landCost, "land");
    this.landPaid += landCost;
    if (withWarehouse) {
      this.spend(warehouseCost, "warehouse");
      this.warehousePaid += warehouseCost;
      addXpTo(this, 15 + 10);
    } else {
      addXpTo(this, 15);
    }
    return parcel;
  }
  addWarehouse(parcel) {
    if (!parcel || parcel.warehouseLevel > 0 || !this.spend(WAREHOUSE_COST, "warehouse")) return false;
    parcel.warehouseLevel = 1;
    this.warehousePaid += WAREHOUSE_COST;
    addXpTo(this, 10);
    return true;
  }
  addWarehouseUpgrade(parcel) {
    if (!parcel || parcel.warehouseLevel <= 0) return false;
    const cost = WAREHOUSE_UPGRADE * Math.max(1, parcel.warehouseLevel);
    if (!this.spend(cost, "warehouse")) return false;
    this.warehousePaid += cost;
    parcel.warehouseLevel += 1;
    return true;
  }
  addHive(parcel, flora, supers) {
    supers = clamp(Math.round(supers), 0, 2);
    const cost = HIVE_PRICE[supers];
    if (!this.spend(cost, "hives")) return null;
    this.hivePaid += cost;
    const hive = {
      id: `${this.id}-hive-${this.hiveList.length + 1}`,
      region: this.region, parcelId: parcel.id, flora,
      supers, beeCount: STARTER_BEES, stock: STARTER_HONEY_KG,
      health: 85, varroa: 2, queenQuality: 82, queenAge: 0,
      treatmentDays: 0, feedDays: 0, transitDays: 0, contract: null,
    };
    this.hiveList.push(hive);
    addXpTo(this, 2);
    return hive;
  }
  splitHive(parent) {
    if (!parent || parent.beeCount < 35000
        || (this.persona.maxHives > 0 && this.hiveList.length >= this.persona.maxHives)) return false;
    const parcel = this.parcels.find(p => p.id === parent.parcelId);
    if (!parcel || this.hives().filter(h => h.parcelId === parcel.id).length >= MAX_HIVES_PER_SITE) return false;
    const cost = SPLIT_BASE_COST + SPLIT_SUPER_COST * parent.supers;
    if (!this.spend(cost, "hives")) return false;
    this.hivePaid += cost;
    parent.beeCount = Math.max(8000, Math.round(parent.beeCount * 0.52));
    const split = {
      id: `${this.id}-hive-${this.hiveList.length + 1}`,
      region: this.region, parcelId: parcel.id, flora: parent.flora, supers: 0,
      beeCount: Math.max(8000, Math.round(parent.beeCount * 0.38)), stock: 0,
      health: parent.health, varroa: parent.varroa, queenQuality: 75, queenAge: 0,
      treatmentDays: 0, feedDays: 0, transitDays: 0, contract: null,
    };
    this.hiveList.push(split);
    this.stats.splits += 1;
    return true;
  }
  addSuper(hive) {
    if (!hive || hive.supers >= 2) return false;
    if (!this.spend(SUPER_PRICE, "hives")) return false;
    this.hivePaid += SUPER_PRICE;
    hive.supers += 1;
    return true;
  }
  addTruck() {
    if (this.truckLevel > 0) return false;
    if (!this.spend(TRUCK_COST, "fleet")) return false;
    this.fleetPaid += TRUCK_COST;
    this.truckLevel = 1;
    return true;
  }
  upgradeTruck() {
    if (this.truckLevel < 1 || this.truckLevel >= 10) return false;
    const cost = TRUCK_UPGRADE[this.truckLevel - 1];
    if (!this.spend(cost, "fleet")) return false;
    this.fleetPaid += cost;
    this.truckLevel += 1;
    return true;
  }
  addShip() {
    if (this.shipLevel > 0) return false;
    if (!this.spend(SHIP_COST, "fleet")) return false;
    this.fleetPaid += SHIP_COST;
    this.shipLevel = 1;
    return true;
  }
  plant(parcel, flora, dayIndex) {
    if (!parcel || !cropAllowedForParcel(flora, parcel)) return false;
    if (parcel.crops.some(c => c.flora === flora)) return false;
    const cost = cropCost(flora);
    if (!this.spend(cost, "crops")) return false;
    this.cropPaid += cost;
    parcel.crops.push({ flora, readyDay: dayIndex + cropGrowDays(flora), annual: cropKind(flora) === "ANNUAL" });
    if (cropKind(flora) === "TREE") this.stats.treesPlanted += 1;
    else this.stats.cropsPlanted += 1;
    addXpTo(this, 10);
    return true;
  }
}
function climatesForOption(region, label) {
  const map = {
    "Mediterráneo": ["med", "south", "cont"], Continental: ["med", "cont", "south"],
    "Atlántico": ["med", "mountain", "cont"], "Alta montaña": ["mountain", "med", "cont"],
    Sur: ["south", "med", "cont"], Highveld: ["za_high", "za_bush"],
    Bushveld: ["za_bush", "za_high"], Karoo: ["za_karoo", "za_high"],
    "Costa subtropical": ["za_sub", "za_high"], "Fynbos": ["za_fynbos", "za_bush"],
    Ecuatorial: ["mdg_eq", "mdg_high"], Altiplano: ["mdg_high", "mdg_eq"],
    Tropical: ["mdg_trop", "mdg_eq"], Desierto: ["mdg_desert", "mdg_trop"],
  };
  return map[label] || ["med", "cont"];
}
function bestParcelForHive(player) {
  const free = player.parcels.filter(p => player.hives().filter(h => h.parcelId === p.id).length < MAX_HIVES_PER_SITE);
  if (!free.length) return null;
  free.sort((a, b) => avg(b.floras.map(floraValue)) - avg(a.floras.map(floraValue)));
  return free[0];
}
function bestHiveFlora(player, parcel) {
  const values = parcel.floras.filter(f => floraBasePrice(f) >= 0);
  values.sort((a, b) => floraValue(b) - floraValue(a));
  return values[0] || "Mil flores";
}
function hiveCapacity(hive) { return SUPER_CAP[clamp(hive.supers, 0, 2)]; }
function hivePeak(hive) {
  return hive.beeCount * Number(cfg.honey.foragerFraction || 0.24)
    * Number(cfg.honey.kgPerForagerFullFlow || 0.0002244375)
    * Number(cfg.honey.chartFloraBoost || 1.15)
    * Number(cfg.honey.chartTempBoost || 1.12) * 1.25;
}
function hiveRate(hive, date) {
  const floraMult = floraValue(hive.flora);
  const healthMult = clamp(hive.health / 85, 0.35, 1.15);
  const queenMult = clamp(hive.queenQuality / 82, 0.65, 1.15);
  const noise = 0.94 + deterministicNoise(`${hive.id}:production`, dayKey(date)) * 0.12;
  return Math.max(0, hivePeak(hive) * Number(cfg.market.typicalOutputFraction || 0.4)
    * floraMult * healthMult * queenMult * productionSeason(dayOfYear(date)) * noise);
}
function carryingCapacity(doy) {
  const xs = [1, 46, 80, 120, 161, 201, 244, 288, 330, 365];
  const ys = [12000, 13000, 18000, 44000, 58000, 48000, 38000, 28000, 18000, 12000];
  const d = clamp(doy, 1, 365);
  for (let i = 0; i < xs.length - 1; i++) {
    if (d <= xs[i + 1]) return ys[i] + (ys[i + 1] - ys[i]) * (d - xs[i]) / (xs[i + 1] - xs[i]);
  }
  return ys[ys.length - 1];
}
function updateHive(hive, player, date, dayIndex) {
  if (hive.transitDays > 0) {
    hive.transitDays -= 1;
    if (hive.transitDays === 0 && hive.destinationFlora) {
      hive.flora = hive.destinationFlora;
      hive.destinationFlora = null;
      hive.destinationParcelId = null;
    }
    return 0;
  }
  if (hive.contract) {
    hive.contract.daysLeft -= 1;
    if (hive.contract.daysLeft <= 0) {
      const successChance = hive.contract.successChance || clamp(0.55 + hive.health / 300 + hive.beeCount / 300000, 0.5, 0.95);
      if (player.rng.chance(successChance)) {
        player.earn(hive.contract.pay, "contracts");
        player.stats.contracts += 1;
        addXpTo(player, 4);
      } else {
        player.stats.contractsFailed += 1;
      }
      hive.contract = null;
    }
    return 0;
  }
  const cap = carryingCapacity(dayOfYear(date));
  const lambda = hive.beeCount < cap ? 0.04 : 0.018;
  hive.beeCount = clamp(hive.beeCount + (cap - hive.beeCount) * lambda, 8000, Number(cfg.population.maxAdultWorkersPerHive || 80000));
  if (hive.treatmentDays > 0) {
    hive.varroa = clamp(hive.varroa * 0.78, 0, 40);
    hive.treatmentDays -= 1;
  } else {
    hive.varroa = clamp(hive.varroa + 0.08, 0, 40);
  }
  if (hive.feedDays > 0) {
    hive.health = clamp(hive.health + 0.10, 0, 100);
    hive.feedDays -= 1;
  } else if (hive.varroa > 8 || hive.stock < 0.5) {
    hive.health = clamp(hive.health - 0.05 * Math.max(1, hive.varroa - 7), 0, 100);
  } else {
    hive.health = clamp(hive.health + 0.025, 0, 100);
  }
  hive.queenAge += 1;
  const produced = Math.min(hiveCapacity(hive) - hive.stock, hiveRate(hive, date));
  hive.stock = round2(hive.stock + Math.max(0, produced));
  player.stats.kgProduced += Math.max(0, produced);
  return Math.max(0, produced);
}
function harvest(player) {
  for (const hive of player.hives()) {
    if (hive.stock <= 3.01) continue;
    const amount = hive.stock - 3;
    const accepted = player.addWarehouseStock(hive.flora, amount);
    hive.stock = round2(hive.stock - accepted);
  }
}
function chooseOrder(player, market, date) {
  if (!player.hasTruck() || player.level < 0 || !player.rng.chance(player.persona.orderChance)) return;
  const band = wageBand(player.level);
  const stocked = Object.keys(player.warehouseStock).filter(f => player.stock(f) > 0.01);
  if (!stocked.length) return;
  stocked.sort((a, b) => floraBasePrice(b) - floraBasePrice(a));
  const flora = stocked[0];
  const kg = round2(player.rng.next() * (band.kgMax - band.kgMin) + band.kgMin);
  const amount = Math.min(player.stock(flora), kg);
  if (amount < 0.5) return;
  const unit = round2(market.priceFor(player.region, flora) * ORDER_PRICE_BONUS);
  const freight = cargoCost(amount, 50);
  const wholesale = market.priceFor(player.region, flora) * amount;
  const net = amount * unit - freight;
  if (net <= wholesale * 1.03 || net <= 10) return;
  const accepted = market.recordSale(player.region, flora, amount);
  if (accepted < amount) return;
  player.takeStock(flora, accepted);
  player.earn(net, "orders");
  player.stats.orders += 1;
  player.stats.kgSold += accepted;
  addXpTo(player, 2 + accepted);
}
function pollinationPay(flora, days, minPoints = 40) {
  const p = clamp(floraBasePrice(flora), 12, 18);
  const daily = round2(POLLINATION_PAY_MIN
    + (p - 12) / (18 - 12) * (POLLINATION_PAY_MAX - POLLINATION_PAY_MIN));
  const multiplier = 1 + 0.015 * clamp(minPoints - 40, 0, 20);
  return { daily, pay: Math.round((daily * days + POLLINATION_CALLOUT) * multiplier) };
}
function acceptPollination(player, market, date) {
  if (player.level < 2 || player.contracts.length >= 2 || !player.rng.chance(player.persona.contractChance)) return;
  const free = player.hives().filter(h => !h.contract && h.transitDays <= 0);
  if (!free.length) return;
  free.sort((a, b) => hiveRate(b, date) - hiveRate(a, date));
  const hive = free[0];
  const days = player.rng.int(POLLINATION_MIN_DAYS,
    Math.min(POLLINATION_MAX_DAYS, 7 + Math.floor(player.level / 3)));
  const flora = hive.flora;
  const minPoints = player.rng.int(40, 60);
  const terms = pollinationPay(flora, days, minPoints);
  const km = player.rng.int(20, 120);
  const travel = Math.round(km * Number(cfg.pollination.bPerKmPerHive || 0.4));
  const marketValue = hiveRate(hive, date) * days * market.priceFor(player.region, flora);
  const successChance = clamp(0.55 + hive.health / 300 + hive.beeCount / 300000, 0.5, 0.95);
  const expected = terms.pay * successChance - travel - marketValue;
  if (terms.pay <= travel * 2 || expected <= 0) return;
  if (!player.spend(travel, "other")) return;
  hive.contract = { daysLeft: days, pay: terms.pay, travel, successChance, flora };
  player.contracts.push({ hiveId: hive.id });
}
function sellMarket(player, market) {
  if (!player.hasTruck()) return;
  const fractionLo = player.persona.sellFraction[0];
  const fractionHi = player.persona.sellFraction[1];
  for (const flora of Object.keys(player.warehouseStock)) {
    const available = player.stock(flora);
    if (available < 0.2) continue;
    const keep = 0.25;
    const amount = Math.max(0, available - keep) * player.rng.next() * (fractionHi - fractionLo) + fractionLo;
    const sell = Math.min(available, amount);
    if (sell < 0.1) continue;
    const price = market.priceFor(player.region, flora);
    const freight = cargoCost(sell, 50);
    const net = sell * price - freight;
    if (net <= 0) continue;
    const accepted = market.recordSale(player.region, flora, sell);
    if (accepted <= 0) continue;
    player.takeStock(flora, accepted);
    player.earn(accepted * price - freight, "market");
    player.stats.kgSold += accepted;
    addXpTo(player, accepted);
  }
}
function tryBuyParcel(player, reserve, force = false) {
  if (player.persona.maxParcels > 0 && player.parcels.length >= player.persona.maxParcels) return false;
  if (!force && !player.rng.chance(player.persona.buyChance)) return false;
  const options = LAND_OPTIONS[player.region].filter(o => o.level <= player.level);
  options.sort((a, b) => avg(b.floras.map(floraValue)) / Math.max(1, terrainCost(b.floras)) - avg(a.floras.map(floraValue)) / Math.max(1, terrainCost(a.floras)));
  const option = options[0];
  if (!option) return false;
  const hasWarehouse = player.parcels.some(p => p.warehouseLevel > 0);
  const attachWarehouse = !hasWarehouse || player.hives().length / Math.max(1, player.parcels.length + 1) >= 3.5;
  const total = terrainCost(option.floras) + (attachWarehouse ? WAREHOUSE_COST : 0);
  if (player.cash <= total + reserve) return false;
  return Boolean(player.addParcel(option, attachWarehouse));
}
function tryBuyHives(player, market, reserve) {
  for (let action = 0; action < player.hiveActionsPerDay; action++) {
    if (player.persona.maxHives > 0 && player.hives().length >= player.persona.maxHives) break;
    if (!player.hivePriority && !player.rng.chance(player.persona.buyChance)) break;
    const parcel = bestParcelForHive(player);
    if (!parcel) break;
    const flora = bestHiveFlora(player, parcel);
    const supers = player.level >= 8 && player.cash > 1800 ? 2 : (player.level >= 4 && player.cash > 900 ? 1 : 0);
    const cost = HIVE_PRICE[supers];
    const estimatedDaily = TYPICAL_OUTPUT * floraValue(flora) * market.priceFor(player.region, flora);
    if (player.cash <= cost + reserve || estimatedDaily * 30 <= cost) break;
    if (!player.addHive(parcel, flora, supers)) break;
  }
}
function careAndInvest(player, market, date, dayIndex) {
  const reserve = player.persona.reserve;
  // Ciomas: el bot trata antes de que la salud caiga.
  for (const hive of player.hives()) {
    if (hive.varroa > 6 && hive.treatmentDays <= 0 && player.cash > reserve + TREAT_COST) {
      if (player.spend(TREAT_COST, "care")) hive.treatmentDays = 7;
    }
    if (hive.health < 78 && hive.feedDays <= 0 && player.cash > reserve + FEED_COST) {
      if (player.spend(FEED_COST, "care")) hive.feedDays = 7;
    }
    if (hive.queenAge > 180 && player.cash > reserve + QUEEN_COST) {
      if (player.spend(QUEEN_COST, "care")) { hive.queenAge = 0; hive.queenQuality = 85; }
    }
  }
  // Almacén: se instala si falta y se amplía antes de que la producción se quede en la colmena.
  if (player.parcels.length && !player.parcels.some(p => p.warehouseLevel > 0)) {
    const parcel = player.parcels[0];
    if (player.cash > reserve + WAREHOUSE_COST) player.addWarehouse(parcel);
  }
  for (const parcel of player.parcels) {
    const used = player.warehouseUsed();
    if (parcel.warehouseLevel > 0 && used > player.warehouseCapacity() * 0.65
        && player.cash > reserve + WAREHOUSE_UPGRADE * parcel.warehouseLevel) {
      player.addWarehouseUpgrade(parcel);
      break;
    }
  }
  // División: una colmena madura genera otra si el terreno tiene hueco.
  for (const hive of player.hives().slice().sort((a, b) => b.beeCount - a.beeCount)) {
    const cost = SPLIT_BASE_COST + SPLIT_SUPER_COST * hive.supers;
    if (hive.beeCount >= 35000 && player.cash > reserve + cost
        && (player.persona.maxHives <= 0 || player.hives().length < player.persona.maxHives)) {
      if (player.splitHive(hive)) break;
    }
  }
  // En prioridad a colmenas, primero se completa el suelo existente y solo se abre
  // otro apiario cuando ya no queda hueco para colocar una colmena.
  if (player.hivePriority) {
    if (!player.parcels.length) tryBuyParcel(player, reserve, true);
    tryBuyHives(player, market, reserve);
    if (!bestParcelForHive(player)) tryBuyParcel(player, reserve, true);
  } else {
    tryBuyParcel(player, reserve);
    tryBuyHives(player, market, reserve);
  }
  // Alza: solo si la colmena se está llenando y hay reserva.
  for (const hive of player.hives().slice().sort((a, b) => b.stock / hiveCapacity(b) - a.stock / hiveCapacity(a))) {
    if (hive.stock > hiveCapacity(hive) * 0.45 && hive.supers < 2 && player.cash > reserve + SUPER_PRICE) {
      player.addSuper(hive);
      break;
    }
  }
  // Camión: requisito para vender/comandar; se compra después del primer ingreso.
  if (!player.hasTruck() && player.hives().length > 0 && player.cash > reserve + TRUCK_COST) player.addTruck();
  if (player.hasTruck() && player.truckLevel < 10) {
    const monthly = sum(player.hives().map(h => hiveRate(h, date) * 30 * market.priceFor(player.region, h.flora)));
    if (monthly > TRUCK_CAPACITY[player.truckLevel - 1] * 0.8 && player.cash > reserve + TRUCK_UPGRADE[player.truckLevel - 1]
        && player.rng.chance(0.35)) player.upgradeTruck();
  }
  if (player.level >= 20 && player.shipLevel === 0 && player.hives().length >= 20
      && player.cash > reserve + SHIP_COST && player.rng.chance(0.1)) player.addShip();
  // Cultivos: una siembra por terreno, sin repetir la misma flora cada día.
  if (player.level >= 2 && player.rng.chance(player.persona.plantChance)) {
    for (const parcel of player.parcels) {
      if (parcel.crops.length >= 2) continue;
      const candidates = Object.keys(CROP_UNLOCK).filter(f => CROP_UNLOCK[f] <= player.level && cropAllowedForParcel(f, parcel));
      candidates.sort((a, b) => floraBasePrice(b) - floraBasePrice(a));
      const crop = candidates[0];
      if (crop && player.cash > cropCost(crop) + reserve) { player.plant(parcel, crop, dayIndex); break; }
    }
  }
  // Trashumancia simple: una cada 30 días si hay dos apiarios y un destino mejor.
  if (dayIndex > 0 && dayIndex % 30 === 0 && player.parcels.length > 1 && player.rng.chance(0.25)) {
    const movable = player.hives().find(h => !h.contract && h.transitDays <= 0);
    const destinations = player.parcels.filter(p => p.id !== movable?.parcelId);
    if (movable && destinations.length && player.cash > reserve + TRANS_COST_BASE) {
      const dest = destinations[0];
      const cost = clamp(Math.round(TRANS_COST_BASE + TRANS_COST_PER_KM * player.rng.int(20, 100)), TRANS_COST_BASE, TRANS_MAX_COST);
      if (player.spend(cost, "other")) {
        movable.transitDays = 1;
        movable.destinationParcelId = dest.id;
        movable.destinationFlora = bestHiveFlora(player, dest);
        addXpTo(player, 4);
      }
    }
  }
}
function updateCropsAndContracts(player, dayIndex, date) {
  const maintenanceDay = dayOfYear(date) === 20;
  for (const parcel of player.parcels) {
    for (const crop of parcel.crops) {
      if (!crop.ready && dayIndex >= crop.readyDay) {
        crop.ready = true;
        if (!parcel.floras.includes(crop.flora)) parcel.floras.push(crop.flora);
      }
      if (maintenanceDay && crop.ready && !crop.annual && !crop.maintenancePaid) {
        if (player.spend(TREE_MAINTENANCE_COST, "crops")) {
          crop.maintenancePaid = true;
          player.stats.treeMaintenance += 1;
        }
      }
    }
  }
  player.contracts = player.contracts.filter(c => {
    const hive = player.hives().find(h => h.id === c.hiveId);
    return hive && hive.contract;
  });
}
function runPlayerDay(player, date, dayIndex, market, hiveRamp = null) {
  player.hiveActionsPerDay = hiveActionsForDay(hiveRamp, dayIndex + 1, player.hiveActionsPerDay);
  const login = player.rng.chance(player.persona.login);
  updateCropsAndContracts(player, dayIndex, date);
  for (const hive of player.hives()) updateHive(hive, player, date, dayIndex);
  if (!login) return;
  player.stats.loggedDays += 1;
  harvest(player);
  chooseOrder(player, market, date);
  acceptPollination(player, market, date);
  sellMarket(player, market);
  careAndInvest(player, market, date, dayIndex);
}
function createFreshPlayers(count, personaMode, seed, startingBalance = STARTING_BALANCE) {
  const players = [];
  for (let i = 0; i < count; i++) {
    const personaName = personaMode === "mixed"
      ? ["smart", "steady", "smart", "mixed"][i % 4]
      : personaMode;
    const rng = new RNG(hashString(`${seed}:fresh:${i}`));
    players.push(new Player(i + 1, personaName, "iberia", rng, startingBalance));
  }
  return players;
}
function createBotPlayers(count, seed) {
  if (!fs.existsSync(BOTS_STATE_PATH)) return createFreshPlayers(count, "smart", seed);
  const source = JSON.parse(fs.readFileSync(BOTS_STATE_PATH, "utf8")).bots || [];
  const players = [];
  for (let i = 0; i < count; i++) {
    const b = source[i % source.length] || {};
    const rng = new RNG(hashString(`${seed}:bots:${i}`));
    const p = new Player(i + 1, "mixed", "iberia", rng);
    p.cash = Number(b.balanceEur || 0);
    p.level = Number(b.level || 0);
    p.xp = Number(b.xp || 0);
    for (const [flora, kg] of Object.entries(b.honeyByFlora || {})) p.warehouseStock[flora] = Number(kg || 0);
    const hexes = Array.isArray(b.ownedHexIds) ? b.ownedHexIds : [];
    for (let j = 0; j < hexes.length; j++) {
      const floras = Array.from(new Set((b.hives || []).filter(h => h.hexId === hexes[j]).map(h => h.floraType).filter(Boolean)));
      const parcel = { id: `${p.id}-state-parcel-${j + 1}`, floras: floras.length ? floras : ["Mil flores"], climates: climatesForOption("iberia", "Mediterráneo"), warehouseLevel: j === 0 ? 1 : 0, crops: [] };
      p.parcels.push(parcel);
    }
    if (!p.parcels.length) p.parcels.push({ id: `${p.id}-state-parcel-1`, floras: ["Mil flores"], climates: climatesForOption("iberia", "Mediterráneo"), warehouseLevel: 1, crops: [] });
    for (const h of (b.hives || [])) {
      const parcel = p.parcels.find(x => x.id.endsWith(String((h.hexId || "").split("_").pop()))) || p.parcels[0];
      const hive = { id: h.id || `${p.id}-state-hive-${p.hiveList.length + 1}`, region: "iberia", parcelId: parcel.id, flora: h.floraType || "Mil flores", supers: Number(h.superCount || 0), beeCount: Number(h.beeCount || STARTER_BEES), stock: Number(h.honeyProduction || STARTER_HONEY_KG), health: Number(h.health || 85), varroa: Number(h.varroaPct || 2), queenQuality: 82, queenAge: 0, treatmentDays: 0, feedDays: 0, transitDays: 0, contract: null };
      p.hiveList.push(hive);
    }
    if (p.hiveList.length) p.truckLevel = 1;
    p.landPaid = sum(p.parcels.map(parcel => terrainCost(parcel.floras)));
    p.warehousePaid = sum(p.parcels.filter(parcel => parcel.warehouseLevel > 0).map(() => WAREHOUSE_COST));
    p.hivePaid = sum(p.hiveList.map(hive => HIVE_PRICE[clamp(hive.supers, 0, 2)] + hive.supers * SUPER_PRICE));
    p.fleetPaid = p.truckLevel > 0 ? TRUCK_COST : 0;
    players.push(p);
  }
  return players;
}
function aggregateDay(players, market, date, dayIndex, before, dayActionLimit = null) {
  const cash = players.map(p => p.cash);
  const worth = players.map(p => p.netWorth(market));
  const levels = players.map(p => p.level);
  const delta = field => sum(players.map((p, i) => Number(p.stats[field] || 0) - Number(before[i][field] || 0)));
  const expense = p => p.stats.expensesLand + p.stats.expensesHives + p.stats.expensesWarehouse
    + p.stats.expensesFleet + p.stats.expensesCrops + p.stats.expensesCare + p.stats.expensesOther;
  const daily = {
    day: dayKey(date), dayIndex, hiveActionLimit: dayActionLimit,
    totalCash: round2(sum(cash)), avgCash: round2(avg(cash)), medianCash: round2(median(cash)), p90Cash: round2(percentile(cash, 0.9)),
    totalNetWorth: round2(sum(worth)), avgNetWorth: round2(avg(worth)), medianNetWorth: round2(median(worth)), p90NetWorth: round2(percentile(worth, 0.9)),
    avgLevel: round2(avg(levels)), maxLevel: Math.max(...levels),
    totalHives: sum(players.map(p => p.hives().length)), totalParcels: sum(players.map(p => p.parcels.length)),
    totalWarehouses: sum(players.map(p => p.parcels.filter(x => x.warehouseLevel > 0).length)),
    truckCount: sum(players.map(p => p.hasTruck() ? 1 : 0)), truckLevels: sum(players.map(p => p.truckLevel)),
    shipCount: sum(players.map(p => p.shipLevel > 0 ? 1 : 0)), shipLevels: sum(players.map(p => p.shipLevel)),
    totalStockKg: round2(sum(players.map(p => p.totalStock()))),
    producedKgToday: round2(delta("kgProduced")), soldKgToday: round2(delta("kgSold")),
    revenueMarketToday: round2(delta("revenueMarket")), revenueOrdersToday: round2(delta("revenueOrders")),
    revenueContractsToday: round2(delta("revenueContracts")),
    expensesToday: round2(sum(players.map((p, i) => expense(p) - before[i].expenses))),
    ordersToday: delta("orders"), contractsToday: delta("contracts"), contractFailuresToday: delta("contractsFailed"),
    cumulativeProducedKg: round2(sum(players.map(p => p.stats.kgProduced))),
    cumulativeSoldKg: round2(sum(players.map(p => p.stats.kgSold))),
    cumulativeRevenueMarket: round2(sum(players.map(p => p.stats.revenueMarket))),
    cumulativeRevenueOrders: round2(sum(players.map(p => p.stats.revenueOrders))),
    cumulativeRevenueContracts: round2(sum(players.map(p => p.stats.revenueContracts))),
    cumulativeExpenses: round2(sum(players.map(expense))),
    cumulativeOrders: sum(players.map(p => p.stats.orders)), cumulativeContracts: sum(players.map(p => p.stats.contracts)),
    lowCashPlayers: cash.filter(x => x < 300).length,
  };
  return daily;
}
function percentileCount(players, predicate) { return players.filter(predicate).length; }
function writeCsv(path, rows) {
  if (!rows.length) return;
  const keys = Object.keys(rows[0]);
  const esc = v => `"${String(v ?? "").replace(/"/g, '""')}"`;
  fs.writeFileSync(path, [keys.join(","), ...rows.map(r => keys.map(k => esc(r[k])).join(","))].join("\n"), "utf8");
}
function main() {
  if (process.argv.includes("--help") || process.argv.includes("-h")) {
    console.log([
      "Uso: node tools/simulation/autonomous_economy_sim.js [opciones]",
      "",
      "  --players N       jugadores sintéticos (100 por defecto)",
      "  --days N          días simulados (365 por defecto)",
      "  --seed N          semilla reproducible",
      "  --profiles fresh|bots",
      "  --persona smart|steady|mixed",
      "  --max-parcels N   límite de apiarios por jugador (0 = sin límite)",
      "  --max-hives N     límite de colmenas por jugador (0 = sin límite)",
      "  --capital-multiplier N  escala la caja inicial (2=20000 B)",
      "  --hive-actions-per-day N  compras de colmenas por día (1 por defecto)",
      "  --hive-ramp SPEC         rampa, por ejemplo 5@100,7@200,10@365",
      "  --hive-priority         prioriza colmena antes de abrir apiarios",
      "  --start YYYY-MM-DD",
      "  --out RUTA        carpeta de resultados",
    ].join("\n"));
    return;
  }
  const count = intArg("players", 100);
  const days = intArg("days", 365);
  const seed = intArg("seed", 20260923);
  const profiles = String(arg("profiles", "fresh"));
  const persona = String(arg("persona", "smart"));
  const capitalMultiplier = Math.max(0.1, Number(arg("capital-multiplier", "1")) || 1);
  const hiveActionsPerDay = intArg("hive-actions-per-day", 1);
  const hiveRampSpec = arg("hive-ramp", null);
  const hiveRamp = parseHiveRamp(hiveRampSpec);
  const startingBalance = round2(STARTING_BALANCE * capitalMultiplier);
  const start = String(arg("start", "2026-01-01"));
  const capitalTag = profiles === "fresh" ? `-${capitalMultiplier}x` : "-bots";
  const priorityTag = boolArg("hive-priority") ? "-priority" : "";
  const rampTag = hiveRampSpec ? `-ramp${String(hiveRampSpec).replace(/[^0-9]+/g, "-")}` : "";
  const fixedActionTag = hiveRamp ? "" : `-h${hiveActionsPerDay}`;
  const runId = `${count}p-${days}d-${profiles}-${persona}-${seed}${capitalTag}${fixedActionTag}${rampTag}${priorityTag}`;
  const outDir = path.resolve(String(arg("out", path.join(ROOT, "tools", "simulation", "results", runId))));
  fs.mkdirSync(outDir, { recursive: true });
  const players = profiles === "bots" ? createBotPlayers(count, seed) : createFreshPlayers(count, persona, seed, startingBalance);
  const hivePriority = boolArg("hive-priority");
  for (const player of players) {
    player.hiveActionsPerDay = hiveActionsPerDay;
    player.hivePriority = hivePriority;
  }
  const maxParcels = nonNegativeIntArg("max-parcels", null);
  const maxHives = nonNegativeIntArg("max-hives", null);
  if (maxParcels !== null || maxHives !== null) {
    for (const player of players) {
      player.persona = {
        ...player.persona,
        maxParcels: maxParcels === null ? player.persona.maxParcels : maxParcels,
        maxHives: maxHives === null ? player.persona.maxHives : maxHives,
      };
    }
  }
  const market = new Market();
  const daily = [];
  const startDate = new Date(`${start}T00:00:00Z`);
  for (let dayIndex = 0; dayIndex < days; dayIndex++) {
    const date = new Date(startDate.getTime() + dayIndex * 86400000);
    market.prepare(date, players);
    const before = players.map(p => ({
      kgProduced: p.stats.kgProduced, kgSold: p.stats.kgSold,
      revenueMarket: p.stats.revenueMarket, revenueOrders: p.stats.revenueOrders,
      revenueContracts: p.stats.revenueContracts, orders: p.stats.orders,
      contracts: p.stats.contracts, contractsFailed: p.stats.contractsFailed,
      expenses: p.stats.expensesLand + p.stats.expensesHives + p.stats.expensesWarehouse
        + p.stats.expensesFleet + p.stats.expensesCrops + p.stats.expensesCare + p.stats.expensesOther,
    }));
    const dayActionLimit = hiveActionsForDay(hiveRamp, dayIndex + 1, hiveActionsPerDay);
    for (const player of players) runPlayerDay(player, date, dayIndex, market, hiveRamp);
    market.closeDay();
    daily.push(aggregateDay(players, market, date, dayIndex, before, dayActionLimit));
  }
  const finalRows = players.map(p => ({
    id: p.id, persona: p.personaName, level: p.level, xp: round2(p.xp), cash: round2(p.cash), netWorth: p.netWorth(market),
    parcels: p.parcels.length, warehouses: p.parcels.filter(x => x.warehouseLevel > 0).length, hives: p.hives().length,
    truckCount: p.hasTruck() ? 1 : 0, truckLevel: p.truckLevel, shipCount: p.shipLevel > 0 ? 1 : 0, shipLevel: p.shipLevel, stockKg: round2(p.totalStock()), producedKg: round2(p.stats.kgProduced),
    soldKg: round2(p.stats.kgSold), marketRevenue: round2(p.stats.revenueMarket), orderRevenue: round2(p.stats.revenueOrders),
    contractRevenue: round2(p.stats.revenueContracts), orders: p.stats.orders, contracts: p.stats.contracts,
    treesPlanted: p.stats.treesPlanted, cropsPlanted: p.stats.cropsPlanted,
    treeMaintenance: p.stats.treeMaintenance, splits: p.stats.splits, levelUps: p.stats.levelUps,
  })).sort((a, b) => b.netWorth - a.netWorth);
  const summary = {
    runId, players: count, days, start, profiles, persona, seed,
    hiveActionsPerDay,
    hiveRamp,
    hivePriority: boolArg("hive-priority"),
    assumptions: {
      note: "Simulación económica local; no usa Firebase ni el modelo completo de clima/rutas.",
      startingBalance: profiles === "bots" ? "estado bots" : startingBalance,
      capitalMultiplier: profiles === "bots" ? "no se aplica al estado bots" : capitalMultiplier,
      production: "proxy de producción pico × fracción típica × salud/reina/flora × estación",
      market: "mismo cupo regional y objetivo de rotación que HoneyMarketEngine",
      fairs: "desactivadas; no forman parte del modelo",
      advertising: "desactivada; no forma parte del modelo",
      orders: "una oportunidad diaria por jugador; precio de comanda = base × 1,12",
      pollination: "pago de llamada + pago diario; una colmena reservada; éxito probabilístico",
      crops: "anuales 450 B/2 días; árboles 1600 B/10 días; mantenimiento 350 B",
    },
    final: {
      totalCash: round2(sum(players.map(p => p.cash))), avgCash: round2(avg(players.map(p => p.cash))),
      medianCash: round2(median(players.map(p => p.cash))), p90Cash: round2(percentile(players.map(p => p.cash), 0.9)),
      totalNetWorth: round2(sum(players.map(p => p.netWorth(market)))), avgNetWorth: round2(avg(players.map(p => p.netWorth(market)))),
      medianNetWorth: round2(median(players.map(p => p.netWorth(market)))), p90NetWorth: round2(percentile(players.map(p => p.netWorth(market)), 0.9)),
      avgLevel: round2(avg(players.map(p => p.level))), medianLevel: round2(median(players.map(p => p.level))), maxLevel: Math.max(...players.map(p => p.level)),
      totalHives: sum(players.map(p => p.hives().length)), totalParcels: sum(players.map(p => p.parcels.length)),
      totalWarehouses: sum(players.map(p => p.parcels.filter(x => x.warehouseLevel > 0).length)),
      truckCount: sum(players.map(p => p.hasTruck() ? 1 : 0)), truckLevels: sum(players.map(p => p.truckLevel)),
      shipCount: sum(players.map(p => p.shipLevel > 0 ? 1 : 0)), shipLevels: sum(players.map(p => p.shipLevel)),
      totalStockKg: round2(sum(players.map(p => p.totalStock()))),
      totalProducedKg: round2(sum(players.map(p => p.stats.kgProduced))), totalSoldKg: round2(sum(players.map(p => p.stats.kgSold))),
      totalMarketRevenue: round2(sum(players.map(p => p.stats.revenueMarket))), totalOrderRevenue: round2(sum(players.map(p => p.stats.revenueOrders))),
      totalContractRevenue: round2(sum(players.map(p => p.stats.revenueContracts))), totalOrders: sum(players.map(p => p.stats.orders)),
      totalContracts: sum(players.map(p => p.stats.contracts)), totalContractFailures: sum(players.map(p => p.stats.contractsFailed)),
      treesPlanted: sum(players.map(p => p.stats.treesPlanted)), cropsPlanted: sum(players.map(p => p.stats.cropsPlanted)),
      treeMaintenance: sum(players.map(p => p.stats.treeMaintenance)), splits: sum(players.map(p => p.stats.splits)),
      playersAtLevel5: percentileCount(players, p => p.level >= 5), playersAtLevel10: percentileCount(players, p => p.level >= 10),
      playersAtLevel20: percentileCount(players, p => p.level >= 20), playersAtLevel40: percentileCount(players, p => p.level >= 40),
      playersWithTruck: percentileCount(players, p => p.hasTruck()), playersWithWarehouse: percentileCount(players, p => p.parcels.some(x => x.warehouseLevel > 0)),
    },
    top10: finalRows.slice(0, 10),
  };
  writeCsv(path.join(outDir, "daily.csv"), daily);
  writeCsv(path.join(outDir, "players.csv"), finalRows);
  fs.writeFileSync(path.join(outDir, "summary.json"), JSON.stringify(summary, null, 2), "utf8");
  console.log(JSON.stringify({ runId, outDir, final: summary.final, top10: summary.top10 }, null, 2));
}
main();
