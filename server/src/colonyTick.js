"use strict";

const WORKER_BROOD_DAYS = 22;
const MIN_HIVE_STOCK_KG = 0.5;
const TREAT_VARROA_KEEP = 0.78;
const FEED_CONSUMPTION_MULT = 0.5;
const QUEEN_DEVELOP_LAST = 16;
const VIRGIN_LAST = 11;

function num(value, fallback = 0) {
  const n = Number(value);
  return Number.isFinite(n) ? n : fallback;
}

function clamp(n, min, max) {
  return Math.max(min, Math.min(max, n));
}

function parsePop(hive) {
  try {
    const pop = JSON.parse(hive.population_state_json || "{}");
    return pop && typeof pop === "object" ? pop : {};
  } catch (err) {
    return {};
  }
}

function adultsOf(hive) {
  const pop = parsePop(hive);
  const fromW = num(pop.w, NaN);
  if (Number.isFinite(fromW) && fromW > 0) return Math.round(fromW);
  const fromLegacy = num(pop.workersAdult, NaN);
  if (Number.isFinite(fromLegacy) && fromLegacy > 0) return Math.round(fromLegacy);
  return Math.max(0, Math.round(num(hive.bee_count)));
}

function queenModeOf(pop) {
  const raw = String(pop.qm || "LAYING");
  return raw || "LAYING";
}

function biologicalDoy(calendarDoy, lat) {
  let d = clamp(calendarDoy, 1, 365);
  if (!(lat < 0)) return d;
  let x = d + 183;
  while (x > 365) x -= 365;
  return Math.max(1, x);
}

function lowHoneyScale(honeyKg, floor) {
  if (honeyKg >= MIN_HIVE_STOCK_KG - 1e-12) return 1;
  const t = Math.max(0, honeyKg) / MIN_HIVE_STOCK_KG;
  const f = clamp(floor, 0, 1);
  return Math.max(f, f + (1 - f) * t);
}

function bandMult(tempC, bands, overC) {
  if (tempC == null || !Number.isFinite(tempC)) return 1;
  if (overC != null && tempC > overC) return 0;
  if (!Array.isArray(bands)) return 1;
  for (const band of bands) {
    if (tempC < num(band.belowC)) return num(band.mult, 1);
  }
  return 0;
}

function tempHealthDelta(tempC) {
  if (tempC == null || !Number.isFinite(tempC)) return 0;
  if (tempC < -5) return -4.5;
  if (tempC < 0) return -3.6;
  if (tempC < 4) return -2.8;
  if (tempC < 8) return -0.35;
  if (tempC < 12) return -0.1;
  if (tempC < 16) return 0;
  if (tempC <= 28) return 0.45;
  if (tempC < 32) return -0.4;
  if (tempC < 36) return -1.5;
  if (tempC < 40) return -2.8;
  if (tempC < 45) return -3.8;
  return -4.5;
}

function tempKMult(tempC) {
  if (tempC == null || !Number.isFinite(tempC)) return 1;
  if (tempC < 0) return 0.22;
  if (tempC < 5) return 0.42;
  if (tempC < 10) return 0.72;
  if (tempC < 14) return 0.90;
  if (tempC <= 32) return 1;
  if (tempC < 36) return 0.70;
  if (tempC < 40) return 0.42;
  return 0.22;
}

function mortalityMult(health, tempC) {
  const h = clamp(health, 0, 100);
  let fromHealth = 1 + (100 - h) / 100 * 1.15;
  let fromTemp = 1;
  if (tempC != null && Number.isFinite(tempC)) {
    if (tempC < 8) fromTemp += Math.min(1.4, (8 - tempC) / 8 * 1.1);
    else if (tempC > 34) fromTemp += Math.min(1.6, (tempC - 34) / 8 * 1.2);
  }
  return fromHealth * fromTemp;
}

function broodMult(eggsLaid, health) {
  const floor = num(health.broodMultFloor, 0.03);
  const cap = num(health.broodMultCap, 1.15);
  const ref = Math.max(1, num(health.eggsRefPerDay, 1400));
  if (eggsLaid <= 0) return floor;
  return clamp(eggsLaid / ref, floor, cap);
}

