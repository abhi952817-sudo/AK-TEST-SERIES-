# Default Firestore Consolidation Audit

**Date:** 2026-11-15  
**Repository:** `abhi952817-sudo/AK-TEST-SERIES-`  
**Audit branch:** `audit/default-firestore-consolidation-2026-11-15`  
**Base branch:** `main`  
**Base commit:** `a857d6ba0e21c9c37f10c26700e925cb1afaa3e8`

## Scope and safety

This document records a code-level audit snapshot. It does not represent a live Firebase Console inventory. No Firestore documents, databases, Storage objects, deployed Functions, Rules, or production configuration have been changed by this audit.

No database deletion, data migration, deployment, PR merge, or production write is authorized by this work. Database retirement must remain blocked until a verified backup, data inventory, migration dry-run, and explicit approval exist.

## 1. Database ID findings on `main`

The following IDs were found in the inspected configuration/source files:

| Location | Database selection on main | Finding |
|---|---|---|
| `app/src/main/res/values/firebase_applet_config.xml` | `ai-studio-android-aktestse-9fa19369-81c5-4d42-bf79-ab4f9f40d4bf` | Android resource selects named database |
| `app/src/main/java/Com/ak/testseries/data/remote/FirestoreHelper.kt` | Same `9fa...` ID as fallback | Android helper selects named DB; on initialization error it silently falls back to default |
| `firebase.json` | Same `9fa...` ID | Firestore rules/index deployment target is named DB |
| `backend_functions/index.js` | `ai-studio-android-aktestse-4a0dbfd1-96e9-481f-b66c-2984491d0612` | Admin SDK selects a different named database |
| `admin.firestore(FIRESTORE_DATABASE_ID)` in backend | Uses the `4a...` ID above | Backend and Android are not aligned on main |

Additional IDs supplied/reported during the project discussion, not confirmed by this repository snapshot:

- `(default)`: user-selected intended production target.
- `ai-studio-android-aktestse-b6ff3593-06aa-4623-ade3-3201b7fad5c9`: reported as another Firebase database ID.
- `ai-studio-android-aktestse-4a0dbfd1-96e9-481f-b66c-2984491d0612`: present in backend source, but user-reported Firebase Console listing did not show it.
- `ai-studio-android-aktestse-9fa19369-81c5-4d42-bf79-ab4f9f40d4bf`: present in Android and firebase.json source; user-reported Console listing showed it in Singapore.

The IDs' existence, contents, region, edition, billing state, and whether they are empty must be verified in Firebase Console or through authorized Firebase Admin access. Source code alone cannot establish live database contents. The third ID `b6...` was not found anywhere in the current `main` code-search results; it may still exist in Firebase without being referenced by this repository.

## 2. Confirmed code-level risks

1. Android on `main` targets `9fa...`, while Cloud Functions target `4a...`. This can split client and privileged server data across different databases.
2. Android `FirestoreHelper` catches initialization errors and falls back to the default database. This can silently route traffic to a different database than configured.
3. `firebase.json` targets `9fa...` for Firestore Rules and indexes. A future deploy without correcting this target could deploy Rules/indexes to the wrong database.
4. A single `admin.firestore(FIRESTORE_DATABASE_ID)` instance is used by backend functions in `index.js`; all exports and their collection operations must be inventoried before changing this selection.
5. Storage is configured separately in `firebase.json`; changing the Firestore database ID does not copy, move, or synchronize Storage files or their metadata.\n6. PR #10's default-database change is directionally correct for centralization, but it is not yet merged, deployed, or sufficient by itself to finish the direct-call and feature dependency audit.

## 3. Additional direct Firestore access findings

A GitHub code search on `main` found 7 files containing direct `FirebaseFirestore.getInstance()` calls. These calls bypass the centralized `FirestoreHelper` and, unless explicitly configured elsewhere, use the Firebase default database rather than the named database configured in the Android resource:

| File | Direct call sites found | Initial risk |
|---|---:|---|
| `app/src/main/java/Com/ak/testseries/data/analytics/AnalyticsManager.kt` | 1 | Analytics writes may already target default |
| `app/src/main/java/Com/ak/testseries/AdManager.kt` | 1 | Ad-related reads/writes may target default |
| `app/src/main/java/Com/ak/testseries/ui/CommunityUI.kt` | 10 | Community data may target default independently of FirestoreHelper |
| `app/src/main/java/Com/ak/testseries/ui/CommunityAdminUI.kt` | 2 | Moderation/admin operations may target default |
| `app/src/main/java/Com/ak/testseries/ui/AdminPanelScreen.kt` | 2 | Admin panel operations may target default |
| `app/src/main/java/Com/ak/testseries/ui/TestReviewScreen.kt` | 1 | Test review may target default; PR #10 modifies this file, so its branch version must be checked |

