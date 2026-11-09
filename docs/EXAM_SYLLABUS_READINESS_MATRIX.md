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
- **JEE Main:** SyllabusCatalog contains a legacy JEE chapter/topic dataset. It needs source, year/version, official wording, scope and weightage verification before it can be treated as an authoritative official syllabus.
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
