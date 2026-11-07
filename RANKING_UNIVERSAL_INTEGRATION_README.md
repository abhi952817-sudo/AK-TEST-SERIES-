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




### 2.4 Phase 1 closeout and Phase 2 backend port — 2026-11-06

**Phase 1 closeout:** The 15-file decision matrix above is the approved integration map. Reverse compare confirms Universal is 4 commits behind Ranking and the only reported behind-side file delta is the validation workflow; the workflow patch has been inspected. The GitHub connector's compare response exposes aggregate commit counts and file-level changes, not a complete list of individual commit SHAs, so no individual SHA list is claimed. This is recorded as a tooling limitation, not a reason to merge either branch wholesale.

**Phase 1 status: COMPLETE.** Ranking Engine V2 code remains the source of truth; the Universal branch is a selective source for scoring, decimal, ungraded and migration features.

**Phase 2 implementation started on integration branch:**
- Replaced `backend_functions/examScoring.js` with the reviewed Universal scoring revision. It adds strict finite-number validation, question-type-aware exact-set MSQ grading, explicit ungraded counts and preserves the existing exam marking scheme entry points.
- Replaced `backend_functions/examScoring.test.js` with the Universal regression suite and removed one duplicated ungraded-answer test case found in the source branch.
- Reconciled `backend_functions/index.js` with the Universal revision. Verified by content comparison that Ranking-specific markers remain present: `calculateWeightedRankingScore`, `firstAttemptLockRef`, `rebuildRankingV2Batch`, and `normalizedRankingPoints`. The port preserves those Ranking flows while persisting decimal score/maxScore values and `ungradedCount` rather than rounding/truncating them.
- Updated the validation workflow to run on pushes to and pull requests targeting `feature/ranking-universal-integration-v1`.

**Commits created on integration branch:**
- Scoring engine: `95bcf625ab810d86960260c6ce01733054aca9d5`
- Scoring regression tests: `e906827af5656d10066a117e83022034b910d1f2`
- Backend callable integration: `35618ae558ff99fa2ac3dbc1984e5eaa868ca92b`
- CI trigger update: `2e315cf398c9b29f77f1ad8a9913733cf0caa132`

**Validation status at the 2026-11-07 refresh:** The integration branch now has a completed green workflow run #183 (`42217629328`) on commit `01eca516a017794cb0f435916adc4c56d84391fa`: backend syntax/unit tests PASS, Firestore Emulator tests PASS, and Android unit tests + debug build PASS. This resolves the earlier observed CrashResilienceTest CI failure for that commit; it must not be treated as proof that every later commit is green.

**Additional Phase 2 correctness fix (2026-11-07):**
- Found a JavaScript truthiness bug in evaluateTestAttempt(): numeric answer value `0` and numeric answer key `0` could be converted to empty strings and incorrectly counted as unanswered/ungraded.
- Fixed null/undefined checks and property-presence handling in `backend_functions/examScoring.js`.
- Added a GATE numerical-zero regression case in `backend_functions/examScoring.test.js`.
- Scoring fix commit: `c48900ebe043f4549e96348b81ff0738f8ae92db`.
- Regression test commit: `cd714bbc3c044f7e451db5798c7a20cfbeccc2b4`.
- New integration workflow run #187 (`42321925510`) was observed IN PROGRESS at the time of this update. The new regression is not marked PASS until that run completes.

**Live branch snapshot — 2026-11-07:**
- Integration head before this README update: `cd714bbc3c044f7e451db5798c7a20cfbeccc2b4`.
- Integration vs `main`: 123 commits ahead, 0 behind.
- Integration vs Ranking branch: 31 commits ahead, 0 behind.
- Integration vs Universal branch: diverged, 35 commits ahead and 45 behind; no wholesale merge is permitted.
- Open Draft PR #3 targets the Ranking development branch, not `main`: https://github.com/abhi952817-sudo/AK-TEST-SERIES-/pull/3.
- No production deployment, production migration, or `main` branch write has occurred.

**Phase 2 status remains IN PROGRESS.** The current workflow validates backend and Android builds, but callable scoring→eligibility→ranking→XP behavior still needs dedicated contract/idempotency regression coverage. Static exam-name marking rules are not yet the canonical year/stage/paper configuration; that is a later Phase 5/6 requirement.


### 2.5 Live test answer-key exposure and auto-ranking audit — 2026-11-07

**P0 confirmed:** The LiveTests collection is readable by every authenticated student. The previous scheduleLiveTest() path wrote the full questionsJson directly to LiveTests, including correctAnswer and explanations. A second generated-live-test path also set isOfficial/countsTowardRanking from the live-mode toggle. This was an answer-key exposure and false-official/ranking risk.

**Changes committed on the integration branch:**
- Commit `98c9432d51e1f92c5d37e5539f5faf43f99f30c4`: scheduleLiveTest() now separates public question content from answer keys before writing LiveTests. The public document receives sanitized questionsJson; private answer keys are written in the same Firestore batch to LiveTests/{testId}/private_keys/answerKey.
- Commit `d6b0d9834962c87ac3e55e1170bc8abb9e5f5e76`: Firestore rules restrict LiveTests/{testId}/private_keys to Admin only.
- Commit `6878113be6b19fdca1b97e3757df9ee3e8074e12`: emulator regression test verifies students can read the public LiveTests document but cannot read its private answer key; Admin can read it.
- Commit `4a476d6b713593102ea38ccbe7660384e121faf5`: generated live mocks no longer automatically set isOfficial, isVerifiedCommunity, or countsTowardRanking. The scheduled-live creation path also explicitly keeps those flags false while retaining the published live-test experience.

**Important residual risk:** Existing LiveTests documents written before this fix may still contain answer keys in their public questionsJson field. The code change does not retroactively sanitize Firestore production documents. Phase 3 cannot be marked complete until a read-only inventory/dry-run identifies affected documents, a backup/rollback plan exists, and an explicitly approved migration or safe cleanup is completed. Do not run that production migration automatically.

**CI status:** Workflow #193 (`42322154778`) was observed running after the LiveTests sanitizer/rules/test changes: backend job PASS; Firestore Emulator and Android jobs were still in progress. The later generated-live flag correction commit `4a476d6b713593102ea38ccbe7660384e121faf5` was pushed after that run started and therefore needs a fresh CI result.

**Phase 3 status: IN PROGRESS.** New writes are being hardened; historical LiveTests documents and all other publishing paths still require audit.


### 2.6 Backend contract hardening — 2026-11-07

- Found a dangerous default in submitTestAttemptServerSide(): a test without targetExam/examId could be scored and grouped under NEET, or use its subject as an exam identity. This could contaminate the wrong exam leaderboard.
- Added resolveCanonicalExamIdentity() in backend_functions/examScoring.js. It prefers explicit examId, then targetExam, rejects missing identity, and rejects the non-specific ALL identity.
- Added regression tests for canonical NEET/JEE identity and missing/ALL rejection.
- Updated backend_functions/index.js to call the fail-closed resolver and return a failed-precondition error rather than guessing an exam.
- Commits: scoring helper `cf8c9cd4ec4d9522aa7929d3138e396f59a3d516`; tests `60add2776ee5ac2e3a2a5fdb2fe0e9a178ffb2a9`; callable integration `6afe7f40ca79b7ffa5169849eb8150b4f46a4f22`.
- This protects exam isolation, but does not yet make the year/stage/paper policy canonical. That remains Phase 5.

**CI evidence:** Workflow #193 (`42322154778`) has backend and Firestore jobs PASS; Android was still running when last checked. That workflow started before commits `4a476d6b...`, `cf8c9cd4...`, `60add277...`, and `6afe7f40...`; it does not validate those later commits. Fresh CI evidence is required before these latest edits are considered verified.

**Live branch snapshot after the latest code commits:** integration head `6afe7f40ca79b7ffa5169849eb8150b4f46a4f22`; 132 commits ahead of main, 40 ahead of Ranking, and diverged from Universal (44 ahead / 45 behind). Main remains untouched.


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
| 0 Baseline refresh | COMPLETE | `6afe7f40ca79b7ffa5169849eb8150b4f46a4f22` | 2026-11-07 live branch comparisons; PR #3; workflow #183 all 3 jobs PASS | Latest code commits are newer than workflow #193; fresh CI is required |
| 1 Overlap map | COMPLETE | `faf51cdb9778b3179dd3be47df12377688b264c9` | 15-file keep/port/reconcile matrix; branch compare and shared-file review | Individual commit SHA list not exposed by connector; only workflow file delta confirmed |
| 2 Backend contract | IN PROGRESS | `169983017b8fde9a2498c2d363cbeaa7d87339b3` | Hardened explicit exam IDs, official/public eligibility, strict Full/Part classification across metadata fields, answer-hash idempotency, private profiles, historical backfill filters, score bounds, and class cohort validation; CI #221 is stale and backend failed one pre-fix regex test | Fresh CI for current head; production callable/emulator transaction tests; audit XP ledger/streak and concurrent first-attempt behavior |
| 3 Secure publishing | IN PROGRESS | `4a476d6b713593102ea38ccbe7660384e121faf5` | LiveTests public/private answer separation, Admin-only private_keys rule, emulator test added; generated live mocks no longer auto-rank | Historical LiveTests docs may still leak keys; dry-run + approved migration and remaining publish-path audit required |
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

### 2.7 Android compile blocker found and corrected — 2026-11-07