This is a high-priority finding: the app may already be split across the named `9fa...` database and `(default)`, even before any migration. Every call site must be traced to its collection and purpose before centralizing it. The PR #10 patch was also inspected: it changes `FirestoreHelper` to fail closed on non-default configuration and changes the TestReview upload path, but it does not remove all direct Firestore calls from the other five UI/analytics/ad files listed above. These remaining direct calls need individual review; they are not automatically proof of wrong-database access because no-argument SDK calls resolve to the default database.

### Cloud Function exports found in `backend_functions/index.js`

The source contains these exported functions:

- `normalizeExamKey`
- `purgeRetiredDemoTests`
- `submitTestAttemptServerSide`
- `getUserRankingDetails`
- `setAdminRole`
- `getTestAttemptSolutions`

The backend creates one shared database handle with `admin.firestore(FIRESTORE_DATABASE_ID)`. All exported functions and their nested helper paths must be reviewed for database usage and side effects. `normalizeExamKey` is a plain exported helper, not an HTTPS callable.

## 4. PR review state at audit start

- PR #10, `fix/use-default-firestore-everywhere`: Open, Draft, Unmerged; head `faa5fb9d033d3bb6bd060b56d054445127a60a60`; base `main` at `a857d6ba0e21c9c37f10c26700e925cb1afaa3e8`.
- PR #8, `feature/community-completion-2026-11-13`: Open, Draft, Unmerged; head `ed032e111d375813fa90fde9b2486ad635f1e3fd`; base `main` at `1e236d099522d63ec89ab0d11287b48315efc48c`.

These PRs are separate workstreams. This audit branch was created from `main` and does not merge or modify either PR.

## 5. Repository inventory snapshot

The recursive Git tree for `main` contained 299 entries (including directories). Firebase-relevant files identified include:

- `firebase.json`
- `firestore.rules`
- `firestore.indexes.json`
- `firestore.test.js`
- `storage.rules`
- `storage.test.js`
- `backend_functions/index.js`
- `backend_functions/authHelper.js`
- `backend_functions/examScoring.js`
- Android Firestore helper, remote data source, repositories, auth manager, upload and backup code
- GitHub Actions workflows `.github/workflows/android.yml` and `.github/workflows/community-rules.yml`

This is an initial path inventory, not yet a complete call-site or feature dependency map.

## 6. Required next audit steps

### Part A — exhaustive ID and SDK call-site search
Search all tracked source/config/docs for:
- all known database IDs and `(default)`
- `FirebaseFirestore.getInstance`
- `admin.firestore`, `getFirestore`, `initializeApp`
- `collection(`, `doc(`, transactions, batches, triggers and scheduled functions
- emulator and deployment target configuration

### Part B — Cloud Functions inventory
For every exported Function record:
- trigger type and authentication/authorization
- Firestore database instance used
- collections/documents read and written
- Storage/Auth/Secrets dependencies
- client callers and expected request/response schema
- tests and deployment status evidence

### Part C — Android dependency inventory
Trace every Firestore read/write from UI/ViewModel through repository/data source/helper. Record offline cache, retry, fallback and error behavior.

### Part D — Collections, Rules, indexes and Storage
Build a collection/schema map, match each access path to Rules and indexes, and separately map Storage paths and metadata references.

### Part E — Feature/plugin mapping
Map tests, ranking, community, notifications, Gemini/Groq/Cerebras connectors, PDF pipeline, admin controls, quotas and backups to their backend and data dependencies.

### Part F — Migration and retirement readiness
Do not migrate or delete until:
- live source and destination inventories are captured;
- a server-side export/backup is verified;
- references and document IDs are mapped;
- dry-run results and counts reconcile;
- rollback is documented and tested;
- user explicitly approves the production operation.

## 7. Change log

| Date | Branch | Change | Production impact |
|---|---|---|---|
| 2026-11-15 | `audit/default-firestore-consolidation-2026-11-15` | Added initial inventory, complete Android Firestore call-site review, routing split, path/rules risks and backend exports | None; documentation only |

## 8. Current status

**Audit in progress.** Complete contents of all identified Android Firestore call-site files and backend index/auth helper were reviewed. Main has confirmed direct-default versus helper-named routing split. The next task is full backend callable, Rules, index and feature-dependency reconciliation. No live Firebase state has been inspected. No database has been removed, migrated, or modified.
