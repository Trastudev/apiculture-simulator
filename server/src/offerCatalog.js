"use strict";

const fs = require("fs");
const path = require("path");

const REGION_FILES = {
  iberia: "iberia.json",
  za: "za.json",
  mdg: "mdg.json",
};

const BALANCE = JSON.parse(fs.readFileSync(
  path.join(__dirname, "..", "data", "game_balance.json"), "utf8"));
const PEAKS = BALANCE.nectarFlora || {};
const POLLINATION = BALANCE.pollination || {};
const CLIMATE_BALANCE = BALANCE.climate || {};
const MOUNTAIN_MIN_M = Number(CLIMATE_BALANCE.mountainMinM || 2000);

const BAND_MAX_LEVEL = [1, 4, 9, 14, 19, 24, 29, 34, 39, 99];
const BAND_MIN_LEVEL = [0, 2, 5, 10, 15, 20, 25, 30, 35, 40];
const BAND_KG_MIN = [0.5, 1, 2, 3.5, 6, 8, 11, 14, 18, 22];
const BAND_KG_MAX = [1.5, 3, 5, 8, 12, 16, 20, 24, 27, 30];
const BAND_ORDER_COUNT = [12, 14, 16, 18, 20, 22, 22, 24, 24, 26];
const BAND_POLLINATION_COUNT = [8, 10, 12, 14, 16, 18, 20, 22, 24, 26];

const NPC_NAMES = [
  "Núria Soler", "Vicente Ferrer", "Elena Martín", "Amaia Lezeaga", "Thabo Mokoena",
  "João Ferreira", "Carmen Ríos", "Iker Arana", "Fatima El Amrani", "Pieter van Zyl",
  "Laia Puig", "Manuel Ortega", "Sofia Almeida", "Nomsa Dlamini", "Ander Urrutia",
  "Rosa Beltrán", "Mei Lin", "Wei Chen", "Yuki Tanaka", "Hiroshi Nakamura",
  "Alba Cruz", "Nico Vidal", "Priya Naidoo", "Sipho Ndlovu",
  "Aina Costa", "Marc Solé", "Hana Sato", "Kwame Mensah",
  "Inês Duarte", "Oleksandr Petrenko", "Fatou Diallo", "Lars Eriksson",
  "Amparo Gil", "Yusuf Demir", "Sibusiso Khumalo", "Mireia Pujol",
  "Chloé Moreau", "Rajesh Patel", "Leila Haddad", "Kenji Watanabe",
  "Aisha Rahman", "Mateo Quiroga", "Naledi Maseko", "Giovanni Rossi",
  "Freya Nielsen", "Hassan Benali", "Ximena Rojas", "Pavel Novák",
  "Zuri Okonkwo", "Eneko Zabala",
];

const NATIVE_POOLS = {
  iberia: {
    ATLANTIC: ["Mil flores", "Castaño", "Brezo", "Eucalipto", "Arboç", "Bosque"],
    MOUNTAIN: ["Mil flores", "Brezo", "Bosque", "Castaño", "Mielato de encina y roble", "Neret", "Arboç"],
    MEDITERRANEAN: ["Mil flores", "Romero", "Tomillo", "Lavanda", "Arboç", "Eucalipto", "Bosque"],
    SOUTH: ["Mil flores", "Eucalipto", "Romero", "Tomillo", "Castaño", "Arboç"],
    CONTINENTAL: ["Mil flores", "Romero", "Tomillo", "Lavanda", "Mielato de encina y roble"],
  },
  za: {
    FYNBOS: ["Fynbos", "Protea", "Buchu", "Aloe", "Eucalipto", "Mil flores"],
    KAROO: ["Aloe", "Mil flores", "Eucalipto"],
    HIGHVELD: ["Eucalipto", "Acacia", "Boekenhout", "Aloe", "Mil flores"],
    SUBTROPICAL: ["Eucalipto", "Mil flores", "Bosque", "Acacia"],
    BUSHVELD: ["Aloe", "Acacia", "Marula", "Boekenhout", "Eucalipto", "Mil flores", "Bosque"],
  },
  mdg: {
    EQUATORIAL: ["Litchi", "Girofle", "Ravintsara", "Longose", "Mil flores"],
    HIGHLANDS: ["Eucalipto", "Tapia", "Café", "Niaouli", "Mil flores"],
    TROPICAL: ["Tamarindo", "Baobab", "Mango", "Mangle", "Mil flores"],
    DESERT: ["Raketa", "Jujube", "Sisal", "Mil flores"],
  },
};

