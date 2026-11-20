# Phase 3 — Universal Versioned Exam Scoring Contract
Date: 2026-11-20
Branch: `feature/universal-exam-policy-engine-2026-11-20`
Base: `feature/single-admin-security-2026-11-20`

## Audit findings
- `UniversalExamRegistry.kt` currently contains 25 exam entries, all marked `verified = true`.
- The 25 entries contain no `examYear` or year-bound scoring version.
- The existing NEET UG entry describes the older Section A/Section B optional-question pattern and a 200-minute duration. It must not be treated as the current year-specific official pattern without a fresh official bulletin check.
- Backend `getMarkingSchemeForExam` and Android `UniversalExamRegistry` use legacy exam-name/section matching. They are not a shared exact-key policy resolver.
- Existing legacy unversioned tests continue to use the legacy engine in this phase to avoid silently changing old saved-test results.

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
