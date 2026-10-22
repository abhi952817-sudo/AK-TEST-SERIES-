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

module.exports = {
  validateSetAdminRoleAuth,
};
