# AK TEST SERIES - Master Branch Code Reconciliation & Integration Plan

**Repository:** abhi952817-sudo/AK-TEST-SERIES-
**Audit date:** 2026-11-12
**Purpose:** Master decision register for all nine branches. Documentation only. This file does not authorize code merges, deployments, migrations, branch deletion or production data changes.

## 1. Rules

- main is the integration source of truth and remains protected from unapproved application-code changes.
- No whole-branch merge from divergent feature branches. Port reviewed files/functions with call-site, schema, API, security and test reconciliation.
- Preserve working screens, attempts, user data, test history, private question vault and existing contracts.
- No dummy questions, offline question-generation fallback, fabricated answers, guessed syllabus/marking rules, fake success, fake rank or fake usage/cost records.
- Every development phase records exact branch HEAD, commit SHA, files changed, test discovery/execution counts, PASS/FAIL/SKIP, CI run/job links and remaining defects.
- A test PASS is valid only for the exact SHA tested. Build/assemble PASS does not override a unit-test failure.
- No production Firebase/Firestore/Storage deployment or destructive migration without separate explicit approval.

## 2. X / Y / Z decision key

- X - KEEP: canonical behavior already present in main or unique requirements worth retaining.
- Y - PORT: useful implementation to port selectively after dependencies and tests are reconciled.
- Z - OBSOLETE: duplicate, stale, insecure or conflicting implementation; do not port. Remove only from active integration code after call-graph and regression proof.

## 3. Branch decisions

| Branch | X: Keep | Y: Port / develop | Z: Obsolete / avoid | Verdict |
|---|---|---|---|---|
| main | Deterministic UniversalQuestionGenerationPlanner, fail-closed routing, source-backed NEET syllabus and current contracts | Receive reviewed scoring, ranking, Gemini/PDF and CBT changes through a main-based integration branch | APK BuildConfig secrets, plaintext secondary keys, URL query API keys, confirmed dummy/offline generation fallbacks | KEEP / protected base |
| feature/ranking-engine-v2-2026-11-06 | Ranking-specific concepts/tests | Transaction/idempotency, first qualifying attempt, normalized score and visibility after test fixes | Whole branch/index/planner/scoring overwrite | SELECTIVE PORT |
| feature/ranking-universal-integration-v1 | Integration workspace, strict classification and server-authoritative rank model | Fix ranking engine, reconcile named Firestore, scoring, Room 20->21 and main planner | Whole index.js / ViewModel / database replacement | KEEP AS INTEGRATION WORKSPACE, BLOCKED |
| feature/universal-exam-scoring-v1-2026-11-06 | Exam-specific scoring contracts/tests | Ungraded keys, exact-set MSQ, finite numeric validation, optional-section caps, one Room migration | Old planner/default exam policy or second ranking engine | SELECTIVE PORT |
| feature/universal-gemini-ai-10-phase-2026-11-08 | API Management page and Student/Admin trust separation | Encrypted Student key store, validator, single connector, atomic quota, Groq/Cerebras and usage accounting | Offline parser/catalog generation, plaintext key store, APK secrets, duplicate transport | SELECTIVE PORT |
| feature/universal-pdf-prompt-contract-v1 | Source-backed prompt contract and strict route resolver | Real PDF/OCR, persistent reviewed syllabus, planner integration and provenance | Duplicate planner/transport, in-memory map as official syllabus, offline parser | SELECTIVE PORT WITH FIXES |
| fix/phase1-real-questions-only | Provenance, private review, fail-closed validation | Real PDF extractor and answer validator after tests | Duplicate ViewModel/API transport or dummy fallback | SELECTIVE PORT |
| restore-files/removed-env-gitignore-2026-10-19 | Historical ignore-file evidence | Verify secret scan and wrapper only | Application code from stale branch | OBSOLETE DEVELOPMENT BASE |
| usi | CBT requirements and unique UX behaviors | Rebuild missing CBT behavior on current base with canonical scoring | Whole stale branch, duplicate screens and stale exam rules | REQUIREMENTS REFERENCE |

## 4. Cross-cutting implementation decisions

### 4.1 Question generation / PDF
- Main's deterministic planner remains canonical for subject/question distribution.
- PDF route resolver validates exam, stage, paper, section and source; it feeds the planner, not replaces its distribution.
- Use original PDF bytes, correct MIME, real extraction/OCR, page-level provenance and verified source mapping.
- Missing OCR, malformed model output, unsupported routes, missing answer keys or incomplete distribution must fail closed and create/publish zero questions.
- Keep generated tests private/Under Review until Admin approval; answer keys stay in protected payloads.

### 4.2 AI connectors / key security
- Exactly one canonical Student key store using Android Keystore and UID-scoped encrypted data.
- Admin key server-side in Secret Manager only. Never embed in APK BuildConfig, ordinary SharedPreferences, Firestore-readable data, logs or URL query strings.
- Consolidate aiConnector.js and geminiProxy.js into one provider transport preserving auth, BYOK/Admin permission, rate limits, errors and usage metadata.
- Reuse existing API Management page in existing Sidebar. No duplicate Settings/page.
- Daily quota 1/2/3/custom must be enforced atomically on server and configurable by Admin.
- Groq/Cerebras use the same provider interface; usage/cost values must come from real provider metadata, never invented estimates.

### 4.3 Scoring / Room
- One canonical exam/stage/paper/section scoring policy shared by Android and backend.
- Port ungraded-key behavior, exact-set MSQ, finite numeric validation, decimal-safe marks and optional-section caps with golden parity tests.
- Room 20->21 migration is one migration only. Verify all supported older upgrade paths, backup/restore and existing attempts. Do not rely on destructive fallback.

### 4.4 Ranking
- One server-authoritative ranking implementation with transaction/idempotency and first qualifying attempt.
- Full Test 1.0x, Part/Chapter/Topic 0.2x only after verified test type and normalized score.
- Keep XP separate. Respect private profiles, exam isolation, ties, percentile denominator and eligibility.
- Never call platform rank official national AIR. Query failure must not become rank 0.

