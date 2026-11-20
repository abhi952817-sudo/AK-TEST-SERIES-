/**
 * Shared Authorization Helper.
 * Administrative authority is held by one verified primary owner account only.
 */
const PRIMARY_ADMIN_EMAIL = "abhi952817@gmail.com";

function isPrimaryAdminToken(token) {
  return Boolean(
    token &&
    typeof token.email === "string" &&
    token.email.trim().toLowerCase() === PRIMARY_ADMIN_EMAIL &&
    token.email_verified === true
  );
}

/**
 * Only the verified primary owner may bootstrap/refresh their own admin claim.
 * An admin claim on any other account is deliberately insufficient.
 */
function validateSetAdminRoleAuth(context) {
  if (!context || !context.auth || !context.auth.uid) {
    const err = new Error("User must be authenticated to manage administrator roles.");
    err.code = "unauthenticated";
    throw err;
  }

  if (!isPrimaryAdminToken(context.auth.token)) {
    const err = new Error("Only the verified primary owner can manage the single administrator account.");
    err.code = "permission-denied";
    throw err;
  }
  return true;
}

/**
 * Resolves setAdminRole to the caller's own UID. Promotion by email or to
 * another UID is rejected, and the sole primary admin cannot self-demote
 * through this bootstrap callable.
 */
function resolveAdminRoleTarget(context, data) {
  if (!context || !context.auth || !context.auth.uid) {
    const err = new Error("User must be authenticated.");
    err.code = "unauthenticated";
    throw err;
  }

  const payload = data && typeof data === "object" && !Array.isArray(data) ? data : {};
  if (Object.prototype.hasOwnProperty.call(payload, "email")) {
    const err = new Error("Email-based administrator promotion is disabled.");
    err.code = "invalid-argument";
    throw err;
  }
  if (payload.targetUid != null && payload.targetUid !== context.auth.uid) {
    const err = new Error("The single administrator may only provision their own account.");
    err.code = "permission-denied";
    throw err;
  }
  if (payload.isAdmin === false) {
    const err = new Error("The primary administrator cannot be demoted through this callable.");
    err.code = "permission-denied";
    throw err;
  }
  if (payload.isAdmin != null && payload.isAdmin !== true) {
    const err = new Error("isAdmin must be true when supplied.");
    err.code = "invalid-argument";
    throw err;
  }
  return context.auth.uid;
}

/**
 * Asserts that the authenticated caller has an active, non-suspended account status.
 */
function assertActiveUserStatus(userData, userId) {
  if (!userData) {
    const err = new Error("User record does not exist or account status could not be verified.");
    err.code = "not-found";
    throw err;
  }
  if (userData.isBanned === true) {
    const err = new Error("Account has been suspended by administration. Access denied.");
    err.code = "permission-denied";
    throw err;
  }
  return true;
}

module.exports = {
  PRIMARY_ADMIN_EMAIL,
  isPrimaryAdminToken,
  validateSetAdminRoleAuth,
  resolveAdminRoleTarget,
  assertActiveUserStatus,
};