- Fresh live CI run #201 (42322347443) on the previous integration head failed in :app:compileDebugKotlin at NEETViewModel.kt:6038: the generated-live TestEntity constructor was missing a comma after countsTowardRanking = false. Backend unit/syntax and Firestore Emulator jobs passed; Android did not reach unit tests or APK assembly.
- Corrected only the Kotlin named-argument separator on the integration branch. Commit: da98ae7aaf9acc93a8ebf475ad54d8948018391a.
- This is a source-level correction, not a passing validation result. A fresh workflow for the corrected head is still required before the fix is marked verified.
- Further Phase 2 audit: callableValidation.test.js is a hand-written simulation, not an invocation of the production callable/Firestore transaction. It still contains legacy default-to-NEET and legacy ranking-eligibility logic that diverges from the current callable. It cannot serve as proof of scoring→eligibility→ranking→XP transactional correctness. Replace/augment it with shared production policy helpers and emulator-backed transaction contract tests before closing Phase 2.
- No production data, Firebase deployment, rules deployment, or main branch was changed.
### 2.8 Ranking contract hardening — 2026-11-07

- `resolveRankingWeight` now returns no weight for ambiguous metadata instead of silently treating unknown/"Mock Test" as Full Test. Explicit Full/Complete/Grand or `isFullTest` resolves 1.0x; Part/Chapter/Topic/Subject or `isPartTest`/`isChapterTest` resolves 0.2x. Invalid explicit weights fail closed.
- `submitTestAttemptServerSide` now requires `countsTowardRanking=true`, `isOfficial=true`, and approved/public publication before a test can contribute to competitive AIR. Ambiguous ranking weight on an otherwise ranking-eligible paper is rejected.
- Ranking detail, exam leaderboard, and period leaderboard callables now require an explicit specific exam identity. Removed silent NEET defaults. Android ViewModel was inspected and already supplies `targetExam` to the rank/period callables.
- Historical ranking rebuild no longer infers exam identity from a subject label and skips historical attempts with missing/ambiguous exam or weight metadata rather than inventing points.
- Legacy and per-test leaderboard writes now anonymize private profiles and carry ban/eligibility metadata.
- Added stable canonical answer payload serialization + SHA-256 attempt hash so semantically identical retries with different JSON key order do not become false conflicting submissions. Added pure unit coverage.
- Commits in this bounded subtask: `2898c671e07ba8d383575c44811d95fedd567051`, `a293304177b23bcafc48b09dafe86e4f03f7e211`, `e295427244dcaf0d0324fc20a30e6ee46932a4ee`, `8c35348b6bc45037b077957f70c251b4f37d659`, `d52dc102b6797c2020683589d4df00985ca6805d`, `12bbfc7cbceb3fca6048d4c41743f125860c6581`, `1b85adb6042091c8f8b6ef760188032f10a3aed6`, `e9ca5e793eaf2b7046161b5b9603e1843ab031bc`, `e7db2d707e3f7b76779b2e97e56957d90b24e5a4`, `40e8b9a7cd24a6e7eb62e7c99cc38ebe5a384773`.
- Workflow #221 (`42324462584`) was queued for the earlier commit `1b85adb...`; backend and Firestore jobs were observed in progress, Android queued. This is not validation evidence for later canonical-hash commits. Fresh CI for the latest branch head is still required.
- No production deployment, data migration, Firestore rules deployment, or main-branch change was performed.
### 2.9 CI failure audit and exam alias isolation — 2026-11-07

- Workflow #221 backend unit tests: 73 passed / 1 failed. Failure was `resolveRankingWeight` classifying `Chapter Test` as null because regex word boundaries were accidentally double-escaped in source. Corrected regex source in commit `2cbdc7ff6a7053fb47af687464be6b1f1cf8ed21`; the CI run itself remains failed and does not validate the correction.
- Removed cross-exam alias collisions: generic `JEE` no longer maps to JEE Main; `NEET PG` no longer maps to NEET UG; generic `RRB`/`Railway` no longer maps to RRB NTPC. Their unsupported schemes now fail closed instead of scoring under another exam. Regression tests added in `caf86197796fa7f3583762150811ced4664aefc8`.
- Ranking API exam validation now checks the scoring engine's supported exam policy before reading a board, preventing arbitrary unknown IDs from returning misleading empty leaderboards. Commit `38985219336762884c81d20a8605fce14aba2a63`.
- The latest regex, alias, canonical answer hash, and API identity changes have not yet received a fresh CI run. Workflow #221 is pinned to the earlier commit and cannot be counted as validation of later changes.
- Workflow #221 Firestore rules job passed. Android validation was still in progress at last inspection; no conclusion recorded.
### 2.10 Historical migration safety and public-only XP/ranking — 2026-11-07

- `isEligibleHistoricalRankingTest` is now a shared pure policy helper. Historical V2 backfill accepts only a test document that exists and is explicitly `isOfficial=true`, `isPublic=true`, `countsTowardRanking=true`, and status APPROVED/PUBLISHED. Orphaned/private/community/pending/rejected/ambiguous test attempts are skipped, not guessed into a board.
- Backfill uses the test document's canonical `examId`/`targetExam`; if the historical attempt's recorded exam conflicts, it is skipped. Subject names are never used as exam identity. Missing or unsupported weights are skipped.
- Competitive ranking now distinguishes official provenance from public visibility. `isOfficial=true` no longer makes a private test publicly attemptable or eligible for XP/AIR; `isPublic=true` is required.
- Added pure unit tests for historical eligibility. Latest relevant commits: `aa2070ce0f15748926aabdabd916145c9b354a30`, `e1f0f6cbf71141e6ce9cdac242980bd73d49141c`, `5533500bce136132dada12076fca314acc7859bc`, `784c17cce130ced6cdc16366da4056fd1a623241`, `b8585da1df56b7079c86f2366553dc16bc99e037`.
- Workflow #221 still validates an earlier commit. Its Firestore Emulator job passed; backend failed only the classification regex test before the regex fix; Android job was still running at last check. No fresh CI result exists for current code.
### 2.11 Score bounds, multi-field weighting, and cohort validation — 2026-11-07

- Full/Part classification now examines all trusted metadata labels (`category`, `testCategory`, `testType`) rather than stopping at the first populated field. Conflicting Full+Part metadata is rejected; ambiguous `Mock Test`/PYQ labels remain unclassified until an Admin sets explicit metadata.
- Weighted ranking normalization now rejects negative scores and scores above the verified maximum rather than clamping corrupt values to 0..10000. Historical rebuild skips out-of-range raw scores.
- Class leaderboard filters now reject unsupported values (e.g. Class 10) instead of silently broadening the query to the All cohort. Supported values remain All, Class 11, Class 12, and Dropper.
- Added unit tests for metadata conflict, invalid cohort filters, and out-of-range score rejection. Latest code commits: `2b0f087df46313d511d3e6f1feb1e078a3719eab`, `b9f54b6e01e43557daaf72bd8942d0f55e6223d5`, `daf8419c56c49ce45a0ef447197fc45bce7f76da`, `67239417488fb8e5e4fe5eb70775d307fc5afcd5`, `ef19cc42830786936839b459a82f6358f5847e38`, `bbe242a4a6cb8227d7fc357debb4b8a2915d67d2`, `9bf7193ee4cfb5c8388bebf7e2ad3b5646e9ad74`, `791a3bafbff23d24298a76541e34e8bacb0802d8`, `cb47480d7b864e18c94c2839467795d51dbe079f`, `169983017b8fde9a2498c2d363cbeaa7d87339b3`.
- No CI result is available for these latest commits. CI #221 used an earlier SHA; it passed Firestore rules, failed one backend unit test due to the then-double-escaped regex, and Android validation was still running at last inspection. Do not mark this phase complete.
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


### 2.12 Main-branch overlap isolation, canonical data source, and fail-closed ingestion — 2026-11-07

- The integration branch is still ahead of `main` and has no commits missing from its base at the latest comparison. This does not mean every edited file is conflict-free; the branch changes shared files and must still be reviewed as a complete diff before any merge. No merge or deployment was performed.
- Removed live dual-writes to the legacy `Leaderboard` and `User_Ranks` collections. `Exam_Leaderboards/{examId}/Entries/{uid}` is now the canonical exam AIR record; `Test_Leaderboards` remains per-test rank, `Ranking_Periods` remains period rank, and XP collections remain a separate gamification domain. Legacy collections are not deleted or overwritten; the old `User_Ranks` cleanup path remains for stale records.
- New V2 exam score/count reads now come from the canonical `Exam_Leaderboards` entry. The backend no longer mirrors new cumulative exam scores into `Users_Data`; old v1 score fields are not used as v2 score values. Legacy qualifying counts can only signal that a verified backfill is pending.
- Android question/test defaults now fail closed: a new test does not silently default to NEET, a new test does not count toward ranking by default, and a new question does not silently default to MCQ or Section A.
- Removed static mock-question generation and offline sample fallback paths from `NEETViewModel`; removed the hardcoded 180-question preview dataset. Live scheduling no longer claims a successful upload through the old form when it has not extracted and validated source questions.
- Generated-question saving, public/community sharing, and PDF ingestion now require a supported source-verified exam, explicit stage/paper/subject/section identity, non-placeholder question content, and section-consistent marks. Missing or conflicting metadata blocks the operation rather than receiving a generic +4/-1 or Section A default.
- NEET UG 2026 and JEE Main 2026 registry patterns were updated from official NTA information bulletins. All other 23 exam configurations are marked unverified and are excluded from PDF processing/publishing until their own official source and pattern are reviewed. This is a deliberate safety gate, not a claim that the other exams are complete.
- Latest relevant code commits include `78c7ebf1ee5fc8e9a66e0ab49d4827d91e478e37` (canonical score source and migration reads), `ca619eff247dda944b9e4aa045eeca94b76385a4` (remove duplicate leaderboard writes), `aa856bacabbf857c308656aa390afe01ac93244f` (persist generated question policy), and the preceding Android fail-closed ingestion commits.
- Fresh workflow #357 (`42327476334`) was queued for the latest backend canonical-source commit at the time of this note. A queued workflow is not a pass. Backend unit tests, Android validation, and Firestore rules tests must all complete on the final branch head before merge readiness can be assessed.
- Still incomplete: actual Firebase Emulator transaction/idempotency/race tests; complete stage/paper selectors for multi-stage exams; official-source verification and year/versioned configs for the remaining exams; complete Kotlin/Node scoring parity tests; ranking history snapshots; privacy/banned-user/per-test leaderboard retrieval audit; safe dry-run/backfill evidence; final full regression and fresh CI. No production data was migrated, no Firestore rules were deployed, and `main` remains untouched.


