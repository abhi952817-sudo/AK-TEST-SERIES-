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
