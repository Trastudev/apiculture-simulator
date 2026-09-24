"use strict";

const crypto = require("crypto");
const fs = require("fs");
const path = require("path");
const { Pool } = require("pg");
const { saveRow, TABLES } = require("./src/tables");

const PROJECT_ID = "apiculture-simulator";
const BASE =
  "https://firestore.googleapis.com/v1/projects/" +
  PROJECT_ID +
  "/databases/(default)/documents";

function b64url(value) {
  return Buffer.from(value)
    .toString("base64")
    .replace(/=/g, "")
    .replace(/\+/g, "-")
    .replace(/\//g, "_");
}

async function accessToken(key) {
  const now = Math.floor(Date.now() / 1000);
  const header = b64url(JSON.stringify({ alg: "RS256", typ: "JWT" }));
  const claim = b64url(JSON.stringify({
    iss: key.client_email,
    scope: "https://www.googleapis.com/auth/datastore",
    aud: "https://oauth2.googleapis.com/token",
    iat: now,
    exp: now + 3600,
  }));
  const signer = crypto.createSign("RSA-SHA256");
  signer.update(header + "." + claim);
  const signature = signer.sign(key.private_key, "base64")
    .replace(/=/g, "")
    .replace(/\+/g, "-")
    .replace(/\//g, "_");
  const res = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "content-type": "application/x-www-form-urlencoded" },
    body: "grant_type=" + encodeURIComponent("urn:ietf:params:oauth:grant-type:jwt-bearer")
      + "&assertion=" + encodeURIComponent(header + "." + claim + "." + signature),
  });
  const data = await res.json();
  if (!data.access_token) throw new Error("token " + JSON.stringify(data).slice(0, 200));
  return data.access_token;
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
  const parts = String(doc.name || "").split("/documents/")[1].split("/").map(decodeURIComponent);
  out.__id = parts[parts.length - 1];
  out.__parts = parts;
  return out;
}

async function api(method, url, token, body) {
  const res = await fetch(url, {
    method,
    headers: {
      authorization: "Bearer " + token,
      "content-type": "application/json",
    },
    body: body ? JSON.stringify(body) : undefined,
  });
  const text = await res.text();
  if (!res.ok) throw new Error(method + " " + res.status + " " + text.slice(0, 220));
  return text ? JSON.parse(text) : {};
}

async function listCollection(token, collectionPath) {
  const docs = [];
  let pageToken = "";
  do {
    const url = BASE + "/" + collectionPath
      + "?pageSize=300"
      + (pageToken ? "&pageToken=" + encodeURIComponent(pageToken) : "");
    const page = await api("GET", url, token);
    for (const doc of page.documents || []) docs.push(decodeDoc(doc));
    pageToken = page.nextPageToken || "";
  } while (pageToken);
  return docs;
}

async function getDoc(token, collectionPath) {
  try {
    const doc = await api("GET", BASE + "/" + collectionPath, token);
    if (!doc.name) return null;
    return decodeDoc(doc);
  } catch (err) {
    if (String(err.message).includes(" 404 ")) return null;
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
    throw new Error(route + " " + res.status + " " + text.slice(0, 160));
  }
}

