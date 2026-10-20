# Master Implementation Tracker - AK Test Series

## Section A: Executive Summary
- **Current Architectural Condition**: MVVM architecture utilizing Jetpack Compose for UI, Kotlin Coroutines & Flow for asynchronous operations, Room for local caching, and Firebase Firestore / Auth / Functions for cloud persistence and server-side authoritative grading. Bilingual (English, Hindi, Bilingual) support is integrated across core screens.
- **Phase A — Secure Submission Integration**: COMPLETED.
  - Replaced direct client Firestore transactions with server-side authoritative execution via Firebase Functions callable endpoint `submitTestAttemptServerSide`.
  - Replaced client answer-key leakage with protected separation: public tests do not expose `correctAnswer` or `explanation`, while trusted grading keys are stored under `private_keys/answerKey` (accessible only by server Admin SDK).
  - Hardened `firestore.rules` to strictly deny direct client writes to server-authoritative score and ranking fields (`maxScore`, `totalTestsAttempted`, `qualifyingTestsCount`, `totalCumulativeScore`, `lastPercentile`, `authoritativeScore`, `verifiedServerSide`, etc.).
  - Added strict idempotency based on stable `attemptSessionToken`, preventing duplicate counters and rejecting conflicting answer payloads.
  - Updated ResultScreen UI to clearly indicate server-verified results vs local/offline previews.
- **Phase B — Universal Ranking Engine & Population-Based Percentile**: COMPLETED.
  - Eliminated client-side random percentile formulas (`Random.nextDouble` and synthetic ratios removed in both `evaluateTestPracticeOrOffline` and `submitTest` local preview). Practice and offline tests evaluate to 0.0 unranked.
  - Implemented genuine population-based percentile engine (`calculatePopulationPercentile` and `calculateScorePercentileFromCounts`) in `backend_functions/examScoring.js`.
  - Enforced per-test competitive ranking against actual candidate cohorts in `submitTestAttemptServerSide` under `Test_Leaderboards/{testId}/Entries`.
  - Added `userPercentile` StateFlow to Android `NEETViewModel` and integrated `getUserRankingDetails` Cloud Function with graceful, hardened Firestore fallback.
  - Corrected competitive score comparison: strictly compares cumulative competitive scores across qualifying tests without falling back to single-test `maxScore`.
  - Enforced `isEligibleForRanking: true` (requiring at least 2 qualifying test attempts) across all leaderboard listeners and aggregate ranking queries to eliminate unranked or practice candidates from inflating participant pools.
  - Secured `Test_Leaderboards` in `firestore.rules` (read: authenticated students, write: admins and server functions only).
- **Recommended Implementation Order**: Phase A: Secure Submission Integration (Done) -> Phase B: Universal Ranking Engine & Population-Based Percentile (Done) -> Phase C: Comprehensive Offline Attempt Synchronization Queue -> Phase D: Production Release Readiness.

---

## Section B: Repository Inventory & Files Changed

### Files Modified / Created in Phase A:
1. **`gradle/libs.versions.toml`**: Added `firebase-functions` to Version Catalog under Firebase BOM.
2. **`app/build.gradle.kts`**: Added `implementation(libs.firebase.functions)` dependency.
3. **`app/src/main/java/Com/ak/testseries/data/Entities.kt`**: Extended `AttemptEntity` with `verifiedServerSide: Boolean = false` and `submissionStatus: String = "CONFIRMED"` (preserving compatibility).
4. **`app/src/main/java/Com/ak/testseries/data/AppDatabase.kt`**: Bumped database version to 18; registered `MIGRATION_17_18` adding `verifiedServerSide` and `submissionStatus` columns.
5. **`app/src/main/java/Com/ak/testseries/viewmodel/NEETViewModel.kt`**:
   - Replaced direct client Firestore attempt write in `submitTest()` with `FirebaseFunctions` callable call to `submitTestAttemptServerSide`.
   - Updated attempt handling to display confirmed server results or fallback to pending/offline previews.
   - Implemented `updateActiveQuestionsWithSolutions` to consume authorized solution keys returned by server post-submission.
   - Refactored `backupDataToCloud()` to eliminate unauthorized attempt collection writes and only backup bookmarks/user preferences.
   - Added `separatePublicAndSecretQuestions()` in test publishing workflows to strip answer keys from public `questionsJson`.
