# AK Test Series — Universal Exam Preparation Platform

> **Project goal:** Build a secure, reliable, student-first exam preparation app for 20+ competitive and academic exams, with a reusable exam engine, AI-assisted question generation, private practice, community learning, and role-based administration.

**Repository:** https://github.com/abhi952817-sudo/AK-TEST-SERIES-  
**Active development branch:** `fix/phase1-real-questions-only`  
**Implementation and audit tracker:** [app/MASTER_IMPLEMENTATION_TRACKER.md](app/MASTER_IMPLEMENTATION_TRACKER.md)

---

## 1. Product vision

AK Test Series is intended to provide one consistent learning experience across 20+ exams instead of maintaining a separate app or test engine for every exam.

The product should support:
- Exam-specific syllabus, subjects, sections, question formats, marking rules, timers, and eligibility.
- Real PDF-based question creation with text and image extraction, duplicate detection, answer verification, and syllabus mapping.
- Timed mock tests, practice tests, review, result analysis, and fair exam-specific rankings.
- Student-owned private tests and controlled public/community sharing.
- Automated processing for routine work, with human review focused on exceptions, suspicious content, and reported questions.

This README describes the target product and the current implementation evidence. A feature listed under **Planned** or **Needs verification** must not be treated as already delivered.

## 2. Current technical architecture

The repository tracker describes this architecture:

| Layer | Technology / responsibility |
|---|---|
| Android UI | Kotlin, Jetpack Compose |
| App architecture | MVVM, Coroutines, Flow |
| Local data | Room database and local repositories |
| Authentication | Firebase Authentication |
| Cloud data and access control | Cloud Firestore security rules |
| Server-side operations | Firebase Cloud Functions and Admin SDK |
| File/media access | Firebase Storage with security rules |
| AI question generation | Gemini integration in the PDF/question pipeline |
| Tests | Kotlin/JUnit, Node test runner, Firebase Emulator rule tests |

The app must treat the server as authoritative for protected answer keys, scores, ranking eligibility, and other privileged fields. Client-side values must never be trusted for official scoring.

## 3. Supported exam model — 20+ exams

The exam engine must be configuration-driven. Adding an exam should primarily require exam metadata and configuration, not a copy of the entire test implementation.

Each exam configuration should define:
- Exam ID, display name, language, level, and active status.
- Syllabus hierarchy: subjects, units, chapters, and topics.
- Stages, papers, sections, and section-specific instructions.
- Supported question types and option count.
- Positive marks, negative marks, partial-mark policy, and unanswered policy.
- Total duration, per-section duration, and optional per-question timer.
- Question selection rules, attempt limits, and ranking eligibility.
- Exam-specific result and percentile rules.

**Important:** An exam must not inherit another exam's marking scheme, eligibility, syllabus, or ranking population by accident. Configuration and regression tests must verify every registered exam.

## 4. Student app — target panels and screens

The following is the proposed complete Student experience. Existing screen files are linked where identified; other entries are product requirements, not claims that a complete screen already exists.

