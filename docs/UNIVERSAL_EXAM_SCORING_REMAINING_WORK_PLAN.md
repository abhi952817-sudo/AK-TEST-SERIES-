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

## 2026-11-12 Cross-branch code reconciliation (X/Y/Z)

### X — KEEP
- Keep exam-specific scoring engine and focused tests as the reference for advanced scoring behavior.
- Keep explicit exam/stage/paper/section configuration. Unknown marking rules fail closed.

### Y — PORT / RECONCILE INTO MAIN
- Port ungraded answer-key handling and explicit ungraded counts.
- Port exact-set MSQ, finite-number validation (reject NaN/Infinity), decimal-safe marks and optional-section caps.
- Require Android/backend parity through shared golden test vectors.
- Reuse Room schema 21 / Migration20To21 only once. Both scoring and Gemini branches contain identical migration content in this audit; do not create duplicate migration histories.

### Z — OBSOLETE / DO NOT PORT
- Do not replace main's newer fail-closed subject planner or current exam routing with old defaults.
- Do not create a second ranking implementation here.

### PASS / FAIL / OPEN evidence (2026-11-12)
| Check | Result | Evidence / action |
|---|---|---|
| Android scoring validation | FAIL | Run 43213218295: 299 tests, 1 failure in CrashResilienceTest; UnsupportedOperationException at DefaultSdkProvider.java:170. |
| Backend unit tests | PASS | Same run reports backend tests PASS. |
| Firestore rules tests | PASS | Same run reports rules tests PASS. |
| Android compile/debug assemble | PASS (partial) | Compilation/assemble completed, but Android validation remains FAIL due to unit test. |
| 341-test target / current-head APK | OPEN | 299-test run is not proof of the 341-test target. |
| Older Room upgrade paths | OPEN | 20→21 test alone is insufficient. |

### Ordered work
1. Reproduce/fix CrashResilienceTest without weakening the test.
2. Run focused scoring tests, full Android suite and exact-head Debug APK.
3. Verify exam policies against official year-specific sources.
4. Test Android/backend parity for MCQ, MSQ, NAT/TITA, decimal, negative, ungraded and optional sections.
5. Test migrations and backup/restore from every supported schema version.

**Verdict:** SELECTIVE PORT. Android validation is FAIL until the crash-resilience failure is fixed.


## 2026-11-12 live parity audit — main HEAD reconciliation

**Main comparison base:** 556617dd978643449c8457fc0bd8fb551773903b (current main base returned by GitHub compare at audit time).  
**Merge base:** d2feae47f908b705209c27b8e61e624f94c400d7.  
**Branch divergence:** scoring branch is 135 commits ahead and 94 behind main. This is not safe for a wholesale merge.

### Source-level findings (not yet test-verified)

| Area | Finding | Status |
|---|---|---|
| Canonical configuration | Main has both ExamConfig/UniversalExamRegistry and legacy ExamConfiguration/ScoringRule models. Scoring source currently consults the registry; legacy model is a potential second source of truth. | OPEN |
| Unknown exam fallback | Backend evaluateTestAttempt constructs String(targetExam || "NEET"), so empty/null exam identity silently becomes NEET before validation. This contradicts the existing unknown-exam fail-closed test intent. | FAIL |
| Android/backend scheme resolution | Both contain separate hardcoded special cases for JEE Advanced, NDA, CDS, GATE and SSC MTS, plus separate exam-name tables. Same-looking code is not a shared authoritative configuration and can drift. | FAIL |
| Per-question rules | Both engines resolve special cases separately and generic registry fallback separately. Alias/type/section normalization and missing-section behavior require golden parity tests. | OPEN |
| Optional question caps | Main Android and backend both add every question's positive marks to maxScore before optional surplus is ignored. The reported maximum can therefore exceed the maximum attainable score for papers with optional sections. | FAIL |
| Configured scoring path | Android calculateScore(examConfig,...) defaults missing section config to the first section's +4/-1 values and does not apply the same optional-section selection policy as evaluateTestAttempt. | FAIL |
| Numeric/MSQ | Scoring branch adds finite numeric validation and exact-set multi-select; these are useful candidate changes. Must compare both code paths and test index/letter normalization, duplicate tokens and numeric tolerance. | PARTIAL |
| Fractional marks | Backend rounds aggregate fields to 2 decimal places while Android retains raw Double values. A single documented rounding policy and decimal-safe parity vectors are still required. | OPEN |
| Question identity | Duplicate/missing question IDs, unknown question types and malformed answers need explicit validation before scoring; no proof of complete symmetric rejection found in this source inspection. | OPEN |
| Policy authenticity | Hardcoded values in code/tests are not official policy evidence. Each exam needs version/year/stage/paper/section official source provenance and review before verified=true. | OPEN |

