"use strict";

/**
 * Legacy scoring is a compatibility exception, never a default.
 * The allowlist is stored in server-owned Firestore Config and is intentionally
 * independent of client-editable test metadata.
 *
 * Schema:
 * {
 *   schemaVersion: 1,
 *   status: "ACTIVE",
 *   testIds: ["immutable-historical-test-id"]
 * }
 */
function isLegacyScoringApproved(testId, registry) {
  if (typeof testId !== "string" || testId.trim().length === 0) return false;
  if (!registry || typeof registry !== "object" || Array.isArray(registry)) return false;
  if (registry.schemaVersion !== 1 || registry.status !== "ACTIVE") return false;
  if (!Array.isArray(registry.testIds) || registry.testIds.length === 0) return false;
  if (registry.testIds.length > 10000) return false;

  const normalizedId = testId.trim();
  return registry.testIds.some((candidate) =>
    typeof candidate === "string" &&
    candidate.trim().length > 0 &&
    candidate.trim() === normalizedId
  );
}

module.exports = { isLegacyScoringApproved };
