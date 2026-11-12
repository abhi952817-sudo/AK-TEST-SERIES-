# AK TEST SERIES — AI Connector, PDF Pipeline & Release Hardening Plan

**Branch:** `feature/universal-gemini-ai-10-phase-2026-11-08`  
**Repository:** `abhi952817-sudo/AK-TEST-SERIES-`  
**Plan status:** Implementation roadmap; not a claim that the listed work is already complete.  
**Priority rule:** Complete, test, and record evidence for each phase before starting the next phase.

## 0. Non-negotiable development rules

1. Work only on this feature branch for the tasks in this plan. Do not commit, merge, or push these implementation changes to `main` as part of this work.
2. Preserve existing working features, data contracts, Room/Firestore data, exam formats, and user flows unless a specific change is required and reviewed.
3. No dummy AI responses, fabricated question data, silent offline generation, fake provider success, or placeholder usage/cost records.
4. Fail closed: if a provider, parser, permission check, quota check, or validation step fails, return a clear error and do not publish or silently substitute generated content.
5. Never log, commit, expose in client builds, or return raw API keys or secret values.
6. Each phase must include code review, automated tests, negative/security tests where relevant, and recorded CI evidence.
7. Do not mark a phase complete merely because code was written. Completion requires passing tests and evidence.
8. Avoid destructive database migrations and preserve backward compatibility. Any schema change needs migration tests and a documented rollback/recovery strategy.
9. Keep provider-specific behavior behind interfaces; do not scatter provider logic through UI or ViewModels.
10. Keep this plan updated with commit SHA, test command, result, date, and unresolved issues after each phase.

## 1. Current priority order

| Order | Workstream | Completion gate |
|---|---|---|
| 1 | Verify and fix all four Validator failures | All four failure cases reproduced or identified, fixed, and passing in CI |
| 2 | Run all 341 Android unit tests, then build Debug APK | Full test suite passes; APK task succeeds; artifact is identifiable |
| 3 | Student/Admin Gemini connector | Student key is preferred; secure Admin server connector is used only under explicit policy |
| 4 | Daily generation quota | Configurable per-student limit (1/2/3/custom); atomic server-side enforcement |
| 5 | PDF question pipeline | Real extraction, validation, Draft Review, and explicit approval before publication |
| 6 | Groq connector | Separate adapter, real authenticated API tests, failure handling |
| 7 | Cerebras connector | Separate adapter, real authenticated API tests, failure handling |
| 8 | Usage, token, and cost tracking | Auditable request-level records with provider, student, token usage, and cost |
| 9 | Security and final regression | Authorization, secret protection, quota bypass, duplicate prevention, all exam formats, end-to-end tests |

Do not reorder these phases for convenience. A blocker may be investigated in parallel, but downstream production integration must not be declared complete before its prerequisites pass.

## 2. Phase 1 — Validator: four failures

**Starting reference:** commit `a44de80` (short SHA supplied in the project status; resolve and record the full SHA before relying on it).

Tasks:
- Identify the exact workflow run and job that tested `a44de80`; do not infer CI status from an empty status response.
- Capture all four failing validator names, exact commands, failing assertions, stack traces, and affected files.
- Reproduce each failure independently where possible.
- Determine whether each failure is a real product defect, a test defect, flaky infrastructure, or an environment/configuration issue.
- Fix root causes with the smallest safe change. Do not weaken/remove assertions just to obtain a green build.
- Add regression tests for each defect.
- Re-run the relevant validator jobs and then the complete applicable CI workflow.
- Record links/IDs for the successful run and jobs.

**Exit criteria:** all four original failures are demonstrably resolved; no validator is skipped, muted, or downgraded; CI is green for the exact tested commit.

## 3. Phase 2 — Android unit tests and Debug APK

Tasks:
- Confirm the exact source branch and commit being tested.
- Run all 341 expected Android unit tests; capture the discovered and executed test counts, not only the Gradle task's exit code.
- Investigate every failure. Do not hide tests with filters or exclusions unless the exclusion is separately justified and approved.
- Run the required compile/build checks.
- Build the Debug APK only after the unit-test gate passes.
- Record APK artifact name, build variant, commit SHA, build result, and artifact location.
- Confirm no signing secrets or production API secrets are embedded in the APK.
- Run migration/integrity tests for any database-related changes.