### 4.5 CBT
- Preserve timer, palette states, bilingual display, section behavior, save/mark/clear, submission and recovery.
- Rebuild only missing behavior on current player; no duplicate screen/navigation. Use canonical exam config and scoring.

## 5. Verified PASS / FAIL / OPEN status (2026-11-12)

| Area / check | Result | Evidence | Next action |
|---|---|---|---|
| Branch inventory / source comparison | PASS (audit only) | Nine branches compared; major overlapping source files inspected | Refresh exact heads before each port |
| Ranking integration Android | FAIL | Run 43213240446: unresolved assertNull in UniversalRankingAndIsolationTest.kt:38-39 | Fix and rerun |
| Ranking integration backend | FAIL | Same run: isEligibleHistoricalRankingTest is not defined at rankingEngine.js:323; three test files did not start | Implement/test missing predicate |
| Ranking Firestore rules tests | PASS | Same run reports rules tests passing; not deployment approval | Keep evidence for cited run only |
| Scoring backend tests | PASS | Run 43213218295 reports backend unit tests PASS | Rerun after changes |
| Scoring Firestore rules tests | PASS | Same run reports rules tests PASS | Rerun after changes |
| Scoring Android | FAIL | Same run: 299 tests, 1 failure in CrashResilienceTest; UnsupportedOperationException at DefaultSdkProvider.java:170 | Root-cause without weakening test |
| Scoring Android compile/assemble | PASS (partial) | Build/assemble completed before unit-test failure | Overall validation remains FAIL |
| Gemini Validator commit a44de807597eef0d155d6b917dca300395163b1d | OPEN | No workflow run returned for exact SHA | Verify head and run CI |
| Four Gemini Validator failures | OPEN | Exact-head proof all four resolved is absent | Fix/test individually |
| 341 Android tests / Debug APK | OPEN | 299-test run is not proof of 341 target; current inventory unknown | Discover and record exact count |
| Student/Admin connector, quota, PDF, Groq/Cerebras, usage-cost | OPEN | Source candidates exist; end-to-end tests not established | Implement in dependency order |
| Real PDF/OCR/no-fallback | OPEN | Real fixture and zero-question-on-failure proof absent | Golden PDF/OCR and call-graph tests |
| Ranking migration/backfill/concurrency | OPEN | No complete dry-run/emulator/concurrency evidence | Test in non-production |
| CBT runtime/UI | OPEN | Requirements exist; current-base behavior not proven | Compose tests after player audit |
| Secret-history scan | OPEN | Ignore rules inspected only; history scan not run | Scan and rotate exposed credentials |

PASS applies only to the named check in the cited run. FAIL blocks that phase. OPEN means unproven, not necessarily broken. No complete branch is declared release-ready.

## 6. Development order

1. Refresh branch heads and CI; fix ranking/scoring blockers without changing main application code.
2. Use a clean main-based integration branch; reconcile named Firestore and shared files before code ports.
3. Complete Student/Admin Gemini connector and secure key boundaries; then atomic daily quota.
4. Integrate real PDF extraction/OCR + prompt contract + response validator + main planner; enforce no-fallback and Admin review.
5. Integrate canonical scoring and one Room migration with parity and upgrade tests.
6. Integrate ranking with concurrency, idempotency, privacy and migration dry runs.
7. Port CBT requirements to current player and run full regression.
8. Run exact-head Android unit/UI suite, backend tests, Firestore/Storage emulator rules, security scans and Debug APK. Request merge/deployment approval only after evidence is green.

## 7. Mandatory change log

For every future code change, append one row before proceeding:

| Date | Branch | Before SHA | After SHA | Files/functions changed | Tests discovered / executed / skipped / failed | CI run/job | PASS/FAIL | Remaining |
|---|---|---|---|---|---|---|---|---|
| 2026-11-12 | All branches | Branch-specific audit snapshots | Documentation commits only | Branch plan documents only; no application source | No tests run in this documentation update | None | AUDIT ONLY | All OPEN items above |

A phase is PASS only when its acceptance tests are green on the resulting exact SHA. Compile-only or partial workflow results cannot close a failing suite.

## 8. Change boundary

This update changes documentation only. It does not alter application source, Firebase, Firestore, Storage, user data, CI configuration or production deployment. The protected main branch receives this plan document only.


## 2026-11-12 Expanded all-exam scoring + ranking audit

This is a source-level audit extension, not a claim that every supported exam has been exhaustively verified. The live source files inspected include `UniversalExamRegistry.kt`, `ExamConfiguration.kt`, `UniversalExamModels.kt`, and backend `examScoring.js`. The exact current branch heads and CI must be refreshed before implementation.

### Confirmed defects / high-risk mismatches
| ID | Area | Finding | Status | Required correction |
|---|---|---|---|---|
| SCORE-001 | Exam identity | Backend `evaluateTestAttempt` uses `String(targetExam || "NEET")`; null/empty identity can silently score as NEET. | FAIL | Reject missing identity; resolve canonical exam/stage/paper/year explicitly. |
| SCORE-002 | Source of truth | Android has both `ExamConfig/UniversalExamRegistry` and legacy `ExamConfiguration/ScoringRule` models. | FAIL / architecture risk | Select one canonical versioned contract; adapters only at boundaries; prove no competing defaults. |
| SCORE-003 | Platform parity | Backend contains hardcoded per-exam and per-section rules; Android has independent special cases. | FAIL | Move rules to one versioned contract consumed by both; shared golden fixtures. |
| SCORE-004 | Optional sections | Backend increments `maxPossible` for every loaded question before ignoring surplus attempts. | FAIL | Calculate attainable maximum from selected paper and deterministic valid selection, not all loaded questions. |
| SCORE-005 | Android default | Android configured scoring path can fall back to first section's `+4/-1` when section config is absent. | FAIL | Missing policy/section must return explicit unsupported/unverified error. |
| SCORE-006 | Answer normalization | Backend numeric equivalence uses `parseFloat` and a fixed tolerance; MSQ normalization/duplicates need symmetric validation. | OPEN | Define strict parser, exact-set semantics, per-policy numeric tolerance and duplicate rejection. |
| SCORE-007 | Decimal parity | Backend rounds aggregate marks to 2 decimals while Android may retain raw Double. | OPEN | Define decimal representation and one rounding boundary in the contract. |
| SCORE-008 | Ranking handoff | Ranking must consume verified canonical score/max/eligibility only; no independent scoring fallback. | OPEN | Golden-vector parity from scoring through ranking snapshot. |
| SCORE-009 | Official policy evidence | Registry presence or hardcoded values do not prove year/paper/section policy. | OPEN | Source URL/document, exam year, stage, paper, section, retrieval date and reviewer required before enabling. |

