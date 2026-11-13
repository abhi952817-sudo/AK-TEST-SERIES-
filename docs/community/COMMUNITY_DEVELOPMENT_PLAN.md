# AK Test Series Community Development Plan

> **Document status:** Planning baseline  
> **Plan branch:** `feature/community-development-plan-2026-11-13`  
> **Base branch:** `main`  
> **Scope:** Community feature only. This document is a work plan, not authorization to merge or modify Main.  
> **Last reviewed:** 2026-11-13

## 0. First-priority MVP scope (decision recorded 2026-11-13)

This section is the current product decision and takes precedence over any broader or older feature ideas elsewhere in this planning document. Build the smallest secure, usable Community first. Do not expand scope until this MVP is implemented and tested.

### MVP user experience

1. A student must select a valid subject before publishing. Subject choices must come from the selected exam's canonical registry mapping. Do not offer a generic fallback subject or allow an unclassified post.
2. A post may contain text only, one image only, or text plus one image. Maximum one image per post. Camera capture and gallery selection are allowed.
3. Community uploads must never accept PDF files, now or in future Community post/report flows. Remove PDF attachment controls from Community UI and stop writing Community `pdfUrl` / `pdfFileName` fields. Existing official/admin test-PDF workflows outside Community are out of scope and must not be broken.
4. A Doubt is one question per post. It supports text and optionally one image. Other students can send manual text replies. No AI, AI fallback, AI summary, AI suggestion, or AI-generated answer in any Doubt path.
5. An MCQ/quiz post contains exactly one question, not a multi-question quiz set. The question can contain text and optionally one image. It has either four or five options, selected by the creator, and one correct option for the MVP. Timer is not required and remains deferred.
6. Subject selection is categorization only. Do not require manual Admin approval or claim academic verification for every student post or MCQ. Publish immediately after authenticated user, subject, content, image and policy validation succeeds.
7. The author can delete their own post immediately. Users can report posts/users and block users. A report may include text and at most one image as private evidence visible only to authorized moderators.
8. Basic Telegram-like interaction means a chronological feed, post detail, text replies, and basic reactions where already supported. Do not build real-time chat, voice/video, typing indicators, or group messaging in the MVP.
9. Admin MVP controls: view reports, hide/remove reported content, suspend/unban users, and review moderation actions. Normal posts are not placed in a manual pre-publication approval queue.
10. Preserve Admin-created and Student-created quiz ownership metadata in the data contract, but the first implementation milestone only needs the single-question MCQ post flow. Full separate quiz libraries, quiz collections, leaderboards and advanced quiz management are later work.

### MVP feature order

- **P0-A: Read-only audit and security baseline.** Inspect current Community UI, models, upload paths, Firestore rules, report/block implementation, admin controls, call sites and tests. Record exact files and risks before edits.
- **P0-B: Subject-gated text/image post.** Mandatory registry-backed subject; text and/or exactly one image; camera/gallery; no PDF; immediate publish without per-post Admin approval.
- **P0-C: Feed and manual replies.** Feed, post detail, text replies, stable pagination if needed, ownership checks and immediate author deletion.
- **P0-D: Single-question MCQ.** One question per post, optional one image, exactly four or five options, one correct option, answer reveal only after a student's attempt. No timer or multi-question sets.
- **P0-E: Report/block and Admin moderation.** Report post/user with optional private single-image evidence; block user; Admin report inbox, hide/remove, suspend/unban and audit log.
- **P0-F: Security and regression.** Server-authoritative authorization, storage validation, no PDF upload route, no answer-key leakage, no fake content, and tests for all MVP permissions and flows.

### Explicitly deferred

PDF attachments in Community (permanently disallowed); multi-question quizzes; quiz library/collections; timers; multi-select MCQs; AI Doubt features; voice/video/live chat; advanced leaderboard/rewards; elaborate animations; advanced analytics; broad notification automation; and any feature not required for P0-A through P0-F.

### MVP acceptance checklist

- [ ] Subject is mandatory and validated against the selected exam's actual registry mapping.
- [ ] A student can publish text only, one image only, or text plus one image.
- [ ] The Community UI, model write path and Storage rules reject PDF uploads; no new Community PDF URL is written.
- [ ] A Doubt contains one question and supports manual text replies only.
- [ ] An MCQ post contains one question and four or five options, with one correct option.
- [ ] Student posts publish without manual pre-approval after server-side validation.
- [ ] The author can delete their own post; another student cannot delete it.
- [ ] Reports can include one private image; ordinary students cannot read report evidence.
- [ ] Blocked/suspended users cannot bypass restrictions through direct Firestore or Storage calls.
- [ ] Admin can review reports, hide/remove content, suspend/unban users and produce an audit record.
- [ ] No AI service is called from Doubt or reply flows.
- [ ] No fake/demo posts, users, answers, reactions or quiz data are introduced.
- [ ] Relevant Firestore/Storage tests and Android build/tests run successfully; exact results are recorded.