function varroaEnvMult(hive, tempC, health) {
  let m = 1;
  const elev = num(hive.elevation_meters, -1);
  if (elev >= num(health.highElevVarroaStartM, 1500)) {
    m = num(health.highElevVarroaMult, 0.70);
  }
  if (tempC != null && Number.isFinite(tempC)) {
    if (tempC < num(health.varroaFreezeC, 0)) m *= num(health.varroaFreezeMult, 0.22);
    else if (tempC < num(health.varroaColdC, 8)) m *= num(health.varroaColdMult, 0.40);
    else if (tempC < num(health.varroaChillyC, 12)) m *= num(health.varroaChillyMult, 0.65);
  }
  return Math.max(num(health.varroaEnvMultFloor, 0.05), m);
}

function varroaHealthPenalty(varroaPct, hiveId, dayKey, uniform01) {
  if (varroaPct <= 2) return 0.05;
  if (varroaPct <= 5) return 0.2;
  if (varroaPct <= 10) return 0.5;
  return 1 + uniform01((hiveId || "") + ":vhp", dayKey);
}

function honeyReservePenalty(hive) {
  let p = 0;
  if (num(hive.honey_production) < MIN_HIVE_STOCK_KG) p += 0.55;
  const r = num(hive.reserves);
  if (r < 12) p += 0.35;
  else if (r < 25) p += 0.2;
  else if (r < 40) p += 0.08;
  return p;
}

function applyHealthAndVarroa(hive, dayKey, eggsLaid, tempC, inTransit, uniform01, balance) {
  const hbal = balance.health || {};
  if (num(hive.last_health_sim_day_key) >= dayKey) {
    return { healthDelta: 0, varroaDelta: 0 };
  }
  const healthStart = clamp(num(hive.health, 80), 0, 100);
  let varroa = num(hive.varroa_pct);
  if (!Number.isFinite(varroa) || varroa < 0) varroa = 2;
  if (num(hive.last_health_sim_day_key) === 0 && varroa <= 0) varroa = 2;
  const varroaStart = varroa;
  const cap = num(hbal.varroaCapPct, 40);
  const loadAmp = num(hbal.varroaLoadAmplifier, 1.6);
  const treating = num(hive.varroa_treatment_days_remaining) > 0;

  if (treating) {
    varroa *= TREAT_VARROA_KEEP;
    hive.varroa_treatment_days_remaining = num(hive.varroa_treatment_days_remaining) - 1;
    if (hive.varroa_treatment_days_remaining === 0) {
      hive.varroa_rebound_days_remaining = 0;
    }
  } else if (num(hive.varroa_rebound_days_remaining) > 0) {
    const burden = clamp(varroa / cap, 0, 1);
    const rel = num(hbal.varroaReboundRelativeDailyRate, 0.006)
      * varroaEnvMult(hive, tempC, hbal) * broodMult(eggsLaid, hbal) * (1 + loadAmp * burden);
    varroa *= 1 + rel;
    hive.varroa_rebound_days_remaining -= 1;
  } else {
    const burden = clamp(varroa / cap, 0, 1);
    const rel = num(hbal.varroaBaseRelativeDailyRate, 0.012)
      * varroaEnvMult(hive, tempC, hbal) * broodMult(eggsLaid, hbal) * (1 + loadAmp * burden);
    varroa *= 1 + rel;
  }
  hive.varroa_pct = clamp(varroa, 0, cap);

  let proposed = healthStart
    - varroaHealthPenalty(hive.varroa_pct, hive.id, dayKey, uniform01)
    - honeyReservePenalty(hive)
    + (treating ? 0.2 : 0)
    - (healthStart < 20 ? 0.5 : 0)
    + tempHealthDelta(tempC)
    - (inTransit ? 0.6 : 0);
  proposed = clamp(proposed, 0, 100);
  let delta = Math.round(proposed - healthStart);
  const maxD = Math.max(1, Math.round(num(hbal.maxHealthDeltaPerDay, 5)));
  delta = clamp(delta, -maxD, maxD);
  hive.health = clamp(healthStart + delta, 0, 100);
  hive.last_health_sim_day_key = dayKey;
  if (hive.health < 50) {
    hive.queen_genetic_quality = Math.max(0, num(hive.queen_genetic_quality) - 1);
  }
  return {
    healthDelta: hive.health - healthStart,
    varroaDelta: hive.varroa_pct - varroaStart,
  };
}