6. **`app/src/main/java/Com/ak/testseries/MainActivity.kt`**: Added verified server badge (`Icons.Rounded.Verified`) and status indicator in `ResultScreen`.
7. **`backend_functions/index.js`**:
   - Implemented strict authentication, argument size, and time bounds validation.
   - Enforced explicit test publication policy.
   - Implemented server-side answer key retrieval from `private_keys/answerKey`.
   - Implemented idempotent attempt handling keyed by `attemptSessionToken`.
   - Returned `solutionKeys` for post-submission review.
8. **`firestore.rules`**:
   - Enforced `hasAuthoritativeKeys` on `Users_Data` creation and updates.
   - Denied direct client creation of verified or ranking-eligible attempts.
   - Protected `private_keys` subcollection across `Tests` and `Tests_Data`.
9. **`firebase.json`**: Configured functions source, storage rules, and emulators.
10. **`backend_functions/callableValidation.test.js`**: Created Node unit test suite verifying callable security, answer key protection, input validation, and idempotency.
11. **`firestore.test.js`**: Updated Firestore security rules unit tests to assert authoritative field protection.
12. **`app/MASTER_IMPLEMENTATION_TRACKER.md`**: Canonical implementation tracker.

### Files Modified / Created in Phase B:
1. **`backend_functions/examScoring.js`**:
   - Refined `calculatePopulationPercentile(rank, totalEligible)` with exact competition formula: `((N - rank + 1) / N) * 100` and deterministic boundary conditions.
   - Added `calculateScorePercentileFromCounts(countBelow, countEqual, totalCount)` supporting official testing formulas with ties.
2. **`backend_functions/index.js`**:
   - Integrated per-test population ranking in `submitTestAttemptServerSide` against `Test_Leaderboards/{testId}/Entries`.
   - Gated `Leaderboard` writes: non-qualifying practice attempts or subsequent retries never pollute the competitive leaderboard.
   - Guaranteed `score` in `Leaderboard` is the cumulative competitive score across qualifying tests (never overwritten by single-test `maxScore`).
   - Returned `testRank`, `testTotalCandidates`, and `percentile` in the callable response.
3. **`firestore.rules`**:
   - Added security match block for `/Test_Leaderboards/{testId}` and its `Entries` subcollection (read: authenticated students, write: verified admin / server only).
4. **`app/src/main/java/Com/ak/testseries/data/Entities.kt`**:
   - Changed default `percentile` in `AttemptEntity` from `92.45` to `0.0`.
5. **`app/src/main/java/Com/ak/testseries/viewmodel/NEETViewModel.kt`**:
   - Removed `Random.nextDouble` in `evaluateTestPracticeOrOffline()` and set `percentile = 0.0`.
   - Removed synthetic formula in `submitTest()` local preview and set `localPercentile = 0.0`.
   - Added `_userPercentile` StateFlow to publish authoritative population percentile.
   - Enhanced `fetchUserRank(selectedExam)`: invokes `getUserRankingDetails` Cloud Function first; falls back to Firestore aggregated counts filtering `.whereEqualTo("isEligibleForRanking", true)`.
   - Removed single-test `maxScore` fallback in competitive score comparisons.
   - Enhanced `fetchLeaderboard(selectedExam)` and `fetchLeaderboardFallback(targetExam)` to filter `.whereEqualTo("isEligibleForRanking", true)` and read `score` directly.
