# AK TEST SERIES — Ranking Engine + Universal Exam Scoring Integration Plan

> **Purpose:** This file is the single source of truth for completing the Ranking Engine V2 and Universal Exam Scoring integration. Every future development session must read this file first, identify the first incomplete phase, inspect the current repository state, complete only the next safe unit of work, update this file with evidence, and then stop. Do not restart from guesses or silently skip phases.

**Repository:** `abhi952817-sudo/AK-TEST-SERIES-`  
**Integration branch:** `feature/ranking-universal-integration-v1`  
**Base branch used when created:** `feature/ranking-engine-v2-2026-11-06`  
**Universal source branch:** `feature/universal-exam-scoring-v1-2026-11-06`  
**Protected branch:** `main`

---

## 0. Non-negotiable development rules

1. Never commit directly to `main`. Never merge to `main`, deploy Firebase Functions, deploy Firestore/Storage rules, or run a production data migration without explicit owner approval.
2. Work only on `feature/ranking-universal-integration-v1` unless this file is deliberately revised after an audit.
3. Preserve working features, UI, user data, test history, attempts, XP, and existing legitimate question content. Do not perform broad rewrites.
4. Do not delete old ranking code, old branches, legacy collections, or old leaderboard data while implementation is in progress.
5. Do not invent questions, answers, explanations, exam patterns, official status, PYQ provenance, marks, or year-specific rules.
6. If source evidence is missing, mark the configuration `UNVERIFIED` / `NEEDS_REVIEW` and prevent it from being represented as official or ranking-eligible.
7. Every code change must have tests. Run focused tests first, then the full relevant CI. Never report a test as passed unless its actual output confirms it.
8. Before every phase, refresh branch heads and inspect current files. This plan records an audit snapshot, not a guarantee that remote code has not changed since.
9. Never resolve conflicts by blindly choosing “ours” or “theirs”. Compare behavior and preserve both required behaviors.
10. Any destructive data operation requires a dry run, a verified backup, a rollback plan, and explicit owner approval.
11. Keep this file updated after each phase with: status, files changed, commit SHA, tests run/results, remaining issues, and next phase.
12. If an unexpected P0/P1 security or data-loss issue appears, stop normal phase progression and fix/verify it first.

## 1. Product requirements to preserve

### 1.1 Exam-wise ranking
- Separate ranking identities and leaderboards by canonical exam ID, not display labels.
- At minimum preserve planned NEET, JEE, Class 11, Class 12, and Dropper views where eligibility applies.
- Class-specific standings must be labelled **Class Rank**, not AIR.
- Platform rank must never be presented as NTA/official government AIR.
- At 1,000 eligible active participants, the product may label the platform result **AK AIR within AK Test Series** only if the threshold and eligibility are verified. Otherwise show **Provisional Rank**.

### 1.2 Ranking score and qualifying attempts
- Full Test weight: **1.0x**.
- Part / Chapter / Topic Test weight: **0.2x**.
- Normalize each score against that test's verified maximum marks to a 10,000-point scale before applying the weight.
- Only the first valid qualifying attempt for a user/test contributes to ranking.
- A draft or non-qualifying attempt must not consume the first qualifying slot.
- Idempotent retries must return the prior canonical result and must not double count score, XP, streak, or attempts.
- Keep legacy raw-mark ranking data separate. Never reinterpret old raw marks as V2 normalized points without an explicit migration and validation.

### 1.3 Boards and profile
- Exam-wise leaderboard and user rank.
- Top 100 and My Rank.
- Competition rank with ties handled consistently.
- Percentile with documented denominator/tie policy.
- 15-day and monthly ranking cohorts; All Time where specified.
- Class 11 / Class 12 / Dropper cohort filters.
- Profile visibility preference respected.
- Banned/suspended accounts excluded according to one authoritative server policy.
- Next Rank Target must use the same ranking score and tie policy as the leaderboard.

### 1.4 XP, leagues, and streaks
- Keep XP separate from AIR/ranking score. XP rewards participation/learning; it must not silently alter AIR.
- Existing provisional XP rule in code: 10 XP for first public test completion + 2 XP per correct answer, capped at 370 XP per test. Treat as a policy requiring owner confirmation before production activation.
- League thresholds in the existing implementation: Rookie 0–500; Scholar 501–2,000; Elite 2,001–5,000; Master 5,001–10,000; Conqueror above 10,000.
- This Week and All Time XP boards.
- IST-based daily activity streak.
- XP must be ledger-based, server-authoritative, idempotent, and auditable.
- Historical XP/streak backfill must be a separately approved, resumable migration with dry-run and rollback.