function expireFeeds(hive, dayKey) {
  if (num(hive.feed_honey_bonus_end_day_key_exclusive) > 0
      && dayKey >= num(hive.feed_honey_bonus_end_day_key_exclusive)) {
    hive.feed_honey_bonus_end_day_key_exclusive = 0;
    hive.feed_honey_bonus_multiplier = 1;
  }
  if (num(hive.feed_brood_bonus_end_day_key_exclusive) > 0
      && dayKey >= num(hive.feed_brood_bonus_end_day_key_exclusive)) {
    hive.feed_brood_bonus_end_day_key_exclusive = 0;
    hive.feed_brood_bonus_multiplier = 1;
  }
}

function broodFeedMult(hive, dayKey) {
  const end = num(hive.feed_brood_bonus_end_day_key_exclusive);
  if (end <= 0 || dayKey >= end) return 1;
  const m = num(hive.feed_brood_bonus_multiplier, 1);
  return m > 1.0001 ? m : 1;
}

function feedingActive(hive, dayKey) {
  return num(hive.feed_honey_bonus_end_day_key_exclusive) > dayKey;
}

function swarmRisk(adults, rules) {
  const base = num(rules.baseAdults, num(rules.baseBees, 40000));
  const capBees = num(rules.capAdults, num(rules.capBees, 52000));
  const step = Math.max(1, num(rules.stepAdults, num(rules.stepBees, 1000)));
  const dailyCap = num(rules.dailyCap, 0.35);
  if (adults <= base) return 0;
  const maxSteps = Math.max(1, Math.floor((Math.max(base + step, capBees) - base) / step));
  const steps = Math.floor((adults - base) / step);
  if (steps <= 0) return 0;
  if (steps >= maxSteps) return dailyCap;
  return steps * (dailyCap / maxSteps);
}

function advanceQueen(pop) {
  let mode = queenModeOf(pop);
  let qpd = num(pop.qpd);
  if (mode === "QUEEN_DEVELOPING") {
    qpd += 1;
    if (qpd > QUEEN_DEVELOP_LAST) {
      mode = "VIRGIN_PRE_LAYING";
      qpd = 0;
    }
  } else if (mode === "VIRGIN_PRE_LAYING") {
    qpd += 1;
    if (qpd > VIRGIN_LAST) {
      mode = "LAYING";
      qpd = 0;
      pop.dwel = 0;
    }
  }
  pop.qm = mode;
  pop.qpd = qpd;
}