const CONTRACT_CROPS = {
  iberia: {
    MEDITERRANEAN: ["Campo de mostaza", "Campo de almendros", "Campo de naranjos", "Campo de trébol", "Campo de lavanda", "Campo de girasoles", "Campo de facelia"],
    CONTINENTAL: ["Campo de almendros", "Campo de mostaza", "Campo de Colza", "Campo de cerezos", "Campo de manzanos", "Campo de perales", "Campo de trébol", "Campo de lavanda", "Campo de girasoles", "Campo de facelia"],
    ATLANTIC: ["Campo de Colza", "Campo de manzanos", "Campo de perales", "Campo de cerezos", "Campo de mostaza", "Campo de trébol", "Campo de lavanda", "Campo de girasoles", "Campo de facelia", "Campo de rabaniza"],
    MOUNTAIN: ["Campo de cerezos", "Campo de manzanos", "Campo de perales", "Campo de lavanda", "Campo de trébol", "Campo de facelia", "Campo de mostaza", "Campo de rabaniza"],
    SOUTH: ["Campo de almendros", "Campo de mostaza", "Campo de naranjos", "Campo de trébol", "Campo de lavanda", "Campo de girasoles", "Campo de facelia"],
  },
  za: {
    HIGHVELD: ["Campo de girasoles", "Lucerna", "Campo de Colza", "Campo de trébol", "Campo de mostaza", "Campo de facelia", "Campo de rabaniza"],
    BUSHVELD: ["Campo de girasoles", "Lucerna", "Campo de Colza", "Campo de trébol", "Campo de mostaza", "Campo de rabaniza"],
    KAROO: ["Lucerna", "Campo de girasoles", "Campo de mostaza", "Campo de trébol", "Campo de rabaniza"],
    SUBTROPICAL: ["Litchi", "Macadamia", "Aguacate", "Campo de naranjos", "Campo de facelia", "Campo de trébol", "Campo de rabaniza"],
    FYNBOS: ["Campo de Colza", "Campo de naranjos", "Campo de girasoles", "Lucerna", "Campo de mostaza", "Campo de trébol", "Campo de facelia", "Campo de rabaniza"],
  },
  mdg: {
    EQUATORIAL: ["Litchi", "Campo de naranjos", "Campo de facelia", "Campo de trébol", "Campo de girasoles"],
    HIGHLANDS: ["Café", "Campo de naranjos", "Campo de trébol", "Campo de facelia"],
    TROPICAL: ["Mango", "Campo de naranjos", "Campo de trébol", "Campo de rabaniza"],
    DESERT: ["Sisal", "Lucerna", "Campo de mostaza"],
  },
};

const CACHE = new Map();
const SLOT_CACHE = new Map();
let OFFER_CACHE_DAY = null;
const OFFER_CACHE = new Map();
const BASE_OFFER_CACHE = new Map();

function loadRegion(region) {
  if (CACHE.has(region)) return CACHE.get(region);
  const file = path.join(__dirname, "..", "data", "hex", REGION_FILES[region]);
  const raw = JSON.parse(fs.readFileSync(file, "utf8"));
  const parcels = Array.isArray(raw.parcels) ? raw.parcels : [];
  const out = parcels.map((p) => normalizeParcel(region, p)).filter(Boolean);
  CACHE.set(region, out);
  return out;
}

const FRANCE_CLIP_WEST_LON = -1.92;
const FRANCE_MAX_LAT = [
  -1.92, 43.60,
  -1.78, 43.58,
  -1.40, 43.50,
  -0.70, 43.20,
  -0.20, 43.02,
  0.70, 42.92,
  1.50, 42.88,
  2.20, 42.74,
  3.00, 42.72,
  3.40, 42.74,
  4.55, 42.80,
];

function franceMaxLat(lon) {
  if (lon <= FRANCE_MAX_LAT[0]) return FRANCE_MAX_LAT[1];
  const n = FRANCE_MAX_LAT.length / 2;
  for (let i = 0; i < n - 1; i++) {
    const lon0 = FRANCE_MAX_LAT[i * 2];
    const lat0 = FRANCE_MAX_LAT[i * 2 + 1];
    const lon1 = FRANCE_MAX_LAT[(i + 1) * 2];
    const lat1 = FRANCE_MAX_LAT[(i + 1) * 2 + 1];
    if (lon <= lon1) {
      const t = (lon - lon0) / (lon1 - lon0);
      return lat0 + t * (lat1 - lat0);
    }
  }
  return FRANCE_MAX_LAT[FRANCE_MAX_LAT.length - 1];
}

/** Mismos recortes que el mapa del teléfono: sin Francia profunda ni Magreb. */
function playableCentroid(region, lat, lon) {
  if (region === "iberia") {
    if (lat < 36.00) return false;
    if (lon > -1.50 && lat < 37.30) return false;
    if (lon <= FRANCE_CLIP_WEST_LON) return true;
    return lat <= franceMaxLat(lon);
  }
  if (region === "mdg") {
    if (lat > -12.20 && lon < 47.40) return false;
    if (lon < 43.18 || lon > 50.52) return false;
    if (lat > -11.90 || lat < -25.65) return false;
    return true;
  }
  return true;
}

