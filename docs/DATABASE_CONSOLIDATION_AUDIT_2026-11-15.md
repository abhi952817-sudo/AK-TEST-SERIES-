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
5. Storage is configured separately in `firebase.json`; changing the Firestore database ID does not copy, move, or synchronize Storage files or their metadata.
6. PR #10's default-database change is directionally correct for centralization, but it is not yet merged, deployed, or sufficient by itself to finish the direct-call and feature dependency audit.

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

## 4. Full Android Firestore call-site review (main snapshot)

Complete contents of the files below were fetched and reviewed. Line numbers refer to main commit `a857d6ba0e21c9c37f10c26700e925cb1afaa3e8`.

### Direct no-argument SDK calls

`FirebaseFirestore.getInstance()` without a database ID selects the project's **(default)** database. Main's `FirestoreHelper` selects named database `9fa...`. Active direct calls therefore bypass the helper and route to a different database.

| File and lines | Collection/path and operation | Status |
|---|---|---|
| `AnalyticsManager.kt:10,32,46,61,76` | `User_Analytics`; four event-write methods | Active direct default writes |
| `CommunityUI.kt:245-255` | `Users_Data/{uid}/Bookmarked_Posts`, `Liked_Posts`; snapshot listeners | Active direct default reads |
| `CommunityUI.kt:269-367` | `Community_Posts`; exam-filtered ordered feed and fallback listener/query | Active direct default reads |
| `CommunityUI.kt:377-388` | `Users_Data/{uid}/Bookmarked_Posts/{postId}`; create/delete | Active direct default writes |
| `CommunityUI.kt:392-404` | `Users_Data/{uid}/Liked_Posts/{postId}`; create/delete | Active direct default writes |
| `CommunityUI.kt:408-414` | `Community_Posts/{postId}/Comments/{commentId}`; create | Active direct default write |
| `CommunityUI.kt:416-432` | `Community_Posts/{postId}`; create/update | Active direct default writes |
| `CommunityUI.kt:434-436` | `Community_Posts/{postId}`; delete | Active direct default write |
| `CommunityUI.kt:1195-1205` | `Community_Posts/{postId}/Comments`; listener | Active direct default read |
| `CommunityUI.kt:1303-1305` | `Community_Posts/{postId}/Comments/{commentId}`; delete | Active direct default write |
| `CommunityAdminUI.kt:89-100,106-110` | `Config/community_settings`; read and merge write | Active direct default |
| `AdminPanelScreen.kt:379-383` | `UserNotifications`; broadcast create | Active direct default write |
| `AdminPanelScreen.kt:526-541` | `test_feedbacks`; latest feedback query | Active direct default read |
| `TestReviewScreen.kt:681-684` | Root `questions/{questionId}`; image URL update | Active direct default write; Rules path mismatch |
| `AdManager.kt:37-38` | `admin_settings/ad_units` | Commented/mock block, not active |

**Confirmed main-branch split:** repository/helper calls use named `9fa...`; active no-argument calls use `(default)`. This is source-code routing evidence, not proof of live collection contents.

### Central helper and callers

| File | Caller / behavior | Firestore paths |
|---|---|---|
| `FirestoreHelper.kt:12-35` | Reads resource ID, opens named DB; silently falls back to default on initialization error | Shared singleton |
| `FirebaseRemoteDataSourceImpl.kt:59-61,76-190,200,221` | App remote source | `Tests_Data`, `LiveTests`, `Users_Data`, `Config/app_config` |
| `FirebaseUserRepositoryImpl.kt:13-15,26,57-86` | User profile and backup/restore | `Users_Data/{uid}`, nested `attempts` |
| `R2Uploader.kt:32-35,93,138` | R2 upload metadata update | Caller-supplied collection/document/field |
| `CrashSyncWorker.kt:86-104` | Crash sync | `Crash_Reports/{incidentId}` |
| `SuperAdminViewModel.kt:86-105` | Admin diagnostics | Aggregate counts in `Users_Data`, `Tests_Data` |
| `GeminiPdfExtractor.kt:28-29,272-276` | Gemini PDF pipeline | `LiveTests/{testId}`, nested `Questions`, `private_keys/answerKey` |

### Other Firebase SDK files reviewed

- `NEETApplication.kt` and `AppCheckHelper.kt`: initialize/access the default FirebaseApp; do not choose a Firestore database.
- `FileUploaderUtil.kt`, `CommunityUI.kt`, `TestReviewScreen.kt`: use default Firebase Storage bucket; Storage is independent of Firestore database selection.
- `R2Uploader.kt`: uploads to R2 and updates Firestore through the helper.
- `GoogleDriveApiClient.kt`: Google Drive transport, not a Firestore server-side export.

