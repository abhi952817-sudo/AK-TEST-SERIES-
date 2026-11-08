# Universal Gemini AI — Master Implementation Contract
Date: 2026-11-08
Repository: abhi952817-sudo/AK-TEST-SERIES-
Implementation branch: feature/universal-gemini-ai-10-phase-2026-11-08
Source audit: docs/UNIVERSAL_GEMINI_AI_AUDIT_2026-11-08.md
Live tracker: docs/UNIVERSAL_GEMINI_AI_10_PHASE_TRACKER.md

## Mission
Complete the AK TEST SERIES Universal Gemini AI workflow across the ten approved phases in one coordinated development campaign. Internal implementation remains phase-gated: do not start dependent work until its interfaces and acceptance tests are stable. The goal is one integrated release candidate, not ten disconnected implementations.

## Non-negotiable repository rules
1. Never commit directly to `main`; keep all work on the dedicated feature branch.
2. Do not merge any pull request or deploy Firebase Functions, Firestore/Storage rules, indexes, migrations or production data without explicit owner approval.
3. Before modifying an existing feature, enumerate its callers, tests, dependencies and persisted-data contracts. Extend working code; do not replace it.
4. Preserve existing local PDF storage, WorkManager recovery, OCR, diagram cropping, exam registry, question validation, duplicate detection, private vault, Admin review and Test Player.
5. No placeholder, dummy, synthetic or guessed question may be described as extracted from a source PDF or generated/verified by Gemini. Mock mode must be separately named, opt-in and visibly labeled.
6. Unknown or ambiguous exams must require confirmation. Never silently default an unknown exam to NEET or the first registry item.
7. A generated Student test is private by default. Public release requires explicit Admin approval. Public documents must not expose answer keys or explanations used for authoritative grading.
8. Student API keys must be isolated by Firebase UID and device-local encrypted storage. Never put Student keys in Firestore, analytics, Crashlytics, logs, URLs, source code, BuildConfig or shared Admin preferences.
9. Every completed phase needs source evidence, bug IDs, tests on the exact commit, commit SHA and tracker update.
10. Do not weaken, skip or delete failing tests to claim a green build.

## Phase execution contract
### Phase 1 — Complete Source Audit
- Inventory all Gemini HTTP/SDK call sites, prompt construction, API-key access, PDF upload/extraction, question parsing, test save, publication, Admin review and Test Player consumers.
- Record caller -> callee -> dependency -> persistence map.
- Identify duplicate clients and dead/legacy paths.
- Capture exact-branch backend, Firestore emulator, Android unit-test and debug-build baseline.
- Exit: audit inventory complete; all existing flows and compatibility contracts documented.

### Phase 2 — Universal Prompt Engine
- Preserve `UniversalAIContext` as the source of existing exam-specific policy; evolve it behind one versioned engine rather than create competing prompt builders.
- Introduce explicit task modes: EXAM_RECOGNITION, PDF_QUESTION_EXTRACTION, QUESTION_GENERATION, ANSWER_VERIFICATION, EXPLANATION, DIAGRAM_ANALYSIS, QUESTION_REPAIR.
- Inputs: canonical examId, verified config version/year, stage, paper, subject, section, question type, language, source references, requested count, difficulty, marking rules.
- Every prompt must state source-of-truth hierarchy, no fabrication, uncertainty handling and exact output schema.
- Exit: all Gemini call sites receive prompts through the same engine; deterministic unit tests cover every task mode and language.

### Phase 3 — Student Gemini Connect
- Add AI Studio link, paste-key screen, local Verify, connect, replace, disconnect and revoke guidance.
- Use Android Keystore-backed authenticated encryption for local key material; scope key records by authenticated UID and clear in-memory copies on sign-out/account switch.
- Never upload a Student key to the backend. The client calls Gemini directly with the user's own key unless the architecture explicitly changes with owner approval.
- Add seven-day reminder as a local notification/reminder preference; do not rotate or revoke automatically.
- Validate modern Gemini authorization keys. Google documentation states new AI Studio keys are auth keys by default and standard keys are scheduled for rejection from September 2026; the current app date is after that migration date.
- Exit: valid/invalid/restricted/revoked key tests; account-switch isolation; no plaintext persistence or leakage.

