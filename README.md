# AK-TEST-SERIES — Complete Universal Product & Software Architecture

> **Architecture baseline — living specification, not a claim of completed implementation.** This section consolidates the product and technical architecture defined so far. Each capability must retain an explicit implementation and verification status. A feature described here is not automatically implemented, tested, or production-ready.

## 1. Product vision and governing principles

AK-TEST-SERIES is intended to become one universal examination, assessment, learning, and AI-assisted study platform for 25 registered examination identities. It must support exam-specific rules through configuration and adapters while reusing common application infrastructure.

Core principles:

- One shared application platform and reusable exam core; no separately duplicated test engine for every exam.
- Each examination keeps its own verified syllabus, stages, papers, sections, question types, duration, marking policy, eligibility, result, and merit rules.
- Unknown or unverified rules fail closed. Never silently apply NEET defaults, generic +4/-1 marking, or another exam's policy.
- Preserve the existing repository, working features, user data, history, private question vault, and existing contracts.
- Reuse and selectively port audited code; do not merge a divergent branch wholesale.
- No fake questions, fake answers, fake success, fake rank, fabricated AI usage/cost, or invented source attribution.
- Keep answer keys and private explanations protected on the server.
- Use versioned contracts, reproducible tests, audit logs, and explicit checkpoints.
- Do not perform production data migrations, Firebase deployments, destructive migrations, or unrelated application-code changes as part of documentation work.
- Distinguish VERIFIED, IMPLEMENTED BUT NOT VERIFIED, PARTIALLY IMPLEMENTED, NOT IMPLEMENTED, BLOCKED, and UNVERIFIED.

## 2. Universal platform architecture

The platform is organized into the following cooperating layers:

1. Client applications: Android student experience and authorized administration interfaces; future web clients can consume the same contracts.
2. Presentation: screens, navigation, UI state, accessibility, language selection, and loading/error/offline states.
3. Application/domain: use cases, validation, authorization decisions, and business workflows.
4. Universal Exam Core: exam registry, stage/paper/section hierarchy, syllabus, question, test, attempt, scoring, result, and ranking contracts.
5. Shared capability engines: AI gateway, PDF/content processing, learning, doubt solving, search, community, notifications, and analytics.
6. Backend: authenticated APIs and callable functions, server-authoritative operations, background jobs, queues, and policy enforcement.
7. Data infrastructure: the verified primary Firestore database, Firebase Storage, Android Room, indexes, backup/recovery, and audit records.
8. Quality and operations: unit/integration/UI/security/regression tests, CI, monitoring, logs, alerts, release checks, and rollback planning.

### 2.1 Logical architecture diagram

    STUDENT AND ADMIN CLIENTS
              |
    PRESENTATION / NAVIGATION / UI STATE
              |
    APPLICATION USE CASES AND DOMAIN CONTRACTS
              |
    UNIVERSAL EXAM CORE
      | Exam Registry
      | Stage / Paper / Section
      | Syllabus / Question Bank
      | Test / Attempt / Timer
      | Scoring / Result / Ranking
              |
    SHARED CAPABILITY ENGINES
      | Universal AI Gateway
      | PDF / OCR / Layout Extraction
      | Question Generation / Validation
      | Learning / Doubt / Search
      | Community / Notification / Analytics
              |
    AUTHENTICATED BACKEND AND BACKGROUND JOBS
              |
    VERIFIED PRIMARY FIRESTORE + STORAGE
       ANDROID ROOM / OFFLINE QUEUE
              |
    SECURITY / AUDIT / TESTING / MONITORING

This is a logical modular architecture. It does not require splitting every module into a separate microservice. Begin with well-separated modules in the existing backend and extract services only when scale, deployment boundaries, or operational needs justify it.

## 3. The 25-exam registry and coverage model

The repository audit snapshot recorded **25 exam IDs, 25 stage IDs, 28 paper IDs, and 99 section IDs**. These are inventory counts from an audit snapshot, not proof that all exam policies or syllabus mappings are complete. Refresh the exact registry and IDs from source before changing them.

The registry must be the canonical source for:

- Stable exam ID and code; display name and aliases.
- Exam category and conducting authority.
- Active/inactive status and supported years/cycles.
- Stage, paper, and section relationships.
- Subject, chapter, topic, and syllabus version references.
- Supported question types and language options.
- Exam pattern, duration, marking, negative marking, optional-question rules, and evaluation policy.
- Eligibility, qualification, merit, ranking, and tie-breaking policies where applicable.
- Official source documents/URLs, source publication date, effective exam year, reviewer, and verification status.
- Configuration version, effective dates, and change history.

### 3.1 Exam families identified in the repository audit

The source audit has identified official-source work for these examination families/cycles: NEET UG, JEE Main, JEE Advanced, CUET, UPSC CSE, SSC CGL, CTET, RRB ALP, UGC-NET, IBPS PO, and IBPS CSA.

