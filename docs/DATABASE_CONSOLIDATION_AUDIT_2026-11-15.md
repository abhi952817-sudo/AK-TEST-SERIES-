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

However, PR #8's Community UI, Community Admin UI, Community Moderation UI and Community MCQ UI use no-argument `FirebaseFirestore.getInstance()` for direct client reads/writes, which selects `(default)`. The PR #8 `NEETViewModel` obtains its database through `FirestoreHelper` configured to named `4a...`, while its Admin SDK Cloud Functions also use named `4a...`. Thus Community UI direct feed/report/MCQ-attempt reads can target `(default)`, while ViewModel operations and callable writes target `4a...`. This is a concrete client/server split inside PR #8.

PR #10 changes the helper and backend to `(default)`, which is the intended direction, but PR #10 does not include PR #8's new community functions and PR #8 is based on an older main commit. A safe integration must combine PR #8's community code with PR #10's default-database selection, preserve all new exports, and then re-run backend/Rules/Android tests. Merging either branch blindly risks restoring an old database ID or losing changes in `backend_functions/index.js`.

### PR #8 Rules additions and remaining gap

PR #8 adds Rules for `Community_Exam_Subjects`, `Community_Moderation_Audit`, `Community_MCQ_AnswerKeys`, `Community_MCQ_Attempts/{postId}/Users/{userId}`, plus bounded `highlightedSpans` validation on Community posts. Those additions align with its new community features.

