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
| 3 | Student Gemini Connect | PARTIAL | Student connect/verify/replace/disconnect, UID-scoped AndroidKeyStore encryption and seven-day reminder implemented; provider-side key revocation remains external; exact-head CI pending. |
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
| GEM-001 | P0 | Student-owned API key connection was missing. | CLOSED on feature branch: verified personal key is stored per Firebase UID with AndroidKeyStore encryption and used by the same prompt builders. |
| GEM-002 | P0 | Admin secondary key was stored in ordinary SharedPreferences. | CLOSED on feature branch: migrated to AndroidKeyStore-encrypted local storage; legacy plaintext is removed after successful migration. |
| GEM-003 | P0 | Gemini API key was placed in URL query parameters. | CLOSED on feature branch: active ViewModel and Admin pipeline clients now send the key in the x-goog-api-key header; legacy extractor is disabled. |
| GEM-004 | P0 | Multiple Gemini HTTP clients exist in ViewModel, UniversalPdfPipeline and legacy GeminiPdfExtractor. | OPEN |
| GEM-005 | P0 | Current PDF Gemini path sends extracted text; direct PDF bytes/File API input not implemented by inspected evidence. | OPEN |
| GEM-006 | P0 | Offline fallback can synthesize generic placeholder questions; source extraction must fail closed or be explicitly marked mock. | OPEN |
| GEM-007 | P0 | Unknown exam can silently fall back to NEET/first registry item. | OPEN |
| GEM-008 | P1 | Prompt construction is duplicated outside UniversalAIContext. | OPEN |
| GEM-009 | P1 | Student API key verify/connect/disconnect and rotation reminder were missing. | CLOSED for local UX: connect/verify/replace/disconnect and seven-day reminder implemented; provider-side revocation remains external. |
| GEM-010 | P1 | Retry, model fallback, errors and usage metering duplicated/inconsistent. | OPEN |
| GEM-011 | P1 | Strict JSON/schema/semantic validation gate before persistence not established. | CLOSED on feature branch for structural/config validation; semantic correctness still needs human review. |
| GEM-012 | P1 | Durable per-question source page provenance not verified. | PARTIAL: Student/Admin models retain source page range when supplied; end-to-end persistence round-trip remains pending. |
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
- GEM-015 | P0 | `saveGeneratedTest()` silently supplies generic placeholder question/option text when generated JSON fields are missing. | CLOSED on feature branch: strict final validation rejects missing/placeholder question/options and unknown exam/section before persistence.
- GEM-016 | P1 | Student generation accepts a non-empty aggregate even if some chunks failed or expected question count was not met. | CLOSED on feature branch: rejected chunks and count mismatch block save; save failure no longer reports Success.
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
- GEM-017 | P0 | Student/Admin response parsers do not consistently enforce response envelope, answerStatus, sourceAnswer, needsReview and provenance before persistence. | CLOSED on feature branch: shared fail-closed validator is wired to both flows; metadata is retained.
- GEM-018 | P0 | `NEETViewModel.generateTestFromPdf()` silently fell back to NEET for unknown target exam. | CLOSED on feature branch: both chunk paths and final save reject unknown exams; no NEET substitution.
- GEM-019 | P1 | Prompt instructions reduce risk but cannot mathematically guarantee zero model errors; deterministic schema/answer validation and human review gates must remain authoritative. | OPEN
- Student-owned API key flow is now implemented on the feature branch: connect/verify/replace/disconnect UI, per-Firebase-UID AES-GCM encryption backed by AndroidKeyStore, backup exclusion, personal-key-only use (no silent Admin quota fallback), and seven-day rotation reminder. Provider-side revocation still requires the user to revoke the key in Google AI Studio. Phase 3 remains PARTIAL until CI and device-level tests pass.

### Verification status
- Prompt unit tests added but NOT YET EXECUTED.
- No Android build, unit-test run, emulator rules test or CI run has been executed on this new implementation head yet.
- Do not mark Phase 2 COMPLETE until the exact-commit build and tests pass and parser integration is hardened.


## Latest repository checkpoint
- Feature branch HEAD: `af110ff02cf741e5b2ce46d2db586ed4fff437f7c5`.
- Main HEAD at verification: `d2feae47f908b705209c27b8e61e624f94c400d7` (unchanged).
- Files verified present on feature branch: updated `UniversalAIContext.kt`, `UniversalPdfPipeline.kt`, `NEETViewModel.kt`, `GeminiPdfExtractor.kt`, and new `UniversalAIContextTest.kt`.
- GitHub commit-status endpoint returned no status entries; workflow-run lookup returned no runs for this commit. Therefore tests are pending, not passed.


## Strict response validation implementation — 2026-11-08

