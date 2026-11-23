# Generic Question-Level Scoring Strategy Contract (v1)

Status: implementation prototype; not production-approved exam policy.

## Purpose and compatibility boundary

The current versioned policy resolver selects scalar positive/negative marks by exact exam identity, year, scoring version, stage, paper, subject, section and question type. The current evaluators still classify answers as correct or wrong. This contract adds a separate question-level strategy API so a question can award a source-approved partial score.

The existing NEET UG 2026 and JEE Main 2026 binary scoring paths remain unchanged: NEET uses its already verified +4/-1/0 policy; JEE Main continues to use its already verified year/section policy. This new API does not silently rewrite those policies or migrate Room data.

JEE Advanced is NOT activated by this prototype. Its official year/paper/question-specific instructions must be transcribed into reviewed question rubrics and approved policy metadata before scoring live attempts.

## Strategy registry

Supported strategy identifiers in v1:

- `BINARY_MARKS`: verified correct answer earns `positiveMarks`; incorrect earns `-negativeMarks`; unanswered earns zero.
- `EXACT_RESPONSE_TABLE`: normalized response is looked up in an explicit rubric table. Intended for response patterns where the awarding rule is a published table.
- `MULTI_SELECT_PARTIAL_TABLE`: selected option tokens are normalized as a sorted, unique set; exact combinations map to explicit marks and outcomes. No inferred partial-credit formula.
- `NUMERIC_TOLERANCE`: a finite numeric response is compared with the verified numeric key using an explicitly supplied non-negative absolute tolerance. Correct/incorrect marks are explicit.

Unknown strategies fail closed. Strategy identifiers are versioned and cannot be selected by student input.

## Question contract

Every strategy input requires:

- `id`: unique question ID within the attempt.
- `strategyId`: one supported identifier.
- `maxMarks`: positive finite upper bound for the question.
- For binary/numeric strategies: positive and negative marks; numeric strategy additionally requires finite `correctAnswer` and rubric `tolerance`.
- For table strategies: `rubric.schemaVersion = 1`, a non-empty `rules` array, explicit `unmatchedMarks`, and optional `unansweredMarks` (defaults to zero in this prototype).

Each table rule contains a response key, an awarded mark value and one outcome: `CORRECT`, `PARTIAL`, or `WRONG`. Duplicate normalized response keys are rejected. Awarded and fallback marks must remain within `[-maxMarks, +maxMarks]`.

For multi-select, response keys such as `A,C` are canonicalized to `A,C` regardless of input order. Duplicate selections and empty tokens are rejected. A response not listed in the table receives only the explicit `unmatchedMarks`; the engine never guesses a partial score from counts.

## Example fixture (illustrative only, NOT an official JEE Advanced marking rule)

```json
{
  "id": "fixture_multi_01",
  "strategyId": "MULTI_SELECT_PARTIAL_TABLE",
  "maxMarks": 4,
  "rubric": {
    "schemaVersion": 1,
    "unmatchedMarks": -2,
    "unansweredMarks": 0,
    "rules": [
      { "response": "A,B", "awardedMarks": 4, "outcome": "CORRECT" },
      { "response": "A", "awardedMarks": 2, "outcome": "PARTIAL" },
      { "response": "A,C", "awardedMarks": 1, "outcome": "PARTIAL" },
      { "response": "C,D", "awardedMarks": -2, "outcome": "WRONG" }
    ]
  }
}
```

These values exist only to test mechanics. They must never be copied into a live JEE Advanced registry.

## Result contract

The evaluator returns total/attempted/correct/partial/wrong/unanswered counts, positive marks earned, negative penalty, raw score, final score and maximum score. `attemptedCount` includes partial outcomes. `correctCount` does not. `finalScore` is clamped to zero for display, while `rawScore` preserves the signed result.

## Trust and activation requirements

1. The server must load scoring strategy and rubric data from an admin-controlled, immutable test/policy snapshot; never trust a student's submitted marks or rubric.
2. Every strategy/rubric must carry schema version, exact examId/year/scoringVersion/stageId/paperId/subjectId/questionType identity, official source URL/title, verification timestamp and approving admin identity at the integration boundary.
3. Question content, answer keys, and scoring rubrics must be integrity-bound to the same versioned test snapshot.
4. Missing, duplicate, unsupported, conflicting, malformed or unverified scoring metadata blocks scoring. No generic fallback is allowed.
5. Subject aggregation and official tie-break rules are separate contracts. This engine does not implement JEE Advanced rank tie-breaks.
6. NEET/JEE Main regression fixtures must continue to pass unchanged.
7. No Firestore registry seeding, deployment, production writes, Room migration or PR merge is part of this change.

## Integration work still required

- Attach approved strategy/rubric references to server-owned question records and bind them to the versioned test fingerprint.
- Extend the exact policy resolver with strategy/rubric provenance and admin approval.
- Route server attempt evaluation through this engine only when the server-approved strategy version is present.
- Wire the Android attempt pipeline to the equivalent Kotlin engine after model/storage compatibility review.
- Add shared golden fixtures consumed by both runtimes and implement subject aggregation/tie-break as separate tested modules.
- Verify current official JEE Advanced brochure and year-specific question instructions before activating any JEE Advanced test.
