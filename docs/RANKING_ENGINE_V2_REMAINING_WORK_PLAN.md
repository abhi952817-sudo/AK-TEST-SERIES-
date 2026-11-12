# Ranking Engine V2 — Branch Remaining Work Plan

**Branch:** `feature/ranking-engine-v2-2026-11-06`  
**Audited HEAD:** `a250f54a54035c21363a190df39dcd041d7aa55e`  
**Main HEAD observed:** `b38bd497434dd24438b37b3de87bd26b4fbe98f8`  
**Git comparison:** 92 commits ahead of main, 93 behind; merge base `d2feae47f908b705209c27b8e61e624f94c400d7`. Branches are diverged.

## Purpose and scope

This branch owns the standalone Ranking Engine V2. The integration branch `feature/ranking-universal-integration-v1` is the later integration workstream. Do not treat the two branches as interchangeable and do not merge this branch wholesale.

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


## Work remaining

### R1. Establish current ranking baseline
- [ ] Inspect `backend_functions/rankingEngine.js`, tests, callable exports in `backend_functions/index.js`, Firestore rules/indexes and Android ranking call sites at the audited HEAD.
- [ ] Reconcile historical README/tracker claims against actual current source. Older CI notes (including runs `42354948833` and `42355909308`) are historical and do not establish current status.
- [ ] Resolve current PR/workflow state and collect the latest branch-head CI logs. No current-head green result is established by this audit.

### R2. Canonical ranking correctness
- [ ] Verify ranking is server-authoritative and transactional under concurrent attempts.
- [ ] Enforce one first valid qualifying attempt per user/test; drafts/non-qualifying attempts do not consume it.
- [ ] Identical retries must return the canonical result; conflicting reuse of attempt IDs must fail.
- [ ] Ensure a failed ranking query never writes rank 0. Use an explicit unavailable/error state.
- [ ] Keep exam IDs isolated. NEET attempts must never contribute to JEE or another exam.
- [ ] Reconcile score calculation, rank materialization and percentile against one consistent snapshot.
- [ ] Verify ties, competition ranking, percentile denominator, zero candidates and population changes.

### R3. Ranking policy and presentation
- [ ] Full Test weight 1.0x; Part/Chapter/Topic Test weight 0.2x, after verified max-mark normalization.
- [ ] Top 100, My Rank, user rank, exam-wise boards and applicable Class 11/Class 12/Dropper cohorts.
- [ ] Class rank must not be labelled official AIR. Never imply NTA/government rank.
- [ ] Keep XP separate from ranking score. Validate Rookie 0–500, Scholar 501–2,000, Elite 2,001–5,000, Master 5,001–10,000, Conqueror above 10,000 only as XP league thresholds, not AIR.
- [ ] Profile visibility, banned/suspended eligibility and private-user exclusion must be server-enforced.
- [ ] Next-rank target must use the exact same score/tie rules as the leaderboard.

### R4. Backend, privacy and migration
- [ ] Review transaction boundaries, batch limits, idempotency and race conditions.
- [ ] Verify Firestore rules deny client writes to authoritative rank, score, XP, eligibility and answer-key fields.
- [ ] Verify legacy leaderboard reads are visibility constrained; do not leak private or legacy rows with missing visibility.
- [ ] Audit historical backfill: trusted source test, private answer key, ownership, timestamp/token order, first qualifying attempt, score/max reconciliation and no ungraded questions.
- [ ] Dry-run and test 450+ writes, pagination, resume, duplicate prevention and rollback before any migration. No production backfill now.
- [ ] Keep raw legacy marks separate from V2 normalized score.

### R5. Integration boundary with universal scoring
- [ ] Consume only canonical exam/stage/paper scoring results from the scoring engine.
- [ ] Do not invent marking rules or use a default exam policy.
- [ ] Review overlap in `ExamScoringEngine.kt`, `backend_functions/examScoring.js`, `NEETViewModel.kt`, `MainActivity.kt`, `firestore.rules`, `firestore.indexes.json` and backend exports before porting.
- [ ] Preserve ranking-specific code while accepting newer main-side fail-closed fixes after review.

### R6. Verification
- [ ] Backend unit and callable tests, Firestore emulator rules tests, concurrent-attempt tests, migration dry-run tests.
- [ ] Android ranking UI/ViewModel tests and full Android unit suite.
- [ ] Verify exact test counts and current-head Debug APK build.
- [ ] Record current CI run IDs and evidence. No old CI result may close a current blocker.

## Cross-branch caution

The current main branch has newer exam planner, scoring, database, and fail-closed subject-routing changes. Ranking work must consume the approved scoring contract after file-by-file reconciliation; do not overwrite those newer safety fixes with this branch's older snapshots.

## Status

All phases above remain **OPEN / NEEDS CURRENT-HEAD VERIFICATION** unless a later evidence entry is added below.

### Evidence log
| Date | Commit | Work/tests | Result | Remaining |
|---|---|---|---|---|
| 2026-11-12 | `a250f54a54035c21363a190df39dcd041d7aa55e` | Branch comparison and plan creation | Audit snapshot only; no code tests run in this audit | All R1–R6 gates |