### 1.5 Universal exam scoring
- One canonical exam/stage/paper/section/year configuration must drive Android and backend scoring. Avoid two independent hardcoded policy registries.
- Support single-correct MCQ, exact-set MSQ/MCQ_MULTIPLE, numerical/TITA, decimal marks, ungraded/invalid answer keys, optional sections, section caps, negative marking, and exam-specific policies.
- Add exam-specific support for partial marking, numerical tolerance, dropped/bonus questions, and question-specific marks where the official pattern requires it.
- Never assume all exams use the same marking scheme.
- Admin must explicitly choose exam, year, stage, paper, and applicable section/configuration. Do not silently select the first paper/stage.

---

## 2. Initial repository snapshot (must refresh before implementation)

At the time this plan was created, GitHub comparison reported:

| Comparison | Observed result |
|---|---:|
| Ranking branch vs main | 92 commits ahead, 0 behind |
| Universal branch vs main | 133 commits ahead, 0 behind |
| Universal branch compared with Ranking branch | 45 ahead, 4 behind; branches diverged |
| Ranking PR | PR #1, draft |
| Universal PR | PR #2, open |

Files that overlap or have related changes include:
- `backend_functions/index.js`
- `backend_functions/examScoring.js`
- `backend_functions/examScoring.test.js`
- `backend_functions/rankingEngine.js`
- `backend_functions/rankingEngine.test.js`
- `app/src/main/java/Com/ak/testseries/viewmodel/NEETViewModel.kt`
- `app/src/main/java/Com/ak/testseries/MainActivity.kt`
- `firestore.rules`
- `firestore.indexes.json`
- `.github/workflows/ranking-v2-validation.yml`
- `app/MASTER_IMPLEMENTATION_TRACKER.md`

Universal-branch-specific changes observed:
- `app/src/main/java/Com/ak/testseries/exam/ExamScoringEngine.kt`
- `app/src/main/java/Com/ak/testseries/data/AppDatabase.kt`
- `app/src/main/java/Com/ak/testseries/data/Entities.kt`
- `app/src/test/java/Com/ak/testseries/data/AppDatabaseMigration20To21Test.kt`
- `app/src/test/java/Com/ak/testseries/exam/ExamScoringEngineTest.kt`
- `app/schemas/Com.ak.testseries.data.AppDatabase/21.json`
- backup and repository adapters.

**Important:** Universal branch is not a clean fast-forward child of Ranking branch. Do not merge PR #2 wholesale into PR #1 or overwrite shared files. Port and reconcile behavior in the integration branch.



### 2.1 Live baseline refresh — 2026-11-06 (verified)

**Integration branch head at this refresh:** commit `f6fc002aeb70644f7a436ad475dbe98cf7b13122` (the integration branch is exactly 1 commit ahead of Ranking and 0 behind; the additional commit is this README integration plan). The README blob SHA before this update was `d6878711b02e28ce1b380da5329b87e6ec93e97c`.

| Ref | Current comparison | Verified detail |
|---|---:|---|
| `main` → Ranking branch | 92 ahead / 0 behind | Ranking PR #1 head `a250f54a54035c21363a190df39dcd041d7aa55e` |
| `main` → Universal branch | 133 ahead / 0 behind | Universal PR #2 head `9b105e9e7c614d615becb99a72b3dc5e50799e05` |
| Ranking → Universal | 45 ahead / 4 behind | Diverged; merge base `2b7b1e81f915c45aff6ccbcd63bde709642df3d7` |
| Ranking → Integration | 1 ahead / 0 behind | Integration is a safe child of Ranking; no source-code port has been made yet |
| `main` → Integration | 93 ahead / 0 behind | `main` remains untouched |

**PR status:** PR #1 remains OPEN and DRAFT, base `main`, head Ranking. PR #2 remains OPEN and NOT DRAFT, base Ranking, head Universal; GitHub reports it mergeable. Neither PR is merged.