## 0.1 First-priority implementation audit findings

Read-only inspection of the current Community code on this branch confirms the following. These are concrete implementation targets, not claims that the MVP is already implemented.

| File | Confirmed current state | Required first-priority change |
|---|---|---|
| `app/src/main/java/Com/ak/testseries/ui/CommunityUI.kt` | `CommunityPost` still has `pdfUrl` and `pdfFileName`; serializer writes them and parser reads them. Create screen defaults subject to `General`, appends `General` to registry subjects, requires both title and content, and has no image picker/upload. | Remove PDF fields from new Community writes/UI; do not create any PDF upload action. Require a real registry subject. Support text and/or one image, including image-only where policy permits. Add upload state and image preview/removal. Add explicit DISCUSSION/DOUBT/MCQ post kinds without multi-question sets. |
| `CommunityUI.kt` feed/reply operations | UI directly reads/writes Firestore. Replies are embedded in `commentsList`; student updates can rewrite the entire list and comment counter. | Keep UI thin; move sensitive writes to a controlled repository/backend path. Use separately owned reply records or another concurrency-safe contract. Do not let a student overwrite other students' replies or counters. |
| `CommunityUI.kt` delete/detail actions | Delete helper calls document delete; visible card delete control is currently Admin-only. | Expose delete for the post owner and Admin, with confirmation and rules enforcing ownership. |
| `app/src/main/java/Com/ak/testseries/ui/CommunityAdminUI.kt` | Existing settings/users/manage-posts UI exists; no report inbox or report-evidence review flow was found. | Add a small Admin report inbox, hide/remove action, suspend/unban and moderation audit. Do not add a manual approval queue for ordinary posts. |
| `firestore.rules` | Student create rule does not require a valid subject or constrain Community post types/content/image count. Other students may update `upvotes`, `views`, `comments` and the full `commentsList`. `Shared_Community_Tests` is separately readable by all authenticated users. | Add exact post field/type/subject/content constraints; protect answer-bearing shared-test data as a separate security task; prohibit client-side counter/list overwrites; add owner-created report and block data rules. |
| `storage.rules` | No Community-specific Storage path exists. Existing default rule is Admin-only. | Add a dedicated image-only Community path with authenticated UID ownership, strict MIME/size limits, safe read policy and owner/Admin deletion. No PDF content type or Community PDF path. |
| `firestore.test.js` | Current tests explicitly expect any student to update another user's `commentsList` and comment count. Post create tests omit mandatory subject. | Replace those expectations with per-reply ownership and mandatory-subject tests; add tests for 1-image limit, PDF rejection, report privacy, block ownership, owner delete and suspended-user denial. |
| `app/src/main/java/Com/ak/testseries/utils/FileUploaderUtil.kt` | Existing generic image helper uploads under `chart_images/` and falls back to a local cache URI if Firebase Storage fails. | Do not reuse this helper for Community. Community must fail the publish operation if its remote image upload fails; a local cache URI must never be saved as a public Community attachment. |

### Safe implementation order after this audit

1. Define exact Firestore/Storage contracts and backward compatibility before changing rules.
2. Add and test the Community image Storage path and report-evidence privacy.
3. Refactor Community create-post state and UI for mandatory subject, text/one-image, and no PDF.
4. Move replies to a per-reply ownership model and add author delete.
5. Add one-question MCQ fields and validation.
6. Add report/block flows and the minimal Admin report inbox.
7. Run Firestore emulator tests, Android unit tests and debug APK build. If any command cannot be run in the available environment, record it as NOT RUN rather than PASS.

## 0.2 Implementation progress (2026-11-13)

Work is being implemented on this feature branch only. This progress table supersedes any assumption that a planned item is complete merely because it appears in the scope above.

