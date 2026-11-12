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

test("Admin Gemini generation rejects empty prompts and unsafe model identifiers", async () => {
  await assert.rejects(
    () => _test.generateGeminiContent("AIza-test-secret-key-123456789", { prompt: "" }),
    { code: "invalid-argument" }
  );
  await assert.rejects(
    () => _test.generateGeminiContent("AIza-test-secret-key-123456789", {
      prompt: "Generate JSON",
      modelName: "../attacker"
    }),
    { code: "invalid-argument" }
  );
});

test("Admin Gemini generation keeps the secret in headers and returns text with token usage", async () => {
  let requestUrl = "";
  let requestOptions = null;
  const result = await _test.generateGeminiContent(
    "AIza-test-secret-key-123456789",
    {
      prompt: "Create one test question as JSON.",
      systemInstruction: "Return valid JSON only.",
      modelName: "gemini-2.5-flash"
    },
    async (url, options) => {
      requestUrl = url;
      requestOptions = options;
      return {
        ok: true,
        status: 200,
        json: async () => ({
          modelVersion: "gemini-2.5-flash-001",
          candidates: [{
            finishReason: "STOP",
            content: { parts: [{ text: '{"questions":[]}' }] }
          }],
          usageMetadata: {
            promptTokenCount: 42,
            candidatesTokenCount: 18,
            totalTokenCount: 60
          }
        })
      };
    }
  );

  assert.equal(requestUrl.includes("AIza-test-secret-key"), false);
  assert.equal(requestUrl.endsWith("/gemini-2.5-flash:generateContent"), true);
  assert.equal(requestOptions.headers["x-goog-api-key"], "AIza-test-secret-key-123456789");
  assert.equal(JSON.parse(requestOptions.body).contents[0].parts[0].text, "Create one test question as JSON.");
  assert.deepEqual(result, {
    responseText: '{"questions":[]}',
    modelVersion: "gemini-2.5-flash-001",
    usageMetadata: {
      promptTokenCount: 42,
      candidatesTokenCount: 18,
      totalTokenCount: 60
    }
  });
  assert.equal(JSON.stringify(result).includes("AIza-test-secret-key"), false);
});

test("Admin Gemini generation rejects provider errors without exposing raw provider body", async () => {
  await assert.rejects(
    () => _test.generateGeminiContent(
      "AIza-test-secret-key-123456789",
      { prompt: "Generate JSON." },
      async () => ({
        ok: false,
        status: 429,
        json: async () => ({ error: { message: "private provider details" } })
      })
    ),
    error => error.code === "resource-exhausted" && !error.message.includes("private provider details")
  );
});

test("Admin Gemini text mode requests plain text for AI Mentor explanations", async () => {
  let requestBody = null;
  const result = await _test.generateGeminiContent(
    "AIza-test-secret-key-123456789",
    { prompt: "Explain this question.", responseMimeType: "text/plain" },
    async (_url, options) => {
      requestBody = JSON.parse(options.body);
      return {
        ok: true,
        status: 200,
        json: async () => ({
          candidates: [{ content: { parts: [{ text: "Step one. Step two." }] } }]
        })
      };
    }
  );
  assert.equal(requestBody.generationConfig.responseMimeType, "text/plain");
  assert.equal(result.responseText, "Step one. Step two.");
});
