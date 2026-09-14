#!/usr/bin/env node
/**
 * Granja de 20 bots autónomos para apiculture-simulator.
 *
 * Comandos:
 *   node tools/bots/bot_farm.js ensure
 *   node tools/bots/bot_farm.js tick [--live]
 *   node tools/bots/bot_farm.js simulate --days 30 [--live]
 *   node tools/bots/bot_farm.js loop --hours 4
 *
 * Personalidades (persona):
 *   casual | regular | competitive  — ver lib/personas.js
 */
"use strict";

const fs = require("fs");
const https = require("https");
const path = require("path");

const { RULES } = require("./lib/rules");
const { PERSONAS, personaForBotId } = require("./lib/personas");
const {
  ensureBotShape,
  planDay,
  applyTerrainPurchase,
  makeLocalHive,
  round2,
} = require("./lib/brain");

const ROOT = path.resolve(__dirname, "..", "..");
const GOOGLE_SERVICES = path.join(ROOT, "app", "google-services.json");
const STATE_PATH = path.join(__dirname, "state.json");
const HEX_IDS_PATH = path.join(__dirname, "hex_ids.json");
const HEX_CENTERS_PATH = path.join(__dirname, "hex_centers.json");
const PROJECT_ID = "apiculture-simulator";

const ROSTER = [
  ["Inés del Valle", "Miel del Altiplano", "Europe/Madrid", ["Mil flores", "Romero"]],
  ["Marc Rovira", "Romería Dorada", "Europe/Madrid", ["Romero", "Tomillo"]],
  ["Lila Mthethwa", "Fynbos Gold", "Africa/Johannesburg", ["Fynbos", "Aloe"]],
  ["João Ferreira", "Serra do Mel", "Europe/Lisbon", ["Eucalipto", "Mil flores"]],
  ["Carmen Soto", "Azahar Vivo", "Europe/Madrid", ["Campo de naranjos", "Lavanda"]],
  ["Pieter Botha", "Karoo Nectar", "Africa/Johannesburg", ["Lucerna", "Acacia"]],
  ["Núria Casals", "Bruc i Mel", "Europe/Madrid", ["Brezo", "Arboç"]],
  ["Ander Etxeberria", "Euskal Erlea", "Europe/Madrid", ["Bosque", "Castaño"]],
  ["Thandi Nkosi", "Highveld Hive", "Africa/Johannesburg", ["Macadamia", "Litchi"]],
  ["Hugo Belmonte", "Encina y Sol", "Europe/Madrid", ["Mielato de encina y roble", "Tomillo"]],
  ["Ainhoa Larralde", "Itsasoko Ezti", "Europe/Madrid", ["Bosque", "Eucalipto"]],
  ["Sipho Dlamini", "Drakensberg Mel", "Africa/Johannesburg", ["Fynbos", "Eucalipto"]],
  ["Paloma Rivas", "Lavanda del Sur", "Europe/Madrid", ["Lavanda", "Campo de girasoles"]],
  ["Gorka Mendizabal", "Gorbeia Eztiak", "Europe/Madrid", ["Brezo", "Neret"]],
  ["Anika van Zyl", "Cape Blossom", "Africa/Johannesburg", ["Aloe", "Campo de Colza"]],
  ["Tomás Quintero", "Dehesa Dulce", "Europe/Madrid", ["Campo de almendros", "Romero"]],
  ["Elisa Moreira", "Mel do Minho", "Europe/Lisbon", ["Castaño", "Campo de manzanos"]],
  ["Kwame Ndlovu", "Savanna Comb", "Africa/Johannesburg", ["Acacia", "Lucerna"]],
  ["Beatriz Olmedo", "Tomillar Viejo", "Europe/Madrid", ["Tomillo", "Campo de cerezos"]],
  ["Joris Steyn", "Cederberg Honey", "Africa/Johannesburg", ["Fynbos", "Macadamia"]],
];

function loadApiKey() {
  if (!fs.existsSync(GOOGLE_SERVICES)) throw new Error("Falta app/google-services.json");
  const data = JSON.parse(fs.readFileSync(GOOGLE_SERVICES, "utf8"));
  return data.client[0].api_key[0].current_key;
}