### All-exam coverage matrix required
For every registered exam and every year/stage/paper/section, record: canonical ID; aliases; official source; source version/date; total questions; duration; question types; options count; positive/negative/partial marks; numerical tolerance; optional/compulsory limits; dropped/bonus policy; unanswered policy; normalization/rounding; Android test; backend test; parity fixture; ranking eligibility. Unknown cells remain UNVERIFIED and must not be guessed.

The audit must explicitly include NEET UG, JEE Main, JEE Advanced, CUET, UPSC, SSC CGL/CHSL/MTS/CPO/GD, RRB NTPC/Group D/ALP/Technician/JE, IBPS PO/Clerk, SBI PO/Clerk, CTET/UPTET, UGC NET, CAT, GATE, NDA and CDS, plus every other active registry entry discovered from source. Do not assume variants share a rule.

### Ranking audit gates
- Confirm score normalization uses verified paper maximum and the same canonical score returned by Android/backend.
- Confirm Full Test 1.0x and Part/Chapter/Topic 0.2x are applied only after verified test type.
- Confirm first valid qualifying attempt, idempotent retry, conflict detection, transaction boundaries and concurrency.
- Confirm per-exam isolation, ties/competition rank, percentile denominator, eligibility, banned/private-user filtering and unavailable-rank behavior.
- Keep XP, streaks and league points separate from ranking score. Platform rank must not be represented as government/NTA AIR.
- Historical backfill requires dry-run, provenance, answer-key validation, pagination/resume, duplicate protection and rollback; no production migration is authorized.

### Test acceptance
- Shared language-neutral golden vectors consumed by Kotlin and Node.
- Every enabled exam/version has at least one official-source-backed fixture for every distinct scoring rule and question type.
- Boundary tests: unanswered, negative raw score, bonus/dropped, partial credit, MSQ exact set, option/index aliases, decimal tolerance, NaN/Infinity, duplicate/missing IDs, invalid key, missing config, optional-section caps and repeated attempts.
- Report discovered, executed, skipped and failed tests separately; test the exact commit SHA. A compile or APK build does not override a failed test suite.

**Status:** Audit findings recorded. No application code, Firebase, production data, ranking records or scoring behavior changed by this section. All-exam correctness and Android/backend parity remain NOT VERIFIED / release blocked.


## 2026-11-13 All-exam scoring/ranking audit handoff

A separate isolated audit branch was created from main SHA `8f7eee9c4beee7db3e447daeff0325cdb685e512`:
`audit/all-exam-scoring-ranking-2026-11-13`.

Audit report: `docs/ALL_EXAM_SCORING_RANKING_AUDIT_2026-11-13.md`.
Draft contract: `shared/scoring/scoring-policy.v1.json`.
Draft shared fixtures: `shared/scoring/scoring-golden-v1.json` (seven vectors; Kotlin and Node test consumers added on the audit branch).
JSON schema: `shared/scoring/scoring-policy-contract.schema.v1.json`.

Source inventory: 25 exam IDs, 25 stage IDs, 28 paper IDs and 99 section IDs. Nineteen official source sets/cycles are now located: NEET, JEE Main, JEE Advanced, CUET, UPSC CSE, SSC CGL, CTET, RRB ALP, UGC-NET, IBPS PO and IBPS CSA. Six registry exams still lack an official source packet or full stage/paper mapping. The rest remain UNVERIFIED. Even sourced exams have registry hierarchy mismatches requiring mapping.

New source-level blockers: NEET registry retains old optional Section B/200-minute scheme; JEE Main registry retains old 20+10 attempt-5 pattern and omits Paper 2A/2B; CUET registry says attempt 40/45 minutes instead of 50 compulsory/60 minutes; CTET only models one paper; UPSC omits qualifying GS Paper-II; SSC CGL omits Tier-II; RRB ALP omits CBT-2/CBAT; JEE Advanced hardcodes a static scheme although 2026 brochure defers marking details to per-question CBT instructions.

Ranking blockers confirmed in main: raw scores are accumulated without max-score normalization; arbitrary numeric rankingWeight is accepted; per-test rank is computed outside transaction; query failure writes rank 0; retry maps missing rank to 1; idempotency compares property-order-sensitive JSON serialization. See audit report for IDs and evidence.

**Status:** Registry inventory PASS; official policy coverage PARTIAL; Android/backend parity FAIL; contract/fixtures DRAFT NOT RUNTIME WIRED; ranking FAIL/OPEN; Backend six-vector historical run passed 62/62; current seven-vector exact-head run and Android tests/Debug APK remain unverified. This handoff is documentation-only and does not authorize main application-code edits, merge or deployment.


### 2026-11-13 shared scoring fixture test wiring

On isolated branch `audit/all-exam-scoring-ranking-2026-11-13`, added a Kotlin test and a Node test that both load `shared/scoring/scoring-golden-v1.json`. Gradle passes the shared fixture path to JVM tests; the audit workflow includes the Node fixture test, Android unit tests and Debug APK build. This is test wiring only; no scoring engine or ranking production call site was changed. Exact-head CI has not returned a status, so test execution and Android/backend parity remain NOT VERIFIED.


### 2026-11-13 exact CI evidence update

