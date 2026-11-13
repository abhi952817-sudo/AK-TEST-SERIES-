# AK TEST SERIES — Root-to-Fruit Master Development & Completion Tracker

> **Purpose:** One authoritative plan to inspect, complete, test, integrate, and release AK TEST SERIES without duplicating existing systems or losing important work from any branch.
>
> **Repository:** `abhi952817-sudo/AK-TEST-SERIES-`  
> **Baseline:** `main` at `1e236d099522d63ec89ab0d11287b48315efc48c`  
> **Plan branch:** `integration/root-to-fruit-master-2026-11-13`  
> **Created:** 2026-11-13  
> **Overall status:** 🟡 PLANNING / REPOSITORY RECONCILIATION IN PROGRESS  
> **Code implementation status:** Not started by this plan commit. No application source files have been changed by creating this tracker.

---

## 0. Operating Rules — Read Before Any Change

1. **Inspect before implementing.** Search the current branch and all relevant branches for an existing implementation, service, model, API, rule, migration, test, or UI before creating anything.
2. **Reuse, do not duplicate.** Keep one canonical implementation for each shared capability (AI connector, prompt engine, exam registry, question model, test engine, scoring, ranking, authentication).
3. **Universal architecture, staged exam delivery.** Shared systems must support multiple exams through configuration. NEET UG is the first end-to-end launch target; other exams are enabled only after their own configuration and verification.
4. **Markdown is a claim, not proof.** A `.md` status of COMPLETE is not accepted until code, tests, and relevant CI evidence support it.
5. **No silent fallback to unsafe or misleading data.** Never fabricate questions when extraction/generation fails; never silently substitute NEET rules for an unknown exam.
6. **Security first.** Never expose answer keys, privileged credentials, private student data, or server secrets to clients or public community documents.
7. **Small, reviewable commits.** Each coherent change must have tests, a clear commit message, and an update to this tracker.
8. **No blind branch merges.** Diverged branches are inspected and selectively ported. Do not merge a branch solely because its name or documentation says it is complete.
9. **Preserve working behavior.** Record baseline tests and affected call sites before refactoring. Add regression tests before or alongside fixes.
10. **Truthful verification.** Distinguish static code review, unit tests, emulator tests, device tests, live API tests, and production verification. Never report one as another.
11. **Release gates are mandatory.** A phase is not complete until its acceptance criteria and dependencies pass.
12. **No production deployment, destructive data migration, secret rotation, branch deletion, or force-push without explicit approval.**
13. **Single implementation, existing entry points.** A capability shared by Student and Admin must be implemented once in the existing shared service/component and reused from both existing flows. Do not copy-paste parallel implementations or create a second control dashboard.
14. **Keep controls at their source.** Admin-only configuration, backup/restore, provider settings, and publishing controls stay in the existing Admin/settings location where that function already belongs. Student screens expose only the student action/view; do not add duplicate Admin control buttons to Student UI. Use existing role checks and navigation rather than inventing a new control layer.
15. **Prefer the smallest working integration.** Extend existing models, repositories, callable functions, and UI screens in their current patterns. Add a new abstraction only when the existing architecture cannot safely support the requirement; document why before adding it.


### Status vocabulary

- `NOT_STARTED` — not yet inspected or worked.
- `INVENTORY_DONE` — files and implementation locations identified.
- `IN_PROGRESS` — work underway.
- `PARTIAL` — some functionality exists; gaps remain.
- `BLOCKED` — dependency or external requirement prevents progress.
- `IMPLEMENTED_UNVERIFIED` — code exists, but adequate tests/evidence are missing.
- `VERIFIED` — required tests and evidence passed.
- `ACCEPTED` — verified and accepted for integration/release.
- `NOT_APPLICABLE` — explicitly reviewed and not required, with rationale.

Severity: `P0` release/security blocker, `P1` critical functionality, `P2` important, `P3` improvement.

---

## 1. Release Goal and Definition of Done

### 1.1 First release: NEET UG complete student journey

- [ ] Registration / login / session recovery
- [ ] Student profile and role-safe dashboard
- [ ] NEET UG exam configuration and test list
- [ ] Admin-created/imported test and question bank
- [ ] Test instructions and attempt creation
- [ ] Question navigation, answer selection, review marking
- [ ] Reliable timer and interruption recovery
- [ ] Answer persistence / autosave and reconnect behavior
- [ ] Safe, idempotent submission
- [ ] Server-side score calculation
- [ ] Result and authorized solution review
- [ ] Ranking eligibility and ranking display
- [ ] Admin audit trail and test publish/unpublish
- [ ] Security, regression, privacy, and release checks

**NEET pattern rule:** verify the target exam year's official pattern and the repository's exam registry before changing question count, duration, sections, or marking rules. Do not hard-code an unverified pattern.

### 1.2 Release quality gates

- [ ] No known P0/P1 security or data-integrity issue open.
- [ ] No answer-key leak in student-facing APIs, logs, or public documents.
- [ ] No fake/mock questions presented as extracted or generated real questions.
- [ ] Firestore/Storage rules tested against authorized and unauthorized users.
- [ ] Scoring golden tests pass for every supported question type.
- [ ] Attempt submission is idempotent and duplicate-safe.
- [ ] Ranking is based only on eligible, verified attempts.
- [ ] Database migrations preserve existing user data.
- [ ] Critical student/admin flows pass integration tests.
- [ ] CI is green for the release commit; flaky/failing jobs are investigated, not ignored.
- [ ] APK/build and supported deployment configuration are reproducible.
- [ ] Privacy, data deletion, retention, backup/restore, and crash reporting are reviewed.
- [ ] Known limitations are documented; no unsupported feature is labelled complete.

---

## 2. Repository and Branch Register

**Important:** This is a work-allocation register, not an instruction to change every branch independently. Each branch will be inspected. Useful changes will be selectively ported to the canonical integration branch. A branch with no unique useful delta may be marked `NO_CHANGE_REQUIRED`.

| Branch | Intended area / review focus | Initial disposition | Status |
|---|---|---|---|
| `main` | Baseline app, master tracker, existing services and architecture | Protected baseline; integrate accepted work only after review | INVENTORY_DONE (partial review) |
| `audit/all-exam-scoring-ranking-2026-11-13` | Cross-exam scoring/ranking audit, policy gaps, golden tests | Audit source; port verified fixes/tests only | INVENTORY_DONE (report reviewed; code verification pending) |
| `feature/community-completion-2026-11-13` | Community implementation, moderation, posts, shared questions | 41 commits ahead of main; inspect unique delta and reconcile with community-development branch | INVENTORY_DONE (source verification pending) |
| `feature/community-development-plan-2026-11-13` | Community feature plan, moderation, posts, shared questions | Inspect implementation vs plan; port only missing, tested pieces | INVENTORY_DONE (plan reviewed; source verification pending) |
| `feature/ranking-engine-v2-2026-11-06` | Ranking v2, transactions, eligibility, idempotency, CI | Diverged; do not merge wholesale; selectively port proven components | INVENTORY_DONE (plan/CI report reviewed; source verification pending) |
| `feature/ranking-universal-integration-v1` | Universal ranking integration, exam verification, roadmap | Reconcile contracts and migrations with canonical scoring/ranking | INVENTORY_DONE (selected docs reviewed) |
| `feature/universal-exam-scoring-v1-2026-11-06` | Universal scoring rules and remaining work | Reconcile scoring contract; port tests and safe implementation | INVENTORY_DONE (plan reviewed; source verification pending) |
| `feature/universal-gemini-ai-10-phase-2026-11-08` | Universal AI connector, prompt engine, Gemini, provider adapters, quota/cost | High-value but heavily diverged; inspect and selectively port | INVENTORY_DONE (selected docs reviewed; CI concerns recorded) |
| `feature/universal-pdf-prompt-contract-v1` | PDF/prompt contract, syllabus readiness, provenance | Reconcile with one canonical AI/PDF pipeline | INVENTORY_DONE (selected docs reviewed) |
| `fix/phase1-real-questions-only` | Prevent fake questions; extraction failure behavior | Inspect exact call sites and regression tests; port minimal fix | INVENTORY_DONE (plan reviewed; source verification pending) |
| `restore-files/removed-env-gitignore-2026-10-19` | Restore/security-sensitive repository files, gitignore/env protection | Verify current main already contains safe equivalents; do not blindly restore | INVENTORY_DONE (plan reviewed) |
| `usi` | CBT simulation, question palette, exam controls | Inspect UI and test-engine compatibility; port compatible components | INVENTORY_DONE (plan reviewed; source verification pending) |

### 2.1 Branch safety checks — required before porting

- [ ] Refresh all branch HEAD SHAs immediately before work.
- [ ] Compare each branch against `main` and the integration branch.
- [ ] Record ahead/behind counts, merge base, changed files, and unique commits.
- [ ] Inspect changed source files, tests, build files, rules, and migrations.
- [ ] Identify cherry-pick conflicts and duplicate implementations.
- [ ] Record source branch + commit SHA for every ported change.
- [ ] Do not delete branches as part of this project without separate approval.

---

## 3. Root-to-Fruit Dependency Map

The sequence below is dependency-led. Independent tasks may proceed in parallel only after their contracts are agreed.

