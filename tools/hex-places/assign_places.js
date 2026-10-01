"use strict";

/**
 * Asigna un topónimo a cada hex de overlay.json (pueblo / ciudad, o accidente
 * orográfico si no hay núcleo). Fuente: dumps GeoNames por país, una vez, offline.
 *
 *   node tools/hex-places/assign_places.js
 *   node tools/hex-places/assign_places.js --dry-run --only=iberia
 *
 * No se llama en runtime: el APK solo lee el campo "place".
 */

const fs = require("fs");
const path = require("path");
const https = require("https");
const zlib = require("zlib");
const readline = require("readline");

const ROOT = path.resolve(__dirname, "..", "..");
const CACHE = path.join(__dirname, "cache");
const GEONAMES = "https://download.geonames.org/export/dump";

const OVERLAYS = {
  iberia: {
    file: path.join(ROOT, "app/src/main/assets/iberia_hex/overlay.json"),
    countries: ["ES", "PT", "AD", "GI"],
  },
  za: {
    file: path.join(ROOT, "app/src/main/assets/za_hex/overlay.json"),
    countries: ["ZA", "LS", "SZ", "NA", "BW", "MZ"],
  },
};

const CELL = 0.15;
const MAX_KM_P = 8;
const MAX_KM_OTHER = 7;
const PLACES_VERSION = 1;

const P_CODES = new Set([
  "PPL", "PPLA", "PPLA2", "PPLA3", "PPLA4", "PPLA5",
  "PPLC", "PPLG", "PPLL", "PPLS", "PPLX", "PPLF",
]);
const T_CODES = new Set([
  "MT", "MTS", "PK", "PKLT", "HLL", "HLLS", "PASS", "RDGE", "SPUR",
  "PLAT", "PLATX", "VAL", "VALS", "GRGE", "VLC", "CAPE", "PEN",
  "CLF", "ISL", "ISLS", "BCH", "RK", "RKFL", "BUTE", "MESA",
]);
const H_CODES = new Set([
  "SPNG", "SPNT", "STM", "STMI", "STMM", "STMSB", "LK", "LKN", "LKI",
  "RSV", "BAY", "FLLS", "CNL", "PND", "WTRC", "PN", "PNDN", "MRSH",
]);
const L_CODES = new Set(["LCTY", "PRK", "RESN", "RESF", "FRST", "AREA", "PLN"]);
const A_CODES = new Set(["ADM3", "ADM4", "ADM4H", "ADM5"]);

const args = parseArgs(process.argv.slice(2));

function parseArgs(argv) {
  const out = { dryRun: false, only: null, skipDownload: false };
  for (const a of argv) {
    if (a === "--dry-run") out.dryRun = true;
    else if (a === "--skip-download") out.skipDownload = true;
    else if (a.startsWith("--only=")) out.only = a.slice(7).toLowerCase();
  }
  return out;
}

function log(msg) {
  process.stderr.write(msg + "\n");
}

function download(url, dest) {
  return new Promise((resolve, reject) => {
    fs.mkdirSync(path.dirname(dest), { recursive: true });
    const tmp = dest + ".part";
    const file = fs.createWriteStream(tmp);
    const get = (u, hops) => {
      if (hops > 6) {
        reject(new Error("too many redirects: " + u));
        return;
      }
      https
        .get(u, { headers: { "User-Agent": "apiculture-simulator-hex-places" } }, (res) => {
          if (res.statusCode >= 300 && res.statusCode < 400 && res.headers.location) {
            res.resume();
            get(res.headers.location, hops + 1);
            return;
          }
          if (res.statusCode !== 200) {
            res.resume();
            reject(new Error(u + " HTTP " + res.statusCode));
            return;
          }
          res.pipe(file);
          file.on("finish", () => file.close(() => {
            fs.renameSync(tmp, dest);
            resolve();
          }));
        })
        .on("error", (err) => {
          try { file.close(); } catch (_) { /* ignore */ }
          try { fs.unlinkSync(tmp); } catch (_) { /* ignore */ }
          reject(err);
        });
    };
    get(url, 0);
  });
}