function docIdForLookup(s) {
  const n = String(s)
    .trim()
    .toLowerCase()
    .normalize("NFD")
    .replace(/\p{M}+/gu, "")
    .replace(/[^a-z0-9]+/g, "_")
    .replace(/^_+|_+$/g, "");
  return n.slice(0, 600);
}

function requestJson(method, urlStr, body, token) {
  return new Promise((resolve, reject) => {
    const u = new URL(urlStr);
    const payload = body == null ? null : JSON.stringify(body);
    const headers = { "Content-Type": "application/json" };
    if (token) headers.Authorization = `Bearer ${token}`;
    if (payload) headers["Content-Length"] = Buffer.byteLength(payload);
    const req = https.request(
      {
        method,
        hostname: u.hostname,
        path: u.pathname + u.search,
        headers,
      },
      (res) => {
        let raw = "";
        res.on("data", (c) => (raw += c));
        res.on("end", () => {
          if (res.statusCode >= 200 && res.statusCode < 300) {
            resolve(raw ? JSON.parse(raw) : {});
            return;
          }
          reject(new Error(`${method} ${urlStr} -> ${res.statusCode} ${raw.slice(0, 400)}`));
        });
      }
    );
    req.on("error", reject);
    if (payload) req.write(payload);
    req.end();
  });
}

function authSignUp(apiKey, email, password) {
  return requestJson(
    "POST",
    `https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=${apiKey}`,
    { email, password, returnSecureToken: true }
  );
}

function authSignIn(apiKey, email, password) {
  return requestJson(
    "POST",
    `https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=${apiKey}`,
    { email, password, returnSecureToken: true }
  );
}

function fsUrl(...parts) {
  const segs = parts.map((p) => encodeURIComponent(p)).join("/");
  return `https://firestore.googleapis.com/v1/projects/${PROJECT_ID}/databases/(default)/documents/${segs}`;
}

function toFirestoreValue(v) {
  if (typeof v === "boolean") return { booleanValue: v };
  if (typeof v === "number" && Number.isInteger(v)) return { integerValue: String(v) };
  if (typeof v === "number") return { doubleValue: v };
  if (typeof v === "string") return { stringValue: v };
  if (Array.isArray(v)) return { arrayValue: { values: v.map(toFirestoreValue) } };
  if (v && typeof v === "object") {
    const fields = {};
    for (const [k, val] of Object.entries(v)) {
      if (val == null) continue;
      fields[k] = toFirestoreValue(val);
    }
    return { mapValue: { fields } };
  }
  return { stringValue: String(v) };
}

function toFields(values) {
  const fields = {};
  for (const [k, v] of Object.entries(values)) {
    if (v == null) continue;
    fields[k] = toFirestoreValue(v);
  }
  return { fields };
}

function fromFields(doc) {
  const out = {};
  for (const [k, v] of Object.entries((doc && doc.fields) || {})) {
    if (v.stringValue != null) out[k] = v.stringValue;
    else if (v.integerValue != null) out[k] = Number(v.integerValue);
    else if (v.doubleValue != null) out[k] = v.doubleValue;
    else if (v.booleanValue != null) out[k] = v.booleanValue;
  }
  return out;
}

async function fsGet(token, ...parts) {
  try {
    return await requestJson("GET", fsUrl(...parts), null, token);
  } catch (e) {
    if (String(e.message).includes(" -> 404 ")) return null;
    throw e;
  }
}

async function fsPatch(token, fields, ...parts) {
  const keys = Object.keys(fields);
  const q = keys.map((k) => "updateMask.fieldPaths=" + encodeURIComponent(k)).join("&");
  await requestJson("PATCH", fsUrl(...parts) + (q ? "?" + q : ""), toFields(fields), token);
}

async function fsCreate(token, collection, docId, fields) {
  const url =
    `https://firestore.googleapis.com/v1/projects/${PROJECT_ID}/databases/(default)/documents/` +
    `${encodeURIComponent(collection)}?documentId=${encodeURIComponent(docId)}`;
  await requestJson("POST", url, toFields(fields), token);
}

function loadState() {
  if (fs.existsSync(STATE_PATH)) return JSON.parse(fs.readFileSync(STATE_PATH, "utf8"));
  return { bots: [], simDay: 0 };
}