### Phase 4 — Direct PDF Understanding
- Use Gemini native PDF document input: inline PDF data for small/temporary documents or Files API upload for larger/reused files, based on current official limits.
- Preserve the current local PDF storage/OCR/page extraction/diagram pipeline as a useful fallback and independent validation path; do not duplicate it.
- Maintain file hash, page boundaries, per-question source page(s), and upload/retry status.
- Exit: real authorized PDFs with text, scanned pages, diagrams, charts and tables; page references survive into saved questions.

### Phase 5 — Universal Exam Recognition
- Reuse `UniversalExamRegistry`; reconcile `ExamConfig` and `ExamConfiguration` only after call-site inventory.
- Recognition must return canonical examId, year/config version, stage/paper/subject, language, duration, question count, marking scheme, section rules and confidence/evidence.
- Ambiguous/unsupported recognition requires user confirmation; never use a silent default exam.
- Exit: verified fixtures for supported exams and unknown/ambiguous cases.

### Phase 6 — Question & Solution Generation
- Support configured MCQ, MSQ, NAT/TITA and other exam-specific types.
- Validate exact schema, option cardinality, answer format, numeric precision, marks, negative marks, section, bilingual fields, explanation and source page before accepting a question.
- Distinguish source-extracted questions from newly generated practice questions.
- Reject duplicates, fabricated filler, unsupported answer keys, incomplete diagrams and malformed responses.
- Exit: strict valid/invalid fixtures and source-provenance tests.

### Phase 7 — Automatic Test Creation
- Extend existing `saveGeneratedTest` and existing Test Player; do not create a second player or storage path.
- Preserve exam identity/config version, timer, marks, sections, question types, language, source metadata, owner UID and generation provenance.
- Autosave/resume must be idempotent; incomplete batches must not be reported as complete.
- Exit: Student-generated test runs through existing player, submission and result flows without score/config drift.

### Phase 8 — Private/Public & Admin Review
- Preserve existing Student-public `Under Review` state, Admin review list and Admin-only `publishReviewedTest` flow.
- Add missing reject, request-correction and revision history only after confirming existing equivalents.
- Keep private answer keys server-protected; strip keys/explanations from public records before publishing.
- Exit: emulator tests prove owner-only private access, Admin-only review, approval gate and no answer-key leakage.

### Phase 9 — Reliability, Security & Cost Controls
- Centralize network timeouts, retry/backoff, rate-limit parsing, model fallback, cancellation and resumable jobs in the shared Gemini client.
- Add per-user daily quotas, request concurrency caps, token usage/cost estimates and abuse protection. Enforce authoritative limits server-side where applicable; local UI counters are not security boundaries.
- Redact secrets from all logs and crash reports.
- Exit: offline, timeout, quota, invalid-key, revoked-key, retry, cancellation, account-switch and key-isolation tests.

### Phase 10 — Full Integration & Release Testing
- Run all relevant Android unit tests, backend tests, Firestore/Storage emulator rules tests and debug build on the exact final feature-branch head.
- Run Student/Admin end-to-end flows with authorized real PDF fixtures for Hindi, English and bilingual cases and different exam types.
- Fix pre-existing CI baseline failures; do not suppress them.
- Produce a final report with phase statuses, files, bug IDs, test counts, workflow links, commits and known limitations.
- Exit: every mandatory check passes; draft PR only. No merge or production deployment without explicit owner approval.

## API implementation references
- Gemini API authentication: use the `x-goog-api-key` header. Avoid putting keys in URL query strings.
- Gemini document understanding: PDF input is supported through native document processing; inline PDF data is suitable for smaller temporary documents, while Files API is recommended for larger/reused files.
- Verify current model names, request schema, file limits and key lifecycle against official docs at implementation time; do not hardcode model names based only on old code.

## Current blockers to carry forward
- GEM-001 through GEM-016 are listed in the live tracker.
- Baseline Android CI on source commit `9b105e9e7c614d615becb99a72b3dc5e50799e05` had one failure: `CrashResilienceTest > classMethod` (`UnsupportedOperationException` at `DefaultSdkProvider.java:170`); backend and Firestore rules jobs passed. Fix or isolate root cause without weakening the suite.
- Phase 1 remains open until the full call-site/dependency inventory and exact-branch baseline are complete.

## Final reporting format
For every phase record: status, changed files, preserved existing implementation/call sites, bug IDs closed, tests run and results, CI run/head SHA, commit SHA, remaining risks and any owner decision needed.