```text
ROOT 0  Repository inventory, branch reconciliation, build/test baseline
  |
ROOT 1  Core foundation: auth, roles, database, storage, backend, security
  |
ROOT 2  Shared contracts: exam IDs, question model, attempt model, API contracts
  |
ROOT 3  Universal AI foundation: provider interface + prompt engine + quotas
  |
ROOT 4  Universal exam registry + syllabus + scoring policy
  |
TRUNK   Question bank + admin authoring + test engine + attempt lifecycle
  |
BRANCH PDF/AI ingestion ----> review/approval ----> question bank
  |
BRANCH Student CBT ---------> submit ------------> server scoring
  |
BRANCH Result/review -------> eligible ranking
  |
BRANCH Community/doubts/notifications (safe, isolated integrations)
  |
FRUIT   NEET UG end-to-end release + security/regression sign-off
```

### Dependency rules

- Authentication/authorization precedes privileged admin and student operations.
- Data model and API contracts precede parallel client/backend implementation.
- Exam configuration precedes exam-specific question validation and test setup.
- Question model and answer-key access policy precede AI import and review UI.
- Attempt lifecycle precedes scoring; scoring precedes ranking.
- PDF extraction and AI generation may run in parallel with CBT UI after their shared question schema is frozen.
- Community is isolated from private answer keys and student attempt records.
- Release integration begins only when each component provides tests and a documented contract.

---

## 4. Phase Plan and Completion Gates

### PHASE 0 — Full repository intelligence audit

**Goal:** know what exists before changing code.

- [ ] Read all relevant Markdown plans/reports in all 12 branches (not just selected documents).
- [ ] Inventory all source files by module and feature.
- [ ] Inventory Android app architecture, Gradle modules, dependencies, and build variants.
- [ ] Inventory backend functions, callable/HTTP APIs, scheduled jobs, and shared helpers.
- [ ] Inventory Firestore collections, document shapes, indexes, and security rules.
- [ ] Inventory Storage paths, upload/delete call sites, and Storage rules.
- [ ] Inventory Room entities, DAOs, database versions, and every migration.
- [ ] Inventory AI providers, HTTP clients, prompt templates, API-key paths, and call sites.
- [ ] Inventory question/test/attempt/result/ranking data models.
- [ ] Inventory CI workflows, latest runs, failure logs, and test commands.
- [ ] Build branch-to-file-to-feature matrix.
- [ ] Build duplicate-function/service inventory.
- [ ] Record all issues with ID, severity, evidence, status, owner/workstream, and next action.

**Exit gate:** every important feature has an evidence-backed status; no branch is selected for porting based only on its README/plan.

### PHASE 1 — Core foundation and security

- [ ] Authentication lifecycle and session handling.
- [ ] Student/admin role enforcement on server and database rules.
- [ ] Authorization checks for every privileged callable/API.
- [ ] Firestore schema and rules review.
- [ ] Storage upload/download/delete authorization and validation.
- [ ] Backend validation, error contracts, rate limiting, abuse controls.
- [ ] Secret and API-key handling; no secrets in source, logs, or client bundle.
- [ ] Room schema/migration safety and data-retention review.
- [ ] Logging, crash reporting, observability, and privacy-safe diagnostics.
- [ ] Backup/restore and account/data deletion behavior documented and tested.

**Exit gate:** core security tests pass; migration and data-loss risks are addressed or explicitly release-blocking.

### PHASE 2 — Shared contracts and universal models

- [ ] Stable exam identifier and exam-version contract.
- [ ] Canonical question schema (type, options, language, media, source, explanation, answer access).
- [ ] Canonical test/paper/section schema.
- [ ] Canonical attempt lifecycle and status transitions.
- [ ] Canonical scoring result schema.
- [ ] Canonical ranking eligibility and tie-break policy.
- [ ] Client/server API contracts and error codes.
- [ ] Backward compatibility and migration strategy.
- [ ] Contract tests shared across Android and backend.

**Exit gate:** schemas and contracts reviewed; duplicate models mapped to one canonical form; migrations tested.

### PHASE 3 — Universal AI connector and prompt engine

#### Connector

- [ ] One provider-neutral interface and one request/response/error model.
- [ ] Gemini adapter verified against actual supported API version.
- [ ] Groq adapter only if provider API/configuration is implemented and tested.
- [ ] Cerebras adapter only if provider API/configuration is implemented and tested.
- [ ] Provider capability registry (models, modalities, limits, structured output).
- [ ] Secure admin-managed credentials and student key handling policy.
- [ ] Key encryption/secret storage and key redaction.
- [ ] Connect/verify/replace/disconnect flows.
- [ ] Timeout, retry with backoff, cancellation, and idempotency behavior.
- [ ] Quota/rate limits and per-user/per-provider abuse protection.
- [ ] Token/usage/cost metering with privacy-safe logs.
- [ ] Provider health/fallback policy; no silent change that alters task quality.
- [ ] Unit, contract, failure-mode, and live-provider smoke tests.

#### Prompt engine

- [ ] One versioned prompt registry; remove duplicate prompt literals after call-site migration.
- [ ] Task contracts for PDF extraction, question generation, answer verification, explanations, syllabus mapping, and regeneration.
- [ ] Exam context supplied from exam configuration, not hard-coded NEET defaults.
- [ ] Language and bilingual policy.
- [ ] Strict structured-output schema validation.
- [ ] Prompt-injection and untrusted-document handling.
- [ ] Source/page provenance and uncertainty fields.
- [ ] Retry/repair policy with bounded attempts.
- [ ] Prompt regression fixtures and model/provider compatibility tests.

**Exit gate:** all active AI call sites use the shared connector and prompt contracts, or each exception is documented and approved.

### PHASE 4 — Universal exam registry, syllabus, and scoring

- [ ] Verify each exam registry entry against authoritative, current sources.
- [ ] NEET UG configuration verified for the selected exam year.
- [ ] Subjects, papers, sections, question types, duration, marks, negative marks.
- [ ] Optional-section and choice rules represented explicitly.
- [ ] Syllabus versioning and topic/chapter mapping.
- [ ] Exam-specific instructions and navigation rules.
- [ ] Unknown exam IDs fail clearly; never silently fall back to NEET.
- [ ] Scoring policy is server-authoritative and versioned.
- [ ] Strict numeric parsing and validation; reject malformed/trailing input.
- [ ] Golden tests for single choice, multi-select, numerical, unanswered, review, partial/optional sections, and edge cases.
- [ ] Tie-breaking and score precision policy documented.
- [ ] Cross-exam isolation tests.

**Exit gate:** NEET configuration and scoring fixtures verified; other exam entries remain marked unverified until individually sourced and tested.

### PHASE 5 — Question bank, PDF intelligence, and admin approval

- [ ] Upload validation (size, MIME, file integrity, access).
- [ ] Preserve original document and safe metadata.
- [ ] Extract text and images; OCR fallback for scanned pages.
- [ ] Real PDF bytes passed through supported provider/file API path where required.
- [ ] Page-level source provenance and extraction confidence.
- [ ] Syllabus/topic classification.
- [ ] Extraction-only mode distinct from question-generation mode.
- [ ] AI question generation with explicit count/type/difficulty constraints.
- [ ] Answer and explanation verification.
- [ ] Duplicate detection and question-quality validation.
- [ ] No fake/mock questions on failure; surface actionable error.
- [ ] Draft state, admin review, correction, approval, publish/unpublish.
- [ ] Answer keys/explanations restricted to authorized server/review flows.
- [ ] Audit trail for imports, edits, approvals, and publication.
- [ ] Tests for corrupted PDF, image-only PDF, multilingual PDF, empty extraction, timeout, invalid JSON, duplicate, and rejected answer.

**Exit gate:** failed extraction cannot publish or masquerade as success; every published question has review status and provenance policy.

### PHASE 6 — Student app, admin panel, and CBT test engine

#### Student

- [ ] Registration/login/logout and account state.
- [ ] Profile and exam dashboard.
- [ ] Exam/test list filtered by availability and eligibility.
- [ ] Instructions and attempt start.
- [ ] Question rendering for supported types and media.
- [ ] Answer selection, clear response, mark for review.
- [ ] Question palette and section navigation.
- [ ] Timer based on server-authoritative start/deadline.
- [ ] Autosave, retry queue, reconnect, and conflict handling.
- [ ] App background/process death recovery.
- [ ] Submit confirmation and safe final submission.
- [ ] Accessible Hindi/English UI and supported device layouts.

#### Admin

- [ ] Role-protected admin dashboard.
- [ ] Exam/test creation and configuration.
- [ ] Question authoring/import/edit.
- [ ] Answer validation and preview.
- [ ] Draft/review/approve/publish/unpublish lifecycle.
- [ ] Test availability and schedule management.
- [ ] Student/attempt moderation tools with audit logs.
- [ ] Permission tests for every admin action.

#### CBT reliability

- [ ] Attempt state machine and server timestamps.
- [ ] Idempotent start/save/submit endpoints.
- [ ] Duplicate device/session policy.
- [ ] Offline/interruption recovery policy.
- [ ] Autosave acknowledgment and pending-save visibility.
- [ ] Timer expiry and auto-submit race tests.
- [ ] Multi-device and concurrent request tests.