function saveState(state) {
  fs.writeFileSync(STATE_PATH, JSON.stringify(state, null, 2), "utf8");
}

function randomPassword() {
  const alphabet = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
  let s = "B!";
  for (let i = 0; i < 18; i++) s += alphabet[Math.floor(Math.random() * alphabet.length)];
  return s;
}

function sleep(ms) {
  return new Promise((r) => setTimeout(r, ms));
}

function honeyStock(bot) {
  return Object.values(bot.honeyByFlora || {}).reduce((a, b) => a + b, 0);
}

function honeyBucketsJson(bot) {
  return JSON.stringify(bot.honeyByFlora || {});
}

function utcDayKey(d = new Date()) {
  const y = d.getUTCFullYear();
  const m = String(d.getUTCMonth() + 1).padStart(2, "0");
  const day = String(d.getUTCDate()).padStart(2, "0");
  return Number(`${y}${m}${day}`);
}

function hexPool() {
  if (!hexPool.cache) hexPool.cache = JSON.parse(fs.readFileSync(HEX_IDS_PATH, "utf8"));
  return hexPool.cache;
}

function hexCenters() {
  if (!hexCenters.cache) {
    hexCenters.cache = fs.existsSync(HEX_CENTERS_PATH)
      ? JSON.parse(fs.readFileSync(HEX_CENTERS_PATH, "utf8"))
      : {};
  }
  return hexCenters.cache;
}

function coordsForHex(hexId) {
  const c = hexCenters()[hexId];
  if (c) return { lat: c.lat + (Math.random() - 0.5) * 0.01, lng: c.lng + (Math.random() - 0.5) * 0.01 };
  if (String(hexId).includes("_za_")) return { lat: -30.5 + Math.random(), lng: 24 + Math.random() };
  return { lat: 40 + Math.random(), lng: -4 + Math.random() };
}

function hexCandidatesForBot(bot, expandSecondary) {
  const pool = hexPool();
  const south = bot.timeZoneId && bot.timeZoneId.startsWith("Africa");
  const primary = south ? pool.za : pool.iberia;
  const secondary = south ? pool.iberia : pool.za;
  const start = (Number(bot.id) * 11) % primary.length;
  const ordered = primary.slice(start).concat(primary.slice(0, start));
  if (expandSecondary) return ordered.concat(secondary);
  return ordered;
}

async function publishProfile(token, bot) {
  const uid = bot.uid;
  const pairs = [
    ["uniqueHoneyBrands", docIdForLookup(bot.honeyBrand)],
    ["uniquePlayerNames", docIdForLookup(bot.playerName)],
  ];
  for (const [coll, key] of pairs) {
    const existing = await fsGet(token, coll, key);
    if (!existing) {
      try {
        await fsCreate(token, coll, key, { ownerUid: uid });
      } catch (e) {
        if (!String(e.message).includes("ALREADY_EXISTS") && !String(e.message).includes("409")) throw e;
      }
    } else {
      const owner = fromFields(existing).ownerUid;
      if (owner && owner !== uid) throw new Error(`Nombre/marca ocupados: ${coll}/${key}`);
    }
  }
  await fsPatch(
    token,
    {
      honeyBrand: bot.honeyBrand,
      playerName: bot.playerName,
      profileComplete: true,
      timeZoneId: bot.timeZoneId,
      playerLevel: bot.level | 0,
      playerXp: bot.xp | 0,
      economyBalanceEur: Number(bot.balanceEur),
      economyHoneyBucketsJson: honeyBucketsJson(bot),
      isBot: true,
      botPersona: bot.persona || personaForBotId(bot.id).id,
    },
    "users",
    uid
  );
}

async function publishPlayer(token, bot) {
  await fsPatch(
    token,
    {
      playerName: bot.playerName,
      nickname: bot.playerName,
      honeyBrand: bot.honeyBrand,
      level: bot.level | 0,
      xp: bot.xp | 0,
      honeyStockKg: round2(honeyStock(bot)),
      hiveCount: (bot.hives && bot.hives.length) || bot.hiveCount || 0,
      adultBeeCount: bot.adultBeeCount | 0,
      totalHoneyKg: round2(honeyStock(bot)),
      isBot: true,
      botPersona: bot.persona || personaForBotId(bot.id).id,
    },
    "players",
    bot.uid
  );
}

