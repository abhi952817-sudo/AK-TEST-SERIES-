# Phase 3 — Universal Versioned Exam Scoring Contract
Date: 2026-11-20
Branch: `feature/universal-exam-policy-engine-2026-11-20`
Base: `feature/single-admin-security-2026-11-20`

## Audit findings
- The original `UniversalExamRegistry.kt` contained 25 exam entries all marked `verified = true`, with no `examYear` or year-bound scoring version.
- Official NTA 2026 bulletins were located for NEET UG and JEE Main. NEET UG 2026 specifies 180 compulsory questions in 180 minutes, with Physics 45, Chemistry 45, Biology 90, total 720, and +4/-1/0 scoring. The old local entry incorrectly described 200 questions, optional Section B and 200 minutes.
- JEE Main 2026 Paper 1 specifies 75 compulsory questions in 180 minutes, 20 MCQs and 5 numerical questions per subject, total 300, with negative marking in both sections. The old local entry incorrectly described 10 numerical questions with only 5 attempted.
- The local registry is now corrected for these two source-verified 2026 patterns and stores their exam year, scoring version and source metadata. The other 23 entries are marked unverified until official year-specific evidence is added.
- Backend legacy `getMarkingSchemeForExam` and Android `UniversalExamRegistry` still use legacy exam-name/section matching for unversioned saved tests. The new exact-key policy resolver is the separate versioned path.
- Existing legacy unversioned tests continue to use the legacy engine in this phase to avoid silently changing old saved-test results.

## Official 2026 source verification
- NEET UG 2026 Information Bulletin, Chapter 4, pages 21–22: https://cdnbbsr.s3waas.gov.in/s37bc1ec1d9c3426357e69acd5bf320061/uploads/2026/02/202602231394640855.pdf
  - 180 compulsory questions / 180 minutes; Physics 45, Chemistry 45, Biology 90; total 720; +4 correct, -1 incorrect, 0 unanswered.
- JEE Main 2026 Information Bulletin, Chapter 2, pages 11–15: https://cdnbbsr.s3waas.gov.in/s3f8e59f4b2fe7c5705bf878bbd494ccdf/uploads/2025/11/202511021649722475.pdf
  - Paper 1: 20 MCQs and 5 numerical questions per subject; 75 questions, 300 marks, 3 hours; negative marking applies to both sections.
- The local registry tests assert these values and ensure the remaining 23 entries are not marked verified.

## New exact scoring identity
Every versioned policy is resolved by the complete key:

`examId + examYear + scoringVersion + stageId + paperId + subjectId + sectionId + questionType`

Display names and aliases are for presentation only and are never resolution keys.

## Firestore registry
Document path: `Config/exam_scoring_registry`

Schema:
```json
{
  "schemaVersion": 1,
  "status": "ACTIVE",
  "policies": [
    {
      "examId": "example_exam",
      "examYear": "2026",
      "scoringVersion": "v2026-1",
      "stageId": "stage_1",
      "paperId": "paper_1",
      "subjectId": "physics",
      "subjectName": "Physics",
      "sectionId": "section_a",
      "sectionName": "Section A",
      "questionType": "MCQ_SINGLE",
      "positiveMarks": 4,
      "negativeMarks": 1,
      "maxAttemptQuestions": null,
      "status": "APPROVED",
      "source": {
        "url": "https://official.example.gov/exam-bulletin-2026.pdf",
        "title": "Official Exam Bulletin 2026"
      },
      "verifiedAt": "2026-01-10T10:00:00.000Z",
      "approvedBy": "primary-admin"
    }
  ]
}
```

The example is a test fixture only. Do not seed it or copy its marks into production.

## Implemented in this phase
- Node resolver rejects inactive registries, missing or ambiguous exact keys, unapproved policies, non-HTTPS/missing source evidence, invalid marks, and invalid optional-section limits.
- Server validation derives question marks from the approved policy, checks source-provided marks for conflicts, checks unique question IDs and question counts, applies optional-section caps, and recomputes total marks.
- `submitTestAttemptServerSide` uses the versioned registry whenever a non-Community source test declares `scoringVersion`. If the registry is missing or any exact mapping is absent, scoring fails closed. It does not fall back to NEET or a generic marking scheme for a versioned test.
- Trusted Community tests continue using their existing Community share-time validation and question-level marks.
- Unversioned legacy tests retain their existing scoring path until their source metadata and year-specific policies are migrated and verified.
- Kotlin and Node exact-key resolvers have matching unit-test coverage for exact resolution, year mismatch, duplicate policies, inactive/unapproved registry, provenance, marks and attempt caps.

## Activation requirements
1. Verify official year-specific patterns for each exam from the exam authority's current information bulletin/notification.
2. Record the exact source URL, source title, publication/document date where available, verification timestamp and approving owner.
3. Create policies only for fully verified exam-year-stage-paper-section-question-type combinations.
4. Keep an exam/year/version inactive until every required section and question type is covered.
5. Run the same golden fixtures through Kotlin and Node before enabling the policy version.
6. Test full attempts, optional sections, multi-select, numerical tolerance, dropped questions, negative marking, maximum marks and ranking eligibility.

## Safety
- No production scoring registry was created or seeded.
- No existing test documents were rewritten.
- No legacy test score or rank was recalculated.
- No Firebase deployment or production data mutation was performed.


## Additional audit finding: legacy path is not time-bounded

The current submission function routes every non-Community test with a non-empty `scoringVersion` through the new exact policy resolver. A test with no `scoringVersion` still enters the legacy scoring engine. The code does not currently prove that an unversioned test is genuinely historical; therefore the legacy path is not limited to old saved tests by a creation-date cutoff or an explicit approved legacy allowlist.

**Release guard:** New test creation and publication paths must write a valid exam/year/scoring version, and server submission must distinguish explicitly grandfathered historical tests from newly created unversioned tests. Until that gate exists, do not treat an unversioned test as official year-verified scoring or allow it to become a new competitive ranking source merely because the legacy evaluator returns a score. Preserve existing stored scores; do not bulk-recalculate them in this phase.

## Additional audit finding: JEE Advanced requires a richer scoring model

The official JEE (Advanced) 2026 Information Brochure states that both 180-minute papers are compulsory, and that negative marks may apply to some questions; the exact marking scheme is supplied in the instructions for each question paper:
- Official IIT Roorkee brochure: https://jeeadv.ac.in/documents/IBEnglish_2026.pdf (Clauses 9, 17 and 18; printed pages 15, 31–33).
- The brochure also defines ranking across both papers and a tie-break sequence based on higher positive marks, then Mathematics marks, then Physics marks (Clause 22; printed page 43).

The current versioned policy model stores only scalar `positiveMarks` and `negativeMarks`. The current evaluator calculates binary correct/wrong outcomes and cannot represent JEE Advanced multiple-select partial-credit rules, question-specific mark tables, or the exam's year-specific tie-break sequence. A comma-separated answer key is not a substitute for a scoring rubric.

**Release guard:** JEE Advanced must remain unverified/inactive in the versioned registry until the engine supports an explicit scoring strategy and rule data (including selected-correct count, selected-incorrect options, partial-credit bands, zero/negative outcomes), plus subject aggregation and official tie-break handling. Do not reuse a generic `+4/-2` fallback for an official JEE Advanced paper. The same rule-engine audit must be applied to any other exam whose official scheme varies by question type or response pattern.
