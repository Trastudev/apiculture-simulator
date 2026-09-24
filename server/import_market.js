"use strict";

const fs = require("fs");
const path = require("path");

const ROOT = path.resolve(__dirname, "..");
const BASE =
  "https://firestore.googleapis.com/v1/projects/apiculture-simulator/databases/(default)/documents";

function decodeValue(v) {
  if (!v || typeof v !== "object") return null;
  if (Object.prototype.hasOwnProperty.call(v, "stringValue")) return v.stringValue;
  if (Object.prototype.hasOwnProperty.call(v, "integerValue")) return Number(v.integerValue);
  if (Object.prototype.hasOwnProperty.call(v, "doubleValue")) return Number(v.doubleValue);
  if (v.mapValue) {
    const out = {};
    for (const [key, val] of Object.entries(v.mapValue.fields || {})) out[key] = decodeValue(val);
    return out;
  }
  return null;
}

async function main() {
  const services = JSON.parse(fs.readFileSync(path.join(ROOT, "app", "google-services.json"), "utf8"));
  const apiKey = services.client[0].api_key[0].current_key;
  const state = JSON.parse(fs.readFileSync(path.join(ROOT, "tools", "bots", "state.json"), "utf8"));
  const bot = state.bots[0];
  const authRes = await fetch(
    "https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=" + apiKey,
    {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({ email: bot.email, password: bot.password, returnSecureToken: true }),
    }
  );
  const auth = await authRes.json();
  const token = auth.idToken;
  const listed = await fetch(BASE + "/globalHoneyMarket?pageSize=20", {
    headers: { authorization: "Bearer " + token },
  });
  const body = await listed.text();
  console.log("list", listed.status, body.slice(0, 300));
  const day = await fetch(BASE + "/globalHoneyMarket/20260922/floraSalesUtc?pageSize=5", {
    headers: { authorization: "Bearer " + token },
  });
  console.log("day", day.status, (await day.text()).slice(0, 300));
}

main().catch((err) => {
  console.error(err.message);
  process.exit(1);
});
