"use strict";

const fs = require("fs");
const path = require("path");

const ROOT = path.resolve(__dirname, "..");
const PROJECT_ID = "apiculture-simulator";
const BASE =
  "https://firestore.googleapis.com/v1/projects/" +
  PROJECT_ID +
  "/databases/(default)/documents";

function decodeValue(v) {
  if (!v || typeof v !== "object") return null;
  if (Object.prototype.hasOwnProperty.call(v, "stringValue")) return v.stringValue;
  if (Object.prototype.hasOwnProperty.call(v, "integerValue")) return Number(v.integerValue);
  if (Object.prototype.hasOwnProperty.call(v, "doubleValue")) return Number(v.doubleValue);
  if (Object.prototype.hasOwnProperty.call(v, "booleanValue")) return v.booleanValue;
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
  out.__parts = parts.map(decodeURIComponent);
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
  if (!res.ok) throw new Error(method + " " + res.status + " " + text.slice(0, 200));
  return text ? JSON.parse(text) : {};
}

async function putLocal(route, id, body) {
  const res = await fetch("http://127.0.0.1:8080/" + route + "/" + encodeURIComponent(id), {
    method: "PUT",
    headers: { "content-type": "application/json" },
    body: JSON.stringify(body),
  });
  if (!res.ok) throw new Error(await res.text());
}

async function queryUntaken(token, collectionId) {
  const rows = await api("POST", BASE + ":runQuery", token, {
    structuredQuery: {
      from: [{ collectionId }],
      where: {
        fieldFilter: {
          field: { fieldPath: "taken" },
          op: "EQUAL",
          value: { booleanValue: false },
        },
      },
    },
  });
  const docs = [];
  for (const row of rows) {
    if (row.document) docs.push(decodeDoc(row.document));
  }
  return docs;
}

async function queryGroup(token, collectionId) {
  const rows = await api("POST", BASE + ":runQuery", token, {
    structuredQuery: {
      from: [{ collectionId, allDescendants: true }],
    },
  });
  const docs = [];
  for (const row of rows) {
    if (row.document) docs.push(decodeDoc(row.document));
  }
  return docs;
}

async function main() {
  const services = JSON.parse(fs.readFileSync(path.join(ROOT, "app", "google-services.json"), "utf8"));
  const apiKey = services.client[0].api_key[0].current_key;
  const state = JSON.parse(fs.readFileSync(path.join(ROOT, "tools", "bots", "state.json"), "utf8"));
  const bot = state.bots[0];
  const auth = await api(
    "POST",
    "https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=" + apiKey,
    null,
    { email: bot.email, password: bot.password, returnSecureToken: true }
  );
  const token = auth.idToken;
  const counts = {};

  const orders = await queryUntaken(token, "honeyOrders");
  for (const doc of orders) await putLocal("honey-orders", doc.__id, doc);
  counts.openOrders = orders.length;

  const offers = await queryUntaken(token, "pollinationOffers");
  for (const doc of offers) await putLocal("pollination-offers", doc.__id, doc);
  counts.openOffers = offers.length;

  try {
    const sales = await queryGroup(token, "floraSalesUtc");
    for (const doc of sales) {
      const dayKey = Number(doc.__parts[1]);
      await putLocal("market-sales", dayKey + "::" + doc.__id, {
        dayKey,
        floraKey: doc.__id,
        kgSold: doc.kgSold || 0,
      });
    }
    counts.marketSales = sales.length;
  } catch (err) {
    counts.marketSales = err.message;
  }

  console.log(JSON.stringify(counts));
}

main().catch((err) => {
  console.error(err.message);
  process.exit(1);
});
