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

module.exports = {
  validateSetAdminRoleAuth,
  assertActiveUserStatus,
};