6. **`app/src/main/java/Com/ak/testseries/MainActivity.kt`**:
   - Updated `ResultScreen` to display verified server percentile with clear "Pending Verification" or "N/A (Non-ranking)" states.
   - Updated `MiniLeaderboardCard` to observe `userPercentile` and render the student's population percentile in their personal ranking card.
7. **`backend_functions/examScoring.test.js`**:
   - Added comprehensive unit tests for population-based percentile calculations, boundary conditions, and tie handling.
8. **`backend_functions/callableValidation.test.js`**:
   - Added tests verifying that practice tests yield `percentile: 0.0` with `countsTowardRanking: false`, and official tests calculate exact population-based percentiles against candidate cohorts.

---

## Section C: Feature Status Matrix

| Feature | Current Status | Evidence | Existing Implementation | Security/Correctness State | Verification Status |
|---|---|---|---|---|---|
| Android Callable Integration | IMPLEMENTED_VERIFIED | `NEETViewModel.kt`, `app/build.gradle.kts` | Online submissions invoke `submitTestAttemptServerSide` via Firebase Functions SDK | Trusted server-side grading; local score marked as unverified preview | Verified via code and build |
| Server-Side Validation | IMPLEMENTED_VERIFIED | `backend_functions/index.js`, `callableValidation.test.js` | Checks auth, test publication status, question IDs, time bounds, attempt identity | Tamper-proof server grading; client supplied scores ignored | 19/19 Node tests pass |
| Answer Key Protection | IMPLEMENTED_VERIFIED | `firestore.rules`, `NEETViewModel.kt`, `index.js` | `correctAnswer` & `explanation` stripped from public `questionsJson`; stored in `private_keys/answerKey` | Ordinary users cannot read answer keys before submission; solutions revealed post-submission | Verified |
| Client Write Bypass Removal | IMPLEMENTED_VERIFIED | `firestore.rules`, `NEETViewModel.kt` | Direct client writes to `Attempts` restricted; `backupDataToCloud()` scoped to bookmarks only | Protected fields (`maxScore`, `xp`, `rank`, `totalTestsAttempted`, etc.) blocked | Verified |
| Publication Access Policy | IMPLEMENTED_VERIFIED | `firestore.rules`, `backend_functions/index.js` | Rejected and under-review tests disallowed for non-creators even if `isPublic: true` | Consistent publication policy across Firestore and Functions | Verified |
| Population-Based Percentile | IMPLEMENTED_VERIFIED | `examScoring.js`, `index.js`, `NEETViewModel.kt` | Computes `((N - rank + 1) / N) * 100` against verified candidate cohorts | Zero synthetic or random math; unranked practice evaluates to 0.0 | 19/19 Node tests pass; compile succeeded |
| Competitive Eligibility Gate | IMPLEMENTED_VERIFIED | `index.js`, `NEETViewModel.kt`, `firestore.rules` | Candidates must complete >= 2 qualifying tests before receiving provisional rank | Ineligible accounts excluded from leaderboard & eligible counts | Verified |
| Leaderboard Score Consistency | IMPLEMENTED_VERIFIED | `NEETViewModel.kt`, `index.js` | Strictly compares cumulative competitive scores across qualifying tests; removed `maxScore` fallback | Consistent ranking metrics across client and backend | Verified |
| Per-Test Leaderboard Security | IMPLEMENTED_VERIFIED | `firestore.rules`, `index.js` | `Test_Leaderboards/{testId}` restricted to admin/server writes | Public per-test rankings cannot be forged by clients | Verified |

---

## Section D: Technical Contracts

### 1. Callable Request Contract (`submitTestAttemptServerSide`)
```json
{
  "testId": "string (non-empty, max 256 chars)",
  "attemptSessionToken": "string (stable logical attempt ID e.g. att_{uid}_{testId}_{timestamp})",
  "answers": {
    "question_id_1": { "ans": "A", "time": 15 },
    "question_id_2": "B"
  },
  "timeSpentSeconds": 180,
  "clientTimestamp": 1700000000000
}
```

