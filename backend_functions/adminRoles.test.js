const { test } = require("node:test");
const assert = require("node:assert/strict");
const {
  ADMIN_ROLES,
  ADMIN_PERMISSIONS,
  isKnownAdminRole,
  hasAdminPermission,
  getAdminPermissions,
  getAdminRoleFromClaims,
  hasAdminPermissionFromClaims,
  buildAdminCustomClaims,
} = require("./authHelper");

test("only the three canonical Admin roles are recognized", () => {
  assert.equal(isKnownAdminRole(ADMIN_ROLES.SUPER_ADMIN), true);
  assert.equal(isKnownAdminRole(ADMIN_ROLES.CONTENT_ADMIN), true);
  assert.equal(isKnownAdminRole(ADMIN_ROLES.SUPPORT_ADMIN), true);
  assert.equal(isKnownAdminRole("admin"), false);
  assert.equal(isKnownAdminRole("owner"), false);
  assert.equal(isKnownAdminRole(""), false);
  assert.equal(isKnownAdminRole(null), false);
});

test("Super Admin receives the complete permission set", () => {
  assert.deepEqual(
    getAdminPermissions(ADMIN_ROLES.SUPER_ADMIN).sort(),
    Object.values(ADMIN_PERMISSIONS).sort()
  );
});

test("Content Admin can process and publish content but cannot administer platform security", () => {
  const role = ADMIN_ROLES.CONTENT_ADMIN;
  assert.equal(hasAdminPermission(role, ADMIN_PERMISSIONS.PDF_UPLOAD), true);
  assert.equal(hasAdminPermission(role, ADMIN_PERMISSIONS.PDF_PROCESS), true);
  assert.equal(hasAdminPermission(role, ADMIN_PERMISSIONS.QUESTION_REVIEW), true);
  assert.equal(hasAdminPermission(role, ADMIN_PERMISSIONS.TEST_PUBLISH), true);
  assert.equal(hasAdminPermission(role, ADMIN_PERMISSIONS.STUDENT_READ), false);
  assert.equal(hasAdminPermission(role, ADMIN_PERMISSIONS.ADMIN_ROLES_MANAGE), false);
  assert.equal(hasAdminPermission(role, ADMIN_PERMISSIONS.STORAGE_MANAGE), false);
});

test("Support Admin can support students but cannot access content or answer-key workflows", () => {
  const role = ADMIN_ROLES.SUPPORT_ADMIN;
  assert.equal(hasAdminPermission(role, ADMIN_PERMISSIONS.STUDENT_READ), true);
  assert.equal(hasAdminPermission(role, ADMIN_PERMISSIONS.STUDENT_SUPPORT), true);
  assert.equal(hasAdminPermission(role, ADMIN_PERMISSIONS.CRITICAL_ALERTS_READ), true);
  assert.equal(hasAdminPermission(role, ADMIN_PERMISSIONS.PDF_PROCESS), false);
  assert.equal(hasAdminPermission(role, ADMIN_PERMISSIONS.QUESTION_REVIEW), false);
  assert.equal(hasAdminPermission(role, ADMIN_PERMISSIONS.TEST_PUBLISH), false);
  assert.equal(hasAdminPermission(role, ADMIN_PERMISSIONS.ADMIN_ROLES_MANAGE), false);
});

test("unknown roles and malformed permissions fail closed", () => {
  assert.equal(hasAdminPermission("legacy_admin", ADMIN_PERMISSIONS.STUDENT_READ), false);
  assert.equal(hasAdminPermission(ADMIN_ROLES.SUPPORT_ADMIN, null), false);
  assert.deepEqual(getAdminPermissions("unknown"), []);
});


test("role claims preserve unrelated claims and assign only canonical roles", () => {
  const claims = buildAdminCustomClaims(
    { premium: true, locale: "hi" },
    ADMIN_ROLES.CONTENT_ADMIN
  );
  assert.deepEqual(claims, {
    premium: true,
    locale: "hi",
    adminRole: ADMIN_ROLES.CONTENT_ADMIN,
    admin: false,
  });
  assert.equal(getAdminRoleFromClaims(claims), ADMIN_ROLES.CONTENT_ADMIN);
  assert.equal(hasAdminPermissionFromClaims(claims, ADMIN_PERMISSIONS.QUESTION_REVIEW), true);
  assert.equal(hasAdminPermissionFromClaims(claims, ADMIN_PERMISSIONS.ADMIN_ROLES_MANAGE), false);
});

test("legacy admin claim remains mapped to Super Admin during migration", () => {
  assert.equal(getAdminRoleFromClaims({ admin: true }), ADMIN_ROLES.SUPER_ADMIN);
  assert.equal(
    hasAdminPermissionFromClaims({ admin: true }, ADMIN_PERMISSIONS.ADMIN_ROLES_MANAGE),
    true
  );
});

test("revocation removes only administrative claims and preserves unrelated claims", () => {
  assert.deepEqual(
    buildAdminCustomClaims(
      { admin: true, adminRole: ADMIN_ROLES.SUPER_ADMIN, premium: true },
      null
    ),
    { premium: true }
  );
});

test("invalid role assignment is rejected", () => {
  assert.throws(
    () => buildAdminCustomClaims({ premium: true }, "owner"),
    (error) => error.code === "invalid-argument"
  );
});


test("solution-review permission is available to Super Admin and Content Admin, never Support Admin", () => {
  assert.equal(
    hasAdminPermissionFromClaims({ adminRole: ADMIN_ROLES.SUPER_ADMIN }, ADMIN_PERMISSIONS.QUESTION_REVIEW),
    true
  );
  assert.equal(
    hasAdminPermissionFromClaims({ adminRole: ADMIN_ROLES.CONTENT_ADMIN }, ADMIN_PERMISSIONS.QUESTION_REVIEW),
    true
  );
  assert.equal(
    hasAdminPermissionFromClaims({ adminRole: ADMIN_ROLES.SUPPORT_ADMIN }, ADMIN_PERMISSIONS.QUESTION_REVIEW),
    false
  );
  assert.equal(
    hasAdminPermissionFromClaims({ adminRole: ADMIN_ROLES.SUPPORT_ADMIN }, ADMIN_PERMISSIONS.TEST_PUBLISH),
    false
  );
});
