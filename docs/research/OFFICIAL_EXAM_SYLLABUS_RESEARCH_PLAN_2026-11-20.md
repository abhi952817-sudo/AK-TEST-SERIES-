# Official Exam & Syllabus Research Plan
Date: 2026-11-20
Repository: `abhi952817-sudo/AK-TEST-SERIES-`
Research branch: `research/official-exam-syllabus-audit-2026-11-20`
Base: `integration/ak-test-series-complete-2026-11-20`

## Goal
Create a source-backed, year-specific syllabus and examination-policy catalogue for every exam already registered in AK Test Series. Research must be completed before production configuration is activated.

## Registered exam inventory
The current Android registry contains 25 exam IDs:

1. `neet_ug`
2. `jee_main`
3. `jee_advanced`
4. `cuet_ug`
5. `upsc_cse`
6. `ssc_cgl`
7. `ssc_chsl`
8. `ssc_mts`
9. `ssc_gd`
10. `ssc_cpo`
11. `rrb_ntpc`
12. `rrb_group_d`
13. `rrb_alp`
14. `rrb_technician`
15. `rrb_je`
16. `ctet`
17. `ugc_net`
18. `cat`
19. `ibps_po`
20. `ibps_clerk`
21. `sbi_clerk`
22. `sbi_po`
23. `nda`
24. `cds`
25. `gate`

## Research workflow
For each exam and target year:

1. Identify the exam-conducting authority and the official exam portal.
2. Locate the latest applicable information bulletin, syllabus PDF, notification, corrigendum and public notice.
3. Record publication date, exam year, source URL, source title, document/page references and retrieval date.
4. Extract the complete hierarchy: exam, stage, paper, subject, unit, chapter/topic, subtopic, question type and practical/skill component.
5. Separately extract question count, compulsory/optional rules, section caps, duration, maximum marks, positive/negative marks, language, mode and normalization/ranking method.
6. Compare with the previous year and record additions, removals, renames and policy changes.
7. Cross-check the official syllabus against the relevant official curriculum/textbook only as a secondary curriculum reference. Do not silently equate NCERT textbook chapters with an exam syllabus.
8. Mark each fact as `VERIFIED`, `PARTIALLY_VERIFIED`, `NOT_PUBLISHED_FOUND`, `CONFLICT` or `NEEDS_AUTHORITY_CONFIRMATION`.
9. Create machine-readable JSON only after the source audit is reviewed. Never generate policy data from AI memory.
10. Add golden fixtures and Kotlin/Node parity tests before activating a scoring version.

## Source hierarchy
1. Statutory regulator / examination authority notice, information bulletin, syllabus and corrigendum.
2. Official examination portal and official question-paper / answer-key documents.
3. Official curriculum body (e.g. NCERT) for curriculum mapping, not for overriding exam-specific scope.
4. Secondary education reporting only as a discovery aid; never the final authority for scoring or syllabus facts.

## Authority discovery map
| Exam family | Primary authority / portal to verify |
|---|---|
| NEET UG | NMC / UGMEB for syllabus; NTA / NEET portal for conduct, bulletin and notices |
| JEE Main, CUET UG, UGC NET | NTA and the respective official exam portal |
| JEE Advanced | Joint Admission Board / official JEE Advanced portal |
| UPSC CSE, NDA, CDS | UPSC |
| SSC CGL, CHSL, MTS, GD, CPO | Staff Selection Commission |
| RRB NTPC, Group D, ALP, Technician, JE | Railway Recruitment Boards / official CEN notification |
| CTET | Central Board of Secondary Education / CTET portal |
| CAT | IIM CAT official portal / convening IIM |
| IBPS PO, IBPS Clerk | Institute of Banking Personnel Selection |
| SBI PO, SBI Clerk | State Bank of India Careers |
| GATE | Official GATE portal / organizing institute for the target year |

## Required research artifact per exam
Each exam gets a versioned record with:
- examId, examYear, scoringVersion, authority
- source documents and hashes where available
- stages, papers, subjects, sections, units, topics
- question types and answer formats
- question counts, attempt limits, marks and duration
- language, mode and normalization rules
- syllabus delta against prior year
- source verification and approval metadata
- unresolved questions and test fixtures

## Development sequence
- R1: Research-source inventory and source-verification contract.
- R2: NEET UG and JEE Main full year-specific syllabus extraction.
- R3: Remaining 23 exams, grouped by conducting authority.
- R4: Machine-readable syllabus schema and import validator.
- R5: Admin review/approval workflow and immutable version snapshots.
- R6: Android syllabus browser, progress tracking and AI question constraints.
- R7: Backend scoring registry activation, golden fixtures and cross-platform parity.
- R8: Regression, staging and release review.

## Safety rules
- Research branch only. Do not merge this branch into Main.
- Do not seed production Firestore registries from research notes.
- Do not rewrite existing test records or recalculate historical scores.
- Never mark a year verified merely because a legacy config says `verified=true`.
- A missing official notice is a tracked unknown, not permission to reuse the previous year silently.
- Any source conflict blocks activation until resolved.
