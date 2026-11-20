/**
 * One-time, single-owner Firebase Admin claim bootstrap.
 *
 * Required environment:
 *   AK_SUPER_ADMIN_UID=<Firebase Auth UID of the primary owner>
 *   CONFIRM_SINGLE_ADMIN_BOOTSTRAP=YES
 *
 * This script never looks up or promotes an arbitrary email/UID. It verifies
 * the configured UID against the one approved, verified owner identity and
 * preserves unrelated existing custom claims.
 */
const admin = require("firebase-admin");

const PRIMARY_ADMIN_EMAIL = "abhi952817@gmail.com";
const ownerUid = process.env.AK_SUPER_ADMIN_UID;
const confirmed = process.env.CONFIRM_SINGLE_ADMIN_BOOTSTRAP === "YES";

async function main() {
  if (!ownerUid || !ownerUid.trim()) {
    throw new Error("Set AK_SUPER_ADMIN_UID to the primary owner's Firebase Auth UID.");
  }
  if (!confirmed) {
    throw new Error("Set CONFIRM_SINGLE_ADMIN_BOOTSTRAP=YES after verifying the Firebase project and owner UID.");
  }

  if (!admin.apps.length) {
    admin.initializeApp();
  }

  const user = await admin.auth().getUser(ownerUid.trim());
  if (
    typeof user.email !== "string" ||
    user.email.trim().toLowerCase() !== PRIMARY_ADMIN_EMAIL ||
    user.emailVerified !== true
  ) {
    throw new Error("Refusing bootstrap: UID must belong to the verified primary owner account.");
  }

  const existingClaims = user.customClaims || {};
  await admin.auth().setCustomUserClaims(user.uid, {
    ...existingClaims,
    admin: true,
    superAdmin: true,
    role: "admin"
  });

  console.log("[AK Test Series] Single primary administrator claim provisioned successfully.");
}

main().catch((error) => {
  console.error("[AK Test Series] Admin bootstrap failed:", error.message);
  process.exitCode = 1;
});
