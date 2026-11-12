"use strict";

const { test } = require("node:test");
const assert = require("node:assert/strict");
const { _test } = require("./aiConnector");

test("Admin verification rejects missing authentication", () => {
  assert.equal(_test.isVerifiedAdmin(null), false);
  assert.equal(_test.isVerifiedAdmin({ uid: "" }), false);
  assert.equal(_test.isVerifiedAdmin({ uid: "student", token: {} }), false);
});

test("Admin verification accepts admin claim and verified owner account only", () => {
  assert.equal(_test.isVerifiedAdmin({ uid: "a", token: { admin: true } }), true);
  assert.equal(_test.isVerifiedAdmin({
    uid: "owner", token: { email: "abhi952817@gmail.com", email_verified: true }
  }), true);
  assert.equal(_test.isVerifiedAdmin({
    uid: "spoof", token: { email: "abhi952817@gmail.com", email_verified: false }
  }), false);
  assert.equal(_test.isVerifiedAdmin({
    uid: "student", token: { email: "student@example.com", email_verified: true }
  }), false);
});

test("Gemini verification rejects an empty or malformed secret before network access", async () => {
  await assert.rejects(
    () => _test.verifyGeminiApiKey(""),
    { code: "failed-precondition" }
  );
});

test("Gemini verification sends the secret only in the API header and returns safe model metadata", async () => {
  let requestedUrl = "";
  let requestedHeaders = null;
  const result = await _test.verifyGeminiApiKey("AIza-test-secret-key-123456789", async (url, options) => {
    requestedUrl = url;
    requestedHeaders = options.headers;
    return {
      ok: true,
      status: 200,
      json: async () => ({
        models: [
          { name: "models/gemini-2.5-flash", supportedGenerationMethods: ["generateContent"] },
          { name: "models/embedding-001", supportedGenerationMethods: ["embedContent"] },
          { name: "models/gemini-2.0-flash", supportedGenerationMethods: ["generateContent"] }
        ]
      })
    };
  });

  assert.equal(requestedUrl.includes("AIza-test-secret-key"), false);
  assert.equal(requestedHeaders["x-goog-api-key"], "AIza-test-secret-key-123456789");
  assert.deepEqual(result, {
    provider: "gemini",
    verified: true,
    models: ["gemini-2.5-flash", "gemini-2.0-flash"]
  });
  assert.equal(JSON.stringify(result).includes("AIza-test-secret-key"), false);
});

test("Gemini verification maps provider rejection without leaking provider body or secret", async () => {
  await assert.rejects(
    () => _test.verifyGeminiApiKey("AIza-test-secret-key-123456789", async () => ({
      ok: false,
      status: 403,
      json: async () => ({ error: { message: "secret-bearing provider body" } })
    })),
    error => error.code === "permission-denied" && !error.message.includes("secret-bearing")
  );
});
