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