**Exit gate:** student can complete a real NEET test and admin can safely create/review/publish it in test environment.

### PHASE 7 — Server scoring, result, solutions, and ranking

- [ ] Server calculates score from trusted answer key and submitted responses.
- [ ] Submission response never leaks answer keys/solution keys.
- [ ] Attempt record stores only necessary attempt data.
- [ ] Review solutions fetched only through authorized, policy-gated endpoint.
- [ ] Score calculation version and exam configuration snapshot retained.
- [ ] Duplicate submissions cannot create duplicate results.
- [ ] Ranking only includes eligible, finalized attempts.
- [ ] Ranking transaction/aggregation consistency.
- [ ] Tie handling, percentile/rank semantics, and privacy rules.
- [ ] Ranking separated by exam, exam year, test, and cohort as required.
- [ ] Recalculation/backfill process is auditable and idempotent.
- [ ] Golden tests and concurrency tests.

**Known audit items to re-verify:** F-016 answer/solution-key response leakage; F-004 permissive numeric parsing; other audit IDs from the scoring/ranking report. Do not assume these remain present or fixed until current code is inspected.

**Exit gate:** scoring and ranking pass golden, authorization, duplicate, and concurrency tests; no answer-key leakage.

### PHASE 8 — Community, doubt solving, notifications, and analytics

- [ ] Community post types and validation.
- [ ] Public shared questions exclude correct answers and private explanations.
- [ ] Image/file upload authorization, limits, and content metadata.
- [ ] Report/block/mute and moderation workflow.
- [ ] Admin moderation audit trail.
- [ ] Doubt/question flow and answer permissions.
- [ ] Notifications with user preferences and permission handling.
- [ ] Analytics events are privacy-minimized and access-controlled.
- [ ] Community and analytics cannot access private exam answer keys/attempt data.
- [ ] Firestore/Storage rules and tests for all post types.

**Known audit item to re-verify:** F-023 public community share may include `correctAnswer`; inspect current call sites and tests before fixing.

**Exit gate:** community features pass authorization and privacy tests; moderation paths work.

### PHASE 9 — Integration, regression, and NEET release

- [ ] Integrate only reviewed commits/components from source branches.
- [ ] Resolve dependency/version conflicts.
- [ ] Run backend unit/integration tests.
- [ ] Run Android unit/UI/Robolectric tests available in CI.
- [ ] Run Firestore and Storage emulator security tests.
- [ ] Run Room migration tests across supported historical versions.
- [ ] Run AI connector mock/contract tests and controlled live smoke tests.
- [ ] Run PDF fixture suite.
- [ ] Run end-to-end student and admin journeys.
- [ ] Run security/privacy review and dependency vulnerability checks.
- [ ] Verify CI workflow stability and investigate repeated failures.
- [ ] Produce release build and verify install/startup on supported Android versions.
- [ ] Verify production configuration separately; no production deployment without approval.
- [ ] Record known limitations, rollback plan, release notes, and sign-off.

**Exit gate:** all P0/P1 issues closed, all mandatory tests pass, NEET student/admin journeys verified, and release approval recorded.

---

## 5. Parallel Workstreams

Parallel work is allowed only after Phase 0 inventory and shared contract decisions.

| Workstream | Scope | Source branches to inspect | Required handoff |
|---|---|---|---|
| A — Core & Security | Auth, roles, Firestore/Storage, backend, Room, secrets | main, restore, audit | Auth/API/data/security contracts + tests |
| B — AI & Documents | Connector, prompts, providers, PDF, syllabus | Gemini, PDF contract, real-questions | Provider/prompt/document contracts + fixtures |
| C — Exam & CBT | Registry, question model, test player, admin, attempt lifecycle | scoring, USI, main | Exam/question/attempt contracts + UI/backend tests |
| D — Scoring & Ranking | Scoring, result, review authorization, ranking | audit, scoring, ranking v2, ranking integration | Scoring/ranking contracts + golden/concurrency tests |
| E — Community | Posts, shared questions, uploads, moderation, doubt | community plan, main | Community schema/rules + privacy tests |
| Integration owner | Conflict resolution, dependency gates, release evidence | integration branch | Integration log, CI evidence, release checklist |

Do not start dependent coding in parallel before its contract is approved. If a workstream discovers a shared-schema change, pause dependent merges and update the contract first.

---

## 6. Branch Change Register — File-Level Work to Fill During Inspection

For every candidate change, add a row with:
- Source branch and exact HEAD SHA
- Exact file path and symbol/function/class
- Existing behavior and call sites
- Defect or missing behavior with evidence
- Proposed minimal change
- Contract/data migration impact
- Security/privacy impact
- Tests to add/update
- Port method (cherry-pick, patch, manual port, or no change)
- Resulting integration commit SHA
- Status and reviewer

| ID | Source branch | File / symbol | Change required | Tests | Status |
|---|---|---|---|---|---|
| BR-001 | main | Pending Phase 0 inventory | Identify canonical existing implementations; no assumed rewrite | Build + feature inventory | NOT_STARTED |
| BR-002 | audit branch | Pending source verification | Verify audit findings and port confirmed fixes/tests only | Scoring/ranking regression | NOT_STARTED |
| BR-003 | community completion | Pending fresh comparison | Confirm whether any unique changes exist | Branch diff | NOT_STARTED |
| BR-004 | community development | Pending source verification | Map plan to current community implementation | Rules/UI/backend tests | NOT_STARTED |
| BR-005 | ranking v2 | Pending source verification | Reconcile transaction, eligibility, idempotency, CI failures | Ranking unit/concurrency | NOT_STARTED |
| BR-006 | ranking integration | Pending source verification | Reconcile integration/migrations and remove duplicates | Contract/integration tests | NOT_STARTED |
| BR-007 | universal scoring | Pending source verification | Canonical scoring rules and strict validation | Golden scoring suite | NOT_STARTED |
| BR-008 | universal Gemini AI | Pending source verification | Canonical connector/prompt engine and provider controls | Provider/prompt tests | NOT_STARTED |
| BR-009 | PDF prompt contract | Pending source verification | Canonical document and syllabus contracts | PDF fixtures/schema tests | NOT_STARTED |
| BR-010 | real questions only | Pending source verification | Remove misleading fallback behavior if still present | Failure-path tests | NOT_STARTED |
| BR-011 | restore files | Pending source verification | Verify secrets/gitignore/build wrappers; restore only missing safe files | Secret scan/build | NOT_STARTED |
| BR-012 | USI | Pending source verification | Port compatible CBT functionality | UI/attempt/timer tests | NOT_STARTED |

---

## 7. Known Findings to Re-check (Not Automatically Current)

These findings were previously reported and must be re-tested against the current source:

| Finding ID | Previously reported location / issue | Severity | Required verification | Status |
|---|---|---:|---|---|
| F-016 | `backend_functions/index.js`, `submitTestAttemptServerSide` may return `solutionKeys` | P0 | Inspect response and add regression test proving no key leak | UNVERIFIED |
| F-004 | `backend_functions/examScoring.js` uses permissive `parseFloat` validation | P1 | Inspect parser and test malformed/trailing numeric input | UNVERIFIED |
| F-023 | `NEETViewModel.kt` community share may copy `correctAnswer` into public docs | P0 | Inspect share call site and public schema/rules | UNVERIFIED |
| F-009 | `NEETViewModel.kt` may create mock questions after AI/PDF failure | P1 | Inspect fallback call sites and failure UX | UNVERIFIED |
| GEM-004 | Multiple Gemini HTTP clients | P2 | Inventory all clients and active call sites | UNVERIFIED |
| GEM-005 | Real PDF bytes / provider File API path reportedly incomplete | P1 | Trace upload bytes to provider request | UNVERIFIED |
| GEM-007 | Unknown exam may silently fall back to NEET | P1 | Inspect exam resolver and add unknown-ID test | UNVERIFIED |
| GEM-008 | Prompt duplication | P2 | Search all prompt literals and call sites | UNVERIFIED |
| GEM-010 | Retry/usage metering inconsistencies | P2 | Trace retry and metering lifecycle | UNVERIFIED |
| GEM-014 | Server-side quota/abuse controls unverified | P1 | Inspect server enforcement and abuse tests | UNVERIFIED |

Add all additional findings from the complete audit report; do not limit the register to these examples.

---

## 8. Test Strategy and Evidence Ledger

For each feature, record the exact command/workflow, commit SHA, date, environment, result, and artifact/log link.

| Test layer | What it proves | Required evidence |
|---|---|---|
| Static review | Code paths/contracts appear correct | File + symbol + reviewer notes |
| Unit tests | Individual logic works | Test name and CI result |
| Contract tests | Client/server/provider schemas agree | Contract test result |
| Emulator tests | Firebase rules/functions behave in emulator | Emulator logs and test result |
| Integration tests | Connected components work together | Workflow/test artifact |
| Device tests | App works on real supported device | Device/OS/build record |
| Live API smoke test | External provider/API works with controlled credentials | Provider/model/time/result; redact secrets |
| Release verification | Packaged build and deployment config are correct | Build artifact/checklist |

### CI history already noted — re-check before relying on it