**CI evidence (latest runs discoverable for the two PR heads):**
- Ranking head run `42176761965` (workflow run 138): backend unit/syntax PASS; Firestore Emulator rules PASS; Android validation FAIL.
- Universal head run `42176925903` (workflow run 143): backend unit/syntax PASS; Firestore Emulator rules PASS; Android validation FAIL.
- Both Android jobs successfully compiled and assembled the debug APK, then failed in ` :app:testDebugUnitTest `: `CrashResilienceTest > classMethod FAILED`, `UnsupportedOperationException at DefaultSdkProvider.java:170`. Ranking run reports 293 tests, 1 failed; Universal run reports 299 tests, 1 failed. This is a real CI blocker and must be root-caused, not waived. The available job log does not establish the cause yet.
- Ranking head also has a separate Android CI run `42176761901` with conclusion SUCCESS, but it does not replace the failing full Ranking V2 Validation job.
- No workflow run was returned for the integration README commit itself.

**Phase 0 exit gate:** PASS for branch/PR/CI baseline capture. CI is not green; the Android test failure is carried forward as a blocking issue. No application source files, Firebase data, rules deployment, or production configuration were changed during this refresh.

### 2.2 Phase 1 overlap audit — initial verified findings (not yet complete)

- PR #2 changes 15 files: validation workflow, implementation tracker, Room schema 21, MainActivity, AppDatabase, Entities, backup adapter, Firebase repository adapter, Android scoring engine, NEETViewModel, Room migration test, Android scoring tests, backend scoring engine/tests, and backend index.
- The reverse branch comparison (Universal → Ranking) confirms 4 commits by which Universal is behind; the reported file-level difference is only `.github/workflows/ranking-v2-validation.yml`. The 45 Universal-ahead commits touch the 15 files listed by PR #2. Do not treat commit counts as proof that the code changes are safe; shared-file behavior still requires semantic review.
- `backend_functions/rankingEngine.js` is byte-identical on Ranking and Universal (blob SHA `d7a1101b0c5532726f7cd305d46edc97933c04f0`). Preserve this Ranking Engine implementation.
- Shared files with different blobs include `backend_functions/index.js` (Ranking `f75c514688abe8fd61905e543d1cdbf932438618`; Universal `b85cef941351700ea6d630eee9598a12dba2a073`) and `NEETViewModel.kt` (Ranking `06913e4c928eefd8e405f85bf407fea0f0ef330f`; Universal `ecb43bab67c69a40046068f96e3cb1b62f09ef28`). These require hunk-level reconciliation, not wholesale replacement.
- Universal adds exact-set MSQ grading, stricter numeric validation, explicit ungraded counts, fractional score persistence, and Room 20→21 migration. The migration adds `ungradedCount` with default 0 and preserves existing attempt rows in its dedicated test.
- Universal still retains `fallbackToDestructiveMigration()` in `AppDatabase.kt`; migration safety is therefore not complete.
- Registry inspection found year-sensitive configuration values that cannot be treated as verified current patterns. Examples include NEET entries showing 200 minutes and optional Section B, and JEE Main entries showing 20 MCQ + 10 numerical with 5 attempted. These must be marked unverified until exact exam-year official sources are attached and validated. Do not silently overwrite them based on memory.
- The Universal branch contains an Admin/UI timer field and test-duration data, but this audit has not yet proved that every exam's timer, section timing, and auto-submit behavior are driven by the selected year/stage/paper configuration. Timer parity remains an explicit audit item.

**Phase 1 status:** IN PROGRESS. Remaining: inspect the exact divergent commit history and hunk-level shared-file behavior; produce the final keep/port/reconcile matrix before Phase 2 code edits.




### 2.3 File-by-file port/reconciliation matrix — working audit