The broader registry/audit scope also names SSC CHSL, SSC MTS, SSC CPO, SSC GD, RRB NTPC, RRB Group D, RRB Technician, RRB JE, SBI PO, SBI Clerk, UPTET, CAT, GATE, NDA, and CDS. These names are a coverage checklist, not a declaration that each is a separate one of the 25 canonical IDs. Reconcile variants and aliases against the exact 25 IDs in the current registry; do not invent missing IDs.

The audit snapshot reported 19 official source sets/cycles located and six registry exams still lacking an official source packet or complete stage/paper mapping. Even sourced exams have hierarchy or policy mismatches requiring review. All unsupported fields remain UNVERIFIED.

### 3.2 Exam policy source-of-truth rules

- Official syllabus changes require an authoritative source; an AI inference, coaching paper, student PDF, or mock test is not proof of an official change.
- Store source URL/document, authority, exam year/cycle, publication/effective date, stage, paper, section, retrieval date, and reviewer.
- Preserve old policy versions for historical tests and attempts.
- A new policy version applies only to tests configured for that version.
- Newly discovered topics remain candidates such as EXTRA_PRACTICE, EXAM_PATTERN_PRACTICE, UNVERIFIED_TOPIC, or NEEDS_REVIEW until verified.
- Out-of-syllabus content must not be silently discarded or promoted into the official syllabus.
- Missing policy must return an explicit unsupported/unverified configuration error.

## 4. Universal examination hierarchy

The configurable hierarchy is:

    EXAM
      └── STAGE
           └── PAPER
                └── SECTION
                     └── SUBJECT / ASSESSMENT AREA
                          └── CHAPTER
                               └── TOPIC
                                    └── SUBTOPIC
                                         └── QUESTION

Not every exam uses every level or the same order. The data model must allow direct and optional relationships, multiple subjects in a section, sections without conventional subjects, optional papers, compulsory papers, and exam-specific assessment components.

### 4.1 Exam registry functions

- Register, inspect, version, activate, deactivate, and archive an exam.
- Maintain canonical IDs, aliases, categories, authorities, and supported cycles.
- Validate required configuration before enabling student-facing tests.
- Compare policy versions and preserve historical references.
- Track official-source verification and reviewer decisions.

### 4.2 Stage management

- Create/update/archive stages and define their order.
- Define prerequisites, eligibility, qualification dependencies, and stage availability.
- Map stage-specific papers, syllabus, tests, attempts, results, and merit rules.
- Support preliminary, main, advanced, interview, skill, aptitude, physical, or other stages only where the exam's verified pattern requires them.

### 4.3 Paper and section management

- Configure paper identity, language, duration, maximum marks, instructions, and availability.
- Configure sections, ordering, mandatory/optional status, attempt limits, question distribution, and navigation rules.
- Support section-specific question types, scoring, negative marking, and time rules.
- Validate optional-question selection and attainable maximum marks.
- Do not infer missing section scoring from another section or exam.

### 4.4 Syllabus hierarchy

- Maintain subjects, chapters, topics, subtopics, aliases, and ordering.
- Version the syllabus by exam year/cycle and source.
- Map questions to one or more syllabus nodes with provenance and confidence.
- Track official, discovered, extra-practice, and unverified topics separately.
- Track student progress and question coverage against the selected syllabus version.

## 5. Universal question architecture

### 5.1 Supported question families

The shared question model should support only the types enabled by each exam/paper policy:

- Single-correct MCQ.
- Multiple-correct / multiple-select.
- True/false.
- Assertion and reason.
- Multiple-statement questions.
- Match-the-following.
- Integer, numerical, and decimal responses.
- Fill-in-the-blank.
- Short and long descriptive answers.
- Passage/comprehension and case-based questions.
- Data interpretation.
- Image, diagram, graph, chart, table, map, formula, and equation-based questions.

### 5.2 Question record contract

A question record should identify:

- question ID, immutable version ID, status, and timestamps.
- exam, cycle/year, stage, paper, section, subject, chapter, topic, and subtopic references as applicable.
- question type, language, text, instructions, marks policy reference, and ordering metadata.
- options with stable option IDs and display order.
- media references, captions, labels, alt text, crop/region metadata, and original page coordinates where applicable.
- source document ID, source page, book/chapter/page attribution, and provenance.
- generation run, provider/model metadata where generated by AI, and validation/reviewer history.
- protected answer-key reference and protected explanation/solution reference.
- publication, visibility, and approval state.

Public question payloads must not contain trusted correct answers or private solutions. Keep answer keys in a server-only protected path such as the repository's existing private_keys/answerKey design, subject to current rules and implementation verification.

### 5.3 Question lifecycle and versioning

Draft → Extracted/Generated → Structural Validation → Answer Validation → Source Review → Human Review where required → Approved → Published → Archived/Rejected.

- Preserve every published version referenced by an attempt.
- Editing a question creates a new version; it must not rewrite the historical attempt's question.
- Record answer-key changes and approval decisions.
- Detect duplicates by normalized text, semantic similarity, source identity, and media fingerprint.
- Reject malformed, incomplete, ambiguous, unsupported, or missing-key content instead of publishing it.

## 6. Universal test-series and test-delivery engine

