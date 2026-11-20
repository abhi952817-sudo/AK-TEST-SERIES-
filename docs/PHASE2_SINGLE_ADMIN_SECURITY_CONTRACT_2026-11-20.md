# Phase 2 — Single Administrator and Security Contract
Date: 2026-11-20
Branch: `feature/single-admin-security-2026-11-20`
Base: `feature/community-server-authoritative-share-2026-11-20`

## Single administrator policy
- The only administrator identity is the verified primary owner account already designated by the repository's authorization contract.
- A legacy `admin: true` or `superAdmin: true` custom claim on another account does not grant backend, Firestore, or Storage administrator access.
- `setAdminRole` may provision/refresh claims only for the authenticated caller's own UID after the caller token and the server-side Firebase Auth user record both prove the primary owner email is verified.
- Promotion by another UID or email is rejected. Self-demotion through this bootstrap callable is rejected to avoid accidental lockout.
- The bootstrap script requires an explicit owner UID and a confirmation environment variable. It verifies the UID's email and verification state and preserves unrelated custom claims.
- No bootstrap script is executed by CI or by this change.

## Enforcement layers
- Cloud Functions use the shared `isPrimaryAdminToken` helper for privileged callable checks.
- Firestore and Storage Rules authorize only the verified primary owner identity, not arbitrary legacy admin claims.
- Android admin UI checks use the verified primary owner identity. This is UX gating only; backend and Firebase Rules remain authoritative.
- Ordinary student profile updates remain restricted to the existing safe field allowlist.

## Required verification
1. Run backend syntax checks and all backend unit tests.
2. Run Firestore and Storage Rules emulator tests, including a legacy admin-claim non-owner denial.
3. Run Android unit tests and Debug APK build.
4. Staging-only owner/student/admin-claim test matrix before deployment.
5. Inspect existing Firebase Auth users for legacy admin claims before rollout. Do not bulk revoke claims or mutate production accounts without explicit approval.

## Explicitly not done
- No Firebase Auth custom claim was set or revoked.
- No production Firestore or Storage Rules were deployed.
- No production user records or data were changed.
- No secrets, Firebase project settings, or database IDs were changed.