| Work item | Current state | Evidence / remaining work |
|---|---|---|
| Read-only baseline audit | **Completed** | Community UI, Admin UI, Firestore rules, Storage rules, registry, and existing Firestore tests inspected. |
| Mandatory registry subject | **Implemented in branch; CI pending** | Community composer and feed now use `getRegisteredSubjectsForExamName`, which returns no generic fallback. Firestore rejects blank and `General` subjects. |
| Community PDF prohibition | **Implemented in branch; CI pending** | PDF fields removed from the Community Kotlin model, parser and new writes; Firestore rejects `pdfUrl` / `pdfFileName`. Other Admin/test PDF workflows remain intact. |
| One-image post | **Camera/gallery flow implemented; device validation pending** | One selected JPEG/PNG/WebP image, maximum 5 MB, preview/remove, Storage upload and post image rendering. Upload failure blocks publishing. Camera uses Android preview capture and saves a temporary JPEG before upload. |
| Safe replies | **Implemented in branch; CI pending** | Replies now use `Community_Posts/{postId}/Comments/{commentId}`; reply creator is recorded and only its author/Admin can delete. Shared parent-array writes and client counter mutation removed. |
| Community Storage paths | **Rules + emulator tests implemented; CI pending** | `community_images/{uid}/{fileName}` is owner-write, authenticated-read, image-only, max 5 MB. Private report-evidence path is Admin/reporter-read and image-only. `storage.test.js` and a dedicated Community Rules CI workflow now exercise owner-path, PDF/MIME rejection and private-evidence access. |
| Report/block Firestore security | **Rules scaffolding implemented; UI pending** | Private `Community_Reports` records and owner-managed `Blocked_Users` records have rules and tests authored. Report form, optional private evidence upload, block controls and Admin report inbox are not yet implemented. |
| Single-question MCQ | **Not implemented** | One question, 4/5 options and secure answer reveal remain pending. Do not treat this PR as delivering quiz creation/attempt scoring. |
| Automated validation | **Known blockers; rerun in progress** | Android CI compiles CommunityUI and completes Kotlin compilation, then fails on the pre-existing `CrashResilienceTest > classMethod` (`UnsupportedOperationException` at `DefaultSdkProvider.java:170`). The same failure exists on the current Main baseline (run 384), so it is not introduced by this Community change. The first Firebase Rules CI attempt failed before tests because latest firebase-tools requires Java 21; workflow was corrected to Node 24 + Java 21 and rerun is in progress. Rules tests have not passed yet. Do not merge until the corrected rules workflow passes and Android baseline failure is explicitly handled/accepted. |

### Implementation commits

- Registry-only Community subject lookup and subject-gated composer.
- Community PDF model/write removal and Firestore rejection.
- Dedicated image Storage paths.
- Per-reply documents and ownership enforcement.
- Firestore security regression test updates.

### Merge gate

This branch is not ready to merge while Android CI is running or Firestore/Storage rules are unvalidated. The pull request is draft. Merge only after reviewing the final diff, passing Android unit tests/build, validating Firestore/Storage rules, and confirming no unrelated Main changes are included.

## 1. Purpose and working rules

This document is the implementation guide for anyone assigned to develop the Community module. Read it before changing code.

### Non-negotiable product rules

1. **No AI in Doubts.** Doubt creation, answers, replies, best-answer selection, and resolution are student/manual workflows only. Do not call Gemini, Groq, Cerebras, an AI connector, an LLM endpoint, or an AI fallback from any doubt path. Do not add AI-generated answers, summaries, suggestions, or auto-replies.
2. **Do not damage existing exam/test systems.** Preserve test generation, private test vault, test attempts, scoring, ranking, leaderboard, exam registry, and existing student data.
3. **No fake content.** No dummy posts, fake students, fake likes, fake comments, placeholder answers, fabricated verification, or offline-generated fallback posts.
4. **Private test confidentiality is mandatory.** Never expose correct answers, explanations, marking data, private test questions, or private storage URLs to an unauthorized student.
5. **Use canonical test identity where possible.** Community should reference the original test by stable ID and must not create an unnecessary second copy of the complete question bank.
6. **Exam structure must come from the canonical exam registry and official, versioned exam pattern.** Never guess a generic subject list when an exam/stage/paper mapping is missing. Fail closed and report the missing mapping.
7. **Separate sharing from educational verification.** A student sharing a test does not make it verified. A shared test must never be marked Admin-verified merely because it was successfully parsed or displayed.
8. **Server-side enforcement is authoritative.** Client-side validation improves UX but cannot be the security boundary for permissions, counters, quotas, moderation, or verification.
9. **Keep Main isolated.** Work on this plan branch or a separately approved implementation branch. Do not merge to `main`, force-push, or overwrite unrelated work without explicit approval.
10. **Audit before implementation.** Inspect the current file and its call sites before editing. Make focused changes, add tests, run the relevant suite, and report the exact result. Do not claim a test passed unless it actually ran and passed.