### Required canonical contract

- Resolve by canonical examId + examYear/version + stageId + paperId + sectionId + questionType; display names are aliases only and ambiguous aliases must fail closed.
- Read marks, negative penalty, partial-credit policy, numerical tolerance, unanswered rule, dropped/bonus policy and optional attempt limits from one versioned configuration contract. Do not infer a policy from a broad exam label.
- Score only verified answer keys. Preserve an explicit UNGRADED count/status; do not silently treat an unverified key as correct, wrong or a normal unanswered question.
- MCQ_SINGLE uses one accepted option; MSQ uses exact-set matching unless that paper's official rule explicitly defines partial credit; NAT/TITA uses finite decimal/integer parsing and configured tolerance.
- Apply compulsory/optional selection deterministically using submitted selection metadata where required. Do not silently choose the first N optional questions based on list order.
- Compute maximum attainable marks from the selected paper's actual rules and valid question selection, not blindly from all loaded questions.
- Use the same decimal representation/rounding boundary on Android and Backend. Backend remains authoritative for official attempts; client result is a parity-checked preview only.
- Return explicit validation errors for unsupported exam versions, unknown question types, missing section policy, malformed IDs, duplicate IDs and invalid answer keys.

### Test strategy

Create language-neutral golden fixtures (JSON) consumed by Kotlin and Node tests. Each fixture records config version/source, input questions/answers, expected counts, positive marks, penalty, raw/final/max score, accuracy, ungraded count and error code. Cover NEET optional sections, JEE Main/Advanced distinct question types, CUET, UPSC, SSC tiers, CTET/UGC NET, IBPS/SBI, CAT MCQ/TITA, GATE 1/2-mark MCQ/MSQ/NAT, NDA/CDS section policies, all unanswered, negative raw score, dropped/bonus, partial credit only where official, decimals/tolerance, NaN/Infinity, duplicate IDs, missing config and aliases.

### Execution order

1. Fix the empty/unknown exam fallback and add regression tests.
2. Define one versioned scoring configuration contract and remove duplicated policy resolution only after all call sites and data migrations are mapped.
3. Correct optional-question selection and max-score computation in both paths.
4. Add shared JSON golden vectors and run Kotlin + Node against the same fixtures.
5. Validate official year-specific policies for every enabled exam. Disable/unverify unsupported configurations rather than guessing.
6. Run full Android, backend, Firestore rules and exact-head Debug APK build; record counts and SHA.
7. Only then port the verified scoring contract into the ranking integration branch. No main merge or deployment without approval.

### Current audit verdict

**FAIL:** empty exam identity can fall back to NEET; optional-section max score is inflated; Android configured scoring path has hardcoded +4/-1 defaults; Android and Backend independently encode exam policies.  
**PARTIAL:** finite-number and exact-set MSQ work on the scoring feature branch is a candidate to retain after parity tests.  
**OPEN:** complete official source-backed exam matrix, golden fixtures, current-head test run, all-exam Android/backend parity and APK verification.  
**Change boundary:** this update changes documentation only. No application code or production data was modified.


## 2026-11-12 Full all-exam marking and Android/backend parity audit extension

