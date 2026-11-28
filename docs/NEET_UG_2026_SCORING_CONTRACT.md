# NEET UG 2026 Scoring Contract

## Policy identity

- `examId`: `neet_ug`
- `examYear`: `2026`
- `scoringVersion`: `neet-ug-2026-v1`
- `stageId`: `neet_main`
- `paperId`: `neet_paper_1`
- Official source: NTA, *NEET (UG)-2026 Information Bulletin*, Chapter 4, Examination Scheme.

The backend policy is pinned to this exact identity. Missing or different year/version/stage/paper metadata is rejected. Numeric `scoringVersion: 1` is accepted only as a temporary compatibility alias when the remaining NEET 2026 identity is exact. New app uploads write the canonical string.

## Standard marking

- Correct single-correct MCQ: +4
- Incorrect option: -1
- Unanswered: 0
- One question: four options and one correct/best option
- Full-length paper: 180 compulsory questions, 180 minutes, 720 maximum marks
- Full-length subject distribution: Physics 45, Chemistry 45, Biology 90 (Botany and Zoology together)

Chapter, subject, and topic tests may contain fewer than 180 questions. Their maximum score is four times the question count.

## NTA answer-key exceptions

The protected `private_keys` answer-key record may carry one of these server-side statuses:

| `scoringStatus` | Required private-key data | Award |
|---|---|---|
| `NORMAL` | `correctAnswer` | +4 / -1 / 0 |
| `MULTIPLE_CORRECT` | `acceptedAnswers` or comma-separated `correctAnswer` | +4 for any accepted option |
| `ALL_OPTIONS_CORRECT` | no correct option required | +4 only if attempted |
| `DROPPED` | no correct option required | +4 whether attempted or unanswered |
| `NO_VALID_OPTION` | no correct option required | +4 whether attempted or unanswered |
| `INVALID_QUESTION` | no correct option required | +4 whether attempted or unanswered |

Unknown statuses fail closed. Exception metadata is copied from the private answer-key record during server validation and is stripped from public question documents. Never accept these fields from a student's answer submission.

## Authoritative submission path

1. Callable loads the test and public question projection.
2. Callable loads protected private answer keys.
3. `validateAndMergeAnswerKeys` merges only server-side answer-key data.
4. `neetUgScoring.js` validates the exact NEET 2026 identity and full-paper structure.
5. The server computes score, counts, subject breakdown, bonus count, and answer-key fingerprint.
6. The attempt record stores the exact policy version, source URL, fingerprint, raw/final score, max score, subject breakdown, and scoring engine.
7. The Android client replaces its local preview with the server-authoritative result.

NEET does not use the generic partial-credit rubric path. Partial credit is not part of the NEET UG 2026 marking scheme.

## Shared tests

The same golden fixture is consumed by Node and Kotlin tests:

`app/src/test/resources/scoring/neet_ug_2026_golden.json`

Backend NEET exception tests additionally cover multiple accepted keys, all-options-correct, dropped questions, unknown IDs/statuses, negative raw score, and the 180-question structure.

## Deployment and release gates

This branch contains code and tests only. It does not seed Firestore, deploy Cloud Functions, rewrite historical attempts, or merge into `main`.

Before enabling the path in production:
- Run exact-head Node and Android CI successfully.
- Verify Firestore rules and private-key access with the Emulator Suite.
- Migrate or recreate NEET test documents with exact policy identity and required full-test metadata.
- Confirm official tests have trusted private answer keys.
- Run admin and student real-device submission tests.
- Review ranking tie-break and subject-result display separately; this scoring contract does not claim official NTA AIR.