### 6.1 Test categories

Support configured full-length tests, mock tests, chapter tests, subject tests, topic tests, section tests, previous-year papers, practice tests, daily/weekly/monthly tests, AI-generated tests, admin-created tests, private tests, shared tests, and scheduled tests. Adaptive practice may be added only with a defined and tested selection policy.

### 6.2 Test configuration

Every test must reference an exam and the applicable policy/configuration version, and may reference stage, paper, sections, syllabus nodes, and question-bank versions.

Configuration includes:

- Test identity, title, instructions, visibility, owner, publication state, and schedule.
- Question source, selection constraints, count, difficulty distribution, and allowed question types.
- Question ordering and option randomization policy.
- Total duration and optional per-question/per-section timing.
- Marking policy, negative marking, partial credit, rounding, and unanswered rules.
- Compulsory/optional sections and selection caps.
- Navigation, review, pause/resume, attempt limits, and submission rules.
- Result visibility, solution release conditions, and ranking eligibility.

No universal default may silently override an exam's official configuration.

### 6.3 Student test journey

Login → choose exam → select stage/paper → browse test series → read instructions → initialize attempt → load questions → answer/change/clear → mark for review → navigate → persist responses → timer/recovery → submit/auto-submit → server validation → authoritative evaluation → result → answer review when authorized → performance analysis → ranking eligibility and ranking.

### 6.4 Attempt and timer integrity

- Assign a unique attempt ID and server-issued session token.
- Persist the exact test, question, policy, and configuration versions used.
- Save responses safely and make submission idempotent.
- Detect conflicting reuse of an attempt ID.
- Use server-authoritative timestamps/deadlines; do not trust the device clock as the source of truth.
- Recover after app restart or network interruption without duplicating or corrupting answers.
- Pause/resume only when the exam/test policy permits it.
- Auto-submit only according to the verified configured deadline.
- Distinguish CREATED, INITIALIZED, IN_PROGRESS, PAUSED where allowed, SUBMITTED, VALIDATING, EVALUATED, RESULT_READY, and terminal failure states.
- Never mark an attempt successful when server validation or submission failed.

## 7. Canonical scoring, evaluation, results, and ranking

### 7.1 One versioned scoring contract

Android and backend must consume the same canonical exam/stage/paper/section scoring contract. Backend evaluation is authoritative. Avoid separate hardcoded rule sets and silent fallbacks in Kotlin and Node.js.

The contract must define:

- Positive, negative, zero, partial, bonus, and dropped-question marks.
- Single-answer and exact-set multiple-select evaluation.
- Numerical parsing, allowed tolerance, decimal precision, and rounding boundary.
- Optional-section selection limits and attainable maximum marks.
- Unanswered, invalid, duplicate, missing, and malformed answer behavior.
- Section/paper aggregation and result normalization.
- Ranking eligibility and score handoff.

Missing exam identity, missing section policy, incomplete answer keys, non-finite values, or unsupported scoring rules must fail closed. Never default a missing exam to NEET or an absent section to +4/-1.

### 7.2 Result engine

Generate attempt, section, paper, stage, and overall results where the exam pattern requires them. Preserve raw score, maximum attainable score, normalized score if explicitly defined, correct/incorrect/unattempted counts, bonus/dropped adjustments, time, accuracy, and policy version.

Support result publication, authorized answer review, correction/revision history, and historical result retrieval. Descriptive evaluation must have a separate review/moderation workflow; do not pretend an objective scoring function has graded a subjective response.

### 7.3 Ranking and merit

- Separate test leaderboard, exam-specific practice ranking, platform percentile, and official authority merit/rank.
- Never label an app-computed rank as official national AIR or an authority-issued rank.
- Enforce exam/stage/cycle isolation; one exam's qualifying count must never unlock another exam's ranking.
- Define eligible test types, first qualifying attempt or repeat-attempt policy, score normalization, ranking weight, privacy filters, ties, competition rank, percentile denominator, and tie-breaking from a verified policy.
- Keep XP, streaks, badges, and league points separate from competitive exam score.
- Use server-authoritative transactions/idempotency for ranking updates.
- Query failure must produce an explicit unavailable/error state, never rank zero. A missing rank must not be converted to rank one.
- Any historical backfill requires dry-run, provenance, answer-key validation, pagination/resume, duplicate protection, rollback plan, and separate approval.

## 8. Universal AI gateway and provider architecture

The user-defined target is one reusable Universal AI Gateway. OpenRouter is the initial main gateway because its OpenAI-compatible interface can route to multiple models by model ID. Keep provider adapters so direct APIs and local models can be added without duplicating business logic. Existing Gemini capabilities and PDF-specific Gemini pipeline work must be reconciled into this shared provider architecture rather than discarded.

Logical flow:

    APP
      → AUTHENTICATED BACKEND
      → UNIVERSAL AI GATEWAY
      → TASK CLASSIFIER / POLICY CHECK
      → MODEL ROUTER
      → PROVIDER ADAPTER
      → RESPONSE NORMALIZER
      → SCHEMA / QUALITY VALIDATOR
      → REQUESTING DOMAIN SERVICE

