/**
 * Canonical least-privilege Admin role policy.
 *
 * IMPORTANT: This module defines the policy only. Callables, Firestore Rules,
 * Storage Rules, and Android route guards must all enforce the same permissions
 * before this policy can be considered active in production.
 */
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

module.exports = {
  ADMIN_ROLES,
  ADMIN_PERMISSIONS,
  ROLE_PERMISSIONS,
  normalizeAdminRole,
  isKnownAdminRole,
  hasAdminPermission,
  getAdminPermissions,
};
