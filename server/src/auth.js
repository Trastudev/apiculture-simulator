"use strict";

const { OAuth2Client } = require("google-auth-library");

const WEB_CLIENT_ID = process.env.GOOGLE_WEB_CLIENT_ID
  || "1072639985838-u22hlv9vi49qroutsse3aism2oer0el6.apps.googleusercontent.com";
const ANDROID_CLIENT_ID = process.env.GOOGLE_ANDROID_CLIENT_ID
  || "1072639985838-vmvj3dfubklvarve0hv0hnpbuuk07en6.apps.googleusercontent.com";
const AUDIENCES = [WEB_CLIENT_ID, ANDROID_CLIENT_ID].filter((id, index, all) =>
  id && all.indexOf(id) === index);

let client = null;

function google() {
  if (!client) client = new OAuth2Client(WEB_CLIENT_ID);
  return client;
}

function aliases() {
  if (!process.env.GOOGLE_UID_ALIASES) return {};
  try {
    const parsed = JSON.parse(process.env.GOOGLE_UID_ALIASES);
    return parsed && typeof parsed === "object" ? parsed : {};
  } catch (err) {
    return {};
  }
}

async function verifyToken(token) {
  if (!token || typeof token !== "string" || token.length > 8192) return null;
  try {
    const ticket = await google().verifyIdToken({
      idToken: token,
      audience: AUDIENCES,
    });
    const payload = ticket.getPayload() || {};
    const sub = payload.sub || null;
    if (!sub) return null;
    return aliases()[sub] || sub;
  } catch (err) {
    return null;
  }
}

function bearer(req) {
  const headers = req && req.headers;
  const header = headers && headers.authorization;
  if (!header || !header.startsWith("Bearer ")) return "";
  return header.slice("Bearer ".length).trim();
}

async function verify(req) {
  const headers = req && req.headers;
  const explicit = headers && (headers["x-google-id-token"] || headers["x-firebase-id-token"]);
  const fromExplicit = await verifyToken(explicit);
  if (fromExplicit) return fromExplicit;
  const fromBearer = await verifyToken(bearer(req));
  return fromBearer;
}

function webClientId() {
  return WEB_CLIENT_ID;
}

function isConfigured() {
  return Boolean(WEB_CLIENT_ID);
}

function sameUid(authUid, ownerId) {
  return String(authUid || "") === String(ownerId || "");
}

function firestore() {
  return null;
}

module.exports = { verify, verifyToken, webClientId, isConfigured, sameUid, firestore };
