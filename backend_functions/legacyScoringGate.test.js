"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const { isLegacyScoringApproved } = require("./legacyScoringGate");

const activeRegistry = {
  schemaVersion: 1,
  status: "ACTIVE",
  testIds: ["historical_neet_2024_01", "historical_jee_2025_02"]
};

test("Only an exact ID in an active schema-v1 server allowlist is grandfathered", () => {
  assert.equal(isLegacyScoringApproved("historical_neet_2024_01", activeRegistry), true);
  assert.equal(isLegacyScoringApproved(" historical_neet_2024_01 ", activeRegistry), true);
  assert.equal(isLegacyScoringApproved("new_student_test_2026", activeRegistry), false);
  assert.equal(isLegacyScoringApproved("HISTORICAL_NEET_2024_01", activeRegistry), false);
});

test("Missing, inactive, malformed, or unsupported allowlists fail closed", () => {
  assert.equal(isLegacyScoringApproved("historical_neet_2024_01", null), false);
  assert.equal(isLegacyScoringApproved("historical_neet_2024_01", {
    ...activeRegistry, status: "DRAFT"
  }), false);
  assert.equal(isLegacyScoringApproved("historical_neet_2024_01", {
    ...activeRegistry, schemaVersion: 2
  }), false);
  assert.equal(isLegacyScoringApproved("historical_neet_2024_01", {
    ...activeRegistry, testIds: "historical_neet_2024_01"
  }), false);
  assert.equal(isLegacyScoringApproved("historical_neet_2024_01", {
    ...activeRegistry, testIds: []
  }), false);
});

test("Invalid IDs and client-style lookalike values are never approved", () => {
  assert.equal(isLegacyScoringApproved("", activeRegistry), false);
  assert.equal(isLegacyScoringApproved(null, activeRegistry), false);
  assert.equal(isLegacyScoringApproved("historical_neet_2024_010", activeRegistry), false);
});