### Confirmed source-level defects
1. **Missing exam identity silently becomes NEET (FAIL).** In main `backend_functions/examScoring.js`, `evaluateTestAttempt` currently initializes `exam` with `String(targetExam || "NEET")`. Null/empty identity must be rejected before any score calculation.
2. **Duplicate configuration models (FAIL / architecture risk).** `ExamConfig` / `UniversalExamRegistry` coexist with legacy `ExamConfiguration` / `ScoringRule`. Establish one canonical versioned config and boundary adapters; do not allow two policy stores.
3. **Independent Android/backend special cases (FAIL).** Backend `getMarkingSchemeForExam` and `getMarkingSchemeForQuestion` contain hardcoded rules for NEET, JEE, GATE, CUET, UPSC, NDA, CDS, SSC, Banking, Teaching, CAT and Railways. Android has its own rules. This is not guaranteed parity and is not year/paper/section specific.
4. **Optional-section maximum inflation (FAIL).** Backend adds each question's positive marks to `maxPossible` before section-attempt caps ignore surplus questions. The maximum can exceed the actual selected-paper maximum.
5. **Unsafe Android default policy (FAIL).** A configured Android scoring path can choose the first section's `+4/-1` when section policy is missing. No silent default is permitted.
6. **Numeric and answer normalization (OPEN).** Backend uses `parseFloat` and a fixed `0.0001` tolerance. It must not accept malformed numeric prefixes; tolerance must be explicit per verified policy. MSQ must normalize indices/letters, reject duplicate/unknown selections and compare exact sets unless the official scheme explicitly provides partial credit.
7. **Decimal rounding (OPEN).** Backend rounds aggregate fields to two decimal places; Android may keep raw Double values. One documented decimal precision/rounding rule is required.
8. **Answer-key integrity (OPEN).** Validate duplicate/missing question IDs, unknown question types, missing/unverified keys and answer-map references symmetrically. Ungraded questions must remain explicit and must not silently count as correct, wrong or unanswered.
9. **Official pattern verification (OPEN).** Every enabled exam/year/stage/paper/section requires official source provenance and review. Registry defaults and code comments are not evidence.

### Required exam-by-exam matrix
Create a row for every active registry exam and each distinct stage/paper/year/section. Required columns: canonical exam ID, alias, year/version, stage, paper, section, official source and retrieval date, total questions, duration, question type, option count, positive/negative/partial marks, tolerance, compulsory/optional cap, dropped/bonus rule, unanswered rule, max-score formula, rounding rule, Android result, backend result, golden fixture ID, ranking eligibility and review status. Do not mark all exams verified from a single sample or generic exam-level entry.

At minimum inspect NEET UG, JEE Main, JEE Advanced, CUET, UPSC, SSC variants, RRB variants, IBPS/SBI variants, CTET/UPTET, UGC NET, CAT, GATE, NDA, CDS and all additional active registry IDs. Different stages/papers must be separate rows. Unknown or unsupported policies remain UNVERIFIED and disabled for official ranking.

### Ranking integration contract
Scoring must return a typed result containing canonical exam/stage/paper/version, score, raw score, max score, counts, ungraded count, eligibility and policy version. Ranking consumes this exact server-authoritative result. It must not recalculate marks or infer exam type from display names. Normalization to 10,000 and Full Test 1.0x / Part/Chapter/Topic 0.2x happen only after test type and max score are verified. XP remains separate.

### Par ity acceptance and implementation order
1. Reject missing/ambiguous exam identity and add regression tests.
2. Remove Android +4/-1 implicit fallback and add missing-section tests.
3. Fix maximum-score calculation with deterministic optional-section selection metadata.
4. Freeze one versioned policy contract and one alias resolver.
5. Build shared JSON golden fixtures consumed by Kotlin and Node.
6. Align numeric parsing, MSQ exact-set, ungraded handling and rounding.
7. Verify official policy source for each enabled year/stage/paper; disable unsupported/unverified entries.
8. Pass the typed score to ranking; test first attempt, retry, tie and exam isolation.
9. Run full Android/backend/emulator suites and exact-head Debug APK; record counts and SHA.

**Current verdict:** FAIL for all-exam parity and the defects above. OPEN for official per-exam coverage, exact current-head CI, migration and ranking end-to-end. No source code or production behavior was changed by this documentation update.