### 8.1 AI modules

- Provider registry and enable/disable controls.
- Model registry, capability metadata, context limits, and availability.
- Provider adapters for OpenRouter, direct provider APIs, and local inference.
- Task classifier and model router.
- Prompt/template version registry.
- Context builder and retrieval boundaries.
- Structured output parser and schema validator.
- Retry, timeout, rate-limit, and provider failover policies.
- Usage/token/cost capture from actual provider metadata.
- Per-user, per-exam, per-task, and global budgets/quotas.
- Request deduplication and safe caching.
- Audit logs, latency/error monitoring, and provider health checks.

### 8.2 Security and cost controls

- Never embed provider secrets in the APK, client configuration, ordinary preferences, readable Firestore documents, logs, or URL query parameters.
- Admin credentials remain server-side in an approved secret manager.
- If user-provided keys are supported, use one canonical UID-scoped encrypted key store with Android Keystore protection and enforce access server-side as appropriate.
- Enforce quotas atomically on the server.
- Never fabricate usage or cost when a provider does not supply it; label unknown values.
- Route simple tasks to cost-efficient models and complex vision/reasoning tasks to capable models based on tested quality and cost.
- Cache only when privacy, freshness, and answer correctness permit it.

### 8.3 AI task families

PDF analysis, OCR/vision interpretation, question extraction, syllabus mapping, topic discovery, question generation, answer generation, explanation, independent answer validation, doubt solving, study planning, performance insight, classification, translation, and search assistance.

Each task needs a versioned input/output schema, allowed context, model capability requirements, budget, validation policy, and failure behavior.

## 9. Universal PDF and content-intelligence pipeline

One shared exam-aware pipeline serves student and Admin uploads. Preserve original PDF bytes unchanged and keep extracted artifacts separately linked to original page numbers and coordinates.

### 9.1 Input types

Text PDFs, scanned PDFs, image-only PDFs, mixed PDFs, multi-page papers, books, notes, question banks, diagrams, tables, charts, equations, and supported image uploads.

### 9.2 Extraction requirements

- File validation, metadata, page count, MIME/size limits, checksum, and safe storage.
- Text extraction, OCR, reading order, and page-level provenance.
- Image and diagram extraction with labels, captions, nearby text, and question relationships.
- Table structure, chart/graph axes and values, mathematical/chemical equations, formulas, symbols, and page coordinates.
- Question/option boundary detection and association of visuals with the correct question.
- Confidence and uncertainty reporting, missing-content detection, duplicate detection, and extraction report.
- Original file preservation, extraction versioning, retry/reprocessing, and failed-page tracking.

### 9.3 Offline-first extraction and AI fallback

1. Validate and store the original document.
2. Run available offline/local extraction and OCR.
3. Assess per-page/per-content completeness and confidence.
4. Route only failed or uncertain pages/regions to an appropriate vision-capable AI provider when possible.
5. Reconcile text and visual context while preserving page provenance.
6. Validate extracted structure and flag uncertain content.
7. Create a private review/import record; do not auto-publish.

A failed extraction must produce an actionable failure report and zero published questions. No dummy/mock questions or fabricated content are permitted.

### 9.4 Syllabus discovery and provenance

Use the canonical exam/stage/paper/section route resolver and deterministic question-generation planner already present in the repository as the baseline to audit and reuse. PDF analysis may discover candidate topics but cannot replace the canonical planner or silently change official syllabus.

Out-of-syllabus material is retained under explicit non-official classifications. Official syllabus promotion requires authoritative source verification or a clearly labelled Admin-approved non-official extension.

## 10. Universal question-generation and validation pipeline

    SOURCE / PDF
      → EXTRACTION WITH PAGE PROVENANCE
      → EXAM / STAGE / PAPER / SECTION ROUTING
      → SYLLABUS AND TOPIC MAPPING
      → DETERMINISTIC DISTRIBUTION PLANNER
      → QUESTION GENERATION
      → ANSWER AND EXPLANATION
      → STRUCTURAL / SCHEMA VALIDATION
      → INDEPENDENT ANSWER VALIDATION
      → SOURCE / AMBIGUITY / DUPLICATE REVIEW
      → ADMIN APPROVAL
      → PRIVATE OR PUBLISHED TEST

### 10.1 Generation configuration

Exam, year/cycle, stage, paper, section, subjects, chapters, topics, number of questions, question types, difficulty distribution, language, source, timer, duration, scoring policy, provider/model, budget, and approval requirements.

### 10.2 Ten-layer quality model

1. Structural/schema validation.
2. Question completeness.
3. Answer-key correctness.
4. Explanation consistency.
5. Subject/scientific accuracy.
6. Source and page provenance.
7. Ambiguity and multiple-valid-answer detection.
8. Duplicate and near-duplicate detection.
9. Exam-pattern and syllabus compatibility.
10. Independent final review and approval.

These are required validation layers in the target architecture, not a claim that ten automated checks currently exist or can prove correctness without human/source review.

### 10.3 Question lifecycle

