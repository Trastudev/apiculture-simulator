"use strict";

const crypto = require("crypto");
const fs = require("fs");
const path = require("path");
const { Pool } = require("pg");
const { saveRow, TABLES } = require("./src/tables");

const BASE = "https://firestore.googleapis.com/v1/projects/apiculture-simulator/databases/(default)/documents";

function b64url(value) {
  return Buffer.from(value).toString("base64").replace(/=/g, "").replace(/\+/g, "-").replace(/\//g, "_");
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
  const signature = signer.sign(key.private_key, "base64").replace(/=/g, "").replace(/\+/g, "-").replace(/\//g, "_");
  const res = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "content-type": "application/x-www-form-urlencoded" },
    body: "grant_type=" + encodeURIComponent("urn:ietf:params:oauth:grant-type:jwt-bearer")
      + "&assertion=" + encodeURIComponent(header + "." + claim + "." + signature),
  });
  const data = await res.json();
  if (!data.access_token) throw new Error("token");
  return data.access_token;
}

function decodeValue(v) {
  if (!v || typeof v !== "object") return null;
  if (Object.prototype.hasOwnProperty.call(v, "doubleValue")) return Number(v.doubleValue);
  if (Object.prototype.hasOwnProperty.call(v, "integerValue")) return Number(v.integerValue);
  if (Object.prototype.hasOwnProperty.call(v, "stringValue")) return v.stringValue;
  if (Object.prototype.hasOwnProperty.call(v, "booleanValue")) return v.booleanValue;
  return null;
}

function decodeDoc(doc) {
  const out = {};
  for (const [key, val] of Object.entries(doc.fields || {})) out[key] = decodeValue(val);
  const parts = doc.name.split("/documents/")[1].split("/").map(decodeURIComponent);
  out.__id = parts[parts.length - 1];
  out.__parts = parts;
  out.__name = doc.name;
  return out;
}

async function eachGroup(token, collectionId, onDoc) {
  let cursor = null;
  let total = 0;
  for (;;) {
    const structuredQuery = {
      from: [{ collectionId, allDescendants: true }],
      orderBy: [{ field: { fieldPath: "__name__" }, direction: "ASCENDING" }],
      limit: 300,
    };
    if (cursor) {
      structuredQuery.startAt = { values: [{ referenceValue: cursor }], before: false };
    }
    const res = await fetch(BASE + ":runQuery", {
      method: "POST",
      headers: { authorization: "Bearer " + token, "content-type": "application/json" },
      body: JSON.stringify({ structuredQuery }),
    });
    const text = await res.text();
    if (!res.ok) throw new Error(collectionId + " " + res.status + " " + text.slice(0, 180));
    const rows = JSON.parse(text);
    let count = 0;
    let last = null;
    for (const row of rows) {
      if (!row.document) continue;
      const doc = decodeDoc(row.document);
      await onDoc(doc);
      last = doc.__name;
      count++;
      total++;
    }
    if (!last || count < 300 || last === cursor) break;
    cursor = last;
  }
  return total;
}

async function putLocal(route, id, body) {
  const res = await fetch("http://127.0.0.1:8080/" + route + "/" + encodeURIComponent(id), {
    method: "PUT",
    headers: { "content-type": "application/json" },
    body: JSON.stringify(body),
  });
  if (!res.ok) throw new Error(await res.text());
}

async function main() {
  const envText = fs.readFileSync(path.join(__dirname, ".env"), "utf8");
  for (const line of envText.split(/\r?\n/)) {
    const eq = line.indexOf("=");
    if (eq > 0) process.env[line.slice(0, eq)] = line.slice(eq + 1);
  }
  const key = JSON.parse(fs.readFileSync(process.argv[2], "utf8"));
  const token = await accessToken(key);
  const pool = new Pool({
    host: "127.0.0.1",
    database: process.env.PGDATABASE,
    user: process.env.PGUSER,
    password: process.env.PGPASSWORD,
  });
  const yieldDef = TABLES.find((item) => item.route === "hive-yields");
  const counts = {};
  counts.marketSales = await eachGroup(token, "floraSalesUtc", async (doc) => {
    const dayKey = Number(doc.__parts[1]);
    await putLocal("market-sales", dayKey + "::" + doc.__id, {
      dayKey,
      floraKey: doc.__id,
      kgSold: doc.kgSold || 0,
    });
  });
  counts.dailyYields = await eachGroup(token, "dailyYields", async (doc) => {
    const hiveId = doc.__parts[1];
    await saveRow(pool, yieldDef, hiveId + "::" + doc.__id, {
      hiveId,
      dayKey: Number(doc.__id),
      kg: doc.kg || 0,
      workerNetDelta: doc.workerNetDelta || 0,
      eggsLaid: doc.eggsLaid || 0,
      consumptionKg: doc.consumptionKg || 0,
      forageKg: doc.forageKg || 0,
    });
  });
  await pool.end();
  console.log(JSON.stringify(counts));
}

main().catch((err) => {
  console.error(err.message);
  process.exit(1);
});
