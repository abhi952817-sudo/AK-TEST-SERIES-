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

### COM-SHARE-001 — Test Verification Card is missing

Evidence:
- `NEETViewModel.shareTestToCommunity(test)` currently prepares and writes `Shared_Community_Tests` and `Community_Posts` directly.
- `CommunityShareManager.buildTestSharePost` silently chooses the test subject when it matches the registry, otherwise it substitutes `Full Syllabus`.
- The share flow does not ask the student to select the subject in a verification card.
- The code does not yet prove that the selected subject matches the actual subject distribution in the test's questions.

Required:
1. Open a verification card before any share write.
2. Show the canonical exam and only registry-approved subjects that are actually represented in the test.
3. Permit `Full Syllabus` only when the question set covers the exam's registered subject set.
4. Validate test owner, test status, question count, duration, stage/paper/section and subject distribution before publishing.
5. Keep answer keys/explanations out of the public Community card and preserve the original test's privacy/ranking flags.
6. Unknown or incomplete registry/pattern data must stop sharing with a clear error; no NEET or Full Syllabus fallback.

### COM-SHARE-002 — Automatic Community sharing bypasses the verification flow

Evidence:
- `NEETViewModel.autoShareToCommunity` currently defaults to `true`.
- Test-save logic invokes `shareTestToCommunity(newTest)` automatically when the flag is true.
- The public auto-workflow intent can set this flag to true.

Required:
- Disable automatic publishing until the verification card is completed.
- A public/private test choice must not silently imply Community publication.
- Community sharing must happen only after the student confirms the verification card.

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