**Exit criteria:** all 341 tests pass (or an explicitly reconciled actual test count is documented); Debug APK build succeeds; no unresolved build/test failure remains.

## 4. Phase 3 — Gemini Student/Admin connector

### 4.1 Architecture
- Define a provider-neutral AI generation interface and a Gemini adapter.
- Keep request construction, response parsing, retry policy, timeout policy, and error mapping out of UI code.
- Student's own Gemini API key is the first-choice credential when the student has configured a valid key.
- If the student key is absent or unavailable, use the Admin-managed server connector only when the server-side policy permits it.
- Do not send an Admin key to the Android client. Store it only in a server-side secret manager/environment secret.
- Make the selected credential route explicit in internal audit metadata without recording the key itself.
- Provide clear errors for missing key, invalid key, rate limit, quota exhaustion, timeout, provider outage, malformed response, and blocked model access.
- No automatic offline/dummy question generation when Gemini is unavailable.

### 4.2 Admin controls
- Admin can enable/disable the shared server connector, configure allowed models and policy limits, and inspect health/usage metadata.
- Only authorized Admin/Super Admin roles can change connector configuration.
- Student requests must be authorized on the server; never trust a client-supplied role, student ID, quota, or provider selection without verification.

### 4.3 Tests
- Student key takes precedence when valid.
- Missing/invalid student key follows the configured server fallback policy, or fails clearly if fallback is disabled.
- Admin secret never appears in API responses, logs, crash reports, or client configuration.
- Unauthorized users cannot read or modify connector settings.
- Simulate 401, 403, 429, 5xx, timeout, malformed JSON, and empty provider response.
- Verify retries are bounded and do not duplicate billable generation requests unexpectedly.

**Exit criteria:** real Gemini requests work through both permitted routes; routing and security tests pass; no dummy fallback exists.

## 5. Phase 4 — Daily quota with server-side enforcement

Requirements:
- Support per-student limits such as 1, 2, 3, or an Admin-configured custom positive integer.
- Define the quota unit precisely (for example, successful test-generation jobs), and apply it consistently across Gemini, Groq, Cerebras, and any future provider.
- Admin policy may define a global default and an authorized per-student override.
- Server obtains the authenticated student identity; ignore client attempts to spoof identity or remaining quota.
- Enforce quota atomically/transactionally so parallel requests cannot exceed the limit.
- Define timezone and daily reset boundary explicitly (use the configured product timezone; do not rely on the device clock).
- Define whether failed, cancelled, rejected, or partially completed jobs consume quota. Record the policy and test it.
- Use idempotency keys/job IDs to prevent retries from consuming quota twice.
- Return remaining quota and reset time as server-computed display data, never as authority for the next request.
- Add audit events for policy changes and quota decisions.

Tests:
- Limits 1/2/3/custom; Admin override; midnight reset; timezone boundary; concurrent requests; replay; duplicate request; forged student ID; unauthorized Admin; provider failure; server restart.

**Exit criteria:** quota cannot be bypassed by modifying the app, replaying a request, or sending parallel requests.

## 6. Phase 5 — Real PDF extraction and question approval pipeline

### 6.1 Upload and extraction
- Accept only supported PDF files; enforce file size/page limits and validate MIME/signature rather than trusting the filename.
- Store the original PDF securely with owner, source, upload time, checksum, and processing status.
- Extract actual embedded text with page boundaries and source references.
- Detect scanned/image-only pages. Use OCR only through a real configured OCR implementation; if unavailable, mark the page as requiring OCR/manual review rather than inventing text.
- Preserve page number and extracted text span for each candidate question.
- Handle malformed, encrypted, empty, oversized, and mixed text/image PDFs with explicit statuses.

