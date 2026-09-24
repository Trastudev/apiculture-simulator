"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const { sameUid } = require("../src/auth");

test("sameUid compares player ids as text", () => {
  assert.equal(sameUid("abc", "abc"), true);
  assert.equal(sameUid("abc", "abd"), false);
  assert.equal(sameUid("abc", null), false);
  assert.equal(sameUid(null, ""), true);
});