## 2. Current code baseline observed

The following findings were recorded during the initial repository audit. Re-check the current branch before implementation because code may have changed since this snapshot.

| Area / file | Existing behavior observed | Known gap or risk |
|---|---|---|
| `app/src/main/java/Com/ak/testseries/ui/CommunityUI.kt` | Feed, create-post route, post-detail route, feed/bookmark tabs, search, subject filter, post cards, likes/comments/bookmarks, test-share card, basic admin delete path. Firestore is called directly from UI. | No complete Doubt type/flow; no rich text/highlight/camera/gallery flow; no upload flow despite URL fields; no Test Verification Card; only loaded 20 posts are searched; no pagination; My Posts enum exists without a tab; nested replies are not fully serialized/hydrated. |
| `app/src/main/java/Com/ak/testseries/ui/CommunityAdminUI.kt` | Settings and Users tabs; manage-posts view; community/maintenance switches, configured daily limits, blocked words, retention, rewards, announcement, student search and suspend/unban controls. | No complete report queue, approval queue, educational test-verification queue, moderation audit UI, analytics or block-history UI. Several settings appear to be stored as configuration without confirmed server enforcement. |
| `app/src/main/java/Com/ak/testseries/data/TestVisibilityManager.kt` | Visibility decisions for admin, public/published, private owner, under-review owner and draft owner. | Must verify all read paths use the same policy; normalize owner/creator/uploader identities; define rejected and community-shared states without weakening private-test access. |
| `firestore.rules` | Authenticated own-post creation, protected-field restrictions, creator edit/delete, admin moderation, and student updates for some counters/comments. | Client-controlled upvote/comment counters and comment arrays can be manipulated. Shared-test read rules are too broad for documents containing answer keys. |
| Test sharing in `NEETViewModel.kt` and `MainActivity.kt` | Share action checks login/ban/ownership/question availability and writes a shared-test document plus a Community post. Auto-share state exists for some generated public tests. | Shared-test document contains full question payload including correct answers/explanations; current read rule allows authenticated users to read it. Parser can set `isVerifiedCommunity = true` without Admin approval. No registry-backed verification selection/review flow. |
| `UniversalExamRegistry.kt` | `getSubjectsForExamName` resolves subjects from exam/stage/paper registry data. | Unknown exam mappings fall back to generic subjects. Verification must not use that fallback. Stage/paper-specific official pattern data and version checks are required. |
| `storage.rules` | Existing paths for question images, chart images, PDFs and live tests. | No dedicated student Community attachment path was observed. Student uploads to a new Community path need explicit scoped rules. |
| `firestore.test.js` | Tests for Community post create, protected fields, other-user edits/deletes, shared-test creator updates and banned-user enforcement. | No coverage for answer-key confidentiality, attachment security, comment ownership, duplicate reactions, concurrency, approval/rejection, verification, quota enforcement or Doubt workflows. Existing tests permit direct client counter/comment updates. |
| `firestore.indexes.json` | Composite index for `Community_Posts` by `targetExam` and descending timestamp. | More indexes may be needed after final query design; do not add speculative indexes. |
| `MainActivity.kt` integration | Community screen/admin screen navigation and feature state exist. | Verify Community enable/disable state is actually synchronized with authoritative remote configuration and enforced beyond navigation visibility. |

### Initial risk priority

- **Critical:** Shared test answer-key exposure through broadly readable shared-test documents.
- **Critical:** A shared test can be represented as verified without a real Admin approval decision.
- **High:** Community image upload is not fully implemented and no dedicated storage path was observed.
- **High:** Client-side counter/comment mutation can be abused.
- **High:** Community settings may be saved but not enforced by backend operations.
- **Medium:** Replies, pagination, My Posts, search scope and feed query/index coverage are incomplete.
- **Medium:** UI-to-Firestore coupling makes validation and testing harder.

## 3. Required target architecture

Keep the existing Android/Kotlin + Firebase architecture unless the audit proves a specific component needs a controlled change.

### Android layers

