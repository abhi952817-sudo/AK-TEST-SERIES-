# Trusted Community Test Sharing Contract
Date: 2026-11-20
Branch: `feature/community-server-authoritative-share-2026-11-20`

## Security model

The Android client calls only `shareTestToCommunity` with:
- `testId`: ID of a source document in `Tests_Data`
- `selectedSubject`: the value selected in the test verification UI

The client must not send question records, answers, explanations, exam identity, marking values, author identity, or publication fields as trusted input.

The callable independently:
1. Authenticates the caller and checks the authoritative `Users_Data/{uid}` status.
2. Loads the original `Tests_Data/{testId}` document using the Functions Admin SDK.
3. Verifies that `uploaderId`, `creatorId`, or `ownerId` identifies the caller.
4. Loads `Config/community_exam_registry`, which is admin-managed.
5. Verifies exact `examId + stageId + paperId + scoringVersion`; the administrator-owned registry binds each scoring version to its official exam year. If a source document also has `examYear`, it must match that registry binding.
6. Verifies every question's subject, section, question type, positive marks and negative marks against the year-specific registry policy.
7. Requires a unique question ID, verified Gemini source metadata, a valid answer key, valid option structure, and hosted HTTPS image URLs.
8. Creates public question projections without correct answers or explanations, stores the private key separately, and writes the Community feed card in one Firestore batch. The Android downloader reads only the public question subcollection; the attempt grader and post-attempt solutions callable prefer the verified Community projection and its private key vault.
9. Uses deterministic document IDs and a source fingerprint for safe retries. A source test changed after publication is blocked for administrator review rather than silently overwritten.

The original `Tests_Data` source document is never made public or modified by this callable.

## Admin-managed registry document

Create the following document only after its exam-year, stage, paper, subject, section and marking data have been verified against authoritative exam sources:

Path: `Config/community_exam_registry`

Example shape (illustrative test fixture only; NOT an approved NEET or other exam policy):

```json
{
  "version": 1,
  "exams": {
    "example_exam": {
      "examName": "Example Exam",
      "years": {
        "2026": {
          "scoringVersion": 1,
          "syllabusVersion": "example-2026-v1",
          "stages": {
            "example_stage": {
              "papers": {
                "example_paper": {
                  "subjects": [
                    {
                      "subjectId": "example_subject",
                      "subjectName": "Example Subject",
                      "sections": [
                        {
                          "sectionId": "example_section_a",
                          "sectionName": "Section A",
                          "maxAttemptQuestions": 10,
                          "questionTypes": {
                            "MCQ_SINGLE": {
                              "positiveMarks": 2,
                              "negativeMarks": 0.5
                            }
                          }
                        }
                      ]
                    }
                  ]
                }
              }
            }
          }
        }
      }
    }
  }
}
```

Do not copy illustrative values into production. Missing years, stages, papers, subjects, sections or question types intentionally fail closed. No generic `+4/-1` fallback exists in this publication path.

## Source document requirements

`Tests_Data/{testId}` must contain:
- `uploaderId` or `creatorId` or `ownerId` equal to the authenticated UID
- `status` equal to `Published` or `APPROVED`
- `examId`, `stageId`, `paperId`, `scoringVersion` (and optional `examYear`, which must match the server registry)
- `questionsJson` as a JSON array and `questionsCount` matching its length
- `durationMinutes` and `totalMarks` as valid positive numbers
- `title` and any optional display metadata

A section may include `maxAttemptQuestions` when the official pattern allows fewer attempts than the number of displayed questions. This value is copied into the server-owned shared question projection and applied during grading; if omitted, every question in that section is attemptable.\n\nThe callable calculates the test's maximum marks from the validated per-question marks and any official section attempt limits. The source `totalMarks` must match this server calculation within 0.01; the server does not trust a client-supplied or inconsistent total.\n\nEvery question must include:
- Unique `id`, `questionText`, `subjectId` and/or registered `subject`
- `sectionId` (or a registry-matching section value), `questionType`
- Exact `positiveMarks` and `negativeMarks` from the approved policy
- `sourceVerified: true` and a `generationModel` containing `Gemini`
- A valid `correctAnswer`
- Four or five unique options for MCQs; no options for numerical/NAT/TITA types
- Optional hosted HTTPS `imageUrl`

Full Syllabus is accepted only when the source record has `syllabusCoverageVerified: true`, its `syllabusVersion` matches the year policy, and at least one question exists for every subject registered on that paper. This is a conservative coverage gate, not a claim that mere subject presence proves chapter-by-chapter completeness.

## Firestore access

- Students can read published Community test parents and public question documents.
- Students cannot write, update or delete `Shared_Community_Tests` documents.
- Students cannot read or write `private_keys`; Android never receives answer keys before the attempt is submitted.
- Students cannot create a `Community_Posts` document with `postType: TEST_SHARE` or convert another post into one.
- The Admin SDK callable bypasses client rules and is the only student publication path.
- Other allowed Community post types remain on their existing rules path.

## Deployment and rollout status

This branch contains code and tests only. No Firebase config document has been created, no production Firestore data was changed, and no Cloud Function or Firestore Rules deployment was performed.

Before enabling this flow:
1. Populate the admin-managed registry from approved authoritative exam-year sources.
2. Verify the actual production Firebase project and named Firestore database.
3. Run Functions unit tests and Firestore Rules emulator tests.
4. Run Android unit tests and Debug APK build.
5. Perform a controlled emulator/staging integration test with one owner account and one non-owner account.
6. Obtain explicit approval before deploying Functions or Firestore Rules to production.