DRAFT → PROCESSING → GENERATED → VALIDATION_PENDING → VALIDATED / REVIEW_REQUIRED / REJECTED → APPROVED → PUBLISHED → ARCHIVED.

Student/Admin uploads create private review/import records. Student-facing tests appear only after the configured validation and explicit publication workflow. Failed extraction, malformed output, missing answers, unsupported routing, or incomplete question distribution must publish zero questions.

## 11. Student learning and study system

- Student dashboard and exam-specific profile.
- Personal study plan and configurable daily/weekly targets.
- Subject/chapter/topic progress and syllabus completion.
- Practice history, saved questions, bookmarks, and personal notes.
- Mistake notebook and incorrect-answer review.
- Revision scheduler and spaced-revision support.
- Weak/strong topic analysis based on sufficient verified attempt data.
- Previous-year question library and source attribution.
- Daily practice and learning recommendations.
- Learning streaks, achievements, and progress summaries.
- Multi-exam student profile without mixing exam histories or ranking eligibility.

Recommendations must be explainable, privacy-aware, and based on actual data. Do not invent progress when data is missing.

## 12. Doubt-solving and educational search

Input types: text, question, image, screenshot, diagram, formula, and supported PDF page.

Workflow: identify exam context → extract question/context → route to suitable AI model → retrieve permitted source material → generate explanation → validate where possible → display answer and provenance.

Functions include step-by-step solution, option-wise explanation, concept/formula explanation, related topics, follow-up conversation, saved doubts, history, feedback, and similar practice.

Google/YouTube educational discovery is a separate search integration: show real result title, thumbnail, channel/source, URL, and available metadata. Keep external search results distinct from AI-generated explanations and do not imply a video was verified when it was not.

## 13. Community and sharing

Separate private student data from public/shared content.

Community capabilities: posts, comments, likes/upvotes, reports, moderation, shared questions, shared tests, search, owner editing/deletion, and visibility controls.

- Private tests and uploaded material are private by default.
- Sharing requires an explicit action and defined audience/permissions.
- Public payloads contain only approved question content and permitted media.
- Correct answers, private explanations, protected keys, internal validation data, and private source files must not leak.
- Enforce ownership and immutable creator/uploader identity server-side.
- Moderation and abuse reports require audit trails.

## 14. Admin and content operations

Admin modules:

- Dashboard and system health.
- Exam registry, stages, papers, sections, and syllabus.
- Question bank, source review, question approval, and publication.
- Test creation, scheduling, publishing, and archiving.
- AI providers, models, prompts, budgets, quotas, and usage.
- PDF upload, extraction queue, failed-page reports, and review.
- Student/user administration and roles.
- Community moderation and reports.
- Result correction/revision and ranking operations.
- Analytics, notifications, configuration, audit logs, and support.

Roles may include Super Admin, Exam Admin, Subject/Paper Manager, Question Creator, Question Reviewer, AI Manager, Community Moderator, Support Admin, and Read-Only Auditor. Exact permissions must be defined in a backend-enforced permission matrix; hiding a UI control is not authorization.

## 15. Android client architecture and screens

### 15.1 Technical layers

Presentation (Jetpack Compose) → ViewModels/StateFlow → use cases/domain → repositories → Firebase/backend and Room/local sources.

Use Kotlin coroutines/Flow, clear repository boundaries, typed state, lifecycle-aware collection, dependency injection consistent with the existing project, and testable domain logic. Reuse existing screens and navigation rather than creating duplicate flows.

### 15.2 Student screen groups

- Splash, welcome, login, registration, verification, password recovery.
- Home/dashboard, exam selection, exam overview, stage/paper selection.
- Syllabus, subjects, chapters, topics, test series, test details, instructions.
- Test player, question palette, section navigation, review, clear response, submission confirmation.
- Result, answer review, solutions, subject/chapter analysis, ranking, attempt history.
- AI assistant, PDF upload, extraction status, question review, doubt solver, search/video results.
- Study material, question bank, bookmarks, notes, mistake notebook, community, shared tests.
- Profile, exam preferences, language, settings, privacy, security, help, logout.

### 15.3 UI states and accessibility

Every asynchronous screen needs loading, empty, success, recoverable error, blocked/permission, offline, and retry states as appropriate. Support English/Hindi/bilingual content where configured, scalable text, screen readers, contrast, touch targets, and responsive layouts. A screen existing in source does not prove its full workflow is wired.

## 16. Backend service boundaries

Logical modules/services:

- Authentication and session.
- User profile and role/permission.
- Exam registry and policy.
- Stage, paper, section, and syllabus.
- Question bank and protected answer keys.
- Question review and publication.
- Test configuration and delivery.
- Attempt, timer, and submission.
- Canonical scoring and evaluation.
- Result and ranking.
- AI gateway, provider adapters, and usage.
- PDF ingestion, extraction, and background jobs.
- Question generation and validation.
- Learning analytics and recommendations.
- Doubt solving and educational search.
- Community and moderation.
- Storage and file access.
- Notifications.
- Admin configuration.
- Audit, monitoring, and reporting.

