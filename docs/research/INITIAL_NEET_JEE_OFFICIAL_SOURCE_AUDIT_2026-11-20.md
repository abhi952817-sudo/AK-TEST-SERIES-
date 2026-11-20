# Initial Official Syllabus Source Audit — NEET UG & JEE Main
Research date: 2026-11-20
Branch: `research/official-exam-syllabus-audit-2026-11-20`
Status: Initial source audit; not yet a complete 25-exam research catalogue.

## Executive findings
- NEET UG 2026 has an official NMC/UGMEB syllabus notice dated 22 December 2025. NTA's public notice dated 8 January 2026 confirms the examination is to be conducted strictly on the NMC-finalized syllabus.
- No official NEET UG 2027 syllabus notice was located in the NMC/NTA source searches performed on 20 November 2026. Therefore NEET UG 2026 must not be silently promoted to a verified 2027 syllabus.
- JEE Main 2027 is already represented on the official JEE Main portal: its 2027 syllabus, 2027 information bulletin and 2027 public notices are listed. The official NTA exam page describes Paper 1 as 75 compulsory questions, 300 marks, 3 hours and +4/-1/0. The actual 2027 syllabus PDF still needs full extraction and topic-level comparison before the app registry is updated.
- JEE Main 2026's official NTA notice confirms Paper 1 has 20 MCQs and 5 numerical-value questions per subject, 75 total; incorrect answers are negatively marked in both sections.
- The current app must separate 2026 and 2027 policies. A yearless exam ID or a single static scoring scheme is not sufficient.

## NEET UG 2026 — verified source record
Authority: National Medical Commission, Under Graduate Medical Education Board (UGMEB); examination conducted by NTA.

Primary documents:
1. NMC/UGMEB public notice and attached syllabus, dated 22-12-2025:
   https://nmc.org.in/whats-new/download/920
2. NTA public notice, dated 08-01-2026, confirming the syllabus was finalized by UGMEB/NMC and that NEET UG 2026 will be conducted strictly on that syllabus:
   https://nta.ac.in/Download/Notice/Notice_20260108180635.pdf

Evidence:
- NMC notice states UGMEB finalized NEET UG 2026 syllabus for academic session 2026-27. NMC document lines 45-71.
- Syllabus begins at printed page 2 / PDF text around lines 78 onward.
- Physics includes Units 1–20, ending with Experimental Skills. The final Physics unit lists 18 experiments/activities. NMC/NTA PDF pages 2–6; NTA PDF lines 102-207.
- Chemistry includes Physical, Inorganic and Organic Chemistry, with Unit 20 Principles Related to Practical Chemistry. NMC/NTA PDF pages 7–11; NTA PDF lines 212-261 and 263-386.
- Biology section begins at printed page 12. Units include Diversity in Living World, Structural Organisation, Cell Structure and Function, Plant Physiology, Human Physiology, Reproduction and further units on the following pages. NMC/NTA PDF pages 12 onward; NMC PDF lines 390-426.
- The source PDF has occasional OCR/typographical artifacts. Extraction must be checked against page images before converting every line into canonical topics.

Important scope rules:
- This is the 2026 syllabus only.
- Do not infer a 2027 syllabus from it.
- The 2026 syllabus is not interchangeable with the 2026 examination pattern/bulletin. Syllabus and marking policy must be sourced separately.
- Topic-level import must preserve official wording, while any simplified Hindi explanation belongs in a separate translation/display field.

## NEET UG 2027 — status
Status: `NOT_PUBLISHED_FOUND` in the official source search performed 2026-11-20; this is not a claim that no document can exist elsewhere.
- Searches covered NMC's official notices and NTA's official site for NEET UG 2027 syllabus.
- No matching official NMC/UGMEB syllabus notice was located.
- Next action: re-check NMC What's New and neet.nta.nic.in before enabling 2027 syllabus or scoring configuration.
- Do not use the 2026 syllabus as a verified 2027 syllabus.

## JEE Main 2027 — verified exam structure, syllabus document pending extraction
Official sources:
1. NTA JEE Main exam information page:
   https://www.nta.gov.in/exams/jee/
2. JEE Main official 2027 syllabus listing:
   https://jeemain.nta.nic.in/document/syllabus-2027/
3. JEE Main official 2027 information bulletin listing:
   https://jeemain.nta.nic.in/information-bulletin/
4. Official 2027 documents index:
   https://jeemain.nta.nic.in/documents/
5. Official JEE Main 2027 FAQ PDF:
   https://cdnbbsr.s3waas.gov.in/s3f8e59f4b2fe7c5705bf878bbd494ccdf/uploads/2026/11/202611131752745450.pdf

Verified from NTA exam information page:
- Paper 1 (B.E./B.Tech): 3 hours, 75 compulsory questions, 25 each in Physics, Chemistry and Mathematics, 300 marks.
- Marking: +4 correct, -1 incorrect, 0 unanswered.
- Two sessions: January and April 2027; better score counts.
- The page says it was last reviewed 16 September 2026.

Verified from the official portal listing:
- A separate 2027 syllabus document and 2027 information bulletin are listed.
- The accessible PDF was not successfully extracted in this research pass. Do not claim topic-level 2027 verification until the PDF is read and compared.
- Secondary reports say the 2027 syllabus is unchanged from 2026, but this is only a discovery lead. The app must use the official 2027 PDF as the controlling source.

## JEE Main 2026 — official pattern evidence
NTA public notice dated 31 October 2025:
https://www.nta.ac.in/Download/Notice/Notice_20251101002217.pdf

Evidence:
- Paper 1 has three subjects: Mathematics, Physics and Chemistry.
- Each subject has 20 Section A MCQs and 5 Section B numerical-value questions; total 75.
- Section A and Section B both have negative marking for incorrect answers.
- The notice points candidates to the official 2026 information bulletin for full scheme details.

## NCERT curriculum mapping
Official NCERT syllabus/textbook entry points:
- https://ncert.nic.in/syllabus.php
- https://ncert.nic.in/textbook.php?kebo5=11-12
- Example Chemistry syllabus PDF: https://ncert.nic.in/pdf/syllabus/desm_s_Chemistry.pdf

Use NCERT as a curriculum cross-reference, not as a substitute for the examination authority's syllabus. Preserve distinctions between:
- official exam topic
- NCERT chapter mapping
- app-generated teaching subtopic
- AI-generated practice coverage

## Data-model implications
1. Keep `examId` stable, but make `examYear` and `scoringVersion` mandatory for new verified policies.
2. Syllabus version and scoring version should be independently versioned. A syllabus change does not necessarily imply a marking change and vice versa.
3. Store source provenance per version and, where appropriate, per unit/policy.
4. Store exam-paper structure separately from syllabus topics.
5. Do not let AI create new official topics. AI can propose mappings, but only an approved source-backed import may become official syllabus data.
6. Missing or ambiguous source data must block verification and scoring activation.

## Outstanding work
- Extract all Biology units and the complete NEET UG 2026 syllabus into structured JSON, verifying OCR against page images.
- Obtain and extract the complete JEE Main 2027 syllabus PDF.
- Compare JEE Main 2027 syllabus against 2026 topic-by-topic.
- Verify full JEE Main 2027 marking and paper structure from the complete 2027 bulletin, including Paper 2A and Paper 2B.
- Locate the official NEET UG 2027 syllabus when released.
- Continue the 23-exam authority-by-authority audit.