### Android findings

1. Main splits active feature traffic between named `9fa...` and `(default)`.
2. Silent helper fallback can hide a bad database configuration.
3. Main TestReview writes root `questions/{questionId}`, while Rules only cover nested questions under `Tests` and `Tests_Data`; default deny blocks the root path. PR #10 changes this to `Tests_Data/{testId}/questions/{questionId}`.
4. `AdManager` is a false positive because its Firestore block is commented.
5. R2Uploader's dynamic collection argument requires a caller-by-caller audit.

## 5. Cloud Functions and server-side Firestore dependency review

The complete `backend_functions/index.js` (896 lines) and `authHelper.js` (67 lines) were fetched and reviewed. The backend creates one shared Admin SDK Firestore handle at `index.js:9`.

| Export | Type | Collections / paths | Purpose |
|---|---|---|---|
| `normalizeExamKey` | Plain helper | None | Exam-key normalization |
| `purgeRetiredDemoTests` | HTTPS callable | `Tests_Data`, `Tests`, `LiveTests`, `Shared_Community_Tests`, `TestAnswerKeys` | Checks hard-coded retired IDs and recursively deletes matching test docs/subcollections and answer-key docs |
| `submitTestAttemptServerSide` | HTTPS callable | `Users_Data`, `Tests_Data`, legacy `Tests`, `LiveTests`, nested `questions`/`Questions`, nested `private_keys`, `TestAnswerKeys`, `Attempts`/`attempts`, `Test_Leaderboards/{testId}/Entries`, `Leaderboard` | Server-side grading and transactional attempt/ranking writes |
| `getUserRankingDetails` | HTTPS callable | `Users_Data`, `Leaderboard` | User rank/percentile reads |
| `setAdminRole` | HTTPS callable | No Firestore; Firebase Auth Admin SDK | Validates caller via `validateSetAdminRoleAuth`, then sets custom claims |
| `getTestAttemptSolutions` | HTTPS callable | `Users_Data`, nested `Attempts`/`attempts`, `Tests_Data`, `Tests`, `Shared_Community_Tests`, `LiveTests`, nested questions/private keys, `TestAnswerKeys` | Verifies completed attempt and returns authorized solutions |

Authorization findings:
- `purgeRetiredDemoTests` requires authenticated admin/verified owner and uses a fixed ID allowlist. It is destructive; do not invoke during audit or migration.
- Submit, ranking and solutions callables require authentication; they validate active user status against `Users_Data/{uid}`.
- `setAdminRole` calls the shared helper, which requires authenticated admin claim or verified primary-owner email.
- No Firestore document-trigger functions were found in current repository code search.
- Repository-wide searches for callable names found backend/tests/docs references but no Android Kotlin caller for these exported callables on main. This is an integration gap to investigate, not proof that no external caller exists.

Backend schema risks:
1. Both uppercase/lowercase legacy paths are supported: `Attempts`/`attempts`, `Questions`/`questions`.
2. Tests may be read from four top-level collections; answer keys may be nested or in `TestAnswerKeys`. Migration must preserve the correct source of truth.
3. Backend Admin SDK bypasses Firestore Rules; function authorization must remain intact.
4. Ranking writes and reads depend on `Test_Leaderboards` and `Leaderboard`; query indexes must match the final target database.

## 6. Firestore Rules, indexes and Storage cross-check

Complete current `firestore.rules` (445 lines), `storage.rules` (88 lines), `firebase.json` (46 lines) and `firestore.indexes.json` (104 lines) were fetched and reviewed.

Rules explicitly cover `Config`, `admin_settings`, `Users_Data` and its listed subcollections, `Leaderboard`, `Leaderboards`, `Test_Leaderboards`, `Tests`, `Tests_Data`, question/private-key paths, `TestAnswerKeys`, `LiveTests`, `Shared_Community_Tests`, `Community_Posts`/Comments, `Community_Reports`, `UserNotifications`, `test_feedbacks`, `User_Analytics`, and audit-log collections. Unmatched paths are denied.