All client-originated input must be validated and authorized server-side. Server-authoritative scores, rank, eligibility, roles, publication, and answer-key access must not be writable by ordinary clients.

## 17. Database, storage, and synchronization

### 17.1 Database identity

The repository contains evidence of multiple Firestore access patterns and historical database-ID concerns. Before changing configuration, inspect the current Firebase project, Firestore initialization helpers, direct SDK call sites, backend initialization, deployed environment, and existing data. Select and document the intended primary named database only after that reconciliation. Do not silently fall back to the default database or migrate data as part of an unverified change.

### 17.2 Logical data domains

- Identity: users, profiles, roles, permissions.
- Registry: exams, cycles, stages, papers, sections, policies.
- Syllabus: subjects, chapters, topics, versions, source packets.
- Questions: questions, versions, options, protected answer-key references, explanations, media, provenance.
- Tests: test definitions, templates, configurations, publication.
- Attempts: attempt sessions, responses, submissions, immutable version references.
- Evaluation: scores, section/paper results, result revisions.
- Ranking: eligibility, leaderboard entries, percentile/merit snapshots.
- AI: providers, models, requests, usage, budgets, prompt versions.
- PDF: original documents, pages, extraction jobs, extracted artifacts, reports.
- Learning: progress, bookmarks, notes, revisions, mistakes, recommendations.
- Community: posts, shared tests, comments, reports, moderation.
- Operations: notifications, configuration, audit logs, job status.

This is a logical domain map, not a finalized Firestore collection schema. Exact collection paths, fields, indexes, transaction boundaries, and relationships must be reconciled with existing source and security rules before implementation.

### 17.3 Storage domains

Use access-controlled logical paths for profile media, exam assets, question media, original PDFs, extracted images, private student files, study material, community media, AI-generated assets, and temporary processing. Enforce owner/role, MIME, size, retention, and deletion policies. Original PDFs remain unchanged.

### 17.4 Room and cloud synchronization

Room supports local cache and permitted offline state. Define schema versions, migrations, pending response queues, sync status, conflict handling, retry, and recovery. Never use destructive migration as a convenience fallback. Historical Room schemas not exported must be treated as a data-safety blocker until a safe supported upgrade path is proven.

## 18. Security, privacy, and governance

- Authentication and server-enforced role authorization.
- Firestore and Storage least-privilege rules.
- Private-by-default user content and ownership checks.
- Protected answer keys and server-only scoring.
- Secure provider key storage and secret management.
- Input validation, rate limits, request size limits, and abuse controls.
- Encryption in transit and appropriate encryption at rest.
- No secrets, answer keys, private documents, or sensitive payloads in logs.
- Audit logs for admin, publication, scoring, ranking, and permission changes.
- Data minimization, retention, export, and account deletion workflows.
- Backup, restore, incident response, and access review.
- Safe handling of uploaded files and untrusted PDF/model content.

## 19. Notifications, search, analytics, and configuration

### Notifications
Test availability/reminders, result publication, authorized solution release, ranking updates, PDF processing, question review, community replies, announcements, and security alerts. Store preferences and delivery status; prevent duplicates.

### Search and discovery
Search exams, stages, papers, subjects, chapters, topics, questions, tests, study material, community, saved resources, and educational videos. Apply permissions before returning results; support filters, sorting, pagination, suggestions, and recent searches.

### Analytics
Track real test participation, completion, subject/topic performance, system health, AI usage/cost where supplied by providers, PDF job outcomes, and error rates. Separate operational metrics from student-facing claims; never fabricate counts or costs.

### Feature/configuration management
Versioned controls for exam availability, feature flags, provider/model routing, quotas, PDF settings, approval requirements, test availability, ranking availability, maintenance mode, languages, and minimum client versions. Every privileged configuration change must be authorized and audited.

## 20. Reliability, performance, and operations

- Background job queues for PDF, OCR, AI generation, notifications, and other long-running tasks.
- Bounded retries, timeouts, idempotency, dead-letter handling, and resumable job status.
- Pagination, indexes, query profiling, caching where safe, and image optimization.
- Upload/download resumption and cleanup of temporary artifacts.
- Monitoring for API latency, errors, queue depth, provider availability, extraction failures, and database health.
- Structured logs with redaction; alerts for security and data-integrity failures.
- Backup/restore drills and documented recovery/rollback procedures.
- Capacity and load testing for exam-day traffic and concurrent submissions.

## 21. Testing and quality assurance

Required layers:

- Kotlin/Android unit and domain tests.
- Compose UI and navigation tests.
- Backend unit and callable validation tests.
- Firestore and Storage Emulator rules tests.
- Database and Room migration tests using real SQLite paths.
- AI provider, schema, failure, quota, and security tests.
- PDF golden fixtures for text, scan, mixed, diagram, table, chart, formula, corrupt, and prompt-injection cases.
- Question extraction/generation/answer validation and provenance tests.
- Exam policy and scoring golden vectors shared by Kotlin and Node.
- Timer, offline recovery, concurrent submission, idempotency, and ranking tests.
- Security, privacy, regression, performance, and end-to-end student journey tests.