function applyColonyDay(hive, dayKey, weather, helpers) {
  const { balance, lerpKnots, dayOfYear, uniform01, superCapKg } = helpers;
  const popBal = balance.population;
  const eggsBal = balance.eggs;
  const honeyBal = balance.honey;
  const swarmBal = balance.swarm || {};
  expireFeeds(hive, dayKey);
  if (num(hive.transhumance_arrives_day_key) > 0 && dayKey >= num(hive.transhumance_arrives_day_key)) {
    hive.transhumance_arrives_day_key = 0;
  }
  const inTransit = num(hive.transhumance_arrives_day_key) > dayKey;
  const tempC = weather && Number.isFinite(weather.tempC) ? weather.tempC : null;
  const pop = parsePop(hive);
  const before = adultsOf(hive);
  const doy = biologicalDoy(dayOfYear(dayKey), num(hive.lat));
  const eggsPreview = estimateEggs(hive, pop, before, doy, dayKey, tempC, eggsBal, uniform01, balance);
  const healthInfo = applyHealthAndVarroa(hive, dayKey, eggsPreview, tempC, inTransit, uniform01, balance);

  let mode = queenModeOf(pop);
  let n0 = Math.max(0, before);
  let swarmLoss = 0;
  let swarmed = false;
  const swarmStart = num(swarmBal.seasonStartDoy, 90);
  const swarmEnd = num(swarmBal.seasonEndDoy, 185);
  if (mode === "LAYING" && doy >= swarmStart && doy <= swarmEnd && n0 > num(swarmBal.baseAdults, 40000)) {
    const risk = swarmRisk(n0, swarmBal);
    if (uniform01((hive.id || "") + ":swarm", dayKey) < risk) {
      swarmLoss = Math.floor(n0 / 2);
      n0 -= swarmLoss;
      swarmed = true;
    }
  }
  if (mode === "LAYING" && uniform01((hive.id || "") + ":queenDeath", dayKey) < num(popBal.queenDailyDeathP, 0.0004)) {
    mode = "REPLACE_WINDOW";
    pop.rwd = 0;
    pop.qpd = 0;
  }
  pop.qm = mode;

  if (mode === "COLLAPSED") {
    const drop = Math.max(num(popBal.collapsedDailyDropMin, 40),
      n0 / Math.max(1, num(popBal.collapsedDailyDropDivisor, 18)));
    const next = Math.max(0, n0 - Math.round(drop));
    writePop(hive, pop, next, dayKey, swarmLoss + (n0 - next), 0, 0, "COLLAPSED");
    finishHoney(hive, dayKey, 0, 0, inTransit, weather, honeyBal, uniform01, superCapKg, balance);
    hive.last_summary_swarmed = swarmed;
    hive.last_summary_delta_health = healthInfo.healthDelta;
    hive.last_summary_delta_varroa = healthInfo.varroaDelta;
    return summaryOf(hive, 0, 0);
  }

  advanceQueen(pop);
  mode = queenModeOf(pop);
  if (mode === "REPLACE_WINDOW" && num(pop.rwd) < 4 && n0 >= 2500 && num(pop.dwel) <= 4) {
    pop.qm = "QUEEN_DEVELOPING";
    pop.qpd = 0;
    mode = "QUEEN_DEVELOPING";
  }

  const health = clamp(num(hive.health, 80), 0, 100);
  const queen = clamp(num(hive.queen_genetic_quality, 50), 0, 100);
  const honeyKg = Math.max(0, num(hive.honey_production));
  let k = lerpKnots(doy, popBal.kDoy, popBal.kAdults);
  k *= popBal.queenKFactorMin + popBal.queenKFactorSpan * (queen / 100);
  k *= popBal.healthKFactorMin + popBal.healthKFactorSpan * (health / 100);
  k *= tempKMult(tempC);
  const varroa = Math.max(0, num(hive.varroa_pct));
  if (varroa > popBal.varroaKStartPct) {
    const t = Math.min(1, (varroa - popBal.varroaKStartPct) / popBal.varroaKSpanPct);
    k *= 1 - popBal.varroaKMaxPenalty * t;
  }
  k *= lowHoneyScale(honeyKg, num(popBal.minHoneyKFactorFloor, 0.75));
  if (mode !== "LAYING") k *= num(popBal.notLayingKMultiplier, 0.35);
  if (mode === "COLLAPSED") k = num(popBal.minBeesCollapse, 400) * 0.25;
  const feed = broodFeedMult(hive, dayKey);
  if (feed > 1.0001) {
    k *= Math.min(num(popBal.feedBroodKCap, 1.12), feed);
  }
  k = clamp(Math.round(k), 0, popBal.maxAdultWorkersPerHive);

  let life = lerpKnots(doy, popBal.workerLifespanDoy, popBal.workerLifespanDays);
  life *= lowHoneyScale(honeyKg, num(popBal.lowHoneyLifespanFloor, 0.5));
  life = clamp(life, popBal.workerLifespanClampMin, popBal.workerLifespanClampMax);
  let deaths = Math.min(n0, Math.round((n0 / life) * mortalityMult(health, tempC)));
  const baseDeaths = Math.min(n0, Math.round((n0 / life) * mortalityMult(health, null)));
  const extraStarvation = Math.max(0, deaths - baseDeaths);

  let next;
  if (mode !== "LAYING") {
    let drop = deaths;
    if (mode === "ORPHANED") drop = Math.max(deaths, Math.round(deaths * 1));
    if (mode === "QUEEN_DEVELOPING" || mode === "VIRGIN_PRE_LAYING" || mode === "REPLACE_WINDOW") {
      drop = Math.max(1, Math.round(deaths * 0.55));
    }
    next = Math.max(0, n0 - drop);
  } else {
    const lambda = k >= n0 ? popBal.lambdaTowardK : popBal.lambdaTowardKDown;
    next = n0 + Math.round(lambda * (k - n0)) - extraStarvation;
    next = clamp(next, 0, popBal.maxAdultWorkersPerHive);
  }

  let net = next - n0;
  let emergences = deaths + net;
  if (emergences < 0) {
    deaths -= emergences;
    emergences = 0;
  }
  if (next > popBal.maxAdultWorkersPerHive) {
    deaths += next - popBal.maxAdultWorkersPerHive;
    next = popBal.maxAdultWorkersPerHive;
  }

  const laid = mode === "LAYING"
    ? estimateEggs(hive, pop, n0, doy, dayKey, tempC, eggsBal, uniform01, balance)
    : 0;
  if (mode === "LAYING") pop.dwel = 0;
  else pop.dwel = num(pop.dwel) + 1;
  if (mode === "REPLACE_WINDOW") {
    pop.rwd = num(pop.rwd) + 1;
    if (pop.rwd >= 4) pop.qm = "ORPHANED";
  }
  if (next < num(popBal.minBeesCollapse, 400)) {
    pop.qm = "COLLAPSED";
  }

  writePop(hive, pop, next, dayKey, swarmLoss + deaths, emergences, laid, pop.qm);
  const forageCons = finishHoney(hive, dayKey, next, laid, inTransit, weather, honeyBal, uniform01, superCapKg, balance);
  hive.last_summary_swarmed = swarmed;
  hive.last_summary_delta_health = healthInfo.healthDelta;
  hive.last_summary_delta_varroa = healthInfo.varroaDelta;
  return summaryOf(hive, forageCons.forage, forageCons.consumption);
}

