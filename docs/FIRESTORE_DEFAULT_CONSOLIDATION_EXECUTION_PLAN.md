# Firestore Default Database Consolidation Execution Plan

**Repository:** abhi952817-sudo/AK-TEST-SERIES-  
**Target:** one Firestore database, (default)  
**Status:** prepared from source audit; production execution blocked pending verified access, backup and approval.  
**Related audit:** docs/DATABASE_CONSOLIDATION_AUDIT_2026-11-15.md

## Non-negotiable safety rules

- Never delete a Firestore database or production document during code integration or staging rehearsal.
- Never run destructive callables such as purgeRetiredDemoTests as a migration shortcut.
- Never use a user's Google Drive archive as a substitute for a complete server-side Firestore export.
- Never copy R2 access keys, Firebase service-account keys, or other secrets into Android code, Git, or chat.
- Keep Storage, Realtime Database, Authentication, Room, Google Drive and Cloudflare R2 as separate services; a Firestore database-ID change does not migrate them.
- Keep old databases read-only after cutover until zero traffic and data reconciliation are demonstrated.
- Production cutover and each database deletion require separate explicit approval.

## Database ID register

| ID | Evidence | Decision |
|---|---|---|
| (default) | User-selected target; user reported collections Community_Posts, Tests_Data, Users_Data and 27 reads / 5 writes over the last 30 days | Target, but live inventory and project/region still need verification |
| ai-studio-android-aktestse-9fa19369-81c5-4d42-bf79-ab4f9f40d4bf | Android config, Android helper and firebase.json on main | Source database candidate; user reported Singapore/Enterprise |
| ai-studio-android-aktestse-4a0dbfd1-96e9-481f-b66c-2984491d0612 | Backend Functions on main; PR #8 config/backend | Source database candidate; not present in user's reported Console list; existence must be checked |
| ai-studio-android-aktestse-b6ff3593-06aa-4623-ade3-3201b7fad5c9 | User-reported ID; no main source references found | Console existence/content must be checked; do not assume empty |

## Phase 0: Access and read-only inventory

Required before any copy:
1. Confirm Firebase project ID and that Android, Functions and Console all point to the same project.
2. List all Firestore databases by exact ID, region, edition, state and creation metadata.
3. For each database, export collection/document counts, nested subcollection counts, oldest/newest timestamps and sampled schemas without changing data.
4. Record per-collection document counts, document IDs, field types, references and known duplicate/collision IDs.
5. Record Firestore indexes and deployed Rules version for each database.
6. Inventory Storage bucket, object counts, prefixes and referenced download URLs.
7. Separately inventory Realtime Database live_tests and its deployed security rules.
8. Record active Functions and their deployed region/runtime/version.

**Stop condition:** if project identity or any database identity cannot be proven, do not proceed.

## Phase 1: Code integration branch

Base the integration branch on the latest main, then combine:
- PR #10's (default) Firestore configuration and fail-closed helper behavior.
- PR #8's Community UI, moderation, MCQ and Cloud Functions changes.
- Any newer main changes since both PR bases.

Preserve all existing main backend exports and all nine new PR #8 community exports. Do not copy PR #8's old 4a... database selection back into the integrated branch.

Required integrated state:
- Android resource and central helper select (default).
- No silent named-database fallback remains.
- All direct Android Firestore access uses the same injected/default helper.
- Cloud Functions use admin.firestore() for the same Firebase project.
- firebase.json Firestore target is (default).
- Rules and indexes are deployed to the default database only after staging validation.
- Storage and Realtime Database initialization remain independent.

## Phase 2: Feature and collection routing

| Feature | Collections / paths | Required check |
|---|---|---|
| User profiles | Users_Data/{uid} | Auth UID ownership, account status, profile schema |
| Attempts | Users_Data/{uid}/Attempts, legacy attempts | Preserve casing and IDs; authoritative server score |
| Test catalog | Tests_Data, legacy Tests, LiveTests | Visibility, uploader ownership, exam identity |
| Questions | nested questions and Questions | Parent test mapping, stable question IDs |
| Answer keys | nested private_keys/answerKey, TestAnswerKeys | Never expose to student reads; verify source of truth |
| Ranking | Leaderboard, Leaderboards, Test_Leaderboards/{testId}/Entries | Exam isolation, index coverage, tie/eligibility |
| Community | Community_Posts, Comments, bookmarks, likes, reports | Subject validation, moderation, owner checks |
| Community MCQ | Community_MCQ_Attempts, Community_MCQ_AnswerKeys | Callable-only answer validation and user attempt isolation |
| Community config | Config/community_settings, Community_Exam_Subjects | Admin-only writes, valid exam-subject mapping |
| Notifications | UserNotifications | Target group and recipient access |
| Feedback / reports | test_feedbacks, Question_Reports, Community_Reports | Least-privilege create/read/update rules |
| Analytics / crash | User_Analytics, Crash_Reports | UID ownership, sanitized payload, anti-abuse limits |
| Audit | Admin_Audit_Logs, admin_audit_logs, Community_Moderation_Audit | Server/admin-only write and retention |

