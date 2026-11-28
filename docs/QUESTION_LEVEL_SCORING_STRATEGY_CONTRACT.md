# Question-Level Scoring Strategy Contract

Status: implementation draft; not approved for production grading.

## Purpose

A versioned scoring strategy evaluates each question using its own explicit, verified rubric. The generic evaluator is separate from `QuestionEntity` and is not wired into the production attempt submission path. Existing NEET UG 2026 and JEE Main 2026 binary scoring paths remain unchanged.

## Strategy identifiers

- `BINARY_MARKS`: exact correct-answer comparison; awards configured positive marks or applies configured negative penalty.
- `EXACT_RESPONSE_TABLE`: looks up a normalized response in an explicit response-to-marks table.
- `MULTI_SELECT_PARTIAL_TABLE`: treats a selected option set as order-independent and looks up the exact set in an explicit rubric. Partial credit is represented by a rule, not inferred from a formula.
- `NUMERIC_TOLERANCE`: compares a finite numeric response with the verified answer using an explicit non-negative tolerance.

Unsupported strategies, missing rubrics, duplicate question IDs, duplicate normalized response rules, invalid numbers, and answer IDs not belonging to the test must fail closed.

## Question and rubric shape

The current in-memory API uses the following conceptual fields:

- Question: `id`, `strategyId`, `maxMarks`, optional `positiveMarks`, `negativeMarks`, `correctAnswer`, and `rubric`.
- Rubric: `schemaVersion: 1`, `rules[]`, `unmatchedMarks`, `unansweredMarks`, and optional `tolerance`.
- Rule: `response`, `awardedMarks`, and `outcome` (`CORRECT`, `PARTIAL`, or `WRONG`).

All marks must be finite and remain within the question's configured maximum bounds. Table-driven strategies require at least one explicit rule. Unmatched and unanswered values must be explicit/defaulted as documented by the schema. A strategy does not invent missing marks.

## Illustrative fixture only (not an official exam rule)

A hypothetical 4-mark multi-select question could define:
- response `A,B` -> 4 marks, CORRECT
- response `A` -> 2 marks, PARTIAL
- response `A,C` -> 1 mark, PARTIAL
- unmatched response -> -2 marks, WRONG

These values demonstrate the data structure only. They must never be copied into a JEE Advanced policy without checking the exact question-type instructions in the applicable official Information Brochure and paper.

## Result fields

The evaluator returns question counts, positive marks earned, negative penalty, raw score, final score, and maximum score. Positive and negative components are accumulated separately. `finalScore` currently clamps a negative raw total to zero; this is a provisional display convention and must be reconciled with each exam's official score-reporting policy before production integration.

## Production activation requirements

1. Load rubrics only from a server-owned, versioned, approved policy registry.
2. Bind every test immutably to `examId`, `examYear`, `scoringVersion`, stage, paper, subject, section, question type, strategy ID, and rubric version/hash.
3. Validate rubric provenance (official source URL/title, relevant page/question instruction, verification timestamp, reviewer/approval status).
4. Keep the client from supplying or changing the authoritative marks/rubric during submission.
5. Implement matching Kotlin/Node golden vectors, including malformed inputs and boundary values.
6. Add subject aggregation and exam-specific tie-break rules as separate versioned policies.
7. Integrate only after regression tests prove NEET UG 2026 and JEE Main 2026 scores are unchanged.
8. Keep JEE Advanced inactive until question-specific official scoring instructions, partial-credit behavior, both-paper aggregation, and official tie-break rules are verified for the selected exam year.

## Current limitations

- The generic engine is not yet connected to Cloud Function attempt grading.
- No official JEE Advanced rubrics or marks have been inserted.
- No production Firestore registry has been created or seeded.
- Subject aggregation and tie-break evaluation are not implemented by this module.
- Cross-language parity still requires a shared golden-fixture suite and a successful CI run on the latest commit.

Do not deploy, activate a policy, migrate historical attempts, or recalculate stored scores as part of this draft.