### 6.2 Question parsing and validation
- Parse question stem, options, correct answer (only when source-supported), explanation, subject, chapter/topic, exam, question type, marks, negative marks, and source page.
- Support existing exam formats and question types without silently coercing unsupported types into MCQ.
- Validate option count, answer-option mapping, missing fields, duplicate options, invalid marks, malformed math, and unsupported media.
- Detect duplicates using normalized text plus a robust similarity/hash strategy; do not delete or merge source records automatically.
- Flag uncertain answers, ambiguous questions, missing answer keys, and low-confidence extraction for human review.
- Keep source provenance for every extracted question.

### 6.3 Draft Review and publication
- Extracted questions must enter Draft/Needs Review state.
- Admin review UI must show source PDF/page, extracted question, options, answer, explanation, warnings, and editable fields.
- Admin can approve, reject, or edit before approval.
- Only explicitly approved questions can enter the public test catalogue.
- Keep reviewer ID, decision, timestamp, and revision history.
- Reprocessing must not overwrite an approved question silently.

Tests:
- Text PDF, scanned PDF, mixed PDF, multi-column pages, equations, diagrams, Hindi/English text, malformed PDF, encrypted PDF, duplicate questions, missing answer key, and each supported exam question format.

**Exit criteria:** real extraction is traceable to the source; validation catches malformed/ambiguous content; no draft becomes public without approval.

## 7. Phase 6 — Groq connector

- Implement a separate Groq adapter behind the common provider interface.
- Keep Groq credentials server-side or in the student's own secure credential store according to the approved credential policy; never expose Admin credentials.
- Configure supported models explicitly; do not assume Gemini model names or request schemas work with Groq.
- Normalize provider responses into the internal generation result while preserving provider/model metadata.
- Implement bounded timeout/retry/rate-limit handling and clear provider-specific errors.
- Do not silently switch providers unless the routing policy explicitly permits it and the selected provider is recorded.
- Run real API smoke tests using a controlled test key and non-production test prompt; keep secrets out of test output.
- Add mocked contract tests for deterministic CI and separate opt-in live integration tests.

**Exit criteria:** real Groq request/response verified; contract, error, routing, and secret-handling tests pass.

## 8. Phase 7 — Cerebras connector

- Implement a separate Cerebras adapter; do not copy Groq/Gemini assumptions without checking the provider's actual API contract.
- Configure supported model IDs, authentication, request schema, response parsing, limits, and error mapping explicitly.
- Apply the same credential and secret-storage policy as other providers.
- Add bounded retries/timeouts and prevent duplicate billable generation on retry.
- Run a real API smoke test with a controlled test key and safe test prompt.
- Keep deterministic mocked contract tests in normal CI and live tests separately gated by secrets.

**Exit criteria:** real Cerebras request/response verified; contract, error, routing, and secret-handling tests pass.

## 9. Phase 8 — Usage, token, and cost tracking

Record one auditable record per provider attempt/request with:
- request/job ID and idempotency key;
- authenticated student ID and relevant Admin policy/config version;
- provider, model, operation, timestamp, outcome, and latency;
- input/output/total token counts when returned by the provider;
- token-count source (provider-reported, locally estimated, or unavailable);
- pricing configuration version, currency, calculated cost, and calculation status;
- retry/attempt number and a safe error category.

Rules:
- Never fabricate token counts or costs. Unknown values remain null/unknown with a reason.
- Store provider pricing as versioned configuration with effective dates; do not hard-code a stale price into business logic.
- Distinguish estimated from provider-reported usage.
- Avoid storing full prompts, answers, API keys, or sensitive student content in usage analytics by default.
- Restrict usage records to authorized Admin views; students may see only their own permitted usage.
- Reconcile provider-reported usage where the provider exposes it.
- Track PDF processing/OCR costs separately from AI token costs where applicable.

Tests:
- Missing usage metadata, zero tokens, provider-reported counts, estimated counts, pricing changes, retries, failed requests, currency rounding, access control, and duplicate event ingestion.

**Exit criteria:** usage/cost records are traceable, access-controlled, and never presented as exact when only estimated or unavailable.

## 10. Phase 9 — Security and final regression

