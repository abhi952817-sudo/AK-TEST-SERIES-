# Universal Exam Scoring — Branch Remaining Work Plan

**Branch:** `feature/universal-exam-scoring-v1-2026-11-06`  
**Audited HEAD:** `9b105e9e7c614d615becb99a72b3dc5e50799e05`  
**Main HEAD:** `b38bd497434dd24438b37b3de87bd26b4fbe98f8`  
**Git comparison:** 133 commits ahead, 93 behind; merge base `d2feae47f908b705209c27b8e61e624f94c400d7`.

# Branch-specific remaining-work plan

**Repository:** `abhi952817-sudo/AK-TEST-SERIES-`  
**Audit date:** 2026-11-12  
**Status:** Audit-derived execution plan. This document does not claim that listed work or tests are complete.

## Mandatory rules

- Work only on this branch for its assigned scope. Do not merge or cherry-pick automatically.
- Keep `main`, production Firebase, live Firestore/Storage data, rules and indexes unchanged unless the owner separately approves a specific deployment.
- Preserve working screens, data contracts, attempts, test history, user data and existing functionality.
- No dummy/fabricated questions, answers, syllabus data, exam patterns, scores, rank, API success or usage/cost records.
- No silent offline generation or silent provider fallback. Fail closed with a clear error and review state.
- Keep API secrets out of source, APK, logs, crash reports and client-readable Firestore.
- Every implementation phase requires focused tests, regression tests, exact commit SHA and CI evidence. Never call an old branch's CI pass a pass for the current head.
- Before integrating work from another branch, compare source, call sites, migrations, security rules and tests file by file.
- Do not delete a branch or legacy data as part of cleanup without owner approval, backup and rollback plan.

## Audit snapshot

Branch HEAD and main divergence were obtained from GitHub branch comparison on 2026-11-12. Commit counts describe Git ancestry, not completed features. The branch is intentionally kept separate from main.

## Execution checklist

- [ ] Re-read this plan and inspect the current branch HEAD before each session.
- [ ] Confirm source files and call sites; distinguish implemented, partially implemented, missing and unverified.
- [ ] Fix the first blocking issue in this branch's scope without broad unrelated rewrites.
- [ ] Add regression coverage for both valid and invalid cases.
- [ ] Run focused tests, full relevant tests, security/emulator tests and build checks.
- [ ] Record commit SHA, commands, discovered/executed/skipped/failed test counts, CI run links and unresolved issues.
- [ ] Update this plan before moving to the next phase.

## Final release gate

No phase is complete until the current branch-head code, tests, security boundaries, data compatibility and user flow are verified. A successful compile alone is not enough. No production deployment or main merge is authorized by this plan.


## Branch purpose

This branch is the source workstream for universal exam scoring and exam-specific evaluation. It must remain distinct from ranking implementation until the scoring contract is verified and consumed by the integration branch.

## Remaining work

### S1. Canonical exam identity and configuration
- [ ] Use stable canonical exam IDs, stage IDs, paper IDs, section IDs and versioned exam-year configurations.
- [ ] Admin must explicitly select exam, year, stage and paper. Reject ambiguous aliases such as bare `JEE` when Main/Advanced cannot be distinguished.
- [ ] No implicit first stage/paper/section, synthetic section or guessed exam defaults.
- [ ] Verify every registered exam's configuration completeness and mark unsupported/unverified patterns as unavailable, not guessed.

### S2. Scoring correctness
- [ ] Define a canonical scoring contract used by Android and backend.
- [ ] Verify single-correct MCQ, MSQ/exact-set, numerical/NAT, TITA, integer and decimal answers, precision/tolerance, unanswered, dropped/bonus questions, partial marks and negative marking.
- [ ] Support five-option questions where the exam config permits; do not globally force four options.
- [ ] Enforce section caps and optional-section attempt limits using the exact paper configuration.
- [ ] Confirm maximum marks are computed from the selected paper and valid attempted questions, not from generic question count.
- [ ] Reject missing/ambiguous answer keys or unknown question types. Do not award fabricated defaults.

### S3. Android/backend parity
- [ ] Create shared golden fixtures and assert identical Android and Node backend outcomes.
- [ ] Test boundary values, floating-point precision, negative score, empty attempts, optional sections, invalid option IDs and duplicate question IDs.
- [ ] Keep server authoritative for official tests and prevent client score/rank writes.
- [ ] Verify result, review and leaderboard consume the same canonical score.

### S4. Database and migration
- [ ] Audit current Room schema version and compare with main's schema 21 / Migration20To21 additions.
- [ ] Verify migration chain from every supported old version; do not assume an earlier branch's migration chain is complete.
- [ ] Test preservation of attempts, questions, answer maps, test metadata, ranking fields and backups.
- [ ] No `fallbackToDestructiveMigration` path may silently erase student data. Document legacy versions without exported schema as an explicit blocker and recovery policy.
- [ ] Verify Firestore serialization/deserialization preserves exam ID, stage, paper, section, question type, marks, visibility, status and owner fields.

### S5. Integration with ranking
- [ ] Provide a stable score/max/eligibility result contract to `feature/ranking-universal-integration-v1`.
- [ ] Keep ranking score separate from XP.
- [ ] Verify Full Test 1.0x and Part/Chapter/Topic 0.2x weighting is applied only after normalized scoring and verified test classification.
- [ ] Test first qualifying attempt, retries, concurrency, ties and per-exam isolation in the integration branch; do not duplicate ranking logic here.

### S6. Exam verification and tests
- [ ] Build an exam-pattern evidence matrix: official source URL/document, year, retrieval date, exam ID, stage, paper, sections, question count, duration, options and marking policy.
- [ ] Do not mark an exam official/verified without source and review evidence.
- [ ] Run `:app:testDebugUnitTest`, backend tests and emulator security tests on exact branch HEAD.
- [ ] Include Migration20To21 and scoring parity regression tests where applicable.
- [ ] Record discovered/executed/skipped/failed test counts, CI run IDs and Debug APK build result.

## Main-branch reconciliation warning

Main currently contains a newer exam planner and additional fail-closed subject/section routing tests. The scoring branch must review those changes before integration. It must not overwrite them with stale registry or scoring defaults. The main branch also contains broader Room, Firestore and backend changes; do not cherry-pick the entire main branch.

## Completion criteria

Scoring is complete only when every enabled exam configuration is source-backed, Android/backend scores match, invalid or unknown inputs fail closed, migration tests preserve data, and exact-head CI/build evidence is green. No main merge or production deployment is authorized here.

### Evidence log
| Date | Commit | Evidence | Status |
|---|---|---|---|
| 2026-11-12 | `9b105e9e7c614d615becb99a72b3dc5e50799e05` | Git branch comparison and tracker review | Audit snapshot only; tests not run |
