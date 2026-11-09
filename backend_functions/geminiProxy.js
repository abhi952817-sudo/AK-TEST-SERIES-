const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { defineSecret } = require("firebase-functions/params");
const admin = require("firebase-admin");

const ownerGeminiKey = defineSecret("GEMINI_API_KEY");
const ALLOWED_MODELS = new Set(["gemini-2.5-flash", "gemini-2.5-pro", "gemini-flash-latest"]);
const ALLOWED_TASKS = new Set([
  "question_generation",
  "question_extraction",
  "document_classification",
  "syllabus_mapping",
  "answer_verification",
  "quality_review",
  "connection_test"
]);

function isOwnerAdmin(auth) {
  const token = auth && auth.token ? auth.token : {};
  return token.admin === true ||
    (token.email === "abhi952817@gmail.com" && token.email_verified === true);
}

async function enforceUserRateLimit(uid) {
  const db = admin.firestore();
  const now = Date.now();
  const minuteBucket = Math.floor(now / 60000);
  const ref = db.collection("GeminiRequestLimits").doc(uid);
  await db.runTransaction(async (tx) => {
    const snap = await tx.get(ref);
    const previous = snap.exists ? snap.data() : {};
    const count = previous.minuteBucket === minuteBucket ? Number(previous.minuteCount || 0) : 0;
    if (count >= 30) {
      throw new HttpsError("resource-exhausted", "Gemini request rate limit reached. Please retry shortly.");
    }
    tx.set(ref, {
      minuteBucket,
      minuteCount: count + 1,
      updatedAt: admin.firestore.FieldValue.serverTimestamp()
    }, { merge: true });
  });
}

module.exports = onCall(
  {
    secrets: [ownerGeminiKey],
    timeoutSeconds: 120,
    memory: "512MiB"
  },
  async (request) => {
    if (!request.auth || !request.auth.uid) {
      throw new HttpsError("unauthenticated", "Sign in before using Gemini processing.");
    }

    const data = request.data || {};
    const credentialMode = data.credentialMode === "USER_BYOK" ? "USER_BYOK" : "OWNER_MANAGED";
    const taskType = String(data.taskType || "");
    const modelName = String(data.modelName || "gemini-2.5-flash");
    const prompt = typeof data.prompt === "string" ? data.prompt : "";
    const systemInstruction = typeof data.systemInstruction === "string" ? data.systemInstruction : "";
    const responseSchema = data.responseSchema && typeof data.responseSchema === "object"
      ? data.responseSchema
      : null;

    if (!ALLOWED_TASKS.has(taskType)) {
      throw new HttpsError("invalid-argument", "Unsupported Gemini task type.");
    }
    if (!ALLOWED_MODELS.has(modelName)) {
      throw new HttpsError("invalid-argument", "Requested Gemini model is not allowlisted.");
    }
    if (!prompt.trim() || prompt.length > 120000 || systemInstruction.length > 30000) {
      throw new HttpsError("invalid-argument", "Gemini prompt is empty or exceeds the allowed size.");
    }
    if (responseSchema && JSON.stringify(responseSchema).length > 20000) {
      throw new HttpsError("invalid-argument", "Response schema exceeds the allowed size.");
    }

    await enforceUserRateLimit(request.auth.uid);

    let apiKey;
    if (credentialMode === "USER_BYOK") {
      apiKey = typeof data.apiKey === "string" ? data.apiKey.trim() : "";
      if (apiKey.length < 20 || apiKey.length > 256) {
        throw new HttpsError("invalid-argument", "A valid personal Gemini API key is required.");
      }
    } else {
      if (!isOwnerAdmin(request.auth)) {
        throw new HttpsError("permission-denied", "Owner-managed Gemini is available to authorized Admins only.");
      }
      apiKey = ownerGeminiKey.value();
      if (!apiKey) {
        throw new HttpsError("failed-precondition", "Owner Gemini secret is not configured on the backend.");
      }
    }

    const generationConfig = { responseMimeType: "application/json" };
    if (responseSchema) generationConfig.responseSchema = responseSchema;

    const body = {
      contents: [{ role: "user", parts: [{ text: prompt }] }],
      generationConfig
    };
    if (systemInstruction.trim()) {
      body.systemInstruction = { parts: [{ text: systemInstruction }] };
    }

    let response;
    try {
      response = await fetch(
        "https://generativelanguage.googleapis.com/v1beta/models/" + modelName + ":generateContent",
        {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            "x-goog-api-key": apiKey
          },
          body: JSON.stringify(body),
          signal: AbortSignal.timeout(100000)
        }
      );
    } catch (_) {
      throw new HttpsError("unavailable", "Gemini service could not be reached. Please retry.");
    } finally {
      // Do not retain credentials in module state, logs, or Firestore.
      apiKey = null;
    }

    let payload;
    try {
      payload = await response.json();
    } catch (_) {
      throw new HttpsError("internal", "Gemini returned an unreadable response.");
    }

    if (!response.ok) {
      const status = response.status;
      if (status === 401 || status === 403) {
        throw new HttpsError("unauthenticated", "Gemini rejected the selected API key. Check the key in Google AI Studio.");
      }
      if (status === 429) {
        throw new HttpsError("resource-exhausted", "Gemini quota or rate limit reached.");
      }
      if (status === 400) {
        throw new HttpsError("invalid-argument", "Gemini rejected the request format or selected model.");
      }
      throw new HttpsError("unavailable", "Gemini request failed with HTTP " + status + ".");
    }

    const candidate = payload.candidates && payload.candidates[0];
    const parts = candidate && candidate.content && candidate.content.parts;
    const responseText = Array.isArray(parts)
      ? parts.map((part) => typeof part.text === "string" ? part.text : "").join("")
      : "";
    if (!responseText.trim()) {
      throw new HttpsError("failed-precondition", "Gemini returned no usable text. The response may have been blocked.");
    }

    return {
      responseText,
      modelName,
      modelVersion: String(payload.modelVersion || "UNAVAILABLE"),
      credentialMode,
      taskType
    };
  }
);
