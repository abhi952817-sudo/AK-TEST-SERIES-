# AK TEST SERIES — App-First Integration Master Plan

**Branch:** `integration/root-to-fruit-master-2026-11-13`  
**Scope:** Android application integration first; existing backend is consumed as-is where available. Backend redesign/completion is deferred to a separately tracked phase.  
**PDF/OCR:** Explicitly excluded from this plan.  
**Status:** PLAN RECORDED — implementation has not yet started.

## 1. Objective

Integrate the existing AK TEST SERIES Android application into one coherent, real-data student and admin experience. Reuse existing screens, ViewModels, repositories, services, Room entities, Firebase paths, and backend callables wherever suitable. Do not create duplicate engines or parallel data flows.

“App-first” does **not** mean pretending missing backend capabilities work. The app must call existing real backend functionality; unavailable or unsafe capabilities must be shown as blocked/unsupported and recorded in the gap register. No dummy records, fake success states, fabricated scores, or simulated rankings.

## 2. Scope boundaries

### Included
- Existing Android Student and Admin panels and their navigation.
- Existing authentication/session and role checks.
- Exam registry, stages, papers, sections, subjects, chapters, and syllabus configuration.
- Existing AI question-generation entry points and provider integration, subject to current backend availability and Admin policy.
- Question editing, validation, approval, and publishing UI flows.
- Student test discovery, attempt UI, timer, answer selection, progress persistence, resume, and submission.
- Result, authorized solutions, and ranking display/integration.
- Private test vault, community cards, and sharing/access UI.
- Room/local storage, existing Firebase synchronization, notifications, error handling, accessibility/usability, and regression checks.

### Explicitly excluded
- PDF extraction and OCR extraction/pipeline implementation.
- Full backend redesign or broad backend feature development in this app-first phase.
- Production deployment, destructive database migration, Firebase project/database cutover, or merge to `main` without explicit approval.

### Backend dependency rule
Use the existing backend interfaces and data contracts. If a required endpoint, permission, schema, or server-side rule does not exist or is unsafe, record it in the Backend Gap Register with evidence and mark the dependent app flow BLOCKED. Do not replace authoritative server behavior with client-side scoring, fake data, or local-only claims of publication/ranking.

## 3. Integration workstreams

| ID | Workstream | Completion target |
|---|---|---|
| APP-01 | Codebase and screen inventory | Map current screens, navigation destinations, ViewModels, repositories, services, and call sites. |
| APP-02 | App architecture and data flow | Reuse one canonical navigation/data path; document ownership of each state and data source. |
| APP-03 | Authentication and sessions | Integrate existing login/register/logout/session restoration and role-gated destinations. |
| APP-04 | Exam registry | Display and consume existing exam definitions without duplicate registries. |
| APP-05 | Stage, paper, and section | Correctly select exam stages/papers/sections using current configuration. |
| APP-06 | Subject and syllabus | Integrate subject/chapter/syllabus selection and empty/error states. |
| APP-07 | AI entry points | Connect Student and Admin UI to the shared existing AI path; no provider keys or provider controls in Student UI. |
| APP-08 | Question management | Integrate real question create/edit/delete/import-existing-supported-source and validation UI. PDF/OCR remains excluded. |
| APP-09 | Admin review and publishing | Connect review/approve/publish controls to actual supported backend operations; no false success. |
| APP-10 | Student test discovery | Show only tests the signed-in user is authorized and eligible to access. |
| APP-11 | Test-taking engine UI | Integrate question navigation, timer, section rules, answer selection, mark-for-review, and palette. |
| APP-12 | Progress, resume, and submit | Persist progress safely, restore the correct attempt, prevent duplicate submission, and expose backend failures honestly. |
| APP-13 | Result and solutions | Display authoritative result data; fetch solutions only when the user is authorized. |
| APP-14 | Ranking and eligibility | Display real ranking only when a supported authoritative ranking record exists; keep eligibility separate from visibility. |
| APP-15 | Private Test Vault | Keep student-created AI test packages private by default; preserve owner boundaries. |
| APP-16 | Community and sharing | Integrate metadata-only community cards and explicit recipient access; never expose full private question packages or answer keys publicly. |
| APP-17 | Admin panel | Integrate existing admin features, user/test management, AI policy controls where present, and role checks. |
| APP-18 | Student panel | Integrate student-facing features without exposing admin/provider configuration controls. |
| APP-19 | Notifications | Connect existing notification flows to real events and handle permission/disabled states. |
| APP-20 | Offline and synchronization | Reuse Room and existing sync paths; define conflict, retry, and account-switch behavior. |
| APP-21 | UI states and error handling | Every key screen handles loading, empty, success, error, retry, offline, and permission-denied states. |
| APP-22 | Regression and final app verification | Test critical flows and verify existing features are not broken by integration. |

## 4. Delivery phases and order

### Phase 0 — Baseline and safety
1. Confirm the exact integration branch head and working tree/commit baseline.
2. Inventory existing screens, symbols, navigation, repositories, Room entities, Firebase paths, and callable functions.
3. Map duplicate/overlapping implementations and select the existing canonical path for each capability.
4. Record all known blockers and backend dependencies.
5. Do not modify Firebase project identity or production data in this phase.

**Exit condition:** inventory and dependency map reviewed; no implementation assumptions left undocumented.