| File | Ranking-side decision | Universal-side decision | Integration action |
|---|---|---|---|
| `.github/workflows/ranking-v2-validation.yml` | Keep existing Ranking checks | Keep added callable-validation test and Room-schema artifact upload | Reconcile triggers so the integration branch and its PR run all backend, Firestore, Android, and schema checks |
| `backend_functions/rankingEngine.js` | KEEP | Blob identical to Ranking | Do not replace; preserve ranking calculation and policy code |
| `backend_functions/index.js` | KEEP Ranking callables, eligibility, idempotency, XP and migration flow | PORT decimal persistence and ungradedCount fields from scoring result | Hunk-level merge; add tests proving Ranking Engine receives only server-evaluated score and retains first-attempt lock |
| `backend_functions/examScoring.js` | Keep supported existing exam behavior | PORT strict finite-number validation, question-type-aware exact-set MSQ, ungraded result and fractional marks | Then refactor duplicated static exam-name rules toward one versioned canonical policy; unsupported/unverified policy must fail closed |
| `backend_functions/examScoring.test.js` | Preserve existing tests | PORT regression cases for MSQ, decimal, ungraded, and optional caps | Remove duplicate test cases/names; add missing partial-credit, tolerance, dropped/bonus, year-policy and parity vectors |
| `app/src/main/java/Com/ak/testseries/exam/ExamScoringEngine.kt` | Preserve Android scoring entry points | PORT MSQ, finite numeric validation, ungraded and optional maximum-mark corrections | Reconcile with canonical policy; no fallback to an unrelated first paper |
| `app/src/main/java/Com/ak/testseries/viewmodel/NEETViewModel.kt` | Preserve Ranking V2 state, APIs and UI flows | PORT ungraded/fractional attempt propagation only after reviewing every publish/share path | Manually reconcile; fix answer-key leakage and automatic official status before any public publish route is considered safe |
| `app/src/main/java/Com/ak/testseries/MainActivity.kt` | Preserve Ranking UI/navigation | PORT ungraded result notice and numerical input support where absent | Keep current design; verify timer, auto-submit, section caps, and exact selected stage/paper/year; do not copy whole file |
| `app/src/main/java/Com/ak/testseries/data/Entities.kt` | Preserve current entity fields | PORT `ungradedCount` with default 0 and fractional Double score fields | Additive schema-only change; preserve old attempt rows |
| `app/src/main/java/Com/ak/testseries/data/AppDatabase.kt` | Preserve migrations 15→16→17→18→19→20 | PORT migration 20→21 | Add migration to existing chain; do not retain destructive fallback as a substitute for migration coverage |
| `app/schemas/Com.ak.testseries.data.AppDatabase/21.json` | No schema 21 in Ranking snapshot | PORT generated schema snapshot | Verify against KSP-generated schema from CI, not hand-edited JSON |
| `app/src/test/java/Com/ak/testseries/data/AppDatabaseMigration20To21Test.kt` | Preserve existing migration tests | PORT real SQLite data-preservation test | Extend to supported upgrade paths and fractional-score preservation |
| `app/src/test/java/Com/ak/testseries/exam/ExamScoringEngineTest.kt` | Preserve current test coverage | PORT MSQ/numeric/ungraded/optional-cap tests | Add cross-engine golden vectors and edge cases |
| `app/src/main/java/Com/ak/testseries/data/backup/AccountDataBackupAdapter.kt` | Preserve backup format compatibility | PORT ungradedCount export/import with default 0 for old backups | Add round-trip tests; retain fractional score precision |
| `app/src/main/java/Com/ak/testseries/data/repository/FirebaseUserRepositoryImpl.kt` | Preserve repository behavior | PORT ungradedCount restore | Verify old documents default safely and new attempts preserve decimals |
| `app/MASTER_IMPLEMENTATION_TRACKER.md` | Preserve existing tracker history | Port Universal audit notes | Append integration status; do not overwrite historical work |

**Additional code-level blockers confirmed in the integration baseline:**
- `UniversalPdfPipeline.kt` still creates synthetic fallback question text, fabricated options, answer keys and explanations when neither Gemini nor real PDF/catalog questions are available. This is a P0 content-integrity blocker; the comment claiming “Zero dummy data” does not match the executable fallback code.
- Community sharing serialization in `NEETViewModel.kt` includes `correctAnswer`, `explanation`, and Hindi explanation fields in the public question map. Every publishing path must be audited and changed to a fail-closed public/private split before any release.
- `UniversalExamRegistry.kt` currently has NEET configuration marked `verified = true` while storing 200-minute / optional Section B pattern values; JEE Main stores an old MCQ/Numerical optional-section pattern. The configuration model has no adequate year-specific official-source metadata. Mark these unverified until the official year/paper source is recorded.
- Android and Node currently contain parallel exam-name scoring rules. Android duration fallback uses the first registered stage/paper and heuristic seconds-per-question; it is not proof of a verified year/stage/paper timer. The UI does have a countdown and numerical keypad, but complete universal timer-policy parity is not established.
- Current Room baseline is version 20 and still calls `fallbackToDestructiveMigration()`; Universal's version 21 migration is a required additive port, not a reason to erase the database.
- Static backend marking rules include defaults for many exam names, but this is not a complete verified year/stage/paper configuration registry. JEE Advanced partial marking, question-specific numerical tolerance, and dropped/bonus policies are not yet proven implemented end-to-end.

