"use strict";
const fs = require("fs");
const path = require("path");

const ROOT = path.resolve(__dirname, "..", "..");
const ids = JSON.parse(fs.readFileSync(path.join(__dirname, "hex_ids.json"), "utf8"));
const want = new Set([...ids.iberia, ...ids.za]);

function extract(file) {
  const raw = fs.readFileSync(file, "utf8");
  const out = {};
  const re =
    /"clat":([0-9.\-]+)[\s\S]*?"clon":([0-9.\-]+)[\s\S]*?"id":"(hex_[^"]+)"/g;
  let m;
  while ((m = re.exec(raw))) {
    const id = m[3];
    if (!want.has(id)) continue;
    out[id] = { lat: Number(m[1]), lng: Number(m[2]) };
  }
  return out;
}

const m = {
  ...extract(path.join(ROOT, "app/src/main/assets/iberia_hex/overlay.json")),
  ...extract(path.join(ROOT, "app/src/main/assets/za_hex/overlay.json")),
};
console.log(`found ${Object.keys(m).length} of ${want.size}`);
fs.writeFileSync(path.join(__dirname, "hex_centers.json"), JSON.stringify(m, null, 2));