Concrete mismatches:
1. **Crash reports blocked:** `CrashSyncWorker.kt` writes `Crash_Reports/{incidentId}`, but there is no Rules match. Default deny blocks it. Add a least-privilege rule or trusted ingestion function; do not open general writes.
2. **Anonymous analytics blocked:** `AnalyticsManager` falls back to `anonymous_user`, but Rules require authenticated UID equality. Unauthenticated writes fail.
3. **TestReview root path blocked:** root `questions/{questionId}` has no Rule; PR #10 changes it to nested `Tests_Data/{testId}/questions/{questionId}`.
4. **Dynamic R2 paths:** all callers must be traced against Rules.
5. **Community schema coordination:** main serializer fields match current allowlist; PR #8 adds `highlightedSpans`, which must be coordinated with Rules and bounded tests.
6. **Report evidence:** Firestore report path and Storage `community_report_evidence/{uid}/{reportId}` need caller UID/path ownership validation.
7. **Storage is separate:** changing Firestore ID does not move Storage objects or update their URLs.

Storage paths covered: `tests/{testId}/questions/{fileName}`, `chart_images/{fileName}`, `question_images/{fileName}`, `LiveTests/{fileName}`, `pdfs/{fileName}`, `community_images/{uid}/{fileName}`, `community_report_evidence/{uid}/{reportId}`, plus admin-only catch-all.

Indexes currently cover Users_Data exam/score queries, Leaderboard exam/eligibility/score queries and Community_Posts exam/timestamp feed. `firebase.json` still targets named `9fa...` on main, so Rules/index deployment currently points to that named database.

## 7. PR #8 and PR #10 branch reconciliation

Both PR heads were inspected independently because they are not interchangeable snapshots.

| Branch | Base snapshot | Firestore ID in branch | Backend Functions state |
|---|---|---|---|
| PR #8 `feature/community-completion-2026-11-13` | Older main base `1e236d099522d63ec89ab0d11287b48315efc48c` | Android resource, helper and backend all contain named `4a...`; `firebase.json` targets `4a...` | Adds community callables and moderation/migration functions |
| PR #10 `fix/use-default-firestore-everywhere` | `main` base `a857d6ba0e21c9c37f10c26700e925cb1afaa3e8` | Android resource, helper, backend and `firebase.json` select `(default)` | Changes shared backend DB handle to `admin.firestore()`; does not contain PR #8's new community backend exports |

### PR #8 community backend additions

The complete PR #8 `backend_functions/index.js` (1,907 lines) was fetched. In addition to the six existing exports, it adds:

- `deleteCommunityPost`
- `createCommunityMcq`
- `submitCommunityMcqAttempt`
- `migrateLegacyCommunitySharedTests`
- `migrateLegacyCommunityPostVisibility`
- `syncCommunityExamSubjects`
- `createCommunityReply`
- `deleteCommunityReply`
- `setCommunityReaction`

The community functions use the same shared Admin SDK database handle configured to `4a...` in PR #8. Their collections include `Community_Posts`, `Community_MCQ_Attempts`, `Community_MCQ_AnswerKeys`, `Community_Moderation_Audit`, `Community_Exam_Subjects`, `Users_Data/{uid}/Blocked_Users`, and nested Comments/Users.

The migration/sync callables are admin-protected. They default to dry-run and require explicit confirmation strings before write mode. **Do not invoke these callables as part of this audit or before backup and review.**

### PR #8 client-to-backend routing mismatch

PR #8 adds Android calls to the new HTTPS callables from:
- `CommunityUI.kt`: `setCommunityReaction`, `createCommunityReply`, `deleteCommunityPost`, `createCommunityMcq`, `deleteCommunityReply`
- `CommunityMcqUI.kt`: `submitCommunityMcqAttempt`
- `CommunityAdminUI.kt`: `syncCommunityExamSubjects`
- `CommunityModerationUI.kt`: `migrateLegacyCommunitySharedTests`, `migrateLegacyCommunityPostVisibility`

However, the same PR #8 Community UI uses no-argument `FirebaseFirestore.getInstance()` for direct client reads/writes, which selects `(default)`, while its Admin SDK Cloud Functions use named `4a...`. Therefore PR #8's community feature itself can split direct client data and callable backend data across two databases.

PR #10 changes the helper and backend to `(default)`, which is the intended direction, but PR #10 does not include PR #8's new community functions and PR #8 is based on an older main commit. A safe integration must combine PR #8's community code with PR #10's default-database selection, preserve all new exports, and then re-run backend/Rules/Android tests. Merging either branch blindly risks restoring an old database ID or losing changes in `backend_functions/index.js`.

### PR #8 Rules additions and remaining gap