function hiveDocFields(bot, hive) {
  const { lat, lng } = coordsForHex(hive.hexId);
  return {
    id: hive.id,
    ownerId: bot.uid,
    name: hive.name,
    beeCount: hive.beeCount | 0,
    health: hive.health | 0,
    honeyProduction: Number(hive.honeyProduction) || 0,
    reserves: 0,
    queenAgeDays: 0,
    queenGeneticQuality: 70 + Math.floor(Math.random() * 20),
    lat,
    lng,
    hexId: hive.hexId,
    elevationMeters: 200 + Math.floor(Math.random() * 400),
    floraType: hive.floraType || "Mil flores",
    superCount: hive.superCount | 0,
    varroaPct: Number(hive.varroaPct) || 2,
    varroaTreatmentDaysRemaining: 0,
    varroaReboundDaysRemaining: 0,
    lastHealthSimDayKey: 0,
    lastSummaryDayKey: 0,
    lastSummaryHoneyKg: 0,
    lastSummaryDeltaBees: 0,
    lastSummaryDeltaHealth: 0,
    lastSummaryDeltaVarroa: 0,
    lastSummaryWorkerDeaths: 0,
    lastSummaryWorkerEmergences: 0,
    lastSummaryEggsLaid: 0,
    lastSummarySwarmed: false,
    feedHoneyBonusEndDayKeyExclusive: 0,
    feedHoneyBonusMultiplier: 1,
    feedBroodBonusEndDayKeyExclusive: 0,
    feedBroodBonusMultiplier: 1,
    transhumanceArrivesDayKey: 0,
  };
}

async function publishHive(token, bot, hive) {
  const fields = hiveDocFields(bot, hive);
  const existing = await fsGet(token, "hives", hive.id);
  if (!existing) {
    try {
      await fsCreate(token, "hives", hive.id, fields);
    } catch (e) {
      if (!String(e.message).includes("ALREADY_EXISTS") && !String(e.message).includes("409")) throw e;
      await fsPatch(token, fields, "hives", hive.id);
    }
  } else {
    await fsPatch(token, fields, "hives", hive.id);
  }
  hive.needsPublish = false;
}

async function sellToMarket(token, flora, kg) {
  const day = utcDayKey();
  const dayS = String(day);
  const doc = await fsGet(token, "globalHoneyMarket", dayS, "floraSalesUtc", flora);
  let soldBefore = 0;
  if (doc) soldBefore = Number(fromFields(doc).kgSold || 0);
  const payload = { kgSold: Math.round((soldBefore + kg) * 1000) / 1000, marketDayKey: day };
  if (!doc) {
    const url =
      `https://firestore.googleapis.com/v1/projects/${PROJECT_ID}/databases/(default)/documents/` +
      `globalHoneyMarket/${dayS}/floraSalesUtc?documentId=${encodeURIComponent(flora)}`;
    try {
      await requestJson("POST", url, toFields(payload), token);
      return;
    } catch (e) {
      if (!String(e.message).includes("ALREADY_EXISTS") && !String(e.message).includes("409")) throw e;
    }
  }
  await fsPatch(token, payload, "globalHoneyMarket", dayS, "floraSalesUtc", flora);
}

async function claimTerrainLive(token, bot, flora, price, expandSecondary) {
  const firstName = String(bot.playerName).split(" ")[0];
  const parcelName = `Apiario ${firstName}`;
  const now = Date.now();
  for (const hexId of hexCandidatesForBot(bot, expandSecondary)) {
    if ((bot.ownedHexIds || []).includes(hexId)) continue;
    const existing = await fsGet(token, "hexParcels", hexId);
    if (existing) {
      const owner = fromFields(existing).ownerId;
      if (owner === bot.uid && !(bot.ownedHexIds || []).includes(hexId)) {
        bot.ownedHexIds.push(hexId);
      }
      continue;
    }
    try {
      await fsCreate(token, "hexParcels", hexId, {
        ownerId: bot.uid,
        hexId,
        parcelName,
        floras: [{ floraKey: flora, plantedAt: now, readyAt: now }],
      });
    } catch (e) {
      const msg = String(e.message);
      if (msg.includes("ALREADY_EXISTS") || msg.includes("409")) continue;
      throw e;
    }
    applyTerrainPurchase(bot, hexId, flora, price);
    return hexId;
  }
  return null;
}

