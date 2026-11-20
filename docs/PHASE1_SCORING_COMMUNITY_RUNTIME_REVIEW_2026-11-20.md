# Phase 1 — Scoring and Community Runtime Integration Review
Date: 2026-11-20
Repository: abhi952817-sudo/AK-TEST-SERIES-
Review base: integration/ak-test-series-complete-2026-11-20
Scope: read-only semantic review of PR #5 (shared scoring contract), PR #8 (Community MVP), and PR #12 (Community/default Firestore composite).

## Safety boundary
This report adds documentation only. No application source, Firestore rules, Storage rules, Firebase configuration, secrets, production data, database target, or deployment was changed. Main remains untouched. PRs #5, #8, and #12 remain Draft and are not merged by this review.

## Executive disposition
- PR #5: preserve the draft contract and fixtures as design material only. Do not merge wholesale or enable scoring/ranking. The contract is not runtime wired and its tests do not yet prove contract resolution parity.
- PR #8: useful Community implementation candidate, but not safe to integrate as a complete feature until shared-test publishing is moved behind trusted server validation and the callable/rules behavior is tested against the final data model.
- PR #12: do not integrate wholesale. It combines Community changes with Firestore default-database, analytics, backup, runtime initialization and file uploader changes. Database selection remains an owner-verified production decision.

## SCORING-001 — Shared contract is not an executable runtime contract
Severity: Release blocker for universal scoring.

Evidence:
- `shared/scoring/scoring-policy.v1.json` declares `status: DRAFT_NOT_RUNTIME_WIRED`; every exam policy has scoring/ranking disabled.
- The declared policy resolution key is `examId + examYear + stageId + paperId + sectionId + questionType`.
- The Android golden-vector test constructs `QuestionEntity` values and calls `ExamScoringEngine.evaluateTestAttempt(targetExam, ...)`. It does not resolve the policy by exam year, stage, paper, section and question type.
- The backend golden-vector test similarly calls `evaluateTestAttempt(examId, ...)`; it does not execute a shared policy resolver.
- The seven fixtures therefore show selected examples against existing engine behavior, not full Kotlin/Node parity for the draft contract.

Required next work:
1. Define a typed policy resolver in Kotlin and Node using the complete resolution key.
2. Reject missing/ambiguous exam-year-stage-paper-section-question-type mappings.
3. Define explicit handling for dropped/invalid keys, multiple-select, numerical tolerance, optional sections, qualifying papers and dynamic question-level marks.
4. Run the same fixture document through both runtime adapters and compare all persisted score fields.
5. Keep all exam policies disabled until each exam-year-stage-paper mapping has authoritative source provenance and complete golden coverage.

## SCORING-002 — Contract metadata has a misleading CI diagnostic
Severity: Low, documentation/CI clarity.

Evidence:
- The audit workflow checks `fixtures.length !== 7`, but its thrown message says `Expected 6 initial golden fixtures`.
- The Android test's expected count is seven.

Required next work: make the validation message and fixture inventory agree. This does not change scoring behavior.

## COMMUNITY-001 — Shared-test verification is client-enforced, not server-authoritative
Severity: Release blocker for the requested Test Verification Card.

Evidence:
- PR #8 adds the Android `CommunityTestVerificationDialog` and validates the chosen subject in the client/ViewModel.
- PR #8's Cloud Functions include MCQ, reply, delete, migration and subject-registry operations, but no trusted callable that accepts a test-share request and independently verifies the source test, owner, exam/stage/paper, question count, and actual question-subject distribution.
- The Community share flow still builds a `TEST_SHARE` card in Android. Firestore rules validate the registered exam/subject pair and allowed fields, but cannot prove that the selected subject matches the underlying test questions.
- PR #12's rules permit a student to create a new shared-test parent with `answerKeySeparated == true` and then write safe question projections and a private key under that parent. That protects answer keys from other students, but does not establish that the parent represents an authentic owned test or that its subject metadata matches the questions.

Required next work:
1. Implement a trusted callable for share creation.
2. Load the source test and question records server-side; verify ownership and publication eligibility.
3. Derive/validate exam, stage, paper, subject distribution, question count, duration and scoring metadata from authoritative source records.
4. Write the public card, safe question projection and private answer key atomically or with a recoverable idempotent workflow.
5. Deny direct student writes to the shared-test parent, question and private-key paths once the callable path is in place.
6. Preserve user consent: manual share only; no automatic Community publishing.

## COMMUNITY-002 — Subject registry synchronization needs production target reconciliation
Severity: Deployment blocker.

Evidence:
- PR #8 uses a packaged `communityExamSubjects.json` and includes an Admin synchronization callable.
- PR #12 and the Community audit refer to different Firestore database target choices across branches. A successful emulator test does not prove the app and Functions point to the same production database.
- PR #12 also contains broad Firestore helper/default-database and backup/runtime changes beyond Community.

Required next work:
1. Confirm the actual production Firebase project and database ID with the owner.
2. Confirm Android, Functions, Firestore Rules, Storage Rules, indexes, backup/restore and emulator configuration all target that same database.
3. Only then run registry sync in dry-run mode and inspect stale mappings. No production sync/deploy is authorized by this review.

## COMMUNITY-003 — Preserve the requested product constraints
The PR #8 implementation contains useful pieces that should be retained after server validation:
- Doubt posts use manual text replies; the inspected Community callable does not route doubt/replies to AI.
- The MCQ validator accepts exactly one question and four or five unique options, with no timer field.
- Doubt highlight spans are bounded.
- Community image paths are scoped to the authenticated creator and Storage rules restrict image MIME/size.
- The UI's current single-image composer and no-PDF behavior must remain intact.
- Shared-test public projections must never contain correct answers or explanations.

Acceptance tests still required on the final integration head:
- A modified client cannot publish a fake test card or forge the subject mapping.
- A student cannot directly create or alter shared-test parent/question/private-key records.
- A shared card contains no answer key or explanation.
- Doubt creation accepts text with at most one image, rejects PDF and empty text, and replies remain manual.
- MCQ creation rejects anything other than one question with four/five unique options and no timer.
- Creator/admin delete, ownership, moderation, blocking, report evidence cleanup, and counter atomicity are tested.
- Android unit tests, Debug APK build, backend tests, Firestore Rules emulator tests and Storage Rules emulator tests pass on the exact final source head.

## Integration decision
No source cherry-pick is approved by this review. The next safe implementation unit is a new isolated Community server-share callable and its tests, developed on a fresh branch from the current integration branch, after confirming the source test data model and callable deployment region/configuration. Keep Main, production Firebase, and existing data unchanged.