| Student panel | Expected capabilities | Status |
|---|---|---|
| Home / Dashboard | Exam shortcuts, continue test, daily activity, announcements, progress summary | Existing UI area; full flow needs verification |
| Exam selection | Browse/search exams, exam overview, syllabus and exam pattern | Universal exam catalog exists; end-to-end verification pending |
| Test series | Official, practice, daily, mock, and topic-wise test lists | Needs complete flow verification |
| Test player | Timer, question palette, navigation, mark-for-review, answer selection, autosave, submit confirmation | Critical end-to-end regression required |
| Result & review | Score, correct/incorrect/unanswered counts, explanations, permitted answer review, section analysis | Protected review flow exists; full UI/authorization verification required |
| Ranking | Exam-specific leaderboard, percentile, eligibility, attempt history | Server-side ranking logic exists; live deployment and full-flow verification required |
| AI PDF upload | PDF selection, page count, extraction preview, progress, quota, retry/error handling | Existing upload UI; extraction gaps remain |
| Private Test Vault | Local/private tests, ownership isolation, rename, delete, retry, controlled sharing | Existing screen: [PrivateTestsVaultScreen.kt](app/src/main/java/Com/ak/testseries/ui/PrivateTestsVaultScreen.kt) |
| Community | Public test discovery, share/download, posts, report, moderation feedback | Existing UI: [CommunityUI.kt](app/src/main/java/Com/ak/testseries/ui/CommunityUI.kt) |
| Study material | Notes, chapter-wise material, saved resources | Existing screen: [StudyMaterialScreen.kt](app/src/main/java/Com/ak/testseries/ui/StudyMaterialScreen.kt) |
| Analytics | Subject/topic performance, accuracy, time use, progress trends | Existing screen: [AnalyticsScreen.kt](app/src/main/java/Com/ak/testseries/ui/AnalyticsScreen.kt) |
| Doubt-solving | Ask a question, attach relevant context, receive sourced explanation, save doubt history | Product requirement; implementation needs audit |
| Notifications | Test reminders, processing status, result alerts, admin announcements | Product requirement; implementation needs audit |
| Profile & settings | Account, language, privacy, data export/deletion request, accessibility, session management | Existing account foundations; completeness needs audit |
| Help & support | FAQs, report a problem, contact/support ticket status | Product requirement; implementation needs audit |

### Student journey acceptance flow

A student must be able to complete this journey without dead ends:

1. Register/login and complete the profile.
2. Select an exam and view its syllabus/test series.
3. Open an eligible test and read instructions.
4. Start the test and answer all supported question types.
5. See a reliable timer and recover safely from temporary network/app interruptions.
6. Submit once; retries must not create duplicate official attempts.
7. Receive a server-authoritative result.
8. View only the answer explanations and solutions authorized for that test.
9. See the correct exam-specific ranking/percentile where eligible.
10. Review mistakes and continue practice.

## 5. Admin console — three roles and eight control areas

The agreed roles are:

1. **Super Admin** — platform-wide authority, role/security governance, critical configuration, and emergency controls.
2. **Content Admin** — exam content, PDF/question processing exceptions, source-answer review, and publication workflow.
3. **Support Admin** — student support, reports, disputes, and account/help workflows within explicitly granted permissions.

Access must be enforced by server-side claims and Firestore/Storage rules. Hiding a button in the UI is not authorization.

### Eight Admin control areas

| Control area | Main responsibilities | Primary role |
|---|---|---|
| 1. Student Management | Search student, account status, support context, access issues, abuse reports; no direct editing of scores | Super Admin / Support Admin |
| 2. PDF Upload Control | Upload permissions, file constraints, processing queue, failure reasons | Super Admin / Content Admin |
| 3. Daily Upload Limit | Per-user quota, rate limits, abuse prevention, transparent quota status | Super Admin |
| 4. Private/Public Control | Ownership, visibility, sharing permission, publication state | Super Admin / Content Admin |
| 5. AI Processing Control | Provider/model configuration, usage limits, cost monitoring, retries, failure alerts | Super Admin |
| 6. Question Exception Review | Missing source key, answer mismatch, duplicate suspicion, malformed question, user reports | Content Admin |
| 7. Storage Management | Retention policy, ownership, orphan cleanup, storage usage, access audit | Super Admin |
| 8. Critical Alerts | Security events, failed jobs, unusual usage, ranking/submission anomalies | Super Admin / Support Admin |

### Admin operating principle: 80% automation / 20% human control

- **Automatic by default:** file checks, extraction, question detection, duplicate/fingerprint checks, generation, structural validation, syllabus suggestions, test packaging, and routine status updates.
- **Human review by exception:** unresolved answer keys, conflicting sources, low-confidence extraction, suspected duplicates, reported content, and publication/security exceptions.
- Do not require manual approval for every normal test.
- Do not auto-publish a question whose factual answer is not independently verified.
- Every privileged action must have an actor, timestamp, reason, and audit record.

## 6. PDF-to-test pipeline

Target pipeline:

`PDF select → local inspection → text/image extraction → question detection → duplicate check → AI generation/structuring → answer/source verification → syllabus mapping → quality gate → test package → private/public decision`

