/**
 * AK Test Series - Super Admin Custom Claim Provisioning Script
 * 
 * Safely grants the `{ admin: true }` Firebase Auth custom claim to the designated
 * owner email account: abhi952817@gmail.com.
 *
 * Usage:
 *   node scripts/bootstrap_superadmin.js [email]
 */

const admin = require("firebase-admin");

const targetEmail = process.argv[2] || "abhi952817@gmail.com";

// Initialize Firebase Admin SDK using Application Default Credentials (ADC) or service account
if (!admin.apps.length) {
  try {
    admin.initializeApp();
  } catch (e) {
    console.error("Firebase Admin initialization error:", e.message);
  }
}

async function grantSuperAdmin(email) {
  console.log(`[AK Test Series] Initiating Super Admin claim check for: ${email}`);
  try {
    const user = await admin.auth().getUserByEmail(email);
    console.log(`[AK Test Series] Found user UID: ${user.uid} for email: ${user.email}`);

    // Set custom claim { admin: true }
    await admin.auth().setCustomUserClaims(user.uid, {
      admin: true,
      superAdmin: true,
      role: "admin",
      authorizedAt: Date.now()
    });

    console.log(`[AK Test Series] SUCCESS: Custom claim { admin: true } stamped on UID: ${user.uid}`);
    console.log(`[AK Test Series] User ${email} is now verified as Super Admin.`);
  } catch (error) {
    if (error.code === 'auth/user-not-found') {
      console.log(`[AK Test Series] Note: User ${email} has not logged in yet. The claim will be automatically provisioned via setAdminRole Cloud Function upon first Google Sign-In.`);
    } else {
      console.error(`[AK Test Series] Error assigning claim:`, error.message);
    }
  }
}

grantSuperAdmin(targetEmail);