function normalizeParcel(region, p) {
  if (!p || !p.id) return null;
  const lat = Number(p.clat);
  const lng = Number(p.clon);
  if (!Number.isFinite(lat) || !Number.isFinite(lng)) return null;
  if (!playableCentroid(region, lat, lng)) return null;
  const rawElevation = p.elev == null ? NaN : Number(p.elev);
  const elevation = Number.isFinite(rawElevation) ? rawElevation : -1;
  const climate = climateFor(region, lat, lng, elevation);
  const nativePool = (NATIVE_POOLS[region] && NATIVE_POOLS[region][climate]) || ["Mil flores"];
  const contractCrops = (CONTRACT_CROPS[region] && CONTRACT_CROPS[region][climate]) || [];
  const ring = Array.isArray(p.ring) && p.ring.length >= 3 ? p.ring : null;
  return {
    id: String(p.id),
    region,
    lat,
    lng,
    ring,
    place: p.place || "",
    elevation,
    climate,
    nativePool,
    contractCrops,
    npcIndex: npcIndexFor({
      id: String(p.id), region, lat, lng, elevation, climate,
    }),
  };
}

function climateFor(region, lat, lon, elev) {
  if (region === "za") return zaClimate(lat, lon, elev);
  if (region === "mdg") return mdgClimate(lat, lon, elev);
  return iberiaClimate(lat, lon, elev);
}

function iberiaClimate(lat, lon, elev) {
  if (lat < 20.0) return "CONTINENTAL";
  if (elev >= MOUNTAIN_MIN_M || highSierra(lat, lon)) return "MOUNTAIN";
  if (lat >= 41.7 && lon <= -7.0) return "ATLANTIC";
  if (lat >= 42.6 && lon <= -1.4 && lon >= -9.5 && elev < 800) return "ATLANTIC";
  if (lat <= 38.35 && lon >= -8.9 && lon <= -3.2 && elev < 700) return "SOUTH";
  if (lat <= 37.8 && lon >= -8.9 && lon <= -7.2) return "SOUTH";
  if (lon >= -0.8 || (lon >= -1.6 && lat <= 41.0)) return "MEDITERRANEAN";
  if (lat <= 38.2 && lon >= -2.4) return "MEDITERRANEAN";
  return "CONTINENTAL";
}

function highSierra(lat, lon) {
  const boxes = [
    [42.70, 43.02, -0.95, -0.18], [42.48, 42.88, -0.18, 0.95],
    [42.18, 42.90, 0.90, 2.42], [43.10, 43.28, -5.08, -4.68],
    [42.96, 43.12, -4.88, -4.52], [42.98, 43.08, -6.00, -5.86],
    [36.92, 37.20, -3.55, -2.70], [40.18, 40.38, -5.40, -4.98],
    [40.26, 40.38, -5.82, -5.60], [40.80, 40.92, -4.02, -3.76],
    [41.72, 41.83, -1.92, -1.74], [41.95, 42.06, -2.94, -2.70],
    [42.18, 42.30, -3.10, -2.88], [37.70, 37.80, -3.54, -3.38],
    [37.34, 37.50, -2.92, -2.66], [37.16, 37.32, -2.62, -2.38],
    [37.92, 38.18, -2.92, -2.48], [40.34, 40.46, -0.74, -0.50],
    [40.06, 40.18, -1.10, -0.92],
  ];
  return boxes.some((b) => lat >= b[0] && lat <= b[1] && lon >= b[2] && lon <= b[3]);
}

function zaClimate(lat, lon, elev) {
  const known = Number.isFinite(elev) && elev >= 0;
  const e = known ? elev : 0;
  if (known && e >= 1600) return "HIGHVELD";
  if (lat <= -32.15 && lon <= 22.2) return "FYNBOS";
  if (lat <= -30.4 && lon >= 19.0 && lon <= 26.2 && (!known || e < 1300)) return "KAROO";
  // Costa este. Sin cota, 800 m de relleno dejaba esta franja en highveld.
  if (lon >= 29.7 && (!known || e < 750)) return "SUBTROPICAL";
  if (lat >= -25.6) return "BUSHVELD";
  return "HIGHVELD";
}

function mdgClimate(lat, lon, elev) {
  const e = Math.max(0, elev < 0 ? 400 : elev);
  if (lat <= -21.8 && lon <= 45.55) return "DESERT";
  if (lat <= -23.2 && lon <= 47.05) return "DESERT";
  if (lat <= -24.15 && lon <= 47.65) return "DESERT";
  if (e >= 900) return "HIGHLANDS";
  if (e >= 700 && lon >= 46.15 && lon <= 48.25 && lat <= -17.15 && lat >= -22.85) return "HIGHLANDS";
  if (lon >= 48.85) return "EQUATORIAL";
  if (lon >= 48.15 && e < 520 && lat >= -23.6) return "EQUATORIAL";
  return "TROPICAL";
}

function ringContains(lat, lon, ring) {
  if (!ring || ring.length < 3) return false;
  let inside = false;
  for (let i = 0, j = ring.length - 1; i < ring.length; j = i++) {
    const yi = Number(ring[i][0]);
    const xi = Number(ring[i][1]);
    const yj = Number(ring[j][0]);
    const xj = Number(ring[j][1]);
    if (((yi > lat) !== (yj > lat))
        && (lon < (xj - xi) * (lat - yi) / (yj - yi + 1e-18) + xi)) {
      inside = !inside;
    }
  }
  return inside;
}