For every test run record exact commit SHA, discovered/executed/skipped/failed counts, command, environment, CI run/job, and outcome. A compile or APK build does not override a failed unit suite. A PASS applies only to the exact tested SHA.

## 22. Release and deployment architecture

Release gates include verified exam policy for enabled exams, safe database identity, migrations, answer-key security, authorized test publication, server-authoritative scoring, ranking correctness, critical Android/backend flows, Firestore/Storage rules, regression, monitoring, backups, and rollback readiness.

Deployment and production data changes require separate approval. Keep documentation updates isolated from application changes. Do not declare launch-ready while critical workflows or security/data-safety gates remain open.

## 23. Repository integration and change governance

- main is the integration source of truth and must not receive unapproved application-code changes.
- Refresh branch heads and exact commit SHAs before each comparison or port.
- Inventory all branches, commits, changed files, dependencies, schemas, APIs, security rules, and tests.
- Classify branch content as KEEP, selectively PORT, or OBSOLETE; do not merge a divergent branch wholesale.
- Port reviewed files/functions only after call-site, schema, API, security, and regression reconciliation.
- Preserve existing working UI, attempts, user data, test history, private vault, and contracts.
- Keep an issue register with ID, file, line, evidence, severity, status, proposed fix, and test proof.
- Create checkpoints with before/after SHA, files changed, test results, and remaining work.
- No production deployment, destructive migration, or production-data mutation without separate explicit approval.

## 24. Known repository audit findings and implementation status

The following are drawn from repository documentation and audit snapshots. Refresh exact source and branch state before acting; historical audit records are not proof of current behavior.

### 24.1 Documented implementation/verification evidence

The main-branch implementation tracker records, for its cited commits and test runs:

- Server-side callable submission and protected private answer-key separation, with public tests not exposing correctAnswer/explanation.
- Firestore rules protecting server-authoritative score/rank/admin/XP fields.
- Shared answer-key validation and exam-specific eligibility helpers.
- Exam-specific qualifying counts and client fallback isolation.
- Population-based percentile functions and leaderboard-related logic.
- Private test ownership checks and community creator/uploader immutability.
- Bounded community upvote deltas.
- Storage MIME/size restrictions and admin-only PDF access.
- Backend unit-test and Firestore Emulator test runs, plus Android unit/migration test evidence.

These are historical evidence recorded in the tracker. Re-run relevant tests on the exact current main SHA before treating them as current release gates.

### 24.2 Open or previously documented blockers

- Room versions <=14 lack exported historical schemas; destructive fallback risk is documented. Versions 15→18 have separately recorded migration tests, but this does not resolve unknown legacy schemas.
- Firestore database identity and direct SDK initialization call sites require complete reconciliation before changing the primary database.
- All-exam official policy coverage remains partial; six registry exams were reported without a complete official source packet/stage-paper mapping in the audit snapshot.
- NEET, JEE Main, JEE Advanced, CUET, CTET, UPSC, SSC CGL, and RRB ALP registry/policy mismatches were documented in the all-exam audit and require current-source review.
- Canonical Android/backend scoring parity, missing-policy fail-closed behavior, optional-section maximum-score calculation, numeric validation, decimal rounding, and ranking handoff require golden-vector verification.
- Ranking audit documented risks involving score normalization, arbitrary ranking weights, transaction boundaries, rank-zero fallback, retry-to-rank-one behavior, and property-order-sensitive idempotency comparison. Verify each against current source before remediation.
- Student/Admin AI provider connector, secure key boundaries, atomic quota, provider usage accounting, real PDF/OCR fallback, and end-to-end review/publication remain subject to implementation and verification.
- CBT UI/runtime behavior must be verified on the current player and current base branch.
- No complete 25-exam official-policy or launch-readiness claim is made here.

## 25. Development workstreams and acceptance order

1. Refresh main and all branch heads; reconcile the exact 25 exam IDs and current architecture sources.
2. Complete exam registry, stage/paper/section mappings, syllabus versions, and authoritative policy packets.
3. Finalize canonical scoring-policy schema and shared Kotlin/Node golden vectors.
4. Complete test/attempt/timer/submission and server-authoritative result contracts.
5. Close ranking normalization, eligibility, transactions, idempotency, privacy, and failure-state gaps.
6. Reconcile Firestore identity, Room schema/migrations, storage paths, indexes, and rules.
7. Consolidate secure Universal AI Gateway and provider adapters, then quotas and real usage metadata.
8. Complete real PDF/OCR/layout extraction, page provenance, AI fallback, and no-fallback-on-failure guarantees.
9. Complete question generation, ten-layer validation, human review, and publication workflow.
10. Complete student learning, doubt/search/video, community, notifications, and analytics.
11. Complete Android screen/navigation/state coverage and Admin permission matrix.
12. Run full security, emulator, migration, scoring, PDF, AI, regression, performance, and end-to-end suites.
13. Review exact SHA, CI evidence, release gates, backups, monitoring, and rollback before any release decision.