### Implemented on the feature branch
- Added fail-closed Gemini response envelope validation: only JSON objects with explicit task status and a questions array are accepted; malformed/non-array task errors and non-empty task errors reject the batch.
- Per-question checks require verified answer status, explicit `needsReview=false`, valid provenance, confidence >= 70, non-empty verification summary, configured section identity and marks, valid question type, non-placeholder options and valid answer labels.
- `KEY_EXTRACTED` requires a preserved `sourceAnswer`; any provided source key must match the resolved answer after order-independent label normalization and numeric normalization. Only `MCQ_MULTIPLE` may contain multiple correct labels.
- Admin parsing validates exam ID, exact configured section name/type, exact marks and exact option labels/count. Unknown section/scoring is not replaced with generic Section A or +4/-1.
- Student parsing validates against the selected `ExamConfig`; the raw-text chunk path no longer silently substitutes NEET for an unknown exam.
- Final Student persistence repeats exam/section/type/marks/options checks. `saveGeneratedTest` now returns success/failure so a failed save cannot be overwritten by a later Success state.
- Student persisted question JSON now retains exam/subject/section identity, marks, answer status/confidence, source key, provenance, verification summary, source page range and review reasons.
- Admin `UniversalQuestionModel` now carries provenance, answer status/confidence, verification summary, source page end, review flag and review reasons. Deterministic validation failures escalate the item to review.
- Admin source page is no longer fabricated from batch index. Unknown source page is represented as unavailable.
- Added validator tests for malformed task envelopes, source-key conflicts, missing extracted keys, multiple-answer rules, exact option counts/order and exam/scoring identity.

### Latest implementation commits
- `b594fb6` — reject conflicting source keys, enforce exact question schema.
- `cbc79b8` — fail closed on unknown exams and failed Student persistence.
- `70174a2` — add strict validator regression tests.
- `b2137fb` and `19a8f54` — enforce exact option labels and task-level errors array.
- `a07406c` — persist Student verification and source provenance metadata.
- `5509085` — add selected-exam configuration tests.
- `5269485`, `35be155`, `f51af4e` — persist Admin provenance/verification metadata and escalate deterministic review failures.

### Verification still pending
- GitHub Actions workflow `Universal Gemini AI Validation` is running on feature branch commits. Latest observed run: `42476938214` at `18e489d5e4249507d3f4d2b37bd0fd81b109f604`; status was `in_progress` at the last check.
- Do not mark tests/build as passed until the latest head's workflow completes successfully.
- Academic answer correctness cannot be guaranteed by schema validation alone. Source-key conflicts are rejected, but independently generated answers still require evidence-based verification and human review for high-stakes/public tests.
- Student-owned API key connection, encrypted key storage, direct PDF bytes/File API and full end-to-end release tests remain incomplete.


## Phase 3 — Student/Admin key security update — 2026-11-08

- Student key connect, verify, replace and disconnect controls added to Settings. Keys are verified against the Gemini Models endpoint before save.
- Student keys are encrypted with AES-256-GCM; non-exportable per-UID keys are generated and held by AndroidKeyStore. Only ciphertext is stored in private SharedPreferences.
- Student key ciphertext and Admin preferences are excluded from cloud backup and device transfer.
- A connected student key is used exclusively; the app does not silently spend shared Admin quota after personal-key quota/authentication failure.
- Admin secondary key is migrated from plaintext SharedPreferences to AndroidKeyStore encryption. Legacy plaintext is removed only after successful encrypted persistence.
- Active Gemini REST calls send keys in the `x-goog-api-key` header, not URL query strings.
- Seven-day key rotation reminder is shown in Settings. Disconnect deletes local ciphertext and the UID-specific AndroidKeyStore key. Provider-side revocation must still be performed in Google AI Studio.

### Security limitations
- BuildConfig primary key remains embedded in the app binary and cannot be considered secret from a determined APK reverse engineer. A server-side proxy with per-user authentication and quota is the long-term safe design.
- Admin and Student ViewModel HTTP clients remain separate from the Admin pipeline client; transport consolidation remains GEM-004.
- Key-store encryption and Settings flow have not yet received device/emulator verification. Android CI is pending.

### Phase 3 commits
- `66b17e1d25b0fcc98c83d0d9d6e00e7813e6d96a` — UID-scoped AndroidKeyStore key vault.
- `40e0adc5410d82e74e0780b4a3cc0465a37f7182`, `e10ffd7b135117fc03f15fc44016e3fa033a7e57` — backup exclusions.
- `dc839e66fcfb93cc7d9f0a1ef7fcdfd6497e21a9` — Student key integration, verification and API-key header transport.
- `d615796d4154be10eec83ea3545aa2f5d10b9b93` — Admin pipeline API-key header.
- `396327eea1fafe6e965c2cd18b1298849c6be1f8` — Settings connect/verify/disconnect UI.
- `e81dace7e6e4eb838c2574a1efd8e3755aeb5884` — Admin secondary key encryption and plaintext migration.
- `86ffa828940eb2070368726a67a3bd31d408bb76` — Admin preferences backup exclusion.
- `1dac654cc108497407bda9ca4a63b05166dc76d5` — isolate Student key use from Admin quota fallback.
- `43dbc253042451adb7e43bd5536c92dbaef46c12`, `18e489d5e4249507d3f4d2b37bd0fd81b109f604`, `af110ff02cf741e5b2ce46d2db586ed4fff437f7` — seven-day key rotation reminder.