/** Punto estable dentro del hex. Un apiario o una tienda en el centro no lo ocupan. */
function pointInParcel(parcel, seed) {
  const lat = parcel ? Number(parcel.lat) : 0;
  const lng = parcel ? Number(parcel.lng) : 0;
  const ring = parcel && parcel.ring;
  if (!ring) return { lat, lng };
  let minLat = Number(ring[0][0]);
  let maxLat = minLat;
  let minLng = Number(ring[0][1]);
  let maxLng = minLng;
  for (const vertex of ring) {
    const y = Number(vertex[0]);
    const x = Number(vertex[1]);
    if (y < minLat) minLat = y;
    if (y > maxLat) maxLat = y;
    if (x < minLng) minLng = x;
    if (x > maxLng) maxLng = x;
  }
  for (let i = 0; i < 40; i++) {
    const u = (hash32(`${seed}:lat:${i}`) % 1000000) / 1000000;
    const v = (hash32(`${seed}:lng:${i}`) % 1000000) / 1000000;
    const candidateLat = minLat + u * (maxLat - minLat);
    const candidateLng = minLng + v * (maxLng - minLng);
    if (ringContains(candidateLat, candidateLng, ring)) {
      return { lat: candidateLat, lng: candidateLng };
    }
  }
  return { lat, lng };
}

function orderEligible(parcel, band) {
  if (!parcel || band < 0 || band >= BAND_MAX_LEVEL.length) return false;
  if (parcel.region === "za" && BAND_MAX_LEVEL[band] < 40) return false;
  if (parcel.region === "mdg" && BAND_MAX_LEVEL[band] < 0) return false;
  return BAND_MAX_LEVEL[band] >= zoneMinLevel(parcel.region, parcel.climate);
}

function offerEligible(parcel, band) {
  if (!parcel || band < 0 || band >= BAND_MIN_LEVEL.length) return false;
  if (parcel.region === "za" && BAND_MIN_LEVEL[band] < 40) return false;
  return BAND_MIN_LEVEL[band] >= zoneMinLevel(parcel.region, parcel.climate)
    && parcel.contractCrops.length > 0;
}

function zoneMinLevel(region, climate) {
  if (region === "za") return { FYNBOS: 55, KAROO: 45, HIGHVELD: 40, SUBTROPICAL: 50, BUSHVELD: 40 }[climate] ?? 40;
  if (region === "mdg") return { EQUATORIAL: 0, HIGHLANDS: 10, TROPICAL: 20, DESERT: 30 }[climate] ?? 20;
  return { ATLANTIC: 15, CONTINENTAL: 5, MEDITERRANEAN: 0, SOUTH: 35, MOUNTAIN: 25 }[climate] ?? 5;
}

function npcIndexFor(parcel) {
  const pool = personaPool(parcel);
  return pool[Number(absHash64(`npc-name:${parcel.id || ""}`) % BigInt(pool.length))];
}

function personaPool(parcel) {
  if (parcel.region === "za" || parcel.region === "mdg") {
    // Mantiene el mismo criterio histórico del catálogo Java: el hemisferio
    // sur usa la zona sudafricana para elegir la persona, incluso en Madagascar.
    const zone = parcel.region === "mdg"
      ? zaClimate(parcel.lat, parcel.lng, 800)
      : zaClimate(parcel.lat, parcel.lng, parcel.elevation);
    if (zone === "FYNBOS") return [9, 22, 13, 4, 19, 18, 42, 34, 44];
    if (zone === "KAROO") return [9, 4, 23, 31, 42];
    if (zone === "SUBTROPICAL") return [13, 23, 22, 4, 27, 30, 48];
    if (zone === "BUSHVELD") return [4, 23, 13, 34, 30, 27];
    return [4, 23, 13, 9, 42, 37, 34];
  }
  const lat = parcel.lat;
  const lon = parcel.lng;
  if (lat >= 42.35 && lon >= -3.2 && lon <= -1.15) return [3, 7, 14, 2, 49, 35, 24];
  if (lon <= -6.7 && lat >= 41.7) return [5, 12, 15, 21, 28, 36, 31];
  if (lon <= -6.7) return [5, 12, 16, 17, 21, 43, 45];
  if (lat < 36.9 || (lat < 37.45 && lon > -6.2 && lon < -2.0)) {
    return [8, 6, 11, 15, 20, 38, 40, 46];
  }
  if (parcel.climate === "MEDITERRANEAN") return [0, 1, 10, 16, 17, 21, 18, 6, 25, 33, 26];
  if (parcel.climate === "SOUTH") return [6, 11, 15, 8, 20, 16, 17, 41, 46, 32];
  if (parcel.climate === "ATLANTIC") return [5, 12, 15, 21, 28, 44, 47];
  if (parcel.climate === "MOUNTAIN") return [3, 7, 14, 2, 20, 49, 29, 39];
  return [2, 11, 15, 19, 20, 16, 17, 37, 43, 48];
}

