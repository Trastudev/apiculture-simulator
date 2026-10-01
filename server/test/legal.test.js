"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const { privacyHtml, deleteHtml } = require("../src/legal");

test("privacy page names the data Play must be told about", () => {
  const html = privacyHtml();
  assert.match(html, /Política de privacidad de ApiSim/);
  assert.match(html, /identificador de publicidad/);
  assert.match(html, /Google Maps/);
  assert.match(html, /13 años en adelante/);
  assert.match(html, /programa de Familias/);
  assert.match(html, /href="\/delete-account"/);
});

test("delete page signs in with the configured Google client", () => {
  const html = deleteHtml("client.apps.googleusercontent.com");
  assert.match(html, /accounts\.google\.com\/gsi\/client/);
  assert.match(html, /data-client_id="client\.apps\.googleusercontent\.com"/);
  assert.match(html, /\/account\/delete/);
});

test("delete page escapes a broken client id", () => {
  const html = deleteHtml('"><script>');
  assert.equal(html.includes('"><script>'), false);
  assert.match(html, /&quot;&gt;&lt;script&gt;/);
});