async function main() {
  const keyPath = process.argv[2];
  if (!keyPath) throw new Error("falta la ruta de la clave");
  const envText = fs.readFileSync(path.join(__dirname, ".env"), "utf8");
  for (const line of envText.split(/\r?\n/)) {
    const eq = line.indexOf("=");
    if (eq > 0) process.env[line.slice(0, eq)] = line.slice(eq + 1);
  }
  const key = JSON.parse(fs.readFileSync(keyPath, "utf8"));
  const token = await accessToken(key);
  const counts = {};

  const users = await listCollection(token, "users");
  counts.users = users.length;
  if (users[0]) console.log("user keys " + Object.keys(users[0]).filter((k) => !k.startsWith("__")).join(","));
  for (const user of users) {
    await putLocal("players", user.__id, {
      playerName: user.playerName || null,
      honeyBrand: user.honeyBrand || null,
      profileComplete: Boolean(user.profileComplete),
      timeZoneId: user.timeZoneId || null,
      economyBalanceEur: user.economyBalanceEur,
      economyHoneyBucketsJson: user.economyHoneyBucketsJson,
      economyHoneySoldKgTotal: user.economyHoneySoldKgTotal,
      economyHoneySoldByFloraJson: user.economyHoneySoldByFloraJson,
      playerLevel: user.playerLevel,
      playerXp: user.playerXp,
    });
  }

  const pool = new Pool({
    host: "127.0.0.1",
    database: process.env.PGDATABASE,
    user: process.env.PGUSER,
    password: process.env.PGPASSWORD,
  });
  const storeDef = TABLES.find((item) => item.route === "player-stores");
  const yieldDef = TABLES.find((item) => item.route === "hive-yields");
  let stores = 0;
  let claims = 0;
  let production = 0;
  for (const user of users) {
    const extra = {};
    for (const keyName of ["invTreatments", "invFeed", "invQueensJson", "hqZaLat", "hqZaLng", "hqMdgLat", "hqMdgLng", "hqIberiaLat", "hqIberiaLng"]) {
      if (user[keyName] !== undefined) extra[keyName] = user[keyName];
    }
    if (Object.keys(extra).length) {
      await saveRow(pool, storeDef, user.__id + "::profile", {
        ownerId: user.__id,
        kind: "profile",
        body: extra,
      });
      stores++;
    }
    const state = await getDoc(token, "users/" + encodeURIComponent(user.__id) + "/meta/productionState");
    if (state) {
      await putLocal("production-states", user.__id, state);
      production++;
    }
    const claimDocs = await listCollection(token, "users/" + encodeURIComponent(user.__id) + "/eventClaims");
    for (const claim of claimDocs) {
      await putLocal("event-claims", user.__id + "::" + claim.__id, {
        ownerId: user.__id,
        instanceId: claim.__id,
        body: claim,
      });
      claims++;
    }
  }
  counts.profileExtras = stores;
  counts.productionStates = production;
  counts.eventClaims = claims;

  const hives = await listCollection(token, "hives");
  for (const doc of hives) await putLocal("hives", doc.__id, doc);
  counts.hives = hives.length;
  let yields = 0;
  for (const hive of hives) {
    const rows = await listCollection(token, "hives/" + encodeURIComponent(hive.__id) + "/dailyYields");
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
  }
  counts.dailyYields = yields;

  const orders = await listCollection(token, "honeyOrders");
  for (const doc of orders) await putLocal("honey-orders", doc.__id, doc);
  counts.honeyOrders = orders.length;

  const offers = await listCollection(token, "pollinationOffers");
  for (const doc of offers) await putLocal("pollination-offers", doc.__id, doc);
  counts.pollinationOffers = offers.length;

  const contracts = await listCollection(token, "pollinationContracts");
  for (const doc of contracts) await putLocal("pollination-contracts", doc.__id, doc);
  counts.pollinationContracts = contracts.length;

  const days = await listCollection(token, "globalHoneyMarket");
  let sales = 0;
  for (const day of days) {
    for (const sub of ["floraSalesUtc", "floraSales"]) {
      const rows = await listCollection(
        token,
        "globalHoneyMarket/" + encodeURIComponent(day.__id) + "/" + sub
      );
      for (const row of rows) {
        await putLocal("market-sales", day.__id + "::" + sub + "::" + row.__id, {
          dayKey: Number(day.__id),
          floraKey: row.__id,
          kgSold: row.kgSold || 0,
        });
        sales++;
      }
    }
  }
  counts.marketDays = days.length;
  counts.marketSales = sales;

  await pool.end();
  console.log(JSON.stringify(counts));
}

main().catch((err) => {
  console.error(err.message);
  process.exit(1);
});