function stableHash64(value) {
  const s = String(value);
  let h = -3750763034362895779n;
  const prime = 1099511628211n;
  const mask = 0xffffffffffffffffn;
  for (let i = 0; i < s.length; i++) {
    h ^= BigInt(s.charCodeAt(i));
    h = (h * prime) & mask;
  }
  return BigInt.asIntN(64, h);
}

function absHash64(value) {
  const h = stableHash64(value);
  return h < 0n ? -h : h;
}

function wrapDoy(value) {
  let day = Number(value);
  while (day < 1) day += 365;
  while (day > 365) day -= 365;
  return day;
}

function canonicalFlora(flora) {
  return String(flora || "")
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .toLowerCase()
    .trim();
}

const PEAK_BY_KEY = new Map(Object.entries(PEAKS)
  .map(([key, value]) => [canonicalFlora(key), value]));

const SOUTHERN_CALENDAR = new Set([
  "fynbos", "aloe", "macadamia", "litchi", "lucerna", "acacia", "buchu",
  "protea", "boekenhout", "aguacate", "marula", "girofle", "ravintsara",
  "longose", "tapia", "cafe", "niaouli", "tamarindo", "baobab", "mango",
  "mangle", "raketa", "jujube", "sisal",
]);

function usesSouthernCalendar(flora) {
  return SOUTHERN_CALENDAR.has(canonicalFlora(flora));
}

function bloomShiftDays(parcel, flora) {
  if (!parcel) return 0;
  if (parcel.region !== "za" && parcel.region !== "mdg") {
    return {
      ATLANTIC: Number(CLIMATE_BALANCE.bloomShiftAtlantic ?? 8),
      MOUNTAIN: Number(CLIMATE_BALANCE.bloomShiftMountain ?? 5),
      MEDITERRANEAN: Number(CLIMATE_BALANCE.bloomShiftMediterranean ?? -6),
      SOUTH: Number(CLIMATE_BALANCE.bloomShiftSouth ?? -10),
      CONTINENTAL: Number(CLIMATE_BALANCE.bloomShiftContinental ?? 0),
    }[parcel.climate] || 0;
  }
  const local = parcel.region === "mdg"
    ? { EQUATORIAL: -4, HIGHLANDS: 2, DESERT: 5, TROPICAL: 0 }[parcel.climate] || 0
    : { FYNBOS: 0, KAROO: 4, HIGHVELD: 0, SUBTROPICAL: -6, BUSHVELD: -3 }[parcel.climate] || 0;
  return usesSouthernCalendar(flora) ? local : local + 183;
}

function doyOf(nowMs) {
  const date = new Date(nowMs);
  const start = Date.UTC(date.getUTCFullYear(), 0, 1);
  return Math.min(365, Math.floor((nowMs - start) / 86400000) + 1);
}

function circularDoyDistance(a, b) {
  const d = Math.abs(wrapDoy(a) - wrapDoy(b));
  return Math.min(d, 365 - d);
}

/** Altura de floración hoy en el clima de la parcela, de 0 a la cima del pico. */
function bloomHeight(parcel, flora, doy) {
  const peaks = PEAK_BY_KEY.get(canonicalFlora(flora)) || [];
  const shift = bloomShiftDays(parcel, flora);
  let best = 0;
  for (const peak of peaks) {
    const center = wrapDoy(Number(peak.center || 0) + shift);
    const width = Math.max(1, Number(peak.width || 1));
    const height = Number(peak.height || 0);
    const distance = circularDoyDistance(doy, center);
    const value = height * Math.exp(-0.5 * (distance / width) ** 2);
    if (value > best) best = value;
  }
  return best;
}

const BLOOM_IN = 0.4;
const BLOOM_EDGE = 0.15;

function orderFloraCandidates(parcel, band) {
  const maxLevel = BAND_MAX_LEVEL[band] ?? 99;
  const seen = new Set();
  const out = [];
  const sources = [...(parcel && parcel.nativePool || []), ...(parcel && parcel.contractCrops || [])];
  for (const flora of sources) {
    const key = canonicalFlora(flora);
    if (!key || seen.has(key)) continue;
    if (floraAccessLevel(flora) > maxLevel) continue;
    seen.add(key);
    out.push(flora);
  }
  return out.length ? out : ["Mil flores"];
}

/**
 * 75 % miel en flor en este clima, 15 % en el borde y 10 % fuera de época.
 * Si el grupo sorteado está vacío, pasa al siguiente sin cambiar de clima.
 */
function pickSeasonalFlora(parcel, floras, nowMs, seed) {
  const list = floras && floras.length ? floras : ["Mil flores"];
  const doy = doyOf(nowMs);
  const buckets = { bloom: [], edge: [], out: [] };
  for (const flora of list) {
    const height = bloomHeight(parcel, flora, doy);
    if (height >= BLOOM_IN) buckets.bloom.push(flora);
    else if (height >= BLOOM_EDGE) buckets.edge.push(flora);
    else buckets.out.push(flora);
  }
  const roll = hash32(`${seed}:season`) % 100;
  const order = roll < 75
    ? ["bloom", "edge", "out"]
    : roll < 90
      ? ["edge", "bloom", "out"]
      : ["out", "edge", "bloom"];
  for (const name of order) {
    const bucket = buckets[name];
    if (bucket.length) {
      return bucket[hash32(`${seed}:${name}`) % bucket.length];
    }
  }
  return list[0];
}