**Phase 1 exit gate:** the file matrix and high-risk overlap behavior are now documented. Exact divergent commit-level attribution is still being checked; Phase 1 remains IN PROGRESS until that last check is complete. No source-code files have been changed on the integration branch yet.


---

## 3. Known audit findings to verify and fix

### P0 — public answer-key / explanation exposure
Previously inspected `NEETViewModel.kt` universal publish path wrote question JSON containing fields such as `correctAnswer`, `explanationEn`, and `explanationHi` into public `Tests_Data`. The generic answer-separation helper was also incomplete for explanation variants and appeared fail-open on parse errors.

**Required:** one shared secure publish service for every publishing path. Public question payload must exclude every answer/key/rationale/explanation alias. Store verified answer keys and explanations only in protected server-side storage. Parse/validation errors must fail closed. Add Firestore Emulator tests proving ordinary students cannot read private keys.

### P0 — automatic Official/Published status
Previously inspected `publishUniversalTest()` assigned official/verified/published flags and an official-pattern batch label without adequate source verification.

**Required:** default imported/generated tests to Draft or Needs Review. Official/PYQ status requires source metadata and authorized Admin verification. Never infer official status from the exam name or AI output.

### P0 — synthetic fallback questions
Previously inspected `UniversalPdfPipeline.kt` generated generic placeholder “Practice Problem” / “Statement I” questions when extraction produced no usable questions.

**Required:** remove synthetic question fallback. Return a clear extraction failure or `NEEDS_REVIEW`; do not publish or count a test with fabricated questions. Preserve legitimate authored practice questions.

### P0 — exam patterns and provenance
Previously inspected registry marked 25 exam IDs verified without adequate per-year official-source evidence. NEET and JEE patterns included outdated values.

**Required:** each configuration must include exam ID, year, stage, paper, sections, question counts, duration, marks, negative marking, optional-attempt rules, source URL/document reference, verification date, and reviewer/status. Mark unsupported years UNVERIFIED. Do not extrapolate one year's pattern to another.

Known example requiring correction/verification: NEET UG 2026 pattern was 180 compulsory questions, 180 minutes, Physics 45, Chemistry 45, Biology 90, +4/-1. Recheck official bulletin before changing code. JEE Main 2026 Paper 1 pattern also needs official-source verification; do not reuse old 90/75 pattern.

### P1 — MSQ validation and scoring parity
Previously inspected validation rejected comma-separated answers without consistently respecting question type. Universal branch added MSQ/decimal/ungraded support.

**Required:** exact-set MSQ grading; retain correct single-select compatibility; reject malformed/duplicate/empty/non-finite keys; verify Android and Node outputs against shared golden vectors.

### P1 — ranking/scoring contract
Ranking must consume only the server-verified canonical scoring result. Define and validate a stable result contract containing user/test/exam/stage/paper/year IDs, score, max score, raw score, ungraded count, eligibility, attempt ID, scoring version, ranking policy version, and timestamps. Never trust client-submitted score, maximum marks, rank, XP, or eligibility.

### P1 — stages and papers
Previously inspected registry had only 25 stage configurations and 28 paper configurations for 25 exam IDs, with known omissions such as UPSC CSE CSAT/Mains/interview, SSC CGL Tier 2, banking prelims/mains/sectional rules, CTET Paper 1/2, UGC NET Paper 1/subject paper, and incomplete GATE branches.

**Required:** inventory every supported exam. Add only source-verified stages/papers. Admin UI must select the exact stage/paper/year. Do not silently default to first stage/paper.

### P1 — Room migration/data safety
Universal branch uses Room schema version 21 and includes migrations 15→16→17→18→19→20→21 plus a 20→21 data-preservation test. `fallbackToDestructiveMigration()` was still present in the inspected code.

**Required:** inspect all supported historical schemas and generated KSP schema snapshots; add migration tests for every supported path and data-integrity assertions. Remove destructive fallback only after proving all supported upgrades and a safe unsupported-version recovery/export strategy. Never delete user database data as a shortcut.

### P1 — Storage permissions
Earlier audit found authenticated users could create test-question image paths under Storage rules. Restrict upload to authorized Admin/test owner and test with Storage Emulator. Review the alternate branch's proposed storage rules/tests before selectively porting.

