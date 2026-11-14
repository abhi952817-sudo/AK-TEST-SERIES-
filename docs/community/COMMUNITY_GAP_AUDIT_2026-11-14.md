# Community Gap Audit — 2026-11-14

Repository: `abhi952817-sudo/AK-TEST-SERIES-`
Audited branch: `feature/community-completion-2026-11-13`
Audited PR: #8
Audited head: `71b165e2a1f9a1c6e507c8384f3ba981b67c449f`
Base: `main` at `1e236d099522d63ec89ab0d11287b48315efc48c`

## Verified current status

- PR #8 is Open, Draft, and not merged.
- Latest PR-head Android CI run #547 completed successfully.
- Latest PR-head Community Firebase Rules CI run #105 completed successfully.
- These checks prove the tested code and emulator contracts at this commit; they do not prove production Functions/Rules deployment or physical-device QA.
- No production Firebase deployment, migration, backup, or live data modification was performed during this audit.

## Confirmed implemented behavior

- Community post composer supports Discussion, Doubt, and one-question MCQ.
- Student post subject is required and selected from the canonical exam registry.
- Community post attachments are limited to one JPEG/PNG/WebP image, maximum 5 MB; no Community PDF upload control/path is present in the current composer.
- Doubt replies are manual text replies through the Community reply flow. The Community doubt/reply code inspected here does not call an AI endpoint.
- MCQ composer supports exactly 4 or 5 unique options and one correct option; no timer or multi-question set is implemented.
- MCQ creation uses a callable and answer reveal follows a server-recorded attempt.
- Report dialog supports one optional camera/gallery evidence image, with a 5 MB client-side size check.
- Admin UI includes Reports, Audit and Migrations tabs.

## Release-blocking gaps found

### COM-SHARE-001 — Explicit Test Verification Card

Status: UI and Android-side validation implemented in the latest follow-up commits; current-head Android CI is pending.

Evidence:
- A new `CommunityTestVerificationDialog` displays exam/test metadata and requires a subject selection before sharing.
- Subject choices are derived from question-level subject metadata where available; `Full Syllabus` is offered only when the visible question data covers every registered exam subject.
- `NEETViewModel.shareTestToCommunity` rechecks the selected subject against the full question list loaded from the local test/Room fallback before writing.
- `CommunityShareManager.buildTestSharePost` no longer silently substitutes `Full Syllabus` for an unrecognized subject.

Remaining:
- The question/subject verification currently runs in the Android client. Firestore Rules enforce canonical exam/subject registration, but they do not independently prove that the selected subject matches the actual question documents. A modified client could bypass the UI check.
- Move the share write behind a trusted callable or otherwise add server-side validation before treating this as a security-complete verification flow.
- Keep answer keys/explanations out of the public Community card and preserve the original test's privacy/ranking flags.
- Unknown or incomplete registry/pattern data must stop sharing with a clear error; no NEET or Full Syllabus fallback.

### COM-SHARE-002 — Automatic Community sharing

Status: Code change applied; Android CI pending.

- `autoShareToCommunity` now defaults to `false`.
- The public auto-workflow intent explicitly keeps Community auto-sharing disabled.
- Manual share entry points now open the Test Verification Card because the share method requires a selected subject before proceeding.
- Keep automatic publication disabled unless a separate, explicit consent flow is designed and tested.



### COM-SHARE-003 — Server-side subject verification

Status: NOT IMPLEMENTED; release blocker.

The latest change validates the subject in Android before the client writes the shared-test documents. Firestore Rules validate canonical exam/subject registration but cannot prove the subject distribution of the underlying question set. A modified client can bypass the UI validation. Implement a trusted callable that validates the submitted test/question structure, ownership, exam/stage/paper, question count, duration and subject distribution before creating the public card and protected answer-key documents. Keep direct client writes from bypassing this validation.

### COM-DOUBT-001 — Highlighted text is not implemented

Evidence:
- `CreatePostScreen` stores the doubt body as a plain `String` in a basic `OutlinedTextField`.
- No selection/highlight formatting control or persisted highlighted-span representation was found in the inspected Community composer.

Required:
- Add a simple, accessible way to select a portion of the doubt text and mark it as highlighted.
- Persist only a safe, bounded text/span representation; do not accept arbitrary HTML.
- Render highlighted spans consistently in the feed and post detail.
- Keep normal text entry, one optional image, and manual replies working.
- Do not add AI calls or AI-generated content.

### COM-REPORT-001 — Report evidence failure cleanup needs verification

Evidence:
- Report UI uploads the evidence image first and then writes the report document.
- Verify that a Firestore report-write failure removes the newly uploaded evidence object, and that retry does not create orphaned files or duplicate reports.

Required:
- Add cleanup on report-write failure and tests for image upload/report write failure.
- Preserve private evidence access for the reporter and authorized Admin only.

## Cross-PR database dependency

PR #8 is based on Main and its Cloud Functions initialize Firestore with named database ID `ai-studio-android-aktestse-4a0dbfd1-96e9-481f-b66c-2984491d0612`.
PR #10 proposes standardizing the app, Firebase CLI and Functions on `(default)`, but PR #10 is still Draft and unmerged.

Before any Community Functions deployment or merge, reconcile both PRs to one confirmed database target. Do not deploy one side while the app and Functions point at different databases. Do not delete or migrate any database as part of this code work.

## Safe implementation order

1. COM-SHARE-002: stop automatic Community publication.
2. COM-SHARE-001: implement the explicit test verification card and actual question/subject validation.
3. COM-DOUBT-001: implement bounded highlighted text and renderer.
4. COM-REPORT-001: close evidence-upload cleanup and retry gaps.
5. Re-run Android unit tests, Debug APK build, Firestore/Storage emulator tests and backend tests on the exact final PR head.
6. Review the PR diff against Main and reconcile Firestore database target with PR #10.
7. Keep PR #8 Draft until all checks and separate device/deployment approvals are complete.

## Safety boundary

No merge, deployment, production data write, migration, export, import, or cleanup is authorized by this audit. Preserve Main and all existing student data.