function bloomSpans(parcel, flora, minBloom01) {
  const peaks = PEAK_BY_KEY.get(canonicalFlora(flora)) || [];
  const shift = bloomShiftDays(parcel, flora);
  const raw = [];
  for (const peak of peaks) {
    const height = Number(peak.height || 0);
    const width = Math.max(1, Number(peak.width || 1));
    if (height + 1e-12 < minBloom01) continue;
    const x = Math.sqrt(-2 * Math.log(minBloom01 / height));
    const half = Math.max(1, Math.round(x * width));
    const center = wrapDoy(Number(peak.center || 0) + shift);
    const start = center - half;
    const end = center + half;
    if (start < 1 && end > 365) {
      raw.push([1, 365]);
    } else if (start < 1) {
      raw.push([wrapDoy(start), 365], [1, end]);
    } else if (end > 365) {
      raw.push([start, 365], [1, wrapDoy(end)]);
    } else {
      raw.push([start, end]);
    }
  }
  raw.sort((a, b) => a[0] - b[0]);
  const merged = [];
  for (const span of raw) {
    const last = merged[merged.length - 1];
    if (last && span[0] <= last[1] + 1) {
      last[1] = Math.max(last[1], span[1]);
    } else {
      merged.push([span[0], span[1]]);
    }
  }
  return merged;
}

function containsDoy(span, doy) {
  return span[0] <= span[1]
    ? doy >= span[0] && doy <= span[1]
    : doy >= span[0] || doy <= span[1];
}

function daysUntilStart(start, from) {
  const a = wrapDoy(start);
  const b = wrapDoy(from);
  return a >= b ? a - b : 365 - b + a;
}

function packSlots(parcel, flora) {
  const cacheKey = `${parcel.id}:${canonicalFlora(flora)}`;
  if (SLOT_CACHE.has(cacheKey)) return SLOT_CACHE.get(cacheKey);
  const minDays = Math.max(2, Number(POLLINATION.slotDaysMin || 5));
  const maxDays = Math.max(minDays, Number(POLLINATION.slotDaysMax || 21));
  const spans = bloomSpans(parcel, flora, Number(POLLINATION.bloomThreshold || 0.4));
  const out = [];
  let slot = 0;
  for (const [start, end] of spans) {
    let remaining = start <= end ? end - start + 1 : (365 - start + 1) + end;
    let cursor = start;
    while (remaining >= minDays) {
      const h = absHash64(`npc-slot:${parcel.id}:${canonicalFlora(flora)}:${slot}`);
      const days = minDays + Number(h % BigInt(maxDays - minDays + 1));
      const take = Math.min(days, remaining);
      if (take < minDays) break;
      const slotEnd = wrapDoy(cursor + take - 1);
      out.push({ startDoy: cursor, endDoy: slotEnd, days: take });
      cursor = wrapDoy(slotEnd + 1);
      remaining -= take;
      slot++;
    }
  }
  SLOT_CACHE.set(cacheKey, out);
  return out;
}

function seasonalKeepRate(parcel, doy) {
  const day = Math.max(1, Math.min(365, doy));
  if (parcel.region === "mdg") {
    const summer = day >= 305 || day <= 60;
    if (parcel.climate === "HIGHLANDS") {
      if (day >= 244 && day <= 334) return 1;
      if (summer) return 0.7;
      return day >= 61 && day <= 120 ? 0.45 : 0.12;
    }
    if (parcel.climate === "TROPICAL") {
      if (day >= 121 && day <= 243) return 1;
      return summer ? 0.65 : 0.35;
    }
    if (parcel.climate === "DESERT") {
      if (summer) return 0.85;
      return day >= 121 && day <= 212 ? 0.45 : 0.12;
    }
    if (summer) return 1;
    return day <= 120 ? 0.55 : (day <= 243 ? 0.12 : 0.75);
  }
  if (parcel.region === "za") {
    const summer = day >= 305 || day <= 60;
    const winter = day >= 152 && day <= 243;
    if (parcel.climate === "KAROO") return summer ? 0.55 : (winter ? 0.1 : 0.28);
    if (parcel.climate === "SUBTROPICAL") {
      if (day >= 305 || day <= 31) return 1;
      return day >= 182 && day <= 304 ? 0.85 : 0.35;
    }
    if (parcel.climate === "FYNBOS") {
      if (day >= 213 && day <= 304) return 1;
      return summer ? 0.75 : 0.28;
    }
    if (summer) return 1;
    return winter ? 0.15 : 0.5;
  }
  if (parcel.climate === "MOUNTAIN") return day >= 121 && day <= 273 ? 1 : 0.05;
  if (day >= 305 || day <= 31) return parcel.climate === "ATLANTIC" ? 0.18 : 0.08;
  return day <= 59 ? 0.45 : 1;
}