function findEocd(buf) {
  for (let i = buf.length - 22; i >= Math.max(0, buf.length - 22 - 65535); i--) {
    if (buf.readUInt32LE(i) === 0x06054b50) {
      return i;
    }
  }
  throw new Error("zip EOCD not found");
}

function extractZipTxt(zipPath, destDir) {
  const buf = fs.readFileSync(zipPath);
  const eocd = findEocd(buf);
  const count = buf.readUInt16LE(eocd + 10);
  let cd = buf.readUInt32LE(eocd + 16);
  let wrote = 0;
  for (let n = 0; n < count; n++) {
    if (buf.readUInt32LE(cd) !== 0x02014b50) {
      throw new Error("bad central directory at " + cd);
    }
    const method = buf.readUInt16LE(cd + 10);
    const compSize = buf.readUInt32LE(cd + 20);
    const nameLen = buf.readUInt16LE(cd + 28);
    const extraLen = buf.readUInt16LE(cd + 30);
    const commentLen = buf.readUInt16LE(cd + 32);
    const localOff = buf.readUInt32LE(cd + 42);
    const name = buf.toString("utf8", cd + 46, cd + 46 + nameLen);
    const localNameLen = buf.readUInt16LE(localOff + 26);
    const localExtraLen = buf.readUInt16LE(localOff + 28);
    const dataStart = localOff + 30 + localNameLen + localExtraLen;
    const base = path.basename(name.replace(/\\/g, "/"));
    const keep = base && !name.endsWith("/") && /\.txt$/i.test(base) && !/^readme/i.test(base);
    if (keep) {
      const data = buf.subarray(dataStart, dataStart + compSize);
      let out;
      if (method === 0) out = data;
      else if (method === 8) out = zlib.inflateRawSync(data);
      else throw new Error("zip method " + method + " in " + name);
      fs.mkdirSync(destDir, { recursive: true });
      fs.writeFileSync(path.join(destDir, base), out);
      wrote++;
    }
    cd += 46 + nameLen + extraLen + commentLen;
  }
  if (wrote === 0) {
    throw new Error("no .txt in " + zipPath);
  }
}

async function ensureCountryTxt(cc) {
  const txt = path.join(CACHE, cc + ".txt");
  if (fs.existsSync(txt) && fs.statSync(txt).size > 1000) {
    return txt;
  }
  if (args.skipDownload) {
    throw new Error("missing cache for " + cc + " (run without --skip-download)");
  }
  const zip = path.join(CACHE, cc + ".zip");
  if (!fs.existsSync(zip) || fs.statSync(zip).size < 100) {
    log("descargando " + cc + ".zip …");
    await download(GEONAMES + "/" + cc + ".zip", zip);
  }
  extractZipTxt(zip, CACHE);
  if (!fs.existsSync(txt)) {
    throw new Error("expected " + txt + " after unzip");
  }
  return txt;
}

function classRank(cls, code) {
  if (cls === "P") {
    if (code === "PPLC" || code.startsWith("PPLA")) return 100;
    if (code === "PPL" || code === "PPLL" || code === "PPLS") return 90;
    if (code === "PPLX" || code === "PPLF") return 70;
    return 60;
  }
  if (cls === "T") return 40;
  if (cls === "H") return 30;
  if (cls === "L") return 20;
  if (cls === "A") return 12;
  return 0;
}

function acceptCode(cls, code) {
  if (cls === "P") return P_CODES.has(code);
  if (cls === "T") return T_CODES.has(code);
  if (cls === "H") return H_CODES.has(code);
  if (cls === "L") return L_CODES.has(code);
  if (cls === "A") return A_CODES.has(code);
  return false;
}