### 2.13 Canonical score read path and legacy privacy hygiene — 2026-11-07

- Further removed the duplicated v2 exam-score cache writes from `Users_Data`. New submissions and historical rebuilds now write the exam score/count only to `Exam_Leaderboards/{examId}/Entries/{uid}`. `Users_Data` still holds profile, attempt totals, and XP state; legacy v1 qualifying counts are consulted only to show a migration-pending message when a v2 entry is missing. Legacy v1 score values are not used as v2 scores.
- `getUserRankingDetails` now reads the canonical score/count/eligibility from the materialized `Exam_Leaderboards` entry. It does not rank from stale `Users_Data` score fields.
- The profile visibility/ban synchronizer no longer updates `User_Ranks` duplicate documents. It continues to update existing legacy `Leaderboard` rows only for display-name/privacy/ban hygiene; it does not write new score rows there.
- The latest branch comparison against `main` reports 213 commits ahead, 0 behind, with 23 changed paths. This confirms the branch contains the current main history, but does not replace a final content-level diff review. No merge, deployment, Firestore rules deployment, or data migration was performed.
- Latest backend commit: `85fc547f694dfdb1e1675bd14cca45cfd5e73005`. Workflow #363 (`42327666187`) was queued for this commit; all three jobs were still queued at the last check. This is not a test pass.


### 2.14 Leaderboard identity fallback isolation — 2026-11-07

- Live audit found one remaining Android display-path fallback in `NEETViewModel.fetchLeaderboard`: when the selected exam, filter, and profile target were empty, the client silently selected `NEET`. This could show NEET standings while the user had not selected an exam, which is a cross-exam identity/data-routing defect.
- Removed the implicit NEET default. The client now clears any stale displayed board, requires an explicit non-empty exam identity, resolves that identity through the exact registry lookup, and refuses to query if the selected exam is missing, inactive, or not source-verified. It does not rewrite an unknown exam into a different board.
- Commits: `1d4d9a778a46c040f308eb08c7286f091a83f114` (initial fallback removal), `037b414770867b9e540393eba2b06ffd392106ba` (correct exact registry validation).
- Fresh Ranking V2 Validation workflow #578 / run `42346012660` was triggered for the corrected branch head. At the last inspection, backend unit tests, Firestore rules tests, and Android validation were all still in progress. This is not a pass.
- Live comparison at this checkpoint: `main` → integration reports 320 commits ahead / 0 behind. The integration branch contains main history; 0 behind means no main commits are missing, not that changed shared files have no semantic overlap. The changed-path diff still requires final review. PR #3 remains DRAFT; PRs #1 and #2 remain open. No merge, deployment, rules deployment, or production migration was performed.
- Canonical v2 exam AIR remains `Exam_Leaderboards/{examId}/Entries/{uid}`; per-test ranking remains `Test_Leaderboards`; period boards remain `Ranking_Periods`; XP boards remain separate. Legacy `Leaderboard` / `User_Ranks` collections are not used as a second write target for new v2 AIR totals. Existing legacy records are not deleted or silently converted.
- Remaining exit blockers are unchanged: emulator-backed production callable transaction/race tests, full official-source/year-stage-paper coverage, Kotlin/Node golden-vector parity, ranking history snapshots, privacy/ban retrieval audit, migration dry-run/rollback evidence, and a fully green latest-head CI run.


### 2.15 CI failure triage — 2026-11-07

- Workflow #578 / run `42346012660` backend job failed 1 test out of the scoring/callable/ranking suite. The failing test was `ranking-eligible tests require complete trusted private answer keys`; the production validator correctly rejected its fixture because the fixture omitted required canonical `stageId` and `paperId`.
- Corrected the test fixture to use the verified NEET identity `neet_main / neet_paper_1` in commit `204f5e937d2518357343149f782f12169d948620`. Production validation was not weakened.
- Run #578 is still not a green validation: backend is FAILED for the earlier fixture revision; Android and Firestore jobs remained IN PROGRESS at the last poll. The connector returned no new workflow run for the fixture-fix commit, so the correction remains unverified by CI.
- Latest branch comparison: `main` → integration is 324 commits ahead / 0 behind. There are 38 changed paths relative to main. This is not a zero-overlap diff: many files intentionally reconcile shared features. The safety rule is to preserve main's current content and reconcile each changed shared file semantically; never blindly overwrite or cherry-pick the Universal branch wholesale.
- No merge or production operation was performed. PR #3 remains a draft integration-validation PR, and main remains untouched.


### 2.16 Live repository refresh and Phase 1 risk findings — 2026-11-07

**Fresh remote snapshot (checked before this audit entry):**
- Integration branch head: `f3636cbe1e144134cfd910cdfa12063badfdfae4`.
- Ranking development branch head: `a250f54a54035c21363a190df39dcd041d7aa55e`.
- `main` comparison base/head snapshot: `d2feae47f908b705209c27b8e61e624f94c400d7`.
- `main` → integration: 325 commits ahead / 0 behind; `feature/ranking-engine-v2-2026-11-06` → integration: 233 ahead / 0 behind. The integration branch is a descendant of the Ranking branch, not a diverged branch. This does not mean changed files are conflict-free.
- The roadmap file exists on the integration branch; fetching it from `main` returns 404, so it is development documentation and is not currently part of main.
- PR #3 remains DRAFT and targets the Ranking development branch, not main. No merge, deployment, rules deployment, or production migration was performed.