### 2. Callable Response Contract (`submitTestAttemptServerSide`)
```json
{
  "success": true,
  "verifiedServerSide": true,
  "attemptId": "att_user1_test1_1700000000",
  "testId": "test1",
  "targetExam": "NEET UG",
  "score": 620,
  "rawScore": 620.0,
  "maxScore": 720,
  "percentile": 94.25,
  "testRank": 3,
  "testTotalCandidates": 45,
  "accuracy": 88.5,
  "correctCount": 155,
  "incorrectCount": 10,
  "skippedCount": 15,
  "positiveMarksEarned": 630.0,
  "negativeMarksPenalty": 10.0,
  "isFirstAttempt": true,
  "countsTowardRanking": true,
  "solutionKeys": {
    "q1": { "correctAnswer": "A", "explanation": "Detailed explanation..." }
  },
  "isRetry": false
}
```

### 3. Callable Request/Response Contract (`getUserRankingDetails`)
```json
// Request:
{
  "targetExam": "NEET"
}

// Response:
{
  "isEligible": true,
  "status": "Provisional Rank",
  "isOfficialAir": false,
  "rank": 4,
  "rankString": "#4",
  "percentile": 92.5,
  "competitiveScore": 1280,
  "qualifyingTestsCount": 2,
  "totalEligible": 40,
  "nextTarget": { "name": "Aarav", "rank": 3, "ptsAway": 15 },
  "message": "Next target: Aarav (#3) — You are 15 pts away! Take 1 test to overtake.",
  "targetExam": "NEET"
}
```

---

## Section E: Test and Verification Baseline

### Tests Actually Executed & Observed Results:
1. **Android App Compilation (`compile_applet`)**:
   - Status: **PASSED (Build succeeded - the applet is compiled)**.
2. **Backend Unit Tests (`node --test backend_functions/examScoring.test.js backend_functions/callableValidation.test.js`)**:
   - Status: **PASSED (19/19 tests passed)**.
   - Tests:
     - `Callable rejects unauthenticated calls` (Passed)
     - `Callable rejects missing attemptSessionToken` (Passed)
     - `Callable rejects malformed answers payload` (Passed)
     - `Callable rejects unpublished or rejected tests even if isPublic=true` (Passed)
     - `Authoritative evaluation uses protected private answer keys` (Passed)
     - `Callable rejects unexpected question IDs not in test questions` (Passed)
     - `Callable idempotency: Identical retry returns canonical result without duplicate processing` (Passed)
     - `Callable idempotency: Conflicting reuse of attempt ID is rejected` (Passed)
     - `Non-qualifying practice test yields percentile: 0.0 and countsTowardRanking: false` (Passed)
     - `Official ranking test calculates population percentile against candidate cohort` (Passed)
     - `Answer verification: standard options, numbers, and multiple keys` (Passed)
     - `Option equivalence: index to letter and numerical precision` (Passed)
     - `User answer correctness: single, index, multiple correct` (Passed)
     - `Marking schemes for distinct exams` (Passed)
     - `Authoritative evaluation: NEET standard attempt` (Passed)
     - `Section B optional limit: surplus questions are ignored` (Passed)
     - `CAT TITA questions have zero negative penalty` (Passed)
     - `Population Percentile: standard ranking formula and boundary cases` (Passed)
     - `Score Percentile From Counts: exact testing formula with ties` (Passed)

### Tests Not Executed:
- Live Firebase production deployment verification (forbidden by safety rules).
- Physical device / streaming emulator touch tests.

---

## Section F: Remaining Phase Backlog
- **TASK-003**: Comprehensive Offline Attempt Synchronization Queue & Retry Worker (Phase C).
- **TASK-004**: End-to-End Automated UI Regression Testing (Phase D).