function offerForParcel(parcel, band, nowMs, dayKey) {
  const date = new Date(nowMs);
  const start = Date.UTC(date.getUTCFullYear(), 0, 1);
  const doy = Math.min(365, Math.floor((nowMs - start) / 86400000) + 1);
  if (OFFER_CACHE_DAY !== dayKey) {
    OFFER_CACHE.clear();
    BASE_OFFER_CACHE.clear();
    OFFER_CACHE_DAY = dayKey;
  }
  const cacheKey = `${parcel.id}:${band}:${dayKey}:${doy}`;
  if (OFFER_CACHE.has(cacheKey)) return OFFER_CACHE.get(cacheKey);
  const save = (value) => {
    OFFER_CACHE.set(cacheKey, value);
    return value;
  };
  if (!offerEligible(parcel, band)) return save(null);
  const baseKey = `${parcel.id}:${band}:${dayKey}:${doy}`;
  let best = BASE_OFFER_CACHE.get(baseKey);
  if (best === undefined) {
    let bestWait = Number.MAX_SAFE_INTEGER;
    const bandMinLevel = BAND_MIN_LEVEL[band] ?? 0;
    for (const flora of parcel.contractCrops || []) {
      // Desde el nivel 2 el contrato no exige haber desbloqueado el cultivo.
      if (bandMinLevel < 2 && cropAccessLevel(flora) > bandMinLevel) continue;
      for (const slot of packSlots(parcel, flora)) {
        if (containsDoy([slot.startDoy, slot.endDoy], doy)) continue;
        const wait = daysUntilStart(slot.startDoy, doy);
        if (wait < 1 || wait >= bestWait) continue;
        bestWait = wait;
        best = { flora, startDoy: slot.startDoy, endDoy: slot.endDoy };
      }
    }
    if (!best || bestWait > Number(POLLINATION.horizonDays || 21)) best = null;
    BASE_OFFER_CACHE.set(baseKey, best);
  }
  if (!best) return save(null);
  const keep = seasonalKeepRate(parcel, doy);
  if (keep < 0.999) {
    const h = absHash64(`season-keep:${dayKey}:${parcel.id}:${band}`) % 1000n;
    if (h >= BigInt(Math.round(keep * 1000))) return save(null);
  }
  return save(best);
}

const CROP_LEVELS = new Map([
  ["campo de naranjos", 2], ["campo de almendros", 4], ["mango", 5],
  ["campo de cerezos", 6], ["campo de perales", 8], ["campo de mostaza", 9],
  ["campo de rabaniza", 10], ["campo de manzanos", 11], ["campo de trebol", 13],
  ["campo de girasoles", 14], ["campo de lavanda", 16], ["cafe", 16],
  ["campo de colza", 17], ["campo de facelia", 20], ["lucerna", 27],
  ["sisal", 30], ["litchi", 43], ["macadamia", 48], ["aguacate", 60],
]);

function buildFloraAccess() {
  const out = new Map();
  for (const [region, climates] of Object.entries(NATIVE_POOLS)) {
    for (const [climate, pool] of Object.entries(climates)) {
      const level = zoneMinLevel(region, climate);
      for (const flora of pool) {
        const key = canonicalFlora(flora);
        out.set(key, Math.min(out.has(key) ? out.get(key) : level, level));
      }
    }
  }
  for (const [flora, level] of CROP_LEVELS) {
    out.set(flora, Math.min(out.has(flora) ? out.get(flora) : level, level));
  }
  return out;
}

const FLORA_ACCESS = buildFloraAccess();

function cropAccessLevel(flora) {
  return CROP_LEVELS.get(canonicalFlora(flora)) || 0;
}

function floraAccessLevel(flora) {
  return FLORA_ACCESS.get(canonicalFlora(flora)) || 0;
}

// Dentro de un mismo nivel, de la más fácil de encontrar a la más escasa.
// El factor va de 1 a 1,2: como mucho un 20 % entre la primera y la última.
const SCARCITY_BANDS = [
  ["Mil flores", "Eucalipto", "Romero", "Tomillo", "Lavanda", "Bosque", "Litchi", "Arboç", "Girofle", "Ravintsara", "Longose"],
  ["Mango", "Mielato de encina y roble"],
  ["Campo de rabaniza", "Café", "Niaouli", "Tapia"],
  ["Castaño", "Brezo"],
  ["Campo de facelia", "Tamarindo", "Baobab", "Mangle"],
  ["Sisal", "Jujube", "Raketa"],
  ["Acacia", "Aloe", "Marula", "Boekenhout"],
  ["Fynbos", "Protea", "Buchu"],
];
const ORDER_SCARCITY = new Map();
for (const band of SCARCITY_BANDS) {
  const last = Math.max(1, band.length - 1);
  band.forEach((flora, index) => {
    const factor = band.length <= 1 ? 1 : 1 + 0.2 * index / last;
    ORDER_SCARCITY.set(canonicalFlora(flora), Math.round(factor * 1000) / 1000);
  });
}