### Required behavior

1. Inspect file type, size, page count, readability, and corruption before expensive processing.
2. Extract text page-by-page and preserve page references.
3. Extract relevant diagrams/tables/images and retain their page mapping.
4. Use OCR for scanned pages; do not silently treat an unreadable page as empty content.
5. Send image payloads to a vision-capable model when diagram understanding is required; OCR text alone is not diagram comprehension.
6. Detect question boundaries and preserve options, equations, tables, and question type.
7. Detect duplicates using normalized content and fingerprints; do not delete merely similar questions without confidence/review policy.
8. Generate or structure questions with source references where available. If source/page is unknown, mark it unknown rather than inventing attribution.
9. Independently verify answer keys. Missing or conflicting source evidence must remain in review state.
10. Map questions to the configured exam syllabus.
11. Run structural, factual, answer-key, option, numerical, marking, and duplicate checks.
12. Create a test only when its configured quality and publication gates pass.
13. Show clear progress, cancellation, retry, and actionable errors.

### Privacy and storage rule from the product plan

- Keep the original PDF and the extracted text/images/generated test in the user's local TestPackage where the workflow permits.
- The server should store only the required PDF/question fingerprints and Test Card metadata, not a full copy of the source PDF by default.
- Any cloud media upload must have an explicit purpose, owner, retention rule, and access policy.
- Private material must remain private unless the owner deliberately shares it.
- Do not expose answer keys or explanations in public test documents before the authorized review stage.

### Known extraction gaps

- Diagram/image understanding through Gemini Vision is not yet confirmed end-to-end in the universal generation path.
- Hindi and other non-Latin OCR support is not yet implemented/verified.
- Large PDF limits and OCR memory behavior require device testing.
- Failed extraction must produce a clear error; it must never create fabricated/sample questions.

## 7. Test engine, scoring, submission, and ranking

- Use one shared test engine driven by exam configuration.
- Support MCQ single, MCQ multiple, numerical, assertion-reason, match, statement, passage/case, diagram/graph, and other configured formats only when their UI, validation, and scoring are implemented.
- Multiple-correct answers require the complete correct set according to the configured marking policy.
- Numerical parsing must reject malformed and non-finite values.
- Timer calculations must use configured duration, not a fixed question-count assumption.
- Official score and ranking must be computed server-side from protected answer keys.
- Submission must be idempotent and resistant to duplicate attempts and forged client scores.
- Ranking populations and eligibility must be isolated per exam and test type.
- Review solutions must be returned only after server-side authorization.

## 8. Security, privacy, and data safety

Required security boundaries:
- Firebase Auth identity and verified server-side role claims.
- Least-privilege permissions for each Admin role.
- Firestore rules that deny client writes to score, rank, answer-key, admin, and other authoritative fields.
- Storage rules that enforce owner/admin access, path ownership, MIME type, size, and allowed operations.
- Private test isolation between students.
- Server-side validation of callable inputs, test publication state, ownership, and attempt tokens.
- Rate limiting and daily quotas for expensive AI/PDF work.
- Audit logs for admin actions and sensitive data access.
- Safe logout/session cleanup and account-switch isolation.
- No secrets, API keys, private answer keys, or personal documents committed to Git.

### Room database migration blocker

The repository tracker records that historical Room schemas for versions 1–14 were not exported. Do not invent old migrations. Before release, recover genuine historical schemas or representative database files, build verified migrations, and test data preservation. Keep this as a release blocker until resolved.

## 9. Reliability, testing, and release gates

Every code change should include relevant regression tests. Track each check as one of:
- **PASS:** actually executed and passed.
- **FAIL:** actually executed and failed.
- **NOT RUN:** not executed in the available environment.
- **STATIC REVIEW:** inspected source only; not runtime proof.
- **BLOCKED:** cannot safely complete because required schema, credentials, environment, or evidence is missing.

