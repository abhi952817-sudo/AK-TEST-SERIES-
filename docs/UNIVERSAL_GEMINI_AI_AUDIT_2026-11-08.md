# AK TEST SERIES — Universal Gemini AI Source Audit
Date: 2026-11-08
Status: PRELIMINARY AUDIT — Phase 1 remains OPEN
Repository: abhi952817-sudo/AK-TEST-SERIES-
Implementation branch: feature/universal-gemini-ai-10-phase-2026-11-08
Audit base: feature/universal-exam-scoring-v1-2026-11-06 @ 9b105e9e7c614d615becb99a72b3dc5e50799e05
Production/default branch: main (read-only; no changes made)

## Scope and method
This is a read-only first-pass audit of the existing Gemini, PDF, prompt, exam-configuration, Student/Admin key, and publishing paths. Existing modules are to be extended rather than replaced. The full call-site/dependency inventory and build/test baseline remain outstanding; therefore no Phase is marked complete based only on this report.

## Existing implementation discovered
| Area | Existing source | Observed behavior | Audit status |
|---|---|---|---|
| Shared exam prompt | app/src/main/java/Com/ak/testseries/exam/UniversalAIContext.kt | Builds exam-specific system instructions and dynamic generation/extraction prompts; includes difficulty, question types, section scoring, language and anti-fabrication constraints. | FOUNDATION_EXISTS |
| Main Student PDF flow | app/src/main/java/Com/ak/testseries/viewmodel/NEETViewModel.kt | analyzePdfWithAI stores PDF in persistent session storage, extracts local text/pages/diagrams, classifies question paper vs notes, optionally sends extracted text to Gemini for metadata; generateTestFromPdf sends page/text chunks to Gemini. | PARTIAL |
| PDF persistence/recovery | app/src/main/java/Com/ak/testseries/data/pdf/* and PdfSessionEntity.kt | Existing storage/session/WorkManager recovery components exist. Preserve and extend; do not create a second persistence pipeline. | EXISTING_FOUND |
| Universal PDF pipeline | app/src/main/java/Com/ak/testseries/exam/UniversalPdfPipeline.kt | Local text extraction, batching, retries/resume, duplicate detection, validation and Gemini text generation already exist. | PARTIAL |
| Legacy Gemini PDF extractor | app/src/main/java/Com/ak/testseries/utils/GeminiPdfExtractor.kt | Separate Firebase LiveTests flow. Its request body sends prompt text only; comments explicitly describe URL/document handling as simulated/MVP. | LEGACY / CALL-SITE CHECK REQUIRED |
| Admin Gemini keys | app/src/main/java/Com/ak/testseries/ui/AdminPanelScreen.kt and NEETViewModel.kt | Admin UI manages primary/secondary global key switches and secondary key. | EXISTING_ADMIN_ONLY |
| Exam registry/config | app/src/main/java/Com/ak/testseries/exam/UniversalExamRegistry.kt and ExamConfiguration.kt | Registry/configuration foundation exists; both ExamConfig and a separate ExamConfiguration model are present and must be reconciled before adding another config model. | FOUNDATION_EXISTS |
| Publishing/access | firestore.rules | Published/approved status and public/official flags are checked; private answer-key subcollections are Admin-only. | EXISTING_SECURITY_FOUND; E2E NOT VERIFIED |

## Confirmed findings / bug IDs
### P0 — Must resolve before release
- GEM-001 — Student-owned API keys are not implemented in the inspected flow. Current key source is BuildConfig.GEMINI_API_KEY plus one global Admin secondary key; there is no per-Student connection/ownership boundary in the inspected UI/ViewModel path.
- GEM-002 — Admin secondary API key is persisted in ordinary SharedPreferences (admin_prefs / sec_gemini_api_key), not encrypted storage.
- GEM-003 — Gemini REST calls place API keys in the URL query parameter (?key=...), increasing exposure risk through URL/request logging. Move to the provider-supported header where supported and ensure redaction.
- GEM-004 — Duplicate Gemini HTTP implementations exist in UniversalPdfPipeline, NEETViewModel, and GeminiPdfExtractor. Inventory all call sites, then consolidate behind one transport/client without breaking callers.
- GEM-005 — The Student PDF path currently sends locally extracted text/page chunks, not the original PDF bytes as Gemini inline_data or an uploaded File API resource. GeminiPdfExtractor also does not attach the supplied pdfUrl to its request body. Therefore Phase 4 Direct PDF Understanding is NOT implemented by current evidence.
- GEM-006 — UniversalPdfPipeline has an offline fallback that can synthesize generic placeholder question text/options/answers when neither extracted source questions nor bundled questions exist. The Student flow also has an Admin-controlled offline mock fallback. Such output must never be represented as source-extracted or verified AI output; disable it for source-faithful PDF extraction or require an explicit, clearly labeled mock mode.
- GEM-007 — Student generation resolves an unknown target exam by falling back to NEET UG. PDF metadata analysis also falls back to the selected/default/first registered exam. This can silently misclassify another exam; unknown/low-confidence detection must stop for confirmation rather than silently choose NEET.

### P1 — Required for complete 10-phase scope
- GEM-008 — Prompt logic is not yet a single engine: UniversalAIContext is shared by some flows, while targeted difficulty regeneration and PDF metadata analysis construct independent prompts in callers.
- GEM-009 — No Student key verify/connect/disconnect/replace flow or seven-day rotation reminder was found in the inspected Student UI/ViewModel path.
- GEM-010 — Gemini request error handling, retry policy, usage counting, and model fallback are duplicated and inconsistent between the pipeline and ViewModel clients.
- GEM-011 — Question JSON parsing in the Student generation flow accepts several envelope shapes but the inspected path does not yet establish a strict schema/semantic validation gate before saveGeneratedTest.
- GEM-012 — Page provenance is carried in the extracted text separators, but a durable page-reference field in the generated question schema and end-to-end UI was not verified.
- GEM-013 — ExamConfig and ExamConfiguration are separate models. Determine active consumers and migration compatibility before modifying either.
- GEM-014 — Existing daily generation limit and Admin global AI kill switch are present; server-enforced per-user quotas, per-key isolation, token/cost metering and abuse controls remain unverified.

## Preserve / do not rebuild
- Existing UniversalAIContext prompt builder.
- Existing UniversalExamRegistry and exam config data.
- Existing AdvancedPdfSmartExtractor, local OCR/diagram extraction, PDF session storage, WorkManager recovery and duplicate-PDF detection.
- Existing Admin Gemini key manager until migrated safely.
- Existing private vault, test ownership, Firestore publishing rules and private answer-key boundary.
- Existing QuestionValidationEngine and DuplicateDetectionEngine.
- Existing test save/player/result flows.

## Phase status (evidence-based)
| Phase | Status | Reason |
|---|---|---|
| 1 Complete Source Audit | IN_PROGRESS | First-pass source audit recorded; full call-site/dependency and test inventory remains. |
| 2 Universal Prompt Engine | PARTIAL | UniversalAIContext exists, but prompt construction is duplicated across flows. |
| 3 Student Gemini Connect | NOT_IMPLEMENTED_IN_INSPECTED_PATH | Only Admin global keys found; Student-owned keys absent from inspected path. |
| 4 Direct PDF Understanding | NOT_IMPLEMENTED | Current Gemini calls send text, not PDF bytes/File API resource. |
| 5 Universal Exam Recognition | PARTIAL | Registry and metadata analysis exist; silent default-exam fallback is unsafe. |
| 6 Question & Solution Generation | PARTIAL | Generation and validation foundations exist; strict schema/source provenance gate needs verification. |
| 7 Automatic Test Creation | PARTIAL / UNVERIFIED | Existing save/player pipeline exists; end-to-end generated-test integration needs tests. |
| 8 Private/Public & Admin Review | FOUNDATION_EXISTS / UNVERIFIED | Rules and private vault exist; generated-test review workflow must be traced end-to-end. |
| 9 Reliability, Security & Cost Controls | PARTIAL | Some batching, retry, daily limits and Admin kill switch exist; key security and server-side quotas incomplete/unverified. |
| 10 Full Integration & Release Testing | NOT_STARTED | No Gemini-specific full E2E evidence recorded. |

## Next audit tasks
1. Enumerate every source call site for Gemini HTTP, Firebase AI SDK, UniversalAIContext, generateTestFromPdf, analyzePdfWithAI, saveGeneratedTest, and publishing functions.
2. Trace generated test fields from UI -> ViewModel -> Room/Firestore -> private answer-key storage -> Admin review -> public Test Player.
3. Inspect all relevant tests and CI workflow results on the exact implementation branch head.
4. Record a dependency graph and baseline build/test results before code changes.
5. Only then implement the shared Prompt Engine and Gemini transport/key abstraction on this feature branch.

## Safety
- Do not modify main.
- Do not merge or deploy Firebase Functions, Firestore rules, Storage rules, indexes, or production data.
- Do not publish generated tests automatically.
- Do not mark a phase COMPLETE without source evidence and passing tests on the exact commit.


## Supplemental findings — generated-test workflow and CI baseline
Additional call-site tracing on the audit base confirmed:
- `NEETViewModel.generateTestFromPdf()` is the main Student generation entry point and calls `UniversalAIContext` plus the ViewModel Gemini client.
- `NEETViewModel.analyzePdfWithAI()` uses existing `PdfStorageManager`, `PdfSessionEntity`, `AdvancedPdfSmartExtractor`, local question classification and diagram extraction. Gemini receives extracted opening-page text for metadata analysis, not the original PDF binary.
- `saveGeneratedTest()` already builds Room `TestEntity` and `QuestionEntity` records. Student public submissions are marked `Under Review`; private submissions remain local/private. This is an existing integration to preserve.
- `AdminTestListContent` already exposes an Under Review list and an Approve & Publish action. `publishReviewedTest()` is Admin-gated, strips public answer keys, and stores the trusted key under `private_keys/answerKey`. This is a substantial Phase 8 foundation, not a greenfield feature. Reject/correction workflow and full E2E permission behavior still need verification.
- `saveGeneratedTest()` currently fills missing question/option text with generic defaults (for example, "Question Text" / "Option A") and does not show a strict schema validation gate in the inspected method. Reject malformed/incomplete generated items before Room/Firestore persistence.
- Existing PDF session recovery, OCR and diagram extraction tests are present. No dedicated Gemini transport, Prompt Engine, Student API key, direct-PDF upload, or full generated-test E2E test file was found in the inspected test tree.

### CI baseline on the audit source
- Source base commit: `9b105e9e7c614d615becb99a72b3dc5e50799e05`.
- GitHub Actions run `42176925903` ("Ranking V2 Validation") completed with failure.
- `firestore-rules-tests`: PASS.
- `backend-unit-tests`: PASS.
- `android-validation`: FAIL because `CrashResilienceTest > classMethod` threw `UnsupportedOperationException` at `DefaultSdkProvider.java:170`; 299 tests completed, 1 failed.
- This is a pre-existing branch baseline failure, not caused by the Gemini audit commits. Re-run on the new Gemini branch after implementation and isolate/fix the test without weakening the test suite.

### Phase 1 status update
Phase 1 remains IN_PROGRESS until every Gemini call site, caller, persistence route, approval route, relevant test and exact-branch CI result is recorded. The findings above narrow the scope but do not authorize marking Phase 1 complete.


## Prompt-engine follow-up — 2026-11-08
The existing `UniversalAIContext.kt` was the natural shared boundary already called by the Student PDF generation flow and Admin `UniversalPdfPipeline`. It has now been replaced on the feature branch with versioned `AKTS_UNIVERSAL_PROMPT_V2`, preserving the existing method signatures used by those primary callers.

Additional previously independent prompt blocks were routed into the shared engine:
- Admin difficulty regeneration in `UniversalPdfPipeline.kt`.
- Student PDF exam metadata recognition in `NEETViewModel.kt`.
- Legacy `GeminiPdfExtractor.kt` extraction prompt.

The new policy requires source hierarchy, explicit uncertainty, independent answer verification, source-key conflict flags, exact section scoring, bilingual fidelity, provenance labels, strict JSON, no filler, and no private chain-of-thought output. Unknown subjects no longer inherit the first configured subject's sections.

Important limitation: this is a prompt-level refactor, not proof of zero hallucinations. The Student/Admin JSON parsers still need to enforce task status, answerStatus, sourceAnswer, needsReview, provenance and strict schema before persistence (GEM-017). The Student flow still silently falls back to NEET for unknown exam identities (GEM-018). Student-owned API-key connect/storage remains Phase 3 work; current inspected key selector still uses BuildConfig primary and Admin secondary preferences.

Prompt tests have been added but not run. Phase 2 remains IN_PROGRESS pending exact-head compile/test and response-parser hardening.
