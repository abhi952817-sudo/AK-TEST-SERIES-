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


/**
 * Calls Gemini only from Cloud Functions. The API key is never accepted from
 * callable input and is never returned to the Android client.
 */
async function generateGeminiContent(apiKey, input, fetchImpl = globalThis.fetch) {
  if (typeof apiKey !== "string" || apiKey.trim().length < 20) {
    const error = new Error("Admin Gemini secret is missing or invalid.");
    error.code = "failed-precondition";
    throw error;
  }
  const prompt = typeof input?.prompt === "string" ? input.prompt.trim() : "";
  const systemInstruction = typeof input?.systemInstruction === "string"
    ? input.systemInstruction.trim()
    : "";
  const modelName = typeof input?.modelName === "string" && input.modelName.trim()
    ? input.modelName.trim()
    : "gemini-2.5-flash";
  const responseMimeType = input?.responseMimeType === "text/plain"
    ? "text/plain"
    : "application/json";

  if (!prompt || prompt.length > 80000 || systemInstruction.length > 20000) {
    const error = new Error("Prompt is empty or exceeds the permitted size.");
    error.code = "invalid-argument";
    throw error;
  }
  if (!/^gemini-[A-Za-z0-9.-]{2,64}$/.test(modelName)) {
    const error = new Error("Requested Gemini model identifier is invalid.");
    error.code = "invalid-argument";
    throw error;
  }
  if (typeof fetchImpl !== "function") {
    const error = new Error("Server fetch implementation is unavailable.");
    error.code = "internal";
    throw error;
  }

  const body = {
    contents: [{ role: "user", parts: [{ text: prompt }] }],
    generationConfig: {
      responseMimeType,
      temperature: 0.2,
      maxOutputTokens: 8192
    }
  };
  if (systemInstruction) {
    body.systemInstruction = { parts: [{ text: systemInstruction }] };
  }

  let response;
  try {
    response = await fetchImpl(
      GEMINI_MODELS_ENDPOINT + "/" + modelName + ":generateContent",
      {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "x-goog-api-key": apiKey.trim()
        },
        body: JSON.stringify(body),
        signal: AbortSignal.timeout(90000)
      }
    );
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
        : status === 404
          ? "The configured Gemini model is unavailable for this API key."
          : status === 429
            ? "Gemini provider rate limit reached."
            : "Gemini generation request failed."
    );
    error.code = status === 429 ? "resource-exhausted" :
      status === 401 || status === 403 ? "permission-denied" :
        status === 404 ? "not-found" : "unavailable";
    throw error;
  }

  let payload;
  try {
    payload = await response.json();
  } catch (_) {
    const error = new Error("Gemini returned an unreadable generation response.");
    error.code = "data-loss";
    throw error;
  }

  const candidate = payload?.candidates?.[0];
  const parts = candidate?.content?.parts;
  const responseText = Array.isArray(parts)
    ? parts.map(part => typeof part?.text === "string" ? part.text : "").join("").trim()
    : "";
  if (!responseText || responseText.length > 1000000) {
    const error = new Error(
      candidate?.finishReason
        ? "Gemini did not return usable content. Finish reason: " + candidate.finishReason
        : "Gemini returned no usable content."
    );
    error.code = "failed-precondition";
    throw error;
  }

  const usage = payload.usageMetadata || {};
  return {
    responseText,
    modelVersion: typeof payload.modelVersion === "string" ? payload.modelVersion : modelName,
    usageMetadata: {
      promptTokenCount: Number(usage.promptTokenCount) || 0,
      candidatesTokenCount: Number(usage.candidatesTokenCount) || 0,
      totalTokenCount: Number(usage.totalTokenCount) || 0
    }
  };
}

exports.generateAdminGeminiContent = onCall(
  { secrets: [adminGeminiApiKey], timeoutSeconds: 120, memory: "512MiB" },
  async request => {
    if (!request.auth || !request.auth.uid) {
      throw new HttpsError("unauthenticated", "Sign in is required.");
    }
    if (!isVerifiedAdmin(request.auth)) {
      throw new HttpsError("permission-denied", "Only a verified Admin can use the central Gemini connector.");
    }

    const data = request.data || {};
    try {
      const result = await generateGeminiContent(adminGeminiApiKey.value(), {
        prompt: data.prompt,
        systemInstruction: data.systemInstruction,
        modelName: data.modelName,
        responseMimeType: data.responseMimeType
      });
      return { ...result, provider: "gemini" };
    } catch (error) {
      const allowedCodes = new Set([
        "invalid-argument", "failed-precondition", "unavailable",
        "resource-exhausted", "permission-denied", "not-found",
        "data-loss", "internal"
      ]);
      throw new HttpsError(
        allowedCodes.has(error.code) ? error.code : "internal",
        error.message || "Gemini generation failed."
      );
    }
  }
);

exports._test = { isVerifiedAdmin, verifyGeminiApiKey, generateGeminiContent };
