# Question-Level Scoring Integration Audit
Date: 2026-11-23
Branch: `feature/question-level-scoring-strategy-2026-11-23`
PR: #25 (Draft)
Scope: all scoring, policy, attempt-submission, exam-registry, tests, and CI files reachable in this branch.

## Executive decision

Do not wire the generic evaluator into the live attempt pipeline yet. The new Kotlin and Node.js engines are currently isolated, while production attempt submission still calls the legacy scalar evaluator. This separation is correct for compatibility, but the next integration must be designed across the entire attempt lifecycle rather than by adding a direct function call.

No production data, Firestore configuration, Firebase deployment, stored score, rank, or main-branch code was changed during this audit.

## Files and components inspected

- Android scoring: `ExamScoringEngine.kt`, `QuestionLevelScoringEngine.kt`, `ScoringPolicyResolver.kt`, `ExamConfiguration.kt`, `UniversalExamRegistry.kt`.
- Android tests: `ExamScoringEngineTest.kt`, `QuestionLevelScoringEngineTest.kt`, `ScoringPolicyResolverTest.kt`, `UniversalExamRegistry2026Test.kt`, `UniversalRankingAndIdentityTest.kt`, `UniversalRankingAndIsolationTest.kt`.
- Backend scoring: `examScoring.js`, `questionLevelScoring.js`, `scoringPolicyResolver.js`, `legacyScoringGate.js`, `index.js`.
- Backend tests: `examScoring.test.js`, `questionLevelScoring.test.js`, `scoringPolicyResolver.test.js`, `legacyScoringGate.test.js`.
- Build and CI: `backend_functions/package.json`, `.github/workflows/android.yml`, `.github/workflows/question-level-scoring.yml`.
- Scoring contracts: Phase 3 versioned scoring contract and both question-level strategy contract documents.

Repository tree at audit time contained 262 tracked files. The audit traced the scoring-related source and test files above plus the production submission entry point and exam registry.

## Findings

### Critical: production attempt pipeline does not call the new question-level engine

`backend_functions/index.js` loads `examScoring.js` and `scoringPolicyResolver.js`, but does not import or call `questionLevelScoring.js`. After resolving a versioned policy it still calls `evaluateTestAttempt(...)`, whose evaluator consumes scalar `positiveMarks` and `negativeMarks` and classifies answers as correct/wrong.

Consequences:
- Partial outcomes are not yet used for authoritative saved scores.
- The attempt record has no `partialCount` field.
- Ranking, cumulative score, max-score update and percentile all consume the legacy evaluator's result.
- Android attempt/review models still consume the legacy `ScoreBreakdown` shape.

Required action: create one explicit server-owned scoring dispatch contract and adapt persistence/ranking fields only after the full output schema is agreed. Do not route by client-supplied `strategyId`.

### Critical: JEE Advanced legacy helpers still expose generic scalar marking

`UniversalExamRegistry.kt` marks JEE Advanced unverified, but the registry still contains hard-coded section mark values and `getMarkingSchemeForQuestion` returns scalar values for its question types. The legacy backend `examScoring.js` also contains a JEE Advanced scalar fallback. The unverified flag is not, by itself, proof that every call site refuses to use those helpers.

The current server legacy allowlist blocks unversioned non-Community tests unless their exact IDs are explicitly approved, and the versioned resolver is separate. However, any future integration must prove that no new or Community scoring route can reach the legacy JEE Advanced helper and must not convert the illustrative scalar pattern into a live rule.

Required action: add explicit JEE Advanced activation guards at the authoritative policy boundary and audit Community publication eligibility before any integration. Keep JEE Advanced unavailable for competitive scoring until year/paper/question rubrics and source evidence are approved.

### High: score result semantics are not yet stable for persistence and ranking

The generic engine returns `partialCount`, signed `rawScore`, `finalScore` clamped to zero, and separate positive/negative totals. The existing persistence model stores correct/incorrect/skipped counts and rounds `score` and `maxScore` to integers in some fields.