- **UI (Compose):** Render screens and collect user input. Do not own security decisions or directly mutate sensitive Firestore fields.
- **ViewModel:** Hold screen state, validation feedback and user actions. No AI doubt dependencies.
- **Repository:** Own Community operations and map domain results/errors.
- **Remote data source:** Execute authenticated Firestore/Storage calls.
- **Domain models:** Explicit, typed models for posts, doubts, answers, comments, attachments, shared-test references, reports, moderation decisions and verification status.
- **Local persistence/cache:** Only if the existing repository architecture requires it. Cached Community data must not bypass visibility checks.

### Backend authority

Use trusted backend functions/transactions for operations that require server authority:
- Creating/updating/deleting counters and reactions.
- Enforcing daily post/comment/attachment limits.
- Validating ownership and share permission.
- Creating a safe public Community test projection.
- Admin verification and moderation decisions.
- Report aggregation and suspension/ban enforcement.
- Retention and cleanup jobs.
- Notification fan-out where appropriate.

Do not introduce a new backend stack solely for Community if existing Firebase Functions can safely own these operations.

## 4. Data model and state contracts

Finalize schemas only after inspecting current entities, repositories, Firestore helper functions, Functions and migrations. The following is the required conceptual contract; adapt field names to existing conventions without duplicating existing data.

### Community post

Minimum concepts:
- `postId`, `creatorId`, safe display snapshot, `postType`, title/body, created/updated timestamps.
- `targetExamId`, optional stage/paper/section, subject/chapter/topic IDs only when valid for that exam mapping.
- Attachment metadata references, not arbitrary local filesystem paths.
- Moderation state, visibility state, pinned/official flags controlled by trusted Admin/backend only.
- Aggregate counters maintained by trusted operations or transactions.
- Optional `testId` reference for a shared test; do not embed its answer key.

Post types should be explicit and validated: `DISCUSSION`, `DOUBT`, `TEST_SHARE`, `ANNOUNCEMENT` (Admin-only where applicable). Image/text content is an attachment/content capability, not a reason to invent more post types.

### Doubt and answer

- A Doubt is a Community post with a dedicated doubt workflow and typed metadata.
- Student can write plain text; rich formatting/highlighting can be stored as a safe structured document or a sanitized supported subset.
- Image attachments can be captured by camera or selected from gallery.
- Answers and replies are written by authenticated students. The author can edit/delete their own content subject to moderation policy; Admin can moderate.
- Best answer can be selected only by the doubt author or an explicitly authorized moderator, according to the final policy.
- Resolved/unresolved state must be explicit and auditable.
- Do not add any AI-generated answer or automated doubt-solving field, endpoint, prompt, connector, or fallback.

### Test sharing and verification states

Keep separate state dimensions. At minimum distinguish:
- Original test visibility/ownership (existing test access policy).
- Community share state: not shared, submitted, shared, withdrawn, rejected/hidden.
- Educational verification: not reviewed, pending review, verified, rejected.
- Moderation state: visible, hidden, removed, locked (as applicable).

A shared test is not automatically public, and a public Community card is not proof of educational verification. Admin approval must be an explicit, auditable action.

### Safe shared-test representation

Preferred design:
- Store canonical `testId` reference and a limited metadata projection needed for a card.
- Fetch questions only through a backend-authorized attempt/read path that checks the share state and the viewer's permission.
- Never include `correctAnswer`, explanation, marking key, or hidden answer metadata in a broadly readable Community document.
- If a separate public question projection is genuinely required, create a purpose-built schema that excludes all answer-bearing fields and prove that with automated tests.
- Do not copy the complete private test into a public collection.

## 5. Test Verification Card: required user flow

1. Student opens Share Test.
2. App verifies login, ban/suspension state, ownership/creator permission, test existence, and that the test is shareable.
3. Student selects the exam from the canonical exam registry.
4. App then requires the correct stage/paper/section where the exam pattern defines them.
5. App shows only subjects allowed by that exact exam + stage/paper + pattern version. For NEET UG, the expected subject choices are Physics, Chemistry, Botany and Zoology, subject to the active official pattern data.
6. Student confirms the intended subject/section and submits the share request.
7. Validator compares selected metadata with the actual test structure: exam identity, stage/paper/section, subject distribution, question count, duration, marks/marking scheme where applicable, ownership and permission.
8. If registry/pattern data is missing or stale, stop the operation with a clear actionable error. Never fall back to generic subjects.
9. Store validation outcome, registry/pattern version, validator version and timestamp.
10. Place the share into the configured review state. Do not label it verified.
11. Admin reviews educational correctness and approves/rejects with reason and audit record.
12. Only the approved safe projection becomes eligible for the verified/public feed, subject to moderation and privacy rules.