function estimateEggs(hive, pop, adults, doy, dayKey, tempC, eggsBal, uniform01, balance) {
  if (queenModeOf(pop) !== "LAYING" || adults <= 0) return 0;
  const queen = clamp(num(hive.queen_genetic_quality, 50), 0, 100);
  const health = clamp(num(hive.health, 80), 0, 100);
  let base = lerpLike(doy, eggsBal.doy, eggsBal.base);
  base *= 0.55 + 0.45 * (queen / 100);
  const strength = Math.min(1, adults / num(eggsBal.strengthRefAdults, 38000));
  base *= 0.35 + 0.65 * strength;
  base *= 0.5 + 0.5 * (health / 100);
  if (num(hive.varroa_pct) > 8) {
    const t = Math.min(1, (num(hive.varroa_pct) - 8) / 22);
    base *= 1 - 0.35 * t;
  }
  base *= lowHoneyScale(Math.max(0, num(hive.honey_production)), num(eggsBal.lowHoneyFactorFloor, 0.55));
  const bands = (balance && balance.layingBands) || eggsBal.layingBands;
  base *= bandMult(tempC, bands, 50);
  const feed = broodFeedMult(hive, dayKey);
  if (feed > 1.0001) base *= feed;
  base *= num(eggsBal.layNoiseMin, 0.96)
    + uniform01((hive.id || "_") + ":lay", dayKey) * num(eggsBal.layNoiseSpan, 0.08);
  const room = 80000 - adults;
  const maxEggs = Math.max(0, Math.min(num(eggsBal.maxPerDay, 2000),
    room + num(eggsBal.maxPerDayRoomBonus, 1800)));
  return clamp(Math.round(base), 0, maxEggs);
}

function lerpLike(doy, knots, values) {
  if (!knots || !values) return 0;
  const x = clamp(doy, knots[0], knots[knots.length - 1]);
  for (let i = 1; i < knots.length; i++) {
    if (x <= knots[i]) {
      const span = knots[i] - knots[i - 1] || 1;
      const t = (x - knots[i - 1]) / span;
      return values[i - 1] + (values[i] - values[i - 1]) * t;
    }
  }
  return values[values.length - 1];
}