function cleanName(name) {
  if (!name) return "";
  let s = String(name).trim().replace(/\s+/g, " ");
  if (s.length < 2 || /^\d+$/.test(s)) return "";
  if (s.length > 42) s = s.slice(0, 41).trim() + "…";
  return s;
}

function cellKey(lat, lon) {
  return (Math.floor(lat / CELL) | 0) + ":" + (Math.floor(lon / CELL) | 0);
}

function haversineKm(lat1, lon1, lat2, lon2) {
  const R = 6371;
  const p1 = (lat1 * Math.PI) / 180;
  const p2 = (lat2 * Math.PI) / 180;
  const dLat = ((lat2 - lat1) * Math.PI) / 180;
  const dLon = ((lon2 - lon1) * Math.PI) / 180;
  const a =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(p1) * Math.cos(p2) * Math.sin(dLon / 2) ** 2;
  return 2 * R * Math.asin(Math.min(1, Math.sqrt(a)));
}

function pointInRing(lat, lon, ring) {
  let inside = false;
  for (let i = 0, j = ring.length - 1; i < ring.length; j = i++) {
    const yi = ring[i][0];
    const xi = ring[i][1];
    const yj = ring[j][0];
    const xj = ring[j][1];
    const denom = yj - yi;
    const intersect =
      yi > lat !== yj > lat &&
      lon < ((xj - xi) * (lat - yi)) / (denom === 0 ? 1e-18 : denom) + xi;
    if (intersect) inside = !inside;
  }
  return inside;
}

function overlayBounds(parcels) {
  let minLat = 90, maxLat = -90, minLon = 180, maxLon = -180;
  for (const p of parcels) {
    minLat = Math.min(minLat, p.clat);
    maxLat = Math.max(maxLat, p.clat);
    minLon = Math.min(minLon, p.clon);
    maxLon = Math.max(maxLon, p.clon);
  }
  return {
    minLat: minLat - 0.4,
    maxLat: maxLat + 0.4,
    minLon: minLon - 0.4,
    maxLon: maxLon + 0.4,
  };
}

function inBox(lat, lon, box) {
  return lat >= box.minLat && lat <= box.maxLat && lon >= box.minLon && lon <= box.maxLon;
}

async function loadFeatures(txtFiles, box) {
  const grid = new Map();
  let kept = 0;
  let scanned = 0;
  for (const file of txtFiles) {
    const rl = readline.createInterface({
      input: fs.createReadStream(file, { encoding: "utf8" }),
      crlfDelay: Infinity,
    });
    for await (const line of rl) {
      if (!line || line.charCodeAt(0) === 35) continue;
      scanned++;
      const cols = line.split("\t");
      if (cols.length < 15) continue;
      const lat = Number(cols[4]);
      const lon = Number(cols[5]);
      if (!Number.isFinite(lat) || !Number.isFinite(lon) || !inBox(lat, lon, box)) continue;
      const cls = cols[6];
      const code = cols[7];
      if (!acceptCode(cls, code)) continue;
      const name = cleanName(cols[1]);
      if (!name) continue;
      const pop = Number(cols[14]) || 0;
      const feat = { name, lat, lon, cls, code, pop };
      const key = cellKey(lat, lon);
      let bucket = grid.get(key);
      if (!bucket) {
        bucket = [];
        grid.set(key, bucket);
      }
      bucket.push(feat);
      kept++;
    }
  }
  log("gazetteer: " + kept + " puntos (de " + scanned + " filas, " + grid.size + " celdas)");
  return grid;
}

function candidatesNear(grid, lat, lon) {
  const out = [];
  const i0 = Math.floor(lat / CELL);
  const j0 = Math.floor(lon / CELL);
  for (let di = -1; di <= 1; di++) {
    for (let dj = -1; dj <= 1; dj++) {
      const bucket = grid.get(i0 + di + ":" + (j0 + dj));
      if (bucket) {
        for (const f of bucket) out.push(f);
      }
    }
  }
  return out;
}