function claimTerrainLocal(bot, flora, price, expandSecondary, occupied) {
  for (const hexId of hexCandidatesForBot(bot, expandSecondary)) {
    if ((bot.ownedHexIds || []).includes(hexId)) continue;
    if (occupied.has(hexId)) continue;
    occupied.add(hexId);
    applyTerrainPurchase(bot, hexId, flora, price);
    return hexId;
  }
  return null;
}

async function ensureBots(apiKey) {
  const state = loadState();
  const bots = new Map((state.bots || []).map((b) => [Number(b.id), b]));
  for (let i = 0; i < ROSTER.length; i++) {
    const id = i + 1;
    const [name, brand, tz, floras] = ROSTER[i];
    const persona = personaForBotId(id);
    if (bots.has(id) && bots.get(id).uid) {
      const b = ensureBotShape(bots.get(id));
      b.persona = persona.id;
      b.playerName = name;
      b.honeyBrand = brand;
      b.timeZoneId = tz;
      b.homeFloras = floras;
      bots.set(id, b);
      console.log(`[${String(id).padStart(2, "0")}] ok ${name} · ${persona.label}`);
      continue;
    }
    const email = `simbot${String(id).padStart(2, "0")}@apiculture-simulator.web.app`;
    let password = randomPassword();
    let auth;
    try {
      auth = await authSignUp(apiKey, email, password);
    } catch (e) {
      if (!String(e.message).includes("EMAIL_EXISTS")) throw e;
      if (bots.has(id) && bots.get(id).password) password = bots.get(id).password;
      auth = await authSignIn(apiKey, email, password);
    }
    const bot = ensureBotShape({
      id,
      email,
      password,
      uid: auth.localId,
      playerName: name,
      honeyBrand: brand,
      timeZoneId: tz,
      homeFloras: floras,
      persona: persona.id,
      level: 0,
      xp: 0,
      balanceEur: RULES.STARTING_BALANCE,
      hiveCount: 0,
      adultBeeCount: 0,
      honeyByFlora: {},
      ownedHexIds: [],
      hives: [],
      lastTickDateUtc: null,
      missedDays: 0,
    });
    for (const f of floras) bot.honeyByFlora[f] = 0;
    await publishProfile(auth.idToken, bot);
    const hex = await claimTerrainLive(
      auth.idToken,
      bot,
      floras[0] || "Mil flores",
      require("./lib/rules").terrainPrice(floras[0] || "Mil flores"),
      false
    );
    if (hex && !bot.hives.length) {
      const hive = makeLocalHive(bot, hex, floras[0] || "Mil flores", 0, 0);
      bot.hives.push(hive);
      bot.hiveCount = 1;
      bot.adultBeeCount = hive.beeCount;
      await publishHive(auth.idToken, bot, hive);
    }
    await publishPlayer(auth.idToken, bot);
    bots.set(id, bot);
    state.bots = [...bots.values()].sort((a, b) => a.id - b.id);
    saveState(state);
    console.log(
      `[${String(id).padStart(2, "0")}] creado ${name} (${persona.label})${hex ? " · " + hex : ""}`
    );
    await sleep(350);
  }
  state.bots = [...bots.values()].sort((a, b) => a.id - b.id).map(ensureBotShape);
  saveState(state);
  return state;
}