function writePop(hive, pop, adults, dayKey, deaths, emergences, laid, mode) {
  const next = Math.max(0, Math.round(adults));
  const eggs = Math.max(0, Math.round(laid || 0));
  pop.w = next;
  pop.workersAdult = next;
  pop.b = Array.from({ length: WORKER_BROOD_DAYS }, () => eggs);
  pop.lpk = dayKey;
  pop.lwd = Math.max(0, Math.round(deaths || 0));
  pop.lwe = Math.max(0, Math.round(emergences || 0));
  pop.lel = eggs;
  if (mode) pop.qm = mode;
  hive.population_state_json = JSON.stringify(pop);
  hive.bee_count = next + eggs * WORKER_BROOD_DAYS;
  hive.last_summary_day_key = dayKey;
  hive.last_summary_worker_deaths = pop.lwd;
  hive.last_summary_worker_emergences = pop.lwe;
  hive.last_summary_eggs_laid = eggs;
  hive.last_summary_delta_bees = pop.lwe - pop.lwd;
}

function finishHoney(hive, dayKey, adults, laid, inTransit, weather, honeyBal, uniform01, superCapKg, balance) {
  const health = clamp(num(hive.health, 80), 0, 100);
  const noise = num(honeyBal.nectarNoiseMin, 0.85)
    + uniform01((hive.id || "_") + ":nectar", dayKey) * num(honeyBal.nectarNoiseSpan, 0.3);
  let sky = 1;
  if (weather) {
    if (weather.mm >= 5) sky = 0;
    else if (weather.code != null && weather.code <= 1) sky = 1.25;
    else if (weather.clouds != null && weather.clouds >= 80) sky = 0.85;
  }
  const tempForage = bandMult(weather && weather.tempC, (balance && balance.temperatureBands) || honeyBal.temperatureBands, null);
  let forage = 0;
  if (!inTransit && adults > 0) {
    forage = Math.max(0, adults * num(honeyBal.foragerFraction) * num(honeyBal.kgPerForagerFullFlow)
      * (0.55 + 0.45 * (health / 100)) * noise * sky * (tempForage || 1));
  }
  let consumption = num(honeyBal.consumptionBaseKg)
    + adults * num(honeyBal.consumptionPerAdultKg)
    + laid * WORKER_BROOD_DAYS * num(honeyBal.consumptionPerBroodEqKg);
  if (feedingActive(hive, dayKey)) consumption *= FEED_CONSUMPTION_MULT;
  const honeyNet = forage - consumption;
  const cap = superCapKg(hive.super_count);
  hive.honey_production = Math.round(clamp(num(hive.honey_production) + honeyNet, 0, cap) * 1000) / 1000;
  hive.last_summary_honey_kg = Math.round(honeyNet * 1000) / 1000;
  hive.last_summary_worker_deaths = num(parsePop(hive).lwd);
  hive.last_summary_worker_emergences = num(parsePop(hive).lwe);
  hive.last_summary_eggs_laid = num(parsePop(hive).lel);
  const w = num(parsePop(hive).w);
  const beforeApprox = w - (hive.last_summary_worker_emergences - hive.last_summary_worker_deaths);
  hive.last_summary_delta_bees = hive.last_summary_worker_emergences - hive.last_summary_worker_deaths;
  return { forage, consumption, beforeApprox };
}

function summaryOf(hive, forage, consumption) {
  return {
    hiveId: hive.id,
    hiveName: hive.name || "",
    floraType: hive.flora_type || "Mil flores",
    honeyKg: hive.last_summary_honey_kg,
    forageKg: Math.round(num(forage) * 1000) / 1000,
    consumptionKg: Math.round(num(consumption) * 1000) / 1000,
    workerNet: hive.last_summary_delta_bees,
    eggsLaid: hive.last_summary_eggs_laid,
    beeCount: hive.bee_count,
    honeyStockKg: hive.honey_production,
    health: hive.health,
    varroaPct: hive.varroa_pct,
  };
}

function writeAdultsCompat(hive, adults, dayKey, deaths, emergences, laid) {
  const pop = parsePop(hive);
  writePop(hive, pop, adults, dayKey, deaths, emergences, laid, queenModeOf(pop));
}

module.exports = {
  applyColonyDay,
  applyHealthAndVarroa,
  adultsOf,
  parsePop,
  writeAdults: writeAdultsCompat,
  WORKER_BROOD_DAYS,
};