### Security checklist
- Verify Firebase/server authentication and role-based authorization on every privileged endpoint.
- Enforce ownership checks for student PDFs, drafts, generated tests, usage records, and API-key metadata.
- Store secrets in server-side secret storage; redact them from logs, exceptions, analytics, and client responses.
- Review Android local key storage and backup behavior; do not treat obfuscation as secret protection.
- Add request validation, upload limits, rate limits, abuse controls, and safe error messages.
- Prevent quota bypass, replay, race conditions, duplicate jobs, and unauthorized provider selection.
- Review Firestore/Storage rules and server IAM with both allowed and denied test cases.
- Check dependency vulnerabilities and ensure release builds do not include test secrets.
- Define key rotation/revocation and incident response steps.

### Regression matrix
- Existing login/authentication and Admin/Super Admin authorization.
- Student-owned API key and Admin server connector routing.
- Gemini, Groq, Cerebras success and failure paths.
- Daily quota limits, overrides, reset, concurrency, and replay.
- PDF upload, extraction, OCR-needed status, validation, Draft Review, approval, rejection, and publication.
- Duplicate-question prevention and source traceability.
- All currently supported exams, subjects, sections, question types, option counts, numerical answers, scoring, negative marking, timers, and result persistence.
- Offline app behavior must not create AI-generated questions or fake provider results.
- Room migrations, Firestore serialization/deserialization, backup compatibility, and existing test data integrity.
- UI loading, empty, error, retry, and permission-denied states.
- End-to-end student generation and Admin review flows.

**Exit criteria:** all required automated suites pass; security-negative tests pass; live provider checks are documented; no known critical/high issue is left unresolved; final Debug APK is built from the verified commit.

## 11. Required evidence log

Update this table after every phase. Use exact commit SHAs and CI links/run IDs.

| Phase | Commit SHA | Test/build command | Result | Evidence / unresolved issues |
|---|---|---|---|---|
| 1. Validator | Pending | Pending | Not started / in progress | Four failures and CI run evidence pending |
| 2. Android tests + APK | Pending | Pending | Not started | Expected 341 tests |
| 3. Gemini | Pending | Pending | Not started | Real API + routing/security tests |
| 4. Daily quota | Pending | Pending | Not started | Atomic server-side tests |
| 5. PDF pipeline | Pending | Pending | Not started | Extraction + Draft Review + approval |
| 6. Groq | Pending | Pending | Not started | Real API smoke test |
| 7. Cerebras | Pending | Pending | Not started | Real API smoke test |
| 8. Usage/cost | Pending | Pending | Not started | Token/cost provenance and access tests |
| 9. Security/regression | Pending | Pending | Not started | Full regression and release evidence |

## 12. Definition of done

The work is complete only when:
- all nine phases meet their exit criteria;
- every change is committed to this feature branch and reviewed;
- all relevant CI checks pass on the final commit;
- all 341 expected Android unit tests pass (or the actual test inventory is reconciled with evidence);
- Debug APK build succeeds from that same final commit;
- provider live-test evidence is recorded without exposing secrets;
- no dummy/offline AI fallback, unapproved PDF question publication, quota bypass, or known duplicate-generation path remains;
- `main` has not been modified by this feature-branch implementation.

**Important:** This document is the execution plan, not proof of implementation or test success. Update statuses only after collecting evidence.


---

## 13. Second-pass Git history and cross-branch audit — 2026-11-12

This section records a read-only audit performed after the initial roadmap was committed. It does not mark implementation or CI work as complete.

### 13.1 Exact branch and divergence evidence

- Repository: `abhi952817-sudo/AK-TEST-SERIES-`
- Implementation branch: `feature/universal-gemini-ai-10-phase-2026-11-08`
- Feature branch HEAD before this audit addendum: `a37710b7c8c8ce2ab5e2cb1becd3e4fd9dad1f67`
- Main HEAD observed during the audit: `b38bd497434dd24438b37b3de87bd26b4fbe98f8`
- Merge base reported by GitHub compare: `d2feae47f908b705209c27b8e61e624f94c400d7`
- Divergence: main contains 319 commits not in the feature branch; the feature branch contains 93 commits not in main. The branches have diverged, so neither a blind merge nor a blind cherry-pick is safe.
- No merge, cherry-pick, rebase, deployment, or source-code modification was performed during this read-only audit.