async function executeDay(bot, dayKey, opts) {
  const live = !!opts.live;
  const occupied = opts.occupied || new Set();
  const planned = planDay(bot, dayKey, opts);
  const token = opts.token || null;
  const notes = [];

  if (!planned.loggedIn) {
    notes.push("ausente");
    return notes.join("; ");
  }

  for (const a of planned.actions) {
    if (a.type === "want_terrain") {
      let hex = null;
      if (live && token) {
        hex = await claimTerrainLive(token, bot, a.flora, a.price, a.expandSecondary);
      } else {
        hex = claimTerrainLocal(bot, a.flora, a.price, a.expandSecondary, occupied);
      }
      if (hex) {
        notes.push(`terreno ${hex}`);
        // Si acabamos de comprar y no hay colmena, planDay no la creó en buy_hive
        if (bot.hives.some((h) => h.hexId === hex && h.needsPublish) && live && token) {
          for (const h of bot.hives) {
            if (h.needsPublish) await publishHive(token, bot, h);
          }
        }
      } else {
        notes.push("sin terreno libre");
      }
      continue;
    }
    if (a.type === "sell") {
      if (live && token) {
        await sellToMarket(token, a.flora, a.kg);
      }
      notes.push(`vende ${a.kg}kg ${a.flora}`);
      continue;
    }
    if (a.type === "buy_hive") {
      notes.push(`+colmena ${a.flora}`);
      continue;
    }
    if (a.type === "buy_super") {
      notes.push(`+alza`);
      continue;
    }
    if (a.type === "harvest") {
      notes.push(`cosecha ${a.kg}kg`);
      continue;
    }
  }

  if (live && token) {
    for (const h of bot.hives) {
      if (h.needsPublish || true) {
        // Publicar estado de miel/abejas cada tick live
        await publishHive(token, bot, h);
        await sleep(40);
      }
    }
    await publishProfile(token, bot);
    await publishPlayer(token, bot);
  }

  bot.lastTickDateUtc = String(dayKey);
  bot.hiveCount = bot.hives.length;
  bot.adultBeeCount = bot.hives.reduce((s, h) => s + (h.beeCount || 0), 0);
  const head = `${planned.persona.label} · nv.${bot.level} · ${bot.hiveCount} col · ${round2(bot.balanceEur)}€`;
  return notes.length ? `${head} · ${notes.slice(0, 5).join(", ")}` : head;
}

async function cmdTick(apiKey, live) {
  const state = loadState();
  if (!state.bots || !state.bots.length) {
    throw new Error("No hay bots. Ejecuta: node tools/bots/bot_farm.js ensure");
  }
  const dayKey = utcDayKey();
  const occupied = new Set();
  for (const b of state.bots) for (const h of b.ownedHexIds || []) occupied.add(h);

  for (const bot of state.bots) {
    ensureBotShape(bot);
    try {
      let token = null;
      if (live) {
        const auth = await authSignIn(apiKey, bot.email, bot.password);
        bot.uid = auth.localId;
        token = auth.idToken;
      }
      const msg = await executeDay(bot, dayKey, { live, token, occupied });
      console.log(`[${String(bot.id).padStart(2, "0")}] ${bot.playerName}: ${msg}`);
    } catch (e) {
      console.log(`[${String(bot.id).padStart(2, "0")}] ${bot.playerName}: ERROR ${e.message}`);
    }
    saveState(state);
    await sleep(live ? 400 : 5);
  }
}