**Latest CI evidence for integration head:** workflow run `42346089736` (run #584) completed with overall FAILURE. Backend unit/syntax job PASS; Firestore Emulator rules job PASS; Android validation FAIL at the combined `:app:testDebugUnitTest :app:assembleDebug` step. The job summary does not expose the failing test/log detail, so the root cause is still UNVERIFIED and must be retrieved before changing or waiving the test. This run is not a release gate pass.

**Confirmed backend correctness issue requiring a code fix before release:**
- In `backend_functions/index.js`, the per-test leaderboard count/rank/percentile query runs before `db.runTransaction`. A concurrent submission can change the population between that query and the transaction, so the attempt's stored rank/percentile can be stale.
- The catch block converts count/query failures into `testRank=0`, `testTotalCandidates=0`, and `percentile=0`, then persists those values as if they were a valid result. This hides infrastructure failure as ranking data.
- The same pre-transaction calculation uses the submitted attempt score even when the user's attempt is not the first qualifying attempt. The canonical per-test board is only written for the first qualifying attempt, so a repeat attempt can receive a rank for a score that is not present on that board.
- Required remediation: define the product contract for historical-vs-live per-test rank; calculate only against the canonical leaderboard population and score basis; do not turn query failure into rank zero; preserve idempotent retry behavior; add concurrent-submission and repeat-attempt tests using Firestore Emulator/transaction behavior. Do not apply a superficial move of the count query into the transaction without checking Firestore transaction read/query semantics and index requirements.

**Phase 1 merge-overlap inventory (from live compare Ranking → integration):**
Shared/overlapping files requiring semantic reconciliation against both parent branch and main include `MainActivity.kt`, `NEETViewModel.kt`, `backend_functions/index.js`, `backend_functions/examScoring.js`, `backend_functions/examScoring.test.js`, `backend_functions/rankingEngine.js`, `backend_functions/rankingEngine.test.js`, `firestore.rules`, `firestore.test.js`, and `firestore.indexes.json` (the latter is unchanged relative to Ranking in this compare but remains part of the overall main diff). Universal-scoring-specific files include the Kotlin scoring/registry/PDF pipeline, Room database/entities/repository, sanitizer, backup adapter, and their tests/schema. These must be ported/reconciled by behavior, not wholesale copied over Ranking or main.

**Current phase status:** Phase 0 baseline refresh is refreshed; Phase 1 overlap/correctness audit remains IN PROGRESS. No source code was modified in this audit. Next bounded work: retrieve the Android job's failing test/log detail, inspect full main-vs-integration content for every shared path, then implement and test the per-test rank contract fix on this integration branch only. Keep main untouched.


## 2.17 Required three-pass completion protocol and consolidated defect register — 2026-11-07

This section is binding for the remaining Ranking Universal work. The work is deliberately split into three controlled passes. Do not mix the final verification pass into implementation or treat a successful build as proof of correctness. Do not promise an error-free result before the tests demonstrate it.

### Pass 1 — Audit, record, and freeze the implementation plan (DOCUMENTATION/AUDIT ONLY)

**Purpose:** record every known defect, overlap, dependency, compatibility rule, acceptance criterion, and unresolved question in this roadmap before the main implementation pass. Do not make broad production-source changes during this pass. Any urgent source change must be separately justified, isolated, tested, and recorded.

Consolidated blockers and required corrections:

1. **Per-test rank/percentile race:** `backend_functions/index.js` calculates per-test rank/count/percentile before the submission transaction. Define the canonical rank contract (historical submission snapshot vs live board rank), derive rank from the exact canonical leaderboard population and canonical score, and handle concurrent submissions deterministically. Do not blindly move count queries inside a transaction; validate Firestore transaction/query support, indexes, retry behavior, and contention strategy first.
2. **Fake zero rank on read failure:** current catch path maps leaderboard query failures to rank=0, population=0, percentile=0. Never persist infrastructure failure as a valid rank. Return an explicit unavailable/error state or fail the relevant operation safely without corrupting the authoritative attempt/score record. Preserve API compatibility deliberately.
3. **Repeat-attempt score mismatch:** current rank calculation uses the latest submitted score even when only the first qualifying attempt is represented in the canonical per-test leaderboard. Rank must use the exact score/attempt that the board represents. Reattempts must not receive a fabricated board position.
4. **Idempotent retry consistency:** identical retries must return the canonical stored attempt and must not double-increment score, XP, streak, period totals, counts, or leaderboard rows. Conflicting reuse of an attempt token must fail closed. Canonical and legacy duplicate copies must be reconciled or rejected, never silently selected when inconsistent.
5. **Android CI blocker:** workflow run `42346089736` / #584 had backend and Firestore rules jobs PASS but Android validation FAIL at the combined unit-test/debug-build step. Exact failing test/log is still unknown. Retrieve the actual failure output, identify root cause, fix the underlying issue, and rerun; do not disable or weaken tests to obtain green CI.
6. **Main/Ranking/Universal overlap:** shared paths include `MainActivity.kt`, `NEETViewModel.kt`, `backend_functions/index.js`, `backend_functions/examScoring.js`, `backend_functions/examScoring.test.js`, `backend_functions/rankingEngine.js`, `backend_functions/rankingEngine.test.js`, `firestore.rules`, `firestore.test.js`, and relevant Firestore indexes. Reconcile each file against the latest `main` and the Ranking parent by behavior and symbol-level changes. Never wholesale replace a shared file, erase main-only features, or assume a zero-behind commit comparison proves there is no semantic conflict.
7. **Universal scoring parity:** one canonical exam identity and scoring policy must drive Android and backend. Verify positive marks, negative marking, unanswered/ungraded questions, decimal precision/rounding, optional questions, sections, duration/time limits, MCQ/MSQ/NAT and exam-specific formats. Add shared golden vectors and require Kotlin/Node outputs to match exactly within the declared decimal policy.
8. **Exam registry completeness:** do not create a board for an unknown or unsupported exam. Each enabled exam needs verified identity aliases, official scoring policy, test type/weight policy, subject/section rules, and source-backed configuration. Missing CAT, IBPS/SBI or any other exam configuration must remain explicitly unsupported until verified, not be filled with guessed/dummy defaults.
9. **Ranking eligibility and provenance:** only explicitly approved, public, official, source-verified tests with valid canonical exam/stage/paper identity and protected answer keys can award competitive AIR points. Community/practice/private/unapproved tests must not leak into AIR. Validate status and answer-key provenance at submission and transaction boundaries.
10. **Canonical storage/no duplicate writes:** retain one authoritative source per ranking domain. Exam AIR, per-test board, period board, XP ledger/boards, and attempt history must have documented ownership. Legacy `Leaderboard`, `User_Ranks`, uppercase attempt paths, and old aggregates must not become a second write target for new v2 totals. Audit collection names, document keys, transaction paths, and backfills for duplicates.
11. **Privacy/ban enforcement:** private profiles, banned/suspended accounts, deleted users, display-name/class changes, and visibility changes must be applied consistently across every ranking collection and retrieval query. Do not rely only on write-time flags; test read-time exclusion and profile visibility propagation.
12. **Historical ranking/migration:** define immutable ranking-period snapshots/history, time-zone boundary behavior (IST), tie-breaking, percentile population definition, eligibility thresholds, and migration source of truth. Migration must be dry-run first, idempotent, conflict-detecting, auditable, and rollback-capable. Never infer or invent historical score data.
13. **Android page/navigation integration:** map every page in the roadmap to an existing navigation route, ViewModel state, repository/API, loading/error/empty state, pagination, privacy handling, and language strings. Reuse existing app shell and features; no duplicate leaderboard tabs/screens or disconnected mock UI.
14. **Room/offline/backup compatibility:** local cache must be explicitly non-authoritative for public rank; schema migrations must preserve existing user/test/attempt data; backup/restore must not duplicate or overwrite canonical server ranking data. Verify old schema migration paths and rollback/recovery.
15. **Security/rules/indexes:** verify callable authentication/authorization, admin-only rebuild, client write denial for authoritative ranking collections, least-privilege reads, privacy filters, required composite indexes, App Check/rate limits where applicable, and Storage/PDF access boundaries.
16. **PDF/import pipeline:** imported papers must retain source, exam, stage, paper, year, official status, answer-key provenance, and validation state. OCR/extraction uncertainty must not silently create official answer keys or ranking-eligible tests. No dummy rows or placeholder official papers.
17. **Operational resilience:** define retry/backoff, transaction contention, rate limits, bounded queries/pagination, aggregation/rebuild observability, error reporting, and safe failure states. No hidden catch-and-default behavior for authoritative ranking values.

Pass 1 must also produce a file-by-file integration matrix: path; main baseline behavior; Ranking branch changes; Universal branch changes; overlap type; data/schema/API impact; merge strategy; tests; status. Mark unresolved findings as BLOCKED, not PASS.

### Pass 2 — Full implementation (CODE ONLY AFTER PASS 1 BASELINE IS RECORDED)

Implement the complete roadmap in the dedicated integration/development branch. Reconcile against latest `main` while keeping `main` untouched. Preserve main-only features and existing data. Make small, reviewable commits grouped by subsystem. For each subsystem update source, tests, rules, indexes, and documentation together. No broad file replacement, speculative schema, duplicate collections, fake data, silent fallback defaults, or production data migration during ordinary development.

Required implementation areas: backend authoritative scoring/submission and transactions; canonical AIR/per-test/period ranking; XP/league/streak ledger; eligibility/privacy/ban handling; tie-break/percentile/history; verified exam registry and scoring policies; Android repositories/ViewModels/pages/navigation; Firestore rules/indexes; Room cache/migrations; PDF/import provenance; migration tooling with dry-run/rollback; observability and admin rebuild. Keep API/data contracts backward-compatible or provide explicit versioned migration adapters. Resolve each Pass 1 defect in source and add its regression test in the same implementation series.

### Pass 3 — Independent complete verification (NO FEATURE WORKAROUNDS)

After Pass 2 is feature-complete, freeze implementation scope and run a clean validation on the exact candidate commit. Minimum required gates:
- Backend lint/syntax/unit tests and callable tests.
- Firestore Emulator integration/security tests, including concurrent submissions, transaction retries, duplicate attempts, conflicting legacy/canonical records, privacy and ban cases.
- Kotlin/Node scoring golden-vector parity across every enabled exam and question type.
- Android unit tests, Room migration tests from every supported prior schema, and clean debug build.
- UI/navigation tests for all Ranking pages, loading/error/empty/pagination and Hindi/English states.
- Firestore rules and index validation; Storage/PDF authorization tests.
- Duplicate-write/collection audit and production-data non-mutation audit.
- Migration dry-run, idempotency, conflict report, rollback proof, and historical-data reconciliation.
- Main compatibility regression: compare candidate with latest main, inspect every changed shared file and preserve all main-only behavior.
- Latest-head CI must be fully green; attach exact run/commit evidence. No skipped, quarantined, disabled, or weakened test may be counted as PASS without explicit documented approval and risk acceptance.

### Release/merge gate

Merge is prohibited until all three passes are complete, all blocking findings are resolved, all required gates are PASS on the exact candidate SHA, the final diff is reviewed against the latest `main`, and the user explicitly authorizes the merge. Keep `main` unchanged until that authorization. No production deployment or historical-data migration is authorized by this plan alone.

### Current execution state

- Pass 1: IN PROGRESS. The known findings above are now consolidated in this file; full file-by-file matrix and Android CI root-cause retrieval remain outstanding.
- Pass 2: NOT STARTED as a complete implementation pass. Existing code is partial and must not be described as complete.
- Pass 3: NOT STARTED. Existing CI results are partial/failing and do not satisfy release gates.


### 2.18 Android CI root cause and isolated blocker fix — 2026-11-07

**Root cause retrieved from the actual failed job log (run 42346089736, job 128324033837):**
- The failure was not a unit-test runtime failure. Kotlin compilation stopped in `NEETViewModel.kt` at lines 5921/5925 because Firebase `DocumentSnapshot.exists` is a function and was referenced as a property in three places.
- Exact compiler output: `Function invocation 'exists()' expected` (three occurrences). The previous summary that suggested a `CrashResilienceTest` failure was incorrect for this integration-head run; the raw job log is authoritative.
- The build also logged missing `google-services.json`; that message did not stop the task. The fatal error was the Kotlin compile failure.

**Isolated corrective change committed on the integration branch only:**
- File: `app/src/main/java/Com/ak/testseries/viewmodel/NEETViewModel.kt`
- Replaced the three invalid `existingTest.exists` / `existingKeyDoc.exists` property-style references with function calls `exists()`.
- Commit: `8e998722a412dfdf3af0cd3e5c26e1b08a6d3e19`.
- Scope is deliberately limited to the compile blocker; no ranking policy, data, Firebase deployment, or main-branch change.
- Validation status: awaiting the CI run for this exact commit. This fix is not marked verified until Kotlin compile, Android tests, and debug build complete successfully.

**Next required work remains:** finish Pass 1's full latest-main/shared-file semantic matrix and then proceed through Pass 2 subsystem implementation in the order defined above. The per-test rank/percentile concurrency and fake-zero fallback defects remain open; no production rank contract has been silently chosen.


### 2.19 Continued integration audit — file matrix and per-test rank contract — 2026-11-07

This is a continuation of Pass 1, not a declaration that Pass 1 is complete.

**Fresh compare after the isolated compile fix:**
- `main` → `feature/ranking-universal-integration-v1`: 329 commits ahead / 0 behind at the audit snapshot.
- Changed-file set includes the shared/high-risk files listed below plus Universal-only Android files.
- Zero commits behind is ancestry information only. It is not semantic compatibility proof.
- CI for compile-fix commit `8e998722a412dfdf3af0cd3e5c26e1b08a6d3e19`: backend unit tests PASS; Firestore rules tests PASS; Android validation was still IN PROGRESS at the last poll. No green overall CI claim.

**File-by-file integration matrix (initial classification; every row remains open until the stated behavior-level comparison and tests are complete):**

| Path | Main baseline / integration concern | Impact and required merge strategy | Status |
|---|---|---|---|
| `.github/workflows/ranking-v2-validation.yml` | Integration-only validation workflow | Preserve required gates; confirm secrets/config handling and exact tested SHA | AUDIT |
| `MainActivity.kt` | Main contains active app shell/navigation; integration adds exam/admin routes | Symbol-level route and state reconciliation; no duplicate tabs or removed main routes | BLOCKED |
| `NEETViewModel.kt` | Main test flows plus integration universal identity, secure publishing, ranking calls | Preserve main test/community flows; isolate Universal changes; compile and ViewModel regression tests | BLOCKED |
| `backend_functions/index.js` | Main callable/submission behavior plus integration ranking transaction | Reconcile callable contract, auth, scoring, idempotency, transactions, legacy reads; no wholesale replacement | BLOCKED |
| `backend_functions/examScoring.js` | Integration verified scoring policy | Confirm main consumers and API contract; Kotlin/Node golden-vector parity | AUDIT |
| `backend_functions/examScoring.test.js` | Integration scoring regression suite | Preserve existing tests; add cross-engine vectors and optional/ungraded/decimal boundaries | AUDIT |
| `backend_functions/rankingEngine.js` | Integration ranking calculations and thresholds | Validate every threshold/weight/tie/period rule against product contract; no unsupported defaults | AUDIT |
| `backend_functions/rankingEngine.test.js` | Integration ranking unit tests | Expand with duplicate, concurrency, reattempt, class/privacy and boundary tests | AUDIT |
| `firestore.rules` | Main authorization plus ranking collection rules | Preserve all existing access paths; emulator-test every changed match and banned/private cases | BLOCKED |
| `firestore.test.js` | Main security regression suite plus ranking coverage | Keep all prior cases; add v2 collection and privacy/ban regression tests | AUDIT |
| `firestore.indexes.json` | Main query indexes plus ranking indexes | Validate each actual query and emulator/deployment index requirements; remove only proven-unused duplicates | AUDIT |
| `AppDatabase.kt` | Main Room schema and existing data | Preserve schema history; verify every supported migration path and export schema diff | BLOCKED |
| `DatabaseRepository.kt`, `Entities.kt` | Main local test/user/attempt data models | Ensure public rank cache is non-authoritative and no entity identity collision | AUDIT |
| `FirebaseUserRepositoryImpl.kt` | Main profile repository | Keep profile privacy/class/ban propagation consistent with ranking queries | AUDIT |
| `ExamScoringEngine.kt`, `ExamFormatStandardizer.kt` | Universal client scoring and normalization | Must match server engine exactly for enabled exam policies | AUDIT |
| `UniversalExamRegistry.kt`, `UniversalExamModels.kt` | Universal identity and policy source | Enable only verified exam/stage/paper configurations; unknown exams fail closed | AUDIT |
| `UniversalPdfPipeline.kt`, `OfflineMarkdownQuestionParser.kt`, `SecureQuestionPayloadSanitizer.kt` | Import, parsing and answer-key security | Preserve provenance; uncertain OCR never grants official/ranking eligibility | AUDIT |
| `UniversalExamAdminScreen.kt`, `AdminPanelScreen.kt`, `UploadAiTabScreen.kt` | Existing admin/upload UI and new Universal workflows | Reuse current admin shell; verify role checks, no disconnected duplicate screens | AUDIT |
| `ExamSimulationScreen.kt`, `SyllabusPdfModal.kt` | Existing test UI and Universal exam formats | Preserve timer, navigation, language, question palette and attempt behavior | AUDIT |
| `CrashResilienceTest.kt` and Universal exam tests | Integration regression tests | Determine exact intended behavior; do not weaken or delete tests to obtain green CI | AUDIT |

**Confirmed code-level per-test ranking defect (re-read at this audit):**
- `index.js` lines around the submission handler calculate `Test_Leaderboards/{testId}/Entries` population and higher-score counts before entering `db.runTransaction`.
- The catch block converts query failure to zeros and those values are later stored on the attempt.
- The transaction only creates the canonical per-test board entry when `isFirstQualifyingAttempt` is true. Therefore the pre-transaction query incorrectly uses the current submission score for a reattempt even though that score is not the board's canonical score.
- The canonical board entry currently stores the first qualifying attempt's score. This is the product behavior already encoded by the lock and `isFirstQualifyingAttempt` gate; do not silently change it to best-score or latest-score semantics.

**Frozen contract proposed for implementation (requires regression tests):**
1. Per-test leaderboard rank is based only on the canonical first qualifying attempt currently written to `Test_Leaderboards/{testId}/Entries/{userId}`. Reattempts remain in private attempt history but do not alter that board entry or receive a rank calculated from their new score.
2. The authoritative attempt and board entry are committed atomically. The response rank is a live snapshot derived from that committed canonical board score and the exact eligible board population, not from an uncommitted submission score.
3. If the post-commit rank query fails, preserve the valid score/attempt/board entry and return explicit `rankStatus: "UNAVAILABLE"` with nullable rank/percentile/population fields. Never store zero as a substitute for infrastructure failure.
4. For a repeat qualifying attempt, return `rankStatus: "NOT_RANKED_REPEAT_ATTEMPT"`; return the canonical first qualifying score/attempt reference if the existing API needs to show the user's board position. Do not fabricate a position for the repeat score.
5. A retry with the same attempt token and same answers returns the same canonical attempt outcome and does not increment counters, XP, streak, period totals or board rows. Reuse with different answers fails closed.
6. Rank/percentile are explicitly documented as live snapshots, so later submissions may change them. Stored attempt score and canonical board score remain immutable. A separate immutable historical rank snapshot is not claimed unless a future product decision explicitly adds it.

**Implementation design to validate before coding:**
- Remove pre-transaction count/rank queries and the catch-to-zero branch.
- Keep first qualifying attempt, lock, attempt history, XP and board writes within the existing idempotent transaction.
- After the transaction commits, derive the per-test rank from the canonical board entry's stored score and a bounded eligible-board query. Return an unavailable status on query/index failure without changing the committed score.
- Make idempotent retry responses use the same rank resolver and canonical board score; never trust stale rank fields on a legacy attempt record.
- Validate query filters, pagination/aggregation semantics, index requirements and concurrent submissions in Firestore Emulator before marking this defect fixed.
- Preserve backward compatibility deliberately: introduce nullable rank fields and `rankStatus` additively; inspect Android parsing before changing response shape.

**Next execution order:** finish the shared-file semantic matrix (including MainActivity, NEETViewModel, Firestore rules/indexes and backend submission), add emulator regression tests for concurrent first submissions + repeat attempts + failed rank reads, then implement the per-test contract on this branch. After that proceed subsystem-by-subsystem through scoring parity, AIR/XP, Android integration, migration, PDF provenance, and security. Pass 1 remains IN PROGRESS; Pass 2 remains NOT STARTED as a complete implementation pass.


### 2.20 Firestore transaction/query research — per-test rank implementation decision — 2026-11-07

Official Firestore documentation was checked before choosing the transaction design:
- Firestore transactions require all reads before writes; the transaction callback may be retried, and read documents must remain unchanged for the transaction to commit. urlFirebase transactions and batched writeshttps://firebase.google.com/docs/firestore/manage-data/transactions
- The Node.js Firestore transaction API documents `transaction.get(refOrQuery)` for a DocumentReference or Query, returning a document or query snapshot. urlNode.js Firestore Transaction APIhttps://googleapis.dev/nodejs/firestore/latest/Transaction.html
- Firestore read-time aggregation documentation describes count/sum/average and their trade-offs; query/index cost and result size still need to be validated for the actual ranking board. urlFirestore aggregation solutionshttps://firebase.google.com/docs/firestore/solutions/aggregation

**Implementation consequence:** a transaction query is a valid option to investigate; it must not be rejected based on an assumption that Firestore transactions only read document references. However, a count aggregation's exact compatibility with the repository's installed `firebase-admin` version, the serializable behavior for concurrent inserts/score changes, transaction contention, query limits and required indexes must be proven in the emulator and dependency version before use. Do not substitute an ordinary pre-transaction count query.

**Candidate implementation paths to benchmark/test:**
A. Read the relevant eligible Test_Leaderboards query through the transaction before any writes, derive the canonical first-attempt rank, then create the attempt and board row in the same transaction. Validate phantom/concurrent insert serialization and whether the aggregate count is transaction-readable in the installed SDK.
B. If exact population serialization or transaction cost is not acceptable, atomically commit attempt + canonical board row first, then return a live rank snapshot via a separate resolver. In this design rank-query failure returns UNAVAILABLE and never rolls back or falsifies the committed score; response explicitly labels rank as a post-commit snapshot.
The chosen approach must be justified by emulator tests, not preference. A normal count query outside the transaction followed by persisted rank fields remains prohibited.


### 2.21 Android CI follow-up — malformed Kotlin test declaration fixed — 2026-11-07

The Android job for workflow run `42354507259` completed with failure. Backend unit tests and Firestore rules tests passed, but Android unit-test compilation failed before tests could execute. The exact compiler diagnostics pointed to `app/src/test/java/Com/ak/testseries/exam/UniversalRankingAndIdentityTest.kt:195`: Kotlin function declarations cannot start with the numeric token in `fun 2026VerifiedPatternsHaveNoOptionalSectionAttemptCaps()`.

**Fix applied on the integration branch:** renamed the test function to `verified2026PatternsHaveNoOptionalSectionAttemptCaps()`. Test body and assertions were not changed. Commit: `2fb8b45548da6f233e2ab723cf2df79ab3859c90`.

**Verification state:** a new CI run must confirm Android compilation and tests. The previous run remains failed; do not mark Android validation green based on the source edit alone. The non-fatal missing `google-services.json` warning was not the cause of this failure. Gradle cache restore warnings were also not the cause.

**Next backend blocker remains open:** `index.js` computes per-test rank and population before the transaction, catches query errors as rank/percentile zero, then only writes the canonical per-test leaderboard entry for the first qualifying attempt. Repeat attempts can therefore receive a rank computed from a score that is never represented in the canonical board. Keep this as the next backend implementation target; do not change the existing first-qualifying-attempt board policy without an explicit product decision.


### 2.22 Per-test ranking defect — implementation pass 1 — 2026-11-07

**Code change completed on this branch (not yet production-deployed):**
- Removed the pre-transaction aggregation queries that ranked the incoming attempt score before the canonical first-attempt policy was decided.
- The transaction continues to create exactly one canonical `Test_Leaderboards/{testId}/Entries/{userId}` row only for the first qualifying attempt. Repeat attempts remain history-only and do not overwrite this row.
- The transaction no longer persists precomputed `percentile`, `testRank` or `testTotalCandidates` values on attempt documents. Existing legacy fields may remain in old records but are no longer trusted by the callable retry response.
- After the transaction commits, the callable fetches the canonical board entry and computes a live rank snapshot from that entry's stored score. It uses strict-greater-than score count for competition rank and total canonical board population for percentile. Identical retries resolve the same canonical entry; repeat qualifying attempts return `NOT_RANKED_REPEAT_ATTEMPT` with nullable rank fields.
- Rank lookup failures return `rankStatus: "UNAVAILABLE"` and null rank/percentile/population. They do not change or roll back the committed score, attempt history, XP, streak, or leaderboard entry. Non-qualifying attempts return `NOT_ELIGIBLE`.
- Added pure helper `buildTestRankingSnapshot` and regression tests for canonical first attempt, repeat attempt, missing canonical entry, invalid population and non-eligible attempt.

**Changed files:**
- `backend_functions/index.js`
- `backend_functions/rankingEngine.js`
- `backend_functions/rankingEngine.test.js`

**Relevant commits:**
- `d69750a43f35ffa8d140ac21cd44cf364ec6ec95` — add rank snapshot helper.
- `762b93c2b0f30f8222408209580aa5927613d361` — add rank snapshot tests.
- `43143786c2f08218be9c7daf46efa6ed33dbcbb3` — resolve rank from canonical committed board entry.
- `68a257c267f11985ea220bdcb2c68b4cb0088276` — fix/export helper.
- `d947105c8c0f0001cb5f3e8c1db4e9f59847328c` — use shared helper in callable.
- `606046087a29aec7b8056161ee0e7b609503fc1b` — test import correction.
- `f50bbb1a4f3360814d0ba58a8d87cd99af308143` — remove obsolete pre-transaction percentile dependency.

**Important verification limits:** this is a post-commit live snapshot, not an immutable historical rank. The higher-score count and total count are separate aggregation reads and may observe slightly different moments while other submissions are arriving; a population consistency/scale test is still required before claiming an exact serialized rank snapshot under heavy concurrency. The installed lockfile resolves `firebase-admin 12.7.0`; this implementation deliberately does not depend on transaction aggregation support. CI must run against the new code commits. No deployment, migration, main-branch update or production Firestore write was performed.

**Next checks:** (1) inspect Android result handling and expose `rankStatus` without converting null/unavailable into rank zero; (2) test concurrent first submissions and retry semantics in Firestore Emulator or an isolated integration harness; (3) verify required indexes and aggregation behavior; (4) continue the full file-by-file integration audit for AIR, XP, periods, class cohorts, privacy/ban updates, migration and security.


### 2.23 Period and XP leaderboard input validation hardening — 2026-11-07

A follow-up endpoint audit found two input-validation gaps:
- `getRankingPeriodLeaderboard` accepted a malformed class cohort as a null filter and relied on a loose period-ID regex that allowed impossible month values such as month 00 or 13.
- `getXpLeaderboard` accepted arbitrary weekly collection IDs instead of validating ISO week syntax and week range.

**Fix applied:** added shared pure validators `isValidRankingPeriodId` and `isValidRankingWeekId`; the period endpoint now rejects invalid class levels and enforces month 01–12 plus the correct H1/H2 suffix for HALF_MONTH versus MONTH. Weekly XP now requires a valid `YYYY-W01..W53` identifier. Added deterministic unit tests for valid and invalid cases.

**Changed files:** `backend_functions/index.js`, `backend_functions/rankingEngine.js`, `backend_functions/rankingEngine.test.js`.

**Verification:** awaiting CI for the current branch head. No Firestore data was changed. The broader audit remains open, including period migration write batching, cross-query population consistency, Android rankStatus visibility, and all ranking/security edge cases.


### 2.24 Migration batching and API pagination hardening — 2026-11-07

The backfill implementation previously accumulated all Exam_Leaderboards, Ranking_Periods and Users_Data writes for one user in a single Firestore WriteBatch. A long-lived account with many historical periods could exceed Firestore's 500-write batch limit and stop migration. The backfill now flushes at 450 queued writes and continues with a fresh batch; each write is an idempotent merge/update, and the migration marker is written only in the final batch. If an intermediate batch fails, the user is not marked complete and a retry safely reapplies the merge writes.

The admin migration and leaderboard endpoints also accepted fractional limits, which can be rejected by Firestore query `limit()`. Added shared `clampRankingLimit` with finite-number handling, floor-to-integer behavior and endpoint-specific bounds (migration 1–20; leaderboards 1–100), with unit tests. Applied to migration batches, exam leaderboard, period leaderboard and XP leaderboard.

**Changed files:** `backend_functions/index.js`, `backend_functions/rankingEngine.js`, `backend_functions/rankingEngine.test.js`.

**Verification:** CI for the latest commits is pending. Emulator testing of a synthetic user with more than 450 materialization writes remains required; no production migration was run.


### 2.25 Per-test leaderboard ban filtering and index — 2026-11-07

The per-test rank resolver was further hardened so banned users cannot contribute to the candidate population. Both higher-score and total-population aggregation queries now require `isBanned == false`; the canonical entry must explicitly carry `isBanned: false`, otherwise the rank is returned as unavailable rather than guessed. Added the required collection-group composite index on `Entries(isBanned ASC, score ASC)` and a regression test for banned/missing active-state rows.

**Changed files:** `backend_functions/index.js`, `backend_functions/rankingEngine.js`, `backend_functions/rankingEngine.test.js`, `firestore.indexes.json`.

**Compatibility note:** pre-existing per-test leaderboard rows without an explicit `isBanned: false` are excluded from the new aggregation until reconciled/backfilled. Do not deploy the new query before the composite index is deployed and active. Existing board data has not been modified.


### 2.26 Firestore test visibility parity with authoritative callable — 2026-11-07

The security-rule publication helper did not match the server callable: it accepted `Published` and `APPROVED` but rejected uppercase `PUBLISHED`, while the callable normalizes status case-insensitively. It also allowed `isOfficial == true` to make a test readable even when `isPublic == false`, while the callable requires an explicit public flag for student submissions. This created a client/server access-policy mismatch.

**Fix applied:** rules now permit only explicit approved/published status variants used by the app (`APPROVED`, `PUBLISHED`, `Published`) AND `isPublic == true`. Admin/creator/uploader ownership exceptions remain unchanged. The admin email fallback now safely reads the optional `email_verified` claim using `get(..., false)`. Added Firestore Emulator regression coverage for uppercase PUBLISHED and private official test denial.

**Changed files:** `firestore.rules`, `firestore.test.js`.

**Verification:** current Firestore Emulator workflow must confirm the updated rules compile and tests pass. No production rules deployment was performed.


### 2.27 Android rank fallback — remove legacy score source overlap — 2026-11-07

The Android `fetchUserRank` fallback path still read `qualifyingTestsCount_{exam}` and `cumulativeScore_{exam}` from `Users_Data` when the callable was unavailable, then compared those legacy values against the v2 `Exam_Leaderboards` population. This could combine two different score systems and produce a rank that did not correspond to the canonical v2 entry. It also did not enforce the same selected-class/profile-class match as the callable.

**Fix applied:** fallback now reads the student's own `Exam_Leaderboards/{examId}/Entries/{uid}` materialized record as the only source for qualifying count and normalized score. If no v2 entry exists, legacy count is used only to show `Ranking Migration Pending`; legacy score is never used to calculate a rank. It checks profile class against the selected cohort, excludes banned entries, uses v2 eligibility flags and computes the cohort rank from the same v2 board. The fallback no longer mixes raw/legacy totals with normalized v2 scores.

**Changed file:** `app/src/main/java/Com/ak/testseries/viewmodel/NEETViewModel.kt`.

**Verification:** Android CI must compile and run the ViewModel/unit tests. No Room schema change or production data update was needed.


### 2.28 Daily streak tracking decoupled from one-time XP awards — 2026-11-07

A behavior audit found that `currentStreak`, `longestStreak` and `lastActivityDateIST` were updated only inside `shouldAwardXp`. Because XP is awarded only once per test, a student who completed another valid public attempt on a later day could fail to advance their daily streak if the XP ledger for that test already existed.

**Fix applied:** added pure `calculateDailyStreak` with same-day idempotency, consecutive-IST-day increment and gap reset behavior. Every new valid publicly available attempt now updates daily activity/streak fields, while XP totals, weekly XP and XP ledger/leaderboard writes remain restricted to the existing one-time first-test award rule. Callable retries still return before any counters or streaks are incremented.

**Changed files:** `backend_functions/index.js`, `backend_functions/rankingEngine.js`, `backend_functions/rankingEngine.test.js`.

**Verification:** waiting for backend CI on the latest branch head. No production user records were changed.


### 2.29 Raw ranking-board privacy — callable-only client access — 2026-11-07

A privacy audit found that the v2 materialized board documents stored internal `userId` values even when `profileVisibility == PRIVATE`. The callable leaderboard APIs already redact the user ID for private profiles, but Firestore rules still allowed any authenticated client to read raw Exam_Leaderboards, Test_Leaderboards, Ranking_Periods and XP leaderboard documents directly. The Android app also bypassed the sanitized callable in its leaderboard and rank fallback paths.

**Fix applied:**
- Added admin-only callable `hasTestRankingContributions` for the visibility-change preflight; Android no longer directly reads Test_Leaderboards.
- Android exam leaderboard now uses `getExamLeaderboard` callable and maps the privacy-filtered projection. Direct Firestore listener/fallback reads were removed.
- Android rank fallback no longer queries raw Exam_Leaderboards. If the secure callable is unavailable, the UI reports service unavailable instead of using raw materializations.
- Firestore rules now deny direct client reads of Test_Leaderboards, Exam_Leaderboards, Ranking_Periods, XP_Leaderboard_AllTime and XP_Leaderboard_Weekly. Server/Admin SDK and callable APIs remain the access path. User_Ranks remains owner/admin-only; XP_Transactions remains owner/admin-only.
- Firestore Emulator tests now assert raw ranking materializations cannot be read directly, while sanitized callable APIs remain the client interface.

**Changed files:** `backend_functions/index.js`, `NEETViewModel.kt`, `firestore.rules`, `firestore.test.js`.

**Compatibility and rollout:** Android and backend callable code must be deployed together with the new Firestore rules. Existing direct-read clients will receive permission-denied until updated. Do not deploy rules alone. The public legacy `Leaderboard` collection is still retained for legacy compatibility and requires a separate usage/privacy audit before removal.

### 2.30 Latest CI audit and test-contract findings — 2026-11-07

The previously queued workflow run `42355909308` has now completed against commit `d2555badbdcb677d0ba528ca32319a7a114c1ba9`:

- Firestore security rules tests: **PASS**.
- Android source compilation and debug APK assembly: **PASS**.
- Android unit-test suite: **FAIL**, 306 tests executed with 19 failures. The failures are in pre-existing exam-pattern/scoring/parser/identity tests whose assertions conflict with the current fail-closed registry policy and current source-verified NEET/JEE configurations. Examples include expecting all 25 exam scaffolds to be verified, expecting obsolete NEET optional Section B / 200-minute timing, expecting stale JEE pattern data, and expecting unverified exam configs to score. These tests must be reconciled to the documented security contract; do not weaken production scoring or fabricate exam configurations merely to make stale assertions pass.
- Backend unit tests: **FAIL**, 70/71 passed. The per-test snapshot fixture omitted the newly required explicit `isBanned: false` active-state field. The fixture was corrected on the feature branch; no production logic was relaxed.

**Follow-up fixes on the feature branch:**
- Corrected the per-test rank unit-test fixture to explicitly represent an active canonical entry.
- Tightened ISO week validation to reject week 53 in years that do not actually contain ISO week 53 (for example, 2025-W53), while retaining valid week 53 years (for example, 2026-W53 and 2020-W53).
- Tightened daily-streak date parsing to reject impossible calendar dates such as 2026-02-30 rather than relying on JavaScript date normalization.
- Added regression tests for the ISO week-year boundary and impossible calendar dates.

**Current verification:** a new CI run was triggered for the latest feature-branch commit and is pending. The Android test-contract failures remain open and must be fixed by aligning obsolete tests with the actual fail-closed registry and verified scoring policies, then rerunning the full suite. No main-branch changes, production deployment, or production data writes were performed.

**Follow-up CI run `42364198857` findings:** the canonical-entry fixture correction passed. The added week/date regression tests exposed a second implementation defect: regex escaping in the first validator/date-hardening commit was doubled, so valid dates/weeks were rejected. This was corrected in commit `55c60de3fdd701b34af25b9f01e40751d74d5147`. The Android and Firestore jobs from that run were still in progress at the time of this note; backend job had failed on the two newly added validator/streak cases before this correction. A fresh run for the corrected code is required before considering those fixes verified.

### 2.31 Source-verification test contract and privacy/migration hardening — 2026-11-07

The Android test failures were traced to stale assertions that predated the source-verification gate. They expected:
- NEET UG to use the older 200-question / optional Section B pattern instead of the source-verified 2026 180-question compulsory pattern.
- JEE Main to be treated as a generic exam identity, although the verified scope is explicitly Paper 1 (B.E./B.Tech) and the canonical short name is `JEE Main P1`.
- CUET, UPSC, CAT, GATE, banking, CTET, UGC NET and other scaffolds to expose unverified question counts, durations, option counts, negative marking and scoring rules.
- Unknown OCR answer keys to be counted as unanswered/wrong instead of ungraded and excluded from ranking.

The following test updates were made without relaxing production scoring:
- Pattern integrity now audits only active, source-verified exam configurations; unverified registry entries must remain inactive with empty stages.
- NEET UG tests now assert 45 compulsory questions per subject, 180 total, 720 marks and 180 minutes.
- JEE Main tests now assert Paper 1 exact identity, 20 compulsory MCQs + 5 compulsory numerical questions per subject, 75 total, 300 marks and 180 minutes.
- Unverified exam tests now assert that pattern metadata and scoring APIs fail closed.
- Generic `JEE` and ambiguous `JEE Main` lookup are rejected; explicit `JEE Main P1` is used for scoring and registry lookup.
- Parser tests resolve the UPSC scaffold by canonical ID and assert that unverified configurations produce no generated question metadata.
- Unknown answer-key test data now includes the exact verified section/type and asserts `ungradedCount` rather than falsely treating it as an unanswered graded question.
- Canonical partition tests verify all 25 registered exam identities remain unique and reject ambiguous generic aliases.

Additional backend corrections:
- Profile visibility now fails closed: only an explicit `PUBLIC` value is publicly identified. Missing, malformed or unknown visibility values are treated as private in writes, profile synchronization, migration materializations and callable response redaction.
- Historical migration no longer assigns attempts with missing/invalid timestamps to `Date.now()`. Such attempts may contribute to the exam aggregate if otherwise verified, but are omitted from date-scoped period buckets because their period cannot be proven.
- Migration batch requests must continue from the server-saved cursor. A client-supplied cursor that differs from the checkpoint is rejected, and completed migrations return an explicit already-completed response. This prevents a stale/manual cursor from skipping users.
- Period identifiers reject year zero.

**Verification boundary:** the latest completed CI run `42364272119` was against the earlier commit `207c4c9...`, before these Android test and latest backend edits. In that run, backend unit tests and Firestore rules tests passed; Android compilation succeeded but its unit-test suite failed on the stale assertions listed above. The updated test suite and backend hardening require a fresh CI result. Do not treat this section as claiming the latest changes are already green.

No production Firestore data, Cloud Functions or rules were deployed or modified. All edits remain isolated to `feature/ranking-universal-integration-v1`; `main` remains untouched.

### 2.32 Full-paper integrity, scoring parity and concurrent migration checkpoint safety — 2026-11-07

**Official JEE Main 2027 source re-check:** the NTA Information Bulletin 2027 confirms Paper 1 has 20 Section A MCQs + 5 Section B numerical questions for each of Mathematics, Physics and Chemistry (75 questions / 300 marks), 180 minutes, +4/-1, and Section B answers must be integer values rounded to the nearest integer. The official bulletin is available from the NTA JEE Main Documents page; the accessible bulletin was checked at Chapter 2, pages 16–20.

**Scoring and ranking integrity changes:**
- Android scoring now resolves the canonical `examId` first, cross-checks the display exam identity, and requires the exact verified `stageId` + `paperId` before duration, answer selection or score calculation. A generic/ambiguous JEE label cannot silently select Paper 1; an explicit `jee_main` ID plus the exact Paper 1 stage/paper is required.
- Kotlin and backend scoring now treat explicit `SKIPPED` answers as unanswered, not wrong.
- Kotlin and backend reject missing or duplicate question IDs before direct scoring. Trusted answer-key IDs must also be unique; ranking answer-key documents cannot contain keys for questions absent from the test.
- JEE Section B now carries `integerAnswerRequired=true` in the Android section model and matching backend policy. Decimal answer keys are rejected during publication validation and become ungraded if encountered during direct evaluation. Integer-form values such as `12` and `12.0` remain numerically equivalent.
- Numerical-value questions are rejected if they contain MCQ options in either the options array or legacy option fields.
- Full-test ranking weight (1.0x) now requires the complete verified paper: NEET UG exactly 180 questions with 45 per subject; JEE Main Paper 1 exactly 75 questions with 20 MCQs + 5 numerical questions per subject. A complete full-paper composition cannot be mislabeled as a 0.2x part test.
- Explicit `rankingWeight` can no longer override contradictory Full/Part flags or labels. Conflicting metadata fails closed.
- The callable test-policy fixture now mirrors Firestore Rules exactly: only `APPROVED`, `PUBLISHED`, or `Published` plus explicit `isPublic=true` is accepted. Official status alone is not a public-read bypass.
- Migration checkpoint writes now use a transaction compare-before-advance guard. If concurrent batches start from the same cursor, a later smaller batch cannot regress a cursor already advanced by another worker, and no worker can overwrite a completed checkpoint. Duplicate user materialization remains safe because writes are idempotent.

**New regression coverage:** full-paper section distributions for NEET/JEE, full-vs-part weight conflicts, duplicate question IDs, JEE integer-only keys, numerical questions with hidden MCQ option fields, skipped-answer classification, exact exam identity, and concurrent migration checkpoint advancement.

**CI status:** these newest commits have triggered fresh GitHub Actions runs, but the runner queue is still reporting them as `queued`. The last completed baseline run remains `42364272119`: backend and Firestore Rules passed, Android unit tests failed on the old test contracts before the corrections documented in sections 2.31–2.32. No green status is claimed for the latest code until its own workflow run completes.

No production deployment or production data writes were performed. All changes remain on `feature/ranking-universal-integration-v1`; `main` remains untouched.

### 2.33 Live privacy verification and identity synchronization — 2026-11-07

The callable leaderboard responses now verify each returned identity against the current `Users_Data/{uid}.profileVisibility` document, not only the cached `profileVisibility` value in the leaderboard row. A user is named/linked only when both the materialized row and the current user document explicitly say `PUBLIC`. Missing user documents, missing/malformed visibility values, stale row values, or a failed profile lookup all fail closed to `Private Student` with a null user ID.

This check is applied to:
- All-time exam leaderboard.
- Half-month/month ranking leaderboard.
- Weekly and all-time XP leaderboard.

The profile-visibility synchronization trigger now compares raw normalized values rather than defaulting both missing and `PRIVATE` to the same value. Therefore a transition from a missing/legacy value to explicit `PRIVATE` or `PUBLIC` is no longer skipped; the trigger updates exam, period, XP and legacy materializations in 450-write batches. If live visibility reads fail, the callable hides identities instead of exposing stale names.

Android score calculation now consistently uses the canonical exam ID resolved from the test's explicit exam ID and exact stage/paper. It no longer passes an ambiguous display label directly to the scoring engine. Answer selection, timer validation, offline score calculation and post-submit leaderboard refresh share this exact identity resolver.

**Current verification:** latest code commit is `6744b09cd136eb42dd59f9a21a75e7b8a6627bef`. GitHub Actions has created workflows for this branch, but the latest runs remain queued; no latest-commit CI pass is claimed. The previous completed baseline `42364272119` predates the current test and code corrections. No production deployment, Firestore migration or main-branch modification has occurred.

### 2.34 Historical backfill revalidation and missing eligibility-helper correction — 2026-11-07

A further backend audit found that `index.js` imported `isEligibleScoringResult` from `rankingEngine.js`, but the function was absent from the module. The import therefore resolved to `undefined`; a qualifying server submission could fail when it attempted to evaluate ranking eligibility. The helper is now implemented and exported. It fails closed unless score and max score are finite and bounded, max score is positive, and `ungradedCount` is exactly zero. Boundary tests cover missing eligibility data, ungraded responses, invalid score bounds and zero maximum.

Historical migration previously trusted the stored attempt's `rankingWeight` and only checked the test's official/public/status flags. That could backfill a legacy attempt whose weight conflicted with its Full/Part classification or whose full-test paper was incomplete. The migration now:
- Resolves weight from current test metadata; a stored weight, if present, must match.
- Loads the official test, question set and protected answer-key document.
- Reuses `validateAndMergeAnswerKeys` so current status, public/official flags, unique IDs, trusted keys, exact subject/section/question type, option count, integer-only JEE NVQ policy and Full/Part composition rules are rechecked.
- Parses the immutable attempt answer payload and recalculates the score with the current verified scoring engine.
- Requires recalculated score and maximum to match the stored authoritative score/max within 0.001, and requires zero ungraded questions.
- Skips historical records lacking sufficient trusted source data rather than guessing. Such records need separate manual reconciliation; they are not silently included in AIR.

Question documents loaded from Firestore now always use the Firestore document ID as the canonical question ID, preventing a data field from overriding the actual document identity.

**CI remains pending:** the latest code changes are committed on the feature branch, but GitHub Actions runs are still queued. The latest green/failed state cannot be determined until a workflow for the current head completes. No production migration or deployment has been run.

### 2.35 Legacy leaderboard direct-read privacy gate — 2026-11-07

The legacy `Leaderboard` collection remains only for compatibility and profile/ban hygiene; new v2 scoring does not write new ranking totals there. Its Firestore read rule is now restricted to documents with explicit `profileVisibility == "PUBLIC"`. Private entries and legacy entries missing visibility metadata are denied. Regression tests verify:
- Explicit public row: readable.
- Explicit private row: denied.
- Legacy row with missing visibility: denied.
- Query constrained to `profileVisibility == "PUBLIC"`: readable.
- Unfiltered collection query containing mixed visibility: denied.

The current Android ranking ViewModel uses the sanitized v2 callable endpoints, not direct reads of raw v2 materialization collections. Legacy rows are still updated by the server-side profile visibility trigger; no production backfill has been run.

Latest code commit before this note: `399aa05025914ea6f0ae4889748d2497871ce705`. GitHub Actions remains queued for the latest branch commits; no current-head CI pass is claimed.

### 2.36 Canonical historical attempt selection — 2026-11-07

Historical backfill now claims the first server-verified, ranking-eligible attempt for a test before revalidation. If its current official test source, private answer key, answer payload, score or max score cannot be revalidated, the migration skips that test for that user rather than substituting a later repeat attempt. Explicit `isFirstQualifyingAttempt=false` records are excluded; where older records lack that field, deterministic timestamp/token ordering is used, preferring a valid timestamp over an unknown one.

This preserves the live product rule: one canonical first qualifying attempt per user/test. It also avoids silently converting a repeat attempt into the canonical score when an earlier attempt's source evidence is incomplete.

Latest code commit: `89da8e37264f89bc27a0dd699c7b3b1198149507`. GitHub Actions for this commit are still queued. The last completed CI run is an older baseline and does not verify these changes.

### 2.37 Historical attempt ownership and timing checks — 2026-11-07

Backfill now also requires the attempt's stored `userId` (when present) to match the user being rebuilt, and requires a finite `timeSpentSeconds` value between 0 and 86,400 seconds. Missing or invalid elapsed time is not inferred. These checks run before recalculation, alongside server-verification, first-qualifying-attempt, explicit ranking eligibility, canonical exam/stage/paper identity, current private answer-key validation, answer-ID validation, score/max reconciliation and zero-ungraded checks.

Latest code commit: `94e6cdd8970047c872751572eec760f7d4fe64ba`. Current-head CI is still waiting in GitHub's queued runner backlog; no green result is claimed.
