"use strict";

let initialized = false;
let firebaseAuth = null;
let configured = false;

function initialize() {
  if (initialized) return;
  initialized = true;
  const hasCredentials = Boolean(
    process.env.FIREBASE_SERVICE_ACCOUNT_JSON
      || process.env.GOOGLE_APPLICATION_CREDENTIALS
      || process.env.FIREBASE_PROJECT_ID
  );
  if (!hasCredentials) return;
  try {
    const admin = require("firebase-admin");
    if (admin.apps.length === 0) {
      if (process.env.FIREBASE_SERVICE_ACCOUNT_JSON) {
        const serviceAccount = JSON.parse(process.env.FIREBASE_SERVICE_ACCOUNT_JSON);
        admin.initializeApp({
          credential: admin.credential.cert(serviceAccount),
          projectId: process.env.FIREBASE_PROJECT_ID || serviceAccount.project_id,
        });
      } else {
        admin.initializeApp({
          credential: admin.credential.applicationDefault(),
          projectId: process.env.FIREBASE_PROJECT_ID || undefined,
        });
      }
    }
    firebaseAuth = admin.auth();
    configured = true;
    console.log("autenticación Firebase habilitada");
  } catch (err) {
    console.error("no se pudo inicializar Firebase Admin:", err.message);
  }
}

async function verify(req) {
  initialize();
  if (!configured || !firebaseAuth) return null;
  const token = req && req.headers && req.headers["x-firebase-id-token"];
  if (!token) return null;
  try {
    const decoded = await firebaseAuth.verifyIdToken(token);
    return decoded.uid || null;
  } catch (err) {
    return null;
  }
}

function isConfigured() {
  initialize();
  return configured;
}

module.exports = { verify, isConfigured };