### Phase 1 — App foundation
- Navigation and screen entry points.
- Authentication/session state and role-aware routing.
- Repository/ViewModel ownership and loading/error states.
- Exam registry, stage/paper/section, subject, and syllabus UI integration.

**Exit condition:** real signed-in Student/Admin can reach the correct authorized app areas and browse configured exam structures.

### Phase 2 — Admin and question workflows
- Existing AI entry points connected to shared existing provider path.
- Question management and validation UI.
- Review/approval/publishing UI wired only to real supported operations.
- Admin controls remain Admin-only; Student UI has no provider keys or provider-switch controls.

**Exit condition:** supported Admin workflows complete without fake success; unsupported backend operations are explicitly blocked and listed.

### Phase 3 — Student test experience
- Test list and access checks.
- Start test, question navigation, timer, section constraints, answer selection, review palette.
- Progress persistence, resume, and submission.
- Correct offline/error behavior.

**Exit condition:** a real authorized test can be attempted and submitted through the existing supported backend contract; no client-only substitute is presented as authoritative.

### Phase 4 — Results, solutions, and ranking
- Result screen consumes authoritative attempt/result data.
- Solutions are fetched only through authorized access.
- Ranking uses real eligible attempts and supported ranking data.
- Private/shared tests do not silently become public leaderboard entries.

**Exit condition:** result and ranking UI accurately represents backend state and eligibility.

### Phase 5 — Privacy, vault, community, and admin/student completeness
- Private Vault ownership and account isolation.
- Metadata-only community display.
- Explicit recipient sharing/access where supported.
- Admin and Student feature integration and notification flows.
- Offline synchronization and account-switch safety.

**Exit condition:** private test data and answer keys are not exposed by app flows; sharing and access states are explicit.

### Phase 6 — Regression and release-readiness assessment
- Critical Student/Admin end-to-end flows.
- Navigation and screen regression.
- Room migration compatibility checks.
- Offline/retry/session restoration checks.
- Security and authorization checks at app boundary.
- Build/test evidence and unresolved backend gap report.

**Exit condition:** each workstream is classified accurately; no untested capability is labelled verified. This phase is an assessment, not production deployment.

## 5. Acceptance flows

### Admin flow
`Admin sign-in → Admin dashboard → choose configured exam → create/manage supported test → manage questions → validate → review → approve/publish (only if backend supports it) → verify actual student visibility`

### Student flow
`Student sign-in → exam/test list → authorized test details → start → timer/questions → answer/review → autosave/resume → submit → authoritative result → authorized solutions → eligible ranking`

### Private AI test flow
`Student AI generation → private local vault by default → optional metadata-only community card → explicit recipient access only where secure grant flow exists`

## 6. Non-negotiable engineering rules

1. Inspect before editing; identify the current canonical implementation before adding code.
2. Reuse existing classes and patterns; do not create duplicate engines, registries, repositories, or provider routers.
3. No dummy data, fabricated scores/ranks, mock production behavior, or fake success messages.
4. Do not expose answer keys, explanations, private question packages, tokens, or local file paths in public UI/data.
5. Student and Admin use shared underlying services where appropriate, but controls are role-separated.
6. Server-authoritative operations remain server-authoritative. Do not substitute client-side scoring or authorization.
7. Every code batch is small, reviewable, and reversible.
8. Update the master tracker after each coherent batch with files/symbols, status, evidence, tests, commit SHA, blockers, and next step.
9. Run tests/builds where an appropriate runtime exists. If only static GitHub inspection is available, say so; do not claim runtime verification.
10. No production deploy, destructive migration, force push, branch deletion, or merge to `main` without explicit approval.

## 7. Status vocabulary and evidence

| Status | Meaning |
|---|---|
| NOT STARTED | No implementation work has begun. |
| IMPLEMENTED | Code/UI integration is present, but required verification is incomplete. |
| VERIFIED | Required tests/build/manual flow were actually run and passed; evidence is recorded. |
| BLOCKED | A dependency, missing backend contract, access limitation, or safety issue prevents completion. |
| UNVERIFIED | Evidence is insufficient to confirm behavior. |
| NOT APPLICABLE | The workstream does not apply to the current app path, with reason recorded. |

A code search or successful static readback alone cannot establish end-to-end runtime verification.

## 8. Backend Gap Register

For every backend dependency discovered, record:
- Gap ID.
- Dependent app workstream and user flow.
- Expected contract/behavior.
- Existing callable/collection/path/symbol inspected.
- What is missing or unsafe.
- Severity and impact.
- Evidence and verification limits.
- Safe app-side behavior while blocked.
- Later backend phase/remediation recommendation.

The app-first phase may document backend gaps and wire existing supported contracts, but it must not silently expand into backend redesign.

## 9. First implementation batch

Start with **Phase 0 — Baseline and safety**:
1. Reconfirm integration branch HEAD.
2. Inventory app navigation and Student/Admin screen entry points.
3. Inventory existing ViewModels, repositories, Room data, Firebase data sources, and backend call sites.
4. Map the 22 workstreams to exact files/symbols and current status.
5. Identify canonical implementation paths and duplicate candidates.
6. Record backend dependencies and blockers.
7. Update `docs/MASTER_ROOT_TO_FRUIT_DEVELOPMENT_TRACKER.md` before starting code edits.

**Current status:** PLAN RECORDED. Phase 0 audit and all implementation work remain NOT STARTED in this plan. No app code, backend code, Firebase data, rules, or production configuration was changed by recording this plan.
