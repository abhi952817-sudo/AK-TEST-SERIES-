# Existing Syllabus Implementation Audit
Audit date: 2026-11-20
Code reference: `feature/universal-exam-policy-engine-2026-11-20`
Research branch: `research/official-exam-syllabus-audit-2026-11-20`

## Executive conclusion
The app has a useful NEET UG 2026 official syllabus transcription and an existing syllabus UI/progress flow worth preserving. However, the current general syllabus resolver is not a verified universal syllabus engine. It contains legacy generic fallbacks, partial hard-coded catalogs and an underspecified Room entity. The next phase must extend the working flow, not rebuild the app, while removing false syllabus claims.

## Existing components to preserve
- `NeetUg2026Syllabus.kt`: versioned NEET UG 2026 unit/topic transcription. It records NMC/UGMEB authority, source URL and version, and explicitly sets unofficial weightage/expected-question values to zero.
- `SyllabusCatalog.kt`: provides a current catalog entry point, chapter detail model, and conversion into Room entities. Its public methods can be retained behind a version-aware repository.
- `SyllabusData` in `Entities.kt`: existing class/subject and progress-facing compatibility layer. It should become a facade over approved syllabus versions, not remain the source of official truth.
- `SyllabusDao`, `DatabaseRepository` and the existing Syllabus Tracker / Completion Dashboard UI: preserve the current UI and user progress where practical.
- `calculateSyllabusProgress` in `HomeTabScreen.kt`: inspect and retain its test/attempt linkage, but make its chapter IDs point to stable versioned syllabus IDs.

## Findings requiring correction

### 1. Syllabus coverage is only partial
`SyllabusCatalog.getChapterDetailsForExam` currently provides:
- NEET UG: a dedicated NEET UG 2026 source-backed catalog.
- JEE Main: a hard-coded chapter list.
- UPSC/CDS/NDA: one shared `upscCdsSyllabus` catalog.
- SSC/CGL: one shared `sscCglSyllabus` catalog.
- JEE Advanced: returns an empty list.
- Most other registered exams: no dedicated catalog.

This does not cover 25 exams independently. NDA must not inherit CDS syllabus, and SSC CHSL/MTS/GD/CPO must not inherit CGL syllabus unless an official source explicitly establishes identical scope.

### 2. Non-NEET catalogs contain unsupported estimates
The `ChapterDetail` model contains `weightagePercent` and `totalQuestionsExpected`. NEET's official catalog correctly sets both to zero because NMC/NTA does not publish chapter-wise weights in that syllabus document. But the JEE Main, UPSC/CDS and SSC CGL lists contain numeric weightage and expected-question counts without per-item official source evidence.
- Treat these estimates as unverified legacy display data.
- Do not use them to generate official chapter weightage, question distribution or rankings.
- Preserve only topic labels that can be reconciled to the target-year official syllabus.
- If later retained as coaching estimates, label them explicitly as estimates and keep them separate from official syllabus facts.

### 3. Resolver uses substring aliases and merges distinct exams
`getChapterDetailsForExam` routes by substring:
- `UPSC`, `CDS` and `NDA` share the same UPSC/CDS list.
- `SSC`, `CGL` and generic `GOVT` share SSC CGL.
- `JEE`/engineering aliases route to JEE Main except JEE Advanced.
This is not safe for year-specific exam policies. Replace with exact `examId + examYear + syllabusVersion` lookup. Display aliases must not determine syllabus identity.

### 4. Generic fallback can present an invented syllabus as real
`SyllabusData.getSyllabusForExam` falls back to static Class 11/Class 12/Dropper lists when no exact catalog is found. Its final `else` branch maps any unmatched subject to `quantChapters`. This can show Quantitative Aptitude chapters for a subject with no mapping and makes unsupported exams look like they have a syllabus.
- Unknown or unapproved exam-year mappings must return an explicit `NOT_CONFIGURED` / empty state.
- Never substitute Class 11/12 NCERT chapters for a government exam's official syllabus.
- Never map an unknown subject to Quantitative Aptitude.