Minimum release suites:
- Android Gradle build and Kotlin/JUnit tests.
- Backend Node unit tests.
- Firestore Emulator rules tests.
- Storage Emulator rules tests.
- Room migration tests with real schema snapshots.
- PDF fixtures: text-only, scanned, mixed, corrupt, password-protected, image-heavy, and over-limit files.
- Student journey: login → test list → start → answer → timer → submit → result → review → ranking.
- Role matrix tests for all three Admin roles and normal students.
- Regression tests across every configured exam and question type.
- Device/network tests for offline recovery, process death, upload interruption, and duplicate submissions.

**Release rule:** No production-ready claim until critical security, migration, extraction, scoring, submission, and end-to-end checks are actually executed and pass.

## 10. Development roadmap

| Phase | Work package | Exit criteria |
|---|---|---|
| 0. Baseline | Repository inventory, branch safety, issue tracker, architecture map | Every critical flow and open finding has an owner/status |
| 1. Data safety | Room schema recovery/migrations, local TestPackage, retention, account isolation | No unverified destructive migration; data preservation tests pass |
| 2. Universal exam engine | 20+ exam configurations, syllabus hierarchy, question formats, marking rules | Registry and scoring regression suite passes for every configured exam |
| 3. PDF intelligence | Text + image extraction, OCR languages, vision, question segmentation | Fixture suite proves extraction behavior; failures never create mock questions |
| 4. AI validation | Source attribution, answer verification, duplicate detection, confidence/review states | No unverified answer is silently approved |
| 5. Student experience | Dashboard, tests, player, review, ranking, vault, community, study, analytics, support | Complete student journey works on device |
| 6. Admin experience | Three roles, eight control areas, exception queue, audit log | Permission matrix and admin workflows pass |
| 7. Backend hardening | Firestore/Storage rules, callable validation, quotas, idempotency | Emulator and backend suites pass |
| 8. Release readiness | Performance, accessibility, privacy, crash recovery, deployment checklist | All release gates pass; rollback and monitoring plan documented |

## 11. Current repository status (checkpoint-based)

The active development work is on `fix/phase1-real-questions-only`; do not treat this as a production release branch.

The implementation tracker records work on:
- Server-authoritative scoring and protected answer keys.
- Exam-specific ranking/eligibility protections.
- Firestore and Storage rule hardening.
- Private test ownership isolation.
- PDF/OCR pipeline improvements and removal of a mock-question fallback.
- Answer-key review gates and scoring regression coverage.

Known outstanding items recorded in the latest audit checkpoint include:
- Historical Room schema/migration evidence for versions 1–14.
- End-to-end Gemini Vision image understanding in the universal PDF pipeline.
- Hindi and other script OCR.
- Deployment of the required Firestore composite index.
- Runtime execution of the relevant Android, backend, Firestore Emulator, Storage Emulator, and physical-device suites for the latest changes.

For exact per-file findings, test status, and commit checkpoints, use the [Master Implementation Tracker](app/MASTER_IMPLEMENTATION_TRACKER.md). The tracker is the source of truth for implementation evidence; this README is the product and architecture overview.

## 12. UI/UX behavior and animation guide

UI (User Interface) is what the student or Admin sees and touches: screens, text, buttons, fields, cards, colors, icons, and navigation. UX (User Experience) is how smoothly and clearly the user completes a task: where they go next, what feedback they receive, whether they can recover from an error, and whether the app feels predictable.

### UI/UX interaction model

Every interactive control must have a defined behavior and visible state:

| User interaction | Expected UI response | UX requirement |
|---|---|---|
| Tap a navigation item | Open the matching screen and show the selected destination | Keep navigation consistent; preserve safe in-progress work |
| Tap Start Test | Show instructions/loading, then open the test player | Prevent accidental duplicate starts |
| Select an answer | Update selected option and save state | Clearly distinguish selected, saved, flagged, and unanswered |
| Move to another question | Update question and palette state | Keep position and timer stable |
| Upload a PDF | Show file details, progress, and processing stage | Explain quota, cancellation, retry, and failure reason |
| Submit a test | Show confirmation, then submission progress | Disable repeat submission while request is in progress |
| Receive result | Show score and breakdown after server confirmation | Never show a guessed or client-forged official score |
| Encounter an error | Show plain-language message and recovery action | Do not silently fail or erase user work |