Previous documents reported unstable CI on some feature branches, including:
- Gemini branch: many failed/cancelled runs in its recorded history.
- Ranking v2: Android CI was reported healthy, while Ranking V2 Validation had substantial failures.
- Real-questions-only branch: Android CI history showed failures/cancellations.

These are historical report claims. Refresh current workflow runs and inspect failure logs before making decisions.

---

## 9. Integration and Commit Protocol

1. Keep `main` as the protected release baseline.
2. Use this integration branch as the consolidation workspace.
3. Refresh source branch HEADs before every port.
4. Port one coherent feature/fix at a time; preserve original source SHA in the log.
5. Run targeted tests after each port.
6. Run impacted regression suites before merging dependent components.
7. Resolve conflicts semantically; do not accept bulk conflict resolution without code review.
8. Update this tracker in the same change or immediately after the verified work.
9. Keep integration commits small and reversible.
10. Merge to `main` only after release gates and explicit approval.

### Integration log

| Date | Source branch / commit | Ported change | Tests | Integration commit | Status |
|---|---|---|---|---|---|
| 2026-11-13 | main baseline `1e236d099522d63ec89ab0d11287b48315efc48c` | Created this planning branch; no application source changes | Branch creation confirmed | Pending file commit | IN_PROGRESS |

---

## 10. Risk Register

| Risk | Impact | Mitigation | Status |
|---|---|---|---|
| Diverged branches contain conflicting implementations | Regressions / lost work | Compare merge bases and selectively port | OPEN |
| Docs claim completion without source/test evidence | False readiness | Require code + tests + CI evidence | OPEN |
| Duplicate AI clients/prompts | Cost, inconsistent behavior | One connector and versioned prompt registry | OPEN |
| Answer-key leakage | Exam integrity/security | Server-only key access and regression tests | OPEN |
| Fake questions after extraction failure | Student misinformation | Fail closed and show explicit error | OPEN |
| Exam config silently defaults to NEET | Wrong exam behavior | Strict unknown-exam rejection | OPEN |
| Firestore/Storage rules differ across features | Data exposure | Emulator security tests | OPEN |
| Room migration breaks existing installs | Data loss/crash | Migration matrix and historical DB tests | OPEN |
| Ranking duplicate/stale results | Incorrect ranks | Idempotency, transaction tests, reconciliation | OPEN |
| Provider quota/cost abuse | Unexpected cost/service outage | Server-side quotas, rate limits, metering | OPEN |
| AI output is invalid or unsupported | Bad published questions | Schema validation, review, provenance | OPEN |
| CI is flaky or incomplete | Hidden regressions | Inspect failures; require stable release run | OPEN |

---

## 11. Progress Dashboard

Update this section whenever work is completed. Do not mark a phase complete based only on code presence.

| Phase | Scope | Status | Completion evidence / next action |
|---|---|---|---|
| 0 | Full repository/branch/source inventory | IN_PROGRESS | Branch list and Markdown path inventory started; finish full document + source audit |
| 1 | Core foundation/security | NOT_STARTED | Await Phase 0 inventory |
| 2 | Shared models/contracts | NOT_STARTED | Await source/model inventory |
| 3 | Universal AI connector/prompt | PARTIAL | Selected AI plans reviewed; inspect source and tests |
| 4 | Exam registry/syllabus/scoring | PARTIAL | Selected scoring/exam plans reviewed; verify source and official NEET config |
| 5 | PDF/question/admin approval | PARTIAL | Selected PDF/real-question plans reviewed; trace actual pipeline |
| 6 | Student/admin/CBT | NOT_STARTED | Inspect current flows and USI source |
| 7 | Scoring/result/ranking | PARTIAL | Selected reports reviewed; source/CI verification pending |
| 8 | Community/doubt/notifications | PARTIAL | Community plan reviewed; source/rules/tests pending |
| 9 | Integration/release | NOT_STARTED | Blocked until required phases verified |

### Next actions (ordered)

- [ ] A0. Refresh branch HEAD SHAs and save a dated baseline.
- [ ] A1. Complete Markdown content audit for every relevant document in all 12 branches.
- [ ] A2. Complete source-code and test inventory for the shared foundations.
- [ ] A3. Compare all branch diffs and identify unique, reusable commits/files.
- [ ] A4. Create a confirmed issue register with evidence and exact file/symbol locations.
- [ ] A5. Freeze shared API/data contracts and dependency order.
- [ ] A6. Produce the exact file-level change plan and test matrix.
- [ ] A7. Begin implementation from the highest-priority verified root dependency.

---

## 12. Change History

| Date | Change | Evidence |
|---|---|---|
| 2026-11-13 | Initial master root-to-fruit plan created on dedicated integration branch from main baseline | Baseline SHA recorded above |
| 2026-11-13 | Recorded 12 known branches and their initial review responsibilities | GitHub branch listing |
| 2026-11-13 | Added phased dependency plan, branch change register, known audit findings, test gates, and risk register | Initial planning synthesis; detailed verification remains open |

---

## 13. Completion Promise and Scope Control

This tracker is the single source of truth for progress, but it does not imply that the application is already implemented. The full project is too broad to safely deliver as one unreviewed bulk commit. The plan is comprehensive and executed as a sequence of small, testable, reversible batches so that the complete result can be integrated without losing existing work.

**Do not remove a checklist item just because it is difficult.** If a requirement is not needed, mark it `NOT_APPLICABLE` with a reason. If it is blocked, record the blocker and owner. If a feature is deferred beyond the first NEET release, mark it explicitly as post-launch rather than silently dropping it.


---

## 14. Execution Update — 2026-11-13 (Repository Reconciliation Pass 1)

**User authorization:** Proceed with development according to this tracker until the agreed system is complete. Work must be dependency-ordered, reuse existing implementations, and avoid overlapping/duplicate systems. This is not permission to claim unrun tests as passed or to deploy to production.

### 14.1 Refreshed branch comparison against current main

Comparison base: `main` (HEAD `1e236d099522d63ec89ab0d11287b48315efc48c`). GitHub compare API returned:

| Branch | Status vs main | Ahead | Behind | Changed files in compare response | Decision |
|---|---:|---:|---:|---:|---|
| audit/all-exam-scoring-ranking-2026-11-13 | diverged | 42 | 11 | 10 | Inspect and selectively port policy/golden tests |
| feature/community-completion-2026-11-13 | ahead | 41 | 0 | 16 | **Correction:** not identical to main; inspect community UI/backend/rules/tests |
| feature/community-development-plan-2026-11-13 | diverged | 33 | 3 | 9 | Reconcile against community-completion branch to avoid duplicate work |
| feature/ranking-engine-v2-2026-11-06 | diverged | 105 | 106 | 14 | Selectively inspect ranking engine and CI |
| feature/ranking-universal-integration-v1 | diverged | 477 | 106 | 44 | High overlap with scoring/AI/PDF; no wholesale merge |
| feature/universal-exam-scoring-v1-2026-11-06 | diverged | 147 | 106 | 22 | Inspect scoring and Room migration separately |
| feature/universal-gemini-ai-10-phase-2026-11-08 | diverged | 325 | 106 | 56 | Inspect connector/prompt/PDF changes; CI failures must be resolved |
| feature/universal-pdf-prompt-contract-v1 | diverged | 143 | 99 | 46 | Inspect contract and removed files carefully |
| fix/phase1-real-questions-only | diverged | 196 | 106 | 53 | Inspect PDF extraction and fail-closed behavior |
| restore-files/removed-env-gitignore-2026-10-19 | diverged | 2 | 141 | 1 | Documentation-only delta reported; verify actual tracked safety files |
| usi | diverged | 4 | 141 | 3 | Documentation/config delta reported; inspect CBT scope |

**Important correction to Section 2:** the earlier note that `feature/community-completion-2026-11-13` was identical to main was stale/incorrect for this baseline. Current compare reports 41 commits ahead and 16 changed files. Do not mark this branch as no-op.

### 14.2 Initial source verification observations

| Item | Evidence observed | Current conclusion |
|---|---|---|
| Server submission answer-key leak (F-016) | In main `backend_functions/index.js`, `solutionKeys` is loaded in the submission path; a separate `getTestAttemptSolutions` callable exists later and checks authentication, attempt ownership, completion, and review/live-window permissions before returning solutions. | **Not confirmed as a submission response leak.** Continue checking the exact submit return and add/verify tests; do not apply a blind removal that breaks authorized review. |
| Strict numerical comparison (F-004) | Main `backend_functions/examScoring.js` uses `parseFloat` in `areOptionsEquivalent`; divergent scoring branches contain an `isFiniteNumericAnswer` helper using a strict numeric regex and `Number.isFinite`. | **Confirmed candidate defect in main.** Port only the minimal helper/logic and regression tests after reviewing compatibility. |
| Community shared answer privacy | Community-completion branch adds `backend_functions/communityValidation.js` with a public-safe field allowlist and separately extracted private keys. | Promising implementation; inspect all write paths and Firestore rules/tests before porting. |
| AI connector | Gemini branch adds `backend_functions/aiConnector.js` with server-side secret-backed callable verification/generation functions. | Connector exists on that branch; verify provider coverage, auth, quotas, retry, metering, and active Android call sites before treating it as universal. |
| PDF prompt contract | PDF branch adds `UniversalPdfPromptContract.kt` with exam-neutral, fail-closed, private-draft rules. | Useful candidate; reconcile with existing prompt registry and actual pipeline call sites. |
| Generated answer validation | Real-questions branch adds `GeneratedQuestionAnswerValidator.kt` with strict numeric/option validation. | Candidate component; compare against existing validators before adding another implementation. |

