"use strict";

const crypto = require("crypto");
const fs = require("fs");

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

async function main() {
  const key = JSON.parse(fs.readFileSync(process.argv[2], "utf8"));
  const token = await accessToken(key);
  const headers = { authorization: "Bearer " + token, "content-type": "application/json" };
  const hives = await fetch(BASE + "/hives?pageSize=1", { headers });
  const hiveBody = await hives.json();
  const name = hiveBody.documents && hiveBody.documents[0] && hiveBody.documents[0].name;
  const id = name ? decodeURIComponent(name.split("/").pop()) : "";
  console.log("hive", hives.status, id);
  if (id) {
    const yields = await fetch(BASE + "/hives/" + encodeURIComponent(id) + "/dailyYields?pageSize=3", { headers });
    const text = await yields.text();
    console.log("yields", yields.status, text.slice(0, 180));
  }
  const market = await fetch(BASE + "/globalHoneyMarket?pageSize=5", { headers });
  console.log("market", market.status, (await market.text()).slice(0, 180));
  const query = await fetch(BASE + ":runQuery", {
    method: "POST",
    headers,
    body: JSON.stringify({
      structuredQuery: { from: [{ collectionId: "floraSalesUtc", allDescendants: true }], limit: 3 },
    }),
  });
  console.log("group", query.status, (await query.text()).slice(0, 220));
}

main().catch((err) => {
  console.error(err.message);
  process.exit(1);
});