- Source evidence now covers 11 of 25 registry exams; 14 remain unverified and the 11 sourced exams still need complete stage/paper/section mapping.
- The audit branch has seven policy-vetted shared fixtures and Kotlin/Node consumers. Historical Backend runs passed 62/62 (six fixtures) and 65/65 (intermediate nine-fixture set); the latter included two IBPS vectors later removed because section-specific mapping was incomplete. The current seven-fixture head still needs its own run.
- Android CI completed 294 tests with 1 failure on the earlier head. The failure was Robolectric SDK 36 requiring Java 21 while the audit workflow used Java 17; Debug APK was skipped. The isolated workflow has been corrected to JDK 21 in commit `f1e967e341e782e1f8865e44f4f932caf875c9fd`; a fresh exact-head run is pending.
- No ranking transaction/concurrency or Firestore emulator tests have passed in this audit. No merge or deployment is authorized.


### 2026-11-13 typed ranking handoff contract

The audit branch's `shared/scoring/scoring-policy.v1.json` now includes a draft `rankingContract`: server-authoritative verified score input, 10,000-point normalization, strict Full=1.0/Part=0.2 weights, competition rank, percentile formula, null-on-error, first valid attempt, canonical answer hash and transactional concurrency. This is a contract draft only; no ranking runtime call site or transaction was changed and no ranking tests passed.


---

## 2026-11-19 — Live branch consolidation register (authoritative update)

### Current baseline and rules
- Current `main` after verified merges: `9a472bfd0de70fd6277be8b67bad8236bf6a5911`.
- This section supersedes the older nine-branch inventory above for current branch-head tracking; historical audit entries remain evidence, not current status.
- Live GitHub branch inventory at this check: **29 branches total (main + 28 non-main)**.
- Branch heads below are exact refs observed during this inventory. Re-fetch before any later merge because branch heads can move.
- No branch is to be deleted as part of consolidation. Preserve all heads until the final reconciliation and explicit cleanup decision.
- Target is one coherent `main`, not a forced merge of every divergent history. Unique, safe code is selectively ported; unfinished work and dependencies stay documented here.

### Verified merges into main
| PR | Branch | Change | Exact CI evidence | Merge commit | Status |
|---|---|---|---|---|---|
| #15 | `fix/strict-numerical-equivalence-2026-11-19` | Reject malformed/non-finite numerical operands before equality; add regression cases and run backend scoring tests in CI | Run `44530788784`: success; backend scoring test step passed | `d56229eff58b53bc8e7ea60ca017195b60aa5bb1` | MERGED |
| #16 | `fix/remove-client-ai-secret-metadata-2026-11-19` | Remove unused client Firestore registry sync and server Secret Manager resource names from Android client model | Run `44530905224`: success | `9a472bfd0de70fd6277be8b67bad8236bf6a5911` | MERGED |

Both were squash-merged after checking exact PR heads, clean mergeability and successful CI. No Firestore rules were loosened and no production secrets/data were changed.

### Complete live branch inventory and disposition
| Branch | Observed HEAD | Current disposition / next action |
|---|---|---|
| `audit/all-exam-scoring-ranking-2026-11-13` | `a89fb90e3f5d28c3c4f2be9b032c4654c4031a54` | Audit evidence and draft scoring contract; compare latest findings, port only tested compatible fixtures/contracts. |
| `audit/default-firestore-consolidation-2026-11-15` | `c7f5d6b8f5c17eac2411954a1ec604e529c6177a` | Preserve DB inventory/backup evidence; reconcile with live Firestore gate; no database cutover without verified inventory and restore. |
| `copilot/firestore-default-clean` | `dabeeaec90ddfde4fa69f4b89e71a5f2ffe1fcb4` | Compare exact diff against other Firestore fixes; deduplicate, then test default-database consistency. |
| `copilot/standardize-firestore-default` | `412d44070ee76393644ca9106ac9f685d3803d41` | Compare exact diff against other Firestore fixes; do not merge overlapping DB edits blindly. |
| `feature/community-completion-2026-11-13` | `a3bcc1ae6658e19872b63eb5bc416ce2aa8919f9` | Selective port only; server-authoritative shared-test validation and database target gate block whole-branch merge. |
| `feature/community-development-plan-2026-11-13` | `f9b8c2fe1102be3d3a703c7dfc25bcd83f10be11` | Read existing docs and compare overlap with Community completion/MVP; consolidate docs, not duplicate features. |
| `feature/community-mvp-completion-2026-11-13` | `c2a0fd5d682d927c28362ef9107eefb25e329458` | Compare against Community completion branch; preserve unique MVP code only after security/schema checks. |
| `feature/ranking-engine-v2-2026-11-06` | `36574191c50b5d5e241ace8a723d7f160011e681` | Backend ranking tests and Firestore rules tests PASS; Android validation FAIL in run `44531380449`. Diagnose exact failing test/build output, fix on branch, rerun full workflow; no merge yet. |
| `feature/ranking-universal-integration-v1` | `703ea68b28f9e45623e800f7a434cc445daa1e07` | Composite integration workspace; blocked by ranking, scoring, Room migration and Firestore contract reconciliation. Never merge whole branch. |
| `feature/secure-user-owned-ai-connectors` | `dabeeaec90ddfde4fa69f4b89e71a5f2ffe1fcb4` | Same HEAD as Copilot Firestore cleanup branch; verify whether true alias/duplicate before classifying; preserve branch. |
| `feature/student-universal-ai-connectors-v1` | `ac5eda2a485684871f6f448759fa2b8ebe01c696` | Preserve Android Keystore/AES-GCM and UID isolation as selective-port reference; provider set, coroutine cancellation, key lifecycle and multimodal/PDF integration remain incomplete. Refresh exact-head CI and docs before port. |
| `feature/universal-exam-scoring-v1-2026-11-06` | `6b99fb1dbb9b20bb1deb51b1c14d279e2bc3d227` | Selectively reconcile policy contract, exact-set MSQ, finite numeric validation and Room migration; run Kotlin/Node golden parity. |
| `feature/universal-gemini-ai-10-phase-2026-11-08` | `a2dd36c6997fa779745d5a0bd8c8c5acd34d04df` | Keep phase tracker; audit secure key store, real provider transports, quota, validation and approval dependencies before port. |
| `feature/universal-pdf-prompt-contract-v1` | `fbafd0c9376dd3d5bc0ac85a75a26447d9692311` | Preserve source-backed prompt/route contract; reconcile with main planner and real PDF/OCR pipeline; no duplicate planner/transport. |
| `fix/community-ci-recovery-2026-11-13` | `944b5f79808d467eac3cec7e524786a291d84b70` | CI recovery branch; compare workflow-only changes against current main workflows and validate exact diff before port. |
| `fix/firestore-default-database-2026-11-17` | `c82c61b6efbff2e415b202466d00cb96ef3934f3` | Candidate database consistency fix; blocked by production DB identity/region/data inventory and backup-restore proof. |
| `fix/phase1-real-questions-only` | `e75003b3dddbedfbe686bd6801ee4e20eaa40ac0` | Large divergent branch; selectively port fail-closed validation/extraction after callsite and numeric-format contract review. No whole-branch merge. |
| `fix/remaining-firebase-analytics-2026-11-15` | `6e5b1921108db04236196ce0e7ee3b9471c08af9` | Review analytics event privacy, consent, duplication and Firebase target; port only tested changes. |
| `fix/remove-client-ai-secret-metadata-2026-11-19` | `9293f723c088f4a575fbacf6ad5aca8f47d037ec` | PR #16 merged; retain source branch until final cleanup decision. |
| `fix/retain-single-firestore-db-9fa` | `e12bdbefb4d2f2b586ee85499061e8df8c1c87a2` | Candidate DB-target fix; compare with all other DB branches; blocked pending live project inventory and restore rehearsal. |
| `fix/strict-numerical-equivalence-2026-11-19` | `764078eef2da6a51e707c692adc75fb0e745b028` | PR #15 merged; retain source branch until final cleanup decision. |
| `fix/unify-firestore-database` | `828dfa2e5d1214842dd734612102872fdd37d945` | Candidate DB consolidation; compare all paths/IDs and ensure no accidental production data split. |
| `fix/use-default-firestore-everywhere` | `faa5fb9d033d3bb6bd060b56d054445127a60a60` | Candidate DB consolidation; compare against competing named DB branches; do not merge independently. |
| `integration/community-firestore-default-2026-11-15` | `ba45c04bbee78b7a11b72ef146f7707d8bc7b5ea` | Composite Community + Firestore integration; do not merge wholesale; split reviewed changes after database gate. |
| `integration/root-to-fruit-master-2026-11-13` | `e91120dbdc4a17382bcd66c0d5524641bfa08995` | Historical composite integration; inspect unique diffs and provenance, avoid re-importing already-present commits. |
| `integration/verified-branch-consolidation-2026-11-19` | `d00c028c20ec5e5d3eabe69eb47c2a78c8b8b549` | Audit workspace; transfer reviewed current findings into this main-branch register; no app feature code assumed merged. |
| `restore-files/removed-env-gitignore-2026-10-19` | `3614f1cdf9d17627169cf485427ba1f2d37e7b89` | Historical evidence only; check secret history and ignore rules; do not restore stale app code. |
| `usi` | `81a09ee158d56efd671a3dab0141e4b0588696af` | CBT/UX requirements reference; rebuild only missing behavior on current player after regression and navigation review. |

