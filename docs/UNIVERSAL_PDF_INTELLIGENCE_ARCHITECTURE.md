# Universal PDF Intelligence & Syllabus Discovery
**Target branch:** main  
**Status:** Architecture baseline / implementation not yet complete  
**Last updated:** 2026-11-09

## Product goal
Every PDF submitted by an authenticated student or an Admin enters one shared, exam-aware Gemini analysis pipeline. The pipeline identifies the document, extracts evidence, maps questions to the canonical syllabus, discovers previously unseen topics, and routes verified material to the correct test experience. The same universal prompt contract and exam registry must be used by student and Admin upload flows; do not maintain separate competing prompt implementations.

## Non-negotiable data boundaries
1. **Official syllabus is source-controlled.** A question paper, coaching sheet, student notes, or Gemini inference is not proof of an official syllabus change.
2. **Automatic discovery is allowed; unverified official publication is not.** New material is automatically written to a DiscoveredTopics/candidate index with provenance. It can be promoted into the canonical official syllabus only after an authoritative source is verified (or an Admin explicitly approves a clearly labelled non-official extension).
3. **Never discard out-of-syllabus material.** Preserve it as EXTRA_PRACTICE, EXAM_PATTERN_PRACTICE, UNVERIFIED_TOPIC, or NEEDS_REVIEW, not as an official syllabus node.
4. **No auto-publish of tests.** Student/Admin uploads create private review/import records. Student-facing tests appear only after validation and explicit publication under the existing access rules.
5. **No fake data.** Recent-test cards, chapter counts, and topic counts must be derived from real, unique, approved and published test records.
6. **No fuzzy canonical IDs.** Persist exact exam, stage, paper, subject, chapter, topic and subtopic IDs. Unresolved mapping remains UNKNOWN/NEEDS_REVIEW.
7. **Source evidence is mandatory.** Preserve PDF name, uploader, source/issuer when observable, document version/date, page evidence, extraction method, model/version, confidence and review reasons. Do not invent page references or source metadata.
8. **Security is server-enforced.** Client-side isAdmin checks are UX only. Firestore/Storage rules and, where required, trusted backend functions must enforce role-based writes and status transitions.

## Shared processing flow
PDF upload -> access/size/type checks -> text extraction/OCR -> Gemini document classification -> route-specific analysis -> schema validation -> duplicate/provenance checks -> persist private draft/discovery record -> approval/publication workflow -> live catalog/test queries

### Document routes
- OFFICIAL_SYLLABUS: extract exact hierarchy and source metadata; compare with current version; create a versioned syllabus-change proposal.
- QUESTION_PAPER / PYQ: extract only visible questions and answer-key evidence; map each question to exact canonical syllabus IDs; classify test coverage.
- MOCK_TEST / CHAPTER_TEST / TOPIC_TEST: map actual questions to approved syllabus nodes and calculate observed coverage.
- STUDY_NOTES: index notes separately; do not create tests or official syllabus nodes merely because a term appears.
- UNKNOWN / unreadable / ambiguous: preserve the upload and route to review; never guess.

### Syllabus and topic classification
- OFFICIAL_IN_SCOPE: supported by the current approved official syllabus version.
- OFFICIAL_SYLLABUS_CANDIDATE: appears in a credible official syllabus document but needs source/version validation.
- NCERT_OUTSIDE_OFFICIAL_SCOPE: outside the current NEET/NCERT scope based on a versioned, approved reference.
- EXAM_PATTERN_PRACTICE: relevant to observed exam style but not a verified syllabus addition.
- EXTRA_PRACTICE: useful enrichment material, clearly labelled non-official.
- UNVERIFIED_TOPIC / NEEDS_REVIEW: insufficient evidence or conflicting mappings.

A question-paper topic can be discovered and searchable without being promoted into the official syllabus. For NEET, the currently selected official syllabus version must be explicit; do not assume that all NCERT content is automatically in the current NEET syllabus.

## Test coverage rules
- FULL_CHAPTER_TEST only when the complete approved chapter scope is represented by evidence.
- PARTIAL_CHAPTER_TEST records included topic IDs and separately records not-observed topic IDs.
- TOPIC_TEST, MULTI_CHAPTER_TEST, and FULL_SYLLABUS_TEST require exact canonical mappings.
- PYQ requires verifiable exam/year/source provenance; otherwise classify as source-extracted practice, not official PYQ.
- Count a test once per unique test ID per relevant level. Never infer a full-syllabus test from a cover page or a few questions.

## Student and Admin upload permissions
- Student uploads may create a private upload record, extraction result, topic-discovery candidate, and import draft owned by that student. They must not write canonical syllabus records, public tests, answer keys, or global counters.
- Admin uploads use the same pipeline but may create Admin review drafts. Approval actions must be enforced by Firestore rules or trusted backend validation.
- Admin approval initially means APPROVED_FOR_CATALOG or APPROVED_FOR_TEST_IMPORT; it does not automatically publish a test.
- Public publication is a separate, explicit action after question validation, duplicate checks, answer verification and permission checks.

