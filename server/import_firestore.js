"use strict";

const fs = require("fs");
const path = require("path");
const { Pool } = require("pg");
const { saveRow, TABLES } = require("./src/tables");

const ROOT = path.resolve(__dirname, "..");
const PROJECT_ID = "apiculture-simulator";
const BASE =
  "https://firestore.googleapis.com/v1/projects/" +
  PROJECT_ID +
  "/databases/(default)/documents";

function loadEnv(file) {
  const text = fs.readFileSync(file, "utf8");
  for (const line of text.split(/\r?\n/)) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith("#")) continue;
    const eq = trimmed.indexOf("=");
    if (eq < 0) continue;
    process.env[trimmed.slice(0, eq)] = trimmed.slice(eq + 1);
  }
}

function decodeValue(v) {
  if (!v || typeof v !== "object") return null;
  if (Object.prototype.hasOwnProperty.call(v, "stringValue")) return v.stringValue;
  if (Object.prototype.hasOwnProperty.call(v, "integerValue")) return Number(v.integerValue);
  if (Object.prototype.hasOwnProperty.call(v, "doubleValue")) return Number(v.doubleValue);
  if (Object.prototype.hasOwnProperty.call(v, "booleanValue")) return v.booleanValue;
  if (Object.prototype.hasOwnProperty.call(v, "nullValue")) return null;
  if (Object.prototype.hasOwnProperty.call(v, "timestampValue")) return v.timestampValue;
  if (v.mapValue) {
    const out = {};
    for (const [key, val] of Object.entries(v.mapValue.fields || {})) out[key] = decodeValue(val);
    return out;
  }
  if (v.arrayValue) return (v.arrayValue.values || []).map(decodeValue);
  return null;
}

function decodeDoc(doc) {
  const out = {};
  for (const [key, val] of Object.entries((doc && doc.fields) || {})) out[key] = decodeValue(val);
  const parts = String(doc.name || "").split("/documents/")[1].split("/");
  out.__id = decodeURIComponent(parts[parts.length - 1]);
  out.__path = parts.map(decodeURIComponent);
  return out;
}

async function api(method, url, token, body) {
  const headers = { "content-type": "application/json" };
  if (token) headers.authorization = "Bearer " + token;
  const res = await fetch(url, {
    method,
    headers,
    body: body ? JSON.stringify(body) : undefined,
  });
  const text = await res.text();
  if (!res.ok) {
    const err = new Error(method + " " + res.status + " " + text.slice(0, 240));
    err.status = res.status;
    throw err;
  }
  return text ? JSON.parse(text) : {};
}

async function signIn(apiKey, email, password) {
  const data = await api(
    "POST",
    "https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=" + apiKey,
    null,
    { email, password, returnSecureToken: true }
  );
  return { token: data.idToken, uid: data.localId };
}

async function listCollection(token, collectionPath) {
  const docs = [];
  let pageToken = "";
  do {
    const url =
      BASE +
      "/" +
      collectionPath +
      "?pageSize=200" +
      (pageToken ? "&pageToken=" + encodeURIComponent(pageToken) : "");
    const page = await api("GET", url, token);
    for (const doc of page.documents || []) docs.push(decodeDoc(doc));
    pageToken = page.nextPageToken || "";
  } while (pageToken);
  return docs;
}

async function getDoc(token, collectionPath) {
  try {
    const doc = await api("GET", BASE + "/" + collectionPath, token);
    if (!doc.fields && !doc.name) return null;
    return decodeDoc(doc);
  } catch (err) {
    if (err.status === 404) return null;
    throw err;
  }
}

async function putLocal(route, id, body) {
  const res = await fetch("http://127.0.0.1:8080/" + route + "/" + encodeURIComponent(id), {
    method: "PUT",
    headers: { "content-type": "application/json" },
    body: JSON.stringify(body),
  });
  if (!res.ok) {
    const text = await res.text();
    throw new Error("local " + route + " " + id + " " + res.status + " " + text.slice(0, 180));
  }
}