async function cmdSimulate(apiKey, days, live) {
  const state = await ensureBots(apiKey);
  const start = state.simDay || 0;
  const occupied = new Set();
  for (const b of state.bots) for (const h of b.ownedHexIds || []) occupied.add(h);

  console.log(`\n=== Simulación ${days} días · live=${!!live} ===\n`);
  const totals = { login: 0, skip: 0, terrain: 0, hives: 0, sells: 0 };

  for (let d = 1; d <= days; d++) {
    const dayKey = 20260100 + start + d;
    console.log(`— Día ${d}/${days} —`);
    for (const bot of state.bots) {
      ensureBotShape(bot);
      const beforeHives = bot.hives.length;
      const beforeHex = (bot.ownedHexIds || []).length;
      try {
        let token = null;
        if (live) {
          const auth = await authSignIn(apiKey, bot.email, bot.password);
          bot.uid = auth.localId;
          token = auth.idToken;
        }
        const msg = await executeDay(bot, dayKey + bot.id * 31, { live, token, occupied });
        if (msg.includes("ausente")) totals.skip += 1;
        else totals.login += 1;
        if (bot.hives.length > beforeHives) totals.hives += bot.hives.length - beforeHives;
        if ((bot.ownedHexIds || []).length > beforeHex) totals.terrain += 1;
        if (msg.includes("vende")) totals.sells += 1;
        if (d === days || d % 5 === 0 || d === 1) {
          console.log(`  [${String(bot.id).padStart(2, "0")}] ${msg}`);
        }
      } catch (e) {
        console.log(`  [${String(bot.id).padStart(2, "0")}] ERROR ${e.message}`);
      }
      if (live) await sleep(250);
    }
    saveState(state);
  }

  state.simDay = start + days;
  saveState(state);

  console.log("\n=== Resumen final ===");
  const byPersona = { casual: [], regular: [], competitive: [] };
  for (const bot of state.bots) {
    ensureBotShape(bot);
    const p = bot.persona || personaForBotId(bot.id).id;
    if (!byPersona[p]) byPersona[p] = [];
    byPersona[p].push(bot);
    console.log(
      `[${String(bot.id).padStart(2, "0")}] ${bot.playerName.padEnd(18)} ${String(p).padEnd(12)} ` +
        `nv.${bot.level}  ${String(bot.hives.length).padStart(2)} col  ` +
        `${String((bot.ownedHexIds || []).length)} hex  ${round2(bot.balanceEur).toFixed(0).padStart(6)}€  ` +
        `miel ${round2(honeyStock(bot)).toFixed(1)}kg`
    );
  }
  console.log("\nMedias por arquetipo:");
  for (const [pid, list] of Object.entries(byPersona)) {
    if (!list || !list.length) continue;
    const avgH = list.reduce((s, b) => s + b.hives.length, 0) / list.length;
    const avgB = list.reduce((s, b) => s + b.balanceEur, 0) / list.length;
    const avgX = list.reduce((s, b) => s + (b.ownedHexIds || []).length, 0) / list.length;
    const label = PERSONAS[pid] ? PERSONAS[pid].label : pid;
    console.log(
      `  ${label.padEnd(14)} n=${list.length}  colmenas≈${avgH.toFixed(1)}  terrenos≈${avgX.toFixed(1)}  saldo≈${avgB.toFixed(0)}€`
    );
  }
  console.log(
    `\nLogins≈${totals.login}  ausencias≈${totals.skip}  +terrenos≈${totals.terrain}  +colmenas≈${totals.hives}  días-con-venta≈${totals.sells}`
  );
  console.log("Estado guardado en", STATE_PATH);
}

async function cmdLoop(apiKey, hours) {
  console.log(`Bucle cada ${hours} h (tick live). Ctrl+C para parar.`);
  for (;;) {
    await cmdTick(apiKey, true);
    const wait = Math.max(60, hours * 3600 * (0.85 + Math.random() * 0.3));
    console.log(`Siguiente tick en ${(wait / 60).toFixed(0)} min…`);
    await sleep(wait * 1000);
  }
}

async function main() {
  const args = process.argv.slice(2);
  const command = args[0];
  const hoursIdx = args.indexOf("--hours");
  const hours = hoursIdx >= 0 ? Number(args[hoursIdx + 1]) : 4;
  const daysIdx = args.indexOf("--days");
  const days = daysIdx >= 0 ? Number(args[daysIdx + 1]) : 14;
  const live = args.includes("--live");

  if (!["ensure", "tick", "loop", "simulate"].includes(command)) {
    console.log(`Uso:
  node tools/bots/bot_farm.js ensure
  node tools/bots/bot_farm.js tick [--live]
  node tools/bots/bot_farm.js simulate --days 30 [--live]
  node tools/bots/bot_farm.js loop --hours 4`);
    process.exit(1);
  }

  const apiKey = loadApiKey();
  if (command === "ensure") {
    await ensureBots(apiKey);
    console.log("Listo. 20 bots en", STATE_PATH);
    console.log("Arquetipos:", Object.values(PERSONAS).map((p) => p.label).join(" · "));
  } else if (command === "tick") {
    await ensureBots(apiKey);
    await cmdTick(apiKey, live);
  } else if (command === "simulate") {
    await cmdSimulate(apiKey, days, live);
  } else {
    await ensureBots(apiKey);
    await cmdLoop(apiKey, hours);
  }
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
