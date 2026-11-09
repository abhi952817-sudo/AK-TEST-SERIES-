# Exam-wise syllabus readiness for Universal PDF Intelligence
**Branch:** feature/universal-pdf-prompt-contract-v1  
**Updated:** 2026-11-09

## Important distinction
The prompt engine is universal; the official syllabus data injected into it is exam-specific.
A universal prompt does not make an exam's syllabus authoritative. It can classify any registered exam PDF, but it may claim official in-syllabus coverage only when a versioned, approved syllabus snapshot for that exact exam/stage/paper/subject is supplied.

## Registry audit snapshot
The current UniversalExamRegistry contains 25 configured exam IDs:

neet_ug, jee_main, jee_advanced, cuet_ug, upsc_cse, ssc_cgl, ssc_chsl,
ssc_mts, ssc_gd, ssc_cpo, rrb_ntpc, rrb_group_d, rrb_alp, rrb_technician,
rrb_je, ctet, ugc_net, cat, ibps_po, ibps_clerk, sbi_clerk, sbi_po, nda,
cds, gate.

The existence of an ExamConfig means the app has exam/pattern configuration; it does NOT prove a complete, source-verified syllabus exists.

## Syllabus data observed in the current main branch
- **NEET UG:** Dedicated versioned syllabus module is present: `NEET_UG_2026_NMC_2025-12-22`. This is the only syllabus version currently identified in the project audit as the explicit approved NEET reference for prompt scope.
- **JEE Main:** NTA has published the official **JEE Main 2027 Syllabus** (listed on the NTA documents page in 2026). The existing `SyllabusCatalog.jeeMainSyllabus` is still a legacy dataset and is NOT the approved 2027 module. A separate, source-backed `jee_main / JEE_MAIN_2027` Paper 1 module must be created from the NTA PDF, preserving exact English text, adding reviewed Hindi translations, and excluding Paper 2A/2B content. No official unit-wise weightage should be inferred.
- **JEE Advanced:** It is a separate exam (`jee_advanced`), not an alias or subset of JEE Main. As of this audit, the latest official syllabus located is JEE Advanced 2026; the official 2027 syllabus/brochure has not yet been located. Keep the 2026 document as a clearly labelled reference only (`JEE_ADVANCED_2026_REFERENCE`), not as an approved 2027 syllabus. Its topics and paper structure must never be copied from JEE Main.
- **UPSC CDS:** SyllabusCatalog contains a legacy UPSC_CDS dataset. It needs official-source and version validation.
- **SSC CGL:** SyllabusCatalog contains a legacy SSC_CGL dataset. It needs official-source and version validation.
- **Other registry exams:** No corresponding detailed chapter/topic dataset was found in the inspected SyllabusCatalog. These exams must be treated as syllabus-not-yet-loaded, not as having an inferred or shared syllabus.

The current SyllabusCatalog tests expect JEE legacy weightage values to be positive, while the NEET test explicitly forbids fabricated official weightage. These legacy assumptions require a separate source audit; do not copy weightage numbers across exams or treat model-generated estimates as official.

## Runtime rules
1. Always pass the selected exact examId, stageId, paperId and subjectId.
2. Pass a syllabus version and canonical JSON snapshot only when the exact exam context has an approved snapshot.
3. If no approved snapshot exists, Gemini may classify the document and extract candidate topics, but all syllabus mappings must remain unverified/review-required.
4. Do not fall back to NEET, JEE or another exam's syllabus when the selected exam has no catalog.
5. Keep exam pattern/marking rules separate from syllabus scope.
6. Student and Admin uploads use the same prompt contract; their persistence permissions differ.
7. Topic discovery never changes official syllabus automatically.

## Delivery order
1. Universal prompt contract and exact identity handling.
2. Verify NEET source snapshot and build source-backed syllabus modules exam by exam.
3. Audit JEE, UPSC CDS and SSC CGL legacy catalog content against official sources; correct or downgrade unsupported metadata.
4. Build remaining 21 exam syllabus modules from official notices/documents, preserving each exam's native hierarchy and year/version.
5. Connect the same classifier/discovery/coverage prompts to the shared Student/Admin PDF pipeline.
6. Add strict typed JSON validation, private draft persistence, topic discovery records and admin review.
7. Add published-only Recent Tests UI and end-to-end tests.

## Release gate
No exam is marked syllabus-ready until its official source URL/document, issuer, publication/version date, effective exam year, extraction evidence, hierarchy and review status are recorded and tests confirm exact exam isolation.


## JEE syllabus ingestion and runtime routing decision (2026-11-09)

### Canonical identity: do not collapse the two exams
- JEE Main: `examId=jee_main`; first supported module: `syllabusVersion=JEE_MAIN_2027`, `paperId=paper_1_be_btech`, subjects `physics`, `chemistry`, `mathematics`.
- JEE Advanced: `examId=jee_advanced`; current official source located: 2026 syllabus only. Until the 2027 official brochure is published and verified, the resolver must return `SYLLABUS_VERSION_PENDING` for a 2027 request. A 2026 reference may be used only when the selected test explicitly targets that reference version.
- The UI label "JEE" must resolve to a user choice between Main and Advanced. It must not be a canonical exam ID and must never silently select one.
- JEE Main Paper 2A (B.Arch) and Paper 2B (B.Planning) are distinct paper modules. They must not be mixed into Paper 1 B.E./B.Tech.