### P2 — ranking policy and migration
- Confirm whether cumulative weighted score is intended for AIR or whether a fixed/average scoring policy is desired. Do not change policy silently.
- Confirm XP award policy before production.
- Add Admin policy visibility/edit controls with server-side validation.
- Add ranking migration dry-run, progress, resumability, audit log, rollback strategy, and post-migration reconciliation.
- Add historical XP/streak backfill only after approval.
- Add rank snapshot/history browsing if retained in product scope.

---

## 4. Authoritative end-to-end flow

```text
Student selects exam/year/stage/paper/test
        |
        v
Server authenticates user and checks account status
        |
        v
Server loads published test + protected answer key
        |
        v
Universal Scoring Engine validates answers and exam policy
        |
        v
Canonical verified score/result (including ungraded count)
        |
        v
Qualifying-attempt and idempotency checks
        |
        +---- not qualifying ----> Save result, do not change AIR/XP eligibility
        |
        v
First valid qualifying attempt lock
        |
        v
Ranking Engine applies normalized score and test weight
        |
        +----> Exam leaderboard / User_Ranks / Top 100 / My Rank
        |
        +----> percentile / cohort / next-rank target
        |
        +----> separate XP ledger / weekly XP / streak / league
        |
        v
Student dashboard and attempt history
```

Scoring is authoritative for marks. Ranking is authoritative for rank aggregation. XP is a separate reward ledger. The client is presentation only.

---

## 5. Sequential implementation phases and exit gates

### Phase 0 — Refresh and baseline (FIRST)
- Refresh current main, Ranking, Universal, and integration branch heads.
- Verify this integration branch is still based on the intended Ranking head.
- Inspect PR status, CI status, and changed files.
- Re-fetch every critical source file listed above.
- Record exact commit SHAs and any changes since this snapshot.
- Do not modify code until baseline is recorded.

**Exit gate:** a current, reproducible branch/PR/CI comparison exists.

### Phase 1 — Integration design and overlap map
- Compare shared-file diffs, especially `index.js`, `examScoring.js`, `NEETViewModel.kt`, `MainActivity.kt`, rules, indexes, and workflow.
- Identify the 4 commits by which Universal branch is behind Ranking and 45 commits by which it diverged ahead; inspect commit contents, not just counts.
- Create a file-by-file port matrix: keep Ranking implementation, port Universal implementation, or manually reconcile.
- Confirm no required Ranking function is lost.

**Exit gate:** reviewed overlap matrix and deterministic integration order.

### Phase 2 — Backend scoring/ranking contract
- Reconcile `examScoring.js` and `rankingEngine.js`.
- Make server scoring output the only source of marks.
- Connect ranking only after successful canonical scoring.
- Preserve idempotency and first-valid-qualifying-attempt locking.
- Keep XP transaction independent and idempotent.
- Add tests for retry, conflicting retry, duplicate attempt, non-qualifying attempt, banned user, unpublished test, and cross-exam isolation.

**Exit gate:** backend tests prove scoring→eligibility→ranking→XP flow without duplicate writes.

### Phase 3 — Secure test publishing and answer separation
- Fix every publishing path, including `publishUniversalTest()`.
- Strip all answer and explanation aliases from public payloads.
- Save private keys and explanations in protected storage only.
- Fail closed on malformed JSON, invalid keys, or missing provenance.
- Require Admin approval for Official/PYQ labels.
- Add emulator rules tests for student/admin/owner access.

**Exit gate:** no public answer leak; all publish paths tested.

### Phase 4 — Real-question/PDF pipeline
- Remove synthetic fallback questions.
- Preserve valid source text and provenance.
- Validate extracted question count, options, question type, answer evidence, language fields, and duplicate IDs.
- Mark uncertain extraction Needs Review.
- Add regression tests for empty PDF, scanned PDF, malformed extraction, missing key, duplicate question, and mixed Hindi/English content.

**Exit gate:** extraction cannot silently invent or publish questions.

### Phase 5 — Canonical exam configuration
- Inventory all 25 currently registered exam IDs.
- Verify official year-wise pattern and sources.
- Add explicit stages/papers/sections and optional limits where evidence exists.
- Correct NEET/JEE outdated pattern only after checking official bulletins.
- Add stage/paper/year selection to Admin flow.
- Mark unverified patterns clearly and block Official/ranking eligibility where needed.

