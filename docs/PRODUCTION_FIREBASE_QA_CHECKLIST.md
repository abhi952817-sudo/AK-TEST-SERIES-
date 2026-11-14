# Production Firebase and Student/Admin QA Checklist

Status: checklist prepared; live Console/device checks have not been performed.

## A. Confirm the exact Firebase project and database

Record these values from Firebase Console / Google Cloud Console before any deployment:

- Firebase Project ID.
- Android app ID and package name. Expected Android package: `Com.ak.testseries`.
- Firestore database ID: `(default)`.
- Firestore location and edition.
- Storage bucket name and location.
- Authentication providers enabled for the app.
- Cloud Functions region and deployed function versions.

Cross-check the Firebase project ID and Android app ID in `google-services.json` against the selected Firebase project. The Android app must use the same Firebase project as the Console project being inspected.

## B. Confirm production data without modifying it

In Firestore Console, select the `(default)` database and record the existence and document counts (where available) for expected collections, including:

- `Users_Data`
- `Tests_Data`
- `Community_Posts`
- `Test_AnswerKeys` / `TestAnswerKeys` (verify the actual canonical name)
- `Test_Leaderboards`
- `User_Ranks`

Also inspect representative test documents and nested `questions` subcollections, user `Attempts`, and community image metadata. Do not edit or delete documents during inventory. Compare with the live app's expected collection names and the Firestore Rules.

Zero recent reads/writes on a database is not proof that it is empty. Confirm actual collections/documents directly.

## C. Verify Firebase Storage

- Confirm the exact bucket selected by the Android Firebase app.
- Confirm expected object prefixes: `chart_images/`, `question_images/`, `tests/{testId}/questions/`, `pdfs/`, `LiveTests/`, `community_images/`, and `community_report_evidence/`.
- Check that objects referenced by existing Firestore documents actually exist.
- Confirm the deployed Storage Rules match the reviewed `storage.rules`.
- Do not change bucket location, delete objects, or run a bulk cleanup during QA.

## D. Student account smoke test

Use a designated QA student account, not a real student's account.

1. Sign in and sign out; verify the authenticated UID matches the corresponding `Users_Data` document.
2. Load the student home/test list from the confirmed `(default)` database.
3. Open one approved public test and load its nested questions.
4. Start and submit a controlled test attempt using a designated QA test; verify one attempt is recorded under the correct student's `Attempts` path and server-side score/ranking fields are consistent.
5. Open Community and load a post; verify permitted image reads.
6. Attempt a forbidden write (for example, student PDF upload or writing to protected answer keys) and confirm it is denied.
7. Sign out and confirm account-scoped data is not shown to the next user.

## E. Admin account smoke test

Use a designated QA admin account with the intended custom claim/role.

1. Sign in and verify the account is recognized as Admin by server-side authorization, not only a client UI flag.
2. Load Admin dashboard, exam registry, tests, users, and community moderation lists.
3. Upload one non-sensitive QA image/PDF to the intended Storage path and verify metadata, MIME type, and download/read behavior.
4. Verify Admin-only Firestore/Storage actions are allowed only on intended paths.
5. Verify a normal student cannot perform the same Admin-only actions.
6. Do not run demo cleanup, mass deletion, retention pruning, or bulk writes during this smoke test.

## F. Release evidence required

Attach run IDs/logs and screenshots with private data redacted:

- Android Unit Tests, Debug APK build, and instrumentation test result.
- Firestore Rules emulator suite result.
- Firebase app project ID/package ID match.
- Default database inventory and representative record checks.
- Storage bucket/path/rules verification.
- Server-side export operation success and Cloud Storage object prefix.
- Restore-to-staging import success and representative restored-record checks.
- Student and Admin QA pass/fail notes.

## G. Stop and approval gates

Stop if the project ID, database ID, Storage bucket, account UID, admin claim, or target of any operation is uncertain. Do not merge PR #10, deploy Firebase Rules/Indexes/Functions, change production data, or retire named databases until the owner has reviewed the evidence and explicitly approved each release action.
