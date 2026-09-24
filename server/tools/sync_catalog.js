"use strict";

const fs = require("fs");
const path = require("path");

const root = path.resolve(__dirname, "..", "..");
const sourceAssets = path.join(root, "app", "src", "main", "assets");
const target = path.join(__dirname, "..", "data");

const files = [
  ["game_balance.json", path.join(sourceAssets, "game_balance.json")],
  ["hex/iberia.json", path.join(sourceAssets, "iberia_hex", "overlay.json")],
  ["hex/za.json", path.join(sourceAssets, "za_hex", "overlay.json")],
  ["hex/mdg.json", path.join(sourceAssets, "mdg_hex", "overlay.json")],
];

for (const [relative, source] of files) {
  const destination = path.join(target, relative);
  fs.mkdirSync(path.dirname(destination), { recursive: true });
  fs.copyFileSync(source, destination);
  console.log(`${relative} <- ${path.relative(root, source)}`);
}