async function main() {
  loadEnv(path.join(__dirname, ".env"));
  const services = JSON.parse(fs.readFileSync(path.join(ROOT, "app", "google-services.json"), "utf8"));
  const apiKey = services.client[0].api_key[0].current_key;
  const state = JSON.parse(fs.readFileSync(path.join(ROOT, "tools", "bots", "state.json"), "utf8"));
  const bots = state.bots || [];
  if (!bots.length) throw new Error("state.json no tiene bots");

  const first = await signIn(apiKey, bots[0].email, bots[0].password);
  const counts = {};

  const hives = await listCollection(first.token, "hives");
  for (const doc of hives) await putLocal("hives", doc.__id, doc);
  counts.hives = hives.length;

  const parcels = await listCollection(first.token, "hexParcels");
  for (const doc of parcels) await putLocal("hex-parcels", doc.__id, doc);
  counts.hexParcels = parcels.length;

  const board = await listCollection(first.token, "players");
  for (const doc of board) await putLocal("leaderboard", doc.__id, doc);
  counts.leaderboard = board.length;

  const names = await listCollection(first.token, "uniquePlayerNames");
  for (const doc of names) {
    await putLocal("unique-names", "player::" + doc.__id, {
      kind: "player",
      nameKey: doc.__id,
      ownerId: doc.ownerUid || null,
    });
  }
  counts.uniquePlayerNames = names.length;

  const brands = await listCollection(first.token, "uniqueHoneyBrands");
  for (const doc of brands) {
    await putLocal("unique-names", "brand::" + doc.__id, {
      kind: "brand",
      nameKey: doc.__id,
      ownerId: doc.ownerUid || null,
    });
  }
  counts.uniqueHoneyBrands = brands.length;

  const events = await listCollection(first.token, "globalGameEvents");
  for (const doc of events) {
    await putLocal("global-events", doc.__id, { status: doc.status || null, body: doc });
  }
  counts.globalEvents = events.length;

  const progress = await listCollection(first.token, "globalEventProgress");
  for (const doc of progress) {
    await putLocal("event-progress", doc.__id, { body: doc });
    try {
      const people = await listCollection(
        first.token,
        "globalEventProgress/" + encodeURIComponent(doc.__id) + "/participants"
      );
      for (const person of people) {
        await putLocal("event-participants", doc.__id + "::" + person.__id, {
          progressId: doc.__id,
          ownerId: person.__id,
          kg: person.kg || person.kgSold || 0,
          body: person,
        });
      }
      counts.eventParticipants = (counts.eventParticipants || 0) + people.length;
    } catch (err) {
      console.log("participantes", doc.__id, err.message);
    }
  }
  counts.eventProgress = progress.length;

  try {
    const orders = await listCollection(first.token, "honeyOrders");
    for (const doc of orders) await putLocal("honey-orders", doc.__id, doc);
    counts.honeyOrders = orders.length;
  } catch (err) {
    console.log("honeyOrders", err.message);
    counts.honeyOrders = "parcial";
  }

  try {
    const offers = await listCollection(first.token, "pollinationOffers");
    for (const doc of offers) await putLocal("pollination-offers", doc.__id, doc);
    counts.pollinationOffers = offers.length;
  } catch (err) {
    console.log("pollinationOffers", err.message);
    counts.pollinationOffers = "parcial";
  }

  const contracts = await listCollection(first.token, "pollinationContracts");
  for (const doc of contracts) await putLocal("pollination-contracts", doc.__id, doc);
  counts.pollinationContracts = contracts.length;

  const pool = new Pool({
    host: process.env.PGHOST || "127.0.0.1",
    port: Number(process.env.PGPORT || 5432),
    database: process.env.PGDATABASE || "apiculture",
    user: process.env.PGUSER || "apiculture",
    password: process.env.PGPASSWORD,
  });

  let yields = 0;
  const yieldDef = TABLES.find((item) => item.route === "hive-yields");
  let users = 0;
  for (const bot of bots) {
    if (!bot.email || !bot.password) continue;
    const auth = await signIn(apiKey, bot.email, bot.password);
    const user = await getDoc(auth.token, "users/" + encodeURIComponent(auth.uid));
    if (user) {
      await putLocal("players", auth.uid, {
        playerName: user.playerName || bot.playerName || null,
        honeyBrand: user.honeyBrand || bot.honeyBrand || null,
        profileComplete: Boolean(user.profileComplete),
        timeZoneId: user.timeZoneId || bot.timeZoneId || null,
        economyBalanceEur: user.economyBalanceEur,
        economyHoneyBucketsJson: user.economyHoneyBucketsJson,
        economyHoneySoldKgTotal: user.economyHoneySoldKgTotal,
        economyHoneySoldByFloraJson: user.economyHoneySoldByFloraJson,
        playerLevel: user.playerLevel,
        playerXp: user.playerXp,
      });
      users++;
    }
    const production = await getDoc(
      auth.token,
      "users/" + encodeURIComponent(auth.uid) + "/meta/productionState"
    );
    if (production) {
      await putLocal("production-states", auth.uid, production);
    }
    for (const hive of hives) {
      if (hive.ownerId !== auth.uid) continue;
      try {
        const rows = await listCollection(
          auth.token,
          "hives/" + encodeURIComponent(hive.__id) + "/dailyYields"
        );
        for (const row of rows) {
          await saveRow(pool, yieldDef, hive.__id + "::" + row.__id, {
            hiveId: hive.__id,
            dayKey: Number(row.__id),
            kg: row.kg || 0,
            workerNetDelta: row.workerNetDelta || 0,
            eggsLaid: row.eggsLaid || 0,
            consumptionKg: row.consumptionKg || 0,
            forageKg: row.forageKg || 0,
          });
          yields++;
        }
      } catch (err) {
        console.log("yield", hive.__id, err.message);
      }
    }
    try {
      const claims = await listCollection(
        auth.token,
        "users/" + encodeURIComponent(auth.uid) + "/eventClaims"
      );
      for (const claim of claims) {
        await putLocal("event-claims", auth.uid + "::" + claim.__id, {
          ownerId: auth.uid,
          instanceId: claim.__id,
          body: claim,
        });
      }
      counts.eventClaims = (counts.eventClaims || 0) + claims.length;
    } catch (err) {
      console.log("claims", auth.uid, err.message);
    }
  }
  counts.users = users;
  counts.dailyYields = yields;
  await pool.end();
  console.log(JSON.stringify(counts));
}

main().catch((err) => {
  console.error(err.message);
  process.exit(1);
});