### Priority order for remaining consolidation
1. **Finish safe small fixes:** ranking V2 Android CI failure; inspect and repair exact failing test/build cause, rerun all jobs. Then selectively test other isolated low-risk fixes.
2. **Firestore single-database decision:** compare all five candidate DB branches as one change set; inspect Android, Functions, rules, indexes, Storage references; obtain live project IDs/regions/collections and verified backup + staging restore. No cutover until the same target is proven end-to-end.
3. **Scoring contract:** compare universal scoring branch and audit branch; settle one versioned exam/stage/paper/section policy, strict numeric format, MSQ exact-set behavior, decimal/rounding and optional sections; Kotlin/Node golden tests; Room migration upgrade paths.
4. **Ranking:** port one engine only; server transaction/idempotency, first qualifying attempt, normalized score, tie/percentile/privacy rules, no rank-zero fallback; migration dry-run and concurrency tests.
5. **AI connector:** retain one shared Universal AI Router/provider adapter contract; one Student encrypted UID-scoped key store; Admin Secret Manager only on backend; provider support must match approved Gemini/Groq/Mistral/Cloudflare Workers AI/Hugging Face list; test request auth, quotas, cancellation, account switching, key deletion and error mapping.
6. **PDF/question pipeline:** preserve original PDF; layout-aware extraction of text, images, diagrams, tables, equations, page order and provenance; offline extraction then AI fallback; fail closed with zero questions on extraction/validation failure; checkpoint/resume; Admin approval before publish.
7. **Community + CBT:** validate shared-test ownership/question sets server-side; enforce answer-key privacy; reconcile test/player schemas; verify timer, answer save, submit, result and recovery without duplicate screens.
8. **Final regression and release gate:** exact-head Android unit/UI/build; backend tests; Firestore/Storage emulator rules; secret scan; migrations/backup restore; no fake data; full student path smoke test. Report PASS/FAIL/NOT RUN separately.

### Current blockers and non-negotiable dependencies
- Ranking V2 run `44531380449`: backend unit tests PASS, Firestore rules tests PASS, Android validation FAIL. The Android job built the Debug APK successfully, then `app:testDebugUnitTest` failed: **293 tests completed, 1 failed**, specifically `CrashResilienceTest > classMethod`, with `UnsupportedOperationException at DefaultSdkProvider.java:170`. The same `CrashResilienceTest.kt` source is present unchanged in main and the ranking branch. This does not yet prove a ranking regression; compare/re-run the same test on the current main baseline and inspect the Robolectric/SDK dependency stack before changing the test or code. This branch is not merge-ready.
- Community full-branch merge blocked by client-bypassable test/question verification and conflicting Firestore database target.
- Firestore consolidation blocked until live database identity and recoverability are verified; source code alone cannot establish which production DB contains canonical records.
- Student connector branch is not the approved provider set and does not yet complete PDF/multimodal/approval workflow.
- Real-question-only and PDF prompt branches require proof of zero generated/published questions on all extraction/model failure paths.
- Composite integration branches may contain overlapping changes; never merge them together or on top of their source branches without per-file duplicate analysis.
- Build success does not close failed unit tests. Static review is not runtime proof. Unrun tests remain NOT RUN, not PASS.

