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

## Phase 9 — Publication-triggered multilingual translation (2026-11-28)

### Product rule: private uploads are never auto-translated
- A Student private upload is processed only for that Student's own workflow and remains in its detected/source language(s). Do not spend AI tokens translating it merely because it was uploaded.
- An Admin private draft follows the same rule: extraction and review do not trigger public-language translation.
- Translation is triggered only by an explicit, authorized transition into public publication. A preview, save-draft, upload, extraction retry, approval-only transition, or private share must not trigger it.
- The original uploaded PDF and original extracted source-language content are immutable. Translation creates separate derived language variants; it never overwrites the source.

### Publication translation workflow
1. The authenticated owner requests publication and selects/accepts the currently enabled public language set from versioned Admin configuration. Do not hardcode a permanent language list.
2. A trusted backend publication orchestrator verifies ownership, role, content eligibility, moderation/approval, source provenance, and the exact public-language configuration version. Client-side flags are not authorization.
3. The orchestrator resolves the AI identity from the authenticated publisher: Admin publisher -> Admin's authorized server-side provider/model configuration and server-held credentials; Student publisher -> that Student's authorized BYOK/provider/model configuration, retrieved only through the approved secure credential path. Never trust a client-supplied owner UID, role, credential, or arbitrary model override.
4. The same Universal AI Router, provider adapter contract, and versioned Translation Prompt are used for both roles. Only credential/configuration resolution differs. Do not create separate translation implementations for Admin and Student.
5. Translate source content into every enabled public language that is not already present and valid. Detect source language at document/page/question level; preserve bilingual or mixed-language source segments rather than needlessly retranslating them.
6. Validate every variant against the source: question count/order, option count/order, IDs, numbers, units, formulas, symbols, negation, diagrams/labels, tables, and answer-key references must remain aligned. Translation must never generate, infer, alter, or expose answer keys.
7. Persist each language variant as a private publication candidate with source hash, language, provider/model, prompt version, configuration version, timestamps, confidence, validation report and review state.
8. Publish atomically only when all required public languages have passed validation and the existing moderation/publication policy succeeds. If any required translation fails or is uncertain, keep publication pending/blocked for review; do not silently publish a partial language set.
9. Retries are idempotent by source version + target language + prompt version + provider/model configuration. A changed source or language configuration creates a new translation job/version; it does not mutate an already published source or variant.
10. Public reads expose only approved public language variants. Private source PDFs, extraction payloads, credentials, answer keys and unpublished translations remain access-controlled.

### Suggested data contract (audit existing collections before creating anything)
- publicationRequest: contentId, publisherUid, publisherRole, sourceVersion, requestedAt, languageConfigVersion, status, translationJobIds.
- translationVariant: contentId, sourceVersion, sourceLanguage, targetLanguage, translatedPayloadRef, providerId, modelId, promptVersion, confidence, validationStatus, reviewStatus, createdAt.
- publicContent: stable content identity plus only approved language-variant references and publication metadata. Do not copy private source files or answer keys into this public record.
- Store large source/translated assets in access-controlled Storage paths; Firestore stores metadata and references. Exact collection names and paths must be reconciled with the current schema and rules before implementation.

### Required security and failure tests
- Student A cannot read, publish, or translate Student B's private upload.
- A Student publication uses only Student A's authorized provider configuration; an Admin publication uses only the Admin server-side configuration.
- A client cannot impersonate Admin, select another user's credentials, or force a different publisher identity.
- Upload/save-draft/approval-only/private-share never invokes translation.
- Explicit public publication invokes one translation job per missing enabled language, with idempotent retries.
- Provider quota exhaustion, timeout, invalid JSON, OCR uncertainty, translation drift, or validation failure blocks publication without partial public leakage.
- Source PDF and source text remain byte/content-identical after translation and retries.
- Public readers cannot access private source files, extraction drafts, credentials or answer keys.
- Firestore/Storage rules and callable-function tests must prove the above before enabling the workflow.

### Current implementation status
- This section defines the required behavior and integration contract; it is not evidence that publication-triggered translation code, secure student credential retrieval, Firestore schema, Storage paths, or emulator tests are already implemented.
- The current storage.rules snapshot allows PDF writes only to Admins under /LiveTests and /pdfs; a private Student PDF path and owner-only rules are not established by that file. Do not enable Student PDF upload or public translation until the actual upload path, provider-key storage, publication callable and rules are audited together.
- Keep this work independent of scoring, negative-marking, ranking, and answer-key evaluation contracts.

### Credential-boundary clarification
- Student BYOK translation must execute through the Student's device-local Universal AI Router/provider adapter using the UID-scoped Android Keystore-backed credential. The Student API key must never be sent to a Cloud Function, Firestore, Storage, logs, or the publication request.
- The Student device submits only the translated candidate payload plus provider/model provenance and validation metadata to the trusted publication endpoint. The backend independently validates publisher ownership, publication eligibility, required language coverage and payload schema before publishing.
- Admin translation may execute server-side through the Admin's authorized provider/model configuration and Secret Manager-held credentials. Never route an Admin secret through the Android client.
- If the Student's device is offline, the selected provider is unavailable, or its quota is exhausted, publication remains pending; do not silently substitute the Admin model or another user's credentials.