function scoreFeature(feat, parcel) {
  const inside = pointInRing(feat.lat, feat.lon, parcel.ring);
  const distKm = haversineKm(feat.lat, feat.lon, parcel.clat, parcel.clon);
  const maxKm = feat.cls === "P" ? MAX_KM_P : MAX_KM_OTHER;
  if (!inside && distKm > maxKm) return -Infinity;
  if (!inside && feat.cls === "A") return -Infinity;
  if (!inside && feat.cls !== "P" && distKm > 6) return -Infinity;
  let s = classRank(feat.cls, feat.code) * 10000;
  if (inside) s += 500000;
  else s += Math.max(0, 25000 - distKm * 3500);
  if (feat.cls === "P") {
    s += Math.min(8000, Math.log10(feat.pop + 10) * 1200);
  }
  s -= distKm * 250;
  return s;
}

function pickPlace(grid, parcel) {
  const feats = candidatesNear(grid, parcel.clat, parcel.clon);
  let best = null;
  let bestScore = -Infinity;
  for (const f of feats) {
    const s = scoreFeature(f, parcel);
    if (s > bestScore) {
      bestScore = s;
      best = f;
    }
  }
  return best && bestScore > -Infinity ? best.name : null;
}

async function processOverlay(key, spec) {
  log("\n=== " + key + " ===");
  const raw = fs.readFileSync(spec.file, "utf8");
  const root = JSON.parse(raw);
  const parcels = root.parcels;
  if (!Array.isArray(parcels) || parcels.length === 0) {
    throw new Error("sin parcels en " + spec.file);
  }
  const box = overlayBounds(parcels);
  log(parcels.length + " hexes  bbox " +
    box.minLat.toFixed(2) + ".." + box.maxLat.toFixed(2) + " / " +
    box.minLon.toFixed(2) + ".." + box.maxLon.toFixed(2));

  const txts = [];
  for (const cc of spec.countries) {
    txts.push(await ensureCountryTxt(cc));
  }
  const grid = await loadFeatures(txts, box);

  let named = 0;
  const t0 = Date.now();
  for (let i = 0; i < parcels.length; i++) {
    const p = parcels[i];
    const name = pickPlace(grid, p);
    if (name) {
      p.place = name;
      named++;
    } else if (p.place) {
      delete p.place;
    }
    if ((i + 1) % 1500 === 0) {
      log("  " + (i + 1) + "/" + parcels.length + " …");
    }
  }
  log("nombrados " + named + "/" + parcels.length +
    " (" + (100 * named / parcels.length).toFixed(1) + "%) en " +
    ((Date.now() - t0) / 1000).toFixed(1) + "s");

  const samples = [];
  for (let i = 0; i < parcels.length && samples.length < 8; i += Math.max(1, Math.floor(parcels.length / 12))) {
    if (parcels[i].place) {
      samples.push(parcels[i].id + " → " + parcels[i].place);
    }
  }
  if (samples.length) log("muestra: " + samples.join(" | "));

  root.places = PLACES_VERSION;
  if (args.dryRun) {
    log("dry-run: no se escribe " + spec.file);
    return { named, total: parcels.length };
  }
  const tmp = spec.file + ".tmp";
  fs.writeFileSync(tmp, JSON.stringify(root));
  fs.renameSync(tmp, spec.file);
  log("escrito " + spec.file + " (" + fs.statSync(spec.file).size + " bytes)");
  return { named, total: parcels.length };
}

async function main() {
  fs.mkdirSync(CACHE, { recursive: true });
  const keys = Object.keys(OVERLAYS).filter((k) => !args.only || k === args.only);
  if (keys.length === 0) {
    throw new Error("unknown --only=" + args.only);
  }
  const stats = {};
  for (const k of keys) {
    stats[k] = await processOverlay(k, OVERLAYS[k]);
  }
  log("\nlisto " + JSON.stringify(stats));
}

main().catch((err) => {
  log(err && err.stack ? err.stack : String(err));
  process.exit(1);
});