Do not delete legacy collection names until all clients and Functions have stopped reading them and counts are reconciled.

## Phase 3: Rules, indexes and callable contracts

- Add explicit least-privilege rules and tests for confirmed paths such as Question_Reports and Crash_Reports; do not allow blanket authenticated writes.
- Align Community highlightedSpans validation, MCQ paths and moderation audit paths between UI, callable functions, Rules and tests.
- Confirm Community report evidence Storage path is bound to the authenticated reporter UID.
- Validate indexes for Community feed, Users_Data ranking and Leaderboard queries against emulator and staging.
- Add Android callable clients and typed request/response contracts for submitTestAttemptServerSide, getUserRankingDetails, getTestAttemptSolutions and PR #8 community callables.
- Ensure all callable authorization checks remain server-side. Admin SDK bypasses Firestore Rules.
- Add tests for unauthenticated, banned, non-admin, admin and cross-user access.

## Phase 4: Backup and migration rehearsal

1. Create a private GCS bucket with retention/versioning and least-privilege access.
2. Run Firestore managed export for each source database; wait for successful operation completion.
3. Verify export object manifest and counts. Record source database ID and export timestamp.
4. Create a protected staging database. Never use production (default) as the restore target for rehearsal.
5. Copy documents using deterministic source-to-target path mapping. Preserve document IDs, nested collections, timestamps and references.
6. Produce a dry-run report before writes: source count, target existing count, expected creates, updates, collisions, skipped records and orphan references.
7. Reconcile per collection and nested subcollection; sample critical test, answer-key, attempt, user, community and ranking documents.
8. Verify Storage URLs and referenced assets separately.
9. Test rollback by restoring the export to a second isolated staging database.
10. Require zero unexplained count mismatch and zero unresolved critical references before production approval.

## Phase 5: Staging QA

Test with two student accounts and one admin account:
- Email/password, Google sign-in and email-link sign-in.
- User profile create/read/update and banned-account rejection.
- Test list, test upload/publish/visibility and deep links.
- Attempt submission, server grading, solutions and answer-key protection.
- Ranking reads, exam isolation, eligibility and indexes.
- Community post, highlight spans, comments, likes, bookmarks, reports, evidence upload, moderation and MCQ attempts.
- Notifications, feedback, analytics and crash sync.
- Gemini PDF extraction and Firestore persistence.
- Storage upload failure must fail closed; no local URI may be saved as a shared remote asset URL.
- Google Drive backup/restore must remain UID-bound and must not be represented as a full Firestore export.
- RTDB live test scores must be tested separately.
- Offline/cache/retry and account-switching behavior.

## Phase 6: Production cutover

Only after explicit approval:
1. Announce maintenance and pause all writes.
2. Take final source exports and capture final counts.
3. Run final delta migration and reconcile.
4. Deploy Firestore Rules and indexes to (default).
5. Deploy Functions using default Firestore.
6. Release Android build with default-only helper and no direct named-database references.
7. Run read/write smoke tests with student and admin accounts.
8. Monitor function errors, Firestore reads/writes, permission-denied events, Storage uploads and user reports.
9. Keep source databases intact and read-only during the agreed observation window.

## Phase 7: Retirement of old databases

A database may be deleted only when all conditions hold:
- Verified GCS export exists and restore drill passed.
- Per-collection counts and critical references reconcile.
- App and Functions no longer reference or send traffic to that database.
- Logs/monitoring show zero reads/writes for the agreed observation window.
- No remaining scheduled jobs, old app versions, scripts or external clients depend on it.
- User explicitly approves deletion of that exact database ID.

Delete databases individually, not in a bulk operation. Record who approved, timestamp, ID and final backup reference. If any condition is unknown, retain the database.

## Separate services: do not accidentally retire them

- Authentication: same project-level Firebase Auth users/custom claims; no Firestore collection copy.
- Storage: keep bucket and object paths; inventory and test independently.
- Realtime Database: live_tests/{testId} is separate; inspect rules and contents before any decision.
- Google Drive: account-level backup archive only; not a full cloud database backup.
- Cloudflare R2: disabled placeholder-only implementation; no production callers found. Never ship R2 secrets in Android.
- Room/local files: remain device-local and use the existing account-owned backup/restore pipeline.
- Firebase AI: model configuration remains separate; generated Firestore records follow the default Firestore helper.

## Current completion state

- Source-code inventory and dependency review: substantially complete.
- PR #8/#10 integration: not performed.
- Production Firebase inventory: not available from repository access.
- Server-side export/backup: not performed.
- Migration dry-run: not performed.
- Production deployment/cutover: not performed.
- Old database deletion: not performed.

This plan deliberately distinguishes code work from production operations. A green CI run cannot prove that production data has been copied or that an old database is safe to delete.