### 14.3 Current blockers / next evidence to collect

- [ ] Inspect complete return payload and callable boundaries for `submitTestAttemptServerSide` and `getTestAttemptSolutions`; add regression coverage for both.
- [ ] Compare the strict numeric helper and tests in scoring branches; port one canonical implementation only.
- [ ] Read full community-completion diff and reconcile it with community-development-plan branch.
- [ ] Inspect all Gemini connector functions and their caller paths; establish whether a single canonical connector can be adopted without breaking existing key flows.
- [ ] Inspect PDF pipeline call graph and prompt contract usage; identify duplicates/removals and Room migration impact.
- [ ] Inspect every `generateMockAiTest` call site and failure path before changing fallback behavior.
- [ ] Refresh current CI runs and obtain actionable failure logs for each candidate branch.
- [ ] Update the Phase 0 inventory only after each document/source group has been reviewed.

### 14.4 Verification honesty

The above observations are static GitHub source/compare inspections. No local checkout, Android build, Node test suite, Firebase emulator, physical-device run, or live provider test was executed in this pass. All code changes remain subject to those test gates.


---

## 15. Execution Update — 2026-11-13 (Scoring Security Batch 1)

### 15.1 Completed source changes

**F-004 — permissive numerical parsing: minimal fix committed**

- File: `backend_functions/examScoring.js`
- Change: replaced `parseFloat` prefix acceptance with a strict finite-number validator. Decimal and scientific notation are accepted only when the complete trimmed input matches the numeric grammar. Inputs such as `12abc` and `3.5kg` no longer compare as numeric prefixes.
- Numerical-response comparisons now fail closed when either value is not a complete finite number.
- Regression tests added in `backend_functions/examScoring.test.js` for valid scientific notation, equivalent decimal values, and malformed suffix/prefix inputs.
- Implementation commit: `a5984c2eb5b956893949ef5ec3957b93b050cb88`
- Test commit: `ac353a52ed9cef25e89876cdda5dd48bdc07f1a8`
- Status: `IMPLEMENTED_UNVERIFIED`. GitHub source read-back confirms the helper and test cases are present. The Node test suite was **not run** because this session has static GitHub access only; do not mark VERIFIED yet.

### 15.2 Re-verified findings

| Finding | Current source evidence | Status / action |
|---|---|---|
| F-016 — answer-key leak from submission response | `submitTestAttemptServerSide` loads trusted `solutionKeys` locally for scoring, but the transaction's first-submit and retry response payloads do not include them. The persisted attempt record contains submitted answers and score fields, not the solution-key map. A separate review callable exists for authorized solution retrieval. | **Not confirmed in current integration branch.** Keep the authorized review flow; add/retain regression coverage and inspect the full review callable/rules before closure. |
| F-004 — permissive numeric parsing | `areOptionsEquivalent` previously used `parseFloat`, accepting numeric prefixes. Strict parser and regression cases now committed as above. | `IMPLEMENTED_UNVERIFIED`; requires Node test execution and integration checks. |
| F-023 — shared community test answer leakage | In `NEETViewModel.kt` community-share path, the standardized question object explicitly copies `correctAnswer` and `explanation`; the later public `qMap` also includes both fields. The separate `uploadTestToFirestore` path has a public/private separation helper, but that does not prove the community-share write path is protected. | **Confirmed candidate leak in the community-share payload.** Trace the final Firestore write, backend validation, and rules; then remove private fields from public payload and store them only in the protected answer-key path. Do not assume the separate upload path protects community sharing. |
| F-009 — synthetic/mock AI fallback | Current integration-branch `NEETViewModel.kt` read did not contain the literal `generateMockAiTest`; earlier line references are not sufficient evidence for this branch. | Re-search all source files and call graph before classifying; no change made. |

### 15.3 Next implementation sequence

1. Trace the full community-share function from question serialization through final Firestore write; inspect backend validation and Firestore rules. Implement one safe public/private separation path with tests.
2. Inspect `getTestAttemptSolutions` end-to-end and add a direct test proving submission responses never contain answer keys while authorized review returns them only to the owner/authorized reviewer.
3. Inspect and run the Node scoring tests in an environment with repository dependencies; record actual output. Then refresh CI.
4. Continue Phase 0 source inventory for AI/PDF call graph, Firestore/Storage rules, Room migrations, and ranking idempotency.

### 15.4 Verification boundary

These two code commits were made only on `integration/root-to-fruit-master-2026-11-13`. No production deployment, database migration, branch merge, or main-branch modification occurred. The changes were read back from GitHub, but no Node tests, Android build, Firebase Emulator, device test, or live-provider test was executed. The scoring fix remains `IMPLEMENTED_UNVERIFIED`.


---

## 16. User Requirement — Exactly Two AI Provider Connectors for NEET (2026-11-13)

**User direction:** The NEET release must support two AI provider connectors, and both providers must be usable by the app's AI workflows. Avoid parallel prompt systems, duplicate pipelines, or provider-specific business logic.

### 16.1 Canonical provider decision

The target pair for the first implementation is:

1. **Google Gemini** — reuse the existing Gemini prompt/validation work and the secret-backed Cloud Functions connector found on `feature/universal-gemini-ai-10-phase-2026-11-08`.
2. **Groq** — add the second provider adapter behind the same provider-neutral contract. The user explicitly selected Groq; do not implement OpenAI or add a third provider.

These are two transport/provider adapters, not two separate AI systems. Both must call the same canonical prompt engine, task contracts, exam registry, question schema, validation pipeline, provenance policy, quota/metering layer, and persistence/approval flow.

### 16.2 Required shared interface

- One `AIProvider` contract with provider/model selection and normalized response + usage metadata.
- Exactly two supported provider adapters for this release: `GeminiProvider` and `GroqProvider`.
- One server-side provider router; no provider API secrets in APK, client preferences, Firestore documents, logs, analytics, or crash reports.
- Provider selection must be explicit/configurable; no silent provider or model substitution.
- Both adapters must support the task modes required by NEET: PDF/text understanding (with actual PDF bytes where provider supports it), syllabus/exam metadata extraction, question generation, answer/explanation generation, question validation/repair, bilingual output, and doubt assistance where the task contract permits.
- Shared validation is mandatory after either provider: strict JSON/schema, NEET syllabus scope, source/page provenance, answer correctness checks, duplicate detection, image/diagram references, question type/marking metadata, and fail-closed rejection.
- One Admin kill switch, per-user/server-enforced quotas, provider/model allowlist, timeout/retry policy, token/cost metering, safe error normalization, and audit events.
- Student/Admin authorization must be task-specific. Students may use only approved student-facing tasks and their own authorized quota; Admin-only configuration, publishing, and key verification remain Admin-only.
- If one provider is unavailable, fallback to the other is allowed only when explicitly enabled by policy and logged; do not silently switch model/provider during an active generation or lose provenance.

### 16.3 Existing branch reconciliation — Gemini

The Gemini feature branch contains:
- `backend_functions/aiConnector.js`: v2 callable functions, server-secret-backed Gemini key, Admin-only verify/generate, global AI kill switch, normalized usage metadata.
- `backend_functions/aiConnector.test.js`: unit coverage for auth, kill switch, secret handling, verification, malformed prompts/model IDs, and provider failures.
- `app/src/main/java/Com/ak/testseries/exam/UniversalAIContext.kt`: shared prompt policy (branch version); it is provider-neutral in credential selection but its documentation and task coverage still need reconciliation.
- Branch tracker reports existing student-owned Gemini key flows and AndroidKeyStore protection, but their exact integration/call sites and current-branch compatibility remain to be verified.

**Important compatibility blocker:** Current integration branch uses the existing v1 callable export style and Node 18 Functions package. The Gemini branch connector uses Functions v2 `onCall` and Secret Manager `defineSecret`. Do not copy files without checking deployed Functions generation compatibility, secret binding, exports, region, database ID, and test setup.

### 16.4 Groq implementation gate

The user selected Groq as the second provider. Add it only as the second adapter under the same provider-neutral interface. Use a server-managed secret; never embed a key in Android. Verify current Groq model capabilities and free-tier limits from official documentation before configuring models or quotas. Do not claim native PDF support for models that only accept extracted text or supported image inputs. Tests must mock network calls and assert that secrets never enter client responses, logs, or persisted records.

### 16.5 NEET acceptance tests for both providers

For each provider independently:
- Authenticated Admin can configure/verify and generate allowed test content.
- Authorized student task flows can use the configured provider without receiving secrets.
- Same prompt version, exam configuration, question schema, validation, and approval rules are applied.
- Valid output passes shared validation; malformed JSON, out-of-syllabus content, missing/contradictory answer keys, missing provenance where required, duplicate questions, unsupported types, and incomplete batches fail closed.
- PDF page/source references survive extraction -> generation -> validation -> draft persistence -> Admin review.
- Provider errors, timeouts, quota exhaustion, retries, usage/cost metadata, and explicit fallback are covered.
- Neither provider may auto-publish generated questions or expose private answer keys.