### How the syllabus system is expected to work
1. Admin selects exam, year/version, stage, paper and language. For a student PDF, the selected test/exam metadata supplies these IDs; Gemini may suggest a classification but cannot silently replace an explicit selection.
2. The app resolves an exact, approved syllabus snapshot by the full key: exam ID + version + paper ID + subject ID. No substring matching, first-subject fallback, fuzzy mapping or "closest exam" fallback is allowed.
3. The app injects only that resolved snapshot into the universal Gemini prompt. The prompt contract remains exam-neutral; the syllabus data is exam-specific.
4. Gemini returns structured candidate mappings to supplied canonical unit/topic IDs. It may suggest an unknown topic, but it cannot create or approve a canonical syllabus node.
5. Validation checks IDs, source evidence, page references, paper/subject consistency, question coverage and duplicate fingerprints. Missing or conflicting evidence becomes `NEEDS_REVIEW`.
6. Out-of-syllabus but useful questions are retained and routed to `EXTRA_PRACTICE`, `EXAM_PATTERN_PRACTICE` or `UNVERIFIED_TOPIC`; they are not discarded and are not counted as official syllabus coverage.
7. Every upload remains a private draft. Only an authorized Admin review and explicit publish action can make a test public.
8. Recent Tests, chapter test counts and coverage percentages are derived only from real, non-duplicate, published tests and validated question mappings. No syllabus file may contain invented test counts or weightage.

### JEE Main 2027 source record
- Issuer: National Testing Agency (NTA).
- Official listing: NTA JEE Main Documents page, entry "Syllabus 2027" (year listed: 2026).
- Official document: JEE Main 2027 syllabus PDF linked from the NTA document listing.
- Initial ingestion scope: Paper 1 (B.E./B.Tech.) only. The Paper 1 syllabus contains 20 Physics units, 20 Chemistry units and 14 Mathematics units (54 units total); each detailed subtopic must be transcribed from the official PDF with its source page recorded.
- Hindi is an app translation layer, not a claim that the NTA source PDF is Hindi. Preserve the exact English source wording alongside Hindi labels/translations.
- Verification state before the actual JSON/module and tests exist: `SOURCE_IDENTIFIED`, not `APPROVED`.

### Current implementation gap (do not mark as completed)
- The universal prompt contract and unknown-subject preflight exist on this feature branch.
- The old `SyllabusCatalog` still contains legacy JEE chapter data and is not yet wired to a versioned JEE syllabus resolver.
- The complete JEE Main 2027 topic JSON/module, JEE Advanced versioned module, exact resolver, Gemini runtime injection, Admin review/publish flow and end-to-end tests remain pending.
- Until these pieces are implemented and tested, the app must not claim that JEE Main 2027 or JEE Advanced 2027 syllabus matching is live.


## Implementation update after exact-path audit (2026-11-09)

### Main-file conventions inspected
The existing approved NEET source module is:
`app/src/main/java/Com/ak/testseries/exam/NeetUg2026Syllabus.kt`.
It is a Kotlin source module in the exam package, referenced by `SyllabusCatalog.kt`; the project does not currently use a central JSON assets folder for this syllabus catalog. Therefore JEE source data is being added under the same exam package and is not being written into a guessed assets path.

### Added in this branch
- `VersionedSyllabusResolver.kt`: exact key is exam + version + stage + paper + subject. Only a structurally valid snapshot with APPROVED state, reviewer ID and review timestamp can resolve as official scope. No fuzzy or fallback match.
- `JeeMain2027SyllabusIndex.kt`: NTA source-linked Paper 1 unit index, with 20 Physics + 20 Chemistry + 14 Mathematics unit headings, Hindi labels and 1-based PDF page references.
- `UniversalPdfPromptContract.buildSyllabusAwareQuestionGenerationPrompt(...)`: explicit runtime prompt contract requiring a versioned approved snapshot, exact canonical topic IDs, no JEE Main/Advanced mixing, no invented weightage, no hidden reasoning, and no AI publication authority.
- `UniversalExamRegistry.kt`: the bare short label `JEE` is now rejected as ambiguous; exact JEE Main and JEE Advanced names/IDs resolve separately.
- Unit tests cover resolver approval gates, exact exam/version/paper isolation, JEE unit counts, ambiguous alias rejection and the new prompt contract.

### Important status boundary
The JEE Main index is a verified **unit-heading/source-page index**, not yet the full topic/subtopic transcription. It is deliberately marked `SOURCE_IDENTIFIED_NOT_APPROVED`. It must not be injected as a complete official syllabus. Remaining work before approval:
1. Transcribe every official subtopic and experimental/practical item from the PDF, preserving exact English text and page references.
2. Add reviewed Hindi translations for the detailed topics (not just unit headings).
3. Add Paper 2A and Paper 2B as separate modules if/when required; never mix them with Paper 1.
4. Research and load JEE Advanced from its own official brochure/source and exact year. Do not reuse JEE Main units.
5. Register the complete snapshots only after structural tests, source-page audit and human Admin review.
6. Wire the resolver into Admin and Student PDF classification, topic coverage, question generation, persistence and publication gates. The existing PDF pipeline does not yet call this resolver.
7. Add Firestore draft/review persistence, security rules, duplicate checks, and end-to-end tests before enabling live use.

### Build/CI
No workflow run or commit status was returned for the latest feature-branch test commit at the time of this update. Therefore build and unit-test success are **not verified**. Do not merge this branch to main or enable the incomplete JEE module in production.