### Change log — 2026-11-19
| Date | Branch / PR | Before / after SHA | Files / behavior | Verification | Result |
|---|---|---|---|---|---|
| 2026-11-19 | PR #15 | `764078eef2da6a51e707c692adc75fb0e745b028` → merge `d56229eff58b53bc8e7ea60ca017195b60aa5bb1` | Strict numerical comparison + regression tests + backend scoring CI step | Run `44530788784` success | MERGED |
| 2026-11-19 | PR #16 | `9293f723c088f4a575fbacf6ad5aca8f47d037ec` → merge `9a472bfd0de70fd6277be8b67bad8236bf6a5911` | Remove unused client metadata sync + Admin secret-name field | Run `44530905224` success | MERGED |
| 2026-11-19 | Ranking V2 | `36574191c50b5d5e241ace8a723d7f160011e681` | Numeric null/blank/boolean coercion fix + tests + docs | Run `44531380449`: backend PASS, rules PASS, Android FAIL | BLOCKED |

### Completion definition
This consolidation is complete only when every live branch has a final recorded classification (merged selectively / duplicate / obsolete with evidence / preserved as incomplete with dependency plan), all unique useful work is either in main or explicitly scheduled, the main branch builds and passes its required tests, and no unresolved merge conflict, duplicate implementation, secret exposure, data migration or answer-key privacy issue remains. Branch deletion is a separate later decision and is not part of this task.


### Ranking V2 Android failure — log review detail (2026-11-19)
- Workflow run: `44531380449`; job: `android-validation` (`135585295170`).
- Debug APK assembly completed successfully (`assembleDebug` PASS).
- Unit test task failed: `293 tests completed, 1 failed`.
- Failure: `CrashResilienceTest > classMethod`; reported exception: `UnsupportedOperationException at DefaultSdkProvider.java:170`.
- The corresponding `CrashResilienceTest.kt` content fetched from `main` and `feature/ranking-engine-v2-2026-11-06` is identical. Therefore do **not** label this as a ranking-engine code defect without further evidence.
- Next diagnostic: identify the exact failing method/stack trace and SDK/Robolectric dependency source; run this same test on current main and ranking branch with the same JDK/Gradle/dependency lock. If main also fails, record as baseline CI/test-environment defect; if only branch fails, isolate the dependency or source delta. Do not delete/skip/weaken the test to get a green workflow.
- Backend unit tests and Firestore rules tests in the same run passed; overall workflow remains FAIL until Android suite passes.


### Ranking V2 CI environment mismatch correction (2026-11-19)
- Main Android CI run `44528426052` ran `gradle testDebugUnitTest` successfully under JDK 21.
- Ranking V2 validation run `44531380449` used JDK 17 for Android validation and failed the same unchanged `CrashResilienceTest` class with `UnsupportedOperationException at DefaultSdkProvider.java:170`; Debug APK assembly itself passed.
- Diff review confirmed main `.github/workflows/android.yml` uses JDK 21 and Ranking V2 `.github/workflows/ranking-v2-validation.yml` used JDK 17. This establishes a workflow environment mismatch, although only a successful exact-head rerun can confirm the failure is resolved.
- Changed Ranking V2 validation Android job to JDK 21 (no test weakening or application-code change). Workflow commit `8561e176272599e501e8d4ac7d169544772a22f8`; branch documentation commit `59d45f61051ff11e0234075eafd74c88f0bc75fd`.
- Exact latest branch head `59d45f61051ff11e0234075eafd74c88f0bc75fd`; validation run `44535036938` queued at update time (workflow-only commit run `44535005393` also in progress). Merge remains blocked until all backend, Firestore rules and Android jobs pass on the latest exact head.


## 2026-11-19 follow-up — overlap and dependency analysis

### Current main and branch state
- Main at this update: `67d655de8c5cbadc04d9118bb9fd112f8bb25549`.
- Live branches rechecked: 29 total (main + 28 non-main).
- Open PRs rechecked: #1–#5, #8–#14 are open; PR #15 and #16 are merged and not in the open list.
- Two branch names are exact HEAD aliases: `copilot/firestore-default-clean` and `feature/secure-user-owned-ai-connectors`, both at `dabeeaec90ddfde4fa69f4b89e71a5f2ffe1fcb4`. Treat them as one content lineage for deduplication; preserve both refs until final cleanup decision.
- `copilot/firestore-default-clean` is behind main with no unique diff; do not merge it.

### Firestore consolidation — exact conflict evidence
A grouped comparison of all Firestore candidate branches confirms they are not interchangeable:
- Main Android resource + helper selects named DB `ai-studio-android-aktestse-9fa19369-81c5-4d42-bf79-ab4f9f40d4bf`.
- Main Cloud Functions `backend_functions/index.js` also explicitly selects `...9fa19369...` through `FIRESTORE_DATABASE_ID`.
- Main `firebase.json` targets `...9fa19369...` for Firestore rules/index deployment.
- `fix/firestore-default-database-2026-11-17` changes Android, Functions and `firebase.json` to `(default)`.
- `fix/use-default-firestore-everywhere` also chooses `(default)`, but its diff is much wider: Android backup, upload, TestReview, Storage rules/tests, workflow and extra integration-test changes are bundled with database edits.
- `fix/retain-single-firestore-db-9fa` selects a different named DB `...4a0dbfd1...`; it conflicts with both current Main and default-database candidates.
- `copilot/standardize-firestore-default` and `fix/unify-firestore-database` remove named database selection in app/backend; the latter bundles additional unrelated app/analytics/community edits.
- `integration/community-firestore-default-2026-11-15` combines Community + Firebase/Storage + workflow + schema/rules changes (over 200 commits ahead); it is not a safe atomic merge.
- The `audit/default-firestore-consolidation` branch is documentation-only relative to Main in its current file diff; preserve the audit findings but do not create duplicate audit documents on Main.

