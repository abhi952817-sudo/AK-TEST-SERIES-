"use strict";

const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { defineSecret } = require("firebase-functions/params");

const adminGeminiApiKey = defineSecret("AKTS_ADMIN_GEMINI_API_KEY");
const GEMINI_MODELS_ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models";

function isVerifiedAdmin(auth) {
  if (!auth || !auth.uid) return false;
  const token = auth.token || {};
  return token.admin === true ||
    (token.email === "abhi952817@gmail.com" && token.email_verified === true);
}

/**
 * Checks a Gemini API key without returning or logging the secret.
 * Kept pure so HTTP behavior can be tested without Firebase emulators.
 */
async function verifyGeminiApiKey(apiKey, fetchImpl = globalThis.fetch) {
  if (typeof apiKey !== "string" || apiKey.trim().length < 20) {
    const error = new Error("Admin Gemini secret is missing or invalid.");
    error.code = "failed-precondition";
    throw error;
  }
  if (typeof fetchImpl !== "function") {
    const error = new Error("Server fetch implementation is unavailable.");
    error.code = "internal";
    throw error;
  }

  let response;
  try {
    response = await fetchImpl(GEMINI_MODELS_ENDPOINT + "?pageSize=100", {
      method: "GET",
      headers: { "x-goog-api-key": apiKey.trim() },
      signal: AbortSignal.timeout(15000)
    });
  } catch (_) {
    const error = new Error("Gemini provider could not be reached.");
    error.code = "unavailable";
    throw error;
  }

  if (!response || !response.ok) {
    const status = response && Number.isInteger(response.status) ? response.status : 0;
    const error = new Error(
      status === 401 || status === 403
        ? "Admin Gemini secret was rejected by Google."
        : status === 429
          ? "Gemini provider rate limit reached."
          : "Gemini provider verification failed."
    );
    error.code = status === 429 ? "resource-exhausted" :
      status === 401 || status === 403 ? "permission-denied" : "unavailable";
    throw error;
  }

  let payload;
  try {
    payload = await response.json();
  } catch (_) {
    const error = new Error("Gemini returned an unreadable verification response.");
    error.code = "data-loss";
    throw error;
  }

  if (!payload || !Array.isArray(payload.models)) {
    const error = new Error("Gemini verification response did not contain a model list.");
    error.code = "data-loss";
    throw error;
  }

  const models = payload.models
    .filter(model => model && typeof model.name === "string" &&
      Array.isArray(model.supportedGenerationMethods) &&
      model.supportedGenerationMethods.includes("generateContent"))
    .map(model => model.name.replace(/^models\//, ""))
    .filter(Boolean)
    .slice(0, 50);

  if (models.length === 0) {
    const error = new Error("The Gemini key is valid, but no generateContent model is available.");
    error.code = "failed-precondition";
    throw error;
  }

  return { provider: "gemini", verified: true, models };
}

exports.verifyAdminGeminiConnector = onCall(
  { secrets: [adminGeminiApiKey], timeoutSeconds: 30, memory: "256MiB" },
  async request => {
    if (!request.auth || !request.auth.uid) {
      throw new HttpsError("unauthenticated", "Sign in is required.");
    }
    if (!isVerifiedAdmin(request.auth)) {
      throw new HttpsError("permission-denied", "Only a verified Admin can verify the central Gemini connector.");
    }

    try {
      const result = await verifyGeminiApiKey(adminGeminiApiKey.value());
      return { ...result, checkedAt: Date.now() };
    } catch (error) {
      const allowedCodes = new Set([
        "failed-precondition", "unavailable", "resource-exhausted",
        "permission-denied", "data-loss", "internal"
      ]);
      throw new HttpsError(
        allowedCodes.has(error.code) ? error.code : "internal",
        error.message || "Gemini connector verification failed."
      );
    }
  }
);

exports._test = { isVerifiedAdmin, verifyGeminiApiKey };