**Mandatory new Gate 0 — branch reconciliation:** before declaring the nine phases ready for implementation/release, inventory the 319 main-only commits and 93 feature-only commits by affected subsystem. Identify common-file conflicts and behavioral changes. Reconcile the current source of truth for each affected file, preserving required feature-branch work and newer main safety fixes. Do not merge the branches wholesale. Use reviewed, minimal, tested cherry-picks or carefully resolved commits only after the owner-approved integration strategy is clear. Keep `main` untouched until explicit approval.

### 13.2 Confirmed main-only work that the feature branch must review

The compare response identifies these important main-side changes relative to the feature branch:

- `UniversalQuestionGenerationPlanner.kt` and `UniversalQuestionGenerationPlannerTest.kt` were added on main. The planner creates deterministic subject/section allocations from registered exam configuration, validates capacity, preserves question-type/option-count/marks metadata, and creates exact batch allocations. Tests cover NEET 180/200, JEE Main 75, batch distribution, and capacity rejection. The planner source is absent from the current feature branch (branch fetch returned 404). This is a critical integration gap for correct **All Subjects** generation; do not substitute equal or guessed distribution unless the selected exam configuration explicitly defines it.
- Main contains additional fail-closed subject/section routing fixes and regression tests, including commits `c838a14acd1153bde9ab1ef7f31ea8fb4a1a6b92`, `14545b2dbb715cfcf4e44018de6b6973a0bf1f4f`, and `b38bd497434dd24438b37b3de87bd26b4fbe98f8`. Review and retain their safety behavior when reconciling `ExamFormatStandardizer`, `UniversalAIContext`, `UniversalPdfPipeline`, and `NEETViewModel`.
- Main removes `OfflineMarkdownQuestionParser.kt` and its tests, and removes `BundledExamCatalog.kt` and its tests. The feature branch still contains `OfflineMarkdownQuestionParser.kt`. Before removing or restoring either implementation, inspect every call site on both branches and prove that real PDF extraction, recovery, and existing user flows remain intact. No synthetic/local question fallback may be reintroduced.
- Main has substantial changes to `NEETViewModel.kt`, `UniversalPdfPipeline.kt`, `GeminiPdfExtractor.kt`, `ExamFormatStandardizer.kt`, `QuestionValidationEngine.kt`, `SyllabusCatalog.kt`, `MainActivity.kt`, and the Firestore/backend code. These are high-conflict files and require file-by-file review, not automatic overwrite.
- Main adds Room schema `21.json` and `AppDatabaseMigration20To21Test.kt`; verify the actual database version and every migration path on the chosen integration base. Preserve user data and do not rely on destructive migration fallback.
- Main changes Firestore rules/indexes, backend Cloud Functions, and database targeting. Re-run emulator/security tests against the exact integrated rules and confirm all Cloud Functions use the intended named Firestore database before any deployment.
- Main's recent verified-test refresh and YouTube/Doubt Solver changes are outside the nine AI connector phases but are existing product behavior. Preserve them and include them in regression coverage; do not accidentally remove or replace them during broad ViewModel/backend conflict resolution.

### 13.3 Validator commit and CI evidence limitation

- The short reference `a44de80` resolves to full commit `a44de807597eef0d155d6b917dca300395163b1d`.
- Its commit diff changes only `GeminiResponseValidator.kt`: it safely separates a non-object JSON item from validation and appends accepted items only when non-null.
- The current feature branch is one documentation commit beyond that validator commit; the plan file is the only file changed in that one-commit comparison.
- GitHub combined status returned an empty status list for `a44de80`. The available workflow-run wrapper also returned an empty list, but that wrapper only returns pull-request-triggered runs, whereas the feature workflow is configured for push and pull_request events. Therefore these empty responses are **not evidence of success or failure** and do not identify the four reported failures.
- Resolve the exact Actions run IDs and all four failing validator/job names from the feature branch's push run. Capture job logs, reproduce each failure, and record the exact tested SHA. Do not close Phase 1 based on the validator commit diff alone.