function orderScarcity(flora) {
  return ORDER_SCARCITY.get(canonicalFlora(flora)) || 1;
}

const MIN_MEAN_PRICE_EUR = 12;
const MAX_MEAN_PRICE_EUR = 18;
let maxFloraAccessLevel = 1;
for (const level of FLORA_ACCESS.values()) {
  if (level > maxFloraAccessLevel) maxFloraAccessLevel = level;
}

/** Precio de mercado con la oferta igual a la demanda: 12 €/kg en la miel más temprana y 18 en la de mayor nivel. */
function meanPriceEur(flora) {
  const level = floraAccessLevel(flora);
  const t = Math.min(1, Math.max(0, level) / maxFloraAccessLevel);
  return Math.round((MIN_MEAN_PRICE_EUR + (MAX_MEAN_PRICE_EUR - MIN_MEAN_PRICE_EUR) * t) * 100) / 100;
}

function npc(parcel) {
  return NPC_NAMES[parcel.npcIndex % NPC_NAMES.length];
}

function floorMod(value, modulus) {
  return ((value % modulus) + modulus) % modulus;
}

function hash32(value) {
  let h = 2166136261;
  const s = String(value);
  for (let i = 0; i < s.length; i++) {
    h ^= s.charCodeAt(i);
    h = Math.imul(h, 16777619);
  }
  return h >>> 0;
}

function bandDailyOrderCount(eligible) {
  return eligible < 50 ? 0 : Math.floor((eligible * 2) / 50);
}

function bandDailyOfferCount(parcelCount, band) {
  if (parcelCount <= 0) return 0;
  const density = Math.max(2, Math.floor((parcelCount * 2) / 70));
  return Math.min(48, Math.max(BAND_POLLINATION_COUNT[band], density));
}

const GRID = new Map();
const GRID_STEP = 0.2;

function gridFor(region) {
  if (GRID.has(region)) return GRID.get(region);
  const cells = new Map();
  for (const parcel of loadRegion(region)) {
    const key = `${Math.floor(parcel.lat / GRID_STEP)}:${Math.floor(parcel.lng / GRID_STEP)}`;
    let bucket = cells.get(key);
    if (!bucket) {
      bucket = [];
      cells.set(key, bucket);
    }
    bucket.push(parcel);
  }
  GRID.set(region, cells);
  return cells;
}

/** Hex que contiene el punto, mirando solo la celda y las vecinas. */
function parcelAt(region, lat, lng) {
  if (!REGION_FILES[region] || !Number.isFinite(lat) || !Number.isFinite(lng)) return null;
  const cells = gridFor(region);
  const row = Math.floor(lat / GRID_STEP);
  const col = Math.floor(lng / GRID_STEP);
  let nearest = null;
  let nearestKm = 8;
  for (let dr = -1; dr <= 1; dr++) {
    for (let dc = -1; dc <= 1; dc++) {
      const bucket = cells.get(`${row + dr}:${col + dc}`);
      if (!bucket) continue;
      for (const parcel of bucket) {
        if (parcel.ring && ringContains(lat, lng, parcel.ring)) return parcel;
        const km = haversineKm(lat, lng, parcel.lat, parcel.lng);
        if (km < nearestKm) {
          nearestKm = km;
          nearest = parcel;
        }
      }
    }
  }
  return nearest;
}

function haversineKm(lat, lng, lat2, lng2) {
  const p1 = (lat * Math.PI) / 180;
  const p2 = (lat2 * Math.PI) / 180;
  const dphi = ((lat2 - lat) * Math.PI) / 180;
  const dl = ((lng2 - lng) * Math.PI) / 180;
  const a = Math.sin(dphi / 2) ** 2 + Math.cos(p1) * Math.cos(p2) * Math.sin(dl / 2) ** 2;
  return 2 * 6371 * Math.asin(Math.min(1, Math.sqrt(a)));
}

function getParcels(region) {
  return loadRegion(region);
}

function stats() {
  return {
    iberia: getParcels("iberia").length,
    za: getParcels("za").length,
    mdg: getParcels("mdg").length,
  };
}

module.exports = {
  BAND_MAX_LEVEL,
  BAND_MIN_LEVEL,
  BAND_KG_MIN,
  BAND_KG_MAX,
  BAND_ORDER_COUNT,
  BAND_POLLINATION_COUNT,
  NPC_NAMES,
  getParcels,
  parcelAt,
  playableCentroid,
  orderEligible,
  pointInParcel,
  ringContains,
  offerEligible,
  bandDailyOrderCount,
  bandDailyOfferCount,
  floraAccessLevel,
  orderScarcity,
  meanPriceEur,
  cropAccessLevel,
  canonicalFlora,
  bloomShiftDays,
  bloomHeight,
  bloomSpans,
  orderFloraCandidates,
  pickSeasonalFlora,
  offerForParcel,
  seasonalKeepRate,
  npc,
  stats,
  zoneMinLevel,
};