**Decision remains BLOCKED:** source diffs prove which database each branch targets, but do not prove which live Firebase database contains canonical production data. Do not merge any database-ID cutover or change rules/index deployment target until project ID, database IDs/regions, collections and record counts, Functions target, Android target, backup/export, and a successful staging restore are verified together. No production database is to be deleted or silently redirected.

### Scoring, PDF and AI overlap — merge policy
- `feature/universal-exam-scoring-v1-2026-11-06` is 147 commits ahead / 116 behind Main and changes shared scoring, MainActivity, NEETViewModel, backend Functions, Firestore rules/indexes and Room schema version 21. PR #2 targets the Ranking V2 branch, not Main. Do not merge its whole branch or PR #2 directly to Main. First isolate the versioned scoring contract, MSQ exact-set behavior, numeric format/rounding policy, backend/Kotlin parity vectors and Room migration; then port/test as one compatible slice.
- `audit/all-exam-scoring-ranking-2026-11-13` adds golden vectors and versioned scoring-policy JSON/schema. This is useful audit/test material but overlaps scoring policy work; compare policy semantics before selecting one canonical contract. Do not create duplicate policy documents or competing vector suites.
- `feature/universal-pdf-prompt-contract-v1` changes core pipeline, NEETViewModel, MainActivity and removes/replaces existing parser/extractor components. It is 143 ahead / 109 behind Main. Preserve its route/prompt contract and tests as candidates, but no whole-branch merge until extractor behavior, source provenance, image/layout extraction, failure handling and admin approval integration are verified.
- `feature/universal-gemini-ai-10-phase-2026-11-08` is a broad 392-ahead / 116-behind branch touching scoring, PDF, AI context, Student key storage, UI, schema and workflow. It overlaps both scoring and PDF branches. Use as reference/source for selective components only; do not merge whole.
- `fix/phase1-real-questions-only` is 196 ahead / 116 behind Main and overlaps scoring, PDF pipeline, NEETViewModel, AI policy and Student key storage. Preserve fail-closed validation/extractor candidates, but resolve scientific-notation vs strict decimal policy and verify callsites/tests before port.
- `fix/remaining-firebase-analytics-2026-11-15` is a broad Community + Firestore + Storage + Analytics composite (201 ahead / 10 behind); it is not an isolated analytics fix. Split by file/behavior and inspect consent/privacy and duplicate event handling before any port.

### Ranking V2 — latest CI gate
- Latest Ranking V2 head observed: `59d45f61051ff11e0234075eafd74c88f0bc75fd`.
- JDK mismatch correction was applied to the branch's validation workflow: JDK 21 now matches Main Android CI (which uses JDK 21). No app code or test was weakened.
- Exact-head run `44535036938`: backend unit tests PASS, Firestore rules tests PASS, Android validation still IN PROGRESS at this check. Older run `44535005393` for workflow-only head also had backend/rules PASS and Android IN PROGRESS.
- Do not merge Ranking V2 or PR #1 until the exact latest-head run completes with all jobs PASS. If Android still fails, inspect the full stack trace and test XML before any code/test changes.
- PR #2 remains based on Ranking V2, so it must be reconciled after Ranking V2 is validated, then re-targeted/rebased through a reviewed integration branch; never merge it straight to Main in its current state.

### Immediate next sequence
1. Wait for exact-head Ranking V2 run `44535036938`; if green, inspect PR #1 complete file diff and merge only after reviewing its Room migration, server-side ranking writes, privacy, idempotency, rules and compatibility with current Main.
2. Keep all Firestore ID branches blocked pending real Firebase inventory/backup/restore evidence. The minimal default candidate and the wider default candidate must not be conflated.
3. Reconcile scoring policy from audit branch + Universal Scoring PR #2 + Ranking V2 as a single canonical contract. Keep one golden-vector source and test Kotlin/Node parity.
4. Reconcile PDF prompt contract + real-questions-only + AI 10-phase work by components and tests; one pipeline, one provider router, one validation chain. Do not keep parallel competing implementations.
5. For Community/Analytics composite branches, port isolated reviewed server-validation and UI/test components only after test ownership, answer-key privacy, Storage paths and Firestore target gates pass.
6. Recheck every branch head before creating any PR/merge. Update this register after each accepted slice.



### Golden scoring fixture audit — additional blocker
- Static review of `audit/all-exam-scoring-ranking-2026-11-13` confirms the shared policy file declares `DRAFT_NOT_RUNTIME_WIRED` and explicitly disables scoring/ranking for the listed exams until adapters and parity tests exist. This is a useful fail-closed design and should be preserved.
- The same branch's fixture JSON labels vectors `SOURCE_BACKED_POLICY_VECTOR` and includes official-source URLs, but the source PDFs/notices and exact marking rules have not yet been independently fetched/reviewed in this integration pass. Do not treat those labels as verified evidence.
- The NEET policy entry itself says the 2026 pattern is 180 compulsory questions and notes the old Section A/B optional scheme is obsolete, while the policy object still carries old-style Section A/B section IDs and the sample vector is only a synthetic four-question scoring example. These can be test mechanics, but must not be mistaken for a complete exam-pattern proof.
- Before porting the shared golden-vector suite, verify each fixture against the matching exam-year official notice, identify whether each is a synthetic unit vector or official pattern fixture, correct inconsistent metadata, and add explicit tests for MSQ exact-set, numerical parsing/tolerance, optional sections, dropped questions, partial marking and rounding. Then wire the same JSON fixture file into Kotlin and Node without creating parallel fixture sources.
- Current disposition: preserve as a candidate; no fixture or scoring policy file has been copied into Main yet.