**Important distinction:** automated metadata validation checks structural consistency. It does not prove that questions or answers are academically correct. Educational verification is an Admin review decision.

## 6. Development phases and exact work sequence

Each phase must be completed and tested before the next dependent phase starts.

### Phase 0 — Finish repository audit (read-only)

Inspect, document and cross-reference:
- `FirestoreHelper.kt`
- `FirebaseRemoteDataSourceImpl.kt`
- Community and test-related entities/models.
- `AppDatabase.kt`, relevant DAO interfaces, schema versions and migrations.
- `TestRepositoryImpl.kt` and every test retrieval path.
- Full `NEETViewModel.kt` share/auto-share call sites.
- Full `MainActivity.kt` navigation, deep-link and feature-flag wiring.
- Firebase Functions related to Community, shared tests, user limits, notifications and moderation.
- Security rules tests and CI workflows.
- All call sites for `Shared_Community_Tests`, `Community_Posts`, `isVerifiedCommunity`, `autoShareToCommunity`, image/PDF URLs and Community settings.

For each file record: responsibility, current behavior, callers, data read/write paths, security boundary, existing tests, exact gaps and likely impact. No code change in this phase.

**Exit criteria:** complete dependency map and confirmed baseline; no unresolved claim about a file that has not been inspected.

### Phase 1 — Freeze contracts and migration strategy

- Agree the post/doubt/answer/attachment/share/verification/report state contracts.
- Map new fields against existing Firestore documents and Kotlin entities.
- Define backward compatibility for old Community posts and shared-test records.
- Define Firestore/Storage rules and backend callable/request contracts before UI wiring.
- Decide whether any Room schema change is actually necessary. Avoid a migration if Community remains remote-only.
- Define rollback and data repair strategy for already-shared documents containing answer-bearing data.

**Exit criteria:** reviewed schema and API contract, migration/rollback notes, security review checklist.

### Phase 2 — Close existing security defects first

- Audit every reader of `Shared_Community_Tests`.
- Remove broad student access to answer-bearing records.
- Separate safe card metadata from answer-bearing test content.
- Ensure private test visibility is checked by the backend and all Android/Room/Firestore paths.
- Remove automatic assignment of Admin verification in parsing code.
- Define and enforce distinct share, verification and moderation states.
- Review Firestore Rules for client-written counters, comment arrays and protected fields.
- Add Storage path rules for Community attachments with UID ownership, allowed MIME types, file-size ceilings and deletion policy.
- Ensure Admin privileges are verified from trusted claims/roles, not client-supplied flags.
- Check existing exposed records and plan cleanup/repair without deleting legitimate student data.

**Exit criteria:** security tests prove unauthorized users cannot read private tests, answer keys, explanations or protected attachments; shared status cannot impersonate Admin verification.

### Phase 3 — Repository and backend service foundation

- Move direct Firestore business operations out of Compose UI into repository/data-source boundaries consistent with the app.
- Add typed result/error handling and stable IDs.
- Add backend-authoritative post creation, update, deletion and moderation operations.
- Add transaction-safe reactions and comment counters.
- Add idempotency keys or equivalent duplicate-submission protection.
- Enforce configured daily limits on the server.
- Enforce blocked words and attachment limits on the server, with clear policy for edits.
- Implement retention/cleanup as a scheduled trusted job, with audit/logging and safe exclusions.
- Confirm feature enable/maintenance switches are enforced by backend as well as UI.

**Exit criteria:** Android UI cannot bypass permissions or limits by calling Firestore directly; duplicate and concurrent operations behave deterministically.

### Phase 4 — Community feed and post creation

- Preserve existing feed, bookmarks, search/filter and post-detail behavior while refactoring.
- Add explicit post-type selection and validation.
- Implement text/discussion creation.
- Implement rich-text/highlight support only with safe sanitization and a defined rendering format.
- Implement camera capture and gallery selection through Android platform APIs.
- Upload images to the dedicated Community Storage path; store attachment metadata and safe download references.
- Add upload progress, retry, cancellation, file-size/type validation and cleanup of abandoned uploads.
- Add My Posts, pagination, stable sorting and query/index coverage.
- Add empty, loading, offline/error and permission-denied states without fake content.
- Keep announcement creation Admin-only.

**Exit criteria:** each post type works end-to-end; attachments are secure; pagination does not duplicate or omit posts under normal feed updates.