### 16.6 Status and next actions

| Work item | Status |
|---|---|
| Existing Gemini server connector branch identified and source/test inspected | INVENTORY_DONE (compatibility/integration pending) |
| Existing Gemini Android/student-key flow | PARTIAL (exact current call graph and secret storage verification pending) |
| Groq connector | NOT_STARTED (second provider selected; implementation pending) |
| Provider-neutral shared interface/router | NOT_STARTED |
| Both-provider task-mode integration across NEET app | NOT_STARTED |
| Shared validation/quota/cost controls and provider contract tests | NOT_STARTED |
| Real PDF and NEET end-to-end provider tests | NOT_STARTED |

**Implementation order:** reconcile and selectively port the existing Gemini connector and prompt engine; define the shared provider contract; implement Groq adapter; connect all approved NEET task modes through the single router; add security/quota/metering/tests; then validate both providers independently. No API keys are available in this repository inspection, so live provider verification cannot be claimed until securely configured by the project owner.


---

## 17. Execution Update — 2026-11-13 (Community CI Failure Investigation)

### 17.1 Community branch reconciliation refresh

- Community completion branch now compares to this integration baseline as **69 commits ahead / 6 behind**, merge base `1e236d099522d63ec89ab0d11287b48315efc48c`.
- Its current diff includes Community UI, moderation, single-question MCQ UI, backend callables/validation, Firestore and Storage rules/tests, indexes, and CI workflow changes.
- It remains a diverged branch. No wholesale merge is safe or authorized.

### 17.2 Actual CI failures found

**Community Firebase Rules CI run 38**
- Run: `43345891563`
- Commit: `423259356d15b90383ed51bd946be0b5747423cf`
- Result: FAILED before executing the Firestore suite because `firestore.test.js:915` contained invalid JavaScript string escaping (`SyntaxError: Invalid or unexpected token`).
- Storage tests did execute: 4 passed, 1 failed only because the Firestore test module failed to parse.
- Current target branch later changed this test to use `JSON.stringify(...)`, so the exact syntax failure appears corrected in later source. A newer target-branch CI run was still in progress during this review.

**Android CI run 449**
- Run: `43345891614`
- Commit: `423259356d15b90383ed51bd946be0b5747423cf`
- Result: FAILED at Kotlin compilation in `CommunityModerationUI.kt:439`.
- Compiler evidence:
  - lambda type mismatch: inferred `Function1<..., Unit>` but `Function0<Unit>` expected;
  - cannot infer lambda parameter;
  - unresolved references `delete` and `update`.
- Root cause: `commitModerationAction(..., targetWrite, onSuccess)` has `onSuccess` as its last parameter. Kotlin trailing-lambda syntax binds the lambda to `onSuccess`, not `targetWrite`. The target branch still had this call in the latest source read.

### 17.3 Isolated repair submitted

To avoid writing into the other active AI's Community branch while it is changing, created isolated branch:
`fix/community-ci-recovery-2026-11-13`

Changes:
1. `CommunityModerationUI.kt`: changed the moderation call to explicit named arguments and `targetWrite = { batch -> ... }`, so the callback is bound to the correct parameter.
   - Commit: `797b06c8b6669dc7b314f05976b532ff895f19b2`
2. `.github/workflows/community-rules.yml`: added `backend_functions/communityExamSubjects.json` to the push path filters so changes to the canonical subject registry trigger the rules CI.
   - Commit: `a04bf665e73fd12e9d0cde95465c843a5579e1b7`

Opened PR **#7** against `feature/community-completion-2026-11-13`:
https://github.com/abhi952817-sudo/AK-TEST-SERIES-/pull/7

PR status at creation: OPEN, not merged. It is intentionally not merged automatically. The target branch had newer CI runs in progress, so verify PR checks and resolve any new conflicts before merge.

### 17.4 Additional Community audit observations

- Current Community UI has per-user block-list loading and hides blocked authors from the feed tab; Firestore rules isolate block-list ownership.
- Reports are private to Admin in Firestore; Storage report-evidence path restricts reads to reporter/Admin.
- Shared Community tests have a safe-field projection helper and separate private answer-key documents; migration is Admin-only, paginated, dry-run by default, and requires explicit confirmation for writes.
- Community Doubt and replies are intended to remain manual; no AI path should be added there.
- Admin report inbox uses `.limit(100)` and client-side sorting. This is acceptable only as an initial MVP bound; add stable pagination/order and explicit handling for more than 100 reports before broad release.
- The Community moderation screen source inspected here has a suspend action; a complete unban/restore-user flow was not found in that screen. Verify whether existing Admin user management supplies the required unban path before calling moderation complete.
- The Firestore Rules test suite includes subject, PDF rejection, separate reply ownership, report privacy, and block ownership tests. Actual latest green CI evidence is still required.

### 17.5 Verification boundary

- PR #7 code has been written to its isolated fix branch, but its checks have not yet been confirmed green.
- The latest target-branch Community Rules CI and Android CI were still in progress at the time of this tracker update.
- No production deployment, Firestore data migration, merge to main, or destructive action was performed.
- Community work remains `IN_PROGRESS`, not `VERIFIED`.

---

## 18. AI Provider Clarification Required

The earlier proposed OpenAI API adapter is **not approved as the second provider** after the user's correction. Do not implement OpenAI or buy/use a paid API by assumption.

Keep the existing Gemini work as the provider already identified in the repository. The user wants the second connector to use a currently available free-plan AI provider, but its exact name/model has not yet been clearly identified. Ask the user to confirm the provider name before implementing the second adapter. Regardless of provider, both adapters must use one shared provider-neutral prompt/validation/router contract; no duplicate AI business logic.


## 19. User-directed integration constraint (2026-11-13)

The user clarified that Student and Admin should share the same underlying capability wherever the work is the same. Implement the capability once and connect it to both existing flows; only authorization and role-specific actions should differ. Do not create a larger parallel system, duplicate controls, or new control buttons on Student screens. Admin controls (including backup/configuration and YouTube-related administration where already present) must remain at their existing source location. The target is a straightforward, runnable integration that reuses existing code and keeps the current control placement.

## 20. Latest verified work

- Community CI recovery PR #7 was merged into feature/community-completion-2026-11-13 only (not main): merge commit d5212079bede5724defc702e4adaf3e3867b7995.
- PR head 944b5f79808d467eac3cec7e524786a291d84b70 had both Android CI and Community Firebase Rules CI completed successfully before merge.
- Integration branch remains the canonical work branch. Community merge is not yet ported into integration; inspect and selectively reconcile it before any cross-branch integration.
- Current next implementation batch: inventory all Gemini direct-call sites and keys on the integration branch, then adapt the existing Admin-only Gemini connector pattern to the integration branch's Functions v1 callable exports and named Firestore database. Add Groq through the same provider contract; do not add another prompt engine or duplicate Student/Admin UI controls.


## 21. AI connector migration inventory — static source review (2026-11-13)

Integration branch `integration/root-to-fruit-master-2026-11-13` was inspected. This is static GitHub source review only; no Android build, Node tests, emulator, or live provider request was run.

### Confirmed direct Gemini client locations

| File | Confirmed locations | Current behavior / migration note |
|---|---:|---|
| `app/src/main/java/Com/ak/testseries/exam/UniversalPdfPipeline.kt` | lines 56, 144, 185, 232, 482–490 | Accepts optional API key, falls back to `BuildConfig.GEMINI_API_KEY`, and calls Gemini HTTP directly for main generation and targeted regeneration. |
| `app/src/main/java/Com/ak/testseries/utils/GeminiPdfExtractor.kt` | lines 20–23, 33, 81 | Separate PDF extraction client accepts an API key and calls Gemini directly. |
| `app/src/main/java/Com/ak/testseries/viewmodel/NEETViewModel.kt` | lines 1646–1650, 2601, 2946, 3067, 4838, 5213, 5349–5368, 5374–5454, 5515–5519 | Multiple question/text generation call paths; local secondary-key preferences and BuildConfig primary key are present. `GeminiPdfExtractor` is invoked from this ViewModel. |

`NEETViewModel.kt` also contains a YouTube API key use at line 6450; this is a separate YouTube integration and must not be changed as part of the Gemini connector migration. Preserve its current Admin/source-of-control placement and existing behavior unless a separate YouTube audit identifies a defect.

### Backend compatibility constraints confirmed

- Integration `backend_functions/index.js` uses `firebase-functions` v1 callable exports (`functions.https.onCall`) and initializes Admin once, then selects the named Firestore database `FIRESTORE_DATABASE_ID` (default `ai-studio-android-aktestse-4a0dbfd1-96e9-481f-b66c-2984491d0612`).
- `backend_functions/package.json` uses Node 18, `firebase-functions` `^5.0.0`, and `firebase-admin` `^12.0.0`.
- The existing Gemini branch connector uses Functions v2 `onCall` and `defineSecret`; it cannot be copied into the current `index.js` blindly. Any port must preserve the named database, existing callable exports, region/configuration, auth policy, and deploy discovery.
- The integration branch already has `UniversalAIContext.kt` and `UniversalPdfPipeline.kt` uses it. Reuse this prompt policy; do not add a second Prompt Engine.

