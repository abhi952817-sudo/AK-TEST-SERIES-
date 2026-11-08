# Universal Gemini AI — 10-Phase Development Tracker
Last updated: 2026-11-08
Repository: abhi952817-sudo/AK-TEST-SERIES-
Implementation branch: feature/universal-gemini-ai-10-phase-2026-11-08
Audit report: docs/UNIVERSAL_GEMINI_AI_AUDIT_2026-11-08.md
Status vocabulary: NOT_STARTED | IN_PROGRESS | PARTIAL | BLOCKED | VERIFIED | COMPLETE

## Global gates
- [ ] Never modify main directly.
- [ ] No production deployment, Firebase migration, rules/index deployment, or PR merge without explicit owner approval.
- [ ] Inventory call sites and dependencies before changing an existing implementation.
- [ ] Preserve working PDF persistence, recovery, OCR, diagrams, exam registry, question validation, private vault and test-player flows.
- [ ] No synthetic/dummy questions may be passed off as source-extracted or verified Gemini output.
- [ ] Student API keys must be isolated per authenticated UID and never be stored in Firestore, logs, analytics, crash reports, or plaintext preferences.
- [ ] Every phase must have code evidence, tests, bug IDs, commit SHA and status before completion.

## Phase tracker
| Phase | Scope | Status | Current evidence / exit criteria |
|---|---|---|---|
| 1 | Complete Source Audit | IN_PROGRESS | First-pass audit committed; complete call-site/dependency map, data-flow trace, existing tests and exact-head CI baseline still required. |
| 2 | Universal Prompt Engine | PARTIAL | UniversalAIContext exists; all duplicated prompts must route through versioned task-mode templates and ExamConfig. Prompt unit tests required. |
| 3 | Student Gemini Connect | NOT_STARTED | Student-owned connect/verify/replace/disconnect/revoke UX, encrypted local key storage, UID isolation and seven-day reminder required. |
| 4 | Direct PDF Understanding | NOT_STARTED | Send actual PDF bytes via supported Gemini document input or File API; preserve page references; keep current local extraction as fallback/parallel validation. |
| 5 | Universal Exam Recognition | PARTIAL | Registry and metadata analysis exist; remove silent NEET/first-exam fallback; require confidence and user confirmation for ambiguity. |
| 6 | Question & Solution Generation | PARTIAL | Existing generation/validation exists; strict schema, answer-key confidence, provenance, duplicates, unsupported question rejection and bilingual checks required. |
| 7 | Automatic Test Creation | PARTIAL | Existing save flow and player exist; verify complete metadata, ownership, scoring, timer, autosave, submission and result integration end-to-end. |
| 8 | Private/Public & Admin Review | PARTIAL | Firestore publication helper/private answer-key rules exist; trace generated-test status and enforce owner-private -> Admin review -> explicit approval -> public. |
| 9 | Reliability, Security & Cost Controls | PARTIAL | Existing daily limit, Admin kill switch and batch retry exist; add server-side quotas, key isolation, token/cost telemetry, safe retry and recovery. |
| 10 | Full Integration & Release Testing | NOT_STARTED | Require real authorized PDFs, all supported task modes, Hindi/English/bilingual, Admin/Student E2E, Android/backend/rules tests and regression pass. |

## Bug register
| Bug ID | Priority | Summary | Status |
|---|---|---|---|
| GEM-001 | P0 | Student-owned API key connection not found in inspected Student flow; current key sources are app BuildConfig and Admin global key. | OPEN |
| GEM-002 | P0 | Admin secondary key is stored in ordinary SharedPreferences. | OPEN |
| GEM-003 | P0 | Gemini API key is placed in URL query parameter in inspected REST clients. | OPEN |
| GEM-004 | P0 | Multiple Gemini HTTP clients exist in ViewModel, UniversalPdfPipeline and legacy GeminiPdfExtractor. | OPEN |
| GEM-005 | P0 | Current PDF Gemini path sends extracted text; direct PDF bytes/File API input not implemented by inspected evidence. | OPEN |
| GEM-006 | P0 | Offline fallback can synthesize generic placeholder questions; source extraction must fail closed or be explicitly marked mock. | OPEN |
| GEM-007 | P0 | Unknown exam can silently fall back to NEET/first registry item. | OPEN |
| GEM-008 | P1 | Prompt construction is duplicated outside UniversalAIContext. | OPEN |
| GEM-009 | P1 | Student API key verify/connect/disconnect and rotation reminder not found. | OPEN |
| GEM-010 | P1 | Retry, model fallback, errors and usage metering duplicated/inconsistent. | OPEN |
| GEM-011 | P1 | Strict JSON/schema/semantic validation gate before persistence not established. | OPEN |
| GEM-012 | P1 | Durable per-question source page provenance not verified. | OPEN |
| GEM-013 | P1 | ExamConfig and ExamConfiguration coexist; active usage and migration compatibility need inventory. | OPEN |
| GEM-014 | P1 | Server-enforced user/key quota, token metering and abuse controls unverified. | OPEN |

## Commit / test log
| Date | Commit | Change | Tests | Result |
|---|---|---|---|---|
| 2026-11-08 | 1a753811f2cc1d78f19cb1989ac59a212a4838f5 | Preliminary Gemini source audit | Read-only source inspection; no build/test executed yet | Audit baseline only |

## Phase completion template
For each phase, append:
- Changed files and why existing call sites were extended rather than replaced.
- Bug IDs closed and evidence.
- Unit, integration, security and regression tests executed on exact commit.
- CI run URL/head SHA and results.
- Commit SHA.
- Remaining risks and owner decision required.