### 13.4 Scope items made explicit after reviewing the older 10-phase tracker

The older `UNIVERSAL_GEMINI_AI_10_PHASE_TRACKER.md`, `UNIVERSAL_GEMINI_AI_AUDIT_2026-11-08.md`, and `ADMIN_GEMINI_SECRET_SETUP.md` contain additional requirements that must remain in scope:

- **Direct PDF understanding:** send the original PDF bytes through a supported Gemini document input or File API path where applicable. Text extraction alone is not equivalent. Define file-size/page limits, MIME/signature checks, lifecycle/retention, retries, and cleanup. Preserve existing PDF session persistence, WorkManager recovery, local extraction, OCR, diagrams, and page boundaries; extend these rather than creating a competing storage pipeline.
- **OCR and visual content:** distinguish text PDFs, scanned PDFs, mixed PDFs, diagrams, equations, and unsupported pages. OCR must be a real configured implementation. If OCR is unavailable, stop with a review-required status rather than inventing text.
- **Source provenance:** persist page number/page range and source references per question through parsing, Room, Firestore, Admin review, and final test display. Verify round-trip persistence, not just in-memory parsing.
- **Review and publishing:** preserve the existing private vault, owner-private/Under Review flow, Admin review, private answer-key separation, and explicit approval before public publication. Test approve, reject, correction, ownership, and answer-key access rules end to end.
- **Universal prompt coverage:** inventory every Gemini call site, including Admin difficulty regeneration, metadata/exam recognition, Student generation, legacy extractor, and any Firebase Functions path. All supported task modes must use versioned shared prompt templates and canonical ExamConfig/registry identifiers.
- **Exam/question fidelity:** retain Hindi, English, and bilingual fidelity; source-answer confidence; duplicate detection; unsupported question-type rejection; option counts including five-option formats; MCQ single/multiple, numerical/NAT/TITA, scoring and negative marks; exact subject/section distribution; and exam-specific stage/paper identity.
- **Student/Admin key boundary:** Student key must be UID-scoped and securely stored. Admin key remains server-side in Secret Manager. Student-key-first routing must never silently fall back to an Admin-paid key; any Admin server route requires explicit, authorized policy and auditable selection. The Admin setup document says Secret Manager configuration and Cloud Functions deployment are still required before the Admin connector can be called live.
- **Quota and metering:** the older tracker marks server-enforced quota, per-key isolation, persistent token/cost metering, and abuse controls as unresolved. The newer roadmap covers these, but acceptance tests must include concurrent requests, retries/idempotency, per-student identity, reset timezone, Admin overrides, and unknown usage values.
- **Legacy transport consolidation:** the older tracker identifies multiple Gemini HTTP transports and ExamConfig/ExamConfiguration coexistence. Inventory all usages and migration compatibility before consolidation or model changes.
- **End-to-end release:** include Student/Admin flows, all supported exams, real authorized PDF fixtures, Android tests, backend tests, Firestore/Storage emulator rules, Room migrations, recovery, and exact-head APK build. No deployment, Firebase migration, rules/index deployment, or PR merge without explicit owner approval.

### 13.5 Current status corrections and evidence discipline

- The older tracker is dated 2026-11-08 and includes historical status notes. Treat it as historical evidence, not as a live CI dashboard. Reconcile its status table with the actual current source and tests before marking bugs closed.
- The Admin setup guide explicitly says Secret Manager configuration/deployment is required and Student Admin-fallback, server quota, persistent usage/cost accounting, Groq, and Cerebras were not implemented at the time of that document. Recheck the current branch before changing those statuses.
- The previous tracker baseline mentioned 299 Android tests and a pre-existing `CrashResilienceTest` failure; the newer roadmap expects 341. Discover the actual test inventory on the exact current branch, explain the difference, and report discovered, executed, skipped, and failed counts. Do not simply assume 341 or suppress the earlier failure.
- Keep every historical test result tied to its exact commit. A pass on main, an older feature commit, or a different branch does not prove the current feature head passes.