### 5. Room entity is not version/provenance capable
Current `SyllabusEntity` fields are only:
- `id`
- `classLevel`
- `subject`
- `chapter`
- `topics`
- `totalTests`

It lacks first-class `examId`, `examYear`, `syllabusVersion`, `stageId`, `paperId`, `unitId`, `topicId`, `sourceDocumentId`, `sourceUrl`, `verificationStatus`, `approvedAt`, and `supersedesVersion`. The current table name `ncert_syllabus` also conflates NCERT curriculum with competitive-exam syllabi.

Do not modify the live Room schema until a migration plan, stable IDs, backup and migration tests are ready. Prefer an additive new schema/table and a reviewed Room migration over destructive recreation.

### 6. Static catalog sync is mislabeled
`DatabaseRepository.syncNcertSyllabusFromRemote()` calls `SyllabusCatalog.getAllSyllabusEntities()`, which is a local static list conversion, not a remote fetch. The name should be corrected when refactoring, and future remote import must validate source/version and approval state.

### 7. GATE and subject-catalogue exams need a richer model
- GATE 2027 has 30 distinct test papers, revised syllabi, a new Robotics & Automation paper and revised XE/XH/XL optional-section mappings. A single generic "General Aptitude & Technical Core" chapter set is inadequate.
- CUET UG has subject-code-specific syllabus documents; its 2026 official portal lists 37 selectable subjects. Each subject needs its own code, syllabus document and year.
- UGC-NET has many subject syllabi maintained separately by UGC. Store subject code and official syllabus URL, not one generic "Paper 2" syllabus.
- RRB notices are CEN/post-specific; use CEN as source-version metadata.
- Banking/SSC/UPSC stages and papers must remain exam-specific.

## Recommended versioned data model
Do not ship this as a production migration until reviewed and tested. The logical model should separate:

1. `ExamDefinition`: stable examId, authority, canonical name and aliases.
2. `ExamYearDefinition`: examId, examYear, official notice/bulletin, active dates, source hash.
3. `SyllabusVersion`: syllabusVersion, status, authority, source docs, publication date, verification timestamp, approver, supersedes.
4. `SyllabusNode`: stable nodeId, parentId, nodeType (stage/paper/subject/unit/chapter/topic/subtopic), official label, display translations, order, syllabusVersion.
5. `SyllabusMapping`: optional NCERT/class/textbook mappings, explicitly marked as cross-reference rather than official source.
6. `SyllabusProgress`: userId, syllabusVersion, nodeId, state, completedAt, evidence/source of completion.
7. `SyllabusQuestionCoverage`: questionId, syllabusVersion, nodeId, mapping confidence, reviewer status.

## Development approach
1. Preserve the existing NEET tracker and progress UI.
2. Build the versioned syllabus schema and import validator on an isolated feature branch.
3. Import NEET UG 2026 from the existing NMC transcription after source/page audit.
4. Import JEE Main 2027 from the official NTA PDF only after the PDF bytes and every topic are extracted.
5. Model GATE 2027 as 30 paper definitions and import the revised paper-specific syllabi.
6. Add CUET subject-code and UGC-NET subject-code catalogs.
7. Add the remaining exams authority-by-authority from current notices.
8. Add explicit unconfigured states for all exams without approved source data.
9. Migrate user progress by stable node identity only where an exact old-to-new mapping is proven. Do not silently mark newly added or changed topics complete.
10. Add tests for year isolation, exam isolation, source provenance, no fallback, import idempotency, progress migration and unknown mapping behavior.

## Safety
- This is a code audit and design record only.
- No Room schema, production data, Firebase documents or student progress was modified.
- Do not delete the existing syllabus UI or progress data.
- Do not activate estimated JEE/UPSC/SSC weightages as official values.