### Phase 5 — Manual Doubt and student-answer workflow

- Add a dedicated Create Doubt entry point.
- Support text, safe formatting/highlighting, camera and image upload.
- Add doubt detail page with answers and replies.
- Add author/moderator permissions for edit/delete.
- Add best-answer selection and resolved/unresolved state.
- Add sorting/filtering for latest, unresolved and solved doubts.
- Add report/block controls and abuse handling.
- Ensure answer/reply counters are server-maintained and concurrency-safe.
- Search all code and dependencies for AI calls reachable from the Doubt workflow and remove/prevent them.
- Do not display AI status, AI suggestions or AI-generated placeholder answers in Doubt screens.

**Exit criteria:** two or more real authenticated test users can create and answer doubts manually; no AI service is called; permission and abuse tests pass.

### Phase 6 — Test sharing and Test Verification Card

- Build exam selection from canonical registry IDs, not display-name guesses.
- Add stage/paper/section selection only where registry data requires it.
- Resolve subjects from exact exam + stage/paper + active pattern version.
- Remove generic fallback for verification. Unknown mappings must fail closed.
- Validate question count, duration, subject distribution, marks/marking scheme, ownership, status and permission.
- Record validation report and version metadata.
- Create safe Community card referencing canonical test ID.
- Ensure private tests remain private unless owner explicitly shares and policy allows it.
- Keep answer keys/explanations outside broadly readable Community documents.
- Add withdrawal, rejection and resubmission rules.
- Add duplicate share protection and idempotency.
- Re-check auto-share behavior; it must not bypass student consent, validation, moderation or verification. Disable it if it cannot meet those requirements.

**Exit criteria:** NEET UG shows Physics, Chemistry, Botany and Zoology from the registry; other exams show only verified registry-defined sections/subjects; missing mappings stop safely; answer keys never leak.

### Phase 7 — Admin moderation and educational verification

- Add review queues for new posts, reported posts and test verification requests.
- Separate content moderation from educational verification.
- Admin actions: approve, reject with reason, hide, restore, lock, remove, suspend/ban as policy allows.
- Store immutable audit events: actor, action, target, reason, timestamp and relevant before/after state.
- Add report status and resolution workflow.
- Add user block/suspension history and appeal/reversal policy if required.
- Protect Admin endpoints and UI with trusted role checks.
- Add bulk actions only after single-item workflows are reliable.

**Exit criteria:** every privileged action is server-authorized and auditable; students cannot approve or verify their own tests.

### Phase 8 — Reactions, comments, search, notifications and contribution ranks

- Implement like/unlike as idempotent per-user records or trusted transactions.
- Prevent duplicate likes and counter drift.
- Implement comments/replies with ownership and moderation rules.
- Complete bookmarks and My Posts.
- Add search across server-paginated results, not just the first 20 loaded posts.
- Add exam/subject/chapter/type/status filters backed by valid registry IDs and indexes.
- Add trending/helpful ranking only with transparent, abuse-resistant signals.
- Add in-app notifications for replies, best answer, moderation outcomes and test verification.
- Add FCM only as a separate later delivery layer if configured and tested.
- Keep Community contribution points separate from exam score, XP used for academic ranking, AIR and leaderboard rank.
- Define point events and idempotency so one action cannot farm unlimited points.

**Exit criteria:** notifications are not duplicated; search is paginated; Community points cannot change exam ranking or scores.

### Phase 9 — Admin settings, operations and observability

- Verify every setting has a real backend enforcement point.
- Add safe configuration validation and audit trail for setting changes.
- Implement and test daily post/comment/attachment limits.
- Implement retention cleanup with dry-run/metrics before deletion.
- Add moderation queue counts and operational metrics without exposing private student content.
- Log security-relevant denials and failures without logging answer keys, tokens or sensitive attachment URLs.
- Add alerts for unusual upload, reaction, report or abuse patterns if infrastructure supports them.

**Exit criteria:** settings have demonstrable behavior; scheduled jobs are observable and safe to retry.

### Phase 10 — Regression, release and controlled rollout

Run:
- Android unit tests and Compose/UI tests.
- Firestore Rules emulator tests.
- Storage Rules emulator tests.
- Backend unit/integration tests.
- Migration tests if schema changes were made.
- Security tests for ownership, role spoofing, private test access and answer-key leakage.
- Concurrency/idempotency tests for likes, comments, uploads, sharing and limits.
- Registry tests for every supported exam/stage/paper.
- Existing test generation, saved/private tests, attempts, scoring, ranking and leaderboard regression tests.
- Build APK/AAB and inspect release configuration.
- Manual multi-account QA: owner, ordinary student, suspended student, moderator/Admin.
- Verify no fake/demo content and no AI invocation in Doubt workflow.