### 13.6 Added exit gate before Phase 1 can be closed

Phase 1 now requires both:
1. The four Validator failures are individually identified, reproduced, root-caused, fixed, regression-tested, and green on the exact feature commit; and
2. The cross-branch reconciliation inventory is complete, with a reviewed disposition for every main-only change that touches shared AI, PDF, exam, scoring, database, backend, rules, workflow, or product-flow files.

Until both conditions have evidence, Phase 1 remains OPEN. This addendum is an audit finding, not a claim that any missing implementation has been completed.


---

## 14. Full branch plan refresh — API Management and prior chat decisions (2026-11-12)

### 14.1 Branch-specific UI decision from the latest project discussion

- Integrate the already-built API Management UI/page into the existing Sidebar/navigation structure. Do not create a second API Management screen or a duplicate settings system.
- Use the existing API Management UI and existing branch implementation as the starting point. Defer a separate Settings-based API page.
- Student and Admin credentials remain separate. Student BYOK key storage must be encrypted locally (Android Keystore-backed implementation); never store raw keys in ordinary preferences, Firestore, source code or logs.
- Preserve the existing app UI and working flows. This branch owns connector/backend behavior and the agreed Sidebar entry integration only where required by the existing feature work.
- Audit the current feature-branch source for the existing API Management page and navigation before coding. The UI currently present on `main` is a reference only; do not modify main to make this branch work.

### 14.2 Remaining connector work

1. **Four Validator failures:** resolve exact Actions push run and failing job logs. The commit `a44de807597eef0d155d6b917dca300395163b1d` changes only `GeminiResponseValidator.kt`; empty status/workflow wrapper responses are inconclusive. Reproduce, root-cause, fix and add tests for each failure.
2. **Test inventory and APK:** reconcile the historical 299-test baseline and later 341-test target by discovering actual tests on this exact branch. Report discovered/executed/skipped/failed counts; run full suite and Debug APK build only after fixing failures.
3. **Student/Admin key route:** student-owned key first. Admin-managed key remains server-only in Firebase Secret Manager. A server fallback is allowed only under explicit server policy, verified user/role authorization and auditable route metadata. Never expose Admin key to client or silently charge the Admin key for Student requests.
4. **Existing API Management UI:** connect existing page to Sidebar; key add/test/replace/remove, provider selection, health status and quota visibility must reflect actual server/client capabilities. Do not create duplicate screens or pretend a key is valid without a real authorized provider test.
5. **Daily quota:** server-authoritative per-student setting 1/2/3/custom; Admin can configure policy. Atomic enforcement, identity from auth token, reset timezone, idempotency, concurrency, retries, cancelled/failed-job policy and clear quota errors are required.
6. **PDF ingestion:** original PDF bytes/File API path where supported, real text extraction, configured OCR for scanned pages, mixed pages, diagrams and equations, page boundaries, MIME/signature/page/size limits, retries, retention and cleanup. OCR-unavailable or unsupported content must stop for review, never fabricate text.
7. **Question provenance/review:** keep page/source references per question through Room, Firestore, Admin review and final test. Preserve private vault, owner-private/Under Review, private answer-key separation and explicit approval before public publication.
8. **Providers:** Groq and Cerebras as separate adapters behind provider-neutral interfaces; real authenticated tests, bounded retries, timeouts, 401/403/429/5xx handling and no fake-success fallback.
9. **Usage/cost:** persistent request-level provider/model/user/job/token/cost records with pricing version and traceability. If provider usage or cost is unknown, record unknown, never estimate/fabricate.
10. **Security/final regression:** role authorization, UID isolation, secret redaction, App Check/rate limits, quota bypass/replay/concurrency, duplicate generation, all exam/question formats, PDF recovery, emulator rules, full Android/backend tests and exact-head APK.

### 14.3 Cross-branch dependencies