## Home page: Recent Tests
Build a Recent Tests section from live queries, with filters/cards for:
- Recently Added
- Full Syllabus
- Full Chapter
- Partial Chapter
- Topic Tests
- Multi-Chapter
- PYQ
- Extra Practice
- Exam Pattern Practice

Only records with explicit approved/published status and public/official visibility may appear. Keep private student uploads, pending drafts, rejected records and unverified content out of public lists. Do not hardcode counts or sample cards.

## Implementation phases and acceptance criteria
### Phase 0 — Baseline and branch safety
- Audit main and the Gemini feature branch independently.
- Compare changed files and preserve Ranking/Universal Exam Scoring work; never merge a divergent feature branch wholesale.
- Identify actual upload entry points, exam registry, canonical syllabus source, repositories, admin claims, Firestore/Storage rules, backup/restore and Room schema dependencies.

### Phase 1 — Universal prompt contract
- Consolidate classification, syllabus extraction, question extraction, coverage mapping and topic discovery prompts into one versioned prompt module.
- Use exact exam/stage/subject IDs and the selected official syllabus version.
- Treat PDF contents as untrusted data, not prompt instructions.
- Require strict JSON schema, evidence, confidence, provenance and explicit uncertainty.
- Unit-test document-type separation, out-of-syllabus routing, partial-vs-full coverage and prompt-injection resistance.

### Phase 2 — Shared PDF processing
- Reuse the same pipeline from Student and Admin upload screens.
- Add page-aware extraction and OCR fallback; unreadable pages remain reviewable.
- Apply file-size, MIME, page-count, time, token and API-cost limits.
- Validate Gemini output against a strict typed schema before persistence.

### Phase 3 — Persistence and security
- Audit existing Firestore collections and Storage rules before adding collections.
- Persist upload metadata, private analysis drafts, topic discoveries and syllabus proposals with stable IDs and server timestamps.
- Enforce student ownership and Admin role in server-side rules/functions.
- Keep large PDF binaries in private Storage only if existing Storage policies can enforce access; never place raw PDFs or answer keys in public collections.
- Avoid Room schema changes unless necessary; if required, add migration, schema export, backup/restore support and migration tests together.

### Phase 4 — Canonical syllabus and discovery index
- Resolve exact canonical IDs and deduplicate by normalized identity plus exam/version context.
- Automatically refresh the discovery index from accepted evidence.
- Promote only source-verified official syllabus changes into a new version; preserve prior versions and change history.
- Keep extra practice and exam-pattern topics in separate labelled collections.

### Phase 5 — Test import and validation
- Validate question count, answer key, question type, option count, scoring, duplicates, source evidence and syllabus mapping.
- Derive coverage type from observed mapped questions, not the PDF title.
- Keep drafts private until Admin approval and explicit publication.

### Phase 6 — Recent Tests Home UI
- Query real published records and derive counts from unique IDs.
- Add the test categories listed above with filters for exam, subject, chapter, topic and date.
- Verify no private, pending, rejected or duplicate test leaks into student UI.

### Phase 7 — End-to-end verification
- Unit tests for prompts, JSON parsing, schema validation, mapping and duplicate handling.
- Firestore/Storage rules emulator tests for student/Admin permissions and forbidden status transitions.
- PDF fixtures: official syllabus, PYQ, mock paper, chapter test, mixed-topic paper, OCR-only scan, corrupt PDF and prompt-injection text.
- Verify version rollback, no accidental publication, no fake counts and no ranking/scoring regressions.
- Run Android build, unit tests and backend tests in CI; report actual results, not assumed success.

### Phase 8 — Controlled release
- Integrate only audited Gemini/PDF files into main.
- Do not merge ranking/scoring or unrelated database migrations as collateral changes.
- Review diff, migration safety, rules, CI and app behavior before release.

## Current known state (2026-11-09)
- Main has an existing UniversalAIContext.kt, UniversalPdfPipeline.kt, exam registry and syllabus catalog; they must be audited before replacement.
- A separate feature/universal-gemini-ai-10-phase-2026-11-08 branch contains substantial Gemini changes, but it is highly divergent from main and includes unrelated ranking/scoring, Room schema, backend, UI and rules changes. It is not safe to merge wholesale.
- That feature branch has draft-only Admin PDF routing and analysis work, but student upload integration, secure persistence, authoritative syllabus promotion, Recent Tests UI and full verification are not yet established.
- No claim of successful Android build, emulator rules tests or end-to-end PDF testing is made by this architecture document.