PR #8 adds Rules for `Community_Exam_Subjects`, `Community_Moderation_Audit`, `Community_MCQ_AnswerKeys`, `Community_MCQ_Attempts/{postId}/Users/{userId}`, plus bounded `highlightedSpans` validation on Community posts. Those additions align with its new community features.

The existing Android `NEETViewModel.kt` also reads/writes `Question_Reports` (main: 61 Firestore collection references in an 8,237-line file; PR #8 version: 8,297 lines). Neither main Rules nor PR #8 Rules define a `Question_Reports` match. The default-deny rule therefore blocks these client operations unless they are moved to an authorized backend path or explicit least-privilege Rules are added. This is separate from `Community_Reports`, which does have Rules.

## 8. NEETViewModel full Firestore dependency map

The complete main-branch `NEETViewModel.kt` (8,237 lines) was fetched and scanned. It contains 61 Firestore collection-reference expressions across these feature groups:

| Collection | Methods / feature areas | Data behavior |
|---|---|---|
| `Admin_Audit_Logs` | `logAdminAudit` | Admin audit event creation |
| `Users_Data` | `fetchAdminUsers`, `toggleUserBan`, `fetchUserRank`, profile save, bookmark loading, account status, backup, attempt sync | Profile, admin user management, status and backup reads/writes |
| `Leaderboard` | `fetchUserRank`, `fetchLeaderboard`, fallback | Ranking queries and listeners |
| `Config` | `listenToAdminSettings`, AI/free-test limit and private community controls | Admin configuration read/write |
| `LiveTests` | schedule, participants/listeners, generated test save, deep-link lookup | Live test lifecycle |
| `UserNotifications` | add/update/delete/listen, schedule/publish notices | In-app notification lifecycle |
| `Tests`, `Tests_Data` | delete test, upload/publish/visibility, deep-link lookup | Test lifecycle and publication |
| `Shared_Community_Tests` | share and deep-link lookup | Shared test content |
| `Community_Posts` | test-share post creation/deduplication | Community feed publication |
| `Question_Reports` | submit, report question error, list, update status | Student issue reports and admin review |
| `test_feedbacks` | submit feedback | Feedback create |
| nested `private_keys` | upload, generated test save, visibility/publish | Protected answer-key writes |

All of these calls use the ViewModel's injected `db`/Firestore helper path, which resolves to named `9fa...` on main and to `(default)` under PR #10's helper.

Important side-effect and authorization checks:
- `deleteTest` deletes the same test ID from `Tests`, `Tests_Data` and `LiveTests`; it is a destructive user/admin workflow and must not be used for database migration.
- Publishing and private answer-key writes depend on admin-only Rules; confirm custom claims and the current signed-in admin account during QA.
- `Question_Reports` has no explicit match in main or PR #8 Firestore Rules; current default-deny blocks those client operations. Do not solve by making the whole collection public or broadly writable.
- Main `shareTestToCommunity` writes to both `Shared_Community_Tests` and `Community_Posts` from the client; these two records need an atomicity/partial-failure review and a consistent server-authoritative subject validation path.

## 9. PR review state at audit start

- PR #10, `fix/use-default-firestore-everywhere`: Open, Draft, Unmerged; head `faa5fb9d033d3bb6bd060b56d054445127a60a60`; base `main` at `a857d6ba0e21c9c37f10c26700e925cb1afaa3e8`.
- PR #8, `feature/community-completion-2026-11-13`: Open, Draft, Unmerged; head `ed032e111d375813fa90fde9b2486ad635f1e3fd`; base `main` at `1e236d099522d63ec89ab0d11287b48315efc48c`.

These PRs are separate workstreams. This audit branch was created from `main` and does not merge or modify either PR.

## 10. Repository inventory snapshot

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

## 11. Required next audit steps

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

## 12. Change log

| Date | Branch | Change | Production impact |
|---|---|---|---|
| 2026-11-15 | `audit/default-firestore-consolidation-2026-11-15` | Added initial inventory, full Android call-site and Cloud Functions reviews, Rules/index/Storage cross-check and concrete permission/path mismatches | None; documentation only |

## 13. Current status

**Audit in progress.** Complete contents of identified Android Firestore call-site files, backend index/auth helper, Firestore Rules, Storage Rules, Firebase config and indexes were fetched and reviewed. Main-branch cross-database routing and concrete Rules mismatches are documented. Next: feature-to-collection schema inventory, client/backend caller contract review and PR #8/#10 integration design. Dynamic R2Uploader call search found no callers outside its own file. No live Firebase state has been inspected. No database has been removed, migrated, or modified.