### Next implementation gates

1. Finish call-site/key inventory, including all `NEETViewModel` helper call paths, Gradle BuildConfig source, and all callers of `GeminiPdfExtractor`.
2. Define one server callable contract compatible with the current Functions setup and named database; Admin controls stay in the existing Admin settings location.
3. Add Gemini and Groq adapters behind one provider-neutral router. Never accept provider secrets from Student/Android callable input. No silent paid fallback.
4. Migrate each existing Android call site to the shared callable without changing student/admin screen control placement; remove client-side Gemini secrets only after every caller is migrated and verified.
5. Add mocked provider, auth, secret isolation, usage/quota, model capability, fallback, and error-path tests before marking the connector verified.


## 22. Student + Admin AI access and per-student quota — clarified requirement (2026-11-13)

### User's clarified target

- The AI feature must be available in both the Student panel and Admin panel.
- Student use is an ordinary running feature: students do not receive provider-control buttons, provider-key controls, routing controls, or Admin configuration UI.
- Admin gets its own control area using the existing Admin/API Management location and existing navigation. Admin can manage provider enablement, primary/default provider, backup/fallback provider, model allowlist, global kill switch, per-student daily generation/test quota, and usage/health visibility. Do not create a duplicate settings dashboard if the existing API Management screen can be reused.
- Both panels use the same two provider adapters (Gemini and Groq), same router, prompt engine, question validation, PDF pipeline, quota/metering, and test persistence. The difference is authorized task access and Admin control privileges, not duplicated AI implementations.
- Student-facing AI flows must use the configured provider policy transparently. If primary provider fails, Groq (or Gemini) may be used only when Admin has enabled that fallback policy; no silent paid/unknown fallback. Students cannot change this policy.

### Existing code found on integration branch (static inspection)

| Existing item | Evidence | Assessment |
|---|---|---|
| Global AI enable/disable | `NEETViewModel.kt` lines 723–724, 741–755, 811–834; Firestore `Config/admin_settings.globalAiEnabled` | Existing partial Admin control; confirm actual Admin UI placement and Firestore rules before relying on it. |
| Daily free generation limit | `NEETViewModel.kt` lines 729–730, 757–805; Firestore `Config/admin_settings.dailyFreeTestLimit`; Admin-only setter checks `isAdmin` | Existing configurable global daily limit, but it is not yet proven to be a secure per-student quota. |
| Student daily generation counter | `NEETViewModel.kt` lines 732–733, 1045–1067; `daily_generation_prefs` stores date/count locally | Local-device counter only; can be reset or bypassed and is not authoritative. Must be moved/enforced atomically on the server per authenticated UID. |
| Student generation gate | `NEETViewModel.kt` lines 4675–4689 | UI/ViewModel checks local count and global AI flag. Client-side check alone is not security enforcement. |
| Existing Admin API Management page | `docs/BRANCH_CODE_RECONCILIATION_AND_INTEGRATION_PLAN.md` section 4.2 records an existing API Management page in the Admin Sidebar on the Gemini feature branch | Reuse/port that page selectively only after refreshing the branch and checking its current source. Do not create a second page. Its presence in the integration branch has not yet been proven. |
| Existing two-provider fallback | Current integration has multiple direct Gemini calls/key fallback; no Groq adapter/router verified | Not implemented as the requested Gemini+Groq shared provider policy. Existing two Gemini keys are not equivalent to two provider connectors. |
| Per-student test assignment / entitlement | Current inspected excerpts show global quota and local generation count; no authoritative per-student assignment/entitlement contract has yet been confirmed | Search existing test-assignment, user-entitlement, ad-unlock, and quota collections/functions before adding schema. |

### Required implementation approach

1. Inspect current Admin Sidebar/API Management UI and the Gemini feature branch's page before creating or porting a screen.
2. Trace current test generation count, ad unlock, any user subscription/entitlement, and test assignment logic end-to-end. Reuse the existing user/test identity and records if they already express the required policy.
3. Implement one server-side atomic quota decision keyed by authenticated UID + date + task type/provider policy; Admin override or exemption must be claim/role checked server-side. Prevent client counter resets, duplicate requests, concurrent overrun, and ad-unlock replay.
4. Keep per-student quota controls in the existing Admin control surface. Student UI shows only remaining availability/limit status where useful; no control affordances.
5. Connect both Admin and Student approved workflows to the same callable/router. Admin-only generation/configuration/publishing operations remain permission-gated; student tasks cannot access private answer keys or unpublished tests.
6. Implement explicit primary/backup provider policy and tests for primary success, fallback allowed, fallback disabled, both unavailable, quota exhaustion, kill switch, and secret isolation.
7. Do not introduce a new test-assignment collection until the existing assignment/entitlement structures and call sites have been fully inspected.

**Current status:** requirement clarified; existing global AI switch and daily limit are partial. Secure per-student quota, shared Gemini+Groq routing, Admin UI verification, and end-to-end Student/Admin wiring remain OPEN. This is static source inspection; no runtime tests were executed.


## 23. Private AI tests + Community card + recipient-only local delivery — full static audit (2026-11-13)

### 23.1 User's clarified target

- Every test generated by an ordinary Student through their own AI/API workflow is private by default and remains in that creator's Private Tests Vault/local app storage.
- Sharing to Community must not publish the full test. At most, a small metadata-only Community card may be visible in the Community feed (subject to the Admin review/approval policy clarified by the user).
- The full question package, correct answers, explanations, and question images must not be readable by all authenticated users or appear in public card documents.
- The creator explicitly chooses which recipients receive access. A recipient who is authorized by the creator can download the package into their own local Room/internal storage and then use it locally. Other users who only see the card cannot fetch the package.
- Admin review of a submitted card/test is a separate approval decision. Admin approval may permit the card/catalog visibility, but it must not automatically make the full test payload globally readable.
- Previous user constraint retained: full private PDF/question payload should remain local; Firebase should hold only minimal card/metadata and fingerprint/ID unless a separately approved transfer design is agreed. Do not quietly turn the full test into a public Firestore/Storage object.
- No ads, paid server ranking, or new payment work in this phase. The current phase is only to make the privacy/share/download boundaries correct.

### 23.2 Confirmed existing implementation

| Area | Evidence on integration branch | Current assessment |
|---|---|---|
| Local test storage | `Entities.kt` defines Room `TestEntity` and `QuestionEntity`; `AppDatabase.kt` has `tests` and `questions` tables; `DatabaseRepository.saveTestWithQuestions()` stores test + questions transactionally. | EXISTING local storage foundation. |
| Private Vault | `PrivateTestsVaultScreen.kt`; filters to owner UID and private tests; `TestVisibilityManager.kt` hides another user's private test in app-level lists/start flow. | PARTIAL; local UI gate exists, not a remote recipient-grant system. |
| Private AI save path | `NEETViewModel.kt` around lines 5965–6003 stores generated question JSON internally and inserts test/questions into Room. When `isPublic=false`, `uploadTestToFirestore()` skips Firestore upload. | PARTIAL; local-only branch exists, but Student generation defaults/auto-routing can still select public. |
| Community card | `CommunityPost` and `communityPostToMap()` contain only card fields such as title, testId, exam, subject, question count, duration, chapter, marks and creator. | GOOD FOUNDATION: card itself is metadata-only. |
| Explicit owner share entry point | `shareTestToCommunity(test)` checks login, ban status and uploader ownership; creates deterministic `TEST_SHARE_<uid>_<testId>` post. | PARTIAL; the post is a card, but its linked payload path is unsafe (below). |
| Recipient local save | `handleDeepLinkTest()` fetches a community test and calls `repository.saveTestWithQuestions()` to save locally before opening. | EXISTING download-to-Room path, but it currently lacks recipient authorization and has an owner-identity mismatch (below). |
| Local download button | `downloadTestOffline()` mainly sets `isDownloaded` and copies already-present `questionsJson` into Room when missing. | This is not the secure remote recipient download flow; deep-link loading does the remote fetch. |
| Existing visibility unit tests | `AuthorizationBoundaryTest.kt` covers public/private owner visibility and under-review/draft visibility. | Useful baseline; no recipient-grant test coverage found. |
| Private test backup | `AccountDataBackupAdapter.filterEligiblePrivateTests()` scopes export to the authenticated owner UID and excludes other users' private tests. Question serialization includes answer/explanation fields in the local backup archive. | Existing local/account-owned backup path; not a Community delivery grant. |

### 23.3 Confirmed high-severity privacy and access defects

**SHARE-001 — Full question payload is written into a broadly readable Community collection (HIGH/CRITICAL).**
- File: `app/src/main/java/Com/ak/testseries/viewmodel/NEETViewModel.kt`, `shareTestToCommunity()`, approximately lines 2201–2441.
- The share projection includes `correctAnswer` and `explanation` in each question map.
- It writes both `questions` and `questionsJson` into `Shared_Community_Tests/{testId}`.
- File: `firestore.rules`, `match /Shared_Community_Tests/{testId}`, line 264: `allow read: if isAuthenticated();`.
- Result: every authenticated user can read the full shared question payload and answer/explanation data, even though the original Room test remains `isPublic=false`. The comment claiming the original test remains private does not protect the separate remote copy.