## Supplemental audit update — 2026-11-08
- Phase 7: Existing `generateTestFromPdf()` -> `saveGeneratedTest()` -> Room `TestEntity`/`QuestionEntity` path is present. Full Test Player/result E2E remains unverified.
- Phase 8: Existing student-public `Under Review` state, Admin review list, Admin-only `publishReviewedTest()`, public answer-key stripping and private answer-key storage were found. Preserve these. Reject/correction and end-to-end security tests remain open.
- GEM-015 | P0 | `saveGeneratedTest()` silently supplies generic placeholder question/option text when generated JSON fields are missing; add strict schema and semantic validation before persistence. | OPEN
- GEM-016 | P1 | Student generation accepts a non-empty aggregate even if some chunks failed or expected question count was not met; require explicit partial-result state and owner decision before save. | OPEN
- CI baseline on source base `9b105e9e7c614d615becb99a72b3dc5e50799e05`: run `42176925903`; backend tests PASS, Firestore rules tests PASS, Android unit tests FAIL (299 tests, one failure: `CrashResilienceTest > classMethod`, `UnsupportedOperationException` at `DefaultSdkProvider.java:170`). This failure predates Gemini branch changes and must be isolated without weakening tests.
- Phase 1 remains IN_PROGRESS. No code implementation changes have been made yet; only audit documentation is committed.


## Prompt Engine implementation update — 2026-11-08
Implementation branch commits:
- `e6de55884d32dc6afc442ccecfcfc1ca73367fc7` — replaced `UniversalAIContext` with versioned `AKTS_UNIVERSAL_PROMPT_V2` policy and shared task prompts.
- `9c90e429f416c837cb9c5dde0dfb6ce480128e9f` — Admin difficulty regeneration now calls the shared prompt builder.
- `c8fb148db6dba78787b83f820a7cbfaf0aee9158` — Student PDF metadata analysis now calls shared exam-recognition prompt.
- `ff5dbbea2bd9109ff43436f71339522b252bed0d` — legacy PDF extractor prompt now comes from shared prompt engine.
- `f038fc55d24ae93fb62511550031521272840970` — exam recognition prompt receives canonical exam name -> examId mapping.
- `34849b6809dffe581eaebdb958bb3d9dc864188e` — metadata caller supplies canonical exam IDs.
- `f2a4bff32191bc1696ff8488088bcdf1138dfd92` — added `UniversalAIContextTest` coverage for source integrity, answer accuracy policy, exam config, ambiguity and no-source behavior.

### What changed
- Prompt construction is independent of API credential identity. The same builder output is passed regardless of which authorized key the transport selects.
- Shared system policy covers source-of-truth hierarchy, answer verification, numeric/unit checks, MCQ_SINGLE vs MCQ_MULTIPLE vs NAT/TITA, answer-key conflicts, bilingual fidelity, provenance, diagrams, anti-fabrication, and strict JSON contracts.
- Added shared task builders for generation/extraction, exam recognition, difficulty regeneration and legacy extraction.
- Removed unsafe first-subject fallback from `getApplicableSections()`; unknown subjects no longer inherit another subject's section/scoring configuration.
- Removed the old "extensive reasoning block" requirement; prompt asks for concise verification summaries, not private chain-of-thought.

### Phase status
- Phase 2: IN_PROGRESS (core prompt-engine refactor committed; build/test execution and complete call-site inventory still required).
- Phase 1: IN_PROGRESS (legacy prompt locations found and redirected; full repository-wide Gemini call-site inventory remains open).

### Remaining blockers / additional bugs
- GEM-017 | P0 | Student/Admin response parsers do not yet consistently enforce the new response envelope, task-level status, answerStatus, sourceAnswer, needsReview and provenance fields before persistence. | OPEN
- GEM-018 | P0 | `NEETViewModel.generateTestFromPdf()` still has a silent NEET fallback for unknown target exam; exam recognition prompt now refuses guessing, but caller/selection UX must be corrected before Phase 5 completion. | OPEN
- GEM-019 | P1 | Prompt instructions reduce risk but cannot mathematically guarantee zero model errors; deterministic schema/answer validation and human review gates must remain authoritative. | OPEN
- Student-owned API-key connect/verify/storage is still not implemented in the inspected Student flow. The current ViewModel key selector still reads BuildConfig primary + Admin secondary preferences. Phase 3 remains NOT_STARTED. Prompt reuse is ready at the builder boundary, but Student-key execution is not yet wired.

### Verification status
- Prompt unit tests added but NOT YET EXECUTED.
- No Android build, unit-test run, emulator rules test or CI run has been executed on this new implementation head yet.
- Do not mark Phase 2 COMPLETE until the exact-commit build and tests pass and parser integration is hardened.


## Latest repository checkpoint
- Feature branch HEAD: `9d777f81d6ce0723549a038cffcc268013cff5c5`.
- Main HEAD at verification: `d2feae47f908b705209c27b8e61e624f94c400d7` (unchanged).
- Files verified present on feature branch: updated `UniversalAIContext.kt`, `UniversalPdfPipeline.kt`, `NEETViewModel.kt`, `GeminiPdfExtractor.kt`, and new `UniversalAIContextTest.kt`.
- GitHub commit-status endpoint returned no status entries; workflow-run lookup returned no runs for this commit. Therefore tests are pending, not passed.