## 26. Status vocabulary

| Status | Meaning |
|---|---|
| VERIFIED COMPLETE | Evidence and required tests pass on the named commit |
| IMPLEMENTED BUT NOT VERIFIED | Code exists; required verification is incomplete |
| PARTIALLY IMPLEMENTED | Only part of the workflow exists |
| NOT IMPLEMENTED | No implementation evidence found |
| BLOCKED | A known dependency, safety, or correctness issue prevents completion |
| UNVERIFIED | Evidence is insufficient; do not assume pass or failure |

## 27. Architecture maintenance record

Update this README's architecture section when a reviewed architecture decision changes. Record the date, exact main SHA, changed scope, evidence links, and whether the change is proposed, implemented, or verified. Keep detailed audit evidence in the linked tracker and reports; this README is the public architecture overview and status entry point.

---

# Existing README content (preserved below)

# AK TEST SERIES

AK TEST SERIES is an Android exam-preparation app project focused on NEET UG first, with a reusable foundation intended to support additional competitive examinations. The repository contains Android application code, Firebase-backed services, and a Node.js Cloud Functions backend.

> **Project status: Active development — not represented as production-ready.** The presence of a screen, class, service, or plan does not by itself mean that the complete user journey is working.

## What is present in the codebase

The current repository includes the following implemented components. Their presence is based on source and project documentation; it is not a claim that every feature has passed a current end-to-end device test.

- **Android application:** Kotlin, Jetpack Compose, ViewModel/Flow-based state, and an MVVM-style structure.
- **Local data layer:** Room database entities, DAOs, repositories, and migration code for local app data and test-related records.
- **Firebase integration:** Firebase Authentication, Cloud Firestore, Cloud Functions, Cloud Messaging-related code, and Firebase App Check integration.
- **Exam and question foundations:** Exam registry/configuration code, question models, question validation, duplicate-detection logic, and exam-specific scoring components.
- **Test and attempt foundations:** Student test screens and attempt-related code, including server-callable submission/scoring paths.
- **Results and ranking foundations:** Backend scoring and ranking functions, eligibility logic, and related Android state/UI code.
- **Admin and community areas:** Admin dashboard/management screens, community post and test-sharing code, and private-test/vault-related components.
- **AI integration foundations:** Gemini-related Android/backend integration code and AI-related configuration/components. Provider routing, secure key handling, quotas, and complete Student/Admin workflows still require integration and verification.

## Important limitations

The full NEET UG journey — sign-in, test discovery, start, question navigation, answer saving, timer recovery, submission, authoritative result, solutions, and eligible ranking — must be verified as one complete flow before the app is described as launch-ready.

Repository audits and development trackers record open work, including:

- Firebase project and named Firestore database identity must be confirmed before changing or migrating production data.
- Some Admin upload/publish paths are not consistently wired end-to-end.
- Student-generated tests and community sharing need private-by-default behavior and enforceable recipient access controls; answer keys and explanations must not be exposed publicly.
- AI entry points need a single secure, policy-controlled backend path; direct client-side provider calls and key handling require review.
- Room database upgrade paths and legacy data-loss risks need further validation.
- Exam patterns and scoring rules must be checked against current authoritative exam sources. A registry entry is not proof of current official accuracy.
- PDF/OCR extraction and question generation are not claimed as a reliable, launch-ready workflow.
- Ranking must use verified eligible attempts; a displayed ranking component is not proof of official national rank.

## Technology

- **Android:** Kotlin, Jetpack Compose, Android Gradle build system
- **Architecture:** ViewModels, Kotlin Flow/Coroutines, repositories
- **Local persistence:** Room
- **Cloud/backend:** Firebase Authentication, Cloud Firestore, Firebase Cloud Functions
- **Backend runtime:** Node.js
- **Quality checks:** Android tests, Node.js tests, Firestore rules tests, and GitHub Actions workflows are present in the repository. Test results must be checked against the exact commit being evaluated.

## Development and verification

The project is under active audit and integration. Work is organized around small, reviewable changes, reuse of existing implementations, security and data-safety checks, and explicit verification.

Do not treat historical test reports or a successful build as proof that every Student/Admin workflow works. For a release decision, run the relevant tests and verify critical flows on the target build and environment.

See the repository's existing development and audit records:

- [Master implementation tracker](app/MASTER_IMPLEMENTATION_TRACKER.md)
- [Branch reconciliation and integration plan](docs/BRANCH_CODE_RECONCILIATION_AND_INTEGRATION_PLAN.md)
- [Universal PDF intelligence architecture](docs/UNIVERSAL_PDF_INTELLIGENCE_ARCHITECTURE.md)
- [Community development plan](docs/community/COMMUNITY_DEVELOPMENT_PLAN.md)
- [CI run history snapshot](docs/CI_RUN_HISTORY_2026-11-13.md)

## Current release position

**Not yet declared launch-ready.** The project should be considered in development until the critical Student and Admin flows, security boundaries, data migrations, exam configuration, and regression checks are verified on the intended release commit.