**SHARE-002 — Community card is metadata-only, but its testId opens the unrestricted full payload (HIGH).**
- File: `CommunityUI.kt`: Community post stores card metadata and its `Attempt Test` button calls `onOpenTest(testId)`.
- File: `MainActivity.kt`: `onOpenTest` routes to `viewModel.handleDeepLinkTest(testId)`.
- File: `NEETViewModel.kt`, `handleDeepLinkTest()`, approximately lines 3432–3437: reads `Shared_Community_Tests/{testId}` directly without an owner-issued recipient grant.
- Result: the card looks like a small public card, but any authenticated user who obtains its testId can load the full test through the linked collection.

**SHARE-003 — No explicit recipient ACL/grant flow found (HIGH).**
- Inspected `firestore.rules`, `storage.rules`, `CommunityUI.kt`, `NEETViewModel.kt` share/deep-link/download paths, and current community rules tests.
- Existing share path verifies the creator, but there is no confirmed owner-managed recipient list, invitation acceptance, per-recipient grant/revoke, expiry, or server-side access check before full package retrieval.
- The existing read rule is global to authenticated users, not recipient-specific.

**SHARE-004 — Recipient copy keeps creator as uploader, so local access checks can reject the recipient (HIGH/FUNCTIONAL).**
- File: `NEETViewModel.kt`, `parseAndValidateSharedCommunityTest()`, approximately lines 3697–3727.
- The downloaded local entity is created with `isPublic=false`, `status="Published"`, and `uploaderId=creatorId`.
- File: `TestVisibilityManager.kt`: a non-public Published test is visible only to its uploader/owner (unless Admin).
- File: `handleDeepLinkTest()`: after saving the recipient's copy, it calls `startTest()`, which applies `TestVisibilityManager`.
- Result: a recipient who is not the original creator may have the package saved locally but fail the local owner check when attempting to start it. A future grant-aware local entitlement must be explicit; do not simply overwrite creator provenance.

**SHARE-005 — Server-side attempt submission also lacks recipient-grant authorization (HIGH).**
- File: `backend_functions/index.js`, attempt submission authorization around lines 200–219.
- Current authorization permits Admin, creator, or a publicly available approved/published test. It has no explicit recipient grant check for a private shared test.
- Therefore the current private-sharing model cannot safely support recipient attempts/ranking even if the client-side start gate were bypassed. Any future recipient authorization must be checked by trusted backend logic too.

**SHARE-006 — Shared question diagrams can be publicly readable (HIGH).**
- File: `storage.rules`, `/tests/{testId}/questions/{fileName}`: `allow read: if true;`.
- `shareTestToCommunity()` calls question-image sanitization/upload before publishing the shared record.
- Result: private question diagrams uploaded through this path may be accessible to anyone with the object URL. A metadata-only card is not enough if referenced assets are public.

**SHARE-007 — Student AI generation is not private-by-default across all routes (HIGH).**
- File: `NEETViewModel.kt`: `genIsPublic` defaults to `true` and `autoShareToCommunity` defaults to `true` (around lines 2006–2007).
- PDF auto-routing sets `genIsPublic = !isQuestionsDetected` and `autoShareToCommunity = !isQuestionsDetected` (around lines 5147–5149); raw-text route can therefore become public/auto-share.
- Final generated test copies `genIsPublic.value` into `TestEntity.isPublic` (around lines 5968–6007), uploads it to Firestore if public, and may auto-share it.
- `TestEntity.isPublic` itself defaults to `true` in `Entities.kt`.
- Result: the existing design does not guarantee that every Student-generated AI test stays private. All Student generation entry points and defaults need to be audited and made fail-closed/private. Admin-authored official publication remains a separate authorized flow.

**SHARE-008 — Community verification badge is not proof of Admin approval (HIGH).**
- File: `parseAndValidateSharedCommunityTest()` sets `isVerifiedCommunity=true` for a downloaded shared record.
- The Community development plan records no complete educational test-verification queue in the current Community Admin UI.
- Result: the verification badge can be inferred from being in the Community collection rather than from an auditable Admin approval record.

**SHARE-009 — Existing tests do not prove Shared_Community_Tests read privacy (MEDIUM/HIGH).**
- File: `firestore.test.js`, existing Shared_Community_Tests test checks creator update/delete/identity immutability, but does not assert that unrelated authenticated users are denied full-payload reads.
- Existing tests cover private `Tests_Data` owner isolation, but that does not protect `Shared_Community_Tests`.
- Add explicit tests for unauthenticated, unrelated authenticated user, owner, Admin, granted recipient, revoked recipient, and expired grant.

### 23.4 Required target data boundary (design only; no implementation in this audit)

1. **Creator vault:** complete test package (questions, correct answers, explanations, source/provenance, and local diagrams) stays in creator-owned Room/internal storage.
2. **Community card:** only small safe metadata and a stable fingerprint/test ID; no question text, options, answers, explanations, PDF bytes, private URLs, local paths, or private Storage tokens.
3. **Admin review:** Admin receives a review reference/card and an authorized review capability. Admin approval is auditable and separate from publication of the small card. Approval must never make the complete private payload public.
4. **Card visibility:** if approved for Community discovery, card may be visible in Community feed. Seeing/bookmarking the card does not grant test access.
5. **Recipient grant:** creator explicitly grants a selected authenticated UID (or an explicitly chosen access scope if later approved). Server checks that grant before returning any package or download capability. Revocation blocks future downloads/attempt submissions; already downloaded local copies need a defined revocation policy.
6. **Recipient local copy:** successful recipient download writes a recipient-owned local entitlement/copy in Room, preserving original creator ID and source fingerprint while recording the recipient UID/grant ID. The recipient can run only while that grant/entitlement policy permits.
7. **Attempts/ranking:** backend validates the recipient grant, test fingerprint/version, and attempt eligibility before accepting submission. A private shared test does not become a public competitive leaderboard test merely because multiple invited recipients attempt it. Ranking eligibility remains a separate explicit setting/policy.
8. **Storage assets:** private diagrams/assets must follow the same access policy; no public-read object path for private test content.
9. **No server-side full private payload:** keep the previously requested metadata/fingerprint-only server design. The recipient package-transfer mechanism must be designed to honor this boundary; do not introduce a permanent Firestore/Storage copy of full private test content without explicit user approval.
10. **No automatic community publishing:** Student AI-generated tests are private regardless of PDF route/source detection. Community card creation must be a separate explicit share action; never auto-share a full payload.
11. **Admin official tests:** Admin-created official/catalog tests may follow the existing Admin approval/publication route, kept separate from student-owned private tests and invited private sharing.

### 23.5 Next investigation/development gates

- Complete call-site inventory for every Student test-generation/save route and every caller that sets `genIsPublic`, `autoShareToCommunity`, `isPublic`, or invokes `uploadTestToFirestore()`.
- Inspect all Firestore/Storage references to shared-test payloads, including any alternate collection names, callable functions, notification/deep-link routes, search/catalog listeners, ranking eligibility and answer-review retrieval.
- Inventory any existing invite, recipient, access-grant, download-ticket, private asset or local entitlement structures before proposing new schema. Do not add a new collection until this search is complete.
- Inspect Admin sidebar/API Management and Community Admin UI for the existing Admin review surface; reuse existing navigation if it exists.
- Decide the secure transfer mechanism consistent with the no-server-full-payload constraint. Document the limitations (for example, recipient must be online/creator must be reachable if transfer is device-to-device) before implementing.
- Add Firestore/Storage/backend/Room tests for card-only visibility, recipient grant/revocation, owner/Admin access, answer-key confidentiality, local copy isolation, account switching, and attempt submission.
- No code or rules were changed in this audit. No Android build, Firebase Emulator, backend test suite, or device test was run. All findings above are static source inspection.

**Audit status:** CONFIRMED STATIC FINDINGS; implementation NOT STARTED. Target branch remains `integration/root-to-fruit-master-2026-11-13`; main unchanged.


### 23.6 Existing architecture document cross-check

- `docs/community/COMMUNITY_DEVELOPMENT_PLAN.md` already states that private test confidentiality is mandatory, that Community should reference the original test by stable ID rather than create an unnecessary second complete question-bank copy, that sharing does not imply Admin verification, and that a complete private test must not be copied into a public collection.
- That same plan records the existing broad `Shared_Community_Tests` read rule as a known answer-bearing-data risk.
- The current `shareTestToCommunity()` implementation conflicts with those documented architecture rules by writing full `questions` and `questionsJson` (including correct answers/explanations) to `Shared_Community_Tests`.
- Local private-file directories are explicitly excluded from Android backup/device-transfer XML rules (`private_tests` and `secure_ai_tests`), and local backup adapters scope test exports by authenticated UID. Keep these protections when implementing recipient-local copies.
- Current Room database is schema version 20 and has explicit migrations through 19→20, but also uses `fallbackToDestructiveMigration()`. Any future recipient-grant/local-entitlement schema change must include a forward migration and schema/restore regression tests; do not rely on destructive fallback.
- Repository-wide code searches for `recipientUid`, `testAccessGrant`, `invitedUsers`, `shareToken`, `testInvite`, and `testPermission` returned no matching implementation. This supports the finding that a recipient grant system was not found, but remains a static search result rather than proof about live Firestore data or external services.
