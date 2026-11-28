# Community Share Flow — Exact Call-Site Findings
Date: 2026-11-20
Branch reviewed: feature/community-completion-2026-11-13
File reviewed: app/src/main/java/Com/ak/testseries/viewmodel/NEETViewModel.kt
Purpose: supplement the Phase 1 runtime integration review with exact call-site behavior.

## Confirmed flow
The current `shareTestToCommunity` Android ViewModel method:
1. Checks current authentication, local banned-state, and `test.uploaderId == currentUid`.
2. Reads question JSON from the TestEntity, Room repository, or a local JSON file.
3. Computes available subjects on-device and shows the verification dialog.
4. Builds a sanitized question projection and a separate list of private answer keys on-device.
5. Writes `Shared_Community_Tests/{testId}`, its `questions` subcollection, and `private_keys/answerKey` directly from the Android client in one Firestore batch.
6. Writes `Community_Posts/{postId}` directly from Android in a separate write.
7. Sends a notification directly from Android after the post write.

## Additional findings

### SHARE-001 — Question subject verification is client-controlled
The owner check, source question loading and selected-subject validation all run in Android. A modified client can bypass them and submit arbitrary shared metadata. Firestore Rules validate the selected subject against the canonical registry, not against the source test's actual question records.

**Required:** server callable must load the canonical source test and question records and derive subject distribution itself. The callable must not trust a client-provided question list or selected subject as proof.

### SHARE-002 — Generic marking fallback can fabricate question marks
During share preparation, the client calls `getMarkingSchemeForExam` and falls back to `Pair(4.0, 1.0)` on error. For exams with multiple marking schemes, it calls `getMarkingSchemeForQuestion` and again falls back to the generic pair on error. The share payload then persists those values as `positiveMarks` and `negativeMarks`.

**Required:** remove generic +4/-1 fallbacks. Resolve exact exam/year/stage/paper/section/question-type policy; if unresolved, stop sharing with an actionable error. A Community share must not manufacture scoring metadata.

### SHARE-003 — Full Syllabus inference can be overclaimed from incomplete metadata
The client adds `Full Syllabus` when every registered subject appears at least once in the question data. Presence of each subject once does not prove complete syllabus coverage, nor does it prove that all questions are validly mapped to the exam's official pattern.

**Required:** only permit Full Syllabus when a trusted, versioned coverage contract proves all required subjects/sections and the source test is a complete-syllabus test. Otherwise allow only the actual validated subject labels or block sharing.

### SHARE-004 — Publication is split across independent writes
The shared-test batch is committed first, then the Community card is written, then a notification is written. Failure between these operations can leave orphaned shared-test documents or missing notifications. Client-side duplicate cleanup is best-effort and not atomic.

**Required:** server-side idempotent callable with a deterministic request key. Persist the shared-test records and Community card as one trusted operation; make notifications an outbox/retry operation or explicitly non-critical. Do not delete legacy duplicates as part of ordinary user share flow without a separate verified migration.

## Required implementation boundary
- No direct student writes to `Shared_Community_Tests` parent, `questions`, or `private_keys` after server-mediated sharing is implemented.
- No client-side mark defaults or guessed question metadata.
- No AI call in doubt creation, reply, moderation, or report flow.
- Keep one image maximum and no PDF for Community doubts.
- Keep manual share consent; no automatic sharing.
- Keep original source test private and unchanged.
- Preserve answer-key confidentiality in public question projections.

No source files were changed by this review. No production Firebase operation, database migration, deployment, or Main merge was performed.