### Student-owned AI connector branch — selective-port review
- Current branch head `feature/student-universal-ai-connectors-v1`: `ac5eda2a485684871f6f448759fa2b8ebe01c696`; compare to Main: 60 ahead / 10 behind, 13 changed files.
- Candidate value: AES-GCM ciphertext in app-private preferences with non-exportable AndroidKeyStore AES key; UID/provider-derived aliases; provider keys placed in request headers rather than URLs; request/response UID rechecks; backup exclusion entries; local provider usage metadata.
- **Do not port the branch wholesale.** Its provider enum is Gemini/OpenAI/Groq/Anthropic/Cerebras, which conflicts with the approved provider set (Gemini/Groq/Mistral/Cloudflare Workers AI/Hugging Face Inference Providers). Provider transport supports text only; it is not the shared Universal AI Router or the PDF/image extraction pipeline.
- Security-test gap: `StudentAIKeyStoreTest` verifies UID-scoped preferred provider/model metadata and clearing behavior, but does not exercise real AndroidKeyStore AES-GCM save/read round-trip, tamper detection, key invalidation, account switch, reinstall/restore, or device backup/transfer behavior. CI success alone does not prove hardware/device keystore behavior.
- Cancellation gap: synchronous OkHttp `execute()` inside `withContext(Dispatchers.IO)` rethrows CancellationException but does not explicitly cancel the underlying OkHttp Call when coroutine cancellation occurs.
- Selective-port candidate: refactor the encrypted local key-store mechanism behind the approved provider IDs (or a provider-neutral stable ID), then add Robolectric contract tests plus instrumentation/device tests for encryption round-trip, tampering, key loss, UID isolation, sign-out/account-switch clearing and backup exclusions. Only after those pass should the approved provider adapters be connected.
- Current disposition: preserve as a security reference; no Student connector app code merged into Main.

### AI connector CI evidence
- Student connector branch exact head has a dedicated CI workflow; prior checkpoint reported a passing run on an earlier exact head, but current head `ac5eda2a...` must be revalidated before any selective port.
- Universal Gemini AI branch head `a2dd36c6997fa779745d5a0bd8c8c5acd34d04df` has a successful dedicated workflow run `43803398049`; that verifies only the workflow's declared checks, not compatibility with current Main or completion of the approved five-provider architecture. Keep the branch as a component reference, not a whole-branch merge candidate.


### Ranking V2 callable/security review — exact-head code findings
Reviewed Ranking V2 head `59d45f61051ff11e0234075eafd74c88f0bc75fd` after its exact-head CI completed successfully:
- Workflow run `44535036938` completed `success`: `backend-unit-tests`, `firestore-rules-tests`, and `android-validation` all PASS. This validates that workflow's tests/build only; it does not close the correctness/privacy gates below.
- PR #1 remains Draft, open, and heavily diverged (110 commits ahead / 119 behind Main at this comparison). It changes the backend callable, Android UI/ViewModel, Firestore rules/indexes and tracker. No whole-PR merge.

**Confirmed code defects requiring correction before ranking integration:**
1. **Rank failure is represented as rank zero:** in Ranking V2 `backend_functions/index.js`, the pre-transaction Test_Leaderboards count query catch block sets `testRank = 0`, `testTotalCandidates = 0`, and `attemptPercentile = 0.0` (around lines 249–253). The callable then persists/returns these values as if they were a numeric ranking result. This conflicts with the explicit requirement that unavailable rank never be represented as rank 0. Change to an explicit unavailable/null state and test query failure and idempotent retry behavior.
2. **Test leaderboard initially writes private user's real name:** around lines 535–545, `Test_Leaderboards/{testId}/Entries/{userId}` is written with `displayName: userData.name || "Student"` without applying `profileVisibility`. The collection is readable by every authenticated user under the branch's rules. The later profile-sync trigger can anonymize it after a profile update, but does not prevent initial exposure. Apply the same private-profile anonymization at creation and test the public payload for both PUBLIC and PRIVATE users.
3. **Percentile/rank query is outside the transaction:** the population count and higher-score count are performed before `runTransaction`, so the saved percentile/rank can be stale under concurrent submissions. This must be explicitly accepted as approximate or redesigned using a consistent/serialized materialization strategy; do not describe it as a transactionally consistent snapshot.
4. **Idempotent retry defaults missing rank data to fabricated values:** existing-attempt response uses `existing.percentile || 0.0`, `existing.testRank || 1`, and `existing.testTotalCandidates || 1` (around lines 291–294). Once rank is made unavailable, these fallbacks must preserve null/unavailable rather than invent rank 1 or percentile 0.

**Integration decision:** Ranking V2 exact-head CI is green, but PR #1 remains BLOCKED by the above correctness/privacy findings, plus the already recorded transaction, backfill, Firestore database, legacy compatibility and Room/UI reconciliation gates. Fix and test the isolated callable behavior on the Ranking branch, then create a narrow integration change against current Main; do not merge the 14-file divergent PR wholesale.


### Community PR #8 recheck — 2026-11-19 latest head
- Latest PR #8 head is `a3bcc1ae6658e19872b63eb5bc416ce2aa8919f9`; PR remains Draft/open, 22 files, 4,650 additions / 312 deletions; compare to current Main is 184 ahead / 14 behind.
- Exact-head GitHub check-runs show one completed check named `build` = SUCCESS. Do not carry forward older audit notes saying the current head has separate Community Rules CI success: that was a different older SHA. Current exact head still needs the Community Rules Emulator workflow/check and complete backend test suite.
- `communityValidation.js` has useful pure validation/sanitization helpers and tests: registered exam/subject mapping, MCQ input bounds, owned image path/host checks, answer/explanation stripping from public question projections, and legacy public/private question splitting.
- Current callable integration does not provide the missing trusted Community test-share operation that independently validates the full underlying question set and subject distribution before publishing. The reviewed Community sharing flow still relies on Android-side verification; therefore COM-SHARE-003 remains a hard security blocker.
- Preserve the pure validation module/tests as selective-port candidates, but do not merge the Community PR's `index.js`, rules, indexes, Storage rules or UI wholesale. Its Cloud Functions still target named database `...4a0dbfd1...`, conflicting with Main's `...9fa19369...`; this is not resolvable without live Firebase inventory and backup/restore evidence.
- Community implementation is valuable but not integration-ready. Next safe unit is to extract/port the independent pure validators only after checking their API against Main's current backend test harness; server-side test-share validation must be implemented and emulator-tested before any public share writes are accepted.
