"use strict";

function encode(points) {
  let out = "";
  let lastLat = 0;
  let lastLng = 0;
  for (const p of points) {
    const lat = Math.round(p[0] * 1e5);
    const lng = Math.round(p[1] * 1e5);
    out += encodeDelta(lat - lastLat);
    out += encodeDelta(lng - lastLng);
    lastLat = lat;
    lastLng = lng;
  }
  return out;
}

function decode(encoded) {
  if (!encoded) return [];
  const out = [];
  let index = 0;
  let lat = 0;
  let lng = 0;
  while (index < encoded.length) {
    const latR = decodeDelta(encoded, index);
    index = latR[1];
    lat += latR[0];
    if (index >= encoded.length) break;
    const lngR = decodeDelta(encoded, index);
    index = lngR[1];
    lng += lngR[0];
    out.push([lat / 1e5, lng / 1e5]);
  }
  return out;
}

function encodeDelta(value) {
  let v = value < 0 ? ~(value << 1) : value << 1;
  let out = "";
  while (v >= 0x20) {
    out += String.fromCharCode((0x20 | (v & 0x1f)) + 63);
    v >>= 5;
  }
  out += String.fromCharCode(v + 63);
  return out;
}

function decodeDelta(encoded, index) {
  let result = 0;
  let shift = 0;
  let b;
  do {
    b = encoded.charCodeAt(index++) - 63;
    result |= (b & 0x1f) << shift;
    shift += 5;
  } while (b >= 0x20 && index <= encoded.length);
  const delta = (result & 1) !== 0 ? ~(result >> 1) : result >> 1;
  return [delta, index];
}

module.exports = { encode, decode };
