# Firestore Branch Decision Gate — 2026-11-19

## Decision status: BLOCKED pending live Firebase inventory

The source repository contains conflicting Firestore database targets. No Firestore branch is safe to merge solely from source-code evidence because the repository cannot prove which database contains the authoritative production records.

## Conflicting candidates

| Candidate | Evidence | Current assessment |
|---|---|---|
| `(default)` | User's intended target in prior project decisions; PR #10 and PR #14 configure Android, Functions and firebase.json for default | Preferred target direction, but actual project identity, contents, location and records must be verified |
| `ai-studio-android-aktestse-9fa19369-81c5-4d42-bf79-ab4f9f40d4bf` | Main source Android resource/helper and firebase.json; PR #13 also uses this ID | Existing source target, not proven to be authoritative production data |
| `ai-studio-android-aktestse-4a0dbfd1-96e9-481f-b66c-2984491d0612` | Main backend_functions/index.js and PR #8/PR #9 history | Backend target in main; existence and data status not verified |
| `ai-studio-android-aktestse-b6ff3593-06aa-4623-ade3-3201b7fad5c9` | Reported in prior user discussion and captured in PR #11 audit | Not found in main source search; must still be checked in Firebase Console |

## Branch/PR conflict

- PR #9 restores the `4a...` named database.
- PR #13 selects the `9fa...` named database and changes several direct Firestore call sites.
- PR #10 selects `(default)` and includes broader safety changes: fail-closed upload handling, Storage catch-all deny, MIME tests, Google Drive ownership/pagination changes, and a Firebase runtime initialization smoke test.
- PR #14 selects `(default)` in four files only; its description explicitly states that no build, emulator, production read/write, backup, data migration or deployment was performed.
- PR #12 is a composite Community + default Firestore integration; its own description says combined CI, emulator checks, live database inventory, backup and staging restore drill are still required.

These are competing implementations, not independent additive branches. Do not merge PR #9, #13, #10, #12 and #14 as a stack.

## Source-level findings from PR #11 audit

The PR #11 audit documents that the historical main snapshot split Android helper traffic to `9fa...` while Cloud Functions used `4a...`; active no-argument Android Firestore calls independently use `(default)`. It also records direct Firestore call sites in Community UI, Admin Panel, Community Admin, Analytics and Test Review, plus a TestReview root-path/rules mismatch. These are code-routing findings, not proof of live data distribution.

PR #11 itself is documentation-only (two docs), and states that no live Firebase inventory or managed export was performed.

## Safe next action

1. Keep the preferred target direction as `(default)`, but do not cut over or merge database-selection changes until the owner verifies the exact Firebase project and all database IDs in Firebase Console.
2. For each database, record exact ID, region, edition, state, collection/document counts and representative schemas using read-only access.
3. Confirm which database contains production Users_Data, Tests_Data, attempts, answer keys, Community_Posts, ranking records and other authoritative collections.
4. Create and verify a server-side managed export to a private GCS bucket; Google Drive account backup is not a substitute.
5. Perform a protected staging restore/migration rehearsal and reconcile counts/references.
6. Only then select one coherent code implementation and run Android/backend/Firestore/Storage tests on the combined integration branch.
7. Never delete or retire any named database as part of code consolidation. Retirement requires separate explicit approval after verified backup, zero traffic and reconciliation.

## Current action

- No Firestore candidate branch merged.
- No database target changed on main.
- No Firebase Console state accessed.
- No production data read, written, migrated or deleted.
- All competing branches retained.