Required action: define versioned result semantics before wiring:
- Preserve fractional marks without integer truncation.
- Add partial count and outcome details to canonical attempt result.
- Define whether official rank uses raw or clamped score, from the exact exam policy.
- Define accuracy denominator for partial outcomes (attempted count, weighted credit, or official rule); do not silently reuse binary accuracy.
- Keep display score, raw score, and ranking score distinct.

### High: question identity and immutable rubric binding are not implemented

The generic in-memory question model is independent of `QuestionEntity`, which avoids an immediate Room migration. However, the server does not yet load an admin-approved strategy/rubric snapshot bound to the immutable test fingerprint. The current versioned resolver binds scalar policies by exam/year/stage/paper/subject/section/type only.

Required action: add server-owned strategy/rubric identity, schema version, hash/fingerprint, source provenance, approval status and immutable test binding. Client-submitted marks or rubric content must never be authoritative.

### Medium: Kotlin and Node.js behavior needs shared golden vectors

The two implementations have matching intent and unit tests, but fixtures are separately hard-coded in Kotlin and Node. This allows drift. Add one language-neutral fixture file consumed by both test suites.

Required vectors: binary correct/wrong/unanswered; table exact/partial/unmatched; order-independent multi-select; numeric tolerance inclusive boundary and just-outside boundary; negative/zero/positive awards; fractional marks; unknown answer IDs; duplicate IDs/rules; malformed types; max score; mixed-strategy aggregation.

### Medium: CI history is not green

The earlier run on commit `9636756...` completed 316 Android tests with one failure in `numericToleranceRequiresAndUsesExplicitTolerance`. The failure was traced to the Kotlin numeric regex. The regex was corrected and tests extended. CI was restarted on commit `1a3e3bb...`; at the time of this audit, both workflow checks were still in progress. Do not call this branch green until the exact latest head has completed successfully.

### Medium: two contract documents describe overlapping versions

`QUESTION_LEVEL_SCORING_STRATEGY_CONTRACT.md` and `QUESTION_LEVEL_SCORING_STRATEGY_V1_2026-11-23.md` both describe the same v1 API, with slightly different wording/default descriptions. Keep one canonical contract and make the other a short redirect or remove it after confirming no links depend on it. Avoid maintaining two independent specifications.

### Medium: workflow overlap

The dedicated `question-level-scoring.yml` runs the full Android unit suite, while `android.yml` also runs Android tests for applicable PR events. This can duplicate expensive CI work. Decide whether the dedicated workflow should run only Node + focused Android scoring tests, or whether the main Android workflow should own all Android tests. Avoid changing workflow triggers until PR event behavior is confirmed.

## Compatibility boundaries

- NEET UG 2026: keep the verified `+4/-1/0` path and official 180-question pattern unchanged. Add regression fixtures comparing old and new engine results before any migration.
- JEE Main 2026: preserve its verified year-specific section rules; do not replace them with a generic universal scalar.
- JEE Advanced: unverified/inactive for live scoring. Exact official question instructions must be represented by explicit per-question rubric rows; no generic `+4/-2` assumption.
- Historical unversioned tests: remain subject to server-owned exact-ID legacy allowlist. Do not auto-grandfather, bulk-recalculate or rewrite stored attempts.
- Community tests: currently use the trusted Community question-level scalar marks path, not the new partial-rubric engine. Review this distinct route separately before adding any partial scoring support.

## Integration sequence

1. Complete and pass exact-head CI.
2. Consolidate the v1 contract and create shared golden fixtures.
3. Define a canonical score-result schema with fractional marks and partial outcome counts.
4. Extend server-owned scoring policy schema with strategy/rubric version, content hash, source provenance and approval.
5. Add an authoritative dispatch function that accepts only server-resolved question/rubric snapshots.
6. Add backend integration tests for full attempt persistence, idempotent retries, ranking, and score aggregation.
7. Add Android model/UI compatibility without modifying Room entities unless a reviewed migration is truly required.
8. Prove NEET UG 2026 and JEE Main 2026 regression parity.
9. Add subject-wise aggregation and exam-specific tie-break as separate versioned modules.
10. Only then consider enabling verified JEE Advanced rubrics for a specific exam year.

## Non-goals for this audit

No production integration, Firestore registry seed, Firebase deployment, historical attempt rewrite, rank recalculation, main merge, or JEE Advanced activation.