### Required screen states

Design and implement these states consistently across Student and Admin panels:
- **Default:** normal screen with clear primary action.
- **Loading:** progress indicator plus a short explanation of what is happening.
- **Empty:** explain why there is no content and what the user can do next.
- **Success:** confirm the completed action and show the next useful step.
- **Error:** explain the problem without exposing secrets or internal stack traces; provide safe retry/help.
- **Offline / reconnecting:** show connectivity status and protect locally saved work.
- **Disabled / permission denied:** explain why an action is unavailable; never rely on hidden buttons as security.

### Motion and animation principles

- Use short, purposeful transitions to explain navigation, hierarchy, progress, and state changes.
- Prefer subtle screen transitions, button feedback, progress animation, question navigation, upload-stage progress, and result-chart reveal.
- Keep animations interruptible and respect Android system **Remove animations** / reduced-motion preferences where available.
- Do not animate every element; avoid distracting loops, flashing, excessive parallax, and motion that delays a task.
- Never use animation as the only way to communicate status; pair it with text, icons, or accessible labels.
- Keep test timer, answer selection, and submission responsive; decorative motion must never block exam actions.
- Test animation smoothness on low-memory and lower-end Android devices.

### Student and Admin UX flows

**Student:** Login → Dashboard → choose exam → test series → instructions → test player → submit confirmation → server result → authorized review → eligible ranking → practice recommendations.

**PDF workflow:** choose PDF → inspect file → extraction preview → show detected questions and source pages → validation status → resolve exceptions if needed → save private test or deliberately share.

**Admin:** sign in → role/permission check → role-specific dashboard → queue/alert → inspect exception → take permitted action → record reason/audit event → confirm outcome.

### UI/UX acceptance checklist

- Every visible button, menu item, tab, and card has a working destination or action.
- Back navigation, deep links, loading states, empty states, and error recovery are tested.
- Touch targets, contrast, text scaling, screen-reader labels, and keyboard/focus behavior are checked.
- Student data and test progress survive supported interruptions.
- Admin actions are visible only when appropriate and are still authorized on the server.
- Motion respects reduced-motion preferences and does not obstruct test-taking.
- Validate layouts on small phones, large phones, and tablet/window sizes.

### Animation video / walkthrough

A real MP4 walkthrough is **not included yet**. The linked storyboard defines the scenes, narration, and UI states to record or render. Once the video is produced and added to the repository or uploaded to a stable video host, replace the pending status below with the verified MP4/video URL. Do not use a fabricated link.

- **Storyboard:** [UI/UX animation video storyboard](docs/UI_UX_ANIMATION_STORYBOARD.md)
- **Video status:** Planned — recording/rendering and upload pending.
- **Suggested final asset:** `docs/media/ak-test-series-ui-ux-overview.mp4` (create this file only after the actual video is rendered).

---

## 13. Contribution and change-control rules

1. Work on a feature/fix branch; keep `main` safe.
2. Inspect call sites and data flow before removing or replacing a function.
3. Make small, reviewable changes with focused commit messages.
4. Add regression tests for every behavior change.
5. Do not mark tests as passed unless they were executed.
6. Do not introduce mock/sample questions as a fallback for failed real processing.
7. Do not weaken security rules to make a UI action appear to work.
8. Document migrations, data retention, permissions, and API costs before rollout.
9. Update the implementation tracker at every meaningful checkpoint.
10. Keep secrets and private student content out of source control.

---

## 13. Definition of done

The platform is ready only when:
- Students can complete the full test journey reliably.
- All configured exams use the correct syllabus, question types, timer, marking, and ranking policy.
- PDF extraction handles supported text/image/scanned formats with honest failure states.
- AI-generated answers are verified or clearly held for review.
- Private data and answer keys remain protected.
- All three Admin roles can perform only their authorized tasks across the eight control areas.
- Routine processing is automated while exceptions are reviewable.
- Data migrations preserve existing student data.
- Required automated and device-level release tests pass.

**Current status: IN DEVELOPMENT — NOT DECLARED PRODUCTION READY.**