**Exit gate:** no configuration is labelled verified without source evidence; Android/backend consume same versioned policy.

### Phase 6 — Scoring completeness and parity
- Test MCQ, exact-set MSQ, numerical/TITA, decimals, partial marking, optional sections, negative marking, ungraded, tolerance, dropped/bonus policies.
- Build shared golden test vectors and compare Kotlin and Node results.
- Verify max marks calculation for optional sections and all section caps.
- Ensure ungraded count survives online/offline attempts, Firestore restore, backup export/import, and result UI.

**Exit gate:** Kotlin and backend parity tests pass for all supported question/policy types.

### Phase 7 — Android integration and UI
- Reconcile `NEETViewModel.kt` without overwriting ranking or scoring behavior.
- Reconcile `MainActivity.kt` navigation and screens.
- Preserve existing app UI/features unless explicitly required.
- Ensure offline attempts are pending/unverified until server confirmation and cannot gain authoritative AIR/XP prematurely.
- Display exam rank, class rank, provisional/AK AIR status, percentile, Top 100, My Rank, XP, streak, and league from trusted server data.

**Exit gate:** online/offline result and ranking states are consistent; no client-trusted rank or XP.

### Phase 8 — Firestore/Storage security and indexes
- Merge rules semantically, not by blind overwrite.
- Protect private answer keys, attempts, ranking collections, XP ledger, migration controls, and user visibility.
- Enforce server-only writes to authoritative ranking and XP fields.
- Restrict test image uploads to Admin/authorized owner.
- Validate all required composite indexes.
- Run Firestore and Storage Emulator suites.

**Exit gate:** emulator tests pass for allowed and denied paths; no public writes to authoritative collections.

### Phase 9 — Room, backup, and data preservation
- Verify schema 21 against generated KSP schema.
- Test each supported migration path and data preservation.
- Verify backup/restore retains fractional scores and ungraded counts.
- Audit `emergencyResetDatabase()` and destructive fallback behavior.
- Do not remove fallback or alter recovery behavior until safe supported-version handling is proven.
- Add backup integrity/checksum tests where supported.

**Exit gate:** migration and backup tests prove existing attempts/test data survive.

### Phase 10 — Ranking correctness and fairness
- Test ties, competition ranks, percentile, zero candidates, 1,000 threshold, hidden profiles, banned users, class cohorts, exam isolation, date boundaries in IST, and Top 100.
- Confirm cumulative score vs average/fixed test-set policy with owner before locking it.
- Ensure Full/Part weights are derived from trusted test metadata and cannot be client-forged.
- Ensure provisional rank is shown below threshold.

**Exit gate:** ranking policy is documented and deterministic; edge-case tests pass.

### Phase 11 — Admin migration and historical data
- Build/verify dry-run report before any writes.
- Show counts of eligible users, skipped users, invalid attempts, duplicates, missing metadata, and expected ranking changes.
- Add resumable cursor, audit logs, checkpoints, and rollback/rebuild plan.
- Backfill XP/streak only after separate approval.
- Reconcile aggregate leaderboard entries against source attempts.

**Exit gate:** dry-run is reviewed and rollback is proven in non-production test data.

### Phase 12 — Full regression and release candidate
- Run backend syntax and unit tests.
- Run Firestore Emulator rules tests.
- Run Storage Emulator tests.
- Run Android unit tests and Room migration tests.
- Run Android debug build.
- Verify workflow artifacts and inspect failures; do not dismiss failures without root-cause evidence.
- Run a complete test submission through the integrated flow in a non-production environment.
- Update this README with final test evidence and known limitations.

**Exit gate:** all required CI jobs pass; no unresolved P0/P1 issue; owner reviews release candidate.

### Phase 13 — Review, merge, and retirement (LAST, approval required)
- Open/update a PR from integration branch to the agreed target branch.
- Keep main unchanged until explicit owner approval.
- Merge only after review, full CI, data/security sign-off, and explicit approval.
- Deploy rules/functions only as a separate approved release step.
- Keep the old Ranking and Universal branches available until the integrated system is verified after merge.
- Only then may obsolete development branches or duplicate legacy code be retired, after confirming no references, no rollback need, and no production data dependency.
- **Do not delete the Ranking Engine itself.** It remains a required component of the completed product. Retire only superseded duplicate/legacy implementations or old branches after proof and approval.