### Verification snapshot
- Main HEAD: `d2feae47f908b705209c27b8e61e624f94c400d7` (unchanged).
- Feature branch latest observed HEAD before this tracker update: `af110ff02cf741e5b2ce46d2db586ed4fff437f7`.
- Latest workflow observed: `42476938214`; it had not yet reached unit-test or APK-build steps. No build/test success is claimed.
## Follow-up hardening — 2026-11-09

- Admin UniversalPdfPipeline.parseGeminiResponse() remains wired to the existing GeminiResponseValidator; the parser preserves source key, answer status/confidence, provenance, verification summary, source-page range and review metadata.
- Admin validation now passes the requested subjectId into the validator. A section ID that exists under another subject can no longer satisfy the current subject's validation by coincidence.
- Student PDF metadata recognition now requires a registered canonical targetExamId, confidence >= 60 and needsUserConfirmation=false before using the Gemini result. Unknown IDs and ambiguous results stop with an explicit error instead of silently selecting the current exam or NEET.
- Offline metadata heuristics now require an explicit exam-name/short-name/ID match in extracted text. The previous fallback to the selected exam or first registry entry has been removed. Subject detection no longer substitutes the first configured subject.
- The legacy GeminiPdfExtractor remains disabled with a Needs Review status because it does not submit verified PDF bytes/configuration; its former synthetic question fallback is not allowed to mark a test Ready.
- Added a regression test proving that a question from a different subject is rejected even when the exam configuration is otherwise valid.
- Commits in this follow-up: 45b38e91868ebeca8b60a43db11909a962f266cb (restore shared Admin validator integration), c8ee7fd9cdf4380c44e1c67bd5ff17766df35232 (remove redundant validator), 65c930017f802a8b1215e51ecb932c632f981580 (Admin subject scoping), 00dc733f9a64f727ea07d14984aedb18a912c995 (evidence-based exam recognition), 0855d47ae46ed8ebf3583aad0c336db39872b63b (subject-scope regression test).
- Verification: source-level inspection only. No Android build, unit tests, emulator run or CI pass is claimed for these latest commits. Main remains unchanged; no merge, PR or deployment was performed.
- Offline exam marker matching now uses token boundaries for short names/IDs, preventing substring false positives such as matching `NET` inside unrelated words. Commit: `ae007f71a13bbc9263b9c1d72f79b8b4f04fcf71`.
- Admin PDF pipeline no longer substitutes unrelated bundled-catalog questions when the uploaded PDF is unreadable or the local parser finds fewer questions. Only questions parsed from the supplied PDF can enter this PDF extraction fallback; missing source items remain missing and the batch stays incomplete. Commit: `fa236669d09976a30a838e6f49194904c519f7a7`.
- Exam marker matching was tightened with token boundaries to avoid false positives from short exam acronyms embedded inside unrelated words. Commit: `ae007f71a13bbc9263b9c1d72f79b8b4f04fcf71`.
## CI failure triage and test-environment fixes — 2026-11-09

- Inspected prior workflow run `42476938214`: Java/Kotlin compilation and test compilation completed; `:app:testDebugUnitTest` failed with 15/321 tests. Fourteen Gemini validator failures shared Android framework `org.json` local-JVM stub runtime exceptions (`GeminiResponseValidatorTest`), not 14 distinct validator logic failures.
- Added `testImplementation("org.json:json:20240303")` so local JVM tests use a real JSON implementation. Commit: `90e869e571bdc9bd09f121c7e65a7ca8ede5a8d3`.
- The remaining `CrashResilienceTest` failure originated from Robolectric's default SDK provider. Pinned that test class to SDK 35 without changing production targetSdk 36. Commit: `5275945e464f2c7323b998ef6dabd87da7d40611`.
- The latest changes have not yet been verified by a fresh completed CI run. Do not infer that the 15 failures are fixed until `:app:testDebugUnitTest` and `:app:assembleDebug` complete on the latest feature-branch head.
- PDF pipeline source/catalog separation was committed as `aa81acb85f32e5d92153e517dd3cf8e8ce0ae316`.
- Offline metadata no longer labels an unrecognized source as a "Previous Year Paper"; it now reports `Unknown Source` unless the document contains an explicit recognized institute/source marker. Commit: `4234c78893a52b7fad0ffe473a5a3aefa1e765ac`.
## Current branch checkpoint — 2026-11-09

- Feature branch checkpoint: `389dbe62780ccc46ab10f9f5b6e3b135c9ecdd2c`.
- Main remains `d2feae47f908b705209c27b8e61e624f94c400d7`; compare result is ahead by 255 commits and behind by 0. No merge, PR or deployment.
- Latest available CI evidence is run `42476938214`, which compiled production and test sources but failed the unit-test task (15/321). Test-environment fixes have since been committed; the current push-triggered run has no status entries exposed by the connected status endpoint. Fresh green CI is still required.
- Release gate remains CLOSED until the latest branch head passes `:app:testDebugUnitTest` and `:app:assembleDebug`, followed by Student/Admin E2E, Firestore rules and real-PDF checks.
- Remaining open work: direct PDF bytes/File API support, consolidating separate Gemini HTTP transports, server-enforced quota/token-cost telemetry, and full multi-exam E2E/regression verification.