Roll out behind a server-controlled feature flag. Start with internal/Admin QA, then a small student cohort, then broader release. Keep rollback instructions and database repair steps ready.

**Exit criteria:** all mandatory tests pass, no critical/high security issue remains, and release owner explicitly approves rollout.

## 7. Required test matrix

| Test area | Required proof |
|---|---|
| Authentication | Signed-out user cannot create, react, comment, upload or share. |
| Ownership | Student cannot edit/delete another student's post, doubt, answer or test. |
| Admin role | Client cannot spoof Admin/monitor/official/verified flags. |
| Doubt | Manual create/answer/reply/resolve works; no AI connector/network call is invoked. |
| Attachments | Only owner can upload to own scoped path; reject wrong MIME, oversize and unauthorized delete. |
| Shared tests | Public card exposes only safe metadata; answer key and explanations are not readable from public Community paths. |
| Private tests | Non-owner cannot read private test via Community, direct ID, search, cache or backend endpoint. |
| Verification | Invalid exam/subject/stage/paper/question count/duration/marks is rejected; unknown registry mapping fails closed. |
| Admin review | Only authorized Admin can approve/reject; action and reason are audited. |
| Counters | Duplicate requests and concurrent likes/comments do not corrupt totals. |
| Limits | Concurrent submissions cannot exceed server-enforced daily quota. |
| Search/feed | Pagination is stable; filters use valid IDs and required indexes. |
| Retention | Cleanup is scoped, retry-safe and does not remove active/private test records. |
| Regression | Test generation, private vault, attempts, scoring, ranking and leaderboard remain unchanged. |

## 8. Definition of done for every task

A task is not complete merely because the screen appears. It is complete only when:
- The exact files and call sites have been inspected.
- The implementation follows the agreed contract.
- Security is enforced on the backend/rules, not only in UI.
- Automated tests cover success, denial, malformed input and concurrency where relevant.
- Existing data is backward-compatible or has a documented migration.
- No dummy data or hidden fallback was introduced.
- Logs do not leak private data or answer keys.
- The exact commands run and their results are recorded.
- The change is committed only to the approved feature branch.
- Main remains unchanged until an explicit merge decision.

## 9. Known audit items still requiring direct file inspection

The initial audit is not a substitute for reading every dependency. Before implementation, finish the file-by-file inspection of:
- `FirestoreHelper.kt`
- `FirebaseRemoteDataSourceImpl.kt`
- Community/test entities and DAOs.
- `AppDatabase.kt` and relevant migrations.
- `TestRepositoryImpl.kt` and test visibility read paths.
- Full Community-related sections of `NEETViewModel.kt` and `MainActivity.kt`.
- Firebase Functions and deployment configuration.
- CI workflows and all related security test files.

Update this plan with exact paths, functions, call sites and test commands discovered during that audit. If a path differs in the repository, record the actual path rather than creating a duplicate file or assuming the name.

## 10. Branch and merge policy

- This branch contains planning documentation only unless a later task explicitly authorizes implementation.
- Do not merge this branch into `main` automatically.
- Implementation can use this branch or a dedicated feature branch created from an agreed base.
- Before any merge, compare the complete diff against current `main`, inspect overlapping files, run all required CI and security tests, and resolve conflicts deliberately.
- No force-push or destructive migration without explicit approval.

## 11. Progress tracking template

For every task added to this document, use:

| Field | Required entry |
|---|---|
| Task ID | Stable ID such as `COM-SEC-001` |
| Status | `NOT_STARTED`, `IN_PROGRESS`, `BLOCKED`, `TESTING`, `DONE` |
| Files | Exact repository paths |
| Current behavior | What code does now |
| Required change | Specific behavior to implement |
| Dependencies | Contracts, backend, rules, registry, migrations |
| Security impact | Data/permission risks |
| Tests | Exact test names/commands |
| Evidence | Commit SHA, CI run, test output |
| Reviewer | Person/role who checked it |

Do not mark work `DONE` without test evidence.

---

**Guiding principle:** Community is a student-to-student learning and discussion feature. Doubts remain fully manual. Test sharing remains permission-safe. Educational verification remains an explicit Admin decision. Existing exam and ranking systems remain protected.