**Exit gate:** integrated system is stable, recoverable, and verified after release.

---

## 6. Required test matrix

| Area | Required cases |
|---|---|
| Score validation | valid/invalid IDs, malformed payload, non-finite values |
| MCQ | correct, incorrect, skipped, alternate legacy key |
| MSQ | exact match, missing option, extra option, duplicate token, order variation |
| Numerical | integer, decimal, tolerance boundary, invalid value |
| Marking | positive, negative, zero negative, partial credit, dropped/bonus |
| Sections | optional cap, attempted cap, section max marks |
| Ungraded | missing/unverified key, user attempted, user skipped |
| Ranking | Full 1.0x, Part 0.2x, first qualifying attempt, retries, ties |
| Isolation | NEET cannot enter JEE board; class rank is not AIR |
| Eligibility | draft, pending, rejected, unpublished, banned, private profile |
| XP | duplicate retry, weekly boundary, daily IST streak, league boundary |
| Security | student cannot read answer keys or write rank/XP |
| Persistence | Room migration, Firestore restore, backup export/import |
| PDF | empty/scanned/partial/malformed extraction, no synthetic fallback |
| Admin | role checks, stage/paper/year selection, migration dry-run |

---

## 7. Definition of done

Development is not complete until all of these are true:

- [ ] One canonical scoring policy/version drives Android and backend.
- [ ] Ranking consumes only server-verified results.
- [ ] No synthetic questions can be published.
- [ ] No answer keys/explanations leak into public test documents.
- [ ] Official/PYQ labels require verified provenance and Admin approval.
- [ ] Supported exam-year-stage-paper configurations are source-verified.
- [ ] Ranking boards, class cohorts, Top 100, My Rank, percentile, XP, streak, and leagues pass tests.
- [ ] First-valid-attempt and idempotency rules prevent duplicate ranking/XP.
- [ ] Firestore and Storage security emulator tests pass.
- [ ] Room and backup tests prove data preservation.
- [ ] Full CI passes on the integration branch.
- [ ] Dry-run and rollback plan are reviewed before historical migration.
- [ ] No P0/P1 issue remains open.
- [ ] Owner approves the PR and any production rollout.

---

## 8. Progress log — update this after every work session

| Phase | Status | Commit SHA | Tests/evidence | Notes |
|---|---|---|---|---|
| 0 Baseline refresh | COMPLETE | `f6fc002aeb70644f7a436ad475dbe98cf7b13122` | Branch/PR comparisons; PR #1/#2 CI jobs and Android logs inspected | Android unit-test failure remains a release blocker |
| 1 Overlap map | IN PROGRESS | — | PR #2 15-file patch; reverse compare; shared blob SHAs | Complete commit/hunk matrix before Phase 2 |
| 2 Backend contract | NOT STARTED | — | — | Scoring → ranking → XP |
| 3 Secure publishing | NOT STARTED | — | — | P0 answer leak |
| 4 PDF pipeline | NOT STARTED | — | — | P0 synthetic fallback |
| 5 Exam configuration | NOT STARTED | — | — | Year/stage/paper provenance |
| 6 Scoring parity | NOT STARTED | — | — | Kotlin/Node golden vectors |
| 7 Android integration | NOT STARTED | — | — | ViewModel/UI/offline |
| 8 Security/indexes | NOT STARTED | — | — | Firestore + Storage |
| 9 Room/backup | NOT STARTED | — | — | Preserve user data |
| 10 Ranking fairness | NOT STARTED | — | — | Policy decision + edge cases |
| 11 Admin migration | NOT STARTED | — | — | Dry-run/rollback |
| 12 Full regression | NOT STARTED | — | — | Full CI |
| 13 Release/retirement | BLOCKED | — | — | Explicit approval required |

### Session handoff rule
At the start of every later session:
1. Read this entire file from the integration branch.
2. Refresh GitHub branch heads and current CI.
3. Check the progress log and repository diff.
4. Resume from the earliest incomplete phase.
5. Complete one phase or a clearly bounded safe subtask.
6. Update this file in the same branch with actual evidence.
7. Do not claim completion without tests and commit evidence.
8. Stop and report what changed, what passed/failed, and the exact next step.

**User's future instruction may be:** “Integration README ke hisaab se agla pending phase complete karo.” Treat this file as the authoritative sequence, but always reconcile it with the live repository before editing.
