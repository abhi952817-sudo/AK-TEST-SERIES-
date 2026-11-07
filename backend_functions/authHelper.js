/**
 * Shared Authorization Helper
 * Enforces strict authorization policies across production callables and unit test suites.
 */

/**
 * Validates caller authorization to assign or modify administrative roles.
 * Restricted to:
 * 1) Callers with custom claim `admin: true`
 * 2) Primary verified owner `abhi952817@gmail.com` with `email_verified: true`
 *
 * Throws structured Error with `.code` matching Firebase Functions HttpsError codes:
 * - "unauthenticated": When caller is unauthenticated
 * - "permission-denied": When authenticated caller lacks required privilege
 *
 * @param {object} context Firebase Functions callable context
 * @returns {boolean} true if caller is authorized
 */
function validateSetAdminRoleAuth(context) {
  if (!context || !context.auth || !context.auth.uid) {
    const err = new Error("User must be authenticated to manage administrator roles.");
    err.code = "unauthenticated";
    throw err;
  }

  const isCallerAdmin = context.auth.token && context.auth.token.admin === true;
  const isOwnerEmail = context.auth.token && context.auth.token.email === "abhi952817@gmail.com" && context.auth.token.email_verified === true;

  if (!isCallerAdmin && !isOwnerEmail) {
    const err = new Error("Only authorized super administrators with verified credentials can assign admin roles.");
    err.code = "permission-denied";
    throw err;
  }
  return true;
}

/**
 * Asserts that the authenticated caller has an active, non-suspended account status.
 *
 * Rules:
 * - If user record does not exist -> fails closed (code 'not-found').
 * - If user record indicates isBanned === true -> fails closed (code 'permission-denied').
 * - Any status verification failure fails closed.
 *
 * @param {object|null|undefined} userData The authoritative snapshot data from Users_Data/{userId}
 * @param {string} userId The verified authenticated user ID
 * @returns {boolean} true if active
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

const ADMIN_ROLES = Object.freeze({
  SUPER_ADMIN: "super_admin",
  CONTENT_ADMIN: "content_admin",
  SUPPORT_ADMIN: "support_admin",
});

const ADMIN_PERMISSIONS = Object.freeze({
  STUDENT_READ: "students.read",
  STUDENT_SUPPORT: "students.support",
  PDF_UPLOAD: "pdf.upload",
  PDF_PROCESS: "pdf.process",
  QUESTION_REVIEW: "questions.review",
  TEST_PUBLISH: "tests.publish",
  DAILY_LIMIT_MANAGE: "limits.manage",
  PRIVATE_PUBLIC_MANAGE: "visibility.manage",
  AI_PROCESSING_MANAGE: "ai.manage",
  STORAGE_MANAGE: "storage.manage",
  CRITICAL_ALERTS_READ: "alerts.read",
  ADMIN_ROLES_MANAGE: "admin_roles.manage",
});

const ROLE_PERMISSIONS = Object.freeze({
  [ADMIN_ROLES.SUPER_ADMIN]: Object.freeze(Object.values(ADMIN_PERMISSIONS)),
  [ADMIN_ROLES.CONTENT_ADMIN]: Object.freeze([
    ADMIN_PERMISSIONS.PDF_UPLOAD,
    ADMIN_PERMISSIONS.PDF_PROCESS,
    ADMIN_PERMISSIONS.QUESTION_REVIEW,
    ADMIN_PERMISSIONS.TEST_PUBLISH,
  ]),
  [ADMIN_ROLES.SUPPORT_ADMIN]: Object.freeze([
    ADMIN_PERMISSIONS.STUDENT_READ,
    ADMIN_PERMISSIONS.STUDENT_SUPPORT,
    ADMIN_PERMISSIONS.CRITICAL_ALERTS_READ,
  ]),
});

function normalizeAdminRole(role) {
  return typeof role === "string" ? role.trim().toLowerCase() : "";
}

function isKnownAdminRole(role) {
  return Object.prototype.hasOwnProperty.call(
    ROLE_PERMISSIONS,
    normalizeAdminRole(role)
  );
}

function hasAdminPermission(role, permission) {
  const normalizedRole = normalizeAdminRole(role);
  if (!isKnownAdminRole(normalizedRole) || typeof permission !== "string") {
    return false;
  }
  return ROLE_PERMISSIONS[normalizedRole].includes(permission);
}

function getAdminPermissions(role) {
  const normalizedRole = normalizeAdminRole(role);
  return isKnownAdminRole(normalizedRole)
    ? [...ROLE_PERMISSIONS[normalizedRole]]
    : [];
}

/**
 * Resolves a caller's canonical role from Firebase custom claims.
 * Legacy admin:true claims are temporarily treated as Super Admin so existing
 * deployments are not locked out during the controlled migration.
 */
function getAdminRoleFromClaims(claims) {
  if (!claims || typeof claims !== "object") return "";
  const explicitRole = normalizeAdminRole(claims.adminRole);
  if (isKnownAdminRole(explicitRole)) return explicitRole;
  if (claims.admin === true) return ADMIN_ROLES.SUPER_ADMIN;
  return "";
}

function hasAdminPermissionFromClaims(claims, permission) {
  return hasAdminPermission(getAdminRoleFromClaims(claims), permission);
}

/**
 * Builds a replacement custom-claims object without discarding unrelated claims.
 * requestedRole=null revokes administrative access; invalid roles fail closed.
 */
function buildAdminCustomClaims(existingClaims, requestedRole) {
  const claims = existingClaims && typeof existingClaims === "object"
    ? { ...existingClaims }
    : {};
  if (requestedRole === null || requestedRole === "") {
    delete claims.adminRole;
    delete claims.admin;
    return claims;
  }
  const role = normalizeAdminRole(requestedRole);
  if (!isKnownAdminRole(role)) {
    const err = new Error("A valid Admin role is required.");
    err.code = "invalid-argument";
    throw err;
  }
  claims.adminRole = role;
  // Preserve compatibility with existing app clients while role-aware
  // authorization is rolled out. This legacy flag is not the role policy.
  claims.admin = role === ADMIN_ROLES.SUPER_ADMIN;
  return claims;
}


module.exports = {
  validateSetAdminRoleAuth,
  assertActiveUserStatus,
  ADMIN_ROLES,
  ADMIN_PERMISSIONS,
  ROLE_PERMISSIONS,
  normalizeAdminRole,
  isKnownAdminRole,
  hasAdminPermission,
  getAdminPermissions,
  getAdminRoleFromClaims,
  hasAdminPermissionFromClaims,
  buildAdminCustomClaims,
};