- Use the versioned prompt and syllabus contract from `feature/universal-pdf-prompt-contract-v1` only after reviewing its source and tests. Do not fork a second syllabus resolver or Gemini transport.
- Consume deterministic subject/section allocation from the main-side `UniversalQuestionGenerationPlanner` after a reviewed, tested integration; the planner is absent from this branch at the audit snapshot. No All Subjects fallback to first subject or guessed equal split.
- Preserve scoring contract from `feature/universal-exam-scoring-v1-2026-11-06` and ranking integration branch. Provider success must not bypass scoring, question validation, source provenance or Admin publication approval.
- Resolve the 320 feature-only vs 93 main-only commit divergence through file-level reconciliation. Main is not to be merged wholesale.

### 14.4 Exit criteria

- [ ] Four Validator failures individually resolved with current-head CI evidence.
- [ ] Actual Android test inventory reconciled; full suite passes and Debug APK builds on exact HEAD.
- [ ] API Management page is connected to existing Sidebar without duplicate page/settings system.
- [ ] Student/Admin key isolation, encrypted local storage and server secret boundaries pass tests.
- [ ] Server quota, PDF pipeline, Groq, Cerebras and usage/cost metering pass real integration tests.
- [ ] No dummy/offline fallback, unapproved publication, data loss or secret leakage.
- [ ] No deployment or main merge without explicit approval.

**Audit status:** Plan updated only. No application code, CI workflow, Firebase resource or production data was changed by this documentation update.

## 2026-11-12 Cross-branch code reconciliation (X/Y/Z)

### X — KEEP
- Keep one API Management page integrated into existing Sidebar; no duplicate Settings page.
- Keep Student BYOK and Admin server-secret as separate trust boundaries.
- Target one canonical provider transport, one response validator and atomic server quota.

### Y — PORT / RECONCILE
- Port encrypted StudentGeminiKeyStore after UID isolation, replacement/removal, account switching and backup tests.
- Port Gemini response-validator hardening with null/malformed item tests.
- Reconcile aiConnector.js and geminiProxy.js into one callable connector preserving auth, BYOK/Admin permission, rate limits, errors and usage metadata.
- Implement daily quota 1/2/3/custom with atomic server enforcement and concurrency tests.
- Add Groq/Cerebras through the same provider interface after Gemini is stable; record only actual provider usage/cost.

### Z — OBSOLETE / DO NOT PORT
- Do not port OfflineMarkdownQuestionParser, bundled catalog generation or silent offline/dummy fallback.
- Do not retain plaintext SharedPreferences key store as a second store.
- Do not keep APK BuildConfig Gemini secrets, raw keys in Firestore or keys in URL query parameters.
- Do not maintain two PDF pipelines or two provider transports.

### PASS / FAIL / OPEN evidence (2026-11-12)
| Check | Result | Evidence / action |
|---|---|---|
| Validator commit a44de807597eef0d155d6b917dca300395163b1d CI | OPEN | No workflow run returned for exact SHA; four Validator failures not proven resolved. |
| Student/Admin key isolation | OPEN | End-to-end encryption, UID isolation and Secret Manager tests not established. |
| Existing API Management Sidebar integration | OPEN | Verify actual route and Sidebar call sites before coding. |
| Daily quota 1/2/3/custom | OPEN | Atomic backend enforcement and concurrency tests required. |
| PDF/Groq/Cerebras/usage-cost | OPEN | End-to-end integration evidence absent. |
| Full Android 341 tests + Debug APK | OPEN | Exact inventory and successful current-head build not established. |

### Ordered work
1. Refresh branch head/CI and resolve four Validator failures individually.
2. Integrate main planner and remove offline/dummy fallback from active call graph.
3. Implement one Student/Admin connector and existing Sidebar integration.
4. Add atomic quota, provider adapters and real usage/cost persistence.
5. Integrate canonical PDF pipeline.
6. Run Android, backend, emulator, secret-scan and exact-head APK regression.

**Verdict:** SELECTIVE PORT. No connector/PDF phase is PASS based on source presence alone.