The existing Android `NEETViewModel.kt` also reads/writes `Question_Reports` (main: 61 Firestore collection references in an 8,237-line file; PR #8 version: 8,297 lines). Neither main Rules nor PR #8 Rules define a `Question_Reports` match. The default-deny rule therefore blocks these client operations unless they are moved to an authorized backend path or explicit least-privilege Rules are added. This is separate from `Community_Reports`, which does have Rules. PR #8's new `CommunityModerationUI` also uses direct default-DB access for `Community_Reports`, `Community_Moderation_Audit`, `Community_Posts` and `Users_Data`; `CommunityMcqUI` directly reads `Community_MCQ_Attempts/{postId}/Users/{uid}`. The corresponding Rules exist for those PR #8 paths, but the direct reads must still be aligned with the callable database.

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

## 9. Remaining Firebase and external-service inventory

The repository-wide search and relevant source files were checked for Firebase Auth, Firestore, Realtime Database, Storage, Functions, Firebase AI, Google Drive and Cloudflare R2.

| Service | Current code use | Does Firestore database-ID migration move it? | Audit result |
|---|---|---|---|
| Firebase Authentication | Email/password, Google sign-in, email-link sign-in; custom admin claims | No. Auth users and claims are project-level, not Firestore database collections | Keep same Firebase project; verify sign-in and admin claims in QA |
| Cloud Firestore | Main app data, tests, users, ranking, community, notifications, reports, analytics, configuration | This is the service being consolidated | Route all Android and Admin SDK calls to `(default)`; migrate documents separately |
| Firebase Realtime Database | `AdminPanelScreen.kt:437-438` writes `live_tests/{activeTestId}` through `Firebase.database` | No. Realtime Database is a separate service/database | No RTDB rules file or RTDB config block exists in `firebase.json`; inspect live RTDB instance/data and rules before deciding whether to retain, migrate or retire |
| Firebase Storage | Images, question assets, PDFs, community images and report evidence | No. Objects, bucket paths and download URLs are independent | Keep bucket and object paths stable during Firestore cutover; verify bucket identity from Google Services config/Console |
| Cloud Functions | Server-side grading, ranking, solutions, admin role, community moderation and migrations | Function code must select the same Firestore target explicitly | PR #10 changes shared Admin SDK handle to default; PR #8 community functions must be carried forward |
| Firebase AI | `FirebaseAiService.kt` uses `Firebase.ai.generativeModel`; Gemini PDF extractor writes generated test data to Firestore via helper | AI model configuration is not a Firestore database; only its Firestore writes are affected | Preserve AI model setup; route only its data persistence through default |
| Google Drive backup | Account-owned Room/SharedPreferences/files archive, UID-bound, staged restore, retention | No. Drive is an external backup store | Not a full Firestore export. Do not treat it as the source Firestore backup or database migration copy |
| Cloudflare R2 | `R2Uploader.kt` contains placeholder credentials and dynamic Firestore update | No. R2 bucket/object data is external | No production callers found outside the class/report. All values are placeholders. Never place real R2 secret keys in Android; keep disabled until server-side signed upload design exists |
| Room / local files | Local tests, attempts, PDF sessions, diagrams and user preferences | No. These are device-local data stores | Keep existing account backup/restore pipeline; verify local data is not mistaken for Firestore cloud data |

### Additional high-risk findings

1. **Realtime Database is a separate database product:** Admin panel writes `live_tests/{testId}` using `Firebase.database`. `firebase.json` has no `database.rules.json` deployment target and repository tree has no RTDB rules file. Its current production contents/rules cannot be inferred from the code. It must not be deleted as part of Firestore cleanup.
2. **R2 credentials must never be embedded in Android:** `R2Uploader.kt` has literal placeholders for access key, secret, account ID, bucket and public domain. No production values are present and no app caller was found. Keep this path disabled; if needed later, use a server-issued short-lived upload authorization or backend proxy with secrets in Secret Manager.
3. **Storage failure fallback is unsafe on main:** `FileUploaderUtil.uploadImage` can return a local cache URI after Firebase Storage upload failure; `uploadPdf` can return the original local content URI after failure. Such references are not remotely accessible to other students/admins and can create broken content. PR #10's patch changes failure handling to return null; retain and test that fail-closed behavior during integration.
4. **Google Drive is not a Firestore backup:** the account archive exports selected Room tests/questions/attempts/bookmarks/progress/syllabus/profile and diagram files for the signed-in UID. It does not export all Firestore collections, Cloud Functions state, Auth users, Storage objects, RTDB records or indexes.
5. **Firebase Storage and Firestore Rules have different scopes:** Firestore database selection does not change Storage bucket selection. Preserve existing Storage object URLs and validate rules separately.

## 10. Test coverage and observed CI evidence

Repository test assets:
- `firestore.test.js`: 937 lines of Firestore Rules emulator tests.
- `storage.test.js`: 96 lines of Storage Rules emulator tests.
- `.github/workflows/community-rules.yml`: runs Firestore and Storage emulator tests using demo project ID `demo-no-project`.
- Backend unit tests: `callableValidation.test.js` and `examScoring.test.js`.
- Android test suite includes Room migration, backup/restore, authorization, account switching, exam/ranking and application tests.

Latest observed GitHub Actions runs at audit time:
- Main commit `a857d6b`: Android CI completed successfully on 2026-11-14.
- PR #8 head `ed032e1`: Android CI and Community Firebase Rules CI completed successfully on 2026-11-14.
- PR #10 head `faa5fb9`: Android CI and Community Firebase Rules CI completed successfully on 2026-11-14.

These are historical CI results for those exact commits, not a test run of the combined integration branch and not live Firebase QA. Passing emulator Rules tests do not prove the deployed production Rules are current. PR #10 Android CI includes the default-database configuration branch but does not prove live Auth/Firestore/Storage connectivity without the configured CI Google Services secret and emulator/runtime setup.

## 11. Target architecture and final dependency decisions

The intended Firestore target is **one database: `(default)`**. The following must be true before a production cutover:

- Android helper, every direct Android Firestore call, Cloud Functions, Firestore Rules and indexes all refer to the same project and default database.
- The app must fail closed on a database configuration mismatch; no silent fallback from a named database.
- Cloud Functions must preserve all existing main exports and PR #8 community exports when combined.
- Community client direct calls and Community callable backend functions must use the same database.
- Collection paths, document IDs, nested subcollections and answer-key locations must be preserved or mapped explicitly.
- Storage bucket and file paths remain unchanged; Firestore document URLs remain valid.
- Auth users and custom claims remain in the same Firebase project; admin authorization is tested.
- RTDB `live_tests` remains a separate, explicitly documented service until its production data/rules are inventoried.
- R2 remains disabled until secrets are moved server-side and a caller is intentionally introduced.
- Google Drive account backups remain separate from the full server-side Firestore export.

## 12. Final migration blockers and safe execution order

### Blockers that require access/approval outside repository code

1. Firebase Console/Admin access to list every Firestore database, exact database ID, region, edition, document counts, collection counts and project ID. The user-reported `b6...` database and source-only `4a...` ID need explicit existence checks.
2. Verify the exact default database project, region, edition and whether the reported 27 reads / 5 writes and three collections are still current.
3. Verify the default Storage bucket and object counts; separately inventory RTDB `live_tests` and its rules.
4. Create and verify a server-side managed Firestore export for every source database to a private, access-controlled GCS bucket. Confirm export operation completion and object counts. Google Drive account backup is not a substitute.
5. Run a read-only source/destination document inventory and schema sampling; identify duplicate IDs, collisions, orphan subcollections, references and collection casing differences.
6. Run a migration dry-run to a protected staging database, reconcile per-collection document counts and checksums, test rollback and obtain explicit production approval.

### Ordered implementation

1. **Code integration branch:** combine PR #8 community feature changes with PR #10 default-database selection without losing main updates or any callable export. Keep PRs unmerged until combined branch passes.
2. **Database routing:** replace direct no-argument Firestore access with the central default helper or a single injected instance. Keep Storage and RTDB initialization separate.
3. **Rules/schema fixes:** add least-privilege rules for confirmed client paths such as `Question_Reports` and crash ingestion; reconcile community moderation/MCQ paths and highlighted-span validation. Do not broadly open collections.
4. **Client/backend contracts:** add explicit callable wrappers and request/response tests for server-side grading, ranking, solutions and PR #8 community callables. The main branch currently has no Kotlin call sites for the five existing backend callables found by name search.
5. **Backup and migration tooling:** implement a server-side export manifest, source/destination inventory, deterministic ID mapping, idempotent copy, per-collection reconciliation and rollback instructions. Do not run on production yet.
6. **Automated QA:** Android unit tests, backend tests, Firestore and Storage emulator Rules tests, default database configuration tests, and end-to-end tests with two student accounts and one admin account.
7. **Staging cutover rehearsal:** test only against a protected staging database, verify app reads/writes, callable paths, admin claims, community, attempts, answer keys, ranking, uploads and backups.
8. **Production cutover:** only after explicit approval; pause writes, export, copy, reconcile, switch app/backend/rules/index targets, smoke-test and monitor.
9. **Retirement:** keep old databases read-only through an agreed observation window. Delete only after verified backups, zero remaining traffic, reconciled data and separate explicit approval for each database.

## 13. Change log

| Date | Branch | Change | Production impact |
|---|---|---|---|
| 2026-11-15 | `audit/default-firestore-consolidation-2026-11-15` | Added initial inventory, full Android call-site and Cloud Functions reviews, Rules/index/Storage cross-check, PR #8/#10 reconciliation, service inventory, CI evidence and gated migration plan | None; documentation only |

## 14. Current status

**Source-code audit substantially complete for the Firebase/database paths identified in the repository tree and code search.** The main routing split, Community branch split, Rules mismatches, separate RTDB/Storage/Auth/Drive/R2 dependencies and migration requirements are documented. The combined integration branch, code changes, live database inventory, server-side backup, migration rehearsal, deployment and database retirement are not complete. No live Firebase state has been inspected. No database has been removed, migrated, or modified.
